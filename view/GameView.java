package view;

import model.GameModel;
import model.Move;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * 视图（View）。只处理界面显示和用户输入的捕获，通过回调将用户动作交给 Controller。
 */
public class GameView {
    public interface BoardClickListener {
        void onCellClicked(int x, int y);
    }

    public interface ChatSendListener {
        void onChatSend(String text);
    }

    public interface ControlListener {
        void onUndoRequest();
        void onReplayRequest();
        void onResetRequest();
    }

    private JFrame frame;
    private BoardPanel boardPanel;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton undoBtn, replayBtn, resetBtn, sendBtn;

    // 定义回调引用
    private BoardClickListener boardListener;
    private ChatSendListener chatListener;
    private ControlListener controlListener;

    public GameView() {
        // 在构造期间同步在 EDT 上创建 GUI，确保调用者（例如 Controller）在构造后能立即使用 view 的组件
        try {
            SwingUtilities.invokeAndWait(this::createAndShowGUI);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GUI", e);
        }
    }

    private void createAndShowGUI() {
        frame = new JFrame("网络五子棋（MVC）");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        boardPanel = new BoardPanel();
        frame.add(boardPanel, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        chatArea = new JTextArea(20, 25);
        chatArea.setEditable(false);
        right.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel chatBottom = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        chatInput.addActionListener(e -> doSendChat());
        sendBtn = new JButton("发送");
        sendBtn.addActionListener(e -> doSendChat());
        chatBottom.add(chatInput, BorderLayout.CENTER);
        chatBottom.add(sendBtn, BorderLayout.EAST);
        right.add(chatBottom, BorderLayout.SOUTH);

        JPanel ctl = new JPanel();
        undoBtn = new JButton("悔棋");
        replayBtn = new JButton("复盘");
        resetBtn = new JButton("新开局");
        undoBtn.addActionListener(e -> { if (controlListener != null) controlListener.onUndoRequest(); });
        replayBtn.addActionListener(e -> { if (controlListener != null) controlListener.onReplayRequest(); });
        resetBtn.addActionListener(e -> { if (controlListener != null) controlListener.onResetRequest(); });
        ctl.add(undoBtn); ctl.add(replayBtn); ctl.add(resetBtn);
        right.add(ctl, BorderLayout.NORTH);

        frame.add(right, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void doSendChat() {
        String t = chatInput.getText().trim();
        if (t.isEmpty()) return;
        if (chatListener != null) chatListener.onChatSend(t);
        appendChat("我: " + t);
        chatInput.setText("");
    }

    public void appendChat(String s) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(s + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public void setBoardClickListener(BoardClickListener l) {
        this.boardListener = l;
        // boardPanel 已在构造中同步创建，不会为 null
        boardPanel.setBoardClickListener(l);
    }

    public void setChatSendListener(ChatSendListener l) {
        this.chatListener = l;
    }

    public void setControlListener(ControlListener l) {
        this.controlListener = l;
    }

    public void updateBoard(GameModel model) {
        boardPanel.updateFromModel(model);
    }

    public void showInfo(String msg) {
        appendChat(msg);
        JOptionPane.showMessageDialog(frame, msg);
    }

    // Board drawing panel
    private static class BoardPanel extends JPanel {
        static final int CELL = 30;
        static final int OFFSET = 20;
        private GameModel model;
        private BoardClickListener listener;

        BoardPanel() {
            setPreferredSize(new Dimension(GameModel.SIZE * CELL + OFFSET * 2, GameModel.SIZE * CELL + OFFSET * 2));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (listener == null) return;
                    int mx = e.getX() - OFFSET;
                    int my = e.getY() - OFFSET;
                    int x = Math.round((float) mx / CELL);
                    int y = Math.round((float) my / CELL);
                    if (!GameModel.inBounds(x, y)) return;
                    listener.onCellClicked(x, y);
                }
            });
        }

        void setBoardClickListener(BoardClickListener l) {
            this.listener = l;
        }

        void updateFromModel(GameModel model) {
            this.model = model;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // background
            g.setColor(new Color(222, 184, 135));
            g.fillRect(0, 0, getWidth(), getHeight());
            // grid
            g.setColor(Color.BLACK);
            for (int i = 0; i < GameModel.SIZE; i++) {
                g.drawLine(OFFSET, OFFSET + i * CELL, OFFSET + (GameModel.SIZE - 1) * CELL, OFFSET + i * CELL);
                g.drawLine(OFFSET + i * CELL, OFFSET, OFFSET + i * CELL, OFFSET + (GameModel.SIZE - 1) * CELL);
            }
            if (model == null) return;
            // draw stones
            for (int y = 0; y < GameModel.SIZE; y++) {
                for (int x = 0; x < GameModel.SIZE; x++) {
                    int c = model.at(x, y);
                    if (c != 0) {
                        int px = OFFSET + x * CELL;
                        int py = OFFSET + y * CELL;
                        if (c == 1) g.setColor(Color.BLACK);
                        else g.setColor(Color.WHITE);
                        g.fillOval(px - 10, py - 10, 20, 20);
                        g.setColor(Color.BLACK);
                        g.drawOval(px - 10, py - 10, 20, 20);
                    }
                }
            }
        }
    }
}