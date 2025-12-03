package model;

import java.io.Serializable;

/**
 * 棋步记录
 */
public class Move implements Serializable {
    public final int x, y;
    public final int color; // 1 = black, 2 = white

    public Move(int x, int y, int color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + color;
    }
}