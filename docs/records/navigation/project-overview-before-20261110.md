# 原始项目总索引

> 2026-11-10：本页是迁移前导航与历史状态的保留来源，不是当前入口。日常从[文档总览](../../README.md)逐层进入；旧完成声明、授权和分支状态只表示原记录时点，未完成项不因迁移关闭。

## 2026-11-07 憎恶女皇工作入口

用户确认后已提交既有修改并新建独立工作区 `.workspace/lobecorp_queen_of_hatred`，分支 `codex/queen-of-hatred`。对应会话ID为 `01a1b4ab-c7de-7173-9fe7-42cdf46ecca1`。专属总览、唯一问题清单和交接位于该工作区 `.agent/`，原始入口不复制其任务状态或验收。Photon/Horde仍按下方历史记录退役。

## 2026-11-06 子工作区与会话清理

Photon、Horde 成果已进入dev，用户确认后已移除两个子工作区、对应本地分支与兼容入口，并归档相关会话。未提交内容、存档和本地资料已完整归档且逐文件校验。当前只维护原始dev；每日检查已移除旧调度目标，频率保持。边界与恢复定位见[工作区记录](../../tooling/workspaces/workspace-layout.md)。

## 2026-11-06 Photon、Horde 已提交成果集成

原始dev先通过 ac324680 合入Photon固定源 ebd5d599，再通过 3a5ef2fe 合入Horde固定源 b0ceaf14；交叉自动合并问题与旧动画入口在 5f02d024 完成复验修正。编译、Bench编译、生产包隔离与Data通过，IDEA定向34文件无错误；全目录检查覆盖不完整，未执行测试、游戏、视觉或性能验收。原始10份WIP文件字节和六项暂存对象保持，两个源工作区未改，未推送。范围与证据边界见[修改记录](../changes.md#2026-11-06双分支集成复验与旧动画入口清理)。各工作区进度与问题继续由所属入口维护。

## 2026-11-01 测试窗口与后台输入修复

已移除场景聚焦、重复最大化／还原、Options 改写与伪造输入；开发客户端增加非激活一次铺满、本窗口最小化命令保护、后台节流／失焦暂停例外及失焦抓鼠标保护。IDEA 诊断、BenchMod Compile（含生产包隔离）通过，Data 23 文件 written=0。未启动游戏，实机前后台／AFK／鼠标验收仍在[唯一问题清单](../../issues.md#待处理与待验证)；当前契约见[后台控制](../../tooling/minecraft-mcp/minecraft-mcp-background-control.md#2026-11-01-开发测试窗口与鼠标边界)，本次证据见[修改记录](../changes.md#2026-11-01测试不抢鼠标与开发后台窗口策略)。


## 2026-10-29 工作区与协调对话轮换

Photon、Horde 已停止旧回合并在各自工作区保存交接，新负责人已接续；协调及每日文档巡检迁到新对话。旧对话归档，真实工作目录、分支、WIP与测试环境保留；当前有效入口见[对话路由](../../tooling/workspaces/workspace-layout.md#对话路由)。原始项目仅维护路由，不代管其它工作进度。

## 2026-10-29 受限注释小批收尾

四个新 DS 小批完成 24 个明确文件阅读，19 文件仅有注释与空白改动。负责人核对实际差异和源码，纠正护盾绘制范围、UUID 避让方向、声波清除条件、尸体筛选、tick/启动阶段与 GPU 槽池容量等说明；没有实施任何逻辑或架构建议。去注释/空白比较与 IDE 检查完成，编译/Data 成功；[证据与范围](../changes.md#2026-10-29-受限注释小批合并)不等于游戏验收或全库覆盖。

## 2026-10-29 DS 代码复核与收敛修正

复查三批实际代码与依赖契约后，保留有依据的领域/API 拆分及清理首因处理，撤回仅消警告的七个字段 final；修正模型错误详情捕获旧快照、非法技能 ID 解析异常类型不匹配，并接受已审的一行缩进。[本轮证据与限制](../changes.md#2026-10-29-ds-代码复核与收敛修正)单独记录。后续只将明确文件的小型注释/风格任务派到新 DS 对话，逻辑、API、修饰符或新结构变化先报负责人，不自行实施。

## 2026-10-28 Mixin 与 world 整理第三批

四组完成 258 文件覆盖，协调核对报告真实落盘及全部实际差异，接受 7 个代码文件改动：私有注入处理器前缀、局部格式/显式导入、实体注释与局部命名、物品创建失败的清理异常保留。未改注入目标、技能值或生命周期顺序；[验证与限制](../changes.md#2026-10-28-mixin-与-world-整理第三批)单独记录，不等于游戏或完整全库验收。

## 2026-10-28 基础域整理第二批

四个 DS 组完成 API/配置/序列化、注册/生成源码、事件/附着、动画/粒子共 174 文件的逐项阅读。协调审查后接受 21 个代码文件改动，保留公开方法原签名、事件及回调顺序、注册 ID 和默认值；动画/粒子未发现确证问题，不制造修改。动态翻译警告已通过登记源码与生成结果确认；本批[验证及限制](../changes.md#2026-10-28-基础域整理第二批)单独记录，不能当作全库整理或游戏验收完成。

## 2026-10-28 原始代码整理首轮

已实施制作事务校验合并与清理首因保留、指挥队形领域提取与 ID 解析、技能资格公共判定、模型失败状态和工程内部职责整理。各候选不等于全库审计完成；公开扩展入口、技能回调后复验、命令清理顺序与模型资源容错保留。统一验证和已保留的边界见[修改记录](../changes.md#2026-10-28-原始代码整理首轮集成)。独立 Photon/Horde 未同步；用户其他资源修改不纳入本次提交。

维护范围：原始 Lobecorp 的 `dev` 工作；整理日期：2026-11-06。本页是原始项目唯一的整体索引，管理目录职责、专题入口、Git 提交边界与本地保留位置。根 README 只介绍项目和基本信息。当前仅维护原始dev；已撤销的Photon、Horde入口及归档边界见[独立工作入口](#独立工作入口)。

## 本轮文档整理进度

2026-10-28 DS 上下文轮换：异常、跑偏或失真完成声明立即交接；每个新批次及同一任务累计三次实质续聊后必须换新 DS 对话。真实交接落盘、必要证据接续、旧对话归档并更新路由，正文见[DS 协作规范](../../standards/collaboration/subagents.md#ds-简单任务委派与停止检查)。已有整理任务继续，交接不扩大业务或测试权限。

2026-10-28 DS 协作规范：非 DS 负责人可委派简单明确任务给 DS，完成须回报并经负责人审查；DS 停止时先检查输出，仅异常中断续接。规则唯一正文见[委派与停止检查](../../standards/collaboration/subagents.md#ds-简单任务委派与停止检查)，原始协调巡检当前四个 DS，不扩大任务/审批边界。

2026-10-28 工作区对话轮换：两个工作区各自保存交接后已创建新对话，两个旧工作对话与前协调按用户最新要求归档，名称与[有效路由](../../tooling/workspaces/workspace-layout.md#对话路由)同步。复用既有目录与分支，停止要求保留；原始只维护路由，不复制业务进度、不跨区写入或同步。

2026-10-28 协调交接与导航核对：当前协调及旧对话排除边界见[对话路由](../../tooling/workspaces/workspace-layout.md#对话路由)。每日检查自动任务已原地迁移；系统索引补工作区入口，修改记录修复一处相对链接。已核对 IDEA Document、后台 Git 与导航差异，未执行构建、Data、测试或游戏；后续仅按确证漂移维护原始文档，不代管其他工作的进度。

2026-10-28 代理文档整理与工作区级规范（最新补充）：`.agent/` 统一为五类子目录加索引，备份与证据迁入 `build/agent-backups/20261028-agent-doc-cleanup/`（49 文件 SHA256 逐项一致）；[统一文档管理](../../standards/documentation/agent-docs.md)补五个文档中心、历史正文只追加、备份落 `build/agent-backups` 并出清单三条；工作区根新增 `standards/` 补充规范（不提交）与 `AGENTS.md` 第 8 条。本轮只动文档与目录，不构建、不测试、不启动游戏。

2026-10-27 测试输入边界（补充）：去除失焦硬门禁，不因失焦自动判失败或丢弃整轮；测试仍应最大化并保持焦点，允许专用API或夹具对本轮被测游戏最大化与维持／恢复焦点。模拟输入和GUI坐标点击仍可使用，但不得移动、捕获或居中实体鼠标，不操作其它用户窗口或系统显示设置。真实焦点时间线保留，规则唯一正文见[后台输入与测试边界](../../standards/workflow/game-testing.md#后台输入与测试边界)；已同步两个有效对话，原始不代改其他工作区。

2026-10-27 对话轮换：按用户明确选择，为两个现有工作区各创建全新对话继续当前任务，原始协调对话保留。当前有效ID与游戏测试串行约束见[工作区对话路由](../../tooling/workspaces/workspace-layout.md#对话路由)；旧记录保留并排除后续调度。本轮原始项目只更新路由、总索引与修改记录，不代管其他工作的业务进度；三份原字节备份位于 `build/agent-backups/20261027-chat-rotation-b186/`。

2026-10-27 DS 规则与统一文档管理：新增[DS 执行规则](../../standards/collaboration/ds-execution-rules.md)、[统一文档管理](../../standards/documentation/agent-docs.md)与工作区根 `AGENTS.md`；建立工作区根、Lobecorp、minecraft-mcp 三处 `.agent/`（不提交），把报告、交接、计划、记忆分开存放并建立索引。FAQ 记忆迁入 `Lobecorp/.agent/memory/faq.md` 并取消 Git 跟踪；根 `agent-backups/photon-r3-20261027/PROGRESS-20261027.md` 迁入工作区根 `.agent/handoffs/20261027-photon-r3-progress.md`。本次纯文档与本地目录调整，只做 IDEA 回读、诊断、引用与差异检查，不执行构建、Data、测试或游戏操作。原字节备份位于 `build/agent-backups/ds-rules-20261027/`。

2026-10-26 项目规则分层：根 AGENTS 收敛为共享入口，重复工具、编辑、并行和验证条款统一维护在工作流程；保留独立工作树、IDEA 编辑、外部 MCP 制品、基准与中文提交约束。GPT/Codex 的模型委派仅适用其全局入口，不施加给 DS。纯文档任务只检查引用、规则与差异。修改前四份原字节备份在 `.git/doc-cleanup-20261026/`；编辑与统一检查分别记录，不执行构建、Data、游戏、安装或同步。

2026-10-26 提问规则更新：已将根规范与工作流程统一为优先使用当前软件可用的提问 API，没有时直接在对话中提问；保留一次一个问题、等待回答与未回答不视为确认。仅修改规则与本工作记录，保留既有工作；检查范围为 IDEA 回读与文档诊断，不执行构建、Data、测试、同步、重载或游戏操作。原字节备份位于 `build/agent-backups/question-api-20261026-2010/`。

上次整理：2026-10-25。已完成78份自有 Markdown 的盘点和备份，将四份重复规则／过程页迁入长期专题与唯一问题清单后通过 IDEA 安全删除，当时保留74份并建立完整分类索引。当时74份文档的IDEA批量诊断已全部覆盖、无截断、0错误；1130处本地引用盘点没有失效文件目标或锚点，完整索引无漏项。源码直读说明已按当前源码补回；两项专项Skill仍缺失，已明确标注并保留问题记录，不冒充恢复能力。此次结果以批量诊断为主，不等于完整编辑器弱提示或运行验收；本轮只按授权范围提交文档增量。测试方法、规则、技能、FAQ 与记忆保留；依赖、缓存、运行输出和既有备份只记录边界。本轮不改业务或执行构建、测试、游戏操作。

## 仓库目录与 Git 管理

| 范围 | 职责与入口 | 管理方式 |
|---|---|---|
| `src/main/java/` | 模组源码；[Java 架构](../../architecture/java-architecture.md)、[系统索引](../../systems/README.md) | 跟踪已完成、可审查的源码；未完成草稿保留本地 |
| `src/main/resources/` | 手写游戏资源及元数据；[资源规范](../../systems/core/resources-datagen.md) | 跟踪，保留资源原始排版 |
| `src/test/`、`src/bench/` | 测试源码与基准场景；[BenchMod](../../tooling/benchmod.md) | 跟踪源码和固定夹具；运行结果不入库 |
| `src/generated/` | Data 生成输出 | 忽略、只读，不放手写资料 |
| `resources/`、`lang/` | 仓库内资产与手写语言输入；按资源规范确认运行定义 | 跟踪源文件，不把目录存在当作已被运行加载 |
| `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties` | 项目构建与模组元数据 | 跟踪共享配置，不提交本机凭据 |
| `gradle/`、`gradlew`、`gradlew.bat` | 版本目录、Gradle Wrapper | 跟踪，Wrapper JAR 属于必要构建入口 |
| `lib/` | 受控依赖与 [Minecraft MCP 发布制品](../../tooling/minecraft-mcp/minecraft-mcp-artifacts.md) | 按既有约定跟踪；JAR、桥接 dist、许可证和哈希清单不能按普通输出忽略 |
| `tools/lobecorp-normify/` | [架构 MCP](../../../tools/lobecorp-normify/README.md)、源码、锁文件及上游来源说明 | 跟踪源码与锁文件；本目录 dist、node_modules 忽略 |
| `tools/workspaces/` | 工作区迁移与占用识别；[布局说明](../../tooling/workspaces/workspace-layout.md) | 跟踪工具源码；报告、备份和运行状态本地保留 |
| `.agents/skills/` | 当前可用项目技能和已提交同步工具 | 跟踪适用当前仓库的内容；旧隔离与 reviewed-merge 草稿已移出有效入口 |
| `.github/`、`.editorconfig`、`.gitattributes`、`.gitignore` | CI、格式与 Git 管理规则 | 跟踪共享规则 |
| `.codex/config.toml` | 本机工作区 MCP 接入；[同步规则](../../tooling/workspaces/workspace-sync.md) | 本地保留，不再跟踪；现有 `/.codex/` 规则忽略，端口和路径继续隔离 |
| `.agent/` | 代理报告、交接、计划与记忆；本地保留 | 不提交；结构与命名见[统一文档管理](../../standards/documentation/agent-docs.md) |
| `AGENTS.md`、`docs/` | 开发规则、当前契约、问题和历史证据 | 跟踪；专题页由下方职责入口管理，不再新建并列的整体索引 |
| `.workspace/` | Photon、Horde 等独立 Git worktree | 原始仓库忽略；各自分支管理，不在原始提交中递归纳入 |
| `.idea/`、`.run/`、`.vscode/`、`run/` | 本机 IDE、运行配置、存档、端口与测试环境 | 忽略，保留在原位置 |
| `.gradle/`、`build/`、`out/`、`node_modules/`、`logs/` | 缓存、编译输出、依赖安装、日志及报告 | 忽略，保留本地 |
| `.local/`、`backups/`、`.backups/`、`.git/` | 本地草稿、备份、Git 索引及运行审计 | 不提交；需要恢复时按下方本地保留记录操作 |
| `.env`、`.env.*`、JVM 崩溃与堆转储 | 本机环境和诊断输出 | 忽略；无凭据的 `.env.example`／`.env.*.example` 可按需跟踪 |

## 按职责阅读

| 内容 | 入口 |
|---|---|
| 当前规则 | [AGENTS](../../../AGENTS.md)、[DS 执行规则](../../standards/collaboration/ds-execution-rules.md)、[统一文档管理](../../standards/documentation/agent-docs.md)、[工作流程](../../standards/workflow/README.md)、[Java 与包](../../standards/development/java-and-packages.md)、[实体契约](../../standards/development/entity-contracts.md) |
| 当前问题 | [本工作问题汇总](../../issues.md) |
| 系统与功能 | [系统索引](../../systems/README.md) |
| Java 架构和语言 | [架构](../../architecture/java-architecture.md)、[Java 25](../../standards/development/java-25-language-and-comments.md) |
| 资料与外部参考 | [历史资料索引](../../reference/project-snapshot/README.md) |
| 工作区管理 | [目录约定](../../tooling/workspaces/workspace-layout.md)、[同步规则](../../tooling/workspaces/workspace-sync.md) |
| Minecraft MCP | [外部制品](../../tooling/minecraft-mcp/minecraft-mcp-artifacts.md)、[使用](../../tooling/minecraft-mcp/minecraft-mcp.md)、[操控](../../tooling/minecraft-mcp/minecraft-mcp-controls.md)、[后台与鼠标](../../tooling/minecraft-mcp/minecraft-mcp-background-control.md) |
| 实际修改证据 | [修改记录](../changes.md) |

## Markdown 分类索引（完整清单）

原始仓库整理后保留75份项目自有 Markdown（2026-10-27 新增 DS 执行规则与统一文档管理，FAQ 记忆迁出并取消跟踪），完整清单如下；四份合并来源的恢复位置见本节末尾。每份文档只列入一个分类，专题索引负责下钻，本页仍是唯一整体索引。`.workspace/` 由各工作独立维护；`.git/`、`.local/`、`backups/`、依赖、缓存及运行输出不作为活动文档，原位保留，不按 Markdown 后缀清空。

### 介绍、导航与问题入口（4份）

| 文档 | 维护定位 |
|---|---|
| [Lobecorp](../../../README.md) | 入口与唯一问题清单 |
| [原始项目总索引](../../README.md) | 入口与唯一问题清单 |
| [原始项目问题汇总](../../issues.md) | 入口与唯一问题清单 |
| [系统文档索引](../../systems/README.md) | 入口与唯一问题清单 |

### 规则、规范与 Skill（10份）

| 文档 | 维护定位 |
|---|---|
| [MCP 后台控制](../../../.agents/skills/lobecorp-mcp-background-control/SKILL.md) | 有效规则／能力入口，保留 |
| [Lobecorp Normify](../../../.agents/skills/lobecorp-normify/SKILL.md) | 有效规则／能力入口，保留 |
| [工作区共同地基同步](../../../.agents/skills/lobecorp-workspace-sync/SKILL.md) | 有效规则／能力入口，保留 |
| [Lobecorp 项目开发规范](../../../AGENTS.md) | 有效规则／能力入口，保留 |
| [Java 25 语法与注释规范](../../standards/development/java-25-language-and-comments.md) | 有效规则／能力入口，保留 |
| [Lobecorp 实体、技能与表现契约](../../standards/development/entity-contracts.md) | 有效规则／能力入口，保留 |
| [Lobecorp Java、接口与包规范](../../standards/development/java-and-packages.md) | 有效规则／能力入口，保留 |
| [Lobecorp 工作流程与收尾规则](../../standards/workflow/README.md) | 有效规则／能力入口，保留 |
| [DS 执行规则](../../standards/collaboration/ds-execution-rules.md) | 有效规则／能力入口，保留 |
| [统一文档管理（DS 与 GPT 共用）](../../standards/documentation/agent-docs.md) | 有效规则／能力入口，保留 |


### 工具、工作区与受控制品（9份）

| 文档 | 维护定位 |
|---|---|
| [BenchMod 基准工具链](../../tooling/benchmod.md) | 技术接入；不等于当前运行已验证 |
| [Minecraft MCP 外部制品消费](../../tooling/minecraft-mcp/minecraft-mcp-artifacts.md) | 技术接入；不等于当前运行已验证 |
| [Minecraft MCP 后台接管、鼠标释放与帧率](../../tooling/minecraft-mcp/minecraft-mcp-background-control.md) | 技术接入；不等于当前运行已验证 |
| [Minecraft MCP 结构化操控](../../tooling/minecraft-mcp/minecraft-mcp-controls.md) | 技术接入；不等于当前运行已验证 |
| [Minecraft MCP](../../tooling/minecraft-mcp/minecraft-mcp.md) | 技术接入；不等于当前运行已验证 |
| [工作区目录与迁移](../../tooling/workspaces/workspace-layout.md) | 技术接入；不等于当前运行已验证 |
| [原始 dev 与独立工作区同步](../../tooling/workspaces/workspace-sync.md) | 技术接入；不等于当前运行已验证 |
| [Minecraft MCP release artifacts](../../../lib/minecraft-mcp/README.md) | 技术接入；不等于当前运行已验证 |
| [Lobecorp Normify](../../../tools/lobecorp-normify/README.md) | 技术接入；不等于当前运行已验证 |

### 历史方案、精确清单与阶段证据（6份）

| 文档 | 维护定位 |
|---|---|
| [Java 包迁移清单与已批准边界](../java-package-organization-plan.md) | 保留独有技术清单、未完成项或阶段证据 |
| [系统修改记录](../changes.md) | 保留独有技术清单、未完成项或阶段证据 |
| [G1 全部代码优先实施记录](../photon/photon-g1-code-stage-20261021.md) | 保留独有技术清单、未完成项或阶段证据 |
| [Photon 重设计实施进度](../photon/photon-redesign-progress.md) | 保留独有技术清单、未完成项或阶段证据 |
| [Photon 自建系统移除实施记录](../photon/photon-removal-result.md) | 保留独有技术清单、未完成项或阶段证据 |

### 资料快照、研究与外部来源（11份）

| 文档 | 维护定位 |
|---|---|
| [项目基线与系统边界](../../reference/project-snapshot/01-project-baseline.md) | 历史／研究参考，使用前核对当前版本 |
| [开发规则与工作流程](../../reference/project-snapshot/02-rules-and-workflow.md) | 历史／研究参考，使用前核对当前版本 |
| [源码架构与数据流](../../reference/project-snapshot/03-architecture-and-data.md) | 历史／研究参考，使用前核对当前版本 |
| [内部 API、注册表、资源与依赖](../../reference/project-snapshot/04-api-registries-resources.md) | 历史／研究参考，使用前核对当前版本 |
| [IDE、MCP 与本地 Skill](../../reference/project-snapshot/05-ide-mcp-skills.md) | 历史／研究参考，使用前核对当前版本 |
| [仓库内本地 MCP 资料](../../reference/project-snapshot/06-local-mcp-reference.md) | 历史／研究参考，使用前核对当前版本 |
| [证据、验证结果与维护清单](../../reference/project-snapshot/07-evidence-and-gaps.md) | 历史／研究参考，使用前核对当前版本 |
| [外部通用开发文档、MCP、Skill 与 API 参考](../../reference/project-snapshot/08-external-development-reference.md) | 历史／研究参考，使用前核对当前版本 |
| [Photon、GeckoLib 与 ParticleStorm 粒子接入研究](../../reference/project-snapshot/09-particle-effects-research.md) | 历史／研究参考，使用前核对当前版本 |
| [Lobecorp 历史资料与研究索引](../../reference/project-snapshot/README.md) | 历史／研究参考，使用前核对当前版本 |
| [Photon MCP 部分能力移植](../../reference/third-party/photon2mcp.md) | 历史／研究参考，使用前核对当前版本 |

### 架构与系统技术契约（35份）

| 文档 | 维护定位 |
|---|---|
| [Lobecorp Java 架构说明](../../architecture/java-architecture.md) | 原始分支的技术说明，修改前仍核对源码 |
| [服务端动画、根运动与空间附着](../../systems/animation/animation-runtime.md) | 原始分支的技术说明，修改前仍核对源码 |
| [实体动画与视觉同步](../../systems/animation/animation.md) | 原始分支的技术说明，修改前仍核对源码 |
| [通用空间附着](../../systems/animation/attachments.md) | 原始分支的技术说明，修改前仍核对源码 |
| [客户端表现与 UI](../../systems/client/client-ui.md) | 原始分支的技术说明，修改前仍核对源码 |
| [仇恨、群体与阵营](../../systems/entities/combat.md) | 原始分支的技术说明，修改前仍核对源码 |
| [指挥家系统](../../systems/encounters/conductor.md) | 原始分支的技术说明，修改前仍核对源码 |
| [实体与 AI](../../systems/entities/entities-ai.md) | 原始分支的技术说明，修改前仍核对源码 |
| [实体抓取](../../systems/entities/entity-grab.md) | 原始分支的技术说明，修改前仍核对源码 |
| [实体技能](../../systems/entities/entity-skills.md) | 原始分支的技术说明，修改前仍核对源码 |
| [事件入口与分发](../../systems/core/events.md) | 原始分支的技术说明，修改前仍核对源码 |
| [四色伤害与生物资源](../../systems/entities/four-color-damage.md) | 原始分支的技术说明，修改前仍核对源码 |
| [命中框](../../systems/entities/hitbox.md) | 原始分支的技术说明，修改前仍核对源码 |
| [系统导航补充与历史证据](../navigation-notes.md) | 原始分支的技术说明，修改前仍核对源码 |
| [网络协议与同步](../../systems/core/network.md) | 原始分支的技术说明，修改前仍核对源码 |
| [粒子效果与渲染](../../systems/client/particle-rendering.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon、LDLib2 与 KilaGraph 依赖升级](../photon/photon-dependency-upgrade.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon 粒子编辑器](../../systems/photon/photon-editor.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon 特效 MCP](../../tooling/photon/photon-effect-mcp.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon 发射器发射器](../../systems/photon/photon-emitter-spawner.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon／LDLib2 修复与兼容对照](../../systems/photon/photon-ldlib2-fixes.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon 模型特效](../../systems/photon/photon-model-effects.md) | 原始分支的技术说明，修改前仍核对源码 |
| [模型特效与高级特性重设计方案](../../design/photon/photon-model-redesign.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon 命名动画与参数控制](../../systems/photon/photon-named-animations.md) | 原始分支的技术说明，修改前仍核对源码 |
| [当前性能静态审查与优化计划](../photon/photon-performance-audit.md) | 原始分支的技术说明，修改前仍核对源码 |
| [Photon 性能检测工具](../../tooling/photon/photon-performance-tool.md) | 原始分支的技术说明，修改前仍核对源码 |
| [特效系统重写需求与现有文件兼容契约](../../design/photon/photon-rewrite-requirements.md) | 原始分支的技术说明，修改前仍核对源码 |
| [玩家动画](../../systems/animation/player-animation.md) | 原始分支的技术说明，修改前仍核对源码 |
| [玩家 HUD、屏幕滤镜与四色护盾](../../systems/client/player-hud-shields.md) | 原始分支的技术说明，修改前仍核对源码 |
| [女皇激光与瞬时星束的技能特效](../../systems/effects/queen-beam-skill-effects.md) | 原始分支的技术说明，修改前仍核对源码 |
| [注册、附件与数据契约](../../systems/core/registration-data.md) | 原始分支的技术说明，修改前仍核对源码 |
| [资源、翻译与数据生成](../../systems/core/resources-datagen.md) | 原始分支的技术说明，修改前仍核对源码 |
| [屏幕伤害粒子](../../systems/client/screen-damage-particles.md) | 原始分支的技术说明，修改前仍核对源码 |
| [世界文本粒子](../../systems/client/text-particles.md) | 原始分支的技术说明，修改前仍核对源码 |
| [动态视觉精细度与自动 LOD](../../systems/client/visual-quality.md) | 原始分支的技术说明，修改前仍核对源码 |

### 合并与旧入口去向

| 合并来源（原文件名去扩展名） | 现入口 | 保留范围 |
|---|---|---|
| 事件规范旧页（`event-registration`） | [事件订阅规则](../../systems/core/events.md#订阅规则) | 唯一入口、顺序、生命周期与检查约束 |
| 类整理旧页（`java-class-organization`） | [架构与整理历史](../../architecture/java-architecture.md#整理历史与维护边界)、[精确迁移清单](../java-package-organization-plan.md) | 包职责、批准边界与历史检查 |
| 格式化审计旧页（`java-formatting-audit`） | [架构历史](../../architecture/java-architecture.md#整理历史与维护边界)、[唯一问题汇总](../../issues.md)、[编辑器维护](../../systems/photon/photon-editor.md#控件与文本输入维护历史) | 未解决候选、必要技术说明与历史验收边界 |
| 独立仓库过程旧页（`minecraft-mcp-standalone`） | [外部制品与迁移](../../tooling/minecraft-mcp/minecraft-mcp-artifacts.md#独立仓库迁移记录历史)、[鼠标契约](../../tooling/minecraft-mcp/minecraft-mcp-background-control.md#当前鼠标契约2026-10-22) | 现消费方式、恢复引用、输入隔离与运行限制 |

原始78份 Markdown、Git 索引和逐文件 SHA256 清单位于 `.git/docs-arrange-20261025-3c524a79/`；四份拟移除页另有 IDEA 原文快照，均与磁盘备份的正文一致。恢复前先比较差异，不整批覆盖当前工作。

## 本次提交与本地保留

2026-10-23：本轮适合入库的是原始项目索引、Git 忽略规则、已保存的历史停止记录和当前管理说明。不提交旧工具草稿，不运行或分发它们。

本地草稿保留目录：`.local/git-wip/git-arrange-20261023-598bed71/`。其 `manifest.json` 记录原文件路径和 SHA256，`tool-wip.patch` 保存两份已跟踪脚本相对提交 `39a85d47` 的修改。目录内按原 `.agents/skills/` 层级保存以下六份原文，已逐份核对字节相同：

- `lobecorp-isolated-workspace/SKILL.md` 与 `scripts/New-IsolatedWorkspace.ps1`：仍依赖旧外置目录默认值和已移除的 MCP 源码模块，不能作为当前创建入口。
- `lobecorp-workspace-sync/scripts/Sync-Workspaces.ps1` 与 `Test-WorkspaceSync.ps1`：保存 reviewed-merge 增强及回归草稿；实际使用位置恢复到整理前已提交基线，外部制品路径识别得到保留。
- `lobecorp-workspace-sync/scripts/Resolve-ReviewedMerge.mjs` 与 `reviewed-merge.attributes`：旧受限合并范围仍包含已移除模块路径，只在本地保存，不接入当前提交。

完整修改前备份位于 `.git/git-arrange-20261023-598bed71/`，包含原索引、原文件与恢复计划。旧合并备份和历史引用不删除。后续恢复草稿时，先基于当前 HEAD 审查差异并适配当前布局、外部制品和端口边界，再逐项通过 IDEA 合并；不整批覆盖当前脚本，不把封存副本直接投入同步。

新增文件按用途判断，不使用全量 `git add .`，不借 `assume-unchanged` 或 `skip-worktree` 隐藏已跟踪改动。`.gitignore` 不会停止跟踪已有文件；上述配置和受控制品例外保持显式管理。本轮提交禁用同步钩子，不推送、不合并到其他工作区。

## 中文提交与后台 Git 插件

所有新增提交的标题和正文使用中文。2026-10-23 经远端引用核对，已处理 `dev`、`codex/photon`、`codex/horde` 的 89 条未推送提交：81 条说明翻译为中文，8 条纯中文说明保留；每条提交的文件树、作者、时间与合并父子关系均保留，三个工作区暂存条目与当次采样文件哈希一致。`main`、远端分支与已发布历史保持不变，未推送。旧引用、原对象、索引备份、新旧编号映射与核对结果位于 `.git/chinese-commit-messages-20261023-cd90dabe/`；旧文档提交编号仍可通过备份追溯。

IDEA 扩展已独立迁移并最终命名为 `idea-mcp-expand`，工程为 `E:/xm/idea/idea-mcp-expand`，依赖现有 IDEA MCP Server（`com.intellij.mcpServer`）。其源码、接口、构建、安装状态和未完成项由自身 `docs/README.md` 维护，本索引只管理入口，不代管该工作。另一个插件工程位于 `E:/xm/idea/idea-gradle-defaults-plugin`，沿用自身资料。两工程迁移均保留完整备份和旧路径兼容 junction，逐文件哈希清单位于 `.git/idea-plugin-relocation-20261023-dba43b3f/`。

原始卡死证据保存在 `.git/idea-git-freeze-20261023-583c10cd/`。现象、已确认位置与未验证边界统一见[问题汇总](../../issues.md)，不另建并列问题文档。用户已授权通过外部后台方式构建、验证和安装新扩展，并完成本轮中文提交；不自动重启 IDEA、不推送，也不触发其他工作区同步。当前仍停止使用会激活终端的 MCP Git 入口，静态编辑继续通过 IDEA 文档 API。

## 独立工作入口

| 工作 | 本地总览 | 分支 |
|---|---|---|
| 原始项目 | 本页、[原始问题汇总](../../issues.md) | `dev` |
| 憎恶女皇 | `.workspace/lobecorp_queen_of_hatred`，专属正文由该工作维护 | `codex/queen-of-hatred` |
| Photon（已撤销） | 原目录、本地资料与撤销前总览保存在本地完整归档；不再作为活动入口 | 本地分支已删除，提交保留在dev历史 |
| Horde（已撤销） | 原目录、本地资料与撤销前总览保存在本地完整归档；不再作为活动入口 | 本地分支已删除，提交保留在dev历史 |

当前原始dev与憎恶女皇入口有效；Photon/Horde两个旧子工作区已于2026-11-06撤销，不自动恢复、跟踪或投递旧会话。历史职责和迁移仍见[工作区记录](../../tooling/workspaces/workspace-layout.md)，本页不复制原分支的任务、问题或验收。

## 维护边界

不同工作各自维护自己的总览；每个工作内只保留一个问题汇总，专题页保留契约和处理经验并链接问题汇总。原始项目不汇总其他工作的开放问题。

每天至少整理一次：核对导航、重复内容、问题状态与证据。长期规则和独特契约保留；同一工作内重复说明合并到职责最明确的页面。完成且无遗留问题的过程文档，须先迁移必要结论、备份并修复引用后移除。历史成功只代表当时的检查，不代表本轮验收；不能仅凭旧报告标记问题已解决。

静态文档整理不执行构建、Data、测试或游戏操作。源码、锁定版本与 IDEA 解析优先于旧资料；生成输出只读。
