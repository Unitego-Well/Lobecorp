# 当前性能静态审查与优化计划

## 2026-10-26 复测与瓶颈复核（进行中，未验收）

本节记录本轮“重新审查结论→修正测试条件→重测→定位瓶颈→实施优化”的进行中状态；所有数字在正式重测完成前都不得当作收益。台账只维护在 [唯一问题清单](../../issues.md)。

### 授权、环境与固定约束

- 用户已明确授权启动游戏、构建、测试与 Debug；正式测量区间不设断点。Sodium 在全部轮次保持加载，不参与对照开关；每轮客户端按 BenchMod 正常退出，测试结束关闭客户端时等待 Sodium 退出输出后再结束进程。
- 允许清理能证明属于本夹具的对象；归属不明实体只登记，不删除。世界、玩家位姿、窗口、光影、负载、代码版本与实验开关须逐轮记录。
- 本轮证据根：`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026`；旧证据根 `.../2026/10/23/01a1669e-.../photon-runtime-comparison-20261025` 只作有条件历史对照。

### 已确认事实（代码与运行证据）

1. **首次门禁轮无效，不计入任何对照。** 运行 `client-1792980111074`（2026-10-26 10:01:51～10:02:11）13 个场景全部 FAILED：`IllegalStateException: 实际光影状态与本轮预期不符：false`。原因是 `verifyBenchClient` 重跑了 `prepareBenchClientOptions`，把已改为 `enableShaders=false` 的 `config/iris.properties` 又写回 `enableShaders=true`。已改为 `-PphotonShader=<bool>` 由构建参数写入隔离配置，待重跑验证。
2. **渲染路径事实（经 IDEA 库源码核对）：** `PhotonWorldRenderState#drain` 每视图每帧只 bake 一次；Bloom 每 drain 一次；顶点上传每 drain 一次（`PhotonBufferCache#write` 仅在容量不足时重建）；framebuffer 切换为每 run 一个 `RenderPass`（`drawRun:931`／`createRenderPass:962`）；`Emitter#bakeJob:733` 每材质每视图新建 64 KiB `ByteBufferBuilder`。
3. `Emitter#bakeJob/build` 不做缓存、逐帧重建；`sortQuads` 只在 `SortMode.DISTANCE` 执行，`RendererSetting` 默认 `NONE`（`SortMode#sortOrigin:71`），因此排序不是当前默认开销点。
4. `PhotonModelOutput#sampleLocalFrame:383` 每帧每模型构造 `Input`（3 次 `Map.copyOf`）、多次 `settings()` 与字符串槽查表；稳定帧快照 `matches:664` 包含 `world`（含相机平移），相机变化即失效。
5. `PhotonModelClipUtil#clear:44` 在每次 `apply` 中遍历 runtime 全部对象做 map 删除（每帧 entries×objects）；`PhotonChildSpawner` 每个子实例 `createRuntime(true)` 整份深拷贝并经 `PhotonSpawnTemplateUtil:110-155` 递归过滤，单槽 prepared 只省一次。
6. `IrisCompat#isShadowPass` 全库唯一引用是 `IrisDiagnostics:28`；`PhotonFXRenderState#submit:29-38` 对非 `IPhotonFXCollector` 的 collector（影子 pass）整体跳过。**Photon 对象级没有可省影子提交点**，光影 shadow 成本属 Iris 自身。
7. Sodium 0.9.2 在 26.1 为 GpuDevice/CommandList 实现，不提供外部 pass 的实例化捷径；Photon 原生实例化是 `Emitter#bakeInstancedGroup:561`（base mesh + 每实例记录 + 排序 + VAT 需 `PhotonData`），而 `PhotonModelOutput` 完全绕过它（override `bakeBatches:442`→CPU 四边形）。
8. **退出 GUI 断连异常**在本轮 FAILED 轮同样出现（10:02:12.374 `Trying to return to in-game GUI during disconnection`），与场景成败无关，属客户端关闭生命周期；保留待归因，不计入性能。

### 证据支持的推断与优化候选（未实施，按重复量×证据×风险排序）

1. 默认启用已实现的稳定快照整块提交（需先证明透明排序/动态覆盖/光影等价，且当前 `matches` 含相机 `world`）。
2. `PhotonModelClipUtil#clear` 改为定向清理，避免每帧遍历全部对象。
3. 池化每材质每视图的 64 KiB builder（需核对原生所有权与释放）。
4. 合并同目标/同材质的 run 为单 `RenderPass`。
5. 无混合时跳过 seed 拷贝；`Input` 快路径减少 `Map.copyOf`／槽查表。
6. 命名计划按内容 revision 预编译；子发射模板/定义分离复用。

### 测量夹具修正（本轮已改/待验证）

- 已改 `build.gradle.kts`：`-PphotonShader=<bool>` 决定隔离 Iris 配置，避免 prepare 覆盖手改。
- 已改探针对照开关 `-Dlobecorp.photon.bench.probes=<bool>`：为 false 时 `PhotonBenchMixinPlugin` 不注入四个高频提交探针；`PhotonBenchCapture` 在关闭时不启用提交诊断用例并记录开关状态。
- 待做：相机位姿显式门禁与跨轮指纹（不再只依赖 `holdPose`）、失焦/恢复计数与自动还原、实体普查与 tag 归属清理、主采集与验证截图的时间戳分离。

### 旧结论逐条复核（截至当前证据）

| 旧结论 | 复核结果 | 证据与修正 |
|---|---|---|
| 16 静态 reserve/Job 2048→1、调用减少 99.951%、顶点数不变 | **不能作为收益证据，需修正** | 旧 baseline 的 `reserve_calls` 数值等于 `cpu_vertices`（submissions×2048），candidate 的 `reserve_calls` 等于 `buffers_created`，两个构建的计数器口径不一致，不能用它计算调用减少比例。更关键的是整块路径只在稳定快照命中时启用，而 `GeometrySnapshot.matches` 的 `world` 含 `-renderOrigin`（相机位置）：相机移动即逐帧失效（[PhotonModelOutput](../../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) 的 `bakeBatches`/`matches`）。Bench 相机固定才会命中，真实游戏常态基本不命中。顶点数不变成立。 |
| 纯静态 resize=0，混合激光有真实扩容 | **成立** | 30/40 场景 `resize=0`；91/92 旧轮 2330～6172、本轮 2386/3116 次 `ByteBufferBuilder.resize`。它是提交缓冲扩容次数，不是窗口/framebuffer resize。 |
| 动画仍逐顶点提交，渲染线程分配约 5.6～6.6 MiB/帧 | **成立，需补充归因** | 旧轮 5.60/6.58 MiB/帧；`jdk.ObjectAllocationSample` 归因：94% 在 `PhotonModelPoseSampler`（skinMatrices 38–47%、positions 17–18%、immutable 10–13%），几何写入 0 分配，探针 0.1%；不是模型独占全部、不是存活量或泄漏。 |
| 光影两轮相机 Y＋7／Z－10 | **事实成立，根因修正** | 该轮 `player_pose` 等于 `holdPose` 前的位置、相机为未应用 hold 的姿态；即 `holdPose` 静默未生效，不是夹具漂移或世界保存导致。新夹具改为显式 `setPose`＋逐帧 `pose`/相机指纹核对：本轮 13 场景 `pose_gate_verified=true`、`camera_audit_changes=0`。 |
| observeReserve／observeBuffer 高频探针出现在热栈 | **成立** | 旧 self/inclusive 18–41%；已新增 `-Dlobecorp.photon.bench.probes=false`，在 Mixin 注入层关闭四个探针，待 probe-off 对照轮给出净差。 |
| 背景动物与 3 只存活女皇归属不明 | **部分修正** | 新夹具在首次 setup 清理了 8 个带 `laser_profile_fixture` tag 的遗留实体；同世界仍有 14–16 只未带 tag 的鸡/猪等，`entity_census` 记录类型/UUID/坐标，未删除；它们属世界背景而非夹具。 |
| 四轮日志退出 GUI 断连异常 | **保留观察** | 本轮 FAILED 轮同样出现，与场景成败无关；属客户端关闭生命周期，不归因渲染。 |
| 历史样本早于录制漏提交修复，不能直接当基线 | **成立** | 旧 `client-1792904292331` 只有 8 场景且提交不同；旧 plain 对为 3a89929b vs 3b7d41c0，不是同一提交，只有 shader 那对同提交。 |
| （新增）施法尖峰与异常风暴 | **新确认事实** | 92 每轮施法尖峰 187–232 ms，Codec 27–38%、deepCopy 16–26%；91 首次 718 ms 且 CPU 只占 0.9%（停顿在 render 外）。每次施法出现 22–80 对 `NoSuchFileException`＋`WindowsException`：77.3% 来自 Photon ShaderGraph 材质构建的 GLSL `#import` 资源探测（渲染线程、`drain→bakeGroup→bakeBatches` 内），13.1% 为首次施法动态类加载；Iris 着色器缓存已排除。 |

### 本轮复测结果（2026-10-26 11:25 完成）

证据根：`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026`（每轮 `runs/<轮次>/` 保存 artifacts、summary.json、console 日志、截图；`analysis/` 为只读 JFR/分配/施法分析）。以下均为本轮实际测得的数值，不是历史复制。

#### 运行台账（全部 `status=PASSED`、`environment.valid=true`；Sodium 全程加载）

| 轮次 | run id（UTC 起止） | 光影 | packed | 探针 | spark | 相机指纹（00-empty） |
|---|---|---|---|---|---|---|
| P0 旧采样器基线 | client-1792980753772（02:12:33～02:20:21） | 关 | 关 | 开 | 关 | (-6.5, -36.7767, -44.5) |
| P2b 新采样器 | client-1792981928748（02:32:08～02:39:57） | 关 | 关 | 开 | 关 | (-6.5, -36.7767, -44.5) |
| P1c 新采样器·探针关 | client-1792983479080（02:57:59～03:05:46） | 关 | 关 | 关 | 关 | (-6.5, -24.7767, -68.5) |
| S1 光影·探针开 | client-1792984044114（03:07:24～03:15:17） | 开 | 关 | 开 | 开 | (-6.5, -24.7767, -68.5) |
| S0 光影·探针关 | client-1792984613162（03:16:53～03:24:41） | 开 | 关 | 关 | 开 | (-6.5, -24.7767, -68.5) |

- 每轮 13 个 Photon 场景；`frame_count` 与捕获帧一致、GPU errors=0、失焦帧=0（P1c/S1/S0 用 `-PphotonRequireFocus=false` 让 BenchMod 不因外部聊天抢焦点直接判无效，改由夹具的 `focus_audit` 与逐帧 flags 复核；本批 `measure_focus_valid=true`、0 失焦帧）。
- **相机仍会跨轮漂移**：P0/P2b 为 A 位姿，P1c/S1/S0 为 A′（Y＋12、Z－24），原因是 BenchMod `holdPose` 的还原在服务器侧存在滞后，下一轮 `origin` 取到上一轮某个极端场景的保持位姿。本轮已记录每轮真实相机指纹，跨相机轮次不作帧时间 A/B；修复建议见 P14。
- 旧 `P2-invalid-focus` 轮（client-1792981301652）因 `client.window.minimized`/`focus_lost` 判为 INVALID，已单独归档，不进入任何对照。首次门禁轮 `client-1792980111074` 因 prepare 覆盖 Iris 配置 13 场景 FAILED，同样作废。

#### 优化 A/B：动画采样分配治理（同相机、同探针、同 spark，仅代码不同）

| 场景 | 帧间隔 均值／P99（P0→P2b, ms） | CPU 均值（ms） | 渲染线程分配（MiB/帧） |
|---|---|---|---|
| 00-empty（对照） | 2.890→2.554 | 1.447→1.264 | 0.127→0.126 |
| 30-static-sixteen | 5.485→5.183 | 3.630→2.681 | 0.184→0.186 |
| 40-animated-sixteen | 7.824→6.204（−20.7%） P99 14.418→10.735 | 6.321→4.820（−23.8%） | **5.508→1.740（−68.4%）** |
| 90-animated-thirtytwo | 12.781→7.866（−38.5%） P99 25.061→13.956 | 11.088→6.634（−40.2%） | **10.882→3.047（−72.0%）** |
| 91-mixed-32＋1 束 | 10.300→9.654 | 8.104→7.367 | **7.323→2.276（−68.9%）** |
| 92-mixed-64＋4 束 | 22.906→23.442（无改善） P99 94.287→105.232 | 16.821→15.616 | **17.223→6.975（−59.5%）** |

结论（证据支持）：改动按设计消除了动画采样的每帧对象分配（−59%～−72%），并带动 16/32 动画与混合场景 CPU 均值下降；`40/90` 的帧时间与 P99 同向改善。**92 的帧时间与 P99 没有改善**，其主导项是每次施法的 Codec／深拷贝尖峰（见下），不是采样分配。

#### 探针效应（同代码、同相机、同 spark；S1→S0 为干净对照）

| 场景 | 光影·探针开 S1 | 光影·探针关 S0 | 降幅 |
|---|---|---|---|
| 30-static-sixteen | 7.868／P99 10.305 | 6.945／P99 8.187 | −11.7% |
| 40-animated-sixteen | 8.241／14.121 | 6.978／8.842 | −15.3% |
| 90-animated-thirtytwo | 10.867／19.170 | 7.502／11.079 | −31.0% |
| 91-mixed-32＋1 束 | 12.879／32.957 | 8.202／16.422 | −36.3% |
| 92-mixed-64＋4 束 | 15.233／46.112 | 12.332／36.580 | −19.0% |

- plain 的 P2b（探针开，相机 A）→P1c（探针关，相机 A′）不能作干净对照（相机不同），仅作参考：40-animated 6.204→4.995、91 9.654→7.165、92 23.442→11.993。
- 结论（确认事实）：四个高频提交探针在本轮是**可观的测量开销**（最高一项使 92 均值 +40%、P99 +22%）。历史 52 项数据均带探针开销，不能直接当作生产帧时间；本轮 S0/P1c 才是无探针的产品侧读数。

#### 目标逐项对照（S0=SLO 场景真值；E2=91、E3=92，plain 用 P1c）

| 指标（计划 SLO） | E2 目标 | E2 实测 S0 | E3 目标 | E3 实测 S0 | 判定 |
|---|---|---|---|---|---|
| 施放窗口均值 | ≤10 ms | 8.202 | ≤12 ms | 12.332 | E2 达标；E3 差 0.33 ms 未达标 |
| P99 | ≤22 ms | 16.422 | ≤25 ms | 36.580 | E2 达标；E3 未达标 |
| 单帧最长（非首帧） | ≤50 ms | 723.730 | ≤50 ms | 246.518 | **未达标**（施法尖峰） |
| 渲染线程 CPU 均值 | ≤5 ms | 6.304 | ≤5 ms | 9.857 | 未达标 |
| GPU 有效查询均值 | ≤10 ms | 7.875 | ≤10 ms | 10.115 | E2 达标；E3 临界超出 |
| 内存（无持续 GC/有上限） | — | 分配已降 59%～72%，快照仍有上限 | — | 同左 | 部分：分配显著下降，未做长时 GC 账本 |
| 视觉等价 | — | 逐顶点语义与几何测试通过；截图已归档 | — | 同左 | 未做像素级同机位 A/B（跨轮相机漂移） |

