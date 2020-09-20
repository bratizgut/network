package App;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 *
 * @author bratizgut
 */
class Receiver {

    private final MulticastSocket socket;

    private final ConnectionStatus status;

    public Receiver(ConnectionStatus status, String IPaddress, int port) throws IOException {
        socket = new MulticastSocket(port);
        InetAddress group = InetAddress.getByName(IPaddress);
        socket.joinGroup(group);
        this.status = status;
    }

    int getPort() {
        return socket.getLocalPort();
    }

    void check() throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.setSoTimeout(1000);
        while (true) {
            socket.receive(packet);
            status.updateConnection(packet.getAddress().toString(), packet.getPort());
        }
    }

    void close() {
        socket.close();
    }
    
}
