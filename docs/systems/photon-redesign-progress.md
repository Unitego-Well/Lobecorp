# Photon 重设计实施进度

日期：2026-10-19。当前实施范围：[重设计方案](photon-model-redesign.md)的 R0 基础与 R1 模型原生输出适配。100处现有模型对象已通过客户端内存注册/准备/复制验证；尚未验收可见渲染、完整播放、往返及性能，R0/R1退出条件仍未全部满足。G1 **未通过**，尚未进入高级扩展。

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

## 已完成的 R1 纯CPU基础

- R0阶段提交为 f527ac395dd91c874775153a132ff49d3befed37，未推送。
- 新增 PhotonModelTopologyUtil：按部件/材质分组，保留面角UV/法线索引，准备不可变三角形拓扑；凹多边形使用保留绕序的耳切，自相交、重叠和退化面明确失败。三角化仅发生在准备阶段，不随播放时间重复。
- 新增 PhotonModelPoseSampler：共享只读准备数据，使用独立输入求值部件层级/枢轴、TRS曲线、Morph/实时权重、整体/局部伸长、起止锚点、宽度、透明度、法线与变形后边界。基础姿态与命名片段显式区分，不共享可变播放时钟。
- 每次输出独立不可变帧；内部JOML矩阵、向量与位置缓存只属于本次调用。并发测试验证同一准备数据的两次Morph采样互不串写，源定义不变；这不代表已经部署游戏工作线程或GUI独立呈现。
- 提供可选CPU采样计时，分别记录曲线、几何变形、法线/帧生成纳秒数；未采集时timings为空。此入口尚未接入逐对象诊断界面，不包含读取、拓扑准备、排队、上传、GPU与帧总成本，也不构成卡顿收益结论。
- 拓扑和姿态在工作边界检查线程中断，取消时不发布半帧；循环采样保留正向极小tick精度。尚未新增线程池、预览任务调度器或渲染上下文。
- 当前CPU路径对骨骼/权重明确拒绝运行并保留原定义；本批102处模型数据无骨骼，不能据此声称骨骼功能通过。原生输出、材质绑定与编辑事务仍未接入，因此不会解除旧资源保护。
- 真实源与包中102处模型出现位置已完成拓扑准备及0/5/100 tick基础姿态采样，原件字节不变；另以专项夹具覆盖真实资源尚未携带的曲线/层级边界。此为纯CPU测试，不是现有资源直接播放验收。

## 已完成的 R1 原生输出接线与客户端内存验证

- 新增 PhotonModelOutput、PhotonModelOutputConfig、PhotonModelMaterials 和原生注册 LcPhotonModelOutputTypes，沿用 lobecorp_model_emitter 标识及现有配置字段。通过 Photon Emitter.bakeGroup 提交按部件/材质分组的QUADS三角形，复用原生透明排序、深度/混合/剔除、覆盖层、草色、线框和渲染阶段；没有新建OpenGL出口。当前只确认接线及CPU顶点提交，未将可见渲染、Bloom/Iris等列为通过。
- 每实例 PhotonModelClock 管理延迟、暂停、速率、循环、重置和周期末柔和停止；运行属性使用原生RuntimeValue与RuntimeBinding。动画片段、动态部件/Morph属性、激活扩展和命名播放等完整接入仍待实施。
- PhotonModelAssetUtil将包内assetMaterialDependencies只应用到运行工作副本；配置保留assetJson原字节/字符串，类型Codec保留原对象NBT并合并当前已知字段。此为内存适配，不替代工程保存、导出和UUID全量往返验收。
- 客户端首次发现本轮assetDependencies初始化为不可变List，LDLib2反序列化需要clear导致UnsupportedOperationException；已改用实例独占ArrayList，并通过后续原生解码/复制验证。不要将可变配置容器和不可变运行定义混为一类。
- 客户端第二次原生验证99/100通过，唯一差异为现存probe_a.mfx缺modelRenderer但保存renderer；仅对当前version=0对象、有效version=1模型快照和已保存renderer推导内存modelRenderer，不接收其他版本或缺失资产，不修改原包。第三次客户端验证100/100通过，包含注册表指向、准备、快照值/表示、可变配置/运行槽与时钟隔离；它通过已登记类型Codec进行只读内存检查，文件加载/写入保护没有放开。
- 新增PhotonModelRuntimeCheckUtil作为客户端只读检查入口。IDEA JUnit材质/当前对象适配3项、时钟3项、顶点提交1项通过；原文件逐一比较未变。IDEA编译成功；最终Data日志ij_run__Data_404489747311025224.log为BUILD SUCCESSFUL，23输出written=0/removed=0；中英文生成文件各含258个既有模型翻译键，编辑器仍提示部分键未索引，不手改生成输出。
- 通过IDEA Debug启动了客户端，真实GLFW主窗maximized=1；没有进入世界、打开原工程或做游戏可见效果验收。只移除本轮临时断点，3个原有断点保留；阶段提交不推送。

