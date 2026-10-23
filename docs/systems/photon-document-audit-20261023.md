# Photon 文档审计与缺失资料（2026-10-23）

## 范围与依据

本轮仅整理 photon 工作区的根 README、docs 与导航；不编辑业务代码、配置、资源或其他工作区。当前入口为 [Photon 工作区](photon-workspace.md)，共同基础仍见 [系统索引](README.md)。源码读取覆盖注册、工程／模型工作区、命名制作与运行、子特效、女皇快照消费、编写态隔离、文件和资源审查；符号存在仅说明当前源码，未通过运行重新验收。

审计前 HEAD 为 `ef201e857ed7b9567f922c3fbf565ebcaccbf181`。已有 41 个业务／配置变更文件，14 项暂存、39 项未暂存（交叠计数）、无冲突；文档与根 README 当时没有用户暂存或未暂存增量。本轮文档提交不得包含这些业务修改。

## 当前文档与历史记录

- 根 README 和 [当前能力表](photon-workspace.md#当前能力与源码入口) 作为本工作区入口；专题页补充当前实现和历史边界，不把旧方法名机械改成新类名来暗示 API 等价。
- [模型](photon-model-effects.md)、[命名动画](photon-named-animations.md)、[子特效](photon-emitter-spawner.md) 保留旧用法与检查，但明确旧测试／后端／路径不能直接调用。当前源码跳转集中到能力表。
- [API 索引](photon-effect-mcp-api.md) 根据当前源码新补；不是找回此前缺失的同名原文，也没有执行文中的游戏操作。
- 最新业务缺项与恢复步骤见 [网格、光束与 G1](photon-mesh-beam-g1-20261023.md)；历史设计、修复、移除、验收的日期和原始失败继续保留。
- 当前分支更新为 codex/photon，历史 codex/photon-performance、7903、旧 HEAD 与原备份身份保留为历史，不作为工具目标。
- Minecraft MCP 当前接入改为 lib 制品；旧源码子项目说明保留为历史，不能据此恢复 modules、安装 npm 或在此重建桥接。

## 缺失资料与历史引用

本工作区的既有链接指向下列不存在的专题文件。未从其他工作区复制、从归档聊天恢复或根据标题编造原报告；旧引用改为明确的缺失路径文本，索引只提供存在的当前入口。

| 缺失文件 | 当前可读入口与限制 |
|---|---|
| photon-performance.md | [性能检测工具](photon-performance-tool.md)、[最新 WIP](photon-mesh-beam-g1-20261023.md)；缺少原完整性能记录 |
| photon-world-performance-comparison.md | [静态候选](photon-performance-audit.md)；世界前后比较原报告缺失，不能恢复其原始样本或数字 |
| photon-detailed-performance-plan.md | [重设计方案](photon-model-redesign.md)、[静态候选](photon-performance-audit.md)；不视为原第 18／19 节原文 |
| shader-compatibility-exit.md | [粒子与渲染](particle-rendering.md)；EXIT 专题原文缺失，没有新增兼容结论 |
| queen-star-beam.md、queen-arcana-beam.md、light-ball.md、laser-spell.md | [女皇当前表现](queen-beam-skill-effects.md)、[资源规则](resources-datagen.md)；旧工程结构／对象索引原文缺失 |
| laser-release-profile.md | [最新 WIP](photon-mesh-beam-g1-20261023.md)；原实测报告缺失，当前首次卡顿、P99、冷热和视觉仍未验收 |
| effect-authoring-lessons.md、photon-effect-authoring-history.md | 现有 [制作 Skill](../../.codex/skills/lobecorp-effect-authoring/SKILL.md) 与 [修改记录](changes.md) 作为规则／历史入口；不替代缺失原案例 |

旧命名动画、发射器、Geo 桥接、模型 LOD 和专用回归类已移除的路径保留为历史文字；当前对应职责见能力表。女皇说明中四份 src/main/resources 下 .fxproj 目标在本工作区不存在，不能将运行目录测试副本或旧记录中的资源 ID 当作这四份手写资产已交付。资源原件与 fixture 缺失是独立状态，本轮未恢复资源。

审计开始时全 docs 的本地文件链接检查发现 205 处缺失目标，涉及 19 份文档。其范围包含玩家动画等共同基础的历史截图；本轮只修正 Photon／MCP 及相关共享页中的特效引用，不改写玩家动画验收或其截图证据。缺失目标去掉失效超链接后保留原路径和说明，不删除历史正文。缺失文件与缺失锚点分别检查，不能把文件存在当作锚点有效。

## 检查、备份与恢复

本轮执行文档目标存在性检查、IDEA 文件回读及文档错误／警告诊断。33 份修改文档均返回 analyzed、无截断，0 错误；changes.md 保留 18 项原有的玩家／附着历史截图缺失警告，其余本轮文档无诊断。全 docs 加根 README 的本地文件缺失链接由 205 处降为 30 处，剩余仅在 changes.md 与 player-animation.md 的共同基础截图历史中；Photon／MCP 目标与本轮修改文档的专题锚点已核对。没有检查远端网址是否可访问。

已有文档的换行类型保持不变，四份新增文档为 CRLF。原有 41 个业务／配置文件 SHA256 逐项相同；排除本轮文档后 Git 索引条目与备份一致。IDEA 新建文件时自动暂存的四份文档属于本次范围，提交时仍使用独立索引隔离业务暂存。

未运行编译、Data、Gradle 同步、业务测试、MCP 游戏连接或视觉／性能验收。已有检查的结果仅引用相应历史记录，不改写为本轮通过。

修改前文档原始字节、Git 索引、暂存条目、业务文件 SHA256、状态与 HEAD 备份在 `C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-docs-backup-20261023`。未修改或删除既有业务备份；恢复时先比较当前文档与备份，再经 IDEA 局部恢复，禁止覆盖之后的用户修改。提交只选择本轮文档并禁用共享同步钩子，不推送；保留用户原索引归属。

未解决业务项保持原样：G1 未通过、F11／F12 待复现、部分源码 CRLF 未恢复、5 条来源不可用、fixture 测试失败、编辑器完整对照与光束实机验收未完成。文档缺失仅登记和解除误导链接，不代表原历史证据已找回。
