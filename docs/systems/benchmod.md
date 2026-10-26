# BenchMod 基准工具链

## 2026-10-26 第四轮：远档片、20帧烘焙与GPU距离降帧（实现验证，测试矩阵未跑）

### 已实现
- 动画统一烘 **20 帧**（`animationFrames` 默认 20，近/中/远/片共用；本次运行的 horde-environment 四档均为 `frames=20`，CPU 烘焙字节 249,600/65,280/23,040/11,520）。
- 模型新增第 4 档“片”：整体 1 盒 = **6 方向面片组成的盒子**（24 顶点），`HordeMeshReduction` 四档 520/136/48/24；近/中/远不变。
- 新增客户端配置（缓存/热重载按资源代际处理）：
  - `cardDistance`：使用片的距离（格，**-1 不使用**；本次测试 100，符合 96 格后进入片档）。
  - `frameDistance`：GPU 距离降帧基数（格，**-1 不降低，0 不动**；本次测试 10）。
- GPU 降帧在 `horde_motion.fsh` 计算，对所有档位生效：到相机绝对距离 `<d` 用全部 20 帧，`d..2d` 10 帧，`2d..3d` 5 帧，`>3d` 静止（1 帧）；帧在 20 帧序列里均匀取样。`HordeParameters` 新增第 18 个 vec4 `FrameLod`（`PARAMETER_BYTES` 288）。
- 新增计数 `lod_card_blocks`/`lod_card_instances`；片档选择带迟滞，回到远景 2 盒。

### 验证运行（client-1792986006389，光影）
- PASSED、10000 实例、焦点 0 失焦；模型档位 520/136/48/24。
- 片档 72 块 / 4673 实例；中景 4741、远景 584；展开顶点 785,071（原 2 盒远档 898k）。
- 帧 13.079/14.291/14.915 ms（均/P95/P99），世界 GPU 12.702 ms，draw 7.254 ms——**GPU 阶段明显低于 2 盒远档**，但本轮环境正常，不能直接当稳态目标结论。
- 截图：`build/agent-backups/20261026-card-lod/artifacts/screenshots/horde-render.png`；配置与 summary 同目录。

### 限制
- 当前“片”是 **6 方向面片盒（UV 方向映射 + 逐帧包围盒动画）**，还不是离屏把 6 个方向渲染成视图图集的真正烘焙 imposter；外观真实性仍需下一步实现与验收。
- `cardDistance`/`frameDistance` 默认 -1，不改变现有默认画质；现有旧配置的 `animationFrames=4` 会覆盖新默认 20，需要改配置或删配置才按 20 帧烘焙。
- 新一轮 10 项测试矩阵（清道夫30 AI/无AI、清道夫尸体30、随机生物尸体100、500×500 无上限事件、同上+10监守者；每项光影/非光影各重启一次）尚未实现/执行；本轮只验证机制。

### 真6方向烘焙与两档化（WIP，2026-10-26）
- 已实现 CPU 软件光栅烘焙：模型完整帧按盒面 6 方向各渲染 64×64 tile、每方向 20 帧，拼成 384×1280 图集（`HordeCardBake`）；tier3 几何 UV 编码 `(方向*2+角点)/12`，Iris/原版顶点着色器按方向+帧取图集 tile；图集缓存为 `DynamicTexture` 并随模型清理关闭。
- 按要求**移除中景/远景简化盒**：`HordeMeshReduction` 只产出完整模型与片（tier0/tier3），`selectLod` 仅在“到相机距离≥cardDistance”时切到片，否则完整模型；不再有 136/48 盒。
- 验证 `client-1792987990655` PASSED：片 4672 实例，但 `cardDistance=100` 让近处完整模型范围过大，展开顶点 2,882,499、horde 34.435/118.543ms——说明两档化后片的距离要明显更近（32–48 格）才有性能意义；截图见 `build/agent-backups/20261026-card-bake/`。
- 外观仍有问题：远片出现偏蓝/偏暗的颜色与光照差异（烘焙采样/通道/光照需继续校准），不是最终验收画面。
- 配置默认仍为 `cardDistance=-1`（不使用片）与 `frameDistance=-1`（不降帧）；关闭片时只剩完整模型，10000 实例会非常重，实际使用需把 cardDistance 设为 32–48 左右。
- 按用户设定 `cardDistance=32, frameDistance=10, animationFrames=20` 复测 `client-1792988762923` PASSED：**10000 全部走片**，展开顶点 240,000（24×10000），draw 5.450ms、世界 GPU 11.065ms、帧 13.054/23.695/27.035ms——几何/GPU 明显变轻，但 P99 仍偏高；片颜色偏蓝偏暗的视觉问题仍在（调试图集本身灰/红正常，说明是采样/光照而非烘焙数据），需继续校准；截图与图集在 `build/agent-backups/20261026-card32-retest/`。
- 片在光影下偏蓝/偏暗是当前光影本身的效果，按用户要求**不再改光影**；需要正常颜色时用非光影组测试。
- `maximumVisible=-1` 现在按**真正无限**跑（不再按 10000 折算）：`client-1792989806860` PASSED，事件宽 512/长 320 实际生成 **29,456** 实例且全部走片，展开 706,956、draw 11.011ms、帧 17.358/20.462/24.163ms；归档 `build/agent-backups/20261026-unlimited/`。

## 优化前后参数与结果对照（2026-10-26 汇总）

### 测试与结构参数
| 参数 | 优化前 | 优化后（当前） |
| --- | --- | --- |
| 近/中/远景顶点档 | 520 / 304 / 176 | 520 / 136 / 48 |
| 中景代理 | 按骨骼逐块包围盒 304 | 5 盒：腿脚/躯干/双臂(合并)/头/罐子 = 136（薄刀面保留真实几何） |
| 远景代理 | 逐组包围盒 176 | 2 盒：下半身/上半身 = 48 |
| LOD 阈值（格） | 48 / 96 | 48 / 96（未改） |
| 背后的罐子 | 并入躯干，红色丢失 | `PART_CAN` 独立盒，灰罐体，仅背面 +z 取 `biomass` 局部红 |
| HordeParameters | 256 B / 16 vec4 | 272 B / 17 vec4（新增 UnitPage：行环掩码、列片数） |
| 单位历史 | 16×16 页、每帧重建/哈希 churn | 行环稳定页+静态查找，仅尺寸变化时重建，环页上限 32 MiB |
| 夹具可见性门禁 | 只查计数/文件，空画面可 PASSED | 地面锚定 + 同机位开关浪潮 A/B 像素门禁 + 相机几何断言 + visible_pixels |
| 窗口焦点 | 无自动恢复/门禁 | BenchWindow 每 tick 还原/重聚焦 + MEASURE 失焦硬门禁 |
| Spark 采样 | `sparkc profiler start --timeout 30`（只采 30 秒） | setup 启动 timeout 600，覆盖整个浪潮场景；measure 结束停止，尸体阶段等上传链接，`requireSaved` 后才允许结束客户端 |
| 截图规则 | 只有基准截图 | 每轮必须截屏并交付路径（含游戏内铺满/外观截图） |

### 光影 10000 实例（同夹具三轮，均 PASSED）
| 指标 | 优化前 client-1792946360760 / 1792946702517 / 1792946911693 | 优化后 client-1792976399030 / 1792976864064 / 1792977119469 |
| --- | --- | --- |
| 帧均 ms | 23.578 / 23.702 / 23.659 | 14.365 / 15.059 / 15.026 |
| P95 ms | 25.419 / 25.697 / 25.549 | 15.993 / 17.255 / 16.791 |
| P99 ms | 46.913 / 46.497 / 46.710 | 18.064 / 18.624 / 17.971 |
| 世界 GPU ms | 23.212 / 23.266 / 23.255 | 13.834 / 14.398 / 14.534 |
| Horde draw GPU ms | 16.608 / 16.687 / 16.606 | 7.818 / 8.457 / 8.193 |
| expand / base GPU ms | 10.022 / 3.730 等 | 3.556 / 2.892 等（三轮 3.556–3.668 / 2.892–2.954） |
| copy / units GPU ms | 1.158 / 0.845 等 | 0.403 / 0.417 等（copy 0.403–0.411） |
| 展开顶点/帧 | 2,369,028 / 2,369,222 / 2,369,162 | 898,328 / 898,320 / 898,291 |
| Iris 逻辑复制/帧 | 132.69 MB | 50.32 MB |
| 单位历史复制/帧 | 0.786 MB | 1.475 MB（全列覆盖后上升，不是变差） |
| 单位阶段 CPU 均值/P95 ms | 2.466/37.074、2.340/33.103、2.353/29.831 | 0.433/0.674、0.356/0.618、0.358/0.564 |
| 光照 CPU 均值/P95 ms | 0.073/0.253、0.078/0.259、0.074/0.270 | 0.079/0.313、0.063/0.337（第三轮 0.066/—） |
| passes / copies | 15 / 2 | 12–14 / 1 |
| 可见像素（VERIFY） | 1,449,664 / 1,418,363 / 1,418,820 | 1,638,388 / 1,636,706 / 约 1.64M |

