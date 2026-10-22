# 深夜清扫性能、CPU 侧与冗余工作审计

日期：2026-10-22。对象为当前 `horde-recovery` 工作区。记录开始时 HEAD 为 `fbfdcd0`；本文初版随文档提交 `6aabf40`，此后补充任务记录并实施首个烘焙帧动画优化。工作区中仍存在未提交的 motion 着色器、粗高度、BenchMod 等改动；本文按工作区当前文件读取。

本文初版是只读静态审计，不是性能验收：当时没有构建、Data、启动游戏、JFR、Spark 或 GPU 计时。文中的“高/中/低”是静态优先级，不是实测耗时。正确性问题（例如清除后客户端可能收不到 remove、目标表只增不减、骨骼 X 平移符号）另见文末非性能问题摘要和任务记录。第 9、10 节记录随后的优化实施；实施有 IDEA 编译、Data 和单元测试检查，但没有游戏内着色器、视觉或性能验证。

## 1. 当前工作分布

| 阶段 | CPU 侧 | GPU 侧 | 频次 |
|---|---|---|---|
| 服务端事件调度 | 事件推进、粗高度脏块刷新、目标扫描、配额、区域分组、出生、同步、清理 | 无 | 每逻辑刻 |
| 客户端提取 | 批次矩形、视锥、区块、范围、显示上限 | 无 | 每帧 |
| 客户端准备 | 光场区段收集与上传、区域/高度纹理更新、参数上传 | 纹理/缓冲上传 | 每帧或按版本 |
| 客户端绘制 | 批次拆分、提交 | motion pass、实例化主体 pass | 每帧 |
| 网络同步 | 每个玩家订阅、路由节点与事件 header；不发送显示区域页 | 无 | 每刻/每 20 刻 header |

主要入口：

