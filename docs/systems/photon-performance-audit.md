# 当前性能静态审查与优化计划

> 阶段说明：本页是 2026-10-19 静态候选与实验方案；后续局部实现见 [最新 WIP](photon-workspace.md)。尚无当前版本完整冷热、P99 与视觉验收，不将候选当作确认根因或收益。

日期：2026-10-19。状态： **只读源码审查与优化提案，未实施、未运行采样**
。本轮三路并行审查时间轴/加载、模型/渲染、诊断/资源事务，由主代理统一记录。没有修改代码、配置、Shader、游戏资源或依赖，没有执行构建、Data、测试或游戏。

配套[模型特效与高级特性重设计方案](photon-model-redesign.md)。旧自建模型/动画/专用渲染核心已移除；本页审查的是仍生效的项目工具及锁定
Photon，不把已经删除的旧实现当作当前热点。

## 2026-10-24：Sodium／BenchMod 与真实激光实测

本节是当前运行证据；下文 2026-10-19 的 S01–S23 保留为历史候选。用户已授权基准、Minecraft MCP 和 Debug，本轮只增加 bench 夹具与运行配置，没有优化生产渲染器、改实体动画、LOD 或特效内容。代码基线为 1a5c2c3＋本轮未提交 bench 改动（报告 dirty=true）。

### 环境与方法

Minecraft 26.1.2、NeoForge 26.1.2.100、Photon 26.1.2.3、BenchMod 0.1.5-beta、Sodium 0.9.2、Java 25.0.2；RTX 3060 Laptop GPU，OpenGL／NVIDIA 616.64。窗口真实最大化，帧缓冲 2560×1494，VSync=false，配置帧上限 260，视距／模拟距离 12。帧间隔实测可低于该配置上限对应的间隔，不能据配置值假定实际按 260 FPS 限帧。保留 Iris、JEI、Jade、Spark 等既有依赖，不能推广为纯 Sodium 环境。

模型夹具固定同一 289 顶点／256 四边面／单骨骼资产，唯一动画差异为 turn 片段；先稳定 40 tick、预热 100 tick、截图后恢复，再测量 300 tick。使用生产 PhotonModelOutput／PhotonGeoEffect；截图已核对模型网格实际可见。首次创建用例在当前 JVM 中测量开始时发射，不能称为进程全冷启动。

首次普通 BenchClient Debug 因默认导入类路径无 MCP 而停止。补齐专用 photonBenchClient 类路径后，Debug setup 确认 Sodium=true、MCP=true、GLFW_MAXIMIZED=1；临时断点先移除再继续。含 Debug 的成绩仅作探索，不用于主表。随后通过 IDEA 独立执行 BenchMod Photon，无 Debug，权威 summary.json 为 PASSED（8 个 Photon 用例通过、清道夫用例按过滤器 SKIPPED）；verifyBenchClientReport 与制品收集成功，构建结束为 BUILD SUCCESSFUL。原生 MCP 工具未连接到本工作区时，使用本工作区既有桥接的标准 MCP 协议，固定 19877，实际得到 pong／connected=true；没有扫描或操控其他实例。

### 无 Debug 模型结果

下表单位均为毫秒，使用各用例 MEASURE 原始帧，不平均不同会话的 P99。

| 负载 | 均值 | P50 | P95 | P99 | 最长帧 |
|---|---:|---:|---:|---:|---:|
| 空场景 | 1.811 | 1.609 | 3.101 | 4.089 | 21.726 |
| 1 个静态 | 1.718 | 1.611 | 2.392 | 2.880 | 25.262 |
| 1 个动画 | 2.073 | 1.918 | 3.126 | 4.106 | 22.445 |
| 16 个静态 | 3.258 | 3.040 | 4.975 | 6.256 | 13.046 |
| 16 个动画 | 5.784 | 5.501 | 7.807 | 10.477 | 21.171 |
| 测量开始时创建 16 个动画 | 6.213 | 5.789 | 8.362 | 12.227 | 273.700 |
| 16 个静态重复 | 2.851 | 2.770 | 3.691 | 4.346 | 7.844 |
| 空场景重复 | 1.645 | 1.242 | 2.425 | 4.671 | 1421.368 |

创建 16 个动画实例的同步 create／emit 为 269.998 ms，与该用例 273.700 ms 最长帧对应；首个静态实例创建 187.634 ms，后续单实例 9.885 ms。它们包含序列化复制、模型准备、材质等整条创建链，不能全部算成网格解析。末尾空场景第 7624／9695 帧出现 1.421 秒尖峰，尚未确认原因，未删除该值；这表明并非所有长帧都由模型造成。静态重复基线下降也表明存在 JIT／缓存／环境漂移。

### 已确认的代码热点

