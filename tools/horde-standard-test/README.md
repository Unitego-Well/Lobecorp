# 深夜清扫标准自动 runner

默认 adapter 为本目录 `idea-adapter.mjs`，无需额外参数。不修改场景/provider/build，不写女皇目录。仅 `--run` 会连接 IDEA、创建独立临时配置并启动游戏；dry-run 不连接、不启动、不采集。

```powershell
.\gradlew.bat prepareBenchClientRun compileBenchJava
node tools/horde-standard-test/runner.mjs --dry-run --batch midnight-r2 --slots A_normal
node tools/horde-standard-test/runner.mjs --run --batch midnight-r2 --slots A_normal
node tools/horde-standard-test/runner.mjs --audit --batch midnight-r2 --slots A_normal
```

先由主线授权运行单格 A normal，通过后才选矩阵 `baseline_normal,A_normal,B_normal,C_normal,baseline_shader,A_shader,B_shader,C_shader`。重复运行创建新 attempt，不覆盖证据。源码或编译资源变化后使用新 batch。

## 已实现接线

- IDEA MCP SDK 从根 `tools/lobecorp-normify/package.json` 解析，Streamable HTTP `http://127.0.0.1:64342/stream`。启动前核对已有 `Photon Bench Debug`，生成本轮 Application XML，module `Lobecorp.photonBenchClient`（含 bench+mcpmod），Make=false。
- 使用准备好的 `benchClientRunVmArgs.txt`/`benchClientRunProgramArgs.txt`，尾部覆盖场景、worldId、resultDirectory、phaseTimeoutTicks=12000、modBench.jfr=false、requireWindowFocus=false；FlightRecorderOptions stackdepth=256。端口固定19878。IDE支持动态覆盖时传 workingDirectory/envs。
- 独立目录 `build/horde-standard-test/<batch>/<slot>_<attempt>/game`；独立worldId、results、来源指纹、环境文件哈希、launch-request/IDE返回/ownership回执。临时XML位于 `.idea/runConfigurations/Horde_Standard_<runId>.xml`，保留用于追溯，不改原模板。
- 环境默认来自根 `build/modBench/runs/default/client`；可通过 `HORDE_ENVIRONMENT_SOURCE` 指定已审核模板。复制options、Iris及选中shader包；Spark backgroundProfiler=false。normal强制关闭Iris，shader需模板实际包存在。
- PID必须匹配本工作区、精确worldId与CreationDate；启动等待240秒，场景及制品等待600秒。测量期间只读本地文件，不连接游戏MCP、不截图、不操作焦点。
- cleanup始终执行；PID未知时按本轮唯一worldId恢复识别，CloseMainWindow等待15秒，必要时重核身份后Stop-Process再等15秒。保留存档和制品，不批量终止Java。只有场景真实encounterRemoved回执和进程退出同时成立才confirmed。

## Euler数据契约

场景 `src/bench/java/org/unitego/lobecorp/client/bench/HordeEncounterPerformanceScenario.java`，ID `lobecorp.horde-encounter-performance`。setup通过服务端 `Encounters.start`，正式模板数量不覆盖。

读取 `results/artifacts/horde-encounter-performance.json`：schemaVersion=1、scenarioId、workloadCorrect、eventId、encounterRemoved、positiveStageFrames、measureTicks、固定测量起止tick、actualMeasureStartEventTick、samples。tick样本的submittedInstances是客户端批次提交总量；不把它称作服务端单位数或可见绘制数。服务端逻辑行列尚未采集，保留 `serverUnitsScope=not_collected_no_mob_or_render_count_substitution`，不构造相等门禁。

真实逐帧计数与CPU/GPU来源为 `artifacts/stages/horde-encounter-stages.jsonl`：测量帧counts.instances>0、failures=0，header gpu_completed>0、gpu_errors=0。要求场景positiveStageFrames>0、400测量tick、起始偏差不超过2tick。整帧GPU读取 `artifacts/gpu/horde-encounter-gpu.json`。Spark必须存在合法本轮链接 `artifacts/spark/horde-encounter-profile.txt`；唯一JFR为attempt根run.jfr，正常退出后检查非空及JFR文件头。框架summary所选场景须PASSED且workloadCorrect。独立日志新增ERROR/FATAL失败。

