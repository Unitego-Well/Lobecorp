# 测试后优化建议与问题原因

编写时间：2026-10-24（北京时间）。本轮只跑诊断，没有修改业务代码、资源或构建配置。测试前的假设与建议见 [测试前优化建议与问题原因](photon-optimization-pretest-20261024.md)；当前问题状态仍以 [唯一问题表](../issues.md) 为准。

## 法线缓存后的真实激光复测（2026-10-24 11:21）

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

## 法线矩阵缓存实施与前后对照（2026-10-24）

此段记录用户后续授权的实现与对照，与下方 DS 只读诊断轮分开；当前问题仍统一维护在 [唯一问题清单](../issues.md)。

### 修订与实际改动

- 修正“相机原点每帧必变”“实例零共享”“采样比例可直接换算收益”和分配口径表述；同时澄清 prepareNext 失败可在蓄力阶段重试，不能说只调用一次。P13 未复现不能直接按环境干扰结案。
- PhotonModelOutput 为每个输出独占持有 NormalCache；键为世界矩阵 3×3 线性部分的逐位值与 JOML PROPERTY_ORTHONORMAL，缓存逆转置矩阵和行列式。平移不入键，旋转／缩放／镜像／奇异变化须失效，属性变化也须保持 JOML 分支语义。
- 最终版本将缓存结果复制到调用局部 Matrix3f，保留原顶点循环主体，避免循环消费共享可变矩阵的别名。该选择旨在减少方法拆分／逃逸对 JIT 的潜在干扰，没有证据断言它就是首版波动的根因。
- 没有改变每三角形 4 角的 QUADS 提交、排序、颜色、光照、shader、材质、动画、LOD 或资源内容；没有加入全局缓存、后台任务或持久 GPU 资源。

### 正确性与路径检查

- 三份 Java 文件 IDEA 0 错误，9 项既有提示（输出类 1 项、测试 Collector 空值注解 8 项）。新增警告已修正，没有借机清理存量提示。
- PhotonModelGeometryTest 5／5 通过，最终格式收尾后再次运行的 XML 时间 2026-10-24T03:08:09.890Z，failures=0／errors=0。新增 3 个用例覆盖平移、旋转、非均匀／负／零缩放、正交属性变化、实例隔离，以及 shading／insideOut／透明度组合下的位置、法线、颜色、UV 和光照完全对照。56 组新旧绘制输入对照通过；这不代替全部真实材质的视觉验收。
- 调试时 Render thread 在 NormalCache.update:96 命中 valid=true，原生结果相等、平移键不变、旋转／镜像／零缩放键改变；单步直接到 return normal:107，确认跳过重算分支。Sodium=true、MCP=true、GLFW_MAXIMIZED=1。
- MCP 固定 19877 得到 pong／connected=true，保存 2560×1494 调试截图。临时断点显式移除，随后正常保存退出世界并退出游戏；PID 54368（2026-10-24T02:30:01.285Z 启动）已退出，桥接 PID 54708 退出 0，Debug 会话为空。暂停轮仅作路径证据，不计性能。

### 本轮运行账本

| 运行 | 报告／归档 | 实际结果与边界 |
|---|---|---|
| 原实现首次基线 | client-1792807664435／before-invalid-focus | 因 focus_lost 为 INCONCLUSIVE，不参与收益比较 |
| 原实现重跑 | client-1792808072981／before-valid | PASSED、environment.valid=true；运行前两份生产源码 SHA256 与原始备份一致 |
| 缓存首版 | client-1792808615293／after-first | PASSED；静态改善但动画变慢、空基线波动，未采用该版作为最终实现 |
| 最终候选第一轮 | client-1792809307255／after-valid | PASSED、environment.valid=true，8 个 Photon 用例通过 |
| 最终候选复核轮 | client-1792809690358／after-repeat | PASSED、environment.valid=true，但空场景和轻负载出现约 6.15 ms 节奏，不能直接用于收益归因 |

上述无 Debug 运行均走 BenchMod Photon，包含最终报告校验；优化前重跑、最终第一轮与复核轮的构建耗时分别为 3 分 50 秒／3 分 45 秒／3 分 42 秒。报告参数及加载 Mod 清单相同，git 均记录 c168b04a＋dirty；具体代码差别另以原始文件／编译类及源文件指纹保留，不以 dirty 标志隐藏版本差别。

### 帧时间实测

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

### JFR 归因与可比性限制

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

### 证据与清理

备份根：C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-normal-cache-20261024。

