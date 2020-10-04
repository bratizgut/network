package App;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class App {

    private final Receiver reciever;
    private final Sender sender;

    private final ConnectionStatus status;

    private Thread mainThread = Thread.currentThread();
    private boolean isRunning = true;

    public App(String IPaddress, int port) throws IOException {
        status = new ConnectionStatus(5000);
        reciever = new Receiver(status, IPaddress, port);
        sender = new Sender(IPaddress);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                isRunning = false;
                try {
                    mainThread.join();
                } catch (InterruptedException ex) {
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public void run() {
        try {
            Scanner in = new Scanner(System.in);
            while (isRunning) {
                sender.send("test message", reciever.getPort());
                try {
                    reciever.check();
                } catch (SocketTimeoutException ignored) {
                }
                if (status.isConnectionChanged()) {
                    status.printStatus();
                }
                status.updateStatus();
                if (System.in.available() > 0) {
                    if (in.next().equals("end")) {
                        break;
                    }
                }
            }
            in.close();
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            closeSockets();
        }
    }

    public void closeSockets() {
        reciever.close();
        sender.close();
    }

}
