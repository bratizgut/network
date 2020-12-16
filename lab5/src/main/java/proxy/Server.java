package proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Server {

    public static final byte ver = 0x05;
    public static final byte ipV4 = 0x01;
    public static final byte domain = 0x03;

    private Handler handler = null;
    private DnsResolver dnsResolver = null;
    private final Selector selector;
    private final Map<Integer, SelectionKey> clients = new HashMap<>();

    private final ServerSocketChannel channel;

    public Server(int port) throws IOException {
        selector = Selector.open();
        channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress("127.0.0.1", port));
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void start() throws IOException {

        dnsResolver = new DnsResolver(selector);
        handler = new Handler(channel, selector, dnsResolver);

        while (selector.select() > -1) {
            try {
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        handler.accept();
                    } else if (key.isConnectable()) {
                        handler.connect(key);
                    } else if (key.isReadable()) {
                        handler.read(key, clients);
                    } else if (key.isWritable()) {
                        handler.write(key);
                    }
                    iter.remove();
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

}
