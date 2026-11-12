# Horde 测试前优化建议与问题原因

记录日期：2026-10-24。范围：`.workspace/lobecorp_horde` 工作区（分支 `codex/horde`）当前源码与未提交实现。本文只做只读审查，**未修改任何代码**；随后用 Debug／Minecraft MCP／BenchMod／Sodium 的实测结论另记于[测试后优化建议](horde-posttest-optimization.md)。问题状态仍以[唯一问题清单](horde-issues.md)为准，本文只回答两件事：**究竟是什么导致这些问题**、**按什么顺序改**。

证据级别：**【实测】**= 有运行报告；**【源码证实】**= 直接读代码可确定；**【源码推断】**= 由调用链/算术推出、尚无计时；**【缺测量】**= 必须新增实验才能定论。

---

## 0. 结论摘要

| 编号 | 一句话根因 | 级别 | 优先级 |
| --- | --- | --- | --- |
| H24 | Iris 顶点"展开"走 `copyTextureToBuffer`，而 GL 后端该 API 实现为 **`glReadPixels` 主机回读**；且按 93 个空间块**逐块串行** bake→回读→绘制。每帧 292 MB 经 PCIe 往返 + 93 次同步，67.89 ms 的"GPU 时间"主要是在等回读，不是在算 shading | 源码证实＋算术闭合 | P0 |
| H15 | 过渡时长写死 **1 逻辑刻（50 ms）**，与帧率耦合；且区域数据模型是"整表替换上一次目标值"，隐含 `过渡 ≤ 1 刻` 的不变式，无法满足"像水一样平滑" | 源码证实 | P0 |
| H15 | 势场是 **flow² 加权平均**：两侧目标反向位移会互相抵消，邻近目标会稀释单体缺口 → 缺口"时大时小、时有时无" | 源码证实 | P0 |
| H15 | 粗高度场横向 **15.2 格**、沿线路 **16 格** 分辨率，1 格薄墙/台阶/门洞必然丢失；地形项增益最大 6.7 格且高度更新**无过渡** | 源码证实＋算术 | P1 |
| H07 | 每 tick 每玩家做**两次**全高（`minY..maxY`）实体查询（`scan` + `clearDebris`），玩家范围重叠时完全重复；短路条件不完整；`clearDebris` 逐候选 O(目标×成员) | 源码证实 | P1 |
| H11 | 首次可见帧在渲染线程同步完成"读 JSON→共享 GeoBone 烘焙 20 帧→CPU 展开→建 GPU 资源"；无状态机/预算/代际令牌，失败即事件永久 `failed` | 源码证实 | P2 |
| H14 | 主链代码基本存在但**零自动化测试**；区块卸载即释放归属且重载 join 被取消（单位消失）；旧超额只淡出已加载成员；`reservations` 等不持久化 | 源码证实＋缺测试 | P2 |
| H03 | "首次建档规则"实为 `events()` map 顺序中第一个覆盖事件；`rules` 为 `final`，后续事件不能提高配额；且 fade/超时用 provider 事件规则、配额用 assignment 规则，**规则混用** | 源码证实 | P2 |
| H25 | 客户端专用 mod 用 `implementation` 声明；BenchMod 的 `benchServer` 与 `benchClient` 共用同一 `bench` 源集；26.1.2 的 ModDevGradle 不支持 per-run 额外 classpath → 专服必然加载 ReRetro/Photon/LDLib | 源码证实 | P2 |
| H23 | 断连时 `LevelEvent.Unload` 在 `clientLevelTeardownInProgress=true` 之后触发；`ConductorControls.exit` 无条件 `grabMouse()` → `setScreen(null)` → MC 保护性抛异常 | 源码证实（栈＋原版源码） | P2 |
| H10 | motion pass 每实例 **6 次** `movingLocation`（3 分量各 1 次 + 朝向 2 次 + 光照 1 次），其中 3 次输入完全相同、1 次结果仅用于取光 | 源码证实 | P3 |
| H19 | GPU 资源单一属主、无 double-free；真实风险是异常路径无 try/finally、grow-only 不回收、瞬态布局错误导致永久停显 | 源码证实 | P3 |

---

## 1. 三个系统性根因（先看这一节）

### R-A：Iris 转接的成本在"主机往返 + 串行"，不在 GPU 算力

**机制**（[HordeGpuRenderer.java](../../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) `IrisVertices.draw`，:529-571）：

