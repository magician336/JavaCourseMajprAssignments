# Java 网络五子棋（Gomoku）README

版本说明
- 语言：Java（JDK8+）
- GUI：Swing
- 架构：MVC（Model-View-Controller）
- 网络：TCP Socket（Server 做配对 + 消息中继）
- 功能：联机对局（在线模式）、本地双人对弈（离线模式）、聊天、悔棋（请求/应答）、胜负判断、复盘、开局重置

一、项目目标
提供一个可运行的教学/演示级网络五子棋实现，重点说明：
- 清晰的 MVC 分层；
- 简明文本协议便于调试与扩展；
- 支持在线（通过 Server 配对）与离线（本机双人）两种模式；
- 包含基础实用功能：聊天、悔棋、胜负检测与复盘。

二、主要特性
- 开局配对与颜色分配（黑/白，黑方先手）
- 落子同步（MOVE）
- 文字聊天（CHAT）
- 悔棋流程（UNDO_REQUEST / UNDO_ACCEPT / UNDO_DENY）
- 胜负判断（五子连珠）
- 复盘（本地从棋谱回放）
- 离线（local）模式：不依赖 Server 的本地双人对局

三、目录与主要文件
- model/
  - GameModel.java —— 棋局数据、落子、悔棋、胜负检测、事件广播
  - Move.java —— 棋步数据结构
- view/
  - GameView.java —— Swing GUI，回调接口（BoardClick、Chat、Control）
- controller/
  - GameController.java —— 在线控制器（网络通信、协议处理）
  - OfflineGameController.java —— 离线控制器（本地双人）
- client/
  - ClientApp.java —— 程序入口，支持在线/离线模式启动参数
- Server.java —— 简易配对与消息中继服务器（文本协议）

四、通信协议（文本行协议）
- NAME:<playerName>
- START:COLOR:BLACK 或 START:COLOR:WHITE
- MOVE:x,y
- CHAT:<text>
- UNDO_REQUEST
- UNDO_ACCEPT / UNDO_DENY
- GAME_OVER:BLACK / GAME_OVER:WHITE
- RESET
- REPLAY_START / REPLAY_END（保留，可扩展）
说明：协议为简单明文行消息（UTF-8），每条消息以换行分隔。建议在以后改为 JSON 或带消息 ID 的结构以增强可靠性。

