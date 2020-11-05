package chat;

import chat.message.MessageResolver;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Reciever extends Thread {

    private final DatagramSocket socket;
    private final int loss;
    private final MessageResolver resolver;

    public Reciever(DatagramSocket socket, int loss, MessageResolver resolver) {
        this.socket = socket;
        this.loss = loss;
        this.resolver = resolver;
    }

    @Override
    public void run() {
        byte[] buf;

        try {
            while (!isInterrupted()) {
                try {
                    socket.setSoTimeout(1000);
                    buf = new byte[2048];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    InetSocketAddress address = new InetSocketAddress(packet.getAddress(), packet.getPort());

                    if (loss <= (int) (Math.random() * 100)) {
                        resolver.resolve(packet.getData(), address);
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Reciever.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
