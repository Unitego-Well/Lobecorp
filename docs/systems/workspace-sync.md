# 原始 dev 与独立工作区同步

原始 dev 是共同地基和成果汇合处。原始工作区每次正常 Git 提交后，通过本仓库 post-commit 钩子向已注册的独立工作区合并该次固定提交。同步包含已提交 MCP、Skill、脚本、规范和业务代码；原始未提交文件不传播，各任务 WIP 不自动合入 dev。

## 使用

脚本为 [.agents/skills/lobecorp-workspace-sync/scripts/Sync-Workspaces.ps1](../../.agents/skills/lobecorp-workspace-sync/scripts/Sync-Workspaces.ps1)。从 IDEA 集成终端执行：

~~~powershell
$sync = 'E:/xm/mc/mod/Unitego-Well/Lobecorp/.agents/skills/lobecorp-workspace-sync/scripts/Sync-Workspaces.ps1'
& $sync
& $sync -Apply -InstallHook
~~~

无 -Apply 只输出固定源提交和目标清单。可通过 -TargetRoots 选择已注册的目标。脚本限定同仓库的其他 worktree，不复制普通目录，不访问归档聊天。原始工作区必须处于 SourceRef（默认 dev）。

仅原始目录内正常提交触发钩子；任务分支自己的提交不会传播。外部 merge、pull、reset 或直接移动 dev 引用不会自动触发 post-commit，之后手动执行 -Apply。已有非本脚本 post-commit 或自定义 hooksPath 不覆盖。钩子不推送，不装 Windows 服务，也不创建 AI 定时任务。

## 保留与暂停

Git common-dir/workspace-sync 下保存运行报告、暂停状态和配置原文备份；refs/backups/workspace-sync/<运行>/<目标>/head、wip、stash、restore 保存提交和跟踪文件工作状态。未跟踪和忽略文件不暂存、不移动、不清理；与传入文件冲突时暂停。

先使用临时 Git 对象预检包含本地改动的三方合并，冲突时原工作目录和原索引不变。无冲突才暂存保管跟踪文件改动、合并源提交，再通过 --index 恢复各自暂存／未暂存状态。保留 stash 和备份引用，不自动 drop。预检不是原子事务；文件在预检期间发生改变会暂停，但不能代替负责人独占写入窗口。

MCP 配置将旧／本地／源版本中的项目路径和 MC_MCP_PORT 归一化后执行三方合并，成功后还原目标自身路径与端口。其他配置冲突、已暂存的本地 MCP 配置、保护运行目录的传入变动、进行中的 Git 操作、未跟踪文件碰撞均暂停，不自动选边。

运行目录、存档、build、缓存、node_modules、.idea、.run 保持独立。合并成功不会安装依赖、编译、执行 Data、重载或启动游戏；旧进程继续运行旧代码，按后续授权分别更新自己的 bridge 和客户端。

## 编辑协调

Git 无法观察 IDEA 未保存的文档，也不能替其他 AI 获取写入锁。同步前负责人必须保存并暂停写入；长期编辑时放置本地 .gradle/workspace-sync.busy。钩子默认不自动处理脏工作区，只有负责人放置 .gradle/workspace-sync.ready 后才允许无人值守恢复 WIP；开始编辑前移除 ready，busy 始终优先。这些本地标记不纳入提交。

脚本用独占文件句柄串行化自己的执行，不按进程名称杀程序。Git 和 PowerShell 调用正常结束后退出，不保留临时服务或 watcher。

## 冲突恢复

先读取 state/<目标哈希>.json 和对应运行目录。预检冲突没有改变 HEAD、索引和工作文件，先由负责人决定如何整理其局部冲突，然后执行 -Apply -Resume 重试。Resume 只重新预检，不忽略冲突。

如果冲突发生在实际 merge 或 stash 恢复阶段，检查 MERGE_HEAD、git status、备份 head／stash／restore；负责人完成合并并核对暂存／未暂存内容前不要重跑。不要盲目 reset --hard、stash pop/drop 或自动 abort。恢复阶段的 stash 仍保存原跟踪改动；配置原文另存于该运行目录。备份不保证收录未保存的 IDE 内容。

原始 dev 的反向成果合并需单独审查和授权；各工作区保留分支，不建立所有目录互相覆盖的文件镜像。

## 本轮执行记录

2026-10-21：脚本、Skill 和回归脚本经 IDEA 创建与格式处理，IDEA 诊断未报告问题。临时 Git 仓库实际通过：分歧提交合并且保留暂存／未暂存／未跟踪／忽略存档和本地端口；WIP 冲突在预检暂停且 HEAD／索引／工作状态未变；提交钩子实际触发且不重试已有暂停。之前发现并修复 stderr 污染提交号、Git 换行转换改变原 LF 两个问题。

首次源提交 aa1c3c3dac5e7e66d389ae2e375554e00b850a1c：

| 工作区 | 原 HEAD | 本轮结果 |
|---|---|---|
| 7903 / photon-performance | 3f935eab70ce3b9bb8aeec0bc38da0b834e0b88e | Paused：MCP 配置三方冲突，目标原文件未更改 |
| 90be / entity-animation-lod（清道夫） | 2559a010322a1a04a602489f932e37230afe35a6 | Paused：MCP 配置三方冲突，目标原文件未更改 |

冲突发生在真正 stash／merge 之前，未产生用于实际合并的 head／stash 备份引用；本地改动工作文件已有字节备份，归一化配置与具体暂停状态保存在原始 .git/workspace-sync 的本轮目录。没有自动选边或继续后续业务合并。原始 .git/hooks/post-commit 已安装；暂停状态持续保留，负责人解决并确认写入窗口后才使用 -Resume。

当前 Codex 聊天协调工具返回 Transport closed，未能通知两个聊天暂停；未把消息发送失败写成已协调成功。未重启 IDE/Codex/MCP，未构建、Data 或启动游戏；既有独立运行配置、存档和端口未修改。
