# Photon 性能检测工具

> 2026-10-23：当前保留通用检测入口与采集框架，后续模型／命名／子特效源码已重写接线；本页旧专用计数、缓存／池和旧回归成绩仍属历史，不能当作新实现性能证据。最新源码改动及尚无冷热／P99／视觉验收的边界见 [最新 WIP](photon-workspace.md)。本轮未执行采集。

> 2026-10-19 当前状态：通用采集、CPU/GPU 面板、区间后台分析、命令/MCP 保留；自建模型/动画钩子和专用回归夹具随核心移除。旧模型统计、缓存和池接口不再代表当前能力。 实际检查与备份见[移除实施记录](photon-removal-result.md)。下文涉及已移除实现的说明仅保留为历史参考。

2026-10-19 更新：现有实现已做本地 Git 备份，用户要求重新设计并重写且兼容当前文件。此页为旧实现与历史证据；后续交付按[重写需求与兼容契约](photon-rewrite-requirements.md)验收，重写尚未开始。

状态：2026-10-19，完整编译、Data 与专项测试已有通过证据；当前底层 23/23、导出 3/3、区间 11/11、后台 3/3。汇总动画自测另有未通过项，见下文，不能用这些专项测试代替。帧图、冻结区间分析、GPU 查看页与区间导出已接入；菜单中的帧图、CPU 区间与导出已有运行证据，原生合成帧图拖选、GPU 页与屏幕关闭已有功能证据，真实负载、完整冷热、范围控制与容量治理仍未完成，不能据此认定全部目标完成。

## 入口

- 扩展编辑器：`/lobecorp photon_editor` → 工具 → 性能分析。
- 世界查看：`/lobecorp photon_profile open`。
- 启动：`/lobecorp photon_profile start 40 detail`；模式可选 `overview`、`detail`、`gpu`，时长沿用既有采样器的 10～120 秒。
- 停止并后台导出：`/lobecorp photon_profile stop`。
- 取消且不导出：`/lobecorp photon_profile cancel`。
- 状态：`/lobecorp photon_profile status`。

新增混入必须在重新启动的开发客户端加载。启动入口会检查 GameRenderer 的实际混入标记，防止仅热替换 Java 后采到缺少对象钩子的数据。该标记不代替全部调用链的运行验收。

世界面板开始采样后关闭查看界面，避免覆盖原场景；编辑器使用游戏内原生 Dialog，可继续操作时间轴。面板固定复用 32 行、每 250 ms 刷新，支持分类、筛选、排序、分页和按归属路径查看；对象定位仅允许仍属于原实例的对象，不能以同 UUID 的新实例代替。

## 帧图、区间分析与后台工作线程

概览和操作页新增帧图，最多 128 个时间桶；每桶保留最长帧间隔，青色 CPU 与紫色 GPU 点取该同一帧，不能把它们读作各自桶内峰值。图例显示纵轴上限与有效间隔数；原始数据仍在 frames.csv，图中压缩不会改变逐帧分位。面板维持每 250 ms 更新与固定 32 行复用。

可以在图中拖选，或输入起止帧后点击“分析区间”；采样中选择的区间等停止后分析。起点从 1 开始，区间 i 对应 HEAD[i-1]→HEAD[i]；render CPU/GPU 取 i-1，分配取 i。停止后使用冻结数组，不在后台读取活动场景或 UI 树。初次检查通过面板同一选区处理方法发起；后续已走通带 hover 刷新的原生 MouseHandler 按下/移动/释放链，得到 635～1917 帧且后台返回同一区间。它属于合成原生输入，不称物理鼠标人工验收。

