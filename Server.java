import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * 修正后的简单匹配服务器：每两个连接配对成一局并中继消息（文本协议）。
 * 使用方式: java Server [port]
 *
 * 要点：
 * - 在 accept 后即时读取客户端 NAME 行（同步），然后再进行配对，避免原来那种 put/take 的竞态/自取问题。
 * - 为每个已配对的客户端创建转发线程，转发时打印日志，便于排查。
 */
public class Server {
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final BlockingQueue<ClientHandler> waiting = new LinkedBlockingQueue<>();

    public Server(int port) { this.port = port; }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
        while (true) {
            Socket sock = serverSocket.accept();
            System.out.println("Client connected: " + sock.getRemoteSocketAddress());
            // 初始化流并读取 NAME 行（非异步），这样后面配对时我们已经知道 name 与 IO
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
                String line = in.readLine();
                String name = (line != null && line.startsWith("NAME:")) ? line.substring(5) : sock.getRemoteSocketAddress().toString();

                ClientHandler ch = new ClientHandler(sock, name, in, out);
                // 尝试从等待队列拿一个对手
                ClientHandler opponent = waiting.poll();
                if (opponent == null) {
                    // 没有等待者，加入队列等待被配对
                    waiting.put(ch);
                    System.out.println("等待配对: " + ch.name);
                } else {
                    // 找到对手，创建会话
                    System.out.println("匹配成功: " + ch.name + " vs " + opponent.name);
                    GameSession session = new GameSession(ch, opponent);
                    exec.submit(session);
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Accept-handling failed: " + e.getMessage());
                try { sock.close(); } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    private static class ClientHandler {
        final Socket socket;
        final String name;
        final BufferedReader in;
        final PrintWriter out;

        ClientHandler(Socket socket, String name, BufferedReader in, PrintWriter out) {
            this.socket = socket;
            this.name = name;
            this.in = in;
            this.out = out;
        }

        void send(String msg) {
            out.println(msg);
        }
    }

    private class GameSession implements Runnable {
        final ClientHandler a, b;

        GameSession(ClientHandler a, ClientHandler b) {
            this.a = a;
            this.b = b;
        }

        public void run() {
            System.out.println("New game session: " + a.name + " vs " + b.name);
            // 分配颜色
            a.send("START:COLOR:BLACK");
            b.send("START:COLOR:WHITE");

            // 启动两条转发线程
            exec.submit(() -> forward(a, b));
            exec.submit(() -> forward(b, a));
        }

        private void forward(ClientHandler from, ClientHandler to) {
            try {
                String line;
                while ((line = from.in.readLine()) != null) {
                    System.out.println("[" + from.name + " -> " + to.name + "] " + line);
                    to.send(line);
                }
            } catch (IOException e) {
                System.out.println("Forwarding stopped between " + from.name + " and " + to.name + ": " + e.getMessage());
            } finally {
                try {
                    to.send("CHAT:对方已断开连接");
                } catch (Exception ex) { /* ignore */ }
                try { from.socket.close(); } catch (IOException ignored) {}
                try { to.socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 5000;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        new Server(port).start();
    }
}