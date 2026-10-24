# Photon 渲染架构优化报告（含 Mixin 方案）

编写时间：2026-10-24（北京时间）。基线 `6ae3093c`（法线缓存 `bd29ff62`＋内嵌模型准备复用 `fba6713a`）。原始方案轮只做设计；后续审查、M7 局部实现和实际结果见第九节，当前问题以唯一清单为准。

依据：[真实激光架构级诊断与优化方案](photon-laser-architecture-20261024.md)、[激光测试报告](photon-laser-optimization-20261024.md)、[性能审查](photon-performance-audit.md)，以及 Photon 26.1.2.3 的 jar 字节码与着色器（`assets/photon/shaders`）核对。当前问题状态仍以 [唯一问题表](../issues.md) 为准。

## 一、结论先行

1. **不能用光影轮的一组 inclusive 样本推断所有场景的优化上限。** 1044 个渲染样本中的 write 3.0%、sample 8.8%、Iris 36.8%（阴影 31.5%）是该轮栈命中比例，彼此可能重叠；不是 CPU／GPU 时间分摊，也未隔离光束与女皇／世界成本。法线与内容准备复用的稳定收益仍未证明，不能据此断言一定无效或架构迁移必有数量级收益。
2. **真正的架构问题是：光束被当作“粒子模型即时提交”在每帧展开。** [PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L431-L452) 每帧把三角形展开成 4 角 QUADS 写进 `VertexConsumer`，原生 `PhotonWorldRenderState.drain` 再消费上传；没有持久网格、没有实例化、没有 GPU 形变。
3. **Photon 本身已经有两套更合适的原生路径，只是没有用上：**
   - **实例化模型**：`InstancedVariant.MODEL / MODEL_TANGENT / MODEL_VAT*`，基网格可在内容与设备代次有效时复用（重载／失效仍需重建），每实例按变体传 `iPos / iScale / iRot / iColor / iLight`，顶点着色器计算 `world = R · (aPos · iScale) + iPos`；`MODEL_VAT` 用一张按顶点×帧烘焙的纹理把动画也放到 GPU（`VertexAnimationBake`）。
   - **实例化光束**：`BeamEmitter` 在 `renderer.isUseGPUInstance()` 为真时直接走 `bakeInstancedGroup(..., InstancedVariant.BEAM, PhotonWorldRenderState.beamQuad(), ...)`；每实例只传 `iStart(含半宽) / iEnd / iColor / iUV / iLight`，一次提取按该 emitter 的实例生成按材质拆分的 Job；不保证跨 emitter 一个 draw。
4. **Mixin 应该用在“把我们的模型接入原生实例化路径”和“光影边界”上，而不是继续改顶点循环。** 具体目标见第六节；其中相当一部分可直接用 Photon 的公开 API 完成，Mixin 是否必要要以接口缺口为准，不预先补写私有编排或改变光影路由。

## 二、当前成本结构（实测回顾）

| 场景 | 空场景 | 施放窗口 | 首发最长 | 备注 |
|---|---:|---:|---:|---|
| 无光影（12:55） | 1.843 ms | 6.275／3.944／3.953 ms | 981.357 ms | CPU 均值 1.114、GPU 均值 0.642 |
| 光影（12:34） | 9.320 ms | 25.548／19.693／20.491 ms | 717.173 ms | CPU 均值 3.605、GPU 均值 11.581 |

光影施放窗口 JFR：Iris 36.8%（阴影 31.5%）、Minecraft 渲染 68.7%、`PhotonModelOutput` 15.1%、`WorldRenderState drain` 13.5%、`sample` 8.8%、`write` 3.0%、`BufferBuilder` 6.1%。首发长帧中 CPU／GPU 抬升仅提示复杂等待或工作重叠，未分离编译、纹理、GC、JIT 和准备；还必须按各数组实际范围对齐，不能直接确诊管线编译。

## 三、Photon 已有的可复用能力（已核对）

