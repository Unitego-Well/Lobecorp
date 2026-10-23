# Horde 历史报告与证据索引

整理日期：2026-10-23。当前工作从[Horde 入口](horde-index.md)开始。以下文件保留原日期、实现阶段、数据和备份说明；历史路径、PID、配置与提交不表示当前运行状态，不读取或恢复归档聊天。

| 阶段 | 记录 | 适用边界 |
| --- | --- | --- |
| 2026-10-21 图片图集方案暂停 | [impostor 进度](horde-impostor-progress.md) | 近三维、远四视角图集旧方案；不是当前顶点帧实现的待执行计划 |
| 2026-10-22 旧图集着色器复验 | [夜间烘焙切换](horde-shader-retest-20261022.md) | 仅对应 view/viewPosition 冲突修复后的普通 Run；Debug 异常与换行差异按原记录保留，未在本轮复核 |
| 2026-10-22 需求与性能讨论 | [讨论整理](horde-rendering-discussion-20261022.md) | 反馈、采样链接、旧实现问题；不能直接视为当前根因或验收 |
| 2026-10-22 静态审计与后续实施 | [性能审计](horde-performance-audit-20261022.md) | 初版推测与后续第 9、10 节实施分开读取；静态优先级不是性能计时 |
| 2026-10-22 固定线路 GPU 实现 | [实现与增量实测](horde-rendering-implementation.md) | 当前机制说明及历史三轮帧间隔；不同高度、批次版本不混算 |
| 2026-10-22 BenchMod | [场景、门禁与运行证据](benchmod.md) | 接入当时未运行与后续已运行按章节区分；原始 dev 结果不替代 horde |
| 2026-10-23 工作区与外部 MCP | [目录与迁移](workspace-layout.md)、[制品消费](minecraft-mcp-artifacts.md) | 迁移、制品、同步和 build/test 历史边界；不证明当前游戏已连接 |
| 按日期查询 | [系统修改记录](changes.md) | 只读相关日期和系统，避免把全仓库历史当作 Horde 当前完成列表 |

历史报告引用的 `backups`、`.backups`、`build/modBench/raw-results` 和 `run/client` 是本机证据位置，可能被后续运行更新；本轮只检查入口是否存在，不重建缺失证据、不下载远端 Spark 报告。恢复前先比较当前源码与 Git 状态，不能将旧快照覆盖未提交改动。

2026-10-23 只读存在性核对：`backups/horde-validation-20261022`、`backups/horde-gpu-before-20261022` 与 `build/modBench/raw-results/default/client` 存在；旧图集复验引用的 `.backups/horde-retest-20261022/before-runtime-config.json` 未找到。存在性不代表报告内容已重新验真，缺失快照的换行恢复结论仍只保留为历史记录。
