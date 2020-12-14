package Control;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import proto.SnakesProto.GameMessage;
import proto.SnakesProto.GameMessage.AckMsg;

/**
 *
 * @author bratizgut
 */
public class AckSender {

    private final DatagramSocket socket;
    private final int senderId;

    public AckSender(DatagramSocket socket, int senderId) {
        this.socket = socket;
        this.senderId = senderId;
    }

    public void sendAck(long msgSeq, InetSocketAddress address, int recieverId) throws IOException {
        AckMsg ackMsg = AckMsg.newBuilder().build();
        GameMessage message = GameMessage.newBuilder().setAck(ackMsg).setSenderId(senderId).setReceiverId(recieverId).setMsgSeq(msgSeq).build();
        byte[] buf = message.toByteArray();
        if (address != null) {
            socket.send(new DatagramPacket(buf, buf.length, address));
        }
    }
}
