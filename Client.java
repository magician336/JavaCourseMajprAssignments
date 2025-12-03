import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.List;

/**
 * 客户端：包含 GUI（棋盘 + 聊天）、网络通信、悔棋/复盘逻辑。
 * 使用: java Client <serverHost> <port> <playerName>
 */
public class Client {
    private String host;
    private int port;
    private String playerName;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JFrame frame;
    private GamePanel gamePanel;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton undoBtn, replayBtn, newGameBtn;

    private GameState state = new GameState();
    private int myColor = 0; // 1 black, 2 white
    private boolean myTurn = false;

    public Client(String host, int port, String name) {
        this.host = host; this.port = port; this.playerName = name;
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    private void createAndShowGUI() {
        frame = new JFrame("网络五子棋 - " + playerName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        gamePanel = new GamePanel();
        frame.add(gamePanel, BorderLayout.CENTER);

        // Right panel: chat + controls
        JPanel right = new JPanel(new BorderLayout());
        chatArea = new JTextArea(20, 25);
        chatArea.setEditable(false);
        right.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel chatBottom = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        chatInput.addActionListener(e -> sendChat());
        chatBottom.add(chatInput, BorderLayout.CENTER);
        JButton sendBtn = new JButton("发送");
        sendBtn.addActionListener(e -> sendChat());
        chatBottom.add(sendBtn, BorderLayout.EAST);
        right.add(chatBottom, BorderLayout.SOUTH);

        JPanel ctl = new JPanel();
        undoBtn = new JButton("悔棋");
        undoBtn.addActionListener(e -> requestUndo());
        replayBtn = new JButton("复盘");
        replayBtn.addActionListener(e -> startReplay());
        newGameBtn = new JButton("新开局");
        newGameBtn.addActionListener(e -> resetLocal());
        ctl.add(undoBtn); ctl.add(replayBtn); ctl.add(newGameBtn);
        right.add(ctl, BorderLayout.NORTH);

        frame.add(right, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // connect network after UI visible
        new Thread(this::connect).start();
    }

    private void connect() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            // send name
            out.println("NAME:" + playerName);

            // listen thread
            new Thread(this::listen).start();
            appendChat("已连接到服务器 " + host + ":" + port);
        } catch (IOException e) {
            showError("连接失败: " + e.getMessage());
        }
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
            }
        } catch (IOException e) {
            appendChat("与服务器的连接已断开: " + e.getMessage());
        }
    }

    private void handleServerMessage(String line) {
        if (line.startsWith("START:COLOR:")) {
            String c = line.substring("START:COLOR:".length());
            if ("BLACK".equalsIgnoreCase(c)) {
                myColor = 1; myTurn = true;
                appendChat("游戏开始，你是黑方（先手）。");
            } else {
                myColor = 2; myTurn = false;
                appendChat("游戏开始，你是白方（后手）。");
            }
            state.reset();
            gamePanel.repaint();
            return;
        }
        if (line.startsWith("CHAT:")) {
            appendChat("对方: " + line.substring(5));
            return;
        }
        if (line.startsWith("MOVE:")) {
            String body = line.substring(5);
            String[] p = body.split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]);
            int color = (myColor == 1) ? 2 : 1; // opponent color
            state.place(x, y, color);
            myTurn = (color != myColor);
            gamePanel.repaint();
            if (state.checkWin(x,y)) {
                appendChat("对方在 " + x + "," + y + " 获胜。");
                JOptionPane.showMessageDialog(frame, "对方获胜。");
            }
            return;
        }
        if (line.equals("UNDO_REQUEST")) {
            int opt = JOptionPane.showConfirmDialog(frame, "对方请求悔棋，是否同意？", "悔棋请求", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) {
                out.println("UNDO_ACCEPT");
                // apply undo locally when server forwards accept back to opponent
                // For simplicity: we perform undo on both sides after forwarding (server will forward)
            } else {
                out.println("UNDO_DENY");
            }
            return;
        }
        if (line.equals("UNDO_ACCEPT")) {
            // 对方同意悔棋，移除最后一步（对方）和自己最后一步
            // 规则：双方各退一步（即局面退回到上一个双步），但这里实现为只退最近一步（可根据需求调整）
            boolean ok = state.undoLast();
            if (ok) appendChat("悔棋已被接受，已悔一手。");
            else appendChat("无法悔棋。");
            gamePanel.repaint();
            return;
        }
        if (line.equals("UNDO_DENY")) {
            appendChat("对方拒绝悔棋。");
            return;
        }
        if (line.startsWith("GAME_OVER:")) {
            String winner = line.substring("GAME_OVER:".length());
            appendChat("游戏结束，获胜方: " + winner);
            JOptionPane.showMessageDialog(frame, "游戏结束，获胜方: " + winner);
            return;
        }
        if (line.equals("REPLAY_START")) {
            appendChat("对方请求进入复盘模式。");
            // 本示例中复盘只作用于本地UI控制
            return;
        }
        // Unknown messages: show raw
        appendChat("收到: " + line);
    }

    private void appendChat(String t) {
        chatArea.append(t + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void sendChat() {
        String t = chatInput.getText().trim();
        if (t.isEmpty()) return;
        out.println("CHAT:" + t);
        appendChat("我: " + t);
        chatInput.setText("");
    }

    private void requestUndo() {
        out.println("UNDO_REQUEST");
        appendChat("已发送悔棋请求，等待对方同意...");
    }

    private void startReplay() {
        // Simple local replay: step through moves list
        List<Move> moves = state.getMoves();
        if (moves.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "当前无棋步可复盘。");
            return;
        }
        // disable inputs during replay
        new Thread(() -> {
            try {
                gamePanel.setReplayMode(true);
                state.reset();
                gamePanel.repaint();
                Thread.sleep(500);
                for (Move m : moves) {
                    Thread.sleep(600);
                    state.place(m.x, m.y, m.color);
                    gamePanel.repaint();
                }
                JOptionPane.showMessageDialog(frame, "复盘结束。");
            } catch (InterruptedException e) { /* ignored */ }
            finally {
                gamePanel.setReplayMode(false);
            }
        }).start();
    }

    private void resetLocal() {
        state.reset();
        gamePanel.repaint();
    }

    private void showError(String msg) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, msg));
    }

    // 游戏面板内嵌
    private class GamePanel extends JPanel {
        static final int CELL = 30;
        static final int OFFSET = 20;
        private boolean replayMode = false;

        GamePanel() {
            setPreferredSize(new Dimension(GameState.SIZE*CELL + OFFSET*2, GameState.SIZE*CELL + OFFSET*2));
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (replayMode) return;
                    if (myColor == 0) {
                        appendChat("尚未分配颜色，等待开局。");
                        return;
                    }
                    if (!myTurn) {
                        appendChat("现在不是你的回合。");
                        return;
                    }
                    int mx = e.getX() - OFFSET;
                    int my = e.getY() - OFFSET;
                    int x = Math.round((float)mx / CELL);
                    int y = Math.round((float)my / CELL);
                    if (!GameState.inBounds(x,y)) return;
                    synchronized (state) {
                        if (state.at(x,y) != 0) {
                            appendChat("该位置已有棋子。");
                            return;
                        }
                        boolean ok = state.place(x,y,myColor);
                        if (!ok) { appendChat("落子失败。"); return; }
                        // notify server/opponent
                        out.println("MOVE:" + x + "," + y);
                        myTurn = false;
                        gamePanel.repaint();
                        if (state.checkWin(x,y)) {
                            appendChat("你获胜！");
                            out.println("GAME_OVER:" + (myColor==1 ? "BLACK" : "WHITE"));
                            JOptionPane.showMessageDialog(frame, "你获胜！");
                        }
                    }
                }
            });
        }

        public void setReplayMode(boolean v) { replayMode = v; }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // background
            g.setColor(new Color(222,184,135));
            g.fillRect(0,0,getWidth(),getHeight());
            // draw grid
            g.setColor(Color.BLACK);
            for (int i=0;i<GameState.SIZE;i++) {
                int x = OFFSET + i*CELL;
                g.drawLine(OFFSET, OFFSET + i*CELL, OFFSET + (GameState.SIZE-1)*CELL, OFFSET + i*CELL);
                g.drawLine(OFFSET + i*CELL, OFFSET, OFFSET + i*CELL, OFFSET + (GameState.SIZE-1)*CELL);
            }
            // draw moves
            for (int y=0;y<GameState.SIZE;y++) {
                for (int x=0;x<GameState.SIZE;x++) {
                    int c = state.at(x,y);
                    if (c != 0) {
                        int px = OFFSET + x*CELL;
                        int py = OFFSET + y*CELL;
                        if (c==1) g.setColor(Color.BLACK);
                        else g.setColor(Color.WHITE);
                        g.fillOval(px-10, py-10, 20, 20);
                        g.setColor(Color.BLACK);
                        g.drawOval(px-10, py-10, 20, 20);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("用法: java Client <serverHost> <port> <playerName>");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];
        new Client(host, port, name);
    }
}