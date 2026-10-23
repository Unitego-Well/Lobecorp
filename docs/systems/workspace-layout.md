# 工作区目录与迁移

2026-10-23，原始 dev 为地基；任务目录统一位于 `.workspace/lobecorp_<content>`，仍为独立 Git worktree，不是源码副本或嵌套 Git 仓库。原始 `.gitignore` 排除整个 `.workspace/`。

| 任务 | 真实目录 | 当前聊天兼容入口 |
| --- | --- | --- |
| 特效 | `.workspace/lobecorp_photon` | `C:/Users/wang_/.codex/worktrees/7903/Lobecorp` |
| 清道夫 | `.workspace/lobecorp_horde` | `C:/Users/wang_/.codex/worktrees/horde-recovery/Lobecorp` |

兼容入口为目录 junction。当前聊天继续完成原任务；用户确定后续任务时再创建新聊天，直接使用真实目录，不提前创建空聊天，不修改 Codex 内部数据库。不要用应用的旧工作区清理操作删除兼容入口；Git worktree 已加保护锁，正常编辑和提交不受影响。

迁移工具：[Move-Workspace.ps1](../../tools/workspaces/Move-Workspace.ps1)。默认只读计划，-Apply 才执行；先保存并暂停负责人写入，确认没有构建或游戏。复制包括暂存、未暂存、未跟踪、忽略文件、run 与缓存；每文件 SHA256 核对完成后，旧目录保留为带时间戳的完整备份，再创建兼容链接并 git worktree repair。记录位于 Git common-dir/workspace-relocations，保存 HEAD、index tree、porcelain 与文件清单。冲突、占用、校验差异必须暂停。

经用户授权的占用处理使用 [Get-WorkspaceDirectoryOwners.ps1](../../tools/workspaces/Get-WorkspaceDirectoryOwners.ps1) 只读查看工具的实际工作目录。-Stop 必须得到明确授权，并在负责人暂停后使用；只结束核对 PID、启动时间、命令类型与工作目录一致的工具子进程，不结束聊天、app-server、IDE 或系统进程。工具内存状态可能重置。IDE 终端按独立 PID 核对，不自动按名称清理。

此次原始备份位于旧目录同级 lobecorp_photon_backup_20261023-085601-e70791a6 和 lobecorp_horde_backup_20261023-091621-dd3d1e37；清道夫关闭 IDEA 时新增的 workspace.xml 另存 lobecorp_horde_late_idea_20261023。HEAD、index tree 和业务状态已核对一致。保留备份至迁移与后续任务确认；本次没有删除备份。

旧创建技能脚本的未提交版本仍使用旧目录默认值，不作为新目录政策依据。新任务显式使用 `git worktree add <原始目录>/.workspace/lobecorp_<content> <已确认分支>`，创建前检查分支、端口及原始状态；独立测试配置仍按项目隔离技能准备。原始索引须排除 `.workspace`，不跨工作区搜索或修改。

## 本次同步与构建结果

2026-10-23：原始、特效、清道夫和外部 MCP 四个项目的 Gradle 同步均返回 SUCCESS。外部 MCP 完整 build 成功；原始、特效、清道夫完整 build 均已执行，编译和 assemble 完成，最终在 test 阶段失败；保留现有 Photon 测试缺失 fxpacks／资源基线断言及 reserveBuildIdentity 配置缓存问题，按用户要求不扩展业务修复。原始 Data 成功，23 文件 written0/removed0；MCP 制品检查和真实 stdio 工具清单检查成功，共 70 项，未启动游戏。

原始制品接入提交 e71d4f0，配置提交 b44d51e；特效 e73f7c0，清道夫 29ab4d9；外部发布源码提交 53084e2。两个工作区通过 Git 接收同一批 21 份制品并逐份核对 SHA256，.gitattributes 禁止制品换行转换。迁移前后分别核对 1006／1112 份业务源文件 SHA256 未变；仅授权的 MCP 测试入口路径发生变化。各自原有暂存条目核对一致，清道夫原有 BenchMod 窗口参数仍留在未提交修改中。

本次未发布 GitHub、未自动汇合业务分支、未创建新聊天、未读取已归档聊天。现有聊天解除暂停后继续任务；正在运行或已注册的旧 MCP 连接不因 Git 更新自动完成运行验收，需要使用新桥接入口重新连接。

