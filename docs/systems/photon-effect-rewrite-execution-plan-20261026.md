# 特效底层重构执行计划（交接版）

更新：2026-10-26 12:20。读者：后续接手的 AI/人。目标：**即使本次会话上下文丢失，只读本文件＋链接文档即可继续实施**（R1→R2→R3→R4→R5→R6）。本文只写计划与操作手册，不代表代码已完成；实施状态以 `docs/systems/photon-workspace.md` 最新进度为准。

## 0. 一句话现状

- 已完成：首次释放**tick 侧**从 **695.7 ms → 64.4 ms（−90.7%）**（预热方案，提交 `3f41ff79`）；动画采样分配 −59～72%（提交 `37f70cc2`）；**R2 首帧渲染侧预构建**把首施放最长帧 **680.3→129.0～167.5 ms**、>250 ms 帧 3→0（默认开启，见 §7.4）。
- 未完成：首施法仍有 52～167 ms 残余长帧；后续每次释放约 **190～250 ms**（`prepareNext` 18～38 ms/次、运行态深拷贝等）；根因是运行时把不可变内容与可变状态绑死，每次播放整树深拷贝并重建材质/类/音频。
- 下一步：R1 已做 A/B（**阴性，默认关闭**，见 §6.0）；R2 已实施并 A/B（**部分正面**，见 §7.4）；下一步优先 **R2 残余（未覆盖变体/首次模拟类加载/同相位像素对照）→ R3（子实例模板缓存，`prepareNext` 18～38 ms/次）→ R4 几何资产空间缓存**，再决定是否继续池化。

## 1. 恢复上下文必读（按顺序）

1. `docs/systems/photon-workspace.md`（最新进度与边界）
2. `docs/systems/photon-performance-audit.md` → 节 `2026-10-26 复测与瓶颈复核（进行中，未验收）`、其内 `首次释放专项诊断（进行中）`、`剩余优化空间评估`、`本轮复测结果`、`旧结论逐条复核`
3. `docs/issues.md`（唯一问题清单：P19、P22、P23 等）
4. `docs/systems/photon-effect-architecture-rewrite-20261026.md`（目标架构与阶段定义）
5. 本文件（执行手册）
6. 原始证据：`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026/`（`first-cast/`、`runs/`、`analysis/`）

## 2. 环境与硬约束

- 工作区：`E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_photon`，分支 `codex/photon`；**不推送**，不自动改写历史。
- IDEA：项目已在 IDEA 打开；`idea` 与 `intellij-debugger` MCP 当前返回 session 错误，**用 `intellij-index` MCP**（所有调用带 `project_path=E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_photon`）；项目文件（Java/Markdown/Gradle）只经 IDEA 工具编辑；`ide_build_project`/Gradle 构建均可。
- 构建：JBR 25：`C:\Users\wang_\AppData\Local\Programs\IntelliJ IDEA\jbr`；命令用 `JAVA_HOME=<jbr> ./gradlew.bat <task> -PphotonBench=true --offline --console=plain`。Gradle 9.6.1，配置缓存开启。
- Git：`idea-mcp-expand` 的 `git_background` 当前不可用；用 `ops_bash` 直接 `git`，只 `git add` 明确文件、`git commit ... -- <paths>`，保留用户已暂存内容。用户 WIP（**不要提交/覆盖**）：`.codex/config.toml`（未暂存修改）、`src/test/java/.../PhotonModelPoseSamplerTest.java`（未暂存修改）、`src/test/java/.../PhotonModelStagedResourcesTest.java`（已暂存）、`logs/`（未跟踪）。
- 游戏测试规则：Sodium 全程加载；Spark：正式性能轮进入测试后**全程开启**直到结束（诊断轮可关但必须记录）；窗口聚焦/最大化，失焦轮无效（夹具记录）；每轮截图归档；**不强制结束客户端**；测量区间不设断点；测试结束按正常退出等输出。
- 关键属性：`-PphotonBench=true`（开基准源集与场景）、`-PphotonShader=<bool>`（隔离 Iris 配置）、`-PphotonSpark=<bool>`（spark backgroundProfiler）、`-PphotonRequireFocus=<bool>`（BenchMod 焦点门禁；夹具另有自己的 `focus_audit`）、`-PphotonScenarios=<filter>`（场景过滤器）。
- JVM 属性：`-Dlobecorp.photon.bench.probes=<bool>`（高频提交探针，默认 true）、`-Dlobecorp.photon.packedSnapshot=<bool>`（整块提交实验，默认 false）、`-Dlobecorp.photon.prewarmBeam=<bool>`（首次释放预热，默认 true）、`-Dlobecorp.photon.diag.cast=true`（CASTPROF 分段日志）。

