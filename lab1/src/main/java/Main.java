
import App.App;

/**
 *
 * @author bratizgut
 */
public class Main {

    public static void main(String[] args) {
        App app = new App(args[0], Integer.parseInt(args[1]));
        app.run();
    }
}
