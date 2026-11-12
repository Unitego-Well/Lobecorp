# 特效运行时底层重构方案（EffectAsset / EffectInstance）

编写：2026-10-26 12:10。范围：`org.unitego.lobecorp.client.photon.*` 与必要的 Photon 适配层。本文是**架构重写方案**，不是对现有实现的微调；实施按阶段进行，每阶段保留开关与回退，并以同条件 A/B、逐顶点属性对照和同帧截图作为出口。

## 1. 为什么必须从底层改（证据）

| 证据 | 说明 |
|---|---|
| 首次释放 tick 695.7 ms | `fxDefinition` 167.6 + `createRuntime` 53.0 + `childDefinition` 270.9 + `emit` 71.8 + `namedBegin` 15.2 + `prepareNext` 116.0（`first-cast/run1` CASTPROF）。 |
| 每次释放仍有 187～232 ms | 92 每轮施法与 `IFXObject.deepCopy`／`mc.CODEC` 同窗（JFR 窗口 27%～38% Codec、16%～26% deepCopy）；对应 `FX.createRuntime(true) → FXData.copy(true)` 的整棵树序列化往返。 |
| 首帧渲染侧还有 1.0～1.4 s | run5b 长帧 545.9/424.8/307.4 ms；同窗口为 Mixin 类转换/类加载、ShaderGraph GLSL `#import` 资源探测异常（每窗口 18～22 对）、JSON 节点图解析与 OGG 解码。 |
| 子实例链路同样深拷贝 | `PhotonChildSpawner.prepareNext → PhotonSpawnTemplateUtil.create` 每次准备/发射复制子树（首次 116 ms）；子发射器在连发/多束时反复调用。 |
| 静态几何缓存被相机绑定 | `GeometrySnapshot.matches` 的 `world` 含 `-renderOrigin`，相机一移动即失效，整块提交只在固定相机 Bench 命中。 |

根因不是某个函数慢，而是**当前运行时把“不可变的美术内容”和“一次播放的可变状态”绑在同一个对象树里**：播放一次就必须整棵复制，复制后材质/管线/动画/类加载都跟着重建。

## 2. 目标分层

```
EffectAsset（每份内容编译一次，不可变、可共享）
├─ EffectGraph       作者对象树 + 父子关系 + 可见性/层
├─ TimelineTracks    编译后的轨道数组（时间、片段、事件、信号）
├─ ClipData          曲线/骨骼/形变轨道，预采样数组
├─ MeshData          拓扑/UV/法线/材质槽（本机字节序，可直接上传）
├─ MaterialVariant   每个（材质, 模式, 混合/深度/模板变体）的 pipeline 句柄（加载/预热期构建）
├─ SpawnerTemplate   子 FX 的“定义+路径+覆盖”编译结果，可多实例复用
└─ AudioBank         该特效引用的音频解码缓存

EffectInstance（池化，一次播放的可变状态）
├─ TimelineCursor    时间、循环、rate、暂停
├─ TransformState    每对象 TRS（骨骼/部件覆盖的最终值）
├─ EmitterState      粒子池、发射器游标、随机数
├─ NamedState        命名播放句柄/混音器/事件队列
└─ SpawnerState      已准备子实例槽位、发射队列

EffectPlayer / RenderBridge
├─ 按材质/通道合批；透明排序键在实例创建时确定，复用不重排
└─ 每帧只做“状态→提交”，不做内容解析/拷贝/材质创建
```

契约：
- **Asset 不可变**：对象树、轨道、材质、网格、音频发布后只读；运行期修改走“新 revision 的 Asset”，旧实例帧末切换。
- **Instance 不持有 Asset 的可变别名**：只持有 asset id + revision + 池化状态缓冲。
- **GL 资源随 Asset 生命周期**：材质/管线/纹理/网格在预热或加载期建好；Instance 不触发 compile/link/upload。
- **复用必须显式 reset**：`timelineCursor.reset()`、`emitters.reset(seed)`、`named.reset()`、`spawner.reset()`；被延迟 drain 引用的缓冲不得与复用实例别名（generation 校验）。
- **线程边界**：Asset 编译可异步（禁止 GL）；GL 提交在主线程；Instance 状态只在客户端主线程。
- **失效规则**：资源重载/包切换 → 全局 revision 递增 → Asset 重建；相机/位置变化不使 Asset 失效。

