# BenchMod 基准工具链

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
