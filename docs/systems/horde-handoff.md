# Horde 交接：计划与当前状态（2026-10-26）

> 新对话从这里继续。本文是自包含的交接文档：目标、已完成、如何运行、已知问题、下一步顺序和证据位置。
> 工作区：`E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_horde`，分支 `codex/horde`；原始项目 `E:\xm\mc\mod\Unitego-Well\Lobecorp` 的规则同样适用。

## 1. 目标与硬约束

- 10000 实例光影目标：帧均 ≤16.7ms、P99 ≤20ms、世界 GPU ≤15ms，画面正确；不静默降数量、不关光/发光、不降动画帧数、不牺牲近景精度。
- 用户新增：远处用“6方向烘焙片组成盒子”替代简化盒；片需要动画，但按到相机**绝对距离**降帧（<10 格 20 帧、10–20 格 10 帧、20–30 格 5 帧、>30 格静止，均匀取样）；片也要完整 20 帧；配置 `cardDistance`（-1 不使用）与 `frameDistance`（-1 不降低，0 不动），全部缓存、GPU 处理。
- 只保留“完整模型 + 拼接片”，**不再生成中景/远景简化盒**（用户明确要求）。
- 当前用户指定：片默认距离 **32**；光影下片偏蓝/偏暗属于光影本身效果，**不要去改光影**，需要正常颜色用非光影组。
- 测试必须：光影/非光影各测、每项测试完成重启客户端、每项截图、Spark 全程、焦点门禁；新矩阵见第 5 节。

## 2. 当前已实现（代码状态）

| 项 | 状态 | 关键位置 |
| --- | --- | --- |
| 20 帧烘焙（所有档共用） | 已完成，默认 `animationFrames=20` | `HordeVisualConfig`、`HordeMeshCache` |
| 4 档模型（520/136/48/24） | 已实现，但**简化盒已停用**：只构建 tier0 与 tier3（tier1/2 为 null） | `HordeMeshReduction.prepare` |
| 6 方向 CPU 烘焙图集 | 已完成：`HordeCardBake`，6 方向×20 帧×64px，图集 384×1280 | `HordeCardBake` |
| 片档 UV/取样 | 已完成：tier3 UV 编码 `(方向*2+角点)/12`；Iris/原版着色器按方向+帧取 tile | `HordeMeshReduction`、`horde_iris_vertices.fsh`、`horde_instanced.vsh` |
| 片图集材质 | 已完成：`CardAtlasTexture`（夹边最近邻），失败回退普通贴图 | `HordeGpuRenderer.geometry` |
| GPU 距离降帧 | 已完成：`horde_motion.fsh` 按相机绝对距离量化帧；`FrameLod` 第 18 个 vec4（288B） | `horde_motion.fsh`、`horde_motion.glsl`、`HordeGpuRenderer.writeParameters` |
| 两档 LOD | 已完成：`selectLod` 只返回 0 或 3；`cardDistance=-1` 时只有完整模型 | `HordeGpuRenderer.selectLod` |
| 无限数量 | 已完成：`maximumVisible=-1` 不再折算 10000，按实际批次记录；无限生成中不硬断言数量恒定 | `HordeRenderScenario` |
| 玩家绕行/分割区域（GPU） | **部分完成**：`horde_unit.fsh::advanceUnit` 与 `horde_motion.glsl::location` 已重写为侧向绕行 + 左右候选搜索；实测“绕过了但没有完全绕过”，仍会拥挤/局部顶住 | `horde_unit.fsh`、`horde_motion.glsl` |
| 500×500 浪潮测试参数 | 已完成参数化：宽/长/测量 tick/玩家站位从 `lobecorp-bench.properties` 读取；夜晚 + 面向事件反方向 + noGravity + 每 tick 锁 pose | `LobecorpBenchConfig`、`HordeRenderScenario` |
| 场景过滤/阶段超时 | 已临时改 `build.gradle.kts`：`scenarioFilter` 来自 `-PbenchScenarios`，`phaseTimeoutTicks=3600`；**build.gradle 含其他在途修改，提交时需谨慎** | `build.gradle.kts` |
| 测试矩阵（10 项） | **未完成**：只做了 500×500 horde 的参数化；AI/无AI 清道夫、尸体30、随机尸体100、+10 监守者场景尚未实现 | 见第 5 节 |

