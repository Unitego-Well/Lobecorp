# Photon 渲染架构优化报告（含 Mixin 方案）

编写时间：2026-10-24（北京时间）。基线 `6ae3093c`（法线缓存 `bd29ff62`＋内嵌模型准备复用 `fba6713a`）。**本报告只做设计与验证计划，没有修改代码**。

依据：[真实激光架构级诊断与优化方案](photon-laser-architecture-20261024.md)、[激光测试报告](photon-laser-optimization-20261024.md)、[性能审查](photon-performance-audit.md)，以及 Photon 26.1.2.3 的 jar 字节码与着色器（`assets/photon/shaders`）核对。当前问题状态仍以 [唯一问题表](../issues.md) 为准。

## 一、结论先行

1. **继续优化 `write`／法线矩阵／内容缓存不会改变量级。** 光影施放窗口 1044 个渲染样本中，Photon `write` 只占 3.0%，`sample` 8.8%，而 Iris 相关 36.8%（阴影 31.5%）。微优化命中的是一小块 CPU 顶点数学，不是主要成本。
2. **真正的架构问题是：光束被当作“粒子模型即时提交”在每帧展开。** [PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L431-L452) 每帧把三角形展开成 4 角 QUADS 写进 `VertexConsumer`，原生 `PhotonWorldRenderState.drain` 再消费上传；没有持久网格、没有实例化、没有 GPU 形变。
3. **Photon 本身已经有两套更合适的原生路径，只是没有用上：**
   - **实例化模型**：`InstancedVariant.MODEL / MODEL_TANGENT / MODEL_VAT*`，基网格只上传一次，每实例只传 `iPos / iScale / iRot / iColor / iLight`，顶点着色器计算 `world = R · (aPos · iScale) + iPos`；`MODEL_VAT` 用一张按顶点×帧烘焙的纹理把动画也放到 GPU（`VertexAnimationBake`）。
   - **实例化光束**：`BeamEmitter` 在 `renderer.isUseGPUInstance()` 为真时直接走 `bakeInstancedGroup(..., InstancedVariant.BEAM, PhotonWorldRenderState.beamQuad(), ...)`；每实例只传 `iStart(含半宽) / iEnd / iColor / iUV / iLight`，一个 draw 绘制全部光束实例。
4. **Mixin 应该用在“把我们的模型接入原生实例化路径”和“光影边界”上，而不是继续改顶点循环。** 具体目标见第五节；其中相当一部分可直接用 Photon 的公开 API 完成，Mixin 只补齐私有编排与光影路由。

## 二、当前成本结构（实测回顾）

| 场景 | 空场景 | 施放窗口 | 首发最长 | 备注 |
|---|---:|---:|---:|---|
| 无光影（12:55） | 1.843 ms | 6.275／3.944／3.953 ms | 981.357 ms | CPU 均值 1.114、GPU 均值 0.642 |
| 光影（12:34） | 9.320 ms | 25.548／19.693／20.491 ms | 717.173 ms | CPU 均值 3.605、GPU 均值 11.581 |

光影施放窗口 JFR：Iris 36.8%（阴影 31.5%）、Minecraft 渲染 68.7%、`PhotonModelOutput` 15.1%、`WorldRenderState drain` 13.5%、`sample` 8.8%、`write` 3.0%、`BufferBuilder` 6.1%。首发长帧的 CPU 与 GPU 同时抬升（例如 555／633 ms），符合“首次管线／纹理／准备”而非单一方法。

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

### 方案 1：模型接入原生实例化路径（首选，保留现有外观）

把 `PhotonModelOutput` 的每帧 QUADS 提交替换为：

