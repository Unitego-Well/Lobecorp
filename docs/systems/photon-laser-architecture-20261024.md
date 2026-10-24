# 真实激光架构级诊断与优化方案（含光影）

测试时间：2026-10-24（北京时间）。生产代码基线 `6ae3093c`（含法线缓存 `bd29ff62` 与内嵌模型准备复用 `fba6713a`）。本轮没有修改业务代码、动画、LOD、资源或技能时序；只做真实激光、光影对照、JFR 归因和架构评估。

相关文档：[激光测试与优化方案](photon-laser-optimization-20261024.md)（无光影轮）、[测试后优化建议与问题原因](photon-optimization-posttest-20261024.md)、[Photon 渲染架构优化报告（含 Mixin 方案）](photon-render-architecture-mixin-20261024.md)、[性能审查](photon-performance-audit.md)、[唯一问题表](../issues.md)。

## 一、原始诊断与后续审查边界

法线缓存属于微优化：它只去掉每个 group 每帧一次 `Matrix4f.normal()`。本轮证据表明，真实激光的主要成本不在这一层：

| 观测 | 无光影（12:55，同代码配对） | 光影（12:34，ComplementaryReimagined） |
|---|---:|---:|
| 空场景均值 | 1.843 ms | **9.320 ms** |
| 首次施放窗口均值 | 6.275 ms | **25.548 ms** |
| 第二次／第三次窗口均值 | 3.944／3.953 ms | **19.693／20.491 ms** |
| 首次施放最长帧 | 981.357 ms | **717.173 ms** |
| 光束相对空场景增加的 CPU | 约 +2.55 ms／帧 | 约 **+5.6 ms／帧** |
| 光束相对空场景增加的 GPU | 约 +1.32 ms／帧 | 约 **+3.5～6.5 ms／帧** |

整体：无光影 42028 帧，均值 2.141／P99 9.052／最长 981.357 ms；光影 7672 帧，均值 11.732／P99 28.108／最长 717.173 ms。11:32 另有一轮无光影（当时尚未含 `fba6713a` 内嵌模型准备复用）：均值 1.750、P99 8.125、最长 599.019，空场景 1.248、施放窗口 5.051／3.710／3.442。同代码两轮差异属运行波动（P14），两轮都保留，不取其中较好者作结论；配对表用 12:55 轮，因为它与光影轮同为 `6ae3093c`。

光影下渲染线程 JFR（施放窗口 1044 个样本）显示：Iris 相关栈 **36.8%**，其中 Iris 阴影 **31.5%**；Photon `write` 只有 **3.0%**，`PhotonModelOutput` 15.1%，`PhotonWorldRenderState` drain 13.5%，`PhotonModelPoseSampler.sample` 8.8%。也就是说：

- 光束当前由 CPU 展开成 QUADS；Iris 栈命中不能证明这些光束本身额外执行了阴影提交，须对齐实际对象／pass／任务计数。
- 法线缓存未改变几何数量和材质；其真实收益仍受可比性约束，不能用该组样本直接给出收益上限。
- 首次长帧窗口与同步准备及首次使用重叠；编译／纹理／GC／JIT／驱动等待未分离，不能把全部时间确诊给管线编译。

**现有数据尚不能证明架构替换必有数量级收益，或局部优化必定无效。** 后续 [Mixin 方案复核](photon-render-architecture-mixin-20261024.md) 纠正了法线／透明排序不等价、protected API 可直接调用、inclusive 比例不可相加等前提；不跳过阴影、不改资产或动画后端。当前问题只在唯一清单维护。

## 二、工具链与运行账本

| 运行 | 入口 | 结果 |
|---|---|---|
| BenchMod 合成基线 | `BenchMod Photon`，Sodium，无 Debug | PASSED，`environment.valid=true`，8 场景通过 |
| 真实激光（无光影） | `Client`＋QueenBeamPerformanceTrial，label `laser_mcp_20261024_1132`，单次采集，MCP 19877 | DURATION，51432 帧；MCP 取得 pong、玩家位姿与截图；正常保存退出 |
| 真实激光（光影） | 同入口，Iris＋`ComplementaryReimagined_r5.9.3`，label `laser_shader_20261024_1234` | DURATION，7672 帧；三次施放 STARTED 且 0→1→2→移除齐全；MCP 截图确认光影生效 |
| 真实激光（光影＋JFR） | 同入口，`JAVA_TOOL_OPTIONS=-XX:StartFlightRecording=…`，label `laser_shader_jfr_20261024_1245` | DURATION，8439 帧；JFR 用于方法级归因，计时只作参考 |
| Debug 路径核对 | 同入口＋Debug | CHARGE 阶段同步 `prepareNext → PhotonNamedRuntime → PhotonModelOutput.prepare`（`laser_body`，clip=`beam`）；NormalCache miss 后 hit |

