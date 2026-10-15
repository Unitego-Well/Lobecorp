# Minecraft MCP 子项目

本目录由 Lobecorp 仓库管理源码，保留 Minecraft MCP 的 common、Minecraft 26.1.2 NeoForge 模组及 TypeScript bridge。Gradle 根工程包含 `:minecraft-mcp`，客户端使用它的源码；桥接使用独立 npm 包。特效实现留在 Lobecorp，MCP 模组提供通用扩展命令入口。

上游：[langyo/minecraft-mod-mcp](https://github.com/langyo/minecraft-mod-mcp)，导入基础提交为 `50e059dccb09a9e23b91833ffdbf42efd97fa6e6`。本地修复工作树的 `McpConfig`、`McpHttpServer`、`PlayerWorldHelper` 与 `modClient.ts` 修改随源码保留，原 `run/client/.mcp-repair-build` 保持原状。个人 MCP 配置、账户、运行数据和构建输出不纳入。

原作者为 langyo；上游提供 MIT／Apache-2.0／CC0 许可选择，三份原始许可证保留于本目录，不改变 Lobecorp 根许可证。当前 Gradle 子项目沿用模组的 MIT 声明。

Photon 部分能力参考 [spawner1145/photon2mcp](https://github.com/spawner1145/photon2mcp)，只补现有 MCP 缺失的工程打开、资源、视角和截图能力；已有编辑事务继续复用。来源、适配修改与 Apache-2.0 原文见 [第三方声明](../../docs/third-party/photon2mcp.md)，未导入其独立服务或 Groovy 运行库。

NeoForge、ModDev 和 Java 版本与 Lobecorp 根工程一致；Node 包依赖按导入时实际安装版本锁定，安装任务生成 package-lock。IDEA 的 `Minecraft MCP Bridge` 配置执行 `checkBridge`／`buildBridge`／`probeBridge`，检查 TypeScript、构建产物与 stdio 工具发现；`Minecraft MCP Effect Runtime` 只读运行客户端的能力与状态。`Minecraft MCP Effect Authoring` 需已进入控制模式，在独立模型与粒子 FX 工程检查编辑、预览、历史、保存与导出，并保留其 UUID 目录中的示例文件，供复核结果。桥接入口为 `packages/minecraft-mod-mcp/dist/cli.js`，已运行的 stdio 进程需要重新连接才能加载源码变更。

两份原始图标已纳入源码。`Minecraft MCP Import Assets` 仅用于本地首次迁移，拒绝覆盖已有图标；正常构建无需 `run/client/.mcp-repair-build`。npm 生成的 node_modules／dist 与 Gradle 构建输出按子项目忽略规则保留在本地，package-lock 纳入依赖锁定。

使用接口、控制模式、编辑历史、路径和实际检查结果统一见 [Minecraft MCP 系统文档](../../docs/systems/minecraft-mcp.md)。
