# 原始 dev 与独立工作区同步

## 2026-10-23 当前工具与本地草稿

本轮 Git 整理将尚未适配当前布局的 reviewed-merge 增强原文及差异封存至忽略的本地目录，活动 `Sync-Workspaces.ps1` 和 `Test-WorkspaceSync.ps1` 恢复为整理前已提交基线；旧受限驱动和属性表未发布。当前脚本不提供封存副本中的 `-ReviewedMerge`、`-PreviewOnly` 参数，不照抄后文历史恢复命令。保留位置和后续审查边界统一见[原始总索引](../README.md#本次提交与本地保留)。

后文 2026-10-21 的停止记录是当时证据，未提交文件的原位置与 HEAD 描述不再代表当前状态。本轮只整理原始项目提交，不触发工作区同步，不运行这些脚本或改动其他工作区。

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

MCP 配置将旧／本地／源版本中的项目路径、MC_MCP_PORT 和临时副本末尾换行归一化后执行三方合并，成功后还原目标自身路径与端口。其他配置冲突、已暂存的本地 MCP 配置、保护运行目录的传入变动、进行中的 Git 操作、未跟踪文件碰撞均暂停，不自动选边。

运行目录、存档、build、缓存、node_modules、.idea、.run 保持独立。合并成功不会安装依赖、编译、执行 Data、重载或启动游戏；旧进程继续运行旧代码，按后续授权分别更新自己的 bridge 和客户端。

## 编辑协调

### 文档同步边界

原始dev的已提交文档会传播到任务分支，因此公共规范使用检出内的跟踪文件及相对链接，不引用原始物理工作树、C盘协议、本地 `.agent/` 或忽略的运行证据作为必要正文。各分支保留同源公共规范快照及清晰标识的专属补充；分支当前任务、问题与验收不能随公共文档更新覆盖为dev状态。

文档也必须经过三方合并与WIP保护。发生公共规范或专属补充冲突时暂停，由负责人审查，不能因“规范同步”自动选择dev整份文件。未提交代理资料不在同步范围，原始资料中心的本地导航不能当作分支独立检出的公共依赖。文档引用与提交边界统一见[代理文档管理](../standards/agent-docs.md#提交边界)。

Git 无法观察 IDEA 未保存的文档，也不能替其他 AI 获取写入锁。同步前负责人必须保存并暂停写入；长期编辑时放置本地 .gradle/workspace-sync.busy。钩子默认不自动处理脏工作区，只有负责人放置 .gradle/workspace-sync.ready 后才允许无人值守恢复 WIP；开始编辑前移除 ready，busy 始终优先。这些本地标记不纳入提交。

脚本用独占文件句柄串行化自己的执行，不按进程名称杀程序。Git 子进程的 PID、启动时间、可执行文件、工作区、结束时间与退出码写入 Git common-dir/workspace-sync/processes.jsonl，持有的进程句柄在调用完成时关闭。Git 和 PowerShell 调用正常结束后退出，不保留临时服务或 watcher。

## 冲突恢复

先读取 state/<目标哈希>.json 和对应运行目录。预检冲突没有改变 HEAD、索引和工作文件，先由负责人决定如何整理其局部冲突，然后执行 -Apply -Resume 重试。Resume 只重新预检，不忽略冲突。

如果冲突发生在实际 merge 或 stash 恢复阶段，检查 MERGE_HEAD、git status、备份 head／stash／restore；负责人完成合并并核对暂存／未暂存内容前不要重跑。不要盲目 reset --hard、stash pop/drop 或自动 abort。恢复阶段的 stash 仍保存原跟踪改动；配置原文另存于该运行目录。备份不保证收录未保存的 IDE 内容。

原始 dev 的反向成果合并需单独审查和授权；各工作区保留分支，不建立所有目录互相覆盖的文件镜像。

## 2026-10-21 用户停止点：代码与资源合并已落地

用户要求“保存文档然后停止”。本节是当前停止状态；后文的配置冲突暂停属于先前历史。本次不再继续提交辅助脚本、同步、构建或验证。

### 实际完成

- 两工作区已合入原始 dev 的 f7579f6ffedd1ea3d8eaa4b867bdae4ccdb6a7c1，最新同步结果均为 Synced。
- 7903：合并提交 d39a6a72d8950a283b39cddf2abc7aba138e4542，保留既有 Photon 提交和当前本地修改，MCP 端口19877。
- 90be：合并提交77eac1cac56120d0c75ca6ef6ee001ec0ead21a9，保留其 e8b972d GPU 优化提交和本地修改，MCP 端口19876。
- 女王两个 Java 文件经逐 token 比对无逻辑差异，两个 JSON 经解析值和 token 比对一致，保留现有排版。端口校验保留双方有效逻辑并统一明确范围提示；修改记录按章节三方合并，未使用 ours／theirs 强选整个文件。
- 仅本次 Git 子进程启用 core.attributesFile 和受限合并驱动；未改全局 Git 配置、项目 .gitattributes 或共享 info/attributes。新增 -ReviewedMerge 与 -PreviewOnly 参数，未知逻辑差异仍拒绝自动接受。
- 暂存区和工作文件分别准备合并树，避免重复新增资源的 --index 恢复错误；已恢复各自暂存／未暂存修改。核对时7903仍有3个暂存、17个未暂存文件，90be仍有158个暂存、64个未暂存文件；这些数字不是后续任务不得改变的约束。
- 两边 .run 和 .idea/runConfigurations 文件 SHA256 与执行前记录相等；各自桥接路径和端口正确，未合并索引项均为0。
- IDEA 对每个工作区的2个女王动画 Java 及5个 MCP Java 文件完成错误检查，均无错误。增强脚本静态检查无问题；临时仓库四组回归通过，其中覆盖同名新增资源暂存内容和工作文件原始字节，测试目录已删除。
- 终端执行曾超时，但没有重复启动同步；之后读取状态确认两个目标均已完成。未执行项目编译、Data、依赖安装、桥接构建、游戏启动或 MCP 重载，不把源码同步当作运行中客户端已加载新代码。

### 未提交与未执行

原始 dev HEAD 仍为 f7579f6。本次辅助实现保留在原始工作目录，尚未创建新的源码提交，也尚未将这些辅助实现分发到两个工作区：

- 修改 .agents/skills/lobecorp-workspace-sync/scripts/Sync-Workspaces.ps1。
- 修改 .agents/skills/lobecorp-workspace-sync/scripts/Test-WorkspaceSync.ps1。
- 新增 .agents/skills/lobecorp-workspace-sync/scripts/Resolve-ReviewedMerge.mjs。
- 新增 .agents/skills/lobecorp-workspace-sync/scripts/reviewed-merge.attributes。
- 本次停止记录在本文件与 changes.md，保存后不提交，避免 post-commit 再次触发同步，遵守用户停止要求。

原有未跟踪的 lobecorp-isolated-workspace 技能目录不属于本次改动，继续保留。聊天协调接口仍返回 Transport closed，不声称已向其他聊天发送成功。

### 备份与恢复入口

实际合并备份：原始 .git/workspace-sync/20261021-210423-f02708c1。7903键为 dbb73852b6b533d5，90be键为 e9562afe1cb20093；每个目标保留配置原文、工作文件字节和 before-merge／result 记录。对应 refs/backups/workspace-sync/20261021-210423-f02708c1/<键>/ 下有 head、wip、prepared、stash、restore 引用；不要删除或盲目重复应用 stash。原始修改前提交另存 refs/backups/before-reviewed-workspace-merge-20261021。

合并审计位于 .git/workspace-sync/reviewed-merges.jsonl，Git 子进程 PID、启动／结束与退出码位于 processes.jsonl。运行配置检查基线为 build/workspace-sync-runtime-before.json，回归结果为 build/workspace-sync-validation.json。

后续获得继续授权后，先重新核对三个 HEAD、工作区当前修改与未保存的 IDE 文档，再审阅上述未提交辅助实现。两个工作区已完成的 f7579f6 合并不应重复还原；先保存并提交本次辅助代码与记录，再按需同步新提交。提交会触发已安装钩子，脏工作区仍需负责人确认写入窗口；不要擅自创建 ready 标记，也不要覆盖其他任务的新修改。若需再预检，使用原始脚本的 -Apply -Resume -ReviewedMerge -PreviewOnly，仅在核对候选后才去掉 -PreviewOnly。

## 2026-10-21 配置冲突恢复

用户授权解决两个工作区的 MCP 配置冲突并恢复同步，业务冲突暂停。核对发现：Git show 的配置文本被读取器去掉了末尾换行，本地配置保留换行；新增 Normify 段因此与文件末尾换行差异产生文本冲突，并非端口或路径冲突。Normalize-Config 只在临时副本统一末尾换行，不重写用户配置格式；回归补充“旧基线无端口段、本地新增独立端口、源新增 Normify 段”的真实形态。

IDEA 修改行格式处理与诊断通过，四组临时 Git 仓库回归通过并清理。执行 -Apply -Resume，固定源 c76e0450644248c7b5bafbb7701ea303987ac8ca 后，两个工作区均通过 MCP 配置三方预检，进入代码／资源预检：

| 工作区 | 配置 | 恢复同步结果 |
|---|---|---|
| 7903 | 19877、自身桥接路径保留，配置文件 SHA256 与执行前备份相等 | Paused：consts.ts、女王动画定义及阶段 Java、两份模型／动画 JSON、changes.md 冲突 |
| 90be | 19876、自身桥接路径保留，配置文件 SHA256 与执行前备份相等 | Paused：consts.ts、两份女王模型／动画 JSON、changes.md 冲突 |

未选择任何业务冲突版本。实际 stash push／merge 尚未执行，两目标 HEAD 仍分别为3f935ea和2559a01，未合并索引项为0。本轮不把配置预检修复写成完整同步成功；准备好的 Normify 配置需等代码合并成功后才写入目标，避免提前启用尚未同步的程序路径。未构建、安装依赖、重载服务或启动游戏。

备份位于原始 Git common-dir/workspace-sync/20261021-203502-4ffec374，每个目标有配置原文和工作文件字节备份，head／wip 引用前缀为 refs/backups/workspace-sync/20261021-203502-4ffec374/。原始修复前提交另存 refs/backups/before-mcp-config-sync-resume-20261021。完整冲突列表见对应 result.json 和 state 文件。聊天协调接口仍为 Transport closed，未声称两个聊天已暂停写入。

## 首次执行记录

2026-10-21：脚本、Skill 和回归脚本经 IDEA 创建与格式处理，IDEA 诊断未报告问题。临时 Git 仓库实际通过：分歧提交合并且保留暂存／未暂存／未跟踪／忽略存档和本地端口；WIP 冲突在预检暂停且 HEAD／索引／工作状态未变；提交钩子实际返回有效暂停结果且不重试已有暂停；真实提交钩子自动合并一个干净目标并保留本地配置。测试临时工作区均已清理。之前发现并修复 stderr 污染提交号、Git 换行转换改变原 LF、Git 钩子 PATH 下多个 Git 候选导致启动失败，并隔离钩子继承的 Git 索引／仓库环境。

首次源提交 aa1c3c3dac5e7e66d389ae2e375554e00b850a1c：

| 工作区 | 原 HEAD | 本轮结果 |
|---|---|---|
| 7903 / photon-performance | 3f935eab70ce3b9bb8aeec0bc38da0b834e0b88e | Paused：MCP 配置三方冲突，目标原文件未更改 |
| 90be / entity-animation-lod（清道夫） | 2559a010322a1a04a602489f932e37230afe35a6 | Paused：MCP 配置三方冲突，目标原文件未更改 |

冲突发生在真正 stash／merge 之前，未产生用于实际合并的 head／stash 备份引用；本地改动工作文件已有字节备份，归一化配置与具体暂停状态保存在原始 .git/workspace-sync 的本轮目录。没有自动选边或继续后续业务合并。原始 .git/hooks/post-commit 已安装；暂停状态持续保留，负责人解决并确认写入窗口后才使用 -Resume。

当前 Codex 聊天协调工具返回 Transport closed，未能通知两个聊天暂停；未把消息发送失败写成已协调成功。未重启 IDE/Codex/MCP，未构建、Data 或启动游戏；既有独立运行配置、存档和端口未修改。