- ds-effective-run、before-invalid-focus、before-valid、after-first、after-valid、after-repeat 保存各轮完整原始结果；首版源码／编译类另留 candidate-first-geometry，优化前编译类亦保留。
- debug-evidence.json、after-debug-frame.png、geometry-test.xml、geometry-test-final.xml、frame-comparison.json、normal-cache-analysis.json 保存路径、数值与分析证据；原始基线和最终版源文件指纹见 source-fingerprints.json；自有游戏 PID／JVM 启动时间见 process-ledger.json。
- 自有基准游戏由工具链正常结束；Debug 游戏通过 MCP 正常保存退出并退出程序。已按 PID 与启动时间核对本轮原始实例结束（66264／48536／56192／54368／70392／57196），桥接 55012／54708 退出 0。本轮原 PID 56192 的实例启动于 10:22:49；11:02 再读该 PID 已被 10:58:11 启动的新进程复用，该新进程不属于本轮，未关闭或操作。本轮临时断点已移除，不按 PID 数字或名称批量关闭进程。
- 未推送；没有新增翻译键或 Data 输出，没有修改 Gradle、动画、LOD 或资源文件。

## 一、本轮实际执行

| 项 | 实际执行 | 结果 |
|---|---|---|
| BenchMod Photon（08:43，无 Debug） | `BenchMod Photon Prepare` + `BenchMod Photon` | 场景全部 PASSED，但 `client.environment.invalidation=client.window.focus_lost`，整体 INVALID；数字只留作对照，不用于结论 |
| BenchMod Photon（08:52，无 Debug） | 同一入口重跑，运行期间不切换窗口、不输入 | **PASSED**，`environment.valid=true`，8 个 Photon 场景通过，`sweeper-render` 按过滤器 SKIPPED |
| JFR 归因 | 每个场景独立 JFR（约 23 秒）；`jfr view hot-methods`／`thread-allocation`／`allocation-by-site`／`allocation-by-class`；`jfr print` 导出执行样本后统计 inclusive 栈 | 见第三节 |
| IDEA Debug | `Photon Bench Debug`，三处断点核对调用路径；该轮计时不作对比 | 见第四节 |
| Minecraft MCP | 通过本工作区桥接（`MC_MCP_PORT=19877`）调用 `ping`、`get_minecraft_status`、`debug_fields`、`screenshot_to_file` | `pong`；截图 2560×1494、1.7 MB；见第五节 |
| Sodium | 客户端日志确认 `lib/sodium-neoforge-0.9.2+mc26.1.2.jar`，`Running graphics bootstrap plugin sodium`，RTX 3060 Laptop（驱动 32.0.16.1664） | 见第六节 |

DS 运行时证据生成于 `build/modBench/raw-results/default/client/`（summary、report、JSONL、JFR、截图）；该默认目录会被后续运行覆盖。DS 有效轮现已固定备份至既有可视化根 `photon-normal-cache-20261024/ds-effective-run`，不得把后续默认目录内容冒充该轮数据。本轮第一次 INVALID 运行证据另存于 `%TEMP%/dsh-uQhxMZ/lobecorp-invalid-run-20261024`（临时目录，不随仓库保存）；上一轮有效基线另存于 `%TEMP%/dsh-uQhxMZ/lobecorp-bench-backup-20261024`。

## 二、实测数据

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

## 三、JFR 归因

### 3.1 渲染线程执行样本（inclusive）

| 场景 | 渲染线程样本 | `write` | `bakeBatches` | `sample` | `skinMatrices` | `positions` | `faceNormal` | `Matrix4f.normal` |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 16 静态 | 448 | 71.0% | 75.9% | 0.4% | 0.2% | 0.2% | — | **19.9%** |
| 16 静态重复 | 422 | 70.1% | 78.0% | — | — | — | — | **24.6%** |
| 16 动画 | 669 | 22.0% | 87.3% | **61.7%** | 22.9% | 13.2% | 10.3% | 6.0% |
| 1 动画 | 195 | 10.8% | 51.3% | 36.9% | 12.8% | 8.2% | 3.1% | 4.1% |

自耗样本（`hot-methods`）：16 静态 `write` 229／529 = 43.3%，`Matrix4f.normal` 89 = 16.8%，`PhotonBufferCache.write` 3.2%；16 动画 `write` 14.2%，`HashMap.getNode` 13.1%，`skinMatrices` 8.5%，`ArrayList.add` 3.9%，`ImmutableCollections$List12.get` 3.1%，`HashMap.computeIfAbsent` 2.9%，stream 相关多项合计约 8%。

