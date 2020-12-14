package Control;

import Model.Model;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import proto.SnakesProto;
import proto.SnakesProto.*;

/**
 *
 * @author bratizgut
 */
public class Master implements Player {

    private int masterId;
    private GameConfig config;

    private String name;

    private long msgSeq;
    private int newPlayerId;

    private final DatagramSocket socket;

    private final ConcurrentHashMap<InetSocketAddress, GamePlayer.Builder> players;
    private final Model model;

    private final GameThread gameThread;
    private final Announcer announcer;
    private final Reciever reciever;

    private final AckSender ackSender;
    private final ConcurrentHashMap<InetSocketAddress, List<Long>> messagesToResend;
    private final MessageInspector messageInspector;

    private final Controller controller;

    private InetSocketAddress deputyAddress;

    public Master(int playerId, Model model, String name, Controller controller) throws IOException {
        this.masterId = playerId;
        this.model = model;
        this.name = name;
        this.controller = controller;
        config = controller.getConfig();

        msgSeq = 0;
        newPlayerId = masterId + 1;

        socket = new DatagramSocket();

        players = new ConcurrentHashMap<>();

        gameThread = new GameThread();
        announcer = new Announcer();
        reciever = new Reciever();

        ackSender = new AckSender(socket, masterId);
        messagesToResend = new ConcurrentHashMap<>();
        messageInspector = new MessageInspector(config.getPingDelayMs(), config.getNodeTimeoutMs(), socket, messagesToResend);

        deputyAddress = null;
    }

    public Master(DatagramSocket socket, InetSocketAddress prevMasterAddress, GameState gameState, Model model, Controller controller, GameConfig config) {
        this.model = model;
        this.controller = controller;
        this.config = config;

        this.socket = socket;

        name = "test";

        players = new ConcurrentHashMap<>();

        gameThread = new GameThread();
        announcer = new Announcer();
        reciever = new Reciever();

        messagesToResend = new ConcurrentHashMap<>();
        messageInspector = new MessageInspector(config.getPingDelayMs(), config.getNodeTimeoutMs(), socket, messagesToResend);

        msgSeq = 1000;
        newPlayerId = 100;

        GamePlayers gamePlayers = gameState.getPlayers();
        for (GamePlayer s : gamePlayers.getPlayersList()) {
            if (s.getRole() != NodeRole.MASTER) {
                if (s.getRole() == NodeRole.DEPUTY) {
                    masterId = s.getId();
                    name = s.getName();
                } else {
                    players.put(new InetSocketAddress(s.getIpAddress(), s.getPort()), s.toBuilder());
                }
            }
        }
        ackSender = new AckSender(socket, masterId);
    }