- CPU 行来自保留的原始调用，按线程/节点/阶段分组；完整调用有已知 self、分配和线程 CPU；跨边界调用只算墙钟交集，计入 partial，不能按比例推算 self。未完成调用单列；缺测字段与采样数一起解释，不能把已知部分之和当完整总量。
- OVERVIEW 没有原始 CPU 事件，选区 CPU 列表不可用；独立保留的整会话最大调用只在导出中作为 peak_hints，不能加入区间总量。环形覆盖后仍仅显示保留记录，线程保留起止时间不证明其间毫无丢样。
- GPU 页按 pass/批次显示 inclusive 合计、最大项与实测/缺失，单对象关联和共享归属分开；不把共享父子 pass 相加。选区只纳入 CPU 提交起止都在区间内的完整查询；GPU 设备时钟未校准，不能裁切设备 duration 或断言这些查询的 GPU 执行全部发生在 CPU 选区内。
- 资源页始终显示整会话计数器，并明确标注范围；全局 dropped/overwritten/incomplete 与 GPU 跳过不能伪装成逐区间精确计数。无提交边界的 GPU 条数属于整份保留查询的未知归属。
- 后台分析采用一条专用平台守护线程、最多 8 项等待任务；每个面板只有一个运行任务与一个最新等待目标。新选区协作取消旧分析，发布时核对请求版本；关闭/清除撤销发布资格，队列满显示错误。虚拟线程仍只用于既有文件导出，不把它当 CPU 计算自动加速。
- “导出区间”写入独立的 performance/<label>-window-<session>-<time>/：window.json、cpu-window.csv、operations.jsonl。包含选区帧分位、CPU/GPU 统计、保留范围、节点字典、环境与独立峰值提示；不覆盖整会话输出。

实现集中在 PhotonPerformanceFrameChart、PhotonPerformanceWindowUtil、PhotonPerformanceWindowAnalysisUtil，采集器只新增冻结快照/帧视图与导出入口。GUI 仍由原渲染线程绘制，这属于分析准备并行，尚未实现第 19 节的独立 GUI renderer。

本轮先完成编译再完整启动 Client，未对活客户端热替换。菜单样本 window_ui_gpu-1-20055936990100 记录 593 帧、2 节点、零 CPU 丢样/覆盖/未完成；它受菜单帧率限制，并非激光性能样本，额外 Photon GPU 查询为 0。通过面板选择 1～100 帧后，后台返回 5 项 CPU 行；区间间隔 P95/P99 为 20.0863/41.4758 ms，截图与导出一致。截图在 run/client/screenshots/window_profile_chart_20261019.png；区间报告 window_ui_gpu-window-1-20134470119600 已完成且 error=null，CSV 将未测线程 CPU 留空。

新增区间测试 11/11、后台任务测试 3/3、底层复跑 12/12，由 IDEA 启动的 Gradle XML 实际数量与零失败/错误确认；IDE 测试面板接口返回 total=0，未用该接口推定通过。最后一次 Data 等待窗口超时，随后完整运行日志确认 BUILD SUCCESSFUL、written=2/removed=0，中英文性能键各 100 项。相关 Java 局部编译和错误诊断通过。随后重启的菜单检查已验证查看屏幕关闭撤销分析、GPU 排序与页头标签及模式重置；编辑器 Dialog 关闭、导出错误注入等分支仍待补充。修改前 IDEA 文本快照在 backups/photon-performance-tool-20261019/window-analysis。

## 容量治理：明确截断并保留根统计

90 秒手动样本 manual_mouse_detail-2-13695017500900 的字典确实达到 4096，渲染线程聚合表也达到 4096；不是仅有原始 span 环形覆盖。字典含 211 个运行实例、420 个原生播放器、1835 个模型计算细分节点，另有真实模型/部件/轨道。不同实例仍分别保存，不为缩小字典而合并其身份。该旧样本总丢样 539910，不能用后半段的未归属项作完整逐对象结论。

现接入非滚动预算保护：节点、线程缓冲、聚合表、计数表、重要标记和调用深度首次耗尽时，锁定 limit.reason/timeNs/frame；停止新增 span，已打开的原业务调用继续并完成计时。下一次 render HEAD 在递增帧数前以 CAPACITY 冻结、导出，也不再执行未触发的采样阶段回调。采样阶段内部触发容量时同样检查，避免追加全零帧。容量与内存预算没有扩大，工具也没有修改真实特效/技能的执行。

聚合项现在在 begin 时预留，尚未完成且 calls=0 的项不展示。子调用填满表后，先打开的整帧/seek 等父调用仍有统计与最大调用证据；不会等父调用结束时才发现没有位置。每帧 4096 条原始 span 的预算仍允许丢弃原始细节并保留聚合，不因这一滚动限制直接结束；丢失数量单列。

新增 collector_quality 到状态、session.json 与 window.json，分开记录节点/线程请求拒绝、原始 span、深度、聚合/计数请求拒绝和重要标记丢失。请求次数不是唯一丢失对象数；环形覆盖、未完与这些类别不能混读。面板状态显示容量原因，导出页和 report.md 明示提前结束的部分捕获。它仍不能代替选定实例/子树的采样控制、预算内覆盖所有 90 秒或实际开销校准。

