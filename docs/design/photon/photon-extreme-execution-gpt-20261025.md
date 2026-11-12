# Photon 极端优化执行文档（供 GPT 编码代理）

## 2026-10-26 复测阶段实施：动画采样分配治理（A1，进行中）

依据本轮重新取得的证据实施，不是历史计划照抄：

- **对应瓶颈**：`jdk.ObjectAllocationSample` 显示 40-animated-sixteen 渲染线程约 5.6 MiB/帧，其中 94% 落在 `PhotonModelPoseSampler`（`skinMatrices` 38–47%、`positions` 17–18%、`immutable` 10–13%、lambda 流式中间对象 11–13%）；几何写入 0 分配、探针 0.1%。短场景最长帧主要由 GC 停顿解释。
- **代码位置**：`PhotonModelPoseSampler` 的 `sample`/`positions`/`skinMatrices`/`morph`/`faceNormal`。
- **消除的重复工作**：每帧每模型重复创建的 `HashMap<Integer,Vector3>` 与 Integer 装箱、`computeIfAbsent` 捕获 lambda、`byte[]/float[16]`×2、`Matrix4f`/`Matrix3f` 临时对象、`stream().mapToDouble()`、无形状键时的两次整表复制、每面 3 个 `Vector3f`、每角法线与包围盒临时向量。
- **数据所有权、线程边界、失效规则**：全部复用缓冲都是采样器实例私有，只在渲染线程的 `sample()` 调用内使用；不进入 `Frame`/`Group`/`Vertex` 发布对象，不跨帧、不跨实例、不作为缓存键。`boneOverrides`/`partOverrides`/`morphWeights` 语义与求值顺序不变；`Input.equals` 命中路径仍是唯一的姿态复用条件。
- **等价性风险**：顶点位置/法线/UV/颜色/光照的浮点运算顺序保持原样（同一 `sub`/`cross`/`add`/`normalize` 顺序），因此逐顶点属性应逐位一致；发布对象仍是新的不可变 `Frame`，`snapshotFrame` 比较语义不变。透明排序、LOD、材质、动画片段与资源内容均未改。
- **预期收益**：动画 16/32 场景每帧分配显著下降、GC 频率与最长帧下降；平均值不保证同比。**验证**：probe-off/probe-on 配对、`frames.json` 分配与 GC 对照、`PhotonModelPoseSamplerTest` 与几何属性回归、同机位截图。**回退**：本改动是等价重写，无运行时开关；若属性对照或截图不一致，按 Git 差异回退该文件。
- **未做**：GPU 实例化、VAT、阴影跳过、减少动画更新率、修改 LOD/资源。
- **本轮结果（2026-10-26 11:25）**：五轮各 13 场景 PASSED（P0 旧采样器、P2b 新采样器、P1c 探针关、S1 光影探针开、S0 光影探针关）。同相机 P0→P2b：40/90/91/92 分配 −68%/−72%/−69%/−60%，16/32 动画 CPU 均值 −24%/−40%，40/90 均值 −21%/−38%；92 均值未改善（施法 Codec/deepCopy 尖峰）。S1→S0 探针净差最高使 92 均值 −19%、P99 −50%。S0 光影 E2 均值 8.202/P99 16.422 达标，E3 均值 12.332/P99 36.580 与最长 246.5 ms 未达标；主因登记 P19，相机漂移登记 P14/P21。详见 [性能审查复测节](../../records/photon/photon-performance-audit.md#本轮复测结果2026-10-26-1125-完成)。


> 2026-10-26 实际运行收尾：无光影／光影×packed关闭／开启四组各13场景通过，共52项；独立Debug已确认录制当次提交和真实缓存路径。静态reserve/Job 2048→1、顶点不变，但光影相机跨轮位移、阴性控制波动与探针开销使G0／G1仍未通过。动画／混合扩容与长帧仍在，WP1缺省关闭，WP2～WP7未实施。正式采样结束，配置原字节恢复，临时进程／断点已清理。完整实际结果见[最终对照报告](../../records/photon/photon-performance-audit.md#2026-10-26无光影光影四组运行对照与结论修订)，待办只维护[唯一问题清单](../../issues.md)。本页下方默认硬件分辨率是计划值，本轮实际2560×1494。

> 当前会话约束（2026-10-25 后续）：用户已明确授权启动游戏并要求 Sodium＋BenchMod＋Minecraft MCP＋IDEA MCP＋Debug，覆盖无光影与光影；此前禁令已撤销。当前仅实施测试、对照与必要夹具修正，不据授权直接宣布 G0／G1 通过或开始 WP2。测量区间不得有断点暂停，Debug 独立归档。

本文把 [极端场景优化计划](photon-extreme-optimization-plan-20261025.md) 拆成可直接交给 GPT 编码代理执行的工作包。**本文是执行规范，不是新的设计提案**；范围、顺序和验收以本文和计划文档为准。

当前代码基线：`ccc36676`（稳定帧几何快照 `8c2fe84d` 已落地；动画帧仍走原 CPU 写入）。默认硬件/环境：RTX 3060 Laptop、2560×1600、Sodium 0.9.2、Iris 1.11.4、ComplementaryReimagined r5.9.3、Java 25、Photon 26.1.2.3。

## 〇、强制授权声明（GPT 必读）

**用户已明确批准按 WP0～WP7 进行大规模代码改造，这不是“先调研”或“只给方案”的任务。** 你的角色是**执行代理**，不是顾问。

- **必须直接落地**：编辑代码、补测试、更新文档、创建中文提交；完成标准是“代码已改＋测试已跑＋文档已更新＋提交已建”，不是分析报告。
- **不得以“风险”“保守”“需要确认”为由缩小范围**：不得把 WP 降级成微优化，不得只输出计划/建议，不得要求用户二次确认已授权的工作包。
- **范围不可自行砍**：WP 太大就按文档步骤拆成子步骤，但仍必须完成该 WP 的出口；不能停在“第一部分完成”就交差。
- **必须实现回退而不是回避改动**：新路径默认关闭、失败自动回退；不允许因为“可能出问题”就不写实例化/缓存/批次代码。
- 只有遇到真正的硬阻塞（IDEA 不可用、需要改文档未授权的动画/LOD/资源/全局设置、无法保持透明排序/法线等价）才停止，并提交**最小可回退实现＋失败证据＋具体阻塞点**；不要用空报告代替工作。
- 大规模修改是用户明确要求；不要用“建议后续再做”替代本 WP 的实现。

## 一、给 GPT 的硬约束（先读，违反即停止）

1. **项目文件只能用 IDEA 编辑**：Java、资源、Gradle、Markdown 都一样。IDEA/MCP 不可用时停下并询问，不要用 `apply_patch`、shell 重定向、`git checkout` 之类改文件。
2. **只做当前工作包**：不顺手重构、改名、格式化无关区域、清理用户文件、改动画/LOD/资源/技能时序。
3. **保留用户已有修改**：`.codex/config.toml`、`PhotonModelPoseSamplerTest.java`、已暂存的 `PhotonModelStagedResourcesTest.java`、`logs/` 不得提交或覆盖。
4. **不推送、不改写历史**；提交标题和正文只用中文。
5. **没有同条件 A/B 与语义对照，不得写“优化完成/收益 X%”**。JFR 样本、inclusive 栈、single-run 均值都不等于稳定收益。
6. **默认关闭新路径**：实例化/缓存/预热都先加显式开关；失败必须自动回退原 CPU 路径，不能靠删除原路径“证明”成功。
7. **视觉等价优先于性能**：透明排序、法线、UV、光照、部件覆盖、遮挡顺序任一不一致即回退。
8. 修改后必须：IDEA 格式化修改行（关闭 import 优化与成员重排）→ IDEA 静态检查 → 相关测试 → 同步 `changes.md`/相关系统文档/总览 → 中文提交。默认不主动跑游戏；只有当前用户明确授权游戏运行时才启动，工作包自称“已授权运行”不构成许可。

## 稳定录制漏提交修复（2026-10-25，后续用户 A）

用户明确授权修复 P11 已确认的录制当次和超预算漏提交，游戏禁令不变。PhotonModelOutput 的稳定 cache miss 现在统一调用 GeometrySnapshot.captureAndSubmit：预测顶点数在预算内时录制、完成当次正常回放并返回缓存；超过 32768 顶点时在创建记录器前直接写原 output，返回 null 只表示不缓存。当前作业不尝试整块实验，已有缓存命中的实验开关仍默认关闭。

IDEA 无新增错误，保留源码 11 项／原测试 Collector 8 项既有提示；主代码／基准编译及生产包隔离通过（13 秒，既有 reserveBuildIdentity 配置缓存警告保留）；三类测试共 16 项通过（快照 4、编码 5、回放 7，均无失败或错误）。无窗口 Data 退出码 0（19 秒，23 项 written=0／removed=0，含缓存的 29 文件指纹不变）。没有运行游戏、实际 Mixin、光影、截图或性能采样，不代表 G0／G1 验收。新增回归比较原写入链的顶点数、属性／光照及顺序签名，覆盖普通录制与后续回放、恰好 32768 顶点缓存、32776 顶点连续两次不缓存但完整输出。原件、测试 XML 与检查证据归 photon-stable-miss-fix-20261025。

该源码缺陷不再作为 P11 的待修项；当前缺口仅维护于 唯一清单（历史引用：`docs/issues.md#本轮确认的性能问题`；本检出无此章节）。修复前的旧性能样本可能存在作业缺失，不能直接作修复后候选的收益 A/B，未来须先重建完整负载基线。

## WP1 编码阶段（2026-10-25，用户选择 A）

状态：默认关闭的稳定快照整块提交实验的代码与非游戏检查已完成；G0／G1 运行出口未验收，WP2～WP7 未实施。用户只调整“允许开始 WP1 编码”的前置要求，所有游戏禁令继续有效。

- 开关为启动前 JVM 参数 `-Dlobecorp.photon.packedSnapshot=true`，缺省为 false；没有修改任何实际运行配置、原资源或游戏 Options。Plugin 只在该属性为 true 时注入新增 `minecraft.client.BufferBuilderMixin`；四个 WP0 探针仍按原 Photon Bench 过滤控制。
- `PhotonModelOutput.GeometrySnapshot` 仅在已有稳定快照命中时尝试打包回放。编码完成后持有排序前 GC `byte[]`，不复用原生缓冲；每组最多 32768 顶点／1 MiB，所有快照的候选总字节数超过 4 MiB 时拒绝新增包。reset、force remove、clearParticles 丢弃 packed 引用，invalidate／新姿态快照清表亦释放所有权。
- `PhotonPackedVertexUtil` 保持位置／UV 的原始有限浮点、颜色截断、法线 clamp 后截断、光照两段 short 与本机字节序。非有限值／非法计数／超限在分配前拒绝，回原逐顶点链。32 字节中最后一字节是 padding，打包置零，不作为视觉属性对照。
- `IPhotonPackedVertexSink` 只借读字节。Mixin 全部预检发生在 reserve 前：exact BufferBuilder、新建未写入、building、QUADS、Photon 专用格式同一对象、mask 0x37、初始待填 mask 0x36、32 槽偏移和载荷预算。通过后只做一次 reserve＋memCopy，再更新顶点元数据；此后异常直接终止原作业，不在已推进的缓冲上补写旧链。
- 原生 Emitter.bakeJob 的 build、每次视图 sortQuads、作业顺序及关闭责任继续保留；没有调整 64 KiB 初始容量。这里减少的是候选路径的逐顶点预留调用，是否减少实际扩容或提高帧时间仍是待验证假设。
- 当前 Iris 1.11.4 对该 Photon 格式不进入其指定格式扩展分支，是源码事实；它不证明所有 Mixin 组合等价。Sodium 嵌套实现未能由当前 IDEA 工具展开，真实光影、透明交叠、正常绘制与收益均未测试。
- 实际检查：IDEA 静态无新增错误；主代码／基准编译和生产包隔离通过（23 秒，保留既有 reserveBuildIdentity 配置缓存警告）；新增 5 项编码／7 项回放测试及原快照 1 项共 13 项通过，失败／错误均为 0。无窗口 Data 退出码 0（21 秒，23 项 written=0／removed=0，含缓存的 29 文件指纹不变）。这些不覆盖真实 Mixin 应用、Sodium／Iris 组合、GPU、视觉或性能收益，未运行游戏。三份测试 XML、编译／Data 日志与前后指纹已保存至 photon-extreme-wp1-20261025；过滤测试会替换前一次 XML，已在每次后单独归档。
- 本 WP1 编码阶段仅登记稳定录制缺口，没有擅自修改；之后用户再次选择 A 授权修复，实际结果见上方修复节，不将空提交视为提速。

## 本轮实施记录（2026-10-25）

**状态：WP0 代码已落地，运行出口未验收；WP1～WP7 未实施。** 当前用户禁止游戏测试，未启动 Client、BenchMod 客户端、Minecraft MCP 控制、截图、施放或性能采样。G0 要求可重复的实测，不能以静态检查通过替代；按依赖顺序暂不进入 WP1。主代码／基准编译、生产包隔离、3 项 JUnit 及无窗口 Data 已通过；静态无新增错误。实际检查与提交见 [总览](../../records/photon/photon-workspace.md#当前任务进度) 和 [修改记录](../../records/changes.md)，当前问题只维护于 [唯一清单](../../issues.md)。

- 复用 `PhotonModelRenderScenario`，保留旧 0／1／16 模型的网格和骨骼动画；增加 32 静态、32 动画、E2（16 静态＋16 动画＋1 束）、E3（32 静态＋32 动画＋4 束）及极端后空基线，共 13 场景。网格密度可配置，模型数上限 64、网格边长上限 64。
- `QueenBeamPerformanceFixture` 共用真实女皇技能创建／施放／清理。E3 女皇沿 X 间隔 6 格，靶位于同 X、Z+14～17，每次施放前仅对本夹具靶回位、清速度；施放中保留真实伤害和击退。有效生命上限为 1024，未设置无效的 4096，也不改属性上限。实际像素交叠、存活与漂移均未验收，报告明确 `overdraw_verified=false`。
- 取消／超时清理先登记创建 future 的迟到完成回调，再有界等待；即使创建尚未完成，仍由原服务器安排清理。超时或服务器停止明确报告未确认，不把排队当作已清理。
- E2/E3 主帧采集要求自然完成 90 秒及 4／31／58 秒三次真实施放，检查各女皇阶段 0→1→2→移除。主采集结束后另施放一次，仅用于 OUTPUT 持续两渲染帧后的即时截图，请求和完成均核对阶段；该验证属于 Bench 测量阶段，但不纳入主 90 秒帧文件。
- `PhotonBenchCapture` 保留原始帧、GPU completed／pending 跳过／busy／取消／错误，不填零缺测；GPU 无有效结果或采集提前停止直接失败。实际相机矩阵／位姿／FOV、framebuffer／窗口尺寸、刷新率及变化次数写入 `measure_submissions`；seed 在显式基准中按对象设置顺序控制，跨运行确定性仍为未验证，`wp0_cross_run_gate_verified=false`。
- `ByteBufferBuilderMixin` 分开记录 reserve 调用与真正 resize、容量及字节；`EmitterMixin` 分开记录 CPU／实例作业。GL draw 仅计成功返回的 `GlCommandEncoder.drawFromBuffers`，范围为 Photon 私有 drain 内（包括其中的后处理），不涵盖 public drain 返回后的 finishLayer／晚期合成，也不代表像素过绘或 GPU 完成量。嵌套 CPU 计时不可相加。
- 提交诊断显式绑定主帧采集，既有 GameRenderer HEAD 顺序先结束帧采集、再结束计数，避免自然截止后的渲染继续记入；新增 running() 只读入口避免每帧创建 JSON。该边界已静态核对，实际帧数对齐仍未运行验收。
- 四个新增高频探针经 `PhotonBenchMixinPlugin` 限制为显式 `modBench.scenarios=lobecorp.photon-*` 才注入；普通游戏不注入这些包装。既有帧／drain 入口在诊断关闭时立即返回；探针成本仍需将来的开关对照，不能当作零。
- 隔离环境准备只在 `-PphotonBench=true` 的 prepare 任务执行：复制原光影 ZIP 字节、写该 build 目录的 Iris 配置并关闭其中 spark 后台 profiler。世界修改前核对真实存档路径必须为当前隔离客户端 saves 的直接子目录，拒绝外链；冻结时间／天气／自然刷怪并设 NORMAL，避免和平难度移除 Husk。这些规则作用于该隔离服务器，不是维度私有规则。**本轮没有执行准备任务或修改实际世界／用户 Options。**

### 方案前提修正

确认事实：旧 16 静态 JFR 的 30.94% 为 112／362 个 Java self 样本，命中 `ByteBufferBuilder.reserve` 的加法／预留调用，不能转换成扩容比例。当前 16×16 网格产生 2048 个提交顶点，按原格式 32 字节恰为 65536 字节，等于原生 bakeJob 的 64 KiB 初始容量；因此“每帧反复扩容”尚无证据，可能根本没有 resize。GPU `PhotonBufferCache` 已复用 ring，与 CPU staging 预留不是同一层。

支持的推断：逐顶点预留／写入仍可能有 CPU 成本。待验证假设：不可变最终顶点的整块提交能否降低该成本，须先取得真实 reserve／resize／格式计数与 Iris 等价证据。WP1 不应直接增大容量或把 GPU ring 复用写成新修复。

后续实例化的静态约束：现有合成动画含根骨骼权重，不能用于证明“无权重纯 TRS”路径；需要另加明确的 TRS 变体。原生 MODEL 法线仅旋转、CPU 法线量化与 ObjectPosition／QUADS 排序也有差异；同一 emitter 多组重复使用同变体 ring 会覆盖排队数据。它们是 WP2／WP3 的实现前提，不是已经修复或已经证明无法实现。

## 二、总任务表

按依赖顺序执行；当前用户 A 只允许 G0 未验收时提前写默认关闭的 WP1 代码，不代表跳过运行验收，也不允许提前进入 WP2。每个 WP 的代码阶段单独提交，提交信息注明 WP 编号与未验收边界。

| WP | 对应计划 | 目标 | 规模 | 出口 |
|---|---|---|---|---|
| WP0 | G0/P0 | 固定测量条件，补 E2/E3 极端夹具 | 中 | 可重复测到当前瓶颈 |
| WP1 | G1/P3 | 提交缓冲预留／扩容实证治理（`ByteBufferBuilder.reserve`） | 中 | 16 静态 buffer 热点下降，视觉一致 |
| WP2 | G2/P1 | 单部件动画实例化 spike（原生 MODEL） | 大 | 单部件逐顶点等价＋CPU 下降 |
| WP3 | G3/P1 | 多部件/多材质/回退与切换 | 大 | E1/E2 达标或安全回退 |
| WP4 | G4/P2 | 多光束同材质批处理 | 大 | E3 draw/CPU 下降，排序正确 |
| WP5 | G5/P4 | 阴影/Iris 对象级分层（仅测量证据支持时） | 中 | A/B 有收益且视觉一致 |
| WP6 | G6/P5 | 参数/命名计划与首帧预热 | 中 | 首帧下降且不转移卡顿 |
| WP7 | G7/E4 | 重载、取消、失焦、资源账本 | 中 | 无泄漏、无累积尖峰 |

### 公共验收矩阵（所有 WP 适用）

| 项 | 要求 |
|---|---|
| 测试 | 相关 JUnit 通过；IDE Gradle 运行配置或等价命令记录结果 |
| 语义 | 逐顶点属性对照＋截图＋透明交叠用例；不能只看截图“能看” |
| 性能 | 同机、同光影、同相机、同 seed；至少 3 次施放；报告均值/P95/P99/最长，不只均值 |
| 工具 | JFR（ExecutionSample/NativeMethodSample/Allocation）＋GPU 查询＋draw/buffer 计数；记录工具开销 |
| 回退 | 开关关闭即恢复原路径，原始代码路径不删除 |
| 文档 | 更新 `changes.md`、相关系统页、`docs/issues.md` 状态；写明未验证边界 |
| 提交 | 只暂存本 WP 文件；中文提交；不推送 |

## 三、WP0：固定条件与 E2/E3 夹具（先做，不改渲染）

### 目标
让“极端”可重复测量：E2（32 模型＋激光）、E3（64 模型＋4 束）能稳定跑，并记录刷新率、相机、帧地板、buffer 扩容、首帧阶段。

### 已存在入口
- 场景注册：[LobecorpBenchProvider](../../../src/bench/java/org/unitego/lobecorp/registry/bench/LobecorpBenchProvider.java)
- 模型负载：[PhotonModelRenderScenario](../../../src/bench/java/org/unitego/lobecorp/client/bench/PhotonModelRenderScenario.java)
- 报告校验任务：`build.gradle.kts` 的 `verifyBenchClientReport.expectedScenarioIds`

### 执行步骤
1. 在 `PhotonModelRenderScenario` 增加可配置的模型数量/网格密度，不复制渲染器；优先复用现有 `count`/`MODEL_GRID`，避免新增一套夹具。
2. 在 `LobecorpBenchProvider` 注册 `lobecorp.photon-80-static-thirtytwo`、`lobecorp.photon-90-animated-thirtytwo`（E2 最小）和 E3 所需的 64 模型/多光束场景；命名保持 `lobecorp.photon-*` 过滤规则。
3. 在 `build.gradle.kts` 的 `expectedScenarioIds` 与 `artifacts` 列表中补齐新场景，否则 `verifyBenchClient` 会失败。
4. 记录并在报告中输出：实际 framebuffer、display refresh、相机位姿、seed、`ByteBufferBuilder` 扩容次数、首帧阶段、GPU 有效/缺测数。
5. 仅在用户另行明确授权游戏后，用以下入口跑一次**夹具验证**（不是优化验收）；当前禁止执行：
   ```text
   IDEA 运行配置：BenchMod Photon Prepare（-PphotonBench=true）
   然后：BenchMod Photon（-PphotonBench=true）
   ```
   若 IDEA 不可用，按项目规范停下询问；不要用命令行改构建文件。
6. 输出 `summary.json`、`report.md`、JFR 与截图；把“当前地板/波动”写入 `changes.md`，不要把夹具验证当收益。

### 出口
- E2/E3 场景通过 BenchMod（PASSED），阶段齐全，截图可见目标。
- 能指出当前 32/64 实例下 CPU/GPU/buffer/首帧各自的量级。
- 若 EI 帧地板仍不稳定，先修测量条件，不进入 WP1。

## 四、WP1：提交缓冲扩容治理

### 目标
定位 16 静态 JFR 的 `ByteBufferBuilder.reserve` self 热点（30.94%）。先区分逐顶点预留与实际扩容，再按真实成本选择整块提交或容量策略；现有样本不证明每帧扩容。

### 先读
- [PhotonModelOutput](../../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) 的 `bakeBatches`/`bakeModelGroup`/`GeometrySnapshot`
- Photon `PhotonBufferCache.write(List, int)`、`PhotonWorldRenderState` 的 draw/batch 路径
- 现有 JFR 证据：`build/modBench/raw-results/default/client/artifacts/jfr/lobecorp.photon-30-static-sixteen.jfr`

### 执行步骤
1. 先用 JFR 确认 `reserve` 的调用者、每帧扩容次数、实际容量与峰值；不要凭名字直接改。
2. 若瓶颈是 Photon 内部共享 `ByteBufferBuilder` 的容量：优先在**本项目自己的提交入口**预热/复用容量，或在 Photon 侧加最小 Mixin（记录版本与签名）；不改所有渲染路径。
3. 对稳定帧快照/实例缓冲设置显式上限（已有 `MAX_SNAPSHOT_VERTICES` 可参考），超限回退原路径。
4. 不做“全局缓存所有 VertexConsumer”“跨帧持有可变缓冲”的危险方案；缓冲只能由渲染线程与原生队列持有。
5. A/B：同一场景开/关缓冲复用，报告 `reserve` 样本、扩容次数、CPU/P99、视觉截图。

### 出口
- 16 静态 `ByteBufferBuilder.reserve` 样本明显下降（≥30% 目标，实测为准），16 动画不退化；
- 无跨帧可变数据、无关闭时泄漏；
- 失败则回退并记录“为什么不能复用”。

## 五、WP2：单部件动画实例化 spike（核心，先不做多部件）

### 目标
用 Photon 原生 `MODEL` 实例化路径替换**单部件、单材质、纯 TRS 动画**的逐帧 CPU 写入；这是大型改造的最小验证，不允许一上来改所有模型。

### 先读（必须）
- [极端场景优化计划](photon-extreme-optimization-plan-20261025.md) P1
- Photon 源码：`Emitter.bakeInstancedGroup`、`Emitter.BaseMesh`、`TileParticleRenderer.fillInstancesModel`/`modelMeshBuffer`、`PhotonInstancedDrawState`、`PhotonPipelines.InstancedVariant`
- 当前 `PhotonModelOutput` 的 `sampleLocalFrame`/`bakeBatches`/`bakeModelGroup` 与 `PhotonModelPoseSampler`
- [架构与 Mixin 方案](../../records/photon/photon-render-architecture-mixin-20261024.md) 的 M2/M3

### 执行步骤
1. 在 `PhotonModelOutputConfig` 增加**默认 false** 的开发开关（例如 `gpuInstancedModel`），并持久化；不要改名已有字段。
2. 只对满足全部条件的输出走新路径：单部件、无顶点级形变（无骨骼权重/morph）、材质组可实例化、平台/Photon 版本支持。
3. 构造一次性的 `BaseMesh`：顶点/索引/UV/法线/切线按原生实例化格式；索引按原生三角形/四边形要求；材质仍用 `PhotonModelMaterials.group(i)`。
4. 每帧只填实例缓冲：`iPos/iScale/iRot/iColor/iLight`（原生 `MODEL`），使用 `Emitter.InstanceFiller`；不要为单实例伪造多个粒子。
5. 通过 `bakeInstancedGroup(...)` 提交；失败时（drawInfo 缺失、布局不匹配、附加数据不满足）返回 false，自动回退原 CPU 写入。
6. 增加逐顶点等价对照测试：同一帧分别走 CPU 与实例化路径，比较最终顶点位置/法线/UV/颜色/光照（不要求实现完全同构，但必须可解释）。
7. 只在 `photon-20-animated-one` 与 `photon-40-animated-sixteen` 做 A/B；先单部件，不碰多部件。

### 出口
- 单部件动画的 CPU `write` 样本明显下降；GPU 不退化；截图/透明交叠一致；
- 开关关闭时与基线逐帧一致；
- 出现任何排序/覆盖异常立即回退并记录。

## 六、WP3：多部件、多材质与回退

### 目标
把 WP2 从单部件扩展到真实模型；这是“大规模”部分，必须先通过 WP2 等价性门槛。

### 执行步骤
1. 按部件×材质拆分 BaseMesh；实例数为部件数；每实例只传该部件的 TRS 与颜色/光照。
2. 处理部件父子变换：部件 TRS 必须与 CPU 路径的父子矩阵结果一致；不能用“父矩阵近似”。
3. 多材质组按现有 `groupOrder` 与透明排序提交；overlay 材质顺序保持原语义。
4. 建立**逐组回退**：某组不满足实例化条件时只回退该组，不能整模型混用导致排序错乱。
5. 覆盖测试：部件覆盖、镜像/非均匀/奇异缩放、双面/insideOut、透明交叠、骨骼/morph 存在时跳过实例化。
6. A/B：E1（真实光影激光）与 E2（32 模型）；报告 CPU/GPU/P99/首帧，不只均值。

### 出口
- E1/E2 窗口与 P99 达到计划目标或明确不达标；
- 任一视觉回归可定位到组并回退；
- 不能把“只对静态模型有效”写成“激光已优化”。

## 七、WP4：多光束与同材质批处理

### 目标
E3 的多束/多层光束叠加不再放大 draw call 与状态切换。

### 执行步骤
1. 统计当前多束的 draw call、材质切换、overdraw；不同材质的透明层不强行合并。
2. 同材质光束优先合并为实例批次；每实例只传 start/end/width/color/UV（原生 `BEAM` 路径）或按 WP2/3 的网格实例。
3. 评估原生 `BeamEmitter` 的外观差距；若外观不满足，保留网格方案，不为性能改视觉。
4. 排序/遮挡：透明光束按原排序语义；不得以“合并”为由改变遮挡顺序。
5. E3 A/B：draw call、CPU、GPU、截图；出现排序错误立即回退。

## 八、WP5：阴影/Iris 分层（只在证据支持时）

### 目标
回答“阴影 pass 里 Photon 到底提交了什么、占多少”，而不是直接跳过。

### 执行步骤
1. 用 `IrisCompat.isShadowPass()` 在现有 Mixin 入口做对象级计数/计时，区分模型、粒子、Bloom、遮挡管线；不改变提交。
2. 记录阴影开启/关闭、Iris 开/关、不同光影包三组对照。
3. 只有某类对象在阴影 pass 无贡献且成本显著时，才加对象级开关；**不做全局 drain skip**。已有实测：模型输出跳过 8.528 ms、drain 跳过 8.866 ms，均差于基线 8.158 ms，已回退。
4. KilaGraph 的 ENTITY/UV1 路由只在 A/B 不退化时考虑；不把 Missing program 压日志当修复。

## 九、WP6：参数、命名与首帧

### 目标
消除每帧 Map/List/字符串求值和首发编译的稳定成本；预热成本单独报告。

### 执行步骤
1. 复用/预编译 `PhotonNamedMixer`、`PhotonModelBindings`、参数轨迹的执行计划；只在内容版本变化时重建。
2. 首次使用前把 BaseMesh、VAT、材质管线、纹理准备好；记录预热耗时与内存。
3. 预热不得把成本静默转给进世界/技能前摇；E4 重载/取消要有释放。
4. 验收：首发长帧下降，稳定窗口不退化，参数语义/随机源/循环/mute 与基线一致。

## 十、WP7：E4 稳定性与资源账本

- 重载资源、取消技能、切世界、失焦恢复、快速相机移动各 3 次；记录 GPU/CPU 对象创建/释放、快照/实例缓存回落、长帧次数。
- 不允许缓存无限增长；每个 effect 输出有上限并在销毁/重载时清理。
- E4 只要求不崩、不泄漏、无累积长帧；首帧与预热单独列，不混入稳定均值。

## 十一、给 GPT 的单工作包提示词模板

每个 WP 单独开一次会话，使用下面模板（把尖括号替换成实际 WP）：

```text
你在 Lobecorp Photon 工作区执行 <WP 编号/名称>。你是执行代理，不是顾问；用户已明确批准大规模修改，禁止只给方案、禁止以风险为由缩小范围、禁止要求二次确认、禁止把 WP 降级为微优化。必须直接编辑代码、补测试、更新文档并创建中文提交。

先读取：
- docs/design/photon/photon-extreme-optimization-plan-20261025.md
- docs/design/photon/photon-extreme-execution-gpt-20261025.md（本文件）
- <本 WP 的“先读”文件清单>

约束：项目文件只能用 IDEA 编辑；只改本 WP 范围；保留用户已有修改；不推送；新路径默认关闭、失败自动回退；没有同条件 A/B 不写收益。
步骤：严格按本文件对应 WP 的“执行步骤”和“出口”执行；WP 太大就拆子步骤，但必须完成出口。每完成一步报告：改了哪些文件、为什么、如何验证、还剩什么未验证。
结束：IDEA 格式化修改行、静态检查、相关测试；更新 changes.md/相关系统文档/总览；中文提交；最后给出“已做/未做/风险/下一步”。完成不了也必须提交最小可回退实现＋失败证据，不能只写分析或建议。
```

## 十二、完成定义与停止条件

- **完成**：WP 出口全部满足；同条件 A/B 有数据；视觉/语义对照通过；开关关闭可回退；文档/提交齐全。
- **停止并报告（不要自行扩大或缩小范围）**：IDEA 不可用、需要改第三方/资源/动画/LOD、需要关闭阴影或降画质、无法保持透明排序/法线等价、测试夹具无法稳定。此时提交最小可回退实现＋失败证据＋具体阻塞点，不要只交分析。
- **不允许**：用单轮均值承诺收益、把 JFR inclusive 相加、删除原路径、把“没采到”当“没有成本”、把 E4 异常帧当稳定值。

## 十三、参考

- [极端场景优化计划](photon-extreme-optimization-plan-20261025.md)
- [架构与 Mixin 方案](../../records/photon/photon-render-architecture-mixin-20261024.md)
- [性能审查](../../records/photon/photon-performance-audit.md)
- [唯一问题清单](../../issues.md)
- 原始证据：`run/client/performance/laser_shader_base_20261025_*`、`laser_shader_snapshot_20261025_*`、`build/modBench/raw-results/default/client`、`build/modBench/baseline-20261025`