1. **静态模型仍逐帧完整提交 CPU 几何。** PhotonModelOutput.bakeBatches → bakeModelGroup → PhotonModelGeometryUtil.write，对每个三角形写 4 个 QUADS 顶点，重复末角，并逐次做世界变换、法线与颜色计算。局部姿态缓存命中并不缓存最终提交几何。16 静态用例的渲染线程 JFR 执行样本中，write 的 inclusive 栈为 245／324（75.6%），重复用例 212／280（75.7%）；不是 GPU 时间占比。该夹具每帧 16×512×4=32768 次顶点写入。优先考虑保留透明排序和材质语义的静态／稳定拓扑复用，不能直接删除重复角或改变画质。
2. **动画 CPU 蒙皮与对象化姿态是新增主热点。** 16 动画 MEASURE 中 sample 为 222／462（48.1%），write 为 101／462（21.9%）；二者存在不同调用层级，不能与其他 inclusive 数字直接求和。skinMatrices 每次采样按顶点创建 combined／values 两个 float[16]、Matrix4f 和映射项；positions、不可变 Vector3／Vertex、Group 又建立整份帧数据。采样时间变化使 Input 缓存失效，静态缓存并未消除动画的这些成本。
3. **分配压力是真实存在，但不能把 JFR 首事件权重当精确单方法字节。** ThreadAllocationStatistics 首尾计数差显示：16 动画整份约 23 秒录制中渲染线程累计分配约 19.554 GiB，静态录制约 0.984 GiB；包含 setup／预热，非存活内存或泄漏。MEASURE 加权分配样本主要落在 skinMatrices、positions、sample／immutable。JFR 开始时首个分配事件可能携带跨录制间隔的权重，分析已按测量窗口过滤，未把 setup 中一次 JSON 构造的巨额首权重当作其真实分配。后续优化应复用预编译权重／索引及局部缓冲，仍需维持帧快照所有权。
4. **同步创建／发射阻塞尚未解决。** 合成负载已有约 270 ms 可复现阻塞；真实激光 spark 栈还命中 PhotonChildSpawner.emitNow → PhotonSpawnInstance.start → FXRuntime.emit → PhotonNamedRuntime 构造 → PhotonModelOutput.prepare，以及 PhotonModelSourceUtil.load／PhotonModelAssetUtil.read 和 IFXObject.deepCopy 的 CODEC 往返。准备与不可变定义共享应先优化这条真实调用链，不能只减少普通 tick 的射线或参数 Map。

JFR 执行样本以 ActiveRecording.recordingStart 加报告阶段耗时近似切出 MEASURE，边界存在毫秒级误差；样本数量有限，不把采样占比当作精确墙钟或 GPU 耗时。

### 真实女皇激光与工具对照

复用现有 QueenBeamPerformanceTrial，在独立快速测试世界、固定种子／相机、16 格目标距离运行两次 90 秒，每次按 4／31／58 秒发射真实技能。两轮均到 COMPLETE，环境全帧 flags=3（聚焦＋最大化），throttle_reason=NONE。原始资源由模组加载；验证截图可见女皇、法阵和激光，不等同于完整视觉验收。粒子随机种子未固定，不能宣称严格确定性 A/B。

| 采集模式 | 帧间隔均值／P99／最长 | render CPU 均值／P99 | render GPU 均值／P99 |
|---|---|---|---|
| spark 开启 | 2.146／11.527／860.759 | 1.165／5.712 | 0.668／4.694 |
| spark 关闭 | 2.235／11.587／855.774 | 1.203／5.560 | 0.660／4.332 |

两轮都保留 GPU timestamp 帧采集，所以仅排查 spark 开关，未排除帧采集自身开销。GPU 有效样本 40297／38383，pending 跳过 1635／1878，错误 0；不能将跳过视为零耗时。GPU 范围仅 GameRenderer.render，不含 tick、extraction、present、swap 和 limiter，与 CPU 未校准，不相加。关闭 spark 后首次长帧仍约 856 ms，不能归咎于 spark 才出现；正常均值也没有支持稳定 GPU 算力饱和的证据，但个别 GPU 长帧仍存在。

spark 使用 Windows 的 Java 采样引擎，为墙钟采样，含等待。渲染线程 90 秒中，PhotonWorldRenderState.drain inclusive 19.868 秒、模型 sample 2.456 秒、geometry.write 2.072 秒、IFXObject.deepCopy 2.180 秒、prepare 1.576 秒；这些嵌套数据不可相加。大量 glFenceSync 栈来自 Minecraft／GUI／雾／AnvilLib／Sodium 的 MappableRingBuffer.rotate 与 DynamicUniformStorage.endFrame，并非本项目 GPU timestamp 查询调用；不能把所有 OpenGL 栈都算作 Photon 模型。未命中 AnimatedGltfModelSource／VertexAnimation／DynamicMeshCache／modelMeshBuffer 的样本，不能声称这些原生路径已解决。

code_animation_render_samples 为空，allocation_capture.enabled=false；本轮无法用这些字段确认旧姿态混合批评、逐帧时间单调性或分配专项完整性。三次技能阶段 marks 已记录，不替代动画骨骼逐帧验收。

### 对旧批评的逐项核对

本节只保存本轮证据判断；当前问题状态以 [唯一问题表](../issues.md) 为准。**没有证据支持 S01–S23 已全部解决。**

