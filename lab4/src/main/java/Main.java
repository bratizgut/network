
import View.MainFrame;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Main {

    public static void main(String[] args) {
        MainFrame mainFrame = new MainFrame();
        try {
            mainFrame.initFrame();
            mainFrame.start();
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
