# Horde 测试后优化建议与问题原因

记录日期：2026-10-24。本文承接[测试前优化建议](horde-pretest-optimization.md)，记录本轮用 **IDEA Debug/Run + BenchMod（Client/Server）+ Sodium 客户端**实际跑出的证据，逐条核对测试前假设，并给出据此修正后的优化建议。本轮仍然**没有修改任何代码**。

执行环境：工作区 `.workspace/lobecorp_horde`，分支 `codex/horde`，HEAD `ef8b4ad4`（`git.dirty=true`）；BenchMod 0.1.5-beta、MC 26.1.2、NeoForge 26.1.2.100、Java 25.0.2（JBR）、Windows 11；窗口 2560×1494 最大化且聚焦、VSync 关闭、`clientFpsLimit=260`（= 不限制）、视距/模拟距离 12；Sodium 0.9.2、Iris 1.11.4 已加载但**未启用光影包**。运行时间 10:21:26–10:23:11（客户端）、10:25:06–10:25:33（专服）。

---

## 0. 本轮实测结论（先看）

| # | 实测结论 | 对应测试前假设 |
| --- | --- | --- |
| 1 | BenchMod Client **PASSED**（两场景）；64 实体间隔均值 **6.775 ms**，10000 实例 **6.238 ms**，GPU 均值 **2.399 ms**，`gpuCopy` 路径**未启用**（无光影走实例化快路径） | A2 部分成立：无光影路径确实没有回读成本，但帧间隔不再是 2.2 ms 量级 |
| 2 | BenchMod Server **启动失败**，`ReRetro Damage Indicators` 因 `NoClassDefFoundError: net/minecraft/client/renderer/MultiBufferSource` 加载失败；日志中 Photon/LDLib2/Iris/Sodium/IMBlocker 全部出现在专服 mod 列表 | **A5 成立** |
| 3 | 客户端退出时**稳定复现** `Trying to return to in-game GUI during disconnection`，栈与测试前分析完全一致：`ConductorControls.exit:518 → clearSession:401 → ClientRuntimeEvents.onClientLevelUnload:71`；同一次退出出现 2 次，并伴随 `Could not schedule ConsecutiveExecutor` | **A6 成立** |
| 4 | 首帧/稳定期仍有 **182.211 ms** 最大帧（horde），上一轮记录 162/224 ms；但同轮 64 实体场景最大帧 **318.974 ms**，说明最大帧不全由 Horde 造成 | A4 部分成立 |
| 5 | 本轮 10000 实例无光影帧间隔 **6.238 ms**，显著高于 10-23 两轮的 2.243/2.380 ms；但**同轮两个场景的 CPU/GPU 都比那两轮高约 18–25%**，环境底噪不可忽略，不能把 2.7× 全归因代码 | 新发现，需重复测量 |
| 6 | 渲染线程分配从 222 KB/帧升到 **443.7 KB/帧**；JFR 显示 `HordeLightField.requiredSections` 占 3.23% 采样、`HordeLightField$Section` 占 3.75% 分配压力 | 新发现（每 tick 全量重建光照区段集合） |
| 7 | `gpuCopy / batches / instances` 仍**只存在于 HUD 字符串**，日志与 artifact 都没有记录 → H24 的 292 MB 在现有证据链中仍不可复核 | 测试前 §3 的判断被本轮证实 |
| 8 | 本轮未取得 Iris 光影包下的大负载数据（未启用包），也没有真实事件/多目标夹具 → A1、A3 仍未验证 | 见 §4 |

---

## 1. 实测结果明细

### 1.1 BenchMod Client（run `client-1792808485966`，PASSED）

| 场景 | 阶段 | 帧数 | 均值 | 中位 | P95 | P99 | 最大 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `sweeper-render`（64 真实实体） | STABILIZE | 70 | 36.665 | 19.822 | 131.839 | 318.974 | 318.974 |
| | WARMUP | 628 | 7.242 | 6.516 | 11.743 | 17.731 | 80.273 |
| | **MEASURE** | **4576** | **6.775** | 6.290 | 9.968 | 12.670 | 28.434 |
| `horde-render`（10000 虚拟实例） | STABILIZE | 195 | 8.123 | 6.218 | 9.890 | 136.480 | 182.211 |
| | WARMUP | 560 | 6.243 | 6.151 | 7.192 | 8.038 | 58.819 |
| | **MEASURE** | **9618** | **6.238** | 6.177 | 7.596 | 8.561 | 38.639 |

