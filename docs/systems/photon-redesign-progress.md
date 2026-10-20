# Photon 重设计实施进度

日期：2026-10-20。当前实施范围：[重设计方案](photon-model-redesign.md)的R0/R1基础、R2模型轨道和命名/子播放接线。模型、命名控制器和生成器已有分项内存检查，counterattack两类资产副本已在原生视窗绘制；原文件直接打开/编辑/发布、完整旧播放、游戏链及性能仍未完整验收。G1 **未通过**。最新授权允许开始高级扩展，但当前新增实现仍为G1测试制作基础，尚无高级能力实现或收益证据。

上轮按用户“保存文档然后停止”保存为46f855c；2026-10-20收到“继续按照文档”后恢复实施。最新优先级为完整命名／测试轨道制作工作区、旧循环和渲染字段及现有资源兼容；高级扩展已获授权，单独记录，不抵扣G1。原文件加载/写入保护保持。历史未执行的检查不能算作本轮已完成。

## 工程测试制作基础（2026-10-20）

- 修改前建立 refs/backups/before-photon-g1-workspace-20261020，指向 1c4cba969d57ae54965e0e56bf0e60a24c5d39b5；沿用原始工作区/资源备份，不覆盖原文件。
- PhotonProjectPreviewPanel新增制作属性面板：无测试定义时创建新UUID的project_test草稿；可编辑名称、时长、自动时长、循环、优先级和HOLD/RELEASE。复制选中轨道只追加最外层选择的独立NBT副本到草稿，保留目标UUID和组内结构，不改主场景。取消丢弃草稿，应用进入原生工程历史。
- 应用前比较完整旧测试字段，拒绝过期面板覆盖；执行文件能力保护和当前运行实例的PhotonNamedPlan准备，未支持类型或失效绑定不能直接写入测试字段。撤销/重做按当前工程解析测试字段，不捕获旧轨道引用。项目测试字段仍不进入FX发布数据。
- PhotonProjectPreviewUtil.edit只合并明确编辑的属性，保留已有UUID、轨道和未知字段。新建或改名仅允许小写字母和下划线；已有其他名称不因只改属性被强制改名。
- IDEA编译已成功；JUnit5项通过，其中8份现有测试定义读取前后文件字节一致，新增专项覆盖属性合并、稳定身份、轨道/未知字段保留、旧名称不变及非法名称/时长拒绝。Data日志ij_run__Data_1829558265010227452.log为BUILD SUCCESSFUL，23输出written=0/removed=0。本轮尚未运行客户端GUI、撤销/重做或磁盘往返验收，不以静态检查替代这些结果。
- 该面板仍不是完整命名／测试轨道制作工作区：缺命名列表/定义事务、参数/绑定/规则编辑、独立轨道视图和完整制作预览。旧循环/渲染字段、全量资源兼容、游戏链及性能仍未补齐，G1未通过。高级扩展已获授权但本轮尚无实现，不宣称GUI独立线程或性能问题已解决。

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

## 工程测试调用适配（2026-10-20恢复实施）

- 从46f855c继续，先建立refs/backups/before-photon-g1-preview-20261020，指向该已保存阶段；未改变原暂存和工作文件。仅修改playback、工程字段接线、专项检查及本文档，不改原资产、实体动画或实体模型LOD。
- 新增PhotonProjectPreviewUtil和PhotonProjectPreviewAccess；FXProject既有Mixin保留data/lobecorp_animation_preview原树、UUID、未知字段及当前版本校验。此字段仍与FXData分离，不进入FX/包导出；原文件加载/写入门禁未解除，完整工程保存/另存/重开和GUI事务尚未验证。
- PhotonNamedRuntime增加独立preview/seekPreview/stopPreview入口；来源须为FXProjectEffectExecutor，测试UUID不在发布库索引内。CallTrack只在该独立测试准备上下文允许，由ChildWindows检查具体宿主/句柄归属；普通场景和发布动画仍拒绝自调用。受控时间变更递增采样版本，帧更新不释放模拟激活状态；关闭时用句柄快照遍历，防止同实例子调用释放期间修改正在遍历的集合。上述为实现接线，未将受控/独立/RELEASE运行结果写成通过。
- IDEA JUnit工程测试定义3项通过，其中8份真实工程测试字段按原树读取，文件字节未变；未知版本/坏UUID明确失败，复制出口不污染来源。基础原生混合夹具在客户端再次passed=true。
- 新工程调用夹具在标题页创建TrackedDummyWorld时因缺少minecraft:dimension_type注册中止。随后复用既有“新的世界 (8)”验证，IDEA Debug恢复进入世界和临时Quick Play重试均发生JVM原生EXCEPTION_ACCESS_VIOLATION；栈为java.lang.invoke.DirectMethodHandle$Holder.invokeStatic，根因未确认，不归因为本次Java逻辑已出错或已正常。日志ij_run__Client_8436095788376394266.log、ij_run__Client_17991510831972487223.log及run/client/hs_err_pid51856.log、hs_err_pid3016.log保留。未完成世界/GUI/画面/生命周期与性能验收，未继续重复启动或改JDK/存档配置；临时断点按ID移除，3个原断点保留，客户端已退出。
- IDEA编译success=true、aborted=false，计数null不作零警告结论；相关代码诊断无错误，保留预留API/既有检查工具空值提示。Data日志ij_run__Data_544338832770161951.log为BUILD SUCCESSFUL，23个输出written=0/removed=0，生成目录无新增工作区差异。原资源/实体动画/实体LOD对原生接入前备份检查无差异。G1未通过，高级扩展未开始。

