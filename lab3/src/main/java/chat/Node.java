package chat;

import chat.message.AcknowledgeMessage;
import chat.message.DataMessage;
import chat.message.Message;
import chat.message.MessageResolver;
import chat.message.NeighbourChangeMessage;
import chat.message.UnbindMessage;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Node implements AutoCloseable {

    private final DatagramSocket socket;
    private final String name;

    private final CopyOnWriteArrayList<InetSocketAddress> neighbourList;
    private InetSocketAddress deputyAddress = null;

    private final ConcurrentLinkedQueue<Message> messageQueue;
    private final Reciever reciever;
    private final Sender sender;
    private final MessageInspector inspector;

    private final MessageResolver resolver;
    private final ConcurrentHashMap<Message, Long> pendingMessageMap;
    private final ConcurrentHashMap<UUID, Long> recievedMessageMap;

    private final long WAIT_TIME = 500;
    private final long RESEND_ATTEMPTS = 4;

    public Node(String name, int loss, int port) throws SocketException {
        neighbourList = new CopyOnWriteArrayList<>();
        this.name = name;
        socket = new DatagramSocket(port);

        resolver = new MessageResolver(this);
        pendingMessageMap = new ConcurrentHashMap<>();
        recievedMessageMap = new ConcurrentHashMap<>();

        messageQueue = new ConcurrentLinkedQueue<>();
        reciever = new Reciever(socket, loss, resolver);
        sender = new Sender(socket, messageQueue);
        inspector = new MessageInspector(sender, pendingMessageMap, recievedMessageMap, messageQueue, WAIT_TIME, RESEND_ATTEMPTS);
    }

    public Node(String name, int loss, int port, String address, int neighbourPort) throws SocketException, UnknownHostException {
        this(name, loss, port);
        neighbourList.add(new InetSocketAddress(InetAddress.getByName(address), neighbourPort));
    }

    public void run() throws IOException {
        sender.start();
        reciever.start();
        inspector.start();

        System.out.println("Type '#EXIT' to exit the program.");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            String text = scanner.nextLine();
            if (text.length() < 500) {
                if (!text.isEmpty() && !text.isBlank()) {
                    if (!text.equals("#EXIT")) {
                        broadcastDataMessage(name, text, null);
                    } else {
                        break;
                    }
                }
            } else {
                System.err.println("Message length should be less then 500 characters.");
            }

        }
    }

    public void printMessage(DataMessage message) {
        System.out.println(message.name + ": " + message.message);
    }

    public void checkNeighbour(InetSocketAddress address) {
        if (!neighbourList.contains(address)) {
            neighbourList.add(address);
        }
    }

    public void broadcastDataMessage(String name, String message, InetSocketAddress fromSocketAddress) {
        neighbourList.stream().filter((i) -> (!i.equals(fromSocketAddress))).forEachOrdered((i) -> {
            addMessage(new DataMessage(name, message, new InetSocketAddress(i.getAddress(), i.getPort())));
        });
    }

    public void sendAcknowldgeMessage(UUID uuid, InetSocketAddress destinationAddress) {
        addMessage(new AcknowledgeMessage(uuid, destinationAddress));
    }

    public void acknowldgeMessage(UUID uuid) {
        pendingMessageMap.keySet().removeIf((key) -> {
            return key.uuid.equals(uuid);
        });
    }

    public void addToRecieved(UUID uuid) {
        recievedMessageMap.put(uuid, System.currentTimeMillis());
    }

    public boolean isRecieved(UUID uuid) {
        return recievedMessageMap.containsKey(uuid);
    }

    public void addMessage(Message message) {
        messageQueue.add(message);
        if (!message.getClass().equals(AcknowledgeMessage.class)) {
            pendingMessageMap.put(message, System.currentTimeMillis());
        }
        if (sender.isWaiting()) {
            synchronized (sender) {
                sender.notify();
            }
        }
    }

    public void unbindNeighbour(InetSocketAddress address) {
        neighbourList.remove(address);
    }

    @Override
    public void close() {
        try (socket) {
            if (!neighbourList.isEmpty()) {
                deputyAddress = neighbourList.get(0);
                pendingMessageMap.clear();
                messageQueue.clear();

                UnbindMessage unbindMessage = new UnbindMessage(deputyAddress);
                addMessage(unbindMessage);
                neighbourList.stream().filter((i) -> (!i.equals(deputyAddress))).forEachOrdered((i) -> {
                    addMessage(new NeighbourChangeMessage(deputyAddress, new InetSocketAddress(i.getAddress(), i.getPort())));
                });

                while (!pendingMessageMap.isEmpty()) {
                }
            }

            reciever.interrupt();
            sender.interrupt();
            inspector.interrupt();

            try {
                reciever.join();
                sender.join();
                inspector.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