| 能力 | 位置 | 关键事实 |
|---|---|---|
| 实例化模型顶点格式 | `particle.glsl` `PARTICLE_MODEL_INSTANCE` | 基网格 `aPos/aUV/aNormal/aTangent`；实例 `iPos/iScale/iRot/iColor/iLight`；世界变换在顶点着色器完成 |
| 顶点动画纹理 | `MODEL_VAT`＋`VertexAnimationBake` | 每顶点每帧一个 texel，逐实例按时间取样，CPU 不参与逐帧形变 |
| 实例化光束 | `BeamEmitter#bakeBatches` | `isUseGPUInstance()` 时调用 `bakeInstancedGroup(..., BEAM, beamQuad(), ...)`；否则回退 CPU `BeamParticleRenderer` |
| 批次/实例上传 | `PhotonInstanceRing`、`Emitter.bakeInstancedGroup` | 每帧只写实例 FloatBuffer；基网格是持久 `GpuBuffer` |
| 光影状态查询 | `IrisCompat#isShadowPass`、`isUsingShaderPack` | Photon 自己已有 public API，可直接用于阴影 pass 门控 |
| 实例化 Job | `PhotonWorldRenderState.InstancedJob/InstancedGeometry/DrawBindings` | 构造器与访问器均为 public，可由自定义代码构造（但仍依赖私有 helper 才能完全复用） |
| 图着色器路由 | KilaGraph `RenderTypeFactory` | 当前因顶点格式不满足 ENTITY 而绕开 shaderpack，并打印 `shaderpack integration needs the ENTITY format` |

## 四、架构方案（按收益/风险排序）

### 方案 1：模型接入原生实例化路径（候选，现有表现等价性未成立）

把 `PhotonModelOutput` 的每帧 QUADS 提交替换为：

1. 准备阶段：从 `PhotonModelTopologyUtil.Prepared` 构建 **持久基网格**（每材质组一份顶点/索引 `GpuBuffer`），材质组继续用现有 `PhotonModelMaterials.group(i)`。
2. 每帧：只计算每个“部件实例”的 `iPos/iScale/iRot/iColor/iLight`，调用原生 `bakeInstancedGroup`（`MODEL` 或 `MODEL_TANGENT`）。模型若只是部件 TRS／部件覆盖变化，仅在无剪切、法线／ObjectPosition／shading 和排序等语义均等价时才可候选；不能仅凭 TRS 名称认定这一步足够。
3. 若动画包含顶点级形变（骨骼／morph）：VAT 只能作为待验证选项（会涉及时间离散、插值与法线量化），不能直接替换连续求值或任意动态骨骼／morph／覆盖组合；每帧仍只传实例参数与时间。原生 `VertexAnimationBake` 面向 GLTF `SkinnedModel`，自定义模型需要按同一 texel 布局自行烘焙（可先用我们的 `PhotonModelPoseSampler` 逐帧生成位置/法线）。
4. 首个实例的基网格上传、材质/管线准备放到准备阶段；测量窗口只保留实例填充与小上传。

收益假设：合格实例可减少 CPU 顶点展开，但 +0.5 ms 尚无原型或实测依据，删除该数值预测。原生 helper 每 emitter／材质仍产生 Job，drain 的 CPU 路径本就会合并相邻同 RenderType／mask Job，不能承诺 draw 数必从组×实例降为组。GPU 顶点／片元与 Iris 工作是否减少须实测。

风险：实例 transform 是否足以表达当前模型动画（若含逐顶点形变则必须 VAT）；材质组与实例的对应关系；半透明排序仍由原生 drain 决定，需要逐项对照。

### 方案 2：原生实例化光束（若允许调整光束资产）

直接用 `BeamEmitter`＋`useGPUInstance=true` 表达光束核心（可叠层：核心束、辉光、噪波），每束一个 quad 实例，一个 draw。现有模型只保留蓄力／星beam等非光束部分。

预期：CPU/GPU 都接近“一个 quad”的成本；资产与准备也显著变轻。风险：外观与现有模型差异需要美术对照，属于资产/视觉变更，不是纯性能改动。