光影包来源：photon 工作区 `run/client/shaderpacks` 原为空，本轮从 `lobecorp_horde` 工作区复制 `ComplementaryReimagined_r5.9.3.zip`（原始仓库 `Lobecorp/run/client/shaderpacks` 也有同一文件）。测试后已把 `run/client/config/iris.properties` 恢复为测试前内容（`enableShaders=true`、`shaderPack=` 空）；包文件保留以便复测。

证据路径：

- 无光影：`run/client/performance/laser_mcp_20261024_1132_frames-*.json`、`…_frames_marks_*.json`
- 光影：`run/client/performance/laser_shader_20261024_1234_frames-*.json`、`…_marks_*.json`
- 光影 JFR：`run/client/performance/laser_shader_jfr_20261024_1245_frames-*.json`、`build/modBench/laser-shader.jfr`
- 截图：`build/modBench/laser-mcp-shot.png`、`laser-shader-shot.png`、`laser-shader-jfr-shot.png`、`run/client/screenshots/*_verification.png`
- BenchMod：`build/modBench/raw-results/default/client/summary.json`

## 三、实测细节

窗口按当前 HEAD 时间划分，边界处 interval 可能属于前一帧；窗口包含技能外时段。

### 3.1 无光影（12:55，同代码配对）

| 窗口 | 均值 ms | P99 ms | 最长 ms | >50 ms |
|---|---:|---:|---:|---:|
| 0～4 s | 1.843 | 4.449 | 23.142 | 0 |
| 4～16 s | 6.275 | 18.832 | 981.357 | 5 |
| 31～43 s | 3.944 | 12.005 | 75.788 | 1 |
| 58～70 s | 3.953 | 11.850 | 79.603 | 1 |

首发长帧集中在 5.0～6.9 秒：981.357（CPU 61.043／GPU 62.055）、876.324（3.829／3.874）、767.484（873.336／873.179）、94.823（21.402／21.914）；超过 50 ms 的 7 帧合计约 2.931 秒。11:32 旧轮在同位置为 1.248／5.051／3.710／3.442 ms、最长 599.019 ms，差异保留，不用于配对。

### 3.2 光影

| 窗口 | 帧间隔 均值／P99／最长 | CPU 均值 | GPU 均值 | >50 ms |
|---|---:|---:|---:|---:|
| 0～4 s | 9.320／11.382／15.803 | 3.443 | 3.012 | 0 |
| 4～16 s | 25.548／46.076／717.173 | 9.048 | 9.457 | 5 |
| 31～43 s | 19.693／31.229／64.489 | 5.014 | 6.524 | 2 |
| 58～70 s | 20.491／30.307／60.230 | 4.878 | 6.791 | 2 |

光影下空场景已约 9.3 ms（约 107 FPS）；施放时约 20～25 ms（40～50 FPS）；首发仍有 717／625／566／512 ms 长帧。GPU 有效样本完成数少于启动数（2831／8439 的 JFR 轮），说明 GPU 队列经常来不及完成查询，是 GPU 侧承压的旁证，但不能把 pending 当作耗时。

### 3.3 施放阶段

- 光影首发 charge→output 1.786 s，后两次 0.956／0.963 s；首发仍比后续多约 0.8 秒。
- 无光影同代码轮首发 charge→output 0.882 s，后两次 0.951／0.954 s；11:32 旧轮为 1.483／0.952／0.952 s。无光影首发长帧主要出现在 output 之后（5.0～6.9 秒），与光影轮的 4.7～6.6 秒同类，说明首次绘制／管线／纹理成本在两种模式下都存在。
- 首发长帧集中在 4.7～6.6 秒，与准备、输出切换、首次管线／纹理使用重叠。

### 3.4 JFR（光影施放窗口）

