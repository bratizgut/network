package App;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 *
 * @author bratizgut
 */
class Sender {

    private final DatagramSocket socket;
    private final InetAddress address;

    public Sender(String IPaddress) throws IOException {
        socket = new DatagramSocket();
        address = InetAddress.getByName(IPaddress);
    }

    void send(String mes, int port) throws IOException {
        byte[] buf;
        buf = mes.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }

    void close() {
        socket.close();
    }

}