1. 每个空间块批次开一个 render pass，用**片元着色器逐 32 位字**把 5.2 M 个顶点展开成 Iris 的 56 B 布局，写进一张 RGBA8 渲染目标（`horde_iris_vertices.fsh`）。
2. 立刻 `encoder.copyTextureToBuffer(texture, vertices, 0, …)` 把这块纹理"复制"进顶点缓冲（:547-548）。
3. 立刻用该缓冲画 `ENTITY_CUTOUT`/`EYES`（:554-570）。
4. 换下一块时**重新烘焙到同一张纹理、复制到同一缓冲 offset 0**，形成 93 次"bake → 回读 → 绘制"的**串行链**。

关键事实：在 GL 后端，`copyTextureToBuffer` **不是 GPU 内复制**。26.1.2 的 `GlCommandEncoder` 把它实现为：

```java
bindFrameBufferTextures(readFbo, source.glId(), …);
_glBindBuffer(GL_PIXEL_PACK_BUFFER, destination.handle);   // 35051
_pixelStore(GL_PACK_ROW_LENGTH, width);                    // 3330
_readPixels(x, y, width, height, format, type, offset);    // 带 PBO 偏移
queueFencedTask(callback);
_getError();                                               // 每次复制一次同步点
```
（`build/moddev/artifacts/minecraft-patched-26.1.2.100-merged.jar!/com/mojang/blaze3d/opengl/GlCommandEncoder.java:259-278`）

即：**渲染目标 →（`glReadPixels` DMA）→ 主机可见的内存 → 再次作为顶点数据上传**。这与 `horde-issues.md:35` 写的"无 CPU 映射""只在 GPU 内从附件复制到顶点缓冲"相反，是当前 H24 判断的**事实性错误**，也是 67.89 ms 的直接来源。

**算术闭合**（两路独立复核一致）：

| 项 | 计算 | 结果 |
| --- | --- | --- |
| 每实例顶点 | `sweeper_a` 130 个非零 quad × 4 | 520 |
| 每实例展开量 | 520 × 56 B（Iris 14 字） | 29,120 B |
| 有效载荷 | 10,000 × 29,120 B | **291,200,000 B** |
| 行对齐填充 | 17,834 行 × 16,384 B − 291,200,000 | **992,256 B**（0.34%） |
| 合计 | 与实测 `gpuCopy` | **292,192,256 B** ✓ |
| 绘制次数 | 93 个可见空间块 ×（主体+发光） | **186** ✓ |
| 展开片元数 | 292,192,256 / 4 | **73,048,064** |

292 MB ÷ 93 ≈ **3.0 MiB/块**，每块都要一次主机往返。PCIe 4.0 x16 实测约 20–25 GB/s：单向往返合计约 584 MB ⇒ **约 25–30 ms 带宽下限**；再叠加 93 次串行同步（每次约 0.3–0.5 ms 延迟）⇒ **约 50–70 ms**，与实测 67.89 ms 吻合。旁证：同机无光影的实例化快路径在同样 10,000 实例下 GPU 仅 **2.08/1.93 ms**（BenchMod）。

**为什么"GPU 计时 67.89 ms"会误导**：`GL_TIME_ELAPSED` 计的是查询区间内 GPU 命令流的**墙钟跨度，含 GPU 空闲等待**。逐块回读把 GPU 卡在"等主机取走数据"，因此表现为"GPU 很忙"，实际是 GPU 在空转。文档"不能把 67.89 ms 全部归因复制"结论方向对，但理由应是"复制是主机往返且被串行化"，不是"复制只值 2–3 ms"。

**由此确定的优化顺序**（详见 §2 H24）：

1. **先测**：5 段顺序 GPU 查询 + `gpuCopy/批次/绘制` 计数落盘 + `copyTextureToBuffer` 次数（当前只有字符串统计）。
2. **消除串行**：一次 bake 整块、一次回读、一个材质 pass 内用 `baseVertex` 画完（93 次同步 → 1–5 次）。
3. **减少字节**：发光层用静态 glow 子集索引（本模型 130 quad 中仅 11 个 quad 命中发光像素，EYES 层可降到约 8.5%）；静态 UV/midUV 预计算（展开取样 −27%）。
4. **唯一能突破 292 MB 地板的手段是减少顶点数**（LOD/距离剔除/减层）——属产品决策，不能静默做。

### R-B：平滑度被"1 刻"和"整表替换"两个隐含假设锁死

`horde_motion.glsl:87`：`transition = smoothstep(0.0, 1.0, time - LightRoute.z)`，`time` 单位是**逻辑刻** ⇒ 过渡固定 50 ms。实测帧间隔 67.98 ms 时，**一帧就跨过整段过渡**，等于硬切；帧率越低越像瞬移，同一场景不同机器观感不同。

