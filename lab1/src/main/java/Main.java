
import App.App;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 2) {
            try {
                App app = new App(args[0], Integer.parseInt(args[1]));
                app.run();
            } catch (IOException | IllegalArgumentException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            System.err.println("Requiered 2 arguments.");
        }
    }
}