与测试前判断一致：静态模型的姿态缓存有效（`sample` 仅 0.4%，`sampleLocalFrame` 2%），但提交几何仍每帧全量重写（`write` 71%）；动画则相反，`sample` 占 61.7%，`write` 降到 22%。

### 3.2 新增的精确子热点：`world.normal()`

`write` 第 19 行每个 group、每帧调用一次 `world.normal(new Matrix3f())`（一般路径求逆转置，正交属性路径直接取 3×3）并新建 `Matrix3f`。在 16 静态场景它单独占 **19.9%** 渲染样本，重复场景 **24.6%**，这些比例的分母和 inclusive／self 口径不同，不能直接相除为“write 自身的三成”。**相机平移不改变法线**；相机原点也不必每帧变化，但当前 write 每组调用仍重算法线矩阵。它是可独立验证的窄范围优化候选；采样归因和调用次数需结合 JIT／内联与同条件 A/B 判断，尚不能承诺收益。

### 3.3 分配

`jfr view thread-allocation`（整段约 23 秒录制，含 setup／预热）：

| 场景 | 渲染线程分配 | 占全进程 |
|---|---:|---:|
| 16 静态 | 9.8 GB | 68.1% |
| 16 动画 | 27.7 GB | 85.1% |

`allocation-by-site` 的动画侧（剔除 JFR 首个分配事件的跨录制伪权重：`JsonReader.<init>` 占 33.49%，与上轮审查记录的现象相同）：`skinMatrices` 13.78%、`DirectMethodHandle.allocateInstance` 10.04%、`immutable` 5.77%、`HashMap.newNode` 4.66%、`Integer.valueOf` 3.49%、`Arrays.copyOf` 3.25%、stream 相关约 6%、`sample` 2.67%、`faceNormal` 1.39%。`allocation-by-class`：`float[]` 10.0%、lambda 类 9.51%、`PhotonModelDefinition$Vector3` 5.77%、`Object[]` 4.77%、`HashMap$Node` 4.66%、`Matrix4f` 3.90%、`Integer` 3.49%、`Vertex` 2.67%。

静态侧剔除同一伪权重后没有模型相关分配热点，说明静态问题是 CPU 计算与顶点写入，不是分配。绝对 GiB 与上轮口径不同（上轮 0.984／19.554 GiB），本次只作同轮内相对比较，不与上轮直接相减。

## 四、Debug 路径核对

Debug 运行仅核对路径，期间暂停过，计时不进入上表。

1. **静态提交路径**：断点 `PhotonModelGeometryUtil.write:24`（Render thread）命中；栈为 `write:24 ← PhotonModelOutput:480 ← bakeGroup:478 ← bakeModelGroup:450 ← PhotonWorldRenderStateMixin:29 ← GameRendererMixin:22`。变量 `indices.size()=1536`（512 三角形），`part.shading()=false`、`doubleSided()=true`、`insideOut()=false`，`world` 行列式 8.0（uniform scale 2）。确认每帧按 512×4 角写入，且夹具未开启 shading，**实际 shade() 成本未被夹具覆盖**。
2. **动画采样路径**：断点 `PhotonModelPoseSampler.sample:150`（Render thread）命中；栈为 `sample:150 ← PhotonModelOutput.sampleLocalFrame:403 ← sampleLocal:361 ← bakeBatches:437`。`Input[time=0.039960623, clip=turn, loop=true, anchor=START]`，时间每帧变化，确认动画每帧 cache miss 并整帧求值。
3. **首次准备路径**：断点 `PhotonModelOutput.prepare:179`（Render thread）命中；栈为 `prepare:179 ← reset:241 ← FXRuntimeMixin:111(emit) ← PhotonGeoEffect:44 ← PhotonModelRenderScenario:134`，`sampler=null` 进入 `loadDefinition()`。确认准备（读取、解析、拓扑、骨骼、材质）发生在 emit 当帧的渲染线程上。

## 五、Minecraft MCP 证据

