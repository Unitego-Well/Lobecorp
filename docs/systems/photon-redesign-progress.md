# Photon 重设计实施进度

日期：2026-10-19。当前实施范围：[重设计方案](photon-model-redesign.md)的 R0 无损基础。G1 **未通过**，尚未进入高级扩展。

## 已完成的 R0 基础

- 修改前建立工作区和暂存区两个独立 Git 备份；工作区快照纳入 82 个被忽略的开发资源、编辑器状态、包缓存与配置文件。另保存 7 份打开源文档的 IDEA 文本快照。
- 新增 PhotonResourceReader、PhotonResourceDocument、PhotonResourceJsonUtil、PhotonResourceLimits：按原容器读取 .fxproj/.mfxproj、压缩 .fx/.mfx、.fxpack/.mfxpack、JSON/OBJ/NBT及未知二进制；原始字节与 SHA-256 保留，NBT/JSON 出口为副本。
- 设置原始/展开/累计NBT估算、复杂度与条目预算；拒绝重复JSON字段、无效UTF-8、NBT尾随内容、截断ZIP、路径越界、目录携带数据等。当前ZIP64/分卷包明确不接收；未知二进制条目仍按字节保留。
- 新增 PhotonResourceInspection：记录类型、旧字段路径、字符串UUID、引用候选、版本和内嵌模型摘要。它不实例化旧类，不宣告运行兼容；尚未实现的引用解析、非字符串UUID分类和字段语义验证不能计作通过。
- 新增不可变 PhotonModelDefinition 与 PhotonModelCodec，按当前模型版本适配部件、面角UV/法线、材质、骨骼/权重、Morph和片段数据，保留未知JSON字段；实际源文件/包中102个模型出现位置已通过结构适配测试，包含源/包重复，不代表102个独立模型或已通过渲染。
- 新增 PhotonResourceInventory：按调用方明确指定的来源扫描，保留同名不同来源与读取失败；顺序为调用方声明，不冒充实际游戏解析优先级。
- 文件级旧数据保护已接入有界读取，并复用旧类型/字段识别；没有解除 .mfx 系列及旧扩展保护。已打开ZIP文件系统的原生GC保护仍需继续统一总预算。
- 真实资源读取测试通过：43 个源文件，10 个包内共 166 个文件条目；验证读取前后原件字节及SHA-256。counterattack JSON与工程的不同结构、light_ball/queen_star_beam 的现有Morph已独立核对。此为原始数据读取，**不是编辑、导出、播放或G1验收**。
- 读取/保护专项13项、真实样本2项、6个分来源扫描、库存扫描2项及模型适配3项已通过IDEA JUnit。编译接口返回success=true、aborted=false，计数null不作零错误/警告结论。
- 完整基线在[资源基线JSON](photon-resource-baseline-20261019.json)，共501条来源/包内记录：source_assets 144、source_packs 176、development_assets 0、development_packs 0、pack_cache 176、editor_state 5。包来源中的176为10个包文件加166个文件条目；来源顺序/路径保留，不表示运行优先级或递归依赖已经验证。

## 当前未完成/正在推进

- 实际来源优先级、递归依赖闭合与源/包版本差异解释；原始来源/条目清单及哈希已经记录，不能以清单替代引用解析。
- 六种旧类型的版本/字段到新运行定义的适配、内嵌模型结构语义验证、命名/子播放和旧字段等价行为。
- 引用候选的类型分类、递归依赖闭合、独立包环境验收、保存/导出的无损往返。
- 模型/原生输出、实例时钟、Morph、参数/锚点、真实游戏接入及可取消预览。
- 实体动画与实体模型LOD属于保护范围，本轮未修改这些文件；后续也不接管其实现。
- G1所有运行/往返/性能门槛保持未通过；高级扩展未开始。

## 检查过程

首次Data通过IDEA启动后失败，报既有 LcItemAnimationSession 与 LcSpawnEggItem 类加载缺失；这两个源码均存在且未在本轮修改。随后IDEA完整重建返回成功，并改为串行检查；Data重试日志 ij_run__Data_14417716740311847134.log 与最终代码检查日志 ij_run__Data_2666954846853660587.log 均为BUILD SUCCESSFUL，19输出、written=0/removed=0。首次失败保留记录，尚未确认当时缺类的根因。

没有启动游戏或进行视觉/性能验收；原始数据测试不证明效果可以直接使用。

## 备份

| 内容 | 本地引用 | 提交 |
|---|---|---|
| 原暂存区 | refs/backups/before-photon-r0-20261019-203307-index | 8a1b7e0c4f02ccda1ec7641ac0b03c1e34655b30 |
| 工作区及相关开发资源/配置 | refs/backups/before-photon-r0-20261019-203307-worktree | 62ec8708a25d34505a58cbc8e4b73e0071f48508 |

文本快照、backup.json 和 source-baseline.json 位于 backups/photon-r0-20261019-203307/，该目录为本地备份。备份不包含全部被忽略运行输出，不声明未落盘二进制资源也已保存。主暂存树在建立快照前后相同。

提交按阶段仅包含本轮授权源码/测试及同步文档，保留用户其他工作与暂存状态；不自动推送。阶段提交哈希以Git回执为准。
