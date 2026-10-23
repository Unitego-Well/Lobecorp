# Photon 未解决问题汇总

更新：2026-10-23。此页是 codex/photon 工作区唯一维护当前问题状态的文档，范围只限 Photon／特效及其必要工具边界。能力、源码、已完成实现和历史检查见 [Photon 总览](systems/photon-workspace.md)。已解决项从当前待办移除；历史页保留当时证据，不再维护另一份当前问题列表。

本轮未构建、未运行测试／Data、未同步／重载或启动游戏。“未验证”表示缺少对应证据，不等同于已经确认存在运行缺陷。

## 当前推进边界

2026-10-23 按用户最新要求：有异议、疑问或尚未确认的实现先不做，只在此记录；已确认且可独立推进的授权事项不因这些问题暂停。当前已确认功能已提交至 330d297a。本次核对剩余项后，P01／P02／P05／P06／P10 需要后续运行或人工验收授权，P03／P04／P09 需要来源或处理范围确认，P07 需要历史换行范围确认，P08 尚非已确认代码缺陷，不以清零提示为由扩大修改。没有因本条协调要求新增功能、恢复资源、修改既有测试或启动验证。

## 当前问题

| 编号 | 现象与当前状态 | 根因与证据 | 下一步及用户介入 |
|---|---|---|---|
| P01 | G1 未完成：F11 编辑 revision 冲突、F12 撤销后完整数据不同的原生场景尚未复测 | 原始制作轮 18 份输入中 16 通过；后续编写态／预览态隔离代码已存在，但没有本轮原生复现结果。见 [原始验收记录](systems/photon-resource-acceptance-20261021.md) 与 PhotonAuthoringSnapshotUtil | 获授权后先复现两份原工程，再核对保存重开、撤销／重做的原树与身份；需要后续测试／游戏授权 |
| P02 | 独立导出包、依赖负例与最终 501 矩阵未完成 | 历史 20 包仅 8 包完成当轮运行，12 包被 F10 越界引用审查中断；后续读取器检查不能替代独立包复测。见 [代码阶段](systems/photon-g1-code-stage-20261021.md)、[验收证据](systems/photon-resource-acceptance-20261021.md) | 授权后完成所有源／缓存／新导出包独立运行、缺 PNG 副本负例和矩阵汇总；不从开发目录补依赖；需运行授权 |
| P03 | 基线中 5 条历史来源不可用 | 501 条来源中 496 条曾完成结构／字节往返，另 5 条来源缺失；基线为 [photon-resource-baseline-20261019.json](systems/photon-resource-baseline-20261019.json) | 保留 sourceUnavailable 状态；需要用户提供明确来源，或确认这些条目的处理方式；不能假造通过 |
| P04 | 原始资源 fixture 测试保留失败，整体构建也有既有失败边界 | PhotonModelPoseSamplerTest 的 samplesExistingModelOccurrencesWithoutChangingOriginals 依赖本工作区不存在的 src/main/resources/fxpacks；历史回读为 9 通过／1 失败。迁移期整体 build 还记录资源基线和 reserveBuildIdentity 配置缓存问题。见 [工作区历史](systems/workspace-layout.md) | 本轮不改断言／复制资源／修构建配置；以后授权范围明确后核对真实 fixture 与配置缓存，需测试／构建授权 |
| P05 | 编辑器与旧备份完整功能／视觉对照未验收 | 本轮补齐默认部件、骨骼／morph 重命名、部件动画复制、选择／gizmo／关键帧和材质事务；IDEA 静态无错误不能证明完整交互与旧设计一致。设计来源为 04a3bab，见 [设计恢复](systems/photon-editor-design-restore-20261022.md) | 人工按选择、变换、环切、拓扑、UV、材质、骨骼／morph、动画、保存重开与取消清理逐项验收；需游戏授权和人工视觉判断 |
| P06 | 激光首次开销、跟随和结束过渡没有本轮实测成绩 | 源码已消除普通更新中的重复射线、复用参数数据，并修正基于固定快照时间的阶段混合；尚无冷热、最长帧、P99 或人工观感证据。入口为 QueenBeamEffects、LcAnimationBoneAdapter，见 [总览](systems/photon-workspace.md) | 授权后分别验证冷／热启动、连续跟随、墙体截断、瞬时星束、恢复、取消、晚追踪和卸载；需游戏授权与人工判断 |
| P07 | 部分源码的历史 LF／CRLF 问题尚未统一 | 本轮 LcClientAnimations.java、QueenBeamEffects.java 的换行回归已按用户 A 授权恢复为原 CRLF，规范化代码 SHA256 相同；其他已有 LF 保留原样 | 如需统一历史 LF，另行确认具体范围；不扩大本次仅两文件的兜底授权 |
| P08 | 本轮修改代码保留 8 项既有 IDE 提示，扩查相关 WIP 后合计 12 项 | LcAnimationBoneAdapter 返回值未使用 1 项、LcClientAnimations.entityPose 未使用 1 项；PhotonChildSpawner 重写形参空值注解 5 项、emitNow 返回值未使用 1 项。扩查另有 PhotonModelOutput 布尔调用提示、PhotonNamedMixer 冗余转换、PhotonNamedRuntime.listener 未使用与 TimelinePlayerMixin 借用运行态资源提示各 1 项。本轮新增的冗余条件提示已修正；没有为清零提示扩展无关清理 | 继续保留，不作为已确认运行错误；若用户要维护这些 API／注解，再单独确定范围 |
| P09 | 历史专题、手写工程和截图证据在本工作区缺失 | 缺失资料清单见下节。先前文档审计将本地缺失文件链接从 205 处降为 30 处；剩余为 changes.md 与 player-animation.md 的共同基础历史截图。没有恢复任何资产或归档聊天 | 需要明确资料／资产来源；本轮仅登记、解除失效跳转并保留历史路径，不推断这些资料已经可用 |
| P10 | 本轮 4 个新增回归用例及代码没有执行验证 | PhotonModelMeshEditingTest 新增默认部件写回、过期环切扩展、骨骼／morph 重命名引用、复制部件轨道的用例；只有静态诊断，无测试结果 | 获明确授权后再运行；当前不得合并进历史通过数 |