## 3. 基线证据（做 R1/R2 时必须复现同条件）

### 3.1 首施法单场景（每轮重开客户端）

| 轮次 | 改动 | 首施法 tick 总耗时 | 首施法长帧 max |
|---|---|---|---|
| run1 | 无预热 | 695.705 ms | 未归档（FAILED） |
| run2 | 预加载 FX 定义 | 194.965 ms | 未归档 |
| run3 | ＋预建运行态壳＋子实例预准备 | 62.690 ms | 566.4 ms |
| run4 | ＋命名运行时预热 | 59.172 ms | 557.6 ms |
| run5b | 128 格内有女皇才预热 | 64.402 ms | 545.9 ms |

run1 分段（CASTPROF）：`fxDefinition 167.6`、`createRuntime 52.993`、`childDefinition 270.920`、`controllerSetup 0.795`、`anchor 0.175`、`emit 71.795`、`namedBegin 15.235`、`prepareNext 115.952`。

run5b 首施法长帧：545.9/424.8/307.4/153.1/141.5/82.5 ms；JFR 同窗口成分为 Mixin 类转换/加载、ShaderGraph GLSL `#import` 探测异常（每窗口 18～22 对 `NoSuchFileException`+`WindowsException`）、JSON 节点图解析、OGG 解码；其中 135 ms 是夹具截图自身。

### 3.2 复测轮（E2/E3 混合场景，同相机对照）

- 采样分配优化（P0→P2b，同相机/探针/spark）：40-animated 分配 5.508→1.740 MiB/帧（−68%）、90-animated 10.882→3.047（−72%）、91 7.323→2.276、92 17.223→6.975；40/90 均值 −20.7%/−38.5%。
- 探针净差（S1→S0，同代码/相机）：91 12.879→8.202 ms（−36%）、92 15.233→12.332（−19%）。
- 92 仍每轮施法 187～232 ms，Codec 27%～38%、`IFXObject.deepCopy` 16%～26%。

### 3.3 提交与证据

- 提交：`39653303`（四组对照报告）→ `37f70cc2`（采样分配治理）→ `3f41ff79`（首次释放诊断与预热）→ `1100a7e8`（架构方案）→ `ebc2b08`（剩余优化评估）。均未推送。
- 证据根：`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026`：`first-cast/`（run1～5b 日志、`run3|run4|run5b-artifacts/`、`analysis/FirstCastWindows.java`）、`runs/`（P0/P2b/P1c/S1/S0）、`analysis/bottleneck-draft.md`（JFR/分配/施法的完整离线分析）。

## 4. 当前代码地图（R1/R2 需要改的地方）

### 项目内（主要修改面）
- `src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java`
  - `tick()`：快照遍历，`new Beam(...)` 触发整树创建；`RETIRED/PLAYING`。
  - `Beam` ctor：`FXHelper.getFX(effectId)`→`new PhotonGeoEffect(definition, level)`→控制器 definition 改写→`anchor()`→`effect.start()`（emit）→回调→`animations().play("charge_start")`。
  - 首次释放预热：`PREWARM`、`PREPARED`（`PhotonGeoEffect` 壳）、`prewarm()`（定义+壳+`spawner.prepareNext()`+命名运行时）、`hasQueen()`、`discardPrepared()`；`Beam` ctor 里 `PREPARED.remove(id)` 采用壳。
  - `finish(UUID)`：`beam.effect.close()`（`stop(true)` → `runtime.destroy(true)`）。
