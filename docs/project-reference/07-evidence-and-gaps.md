# 证据、验证结果与维护清单

## 本次实际检查

本次盘点与整理实际执行了以下读取、静态检查和最小范围修改：

- 读取当前任务提供的开发规则，并读取通用开发、Minecraft 模组、IDEA JVM、Explore 和 Feature Research Skill。
- IDEA MCP 传输短暂异常时，读取并按 `codex-windows-runtime-mcp-recovery` 的只读恢复流程检查连接；未重启 IDE，也未绕过 IDEA 编辑项目。
- 使用 IDEA/MCP 检查项目状态、索引状态、模块、依赖和目录树。
- 读取 Gradle 配置、版本目录、NeoForge 元数据、Mixin 配置、接口注入配置和 CI 配置。
- 读取主类、注册表、Attachment、Capability、技能注册、事件入口、网络基类和既有架构文档。
- 读取 `.codex/standards`、`.codex/skills`、`.claude/skills` 中与项目相关的本地规则和 Skill 说明。
- 读取仓库内嵌 `minecraft-mod-mcp` 的 README、中文 AI 工具指南、CLI 指南、26.1.2 NeoForge 研究文档、嵌套 AGENTS 和本地配置说明；没有读取敏感 `.env`。
- 通过官方网页核对 Java SE/JLS 25、NeoForge 26.1/版本化开发文档、Gradle Kotlin DSL、IntelliJ IDEA 文档和 MCP 2026-07-28 规范，并记录版本差异与不可直接套用的边界。
- 使用 IDEA Git 状态确认当前分支、暂存/未暂存数量、冲突和未跟踪状态。
- 使用 IDEA/MCP 完成网络 Payload 接口、事件/注册/客户端/工具包职责、相关可见性和明确命名工具类构造函数的整理，并检查了旧包名与过时调用。
- 使用 IDEA 诊断检查相关文件；修复编译暴露的 4 个访问控制/初始化错误后，IDEA 项目编译成功且无编译问题；本轮工具类规范补充后再次编译，仍成功且无问题。
- 本轮源码修改后再次使用 IDEA `Data` 配置完成数据生成，退出码为 0；读取 `src/generated/resources` 目录并确认没有通过 Git 产生新的手写源文件变更。
- IDEA Gradle 同步最终完成，最后一次状态为 `SUCCESS`，索引已退出忙碌状态。
- 使用 IDEA/MCP 创建本目录的 9 个 Markdown 文档（README 加 8 个分类文档），并通过 IDEA 更新本文件及规则文档中的事实记录。

## 尚未完成或未验证项目

以下操作仍未执行或没有形成成功证据，因此不能报告为成功：

- 完整 Gradle `build`、`internalTestBundle`、测试任务或发布包验证。
- Minecraft 客户端、服务器或游戏测试；`Data` 只完成了数据生成，不等同于游戏验收。
- JetBrains Debugger 运行、断点、变量检查或调用栈追踪。
- Minecraft Mod MCP bridge 的 `initialize`、`tools/list`、`ping`、截图或控制模式验证。
- NeoForge、GeckoLib、LDLib2、AnvilLib、Mixin 或其他外部依赖的联网版本核对。
- 全量警告清理、自动格式化、完整打包和游戏验收。

## 需要持续标注的差异

1. `docs/java-architecture.md` 和 `.codex/standards/java-class-organization.md` 记录了约 284 个 Java 文件以及历史静态诊断统计；当前工作区仍有大量包迁移和并行修改，具体状态以 IDEA 当前索引和文件树为准。
2. IDEA Gradle 同步已成功完成；后续依赖、同步或语义操作仍应以再次读取的当前状态为准。
3. `minecraft-mod-mcp` 本地 README 对工具数量、支持版本和端口有多处说明，存在 `35+`/`45` 的数量差异；没有运行时握手就不应择一作为事实。
4. `run` 目录同时包含游戏存档、截图、日志、嵌套仓库和备份，不应与 Lobecorp 的主源集混合统计。
5. 当前 Git 状态存在大量暂存/未暂存修改；任何后续自动化都必须先重新读取状态，避免将用户改动误认为本资料集产生。

## 安全和可维护性边界

- 不把 `.env`、密钥、Cookie、Token、账户、数据库或内部服务地址写入文档。
- 不复制 `node_modules`、`.gradle`、`build` 或运行存档中的第三方内容。
- 不把历史 Skill 中的计划、旧版本示例或嵌套仓库测试报告写成当前 Lobecorp 的运行结论。
- 文档中的版本、路径、协议号和端口都是当前读取到的项目资料；发生版本升级后应重新盘点。

## 后续维护顺序

1. 版本升级：更新项目基线、依赖表、Mixin/NeoForge 版本说明。
2. 架构迁移：更新包职责、数据所有权、事件唯一入口和引用路径。
3. 网络或同步变化：更新 Payload 表、协议版本、服务端校验和视觉同步边界。
4. Skill/MCP 变化：更新工具来源、连接方式、敏感信息边界和已验证状态。
5. 每次维护后记录实际使用的 IDEA/MCP 检查；仍未执行的构建、运行和测试继续保留为未验证。