实例数量指标全程 10000；两场景截图与 JFR 已登记，`spark` 采样链接为 `https://spark.lucko.me/g2IFp5byu6`（沙箱网络不可访问，仅登记）。

独立 30 s GameRenderer 捕获：

| 指标 | horde-render | sweeper-render |
| --- | ---: | ---: |
| `frame_interval_ms` 均值/中位/P95/P99 | 6.167 / 6.163 / 7.232 / 7.811 | 6.689 / 6.241 / 10.060 / 13.117 |
| `render_cpu_ms` 均值/P95 | 1.905 / 2.809 | 4.191 / 6.641 |
| `render_gpu_ms` 均值/P95/P99/最大 | 2.399 / 4.009 / 4.864 / 7.386 | 1.576 / 4.516 / 6.858 / 19.564 |
| 渲染线程分配（均值/P95/最大） | 443,675 / 1,070,536 / 10,283,336 B | 1,620,698 / 1,855,960 / 2,097,496 B |
| GPU 查询 started/completed/skipped_pending/errors | 4792 / 4792 / 73 / 0 | 2990 / 2990 / 0 / 0 |
| 实测 `mean_fps` | 162.14 | 149.49 |

### 1.2 与 10-23 两轮对照（同机、同分辨率、同场景、无光影）

| 指标（horde-render） | after-client-2 | after-client-3 | 本轮 10-24 |
| --- | ---: | ---: | ---: |
| 帧间隔均值 ms | 2.380 | 2.243 | **6.167** |
| `render_cpu` 均值 ms | 1.115 | 1.206 | **1.905** |
| `render_gpu` 均值 ms | 2.080 | 1.929 | **2.399** |
| 渲染线程分配 均值 B/帧 | 234,022 | 222,013 | **443,675** |
| GPU 完成/跳过帧 | 4295/8309 | 4846/8527 | 4792/73 |

关键判读：

1. **不能直接宣布“代码回归 2.7×”**。同轮 `sweeper-render` 的 CPU 也从 3.34/3.06 ms 升到 4.19 ms、GPU 从 1.34/1.26 升到 1.58，说明本轮机器底噪比 10-23 高约 18–25%（期间 IDE 与其他进程活跃）。
2. 但 horde 的升幅（+178%）远大于底噪（sweeper 间隔 +40%），且 **CPU 只升了 0.7 ms、GPU 只升了 0.47 ms**，无法解释多出的约 4 ms。因此除底噪外还存在**未计入 `render_cpu` 的稳态成本**（tick/提取/提交/呈现），需要按测试前 §2 H24-A 的分阶段计时与 `batches/draws` 计数才能定论。**本轮不将其写成已确认回归。**
3. 分配量翻倍是唯一“干净”的增量信号（分配不受外部负载影响这么大），指向每帧/每 tick 的新增集合与对象。

### 1.3 BenchMod Server（run 失败 27 s）

- 结果：`runBenchServer FAILED`（exit 1），无 summary；`collectBenchServerArtifacts` 已收集 6 个文件。
- 根因证据：专服日志 mod 列表包含 `Photon 26.1.2.3`、`LowDragLib2 26.1.2.42`、`ReRetro Damage Indicators 1.0.2`、`Iris 1.11.4`、`Sodium 0.9.2`、`IMBlocker 7.3.2`、`KilaGraph`、`ParticleStorm`；随后
  `ReRetro Damage Indicators (retrodamageindicators) has failed to load correctly: java.lang.NoClassDefFoundError: net/minecraft/client/renderer/MultiBufferSource`，
  并 `FatalStartupException: Couldn't find Minecraft server thread`。
- 与测试前判断一致：不是 Horde 服务端问题，而是 `implementation` 依赖 + bench 共用源集把客户端 mod 带进专服。