新契约要求Spark从setup最早context可用起至teardown、GPU从最早context可用起至teardown；实际边界以Euler更新报告为准，旧setup_through_measure_end报告不能代表全程完成。JFR非空与GPU JSON存在只证明产物已导出；内部采样覆盖和性能结论仍由主线分析，不能以脚本完成代替性能验收。

## 启动前明确阻塞

当前Bench0.1.5-beta失败路径会主动截图；已有 `ClientAutomationNoScreenshotMixin` 仅允许旧Photon场景，`BenchMixinPlugin`也尚未为新场景启用。adapter preflight要求这两个源码入口显式包含新ID，缺失则拒绝启动。主线须在其授权文件范围接线、编译后运行；这不是虚构禁截图属性。runner自身无截图API。游戏存档图标不作为主动截图。

性能独占检查独立于端口：preflight盘点现有Java游戏进程并拒绝并行游戏。此检查不覆盖非Java或外部GPU消费者，environment回执明确标记未验证；不能把端口空闲或进程盘点称作GPU独占已证实。

参考均只读：`.workspace/lobecorp_queen_of_hatred/AGENTS.md` 与 `tools/photon/performance/20261106-first-cast-matrix.mjs`（在该女皇目录内）：SDK解析、launchXml、独立attempt、指纹、PID+CreationDate核验及finally清理。其截图步骤不移植。未使用外部模型。

## variant与summary格式核对

slot前缀baseline/A/B/C分别显式传入lobecorp.horde.variant=baseline/a/b/c，导出summary中的实际JVM参数还要反查最后生效值。数量和固定事件测量窗不随slot改变。

格式参考（负载已失效，仅参考字段）：build/agent-backups/20261110-horde-card-performance/before-normal/results/summary.json，schema=1.0.0，scenarios条目使用id/status/failure。历史格式确实含workloadCorrect，但runner不将其替代新场景报告；新负载以Euler report.workloadCorrect为准。只要求精确选中新ID的条目PASSED，不把其他SKIPPED场景误判失败。

## 全程采集最新契约

唯一JFR参数：`-XX:StartFlightRecording=filename=<attempt绝对路径>/run.jfr,settings=profile,dumponexit=true`，保留stackdepth256，modBench.jfr=false避免双录制。preflight拒绝模板或Java环境变量中额外StartFlightRecording。collect不要求退出前文件完整；cleanup正常退出后记录cleanup.jfr，runner在cleanup之后audit。强制终止即使留下文件也不算全程完成。当前检查非空及JFR魔数，内部事件时间覆盖仍需分析。

JFR scope为JVM startup->normal exit（从JVM初始化录制生效起）；Spark/GPU为各自earliest available context bounds，实际起止由Euler记录。启动前GPU context不存在，不能采集。稳定窗只用于阶段分析，不裁剪全程制品。使用新batch；未启动游戏或性能采集，禁截图接线门禁继续生效。

最新接线：主线已为新ID启用禁截图Mixin及插件选择，preflight继续核对。窗口显式设置lobecorp.developmentWindowTestPolicy=true、requireWindowFocus=false，adapter不前置窗口、不恢复焦点、不操作实体鼠标。框架summary只读取精确id的status/failure，不依赖其workloadCorrect字段。

Euler全程报告须collectionComplete=true且captureEndNs>captureStartNs>0。GPU默认120秒保持，runner显式设置lobecorp.performance.capture.maxSeconds=600；提前结束/容量截断必须失败，不能宣称全程。此限制以场景报告判定；阶段计数到teardown才导出。每次collect完成后仍需正常退出校验唯一JFR。

全部源码/资源/编译产物准备完成后统一生成指纹，使用新batch，例如midnight-full-600-r1。全程GPU达600秒或131072帧容量提前结束，由场景active检查判失败，runner还要求collectionComplete=true、gpuMaxSeconds=600；不能用截断制品完成验收。