## 当前未完成/正在推进

- 实际来源优先级、递归依赖闭合与源/包版本差异解释；原始来源/条目清单及哈希已经记录，不能以清单替代引用解析。
- 六种旧类型的版本/字段到新运行定义的适配、内嵌模型结构语义验证、命名/子播放和旧字段等价行为。
- 引用候选的类型分类、递归依赖闭合、独立包环境验收、保存/导出的无损往返。
- 原生模型输出的可见画面/材质与边界验收、原生/命名模型动画轨道及动态属性、游戏中的Morph/参数/锚点、真实游戏接入及可取消预览；已接线与内存准备不替代这些运行验收。
- 实体动画与实体模型LOD属于保护范围，本轮未修改这些文件；后续也不接管其实现。
- G1所有运行/往返/性能门槛保持未通过；高级扩展未开始。

## 检查过程

R1拓扑3项、姿态7项与取消边界单项复查均通过IDEA JUnit。最终代码经IDEA语义引用更新并编译，返回success=true、aborted=false，编译计数null不作零警告结论；相关新Java无错误/警告，测试println仅有调试建议。串行Data日志ij_run__Data_5683543206800967020.log为BUILD SUCCESSFUL，23输出written=0/removed=0；23包含共享工作区其他功能的生成项，不能算作模型功能新增输出。原始资产/实体动画/实体LOD对备份比较无差异，未运行游戏。

首次Data通过IDEA启动后失败，报既有 LcItemAnimationSession 与 LcSpawnEggItem 类加载缺失；这两个源码均存在且未在本轮修改。随后IDEA完整重建返回成功，并改为串行检查；Data重试日志 ij_run__Data_14417716740311847134.log 与最终代码检查日志 ij_run__Data_2666954846853660587.log 均为BUILD SUCCESSFUL，19输出、written=0/removed=0。首次失败保留记录，尚未确认当时缺类的根因。

没有启动游戏或进行视觉/性能验收；原始数据测试不证明效果可以直接使用。

## 备份

| 内容 | 本地引用 | 提交 |
|---|---|---|
| 原暂存区 | refs/backups/before-photon-r0-20261019-203307-index | 8a1b7e0c4f02ccda1ec7641ac0b03c1e34655b30 |
| 工作区及相关开发资源/配置 | refs/backups/before-photon-r0-20261019-203307-worktree | 62ec8708a25d34505a58cbc8e4b73e0071f48508 |

文本快照、backup.json 和 source-baseline.json 位于 backups/photon-r0-20261019-203307/，该目录为本地备份。备份不包含全部被忽略运行输出，不声明未落盘二进制资源也已保存。主暂存树在建立快照前后相同。

继续原生接入前另建refs/backups/before-photon-g1-runtime-20261019-index（06175a88370979214401ce94f9b371bef11f2149）及-worktree（2b905c2d1d21c9c07bffaff8ea858a7c91eff2ff），保存当时暂存与工作区；相关被忽略资源仍以R0备份为准，主暂存树在建立快照前后相同。对该工作区快照检查，原资源/实体动画/实体LOD目录无差异。

提交按阶段仅包含本轮授权源码/测试及同步文档，保留用户其他工作与暂存状态；不自动推送。阶段提交哈希以Git回执为准。