- 施法尖峰（本轮 JFR 确认）：92 每轮施法 187～232 ms，窗口内 Codec 27%～38%、`IFXObject.deepCopy` 16%～26%；91 首次 718 ms 且 CPU 只占帧长 0.9%（停顿在 render 外），第 2/3 次 92/80 ms。每次施法还有 22～80 对 `NoSuchFileException`＋`WindowsException`：77.3% 来自 Photon ShaderGraph 材质构建的 GLSL `#import` 资源探测，13.1% 为首次施法动态类加载；Iris 着色器缓存已排除。
- 因此“未达标”的主因已从“逐顶点提交/预留调用”修正为：**施法期同步的 FX 深拷贝＋Codec 往返与 ShaderGraph 材质重建**，以及光影下的 GPU/CPU 基线；采样分配优化只解决了动画稳态的一部分。

#### 视觉与截图（可直接查看）

- P0（旧代码，plain）：`.../runs/P0-plain-probes-on-packed-off/artifacts/screenshots/photon-*.png`
- P2b（新代码，plain）：`.../runs/P2b-plain-probes-on-quiet/artifacts/screenshots/photon-*.png`
- P1c（新代码，plain，探针关）：`.../runs/P1c-plain-probes-off/artifacts/screenshots/photon-*.png`
- S1／S0（光影）：`.../runs/S1-shader-probes-on-spark-on/artifacts/screenshots/`、`.../runs/S0-shader-probes-off-spark-on/artifacts/screenshots/`（含 `*-beam.png` 施法验证截图）
- 旧基线截图：`.../photon-runtime-comparison-20261025/before/build/modBench/raw-results/default/client/artifacts/screenshots/`
- 像素差只应在同相机轮次内解释：P0 与 P2b 同为相机 A（旧 plain-baseline 也是 A），S1 与 S0 同为 A′；P0↔P1c 的差异包含相机位移，不能当视觉回归。逐顶点属性由 `PhotonModelGeometryTest`／快照测试保证；像素级同机位 A/B 仍需先修 P14 相机漂移。

#### 事实、推断与假设（本轮新增）

- **确认事实**：分配归因 94% 在 `PhotonModelPoseSampler`（skinMatrices 38%～47%、positions 17%～18%、immutable 10%～13%），几何写入 0 分配；优化后 40/90 分配降 68%/72%；探针本身使 92 均值 +40%；92 每次施法都有 187～232 ms 尖峰且与 Codec/deepCopy 同窗；91 首次 718 ms 停顿在 render 外；跨轮相机仍因 `holdPose` 还原滞后漂移；世界内有 14～16 只未加 tag 的背景动物，首次 setup 清理了 8 个带夹具 tag 的遗留实体。
- **证据支持的推断**：施法尖峰的稳态化需要消除每次施法的 FX 子树深拷贝／Codec 往返与 ShaderGraph 材质重建（P06/P19），继续微调逐顶点提交对 P99 帮助有限；光影 E3 的 GPU 10.1 ms 已接近上限，进一步收益要靠减少重复 pass/材质重建而不是跳过阴影。
- **待验证假设**：把 ShaderGraph 材质／GLSL import 结果按内容代次缓存能否消除 77% 的异常风暴与部分尖峰；把相机原点改为世界固定锚点能否同时消除跨轮漂移；spark 全程开启时（本轮 S0/S1）与关闭时的净差值（本轮 plain 组为 spark 关，未做同代码开关对照）。

#### 复现（本轮实际命令）

```text
JAVA_HOME=<IDEA jbr 25>
JAVA_TOOL_OPTIONS=-Dlobecorp.photon.packedSnapshot=false -Dlobecorp.photon.bench.shader=<true|false> -Dlobecorp.photon.bench.probes=<true|false>
./gradlew.bat verifyBenchClient -PphotonBench=true -PphotonShader=<true|false> -PphotonSpark=<true|false> -PphotonRequireFocus=false --offline
```

`-PphotonShader` 决定隔离 Iris 配置（修复首轮 prepare 覆盖问题）；`-PphotonSpark` 决定 spark backgroundProfiler（本轮 S0/S1 为 true，此前 plain 轮为 false，差异已在台账标注）；`-Dlobecorp.photon.bench.probes=false` 从 Mixin 注入层关闭四个提交探针（生产路径不受影响）。

### 首次释放专项诊断（进行中，2026-10-26 11:40 起）

- 用户要求：**只测第一次释放**，施放完成立即诊断，然后重开游戏；其他场景暂停。另需关注“发射器/子发射器等会被到处调用”的路径，避免只优化首次施放而忽略反复调用的成本。
- 已加诊断代码：`PhotonCastProfiler`（仅在 `-Dlobecorp.photon.diag.cast=true` 时记录分段毫秒，默认空操作）；在 `QueenBeamEffects.Beam` 构造与首 tick 打点：`fxDefinition`、`createRuntime`、`childDefinition`、`controllerSetup`、`anchor`、`emit`、`namedBegin/namedSeek`、`prepareNext`；首个 Beam 构造的 tick 输出 `CASTPROF` 日志。
- 计划：新增“首次施放”单场景（注册为第一个场景、只触发 1 次真实施法、输出+2 帧截图后立即完成），用 `runBenchClient` 只跑该场景并读 `CASTPROF`＋JFR＋帧时间＋marks；每轮重开客户端，先测再改，改后再测同条件。
- 结果（单场景、每轮重开客户端；证据 `first-cast/`）：
  | 轮次 | 改动 | 首施法 tick 总耗时 | 首施法长帧 max | 状态 |
  |---|---|---|---|---|
  | run1 | 无预热 | **695.705 ms** | 未归档（截图校验失败） | FAILED |
  | run2 | 预加载 FX 定义 | **194.965 ms** | 未归档 | PASSED |
  | run3 | ＋预建运行态壳＋子实例预准备 | **62.690 ms** | 566.4 ms | PASSED |
  | run4 | ＋命名运行时预热 | **59.172 ms** | 557.6 ms | PASSED |
  | run5b | 改为“128 格内有女皇才预热” | **64.402 ms** | 545.9 ms | PASSED |
- 首施法 tick 侧分段（run1 → run5b，ms）：`fxDefinition 167.6→0.4`、`createRuntime 53.0→0(采用预建)`、`childDefinition 270.9→0.05`、`emit 71.8→50.1`、`namedBegin 15.2→12.9`、`prepareNext 116.0→0.02`；controller/anchor 合计 <2 ms。预热门控 `CASTPREWARM` 672～712 ms、`prepared=2`，仅在检测到 128 格内有女皇时执行（不把成本转给每次进世界），可用 `-Dlobecorp.photon.prewarmBeam=false` 关闭。
- **仍存在的首施法停顿**（run5b 帧 >50 ms：545.9/424.8/307.4/153.1/141.5/82.5 ms）不在 Beam 构造里。同窗口 JFR（12/9/8 个全线程样本，不能把单方法当整段耗时）显示首帧渲染侧一次性成本：Mixin 类转换/类加载（`MixinApplicatorStandard`、`DirectMethodHandle`、`NoSuchMethodError` 6 次、`IncompatibleClassChangeError` 2 次）、ShaderGraph 材质构建的 GLSL `#import` 资源探测（`NoSuchFileException`+`WindowsException` 每窗口 18～22 对）、Gson/JSON 节点图解析、OGG 音频解码，以及夹具截图自身 135 ms。
- 结论：**tick 侧首施法已从 695.7 ms 降到 64.4 ms（−90.7%）**，但首帧渲染侧仍有约 1.0～1.4 s 的一次性构建，用户看到的“第一次释放巨卡”仍会存在；需继续做 P22（预热阶段预构建 ShaderGraph 管线/缓存 GLSL import，或受控隐藏预渲染），并把截图移出测量窗口后再验收。
- 视觉：run3 与 run4 同一施放截图 `mean_abs=1.741`、`max=255`、`diff>8` 像素 2.69%，差异集中在光束粒子相位；未发现缺失网格、排序或材质整体变化，但同帧像素对照仍未完成。
- 证据：`runs` → `C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026/first-cast/run{1..5b}-*.log`、`run3|run4|run5b-artifacts/`、`analysis/FirstCastWindows.java`；casts/截图在各 artifacts/custom。

#### 剩余优化空间评估（2026-10-26 12:00，未实施）

| 目标 | 当前证据 | 可做手段 | 预估收益 | 风险 |
|---|---|---|---|---|
| 首施法首帧渲染侧 1.0～1.4 s | run5b 长帧 545.9/424.8/307.4/153.1/141.5/82.5 ms；JFR 同窗口：Mixin 类转换/加载、GLSL import 探测异常、JSON 节点图、OGG 解码 | 受控隐藏预渲染/预热管线与音频；或缓存 GLSL import 结果 | 首施法可见停顿大部分可移到女皇出现时；若再配合运行时池化可避免后续释放重建 | 视觉/状态泄漏；隐藏渲染需验证不产生残留粒子、时间线偏移 |
| 后续每次施法 190～250 ms | 首施法分段：深拷贝 53＋emit 50～72＋prepareNext 85～116＋命名 12；首施法已可预热 | 运行态池化复用（destroy→re-emit）、子实例 prepared 多槽/模板缓存 | 每次施法有望降到 50～80 ms，极端可到 15～30 ms | re-emit 状态重置与命名运行时/子实例所有权，需逐帧对照 |
| 稳态子发射器反复调用 | 旧 JFR：E2/E3 MEASURE 中 `IFXObject.deepCopy` 约 2.3% inclusive、`mc.CODEC` 3.2%；`prepareNext` 116 ms 首次 | 多槽 prepared、模板与定义分离、按内容代次缓存 | 降低持续播放/连发时的抖动与分配 | 子实例随机数/路径/作用域语义 |
| 测量口径 | 首施法截图自身 135 ms 落在帧窗口 | 把截图移出测量窗口或独立 session | 仅影响测量，不影响游戏 | 低 |

结论：**还能继续优化，且剩余收益主要在后两项（首帧渲染侧预构建、运行态/子实例池化）**；需要决定优先实施哪一项。

#### R1 运行态复用 A/B（阴性结果，默认关闭）

同场景（91，3 次真实施法，plain、探针关、spark 关、prewarm 开、新客户端）只改 `-Dlobecorp.photon.poolBeam`：

| 轮次 | 第 1/2/3 次施法 tick 总耗时（ms） | 帧均值 | >50 ms 帧 | >100 ms 帧 |
|---|---|---|---|---|
| A：pool off | 42.776 / 26.576 / 34.376 | 6.356 | 9 | 4 |
| B：pool on | 32.033 / 37.055 / 42.317 | 8.181 | 16 | 5 |

- pool 复用确实消除了 `createRuntime`（本场景仅 6.4/5.9 ms，`reuseEmit` 2.5～3.5 ms），但 `prepareNext` 从 18.4/26.9 ms 升至 33.4/38.5 ms，第 2/3 次施法 tick 反而略升；整体帧数 B 明显少于 A（11002 vs 14161），存在帧时间退化风险。
- 结论：**R1 单独复用运行态无正收益，默认关闭**（`-Dlobecorp.photon.poolBeam=false`，代码保留作实验与后续池化基础）。首次释放里的 53 ms 深拷贝主要是首次类加载/解析，类与 JIT 热后仅约 6 ms；真正大头是**子实例准备 `prepareNext`（18～38 ms/次）与首帧材质/管线构建**——下一步优先 R3（子模板缓存）与 R2（材质/管线预构建）。
- 证据：`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026/pool/`（runA-pool-off.log、runB-pool-on.log、A/B-artifacts）。

#### R2 首帧渲染侧预热 A/B（部分正面，2026-10-26 12:50）

- 背景：按“只测第一次释放、每轮重开客户端”的口径，目标从 tick 侧转到首帧渲染侧（P22）。对 run5b／本轮 A 的长帧窗口复核：Java 样本很少，`RenderTypeFactory#buildPipeline`、`ShaderGraphRuntime#compile` 与每窗 20～57 次 `NoSuchFileException`（资源/`#import` 探测）同窗出现，说明耗时主要在原生 GL 管线与着色编译，JFR 无法直接量到；因此改为“预热阶段用绘制同一入口提前构建”。
- 实现（`client/photon/performance/PhotonRenderPrewarm`，开关 `-Dlobecorp.photon.prewarmRender`，**默认开启**，`false` 关闭）：
  - 预热壳内每个发射器按 `MaterialSetting#getRenderType(geometryMode)`（与 `Emitter#bakeGroup` 同一入口）建材质、着色图与 CPU 管线；GPU 实例化变体（TILE/MODEL/BEAM/TRAIL/ARA）另经 `PhotonPipelines.graphShader` + `precompilePipeline`；
  - `PhotonModelOutput.prewarmRenderTypes()` 预准备模型资源与材质；`PhotonChildSpawner.preparedTemplate()` 递归覆盖子发射器（深度 2 已覆盖本轮全部 15 个 spawner、50 个渲染类型）；
  - 任务队列化：每 tick 预算 10 ms，避免女皇出现时一次性冻结；壳创建仍同步（约 0.9～1.05 s）。
- 同条件 A/B（`lobecorp.photon-00-first-cast` 单场景、每轮重开客户端、plain、探针开、spark 关；数值为 `PhotonFrameCapture` 施放窗口）：

| 轮次 | prewarmRender | 壳创建 | 预热队列 | 首施法 tick | 窗口最长帧 | >250 ms | >100 ms | >50 ms（全轮） | 窗口均值 | 采集帧数 |
|---|---|---|---|---|---|---|---|---|---|---|
| A 基线 | false | 888 ms | — | 68.3 ms | **680.3 ms** | 3 | 4 | 7 | 12.84 ms | 4744 |
| B1 同步·仅顶层 | true | 1619 ms | — | 87.5 | 797.9 | 3 | 6 | 7 | 27.25 | 3814 |
| B2 同步·深度3 | true | 2669 ms | — | 105.9 | 268.7 | 1 | 4 | 12 | 17.96 | 3303 |
| B3 队列20 ms·深度3 | true | 1063 ms | 3 s | 99.2 | 172.9 | 0 | 3 | 8 | 11.39 | 3507 |
| B4 队列20 ms·深度2 | true | 984 ms | 1 s | 70.9 | **129.0** | 0 | 1 | 5 | 8.03 | 4202 |
| B5 队列10 ms·深度2 | true | 1047 ms | 2 s | 75.3 | 167.5 | 0 | 2 | 6 | 9.85 | 4111 |
| B6 默认（不传 -D） | 默认 true | 1090 ms | 2 s | 90.5 | 208.7 | 0 | 4 | 8 | 15.80 | 3995 |

- 结论：
  - **同步预热无收益**（B1 反而更差）：只把成本搬到女皇出现时，首施法仍缺内容；
  - 递归覆盖 + 每 tick 预算（B3/B4/B5）才有效：首施放窗口最长帧 680→129～168 ms（−75%～−81%），>250 ms 帧 3→0，>100 ms 帧 4→1～2，窗口均值 12.84→8.03～9.85 ms，首施法 tick 68→71～75 ms 未退化；
  - **不能写成“首施法已不卡”**：B5 仍有 +0.08/+0.29/+0.39/+1.18 s 处 52～167 ms 帧，残余疑为未覆盖的实例化变体、首次模拟类加载与 tick 侧首帧工作，需下一步；
  - 代价：女皇出现时壳创建约 1 s（同步）＋预热队列约 1～3 s 的每 tick ≤10 ms 额外帧时；未做长时内存账本；
  - 视觉：B4 截图与基线相位不同（基线为光束展开、B4 为蓄力阶段），未发现材质缺失或几何丢失；同相位像素对照仍未完成。
