package Control;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import proto.SnakesProto.GameMessage;

/**
 *
 * @author bratizgut
 */
public class MessageInspector extends Thread {

    private final long ping_delay_ms;
    private final long node_timeout_ms;
    private final DatagramSocket socket;

    private final ConcurrentHashMap<InetSocketAddress, List<Long>> messagesToResend;
    private final ConcurrentHashMap<InetSocketAddress, Long> lastMsgTime;
    private final ConcurrentHashMap<Long, GameMessage> messageMap;

    private final List<InetSocketAddress> toDisconnectList;

    public MessageInspector(long ping_delay_ms, long node_timeout_ms, DatagramSocket socket, ConcurrentHashMap<InetSocketAddress, List<Long>> messagesToResend) {
        this.ping_delay_ms = ping_delay_ms;
        this.node_timeout_ms = node_timeout_ms;
        this.socket = socket;
        this.messagesToResend = messagesToResend;
        this.lastMsgTime = new ConcurrentHashMap<>();
        this.messageMap = new ConcurrentHashMap<>();
        this.toDisconnectList = Collections.synchronizedList(new ArrayList<>());
    }

    public void addMessageToResend(InetSocketAddress address, long msgSeq) {
        List<Long> msgSeqList = messagesToResend.get(address);
        if (msgSeqList == null) {
            msgSeqList = new ArrayList<>();
            messagesToResend.put(address, msgSeqList);
        }
        synchronized (msgSeqList) {
            msgSeqList.add(msgSeq);
        }
    }

    public void addMessage(long msgSeq, GameMessage gameMessage) {
        messageMap.put(msgSeq, gameMessage);
    }

    void resendMessages() throws IOException {
        Map<Long, GameMessage> messages = this.messageMap;
        synchronized (messagesToResend) {
            for (Map.Entry<InetSocketAddress, List<Long>> sentMsgEntry : messagesToResend.entrySet()) {
                synchronized (messagesToResend.get(sentMsgEntry.getKey())) {
                    for (Long msgSeq : sentMsgEntry.getValue()) {
                        GameMessage msg = messages.get(msgSeq);
                        byte[] buf = msg.toByteArray();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length,
                                sentMsgEntry.getKey().getAddress(),
                                sentMsgEntry.getKey().getPort());
                        socket.send(packet);
                    }
                }
            }
        }
    }

    public void removeMessage(InetSocketAddress addr, Long msgSeq) {
        synchronized (messagesToResend) {
            if (messagesToResend.containsKey(addr)) {
                messagesToResend.get(addr).remove(msgSeq);
            }
        }
    }

    public void updateLastMsgTime(InetSocketAddress address) {
        lastMsgTime.put(address, System.currentTimeMillis());
    }

    public boolean isDisconnectListEmpty() {
        return toDisconnectList.isEmpty();
    }

    public List<InetSocketAddress> getToDisconnectList() {
        return toDisconnectList;
    }

    public void checkConnections() {
        lastMsgTime.forEach((address, time) -> {
            if (System.currentTimeMillis() - time > ping_delay_ms) {
                if (System.currentTimeMillis() - time > node_timeout_ms) {
                    toDisconnectList.add(address);
                    messagesToResend.remove(address);
                    lastMsgTime.remove(address);
                } else {
                    GameMessage.PingMsg pingMsg = GameMessage.PingMsg.newBuilder().build();
                    GameMessage message = GameMessage.newBuilder().setPing(pingMsg).setMsgSeq(1000).build();
                    byte[] buf = message.toByteArray();
                    try {
                        socket.send(new DatagramPacket(buf, buf.length, address));
                    } catch (IOException ex) {
                        Logger.getLogger(MessageInspector.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                resendMessages();
                checkConnections();
                sleep(ping_delay_ms);
            } catch (InterruptedException ex) {
                return;
            } catch (IOException ex) {
                Logger.getLogger(MessageInspector.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