1. 准备阶段：从 `PhotonModelTopologyUtil.Prepared` 构建 **持久基网格**（每材质组一份顶点/索引 `GpuBuffer`），材质组继续用现有 `PhotonModelMaterials.group(i)`。
2. 每帧：只计算每个“部件实例”的 `iPos/iScale/iRot/iColor/iLight`，调用原生 `bakeInstancedGroup`（`MODEL` 或 `MODEL_TANGENT`）。模型若只是部件 TRS／部件覆盖变化，这一步就足够；`write` 与逐顶点变换全部消失。
3. 若动画包含顶点级形变（骨骼／morph）：用我们的采样器离线烘焙 `VAT`（顶点×帧），改用 `MODEL_VAT`；每帧仍只传实例参数与时间。原生 `VertexAnimationBake` 面向 GLTF `SkinnedModel`，自定义模型需要按同一 texel 布局自行烘焙（可先用我们的 `PhotonModelPoseSampler` 逐帧生成位置/法线）。
4. 首个实例的基网格上传、材质/管线准备放到准备阶段；测量窗口只保留实例填充与小上传。

预期：CPU 每帧顶点展开降到“每实例几十个 float”，无光影光束 CPU 增量从约 +2.55 ms 降到 +0.5 ms 量级；光影下 Iris 逐顶点钩子与切线计算的调用量同步下降。GPU 顶点数不变，但 draw 数由“组×实例”变为“组（实例化）”。

风险：实例 transform 是否足以表达当前模型动画（若含逐顶点形变则必须 VAT）；材质组与实例的对应关系；半透明排序仍由原生 drain 决定，需要逐项对照。

### 方案 2：原生实例化光束（若允许调整光束资产）

直接用 `BeamEmitter`＋`useGPUInstance=true` 表达光束核心（可叠层：核心束、辉光、噪波），每束一个 quad 实例，一个 draw。现有模型只保留蓄力／星beam等非光束部分。

预期：CPU/GPU 都接近“一个 quad”的成本；资产与准备也显著变轻。风险：外观与现有模型差异需要美术对照，属于资产/视觉变更，不是纯性能改动。

### 方案 3：光影阴影与 Iris 路由（与方案 1/2 并行）

- **阴影**：光束是半透明加色效果，通常不应投射阴影。用 `IrisCompat.isShadowPass()` 在光束的 `bakeBatches` 中跳过（我们的输出可直接改；原生 `BeamEmitter` 需要 Mixin）。先做 A/B 确认 31.5% 里有多少是光束阴影。
- **Iris 路由**：把光束/实例化网格的顶点格式补齐到 shaderpack 期待的 ENTITY 布局（切线、UV1/UV2），使 `RenderTypeFactory` 不再绕开 shaderpack。收益不确定：进入 shaderpack 管线可能更重，也可能因统一管线减少状态切换；必须 A/B，不能假设一定更快。
- **材质合并**：把同一光束的多层材质合并为 1～2 个 draw；减少 overdraw 与状态切换。

### 方案 4：首帧预热

把基网格上传、VAT 上传、材质/管线编译、纹理上传移到 SETTLE 之前（或首次进入事件前）。预热成本单独报告；不能把成本静默转移到进世界或技能前摇。

## 五、Mixin 补丁设计（只给目标与注入点，不含实现）

> 标注“已验证”的类/方法是本轮从 jar 或源码核对过的；“待 spike”表示需要最小原型确认签名与行为。

### M1 光影阴影门控（低风险，先做）

- 目标（原生光束）：`com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter#bakeBatches(PhotonViewSettings, List, Camera, float)`；
  注入：`@Inject(method="bakeBatches", at=@At("HEAD"), cancellable=true)`；
  条件：`IrisCompat.isShadowPass()` 且该发射器未标记“投射阴影” → `ci.cancel()`。
- 我们的模型输出无需 Mixin：在 `PhotonModelOutput#bakeBatches` 开头用同一条件返回即可。
- 风险：若某些光束阴影是刻意效果，需要按发射器开关；先 A/B 验证视觉与收益。

### M2 原生实例化模型的“桥”

