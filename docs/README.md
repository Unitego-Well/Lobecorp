# Photon 工作区文档总索引

整理日期：2026-10-26。范围仅限 codex/photon。磁盘严格 .md 盘点 110 份，合并移除 4 页、新增本索引后共 107 份；其中 106 份为项目自有文档，1 份 lib 外部制品说明作为只读边界。IDEA 搜索不能覆盖全部本地文件，以下逐项登记全部 107 个路径。

业务从 [Photon 总览](systems/photon-workspace.md) 恢复，当前问题只维护于 [唯一清单](issues.md)。[系统索引](systems/README.md) 按职责选专题；不要把历史计划、旧测试或文档自称授权当作当前执行许可。共同系统只保留契约，不在本工作区维护其业务进度。

## 合并去向与证据

| 原页 | 合并去向 | 保留范围 |
|---|---|---|
| photon-optimization-pretest-20261024.md、photon-optimization-posttest-20261024.md | [性能审查归档节](systems/photon-performance-audit.md#2026-10-24-历史模型诊断与法线缓存对照) | 成本机制、约束、逐轮数值、JFR／Debug／MCP 与修订边界 |
| photon-removal-plan.md | [移除记录归档节](systems/photon-removal-result.md#历史移除方案与保留边界) | 模块分类、保留边界、共享文件拆分、资源保护、当时决策与实施顺序 |
| minecraft-mcp-standalone.md | [外部制品消费](systems/minecraft-mcp-artifacts.md) | 重复导航，外部源码与制品消费契约原已覆盖 |

110 份原字节、SHA256、HEAD、原始 Git 索引及引用审计保存在 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-docs-organize-20261026；没有恢复旧源码或假造缺失资料。

## 排除与保留边界

.git、.workspace、.gradle、.idea、.local、.backups、backups、build、out、run、logs、node_modules、链接／junction 和嵌套仓库不纳入自有活动文档扫描；依赖、缓存、运行证据、已有备份和许可证保留。lib 发布 README 登记但不编辑。规则、Skill、记忆、工具参考与本地未跟踪状态均保持；正文只在合并、导航或历史缺失说明处局部调整。

35 处原缺失文件链接中，30 处历史截图改为明确历史路径说明；5 处在原字节保留的专项 Skill 中，继续作为 P09 的资料缺失，详见唯一清单。资源命名与动画示例两处旧锚点已修复，另有 1 处保留原字节 Skill 内的旧 API 锚点继续在 P09 记录。没有把不可定位的旧专题替换成含义不同的新文档。

## 本地 Skill 与工具参考（原字节保留）

| 文档 | 路径与维护状态 |
|---|---|
| [MCP 后台控制](../.agents/skills/lobecorp-mcp-background-control/SKILL.md) | `.agents/skills/lobecorp-mcp-background-control/SKILL.md`；已跟踪 |
| [Lobecorp Normify](../.agents/skills/lobecorp-normify/SKILL.md) | `.agents/skills/lobecorp-normify/SKILL.md`；已跟踪 |
| [工作区共同地基同步](../.agents/skills/lobecorp-workspace-sync/SKILL.md) | `.agents/skills/lobecorp-workspace-sync/SKILL.md`；已跟踪 |
| IDE Index MCP - Tools Reference（当前分支缺失，原路径：`../.claude/skills/ide-index-mcp/references/tools-reference.md`；见[问题汇总](issues.md)） | `.claude/skills/ide-index-mcp/references/tools-reference.md`；本地保留，不纳入本次提交 |
| IDE Index MCP - Agent Guide（当前分支缺失，原路径：`../.claude/skills/ide-index-mcp/SKILL.md`；见[问题汇总](issues.md)） | `.claude/skills/ide-index-mcp/SKILL.md`；本地保留，不纳入本次提交 |
| JetBrains Debugger MCP - Complete Tool Reference（当前分支缺失，原路径：`../.claude/skills/jetbrains-debugger/references/tool-reference.md`；见[问题汇总](issues.md)） | `.claude/skills/jetbrains-debugger/references/tool-reference.md`；本地保留，不纳入本次提交 |
| JetBrains Debugger MCP（当前分支缺失，原路径：`../.claude/skills/jetbrains-debugger/SKILL.md`；见[问题汇总](issues.md)） | `.claude/skills/jetbrains-debugger/SKILL.md`；本地保留，不纳入本次提交 |
| 指挥家项目记忆（当前分支缺失，原路径：`../.codex/skills/lobecorp-conductor/references/architecture.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-conductor/references/architecture.md`；本地保留，不纳入本次提交 |
| 实体技能架构参考（当前分支缺失，原路径：`../.codex/skills/lobecorp-conductor/references/entity-skill-architecture.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-conductor/references/entity-skill-architecture.md`；本地保留，不纳入本次提交 |
| 指挥家数据迁移历史记录（当前分支缺失，原路径：`../.codex/skills/lobecorp-conductor/references/migration-plan.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-conductor/references/migration-plan.md`；本地保留，不纳入本次提交 |
| 指挥家维护（当前分支缺失，原路径：`../.codex/skills/lobecorp-conductor/SKILL.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-conductor/SKILL.md`；本地保留，不纳入本次提交 |
| Photon 原生制作参考（当前分支缺失，原路径：`../.codex/skills/lobecorp-effect-authoring/references/native-workflow.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-effect-authoring/references/native-workflow.md`；本地保留，不纳入本次提交 |
| Lobecorp 制作与交付约定（当前分支缺失，原路径：`../.codex/skills/lobecorp-effect-authoring/references/project-workflow.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-effect-authoring/references/project-workflow.md`；本地保留，不纳入本次提交 |
| 教程冲击波逐步对应表（当前分支缺失，原路径：`../.codex/skills/lobecorp-effect-authoring/references/tutorial-shockwave-breakdown.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-effect-authoring/references/tutorial-shockwave-breakdown.md`；本地保留，不纳入本次提交 |
| AI 特效制作（当前分支缺失，原路径：`../.codex/skills/lobecorp-effect-authoring/SKILL.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-effect-authoring/SKILL.md`；本地保留，不纳入本次提交 |
| Lobecorp 实体动画（当前分支缺失，原路径：`../.codex/skills/lobecorp-entity-animation/SKILL.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-entity-animation/SKILL.md`；本地保留，不纳入本次提交 |
| Lobecorp 实体视觉同步（当前分支缺失，原路径：`../.codex/skills/lobecorp-entity-visual-sync/SKILL.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-entity-visual-sync/SKILL.md`；本地保留，不纳入本次提交 |
| Lobecorp Event Architecture（当前分支缺失，原路径：`../.codex/skills/lobecorp-event-architecture/SKILL.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-event-architecture/SKILL.md`；本地保留，不纳入本次提交 |
| 连接、截图与输入定位（当前分支缺失，原路径：`../.codex/skills/lobecorp-minecraft-mcp/references/connection-and-input.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-minecraft-mcp/references/connection-and-input.md`；本地保留，不纳入本次提交 |
| Debug 与控制归属（当前分支缺失，原路径：`../.codex/skills/lobecorp-minecraft-mcp/references/debug-ownership.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-minecraft-mcp/references/debug-ownership.md`；本地保留，不纳入本次提交 |
| 固定操作快速流程（当前分支缺失，原路径：`../.codex/skills/lobecorp-minecraft-mcp/references/fast-flows.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-minecraft-mcp/references/fast-flows.md`；本地保留，不纳入本次提交 |
| Lobecorp Minecraft MCP（当前分支缺失，原路径：`../.codex/skills/lobecorp-minecraft-mcp/SKILL.md`；见[问题汇总](issues.md)） | `.codex/skills/lobecorp-minecraft-mcp/SKILL.md`；本地保留，不纳入本次提交 |

## 记忆与 FAQ（原字节保留）

| 文档 | 路径与维护状态 |
|---|---|
| [.cherry-assistant/faq.md](../.cherry-assistant/faq.md) | `.cherry-assistant/faq.md`；已跟踪 |
| Lobecorp Mod Architecture Decisions（本地未发布定位：`../memory/FACT.md`，不作为公共证据入口） | `memory/FACT.md`；本地保留，不纳入本次提交 |

## 规则、入口与架构

| 文档 | 路径与维护状态 |
|---|---|
| [Lobecorp 项目开发规范](../AGENTS.md) | `AGENTS.md`；已跟踪 |
| [NeoForge 事件监听规范](event-registration.md) | `docs/event-registration.md`；已跟踪 |
| [Photon 未解决问题汇总](issues.md) | `docs/issues.md`；已跟踪 |
| [Java 25 语法与注释规范](java-25-language-and-comments.md) | `docs/java-25-language-and-comments.md`；已跟踪 |
| [Lobecorp Java 架构说明](java-architecture.md) | `docs/java-architecture.md`；已跟踪 |
| [Lobecorp Java 类整理记录](java-class-organization.md) | `docs/java-class-organization.md`；已跟踪 |
| [2026-10-05 Java 整理与 Photon 修复记录](java-formatting-audit.md) | `docs/java-formatting-audit.md`；已跟踪 |
| [Java 包整理审批计划](java-package-organization-plan.md) | `docs/java-package-organization-plan.md`；已跟踪 |
| [Photon 工作区文档总索引](README.md) | `docs/README.md`；本次新增索引 |
| [Lobecorp 实体、技能与表现契约](standards/entity-contracts.md) | `docs/standards/entity-contracts.md`；已跟踪 |
| [Lobecorp Java、接口与包规范](standards/java-and-packages.md) | `docs/standards/java-and-packages.md`；已跟踪 |
| [Lobecorp 工作流程与收尾规则](standards/workflow.md) | `docs/standards/workflow.md`；已跟踪 |
| [Lobecorp Photon 工作区](../README.md) | `README.md`；已跟踪 |

## 项目参考

| 文档 | 路径与维护状态 |
|---|---|
| [项目基线与系统边界](project-reference/01-project-baseline.md) | `docs/project-reference/01-project-baseline.md`；已跟踪 |
| [开发规则与工作流程](project-reference/02-rules-and-workflow.md) | `docs/project-reference/02-rules-and-workflow.md`；已跟踪 |
| [源码架构与数据流](project-reference/03-architecture-and-data.md) | `docs/project-reference/03-architecture-and-data.md`；已跟踪 |
| [内部 API、注册表、资源与依赖](project-reference/04-api-registries-resources.md) | `docs/project-reference/04-api-registries-resources.md`；已跟踪 |
| [IDE、MCP 与本地 Skill](project-reference/05-ide-mcp-skills.md) | `docs/project-reference/05-ide-mcp-skills.md`；已跟踪 |
| [仓库内本地 MCP 资料](project-reference/06-local-mcp-reference.md) | `docs/project-reference/06-local-mcp-reference.md`；已跟踪 |
| [证据、验证结果与维护清单](project-reference/07-evidence-and-gaps.md) | `docs/project-reference/07-evidence-and-gaps.md`；已跟踪 |
| [外部通用开发文档、MCP、Skill 与 API 参考](project-reference/08-external-development-reference.md) | `docs/project-reference/08-external-development-reference.md`；已跟踪 |
| [Photon、GeckoLib 与 ParticleStorm 粒子接入研究](project-reference/09-particle-effects-research.md) | `docs/project-reference/09-particle-effects-research.md`；已跟踪 |
| [Lobecorp 项目资料总索引](project-reference/README.md) | `docs/project-reference/README.md`；已跟踪 |

## 共同系统契约与其他专题

| 文档 | 路径与维护状态 |
|---|---|
| [服务端动画、根运动与空间附着](systems/animation-runtime.md) | `docs/systems/animation-runtime.md`；已跟踪 |
| [实体动画与视觉同步](systems/animation.md) | `docs/systems/animation.md`；已跟踪 |
| [通用空间附着](systems/attachments.md) | `docs/systems/attachments.md`；已跟踪 |
| [系统修改记录](systems/changes.md) | `docs/systems/changes.md`；已跟踪 |
| [客户端表现与 UI](systems/client-ui.md) | `docs/systems/client-ui.md`；已跟踪 |
| [仇恨、群体与阵营](systems/combat.md) | `docs/systems/combat.md`；已跟踪 |
| [指挥家系统](systems/conductor.md) | `docs/systems/conductor.md`；已跟踪 |
| [实体与 AI](systems/entities-ai.md) | `docs/systems/entities-ai.md`；已跟踪 |
| [实体抓取](systems/entity-grab.md) | `docs/systems/entity-grab.md`；已跟踪 |
| [实体技能](systems/entity-skills.md) | `docs/systems/entity-skills.md`；已跟踪 |
| [事件入口与分发](systems/events.md) | `docs/systems/events.md`；已跟踪 |
| [四色伤害与生物资源](systems/four-color-damage.md) | `docs/systems/four-color-damage.md`；已跟踪 |
| [命中框](systems/hitbox.md) | `docs/systems/hitbox.md`；已跟踪 |
| [Minecraft MCP 外部制品消费](systems/minecraft-mcp-artifacts.md) | `docs/systems/minecraft-mcp-artifacts.md`；已跟踪 |
| [Minecraft MCP 后台接管、鼠标释放与帧率](systems/minecraft-mcp-background-control.md) | `docs/systems/minecraft-mcp-background-control.md`；已跟踪 |
| [Minecraft MCP 结构化操控](systems/minecraft-mcp-controls.md) | `docs/systems/minecraft-mcp-controls.md`；已跟踪 |
| [Minecraft MCP 工作区接入](systems/minecraft-mcp-workspace.md) | `docs/systems/minecraft-mcp-workspace.md`；已跟踪 |
| [Minecraft MCP](systems/minecraft-mcp.md) | `docs/systems/minecraft-mcp.md`；已跟踪 |
| [系统导航补充与历史证据](systems/navigation-notes.md) | `docs/systems/navigation-notes.md`；已跟踪 |
| [网络协议与同步](systems/network.md) | `docs/systems/network.md`；已跟踪 |
| [粒子效果与渲染](systems/particle-rendering.md) | `docs/systems/particle-rendering.md`；已跟踪 |
| [玩家动画](systems/player-animation.md) | `docs/systems/player-animation.md`；已跟踪 |
| [玩家 HUD、屏幕滤镜与四色护盾](systems/player-hud-shields.md) | `docs/systems/player-hud-shields.md`；已跟踪 |
| [系统文档索引](systems/README.md) | `docs/systems/README.md`；已跟踪 |
| [注册、附件与数据契约](systems/registration-data.md) | `docs/systems/registration-data.md`；已跟踪 |
| [资源、翻译与数据生成](systems/resources-datagen.md) | `docs/systems/resources-datagen.md`；已跟踪 |
| [屏幕伤害粒子](systems/screen-damage-particles.md) | `docs/systems/screen-damage-particles.md`；已跟踪 |
| [世界文本粒子](systems/text-particles.md) | `docs/systems/text-particles.md`；已跟踪 |
| [动态视觉精细度与自动 LOD](systems/visual-quality.md) | `docs/systems/visual-quality.md`；已跟踪 |
| [工作区目录与迁移](systems/workspace-layout.md) | `docs/systems/workspace-layout.md`；已跟踪 |
| [原始 dev 与独立工作区同步](systems/workspace-sync.md) | `docs/systems/workspace-sync.md`；已跟踪 |
| [Photon MCP 部分能力移植](third-party/photon2mcp.md) | `docs/third-party/photon2mcp.md`；已跟踪 |

## Photon 技术、性能与阶段证据

| 文档 | 路径与维护状态 |
|---|---|
| [BenchMod 基准工具链](systems/benchmod.md) | `docs/systems/benchmod.md`；已跟踪 |
| [Photon、LDLib2 与 KilaGraph 依赖升级](systems/photon-dependency-upgrade.md) | `docs/systems/photon-dependency-upgrade.md`；已跟踪 |
| [Photon 原始编辑器设计恢复（2026-10-22）](systems/photon-editor-design-restore-20261022.md) | `docs/systems/photon-editor-design-restore-20261022.md`；已跟踪 |
| [Photon 粒子编辑器](systems/photon-editor.md) | `docs/systems/photon-editor.md`；已跟踪 |
| [Photon 特效 MCP API](systems/photon-effect-mcp-api.md) | `docs/systems/photon-effect-mcp-api.md`；已跟踪 |
| [Photon 特效 MCP](systems/photon-effect-mcp.md) | `docs/systems/photon-effect-mcp.md`；已跟踪 |
| [Photon 发射器发射器](systems/photon-emitter-spawner.md) | `docs/systems/photon-emitter-spawner.md`；已跟踪 |
| [Photon 极端优化执行文档（供 GPT 编码代理）](systems/photon-extreme-execution-gpt-20261025.md) | `docs/systems/photon-extreme-execution-gpt-20261025.md`；已跟踪 |
| [Photon 极端场景优化计划（最坏情况）](systems/photon-extreme-optimization-plan-20261025.md) | `docs/systems/photon-extreme-optimization-plan-20261025.md`；已跟踪 |
| [G1 全部代码优先实施记录](systems/photon-g1-code-stage-20261021.md) | `docs/systems/photon-g1-code-stage-20261021.md`；已跟踪 |
| [真实激光架构级诊断与优化方案（含光影）](systems/photon-laser-architecture-20261024.md) | `docs/systems/photon-laser-architecture-20261024.md`；已跟踪 |
| [真实女皇激光测试与优化方案](systems/photon-laser-optimization-20261024.md) | `docs/systems/photon-laser-optimization-20261024.md`；已跟踪 |
| [Photon／LDLib2 修复与兼容对照](systems/photon-ldlib2-fixes.md) | `docs/systems/photon-ldlib2-fixes.md`；已跟踪 |
| [Photon 模型特效](systems/photon-model-effects.md) | `docs/systems/photon-model-effects.md`；已跟踪 |
| [模型特效与高级特性重设计方案](systems/photon-model-redesign.md) | `docs/systems/photon-model-redesign.md`；已跟踪 |
| [Photon 命名动画与参数控制](systems/photon-named-animations.md) | `docs/systems/photon-named-animations.md`；已跟踪 |
| [当前性能静态审查与优化计划](systems/photon-performance-audit.md) | `docs/systems/photon-performance-audit.md`；已跟踪 |
| [Photon 性能检测工具](systems/photon-performance-tool.md) | `docs/systems/photon-performance-tool.md`；已跟踪 |
| [Photon 重设计实施进度](systems/photon-redesign-progress.md) | `docs/systems/photon-redesign-progress.md`；已跟踪 |
| [Photon 自建系统移除实施记录](systems/photon-removal-result.md) | `docs/systems/photon-removal-result.md`；已跟踪 |
| [Photon 渲染架构优化报告（含 Mixin 方案）](systems/photon-render-architecture-mixin-20261024.md) | `docs/systems/photon-render-architecture-mixin-20261024.md`；已跟踪 |
| [G1 全量资源往返与独立包验收](systems/photon-resource-acceptance-20261021.md) | `docs/systems/photon-resource-acceptance-20261021.md`；已跟踪 |
| [特效系统重写需求与现有文件兼容契约](systems/photon-rewrite-requirements.md) | `docs/systems/photon-rewrite-requirements.md`；已跟踪 |
| [Photon 静态审查修复与完整轨道接线](systems/photon-static-fixes-20261021.md) | `docs/systems/photon-static-fixes-20261021.md`；已跟踪 |
| [Photon 当前能力与恢复入口](systems/photon-workspace.md) | `docs/systems/photon-workspace.md`；已跟踪 |
| [女皇激光与瞬时星束的技能特效](systems/queen-beam-skill-effects.md) | `docs/systems/queen-beam-skill-effects.md`；已跟踪 |

## 外部制品说明（只读边界）

| 文档 | 路径与维护状态 |
|---|---|
| [Minecraft MCP release artifacts](../lib/minecraft-mcp/README.md) | `lib/minecraft-mcp/README.md`；外部制品，只读 |

## 工具说明

| 文档 | 路径与维护状态 |
|---|---|
| [Lobecorp Normify](../tools/lobecorp-normify/README.md) | `tools/lobecorp-normify/README.md`；已跟踪 |
