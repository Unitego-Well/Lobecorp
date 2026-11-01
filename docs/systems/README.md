# 系统文档索引

整理日期：2026-10-25。本页属于原始 dev 工作；[原始总览](../README.md)与[原始问题汇总](../issues.md)独立维护。Photon 和 Horde 的进度、问题与验收由[各自工作区总览](../README.md#独立工作入口)维护，本页仅保留原始分支已有系统契约。按任务选择下表匹配入口，只读适用章节；不默认加载全部系统文档、案例、changes.md 或所有 Skill 参考。文档可能过时，当前源码、IDEA 解析和锁定依赖优先。

## 项目工具链

本项目的开发与验收工具链固定为下列五项，各自环节不得用其他方式替代：

- **spark**：性能剖析基线，`lib/spark-1.10.172-neoforge.jar`。用于帧耗时、GPU 阶段与热点采样；剖析链接与本地 profile 分别记录，本地文件不能冒充已上传链接，不代表任意第三方模组组合。
- **BenchMod**：项目内基准夹具（`src/bench`），通过 IDEA 的 `BenchClient`、`BenchServer`、`BenchPairedServer`、`BenchRemoteClient` 运行配置启动；场景、门禁与历史数据见 [BenchMod](benchmod.md)。
- **mc mcp**：Minecraft MCP 桥接（`lib/minecraft-mcp`，固定端口 `MC_MCP_PORT=19876`），负责 `ping`、`screenshot`、`enumerate_widgets`、`execute_command` 与输入控制；接入与限制见 [Minecraft MCP 外部制品消费](minecraft-mcp-artifacts.md)。
- **idea mcp**：IDEA MCP（JetBrains 插件，`http://127.0.0.1:64342/stream`），负责源码读写、诊断、运行配置与后台 Git。
- **debug**：IDEA Debug 会话，负责断点、栈帧与表达式求值，并与 mc mcp 共同完成进游戏、控制与截图。

静态诊断、编译、Data、无游戏测试、实机画面与性能分别记录，不能互相代替。Sodium 与 BenchMod 只对应当前构建与夹具配置；换包、关光影或改窗口后的结果须重新记录。

## 功能与入口

统一配置树、客户端/服务端归属与重新生成规则见[配置结构](configuration.md)。

| 系统 | 入口 |
|---|---|
| 注册与数据 | [注册、附件与数据契约](registration-data.md) |
| 实体与 AI | [实体与 AI](entities-ai.md) |
| 群体事件与清道夫浪潮 | [深夜清扫、固定间距、参战配额与配置](horde-events.md) |
| 群体事件渲染实现 | [固定线路、客户端目标区域检测、GPU让行、烘焙帧动画、区段光场](horde-rendering-implementation.md) |
| 浪潮视觉过渡与战斗区域 | [圆核心、胶囊轮廓、真实脚点粒子与服务端方区](horde-visual-transition.md) |
| 视觉过渡交接 | [本轮已完成、未解决问题与下一步](horde-handoff-visual-transition.md) |
| Horde 问题汇总 | [缺陷、性能候选、验收与历史遗留的唯一清单](horde-issues.md) |
| 仇恨、群体与阵营 | [战斗关系](combat.md) |
| 四色伤害与生物资源 | [四色伤害、固定防御、承伤系数与精神／灵魂值](four-color-damage.md) |
| 临时抓取 | [实体抓取](entity-grab.md) |
| 实体技能 | [技能系统](entity-skills.md) |
| 命中框 | [命中框](hitbox.md) |
| 动画 | [动画](animation.md) |
| 服务端动画与附着 | [服务端动画、根运动与空间附着](animation-runtime.md) |
| 通用空间附着 | [通用空间附着](attachments.md) |
| 玩家动画 | [玩家动画](player-animation.md) |
| 指挥家 | [指挥家](conductor.md) |
| 客户端 UI | [客户端 UI](client-ui.md) |
| 玩家 HUD、滤镜与护盾 | [ImaginaryCraft 迁移、配置及护盾重写](player-hud-shields.md) |
| Photon 编辑器 | [独立入口、原生隔离与制作功能](photon-editor.md#入口与隔离) |
| 粒子与渲染 | [粒子与渲染](particle-rendering.md) |
| 光影兼容研究 | EXIT 适用性、当前版本与后续调用（历史路径：`shader-compatibility-exit.md`；当前 dev 未保留） |
| 动态视觉精细度 | [距离、负载、自动 LOD 与豁免](visual-quality.md) |
| 特效移除 | [实施结果、保留能力与备份](photon-removal-result.md)、[原移除计划](photon-removal-plan.md) |
| 特效重写 | [当前需求与文件兼容契约](photon-rewrite-requirements.md) |
| 模型特效重设计 | [功能、架构及现有资源直接可用硬性门槛](photon-model-redesign.md)、[当前性能静态审查与优化计划](photon-performance-audit.md) |
| 特效重设计实施 | [模型输出、命名制作事务、工程测试验收与G1未完成项](photon-redesign-progress.md)、[原始资源基线](photon-resource-baseline-20261019.json) |
| G1代码阶段 | [全部代码优先的编写范围、API/MCP和未验证边界](photon-g1-code-stage-20261021.md) |
| 特效性能 | [检测工具与实施状态](photon-performance-tool.md)、优化、基线与采样（历史路径：`photon-performance.md`；当前 dev 未保留）、世界前后稳定性对比（历史路径：`photon-world-performance-comparison.md`；当前 dev 未保留）、逐对象检测与关键优化计划（历史路径：`photon-detailed-performance-plan.md`；当前 dev 未保留） |
| 世界文本粒子 | [文本粒子](text-particles.md) |
| 屏幕伤害粒子 | [屏幕伤害粒子](screen-damage-particles.md) |
| Photon／LDLib2 修复 | [修复对照](photon-ldlib2-fixes.md) |
| Photon 依赖升级 | [发布与适配](photon-dependency-upgrade.md) |
| 发射器发射器 | [Photon 发射器发射器](photon-emitter-spawner.md) |
| 命名粒子动画 | [Photon 命名动画](photon-named-animations.md) |
| 模型特效 | [Photon 模型特效](photon-model-effects.md) |
| 事件 | [事件入口](events.md) |
| 网络 | [网络协议](network.md) |
| 资源与生成 | [资源与数据生成](resources-datagen.md) |
| 激光模板资产 | 星束模板与资源（历史路径：`queen-star-beam.md`；当前 dev 未保留） |
| 光球 | 独立启动、结束与爆发（历史路径：`light-ball.md`；当前 dev 未保留） |
| 激光 | 工程、变量与资源（历史路径：`queen-arcana-beam.md`；当前 dev 未保留） |
| 激光法术 | 组合时序与对象索引（历史路径：`laser-spell.md`；当前 dev 未保留） |
| 女皇技能特效与瞬时星束 | [代码动画阶段、中心起点与精简星束](queen-beam-skill-effects.md) |
| BenchMod 基准测试 | [工具链、清道夫 AI 与客户端渲染场景](benchmod.md) |
| 工作区目录与对话路由 | [真实目录、当前协调入口与历史边界](workspace-layout.md) |
| 工作区共同地基 | [dev 提交自动同步、保护与暂停恢复](workspace-sync.md) |
| 架构建图 MCP | [独立 Normify MCP、CLI 与 Skill](../../tools/lobecorp-normify/README.md) |
| Minecraft MCP 外部制品 | [外部构建、lib、Git 分发与端口隔离](minecraft-mcp-artifacts.md) |
| Minecraft MCP | [独立仓库与鼠标修复](minecraft-mcp-artifacts.md#独立仓库迁移记录历史)、[Minecraft MCP](minecraft-mcp.md)、[GUI、玩家、实体与方块操控](minecraft-mcp-controls.md)、[后台接管、鼠标释放与限帧](minecraft-mcp-background-control.md) |
| 特效 MCP | [Photon 特效 MCP](photon-effect-mcp.md) |
| 特效经验 | [制作经验与维护规范](effect-authoring-lessons.md) |
| AI 特效制作 | 制作技能（历史路径：`../../.codex/skills/lobecorp-effect-authoring/SKILL.md`；当前 dev 未保留） |
| 修改记录 | [修改记录](changes.md) |

## 修改与规则入口

修改前按目标系统确认已有能力，再通过 IDEA 核对；修改后同步系统文档与[实际记录](changes.md)。详细导航、测试入口和历史证据边界按需查[导航补充](navigation-notes.md)。

- [项目根规范](../../AGENTS.md)：必须读取的规则与专项路由。
- [工作流程](../standards/workflow.md)、[Java 与包](../standards/java-and-packages.md)、[实体契约](../standards/entity-contracts.md)：按任务读取，效力与根规范一致。
- [项目资料](../project-reference/README.md)、[Java 25](../java-25-language-and-comments.md)、[架构](../java-architecture.md)、[锁定版本](../../gradle/libs.versions.toml)：涉及对应内容时读取。

静态诊断、编译／Data、运行加载、视觉与性能检查分别记录；不能用历史结果替代本次验收。

## 全部保留文档分类索引

范围：项目自有 Markdown，排除运行/构建输出、缓存、备份及符号链接目录。以下 97 份文档保留；系统计划与报告按各自历史边界读取，不因导航整理视为已完成。许可证、程序读取夹具与第三方原文不修改。

### 项目入口、规范与设计

- [AGENTS.md](../../AGENTS.md)
- [README.md](../../README.md)
- [docs/java-25-language-and-comments.md](../../docs/java-25-language-and-comments.md)
- [docs/java-architecture.md](../../docs/java-architecture.md)
- [docs/java-package-organization-plan.md](../../docs/java-package-organization-plan.md)
- [docs/standards/entity-contracts.md](../../docs/standards/entity-contracts.md)
- [docs/standards/java-and-packages.md](../../docs/standards/java-and-packages.md)
- [docs/standards/workflow.md](../../docs/standards/workflow.md)

### 系统、计划与历史证据

- [docs/systems/animation-runtime.md](../../docs/systems/animation-runtime.md)
- [docs/systems/animation.md](../../docs/systems/animation.md)
- [docs/systems/attachments.md](../../docs/systems/attachments.md)
- [docs/systems/benchmod.md](../../docs/systems/benchmod.md)
- [docs/systems/changes.md](../../docs/systems/changes.md)
- [docs/systems/client-ui.md](../../docs/systems/client-ui.md)
- [docs/systems/combat.md](../../docs/systems/combat.md)
- [docs/systems/conductor.md](../../docs/systems/conductor.md)
- [docs/systems/effect-authoring-lessons.md](../../docs/systems/effect-authoring-lessons.md)
- [docs/systems/entities-ai.md](../../docs/systems/entities-ai.md)
- [docs/systems/entity-grab.md](../../docs/systems/entity-grab.md)
- [docs/systems/entity-skills.md](../../docs/systems/entity-skills.md)
- [docs/systems/events.md](../../docs/systems/events.md)
- [docs/systems/four-color-damage.md](../../docs/systems/four-color-damage.md)
- [docs/systems/hitbox.md](../../docs/systems/hitbox.md)
- [docs/systems/horde-events.md](../../docs/systems/horde-events.md)
- [docs/systems/horde-extreme-execution-plan.md](../../docs/systems/horde-extreme-execution-plan.md)
- [docs/systems/horde-extreme-optimization-plan.md](../../docs/systems/horde-extreme-optimization-plan.md)
- [docs/systems/horde-handoff-visual-transition.md](../../docs/systems/horde-handoff-visual-transition.md)
- [docs/systems/horde-handoff.md](../../docs/systems/horde-handoff.md)
- [docs/systems/horde-index.md](../../docs/systems/horde-index.md)
- [docs/systems/horde-issues.md](../../docs/systems/horde-issues.md)
- [docs/systems/horde-posttest-optimization.md](../../docs/systems/horde-posttest-optimization.md)
- [docs/systems/horde-pretest-optimization.md](../../docs/systems/horde-pretest-optimization.md)
- [docs/systems/horde-rendering-implementation.md](../../docs/systems/horde-rendering-implementation.md)
- [docs/systems/horde-visual-transition.md](../../docs/systems/horde-visual-transition.md)
- [docs/systems/laser-release-profile.md](../../docs/systems/laser-release-profile.md)
- [docs/systems/laser-spell.md](../../docs/systems/laser-spell.md)
- [docs/systems/light-ball.md](../../docs/systems/light-ball.md)
- [docs/systems/minecraft-mcp-artifacts.md](../../docs/systems/minecraft-mcp-artifacts.md)
- [docs/systems/minecraft-mcp-background-control.md](../../docs/systems/minecraft-mcp-background-control.md)
- [docs/systems/minecraft-mcp-controls.md](../../docs/systems/minecraft-mcp-controls.md)
- [docs/systems/minecraft-mcp.md](../../docs/systems/minecraft-mcp.md)
- [docs/systems/navigation-notes.md](../../docs/systems/navigation-notes.md)
- [docs/systems/network.md](../../docs/systems/network.md)
- [docs/systems/particle-rendering.md](../../docs/systems/particle-rendering.md)
- [docs/systems/photon-dependency-upgrade.md](../../docs/systems/photon-dependency-upgrade.md)
- [docs/systems/photon-detailed-performance-plan.md](../../docs/systems/photon-detailed-performance-plan.md)
- [docs/systems/photon-editor.md](../../docs/systems/photon-editor.md)
- [docs/systems/photon-effect-authoring-history.md](../../docs/systems/photon-effect-authoring-history.md)
- [docs/systems/photon-effect-mcp-api.md](../../docs/systems/photon-effect-mcp-api.md)
- [docs/systems/photon-effect-mcp.md](../../docs/systems/photon-effect-mcp.md)
- [docs/systems/photon-emitter-spawner.md](../../docs/systems/photon-emitter-spawner.md)
- [docs/systems/photon-g1-code-stage-20261021.md](../../docs/systems/photon-g1-code-stage-20261021.md)
- [docs/systems/photon-ldlib2-fixes.md](../../docs/systems/photon-ldlib2-fixes.md)
- [docs/systems/photon-model-effects.md](../../docs/systems/photon-model-effects.md)
- [docs/systems/photon-model-redesign.md](../../docs/systems/photon-model-redesign.md)
- [docs/systems/photon-named-animations.md](../../docs/systems/photon-named-animations.md)
- [docs/systems/photon-performance-audit.md](../../docs/systems/photon-performance-audit.md)
- [docs/systems/photon-performance-tool.md](../../docs/systems/photon-performance-tool.md)
- [docs/systems/photon-performance.md](../../docs/systems/photon-performance.md)
- [docs/systems/photon-redesign-progress.md](../../docs/systems/photon-redesign-progress.md)
- [docs/systems/photon-removal-plan.md](../../docs/systems/photon-removal-plan.md)
- [docs/systems/photon-removal-result.md](../../docs/systems/photon-removal-result.md)
- [docs/systems/photon-rewrite-requirements.md](../../docs/systems/photon-rewrite-requirements.md)
- [docs/systems/photon-world-performance-comparison.md](../../docs/systems/photon-world-performance-comparison.md)
- [docs/systems/player-animation.md](../../docs/systems/player-animation.md)
- [docs/systems/player-hud-shields.md](../../docs/systems/player-hud-shields.md)
- [docs/systems/queen-arcana-beam.md](../../docs/systems/queen-arcana-beam.md)
- [docs/systems/queen-beam-skill-effects.md](../../docs/systems/queen-beam-skill-effects.md)
- [docs/systems/queen-star-beam.md](../../docs/systems/queen-star-beam.md)
- [docs/systems/README.md](../../docs/systems/README.md)
- [docs/systems/registration-data.md](../../docs/systems/registration-data.md)
- [docs/systems/resources-datagen.md](../../docs/systems/resources-datagen.md)
- [docs/systems/screen-damage-particles.md](../../docs/systems/screen-damage-particles.md)
- [docs/systems/shader-compatibility-exit.md](../../docs/systems/shader-compatibility-exit.md)
- [docs/systems/text-particles.md](../../docs/systems/text-particles.md)
- [docs/systems/visual-quality.md](../../docs/systems/visual-quality.md)
- [docs/systems/workspace-layout.md](../../docs/systems/workspace-layout.md)
- [docs/systems/workspace-sync.md](../../docs/systems/workspace-sync.md)

### 项目资料

- [docs/project-reference/01-project-baseline.md](../../docs/project-reference/01-project-baseline.md)
- [docs/project-reference/02-rules-and-workflow.md](../../docs/project-reference/02-rules-and-workflow.md)
- [docs/project-reference/03-architecture-and-data.md](../../docs/project-reference/03-architecture-and-data.md)
- [docs/project-reference/04-api-registries-resources.md](../../docs/project-reference/04-api-registries-resources.md)
- [docs/project-reference/05-ide-mcp-skills.md](../../docs/project-reference/05-ide-mcp-skills.md)
- [docs/project-reference/06-local-mcp-reference.md](../../docs/project-reference/06-local-mcp-reference.md)
- [docs/project-reference/07-evidence-and-gaps.md](../../docs/project-reference/07-evidence-and-gaps.md)
- [docs/project-reference/08-external-development-reference.md](../../docs/project-reference/08-external-development-reference.md)
- [docs/project-reference/09-particle-effects-research.md](../../docs/project-reference/09-particle-effects-research.md)
- [docs/project-reference/README.md](../../docs/project-reference/README.md)

### 技能、FAQ、第三方与工具

- [.agents/skills/lobecorp-mcp-background-control/SKILL.md](../../.agents/skills/lobecorp-mcp-background-control/SKILL.md)
- [.agents/skills/lobecorp-normify/SKILL.md](../../.agents/skills/lobecorp-normify/SKILL.md)
- [.agents/skills/lobecorp-workspace-sync/SKILL.md](../../.agents/skills/lobecorp-workspace-sync/SKILL.md)
- [docs/third-party/photon2mcp.md](../../docs/third-party/photon2mcp.md)
- [lib/minecraft-mcp/README.md](../../lib/minecraft-mcp/README.md)
- [tools/lobecorp-normify/README.md](../../tools/lobecorp-normify/README.md)
