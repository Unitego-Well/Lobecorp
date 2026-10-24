# 测试前优化建议与问题原因

编写时间：2026-10-24（北京时间）。依据：`9d83824`／`476cffe`／`6759649d` 阶段的源码只读核对，以及 [性能审查](photon-performance-audit.md) 当轮实测数字。本轮只读代码，没有修改业务代码、没有构建、没有运行游戏，也没有重新采样；文中的收益均为待测方向，不是已实现结果。

本文是“实施前”文档：只回答成本由什么造成、可以怎么改、改完怎么验收。当前问题状态、编号和关闭条件仍以 [唯一问题表](../issues.md) 为准；本文不另建状态账本，也不把建议写成已实现。测试后的实际证据与修正见 [测试后优化建议与问题原因](photon-optimization-posttest-20261024.md)。

## 一、范围与证据等级

- 已测到成本的模型热点：P11 静态几何逐帧提交、P12 动画蒙皮与整帧对象化、P06 首次同步创建／发射。
- 只给归因方法：P13 空场景 1.421 秒长帧。
- 不覆盖：S01–S23 中没有专项场景实测的编辑器、时间轴、事务和工具项；原生 GLTF／VAT 分支（S10–S14）；真实激光的完整技能链（P06 同项验收缺口）。
- “已确认”只表示成本路径和样本已有证据，不代表每个毫秒都已精确分摊；执行样本占比不等于 GPU 占比。

## 二、结论摘要

| 项 | 一句话根因 | 关键证据 | 主要方向 | 风险 |
|---|---|---|---|---|
| P11 | 姿态缓存只到局部帧，最终提交几何仍每帧按三角形×4 角全量重算并写入 | 16 静态均值 3.258 ms、P99 6.256 ms；`geometry.write` inclusive 245／324 个执行样本（75.6%），重复 212／280（75.7%） | 稳定拓扑／局部几何复用；先核对原生批次能力，再决定缓存层级 | 中：透明排序、材质组顺序、双面／深度／混合／光照语义 |
| P12 | 每帧重做蒙皮矩阵、权重归一化、整帧 `Vertex`／`Group` 重建与多份临时容器 | 16 动画均值 5.784 ms、P99 10.477 ms；`sample` 222／462 个执行样本；约 23 秒录制渲染线程分配约 19.554 GiB | 预编译权重／索引＋实例局部缓冲复用；输入键轻量化 | 中高：帧快照所有权、多视图消费、morph／骨骼／部件覆盖语义 |
| P06 | 首发当帧同步完成资源读取、解析、拓扑、骨骼、命名库、深拷贝与材质准备，且实例间零共享 | 合成创建 269.998 ms／最长帧 273.700 ms；真实激光首发 860.759／855.774 ms；spark 关闭后仍在 | 内容级共享不可变定义／拓扑；准备分段与有界预取 | 高：生命周期、首次未就绪、时序／伤害、进世界卡顿转移 |
| P13 | 根因未确认，不能归给模型；需时间对齐的证据 | 空场景重复用例第 7624／9695 帧 1.421 s；同轮大量 `glFenceSync` 来自 Sodium／GUI／雾 | 先做空基线复现与事件对齐，再决定是否改 | 低（只测不改），但容易误归因 |

## 三、P11：静态模型逐帧 CPU 几何提交

### 3.1 现象

16 静态模型 MEASURE 均值 3.258 ms、P99 6.256 ms；重复用例均值 2.851 ms。渲染线程 JFR 中 `geometry.write` inclusive 为 245／324 个执行样本，重复用例 212／280（约 75.6%／75.7%）。这是执行样本比例，不是 GPU 占比或精确墙钟占比。

### 3.2 调用链

