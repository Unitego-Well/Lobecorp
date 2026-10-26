# Horde 交接文档（2026-10-26 第五轮收尾）

> 自包含交接：下一个会话从这里继续。工作区 `E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_horde`，分支 `codex/horde`；原始项目 `E:\xm\mc\mod\Unitego-Well\Lobecorp` 的 AGENTS 规则同样适用。
> 最后更新：2026-10-26 20:0x（矩阵 12/12 PASSED，代码完成；文档与提交状态见 §2.4）。

## 1. 目标与硬约束

- 用户要求**整体重写 GPU 避让**（RVO/流场代价方向，禁止在 `location()`/`advanceUnit` 上打小补丁）：每单位 GPU 速度/避让状态、玩家竖直胶囊、格点邻居分离、分割区域沿代价梯度向外；用 **500×500、2 分钟、数量 -1、玩家站 400 格地面、夜晚、面向事件反方向、第一人称不飞** 的夹具验证“从两侧绕过去、不原地停、分割区域向外”。
- 判图后追加要求：浪潮速度 **0.75 格/刻**；推进要“方向不变、提前分割、加大侧向速度”；绕行结束后要**会合**；生物刷新导致的空白要“被推/主动离开”而不是突然消失。
- 片（card）：能**发光**、**不渲染反面**、走**透明裁剪（cutout，非半透明）**、颜色正确（红色）。
- 监守者：保留**全部 AI**“模拟真实战斗”；用户澄清“**清道夫由事件本身生成**”，所以监守者轮改为启动**真实 HordeEvent**，玩家用**旁观者模式**；不允许再出现“潮还没到就有粒子/生成清道夫”。
- 分割：多个缺口并存时不能突然出现/消失、不能在空地上分割；生物分割范围 **3 → 1.5**。
- 测试矩阵：6 类场景 × 光影/非光影 = **12 轮**，每轮**重启客户端**，逐轮截图 + Spark 全程 + 焦点门禁；失焦轮=INCONCLUSIVE，只重跑、不充数。
- 文档与提交：更新 `benchmod.md`/`changes.md`/`horde-issues.md`/`horde-index.md` 与本交接；**中文提交、不推送**。

## 2. 当前状态

### 2.1 代码（全部完成并编译通过）

| 项 | 状态 | 关键位置 |
| --- | --- | --- |
| GPU 避让重写 | 完成 | `include/horde_avoid.glsl`（新）、`core/horde_unit.fsh`、`include/horde_unit.glsl`、`include/horde_motion.glsl` |
| 单位状态布局 | 12 值：位置/速度/时间/身份；朝向由速度派生 | `HordeUnitState`、`horde_unit.fsh` |
| 平滑推出/提前分流/会合 | 完成（每帧限幅、横向阻尼、1 秒尖峰容忍、elapsed≤5） | `horde_unit.fsh`、`horde_avoid.glsl` |
| 片颜色通道 | 修复 ARGB/ABGR 互换（根因 `NativeImage.getPixel` 返回 ARGB） | `HordeCardBake` |
| 片发光 | glowmask 图集，原版混合/Iris EYES 层 | `HordeCardBake.bakeGlow`、`HordeGpuRenderer` |
| 片 cutout+背面剔除 | 完成（非半透明）；透明区域透出背景属用户选择 | `HordeGpuRenderer`（card pipeline、Iris `ENTITY_CUTOUT_CULL`） |
| 多缺口修复 | 完成：目标按视距+路线过滤、缺口滞留 30 tick、走廊取最强单区域、硬推力取最深、null 防御 | `HordeClient.updateGaps`、`HordeAmbient.insideTide`、`horde_avoid.glsl` |
| 生物分割范围 | `VISUAL_GAP_SCALE=1.5`（原 3；`MIN_VISUAL_CLEARANCE=3` 不变） | `HordeClient` |
| 真实事件模式 | 完成：监守者轮 `HordeEvents.start(...)`（500 宽/6000 tick），玩家旁观者+每 5 tick 服务端锁位；监守者全 AI、Pose.STANDING/清 IS_EMERGING/长 DIG_COOLDOWN；09/10 仍合成夹具 | `HordeRenderScenario` |
| 波带覆盖修复 | 完成：`covers()` 严格 `[tail, head]` 站点判定，修复“潮未到提前生成清道夫/粒子” | `HordeEvent.covers/station` |
| 基准场景 | 完成：sweeper30-ai/noai、corpse-render(30)、corpse-random100、horde-render（合成）、horde-render（真实事件+10 监守者） | `src/bench/java/...` |