测量期collect每500ms仅process.kill(pid,0)做廉价存活检查，不启动PowerShell/CIM、不轮询MCP。完整PID/CreationDate身份核验限定启动、summary完成后和cleanup；不设置周期完整身份查询。

## 首轮失败后的启动修复

失败证据：midnight-full-600-r1/baseline_normal_1/ownership.json中原VM文件和StartFlightRecording各重复两次，IDE把-D选项抽至前端后被原文件空scenarios覆盖。该轮无效。

新启动仅引用attempt/launch-vm.args一次，过滤将覆盖的-D键、StartFlightRecording/FlightRecorderOptions，拒绝嵌套@文件。唯一JFR和本轮覆盖属性仅放runconfig的子进程_JAVA_OPTIONS（动态启动同样传此env），不修改全局环境、不在CLI重复JFR。启动核对命令行包含sanitized argfile且不含原文件/裸JFR；同一sanitized文件重复引用允许并记录次数；归属以精确--gameDir attempt路径+workspace核对，之后验证PID/CreationDate。collect看到phase=scenario与目标不符立即失败；summary检查最后生效scenario/variant/JFR开关和唯一StartFlightRecording。正常退出后用该JDK jfr工具读取jdk.ActiveRecording，要求唯一recording id，无法验证则JFR门禁失败。

主线准备完毕后使用新batch midnight-full-600-r2。本轮修复未启动游戏，不复用失败轮证据。

IDE重复sanitized @file不单独判失败；vmfile-observation.json记录references/duplicated。拒绝原VM文件、命令行JFR及与本轮覆盖键冲突的命令行属性；实际effective场景/variant仍通过日志和summary门禁核对。

测量审计修正：客户端回调次数不等于服务端游戏刻跨度。保留原始398/399；要求measureTicks正整数且等于MEASURE samples条数，nonemptyMeasureSamples等于其中submittedInstances>0条数。actualStart相对plannedStart容差不超过2tick；真实单调时钟主窗20±0.2秒。各时钟差异留证，不用等式抹平；超出真实时长范围仍失败。

## 原生最大化：使用与执行时机

默认新批次使用native模式。启动前设置模板，再执行runner：

```powershell
$env:HORDE_ENVIRONMENT_SOURCE = 'run/client'
$env:HORDE_WINDOW_MODE = 'native'
node tools/horde-standard-test/runner.mjs --run --batch native-window-r1 --slots A_normal
```

runner只在本轮gameDir的config/fml.toml设置earlyWindowControl=false，关闭该实例的NeoForge早期加载窗口，避免复用一个已创建的普通窗口。原run/client不改。真正最大化由Minecraft真实窗口创建前的GLFW hints完成（禁止创建/显示时激活，关闭居中光标），不是将分辨率写成工作区大小，也不是进入世界后调用ShowWindow。

开发环境可直接选Horde 开发 - 基线/A/B/C，配置已带两个JVM参数：-Dlobecorp.developmentWindowTestPolicy=true -Dlobecorp.developmentWindowTestMode=native，对应独立目录FML已设置。需要完整重启，因为创建提示不能在窗口已创建后补做。

执行链：Window.createGlfwWindow内、实际glfwCreateWindow前→窗口构造结束同步真实尺寸/位置、保留原生MAX状态→首个可见updateFullscreenIfChanged后只读验收。应在日志看到event=creation-hints一次、event=first-visible一次，后者max=true、placementRead=true、placement=3、glfwMaximized=1，并核对foregroundBefore/After。程序不恢复焦点、不反复最大化、不移动或捕获鼠标；完整状态和失败记录必须保留。

legacy复现可显式HORDE_WINDOW_MODE=workarea，它只铺满不是真正最大化；旧性能数据沿用旧策略记录。新策略目前通过独立启动验证，未重跑完整性能矩阵，不把两类结果混写。共享规范及项目调用细节见docs/standards/workflow/game-testing.md#测试窗口原生最大化。

