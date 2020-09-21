package App;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class App {

    private Receiver reciever;
    private Sender sender;

    private ConnectionStatus status;

    public App(String IPaddress, int port) {
        status = new ConnectionStatus(5000);
        try {
            reciever = new Receiver(status, IPaddress, port);
            sender = new Sender(IPaddress);
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void run() {
        try {
            while (true) {
                sender.send("test message", reciever.getPort());
                try {
                    reciever.check();
                } catch (SocketTimeoutException ignored) {
                }
                if (status.isConnectionChanged()) {
                    status.printStatus();
                }
                status.updateStatus();
            }
        } catch (IOException ex) {
            reciever.close();
            sender.close();
        }
    }

}
