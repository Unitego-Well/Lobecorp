---
name: lobecorp-workspace-sync
description: 将原始 Lobecorp dev 的已提交工具、规范与业务代码同步到现有独立 Git 工作区，保留各自改动和测试环境，遇到冲突暂停。用于地基更新、提交钩子安装、暂停检查与恢复；不反向自动汇总任务分支。
---

# 工作区共同地基同步

先读 [同步方案](../../../docs/systems/workspace-sync.md) 和 dev-standards。原始 dev 是成果汇合处和地基，仅同步已提交内容；任务成果须审查后合入
dev，不自动提交任务 WIP 或反向合并所有分支。

通过 IDEA 集成终端运行 [Sync-Workspaces.ps1](scripts/Sync-Workspaces.ps1)。默认只列计划；用户授权同步时加 -Apply，安装提交触发加
-InstallHook。冲突暂停后先读原始 Git common-dir/workspace-sync/state 的原因、备份和详情，解决后才用 -Resume 重试。它不会强选
ours/theirs、丢弃 stash、reset 或清理工作区。

- 同步前请工作区负责人保存 IDEA 文件并暂停写入；无法确认时不要创建 ready 标记。持续开发期间用本地
  .gradle/workspace-sync.busy 阻止同步。
- 提交钩子无人值守遇到脏工作区时，只有负责人明确准备好的 .gradle/workspace-sync.ready 存在才允许合并；开始继续编辑前移除
  ready。busy 优先。
- 保留任务提交、暂存／未暂存／未跟踪内容；不复制 run、build、缓存、node_modules、IDE 和运行配置。已提交变动若涉及保护路径，暂停审查。
- MCP 配置三方合并前归一化仓库路径与端口，成功后定位到目标根和原端口；有其他配置冲突仍暂停。配置已暂存时不擅自重新分配暂存归属。
- 不自动构建、安装依赖、Data、重载 MCP 或重启游戏。源码同步不代表正在运行的旧 bridge／游戏已更新。
- 不操作归档聊天。不创建 Codex 定时任务、系统服务或常驻 watcher；Git 钩子调用进程结束即关闭。不要自动关闭其他任务的游戏或
  IDE。

完成报告每个工作区 Synced／Paused、源提交、原 HEAD、恢复位置和实际验证；保留备份至负责人确认。
