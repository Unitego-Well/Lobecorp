# BenchMod 基准工具链

当前导航见 [Horde 入口](horde-index.md)，历史阶段见[证据索引](horde-history.md)。下文接入时的 detached／未运行说明仅描述当时状态；2026-10-23 工作区为 `.workspace/lobecorp_horde`、分支 `codex/horde`，后续运行证据单列。本轮只更新文档，未运行任何基准。

同步日期：2026-10-22，horde-recovery恢复工作区；来源为原始dev提交278c418。上游：[zhongbai2333/BenchMod](https://github.com/zhongbai2333/BenchMod)，固定 JitPack 0.1.5-beta，MIT 许可。采用其 Minecraft 26.1.2／26.1 适配线；本项目保留 NeoForge 26.1.2.100、ModDev 2.0.142、Java 25 与现有 Gradle wrapper，不为安装降级。上游基线为 NeoForge 26.1.2.76，具体本地兼容性以本次实际编译和运行结果为准。

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

参数集中在 LobecorpBenchConfig：固定 seed=7，默认64个普通清道夫，8×8网格、间隔3格，稳定40tick、预热至少60tick、测量200tick；每阶段超时60秒。客户端另等待20个稳定帧，截图后再留恢复窗口。没有预设“5000单位60FPS通过”等结论或性能门槛。

- lobecorp.sweeper-ai：在服务端通过真实注册类型创建64个清道夫，保留AI，使用确定性UUID、随机种子与外观变种；只新增所需区块的强制加载票据。记录server.tick.duration、实体数量和实体tick累计值，检查实体身份不变且测量期间确实参与tick。未主动布置玩家、战斗目标或清道夫事件负载；没有更改自然生成规则，比较时仍需保持隔离世界初始状态一致。不能等同于实战、群体事件或5000单位结果。
- lobecorp.sweeper-render：仅在客户端创建64个本地清道夫渲染实体，关闭AI与重力，使用保留的负数实体ID并在占用时失败。使用原有SweeperRenderer／GeckoLib渲染路径与固定相机；预热阶段取得sweeper-render.png，再测量真实client.frame.interval和帧样本数量。它不测网络同步或服务端AI，帧间隔也不是GPU执行时间。实体数量检查不等于图像内全部可见，首次运行仍需复核截图。
- 清理仅移除各场景拥有的实体、释放本次新加的强制区块票据或相机保持句柄；不会清空世界实体或移除既有强制加载票据。客户端生命周期不阻塞等待future，截图通过后续tick检查。

客户端保留上游严格环境门禁：失焦、最小化、暂停、界面打开、窗口变化等会使结果不可比，不以降低门禁制造成功。截图、JSONL、JFR必须登记在权威summary.json中；构建成功不代表真实游戏、图形或性能验收成功。

## 接入时的历史状态（不覆盖后续运行证据）

通过IDEA局部接入BenchMod配置和两个普通清道夫场景，依赖导入成功，BenchMod Compile／Server／Client入口已生成。7个相关配置／Java文件IDEA错误检查为空；4个Java文件与来源仅有本地排版空白差异。原有MCP配置SHA256未变，build.gradle.kts中快照已有的资源输出排除规则保留；深夜清扫业务实现及重写计划不动，维持detached状态。不把原始dev检查结果视为本工作区验收。

本次不编译、Data或启动真实基准游戏；场景只覆盖普通清道夫实体AI和本地渲染，不替代未来GPU浪潮的专用负载和性能验收。

## 原始dev历史检查

已通过 IDEA 导入固定版本插件，POM可访问（HTTP 200）；修改行及新增Java格式处理完成。7个Java／Gradle／版本文件统一错误检查为空；6条借用世界的资源归属提示及1条既有Kotlin限定名提示保留，不关闭游戏拥有的世界。已通过 IDEA 的 BenchMod Compile 执行 compileJava、compileBenchJava、verifyProductionJarHasNoBenchContent，日志为 BUILD SUCCESSFUL（17秒）。原有 reserveBuildIdentity 不支持配置缓存序列化，缓存被丢弃但构建成功；没有扩大范围修复该既有问题。

IDEA Data运行完成，日志为 BUILD SUCCESSFUL（25秒），23份生成资源、written=0／removed=0；运行前后23份生成文件SHA256均不变。Data进程已正常结束，未留下本任务Data实例。真实服务端／客户端基准尚未运行，没有生成本轮性能验收、截图视觉或FPS结论。

恢复基线保留在refs/backups/before-benchmod-sync-horde-recovery-20261022/{head,index}；原快照6328b11及其业务实现不改写，本次仅新增BenchMod配置、场景和文档。