### 普通组（关闭光影）
| 指标 | 优化前 client-1792976051992 | 优化后 client-1792977359699 / 1792978532864 |
| --- | --- | --- |
| 帧均/P95/P99 ms | 3.402 / 4.498 / 5.690 | 6.168/7.054/7.552、6.184/7.163/8.130（当前显示器 165Hz 地板，不与 3.4 直接比） |
| 世界 GPU ms | 2.026 | 2.026 / 1.897 |
| Horde draw ms | 1.507 | 1.359 / 1.225 |

### 尸体
| 场景 | 优化前 | 优化后 |
| --- | --- | --- |
| 64 尸体光影 | 8.078 / 9.576 ms | 7.494 / 9.55、7.539 / 9.855 ms |
| 64 尸体普通 | 1.615 / 3.439 ms | 1.568 / 3.012、1.561 / 3.046 ms |
| 256 尸体光影 | — | 8.428 / 10.590 ms，static_drawn 256、fallback 0、draws 2 |
| 256 尸体普通 | — | 1.986 / 4.382 ms，static_drawn 256、fallback 0、draws 1 |

### 说明
- “优化后”性能列取 520/136/48 档位的三轮干净数据；当前第三轮只改了代理盒组成与罐子 UV（顶点数仍 136/48），尚无干净三轮，一次场景级 `client-1792980328267` 世界 GPU 14.620 ms 但整轮因外部抢焦点 INCONCLUSIVE，另有 valid 轮被并行 GPU 负载拖到 23–33 ms，均不计达标。
- 普通组帧间隔受当前 165Hz 刷新地板影响，比较以世界 GPU/draw 为准；所有轮次原始 summary、GPU、Spark 链接、截图和 SHA256 在 `build/agent-backups/`。


## 2026-10-26 第三轮：罐子/红色元素补回、Spark 全程采样与测试环境记录

### 需求
清道夫背后的罐子（模型骨骼 `can`，子骨骼 `biomass`）是角色元素之一，简化模型丢失了红色；Spark 性能采样必须在进入测试后一直开启到测试结束（不再只开 30 秒），测试完成后先关闭 Spark、等上传链接输出后再关闭客户端；所有测试要截屏并交付；仍按先测试、再修改、再测试执行。

### 模型核对（`resources/entity/sweeper/sweeper_a.geo.json`，128×128 纹理）
- `can` 有 6 个立方体（灰罐体，范围 y≈8–34、z≈2.5–14.1）；子骨骼 `biomass` 是 9×19×9 立方体，UV [0,29]，扩散纹理与 `sweeper_a_glowmask.png` 都以红色为主（区域平均 RGB≈145,69,74，红像素 495）；双臂薄刀面 UV [0,65] 也是红色（≈174,72,71）。
- 旧代理把 `can/biomass` 归入躯干、远档把红刀面合并进整盒，红色因此丢失；仅靠合并盒的随机法线选面无法取到红色面。

### 修改
- `HordeMeshReduction.partOf` 识别 `can/jar/pot/biomass` → `PART_CAN`；中景 5 盒改为“腿脚/躯干/双臂（合并）/头/罐子”，顶点数仍 136（薄刀面仍直出）；远景仍 2 盒 48。
- `bestFace` 只在罐子/上身的**背面（+z）**优先取 `biomass` 红色面，其余面保持罐体/身体灰色，形成“灰身体+灰罐体+局部红色开口”，避免整个代理被染红；纹理解析失败时按法线选择。`NativeImage` 在全部选面完成后关闭（第一次实现提前 close 导致“Image is not allocated”并使模型准备失败，已修复）。
- 近景 520 全精度、4 帧动画、发光 pass、LOD 迟滞不变；中景双臂由两个盒合并为一个，换取罐子独立盒（顶点预算不变）。

### 视觉证据
- `build/agent-backups/20261026-appearance-can/can-A3.png`、`can-B3.png`（2560×1494，游戏内真实事件 96 宽，模型三档 520/136/48）：红色罐体/红刀面已保留，从背面看几乎每个单位都有红色罐体。
- 若认为“过红”或罐体轮廓应更接近原模型的灰色外壳，请指出，我再按同一 UV 选择逻辑改为灰壳优先、局部红面。

### 本轮运行与环境（不冒充达标数据）
- Spark 全程开启：`BenchSpark.start` 在浪潮 setup 启动 `sparkc profiler start --timeout 600`（覆盖整个浪潮场景，不再 30 秒超时）；浪潮 measure 结束后 `BenchSpark.stop`，随后尸体场景每个 tick 轮询上传链接，拿到本次新链接后写入 `artifacts/spark/horde-profile.txt`；尸体 measure 结束前 `requireSaved` 硬门禁，没拿到 Spark 输出不允许结束客户端。Sodium 模组始终随客户端加载，不为了对比关闭。
- 客户端保持运行到用户手动关闭，不强制 kill；Spark 停止与链接落盘发生在客户端退出前。
- `client-1792980038619`：新增 `redness` 提前关闭 NativeImage，模型准备失败（已修复），horde FAILED。
- `client-1792980328267`：horde 场景 PASSED，帧 15.403/17.653/18.837ms、世界 GPU 14.620ms、draw 8.855ms、展开 897,237、中景实例 4741/远景 5258；但整轮被 BenchMod 判 INCONCLUSIVE（`client.window.focus_lost`，外部抢焦点）。
- `client-1792980557725`：本夹具焦点硬门禁捕获 MEASURE 失焦 1 次（losses=1），FAILED，保留证据不计数据。
- `client-1792980845712`、`client-1792981136050`：PASSED 且环境 valid，但 GPU 被并行负载拖慢（世界 GPU 33.229/25.472ms、帧 34.361/20.723ms），不能用于性能达标。
- 因此本构建只有一次场景级 14.620ms 世界 GPU 的数据；同顶点档位（520/136/48）的干净三轮仍是上一轮的 14.365/15.059/15.026ms、世界 GPU 13.834/14.398/14.534ms。**新构建缺少干净的三轮达标证据**，不写成已达标；待 GPU 空闲时补跑。

- `client-1792983163376`（Spark 全程覆盖 + 灰罐体局部红）：PASSED，帧 25.097/32.433/34.945ms、世界 GPU 23.013ms（并行负载），Spark 链接 `https://spark.lucko.me/1kvmGBN9y2` 已写入 `artifacts/spark/horde-profile.txt`，模型三档 520/136/48；该轮同样不能作为性能达标。
- 视觉证据：`build/agent-backups/20261026-appearance-can/can-A3.png`、`can-B3.png` 是“整体红罐”旧版；`build/agent-backups/20261026-appearance-spark/artifacts/screenshots/horde-render.png` 是修正后的灰身体/灰罐体+局部红。

### 规则记录
- Spark 全程采样：进入浪潮测试后开启，测试结束后停止并等待上传链接落盘，再允许关闭客户端；Sodium 保持加载，不为了对比关闭。每轮测试必须截屏并交付路径（见原始项目 `AGENTS.md`/`workflow.md` 的焦点与截屏规则）。
- 焦点被外部程序抢走时，本夹具会拒绝该轮（保留证据），不能通过放宽门禁或删日志美化。

## 2026-10-26 第二轮：失焦防护、外观改善与普通/光影复测

### 需求与顺序
用户要求：关闭光影也要测试；测试期间焦点不能被抢走；测试必须截屏给人看（该规则已写入项目规范）；中远景外观不要过度简化；仍按“先测试、再修改、再测试”执行。本轮先跑当前代码的普通/光影基线，再修改焦点与外观，最后复测两组。

### 失焦防护（测试夹具）
- 新增 `src/bench/java/org/unitego/lobecorp/client/bench/BenchWindow.java`：每 tick 检查窗口，最小化则还原并最大化，失焦则重新聚焦，累计 `focus_losses`/`focus_restores`。
- Horde/Sweeper/Corpse 三个场景的 setup 与每个 STABILIZE/WARMUP/MEASURE tick 调用 `BenchWindow.ensure`；Horde 在 MEASURE 开始时重置计数，只统计测量窗口。
- Horde verify 增加硬门禁：MEASURE 结束必须 `focused=true`、`iconified=false`、`focus_losses==0`，否则判失败；`horde-environment.json` 新增 `window_focused_at_end`、`window_iconified_at_end`、`focus_losses`、`focus_restores`。
- 实测：接受的三轮光影与两轮普通均为 0 失焦/0 恢复；另有两轮被拒绝并保留证据——client-1792977705014（本轮门禁捕获 losses=2）、client-1792978004840（BenchMod 环境判定 `client.window.focus_lost` 为 INCONCLUSIVE）。这说明焦点门禁实际生效，外部程序仍可能抢焦点，被拒绝的轮次不作为数据。