## 条件断点对照与后续调试约束（2026-10-20）

- 在同一JBR 25.0.4.1+1-b610.67及同一存档“新的世界 (8)”进行普通Run和仅普通无条件行断点的Debug对照。Run于18:30:35进入、18:32:52正常退出，BUILD SUCCESSFUL；Debug于18:34:49进入、18:38:56正常退出，BUILD SUCCESSFUL，两组均无新增hs_err。
- Debug两次读取ClientRuntimeEvents.class.classRedefinedCount均为0，世界已加载；AllowEnhancedClassRedefinition和debugger-agent.jar仍启用，未更换JDK或修改持久启动配置。对照前后Java/构建文件现有差异的SHA-256一致：691AC51ED2AFC0ACBFA0D657152FEB88B339364F847F312F294D59DA94F5CEEE。
- 历史两次条件断点崩溃分别在目标类重定义后约306/329ms发生，Render thread栈均为DirectMethodHandle$Holder.invokeStatic原生访问异常。结合JetBrains关于条件断点/日志点自动插桩的说明，结果强烈支持插桩/类重定义路径为触发因素；尚未定位JBR内部确切缺陷，不宣称全部Debug负载已安全或G1已通过。参考：https://blog.jetbrains.com/idea/2026/08/println-debugging-done-right/ 。
- 对照并非逐字节冻结世界：Run首次最大化尝试在鼠标捕获中误破坏砂岩，玩家下落一格；已告知用户，未猜测恢复。Debug复用该次保存后的同一存档。以后先暂停释放鼠标，再最大化。
- 原始证据保存在backups/jbr-conditional-compare-20261020/：comparison-observations.json、run-no-debug.log、debug-unconditional.log、两份baseline-conditional客户端日志及两份hs_err。临时断点均移除，3个用户原断点保留，客户端正常关闭。
- 后续仅使用普通无条件行断点；不设置条件、日志表达式、日志点或自动插桩探针。暂停后读取当前栈再手动求值；不因本次规避而更改JDK、捕获代理或增强重定义设置。继续先在原隔离世界验证工程测试调用，再补GUI/原生ValueIO往返；原文件保护、实体动画及实体模型LOD保持，高级扩展不做。

## G1工程调用与原生ValueIO运行复核（2026-10-20）

- 在既有“新的世界 (8)”通过普通无条件断点进入当前项目栈，确认world=true、GLFW_MAXIMIZED=1、ClientRuntimeEvents.class.classRedefinedCount=0。inspectProjectPreviewFixture返回passed=true：受控映射、正向定位/回拖、停止释放、独立句柄和时长、RELEASE及非工程来源拒绝通过。此为内存夹具，不替代可见组合效果或GUI验收。
- 通过只读有界资源读取，取出源资源及包中8份真实工程测试字段，分别注入临时空FXProject，再调用原生serializeProject/deserializeProject与TagValueOutput/Input。8份原始测试定义逐树相等，日志记录G1_PROJECT_PREVIEW_NATIVE_VALUEIO=8；没有加载完整旧工程、写回源文件或绕过文件保护。
- 额外临时夹具确认传入/读出副本隔离、无字段反序列化清除旧测试、错误字段类型拒绝，以及FX.serializeNBT不携带工程测试定义，日志G1_PROJECT_PREVIEW_BOUNDARIES=passed。未知字段/UUID的保留包含在树相等检查；不等于完整工程其他未知字段、磁盘文件往返或包发布全部通过。
- 客户端日志ij_run__Client_11263211762789435760.log已复制到backups/jbr-conditional-compare-20261020/g1-preview-unconditional.log，正常退出且BUILD SUCCESSFUL in 3m 10s，结束目标类重定义计数仍0；本轮临时断点清除，3个原用户断点保留。退出阶段日志还记录conductor_snapshot/text_particle_state配置阶段取玩家失败及Spark CancellationException，未定位根因、未顺带修改，不称为无错误运行。
- 本轮仅修改实施进度和修改记录；没有新增Java、构建配置或资源修改，未额外执行编译/Data/JUnit。源资源、实体动画/LOD对原生接入前备份比较无差异，沿用R0/原生接入/工程预览阶段备份。G1仍未通过，高级扩展未开始。