同时 `HordeClient.java:486-493` 用**整表替换**保存 `previousRegions ← regions`，`HordeGpuRenderer.updateRegions` 只平铺"上一份目标值 + 当前值"。这隐含不变式：**过渡必须在下一次变化前完成**（即 ≤1 刻）。一旦把过渡调长（"像水一样平滑"的必要条件），任何一个目标的变化都会把另一个正在过渡的目标**弹回旧端点**（反向抽动）。另外零尺寸端点落在盒的 **min 角**（`HordeGpuRenderer.java:626-633`），过渡期间障碍中心会滑动半个盒宽。

⇒ 要满足"水一样平滑"，必须**同时**改三件事：逐区域 `startedAt`（不再用事件级 `regionsChanged`）、端点塌缩在盒心、过渡时长改成秒级并写入 UBO。只改其中一件会立刻暴露另两件。

### R-C：全量遍历 + 粗精度，是玩法侧成本与观感的共同来源

- 客户端每 tick 把**整个模拟距离内**的 `Mob` 都变成分割区域（`HordeClient.java:110,119-125`），与线路无关：农场里的牛也会进 GPU 的 O(N) 区域循环，并稀释单体缺口。
- 服务端每 tick 每玩家做两次全高实体查询（`HordeRuntime.java:254-319`），去重发生在取回之后。
- 高度场横向 15.2 格、沿线路 16 格（`HordeRoute.java:27-30,206-207`），本质只能表达"缓坡"；地形项增益却可达 6.7 格，且高度上传无过渡（`HordeGpuRenderer.java:607-612`）。

⇒ 三条线（区域过滤、查询合并、SDF 障碍）互相独立，但都能立刻降低 N 与突变，是性价比最高的"先做项"。

---

## 2. 分问题：原因、证据与建议

### H24 Iris 大负载 GPU 转接过慢（P0）

**现状**：10000 实例 442 帧/30 s，帧间隔均值 67.98 ms、P95 85.74 ms，`render_gpu` 均值 67.89 ms（442 帧中仅 147 帧拿到查询结果，`gpu_skipped_pending=294`），`render_cpu` 均值 38.91 ms；`gpuCopy=292,192,256 B/帧`、主体/发光绘制 186 次。

**根因**（按贡献排序）：

1. **主机回读 + 93 次串行**（R-A）：`GlCommandEncoder.copyTextureToBuffer` = `glReadPixels` 到 PBO，每块一次，且每块之间共用同一纹理/缓冲 → 强制串行。`_getError()` 每块一次也是同步点。
2. **展开本身很贵**：73,048,064 个片元、约 2.7 亿次 `texelFetch`（逐字计数：word0/1/2 各 4/3/4 次、word8 4 次、word11/12 各 8 次、word13 约 17 次…），其中 **34.6% 的取样用于逐帧不变的静态字**（UV/midUV/常量）。
3. **pass/draw 数量**：每帧 280 个 render pass（1 motion + 93 bake + 186 材质）、372 次 draw、186 次 Iris 程序绑定。
4. **材质层 overdraw**：`ENTITY_CUTOUT` 依赖 alpha discard（基础贴图 82% 像素 alpha<26），early-Z 被削弱；`EYES` 为半透明混合、不写深度，10,000 个单位两层叠画。

**不建议的归因**：把 67.89 ms 当"显存带宽不够"（292 MB 按 336 GB/s 只需约 2–3 ms）。真正的约束是**主机往返带宽与串行延迟**。

**建议**：

| 步骤 | 改动点 | 预期 |
| --- | --- | --- |
| A 计量 | `HordeGpuRenderer.drawGroup`/`IRIS.draw` 插 5 段顺序 `timerQuery`（motion/bake/copy/base/glow，延迟 2–3 帧读取）；把 `passes/draws/instances/gpuCopy` 写进 BenchMod metric 与 JSONL | 决定后续投入，当前无分阶段数据 |
| B 去串行 | 逐块 copy 到**互不重叠的缓冲偏移**（56 B 对齐），所有项准备完后在**一个** `ENTITY_CUTOUT` pass 内 `drawIndexed(baseVertex, …)` 循环，`EYES` 同理；按 16–64 MB 分块双缓冲控制显存 | 93 次同步 → 1–5 次；若同步占 30–40 ms，可直接砍掉一半 |
| C 发光子集 | `geometry()` 按 glowmask 像素与 quad UV 覆盖生成 `glowIndices`（本模型 11/130 quad），EYES 层用子集索引 | 发光层三角形 ↓≈91% |
| D 静态字预计算 | UV 纹理由 2 float/顶点扩为 8 float（u,v,midU,midV,d1x,d1y,d2x,d2y），shader 中 word11/12 由 8 fetch→1、word13 少 4 次 | 展开取样 −27% |
| E 降级 | 布局不支持时回退实例化路径并退避重试，**不要**把事件永久标 `failed` | 修复"重载/换包后永久消失" |
| F 产品决策 | 距离 LOD／减少可见实例／减少层——**唯一能低于 292 MB 地板的路** | 需用户授权，不能静默减量 |