### 外观改动（中/远景显式 LOD）
- 修改前：中景 4 个包围盒 112 顶点（躯体含腿、头、左右臂），远景 1 个整体包围盒 24 顶点；高位截图呈块状板。
- 修改后：中景按腿脚/躯干/头/左右臂 5 个包围盒 136 顶点（薄片仍保留真实几何），远景上下 2 个包围盒 48 顶点；近景 520 全精度不动，动画仍 4 帧、发光 pass 与迟滞不变。`HordeMeshReduction` 的 `PARTS=5` 与远景 2 盒由 `partOf` 分类实现。
- 新增按档实例计数 `lobecorp.horde.lod_{near,mid,far}_instances`，本轮中景 4753、远景 5246、近景 0（夹具相机 48–96/96+ 格，无近景实例）。

### 光影复测（同一夹具，三轮）
| 指标 | 修改前 client-1792975839616 | 修改后 client-1792976399030 / 1792976864064 / 1792977119469 |
| --- | --- | --- |
| 帧均/P95/P99 ms | 13.889 / 15.811 / 17.863 | 14.365/15.993/18.064、15.059/17.255/18.624、15.026/16.791/17.971 |
| 世界 GPU 均值 ms | 13.305 | 13.834 / 14.398 / 14.534 |
| Horde draw ms | 7.239 | 7.818 / 8.457 / 8.193 |
| expand / base / units GPU ms | 2.749 / 2.583 / 1.069 | 3.556/2.892/0.417、3.627/2.918/0.701、3.668/2.954/— |
| 展开顶点 / 复制 | 658284 / 36.9MB | 898291–898328 / 50.3MB |
| 焦点 | — | 0/0，0/0，0/0 |

三轮均 PASSED、10000 实例不降；帧均≤16.7、P99≤20、世界 GPU≤15 仍全部满足（最差世界 GPU 14.534）。外观增加的约 24 万顶点换来世界 GPU 约 +0.5–1.2 ms，仍在目标内。

### 普通组复测（关闭光影）
| 指标 | 修改前 client-1792976051992 | 修改后 client-1792977359699 / 1792978532864 |
| --- | --- | --- |
| 帧均/P95/P99 ms | 3.402 / 4.498 / 5.690 | 6.168/7.054/7.552、6.184/7.163/8.130 |
| 世界 GPU 均值 ms | 2.026 | 2.026 / 1.897 |
| Horde draw ms | 1.507 | 1.359 / 1.225 |
| 焦点 | — | 0/0，0/0 |

普通组帧间隔出现约 6.15 ms 的一致地板：当前显示器为 2560×1600@165Hz（1/165≈6.06 ms），而早前同代码普通基线在刷新/呈现条件不同时为 3.40 ms，故普通组帧间隔不与早前直接可比；GPU 阶段（世界 GPU 2.03→1.90–2.03、draw 1.51→1.23–1.36）显示外观增加顶点没有让普通组变差。

### 截图（本轮交付）
- 基准制品：各轮 `artifacts/screenshots/horde-render.png`、`horde-render-off.png`、`sweeper-render.png`、`corpse-render.png`（例如 `build/agent-backups/20261026-appearance-final/iris-run3/artifacts/screenshots/`）。
- 游戏内铺满取景（光影开启，2560×1494）：`build/agent-backups/20261026-appearance-final/in-game-fill/fill-D.png`（人群填满全屏、左右出屏）、`fill-F.png`（大范围铺满）、`fill-night.png`（夜间发光）。实机 overlay 显示 submitted/GPU instances 5247–9481、batches=2、modelBuilds=3、irisMiWords=16。
- 取景说明：基准固定相机位于上方 64 格、后方 100 格，用于一次容纳全部 10000 实例做可重复测量，因此基准截图不是“贴脸铺满”视角；游戏内把相机放到人群内部即可看到从屏幕外到屏幕外的铺满效果，计数仍为真实事件规模。

### 限制
- 普通组只取得两轮有效数据（另一轮被焦点门禁拒绝、一轮被 BenchMod 焦点环境判 INCONCLUSIVE，均保留但不算数据）；E4 换包/重载未做；焦点仍可能被外部程序短暂抢走，门禁会拒绝该轮而不是美化结果；游戏内截图由 MCP 采集，不能作为帧时间证据。


## 2026-10-26 基准有效性修复与修改前后对照

本轮先证明旧门禁失效，再在同一夹具下取得修改前后数据。全部运行均为隔离 BenchMod Client：MC26.1.2、NeoForge26.1.2.100、Sodium0.9.2、JBR25.0.2、RTX3060 Laptop/NVIDIA616.64、2560×1494、视距/模拟12、VSync关闭、260、seed7 flat 世界；光影组 Iris1.11.4 + ComplementaryReimagined_r5.9.3，普通组关闭 Iris 着色器。每轮含 64 真实清道夫、10000 虚拟实例、64 尸体三段，summary 均 PASSED。

### H27 空画面根因（已确认）
- 夹具以 `context.player().position()` 同时作为事件锚点和相机基准。bench 隔离世界实际玩家在 y≈68.6 悬空，而平坦地面为 y=-60；相机被抬到 `origin.y+64≈132.6`，即地面之上约 192.6 格。
- 相机在 12 区块渲染距离下雾端为 `12×16=192` 格（`FogRenderer.setupFog`），整片地面与所有虚拟单位都位于 3D 距离 ≥192.6 处，被完全雾化成天空色；单位仍有提交/展开计数，所以旧 `verify`（只查采样、截图文件与统计字符串）判 PASSED。
- 修正：事件/相机改以中心地面高度锚定（`getHeight(MOTION_BLOCKING_NO_LEAVES)`，未加载列回退该地面值），相机仍为侧上方 64 格、后 100 格、pitch≈32.2°。

### 新门禁（错误画面不可通过）
- 同机位先关闭浪潮拍 `horde-render-off.png`，再开启拍 `horde-render.png`；只在画面下 3/4、通道差>16 统计差异像素，要求 ≥max(5000, 下3/4×0.5%) 且差异包围盒宽≥画面1/5、高≥画面1/20；`visible_pixels` 写入 summary 与 `horde-environment.json`。
- 相机几何断言：实际 pose 与地面高差必须在 `CAMERA_HEIGHT±8`、pitch∈[20,45]、后移距离±8，否则 VERIFY 失败。
- 计数门禁保留：MEASURE 每 tick `lobecorp.horde_render.instances==10000`，事件 failed 即失败；本轮同机位差异 1.62–1.66M 像素、包围盒覆盖画面下 3/4。

### 修改前后（光影，三轮同一夹具）
| 指标 | 修改前 client-1792946360760 / 1792946702517 / 1792946911693 | 修改后 client-1792950624281 / 1792950875573 / 1792951133286 |
| --- | --- | --- |
| 帧均 ms | 23.578 / 23.702 / 23.659 | 14.444 / 14.456 / 14.992 |
| P95 / P99 ms | 25.419/46.913、25.697/46.497、25.549/46.710 | 15.650/16.348、15.690/16.273、16.239/16.919 |
| 世界 GPU 均值 ms | 23.212 / 23.266 / 23.255 | 14.089 / 14.123 / 14.648 |
| Horde draw GPU ms | 16.608 / 16.687 / 16.606 | 7.354 / 7.343 / 7.945 |
| expand GPU ms | 10.022 / 10.028 / 10.067 | 3.136 / 3.146 / 3.126 |
| base / glow GPU ms | 3.730/0.453、3.759/0.456、3.767/0.454 | 2.872/0.173、2.882/0.174、2.869/0.173 |
| copy GPU ms | 1.158 / 1.157 / 1.164 | 0.347 / 0.349 / 0.346 |
| units GPU ms | 0.845 / 0.917 / 0.773 | 0.474 / 0.455 / 1.016 |
| 展开顶点/帧 | 2369028 / 2369222 / 2369162 | 658654 / 658665 / 658700 |
| Iris 逻辑复制 MB/帧 | 132.7 / 132.7 / 132.7 | 36.9 / 36.9 / 36.9 |
| 单位阶段 CPU 均值/P95 ms | 2.466/37.074、2.340/33.103、2.353/29.831 | 0.371/0.597、0.349/0.617、0.373/0.610 |
| 光照 CPU 均值/P95 ms | 0.073/0.253、0.078/0.259、0.074/0.270 | 0.054/0.213、0.056/0.194、0.058/0.201 |
| 可见像素（VERIFY） | 1449664 / 1418363 / 1418820 | 1658745 / 1659273 / 1655515 |
| passes / copies | 15 / 2 | 12/14、12、12 / 1 |