最近提交（horde 分支）：`afaa061`（无限数量）、`7aa5830`（cardDistance=32/10 复测）、`77ff4a3`（真6方向烘焙+两档）、`ace989c`（20帧/片/降帧机制）。
最近验证：`client-1792994202355` PASSED；horde 24,831 实例、24,276 走片、展开 871,062、draw 16.666ms、帧 23.046/26.739/27.894ms。用户反馈“绕过了但没有完全绕过”。

## 3. 关键文件

- 渲染/片：`src/main/java/org/unitego/lobecorp/client/horde/HordeCardBake.java`、`HordeMeshReduction.java`、`HordeMeshCache.java`、`HordeGpuRenderer.java`、`HordeRenderMetrics.java`、`HordeVisualConfig.java`
- 着色器：`src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl`、`core/horde_motion.fsh`、`core/horde_unit.fsh`、`core/horde_iris_vertices.fsh`、`core/horde_instanced.vsh`
- 基准：`src/bench/java/.../HordeRenderScenario.java`、`SweeperRenderScenario.java`、`CorpseRenderScenario.java`、`BenchSpark.java`、`BenchWindow.java`、`src/bench/java/org/unitego/lobecorp/config/bench/LobecorpBenchConfig.java`
- 配置/隔离运行目录：`build/modBench/runs/default/client/config/lobecorp-client.toml`、`build/modBench/runs/default/client/lobecorp-bench.properties`
- 文档：`docs/systems/benchmod.md`（第四轮）、`changes.md`、`horde-issues.md`（I39）、`horde-index.md`

## 4. 如何运行

### 4.1 编译/单场景测试
```powershell
cd E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_horde
.\gradlew.bat --console=plain compileBenchJava
.\gradlew.bat --console=plain verifyBenchClient "-PbenchScenarios=lobecorp.sweeper-render,lobecorp.horde-render"
```
- `verifyBenchClientReport` 目前期望 sweeper+horde 两场景，过滤器至少包含这两个；否则报告任务会因缺预期场景失败。
- 每次运行前确认隔离配置：
  - `config/lobecorp-client.toml`：`maximumVisible=-1`、`animationFrames=20`、`cardDistance=32`、`frameDistance=10`
  - `lobecorp-bench.properties`：`hordeWidth=500`、`hordeLength=500`、`hordeMeasureTicks=2400`、`hordePlayerStation=400`
- 光影开关：隔离 Iris `iris.properties` 的 `enableShaders`；光影/非光影测试各一轮、每轮重启客户端。

### 4.2 结果位置
`build/modBench/raw-results/default/client/`：
- `summary.json`、`artifacts/screenshots/horde-render.png`（及 `-off`）、`artifacts/gpu/horde-gpu.json`、`artifacts/stages/`、`artifacts/spark/horde-profile.txt`。
- 归档惯例：`build/agent-backups/<日期>-<主题>/`，附 `sha256.txt`、测试配置副本。

## 5. 测试矩阵（用户要求，尚未完成）

每项光影/非光影各一次，**每项完成重启客户端**，共 10 次：
1. 清道夫×30（有 AI）30 秒
2. 清道夫×30（无 AI）30 秒
3. 清道夫尸体×30 30 秒
4. 随机生物尸体×100 30 秒
5. 横跨 500 格 / 宽 500 格清道夫事件 2 分钟，数量 -1（无限），玩家站路线 400 格处地面、第一人称、不飞、面向事件反方向、夜晚
6. 同 5 + 随机 10 个监守者

