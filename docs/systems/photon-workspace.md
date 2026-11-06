# Photon 当前能力与恢复入口

## 2026-11-06 标准首次施法测试脚本

新标准测试从[标准延迟首次施法入口](../../tools/photon/performance/README.md#2026-11-06-标准延迟首次施法入口)运行，默认dry-run。旧Run矩阵保留作历史对照，不混入新批次。已完成脚本静态检查与六格计划核对，实际游戏/性能验收尚未执行，不能把采集完整当收益或全部架构契约通过。

## 2026-11-06 首次施法创建架构优化

PROJECT校验/输入对齐、资源级静态子视图与命名描述/实例绑定按同一创建链组织；语义与适用模式见[修改记录](changes.md#2026-11-06-首次施法创建架构优化)。本批没有切换默认模式或进行收益承诺，实际游戏与性能验收边界见[问题汇总](../issues.md#2026-11-06-架构优化运行验收仍开放)。

## 2026-11-06 子发射模板程序复用

成功回退创建后保留可复用模板程序；失效校验、瞬态来源与原生回退保持原契约。实现与证据边界见[修改记录](changes.md#2026-11-06-子发射模板程序复用)。

## 2026-11-06 静态准备与项目模板查找首批优化

- 沿用现有资源加载入口，编译对象统一预备模型与命名播放的不可变缓存；既有定义规范化仍保留，不把全部命名构建前移或宣称消除累计耗时。
- 有效 prepared PROJECT 使用定义中保存的控制器 UUID 查找；结构/修订校验、缺失拒绝及无已知控制器时的多控制器检查保留，不缓存活场景对象。
- 四文件 IDEA 无错误，统一编译及生产包隔离通过（12秒）；Data 通过（21秒，23个输出、written=0）。未运行游戏或性能对照，收益与加载新增成本待测。实例绑定、随机数、技能时序和原版行为保持原路径。

## 2026-11-05 主线接手修复

用户要求主线接手停止使用的 DS 上下文。主线完成本批八文件修复：动画来源索引、零缩放分解、稀疏分块、运行资源预算、空目标同步、空来源命中框、诊断假零字段和粘贴标题。原有 WIP 保留，原版存档图标包装已撤回。

IDEA 八文件无错误，统一编译/生产包隔离及 Data 通过，生成输出 written=0；未执行单元测试、游戏、视觉或性能复测。实现契约见[动画运行时](animation-runtime.md#2026-11-05-动画索引与内容接收)与[空间附着](attachments.md)，当前剩余边界以[问题汇总](../issues.md#2026-11-05-本批修复后的剩余边界)为准。详细收尾见[修改记录](changes.md#2026-11-05-本批审查问题修复)，不将历史性能数据当作本批收益证据。

## 2026-11-03 激活目标局部预分组已实现，游戏性能未验收

新增 `PhotonActivationTargetUtil.group`，每次 `collect` 按当前 Track/Clip 快照建立目标首次序局部映射；ActivatorTrack 按 header，ControlTrack 仅按 clip target，同一次 occurrence 内去重、同一 Track 多次 leaves occurrence 保留。`PhotonNamedGates.collectTarget` 保留原 mute/target、hasControl、clipForObjectAt、异常与 activation/signals/commit/reset 逻辑，无跨 tick 缓存或线程。

父实测定向 CPU 22/22（TargetUtil 8、ActivationState 9、TrackCompatibility 5）；Photon Global Compile 17s、Bench/生产包隔离成功；Data 21s，23 JSON written0/removed0 且前后 SHA256 无变化。未启动游戏、桥接、视觉或性能采集，660ms/P22/P23 不关闭；目标局部扫描 9→3 不等于整帧收益。契约与修改记录见[命名动画](photon-named-animations.md#执行计划事件顺序与贡献所有权2026-11-02)与[修改记录](changes.md#2026-11-03-激活目标局部预分组)。过程证据仅保存在本地 `.agent`（`20261103-parent-gates-validation.md`、`20261103-gates-local-grouping-implementation.md`），未随 Git 发布。

## 2026-11-02 通用性能重构候选与统一验证

更新时间2026-11-03。通用重构R1已冻结679a2846，R2缓存与SPI修复冻结967cf048，均为本地提交，未集成原始dev。旧“尚未运行”阶段已由真实证据替代：CPU133项历史有效，R2限定26项通过；Global、Native总体PASSED，Native创建74/复制19/元数据10/材质9。完整视觉与真实资源reload仍未覆盖，功能通过不替代性能。

用户已退出安静模式，正式比较使用当前模式同输入A/B。共同观测基线5a41ce04、旧优化679a2846与R2 967cf048首轮各7局已运行；R2七局eligible，严格完整窗激光普通276.8948ms/光影256.7746ms，普通女皇97.0028/牛37.6854=2.5740倍，仍未满足100ms/1.5倍。三轮尚未完成，不混用旧B与R2为同一候选；原失败、最大帧及QCW/QQN部分独立身份留档缺口保留。唯一未决边界见[问题汇总](../issues.md#2026-11-02-通用重构仍待运行与性能验收)。

R2残余JFR推动两项通用修复：Prepared持有纯材质SNBT惰性蓝图，各实例仍独占副本、codec和运行槽；连接动画分块以收齐计数一次拼接，避免不完整前缀反复复制，发布校验/通知时序保持。新修复Compile与生产包隔离17s通过、16项定向CPU通过、Data41s通过且23生成JSON哈希不变；新修复Global与Native总体PASSED，父已回读Native创建74/复制19/元数据10/材质9和四类Global契约；Global截图及部分前后class留档缺失，不能声称身份闭环/完整视觉。真实reload和新批性能尚待运行。模型identity布局仅设计，未实施。用户明确DS失败可换GPT，现由GPT执行、父审核，不通知其他聊天。

系统契约见[创建](photon-runtime-creation.md)、[模型](photon-model-effects.md)、[命名](photon-named-animations.md)、[动画](animation.md)，测量口径见[BenchMod](benchmod.md)。当前独占证据在build/agent-backups/20261103-photon-r3-validation；原件备份与过程报告保留所属.agent及build，不随Git发布。


## 2026-11-02 通用性能修复与校时基线冻结

设备戳漏通知已确认并修复，功能确认局3次光影激光、376条readiness全READY、回退/错误0、verified/cleanup=true；该局源码dirty，仅作功能确认。Sampler新增不可变part/morph ID集合；30项相关Java CPU测试、2项Python时钟回归、Compile/生产包隔离与Data（23输出written0/removed0）通过。R3旧`matrix-audit.json`为v1，`matrix-audit-v2.json`已校正CSV相对trace起点偏移，最坏帧不变；受保护原脚本未改。

硬目标仍未达成：激光最长完整帧≤100ms、普通女皇≤同负载普通生物1.5倍均待安静模式同条件A/B。普通女皇已按“最多/最长”严格含义采用单只原版牛同场景最坏完整帧为对照，保留P99/CPU作用域；每轮配对报告QueenMax/CowMax，三轮全部保留，门槛QueenMax≤1.5×CowMax。激光按每局完整窗口max≤100ms判定，另标激光活跃窗但不替代全窗。新Bench场景尚未实现，端口19877与旧R4配置只占位，未启动游戏/采样。基线提交`de6a368c0e87de66152ea278d193cb74f7557439`；manifest见`build/agent-backups/20261102-photon-quiet-baseline/source-manifest.json`，完整交接见本地`.agent/reports/20261102-photon-quiet-baseline-freeze.md`。

## 2026-11-01 R3性能矩阵已执行，15有效3失败

用户重新授权开始测试；在a58fbf1上串行执行普通/光影×女皇25秒/三次激光/空白各3局。15局通过完整帧/Spark归档校验，三局光影激光均因资源PREPARING未能施法，不计有效性能。普通激光最坏275.10～396.47ms，光影空白单局259.59ms，P22/P23不关闭；没有同版本优化收益结论。具体失败见[唯一问题清单](../issues.md#2026-11-01-r3光影激光资源准备未就绪)，完整统计见本地.agent/reports/20261101-photon-event-performance-r3.md与build/agent-backups/20261101-photon-event-perf-r3/matrix-audit.json。

本轮只读观察窗口，固定负载/场景与源码保持，旧失败局不覆盖；测试已结束，游戏/Debug/observer/helper已释放，原WIP保留。未通知其他会话，未进行窗口最小化/鼠标交互等专项验收。下方暂停记录为此前阶段历史。

## 2026-11-01 公共窗口策略已接入，性能继续暂停

已通过Git三方同步94165d5并完成Photon专属适配：窗口生命周期由公共DevelopmentWindowPolicy一次非激活铺满处理，测试场景只读观察焦点/native max位；不调用场景Focus/Max/restore，不用native MAX位作为硬门禁。保留未最小化、有效窗口/帧尺寸和原负载/ID/时长。开发旧性能入口移除窗口/AFK/VSync/FPS/pause改写与伪造输入，production PhotonFrameCapture的原有输入计时行为保留。正常的一次鼠标释放及聚焦后用户主动点击逻辑未全局禁用。

本分支已有六个Horde/BenchWindow等文件删除未被复活；四文档冲突只合本次公共增量，保留Photon正文。16个Java静态错误0，修改行格式处理、compileJava/compileBenchJava/生产包隔离及Data通过，23生成文件未写入。未启动游戏或采集，窗口防最小化、真实鼠标/后台帧率仍未验收；非激活铺满不是GLFW native maximized，外部直接最小化不作保证。此前性能失败/中止证据保留，后续须新批次，不自动恢复旧配置。

## 2026-11-01 性能窗口夹具静态修复为只读

按最新“主要避免抢鼠标”及独占分工，已删除本线PS夹具的ShowWindowAsync/SetForegroundWindow及反复恢复焦点逻辑；两个性能观察器只启动observe_only状态记录。没有窗口前置/聚焦或实体鼠标注入、捕获、居中API；36份新运行配置无直接窗口变更调用。两份Node语法检查、PS解析与定向API搜索通过，未启动游戏/采集作运行验证。性能采集继续暂停，本轮没有有效性能结果；防最小化、一次最大化、后台不降性能留主线统一修复，不能用此静态修改替代验收。原有WIP保护，旧失败与中止证据保留。

## 2026-11-01 性能采集按最新窗口要求暂停

用户原要求性能测试，随后明确要求先修复反复前置/抢焦点、防最小化、仅启动最大化一次及后台不降性能。已停止本轮自有客户端、observer及焦点夹具，Debug sessions=[]，没有有效完成的性能局：R1为目录后缀门禁失败，R2进入世界后中止，只有partial报告，不能给出性能收益或18局完成结论。新性能工具与隔离配置未提交保留，窗口/后台链路由主线统一修复，本线不并发修改、不自动恢复采集。此前完整逻辑139项和MCP16通过保持原范围，不扩作性能证据；精确PID、调用API及配置入口记录于所属.agent/handoffs/20261101-photon-performance-focus-stop.md。

## 2026-11-01 完整逻辑与端到端导出验收

本轮范围为事件准备、编辑器归属、组合状态、资源与关闭生命周期、导出事务的完整逻辑矩阵。F7独立客户端139项断言全部通过、0失败、无未测项；真实worker门禁分别验证取消、reload及最终关闭的旧epoch回调返回。B1标准MCP再通过16项端到端检查：真实原生粒子与OBJ三角模型的定义/包导出、修订保持、拒绝覆盖、拒绝脏关闭、显式关闭及PROJECT_CLOSED。四输出均回读：每定义1对象、每包2条目。两者分别报告，不把重叠检查相加为覆盖率。

最终编译/生产包隔离、Data（23份written0）及7项Scheduler JUnit通过；限定源码审查无确认P1/P2。修复即时display旧读、导出ValueIO遗漏快照、发布刷新先激活后改定义、关闭拒绝写入pending表四处实际发现的问题。原生空工程关闭仅验证对话框/标签和既有生命周期保持，不将inactive runtime的valid值扩称GPU资源销毁。当前无控件强/弱登记表，控件更新仅使用事件本地引用。历史G1、视觉等价与P22/P23性能问题不属于本轮通过结论。

原始证据：build/agent-backups/20261101-photon-full-logic-f7/client/agent-results/photon-logic-regression-20261101/report.json；F7截图同client/screenshots/full-logic-final.png；MCP调用与回读证据build/agent-backups/20261101-photon-bridge-b1/mcp-evidence.json，截图bridge-model.png。框架自动帧间隔记录存在，JFR关闭、无主动Spark采集，不将这些运行称为零性能采集或优化收益。游戏/Debug/临时桥接和本轮断点均已回收，失败轮次保留。

## F7完整逻辑139项通过，最终端到端导出与提交待收尾

F7独立PID43848，139项全部通过、0失败、not_run为空，completed_checks_passed及full_requested_matrix_passed均true；关闭拒绝不改变pending状态已实际通过。真实reload旧epoch4/当前5、最终关闭旧epoch5/当前6，在return后STEP_OUT才记录消费。Data成功，23生成文件written0；Scheduler本次JUnit7/7，XML时间2026-11-01T10:27:49.574Z。截图F7 client/screenshots/full-logic-final.png已回看，属于游戏内编辑器帧，不扩称完整视觉或性能验收；游戏退出、Debug sessions=[]、临时断点移除。最终Sol只读审查未发现证据充分的当前可达P1/P2，已关闭。

为严格满足计划ordinary effect_export入口，再新增独立photon-11-bridge-contract待真实MCP调用，底层往返已由完整场景通过，不重复其余矩阵。用户明确允许把原保护的PhotonModelOutputConfig(boolean)纯数据构造改动一并集成；其他原配置/测试/工具暂存继续保护。完整目标仍active，待MCP端到端结果与精确提交。

## F6统一138项通过，关闭拒绝无副作用补正待F7

F6独立PID70044，138项断言通过、0失败；覆盖新增live=false、初始化既有控件、global stamp三来源重建、reload晚到和最终关闭。Debug真实证据：旧reload callback捕获epoch4/当前5，在return分支后STEP_OUT才记录消费；最终关闭捕获epoch5/当前6、closed=true，同样返回后记录，不伪置成功。截图client/screenshots/full-logic-final.png已落盘。证据build/agent-backups/20261101-photon-full-logic-f6/client/agent-results/photon-logic-regression-20261101/report.json；游戏退出、Debug sessions=[]，本轮全部断点移除。

最后代码审查发现queueSnapshot在Scheduler关闭拒绝前先写待准备表；已将closed检查前置，并新增rejection_preserves_pending_state断言。改后构建通过（ij_run__BenchMod_Compile_2593638800713691781.log），待F7实机确认；报告full_requested_matrix_passed改为完整模式、末阶段、无遗漏且无失败时计算，不能用字段本身替代逐项审计。完整目标继续active，待最终回归、Data及精确集成。

## 审计补证夹具已接入（待实机）

已新增owner.initialization_refreshes_existing_control；源码复核当前featureControls注册表已删除，通知方法只使用事件本地控件引用，不靠GC判断。WorkspaceCheck新增phase9–12，真实按钮切live=false、跨tick不自动刷新、显式手动刷新完成、恢复live自动刷新，尚未运行。LifecycleCheck已新增并接入runner末尾：global stamp失效及三来源重建、真实reload worker门禁、最后closePreparation取消/拒绝/旧回调；主线须在旧dispatch客户端回调epoch拒绝返回后记录oldCallbackConsumed，不能仅手填该标志。已修正测试预期，reload可合法重准备相同修订，不能把新epoch发布误判旧worker回写。新增代码错误级静态0，构建日志ij_run__BenchMod_Compile_2616727209077701334.log；下一步F6实机补证，完整目标active。两Sol已关闭，暂无本线游戏。

## F5统一轮次111项通过，完整审计仍有补证项

F5独立JVM PID61544，111项断言全部通过、not_run为空、completed_checks_passed=true；真实桥接discardUnsaved=false拒绝及project/runtime/revision保持已通过。W6组合31项已在本轮统一执行，不能与之前报告直接相加。full_requested_matrix_passed仍false：审计发现live=false手动刷新、global设备stamp失效重建、reload期间worker晚到与最终closePreparation边界缺少独立证据，已新委派互斥范围补证；owner初始化前既有控件及原生关闭资源清理需再核对。F5证据build/agent-backups/20261101-photon-full-logic-f5/client/agent-results/photon-logic-regression-20261101/report.json，Bench日志ij_run__Photon_Full_Logic_F5_15220023896767368154.log；游戏已退出，Debug sessions=[]，worker断点移除，临时bridge22328已关闭。截图桥接调用在退出后返回not connected，未伪称新增截图成功。框架自动帧间隔采集存在，jfr=false不等于零性能采集，本轮无性能结论。完整目标active。

## 组合契约W5与发布刷新顺序修正（待重跑）

W5实机20项断言通过，新增冻结导出成功、源工程不变、重复输出失败保留旧文件、清单存在、真实挂包回读、定义一致及15组成件完整，证明ValueIO资产快照修正有效。随后发布预览刷新抛“运行中不可替换组合定义”：restartPreview先importPublished/applyTime激活第0帧，再composition导致保护拒绝。已增加importPublished(preserveDefinition)，保留编辑定义/禁用集合并在首次applyTime前应用组合；不取消运行态保护。IDEA错误0，构建日志ij_run__BenchMod_Compile_16244508899460837620.log，运行效果待W6。W5证据build/agent-backups/20261101-photon-workspace-w5/results；Debug sessions=[]，setup断点移除。目标active，剩余输出/清理及最终完整轮次仍待验证。

## 组合契约W4与导出准备修正（待重跑）

W4实机11项断言通过，包括删除依赖后原生回退、恢复自动刷新及播放目标保持。冻结导出阶段真实失败“模型实例没有内嵌资产”，堆栈落prepareModelObject。源码确认该路径直接PersistedParser读写，未调用PhotonModelOutputConfig自定义ValueIO中assetSnapshot读写；已将此单一路径改为config.deserialize/serialize与ProblemReporter检查，保留未知字段局部写回。静态错误0，构建正在/已由本轮日志ij_run__BenchMod_Compile_11029998293328529092.log核实，冻结导出运行效果尚未证明。W4证据build/agent-backups/20261101-photon-workspace-w4/results；客户端已stopped，本轮setup断点移除。完整目标active，待W5和最终全矩阵。

## 组合契约W3（7项通过，删除阶段待重跑）

W3实机已通过原生工程载入、seek17、UI responder持久化、暂停时间保持、自动刷新保持以及同大小同mtime纹理变更检测，共7项。阶段3因仍沿用“模型缺失必须失败保留旧预览”的夹具预期超时。已核对当前TextureManager.loadContentsSafe对纹理读取异常返回TextureContents.createMissing；夹具修正为原生纹理删除必须自动探测并完成回退修订，再以该新修订作为恢复基线，防止恢复断言提前通过。该修正尚未实机验证；无生产修改。W3证据build/agent-backups/20261101-photon-workspace-w3/results，Debug已stopped，临时setup断点移除；完整目标仍active。

## 组合契约W2根因修正（待重跑）

W2在异常包装入口Debug确认原始NoSuchElementException来自openNativeAuthor查找顶层ParticleEmitter；真实laser_spell工程顶层是子生成器、命名控制器与空对象，没有顶层粒子发射器。夹具现新增独立原生ParticleEmitter（发射率0）承载临时纹理依赖，保留原激光对象，不改变生产门禁。静态错误0、编译/生产包隔离成功（ij_run__BenchMod_Compile_1195581560841061619.log）。W2失败证据和完整异常栈保留，Debug sessions=[]、临时异常断点移除；下一步W3重跑，组合/导出仍未验收，目标active。

## 组合契约W1（失败，待根因诊断）

新增独立photon-09-workspace-contract，仅用于组合问题迭代，不代替完整矩阵。WorkspaceCheck已改为原生FXProject引用临时目录资源包TextureMaterial，真实挂包重载后才载入作者；保留同大小同mtime、删除恢复、冻结回读及模型单资产往返断言。编译成功。W1实机在初次重载后包装异常退出、assertions为空，当前外层信息不足以归因；已增强新场景报告保存failure_stack并记录真实异常链，下一轮在failure入口核对。证据build/agent-backups/20261101-photon-workspace-w1/results，日志ij_run__Photon_Workspace_W1_2231674735570731872.log；Debug sessions=[]、临时setup断点移除、Sol已关闭。目标保持active，组合/导出仍未验收。

## 完整测试F3诊断与夹具修正（待重跑）

F3在WorkspaceCheck载入后断点确认currentProject=null，实际containsExtensions(project)=true：夹具将FILE模型加入普通FXProject，现有EditorMixin格式门禁正确拒绝打开。未绕过生产门禁；改用正式PhotonModelProject的既有模型对象承载FILE依赖，再添加真实激光原生成件，并增加project_loaded断言。修改后IDEA错误0、BenchMod Compile及生产包隔离通过（日志ij_run__BenchMod_Compile_10775974074924045374.log）。F3已正常退出、Debug sessions=[]，两个临时断点已移除。下一步F4独立目录重跑完整矩阵；当前不称组合/导出已通过，完整目标仍active。

## 完整测试F2进展（未完成）

F2实机76项断言通过、断言失败0，但随后WorkspaceCheck.begin调用真实组合begin时报“请先打开激光作者工程”，故completed_checks_passed=false。真实worker晚到、保存取消、确认退出及夹具宿主重挂均已通过；组合状态/依赖/冻结导出尚未执行，不能称完整测试通过。下一步在真实组合begin入口Debug核对测试editor的currentProject与载入路径，不凭推测改生产逻辑。证据build/agent-backups/20261101-photon-full-logic-f2/client/agent-results/photon-logic-regression-20261101/report.json，客户端PID66420已退出，Debug sessions=[]，本轮worker断点已移除。

## 完整测试F1进展（未完成）

F1实机真实worker晚到6项断言全部通过：后台normalize断点处客户端取消旧修订、放行前future已取消、后继完成、旧提交未发布。真实保存框取消和runtime身份保持通过；确认退出阶段LDLib2末项hideHost关闭测试宿主，runner误判为意外卸载，F1报告保留失败。runner已只在实际原生确认调用栈内接纳预期卸载并重挂自有宿主，错误级静态0项，尚待F2重跑。WorkspaceCheck已交付并接入，依赖与冻结导出待实机。F1证据build/agent-backups/20261101-photon-full-logic-f1/client/agent-results/photon-logic-regression-20261101/report.json；游戏已退出，临时worker断点移除，两Sol已关闭，完整目标保持active。

## 2026-11-01 完整矩阵补测进行中

用户持续目标为“进行完整测试”，58项通过仅为已有子集。当前新增LateWorkerCheck，真实normalize worker暂停后由客户端取消旧来源，后继真实任务成功用于证明旧回调已消费且未发布；尚未运行。CloseCheck已落盘，runner已接入晚到、保存取消两阶段并通过错误级静态检查，确认退出后的真实清理及桥接拒绝仍待补证。WorkspaceCheck此前仅完成研究未交付，已明确要求继续真实组合状态、外部依赖自动探测与冻结导出回读实现；不把研究记为完成。独立配置Photon Full Logic F1及隔离gameDir已创建，尚未启动；完整矩阵结果仍未通过，任务保持active。主线负责集成和实机验证，两项Sol仅写独占新增夹具。

## 2026-11-01 独立游戏逻辑回归：58项通过

新增独立 Bench 场景 lobecorp.photon-08-logic-regression，精确注册后返回，不修改旧场景。R3/R4保留失败证据；R4 Debug确认ADDED已写入NONE，而isDisplayed仍读布局旧值。生产控件通知的去重改用getValueImmediate(LayoutProperties.DISPLAY)，避免同帧反向迁移漏写；夹具同样读取即时样式，不再把未解析布局当事件失败。

R5独立JVM PID45280，58项断言全部通过、0失败，Bench最终run_complete PASSED；编译与生产包隔离通过。游戏内嵌编辑器截图已回看；本轮无性能结论，JFR关闭，spark仅加载且后台采样关闭。证据根build/agent-backups/20261101-photon-logic-bench-r5/client，断言agent-results/photon-logic-regression-20261101/report.json，截图screenshots/logic-verified.png与logic-world.png；Bench报告位于同前缀r4/results，实际游戏目录为r5，路径差异如实保留。报告full_requested_matrix_passed=false：保存对话框取消、确定性真实worker晚到、组合时间/播放与冻结导出仍未覆盖，不称完整矩阵或视觉等价验收。

最终Debug sessions=[]，本轮断点已移除，PID45280与临时桥接46800/51932均已退出，Sol已关闭。规范同步已提交2215c05、2ee1ff8；业务代码仍在原WIP中待精确集成，不混入原配置、测试、run-control和audit工具暂存。

## 2026-11-01 游戏测试启动排查（未通过）

已创建独立 Photon Logic Toolchain 20261101 配置，使用 photonBenchClient 与端口19877。实际客户端确认加载 spark、modbench_runtime、mcpmod、sodium；首次运行目录缺失及随后 Bench 必填 resultDirectory 缺失均保留失败日志。补充基础参数后 Bench 自动启动默认 sweeper-render 并退出，不能作为 Photon 逻辑验收。尝试在独立 JVM 通过 Debug 置 attempted 停止默认自动入口，也未得到持续可用客户端；没有 report.json 通过证据、未完成截图和完整断言，不能宣称游戏测试完成。最新日志 ij_run__Photon_Logic_Toolchain_20261101_3581605753112257345.log 位于 IDEA 临时目录；Debug 最终 sessions=[]，本轮临时断点已显式移除。下一步应建立 Bench 正式独立逻辑场景或确认真实无自动测量入口，不继续沿用空场景默认启动。

并行非性能测试授权已收到。规范960aedf的三方cherry-pick被Git以现有本地修改阻止，未覆盖WIP；同步尚未完成。

## 2026-11-01 迁移后主线验证（进行中）

已在原有 codex/photon 工作树接续，基线 HEAD 为 8d3a5e5，保留全部既有暂存和未暂存改动。IDEA 执行 PhotonOwnedPreparationSchedulerTest，7 项通过、0 失败、0 错误，XML 时间为本日 15:45；覆盖独立重试期限、cancelSnapshot 保留 DEVICE、来源取消及旧代次失效。BenchMod Compile 成功，生产包无 Bench 内容检查通过；reserveBuildIdentity 的既有配置缓存问题导致缓存丢弃，未导致构建失败。Data 成功，23 份生成输出 written=0。

正在审查事件准备与维护性业务差异及独立游戏逻辑入口。尚未启动本轮游戏或性能采集；完整运行矩阵、视觉与 P22/P23 未验收，不以这批 JUnit、构建和 Data 替代。测试证据为 build/test-results/test/TEST-org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationSchedulerTest.xml；IDEA 构建与 Data 日志分别为 ij_run__BenchMod_Compile_9169604801606548857.log 和 ij_run__Data_16479489654129160222.log（IDEA 临时目录）。

## 2026-10-31 两项P2后完整18局复测

用户明确复跑普通/光影×憎恶25秒、单憎恶三次激光、空白各3次，18独立JVM/世界全部PASSED，18次STARTED、单憎恶/组合并发1、每次15组成件活跃、无回退/错误/重叠，尾迹34.530～34.607秒结束。受测eaf6155及原WIP不变，旧场景不改，新控制独立归档。加载39.408～48.321秒；18份启动JFR PID匹配且每局1份启动录制，最早1.104～1.406秒只有墙钟；144原始文件SHA一致，六设置/五保护文件SHA恢复保持、游戏/Debug/observer回收。

P22/P23开放：普通/光影激光最坏377.598/309.489ms；普通空白最坏207.492ms。与旧18局描述比较多项下降，但代码跨迁移及多项修正、后台负载未隔离，不能归因两个P2或称卡顿根治。10局span环覆盖，完整帧CSV/聚合另行核验；无新截图/完整视觉、故障注入、同ID导出负例或长时reload验收。 结果见[BenchMod](benchmod.md)，本地.agent/reports/20261031-photon-p2-hybrid18-final.md。编译/生产包隔离通过，本轮未追加Data/JUnit，未推送。

## 2026-10-31 两项P2代码修复（仅IDEA验证）

`PhotonOwnedEffectResources`将已读取的CPU Prepared与公开READY结果分离保存，初次发布和设备重建共用准备入口。失败保留快照，同设备戳每秒最多一次尝试，新戳立即允许，成功转READY并清重试记录；资源重载替换快照并清旧代次记录，读取失败无快照不重试，公开Result约束保持。`PhotonModelPackExportUtil`在capture和export写包前遍历原生实例与自定义子源引用，拒绝任何层级与规范化根ID相同的依赖；定义同路径不同内容也拒绝，避免静默覆盖或self跳过。

仅两个修改Java文件IDEA检查：0 error，6项提示均在原有代码；新增两个helper各2处语义引用已核对。按用户明确边界未构建、Data、JUnit、Bench、游戏或Debug，不宣称运行、视觉或长帧验收；P22/P23保持。中转站v4.1-flash low只读复核完成，两个P2静态修复闭合，无新增确认P1/P2。本地结果`.agent/reports/20261031-photon-p2-fix-final.md`。

## 2026-10-31 Photon负责范围审查修正（禁止后续游戏）

修复PROJECT子池Scope退役、不可复用租约自然排空后回收、子生成器停止后残留计数、设备代次失效和失败重试、Map退化迭代续接、FILE最新几何冻结及自定义子源/.mfx依赖适配。FILE导出在独占配置准备后只写来源/资产字段，完整guard通过才提交，发布校验保持。迁移fixture改用真实受管租约与自然排空，最终报告必须后置且核对frames/session/hash，避免假阳性。

37限定JUnit、3Node测试及首轮编译/生产包隔离/Data通过、23生成JSON不变；后续FILE顺序补正IDEA无error、最终编译/生产包隔离退出0。P01-r1独立review场景曾失败且保留，不计游戏通过；用户新指令禁止游戏，P01-r2/光影未启动。六设置已按原字节恢复，两个Sol已关闭。DS旧对话上下文超限，精简新中转站v4.1-flash low接续确认后旧对话归档；补充静态报告已收敛，无新增确认P1/P2；主线另修验收资源数在add前检查导致末项超一的低风险边界，改为add后检查，保持重复ID去重语义。静态批次不是逐行全覆盖，P22/P23保持开放，无新增游戏/视觉/FPS通过结论。本地报告 `.agent/reports/20261031-photon-workspace-review-final.md`。

## 2026-10-31 Photon26.1.2.9当前交付

2026-10-31 同平台迁移完成：Photon26.1.2.9、LDLib2 26.1.2.43、KilaGraph26.1.0.16.a；MC/NeoForge/JDK保持。旧18轮深入归因确认旧Star运行时预热和私有FX重复编译；迁移补齐显式准备来源、世界/资源代次、已知UUID删除重插的数组顺序、ValueIO/FXInstance/FXPack和新全屏合成Iris边界。最终普通/光影×NATIVE/HYBRID四条路径各1局通过，共12次激光及归档后契约通过；不是三轮/18局复测。HYBRID两局主窗私有编译0、缓存命中60、池命中/复位238、失败/布局回退0；最坏仍普通427.216ms、光影269.515ms，首发SharedPrepared/命名/字段回退/姿态几何及旧空白未知区间使P22/P23继续开放。编译/生产包隔离/Data、12项定向JUnit通过，23生成输出不变，六配置与五原WIP/暂存SHA恢复保持，游戏/Debug/observer及8子代理已回收。完整本地报告.agent/reports/20261031-photon26129-migration-final.md，证据build/agent-backups/20261031-photon-26129-migration；原失败和带Iris提示结果保留，未推送。

## 2026-10-31 HYBRID与自有激光18轮实测

新增独立queen25/laser3/blank25场景，普通/光影各三轮，R2有效18个新JVM/世界全部通过；激光6局共18次STARTED，同一queen、组合并发1、15组件活跃、无fallback/error/overlap及期限内尾迹回收。blank45并裁前25，laser固定10/20/30秒，加载39.573～60.971秒另录。4前置试跑（2通过、2失败）保留不计R2；测试发现局部动画公共参数分发与RECOVERY末端cue漏消费，已仅修自有组合层并重编后完整重测。

功能门禁通过不等于不卡顿：普通/光影生成组最坏804.061/781.529ms、激光550.950/705.342ms；光影空白1022.659ms发生于进入后1.721～2.744秒。P22/P23开放，不能与旧不同负载轮次算优化比例。18局源清单一致、JFR PID及单份启动录制匹配，9局raw span环覆盖明确记录；HNB01部分帧失焦但无throttle，原样保留。编译/生产包隔离/Data通过，23生成JSON不变，六配置/五原WIP暂存SHA保持，游戏/Debug/observer/2子代理回收，未推送。完整报告.agent/reports/20261031-hybrid18-final.md，证据build/agent-backups/20261031-hybrid18。

## 2026-10-31 自有激光v2剩余实现完成

15独立组成件及可序列化组合阶段/绑定/参数/结束策略已接通；实际光束内部激活时序外移，reverse_muzzle保留PROJECT喷口模板。资源级准备覆盖递归FX、模型/拓扑、材质图、基础/实例化管线和原版纹理管理器；运行私有resolver消费同一准备成果。组合工作区支持编排、暂停/步进/seek、实时作者修订、工程保存恢复、冻结包导出与有效资源包回读，作者/发行/发布预览来源分离，局部预览不清Photon全局缓存。补齐帧插值及新路径旧laser预热隔离；原生/旧资源保留，仍-Dlobecorp.photon.ownedLaser=true显式选择，默认旧生产路径。

15 Java限定IDEA静态0 error/27 warning、2 JSON静态无错误；未构建/Data/测试/游戏，不宣称视觉等价或长帧收益，P22/P23继续开放。本地.agent/reports/20261031-owned-laser-completion-final.md及三份handoff记录调用链/备份；四个本轮子代理已关闭，原WIP与staged run-control保护，未推送。

## 2026-10-31 自有激光组合首批接入

六外层组件独立FX资源；资源reload客户端静态定义准备与作者内存修订分开，按阶段创建组件。仅自有编辑器/女皇激光可选接入，旧资源和Photon原生保持，启动属性lobecorp.photon.ownedLaser=true，默认旧路径。6 Java静态0 error/7 warning；未构建/Data/测试/游戏，不能称视觉或性能通过。光束内部细分、完整模型/材质GPU前置、帧插值及完整组合工作区仍待完成，P22/P23开放。源及备份见本地.agent/reports/20261031-owned-laser-implementation.md与core-handoff；原WIP保留、代理关闭。

2026-10-31 单女皇进入25秒独立窗口完成：原生普通/光影与空白各三局，共12新JVM/世界；5秒生成、25秒结束，加载另录。女皇生成后最长普通839～993ms、光影875～1240ms，空白同期14～46ms且无>100ms；P22/P23保持。新Scene/ID/控制追加，旧场景不改，六配置恢复、3代理关闭。[完整对照](benchmod.md#2026-10-31-单女皇进入后25秒与空白对照)，本地报告.agent/reports/20261031-queen-entry25-final.md。


2026-10-31 全方案再测完成：3eb1e0e生产代码不变，12独立JVM/世界、36触发、每局74契约通过。A的P99、B的>100ms频次、C的分配各有优势，跨轮原生帧率明显不同；默认NATIVE、P22/P23保持。[复测结果](benchmod.md#2026-10-31-实例创建全方案再次复测)，本地报告.agent/reports/20261031-runtime-redesign-retest.md。


2026-10-31 实例创建重设计：研究后完成A/B/C、四套独立版本及12局36次，每局74宿主检查通过。C的>100ms数量24→15、平均分配约降26.8%，但最坏峰值402ms仍高于原生250ms；默认NATIVE，P22/P23开放。见[创建架构](photon-runtime-creation.md)、[三轮比较](benchmod.md#2026-10-31-实例创建架构三方案对照)，本地完整报告.agent/reports/20261031-runtime-redesign-final.md。


2026-10-31（10-30并行批次收尾）：退出clearSession纯清理与EMBEDDED失效键生命周期回收已修复/复验；复制/材质细账、旧JFR长帧归因和opt-in内存检查已交付，未冒称新增复制或材质提速。6 Java/7 Python、编译/包隔离/Data通过；v1两局+最终v2复验共9触发/82原生检查，120引用每局回收，最终3死键630868B清零、RESOURCE4保留。退出GUI根因三轮不再出现，P22/P23长帧仍开放。见[四线结果](benchmod.md#2026-10-31-四线并行批次收尾)，本地报告.agent/reports/20261031-photon-parallel-final.md。

2026-10-30 类型复制与细粒度预热：两个明确类型直接恢复独占字段，保留原wrapper/guard及不等价函数原生回退；材料/变体任务拆分和clear代次完成。7队列测试、编译/包隔离/Data通过，两个新普通FULL共6触发/44项测量后原生检查通过，六配置SHA恢复。真实根FXData分配仅约降1.22%～1.25%，无墙钟/FPS收益证明，激光最长仍775.821ms，P22/P23及退出异常开放。详见[本轮边界](benchmod.md#2026-10-30-类型复制和材质粒度预热)，本地完整报告.agent/reports/20261030-runtime-copy-prewarm-final.md。

2026-10-30 编码端补齐：生产元数据缓存扩至serializeInternal与deserializeInternal，8项原生回归、三独立JVM的fixture往返、编译/包隔离/Data完成。相对仅解码缓存，fixture分配观测降19.85%～20.60%，不是实际FX或FPS收益；本轮未游戏测试，P22/P23继续开放。中转站Flash辅助实际404无产出，未切路由，两个实现代理关闭。见[编码端对照](benchmod.md#2026-10-30-编码端元数据缓存与离线往返对照)，本地报告.agent/reports/20261030-encode-metadata-final.md。

2026-10-30 新基线v6与优化后v8各12局36触发完成，生产声明类元数据缓存已实现；19项目标测试、三JVM微基准、54项测量后原生集成通过。实际每copy分配约降7.4%，未证明整体FPS改善，P22/P23与进入世界卡顿仍开放。入口见[BenchMod](benchmod.md#2026-10-30-原生持久化元数据首批优化)，本地总报告.agent/reports/20261030-metadata-optimization-final.md。


2026-10-30：四组三连发矩阵12局36触发完成，六局FULL总体/场景PASSED，截图0；有界容量与离线审计工具修正、实际配置恢复已完成。长帧和完整性能/视觉边界仍开放，详细入口见[BenchMod](benchmod.md#2026-10-30-三连发-full-与无测试参数-run-矩阵)。本地最终证据build/agent-backups/20261030-repeat-matrix-final/。


核对日期：2026-10-24。适用真实根为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`，分支为 `codex/photon`。本页基于当前工作文件、IDEA 文件读取及现有检查记录；已确认的网格／编辑器／光束续做源码与文档已纳入本地提交 330d297a，尚未推送。配置及两份资源验收测试的既有修改继续保留，提交不代表构建、游戏或完整能力验收通过。

## 当前任务进度

2026-10-29 按用户要求只补FX加载信息、不优化：新增直接getFX／loadFX／反序列化／guard与模板复制诊断；原缓存、校验、预热和粒子负载不变。四项账本测试、编译／生产包隔离、Data及普通三轮已完成，测试截图为零，光影未启动。star_beam_spell_beam.fx首个查询1.521–4.521秒，反序列化包含1.515–4.507秒，内部对象建立／反射／校验／类加载等仍待细分；P22／P23保持开放。详见[BenchMod诊断入口](benchmod.md#2026-10-29-fx-冷加载与校验分段诊断)，证据根`build/agent-backups/20261029-fxload-diagnostics/`。

2026-10-29 DS补正后普通组三次独立验证已完成：总体／场景均PASSED，真实负载及十秒门禁通过、测试截图为零。controller_changed归零；最长正式间隔245／330／451ms、预热3.42–5.03秒，现场实例仍196–198次，P22／P23未关闭。用户明确光影等普通优化进展，本批未启动光影。主线仅复测与记录，代码为060e9080；证据`build/agent-backups/20261029-ds-verification/`，当前剩余见[问题汇总](../issues.md#2026-10-29-普通组独立复测与光影暂缓)。

2026-10-28 延迟十秒的纯诊断复测：普通／光影各三轮独立进程已完成，六轮实际等待10.008–10.049秒，真实模型负载均通过、测试截图为零。正式窗口最长间隔普通329／452／840ms、光影433／821／993ms；光影末轮初始窗口resize使总体INCONCLUSIVE，用户明确不补跑，保留标记。日志确认SHELL单任务2.007–4.515秒、每轮命名运行态因控制器变化重建一次、子模板现场创建192–199次，P22／P23仍开放。只补门禁与日志，未改渲染／资源算法；Data通过，29生成文件哈希不变。方法与证据见[BenchMod](benchmod.md#2026-10-28-无截图性能采集与进入世界后延迟)，证据根`build/agent-backups/20261028-delayed-performance/`。

2026-10-28 标准实机测试已收尾：用户指定五件套并明确 Photon 独立、不等待 Horde。新增显式资源重载场景，普通 `client-1793187353534`／光影 `client-1793187656047` 四生产模型的共享、真实 reload 后替换及定义／快照／指纹／帧隔离均通过，真实光束 OUTPUT 截图已检查；Spark 实件验证归档后正常退出，标准 MCP 19877、IDEA 和 Debug 均使用。两轮场景 PASSED，但总体 INCONCLUSIVE（真实 reload 的暂停／覆盖层标记），不计为性能或完整视觉等价通过。一次复跑的测试实体清理 NPE 已修复为先收集已标记对象再删除，失败证据保留；最终配置原字节、三断点及临时对象均已收尾。Data 通过、29 生成文件指纹不变，证据 `build/agent-backups/20261028-standard-resource-test/`，详情见[BenchMod](benchmod.md#2026-10-28-模型资源真实重载诊断)。W1 全量失败基线、P22 长帧及完整长期生命周期仍开放。

2026-10-28 架构优化恢复：用户已将 RESOURCE 模型准备共享确定为固定资源能力。已移除启动开关、16 项／16 MiB 淘汰及 Photon 渲染代次依赖，按当前 NeoForge `ObjLoader` 的资源生命周期方式保留已准备模型，由唯一客户端入口注册 `ResourceManagerReloadListener` 并在重载清空。先实际比较现有／NeoForge／Photon OBJ：四个真实原件证实 Neo 需要不同 MTL 输入契约、Photon 能输出几何但丢失原部件／材质名，故保留本项目解析器；修复无 UV OBJ 的默认面角 UV，混合缺失仍明确失败。首轮 48 项有 6 失败并保留原证据，修复后 50/50 通过（比较20、资源生命周期7），`benchClasses jar test` 退出码0，Data通过且29文件不变；IDEA修复后一次编译等待超时由后续实际Gradle编译结果补足。详见[模型特效](photon-model-effects.md#resource-模型准备共享2026-10-28)，备份 `build/agent-backups/20261028-model-resource-lifecycle/`。原WIP保留；未运行游戏或性能A/B，W1全量基线、P22/P23/P24实机及插件正式宿主验收仍开放。

2026-10-27 停工：用户要求重新梳理并收拢所有相关任务。本对话实现代理已停止，协调与Horde均已给出最终清理／停工确认；Horde释放不再自动放行Photon。当前没有自有构建、游戏、Debug或GPU负载，不重启／关闭IDEA、Codex及可能共享服务，等待用户明确恢复。现有两提交和WIP保留；Java／游戏验收、审计脚本焦点逻辑修正、文档布局纠偏、IDEA MCP二进制接口均未完成，不据静态检查宣称验收通过。

2026-10-27 方法约束（以用户最新连续指令为准）：允许自有 Minecraft 游戏窗口最大化并维持焦点；已取消失焦硬门禁，不再仅因失焦判失败、作废或丢弃样本，焦点变化／实际补焦次数仍如实记录。允许游戏内部模拟输入、GUI／控件／坐标点击、后台定向操作和专用 MCP/API，但不得移动／捕获系统实体鼠标、注入全局输入、抢占实体输入或操控其它桌面窗口／显示器。最大化和保持焦点仅用于本轮自有游戏，不恢复显示／还原／移动窗口、切全屏或修改尺寸／分辨率的旧路径。其余相机、GPU、原始身份、最大化／非最小化等验收边界保留；含失焦区间时报告真实情况，不伪称全程聚焦。Horde→Photon 时段顺序不变，不提前启动游戏。

2026-10-27 首发长帧接续（进行中）：当前负责对话为 `01a17d1d-80cf-74b1-a5dc-c8bd660dffc3`；旧 `01a1669e-b2f3-7ca3-831c-69205680df6c` 保留但排除检索、读取、跟踪与投递。原始路由已发布，复用本工作树／分支／19877 端口，不同步原始分支。优先收敛 P22/P23 的 R2 残余与预热成本转移，暂不推广其它大 FX 外部化，不并开 R3/R4。单次施法夹具现已从 setup 起独立捕获预热帧，在主测量前归档；世界出生点为固定锚点，实际相机变动仍会失败；失焦仅记录并尝试保持焦点，不再据此拒绝样本，并记录截图请求时的技能阶段。该阶段代码与静态检查已完成，编译和实机尚未执行；后续已按用户停工要求暂停。此前等待的新 Horde 对话 `01a17d1c-da93-7422-bb8a-1916d4a764d1` 已最终释放；用户全体停工要求优先，当前不自动启动任何后续验证。

本轮取证：初始 HEAD `08890b8b` 与四项用户 WIP 已核对并经 IDEA 原字节备份；交接所列 `agent-backups/photon-r3-20261027/`、比较脚本／图片及 `photon-reaudit-20261026/first-cast/` 在本工作区缺失，不补造旧证据。现存 `client-1793080445861` 是旧提交＋dirty 的诊断参考：实际全程聚焦最大化、实际 2560×1494，捕获窗口最长 153.67 ms、>50 ms 10 帧，稳定阶段另有 1677.16 ms 长帧；预热结束后发射路径仍采到 OBJ 读取／拓扑准备。10 ms 队列预算只限制任务开始，单任务可超时；同世界 reload 后预热标记未复位的路径已确认，预热标记复位、异常诊断 scope 收尾与逐类任务账本已实施并静态检查，无本轮性能／视觉／生命周期通过结论。另确认 RESOURCE OBJ 生产路径未接入伴生 NBT 的 P24：当前已补加载／快照合并及真实模型回归；进一步发现四个伴生NBT原件缺结束标记，已获用户一次脚本例外授权，仅补共6字节并验证原值与原JSON相等；Java回归未运行；须先建立完整表现基线再比较缓存收益。详细报告、指纹和证据原字节位于本工作区 `.agent/reports/20261027-first-cast.md` 与 `build/agent-backups/20261028-agent-doc-cleanup/evidence/first-cast-20261027-existing/`，不提交。

2026-10-27 模型资产外部化（OBJ 几何 + `.mfx.nbt` 附加数据）：新增 `util/photon/model/PhotonModelSplitUtil` 合并 OBJ 与同名 NBT；`laser.fx` 的 4 个模型发射器改为 `source=RESOURCE` 指向 `assets/lobecorp/models/photon/the_queen_of_hatred/beam/*.obj`，骨骼／蒙皮／形变／动画／材质参数存同名 `.mfx.nbt`。为放行该来源，经用户授权修改 `PhotonModelFileValidationUtil#emitterData`：`RESOURCE` 要求资源标识非空、几何与片段引用延后到首次使用时校验，`FILE` 与其它来源仍拒绝（原闸门由 `11c575f8` 引入，只允许 `EMBEDDED`）。体积 405,987 → 63,313 B（−84%），顶点／面数与原 JSON 逐项一致；三轮基准 `built=50, spawners=15, failed=0`、`Failed to load fx` 为 0，与改动前一致。早期此处记为未验收；较晚的 [2026-10-27 交接](photon-effect-rewrite-handoff-20261027.md#55-验证) 记录用户已确认视觉正常。本轮尚未独立复验，所引比较图当前缺失；`define` 仍为约 300 ms，本次改动**不带来性能收益**（目标为可维护性）。证据 `agent-backups/photon-r3-20261027/`。

2026-10-26 提问规则更新：已将根规范与工作流程统一为优先使用当前软件可用的提问 API，没有时直接在对话中提问；保留一次一个问题、等待回答与未回答不视为确认。仅修改规则与本工作记录，保留既有工作；检查范围为 IDEA 回读与文档诊断，不执行构建、Data、测试、同步、重载或游戏操作。原字节备份位于 `build/agent-backups/question-api-20261026-2010/`。

2026-10-26 12:52 R2 首帧渲染侧预热（部分正面，默认开启）：新增 `client/photon/performance/PhotonRenderPrewarm`（`-Dlobecorp.photon.prewarmRender`，默认 true，置 false 关闭）——预热壳内按绘制同一入口（`MaterialSetting#getRenderType`）预构建材质/着色图/CPU 管线，GPU 实例化变体（TILE/MODEL/BEAM/TRAIL/ARA）另经 `PhotonPipelines.graphShader`＋`precompilePipeline`，`PhotonModelOutput.prewarmRenderTypes()` 预准备模型材质，并递归预热子发射器模板（`PhotonChildSpawner.preparedTemplate()`，深度 2 已覆盖 15 个 spawner/50 个渲染类型）；任务队列每 tick 预算 10 ms，避免女皇出现时一次性冻结。单施法同条件 A/B（每轮重开客户端、plain、探针开、spark 关）：基线最长帧 680.3 ms、>250 ms 帧 3、窗口均值 12.84 ms → 预热 129.0～167.5 ms、>250 ms 帧 0、均值 8.03～9.85 ms；首施法 tick 68.3→70.9～75.3 ms 未退化。同步预热（B1/B2）反而更差（797.9/268.7 ms），证明必须递归覆盖＋分摊。剩余 +0.08～1.18 s 处 52～167 ms 长帧，疑为未覆盖的实例化变体与首次模拟类加载，**P22 未关闭**。证据 `photon-reaudit-20261026/first-cast/r2-*`（A/B/B2/B3/B4/B5 日志与 artifacts）。

2026-10-26 12:22 R1 运行态复用实验（阴性，默认关闭）：实现 `-Dlobecorp.photon.poolBeam`（`PhotonGeoEffect.restart()`＋`QueenBeamEffects` 池化 borrow/recycle），91 场景同条件 A/B：pool off 第 1/2/3 次施法 42.8/26.6/34.4 ms、帧均值 6.356、>50 ms 帧 9；pool on 32.0/37.1/42.3 ms、帧均值 8.181、>50 ms 帧 16。复用省掉约 6 ms `createRuntime`，但 `prepareNext` 由 18～27 升到 33～38 ms，整体帧数下降（11002 vs 14161），**默认关闭、代码保留实验**。结论：真正大头是子实例准备与首帧材质/管线，下一步优先 **R3 子模板缓存 → R2 材质/管线预构建**。证据 `photon-reaudit-20261026/pool/`（runA/runB 日志与 A/B-artifacts）。

2026-10-26 12:10 特效运行时底层重构方案：用户选择从底层架构重写而非继续微调。方案写在 [特效运行时底层重构方案](photon-effect-architecture-rewrite-20261026.md)：拆分不可变 EffectAsset（对象树/轨道/网格/材质变体/子模板/音频）与池化 EffectInstance（时间线游标/TRS/发射器/命名状态），迁移 R1 运行态复用→R2 材质/管线预构建→R3 spawner 分离→R4 几何资产空间缓存→R5 批处理→R6 可选 GPU，每阶段开关/回退/同条件 A/B。现有首次释放预热与采样器分配治理作为中间态保留；R1/R2 未开始。可交接的执行手册见 [特效底层重构执行计划（交接版）](photon-effect-rewrite-execution-plan-20261026.md)，含代码地图、R1～R6 步骤、测试命令、验收与恢复检查清单。

2026-10-26 11:54 首次释放专项（tick 侧已修，渲染侧未完成）：按用户要求只测第一次释放、每轮重开客户端。新增 `PhotonCastProfiler`（`-Dlobecorp.photon.diag.cast=true`）与“首次施放”单场景（只施放 1 次、输出截图后立即完成）。实测首施法 tick 总耗时 **695.7→64.4 ms（−90.7%）**：根因是首次 FX 定义解析 438 ms、运行态深拷贝 53 ms、子实例准备 116 ms、emit 72 ms；现改为“检测到 128 格内有女皇后预热 FX 定义＋预建运行态壳＋预准备子实例”（`CASTPREWARM 672～712 ms, prepared=2`，`-Dlobecorp.photon.prewarmBeam=false` 可关）。但首施法仍有约 1.0～1.4 s 的首帧渲染侧一次性构建（Mixin 类转换/类加载、ShaderGraph GLSL import 资源探测异常风暴、JSON 节点图解析、OGG 解码），需 P22 继续；run3/run4 施放截图差异仅粒子相位（mean_abs 1.741、diff>8 2.69%）。详见 [性能审查](photon-performance-audit.md#首次释放专项诊断进行中2026-10-26-1140-起)；问题见 [唯一清单](../issues.md)。

2026-10-26 11:30 复测与优化阶段完成（未推送）：五轮有效运行（P0 旧采样器、P2b 新采样器、P1c 探针关、S1 光影探针开、S0 光影探针关）全部 PASSED、环境 valid；动画采样分配治理实施后，40/90/91/92 场景渲染线程分配下降 59%～72%，16/32 动画 CPU 均值下降 24%～40%，40/90 帧时间与 P99 同向改善；92 帧时间未改善，主因是每次施法 187～232 ms 的 Codec／`IFXObject.deepCopy` 尖峰与 ShaderGraph 材质重建（77% 异常来自 GLSL import 资源探测）。探针对照确认四个提交探针本身使 92 均值 +40%、P99 +22%，历史数据不能当生产帧时间；S0（光影、无探针）E2 均值 8.202／P99 16.422 达标，E3 均值 12.332／P99 36.580 未达标，最长帧 723/247 ms 远超 50 ms。跨轮相机仍因 `holdPose` 还原滞后漂移（A/A′），已按轮记录且不作跨相机 A/B。夹具修复包含显式位姿门禁、焦点丢失/恢复计数、实体普查与 tag 归属清理、主采集/验证时间戳、探针与 spark 开关。详细数字、SLO 对照、事实/推断/假设、复现命令见 [复测与瓶颈复核节](photon-performance-audit.md#2026-10-26-复测与瓶颈复核进行中未验收)；问题只在 [唯一清单](../issues.md)。

2026-10-26 10:20 复测重启与瓶颈复核（进行中）：用户重新授权游戏/构建/测试/Debug，并要求 Sodium 全部轮次保持加载、关闭客户端时等待 Sodium 退出输出。已复核旧结论并写入 [复测与瓶颈复核节](photon-performance-audit.md#2026-10-26-复测与瓶颈复核进行中未验收)；经 IDEA 库源码核对确认 drain/Bloom/上传/每材质 builder、默认不排序、`PhotonModelOutput` 每帧 Input、`PhotonModelClipUtil#clear` 全对象遍历、子实例整份深拷贝、影子 pass 已在 Photon 侧跳过等事实与候选。首次门禁轮 `client-1792980111074` 因 prepare 覆盖 Iris 配置，13 场景全部 FAILED，已作废；已改为 `-PphotonShader` 构建开关与 `-Dlobecorp.photon.bench.probes` 探针对照开关。待完成：相机/焦点/实体归属夹具修正、多轮配对重测、JFR/GPU 归因、有证据的优化与前后对照；当前没有任何性能收益可宣称。

2026-10-26 最终运行与报告完成：Sodium＋BenchMod 四组（无光影／光影×packed 关／开）各13个Photon场景PASSED，共52项，独立Debug与标准Minecraft MCP桥证据已归档。正式采样结束，不再启动游戏。16静态 reserve/Job 2048→1、每Job顶点仍2048、resize=0；动画仍逐顶点，混合激光仍有扩容。无光影两轮相机一致，光影跨轮Y＋7／Z－10；阴性控制波动、诊断开销与背景实体限制收益结论，G0／G1未通过，WP1缺省关闭、WP2～WP7未实施。完整表格、历史同口径比较、JFR／GPU／Debug／日志、事实／推断／假设见[最终对照报告](photon-performance-audit.md#2026-10-26无光影光影四组运行对照与结论修订)。四轮退出GUI异常与背景女皇归属待核实，统一P14／P18；其余未解问题保留唯一清单。游戏与临时桥已结束、用户3个断点保留，隔离Iris／spark／Options逐字节恢复；原有配置、测试WIP与暂存状态保留。文档整理提交3b7d41c已完成；当前授权测试与报告已收尾，后续问题／下一阶段方案留唯一清单，聊天轮换由协调安排，本聊天不自行归档或创建。收尾前5份原文备份于photon-runtime-comparison-20261025/final-report-before。

收尾检查：5份修改文档IDEA均analyzed、无截断、0错误／普通警告；仅本次路径diff --check通过。原CRLF／LF类别逐文件保持，final-audit.json确认52个当前Photon JFR各2次ThreadDump、52项采样无flag异常／帧数不一致。报告修改将以独立中文提交收尾，保留既有暂存测试。

以下为已完成的历史阶段恢复记录，旧的“最后一轮未完成”不再表示当前状态。

2026-10-26 文档整理插入阶段：磁盘盘点 110 份 Markdown，原件／SHA256／HEAD／原始索引已完整备份于 photon-docs-organize-20261026。将测试前／后技术证据并入性能审查，历史移除方案并入实施记录，重复 MCP 导航并入外部制品说明；规则、Skill、记忆、本地未跟踪资料与 lib 发布说明不改原字节。总索引逐项登记整理后的 107 个路径；本地引用审计 1462 处，0 未索引／0 从根 README 不可达，25 份保留资料 SHA256 不变，换行类别无变化。12 份修改文档 IDEA 闭合批次均 analyzed、无截断、0 错误／普通警告；5 个缺文件引用和 1 个旧锚点在原字节保留 Skill 内，统一记录 P09。文档整理已完成，提交后立即恢复原业务最后一轮与报告。原业务已保存于 ced6dc9：三轮各13场景PASSED、无光影候选与最终对照报告未完成；整理提交后恢复该任务，不再新建当前范围之外的优化或运行目标。后续对话轮换由协调聊天安排，本聊天不自行创建、归档或删除聊天／工作树。

2026-10-25 后续游戏对照重新授权：用户明确要求 Sodium＋BenchMod＋Minecraft MCP＋IDEA MCP＋Debug，且无光影／光影均测试，撤销之前本轮游戏禁令。计划独立 Debug 验证后，使用同一完整负载修复版本测试光影开／关×packed 实验开／关四组；测量不设断点，旧样本只作有条件历史参考。现无游戏或 Debug 实例；旧报告和隔离配置已归档到 photon-runtime-comparison-20261025。发现 E2/E3 硬要求光影的夹具不支持普通对照，仅将其改为预期模式校验并输出开关／Mixin 元数据，未改生产渲染。独立 Debug 已确认录制当次 0→2048 顶点、cached 65536 字节命中成功，主窗最大化；诊断运行已正常结束且不作性能样本。首轮光影基线 13 场景 PASSED，16 静态 reserve=60555264、resize=0；E3 主采集 4990 帧，均值／P99／最长 18.034／52.076／301.549 ms。光影基线／候选及无光影基线三轮各 13 个 Photon 场景均 PASSED，客户端已退出；无光影候选尚未运行，最终对照报告待写。光影 16 静态的跨轮相机出现 Y＋7／Z－10 位移，P14 未解决，不能将帧时间变化认定为稳定收益。2026-10-26 现先接入用户授权的 Markdown 整理插入阶段，整理后恢复 Photon Bench Debug 的正常 Run，JAVA_TOOL_OPTIONS 设置 packedSnapshot=true、bench.shader=false，保留当前隔离 Iris 关闭状态，完成最后一轮与报告；标准 MCP 桥固定 19877 已 pong／connected=true，原生工具未连上不冒充成功。退出 GUI 断连异常保留，后续统一报告与清理。额外收到文档整理协调请求，当前先保留本次全部技术证据，待本测试安全收尾后再处理。

2026-10-25 稳定录制漏提交修复：用户再次选择 A，明确授权修复 P11 的录制当次与超预算漏提交，继续禁止游戏测试。已将稳定 cache miss 接入 captureAndSubmit：预算内录制后当次回放，超 32768 顶点直接走原写入链且不分配记录器、不缓存；后续已有快照的默认关闭实验不变。新增三项回归覆盖预算内、恰好上限与超限连续两次提交。IDEA 无新增错误，保留源码 11 项／原测试 Collector 8 项既有提示；主代码／基准编译及生产包隔离通过（13 秒，既有 reserveBuildIdentity 配置缓存警告保留）；三类测试共 16 项通过（快照 4、编码 5、回放 7，均无失败或错误）。无窗口 Data 退出码 0（19 秒，23 项 written=0／removed=0，含缓存的 29 文件指纹不变）。没有运行游戏、实际 Mixin、光影、截图或性能采样，不代表 G0／G1 验收。运行视觉与性能门槛仍未验收。原件及用户 WIP 备份于 photon-stable-miss-fix-20261025，问题只在 [唯一清单](../issues.md) 维护。

2026-10-25 WP1 默认关闭实验：用户选择 A，允许 G0 未验收期间先编码并做非游戏检查；游戏禁令保持。已写稳定快照命中时的整块顶点提交、严格格式／初始状态预检、原生排序保留、每组 1 MiB／输出候选 4 MiB 预算与引用清理。开关缺省关闭，未修改任何实际运行配置。IDEA 静态无新增错误；主代码／基准编译和生产包隔离通过（23 秒，保留既有 reserveBuildIdentity 配置缓存警告）；新增 5 项编码／7 项回放测试及原快照 1 项共 13 项通过，失败／错误均为 0。无窗口 Data 退出码 0（21 秒，23 项 written=0／removed=0，含缓存的 29 文件指纹不变）。这些不覆盖真实 Mixin 应用、Sodium／Iris 组合、GPU、视觉或性能收益，未运行游戏。G0／G1 性能与视觉出口仍未验收，WP2～WP7 未做。既有稳定录制缺口仅登记 P11，未扩大修复。原件、用户 WIP 与后续证据保存于 photon-extreme-wp1-20261025。详见 [WP1 编码记录](photon-extreme-execution-gpt-20261025.md#wp1-编码阶段2026-10-25用户选择-a)，问题只维护于 [唯一清单](../issues.md)。

2026-10-25 极端优化 WP0：32／64 混合模型、1／4 条真实技能光束、极端后空基线、实际相机／刷新率／draw／reserve 与 resize 分离计数代码已落地，主采集和验证截图分窗。用户明确禁止游戏测试，本轮未启动 Client、BenchMod 客户端、Minecraft MCP 控制或采样；G0 运行出口未验收，按依赖顺序尚未实施 WP1～WP7，不宣称性能收益。IDEA 格式与静态检查无新增错误；主代码／基准编译及生产包隔离检查成功（61 秒，保留既有 reserveBuildIdentity 配置缓存警告）；新增 3 项 JUnit 通过（XML 为 tests=3、failures=0、errors=0）；无窗口 Data 退出码 0（25 秒，23 项 written=0／removed=0，含缓存的 29 个生成目录文件前后 SHA256 不变）。这些是非游戏检查，未运行 Client／BenchMod 场景、截图或采样；G0 运行验收仍缺失。执行细节见 [执行文档本轮记录](photon-extreme-execution-gpt-20261025.md#本轮实施记录2026-10-25)，待验收项只在 [唯一清单](../issues.md) 维护。已有用户配置、两份测试及 logs 保留；修改前原件与用户 WIP 备份于 photon-extreme-wp0-20261025。

2026-10-25 极端场景优化计划：新增 [最坏情况优化计划](photon-extreme-optimization-plan-20261025.md) 与 [GPT 执行文档](photon-extreme-execution-gpt-20261025.md)，定义 E0～E4 场景、CPU/GPU/P99/首帧/内存目标、P0～P6 路线、WP0～WP7 执行包、M1～M9 Mixin 目标与 G0～G7 门槛；明确先做提交缓冲和实例化，不用全局阴影跳过或默认降画质。本文只写计划，未改代码或新增采样。

2026-10-25 稳定帧几何快照（第一阶段）：按用户授权修改 [PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java)，对姿态输入未变化的稳定帧记录并回放最终顶点，动画帧保持原路径；新增逐顶点快照一致性测试。BenchMod 8 场景 PASSED，16 静态 JFR 中 `write` 不再居首、`ByteBufferBuilder.reserve` 30.94%；光影激光动画路径 9879 帧、9.110／26.597／835.600 ms，未证明收益。Iris 配置已恢复，未改动画／LOD／资源／技能；后续仍需原生实例化覆盖动画帧。详见 [changes](changes.md) 与 [性能审查复测节](photon-performance-audit.md)。

2026-10-25 BenchMod＋光影激光复测（阴性）：用户授权按测试结果优化；BenchMod 8 场景 PASSED，光影激光基线 11033 帧、8.158／15.519／764.657 ms、窗口 8.841／8.017／7.846 ms。JFR 静态 16 的 `write` self 33.3%、`List12.get` 12.3%。尝试几何热循环、模型输出阴影跳过、Photon drain 阴影跳过三项候选，均无收益或更差（8.528／8.866 ms），全部回退，源码恢复 HEAD；Iris 配置已恢复，证据与结论写入 [性能审查复测节](photon-performance-audit.md)。下一步应转向原生实例化／分 pass 结构，不再用微调或跳过阴影换数字。

2026-10-25 证据分层复核：按用户“不修改代码”的范围，只读核对既有 Sodium JFR／帧／GPU／日志，未启动游戏或新增采样，未改代码及分析器。主诊断页新增 F1～F5 确认事实、I1～I4 证据支持推断、H1～H7 待验证假设及各自所需证据；明确 deepCopy 代表栈并非全部来源分摊、后台采样请求关联并非净收益、native 样本并非 API 精确耗时。O1～O6 建议、参考和对应源码继续保留，问题状态不变；文档原件备份于既有诊断证据目录的 evidence-tier-review-20261025。

2026-10-25 Sodium 诊断与文档已完成：已完成普通完整 profile JFR 与续跑光影 profile JFR 的流式、分阶段及最长帧分析；中断前光影只到 SETTLE，单独隔离，不冒充有效轮。确认持续子树发射经 includeController／shallowCopy 仍触发深拷贝；两轮另各有后台 spark 发起的约 9000 次 ThreadDump，并以 safepointId 将 168／103 ms 安全点关联到取栈请求，测量隔离需优先处理。普通 602.670 ms 首发、光影 1579.635 ms 热尖峰均保留；后者 native 样本以 Bloom 帧缓冲切换和 fence 为主，未记录该窗口 GC 暂停，不能全部归因模型或 GC。本轮没有修改生产代码／动画／LOD／资源，客户端均已退出、Iris 原始字节恢复。方法、证据等级、建议、对应代码及参考文献已汇入 [最新性能诊断](photon-performance-audit.md)，问题清单新增 P17 并补充 P06／P12／P16；实际静态检查与备份见 changes.md；完整证据沿用 photon-sodium-diagnosis-20261024。

2026-10-24 Sodium 深入诊断进行中：用户授权进一步定位瓶颈并整理证据、推断、建议、参考与代码对应。本轮不实施性能修复；将以同代码 Sodium 普通／光影的 profile JFR、帧／GPU 与日志对齐，区分空基线、首发、热施放和等待，另核对 P16 的 Iris fallback 源码。现有 QueenBeamAllocationCapture 仅启用分配／GC／编译事件，不把它当 CPU 执行采样；采用本次启动参数临时开启完整 JFR profile。进展沿用本页，分析汇入 photon-performance-audit.md，问题仅在 docs/issues.md；原件备份于 photon-sodium-diagnosis-20261024。

2026-10-24 普通／光影测试已完成：用户重新明确授权本轮游戏测试；同代码 9a863816、Sodium＋MCP 各采集 90 秒／三次施放，阶段完整，测量后的绑定语义回归两轮均 3／3 通过。普通均值／P99／最长 1.740／7.411／596.336 ms；ComplementaryReimagined r5.9.3 为 7.974／15.383／587.785 ms。截图中两种模式均可见女皇、法阵和光束，但首发与后续尖峰仍在。光影日志另有 Iris Missing program 的 Photon bloom／合成／graph override 诊断，登记 P16，不能宣布光影兼容完全通过；不证明稳定优化收益或完整视觉验收。Iris 文件原始字节已恢复，两个游戏与五个桥接已正常关闭；不改动画、LOD、资源或生产代码。详细结果见 [架构报告第十节](photon-render-architecture-mixin-20261024.md)，问题只在 docs/issues.md，原件与实测备份于 photon-normal-shader-20261024。

2026-10-24 渲染架构复核已收尾：按样本口径、原生 API／shader、动画与所有权、运行回归反复核对；已修正两份 DS 架构报告，只实施 M7 的绑定槽预解析与空结果延迟分配，原动画／LOD／资源／渲染后端不改。普通 JUnit 因 FML ModList 未初始化而未进入断言；改为测量结束后的真实客户端三项回归，候选与重复轮均通过。三份 Java IDEA 0 错误、2 项既有提示，编译及 Data 成功且 23 项 written0／removed0。前后均值 3.458→3.327 ms，但 P99／最长未改善；同候选重复均值 15.597 ms、空基线 14.011 ms，后续同候选又变快，稳定收益未验收。误标 reference 与叫停后的自动夹具输出分开归档，不冒充原实现验收。用户最新要求不再游戏测试，所有自有客户端与桥接已正常关闭，后续只做静态审查／文档。实际证据见 [架构复核第九节](photon-render-architecture-mixin-20261024.md)，问题仅在 docs/issues.md，原件与结果备份于 photon-render-review-20261024。

2026-10-24 12:26 激光优化续做完成：已审查 photon-laser-optimization-20261024.md，纠正全程均值当稳态、长帧排除 GC、单槽只尝试一次、输出必重建命名运行态、阶段持续时间及合成采样套用真实激光等表述。方案限定为复用相同内嵌内容的不可变模型准备结果，实例运行态／材质／时钟独立。优化前 Sodium＋MCP＋JFR 基线已完成并归档（90 秒、37072 帧）；内嵌准备缓存与调用接入已完成，三项回归 3／3 通过，IDEA 三份代码 0 错误、1 项既有提示，Data 成功且 23 输出 written=0／removed=0。相同 JFR 开关下的优化后复测与额外无 JFR 复核均已完成；三次施放阶段每轮齐全。JFR 前后均值／最长 2.428／1157.199 → 1.980／821.030 ms，但空基线也变快；无 JFR 复核再现 6.15 ms 帧节奏且首发仍为 677.065 ms。局部准备复用已实现，稳定收益未验收，P06／P11／P12／P14 保留。三轮游戏经 MCP 正常保存退出，六个桥接均退出 0；后续先补相机／节奏可比性及准备／驱动阶段归因，未实施预热或队列扩展。实测与边界见 [激光优化报告](photon-laser-optimization-20261024.md)。提前预热、扩大队列和 GPU 预热尚未证实，不实施。进度与实际结果沿用该激光报告，问题仅在 docs/issues.md；原件备份于 photon-laser-content-cache-20261024。

2026-10-24 11:25 激光复测完成：bd29ff6 上的真实女皇激光采集 90 秒／52033 帧，三次施放均 STARTED，客户端阶段 0→1→2→移除齐全；第四次输出截图可见女皇、法阵、光束和目标。帧间隔均值／P99／最长为 1.730／8.529／608.366 ms，全帧聚焦最大化、无节流；首发仍有四个 400～608 ms 长间隔，热施放仍有 81／89 ms 尖峰，P06 未解决。动画逐帧字段仍为空，严格同视角 A/B 和稳定缓存收益仍未验收；P11／P14 保留。结果与限制已写入 [测试后报告](photon-performance-audit.md#2026-10-24-历史模型诊断与法线缓存对照)，备份于 photon-normal-cache-laser-20261024。游戏经 MCP 正常保存退出，桥接均退出 0；未修改生产代码、动画、LOD 或资源。下一步细分首发准备／复制／GPU 首用成本及热施放尖峰；本轮未实施这些优化。

2026-10-24 法线缓存任务：已完成 DS 报告修订、基线归档、实例缓存实现及前后采样记录。MCP／Debug 仅用于测量之外的实例和失效路径确认。报告修正、实例法线／行列式缓存及三项新增数值回归已完成；IDEA 三份代码 0 错误、9 项既有提示，测试 5／5 通过，Debug／MCP 核对实际命中与失效键后已正常退出。有效原实现基线和最终候选两轮均完整保存；复核出现统一约 6.15 ms 帧节奏，前后截图视角也存在差异，稳定性能收益和严格同视角视觉 A/B 尚未充分验收。下一步应先核对实际渲染相机和帧节奏，再受控配对；P11 不关闭，未改动画、LOD、材质、排序或资源内容，详情见 [测试后报告](photon-performance-audit.md#2026-10-24-历史模型诊断与法线缓存对照)。备份根为既有可视化目录下 photon-normal-cache-20261024。

最近更新：2026-10-25 13:25（北京时间，极端场景优化计划）。计划入口为 photon-extreme-optimization-plan-20261025.md；当前代码仍只含稳定帧快照，后续按 G0～G7 逐步实施。

- 此前详细列表阶段：[待优化详细列表](../issues.md#待优化详细列表)集中列出 P11／P12／P06 的确认热点、P13 的待归因异常和 S01–S23 后续处理；包含代码入口、执行顺序、实现约束与关闭条件。该阶段仅整理文档；后续获授权的法线缓存实现与采样已记录于上方，其他优化建议仍未实施。
- 已完成真实激光复测（2026-10-24 11:32）：Sodium＋BenchMod＋Minecraft MCP＋Debug，90 秒／51432 帧，首发 4～7 秒长帧合计约 2.118 秒、后续施放最长 80／68 ms；路径、数字与优化方案见 [真实激光测试与优化方案](photon-laser-optimization-20261024.md)。生产代码未改。
- 已完成光影激光测试（2026-10-24 12:34）：ComplementaryReimagined r5.9.3 下 7672 帧、空场景 9.320 ms、施放窗口 20～25 ms、首发最长 717 ms；JFR 显示 Iris 36.8%（阴影 31.5%）而 Photon `write` 仅 3.0%，微优化无法覆盖；架构级方案见 [架构报告](photon-laser-architecture-20261024.md)。测试后已恢复 Iris 配置，包文件保留。
- 已完成同代码无光影配对轮（2026-10-24 12:55）：42028 帧、空场景 1.843 ms、施放窗口 6.275／3.944／3.953 ms、首发最长 981.357 ms、GPU 均值 0.642 ms；与光影轮同基线 `6ae3093c`，数据已补入架构报告与 P15。
- 已完成渲染架构与 Mixin 方案报告（2026-10-24）：核对 Photon 原生实例化 `MODEL/MODEL_VAT`、`BeamEmitter` GPU 光束路径与 `IrisCompat.isShadowPass`，给出阴影／Iris 路由、首帧预热等 Mixin 目标；另含参数传输、命名动画与骨骼 GPU 化方案（NamedRuntime/Mixer 约 13%）。只写报告未改代码，见 [架构与 Mixin 报告](photon-render-architecture-mixin-20261024.md)。
- 此前 DS 诊断：BenchMod Photon 两次运行（一次窗口失焦 INVALID、一次 PASSED），JFR 归因、Debug 三处调用路径核对与 Minecraft MCP ping／截图；根因与修正建议见 [测试前文档](photon-performance-audit.md#2026-10-24-历史模型诊断与法线缓存对照)、[测试后文档](photon-performance-audit.md#2026-10-24-历史模型诊断与法线缓存对照)。未修改业务代码，无优化后 A/B。
- 已完成：正式 Photon 资源迁入模组，118 项目标与原始 SHA256 一致，资源与文档提交为 1a5c2c3；81 项新增，37 项复用，原始字节备份见 [资源说明](resources-datagen.md#photon-内置资源)。
- 此前性能采样：无 Debug 的 8 个 Photon 用例与最终报告校验通过；两轮真实激光各 90 秒／3 次施放，完成 spark 开关和 GPU 帧数据对照。16 静态／动画均值 3.258／5.784 ms、P99 6.256／10.477 ms；静态逐帧几何写入、动画 CPU 蒙皮／分配及同步创建已被调用栈与样本确认，完整证据见 [性能审查](photon-performance-audit.md)。
- 此前基准接入收尾：Provider 增加显式 Photon 过滤守卫，普通基准不自动登记这些负载；再编译成功（3 秒），最终代码／配置 0 错误、2 项提示，5 份文档 0 错误、18 项既有截图警告。该阶段未优化生产渲染代码；本轮仅法线缓存已实现，完整几何提交与动画采样优化仍未实施，后续需先补可比性再复测；当前问题与待确认项只在 [唯一问题表](../issues.md) 维护。
- 此前采样检查与边界：IDEA 静态 0 错误、两项提示；Gradle 准备编译、无 Debug 最终报告校验、两轮真实客户端均正常结束。固定 19877 MCP 已取得 pong／connected=true；本轮临时断点移除、Debug 结束，所有临时游戏和桥接均关闭。原始报告分别备份于可视化根 photon-bench-normal-baseline-20261024、photon-bench-debug-baseline-20261023、photon-real-laser-spark-20261024、photon-real-laser-plain-20261024。未实施生产优化，没有优化后 A/B 收益；S01–S23 不宣称全部解决，动画逐帧字段为空和未归因长帧等边界见唯一问题表与性能审查。

## 职责与工具边界

本工作区负责 Photon／特效；原始 dev 是共同地基，清道夫由 horde 工作区负责。共同实体、动画、网络、注册和资源文档继续保留引用，本轮没有验证或更新 horde 业务。旧 `C:/Users/wang_/.codex/worktrees/7903/Lobecorp` 仅为兼容 junction，当前工具显式传入真实根，不删除兼容入口或 Git worktree 保护锁。

- 规则：[项目规范](../../AGENTS.md)、[工作流程](../standards/workflow.md)、[系统索引](README.md)。
- Minecraft MCP：[外部制品](minecraft-mcp-artifacts.md)、[本工作区接入](minecraft-mcp-workspace.md)。只消费本地 `lib/minecraft-mcp-0.3.0.jar` 与 `lib/minecraft-mcp/bridge`，端口 `19877`。不恢复 `modules/minecraft-mcp`，不在消费者安装 npm 或重建桥接。
- Normify：[工具说明](../../tools/lobecorp-normify/README.md)。本工作区配置复用原始仓库 `tools/lobecorp-normify/dist/src/cli.js`，`--workspace-root` 和 cwd 均为本工作区；CLI 共享不表示数据输出可跨工作区。
- MCP 工具可见、IDEA 可读与游戏连接是独立状态；本轮法线缓存 Debug／MCP 结果及清理见当前进度与测试后报告，不沿用历史连接状态。
- 锁定依赖：[版本目录](../../gradle/libs.versions.toml)，Minecraft 26.1.2、NeoForge 26.1.2.100、Photon 26.1.2.3、LDLib2 26.1.2.42、KilaGraph 26.1.0.15；MCP 制品清单标记 Java 25。

## 当前能力与源码入口

下表的“存在”指当前源码接线；只有明确引用的历史记录包含当时验证，本轮文档审计没有执行业务测试。

| 功能 | 当前入口 | 能力与限制 |
|---|---|---|
| 扩展编辑器 | [PhotonEffectEditor](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonEffectEditor.java)、[编辑器说明](photon-editor.md) | `/lobecorp photon_editor` 为扩展窗口，`/photon_editor` 保留原生入口；两者的工程枚举与扩展加载有隔离 |
| 模型工程 | [PhotonModelProject](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelProject.java)、[PhotonModelWorkspace](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelWorkspace.java) | 原生 FX 承担预览；模型工程保留原始容器、meta、data 与未知字段；当前模型格式为 .mfxproj／.mfx／.mfxpack |
| 模型输出与轨道 | [LcPhotonModelOutputTypes](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonModelOutputTypes.java)、[PhotonModelOutput](../../src/main/java/org/unitego/lobecorp/client/photon/model/PhotonModelOutput.java) | `lobecorp_model_emitter` 与 `lobecorp_model_animation` 已注册；动态属性和模型采样由当前 model 包承担，旧专用渲染器不可据此视为恢复 |
| 高级网格 WIP | [PhotonModelWorkspaceMesh](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelWorkspaceMesh.java)、[PhotonModelEditUtil](../../src/main/java/org/unitego/lobecorp/util/photon/model/editor/PhotonModelEditUtil.java) | 选择、变换、挤出、复制、删除、UV、拓扑、环切及几何创建接私有候选和会话提交；与旧备份完整对齐及人工交互仍未验收 |
| 材质、骨骼与动画 | [PhotonModelWorkspaceResources](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelWorkspaceResources.java)、[PhotonModelPoseSampler](../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelPoseSampler.java) | 材质副本显式应用、骨骼／morph 和蒙皮采样存在；不声明材质视觉、全部资产或完整编辑行为通过 |
| 命名制作与播放 | [PhotonNamedController](../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedController.java)、[PhotonNamedRuntime](../../src/main/java/org/unitego/lobecorp/client/photon/playback/PhotonNamedRuntime.java)、[PhotonNamedAuthoringSession](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonNamedAuthoringSession.java) | 控制器保存制作定义，所属 FXRuntime 持有运行态；草稿、原树事务、句柄、轨道及测试预览接当前 playback 实现 |
| 子特效／发射器发射器 | [LcPhotonSpawnTypes](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonSpawnTypes.java)、[PhotonChildSpawner](../../src/main/java/org/unitego/lobecorp/client/photon/playback/spawn/PhotonChildSpawner.java) | 保留类型 ID `lobecorp_emitter_spawner`，由当前调度器创建独占原生 FX；最多准备下一份未发射实例，来源变化或关闭时销毁 |
| 女皇技能表现 | [QueenBeamEffects](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java)、[技能边界](queen-beam-skill-effects.md) | 消费权威动画快照，帧回调更新锚点／参数；瞬时星束为已见蓄力的实例补一次呈现，伤害与时序仍由服务端技能决定 |
| 编写态／预览态 WIP | [PhotonAuthoringPose](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonAuthoringPose.java)、[PhotonAuthoringSnapshotUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonAuthoringSnapshotUtil.java) | 序列化副本排除仍由预览持有的分量，保留外部修改；F11／F12 原生复现尚未重跑 |
| MCP | [API 索引](photon-effect-mcp-api.md)、[LcPhotonMcpCommands](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonMcpCommands.java) | 当前源码登记 7 个读操作与 13 个写操作；能力位与路径以实际会话返回为准，登记数量不等于当前游戏已连接 |
| 性能检测 | [检测工具](photon-performance-tool.md)、[静态候选](photon-performance-audit.md) | 保留通用采集与分析入口；旧专用模型／动画统计和历史时间数字不自动适用于当前实现 |

## 专题与历史索引

| 目的 | 文档 | 阅读边界 |
|---|---|---|
| 当前问题与恢复 | [Photon 唯一问题汇总](../issues.md) | 仅维护未解决项；已完成能力、实际检查和备份保留在本页 |
| 编辑器布局来源 | [原始设计恢复](photon-editor-design-restore-20261022.md)、[编辑器](photon-editor.md) | 04a3bab 为设计来源；2026-10-22 恢复后的交互／视觉未全验收 |
| 模型、命名与子特效旧用法 | [模型](photon-model-effects.md)、[命名动画](photon-named-animations.md)、[发射器发射器](photon-emitter-spawner.md) | 页首当前入口之后的旧 API、旧回归与性能说明为历史，不能直接套用 |
| G1 资源来源与问题台账 | [501 条验收记录](photon-resource-acceptance-20261021.md)、[基线 JSON](photon-resource-baseline-20261019.json) | 496 条来源往返与 5 条来源不可用分别记录，未完成最终矩阵 |
| G1 代码与集中检查 | [代码阶段](photon-g1-code-stage-20261021.md)、[静态修复与集中记录](photon-static-fixes-20261021.md)、[实施进度](photon-redesign-progress.md) | 记录各阶段真实结果，不把早期失败或局部成功推广为最终状态 |
| 需求与架构目标 | [重写需求](photon-rewrite-requirements.md)、[模型重设计](photon-model-redesign.md) | 需求基线和方案，不是全部实施完成清单 |
| 旧核心移除 | [计划](photon-removal-result.md#历史移除方案与保留边界)、[结果与备份](photon-removal-result.md) | 2026-10-19 历史，不代表当前恢复代码不存在 |
| 依赖与兼容维护 | [依赖升级](photon-dependency-upgrade.md)、[Photon／LDLib2 修复](photon-ldlib2-fixes.md) | 修复条目按版本／阶段核对；删除的类型引用只保留历史文本 |
| 渲染与共同基础 | [粒子渲染](particle-rendering.md)、[视觉精细度](visual-quality.md)、[资源](resources-datagen.md)、[文本粒子](text-particles.md)、[伤害粒子](screen-damage-particles.md) | 共同基础保留；实体动画和模型 LOD 不属于本轮文档的重新验收范围 |
| 问题与检查记录 | [唯一问题汇总](../issues.md)、[修改记录](changes.md) | 文档缺口只在问题表登记，历次检查与已完成结论在本页保留 |

## 本轮续做（2026-10-23）

用户授权继续完成功能并严格审查；没有构建、Data、测试、同步、重载或启动游戏。当前未解决项只维护在 [Photon 问题汇总](../issues.md)，本页保存能力、检查与恢复信息。

- 网格按钮与 Delete 共用删除路径；点／边／面选择按当前模式派生，不保留已取消边的旧顶点。框选按模式写回，部件框选选中完整落入矩形的一张面的所属部件；当前工作区仍为单部件选择。
- 原生 gizmo 已开始拖拽时网格拾取不再更改选区；左键释放和拖拽结束提交候选，右键／Escape／切换或 revision 过期取消。取消结束原生拖拽并恢复 gizmo；无效候选不会提交前一次有效预览。
- gizmo 使用部件世界方向并在增量中消除基础方向，局部旋转／缩放不混入已有姿态；奇异或非有限矩阵停用手柄。几何操作仍由候选验证及现有会话统一提交。
- 默认部件可在没有显式 parts 的当前模型中显示与编辑；只读不补写原树，显式部件修改才物化数据。默认部件的变换不再被 document() 忽略。
- 骨骼／morph 重命名更新父链、权重和对应片段轨道，保留未知字段；部件复制同时复制自己的动画轨道，复制几何仍保留已有权重／morph 元数据。
- 环切提交额外核对原面扩展数据；合并后位置没有改变的代表顶点保留原始数值来源。未恢复历史文件格式。
- 关键帧拖动限定片段时长、拒绝同刻冲突、核对 revision，关闭后回调不写回；同刻重新写键只更新已知 TRS 或 value，不覆盖未知变换字段。文本框中的 Delete 不删除已选关键帧。
- PNG 文件选择从打开选择框时捕获 revision，异步导入完成后不能把旧窗口选择应用到后续新状态。
- 光束普通 tick 不再重复执行几何射线查询，首次输出和恢复冻结仍显式更新；参数签名按句柄解析，数值未变时复用输入映射，后续子实例仍接收参数。准备未成功时可在下一次蓄力 tick 重试；帧更新失败时清理并记录该实例，避免反复重建。
- 阶段姿态混合复用已有客户端收包时钟外推，不再把最后一份 serverTick 当作持续时间。服务端伤害、射程、技能时长、实体动画资源与模型 LOD 不在本轮修改范围。
- 新增 4 个数据保留／过期计划回归用例，未执行；不能把静态通过、源码存在或历史夹具成绩当作 G1 与视觉／性能通过。

## 检查与备份

本轮修改的 15 个 Java／测试文件经 IDEA 统一诊断均 analyzed、无截断、0 错误；保留 8 项既有提示，详见问题汇总。新增冗余条件提示已修正。修改行通过 IDEA 格式化，导入优化和成员重排关闭。两份由本轮 IDEA 补丁改为 LF 的原 CRLF 文件已按用户 A 授权用脚本恢复，规范化代码 SHA256 不变；其他既有 LF 范围未扩大处理。随后将检查扩展到 40 个相关 WIP 代码／配置／测试文件与 18 份文档，首次大批次超时后已按未覆盖文件分组补查，最终 58 份均 analyzed、无截断、0 错误；代码保留 12 项存量提示，changes.md 保留 18 项历史截图警告。当前文档文件与专题锚点检查没有新增失效目标，既有截图缺失继续登记。

| 历史阶段 | 已记录的实际结果 | 边界 |
|---|---|---|
| 2026-10-22 设计恢复 | 19 文件诊断 0 错误／0 警告，IDEA 编译与 Data 成功 | 属于当时布局恢复，未做该轮人工视觉验收；详情见 [设计恢复](photon-editor-design-restore-20261022.md) |
| 2026-10-22 网格 WIP | 40 份相关代码／配置无错误，保留既有警告；IDEA 构建成功。Data 日志 ij_run__Data_17237209521181167582.log 显示 23 个输出、写入 0／删除 0 | 日志原路径为 C:/Users/wang_/AppData/Local/JetBrains/IntelliJIdea2026.2/tmp；不代表本轮重新构建 |
| 2026-10-23 历史 XML 回读 | MeshEditing 10/10；PoseSampler 9 通过／1 失败；Reader 27/27；StagedResources 1/1，496 项 SHA、234 个模型按 0／5／100 CPU 采样 | 不是本轮执行；5 项缺源、原 fixture 失败及原生验收边界见 [唯一问题表](../issues.md) |
| 2026-10-23 文档审计 | 33 份文档静态 0 错误，18 项既有截图警告；本地文件缺失链接由 205 处降为 30 处 | 旧审计完成，原审计页已合并；未访问远端网址或运行业务检查 |
| 本轮功能续做 | 15 个修改代码文件静态 0 错误；4 个新增测试只检查源码，未执行 | 未构建／Data／业务测试／游戏／视觉／性能验收；当前问题只记于 issues.md |

| 恢复引用 | 对象 | 用途 |
|---|---|---|
| refs/backups/before-editor-mesh-g1-20261022/head | bdc23609e5ee82a86beb57178077deed098cd6ed | 原开发前 HEAD |
| refs/backups/before-editor-mesh-g1-20261022/wip | 95b26dbf0cb967b341f35c3e4e4969e29b25d570 | 原开发前 WIP |
| refs/backups/editor-mesh-g1-resume-20261023/head | ca4fd9203bfd18c491044f9abc369923f06fc960 | 迁移后原续做 HEAD |
| refs/backups/editor-mesh-g1-resume-20261023/wip | 170f9964f164fb140777d5e8b409839a0c5d2706 | 迁移后原业务 WIP |
| refs/backups/photon-resume-20261023/head | d4a5a998cd12bd1a1a9b384be28da4f148d30987 | 本轮续做前 HEAD |
| refs/backups/photon-resume-20261023/wip | 669f71fbe25354711909f604b3f6f67b9dde2738 | 本轮续做前 WIP |

本轮修改前 318 个相关文件的原始字节、Git 索引／暂存条目、状态与 HEAD 备份在 C:/Users/wang_/.codex/visualizations/2026/10/23/01a1669e-b2f3-7ca3-831c-69205680df6c/photon-resume-backup-20261023；修改前测试文件也已另存同根对应路径。前轮文档审计备份在同级 photon-docs-backup-20261023。已合并的旧网格续做页与审计页均保留于本轮原始字节备份，不删除既有 Git 备份或兼容 junction。恢复前先比较当前差异，再经 IDEA 局部处理，不 reset 或覆盖用户内容。

## 后续恢复顺序

1. 从 [唯一问题表](../issues.md) 选择当前问题，再查对应历史证据；旧页的待办和状态仅代表当时阶段。
2. 核对真实根、分支、HEAD、现有暂存与打开的编辑器内容；不跨工作区操作，不自动汇合或推送。
3. 只有取得对应授权后才运行构建、Data、测试或游戏；静态、运行、视觉和性能结果分开记录。
4. 当前未完成项关闭后从问题表移除，长期契约留在专题，证据留在总览／修改记录。
