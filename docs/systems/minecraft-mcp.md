# Minecraft MCP

核对日期：2026-10-13。本文记录本地桥接结构、快速使用路径与本轮实际结果，避免每次游戏检查重复排查。项目技能入口为 [lobecorp-minecraft-mcp](../../.codex/skills/lobecorp-minecraft-mcp/SKILL.md)
；版本、工具配置或 Mod 改变时再核对对应源码。

## 当前结构与入口

- 项目客户端使用 Minecraft `26.1.2`、NeoForge `26.1.2.100` 和 Java `25`，通过 IDEA 的 `Debug` 配置启动。
- Minecraft MCP 源码现由本仓库的 [`modules/minecraft-mcp`](../../modules/minecraft-mcp/README.md) 管理，Gradle 子项目为 `:minecraft-mcp`；仅 Client 加载其源码，Data／Server 不加载 MCP Mod。Lobecorp 对它使用 `compileOnly`，正式 Lobecorp JAR 不内嵌 MCP 模组。
- 原 `run/client/.mcp-repair-build/` 保持原状，导入其 common、当前 NeoForge 模组、bridge 与四份本地源码修复。旧 JAR 已通过 IDEA 移至 `run/client/mcp-backups/2026-10-13-source-subproject/`，避免与源码重复加载；备份中的 JAR 元数据可读。
- bridge 保留原有 46 项工具；原 8 项 `effect_*` 之外补入 7 项工程打开、资源与视角入口，当前共 61 项工具。本轮 IDEA 编译、Data 与 bridge 构建成功，真实 stdio 发现确认 61 项；新增能力尚未游戏验收，详见修改记录。原截图工具同时扩展原生 GPU 读回及工程区域裁剪。接口与限制见 [Photon 特效 MCP](photon-effect-mcp.md)。已连接的旧 bridge 需重新连接才加载新入口；工具存在不表示游戏场景均已验收。

连接分为两层：Codex 连接 bridge 的标准 stdio MCP；bridge 再通过本地 HTTP 访问游戏 Mod。不能把 Mod 的 URL 当成标准 MCP 的
JSON-RPC/Streamable HTTP endpoint。

| 层次           | 当前接口                                 | 用途                                            |
|----------------|------------------------------------------|-------------------------------------------------|
| Codex → bridge | stdio MCP                                | 注册并调用 Minecraft 工具                       |
| bridge → Mod   | `GET http://127.0.0.1:<port>/api/status` | 检查 `ok=true`、`type=minecraft-mod` 与数字端口 |
| bridge → Mod   | `POST /api/cmd`，JSON `cmd` 与参数       | 调用 Mod 命令处理器                             |
| bridge → Mod   | `GET /api/screenshot`                    | 原图、坐标网格及图片尺寸                        |
| 调试页面/事件  | `/debug`、`/api/events`、`/api/calls`    | HTTP 调试资源与调用历史，不是 MCP SSE transport |

当前监听限定回环地址。Mod 端口配置只读取 JVM 系统属性 `mcp.port`、环境变量 `MC_MCP_PORT`，没有对应的 Mod TOML 或连接 token
配置。账号凭据属于 launcher 的独立配置；诊断连接时不得输出账号、access token 或 refresh token。

## 快速使用

客户端已经运行时，优先调用 `mcp__minecraft__ping`。收到 `pong` 即按本轮已授权范围继续；需要启动时沿已确认的 IDEA `Debug`
配置执行，不另用 MCP launcher 或终端启动第二个实例。

进入客户端后先最大化 Minecraft 游戏主窗口；用户已明确要求 Photon 编辑器嵌入游戏内，不创建独立 OS 编辑器窗口。已有独立宿主通过 `dockIntoGameWindow()` 切回游戏内，内部布局可再调用 `EditorWindow.maximizeWindow()`。实际最大化状态按游戏主窗验证，不能用内部布局标志代替。固定连续步骤合并为一次 MCP/API 或批处理，仅在边界、分支和失败时读取必要状态。新测试世界优先使用开发环境的 Create Test World 快速入口，已有目标世界则直接继续。当前入口为 `CreateWorldScreen.testWorld`，它预设测试规则并打开创建界面，还需执行创建动作。这些是用户明确要求的固定操作规则。