**验收**：同实例/模型/贴图/发光下，`gpuCopy` 不增加、`drawn=10000` 不变；分阶段计时之和与世界渲染 GPU 可核对；三轮 P50/P95；禁用发光做 A/B 只允许用于诊断；任何降级必须在兼容矩阵中写明。

### H15 连续分割、小范围与高度隔离（P0/P1）

| 子项 | 现状（源码证据） | 判定 | 建议 |
| --- | --- | --- | --- |
| H15.1 单体缺口 | `VISUAL_GAP_MARGIN=0.5`，`RegionBox.of` 用 `floor/ceil` 按 1 格外扩（`HordeClient.java:38,503-511`）；`regionMargin` 只影响绘制块 padding | **已解耦**；但每侧有效余量实际为 **0.5–1.5 格**（均值约 1 格），0.6 宽单位得到 2 格宽盒子 | 文档补"合计最坏 1.5 格/侧"；若要真正小范围，把区域改为 1/16 格定点而非整格外扩 |
| H15.2 连续势流 | 无 `sign`、无 argmax；`flowAround` 为 tanh 型饱和（`horde_motion.glsl:56-63`） | **方向连续**，但 `flowSum += flow*influence` 为 **flow² 加权平均**（:110-112） | 改为保约束的求和后饱和，或先取 `max|flow|` 再合成；两侧目标不允许互相抵消 |
| H15.2 剩余穿透 | 单目标 `across=0` 时位移 ≈0.83r，盒半宽 r ⇒ 正面单位仍与生物重叠约 0.17r | 【源码推断】 | 去掉 `preferredSide*0.2r` 偏置，零位移线回到 `across=0`；正面位移至少 = r |
| H15.3 过渡 | `smoothstep(0,1,time-regionsChanged)`；`previousRegions` 整表替换；端点取 min 角 | **1 刻上限 + 隐含不变式 + 端点错位**（R-B） | 逐区域 `(from,to,startedAt)` + 端点塌缩盒心 + 秒级过渡（UBO 常量），三件一起改 |
| H15.4 高度 | `separation` 与 `smoothstep(0,0.5,…)`（:101-103）；`entity_height` 是**虚拟清道夫 1.8 m**（`HordeSync.java:94-95`），不是目标高度 | 完全归零阈值实为 **2.3 格**；1.8–2.3 格高差仍部分让行 | 文档改正字段语义；判定改用"目标 XZ 处地面"而非单位 lateral 处地面 |
| H15.4 地形 | 高度纹理直接 `update`，无过渡；地形项最大横推 6.7 格、权重 ~45，压过目标项 | **真实突变源** | 高度也做双缓冲过渡；地形项增益降到 ≤2–3 格 |
| H15.6 障碍 | 横向 15.2 格、沿线 16 格、单层 `MOTION_BLOCKING` | 薄墙/屋檐/门洞/坑洞全丢 | 推荐走廊 2D SDF（约 0.6 MB/事件、1 次双线性采样）；多层高度场次之；体素直接采样不可行 |
| H15.7 随机 | drift≤1.8、sway≤1.5、频率 0.006–0.07/刻、基础速度不变 | 数学上不反向；但 `seed` 依赖 `row mod 512` ⇒ 每 512 行整体重随机；端部 1.8 格会聚团 | seed 改无回绕哈希；端部 clamp 后保留位移 |
| H15.5 事件成员 | `isGapTarget` 排除 `HordeParticipant.isEvent`；附件 `serialize+sync`；设置早于 `addFreshEntity` | **完全成立** | 保留；仅建议加调试计数 |

### H11 首次模型准备卡顿（P2）