同一调试会话内通过标准 MCP 协议（桥接 `dist/cli.js mcp --no-discover`，`MC_MCP_PORT=19877`）调用：`ping` → `pong`；`screenshot_to_file` 输出 `build/modBench/debug-mcp-screenshot.png`（2560×1494，1,735,580 字节），画面可见测试世界、模型网格与玩家；`debug_fields` 返回 `net.minecraft.client.Minecraft` 的字段概览（不是 F3 文本，工具语义与名称有偏差，已按实际记录）；`get_minecraft_status` 返回 `connected=false / port=null / processAlive=false`，与 `pong` 成功并存——该次状态回读与命令响应不一致，不能仅据此断言是启动器视角或游戏已断开；需结合端口、同次会话与实例归属判断，不能忽略状态矛盾。

## 六、环境与边界

- 日志确认 Sodium 加载、Ivy/Iris 版本段存在；本轮基准使用 Sodium 无光影客户端，不代表 Iris 大负载或光影场景。
- 第一次运行被判定 INVALID 的字段是 `client.environment.invalidation=client.window.focus_lost`、`verdict=invalidated`；该轮数据（空场景 3.769 ms 等）只作“失焦时不可比”的示例，不进入对比。
- 夹具固定 17×17 顶点、256 个四边面、单骨骼、part.shading=false、doubleSided=true；不能代表真实特效的多材质、shading、骨骼层级与透明交叠。
- P13 的空场景 1.421 秒长帧本轮**未复现**：有效运行 `70-empty-repeat` 最长 16.340 ms，无效运行 6.77 ms。因此不能宣布已修复，仍按“未归因异常”保留。

## 七、对测试前建议的修正

1. **P11 优先级调整**：先处理 `world.normal()`／法线矩阵的每帧重算。做法：把 `world` 拆成“相机平移”和“模型旋转／缩放”两部分；法线矩阵只由后者决定，可在模型朝向／缩放不变时跨帧复用，相机移动时只更新顶点位置。20%～25% 是当轮采样归因，不是可直接消除的墙钟比例或预期帧率收益。仅缓存法线矩阵及其行列式，按线性部分和 JOML 正交属性失效；保持 QUADS 四角、材质与排序语义，并以数值对照及实测决定收益。之后再做法线／shade 预缓存、角点预展开与矩阵手写乘。
2. **P11 的 shade 覆盖缺口**：夹具 `shading=false`，真实资源若启用 `shading`，`shade()` 每角 6 次乘加的成本会额外出现；下一轮夹具应增加 shading=true 变体，否则不能用当前 71% 直接推断真实资产。
3. **P12 维持原方向但顺序更明确**：`sample` 61.7%、`skinMatrices` 22.9%、`positions` 13.2%、`faceNormal` 10.3%。优先预编译权重／索引与轨道检索，再替换 `HashMap<Integer, Vector3>`、两份 `float[16]`、`Matrix4f` 与 stream 归一化；`morph` 整份复制（4.0%）也要覆盖“权重全 0 仍复制”的路径。
4. **P06 不能沿用 270 ms 基线**：同夹具两次有效运行分别为 270.0 ms 与 79.2 ms；Debug 已确认 prepare 在 emit 的渲染线程栈内。下一步必须先做冷进程、同进程热、真实激光三类分开测量，再决定内容级共享与分帧准备的范围。
5. **P13**：两次运行均未复现，维持未归因；下一轮用 GC／线程／IO 时间对齐而不是继续加模型样本。
6. **分配口径**：JFR 首个分配事件可能带跨录制权重（`JsonReader` 33%～77%），不能当作真实分配；本节尚未统一 thread-allocation 视图估计与 ThreadAllocationStatistics 首尾计数差的算法；GB／GiB、录制阶段、首事件权重和分母均须标明。同轮数字也只能在同口径、同窗口、同线程时比较；跨轮绝对量不能直接相减或用于推算收益。

## 八、更新后的优化顺序

1. P11-1：按不含平移的线性部分缓存法线矩阵；20%～25% 仅为历史采样归因，优化收益待独立前后对照。
2. P11-2：法线／shade 预缓存、角点预展开、矩阵手写乘；随后评估原生持久网格。
3. P12：预编译权重／索引与轨道检索 → 平铺数组与缓冲复用 → 消除每帧 Map。
4. P06：先补冷进程／热进程／真实激光三段测量，再做内容级共享与有界准备。
5. P13：独立空基线复现与事件对齐，不阻塞前四项。

## 九、未覆盖

- 没有做优化后的对照（本轮不改代码），因此没有任何“改善百分比”。
- 未测真实激光、Iris／光影、透明交叠、多材质和真实骨骼层级。
- 未测冷进程首次创建与进世界预热转移。
- S01–S23 中除 S01／S02／S03／S06／S18／S20／S22 的源码条件外，其余仍未专项验收。
