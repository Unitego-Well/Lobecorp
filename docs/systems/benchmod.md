# BenchMod 基准工具链

当前导航见[Horde 总览](horde-index.md)，全部未解决问题只在[问题汇总](horde-issues.md)维护。工作区为 `.workspace/lobecorp_horde`、分支 `codex/horde`。本文保留基准操作说明与合并后的必要历史证据，不继续维护已结束的接入过程。

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

## 历史证据与归档

以下仅为既有记录摘要，本轮未重新运行。原始 dev 的构建、Data 与当前 horde 验收分开判断；完整阶段记录可从文档整理前备份及 Git 历史恢复。

| 阶段 | 既有证据与条件 | 使用边界 |
| --- | --- | --- |
| 2026-10-21 旧 GPU/图片图集 | 旧2000/5000单位轮平均帧间隔20.50/22.45ms，854×480、VSync开启、夜间；图集复验曾确认5000逻辑单位持续运行约195秒并正常退出 | 旧表示已移除，不作为当前顶点帧、2560×1494或稳定60FPS验收 |
| 2026-10-22 逐格高度、逐小段绘制 | 平均9.93ms、P95 13.41ms、P99 16.74ms；[Spark](https://spark.lucko.me/3N4nE67wyv) | 与后续批次/高度版本分别保留 |
| 2026-10-22 逐格高度、16小段合批 | 平均8.57ms、P95 10.00ms、P99 10.97ms；[Spark](https://spark.lucko.me/W0HnlFero9) | 单轮，含Spark采样扰动 |
| 2026-10-22 记录标为粗高度、16小段合批 | 平均10.02ms、P95 20.54ms、P99 21.46ms；[Spark](https://spark.lucko.me/HirLK4nU0D) | 当前夹具逐节点采高，生产粗采样对应关系按问题汇总H05复核 |
| 2026-10-22 顶点帧与区域检测 | 既有IDEA编译、Data和规则/区域单测记录；用户确认烘焙朝向和动画正常 | 不能扩展为更多模型、当前完整玩法或独立性能验收 |
| 2026-10-23 迁移与制品 | 迁移、端口、制品与构建边界见[工作区目录](workspace-layout.md)和[制品规范](minecraft-mcp-artifacts.md) | 配置正确不代表当前游戏已连接；完整build的test失败仍未解决 |

三轮10000实例记录使用128格宽、2560×1494、VSync关闭、测量1200tick及Spark30秒，普通64实体对照620tick。实例指标全程10000是提交负载证据，不等于所有单位图像正确可见，也不覆盖真实参战和网络。粗高度标记轮P95变差，不能宣称它提高帧率或全程60FPS。

普通客户端历史观察包括石墙不透明遮挡、夜间萤石附近变亮、铁傀儡附近让行，以及无限配置提交28944/32848实例。逐格高度阶段的56→61→56与版本0→1→2只覆盖当时高度实现，不能替代后来粗采样验收。

原报告目录为 `build/modBench/raw-results/default/client`，版本对照与失败预检查归档为 `backups/horde-validation-20261022`；旧图集和遮挡源备份为 `backups/horde-gpu-before-20261022`。2026-10-23曾只读确认这三个目录存在，未重新验证报告内容。其他旧证据入口为 `.backups/horde-isolation-20261021`、`.backups/horde-impostor-20261021`、`.backups/horde-crash-20261022`；不自动恢复旧实现或配置。

原始dev的BenchMod接入编译/生产包隔离和Data历史结果保留在[修改记录](changes.md)。恢复基线引用为 `refs/backups/before-benchmod-sync-horde-recovery-20261022/{head,index}`，旧快照为6328b11，BenchMod接入为1f30bde。已结束的安装步骤、PID和重复检查输出不再单独保留过程页；旧Debug异常、配置换行和缺失快照均转入[问题汇总](horde-issues.md)。

本轮文档合并备份：`C:/Users/wang_/AppData/Local/Temp/horde-docs-consolidation-20261023-kwqkdh3p`，含全文、SHA256清单和修改前Git差异；这份备份不是当前运行结果。