- 服务端：[HordeRuntime.tick](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L164)、[HordeSync.tick](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java#L43)。
- 客户端提取：[HordeClient.extract](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L78)。
- 渲染准备：[HordeGpuRenderer.drawPrepared](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java#L84)、[HordeLightField.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java#L39)。
- GPU：motion pass [horde_motion.fsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh#L7)、主体 [horde_instanced.vsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh#L52)。

## 2. 性能热点

### P1：每顶点骨骼链是最大的 GPU 顶点成本（本轮已改为烘焙静态帧）

原实现的 [horde_instanced.vsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh#L21-L56) `animate()` 对每个顶点沿父骨骼链逐层计算变换：

- 每层读取两帧的平移、四元数、缩放和父索引，约 22 次数据纹理读取；
- 层级最多 64；
- 每个实例的每个顶点都会重复整条链。

motion pass 只把位置、让行、朝向和光照降到逐实例，骨骼动画仍然是逐顶点。以 5 层骨骼、每顶点约 110 次纹理读取估算，5000 个实例、数百顶点时每帧就是数千万到上亿次纹理读取；实际值取决于 Sweeper 模型顶点数和骨骼深度，必须实测，但结构上这是第一优先级。

本轮已改为烘焙静态帧：烘焙阶段把每帧所有骨骼变换后的顶点位置/法线写入数据纹理，顶点阶段只按单位相位选帧并读取该帧的 6 个 float，不再执行父链循环、骨骼矩阵或四元数插值；帧号取整后循环切换，不是连续插值。详见第 9 节。

后续方向（不在本轮）：

- 如果帧切换出现明显跳变，可评估相邻帧插值或提高烘焙帧率；
- 逐顶点仍需要读取 6 个浮点，可进一步评估更紧凑的帧数据编码；
- 评估是否需要对远距离实例降低动画采样，但这会重新引入当前已删除的 LOD 分支。

### P1：客户端每帧重建批次和光场区段集合（本轮改为 tick 区域检测）

[HordeClient.extract](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L112-L166) 仍每帧从时间、线路块生成行列批次，并做距离、视锥、已加载区块和显示上限过滤；[HordeClient.tick](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L85-L105) 本轮只检测本地玩家和附近真实战斗目标是否进入线路走廊，命中后更新事件的 GPU 让行区域。tick 不再缓存绘制批次；本地玩家每 tick 更新，其他目标按距离延长检测/更新间隔。

仍保留的成本：

- `extract` 每帧遍历全部绘制块并计算活动行号；
- 目标区域检测每 tick 扫描附近可渲染实体，并对每个事件遍历绘制块判断走廊；
- [HordeLightField.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java#L39-L99) 仍每帧重建 `HashSet<Section>`、排序并处理区段槽位。

区段集合在大多数帧是稳定的，可以按绘制块缓存，只有批次集合或相机范围变化时重建；当前光照侧仍每帧从头做。

### P1：全线路同步扫描，不是文档描述的“附近路段”

[HordeSync.tick](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java#L64-L126) 每次 header 都把范围设成 `0..size-1`，并发送 `scope_first/scope_last`；客户端没有读取这两个字段。路由发送循环的范围约等于整条线路，在 revision 都没变化时也会每 tick 扫描大量节点，因为 `remaining` 只在真正发送时减少。

影响：

- 每 tick 每玩家每事件 O(线路节点数) 的 revision 查询；
- 长线路首次同步到 ready 的时间长；
- 与 [horde-events.md](horde-events.md#L56) 的“仅订阅附近路段”不一致，而 [horde-rendering-implementation.md](horde-rendering-implementation.md#L11) 又要求客户端收齐全部节点。

### P1：`covers()` 和 `route.nearest()` 是未纳入预算的线性工作

[HordeEvent.covers](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEvent.java#L106-L110) 调用 [HordeRoute.nearest](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java#L101-L124)，在 `[tail, head]` 对应路段内逐节点求最近点。

调用点包括：

- 扫描目标时对每个候选实体调用，[HordeRuntime.scan](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L293-L301)；
- 参战单位每 20 tick 检查是否仍在覆盖范围，[HordeRuntime.tickParticipants](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L238)；
- 目标 provider 选择，[HordeRuntime.provider](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L350-L357)。

`scanNodesPerTick` 限制的是粗网格节点数，每个节点命中的实体数量没有上限；浪潮后期 `tail..head` 接近全线路时，每次 `covers()` 都是近全线路扫描。长线路、多实体场景会放大。

可行方向：按弧长构建空间索引或把场景限制到“节点窗口 + 少量邻域”，并对 `covers()` 使用二分定位的局部搜索；这些都需要单独设计，不在本文实施。

### P2：服务端每刻遍历目标、成员和存档

- [HordeRuntime.assignment](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L550-L555) 每次被参战单位调用时流式扫描全部目标，复杂度约为“参战单位 × 目标数”。可以维护 `targetId -> assignment` 索引。
- [HordeRuntime.tickTargets](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L311-L329) 每刻遍历全部目标，只有 `due` 的目标才更新位置，但循环本身不省。
- [HordeRuntime.prune](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L690-L696) 每刻重建域集合并过滤全部目标；它更适合在事件创建/删除时运行，或使用版本号。
- [HordeSavedData.advance](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSavedData.java#L45-L48) 每刻 `setDirty()`，自动存档时又要写入完整线路和每个目标的完整规则。
- 目标条目和 `rules` 目前只增不减，`seenAt` 写入后从未读取；长期运行会同时增加服务端 CPU 和存档体积。

### P2：绘制批次、背面和默认无上限

[HordeGpuRenderer.drawPrepared](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java#L143-L163) 每个可见绘制块提交一次 `drawIndexed`。线路段 16、横向 16 格的组合已经把批次压得较低，但长线路/宽浪潮下 draw call 数仍会增长。

[horde_instanced](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java#L72-L82) 管线使用 `withCull(false)`，即不剔除背面。对实体模型是否必须关闭剔除需要单独确认；如果模型绕序正确，开启背面剔除可以减少无效片元和顶点处理。

[客户端默认值](../../src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java#L18-L19) 是 `maximumVisible=-1`、`rangeChunks=-1`，即默认不限制显示数量并跟随视野。文档说明了这是显式选择，但没有任何自动降级兜底，因此“长线路 + 宽浪潮 + 远视野 + 多事件”会直接叠加成本。

### P2：motion pass 对同一实例重复计算 `location()`

[horde_motion.fsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh#L7-L34) 中，`location()` 在分支外被调用，因此每个 component 都会算一次；对朝向 component 3、4 又各调用两次（取得当前点与前后采样点）。同一个实例的同一 station/lateral 一次 motion pass 内可能执行约 11 次 `location()`，每次包含：

- 弧长查表与节点插值；
- 区域让行循环；
- 两次高度采样。

motion pass 本来是为了避免逐顶点做这些工作，但逐 component 的重复计算仍然明显。优化方向是把“位置、朝向、光照”编码到更少的通道，或先由一个更便宜的 pass 生成逐实例状态，再由 motion 编码 pass 转换成 8 通道；也可以在分支前按 component 只计算必要部分，避免无意义的 `location()`。

## 3. 放在 CPU 上的工作清单

| 工作 | 位置 | 频次 | 判断 | 备注 |
|---|---|---|---|---|
| 玩家/目标区域检测 | [HordeClient.tick](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L85-L105) | 本地每 tick，远目标延迟 | 已实施 | 只更新 GPU 让行区域，不缓存绘制批次 |
| 光场区段 `HashSet`/排序 | [HordeLightField.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java#L39-L99) | 每帧 | 可缓存 | 区段集合按块缓存，变化时重建 |
| 每批参数上传 | [HordeGpuRenderer.writeParameters](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java#L194-L218) | 每帧 | 必要 | 时间/相机/光照每帧变化；可减少每批 UBO 数量 |
| 全线路 revision 扫描 | [HordeSync.tick](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java#L104-L126) | 每 tick/玩家/事件 | 可优化 | 维护待发送队列，只处理未发送节点 |
| `covers()` 最近点 | [HordeEvent.covers](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEvent.java#L106-L110) | 每候选/每 20 tick 参战单位 | 可优化 | 局部窗口或空间索引 |
| 全目标 `assignment` 查找 | [HordeRuntime.assignment](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L550-L555) | 每 20 tick/参战单位 | 可优化 | 改用 `targetId -> assignment` 映射 |
| 全目标 prune | [HordeRuntime.prune](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L690-L696) | 每 tick | 可移除 | 事件结束或定时执行 |
| `advance()` 脏标记 | [HordeSavedData.advance](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSavedData.java#L45-L48) | 每 tick | 可拆分 | 时钟推进不必等于存档变脏 |
| 初始线路准备/烘焙 | [HordeRoute.prepare](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java#L140-L179)、[HordeMeshCache.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java#L36-L79) | 事件启动/资源代际 | 一次性 | 会造成启动卡顿，应避免在渲染关键帧内做 |
| 聚合脚步声/扬尘 | [HordeAmbient.tick](../../src/main/java/org/unitego/lobecorp/client/horde/HordeAmbient.java#L21-L63) | 每客户端 tick | 低 | 已按可见块摘要聚合，不是逐单位成本 |

## 4. 冗余功能与失效设计

### 已写入但未被读取的字段/API

- [HordeEvent.revision](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEvent.java#L21-L62)：会保存、加载、递增，但网络和客户端都不读取；`changed()` 目前没有可观察消费者。
- [HordeTargetAssignment.seenAt](../../src/main/java/org/unitego/lobecorp/world/horde/HordeTargetAssignment.java#L29)：扫描时写入，没有任何 TTL 清理逻辑读取。
- [HordeEventSnapshot](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEventSnapshot.java#L7) 和 [HordeEvents.snapshot](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEvents.java#L89-L92)：当前工作区没有调用方。
- [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java#L82-L83) 的 `scope_first/scope_last`：发送后客户端忽略。
- [HordeRoute.slice](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java#L280-L291) 的 `total`、`length`：客户端只读 `first` 和 `nodes`。
- [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java#L194-L202) 外层 `clock`：客户端 `apply` 不读取。

这些字段单独看都不大，但会让“当前实际行为”和文档描述持续偏离，也不利于后续做全量/局部同步的切换。

### 文档存在、实现没有的行为

- 虚拟单位的 `fadeTicks` 透明度渐变：文档 [horde-events.md](horde-events.md#L23) 有描述，着色器当前只按区间剔除，片元 alpha 固定为 1。
- “仅订阅附近路段”：文档与当前全线路同步不符。
- 命令的 seed 参数：文档仍保留旧语法，当前命令没有 seed。
- 客户端默认值 5000/12：文档仍写旧值，代码是 -1/-1。
- [HordeEventConfig](../../src/main/java/org/unitego/lobecorp/config/server/HordeEventConfig.java#L20) 的 duration 注释仍说收尾使用相同时长，实际按 `route.length / speed`。

### 可能不必要的重复数据

- 每个 [HordeTargetAssignment](../../src/main/java/org/unitego/lobecorp/world/horde/HordeTargetAssignment.java#L58-L74) 都保存一份完整 `HordeRules`。规则可按 domain 或事件共享，目标只需引用。
- 事件 targets 只增不减时，规则副本、成员集合和存档文本会持续叠加。
- 服务端已不再向客户端序列化显示区域；目标区域分组仍保留用于参战转派，是否进一步与目标表解耦需专项设计。

## 5. 建议的验证顺序

1. **采样拆分**：分别记录服务端 tick、客户端 extract、客户端 prepare、motion pass、主体 draw 的耗时；先用 Spark/JFR 确认是 CPU 还是 GPU 限制，不直接拿帧间隔推断。
2. **骨骼成本**：固定实例数，比较不同骨骼深度/顶点数的模型；或临时用固定姿态替换动画，观察 GPU 时间变化。
3. **光场与批次**：固定场景，比较“每帧重建”和“按块缓存”两种思路的 CPU 时间；同时记录区段数、批次数和 draw call 数。
4. **网络**：记录长线路首次 ready 所需的 tick 数、每 tick 发送字节和客户端请求次数。
5. **目标表**：长时间运行后输出 `targets` 数量、存档大小、`assignment` 调用次数；验证同 profile 连续事件的清理行为。
6. **绘制开关**：分别测试背面剔除开/关、显式 `maximumVisible` 上限、单事件/多事件，验证收益和画面边界。

以上都没有在本次执行；本文只记录静态审计结论。

## 6. 优先级结论

| 优先级 | 项目 | 预期收益 | 风险/前置 |
|---|---|---|---|
| P1 | 烘焙每帧静态顶点，循环切换帧（本轮已实施） | 父链、骨骼矩阵和四元数插值移出顶点阶段；顶点改为读取 6 个浮点 | 帧切换无插值，可能有跳变；需视觉验收 |
| P1 | 光场区段集合与批次缓存 | 降低客户端每帧 CPU/分配 | 需要失效条件正确 |
| P1 | 全线路同步改为待发送队列/局部窗口 | 降低每 tick 扫描和首次延迟 | 客户端完整性约定要同步调整 |
| P1 | `covers()`/`assignment` 索引化 | 长线路、多目标时降 CPU | 需要保持区域/配额语义不变 |
| P2 | motion pass 去重 `location()` | 降低 motion pass GPU 成本 | 需要重新设计通道编码 |
| P2 | 目标表 TTL 与规则共享 | 降 CPU、存档体积 | 要先确认目标保留语义 |
| P2 | 背面剔除、显式上限对照 | 降 GPU/片元成本 | 需要视觉回归 |
| P3 | 清理未使用字段/API与文档偏差 | 降低维护成本 | 不影响当前运行 |

## 7. 附：本次读码发现但不属于本审计重点的问题

以下问题优先影响正确性、可见结果或恢复状态，性能只是次要影响；本文不展开修复设计：

- `/clear` 后如果运行时已经没有事件和加载实体，[HordeRuntime.tick](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L166-L168) 会提前返回，跳过 [HordeSync.tick](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java#L43)，客户端可能收不到 `remove`，虚拟浪潮在客户端时间冻结后残留。
- [HordeTargetAssignment.seenAt](../../src/main/java/org/unitego/lobecorp/world/horde/HordeTargetAssignment.java#L29) 没有 TTL 使用方；[HordeRuntime.prune](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L690-L696) 只清理当前没有事件覆盖的 domain，同 profile 的新事件会保留旧目标。
- [HordeMeshCache.buildBoneMatrices/bakeVertex](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java#L106-L164) 对骨骼平移使用 `+position.x`，而普通实体路径 [LcAnimationBoneAdapter](../../src/main/java/org/unitego/lobecorp/client/animation/LcAnimationBoneAdapter.java#L53-L65) 经 GeckoLib `BoneSnapshot.translate` 实际得到 `-position.x`；带横向平移的动画可能左右镜像。
- 战斗范围判定使用 `rules.margin()`，渲染高度和让行使用 `regionMargin`；配置不同时会出现“画得到但不算覆盖”或相反的情况，见 [HordeEvent.covers](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEvent.java#L106-L110) 与 [HordeRoute.prepare](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java#L140-L142)。
- 客户端 header 解析在 [HordeClient.apply](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L50-L76) 中先创建事件再校验；解析失败会留下半初始化事件，后续 route 包不会再触发重同步请求。
- [HordeLightField.ensureAtlas](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java#L148-L156) 在区段数超过约 4096 时会抛异常，[HordeGpuRenderer.draw](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java#L84-L92) 会把该事件标记为失败并停止显示。

## 8. 本次任务记录

- 2026-10-22：初版按当前工作区只读复查深夜清扫的服务端调度、网络同步、客户端提取/光场和 GPU 管线，形成本文；随后的首个优化实施见第 9 节。
- 初版检查方式：读取 Java、GLSL、配置和系统文档；当时未构建、Data、启动游戏、JFR 或 Spark 采样；初版未修改源码、资源和配置。
- 文档落地：通过 IDEA 创建/更新 Markdown，并同步[系统文档索引](README.md#L14)与[修改记录](changes.md#L3-L8)。
- Git：初版文档和任务记录已单独提交；代码实施的提交状态见第 9 节。
- 备份：本次未新增备份。

## 9. 本轮实施记录：GeckoLib 烘焙静态帧与循环切换

实施日期：2026-10-22，对应第 6 节 P1 第一项。

### 改动

- [HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java#L65-L181) 在烘焙阶段把每帧所有骨骼变换后的顶点烘焙成静态帧数据：每顶点每帧保存模型空间位置和法线，共 6 个 float。每帧通过 GeckoLib `BoneSnapshot`、`LcAnimationBoneAdapter` 和 `RenderUtil.prepMatrixForBone` 链/`GeoCube.render` 直接生成顶点，父链、枢轴、基础旋转、缩放和法线均由 GeckoLib 处理；不再自写骨骼矩阵，也不使用会额外平移到骨骼枢轴的 `transformToBone`。`BoneSnapshot`、帧捕获器和 `PoseStack` 只创建一次，在帧循环内复用，每帧结果写入一次分配好的 `frameData` 并由 `Model` 缓存。
- 此前的自写矩阵在 X 平移符号和旋转顺序上有误，导致烘焙朝向与动画不正确；现改为 GeckoLib 的同一套 snapshot 入口，并复用现有实体动画适配器。
- [HordeGpuRenderer](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java#L205-L246) 的 `HordeAnimation` 纹理改为帧顶点纹理；`AnimationInfo.x` 从骨骼数改为顶点数，帧数仍为 `AnimationInfo.y`，时长仍为 `AnimationInfo.z`。
- [horde_instanced.vsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh#L21-L56) 的 `animate()` 改为按单位相位选择帧号，读取该帧的 6 个 float 后直接作为位置和法线；帧号取整后循环切换。顶点阶段不再有骨骼索引、父链循环、骨骼矩阵或四元数插值。
- 行为差异：从连续骨骼动画变为每秒 20 帧的静态帧切换，可能出现明显跳变；每个单位仍保留由行列决定的相位差，不会全部同步。
- 顶点侧成本：从每顶点约 `22 × 骨骼链深度` 次数据纹理读取，变为固定 6 次。静态顶点缓冲和索引不变，仍保留原有的 Position/UV/Color/Normal 顶点格式。

### 实际检查

- IDEA 项目构建：成功，约 14.3 秒，无编译器消息。
- `runData`：BUILD SUCCESSFUL，约 30 秒；23 个缓存输出，written=0、removed stale=0。
- 未启动游戏、未编译运行时着色器、未做视觉或性能采样。因此不能声称动画视觉通过。

### 未验证与风险

- 20 帧/秒的帧切换与旧动画的视觉差异，尤其关节和快速动作；
- GeckoLib snapshot 路径的朝向、镜像和法线仍需游戏内确认；
- 烘焙期临时挂载 `BoneSnapshot` 依赖单线程资源准备，未验证并发或热重载场景；
- 着色器语法和运行时管线只能靠游戏内加载验证；
- 帧切换无插值，低帧率下可能显得卡顿；若不可接受，需要提高烘焙帧率或恢复相邻帧插值。

### Git

本次代码改动所在文件在工作区已有其他未提交修改。为避免把既有改动混入，本次实现暂未单独提交；文档与实现状态在本节和修改记录中如实标注。

## 10. 本轮实施记录：客户端目标区域检测、线路平滑与出生调整

实施日期：2026-10-22。对应本轮新增需求：tick 只做区域检测、检测到玩家/真实战斗目标后通知 GPU 分割、线路转弯平滑、服务端只负责生成与出生朝向/目标。

### 改动

- [HordeClient.tick](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L85-L105) 不再缓存绘制批次，只做目标区域检测：本地玩家每 tick 检查是否进入线路走廊，附近其他 `LivingEntity` 按到本地玩家的距离延长检测/更新间隔；命中后通过 [ClientEvent.updateGaps](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L409-L470) 更新事件的让行区域并提升 `regionVersion`，GPU 下一帧读取新区域。
- [HordeClient.extract](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L112-L166) 恢复逐帧从时间、线路块生成行列批次；tick 不保存 `Batch`，也不重算所有行号。
- [HordeClient.Region](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java#L478-L516) 增加本地 `Vec3 + 半径` 更新入口，保留原有平滑进出和过期规则。
- [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java) 不再准备或发送 `regions` 页面；[HordeRuntime](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) 也移除了 GPU 显示区域的序列化缓存。服务端仍保留目标扫描和区域分组供参战转派，客户端显示分割由本地目标检测产生。
- [HordeSamplingUtil.loop](../../src/main/java/org/unitego/lobecorp/util/horde/HordeSamplingUtil.java#L20-L71) 使用向心 Catmull-Rom 和 8—64 自适应采样，长线段和转角获得更密节点。
- [HordeSpawnPlanner.candidate](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSpawnPlanner.java#L24-L38) 生成距离至少 16 格，保持在目标后方并沿虚拟潮来向一侧；[HordeRuntime.processRequests](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java#L411-L432) 创建后立即朝向目标，在加入世界前保留 `bind`，立即设置攻击目标和 Brain 记忆。
- [HordeRegionTest](../../src/test/java/org/unitego/lobecorp/client/horde/HordeRegionTest.java#L9-L40) 改用本地更新入口；[HordeRulesTest](../../src/test/java/org/unitego/lobecorp/world/horde/HordeRulesTest.java#L90-L95) 不再断言固定 16 样本数。

### 实际检查

- IDEA 项目构建：成功，约 6.7 秒，无编译器消息。
- `HordeRegionTest`：2/2 通过；`HordeRulesTest`：9/9 通过。
- `runData`：BUILD SUCCESSFUL，约 35 秒；23 缓存输出，written=0，removed stale=0。
- 未启动游戏、未编译运行时着色器、未做视觉或性能采样。

### 未验证与风险

- 按用户选择 B，客户端目标筛选保留“存活、非旁观、非事件参战、非尸体、非 Indigo 试炼”，不新增目标 UUID 同步；它比服务端实际接受目标的规则更宽，普通生物也可能触发让行缺口。
- 每个目标一个区域，没有像服务端那样按 32 格单元合并近距离目标；目标密集时 GPU 区域数量可能偏高。
- 区域检测每 tick 扫描附近实体，并对每个事件遍历绘制块判断是否在走廊内，性能需要实测；尚未加入空间索引。
- 本地玩家始终参与检测，旁观/未参战状态是否应排除仍需确认。
- 向心 Catmull-Rom 的自适应样本会提高线路节点数；超长或锚点过多的线路可能触发 `HordeRoute` 的 8192 节点上限。
- 出生距离固定最小 16 格，具体观感与接敌时间需要实机调参；朝向只在生成瞬间设置，后续仍由 AI 导航接管。
- 服务端已停止发送显示区域，但本地目标检测与 GPU 分割没有运行时验证；旧客户端/服务端不兼容风险未测试。
- 烘焙帧动画仍未经游戏内加载验证。
- 代码所在文件仍有既有未提交改动，本轮代码暂未单独提交。

### Git

客户端目标区域检测、线路采样、出生、网络区域同步移除和测试改动均写入工作区；因目标文件已有其他未提交修改，未单独提交，避免混入既有改动。
