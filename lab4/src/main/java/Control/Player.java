package Control;

import proto.SnakesProto.Direction;

/**
 *
 * @author bratizgut
 */
public interface Player {

    void start();

    void stop();
    
    void changeToViewer();

    void setSnakeDirection(Direction dir);
    
    void exit();
}