## 缺失资料

| 缺失原文或资产 | 可读的现存入口与限制 |
|---|---|
| photon-performance.md、photon-world-performance-comparison.md、photon-detailed-performance-plan.md、laser-release-profile.md | [检测工具](systems/photon-performance-tool.md)、[静态候选](systems/photon-performance-audit.md)；这些不是缺失原报告，也没有原始实测数据 |
| shader-compatibility-exit.md | [粒子与渲染](systems/particle-rendering.md)；没有新增 EXIT 兼容结论 |
| queen-star-beam.md、queen-arcana-beam.md、light-ball.md、laser-spell.md | [女皇技能表现](systems/queen-beam-skill-effects.md)、[资源规范](systems/resources-datagen.md)；旧工程结构与对象索引未找回 |
| effect-authoring-lessons.md、photon-effect-authoring-history.md | [制作 Skill](../.codex/skills/lobecorp-effect-authoring/SKILL.md) 与 [修改记录](systems/changes.md) 只作现存规则／历史入口，不替代缺失案例 |
| src/main/resources/assets/lobecorp/fx/the_queen_of_hatred 下 laser_spell.fxproj、star_beam_spell.fxproj、star_beam_spell_ball.fxproj、star_beam_spell_beam.fxproj | 四份旧链接目标不存在；运行目录测试副本、历史 ID 与隔离输入不能当作这些手写资产已交付 |
| 共同基础的玩家／附着历史截图 | 保留现有历史条目和缺失说明，不将 horde 或其他工作区的验收写入本页 |

## 证据与恢复边界

当前代码与历次检查、Git 恢复引用见 [总览的检查与备份](systems/photon-workspace.md#检查与备份)。本轮备份根为 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-resume-backup-20261023。修复关闭对应问题时从本页移除当前待办，必要结论与实际证据写入总览／修改记录；不在多个历史页复制状态。
