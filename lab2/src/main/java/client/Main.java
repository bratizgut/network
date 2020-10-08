package client;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Main {
    public static void main(String[] args) {
        try (Client client = new Client(args[0], Integer.parseInt(args[1]))) {            
            client.sendFile(args[2]);
        } catch (IOException | NumberFormatException | IndexOutOfBoundsException | NoSuchAlgorithmException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
