# 真实女皇激光测试与优化方案

测试时间：2026-10-24（北京时间）。范围：真实技能夹具（女皇激光／星束）的帧时间、GPU 时间、阶段 marks、首次施放阻塞与 Debug 路径。生产代码基线为 `1fb3aba4`（含法线缓存 `bd29ff62`）；本轮没有修改业务代码、资源、动画、LOD、技能时序或伤害。

相关文档：[测试前优化建议与问题原因](photon-optimization-pretest-20261024.md)、[测试后优化建议与问题原因](photon-optimization-posttest-20261024.md)、[性能审查](photon-performance-audit.md)、[唯一问题表](../issues.md)。

## 一、结论摘要

1. **稳态帧时间正常，成本集中在首次施放。** 90 秒 51432 帧：均值 1.750 ms、P99 8.125 ms、最长 599.019 ms。0～4 秒空基线均值 1.248 ms、无超过 50 ms 的帧；4.69～6.21 秒出现 599／525／486／421 ms 长帧，4～7 秒内长帧合计约 **2.118 秒**，占全部超过 50 ms 帧时间（2.266 秒）的绝大部分。
2. **后续两次施放已明显变小但仍有尖峰。** 第二次／第三次施放窗口均值 3.710／3.442 ms，最长 80.465／67.757 ms；相对首发下降约一个数量级，但未消失。
3. **Debug 证实首次施放窗口内存在同步准备。** CHARGE 阶段的 `QueenBeamEffects$Beam.update:289 → PhotonChildSpawner.prepareNext:67 → FXRuntimeMixin.lobecorp$prepareNamedRuntime:65 → PhotonNamedRuntime.<init>:115 → PhotonModelOutput.prepare:180` 在 Render thread 上准备模型 `laser_body`（clip=`beam`），读取、解析、拓扑与材质都在该调用内完成。
4. **法线缓存按预期工作。** 首次烘焙 `NormalCache.update` 为 miss（`valid=false`），随后同一世界矩阵命中（`valid=true`，缓存行列式 1.7280004），不再调用 `Matrix4f.normal`。
5. **与上一轮法线缓存后复测同量级，不能宣称稳定收益。** 上一轮 11:19 为均值 1.730／P99 8.529／最长 608.366 ms；本轮 1.750／8.125／599.019 ms。差异在运行波动内，且 P14 的帧节奏与相机可比性仍未解决。

## 二、工具链与运行账本

| 运行 | 入口与参数 | 结果 |
|---|---|---|
| BenchMod 合成基线 | IDEA `BenchMod Photon`（`-PphotonBench=true`），无 Debug，Sodium 0.9.2 | **PASSED**，`environment.valid=true`，8 个 Photon 场景通过，`sweeper-render` 按过滤器 SKIPPED；11:27:28～11:30:34 |
| 真实激光采集 | IDEA `Client` 运行配置；环境变量 `LOBECORP_LASER_PROFILE=laser_mcp_20261024_1132`、`LOBECORP_LASER_PROFILE_SINGLE_CAPTURE=true`；Sodium 0.9.2；MCP 端口 19877 | **DURATION** 正常结束，90.0004 秒／51432 帧；三次施放 STARTED，三次客户端 0→1→2 与移除齐全；验证截图可见女皇、法阵、粉色光束与目标 |
| Minecraft MCP | 运行期间经 19877 桥接调用 `ping`、`get_minecraft_status`、`get_player_info`、`screenshot_to_file` | `pong`；玩家 `Dev` 位于 `-7.0 58.0 -8.0`、朝向 `-25.0 8.0`，与夹具相机参数一致；截图 2560×1494／952,961 字节 |
| Debug 路径核对 | IDEA Debug 启动同一 `Client`；环境变量 `LOBECORP_LASER_PROFILE=laser_debug_20261024_1137`、`SINGLE_CAPTURE=true` | 断点确认准备与法线缓存路径；该轮暂停不计性能，核对后停止会话 |

原始证据：

- 采集报告：`run/client/performance/laser_mcp_20261024_1132_frames_marks_68751587421500.json`
- 逐帧数据：`run/client/performance/laser_mcp_20261024_1132_frames-20261024-033306-813-68750546447200.json`
- 验证截图：`run/client/screenshots/laser_mcp_20261024_1132_verification.png`
- MCP 截图：`build/modBench/laser-mcp-shot.png`
- BenchMod 报告：`build/modBench/raw-results/default/client/summary.json`、`report.md`
- 客户端日志：`run/client/logs/latest.log`

## 三、环境