IntelliJ Debugger 的 `project_path` 原样使用 `available_projects` 返回的 `D:/xm/mod/Unitego-Well/Lobecorp`；本轮传入 `D:\xm\mod\Unitego-Well\Lobecorp` 得到 `project_not_found`，改用正斜杠形式后会话可用，不据此判定项目未打开。

`get_minecraft_status` 只查看已有连接。当前 bridge 将它放入 `MANAGEMENT_TOOLS`，处理时只调用 `checkAlive()`
；如果还没有缓存端口，会直接返回未连接，不触发 `discover()`。后台初次发现超时后反复调用它，可能一直得到 `connected=false`。
`ping` 属于非管理工具，会在未连接时触发扫描，适合首次握手与重新发现。

需要界面输入时使用当前 Mod 的控制模式流程；`enter_control_mode` 的用户激活条件仍由 Mod 约束。优先结合 `enumerate_widgets`
与截图定位当前控件，执行后重新读屏确认，再继续下一项操作；不能把“工具调用返回成功”当成“点中了预期按钮”或“输入已经生效”。

### 截图输出

本轮 `screenshot` 返回的是 MCP text 内容中的 JSON，而不是直接的 image 内容：

- `original`：原始 PNG data URL。
- `grid`：附坐标网格的 PNG data URL。
- `width`、`height`：PNG 图片像素尺寸。

先对 text 内容执行 `JSON.parse`，仅把图像字段传给图像输出接口；不要 `text(result)` 或打印 data URL/base64。使用
`functions.exec` 时可通过 `image(parsed.original)` 输出；在 Node REPL 中，解析 JSON 后把 data URL 解码为图片字节，再交给
`nodeRepl.emitImage`。尺寸可单独输出。根据当前工具实际返回结构解析，不能把 text JSON 误当成已经渲染的图片。

PNG 像素、窗口尺寸和 GUI 缩放坐标是不同量。当前版本的定位风险见下节，不能仅按比例推断成功。

## 2026-10-15 命令执行适配

已修复 [PlayerWorldHelper.sendCommand](../../modules/minecraft-mcp/packages/common/src/main/java/xyz/langyo/minecraft/mcp/common/PlayerWorldHelper.java) 在当前 26.1.2 客户端找不到命令方法的问题。原实现只尝试玩家的 `connection()` 方法、大小写不符的 `Connection` 字段及旧单机执行入口；当前玩家公开字段为 `connection`，Minecraft 已提供 `getConnection()`。

当前路径优先从 Minecraft 取得连接并调用原生 `sendCommand`。NeoForge 在此先处理 `sparkc` 等客户端命令，未处理时再按玩家身份与当前签名协议提交服务端；原生调用已提交或抛错时不会继续重发。其他既有反射代码未清理，没有新增旧版本兼容格式或适配分支。

通过实际 `mcp__minecraft__execute_command` 调用带斜杠的 `/sparkc profiler info` 与不带斜杠的 `time query gametime`，两次均返回 `sent=true, method=conn.sendCommand`，日志确认 Spark 状态与游戏时间反馈。IDEA 修改行格式化、诊断和编译通过，Data 退出 0；没有修改 bridge、全局 MCP 配置或工具协议。多人服务器、权限拒绝与签名参数命令没有现场验收，`sent` 仅为提交结果。下方旧 JAR 的失败记录保留历史含义，不再代表本次已验收的命令路径。

## 源码纳入前的实测与保留限制