| 项 | 本轮核对结论 |
|---|---|
| S01 | 未解决的源码条件仍在：当前 SceneView.simulateTo／runSimulationTicks 同步循环，项目 Mixin 仅观察；未测编辑器长拖动。 |
| S02 | 同刻时间仍走 reset 分支，10000 tick 上限后仍 setTime(target)；源码风险保留，未做原生负例。 |
| S03 | pendingSimulateTarget 仍是时间值；未执行跨工程／重置身份实验，不能关闭。 |
| S04 | 未测参数／种子高频拖动，本轮运行播放不覆盖编辑器重放。 |
| S05 | 未执行 MCP 连续 seek／取消场景，不能关闭。 |
| S06 | 当前 TimelinePlayerMixin 仍调用 leafTracks 并建立流／列表；本轮未测多轨道规模收益。 |
| S07 | 未完成控制轨道索引对照；真实播放存在不代表复杂度改善。 |
| S08 | 未测大工程 MCP 状态序列化／哈希；正常 pong 不构成验收。 |
| S09 | 未解决：真实激光命中 load／copy／emit／prepare，同步创建阻塞已测到。 |
| S10 | 原批评指原生 GLTF；本轮命中自定义模型解析，原生 GLTF 未覆盖，不能混称修复。 |
| S11 | VAT 烘焙／上传／多副本内存未覆盖，未关闭。 |
| S12 | DynamicMeshCache 新几何包装及 TileParticleRenderer 按 mesh 身份重建条件仍在；真实样本未命中该链，不能称当前主瓶颈或已修复。 |
| S13 | 自定义模型完整 CPU 几何提交已确认；原生 TileParticle CPU fallback 需独立实验。 |
| S14 | 当前 sampler 仍重建法线与展开角数据；原生 GLTF 缺法线／切线用例未覆盖。 |
| S15 | 自定义 Input／Frame 用不可变容器是局部保护；原生姿态数组／资源代次失效未验收。 |
| S16 | 保存／导出客户端准备与取消未覆盖，不能关闭。 |
| S17 | 安全扫描与重复解码未做本轮事务统计，不能关闭。 |
| S18 | 未开细粒度 Trace 面板做身份／锁 A/B，不能关闭。 |
| S19 | 未测隐藏／冻结大图表，不能关闭。 |
| S20 | 两次采样均包含启动成本，但未做模式／线程首次分配专项；不能关闭。 |
| S21 | 末尾空场景仍有未归因长帧；不能未经调用证据归因导出，也不能宣称停止路径已解决。 |
| S22 | 当前 GPU 结果仍逐项构造 JsonObject；真实帧 GPU 采集有效，但详细查询工具分配未做 A/B。 |
| S23 | 本轮自有 FX 均按场景清理、游戏正常退出；没有 GPU 创建／释放账本或重载循环，不能据此证明无泄漏。 |

### 原始证据与收尾

可视化备份根：C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c。

- photon-bench-normal-baseline-20261024/client：权威 summary.json、JSONL、8 份 JFR、截图；上级 analysis.json 保存分析结果。
- photon-bench-debug-baseline-20261023/client：含 Debug 的探索原始报告，不混入无 Debug 主表。
- photon-real-laser-spark-20261024：真实激光帧 JSON、阶段 marks、验证截图及原始 .sparkprofile；photon-laser-analysis-20261024.json 保存解码后的渲染线程树统计。字段定义经 IDEA 读取本地 SparkSamplerProtos 核对。
- photon-real-laser-plain-20261024：无 spark 的原始帧、阶段 marks 与验证截图；工作区原始文件仍在 run/client/performance/photon_plain_20261024_*。
- 所有本轮临时断点均已移除，Debug 会话已结束；两个真实激光客户端均通过 MCP 正常保存退出世界并点击退出游戏，IDEA 日志均 BUILD SUCCESSFUL。最后客户端 PID 68400（2026-10-24 00:11:18 启动）已不再存在。临时桥接 PID 64004／67504／61560／67740 均正常退出 0；未关闭归属不明的既有 Java 进程。
- 最终收尾新增 Provider 显式过滤守卫，避免普通 BenchMod 运行混入 Photon 用例；静态检查和再编译成功，未另重跑完整样本。该守卫不改变显式 Photon profile 的负载，但本节原始数据的代码状态早于该注册保护。
- 未修改生产优化代码，没有“优化后提升百分比”。下一步应先按上述热点制定窄范围优化和同条件复测，不以降低动画帧率、LOD 或改变资源掩盖成本。

## 1. 证据等级、范围与优先级

- C：通过 IDEA 读取到的源码条件路径/数据结构，可确认“满足条件时会执行什么”。
- H：由条件路径推导的性能/正确性风险，尚未运行复现。
- M：运行量测。本轮 **没有 M 级结果**，没有实测占比、收益或根因确认。
- P0：交互长阻塞、进度/取消/保护正确性基础，后续首先建立量测与原型。
- P1：明确成本路径，需判断真实资产是否命中及占比后局部优化。
- P2：资源预算、工具额外成本和生命周期完善，按量测排序。

行号来自本轮 IDEA 读取；后续实现前重新导航，不能只按旧行号打补丁。依赖基线为 Photon 26.1.2.3，源码位置：
E:
/xm/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/photon-neoforge-26.1/26.1.2.3/cdf432d4279bfd20133d56ec1900b74d48b67860/photon-neoforge-26.1-26.1.2.3-sources.jar

外部类缩写：

- scene = com.lowdragmc.photon.gui.editor.view.scene
- timeline = com.lowdragmc.photon.client.fx.timeline
- model = com.lowdragmc.photon.client.gameobject.emitter.data.model
- skin = model.skin
- renderer = com.lowdragmc.photon.client.gameobject.particle.renderer

项目类以 org.unitego.lobecorp 为前缀，下文给出其余包路径。外部依赖通过 IDEA 解析，未复制/解压到项目。各问题的具体耗时与触发频率仍需未来运行授权。

## 2. 优先级总表