- 证据：`first-cast/r2-{A,B,B2,B3,B4,B5}-artifacts/`、`run{A,B,B2,B3,B4,B5}-r2-*.log`；长帧分析器 `analysis/FirstCastDeep.java`（窗口内 self/异常调用栈/管线构建调用链/GC/分配）。

### 下一步

按“无光影/光影 × packed 关/开 × 探针开/关”做重复配对；每轮记录相机、投影、分辨率、窗口/焦点、帧率、世界、实体、光影、负载、代码版本与开关，截图归档；随后用 JFR/GPU/帧数据区分 CPU 求值/提交、资源准备、GPU 执行与工具开销。未达标就如实报告，不以“场景通过”代替性能验收。

## 2026-10-26：无光影／光影四组运行对照与结论修订

本节为当前测试结论，旧节均为当时历史，不能将旧节的“未运行”当作当前状态。用户已重新授权游戏测试；完成 Sodium＋BenchMod＋Minecraft MCP＋IDEA MCP＋独立 Debug。四组各 13 个 Photon 场景全部 PASSED，共 52 项；Sweeper 为 SKIPPED，不计通过数。实验缺省仍关闭，动画、LOD、原资源与生产渲染源码没有因本轮测试改变。**确认静态快照整块提交减少 reserve 调用；尚不能确认正式环境的稳定帧时收益，G0／G1 未通过。** 当前待办只在 [唯一清单](../../issues.md) 维护。

### 运行、原件与统计口径

原件根：`C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-runtime-comparison-20261025`。各运行子目录保留 report、JFR、帧／GPU／workload、截图与控制台；comparison-data.json 保存汇总，jfr-audit.jsonl 保存流式分析，debug 保存独立路径核对。所有运行正常结束，控制台 BUILD SUCCESSFUL；这与退出 GUI 异常并存，不能写成无异常退出。

| 分组 | 报告运行编号 | 北京时间 | packed 请求／实际 Mixin | Photon 结果 |
|---|---|---|---|---|
| plain-baseline | client-1792942126414 | 2026-10-25 23:28:46～23:36:29 | false／false | 13 PASSED |
| plain-candidate | client-1792947609751 | 2026-10-26 01:00:09～01:07:55 | true／true | 13 PASSED |
| shader-baseline | client-1792940658550 | 2026-10-25 23:04:18～23:12:09 | false／false | 13 PASSED |
| shader-candidate | client-1792941303454 | 2026-10-25 23:15:03～23:22:52 | true／true | 13 PASSED |

- 环境：Minecraft 26.1.2／NeoForge 26.1.2.100、Java 25、Sodium 0.9.2、Iris 1.11.4、ComplementaryReimagined r5.9.3、RTX 3060 Laptop；无光影仍加载 Iris，不能称纯 Sodium。seed=7、flat 世界、视距／模拟距 12、关闭垂直同步、报告限制值 260；实际窗口和 framebuffer 为 **2560×1494、165 Hz**，不是计划中的 2560×1600，也不是配置声明的 1280×720。
- 每场景检查实际光影状态以及 packed 请求与实际 Mixin 一致；普通模型 warmup 100 ticks、MEASURE 300 ticks。E2 为 16 静态＋16 动画＋1 束，E3 为 32＋32＋4 束；主采集 90 秒、4／31／58 秒三次施放。第四次验证截图在主采集之外，但仍可能落在 Bench MEASURE 中，故主表统一使用 PhotonFrameCapture。
- 52 项全部采集 flags=3（聚焦＋最大化），诊断帧数与捕获帧数一致，GPU query errors=0；异步 busy／pending 仍按原件保留，缺测不是零。CPU 为捕获的 render scope，GPU 为整个 GameRenderer scope，含世界／实体／后处理；二者与帧间隔不能相加。draw 只覆盖私有 Photon drain 内的原生提交，可能包含嵌套 Bloom，不含返回后的所有合成，不能当模型独占 draw 或像素过绘。
- 前三轮 metadata HEAD=3a89929b、最后一轮 HEAD=3b7d41c0，均 dirty=true；期间提交 ced6dc9 的基准门禁改动以及 3b7d41c 的文档整理，未再改生产渲染。不是四轮干净同提交。用户原有配置和两份测试 WIP 保留，不能把 dirty 当作已知性能原因。
- 最后一轮第一次误用了生成的默认 Sweeper profile，只有 Sweeper，没有 Photon；已归档 plain-candidate-wrong-profile 并排除。重做准备、确认 modBench.scenarios=lobecorp.photon-* 和 MCP 19877 后完成表内有效运行。Debug 样本也排除。

### 全部场景的主采集帧时间

单位 ms，单元格依次为均值／P99／最长；每组只有一次有效运行，不能以单次百分比宣布稳定收益。

| Photon 场景 | 无光影基线 | 无光影候选 | 光影基线 | 光影候选 |
|---|---|---|---|---|
| 00-empty | 2.373／4.166／21.672 | 3.211／6.755／30.023 | 7.279／10.663／19.934 | 6.704／8.376／11.380 |
| 10-static-one | 2.408／4.029／15.444 | 4.446／7.500／79.473 | 7.846／9.653／22.676 | 7.361／8.723／12.321 |
| 20-animated-one | 2.622／4.407／24.653 | 2.708／5.407／21.820 | 8.155／12.013／22.662 | 7.380／8.808／15.089 |
| 30-static-sixteen | 3.862／7.087／12.036 | 3.232／6.352／15.028 | 8.086／9.742／16.813 | 7.626／8.809／11.109 |
| 40-animated-sixteen | 5.973／10.318／15.416 | 6.286／13.156／42.333 | 9.297／17.708／59.049 | 7.910／13.124／27.870 |
| 50-cold-sixteen | 6.067／10.536／14.986 | 6.041／11.694／31.973 | 8.623／14.671／20.838 | 8.099／13.356／19.953 |
| 60-static-repeat | 3.431／6.103／10.861 | 2.592／4.895／10.738 | 8.153／10.542／25.015 | 7.747／10.815／13.585 |
| 70-empty-repeat | 1.925／3.243／7.973 | 1.861／3.555／7.837 | 7.274／8.625／10.354 | 7.304／9.093／11.705 |
| 80-static-thirtytwo | 4.205／6.129／12.295 | 2.616／4.718／13.355 | 8.309／9.911／20.131 | 8.315／9.718／11.512 |
| 90-animated-thirtytwo | 11.114／20.851／28.875 | 9.408／16.406／22.480 | 12.104／20.341／33.017 | 12.158／21.539／25.072 |
| 91-mixed-thirtytwo-laser | 9.305／19.083／648.135 | 8.161／19.864／717.979 | 11.064／24.782／804.829 | 10.136／22.861／757.503 |
| 92-mixed-sixtyfour-four-lasers | 14.560／39.036／232.884 | 13.617／43.489／232.200 | 18.034／52.076／301.549 | 16.050／44.443／219.921 |
| 99-empty-extreme-repeat | 1.858／3.124／8.288 | 2.024／3.622／9.224 | 7.204／8.591／19.253 | 7.273／8.413／11.061 |

### 提交、分配与 GPU 的实际结果

16 静态：两组均为每 Job 2048 顶点；基线每 Job reserve=2048，候选=1，调用数减少 **99.951%**，各轮真实 resize=0。这是减少逐顶点预留调用，不是减少 99.951% 内存、扩容或帧时间；原生 Job 缓冲仍创建，不是持久缓冲复用。光影基线 reserve 总数 60,555,264。动画 16 模型两组仍为 reserve/Job=2048，未被整块稳定快照覆盖。

| 分组／负载 | CPU 均值 ms | GPU 有效均值 ms | 渲染线程分配 MiB/帧 | reserve/Job | resize/采集帧 |
|---|---|---|---|---|---|
| plain-baseline／30-static-sixteen | 2.741 | 1.673 | 0.282 | 2048.000 | 0/3871 |
| plain-baseline／40-animated-sixteen | 4.939 | 2.644 | 5.608 | 2048.000 | 0/2503 |
| plain-baseline／91-mixed-thirtytwo-laser | 7.585 | 4.392 | 8.254 | 1958.654 | 2869/9673 |
| plain-baseline／92-mixed-sixtyfour-four-lasers | 11.877 | 7.170 | 18.112 | 1908.643 | 6116/6182 |
| plain-candidate／30-static-sixteen | 2.233 | 1.588 | 0.308 | 1.000 | 0/4625 |
| plain-candidate／40-animated-sixteen | 5.243 | 2.736 | 5.631 | 2048.000 | 0/2378 |
| plain-candidate／91-mixed-thirtytwo-laser | 6.563 | 3.860 | 7.244 | 971.462 | 3129/11028 |
| plain-candidate／92-mixed-sixtyfour-four-lasers | 10.909 | 6.812 | 15.866 | 943.035 | 6172/6610 |
| shader-baseline／30-static-sixteen | 3.764 | 7.699 | 0.259 | 2048.000 | 0/1848 |
| shader-baseline／40-animated-sixteen | 7.682 | 8.002 | 6.338 | 2048.000 | 0/1608 |
| shader-baseline／91-mixed-thirtytwo-laser | 8.957 | 9.427 | 7.455 | 1960.864 | 2330/8135 |
| shader-baseline／92-mixed-sixtyfour-four-lasers | 14.406 | 13.722 | 16.794 | 1912.470 | 4740/4991 |
| shader-candidate／30-static-sixteen | 3.315 | 7.244 | 0.384 | 1.000 | 0/1960 |
| shader-candidate／40-animated-sixteen | 6.759 | 7.368 | 6.589 | 2048.000 | 0/1890 |
| shader-candidate／91-mixed-thirtytwo-laser | 8.226 | 9.262 | 7.614 | 970.372 | 2572/8880 |
| shader-candidate／92-mixed-sixtyfour-four-lasers | 12.995 | 12.562 | 16.875 | 939.870 | 5444/5608 |

混合激光确有真实 resize，不能将纯静态的零扩容推广至全部 Photon。应比较 resize/帧或 Job，而非直接拿不同帧数的总计数论增减。动画渲染线程分配仍约 5.6～6.6 MiB/帧；此数包含相应渲染线程全部活动，不是模型独占、存活量或内存泄漏证据。E2/E3 仍出现数百毫秒最长帧，未完成逐长帧因果定位。

### JFR、Debug 与 Minecraft MCP

JFR 使用 Java 25 RecordingFile 流式两遍读取；以包含 Minecraft.run 的栈识别渲染线程 ID，再分别统计 ExecutionSample／NativeMethodSample。分析器处理 61 个录制，当前权威 Photon 52 个、历史 Photon 8 个；额外旧 Sweeper 文件不进入对照。场景 JFR 包含预热／收尾，不冒充精确主采集窗口；self 与 inclusive 不混用、inclusive 主题不相加。

- 静态16：无光影基线 replay self=108/401 Java 样本，候选 memcpy self=18/242；光影基线 replay=49/229，候选 memcpy=12/188。支持执行路径从逐顶点回放转到整块复制，不直接代表所列墙钟占比。
- 动画16 PoseSampler inclusive：无光影基线271/636、候选275/629；光影223/581、236/581。仍可见求值／蒙皮链。Iris wrapper 在无光影栈也大量存在，不能据 inclusive 计数宣称全部为光影 GPU 成本。
- **探针成本不可忽略**：无光影静态基线 observeReserve self=33/401；动画基线 observeReserve=204/636；候选动画 observeBuffer=138/629。相同探针配置不等于相同执行次数，候选减少 reserve 的同时也减少诊断调用。尚无 probe-off A/B，不能外推生产净收益。
- 历史静态 reserve self=112；旧报告 112/362=30.94% 保留旧分母，本次严格渲染线程 Java 过滤为112/271=41.33%。不同过滤不能拼接为性能趋势；reserve 本身不等于 realloc。
- 独立 Debug（PID41252）确认录制当次 output.vertices 0→2048、缓存2048顶点／65536字节并命中真实成功返回；原生临时缓冲探针 accepted=true、4顶点／128字节。临时探针资源已关闭。一个行号690非可执行断点与一次 run-to 等待超时保留在证据，随后实际命中688；不隐藏诊断失败。
- 原生 Minecraft MCP 工具未连接成功；按项目技能使用已发布桥和标准 SDK 1.32.1，固定19877，取得 pong、connected=true、世界／玩家／实体及截图。未修改全局端口，未重建桥。每次 Client／Stdio transport 均 finally close，桥进程已退出。原生连接失败原因未确认。

### 确认事实、证据支持推断与待验证假设

**确认事实：** 无光影两轮所有13场景相机指纹一致，场内 camera_changes=0；光影基线对候选绝对相机 Y＋7／Z－10，投影／朝向一致并不能抵消该位移。普通位置分别是 (-6.5,-36.776718807064796,-44.5)，极端场景为 (-6.5,-31.776718807064796,-58.5)；光影基线对应低7、高Z10。wp0_cross_run_gate_verified 与 particle_seed_determinism_verified 未证实。无光影空场景均值 2.373→3.211 ms，16动画 P99 10.318→13.156 ms，存在阴性控制波动。

**确认事实：** MCP 在无光影候选读到3只存活女皇，截图也可见背景动物。女皇 UUID：b3df0c8f-aa69-44b4-9ae8-fa7716ed15ef、fbfb0a9a-9f61-4d66-b5cd-27a1ab8e6d5a、b5dcddd9-0b7b-4063-b9d3-9d59c7fa5d2b；来源／归属／是否跨轮残留未核实。empty 表示无当前模型负载，不是干净空世界。未删除这些对象。四组控制台均包含 Trying to return to in-game GUI during disconnection，场景 PASSED 不覆盖退出生命周期正确性。

**证据支持的推断：** 整块提交确实消除了稳定快照的大部分 reserve 调用；当前动画／动态部分仍有 CPU 提交、蒙皮／分配及混合负载扩容压力，值得分别验证。光影 GPU 有效均值较高，但相机、背景与工具开销限制其因果分摊。无光影静态帧时间虽下降，也不足以消除阴性控制和探针偏差。

**待验证假设：** 相机位移是否由夹具位置恢复／服务器保存时序导致；3只女皇是否来自夹具生命周期；首发长帧中资源准备、驱动等待和管线各占多少；关闭探针后候选是否仍获益；混合 resize 能否用容量策略安全减少。现有数据均不能确认这些根因，不在本次报告任务中擅自修复。

### 与此前报告的同口径描述性对照

旧 client-1792904292331 仅8场景，早于录制漏提交修复且没有本轮探针、相机字段；不作为正确完整负载的性能基线。以下全部使用 Bench MEASURE 帧间隔（不是上方 PhotonFrameCapture），单位 ms，均值／P99，保留数字供追踪，不计算优化收益。

| 公共场景 | 历史样本 | 本轮无光影基线 | 本轮无光影候选 |
|---|---|---|---|
| 00-empty | 2.274／5.120 | 2.376／4.147 | 3.214／6.754 |
| 10-static-one | 2.059／3.934 | 2.409／4.066 | 4.446／7.471 |
| 20-animated-one | 2.530／5.740 | 2.621／4.420 | 2.708／5.407 |
| 30-static-sixteen | 2.485／4.553 | 3.860／7.067 | 3.234／6.332 |
| 40-animated-sixteen | 5.571／10.588 | 5.975／10.340 | 6.286／13.126 |
| 50-cold-sixteen | 5.730／10.749 | 6.028／10.520 | 6.072／11.886 |
| 60-static-repeat | 2.455／3.950 | 3.434／6.116 | 2.590／4.887 |
| 70-empty-repeat | 1.552／2.937 | 1.927／3.251 | 1.861／3.552 |