### 1.4 退出异常（H23）

```
[10:23:11.128] [Render thread/ERROR] Exception caught during firing event:
  Trying to return to in-game GUI during disconnection
  at net.minecraft.client.Minecraft.setScreen(Minecraft.java:1152)
  at net.minecraft.client.MouseHandler.grabMouse(MouseHandler.java:459)
  at org.unitego.lobecorp.client.conductor.ConductorControls.exit(ConductorControls.java:518)
  at org.unitego.lobecorp.client.conductor.ConductorControls.clearSession(ConductorControls.java:401)
  at org.unitego.lobecorp.events.client.ClientRuntimeEvents.onClientLevelUnload(ClientRuntimeEvents.java:71)
```
同一次退出出现 2 次；随后 `[10:23:11.251] [Server thread/ERROR] Could not schedule ConsecutiveExecutor`。**与源码推断逐行吻合**：`clientLevelTeardownInProgress=true` 期间的 `setScreen(null)` 是必抛路径，与 Horde 无关，但会影响 Cleanup 顺序（Horde 的 `clear()` 已提前到第 68 行，故 Horde GPU 资源未被跳过）。

### 1.5 JFR（`lobecorp.horde-render.jfr`，65 s）热点

- 采样：`jdk.ExecutionSample` 773、`jdk.ObjectAllocationSample` 5799、`jdk.GCPhaseParallel` 3391（GC 活跃）。
- CPU 热点：`HashMap.getNode` 8.15%、`LevelChunkSection.isRandomlyTickingFluids` 3.23%、**`HordeLightField.requiredSections` 3.23%**、`HashMap.putVal` 2.33%、`Comparator` 排序 1.94%、`HordeClient$$Lambda.applyAsDouble`（批次距离排序）1.55%、**`HordeGpuRenderer.writeParameters` 1.16%**、`HordeGpuRenderer.drawGroup` 0.91%、`HordeLightField.prepare` 0.78%。
- 分配压力：`byte[]` 57.39%、`ForkJoinTask$RunnableExecuteAction` 6.42%、`IdentityHashMap$KeyIterator` 5.00%、`HashMap$Node` 4.19%、**`HordeLightField$Section` 3.75%**、`LevelEvent$PotentialSpawns` 3.26%、`Long` 2.60%、`NativeMemorySegmentImpl` 2.39%、`GlRenderPass$TextureViewAndSampler` 1.44%、**`HordeClient$Batch` 1.21%**。

**由此确认的新根因**（测试前未列出）：

`HordeLightField.prepare` 每帧执行，且每 tick（`preparedTick != level.getGameTime()`）都会：
1. `batches.stream().map(Batch::block).distinct().toList()`（每帧）；
2. `requiredSections(blocks)` 对每个绘制块按 XZ 区块 × Y 区段**逐个 new `Section`** 放进 `HashSet`（每 tick，约 93 块 × 每块 10–30 区段）；
3. `required.stream().filter(!slots.containsKey).count()`、`slots` 全表遍历、`orderedSections` 全量循环（每帧）。

在 160 FPS 下等于每秒重建 20 次上千个 `Section` 对象，并每帧做 93 元素流/去重/集合运算——这正好解释了 **`Section` 3.75% 分配 + `requiredSections` 3.23% CPU + 分配量翻倍**。它是“每帧每批次的固定开销随批次数量线性增长”的具体实例，也解释了为什么 1.8 间距（同样 10000 单位占用更长线路 → 可见块更多）会让帧间隔涨幅远大于 GPU 涨幅。

---

## 2. 测试前假设的核对结果

