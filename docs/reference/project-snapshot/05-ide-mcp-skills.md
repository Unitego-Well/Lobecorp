# IDE、MCP 与本地 Skill

## 当前 IDEA 状态

本次盘点通过 IDEA/MCP 读取了项目文件和索引信息：

- Mixin IDEA 状态：空闲，非 dumb mode，没有正在进行的 Gradle resolve 或项目导入。
- IntelliJ index 状态：`isDumbMode=false`、`isIndexing=false`。
- IDEA 模块：`Lobecorp`、`Lobecorp.main`、`Lobecorp.test`。
- 当前依赖索引包含 Minecraft patched merged、NeoForge、GeckoLib、Curios、JEI、Jade、LDLib2、Photon、ParticleStorm 和 AnvilLib 等。
- 生命周期管理器显示 Lobecorp 已登记但当前 mode 为 `closed`，且生命周期自动化被禁用；这不等价于本次 IDEA 索引不可用，以实际工具返回结果为准。

本次使用的能力包括：项目状态、索引状态、目录树、文件读取、Git 状态、模块/依赖列表、文件诊断、IDEA 项目编译、`Data` 数据生成配置和 Gradle 同步。没有执行完整测试、客户端/服务端游戏运行或调试会话。

## 项目 MCP 配置

`.codex/config.toml` 声明了本地 `idea` MCP，并对 `apply_patch` 和 `build_project` 设置审批模式。该配置属于工作区现有内容，本次没有修改。

当前任务环境可见的工具类别包括：

| 工具类别 | 典型能力 | 本次状态 |
| --- | --- | --- |
| IDEA MCP | 文件、目录、依赖、Mixin、注册和项目状态 | 已用于读写、静态检查和收尾验证 |
| IntelliJ Index | 符号、引用、层级、诊断和项目索引 | 已读取状态并用于静态检查 |
| JetBrains Debugger | 断点、运行配置、变量和调用栈 | 未使用；本任务不需要运行时调试 |
| Minecraft Mod MCP | Minecraft 屏幕、输入、世界和玩家运行时 | 未连接/未使用；不能据此声明游戏状态 |
| Codex/系统工具 | 文件补丁、线程、工作区和文档辅助 | 仅用于本地读取和文档辅助；项目文件通过 IDEA MCP 修改 |

## 项目内 `.codex` Skill

| Skill | 责任 |
| --- | --- |
| `lobecorp-conductor` | 指挥家 SavedData、附件、能力、目录同步、生命周期和远程控制 |
| `lobecorp-entity-animation` | 实体动画层、状态边沿、技能动作动画和退出清理 |
| `lobecorp-entity-visual-sync` | 实体状态、粒子、技能同步、效果实例和客户端表现 |
| `lobecorp-event-architecture` | NeoForge 事件入口唯一性、包边界和有序分发 |

相关参考位于 `.codex/skills/lobecorp-conductor/references/`，包括指挥家架构、实体技能架构和迁移计划。

## 项目内 `.claude/skills` Skill

| Skill | 责任/适用范围 |
| --- | --- |
| `find-skills` | 发现和安装可用 Agent Skill |
| `ide-index-mcp` | JetBrains Index MCP 的项目、符号、引用、诊断和层级工具 |
| `image-vision` | 图片识别/OCR；其脚本包含 `.env`，本次未读取 |
| `jetbrains-debugger` | JetBrains 调试器 MCP 的断点、暂停、变量和调用栈流程 |
| `mc-mod-operations` | Minecraft 模组文件操作、版本分流、修改和验证边界 |
| `minecraft-logs` | Bedrock 网络包捕获；不是本项目 Java 调试的默认路径 |
| `minecraft-modder-neoforge` | 默认 Minecraft 26.1.2/NeoForge 26.1.2 模组开发辅助 |
| `minecraft-modding` | NeoForge/Fabric/Legacy Forge 的通用模组开发与资源生成 |
| `minecraft-modpack-server` | Linux/macOS 模组服务器托管 |
| `minecraftconsoles-lce` | Minecraft Legacy Console Edition C++ 项目 |
| `mixin-master-reference` | SpongePowered Mixin、MixinExtras、MixinSquared 和冲突规避 |
| `nbt` | `nbt!` 宏和 Minecraft 协议 NBT 编码 |
| `skill-creator` | 创建、改进、评测和打包 Skill |

本项目 Java/NeoForge 修改最相关的是 `ide-index-mcp`、`mc-mod-operations`、`minecraft-modder-neoforge`、`minecraft-modding`、`mixin-master-reference`、`jetbrains-debugger`，以及四个 `.codex` Lobecorp Skill。

## 本次任务使用的通用 Skill

- `dev-standards`：工具顺序、范围、补丁、并行编辑和验证约束。
- `mc-mod-operations`：确认 Minecraft/NeoForge/Java 版本并保持平台 API 边界。
- `intellij-idea-jvm-development`：将 IDEA 和 IntelliJ Index 作为 JVM 项目的权威静态工具。
- `explore`：先用代码库探索和目录/入口分析建立源码地图。
- `feature-research`：按现有架构、数据边界和入口整理资料。

这些 Skill 只作为本次工作的操作规范；不代表所有相关功能都已运行验证。

## 敏感信息边界

- 不读取、不复制、不整理 `.env`、Token、Cookie、密码、私钥、数据库内容或运行账户信息。
- `.claude/skills/image-vision-zhipu/scripts/.env` 被识别为敏感路径，本次仅记录 Skill 存在，没有读取内容。
- `run` 下的运行存档、日志、截图、嵌套 `node_modules` 和缓存不作为项目 API 文档来源。