### 后续建议、对应代码与收尾

1. 先复核隔离世界与相机恢复门禁：PhotonModelRenderScenario、QueenBeamPerformanceFixture、PhotonFrameCapture；只清理可证明自有对象，不直接删除背景实体。下一次正式验证应有重复配对与 probe-off 控制，当前不继续启动游戏。
2. WP1 保持默认关闭：PhotonModelOutput、PhotonPackedVertexUtil、BufferBuilderMixin、PhotonBufferCache；后续验证透明排序、动态覆盖与容量策略，不能将 reserve 计数当扩容。
3. 动画独立定位：PhotonModelPoseSampler、PhotonModelGeometryUtil、PhotonSubmissionDiagnostics；先拆求值、蒙皮、提交与诊断开销，保持动画、LOD、资源表现等价。WP2～WP7 尚未实施，GPU 化不因本次采样自动获准。
4. 参考继续使用本页既有 JFR 官方指南与 Sodium／Iris 锁定版本源码；[JFR RecordingFile API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html) 用于离线读取，[Sodium 官方源码](https://github.com/CaffeineMC/sodium) 用于渲染边界参考。参考资料不代替本地调用证据。

正式采样已经结束。四个游戏进程（67216／72044／39744／14452）和独立诊断进程41252均已结束，临时桥／离线分析进程均完成；无 Debug 会话，临时断点移除、用户原3个断点保留。隔离 iris.properties、spark/config.json、options.txt 已逐字节恢复原件（config-restored.json 三项 true）；未动用户原有配置和测试修改，未推送。本轮无新增生产优化或资产修改。进度与当前问题同步总览／唯一清单，后续聊天轮换交协调安排。



## 2026-10-25：BenchMod 与光影激光复测（阴性结果，候选已回退）

本节只记录本轮新增复测与结论；没有保留任何优化代码，生产源码已恢复到 HEAD。测试使用 Sodium 0.9.2、BenchMod 8 个 Photon 场景（PASSED）、ComplementaryReimagined r5.9.3 光影与 90 秒真实激光三连施放。

**基线（光影激光，`laser_shader_base_20261025`）**：11033 帧，帧间隔均值／P99／最长 8.158／15.519／764.657 ms；空阶段 7.521 ms，三次施放窗口 8.841／8.017／7.846 ms；GPU 均值 7.722 ms，CPU 均值 3.522 ms。与旧轮不同，本轮同机同时段，作为下面的对照。

**JFR（BenchMod 16 静态）**：`PhotonModelGeometryUtil.write` self 100／300（33.3%），`ImmutableCollections$List12.get` 37／300（12.3%）；这支持“几何提交仍是热点”，但不等价于算术热点。

候选与复测结果：

1. **几何热循环重写**（int[] 索引、标量矩阵变换、颜色／透明度外提）：单测通过；JFR 中 `write` 样本 100 → 273（标量版）或 100（混合版），墙钟无收益；**回退**。
2. **仅模型输出跳过光影阴影 pass**：光影激光 10554 帧，均值 8.528 ms，窗口 9.411／8.275／8.159 ms；相对基线 8.841／8.017／7.846 无收益，**回退**。
3. **整个 Photon drain 跳过阴影 pass**：10152 帧，均值 8.866 ms，窗口 9.712／8.600／8.559 ms；更差，**回退**。

结论：当前实现上，继续微调 `write` 的算术或直接跳过阴影都不能证明收益。光影成本主要在 Iris／提交／管线结构；下一步应做原生实例化或按 pass／对象拆分测量，而不是用“跳过阴影”换数字或继续局部微优化。证据保留在 `run/client/performance/laser_shader_base_20261025_*`、`laser_shader_guard_20261025_*`、`laser_shader_drain_20261025_*`；Iris 配置已恢复，源码无差异。

**随后实现的第一阶段**：`PhotonModelOutput` 对稳定帧（同一姿态输入、世界矩阵／颜色／光照未变）记录最终顶点属性并回放，避免重复 CPU 变换；动画帧仍走原写入路径。BenchMod 16 静态 JFR 中 `write` 不再位于榜首，`ByteBufferBuilder.reserve` 成为首位（30.94%），说明提交缓冲成为新观察点；光影激光 `laser_shader_snapshot_20261025` 9879 帧、9.110／26.597／835.600 ms，动画光束未受益，不宣称优化。详见 changes.md。

## 2026-10-25：Sodium 诊断、瓶颈推断与优化建议

本节汇总 2026-10-24～25 的最新诊断。用户要求定位瓶颈并将方法、推断、建议、参考文献和对应代码写成文档；本轮**没有实施新的性能修复**，没有修改动画、LOD、资源或技能行为。当前问题状态仍只在 [唯一问题清单](../../issues.md) 维护。生产代码含 9a863816，采样时 HEAD 为文档提交 3e0c6576；同代码不等于主机条件完全相同。

### 1. 方法、原件与证据等级

这里的 Sodium 指已加载的 Sodium 0.9.2 渲染环境，不是把 Sodium 当作通用 JVM profiler。客户端仍含 Iris、Photon、GeckoLib、spark 等既有 Mod，不能称“纯 Sodium”。Sodium 的公开定位见 [官方仓库](https://github.com/CaffeineMC/sodium)；本项目的具体结论以下述本地录制与锁定版本源码为准。

- IDEA Client 运行既有 QueenBeamPerformanceTrial，普通与 ComplementaryReimagined r5.9.3 各采集 90 秒，按 4／31／58 秒发射真实技能；固定 MCP 19877，只对本工作区操作。
- 通过本次启动的 JAVA_TOOL_OPTIONS 临时启用完整 JFR profile：StartFlightRecording 的 settings=profile、dumponexit=true、maxsize=128m，FlightRecorderOptions 的 stackdepth=128。没有把这些参数写成全局设置。原有 QueenBeamAllocationCapture 只启用分配／GC／编译事件，不将其误称 CPU 执行采样。
- 读取 jdk.ExecutionSample、NativeMethodSample、ObjectAllocationSample、GCPhasePause、SafepointBegin、ExecuteVMOperation 及线程分配计数；按 frame.started_at、started_ns 和阶段 marks 对齐，另对齐最长帧的前一 HEAD→当前 HEAD 区间。秒级阶段边界及墙钟换算存在小误差，不用它证明服务端技能时长变化。
- 渲染线程按捕获区间中含 Minecraft.run 的调用栈识别 Java thread id，两轮均为 3。启动期 JFR 中名字仍可显示 main；只按字符串“Render thread”过滤会漏掉本轮样本。
- 首发窗口 cold=4～16 秒；warm1／warm2 为 31～43／58～70 秒。sustain1 从第二次实际客户端 OUTPUT 后 2 秒起，到 RECOVERY 为止，普通约 33.991～42.158 秒、光影约 33.984～41.985 秒；不是全程均值。
- 启动期完整录制也包含资源加载、类转换和关闭，但主表只过滤实际 90 秒窗口。profile 会产生工具开销；CPU 样本、native 样本、GPU 查询和帧间隔不是同一指标，不相加、不按样本比例直接承诺帧率收益。[JFR 性能诊断指南](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)

证据等级：**确认事实**只包含原始帧／JFR／日志直接记录及当前源码可见的控制流；**证据支持的推断**为事实共同支持但未经干预验证的解释；**待验证假设**指现有数据不能确认的因果、主次或收益判断。**优化建议**是未来方案，不属于已实施修改。没有采到不等于没有执行。本次补充只复核已保存证据，没有新增游戏采样，没有修改代码或分析器。

#### 确认事实

| 编号 | 可直接确认的内容 | 证据位置／对应代码 | 不应外推的内容 |
|---|---|---|---|
| F1 | 两轮各 9000 次 ThreadDump；168.208／103.256 ms 安全点分别与 spark 请求具有相同 safepointId | jfr-stacks.tsv、jfr-long-events.tsv；spark JavaSampler.run／dumpThreads | 不是停止 spark 后必能节省的毫秒数 |
| F2 | 持续输出已采到 PROJECT 子树节点及控制器的复制链；全部来源 deepCopy 主题在 sustain1 命中 33／269、23／189 个 Java 样本 | jfr-examples.tsv；PhotonSpawnTemplateUtil.create／includeController、PhotonNamedController.shallowCopy | 未将全部 deepCopy 样本逐一按来源分摊，不能把主题总数全算给 PROJECT |
| F3 | 普通 602.670 ms 首发区间采到本地动画片段 JSON 解析、复制和类转换；该区间 GC 暂停约 5.361 ms | spike0；LcClientAnimationResources.visualClip、LcAnimationClip.parse | 不能据一次栈命中断言单个解析方法耗时 602 ms |
| F4 | 光影 1579.635 ms 热长帧窗口中记录 4 个 Java／14 个 native 样本；native 有 10 个 glBindFramebuffer、3 个 glFenceSync，窗口未记录 GCPhasePause | shader spike0；PhotonBloom／PhotonFullscreenPass、Iris finishFramebuffer、原版 FogRenderer／MappableRingBuffer | 未直接测量各 native 调用的起止时长，未排除调度或驱动因素 |
| F5 | GPU 查询范围、有效数和缺测数已保存；Iris 缺项分支没有取消原编译方法 | 原始帧 JSON；PhotonFrameCapture、Iris MixinShaderManager_Overrides | 缺测不是零耗时；fallback 继续执行不等于视觉兼容完全正确 |

#### 证据支持的推断

- **I1：测量需要先隔离后台采样。** F1 证明后台取栈活动及长安全点关联真实存在；它支持把工具开销纳入排查，不支持把所有卡顿归给 spark。对应 P14／P17。
- **I2：持续创建／复制是值得优先验证的优化入口。** F2 与源码中的 CODEC、定义过滤和防御性复制相符；减少重复纯定义处理可能降低 CPU／分配，但可消除比例未知。对应建议 O1／P06。
- **I3：首次资源准备和持续渲染应分开处理。** F3 与 F4 的阶段和栈不同，单一法线缓存、单一 GC 调整或统一“首次 shader 编译”解释不足。对应 O2／O5。
- **I4：当前不能直接决定全面 GPU 化。** 本轮模型求值、复制、命名、Iris／Sodium 和 native 路径均有证据，且 GPU 缺测与跨时段条件存在；尚无等价性和受控收益对照。对应 O3／O4 及原架构门槛。

#### 待验证假设与所需证据

| 假设 | 当前为何不能确认 | 后续验证方案（本次未执行） |
|---|---|---|
| H1：关闭后台 spark 会显著降低 P99／全部长帧 | 请求关联不等于净可消除成本；到达安全点等待也受应用和调度影响 | 同代码／同场景／同工具条件下单独切换后台采样，比较 ThreadDump、安全点、CPU 和各轮分位数 |
| H2：模板／命名定义复用能消除主要创建成本 | 还含实例独占状态、UUID 重绑定、未知字段、动态子树与资源版本处理 | 分别量测节点复制、控制器复制、定义过滤／解析；做所有权与往返一致性对照，见 O1 |
| H3：光影热尖峰主要是 GPU 队列／帧缓冲切换阻塞 | native 采样不是每次 API 耗时，GPU 查询未覆盖全部帧；缺驱动／调度遥测 | 使用现有 Bloom／DRAW 入口分段，关联有效 GPU 时间、RenderPass.close、fence 来源和主机状态，见 O5 |
| H4：首发主要耗在 shader 编译或纹理上传 | 当前只看到首次使用附近的混合栈，没有独立的编译／上传时间分摊 | 在现有工具能力内分离查找、编译、上传与复制；无法独立测量时继续保留假设，不擅自加代码 |
| H5：高分配意味着内存泄漏 | 分配不是存活量；线程累计计数只覆盖短区间，未建立持有与释放账本 | 独立记录存活对象、owner／代次与关闭后回落；本次未执行 heap dump 或生命周期改造 |
| H6：实例化／VAT／GPU 蒙皮或阴影门控必有数量级收益且外观不变 | 缺法线、排序、动态覆盖和动画等价性；没有目标方案的真实对照 | 先满足现有架构报告的数值／排序／生命周期门槛，再谈收益；不默认降低动画、LOD、阴影或资源质量 |
| H7：P16 完全无害或必然造成漏绘 | 缺项后的原方法继续只是控制流事实；遮挡、混合和多光影包未全验收 | 按管线 ID 核对期望程序、override／fallback 与可见结果，见 O6 |

以上矩阵仅说明证据归属，不另建问题状态副本；当前问题和关闭条件仍以唯一清单为准。后文 O1～O6 已列出对应源码、语义约束与验收方法，参考文献列在第 9 节。

### 2. 运行账本与基本结果

| 运行 | 原始记录 | 实际结果 |
|---|---|---|
| 普通完整诊断 | sodium_diag_normal_20261024；10-24 19:04:14（北京时间）开始 | 53229 帧，90 秒，三次 STARTED、0→1→2→移除齐全 |
| 中断光影 | sodium_diag_shader_20261024 | 只到 SETTLE，随后退出；没有有效帧报告，单独隔离，不作光影测量 |
| 光影续跑 | sodium_diag_shader_20261025；10-25 00:54:12 开始 | 11101 帧，90 秒，三次 STARTED、0→1→2→移除齐全；日志确认实际包名 |
| 分析器 | JDK 25 RecordingFile 流式 API | 分别读取 240520／217112 个事件；原始 JFR 不改写，分窗与脚本可复查 |

两份有效录制跨时段，不能当严格确定性 A/B。全帧 flags=3、throttle=NONE、2560×1494；粒子随机 seed、实际渲染相机／投影、外部负载和 GPU 时钟未完全控制。code_animation_render_samples 仍为空。本轮不把阶段齐全当作连续动画或全部视觉通过。

| 指标，单位 ms | 普通 profile | 光影 profile |
|---|---|---|
| 帧间隔均值／P99／最长 | 1.691／7.247／602.670 | 8.108／18.612／1579.635 |
| 空基线均值 | 1.282 | 7.533 |
| 首发附近均值／P99／最长 | 4.745／14.271／602.670 | 16.456／39.005／1435.656 |
| 第二次附近均值／P99／最长 | 3.609／11.301／191.050 | 9.821／20.640／1579.635 |
| 第三次附近均值／P99／最长 | 3.386／10.145／62.514 | 7.721／14.906／67.982 |
| GameRenderer CPU 均值／P99／最长 | 0.927／3.268／387.706 | 3.889／11.135／1421.241 |
| GPU 有效查询均值／P99／最长 | 0.656／3.628／391.381 | 7.745／16.361／1248.802 |
| GPU 完成／pending 跳过／错误 | 45226／8003／0 | 3876／7224／0 |
| 渲染线程分配均值 B／帧 | 249139 | 823631 |

GPU 范围只覆盖 GameRenderer.render，光影缺测多，不能将有效样本均值推广为所有帧或把 pending 当耗时。分配是相邻 HEAD 的渲染线程计数差，含工具开销，不是存活内存／泄漏。ThreadAllocationStatistics 两轮在测量窗口各只有约 0.14 秒覆盖的两个计数点，**不能把其差值当 90 秒总分配**。分配样本权重也可能跨阶段，主要用来指路，不能精确分摊一次调用字节。

### 3. 优先纠正测量干扰：客户端 spark 关闭不等于后台采样关闭

**实测／源码确认：**两轮各有 9000 次 ThreadDump VM 操作，caller 均为 spark-java-sampler 的六个线程；日志确认集成服务器启动 background profiler，并在 Windows 采用 Java 引擎。spark 1.10.172 的 JavaSampler:27 创建六线程池，:60 按间隔调度，:89 调用 threadDumper.dumpThreads(threadBean)。这不是只看 Mod 名称的猜测。

- 普通：ThreadDump 操作体累计约 291.631 ms、单次体内最长 3.579 ms；光影为 330.256／9.698 ms。**这些值不包含全部到达安全点等待、后台 CPU 与分配成本，不能当作 profiler 的总开销。**
- 普通安全点 id=6801，19:04:56.655042 开始、duration=168.208 ms；同 id 的 ThreadDump caller 为 spark-java-sampler-4-thread-3，操作体仅 0.032 ms。该安全点与普通 41.822～42.013 秒的 191.050 ms 长间隔重叠。
- 光影安全点 id=5705，00:54:42.862278 开始、duration=103.256 ms；同 id 对应同名 spark 线程的 ThreadDump，操作体约 0.034 ms。
- SafepointBegin 的持续时间与 VM 操作体是不同阶段；不能相加为精确全局暂停，不能承诺停止 spark 就节省全部 168／103 ms。到达安全点的延迟还可能受应用执行状态或调度影响。
- 邻近还有 JFR Periodic Tasks 发起的 ClassLoaderStatsOperation，但它们 id 不同；本轮没有把“时间接近”误写成同一操作。完整 JSON 展开曾超过分析工具容量，后改流式读取，原始录制没有删除或截样。

**推断：**现有测量中存在不可忽略的工具／安全点混杂，先隔离它，比凭单轮平均值决定 GPU 重写更有依据。**建议：**用当前版本支持的入口单独停止服务器后台采样，再按“帧采集自身／JFR profile／后台 spark”分别做开关对照；记录工具线程、ThreadDump、safepointId 与负载。尚未实施此对照，不自动改其他工作区或全局配置。spark 官方说明 Java 引擎存在 safepoint sampling bias，且 async-profiler 的支持平台不包含本次 Windows 环境，不能假设已有 Linux 原生采样器可直接使用。[官方说明](https://spark.lucko.me/docs/misc/Using-async-profiler)

### 4. 计算热点：持续输出仍在复制子树和命名控制器

下表为调用栈主题的 **inclusive 命中数**，主题会嵌套，不求和。普通全窗口 Java 样本 1666、native 562；光影为 1877、555。持续输出第二次的 Java 样本分别为 269／189。

| 主题 | 普通全窗口 | 光影全窗口 | 普通／光影 sustain1 |
|---|---:|---:|---|
| Photon drain | 511 | 310 | 103／45 |
| 模型 sample | 175 | 87 | 35／11 |
| geometry.write | 156 | 95 | 29／18 |
| IFXObject.deepCopy | 137 | 137 | 33／23 |
| NamedRuntime | 220 | 170 | 45／36 |
| NamedMixer | 122 | 73 | 28／15 |
| Iris 全类栈 | 1 | 755 | 未采到／42 |
| Iris ShadowRenderer | 未采到 | 657 | 未采到／39 |
| Sodium 栈 | 36 | 440 | 4／26 |

**已确认的新细化：**持续输出中存在 PROJECT 子树分支的 deepCopy 调用，不只是首次 FX 资源加载；下列代表栈不能替代全部 deepCopy 样本的来源分摊：

```text
PhotonChildSpawner.update
 → PhotonSpawnSchedule.advance/window → emitConfigured → emitNow → nextTemplate
 → PhotonSpawnTemplateUtil.create
    → object.copy(true)                         [子树节点]
    → includeController → source.shallowCopy()  [控制器]
 → PhotonNamedController.shallowCopy → IFXObject.deepCopy → CODEC / PersistedParser / NBT
```

对应代码：
- [PhotonSpawnTemplateUtil.create／includeController](../../../src/main/java/org/unitego/lobecorp/util/photon/playback/PhotonSpawnTemplateUtil.java)：35～95 行选择 FX／PROJECT，75 行复制节点，110～154 行按目标集合过滤命名定义并复制控制器。
- [PhotonNamedController](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedController.java)：23～36 行防御性复制，54～55 行 shallowCopy 实际调用 deepCopy。
- [PhotonChildSpawner](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java)：77～91 行预备实例取用，220～260 行按预算发射；单槽预备不会覆盖所有持续生成的子树。
- 依赖 Photon 26.1.2.3 的 IFXObject.deepCopy／FXObject.copy、LDLib2 PersistedParser；不是只改模型准备缓存就能消除的路径。

**建议 O1，优先级高：**将不随实例变化的模板描述、目标过滤和命名库解析，与可写运行态分离。候选缓存键必须包括工程／内容代次、子树身份、includeChildren、完整目标集合与定义指纹；过滤必须保留轨道顺序和未知字段。先复用只读定义／未绑定执行描述，再为每个实例解析 UUID、目标对象和 slot。不得直接把 deepCopy 换成共享可写 shallowCopy，不得共享 FXRuntime、随机数、时钟、句柄或含 FXObject 引用的 PhotonNamedPlan。

验收：分别量测 PROJECT／FX 的节点复制、控制器复制、定义过滤、CODEC 解码次数与 CPU／分配；保留多实例不同相位、取消、退出世界、内容编辑／重载、未知字段与原树不变回归。首次和热生成都要测；不能仅把准备移到进世界后称总成本消失。此建议属于 P06 的细化，本轮未实施。

### 5. 首发不只涉及 Photon 模型：本地动画 JSON 首次解析也被命中

普通最大 602.670 ms 区间为约 4.085～4.687 秒，采到的路径包括 LcClientAnimations.visualPose → LcClientAnimationResources.visualClip → LcAnimationClip.parse → Gson，以及 FX 复制、模型准备与类转换。该区间只记录到约 5.361 ms GCPhasePause，不能把整段算成 GC，也没有证据把整段算成某一次解析。

- [LcClientAnimationResources.visualClip](../../../src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimationResources.java)：57～64 行已按动画 ID／名称缓存，不是每帧重新解析；104～123 行管理资源代次与清理。
- [LcAnimationClip.parse](../../../src/main/java/org/unitego/lobecorp/animation/pose/LcAnimationClip.java)：41～65 行每个未缓存片段从完整 JSON 解析 ActorAnimations，再烘焙所选名称及表达式。
- [PhotonNamedRuntime 构造与 matchesController](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java)：105～129 行读取命名库、准备模型、绑定实例计划；144～145 行比较来源定义。

**建议 O2，中高优先级：**调查同一资源文件中不同片段首次选择是否重复解析同一完整 JSON；若成立，按资源代次复用只读解析树，片段及 Molang 求值上下文继续独立。优先核对 GeckoLib 已有解析能力是否语义一致，不直接替换自定义 easing／坐标／时间处理。缓存要有预算、失败重试和重载失效。验证逐时刻姿态／表达式与原输入一致，同时列出资源加载和首次施放的总成本；不减动画更新率、不改原资源。

### 6. 模型与命名求值：保留成本问题，但不要默认先上 GPU 蒙皮

[PhotonModelPoseSampler](../../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelPoseSampler.java) 的 positions:319～345 每次建立部件 Map 并按角点查找，sample／faceNormal／immutable 仍在当前调用栈中；[PhotonModelGeometryUtil.write](../../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java) 继续逐角变换和 QUADS 提交；[PhotonModelOutput](../../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) 的 sampleLocalFrame／bakeBatches 仍按当前输入求值。法线缓存和内嵌准备复用没有缓存最终连续姿态或最终几何。

但本次 skinMatrices 主题在光影全窗口仅采到 1 次，普通未采到；不能把过去“16 个单骨骼合成模型”的蒙皮占比套到这条真实激光。也不能据未采到断言所有资源无蒙皮成本。

**建议 O3，中优先级：**预编译稳定角点／顶点索引与部件执行顺序，减少 positions 的整数 Map 与装箱；只对确实稳定的局部数据复用。动态姿态仍在实例局部缓冲中计算，发布前形成能安全供多视图消费的不可变快照；不能让下一帧覆盖仍被延迟批次持有的数据。保持每三角形四角 QUADS、UV、透明度、镜像／非均匀／奇异法线和透明排序。先用逐顶点属性对照及 1／16 实例验证，再讨论受限的 GPU 路径；原生实例化的法线与排序障碍见架构复核报告。

**建议 O4，中优先级：**对 [PhotonNamedRuntime](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java)、[PhotonNamedMixer](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedMixer.java) 与 [PhotonModelClipUtil](../../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelClipUtil.java) 复用按内容版本编译的轨道／目标执行计划。后者 apply:14～40 每次 clear 并 leafTracks(false)，可量测真实展开次数后决定是否准备计划。只缓存计划，不按“输入对象未换”缓存时间函数结果；mixer.begin 的 release 与 install、mute、优先级、循环、外部基础值及随机源必须保持。已完成的绑定槽预解析只是局部，不当作完整 O4。

### 7. 光影热尖峰：native 帧缓冲／Bloom 与 fence 需要分层定位

光影最大 1579.635 ms 是第二次施放附近约 37.557～39.136 秒，**不是首发**。该窗口 Java 样本 4、native 14；native 中 10 个落在 glBindFramebuffer、3 个在 glFenceSync。代表调用链：

```text
PhotonWorldRenderState.drain → PhotonBloom.run → fullscreenPass
 → PhotonFullscreenPass.draw → RenderPass.close
 → Iris finishFramebuffer → glBindFramebuffer

GameRenderer.render → FogRenderer.endFrame → MappableRingBuffer.rotate
 → GlFence / createFence → glFenceSync
```

窗口内未记录 GCPhasePause。它支持“帧缓冲切换／驱动或队列同步是调查方向”的推断，不证明 glBindFramebuffer 独占整段时间，也不证明都是 Photon、Sodium 或 GPU 算力不足；少量 native 样本不能按采样周期乘成精确耗时。第二条链来自原版雾／ring 生命周期，不是 Photon GPU 查询。glFenceSync 的规范行为是插入 fence，不能仅凭名字就等同于一次客户端阻塞等待；也不应删除 fence 或强行阻塞 GPU 来“优化”。[Khronos 原始参考](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glFenceSync.xml)

**建议 O5，调查优先级高、改动须谨慎：**
- 先复用 [PhotonBloomMixin](../../../src/main/java/org/unitego/lobecorp/mixin/photon/performance/client/PhotonBloomMixin.java) 中已有 BLOOM／DRAW 分段入口，统计各 pass、RenderPass.close、目标切换、有效 GPU query 与等待；单独量测诊断开关开销，不新增另一套并行 profiler。
- 查明同一帧的目标绑定／pass 切换及数据依赖，再考虑在合法边界复用状态／缓冲，保持 Iris 状态恢复、HDR、深度遮挡与混合顺序；不关闭 Bloom、不合并任意材质、不降低分辨率或阴影质量。
- [PhotonOcclusionDrawUtil](../../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonOcclusionDrawUtil.java) 的 prepare:110～160／draw:178～207 是本项目入口；借用的原生缓冲不得提前关闭。需先证明自己增加的 pass／UBO／同步成本，而不是把全部 fog 或 Sodium 的 fence 都归到这里。
- 反证：原版 DynamicUniformStorage.writeUniform:58～60 已复用相邻相等 uniform；不能宣称每次 writeTransform 都上传新 UBO。DynamicUniforms.writeTransform:37～40 仍会创建矩阵／向量副本，但它是否值得单独缓存要先计数，不以重复实现原生缓存作为主优化。

### 8. P16 的机制已确认，但视觉正确性和性能影响仍待验收

锁定 Iris 1.11.4 的反编译类 net.irisshaders.iris.mixin.MixinShaderManager_Overrides：
- redirectIrisProgram:27 在 GlDevice.getOrCompilePipeline 的 HEAD 注入。
- :35～49 中 override 返回程序才 setReturnValue；缺项时按 RenderPipeline 对象进入 missingShaders 集合，并记录携带 Throwable 的消息。
- :56～58 经 IrisPipelines.getPipeline／shaderMap 取 override；缺项分支没有取消原方法，原有 getOrCompilePipeline 继续。

因此 Missing program 不等于该管线完全没绘制；也不证明 fallback 在所有光影／遮挡情况下正确。它与 KilaGraph 的 ENTITY 格式门槛是不同检查，不能混写成同一个根因。光影本轮仍记录这类诊断，原始日志保留。

**建议 O6，兼容性优先：**按管线 ID／对象身份／资源代次记录期望路由、override 命中与 fallback；先验证 Photon graph、Bloom、合成和 Lobecorp occlusion 各自应走的程序。若本应保留自定义程序，再核对当前版本支持的最小互操作方式及状态恢复。不要全局强制 ENTITY、跳过阴影或只压低日志级别；关闭条件包括深度遮挡、透明交叠、取消／卸载／重载和多光影包的独立验收。本轮未修改路由。

### 9. 优先顺序、未实施边界与参考文献

| 顺序 | 当前有依据的工作 | 对应现有问题 | 判定依据 |
|---|---|---|---|
| 先隔离测量 | 服务端后台 spark／JFR／帧采集开关对照 | P14／P17 | 9000 次 ThreadDump、已匹配长安全点 |
| 第一批实现候选 | O1 子树／控制器定义复制与 O2 同文件片段解析复用 | P06 | 持续和首发均有实际调用链 |
| 第一批调查 | O5 分解热尖峰、O6 明确 override／fallback | P15／P16 | native 栈、源代码与真实 ERROR 日志 |
| 后续局部优化 | O3 稳定索引／姿态发布，O4 内容版本执行计划 | P11／P12 | 当前真实模型与命名求值样本 |
| 不默认采用 | 全面 GPU 蒙皮／VAT、资产替换、阴影／Bloom 关闭 | P11／P15 | 未满足等价性或独立收益证据 |

本轮只诊断和写文档，不将这些建议视为已经实施或自动授权的全部改动。当前数据不支持“改一个法线矩阵就解决”“GPU 化必然数量级提升”“没有启用客户端 spark 就没有采样干扰”等结论。测量隔离和具体复制链比泛泛扩大缓存或降低视觉负载更值得优先验证。

参考文献与本地版本依据：
1. [Sodium 官方仓库与定位](https://github.com/CaffeineMC/sodium)：只用于说明工具职责；本次二进制为 sodium-neoforge-0.9.2+mc26.1.2.jar，不以仓库当前分支替代锁定版本行为。
2. [Oracle JDK 25：JFR 性能问题诊断](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)：区分 CPU、等待、GC 与测量开销，事件阈值会影响可见性。
3. [Oracle JDK 25：jfr 命令](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html) 与 [RecordingFile API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html)：离线事件读取和流式分析方法。
4. [spark 官方 Java／async-profiler 引擎说明](https://spark.lucko.me/docs/misc/Using-async-profiler)：采样偏差与平台条件；实际源码核对 spark-1.10.172 的 JavaSampler:27／60／89，不将其他版本命令猜成当前可用 API。
5. [Khronos glFenceSync 原始规范页源码](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glFenceSync.xml)：fence 的协议含义，不是本项目性能收益承诺。
6. 本地 IDEA 源码：Photon 26.1.2.3／LDLib2 26.1.2.42、Iris 1.11.4、Minecraft 26.1.2／NeoForge 26.1.2.100。依赖类通过 IDEA／MixinMCP 读取；本节项目代码链接与方法名为当前真实入口，升级后需重新核对。

### 10. 备份、复现与清理

完整证据根为 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-sodium-diagnosis-20261024。normal／shader 分开保存帧、marks、截图、完整 JFR、windows.tsv、jfr-summary.tsv、jfr-stacks.tsv、jfr-examples.tsv、jfr-long-events.tsv、jfr-event-counts.tsv；shader-interrupted 不混入有效轮。JfrWindowAnalysis.java 为备份目录中的离线分析源文件，不是模组生产代码；frame-summary.json 保存全部帧统计。

复现步骤：用 IDEA Client 及唯一 label 开启本节记录的 JFR 参数；结束后使用同一 JDK 25 运行分析源文件，依次传入 JFR 路径、帧报告 started_at、包含 windows.tsv 的输出目录。分析按实际栈识别渲染线程 ID，按时间筛选而不是把 startup 全部算进技能。原始录制及最差长帧不删，普通／光影跨时段不作稳定收益 A/B。

普通游戏 PID 59968（10-24 19:03:24.914 启动），光影续跑 PID 17540（10-25 00:53:17.473 启动），JFR 均有 Shutdown requested from Java；续跑游戏还保存了正常保存退出、Stopping! 与 IDEA BUILD SUCCESSFUL 日志。普通轮 IDEA 临时日志在重启后不可用，未用当前 latest.log 冒充；保留其原始 JFR、帧、阶段及原操作回执。分析辅助进程已自行结束，桥接调用结束即关闭；未按名称结束 IDE、未知 Java 或其他任务。

中断遗留 Iris 已先恢复并核对原字节，再显式启用本地光影包重跑；最终再次恢复原始字节。没有修改全局 JAVA_TOOL_OPTIONS、其他工作区、动画、LOD、原始资源或生产代码，没有推送。问题状态与后续验收仍统一在唯一清单。

## 历史审查与既有实验背景


> 阶段说明：本页是 2026-10-19 静态候选与实验方案；后续局部实现见 [最新 WIP](photon-workspace.md)。尚无当前版本完整冷热、P99 与视觉验收，不将候选当作确认根因或收益。

日期：2026-10-19。状态： **只读源码审查与优化提案，未实施、未运行采样**
。本轮三路并行审查时间轴/加载、模型/渲染、诊断/资源事务，由主代理统一记录。没有修改代码、配置、Shader、游戏资源或依赖，没有执行构建、Data、测试或游戏。

配套[模型特效与高级特性重设计方案](../../design/photon/photon-model-redesign.md)。旧自建模型/动画/专用渲染核心已移除；本页审查的是仍生效的项目工具及锁定
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

本节只保存本轮证据判断；当前问题状态以 [唯一问题表](../../issues.md) 为准。**没有证据支持 S01–S23 已全部解决。**

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

本轮文档备份与提交范围见[重设计方案第23节](../../design/photon/photon-model-redesign.md#23-本轮交付备份与未完成事项)
。后续代码实施、运行实验及是否调整Photon依赖仍需按用户授权范围推进。


## 2026-10-24 历史模型诊断与法线缓存对照

2026-10-26 文档整理将原测试前／后两页的成本分析、实施约束、逐轮账本、JFR／Debug／MCP、数值及修订边界合并至此。以下分别对应当时版本，源码旧行号只作历史定位；建议不表示已实施，当前状态只在 [唯一清单](../../issues.md) 维护。原件及 SHA256 保存在 photon-docs-organize-20261026，未重新运行或改写当时结果。

### 前测：三、P11：静态模型逐帧 CPU 几何提交

#### 前测：3.1 现象

16 静态模型 MEASURE 均值 3.258 ms、P99 6.256 ms；重复用例均值 2.851 ms。渲染线程 JFR 中 `geometry.write` inclusive 为 245／324 个执行样本，重复用例 212／280（约 75.6%／75.7%）。这是执行样本比例，不是 GPU 占比或精确墙钟占比。

#### 前测：3.2 调用链

[PhotonModelOutput.bakeBatches](../../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L431-L452) → `bakeModelGroup`（第 454 行）→ `bakeGroup` 回调里的 [PhotonModelGeometryUtil.write](../../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java#L13-L67)。

#### 前测：3.3 成本机制

1. **每帧无条件重建全部提交几何。** [sampleLocalFrame 第 402 行](../../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java#L402-L420) 的 `input.equals(sampledInput)` 命中后只是跳过 `sample()`；`bakeBatches` 仍对每个 group 调 `write`，逐三角形、逐角写顶点。
2. **提交坐标依赖相机与模型变换。** 第 444 行 `world = translation(-cameraOrigin) × localToWorld`；相机或模型变换变化时，提交位置可能不同，不能无条件复用上一帧最终位置。相机静止时原点不必变化；平移不改变法线所需的线性部分。
3. **顶点数 ×4/3。** `write` 每个三角形写 4 个角，[第 27–30 行](../../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelGeometryUtil.java#L27-L30) 用 `offset = Math.min(corner, 2)` 重复末角，只为保留原生 QUADS 透明排序路径。16 模型 ×512 三角形 ×4 = **32768 次 `addVertex`／帧**。
4. **每角固定变换与光照。** 位置经 4×4 变换（第 33 行）、法线经 3×3 变换（第 34 行）、`signum(determinant)` 乘符号、长度判断与归一化（第 37–38 行）、`shade()` 6 次乘加与分支（第 56 行）、`setColor`／`setLight`／`setNormal`（第 58–62 行）；法线退化时还会分配临时 `Vector3f`（第 41–44 行）。
5. **数据结构间接访问。** 每个角都要 `group.vertices().get(indices.get(...))` 解包 `Vertex` record，再取三个 `Vector3` 分量；没有预展开的紧凑顶点数组，也没有把 4 个角共享的法线矩阵变换结果合并。

#### 前测：3.4 为什么现有缓存不足以关闭 P11

`sampleLocalFrame` 解决的是“同一输入不重复求值”，省掉的是姿态计算；P11 的成本在“求值之后、提交之前”的世界变换、法线、颜色与顶点写入，与输入是否变化无关。因此静态模型的缓存命中率再高，只要仍在 `bakeBatches` 里逐角重写，成本就不会消失。

#### 前测：3.5 优化建议

- **前置能力核对（必须先做）。** Photon 26.1.2.3 已提供 `Emitter.bakeGroup`／`bakeInstancedGroup`、`BaseMesh`、`AdditionalGPUDataSetting` 等入口（jar 内已确认存在，语义尚未验证）。先确认原生批次是否支持“静态局部网格 + 每帧仅变换”的提交方式，再决定复用层级，避免新增平行渲染体系。
- **方案 A（低风险，常数项）。** 预展开角点表（每 group 一个 `int[]` 角序列，替代 `List.get` + record 解包）；把 `world` 提取为 `float[16]` 手动乘；法线矩阵展开为 9 次乘加；`shade` 预查表；消除法线兜底分支的临时对象。保持 4 角重复、材质组顺序、insideOut、双面、深度、混合、光照语义不变。预期只削常数，不解决量级。
- **方案 B（中风险，帧缓冲复用）。** 按 `frame` 身份缓存“已展开的局部顶点缓冲”（位置、法线、UV、颜色），每帧只做世界／法线变换后写 consumer。相机原点每帧变，仍逐顶点变换，但省掉解包、重复角取数和法线兜底。
- **方案 C（高风险，收益最大）。** 走原生持久网格／缓冲（参考原生 `TileParticleRenderer.modelMeshBuffer` 的持久 VBO 思路），局部几何只上传一次，变换交给提交矩阵。必须先证明透明排序、材质批次和资源重载语义可保持，否则不采用。

#### 前测：3.6 实现约束

- 不能为了减少顶点直接删角；当前 4 角重复是 QUADS 透明排序路径的一部分。
- 材质组顺序、insideOut、双面、深度、混合、光照和资源重载语义须保持。
- 缓存键必须覆盖拓扑、局部姿态、动态材质与必要版本，不能只看模型路径。

#### 前测：3.7 验证与关闭条件

同一资产 1／16 静态模型、动画切换、仅世界 TRS 变化、相机变化、透明交叠、多材质、镜像／非均匀缩放及重载；记录每帧顶点写入量、缓冲创建／上传字节、CPU 样本与 GPU 时间。关闭条件：稳定静态输入不再无条件重做全部局部几何，重复新进程对照显示成本下降且 P95／P99 无退化，截图／排序与资源释放对照通过；仅平均 FPS 上升不能关闭。

### 前测：四、P12：动画蒙皮、查表与整帧对象分配

#### 前测：4.1 现象

16 动画模型均值 5.784 ms、P99 10.477 ms；`sample` 占 222／462 个渲染执行样本。约 23 秒整份录制中渲染线程分配计数增加 19.554 GiB（静态约 0.984 GiB），包含 setup／预热，不是存活内存或泄漏量。

#### 前测：4.2 调用链

[PhotonModelPoseSampler.sample](../../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelPoseSampler.java#L149-L255)，以及 `partMatrices`、`skinMatrices`、`morphWeights`、`morph`、`positions`、`faceNormal`、`immutable`；上层是 `PhotonModelOutput.sampleLocalFrame`。

#### 前测：4.3 成本机制

1. **每帧先做全量校验。** morph／part 用 `stream().noneMatch` 逐个比对（第 153–168 行），clip 用 `stream().filter` 查找（第 171 行）；这些只随内容变化的检查每帧重复。
2. **partMatrices 每部件多份临时矩阵。** 第 261–286 行：每部件 `new Matrix4f().translation(pivot).mul(matrix).translate(...)`，有父级时再 `new Matrix4f(parent).mul(...)`。
3. **skinMatrices 每权重顶点固定 3 个对象。** 第 386–430 行：每骨骼 `new Matrix4f` 连乘；每个带权重点执行 `stream().mapToDouble().sum()` 归一化（第 410 行）、两份 `float[16]`（第 414–415 行）、`new Matrix4f().set`（第 423 行）并放入 `HashMap<Integer, Matrix4f>`（第 427 行）。
4. **morph 整份复制顶点。** 第 300–317 行先把所有顶点复制成新 `Vector3`，再逐顶点 `immutable()`；权重全为 0 时也复制。
5. **positions 走装箱 Map + 每顶点临时对象。** 第 319–346 行：每部件一个 `HashMap<Integer, Vector3>`，每角 `computeIfAbsent`（int 装箱 + lambda 捕获），`dimensions()` 每顶点新建 `Vector3f`，蒙皮／部件矩阵两次变换后再 `immutable()`。
6. **整帧 Vertex／Group 重建。** 第 186–246 行：每角 `new Vector3(faceNormal)` + `new Vertex(...)`；每面重算 `faceNormal`（3–4 个 `Vector3f`）；`min/max` 每角 `vector(position)` 又建 `Vector3f`。
7. **输入与绑定每帧重建。** `Input` 构造内 3 次 `Map.copyOf`（第 67–79 行）；[PhotonModelBindings](../../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelBindings.java#L84-L131) 的 `morphWeights`／`partOverrides`／`boneOverrides` 每帧各建 `LinkedHashMap` + `Map.copyOf`，overrides 还要遍历全部 part／bone × 3 个槽。
8. **共享范围依复制路径而异。** PhotonModelOutput.shallowCopy() 已共享 sampler 与 groupOrder；深拷贝创建与命名运行态准备不能据此视为同样共享。需要按真实创建路径统计定义、拓扑和 sampler 的准备次数，不使用“零共享”的概括。

#### 前测：4.4 为什么现有缓存不足以关闭 P12

动画时间每帧变化，`Input.equals` 必然不等，`sample()` 每帧全跑；`sampleLocalFrame` 的缓存只对静态模型有效，对连续动画不能命中，也不应该靠冻结动画制造命中。

#### 前测：4.5 优化建议

- **预编译（内容级、可跨实例共享）。** 顶点→权重索引与预归一化权重、顶点→UV、面法线索引、角点展开表、clip 轨道二分索引、part 维度参数；把每帧重复的 id 校验改成随内容 revision 失效。
- **每帧缓冲复用（实例级、渲染线程独占）。** 用平铺 `float[]` 替代 `HashMap<Integer, Vector3>`、两份 `float[16]` 和 `Matrix4f` 临时对象；位置、法线、UV 直接写入复用数组；`Frame` 改为引用这些数组的只读视图。注意帧快照可能被多视图／延迟批次消费，不能提前覆写，需双缓冲或不可变发布。
- **消除每帧 Map。** `Input` 与 bindings 的三个 Map 只在 overrides 真正变化时重建，用内容 revision 做键。
- **减少中间对象。** `Vertex`／`Group` record 改为平铺数组或复用容器；`min/max` 用 float 比较，不再逐角创建 `Vector3f`。

#### 前测：4.6 实现约束

不能跨实例共享可写采样时间、权重或输出数组；渲染帧快照仍可能被多个视图／延迟批次消费，缓冲不能提前复用。保留多权重、骨骼层级、部件覆盖、morph、长度／宽度拉伸、透明度和取消检查。

#### 前测：4.7 验证与关闭条件

静态／单骨骼／多骨骼多权重、morph 与骨骼组合、父子层级、外部轨道覆盖、循环边界、反向／零速／暂停及多实例不同相位；对比逐顶点位置、法线、UV、包围盒和输出图像。关闭条件：同内容 CPU 与渲染线程分配明显下降，GC／最长帧不恶化，计数器与姿态精度对照均通过。JFR 首次分配样本可能携带跨录制权重，不能将其全部归因一次调用，也不能以堆占用回落代替分配速率改善。

### 前测：五、P06／S09：首次创建、深拷贝和同步发射

#### 前测：5.1 现象

测量开始时创建 16 动画模型耗时 269.998 ms，对应最长帧 273.700 ms；首个静态实例创建 187.634 ms，后续单实例 9.885 ms。真实激光两轮首次最长帧约 860.759／855.774 ms，关闭 spark 后仍存在；真实首发帧含多种成本，尚不能把全部时间归给一个方法。

#### 前测：5.2 调用链

[PhotonChildSpawner.emitNow](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java#L220-L263) → `new PhotonSpawnInstance` + [`start`](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonSpawnInstance.java#L134-L180)（第 137 行 `runtime.emit`）→ 原生 `FXRuntime.emit` 的注入点 [FXRuntimeMixin](../../../src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/FXRuntimeMixin.java#L51-L88) → [PhotonNamedRuntime 构造](../../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java#L105-L130) → `PhotonModelOutput.prepare`；此外还命中 `PhotonModelSourceUtil.load`／`PhotonModelAssetUtil.read`、`FXHelper.loadFX` 和 `IFXObject.deepCopy` 的 CODEC 往返。

#### 前测：5.3 成本机制

1. **同步循环。** `emitNow` 在当帧按次数循环 `new PhotonSpawnInstance` + `start()`（第 235–260 行），没有分帧、预算或后台任务。
2. **每实例重新准备。** 命名运行态构造里 `PhotonNamedCodecUtil.read(controller.definition())` 重新解析命名库；随后对每个 `PhotonModelOutput` 调 `prepare()`。
3. **prepare 重新读取与重建。** `PhotonModelSourceUtil.load` 重新取资源／解析内嵌 JSON/NBT；`PhotonModelTopologyUtil.prepare` 对全模型逐面三角化；`new PhotonModelPoseSampler` 逐骨骼求逆；随后重建材质。
4. **实例来源本身深拷贝。** [PhotonSpawnTemplateUtil.create](../../../src/main/java/org/unitego/lobecorp/util/photon/playback/PhotonSpawnTemplateUtil.java#L35-L96) 走 `fx.createRuntime(true)`（原生 `FXData.copy` → 每个对象 `IFXObject.deepCopy` 的 CODEC 往返），或 `object.copy(true)` 后逐对象复制并改写动画轨道。
5. **深拷贝链的共享不足需按路径量测。** 当前 shallowCopy 已共享 sampler／groupOrder；本轮命中的深拷贝及 prepare 链仍有同步准备成本。是否每实例重复、重复哪些阶段，必须按调用路径和内容代次统计，不能概称所有实例零共享。
6. **技能句柄的预备范围有限。** QueenBeamEffects 在 CHARGE 且 preparedOutput=false 时调用 prepareNext；失败时后续更新仍可重试，不能说只调用一次。成功后该句柄置 preparedOutput=true，未主动为后续消费补下一份；通用发射器的预备和补充范围仍需按实际路径核对。

#### 前测：5.4 优化建议

1. **先补细分（不改行为）。** 在 `emitNow → createRuntime → buildNamed → codec read → load → topology → sampler → materials → emit → 首次几何／GPU` 每段用现有 `PhotonPerformanceTraceUtil` 打点，分清新进程冷、同进程热、资源重载后首次和间隔施放。
2. **内容级共享（核心）。** 以资源 id + 内容哈希／代次缓存不可变定义、Prepared 拓扑、骨骼逆矩阵和预编译权重；`PhotonModelSourceUtil.load` 与 `PhotonNamedCodecUtil.read` 增加缓存；`prepare()` 仅在内容代次变化时重建。多实例只保留各自可写时钟、槽和材质。
3. **深拷贝去重。** 评估 `FXData.copy`／`IFXObject.deepCopy` 中哪些字段必须每实例复制、哪些可共享只读定义；必须先核对原生 API 允许的共享边界。
4. **准备调度。** 把“下一份”扩展为有界预备缓冲池或分帧任务；但不能把成本静默转移到进世界，首次未就绪、失败重试、取消、退出世界都要有明确生命周期。

#### 前测：5.5 实现约束

不共享活 `FXRuntime`、可写轨道或实例随机状态；不把静默预热移到进世界时后宣称总成本消失；技能时序／伤害不能因准备策略改变。

#### 前测：5.6 验证与关闭条件

同资源 1／多实例、连续与间隔发射、缺失／损坏资源、加载失败后修复、重载、准备中取消及退出世界；分列触发至可见延迟、最长帧、P99、准备次数、分配峰值和就绪状态。关闭条件：首发阻塞实测下降且没有转移为同等级进世界卡顿、准备队列堆积或首次漏播；按真实技能截图／阶段 marks 验证，不能只用合成网格成绩。

### 前测：六、P13：空场景异常与 OpenGL 等待的独立归因

#### 前测：6.1 现象与边界

无 Debug 的末尾空场景第 7624／9695 帧为 1.421 秒；不能把这个值算成模型成本。真实采样中 `glFenceSync` 栈还来自 Minecraft／GUI／雾／AnvilLib／Sodium 的 `MappableRingBuffer.rotate`／`DynamicUniformStorage.endFrame`，不能全部记在 Photon 名下。

#### 前测：6.2 归因方法

1. 重复空基线，采集 GC 日志、JFR（gc／thread park／monitor／io／socket／driver）、帧起止时间、JFR 启停与落盘时间点、截图／导出时间点、窗口焦点与节流标志。
2. 把异常帧与上述事件做时间对齐；优先排除 JFR 自身 flush、GC 停顿、线程停泊、文件导出、驱动等待、Sodium／AnvilLib 上传。
3. 能稳定复现并定位到具体等待后再改代码；不能复现则保留未归因状态及原始样本；没有时间对齐证据，不能直接按环境干扰结案。

#### 前测：6.3 关闭条件

复现时能给出时间对齐的原因，或重复试验明确界定为当前模型外的干扰；“后续没有再看到”不等同于已修复。报告保留异常、失焦、容量丢样和失败原因，不能只删长帧或只报中位数。

### 后测：法线缓存后的真实激光复测（2026-10-24 11:21）

后续 Sodium＋BenchMod＋Minecraft MCP＋Debug 的激光复测与优化方案见 [真实女皇激光测试与优化方案](photon-laser-optimization-20261024.md)。本段保留 11:21 那一轮“无 Debug、无 spark”的记录，两轮口径不同，不合并数字。

用户后续要求“测试激光”，本轮只运行既有 QueenBeamPerformanceTrial 并更新记录，生产代码基于 bd29ff6；没有修改动画、LOD、资源、技能时序或伤害。IDEA Client 启动，Sodium 0.9.2 日志确认；本轮使用真实技能夹具与 PhotonFrameCapture，未重跑 BenchMod 合成用例，未启用客户端 spark 或 Debug，不能补作新的方法栈归因。

- 11:19:24 开始采集，90.0008 秒、52033 帧，状态 DURATION；三次服务端施放均 STARTED，每次都有客户端 0→1→2 和移除记录。首轮阶段 0→1 约 1.576 秒，后两轮约 0.951 秒；这是客户端观测时刻，首发阻塞会延迟记录，不能据此推断服务器技能时长改变。
- 全部帧 flags=3（focused／maximized），节流全部 NONE，帧缓冲全部 2560×1494，Vsync 关闭。GPU 完成 44310，pending 跳过 7723，错误 0；缺测未填零。
- 第四次施放仅供采集结束后的截图验证，未计入性能数据。真实截图可见女皇、法阵、粉色光束和目标；未发现该静态输出帧中整体缺失或明显破面。单张截图不能证明连续法线、动画过渡、跟随、墙体截断及全部材质表现均通过。
- code_animation_render_samples 仍为空，allocation_capture.enabled=false；没有新增逐帧动画混合或分配调用栈证据。

| 指标 | 本轮均值／P99／最大值 ms | 既有无客户端 spark 基线均值／P99／最大值 ms |
|---|---|---|
| 相邻渲染 HEAD 帧间隔 | 1.730／8.529／608.366 | 2.235／11.587／855.774 |
| GameRenderer.render CPU | 0.938／3.460／441.588 | 1.203／5.560／543.929 |
| GameRenderer.render GPU（有效样本） | 0.657／3.717／441.202 | 0.660／4.332／546.163 |

渲染线程分配均值 274799 B／帧，最大 219394536 B（约 209.2 MiB），包含采集开销，不是存活内存。CPU／GPU 区间范围不同，不相加，也不将 GPU 等待直接归因某一个 Photon 方法。帧间隔属于前一个 HEAD，表中同数组索引的 CPU／GPU 属于当前渲染体，不能当作长间隔的精确同步栈。

| 观测窗口（相对采集秒） | 均值 ms | P99 ms | 最大 ms | 大于 50 ms 帧数 |
|---|---:|---:|---:|---:|
| 0～4 空基线 | 1.321 | 2.764 | 19.066 | 0 |
| 4～16 首次施放附近 | 5.319 | 16.292 | 608.366 | 5 |
| 31～43 第二次施放附近 | 3.768 | 13.286 | 81.208 | 1 |
| 58～70 第三次施放附近 | 3.652 | 12.600 | 89.218 | 1 |

窗口按当前 HEAD 时间选取，边界附近的前一间隔可能跨界；这些窗口包含技能外时段，不是纯光束输出成本。首发 608／519／561／413 ms 长间隔集中于约 4.70～6.19 秒，另有 82 ms；后两轮首次阶段附近仍有 81／89 ms。**P06 首发阻塞未解决，热施放也仍有明显尖峰。** 本轮数字较旧轮降低，但粒子随机种子未固定、实际渲染相机／投影未额外采集、主机帧节奏仍缺受控配对；不能把差值全部归因法线缓存，P11／P14 不关闭。

原始 JSON、阶段 marks、verification.png、latest.log 与 analysis.json 归档于 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-normal-cache-laser-20261024。源报告文件名含 frames-20261024-032054-413-68018122667100 与 frames_marks_68019164953400。文档修改前字节也保留在同目录。

11:21 夹具 COMPLETE 并恢复临时 Options，随后经固定 19877 MCP 正常保存退出世界、退出游戏；IDEA Client 日志 BUILD SUCCESSFUL in 3m 8s。桥接 PID 47908（03:19:14.370Z）和 14132（03:21:16.550Z）均退出 0。游戏 PID／启动时刻本轮未单独取得，不伪造进程账本；退出证据为正常界面链、Stopping! 与运行结束日志。本轮未建立 Debug 会话或断点，未推送。

### 后测：法线矩阵缓存实施与前后对照（2026-10-24）

此段记录用户后续授权的实现与对照，与下方 DS 只读诊断轮分开；当前问题仍统一维护在 [唯一问题清单](../../issues.md)。

#### 后测：修订与实际改动

- 修正“相机原点每帧必变”“实例零共享”“采样比例可直接换算收益”和分配口径表述；同时澄清 prepareNext 失败可在蓄力阶段重试，不能说只调用一次。P13 未复现不能直接按环境干扰结案。
- PhotonModelOutput 为每个输出独占持有 NormalCache；键为世界矩阵 3×3 线性部分的逐位值与 JOML PROPERTY_ORTHONORMAL，缓存逆转置矩阵和行列式。平移不入键，旋转／缩放／镜像／奇异变化须失效，属性变化也须保持 JOML 分支语义。
- 最终版本将缓存结果复制到调用局部 Matrix3f，保留原顶点循环主体，避免循环消费共享可变矩阵的别名。该选择旨在减少方法拆分／逃逸对 JIT 的潜在干扰，没有证据断言它就是首版波动的根因。
- 没有改变每三角形 4 角的 QUADS 提交、排序、颜色、光照、shader、材质、动画、LOD 或资源内容；没有加入全局缓存、后台任务或持久 GPU 资源。

#### 后测：正确性与路径检查

- 三份 Java 文件 IDEA 0 错误，9 项既有提示（输出类 1 项、测试 Collector 空值注解 8 项）。新增警告已修正，没有借机清理存量提示。
- PhotonModelGeometryTest 5／5 通过，最终格式收尾后再次运行的 XML 时间 2026-10-24T03:08:09.890Z，failures=0／errors=0。新增 3 个用例覆盖平移、旋转、非均匀／负／零缩放、正交属性变化、实例隔离，以及 shading／insideOut／透明度组合下的位置、法线、颜色、UV 和光照完全对照。56 组新旧绘制输入对照通过；这不代替全部真实材质的视觉验收。
- 调试时 Render thread 在 NormalCache.update:96 命中 valid=true，原生结果相等、平移键不变、旋转／镜像／零缩放键改变；单步直接到 return normal:107，确认跳过重算分支。Sodium=true、MCP=true、GLFW_MAXIMIZED=1。
- MCP 固定 19877 得到 pong／connected=true，保存 2560×1494 调试截图。临时断点显式移除，随后正常保存退出世界并退出游戏；PID 54368（2026-10-24T02:30:01.285Z 启动）已退出，桥接 PID 54708 退出 0，Debug 会话为空。暂停轮仅作路径证据，不计性能。

#### 后测：本轮运行账本

| 运行 | 报告／归档 | 实际结果与边界 |
|---|---|---|
| 原实现首次基线 | client-1792807664435／before-invalid-focus | 因 focus_lost 为 INCONCLUSIVE，不参与收益比较 |
| 原实现重跑 | client-1792808072981／before-valid | PASSED、environment.valid=true；运行前两份生产源码 SHA256 与原始备份一致 |
| 缓存首版 | client-1792808615293／after-first | PASSED；静态改善但动画变慢、空基线波动，未采用该版作为最终实现 |
| 最终候选第一轮 | client-1792809307255／after-valid | PASSED、environment.valid=true，8 个 Photon 用例通过 |
| 最终候选复核轮 | client-1792809690358／after-repeat | PASSED、environment.valid=true，但空场景和轻负载出现约 6.15 ms 节奏，不能直接用于收益归因 |

上述无 Debug 运行均走 BenchMod Photon，包含最终报告校验；优化前重跑、最终第一轮与复核轮的构建耗时分别为 3 分 50 秒／3 分 45 秒／3 分 42 秒。报告参数及加载 Mod 清单相同，git 均记录 c168b04a＋dirty；具体代码差别另以原始文件／编译类及源文件指纹保留，不以 dirty 标志隐藏版本差别。

#### 后测：帧时间实测

每格为均值／P99／最长帧，单位 ms。保留全部结果，不跨会话平均 P99；复核轮的统一节奏也没有被剔除为“通过的优化”。

| 负载 | 原实现有效轮 | 最终候选第一轮 | 最终候选复核轮 |
|---|---|---|---|
| 空场景 | 2.253／4.999／29.104 | 2.204／5.210／23.651 | 6.165／8.081／19.549 |
| 1 静态 | 3.189／7.206／10.245 | 2.271／4.810／25.971 | 6.152／7.762／14.336 |
| 1 动画 | 3.296／6.694／16.705 | 2.198／4.793／17.949 | 6.192／8.372／18.426 |
| 16 静态 | 4.455／9.421／19.593 | 3.322／5.785／22.253 | 6.913／12.681／20.379 |
| 16 动画 | 6.552／12.633／29.411 | 5.827／11.480／17.514 | 6.611／12.374／36.560 |
| 测量开始时创建 16 动画 | 6.819／13.877／102.356 | 6.281／14.377／281.435 | 6.463／12.014／69.394 |
| 16 静态重复 | 3.498／6.103／13.924 | 3.256／5.685／13.788 | 6.179／7.614／34.422 |
| 空场景重复 | 1.706／3.490／7.274 | 1.477／2.920／7.588 | 6.154／7.341／9.278 |

原实现与最终第一轮中，16 静态均值观测下降约 25.4%、静态重复约 6.9%、动画约 11.1%，但不是已证明的稳定收益。首次创建用例 create_emit 为 98.278／275.995／61.419 ms，本轮没有修复 P06，也不能删除 281.435 ms 的反向长帧。

#### 后测：JFR 归因与可比性限制

以 ActiveRecording.recordingStart 加报告阶段耗时近似切出 MEASURE，保留毫秒级窗口误差。原始分析见 normal-cache-analysis.json。

| 负载 | 原实现渲染样本：write／normal | 最终第一轮：write／cache.update |
|---|---|---|
| 16 静态 | 202／286，normal=59／286 | 205／283，update=68／283 |
| 16 动画 | 89／431，normal=27／431 | 89／417，update=18／417 |
| 静态重复 | 200／304，normal=58／304 | 232／322，update=65／322 |

- normal 标签未再出现，但相近比例转到 cache.update，完整 write inclusive 比例没有整体消失；JIT／内联后的符号归因不能直接转为可消除的墙钟成本。Debug 可以证明命中时未重算，不能证明整体消除了原“20%～25%”成本。
- 同口径 ThreadAllocationStatistics 首尾计数差（整份录制，含 setup／预热）中，16 静态为 1.084→1.197 GiB、动画为 16.951→19.854 GiB。两轮帧数不同；这些是累计分配而非存活量，本轮也没有证明总分配下降。P12 继续开放。
- 复核空尾场景的 2437 帧中有 2279 帧处于 5～7 ms；原实现为 8／8794，最终第一轮为 17／10157。窗口门禁通过不代表帧节奏与主机条件相同。
- 只读核对了项目后台控制规则及 MCP 0.3.0 的 FramerateLimitTrackerMixin：该 Mixin 在开发接管时仅令 throttle reason 为 NONE；没有找到能解释该约 6.15 ms 节奏的原版 AFK 分支。本轮没有证明节奏由 MCP、Sodium、驱动、操作系统或其他负载中的哪一项造成；没有手工改用户 Options、驱动或其他工作区；隔离测试目录的 options 由已有 BenchMod Prepare 按既有配置生成。
- 实际查看前后静态网格截图，网格形状与数量可见，但地平线／模型投影位置不同；夹具没有保存实际渲染相机／投影状态，也没有锁定全部世界动态内容。相同配置参数不足以证明严格同视角 A/B，不能把第一轮降幅全部归因法线缓存，也不能据截图宣布全部视觉表现已验收。
- 本轮没有重新进行真实激光、多材质／光影的完整视觉验收；未改资源与动画内容，当前同输入顶点属性一致性由上述数值测试支持。

**结论：缓存逻辑已实现且正确生效，稳定性能收益尚未得到充分验收。** 保留 P11 的整体几何提交问题及新增可比性问题，不能按“法线缓存已做”关闭全部 P11／P12／P06。后续应先记录并核对实际相机和帧节奏，再做受控配对，不用继续叠加无关优化掩盖不确定性。

#### 后测：证据与清理

备份根：C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-normal-cache-20261024。

- ds-effective-run、before-invalid-focus、before-valid、after-first、after-valid、after-repeat 保存各轮完整原始结果；首版源码／编译类另留 candidate-first-geometry，优化前编译类亦保留。
- debug-evidence.json、after-debug-frame.png、geometry-test.xml、geometry-test-final.xml、frame-comparison.json、normal-cache-analysis.json 保存路径、数值与分析证据；原始基线和最终版源文件指纹见 source-fingerprints.json；自有游戏 PID／JVM 启动时间见 process-ledger.json。
- 自有基准游戏由工具链正常结束；Debug 游戏通过 MCP 正常保存退出并退出程序。已按 PID 与启动时间核对本轮原始实例结束（66264／48536／56192／54368／70392／57196），桥接 55012／54708 退出 0。本轮原 PID 56192 的实例启动于 10:22:49；11:02 再读该 PID 已被 10:58:11 启动的新进程复用，该新进程不属于本轮，未关闭或操作。本轮临时断点已移除，不按 PID 数字或名称批量关闭进程。
- 未推送；没有新增翻译键或 Data 输出，没有修改 Gradle、动画、LOD 或资源文件。

### 后测：一、本轮实际执行

| 项 | 实际执行 | 结果 |
|---|---|---|
| BenchMod Photon（08:43，无 Debug） | `BenchMod Photon Prepare` + `BenchMod Photon` | 场景全部 PASSED，但 `client.environment.invalidation=client.window.focus_lost`，整体 INVALID；数字只留作对照，不用于结论 |
| BenchMod Photon（08:52，无 Debug） | 同一入口重跑，运行期间不切换窗口、不输入 | **PASSED**，`environment.valid=true`，8 个 Photon 场景通过，`sweeper-render` 按过滤器 SKIPPED |
| JFR 归因 | 每个场景独立 JFR（约 23 秒）；`jfr view hot-methods`／`thread-allocation`／`allocation-by-site`／`allocation-by-class`；`jfr print` 导出执行样本后统计 inclusive 栈 | 见第三节 |
| IDEA Debug | `Photon Bench Debug`，三处断点核对调用路径；该轮计时不作对比 | 见第四节 |
| Minecraft MCP | 通过本工作区桥接（`MC_MCP_PORT=19877`）调用 `ping`、`get_minecraft_status`、`debug_fields`、`screenshot_to_file` | `pong`；截图 2560×1494、1.7 MB；见第五节 |
| Sodium | 客户端日志确认 `lib/sodium-neoforge-0.9.2+mc26.1.2.jar`，`Running graphics bootstrap plugin sodium`，RTX 3060 Laptop（驱动 32.0.16.1664） | 见第六节 |

DS 运行时证据生成于 `build/modBench/raw-results/default/client/`（summary、report、JSONL、JFR、截图）；该默认目录会被后续运行覆盖。DS 有效轮现已固定备份至既有可视化根 `photon-normal-cache-20261024/ds-effective-run`，不得把后续默认目录内容冒充该轮数据。本轮第一次 INVALID 运行证据另存于 `%TEMP%/dsh-uQhxMZ/lobecorp-invalid-run-20261024`（临时目录，不随仓库保存）；上一轮有效基线另存于 `%TEMP%/dsh-uQhxMZ/lobecorp-bench-backup-20261024`。

### 后测：二、实测数据

条件：种子 7，2560×1494，窗口化，FPS 上限 260，Vsync 关，渲染距离 12，Java 25.0.2（JetBrains），Sodium 0.9.2，测量 300 tick。表中为 MEASURE 的 `client.frame.interval`。

| 场景 | 均值 ms | P95 ms | P99 ms | 最长 ms | 上轮有效基线均值／P99 |
|---|---:|---:|---:|---:|---|
| 空场景 | 1.964 | 3.161 | 3.967 | 13.219 | 1.811／4.089 |
| 1 静态 | 2.027 | 3.037 | 3.707 | 9.890 | 1.718／2.880 |
| 1 动画 | 2.430 | 3.777 | 4.843 | 13.154 | 2.073／4.106 |
| 16 静态 | 3.259 | 4.586 | 5.931 | 11.851 | 3.258／6.256 |
| 16 动画 | 6.454 | 9.443 | 12.129 | 17.591 | 5.784／10.477 |
| 创建时 16 动画 | 6.323 | 9.993 | 12.117 | 81.570 | 6.213／12.227 |
| 16 静态重复 | 3.358 | 5.039 | 6.230 | 14.586 | 2.851／4.346 |
| 空场景重复 | 1.672 | 2.698 | 3.570 | 16.340 | 1.645／4.671 |

创建／发射（`lobecorp.photon.create_emit`，每场景 1 个样本）：1 静态 188.9 ms（上轮 187.6）、1 动画 11.6 ms（9.9）、16 静态 118.4 ms（102.4）、16 动画 68.2 ms（73.4）、**创建时 16 动画 79.2 ms（上轮 270.0）**、16 静态重复 82.2 ms（75.8）。

结论：本轮与上轮同代码、同夹具，16 静态几乎完全一致（3.259 vs 3.258）；其余场景均值相差 5%～18%，属于运行间波动。**首次创建成本不是稳定常数**：50-cold-sixteen 的 79.2 ms 与上轮 270.0 ms 相差 3.4 倍，不能用单次 269.998 ms 当作固定基线，P06 必须单独做冷进程与真实激光测量。

### 后测：三、JFR 归因

#### 后测：3.1 渲染线程执行样本（inclusive）

| 场景 | 渲染线程样本 | `write` | `bakeBatches` | `sample` | `skinMatrices` | `positions` | `faceNormal` | `Matrix4f.normal` |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 16 静态 | 448 | 71.0% | 75.9% | 0.4% | 0.2% | 0.2% | — | **19.9%** |
| 16 静态重复 | 422 | 70.1% | 78.0% | — | — | — | — | **24.6%** |
| 16 动画 | 669 | 22.0% | 87.3% | **61.7%** | 22.9% | 13.2% | 10.3% | 6.0% |
| 1 动画 | 195 | 10.8% | 51.3% | 36.9% | 12.8% | 8.2% | 3.1% | 4.1% |

自耗样本（`hot-methods`）：16 静态 `write` 229／529 = 43.3%，`Matrix4f.normal` 89 = 16.8%，`PhotonBufferCache.write` 3.2%；16 动画 `write` 14.2%，`HashMap.getNode` 13.1%，`skinMatrices` 8.5%，`ArrayList.add` 3.9%，`ImmutableCollections$List12.get` 3.1%，`HashMap.computeIfAbsent` 2.9%，stream 相关多项合计约 8%。

与测试前判断一致：静态模型的姿态缓存有效（`sample` 仅 0.4%，`sampleLocalFrame` 2%），但提交几何仍每帧全量重写（`write` 71%）；动画则相反，`sample` 占 61.7%，`write` 降到 22%。

#### 后测：3.2 新增的精确子热点：`world.normal()`

`write` 第 19 行每个 group、每帧调用一次 `world.normal(new Matrix3f())`（一般路径求逆转置，正交属性路径直接取 3×3）并新建 `Matrix3f`。在 16 静态场景它单独占 **19.9%** 渲染样本，重复场景 **24.6%**，这些比例的分母和 inclusive／self 口径不同，不能直接相除为“write 自身的三成”。**相机平移不改变法线**；相机原点也不必每帧变化，但当前 write 每组调用仍重算法线矩阵。它是可独立验证的窄范围优化候选；采样归因和调用次数需结合 JIT／内联与同条件 A/B 判断，尚不能承诺收益。

#### 后测：3.3 分配

`jfr view thread-allocation`（整段约 23 秒录制，含 setup／预热）：

| 场景 | 渲染线程分配 | 占全进程 |
|---|---:|---:|
| 16 静态 | 9.8 GB | 68.1% |
| 16 动画 | 27.7 GB | 85.1% |

`allocation-by-site` 的动画侧（剔除 JFR 首个分配事件的跨录制伪权重：`JsonReader.<init>` 占 33.49%，与上轮审查记录的现象相同）：`skinMatrices` 13.78%、`DirectMethodHandle.allocateInstance` 10.04%、`immutable` 5.77%、`HashMap.newNode` 4.66%、`Integer.valueOf` 3.49%、`Arrays.copyOf` 3.25%、stream 相关约 6%、`sample` 2.67%、`faceNormal` 1.39%。`allocation-by-class`：`float[]` 10.0%、lambda 类 9.51%、`PhotonModelDefinition$Vector3` 5.77%、`Object[]` 4.77%、`HashMap$Node` 4.66%、`Matrix4f` 3.90%、`Integer` 3.49%、`Vertex` 2.67%。

静态侧剔除同一伪权重后没有模型相关分配热点，说明静态问题是 CPU 计算与顶点写入，不是分配。绝对 GiB 与上轮口径不同（上轮 0.984／19.554 GiB），本次只作同轮内相对比较，不与上轮直接相减。

### 后测：四、Debug 路径核对

Debug 运行仅核对路径，期间暂停过，计时不进入上表。

1. **静态提交路径**：断点 `PhotonModelGeometryUtil.write:24`（Render thread）命中；栈为 `write:24 ← PhotonModelOutput:480 ← bakeGroup:478 ← bakeModelGroup:450 ← PhotonWorldRenderStateMixin:29 ← GameRendererMixin:22`。变量 `indices.size()=1536`（512 三角形），`part.shading()=false`、`doubleSided()=true`、`insideOut()=false`，`world` 行列式 8.0（uniform scale 2）。确认每帧按 512×4 角写入，且夹具未开启 shading，**实际 shade() 成本未被夹具覆盖**。
2. **动画采样路径**：断点 `PhotonModelPoseSampler.sample:150`（Render thread）命中；栈为 `sample:150 ← PhotonModelOutput.sampleLocalFrame:403 ← sampleLocal:361 ← bakeBatches:437`。`Input[time=0.039960623, clip=turn, loop=true, anchor=START]`，时间每帧变化，确认动画每帧 cache miss 并整帧求值。
3. **首次准备路径**：断点 `PhotonModelOutput.prepare:179`（Render thread）命中；栈为 `prepare:179 ← reset:241 ← FXRuntimeMixin:111(emit) ← PhotonGeoEffect:44 ← PhotonModelRenderScenario:134`，`sampler=null` 进入 `loadDefinition()`。确认准备（读取、解析、拓扑、骨骼、材质）发生在 emit 当帧的渲染线程上。

### 后测：五、Minecraft MCP 证据

同一调试会话内通过标准 MCP 协议（桥接 `dist/cli.js mcp --no-discover`，`MC_MCP_PORT=19877`）调用：`ping` → `pong`；`screenshot_to_file` 输出 `build/modBench/debug-mcp-screenshot.png`（2560×1494，1,735,580 字节），画面可见测试世界、模型网格与玩家；`debug_fields` 返回 `net.minecraft.client.Minecraft` 的字段概览（不是 F3 文本，工具语义与名称有偏差，已按实际记录）；`get_minecraft_status` 返回 `connected=false / port=null / processAlive=false`，与 `pong` 成功并存——该次状态回读与命令响应不一致，不能仅据此断言是启动器视角或游戏已断开；需结合端口、同次会话与实例归属判断，不能忽略状态矛盾。

### 后测：六、环境与边界

- 日志确认 Sodium 加载、Ivy/Iris 版本段存在；本轮基准使用 Sodium 无光影客户端，不代表 Iris 大负载或光影场景。
- 第一次运行被判定 INVALID 的字段是 `client.environment.invalidation=client.window.focus_lost`、`verdict=invalidated`；该轮数据（空场景 3.769 ms 等）只作“失焦时不可比”的示例，不进入对比。
- 夹具固定 17×17 顶点、256 个四边面、单骨骼、part.shading=false、doubleSided=true；不能代表真实特效的多材质、shading、骨骼层级与透明交叠。
- P13 的空场景 1.421 秒长帧本轮**未复现**：有效运行 `70-empty-repeat` 最长 16.340 ms，无效运行 6.77 ms。因此不能宣布已修复，仍按“未归因异常”保留。

### 后测：七、对测试前建议的修正

1. **P11 优先级调整**：先处理 `world.normal()`／法线矩阵的每帧重算。做法：把 `world` 拆成“相机平移”和“模型旋转／缩放”两部分；法线矩阵只由后者决定，可在模型朝向／缩放不变时跨帧复用，相机移动时只更新顶点位置。20%～25% 是当轮采样归因，不是可直接消除的墙钟比例或预期帧率收益。仅缓存法线矩阵及其行列式，按线性部分和 JOML 正交属性失效；保持 QUADS 四角、材质与排序语义，并以数值对照及实测决定收益。之后再做法线／shade 预缓存、角点预展开与矩阵手写乘。
2. **P11 的 shade 覆盖缺口**：夹具 `shading=false`，真实资源若启用 `shading`，`shade()` 每角 6 次乘加的成本会额外出现；下一轮夹具应增加 shading=true 变体，否则不能用当前 71% 直接推断真实资产。
3. **P12 维持原方向但顺序更明确**：`sample` 61.7%、`skinMatrices` 22.9%、`positions` 13.2%、`faceNormal` 10.3%。优先预编译权重／索引与轨道检索，再替换 `HashMap<Integer, Vector3>`、两份 `float[16]`、`Matrix4f` 与 stream 归一化；`morph` 整份复制（4.0%）也要覆盖“权重全 0 仍复制”的路径。
4. **P06 不能沿用 270 ms 基线**：同夹具两次有效运行分别为 270.0 ms 与 79.2 ms；Debug 已确认 prepare 在 emit 的渲染线程栈内。下一步必须先做冷进程、同进程热、真实激光三类分开测量，再决定内容级共享与分帧准备的范围。
5. **P13**：两次运行均未复现，维持未归因；下一轮用 GC／线程／IO 时间对齐而不是继续加模型样本。
6. **分配口径**：JFR 首个分配事件可能带跨录制权重（`JsonReader` 33%～77%），不能当作真实分配；本节尚未统一 thread-allocation 视图估计与 ThreadAllocationStatistics 首尾计数差的算法；GB／GiB、录制阶段、首事件权重和分母均须标明。同轮数字也只能在同口径、同窗口、同线程时比较；跨轮绝对量不能直接相减或用于推算收益。
