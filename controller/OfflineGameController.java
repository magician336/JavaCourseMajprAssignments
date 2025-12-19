package controller;

import model.GameModel;
import model.Move;
import view.GameView;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * 离线控制器：用于本机双人对弈（不依赖服务器）。
 * - 直接调用 model 更新棋局
 * - 立即执行悔棋（无需对方同意）
 * - 聊天为本地显示（不通过网络）
 */
public class OfflineGameController {
    private final GameModel model;
    private final GameView view;
    private final String playerName;

    public OfflineGameController(GameModel model, GameView view, String playerName) {
        this.model = model;
        this.view = view;
        this.playerName = playerName != null ? playerName : "Local";
        bindView();
        bindModel();
        // 初始提示
        view.appendChat("已进入离线模式，本地双人对弈。");
        view.appendChat("玩家 " + this.playerName + "（仅作标识）");
    }

    private void bindView() {
        view.setBoardClickListener((x, y) -> onBoardClicked(x, y));
        view.setChatSendListener(text -> sendChat(text));
        view.setControlListener(new GameView.ControlListener() {
            @Override
            public void onUndoRequest() { performUndo(); }

            @Override
            public void onReplayRequest() { startReplay(); }

            @Override
            public void onResetRequest() { localReset(); }
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

    private void onBoardClicked(int x, int y) {
        synchronized (model) {
            if (!GameModel.inBounds(x, y)) return;
            if (model.at(x, y) != 0) {
                view.appendChat("该位置已有棋子。");
                return;
            }
            int color = model.getCurrentTurn();
            boolean ok = model.place(x, y, color);
            if (!ok) {
                view.appendChat("落子失败。");
                return;
            }
            if (model.checkWin(x, y)) {
                view.appendChat((color == 1 ? "黑方" : "白方") + " 在 " + x + "," + y + " 获胜。");
                view.showInfo("游戏结束，获胜方: " + (color == 1 ? "BLACK" : "WHITE"));
            }
        }
    }

    private void sendChat(String text) {
        // 本地聊天，附带当前回合方标签以示区分
        String who = (model.getCurrentTurn() == 1) ? "黑方" : "白方";
        view.appendChat(who + "（本地）: " + text);
    }

    private void performUndo() {
        boolean ok = model.undoLast();
        if (ok) view.appendChat("悔棋：已悔一手。");
        else view.appendChat("悔棋失败：无可悔步。");
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

    private void localReset() {
        model.reset();
        view.appendChat("局面已重置（离线）。");
    }
}