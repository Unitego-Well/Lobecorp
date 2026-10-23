# 系统文档索引

本工作区负责 **Horde／清道夫**。先读 [Horde 当前入口](horde-index.md)与[历史证据索引](horde-history.md)，再按下表查询共同基线；其他系统状态不作为本工作区验收。根入口见 [README](../../README.md)。

整理日期：2026-10-23。按任务选择下表匹配入口，只读适用章节；不默认加载全部系统文档、案例、changes.md 或所有 Skill 参考。文档可能过时，当前源码、IDEA 解析和锁定依赖优先。

## 功能与入口

| 系统 | 入口 |
|---|---|
| 注册与数据 | [注册、附件与数据契约](registration-data.md) |
| 实体与 AI | [实体与 AI](entities-ai.md) |
| 群体事件与清道夫浪潮 | [深夜清扫、固定间距、参战配额与配置](horde-events.md) |
| 群体渲染讨论 | [2026-10-22聊天反馈、sparkc链接与待验证问题](horde-rendering-discussion-20261022.md) |
| 群体事件渲染实现 | [固定线路、客户端目标区域检测、GPU让行、烘焙帧动画、区段光场](horde-rendering-implementation.md) |
| 群体渲染性能审计 | [性能、CPU侧工作与冗余功能静态审计](horde-performance-audit-20261022.md) |
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
| 光影兼容研究 | [EXIT 适用性、当前版本与后续调用](shader-compatibility-exit.md) |
| 动态视觉精细度 | [距离、负载、自动 LOD 与豁免](visual-quality.md) |
| 特效移除 | [实施结果、保留能力与备份](photon-removal-result.md)、[原移除计划](photon-removal-plan.md) |
| 特效重写 | [当前需求与文件兼容契约](photon-rewrite-requirements.md) |
| 模型特效重设计 | [功能、架构及现有资源直接可用硬性门槛](photon-model-redesign.md)、[当前性能静态审查与优化计划](photon-performance-audit.md) |
| 特效重设计实施 | [模型输出、命名制作事务、工程测试验收与G1未完成项](photon-redesign-progress.md)、[原始资源基线](photon-resource-baseline-20261019.json) |
| G1代码阶段 | [全部代码优先的编写范围、API/MCP和未验证边界](photon-g1-code-stage-20261021.md) |
| 特效性能 | [检测工具与实施状态](photon-performance-tool.md)、[优化、基线与采样](photon-performance.md)、[世界前后稳定性对比](photon-world-performance-comparison.md)、[逐对象检测与关键优化计划](photon-detailed-performance-plan.md) |
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
| 激光模板资产 | [星束模板与资源](queen-star-beam.md) |
| 光球 | [独立启动、结束与爆发](light-ball.md) |
| 激光 | [工程、变量与资源](queen-arcana-beam.md) |
| 激光法术 | [组合时序与对象索引](laser-spell.md) |
| 女皇技能特效与瞬时星束 | [代码动画阶段、中心起点与精简星束](queen-beam-skill-effects.md) |
| BenchMod 基准测试 | [工具链、清道夫 AI 与客户端渲染场景](benchmod.md) |
| 工作区共同地基 | [dev 提交自动同步、保护与暂停恢复](workspace-sync.md) |
| 架构建图 MCP | [独立 Normify MCP、CLI 与 Skill](../../tools/lobecorp-normify/README.md) |
| Minecraft MCP 外部制品 | [外部构建、lib、Git 分发与端口隔离](minecraft-mcp-artifacts.md) |
| Minecraft MCP | [工作区固定版本接入](minecraft-mcp-workspace.md)、[Minecraft MCP](minecraft-mcp.md)、[GUI、玩家、实体与方块操控](minecraft-mcp-controls.md)、[后台接管、鼠标释放与限帧](minecraft-mcp-background-control.md) |
| 特效 MCP | [Photon 特效 MCP](photon-effect-mcp.md) |
| 特效经验 | [制作经验与维护规范](effect-authoring-lessons.md) |
| AI 特效制作 | [制作规范入口](../../AGENTS.md) |
| 修改记录 | [修改记录](changes.md) |

## 修改与规则入口

修改前按目标系统确认已有能力，再通过 IDEA 核对；修改后同步系统文档与[实际记录](changes.md)。详细导航、测试入口和历史证据边界按需查[导航补充](navigation-notes.md)。

- [项目根规范](../../AGENTS.md)：必须读取的规则与专项路由。
- [工作流程](../standards/workflow.md)、[Java 与包](../standards/java-and-packages.md)、[实体契约](../standards/entity-contracts.md)：按任务读取，效力与根规范一致。
- [项目资料](../project-reference/README.md)、[Java 25](../java-25-language-and-comments.md)、[架构](../java-architecture.md)、[锁定版本](../../gradle/libs.versions.toml)：涉及对应内容时读取。

静态诊断、编译／Data、运行加载、视觉与性能检查分别记录；不能用历史结果替代本次验收。
