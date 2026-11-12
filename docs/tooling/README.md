# 工具与工作区

维护：2026-11-10。按操作目标选读；读取手册不执行命令，工具源码及随包说明保持原位。

| 分类 | 入口 |
|---|---|
| 开发 | [通用脚本](dev-scripts.md) |
| 验证 | [验证范围与测试入口](testing.md) |
| 基准夹具 | [BenchMod](benchmod.md) |
| 游戏调试 | [调试开关](debugging.md) |
| Minecraft MCP | [接入、制品、操控和后台控制](minecraft-mcp/README.md) |
| 工作区 | [布局与同步](workspaces/README.md) |
| Photon | [制作 MCP、API 与性能工具](photon/README.md) |
| 代码工具 | [工具目录](../../tools/README.md) |
| 架构建图 | [Normify](../../tools/lobecorp-normify/README.md) |

## 项目工具链

外部终端常用读查与语义操作入口见[通用开发脚本](dev-scripts.md)，实际可用范围与受阻能力在手册中分别标明。

本项目的开发与验收工具链固定为下列五项，各自环节不得用其他方式替代：

- **spark**：性能剖析基线，`lib/spark-1.10.172-neoforge.jar`。用于帧耗时、GPU 阶段与热点采样；剖析链接与本地 profile 分别记录，本地文件不能冒充已上传链接，不代表任意第三方模组组合。
- **BenchMod**：项目内基准夹具（`src/bench`），通过 IDEA 的 `BenchClient`、`BenchServer`、`BenchPairedServer`、`BenchRemoteClient` 运行配置启动；场景、门禁与历史数据见 [BenchMod](benchmod.md)。
- **mc mcp**：Minecraft MCP 桥接（`lib/minecraft-mcp`，端口以当前工作区配置为准，隔离约定见[制品规范](minecraft-mcp/minecraft-mcp-artifacts.md)），负责 `ping`、`screenshot`、`enumerate_widgets`、`execute_command` 与输入控制；接入与限制见 [Minecraft MCP 外部制品消费](minecraft-mcp/minecraft-mcp-artifacts.md)。
- **idea mcp**：IDEA MCP（JetBrains 插件，`http://127.0.0.1:64342/stream`），负责符号语义、引用与调用关系、语义重构、未保存 Document、诊断及调试相关能力；普通文件读写、Git 与构建使用 CLI，按需使用 IDE 专属运行配置。
- **debug**：IDEA Debug 会话，负责断点、栈帧与表达式求值，并与 mc mcp 共同完成进游戏、控制与截图。

静态诊断、编译、Data、无游戏测试、实机画面与性能分别记录，不能互相代替。Sodium 与 BenchMod 只对应当前构建与夹具配置；换包、关光影或改窗口后的结果须重新记录。
