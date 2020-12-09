package Control;

import Model.Model;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import proto.SnakesProto.*;
import proto.SnakesProto.GameState.*;

/**
 *
 * @author bratizgut
 */
public class Controller {

    private Model model;

    private Player player;

    private GameConfig config;
    private final Properties properties = new Properties();

    public Controller() throws IOException {
        properties.load(Controller.class.getResourceAsStream("/settings.properties"));
        model = null;

    }

    public void initController() throws IOException {
        stopPrevGame();

        config = GameConfig.newBuilder()
                .setWidth(Integer.parseInt(properties.getProperty("width")))
                .setHeight(Integer.parseInt(properties.getProperty("height")))
                .setFoodPerPlayer(Integer.parseInt(properties.getProperty("food_per_player")))
                .setFoodStatic(Integer.parseInt(properties.getProperty("food_static")))
                .setDeadFoodProb(Float.parseFloat(properties.getProperty("dead_food_prob")))
                .setStateDelayMs(Integer.parseInt(properties.getProperty("state_delay_ms")))
                .setPingDelayMs(Integer.parseInt(properties.getProperty("ping_delay_ms")))
                .setNodeTimeoutMs(Integer.parseInt(properties.getProperty("node_timeout_ms")))
                .build();

        model = new Model(config.getWidth(),
                config.getHeight(),
                config.getFoodStatic(),
                (int) config.getFoodPerPlayer(),
                config.getDeadFoodProb());
        player = new Master(0, model, properties.getProperty("name"), this);
        model.addSnake(0);
    }

    public void initController(GameConfig config, InetSocketAddress address) throws IOException {
        stopPrevGame();

        this.config = config;

        String name = properties.getProperty("name");
        model = new Model(config.getWidth(),
                config.getHeight(),
                config.getFoodStatic(),
                (int) config.getFoodPerPlayer(),
                config.getDeadFoodProb());
        try {
            player = new Normal(name, this, address, false);
            player.start();
        } catch (SocketTimeoutException ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void startGame() {
        player.start();
    }

    public void chengeToViewer() {
        if (player != null) {
            player.changeToViewer();
        }
    }

    void setDeputyToMaster(DatagramSocket socket, InetSocketAddress prevMasterAddress, GameState gameState, Model model, GameConfig config) {
        player.stop();
        model.initWithState(gameState);
        player = new Master(socket, prevMasterAddress, gameState, model, this, config);
        player.start();
    }

    void setMasterToViewer(DatagramSocket socket, InetSocketAddress newMasterAddress, int id, GameConfig config) {
        player.stop();
        try {
            player = new Normal("test", this, newMasterAddress, true);
            player.start();
        } catch (IOException ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void stopPrevGame() {
        if (player != null) {
            player.stop();
        }
    }

    public Model getModel() {
        return model;
    }

    public GameConfig getConfig() {
        return config;
    }

    public void addModelObserver(Observer observer) {
        model.addObserver(observer);
    }

    public void setSnakeDirection(Direction dir) {
        if (player != null) {
            player.setSnakeDirection(dir);
        }
    }

    public void setGameState(GameState gameState) {
        model.setState(gameState);
    }

    public int getGameWidth() {
        return config.getWidth();
    }

    public int getGameHeidht() {
        return config.getHeight();
    }

    public static List<Coord> getAllCoords(List<Coord> coords, int limitX, int limitY) {
        ArrayList<Coord> snakeCoordinates = new ArrayList<>();

        Iterator<Coord> it = coords.iterator();
        snakeCoordinates.add(it.next());

        for (Iterator<Coord> i = it; i.hasNext();) {
            Coord s = i.next();
            for (int j = s.getX(); j != 0; j -= Integer.signum(s.getX())) {
                if (j > 0) {
                    int x = (snakeCoordinates.get(snakeCoordinates.size() - 1).getX() + 1) >= limitX ? 0 : (snakeCoordinates.get(snakeCoordinates.size() - 1).getX() + 1);
                    int y = snakeCoordinates.get(snakeCoordinates.size() - 1).getY();
                    snakeCoordinates.add(Coord.newBuilder().setX(x).setY(y).build());
                } else {
                    int x = (snakeCoordinates.get(snakeCoordinates.size() - 1).getX() - 1) < 0 ? (limitX - 1) : (snakeCoordinates.get(snakeCoordinates.size() - 1).getX() - 1);
                    int y = snakeCoordinates.get(snakeCoordinates.size() - 1).getY();
                    snakeCoordinates.add(Coord.newBuilder().setX(x).setY(y).build());
                }
            }
            for (int j = s.getY(); j != 0; j -= Integer.signum(s.getY())) {
                if (j > 0) {
                    int x = snakeCoordinates.get(snakeCoordinates.size() - 1).getX();
                    int y = (snakeCoordinates.get(snakeCoordinates.size() - 1).getY() + 1) >= limitY ? 0 : (snakeCoordinates.get(snakeCoordinates.size() - 1).getY() + 1);
                    snakeCoordinates.add(Coord.newBuilder().setX(x).setY(y).build());
                } else {
                    int x = snakeCoordinates.get(snakeCoordinates.size() - 1).getX();
                    int y = (snakeCoordinates.get(snakeCoordinates.size() - 1).getY() - 1) < 0 ? (limitY - 1) : (snakeCoordinates.get(snakeCoordinates.size() - 1).getY() - 1);
                    snakeCoordinates.add(Coord.newBuilder().setX(x).setY(y).build());
                }
            }
        }
        return snakeCoordinates;
    }
    
    public void exit() {
        if(player != null) {
            player.exit();
        }
    }
}