Windows 11，16 逻辑处理器，RTX 3060 Laptop（驱动 616.64），Java 25.0.2（JetBrains），Sodium 0.9.2，Iris 已加载但**未选择 shaderpack**（日志：`Shaders are disabled because no valid shaderpack is selected`）。窗口 2560×1494、非全屏、聚焦且最大化（全部帧 flags=3），Vsync 关，渲染距离 12，限帧 260，`throttle_reason=NONE`。世界种子 `20261018`，目标距离 16 格，目标生命 1024，相机 `(-7, -8)`、yaw -25、pitch 8，`camera_distance_scale=1`。粒子随机种子未固定；`code_animation_render_samples` 仍为空对象，`allocation_capture.enabled=false`。

## 四、实测数据

### 4.1 整体

| 指标 | 数值 |
|---|---|
| 帧数／时长 | 51432／90.0004 秒 |
| 帧间隔 均值／P50／P95／P99／最长 | 1.750／1.191／3.863／8.125／599.019 ms |
| GameRenderer CPU 均值／P99／最长 | 0.945／3.397／401.091 ms |
| GameRenderer GPU 均值／P99／最长（48409 个完成样本） | 0.604／3.624／400.645 ms |
| GPU 查询 | started 48409、completed 48409、pending 跳过 3023、错误 0 |
| 渲染线程分配 均值／P99／最长 | 283,664 B／帧、2,286,512 B、219,685,544 B（含采集开销） |

### 4.2 按窗口

区间按当前 HEAD 时间选取，边界处的 interval 可能属于前一帧；窗口包含技能外时段，不是纯光束成本。

| 观测窗口（采集秒） | 均值 ms | P95 ms | P99 ms | 最长 ms | >50 ms 帧数 |
|---|---:|---:|---:|---:|---:|
| 0～4 空基线 | 1.248 | 1.844 | 2.315 | 4.844 | 0 |
| 4～16 首次施放附近 | 5.051 | 9.948 | 16.389 | 599.019 | 5 |
| 31～43 第二次施放附近 | 3.710 | 8.335 | 13.164 | 80.465 | 1 |
| 58～70 第三次施放附近 | 3.442 | 7.797 | 11.911 | 67.757 | 1 |

全部超过 50 ms 的 7 帧合计 2.266 秒；其中 4～7 秒内 5 帧合计 2.118 秒。最长帧序列与 CPU／GPU：599.019（CPU 2.150／GPU 1.983）、525.018（320.512／317.344）、486.370（401.091／400.645）、421.375（55.377／55.574）、85.745（14.614／15.181）；第二次／第三次施放为 80.465（11.826／13.653）与 67.757（21.176／24.163）。**长间隔与当前帧 CPU／GPU 按数组索引并不同步**（间隔属于前一 HEAD），上表用于定位窗口，不用于把某帧精确归因到单个方法。

### 4.3 施放阶段（相对采集开始）

| 施放 | 服务端施放 | 客户端 charge | 客户端 output | 移除 | charge→output |
|---|---:|---:|---:|---:|---:|
| 第 1 次 | 4.067 s | 4.101 s | 5.584 s | 15.542 s | **1.483 s** |
| 第 2 次 | 31.019 s | 31.090 s | 32.042 s | 42.541 s | 0.952 s |
| 第 3 次 | 58.013 s | 58.090 s | 59.042 s | 69.543 s | 0.952 s |

首发 charge→output 比后续多约 0.53 秒，与 4～7 秒的长帧窗口一致；输出持续约 11.44／10.50／10.50 秒。charge 记录是客户端观测时刻，首发阻塞会推迟记录，不能据此推断服务端技能时长变化。

## 五、BenchMod 当前代码基线

同条件（种子 7、Sodium、无 Debug）MEASURE 帧间隔，单位 ms：

| 负载 | 均值 | P99 | 最长 | create_emit |
|---|---:|---:|---:|---:|
| 空场景 | 2.114 | 4.779 | 31.230 | — |
| 1 静态 | 2.094 | 5.950 | 22.030 | 191.370 ms |
| 1 动画 | 2.176 | 3.923 | 11.077 | 10.439 ms |
| 16 静态 | 3.378 | 5.661 | 22.285 | 91.225 ms |
| 16 动画 | 5.874 | 10.071 | 15.890 | 73.083 ms |
| 创建时 16 动画 | 5.663 | 10.173 | 62.808 | 60.374 ms |
| 16 静态重复 | 3.061 | 4.620 | 9.544 | 60.959 ms |
| 空场景重复 | 1.400 | 2.496 | 7.720 | — |

