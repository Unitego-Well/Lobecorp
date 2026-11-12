# 仓库内本地 MCP 资料

本章只整理仓库中已经存在的 `minecraft-mod-mcp` 资料，不联网核对其上游状态，也不把它当作 Lobecorp 的 Gradle 子模块。

## 快照位置

仓库中发现两个独立工作副本：

- `run/client/.mcp-repair-build`
- `run/client/devbridge-backup-20260924/mcp-secure-build`

两个目录都包含独立 `.git`、`AGENTS.md`、`README.md`、`packages/minecraft-mod-mcp`、`docs`、`scripts` 和多版本模组源码/资源。第一个是主要阅读来源，第二个是备份快照。它们不属于 `src/main`，不应参与 Lobecorp 包职责判断。

## 本地文档描述的架构

仓库内 README 和中文 AI 工具指南描述的链路是：

```text
AI 工具 -- MCP/stdio --> minecraft-mod-mcp bridge -- HTTP --> 游戏内 MCP Mod -- 反射 --> Minecraft
```

- AI 工具启动 Node.js stdio bridge。
- bridge 扫描游戏内 Mod 的 HTTP 端口并读取状态。
- 每次 MCP 调用转成游戏 Mod 的 HTTP 请求。
- 游戏内 Mod 在渲染线程/游戏线程执行截图、GUI、输入、命令和状态读取。
- 本地文档描述默认端口从 `9876` 向 `9000` 回退；这是文档约定，不是本次运行时验证结果。

## 本地文档列出的能力

`packages/minecraft-mod-mcp/README.md` 把工具分为：

- 感知：`ping`、`screenshot`、玩家/世界信息、调试字段、屏幕按钮和组件树。
- 输入：点击、右键、拖拽、滚轮、按键、输入/粘贴、热键、视角和命令。
- 控制：进入/退出控制模式、释放鼠标、游戏模式、启动/停止 Minecraft、连接状态。

中文 `AI-TOOLS.md` 还说明 MCP 客户端应连接 stdio bridge，而不能把 MCP/SSE 客户端直接指向游戏 Mod 的 HTTP 端口。仓库内 `.opencode.json` 使用本地命令 `minecraft-mod-mcp` 作为 MCP server。

## 版本和说明差异

本地资料存在需要保留而不能擅自消解的差异：

- `packages/minecraft-mod-mcp/README.md` 写的是 `35+ MCP tools`。
- `docs/guides/zhs/AI-TOOLS.md` 写的是 `45 个工具`。
- 根 README 的支持矩阵列出 26.1.2 的 Forge 和 NeoForge 包；Fabric 列为不可用。
- `docs/research/zh-CN/26.1.2+neoforge.md` 描述 NeoForge 26.1.2 使用事件驱动注入，不使用 GLFW 回调或 Mixin，并记录了 `neoforge.mods.toml`、`IEventBus`、`NeoForge.EVENT_BUS` 和输入事件差异。

这些数字和版本能力没有在本次任务中通过 bridge handshake、`tools/list`、Minecraft 实例或构建重新确认，不能当作当前安装版本的运行保证。

## 嵌套仓库规则

嵌套 `AGENTS.md` 规定其自身的分支、PR、提交格式、验证循环和敏感信息红线。它只约束在嵌套 `minecraft-mod-mcp` 仓库中进行的工作；本次没有在嵌套仓库中修改、提交、推送或运行验证。

## 使用建议

- 研究 Lobecorp 的游戏内 MCP 时，先确认实际运行的 Minecraft、NeoForge 和 bridge 版本，再使用 `ping`/状态读取做连接确认。
- 不把 bridge 的 HTTP 端口误写成 MCP endpoint；按本地 AI 工具指南使用 stdio bridge。
- 运行时输入工具需要控制模式，涉及点击、按键、命令和世界改变时应另行确认授权。
- 需要把本地 MCP 资料升级为正式依赖说明时，应单独核对嵌套仓库版本、license、工具列表和安全配置；本资料集不联网补充这些内容。