| ID  | 优先级    | 已确认的成本或结构                             | 主要触发                    | 优化方向                       |
|-----|-----------|------------------------------------------------|-----------------------------|--------------------------------|
| S01 | P0        | 合并后 seek 仍同步逐 tick 重放                 | 回退/长跨度拖动             | 有版本的分片作业、完整结果发布 |
| S02 | P0        | 相等时间重放；超长请求实际步数与时间标签可不同 | 同目标刷新、超过原生上限    | 按内容版本去重、进度真实化     |
| S03 | P0 基础   | pending 仅保存时间，缺工程/内容身份            | 重置/切工程及未来异步       | 生命周期取消、版本键           |
| S04 | P1        | 参数/种子/循环直接重放                         | 高频数值拖动                | 统一最新预览请求               |
| S05 | P1        | MCP seek 绕过合并，提交后不可伪取消            | MCP 连续 seek               | 显式预览作业协议与旧语义兼容   |
| S06 | P1        | 时间轴多次展开树与创建集合                     | 多轨道/长重放               | 按内容版本编译执行列表         |
| S07 | P1        | 每目标扫描控制轨道/片段                        | 多目标、多控制片段          | 保序索引与区间查询             |
| S08 | P1        | MCP 回复重复工程序列化/哈希                    | 大工程状态查询/预览         | 单快照复用、轻量状态           |
| S09 | P1 先测   | FX 冷加载与对象/轨道复制                       | 首次触发/多实例             | 准备就绪、保留原生浅拷贝收益   |
| S10 | P1        | glTF 同步解析可在配置/渲染请求时发生           | 首次选模型/首绘             | 合并加载、后台纯解析           |
| S11 | P1        | 首次 VAT 同步烘焙/上传，副本可重复分配         | 首次播放/复制源/改帧数      | 共享不可变表、实例相位分离     |
| S12 | P1 高价值 | 动画 mesh 身份变化触发基础 VBO/IBO 重建        | CPU 动画及部分 VAT 实例路径 | 拓扑与几何 revision 分离       |
| S13 | P1        | CPU fallback 按粒子展开模型顶点                | 未使用/不支持实例化         | 显示实际后端，兼容后再选路     |
| S14 | P1        | 缺法线展开顶点、切线首次生成/重采样            | 特定模型/法线贴图           | 导入报告、按需属性准备         |
| S15 | P2        | 姿态缓存可变数组、全局失效与组合依赖           | 异步求值/热更新             | 快照发布、局部失效             |
| S16 | P0        | MCP 文件准备位于客户端，超时不打断同步工作     | 大工程保存/导出             | 不可变捕获、可取消后台准备     |
| S17 | P0/P1     | NBT/zip 完整扫描及多层重复校验                 | 打开/保存/导出/GC           | 有界安全扫描、事务内验证复用   |
| S18 | P1        | 诊断身份包装与锁定/全量 View 复制              | 开启细粒度采集              | 有界身份缓存、冻结增量         |
| S19 | P1        | 面板反复排序/分桶，隐藏图表仍准备              | 大会话/冻结面板             | 内容版本缓存、可见页更新       |
| S20 | P2        | 采样开始/新线程进入时大数组分配                | 开始采集/首个 worker        | setup 分离、模式化预算         |
| S21 | P2        | 结束统计和 frame-only 路径仍有同步工作         | 停采集/专用 trial           | 统一冻结后后台统计与导出       |
| S22 | P2        | GPU 查询结果逐项 JSON 分配                     | GPU 详细采集                | primitive 记录，导出时编码     |
| S23 | P2 审计   | GPU 资源释放需完整追踪所有者                   | 关闭/移除/重载/异常         | 资源账本与生命周期配平         |

“高价值”表示有明确条件链值得优先做实验，不表示已证明它占用最多性能。

## 3. 时间轴与运行准备

### S01：合并请求之后仍是一个长同步任务

C：scene.SceneView.requestSimulateTo（160）只覆盖一个 long；flushPendingSimulate（164）从 drawBackgroundAdditional（175）或
screenTick（185）同步进入 simulateTo（190）。runSimulationTicks（210–223）连续执行最多 10000 tick，没有分片、取消或内容版本检查。

H：后退/大跨度拖动仍可占满界面所属线程。请求数量合并不能缩短单个请求的重放成本。

计划：将 seek 拆为有 request ID 的可取消作业，保持最新目标；先在原线程按一致模拟边界分帧，研究约 2 ms
切片起点；纯模型曲线直接求值。完整审计前不将整个 tickInternal 搬到 worker。

实验：前/后/交替拖动、长跨度跳转，记录请求/开始/每片/结束/发布时刻、实际 tick、最长主线程任务、取消延迟及最终落点；总模拟步和最终状态不能为保持流畅而默默减少。

### S02：同一时间也重放，长请求的时间标签可超前

C：SceneView.simulateTo（196–205）仅 time > curTime 走向前增量；相等时间走 reset、emit、从零重放。runSimulationTicks 截断为
10000 步，调用方随后仍 setTime (time)。

H：重复相同目标会重复工作；超过上限的 UI/其他入口请求可能出现“标签已到目标，状态未模拟到目标”。当前 MCP 的最大1200 tick
不会直接触发10000步截断，不能混为同一触发条件。

计划：以项目/资源/输入 revision＋时间判断 no-op；内容变化的同时间刷新仍重新求值。requested、actual、published
时间分开；超限明确继续分片/拒绝/报告未完成。

实验：未改内容重复 seek、修改参数后同时间 seek、边界与超上限请求；比较 reset 次数、实际步数与状态哈希。禁止只加 time==curTime
return。

### S03：pending 请求缺乏内容与生命周期身份

C：pending 仅为目标时间；SceneView.reset（143）、clearScene（135）未在这些方法中清除
pending，simulateTo（191）才清除。FXEditor.loadNewProject（65–75）会替换 runtime。

H：待处理请求在重置/切工程时是否错误应用尚未复现。当前同步代码不能被描述为已经存在“后台晚到结果”；版本保护是未来引入异步的前置条件。

计划：请求携带 project ID、runtime generation、authoring revision、resource
generation、target、seed；重置/关闭/切工程清理；发布前再核对，过期结果丢弃并记录原因。

实验：排入请求后立即切工程、关闭、撤销、改种子/刷新资源，确保无跨工程应用、无悬挂结果。