官方依据：GLFW window guide的GLFW_MAXIMIZED/GLFW_FOCUSED/GLFW_FOCUS_ON_SHOW创建提示；Win32 ShowWindow的SW_MAXIMIZE会激活窗口，不能作为无激活补救。https://www.glfw.org/docs/latest/window_guide.html#window_hints_wnd ；https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-showwindow 。

## 独立离线详细分析

`analyze.mjs` 不连接 IDE、不启动游戏、不修改原始证据。读取指定 batch，逐 slot 从最大 attempt 向前寻找 checks 全部为 true 的 completion 或 repair receipt；失败 attempt 记录拒绝原因，缺失格明确标记，不伪造完整矩阵。

```powershell
node tools/horde-standard-test/analyze.mjs --batch midnight-all-after-r1 --out build/agent-backups/20261111-horde-r2/evidence/analysis.json --markdown build/agent-backups/20261111-horde-r2/evidence/analysis.md
```

输出父目录须已存在，文件必须为新文件，拒绝覆盖。无输出参数时 JSON 写标准输出。时间窗分别为 capture、warmup（captureStart 到 measureStart）、measure、teardown（measureEnd 到 captureEnd）。warmup 包含 context 后的稳定及预热；startup 无 GPU context，不声称覆盖。只纳入正 interval_ns 且两个端点都在窗口内的真实 render HEAD 间隔；跨边界间隔不归入子阶段，但仍可进入 capture。零帧阶段返回 count=0 和空统计，不补零或推算。

提供逐帧 1000/ms 的最小、最大、算术平均及 aggregate FPS（帧数/累计间隔）；帧时最小、最大、平均和 nearest-rank P50/P95/P99。严格 >16.667/33.333/50/100ms 分别统计次数、完整帧时总和、超过阈值时长、次数比例、完整帧时比例、连续长帧段数及最长段累计时长。不同阈值是嵌套统计，不能相加；保留全部卡帧。

场景 tick 样本只用于 focus、尺寸和 submittedInstances 元数据，不冒充逐帧；CPU/GPU stage 摘要按各自有效样本统计，null 不补零，父子 CPU 与不同 GPU cohort 禁止相加。JSON 保留 GPU summary、stage header 和原始证据绝对路径；Markdown 附证据链接和详细阶段统计。旧批次离线分析不代表新优化复测。

### 复核补充口径

旧批次实读 sample 的时间为 nanoTime，尺寸为 width/height。stage 只纳入 calls>0、render_frame_valid=true 的记录；GPU 额外限定 STAGE_SUM、非 incomplete、stage coverage COMPLETE 且 expected_calls>0。CPU 不要求 GPU cohort（CPU 在不同 cohort 都有合法调用）。各项 count/sampleCount 独立，不将零调用混入均值。counts 单独报告 draws/passes/upload_bytes/copies/copy_bytes/expanded_vertices/instances 的总和与分布，包含零值，代表有记录的 stage 行而非完整 GPU capture 帧。

crossBoundary 独立列出所有跨阶段间隔及超过的阈值，归入 endPhase；captureStart 跨入记录标记 crossStart，保留完整间隔但不混入主统计。主统计仍要求两端落窗。日志只读抽取 creation-hints/first-visible、nativeIsZoomed、foregroundBefore、placementRead 原文及行号；缺失标记 not_recorded，不依据 GLFW 尺寸推断原生最大化成功。

离线夹具：`node tools/horde-standard-test/tests/analyze.test.mjs`，覆盖严格阈值、连续段、阶段跨界与 cross-start、零调用过滤、环境字段及不完整矩阵。临时夹具自动清理，不修改历史采集。

### BenchMod 优先统计（schema 2）

新报告以 BenchMod 导出的 `client.frame.interval` summary 和 phase 原始 samples 为主：最大/最小/平均/分位数及长帧次数直接使用框架值，补算 FPS、长帧累计/超额时长、连续段，并核对原始样本与汇总一致。严格阈值为框架的 16666667、33333334、50000000、100000000 ns。原 render HEAD 时间窗统计保留为辅助，不与 Bench 阶段混算；全 context 采集、跨界帧及 GPU 分阶段仍使用专用产物。只改变离线分析，不新增游戏采集器，也不追改历史报告。

