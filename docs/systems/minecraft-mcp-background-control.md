# Minecraft MCP 后台接管、鼠标释放与帧率

核对日期：2026-10-21。适用于原始项目 dev 的 Minecraft 26.1.2 开发客户端。主入口见 [Minecraft MCP](minecraft-mcp.md)
，结构化操作见 [GUI、玩家、实体与方块操控](minecraft-mcp-controls.md)。

## 2026-11-01 开发测试窗口与鼠标边界

用户明确要求：首要避免抢实体鼠标；测试不得反复前置或恢复焦点，启动仅展开一次，防最小化与后台运行不能通过抢焦点实现。此要求覆盖旧的保持／恢复焦点操作规则，具体规则见[后台输入与测试边界](../standards/workflow.md#后台输入与测试边界)。

已确认两类源码路径：项目 BenchWindow 在最小化后每 tick restore/maximize，HordeTideAcceptanceScenario 在失焦时 focus；Photon 所属性能观察器还启动了每 500ms 调用 ShowWindowAsync(3)／SetForegroundWindow 的脚本。Windows 官方定义前者会激活窗口，不能只删除后者；[ShowWindow 文档](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-showwindow)与[SetWindowPos 文档](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowpos)是原生激活副作用的维护依据。

当前项目修复限定开发环境，普通 Client 与 BenchClient 共用，不要求 MCP control_mode 已进入：

- BenchWindow 仅观察焦点，场景不再直接聚焦、反复还原或最大化，不修改 Options 或伪造输入时间戳。
- DevelopmentWindowPolicy 仅对当前 Windows 游戏窗口安装本地消息链，移除最小化按钮并拦截 SC_MINIMIZE，保留关闭与原消息链；一次使用 SWP_NOACTIVATE／SWP_NOZORDER 铺满工作区，不抢前台。不将工作区铺满伪称 GLFW 原生 maximized=true；不保证拦截外部程序直接调用原生最小化或系统级桌面行为。
- 开发节流 Mixin 取消 WINDOW_ICONIFIED／SHORT_AFK／LONG_AFK 的额外帧率限制，开发失焦暂停路径取消；用户 maxFps、VSync 和 Options 文件保持，不把取消限流等同于硬件性能提升或保证达到指定 FPS。
- 开发鼠标 Mixin 在原生窗口失焦时拒绝自动 grabMouse；真人聚焦后点击仍使用原版鼠标控制，不全局永久禁用玩家输入。
- Photon 专属脚本修复及本轮实例停止证据由其所属工作区维护；本页仅记录跨工作区通用规则，不复制分支验收。

本节为源码实现与维护边界，构建和实机结果以本次修改记录为准；未运行的前后台、最小化与 AFK 行为不得标为已验收。下列 2026-10-21／22 内容保留其 MCP 历史范围，不再作为测试必须维持焦点的要求。

## 当前鼠标契约（2026-10-22）

已接入[独立仓库固定版本](minecraft-mcp-artifacts.md#历史接入记录)。保留开发默认激活，enter／exit 不改变系统鼠标捕获，取消每 tick 持续释放；模拟输入仅走内部回调，在同步作用域内阻止 grabMouse 居中，真人点击保留原版捕获。显式 release_mouse 只释放一次。本次未构建或运行验收；下方持续释放与历史实测属于旧实现，不代表当前版本。

## 2026-10-21 现象与原因（历史）

鼠标接管与后台限帧是两个独立问题。退出接管不应把操作系统鼠标强制绑回游戏；接管期间没有真人输入也不应被当成 AFK
后额外节流。先分别读取控制模式、窗口活动状态、GUI 和帧率原因，不以“桥接已断开”判断接管已经退出。

### 退出后鼠标被抓取

已确认路径是 common 的 ControlModeHelper.exitMcpControlMode。旧实现无 GUI 时反射调用 grabMouse／lockCursor，LWJGL2 分支调用
setGrabbed (true)，会在退出时主动恢复抓取。不是 stdio MCP 本身绑定鼠标，也不能只靠关闭 bridge 解决游戏内状态。

修复契约：退出清理 MCP 控制状态与模拟输入，释放鼠标，不主动 grab／lock；由用户主动点击游戏恢复原版鼠标操作。存在 GUI 时仍按
GUI 的鼠标规则处理。不能通过强写 grabbed=true、强制 GLFW 隐藏／禁用光标或把窗口抢到前台来“恢复控制”。

对应源码：ControlModeHelper.java（历史引用，当前工作区缺失：`../../modules/minecraft-mcp/packages/common/src/main/java/xyz/langyo/minecraft/mcp/common/ControlModeHelper.java`）
。已移除退出路径中的主动抓取，退出保持普通光标；客户端 Pre tick 在接管期间持续释放鼠标，防止切入世界或关闭 GUI 后重新抓取。实际验收见文末。

### 原版后台与 AFK 节流

本轮对 Minecraft 26.1.2 FramerateLimitTracker 的核对结果：

| 原因                  | 额外限制 |
|-----------------------|----------|
| AFK 无输入超过 60 秒  | 30 FPS   |
| AFK 无输入超过 600 秒 | 10 FPS   |
| 窗口最小化            | 10 FPS   |
| 主菜单                | 60 FPS   |

这些值是当前版本源码分支，不应套用到其他 Minecraft 版本。用户设置的最大帧率、垂直同步、硬件负载和优化 Mod 的限制仍可能进一步影响实际帧率。

本轮原始客户端 options 快照为
maxFps=260、enableVsync=false、inactivityFpsLimit=afk、pauseOnLostFocus=false。快照仅描述当时配置，不意味着其他工作区相同，也不应覆盖用户后续设置。关闭失焦暂停并不会关闭
AFK 或最小化限帧。

修复限定在开发环境且 MCP 接管有效时，使 getThrottleReason 返回 NONE；继续保留玩家配置的最大帧率与垂直同步。退出接管后条件自然失效，原版节流立即恢复，不修改或保存
Options，也不伪造真人输入来刷新时间戳。

对应源码：FramerateLimitTrackerMixin.java（历史引用，当前工作区缺失：`../../modules/minecraft-mcp/packages/mods/26.1.2/neoforge/src/main/java/xyz/langyo/minecraft/mcp/mod/mixin/client/FramerateLimitTrackerMixin.java`）
。Mixin 已编译并由重启后的开发客户端加载；接管和退出分支取得运行证据，完整边界见文末。

### 优化 Mod 的独立限制

本轮已核对版本为 Sodium 0.9.2、Iris 1.11.4。在已查清单中未发现 Dynamic FPS；这不等于排除所有其他来源的后台节流。已读取的 Sodium 配置没有独立限帧项；其 JarJar 内部源码未完成解析，不把“未发现”写成已排除。未改动 Sodium／Iris 配置。

处理顺序：

1. 先读当前加载的 Mod 版本、实际配置和日志，不按网上默认值猜测。
2. 原版 getThrottleReason 已为 NONE 仍限帧时，再检查优化 Mod 是否另有后台、失焦、最小化或省电分支，以及 VSync、窗口系统和 GPU
   负载。
3. 仅当实际发现某 Mod 独立节流时，记录触发条件和配置项，按用户授权局部处理；不预先删除 Mod、重写 Sodium／Iris 配置或批量关闭优化。
4. 区分刻意限帧与渲染性能不足。FPS 低但没有限帧分支，不应承诺取消节流就能达到 configured maxFps。

## 诊断与操作流程

先核对当前实例归属：原始项目端口 19878；其他独立工作区为 19876、19877。只连接原始实例，检查 status
的端口、客户端身份及工作目录。不得扫描、启动、关闭或操控其他工作区，不查找、恢复或唤醒已归档 AI。

控制链为 Codex → stdio bridge → 本地游戏 HTTP。关闭 stdio 进程只断开桥接，不保证游戏端收到
exit_control_mode。正常结束先显式退出游戏内接管，再读回 control_mode=false；异常断开时重新连接同一实例核对状态，不能盲目假定已释放。

需要模拟操作时先检查是否接管、当前 GUI
与目标；使用游戏内部接口，避免操作系统鼠标键盘抢占。完成或失败后释放本次模拟的按键／鼠标按钮，防止持续移动或攻击。退出鼠标保持可用于其他应用，人工恢复视角控制由用户主动点击。

## 运行验收清单

以下是可复用的验收步骤；本轮实际执行范围见文末。构建、启动和游戏控制依当前用户授权执行；先完成全部相关代码修改及 IDEA 检查，再集中验证。

1. 核对原始项目 dev、IDEA 运行配置、端口 19878，保留已有世界和用户设置；需要可写测试时使用已授权测试世界。
2. 读取接管前配置与原版节流原因；记录最大帧率、VSync、窗口状态和当前 GUI，不写 options.txt。
3. 进入接管并执行一次内部模拟操作，确认窗口失焦后操作仍落在目标客户端，不抢系统鼠标。
4. 在接管中检查 AFK 超过 60 秒及最小化场景：原版 getThrottleReason 应为 NONE；不把偶发 FPS 数字当作原因判定。超过 600
   秒的长期场景单独记录，未等待或未验证就标未执行。
5. 在无 GUI、有 GUI、窗口失焦三种状态显式退出接管，读回 false，检查没有 grab／lock 调用，系统鼠标可离开游戏；用户主动点击后原版操作恢复。
6. 退出后确认原版 AFK／最小化分支恢复、最大帧率和 VSync 配置保持不变；再区分优化 Mod 是否独立限帧。
7. 断开并重新连接 stdio，核对仍是同一游戏实例；验证状态恢复流程，不用断开本身充当退出证据。

每项分别记录源码诊断、编译、运行加载、输入结果、鼠标行为和帧率原因。未进入游戏、未满足 AFK
时长、调试暂停、客户端未加载新类等情况必须单列，不以静态检查或工具返回成功替代。

## 回滚与维护

修复只涉及本仓库 MCP common 退出逻辑和当前版本客户端 Mixin／其注册。主任务记录实际备份或提交；回滚仅恢复本次相关补丁，不恢复混合快照、不覆盖其他工作区或用户现有改动。

需要重新加载修改时通过对应 IDEA 流程，遵守当次授权。回滚后重新检查接管退出与原版节流；不要为了回滚写用户 Options，因为方案不保存
Options。若曾另行手动调节配置，仅恢复有明确备份的对应项，不假定默认值就是用户原值。

升级 Minecraft 或优化 Mod 时重新通过 IDEA 核对 FramerateLimitTracker
方法、枚举、注入注册和实际第三方节流分支。维护目标是在开发接管期间取消额外不活动节流，保留用户性能选择；生产环境不启用该开发例外。

## 本文状态

本文件与配套 Skill 由 IDEA 新增。三份修改 Java 统一静态检查无错误；IDEA 项目构建成功，未运行 Data。修复基线为 refs/backups/before-mcp-background-control-20261021。

重新通过 IDEA Client 启动，MC_MCP_PORT=19878；调试进程 PID 43400、user.dir 为原始项目 run/client。新 Mixin 运行返回表明已加载。其他工作区未修改或控制。

| 场景 | 实际结果 |
|---|---|
| 开发接管、自然无真人输入 | control=true，reason=NONE，limit=maxFps=260；mouseGrabbed=false、GLFW_CURSOR_NORMAL=212993 |
| 610秒无输入条件注入 | 接管 NONE／260／非重度节流；退出 LONG_AFK／10 |
| 61秒无输入条件注入 | 退出 SHORT_AFK／30 |
| iconified=true 条件注入 | 接管 NONE／260；退出 WINDOW_ICONIFIED／10 |
| 菜单、新输入时间戳条件注入 | 退出 OUT_OF_LEVEL_MENU／60 |
| 测试世界无GUI、窗口失焦，真实 MCP exit_control_mode | 返回 false；随后读回 screen=null、control=false、grabbed=false、cursor=212993、forward=false、focused=false |
| 退出后再次写入输入 | CONTROL_REQUIRED；release 仍可执行 |

时间戳和 iconified 字段在 Render thread 的临时断点中注入，分别恢复原值0和false后继续运行；所有本次临时断点均已移除。这是已加载代码的分支验收，不等同于持续最小化或等待610秒的 FPS 性能验收。GLFW 最小化调用后实读仍为 iconified=false／native=0，因此没有把真实最小化场景记为通过。未替用户进行人工点击恢复鼠标的验收，也未测量硬件实际260 FPS或全部优化 Mod 组合。

测试存档 MCP Controls 20261021-sim 中请求保持前进200 tick后退出；前进键在下一客户端tick清理，保留短暂惯性，未持续前进。stdio 断开与重连均只连接19878，未触发自动退出接管；结束时明确退出并核对false。用户最大帧率260和VSync=false保持不变，修复未保存Options。
