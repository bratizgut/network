package View;

import Control.Controller;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import proto.SnakesProto.*;
import proto.SnakesProto.GameState.*;

/**
 *
 * @author bratizgut
 */
public class GamePanel extends JPanel implements Observer {

    private Controller controller;

    private final int BORDER_SIZE = 2;

    private final int WINDOW_HEIGHT = 800 + 2 * BORDER_SIZE;
    private final int WINDOW_WIDTH = 800 + 2 * BORDER_SIZE;

    private int blocksX;
    private int blocksY;
    private int blockSize;

    private int offsetX;
    private int offsetY;

    private GameState curGameState;

    private final GraphicsThread graphicsThread;

    private JTextArea scoreArea;

    public GamePanel() throws HeadlessException {

        super.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);

        graphicsThread = new GraphicsThread();

        Map<String, Direction> keys = Stream.of(
                new AbstractMap.SimpleEntry<>("W", Direction.UP),
                new AbstractMap.SimpleEntry<>("A", Direction.LEFT),
                new AbstractMap.SimpleEntry<>("S", Direction.DOWN),
                new AbstractMap.SimpleEntry<>("D", Direction.RIGHT))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        keys.forEach((k, v) -> {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(k), k);
            getActionMap().put(k, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    controller.setSnakeDirection(v);
                }
            });
        });
    }

    public void setGameSettings(int blocksX, int blocksY, Controller controller, JTextArea scoreArea) {
        this.controller = controller;

        this.blocksX = blocksX;
        this.blocksY = blocksY;
        blockSize = 800 / Integer.max(blocksX, blocksY);

        if (blocksX != blocksY) {
            if (blocksX == Integer.min(blocksX, blocksY)) {
                offsetX = (blocksY - blocksX) / 2 * blockSize + BORDER_SIZE;
                offsetY = BORDER_SIZE;
            } else {
                offsetX = BORDER_SIZE;
                offsetY = (blocksX - blocksY) / 2 * blockSize + BORDER_SIZE;
            }
        } else {
            offsetX = BORDER_SIZE;
            offsetY = BORDER_SIZE;
        }

        this.scoreArea = scoreArea;
    }

    private class GraphicsThread extends Thread {

        @Override
        public void run() {
            while (true) {
                repaint();
                try {
                    sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(GamePanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        paintFieldBorder(g);
        if (curGameState != null) {
            for (Snake s : curGameState.getSnakesList()) {
                paintSnake(g, Controller.getAllCoords(s.getPointsList(), blocksX, blocksY));
            }
            paintFood(g, curGameState.getFoodsList());
            scoreArea.setText(getScoreString());
        }
    }

    private void paintFieldBorder(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        g.setColor(Color.WHITE);
        g.fillRect(offsetX - BORDER_SIZE, offsetY - BORDER_SIZE, blocksX * blockSize + 2 * BORDER_SIZE, blocksY * blockSize + 2 * BORDER_SIZE);
        g.setColor(Color.BLACK);
        g.fillRect(offsetX, offsetY, blocksX * blockSize, blocksY * blockSize);
    }

    private void paintSnake(Graphics g, List<Coord> snakeCoords) {
        Iterator<Coord> it = snakeCoords.iterator();
        Coord headCoord = it.next();
        g.setColor(Color.WHITE);
        for (Iterator<Coord> i = it; i.hasNext();) {
            Coord s = i.next();
            g.fillRect(offsetX + s.getX() * blockSize, offsetY + s.getY() * blockSize, blockSize, blockSize);
        }
        g.setColor(Color.GREEN);
        g.fillRect(offsetX + headCoord.getX() * blockSize, offsetY + headCoord.getY() * blockSize, blockSize, blockSize);
    }

    private void paintFood(Graphics g, List<Coord> foodCoords) {
        for (Coord s : foodCoords) {
            g.setColor(Color.RED);
            g.fillRect(BORDER_SIZE + offsetX + s.getX() * blockSize, BORDER_SIZE + offsetY + s.getY() * blockSize, blockSize - 2 * BORDER_SIZE, blockSize - 2 * BORDER_SIZE);
        }
    }

    private String getScoreString() {
        String res = new String();
        GamePlayers players = curGameState.getPlayers();
        if (players != null) {
            for (GamePlayer s : players.getPlayersList()) {
                res += s.getName() + ": " + s.getScore() + "\n";
            }
        }
        return res;
    }

    public void startPaint() throws InterruptedException {
        if (!graphicsThread.isAlive()) {
            graphicsThread.start();
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        curGameState = (GameState) arg;
    }

}
