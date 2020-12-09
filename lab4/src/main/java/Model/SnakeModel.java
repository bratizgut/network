package Model;

import Control.Controller;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import proto.SnakesProto.*;
import proto.SnakesProto.GameState.*;

/**
 *
 * @author bratizgut
 */
public class SnakeModel {

    private Direction curDirection;

    private int length;

    private final int limitX;
    private final int limitY;

    private final float deadFoodProb;
    private final Random random;

    private final Snake.Builder snake;

    public SnakeModel(int playerId, Coord startCoordinate, Direction starDirection, int startLength, float deadFoodProb, Random random, int limitX, int limitY) {
        curDirection = starDirection;

        this.limitX = limitX;
        this.limitY = limitY;

        this.deadFoodProb = deadFoodProb;
        this.random = random;

        length = startLength;

        Coord tailCoord = null;
        switch (curDirection) {
            case UP:
                tailCoord = Coord.newBuilder().setX(0).setY(length - 1).build();
                break;
            case DOWN:
                tailCoord = Coord.newBuilder().setX(0).setY(-(length - 1)).build();
                break;
            case RIGHT:
                tailCoord = Coord.newBuilder().setX(-(length - 1)).setY(0).build();
                break;
            case LEFT:
                tailCoord = Coord.newBuilder().setX(length - 1).setY(0).build();
                break;
        }

        snake = Snake.newBuilder();
        snake.setPlayerId(playerId)
                .addPoints(startCoordinate)
                .addPoints(tailCoord)
                .setHeadDirection(starDirection);

    }

    public SnakeModel(int limitX, int limitY, float deadFoodProb, Random random, Snake snake) {
        this.limitX = limitX;
        this.limitY = limitY;
        this.deadFoodProb = deadFoodProb;
        this.random = random;
        this.snake = snake.toBuilder();
        curDirection = snake.getHeadDirection();
    }

    private Coord incrementCoordinate(Coord coordinate, Direction direction) {
        int x;
        int y;
        switch (direction) {
            case UP:
                x = coordinate.getX();
                y = (coordinate.getY() - 1) < 0 ? limitY - 1 : (coordinate.getY() - 1);
                return Coord.newBuilder().setX(x).setY(y).build();
            case DOWN:
                x = coordinate.getX();
                y = (coordinate.getY() + 1) >= limitY ? 0 : (coordinate.getY() + 1);
                return Coord.newBuilder().setX(x).setY(y).build();
            case RIGHT:
                x = (coordinate.getX() + 1) >= limitX ? 0 : (coordinate.getX() + 1);
                y = coordinate.getY();
                return Coord.newBuilder().setX(x).setY(y).build();
            case LEFT:
                x = (coordinate.getX() - 1) < 0 ? limitX - 1 : (coordinate.getX() - 1);
                y = coordinate.getY();
                return Coord.newBuilder().setX(x).setY(y).build();
            default:
                throw new AssertionError();
        }
    }

    private boolean foodCheck(Coord headCoordinate, ArrayList<Coord> food) {
        if (food.contains(headCoordinate)) {
            food.remove(headCoordinate);
            return true;
        }
        return false;
    }