这与既有报告的“最终候选”两轮同量级（如 16 静态 3.322／6.913、16 动画 5.827／6.611）；同代码不同轮仍有空场景 1.4～2.2 ms、16 静态 3.3～3.4 ms 的波动。**不能把差异全部归因法线缓存，也不据此关闭 P11／P14。** 夹具仍为 `shading=false`，未覆盖真实资产的逐角光照成本。

## 六、Debug 路径证据

1. **首次施放的同步准备（Render thread）**：断点在 `PhotonModelOutput.prepare:180` 命中，栈为
   `prepare:180 ← PhotonNamedRuntime.<init>:115 ← FXRuntimeMixin.lobecorp$buildNamed:83 ← lobecorp$prepareNamedRuntime:65 ← PhotonChildSpawner.prepareNext:67 ← QueenBeamEffects$Beam.update:289 ← QueenBeamEffects.tick:75 ← ClientRuntimeEvents.onClientTick:100`。
   变量与求值：`getName()="laser_body"`、`config.resource=null`（内嵌资产）、`config.clip="beam"`、`sampler=null`（首次进入 `loadDefinition()`）。说明首次施放时，蓄力阶段在客户端 tick 上同步完成“下一份光束实例”的命名运行态构造与模型准备。
2. **法线缓存 miss→hit（Render thread）**：断点在 `PhotonModelGeometryUtil$NormalCache.update:99` 命中。首次 `valid=false`、`determinant=0.0`、world 线性部分为 1.2 均匀缩放加轻微旋转（2.945e-2）；条件改为 `valid && matches(world)` 后下一次命中 `valid=true`、`determinant=1.7280004`（1.2³），线性缓存与 world 一致，跳过 `world.normal()`。
3. **提交栈**：`NormalCache.update:99 ← PhotonModelGeometryUtil.write:28 ← PhotonModelOutput.lambda$bakeModelGroup$0:482 ← bakeModelGroup:480 ← bakeBatches:452 ← PhotonWorldRenderStateMixin:29 ← GameRendererMixin:22`，确认光束模型仍走 QUADS 逐角提交路径。
4. 日志同时显示 KilaGraph 对若干 graph 的 Iris 提示：`vertex format [position, color, uv0, uv2, normal]` 不满足 ENTITY 格式，故这些 graph 不经过 shaderpack，保持自带 shader。**本轮无 shaderpack，该提示不构成本轮性能结论；但启用光影后这些 graph 的材质／光照路径需要单独验收。**

## 七、根因分析

### 7.1 首次施放约 2.1 秒停顿

- **同步准备落在施放窗口内**：`prepareNext` 由 CHARGE 触发，Debug 栈证明它在客户端 tick（Render thread）上完成 `PhotonNamedRuntime` 构造、命名库解析、模型读取／解析／拓扑／骨骼／材质准备。第一次施放窗口因此同时包含准备、输出切换与首次绘制，首发 charge→output 比后续多 0.53 秒。
- **首次 GPU 使用与管线／纹理准备**：两个重帧的 CPU 与 GPU 时间几乎相等（401／401、320／317 ms），更像渲染线程在首次使用该材质／管线时同步等待或编译；但本轮没有 JFR／spark 栈，也没有 GPU pipeline 编译计时，不能把 401 ms 精确归给某一个方法。日志显示首次施放前后才出现 KilaGraph／Iris 的新 graph 提示，方向一致，仍属待验证假设。
- **长帧集中且可重复**：每次运行都会在 4～7 秒出现数百毫秒级帧，说明不是随机 GC；但本轮粒子随机种子未固定，不能做严格 A/B。

### 7.2 后续施放 80／68 ms 尖峰

- 预准备只有“下一份”一份（`prepareNext` 单槽），且只在 CHARGE 触发一次；OUTPUT 时 `emitNow` 仍要建实例、走 `FXRuntime.emit`、命名运行态与材质；热施放不再付完整读取成本，但仍有可观测的同步创建与首次帧成本。
- 稳态均值 1.75 ms、GPU 均值 0.60 ms，说明光束持续渲染不是主要瓶颈；尖峰来自“施放瞬间的同步工作”，不是持续输出。

### 7.3 稳态渲染

- 法线缓存已生效（miss 一次后 hit）；上一轮 JFR 的 `write` inclusive 仍约 71%，说明剩余成本在每帧逐角世界变换、颜色／光照与顶点写入，以及动画模型的 `sample`／`skinMatrices`（激光 `clip=beam` 属于动画路径）。
- 本轮逐帧分配均值约 284 KB、P99 约 2.29 MB，包含采集开销；`allocation_capture.enabled=false`，没有新的分配调用栈。

## 八、优化方案

### P0（先补证据，改动最小）