本轮相关 10 份 Java 通过修改范围格式化、局部编译及错误诊断；已有同步/final/null 分析建议保留。底层 18/18、导出 2/2、区间 11/11、后台 3/3（合计 34 项）由 Gradle XML 证明零失败/错误，涵盖表容量、父调用保留、第九线程拒绝后前八线程闭合、原始预算与首次原因不变。Data exitCode=0、written=2/removed=0，中英文性能键各 109 项。CAPACITY 自动结束与界面文案已有下述合成预算 fixture 证据；没有以专项测试代替该证据，也未据此认定真实激光长时容量已解决。快照在 backups/photon-performance-tool-20261019/capacity。

### 菜单内功能验收与边界

完整重启，未使用活客户端热替换或修改 JVM/IDE 配置。通过公开 openWorld 入口打开查看屏幕、实际最大化 2560×1494。capacity_live_node-1-22737630939600 在已采 2558 帧时通过 Debug 合成节点请求，触发 NODES/帧 2557；下一 render HEAD 自动以 CAPACITY 冻结并完成导出，冻结后仍为 2558 帧，未追加全零帧。quality 记录节点请求拒绝 7，其余拒绝/原始丢失为 0，未完成 0。触发前打开的合成 SEEK 仍有完整最大项；约 9.55 秒来自调试器循环，绝不是实际时间轴性能结论。截图 performance_capacity_stop_20261019.png 已视觉核对。

帧图第一次合成输入未先更新 LDLib2 的缓存 hover，因此没有拖动；当前依赖 ModularUI 明确要求合成输入调用 refreshHoveredElementAtScreen。按此更新后，原生 MouseHandler 链记录按下 anchor=635、释放范围 635～1917、dragging=false，后台结果同范围。capacity_live_node-window-1-22995907307900 的 window.json 保留 1283 个间隔、5 项 CPU 行与全局容量质量，区间导出完成。这不代替真实编辑器时间轴的连续物理手势、撤销或最终落点等价性。

gpu_ui_empty_query_fixture-2-23003750637000 使用两组真正 GL timestamp 空绘制查询，一组共享、一组关联合成节点；没有提交真实特效绘制，不把 1024 ns 当成任何特效成本。原生鼠标点击 GPU 页后两行显示正确，共享/关联、inclusive 标题与排序已截图 performance_gpu_ui_fixture_20261019.png；2/2 查询回收、无跳过/错误，五帧报告已导出。真实 batch/Bloom 的此前世界查询另列，不能用本 fixture 代替其采样。

随后启动非 GPU DETAIL，requested=false、supported=null、GPU 旧行数 0；取消后退出查看屏幕，回到 TitleScreen，分析器 closed=true、working=false、result=null、采集 active=false。恢复 FIRST_PERSON、guiScale=4、AFK；清除临时调试引用后请求正常关闭，日志 15:01:51 Stopping，调试状态 stopped/sessions=[]。只移除本轮断点，用户原三项状态未变。此轮完成了这些功能检查，不据一次未崩溃推定此前 JBR native 问题已经修复。

## 动画逐帧、命名标签与轨道上下文

经 IDEA 核对，原有钩子只包住 TimelinePlayer.evaluate，而原生 frame(float) 也会调用动画采样及逐帧 post process。现新增独立 ANIMATION_FRAME，使用原适配器的 sampleTime 读取实际小数 tick；原调用、录制早退、子动画与预览回调保持原路径。ANIMATION 与 ANIMATION_FRAME 分列，不能把帧插值漏算进“未归属”。命名播放器按已有弱来源表读取名称与定义 UUID，不重解码动画库，不在每帧解析 UUID；普通时间轴仍标 timeline，数值节点 ID 区分每个播放器实例。

轨道的 Node.parent 继续指向真实目标对象；额外 node_contexts 保存执行该轨道的播放器节点。共享定义按对象身份、目标节点、执行上下文分别建立有界弱映射，与普通节点共用 4096 字典预算。原生正常复制路径本身会复制时间轴；本轮没有据此声称已复现共享轨道串用 Bug，而是补上双向归属并避免可复用定义被误合并。上下文不额外产生计时调用，不能与 parent 或 inclusive 重复相加。