### S04：通用预览工具绕过合并

C：client.photon.editor.PhotonEditorTools.parameterPanel（879–904）的值回调调用 replayPreview；replayAt（931–935）连续
simulateTo (0) 与 simulateTo (time)。种子面板（1005–1010）和范围循环 tick（814–821）也直接重放。

H：数值拖动可能每次回调都重算；双重调用不必然每次重复完整两遍，需要记录 time=0 与最终目标各自工作量。

计划：统一为“内容已变＋目标时间”的最新预览请求；拖动期间按预算执行，松手确保精确最终值。范围循环研究起点检查点；发射参数变化仍需正确重模拟。

实验：统计数值事件数/重放次数、最终参数/画面、输入到可见延迟与分配；不得只更新已有粒子外观冒充完整发射预览。

### S05：MCP seek 的同步完成与取消语义

C：client.photon.editor.automation.PhotonEffectAutomation.preview（265–290，直接调用在284）调用 simulateTo ((long)time)
，MAX_SEEK_TICKS（35）为1200。execute（134–163）调度客户端；Request.run（89–96）在动作之前进入
COMMITTING，cancel（117–131）不能取消已提交阶段。

H：MCP 连续请求不走 UI 合并，仍可能阻塞。8秒超时不是 seek 计算上限；提交后等待真实结果的机制不能删除。

计划：定义新预览作业能力协商、jobId、query/await/cancel及 requested/actual/published
状态；保留既有调用“完成回复即完成”的语义或提供兼容适配，不能静默提前报完成。

实验：排队取消、准备中取消、提交后超时、反复 seek；回复与实际执行一致，无假取消或过期发布。

### S06：时间轴树多次展开与临时集合

C：timeline.Timeline.leafTracks（46–49）每次创建 ArrayList
并递归遍历；TimelinePlayer.evaluate（193–258）、applyAnimations（391–402）重复取
leaves；frame（147–152）的后处理（157–168）和动画又各遍历。受控对象集合也逐 tick 构造。

计划：按可靠的内容版本编译分类执行列表，区分激活/控制/动画/速度/信号/音频/后处理。原 tracks（29）暴露可变列表，没有失效覆盖时不能永久缓存。保留
tick/frame、小数插值、录制冻结与轨道顺序。

实验：0/100/500/1000轨道及分组，量测树展开次数、分配、tick/frame求值，逐场景对比语义。

### S07：目标与控制片段的乘积匹配成本

C：TimelinePlayer.controlledObjectIds（424–442）扫目标，evaluate（208–238）每个目标再扫轨道/片段；ControlTrack.clipForObjectAt（19–25）继续线性查找。

计划：目标 UUID → 保序轨道/区间索引；单调播放用游标，随机 seek 用区间查找，编辑后按版本重建。保留首匹配、边界包含、片段切换reset/seed、父子重置。

实验：固定片段总数增加目标数，测是否近似乘积增长；重叠/邻接/循环/撤销逐项对照，不能靠重排内容提高速度。

### S08：MCP 状态回复重复快照、哈希与序列化

C：util.photon.editor.automation.PhotonEffectProjectUtil.revision（273–276）snapshot→canonical→SHA256；describe（331–380）在337调revision，344再取snapshot，360–365逐对象序列化。seek结束也返回describe。

计划：一次请求固定一份快照，复用于哈希/SNBT/对象描述；提供轻量播放/任务状态与显式完整状态。authoring revision 与 pose
revision 分开，同时覆盖人工编辑、未入历史的真实修改。

实验：记录一次查询的snapshot次数、序列化字节、canonical/hash/JSON耗时；expectedRevision冲突保护不退化。

### S09：FX 冷加载和运行实例复制

C：com.lowdragmc.photon.client.fx.FXHelper.getFX（82–83）cache
miss在请求线程同步loadFX（87–100），含资源打开、压缩NBT、版本修复和反序列化；null失败不形成有效缓存值。FX.createRuntime（63–64）复制FXData；FXData.copy（46–49）复制对象和时间轴。IFXObject.deepCopy（189–190）走CODEC；ParticleEmitter.shallowCopy（327–329）已有config共享。

计划：按load、copy、init、emit、first draw分段；内容代次内合并准备，确定失败可诊断且可失效重试；保留原生浅拷贝已有收益。不可变定义与可写实例分离，不共享活Runtime。

实验：冷/热、1/10/100对象与多轨道，记录P50/P95/P99、分配、缓存；重载后按代次失效。旧扩展资源被保护拒绝时记录为不可用，不能计作快播放。

## 4. 模型、动画与绘制

### S10：首次模型解析可能发生在配置构建或渲染请求中

C：model.AnimatedGltfModelSource.load（508–525）直接ResourceManager.open→GltfMeshParser.parseModel；model.PhotonMeshCache.getModel（68–70）computeIfAbsent在调用线程运行loader。buildConfigurator（558–565）取model
()
.clipNames。GltfMeshParser.parseModel（86–102）readAllBytes/解码，Reader.read（177–193）准备骨架/网格/动画；GLB分配复制（113–144）及accessor数组（852–880）在同次路径。

计划：记录read/decode/mesh/skeleton/clip/publish；固定不可变字节与参数后后台纯解析，同资产请求合并，界面显示加载/失败状态。暂不可加载不缓存与确定失败缓存分开。

实验：冷选模型、重开工程、首次/后续触发；文件大小、渲染顶点和动画通道一同记录。

### S11：VAT 首次烘焙、上传及副本内存