三项目标：帧均≤16.7 ms、P99≤20 ms、世界 GPU≤15 ms 在修改后三轮全部达到；三轮帧均差 3.7%（<5%）。修改前数据是在同一修正夹具下旧代码取得，因此可作为同夹具对照；旧版空画面数据不再用于性能结论。

### 普通组对照
| 指标 | 修改前 client-1792946075623 | 修改后 client-1792951390739 |
| --- | --- | --- |
| 帧均/P95/P99 ms | 3.706 / 6.034 / 7.940 | 3.365 / 5.346 / 6.777 |
| 世界 GPU 均值 ms | 1.850 | 1.663 |
| Horde draw GPU ms | 1.861 | 1.094 |
| base / units GPU ms | 0.600 / 0.910 | 0.348 / 0.461 |
| 展开/复制 | 0 / 0 | 0 / 0（原生实例化路径） |
| 可见像素 | 1250386 | 1610397 |

### 尸体场景
- 64 尸体：普通 1.568 ms（draw1/fallback0，修改前 1.615）；光影 8.013–8.113 ms（draw2/fallback0，修改前 8.078–8.191）。
- 256 尸体（-D 文件开关 `lobecorp-bench.properties: corpseRenderCount=256`）：光影 client-1792949419160，帧均 8.428 ms/P99 10.590 ms，static_drawn 256、fallback 0、draws 2；普通 client-1792953137469，帧均 1.986 ms/P99 4.382 ms，static_drawn 256、fallback 0、draws 1。
- 256 尸体轮 Horde 段与 64 轮同源但单位页仍是修复前版本；尸体指标独立，不受影响。

### 中间试验（不作为验收）
- A1（中136/远48）世界 GPU 15.87；A2（中136/远24）15.40；A3（中88/远24）13.86；A4–A6（中112/远24，但单位页列片缺陷）14.09–14.65 且单位历史只覆盖首列。A4–A6 的 summary 保留用于对照，最终配置以 A7–A9 为准。
- 00:59–01:08 另一并行 Photon 工作区基准占用了同一 GPU/窗口，期间启动的一轮样本终止并丢弃，不影响上表（上表各轮均为独占采样）。

### 证据与限制
- 原始 summary、GPU 捕获、JFR、阶段 JSONL、截图及 SHA256：`build/agent-backups/20261026-runs/`（iris-1/2/3、iris-A4…A9、ordinary-1/B2、iris-A6-256 尸体）；修改前源码/着色器备份：`build/agent-backups/20261026-horde-opt/`。
- 本轮未完成：E4 换包/重载/窗口变化；区域 3 格净空与平滑退出的游戏内定量（俯视截图未形成可测净空，仅契约单测通过）；首帧≤100 ms（STABILIZE 仍 216–341 ms）；LOD 外观美术接受。真实事件已取得发光/近中远/动画/镜头移动部分证据。不能以本轮 PASSED 或数值代替这些门禁。
- 世界 GPU 为 GameRenderer 30 s 独立捕获均值，帧间隔为完整 MEASURE；两组窗口不同，不能相减或相加做成本分摊。`expand/copy` 为整 draw 内阶段和，跨版本单次轮换样本不可直接比较。

### 2026-10-26 游戏内真实事件功能取证（Debug + Minecraft MCP，补充而非替代 A/B）

- 环境：Debug 启动的 Client、MCP 端口 19876、隔离世界（创造模式、命令可用）；用 `/lobecorp horde start lobecorp:midnight_cleanup 161.5 56 3.5 -158.5 56 3.5 20000 96` 启动真实服务端事件，id `cad5c81e-4f36-4724-a96f-c848a9c4fedd`，`/time set night` 置于夜间。
- 观察：debug overlay 全程 `events=1`；submitted 与 GPU instances 相等并随波次变化（3180→4558→7538→9364/9359/9484），batches 56→154，lightSections 153→476，gpuCopy=0、失败未见。
- Debug 求值：`HordeVisualConfig` 动画帧/LOD/ENABLED=`4/true/true`；`HordeGpuRenderer.statistics()` 为 `modelBuilds=3, irisMiWords=16, irisSlots=2, lightSections=374`；`HordeClient.batches()` 合计 9434，说明三档模型与双缓冲工作集在真实事件路径建立。
- 画面：夜间单位红色发光可见；近/中/远三档取景均可见单位；同机位 650ms 两帧位姿变化而计数稳定；镜头 x=110→60 移动后单位持续可见。未做量化区域净空、E4 换包/重载与真实事件 256 尸体。
- 回归单测：IDEA JUnit 配置 `HordeRegionTest`（含 `bodyGapKeepsThreeBlockClearanceWithoutExpandingHeight`、`interruptedEntryAndExitRemainContinuous`、`integerRegionCoversNegativeCoordinatesAtEverySubmissionTier`）、`HordeSpatialFieldTest`、`HordeRulesTest`、`HordeCleanupTest`（200tick 尸体规则）、`HordeOptimizationTest` 均退出 0，未出现失败；它们是契约级证据，不替代游戏内区域定量观察。
- 限制与清理：期间一个用户临时断点（ClientRuntimeEvents:95）多次暂停线程导致截图超时，采用会话级断点静音后恢复 false，未删除用户断点；事件用 `/lobecorp horde clear` 清除、平台方块回填空气、测试牛清理、保存退出到标题，Debug 会话停止，游戏进程退出。截图与说明：`build/agent-backups/20261026-runtime-event/`。

## 2026-10-25极端优化实机测试与历史对照

**结论：本轮整体验收不通过。** 普通与光影两轮BenchMod最终summary均为三个场景PASSED、workloadCorrect=true；这证明已有流程/计数断言通过，不证明预期负载进入画面。两轮horde-render截图均近乎天空，不能把下面较低耗时认定为有效优化收益；光影P99和GPU均值也未达到60FPS合同。没有修改生产代码来“修好”本轮数据，没有把旧数据或Debug暂停后的FPS混入基准。

### 执行与证据

- 代码：HEAD 11f7721971eb0c02c5ff39ed631e3ef6bdf26dde，包含c21899d优化；保留原通用构建号闭包WIP。两轮都经IDEA现有BenchMod Client启动，正常完成，Gradle日志分别BUILD SUCCESSFUL in 3m8s、3m；API启动返回未给最终退出码，不补写exitCode=0。
- 普通：client-1792939439969，2026-10-25 14:43:59.969～14:46:23.971 UTC；光影：client-1792939667496，14:47:47.496～14:50:10.654 UTC。北京时间分别22:43～22:46、22:47～22:50。每轮含64真实清道夫、10000虚拟浪潮、64本地尸体。
- 环境：MC26.1.2、NeoForge26.1.2.100、Sodium0.9.2、Iris1.11.4、BenchMod0.1.5-beta、JBR25.0.2、RTX3060 Laptop/OpenGL3.3/NVIDIA616.64。光影为现有ComplementaryReimagined_r5.9.3；只切隔离Bench的enableShaders，不改Shader ZIP、Sodium选项或全局配置。
- 2560×1494，视距/模拟距离12，VSync关、260（本版不限帧）；各GPU捕获首尾均focused/maximized=true、iconified=false、throttle=NONE。独立捕获分别20/30/28秒，完整MEASURE约31/60/30秒；窗口不同，不能相加/相减推CPU或GPU占比。
- 夹具：当前宽512、直线相对x+160→-160，读取配置-1在夹具回退10000，4帧、显式三档均启用。实际相机记录为x=-11、y=132.645、z=-424、yaw0/pitch32.211；实际画面异常的根因尚未确认。不能把“配置相机字符串”当实际屏幕负载正确的证明。
- 归档根为build/agent-backups/20261025-runtime-compare/：ordinary-1/summary.json与gpu/保存首轮最终报告和三个独立GPU捕获；iris-1/report/保存光影summary、GPU、3份JFR、samples、screenshots、stages及Spark链接，17份bundle非日志制品及从raw补齐的尸体环境制品均由IDEA逐文件归档；iris-horde-jfr-analysis.json为离线解析；runtime-results.json和archive-hashes.txt保存检查摘要及哈希。前后配置逻辑文本、文档原文和Debug截图同目录。光影summary中10份带注册SHA的制品均已核对匹配；早期逐文件归档跨collector窗口误入普通目录的光影尸体环境副本已隔离到collector-overlap，不当作普通证据。
- **取证缺口**：首轮归档未在下一次collect覆盖前完成，普通轮JFR、完整阶段JSONL及独立截图文件未保全；首轮截图已实际查看且哈希在summary中，但不能提供现存文件链接。保留普通summary和原始GPU逐帧数据，不伪称完整原始归档。IDEA对忽略目录返回过时summary后，已以磁盘最终运行ID/时间为准，旧TIMED_OUT报告不属于本轮。
- 首轮启动API未提供可验证的游戏PID；第二轮游戏PID50224及启动22:47:12.596已通过命令行/父进程核对。Debug实例PID28480、启动22:53:29.338、端口19876，见后文。不得把共享Gradle守护进程34944当游戏关闭。

