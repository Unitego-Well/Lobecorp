# Photon 极端优化执行文档（供 GPT 编码代理）

本文把 [极端场景优化计划](photon-extreme-optimization-plan-20261025.md) 拆成可直接交给 GPT 编码代理执行的工作包。**本文是执行规范，不是新的设计提案**；范围、顺序和验收以本文和计划文档为准。

当前代码基线：`ccc36676`（稳定帧几何快照 `8c2fe84d` 已落地；动画帧仍走原 CPU 写入）。默认硬件/环境：RTX 3060 Laptop、2560×1600、Sodium 0.9.2、Iris 1.11.4、ComplementaryReimagined r5.9.3、Java 25、Photon 26.1.2.3。

## 一、给 GPT 的硬约束（先读，违反即停止）

1. **项目文件只能用 IDEA 编辑**：Java、资源、Gradle、Markdown 都一样。IDEA/MCP 不可用时停下并询问，不要用 `apply_patch`、shell 重定向、`git checkout` 之类改文件。
2. **只做当前工作包**：不顺手重构、改名、格式化无关区域、清理用户文件、改动画/LOD/资源/技能时序。
3. **保留用户已有修改**：`.codex/config.toml`、`PhotonModelPoseSamplerTest.java`、已暂存的 `PhotonModelStagedResourcesTest.java`、`logs/` 不得提交或覆盖。
4. **不推送、不改写历史**；提交标题和正文只用中文。
5. **没有同条件 A/B 与语义对照，不得写“优化完成/收益 X%”**。JFR 样本、inclusive 栈、single-run 均值都不等于稳定收益。
6. **默认关闭新路径**：实例化/缓存/预热都先加显式开关；失败必须自动回退原 CPU 路径，不能靠删除原路径“证明”成功。
7. **视觉等价优先于性能**：透明排序、法线、UV、光照、部件覆盖、遮挡顺序任一不一致即回退。
8. 修改后必须：IDEA 格式化修改行（关闭 import 优化与成员重排）→ IDEA 静态检查 → 相关测试 → 同步 `changes.md`/相关系统文档/总览 → 中文提交。默认不主动跑游戏；只有工作包明确写“已授权运行”时才启动。

## 二、总任务表

按依赖顺序执行，不要跳过前置门槛。每个 WP 完成后单独提交，提交信息注明 WP 编号。

| WP | 对应计划 | 目标 | 规模 | 出口 |
|---|---|---|---|---|
| WP0 | G0/P0 | 固定测量条件，补 E2/E3 极端夹具 | 中 | 可重复测到当前瓶颈 |
| WP1 | G1/P3 | 提交缓冲扩容治理（`ByteBufferBuilder.reserve`） | 中 | 16 静态 buffer 热点下降，视觉一致 |
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
- 场景注册：[LobecorpBenchProvider](../../src/bench/java/org/unitego/lobecorp/registry/bench/LobecorpBenchProvider.java)
- 模型负载：[PhotonModelRenderScenario](../../src/bench/java/org/unitego/lobecorp/client/bench/PhotonModelRenderScenario.java)
- 报告校验任务：`build.gradle.kts` 的 `verifyBenchClientReport.expectedScenarioIds`

### 执行步骤
1. 在 `PhotonModelRenderScenario` 增加可配置的模型数量/网格密度，不复制渲染器；优先复用现有 `count`/`MODEL_GRID`，避免新增一套夹具。
2. 在 `LobecorpBenchProvider` 注册 `lobecorp.photon-80-static-thirtytwo`、`lobecorp.photon-90-animated-thirtytwo`（E2 最小）和 E3 所需的 64 模型/多光束场景；命名保持 `lobecorp.photon-*` 过滤规则。
3. 在 `build.gradle.kts` 的 `expectedScenarioIds` 与 `artifacts` 列表中补齐新场景，否则 `verifyBenchClient` 会失败。
4. 记录并在报告中输出：实际 framebuffer、display refresh、相机位姿、seed、`ByteBufferBuilder` 扩容次数、首帧阶段、GPU 有效/缺测数。
5. 用以下命令跑一次**只作夹具验证**（不是优化验收）：
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
处理 16 静态 JFR 的新首位 `ByteBufferBuilder.reserve`（30.94%），让固定拓扑的提交缓冲不再每帧按峰值扩容。

### 先读
- [PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) 的 `bakeBatches`/`bakeModelGroup`/`GeometrySnapshot`
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
- [架构与 Mixin 方案](photon-render-architecture-mixin-20261024.md) 的 M2/M3

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
你在 Lobecorp Photon 工作区执行 <WP 编号/名称>。先读取：
- docs/systems/photon-extreme-optimization-plan-20261025.md
- docs/systems/photon-extreme-execution-gpt-20261025.md（本文件）
- <本 WP 的“先读”文件清单>

约束：项目文件只能用 IDEA 编辑；只改本 WP 范围；保留用户已有修改；不推送；新路径默认关闭、失败自动回退；没有同条件 A/B 不写收益。
步骤：严格按本文件对应 WP 的“执行步骤”和“出口”执行。每完成一步报告：改了哪些文件、为什么、如何验证、还剩什么未验证。
结束：IDEA 格式化修改行、静态检查、相关测试；更新 changes.md/相关系统文档/总览；中文提交；最后给出“已做/未做/风险/下一步”。
```

## 十二、完成定义与停止条件

- **完成**：WP 出口全部满足；同条件 A/B 有数据；视觉/语义对照通过；开关关闭可回退；文档/提交齐全。
- **停止并询问**：IDEA 不可用、需要改第三方/资源/动画/LOD、需要关闭阴影或降画质、无法保持透明排序/法线等价、测试夹具无法稳定。
- **不允许**：用单轮均值承诺收益、把 JFR inclusive 相加、删除原路径、把“没采到”当“没有成本”、把 E4 异常帧当稳定值。

## 十三、参考

- [极端场景优化计划](photon-extreme-optimization-plan-20261025.md)
- [架构与 Mixin 方案](photon-render-architecture-mixin-20261024.md)
- [性能审查](photon-performance-audit.md)
- [唯一问题清单](../issues.md)
- 原始证据：`run/client/performance/laser_shader_base_20261025_*`、`laser_shader_snapshot_20261025_*`、`build/modBench/raw-results/default/client`、`build/modBench/baseline-20261025`