**根因**：`HordeGpuRenderer.drawPrepared` → `prepare()` → `geometry()` → `HordeMeshCache.get()`（`HordeGpuRenderer.java:129,234,285`）在**渲染线程**同步完成：资源读取/解析 → 递归收集骨骼 → 静态几何捕获 → `frames = ceil(duration×20)` 的大数组分配 → 逐帧 `BoneSnapshot.apply`＋`cube.render` 顶点捕获 → `memAlloc`＋`createBuffer`＋2 张数据纹理。没有状态机、没有预算、没有代际令牌，`clear()` 只清 CPU 帧数组，无法取消。

关键约束：烘焙**写共享 `GeoBone`**（`BoneSnapshot.apply`：`HordeMeshCache.java:72,86,106`），而该模型与真实清道夫共用（`MidnightCleanup` 的 visual 同 `SweeperModel`）。⇒ **不能直接把 prepare 挪后台线程**，否则与真实实体渲染竞争。

**建议**（顺序不可颠倒）：

1. **先计量**：`parse/bake/allocate/upload` 四段纳秒 + `frames/vertexCount/frameBytes` + "header 到首绘"延迟，并入 `statistics()`。
2. **去共享骨骼**：从 `GeoBone` 一次性采集静态 pivot/rest/父链，用 `LcSkeletalPose` 数值直接装配 `PoseStack`（按 `RenderUtil.prepMatrixForBone` 同序），不再写 `BoneSnapshot`。用"旧路径 vs 新路径输出 float[] 的 SHA-256 一致"做单测闸门。
3. **状态机 + 预热**：`EMPTY→PARSING→BAKING(i/N)→UPLOADING→READY/FAILED(token)`；在 `apply(header)` 后立即 warm（线路分页通常还有数十 tick 提前量），按帧预算推进；`clear/资源代际` 递增 token 作废在途结果。
4. **分阶段上传**：帧纹理按行切片 `HordeDataTexture.update(...)`；未 READY 的 visual 直接不画（不画半成品、不标永久 failed）。
5. 失败隔离到 visual 级，避免一个模型错误让所有事件闪断。

### H07 每 tick 扫描（P2）

**根因**（`HordeRuntime.java:158-204,254-319`）：

- `scan` 与 `clearDebris` **各**对每玩家做一次 `minY..maxY` 全高 AABB 查询；默认模拟距离 10 ⇒ 每层每玩家 21×21 列 ×约 24 个区段 ≈ **1 万区段入口**，两次 ≈ 2 万；多玩家重叠范围完全重复。
- 去重 `seen.add` 在实体取回**之后**，只省后续处理，不省查询与遍历。
- 早退条件 `events.isEmpty() && loaded.isEmpty()`：有淡出残留时仍全量；早退分支仍执行 `sync.tick`+`prune`。
- `clearDebris` 每候选做 `events.anyMatch(covers)`（线路最近点）＋`protectedByCombat` 遍历全部 target 及其 members ⇒ O(掉落物×目标×成员)。
- 每 tick 新建 `HashSet/AABB/String key/HordeTargetContext/List.copyOf(loaded)`；`targetEntity` 每次遍历所有 level；`updateRegions` 每群一次 `UUID.nameUUIDFromBytes`（MD5）；`HordeSavedData.advance` 每 tick `setDirty`。

**建议**：① 每 tick 一次 `runningByDimension` 与 `chunkRange` 缓存；② 事件 `activeBounds()` 与玩家 AABB 不相交直接跳过（语义等价，收益最大）；③ `scan`＋`clearDebris` 合并为一次查询按类型分流，`seen` 复用；④ 受保护 AABB 预计算并按区块索引；⑤ `targetEntity` 每 tick 一次 map；⑥ 统计加 `entityQueries/entitiesReturned/debrisCandidates/targets`。

### H14 玩法、持久化与联机（P2）

主链代码基本存在（配额/预约/转派/取消/超时/淡出/存档/订阅/丢页重发），但以下**与验收条件直接冲突**或**没有测试**：

1. **区块卸载即释放归属**：`leave()` 走 `release` → 重载时 `join` 因 `registered=false` 被取消 ⇒ 单位从世界消失，与 H14.2"卸载/重载保持归属"矛盾。
2. **旧超额只淡出已加载成员**：未加载 UUID 永久占名额，`occupied()` 长期 >10，阻塞新生成。
3. **关服恢复**：目标 chunk 未加载时 `tickParticipants` 把 `assignedTarget==null` 当作目标失效 → 转派/淡出。
4. **`leave()` 的 KILLED 早退**：若移除未经 `LivingDeathEvent`，`memberEvents/members` 残留，永久占名额。
5. **不持久化** `reservations/outsideSince/splitSince/seenAt`；存档恢复后出界宽限重新计时。
6. **零 JUnit/GameTest**：`HordeRuntime/HordeSavedData/HordeSync/HordeParticipant` 全无覆盖；`/horde stats` 也没有 per-target 的 quota/occupied/members 摘要。