| 假设 | 结果 | 证据 |
| --- | --- | --- |
| A1：292 MB 回读是 67.89 ms 主因 | **未验证** | 本轮未启用光影包，Iris 转接路径未执行；`gpuCopy` 也未落盘 |
| A2：无光影路径不受回读影响 | **部分成立** | 无 `copyTextureToBuffer` 调用，GPU 2.4 ms；但帧间隔 6.17 ms 高于历史 2.24 ms，原因见 §1.2/§1.5，未定论 |
| A3：1 刻过渡在低帧率下等于硬切 | **未验证** | 需要真实事件 + 目标 + 动态画面；本轮无事件夹具 |
| A4：首次可见帧仍有数百 ms 阻塞 | **部分成立** | horde STABILIZE 最大帧 182.211 ms；但同轮 sweeper 最大帧 318.974 ms，最大帧不专属 Horde；H11 的分阶段计时仍未实现 |
| A5：专服失败是客户端 mod 进 classpath | **成立** | 见 §1.3 |
| A6：断连仍抛鼠标异常 | **成立（稳定复现）** | 见 §1.4 |
| A7：分流/高度隔离回归未坏 | **未完成** | 本轮尝试启动 `HordeRulesTest`/`HordeRegionTest` 时 before-launch build 未成功（IDE 侧），测试未执行；不能用“未运行”当作通过 |

---

## 3. 据此修正的优化建议（相对测试前文档的增量）

### 3.1 优先级变化

1. **H24 的“先计量”从建议升级为阻塞项**：`statistics()` 只有 HUD 字符串，导致 292 MB、186 次绘制、批次数量都无法从 artifact 复核。必须先把它写进 BenchMod metric 与 JSONL（`gpuCopy/passes/draws/instances/regions`），否则后续任何 H24 改动都无法验收。
2. **新增 P1：`HordeLightField` 的每帧/每 tick 全量重建**（`HordeLightField.java:42-98,100-122`）。建议：
   - 按 `Block` 缓存 `Set<Section>`（Block 的 bounds 不变，只有区段/方块变化时失效），`prepare` 只做并集与槽位维护；
   - `Section` 用 `long` 打包（x/y/z 各 21/8/21 位）替代 record 对象，消除每 tick 上千个对象；
   - `batches.stream().map(...).distinct()` 改为复用列表，`required.stream().filter(...).count()` 与 `slots` 遍历只在 `required` 变化时执行。
   预期直接削减本轮观测到的 3.23% CPU + 3.75% 分配压力中的大部分，且对 10000 实例/多批次场景线性受益。
3. **批次/绘制数量应成为一等指标**：本轮 JFR 里 `writeParameters`、`drawGroup`、批次距离排序都已进入热点，说明非 Iris 路径在 1.8 间距下同样受“空间块数量”支配。建议把 `HordeClient.extract` 产出的批次数、`drawGroup` 的 draw 次数、每批实例数纳入统计，并把“同实例数、不同间距”作为一组对照实验（当前 bench 默认 1.8，历史数据 1.2，二者不可直接比）。
4. **H23 的修复方式在实测中被再次确认**：`exit()` 的 `grabMouse` 必须在断连路径移除；仅“把 HordeClient.clear 提前”不够，因为异常仍会中断 `onClientLevelUnload` 后续清理，并触发 `ConsecutiveExecutor` 调度失败。建议按测试前 §2 H23 的 `resetState()`/延迟恢复方案实施。
5. **H25 解阻方案在实测中被确认**：专服 classpath 里确实有全部客户端 mod。建议按“新增 `benchServer` 源集 + 复制 RunModel”的最小改动执行；不要删公共依赖。验收时以 `summary.environment.loadedMods` 不含客户端 mod 为准。
6. **A4 需拆分归因**：sweeper 场景最大帧 318.974 ms 大于 horde 的 182.211 ms，说明“首次模型准备卡顿”不是唯一首帧来源（还有区块/着色器/资源首用）。H11 计时应同时采集“游戏首个稳定帧”和“事件首个可见帧”，避免把全局首帧算到 Horde。

### 3.2 需要在下一轮补测的实验（按性价比）

