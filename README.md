```markdown
# Java 网络五子棋（MVC 实现）

说明
- 本示例将客户端改为 MVC 架构：model（model/GameModel.java）、view（view/GameView.java）、controller（controller/GameController.java）。
- Server.java 为简易配对与消息中继服务器（文本协议）。
- 协议（文本，以换行分隔）：
  - NAME:<playerName>          客户端连接后先发送自己的名称
  - START:COLOR:BLACK/WHITE    服务器分配颜色并通知
  - MOVE:x,y                   落子消息
  - CHAT:文本                  聊天消息
  - UNDO_REQUEST               请求悔棋
  - UNDO_ACCEPT / UNDO_DENY    悔棋同意/拒绝
  - GAME_OVER:COLOR            通知获胜方
  - REPLAY_START               复盘开始（示例里未使用额外交互）

目录（示例）
- model/Move.java
- model/GameModel.java
- view/GameView.java
- controller/GameController.java
- client/ClientApp.java
- Server.java

编译
- 建议在项目根目录下按包目录结构保存文件（model、view、controller、client 目录）。
- 编译（在项目根目录）：
  javac model/*.java view/*.java controller/*.java client/*.java Server.java

运行
1. 启动服务器（默认端口 5000）：
   java Server 5000

2. 启动两个客户端（不同终端）：
   java client.ClientApp localhost 5000 Alice
   java client.ClientApp localhost 5000 Bob

功能实现
- GUI（Swing）绘制棋盘、聊天和控制按钮。
- 悔棋：一方点击“悔棋”按钮会发送 UNDO_REQUEST，对方收到后可以同意或拒绝；若同意，服务器会把 UNDO_ACCEPT 中继给请求方并由客户端在本地执行 undoLast（示例实现为悔一手）。
- 复盘：控制器从模型读取 moves 列表并按顺序在本地回放。
- 胜负判断：在模型中实现，落子后会触发 gameover 事件。

已知限制 & 可扩展项
- 服务器仅做消息中继，未在服务器端验证棋步合法性（可在将来将规则放在服务器以防作弊）。
- 协议为明文，未加入鉴权、断线重连等机制。
- 悔棋策略为“悔一手”，可改为“双方各退一步”或由服务器统一回退。
- 可以实现棋谱保存/加载（SGF 或自定义格式）、观战、房间/房号、UDP/TCP 混合优化或 WebSocket 替代等。

如果你希望我：
- 把悔棋规则改为双方各退一步并同步实现（客户端+协议说明），或
- 将合法性校验与胜负判断迁移到服务器端（防作弊），或
- 添加棋谱导出/导入功能（例如保存到 moves.txt 或 SGF），
我可以在此基础上直接修改并给出更新后的文件。
```