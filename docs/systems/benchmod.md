# BenchMod 基准工具链

## 2026-10-28 模型资源真实重载诊断

显式场景 `lobecorp.photon-01-resource-reload` 由 `PhotonModelResourceReloadScenario` 继承单次真实光束夹具；默认 `photon-*` 集合不增加此场景，避免隐式扩大标准性能矩阵。主测量前验证四个生产 RESOURCE 模型在两个 config 下共享 sampler，调用真正 `Minecraft.reloadResourcePacks()`，等待 future、覆盖层退场及第二次稳定预热，再检查新 sampler 替换、完整定义／快照／组合指纹及多消费者帧隔离；随后交给原夹具施放和 OUTPUT 截图。失败走原异步 Spark 保存链。

两轮通过 IDEA 准备 `benchClasses verifyProductionJarHasNoBenchContent prepareBenchClientRun prepareBenchClientOptions`，参数 `-PphotonBench=true -PphotonScenarios=lobecorp.photon-01-resource-reload -PphotonSpark=true -PphotonRequireFocus=false`，`photonShader` 分别为 false／true。Debug 启动显式设置 `-Dlobecorp.photon.bench.preserveWindow=true` 与匹配的 `-Dlobecorp.photon.bench.shader`；保持 Sodium、全程自有 Spark、固定 19877 MCP、IDEA 和 Debug。用户本轮明确要求 Photon 独立执行，不再等待 Horde 放行；不操作其工作区或实例。

| 模式                           | 当前运行                          | 场景／总体结果                 |
|--------------------------------|-----------------------------------|--------------------------------|
| 普通                           | `client-1793187353534`，PID 49188 | 功能 PASSED；总体 INCONCLUSIVE |
| ComplementaryReimagined_r5.9.3 | `client-1793187656047`，PID 70400 | 功能 PASSED；总体 INCONCLUSIVE |

两轮四模型全部通过共享／替换／内容及采样隔离断言，真实光束截图已目视可见；Spark 为 COMPLETE，保存实件、SHA256、protobuf 身份／客户端来源／时间／样本验证与归档均确认后正常退出。窗口观测为最大化 2560×1494，主审计及重载审计均未记录失焦；这是逐 tick 观测，不代表连续窗口回调证明。总体 INCONCLUSIVE 来自真实重载造成的 `client.game.paused`／`client.overlay.active`，未取消这两个门禁，不能作为性能或修改前后逐像素等价结论。

初次 Debug 诊断保留于 `plain-debug-first/`；一次重复运行在遗留测试实体清理时失败，保留于 `plain-repeat-failed/`。已修复遍历活实体集合时直接 discard 的问题：先收集带夹具标签的对象再删除，不删除归属不明实体；修复后普通／光影均完成。原生 MCP 工具缓存未连上，使用机器现有标准 SDK 驱动本工作区 CLI，固定 19877 获得 pong／连接与光影世界／截图证据，没有安装依赖或改全局配置。

完整证据根为 `build/agent-backups/20261028-standard-resource-test/`，最终报告分别在 `plain-final/`、`shader-final/`，原始路径结构保留；临时启动配置在 `launch-configs/`。隔离 options／Iris／Spark 三文件恢复原字节，原三断点保留并恢复解除 mute，临时客户端／桥接及游戏已退出。补充 Data 成功、written=0／removed=0，含缓存 29 文件指纹不变。

