package Server;
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
	// port保存端口号
    private final int port;
    
    // ServerSocket 用于监听客户端连接
    private ServerSocket serverSocket;
    
    // ExecutorService 线程池接口 
    // CachedThreadPool 会根据需要创建新线程并复用空闲线程，适合连接数不确定但每个任务短暂的场景。
    private final ExecutorService exec = Executors.newCachedThreadPool();
    /*
     * 这是一个线程安全的队列，
     * 用于存放等待配对的 ClientHandler（等待对手加入）。
     * BlockingQueue 的好处是可以阻塞 put/take 操作（本代码用了 put 和 poll）
     */
    private final BlockingQueue<ClientHandler> waiting = new LinkedBlockingQueue<>();

    // 构造函数
    public Server(int port) { this.port = port; }

    public void start() throws IOException {
    	// 在指定端口创建一个监听套接字
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
        
        while (true) {
        	/*
        	 * accept() 是一个阻塞调用：
        	 * 当没有客户端连接时它会阻塞，
        	 * 直到有新的 TCP 连接到来，
        	 * 返回一个代表客户端连接的 Socket。
        	 */
            Socket sock = serverSocket.accept();
            
            // sock.getRemoteSocketAddress()：打印客户端地址
            System.out.println("Client connected: " + sock.getRemoteSocketAddress());
            
            // 初始化流并读取 NAME 行（非异步），这样后面配对时我们已经知道 name 与 IO
            try {
            	// 为Socket创建输入/输出流
            	// PrintWriter的第二个参数表示自动flush(每次println后自动刷新）
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
                
                //读取客户端发来的第一行文本
                String line = in.readLine();
                
                /*
                 * 解析第一行，如果以 "NAME:" 开头就把冒号后的字符串
                 * 作为客户端用户名，
                 * 否则用远端地址作为默认名字。
                 */
                String name = (line != null && line.startsWith("NAME:")) ? line.substring(5) : sock.getRemoteSocketAddress().toString();

                // 把 socket + IO 流 + name 封装成一个 ClientHandler 对象，方便后续传递与处理
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
                    
                    // 创建 GameSession（会话/对局）并提交给线程池执行
                    GameSession session = new GameSession(ch, opponent);
                    exec.submit(session);
                }
                /*
                 * 捕获初始化流或队列操作中的异常
                 * （例如读第一行时抛异常或 put 被中断），
                 * 并尝试关闭 socket 以释放资源。
                 */
            } catch (IOException | InterruptedException e) {
                System.err.println("Accept-handling failed: " + e.getMessage());
                try { sock.close(); } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    // 数据容器，封装每个连接所需的信息
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

    // 用于执行会话相关工作（分配颜色并启动消息转发）
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
                // 循环读取阻塞直到收到对方发来的新行或对方关闭连接
                while ((line = from.in.readLine()) != null) {
                	/*
                	 * 读取到每行后打印日志并调用 to.send(line) 
                	 * 将原文中继给对方（服务器做的是“中继/转发”，
                	 * 不会修改消息内容）
                	 */
                    System.out.println("[" + from.name + " -> " + to.name + "] " + line);
                    to.send(line);
                }
            } catch (IOException e) {
                System.out.println("Forwarding stopped between " + from.name + " and " + to.name + ": " + e.getMessage());
            }
            /*
             * finally 块尝试向对方发送 "CHAT:对方已断开连接"（告知对方对端断线），
             * 并关闭两个 socket（from.socket.close() / to.socket.close()）
             * 以结束会话并释放资源。
             */
            finally {
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