### 2.2 12 轮矩阵（最终：12/12 PASSED，焦点 losses=0，Spark 均有链接）

| 轮次 | 场景 | 光影 | 帧均/P95/P99 (ms) | 世界GPU | draw GPU | 实例/实体 | Spark |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | sweeper30-ai | 光影 | 10.394/15.857/20.258 | 8.132 | n/a | 30 | https://spark.lucko.me/uHVOy9cAtn |
| 02 | sweeper30-ai | 非光影 | 4.605/7.294/10.012 | 1.073 | n/a | 30 | https://spark.lucko.me/AY9WXVVIrl |
| 03 | sweeper30-noai | 光影 | 8.217/11.758/15.812 | 7.318 | n/a | 30 | https://spark.lucko.me/wS5ETGw8yC |
| 04 | sweeper30-noai | 非光影 | 4.313/6.900/9.094 | 0.997 | n/a | 30 | https://spark.lucko.me/YEX9uz8VMz |
| 05 | corpse-render(30) | 光影 | 6.797/9.105/11.642 | 6.249 | n/a | 30 | https://spark.lucko.me/8UqMGtGGHt |
| 06 | corpse-render(30) | 非光影 | 3.203/5.632/7.157 | 0.898 | n/a | 30 | https://spark.lucko.me/jCRifoJFIj |
| 07 | corpse-random100 | 光影 | 11.184/20.223/24.789 | 10.051 | n/a | 100 | https://spark.lucko.me/7RTylwVTzR |
| 08 | corpse-random100 | 非光影 | 5.827/9.075/11.524 | 1.673 | n/a | 100 | https://spark.lucko.me/85u342RCak |
| 09 | horde wardens=0（合成） | 光影 | 19.196/22.989/25.037 | 19.082 | 12.295 | 25,642 | https://spark.lucko.me/KyY3Bbzr0y |
| 10 | horde wardens=0（合成） | 非光影 | 6.534/9.609/12.381 | 7.486 | 5.249 | 25,640 | https://spark.lucko.me/1sXlObSY3I |
| 11 | horde wardens=10（真实事件） | 光影 | 20.814/26.035/30.115 | 20.652 | 12.882 | 25,604 | https://spark.lucko.me/Gy3e9Erk0D |
| 12 | horde wardens=10（真实事件） | 非光影 | 9.336/12.512/15.478 | 4.143 | 2.362 | 25,543 | https://spark.lucko.me/rZS0VqNJ1e |

- 09/10 为合成夹具（性能可比）；11/12 为真实 HordeEvent（事件自身生成真实清道夫，与全 AI 监守者真实战斗），**不要与 09/10 直接等价比**。
- 11/12 env：`horde_real_event=true`、`horde_workload_expected=10`、warden server/client=10、spawn_failure=0、respawn/issues≈20（审计记录，最终 10/10）。
- 未达标：09/11 光影轮世界GPU 19–21ms、P99 25–30ms，仍高于 16.7/20/15ms 目标；非光影达标。

### 2.3 证据位置

