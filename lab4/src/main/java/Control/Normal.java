package Control;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import proto.SnakesProto;
import proto.SnakesProto.GameConfig;
import proto.SnakesProto.GameMessage;
import proto.SnakesProto.GameMessage.RoleChangeMsg;
import proto.SnakesProto.GamePlayer;
import proto.SnakesProto.GamePlayers;
import proto.SnakesProto.GameState;

/**
 *
 * @author bratizgut
 */
public class Normal implements Player {

    private final DatagramSocket socket;
    private int id;
    private final Controller controller;

    private boolean viewer;

    private final GameConfig config;

    private final RecieverThread recieverThread;

    private long stateOrder;
    private int msgSeq;

    private final String name;

    private InetSocketAddress masterAddress;

    private AckSender ackSender;
    private ConcurrentHashMap<InetSocketAddress, List<Long>> messagesToResend;
    private MessageInspector messageInspector;

    public Normal(String name, Controller controller, InetSocketAddress address, boolean viewer) throws SocketException, IOException {
        this.socket = new DatagramSocket();
        this.name = name;
        this.controller = controller;

        this.viewer = viewer;

        config = controller.getConfig();

        masterAddress = address;

        stateOrder = 0;
        msgSeq = 0;

        sendJoinMsg();
        recieverThread = new RecieverThread();

    }

    private class RecieverThread extends Thread {

        @Override
        public void run() {
            while (!isInterrupted()) {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.setSoTimeout(500);
                    socket.receive(packet);
                    InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    messageInspector.updateLastMsgTime(senderAddress);

                    byte[] msgBytes = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                    SnakesProto.GameMessage gameMessage = SnakesProto.GameMessage.parseFrom(msgBytes);
                    if (gameMessage.hasState()) {
                        if (!masterAddress.equals(senderAddress)) {
                            masterAddress = senderAddress;
                        }
                        if (stateOrder <= gameMessage.getState().getState().getStateOrder()) {
                            stateOrder = gameMessage.getState().getState().getStateOrder();
                            GameState gameState = gameMessage.getState().getState();
                            controller.setGameState(gameState);
                        }
                        ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, id);
                    }
                    if (gameMessage.hasRoleChange()) {
                        GameMessage.RoleChangeMsg roleChangeMsg = gameMessage.getRoleChange();
                        if (roleChangeMsg.getReceiverRole() == SnakesProto.NodeRole.VIEWER) {
                            viewer = true;
                        } else if (roleChangeMsg.getReceiverRole() == SnakesProto.NodeRole.MASTER) {
                            controller.setDeputyToMaster(socket, masterAddress, controller.getModel().getCurGameState(), controller.getModel(), config);
                            return;
                        }
                        ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, id);
                    } else if (gameMessage.hasPing()) {
                        ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, id);
                    }
                } catch (SocketTimeoutException ignored) {
                    if (!messageInspector.isDisconnectListEmpty()) {

                        List<InetSocketAddress> disconnecList = messageInspector.getToDisconnectList();
                        synchronized (disconnecList) {
                            for (InetSocketAddress s : disconnecList) {
                                if (s.equals(masterAddress)) {
                                    System.out.println("Control.Normal.RecieverThread.run()");
                                    if (getNodeRole() == SnakesProto.NodeRole.DEPUTY) {
                                        controller.setDeputyToMaster(socket, masterAddress, controller.getModel().getCurGameState(), controller.getModel(), config);
                                    } else {
                                        masterAddress = findNewMasAddress();
                                    }
                                }
                            }

                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Normal.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private InetSocketAddress findNewMasAddress() {
        GameState state = controller.getModel().getCurGameState();
        GamePlayers players = state.getPlayers();
        for (GamePlayer s : players.getPlayersList()) {
            if (s.getRole() == SnakesProto.NodeRole.DEPUTY) {
                return new InetSocketAddress(s.getIpAddress(), s.getPort());
            }
        }
        return null;
    }

    private SnakesProto.NodeRole getNodeRole() {
        GameState state = controller.getModel().getCurGameState();
        GamePlayers players = state.getPlayers();
        for (GamePlayer s : players.getPlayersList()) {
            if (s.getId() == id) {
                return s.getRole();
            }
        }
        return SnakesProto.NodeRole.NORMAL;
    }

    private void sendJoinMsg() throws IOException, SocketTimeoutException {
        GameMessage.JoinMsg joinMsg = GameMessage.JoinMsg.newBuilder()
                .setPlayerType(SnakesProto.PlayerType.HUMAN)
                .setName(name)
                .setOnlyView(viewer)
                .build();
        GameMessage message = GameMessage.newBuilder().setJoin(joinMsg).setMsgSeq(1).build();

        byte[] buf = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, masterAddress);
        socket.send(packet);
        buf = new byte[256];
        packet = new DatagramPacket(buf, buf.length);
        socket.setSoTimeout(3000);

        socket.receive(packet);
        GameMessage msg = GameMessage.parseFrom(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()));
        if (msg.hasAck()) {
            id = msg.getReceiverId();
            ackSender = new AckSender(socket, id);
            messagesToResend = new ConcurrentHashMap<>();
            messageInspector = new MessageInspector(config.getPingDelayMs(), config.getNodeTimeoutMs(), socket, messagesToResend);
            messagesToResend.put(masterAddress, new ArrayList<>());
        } else if (msg.hasError()) {
            throw new IOException("JOPA");
        }
    }

    private void sendViewerRoleMessage() {
        RoleChangeMsg roleChangeMsg = RoleChangeMsg.newBuilder().setSenderRole(SnakesProto.NodeRole.VIEWER).build();
        GameMessage message = GameMessage.newBuilder().setRoleChange(roleChangeMsg).setMsgSeq(msgSeq).build();

        byte[] buf = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, masterAddress);
        try {
            socket.send(packet);
        } catch (IOException ex) {
            Logger.getLogger(Normal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void joinThreads() {
        try {
            recieverThread.join();
            messageInspector.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(Normal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void start() {
        recieverThread.start();
        messageInspector.start();
    }

    @Override
    public void stop() {
        recieverThread.interrupt();
        messageInspector.interrupt();
    }

    @Override
    public void changeToViewer() {
        viewer = true;
        sendViewerRoleMessage();
    }

    @Override
    public void setSnakeDirection(SnakesProto.Direction dir) {
        if (!viewer) {
            GameMessage.SteerMsg steerMsg = GameMessage.SteerMsg.newBuilder().setDirection(dir).build();
            GameMessage gameMessage = GameMessage.newBuilder().setSteer(steerMsg).setMsgSeq(10).build();

            try {
                byte[] buf = gameMessage.toByteArray();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, masterAddress);

                socket.send(packet);
                messageInspector.addMessageToResend(masterAddress, msgSeq);
                messageInspector.addMessage(msgSeq, gameMessage);

                msgSeq++;
            } catch (IOException ex) {
                Logger.getLogger(Normal.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void exit() {
        sendViewerRoleMessage();
        stop();
        joinThreads();
        socket.close();
    }

}
