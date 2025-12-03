package client;

import controller.GameController;
import controller.OfflineGameController;
import model.GameModel;
import view.GameView;

/**
 * 客户端入口：支持在线模式（连接服务器）与离线模式（本地双人）。
 *
 * 用法:
 * 1) 在线（连接服务器）:
 *    java client.ClientApp <serverHost> <port> <playerName>
 *
 * 2) 离线（本地双人）:
 *    java client.ClientApp offline [playerName]
 */
public class ClientApp {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法:");
            System.out.println("  在线: java client.ClientApp <serverHost> <port> <playerName>");
            System.out.println("  离线: java client.ClientApp offline [playerName]");
            System.exit(1);
        }

        if ("offline".equalsIgnoreCase(args[0])) {
            String name = (args.length >= 2) ? args[1] : "Local";
            model.GameModel model = new model.GameModel();
            view.GameView view = new view.GameView();
            // 使用离线控制器
            new controller.OfflineGameController(model, view, name);
            return;
        }

        // 在线模式 (原有用法)
        if (args.length < 3) {
            System.out.println("在线模式用法: java client.ClientApp <serverHost> <port> <playerName>");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];

        model.GameModel model = new model.GameModel();
        view.GameView view = new view.GameView();
        new controller.GameController(model, view, host, port, name);
    }
}