## 受控启动与 D 组合版（2026-11-11）

Bench 的 `--mixin META-INF/lobecorp.bench.mixins.json` 必须显式位于 IDE main 参数外层，不能仅藏在 `@benchClientRunProgramArgs.txt`：Mixin bootstrap 读取 `sun.java.command` 时不展开该文件。runner 当前接线已处理；以 setupGate 的 `benchWindowPreservationApplied=true` 为准，不能只检查源码包含场景ID。保护仍使用现有 Mixin，没有新建窗口控制器或反复最大化。

受控行启用 `controlledDebugOverlay`、`requireDebugOverlayGuardApplied`、`requireWindowPreservationApplied` 和 `windowObserver`；仅隔离 Bench 拒绝调试覆盖层开启请求，正常开发不锁F3。逐tick导出实际状态；PID限定只读 WinEventHook 输出 `window-events.jsonl`，含 DWM 物理边框及观察线程DPI，退出时解除 hook。cleanup 的 observer 必须正常完成；不会因失焦自动拒绝或恢复焦点。

D 保留A的常态/近景精细避障，横向地形不一致时采用B共享场，继承C障碍触发的全局绕行；同中心线通过碰撞验证后沿用原采样索引与脏组预算。数量、LOD额度与采样精度不变，不自动改默认版本。选择 `Horde 开发 - D` 可直接启动，独立目录 `run/horde-dev/d`，端口19885；正常入口不依赖Bench、不打包。

```powershell
node tools/horde-standard-test/runner.mjs --run --batch <新批次> --slots D_normal,D_shader
```

分析器仅在批次实际有D目录时扩展D；旧八格历史不追改。D独立补采批次的missing旧八格是批次划分，需结合每格completion判断，不把partial矩阵当D场景失败。性能仍以Bench原始interval/summary为主；GPU阶段不同cohort不可相加。UPLOAD_BYTES不覆盖纹理构造首次上传，不能把更新与重建路径切换后的计数直接当完整带宽对比。

## 并行开发时冻结运行目录

同仓库有其它源码/编译工作时，先编译，再从已通过的IDE启动ownership创建独立开发运行快照：

```powershell
node tools/horde-standard-test/frozen-runtime.mjs --project-root . --ownership <通过项目录>/ownership.json --id a-runtime-NEW
$env:HORDE_RUNTIME_SNAPSHOT = '<上一步snapshotRoot>'
```

旧CIM记录若中文路径被编码破坏，只允许通过已核实的游戏加载日志/行号和JAR哈希提供`--verified-path-mappings <json>`，不按模糊名字猜测。后续CIM查询强制UTF-8。

快照只复制开发类、资源、依赖和必要启动文件，不创建模组JAR、不复制存档、不改原文件。复制前后字节必须稳定，manifest和每个文件哈希校验；项目类路径全部替换为快照。设置此变量时，adapter用保存的开发JDK和参数直接启动冻结目录中的DevLaunch，仍使用BenchMod及原生无激活最大化；普通IDE开发入口保持现状。指纹覆盖不可变快照和当前测试工具，明确排除活工作区，测试不会自动吸收之后的源码修改。未设置变量时仍走原IDE流程及完整活工作区指纹。

运行中指纹变化会写失效sidecar，分析器即使看见旧completion也拒绝该项。停机后指定Netty ClosedChannelException只有完整PASSED/report证据才可接受并单列；其它ERROR/FATAL仍失败。完成阶段CIM身份校验区分正常退出与真实PID/命令行变化，未知情况拒绝，不弱化终止前的严格归属检查。

## A普通／光影分流（独立4格）

