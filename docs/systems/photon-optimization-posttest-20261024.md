# 测试后优化建议与问题原因

编写时间：2026-10-24（北京时间）。本轮只跑诊断，没有修改业务代码、资源或构建配置。测试前的假设与建议见 [测试前优化建议与问题原因](photon-optimization-pretest-20261024.md)；当前问题状态仍以 [唯一问题表](../issues.md) 为准。

## 一、本轮实际执行

| 项 | 实际执行 | 结果 |
|---|---|---|
| BenchMod Photon（08:43，无 Debug） | `BenchMod Photon Prepare` + `BenchMod Photon` | 场景全部 PASSED，但 `client.environment.invalidation=client.window.focus_lost`，整体 INVALID；数字只留作对照，不用于结论 |
| BenchMod Photon（08:52，无 Debug） | 同一入口重跑，运行期间不切换窗口、不输入 | **PASSED**，`environment.valid=true`，8 个 Photon 场景通过，`sweeper-render` 按过滤器 SKIPPED |
| JFR 归因 | 每个场景独立 JFR（约 23 秒）；`jfr view hot-methods`／`thread-allocation`／`allocation-by-site`／`allocation-by-class`；`jfr print` 导出执行样本后统计 inclusive 栈 | 见第三节 |
| IDEA Debug | `Photon Bench Debug`，三处断点核对调用路径；该轮计时不作对比 | 见第四节 |
| Minecraft MCP | 通过本工作区桥接（`MC_MCP_PORT=19877`）调用 `ping`、`get_minecraft_status`、`debug_fields`、`screenshot_to_file` | `pong`；截图 2560×1494、1.7 MB；见第五节 |
| Sodium | 客户端日志确认 `lib/sodium-neoforge-0.9.2+mc26.1.2.jar`，`Running graphics bootstrap plugin sodium`，RTX 3060 Laptop（驱动 32.0.16.1664） | 见第六节 |

证据位置：`build/modBench/raw-results/default/client/`（summary、report、JSONL、JFR、截图）。本轮第一次 INVALID 运行证据另存于 `%TEMP%/dsh-uQhxMZ/lobecorp-invalid-run-20261024`（临时目录，不随仓库保存）；上一轮有效基线另存于 `%TEMP%/dsh-uQhxMZ/lobecorp-bench-backup-20261024`。

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

`write` 第 19 行每个 group、每帧调用一次 `world.normal(new Matrix3f())`（确定逆矩阵再转置）并新建 `Matrix3f`。在 16 静态场景它单独占 **19.9%** 渲染样本，重复场景 **24.6%**，约为 `write` 自身样本的三成。原因是：即使模型静止，`world` 也包含 `translation(-cameraOrigin)`，每帧变化；但**相机平移不改变法线**，当前实现仍每帧重算法线矩阵。这是本轮最明确、改动面最小的优化入口。

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

同一调试会话内通过标准 MCP 协议（桥接 `dist/cli.js mcp --no-discover`，`MC_MCP_PORT=19877`）调用：`ping` → `pong`；`screenshot_to_file` 输出 `build/modBench/debug-mcp-screenshot.png`（2560×1494，1,735,580 字节），画面可见测试世界、模型网格与玩家；`debug_fields` 返回 `net.minecraft.client.Minecraft` 的字段概览（不是 F3 文本，工具语义与名称有偏差，已按实际记录）；`get_minecraft_status` 返回 `connected=false / port=null / processAlive=false`，与 `pong` 成功并存——该字段是启动器视角，不代表协议不可用。

## 六、环境与边界

- 日志确认 Sodium 加载、Ivy/Iris 版本段存在；本轮基准使用 Sodium 无光影客户端，不代表 Iris 大负载或光影场景。
- 第一次运行被判定 INVALID 的字段是 `client.environment.invalidation=client.window.focus_lost`、`verdict=invalidated`；该轮数据（空场景 3.769 ms 等）只作“失焦时不可比”的示例，不进入对比。
- 夹具固定 17×17 顶点、256 个四边面、单骨骼、part.shading=false、doubleSided=true；不能代表真实特效的多材质、shading、骨骼层级与透明交叠。
- P13 的空场景 1.421 秒长帧本轮**未复现**：有效运行 `70-empty-repeat` 最长 16.340 ms，无效运行 6.77 ms。因此不能宣布已修复，仍按“未归因异常”保留。

## 七、对测试前建议的修正

1. **P11 优先级调整**：先处理 `world.normal()`／法线矩阵的每帧重算。做法：把 `world` 拆成“相机平移”和“模型旋转／缩放”两部分；法线矩阵只由后者决定，可在模型朝向／缩放不变时跨帧复用，相机移动时只更新顶点位置。预计直接削掉当前 20%～25% 的渲染样本，且不改变 QUADS 四角、材质与排序语义，是风险最低的第一步。之后再做法线／shade 预缓存、角点预展开与矩阵手写乘。
2. **P11 的 shade 覆盖缺口**：夹具 `shading=false`，真实资源若启用 `shading`，`shade()` 每角 6 次乘加的成本会额外出现；下一轮夹具应增加 shading=true 变体，否则不能用当前 71% 直接推断真实资产。
3. **P12 维持原方向但顺序更明确**：`sample` 61.7%、`skinMatrices` 22.9%、`positions` 13.2%、`faceNormal` 10.3%。优先预编译权重／索引与轨道检索，再替换 `HashMap<Integer, Vector3>`、两份 `float[16]`、`Matrix4f` 与 stream 归一化；`morph` 整份复制（4.0%）也要覆盖“权重全 0 仍复制”的路径。
4. **P06 不能沿用 270 ms 基线**：同夹具两次有效运行分别为 270.0 ms 与 79.2 ms；Debug 已确认 prepare 在 emit 的渲染线程栈内。下一步必须先做冷进程、同进程热、真实激光三类分开测量，再决定内容级共享与分帧准备的范围。
5. **P13**：两次运行均未复现，维持未归因；下一轮用 GC／线程／IO 时间对齐而不是继续加模型样本。
6. **分配口径**：JFR 首个分配事件可能带跨录制权重（`JsonReader` 33%～77%），不能当作真实分配；同轮内相对比较有效，跨轮 GiB 不直接相减。

## 八、更新后的优化顺序

1. P11-1：拆分相机平移与模型变换，缓存法线矩阵（本轮已量化 20%～25%）。
2. P11-2：法线／shade 预缓存、角点预展开、矩阵手写乘；随后评估原生持久网格。
3. P12：预编译权重／索引与轨道检索 → 平铺数组与缓冲复用 → 消除每帧 Map。
4. P06：先补冷进程／热进程／真实激光三段测量，再做内容级共享与有界准备。
5. P13：独立空基线复现与事件对齐，不阻塞前四项。

## 九、未覆盖

- 没有做优化后的对照（本轮不改代码），因此没有任何“改善百分比”。
- 未测真实激光、Iris／光影、透明交叠、多材质和真实骨骼层级。
- 未测冷进程首次创建与进世界预热转移。
- S01–S23 中除 S01／S02／S03／S06／S18／S20／S22 的源码条件外，其余仍未专项验收。
