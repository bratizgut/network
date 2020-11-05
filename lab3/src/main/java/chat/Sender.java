package chat;

import chat.message.Message;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Sender extends Thread {

    private final DatagramSocket socket;
    private final ConcurrentLinkedQueue<Message> messageQueue;
    
    private boolean waiting;

    public Sender(DatagramSocket socket, ConcurrentLinkedQueue<Message> messageQueue) {
        this.socket = socket;
        this.messageQueue = messageQueue;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                waiting = false;
                if (!messageQueue.isEmpty()) {
                    Message message = messageQueue.poll();
                    try {
                        socket.send(message.getDatagramPacket());
                    } catch (IOException ex) {
                        Logger.getLogger(Sender.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    synchronized (this) {
                        waiting = true;
                        wait(3000);
                    }
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    public boolean isWaiting() {
        return waiting;
    }

}