面板支持从目标或播放器筛选轨道，按上下文名称搜索；行内显示播放名称与节点编号。live view 改变时清除路径缓存，避免晚绑定父级留下旧路径。轨道/部件的定位向父级寻找仍属于原实例的 FXObject，继续核对同实例身份；编辑器实际定位尚未运行验收。session.json/window.json 新增 node_contexts，objects.csv 与 cpu-window.csv 新增 context_node。原始 span 的 parent_span_id 仍表示同步调用嵌套，与目标/执行关系分别解释。

### 动画专项运行证据

本轮完整重启后菜单夹具 animation_context_tracks-3-25409648660400：两个命名播放 profile_alpha/profile_beta 各有一条注册过类型的原生 AnimationTrack，共同作用于同一根对象。原始输出保留八个节点，轨道 5→播放器 3、轨道 7→播放器 6，二者 parent 都为目标 4；按播放器或目标筛选均命中，按 profile_alpha 搜索得到三个统计项。截图 run/client/screenshots/performance_animation_context_20261019.png 已视觉核对。

每个播放有两次 evaluate、四次 frame、五次轨道采样；frame 和轨道的 1.5、1.75、1.875 tick 对应，原始 parent_span_id 关联到所属 frame。录制时 frame 在约 1.9 tick 仍记录，但没有轨道采样：全局轨道计数 8→8，退出录制后 10；时钟 position 保持 0.05。浮点值以原始 double 保存，不把 0.05 的归一化 position 当作动画 tick。3321 个菜单帧、零丢样/未完、limit=null、导出无错误；菜单与 Debug 片段不是实际激光性能样本，不能据此推定优化收益或完整画面等价。

早期 animation_context_pair 夹具未设置轨道注册类型，序列化后零轨道，只可证明播放器标签与 frame；修正的是临时夹具，未保存工程。初次 animation_context_fixture 含汇总自测失败，亦不作为通过证据。实际有效轨道证据来自上面的 tracks 会话。

底层 23/23、导出 3/3、区间 11/11、后台 3/3，共 40 项 Gradle XML 零失败/错误。11 份相关 Java 经 IDEA 修改范围格式化、编译及错误诊断；保留非 final 字段建议、刻意身份 String 检查及既有同步/null 警告，不称所有警告为零。Data 两次均 exitCode=0，最终性能键中英文各 111 项，最后一次 written=0/removed=0。备份为 backups/photon-performance-tool-20261019/animation-context 的修改前文本快照。

### 汇总动画自测的未通过项

PhotonAnimationRuntimeRegression.runLoaded 在 PhotonAnimationChildRegression.localCalls 第 284 行失败；开启与关闭采样都出现相同 requireNonNull 异常。该行使用 playback(source.id.toString()) 查询未入库的测试定义；IDEA 当前源码中字符串接口经 library.find，而 UUID 接口直接查 playing。独立实际检查得到 UUID 查询非空、字符串查询为空。没有修改这两份既有回归文件，也不把此汇总自测计为通过；更广的动画/嵌套/画面等价性仍需处理这项调用问题后继续验收。

本轮只进入已加载的菜单，未进入世界或覆盖用户资源。夹具已 close/destroy，关闭查看屏幕后分析器 closed=true、采集停止；FIRST_PERSON、gui4、AFK 保持，移除临时引用与断点后正常请求关闭并确认会话 stopped。不因本轮正常运行而宣称此前 JBR 问题根治。混合器所有权、排序/commit 的共享成本仍未按命名播放细分，不能把 native evaluate/frame 当整个动画系统的全部成本。

## 已接入的源码范围

