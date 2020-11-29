package chat.message;

import java.net.InetSocketAddress;

/**
 *
 * @author bratizgut
 */
public class NeighbourChangeMessage extends Message {

    public final InetSocketAddress deputyAddress;
    
    public NeighbourChangeMessage(InetSocketAddress deputyAddress, InetSocketAddress destinationAddress) {
        super(destinationAddress);
        this.deputyAddress = deputyAddress;
    }

}