- `src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffect.java`：`start()/stop()/isAlive()/anchor()`；R1 需增加 `restart()`/可复用状态。
- `src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java`：`prepareNext()`、`nextTemplate()`、`discardPrepared()`、`emitNow()`；R3 重点。
- `src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java`：命名播放/混音/事件/retention；R1 reset 重点。
- `src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/FXRuntimeMixin.java`：`emit` 上挂 named runtime 构建、`destroy` 关闭；R1 复用语义依赖这里。
- `src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonSpawnTemplateUtil.java`（在 `util/photon/playback/`）：`create()` 子 FX 深拷贝；R3 重点。
- `src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java`：`GeometrySnapshot.matches`/`bakeBatches`；R4 重点（`world` 含 `-renderOrigin`）。
- `src/main/java/org/unitego/lobecorp/client/photon/performance/PhotonCastProfiler.java`（新增）：`-Dlobecorp.photon.diag.cast=true` 输出 CASTPROF。
- 基准：`src/bench/java/org/unitego/lobecorp/client/bench/PhotonModelRenderScenario.java`（`singleCast` 分支）、`PhotonBenchCapture.java`（单施放 20 s、1 个 phase）、`src/bench/java/org/unitego/lobecorp/registry/bench/LobecorpBenchProvider.java`（`lobecorp.photon-00-first-cast` 首个注册）、`build.gradle.kts`（`photonScenarios/Shader/Spark/RequireFocus`、spark 配置）。

### Photon 库（不改源码时的适配/窄 Mixin 目标）
- `com.lowdragmc.photon.client.fx.FX#createRuntime(boolean)` → `new FXRuntime(fxData.copy(deepCopy))`（每次播放整树 CODEC 往返的源头）。
- `com.lowdragmc.photon.client.fx.FXRuntime`：`objects/root/timelinePlayer`、`emit/destroy`（文档声明可 destroy 后 re-emit 复用，是 R1 的库级依据）。
- `com.lowdragmc.photon.client.gameobject.IFXObject#deepCopy`、`FXData#copy`。
- `com.lowdragmc.photon.client.gameobject.emitter.Emitter#bakeJob/bakeGroup`（每材质每视图 64 KiB builder，稳态热点，非本轮）。
- `com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial#getRenderType` → KilaGraph `RenderTypeFactory` 的 `getOrBuildPipeline/buildPipeline/GlslImportProcessor`（R2 目标；GLSL import 探测异常 77% 来源）。
- 项目已有 Mixin：`FXRuntimeMixin`、`TimelinePlayerMixin`、`PhotonWorldRenderState` 提交探针等。

## 5. 目标架构与不变量

见 `photon-effect-architecture-rewrite-20261026.md`。执行时必须守住：
- Asset 不可变；GL 资源随 Asset；Instance 池化且显式 reset；延迟 drain 用 generation 校验；资源重载 revision 失效；相机/位置不失效。
- 等价性：透明排序、法线/光照、动画时序（partial tick/循环/优先级/事件）、材质变体、LOD/资源、随机数、所有权。
- 每个新路径必须有开关（默认关闭或可回退）与旧路径回退；没有同条件 A/B 与视觉对照不得宣布收益。

## 6. R1：运行态池化复用（已做 A/B：阴性，默认关闭）

### 6.0 实施结果（2026-10-26 12:22，必读）

实现：`PhotonGeoEffect.restart()`（`stop(true)` 后重新 `emit`）＋`QueenBeamEffects` 的 `IDLE` 池（`borrow/recycle/closeIdle`），开关 `-Dlobecorp.photon.poolBeam`（默认 false）；`finish` 正常回收，错误路径 `finishAndClose`。