| 范围       | 实际接入点                                                                                             |
|------------|--------------------------------------------------------------------------------------------------------|
| 帧与提取   | 复用 PhotonFrameCapture；GameRenderer.render、extract、extractGui                                      |
| 特效与对象 | FX 创建、FXObject.copy/tick/extractFrame、FXRuntime.emit/destroy                                       |
| 延迟网格   | Emitter.extractBatches 注册的 BakeTask 实际执行；保留原延迟烘焙结构                                    |
| 模型       | 准备/载入、采样/姿态缓存、Morph/骨骼/部件姿态、曲线、部件/材质组烘焙与材质运行态                       |
| 网格计数   | LOD 后输入顶点/三角形、写出的四边面顶点、有效三角形与包含退化填充的提交三角形分列                      |
| 时间轴     | 请求合并计数、排队、直接/合并定位、重置、原生模拟预算和成功处理 tick 数                                |
| 动画       | 原生 TimelinePlayer.evaluate 与 AnimationTrack.sampleInto；新增 frame、命名播放标签和轨道执行上下文；混合/所有权细分仍未完成              |
| 指挥家     | 鼠标/朝向施放入口、sendTarget、服务端 work、受控 tick/cast、施放结果与客户端激光阶段                   |
| UI         | ModularUI 布局与 hover；指挥家准备/更新/绘制、卡片/技能/命令、头像提取/纹理等分段；新增帧图与区间分析，详见下节    |
| GPU        | 全帧原计时、Photon stage、merged/instanced batch、Bloom 整链与 fullscreen pass；批次归属与查询已实测；新增 GUI 页待验收 |

普通特效时间轴已有同帧合并，模拟已有快速路径。首项优化为复用注册表按 ID 查询能力，保留控制资格与自定义控制器默认遍历；没有更改技能时序、射程、伤害、冷却或特效资源。

## 数据与口径

输出在当前游戏目录的 `performance/<label>-<session>-<time>/`，后台导出完成后查看实际路径。

| 文件                | 内容                                                                     |
|---------------------|--------------------------------------------------------------------------|
| session.json        | 环境、范围、对象元数据、整会话聚合、计数、质量与每项最大调用             |
| frames.csv          | 逐帧间隔、前一 Minecraft 帧、render CPU/GPU、渲染线程分配及窗口/阶段信息 |
| objects.csv         | 逐对象/阶段/线程的 self/inclusive、最大调用、分配与保留调用窗口的分位    |
| operations.jsonl    | 指令、定位、重放及阶段标记                                               |
| trace.json          | 保留的完成调用与标记，供本地 Perfetto 查看                               |
| outliers.trace.json | 各统计项最大调用与标记，防止首次长调用被后续环形记录覆盖                 |
| gpu.jsonl           | 原有 render 设备时间、额外 stage 查询、来源帧和不可用原因                |
| report.md           | Top self、最大调用、丢样与结束状态                                       |

self 是同线程同步嵌套的墙钟耗时减去测到的直接子调用；包含未细分工作与部分观察开销，不等于纯业务 CPU 周期。线程 CPU 仅在支持且已启用的粗粒度作用域读取。分配为近似堆分配差，不含原生内存/显存。

objects.csv 的 P95/P99 是保留完成调用的分位，不是全会话逐帧 P99。帧分位以 frames.csv 的有效帧重新计算。span 的 frame_at_begin 是开始时帧号，跨帧和 tick/extract 窗口应按单调时戳对齐。

GPU 查询只读 available，不等待、不 glFinish、不结束其他组件的 TIME_ELAPSED。设备时钟未与 CPU 校准；共享 stage 的耗时不能给每个对象重复计入，也不能与 CPU 相加。逐 batch 与 Bloom 子阶段已有下文列出的真实 GL 查询与归属检查；新增 GUI 页的交互另行验收。合并类型在当前 drain 内全部贡献者相同才链接对象，其余标为共享/未知，不能按数量平摊。

指令只在同 JVM 内按玩家、现有 action/单位/技能字段和 FIFO 推断关联，明确标记可信度；没有修改业务包或增加 request ID。独立服务端缺少匹配记录时保留 unmatched，不相减两个 JVM 的 nanoTime。

## 运行证据与首项优化

环境：RTX 3060 Laptop、OpenGL、2560×1494、窗口最大化且前台、VSync 关闭、帧率上限 260。新建固定种子 `20261019` 测试世界，女皇与 16 格外靶子；输入由调试器安排原生 MouseHandler 鼠标事件，经过原 HUD 预览/命令/网络/服务端技能链，不是物理人工点击。