    public boolean step(ArrayList<Coord> food) {
        List<Coord> prevCoords = snake.getPointsList();
        List<Coord> newCoords = new ArrayList<Coord>();
        snake.clearPoints();
        synchronized (curDirection) {
            if (curDirection == snake.getHeadDirection()) {
                Coord headCoord = prevCoords.get(0);
                Coord preHeadCoordinate = prevCoords.get(1);
                int x;
                int y;
                switch (curDirection) {
                    case UP:
                        x = preHeadCoordinate.getX();
                        y = (preHeadCoordinate.getY() + 1);
                        preHeadCoordinate = Coord.newBuilder().setX(x).setY(y).build();
                        break;
                    case DOWN:
                        x = preHeadCoordinate.getX();
                        y = (preHeadCoordinate.getY() - 1);
                        preHeadCoordinate = Coord.newBuilder().setX(x).setY(y).build();
                        break;
                    case RIGHT:
                        x = (preHeadCoordinate.getX() - 1);
                        y = (preHeadCoordinate.getY());
                        preHeadCoordinate = Coord.newBuilder().setX(x).setY(y).build();
                        break;
                    case LEFT:
                        x = (preHeadCoordinate.getX() + 1);
                        y = preHeadCoordinate.getY();
                        preHeadCoordinate = Coord.newBuilder().setX(x).setY(y).build();
                        break;
                    default:
                        throw new AssertionError();
                }

                headCoord = incrementCoordinate(headCoord, curDirection);

                newCoords.add(headCoord);
                newCoords.add(preHeadCoordinate);
                for (int i = 2; i < prevCoords.size(); i++) {
                    newCoords.add(prevCoords.get(i));
                }
                if (foodCheck(headCoord, food)) {
                    snake.addAllPoints(newCoords);
                    return true;
                }
            } else {
                Coord headCoord = prevCoords.get(0);

                Coord turnCoord = null;
                switch (curDirection) {
                    case UP:
                        turnCoord = Coord.newBuilder().setX(0).setY(1).build();
                        break;
                    case DOWN:
                        turnCoord = Coord.newBuilder().setX(0).setY(-1).build();
                        break;
                    case RIGHT:
                        turnCoord = Coord.newBuilder().setX(-1).setY(0).build();
                        break;
                    case LEFT:
                        turnCoord = Coord.newBuilder().setX(1).setY(0).build();
                        break;
                }

                headCoord = incrementCoordinate(headCoord, curDirection);
                newCoords.add(headCoord);
                newCoords.add(turnCoord);
                for (int i = 1; i < prevCoords.size(); i++) {
                    newCoords.add(prevCoords.get(i));
                }

                if (foodCheck(headCoord, food)) {
                    snake.addAllPoints(newCoords);
                    return true;
                }
            }
            Coord tailCoord = newCoords.get(newCoords.size() - 1);
            tailCoord = Coord.newBuilder()
                    .setX(tailCoord.getX() - Integer.signum(tailCoord.getX()))
                    .setY(tailCoord.getY() - Integer.signum(tailCoord.getY()))
                    .build();
            if (!(tailCoord.getX() == 0 && tailCoord.getY() == 0)) {
                newCoords.set(newCoords.size() - 1, tailCoord);
            } else {
                newCoords.remove(newCoords.size() - 1);
            }
            snake.addAllPoints(newCoords);
            snake.setHeadDirection(curDirection);
        }
        return false;
    }

    public void kill(List<Coord> food) {
        List<Coord> coords = Controller.getAllCoords(snake.getPointsList(), limitX, limitY);
        for (Coord s : coords) {
            float i = random.nextFloat();
            if (i < deadFoodProb) {
                food.add(s);
            }
        }
    }

    public List<Coord> getCoordsList() {
        return snake.getPointsList();
    }

    public Coord getHeadCoordinate() {
        return snake.getPoints(0);
    }

    public Direction getCurDirection() {
        return curDirection;
    }

    public int getLength() {
        return length;
    }

    public void setDirection(Direction direction) {
        synchronized (curDirection) {
            switch (direction) {
                case UP:
                    if (snake.getHeadDirection() != Direction.DOWN) {
                        curDirection = direction;
                    }
                    break;
                case DOWN:
                    if (snake.getHeadDirection() != Direction.UP) {
                        curDirection = direction;
                    }
                    break;
                case RIGHT:
                    if (snake.getHeadDirection() != Direction.LEFT) {
                        curDirection = direction;
                    }
                    break;
                case LEFT:
                    if (snake.getHeadDirection() != Direction.RIGHT) {
                        curDirection = direction;
                    }
                    break;
            }
        }
    }
}