C：AnimatedGltfModelSource.vertexAnimation（225–247）同步VertexAnimationBake.bake；skin.VertexAnimationBake.bake（78–109）逐帧蒙皮再逐顶点写表。model.VertexAnimation.buffer（88–107）首次要求渲染线程，分配native
staging并createBuffer。ParticleEmitter.extractInstancedTiles（763/772/779）使用该路径。source.copy（445–456）复制配置但不共享已烘焙表。

C：表按 frames × vertices × 16 bytes；当前上限2,000,000
texels，即32,000,000B约30.52MiB。CPU表上传后为fallback继续保留，上传另有staging。10,000顶点×120帧为19.2MB，CPU＋GPU至少约38.4MB，上传时再有约19.2MB
staging，未计其他开销。失败烘焙（236–240）没有失败键记忆，可能再次尝试/警告。

计划：CPU烘焙后台有界且可取消；渲染线程按预算上传；共享不可变payload和GPU资源，实例相位单独保存。当前VertexAnimation.phase可变，不能直接共享整个对象。失败/超预算按输入键记录诊断直到内容变化。

实验：1/10相同源副本，测bake次数、CPU/GPU资源份数、staging/峰值、就绪延迟与关闭回落。CPU_READY与GPU_READY分别显示，不改变当前shader编码协议。

### S12：动画网格身份变化触发基础缓冲重建

C：条件链已核对：

1. AnimatedGltfModelSource.getMesh（337–338）始终meshCache.resolve，包括perParticlePhase；327–334说明编辑预览/发射形状仍需共享CPU姿态。
2. model.DynamicMeshCache.resolve（17–30）在revision变化时topology.withGeometry产生新PhotonMesh包装。
3. renderer.TileParticleRenderer.modelMeshBuffer（369–438）在375检查modelBuiltMesh !=
   mesh；376关闭旧VBO/IBO；393–421重建VBO，425–432重建IBO。
4. ParticleEmitter.extractInstancedTiles（772–774）确实调用该基础模型缓冲路径。

H：动画时间推进造成该mesh身份变化时会重建；实际资产是否走此分支、每帧频率与占比未测。VAT不等于整个链路没有CPU姿态或基础缓冲工作。

计划：拓扑ID与几何revision分别驱动缓存；IBO/UV/静态属性按拓扑复用，位置/法线按revision更新。VAT绘制请求稳定基础拓扑；编辑预览/发射形状仍读取CPU当前姿态，不能全局把getMesh改回rest
pose。

实验：1/10/100实例，CPU动画/VAT与暂停/推进组合；计数pose、mesh包装、VBO/IBO创建与上传字节。固定拓扑推进时目标IBO重建接近0，视觉/发射位置一致。

### S13：CPU fallback 仍按粒子展开模型

C：ParticleRendererSetting.useGPUInstance（108–110）默认false；TileParticleRenderer.renderQueue（52–65）、renderParticle（110–142）逐粒子遍历模型三角形写顶点。putMeshVertex（245–258）CPU
VAT仍逐输出顶点解法线/插值；部分三角形以a,b,c,c输出。

计划：显示实际CPU/实例化后端、回退原因和粒子×渲染顶点估算；兼容材质/布局后选择路线，保留CPUfallback，不批量翻转现有资产默认开关。

实验：同模型/材质/画面分别量测CPU/实例路径的展开顶点、批次、提交、GPU时间和图像。模型少时不能假定实例化必优。

### S14：法线、切线和渲染顶点膨胀

C：GltfMeshParser.readPrimitive（589–610）缺NORMAL走readUnindexed（639–672）展开三角形角点；PhotonMesh.tangents（159–170）可按需生成；AnimatedPose.tangents（97–104）首次分配并重采样，SkinDeformer.deform（67–165）增加切线运算。VAT不烘焙切线（source90–95）；SkinDeformer法线变换（112–115）为3×3乘法，非均匀缩放需进一步正确性对照。

计划：报告源顶点/渲染顶点/索引/展开倍率/缺失属性；准备期生成确实需要的切线。自发光无相关需求时不强制计算；不能以删除作者法线或UV接缝换速度。VAT法线贴图限制明确报告。

实验：有/无法线、切线开关、normal map、非均匀骨骼缩放，分别测准备、运行和视觉。

### S15：姿态共享、可变数组与失效边界

C：AnimatedPose（23–31）按model/clip/speed/loop共享最新姿态，ensurePosed（66–88）同float时间不重算；geometry（91–92）返回内部可变数组。SkinDeformer（6–8）非线程安全。超过16槽时清5秒旧项（56–60），不是硬性16项上限。

C：PhotonMeshCache.invalidate（78–85）一个key失效清全部AnimatedPose。CombinedKey（source270–316）依赖基础模型/外部动画；watcher注册GltfKey（514–516），显式source.invalidate（434–441）另清组合键。

H：文件watcher是否遗留旧CombinedKey尚未运行复现；不得写为已复现资源不更新。只锁求值不能保证读者持有的数组在下一次写入后仍稳定。

计划：冻结采样时间/上下文，双缓冲或不可变发布；按字节/存活淘汰，不默认量化时间降帧。依赖图/generation使组合键与相关pose/bake/GPU局部失效。

实验：同模型同时间多消费者一次deformation，不同时钟隔离；改A外部动画时A更新且B不重准备；撤销/重载/关闭后无旧发布。

### 模型导入能力限制

GltfMeshParser（36–49、825–848）支持GLB与内嵌data URI，拒绝外部bin；577–581仅TRIANGLES；449–455动画接TRS而跳过Morph
weights；856不支持sparse accessor；材质/纹理/Morph不导入；JOINTS_0/WEIGHTS_0为四分量。原生已有STEP/LINEAR/CUBICSPLINE（463–477），不能写成没有曲线插值。