| 会话                                               | 实际结果                                                                                                                                 | 边界                                                                                                                                          |
|----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| manual_first_detail-1-13494140530200，90 s         | 三次朝向 CAST 到达服务端；没有启动技能                                                                                                   | 朝向点只有 1 格，被最小距离条件拒绝；不能计为激光性能样本，行为问题仅记录未修复                                                               |
| manual_mouse_detail-2-13695017500900，90 s         | 原生鼠标链启动激光；首项 SKILL 最大 123.16 ms、共享 DRAW 最大 118.68 ms；整段最大帧间隔 421.38 ms                                        | 预热池初始 ready=10，不能标为完全冷启动；4096 节点耗尽，dropped=539910、环形覆盖=448400，不用于完整冷热排行榜                                 |
| conductor_hud_sections-3-13833229722700，15 s      | 静止 HUD 每帧约 6 次 update，update 平均 1.676 ms/次；技能栏平均 3.861 ms/次；帧间隔平均 22.706 ms/P99 41.523 ms；每帧分配平均 30.095 MB | 新增分段后的优化前样本，14 节点、零丢样/覆盖                                                                                                  |
| conductor_hud_lookup_direct-4-14043295369500，15 s | 单项能力查询走已有注册表后，update 平均 0.406 ms/次、技能栏 1.086 ms/次；帧间隔平均 10.169 ms/P99 19.362 ms；每帧分配平均 9.855 MB       | 同世界/窗口/相机/选择/模式，无活动特效；14 节点、零丢样，原始事件环形覆盖 14459，整会话聚合仍保留；一次前后对照，尚未做交错重复和关闭工具校准 |

优化没有缓存资格：`ConductorUtil.ability` 经 `IConductorUnitControl.ability` 查询，注册表控制器复用 `RegisterConductorAbilitiesEvent.get`，自定义控制器默认仍从现有列表查询。运行时检查客户端 17 项已拥有能力逐项对象一致，缺失 ID 返回 null；动态增删、服务端和自定义控制器仍待补充验收。持续 GUI 压力有改善，不代表激光首次峰值已解决。

原始文件位于 `run/client/performance/<上述会话>/`，各含 session.json、frames.csv、objects.csv、operations.jsonl 与 traces；这是本地开发运行产物，不保证随 Git 文档分发。

面板布局已真实截图确认标题、状态、行区和页脚可见；截图 `run/client/screenshots/performance_tool_panel_fixed_20261019_13088901110400.png`。一次对仍打开的旧面板进行结构热替换触发旧 lambda 的 NoSuchMethodError，重启本次客户端后恢复；后续修改 UI 结构应先关闭旧界面。这不是 GPU 驱动崩溃证据。

## 时间轴首项优化与检查

通过原生标尺 scrubTo 处理入口安排目标 `15,15,15,5,18,18,10`，选中激光工程的命名动画 `laser_start`，长度 20 tick；这组是合成标尺事件，不是物理鼠标完整手势。主工程读取为未关联保存文件的内存副本；前后源 .fxproj 的 SHA-256 均为 `124dd69e858f0db5e1e508185dedd668943c882a9889a460c4e4683d17c721b4`。

初次定位样本 `editor_laser_seek_baseline-5-14559197362500` 捕获同点重放：15→15 约 245.6/231.5 ms，18→18 约 195.1 ms；此样本窗口 focused=false，只作为路径与耗时证据。

| 会话                                              | 重放次数/tick                | SEEK 合计/最大      | 整段帧间隔 P99/最大 |
|---------------------------------------------------|------------------------------|---------------------|---------------------|
| editor_seek_focused_before-6-14652603142100，18 s | 7 次 / 96 tick               | 1083.17 / 231.39 ms | 20.95 / 244.13 ms   |
| editor_seek_reuse_after-7-14796617679100，18 s    | 4 次 / 48 tick，3 次同点复用 | 1252.47 / 661.97 ms | 30.07 / 672.01 ms   |
| editor_seek_reuse_repeat-8-14916610719200，18 s   | 4 次 / 48 tick，3 次同点复用 | 563.84 / 180.38 ms  | 17.60 / 190.13 ms   |

三组同为前台最大化、2560×1494、VSync 关闭、上限 260、OVERVIEW，零丢样/覆盖。首份优化后样本更差的尖峰仍原样保留；不能只选后一次数据宣称整体已达标。热替换后的首次长调用还需用重启与交错重复进一步区分。已确认的收益是消除了三个重复重放，尚未证明所有不同目标的拖动满足响应目标。

改动位于 PhotonAnimationWorkspace.canReusePausedSeek 与 PhotonAnimationTimelineContext.seek：仅在暂停且已完成、时间一致、未录制、无待模拟/场景刷新、无测试动画/时间轴播放时复用已有状态。内容刷新仍调用原 scrub，不把所有 SceneView 同目标调用统一跳过；初始未采样的零刻仍需模拟。

