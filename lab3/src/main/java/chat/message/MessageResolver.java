package chat.message;

import chat.Node;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class MessageResolver {

    Node node;

    public MessageResolver(Node node) {
        this.node = node;
    }

    public void resolve(byte[] data, InetSocketAddress address) {
        try {
            Message message = (Message) MessageSerializer.deserialize(data);

            if (!node.isRecieved(message.uuid)) {
                node.addToRecieved(message.uuid);
                if (message.getClass().equals(DataMessage.class)) {
                    DataMessage dataMessage = (DataMessage) message;
                    node.printMessage(dataMessage);
                    node.checkNeighbour(address);
                    node.broadcastDataMessage(dataMessage.name, dataMessage.message, address);
                    node.sendAcknowldgeMessage(message.uuid, address);
                }

                if (message.getClass().equals(AcknowledgeMessage.class)) {
                    AcknowledgeMessage acknowledgeMessage = (AcknowledgeMessage) message;
                    node.acknowldgeMessage(acknowledgeMessage.ackUuid);
                }
                
                if(message.getClass().equals(UnbindMessage.class)) {
                    node.unbindNeighbour(address);
                }
                
                if(message.getClass().equals(NeighbourChangeMessage.class)) {
                    NeighbourChangeMessage neighbourChangeMessage = (NeighbourChangeMessage) message;
                    node.unbindNeighbour(address);
                    node.checkNeighbour(neighbourChangeMessage.deputyAddress);
                }
            }

        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(MessageResolver.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