### 当前数值（ms，完整MEASURE）

| 场景 | 普通帧数 | 普通均值 / P95 / P99 | 光影帧数 | 光影均值 / P95 / P99 | 独立GameRenderer GPU均值，普通 / 光影 |
| --- | --- | --- | --- | --- | --- |
| 64真实清道夫 | 6610 | 4.689 / 6.364 / 8.901 | 2466 | 12.572 / 19.401 / 23.002 | 1.333 / 10.268 |
| 10000虚拟浪潮 | 16876 | 3.555 / 4.805 / 9.960 | 3762 | 15.945 / 17.719 / 31.673 | 2.110 / 15.578 |
| 64静态尸体 | 13855 | 2.165 / 3.420 / 4.268 | 3963 | 7.572 / 8.586 / 9.151 | 0.603 / 7.184 |

浪潮MEASURE计数全程10000、累计绘制失败0；尸体全程64、static_drawn64、fallback0，普通draw1、光影draw2。尸体光影截图中可见8×8布局，证明本夹具的64静态尸体路径实际工作；不能外推所有尸体类型、256场景或180格服务器跟踪。普通世界GPU样本2750、光影625；光影尾部取消1个查询，不当作零。实际模型/字节与统计结果另见下文。

### 与之前三轮对照

| 版本/条件 | 浪潮帧均 / P95 / P99 ms | 独立世界GPU均值 ms | 可比性 |
| --- | --- | --- | --- |
| 修改前普通 client-1792899914313 | 3.290 / 4.215 / 4.866 | 2.875 | 旧路线、全精度14帧 |
| 本轮普通 | 3.555 / 4.805 / 9.960 | 2.110 | 新512直线/4帧/远档，截图异常 |
| 修改前光影 client-1792900642330 | 34.967 / 36.100 / 36.939 | 34.438 | 旧路线，活动区域7、光区段714 |
| 修改前干净光影 client-1792902465754 | 56.359 / 74.134 / 75.769 | 47.593 | 同旧路线但波动，区域11、区段624 |
| 本轮光影 | 15.945 / 17.719 / 31.673 | 15.578 | 区域13、区段2740，全远档且画面异常 |

本轮光影帧均数值较旧光影两轮低约54.4%/71.7%，普通帧均数值高约8.1%；这些仅为跨版本数值对照，**不能作为因果收益或“优化提升百分比”**。路线、动画采样、LOD、区域、光照覆盖和实际屏幕内容同时变化；旧世界/阶段窗口还不同。旧GPU阶段仅轮转单次工作集，新阶段为完整draw内求和，禁止用1.049ms旧expand与6.513ms新expand直接判回退。旧场景64真实清道夫普通9.054ms、光影10.570/14.571ms也有波动，没有严格A/B，不归因本次尸体/浪潮优化。

### 阶段、负载与合同检查

| 浪潮GPU阶段，整draw内累计均值 ms | 普通 | 光影 |
| --- | --- | --- |
| regions | 0.011 | 0.017 |
| terrain | 0.013 | 0.025 |
| spatial | 0.095 | 0.206 |
| units | 0.831 | 1.181 |
| motion | 0.128 | 0.146 |
| expand | 不适用 | 6.513 |
| copy | 不适用 | 0.762 |
| base | 0.344（含发光） | 0.735 |
| glow | 已含base | 0.331 |
| unit_stats | 0.081 | 0.084 |
| 独立Horde draw elapsed | 1.504 | 9.995 |

光影JSONL的3759个MEASURE周期中，完整阶段和有效1253个、均值10.0003ms；独立draw有效1252个、均值9.9952ms，跨组统计差约0.051%，低于15%覆盖审计阈值。它们是不同帧组，不能逐帧精确守恒。无重复/漏绘帧、GPU查询errors0；世界捕获仍与阶段/MEASURE时窗不同，不能将15.578-9.995直接当“其他系统成本”。

- 光影passes15、Iris copies2，工作集16MiWords、双缓冲、无缩档；自有工作集268435456字节。每帧另有1次786432字节单位历史复制，不能在总传输分析时省略。
- 每帧展开176万顶点、Iris逻辑复制约98.570MB；旧为520万顶点/约291.34MB。当前10000实例全远档（155～157个块，近/中档计数0），减少工作量主要包含模型档位变化，不是消除全部复制，更不是PCIe带宽实测。
- 全精度520→520（隐藏面0%）；中档304、远档176，默认4帧、原周期0.6667秒。三档CPU动画数组49920+29184+16896=96000字节。以历史同模型单全精度14帧174720字节计算，约少45.1%；不能用单档14→4的71.4%声称完整三档或全系统内存下降70%。
- 光照CPU按tick取最近draw：普通均值0.128/P95 0.178ms，光影0.277/P95 0.959ms。光影light P95≤0.5ms门禁未过，且覆盖2740区段，不能与历史714区段作严格单变量比较。
- GPU约束归约3756/3759周期完整、errors0；完整样本最大穿透0microblocks、单周期拒绝最多850次，尾部3周期未知。它只证明本次GPU状态查询，不能替代高速移动/多层/瞬移/画面的完整分割验收；Bench tick指标未导出对应数值不代表GPU JSONL没有数据。
- E1均值≤16.7ms通过该数值条件，但P99=31.673>20、世界GPU均值15.578>15，整体未过；copy/draw约7.6%也未达到<3%。copies≤5及passes≤20有本轮数值，其他A/B降幅、首帧≤100ms、256尸体/多事件/重载/三轮±5%等没有完成。本轮普通STABILIZE最大帧1.320秒，不能宣称冷启动卡顿解决。

### JFR、日志与判断层级

**事实**：只离线解析已保全的本轮光影浪潮JFR；以GPU开始UTC为近似MEASURE首帧、窗口60秒，边界可有约一帧偏差并可能混入少量首尾工作。ExecutionSample/NativeMethodSample均20ms，Render thread分别453/530样本，采样类别独立，不相加当CPU占比。原生叶热点353条匿名MethodHandle实际栈为glfwSwapBuffers→GlDevice.presentFrame→RenderSystem.flipFrame；另有glBindFramebuffer53、glFenceSync25、删纹理25、nglReadPixels4条。它们不证明PCIe回读或单一驱动根因。

**事实**：最靠近叶子的Horde Java栈含updateSection47、extract30、prepare22、requiredSections19、loaded19。分配采样估计权重约3.58GB/窗口，其中HordeLightField lambda约1.167GB、HashMap.Node约338MB、TextureViewAndSampler约183MB、Long约151MB、GpuBufferSlice约89MB、UnitState.Tile约42MB；这是估计分配而非存活/泄漏。updateSection仍有无DataLayer天空光256列回退查询，requiredSections仍在候选循环创建捕获level的谓词。

**证据支持推断**：本负载的GPU阶段主压力在expand，单位历史也有显著成本；copy约0.76ms不是唯一瓶颈。光照区段/加载集合及回退查询仍值得优化，新增编号页/批对象/描述对象分配不能忽略。GPU探针记录1875～1920MHz、122.67～130.68W、75～82℃、利用率97～100%、显存使用3404～3546MiB（设备总量），支持GPU忙碌判断，不能证明热降频或本模组独占显存。

**待验证假设**：基准空画面涉及相机/投影/viewport/深度状态、单位位置或光照剔除中的哪一环；拒绝步进较多是否造成停滞；LOD代理在移动镜头中的可接受性与跳变；光影换包/故障回退效果。没有通过改相机、关LOD/发光或减少数量来让本轮通过。

**日志事实**：光影本轮出现7条Missing program：region_update、terrain_update、spatial_index、unit_update、motion、iris_vertices、unit_stats；旧6条名单不同，不写成“已清除”。另有HordeHeights未使用sampler提示、既有光影变量/块映射警告。普通有Spark CancellationException，Windows实际built-in Java；两轮run_complete后仍见指挥家GUI恢复异常，执行器拒绝/保存错误与测量分开。错误计数0不等于无兼容或退出异常。

### IDEA Debug与Minecraft MCP功能取证