| 实验 | 目的 | 前提 |
| --- | --- | --- |
| 同一 BenchMod 场景连跑 3 轮，记录两场景 CPU/GPU 作为底噪基准 | 把本轮 2.7× 拆成底噪 vs 代码 | 无（只需时间） |
| 打开 Iris 光影包跑 10000 实例，并临时把 `gpuCopy/passes/draws` 打到日志 | 验证 A1：copy 段占比、回读次数 | 需手动启用包 + 增加日志（不改算法） |
| 真实事件夹具（1 目标/2 目标/2 事件）录 30/60/144 FPS | 验证 A3 与 H15 平滑度 | 需要事件夹具（当前 bench 无） |
| 专服隔离 classpath 后跑 `sweeper-ai` | 取得 server tick P50/P95/P99 与实体数 | 需 H25 修复 |
| 渲染线程分配按“场景/批次数”分组记录 | 确认 `Section`/`Batch` 分配与批次数线性 | 需统计扩展 |

---

## 4. 本轮仍未验证 / 不能写成结论的部分

1. **Iris 大负载性能**：本轮未启用光影包，67.89 ms 与 292 MB 未复测；H24 仍是“已实测失败项”，不能因为本轮 PASSED 而关闭。
2. **平滑与高度隔离的观感**：无真实事件与动态画面，H15 的“像水一样平滑”“楼上不缺口”仍需实机验收。
3. **单元测试/回归**：`HordeRulesTest`、`HordeRegionTest` 本轮未能启动（IDE before-launch build 未成功），状态保持“未执行”。
4. **服务端性能**：专服未进入测量，无任何 server tick 数据。
5. **证据保存**：受 DSH 沙箱限制，无法把 `build/modBench/raw-results` 复制到独立备份目录；当前原始证据仍在工作区 `build/modBench/`（下次同名运行会被插件清理）。如需长期保留，应在下次运行前用 IDE/资源管理器手工复制。

---

## 5. 证据索引

| 内容 | 路径 |
| --- | --- |
| 客户端 summary / report | `build/modBench/raw-results/default/client/summary.json`、`report.md` |
| 逐帧样本 | `build/modBench/raw-results/default/client/artifacts/samples/*.jsonl` |
| GPU/CPU/分配捕获 | `build/modBench/raw-results/default/client/artifacts/gpu/*-gpu.json` |
| JFR | `build/modBench/raw-results/default/client/artifacts/jfr/*.jfr` |
| 截图 | `build/modBench/raw-results/default/client/artifacts/screenshots/*.png` |
| spark 链接 | `artifacts/spark/horde-profile.txt` → `https://spark.lucko.me/g2IFp5byu6` |
| 专服失败日志 | `build/modBench/runs/default/server/logs/`、`build/modBench/bundles/default/server/` |
| 客户端运行日志（含 H23 栈） | `build/modBench/runs/default/client/logs/latest.log:403-415` |
| 本轮分析文档 | [测试前优化建议](horde-pretest-optimization.md)、本文 |

---

## 6. 与测试前文档的差异说明

- 测试前文档把 `HordeLightField` 仅列为“CPU 准备仍保留”，本轮 JFR 证明它**每帧参与**且占用可观，已升级为独立优化项。
- 测试前对“无光影路径不受影响”的预期（A2）不完整：路径确实不触发回读，但帧间隔仍受批次数与每帧集合重建支配。
- 测试前认为 `render_cpu/GPU` 足以解释帧间隔的假设被本轮数据否定：horde 的 CPU+GPU 合计 4.3 ms，间隔 6.17 ms，存在未归因的稳态成本，必须补测。

---

## 7. 测量时刻与工作区变动说明

本轮 BenchMod Client 使用的编译产物对应 **10:21 时刻的工作区**（HEAD `ef8b4ad4` + 当时未提交修改）。测量结束后，工作区源码仍在被继续编辑：`HordeCleanupUtil.java`（10:25 新增）、`HordeGameplayEvents.java`（10:26）、`LcParticleTypes.java`（10:26）、`EntityCorpse.java`（10:27）、`HordeRuntime.java`（10:27）、`HordeClient.java`（10:28）、`HordeVisualConfig.java`（10:28）等文件的修改时间均晚于测量窗口。

因此：

1. 本文数据只代表 **10:21 时刻的代码**，不能代表其后继续修改的版本；
2. 本文引用的行号在该批文件被改动后可能偏移；
3. 若要以本文数据做"改前/改后"对照，必须在同一静止工作区重跑；当前工作区仍在变动，不满足单变量条件。

