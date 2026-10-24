# Photon 当前能力与恢复入口

核对日期：2026-10-23。适用真实根为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`，分支为 `codex/photon`。本页基于当前工作文件、IDEA 文件读取及现有检查记录；已确认的网格／编辑器／光束续做源码与文档已纳入本地提交 330d297a，尚未推送。配置及两份资源验收测试的既有修改继续保留，提交不代表构建、游戏或完整能力验收通过。

## 当前任务进度

最近更新：2026-10-24 09:00（北京时间）。性能基准与证据提交为 9d83824，状态回写提交为 476cffe，未推送；本轮测试前／测试后优化文档与诊断另见 [测试后文档](photon-optimization-posttest-20261024.md)。

- 已完成详细列表：[待优化详细列表](../issues.md#待优化详细列表)集中列出 P11／P12／P06 的确认热点、P13 的待归因异常和 S01–S23 后续处理；包含代码入口、执行顺序、实现约束与关闭条件。当前只整理文档，生产优化仍未实施；下一步从已确认热点确定实施范围，再按同内容进行复测。本次未新增主动验证。
- 已完成本轮诊断：BenchMod Photon 两次运行（一次窗口失焦 INVALID、一次 PASSED），JFR 归因、Debug 三处调用路径核对与 Minecraft MCP ping／截图；根因与修正建议见 [测试前文档](photon-optimization-pretest-20261024.md)、[测试后文档](photon-optimization-posttest-20261024.md)。未修改业务代码，无优化后 A/B。
- 已完成：正式 Photon 资源迁入模组，118 项目标与原始 SHA256 一致，资源与文档提交为 1a5c2c3；81 项新增，37 项复用，原始字节备份见 [资源说明](resources-datagen.md#photon-内置资源)。
- 已完成本轮采样：无 Debug 的 8 个 Photon 用例与最终报告校验通过；两轮真实激光各 90 秒／3 次施放，完成 spark 开关和 GPU 帧数据对照。16 静态／动画均值 3.258／5.784 ms、P99 6.256／10.477 ms；静态逐帧几何写入、动画 CPU 蒙皮／分配及同步创建已被调用栈与样本确认，完整证据见 [性能审查](photon-performance-audit.md)。
- 收尾检查完成：Provider 增加显式 Photon 过滤守卫，普通基准不自动登记这些负载；再编译成功（3 秒），最终代码／配置 0 错误、2 项提示，5 份文档 0 错误、18 项既有截图警告。生产渲染代码未优化。下一步按已确认热点确定窄范围优化，再做同条件复测；当前问题与待确认项只在 [唯一问题表](../issues.md) 维护。
- 实际检查与边界：IDEA 静态 0 错误、两项提示；Gradle 准备编译、无 Debug 最终报告校验、两轮真实客户端均正常结束。固定 19877 MCP 已取得 pong／connected=true；本轮临时断点移除、Debug 结束，所有临时游戏和桥接均关闭。原始报告分别备份于可视化根 photon-bench-normal-baseline-20261024、photon-bench-debug-baseline-20261023、photon-real-laser-spark-20261024、photon-real-laser-plain-20261024。未实施生产优化，没有优化后 A/B 收益；S01–S23 不宣称全部解决，动画逐帧字段为空和未归因长帧等边界见唯一问题表与性能审查。

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
| 当前问题与恢复 | [Photon 唯一问题汇总](../issues.md) | 仅维护未解决项；已完成能力、实际检查和备份保留在本页 |
| 编辑器布局来源 | [原始设计恢复](photon-editor-design-restore-20261022.md)、[编辑器](photon-editor.md) | 04a3bab 为设计来源；2026-10-22 恢复后的交互／视觉未全验收 |
| 模型、命名与子特效旧用法 | [模型](photon-model-effects.md)、[命名动画](photon-named-animations.md)、[发射器发射器](photon-emitter-spawner.md) | 页首当前入口之后的旧 API、旧回归与性能说明为历史，不能直接套用 |
| G1 资源来源与问题台账 | [501 条验收记录](photon-resource-acceptance-20261021.md)、[基线 JSON](photon-resource-baseline-20261019.json) | 496 条来源往返与 5 条来源不可用分别记录，未完成最终矩阵 |
| G1 代码与集中检查 | [代码阶段](photon-g1-code-stage-20261021.md)、[静态修复与集中记录](photon-static-fixes-20261021.md)、[实施进度](photon-redesign-progress.md) | 记录各阶段真实结果，不把早期失败或局部成功推广为最终状态 |
| 需求与架构目标 | [重写需求](photon-rewrite-requirements.md)、[模型重设计](photon-model-redesign.md) | 需求基线和方案，不是全部实施完成清单 |
| 旧核心移除 | [计划](photon-removal-plan.md)、[结果与备份](photon-removal-result.md) | 2026-10-19 历史，不代表当前恢复代码不存在 |
| 依赖与兼容维护 | [依赖升级](photon-dependency-upgrade.md)、[Photon／LDLib2 修复](photon-ldlib2-fixes.md) | 修复条目按版本／阶段核对；删除的类型引用只保留历史文本 |
| 渲染与共同基础 | [粒子渲染](particle-rendering.md)、[视觉精细度](visual-quality.md)、[资源](resources-datagen.md)、[文本粒子](text-particles.md)、[伤害粒子](screen-damage-particles.md) | 共同基础保留；实体动画和模型 LOD 不属于本轮文档的重新验收范围 |
| 问题与检查记录 | [唯一问题汇总](../issues.md)、[修改记录](changes.md) | 文档缺口只在问题表登记，历次检查与已完成结论在本页保留 |

## 本轮续做（2026-10-23）

用户授权继续完成功能并严格审查；没有构建、Data、测试、同步、重载或启动游戏。当前未解决项只维护在 [Photon 问题汇总](../issues.md)，本页保存能力、检查与恢复信息。

- 网格按钮与 Delete 共用删除路径；点／边／面选择按当前模式派生，不保留已取消边的旧顶点。框选按模式写回，部件框选选中完整落入矩形的一张面的所属部件；当前工作区仍为单部件选择。
- 原生 gizmo 已开始拖拽时网格拾取不再更改选区；左键释放和拖拽结束提交候选，右键／Escape／切换或 revision 过期取消。取消结束原生拖拽并恢复 gizmo；无效候选不会提交前一次有效预览。
- gizmo 使用部件世界方向并在增量中消除基础方向，局部旋转／缩放不混入已有姿态；奇异或非有限矩阵停用手柄。几何操作仍由候选验证及现有会话统一提交。
- 默认部件可在没有显式 parts 的当前模型中显示与编辑；只读不补写原树，显式部件修改才物化数据。默认部件的变换不再被 document() 忽略。
- 骨骼／morph 重命名更新父链、权重和对应片段轨道，保留未知字段；部件复制同时复制自己的动画轨道，复制几何仍保留已有权重／morph 元数据。
- 环切提交额外核对原面扩展数据；合并后位置没有改变的代表顶点保留原始数值来源。未恢复历史文件格式。
- 关键帧拖动限定片段时长、拒绝同刻冲突、核对 revision，关闭后回调不写回；同刻重新写键只更新已知 TRS 或 value，不覆盖未知变换字段。文本框中的 Delete 不删除已选关键帧。
- PNG 文件选择从打开选择框时捕获 revision，异步导入完成后不能把旧窗口选择应用到后续新状态。
- 光束普通 tick 不再重复执行几何射线查询，首次输出和恢复冻结仍显式更新；参数签名按句柄解析，数值未变时复用输入映射，后续子实例仍接收参数。准备未成功时可在下一次蓄力 tick 重试；帧更新失败时清理并记录该实例，避免反复重建。
- 阶段姿态混合复用已有客户端收包时钟外推，不再把最后一份 serverTick 当作持续时间。服务端伤害、射程、技能时长、实体动画资源与模型 LOD 不在本轮修改范围。
- 新增 4 个数据保留／过期计划回归用例，未执行；不能把静态通过、源码存在或历史夹具成绩当作 G1 与视觉／性能通过。

## 检查与备份

本轮修改的 15 个 Java／测试文件经 IDEA 统一诊断均 analyzed、无截断、0 错误；保留 8 项既有提示，详见问题汇总。新增冗余条件提示已修正。修改行通过 IDEA 格式化，导入优化和成员重排关闭。两份由本轮 IDEA 补丁改为 LF 的原 CRLF 文件已按用户 A 授权用脚本恢复，规范化代码 SHA256 不变；其他既有 LF 范围未扩大处理。随后将检查扩展到 40 个相关 WIP 代码／配置／测试文件与 18 份文档，首次大批次超时后已按未覆盖文件分组补查，最终 58 份均 analyzed、无截断、0 错误；代码保留 12 项存量提示，changes.md 保留 18 项历史截图警告。当前文档文件与专题锚点检查没有新增失效目标，既有截图缺失继续登记。

| 历史阶段 | 已记录的实际结果 | 边界 |
|---|---|---|
| 2026-10-22 设计恢复 | 19 文件诊断 0 错误／0 警告，IDEA 编译与 Data 成功 | 属于当时布局恢复，未做该轮人工视觉验收；详情见 [设计恢复](photon-editor-design-restore-20261022.md) |
| 2026-10-22 网格 WIP | 40 份相关代码／配置无错误，保留既有警告；IDEA 构建成功。Data 日志 ij_run__Data_17237209521181167582.log 显示 23 个输出、写入 0／删除 0 | 日志原路径为 C:/Users/wang_/AppData/Local/JetBrains/IntelliJIdea2026.2/tmp；不代表本轮重新构建 |
| 2026-10-23 历史 XML 回读 | MeshEditing 10/10；PoseSampler 9 通过／1 失败；Reader 27/27；StagedResources 1/1，496 项 SHA、234 个模型按 0／5／100 CPU 采样 | 不是本轮执行；5 项缺源、原 fixture 失败及原生验收边界见 [唯一问题表](../issues.md) |
| 2026-10-23 文档审计 | 33 份文档静态 0 错误，18 项既有截图警告；本地文件缺失链接由 205 处降为 30 处 | 旧审计完成，原审计页已合并；未访问远端网址或运行业务检查 |
| 本轮功能续做 | 15 个修改代码文件静态 0 错误；4 个新增测试只检查源码，未执行 | 未构建／Data／业务测试／游戏／视觉／性能验收；当前问题只记于 issues.md |

| 恢复引用 | 对象 | 用途 |
|---|---|---|
| refs/backups/before-editor-mesh-g1-20261022/head | bdc23609e5ee82a86beb57178077deed098cd6ed | 原开发前 HEAD |
| refs/backups/before-editor-mesh-g1-20261022/wip | 95b26dbf0cb967b341f35c3e4e4969e29b25d570 | 原开发前 WIP |
| refs/backups/editor-mesh-g1-resume-20261023/head | ca4fd9203bfd18c491044f9abc369923f06fc960 | 迁移后原续做 HEAD |
| refs/backups/editor-mesh-g1-resume-20261023/wip | 170f9964f164fb140777d5e8b409839a0c5d2706 | 迁移后原业务 WIP |
| refs/backups/photon-resume-20261023/head | d4a5a998cd12bd1a1a9b384be28da4f148d30987 | 本轮续做前 HEAD |
| refs/backups/photon-resume-20261023/wip | 669f71fbe25354711909f604b3f6f67b9dde2738 | 本轮续做前 WIP |

本轮修改前 318 个相关文件的原始字节、Git 索引／暂存条目、状态与 HEAD 备份在 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-resume-backup-20261023；修改前测试文件也已另存同根对应路径。前轮文档审计备份在同级 photon-docs-backup-20261023。已合并的旧网格续做页与审计页均保留于本轮原始字节备份，不删除既有 Git 备份或兼容 junction。恢复前先比较当前差异，再经 IDEA 局部处理，不 reset 或覆盖用户内容。

## 后续恢复顺序

1. 从 [唯一问题表](../issues.md) 选择当前问题，再查对应历史证据；旧页的待办和状态仅代表当时阶段。
2. 核对真实根、分支、HEAD、现有暂存与打开的编辑器内容；不跨工作区操作，不自动汇合或推送。
3. 只有取得对应授权后才运行构建、Data、测试或游戏；静态、运行、视觉和性能结果分开记录。
4. 当前未完成项关闭后从问题表移除，长期契约留在专题，证据留在总览／修改记录。