- 矩阵总表：`build/agent-backups/20261026-matrix12/matrix.md`、`matrix.csv`（最终版，含修复说明与无效尝试清单）。
- 每轮归档：`build/agent-backups/20261026-matrix12/<轮次>/`（summary/report/artifacts/config 快照/round.json/focus.json/metrics.csv/sha256.txt）；截图在 `artifacts/screenshots/`（horde 轮有 `horde-render.png`、`-off`、`pass-a`、`pass-b`）。
- 无效尝试全部保留未计入：焦点丢失（07/09/10/11/12 多次）、Spark bytebin 上传失败、warden 生成失败、NPE 崩溃、并发编译 `NoClassDefFoundError`、covers 前作废轮。备份：`matrix-20261026-1900-pre-covers.md/.csv`、`matrix-20261026-1810-frozen.*`。

### 2.4 文档与提交状态（下一个会话务必先核对 `git status`）

- 已更新：`docs/systems/benchmod.md`（第五轮节+12 行表+修复说明）、`changes.md`（第五轮）、`horde-issues.md`（I39–I44）、`horde-index.md`（本轮进度段，仅追加在 DOC-HORDE-01 之前）、本交接文档。
- **提交状态需核对**：若 `git log` 尚无本轮提交，按 §5 提交；若已提交，核对只含本次授权范围。
- 不得提交他人未提交内容：`build.gradle.kts` 的 `reserveBuildIdentity` 改动、`docs/standards/entity-contracts.md`、`horde-index.md` 中 DOC-HORDE-01/格式重排。只提交我们自己的 hunk（`build.gradle.kts` 的 `scenarioFilter` + `phaseTimeoutTicks=3600`；`horde-index.md` 的本轮进度段）。

## 3. 关键文件

- 生产：`src/main/java/org/unitego/lobecorp/client/horde/`（`HordeClient`、`HordeAmbient`、`HordeGpuRenderer`、`HordeCardBake`、`HordeRegionState`、`HordeSpatialIndex`、`HordeUnitState`、`HordeMeshReduction`…）、`src/main/java/org/unitego/lobecorp/world/horde/HordeEvent.java`
- 着色器：`src/main/resources/assets/lobecorp/shaders/core/`（`horde_unit.fsh`、`horde_motion.fsh`、`horde_instanced.vsh`、`horde_region_update.fsh`、`horde_iris_vertices.fsh`）、`include/`（`horde_avoid.glsl`、`horde_unit.glsl`、`horde_motion.glsl`、`horde_spatial.glsl`）
- 基准：`src/bench/java/org/unitego/lobecorp/client/bench/`（`HordeRenderScenario`、`Sweeper30RenderScenario`、`RandomCorpseRenderScenario`、`CorpseRenderScenario`、`BenchServerWorkload`、`BenchSpark`、`BenchWindow`）、`src/bench/java/org/unitego/lobecorp/config/bench/LobecorpBenchConfig.java`
- 运行配置：`build/modBench/runs/default/client/config/lobecorp-client.toml`、`lobecorp-bench.properties`、Iris `config/iris.properties`
- 文档：`docs/systems/benchmod.md`、`changes.md`、`horde-issues.md`、`horde-index.md`、本文件

## 4. 如何继续运行（重要坑）