**建议**：先写"规则表"（卸载/重载、关服恢复、择主、旧超额各一条），再按表改；同时补纯函数单测（配额/预约/转派选择/存档往返/订阅状态机）与一个 dev 命令输出 per-target 摘要。**不要在没有规则表时直接改行为**。

### H03 同域规则归属（P2，规则异议）

"首次建档"= `data.targets().computeIfAbsent(domain|uuid, … event.rules())`（`HordeRuntime.java:283-284`），即 `events()` 迭代顺序中第一个覆盖该目标的事件；`HordeTargetAssignment.rules` 是 `final`，此后无更新路径。后果：**任何配额参数都不受后续事件提高**（不只是硬上限 10），且 `tickParticipants` 的 `outsideTicks/unreachableTicks` 用 provider 事件规则、`maximumQuota/reinforceTicks` 用 assignment 规则 ⇒ 同一目标同时遵循两套规则。

**建议**：先落地规则表（first-wins / max-wins / 每事件独立），若保持 first-wins 就显式稳定排序并统一规则来源；若 max-wins 则把 `rules` 改为可变并提供 `rebindRules`。注意硬上限 10 是常量，实际上限 = `min(10, initialQuota×maximumMultiplier)`，可低于 10。

### H25 专服基准启动受阻（P2）

**根因链**（已核对 Gradle 配置与 BenchMod 插件源码）：

- `build.gradle.kts:136-174` 把 `reretrodamageindicators`、`photon`、`ldlib2`、`kilagraph`、`particlestorm`、`jei`、`jade` 以及 `lib/` 下 iris/sodium/IMBlocker 全部声明为 `implementation` ⇒ 进入所有 run 的 runtimeClasspath。
- BenchMod 0.1.5-beta 的 `ModDevConfigurer` 把 `benchServer` 与 `benchClient` 都绑定到**同一个 `bench` 源集**，没有 client/server 两套 classpath。
- 26.1.2 的 ModDevGradle `hasLegacyClasspath()=false`，不支持 per-run `additionalRuntimeClasspath`（会直接抛错）。
- `loadedMods` 只筛项目 `ModModel`，不阻止 classpath 上的第三方 jar 被 FML 发现（server log 已证明全部加载）。
- `internalTestMods` **不是**原因（唯一消费者是 `internalTestBundle`），文档把它列为嫌疑不准确。

**建议**：新建 `benchServer` 源集（`compileClasspath=bench.compileClasspath`，runtime 只含服务端必需 mod + `benchRuntimeMod`），复制 `benchServer` RunModel 的 systemProperties/programArguments/gameDirectory/loadedMods 到新 run，复用 `verifyBenchServerReport`；长期向上游提 `clientSourceSet/serverSourceSet` 能力。**不要**直接删公共依赖。

### H23 指挥家退出鼠标恢复异常（P2）

**精确机制**：`Minecraft.disconnect` 先置 `clientLevelTeardownInProgress=true`，**之后**才 post `LevelEvent.Unload`；`ConductorControls.clearSession→exit`（`ConductorControls.java:400-404,496-520`）在末尾 `if (minecraft.screen == null) mouseHandler.grabMouse()`，而 `MouseHandler.grabMouse()` 内部**无条件** `minecraft.setScreen(null)`，此时 `setScreen` 命中 `clientLevelTeardownInProgress` 检查 → `IllegalStateException("Trying to return to in-game GUI during disconnection")`。`exit` 既不检查 `active`（基准客户端从未进入指挥模式也会走），也不检查当前是否允许恢复 GUI，且同一次断开会被 `LoggingOut` 与 `Unload` 调用两次。

**建议**：拆分 `resetState()`（清状态，不动鼠标/screen）与 `finishSessionIfNeeded()`；`exit` 去掉 `grabMouse` 副作用；鼠标恢复改为"仅当本次确实接管过输入"且延迟到下一次 `ClientTickEvent.Post`，并检查 `level != null && screen == null && player != null && gameMode != null`；`LevelEvent.Unload` 只做资源清理。不要吞异常。

### H10 RGBA8 运动缓存重复求值（P3）

`horde_motion.fsh:20-31`：component 0/1/2 各做一次完整 `movingLocation`（三次输入相同），component 3 为算朝向额外两次（`t±2`），component 4 再做一次并只用于 `lightAt` ⇒ **6 次/实例**，10,000 实例 = 60,000 次/帧。每次 `location()` 含区域循环（每区域 12 次 `integer()`）＋`heightAt`×约 4＋路由查表 ≈ 40–50 fetch。