`editor_seek_guard_regression-9-15024169268800` 实际记录：完成态同点被复用；显式 refreshTimelinePreview 同点仍重放；同帧 10→15→10 保留最终 10 并合并一项请求；10.25 tick 第一次重新采样、重复时复用；跳到 0 第一次初始化、重复时复用。七阶段无错误，零丢样/覆盖/未完成。它覆盖这些调用路径，不代替实际字段编辑、撤销、录制、连续物理手势或最终网格等价性验收。

两份 Java 的局部编译成功。Context 错误诊断为空；Workspace 一次检查 timedOut=true/errors=[]，不计为整文件诊断通过。修改前 IDEA 文本快照在 `backups/photon-performance-tool-20261019/unchanged-seek`。

多线程候选、快照/版本、有界队列、线程归属与异步验收已加入计划第 18 节（历史路径 `photon-detailed-performance-plan.md#18-多线程优化专项用户补充`，本工作区不存在），尚未新增特效计算池或宣称多核收益。

## GPU 分段接入状态

gpu 模式的 timestamp 查询新增父查询 ID 与 CPU 提交边界，后者仅用于标记提交顺序，不能当成已校准的设备时钟。merged/instanced batch 保留原批处理；Bloom 整链和各管线/目标尺寸分段属于共享成本。

gpu.jsonl 的额外记录带 record_type=sample/aggregate。aggregate 独立保留整会话请求数、已测、缺测、预算跳过、首项和最大项，避免早期峰值被 8192 条原始查询环形记录覆盖；最多 4096 个聚合组，容量丢失显式报告。各 pass 为 inclusive，父子及共享时间不可相加。

相关源码已通过局部编译与错误诊断；GPU 统计新增 3/3 测试通过。已重启本次 Client，editor_gpu_batches_bloom-1-16537052577800 的 10 秒实测完成 28254/28254 查询回收、151 次预算跳过、零 GPU 聚合丢失/查询错误；343 节点、零 CPU 丢样/未完成，CPU 原始环形覆盖 165565，GPU 原始覆盖 20062。31 项 GPU 汇总中 16 项对象批次、15 项共享阶段/Bloom；早期最大项仍在 maximum_sample，证明峰值独立于原始环形记录保留。临时 fixture 的 gpu_ownership_fixture-2-16829873203000 随后验证 40328/40328 回收、0 跳过/缺测/聚合丢失；两个同材质 CPU emitter 的 merged batch 标共享，实例化 batch 精确链接 gpu_test_instanced，24 节点且零 CPU 丢样/未完成。GPU 汇总 measured 合计与 started 一致，所有峰值的 CPU 提交边界单调。fixture 未保存。

该样本共享 AFTER_TRANSLUCENT_PARTICLES 设备区间最大约 34.49 ms，1227×666 的 Bloom 整链最大约 1.05 ms。timestamp 区间是设备时间跨度，可能包含供给/依赖等待，不能解释为 GPU 全程繁忙；没有 CPU/GPU 时钟校准，也不能凭这些数值把长帧全部归因于 Bloom。新功能测得数据不构成优化收益证明。

## 原生粒子并行小负载对照

复用依赖自带 parallelUpdate，临时工程三个原生 emitter 各上限 512、无碰撞，其中两个 CPU 绘制、一个实例化绘制；固定种子 20261019、先重置并预进 30 tick，再采样 10 秒。只改变内存 fixture 的开关，不保存工程，不修改用户激光资源。三组均前台最大化 2560×1494、VSync 关闭、上限 260、GPU 模式。

| 会话                                     | 并行 | 帧间隔均值/P99/最大 ms  | 三个 emitter 的主线程 tick 墙钟合计 ms / 调用数 |
|------------------------------------------|------|-------------------------|-------------------------------------------------|
| particle_parallel_off_a-3-17047494504700 | 关   | 7.286 / 11.361 / 22.340 | 68.966 / 630                                    |
| particle_parallel_on_b-4-17113233166600  | 开   | 4.688 / 8.253 / 22.287  | 117.583 / 627                                   |
| particle_parallel_off_c-5-17211900014200 | 关   | 4.589 / 10.214 / 18.145 | 44.520 / 630                                    |

开启前实际确认每个 emitter 512 粒子、useParallelUpdate=true；三组零 CPU 丢样/未完成、GPU 查询全部回收且无预算跳过/聚合丢失。结束后已将 fixture 开关恢复 false，并暂停预览。