## 工程测试GUI与文件保护收口（2026-10-20）

- 新增PhotonProjectPreviewPanel，从扩展编辑器“工具”菜单读取当前工程保存的测试定义，提供重启、指定tick应用定位、停止和关闭释放。面板校验工程/实例/句柄归属，旧面板不能操作重建后的实例；不新增控制器、修改测试定义或恢复整套历史命名动画制作工作区。
- 在原隔离世界的临时原生工程中，经真实按钮回调验证8tick重启、回拖3tick、停止和关闭释放。首轮检查误以发布库playback查询测试UUID，因测试定义不在发布索引而失败；改查实际测试句柄后通过，不修改发布索引来迎合检查。首张截图发现文字/输入横向重叠，调整纵向布局和换行；修正截图为backups/photon-g1-gui-20261020/screenshots/project-preview-panel-fixed.png，确认游戏内嵌透视视窗。
- 完整磁盘链首试在保存临时工程后被读取门禁拒绝，未算通过。现在仅工程data/lobecorp_animation_preview的有效容器可进入已支持路径，内部旧类型/旧字段仍递归拒绝，其他位置及FX发布数据中的同名字段继续阻断；保存前也检查工程测试内容。没有放开模型后缀、调用轨道、旧激活或全部注册类型。
- 隔离原生工程使用ProjectType.saveProjectToFile/loadProjectFromFile完成保存、重开、再保存，测试定义含UUID和future未知字段保持，两份完整临时工程NBT树相等并实际载入嵌入编辑器。文件为roundtrip/native_supported.fxproj及native_supported_reopened.fxproj；这是支持子集的完整文件链，不是全部真实旧工程的往返。此前8份真实测试字段仍仅通过内存往返，不能将它们写作完整旧工程通过。
- 旧播放语义只读核对：历史PhotonActivatorTimelineUtil的STOP_EMISSION保留active=true并设置stopped=true，使残留继续模拟；CLEAR还记录清理边界，RESTART记录进入边界，另有点控制/首尾开关和子级独立激活。当前NamedGates仅合成原生激活/可见及控制重置，尚不等价；后续须分别实现和验收，不能把停发映射成停用或冻结。原始旧字段继续保留并阻断文件放行。
- 已建refs/backups/before-photon-g1-gui-20261020指向0efd5f3；原资源、实体动画/LOD对原生接入前备份比较无差异。仅普通无条件断点，最后一次独立JDI读取classRedefinedCount=0；多语句片段读取该JDK私有字段曾因模块访问拒绝，未改模块/JDK设置，随后单表达式读取成功。
- 最终限定路径后IDEA编译success=true、aborted=false，消息计数null；PhotonProjectPreviewTest共4项通过（8份真实定义仍按原字节保护），含有效工程字段、错误类型/版本、内部旧类型/字段及普通FX扫描继续阻断。最终Data日志ij_run__Data_5895912693308660303.log为BUILD SUCCESSFUL，23输出written=0/removed=0，生成目录无差异。相关Java无错误；保留NamedRuntime借用实例的资源检查提示、原guard未使用API及测试println建议，不改用日志点。
- 两轮客户端正常退出且BUILD SUCCESSFUL，日志已保留为client-gui-initial.log和client-gui-layout-disk.log；原始临时日志分别为ij_run__Client_10249724691152889811.log和ij_run__Client_5256740108241147824.log。最终工程路径限定修改通过静态诊断/编译/JUnit/Data，未再启动第三轮客户端；上述文件链与布局证据采集于该限定前，最终版本完整GUI/文件链仍需复核。临时工程已关闭，不保存原资产；本轮临时断点清除，3个原用户断点保留。
- 退出阶段仍出现conductor_snapshot/text_particle_state同步任务报错，与前轮相同类别，尚未定位，未顺带修改；不称为无错误客户端运行。