**建议**：先测 motion 段占时（预计比 bake 小 1.5–2 个数量级，P3 合理）；低风险做法是 CPU 按 `transition` 预混合区域为 6 个 float（12 fetch→6）＋区域早退；中风险做法是新增"位移缓存 pass"（3 个时刻各算一次），把昂贵求值 6→3。由于 26.1.2 无 MRT/浮点附件，不能用"一个片元写多字"。

### H19 公开协议与 GPU 资源生命周期（P3）

- **无 double-free**：资源单一属主，`GlBuffer/GlTexture.close()` 自带 `closed` 幂等；`remove()` 先摘 map 再 close。
- 真实问题：① `EventData/IRIS/HordeLightField.close()` 顺序 close 无 try/finally，异常会漏关后续资源；② 瞬态布局错误 → `event.failed=true` 永久停显（应与 H24 的降级一起修）；③ `regions` 纹理按 2 的幂反复申请/关闭，且 `HordeDataTexture.updateWords` 每行一次 `writeToTexture`；④ `clear()` 未复位 `gpuCopiedBytes/uploadedBytes/motionWidth` 等统计。
- `GeckoLib Internal`、Iris 内部布局、公开协议保留字段：保留并登记版本表，不以"本项目未调用"删除。

### H16/H17/H20/H21 证据与交付（P2/P3）

- **口径错误（重要）**：`clientFpsLimit=260` 里的 260 是 MC 的 `UNLIMITED_FRAMERATE_CUTOFF`，表示"无限制"，**不是 260 FPS 上限**；实测 `mean_fps=445.76`、`throttle_reason=NONE` 已证明。后续文档应写"不限帧"。
- GPU 查询只有 147/442 帧完成（`gpu_skipped_pending=294`），均值天然偏低，必须同时给 `started/completed/skipped` 与 P95/P99。
- BenchMod 帧间隔（MEASURE 全窗口）与 `PhotonFrameCapture` 的 `render_gpu/cpu`（前 20/30 s 子窗口）是两套口径，不能互相换算 FPS。
- `gpuCopy` 目前**只在 HUD 统计字符串里**，没有落盘 → H24 的数字在现有 artifact 中不可复现，必须新增指标。
- H20：实际是 **5 项暂存**（`HordeRenderScenario`、`LevelMixin`、`horde_motion.fsh/.vsh/.glsl`），文档写"3 个"需更正；`horde-fixes…`/`horde-docs-consolidation…` 两个备份目录 **ACL 拒绝枚举**，不能声称"已核对"。
- H17：`.backups/horde-retest-20261022/before-runtime-config.json` 确认不存在（不可补）；工作区 `logs/*.log.gz` 是单元测试日志，不是游戏日志。
- H21：人工边界与源码一致（1.8 间距、上限 10、0.5 余量、64 格横移＋1.5 摆幅、模型/线路/光照 CPU 准备仍在），但"是否仍平滑"必须由 H15 实测回答。

---

## 3. 文档与代码不一致清单（本轮新增结论）

| # | 文档表述 | 代码事实 | 处理 |
| --- | --- | --- | --- |
| 1 | `horde-issues.md:35` "无 CPU 映射…只在 GPU 内从附件复制到顶点缓冲" | GL 后端 `copyTextureToBuffer` = `glReadPixels` 到 PBO（主机回读） | **必须改**；这是 H24 根因判断的前提 |
| 2 | `horde-issues.md:37,39` "不能把 67.89 ms 全部归因复制" | 复制是主机往返且 93 次串行，恰是主因 | 改写为"归因于回读串行，需分阶段计时确认占比" |
| 3 | `horde-rendering-implementation.md:12` "目标变化或旧端点收敛才提升版本" | 只有 `!regions.equals(updated)`；另半句恒假，**不存在**"旧端点收敛"触发器 | 更正 |
| 4 | `horde-issues.md:53,64` "高度判断使用服务器同步的实体实际高度" | 发的是虚拟清道夫模型高度 1.8 m | 更正字段语义 |
| 5 | `horde-issues.md:53,206` "一 tick 过渡" 被当作已满足平滑 | 1 刻 = 50 ms，且与帧率耦合 | 标注为上限，不是达标 |
| 6 | `horde-issues.md:87` "无活动事件时跳过查询" | 条件含 `loaded.isEmpty()`，且有淡出残留即全量 | 补充条件 |
| 7 | `horde-events.md:35` "区块卸载释放名额" vs `horde-issues.md:129` "卸载/重载保持归属" | 二者互斥，现状=释放且重载丢失 | 先定规则表 |
| 8 | `horde-events.md:27` "默认最多 10" | 实际上限 = `min(10, init×mult)`，可低于 10，后续事件不提高 | 统一表述 |
| 9 | `benchmod.md` 多轮 "配置上限 260" | 260 = 无限制 | 改为"不限帧" |
| 10 | `benchmod.md` "H25 由 internalTestMods 引入" | `internalTestMods` 只进内测 bundle | 改为 `implementation` + bench 共用源集 |
| 11 | `horde-issues.md:190` "原 5 项暂存" vs 同页只列 3 个文件 | 实际 5 个（另含 `LevelMixin.java`、`horde_motion.vsh`） | 更正清单 |
| 12 | `horde-issues.md:73` "后台线程会触及共享 GeoBone" | 当前无后台线程；应写成"后台化前必须先解除共享写" | 改写 |

