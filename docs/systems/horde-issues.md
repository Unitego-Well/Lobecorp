# Horde 问题汇总

更新日期：2026-10-26。范围仅为 `.workspace/lobecorp_horde`、`codex/horde` 当前完整工作区，包含原有暂存和未暂存实现。本页是唯一的问题清单；已完成且已验证的事项移至[修改记录](changes.md)，进度见[总览](horde-index.md)，原始基准见[BenchMod](benchmod.md)。

前轮已执行IDEA编译、20项Horde回归、三轮Data、基准制品编译、Minecraft MCP+Debug及两轮当时代码的Sodium客户端复测。测试通过不代表全部问题解决。用户要求不再主动询问：以下未定事项记录并沿用保守替代，只有用户要求时再提问，不将等待确认伪装成已解决。

## 2026-10-26 基准有效性、复测与优化后问题

### 已确认事实
- **H27 根因已确认**：bench 夹具用 `context.player().position()` 同时作事件锚点与相机基准；隔离世界里玩家实际悬空 y≈68.6、地面 y=-60，相机被抬到地面之上约 192.6 格。12 区块渲染雾端为 192 格，地面与全部虚拟单位都在雾外，故截图近乎天空，而旧 verify 只查计数/文件仍判 PASSED。修复为地面锚定 + 同机位关闭浪潮 A/B 像素门禁 + 相机几何断言 + `visible_pixels` 指标；错误画面不能再通过。
- **修改后光影三轮达标**：client-1792950624281/1792950875573/1792951133286，帧均 14.444/14.456/14.992 ms、P99 16.348/16.273/16.919 ms、世界 GPU 14.089/14.123/14.648 ms；Horde draw 7.354/7.343/7.945 ms，展开顶点 0.659M、Iris 复制 36.9 MB/帧，单位 CPU P95 0.60–0.62 ms。三轮帧均差 3.7%。
- 普通同夹具 client-1792951390739：帧均 3.365 ms/P99 6.777 ms、世界 GPU 1.663 ms、draw 1.094 ms，相对修改前 3.706/7.940/1.850/1.861 均有下降。
- 64/256 尸体、10000 数量、Sodium+Iris 路径、BenchMod Compile 退出 0 与 summary PASSED 只证明对应流程/计数；不等于完整画面与功能验收。

### 真实事件取证（Debug + Minecraft MCP）
- 在有命令的隔离世界用 Debug 客户端加 MCP 端口 19876 启动 `/lobecorp horde start lobecorp:midnight_cleanup 161.5 56 3.5 -158.5 56 3.5 20000 96`；事件 id cad5c81e-4f36-4724-a96f-c848a9c4fedd 实际启动并在画面可见。
- 客户端 debug overlay：events=1；submitted 与 GPU instances 相等并随波次变化（3180→9484、7538、9364/9359 等），batches 56→154，lightSections 153→476，gpuCopy=0。
- Debug 求值：`HordeVisualConfig` 动画帧/ LOD / ENABLED = 4/true/true；`HordeGpuRenderer.statistics()` modelBuilds=3、irisMiWords=16、slots=2、lightSections=374；`HordeClient.batches()` 合计 9434。
- 夜间时间 13000 下单位出现红色发光；近/中/远三档取景与 650ms 间隔两帧可见位姿变化；x=110→60 镜头移动后单位持续可见，未观察到停显或数量归零。
- 未完成：区域 3 格净空/平滑退出的定量读数、E4、真实事件 256 尸体；截图与哈希见 `build/agent-backups/20261026-runtime-event/`。

### 新增/剩余问题
- **I29 [P1／待验收] 中远 LOD 外观与切换**：2026-10-26 第二轮把中景从 4 盒 112 顶点改为 5 盒 136 顶点、远景从 1 盒 24 改为 2 盒 48；第三轮又按模型骨骼核对补回背后的罐子（`can`+`biomass`）独立盒与局部红色：中景 5 盒现为腿脚/躯干/双臂(合并)/头/罐子（136 顶点不变），远景仍 2 盒；`bestFace` 只在罐子/上身背面（+z）取生物质红面，其余面保持灰身体/灰罐体。修正后截图 `build/agent-backups/20261026-appearance-spark/artifacts/screenshots/horde-render.png` 显示灰身体/灰罐体加局部红；旧整体红截图保留在 `build/agent-backups/20261026-appearance-can/`。罐体轮廓仍是包围盒，LOD 切换迟滞仍未录像验证无跳变。近景仍 520 全精度、动画仍 4 帧。不得以“性能达标”代替外观验收。
- **I30 [P1／部分完成] 游戏内功能矩阵**：2026-10-26 已用 Debug 客户端 + Minecraft MCP 完成真实事件部分取证：`/lobecorp horde start` 启动事件并在画面可见；客户端 overlay 显示 events=1、submitted=GPU instances 随波次 3180→9484、batches 56→154、lightSections 153→476、gpuCopy=0；Debug 求值确认 animationFrames=4、LOD/ENABLED=true、modelBuilds=3、irisMiWords=16/slots=2；夜间截图可见单位发光，近/中/远三档与两次 650ms 间隔的位姿变化、x=110→60 镜头移动下单位持续可见。**未完成**：区域 3 格净空与平滑退出的定量证据（未取到 penetration/rejected 回读；召唤 3 头牛后仅取得目标附近截图，不足以证明不进入区域）、E4 换包/重载/窗口变化、真实事件下的 256 尸体。契约级补充：`HordeRegionTest`（3 格净空/中断进出连续）、`HordeSpatialFieldTest`、`HordeRulesTest`、`HordeCleanupTest`（200tick 尸体）、`HordeOptimizationTest` 本轮 IDEA 运行均退出 0，不替代游戏内定量。证据 `build/agent-backups/20261026-runtime-event/`（截图、runtime-event.json、sha256）。
- **I31 [P2] 首帧与稳定尖峰**：修改后 STABILIZE 216–341 ms、WARMUP 最大 29–98 ms、MEASURE 单帧最大 45–69 ms；H11 首帧≤100 ms 未过，模型烘焙/管线预热的尖峰仍在。
- **I32 [P2] 单位历史页预算边界**：行环页按可见行跨度与 32 MiB 预算缩 ring；超宽/超长路线可能降低方向平滑（`unitMatches` 回退解析位置，不截断、不隐藏单位）。本轮已修复“列片数按单块 9 列计算导致多数列历史查找失败”的缺陷，全列覆盖仅在 bench 验证。
- **I33 [P2] 余量与未覆盖矩阵**：外观改善后世界 GPU 余量约 0.47–1.17 ms（三轮最差 14.534/15）；E4 换包/关光影/重载/改窗口、GPU 频率/功耗同步采样与本轮 JFR 未完成，环境波动或后台负载可能吃掉余量。普通组 256 尸体已补测（帧均 1.986 ms/P99 4.382 ms，static_drawn 256、fallback 0、draws 1）。
- **I34 [P3] 全精度隐藏面**：520→520（0%），W0 的 15–35% 减面门禁仍未达到；本轮未改全精度几何。
- **I35 [P3] Iris missing program**：region_update、terrain_update、spatial_index、unit_update、motion、iris_vertices、unit_stats 等告警仍存在，未关闭，也未证明无渲染影响。
- **I39 [P1／待完成] 真正的6方向烘焙片与新测试矩阵**：本轮已实现20帧烘焙、第4档“片”盒（24顶点）与GPU按相机绝对距离降帧（<d全帧、d..2d半、2d..3d四分之一、>3d静止），配置 cardDistance/frameDistance 已接入并验证一次运行（世界GPU 12.702ms、展开 785,071）。但“片”目前只是6方向面片盒+逐帧包围盒，**没有离屏把模型从6方向渲染成视图图集**；需要下一步实现真正的方向图集烘焙（含20帧、缓存、着色器按方向/帧取tile），再按用户给的外观与性能目标验收。10项测试矩阵（清道夫30 AI/无AI、清道夫尸体30、随机生物尸体100、500×500无上限事件、同上+10监守者，各光影/非光影且每项重启）尚未实现。
- **I38 [P2／测试环境] Spark 全程采样与并行 GPU 负载**：按用户要求 Spark 在测试开始时开启并覆盖整个浪潮场景（timeout 600，不再 30 秒），测量结束停止并在尸体阶段等待上传链接、`requireSaved` 后才允许关闭客户端；Sodium 保持加载、不为了对比关闭。但同一时段并行 GPU 负载会让世界 GPU 从约 14ms 涨到 23–33ms（client-1792980845712/1792981136050/1792983163376），即使环境 valid 也不能用于达标。应在 GPU 空闲时重跑；本构建仍缺少干净三轮。IDE MCP 会话在关闭原始项目后丢失，运行改由同一 Gradle `verifyBenchClient` 任务执行，属可复现回退。

- **I36 [P2／取证流程] 并行运行干扰**：00:59–01:08 另一 Photon 工作区在同一 GPU/窗口跑基准，期间一轮样本终止丢弃。后续每轮启动前必须确认没有其他 Minecraft 进程/窗口，并在结束时归档上一轮制品与运行 ID。
- **I37 [P2／环境] 焦点与显示刷新**：BenchMod 要求窗口焦点，外部程序抢焦点会让运行判 INCONCLUSIVE（client-1792978004840 的 `client.window.focus_lost`）；现已加每 tick 自动还原/重聚焦、MEASURE 失焦硬门禁与环境字段，但外部抢占仍可能拒绝整轮（client-1792977705014 losses=2），只能重跑不能美化。当前显示器 2560×1600@165Hz 使普通组帧间隔出现约 6.06–6.15 ms 地板，普通组帧间隔不可与早前 3.40 ms 基线直接比较，比较以世界 GPU/draw 等阶段指标为准。
- **清理边界**：隔离 Iris 配置已按备份恢复；本轮临时开启的 `run/client` Iris 配置也已按备份 SHA256 还原；run/client 下本轮创建的多个“新的世界”为临时测试世界，保留未删除用户其他世界；Debug 会话与游戏进程已停止；会话级断点静音已恢复 false，未删除/改动用户既有断点。真实功能矩阵剩余项（区域定量、E4、256 尸体）若要补做，须重新授权并在有命令的隔离世界进行。

## 2026-10-25 实机复测后的当前优先项