1. **给试验夹具加分段计时**：在 `QueenBeamPerformanceTrial` 的 CHARGE／OUTPUT 前后记录 `prepareNext`、`emitNow`、首次渲染该实例的耗时，并单独记录 GPU 管线／纹理首次使用（可用现有 `PhotonFrameCapture` 阶段或 `PhotonPerformanceTraceUtil` 标记）。目标是把 2.1 秒拆成“准备／创建／首次 GPU 使用”三块，而不是继续猜。
2. **修正可比性**：记录实际相机／投影矩阵、固定粒子 seed、固定世界动态条件；同一 label 至少两轮，P99 按会话报告。否则无法判断法线缓存或任何改动的稳定收益（P14）。

### P1（首次施放）

3. **把预准备提前到采集／施放前**：当前 `prepareNext` 在 CHARGE；可改为在 SETTLE（或技能进入前摇之前）预取第一份光束实例，并保留失败重试。预期把 `laser_body` 的读取／解析／拓扑从首次施放窗口移出；不改变技能时序与伤害，但会改变“何时占用客户端 CPU”，需要按进世界／首次施放整体成本验收。
4. **内容级共享**：`PhotonModelSourceUtil.load`、`PhotonNamedCodecUtil.read`、`PhotonModelTopologyUtil.prepare` 与材质模板按“资源 id＋内容代次”缓存不可变结果；多个光束实例只保留可写时钟／槽／实例状态。这是同时降低首次与热施放准备成本的核心改动，需先核对原生 `FXData.copy`／`IFXObject.deepCopy` 的共享边界。
5. **首次 GPU 使用预热**：若分帧计时证明 401／320 ms 主要来自管线编译或纹理上传，应在进入测量／首次输出前完成一次性预热（例如在 SETTLE 阶段离屏提交一次同一材质）。不能在测量帧内做静默预热并宣称成本消失；预热本身的成本要单独报告。

### P2（热施放与稳态）

6. **把单槽预准备扩展为有界预备队列**：允许 1～N 份同内容实例预创建，输出时直接取用；创建预算按 tick 分摊，失败回退到同步路径并记录。
7. **复用实例与帧缓冲**：`PhotonModelPoseSampler` 的权重／索引预编译、平铺数组与实例局部缓冲复用（P12 方向）；`PhotonModelGeometryUtil.write` 的角点预展开、法线／shade 预缓存与矩阵手写乘（P11 方向）。保持 4 角 QUADS、排序、材质与光照语义。
8. **避免每施放重复命名库解析**：若 `PhotonNamedRuntime` 每个实例都读取同一 controller 定义，应按定义哈希缓存解析结果；这只是准备链的一部分，不能替代 P1 的内容级共享。

### P3（工具与报告）

9. **为真实激光加 JFR／spark 采集**：本轮只有帧与 GPU 时间，无法给出方法级归因；下一轮在采集期间同时开 JFR，至少覆盖首次施放 4～8 秒，区分 CPU 同步、GPU 等待与线程停泊。
10. **修正夹具视觉覆盖**：本轮验证截图可见光束，但没有连续帧、墙体截断、跟随、取消与多材质；这些需要独立场景，不能用单张截图代替验收。

## 九、验证与关闭条件

| 改动 | 验证 | 关闭条件 |
|---|---|---|
| 预准备提前 | 同 label 两轮；首次施放窗口、charge→output、长帧计数；进世界／首次施放整体成本 | 首次施放长帧显著下降，且没有把同等级成本转移为进世界或准备队列堆积 |
| 内容级共享 | 单／多实例、连续／间隔施放、重载、缺失资源；准备次数与分配峰值 | 同内容重复准备消失；技能时序、伤害与输出一致 |
| GPU 预热 | 分帧计时 + GPU 管线／纹理首次使用时间；截图与材质对照 | 首次使用成本从测量窗口移出且总成本可解释，不以删帧或降画质换成绩 |
| 热施放预备队列 | 连续施放、取消、退出世界；队列深度与失败回退 | 后续施放尖峰下降，无漏播、无取消后旧结果发布 |
| 稳态几何／动画 | 1／16 实例、透明交叠、多材质、动画相位；逐顶点对照 | 成本下降且 P95／P99 不退化，截图与排序通过 |

## 十、未覆盖

- 未启用 shaderpack；Iris 只加载未选包，KilaGraph 的 shaderpack 路径未验收。
- 未做 JFR／spark 方法级归因，首次 401／320 ms 的重帧成因仍是假设。
- 未测跟随、墙体截断、取消、晚追踪、卸载及多玩家服务器场景。
- 未做改进前后对照，没有任何“改善百分比”；法线缓存收益仍未证明稳定，P11／P14 不关闭。
- 粒子随机种子未固定，不能宣称严格确定性对照。
