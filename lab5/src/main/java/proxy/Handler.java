package proxy;

import message.Attachment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

public class Handler {

    private final ServerSocketChannel serverSocket;
    private final Selector selector;
    private int idCounter = 0;
    DnsResolver dnsResolver;

    public Handler(ServerSocketChannel serverSocket, Selector selector, DnsResolver dnsResolver) {
        this.serverSocket = serverSocket;
        this.selector = selector;
        this.dnsResolver = dnsResolver;
    }

    public void accept() throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }

    public void connect(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        channel.finishConnect();

        InetSocketAddress socketAddress = (InetSocketAddress) ((SocketChannel) attachment.getPeerKey().channel()).getLocalAddress();

        ByteBuffer msg = makeResponseMessage(socketAddress);
        attachment.setIn(msg);
        attachment.getIn().flip();

        attachment.setOut(((Attachment) attachment.getPeerKey().attachment()).getIn());
        ((Attachment) attachment.getPeerKey().attachment()).setOut(attachment.getIn());

        attachment.getPeerKey().interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps(0);
    }

    public void read(SelectionKey key, Map<Integer, SelectionKey> clients) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();

        if ((attachment != null) && (attachment.getRole() == Attachment.Role.DNS_RESOLVER)) {
            dnsResolver.readDnsMessage(key);
        } else {
            if (attachment == null) {
                attachment = new Attachment(getNextId(), Attachment.Role.CLIENT);
                key.attach(attachment);
                clients.put(attachment.getClientId(), key);
                attachment.setIn(ByteBuffer.allocate(Attachment.BUF_SIZE));
            }

            int n = channel.read(attachment.getIn());
            if (n > 0) {
                if (attachment.getStatus() == Attachment.Status.GREETING) {
                    readGreeting(key, channel, attachment);
                } else if (attachment.getStatus() == Attachment.Status.CONNECTION) {
                    readConnection(key, attachment);
                } else {
                    if (attachment.getPeerKey() != null) {
                        attachment.getPeerKey().interestOps(attachment.getPeerKey().interestOps() | SelectionKey.OP_WRITE);
                        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
                        attachment.getIn().flip();
                    } else {
                        close(key);
                    }
                }
            } else {
                close(key);
            }
        }
    }

    public void write(SelectionKey key) throws IOException {
        Attachment attachment = (Attachment) key.attachment();
        if ((attachment != null) && (attachment.getRole() == Attachment.Role.DNS_RESOLVER)) {
            dnsResolver.sendDnsRequest(key);
        } else {
            if ((attachment.getStatus() == Attachment.Status.GREETING) || (attachment.getStatus() == Attachment.Status.ERROR)) {
                writeAuthResponse(key, attachment);
            } else if (attachment.getStatus() == Attachment.Status.CONNECTION) {
                readConnection(key, attachment);
            } else if (attachment.getStatus() == Attachment.Status.CONNECTED) {
                writeData(key, attachment);
            }
        }
    }

    private void writeData(SelectionKey key, Attachment attachment) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        if (channel.write(attachment.getOut()) > 0) {
            if (attachment.getOut().remaining() == 0) {
                attachment.getOut().clear();
                attachment.getPeerKey().interestOps(attachment.getPeerKey().interestOps() | SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }
        } else {
            close(key);
        }
    }

    private void writeAuthResponse(SelectionKey key, Attachment attachment) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        byte[] choice;
        if ((attachment.getStatus() == Attachment.Status.GREETING)) {
            choice = new byte[]{Server.ver, 0x00};
            attachment.setStatus(Attachment.Status.CONNECTION);
            key.interestOps(SelectionKey.OP_READ);
            key.attach(attachment);
        } else {
            choice = new byte[]{Server.ver, (byte) 0xFF};
        }
        attachment.setOut(ByteBuffer.wrap(choice));
        channel.write(attachment.getOut());
    }

    private ByteBuffer makeResponseMessage(InetSocketAddress address) {
        byte[] ipBytes = address.getAddress().getAddress();
        ByteBuffer res = ByteBuffer.allocate(10);
        res.put(new byte[]{Server.ver, 0x00, 0x00, Server.ipV4});
        byte[] port = Arrays.copyOfRange(ByteBuffer.allocate(4).putInt(address.getPort()).array(), 2, 4);
        res.put(ipBytes).put(port);
        return res;
    }

    private void newConnection(InetAddress address, int port, SelectionKey key, Attachment attachment) throws IOException {
        SocketChannel connectionSocket = SocketChannel.open();
        connectionSocket.configureBlocking(false);

        connectionSocket.connect(new InetSocketAddress(address, port));
        SelectionKey connectionKey = connectionSocket.register(key.selector(), SelectionKey.OP_CONNECT);

        key.interestOps(0);
        attachment.setPeerKey(connectionKey);
        attachment.getIn().clear();
        attachment.setStatus(Attachment.Status.CONNECTED);

        Attachment connectionAttachment = new Attachment(getNextId(), Attachment.Role.CLIENT);
        connectionAttachment.setStatus(Attachment.Status.CONNECTED);
        connectionAttachment.setOut(ByteBuffer.allocate(Attachment.BUF_SIZE));
        connectionAttachment.setIn(ByteBuffer.allocate(Attachment.BUF_SIZE));
        connectionAttachment.setPeerKey(key);
        connectionKey.attach(connectionAttachment);
    }

    private void readConnection(SelectionKey key, Attachment attachment) throws IOException {

        if (attachment.getIn().position() > 4) {
            byte[] data = Arrays.copyOfRange(attachment.getIn().array(), 0, attachment.getIn().position());

            if ((data[0] != Server.ver) || (data[1] != 0x01)) {
                close(key);
                return;
            }

            byte[] portBytes = new byte[]{0, 0, data[data.length - 2], data[data.length - 1]};
            int port = ByteBuffer.wrap(portBytes).getInt();

            if (data[3] == Server.ipV4) {
                InetAddress address = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
                newConnection(address, port, key, attachment);
            } else if (data[3] == Server.domain) {
                if (attachment.getRequestAddr() != null) {
                    newConnection(attachment.getRequestAddr(), port, key, attachment);
                } else {
                    String name = new String(Arrays.copyOfRange(data, 5, 5 + data[5]), StandardCharsets.UTF_8) + ".";
                    dnsResolver.makeDnsRequest(name, attachment.getClientId(), key);
                    key.interestOps(0);
                }
            }
        }
    }

    private void readGreeting(SelectionKey key, SocketChannel channel, Attachment attachment) throws IOException {
        channel.read(attachment.getIn());
        byte[] msg = attachment.getIn().array();

        if ((msg[0] != Server.ver) || (msg.length < 3)) {
            close(key);
        }
        byte[] authMethods = Arrays.copyOfRange(msg, 2, 2 + msg[1]);
        boolean isAuthed = false;

        for (byte authMethod : authMethods) {
            if (authMethod == 0x00) {
                attachment.setStatus(Attachment.Status.GREETING);
                attachment.getIn().clear();
                key.interestOps(SelectionKey.OP_WRITE);
                key.attach(attachment);
                isAuthed = true;
                break;
            }
        }
        if (!isAuthed) {
            attachment.setStatus(Attachment.Status.ERROR);
            key.interestOps(SelectionKey.OP_WRITE);
            key.attach(attachment);
        }
    }

    private int getNextId() {
        return ++idCounter;
    }

    private void close(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
        SelectionKey peerKey = ((Attachment) key.attachment()).getPeerKey();
        if (peerKey != null) {
            ((Attachment) peerKey.attachment()).setPeerKey(null);
            if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((Attachment) peerKey.attachment()).getOut().flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

}
