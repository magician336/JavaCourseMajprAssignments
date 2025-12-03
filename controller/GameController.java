package controller;

import model.GameModel;
import model.Move;
import view.GameView;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * 控制器（Controller）。负责把 View 的用户操作转为 Model 调用、并处理网络通信。
 */
public class GameController {
    private final GameModel model;
    private final GameView view;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private int myColor = 0; // 1 black, 2 white
    private boolean myTurn = false;
    private final String playerName;
    private final String host;
    private final int port;

    public GameController(GameModel model, GameView view, String host, int port, String playerName) {
        this.model = model;
        this.view = view;
        this.host = host;
        this.port = port;
        this.playerName = playerName;

        bindView();
        bindModel();

        connectToServer();
    }

    private void bindView() {
        view.setBoardClickListener((x, y) -> SwingUtilities.invokeLater(() -> onBoardClicked(x, y)));
        view.setChatSendListener(text -> sendChat(text));
        view.setControlListener(new GameView.ControlListener() {
            @Override
            public void onUndoRequest() { sendUndoRequest(); }

            @Override
            public void onReplayRequest() { startReplay(); }

            @Override
            public void onResetRequest() { sendResetRequest(); } // 改为发送 RESET 通知
        });
    }

    private void bindModel() {
        model.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                switch (evt.getPropertyName()) {
                    case "move":
                    case "undo":
                    case "reset":
                    case "turn": {
                        view.updateBoard(model);
                        break;
                    }
                    case "gameover": {
                        int winner = (int) evt.getNewValue();
                        view.appendChat("游戏结束，获胜方: " + (winner == 1 ? "BLACK" : "WHITE"));
                        view.showInfo("游戏结束，获胜方: " + (winner == 1 ? "BLACK" : "WHITE"));
                        break;
                    }
                }
            }
        });
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                out.println("NAME:" + playerName);
                view.appendChat("已连接到服务器 " + host + ":" + port);
                // listen loop
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                view.appendChat("与服务器连接异常: " + e.getMessage());
            }
        }).start();
    }

    private void handleServerMessage(String line) {
        if (line.startsWith("START:COLOR:")) {
            String c = line.substring("START:COLOR:".length());
            if ("BLACK".equalsIgnoreCase(c)) {
                myColor = 1; myTurn = true;
                view.appendChat("游戏开始，你是黑方（先手）。");
            } else {
                myColor = 2; myTurn = false;
                view.appendChat("游戏开始，你是白方（后手）。");
            }
            model.reset();
            return;
        }
        if (line.startsWith("CHAT:")) {
            view.appendChat("对方: " + line.substring(5));
            return;
        }
        if (line.startsWith("MOVE:")) {
            String body = line.substring(5);
            String[] p = body.split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]);
            int color = (myColor == 1) ? 2 : 1; // 对手颜色
            model.place(x, y, color);
            myTurn = (color != myColor);
            if (model.checkWin(x, y)) {
                view.appendChat("对方在 " + x + "," + y + " 获胜。");
                view.showInfo("对方获胜。");
            }
            return;
        }
        if (line.equals("UNDO_REQUEST")) {
            // 对方请求悔棋：在本地提示并在同意时本地撤步并通知对方
            int opt = JOptionPane.showConfirmDialog(null, "对方请求悔棋，是否同意？", "悔棋请求", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) {
                // 接受者立即在本地执行撤销（保证双方一致），然后通知对方
                boolean ok = model.undoLast();
                if (ok) {
                    view.appendChat("你已同意悔棋，己方已撤一手。");
                    out.println("UNDO_ACCEPT");
                } else {
                    view.appendChat("无法悔棋（无棋步）。");
                    out.println("UNDO_DENY");
                }
            } else {
                out.println("UNDO_DENY");
            }
            return;
        }
        if (line.equals("UNDO_ACCEPT")) {
            // 对方同意悔棋，作为请求方在收到此消息时也撤一手
            boolean ok = model.undoLast();
            if (ok) view.appendChat("悔棋已被对方接受，已悔一手。");
            else view.appendChat("无法悔棋（本地无棋步）。");
            return;
        }
        if (line.equals("UNDO_DENY")) {
            view.appendChat("对方拒绝悔棋。");
            return;
        }
        if (line.startsWith("GAME_OVER:")) {
            String winner = line.substring("GAME_OVER:".length());
            view.appendChat("游戏结束，获胜方: " + winner);
            view.showInfo("游戏结束，获胜方: " + winner);
            return;
        }
        if (line.equals("REPLAY_START")) {
            view.appendChat("对方请求进入复盘模式。");
            return;
        }
        if (line.equals("RESET")) {
            // 对方发起新开局，重置本地模型并通知用户
            model.reset();
            view.appendChat("对方发起新开局，已重置局面。");
            return;
        }
        view.appendChat("收到: " + line);
    }

    private void onBoardClicked(int x, int y) {
        if (myColor == 0) {
            view.appendChat("尚未分配颜色，等待开局。");
            return;
        }
        if (!myTurn) {
            view.appendChat("现在不是你的回合。");
            return;
        }
        synchronized (model) {
            if (model.at(x, y) != 0) {
                view.appendChat("该位置已有棋子。");
                return;
            }
            boolean ok = model.place(x, y, myColor);
            if (!ok) {
                view.appendChat("落子失败。");
                return;
            }
            out.println("MOVE:" + x + "," + y);
            myTurn = false;
            if (model.checkWin(x, y)) {
                view.appendChat("你获胜！");
                out.println("GAME_OVER:" + (myColor == 1 ? "BLACK" : "WHITE"));
                view.showInfo("你获胜！");
            }
        }
    }

    private void sendChat(String text) {
        if (out != null) {
            out.println("CHAT:" + text);
            view.appendChat("我: " + text);
        } else {
            view.appendChat("尚未连接到服务器，无法发送消息。");
        }
    }

    private void sendUndoRequest() {
        if (out != null) {
            out.println("UNDO_REQUEST");
            view.appendChat("已发送悔棋请求，等待对方...");
        } else view.appendChat("尚未连接到服务器。");
    }

    private void startReplay() {
        List<Move> moves = model.getMoves();
        if (moves.isEmpty()) {
            view.showInfo("当前无棋步可复盘。");
            return;
        }
        new Thread(() -> {
            try {
                model.reset();
                Thread.sleep(300);
                for (Move m : moves) {
                    Thread.sleep(500);
                    model.place(m.x, m.y, m.color);
                }
                view.showInfo("复盘结束。");
            } catch (InterruptedException e) {
                // ignore
            }
        }).start();
    }

    // 将本地重置改为发送 RESET 给对手
    private void sendResetRequest() {
        if (out != null) {
            out.println("RESET");
            // 本地也立即重置（视需求可等对方确认）
            model.reset();
            view.appendChat("已发起新开局并本地重置。");
        } else {
            model.reset();
            view.appendChat("本地重置（未连接服务器）。");
        }
    }
}