这组中并行的 emitter 主线程 tick 墙钟更高，包含等待工作任务的时间；关闭后的重复样本总体均值也没有变慢。前一关闭样本的绘制/整帧存在较大漂移，不能挑“关 A→开 B”的帧率变化宣称并行收益，也不能据小负载否定更复杂/更大负载。尚未计完整工作线程 CPU、全线程分配或验证粒子最终状态等价，不能将它当成激光多线程验收。当前不全局开启此开关；后续按真实热点、任务粒度和正确性继续评估。

## GUI 独立渲染候选

按用户补充，已在计划第 19 节（历史路径 `photon-detailed-performance-plan.md#19-gui-独立线程渲染专项用户补充`，本工作区不存在）记录 GUI 数据准备并行、帧缓冲/离屏与真正独立 renderer 三层方案，以及输入、资源、上下文、最终呈现与等价性验收。当前 LDLib2 独立窗口仍在 RenderFrameEvent.Post 中由同一渲染线程顺序驱动、并借用游戏渲染 scratch；这不是已经存在的 GUI 独立渲染线程。现已增加上述冻结性能数据的后台区间分析；它没有迁移 GUI 的实际绘制，也不构成独立 renderer 收益证明。

## 调试环境记录

GPU 分段与原生并行样本均已在 JVM 结束前完成导出。之后两份采集生命周期 Java 的局部编译成功、错误诊断为空；GPU→非 GPU 状态重置运行复测未能开始，因为调试会话已消失。经 IDEA 读取 hs_err_pid39136.log，13:31:04 的 C2 CompilerThread3 报 ciMethod.cpp:749：old method not detected，涉及 JVMTI 状态；未认定为 GUI/GPU 业务逻辑崩溃。后续源码修改先完成编译，再完整启动测试客户端，避免本次 JBR 增强类重定义路径；没有修改全局 JVM/IDE 配置。

本轮完整重启后的会话在 14:14:30 再次结束。hs_err_pid26704.log 记录 Render thread 的 EXCEPTION_ACCESS_VIOLATION，problematic frame 为 java.lang.invoke.DirectMethodHandle$Holder.invokeStatic；与前一份 C2 内部错误不同。此前帧图/区间分析与导出已完成，后续鼠标链调用前等待断点时会话结束；不称鼠标拖选、GPU 页、GPU→非 GPU 状态重置已验收，也不能仅凭 native dump 判断业务根因。没有修改全局 JVM、驱动或 IDE 设置；options.txt 仍为 guiScale=4、AFK、上限 260、VSync 关闭，没有将内存测试选项保存到配置。

## 当前检查与未完成项

- 底层记录器 12/12 专项测试已由 IDE 测试运行器确认通过，包含多线程身份、嵌套计时、代次/局部时间、容量、重要标记及峰值保留。
- 导出专项 1/1 已确认通过，核对缺测值、NaN、CSV 引号/换行及各输出文件。
- 专项测试本身不证明游戏绑定或性能目标；上述运行样本另列，仅覆盖实际经过的路径，不能替代未测的场景与验收。
- 已完成：16 份新增 Java 整文件与 18 份既有 Java 修改范围的 IDEA 格式化；完整 IDEA 编译 isSuccess=true/problems=[]；Data exitCode=0、实际日志 written=2/removed=0；生成中英文各 79 个新增键无缺失。后续 HUD 分段及三份能力查询 Java 的局部编译也成功、相关错误诊断为空。
- 尚待：容量耗尽后的归属治理；连续物理时间轴拖动与实际编辑/录制链；帧图/区间在真实特效负载下的交互与开销、编辑器 Dialog 关闭生命周期；动画混合/所有权细分与汇总回归调用问题；GPU 页在实际复杂特效中的使用及共享贡献者展示；资源指纹、随机种子与工具开销；完全冷启动/间歇/重复对照；首项优化的交错重复、动态资格与服务端验收；激光和时间轴进一步关键优化及复测。
- 修改前已有用户内容的 IDEA 文本快照见 `backups/photon-performance-tool-20261019`；不是字节级备份。没有手动暂存或提交。

总实施要求见逐对象检测与关键优化计划（历史路径 `photon-detailed-performance-plan.md`，本工作区不存在），历史基线见性能记录（历史路径 `photon-performance.md`，本工作区不存在）。