这些是兼容门禁与准备器设计约束，不是要求本轮修改依赖或让用户重做资产。

## 5. 文件事务、保护与诊断工具

### S16：客户端同步文件准备与取消不及时

C：PhotonEffectAutomation.Request.run（79–101）同一客户端任务内prepare并提交；execute（133–163）通过Minecraft.execute排队。util.photon.editor.automation.PhotonEffectFileUtil.prepareSave（62–78）同步校验/复制/编码/回读/两份快照比较。Request.cancel（116–130）可改变准备期状态，但同步工作不会立即停止；准备返回后才检查是否可发布。

计划：客户端捕获稳定文档/参数与revision；可审计的纯编码/校验/I/O交后台；提交前复核revision/目标/控制模式。快照本身若昂贵，需增量文档/分片捕获，不能把活FX传给worker。提交开始后返回真实结果，不伪报取消。

实验：不同规模工程与慢磁盘，测capture/encode/validate/publish。建议准备主线程单段≤4ms，取消后100ms内停止新增处理；均为待校准目标，不保证不可中断的系统I/O立即返回。

### S17：安全保护完整扫描与重复校验

C：util.photon.editor.PhotonEditorProjectGuardUtil.containsFileExtensions（98–125）、containsPackExtensions（128–155）、requireSupportedPack（158–185）同步解压NBT/枚举zip；使用unlimitedHeap，业务遍历深度128不等于解压字节预算。

C：PhotonEffectFileUtil.destination（166–181）、prepareExport（84–131）、publish（225–232）和PhotonPackWriteGuardMixin（19–20）、FXPacksMixin（31–38）、PhotonDefinitionWriteGuardMixin（19–28）可能对同一事务内容重复读取。实际次数由调用路径决定，不写成固定N遍。

计划：增加操作scan-id、bytes-read、解码次数、validation-ns。验证同一不可变内容一次，结果携内容指纹/验证版本；原生UI独立入口仍默认保护。限制解压字节/节点/条目/总内存并可取消；发布前核对目标内容身份，变化必须重新校验，防TOCTOU。

实验：普通包、大包、损坏/高压缩比包、缺失扩展、验证后外部换文件。目标每事务未变化内容完整解码≤1次，发布变更仍拒绝/重验；不得为速度移除保护或只按mtime判安全。

### S18：采集身份与全量快照锁成本

C：util.photon.performance.PhotonPerformanceTraceUtil.knownNode（294–308）、node（382–393）每帧/512项清线程本地缓存；WeakIdentityTable.put（647–649）创建IdentityReference。begin/end（425–456）锁线程buffer；Session.view（813–865）复制节点/上下文/marks并排序，再持锁构造全部聚合。PhotonPerformanceCapture.view（237–241）有250ms节流，不能说每帧全量复制。

计划：线程拥有的有界身份数组/开放寻址，帧末清强引用；会话弱身份仍保留。短锁交换冻结增量、后台生成面板DTO，不能后台遍历正在写入的数组。

实验：512/1024/4096对象，量测弱包装分配、身份查找、writer等待及snapshot成本；建议writer等待P99<0.1ms，UI应用P99<1ms，关闭后无对象滞留。

### S19：隐藏图表和冻结数据仍反复计算

C：client.photon.performance.PhotonPerformancePanel.refresh（252–339）每250ms过滤/排序；新View清路径缓存；277无条件refresh图表。PhotonPerformanceFrameChart.refresh（105–117）调用WindowUtil.chartFrames（138–153）遍历全部帧。绘制已限制128桶，不能误报为绘制全部帧图元。

计划：按session/revision/count/page/filter/sort缓存，隐藏图表不准备，冻结内容不变只算一次；增量桶或worker计算后UI只更新可见行。

实验：大/冻结/实时会话，各页切换与筛选；冻结静止不重复全量排序/分桶，131072帧UI刷新P99<2ms为建议目标。

### S20：采样启动和新线程首次进入的大分配

C：PhotonFrameCapture.Capture（359–374）启动分配131072长度帧数组，约10–11MiB；TraceUtil.Buffer（921–934）每线程32768事件，最多8线程，primitive数组约2.72MiB/线程，未计对象/表/JSON。它们有上限，不是泄漏证据。

计划：采集setup单列，按模式/预计时长准备，分块原始事件受总字节预算控制；overview不无条件使用详细模式全部内存。若提前准备，应将准备成本计入工具启动成本，不能从整条交互链隐藏。

实验：启动、首个worker入采样、扩容/容量停止，测大分配、GC、帧时间；稳定计时阶段不再首次分配整块采样数组。

### S21：结束阶段统计与特殊导出路径

C：PhotonFrameCapture.completeSafely（221–242）先framesSnapshot/summary；summary（684–737）对五组统计复制排序；749–790有frame-only同步JSON路径。QueenBeamPerformanceTrial（382）使用FrameCapture，213调用snapshot。常规PhotonPerformanceCapture.complete（180–190）已有后台导出，不可泛称全部同步。

计划：冻结后后台统计/写盘，状态查询只取轻状态，各入口统一有界导出器；结束回调不等待磁盘写完。

实验：接近帧容量时停止、取消、导出失败，建议客户端停止动作P99<2ms；查询频率与snapshot成本另外采集。

### S22：GPU 采样结果分配与正确口径

C：client.photon.performance.PhotonPerformanceGpu（79–118）每帧最多32对timestamp、256pending；219–241先AVAILABLE，8帧未完成标timeout，没有主动等待GPU。248–269逐查询构建JsonObject，最多8192样本。