```powershell
cd E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_horde
# 只跑单场景（必须排除 verifyBenchClientReport，它期望旧场景集合）
.\gradlew.bat --console=plain verifyBenchClient "-PbenchScenarios=<场景ID>" -x verifyBenchClientReport
```
- 场景 ID：`lobecorp.sweeper30-ai`、`lobecorp.sweeper30-noai`、`lobecorp.corpse-render`、`lobecorp.corpse-random100`、`lobecorp.horde-render`（horde 轮由 `hordeWardens=0/10` 切换合成/真实事件）。
- horde properties：`hordeWidth=500 hordeLength=500 hordeMeasureTicks=2400 hordePlayerStation=400 hordeSpeed=0.75 hordeWardens=0|10`；**不要** pin `entitySyncTimeoutTicks`（默认 600）。
- 光影开关：`build/modBench/runs/default/client/config/iris.properties` 的 `enableShaders`；每轮重启客户端。
- 结果：`build/modBench/raw-results/default/client/`（summary/report/artifacts）+ 归档到 `build/agent-backups/<日期>-<主题>/`，附 sha256 与配置快照。
- 坑：
  - **客户端运行期间绝对不要 `gradle compile*`**（会出现陈旧 class 的 `NoClassDefFoundError`/NPE 假崩溃）。
  - 焦点被外部程序（QQ/explorer/剪映等）抢占的轮次必须重跑，不得当作有效数据；`BenchWindow.losses()>0` 即 INVALID。
  - Spark bytebin 上传偶发 30s 无链接 → 该轮作废重跑，不能拿本地 profile 冒充。
  - 关闭客户端前必须等 Spark 链接/落盘（`Spark.requireSaved`）。
  - 不要改光影包/全局 Iris 配置；红蓝通道问题已在烘焙代码修复。

## 5. 提交（如尚未提交）

1. `git status --short` 与 `git diff` 核对范围；只暂存本次文件：
   - 生产/着色器：`src/main/java/org/unitego/lobecorp/client/horde/{HordeClient,HordeAmbient,HordeCardBake,HordeGpuRenderer}.java`、`src/main/java/org/unitego/lobecorp/world/horde/HordeEvent.java`、`src/main/resources/assets/lobecorp/shaders/include/{horde_avoid,horde_unit,horde_motion}.glsl`、`core/horde_unit.fsh`
   - 基准：`src/bench/java/org/unitego/lobecorp/client/bench/*`（新增场景与重构）、`src/bench/java/org/unitego/lobecorp/config/bench/LobecorpBenchConfig.java`
   - 文档：`docs/systems/{benchmod,changes,horde-issues,horde-index,horde-handoff}.md`
2. `build.gradle.kts` 只提交自己的 hunk（`scenarioFilter`、`phaseTimeoutTicks.set(3600L)`），用 `git show HEAD:build.gradle.kts` 造 patch 后 `git apply --cached`；`horde-index.md` 同理只提交“本轮任务进度”新增段（不要带 DOC-HORDE-01/格式重排）。
3. 提交信息中文，例如：
   `Horde 第五轮：GPU避让重写、片颜色/发光/cutout、多缺口修复与12项矩阵`
   正文列范围、12 轮 PASSED、未达标项、证据路径；**不推送**。

## 6. 已知问题 / 限制（交给下一个会话继续）

- `horde-issues.md`：I39（片颜色/发光/cutout/矩阵）、I40（避让真实场景待复验）、I41（光影性能未达标：09/11 世界GPU 19–21ms）、I42（Spark 上传偶发失败）、I43（服务端实体同步已修）、I44（多缺口分割已修待实机长时复验）。
- 真实事件模式 11/12 与合成夹具 09/10 不可直接等价比；`warden_respawn_count≈20` 为审计记录（全 AI 移动下重复补生成），最终 10/10。
- 多缺口修复只做了 09-12 重测与截图人工检查；仍需长时间移动镜头、多人/多事件、真实玩家参战等场景复验。
- 片 cutout 下透明区域会透出背景，是用户明确选择；如需“实心片”需另议。
- 性能目标仍未达成（光影 horde 轮），后续可继续优化 expand/draw/glow 或与用户确认验收口径。

## 7. 快速核对清单（新会话开始）

1. `git status` + `git log -3`：确认本轮是否已提交、工作区是否混入他人改动。
2. 读 `benchmod.md` 第五轮节 + `matrix.md`：确认 12 轮数据与证据路径。
3. 若要继续跑：先确认没有客户端/gradle 进程，再按 §4 单场景运行；**运行期间不要编译**。
4. 若要收尾：按 §5 提交，更新 `horde-index.md` 进度与 `changes.md` 检查记录，不推送。
