# 系统文档索引

## 分支专属导航与同步保留

本页工作区职责、任务入口和工具端口是分支专属补充；同步公共地基发生冲突时须保留并逐段审查，不整文件选用源版本。公共规范使用本分支跟踪文件中的正文和仓库内链接，不依赖原始物理工作树；各分支进度、问题和验收证据仍由所属分支维护，不复制到 dev，也不将 dev 视为所有分支当前任务的执行者。同步只传播已提交内容，本地未发布报告、备份和运行输出仅作定位，不构成公共证据。


全部自有 Markdown、规则、Skill、记忆、工具参考及只读边界见 [文档总索引](../README.md)；本页仅按系统职责提供专题入口。

本工作区负责 **Photon／特效**，真实根为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`，分支为 `codex/photon`。先读 [当前能力与恢复入口](photon-workspace.md)、[Photon 唯一问题汇总](../issues.md) 和 [MCP API](photon-effect-mcp-api.md)，再按下表选择专题。其余系统作为共同基础保留，不表示在本工作区重新验收；旧资料缺失时只保留明确的历史路径。

共同索引整理日期：2026-10-15；Photon 导航核对日期：2026-10-23。按任务选择下表匹配入口，只读适用章节；不默认加载全部系统文档、案例、changes.md 或所有 Skill 参考。文档可能过时，当前源码、IDEA 解析和锁定依赖优先。

## 功能与入口

| 系统 | 入口 |
|---|---|
| 注册与数据 | [注册、附件与数据契约](registration-data.md) |
| 实体与 AI | [实体与 AI](entities-ai.md) |
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
| Photon 编辑器 | [独立入口、原生隔离与制作功能](photon-editor.md#入口与隔离)、[原始备份设计恢复与人工审查](photon-editor-design-restore-20261022.md)、[高级网格、光束衔接与G1续做](photon-workspace.md) |
| 粒子与渲染 | [粒子与渲染](particle-rendering.md) |
| 光影兼容研究 | EXIT 适用性、当前版本与后续调用（历史路径 `shader-compatibility-exit.md`，本工作区不存在） |
| 动态视觉精细度 | [距离、负载、自动 LOD 与豁免](visual-quality.md) |
| 特效移除 | [实施结果、保留能力与备份](photon-removal-result.md)、[原移除计划](photon-removal-result.md#历史移除方案与保留边界) |
| 特效重写 | [当前需求与文件兼容契约](photon-rewrite-requirements.md) |
| 特效重构交接 | **[2026-10-27 版当前入口](photon-effect-rewrite-handoff-20261027.md)**：本轮成果、阴性结论、恢复清单与下一步；[2026-10-26 版执行计划](photon-effect-rewrite-execution-plan-20261026.md)（R1～R6 框架与历史 A/B 数据） |
| 模型特效重设计 | [功能、架构及现有资源直接可用硬性门槛](photon-model-redesign.md)、[Sodium 诊断、瓶颈推断、建议与历史审查](photon-performance-audit.md)、[极端场景优化计划](photon-extreme-optimization-plan-20261025.md)、[GPT 执行文档](photon-extreme-execution-gpt-20261025.md) |
| 特效重设计实施 | [模型输出、命名制作事务、工程测试验收与G1未完成项](photon-redesign-progress.md)、[原始资源基线](photon-resource-baseline-20261019.json) |
| G1全量资源验收 | [501条来源、逐文件往返、独立包运行与问题台账](photon-resource-acceptance-20261021.md) |
| G1代码阶段 | [全部代码优先的编写范围、API/MCP和未验证边界](photon-g1-code-stage-20261021.md) |
| 特效性能 | [检测工具与实施状态](photon-performance-tool.md)、优化、基线与采样（历史路径 `photon-performance.md`，本工作区不存在）、世界前后稳定性对比（历史路径 `photon-world-performance-comparison.md`，本工作区不存在）、逐对象检测与关键优化计划（历史路径 `photon-detailed-performance-plan.md`，本工作区不存在） |
| 世界文本粒子 | [文本粒子](text-particles.md) |
| 屏幕伤害粒子 | [屏幕伤害粒子](screen-damage-particles.md) |
| Photon／LDLib2 修复 | [修复对照](photon-ldlib2-fixes.md) |
| Photon 依赖升级 | [发布与适配](photon-dependency-upgrade.md) |
| 实例创建架构 | [定义编译、受管租约、索引布局与原生默认](photon-runtime-creation.md) |
| 发射器发射器 | [Photon 发射器发射器](photon-emitter-spawner.md) |
| 命名粒子动画 | [Photon 命名动画](photon-named-animations.md) |
| 模型特效 | [Photon 模型特效](photon-model-effects.md) |
| 事件 | [事件入口](events.md) |
| 网络 | [网络协议](network.md) |
| 资源与生成 | [资源与数据生成](resources-datagen.md) |
| 激光模板资产 | 星束模板与资源（历史路径 `queen-star-beam.md`，本工作区不存在） |
| 光球 | 独立启动、结束与爆发（历史路径 `light-ball.md`，本工作区不存在） |
| 激光 | 工程、变量与资源（历史路径 `queen-arcana-beam.md`，本工作区不存在） |
| 激光法术 | 组合时序与对象索引（历史路径 `laser-spell.md`，本工作区不存在） |
| 女皇技能特效与瞬时星束 | [代码动画阶段、中心起点与精简星束](queen-beam-skill-effects.md) |
| BenchMod 基准测试 | [工具链、清道夫 AI 与客户端渲染场景](benchmod.md) |
| 工作区共同地基 | [dev 提交自动同步、保护与暂停恢复](workspace-sync.md) |
| 架构建图 MCP | [独立 Normify MCP、CLI 与 Skill](../../tools/lobecorp-normify/README.md) |
| Minecraft MCP 外部制品 | [外部构建、lib、Git 分发与端口隔离](minecraft-mcp-artifacts.md) |
| Minecraft MCP | [工作区固定版本接入](minecraft-mcp-workspace.md)、[Minecraft MCP](minecraft-mcp.md)、[GUI、玩家、实体与方块操控](minecraft-mcp-controls.md)、[后台接管、鼠标释放与限帧](minecraft-mcp-background-control.md) |
| 特效 MCP | [Photon 特效 MCP](photon-effect-mcp.md) |
| 特效经验 | 制作经验与维护规范（历史路径 `effect-authoring-lessons.md`，本工作区不存在） |
| AI 特效制作 | 制作技能（当前分支缺失，原路径：`../../.codex/skills/lobecorp-effect-authoring/SKILL.md`；见[问题汇总](../issues.md)） |
| 修改记录 | [修改记录](changes.md) |

## 修改与规则入口

修改前按目标系统确认已有能力，再通过 IDEA 核对；修改后同步系统文档与[实际记录](changes.md)。详细导航、测试入口和历史证据边界按需查[导航补充](navigation-notes.md)。

- [项目根规范](../../AGENTS.md)：必须读取的规则与专项路由。
- [工作流程](../standards/workflow.md)、[Java 与包](../standards/java-and-packages.md)、[实体契约](../standards/entity-contracts.md)：按任务读取，效力与根规范一致。
- [项目资料](../project-reference/README.md)、[Java 25](../java-25-language-and-comments.md)、[架构](../java-architecture.md)、[锁定版本](../../gradle/libs.versions.toml)：涉及对应内容时读取。

静态诊断、编译／Data、运行加载、视觉与性能检查分别记录；不能用历史结果替代本次验收。
