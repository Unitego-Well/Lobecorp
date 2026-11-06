# Photon 重复特效性能入口

## 2026-11-06 标准延迟首次施法入口

新测试使用独立脚本与 profile，旧 20261105 Run 矩阵及场景保留，不能混作同一批结果。入口默认只检查路径、环境模板和六格计划，不连接 IDEA/Minecraft、不创建输出目录；只有显式 --run 才启动游戏。

```powershell
node tools/photon/performance/20261106-first-cast-matrix.mjs --dry-run

# 按实际情况记录；允许 enabled / disabled / unknown，不凭 FPS 推断安静模式。
$env:PHOTON_QUIET_MODE='enabled'
$env:PHOTON_ENVIRONMENT_NOTE='当前安静模式，记录实际电源与后台负载'

# 以下命令会真实启动六局，不是 dry-run。
node tools/photon/performance/20261106-first-cast-matrix.mjs --batch 20261106-photon-next-standard-r1 --run
node tools/photon/performance/20261106-first-cast-matrix.mjs --batch 20261106-photon-next-standard-r1 --resume --run

# 仅回读本入口已生成的批次，不连接或启动游戏。
node tools/photon/performance/20261106-first-cast-matrix.mjs --batch 20261106-photon-next-standard-r1 --audit

```

- 普通/光影各三轮、每局独立 JVM/新世界/输出目录；--slots N_first_1 可仅准备或运行一格，但报告 matrix_complete=false，不冒充六格完整。
- 唯一场景 lobecorp.photon-02-first-cast-performance：世界/玩家就绪、screen/overlay 消失连续至少10秒后，Spark ack 再生成真实女皇/Husk；沿用100 tick暖场，20秒主窗内第2秒请求一次真实施法，记录阶段0/1/2及移除。启动/进入世界排除优化范围，延迟后的夹具生成/准备窗与主窗分别列出，不混算。setup-prewarm可能晚于fixture queue，不能声称覆盖完整生成CPU；原始Spark/JFR与阶段日志保留。
- MCP bridge只在读到本局 PHOTONSTAGE spark_stop_request 后创建；再核PID/创建时间/19877归属和GUI无Screen，保存 scene-after-measure.png。无物理鼠标、前置、恢复焦点或反复最大化操作。未最大化和失焦不直接拒绝数据；窗口尺寸/最小化、焦点时间线、限帧与throttle按工件核对。
- 所选场景须 PASSED 且 workloadCorrect，其他框架场景允许SKIPPED。脚本还校验延迟、100 tick、20秒/2秒、目标UUID/health、阶段、原始帧列长度/时钟、实际JVM、Sodium、Spark/JFR工件及哈希。Spark关闭看最终spark-teardown，不拿workload中的早期closed=false误判；GPU仅render段、分配缺失保持未知，不填0、不与CPU相加。
- profile显式 -XX:FlightRecorderOptions=stackdepth=256，仅设置录制深度，不额外启动第二个JFR。raw JFR保留，实际栈截断仍须后续核对；如导出，jfr print同样显式 --stack-depth 256，避免工具默认短栈。
- 未导出独立命中/伤害数值和prepared_project_*统计时明确unknown，不据目标存在或静态注册宣称命中正确/缓存首发命中。collection_valid/selected_complete/matrix_complete只表示采集证据，不是性能达标、视觉或全部架构契约验收。
- --resume只复用同批次同HEAD/源与编译输出指纹、profile、选择和Node版本；只考虑最新attempt，不回退挑旧成功局。失败目录保留，新尝试使用_attemptXX。代码/环境模板变化须新批次。每局先确认Spark归档再关闭本脚本拥有的JVM；清理身份未知时保留进程并明确失败，不按名称批量结束。
- 编译产物/程序参数文件须预先准备；脚本不自动构建。正式性能采集前由主线协调独占测量资源，端口空闲不等于GPU独占。安静模式/观测开销变化如实记录；当前为IDEA Run诊断profile，与历史Debug采集不能直接做严格收益A/B。
- 环境模板默认读取最新正式r3的两份独立gameDir（见JSON environmentSources），只复制options、Iris、Spark配置与选定shader字节到新目录，不改模板或用户原有环境；模板缺失时修改profile指向已核目录并使用新批次。--discover仅查询实际IDEA工具，不启动游戏。

## 本地证据与保留范围

当前只保留本地正式批次 `build/agent-backups/20261106-architecture-standard-r3` 及其控制/清理回执，未随 Git 发布；历史过程报告和旧数据已按用户要求清理。源码、旧分析工具和已有暂存内容保留，但依赖已清理数据的旧入口不再作为当前测试入口。

继续测试应使用上面的新批次脚本，不直接重跑旧 IDEA 六局配置覆盖最新工件。规则、系统契约、长期记忆和作者资源不属于运行数据清理范围。
