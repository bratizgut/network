
import App.App;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Main {

    public static void main(String[] args) {
        App app = new App(args[0], Integer.parseInt(args[1]));
        app.start();
        Scanner in = new Scanner(System.in);
        String mes = in.nextLine();
        while (!mes.equals("stop")) {
            mes = in.nextLine();
        }
        app.interrupt();
        try {
            app.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        in.close();
    }
}
