# Lobecorp Photon 工作区

本工作区负责 Photon 编辑器、模型特效、命名播放、子特效、女皇光束表现及 G1 资源验收。当前分支为 `codex/photon`，真实目录为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`。

从 [Photon 当前能力与恢复入口](docs/systems/photon-workspace.md) 开始，再按任务读取 [系统索引](docs/systems/README.md)。[Photon 唯一问题汇总](docs/issues.md) 区分当前实现、历史说明和缺失证据；[项目规则](AGENTS.md) 与共同基础文档继续有效。

本目录保留用户尚未提交的高级网格、光束与 G1 修改。代码存在不代表游戏、视觉或性能已验收，G1 仍未通过。清道夫及其他系统文档作为共同地基引用，不在此工作区更新其业务状态。

Minecraft MCP 只使用本工作区 `lib` 制品，端口 `19877`；Normify 复用原始仓库 CLI，目标根和 cwd 限定本工作区。接入见 [Minecraft MCP 工作区说明](docs/systems/minecraft-mcp-workspace.md)。

默认不构建、Data、测试、同步或启动游戏；分支仅本地使用，不推送。恢复业务先读 [未完成工作记录](docs/systems/photon-workspace.md)，保留暂存、未暂存、备份及兼容 junction。
