package App;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author bratizgut
 */
public class ConnectionStatus {

    LinkedHashMap<Client, Long> connections;
    private boolean connectionChanged = false;

    int timeOutTime;

    public ConnectionStatus(int timeOutTime) {
        connections = new LinkedHashMap<Client, Long>();
        this.timeOutTime = timeOutTime;
    }

    private class Client {

        String IPaddress;
        int port;

        public Client(String IPaddress, int port) {
            this.IPaddress = IPaddress;
            this.port = port;
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
            Client client = (Client) obj;
            return this.IPaddress.equals(client.IPaddress) && (this.port == client.port);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + Objects.hashCode(this.IPaddress);
            hash = 89 * hash + this.port;
            return hash;
        }

    }

    public void updateConnection(String IPaddress, int port) {
        Client client = new Client(IPaddress, port);
        if (connections.containsKey(client)) {
            connections.replace(client, System.currentTimeMillis());
        } else {
            connections.put(client, System.currentTimeMillis());
            connectionChanged = true;
        }
    }

    public void updateStatus() {
        int prevSize = connections.size();
        connections.values().removeIf(value -> (System.currentTimeMillis() - value >= timeOutTime));
        if (prevSize != connections.size()) {
            connectionChanged = true;
        }
    }

    public boolean isConnectionChanged() {
        return connectionChanged;
    }

    void printStatus() {
        System.out.println("======================================================");
        for (Map.Entry<Client, Long> entry : connections.entrySet()) {
            Client key = entry.getKey();
            System.out.println(key.IPaddress + " " + key.port + " connected");
        }
        System.out.println("======================================================");
        connectionChanged = false;
    }
}
