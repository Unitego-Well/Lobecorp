# Photon 未解决问题汇总

更新：2026-10-24。此页是 codex/photon 工作区唯一维护当前问题状态的文档，范围只限 Photon／特效及其必要工具边界。能力、源码、已完成实现和历史检查见 [Photon 总览](systems/photon-workspace.md)。已解决项从当前待办移除；历史页保留当时证据，不再维护另一份当前问题列表。

下方功能续做历史阶段未运行验证；2026-10-24 已按用户授权完成 Sodium＋BenchMod、Minecraft MCP／Debug 与真实激光性能采样，实际证据见 [性能审查](systems/photon-performance-audit.md)。“未验证”仍表示缺少对应证据，不等同于确认存在运行缺陷。

## 性能测试进度（2026-10-24）

- 授权与计划：继续使用 Sodium＋BenchMod、Minecraft MCP 与 IDEA Debug 排查模型渲染和动画，优先建立空场景、单模型、16 模型、静态／动画与首次创建对照，再测试迁入的真实激光资源。采样区间不设断点、不切换焦点；Debug 仅用于测量区间之外的执行路径核对。
- 当前阶段：本轮采样与归因已完成，等待针对热点的窄范围优化。无 Debug 的 8 个 Photon 用例及最终报告校验通过；两轮真实激光各完成 90 秒／3 次施放，包含 spark 开关与 GPU 帧采集。已确认 CPU 几何提交、动画分配和同步发射阻塞；并非 S01–S23 全部解决。完整数字、原始证据和未覆盖项见性能审查页。
- 已完成前置：118 项正式资源已在模组内，81 项新增迁移提交为 1a5c2c3，原字节备份与哈希核对见 [资源说明](systems/resources-datagen.md#photon-内置资源)。本轮基准代码与证据文档已纳入本地提交 9d83824，未推送，用户原有改动保持。
- 原始证据与结论已写入 [性能审查页](systems/photon-performance-audit.md)；本页新增 [待优化详细列表](#待优化详细列表)，覆盖当前确认热点、S01–S23 的后续处理和关闭条件。

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

## 待优化详细列表

整理时间：2026-10-24 08:21（北京时间）。依据为 9d83824／476cffe 阶段的源码核对及既有实测，本次没有重新采样。以下均为**尚未实施的优化建议或待验证工作**；编写清单不代表批准实施全部方案。当前状态只在本页维护，[性能审查](systems/photon-performance-audit.md)保留当轮数据、方法和 S01–S23 历史证据。

### 阅读顺序与证据等级

| 顺序 | 范围 | 当前判定 | 处理原则 |
|---|---|---|---|
| 第一批 | P11 静态几何、P12 动画采样、P06 同步创建／发射 | 已测到成本与对应调用链 | 逐项实施、逐项复测，避免把多种改动的收益混在一起 |
| 独立查因 | P13 空场景 1.421 秒异常、首发长帧内的驱动／准备细分 | 现象已记录，完整根因未确认 | 先关联时间与栈，不直接改代码或删除异常样本 |
| 第二批 | S01–S08、S15–S17 | 部分源码条件仍在，但缺相应场景实测 | 先复现编辑器／时间轴／事务负载，再决定实现范围 |
| 第三批 | S10–S14 的原生 GLTF／VAT 分支 | 当前主要样本没有覆盖原始批评的完整分支 | 用明确命中该后端的资源独立量测，不把自定义模型结果套上去 |
| 贯穿各批 | S18–S23、工具干扰与资源生命周期 | 仍缺专项对照或账本 | 工具开销过高时提前处理；关闭安全保护不属于优化方案 |

“已确认”指成本路径和样本已有证据，不代表每个毫秒都已精确分摊；“待测”不等同于已经确认存在缺陷。优先级是执行建议，不改动旧审查当时的分级。

### P11：静态模型逐帧 CPU 几何提交

- **现象与证据：**16 静态模型 MEASURE 均值 3.258 ms、P99 6.256 ms；重复用例均值 2.851 ms。渲染线程 JFR 中 geometry.write inclusive 为 245／324 个执行样本，重复用例 212／280，约 75.6%／75.7%。这是执行样本比例，不是 GPU 占比或精确墙钟占比。
- **代码入口：**[PhotonModelOutput](../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) 的 bakeBatches／bakeModelGroup、[PhotonModelGeometryUtil](../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java) 的 write。局部姿态缓存命中后仍逐帧转换并提交最终顶点。
- **需要优化：**将稳定拓扑、静态局部几何、实例世界变换分别识别；减少未变化的几何重复展开、逐角坐标／法线／颜色计算及完整上传。先核对 Photon 原生批次和缓冲已有能力，再决定复用层级，避免新增平行渲染体系。
- **实现约束：**当前三角形通过重复末角进入 QUADS 透明排序，不能为了减少顶点直接删角；材质组顺序、insideOut、双面、深度、混合、光照和资源重载语义须保持。缓存键必须覆盖拓扑、局部姿态、动态材质与必要版本，不能只看模型路径。
- **建议验证：**同一资产的 1／16 静态模型、动画切换、仅世界 TRS 变化、相机变化、透明交叠、多材质、镜像／非均匀缩放及重载。记录每帧顶点写入量、缓冲创建／上传字节、CPU 样本与 GPU 时间。
- **关闭条件：**稳定静态输入不再无条件重做全部局部几何，重复新进程对照显示成本下降且 P95／P99 无退化；截图／排序与资源释放对照通过。仅平均 FPS 上升不能关闭此项。

### P12：动画蒙皮、查表与整帧对象分配

- **现象与证据：**16 动画模型均值 5.784 ms、P99 10.477 ms；sample 占 222／462 个渲染执行样本。约 23 秒整份录制中渲染线程分配计数增加 19.554 GiB，静态约 0.984 GiB；包含 setup／预热，不是存活内存或泄漏量。
- **代码入口：**[PhotonModelPoseSampler](../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelPoseSampler.java) 的 sample／skinMatrices／positions／faceNormal／immutable，以及 PhotonModelOutput.sampleLocalFrame。
- **需要优化一——准备阶段：**将骨骼／部件字符串查找、顶点权重索引、归一化权重、父序和稳定拓扑信息预编译；避免每个采样点重复 Stream 求和、整数装箱和 HashMap 查找。
- **需要优化二——求值阶段：**减少每顶点两份 float[16]、Matrix4f 和映射项，以及位置、法线、Vertex／Group 整帧重建；在明确所有权的实例局部缓冲中计算，按实际变化准备法线／几何。只在输入真正相同时复用姿态，不能冻结连续动画来制造缓存命中。
- **实现约束：**不能跨实例共享可写采样时间、权重或输出数组；渲染帧快照仍可能被多个视图／延迟批次消费，缓冲不能提前复用。保留多权重、骨骼层级、部件覆盖、morph、长度／宽度拉伸、透明度和取消检查。
- **建议验证：**静态／单骨骼／多骨骼多权重、morph 与骨骼组合、父子层级、外部轨道覆盖、循环边界、反向／零速／暂停及多实例不同相位；对比逐顶点位置、法线、UV、包围盒和输出图像。
- **关闭条件：**同内容 CPU 与渲染线程分配明显下降，GC／最长帧不恶化；计数器与姿态精度对照均通过。JFR 首次分配样本可能携带跨录制权重，不能将其全部归因一次调用，也不能以堆占用回落代替分配速率改善。

### P06／S09：首次创建、深拷贝和同步发射

- **现象与证据：**测量开始时创建 16 动画模型耗时 269.998 ms，对应最长帧 273.700 ms。真实激光两轮首次最长帧约 860.759／855.774 ms，关闭 spark 后仍存在；真实首发帧含多种成本，尚不能把全部时间归给一个方法。
- **代码入口：**[PhotonChildSpawner](../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java).emitNow → [PhotonSpawnInstance](../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonSpawnInstance.java).start → FXRuntime.emit → [PhotonNamedRuntime](../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java) 构造 → PhotonModelOutput.prepare；还命中 PhotonModelSourceUtil.load／PhotonModelAssetUtil.read、FXHelper.loadFX 和 IFXObject.deepCopy 的 CODEC 往返。
- **先补细分：**分别记录资源打开、NBT／JSON 解码、实例复制、拓扑／骨骼准备、材质解析、命名轨道初始化、emit、首次几何与首次 GPU 使用。区分新进程冷、同进程热、资源重载后首次和间隔施放。
- **需要优化：**内容版本内共享不可变定义和拓扑，合并同资源的重复准备；可后台执行的纯读取／解析使用有界任务，发布时核对内容代次与取消状态；注册表、活运行态和 GPU 操作留在其合法线程。优先复用现有下一份子实例预备机制，检查它尚未覆盖的解析／复制成本。
- **实现约束：**不共享活 FXRuntime、可写轨道或实例随机状态；不把静默预热移到进世界时后宣称总成本消失。首次未就绪、失败重试、资源变化、退出世界和延迟返回均须有明确生命周期，技能时序／伤害不能因准备策略改变。
- **建议验证：**同资源 1／多实例、连续与间隔发射、缺失／损坏资源、加载失败后修复、重载、准备中取消及退出世界。分列触发至可见延迟、最长帧、P99、准备次数、分配峰值和就绪状态。
- **关闭条件：**首发阻塞实测下降且没有转移为同等级进世界卡顿、准备队列堆积或首次漏播；按真实技能截图／阶段 marks 验证，不能只用合成网格成绩。
- **同项验收缺口：**code_animation_render_samples 为空，现有 marks 只能证明阶段变化。连续跟随、墙体截断、瞬时星束、恢复混合、取消、晚追踪和卸载仍需独立验收；旧射线／参数 Map 修改不能据此宣布全部批评已解决。

### P13：空场景异常与 OpenGL 等待的独立归因

- **现象与边界：**无 Debug 的末尾空场景第 7624／9695 帧为 1.421 秒；不能把这个值算成模型成本。真实采样中 glFenceSync 栈还来自 Minecraft／GUI／雾／AnvilLib／Sodium 的 MappableRingBuffer.rotate／DynamicUniformStorage.endFrame，不能全部记在 Photon 名下。
- **需要补证据：**将异常帧时间与 GC 停顿、线程停泊／锁、文件导出、JFR 启停、渲染提交、驱动调用和外部调度关联；按调用路径区分 CPU 计算、驱动等待、GPU 范围以及展示／限帧。
- **处理顺序：**先在空基线重复定位；仅在出现稳定归因后修改对应代码。报告保留异常、失焦、容量丢样和失败原因，不能只删长帧或只报中位数。
- **关闭条件：**复现时能给出时间对齐的原因，或重复试验明确界定为当前模型外的干扰；“后续没有再看到”不等同于已修复。

### 编辑器、时间轴和 MCP：S01–S08

以下条目均未完成专项运行验收。依赖类表示 Photon 26.1.2.3 的已核对入口，后续实施前仍需 IDEA 重新确认。

| 旧编号 | 当前证据与代码入口 | 需要继续处理的内容 | 关闭所需证据 |
|---|---|---|---|
| S01 同步 seek | SceneView.simulateTo／runSimulationTicks；ModelSceneViewMixin 主要提供观察 | 先量测回退／长跨度 seek；若阻塞成立，将重放改为有内容身份的分片任务，完整结果才发布 | 长跨度操作不中断输入；最终状态与原完整重放一致，取消后旧结果不发布 |
| S02 同刻重放与超长时间标签 | 当前相等时间走 reset；循环最多 10000 tick 后仍设置目标时间 | 以内容版本、seed、目标时间和运行设置组成去重条件；真实处理进度与展示时间分离 | 同目标不无故重放；超过上限明确完成／截断状态，数据与时间标签一致 |
| S03 pending 身份 | pendingSimulateTarget 仍主要记录时间，缺完整工程／内容身份实验 | 请求绑定工程、runtime／内容代次；重置、关闭、切换工程使旧请求失效 | 切工程／重载／撤销后不会发布旧结果；所有取消对象均可追踪 |
| S04 高频参数重放 | 参数、种子、循环等编辑预览入口；当前游戏播放样本不覆盖 | 盘点直接重放调用并量测；对等价预览使用同一个最新请求机制，避免多个独立队列 | 连续拖动请求有合并计数和最终完成证据，最终值、seed 与循环语义一致 |
| S05 MCP seek／取消 | 场景模拟与 MCP 预览／播放任务入口 | 把已排队、执行中、完成、取消与失败分清；取消只影响本请求，不伪称打断已完成同步工作 | 连续请求／取消／关闭测试有 request 与 runtime 身份、最终状态和无过期写回证据 |
| S06 多次展开轨道树 | TimelinePlayerMixin 的 evaluate／frame 扩展仍使用 leafTracks／流与集合；TimelinePlayer 原生路径需一起测 | 按内容版本准备保序执行列表，并在真实编辑、mute、控制边界和录制变化时失效 | 多轨道输入下展开次数与分配下降；控制结果、顺序、录制与边界事件一致 |
| S07 每目标扫描控制轨道 | TimelinePlayer 的控制评估、PhotonNamedGates／PhotonChildWindows 等相关控制链 | 量测目标数×轨道数；为目标／区间建立保序索引，明确循环和端点规则 | 大量目标时扫描量下降；重叠片段、层级 mute、精确端点与 seed 无行为差异 |
| S08 重复完整快照 | MCP 状态回复、工程序列化／canonical／哈希链 | 单请求内复用同一不可变快照；设计轻量播放／任务状态，完整状态显式读取 | 大工程一次查询的快照／哈希次数与字节量可测，revision 冲突保护、完整回复字段不退化 |

### 原生模型后端、缓存和事务：S10–S17

S09 已并入 P06，不另建同一问题副本。下表的原生 GLTF／VAT 路径与本项目 PhotonModelOutput 不能混为一个后端。

| 旧编号 | 当前状态与涉及入口 | 待优化或验证方案 | 关闭所需证据 |
|---|---|---|---|
| S10 GLTF 首次解析 | 原批评指 AnimatedGltfModelSource.load／PhotonMeshCache；真实激光主要命中自定义模型准备 | 用 GLTF 资产细分读取、解码、骨架和网格准备，再决定后台纯解析／同资源合并；P06 的自定义准备另行核对 | 原生冷选、首绘、重开、错误修复后重试均有分段成本；不以未命中当通过 |
| S11 VAT 烘焙／上传与副本 | AnimatedGltfModelSource.vertexAnimation、VertexAnimationBake／VertexAnimation；本轮未覆盖 | 量测烘焙、staging、上传与 CPU／GPU 副本；共享不可变 payload，实例相位独立；失败键随内容失效 | 多个相同源不重复整表烘焙／分配；就绪延迟、峰值、取消、关闭和 shader 编码一致 |
| S12 动态 mesh 身份导致重建 | DynamicMeshCache.resolve 新几何包装；TileParticleRenderer.modelMeshBuffer 仍按 mesh 身份判断重建 | 先用明确命中此链的动画资产记录 VBO／IBO 创建、上传和销毁次数；按拓扑与几何 revision 分离稳定属性和动态属性 | 动画时稳定索引／UV 不因包装身份变化重建；CPU 预览姿态和原生 VAT 分支都正确 |
| S13 原生 CPU fallback | 原批评是 TileParticleRenderer 等原生粒子后端；自定义完整几何提交归 P11 | 明示实际实例化／CPU 路径、回退原因、顶点和上传量，核对兼容能力后再选择后端 | 真正命中实例化／回退两条路径的对照结果；无材质、排序或平台兼容退化 |
| S14 法线／切线与顶点展开 | 原生缺法线／切线生成未验收；自定义动态法线成本归 P12 | 记录导入顶点／展开顶点比；仅按材质需求准备切线，稳定数据缓存，动态几何按必要版本更新 | 缺法线、法线贴图、镜像 UV、变形和重载有正确图像与计算次数证据 |
| S15 姿态快照和失效 | 自定义 Input／Frame 的不可变容器只是局部保护；原生数组和组合依赖未验收 | 明确只读定义、实例缓冲、帧快照所有权；缓存键覆盖骨骼／morph／外部轨道／资源代次，优先局部失效 | 多实例不同相位、撤销／重做和重载不互串；旧快照不会被下一帧覆盖，缓存命中与失效可解释 |
| S16 MCP 文件准备与取消 | 大工程保存／导出准备是否长占客户端仍待测 | 先在客户端捕获不可变内容，再有界后台编码／写入准备，提交前核对 revision；取消后不发布旧文件 | 大工程、取消、外部改文件、权限／写入失败均有实际状态，超时不被描述为已中断后台工作 |
| S17 扫描与重复解码 | NBT／ZIP 安全扫描及多层事务校验没有本轮解码次数数据 | 在单个事务内复用已验证的同一份字节／内容指纹；外部文件变化重新验证，记录扫描、解码和复制成本 | 普通／大包／损坏包／高压缩比包有次数与耗时对照；路径穿越、预算、CRC／内容变化保护仍有效 |

### 检测工具、导出与 GPU 生命周期：S18–S23

| 旧编号 | 当前状态与涉及入口 | 待优化或验证方案 | 关闭所需证据 |
|---|---|---|---|
| S18 身份包装与快照锁 | PhotonPerformanceTraceUtil 身份表／线程缓冲、Session.view；未做详细 Trace 开关 A/B | 测分配、锁等待与全量快照；按线程有界身份缓存、短锁冻结增量，后台生成面板 DTO，禁止后台遍历正在写入的数组 | 开关 A/B、writer 等待、查询成本与关闭引用回落；工具成本不压过业务 |
| S19 隐藏／冻结图表计算 | PhotonPerformancePanel／PhotonPerformanceFrameChart；大面板未测 | 按 session／revision／过滤／排序／可见页复用结果；隐藏不准备图表，冻结数据不反复全量分桶 | 大会话、冻结、切页与筛选仍响应；重复排序／分桶次数下降，显示内容正确 |
| S20 开始采集大数组 | PhotonFrameCapture 与 TraceUtil.Buffer 的启动／首线程分配 | 区分 setup 和稳定阶段，按模式／预计时长与总预算分配；必要时分块，不隐藏预分配成本 | 启动、首 worker、容量停止和取消的分配／GC／最长帧可测；overview 不无条件承担 detail 全量成本 |
| S21 结束统计与导出 | PhotonFrameCapture.completeSafely／summary、frame-only 导出；P13 不能未经证据归给它 | 冻结后后台统计／编码／写盘，状态查询只读取轻状态，复用已有有界导出器与并发限制 | 大样本停止／取消／失败有客户端响应时间与持有内存证据；最终文件完整，重复结束不重入 |
| S22 GPU 结果分配 | PhotonPerformanceGpu.sample 仍逐项构造 JsonObject；帧 GPU 有效不等于详细工具开销已清零 | 先做 GPU 采集开关 A/B；若 JSON 分配显著，改原始有界记录、导出时编码 | 保留 started／completed／skipped／timeout／error；禁止等待 GPU、填零缺测或把嵌套 inclusive 时间相加 |
| S23 GPU 资源所有者 | 正常退出只说明本次生命周期结束，未建立缓冲／查询／纹理账本 | 按 owner、generation、bytes、创建／退休／close 原因追踪；唯一所有者关闭，等待延迟帧消费后回收 | 重复开关、移除、重载、异常与退出世界后创建／释放可配平，稳定回落；不能靠 GC 或一次截图证明无泄漏 |

### 优化前后统一验收与记录要求

1. 固定代码提交、依赖、资产指纹、seed、相机、分辨率、视距与画质；记录实际后端、窗口焦点／最大化、节流与测量工具开关。粒子 seed 未固定时明确限制，不能称严格确定性对照。
2. 每次只改变一种成本；保存完整 summary、JSONL、JFR、GPU 状态、截图与调用链；进程冷／热、同进程重复和间隔后施放分别记录。P99 按会话比较，不把多个会话 P99 简单平均。
3. 同时报告均值、P50／P95／P99、最大值、长帧次数、创建／就绪延迟、渲染线程分配、GC、缓冲上传和资源峰值。执行样本、CPU 提交、GPU 时间和驱动等待分别解释。
4. 保留失败、截断、失焦、pending 跳过和异常长帧。工具关闭后卡顿仍在只说明该工具不是唯一必要条件，不能据此精确归因其他方法。
5. 保持实体动画、技能逻辑、资源内容、LOD、动画更新频率、透明排序和材质表现；不得以减负载或漏播换取成绩。模型格式仍遵循现有 .mfxproj／.mfx／.mfxpack 约定。
6. 未覆盖的正确性场景和工具字段缺失继续保留待验证；完成一项后从本页当前待办移除，将实现、证据和提交记入总览／修改记录。没有复测证据不填写改善百分比。

本次只交付详细列表：没有实施上述生产优化，也没有新增构建、测试、同步、Data、重载或游戏采样。


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