### 方案 3：光影阴影与 Iris 路由（与方案 1/2 并行）

- **阴影**：31.5% 是 Iris 阴影栈样本，并未证明光束被额外提交或应当去掉阴影。先计数实际 shadow pass 中的光束任务与绘制；当前不跳过、不新增阴影开关。即使存在重复，也须先证明删除不改现有表现。
- **Iris 路由**：把光束/实例化网格的顶点格式补齐到 shaderpack 期待的 ENTITY 布局（切线、UV1/UV2），使 `RenderTypeFactory` 不再绕开 shaderpack。收益不确定：进入 shaderpack 管线可能更重，也可能因统一管线减少状态切换；必须 A/B，不能假设一定更快。
- **材质合并**：只是待验证方向。合并可能改变深度、混合、排序与 graph 输入；减少 draw 不自动减少片元 overdraw，本轮不合并或改写资产。

### 方案 4：首帧预热

把基网格上传、VAT 上传、材质/管线编译、纹理上传移到 SETTLE 之前（或首次进入事件前）。预热成本单独报告；不能把成本静默转移到进世界或技能前摇。

## 五、参数传输、命名动画与骨骼 GPU 化

### 5.1 参数通道实测

光影施放窗口 1044 个渲染样本中：`PhotonNamedRuntime` 8.4%、`PhotonNamedMixer` 2.9%、`RuntimeValue/RuntimeBinding` 2.0%、`PhotonMaterialUniforms` 0.4%；`AdditionalGPUDataSetting`／`PhotonGpuChannels`／`PhotonInstanceRing` 在当前模型路径为 0%。这些 inclusive 栈可能嵌套，不能相加成约 13%，也不能与 Iris 的整类栈简单比较为互斥成本；0% 仅表示该轮没有采到，不证明没有调用。

参数路径分三类：

1. **CPU 求值**：`PhotonNamedRuntime.tick/frame` 每帧回调、`PhotonNamedMixer.begin/add/commit`、`RuntimeValue.get`、`RuntimeBinding`、`NumberFunction`。当前 mixer 每帧构造 `List<Float>`、`ArrayList`、`List.copyOf`，按 target/property 分组后全量 `install`，输入未变化也重算。
2. **我们自己的模型绑定**：`PhotonModelBindings.morphWeights/partOverrides/boneOverrides` 每帧建 3 个 Map＋`Map.copyOf`，并遍历全部 part/bone×3 槽；前轮 HashMap／Integer／Map 分配来自多条链，未分离该类的精确贡献，不能全部归到这里。
3. **GPU 传输**：材质 uniform（`PhotonMaterialUniforms` 按 RenderType 缓存，但值变化时会重新 upload）、实例缓冲（`iPos/iScale/iRot/iColor/iLight`）、`PhotonData`／custom data 通道。当前模型路径没有用实例/通道，参数主要经 uniform 与 CPU 顶点；迁移到实例化后，应尽量把每帧变化的参数放进实例属性（`iColor/iUV` 等），避免新增 custom data 通道。

### 5.2 参数与命名动画的优化方向

- **预编译参数计划**：把字符串路径/slot 查找、`NumberFunction` 类型分派、绑定列表在内容版本变化时编译成索引与指令数组，每帧仍按原顺序、时间和随机源求值；不能仅因输入对象未换就跳过时间函数。
- **只重算变化目标**：不能仅用目标／属性／内容版本缓存结果；时间、循环、优先级、随机状态与原生时间轴基础值都会变化。PhotonNamedMixer.begin 每轮先 release，跳过 install 可能使覆盖丢失。本轮仅预解析稳定槽引用，仍逐帧求值和发布不可变 Map。
- **绑定 Map 去物化**：用平铺数组＋版本号代替每帧 `Map.copyOf`，只在 override 真正变化时重建。
- **uniform/实例合并**：同一材质每帧最多一次 uniform 更新；能用实例属性表达的参数不要再单独走 uniform；避免为未使用的通道保留 `PhotonData` 记录。
- **同步边界**：本轮是单机夹具；若多人环境会同步运行时参数，需要单独测序列化与包量，不能把单机结论外推。

