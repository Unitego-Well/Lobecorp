# Minecraft MCP 工作区接入

2026-10-22：7903 通过 [版本锁](../../modules/minecraft-mcp.lock.json) 消费独立仓库 E:/xm/mc/mod/Unitego-Well/minecraft-mcp 的 aa8289a485cfaf2b90eca073f2ce1eb719328b6c。工作区保留自己的 modules/minecraft-mcp 源码快照，不链接共享 build 或 bridge 产物。

已保留特效 MCP 扩展，合入鼠标输入隔离修复、独立构建入口与来源许可记录。端口 19877、.codex/config.toml、.run、IDEA 运行配置、run、build、node_modules 和 dist 保持工作区归属。consts.ts 原有文案与目标相同，保留为未提交改动；已有 MCP 配置修改不纳入本次提交。

备份为 refs/backups/before-mcp-snapshot-7903-20261022/head 与 wip。最初 IDEA 批量补丁因换行上下文与二进制支持限制只部分应用，已停止盲目重试，核对原文后通过 IDEA 文档 API 逐块完成文本接入；wrapper JAR 从独立仓库复制并核对 SHA256 相同。

源码接入不代表运行中的客户端或 bridge 已加载修复。本轮只读取 IDEA 静态诊断与 Git 源码/保护配置差异，不构建、测试、Data、安装、重连或启动游戏。后续升级通过 IDEA 合并固定提交差异，保留本地修改，冲突停止。
