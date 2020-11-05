package chat;

import chat.message.Message;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author bratizgut
 */
public class MessageInspector extends Thread {

    private final Sender sender;
    private final ConcurrentHashMap<Message, Long> pendingMessageMap;
    private final  ConcurrentHashMap<UUID, Long> recievedMessageMap;
    private final ConcurrentLinkedQueue<Message> messageQueue;
    
    private final long WAIT_TIME;
    private final long RESEND_ATTEMPTS;

    public MessageInspector(Sender sender, ConcurrentHashMap<Message, Long> pendingMessageMap, ConcurrentHashMap<UUID, Long> recievedMessageMap, ConcurrentLinkedQueue<Message> messageQueue, long WAIT_TIME, long RESEND_ATTEMPTS) {
        this.sender = sender;
        this.pendingMessageMap = pendingMessageMap;
        this.recievedMessageMap = recievedMessageMap;
        this.messageQueue = messageQueue;
        
        this.WAIT_TIME = WAIT_TIME;
        this.RESEND_ATTEMPTS = RESEND_ATTEMPTS;
    }

    

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                checkMessages();
                sleep(WAIT_TIME);
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void checkMessages() {
        pendingMessageMap.values().removeIf((value) -> {
            return (System.currentTimeMillis() - value) > RESEND_ATTEMPTS * WAIT_TIME;
        });
        recievedMessageMap.values().removeIf((value) -> {
            return (System.currentTimeMillis() - value) > RESEND_ATTEMPTS * WAIT_TIME;
        });
        pendingMessageMap.entrySet().forEach((entry) -> {            
            Message key = entry.getKey();
            Long value = entry.getValue();
            if (value >= WAIT_TIME) {
                messageQueue.add(key);
                if(sender.isWaiting()) {
                    synchronized(sender) {
                        sender.notify();
                    }
                }
            }
        });
    }

}
