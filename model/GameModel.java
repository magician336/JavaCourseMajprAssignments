package model;

/* 
 * 引入 Java Bean 的属性变化监听器接口，
 * 供Controller注册来接收模型变化通知。
 */
import java.beans.PropertyChangeListener;
/*
 * 用于管理和分发 `PropertyChangeEvent` 
 * 给所有注册的监听者（实现观察者模式的工具类）。
 */
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 五子棋模型（Model）。
 * 负责棋盘、走法列表、悔棋、胜负检测，并通过 PropertyChange 支持进行事件通知。
 */
public class GameModel {
    public static final int SIZE = 15;
    // 0 empty, 1 black, 2 white
    private int[][] board = new int[SIZE][SIZE];
    private final List<Move> moves = new ArrayList<>();
    private int currentTurn = 1; // 黑先

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public GameModel() {
        reset();
    }

    public synchronized boolean place(int x, int y, int color) {
        if (!inBounds(x, y)) return false;
        if (board[y][x] != 0) return false;
        board[y][x] = color;
        Move m = new Move(x, y, color);
        moves.add(m);
        int oldTurn = currentTurn;
        currentTurn = 3 - color;
        pcs.firePropertyChange("move", null, m);

        if (checkWin(x, y)) {
            pcs.firePropertyChange("gameover", null, color);
        } else {
            pcs.firePropertyChange("turn", oldTurn, currentTurn);
        }
        return true;
    }

    public synchronized boolean undoLast() {
        if (moves.isEmpty()) return false;
        Move last = moves.remove(moves.size() - 1);
        board[last.y][last.x] = 0;
        int oldTurn = currentTurn;
        currentTurn = last.color;
        pcs.firePropertyChange("undo", last, null);
        pcs.firePropertyChange("turn", oldTurn, currentTurn);
        return true;
    }

    public synchronized void reset() {
        for (int y = 0; y < SIZE; y++) Arrays.fill(board[y], 0);
        moves.clear();
        int oldTurn = currentTurn;
        currentTurn = 1;
        pcs.firePropertyChange("reset", null, null);
        pcs.firePropertyChange("turn", oldTurn, currentTurn);
    }

    public synchronized int at(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return board[y][x];
    }

    public synchronized List<Move> getMoves() {
        return new ArrayList<>(moves);
    }

    public synchronized int getCurrentTurn() {
        return currentTurn;
    }

    public static boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < SIZE && y < SIZE;
    }

    // 五子连珠检测（基于最后落子）
    public synchronized boolean checkWin(int x, int y) {
        int color = board[y][x];
        if (color == 0) return false;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int cnt = 1;
            cnt += countDir(x, y, d[0], d[1], color);
            cnt += countDir(x, y, -d[0], -d[1], color);
            if (cnt >= 5) return true;
        }
        return false;
    }

    // dx, dy: 步长
    private int countDir(int x, int y, int dx, int dy, int color) {
        int c = 0;
        int nx = x + dx, ny = y + dy;
        while (inBounds(nx, ny) && board[ny][nx] == color) {
            c++;
            nx += dx;
            ny += dy;
        }
        return c;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
}