# Photon 重设计实施进度

日期：2026-10-20。当前实施范围：[重设计方案](photon-model-redesign.md)的R0/R1基础、R2模型轨道和命名/子播放接线。模型、命名控制器和生成器已有分项内存检查，counterattack两类资产副本已在原生视窗绘制；原文件直接打开/编辑/发布、完整旧播放、游戏链及性能仍未完整验收。G1 **未通过**，尚未进入高级扩展。

本轮已按用户“保存文档然后停止”的要求停止实施，保存当前代码与文档现场。没有继续启动最终Data、全量资源播放或性能验收；下列未完成项保留为后续恢复入口，不表示仍在后台执行。

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

## 模型可见预览与R2轨道基础（2026-10-20）

- 使用本轮创建的隔离世界“新的世界 (8)”，在游戏内嵌入式透视视窗预览原始counterattack JSON与mfxproj的内存副本；没有通过原文件菜单打开/保存。JSON为242顶点/128面/2部件/3材质槽，实际128面只使用spherical_patch_1及白贴图槽，故一组输出；工程为121顶点/224面/1部件/0材质槽，沿用默认材质。OBJ另行冻结为121位置/672面角UV/672面角法线/224面/1对象/1材质标签；不能与JSON自动合并。
- 本地截图：backups/photon-g1-native-20261019/screenshots/g1-native-counterattack-json-ready.png、g1-native-counterattack-project-ready.png。首张截图因父目录不存在未保存，补建本轮证据目录后重新取图。首次立即暂停时引擎仍有待加入对象；场景就绪后重新reset/play/pause/tickInternal，确认root+模型共2对象并产生几何。后续seek完成状态必须包含场景就绪/队列，不仅判断时间指针。
- JSON一次CPU诊断为曲线42400ns、变形253000ns、法线/帧525400ns，非统计基准。后台截图为10fps，不作正式帧率或收益结论；GPU、全量材质/Bloom/Iris对照未通过。
- 新增PhotonModelBindings及ModelClip/Track/TrackEditor/ClipUtil，保留lobecorp_model_animation标识、原生窗口/UUID、未知字段、最近起点/同起点保存顺序和HOLD/RESTORE。解绑target/clipTarget时清除旧已知字段，防止原始NBT合并复活引用；resource验证识别LDLib2的_NULL_标记。
- 部件TRS/Morph走原生RuntimeBinding/RuntimeValue；只覆盖的TRS分量改变。模型按owner持有动画选择贡献，优先级/启动顺序决定结果，移除一项恢复剩余贡献；仅此选择机制通过，不代表完整命名库已实现。现有TimelinePlayerMixin接入tick/frame/stop，保留预览及Trace；无片段静态几何忽略无关时间，覆盖值仍参与缓存键，奇异缩放法线及配置剔除分别处理。
- 原生Trace已接入MODEL_SAMPLE、缓存命中/缺失、三个CPU阶段纳秒mark、部件/材质组提交及几何/批次数；GPU继续采用原有对象/共享归属，不伪分摊动画GPU成本。
- IDEA JUnit姿态8项、模型轨道5项（84处真实轨道逐字段内存往返）、几何2项、对象/材质3项（100处真实对象）通过。严格resource校验曾误拦截合法空值，使用LDLibExtraCodecs.isEmptyOrStringNull修正后复测通过；UUID负例的错误日志为预期诊断。
- 客户端再次100/100对象通过；独立夹具验证原生轨道回调、TRS/Morph槽、循环HOLD末姿态、贡献释放和静态缓存。DETAIL夹具得到9次MODEL_SAMPLE、24条CPU阶段mark及1次缓存命中，不作帧率/GPU基准。临时断点按ID移除，3个原有断点保留；临时工程不保存，世界正常保存退出后关闭本轮客户端。
- IDEA编译success=true、aborted=false；Data日志ij_run__Data_14371939407349434873.log为BUILD SUCCESSFUL，23输出written=0/removed=0。之后诊断局部变量/读帧校验调整由客户端启动前编译并完成运行检查；可选final、预留API等提示不当作零警告结论。原资源、实体动画/LOD对实施前备份无差异。G1仍未通过，高级扩展未开始。

