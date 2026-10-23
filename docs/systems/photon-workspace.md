# Photon 当前能力与恢复入口

核对日期：2026-10-23。适用真实根为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`，分支为 `codex/photon`。本页基于当前工作文件、IDEA 文件读取及现有检查记录；用户尚未提交的业务代码也在核对范围内，不代表提交树已具备全部能力。

## 职责与工具边界

本工作区负责 Photon／特效；原始 dev 是共同地基，清道夫由 horde 工作区负责。共同实体、动画、网络、注册和资源文档继续保留引用，本轮没有验证或更新 horde 业务。旧 `C:/Users/wang_/.codex/worktrees/7903/Lobecorp` 仅为兼容 junction，当前工具显式传入真实根，不删除兼容入口或 Git worktree 保护锁。

- 规则：[项目规范](../../AGENTS.md)、[工作流程](../standards/workflow.md)、[系统索引](README.md)。
- Minecraft MCP：[外部制品](minecraft-mcp-artifacts.md)、[本工作区接入](minecraft-mcp-workspace.md)。只消费本地 `lib/minecraft-mcp-0.3.0.jar` 与 `lib/minecraft-mcp/bridge`，端口 `19877`。不恢复 `modules/minecraft-mcp`，不在消费者安装 npm 或重建桥接。
- Normify：[工具说明](../../tools/lobecorp-normify/README.md)。本工作区配置复用原始仓库 `tools/lobecorp-normify/dist/src/cli.js`，`--workspace-root` 和 cwd 均为本工作区；CLI 共享不表示数据输出可跨工作区。
- 当前 MCP 工具可见、IDEA 可读与游戏连接是独立状态；本轮未连接或运行游戏。
- 锁定依赖：[版本目录](../../gradle/libs.versions.toml)，Minecraft 26.1.2、NeoForge 26.1.2.100、Photon 26.1.2.3、LDLib2 26.1.2.42、KilaGraph 26.1.0.15；MCP 制品清单标记 Java 25。

## 当前能力与源码入口

下表的“存在”指当前源码接线；只有明确引用的历史记录包含当时验证，本轮文档审计没有执行业务测试。

| 功能 | 当前入口 | 能力与限制 |
|---|---|---|
| 扩展编辑器 | [PhotonEffectEditor](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonEffectEditor.java)、[编辑器说明](photon-editor.md) | `/lobecorp photon_editor` 为扩展窗口，`/photon_editor` 保留原生入口；两者的工程枚举与扩展加载有隔离 |
| 模型工程 | [PhotonModelProject](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelProject.java)、[PhotonModelWorkspace](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelWorkspace.java) | 原生 FX 承担预览；模型工程保留原始容器、meta、data 与未知字段；当前模型格式为 .mfxproj／.mfx／.mfxpack |
| 模型输出与轨道 | [LcPhotonModelOutputTypes](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonModelOutputTypes.java)、[PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) | `lobecorp_model_emitter` 与 `lobecorp_model_animation` 已注册；动态属性和模型采样由当前 model 包承担，旧专用渲染器不可据此视为恢复 |
| 高级网格 WIP | [PhotonModelWorkspaceMesh](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelWorkspaceMesh.java)、[PhotonModelEditUtil](../../src/main/java/org/unitego/lobecorp/util/photon/model/editor/PhotonModelEditUtil.java) | 选择、变换、挤出、复制、删除、UV、拓扑、环切及几何创建接私有候选和会话提交；与旧备份完整对齐及人工交互仍未验收 |
| 材质、骨骼与动画 | [PhotonModelWorkspaceResources](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelWorkspaceResources.java)、[PhotonModelPoseSampler](../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelPoseSampler.java) | 材质副本显式应用、骨骼／morph 和蒙皮采样存在；不声明材质视觉、全部资产或完整编辑行为通过 |
| 命名制作与播放 | [PhotonNamedController](../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedController.java)、[PhotonNamedRuntime](../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java)、[PhotonNamedAuthoringSession](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonNamedAuthoringSession.java) | 控制器保存制作定义，所属 FXRuntime 持有运行态；草稿、原树事务、句柄、轨道及测试预览接当前 playback 实现 |
| 子特效／发射器发射器 | [LcPhotonSpawnTypes](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonSpawnTypes.java)、[PhotonChildSpawner](../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java) | 保留类型 ID `lobecorp_emitter_spawner`，由当前调度器创建独占原生 FX；最多准备下一份未发射实例，来源变化或关闭时销毁 |
| 女皇技能表现 | [QueenBeamEffects](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java)、[技能边界](queen-beam-skill-effects.md) | 消费权威动画快照，帧回调更新锚点／参数；瞬时星束为已见蓄力的实例补一次呈现，伤害与时序仍由服务端技能决定 |
| 编写态／预览态 WIP | [PhotonAuthoringPose](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonAuthoringPose.java)、[PhotonAuthoringSnapshotUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonAuthoringSnapshotUtil.java) | 序列化副本排除仍由预览持有的分量，保留外部修改；F11／F12 原生复现尚未重跑 |
| MCP | [API 索引](photon-effect-mcp-api.md)、[LcPhotonMcpCommands](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonMcpCommands.java) | 当前源码登记 7 个读操作与 13 个写操作；能力位与路径以实际会话返回为准，登记数量不等于当前游戏已连接 |
| 性能检测 | [检测工具](photon-performance-tool.md)、[静态候选](photon-performance-audit.md) | 保留通用采集与分析入口；旧专用模型／动画统计和历史时间数字不自动适用于当前实现 |

## 专题与历史索引

| 目的 | 文档 | 阅读边界 |
|---|---|---|
| 最新恢复与未完成项 | [网格、光束与 G1](photon-mesh-beam-g1-20261023.md) | 当前业务 WIP、备份引用、检查记录、CRLF 受阻及恢复顺序 |
| 编辑器布局来源 | [原始设计恢复](photon-editor-design-restore-20261022.md)、[编辑器](photon-editor.md) | 04a3bab 为设计来源；2026-10-22 恢复后的交互／视觉未全验收 |
| 模型、命名与子特效旧用法 | [模型](photon-model-effects.md)、[命名动画](photon-named-animations.md)、[发射器发射器](photon-emitter-spawner.md) | 页首当前入口之后的旧 API、旧回归与性能说明为历史，不能直接套用 |
| G1 资源来源与问题台账 | [501 条验收记录](photon-resource-acceptance-20261021.md)、[基线 JSON](photon-resource-baseline-20261019.json) | 496 条来源往返与 5 条来源不可用分别记录，未完成最终矩阵 |
| G1 代码与集中检查 | [代码阶段](photon-g1-code-stage-20261021.md)、[静态修复与集中记录](photon-static-fixes-20261021.md)、[实施进度](photon-redesign-progress.md) | 记录各阶段真实结果，不把早期失败或局部成功推广为最终状态 |
| 需求与架构目标 | [重写需求](photon-rewrite-requirements.md)、[模型重设计](photon-model-redesign.md) | 需求基线和方案，不是全部实施完成清单 |
| 旧核心移除 | [计划](photon-removal-plan.md)、[结果与备份](photon-removal-result.md) | 2026-10-19 历史，不代表当前恢复代码不存在 |
| 依赖与兼容维护 | [依赖升级](photon-dependency-upgrade.md)、[Photon／LDLib2 修复](photon-ldlib2-fixes.md) | 修复条目按版本／阶段核对；删除的类型引用只保留历史文本 |
| 渲染与共同基础 | [粒子渲染](particle-rendering.md)、[视觉精细度](visual-quality.md)、[资源](resources-datagen.md)、[文本粒子](text-particles.md)、[伤害粒子](screen-damage-particles.md) | 共同基础保留；实体动画和模型 LOD 不属于本轮文档的重新验收范围 |
| 文档缺口与本轮检查 | [文档审计](photon-document-audit-20261023.md)、[修改记录](changes.md) | 缺失文件明确登记，不以同名新文档伪造原历史报告 |

## 未完成与恢复顺序

G1 未通过。最新未完成项包括高级网格／编辑器完整对照、部分源码 CRLF 恢复、F11 revision 冲突与 F12 撤销完整数据的原生复测、保存重开和独立导出包运行、5 条来源不可用的解释、资源 fixture 相关测试失败。激光首次开销、跟随和退出过渡仍需真实帧时间／P99／冷热样本及人工视觉验收。迁移期间整体 build 在 test 阶段失败，不写成完整构建成功。

1. 阅读最新 WIP 与备份引用，核对当前 HEAD、暂存、未暂存及打开的编辑器内容；本轮审计前 HEAD 为 `ef201e857ed7b9567f922c3fbf565ebcaccbf181`，仅是记录点。
2. 根据新业务授权确定代码范围；本轮只整理文档，未继续旧修复、恢复资源或解除保护。
3. 后续编辑通过 IDEA，先保留用户内容并解决对应修改范围的格式问题，再统一读取诊断。
4. 仅在获授权后运行构建、Data、测试、游戏；分别保存静态、运行、视觉与性能证据。旧记录中的启动和运行命令不是本轮执行授权。
5. 人工验收后再更新对应结果；不要因为源码存在、测试夹具通过、工具注册或资源字节不变而宣告 G1 完成。