## 3. 迁移阶段

| 阶段 | 内容 | 出口 | 风险 |
|---|---|---|---|
| R0 | 单施法夹具与诊断（已完成） | 基线；已 695.7→64.4 ms（预热临时方案） | 低 |
| R1 | **运行态复用**：`EffectInstance` 池 + `destroy→re-emit`；`QueenBeamEffects` 不再每施法 `createRuntime(true)` | 后续每次施法 tick 190～250→≤80 ms；无残留粒子/时间线偏移 | 中：reset、命名运行时/子实例所有权 |
| R2 | **Asset 编译 + 材质/管线预构建**：FX/子 FX 编译一次；`RenderTypeFactory` 结果按（asset, variant）缓存；GLSL import 解析 memoize | 首次施法首帧 ≤50 ms，无 GLSL import 风暴 | 中高：Photon/KilaGraph 适配、热重载失效 |
| R3 | **SpawnerTemplate/Instance 分离**：子 FX 只编译一次；多槽 prepared；发射只复制状态 | 连发/多束时子模板创建≈0（仅首次） | 中：随机数/路径/作用域 |
| R4 | **静态几何资产空间缓存**：快照不随相机失效，按实例变换提交 | 相机移动仍命中；透明排序不变 | 中：ObjectPosition/法线/光照等价 |
| R5 | **批处理/排序** | draw/状态切换下降 | 中 |
| R6 | **可选 GPU 路径**（VAT/实例化，CPU 回退） | 等价且有收益才启用 | 高 |

每阶段：开关默认关闭/可回退、单施法＋混合场景 A/B、逐顶点属性签名＋同帧截图、文档记录收益与退化。

## 4. 等价性契约

1. 透明排序：同一实例内部顺序与跨实例排序键一致；合批不改变同深度交叠顺序。
2. 法线/光照：位置与法线同一矩阵语义；ObjectPosition/相机偏移不改变明暗。
3. 动画时序：partial tick、循环、片段混合、命名播放优先级与事件顺序不变；复用时 timeline 起点与新建实例一致。
4. 材质变体：混合/深度/模板/bloom/UV/双面语义不变；变体键覆盖旧实现全部组合。
5. LOD/资源：不改 LOD、不换资源、不降采样率或粒子数换数字。
6. 随机性：子实例随机数来源与序列一致；复用实例重新播种。
7. 所有权：实例借用期唯一 owner；延迟 drain 用 generation 校验。

## 5. 与当前中间态

- `QueenBeamEffects` 的首次释放预热（提交 `3f41ff79`）是 R1/R2 的临时收窄版：把首次定义解析/深拷贝/子实例准备移到女皇出现时；后续施法与首帧材质仍存。R1/R2 落地后可删除或改为内部实现。
- 采样器分配治理（提交 `37f70cc2`）属于“状态→提交”的局部收益；R4 进一步改为资产空间缓存。

## 6. 待决策

1. R1 与 R2 先后：建议 **R1 先行**（不动 GL/库，先消除每施法深拷贝并验证 reset），再用 R2 解决首帧材质/类加载。
2. Photon/KilaGraph 适配：优先在原库加公开缓存/生命周期钩子（若可改依赖），否则窄 Mixin；不复制库实现。
3. 编辑器/热重载的 revision 传播与旧实例处置时机。

## 7. 当前状态

- 已完成：R0 夹具/诊断；首次释放预热（临时替代）。
- 未开始：R1 实例池、R2 Asset/材质预构建、R3 spawner 分离、R4 几何缓存、R5 批处理、R6 GPU。
- 数据入口：`docs/records/photon/photon-performance-audit.md#首次释放专项诊断进行中2026-10-26-1140-起`、`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026/first-cast`。
- 执行手册：`docs/design/photon/photon-effect-rewrite-execution-plan-20261026.md`（R1～R6 步骤、代码地图、测试命令、验收与恢复清单）。