同场景（`lobecorp.photon-91-mixed-thirtytwo-laser`，3 次真实施法，plain、探针关、spark 关、prewarm 开、新客户端）：

| 轮次 | 第 1/2/3 次施法 tick（ms） | 帧均值 | >50 ms 帧 | >100 ms 帧 |
|---|---|---|---|---|
| A pool off | 42.776 / 26.576 / 34.376 | 6.356 | 9 | 4 |
| B pool on | 32.033 / 37.055 / 42.317 | 8.181 | 16 | 5 |

结论：复用消除了 `createRuntime`（本场景仅 6.4/5.9 ms，`reuseEmit` 2.5～3.5 ms），但 `prepareNext` 从 18.4/26.9 升到 33.4/38.5 ms，第 2/3 次 tick 反而略升，帧数 B 明显少于 A（11002 vs 14161）。**R1 不单独推进，默认关闭**；保留代码作实验与后续池化基础。下一步优先 **R3（`prepareNext` 子模板缓存，18～38 ms/次）→ R2（首帧材质/管线预构建）**。

证据：`C:/Users/wang_/.codex/visualizations/2026/10/26/photon-reaudit-20261026/pool/`。

### 6.1 目标与验收
- 目标：消除每次释放的 `FXData.copy(true)/deepCopy`，复用同一 `PhotonGeoEffect` 运行态（`destroy→re-emit`），并显式 reset 命名/子实例/发射器状态。
- 验收：单场景连续 2～3 次施法（需把 `photon-00-first-cast` 扩展成可配置施法次数或临时用 91 场景）后，后续每次施法 CASTPROF tick ≤80 ms（目标 50～80）；首施法保持 ≤70 ms；无残留粒子/时间线偏移；`pose_gate/camera_gate/focus` 通过；同机位截图对比通过。
- 开关：`-Dlobecorp.photon.poolBeam=true|false`（先默认 false，A/B 通过后再默认 true）。回退：关闭即走现有每次新建路径。

### 6.2 实施步骤
1. `PhotonGeoEffect` 增加可复用状态：`started/stopped` 可重置；`restart(executor)`：若 runtime 已 destroy，则重新 `runtime.emit(executor)`；保留 `anchor()`。
2. `QueenBeamEffects` 增加实例池：`Map<Identifier, Deque<PhotonGeoEffect>> IDLE`；`finish()` 不 close，而是 `effect.stop(false)`（保留对象树）→ 放回池；`clear()`/换世界时真正 close 所有池内实例。
3. `Beam` 改为“可激活对象”：
   - ctor 拆成 `create(owner, snapshot)` 与 `activate(snapshot)`；字段 `effect/emitter` 可替换（或保留 Beam 轻量对象，effect 从池借用）。
   - 复用前必须：`namedRuntime.close()`/`lobecorp$prepareNamedRuntime()`（`FXRuntimeMixin` 在 emit HEAD 会重建）、`emitter.discardPrepared()`、`charge` 动画从 `charge_start` 重新 `play`、`phase`/`active`/`frozen*`/`inputHandle`/`beamInputs` 重置、控制器 definition 的 CONTROLLED 改写重放（幂等）。
   - 时间线：复用实例在 `emit` 前 `runtime` 时间需归零（检查 `TimelinePlayer`/root `reset` 是否由 emit 完成；若否显式 reset）。
4. 池容量：每个 effect id 最多 1～2 个空闲实例；超出 close；转换世界/`clear()` 时清空并 close。
5. 记录：池借用/归还/复用次数、每次施法 CASTPROF（`reuse=true/false`）、`PhotonPerformanceTraceUtil` 里已有 FX_EMIT/CLEANUP 阶段可用于对照。