    public GameState update(GameState arg) {
        boolean masterDead = false;

        if (!messageInspector.isDisconnectListEmpty()) {
            List<InetSocketAddress> disconnecList = messageInspector.getToDisconnectList();
            synchronized (disconnecList) {
                for (InetSocketAddress s : disconnecList) {
                    if (s.equals(deputyAddress)) {
                        deputyAddress = null;
                    }
                    players.remove(s);
                }
            }
        }

        GamePlayer.Builder deputy = null;
        if (deputyAddress != null) {
            deputy = players.get(deputyAddress);
        }

        if (!model.isDeadPlayerListEmpty()) {
            ArrayList<Integer> deadIdList = model.getDeadPlayesList();
            for (int id : deadIdList) {

                if (id == masterId) {
                    masterDead = true;
                }

                if (deputy != null) {
                    if (id == deputy.getId()) {
                        deputyAddress = null;
                    }
                }

                GameMessage.RoleChangeMsg roleChangeMsg = GameMessage.RoleChangeMsg.newBuilder().setReceiverRole(NodeRole.VIEWER).build();
                GameMessage message = GameMessage.newBuilder().setRoleChange(roleChangeMsg).setMsgSeq(msgSeq).build();

                byte[] buf = message.toByteArray();
                InetSocketAddress address = null;
                for (Entry<InetSocketAddress, GamePlayer.Builder> entry : players.entrySet()) {
                    if (Objects.equals(id, entry.getValue().getId())) {
                        address = entry.getKey();
                    }
                }
                if (address != null) {

                    DatagramPacket packet = new DatagramPacket(buf, buf.length, address.getAddress(), address.getPort());
                    try {
                        socket.send(packet);
                        messageInspector.addMessageToResend(address, msgSeq);
                        messageInspector.addMessage(msgSeq, message);
                        incrementMsgSeq();
                    } catch (IOException ex) {
                        Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            model.clearDeadPlayerList();

        }

        GameState gameState = GameState.newBuilder((GameState) arg).setPlayers(getGamePlayers()).build();
        GameMessage.StateMsg stateMsg = GameMessage.StateMsg.newBuilder().setState(gameState).build();
        GameMessage gameMessage = GameMessage.newBuilder().setState(stateMsg).setMsgSeq(msgSeq).build();
        byte[] buf = gameMessage.toByteArray();

        players.forEach((address, player) -> {
            if (player.getId() != masterId) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, address.getAddress(), address.getPort());
                    socket.send(packet);
                    messageInspector.addMessageToResend(address, msgSeq);
                    messageInspector.addMessage(msgSeq, gameMessage);
                } catch (IOException ex) {
                    Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

        if (masterDead) {
            changeToViewer();
        }
        incrementMsgSeq();

        return gameState;
    }

    private class Reciever extends Thread {

        @Override
        public void run() {
            while (!isInterrupted()) {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                try {
                    socket.setSoTimeout(1000);
                    socket.receive(packet);
                    InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    messageInspector.updateLastMsgTime(senderAddress);

                    byte[] msgBytes = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

                    GameMessage gameMessage = GameMessage.parseFrom(msgBytes);
                    if (gameMessage.hasAck()) {
                        messageInspector.removeMessage(senderAddress, gameMessage.getMsgSeq());
                    } else if (gameMessage.hasJoin()) {
                        if (gameMessage.getJoin().getOnlyView()) {
                            GamePlayer.Builder newGamePlayer = GamePlayer.newBuilder()
                                    .setName(name)
                                    .setId(newPlayerId)
                                    .setRole(NodeRole.VIEWER)
                                    .setScore(0)
                                    .setIpAddress(senderAddress.getHostName())
                                    .setPort(senderAddress.getPort());
                            players.put(senderAddress, newGamePlayer);
                            ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, newPlayerId);
                            incrementNewPlayerId();
                        } else {
                            if (initPlayer(new InetSocketAddress(packet.getAddress(), packet.getPort()), gameMessage.getJoin().getName())) {
                                ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, newPlayerId);
                                incrementNewPlayerId();
                            } else {
                                GameMessage.ErrorMsg errorMsg = GameMessage.ErrorMsg.newBuilder().build();
                                GameMessage message = GameMessage.newBuilder().setError(errorMsg).setMsgSeq(msgSeq).build();
                                byte[] buffer = message.toByteArray();
                                socket.send(new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort()));
                            }
                        }
                    } else if (gameMessage.hasSteer()) {
                        GameMessage.SteerMsg steerMsg = gameMessage.getSteer();
                        int senderId = players.get(senderAddress).getId();
                        model.snakeRotation(senderId, steerMsg.getDirection());
                        ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, senderId);
                    } else if (gameMessage.hasPing()) {
                        GamePlayer.Builder player = players.get(senderAddress);
                        if (player != null) {
                            int senderId = player.getId();
                            ackSender.sendAck(gameMessage.getMsgSeq(), senderAddress, senderId);
                        }
                    } else if (gameMessage.hasRoleChange()) {
                        GameMessage.RoleChangeMsg roleChangeMsg = gameMessage.getRoleChange();
                        if (roleChangeMsg.getSenderRole() == NodeRole.VIEWER) {
                            GamePlayer.Builder player = players.get(senderAddress);
                            if (player != null) {
                                player.setRole(NodeRole.VIEWER);
                                if (senderAddress == deputyAddress) {
                                    deputyAddress = null;
                                }
                            }
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (IOException ex) {
                    Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

    }

    private class Announcer extends Thread {

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    GameMessage.AnnouncementMsg announcementMsg = GameMessage.AnnouncementMsg.newBuilder()
                            .setConfig(config)
                            .setPlayers(getGamePlayers()).build();

                    GameMessage msg = GameMessage.newBuilder().setAnnouncement(announcementMsg).setMsgSeq(1).build();
                    byte[] msgBytes = msg.toByteArray();
                    socket.send(new DatagramPacket(msgBytes, msgBytes.length, InetAddress.getByName("239.192.0.4"), 9192));
                    sleep(1000);
                }
            } catch (InterruptedException ignored) {
            } catch (IOException ex) {
                Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    private class GameThread extends Thread {

        @Override
        public void run() {
            while (!isInterrupted()) {
                GameState state = model.nextState();

                GameState curGameState = update(state);
                model.setState(curGameState);
                try {
                    sleep(config.getPingDelayMs());
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }

    }

    private void findDeputy() {
        if (deputyAddress == null) {
            players.forEach((address, player) -> {
                if (player.getRole() == NodeRole.NORMAL) {
                    player.setRole(NodeRole.DEPUTY);
                    deputyAddress = address;
                }
            });
        }

    }

    private GamePlayers getGamePlayers() {
        ArrayList<GamePlayer> playersList = new ArrayList<>();
        players.forEach((address, player) -> {
            int score = model.getScore(player.getId());
            if (score >= 0) {
                playersList.add(player.setScore(score).build());
            }
        });
        playersList.add(GamePlayer.newBuilder()
                .setName(name)
                .setId(masterId)
                .setScore(model.getScore(masterId))
                .setIpAddress("")
                .setPort(0)
                .setRole(NodeRole.MASTER)
                .build());
        return GamePlayers.newBuilder().addAllPlayers(playersList).build();
    }

    private boolean initPlayer(InetSocketAddress address, String name) {
        GamePlayer.Builder newGamePlayer = GamePlayer.newBuilder()
                .setName(name)
                .setId(newPlayerId)
                .setScore(0)
                .setIpAddress(address.getHostName())
                .setPort(address.getPort());

        if (deputyAddress == null) {
            newGamePlayer.setRole(NodeRole.DEPUTY);
            deputyAddress = address;
        } else {
            newGamePlayer.setRole(NodeRole.NORMAL);
        }

        players.put(address, newGamePlayer);
        model.addSnake(newPlayerId);
        return true;
    }

    private void incrementMsgSeq() {
        synchronized (this) {
            msgSeq++;
        }
    }

    private void incrementNewPlayerId() {
        synchronized (this) {
            newPlayerId++;
        }
    }

    private void joinThreads() {
        try {
            gameThread.join();
            reciever.join();
            announcer.join();
            messageInspector.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean sendDeputyToMasterMessage() {
        findDeputy();
        if (deputyAddress != null) {
            GameMessage.RoleChangeMsg roleChangeMsg = GameMessage.RoleChangeMsg.newBuilder().setReceiverRole(NodeRole.MASTER).build();
            GameMessage message = GameMessage.newBuilder().setRoleChange(roleChangeMsg).setMsgSeq(msgSeq).build();

            byte[] buffer = message.toByteArray();
            DatagramPacket p = new DatagramPacket(buffer, buffer.length, deputyAddress.getAddress(), deputyAddress.getPort());
            try {
                socket.send(p);
            } catch (IOException ex) {
                Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
            }
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        announcer.start();
        reciever.start();
        messageInspector.start();
        gameThread.start();
    }

    @Override
    public void stop() {
        if (gameThread.isAlive()) {
            gameThread.interrupt();
        }
        reciever.interrupt();
        announcer.interrupt();
        messageInspector.interrupt();
    }

    @Override
    public void changeToViewer() {
        if (sendDeputyToMasterMessage()) {
            controller.setMasterToViewer(socket, deputyAddress, masterId, config);
        } else {
            System.out.println("Game Over!");
            stop();
        }
    }

    @Override
    public void setSnakeDirection(SnakesProto.Direction dir) {
        model.snakeRotation(masterId, dir);
    }

    @Override
    public void exit() {
        sendDeputyToMasterMessage();
        stop();
        joinThreads();
        socket.close();
    }

}