计划：量测后决定是否改primitive ring并在导出时编码；保留查询上限、超时、缺测和异常。不能为完整数据阻塞等GPU。

必须保持：

- FrameCapture（53–58）整帧GPU范围是GameRenderer.render，不含tick/extraction/present/swap/limiter；render HEAD间隔另算。
- OwnerUtil.beginDraws（145–163）同RenderType贡献者不一致则owner0，这是保守共享归因，应保留。
- 嵌套Pass/Bloom不求和当总GPU时间，不按顶点/粒子数分摊出虚假的单模型精确GPU成本。
- 报告measurement_scope、inclusive/self、ownership/shared_contributors、measured/unavailable/skipped/timeout与CPU/GPU未校准标志。

实验：GPU采集开关A/B、共享批次、超时/禁用/模式切换；量测采样自身分配，CPU提交与GPU时间分列。

### S23：GPU 资源生命周期需补齐外部所有者审计

C：VertexAnimation有dispose/AutoCloseCleaner（25–35、92–95、113–118）；TileParticleRenderer的VBO/IBO关闭在453–461，本类在模型缓冲重建前调用（375–376）。尚未完成全部持有者追踪。

H：不能据此断言显存泄漏。需要确认关闭/销毁/资源重载和GC清理是否覆盖所有持有者。

计划：建立资源账本：owner、generation、bytes、创建/使用/退休/close原因；唯一所有者负责最终释放，帧快照只借用/持引用；不得提前回收正在延迟绘制的切片。

实验：反复开关工程/移除发射器/退出世界/烘焙异常，配平创建与释放，观察稳定回落。未来可先做100次循环作为压力场景，次数不是当前实测结果。

## 6. 已有合理机制应继续复用

-
util.photon.performance.PhotonPerformanceWindowAnalysisUtil（19–21、43–59、78–103）已有单worker、全局队列8、每面板一个在途和最新目标、revision取消与关闭清理；先测公平性/queue
age，不重复建线程池。
- PhotonPerformanceCapture已有冻结后后台导出与exporting限制。
- TraceUtil已有4096节点、8线程、深度128和原始span预算及容量停止；继续保留原因、丢失、覆盖、未完成数据。
- 原生ParticleEmitter已有条件并行、共享config和姿态缓存；先观察真实复用，不以重写所有基础设施作为默认优化。
- GPU查询已异步检查可用性，共享批次归因保守；不把正确边界当作“统计不够细”而取消。

## 7. 未来量测与实施顺序

### O0：先获得可归因基线

只增加必要计数/关联，不先改行为。复用现有seek/reset/replay、TimelinePlayer与通用采集入口；补基础缓冲创建/上传、加载/拷贝、文件scan-id、工具自身开销。

真实指挥家链与编辑器链分开。冷、热、间歇后触发、多实例、当前内容不支持/拒绝等状态分别记录。已移除类型导致真实资产不能运行时，报告不可用，待R0/R1恢复后再测该样本。

### O1：先处理交互与事务基础

S01–S05、S16–S17：有版本的seek/预览作业、同时间正确去重、真实进度、可取消后台准备、不可变快照和安全扫描。先证明不卡住UI且最终状态正确，不要求先恢复全部建模工具。

### O2：针对量测结果优化模型热路径

S10–S14：加载/烘焙就绪、不可变表共享、稳定拓扑基础buffer、CPUfallback选择、渲染顶点与切线。一次只改一种成本，做相同内容A/B；特别核实S12实际命中率。

### O3：编译时间轴与精确依赖失效

S06–S09、S15：准备执行列表、target索引、快照复用、复制语义、局部资源失效；对mute/录制/控制边界/seed和旧文件往返做语义对照。

### O4：降低检测工具干扰并完善生命周期

S18–S23：稳定身份、短锁冻结、冻结面板缓存、模式化内存、后台结束统计和资源账本。工具开销也在O0先测；若已压过业务成本，提前处理相关项。

## 8. 实验记录模板与退出条件

每次后续实验至少包含：

- 代码/依赖版本、资源内容指纹、工程/实例/请求ID、资源代次、相机/分辨率/画质、种子、线程与模式；
- 假设、单一变化、样本范围、冷/热/间歇分类、失败/缺失/容量标记；
- 原始帧与输入时刻、CPU inclusive/self/等待、实际模拟步、buffer创建/上传字节、GPU有效范围、分配/GC/峰值；
- P50/P95/P99、最大值、长帧次数、最终请求完成率、取消/就绪/发布延迟；
- 图像/状态/文件兼容对照、取消/重载/关闭后的资源回落、回退方法。

不跨会话平均P99，不裁去首次尖峰，不把工具夹具当真实激光输入链；共享CPU/GPU成本不重复相加。各建议毫秒阈值需在硬件基线后校准，不能写成已达成绩。

退出条件：所选假设在真实触发中证实或被排除；优化后有同条件原始证据、语义/画质/兼容无退化；若收益不明显或工作转移到其他线程造成总CPU/内存/输入延迟恶化，撤回或重新设计。

## 9. 本轮结果与边界

完成三路IDEA只读审查与23项候选/基础约束整理。没有运行采样，没有修复实现，不给出“已优化百分比”或“首次卡顿根因已确认”。文档与索引由主代理通过IDEA写入；代码、配置、Shader和资产未在本轮修改。

本轮文档备份与提交范围见[重设计方案第23节](photon-model-redesign.md#23-本轮交付备份与未完成事项)
。后续代码实施、运行实验及是否调整Photon依赖仍需按用户授权范围推进。