### 6.3 测试命令（R1 A/B）
```text
基线（no pool）：JAVA_TOOL_OPTIONS='-Dlobecorp.photon.poolBeam=false -Dlobecorp.photon.diag.cast=true -Dlobecorp.photon.bench.probes=true' \
  JAVA_HOME=<jbr25> ./gradlew.bat runBenchClient -PphotonBench=true -PphotonShader=false -PphotonSpark=false -PphotonRequireFocus=false -PphotonScenarios=lobecorp.photon-00-first-cast --offline
实验（pool）：同上把 poolBeam=false→true。
```
需要多施法验证时：临时给 `PhotonModelRenderScenario` 增加 `singleCast=false` 版本或用现有 91 场景（`-PphotonScenarios=lobecorp.photon-91-*`，场景含 3 次施法，但注意它还有 32 模型负载；也可复制一个新场景 `photon-01-three-casts` 只放 3 次施法）。
判据：CASTPROF 第 2/3 次 `createRuntime` 应为 0（复用）、总 tick ≤80 ms；帧 >50 ms 次数不增加；截图与基线同相位对比无缺失。

## 7. R2：Asset 编译与材质/管线预构建（首帧 1.0～1.4 s）

### 7.1 目标与验收
- 目标：首次施法首帧从 1.0～1.4 s 降到 ≤50 ms（不含夹具截图；截图移出测量窗口）。
- 验收：run 单场景，`frames` 中施法前后无 >50 ms 帧；JFR 窗口无 `NoSuchFileException` 风暴（GLSL import）；材质/类/音频在预热阶段完成；截图/阶段 mark 与基线一致。
- 开关：`-Dlobecorp.photon.prewarmRender=true|false`（默认 false，验证后开）。

### 7.2 可行路径（按风险从低到高，先做低风险）
1. **预构建材料/管线**：在预热壳上，遍历运行时对象，找到其材质/渲染器入口，调用与真实绘制相同的 render type/material 构建（`ShaderGraphMaterial#getRenderType(setting, mode)` 或 emitter 渲染器的等价入口），把 KilaGraph 管线、类加载、GLSL import 解析提前；不发射粒子、不推进时间线。
2. **GLSL import memoize**：若 KilaGraph 的 import 解析仍每次探测资源，增加按（shader 源 + import path）内容的缓存（优先库公开钩子，其次窄 Mixin），消除 `NoSuchFileException` 风暴；同时保留原错误处理。
3. **受控隐藏预渲染**（仅当 1/2 不足）：把预热壳以 `rate=0`、不可见/远位渲染 1～2 帧，触发剩余首帧构建；必须验证无残留粒子、无时间线偏移、无可见伪影；通过后把壳复位（visible、rate=1，等待采用）。
4. **音频预解码**：预热阶段触发一次音频解码（按现有声音事件路径），避免首施法 OGG 解码抖动。

### 7.3 实施顺序
先 1 + 2（不动绘制路径、可单测：观察异常数与首帧窗口），不足再做 3；每步记录 JFR 窗口、帧 >50 ms 次数、异常计数（`analysis/FirstCastWindows` 可复用）。

### 7.4 实施结果（2026-10-26 12:52，部分正面）

- 已实现 §7.2 的 1（材质/渲染类型预构建）和 §7.2-4 的模型资源准备；对 §7.2-3 采用“逐 tick 预算队列”的替代实现（不做隐藏预渲染）：`client/photon/performance/PhotonRenderPrewarm`，开关 `-Dlobecorp.photon.prewarmRender`（**默认开**，false 关闭）。
- 覆盖：壳内每个发射器的材质/着色图/CPU 管线＋GPU 实例化变体（TILE/MODEL/BEAM/TRAIL/ARA）；`PhotonModelOutput.prewarmRenderTypes()`；递归子模板（`PhotonChildSpawner.preparedTemplate()`，深度 2 覆盖 15 个 spawner/50 个渲染类型）；队列每 tick 10 ms。
- 同条件单施法 A/B：最长帧 **680.3→129.0～167.5 ms**、>250 ms 帧 **3→0**、窗口均值 12.84→8.03～9.85 ms、首施法 tick 68.3→70.9～75.3 ms（未退化）。同步预热（无队列）反而更差（797.9/268.7 ms），作为反例保留。
- 未完成：剩余 +0.08～1.18 s 处 52～167 ms 长帧（疑为未覆盖的实例化变体与首次模拟类加载）；§7.2-2 GLSL import memoize 未实施（异常风暴已移出施放窗口，需复评）；同相位像素对照未做。
- 证据：`docs/systems/photon-performance-audit.md` 的“R2 首帧渲染侧预热 A/B（部分正面）”。