## 最终限制复核与旧激活恢复（2026-10-20）

- 修改前建立refs/backups/before-photon-g1-activation-20261020，指向75c2140；沿用R0原资产/工作区备份，保留其他暂存和未暂存工作。先在该提交的最终路径限制下，完成原生工程重开/再保存的完整NBT相等及测试面板8tick重启/回拖3tick/关闭释放，日志为client-final-path-verification.log，标记G1_FINAL_GUARD_NATIVE_DISK/G1_FINAL_GUARD_GUI_CALLBACKS均passed。
- 新增PhotonActivationOptions/StateUtil、轨道/发射/归属桥接、激活运行Mixin及扩展编辑器历史适配；原生无扩展字段保持CONTINUE/ORIGINAL，v6命名动画缺省激活在运行准备阶段采用历史RESTART语义，不改写原库。支持两端开关、旧singleFrame读取、RESTART/CONTINUE、ORIGINAL/STOP_EMISSION/CLEAR、保留粒子重启和单边点；同轨道区间并集只进入一次，最新单边命令覆盖持续区间，后续新入口才能恢复，同时间按保存顺序处理。
- 原生TimelinePlayer过滤非区间命令后再处理扩展；NamedGates用归属令牌协调原生/命名控制，父级重启/清理跳过独立子级，并记录当帧原生基底用于命名贡献释放。ParticleEmitter只拦截新生成，保留残留模拟；空停发实例不以发射能力保活。模型停发期限与执行器停止各自独立，支持保留重启/清理；子生成器接入相同桥接，但真实子实例/依赖资源的完整生命周期验收仍未完成。
- 激活区间边界仅在准备/编辑变化时重建，运行缓存不持有可变原树；没有增加线程池或GUI独立渲染，没有宣称帧率收益。当前Trace仍沿既有运行/时间轴范围计量，完整冷/热/多实例/拖动基准未通过。
- 新增PhotonActivationPanel：“工具→激活行为”配置单个已选原生激活轨道，应用进入原生历史；撤销/重做按当前工程轨道路径定位，保持字段/绑定，不依赖历史恢复前的Java对象引用，不自动保存。此为原生激活制作界面，不代表历史命名/工程测试轨道制作工作区已整体恢复。
- 激活轨道保存保留原始轨道、片段及激活容器未知字段，合并已知字段时删除旧绑定，避免解绑后复活。文件门禁仅允许经协议校验的原生activator包装；未知包装字段、错误类型/UUID/范围及其他旧扩展仍阻断，模型后缀和未恢复类型没有整体放开。
- 客户端实际粒子检查通过停发后残留更新、直接生成拦截、残留保活/空实例结束、保留/清空重启和入口只重置一次；单边点、区间恢复、外部停用状态回拖及关闭两端也通过。完整activation_supported.fxproj→重开→另存NBT相等。G1_NATIVE_ACTIVATION_PARTICLES与G1_NATIVE_ACTIVATION_POINTS通过；首轮G1_ACTIVATION_GUI_HISTORY_DISK检查持有旧轨道引用，未复核当前工程，不能据此确认撤销/重做和磁盘未知字段通过，结论已撤回。
- 父激活/父控制两种夹具均保留独立命名子级粒子，手动隐藏祖先仍隐藏子级，释放后恢复继承；另以真实命名运行时覆盖原生激活/控制、原生进入/退出两种边界，命名释放同tick恢复当帧原生基底。标记G1_ACTIVATION_PARENT_CHILD_OWNERSHIP及G1_NATIVE_NAMED_BASE_RELEASE均passed。模型内存夹具通过保留重启、周期末停发和立即清理；已有模型轨道/TRS/Morph/HOLD、命名混合和工程调用夹具也通过，不替代全量可见资源验收。
- 压缩FX首轮文件NBT往返相等，但实际FX.createRuntime播放失败：Track.copy走独立copyExtra路径，没有传递激活设置，实例退回NATIVE。已新增无Provider依赖的复制桥接，连同原树/片段未知字段独立复制；后续带未知字段的浅/深复制及原生播放已通过，首轮失败证据保留，未覆盖原资源。
- 复制修复后，从带三层未知字段的activation_unknown_seeded.fx实际重读，浅/深两种createRuntime均保持完整轨道数据，修改副本不改变模板，原生emit与停发行为通过；标记G1_ACTIVATION_COPY_NATIVE_FX_PLAYBACK=passed。一次“copy aliases template”检查错误地假定旧夹具含future字段；实测旧文件为ABSENT、复制前后模板树相同，该次失败属于夹具前提错误，不是别名根因。
- GUI复核进一步发现：原生HistoryView.undo回到初始SerializableRecordAction时反序列化整个工程，旧轨道引用脱离当前数据，旧重做闭包写不到当前工程，运行时对象映射也会陈旧。已改为按当前工程轨道路径恢复激活字段，并仅在扩展编辑器历史恢复后、数据与运行时脱离时重建预览和层级，保留播放/暂停及时间目标；最终按当前工程重新取轨道，验证原始未知字段、撤销/重做、运行时对象映射与暂停、保存后修改读回及完整工程NBT一致，G1_ACTIVATION_CURRENT_HISTORY_DISK=passed。此处理不修改普通原生Photon编辑器或其他项目的历史行为。
- 纯语义/保护/未知字段JUnit9项、模型时钟4项、工程测试字段4项通过；IDEA编译返回success=true、aborted=false，计数null不作零警告结论。Data日志ij_run__Data_9134491337992206456.log为BUILD SUCCESSFUL；真实资源原字节保护与最终生成目录检查分别记录，不由夹具推导。源资源、实体动画/LOD对原生接入前备份检查无差异。
- 本轮仅普通无条件行断点；一次调用栈只读请求长时间未回执，取消请求后重新读取会话/栈恢复，未更换JDK/修改模块参数或清空断点。激活首轮末尾独立读取classRedefinedCount=0；窗口末尾max=0，故首张激活面板图只作布局证据，最终窗口状态与复制修复证据见下项。
- 最终当前工程/历史修复验证后，再次确认主窗GLFW_MAXIMIZED=1、真实帧缓冲2560×1494，截图screenshots/activation-panel-final-maximized.png已读回检查，为游戏内嵌透视界面；独立读取classRedefinedCount仍0。最终Client日志ij_run__Client_17843290617460757301.log正常退出且BUILD SUCCESSFUL；全部本轮客户端关闭，临时断点清除，3个用户原断点保留。
- 最终编译success=true、aborted=false，消息计数null；最后一轮JUnit9+4+4全部通过。最终Data日志ij_run__Data_3217596625298289299.log为BUILD SUCCESSFUL，23输出written=0/removed=0，生成目录无差异。主要日志已复制到本轮backups目录，含client-final-path-verification.log、client-activation-before-copy-fix.log、client-copy-fix-before-history-fix.log、client-final-history-fix.log及data-final-history-fix.log；退出阶段仍有既有配置阶段网络同步取玩家报错，未修复，不称为无错误客户端运行。