- 目标：`com.lowdragmc.photon.client.gameobject.emitter.Emitter`。
- 目的：原生 `bakeInstancedGroup` 从 `RendererSetting.Runtime.getMaterials()` 取材质、用私有 `staging/instanceDrawOrder/ringFor` 组织实例缓冲，无法直接传入“自定义网格 + 自定义实例填充 + 指定组材质”。
- 注入：`@Unique` 新增 `lobecorp$bakeInstancedModel(...)`（委托或复制原生编排，内部可调用 `@Invoker` 暴露的私有 helper），或 `@Invoker`＋我们自己的编排。
- 待 spike：`AdditionalGPUDataSetting` 在实例填充前被调用 `planAttribs/attribFloats`（字节码可见），需要提供最小实现或 Mixin 允许 null。
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

### M5 首次编译预热（低风险）

- 目标：`PhotonPipelines#getOrBuildPipeline`/`instancedHdrParticle`（已验证）或 `PhotonRenderTypes#createGraphShader`。
- 注入：资源重载后/首次创建材质时，预创建光束会用到的 pipeline variants（在加载阶段，不在测量帧）。
- 或者不改 Photon：在试验 SETTLE 阶段做一次离屏预热绘制。后者更简单，但不能改善真实玩家首次施放；需要产品决策。

### M6 原生光束参数扩展（仅方案 2 需要）

- 目标：`BeamConfig`/`BeamEmitter`。
- 目的：若要把现有模型外观映射到 quad 光束，需要宽度曲线、分段、纹理滚动、端点形状等参数；先确认现有字段能否满足，不足再 Mixin 扩展。

### M7 不建议的 Mixin

- 全局缓存所有 DrawJob、按内容哈希跨实例共享 GPU 缓冲、强制关闭阴影/降 LOD/降分辨率：会破坏排序、所有权或画质语义，不能用“性能”名义做默认行为。
- 直接改写 `PhotonBufferCache` 做“相同字节不重传”：相机每帧变化时字节不同，命中率低，收益有限。

## 六、实施顺序与验收门槛

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0 | 补 draw call、shadow pass、pipeline 编译计时；固定相机/seed；保留本报告的光影/无光影配对基线 | 能解释每次施放窗口的 CPU/GPU/draw/shadow 构成 |
| P1 | 方案 1 最小 spike：单部件、单组、`MODEL` 实例化，不做 VAT | 视觉逐像素/截图对照通过；CPU 每帧顶点展开消失；GPU 不退化 |
| P2 | 若 P1 通过：多组材质、多部件实例、`MODEL_TANGENT`；必要时再做 VAT | 施放窗口 CPU 增量 ≤1 ms；P99 不退化 |
| P3 | M1 阴影门控（先 A/B 估算光束阴影占比） | 光影下 CPU/GPU 有可测下降且视觉一致 |
| P4 | M4 Iris 路由 A/B | 只有对照证明不退化才保留 |
| P5 | M5 预热 | 首发长帧下降且没有转移为进世界卡顿 |
| P6 | 方案 2 评估（若允许资产调整） | 视觉与成本同时达标才替换 |

目标值（建议作为门槛，不是承诺）：光影施放窗口均值从 20～25 ms 降到 ≤12 ms（空场景 9.3 ms 的合理增量）；无光影施放窗口从约 6.3 ms 降到 ≤3 ms；首发从 717／981 ms 降到 ≤250 ms；不降低画质、不关闭阴影作为默认优化。

## 七、未决问题

- 激光模型动画是否包含顶点级形变（决定是否必须 VAT），需要读取/采样该 clip 的顶点位移范围；本报告未完成该项。
- 实例化模型路径对半透明排序、材质组、部件覆盖的兼容性需要 P1 spike 验证。
- 光影 31.5% 阴影样本中有多少来自光束本身，需要 M1 A/B 或 shadow pass 单独计时才能定量。
- KilaGraph ENTITY 路由补齐后，shaderpack 是否真的更快未知，必须 A/B。
- 本报告没有实现任何补丁；Mixin 目标以 Photon 26.1.2.3 为准，升级 Photon 后需重新核对。
