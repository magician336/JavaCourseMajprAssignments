import java.util.*;

/**
 * 五子棋逻辑: 15x15 棋盘
 */
public class GameState {
    public static final int SIZE = 15;
    // 0 empty, 1 black, 2 white
    private int[][] board = new int[SIZE][SIZE];
    private List<Move> moves = new ArrayList<>();
    private int currentTurn = 1; // 黑先

    public synchronized boolean place(int x, int y, int color) {
        if (!inBounds(x,y)) return false;
        if (board[y][x] != 0) return false;
        board[y][x] = color;
        moves.add(new Move(x, y, color));
        currentTurn = 3 - color;
        return true;
    }

    public synchronized boolean undoLast() {
        if (moves.isEmpty()) return false;
        Move last = moves.remove(moves.size()-1);
        board[last.y][last.x] = 0;
        currentTurn = last.color;
        return true;
    }

    public synchronized List<Move> getMoves() {
        return new ArrayList<>(moves);
    }

    public synchronized int at(int x, int y) {
        if (!inBounds(x,y)) return 0;
        return board[y][x];
    }

    public synchronized void reset() {
        for (int y=0;y<SIZE;y++) Arrays.fill(board[y], 0);
        moves.clear();
        currentTurn = 1;
    }

    public static boolean inBounds(int x, int y) {
        return x>=0 && y>=0 && x<SIZE && y<SIZE;
    }

    // 五子连珠检测（当前最后一手）
    public synchronized boolean checkWin(int x, int y) {
        int color = board[y][x];
        if (color == 0) return false;
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int cnt = 1;
            cnt += countDir(x,y,d[0],d[1],color);
            cnt += countDir(x,y,-d[0],-d[1],color);
            if (cnt >= 5) return true;
        }
        return false;
    }

    private int countDir(int x, int y, int dx, int dy, int color) {
        int c=0;
        int nx=x+dx, ny=y+dy;
        while (inBounds(nx,ny) && board[ny][nx]==color) {
            c++; nx+=dx; ny+=dy;
        }
        return c;
    }
}