## 当前未完成

- 实际来源优先级、递归依赖闭合与源/包版本差异解释；原始来源/条目清单及哈希已经记录，不能以清单替代引用解析。
- 六种旧类型的版本/字段到新运行定义的适配、内嵌模型结构语义验证、命名/子播放和旧字段等价行为。
- 引用候选的类型分类、递归依赖闭合、独立包环境验收、保存/导出的无损往返。
- 全量模型画面/材质/边界、完整命名库与子播放/发射器、游戏中的Morph/参数/锚点、真实游戏接入及可取消预览；两份内存预览和模型轨道夹具不替代全量文件/游戏链验收。
- 实体动画与实体模型LOD属于保护范围，本轮未修改这些文件；后续也不接管其实现。
- G1完整运行/往返/性能门槛仍未通过；已通过的分项不代替完整门槛，高级扩展未开始。

后续优先核对：命名/工程测试轨道完整制作工作区及真实旧工程完整磁盘往返；激活在全部真实组合资源中的可见等价与旧循环/渲染字段；实际发射生成器和受控/独立子动画的可见/生命周期检查；递归来源解析与独立包；模型工程/OBJ/UV及编辑事务；原文件另存/重开/发布；既有快照驱动的真实指挥家激光/星束及可取消拖动性能。当前生成器FX来源仍经过原FXHelper文件保护，完整旧文件播放不能直接放行；真正Beam目标的旧长度覆盖仍明确拒绝。工程调用、原生激活制作/历史、带未知字段的隔离原生工程及压缩FX复制播放已有分项证据，完整真实资源链和G1仍未通过，高级扩展未开始。

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