渲染线程 1044 个执行样本的 inclusive 占比：Iris 36.8%（其中阴影 31.5%）、Minecraft 渲染 68.7%、PhotonModelOutput 15.1%、WorldRenderState drain 13.5%、PhotonModelPoseSampler.sample 8.8%、Photon write 3.0%、BufferBuilder 6.1%、VertexConsumer 11.6%、GeckoLib 12.2%、KilaGraph 1.6%。热点叶子包括 Iris 阴影视锥测试、Iris 逐顶点切线计算、BufferBuilder 的 Iris 钩子、Photon 采样与 `write`。JFR 轮均值 10.665 ms／最长 641.94 ms，与干净光影轮同量级，说明 JFR 开销没有改变结论方向。

## 四、架构现状（为什么结构上受限）

1. **光束走的是“粒子模型即时提交”路径。** [PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L431-L452) 每帧在 `bakeBatches` 里调用 [PhotonModelGeometryUtil.write](../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java#L21-L86)，把三角形展开成 4 角 QUADS 写进原生 `VertexConsumer`；原生在 `PhotonWorldRenderState.drain` 时消费并上传。没有持久网格、没有实例化、没有 GPU 侧形变。
2. **原生其实已有持久／实例化模型路径。** Photon 26.1.2.3 的 `TileParticleRenderer` 提供 `modelVertexBuffer`／`modelIndexBuffer`、`usesVertexAnimation`、`fillInstancesModel`、`AdditionalGPUDataSetting` 等入口（jar 内已确认）。同一引擎里存在“上传一次网格、按实例填参数”的能力，但自定义模型输出没有使用。
3. **本仓库已有成功参考。** `lobecorp_horde` 的 `HordeGpuRenderer` 是“常驻模型＋GPU 运动纹理＋实例化批次＋Iris 兼容管线”的实现，注释明确“每批只上传时间与编号区间，不上传逐单位变换”，并以 65536 实例／draw 分批。这正是激光光束需要迁移的形态。
4. **光影下光束没有走高效路径。** KilaGraph 对部分 graph 记录 `vertex format [position, color, uv0, uv2, normal]` 不满足 Iris ENTITY 格式，故这些 graph 不经 shaderpack，保持自带 shader；该警告并未证明光束参与 shadow pass，也不证明 shaderpack 路由更快；31.5% 是整类 Iris 阴影栈，不能当作光束独占成本。
5. **绘制粒度偏细。** 模型按 material group 拆分绘制，多个 FX 对象（蓄力、光束、粒子等）各自成批；没有按“同一材质一个 draw”或实例化合批。填充率（2560×1494、半透明叠加）在光影下进一步放大 GPU 成本。

## 五、架构级方案

### 方案 A：光束专用 GPU 渲染器（推荐主方向）

把“光束”从 Photon 粒子模型中拆出，做成独立渲染器：

- 拓扑（顶点／索引／UV／切线／UV2）只在内容加载时上传一次；
- 每帧只上传光束实例参数（起点、终点、宽度、相位、颜色、可见性），或在 GPU 中由参数纹理生成；
- 顶点着色器完成朝向、拉伸、相位与切线生成；片元着色器保留现有光束外观（渐变、加色、噪声），可由现有 KilaGraph graph 改写或直接 GLSL；
- 一次 draw per beam（必要时按材质 1～2 个），与 Iris ENTITY 程序兼容；光束作为半透明加色效果**不投射阴影**（或用极简代理），直接去掉阴影 pass 的重复成本；
- 首帧前完成 pipeline／纹理／网格预热。

预期：去掉每帧 CPU 顶点展开（无光影 CPU +2.3 ms、光影 +5.6 ms 的主要来源）、Iris 逐顶点与阴影 CPU 成本（36.8% 样本中的大部分），GPU 因单 draw、无阴影、几何精简而下降。风险：需要重写光束材质与视觉对照，编辑器预览和资源格式需要兼容层。

### 方案 B：先用原生实例化模型路径（渐进、代码量小）

不新写渲染器，改用 Photon 已存在的持久网格／实例化能力：

- 用 `TileParticleRenderer.modelMeshBuffer` 类路径或 `fillInstancesModel`／`AdditionalGPUDataSetting` 让同一 mesh 只上传一次；
- 光束的形变尽量放到实例参数或顶点动画（VAT），避免每帧 CPU 重展开；
- 保留现有材质、排序与编辑器语义，先验证视觉完全一致，再扩展。

适用条件：原生路径能表达“单条长光束”的连续形变与透明排序；若只能支持粒子式实例，需评估是否把光束拆成分段实例。风险低于方案 A，收益也可能小于 A。

### 方案 C：光影集成与阴影策略（与 A/B 并行）

- 为光束顶点格式补齐切线／UV2，使其路由到 Iris 的 ENTITY 程序（当前提示明确不满足格式）；
- 半透明加色光束默认不进入阴影 pass，或在阴影 pass 使用简化几何；
- 统一材质与 graph，减少 pipeline 切换与状态变更；
- 用 Iris 的 shadow distance／culling 对照，量化阴影 pass 的 CPU／GPU 占比。

本轮证据：Iris 阴影相关占施放窗口渲染样本 31.5%，且逐顶点切线计算出现在热点中；这两项是候选调查方向，未证明可无损回收。

### 方案 D：资产与绘制结构

- 统计并合并光束模型的 material group、层数与子实例（蓄力／光束／粒子）数量，目标是一条光束一个 draw；
- 检查半透明叠加层数与屏幕覆盖，减少 overdraw（在不改变观感的前提下合并近似层）；
- 对光束做视锥／屏幕空间裁剪与距离剔除，避免屏幕外仍提交。

### 方案 E：首帧预热

- 在 SETTLE／测量之前完成 laser_body 的内容准备、mesh 上传、pipeline／shader 编译与纹理上传；
- 预热成本单独报告，不能把首次成本静默转移到进世界；
- 目标：首发从 600～717 ms 降到可接受的首次显示延迟，且不影响技能时序。

### 方案 F：工具补齐

- 在试验夹具中记录 draw call 数、pipeline 切换、shadow pass 时间、GPU pipeline 编译时间；
- 真实激光期间常开 JFR（本轮已验证可行）；
- 固定相机／投影／粒子 seed，做光影开／关配对，避免把环境差异当收益。

## 六、建议顺序

1. **P0 工具与基线**：把 draw call、shadow pass、pipeline 编译计时接入试验；本轮的 JFR＋帧／GPU 数据作为基线。
2. **P1 方案 C 的阴影与 Iris 路由**：先调查光束实际是否重复提交阴影及格式语义；当前不去掉阴影、不补写 ENTITY 路由，收益与等价性均未证明。
3. **P2 方案 B 原型**：用原生实例化／持久网格只处理光束模型，验证视觉与排序。
4. **P3 方案 A**：若 B 仍不满足，迁移到专用 GPU 光束渲染器（参考 `HordeGpuRenderer`）。
5. **P4 方案 D/E**：绘制合并、overdraw、首帧预热。
6. 微优化（法线缓存等）保留，但不再作为主路径。

## 七、验证与关闭条件

| 方案 | 验证 | 关闭条件 |
|---|---|---|
| C 阴影／Iris 路由 | 光影开／关、同一 label 两轮；shadow pass 时间、draw call、JFR | 阴影相关 CPU 明显下降，光束视觉一致，光影下仍可见且无阴影伪影 |
| B 原生实例化 | 单／多光束、透明交叠、编辑器预览；逐顶点／截图对照 | CPU 每帧顶点展开消失，排序与材质一致，P95／P99 改善 |
| A 专用渲染器 | 视觉对照、连续帧、墙体截断、跟随、取消；GPU 时间与 draw call | 光影下施放窗口接近空场景基线的合理增量，首发可控；无视觉退化 |
| D 资产合并 | overdraw 统计、截图对照 | 绘制与填充成本下降且观感不变 |
| E 首帧预热 | 预热成本、首发延迟、进世界成本 | 首发长帧下降且没有转移为进世界卡顿 |

所有改动都需固定相机／投影／seed、记录实际后端（Iris／Sodium 版本、shaderpack 名）、分开报告 CPU、GPU、阴影与等待，不以平均 FPS 或单张截图代替验收。

## 八、未覆盖

- 未实现任何架构改动，因此没有“优化后收益”。
- 未做 draw call 计数与 shadow pass 独立计时；Iris 阴影占比来自 JFR 样本，不是精确墙钟。
- 未验证方案 B 的原生 API 是否完整支持单条长光束的透明排序与编辑器预览。
- 未测其它 shaderpack、不同分辨率、不同 shadow distance 与多人场景。
- 光影包来源为 horde 工作区，测试后已恢复 Iris 配置；包文件保留，后续复测需重新启用并确认版本一致。