正常Client配置启动Debug会话705ecc9a-2cb0-4dfa-943f-2d45870b6a74，PID28480，真实gameDirectory为本工作区run/client，MCP端口19876。聊天内通用Minecraft工具ping未连接本端口；经读取固定桥接实现，使用同一MCP Mod的官方/api/status、/api/cmd接口限定19876检查，返回type=minecraft-mod、pid28480和pong，未改全局MCP配置，未连接19877/19878。

- 按项目规则先在Render thread调用GLFW最大化，随后Debug读回GLFW_MAXIMIZED=1；MCP控制true。GUI按当前screenId/widgetId经Create Test World创建Horde runtime compare 20261025，没有进入用户原有世界。
- 实际命令创建48宽事件00f6d9eb-02c7-43d1-9025-bcb438f9d149并从服务端日志核对，MCP/Debug确认事件及2886提交实例；普通无光影路径gpuCopy0。地面视角和80格旁观视角截图均看到浪潮，缓存4帧和520/304/176顶点三档与基准一致。截图中中远档出现明显块状/四面体代理，不能把它写成视觉质量已通过；没有完成连续录像、3格净空/地形/粒子/尸体10秒清理的专项验收。
- event clear后保存退回标题屏；退出控制读回false，再在本人临时断点调用Minecraft.stop并继续执行，正常关闭。四个新建106行断点均按ID删除；原有105行和异常断点保留。IDEA会话列表为空、进程只读核对无Horde游戏候选；共享Gradle/IDE/其他任务程序未结束。
- Debug截图与暂停后的FPS只作功能取证，不放入性能表。自动探针输出均cleanup_complete、closed/worker_terminated为true；离线JFR工具已退出，没有留下游戏、桥接或探针服务。

### 后续处理顺序与维护

具体问题只在[horde-issues.md](horde-issues.md)维护。处理顺序：先修基准可见性门禁并核对空画面根因，再验证LOD画面；随后对同夹具做光照分配/天空光回退、expand及单位历史的单变量A/B。对应入口为HordeRenderScenario.verify（约270行）、HordeGpuRenderer.drawUnitHistories/IrisVertices、HordeMeshReduction、HordeLightField.requiredSections/updateSection、HordeLightCoverage.loaded、HordeUnitState.prepare。当前报告不授权额外改代码，也不把当前profile直接变成生产修复。

源码定位：[基准断言](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java)、[GPU历史/展开/复制](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)、[隐藏面与闭合代理](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshReduction.java)、[光照候选及天空光回退](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java)、[单位编号页](../../src/main/java/org/unitego/lobecorp/client/horde/HordeUnitState.java)、[约束标量归约](../../src/main/java/org/unitego/lobecorp/client/horde/HordeUnitStats.java)。

测试结束恢复隔离Iris原true标志及原注释时间戳；Bench options归一化换行后的完整文本与开始一致，未声称字节哈希完全相同。NeoForge自动补入9项新配置，原文本备份保留，不删除引擎合法新增项。Shader ZIP/Sodium未手工修改，但本轮未记录它们开始哈希，不补造前后哈希证明。


## 2026-10-25极端优化夹具与计时更新（未运行）