实现建议（按顺序）：
1. `SweeperRenderScenario` 增加属性：数量=30、`serverSide=true`、`noAi`；有 AI/无 AI 用集成服务端生成真实 Sweeper（`setNoAi` 或对应 API），渲染仍走客户端。
2. `CorpseRenderScenario` 支持 30；新增随机生物尸体场景（随机敌对/中立/被动混合，100 个，客户端生成尸体批）。
3. 复用 `HordeRenderScenario` 的宽/长/2 分钟/400 格/夜晚/反向参数；再生成 10 个监守者（服务端实体，随机位置）。
4. 每项通过 `-PbenchScenarios=<id>` 单独启动；运行前写入对应 `lobecorp-bench.properties`；每轮截图/Spark/焦点门禁；数据表按 10 项重写，不引用旧场景。
5. 注意 `phaseTimeoutTicks` 需覆盖 30s/2min 测量（当前 3600 临时值）。

## 6. 已知问题/阻塞

1. **避让未完全通过**：单位会侧移，但仍会局部拥挤/顶住（用户 2026-10-26 反馈）。当前是“侧向偏移 + 左右候选搜索”，不是真正 RVO/邻域分离；下一步需按 RVO/流场代价重写（见第 7 节）。参考：[NVIDIA GPU agents](https://developer.download.nvidia.com/compute/cuda/docs/MultiAgentGPU-RA09.pdf)、[AMD 空间分桶/路径规划](https://www.freepatentsonline.com/8810590.html)、[Continuum Crowds 综述](https://core.ac.uk/download/154796454.pdf)。
2. **焦点丢失**：多轮因外部抢焦点 INCONCLUSIVE（losses=1/2），需在无人切窗时重跑；不要放宽门禁。
3. **片在光影下偏蓝/暗**：用户明确这是光影效果，不要改光影；非光影组才是颜色验收。
4. **旧配置覆盖**：已有 `animationFrames=4` 会覆盖新默认 20，需要改配置或删键。
5. **build.gradle.kts** 有并行任务/他人的未提交修改；本次临时加了 `scenarioFilter` 与 `phaseTimeoutTicks=3600`，提交前需只挑自己的 hunk。
6. 原始项目规则要求 IDEA 工具编辑/提交；IDE MCP 会话可能再次失效，失效时按已授权范围用 Gradle CLI 跑基准、pwsh 做 git，不重启 IDEA。

## 7. 下一步顺序

1. **完成避让重写**（用户明确要求，不要再打小补丁）：
   - 每单位 GPU 状态：位置/速度/避让侧（复用 unit 历史纹理）；
   - 每帧 GPU 更新：路线切线目标速度 + 玩家/障碍胶囊斥力 + 前向 lookahead + 邻居分离（复用 spatial 纹理）；
   - 被分割区域：流场代价梯度向外，禁止进入；左右候选只在无梯度时兜底；
   - 渲染只读状态，CPU 不参与逐单位。
2. 用 500×500 / 400 格 / 夜晚 / 反向夹具验证“从两侧绕过去、不停、分割区域向外”，截图对比。
3. 实现第 5 节 5 类场景 + 10 轮重启矩阵，重写数据表。
4. 更新 `benchmod.md`、`changes.md`、`horde-issues.md`、`horde-index.md`，提交中文说明。
5. 最后再做目标达标判定；不要把光影污染轮次/焦点失败轮次算达标。

## 8. 证据路径

- `build/agent-backups/20261026-card-lod/`：片档机制首轮
- `build/agent-backups/20261026-card-bake/`：真 6 方向图集
- `build/agent-backups/20261026-card32-retest/`：cardDistance=32 复测
- `build/agent-backups/20261026-unlimited/`：真正无限数量
- 最新失败/通过轮次在 `build/modBench/raw-results/default/client/`；最新 `client-1792994202355` PASSED（避让重写后）。