## 8. R3～R6（简版，实施时再细化）

- **R3 Spawner 分离**：`PhotonSpawnTemplateUtil.create` 改为“编译模板（FXData/子图/材质一次）+ 每实例状态”；`PhotonChildSpawner.prepared` 改多槽/按路径缓存；目标：连发/多束时子模板创建次数≈0，E2/E3 稳态 `deepCopy`/CODEC 占比下降。开关 `-Dlobecorp.photon.spawnTemplateCache`。
- **R4 几何资产空间缓存**：`GeometrySnapshot` 缓存局部空间顶点/属性；每帧用实例世界变换提交（uniform 或每批 offset），去掉 `world` 中 `-renderOrigin` 的失效条件；必须逐属性对照位置/法线/UV/光照与透明排序。开关 `-Dlobecorp.photon.assetSpaceGeometry`。
- **R5 批处理/排序**：按材质/通道合批、稳定排序键；对照 draw call 与透明交叠。
- **R6 可选 GPU**：VAT/实例化，仅当等价且有收益，CPU 回退常开默认路径。

## 9. 等价性测试矩阵（每阶段执行）

1. 单施法场景：首施法 tick/首帧、CASTPROF、phase marks、截图（`photon-00-first-cast-beam.png`）。
2. E2（91）与 E3（92）：帧均值/P99/最长、CPU/GPU/分配、drain/draw/reserve/resize、施法窗口逐次对齐。
3. 逐顶点属性：`PhotonModelGeometryTest`/`PhotonModelOutputSnapshotTest`/`PhotonPackedSnapshotReplayTest` 及新增属性签名对照（位置/法线/UV/颜色/光照、顺序）。
4. 透明排序：同深度交叠截图/场景；粒子叠加顺序对照。
5. 动画时序：同一 seed/相位下截图 + 阶段 mark + 动画时间对照。
6. 视觉：同机位同帧截图，优先同帧（记录 draw 帧序号/phase）；跨轮相机漂移时先修 P14/P21 或用固定相机夹具。

## 10. 运行/分析命令手册（可直接复制）

```text
# 环境
set JAVA_HOME=C:\Users\wang_\AppData\Local\Programs\IntelliJ IDEA\jbr
set EV=C:\Users\wang_\.codex\visualizations\2026\10\26\photon-reaudit-20261026

# 单施法（新鲜客户端，每轮重开；plain）
set JAVA_TOOL_OPTIONS=-Dlobecorp.photon.packedSnapshot=false -Dlobecorp.photon.bench.shader=false -Dlobecorp.photon.bench.probes=true -Dlobecorp.photon.diag.cast=true
.\gradlew.bat runBenchClient -PphotonBench=true -PphotonShader=false -PphotonSpark=false -PphotonRequireFocus=false -PphotonScenarios=lobecorp.photon-00-first-cast --offline --console=plain

# 完整 13 场景（正式性能轮；光影/plain 用 -PphotonShader 切换；spark 按规则）
.\gradlew.bat verifyBenchClient -PphotonBench=true -PphotonShader=false -PphotonSpark=true -PphotonRequireFocus=false --offline

# 分析首施法长帧（JDK25）
<jbr>\bin\java -cp "<EV>\first-cast\analysis;<gson jar>" FirstCastWindows <frames.json> <jfr>
# 指标提取脚本：<EV>\runs\extract_metrics.py；截图对比用 bundled python + Pillow
```

