package chat.message;

import java.net.InetSocketAddress;

/**
 *
 * @author bratizgut
 */
public class UnbindMessage extends Message {

    public UnbindMessage(InetSocketAddress destinationAddress) {
        super(destinationAddress);
    }
    
}