初次同步：2026-10-22，原 7903 特效工作区；来源为原始 dev 提交 278c418。WP0 文档更新：2026-10-25，当前真实工作区为 `.workspace/lobecorp_photon`。上游：[zhongbai2333/BenchMod](https://github.com/zhongbai2333/BenchMod)，固定 JitPack 0.1.5-beta，MIT 许可。采用其 Minecraft 26.1.2／26.1 适配线；本项目保留 NeoForge 26.1.2.100、ModDev 2.0.142、Java 25 与现有 Gradle wrapper，不为安装降级。上游基线为 NeoForge 26.1.2.76，具体本地兼容性以本次实际编译和运行结果为准。

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

## Photon 模型诊断入口

Provider 仅在 `modBench.scenarios` 显式包含 `lobecorp.photon-` 前缀时登记 Photon 诊断场景，普通基准保留清道夫默认集合。本工作区通过 `-PphotonBench=true` 启用 `lobecorp.photon-*` 场景过滤、仅该 profile 的 MCP 运行依赖和端口 19877；普通服务端／客户端基准保持原入口。`PhotonBenchMixinPlugin` 也只在该显式过滤存在时注入四个新探针：`FXObjectMixin`、`EmitterMixin`、`ByteBufferBuilderMixin`、`GlCommandEncoderMixin`；普通游戏不包装这些高频入口，不宣称诊断零开销。IDEA 的 `BenchMod Photon Prepare` 配置为编译 bench、生成客户端参数并准备隔离 options，`BenchMod Photon` 配置为运行并校验报告。`Photon Bench Debug` 使用独立 `photonBenchClient` 类路径包含 bench 与 MCP，不依赖普通 Gradle 导入时未启用的 profile 依赖。**用户当前禁止所有游戏测试，本轮未执行上述 Photon Prepare、Debug 或真实基准入口；只执行 BenchMod Compile、纯 JUnit 和无窗口 Data。**

夹具 `PhotonModelRenderScenario` 保留原 `GRID=16` 资产：17×17 顶点、256 个四边面、`root` 单骨骼权重与 `boneTracks`；静态与动画使用同一资产，区别仅是是否选择 `turn` 片段。它不是无权重的部件 TRS 资格夹具，WP2 不可直接拿这套资产宣称 TRS 实例化等价。当前 Provider 与报告校验表均登记 **13 个 Photon 场景**：原 8 个（00／10／20／30／40／50／60／70）加 80 静态 32、90 动画 32、91 E2、92 E3、99 极端后空基线；清道夫场景仍保留，由过滤器选择。

E2 为 16 静态＋16 动画＋1 束真实女皇激光；E3 为 32 静态＋32 动画＋4 束真实激光及技能自带粒子。四位女皇沿 X 相距 6 格，各自 Husk 靶位于共同 X 的前方 14／15／16／17 格，属于 `configured_converging_region`，不保证实际像素重叠或固定靶。非光束模型场景预热 100 tick、测量 300 tick；首次创建场景只描述当前 JVM 中该用例的创建成本，不能当作进程冷启动。生命周期只清理各场景拥有的 FX、真实技能实体和相机句柄。

原始帧 JSONL、JFR、自定义负载 JSON 和截图应以当次权威报告为准。Debug 路径核对在测量之外完成，断点暂停、失焦或窗口变化导致的样本不得用于对比。实际进度见 [Photon 总览](photon-workspace.md#当前任务进度)，实验判读见 [性能审查](photon-performance-audit.md)，问题只维护在 [唯一问题清单](../issues.md)。

### WP0 隔离条件与采集契约（代码已写，运行未验收）

仅显式 Photon profile 的 `prepareBenchClientOptions` 配置会向 `build/modBench/runs/default/client` 复制原 `ComplementaryReimagined_r5.9.3.zip` 字节、启用其中 Iris 光影并关闭该目录的 spark 后台 profiler；不写用户 `run/client` 配置。**本轮未执行准备任务，未修改实际世界或用户 Options。**

真实技能夹具在任何世界修改前检查启动 worldId／preset／场景过滤和实际世界名称，并要求客户端目录的词法与真实路径都匹配 `build/modBench/runs/default/client` 尾路径；实际存档 realpath 必须是其真实 `saves` 根内的指定直接子目录，拒绝整个 saves 或单存档链接到外部。此 guard 证明校验时的路径归属，不覆盖外部进程随后替换链接。通过后才固定正午、晴天、关闭时间／天气推进及自然刷怪，并设 NORMAL 防止和平难度移除 Husk；这些规则属于该隔离服务器共享规则，不是单一维度私有规则，旧 `QueenBeamPerformanceTrial` 不调用此准备方法。

所有靶沿用实际 `MAX_HEALTH=1024`；4096 设想因当前属性上限已取消，不改全局属性、伤害、动画、LOD 或资源。只有 E3 交汇夹具在每次施放前恢复自有靶的 `targetSpawn` 并清零速度，随后仍由真实技能处理伤害与物理；施放期间的击退、靶漂移、交叉伤害下存活及像素过绘均未验收。报告保存实际血量与创建位置，并明确 `overdraw_verified=false`、`target_drift_during_cast_verified=false`。

E2／E3 的帧与 GPU 主采集为自然完成的 90 秒，在主采集起点后 4／31／58 秒调度三次真实施放，要求完成全部阶段和三次有效施放；提前 STOPPED／CAPACITY／ERROR 不当作完整主采集。主采集结束后再做第 4 次验证施放，观察全部光束 OUTPUT 后等待帧样本，再调用 `BenchCaptureOptions.immediate()` 截图，并在请求与完成时复核仍处于 OUTPUT。第 4 次与截图不属于 90 秒主采集，但仍可能计入 Bench 的 MEASURE 生命周期尾段；报告的 `bench_measure_includes_visual_validation` 标记这一边界，不能混作纯主窗口指标。

提交诊断显式绑定帧采集：在自然截止当帧的 HEAD 停止，避免等到下一次客户端 tick 才结束所造成的额外尾窗；实际帧数对齐仍未运行验收。提交诊断区分逐顶点 reserve 和真实 resize 次数／字节、CPU／实例作业、成功下发的 GL draw，以及实际相机／投影／窗口／刷新率；GL dispatch 不等于 GPU 执行完成，也不自动证明模型独占归因。当前 16 网格双面提交 2048×32＝65536 字节，等于原生 64 KiB staging 初始容量，原生 GPU 环已复用，不能从历史 reserve self 112/362＝30.94% 推定扩容根因。原始 PhotonFrameCapture JSON 从本基准目录复制登记到自定义 artifacts；GPU 全缺测的验收漏洞已在代码中修正：`gpu_available=false` 或 `gpu_completed=0` 会失败，但此门禁尚未运行验收。

固定条件与跨轮复现仍未通过：相机／窗口变化计数和刷新率是观测记录，`fixed_camera_observed`／`fixed_window_observed` 不替代跨轮门禁；`wp0_cross_run_gate_verified=false`、`particle_seed_determinism_verified=false` 明示未验收。当前仅完成 WP0 代码编写，**WP0 静态与非游戏检查已完成，G0 未通过；用户随后允许提前编码默认关闭的 WP1，代码与非游戏检查已完成，WP2～WP7 未实施**；主代码／基准编译与生产包隔离通过，新增 3 项 JUnit 通过，无窗口 Data 25 秒成功且 written=0／removed=0。没有性能、视觉或运行门槛通过结论。

### WP1 实验开关与比较边界

用户选择 A 允许先编码；仅在另行获准运行时才可给 JVM 设置 `-Dlobecorp.photon.packedSnapshot=true`。缺省不加载新增 BufferBuilderMixin，不更改真实运行配置；它与 WP0 探针过滤是两个独立条件，不能把开启实验误当作游戏授权。实验仅优化已有稳定快照命中的提交，拒绝或关闭时保留旧回放，实际排序与 Mixin 组合仍未验收。每组上限 32768 顶点／1 MiB，输出候选总量 4 MiB；不增加原生 64 KiB 初始容量。后续获授权的录制／超限漏提交已修复，相关 16 项非游戏测试、主代码／基准编译、生产包隔离和无窗口 Data 通过；这不代表真实 Mixin 或渲染已验收。未来须先重建完整负载基线，不直接以可能缺失作业的旧样本报告收益。详细非游戏对照与执行状态见 [WP1 记录](photon-extreme-execution-gpt-20261025.md#wp1-编码阶段2026-10-25用户选择-a)。

## 首批场景

参数集中在 LobecorpBenchConfig：固定 seed=7，默认64个普通清道夫，8×8网格、间隔3格，稳定40tick、预热至少60tick、测量200tick；每阶段超时60秒。客户端另等待20个稳定帧，截图后再留恢复窗口。没有预设“5000单位60FPS通过”等结论或性能门槛。

- lobecorp.sweeper-ai：在服务端通过真实注册类型创建64个清道夫，保留AI，使用确定性UUID、随机种子与外观变种；只新增所需区块的强制加载票据。记录server.tick.duration、实体数量和实体tick累计值，检查实体身份不变且测量期间确实参与tick。未主动布置玩家、战斗目标或清道夫事件负载；没有更改自然生成规则，比较时仍需保持隔离世界初始状态一致。不能等同于实战、群体事件或5000单位结果。
- lobecorp.sweeper-render：仅在客户端创建64个本地清道夫渲染实体，关闭AI与重力，使用保留的负数实体ID并在占用时失败。使用原有SweeperRenderer／GeckoLib渲染路径与固定相机；预热阶段取得sweeper-render.png，再测量真实client.frame.interval和帧样本数量。它不测网络同步或服务端AI，帧间隔也不是GPU执行时间。实体数量检查不等于图像内全部可见，首次运行仍需复核截图。
- 清理仅移除各场景拥有的实体、释放本次新加的强制区块票据或相机保持句柄；不会清空世界实体或移除既有强制加载票据。客户端生命周期不阻塞等待future，截图通过后续tick检查。

客户端保留上游严格环境门禁：失焦、最小化、暂停、界面打开、窗口变化等会使结果不可比，不以降低门禁制造成功。截图、JSONL、JFR必须登记在权威summary.json中；构建成功不代表真实游戏、图形或性能验收成功。

## 2026-10-22 本工作区接入记录（历史）

以下为最初接入记录，不替代本轮 WP0 的 IDE 检查或运行验收。

当时已通过IDEA局部合入构建配置、Provider和两个场景，按本地排版处理新增Java及修改行。Gradle依赖导入成功，BenchMod Compile／Server／Client及底层bench运行入口已生成；7个相关配置／Java文件IDEA错误检查为空。三个构建配置和ServiceLoader与源提交相同，4个Java文件仅存在本地IDE排版空白差异。

保留现有6份运行／MCP配置、端口19877、业务代码及暂存内容，测试运行目录仍归属本工作区build/modBench。Photon UI Java处于另一聊天收尾阶段，按其协调请求暂缓本工作区Compile和Data，未运行真实基准游戏；下方原始dev的编译与Data结果只作为历史来源证据。

## 原始dev历史检查

已通过 IDEA 导入固定版本插件，POM可访问（HTTP 200）；修改行及新增Java格式处理完成。7个Java／Gradle／版本文件统一错误检查为空；6条借用世界的资源归属提示及1条既有Kotlin限定名提示保留，不关闭游戏拥有的世界。已通过 IDEA 的 BenchMod Compile 执行 compileJava、compileBenchJava、verifyProductionJarHasNoBenchContent，日志为 BUILD SUCCESSFUL（17秒）。原有 reserveBuildIdentity 不支持配置缓存序列化，缓存被丢弃但构建成功；没有扩大范围修复该既有问题。

IDEA Data运行完成，日志为 BUILD SUCCESSFUL（25秒），23份生成资源、written=0／removed=0；运行前后23份生成文件SHA256均不变。Data进程已正常结束，未留下本任务Data实例。真实服务端／客户端基准尚未运行，没有生成本轮性能验收、截图视觉或FPS结论。

本工作区修改前备份：refs/backups/before-benchmod-sync-7903-20261022/{head,wip,index}；原有业务修改、暂存与运行配置保留，不纳入本次提交。