### 5.3 动画与骨骼的 GPU 化（由易到难）

1. **纯 TRS 动画**：用原生 `MODEL` 实例化的 `iPos/iScale/iRot` 表达部件变换，不做 GPU 蒙皮；以部件缩放/旋转为主的光束最合适。
2. **VAT**：把骨骼／morph 后的顶点位置与法线按“顶点×帧”烘焙进纹理，用 `MODEL_VAT`；原生 `VertexAnimationBake` 面向 GLTF，自定义模型需按同一 texel 布局自行烘焙。该原生布局为每顶点每帧 4 float（RGBA32F，w 为打包法线），另有基础网格、上传副本和元数据；离散时间与法线量化不保证等价于当前连续采样。原生 MAX_TEXELS=2000000；不能随意降低动画更新率，本轮不采用 VAT。
3. **GPU 蒙皮**：上传骨骼矩阵纹理与每顶点骨索引/权重，在顶点着色器做线性混合蒙皮。Photon 原生实例化格式没有骨索引/权重属性，需要扩展 `PhotonPipelines` 格式与自定义 shader，或走自定义渲染器；优点是内存小、支持混合与动态骨骼，代价是 shader 复杂度与兼容性。
4. **形变/布料/顶点动画**：优先烘焙（VAT）或 compute 预计算；Photon 自带 `catmull_rom.comp` 但不提供通用 compute 粒子通道，compute 方案需要自定义管线。
5. **LOD/更新率**：只能作为辅助，不能默认降画质；动画更新率、骨骼数、morph 数要有内容预算。

### 5.4 外部参考：引擎、游戏与 Mod 的成熟做法