[PhotonModelOutput.bakeBatches](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L431-L452) → `bakeModelGroup`（第 454 行）→ `bakeGroup` 回调里的 [PhotonModelGeometryUtil.write](../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java#L13-L67)。

### 3.3 成本机制

1. **每帧无条件重建全部提交几何。** [sampleLocalFrame 第 402 行](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L402-L420) 的 `input.equals(sampledInput)` 命中后只是跳过 `sample()`；`bakeBatches` 仍对每个 group 调 `write`，逐三角形、逐角写顶点。
2. **世界坐标每帧必然变化。** 第 444 行 `world = translation(-cameraOrigin) × localToWorld`，相机原点每帧变，所以即使模型静止，写入的世界坐标也不同，上一帧结果无法直接复用。
3. **顶点数 ×4/3。** `write` 每个三角形写 4 个角，[第 27–30 行](../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java#L27-L30) 用 `offset = Math.min(corner, 2)` 重复末角，只为保留原生 QUADS 透明排序路径。16 模型 ×512 三角形 ×4 = **32768 次 `addVertex`／帧**。
4. **每角固定变换与光照。** 位置经 4×4 变换（第 33 行）、法线经 3×3 变换（第 34 行）、`signum(determinant)` 乘符号、长度判断与归一化（第 37–38 行）、`shade()` 6 次乘加与分支（第 56 行）、`setColor`／`setLight`／`setNormal`（第 58–62 行）；法线退化时还会分配临时 `Vector3f`（第 41–44 行）。
5. **数据结构间接访问。** 每个角都要 `group.vertices().get(indices.get(...))` 解包 `Vertex` record，再取三个 `Vector3` 分量；没有预展开的紧凑顶点数组，也没有把 4 个角共享的法线矩阵变换结果合并。

### 3.4 为什么现有缓存不足以关闭 P11

`sampleLocalFrame` 解决的是“同一输入不重复求值”，省掉的是姿态计算；P11 的成本在“求值之后、提交之前”的世界变换、法线、颜色与顶点写入，与输入是否变化无关。因此静态模型的缓存命中率再高，只要仍在 `bakeBatches` 里逐角重写，成本就不会消失。

### 3.5 优化建议

- **前置能力核对（必须先做）。** Photon 26.1.2.3 已提供 `Emitter.bakeGroup`／`bakeInstancedGroup`、`BaseMesh`、`AdditionalGPUDataSetting` 等入口（jar 内已确认存在，语义尚未验证）。先确认原生批次是否支持“静态局部网格 + 每帧仅变换”的提交方式，再决定复用层级，避免新增平行渲染体系。
- **方案 A（低风险，常数项）。** 预展开角点表（每 group 一个 `int[]` 角序列，替代 `List.get` + record 解包）；把 `world` 提取为 `float[16]` 手动乘；法线矩阵展开为 9 次乘加；`shade` 预查表；消除法线兜底分支的临时对象。保持 4 角重复、材质组顺序、insideOut、双面、深度、混合、光照语义不变。预期只削常数，不解决量级。
- **方案 B（中风险，帧缓冲复用）。** 按 `frame` 身份缓存“已展开的局部顶点缓冲”（位置、法线、UV、颜色），每帧只做世界／法线变换后写 consumer。相机原点每帧变，仍逐顶点变换，但省掉解包、重复角取数和法线兜底。
- **方案 C（高风险，收益最大）。** 走原生持久网格／缓冲（参考原生 `TileParticleRenderer.modelMeshBuffer` 的持久 VBO 思路），局部几何只上传一次，变换交给提交矩阵。必须先证明透明排序、材质批次和资源重载语义可保持，否则不采用。

### 3.6 实现约束

- 不能为了减少顶点直接删角；当前 4 角重复是 QUADS 透明排序路径的一部分。
- 材质组顺序、insideOut、双面、深度、混合、光照和资源重载语义须保持。
- 缓存键必须覆盖拓扑、局部姿态、动态材质与必要版本，不能只看模型路径。

### 3.7 验证与关闭条件

同一资产 1／16 静态模型、动画切换、仅世界 TRS 变化、相机变化、透明交叠、多材质、镜像／非均匀缩放及重载；记录每帧顶点写入量、缓冲创建／上传字节、CPU 样本与 GPU 时间。关闭条件：稳定静态输入不再无条件重做全部局部几何，重复新进程对照显示成本下降且 P95／P99 无退化，截图／排序与资源释放对照通过；仅平均 FPS 上升不能关闭。

## 四、P12：动画蒙皮、查表与整帧对象分配

### 4.1 现象

16 动画模型均值 5.784 ms、P99 10.477 ms；`sample` 占 222／462 个渲染执行样本。约 23 秒整份录制中渲染线程分配计数增加 19.554 GiB（静态约 0.984 GiB），包含 setup／预热，不是存活内存或泄漏量。

### 4.2 调用链

[PhotonModelPoseSampler.sample](../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelPoseSampler.java#L149-L255)，以及 `partMatrices`、`skinMatrices`、`morphWeights`、`morph`、`positions`、`faceNormal`、`immutable`；上层是 `PhotonModelOutput.sampleLocalFrame`。

### 4.3 成本机制

1. **每帧先做全量校验。** morph／part 用 `stream().noneMatch` 逐个比对（第 153–168 行），clip 用 `stream().filter` 查找（第 171 行）；这些只随内容变化的检查每帧重复。
2. **partMatrices 每部件多份临时矩阵。** 第 261–286 行：每部件 `new Matrix4f().translation(pivot).mul(matrix).translate(...)`，有父级时再 `new Matrix4f(parent).mul(...)`。
3. **skinMatrices 每权重顶点固定 3 个对象。** 第 386–430 行：每骨骼 `new Matrix4f` 连乘；每个带权重点执行 `stream().mapToDouble().sum()` 归一化（第 410 行）、两份 `float[16]`（第 414–415 行）、`new Matrix4f().set`（第 423 行）并放入 `HashMap<Integer, Matrix4f>`（第 427 行）。
4. **morph 整份复制顶点。** 第 300–317 行先把所有顶点复制成新 `Vector3`，再逐顶点 `immutable()`；权重全为 0 时也复制。
5. **positions 走装箱 Map + 每顶点临时对象。** 第 319–346 行：每部件一个 `HashMap<Integer, Vector3>`，每角 `computeIfAbsent`（int 装箱 + lambda 捕获），`dimensions()` 每顶点新建 `Vector3f`，蒙皮／部件矩阵两次变换后再 `immutable()`。
6. **整帧 Vertex／Group 重建。** 第 186–246 行：每角 `new Vector3(faceNormal)` + `new Vertex(...)`；每面重算 `faceNormal`（3–4 个 `Vector3f`）；`min/max` 每角 `vector(position)` 又建 `Vector3f`。
7. **输入与绑定每帧重建。** `Input` 构造内 3 次 `Map.copyOf`（第 67–79 行）；[PhotonModelBindings](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelBindings.java#L84-L131) 的 `morphWeights`／`partOverrides`／`boneOverrides` 每帧各建 `LinkedHashMap` + `Map.copyOf`，overrides 还要遍历全部 part／bone × 3 个槽。
8. **实例间零共享。** 同一资源的每个实例各自持有 sampler、拓扑和预编译结构，16 个动画实例重复这些内存与局部性成本。

### 4.4 为什么现有缓存不足以关闭 P12

动画时间每帧变化，`Input.equals` 必然不等，`sample()` 每帧全跑；`sampleLocalFrame` 的缓存只对静态模型有效，对连续动画不能命中，也不应该靠冻结动画制造命中。

### 4.5 优化建议

- **预编译（内容级、可跨实例共享）。** 顶点→权重索引与预归一化权重、顶点→UV、面法线索引、角点展开表、clip 轨道二分索引、part 维度参数；把每帧重复的 id 校验改成随内容 revision 失效。
- **每帧缓冲复用（实例级、渲染线程独占）。** 用平铺 `float[]` 替代 `HashMap<Integer, Vector3>`、两份 `float[16]` 和 `Matrix4f` 临时对象；位置、法线、UV 直接写入复用数组；`Frame` 改为引用这些数组的只读视图。注意帧快照可能被多视图／延迟批次消费，不能提前覆写，需双缓冲或不可变发布。
- **消除每帧 Map。** `Input` 与 bindings 的三个 Map 只在 overrides 真正变化时重建，用内容 revision 做键。
- **减少中间对象。** `Vertex`／`Group` record 改为平铺数组或复用容器；`min/max` 用 float 比较，不再逐角创建 `Vector3f`。

### 4.6 实现约束

不能跨实例共享可写采样时间、权重或输出数组；渲染帧快照仍可能被多个视图／延迟批次消费，缓冲不能提前复用。保留多权重、骨骼层级、部件覆盖、morph、长度／宽度拉伸、透明度和取消检查。

### 4.7 验证与关闭条件

静态／单骨骼／多骨骼多权重、morph 与骨骼组合、父子层级、外部轨道覆盖、循环边界、反向／零速／暂停及多实例不同相位；对比逐顶点位置、法线、UV、包围盒和输出图像。关闭条件：同内容 CPU 与渲染线程分配明显下降，GC／最长帧不恶化，计数器与姿态精度对照均通过。JFR 首次分配样本可能携带跨录制权重，不能将其全部归因一次调用，也不能以堆占用回落代替分配速率改善。

## 五、P06／S09：首次创建、深拷贝和同步发射

### 5.1 现象

测量开始时创建 16 动画模型耗时 269.998 ms，对应最长帧 273.700 ms；首个静态实例创建 187.634 ms，后续单实例 9.885 ms。真实激光两轮首次最长帧约 860.759／855.774 ms，关闭 spark 后仍存在；真实首发帧含多种成本，尚不能把全部时间归给一个方法。

### 5.2 调用链

[PhotonChildSpawner.emitNow](../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java#L220-L263) → `new PhotonSpawnInstance` + [`start`](../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonSpawnInstance.java#L134-L180)（第 137 行 `runtime.emit`）→ 原生 `FXRuntime.emit` 的注入点 [FXRuntimeMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/FXRuntimeMixin.java#L51-L88) → [PhotonNamedRuntime 构造](../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java#L105-L130) → `PhotonModelOutput.prepare`；此外还命中 `PhotonModelSourceUtil.load`／`PhotonModelAssetUtil.read`、`FXHelper.loadFX` 和 `IFXObject.deepCopy` 的 CODEC 往返。

### 5.3 成本机制

1. **同步循环。** `emitNow` 在当帧按次数循环 `new PhotonSpawnInstance` + `start()`（第 235–260 行），没有分帧、预算或后台任务。
2. **每实例重新准备。** 命名运行态构造里 `PhotonNamedCodecUtil.read(controller.definition())` 重新解析命名库；随后对每个 `PhotonModelOutput` 调 `prepare()`。
3. **prepare 重新读取与重建。** `PhotonModelSourceUtil.load` 重新取资源／解析内嵌 JSON/NBT；`PhotonModelTopologyUtil.prepare` 对全模型逐面三角化；`new PhotonModelPoseSampler` 逐骨骼求逆；随后重建材质。
4. **实例来源本身深拷贝。** [PhotonSpawnTemplateUtil.create](../../src/main/java/org/unitego/lobecorp/util/photon/playback/PhotonSpawnTemplateUtil.java#L35-L96) 走 `fx.createRuntime(true)`（原生 `FXData.copy` → 每个对象 `IFXObject.deepCopy` 的 CODEC 往返），或 `object.copy(true)` 后逐对象复制并改写动画轨道。
5. **零跨实例共享。** 同一资源没有“定义／拓扑／预编译权重”的内容级缓存，每个实例都重做一遍。
6. **预取只覆盖一次。** `prepareNext` 只准备“下一份子实例”且仅在 CHARGE 相位调用一次（[QueenBeamEffects 第 288–290 行](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java#L288-L290)）；`emitNow` 消费后不会自动补下一份。

### 5.4 优化建议

1. **先补细分（不改行为）。** 在 `emitNow → createRuntime → buildNamed → codec read → load → topology → sampler → materials → emit → 首次几何／GPU` 每段用现有 `PhotonPerformanceTraceUtil` 打点，分清新进程冷、同进程热、资源重载后首次和间隔施放。
2. **内容级共享（核心）。** 以资源 id + 内容哈希／代次缓存不可变定义、Prepared 拓扑、骨骼逆矩阵和预编译权重；`PhotonModelSourceUtil.load` 与 `PhotonNamedCodecUtil.read` 增加缓存；`prepare()` 仅在内容代次变化时重建。多实例只保留各自可写时钟、槽和材质。
3. **深拷贝去重。** 评估 `FXData.copy`／`IFXObject.deepCopy` 中哪些字段必须每实例复制、哪些可共享只读定义；必须先核对原生 API 允许的共享边界。
4. **准备调度。** 把“下一份”扩展为有界预备缓冲池或分帧任务；但不能把成本静默转移到进世界，首次未就绪、失败重试、取消、退出世界都要有明确生命周期。

### 5.5 实现约束

不共享活 `FXRuntime`、可写轨道或实例随机状态；不把静默预热移到进世界时后宣称总成本消失；技能时序／伤害不能因准备策略改变。

### 5.6 验证与关闭条件

同资源 1／多实例、连续与间隔发射、缺失／损坏资源、加载失败后修复、重载、准备中取消及退出世界；分列触发至可见延迟、最长帧、P99、准备次数、分配峰值和就绪状态。关闭条件：首发阻塞实测下降且没有转移为同等级进世界卡顿、准备队列堆积或首次漏播；按真实技能截图／阶段 marks 验证，不能只用合成网格成绩。

## 六、P13：空场景异常与 OpenGL 等待的独立归因

### 6.1 现象与边界

无 Debug 的末尾空场景第 7624／9695 帧为 1.421 秒；不能把这个值算成模型成本。真实采样中 `glFenceSync` 栈还来自 Minecraft／GUI／雾／AnvilLib／Sodium 的 `MappableRingBuffer.rotate`／`DynamicUniformStorage.endFrame`，不能全部记在 Photon 名下。

### 6.2 归因方法

1. 重复空基线，采集 GC 日志、JFR（gc／thread park／monitor／io／socket／driver）、帧起止时间、JFR 启停与落盘时间点、截图／导出时间点、窗口焦点与节流标志。
2. 把异常帧与上述事件做时间对齐；优先排除 JFR 自身 flush、GC 停顿、线程停泊、文件导出、驱动等待、Sodium／AnvilLib 上传。
3. 能稳定复现并定位到具体等待后再改代码；不能复现则按环境干扰结案并保留原始样本。

### 6.3 关闭条件

复现时能给出时间对齐的原因，或重复试验明确界定为当前模型外的干扰；“后续没有再看到”不等同于已修复。报告保留异常、失焦、容量丢样和失败原因，不能只删长帧或只报中位数。

## 七、执行顺序与统一验收

1. P06 细分打点（成本最低，直接决定后续收益归属）。
2. P11 前置能力核对，再做方案 A。
3. P12 预编译与缓冲复用（收益大、风险中，需逐顶点对照）。
4. P06 内容级共享与深拷贝去重（收益最大、风险最高）。
5. P11 方案 C 只在前置核对通过后考虑。
6. P13 并行归因，不阻塞前四项。

每轮只改变一种成本，固定提交、依赖、资产指纹、seed、相机、分辨率、视距与画质；同时报告均值、P50／P95／P99、最大值、长帧次数、创建／就绪延迟、渲染线程分配、GC、缓冲上传和资源峰值。执行样本、CPU 提交、GPU 时间和驱动等待分别解释，不把多个会话的 P99 简单平均。

## 八、未覆盖与待确认

- 原生 26.1.2.3 的 `bakeInstancedGroup`／`BaseMesh` 是否可用于自定义模型的静态网格，尚未验证；这是 P11 方案 C 的决策点。
- S01–S23 中本轮只做源码条件复核（S01／S02／S03／S06／S18／S20／S22），其余仍按 [唯一问题表](../issues.md) 的“未专项验收”处理。
- 本轮没有重新采样，所有数字来自当轮权威报告；测试后的实测与差异见 [测试后优化建议与问题原因](photon-optimization-posttest-20261024.md)。