新开发入口“Horde 开发 - A 光影分流”，模式a_adaptive，独立目录run/horde-study/a_adaptive、端口19894。仅该模式读取lobecorp-client.toml的events.midnight_cleanup.wave.display.adaptive六项开关：native/iris各自的GroupedMotion、SelectedScatter、DepthScissor。普通默认分组运动+候选散射，光影默认保留A运动内核且关闭候选散射；深度有效区两边默认关闭。irisGroupedMotion=false可仅在光影状态使用旧运动内核；Iris仅安装但未开启shaderpack仍走native。Config变更在下一次渲染刷新锁存，切换variant本身需要重启。

原A作为对照保留。未来恢复运行授权后，`--modes a,a_adaptive --run-matrix`采集4格，intent家族a-adaptive-1；不与旧20格或8格混批。Bench报告实际锁存policy与即时shaderActive；实际是否执行仍以stage数据为准。本次只通过编译、6项普通JVM策略测试和静态/Data检查，游戏/GPU/性能测试仍暂停，不能宣称FPS提高。现有旧运行快照不含新代码，未来测试须创建新快照。

## 当前A基底迭代（独立8格）

`node tools/horde-standard-test/runner.mjs --batch a-iteration-NEW --modes a,a_selected,a_depth,a_next --run-matrix` 以当前A为同期对照，自动执行普通/光影8格并报告。`--all --run-matrix` 仍为原20格，不自动加入迭代项；两种实验族不得混入同一batch。每次源码变化使用新batch。

a_selected由最终top128候选直接散射selected标记，并统一selected/compact的key距离档额度；a_depth复用已初始化的远深度padding，只更新实际源尺寸对应矩形；a_next组合两项。A本身保持原实现。配置报告新增selectedScatter/depthScissor，原schema2模式含义不改写；迭代intent标为a-iteration-1。统计额外报告depth_target_pixels/depth_updated_pixels/lod_selected_tests，审计要求配置和实际工作量一致。像素工作量不等于耗时或可见人数。

新IDE开发入口为A候选散射、A深度有效区、A组合迭代，仍可直接启动。-1/0/5层配置只属于B，不参与本轮。当前B闪烁尚未视觉通过，不能将其计数下降当作等效果优化。协议2历史矩阵和全部失败证据保留。

## GPU实验协议2（当前入口）

`node tools/horde-standard-test/runner.mjs --batch gpu-study-NEW --run-matrix` 自动测试20格，先验证default_normal，再逐格采集、清理和审计，最后生成报告。默认首格失败、清理未确认进程退出或源码变化会停止；其它隔离模式失败会保留原因并继续，报告不补零。

`--all --dry-run` 预览；`--modes default,a,b --run` 选择模式；`--slots no_hiz_normal --run` 选择单格。每次源码或编译资源改变使用新batch。旧批次保留原baseline/A/B/C/D语义；新行variantSchemaVersion=2，Java报告必须匹配。

模式：default为旧A默认对照；a为GPU集中计算；b为a上的分层（默认5，-1关闭、0隐藏）；no_hiz关闭候选遮挡及深度金字塔；no_facing_cache跳过朝向缓存；no_motion_opt/no_avoidance_opt/no_iris_opt分别使用旧内核作同功能对照；no_detail_lod为全卡片降细节参考；no_hidden_faces保留原模型隐藏面。

no_detail_lod不参加等画质胜负；no_facing_cache_normal、no_iris_opt_normal未运行对应功能，标为不适用。flags表示配置，实际执行以stage数据为准。粒子、光影、逻辑数量保持不变，B可降低实际绘制数。B包含同步flags和高模ID读回，这项成本必须计入，不宣称纯GPU零同步。unsupported层剔除在正常游戏保守显示完整列表，在基准中失败，不冒充提速。

Bench原始帧统计为主，保留分位数、四档严格长帧次数/时长/超额及连续段；20格历史结果和失败attempt均保留。旧run/horde-dev存档未删除，新入口在run/horde-study各独立目录。

矩阵异常停止时也会在 finally 尝试生成已有证据的 partial 报告；JSON/Markdown 共用一个时间戳。清理失败逐项列出 processExited、encounterRemoved、confirmed（缺失为 unknown），保存 cleanup-stop 和 matrix-stop，保留原异常及非零退出码，不继续后续格。报告生成失败单独保存，不覆盖原异常。
