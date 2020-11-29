
import chat.Node;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Main {

    public static void main(String[] args) {
        try {
            switch (args.length) {
                case 3:
                    try (Node node = new Node(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]))) {
                        node.run();
                    }
                break;
                case 5:
                    try (Node node = new Node(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3], Integer.parseInt(args[4]))) {
                        node.run();
                    }
                    break;
                default:
                    System.err.println("Incorrect number of arguments. Required 3 or 5 arguments.");
                    break;
            }
        } catch (IOException | NumberFormatException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
