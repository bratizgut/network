package Model;

import Control.Controller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Random;

import proto.SnakesProto.*;
import proto.SnakesProto.GameState.*;

/**
 *
 * @author bratizgut
 */
public class Model extends Observable {

    private final int blocksX;
    private final int blocksY;

    private ArrayList<Coord> food;

    private final Random random;

    private int foodCap;
    private final int foodPerPlayer;
    private final float deadFoodProb;
    private int foodCount;

    private int stateOrder;

    private final Map<Integer, SnakeModel> players;
    private final Map<Integer, Integer> score;
    private final ArrayList<Integer> deadPlayesList;

    private GameState curGameState;

    public Model(int blocksX, int blocksY, int foodStatic, int foodPerPlayer, float deadFoodProb) {
        this.blocksX = blocksX;
        this.blocksY = blocksY;

        random = new Random(System.currentTimeMillis());

        food = new ArrayList<>();
        foodCap = foodStatic;
        this.foodPerPlayer = foodPerPlayer;
        this.deadFoodProb = deadFoodProb;
        foodCount = 0;

        stateOrder = 0;

        players = new HashMap<>();
        score = new HashMap<>();
        deadPlayesList = new ArrayList<>();
    }

    public void initWithState(GameState state) {
        food = new ArrayList<>(state.getFoodsList());
        for (Snake s : state.getSnakesList()) {
            players.put(s.getPlayerId(), new SnakeModel(blocksX, blocksY, deadFoodProb, random, s));
        }

        GamePlayers gamePlayers = state.getPlayers();
        for (GamePlayer s : gamePlayers.getPlayersList()) {
            score.put(s.getId(), s.getScore());
        }

        stateOrder = state.getStateOrder();
        curGameState = state;
    }

    public GameState nextState() {

        Map<Integer, List<Coord>> packedCoords = new HashMap<>();
        for (Map.Entry<Integer, SnakeModel> entry : players.entrySet()) {
            Integer key = entry.getKey();
            SnakeModel value = entry.getValue();
            packedCoords.put(key, Controller.getAllCoords(value.getCoordsList(), blocksX, blocksY));
        }

        foodCount = food.size();
        if (foodCount < foodCap) {
            generateFood(packedCoords, foodCap - foodCount);
        }

        players.forEach((id, s) -> {
            if (s.step(food)) {
                int curscore = score.get(id);
                score.put(id, curscore + 1);
            }
        });
        players.forEach((id, s) -> {
            checkCollisions(packedCoords, id);
        });
        for (int id : deadPlayesList) {
            removeSnake(id);
        }
        return createCurGameState();

    }

    public void addSnake(int id) {
        Coord startCoord = Coord.newBuilder().setX(random.nextInt(blocksX)).setY(random.nextInt(blocksY)).build();
        foodCap += foodPerPlayer;
        players.put(id, new SnakeModel(id, startCoord, Direction.RIGHT, 2, deadFoodProb, random, blocksX, blocksY));
        score.put(id, 0);
    }

    public void snakeRotation(int id, Direction direction) {
        SnakeModel snakeModel = players.get(id);
        if (snakeModel != null) {
            snakeModel.setDirection(direction);
        }
    }

    private void generateFood(Map<Integer, List<Coord>> packedCoords, int amount) {

        if ((blocksX * blocksY) <= (food.size())) {
            return;
        }

        for (int i = 0; i < amount; i++) {
            int x;
            int y;
            do {
                x = random.nextInt(blocksX);
                y = random.nextInt(blocksY);
            } while (!foodPlacementCheck(Coord.newBuilder().setX(x).setY(y).build(), packedCoords));
            foodCount++;
            food.add(Coord.newBuilder().setX(x).setY(y).build());
        }
    }

    private boolean foodPlacementCheck(Coord coordinate, Map<Integer, List<Coord>> packedCoords) {
        return !(food.contains(coordinate) || foodCollision(coordinate, packedCoords));
    }

    private boolean foodCollision(Coord coordinate, Map<Integer, List<Coord>> packedCoords) {
        for (List<Coord> s : packedCoords.values()) {
            if (s.contains(coordinate)) {
                return true;
            }
        }
        return false;
    }

    private void checkCollisions(Map<Integer, List<Coord>> packedCoords, int playerId) {
        Coord headCoord = players.get(playerId).getHeadCoordinate();
        packedCoords.forEach((id, coords) -> {
            if (coords.contains(headCoord)) {
                if (id != playerId) {
                    int curScore = score.get(id);
                    score.put(id, curScore + 1);
                }
                players.get(playerId).kill(food);
                deadPlayesList.add(playerId);
                foodCap -= foodPerPlayer;
            }
        });
    }

    public void setState(GameState gameState) {
        curGameState = gameState;
        setChanged();
        notifyObservers(curGameState);
    }

    public int getScore(int id) {
        if (score.containsKey(id)) {
            return score.get(id);
        }
        return -1;
    }

    private GameState createCurGameState() {
        GameState gameState;

        ArrayList<Snake> snakes = new ArrayList<>();

        players.forEach((id, snakeModel) -> {
            Snake snakeProto = Snake.newBuilder()
                    .setPlayerId(id)
                    .setState(Snake.SnakeState.ALIVE)
                    .addAllPoints(snakeModel.getCoordsList())
                    .setHeadDirection(snakeModel.getCurDirection())
                    .build();
            snakes.add(snakeProto);
        });

        gameState = GameState.newBuilder()
                .setStateOrder(stateOrder)
                .addAllSnakes(snakes)
                .setPlayers(GamePlayers.getDefaultInstance())
                .setConfig(GameConfig.getDefaultInstance())
                .addAllFoods(food)
                .build();
        stateOrder++;

        return gameState;
    }

    public GameState getCurGameState() {
        return curGameState;
    }

    public static boolean isBetween(int a, int b, int c) {
        if (a == b) {
            return a == c;
        } else {
            if (a >= 20) {
                a -= 20;
                return c <= a && c >= b;
            } else if (a < 0) {
                a += 20;
                return c >= a && c <= b;
            } else if (b >= 20) {
                b -= 20;
                return c <= b && c >= a;
            } else if (b < 0) {
                return c >= b && c <= a;
            }
        }
        return a > b ? (c <= a && c >= b) : (c >= a && c <= b);
    }

    public boolean isDeadPlayerListEmpty() {
        return deadPlayesList.isEmpty();
    }

    public void clearDeadPlayerList() {
        deadPlayesList.clear();
    }

    public ArrayList<Integer> getDeadPlayesList() {
        return deadPlayesList;
    }

    public void removeSnake(int id) {
        players.remove(id);
    }

}