- lobecorp.horde-render复用既有入口：直线x+160→-160、宽512；读取maximumVisible，-1仅在夹具回退10000，正值原样，非法非正值失败。环境制品记录端点/相机/资源各档顶点与字节/缺失高度样本，不移动相机凑门禁。
- 新增lobecorp.corpse-render，默认64尸体，可用-Dlobecorp.bench.corpseRenderCount=256改256；600tick测量，GPU28秒明确为子窗口。固定布局、实际静态批绘/兜底计数、失败基线和对象归属均记录；只清理自身实体/捕获/探针并恢复原姿态。
- Horde schema3：render_frame_id共同关联；WORLD/STAGE_SUM/HORDE_DRAW_TOTAL三组非嵌套，重复/漏绘标失配。独立draw不含extract预热，15%为覆盖审计，阶段和不能逐帧与世界相减。新增UNITS、UNIT_STATS、各LOD块数、自适应缩档、历史复制及穿透/拒绝覆盖；未知不写0。穿透counter单位microblocks，除1e6为格；GPU标量尾部未完成保留null。
- FULL_STAGE_SUM GPU指标按完整draw row累计；CPU/工作量Bench指标按tick取最近一次draw，层级父阶段包含子阶段，不能相加。Iris展开复制、单位历史复制、8字节统计回读是不同通道；新增统计自身耗时包含在draw中。
- 本轮只执行IDEA BenchMod Compile（退出0）与无窗口Data（退出0），未运行任何上述场景、JUnit、客户端/服务器基准或Debug；没有新性能、光影或画面数据，不写PASSED。Data日志为C:/Users/wang_/AppData/Local/JetBrains/IntelliJIdea2026.2/tmp/ij_run__Data_16117074889087978056.log；生成23份实际资源无删除，仅两份语言及两份缓存变化。
- 各任务代码/回滚/门禁详见[执行文档第9节](horde-extreme-execution-plan.md#9-2026-10-25-本轮执行记录代码已写入运行门禁未执行)；全部剩余问题只维护[horde-issues.md](horde-issues.md)。


## 2026-10-25 Sodium联合诊断（只读）

本轮已重新获得普通/光影诊断授权，采用现有隔离BenchMod Client，未修改代码。三次summary均两场景PASSED、workloadCorrect=true，但不等于兼容性、收尾和性能达标。Sodium0.9.2与Iris1.11.4实际加载，JBR25.0.2、RTX3060 Laptop/OpenGL3.3/NVIDIA616.64；2560×1494、视距/模拟距离12、VSync关闭、260不限帧，捕获窗口全部聚焦/最大化/限帧原因NONE。

| 运行 | 64真实实体帧间隔均值/P95/P99 ms | 10000虚拟实例帧间隔均值/P95/P99 ms | 虚拟GameRenderer GPU均值 ms |
| --- | --- | --- | --- |
| 普通 client-1792899914313 | 9.054 / 14.216 / 29.149 | 3.290 / 4.215 / 4.866 | 2.875 |
| 光影 client-1792900642330 | 10.570 / 13.456 / 17.763 | 34.967 / 36.100 / 36.939 | 34.438 |
| 干净光影复测 client-1792902465754 | 14.571 / 21.722 / 27.044 | 56.359 / 74.134 / 75.769 | 47.593 |

完整MEASURE为31/60秒，GPU独立捕获20/30秒，不相加/相减做成本分摊。区域数11/7/11、索引级别3/1/3、区段714/714/624，世界状态不完全一致；光影波动显著，不能只选快的一轮或当作严格单变量。两次光影每帧展开520万顶点、逻辑复制约291MB，GPU轮换计时仍只是一处调用。JFR Render thread的Java/原生测量样本为普通614/409、光影176/631、复测197/580，均20ms周期；Swap链、光照集合/分配及真实实体GeckoLib/Iris写入热点已与代码对照。粒子计数全0，不构成粒子性能验证。

事实、推断、待验证假设、逐项优化建议、代码位置和参考文献统一写在[唯一问题页的2026-10-25诊断节](horde-issues.md)，本页不另建待办。Iris每轮六条Missing program在测量前；退出仍有指挥家GUI/执行器/保存异常。截图浪潮可见，不能证明一万单位全部可见。没有单独归因为主机回读、温度或Sodium本身；GPU频率/功耗/竞争没有同步采集。

归档根：C:/Users/wang_/AppData/Local/Temp/horde-sodium-diagnosis-20261025-6d507349；ordinary、iris、iris-clean保留summary、JSONL、JFR、GPU、截图与日志，根目录保存同口径聚合JSON及只读JFR分析工具。初次大文本JFR导出已中止并删除，原始记录未改；为隔离导出收尾竞争另做了干净复测。没有修改模组代码，864份源码/着色器指纹未变。手工仅切换隔离Iris开关，当前Iris/Sodium选项/options.txt哈希与原值一致，Shader ZIP未变；NeoForge自动纠正隔离lobecorp-client.toml已记录，不假称该文件恢复原哈希。没有进入或改动run/client用户世界。

当前导航见[Horde 总览](horde-index.md)，全部未解决问题只在[问题汇总](horde-issues.md)维护。工作区为 `.workspace/lobecorp_horde`、分支 `codex/horde`。本文保留基准操作说明与合并后的必要历史证据，不继续维护已结束的接入过程。

同步日期：2026-10-22，horde-recovery恢复工作区；来源为原始dev提交278c418。上游：[zhongbai2333/BenchMod](https://github.com/zhongbai2333/BenchMod)，固定 JitPack 0.1.5-beta，MIT 许可。采用其 Minecraft 26.1.2／26.1 适配线；本项目保留 NeoForge 26.1.2.100、ModDev 2.0.142、Java 25 与现有 Gradle wrapper，不为安装降级。上游基线为 NeoForge 26.1.2.76，具体本地兼容性以本次实际编译和运行结果为准。

## 2026-10-24 追加优化检查（游戏测试已取消）

本轮没有运行光影开启或关闭的游戏基准，没有新增FPS、GPU均值或P95数据；用户先要求对照运行、随后明确取消，未启动或接管游戏。实际完成为13份Java与9份GLSL零错误诊断、必要引用核对、编译、23项无游戏回归和Data；29份含缓存生成文件SHA256不变。检查原始结果为horde-spatial-smooth-20261024-124157-e4bdef48/checks.json，编译/Data日志在同一备份目录。历史下表不作为本轮验收。

## 2026-10-24 分阶段统计（本轮不启动游戏）

HordeRenderScenario在setup记录累计失败基线，在STABILIZE/WARMUP/MEASURE/VERIFY逐阶段检查序号变化，任何已记录的GPU绘制异常都会失败，不依赖每tick恰好读到失败帧；此保护已有CPU回归，真实基准未运行。setup显式开启HordeRenderMetrics，teardown先保存artifacts/stages/horde-stages.jsonl再恢复原场景；STABILIZE超时也会保存已经捕获的周期，无需先进入MEASURE。该收尾钩子路径已写入，CPU无游戏回归已确认STABILIZE样本及零帧文件能落盘、缺测为null；真实BenchMod超时收尾未运行。本轮只允许无游戏检查，不运行BenchMod Client/Server，不将源码接入当作运行产物已生成。

JSONL首行为schema/容量及GPU查询状态，随后最多65536行，每行一个Horde绘制周期，包含当前阶段、最近extract、各阶段CPU纳秒/调用数、工作量和可缺失的GPU样本。阶段为prepare/light/regions/terrain/spatial/iris_prepare/motion/expand/copy/base/glow，extract单列；prepare包含其内部区域更新，CPU不能跨阶段直接相加。非Iris基础/发光共用原管线，记为base，glow单独阶段不适用。

GPU按阶段轮换，每周期至多采一处调用，记录调用序号与work_units；regions为槽位数、terrain为地形样本数、spatial为粗档有效字数、iris_prepare为一次缓存的帧字数、motion为实例、expand/base/glow为展开顶点或实例、copy为字节。它不是完整帧的阶段总和，不能直接与整帧GPU时长相减。原版TimerQuery忙时跳过，待决最多128，结束不等待，未完成取消；缺测写null，跳过/取消/错误另计。CPU时间包含查询开销，查询缺失时仍保存CPU和计数。

计数区分logical_batches/groups/passes/draws/copies/copy_bytes/expanded_vertices/regions/light_sections/light_slots；新增spatial_words/spatial_levels为各级有效纹理字数/提交级数，不是GPU命中数，iris_frame_words为一次资源帧缓存字数。particle_areas/particles为最近客户端tick选区/提交数，会在多个绘制周期重复，不能逐帧相加当作出生总量；原版引擎仍可按用户粒子设置筛选。light_coverage_builds与light_column_queries为该周期新增工作量。upload_bytes统计当前批次参数、区域目标/参数、地形和索引参数上传，不包含首次模型、全量图集、静态线路或其他模组开销。BenchMod的MEASURE阶段每tick补记最近绘制周期CPU/计数，逐周期完整记录以JSONL为准；不得将重复最近帧的tick样本误当独立渲染帧。容量满后停止增加行，明确标CAPACITY，不静默覆盖。额外统计内存仅显式捕获期间分配，结束释放引用。

## 2026-10-24 前轮代码复测

以下数据来自1.2格间距及旧区域流场。本次追加改为1.8格、小范围连续分割、个体随机运动和单目标上限10；用户明确本次不启动游戏，尚未重跑客户端、服务端或GPU基准，不能把下表当作最新代码验收。

全部运行针对当前工作区HEAD 7c1859f加未提交修改，Sodium0.9.2、Iris1.11.4、Java25.0.2、RTX3060 Laptop/NVIDIA616.64；普通和隔离配置的纵横间距已明确调整为1.2，旧事件快照不强制覆盖。当前两轮无光影基准均退出0、summary为PASSED，两场景数量验证通过。没有以索引查询干扰测量窗口。

| 运行 | 64真实实体 间隔均值/P95/P99 ms | 10000虚拟实例 间隔均值/P95/P99 ms | 虚拟GameRenderer GPU均值 ms |
| --- | --- | --- | --- |
| 基线007ff470 | 5.83 / 8.18 / 10.73 | 3.84 / 7.60 / 8.21 | 5.08 |
| 中间轮55b9b619（旧密度/测量有干扰） | 12.04 / 14.88 / 17.40 | 3.38 / 7.25 / 8.74 | 2.40 |
| 当前de012627 | 4.84 / 7.64 / 11.57 | 2.41 / 3.12 / 3.87 | 2.08 |
| 当前重复8b660c93 | 4.31 / 6.19 / 8.56 | 2.30 / 3.20 / 4.41 | 1.93 |

固定条件：2560×1494、最大化且前台、Vsync关闭、配置上限260、视距/模拟距离12、seed7，GPU捕获首尾throttle_reason为NONE。每个64实体场景测31秒，虚拟测60秒；GameRenderer分别捕获20/30秒。当前两轮虚拟Render CPU均值1.11/1.21ms、渲染线程分配均值234022/222013字节每帧；真实实体GPU1.34/1.26ms、Render CPU3.34/3.06ms、分配1695878/1833549字节每帧。首次/稳定期仍存在224/162ms最大帧。

口径限制：BenchMod事件和独立捕获窗口不同，不能混用帧数推导某阶段GPU耗时；配置上限260与计数的表观帧率不构成实际稳定FPS承诺。截图大量天空，只有部分提交单位在画面中，不能说10000个全部可见。线路、密度和缓存行为改变，表格是实际运行记录，不是单变量因果实验。此前64实体回退未复现，不据此删除所有CPU性能异议。

Iris硬件复验使用同一独立测试世界、ComplementaryReimagined_r5.9.3。修正36/56字节布局后100及10000单位可见，夜间虚拟遮罩发光可见；10000实例30秒442帧、平均间隔67.98ms/P95 85.74ms、GPU67.89ms，约14.71FPS，每帧GPU复制292192256字节。Vsync开启/120上限、前台最大化、throttle_reason=NONE；该环境和无光影夹具不同，不能直接将差值归因单一部件。剩余GPU瓶颈见H24。

本轮BenchMod Server（2cd82417）退出1，ReRetro Damage Indicators在专服加载MultiBufferSource失败，同时有Photon/LDLib反射Particle缺类；没有生成server summary，未取得服务端性能数据。没有擅自移除第三方依赖。客户端收尾重复出现ConductorControls鼠标恢复异常，场景PASSED不代表退出无异常。

完整结果分别保存在C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d/baseline-client、after-client-1/2/3，包含summary、报告、原始样本、JFR、GPU捕获和截图；Iris报告为同根iris-layout56-10000.json，专服失败证据在server-start-failure。所有未解决项仅维护于horde-issues.md。

## 隔离与入口

settings.gradle.kts 映射 com.zhongbai233.minecraft-bench 到 JitPack 插件模块；版本统一放在 gradle/libs.versions.toml。JitPack 仓库仅允许 com.github.zhongbai2333.BenchMod 组。API 与 Runtime 由插件成套注入 benchImplementation／benchRuntimeMod，不加入普通 implementation 或 runtimeOnly。

测试源码只在 src/bench，按 config／registry／world.entity／client 分包。ServiceLoader 文件注册 LobecorpBenchProvider，显式 targetMod=lobecorp，expectedProviderCount=1。生产源码不依赖 bench；插件的 verifyProductionJarHasNoBenchContent 用于检查生产包隔离。

所有测试运行目录位于当前工作区 build/modBench/runs/default，报告位于 build/modBench/raw-results/default/server 或 client。不会复用 run/client 的用户世界、账号或 MCP 端口，也不复制其他工作区构建产物。普通 Client 的开发调试 JVM 标志不注入 bench 运行。

Gradle／IDEA 入口：

| 入口 | 任务 | 行为 |
| --- | --- | --- |
| BenchMod Compile | compileBenchJava、verifyProductionJarHasNoBenchContent | 编译基准代码并检查生产包隔离，不运行场景 |
| BenchMod Server | verifyBenchServer | 启动真实隔离专服、运行并验收服务端报告 |
| BenchMod Client | verifyBenchClient | 启动真实隔离客户端、运行并验收渲染报告 |

这些运行说明不代表任务已经执行。真实 Server／Client 基准须取得当次运行授权。插件每次运行会清理对应旧报告；做版本对比前先保存完整结果目录。普通 check 不等于已经运行真实基准。

## 首批场景

参数集中在 LobecorpBenchConfig：固定 seed=7，默认64个普通清道夫，8×8网格、间隔3格，稳定40tick、预热至少60tick、测量620tick（约31秒）；每阶段超时120秒。客户端另等待20个稳定帧，截图后再留恢复窗口。没有预设“5000单位60FPS通过”等结论或性能门槛。

- lobecorp.sweeper-ai：在服务端通过真实注册类型创建64个清道夫，保留AI，使用确定性UUID、随机种子与外观变种；只新增所需区块的强制加载票据。记录server.tick.duration、实体数量和实体tick累计值，检查实体身份不变且测量期间确实参与tick。未主动布置玩家、战斗目标或清道夫事件负载；没有更改自然生成规则，比较时仍需保持隔离世界初始状态一致。不能等同于实战、群体事件或5000单位结果。
- lobecorp.sweeper-render：仅在客户端创建64个本地清道夫渲染实体，关闭AI与重力，使用保留的负数实体ID并在占用时失败。使用原有SweeperRenderer／GeckoLib渲染路径与固定相机；预热阶段取得sweeper-render.png，再测量真实client.frame.interval和帧样本数量。它不测网络同步或服务端AI，帧间隔也不是GPU执行时间。实体数量检查不等于图像内全部可见，首次运行仍需复核截图。
- lobecorp.horde-render：客户端构造固定线路GPU快照，10000个实例、宽128；测量1200tick（约60秒），首个测量tick开始sparkc 30秒采样。检查全程实例数量、事件资源失败、截图与Spark链接；报告登记horde-render.png、JSONL、JFR和artifacts/spark/horde-profile.txt。原有客户端配置、相机和本场景事件在清理时恢复；不测真实参战或网络。运行窗口默认2560×1494，支持benchWindowWidth/benchWindowHeight启动尺寸属性；场景开始前最大化并激活窗口，测量中不更改窗口状态。
- 清理仅移除各场景拥有的实体、释放本次新加的强制区块票据或相机保持句柄；不会清空世界实体或移除既有强制加载票据。客户端生命周期不阻塞等待future，截图通过后续tick检查。

客户端保留上游严格环境门禁：失焦、最小化、暂停、界面打开、窗口变化等会使结果不可比，不以降低门禁制造成功。截图、JSONL、JFR必须登记在权威summary.json中；构建成功不代表真实游戏、图形或性能验收成功。

2026-10-23：horde-render已复用生产侧平滑闭环与HordeRoute.prepare的粗高度算法；客户端采样器只提供已加载地面。Spark取证排除本次命令启动前已有的聊天链接。基准源码编译与生产包隔离通过，但未重新运行真实客户端场景，下述旧数据不能当作新夹具性能结果。

## 2026-10-23 修改前实测基线

基线为7c1859f及现有未提交工作区；BenchMod Client退出0，权威summary.json为PASSED，两个场景通过。Sodium0.9.2、Iris1.11.4、RTX3060Laptop、NVIDIA616.64、Java25.0.2，2560×1494、视距/模拟距离12、VSync关闭、帧率上限260；实际窗口最大化且聚焦，未开启光影包验收。

- 64实体：5320帧，平均5.83ms，P95 8.18ms，P99 10.73ms。
- 10000浪潮实例：15631帧，平均3.84ms，P95 7.60ms，P99 8.21ms，数量全程10000；稳定阶段最大555.17ms，测量阶段最大50.08ms。
- 独立30秒GameRenderer采样：GPU1719样本、均值5.08ms/P95 6.66ms，Render CPU均值1.39ms，每帧渲染线程分配平均约203KB。GPU计时仅涵盖GameRenderer.render，不是浪潮单独耗时；30秒与60秒测量窗口不同，不能混算FPS或归因。
- 完整报告、截图、JSONL、JFR、Spark与GPU结果已保存到C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d/baseline-client，原始报告位于build/modBench/raw-results/default/client。后续运行前保留此副本；单轮不能证明全部批评已解决。

## 2026-10-24 修改后第一轮

同一Sodium/Iris及窗口环境，summary为PASSED，两个场景通过；源码为开放线路/1.2格默认密度/整数区域及新发光实现，工作区未提交。64实体均值12.04ms/P95 14.88ms/P99 17.40ms；10000实例均值3.38ms/P95 7.25ms/P99 8.74ms。后者平均值下降，但P99及真实实体对照退化，不宣称整体优化成功。

30秒GameRenderer样本：GPU均值2.40ms/P95 5.05ms，CPU均值3.39ms/P95 4.91ms，渲染线程分配均值约265KB；完整窗口最大化、聚焦、VSync关闭、260上限。新线路、密度、发光及采样期间IDE索引活动都是对照差异，需复测后再归因。副本为C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d/after-client-1。

基准结束后日志另有ConductorControls退出鼠标恢复异常，场景通过和退出生命周期分别判断，详见H23。配置迁移本轮已在隔离客户端实际执行，备份目录为build/modBench/runs/default/client/config/lobecorp-client-backups/migration-9580139753885649609，值保持仍需回读核对。

## 历史证据与归档

以下仅为既有记录摘要，本轮未重新运行。原始 dev 的构建、Data 与当前 horde 验收分开判断；完整阶段记录可从文档整理前备份及 Git 历史恢复。

| 阶段 | 既有证据与条件 | 使用边界 |
| --- | --- | --- |
| 2026-10-21 旧 GPU/图片图集 | 旧2000/5000单位轮平均帧间隔20.50/22.45ms，854×480、VSync开启、夜间；图集复验曾确认5000逻辑单位持续运行约195秒并正常退出 | 旧表示已移除，不作为当前顶点帧、2560×1494或稳定60FPS验收 |
| 2026-10-22 逐格高度、逐小段绘制 | 平均9.93ms、P95 13.41ms、P99 16.74ms；[Spark](https://spark.lucko.me/3N4nE67wyv) | 与后续批次/高度版本分别保留 |
| 2026-10-22 逐格高度、16小段合批 | 平均8.57ms、P95 10.00ms、P99 10.97ms；[Spark](https://spark.lucko.me/W0HnlFero9) | 单轮，含Spark采样扰动 |
| 2026-10-22 记录标为粗高度、16小段合批 | 平均10.02ms、P95 20.54ms、P99 21.46ms；[Spark](https://spark.lucko.me/HirLK4nU0D) | 旧夹具曾逐节点采高，旧结果与生产路径对应关系不能追认；本轮新夹具尚未实测 |
| 2026-10-22 顶点帧与区域检测 | 既有IDEA编译、Data和规则/区域单测记录；用户确认烘焙朝向和动画正常 | 不能扩展为更多模型、当前完整玩法或独立性能验收 |
| 2026-10-23 迁移与制品 | 迁移、端口、制品与构建边界见[工作区目录](workspace-layout.md)和[制品规范](minecraft-mcp-artifacts.md) | 配置正确不代表当前游戏已连接；完整build的test失败仍未解决 |

三轮10000实例记录使用128格宽、2560×1494、VSync关闭、测量1200tick及Spark30秒，普通64实体对照620tick。实例指标全程10000是提交负载证据，不等于所有单位图像正确可见，也不覆盖真实参战和网络。粗高度标记轮P95变差，不能宣称它提高帧率或全程60FPS。

普通客户端历史观察包括石墙不透明遮挡、夜间萤石附近变亮、铁傀儡附近让行，以及无限配置提交28944/32848实例。逐格高度阶段的56→61→56与版本0→1→2只覆盖当时高度实现，不能替代后来粗采样验收。

原报告目录为 `build/modBench/raw-results/default/client`，版本对照与失败预检查归档为 `backups/horde-validation-20261022`；旧图集和遮挡源备份为 `backups/horde-gpu-before-20261022`。2026-10-23曾只读确认这三个目录存在，未重新验证报告内容。其他旧证据入口为 `.backups/horde-isolation-20261021`、`.backups/horde-impostor-20261021`、`.backups/horde-crash-20261022`；不自动恢复旧实现或配置。

原始dev的BenchMod接入编译/生产包隔离和Data历史结果保留在[修改记录](changes.md)。恢复基线引用为 `refs/backups/before-benchmod-sync-horde-recovery-20261022/{head,index}`，旧快照为6328b11，BenchMod接入为1f30bde。已结束的安装步骤、PID和重复检查输出不再单独保留过程页；旧Debug异常、配置换行和缺失快照均转入[问题汇总](horde-issues.md)。

本轮文档合并备份：`C:/Users/wang_/AppData/Local/Temp/horde-docs-consolidation-20261023-kwqkdh3p`，含全文、SHA256清单和修改前Git差异；这份备份不是当前运行结果。