普通client-1792939439969、光影client-1792939667496均已实际完成，另经IDEA Debug和19876的Minecraft MCP Mod接口检查隔离新世界。完整数值、旧版对照、JFR方法及证据缺口只见[BenchMod本轮报告](benchmod.md#2026-10-25极端优化实机测试与历史对照)；下列是本页唯一维护的后续问题，生产代码本轮未修改。旧“全部NOT_RUN”仅代表交付时快照，不再代表当前完全未运行。

### H27 [P0／根因已定位、门禁已修] 基准空画面仍判PASSED（2026-10-26 见新节）

两轮10000实例计数、截图文件及捕获归属断言均通过，实际horde-render截图却近乎天空。源码已核对HordeRenderScenario.verify约270行：只检查采样/文件存在和statistics字符串，不检查负载像素；故PASSED不能证明可见性。空画面的具体根因未确认，不能直接说是某个shader或相机错误。Debug新建世界的真实48宽事件可见，排除“所有生产事件完全不显示”的过度结论。

先固定同夹具的实际相机/投影、渲染目标/viewport/深度、单位位置与光照有效性证据，再补可见性或遮挡/像素验证，不能移镜头、关发光/LOD、少画单位凑通过。可见性未恢复前，3.555/15.945ms和较旧版低54.4%～71.7%的数值不能记为有效性能收益，也不能关闭H24。首轮JFR/完整JSONL未及时保全属本轮取证缺口，已保留summary和独立GPU逐帧数据；未来每轮运行前必须确认上一轮非日志制品已归档，不能依赖IDE忽略目录的缓存summary。

### H28 [P1／待验收] 中远档外观与减面/内存门禁未过

实机缓存确为4帧、520/304/176顶点；全精度520→520，W0本模型没有达到15～35%减面门禁。Debug80格视角可见大量块状/四面体代理与近档混排；这是外观风险证据，不是无洞/无跳变或美术接受的证明。基准全部为远档，不能据此关闭近中景性能/画质问题。

三档CPU动画数组合计96000字节；历史同模型单全精14帧为174720字节的计算对照约少45.1%，不能宣称完整系统下降70%。后续须以真实相机路径/同帧截图及实际全缓存字节验证，保持近档全精度和显式开关；不静默减少数量或降低配置帧数。

### H24/H11/H15 本轮补证与剩余边界

- H24：光影完整MEASURE均值15.945/P99 31.673ms，独立世界GPU均值15.578ms；P99≤20、GPU≤15和有效可见负载均未过。阶段和约10.0003ms、独立draw9.9952ms，跨组审计差约0.051%，查询errors0/无漏绘重复，仅证明该统计口径有本轮覆盖。Iris copies2/passes15达到对应数量门槛，但copy/draw约7.6%未达<3%；expand6.513ms、units1.181ms仍是优先测量对象。新旧GPU阶段口径不同，不直接算改进比例。
- H24/兼容：本轮7条Iris Missing program（含新增unit_update/unit_stats）仍未关闭，HordeHeights未使用sampler提示也保留；两轮run_complete后仍有既有指挥家GUI恢复、执行器/保存异常。数据pass正确归属、换包/回退恢复及夜间发光没有完整矩阵验收。
- H11：普通STABILIZE最大帧约1.320秒；首帧≤100ms、冷启动/重载≤150ms仍未过，不能以预热代码存在关单。
- H15：光影GPU归约3756/3759周期完整，错误0、完整样本最大穿透0microblocks、单周期拒绝最多850；3尾样本未知。未完成高速移动/瞬移/多层/地形、画面平滑及连续录像，不把当前零穿透推广到全部情形。32MiB历史预算边界仍保留。
- 光照：普通P95 0.178ms、光影P95 0.959ms，后者未过0.5ms目标；当前2740区段与旧714/624不同。光影JFR仍有updateSection、requiredSections/loaded、集合/装箱和捕获level谓词分配；约1.167GB lambda权重是估计分配，不是泄漏。建议先同夹具测天空光回退和候选循环分配，再决定实现，不直接据profile改生产。
- 尸体：64场景实际普通1draw/光影2draw、static_drawn64/兜底0；光影截图可见8×8布局，支持本适配器路径。256尸体、优化前CPU/分配基线、网络80/180格、选中/阴影/未知模型与重组/200tick清理仍未验证，EX-15/16不标DONE。
- 本轮只各1次，不满足同轮三次±5%，未做新旧同夹具严格A/B。完整玩法、粒子、联机和E4仍保留原问题，不自动扩大本轮测试结论。

## 2026-10-25 极端优化执行边界与异议（代码交付时记录）

- 已确认决定：代码按依赖继续、不等待实机门禁（A）；突发包围优先平滑≤0.75格/tick、多tick退出且不隐藏（B）；尸体保留5区块基础跟踪（B）；不新增尸体重组动画或同步（A）；独立Horde draw elapsed和15%覆盖审计（A）；既有Horde WIP及原5项暂存纳入本次提交、其他系统排除（A）。上述决定均已落实到本批代码，无待答选项阻止当前实施。
- EX-01…17代码已写入，逐项实现/文件/回滚见执行文档第9节；37份Java/GLSL错误诊断全部analyzed，修复两处错误后0错误。23份Java警告审阅69项；主要为借用世界/跨帧资源缓存拥有者提示、GeckoLib Internal、既有空值/注解及未使用项，不能把这些提示都当泄漏或声称零警告。另补查8份原有接入文件0错误。IDEA BenchMod Compile退出0，Data退出0，两份语言新增9组键并更新对应两份缓存，无生成资源删除；JUnit未运行。备份与检查清单：build/agent-backups/20261025-extreme-confirmed/。
- **P1／未验收**：全部实机、GPU硬件编译、E1/E4、光影/无光影A/B、三轮一致性、截图/哈希/首帧及60FPS门禁保持NOT_RUN。游戏、BenchMod Client/Server和Debug仍禁止；本次A不构成测试授权。原始诊断是修改前证据，不能证明新代码性能。
- **P1／EX-13待验证**：稳定编号页、GPU历史/限速/方向阻尼及穿透/拒绝归约已接入，regionClear不再作为隐藏条件。初次出现无历史时使用解析位置；突发包围可多tick退出，不能宣称当tick穿透归零。密集/重叠区域、出口不可达、多楼层/粗地形遗漏及快速移动边界还缺实机证据。每事件GPU历史双纹理预算32MiB；当前可见编号页超过预算会显式报错，未实现无限单位的有界历史分页回退，不能宣称该极端情况仍完整绘制。区域重叠推远超过光照粗覆盖时也可能受既有光照有效性剔除，需要专项检查，不能将删除区域隐藏等同于所有停显问题关闭。
- **P1／EX-04/06/07待验证及实现取舍**：16MiWords双套纹理+VBO约256MiB是自有分配量，非驱动显存峰值；真正异步重叠、copy<3%、passes下降均未测。尝试全帧持有每个展开VBO会随总实例增长并突破预算，因此采用双槽窗口合并base/glow；超过两工作集会分窗口，不描述成全帧只有两个材质pass。工作集缩档/原生回退/5秒恢复不减少配置数量，但GPU故障当帧仍可能不完整，未作硬件保证。
- **P1／Iris兼容未关闭**：锁定1.11.4的9/14字布局、实体ID和管线versionCounter已核对；不支持布局改为原生实例化回退。同active换包代际检测已接入，实际重载/故障恢复待验。此前六条Missing program日志不能由源码断言消失；当前IrisApi没有RAW/none程序入口，不把数据pass硬映射成ENTITIES以消日志，未升级依赖。其他包和全部光影阶段仍待兼容验收。
- **P2／EX-02/08/14待验证**：隐藏面采用不透明闭合证明，透明/刀面保留；中远档闭合代理和默认4帧均有显式配置。减面15～35%、无洞/切档质量、完整三档内存下降70%尚无证据；14→4帧仅同网格全精度缓存理论减少71.4%，不能用它证明三档合计。减少失败回退到原网格会保留数量及外观，但可能达不到性能门禁。
- **P2／EX-15/16待验证**：尸体默认静态、180/32接口、owner状态/revision/资源代际缓存及重复初始化修正已写入；明确支持的普通清道夫尸体使用冻结网格批绘。未知/反向/轮廓/阴影回退仍有原GeckoLib骨骼提交，不能宣称所有类型都无CPU骨骼。80格基础跟踪保留，180仅已收到尸体的客户端上限；不新增尸体重组动画，保留CLEAR1/2/3及服务端进度/复活。64/256夹具已注册但未运行，CPU/分配量收益与网络同步回归未验证。
- **P2／EX-01/11统计与首帧边界**：WORLD/STAGE_SUM/HORDE_DRAW_TOTAL三组不同帧，按共同帧标识检查漏绘/重复，跨组只比较同负载统计；15%用于调查未覆盖命令、阶段间等待或分组失配，不伪补OTHER。draw不含extract预热，3ms是步骤协作预算，解析/驱动编译/上传不可抢占。GPU单位统计只异步回读8字节标量，尾部未完成、关闭/失败仍为null；穿透计数单位microblocks（除1e6为格），拒绝数为次数。新增UNITS/UNIT_STATS及历史复制须一起计成本，不只报motion下降。CPU/tick最新draw与GPU完整row采样粒度不同。
- **提交范围**：已授权Horde源码/基准/配置合并/文档可以一并提交；build.gradle.kts的通用构建号闭包修改、logs、build备份、运行产物及其他系统内容排除。原5项暂存补丁已保存，已按用户A随Horde提交c21899d消耗；H20提交归属问题关闭并移至changes.md，不推送。

## 2026-10-25 Sodium联合诊断：事实、推断、假设与优化建议

本节为当前工作区的只读诊断，不是代码修复。测试代码为HEAD 4a1b511b加既有WIP；864份Java/GLSL/着色器源码指纹前后不变。三次运行均由现有IDEA BenchMod Client完成，权威summary两场景均PASSED、workloadCorrect=true；这只说明既有门禁通过，不代表无兼容日志、无收尾异常或性能达标。Sodium是当轮已加载的渲染引擎；JFR来自JDK，帧时间/GPU查询来自既有BenchMod、PhotonFrameCapture与HordeRenderMetrics，不冒称“Sodium内部GPU计时”。

### 证据目录与口径

归档根：C:/Users/wang_/AppData/Local/Temp/horde-sodium-diagnosis-20261025-6d507349。E1为各组summary.json；E2为artifacts/gpu下的horde-gpu.json、sweeper-gpu.json；E3为artifacts/stages/horde-stages.jsonl；E4为artifacts/jfr原始记录及根目录的对应jfr-analysis.json；E5为*-client.log、*-launch.log；E6为artifacts/screenshots；E7为source-before.xml及配置/Shader ZIP哈希核对。组目录ordinary、iris、iris-clean分别对应普通、首次光影和干净光影复测。

- 普通：client-1792899914313，客户端PID44596，启动2026-10-25 11:44:32.700（北京时间）；Gradle日志BUILD SUCCESSFUL in 2m51s。
- 首次光影：client-1792900642330，PID61880，启动11:56:43.450；BUILD SUCCESSFUL in 2m33s。
- 干净光影复测：client-1792902465754，PID58676，启动12:27:06.552；BUILD SUCCESSFUL in 2m28s。采样期间不执行离线JFR解析或IDE索引。
- 三组均为Minecraft26.1.2/NeoForge26.1.2.100、Sodium0.9.2、Iris1.11.4、JBR25.0.2、RTX3060 Laptop/OpenGL3.3/NVIDIA616.64；2560×1494，seed7平坦隔离世界，视距/模拟距离12，VSync关闭、配置260。本版260表示不限帧；E2全部捕获样本flags=3（聚焦、最大化、非最小化）、限帧原因为NONE。
- 光影日志确认ComplementaryReimagined_r5.9.3，HIGH（用户改动选项0）。结束后核对隔离事件配置纵横间距1.8、regionMargin8；未逐轮保存NBT规则快照，不将当前值作为全部历史参数的完整证明。普通/光影复用隔离场景，不进入run/client用户存档。
- E1帧间隔为完整MEASURE：64实体约31秒、浪潮约60秒。E2的GameRenderer GPU/CPU仅覆盖20/30秒，不含tick、提取、呈现和限帧；不能与60秒均值直接相减，也不能把CPU和GPU相加。[R3]
- E3 CPU为每绘制周期阶段调用之和，prepare包含regions/terrain/spatial等子阶段，禁止再相加。GPU为轮换的一次调用：普通base包含整pass，光影expand/copy/base/glow只采首个工作集，不是整帧阶段总量。
- E4执行/原生采样周期均20ms；按E2首帧UTC和E1 MEASURE时长筛选，边界可能相差一帧。Java与原生样本分别计数；inclusive栈可重叠，不相加当CPU占比。ObjectAllocationSample权重是估计分配量，不是精确对象数、存活量或泄漏量。[R1][R2]

### 已确认事实 F

| 组/场景 | MEASURE帧数 | 间隔均值/P95/P99 ms | 间隔最大 ms | GameRenderer GPU均值 ms（有效样本） | Render CPU均值 ms |
| --- | --- | --- | --- | --- | --- |
| 普通/64真实实体 | 3424 | 9.054 / 14.216 / 29.149 | 256.924 | 3.326（1631） | 8.155 |
| 普通/10000虚拟实例 | 18238 | 3.290 / 4.215 / 4.866 | 40.179 | 2.875（3275） | 1.996 |
| 首次光影/64真实实体 | 2933 | 10.570 / 13.456 / 17.763 | 33.039 | 8.787（917） | 9.093 |
| 首次光影/10000虚拟实例 | 1716 | 34.967 / 36.100 / 36.939 | 81.702 | 34.438（431） | 7.110 |
| 干净光影/64真实实体 | 2128 | 14.571 / 21.722 / 27.044 | 59.589 | 9.897（716） | 11.651 |
| 干净光影/10000虚拟实例 | 1064 | 56.359 / 74.134 / 75.769 | 415.267 | 47.593（209） | 14.954 |

F1：三轮一万实例计数恒定10000，MEASURE记录的Java绘制失败为0，GPU查询errors=0；干净光影阶段查询有1个未完成结果在结束时取消，不当成0耗时。两次光影不能只选34.967ms较好的一次。干净光影的E2前30秒间隔均值48.985ms，而E1全60秒为56.359ms，运行自身也非完全平稳。

F2：E3普通/首次光影/干净光影分别有18221/1714/1063条MEASURE绘制周期，数量与E1不相同属于不同采样入口，不能一一混算。普通/首次光影/干净光影的活动区域数为11/7/11、索引级别3/1/3、光照区段714/714/624；测试并非严格单变量。自然Mob、缓存、窗口跨度及未记录的GPU频率/功耗/其他负载都需要作为限制保留。

| E3一万实例工作量 | 普通 | 首次光影 | 干净光影 |
| --- | --- | --- | --- |
| 逻辑批次均值 | 188.46 | 188.46 | 188.60 |
| pass / draw均值 | 7 / 381.92 | 60.34 / 417.47 | 61.92 / 419.48 |
| 顶点展开数/帧 | 0 | 5200000 | 5200000 |
| 复制次数/帧 | 0 | 18.78 | 18.64 |
| 复制字节/帧均值 | 0 | 291347113 | 291342985 |
| 索引有效字数 | 296088 | 3120 | 284700 |
| 粒子选区/提交数 | 0 / 0 | 0 / 0 | 0 / 0 |

F3：520顶点/模型×10000实例×14字/顶点×4字节=291200000字节，实际复制略高来自行/工作集填充；这是逻辑传输工作量，不是实测PCIe带宽或主机往返。draw计数包含motion、展开和材质等，不能与旧报告“186次主体/发光draw”直接比较。两次光影实际主体+发光约为2×工作集数，约37～38次。

| E3 CPU阶段均值 ms（整周期累计，prepare含子项） | 普通 | 首次光影 | 干净光影 |
| --- | --- | --- | --- |
| extract（最近一次） | 0.085 | 0.097 | 0.132 |
| prepare | 0.146 | 0.138 | 0.285 |
| light | 0.212 | 0.609 | 1.029 |
| motion | 0.446 | 0.472 | 0.881 |
| expand / copy | 不适用 | 0.662 / 0.978 | 1.157 / 1.598 |
| base / glow | 0.319 / 合并在base | 0.977 / 0.587 | 1.636 / 1.077 |

F4：GPU单次阶段样本如下，不能逐项相加、乘工作集数后当作已测的整帧分摊。

| E3 GPU阶段一次调用均值 ms（有效样本） | 普通 | 首次光影 | 干净光影 |
| --- | --- | --- | --- |
| spatial（本轮各自所有提交级别） | 0.146（1691） | 0.064（142） | 0.194（81） |
| motion（一个group） | 0.768（1735） | 0.841（143） | 0.922（115） |
| expand（光影首工作集） | 不适用 | 1.049（142） | 1.725（81） |
| copy（光影首工作集） | 不适用 | 0.146（143） | 0.370（88） |
| base | 1.447（1731，整pass） | 0.227（143，首工作集） | 0.210（117，首工作集） |
| glow | 合并在base | 0.066（143） | 0.072（79） |

干净光影的首工作集约29.15万展开顶点、复制约16.37MB，不代表每个工作集等量。Light CPU并非光影专属分支：低FPS时更多绘制帧跨逻辑tick，loaded列查询均值普通24.78/首次光影268.00/复测308.62每帧，不能将每帧light升高直接解释为Iris使同一代码慢了五倍。

F5：E4 Render thread MEASURE样本：普通浪潮Java614/原生409，首次光影176/631，干净光影197/580。普通Java样本中365条包含Horde、22条包含Sodium；后两组包含Horde的Java样本为71/89、Sodium为12/16。它们是inclusive计数而非独占CPU时间，不能据此称“Sodium占比很低所以可以移除”。

F6：原生热栈解开匿名MethodHandle后为glfwSwapBuffers → GlDevice.presentFrame → RenderSystem.flipFrame → Minecraft.renderFrame。普通157/409、首次光影533/631、干净光影492/580条同类原生样本落在该链。首次光影nglReadPixels仅11条、干净光影10条；并非原生样本的大多数。

F7：确有HordeGpuRenderer.IrisVertices.draw:L646 → CommandEncoder.copyTextureToBuffer → GlCommandEncoder.copyTextureToBuffer:L269 → glReadPixels调用。当前26.1.2后端先绑定GL_PIXEL_PACK_BUFFER，随后缓冲作为顶点输入；本路径没有应用CPU map。仅凭glReadPixels不能证明PCIe往返、主机内存驻留或固定同步次数，驱动驻留和等待需要专门GPU时间线证据。[R4]

F8：光照几何缓存MEASURE期light_coverage_builds均0，说明候选几何复用生效；但requiredSections:L137仍创建Section并插入HashSet。JFR该最靠近叶子的Horde分配栈估计权重：普通995MB、首次光影705MB、复测469MB（十进制MB）；这是各自窗口分配估计，不是精确泄漏。普通Section类估计948MB，GlRenderPass.TextureViewAndSampler约1033MB，GpuBufferSlice约277MB。

F9：64真实实体控制场景与虚拟浪潮不同。Java执行样本包含GeckoLib的数量普通448/691、首次光影671/894、复测618/881；普通叶热点有Vector3f.mul/矩阵旋转，光影叶热点有BufferBuilder.beginElement、VertexConsumer.addVertex、NormalHelper.computeFaceNormalManual/computeTangent。首次光影Vector4f分配估计权重6.93GB；这不属于虚拟GPU帧缓存的切线计算，不能混为同一瓶颈。

F10：普通浪潮稳定阶段最大帧1.140s，首周期prepare CPU312ms（包含regions79.6、terrain24.4、spatial137.2）、motion716ms；稳定前原生样本出现nglGetProgramiv。光影iris_prepare全程只调用1次、准备72800字，MEASURE期该阶段调用/字数均0。缓存确实一次准备，但首次CPU停顿不能全部归因模型烘焙，GPU硬件编译/创建也需拆分。

F11：GC暂停在浪潮MEASURE窗口分别为21次/146.46ms（最大28.43ms）、7次/66.88ms（最大34.40ms）、10次/74.52ms（最大20.59ms）。有暂停事实，但未逐帧对齐全部峰值，不将415ms最大帧直接归因GC，也不以GC总时长解释持续35～56ms。

F12：两次光影均在MEASURE前记录6个Missing program：horde_iris_frames、horde_region_update、horde_terrain_update、horde_spatial_index、horde_motion、horde_iris_vertices。Iris说明不是critical，但可能有渲染异常；截图浪潮可见和failures=0不能关闭兼容问题。三轮收尾仍有ConductorControls返回GUI异常，光影另有ConsecutiveExecutor/保存区块失败，发生于run_complete后，与测量和退出生命周期分开。Spark在Windows降级为built-in Java，不把它当async-profiler原生事件。[R5]

F13：已看两组截图，浪潮可见，但画面大量天空、只部分单位在可见区域；提交10000不等于可见10000。粒子计数均0，当前高处相机与24格粒子范围没有覆盖真实粒子负载；未做移动生物轨迹、三格净空、楼层、粒子或多玩家动态验收。

### 证据支持的推断 I（不是已确认唯一根因）

- I1／较强：光影慢帧主要处在GPU执行/呈现链，而不是纯Java算力或一个CPU light循环。依据E2 GPU时间显著上升、E4 Swap链密集、E3全量展开工作量；仍不能将Swap所有等待都等同某个shader，CPU/GPU不同窗口不相加。[R3]
- I2／较强：Iris全量展开路径是优先优化对象。每帧520万顶点、约7280万数据字输出与约291MB复制，加上首工作集GPU展开1.05～1.73ms，比只看CPU copy约1～1.6ms更有解释力；但没有全工作集统一GPU分摊，不能宣布“复制占全部耗时”。
- I3／较强：光照候选包装、集合与重复绑定仍有CPU/分配优化空间。源码路径、JFR权重及light/提交阶段CPU共同支持；几何缓存已生效但没有消除Section/HashMap对象。
- I4／较强：64真实实体的GeckoLib/Iris顶点写入是另一条CPU压力路径，不能用一万虚拟实例成绩替代真实参战实体性能。
- I5／中等：首轮prepare/motion大停顿含GPU程序/资源准备的可能性较高；JFR初始化栈、GetProgramiv和一次缓存指标支持，但缺独立冷启动重复，不分摊为确定百分比。
- I6／中等：普通约188个逻辑批次产生约382个总draw，CPU提交与描述对象重复工作可能值得批处理；本轮未实现或测量收益，drawMultipleIndexed的存在不保证少发GL调用。

### 待验证假设 H

H1：GPU频率、功耗、温度或外部竞争导致两轮光影明显差异。本轮没有同步记录这些量，不能把干净复测较慢归因某一项；自然区域11/7/11、区段714/714/624、缓存与世界状态也不完全一致。

H2：驱动/DWM呈现节奏或资源读写依赖增加Swap等待。VSync关闭/限帧NONE不能独自排除驱动或系统呈现控制；需要GPU时间线，不能凭CPU样本断言。

H3：单纹理/单VBO工作集复用导致GPU序列化，双缓冲可能有益。copy一次调用GPU计时并不等于传输本体带宽；尚无受控缓冲策略对比。[R4]

H4：三级索引存在大量空细档/Y空层成本。CPU全局count>8即允许细档，而GPU逐单元再判密度；DOMAIN_PADDING=128同时用于XYZ，实际命中/非空单元未记录。spatial_words不是命中数，不能从11区域3级就宣布细分收益或浪费比例。

H5：六个Iris覆盖警告可能只是数据pass未映射，也可能影响状态恢复；日志不证明它们导致35/56ms，更不能仅因截图可见就忽略。

H6：GC/JIT/分配、Spark agent或其他后台活动解释个别峰值。本轮只确认事件存在，缺完整逐帧因果链。

H7：实际无限上限、多事件/密集目标/低位粒子和服务器负载可能比本夹具更重。默认无限路径不执行MAX_VISIBLE排序，而Bench强制10000并触发排序；不能把extract排序热点泛化为默认生产路径。

### 优化建议与对应代码（本轮全部未实施）

| 优先级/已有问题 | 建议与证据 | 对应代码 | 后续验收条件/风险 |
| --- | --- | --- | --- |
| **P0 H24** | **2026-10-25 决策：极端目标改为 60 FPS**。按[极端情况优化方案](horde-extreme-optimization-plan.md)与[GPT 执行文档](horde-extreme-execution-plan.md)实施 W1 去串行、W2 expand 预计算、W3/W4 重叠与合并、W0 静态减面、W9 距离质量档 | [HordeGpuRenderer](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)、[展开 shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh)、[HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) | E1：10,000 实例 + Iris 60 FPS（帧均 ≤16.7 ms、P99 ≤20 ms、GPU ≤15 ms），可见数量仍 10,000；允许 128–256 MiB 工作集；允许自动降级并记录；60 FPS 不可达时停止上报 |
| **P1 H24** | 先固定测量：整帧阶段求和 + GPU 时钟/功率/温度 + HEAD/dirty 写入 artifact（F3/F4） | [HordeRenderMetrics](../../src/main/java/org/unitego/lobecorp/client/horde/HordeRenderMetrics.java)、[HordeRenderScenario](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java) | 同轮三次帧间隔 ±5%；整帧求和与世界 GPU 差 ≤15% |
| **P2 H07/H11** | 执行文档 EX-10/EX-11：光照区段打包与首帧预编译/分阶段烘焙 | [HordeLightField](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java)、[HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) | light CPU P95 ≤0.5 ms；首可见帧 ≤100 ms |
| P0 H16 | 先固定世界/目标、功耗频率及外部GPU负载，按冷/热、控制/浪潮分别复测；保留全部波动结果（F2/H1） | [HordeRenderScenario](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java)、[Bench配置](../../build.gradle.kts) | 不改门禁制造通过；报告场景差异、时段和样本口径，不只选最快结果 |
| P1 H24 | 降低Iris每帧全量数据展开；研究GPU可见性筛选/压实、模板属性复用或受支持的实例化接口（F3/F4/I2） | [IrisVertices.draw](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) L608–704；[展开shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh) | 只剔除确定无贡献单位，不减少可见数量（历史约束；2026-10-25 决策允许 W0/W9，见上） |
| P2 H24 | 测试有界双缓冲/批次依赖，区分执行、复制、材质与呈现，不先承诺带宽收益（H2/H3） | 同一draw L646复制、L700材质绘制 | 内存预算显式记录；同工作量GPU/CPU与时间线下降，不能把逻辑字节当PCIe数据 |
| P2 H07 | packed SectionPos primitive集合去重，缓存必要集合/排序/槽位不变部分，避免每tick重复Section包装（F8/I3） | [HordeLightField](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java) L68–137、L161–165 | 保留逐tick加载失效、5tick光照和天空光回退；比较每秒分配/时间，不能只比较不同FPS的每帧均值 |
| P2 H07/H10 | 相同Geometry的纹理/VBO绑定去重、复用稳定buffer slice/描述数据（F8/I6） | [drawGroup](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) L240–252、[drawMotion](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) L432–455 | 跨材质、资源代际和缓冲扩容正确；JFR TextureViewAndSampler/GpuBufferSlice及CPU阶段有可重复下降 |
| P2 H15 | 补GPU非空细档/查询命中/完整回退指标，再研究稀疏索引或更紧Y域（H4） | [HordeSpatialIndex](../../src/main/java/org/unitego/lobecorp/client/horde/HordeSpatialIndex.java) L29–30、L173–192；[索引shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_spatial_index.fsh) | 不能盲缩128或截断候选；保留平滑旧状态、瞬移、负坐标和域外完整回退 |
| P2 H11 | 在正确GPU上下文预热已知管线/资源，分开记录模型烘焙与程序创建（F10/I5） | [prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) L263–298；本地GpuDevice.precompilePipeline | 当前API确有precompilePipeline；不能把GPU调用丢到任意CPU线程，也不能只将首帧停顿移成加载期卡顿不记录 |
| P2 H14/H24 | 真实实体路径优先减少重复变换/临时向量和Iris顶点写入工作（F9/I4） | [SweeperRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/ordeal/indigo/renderer/SweeperRenderer.java)、GeckoLib/NormalHelper当前版本代码 | 属跨框架路径，先划职责；不共享可变骨骼状态、不通过降动画率/数量掩盖 |
| P2 H24.4/H23 | 核对数据管线映射和退出时GUI/执行器时序（F12/H5） | [GPU管线定义](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)、[ConductorControls](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorControls.java) | 把兼容日志与退出缺陷单列；不靠吞异常或忽略保存失败通过 |
| P3 H10 | 运动5个字导致location约6次求值，研究受支持共享求值方案（源码事实） | [motion fsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh) L19–27、[location](../../src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl) | 本地TextureFormat没有浮点颜色附件，RenderPass只有现有draw/drawMultipleIndexed等入口；不可直接假定RGBA32F/MRT/间接绘制可用，不能转CPU逐单位变换 |

所有建议均需后续明确授权实施。2026-10-25 用户已确认：极端目标 **60 FPS**；允许 Iris 工作集 **128–256 MiB**；允许极端情况自动缩小工作集/回退实例化并记录；H15 **维持 3 格净空**；据此允许 W0 静态减面与 W9 距离质量档（必须显式配置、可见数量不变、A/B 与夜间发光通过）。其余光照、发光、20 FPS 动画、数量与玩法保留；本轮不修改代码、不安装或升级 Profiler/Sodium/Iris，不将假设写成已确认根因。

### 2026-10-25 追加需求（分割/4 帧/尸体/512 宽夹具）

用户在 DSH 问答中新增并确认以下需求；实现任务见[GPT 执行文档](horde-extreme-execution-plan.md) EX-13…EX-17，方案见[极端情况优化方案](horde-extreme-optimization-plan.md) §10。

| # | 需求 | 验收/约束 | 任务 |
| --- | --- | --- | --- |
| 1 | **分割区域不可进入**：虚拟单位左右移动（随机横摆/漂移）不得进入分割区域，更不能“进入后消失” | 逐帧断言无单位进入原始区域（含 radius）；alpha 恒 1.0，禁止用隐藏/软淡出规避；单 tick 位移 ≤0.75 格；正常让行/平滑/高度隔离回归 | EX-13 |
| 2 | **动画改为 4 帧**：在原始动画中等距平均取 4 帧 | 默认 4 帧、配置可回退（4–20）；动画纹理与 Iris 帧缓存内存 −70% 以上；循环无闪烁/T-pose；文档“20 FPS 采样”改为“4 帧烘焙” | EX-14 |
| 3 | **尸体渲染**：实体死亡归实体自身逻辑；生成的尸体默认静态、无动画 | 在 `EntityCorpse` 提供可覆写方法 `renderDistance()`（默认 180，复杂/动画 32）、`hasCorpseAnimation()`（默认 false）、`isComplexModel()`（默认 false）；注册/覆写可启用尸体专用动画；重组时临时动画；冻结姿态、去每帧分配与实体副作用、批渲染、同步事件驱动；64 尸体 CPU −40%、分配 −50% | EX-16 → EX-15 |
| 4 | **后续性能夹具**：宽度 512、直线、x≈+160 到 x≈−160 | 绘制数量跟随配置（默认 10,000），不得写死/截断/静默减少；作为 E1 唯一验收夹具；同轮 3 次帧间隔 ±5% | EX-17 |
| 5 | **60 FPS 取舍**：允许降低精度/顶点（LOD） | 但**绘制数量必须等于配置值**；近景全精度；不得关闭发光/降低动画帧数 | EX-08 |

> 这些需求覆盖此前的相反表述：H21 的“20 FPS 动画采样未改”、以及 E1 的“可见 10,000 固定”；以本节为准。

### 参考文献与解释边界

- R1：[Oracle JDK25 JFR性能排查](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)：采样、分配和GC分析应结合观测窗口；样本不是精确独占CPU时间。
- R2：[RecordingFile消费API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html)、[jfr工具手册](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html)：本轮使用公开消费API流式读取，不依赖巨大JSON展开。解析工具位于任务临时目录，运行JDK25.0.1；游戏记录来自JBR25.0.2。
- R3：[Khronos ARB_timer_query](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt)：GPU异步时间查询与CPU计时语义不同，不能混加；一次区间样本不自动代表全帧。
- R4：[Khronos ARB_pixel_buffer_object](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_pixel_buffer_object.txt)：缓冲可以用于像素与顶点数据，规范提供render-to-vertex-array用途；不能据API名字断言物理驻留和主机往返。
- R5：[Sodium官方仓库](https://github.com/CaffeineMC/sodium)：说明其渲染引擎定位；本轮版本以E1/E5实际加载0.9.2为准，不用上游当前分支代替本地二进制。
- R6：IDEA解析的本地Minecraft26.1.2.100 GpuDevice/RenderPass/TextureFormat及GlCommandEncoder:L258–278，Iris1.11.4本地JAR；本地后端确认PBO绑定/readPixels与fence路径，不能用别版API举例当成已支持。

## 本次追加需求与证据级别

2026-10-24用户补充：分割必须像水一样平滑、连续；单个生物范围缩小，楼上/高空生物不在地面制造缺口；间距更疏，左右移动更大且更随机；保留基础速度，只随机加减速；单目标真实参战上限10；事件生成的真实清道夫不参与分割。本次明确不启动游戏，以下代码变更尚无本次实机验收。

当前写入：默认纵横间距1.8格；GPU个体相位、频率与幅度变化，纵向扰动最大1.8格、左右最大1.5格，基础速度来源不变；单体AABB水平外扩至少3格，较大体型保留三倍余量、Y保持实际高度，再按1格外取整，不再使用战斗余量8格或8/16/32格扩张实际缺口；渲染CPU提交六整数目标和稳定身份，GPU持久保存中心/半尺寸/速度并进行阻尼更新、软方向和多区域连续上包络；按目标Y区间与当前地面到实体高度区间判断，让高处目标影响归零。事件成员继续复用已有同步附件排除，不把全部普通清道夫排除。

“上限10”按每目标有效真实参战成员与预约合计解释：初始、死亡增援、定时增援、预约、转派均受限制；旧超额成员加载后按既有淡出流程退出，不瞬间销毁。新建事件默认从1逐步增援至10；旧事件已有快照不强制改写为新间距或增援规则。该解释及旧数据边界保留记录，不主动询问。

证据标记：**实测缺陷**有运行报告；**源码风险**仅确认实现路径；**待验收**表示尚无相应运行证据；**人工边界**表示已有取舍。前轮1.2格、旧流场数据仅作历史对照，不能当作本轮性能结果。下列验收标准是待执行清单，不是已运行结果。

| 优先级 | 问题 | 当前证据与影响 |
| --- | --- | --- |
| P1 | H24 Iris大负载GPU转接过慢 | 2026-10-25光影帧间隔35.0/56.4ms、GPU34.4/47.6ms；运行通过但波动与瓶颈仍在 |
| P1 | H15 连续分割、小范围与高度隔离 | 三级索引、同源地形平滑及3格净空已写入；连续性与高度隔离待实机 |
| P2 | H11 首次模型准备卡顿 | 同步烘焙仍在首次绘制准备路径，历史稳定阶段最大555ms |
| P2 | H07、H16 每tick扫描及完整性能证据 | 客户端固定场景已重复采样，真实多事件/多玩家服务器压力未覆盖 |
| P2 | H14 玩法、持久化与联机链路 | 一tick清理已实测，完整配额/增援/转派/重连仍未验收 |
| P2 | H26 事件尸体200tick与清扫粒子 | 来源期限及客户端潮内多区分级粒子已写入；动态验收未执行 |
| P2 | H25 独立服务端基准启动受阻 | 客户端依赖ReRetro误载，缺少MultiBufferSource；无server测量结果 |
| P2 | H23 指挥家退出异常 | 两轮新基准收尾再次出现鼠标恢复异常，Horde资源已提前清理 |
| P2 | H03 同域规则归属 | 保留首次建档规则，未擅自改变共享配额玩法 |
| P3 | H10、H19 图形API与内部依赖 | RGBA8重复求值、GeckoLib内部入口及公开协议保留字段 |
| P3 | H17、H20 历史证据及提交归属 | 缺失旧快照不伪造恢复；混合源码不能连带提交用户改动 |
| 人工边界 | H21 需求取舍 | 保留原有资源准备、出生预算及旧事件快照；不主动提问 |

## 2026-10-24追加优化：代码与无游戏检查完成

- H15：GPU生成32格粗候选位图，密度超过8再细分16/8格；六整数实际区域不扩大到分档格。每级最多16MiB、三档最多约48MiB，位图包含全部活跃区域；容量不足退回粗档，域外或粗档不足时完整查询，不截断候选或减少浪潮。CPU只在目标版本/活跃数变化时准备整数域与容量，不构建候选表。
- H15：地形样本高度/速度及绕行位移/速度常驻GPU，与分割共享hordeGapPush/hordeDamped。包围盒水平外扩至少3格，保留较大体型所需三倍余量；GPU再加完整动画的模型半径，并用原始区域安全遮罩避免过渡期穿入净空。Y不扩大，避免高处目标投影到地面。突发目标允许局部隐藏，平滑观感仍未验收。
- H15：同层、非退场、靠近既有目标的事件成员并入该事件战斗包围，不单独开缺口；始终以原目标种子判断距离，防止成员链式把包围扩到整片潮。服务器战斗保护/清理规则未改，完整联机仍属H14。
- 整潮抖动/消失：公共时钟接收header时连续校正，最大校正速率0.25tick/tick、外推上界40tick；GPU异常不再全局clear或等待数秒，只限流日志、保留有效资源/历史并下一帧重试。源码确认这些旧路径可能造成跳变/停显，但没有实机确认用户现象的唯一根因；GeckoLib缓存getter返回稳定引用，未将它误判为每帧换代。
- H24：在GPU按资源代际一次生成Iris位置/法线/切线帧缓存，每顶点每动画帧10字，逐实例采样与旋转；没有新增CPU切线或顶点处理。沿用既有工作集合批、中心UV和材质合并，保留数量、LOD、发光、20FPS动画与原资源。复制总字节、驱动同步成本和整体帧率没有新实测，H24不关闭。
- H26：原版环境粒子只在客户端发射，默认每tick尝试近3/中2/远1个不同区域，选区格8/16/32、距离带0～8/8～16/16～32，同时受默认24格particleRange限制。每区烟雾/碎屑/刀光为2/4/2、1/2/1、1/1/1，清扫提示最多另补1区；从实际提交的潮内行列随机取点，检查头尾、加载、视锥、遮挡和目标/模型安全包围，每粒再按最终出生坐标检查距离/视锥和准确相机射线，避免烟雾高度偏移越界。原版引擎仍管理粒子生命周期，不宣称全GPU模拟，不改变用户粒子设置。
- 统计增加不随帧/捕获重置的累计失败序号，基准在稳定/预热/测量/验证阶段检查，避免瞬时失败被下一帧清零掩盖；10项统计回归包含跨帧序号保留，真实基准未运行。统计扩展terrain/spatial/iris_prepare，记录spatial_words、spatial_levels、iris_frame_words及最近tick的粒子选区/提交量。GPU轮换样本仍非全帧总和，忙时缺测为null；索引字数不是命中次数，粒子逐帧值不能累计为独立出生次数。
- 实际检查：13份Java、9份GLSL错误诊断均完成且0错误；必要引用核对为updateGaps 1处、includeCombat 4处、HordeIrisAnimation 7处，未遗留旧签名。编译通过，新空间/时钟/间距/帧容量9项、区域4项、既有缓存/统计10项共23/23通过。Data退出0，23份实际资源written=0，29份含缓存文件SHA256不变。GPU拥有者误报按精确缓存归属处理；HordeClient既有2项空值提示保留。
- 用户随后取消游戏测试：没有启动、重启、接管或停止游戏，没有MC MCP、Debug或BenchMod Client/Server运行；没有本轮光影/无光影FPS、GPU执行时间、画面或抖动验收，历史数据只保留原含义。
- 备份与审阅：C:/Users/wang_/AppData/Local/Temp/horde-spatial-smooth-20261024-124157-e4bdef48，含开始快照、旧原始基准归档、生成哈希、编译/Data日志与checks.json。原5项暂存raw对象未变，源码混合WIP按H20不整份提交；仅独立进度文档可提交，不自动推送。

## DS报告复核（2026-10-24，只读核对，不重跑）

原报告保留为[测试前诊断](horde-pretest-optimization.md)、[测试后报告](horde-posttest-optimization.md)，它们是特定代码快照的证据，不作为另一份长期问题清单，也不把报告内实施建议视为用户新增授权。

- 已核对现存原始summary：client-1792810299088，光影轮整体FAILED；64真实实体PASSED，均值11.052ms；10000实例夹具TIMED_OUT，failure为Phase STABILIZE exceeded 2400 ticks，workloadCorrect=false，未进入MEASURE。2065个STABILIZE帧均值57.897ms、P95 93.565ms，不能当作正式测量或可见性通过。客户端日志确认实际加载ComplementaryReimagined_r5.9.3。
- DS最初无光影轮与追加光影轮不是同一次运行；当前raw-results已对应光影轮，不能把现存文件再次当成无光影原始产物。报告记录并发编辑与负载变化，因此跨轮差值不是单变量因果证据，不能直接宣称代码回退2.7倍。
- H24机制校正：本地GlCommandEncoder确实绑定GL_PIXEL_PACK_BUFFER并调用glReadPixels，随后将同一个缓冲作为顶点输入；当前创建usage没有CPU映射位，BufferStorage不会为它建立持久CPU映射。由此只能确认驱动管理的像素到缓冲路径及资源依赖，不能证明必经主机内存、再次上传或发生固定次数的CPU同步。
- [Khronos ARB_pixel_buffer_object规范](https://raw.githubusercontent.com/KhronosGroup/OpenGL-Registry/main/extensions/ARB/ARB_pixel_buffer_object.txt)明确给出渲染结果经glReadPixels写入缓冲后直接作为顶点源的用途，实际驻留位置由实现决定。DS“584MB PCIe往返＋93次固定延迟解释全部耗时”是未证实模型；此前将物理传输保证为纯GPU复制也过于绝对，准确边界是“应用未CPU映射/取数，驱动驻留及同步成本需测”。逐批复用同一附件/缓冲仍有依赖与性能风险，H24.1分阶段计量仍优先。
- H15部分条目过时或读法有误：当前已是体型3倍宽深，非0.5格余量；updateGaps明确含!previousRegions.equals(regions)分支，不能说没有旧端点收敛触发；当前种子回绕为1024行，不是512行。整表两端点、一tick过渡、零尺寸端点位置及反向流场抵消仍是合理的待优化/待验收项，不因这些校正关闭H15。
- H15建议不能直接照搬：CPU预混合区域违背既定GPU处理约束；只把过渡时长拉长、只去偏置、只求和后饱和，均不能单独证明持续运动、稳定分流和不穿透。须同时设计GPU历史/区域身份、连续约束与验收曲线；不擅自降低LOD、数量、发光或改规则择主。
- H07新增优先候选：源码确认HordeLightField.prepare每次构造distinct块列表，并在逻辑刻或块集合变化时重建requiredSections；后者逐区段创建Section，槽位/排序/更新仍有全量遍历。DS的JFR热点与此路径吻合，支持优先测量缓存/分配；具体百分比和“翻倍完全由它造成”未在本轮重新计算，不能作为已闭合因果。
- 已核对Minecraft源码：只有framerateLimit<260才执行帧率限制，本版260表示不限帧。DS对此校正成立；旧“上限260”表述不能解释历史高于260的计数。CPU提交、GPU执行和帧间隔有重叠及不同窗口，不能简单相加或相减来分摊成本。
- DS本轮未成功启动的规则测试不覆盖或推翻此前已记录的其他测试运行；源码中HordeParticipant与HordeRules的已有直接测试也使“全部零JUnit”表述过宽。HordeRuntime/存档/同步完整集成覆盖仍不足，继续保留H14。
- 本轮未改代码、未启动游戏、未构建或重跑测试。两份报告及当前仍存在的光影raw-results、专服bundle已归档至C:/Users/wang_/AppData/Local/Temp/horde-ds-review-20261024-d2f64f03；覆盖前共20文件约26.3MB。该归档不代表恢复了已被后续运行覆盖的无光影原始产物。

建议执行次序：先将batches/draws/gpuCopy/区域数与各阶段计时落盘，再在固定快照下处理光照集合缓存、GPU连续分割和Iris批次依赖；H23退出与H25类路径单独修复和验收。所有未完成项仍按原问题编号维护。

## H24 [P1／部分修复] Iris可见性已恢复，大负载性能仍不合格

根因一：自定义horde_gpu管线没有Iris覆盖，日志明确Missing program。根因二：Iris在世界渲染期间将ENTITY从36字节扩展为56字节，旧转接按36字节写入，造成后续顶点属性错位。

当前按实际管线选择9或14个32位字；扩展布局补中性实体标识、面中心UV及切线，均在GPU生成。RGBA8附件经CommandEncoder.copyTextureToBuffer直接复制至GPU顶点缓冲，再使用已有Iris映射的ENTITY_CUTOUT/EYES；无CPU映射、逐单位运动或OpenGL旁路。100及10000实例已实际可见，夜间虚拟遮罩和真实清道夫发光已取证。首次GLSL导入格式与保留词错误也已修正。

剩余严重问题：RTX3060 Laptop、2560×1494、Iris1.11.4/ComplementaryReimagined_r5.9.3、前台最大化、Vsync开启/上限120、限帧原因NONE；10000实例30秒442帧，间隔均值67.98ms/P95 85.74ms，GPU均值67.89ms，约14.71FPS；每帧GPU复制292192256字节、186次主体/发光绘制。不能以100个负载约115FPS或数量计数证明大负载合格。单工作集约32MiB只限制工作集，不自动减量。

2026-10-25已取得三轮联合诊断，完整事实/推断/假设见本页顶部；光影仍每帧约520万展开顶点和291MB复制，不能用旧14.71FPS替代当前数字。下一步按证据评估展开/复制及重复管线工作；当前26.1.2图形API无浮点颜色附件，不能未经设计改用CPU逐单位变换、低精度位置、LOD或关闭发光掩盖问题。原始数据与截图在本轮备份iris-layout56-10000.json及iris-layout56-*.png。任意光影包、阴影和透明排序仍未验收。

### H24 详细优化任务

| 编号 | 代码位置／证据 | 需要优化或补证 | 完成条件 |
| --- | --- | --- | --- |
| H24.1 | [IrisVertices.draw](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)；前轮292192256字节/帧 | 2026-10-25已有轮换GPU单次样本及整帧捕获；仍缺全工作集统一分摊，需确认展开/复制/主体/发光占比，不能把旧67.89ms全部归因复制 | 原始分阶段GPU计时、总帧时间及环境能互相核对；无计时查询错误 |
| H24.2 | 同上；186次主体/发光绘制 | 工作集合批与连续材质合并已写入，256小批限制已取消；仍需实测展开、复制与提交成本 | 同实例数/模型/贴图/发光下，展开量、复制字节或提交成本有可重复下降，未漏画或减量 |
| H24.3 | [horde_iris_vertices.fsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh) | 中心UV缓存、GPU一次位置/法线/切线帧缓存已写入；2026-10-25确认单次帧缓存准备和稳态零调用；实际收益仍缺同条件前后对照 | 同一动画帧、法线、UV和发光外观保持一致；资源重载后正确；三轮性能记录可比较 |
| H24.4 | [HordeIrisShadows](../../src/main/java/org/unitego/lobecorp/client/horde/HordeIrisShadows.java) | 复核光影开关/重载时36与56字节布局切换、输出目标及深度方向 | 无错位、透明幽灵、残留顶点或资源泄漏；Iris关闭也保持正确 |
| H24.5 | 前轮仅一个光影包可见性证据 | 补不同视角、夜间、遮挡、更多包及版本的兼容矩阵；阴影仍未实现，不包装为已支持 | 支持范围有截图与版本记录，未支持范围明确；GPU预算另行记录，14.71FPS不能只因可见而关闭问题 |

## H15 [P1／代码已实现待实机] 三级索引、连续分割与地形绕障

当前GPU持久保存区域中心/半尺寸/速度，地形高度与绕行也有持续状态；两者共享临界阻尼和连续排斥函数，不在CPU求逐单位插值。水平净空取体型三倍余量与3格的较大值，再计入模型半径；Y保持真实层次。事件成员不单独分割，只能扩展已有、同层且近处的目标战斗包围。以上为源码状态和无游戏回归证据，不代表用户画面已验收。

剩余为GPU/画面验证及粗高度局限：薄墙、屋檐、多层拓扑仍不能完整表达；64格位移上界和旧事件原采样范围保留。新目标/瞬移的原始区域安全遮罩可能局部隐藏，不能把这个净空优先取舍描述为无闪动。用户取消实机后，不关闭这些待验收项。

| 编号 | 当前入口 | 已写入与仍须验证 |
| --- | --- | --- |
| H15.1 | [RegionBox.forEntity/includeCombat](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) | 至少3格水平外扩、保留较大体型三倍余量、Y不扩大；近处同层成员并入已有战斗包围。规则回归通过，动态移动/不同体型观感待验收 |
| H15.2 | [共享流场](../../src/main/resources/assets/lobecorp/shaders/include/horde_flow.glsl)、[location](../../src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl) | 同侧连续上包络、前后切线朝向；横穿、反向、重叠目标交接与加速度连续性仍需实机 |
| H15.3 | [HordeRegionState](../../src/main/java/org/unitego/lobecorp/client/horde/HordeRegionState.java) | 六整数目标+稳定代次，GPU持久阻尼/速度、退场40tick和重入身份保留；身份/复用回归通过，30/60/144FPS硬件验收未执行 |
| H15.4 | 同一GLSL与[HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java) | 真实目标Y与当前地面到实体高度判断；5/16/32格高处不误分割、地形更新后的层次隔离仍需画面 |
| H15.5 | [isGapTarget/HordeParticipant](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) | 事件成员不单独创建缺口，仅参与其已有目标战斗包围；首次追踪、重进范围、重载和普通来源区别仍待联机 |
| H15.6 | [HordeTerrainState](../../src/main/java/org/unitego/lobecorp/client/horde/HordeTerrainState.java)、[地形shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_terrain_update.fsh) | 与分割相同的GPU阻尼和排斥，地形高度/绕行持续更新；粗采样不能承诺薄墙、屋檐、坑洞、窄道与多层精确碰撞 |
| H15.7 | movingLocation与[采样覆盖](../../src/main/java/org/unitego/lobecorp/util/horde/HordeSamplingUtil.java) | 间距1.8、横摆最大1.5、纵向扰动1.8，基础速度和20FPS动画不变；批次/视角变化后的随机身份稳定性待验收 |
| H15.8 | [HordeSpatialIndex](../../src/main/java/org/unitego/lobecorp/client/horde/HordeSpatialIndex.java) | GPU 32/16/8位图与密度细分，容量/域外完整回退；容量、远坐标、部分字回归通过，GPU候选完整性和实际加速仍未验收 |
| H15.9 | [HordeRenderClock](../../src/main/java/org/unitego/lobecorp/client/horde/HordeRenderClock.java)、GPU绘制异常路径 | 时钟接续/单调回归通过，异常保留缓存并下一帧重试；整潮抖动/停显真实触发条件及恢复画面仍待验证 |

## H11 [P2／未解决] 首次模型烘焙仍可能卡顿

HordeGpuRenderer.prepare/geometry同步调用HordeMeshCache.prepare，首次解析、完整帧烘焙与上传仍位于绘制准备路径。基线稳定阶段最大帧555ms；当前两轮固定场景稳定阶段最大224/162ms，不能证明首次卡顿已消除。

后台线程会触及共享GeoBone/BoneSnapshot临时挂载；挪到tick也不能消除同步工作。需设计资源预准备或分阶段上传，并保持既有动画/模型和资源代际语义，独立记录首次显示与重载耗时。

### H11 详细优化任务

| 编号 | 代码位置 | 需要优化或补证 | 完成条件 |
| --- | --- | --- | --- |
| H11.1 | [HordeMeshCache.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) | 分开记录资源解析、骨骼采样、帧数组分配和上传；不能直接认定历史555ms全是烘焙 | 冷启动、首个事件、第二事件、重载各有阶段耗时和最大帧 |
| H11.2 | [HordeGpuRenderer.geometry](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) | 评估资源预准备与有界分阶段上传；不能只把同步工作挪到tick | 首次显示延迟与单帧阻塞都可测；准备期间无半成品绘制或永久failed |
| H11.3 | BoneSnapshot及共享模型挂载 | 后台化前确认独占性、资源代际和取消机制；保持20FPS原动画 | 多事件共用模型、资源重载中断、退出后无共享状态污染和悬挂任务 |

## H07 [P2／待压力测量] 每tick玩家模拟范围扫描的CPU成本

旧节点轮转和scanTicks/scanNodesPerTick设置已移除，当前每tick按玩家所在区块的模拟距离范围收集已加载实体，UUID去重，成员存活/目标、区域及生成请求不分区域错峰；无活动事件时跳过查询。出生安全工作预算和原有重试/宽限仍保留。

剩余：模拟范围包含全建筑高度，多玩家重叠与高实体密度仍有查询、去重及生命周期遍历成本；不能用隔离虚拟渲染夹具证明真实HordeRuntime成本。增加实体硬上限会改变即时发现要求，当前不截断、不暗中限量；需结合H16取得实体数、玩家数和server tick证据。

### H07 详细优化任务

| 编号 | 代码位置 | 需要优化或补证 | 完成条件 |
| --- | --- | --- | --- |
| H07.1 | [HordeRuntime.scan/clearDebris](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) | 测玩家范围实体查询、重复覆盖和UUID去重；评估复用查询结果，保留每tick语义 | 单/多玩家、重叠/分离范围和高实体数的server tick P95/P99、分配及查询数完整 |
| H07.2 | [ClientEvent.updateGaps](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java)、EventData.updateRegions | 两份区域快照增加整数上传和临时集合，量化实际成本；避免为未变化区域反复重建 | 记录目标数、区域数、上传字节/次数、CPU分配；静止目标稳定后不持续上传 |
| H07.3 | [GLSL区域循环](../../src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl) | 当前每次location仍遍历区域；研究GPU空间索引/分档，不能重新扩大单体轮廓 | 0/1/16/64及更高目标数成本曲线，跨桶不漏目标；32/16/8若复用只作为索引而非大缺口 |

## H16 [P2／部分验证] 多事件、服务器与分阶段性能证据不足

当前两轮Sodium无光影夹具均PASSED：64实体间隔均值4.84/4.31ms，10000虚拟实例2.41/2.30ms，后者GameRenderer GPU均值2.08/1.93ms。此前H22的12.04ms真实实体回退未复现，已从活动缺陷中移除；单轮扰动原因未获因果证明。

剩余：夹具截图大量天空，提交10000不等于全数可见。BenchMod帧间隔与20/30秒GameRenderer CPU/GPU捕获的窗口和事件口径不同；本版本配置值260表示不限帧；采样帧数仍不构成稳定FPS承诺。64实体渲染线程分配均值约1.70/1.83MB每帧，尚无本轮之前的同口径分配基线，不归因Horde。

需分别覆盖真实事件、长线路、多目标/玩家、多事件、显式上限与无限配置，拆出extract/prepare/motion/draw/复制/显存和server tick；H24是已实测失败项，独立服务端当前受H25阻断。不得以减少实体、关闭发光或放宽环境门禁制造通过。

### H16 详细补证任务

| 编号 | 任务 | 记录内容与判定 |
| --- | --- | --- |
| H16.1 | 为本轮1.8格、新随机运动与新分割建立新基线 | 固定代码/未提交差异、配置、显卡/驱动、分辨率、相机、可见比例、实体/区域数量；旧1.2报告保留为历史 |
| H16.2 | 分离指标口径 | 同时保留BenchMod帧间隔与GameRenderer CPU/GPU原始窗口；提取、tick、swap/限帧、分配和GPU复制独立命名，禁止互相替代 |
| H16.3 | 单变量、多轮对照 | 每组至少三轮；改变一项算法时保留同场景对照，记录首次/稳态P50/P95/P99/最大值；不以平均值掩盖卡顿 |
| H16.4 | 场景覆盖与资源上界 | 0/100/1000/10000虚拟实例、64真实实体、单/多事件、目标数与视距变化、显式上限/-1、重载和退出；记录峰值显存与资源回落 |

本次用户明确不启动游戏，上述客户端、服务端、GPU与视觉场景均不在本轮执行；当前只能关闭静态/规则检查已覆盖的子项。

## H14 [P2／部分验证] 完整玩法、存档和联机仍缺验收

已实测未加载远处事件创建、新事件spacing=1.2、一tick删除区域外物品/清道夫尸体、保留战斗保护物品和普通牛，目标/成员1→2。调试Gizmo及配置迁移/旧值比对也已有证据，不再列为待办。

剩余：完整配额1→2→10、死亡增援、转派、取消/超时、区块卸载与重载、关服恢复、成员归属及多玩家订阅。本轮21项规则/区域回归与前轮单机Debug不能覆盖这些链路；旧Region辅助类的连续性测试也不能证明新GPU过渡画面通过。完整build历史Photon资源/基线失败未在本轮修复，不声称全项目测试通过；每日构建编号策略保留，不为配置缓存复用旧编号。

### H14 详细验收任务

| 编号 | 场景与入口 | 通过条件 |
| --- | --- | --- |
| H14.1 | [配额/预约/转派](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java)；本轮新增上限10 | 新规则初始1，定时/死亡增援逐步至10；第11个有效成员或预约不建立；多事件共享、同时转派、请求取消不穿透上限 |
| H14.2 | [HordeSavedData](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSavedData.java)、HordeTargetAssignment.load | 卸载/重载、退出/恢复、无效规则、旧超额成员淡出均保持归属和配额；不把区块卸载算作死亡增援 |
| H14.3 | [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java)与同步附件 | 多玩家进出/维度切换、丢页重发、完整快照及首次实体追踪正确；entity_height正确；事件成员不进入分割对象 |

## H26 [P2／已实现待验收] 事件尸体200tick与近处清扫粒子

当前范围：单体水平外扩至少3格并保留较大体型三倍余量，再按1格向外取整；Y不扩大，保留GPU连续过渡及高处隔离。事件生成清道夫死亡、以及它作为伤害来源击杀Mob时，在服务端死亡事件记录来源；创建EntityCorpse时设绝对世界期限为当前时间加200tick，并保存HordeCleanupAt。事件结束不会取消该期限；区块卸载后重载沿用原期限，不重新计满10秒。缺少期限但已有事件附件的旧尸体首次tick补200tick安全保留期，无法识别来源的旧普通尸体不猜测删除。

战斗保护优先：到期尸体仍在战斗保护AABB内时不由事件清理，移出保护后才清理；普通自然腐烂或正常战斗伤害不属于该自动清理。原版死亡过程中的清道夫本体不再直接被区域discard，确保能够正常创建尸体。未过期的事件尸体不被沿路清理抢先删除；路径掉落物在非战斗区域仍按既有规则处理。

自动审批拒绝了把isDebris扩大到所有EntityCorpse，理由是可能不可逆删除未授权的普通尸体。已采用更安全的明确来源/期限筛选，仅处理事件成员及其击杀产生的尸体；普通无来源尸体不扩大清除。没有通过其他路径执行被拒绝的全体普通尸体删除，也不主动询问扩大授权。

粒子实现：保留particleIntervalTicks默认1及particleRange默认24格（1～32），每次在潮内尝试近3/中2/远1个不同区域，烟雾/碎屑/刀光分级批量发射，基础频率详见上方追加实现。复用CAMPFIRE_COSY_SMOKE、BLOCK粒子和已有短/长/双斩资源。只选择已加载、玩家范围内、视锥可见且相机射线无遮挡的位置；从实际提交行列随机采样潮内位置，检查波头/波尾、目标安全包围与模型半径，不再只在块中心发射。实际清扫位置通过原版粒子协议的horde_cleanup发射器通知，客户端按格去重、最多保留一秒旧提示并优先发射；该发射器使用registerSpecial，不创建贴图JSON、不复制造型或战斗伤害。

GPU边界：主浪潮分割、位移和动画仍在GPU；本轮指定的原版烟雾/碎屑及原有刀光复用原版客户端粒子引擎，出生筛选、生命周期与碰撞并非全GPU模拟。没有把原版GPU绘制等同于全GPU生命周期。严格仅CPU提交区域的完整粒子替代仍未实现，作为人工边界记录。

| 编号 | 入口 | 待验收／下一步 |
| --- | --- | --- |
| H26.1 | [HordeCleanupUtil](../../src/main/java/org/unitego/lobecorp/util/horde/HordeCleanupUtil.java)、[HordeGameplayEvents](../../src/main/java/org/unitego/lobecorp/events/HordeGameplayEvents.java) | 验证事件成员自身、直接/间接击杀、非事件来源、死亡取消及原尸体创建链；普通来源不误删 |
| H26.2 | [EntityCorpse](../../src/main/java/org/unitego/lobecorp/world/entity/EntityCorpse.java) | 验证创建后199/200tick边界、保存重载、未加载期限、事件已结束及旧标记尸体；确认保留期不被路线扫描提前截断 |
| H26.3 | [HordeRuntime.clearDebris/protectedFromCleanup](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) | 战斗区域中到期尸体与掉落物仍保留，移出保护后清理；同时多目标/成员分配不漏保护 |
| H26.4 | [HordeAmbient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeAmbient.java) | 正常频率、不同间隔、正面/背面/遮挡/远处/未加载与暂停测试；三类粒子可见且没有超过配置范围生成 |
| H26.5 | [粒子注册](../../src/main/java/org/unitego/lobecorp/registry/particle/client/RegisterParticleProviders.java) | 验证发射器协议、清扫刀光位置、多人追踪、提示合并及过期；对比主体CPU/GPU和粒子分配成本 |
| H26.6 | [RegionBox.forEntity](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) | 至少3格净空/大体型余量与真实Y、不同体型/负坐标、移动目标平滑合流；GPU粒子替代不计为已完成 |

本轮IDEA错误诊断11份文件均为零，BenchMod Compile退出0，HordeCleanupTest3/3、HordeRegionTest4/4通过；Data退出0，23份实际资源仅中英文语言两份更新，另有两份生成缓存变化。本次未启动、重启、接管游戏，未运行MC MCP、Debug或真实基准；上述动态验收不因静态诊断/规则回归通过而关闭。

## H25 [P2／环境受阻] BenchMod Server未能进入测量

本轮IDEA执行BenchMod Server（2cd82417-bd40-4a0c-870d-9feef175646e）退出1，无server summary。专服启动时ReRetro Damage Indicators加载客户端MultiBufferSource失败，Photon/LDLib反射同时记录Particle类缺失。当前依赖由既有implementation/internalTestMods引入，未擅自移除第三方Mod或改变公共依赖。

需为专服基准建立正确的客户端依赖隔离，再运行64实体AI与真实事件压力场景；该失败不是Horde服务端性能结论。日志和崩溃报告保存在本轮备份server-start-failure。

### H25 解阻与验证任务

1. 通过现有Gradle/运行配置核对专服类路径，隔离仅客户端的ReRetro等依赖，不直接删除公共依赖或掩盖类加载失败。
2. 核对Photon/LDLib反射缺类是否仍阻断，保存修正前后加载日志；真实进入测量并生成server summary后，才能报告服务端场景结果。
3. 先运行64真实实体AI基线，再补真实Horde事件、每tick扫描和多玩家负载；AI场景不能代替事件调度成本。

## H23 [P2／范围外记录] 指挥家退出时鼠标恢复异常

最新两轮客户端基准退出0、场景PASSED，但收尾再次出现Trying to return to in-game GUI during disconnection：Minecraft.setScreen → MouseHandler.grabMouse → ConductorControls.exit:518 → clearSession:401 → ClientRuntimeEvents.onClientLevelUnload；随后有异步任务拒绝。

本轮已将HordeClient.clear放在其他系统会话恢复之前，避免阻断本系统GPU释放；ConductorControls不属于本轮Horde修改，未改其逻辑。需由对应系统修复断开过程鼠标恢复，不能将正常测量结论扩展为退出无异常。

### H23 后续处理与验收

入口：[ConductorControls.exit/clearSession](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorControls.java)与[卸载事件](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)。需要在对应系统授权范围内处理“断开时恢复鼠标/界面”的顺序；不能只吞异常。验收包含正常退世界、退出客户端、基准自动退出及指挥模式退出；无GUI重入异常、无Horde GPU资源残留，且不改变用户原有鼠标状态。

## H03 [P2／规则异议] 同域事件规则与配额归属

多个同域事件规则快照不同时，目标保留首次建档规则。已修复无效/空闲目标回收及直接assignment查表；剩余是共享配额、冷却与持久化择主语义。当前沿用已有规则，不重置仍受另一事件覆盖的目标；将此取舍记录为人工边界，不主动提问。

### H03 人工规则与后续验证

当前仍采用首次建档规则，单目标硬上限10不受后续事件提高。若后续要改变规则择主，先把“重叠事件不同配额/冷却、某事件结束、存档重载”的期望写成规则表，再实现和验证；本轮不默认为较大配额优先，也不通过重建目标绕过冷却。此项不是已确认性能缺陷。

## H10 [P3／API约束] RGBA8运动缓存重复求值

当前运动缓存每实例5个原始float值，普通location约6次求值；已减少数据量但不同输出像素仍各自计算位置。26.1.2 TextureFormat无可用浮点颜色附件，跨片元共享或更高效展开需要受支持的多输出/新管线方案。无光影GPU采样并不证明该重复成本为零；与H24优化一并评估。

### H10 后续优化任务

- 定量测motion pass随单位数/区域数增长的耗时；分别评估位置、前后切线与光照的重复求值。源码约6次location不是实测GPU预算。
- 先核对当前Blaze3D支持的输出、缓存和材质能力，再设计共享求值；只接受保持float32位置、发光和既有动画的方案。任何新旁路或后端绑定都应作为独立取舍记录，不能伪称纯接口替换。

## H19 [P3／接口与依赖异议] 公开协议及GeckoLib内部入口

旧扫描/过渡配置已移除，快照保留字段不再代表当前扫描间隔。公开snapshot/revision/scope/clock等字段不能因当前无内部调用就删除。GeckoLib5.5.2 apply/cleanup仍是骨骼临时挂载路径，IDEA标记Internal，无经验证的稳定等价替代；固定版本与清理语义保留，升级时专项处理。Iris转接还直接核对IrisVertexFormats扩展布局，属于版本相关内部入口；已验证1.11.4，不视为所有版本兼容。GPU渲染器本轮全诊断0问题，缓存拥有者误报已按精确生命周期处理；HordeClient保留既有2项空值提示。仍不应为消除借用提示立即close共享对象。每目标规则副本与每tick存档脏标记优化还需尊重H03及持久化语义。

### H19 维护与资源优化任务

- 公开字段、快照和保留计时字段先做调用/兼容边界清单，不能以“本项目未调用”直接删除；新增实体高度与区域双快照的布局需有生产/夹具一致性检查。
- 整理GeckoLib/Iris内部入口的版本表及失配行为；分别核对事件移除、世界退出、资源重载、准备异常时GPU资源各释放一次。IDE缓存资源提示不是泄漏定论，不能在借用位置立即关闭共享对象。

## H17 [P3／历史证据缺口] 旧Debug与配置恢复无法完整追溯

历史两次Debug提前结束原因未确认；旧引用.backups/horde-retest-20261022/before-runtime-config.json在2026-10-23检查时缺失。不能从后来正常运行推断旧失败根因，也不能凭空恢复旧换行。本轮新的Debug、PID、原始配置及SHA证据已独立保存，不替代缺失历史记录。

### H17 证据补全边界

只追溯能定位到原始日志/快照的旧故障；无法找到的历史文件维持“证据缺失”，不逆推配置、不重新制造同名备份。新运行每轮保存时间、代码、进程归属与原配置哈希，分别证明新行为；这不能消除旧证据缺口。

## H21 [人工边界／已采用替代] GPU范围、预算及旧数据

CPU提交动态六整数目标与稳定身份，GPU保留平滑状态，不在CPU计算区域插值或逐绘制块影响；既有模型烘焙、线路/高度准备、光照上传与粗块筛选仍保留，因此不声称整个渲染系统只有CPU区域提交。若字面要求连既有资源准备全部取消，需要单独GPU资源架构，当前保留可运行能力。

每tick解释为目标发现/成员存活/区域/生成请求的即时响应，出生安全预算仍保留；未加载处以输入高度暂代并等待加载，不强制加载。旧事件快照的间距和高度宽度不自动改写，新建事件使用1.8默认值和扩大采样。全亮遮罩不等于动态照亮环境，GPU整数小包围、三级查询索引、连续状态和粗高度的未解决边界见H15。疑问全部记录，只有用户明确要求时再询问。

### H21 必须保留的取舍

- 视觉范围按最新指令改为体型3倍宽深、真实Y和1格整数包围；8/16/32大轮廓已取消，后续若研究GPU索引必须继续保持小缺口与连续过渡。
- 单目标10指有效真实参战成员加预约，旧超额通过既有退场释放名额；不把“所有Mob同时立即删除”或“全局只有10只”作为替代需求。
- 默认间距1.8与增援至10只作用于新规则；既有活动事件快照不强改，需要重建后观察新参数。基础速度未降低；LOD及自动减量规则默认关闭。
- **2026-10-25 覆盖**：动画采样由“20 FPS”改为“原始动画等距 4 帧”（默认 4、配置可回退，见追加需求与 EX-14）；尸体默认静态模型、32/180 格距离；分割区域不可进入（EX-13）。
- 静态模型/线路/高度与光照CPU准备仍在；粗高度不能完成真实多层路径求解。未验证项继续保留，只有用户要求时再询问。

本轮尸体与粒子备份：C:/Users/wang_/AppData/Local/Temp/horde-cleanup-particles-20261024-5761ddab。普通无来源尸体扩清理被自动审批拒绝的操作未执行；来源和GPU粒子边界见H26。

## 证据与维护

本轮备份根为C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d，包含baseline-client、after-client-1/2/3、运行前配置、Iris原始报告与截图；文档收尾前另存closeout-docs。旧文档合并备份horde-docs-consolidation-20261023-kwqkdh3p及旧修复备份horde-fixes-20261023-lhyy6_x_保留。本次追加文档、源码/配置快照及用户截图另存C:/Users/wang_/AppData/Local/Temp/horde-optimization-doc-20261024-991df925。问题解决且完成适用验证后从本页移除，不维护第二份待办。