注意：
- `runBenchClient` 不做 verify；`verifyBenchClient` 会校验场景清单，新增/过滤场景时可能需要同步 `build.gradle.kts` 的 expected 列表，或接受 verify 失败但读取客户端生成的 report/artifacts。
- 隔离运行目录：`build/modBench/runs/default/client`；用户 `run/client/config` 不应被修改。
- 截图路径：`build/modBench/raw-results/default/client/artifacts/custom/*.png`，每轮归档到证据根。
- 测量期间避免使用会抢焦点的 IDEA/浏览器操作；需要时用 `-PphotonRequireFocus=false` 并检查夹具 `focus_audit` 与逐帧 flags。

## 11. 文档与提交协议

- 每个实质阶段（开始/完成/受阻）更新：`docs/systems/photon-workspace.md`（进度）、`docs/systems/photon-performance-audit.md`（数据与结论）、`docs/issues.md`（唯一问题清单）、`docs/systems/changes.md`（修改记录）。
- 提交：中文标题/正文；`git add` 只列本次文件；`git commit ... -- <paths>`；保留用户 WIP；不推送。
- 原件备份：`git show HEAD:<path> > <EV>/backup-<date>/<path>`；修改前先备份被改文件。
- 未验证的收益不得写成“已优化”；JFR inclusive 不相加、样本比例不当墙钟、GPU 缺测≠0。

## 12. 未决与风险（R1/R2 前需注意）

1. **Spark 口径**：正式轮要求全程开启；诊断轮当前 spark 关。R1/R2 A/B 要统一口径并记录。
2. **相机漂移 P14/P21**：BenchMod `holdPose` 还原滞后导致跨轮 A/A′；同轮内对照可用，跨轮只能同相机比较；P22 视觉验收优先同帧/固定相机。
3. **Focus**：`-PphotonRequireFocus=false` 时 BenchMod 不判无效，必须看夹具 `focus_audit.measure_focus_valid` 与 frames `flags` 失焦帧；失焦轮作废。
4. **截图成本**：夹具截图约 135 ms，R2 验收时把截图移出测量窗口或单独 session。
5. **库适配**：优先原库公开钩子；需 Mixin 时只做窄注入，不复制库实现；热重载/编辑器状态必须失效。
6. **92 尖峰**与 R1 相关但不同：R1 解决“每施法重建”，92 还有多女王并发与子实例链路；R3 才完全覆盖。

## 13. 恢复检查清单（上下文丢失后从这里开始）

1. `git -C <workspace> log --oneline -5` 确认 HEAD（应含 `3f41ff79`/`1100a7e8`/`ebc2b08`）；`git status --short` 确认用户 WIP 仍在。
2. 读第 1 节列出的文档；确认最新进度与未决项。
3. 构建：`JAVA_HOME=<jbr> ./gradlew.bat benchClasses -PphotonBench=true --offline --console=plain` 应 BUILD SUCCESSFUL。
4. 跑一次单施法基线（命令见第 10 节），期望：`CASTPREWARM ... prepared=2`、`CASTPROF ... end:cast-tick ≈ 60～70 ms`、`run_complete status=PASSED`。若不符，先查环境（Sodium/spark/Iris 配置、JBR、focus）再继续。
5. 进度：**R1 已做阴性 A/B**（§6.0，`poolBeam` 默认关闭）；**R2 已实施并 A/B（部分正面，§7.4）**——`-Dlobecorp.photon.prewarmRender` 默认开，首施放最长帧 680→129～168 ms、>250 ms 帧清零，但仍有 52～167 ms 残余尖峰。下一步：①R2 残余（未覆盖的实例化变体、首次模拟类加载、同相位像素对照、GLSL import memoize 复评）；②R3 子实例模板缓存/多槽，降低 `prepareNext` 18～38 ms/次；③R4 几何资产空间缓存。