---

## 4. 建议的实施路线（不含运行验证）

**阶段 0（先测量，不改行为）**：H24 分阶段 GPU 计时＋`gpuCopy/passes/draws` 落盘；H11 四段耗时；H07 查询/实体计数；H15 的过渡时长与区域数进入统计。**没有这些数字，后续改动无法验收。**

**阶段 1（低风险、纯收益）**：
1. H15 客户端区域按线路走廊过滤（同时改善缺口质量、H07.3 的 O(N) 与 H07.2 上传量）。
2. H15 `flowAround` 混合算子改为"求和后饱和"；去掉 `preferredSide*0.2r` 偏置。
3. H15 端点塌缩到盒心（3 行）。
4. H24 发光子集索引＋静态 UV 预计算。
5. H07 早退条件修正、`runningByDimension`/`chunkRange` 缓存、`scan+clearDebris` 合并。
6. H24/H19 布局不支持时降级而非永久 failed；`close()` 加 try/finally。

**阶段 2（结构改动，需先有阶段 0 数据）**：
1. H24 去串行（批量回读 + 单 pass 多 `baseVertex` 绘制 + 分块双缓冲）。
2. H15 逐区域 `(from,to,startedAt)` + 秒级过渡 + 高度纹理过渡。
3. H11 纯数值姿态烘焙（去掉共享骨骼写）→ 状态机 → 预热 → 分阶段上传。
4. H14/H03 规则表 + 归属/持久化修正 + 单测。

**阶段 3（需产品决策）**：Iris 大负载下的顶点数/距离 LOD（唯一能突破 292 MB 地板）；H15.6 走廊 SDF；H03 规则语义（first-wins / max-wins）。

---

## 5. 随后实测要验证的假设（供测试后文档对照）

| 假设 | 验证方式 | 判定标准 |
| --- | --- | --- |
| A1 292 MB/帧的回读是 67.89 ms 主因 | 分阶段 GPU 计时；`copyTextureToBuffer` 次数；禁用材质/禁发光消融 | copy 段占 GPU 时间 ≥50%，且随块数线性 |
| A2 无光影路径不受影响 | BenchMod Sodium 10000 场景 | 帧间隔均值 ≈2–5 ms 量级，`gpuCopy=0` |
| A3 1 刻过渡在低帧率下等于硬切 | 固定事件、录 30/60/144 FPS 位移曲线 | 低帧率下横向位移出现单帧台阶 |
| A4 首次可见帧仍有数百 ms 阻塞 | BenchMod 首帧/稳定期最大帧 | max frame ≥200 ms 且集中在首事件 |
| A5 专服失败是客户端 mod 进 classpath | 运行 BenchMod Server，抓 `NoClassDefFoundError: MultiBufferSource` | 复现同一栈与 mod 列表 |
| A6 断连仍抛鼠标异常 | BenchMod 客户端退出后查日志 | 出现 `Trying to return to in-game GUI` |
| A7 分流/高度隔离回归未坏 | 静态验证 + 规则/区域测试 | 现有 `HordeRulesTest`/`HordeRegionTest` 通过 |

---

## 6. 本轮边界

- 本轮为**只读分析**，未修改任何源码、资源、Gradle；只新增本文与随后的测试后文档。
- 未运行游戏时的一切结论均标为【源码证实】或【源码推断】；涉及观感（平滑、缺口宽度、发光外观）的结论**只有实测才能关闭**。
- 代码位置均来自当前工作区（含未提交实现），行号可能随后续修改移动。