以下连接、输入与崩溃记录属于此前使用旧 JAR 的检查证据；新子项目的实际检查见 [特效 MCP 检查记录](photon-effect-mcp.md#实际检查与限制)。未在本次重做的场景不能视为新源码已通过。

### 连接与启动

- 第二次 IDEA `Debug` 启动已进入 `TitleScreen`，`ping` 已实际得到 `pong`，`enter_control_mode` 已成功，截图也已返回有效图片
  JSON。
- 第一次 JBR 25 启动发生 native crash，证据文件为 `run/client/hs_err_pid27524.log`；重试成功。根因尚未确认，不能认定 MCP
  导致，也不能据此宣称 JBR 25 永久不可用。
- 第二次会话在进入世界后于 10:45:37 再次发生 native crash，证据为 `run/client/hs_err_pid43248.log`：Render thread、`EXCEPTION_ACCESS_VIOLATION`、`DirectMethodHandle$Holder.invokeStatic`。报告记录崩溃前约 0.33 秒重定义 `ClientRuntimeEvents`，随后发生 DEOPT 和 ChangeSingleStep；这里只确认时间顺序，没有确证重定义、调试操作或粒子代码是根因。Java 日志没有完整异常时应检查 `hs_err`，不能将会话消失误报为业务检查成功。
- 随后按当前控件树选择目标，已通过 `CreateWorldScreen` 创建并进入隔离新世界；客户端 tick
  断点已确认玩家存在与位置。伤害粒子显示及其他业务表现仍须独立验收，不能由连接或进入世界推定通过。
- 第三次沿相同配置启动并重新握手后，回到本轮创建的 `新的世界 (5)`，完成伤害显示、合并、显式位置、非文本内容、池复用及字体批次的基础检查；结果分别见[世界文本粒子](text-particles.md)与[屏幕伤害粒子](screen-damage-particles.md)。退出前清理测试对象和客户端实例、恢复临时配置与原断点，通过当前 PauseScreen 的保存退出按钮回到 TitleScreen，`exit_control_mode` 确认 false。客户端留在标题界面，测试世界保留。

### 按钮枚举与坐标

本轮 `get_screen_buttons` 返回重复按钮，部分 `label` 为空。尝试点击截图中的 `CreateTestWorld` 时，`click_button_index(3)` 与
`click(x=325, y=348)` 均实际命中 `ModsButton`；后者返回的 GUI 坐标为 `(162,174)`，与预期界面目标不匹配。

随后使用 `enumerate_widgets`，得到不重复的 `i/c/x/y/w/h/press` 控件列表，确认本次 `TitleScreen` 中索引 `3` 是 `ModsButton`
，索引 `4` 才是 `Plain/CreateTestWorld`。按当前列表的索引 `4` 已成功进入 `CreateWorldScreen`，再按该界面的索引 `1`
创建新世界。索引是当前界面的局部标识，界面变化后必须重新枚举；以上数值属于本轮证据，不能作为固定自动化模板。

重复枚举与坐标定位问题尚未修复；尚未确认坐标失败的根因属于缩放还是界面实现。遇到枚举/截图不一致或一次点击偏离预期时，立即重新获取当前界面；不要继续按同一索引、猜测坐标或重复点击推进流程。选择其他交互路径前先核对当前界面及实际实现，并保留任务原有授权边界。

本轮 `hotkey` 的 `CTRL,A` 和 `type_text` 仅返回输入回显，界面 `EditBox`
仍显示“新的世界”，未改成输入内容；最终新世界使用默认名称。因此输入工具回显只证明请求得到处理，文本框结果必须通过读屏、控件状态或其他已授权只读手段确认。

第三次会话中的 `press_key(key.keyboard.escape)` 返回 pressed，但截图及控件枚举仍无菜单；坐标点击也未选中世界列表。`execute_command` 的 tp 与 summon 均返回 `no command method found`，`player_class` 为 LocalPlayer，不能宣称命令执行成功。当前可用的控件 `click_button_index` 通过 `onPress.onPress` 实际打开界面和完成保存退出。

本轮已有 Debug 操作授权，因此通过 IDEA 核对当前 API，在客户端项目帧读取 WorldListEntry 的 levelId 确认为本轮测试世界后调用 `joinWorld()`；通过 `pauseGame(false)` 打开菜单，再恢复游戏执行并由 MCP 点击保存退出。服务端命令通过 Server thread 上的 `Commands.performPrefixedCommand` 执行；这些是本轮 Debug 路径，没有修复或替换 MCP 输入实现。后续保持相同授权和目标核对，不把任意世界列表首项当作测试世界。

### 端口降级

Mod 与 bridge 的默认端口范围均为 `9876` 至 `9000`，bridge 按降序探测 `/api/status`。但当前实际安装 JAR 的 `McpHttpServer`
构造器会把未配置的 `0` 立即改成 `9876`，`start()` 又将正数视为固定端口，所以自动遍历备用端口的分支实际上不可达。

因此 `9876` 被占用时，Mod 可能直接启动 HTTP 失败；不能假定它已经转到 `9875`。该源码问题已确认，但本轮没有修改或重建 MCP
Mod。只有故障证据确认端口冲突后，才评估在 IDEA 启动参数中显式指定扫描范围内的空闲端口；不要为一次 `connected=false`
自动改全局配置或桥接。

## Debug 与控制生命周期

同一客户端/Debug 会话只由一个执行者操作 Minecraft
输入、断点、继续、单步与求值。并行代理可只读源码、诊断或文档，不得同时操纵同一游戏界面。用户已授权的运行检查可沿既定流程完成；本技能不扩张原有授权。

临时断点记录本轮创建的标识及状态，收尾仅删除这些临时断点，并恢复本轮改动的断点状态；保留用户原有断点。客户端停在断点时先读取
Debug 状态，不为恢复 MCP 擅自继续用户暂停或断开调试器。涉及恢复、停止或重启时使用 IDEA/MCP，并遵守本轮会话归属。

本轮确认 `temporary=true` 的断点命中后仍然存在，不能把该参数视为自动清理保证。创建时保存返回的 `breakpointId`；完成本次求值后，先按该标识显式 `remove` 并确认删除，再执行已授权的 `resume`。否则下一 tick 可能再次命中，游戏主线程重新暂停后，Minecraft MCP 请求也可能挂起。不要用循环请求 MCP 代替读取 Debug 状态与清理断点。

多语句或含 lambda 的 Debug 片段应在项目源码栈帧执行，例如本轮 `ClientRuntimeEvents.java:77`；该行号属于本次定位证据，后续以当前源码与实际栈帧为准。停在依赖 JAR 或系统类帧时，lambda 动态编译可能缺少项目模块类路径，报 `net.minecraft.client` 不存在。此时移到项目源码帧再求值，不把调试上下文问题当成依赖缺失，不修改依赖或项目类路径。

第三次检查使用单表达式完成操作，没有再发生本轮前两次的崩溃；这不能证明复杂求值就是 native crash 根因。服务端对象在 Server thread 的项目 tick 帧修改，客户端配置与集合在客户端 tick 帧修改，渲染帧只读检查。避免在遍历活跃队列时发射／清空实例；断开世界等需要服务端继续运行的操作，在恢复执行后通过界面触发，避免 suspend-all 下等待另一个已暂停线程。

完成或交回交互时按任务退出控制模式；不能把退出控制模式、恢复游戏或移除断点等未执行操作报告为已完成。

### 退出控制后的鼠标抓取修复（2026-10-14）

已确认旧 ControlModeHelper.exitMcpControlMode 无论当前是否打开界面，都会将 mouseGrabbed 强写为 true 并设置 GLFW_CURSOR_DISABLED；Photon 仍在 ModularUIScreen 内时因此失去可用光标。进入时的 hadScreenOnEnter 不能代表退出时的实际界面，不能用其恢复状态。

当前退出路径读取实际 currentScreen：有界面时释放鼠标并清零累积位移；无界面时调用当前映射的原生 grabMouse／lockCursor，由原生判断窗口是否活动，不再强写抓取字段或隐藏光标。LWJGL2 的现有抓取入口保留。仅无界面退出时保留 waitingForRelease；不主动抢占操作系统焦点。

三个修改文件的 IDEA 编译通过；Data 日志为 BUILD SUCCESSFUL、19 文件、written=0，生成目录 Git 差异为空。重新启动 Client Debug 后，已在原世界的 Photon ModularUIScreen 实际退出控制：mouseGrabbed=false、GLFW_CURSOR_NORMAL=true、embedded=true。保存提示同时检查通过，最后一次专用 exit_control_mode 返回 false；未主动抢占操作系统焦点。其他界面、无界面抓取和跨窗口切换仍未做完整交互矩阵验收。

## 何时重新核对

常规检查优先读技能并走 `ping` 快速路径。只有首次握手失败、版本/Mod/bridge
变化、工具集合变化、截图结构变化或出现新的可重复故障时，再读取相关源码和配置；不每次重新遍历整个环境。

| 需要确认的内容                     | 本地证据入口                                                                                                                                      |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| NeoForge 初始化、延迟启动 HTTP     | `modules/minecraft-mcp/packages/mods/26.1.2/neoforge/src/main/java/xyz/langyo/minecraft/mcp/mod/ModDevMcpMod.java`                         |
| 系统属性/环境变量与端口            | `packages/common/src/main/java/xyz/langyo/minecraft/mcp/common/McpConfig.java`                                                                    |
| HTTP 路由、截图 JSON、端口构造逻辑 | 同目录 `McpHttpServer.java`，必要时通过 IDEA 读取实际安装 JAR 的对应类                                                                            |
| bridge 扫描与连接缓存              | `packages/minecraft-mod-mcp/src/discovery/scanner.ts`、`packages/minecraft-mod-mcp/src/api/modClient.ts`                                          |
| status 不重新发现、工具分发        | `packages/minecraft-mod-mcp/src/mcp/handlers.ts`                                                                                                  |
| stdio transport、工具集合及默认值  | `packages/minecraft-mod-mcp/src/mcp/transport.ts`、`packages/minecraft-mod-mcp/src/mcp/tools.ts`、`packages/minecraft-mod-mcp/src/mc/defaults.ts` |

表中 `packages/` 路径均相对 `modules/minecraft-mcp/`。源码与三份原始许可证已纳入仓库，`node_modules`、`dist`、构建和运行数据不纳入。源码/JAR 通过 IDEA/MCP 读取，不用命令行解包或自行反编译。只提取端口、协议等必要配置键，不打印秘密。源码子项目导入时只更新项目 `.codex/config.toml`；后续全局配置整理见下节。

IDEA 的 `Minecraft MCP Bridge` 配置运行 TypeScript 检查、构建与 stdio 工具发现，数量和能力以当次实际发现为准；`Minecraft MCP Effect Runtime` 只读取运行客户端的能力与工程状态。原图标已导入源码，`Minecraft MCP Import Assets` 仅为一次性迁移任务，拒绝覆盖已有图标，正常构建不依赖原修复工作树。

### Skill 与全局配置维护（2026-10-14）

全局 `C:/Users/wang_/.codex/config.toml` 的 Minecraft bridge 已从历史 `.mcp-repair-build` 副本切换到 `modules/minecraft-mcp/packages/minecraft-mod-mcp/dist/cli.js`，并设置同目录 `cwd`；项目覆盖原本已使用该入口，未修改。旧修复目录及其历史证据保留。

全局 Rider 保留 `rider` 和独立的 `rider-debugger`，移除同端口的旧 `jetbrains` SSE 别名。IDEA、语义索引、调试器与 Minecraft bridge 各有独立职责，不能因暂时连接失败合并或删除。

本次只核对配置、文件路径、已注册工具与编辑器信息，没有重连、重载、启动客户端或运行 bridge 验证。整理前当前会话注册 60 项 Minecraft 工具，缺 `effect_view`；此前源码构建的 61 项记录是历史证据，不等同于本次连接状态。配置修改不证明既有会话工具列表已更新。

### 历史：粉色模型激光制作（2026-10-13）

以下保留当时的制作与验收记录。2026-10-14 的独立清理任务已移除对应脚本、运行入口及激光试稿，见 [修改记录](changes.md)；不再按本节启动或恢复旧工程。当前通用工程操作使用 [Photon 特效 MCP](photon-effect-mcp.md)。

IDEA 的 `Minecraft MCP Model Laser` 运行 `:minecraft-mcp:createModelLaser`，在
`modules/minecraft-mcp/packages/minecraft-mod-mcp` 调用 `npm run model:laser -- all`。
当时入口为 `scripts/create-model-laser.mjs`，资产与变量配方为 `scripts/effects/model-laser.mjs`；两份文件现已清理。
客户端已进入所需世界、主窗最大化并开启控制模式后，一次批处理完成新建、配方、命名定义、保存及导出；编辑器保持嵌入游戏。
各步骤直接消费修改响应中的完整状态与 revision，不逐步轮询状态。

本轮已保存 `run/client/ldlib2/assets/lobecorp/the_queen_of_hatred/pink_laser` 的
`.mfxproj / .mfx / .mfxpack / .fxproj / .fx / .fxpack` 六份文件，原生保存／导出警告均为空。
前三份为单圆柱模型工程、定义与包；后三份为包含模型发射器和命名动画控制器的 FX 工程、定义与包。
白芯、粉色主体、外晕和流动由一个自定义着色器生成，不使用三层模型；材质引用
`lobecorp:the_queen_of_hatred/pink_laser`，对应 `assets/lobecorp/shaders/core/the_queen_of_hatred/pink_laser.json/.vsh/.fsh`。
FX 命名动画 `laser` 的实例变量为 `length`（默认 12，0.01～64 格）、`width`（默认 0.8，0.01～8 格完整直径）、
`intensity`（默认 1，0～1 透明强度）及 `enabled`（默认 true）。它们分别绑定模型原生
`config.length / config.width / config.opacity / config.enabled`；长度沿局部负 Z 轴，不沿用 Beam 半宽换算。
保存与导出回执不代替着色器 GPU 外观或变量运行验收。

进度与身份清单保存在 `build/mcp/model-laser-active.json` 及其指向的 `model-laser-delivery-*.json`。
失败后使用同一入口的 `resume` 阶段恢复原工程；恢复先暂停并核对清单 revision，工程关闭、外部编辑或不符的文件身份均停止，
不新建重复工程、不覆盖现存文件。已经完成的清单只返回交付结果；配方改变且已有文件保存时拒绝覆盖。
遇到 `INVALID_ARGUMENT / INVALID_DATA / UNSUPPORTED_OPERATION` 时，在相同 revision 上以 `dryRun` 二分定位首个失败操作前缀，
再检查该单项是否独立失败；诊断写入新的 `model-laser-failure-*.json`，不提交试探操作，不修改或覆盖已有诊断。

本轮首个失败位于模型资产之后的旋转设置：资产内材质 SNBT 使用 `type,data` 顺序，反序列化回填依赖时按原生字典序改成
`data,type`，使 `assetJson` 字节数组变化并触发严格往返拒绝。配方已改为原生规范顺序，随后整批保存／导出成功；
维护时保持材质快照的原生 SNBT 表示，不为此放宽 UUID、类型或工程往返检查。

项目 `.codex/config.toml` 的 `mcp_servers.minecraft` 指向新 `dist/cli.js`；可信项目配置覆盖用户同名配置，已有连接需重连，参见 [Codex MCP 配置](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)。保留该文件原有 Git 管理状态。

IDEA/MCP 不可用时明确报告受阻，不偷偷换桥接或绕过 IDEA。新增连接故障按实际日志区分启动失败、未发现、失去连接与调试暂停；未确认的推测不能写成根因。