五、编译（命令行）
在项目根目录（src 文件按包结构放置）：
1. 编译所有源文件：
   javac model/*.java view/*.java controller/*.java client/*.java Server.java
2. 启动服务器（在一台或同一台机器的不同终端）：
   java Server 5000
   （若不指定端口默认 5000）
3. 启动两个在线客户端（不同终端）：
   java client.ClientApp localhost 5000 Alice
   java client.ClientApp localhost 5000 Bob
4. 启动离线客户端（本机双人，不需要 Server）：
   java client.ClientApp offline [playerName]
   例：java client.ClientApp offline LocalPlayer

六、在 Eclipse 中运行（简要）
1. 在 Eclipse 中创建 Java 项目并导入 src 目录（保持 package 结构 model/view/controller/client）。
2. 确保项目编码使用 UTF-8（Window → Preferences → Workspace 或 右键项目 → Properties → Resource）。
3. 运行 Server：
   - 右键 Server.java → Run As → Java Application（或 Run Configurations 指定 Program arguments）。
4. 运行客户端（在线）：
   - 创建 Run Configuration，Main class = client.ClientApp，Program arguments = localhost 5000 Alice；新建另一个实例为 Bob。
5. 运行客户端（离线）：
   - Program arguments = offline LocalPlayer

七、主要实现要点（摘要）
- MVC：Model 保持棋盘与棋谱并发布事件；View 只渲染界面并通过回调暴露用户动作；Controller 调度 Model 与 View 并处理网络协议。
- EDT（Event Dispatch Thread）：所有 Swing UI 创建与更新必须在 EDT 上执行。View 构造同步化（invokeAndWait 或 isEventDispatchThread 检查）以避免 Controller 在 GUI 未就绪时访问组件。
- 线程模型：
  - 客户端：网络监听在后台线程，接收到消息后通过 SwingUtilities.invokeLater 回切到 EDT 处理 UI 更新。
  - Server：accept 循环在主线程，配对后为会话创建转发线程；使用 ExecutorService 管理线程。
- 悔棋同步：接收方在同意悔棋时立即在本地执行 undoLast() 并发送 UNDO_ACCEPT；发起方收到 UNDO_ACCEPT 后也执行 undoLast()，以保证双方一致（短期方案）。长期建议由 Server 作为权威执行棋谱回退并广播结果。

八、常见问题与排查
- 无法配对/消息未转发：
  - 确认 Server 已启动并监听正确端口；
  - 查看 Server 控制台是否打印配对（matching）与转发日志（"[Alice -> Bob] ..."）；
  - 确认客户端连接地址与端口一致（例如 localhost vs 127.0.0.1）；
  - 检查防火墙或端口占用。
- NPE（boardPanel 为 null）：
  - 原因：View 异步创建 GUI，而 Controller 在 GUI 准备好之前访问组件。解决方法：View 使用 invokeAndWait 同步创建或 Controller 在 ready 后再绑定。
- 悔棋不同步：
  - 检查 UNDO_REQUEST/UNDO_ACCEPT 是否在 Server 控制台被转发；确认同意方在同意时本地执行 undoLast() 并发出 UNDO_ACCEPT。
- 中文乱码：
  - 确保用 UTF-8 编码编译并设置 JVM 参数 -Dfile.encoding=UTF-8（可在 Run Configurations → VM arguments 中设置）。
- UI 卡顿：
  - 确保耗时操作（网络、复盘 sleep）在后台线程或 SwingWorker 中执行，不在 EDT 上阻塞。

九、已知限制与改进建议
- 协议为明文且无消息 ID/ACK，不可靠网络场景下可能导致状态不一致。建议切换到 JSON + messageId + ack 或 RPC。
- Server 目前不维护棋局权威（仅中继），存在客户端作弊风险。建议将 GameModel 放到 Server，客户端只发送请求，Server 验证并广播状态。
- 无鉴权与加密（明文传输）。生产环境需用 TLS/认证。
- 无断线重连与断线判负策略，可增强用户体验。
- 建议引入构建工具（Maven/Gradle）、日志框架（SLF4J + Logback）与单元测试（JUnit）。

十、测试建议
- 单元测试：为 GameModel 编写 JUnit 测试（落子合法、undo 边界、连珠检测）。
- 集成测试：本地启动 Server，启动两个客户端并通过脚本/人工测试聊天、落子、悔棋、重置、复盘场景。
- 网络测试：使用 Wireshark/tcpdump 验证消息是否正确往返；可在高延迟/丢包场景模拟以评估协议鲁棒性。

十一、扩展路线（参考优先级）
1. 高优先级
   - 将棋局逻辑迁移到 Server（Server 为权威）。
   - 协议重构为 JSON，并加入消息 ID 与 ACK/重试。
   - 支持 TLS / 用户认证。
2. 中优先级
   - 断线重连、断线判负、房间与观战功能、棋谱保存（SGF/JSON）。
   - 改进悔棋策略（双方各退一步或服务器统一回退）。
3. 低优先级
   - 添加 AI 对手（Minimax/启发式/Monte-Carlo）。
   - Web 前端（WebSocket）或移动端客户端。
   - UI 美化、动画、主题、国际化。

十二、贡献与代码风格
- 建议使用包结构（model/view/controller/client）并保持类职责单一。
- 把常用协议字符串抽成常量类或枚举，消除硬编码字符串。
- 编写单元测试覆盖核心逻辑（GameModel）。
- 使用 Git 进行版本管理，提交信息清晰描述改动。

十三、许可
- 示例代码用于教学与学习，按 MIT/无版权限制使用（请在具体项目中替换为合适许可证并注明来源）。

十四、联系方式 / 进一步帮助
- 如需我提供：
  - 把 GameModel 迁移到 Server 的具体实现补丁；
  - 将协议改为 JSON 的示例实现；
  - 把项目打包成 Maven/Gradle 模板；
  - 添加 AI（人机对局）控制器；
  请在 issue 中说明，我将给出相应代码与指导。

谢谢使用！希望这份 README 能帮你快速上手、部署与扩展该网络五子棋项目。