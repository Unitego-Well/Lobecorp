# Minecraft MCP

本仓库独立管理 Minecraft MCP 的 common、Minecraft 26.1.2 NeoForge 模组及 TypeScript bridge。它有自己的 Git 历史、Gradle 根工程、版本目录与 wrapper，不依赖 Lobecorp 的根构建。特效业务实现留在消费方，MCP 模组提供通用扩展命令入口。

上游：[langyo/minecraft-mod-mcp](https://github.com/langyo/minecraft-mod-mcp)，导入基础提交为 `50e059dccb09a9e23b91833ffdbf42efd97fa6e6`。本地修复工作树的 `McpConfig`、`McpHttpServer`、`PlayerWorldHelper` 与 `modClient.ts` 修改随源码保留，原 `run/client/.mcp-repair-build` 保持原状。个人 MCP 配置、账户、运行数据和构建输出不纳入。

原作者为 langyo；上游提供 MIT／Apache-2.0／CC0 许可选择，三份原始许可证保留于本目录，不改变 Lobecorp 根许可证。当前 Gradle 子项目沿用模组的 MIT 声明。

Photon 部分能力参考 [spawner1145/photon2mcp](https://github.com/spawner1145/photon2mcp)，只补现有 MCP 缺失的工程打开、资源、视角和截图能力；已有编辑事务继续复用。来源、适配修改与 Apache-2.0 原文见 [来源与许可记录](SOURCE_PROVENANCE.md)，未导入其独立服务或 Groovy 运行库。

独立工程锁定 NeoForge 26.1.2.100、ModDev 2.0.142、Java 25 与 Gradle 9.6.1；Node 包依赖按导入时实际安装版本锁定，安装任务生成 package-lock。IDEA 的 `Minecraft MCP Bridge` 配置执行 `checkBridge`／`buildBridge`／`probeBridge`，检查 TypeScript、构建产物与 stdio 工具发现；`Minecraft MCP Effect Runtime` 只读运行客户端的能力与状态。`Minecraft MCP Effect Authoring` 需已进入控制模式，在独立模型与粒子 FX 工程检查编辑、预览、历史、保存与导出，并保留其 UUID 目录中的示例文件，供复核结果。桥接入口为 `packages/minecraft-mod-mcp/dist/cli.js`，已运行的 stdio 进程需要重新连接才能加载源码变更。

两份原始图标已纳入源码。`importIcons -Pmcp.iconSource=<目录>` 仅用于首次导入，拒绝覆盖已有图标；正常构建无需 `run/client/.mcp-repair-build`。npm 生成的 node_modules／dist 与 Gradle 构建输出按子项目忽略规则保留在本地，package-lock 纳入依赖锁定。

## 独立构建与消费

通过 IDEA 打开本目录。需要构建时，使用本仓库 wrapper 的 `build`、`buildBridge`；`checkBridge` 与 `probeBridge` 分别用于 TypeScript 和 stdio 检查。任务说明不代表本次已执行。消费方可将已提交版本固定在自己的 `modules/minecraft-mcp` 源码快照中；每个工作区保留自己的 build、node_modules、dist、run 与端口，不将它们链接到此仓库共享。

本次拆分保留消费方原路径与现有运行配置，不迁移存档、账号或全局 MCP 配置。升级先记录独立仓库提交，合并源码差异并保留本地修改；冲突停止。旧进程仍使用旧类或旧 dist，源码更新不等于运行中的客户端已修复。

## 输入所有权

保留开发客户端默认进入控制模式的既有要求；自动激活与显式 `enter_control_mode`／`exit_control_mode` 都只改变 MCP 操作授权，不改变系统鼠标捕获。仅显式 `release_mouse` 请求一次性释放。

模拟操作只调用游戏内部回调，不调用 AWT Robot 或 GLFW 移动系统光标。内部模拟请求同步作用域内，MouseCaptureMixin 阻止原版 grabMouse 的重新居中；作用域通过 finally 清理，真人输入仍沿用原版捕获。无法找到内部输入 API 时返回错误，不回退为系统输入。GUI、玩家、实体与方块的 direct／simulate 接口及可选特效适配仍保留。

本次尚未运行编译、Data、bridge 构建或游戏验收。鼠标修复是源码结论，不能代替真人点击重新捕获、MCP 调用不居中和跨窗口切换的运行验证。
