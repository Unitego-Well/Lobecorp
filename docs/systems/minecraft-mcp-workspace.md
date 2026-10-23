# Minecraft MCP 工作区接入

## Photon 当前接入（2026-10-23）

真实根为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`，分支为 `codex/photon`。当前只消费 [外部制品](minecraft-mcp-artifacts.md)：[清单](../../lib/minecraft-mcp/artifacts.json)、`lib/minecraft-mcp-0.3.0.jar` 和 `lib/minecraft-mcp/bridge`。清单源码提交为 `53084e20743d7dcdb158ee431286147070119f84`，Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25。

项目 `.codex/config.toml` 的 Minecraft CLI 与 cwd 指向本工作区 bridge，MC_MCP_PORT=19877；不恢复 modules 源码快照、不安装 npm、不在此重建桥接。run、build、存档和端口保留各自归属。Normify CLI 复用原始仓库 `tools/lobecorp-normify/dist/src/cli.js`，但 workspace-root 与 cwd 均限定本工作区，输出不跨目录。

本轮通过 IDEA 回读配置、制品清单及源码注册，未重新连接 bridge 或游戏。历史 70 项 stdio 发现和迁移构建结果属于 [工作区记录](workspace-layout.md)，不能替代当前游戏状态；当前源码登记的 20 个 effect 操作见 [API 索引](photon-effect-mcp-api.md)。

## 源码快照接入记录（2026-10-22，历史）

以下只保留原接入与备份身份；已由上述外部制品消费替代，不执行旧升级或构建步骤。

2026-10-22：7903 通过 版本锁（历史路径 `../../modules/minecraft-mcp.lock.json`，本工作区不存在） 消费独立仓库 E:/xm/mc/mod/Unitego-Well/minecraft-mcp 的 aa8289a485cfaf2b90eca073f2ce1eb719328b6c。工作区保留自己的 modules/minecraft-mcp 源码快照，不链接共享 build 或 bridge 产物。

已保留特效 MCP 扩展，合入鼠标输入隔离修复、独立构建入口与来源许可记录。端口 19877、.codex/config.toml、.run、IDEA 运行配置、run、build、node_modules 和 dist 保持工作区归属。consts.ts 原有文案与目标相同，保留为未提交改动；已有 MCP 配置修改不纳入本次提交。

备份为 refs/backups/before-mcp-snapshot-7903-20261022/head 与 wip。最初 IDEA 批量补丁因换行上下文与二进制支持限制只部分应用，已停止盲目重试，核对原文后通过 IDEA 文档 API 逐块完成文本接入；wrapper JAR 从独立仓库复制并核对 SHA256 相同。

源码接入不代表运行中的客户端或 bridge 已加载修复。本轮只读取 IDEA 静态诊断与 Git 源码/保护配置差异，不构建、测试、Data、安装、重连或启动游戏。后续升级通过 IDEA 合并固定提交差异，保留本地修改，冲突停止。
