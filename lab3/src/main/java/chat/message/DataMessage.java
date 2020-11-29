package chat.message;

import java.net.InetSocketAddress;

/**
 *
 * @author bratizgut
 */
public class DataMessage extends Message {

    public final String message;
    public final String name;

    public DataMessage(String name, String message, InetSocketAddress destinationAddress) {
        super(destinationAddress);
        this.name = name;
        this.message = message;
    }

}