## 命名播放与生成器接线（2026-10-20，已停止）

- 新增PhotonNamedLibrary/CodecUtil、PlaybackClock/Handle、PropertyTarget/MixedFunction/Mixer、NamedPlan/Runtime及原生控制器注册；保留第六版库、原树/未知字段、UUID、形参类型/范围、时长形参、归一化改时长、受控/独立时钟、跟随结束冻结、优先级/启动顺序、HOLD/RELEASE和有预算的触发/事件队列。运行写入独占槽，上一轮贡献先释放；粒子生命周期函数不提前压成常量，旋转跨循环偏移仍保留。命名层按原Trace的ANIMATION/ANIMATION_FRAME记录CPU，不伪造动画GPU时间。
- 原生FXRuntimeMixin在emit前准备/emit后启动，重放/销毁时关闭；根回调和保留条件按实例持有与恢复。Model准备先于动态属性解析；录制帧跳过命名/模型覆盖，编辑器通用预览在混合后应用。声明这些接线不代表已通过全量录制、编辑或发布验收。
- ChildClip/Track保留现有子动画/工程调用类型、片段身份、窗口、独立时长、形参和未知字段；126处真实轨道完成逐字段内存往返，原文件字节未变。ChildWindows区分受控/独立、迟生成实例、窗口完成与停止；生成器沿用原生粒子宿主而非二次手动tick。工程动画调用轨道的测试入口仍明确拒绝执行，制作界面/发布转换未完成。
- 69处真实生成器记录中，42处PROJECT/INTERVAL、27处FX/ONE_SHOT，均为POINT，31处follow；沿用原文件photon.lobecorp.emitter_spawner.enum.*编码。新增SpawnConfig/Shape/Parameters、Schedule、ChildSpawner、SpawnTemplate/Instance/Overrides及类型注册，具备独占槽、随机数、容量/递归预算、模板过滤、跟随变换、寿命和结束接线。尚未将其当作完整资源生成或可见播放通过；旧光束长度覆盖遇到真正Beam目标时仍明确拒绝，原资源加载门禁保持。
- IDEA JUnit库4项、时钟4项、句柄5项、子轨道3项（126处真实轨道）、调度3项已通过；库扫描为44库/150动画/70规则/101形参/286绑定。客户端原生夹具通过控制器编码、基础值恢复、优先级混合、重复帧不累加、形参、触发规则、HOLD和关闭恢复；当前未替代整套效果/性能检查。
- PhotonPlaybackResourceCheckUtil在客户端直接通过已登记类型做只读内存检查：44控制器和69生成器均通过字段往返、深复制身份；初次计划19/44通过，25处被原Timeline文件门禁拦截，内存计划改为已登记类型的严格读取后40/44。余4处为magic_circle源/工程及包中保留而无对应启用轨道的有效绑定；旧实现不单独驱动，现按此语义保留，最终44/44计划准备通过。同属性多条绑定均按保存顺序收集，布尔等离散属性仅采用OVERRIDE；未使用绑定不自动生成新轨道或行为。
- 最后客户端日志为ij_run__Client_13810916951836818788.log，G1_PLAYBACK_MEMORY_CHECK记录44控制器/69生成器/44命名计划均通过，原生混合夹具再次passed=true。检查仅为注册、内存往返和计划准备，未发射真实组合效果，不能计为G1通过；原文件和保存门禁未放开。
- IDEA编译返回success=true、aborted=false、消息计数null；后续修改经客户端启动前编译，最后客户端检查使用当前代码。源文件诊断无错误，仍有预留API/可选写法/注解注册未使用等提示；本批新增生成器文件的最终格式化/完整诊断、Data及生成输出检查未完成。客户端仅标题页内存检查，未进世界/写原件；临时断点按ID删除，3个原断点保留，所有本轮客户端已关闭。沿用R0和原生接入前备份，最近原资产/实体动画/实体LOD对备份检查无差异；收到停止要求后未再启动实现或验证。

