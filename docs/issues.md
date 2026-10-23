# Photon 未解决问题汇总

更新：2026-10-23。此页是 codex/photon 工作区唯一维护当前问题状态的文档，范围只限 Photon／特效及其必要工具边界。能力、源码、已完成实现和历史检查见 [Photon 总览](systems/photon-workspace.md)。已解决项从当前待办移除；历史页保留当时证据，不再维护另一份当前问题列表。

下方功能续做历史阶段未运行验证；2026-10-24 已按用户授权完成 Sodium＋BenchMod、Minecraft MCP／Debug 与真实激光性能采样，实际证据见 [性能审查](systems/photon-performance-audit.md)。“未验证”仍表示缺少对应证据，不等同于确认存在运行缺陷。

## 性能测试进度（2026-10-23）

- 授权与计划：继续使用 Sodium＋BenchMod、Minecraft MCP 与 IDEA Debug 排查模型渲染和动画，优先建立空场景、单模型、16 模型、静态／动画与首次创建对照，再测试迁入的真实激光资源。采样区间不设断点、不切换焦点；Debug 仅用于测量区间之外的执行路径核对。
- 当前阶段：本轮采样与归因已完成，等待针对热点的窄范围优化。无 Debug 的 8 个 Photon 用例及最终报告校验通过；两轮真实激光各完成 90 秒／3 次施放，包含 spark 开关与 GPU 帧采集。已确认 CPU 几何提交、动画分配和同步发射阻塞；并非 S01–S23 全部解决。完整数字、原始证据和未覆盖项见性能审查页。
- 已完成前置：118 项正式资源已在模组内，81 项新增迁移提交为 1a5c2c3，原字节备份与哈希核对见 [资源说明](systems/resources-datagen.md#photon-内置资源)。本轮基准代码与证据文档已纳入本地提交 9d83824，未推送，用户原有改动保持。
- 原始证据与结论将写入 [性能审查页](systems/photon-performance-audit.md)，本页维护当前进度和未解决项，完成后再更新对应问题状态。

## 当前推进边界

2026-10-23 按用户最新要求：有异议、疑问或尚未确认的实现先不做，只在此记录；已确认且可独立推进的授权事项不因这些问题暂停。当前已确认功能已提交至 330d297a，资源迁移提交为 1a5c2c3。该段原属功能续做边界；本轮性能采样已获授权并执行，P06 的冷热长帧证据已补充。P01／P02／P05／P10 的完整原生或人工验收仍未覆盖，P03／P04／P09 需要来源或处理范围确认，P07 需要历史换行范围确认，P08 尚非已确认代码缺陷，不以清零提示为由扩大修改。没有因本条协调要求新增功能、恢复资源、修改既有测试或启动验证。

## 当前问题

| 编号 | 现象与当前状态 | 根因与证据 | 下一步及用户介入 |
|---|---|---|---|
| P01 | G1 未完成：F11 编辑 revision 冲突、F12 撤销后完整数据不同的原生场景尚未复测 | 原始制作轮 18 份输入中 16 通过；后续编写态／预览态隔离代码已存在，但没有本轮原生复现结果。见 [原始验收记录](systems/photon-resource-acceptance-20261021.md) 与 PhotonAuthoringSnapshotUtil | 获授权后先复现两份原工程，再核对保存重开、撤销／重做的原树与身份；需要后续测试／游戏授权 |
| P02 | 独立导出包、依赖负例与最终 501 矩阵未完成 | 历史 20 包仅 8 包完成当轮运行，12 包被 F10 越界引用审查中断；后续读取器检查不能替代独立包复测。见 [代码阶段](systems/photon-g1-code-stage-20261021.md)、[验收证据](systems/photon-resource-acceptance-20261021.md) | 授权后完成所有源／缓存／新导出包独立运行、缺 PNG 副本负例和矩阵汇总；不从开发目录补依赖；需运行授权 |
| P03 | 基线中 5 条历史来源不可用 | 501 条来源中 496 条曾完成结构／字节往返，另 5 条来源缺失；基线为 [photon-resource-baseline-20261019.json](systems/photon-resource-baseline-20261019.json) | 保留 sourceUnavailable 状态；需要用户提供明确来源，或确认这些条目的处理方式；不能假造通过 |
| P04 | 原始资源 fixture 测试保留失败，整体构建也有既有失败边界 | PhotonModelPoseSamplerTest 的 samplesExistingModelOccurrencesWithoutChangingOriginals 依赖本工作区不存在的 src/main/resources/fxpacks；历史回读为 9 通过／1 失败。迁移期整体 build 还记录资源基线和 reserveBuildIdentity 配置缓存问题。见 [工作区历史](systems/workspace-layout.md) | 本轮不改断言／复制资源／修构建配置；以后授权范围明确后核对真实 fixture 与配置缓存，需测试／构建授权 |
| P05 | 编辑器与旧备份完整功能／视觉对照未验收 | 本轮补齐默认部件、骨骼／morph 重命名、部件动画复制、选择／gizmo／关键帧和材质事务；IDEA 静态无错误不能证明完整交互与旧设计一致。设计来源为 04a3bab，见 [设计恢复](systems/photon-editor-design-restore-20261022.md) | 人工按选择、变换、环切、拓扑、UV、材质、骨骼／morph、动画、保存重开与取消清理逐项验收；需游戏授权和人工视觉判断 |
| P06 | 首次施放阻塞已复现；跟随、过渡和完整技能边界仍未验收 | 两轮真实采样首发最长帧约 861／856 ms；三次施放阶段 marks 存在，code_animation_render_samples 为空，不能证明逐帧混合已解决。见 [性能审查](systems/photon-performance-audit.md) | 优先拆分并优化同步创建／准备链；墙体、瞬时星束、取消、晚追踪、卸载和人工观感仍需专门场景 |
| P07 | 部分源码的历史 LF／CRLF 问题尚未统一 | 本轮 LcClientAnimations.java、QueenBeamEffects.java 的换行回归已按用户 A 授权恢复为原 CRLF，规范化代码 SHA256 相同；其他已有 LF 保留原样 | 如需统一历史 LF，另行确认具体范围；不扩大本次仅两文件的兜底授权 |
| P08 | 本轮修改代码保留 8 项既有 IDE 提示，扩查相关 WIP 后合计 12 项 | LcAnimationBoneAdapter 返回值未使用 1 项、LcClientAnimations.entityPose 未使用 1 项；PhotonChildSpawner 重写形参空值注解 5 项、emitNow 返回值未使用 1 项。扩查另有 PhotonModelOutput 布尔调用提示、PhotonNamedMixer 冗余转换、PhotonNamedRuntime.listener 未使用与 TimelinePlayerMixin 借用运行态资源提示各 1 项。本轮新增的冗余条件提示已修正；没有为清零提示扩展无关清理 | 继续保留，不作为已确认运行错误；若用户要维护这些 API／注解，再单独确定范围 |
| P09 | 历史专题、手写工程和截图证据在本工作区缺失 | 缺失资料清单见下节。先前文档审计将本地缺失文件链接从 205 处降为 30 处；剩余为 changes.md 与 player-animation.md 的共同基础历史截图。没有恢复任何资产或归档聊天 | 需要明确资料／资产来源；本轮仅登记、解除失效跳转并保留历史路径，不推断这些资料已经可用 |
| P10 | 本轮 4 个新增回归用例及代码没有执行验证 | PhotonModelMeshEditingTest 新增默认部件写回、过期环切扩展、骨骼／morph 重命名引用、复制部件轨道的用例；只有静态诊断，无测试结果 | 获明确授权后再运行；当前不得合并进历史通过数 |

## 本轮确认的性能问题

| 编号 | 现象与当前状态 | 已确认证据与边界 | 后续方向 |
|---|---|---|---|
| P11 | 静态自定义模型仍逐帧完整 CPU 几何写入，尚未优化 | 16 静态模型 JFR 中 write inclusive 栈约占渲染执行样本 75.6%，重复用例 75.7%；姿态缓存未缓存最终几何。详见性能审查 | 保留材质、透明排序、变换语义的稳定拓扑／几何复用；不降低 LOD 或画质 |
| P12 | 动画 CPU 蒙皮及帧对象分配过重，尚未优化 | 16 动画 sample 为 222／462 执行样本，约 23 秒录制的渲染线程分配计数增加 19.554 GiB；包含预热，不是泄漏或存活量 | 预编译权重／索引、复用局部求值缓冲，保持不可变发布与所有权，做同内容 A/B |
| P13 | 空场景存在未归因 1.421 秒长帧 | 无 Debug 末尾空用例第 7624／9695 帧出现；未过滤、未归因模型或导出，不能用其最大值证明模型缺陷 | 独立异常帧／系统等待采样，保留当前原始证据 |

## 缺失资料

| 缺失原文或资产 | 可读的现存入口与限制 |
|---|---|
| photon-performance.md、photon-world-performance-comparison.md、photon-detailed-performance-plan.md、laser-release-profile.md | [检测工具](systems/photon-performance-tool.md)、[静态候选](systems/photon-performance-audit.md)；这些不是缺失原报告，也没有原始实测数据 |
| shader-compatibility-exit.md | [粒子与渲染](systems/particle-rendering.md)；没有新增 EXIT 兼容结论 |
| queen-star-beam.md、queen-arcana-beam.md、light-ball.md、laser-spell.md | [女皇技能表现](systems/queen-beam-skill-effects.md)、[资源规范](systems/resources-datagen.md)；旧工程结构与对象索引未找回 |
| effect-authoring-lessons.md、photon-effect-authoring-history.md | [制作 Skill](../.codex/skills/lobecorp-effect-authoring/SKILL.md) 与 [修改记录](systems/changes.md) 只作现存规则／历史入口，不替代缺失案例 |
| 共同基础的玩家／附着历史截图 | 保留现有历史条目和缺失说明，不将 horde 或其他工作区的验收写入本页 |

## 证据与恢复边界

当前代码与历次检查、Git 恢复引用见 [总览的检查与备份](systems/photon-workspace.md#检查与备份)。本轮备份根为 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-resume-backup-20261023。修复关闭对应问题时从本页移除当前待办，必要结论与实际证据写入总览／修改记录；不在多个历史页复制状态。