- Unreal Niagara 用 GPU 实例计数与 indirect draw，把实例生成/剔除放到 GPU，CPU 只提交批次：[Niagara AddDrawIndirect](https://dev.epicgames.com/documentation/en-us/unreal-engine/API/Plugins/Niagara/FNiagaraGPUInstanceCountManager/AddDrawIndirect?application_version=5.1)。
- Unity 的 GPU Skinning：骨骼矩阵驻留 GPU、顶点着色器蒙皮：[GPU Skinning 手册](https://docs.unity.cn/cn/tuanjiemanual/Manual/GpuSkinning.html#1)。
- VAT/VAB 烘焙工具链：骨骼/形变预烘成纹理，运行时零 CPU 蒙皮：[Vertex Animation Toolkit](https://extensions.blender.org/add-ons/vat/)。
- GeckoLib 的 Bone Snapshots：Minecraft 生态里骨骼动画仍以 CPU 逐帧快照为主，说明女王本体这类 GeckoLib 模型不能直接套 Photon 的 VAT，需要单独评估：[Bone Snapshots](https://wiki.geckolib.com/docs/geckolib5/updating/important/bone-snapshots/)。
- 本仓库 horde 的 `HordeGpuRenderer`：常驻网格＋实例批次＋GPU 运动纹理＋Iris 兼容，是 Photon 之外的现成参考实现。

共同模式：**CPU 只提交实例参数与批次，几何/动画/剔除尽量常驻 GPU；避免每帧重建对象与逐顶点 CPU 展开。**

## 六、Mixin 补丁设计（只给目标与注入点，不含实现）

> 标注“已验证”的类/方法是本轮从 jar 或源码核对过的；“待 spike”表示需要最小原型确认签名与行为。

### M1 光影阴影门控（尚未证明参与阴影，本轮不实施）

- 目标（原生光束）：`com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter#bakeBatches(PhotonViewSettings, List, Camera, float)`；
  注入：`@Inject(method="bakeBatches", at=@At("HEAD"), cancellable=true)`；
  条件：`IrisCompat.isShadowPass()` 且该发射器未标记“投射阴影” → `ci.cancel()`。
- 我们的模型输出无需 Mixin：在 `PhotonModelOutput#bakeBatches` 开头用同一条件返回即可。
- 风险：若某些光束阴影是刻意效果，需要按发射器开关；先 A/B 验证视觉与收益。

### M2 原生实例化模型的“桥”

- 目标：`com.lowdragmc.photon.client.gameobject.emitter.Emitter`。
- 复核：Photon 26.1.2.3 Emitter:561 的 bakeInstancedGroup 为 protected final，PhotonModelOutput 可从子类直接调用；参数本就包含指定 RendererSetting.Runtime、AdditionalGPUDataSetting、BaseMesh 与 InstanceFiller。私有 staging／排序／ring 已由原方法管理，不构成必须复制编排的理由。
- 当前决定：不新增 Mixin 桥、不复制私有编排。先用现有受保护方法评估调用条件；只有出现实际接口缺口才重新设计最小扩展。
- 待 spike：`AdditionalGPUDataSetting` 在实例填充前被调用 `planAttribs/attribFloats`（字节码可见），应提供有效 setting 并遵守记录数量／变体协议；不能直接允许 null，后续还有多处解引用。
- 风险：复制原生编排会随 Photon 升级漂移；优先“调用原方法＋最小参数扩展”。

### M3 基网格/VAT 构建（可能无需 Mixin）

- `BaseMesh`、`InstancedGeometry`、`InstancedJob`、`PhotonInstanceRing` 的构造器/方法均为 public（已验证）；可以直接用 `RenderSystem` 创建 `GpuBuffer`。
- 若需要原生 `PhotonMesh` 的打包逻辑或 `TileParticleRenderer.modelMeshBuffer` 的上传路径，再加 `@Invoker`/`@Accessor`；否则不引入 Mixin。
- VAT：`VertexAnimationBake.bake(SkinnedModel, AnimationClip, int)` 面向 GLTF；自定义模型需按同布局自行烘焙（可复用我们的采样器）。

### M4 Iris 顶点格式路由（中高风险，收益待测）

- 目标 A：`com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory`（已验证存在 `buildPipeline/vertexFormat` 等；警告文本在该类）。
  注入：在创建管线/格式时把 Photon 实例化格式补齐为 ENTITY 兼容（切线、UV1/UV2），或按 shaderpack 期望重写格式。
- 目标 B：`com.lowdragmc.photon.client.render.PhotonPipelines#instancedFormat`（私有静态，已验证）＋ `instancedHdrParticle`；
  注入：`@ModifyReturnValue`/`@Inject` 追加属性与 define，供 graph 着色器读取。
- 风险：shaderpack 的 ENTITY 程序需要完整属性与正确的语义；补属性不等于视觉正确。必须开/关 shaderpack、同场景截图对照，并记录 GPU/CPU。

### M5 首次编译预热（收益和生命周期待证实）

- 目标：KilaGraph `RenderTypeFactory#getOrBuildPipeline`（原报告所属类标注错误）／Photon `PhotonPipelines#instancedHdrParticle`或 `PhotonRenderTypes#createGraphShader`。
- 注入：资源重载后/首次创建材质时，预创建光束会用到的 pipeline variants（在加载阶段，不在测量帧）。
- 或者不改 Photon：在试验 SETTLE 阶段做一次离屏预热绘制。后者更简单，但不能改善真实玩家首次施放；需要产品决策。

### M6 原生光束参数扩展（仅方案 2 需要）

- 目标：`BeamConfig`/`BeamEmitter`。
- 目的：若要把现有模型外观映射到 quad 光束，需要宽度曲线、分段、纹理滚动、端点形状等参数；先确认现有字段能否满足，不足再 Mixin 扩展。

### M7 参数与命名动画（本仓库类，不需要 Mixin）

- 目标：`PhotonNamedRuntime#tick/frame`、`PhotonNamedMixer#begin/add/commit`、`PhotonModelBindings`。
- 本轮范围：只将模型部件／骨骼／Morph 的路径查找预解析为实例槽引用，延迟创建空结果 Map；不缓存时间函数结果、不跳过 install、不把可写数组冒充不可变帧快照。命名 mixer 的 release／install 与原生基础值契约维持原样。
- 若将来要改 Photon 原生 `RuntimeValue/RuntimeBinding` 的每帧解析，再考虑用 Mixin 缓存 slot 索引；当前样本 2.0%，优先级低于命名动画。

### M8 GPU 蒙皮扩展（高风险，仅在 VAT 不足时）

- 目标：`PhotonPipelines#instancedFormat`／`instancedHdrParticle` 追加骨索引/权重属性；`PhotonRenderTypes#createGraphShader` 或自定义 shader 采样骨骼矩阵纹理；`Emitter.bakeInstancedGroup` 增加骨骼纹理绑定。
- 先定义现有连续动画、动态覆盖、法线与排序的等价约束；TRS／VAT 任一不满足都不能默认替换。GPU 蒙皮也需证明相同数值与资源生命周期，不以先上 VAT 作为默认顺序。

### M9 参数传输原生侧（低优先）

- 目标：`PhotonMaterialUniforms#sliceFor/upload`、`AdditionalGPUDataSetting#planAttribs/uploadChannel`。
- 只在参数密集型特效上 A/B：确认每帧 uniform／通道上传是瓶颈后再做；当前激光样本中 `PhotonMaterialUniforms` 仅 0.4%，不应先动。

### M10 不建议的 Mixin

- 全局缓存所有 DrawJob、按内容哈希跨实例共享 GPU 缓冲、强制关闭阴影/降 LOD/降分辨率：会破坏排序、所有权或画质语义，不能用“性能”名义做默认行为。
- 直接改写 `PhotonBufferCache` 做“相同字节不重传”：实际相机／物体变换变化时字节可能不同；相机并非每帧必变，命中率及收益须量测，不能直接给定。

## 七、实施顺序与验收门槛

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0 | 补 draw call、shadow pass、pipeline 编译计时；固定相机/seed；保留本报告的光影/无光影配对基线 | 能解释每次施放窗口的 CPU/GPU/draw/shadow 构成 |
| P1 | 方案 1 最小 spike：单部件、单组、`MODEL` 实例化，不做 VAT | 视觉逐像素/截图对照通过；CPU 每帧顶点展开消失；GPU 不退化 |
| P2 | 若 P1 通过：多组材质、多部件实例、`MODEL_TANGENT`；必要时再做 VAT | 施放窗口 CPU 增量 ≤1 ms；P99 不退化 |
| P2b | 参数与命名动画：预编译计划、去 Map、只重算变化 | 参数密集 vs 静态 A/B；每帧求值次数、分配、上传字节 |
| P2c | 动画 GPU 化：TRS → VAT →（必要时）GPU 蒙皮 | 逐顶点/截图对照；VAT 内存与更新率预算 |
| P3 | M1 阴影门控（先 A/B 估算光束阴影占比） | 光影下 CPU/GPU 有可测下降且视觉一致 |
| P3b | M8/M9 参数与蒙皮扩展（仅证实瓶颈后） | A/B 证明收益且不退化才保留 |
| P4 | M4 Iris 路由 A/B | 只有对照证明不退化才保留 |
| P5 | M5 预热 | 首发长帧下降且没有转移为进世界卡顿 |
| P6 | 方案 2 评估（若允许资产调整） | 视觉与成本同时达标才替换 |

目标值（建议作为门槛，不是承诺）：光影施放窗口均值从 20～25 ms 降到 ≤12 ms（空场景 9.3 ms 的合理增量）；无光影施放窗口从约 6.3 ms 降到 ≤3 ms；首发从 717／981 ms 降到 ≤250 ms；命名与参数求值应先建立互斥计时和调用次数基线，不使用叠加 inclusive 的约 13% 作为门槛；不降低画质、不关闭阴影作为默认优化。

## 八、当前问题入口

本节不再维护平行问题列表：模型实例化的法线／排序／动态覆盖等价性归 P11，动画与绑定求值归 P12，首发成本归 P06，可比性归 P14，资源生命周期归 S23。当前状态、后续工作及关闭条件统一见 [唯一问题清单](../issues.md)。原始候选问题保留在修改前备份；下面的本轮审查与实测是证据记录。

## 九、反复复核与本轮实际执行（2026-10-24）

### 复核结论与实施边界

1. **证据口径复核**：原报告 1044 样本及各百分比是 DS 当轮 inclusive 栈统计，本轮未独立重算其完整窗口；不能叠加成命名／参数“13%”，不能当互斥 CPU 或 GPU 时间，也未隔离女皇、世界和光束。预计 +0.5 ms、单 draw 及数量级收益没有原型证据，不能承诺。
2. **接口复核**：IDEA 当前 Photon 26.1.2.3 源码 Emitter:561 的 bakeInstancedGroup 为 protected final，已有 renderer／setting／variant／BaseMesh／InstanceFiller 参数，子类可调用，无需为现有能力复制 staging／ring 编排。AdditionalGPUDataSetting 被多处解引用，不能只允许 null。符号搜索还确认 getOrBuildPipeline 属于 KilaGraph RenderTypeFactory，原 M5 标为 PhotonPipelines 的目标不准确。
3. **渲染语义复核**：particle.glsl:306 的模型实例法线为 normalize(rotMat * photonNormal)，有意忽略非均匀缩放；当前 CPU 路径为逆转置并保留奇异回退。ObjectPosition／ObjectNormal 的语义也与 CPU 已变换顶点不同，可能影响 graph。Emitter:766 的 CPU sortQuads 排原语，instanceDrawOrder 排整实例；不能默认视为相同透明顺序。drain 已合并相邻同 RenderType／mask Job，实例化 helper 仍按 emitter／材质生成 Job。
4. **动画与所有权复核**：原生 VAT 每顶点每帧为 RGBA32F，w 打包量化法线，按有限帧采样；不能直接等价任意连续时间、动态骨骼／Morph／覆盖输入。父子非均匀缩放还可能含剪切，不能简单分解为一个 TRS。GPU 资源必须覆盖重载、设备代次、延迟消费与释放，public 构造器不等于生命周期可直接照搬。
5. **绑定与混合复核**：RuntimeValue.clear 仅清值，不替换槽，适合预解析引用；时间函数即使对象相同仍须重新求值。PhotonNamedMixer.begin 每轮 release 后再 install，不能凭静态参数相等跳过覆盖安装。

因此，本轮**不实施 M1／M2／M3／M4／M5／M8 的渲染、VAT、阴影、格式或预热变更**；保留动画、LOD、资源、shader、排序和原 CPU 渲染路径。方案 2 的资产替换也不在范围内。外部 Niagara／VAT／GeckoLib 文档只是设计参考，不能作为本项目运行等价或收益证明；本轮未跨工作区修改或采用 horde 的验收结论。

### 已实施与正确性检查

仅实施 M7 的一个局部部分：PhotonModelBindings 在构造期按原顺序准备 part／bone／Morph 的 RuntimeValue 引用，避免每帧拼接路径和查槽；没有覆盖时不创建临时 LinkedHashMap。有覆盖时仍返回新的不可变 Map，保持逐帧时间函数调用、position→rotation→scale 顺序、随机 supplier 与原槽身份，不缓存求值结果、不改 mixer。

普通 JUnit 首次运行三项均在 NumberFunction 静态初始化失败：ModList.get() 为 null，**没有进入断言，不算通过**。原失败 XML 与测试源码已归档；该不适用的临时 JUnit 文件未保留在测试集。改为显式开发客户端校验 PhotonModelBindingsValidation，在既有夹具 VERIFY 阶段、90 秒测量及截图之后运行，只有 LOBECORP_LASER_PROFILE_BINDINGS_CHECK=true 才调用。它不提前预热、推进真实技能或创建游戏内特效。

优化后及重复轮均记录 bindings_validation_passed_3，三项检查各通过：部分 TRS／四元数／clear 与槽身份，连续时间与分量顺序，Morph／实例隔离与旧不可变快照。校验不是全部资源或视觉矩阵验收，仍不声称逐像素等价。

三份 Java 最终 IDEA 0 错误，夹具保留 verifyRuntimeCopy／renderedPose 两项既有未使用提示；原 LF／CRLF 分别保持。IDEA 编译及 Data 成功，最终 Data 35 秒、23 项 written=0／removed=0。用户叫停后只读取静态诊断与文档，不再安排编译／Data／测试／游戏。原有配置及两份资源验收测试改动继续保留。

### 实际前后与重复数据

全部下表采集通过既有 Client、Sodium 0.9.2、固定 19877 MCP；无客户端 spark／JFR／Debug。每轮 90 秒／三次 STARTED，阶段 0→1→2→移除齐全；全帧 flags=3、throttle=NONE、2560×1494。日志均确认未选有效 shaderpack，因此本轮不提供光影优化验收。窗口选取沿用当前 HEAD，可能包含非输出时段；粒子 seed、实际相机／投影和外部主机负载未完全控制。

| 轮次 | 帧数 | 全程均值／P99／最长 ms | 空基线均值 ms |
|---|---:|---|---:|
| 原实现 before（生产基线 4857591b） | 26030 | 3.458／31.807／982.405 | 2.675 |
| 候选 after | 27054 | 3.327／32.075／1066.151 | 3.279 |
| 同候选 repeat | 5771 | 15.597／67.792／785.504 | 14.011 |
| 同候选误标 reference（不是原实现） | 43093 | 2.089／19.033／750.564 | 未在本轮报告重算 |

| 施放附近窗口 | 原实现均值／P99／最长 ms | 候选 after | 候选 repeat |
|---|---|---|---|
| 4～16 秒 | 18.177／44.768／982.405 | 11.606／37.420／1066.151 | 48.406／572.485／785.504 |
| 31～43 秒 | 15.001／37.566／132.237 | 16.689／56.508／419.060 | 40.534／60.124／99.898 |
| 58～70 秒 | 21.355／48.513／129.533 | 20.905／44.849／134.092 | 51.456／99.017／144.481 |

不能宣称稳定收益：after 的 P99／最大值未改善，repeat 连空基线也明显变慢，而后续同候选又变快。GPU 有效样本均值从 before 2.078、after 1.395 到 repeat 7.672 ms，范围为 GameRenderer.render，不能与整帧简单相加或归给绑定查槽；缺测跳过仍保留。代码原因和外部调度／GPU 负载未分离，不据此断言一定是环境干扰，也不关闭 P06／P11／P12／P14。

原实现回切的第一次 IDEA 补丁未匹配，导致 reference 标签仍运行候选；已独立标为 candidate-mislabeled，不作为原实现基线。随后通过 IDEA 局部恢复并核对原始字节 SHA256=5fbf5656eae63ea6983494fb5790c0161b806eee4fb1a0229d1676a6f451daad 后启动 verified reference。用户随后明确不再需要游戏测试：只做正常保存关闭；该夹具在关闭前自动完成的日志与输出另行保留，**不纳入新的验收或收益比较**。最终源码已恢复为上述完成静态检查的 M7 候选，没有继续游戏测试。

### 归档、清理与后续限制

备份根：C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-render-review-20261024。原文／源码原件、before／after／repeat、candidate-mislabeled、stopped-reference、比较 JSON、JUnit 初始化失败证据、各 IDEA 结束日志和桥接 ledger 分开保留。单次 nvidia-smi 只在测量外读取到 55℃／1425 MHz／GPU 13%，不构成测量期间未降频或无其他负载的证明。

所有本轮客户端经 MCP 正常保存退出并退出程序，桥接均退出 0；没有新增 Debug 断点，不关闭未知进程，不伪造未取得的游戏 PID／启动时刻。后续按用户最新指令不再进行游戏测试。当前性能与迁移缺口只在唯一问题清单维护；本轮已实现的槽预解析不等于完整 M7、GPU 化或激光性能问题已解决。
