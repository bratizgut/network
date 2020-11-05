package chat.message;

import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

/**
 *
 * @author bratizgut
 */
public class Message implements Serializable {

    public final UUID uuid;
    public final InetSocketAddress destinationAddress;

    public Message(InetSocketAddress destinationAddress) {
        uuid = UUID.randomUUID();
        this.destinationAddress = destinationAddress;
    }

    public DatagramPacket getDatagramPacket() throws IOException {
        byte[] data = MessageSerializer.serialize(this);
        return new DatagramPacket(data, data.length, destinationAddress.getAddress(), destinationAddress.getPort());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Message message = (Message) obj;
        return  this.uuid == message.uuid;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + Objects.hashCode(this.uuid);
        return hash;
    }

}
