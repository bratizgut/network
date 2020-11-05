package chat.message;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 *
 * @author bratizgut
 */
public class AcknowledgeMessage extends  Message {

    public final UUID ackUuid;

    public AcknowledgeMessage(UUID ackUuid, InetSocketAddress destinationAddress) {
        super(destinationAddress);
        this.ackUuid = ackUuid;
    }

}