## 当前未完成（按用户要求停止）

- 实际来源优先级、递归依赖闭合与源/包版本差异解释；原始来源/条目清单及哈希已经记录，不能以清单替代引用解析。
- 六种旧类型的版本/字段到新运行定义的适配、内嵌模型结构语义验证、命名/子播放和旧字段等价行为。
- 引用候选的类型分类、递归依赖闭合、独立包环境验收、保存/导出的无损往返。
- 全量模型画面/材质/边界、完整命名库与子播放/发射器、游戏中的Morph/参数/锚点、真实游戏接入及可取消预览；两份内存预览和模型轨道夹具不替代全量文件/游戏链验收。
- 实体动画与实体模型LOD属于保护范围，本轮未修改这些文件；后续也不接管其实现。
- G1所有运行/往返/性能门槛保持未通过；高级扩展未开始。

恢复时优先核对当前未完成接线：工程动画调用轨道测试入口、原生激活的开始/结束/停止发射/重启保留粒子及子级覆盖、旧循环/渲染字段；实际发射生成器和受控/独立子动画的可见/生命周期检查；递归来源解析与独立包；模型工程/OBJ/UV及编辑事务；原文件另存/重开/发布；既有快照驱动的真实指挥家激光/星束及可取消拖动性能。当前生成器的FX来源仍经过原FXHelper文件保护，完整文件播放不能直接放行；真正Beam目标的旧长度覆盖与工程动画调用仍明确拒绝。

## 检查过程

R1拓扑3项、姿态7项与取消边界单项复查均通过IDEA JUnit。最终代码经IDEA语义引用更新并编译，返回success=true、aborted=false，编译计数null不作零警告结论；相关新Java无错误/警告，测试println仅有调试建议。串行Data日志ij_run__Data_5683543206800967020.log为BUILD SUCCESSFUL，23输出written=0/removed=0；23包含共享工作区其他功能的生成项，不能算作模型功能新增输出。原始资产/实体动画/实体LOD对备份比较无差异，未运行游戏。

首次Data通过IDEA启动后失败，报既有 LcItemAnimationSession 与 LcSpawnEggItem 类加载缺失；这两个源码均存在且未在本轮修改。随后IDEA完整重建返回成功，并改为串行检查；Data重试日志 ij_run__Data_14417716740311847134.log 与最终代码检查日志 ij_run__Data_2666954846853660587.log 均为BUILD SUCCESSFUL，19输出、written=0/removed=0。首次失败保留记录，尚未确认当时缺类的根因。

上述R0/纯CPU阶段当时没有启动游戏；最新客户端/预览证据见R2记录。尚未进行完整旧文件、游戏链和正式性能验收，原始数据或夹具通过不证明G1已可直接使用。

## 备份

| 内容 | 本地引用 | 提交 |
|---|---|---|
| 原暂存区 | refs/backups/before-photon-r0-20261019-203307-index | 8a1b7e0c4f02ccda1ec7641ac0b03c1e34655b30 |
| 工作区及相关开发资源/配置 | refs/backups/before-photon-r0-20261019-203307-worktree | 62ec8708a25d34505a58cbc8e4b73e0071f48508 |

文本快照、backup.json 和 source-baseline.json 位于 backups/photon-r0-20261019-203307/，该目录为本地备份。备份不包含全部被忽略运行输出，不声明未落盘二进制资源也已保存。主暂存树在建立快照前后相同。

继续原生接入前另建refs/backups/before-photon-g1-runtime-20261019-index（06175a88370979214401ce94f9b371bef11f2149）及-worktree（2b905c2d1d21c9c07bffaff8ea858a7c91eff2ff），保存当时暂存与工作区；相关被忽略资源仍以R0备份为准，主暂存树在建立快照前后相同。对该工作区快照检查，原资源/实体动画/实体LOD目录无差异。

提交按阶段仅包含本轮授权源码/测试及同步文档，保留用户其他工作与暂存状态；不自动推送。阶段提交哈希以Git回执为准。
