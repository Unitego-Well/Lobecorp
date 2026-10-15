# 系统修改记录

每次修改后更新本文件与对应系统文档。记录实际执行结果，不把计划或旧一轮验证写成本轮成功。

## 2026-10-15：十字星提前退场与激光冲击提速

- 16 十字星的消失段整体提前 2 tick。寿命仍为 10 tick，保留出生时刻和前段形态；按生命周期 Alpha 的 0.001→1 范围，将消失阈值由 0.65／0.82 改为 0.4502／0.6202，约从年龄 6.5～8.2 tick 提前到 4.5～6.2 tick。使用私有 `burst_cross_early_end.shader_graph.nbt`，原图保留。
- 27／28 两根光柱及 29 源头尘雾的五份模型图另存为 `*_rush.shader_graph.nbt`，仅将材质内部时钟乘以 2，加快纹理流动和原有顶点扰动，不改变动画时长或发射数量。释放到位的 22 tick 内柱／外柱横向峰值为 1.3／1.2，24 tick 回到 1；源头尘雾 22 tick 达到 0.9、23 tick 短促展开到 1.2 后回稳。
- 两批原生资源及工程事务已成功，原 V3 保存 dirty=false，激光定义／包更新 warnings=[]。旧图保留，工程引用和模型曲线可通过原生撤销恢复；未新增整工程备份，未运行编译、Data、测试、截图检查或性能采样。没有增加模型、粒子数量或 Java 实现。

## 2026-10-15：首爆椭圆冲击波 X 基形修正

- 15 首爆椭圆冲击波的基础网格 X 直径为 1.24、Y 为 2.5；前轮只同步动画缩放系数，无法消除基础网格原有的 X 压扁。本轮只将该网格 X 乘以 1.25／0.62，使 X／Y 直径均为 2.5，并按逆转置比例修正、归一化面角法线。
- 原生事务提交成功；动画、部件、UV、固定根端及其他资产保持原值。工程保存 dirty=false，激光定义和包重新导出 warnings=[]、包 18 文件。未新增独立备份，保留原生撤销记录和已有备份；未运行编译、Data、测试或新增截图检查。

## 2026-10-15：V3 双层三维激光与首爆调整

- 原生 V3 增加内层亮核／金橙等离子／粉色主体、外层粉紫尘雾光柱及前开口半椭圆尘雾。三个新模型合计 441 顶点／760 三角面；+Z 发射，两柱 2 tick 达到目标长度，总参考宽 0.952，约为稳定亮球直径的 1.7 倍。总时长仍为 200 tick。
- 按最后指令，首爆椭圆根端固定、X／Y 同比例收细并向前拉长，高亮 2 tick 后立即蚀除，接着 2 tick 消失，蚀除时不额外收缩。十字星放大，采用中细→小粗→中细→大细→消失；暗能量尺寸 2.1。持续火花、星粒、粉色流动和电弧改在 20 tick 与首爆同时开始，保留发射终点。
- 命名动画 `tutorial_laser_v3` 实现 length／width／intensity／enabled，38 轨道、11 绑定。已实测三模型缩短／变窄／强度减半、关闭与重触发；problems=[]，六份模型图编译错误为空。根端三个阶段采样均为 Z=0，七张阶段透视截图已查看，200 tick 无可见特效；未做独立性能测量或原视频逐像素验收。
- `v3/tutorial_shockwave_v3.fxproj` 保存 dirty=false；`v3/beam/tutorial_laser_v3.fx／.fxpack` 导出 warnings=[]，包 18 文件。备份 `v3/beam/before-beam-20261015.fxproj`，原 charge 资源及旧蓄力导出保留。通过 IDEA 同步本记录和 `photon-effect-mcp.md`，没有 Java 修改或本轮编译／Data；保留用户已有 Git 修改。

## 2026-10-15：持续生成模型的网格精简

- 仅修改当前 V3 六个模型模板：03 紫色汇聚 99 顶点／128 面→34／32；02、13、14 粉色宽环分别 287／160→63／80；23 粉色流动、24 黄色电弧分别 153／224→62／120。合计 1,266 顶点／1,056 三角面→347／512，减少约 72.6%／51.5%。核心、08／09 蓄力外壳、首爆椭球及普通粒子未改。
- 原宽环每个只有 123 个顶点被面引用，先去掉未引用列，再按曲线误差保留 21 排关键截面与可见边界；紫色条带保留 17 排，去掉最大偏差约 1.12e-7 的冗余中列，并重映射汇聚 Morph。球壳改为 12×6 分段，焊接重合极点／接缝位置；UV 仍按面角分别保存，保留接缝、原材质、绕序和三维载体。
- 通过原生 effect_edit_batch 校验并提交；现场核对时间轴、全部发射源配置、目标材质／片段／部件／参考尺寸及其他模型资产均与修改前一致。实际查看相同透视镜头的 10 tick、73 tick 前后截图，主轮廓保留，低分段球壳略有棱角；未做全视角或独立性能采样，不把面数降幅当作 CPU／GPU 占用降幅。
- 修改前原生备份为 `v3/performance_backup/model_complexity_before_20261015_620ece2e.fxproj`，保存了本轮开始时的当前工程状态；此轮没有修改着色器，备份仍引用原资源路径。原工程 `v3/tutorial_shockwave_v3.fxproj` 已覆盖保存，dirty=false，`v3/charge/tutorial_charge_v3.fx／.fxpack` 重新导出，包 13 文件、warnings=[]。
- 前后截图为 `build/mcp/v3-low-poly-before-10.png`、`before-73.png` 与同前缀 `after-10.png`、`after-73.png`，已实际查看。此轮只有原生资源和两份系统文档修改，没有 Java 修改、编译、Data 或测试；保留既有暂存／未暂存工作，没有清理用户资产。

## 2026-10-15：Minecraft MCP 原生命令路径修复

- 按用户指定的下一步，修改子项目 `PlayerWorldHelper.java` 的命令入口，优先调用当前 Minecraft 连接的原生 `sendCommand`；保留未涉及的反射代码，不改 bridge、工具协议、控制模式或全局配置。
- 根因由当前 IDEA API 与源码确认：旧入口没有读取 `LocalPlayer.connection`，单机执行分支也未使用当前 API。原生连接会先分流 NeoForge 客户端命令，再以玩家权限与签名发送服务端；不直接使用单机控制台，不在已提交或失败后重复发送。
- IDEA 修改行格式化、诊断与项目编译成功。真实 MCP 的 `/sparkc profiler info` 与 `time query gametime` 返回 `sent=true, method=conn.sendCommand`，日志分别确认 Spark 状态及“游戏时间为906685刻”。Data 退出 0；无 TypeScript 修改，不执行无关 bridge 构建。
- 多人、权限拒绝及签名参数命令未验收；`sent` 不等于服务端执行成功。未处理其他历史输入／坐标问题，没有新建备份，原有源码和资产备份保留。
- 最终 Data 为 19 文件、写入 0／删除 0，生成目录 Git 状态不变。移除本轮代理断点，保留三个用户断点；Debug 恢复运行。退出 MCP 控制后实际确认 ModularUIScreen、主窗 maximized=1、mouseGrabbed=false、GLFW_CURSOR_NORMAL。V3 在 80 tick 暂停，28 对象；原生预览定位产生 dirty=true，本轮没有覆盖磁盘工程或导出资产。

## 2026-10-15：模型采样池、绘制精简与发射复制开销

- `PhotonModelSamplerUtil` 增加仅运行绘制的 `sampleForRender`，只构建实际使用的部件或材质分组；完整 `sample` 仍向编辑拾取、骨骼显示和 OBJ 导出提供全部结果。临时向量和面角数组使用每线程最多两个工作区的对象池，输出网格始终拥有独立数组；无形状键时不复制整份基础顶点，无动态覆盖时不生成 ID 列表。
- `PhotonModelEmitter` 对没有部件／骨骼／形状轨道的模型跨时间复用几何，透明度继续独立按片段时间更新。新 `PhotonModelCullUtil` 根据眼点、实际绕序和镜像变换判断背面；仅精确的普通 TextureMaterial、有效单面设置且非线框时启用 CPU 提前剔除，双面、奇异变换和自定义着色器保留原生 GPU 判断，避免忽略顶点位移。
- `PhotonModelIOUtil` 的原有 32 条／4 Mi 字符 LRU 同时管理不可变准备快照、纹理依赖列表与材质 NBT。资产编辑仍返回副本，材质 NBT 也返回副本；`PhotonModelEmitterConfig` 在依赖未变化的复制过程中跳过整份 JSON 重写，资源包改写路径时仍消费修改后的依赖。没有复用仍在粒子引擎中的 FXRuntime 或外部播放句柄。
- IDEA 修改范围格式化和生产文件诊断通过，项目编译成功。四项新回归加入后，模型回归共 20 项通过；最后一次 Data 退出 0。
- 同版算法比较：核心模型在预热后各采样 50 次，完整编辑采样分配 90,070,400 字节、76.5021 ms，精简运行绘制采样为 34,235,600 字节、54.8324 ms；这是两条采样 API 的同场比较，不是整个客户端前后利用率。
- 同段 110→150 tick 的 40 次引擎更新，修改前分配 59,895,528 字节／161.7524 ms，完成依赖复制优化后为 38,746,696 字节／116.9884 ms，分别减少约 35.3%／27.7%。客户端重启、预热和 Debug 求值会影响结果，不据此宣称 FPS、GPU 或长期压力达标。前一阶段只有绘制精简时，更新分配仍为 60,654,656 字节，促使继续处理发射复制。
- 现场确认原生深复制保持相同资产 JSON、准备快照共享、材质 NBT 副本隔离；临时依赖变更经配置反序列化后被消费、原模板不变，静态临时模型跨部分刻复用网格。持续核心的动画姿态未被错误复用。35 秒补充 Spark 报告保存于 `run/client/config/spark/profile-2026-10-15_09.08.54.sparkprofile`；实际查看嵌入场景的 80 tick 画面，暂停时 CPU 时间为零不作为性能结论。
- 最终 Data 为 19 文件、写入 0、删除 0，生成目录 Git 状态与修改前一致。新旧采样 API 语义引用检查完成，完整采样仍服务编辑器／OBJ；六份 Java 文件诊断无错误或警告，20 项回归通过。新增工具类由 IDEA 自动登记，原有 437 项暂存范围未手动处理。
- 已通过锁定的 Spark 1.10.172 获取 45 秒本地报告 `run/client/config/spark/profile-2026-10-15_08.39.46.sparkprofile`。Windows/amd64 回退 Java 引擎，不支持其 allocation profiler；分配量另通过 ThreadMXBean 观察。报告包含播放、空闲和 MCP 操作，只用于定位调用路径，不作为严格 CPU 百分比或 GPU 计时。
- 性能分析时 `execute_command` 报 `no command method found`，暂通过锁定 NeoForge `ClientCommandHandler.runCommand` 提交 Spark 命令；随后按用户安排修复 MCP 并完成真实调用检查，见本日命令修复记录。
- 本轮没有删除或改写 V3 几何、着色器、用户资源及共享资产，没有新建资产备份；保留上一轮 `v3/performance_backup/`。IDEA 自动登记新增工具类，未手动暂存、提交或重置既有修改。

## 2026-10-15：V3 与模型特效运行性能优化

- V3 原生工程的四个流动／电弧球壳由 960→224 三角面，核心和首爆椭球改为 528 面；保持发射数量、寿命、尺寸动画及参考换算。私有 electric 主弧按球面角度求段，waves 减少一层噪声，三张重型图增加全透明短路；已确认三图有效。工程和 electric／waves／impact_ellipsoid 图备份于 `v3/performance_backup/`，未删除用户资源或共享图。
- `PhotonModelIOUtil.java` 增加有字符预算和条目上限的严格 JSON 副本缓存；`PhotonModelSamplerUtil.java` 增加校验过的独占准备快照和三角拓扑；`PhotonModelEmitter.java` 使用准备结果、相同姿态缓存、透明早退及复用顶点临时向量；`PhotonModelEmitterRuntime.java` 缓存私有着色器槽提交设置，并保留动态控制和失效边界。未修改原生纹理材质的实时复制语义。
- `PhotonModelRegressionTest.java` 增加三个缓存隔离、动态采样及透明度回归，16 项全部通过。第一次失败仅为新测试错误地限定畸形 JSON 的异常子类型，已修正测试断言，生产解析器仍严格拒绝输入。修改行已通过 IDEA 格式化，编译成功；Data 实际退出 0，19 份生成文件写入 0，IDEA/Git 未发现生成目录改动。
- 编译触发 IDEA 结构热替换，旧运行实例新增 `renderers` 字段为空，客户端日志确认 NPE 并退出；改为惰性实例缓存和独立静态初始化容器后重新启动，恢复原世界、工程及视角。现场九项缓存复用／失效检查均为 true，游戏主窗口 maximized=1；没有把先前退出或未保存状态报告为成功。
- 同段 40 刻调试采样平均 31.274→3.998 ms，逐刻粒子数序列一致；系统优化配旧几何为 11.648 ms，单独精简几何为 8.399 ms。该结果不是 GPU 或系统占用率测量，预热、调试和客户端重启差异保留；尚无有效独立 GPU 计时或全视角／多实例压力验收。最终工程 dirty=false，定义及包重新导出，包 13 文件、warnings=[]；截图和细节见特效 MCP 文档。

## 2026-10-15：V3 贴球电弧、粉色流动与持续生灭

- 修改 V3 原生工程及其私有 electric／waves 着色图，保留用户资源和共享着色器。黄色电弧改为 10 组独立生灭的六段锯齿与短分叉，并加粗增密；粉色流动使用双向密集纹路及生灭遮罩。两个图现场均有效。
- 电弧／流动模型收近光球，持续段新增两个模型模板和两个原生发射源，当前 28 对象、28 轨道。粉色流动每 5 tick 一个、寿命 12 tick；电弧每 4 tick 一个、寿命 10 tick，各自显现、扩大并淡出。局部模型半径分别从 0.37／0.39 逐渐扩大到 0.6，仍保留原参考尺寸换算。暗能量起始尺寸从 1.8 收至 0.9。
- 三组粉环整体尺寸缩为上一版 60%，仅蓄力和首爆交接时产生，持续阶段移除；80 tick 实际存活粉环为 0，粉色流动／电弧为 2／3 个。保留蓄力大幅脉动，核心持续段加入约 ±4% 的小幅起伏。
- 查看 16／26／84／180／200 tick 阶段，200 tick 无可见残留；最后收近暗能量及增加实例增长后查看 80／86 tick，截图为 `build/mcp/dense-arcs-growing-80.png` 与 `dense-arcs-growing-86.png`。最终工程保存通过、dirty=false；定义与包重新导出，包 13 文件、warnings=[]。同步系统文档；未修改 Java、重新编译、运行 Data 或进行性能／全视角／世界附着验收，没有新建独立备份。

## 2026-10-14：V3 模型汇聚、宽环独立生成与快速黑星

- 紫色汇聚从 Billboard 替换为真实弯曲模型，99 顶点／128 三角面，使用已有发射器发射器每 2 tick 生成独立实例。模型片段以 Morph 向内移动中心线、旋入并退场；新建私有 purple_model 图。10 tick 现场累计生成 5 个模型，图有效。
- 球边三组粉环保留为模型模板，带宽按截面中心扩大 2.5 倍，保留参考尺寸换算；三个源从 6／9／12 tick 错开发射，每源每 9 tick 一个、实例 12 tick。各实例固定出生朝向，0.45→1.45 放大、0→0.8→0 淡入退场；随机三轴离散朝向在制作时采样并保存，重播复用序列。蓄力核心／波壳／电弧／辉光脉动幅度加大。
- “粒子分阶段出生”按目标拆分，当前 24 对象、22 轨道。首爆椭球时序加快一倍，十字星寿命 16→10 tick；按最新要求先大、变黑同步缩小、复亮变大、逐渐变细消失。查看 22／23／24／25／27／29 tick 帧，确认 23 tick 黑星已处于小尺寸，29 tick 星不可见；200 tick 无可见残留。
- 首次保存被原生精确读回拒绝，已确认仅一个源的 transform/localRotation 归一化改变约 1e-7。出生朝向改用可稳定往返的关键帧，没有改 Java 或放宽校验；最终保存通过且 dirty=false。定义与包重新导出，包 13 文件、warnings=[]；一次包调用无有效 JSON 回执，随后取得成功回执。系统文档与制作参考同步，未重新编译、Data 或性能检查。
- 仅替换本轮自建平面紫带对象，保留资源文件、用户工程、旧版和原三个断点；原生编辑历史可撤销。未创建额外文件备份，未暂存、提交或推送。外观仍待用户验收。

## 2026-10-14：V3 稳定发射、粒子紫带与五段首爆

- 用户选择后方半球限制，持续 Cone 半角从 35° 扩至 60°。查明每颗新粒子的 26 tick 延迟占满队列：现场 150 中 82 等待，上限 160；与原生默认 Exacting／0.5 每刻／无 burst／零延迟比较后，改用原生 ControlTrack 定阶段，粒子延迟归零、普通火花每刻 1、独立星粒每 4 刻 1。
- 新增私有 warm_star、burst_cross、purple_spark、dark_energy、impact_ellipsoid 图；星粒偏黄且更不透明，原共享图保留。固定三条紫带对象替换为独立旋入粒子；两条新增粉带保留。光圈蓄力脉动、持续尺寸稳定，辉光使用材质波动，径向暗能量独立控制。
- 首爆重做为立体椭球与十字星，按图片 1→3→2→4→5 调整亮黄／深紫／复亮、尺寸、伸长与消失。仍为 200 tick，20 个对象；未新增激光、实体或伤害逻辑。
- 原生批次校验与提交完成。十字大小 Constant→Curve 的类型模板不足，经已授权 Debug 在工程副本建立原生 Curve，再走 prepare/commit；没有放宽校验或新增接口。连续 40 刻真实新生身份数均为 1，延迟数 0，末次存活 11，方向样本最大半角约 58.214°。修正前轮年轻粒子计数不足以证明连续出生的结论。
- 查看蓄力、21／23／26／29／32 tick 首爆、80 tick 持续、184 tick 终结及 200 tick 无可见残留画面。11 个活动私有图统一检查无编译错误，后续细化已实际渲染。覆盖保存 V3 .fxproj，dirty=false；导出 .fx／.fxpack，包 13 文件、warnings=[]。未删除用户资源、未额外备份、未暂存或提交；本轮没有 Java 修改，未重复编译或 Data。外观仍待用户验收，未做性能、全视角或世界附着检查。

## 2026-10-14：V3 新增粉色双带、后向圆锥输出与首爆层

- 用户确认向后为局部 -Z，并补充圆锥发散。原持续发射器改为 Cone 35°、Shape X=-90°、每刻 8、速度 4～6、寿命 10～16、上限 160；缩短拉伸长度。原粉环保留，新增两个不同旋转平面的粉带，复用 ring 图。
- 紫带固定带宽，以移动 UV 窗口从外端向内端旋入，20 tick 后不可见；不再参加持续和终结。首次小爆发新增十字星及 Morph 扩张的椭圆冲击波，新增私有 impact_ring 图。总时长仍为 200 tick，共 18 对象。
- 原生批次编辑、资源写入与版本检查完成；Cone Float 类型模板不足时，经已授权 Debug 在工程副本建立原生形状，再调用现有 prepare/commit 事务，不改变通用接口或校验。6 个模型图编译错误为空，所检 157 个队列粒子均向 -Z，最大半角约 34.8657°；四刻均有年轻粒子。实际查看 10／18／24／80／200 tick 画面，结束无可见残留；未做性能、全部视角或世界附着验收。
- 覆盖保存 V3 工程，dirty=false；重新导出 .fx／.fxpack，warnings=[]，包 11 文件。最终透视、24 tick 暂停、GLFW 最大化=1、内嵌编辑器，控制模式退出；原临时断点均移除、用户三断点保留。未删除用户资源或旧版本，未创建额外备份，未暂存、提交或推送。
- 同期鼠标／通知修复在重启后的客户端实测：mouseGrabbed=false、cursorNormal=true；定时提示保留原焦点、可命中后代为 0，普通确认框仍取得焦点。通知顶部实际画面已查看；完整跨窗口交互仍未验收。源码编译和 Data 结果见下条，未因特效参数调整重复构建。

## 2026-10-14：修复 MCP 交回鼠标与 Photon 定时通知层级

- ControlModeHelper 退出时按实际界面释放鼠标；无界面使用原生抓取方法，移除强写 mouseGrabbed=true／GLFW_CURSOR_DISABLED，保留窗口活动检查和既有 LWJGL2 入口。
- PhotonEditorNotificationUtil 明确通知栈 zIndex=1 并关闭提示子树命中；DialogMixin 仅跳过该栈内定时通知的 focus，普通确认弹窗及关闭设置时保留原逻辑。
- 通过 IDEA 检查引用与既有 Git 差异，按修改行格式化，三文件编译成功；通知两文件 errors=[]，MCP 文件原有 8 条警告未扩展处理。Data 实际输出 BUILD SUCCESSFUL、19 文件、written=0，生成目录 Git 无差异。
- 运行中的旧客户端随后已正常保存并退出，日志无本轮崩溃证据；退出原因未确认。已通过现有 Client Debug 配置重新启动，交互验收待完成。未删除用户资源、未新建备份、未暂存或提交。

## 2026-10-14：V3 改为十秒四阶段并移除环带缩放

- 原生工程调整为 0～20 tick 汇聚、20 tick 小爆发、26～180 tick 持续输出、180～200 tick 爆炸结束；最终 14 对象。新增持续输出、小爆发和终结星火，保留既有空控制器。
- 去掉粉环与三条紫带的全部根／子部件缩放，改用沿 UV 显现、快速旋转及 1.8 倍材质流动；终结阶段使用环的径向 Morph 与紫带外移保持带宽。加快原生汇聚与发散粒子，未新增 Java 接口或固定特效配方。
- core／waves／electric 使用球面方向连续三维噪声并修正极点面 UV；实际查看无 Bloom 侧面及本轮 10／18／80／184／200 tick 画面。5 个模型图编译错误为空，200 tick 无可见残留；仅外柔光仍有一个 Alpha=0 粒子，不能记为全部对象销毁。
- 覆盖保存同一 V3 .fxproj，dirty=false；重新导出 charge/tutorial_charge_v3.fx 与 .fxpack，warnings=[]，包 10 文件。未删除用户资源、共享图或旧版本，未新建备份，工程事务保留撤销历史。
- 仅通过 IDEA 更新两份系统文档并读取诊断；未执行编译、Data、测试、同步或重载。游戏保持最大化、内嵌编辑器和透视，临时断点已移除、Debug 恢复运行，用户三断点保留。Git 仍为 757 项、437 暂存、623 未暂存、3 未跟踪、0 冲突；未暂存、提交或推送。

## 2026-10-14：V3 仅重做蓄力并拆开各层进退

- 使用游戏内 Photon MCP 在清空后的 V3 重建金核、粉色波壳、细电弧、开口环、宽紫拖带、双柔光和两种短命火花。按用户补充将金核／波壳／电弧拆为独立模型，所有模型与粒子分别控制成长和退场；约 22～34 tick 依次加入，核心与外层从 52～56 tick 分批淡尽，外柔光保留到 58 tick，60 tick 画面清空。期间出现的既有空控制器保留；最终 5 模型、4 粒子、1 锚点及该控制器，共 11 对象。
- 新增 7 份 `tutorial_shockwave/v3/charge/` 原生图，复用两份 refined 火花图；7 图编译错误列表为空。实际查看组合、隔离与无 Bloom 画面，以及最终 30／34／40／48／52／56／60 tick 分层时序截图。此前同进同退版本的 56 tick 粒子数全零只属于该版本证据，最终退出以新 60 tick 截图为准；不作为完美复刻、世界附着或性能验收。
- 原 V3 `.fxproj` 覆盖保存，dirty=false；新增蓄力专用 `v3/charge/tutorial_charge_v3.fx`／`.fxpack` 并在时序修改后重新导出，warnings=[]，fxId=lobecorp:tutorial_charge_v3，包 10 文件。旧版、旧整束导出、共享着色器与用户资源保留，未删除文件，未新建额外备份；原生工程编辑留有撤销历史。
- 仅以 IDEA 修改系统文档；资产通过原生资源与工程事务写入。新曲线缺少 NBT 类型模板时使用已授权 IDEA Debug 在副本构造原生曲线并调用现有 prepare/commit 事务，后续常规 MCP 曲线编辑成功。未改 Java、依赖、战斗规则或生成资源，未另行执行编译、Data、测试、同步或重载；仅通过 IDEA 启动现有 Client Debug。
- 实际 GLFW 最大化状态为 1，编辑器嵌入游戏、透视；最终保持 40 tick 暂停，控制模式退出。临时断点移除、用户三断点保留，Git 既有暂存／未暂存／未跟踪工作保留。原贴图、角色动作、真实局部 RGB 点光与雾均未实现。

## 2026-10-14：按要求仅清空教程冲击波 V3 工程内容

- 用户收窄范围为仅清理 V3 本身的项目内容。通过游戏内特效 MCP 单批移除 V3 的 17 个对象并清空 3 条原生时间轴轨道，覆盖保存同一 `.fxproj`，返回对象 0、轨道 0、dirty=false。
- 保留 V3 工程文件、着色器、已有 `.fx`／`.fxpack` 和旧版本，不删除用户资源或其他文件；旧导出仍含清空前内容。本轮未新建备份，原生清空操作留有撤销历史。
- 通过 IDEA 更新本记录与 photon-effect-mcp.md；未改 Java、未执行编译、Data、测试、同步或重载。项目原有暂存／未暂存／未跟踪工作保留。

## 2026-10-14：Photon 雾与体积效果入口核对

- 按用户要求自行读取游戏实际 ModList、Photon CodeSource、Iris 当前光影包、原生能力／资源、锁定版本和依赖源码。运行 Photon 26.1.2.3／LDLib2 26.1.2.42／KilaGraph 26.1.0.15 与版本目录一致；Iris pack=(off)、enabled=false，shaderpacks 仅发现 ComplementaryReimagined_r5.9.3.zip。
- 确认原版距离雾节点与全屏图的边界，以及可复用的 SceneDepthInput → 显式采样器 → ScreenToWorld → 自定义全屏计算 → RenderGraph 路径。当前注册项未发现现成的点光／体积光／雾模板；没有将这个枚举结果写成整个图系统无法制作。局部雾、体积积分及 RGB 环境点光仍未接入案例。
- 仅通过 IDEA 局部修改 photon-effect-mcp.md、制作技能的 references/native-workflow.md 与本记录；保留用户已有修改。未修改代码、特效资产、依赖、光影开关或生成输出，未编译、Data、测试、同步或重载；本轮未新建备份。
- 原生工程为 tutorial_shockwave_refined.fxproj，16 对象、70 tick 暂停、dirty=false；实际主窗已最大化，本轮临时断点移除并恢复运行，用户三断点保留。修改前后三份 Markdown 的 IDEA 诊断无问题；Git 原有暂存／未暂存／未跟踪工作保留，未手工暂存、提交、推送、重置或删除。

## 2026-10-14：单属性多绑定与教程冲击波分层续作

- 依据完整 27 步重新制作原生分层工程 `tutorial_shockwave/refined/tutorial_shockwave_refined.fxproj`：6 模型、9 粒子、1 控制器、22 ShaderGraph、44 绑定、3 原生后期轨道。补入第二蓄力辉光、环带、宽拖尾、星闪后拉伸球、独立亮核／暗波／径向暗能量、六层束、沿束混合火花、平面映光和曲面扬尘；图内执行 UV 扭曲、侵蚀及参与实际明暗的扰动法线，烘焙椭球的作者法线按实际半径归一化。原工程与原九图保留，不删除用户资源。
- 实测同属性的分轴绑定只执行首项，根因为 `PhotonAnimationMixer.Layer.sample` 的 findFirst。仅修改该入口，按定义列表顺序采集全部匹配绑定，对原生属性只采样一次；离散属性仍仅接收 OVERRIDE，单绑定、函数生命周期／随机采样和释放所有权沿用既有实现。通过 IDEA 合并既有工作并仅格式化 181～216 行，无导入优化或成员重排；未改协议或新增专用效果配方。
- 已加载客户端实测：运行槽从 12／2.4／1 变为 6／1.2／0.5，沿束 shape.scale=[0.5,0.5,6]、shape.position=[0,0,3]；关闭后模型 opacity=0、9 类粒子生命周期颜色函数 Alpha=0；同实例重触发到动画 70 tick，problems=[]。早先直接读取 config 字段只得到基础值，不作为运行槽失效证据；颜色倍率保留在已有生命周期颜色函数适配层。
- 当前 22 份图编译检查有效，错误列表为空。阶段画面通过原生 seek／区域截图记录；组合工程已评审，未逐层隔离或完成用户外观验收。程序纹理重建不等于取得原始贴图；真实环境点光尚未接入，用户指出的体积光／雾须按实际入口继续核对，不再仅凭类名负检索断言整个版本缺失。
- 原生保存 dirty=false；定义与包导出 warnings=[]，包 23 份文件，fxId=lobecorp:tutorial_shockwave_refined。编辑器保持游戏内嵌与透视，真实游戏主窗最大化为 1；本轮临时断点已移除，保留用户原三断点，游戏继续运行。
- IDEA 编译 isSuccess=true、problems=[]；Data 实际 exitCode=0、BUILD SUCCESSFUL in 29s、written=0。前后生成目录 25 文件与用户 the_queen_of_hatred 的 41 文件集合及 SHA-256 全部一致。未新增单元测试、手改生成输出、提交、推送、重置或清理用户文件。

## 2026-10-14：模型面角法线与 AI 特效制作技能

- 根据教程冲击波对照中确认的法线丢失问题，修改 `PhotonModelSamplerUtil`：读取已有 `Face.normals`，按尺寸／局部伸长、归一化权重混合蒙皮及部件层级的组合矩阵逆转置更新；镜像按实际绕序修正，`insideOut` 保留独立翻面。缓存同次采样内的顶点矩阵，公开接口及文件格式不变。
- 缺失／零法线、奇异变换和非有限结果回退到采样面法线。形状键只有位置增量，实际变形时保持原有变形面法线计算，不宣称支持形状键平滑法线增量；未修改骨骼、尺寸、位置、材质或动画数据协议。
- 新增项目技能 `.codex/skills/lobecorp-effect-authoring/SKILL.md` 与按需读取的 `references/native-workflow.md`，记录游戏内模型／ShaderGraph／粒子制作、透视与主窗最大化、固定流程合批、参考视频防连播、版本／资源事务、变量、阶段对照及可编辑工程交付。系统索引新增技能入口，特效 MCP 与模型文档同步。
- 通过 IDEA 对本次 Java 行范围格式化，关闭导入优化与成员重排；修改前后采样文件诊断为空，私有 `appendTriangle` 的语义引用仅为本类调用，公开 `sample` 调用保持。新增技能与操作参考无 IDEA 问题；模型文档和索引各保留原有表格弱警告，未扩大格式修整。
- 本轮 IDEA 编译 `isSuccess=true`、`problems=[]`；Data 实际退出码 0。25 份生成文件及用户 `the_queen_of_hatred` 的 41 份文件，前后集合和 SHA-256 均一致。未执行额外单元测试或技能验证脚本。
- 代码及技能文档已完成；后续材质、动画与外观修改通过游戏内原生工作流继续，尚不把计划中的外观修复写为完成。保留已有暂存／未暂存／未跟踪工作，未手工暂存、提交、推送、重置或删除；未创建独立源码备份。
- 根据用户指出的教程遗漏，再次打开并核对同一 BV15vq1B2EUY 的 URL、标题和 100.9 秒时长；对 10～100 秒每两秒采样，实际查看 12 张联系表中的 46 帧。已建立 27 个分解步骤的工程对应表，补录此前遗漏的环带、Star Shot 拉伸球能量体、Dark Waves／Dark Energy、束后烟雾、两类内芯、沿束火花、地面尘雾、真实点光源和后期；修正 CEED PLASMA 的原文识别。技能增加完整步骤覆盖要求，区分讲解时间与效果运行时间。
- Client Debug 实际启动，游戏主窗 GLFW_MAXIMIZED=1，Neo 快速创建入口及创建动作以一个安全排队片段完成；MCP 玩家查询确认创造模式主世界，ping=pong、control_mode=true。原生工程重新打开为游戏内嵌标签，保持透视并暂停；九份原图与检查基线完全一致。原生另存备份 `before-refinement-20261014.fxproj`，未提交新的材质或模型资源；视频在 100 秒暂停，没有切换下一条。

## 2026-10-14：教程冲击波原生工程与模型 MCP 状态去重

- 按用户指定的 BV15vq1B2EUY 制作原生教程工程，参考同一视频的蓄力、星闪、主束与收束阶段。游戏主窗真实 GLFW_MAXIMIZED=1、2560×1494；编辑器保持游戏内嵌，透视相机，无独立 OS 编辑器窗口。保留用户已打开的 lasel.fxproj 及其未保存状态。
- 原生工程包含 4 个模型、5 个粒子发射器和 1 个命名动画控制器，九份 ShaderGraph 分别负责等离子主束、光球、两类拖线、半球烟雾、星闪、辉光、火花与细丝。模型／动画通过游戏原生对象和候选工程事务处理，着色图通过原生资源事务处理；未添加仓库模型配方或 GLSL 资产，未修改实体技能、命中框、伤害和网络。
- 命名动画 tutorial_shockwave 长 140 tick，默认不循环；length=12、width=2.4、intensity=1、enabled=true。19 条绑定复用既有运行槽和参数混合，原生控制轨道负责同实例整段重触发；源头模型与粒子宽度按比例变化，开关乘算全部表现对象的 Alpha。实测 6／0.8／0.45、关闭与重新触发，以及数值／布尔边界限值通过，问题列表为空。
- 已确认直接沿用粒子内部 startDelay 时，等待中的粒子保留出生颜色，开关变化后首次可见帧仍可能渲染旧插值颜色。命名播放改由五个控制片段安排发射时机，并以原生属性曲线覆盖该实例的 startDelay=0；普通场景原始延迟保留。调整后关闭状态下四模型 opacity、五类粒子颜色函数及现场 17 个粒子的插值 Alpha 均为 0。未扩大修改 TileParticle 或第三方渲染源码。
- 此前制作四个模型时状态响应达 103,512,451 字节，超过 67,108,864 字节通道上限。通过 IDEA 调整 PhotonEffectProjectUtil／PhotonEffectEditUtil：含模型工程返回 snapshotIncluded=false，模型资产单独作为 asset 返回，省略参数和 schema 中重复的 assetJson 字节数组；dryRun 同样报告 proposedSnapshotIncluded。实际 dryRun 通过、版本与历史不变、未返回重复 SNBT；原生文件、完整候选校验和撤销格式不变。PhotonEffectProjectUtil 与 PhotonEffectAutomation 在重建／seek 后恢复视角，连续定位实测相机与投影／显示设置不变。
- 原生保存 tutorial_shockwave.fxproj，导出 tutorial_shockwave.fx 与 tutorial_shockwave.fxpack，目录为 run/client/ldlib2/assets/lobecorp/tutorial_shockwave；保存 dirty=false，定义与包的 warnings=[]，包内 10 份文件，fxId=lobecorp:tutorial_shockwave。九个图的材质编译检查无错误。阶段截图为 build/mcp/tutorial-shockwave-charge／flash／beam／decay／finished.png，实际区域 1225×641，源帧 2560×1494；结束预览留在 65 tick 并暂停。
- IDEA 项目编译 isSuccess=true、problems=[]；Data 配置实际 exitCode=0。运行前后生成目录均 25 份文件、用户 the_queen_of_hatred 目录均 41 份文件，逐文件 SHA-256 与集合均未变化。三份相关 Java 诊断无新增错误，ProjectUtil 的 createNewEditor 可能空指针提示为已有警告，另外两份诊断为空。此前通过 IDEA 修改并格式化相关 Java 类，本轮未再改写这些源码；同步特效 MCP 文档、系统索引和本记录。
- 本版仍与原视频的原始贴图、烟雾细节、后期和镜头存在差异，未声明逐像素复刻或用户外观验收；未执行额外测试、多人、战斗接入或性能验收。保留既有暂存／未暂存／未跟踪工作，未手工暂存、提交、推送、重置或删除用户资源；未创建独立备份。
- 特效 MCP 文档的两处修改表格通过 IDEA 局部格式化，关闭导入优化与成员重排；该文档与修改记录诊断为空，系统索引保留一项已有表格弱警告。连续 seek 的视角完全一致，模型工程 dryRun 不改变版本或历史，130 tick 截图确认效果已消散。收尾 Git 为 dev，757 条状态、437 暂存／623 未暂存／3 未跟踪、0 冲突，与本轮恢复工作时相同。

## 2026-10-14：模型特效常规场景与动画场景

- 用户确认模型工程按照特效项目分常规场景／动画场景，复用原场景和时间轴，默认不限时，有活动动画时遵循其时长与循环；本轮据此实施并整理系统文档。此前只放开空片段播放的处理不代表已经完成场景分离。
- 新增 `PhotonModelAnimationSceneView`，与常规 `SceneView` 构成中央标签，借用同一世界、相机、运行时和粒子管理器；借用期间关闭自动释放，退出、移除标签和关闭工程时归还。`PhotonModelProjectEditor` 登记面板、布局安置与清理入口；模型文件协议和资产版本 1 不变。
- `PhotonModelEditor` 统一场景、左侧工作区和检视切换，拦截切换回调重入及同工作区重复打开。已确认的布局问题是轨道选择／录制再次执行标签打开会恢复最大化布局；同工作区提前返回后，这些操作不再走恢复布局路径。`PhotonModelSceneController` 将活动片段与记住的编辑选择分离，常规场景不读取所选片段的播放终点或循环；初始化前定位不调用场景重模拟。
- `PhotonModelAnimationWorkspace` 在内存中分别保存常规场景与各片段的小数 tick 时间和视图快照；进入／切换动画恢复位置后暂停，退出动画恢复常规场景原时间及播放／暂停状态。切换先提交关键帧拖动，重命名迁移快照，删除清理失效快照，不添加持久化字段、旧格式兼容或第二套时钟。
- `PhotonModelTimelineView` 的播放、停止、步进、时间输入、标尺和跳末尾按当前场景处理，不再强制进入动画；时长、循环和轨道展示统一读取活动片段，常规场景禁止写入记住的动画。复用原生 `PhotonTimelineEditAccess` 捕获／恢复视图，再校验并恢复模型自身轨道与关键帧选择。通过 IDEA 语义引用核对选择、场景采样切换及调用入口，没有保留旧的直接工作区采样切换。
- 仅通过 IDEA 编辑 6 份 Java 文件并格式化涉及行，关闭导入优化与成员重排；最终诊断均无错误／警告。最后一次 IDEA 编译检查 `success=true`，`compileJava` 实际执行，编译消息计数未提供；未将 `compileTestJava` 当作测试执行。最终 Data 配置实际退出码 0；生成目录前后均为 25 份文件，逐文件 SHA-256 相同，未手改生成输出。一次格式化行范围超出文件末尾已按实际行数重试成功。
- 同步 `photon-model-effects.md`、系统索引及本记录，整理当前规则、已确认根因、生命周期、公开入口和维护约束；两份说明文档保留原有表格弱警告，不扩大格式清理范围。范围内 Git diff --check 退出 0，保留既有 LF→CRLF 提醒；新增场景类由 IDEA 自动登记为已暂存 A，未手工暂存、提交、推送或重置。保留既有暂存／未暂存工作，未创建独立备份。未执行额外测试、同步、重载、客户端启动或游戏内场景／输入验收，未将静态检查当作交互成功。

## 2026-10-14：项目与全局 Skill、MCP 整理

- 审阅 5 个项目与 45 个全局用户 Skill。项目保留 5 个职责独立的入口，使用 IDEA 的 intellij-index Document API 对 5 个入口和 3 份指挥家参考做局部替换；修正事件规范断链、旧接口／动画调用、包路径、版本与工具数量叙述，迁移计划明确为历史。IDEA 语义确认 `IEntitySkillAccess`、`ILcCustomAnimatable`、`IEntityStateHolder`，读取当前附件与动画源码核对委托和原生触发接口；8 文件回读一致、16 个本地链接可读，Markdown 诊断 8/8 analyzed、0 问题、无截断。
- 全局修正 33 个用户 Skill 入口及 2 份参考：替换当前不存在的工具调用，收窄泛关键词触发，修正错误固定仓库、自动写 FAQ／上传图片／部署操作和验证边界；保留有独立职责及适用版本的资料。5 个第三方入口去除旧 frontmatter 字段，保留原有显式调用 YAML 策略。纯转发 `grill-me` 完整备份归档，全局保留 44 个入口；索引同步职责、旧名映射和检查限制，不再固定插件版本化缓存路径。系统与插件文件未手工修改。
- 全局 `config.toml` 删除与 Rider 同端口的旧 `jetbrains` SSE 别名，Minecraft bridge 从历史修复副本切换为正式子项目并补齐工作目录，服务器由 11 项减为 10 项。TOML 静态解析、原始备份字节比对与语义比较通过，其余配置、审批策略与密钥保持原样；项目配置原本正确，未修改。Morph 锁定版本依据不足，保留原配置；监听归属查询受沙箱 CIM 限制，未将缺少工具暴露判为废弃。
- 修正 5 份全局辅助脚本的实际失败／JSON 退出码、Godot 缓存保留与退出码传播、ImageMagick 参数与图集尺寸／padding 一致性、并发与计时器边界、有限生成等待／终态、下载失败和大图编码。错误生成随机 UID／缓存路径的 `generate-import-files.ts` 已归档，导入交给 Godot 原生能力。已做精确片段、字节回读与独立静态复核；未运行这些脚本、ImageMagick、Godot 或外部生成 API，不提供运行成功结论。
- 同步本索引、指挥家与 Minecraft MCP 文档。初次系统文档诊断发现旧激光脚本的 2 条失效链接；通过 IDEA 核对独立清理任务记录后，将对应制作章节标为历史并移除失效链接，保留该任务的修改记录和历史验收含义。4 份系统文档最终均已分析、0 问题、无截断。原生 IDEA MCP 曾出现 UI 无响应与传输失败，后续项目编辑、语义查询和诊断使用仍可用的 IDEA 索引接口，没有重启 IDE 或改用命令行修改项目。
- 原始文件、归档与逐文件哈希清单位于 `C:/Users/wang_/.codex/backups/skill-mcp-cleanup-20261013/`（目录沿用任务开始日期）；保留本轮开始前的用户暂存、未暂存和未跟踪工作。全局文件超出 IDEA 项目编辑范围，采用有备份、唯一片段与并发检查的定点补丁。未修改业务源码、生成资源或游戏文件，未执行编译、Data、构建、测试、重载、重连、提交或推送。

## 2026-10-14：仅清理本任务创建的模型激光试稿

- 用户明确暂停新模型／着色器制作，要求仅清理并列举剩余文件；只清理本任务创建的内容。交付清单确认 `pink_laser` 与 `pink_laser_v2` 各六份工程／定义／包归属本任务。发现 V2 模型双面及 FX 控制器范围有后续改动后先保留，用户随后明确“这个也不要”，故一并清理；用户原有 41 个文件保留。
- 通过 IDEA 删除 12 份激光成品、当时剩余 6 份源码着色器、2 份模型制作脚本、6 份构建资源副本及 20 份激光交付／失败诊断／截图。此前新建的 V3 临时源码已在统一资源阶段移除，本轮不保留新效果或源码资产。
- 最小范围移除 package.json 的 model:laser、MCP 子项目 createModelLaser 任务和根 Gradle 的对应运行配置声明，保留通用 MCP、模型编辑系统、原参考星轨与原生特效入口及其他既有修改。系统文档与索引同步清理失效入口；旧历史记录保留当时证据含义。
- 本轮仅做清理与 IDEA 文件／诊断／引用、磁盘清单核对；未启动游戏、编译、Data、测试、同步或重载。未将其他任务在 2026-10-14 完成的模型默认渲染改动归为本轮结果。
- 收尾目标资源目录剩余 41 个文件，清理前后逐文件 SHA-256 均一致；核对 56 个旧试稿、源码、脚本和输出路径，残留为 0。IDEA 无新增错误，保留根 Gradle 一条既有冗余限定提示与两份文档的既有表格排版弱警告。Git 为 dev，756 条状态、436 暂存／623 未暂存／3 未跟踪、0 冲突；没有手工暂存、提交、推送或重置，保留其他任务的修改。
- 按用户“不保留”指示未新建旧稿备份，原有备份未动；参考查看专用后台浏览器已关闭。未清理用户文件，完整剩余文件清单随本轮结果列出。

## 2026-10-14：模型默认渲染与特效实例显式覆盖

- 用户确认模型默认、实例显式覆盖，且不兼容旧数据。`PhotonModelEmitterConfig` 新增原生 `modelRenderer` 与默认关闭的 `overrideRenderer`；模型项目保存独立 `renderer` 快照，缺少新必需节点时拒绝读取，不迁移旧字段。几何／动画资产 JSON 协议保持版本 1。
- `PhotonModelProject`、`PhotonModelEditor`、`PhotonModelExportUtil` 与新 `PhotonModelRendererUtil` 接通原生完整渲染设置、深复制、完整工程历史、预览、应用和定义／包导出。首次参数编辑不重建检视，过期面板不能继续提交。模型默认和实例覆盖以结构化 NBT 保存，材质依赖沿用原生打包器扫描。
- `PhotonModelEmitterRuntime`、`PhotonModelEmitter` 与 `LcPhotonModelEmitterTypes` 统一选择生效渲染侧，保留遮挡／视深排序扩展和 `renderer.orderInLayer` 动画路径，清理非活动运行槽。双面与阴影继续从实际部件属性参与绘制；`PhotonModelSceneController`／`PhotonModelPreviewMaterialUtil` 移除强制预览深度写入，UV 材质读取跟随有效设置。阴影是顶点方向明暗，自定义着色器需使用顶点 RGB；未改写用户着色器。
- 仅经 IDEA 编辑并局部格式化，关闭导入优化与成员重排；11 份 Java 文件最终诊断无错误／警告。初期 UI 查询超时及 native IDEA MCP HTTP 502 未作为成功证据；连接恢复后 IDEA index 编译检查返回 success=true，增量任务为 UP-TO-DATE，编译消息计数未提供；原生 Data 配置实际 exitCode=0。生成目录 25 份文件集合不变，SHA-256 仅中英语言 JSON 与两份对应缓存变化，新增三个翻译键已通过 IDEA 定位；未手改生成输出。
- 同步模型特效文档和系统索引，保留两项原有表格弱警告及模型文档两项既有 V2 脚本链接解析提示，未扩大范围修复。范围内 Git diff --check 退出码 0，保留 LF→CRLF 提醒。新工具类由 IDEA 自动登记，未手工暂存。未执行额外测试、资源重载、同步、客户端启动或游戏交互／GPU 验收；未重启 IDE、修改全局配置、提交、推送、重置或删除用户文件，未创建独立备份。

## 2026-10-13：模型时间轴默认不限时

- 仅修改 `PhotonModelTimelineView` 的播放、步进和内容终点：无选中动画时允许原生时钟持续播放及逐帧定位，不再使用新建片段默认长度作为空轴终点；有选中动画时保留其 duration／loop、非循环结束重播、循环暂停相位和小数 tick。没有动画时跳末尾保持无效，与普通空特效时间轴一致。
- 通过 IDEA 核对原生按钮、特效时间轴及模型场景时钟，语义引用确认既有 Mixin 仍调用原入口。只格式化修改行，关闭导入优化与成员重排；修改前后 Java 诊断均无错误／警告，完整内容比对确认只产生预定修改。同步[模型特效文档](photon-model-effects.md)。
- 本次为单类局部行为调整，未执行编译、Data、测试、同步、重载或游戏运行验收。保留既有暂存／未暂存／未跟踪修改，未手工暂存、提交、推送或重置；未创建独立备份。

## 2026-10-13：粉色 3D 激光 V2 与透视预览

- 根据已研究的成熟激光分解重做 `pink_laser_v2`：新增三个同名 JSON／VSH／FSH 资源、`model-laser-v2.mjs` 配方和 `create-model-laser-v2.mjs` 入口。主束使用一个 128 分段圆柱与一个着色器合成白亮主体、粉色包络、外晕、稀疏细丝和低频断续亮斑；独立模型负责一次性源头星爆和冲击环，复用原生命名动画及 length／width／intensity／enabled 绑定。旧激光与用户文件保留，没有新增 Java 系统或修改实体技能、伤害、射程和同步。
- 新 shader 从位置／UV 导数重建切向、轴向及面法线，修正早期预览中分裂偏移的条带；轴向密度随实际长度计算。intensity 为 0～1，经 opacity 同时影响透明度与 HDR 增益。星爆贴图已确认是纵向七帧，修正横向切帧后实际源头闪光可见。GLSL 格式化后恢复被 IDEA 插入空格的 moj_import 路径，三份 shader 最终诊断为空，最新资源重载 future 成功。
- 脚本按 apply／preview／recover／save 分阶段，默认不发布；相机不参与业务配方指纹，按用户最新要求全部改为透视，编辑器始终嵌入游戏。完整状态响应实测 13,300,404 字节，超过 SDK 默认 10 MiB；使用公开 maxBufferSize=32 MiB 后恢复正常，通过对象身份、完整资产、历史及原生 dryRun 严格恢复已提交批次，没有改 SDK、放宽校验或重复创建。
- 通过 IDEA 启动 Client Debug，游戏 GLFW_MAXIMIZED=1，实际 2560×1494；使用 Neo 开发环境快速 Create Test World 入口进入新测试世界。新 bridge 实际发现 61 项工具，原生视角与 GPU 区域截图运行成功；第 1／4／16 tick 透视斜视及侧视截图为 `build/mcp/pink-laser-v2-*.png`，scene 为 1225×641。当前对话旧工具目录未刷新，新增资源写入／PNG 导入／工程打开未在本轮验收。
- Client Debug 在真实 FX 运行态验证 `12／0.8／1／true` 改为 `6／0.4／0.35／false`，主束与两个辅助模型对应尺寸、透明度和开关均生效，问题列表为空；再启用并恢复默认值，资产 revision 不变。星爆 8 tick、冲击环 12 tick，新实例触发一次；同实例重播命名动画不重新创建结束的辅助模型，完整起手需新 FX 实例。
- save 退出码 0，六份 `.mfxproj/.mfx/.mfxpack/.fxproj/.fx/.fxpack` 实际保存于 `run/client/ldlib2/assets/lobecorp/the_queen_of_hatred/pink_laser_v2.*`，两个工程 dirty=false，所有导出警告为空；交付清单 `build/mcp/model-laser-v2-delivery-69af21a6-1d6a-421e-a836-f8a50171d3f9.json` completed=true，磁盘六文件存在且非空。
- IDEA 编译 success=true、problems=[]；Data 退出码 0，25 份生成文件集合与 SHA-256 未变化。配方诊断为空，入口保留与旧脚本一致的 4 项 SDK 导入解析提示；系统文档与索引同步。只格式化新增代码范围，资源保持原排版。没有执行额外测试、多人、性能或战斗接入；外观仍待用户评审。
- 临时断点与调试引用已清理，保留原全局断点静音状态及用户断点；MCP 已回执 control_mode=false，Client 保持运行、游戏内嵌编辑器保留透视预览。保留既有暂存／未暂存／未跟踪工作，未手工暂存、提交、推送、重置或删除；没有新建源码备份，旧稿保留用于对照。
- 收尾 Git 为 dev，765 条状态、445 暂存／632 未暂存／3 未跟踪、0 冲突，新增五份源码由 IDEA 自动登记。范围内 git diff --check 退出码 0，保留 Git 的 LF→CRLF 提醒；四份文档无新增错误，模型特效与索引各保留一条原有表格排版弱警告，未扩大范围修整旧表。

## 2026-10-13：Photon MCP 缺口接入与原生区域截图

- 用户确认仅移植需要且当前 MCP 缺失的能力，选择工程打开、资源操作、预览视角与扩展既有截图四项。参考上游 `26.2` 固定提交 `7b57fb4c3956526396d88b5d8eec13c2141c21d8`，通过当前 IDEA 解析适配 Minecraft 26.1.2／Photon 26.1.2.3／LDLib2 26.1.2.42；原作者授权与 Apache-2.0 原文纳入第三方声明。不引入 Groovy、独立服务器或重复编辑系统。
- 新增 `PhotonEffectResourceUtil`／`PhotonEffectViewUtil`，扩展 `PhotonEffectProjectUtil`／`PhotonEffectFileUtil`、Automation、客户端命令注册与 TypeScript 工具。原 8 项特效工具保留，新增 7 项：打开、资源列表／读取／写入、PNG 导入、视角读取／设置。只读与写入分开注册，延续控制模式、客户端线程、取消／提交、目录和覆盖边界。
- 工程文件只支持当前 `.fxproj`／`.mfxproj`，原生读取校验后开新标签，失败清理本次新增内容。资源采用完整原生 Tag 往返与临时文件发布，提交后更新缓存／资源面板，文件操作不属于 FX 撤销；PNG 限定命名空间纹理目录。相机复用原生 Scene，不修改资产版本或历史。
- 既有截图工具增加工程区域与等比缩小，按当前异步 Screenshot 回调读取真实 GPU 尺寸与像素；渲染线程不阻塞等待。新增 MCP 模块 `events.client.McpScreenshotEvents`，作为唯一 RenderFrameEvent.Post 入口，等待两次完整帧再发起抓取。局部截图核对指定工程的可见标签、嵌入宿主与布局，并按 Window 的 ceil 缩放规则处理右／下边界的取整像素；不能截取另一工程。旧截图尺寸问题的历史记录保留，真实运行效果尚待验收。
- IDEA 项目编译 success=true，主项目与 MCP 子项目的 compileJava 均完成；构建接口未提供错误／警告汇总计数，不据此声称全项目无警告。通过 IDEA 运行既有 Data 配置，exitCode=0、timedOut=false；25 份生成文件的集合与 SHA-256 均与运行前一致，未手工修改输出。
- 首轮 bridge 检查定位 `URLSearchParams` 参数的 `string[][]`／`[string,string][]` 推断不匹配，按 mapper 返回类型明确二元组后修正。最终 IDEA `Minecraft MCP Bridge` 退出码 0：TypeScript、ESM／CJS／声明构建成功，真实 stdio 工具发现 ok=true、toolCount=61，全部 15 项特效工具与读写元数据通过。
- 新增及修改区域 IDEA 无相关错误／新增警告；旧 ScreenshotHelper 35、McpHttpServer 9、McpMessageHandler 29、modClient 4 与探针脚本 4 项警告保留。Minecraft MCP 文档原有证据表的 1 项排版弱警告保留；仅对本轮扩充的特效工具表做局部排版。代码仅格式化修改范围，未格式化 JSON、许可证原文或生成资源；Git diff --check 退出码 0，既有 LF→CRLF 提醒保留。系统索引、接口文档、子项目说明与第三方声明同步。
- 修改前已读取 IDEA 文件信息、Git 差异及诊断；由 755 条 Git 状态、435 暂存／619 未暂存／3 未跟踪变为 760 条、440 暂存／627 未暂存／3 未跟踪，0 冲突。新增 5 份源码／第三方文件由 IDEA 自动登记，未手工暂存、提交、推送、重置或移除已有工作。未新增源码备份，原备份保留。
- 未启动或操纵游戏，未执行额外测试；工程打开、原生资源／纹理更新、预览相机与真实 GPU 截图的游戏内运行验收仍未完成。已连接的旧 stdio bridge 需重新连接才能加载本轮工具集合，没有声称当前对话的工具注册表已刷新。

## 2026-10-13：模型激光着色器方案与游戏内编辑器规则

- 用户要求激光改用 3D 模型、粉色为主，并明确多层表现由着色器实现；length／width／intensity／enabled 四变量保留。通过当前源码确认模型发射器已有 config.length／config.width／config.opacity／config.enabled 绑定与自定义着色器材质接口，方案复用单体积网格与单材质，不新增 Java 系统。模型及 GLSL 尚未修改，等待截图中“报错目录”与保存目录含义澄清。
- 阅读 Epic 的 [Fortnite GDC 官方讲义](https://media.gdcvault.com/gdc2018/presentations/Kladis_Autonomy_Real-time.pdf) 中单圆柱材质分层、Axis Aligned Fresnel 与时间轴控制说明，并定位参考视频署名作者的 [Laser Beam 分解页](https://danver_07.artstation.com/projects/kQyYBd)。B 站页面标题与署名已取得，视频画面读取多次超时，未将其记录为已完整观看。
- 经 IDEA 修改 AGENTS.md、Minecraft MCP 项目 Skill 与系统文档：游戏主窗先最大化，编辑器固定嵌入游戏；既有独立宿主通过 dockIntoGameWindow 切回，不再创建独立 OS 编辑器窗口。保留固定步骤合批、边界确认和失败诊断的快速流程。
- 截图对应资源目录已定位为 resources/ldlib2/assets/lobecorp/the_queen_of_hatred 与运行目录 run/client/ldlib2/assets/lobecorp/the_queen_of_hatred，运行目录已有 lasel.fxproj 等用户资源，未覆盖、迁移或删除。
- 本轮仅修改已明确的规则与记录，读取相关 IDEA 文件信息；未执行编译、Data、测试、游戏启动或效果保存，未创建新备份，保留现有 Git 工作区与索引。

## 2026-10-13：参考星星拖尾、变量激光与 Minecraft 快速流程

- 按用户选择制作两套原生 Photon FX：一条紫粉 Trail 加粉白／黄／蓝星点，三层激光加命名动画控制器。激光 `laser` 通过 length、width、intensity、enabled 四个实例参数驱动；width 是主体完整直径，enabled 隐藏而不中止时钟。没有修改女皇技能、命中框、伤害或同步。
- 新增两份集中配方与 `create-reference-effects.mjs`，通过已有 idea-ext 与子项目 Exec 任务暴露 Inspect／Create／Save。完整配方批量应用成功，星轨本次实际改变 59 项、激光 66 项；完整控制器 SNBT 的 4 参数／3 轨道／9 绑定已读回。6 份原生工程／定义／包实际保存于 `run/client/ldlib2/assets/reference-effects/`，定义与包警告均为空，真实路径／变量清单保存在生成诊断 `build/mcp/reference-effect-delivery.json`。
- 实际定位 UV／生命周期颜色模块启用的 INVALID_DATA：原生启用后补齐默认字段，旧接口要求整个包装完全一致。仅修改 `PhotonEffectEditUtil`，允许显式编辑的 /_enable 模块内部按原生展开，仍严格核对开关、外部数据、对象 UUID 与类型。重新编译／Data 后，6 个模块开关真实通过。
- 另已确认 Constant 默认整数 NBT 会拒绝合法浮点速度 0.12。脚本只为本轮两份新建参考工程准备等值原生 float Constant 模板，再走原生完整 SNBT 往返与批量编辑；没有全局放宽整数或修改第二处后端。失败诊断只在完整批次失败时自动二分定位，保持同一版本 dryRun、不提交或逐项重复读取状态。
- Client Debug 中四变量真实合成通过：主体长度 12→6、半宽 0.4→0.2、Alpha 1→0.35→0→0.35，恢复默认参数后问题列表为空。星轨弧线只加在运行时深拷贝，生产轨道仍为 0，生产 revision 与应用后相同；没有把演示路径保存到基础工程。
- 用户要求最大化并优化操作：更新 `AGENTS.md`、项目 Minecraft MCP Skill 与系统文档，明确游戏主窗和独立 Photon OS 窗分别最大化，固定步骤合批、关键边界统一检查、失败才展开诊断、新测试世界优先开发环境 Create Test World 入口。该入口仍需触发创建按钮；本轮未另建世界，继续使用已确认测试存档。实际 Windows IsZoomed 确认游戏和编辑器均 true；旧的主窗 GLFW 标志不能证明独立编辑器已最大化，已纠正。
- 原 MCP 截图得到 854×480 局部帧；原生 Window／framebuffer／RenderTarget 实际均为 2560×1494。已用原生 Screenshot 回调取得完整激光帧，没有扩展修改旧截图代码。IDEA Debug 恢复操作改用短超时，不固定等待默认 30 秒。
- 本轮后端修复 IDEA 编译成功，Data 退出码 0，25 份既有生成文件内容未变化。配方文件 IDEA 无错误／警告；编排脚本保留 4 项 SDK stdio 解析告警，真实 Inspect／Create／Save 均退出码 0。没有执行额外测试、性能、联机或战斗接入验收。
- 保留原有暂存、未暂存与未跟踪内容；IDEA 自动登记新增文件，没有手工暂存、提交、推送或重置。本轮未创建新的源码备份；修复前生成快照保留在 `build/mcp/before-module-toggle-fix/`，既有 MCP JAR 备份不变。收尾已取得完整星轨弧线截图 `build/mcp/reference-star-arc.png`，原生回调完成后重建两份基础运行态；两工程 dirty=false、revision 与保存前一致。MCP 已回执 control_mode=false，三个原用户断点与启用状态保留，临时断点为 0，全局静音关闭；编辑器保留打开。最终 Git 为 dev，750 条状态、430 暂存／615 未暂存／3 未跟踪、0 冲突。

## 2026-10-13：Minecraft MCP 源码子项目与 Photon 特效专用接口

- 按用户选择“专用 MCP 工具 + 源码复制”，将修复工程的 common、当前 NeoForge 26.1.2 模组、TypeScript bridge、共享 i18n 与原始图标纳入 `modules/minecraft-mcp`，保留基础提交 `50e059dccb09a9e23b91833ffdbf42efd97fa6e6`、四份本地源码修复和三份原许可证；原修复工作树、账户和个人配置不改动。根 settings/build 增加 `:minecraft-mcp`，共用 Java 25／NeoForge 26.1.2.100／ModDev，Client 加载源码，Data／Server 不加载 MCP 模组；Lobecorp compileOnly 引用，不内嵌 MCP JAR。具体来源与依赖见[子项目说明](../../modules/minecraft-mcp/README.md)。
- 新增通用 `McpExtensionRegistry` 与 HTTP JSON 扩展分派，不在 MCP common 引用 Photon；bridge 保留原 46 工具，新增 8 项 `effect_*`。Lobecorp 新增 `PhotonEffectAutomation`、异常码、5 个职责分离的 Util、注册入口与 Editor Accessor，既有接入只涉及 `LobecorpClientEvents` 和 Mixin 配置。复用原生工程、UUID、模型会话、时间轴、历史、保存与 FXPack 导出，支持参数／模型资产／播放／时间轴／原生 SNBT 批量编辑。
- 版本由实际工程内容 SHA-256 决定；副本校验后一次提交，只有正式批次实际改变内容才新增一次历史，dryRun 不写入。已知范围、原生枚举和 NumberFunction 注册名从锁定 API／配置器提取，未知范围／单位明确 null。写操作保持原控制门禁，客户端队列可取消阶段与提交阶段隔离；文件仅允许原生资源根内，默认拒绝覆盖，先临时编码再发布。FX 主时间轴播放可能改变持久变换，固定使用 pause → state → edit_batch。协议、8 类操作及限制见[Photon 特效 MCP](photon-effect-mcp.md)。
- 实际标题界面创建因原生场景缺失 dimension_type 注册表失败；已增加 WORLD_REQUIRED 前置检查，位于创建窗口／工程等副作用前。重新通过 IDEA 编译与 Data 后，在既有隔离世界 `新的世界 (5)` 运行真实 stdio → HTTP → 客户端检查：独立模型的编辑、dryRun、单次历史、版本冲突、undo／redo、四种预览、5.5 tick、保存后 clean、定义／包导出与覆盖／越界拒绝通过；补充 FX 中模型发射器 UUID、模型资产赋值、暂停保持、定位／停止、保存和定义导出，最终 29 项检查通过，模型 packWarnings=[]。输出目录及默认白模边界见接口文档，未以此宣称全部参数、着色器 GPU 外观、多人或性能验收。
- IDEA 编译成功，Data 退出码 0；生成目录 25 份文件的路径集合及内容均与本次执行前一致。IDEA Gradle 同步成功，`Minecraft MCP Bridge` 完成 TypeScript 检查、ESM／CJS／声明构建及真实 stdio 初始化／54 工具发现；只读运行配置确认协议 1、9 类发射器。语义引用确认注册只有既有客户端入口调用，HTTP 分派只有预期入口。新增 Java／接入类、工具 TS、配置与新接口文档无错误／警告；探针保留 4 项 SDK 通配导出解析警告，当前 exports／目标文件与实际 Node 运行均已核对，未隐藏警告。根 Gradle、第三方 HTTP 和旧文档的既有警告保留。
- 项目 `.codex/config.toml` 指向新 bridge，未改全局配置；旧连接需重连才能载入新增工具。通过现有 idea-ext 生成 Bridge／Effect Runtime／Effect Authoring／Import Assets 运行配置，未引入新 Gradle 插件。两 PNG 通过 IDEA 一次性迁移任务复制，正常构建不依赖原修复工作树。同步系统索引、MCP／编辑器／模型文档及项目操作技能；失败的临时运行配置 XML 保留于 `.backups/mcp-integration/`，旧 MCP JAR 已通过 IDEA 移至 `run/client/mcp-backups/2026-10-13-source-subproject/`，备份 JAR 元数据可读。
- 收尾确认三个本轮工程均 dirty=false，按原生 closeWindow 关闭后工程数为 0；随后 pauseGame(false)／resume 时客户端结束，日志记录 Stopping 与全部维度保存，原因未确认。未取得回到标题界面／显式退出控制模式回执，不能报告这些操作成功；原三个用户断点及启用状态保留，临时断点已移除。运行日志的 PassSource／PassSize 原生 serializer 警告仅记录，未扩展修改依赖。
- 保留原有 651 条 Git 工作区记录和其索引状态，无重置、覆盖、手工暂存、提交或推送；IDEA 自动登记新增文件。收尾 Git 为 dev，747 条状态、427 暂存／613 未暂存／3 未跟踪、0 冲突。本次新增文件的工作副本 EOF 空行已通过 IDEA 修正；本次范围工作副本 git diff --check 退出码 0，保留原 LF／CRLF 提示，暂存快照仍有原有／IDEA 初次登记版本的 EOF 空行，未手工更新索引。

## 2026-10-13：伤害基础字号简化为 0.7 至 2 倍

- `ScreenParticleRenderer.damageScale` 改为伤害比例 0% 对应 0.7 倍、100% 对应 2 倍的单段线性插值，100% 以上基础倍率保持 2；删除 10%／50% 独立节点及其常量。
- 保留原有 0.65 → 1.25 → 1 的弹出缩放，与基础倍率相乘，最高瞬时倍率为 2.5；震动仍按 200% 伤害比例封顶。世界锚点、合并、寿命和非伤害内容行为未改动。
- 同步[屏幕伤害粒子](screen-damage-particles.md)的当前规则，并明确原协议 19 运行记录中的分段字号采样属于调整前证据。
- 通过 IDEA 修改并仅格式化涉及的 Java 行，关闭导入优化与成员重排；渲染器和系统文档的 IDEA 诊断均无错误或警告。本次数值规则小调整未重新编译、执行 Data、测试或游戏验证。
- 保留已有工作区修改，未创建独立备份、手动暂存、提交或推送。

## 2026-10-13：伤害数字世界锚点、寿命内空间合并与生命比例动画

- 按用户确认，本人近战首次以屏幕中心确定固定世界显示点；实际命中坐标独立保留，供同目标、同攻击者、同伤害类型的空间合并使用。持续寿命内选取首次位置一格内最近实例，同键支持多个空间簇，不按后续命中接力移动合并中心。
- `DamageParticlePayload` 增加 `maxHealth` 快照，`DamageParticles` 读取受击者最大生命值，`LcPayloads` 协议从 18 升至 19。`ScreenParticles` 按累计伤害除以最近命中的生命上限计算比例，保留首次弹出／漂移年龄，每次合并续期并重新触发冲击；池回收清空显示锚点与比例状态。
- `DamageParticleConfig` 使用默认一格的 `mergeRadius` 替换六 tick 合并窗口；旧窗口配置不再参与判断。字号规则为比例 0／0.1／0.5／1 对应 0.5／1／2／3 倍，分段线性插值、三倍封顶；震动比例两倍封顶。相关接口与限制同步到[屏幕伤害粒子](screen-damage-particles.md)。
- IDEA 项目编译成功、Data 退出码 0；生成目录 25 份文件（含缓存）的路径集合及 SHA-256 完全不变。六个修改 Java 文件的 IDEA 诊断均无错误或警告；语义引用确认新 Payload 构造与 renderer 调用已适配，旧合并窗口源码引用为零。
- Debug + Minecraft MCP 确认：真实近战 100／最大生命 1000 的数字首次居中，转动 yaw 后固定世界锚点不变；受控通知验证寿命内跨 1679 tick 合并、1 格／1.001 格边界、多空间簇、来源／目标／类型隔离与到期池清理。分段字号八个采样点符合规则，同一渲染帧中 200% 与 300% 的缩放及震动完全相同，比例封顶生效。实际证据及受控通知边界见[伤害运行检查](screen-damage-particles.md#2026-10-13-协议-19-运行检查)。
- 世界文本补齐 DIRECT／左右逐字／奇偶中心展开、多行固定字位、单字三轴旋转与非均匀缩放、整体旋转和字号更新、左右视口外中心的范围裁剪、资源重载后对象与进度保留及移除检查。详见[文本补充验收](text-particles.md#2026-10-13-补充动态验收)；多客户端、上下边缘及性能基准未执行。
- 清理本轮测试牛与四个文本实例，恢复玩家进入测试前的位置、朝向和配置，保存退出到标题界面并关闭 MCP 控制；临时断点移除、原三个断点状态恢复。保留用户既有修改，未创建独立备份、暂存、提交或推送。
- 本轮共修改六个 Java 文件与四份系统文档；最终 IDEA 检查不新增错误或警告，README 保留既有表格弱警告，范围内 `git diff --check` 退出码 0。Git 保持 dev 分支、651 条状态、332 暂存／592 未暂存／3 未跟踪、0 冲突，索引状态未变。

## 2026-10-13：Minecraft MCP 项目技能、操作文档与粒子运行检查

- 按用户要求新增 [lobecorp-minecraft-mcp](../../.codex/skills/lobecorp-minecraft-mcp/SKILL.md) 与 [Minecraft MCP 文档](minecraft-mcp.md)，在系统索引增加入口。常规使用先 `ping` 握手，仅在失败、版本或桥接变化时重新排查；记录 Mod HTTP 与 bridge stdio 的分层、端口覆盖、截图 JSON 解析、控件枚举、Debug 路径格式、单执行者归属、临时断点释放及项目栈帧求值限制。
- 已通过 IDEA 核对本地 26.1.2 NeoForge Mod JAR、修复工程和 bridge；当前 46 项工具匹配。第二次 Client Debug 实际进入主菜单和隔离新世界，`ping` 得到 pong，控制模式和截图成功。按钮重复／空标签、坐标偏离和文本输入回显未生效的现象按实测记录，不把工具返回成功当成界面结果。
- 世界文本截图确认可见方向、黑色轮廓和 LEFT_TO_RIGHT 逐字中间状态；MutableComponent 后续追加不影响已存文本。相同 revision 的两次周期同步保留 Particle／layout 对象身份，playback 从 620 增长到 648。CENTER_OUT 的 17 字符中心排名通过 Debug 核对；完整单字动画和自动范围边缘尚未视觉验收。详见[文本粒子实测](text-particles.md#2026-10-13-debug-与-minecraft-mcp-实测)。
- 真实伤害链路中，generic 的 2、1 和当前玩家近战的 1 均到达服务端发包断点，生命 1000 → 996。第三次会话重做后，客户端恰有 amount=3／centered=false 与 amount=1／centered=true 两个实例，MCP 截图确认身体位置与中心上浮；显式 hurtAt 的 2.5 保留完整指定坐标，非文本 diamond 纹理可见。自然到期后的对象字段清空、同一池壳复用、单批正文／描边与跨帧缓存身份均通过 Debug 核对。
- 受控客户端通知验证其他攻击者 UUID 的近战、当前玩家的非近战均不居中；关闭合并后相同通知分别创建实例。该检查不等于多人联机。原版箭／MagicStar 精确命中、全部单字动画、边缘裁剪、资源重载、窗口与容量边界、多人与性能测量仍未完成，详见[屏幕伤害粒子实测](screen-damage-particles.md#投影与已知限制)。
- 本轮前两次 JBR 25 Debug 会话发生 native crash，报告为 `run/client/hs_err_pid27524.log` 与 `run/client/hs_err_pid43248.log`；后者在 Render thread 的 DirectMethodHandle$Holder.invokeStatic 发生 EXCEPTION_ACCESS_VIOLATION。重定义／DEOPT／调试事件与崩溃的时间邻近已记录，根因未确认；没有修改 JVM、桥接或第三方依赖来绕过。
- 第三次沿原配置成功完成上述检查；MCP 的 execute_command 返回 no command method found，Escape 输入也未生效，已写入技能／文档。已授权的替代路径使用 IDEA Debug 当前版本 API，服务端和客户端分别在正确线程操作；没有命令行控制游戏。
- 收尾恢复 lifetimeTicks=30、mergeEnabled=true，清空实例／池并删除本轮测试牛；MCP 保存退出后确认 TitleScreen，控制模式为 false。原有三个断点及启用状态恢复，本轮临时断点全部移除；客户端保留在标题界面，隔离世界 `新的世界 (5)` 保留。磁盘 CLIENT 配置已核对为默认值。
- 本次项目文件均通过 IDEA 编辑，仅新增技能／文档与维护索引、修改记录和两份粒子系统实测记录，没有新增业务代码修改。技能目录受 `.gitignore:48` 的 `/.codex/` 规则忽略，文件本地保留；未擅自改变忽略规则或手工暂存。未创建独立备份，保留已有工作区修改。
- 最终 6 份本轮文档／技能的 IDEA 检查无错误，README 仅保留既有表格弱警告；范围内 git diff --check 退出码 0，保留既有 LF／CRLF 提示。新技能已被项目技能目录识别，双向文档链接与索引链接已复核。Git 为 dev 分支 651 条状态、332 暂存／589 未暂存／3 未跟踪、0 冲突；新增系统文档由 IDEA 自动登记，未手动暂存、提交或推送。本轮文档工作没有额外运行编译、Data 或自动化测试，既有编译／Data 证据见下方记录。

## 2026-10-13：移除孤立语句并完成粒子系统编译与数据生成

- 用户明确选择仅删除 `MagicStarProjectile.tick()` 中孤立的 `Component` 行；通过 IDEA 删除该行，并仅格式化第 254–256 行，关闭导入优化与成员重排。保留其余用户修改及既有导入；该文件当前无错误，保留 Level 资源、NullMarked、枚举构造等原有警告，移除语句后原有 Component 导入成为未使用警告，按授权范围保留。
- IDEA 项目编译通过，返回 `isSuccess=true`、问题列表为空；原 `Data` 运行配置正常结束，退出码 0。这次结果同时补齐下方世界文本粒子和屏幕伤害粒子的编译证据；下方失败记录属于修复前历史。
- Data 前后通过 IDEA 读取并比较生成目录中的 25 份文件（含缓存），文件集合与内容均未变化；刷新生成目录后再次确认，没有手工编辑生成输出。同步[屏幕伤害粒子维护记录](screen-damage-particles.md#编译检查与维护)。
- IDEA Git 状态仍为 dev 分支 650 条状态、331 暂存／588 未暂存／3 未跟踪、0 冲突；本次前后路径及索引状态不变。未手动暂存、提交、推送、重置或创建独立备份。
- 未运行测试、游戏、专用服务器、多人与性能验收；投影精度、实际合并表现和第三方数字同时显示的限制仍保留。

## 2026-10-13：屏幕伤害粒子、位置投影与可配置合并

以下检查结果为孤立语句修复前的首轮记录，后续通过结果见上方。

- 新增服务端 `DamageParticles`／`DamageParticleHitContext` 和一次性 `DamageParticlePayload`；复用唯一 `LivingDamageEvent.Post` 入口，只广播有限正 `healthDamage`，不含吸收、包含过量伤害，接收范围为发生点周围同维度 32 格。保留任意实体攻击者 UUID；统一网络版本由 17 升至 18，2 项 C2S／14 项 S2C。
- 普通伤害固定首次世界锚点并逐帧投影到 GUI；只有攻击者 UUID 等于当前客户端玩家、且明确为近战时使用该玩家屏幕中心。其他玩家的近战仍显示在世界发生点。投射物作用域保留碰撞点，MagicStar 移动单目标命中显式传入坐标；玩家普通攻击、刺击和横扫包装实际伤害调用，绑定目标与 DamageSource，避免附魔追加伤害继承近战。普通近战只能估算视线与 AABB 交点，无数据时身体中心回退，不宣称取得模型表面命中点。
- 新增 `IScreenParticleContent`、Component 深快照文本和原生纹理适配器，客户端 `ScreenParticles` 管理私有对象池与寿命。合并采用已确认的同攻击者／同目标／同类型／同显示模式滑动窗口，默认 6 tick；即时累计并续期默认 30 tick，保留第一次锚点与运动年龄。活跃和空闲容量各默认 256，关闭合并、窗口过期、缩容、禁用及世界切换均处理索引和引用释放；F1 隐藏绘制，暂停不推进客户端时钟。
- 文本复用 `TextGlyphMesh.Capture/createOutline`，缓存正文及同字形八方向描边，按 GUI pipeline／纹理聚合；常见数字正文和轮廓同批，不使用每数字 `nextStratum`。字体、语言、字体选项、资源重载和动态材质变化维护缓存；提取状态冻结矩阵和稳定网格，不捕获池实例。新 CLIENT 配置在 Mod 构造注册，默认 `config/lobecorp-client.toml`；没有新增依赖、手写生成资源或更改第三方伤害数字配置。
- 修改范围为 18 份 Java（新增 12 份）、Mixin JSON 和 8 份系统文档。既有接入仅涉及 `Lobecorp`、`LcPayloads`、`LivingEntityEvents`、`ClientRuntimeEvents`、`LobecorpClientEvents` 与 `MagicStarProjectile`；新增 Mixin 位于 `mixin.particle`。系统文档含配置、调用示例、位置来源、池、投影限制与重复第三方数字风险，入口见[屏幕伤害粒子](screen-damage-particles.md)。
- 全部项目编辑与 Java 修改行格式化均通过 IDEA，关闭导入优化和成员重排。12 份新增 Java 最终诊断无错误／警告，4 份 root 接入类无问题；既有 LivingEntityEvents 的 3 条 Level 资源警告保留，MagicStar 原有语法错误和警告保留。Mixin JSON 无问题，Player 的三个伤害调用通过当前依赖字节码核对目标 owner／descriptor，Projectile 包装方法通过当前依赖源码核对。IDEA 语义引用确认 Post、Payload 接收和 GUI 提取均只有预期业务调用；范围内 git diff --check 退出码 0，仅保留既有 LF／CRLF 策略提示。8 份文档无错误，新文档无警告；索引、网络、事件文档各保留一条既有表格弱警告。
- IDEA 项目编译失败；Data 原配置退出码 1，在 compileJava 阶段被 `MagicStarProjectile.java` 第 255 行孤立的 `Component` 阻断，尚未进入数据生成。该语法错误在修改前已经存在，不属于此次位置调用修改，未删除或猜测其意图。刷新生成目录后读取 25 份文件（含缓存），集合与内容均和执行前一致；没有手工编辑生成输出。新增代码尚未取得编译通过证据。
- 保留原有 Git 工作；收尾检查 dev 分支 650 条状态、331 暂存／588 未暂存／3 未跟踪、0 冲突；原有路径均保留，原有索引状态均未改变。13 份新增文件由 IDEA 自动登记；本轮未手动暂存、提交、推送、重置或创建独立备份。管理器末轮协作调整了检查方法名称，一处基于旧快照的补丁未匹配且未应用；核对归属和最新内容后仅追加 F1 门控，未覆盖暂停与部分刻修正。
- 未启动或重启游戏，未运行测试、HotSwap、专用服务器或多人验收；未测量帧率、CPU 分配或 GPU 时间。相机公开投影不包含受伤／视角摇晃及传送门／恶心额外变形，GUI 不使用世界深度遮挡；旧 Re-Retro Damage Indicators 的数字配置仍开启，可能同时显示两套数字，未擅自关闭。

## 2026-10-13：世界空间文本粒子与缓存描边

以下检查结果为孤立语句修复前的该轮记录，后续通过结果见上方。

- 新增 `particle.text` 的服务端实例、Level 集合、管理入口及 Component／关键帧快照；服务端不依赖客户端 Particle 类，不注册实体、不写世界存档。传入、读取和发送文本均复制完整 Component 树；外部 MutableComponent 的继续修改须再次 setText。
- 支持整体 XYZ 旋转和世界字号，直接／左右方向／中心向外显露，单字 XYZ 旋转与缩放关键帧及索引覆盖，没有单字位移。完整排版保持固定字位，自动范围覆盖描边、装饰及全部关键帧最大缩放和任意旋转；翻译采用用户确认的客户端本地语言。
- 新增 `TextParticleStatePayload`，网络版本 16 → 17；修改立即发、移除立即发，每 20 tick 同 Level 完整补发。客户端按 UUID／revision 幂等安装，周期包不重建或重播，逐字和动画由客户端 tick 推进；加入前配置不发包，无玩家时不构造传输快照。
- 新增客户端文本 Particle、布局／网格缓存、自定义 ParticleGroup 与 registry 登记。普通字形及八方向一像素轮廓缓存局部顶点，按字体材质跨实例合批；不逐字创建 TextSubmit。混淆字按 tick 局部刷新，正文和描边共用一次字形选择，避免独立随机选择导致轮廓错配。动态玩家字体材质按帧解析；资源重载在字体及粒子引擎清空后失效并恢复现存实例。
- 本轮涉及 19 份 Java（新增 14 份）：公共数据位于 `particle/text`，客户端实现位于 `client/particle/text`，载荷位于 `network/tc/text`，客户端登记位于 `registry/particle/client`；既有接入仅修改 `LcAttachmentTypes`、`LcPayloads`、`WorldTickEvents`、`ClientRuntimeEvents`、`LobecorpClientEvents`。同步本记录、系统索引、文本粒子新文档及粒子／网络／事件／注册文档。
- IDEA 已对新代码与既有文件本次涉及行格式化，关闭导入优化和成员重排。当前文本源码诊断无错误，保留 4 个暂未被业务调用的公共配置 API 未使用警告；既有文档的表格弱警告保留。详见[文本粒子接口与示例](text-particles.md)。
- 实际执行 IDEA 项目编译及 `Data`：两者均被本次未修改的 `MagicStarProjectile.java:254-255` 孤立 `Component` 语句触发的两条“需要 ';'”错误阻断；Data 退出码 1，停在 compileJava，未进入数据生成阶段，不能报告编译或生成通过。本轮不扩大范围修复该文件。
- 未运行测试、游戏、多人与资源重载交互验收，也未测量帧率或 GPU 性能；描边仍保留八方向几何量，只确认 CPU 缓存与合批机制。保留原有暂存、未暂存和未跟踪修改；IDEA 自动将 15 个新增文件加入暂存，本轮没有手动暂存、提交、推送、重置或创建备份。

## 2026-10-13：粒子特效触发模型动画

- 用户确认同时支持 FX 时间轴／命名动画与粒子出生、死亡、碰撞事件；粒子事件选择方案 1，每次创建新模型 FX 实例并播放默认动画。复用原生 SubEmittersSetting／FXHelper 资源链，没有新增事件总线、网络协议、依赖或已有模型实例切换逻辑。
- 本轮修改 15 份 Java，其中新增 `PhotonModelAnimationClip`、`PhotonModelAnimationTrack`、`PhotonModelAnimationTrackEditor`、`PhotonModelAnimationTimelineUtil`、`PhotonModelPlaybackConfig`；既有修改涉及 `PhotonModelEmitter`、`LcPhotonModelEmitterTypes`、`TimelinePlayerMixin`、`PhotonAnimationNativePlayer`、`PhotonAnimationTargetPickerMixin`、`PhotonAnimationTimelineMixin`、`PhotonModelTextUtil`、`PhotonModelProject`、`PhotonModelEditor`、`PhotonModelExportUtil`。同步模型系统、命名动画、系统索引及本记录。
- 新“模型动画”轨道只绑定模型发射器，选择资产动画并配置倍率、偏移、循环和片段结束行为。原生时钟控制暂停、定位和重播；保持末帧按固定采样时间，循环整周期末保留末帧。单轨道按最近开始片段选择，恢复默认后不重新激活更早的保持片段；同播放器后轨覆盖前轨，多命名播放按优先级／启动顺序，主时间轴作为基础。整段动画不做骨骼混合，数值属性继续由原 Mixer 处理。
- 模型采样覆盖通过弱引用关联原生播放器，begin／stop／close 以及模型 reset／强清空释放临时贡献。新轨道沿原生 Extra NBT、copy／restoreFrom 与目标 UUID 重映射，不创建第二份时钟，不负责模型生成或寿命延长。
- 已确认原模型定义导出仅复制资产，默认动画等播放配置丢失。本轮将启用、持续时间、延迟、实例循环、暂停、默认片段、倍率和偏移独立保存到工程 playback 节点，在预览强制配置之前捕获，并统一应用到来源发射器与定义／模型包导出；定义导入保留完整播放配置。保存允许保留已失效片段引用以便重开修复，应用与导出明确拒绝；不增加旧工程缺失节点的兼容分支。
- 源码审查修复本轮新增 Selector 标签 provider 初始化空值问题、数字输入时重建检视导致失焦、捕获被替换配置对象、独立导入错误显示来源应用按钮，以及循环保持末帧的整周期边界。数字输入初次变更只登记历史，撤销／重做刷新检视；supplier 动态读取配置。既有 `enumSelector` 仍有未处理初始化空值的源码风险，本轮未运行该交互，也未扩大修改范围。
- 全部项目编辑通过 IDEA；Java 修改范围已格式化，关闭导入优化与成员重排，编辑后复读确认完整。15 份 Java 最后逐文件诊断均 `errors=[]`；相关文档无错误，模型系统与索引各保留 1 条既有表格弱警告。反射注册与标准 Mixin 自转换的静态假阳性按已有方式作局部说明。IDEA 编译 `isSuccess=true`、`problems=[]`；Data 原配置退出码 0。
- Data 后通过 IDEA 刷新生成目录并逐文件比较 25 份输出，文件集合不变；en_us／zh_cn 各新增 11 键，无删除及既有文案变化，两份对应缓存变化，其余输出相同。未手工修改生成输出。IDEA 语义引用核对定义读取仅由新导入构造消费，应用与导出共用 applyPlayback；目标选择／绑定均限制模型轨道对象类型。
- 保留已有 Git 工作，dev 分支收尾检查为 622 条状态、303 暂存／570 未暂存、3 未跟踪、0 冲突。两份新增配置／工具由 IDEA 自动登记，三份轨道类未跟踪；未手动暂存、提交、推送、重置或创建独立备份文件，修改前快照保留在本次会话内。范围外已有工作未处理。
- 未运行 JUnit、Client、HotSwap 或游戏内事件／视觉验收。时间轴暂停定位、连续输入、保存重开、导出往返和粒子出生／死亡／碰撞的实际表现仍待客户端验收；本轮编译与 Data 不代表这些交互已成功。使用入口与维护细节见[模型动画触发](photon-model-effects.md#由粒子特效触发模型动画2026-10-13)。

## 2026-10-13：模型工程原生着色器预览、存储与应用

- 用户选择方案 1：复用锁定 Photon 的 ShaderGraph／CustomShader，模型工程提供新建着色器槽、选入／替换库材质、原生实时预览与参数控件，以及按面分配。参数修改进入模型会话撤销／重做，保存与“应用到发射器”沿用现有工程与来源有效性门禁；没有新增 GLSL 源码编辑器。
- 修改 9 份 Java：`PhotonModelAsset`、新增 `PhotonModelMaterialDataUtil`、`PhotonModelValidationUtil`、`PhotonModelMaterialUtil`、`PhotonModelPreviewMaterialUtil`、`PhotonModelResourcesView`、`PhotonModelTextUtil`、`PhotonModelEmitterConfig`、`PhotonModelProject`。同步本记录、模型系统文档与系统索引；未修改网络协议、依赖版本或运行配置。
- 已确认旧面板只接受 TextureMaterial 且资产没有着色器参数存储。本轮增加 `materialData` 完整原生 CODEC 快照，UIResourceMaterial 先解除库引用后复制参数，运行时按资产槽缓存独立原生材质。渲染复用来源混合／深度管线及部件双面开关，预览副本启用基础面深度写入；UV 仅读取实际纹理材质。
- 工程资源增加原生 ShaderGraphResource 与 PhotonShaderFunctionGraphResource。发射器派生 `assetMaterialDependencies` 结构化 wrapper，供原生 FXPack 收集、改写路径及 GC 扫描；载入回填改写后的快照。SNBT 与 `type/data` 结构校验避免坏派生 wrapper 覆盖原始资产 JSON；通用配置层不加载客户端材质注册。原 PNG 派生依赖保持原行为。
- 全部项目编辑通过 IDEA，Java 修改范围已格式化，关闭导入优化／成员重排。最后逐文件检查 9 份 Java 均 `errors=[]` 且无超时；模型系统文档及索引各保留 1 条既有表格弱警告，新研究表格已按范围格式化。语义引用确认运行时 rendererFor 消费更新后的材质方法；源码不再引用已移除的 `resources.texture_only`。
- 最终 IDEA 编译 `isSuccess=true`、`problems=[]`；Data 原配置退出码 0。刷新生成目录 VFS 后逐文件比对 25 份输出，文件集合不变，仅 en_us／zh_cn 各新增 7 键、删除 texture_only，调整本轮库材质与资源说明文案，以及两份对应缓存变化；其余输出内容相同，没有手工编辑生成输出。
- 并行查阅 Effekseer、Unity ShaderGraph／VFX Graph、Unreal Material Editor／Niagara、EmberGen 官方文档，功能对比与来源链接见[模型着色器使用与维护](photon-model-effects.md#模型着色器使用与维护2026-10-13)。图资源与源文件仍为共享引用；原生 CustomShader 打包仅收集 JSON 及直接 vsh／fsh，不递归收集 GLSL include，未扩大修改原生打包器。
- 首次 IDEA 补丁写入资源面板遇 Windows 映射文件写入失败，并发生尾部截断；已通过 IDEA Document API 按修改前快照恢复原尾，再逐段格式化与复读确认文件完整。本轮未创建独立备份文件。保留已有 Git 工作，dev 分支最终检查为 617 条状态、301 暂存／568 未暂存、0 冲突；新增工具文件由 IDEA 自动登记。另发现范围外 `.codex/config.toml` 变动，未修改或回退；未手动暂存、提交、推送或重置。
- 未运行 JUnit、Client、HotSwap 或 GPU／交互验收；材质选择、连续参数输入、保存重开、应用和导出包往返目前为源码／静态链路检查，不把编译与 Data 当作运行成功。Data 返回的完整临时日志不在 IDEA 可读根内，未绕过 IDE 读取；成功依据为运行配置实际退出码。

## 2026-10-12：长过渡连续旋转与剩余问题检查

- 修复长淡入或移动源／目标跨 180° 时逐帧独立最短路径翻支：新增 `LcRotationTransitionUtil`，连续选择相对旋转向量的等价分支，多轴整圈奇点使用有界修正重新建基，保持权重端点和当前原版基底。状态不可变且长度固定；`NORMAL` 仍保留 Euler 数值路径，不改写资源圈数、显式 seek 或代码绝对跳变。
- Gecko 控制器动画切换与层混合、玩家模型贡献及独立物品显示接入独立旋转历史。玩家评估返回下一份不可变贡献，由桥接器按新渲染帧提交；同帧重复渲染不推进历史。物品历史按绑定保存，网络描述更新不丢失；播放修订、资源版本、阶段／模式变更和既有移除入口负责重置或清理。已核对软停止／暂停不增加播放修订号，停止仅重置淡出历史，保留淡入分支。
- 本轮修改 8 个主源码文件：`LcAnimationPose`、`LcAnimationControllerRuntime`、`LcAnimationControllerIntegration`、`LcRotationTransitionUtil`、`LcPlayerModelPose`、`LcPlayerPoseContribution`、`LcPlayerPoseBridge`、`LcAnimationDisplays`；新增 `LcRotationTransitionRegression` 与 `LcPlayerRotationRegression` 两个回归 main，同步动画控制器、动画运行时、玩家动画、附着、系统索引及本记录。全部项目编辑通过 IDEA，Java 修改范围已格式化并关闭导入优化／成员重排；没有修改资源、网络协议或生成输出。
- 首轮回归暴露接近 90° 的浮点 Euler 分解误差，分别约 `1.18224E-4`／`1.00553E-4` 弧度；共享分解改用 `Quaterniond` 后再选 Euler 分支和转回 float，玩家输出复用该入口，未放宽 `1E-4` 断言。最终 IDEA 编译 `isSuccess=true`、`problems=[]`，两个 main 退出码均为 0，覆盖跨半圈、多圈、移动基底、混合／顺序阶段、冻结、状态纯度与奇点重建等场景；未运行 JUnit。
- 两个独立回归分别用新 IDEA Debug 会话和预置无条件断点暂停，读取当前栈后正常恢复并停止，`breakpointErrors=[]`；玩家移动基底用例实测 `right_arm.yRot=1.308997`，约 75°。Data 配置退出码 0；IDEA VFS 刷新后 25 份生成文件（含缓存）的集合与逐文件文本均和执行前相同。相关语义引用已核对，没有遗留旧调用。
- 最终 IDEA 逐文件复查 10 份 Java 无错误或新增警告；保留 17 条既有警告（15 条 Gecko Internal API、2 条未使用显示入口）以及两个回归 main 的 `System.out.println` 日志点建议。6 份系统文档无错误，索引与动画运行时各保留 1 条既有表格弱警告；没有扩大为无关诊断清理。
- 原 Client 配置新建 Debug 后通过 Minecraft MCP 进入已有世界，服务端线程实际调用 Brigadier 登记 80 tick 淡入／淡出、`OVERLAP`／`SHORTEST_PATH` 的钻石剑动画 `87852fef-a505-431f-9777-7fb458e8c5ad`，绑定 `632d2548-c4b2-4b25-baf0-f3cfad219363`。客户端显示与物品旋转历史各为 1；命令软停止并到期后，服务端播放会话、客户端显示及物品旋转历史均为 0。实际查看无动画时原版第一人称手臂画面；未取得长过渡逐帧画面，不将登记／清理证据当作跨角度视觉验收。MCP `open_chat` 返回 `ChatScreen instantiation failed`，后续文字输入没有执行命令，命令成功证据来自真实服务端 dispatcher 及运行态。
- 审计 6 份同签名 native 崩溃日志，其中 4 份早于近期玩家修复。两份当日日志在 `ClientRuntimeEvents` 重定义后紧邻 Render thread 的 `DEOPT → ChangeSingleStep` 及 JBR 25.0.4.1 `DirectMethodHandle$Holder.invokeStatic` 访问冲突；只能确定高度相关，不能确定 JVM 根因。本轮采用编译完成后新建 Debug、不 HotSwap、不对该类设置条件／日志断点的路径，未复现崩溃；未修改 JDK、IDE 设置、运行参数或第三方模组，不声称原生崩溃已修复。
- 本轮游戏日志读取有 1 条 JEI 配方登记耗时 5.009 秒的 ERROR，没有 FATAL；Debug 暂停的追帧 WARN 不作为性能证据。测试对象已清理，日志开关保持 false，全部代理断点已移除，仅保留两个原有禁用用户异常断点；退出 MCP 控制模式，Client Debug 保持 running。未主动移动玩家或修改背包，未验收多人、专用服务器、全部姿态或第三方渲染组合。
- 保留已有 Git 工作，dev 分支 615 条状态、300 暂存／566 未暂存、0 冲突；IDEA 自动登记 3 份新 Java 文件，本轮未手动暂存、提交、推送、重置或创建备份。最终编辑器诊断与检查边界以本条及相关系统文档为准。

## 2026-10-12：第一人称显式显示与循环旋转跳变修正

- 按用户截图反馈收紧第一人称接管条件：`LcPlayerAnimationPolicy.firstPersonArms` 默认 false，必须由当前视觉动画层显式 `withFirstPersonArms(true)` 且策略权重大于 0 才打开自定义手臂作用域。其他 builder 保留该标志；玩家示例显式开启。无动画或没有声明时完整回退原版每手／持物路径；软停止沿既有视觉淡出尾帧保留接管，到期后恢复原版。没有新增同步通道，既有状态 Payload 的玩家策略段增加该布尔值，协议 15 升级为 16。
- 通过 IDEA Debug 确认默认物品循环的跳变根因：`example/item_pose` 没有客户端覆盖，`visualClip=null`；1.95→2.00 秒快照的 Euler Y 从约 −351° 回零，旧半刻插值为 −175.5°，与正确旋转相差约 180°。`LcClientAnimations` 在同播放修订跨循环时，按派生资源的取模时间重新采样旋转；代码声明拥有的骨骼仍保留同步结果，位移／缩放保持原快照插值。显式播放修订直接采用当前姿态与世界变换。`LcClientAnimationResources.contentClip` 按内容版本和动画名缓存，完整 clear 时清理。
- 本轮 Java 修改为 `LcPlayerAnimationPolicy`、`LcPlayerFirstPersonRenderer`、`LcPlayerAnimationDemo`、`LcAnimationStatePayload`、`LcPayloads`、`LcClientAnimations`、`LcClientAnimationResources`；同步玩家动画、动画运行时、通用附着、网络、系统索引及本记录。全部项目编辑通过 IDEA，Java 修改范围已格式化且关闭导入优化／成员重排；没有资源内容改动或手工编辑生成输出。
- IDEA 最终检查 7 份 Java 无错误或新增警告，保留 6 条既有未使用 API／枚举可见性提示；6 份系统文档无错误，索引／动画运行时／网络各保留一条表格弱警告。项目编译 `isSuccess=true`、`problems=[]`。Data 配置退出码 0；VFS 刷新后 25 份生成文件（含缓存）与执行前读取文本完全相同，文件集合无新增／删除。策略构造、builder 与 Payload 读写的语义引用已核对，没有过时构造调用；未运行 JUnit。
- Minecraft MCP + IDEA Debug 第一轮实测无动画 `frames=0/open=null`，[原版第一人称截图](../../run/client/screenshots/player-first-person-gated-vanilla-20261012.png) 已实际查看。同一暂停玩家动画 `cf73d0d1-2ed2-491f-abb9-62f7b2e77918` 声明 false 时 `open=null`；改为 true 后 `scope=true/activeDuring=true`，关闭 scope 后 active=false，[显式自定义姿态截图](../../run/client/screenshots/player-first-person-gated-active-20261012.png) 已实际查看。
- 第二轮原配置重启后，物品实例 `25dd4fa5-5e1e-4289-81dd-8ee637a13091` 的实际跨圈快照为 3.999999999999994→4.049999999999994 秒。旧半刻 Y=−184.500005°，修复后为 −4.500000125°，与资源期望一致，四元数角误差 0；positionPreserved／scalePreserved=true、overrides=0、sameRevision=true。代码拥有骨骼的跳过分支仅做源码检查，没有把本例当作该分支的运行证据。
- 服务端直接停止玩家 `519f03d8-dd39-4afe-8ea4-51d121fa36b9` 和物品后，客户端实测 `frames=0/pending=0/openNull=true/active=false`，[停止后的原版截图](../../run/client/screenshots/player-first-person-gated-stopped-20261012.png) 已实际查看；服务端动画／物品／附着均为 0。没有取得本轮淡出中间权重，不将到期回退当作中间淡出验收。日志开关均为 false，代理断点全部移除，仅保留两个原有禁用用户异常断点；Client Debug 恢复 running，退出 MCP 控制模式。
- 首轮恢复执行时 JVM 意外退出，`run/client/hs_err_pid36000.log` 确认 18:11:44 Render thread 的 `EXCEPTION_ACCESS_VIOLATION`，问题帧为 JBR 25.0.4.1 `DirectMethodHandle$Holder.invokeStatic`；触发原因未确定，没有修改 JVM／第三方模组／运行配置或声称修复。第二轮日志有 6 条 JEI 配方登记耗时超过一分钟的 ERROR，以及 Debug 追帧／环境 WARN；未扩大为第三方修复，也不作为正常性能证据。
- 玩家位置 (55.936713553182834,56,204.9339730493009)、yaw=128.94997、pitch=63.89987、FIRST_PERSON、hideGui=false，主手 AIR／副手 1 个 sandstone 保持本轮基线，没有移动或修改背包。Git 仍为 dev、612 条状态、297 暂存／564 未暂存、0 冲突；未手动暂存、提交、推送、重置或创建备份。长淡入期间运动目标跨 180° 的最短路径翻支风险仍保留，资源自身不连续与代码显式跳变也不被此修复改写；未验收多人、专用服务器、全部物品与着色器组合。

## 2026-10-12：通用附着、独立物品动画与命令参数

- 用户确认 A：附着是世界、实体、动画和渲染共用的系统，客户端也能直接创建；采用新 API／命令，不保留旧格式与命令别名。将目标、TRS、绑定及实体／Photon 适配迁到通用 attachment 入口，动画仅保留 LcAnimationAnchor 适配。通过 IDEA 语义迁移更新引用，保留已有暂存／未暂存修改；入口与生命周期见[通用空间附着](attachments.md)。
- 新增 LcAttachments、来源引用与显式渲染来源。直接绑定不依赖动画；instance.attach 仅增加播放结束时的释放归属。根运动共用目标身份写入权，跨 Level、失效来源和不可逆切锚均有边界。通用绑定只写位置，不创建／删除对象或停止目标动画；服务器与集成客户端共享的核心及物品登记入口采用同步访问。
- 协议升级为 15：LcAnimationSnapshot 不再携带附着，独立 LcAttachmentStatePayload 发送绑定、切锚修订、视觉偏移及物品描述；完整内容与动画状态先于附着发送。服务器镜像与客户端本地绑定分区，网络 full/remove 不释放本地绑定。沿既有唯一 tick／卸载／停止入口维护，不新增平行事件或重复空间推进。
- 最终玩家 back／left_hand／right_hand 矩阵接入第三人称层及第一人称双臂路径；公开 provider 与 display Frame 通道供其他渲染器使用。逻辑实体偏移与 renderOffset 分开，避免实体到手的位移再叠到最终手节点；同帧旧、新来源可解析时按绑定、视图及修订缓存矩阵校正。提供器缺席时回退权威世界结果，修正渲染转世界可能无限隐藏的路径；不保证任意缺席来源或 HUD／世界投影之间无跳变。
- 新增 LcAttachedItems、LcItemAnimationSession 和 example/item_pose 的循环局部 TRS；整体物品动画使用独立播放实例，内部 GeoItem 骨骼继续沿 GeckoLib。进出过渡使用完整配置时长；SEQUENTIAL 替换延后新显示开始，并同时冻结新物品与父附着时钟；软停止冻结姿态，按世界时间淡出，不受暂停或 speed=0 阻塞。空物品在命令、登记及快照入口拒绝。
- 统一命令为 /lobecorp animation player|attachment|item，支持玩家选择器、独立速度、进出时长、OVERLAP／SEQUENTIAL、旋转过渡及暂停／恢复／停止；attachment 另支持 BACK／HAND／WORLD 切锚和物品自身控制。玩家 dynamic 时间点只改来源，保留命令指定过渡。移除旧 DemoClient 和旧命令别名，实际语法见[动画调试命令](animation-runtime.md#动画调试命令)。
- 新增默认关闭的 LcAttachmentDebugUtil.ENABLED，仅开发环境记录创建、切锚、视觉来源及释放边沿。全部项目编辑通过 IDEA，代码修改范围已格式化并关闭导入优化／成员重排；资源保持原排版。IDEA 编译 isSuccess=true、problems=[]；Data 退出码 0。经 VFS 刷新比对 25 份生成文件，只有 en_us／zh_cn 各新增 commands.lobecorp.animation.empty_item 及两份对应缓存时间／哈希变化，文件集合不变；未手工编辑生成输出。
- Minecraft MCP + IDEA Debug 实测客户端无动画绑定 f2bd5950-083e-441b-a692-75d6f18d34e8：本地绑定／物品各 1、动画 0，接收空 full 后仍为 1；[本地世界物品截图](../../run/client/screenshots/attachment-local-20261012.png) 已实际查看。[本地第一人称手部截图](../../run/client/screenshots/attachment-local-hand-20261012.png) 确认无自身动画的本地显示可使用最终手节点。两个本地测试对象均已释放。
- 通过服务端线程的真实 Brigadier 命令树执行参数验证：AIR 返回中文错误，独立世界物品采用 speed=0.5、12 tick、SEQUENTIAL；[世界物品截图](../../run/client/screenshots/item-animation-world-20261012.png) 已查看。替换瞬间实测旧物品 paused、新物品 time=0／paused，startedTick=当前 tick+12；之后客户端只余新播放。speed=0／暂停后的 stop 正常完成淡出清理。Minecraft MCP execute_command 在当前版本返回 no command method found，因此命令由 Debug 调用同一服务端 dispatcher，没有把 MCP 返回错误报告为成功。
- 真实玩家附着 44e69e40-728c-41f0-af94-f97877b67505 手动 BACK→HAND→WORLD 保留绑定 UUID，父时间线 paused／speed=0 时自身动画继续并可独立改为 0.25；[背部截图](../../run/client/screenshots/attachment-back-20261012.png)和[世界路线截图](../../run/client/screenshots/attachment-world-20261012.png)均已实际查看。新自然播放 fdc60dfe-0041-4819-98d9-00f903b9795c 的绑定 b106633e-07a7-4656-b966-c99c34acb31b 记录 1.5 秒手部切换、3 秒世界切换、6 秒结束，随后自身物品淡出并释放。
- 玩家 dynamic 实例 1047566b-e8a6-420b-8067-f8cb32bb76e0 在 1.5 秒变更 vanilla_idle 时，服务端和客户端均保留 12 tick／SEQUENTIAL；模型日志实测淡入 0.3541666667、来源变更后 0.1875、结束淡出 0.1875。收尾 Debug 实测重复写入与奇异切锚均拒绝且 revision 不变；本地绑定／描述、附着帧、客户端播放以及服务端绑定／描述／播放均为 0。
- 最终日志开关均恢复 false，移除全部代理断点，仅保留两个原有禁用用户异常断点，Client Debug 恢复 running 并退出 MCP 控制模式。位置 (70.54768729371958,56,236.6327550873684)、yaw=0.9999179、pitch=31.200022、FIRST_PERSON、hideGui=false，主手 1 个 evoker_spawn_egg／副手 1 个 sandstone 保持本轮基线；未修改库存或移动玩家。第二次启动完整日志读取无 ERROR／FATAL，暂停引起的追帧 WARN 不作为性能证据。
- 首次 Client 进入世界后 JVM 退出，run/client/hs_err_pid32300.log 记录 16:19:17 Render thread 的 EXCEPTION_ACCESS_VIOLATION，问题帧为 DirectMethodHandle$Holder.invokeStatic；尚未确认触发原因，没有归因于本功能或声称修复。原配置重启后完成以上检查。未验收专用服务器、多人晚加入／失去追踪、全部姿态、全部特殊物品和着色器组合；未运行 JUnit，未修改第三方模组或运行配置，未新增手工备份、暂存、提交、推送或重置。
- 最终分批检查 51 份相关 Java，无错误、超时或未分析文件；保留 26 条未使用公开 API／翻译登记、Level 资源检查和冗余初始化／枚举可见性提示。7 份系统文档无 ERROR，索引、动画、网络与事件文档各保留一条表格格式弱警告；历史示例链接更新到现命令入口。最终 Git 为 dev、612 条状态、297 暂存／564 未暂存、0 冲突，已有工作及备份保留。

## 2026-10-12：玩家动画综合复验与后台调试边界

- 按用户要求重新综合验证，未修改 Java、Mixin 配置、依赖或运行配置。14 份相关 Java 的 IDEA 检查为 0 ERROR、6 条既有未使用 API 警告；第一人称渲染器、Mixin／Accessor、姿态桥接等核心文件及 Mixin JSON 无问题。重新核对来源混合、过渡、独立模型、相机矩阵、延迟提交及原版／NeoForge／Iris 入口。
- IDEA 新一轮编译 `isSuccess=true`、`problems=[]`；Data 退出码 0。经 IDEA VFS 刷新后，25 份生成文件（含缓存）与执行前读取文本一致，文件集合无新增／删除；没有手工修改生成输出。随后通过 IDEA 启动新的 Client Debug 并进入既有单人世界，运行期间未编译或热替换。
- 本轮基线为 SLIM、creative，位置 (62.20273619533319, 56, 182.02224929062757)，FIRST_PERSON，yaw=-24.650059／pitch=3.150028；主手 1 个 barrier、副手 1 个 sandstone，四个盔甲槽为空，默认日志关闭。[新基线截图](../../run/client/screenshots/player-comprehensive-baseline-20261012.png) 实际确认双臂／袖层和双手当前物品；截图时临时 pitch=65。
- 不设中途断点的 dynamic 播放实例 `6e154f55-be97-4153-8fa8-407d6d9ed633` 实际完成 1.5 秒 vanilla_idle 策略修订、3 秒 FINISHED，第一人称模型记录 fadeOut=0.125 与随后 0.96875，最终 Debug 确认 frames=0、histories=0。首次 MODEL_LAYER 的 fadeIn 已为 1，本轮没有新的淡入中间帧证据，不能将前轮证据混作本轮结果。
- 已确认静态数据来源边界：实际持物从当前玩家 render state 提取，原版每手事件与 Iris HEAD 透明判断使用原版换装缓存，两者可在换装期间不同。临时将无组件修补的副手 sandstone 替换为 glass 后，稳定帧实测绘制值／缓存均为 glass；不一致条件断点未命中，光影可见症状尚未复现。副手已恢复并实测为 1 个 sandstone，主手未改。
- 第一人称普通物品只复用第三人称 ItemInHandLayer／ArmPose 效果，不执行被替换分支中的第一人称吃喝、刷子、弓弩屏幕布局或 applyForgeHandTransform。当前默认吃喝没有食物送嘴的屏幕抖动；这是现有真实身体路径的行为边界，本轮没有扩展物品动作。
- 用户要求断点命中不抢焦点后，先移除暂停临时断点并恢复运行，改用 NONE 日志点；实际收到 currentOff=eventOff=sandstone、FIRST_PERSON、invisible=false、scopesBefore=0，无断点表达式错误。NONE 日志点没有因 temporary 自动移除，已显式清理。MCP 没有 IDEA 全局焦点设置接口；用户随后报告已手动完成设置，并明确允许继续暂停断点，本轮未替用户修改或验证全局设置值。
- 恢复暂停式 Debug 后，override 实例 `5663f715-dd33-4742-9918-52206bd07aad` 在服务端跳至 1 秒并暂停播放；实际第一人称模型右臂 X=1.7453293、Z=-0.2617994，左臂 Z=0.17453292 弧度，history=1、frames=1。[覆盖姿态截图](../../run/client/screenshots/player-comprehensive-override-20261012.png) 已通过 MCP 实际查看，右臂位于相机后方的示例行为仍沿用前轮边界。
- F1 运行检查已完成：从上述活动姿态设置 hideGui=true 后主动 stop，[F1 截图](../../run/client/screenshots/player-comprehensive-f1-20261012.png) 没有界面、手臂或持物；原始字段实测 HISTORIES=1、FRAMES=0、PENDING=0，确认模型历史有界留存。已确认原因是 F1 跳过手部及本地相机实体状态提取，历史清理仍由 prepare 的空帧分支触发；恢复渲染后实测 history=0、frames=0、pending=0。维护时不能把动画帧到期等同于模型历史立即释放，也不能把该缓存留存当作继续绘制；本轮未扩大为即时清理改动。WIDE、其他动作组合、实际地图内容、多人与着色器组合仍未验收。
- [第三人称正面截图](../../run/client/screenshots/player-comprehensive-third-person-20261012.png) 实际确认完整头部、身体、双腿与双手持物；停止后 scope=0。最终 Debug 实测主手 1 个 barrier、副手 1 个 sandstone、四个盔甲槽为空；恢复 FIRST_PERSON、yaw=-24.650059、pitch=3.150028、hideGui=false，并关闭开发日志。MCP 确认位置、旋转、创造模式与生命／饥饿值保持基线；仅保留两个原有禁用用户异常断点，Client Debug 为 running。经 IDEA VFS 刷新读取当前完整日志，未出现 ERROR／FATAL。
- 本轮只补充玩家动画文档与本记录；既有 591 条 Git 状态（276 暂存、546 未暂存、0 冲突）保留，没有手动暂存、提交、重置或额外备份。新截图保留，未运行 JUnit；日志中的 Debug 追帧、spark 超时及启动环境警告不作为正常性能证据。

## 2026-10-12：玩家第一人称双臂与随手物品

- 用户确认 A：第一人称复用真实玩家模型的当前姿态，在原相机空间只渲染双臂／袖层及随手物品；没有自定义策略时也使用原版玩家姿态。复用现有 GeckoLib 求值、覆盖／来源混合及淡入淡出，不增加播放同步或第三方库，不改变相机。
- 新增 `LcPlayerFirstPersonRenderer`，为 WIDE／SLIM 分别建立独立模型；通过现有唯一 `EntityRenderersEvent.AddLayers` 入口调用 `LcPlayerAnimationRegistration.registerFirstPerson` 初始化。复用 AvatarRenderer 的状态提取、NeoForge 状态修饰、旋转和缩放，渲染时只提交手臂子树；没有提交头、身体、腿或盔甲层，不改变第三人称模型。
- 新增 `ItemInHandRendererMixin` 和地图 Invoker `ItemInHandRendererAccessor`，在原每手事件和 Iris HEAD 过滤之后替换手部提交，保留原渲染批次 flush、手臂事件和窥镜隐藏语义。作用域用 try/finally 清理，独立模型避免第一人称姿态污染第三人称；原物品视图延迟旋转不再叠加到真实玩家手臂。
- 普通随手物品调用原版 `ItemInHandLayer` 变换，地图在最终手部变换下调用原版 `renderMap`，保留地图背景、内容和装饰入口。上述内容／装饰入口已按本地当前版本源码确认，实际地图内容和装饰尚未验收；没有承诺任意特殊物品或第三方模组自动适配。
- 所有项目修改通过 IDEA；三份新 Java 与两份既有 Java 的修改范围经 IDEA 格式化，关闭导入优化与成员重排，资源排版保留。最终五份 Java 和 Mixin JSON 的 IDEA 诊断均为 0 问题。语义引用核对新渲染器 12 处、注册方法 1 处、地图 Invoker 1 处，AddLayers 与 RenderHandEvent 仍沿用既有唯一监听入口。
- IDEA 编译 `isSuccess=true`、`problems=[]`；Data 退出码 0。25 份生成文件（含缓存）读取文本与执行前相同，生成文件集合无新增／删除；没有手工编辑生成输出。完成后启动新 Client Debug，运行期间没有再次编译或热替换，也没有运行 JUnit。
- Minecraft MCP 实际截图确认第一人称双臂／袖层和主手刷怪蛋可见，未绘制身体；[默认姿态截图](../../run/client/screenshots/player-first-person-idle-20261012.png)。覆盖示例在服务端 seek(1)／暂停后，独立第一人称模型右臂 X=1.7453293、Z=-0.2617994，fadeIn=1；右臂移到相机后方，左臂可见，[向下观察截图](../../run/client/screenshots/player-first-person-override-down-20261012.png) 不作为右臂可见证据。
- 不设中途断点的 dynamic 播放实际记录到第一人称模型 fadeIn=0.8125、1.5 秒修订为 vanilla_idle 时新 fadeIn=0.125、3 秒 FINISHED 后 fadeOut=0.15625，最后客户端帧／模型历史均清零。暂停 stop 的晚到包直接到期，不作为渐变过程证据。[地图截图](../../run/client/screenshots/player-first-person-map-20261012.png) 实际显示副手地图背景／边框和主手物品，本轮测试地图没有 map_id。
- [第三人称正面截图](../../run/client/screenshots/player-first-person-third-person-20261012.png) 确认完整头身腿和手持物仍可见。临时副手恢复为空，主手仍为原刷怪蛋；日志 false、history=0、渲染作用域 inactive。视角恢复 FIRST_PERSON、yaw=-71.60007／pitch=-33.6，MCP 确认位置／旋转；移除全部临时断点，仅保留两条用户原有禁用异常断点，Client Debug 继续运行。
- 未验收 WIDE 皮肤、全部动作组合、袖层开关、特殊物品使用、实际地图数据／装饰、多人与第三方着色器／模组组合。当前日志读取未见 ERROR／FATAL；Debug 暂停导致追帧及 spark 超时警告，不作为正常性能证据。本轮未创建额外备份，没有手动暂存、提交、推送或重置；IDEA 自动将三份新 Java 加入索引，既有工作区修改保留。接口、限制与运行证据同步到玩家动画文档、动画运行态、网络及系统索引。
- 六份相关文档 IDEA 检查无 ERROR，系统索引和网络文档各保留一条既有表格 WEAK WARNING，其余四份无问题；范围内 `git diff --check HEAD` 退出码 0，仅提示 LF／CRLF 策略。最终 Git 为 591 条、276 暂存／546 未暂存／0 冲突，相对本轮开始新增三份 Java；现有注册文件原已暂存，本轮新增调用保留为未暂存修改。

## 2026-10-12：动画开发日志开关与玩家／胸甲补验

- 用户确认 A：新增 `util/animation/LcAnimationDebugUtil.ENABLED` 开发内存开关，默认 false，仅非生产环境生效，可经 Debug 动态修改，重启恢复关闭。复用 Lobecorp 日志，集中模板和事件标识；没有新增配置注册、网络包或动画功能开关。
- 最小修改 `LcAnimationInstance`、`LcClientAnimations`、`LcPlayerAnimationRuntime` 与 `LcPlayerPoseBridge`：记录启动回调正常返回、策略实际变化、首次停止、客户端安装／修订／停止、实际移除／尾部到期／非空世界缓存清理、模型层边沿与采样异常。稳定 tick 和重复 render state 静默，复用现有历史，不新增去重缓存；服务端直接读取字段，不为日志再次执行查询提供器或时间线。采样异常记录后仍抛出同一异常。
- `SERVER_START` 表示零时刻回调正常返回；回调内自行停止／改变策略时，相应日志可先于 START。客户端终止尾部到期后，最后一份终止同步可能短暂重新安装并立即到期，观察到第二组 CLIENT_STATE／CLIENT_EXPIRED；日志反映实际缓存变化，不据日志条数推断业务回调次数，本轮没有改变原有同步或清理行为。
- 所有项目编辑均通过 IDEA。新增工具类与四份 Java 的修改行通过 IDEA 格式化，关闭导入优化与成员重排。五份 Java 最终检查为 0 ERROR、6 条既有 WARNING；新工具、运行提取和姿态桥接无错误／警告。IDEA 语义引用核对工具的 16 处引用，未发现过时调用。
- IDEA 编译成功，`isSuccess=true`、`problems=[]`；Data 退出码 0。25 份生成文件（含缓存）读取文本与执行前相同，文件集合无新增／删除，未手工编辑生成输出；Data 输出预览截断并含环境警告，不声称全量日志无警告。运行期间未再次编译或热替换，也未运行 JUnit。
- 已实际通过 Minecraft MCP + 新 Client Debug 进入现有单人世界。Debug 确认初始 ENABLED/effective 均 false，关闭时执行 dynamic 无动画日志；开启后同一 dynamic 播放出现 SERVER_START → SERVER_POLICY（1.5 秒，revision 0→1，仅 vanilla_idle）→ SERVER_STOP（FINISHED），客户端及模型层收到对应边沿并清理尾部。再次关闭后停止冻结播放并执行 dynamic，没有新增动画日志。主动停止胸甲示例另确认 STOPPED 原因和 fadeOut=0.40625。
- 覆盖示例在服务端线程调用 seek(1)／setPaused(true)，随后在本项目姿态桥接取值：时间 1 秒、fadeIn=1、sources 为空，主 PlayerModel 右臂 X=1.7453293、Z=-0.2617994 弧度（100°／-15°）。临时将原空胸部槽替换为钻石胸甲，独立护甲 PlayerModel（modelIdentity=1390645920，与主模型 2066557984 不同）得到相同角度。MCP 保存并实际查看 [玩家截图](../../run/client/screenshots/player-animation-override-20261012.png) 和 [胸甲截图](../../run/client/screenshots/player-animation-chest-20261012.png)，均为 854×480，可见手臂及胸甲一致姿态。
- 调试收尾已停止示例，客户端帧／模型历史均为 0，胸部槽恢复为空，开关恢复 false；设置回本轮记录的 FIRST_PERSON、yaw=-71.60007／pitch=-33.6。临时断点已移除，保留两个用户原有禁用异常断点，Client Debug 继续运行。本轮未使用原生 Screenshot 调试调用、未更改运行配置、第三方模组或库存中的其他物品；两份 MCP 截图保留，没有创建额外备份或手动暂存／提交／推送／重置。
- 本轮未复现前轮 Sodium 崩溃，根因仍未确认。暂停 Debug 引发服务端追帧和 spark 统计超时 WARN，不作为正常性能证据；头盔／护腿／靴子各槽、多人与晚加入、第三方适配、组合压力及第一人称未验收。相关开关入口、边界与截图同步到玩家动画文档及系统索引。
- 四份涉及文档最终 IDEA 检查无 ERROR，系统索引保留一条既有表格格式弱警告，其余三份无问题；范围内 `git diff --check HEAD` 退出码 0，仅提示 LF／CRLF 策略。最终 Git 为 588 条、273 暂存／544 未暂存／0 冲突，相对开始仅新增工具文件一条（由 IDEA 自动加入索引）；既有工作区继续保留。当前 Client 日志未检出 ERROR／FATAL，不把此前警告或历史崩溃报告清零。

## 2026-10-12：模型特效工程 MCP 与 Client Debug 验收

- 按用户要求启动 IDEA Client Debug，Minecraft MCP 连接现有单人世界；在渲染线程经原生 Screen 确认保存／另存为、项目重开、定义导出、OBJ 导出及包管理弹窗。新建项目文件菜单同时提供保存和另存为，已保存后不重复添加保存项，标题为模型特效项目。
- 实际生成并重开 `.mfxproj`，确认原生 NBT 的 `meta/data`、后缀及字节数组资产字段，完整资产往返一致且保存后非脏状态。OBJ 和 `.mfx` 打开为未保存的新模型特效项目；OBJ 工程另存为 `.mfxproj` 不覆盖来源，OBJ 导出文本与源几何一致。
- 实际导出 `.mfx` 与含两条定义的 `.mfxpack`，没有生成普通 `.fx`／`.fxpack`。原生管理弹窗删除一条定义并 GC 后，另一条完整资产仍保留。资源重载 future 完成且无异常，日志确认挂载模型包，`FXHelper` 发现并加载独立定义及包内定义为 `PhotonModelEmitter`，完整资产 JSON 与导出源一致。
- 一次性负尺寸真实菜单回调的五项断言全为 true：绕序反转、法线反向、采样位置及对应 UV 不变、其余资产不变、再次执行恢复。原生层级行 CLICK／DOUBLE_CLICK 监听的三项断言全为 true：单击保留模型视图并选中部件，双击进入 UV。部件阴影默认关、双面默认开，两个菜单可分别修改并恢复；运行时两个材质副本分别采用双面／单面的背面剔除设置。
- 独立模型窗口经原生视图倍率菜单从 2 改为 1，再重复选择 1，均保留同一 OS 窗口与工程；下一渲染帧的 surface／ModularUI 为 2048×1195、倍率 1。测试期间设置文件重定向至唯一测试文件，之后恢复原设置文件、外观、设置快照／脏标志与游戏倍率，七项恢复断言全为 true。
- 清理了四个临时编辑器的工程及 JVM 引用，临时断点全部移除，原有两个禁用异常断点保留；已退出 MCP 控制模式，Client Debug 保持运行。唯一测试前缀 `mfx-acceptance-20261012-12686476-7d09-4953-9b4a-b152f3812b47` 的工程／OBJ／定义保留于 `run/client/ldlib2/assets`，模型包保留于 `run/client/photon/fxpacks`，未删除测试产物或既有备份。
- 本轮使用默认白模，未验收自定义 PNG 依赖收集／GC、模组 JAR 内包、多人、快捷键实际按键与检视器全部控件。后期 MCP 截图为黑屏；原生事件链与运行数值不代表完整视觉效果或物理双击间隔已验收。一次标题界面创建编辑器的调试探测因缺少维度注册表失败，进入现有世界后上述路径完成；调试表达式的 API／编译错误不作为功能失败或成功证据。
- 同日按用户要求重新打开：上次 Client 正常退出后，重新启动 Client Debug（会话 `f52aa94b-a640-4362-9786-b6bfabf92d5a`），从新原生 `FXEditor.fileMenu.openProject` 打开上述已存 `.mfxproj`，自动创建 `PhotonModelProjectEditor`。标题、工程文件路径及后缀正确，非脏状态且完整资产与磁盘一致；重启后的独立 `.mfx` 和包内 `probe_a` 被 `FXHelper` 发现并加载，资产 JSON 与工程相等。Minecraft MCP 在 9876 重新 ping 连接成功，当前日志无 ERROR／FATAL，保留 Iris 更新网络 WARN。
- 重开时 MCP 截图只返回 854×480 局部；原生 `Screenshot.grab` 使用非空 `System.out::println` 回调生成 [2560×1494 完整帧截图](../../run/client/screenshots/model-project-reopen-20261012-1791737497580.png)，已实际查看整个模型工程、白模、阴影未选／双面已选及负尺寸普通按钮。仅补充静态画面证据，物理双击、实际快捷键、纹理依赖／GC、JAR 包及多人等未验收范围不变。
- 本轮仅通过 IDEA 补充本记录及 [模型特效维护记录](photon-model-effects.md#维护与实际检查)，未修改生产代码、运行配置或第三方工具，未单独执行编译、Data 或 JUnit。两份文档的 IDEA 诊断无新增问题，模型文档保留第 90 行既有表格格式弱警告；范围内 `git diff --check HEAD` 退出码 0，仅提示 LF／CRLF 策略。当前 Client 日志检索无 ERROR／FATAL，仍含启动环境 WARN。保留用户原有工作区，未手动暂存、提交、推送或创建额外备份。

## 2026-10-12：玩家第三人称 GeckoLib 姿态与原版来源混合

- 用户确认 A：接入第三人称玩家、皮肤外层与原版盔甲，第一人称留待后续；兼容范围是公开来源和桥接通道，没有自动适配其他模组。接口、示例及边界见[玩家第三人称动画](player-animation.md)。
- `LcPlayerAnimationPolicy` 与 `playerAnimation/setPlayerPolicy` 显式声明来源、骨骼／TRS 遮罩、权重、优先级和进出过渡；没有声明时不接管玩家，已接管时默认完全覆盖。新增 override、idle、dynamic、stop 示例命令与一组无方块的玩家绑定骨架／动画资源。
- `LcPlayerAnimationSampler` 使用当前 GeckoLib 5.5.2 的原生 loader、AnimationTimeline 与 AnimationProcessor，消费权威时间及代码修饰结果；没有第二套触发同步或独立播放时钟。`modifyPose(ownedBones, modifier)` 同步声明骨骼的绝对结果，明确归零也能覆盖客户端资源，不用数值差异推断所有权。
- 现有 state/remove 协议扩展为 14，携带策略、修订与服务器／阶段／停止时刻、终止原因和姿态修饰结果。软停止立即结束逻辑和附着，只保留视觉尾部；硬清理直接释放。补充 `finishTick` 之后停止的玩家快照，避免本 tick 先硬移除、下一 tick 才出现终止尾部。
- 已完成重叠层分别淡化、首次观察终止尾部、过渡中再次修订的有界贡献快照，以及 NORMAL 原始 Euler、多圈旋转和负缩放分支修正。下层基底继续响应原版状态，不积累递归过渡历史；复杂骨架首次观察前未见过的圈数仍无法恢复，shear 限制未改变。
- 项目编辑均通过 IDEA 完成，Java 格式化限制在新类或修改行，关闭导入优化与成员重排；手写 JSON 未自动格式化。上述最终代码再次通过 IDEA 编译，`isSuccess=true`、`problems=[]`；最终 Data 退出码 0。25 份生成文件（含缓存）的 IDEA 读取文本前后完全一致，未手工修改生成输出。一次与 Client 并行的 Data 调用超时，不作为退出码成功证据；最终结果来自停止 Client 后的独立重跑。Data 输出预览截断且含环境警告，不声称全量日志无警告。
- 29 份 Java 的最终 IDEA 诊断为 0 ERROR、15 WARNING：10 条既有警告，以及 4 条新公开策略方法未使用、1 条示例枚举显式 private 构造器冗余提示；后者保留以遵守显式可见性规范。新渲染桥接／采样类无错误或警告。Snapshot、RemovePayload 构造与引用已核对，客户端注册精确一个调用入口；两个玩家 Mixin 仅登记在客户端。最终生成目录仍为 25 份文件，无新增／删除；本轮范围内 git diff --check HEAD 退出码 0，仅保留 LF／CRLF 策略提示。两份本轮收尾文档的 IDEA 诊断无错误或警告。
- 已实际执行 Minecraft MCP + IDEA Client Debug：MCP 读取 Dev 玩家与世界，Debug 在服务端线程播放并冻结 1 秒示例，通过原生采样和最终 PlayerModel 断点确认覆盖时右臂 X=1.7453293、Z=-0.2617994 弧度，即 100°／-15°，来源为空。同实例策略修订从 1 到 2 后客户端仅收到 vanilla_idle，并观察到待机摆动；动画时间仍为 1 秒。记录到淡入中间进度 0.25、软停止淡出进度 0.59375，尾部结束后客户端帧／玩家层均为 0。相关条件／日志断点未报告表达式错误。这些是模型数值与单人同步证据，不是完整画面或多人验收。
- 运行限制：当前 MCP 命令入口报 no command method found、聊天入口报 ChatScreen instantiation failed，按键返回成功但未产生对应效果；截图多次为黑屏。原版完整帧截图只确认无动画时的玩家画面，不能验收自定义动画。补截图时一次 Debug 调用误传 null 回调，明确触发 Screenshot 的 NPE 并退出；重启后的补充检查又出现 Sodium 注入方法 handler$zkm000$sodium$postRender 的 NoSuchMethodError，根因尚未确认，未修改第三方模组。两份崩溃报告保留在 run/client/crash-reports/crash-2026-10-12_00.05.52-client.txt 和 crash-2026-10-12_00.09.29-client.txt。护甲／皮肤外层完整画面、多人／晚加入、第三方适配及组合压力场景未验收，第一人称未接入。
- 调试收尾：本次临时断点全部移除，原有两个禁用异常断点保留；首次会话已退出 MCP 控制模式。两次退出日志均确认保存玩家与所有维度，当前无运行中的 Debug 会话。首次会话已恢复第一人称和原视角，末次重启的测试视角未完成恢复；没有发放物品、修改装备或保存动画实例。
- 原工作区修改继续保留；IDEA 自动将新增文件加入索引，未手动暂存、提交、推送、重置或删除备份，未手动创建额外备份。

## 2026-10-11：独立动画根位移与背／手／世界切锚示例

- 用户确认 A：新增独立接入示例，不迁移女皇、清道夫等既有技能。新增 6 份 Java、3 份手写 JSON，修改 Lobecorp 初始化与客户端运行事件两份 Java，并同步 5 份系统文档；当时采用旧独立拔刀入口，后续已改为[动画调试命令](animation-runtime.md#动画调试命令)，此条保留历史实施范围。
- `/lobecorp animation_example [stride]` 使用 `Commands.LEVEL_GAMEMASTERS` 权限，stride 为 0–4、默认 1。在命令来源水平前方 2 格创建本次播放拥有的独立白模与刀逻辑目标；默认资源在 6 秒内推进主体 1 格乘 stride。没有注册新 Minecraft 实体、发放钻石剑或移动玩家，也未声明伤害、碰撞、重力或导航接管。
- `LcAnimationExampleDefinitions` 延迟包装完整 data 模型与动画，`editAnimation` 添加头部旋转，显式查询 `q.example_flourish` 读取实例变量，显式声明 root 的本地 Z 平移。命令启动后通过 `setParameter` 设置独立 stride 与摆头幅度，资源与代码包装分别保留职责。
- `LcAnimationDemoSession` 复用通用播放／目标／绑定 API：0 秒将同一个 `BladeTarget` 绑定背部并采样 draw 骨；1.5 秒切手部；3 秒切世界锚点并采样 drop 骨，在后续 0.5 秒下落与躺平，主体继续前移。切锚前先更新当前采样，保持世界变换，目标与绑定 UUID 全程不重建；非循环片段到末尾保留最终姿态，下一次推进停止，由显式 `onStop` 结束本示例目标寿命。
- 新增 `data/lobecorp/animation/models/example/attachment_draw.geo.json`、`data/lobecorp/animation/animations/example/attachment_draw.animation.json` 与同 ID 的 assets 动画覆盖。白模包含 11 个骨骼、7 个方块和背／手 locator，纹理使用原版白混凝土；客户端覆盖额外抬手并加倍摆头频率，根平移仍消费，交接关键帧端点一致，不反写服务端轨迹。
- `LcAnimationDemoClient` 枚举已同步快照，按示例 definitionId 显式登记主体模型与钻石剑物品显示，通过自有 UUID 差集注销结束／消失的显示；重新出现可再次登记，晚加入读取当前锚点，不回放时间点。复用唯一客户端 tick、登录／退出和世界清理入口；新增唯一 `RegisterCommandsEvent` 入口，不新增 Payload、不修改协议或通用动画运行态。
- 项目文件均通过 IDEA 编辑；新 Java 与既有修改行通过 IDEA 格式化，关闭导入优化和成员重排，手写 JSON 未自动格式化。8 份 Java 的 IDEA 诊断为 0 ERROR，只有 Lobecorp 构造器原有的 modContainer 未使用警告；3 份 JSON 无错误／警告。5 份系统文档无 ERROR，README／events 各保留一条既有表格式弱警告；新增示例链接的标题锚点已修正。范围内 `git diff --check` 退出码 0，仅保留 LF／CRLF 策略提示。定义初始化与命令登记的 Call Hierarchy 查询超时后，IDEA 语义引用分别确认唯一调用点；客户端示例 tick 调用链确认来自唯一 ClientRuntimeEvents 入口。
- IDEA 项目编译 `isSuccess=true`、`problems=[]`；IDEA Data 退出码 0。生成目录仍为 25 份文件（含缓存），IDEA 读取文本前后完全一致，无新增／删除，未手工修改生成输出。Data 输出预览截断，不据此声称全量日志无警告；未运行测试、游戏、专用服务器或联机，实际画面、切锚和晚加入验收仍待完成。
- 原有 244 个暂存条目的索引状态不变，9 个新增文件由 IDEA 自动加入索引，后续编辑保留在工作树。未手动暂存、提交、推送、重置或创建额外备份。

## 2026-10-11：服务端资源动画、显式根运动与连续空间附着基础层

- 本轮实现已确认的基础层与公开适配 API，未迁移女皇、清道夫等既有技能。新增 39 份 Java 与一份系统文档，修改 10 份既有 Java 和 8 份系统文档；公开入口及接入边界见[服务端动画、根运动与空间附着](animation-runtime.md)。
- `LcAnimationDefinition` 包装完整资源，支持 JSON 内容派生、姿态修饰、独立查询、时间点／逐 tick／停止回调；`LcAnimationDefinitions` 延迟登记，`LcAnimationInstance` 固定本次资源版本，允许实例参数及显式内容修改。时间点按时间稳定排序；跳转或修改内容采样失败时回滚，停止／跳转／内容变更取消失效时间线后续回调。激活失败释放实例与绑定，世界／服务器清理期间拒绝回调重新创建播放。
- `LcSkeleton`、`LcAnimationClip` 与 `LcMolangParser` 复用当前 GeckoLib 解析和缓动，保存 Euler 旋转与单实例查询／变量上下文。标准查询及显式查询值随快照发送；查询和姿态修饰约定为纯计算。未知查询明确失败，不用零静默代替；变量／临时变量保留缺省零规则。
- `LcRootMotion` 仅在显式声明时消费顶层根骨骼的指定平移轴；默认 direct 或碰撞差分执行器不接管导航／重力／步高。碰撞裁剪丢弃被阻挡差分，时钟继续，消费后的骨架与实际世界位置共同计算锚点，避免重复位移。修改参数重建采样基线，避免幅度变化补成瞬移；非循环片段显式跳转越界或缩短内容后不倒退逻辑时间。矩阵分解将镜像反射归入 X 缩放，保留变换手性；TRS 的 shear 限制保留。
- `LcAnimationAnchor`、`LcAttachmentBinding` 支持骨骼／locator／世界锚点、资源或代码轨迹与保留世界变换的切换。同一底层目标由 identity 防止多处空间写入；切换与解除不重建目标、不重置寿命或命中记录，也不自动删除目标。新增 Entity、SkillEffect、Hitbox 和客户端 Photon 接收适配；`HitboxInstance.stopFollowing` 与 Photon 外部锚点模式避免原跟随覆盖。
- `LcAnimationResources` 通过保留型服务端重载监听器读取 `data/<namespace>/animation/models/<path>.geo.json` 与 `animation/animations/<path>.animation.json`；模组内置数据目录作为默认入口，外部数据包可覆盖。现有 assets 动画与模型未复制、迁移或修改；重载只影响新实例，旧实例固定内容直到结束。
- 新增完整内容分片、状态帧和移除载荷，沿用 `network.tc`／`IToClientPayload`；`LcPayloads` 协议由 12 升为 13，现为 2 C2S／11 S2C。同步完整几何、locator 与动画 JSON，使用 SHA-256 版本和 24 KiB 分片；不传输纹理或 Java 回调。按连接缓存发送历史，重生与重新追踪不会遗留旧帧或永久丢失独立显示登记。
- 客户端 `LcClientAnimations`、`LcAnimationBoneAdapter`、`LcServerGeoModel` 与 `LcAnimationDisplays` 提供权威姿态插值、标准 assets 视觉覆盖、显式模型／物品／自定义展示。已修正覆盖循环时间、变量别名、重复渲染采样累积及 Euler／四元数一致性。显示登记由接入方显式 remove，失去追踪只隐藏；客户端结果不影响服务端位置、碰撞或伤害。
- 复用既有实体／世界／追踪／客户端事件入口。实体技能后推进动画，再处理抓取；世界帧末按技能效果 → 动画与附着 → 命中框 → 状态同步执行。服务端停机与已卸载世界、客户端世界卸载及连接退出分别清理所属状态；没有新增重复 LevelEvent.Unload 监听。
- 项目文件均通过 IDEA 编辑；新 Java 与既有修改行通过 IDEA 格式化，关闭导入优化与成员重排。49 份相关 Java 的最终 IDEA 诊断完成、0 ERROR；37 条提示分别为 22 条未接入公开 API、14 条世界借用资源检查、1 条初始空姿态检查，不记为零警告通过。系统文档无 ERROR，README／network／events／hitbox 保留各一条既有表格式弱警告。IDEA 调用／引用核对世界帧末入口、渲染桥接和载荷登记，没有删除或重命名既有公共 API。
- IDEA 项目编译 `isSuccess=true`、`problems=[]`；IDEA Data 退出码 0。生成目录仍为 25 份文件（含缓存），IDEA 读取文本前后完全一致，无新增／删除，未手工修改生成输出。Data 输出预览截断，不据此声称全量日志无警告。范围内 `git diff --check` 退出码 0；本轮两处文件末尾多余空行已修正，保留原有 LF／CRLF 策略提示。
- 原有 204 个暂存条目的索引状态不变，40 个新文件由 IDEA 自动加入索引；后续编辑保留在工作树。未手动暂存、提交、推送、重置或创建额外备份。
- 未执行测试、启动／重启游戏或专用服务器。多人追踪／重生／重载、碰撞、切锚及画面实际验收仍未完成；根运动本轮只支持顶层根平移，TRS 分解不精确保留 shear，服务器模型渲染需显式接入新 GeoModel，现有技能接入及纹理传输不属于本轮范围。

## 2026-10-11：模型特效原生项目与独立定义／包格式

- 用户确认 A：工程 `.mfxproj`、定义 `.mfx`、包 `.mfxpack`；本轮不注册旧模型文件后缀或迁移入口。根 `AGENTS.md` 增加涉及向后兼容时默认先询问的项目规则，已明确选择时不重复询问。
- `PhotonModelProject` 委托原生 NBT `{meta,data}` 读写与 dirty 比较，资产仍存为字节快照；打开 OBJ／模型定义创建未保存模型工程，保存不覆盖来源。`PhotonModelProjectEditor` 补未保存工程的“保存”，保存／另存、最近项目和关闭确认走原生流程。`PhotonModelEditor` 保留 OBJ 交换，移除裸 JSON 工程／数据导出入口。
- `PhotonModelExportUtil` 标识原生导出委托并提供单模型压缩 NBT 定义读取；`FXProjectMixin` 同步模型定义／包的对话框后缀、文件补名与包名裁切。新增 `PhotonModelFileUtil` 集中文件约定，`FXPackExporterMixin` 写模型包内 `.mfx`，`FXPacksMixin` 接入用户目录／模组包发现、缓存、枚举、删除及 GC，`FXHelperMixin` 复用原生资源列表、CACHE、版本读取及运行时。逻辑 ID 沿用无后缀形式，同 ID 普通 `.fx` 优先、缺失才查 `.mfx`。
- 同步 Mixin 配置、模型帮助翻译和系统文档／索引；项目文件均通过 IDEA 修改，Java 仅格式化修改范围并关闭导入优化／成员重排。IDEA 对 10 份 Java、Mixin 配置及 4 份系统文档的最终诊断均完成、未截断，0 错误／警告；根 `AGENTS.md` 已写入并复读，专用检查因 `Failed to download agnix-lsp binary` 受阻，不记为检查通过。中途漏掉的模型编辑器 import 已恢复，连锁错误已清除。
- IDEA 项目编译 `isSuccess=true`、`problems=[]`；IDEA Data 退出码 0。生成目录仍为 25 份文件，只有中英文语言文件和两份生成缓存变化；两种语言各 1113 键，无增删，仅模型帮助及缺少资产提示两键的值更新。生成输出未手工修改。Data 输出预览被截断且含原终端／Sodium 警告，不据此声称全量日志无警告。
- 通过 IDEA 核对原生方法／lambda 字节码及 ZIP URI 约定，互相复核模型包导出、枚举、删除与 GC；语义引用确认两种编辑器共用类型安装，模型 TYPE 仅由本工程返回／登记。主源码及测试源码没有旧 `.lcmodelproj` 或已删除编辑器常量的引用；范围内 `git diff --check` 退出码 0，仅保留既有 LF／CRLF 策略提示。原有 200 个暂存条目的索引状态不变，4 个新文件由 IDEA 自动暂存，后续修改仍保留在工作树；未操作暂存、提交、推送或重置，未创建额外备份。
- 未执行测试或启动／重启游戏；保存／另存为、定义重开、包挂载／删除／清理及贴图的实际客户端验收仍未完成。普通特效中的模型对象及既有外部 JSON／OBJ 几何资产来源不属于本轮文件类型迁移范围，未清理其内部资产协议；本轮取消的是旧模型工程／发布后缀与裸 JSON 导出入口。模型包只管理 `.mfx`、普通包只管理 `.fx`，混装或直接重命名旧包不受支持。

## 2026-10-11：模型负尺寸改为一次执行的翻面操作

- 用户重新测试确认原翻面效果正常；本轮仅修改 `PhotonModelEditor.java` 中的交互入口：检视由 Toggle 改为普通按钮，层级／视窗右键由复选项改为普通菜单项。每次点击翻转一次目标部件朝向，再次执行恢复，统一使用会话历史，支持撤销与重做。
- 保留现有 `Part.insideOut`、采样与 OBJ 输出实现，不执行 XYZ 负缩放；UV 坐标与顶点对应关系、模型几何、位置、尺寸和 TRS 保持既有行为。同步模型系统文档；未新增翻译键、资产字段或公共接口。
- 通过 IDEA 最小补丁修改，Java 仅格式化两个修改范围，关闭导入优化与成员重排。IDEA 对 Java 及两份文档的诊断均完成、未截断，0 错误／警告；IDEA 项目编译 `isSuccess=true`、`problems=[]`，Data 退出码 0。生成目录 25 份文件（含缓存）的 SHA-256 前后相同，无新增／删除；范围内 `git diff --check` 退出码 0，仅保留既有 LF／CRLF 策略提示。Data 预览有原终端与 Sodium 警告且被截断，不据此声称全量日志无警告。
- 只读复核确认检视与右键每次各提交一次历史，两种右键入口复用同一方法；底层资产、会话、采样和 IO 文件与本轮修改前相同。已有 200 个暂存条目的状态前后相同。未运行测试或游戏，本轮普通按钮的实际点击验收仍待客户端确认；未创建备份文件，未暂存、提交、推送或重置，保留用户已有修改。

## 2026-10-11：清道夫闲逛常速、目标加速与 2 格减速

- 修正已确认的闲置突加速原因：`SweeperAi.updateMovementSpeed` 原先将任意 WALK_TARGET／导航回退目的地纳入 10 格快跑判断，随机闲逛也可能瞬时切换为 3 倍。现无有效攻击／清理实体目标且无有效指挥指令时固定使用原常速 1；鸟群转向和原目标资格不变。
- 攻击、清扫和重组等实体目标以 20 tick（1 秒）从倍率 1 线性升至原追踪倍率 3；计时绑定稳定实体对象，不随实体位置或路径节点变化重启。目标变化、实际停止、技能占用、闲逛、位置指令接管、常规近距减速或 AI tick 间断后重置。原版 JUMPING 的 hasWanted=false 不再单独认定停止，活跃导航中可连续更新加速与速度，setWantedPosition 保留跳跃操作状态。
- 清道夫自身非攻击移动的减速边界由 10 格改为不超过 2 格，覆盖清理接近与 MOVE／RETURN；攻击追击仍保留原到位及技能范围规则。位置指令先于实体记忆选目的地，不使用实体目标加速计时；MOVE 自定义速度上限仍最后应用。未改指挥家通用避让／精确接近参数、清理 0.5 格工作间距、技能生命周期、持久化或网络。
- 项目修改通过 IDEA 完成，仅改 `SweeperAi.java` 并同步实体文档、系统索引和本记录。Java 已按修改范围格式化，关闭导入优化和成员重排；源码 index 诊断 analysisFresh=true、analysisTimedOut=false、未截断，0 错误，7 条均为修改前已有警告。语义引用核对既有 Sweeper 委托入口、唯一实体加速调用及距离常量的两处引用，无公共接口删除或迁移。
- IDEA 项目编译 `isSuccess=true`、`problems=[]`；IDEA Data 配置退出码 0。生成目录 25 份文件（含缓存）的 IDEA 读取文本前后完全一致，无新增／删除，未手工修改生成输出。Data 输出预览被截断，含原有终端与 Sodium 警告，不据此声称全量日志无警告。范围内 `git diff --check` 退出码 0，保留既有 LF／CRLF 策略提示。
- 修改前 Java 文本备份位于 `backups/sweeper-movement-speed-2026-10-11`，保留来源相对路径，读取内容与修改前快照一致。已有 200 个暂存条目的状态前后相同，未手动暂存、提交、推送或重置，保留用户已有修改。未执行测试、启动／重启游戏或热替换；闲逛、目标切换、跳跃／台阶连续加速与 2 格减速手感仍待游戏验收。

## 2026-10-11：清道夫地面鸟群移动

- 用户确认 A+B：自主闲逛和战斗追击均使用分离、对齐和聚合。仅在 `SweeperAi` 接入局部群体转向，复用原附近／可见生物 Sensor、Navigation 与 MoveControl；邻域 6 格、同层高差 1 格、最多 12 个有效邻居，每 5 tick 重算并逐 tick 平滑。对齐使用实际水平速度，聚合只参考相同攻击目标的同类；近距同线前方或重叠时按统一世界侧向与实体 ID 分侧，修正单纯纵向分离被前进约束抹掉的问题。
- 保留原速度、路径、WALK_TARGET 与路点 Y；局部前视 0.75 格，最大偏转 45 度，接近节点逐渐退出，追击修正必须缩短与敌人的距离且普攻范围内退出。指挥家控制、清理精确接近、技能运行、当前 tick 施放前 AIMING、非地面／流体／攀爬等状态优先使用原逻辑。候选复用 safeApproach 并采样流体与寻路 malus，失败回退原路点。状态只归 AI 实例，不新增持久化、网络、注册项或资源。
- 项目修改全部通过 IDEA 完成；`SweeperAi` 按修改行格式化，关闭导入优化和成员重排。源码 index 诊断 analysisFresh=true、analysisTimedOut=false、未截断，0 错误、7 条均为修改前已有警告；系统文档同步鸟群范围、参数、生命周期、优先级和维护陷阱。语义引用确认既有 Sweeper 委托入口与唯一局部转向调用，无公共接口删除或迁移。
- IDEA 项目编译 `isSuccess=true`、`problems=[]`；IDEA Data 配置退出码 0。生成目录 25 份文件（含缓存）的 IDEA 读取文本前后完全一致，无新增／删除，未手工修改生成输出。Data 预览有原有终端与 Sodium 警告，输出被截断，不据此声称全量日志无警告。范围内 `git diff --check` 退出码 0，保留既有 LF／CRLF 策略提示。
- 更新 `docs/systems/entities-ai.md`、`README.md` 和本记录；修改前 Java 文本备份位于 `backups/sweeper-flocking-2026-10-11`，保留来源相对路径，读取内容与修改前快照一致。已有 200 个暂存条目的状态前后相同，未手动暂存、提交、推送或重置，保留其他任务修改。未执行测试、启动／重启游戏或热替换；群体幅度、拥挤／窄路、台阶及战斗到位仍待游戏验收。

## 2026-10-11：清道夫攻击距离死区与飞扑渲染

- 修正已确认的两处施放阻断：原版近战碰撞范围与普攻 2 格脚部距离不一致，可能使 AI 选择普攻但技能拒绝、飞扑也拒绝；战斗导航的方块停止容差 1 可能让实体停在普攻范围外。`AttackSkill.isWithinAttackRange` 开放为共享谓词，`SweeperAi` 的普攻／飞扑选择和 `LeapSkill.canUse` 共用它；仅战斗追击的停止容差改为 0。原攻击半径、伤害、连段、冷却与飞扑轨迹参数保留。
- 按用户要求移除 `SweeperRenderer` 中飞扑专属的 root 俯仰、0.1 秒动作权重过渡及头骨俯仰补偿，保留普通头部转向、三阶段动作资源、粒子和运动机制。同步实体／动画文档、系统索引和本记录。
- 项目编辑全部通过 IDEA 完成；四份 Java 已按修改行格式化，关闭导入优化与成员重排。最终四份源码的 index 诊断均 analyzed、未截断，0 错误；16 条警告均为修改前已有问题。IDEA 语义引用确认范围谓词的五个调用，AI／普攻／飞扑未残留旧近战分界，特殊俯仰符号检索无残留。
- IDEA 项目编译 `isSuccess=true`、`problems=[]`；IDEA Data 配置退出码 0。生成目录 25 份文件（含缓存）的 IDEA 读取文本前后完全一致，无新增／删除，未手工修改生成输出。Data 预览含原有终端与 Sodium 警告，输出被截断，不据此声称全量日志无警告。范围内 `git diff --check` 退出码 0，保留既有 LF／CRLF 策略提示。
- 四份修改前 Java 备份位于 `backups/sweeper-attack-render-2026-10-11`，保留来源相对路径。已有暂存条目状态前后相同，未手动暂存、提交、推送或重置；其他任务的文档更新原样保留。未运行测试、启动／重启游戏或热替换；宽体／移动目标、障碍寻路和飞扑视觉尚未游戏验收。

## 2026-10-11：模型特效工程、部件渲染、原生快捷键与独立窗口缩放

- 模型层级单击仅选择并刷新检视，使用原生 TreeList 双击打开 UV。按已确认的部件粒度保存 `shading`（默认关）、`doubleSided`（默认开）与 `insideOut`（默认关），移除通用预览阴影开关；检视、层级与视窗右键共用会话历史。负尺寸翻转采样三角绕序、法线及对应 UV，原几何坐标、位置、大小与 TRS 不变；OBJ 输出同样保留翻面。
- 工程显示改为“模型特效项目”，加载项目支持 `.lcmodelproj.json`、`.lcmodel.json` 和 `.obj`；定义／OBJ 导入采用来源文件名但不作为工程保存目标。复用原生“导出特效（仅定义）／导出到 FXPack／管理 FXPack”菜单，最终序列化最新模型为 `lobecorp_model_emitter`，采用工程名，不携带预览强制配置。OBJ／模型 JSON 保留在模型数据菜单。派生 `assetDependencies` 使原生 FXPack 资源扫描与 GC 能发现字节数组快照中的贴图引用。
- 14 个扩展快捷键与模型撤销／重做、全选、复制、删除、框选、取消、时间轴删除关键帧统一进入原生 Keymap，支持主／副键、搜索、冲突与重置。旧绑定仅 bindIfUnset 迁移一次，保留原生覆盖；旧页仅保留总开关。移除模型及继承时间轴的固定按键监听，动态帮助读取实际绑定；保留输入、Dialog、焦点、拖动和项目资格。模型通用编辑动作复用原生 ID，关闭或构造失败安全清理注册。
- 独立窗口的“视图大小”原先先应用当前标签的宿主偏好，再忽略 OS 窗口缩放；共享窗口与按标签保存的偏好不一致会使窗口回停靠。新 ViewMenu／AppearanceSettings Mixin 仅对 FXEditor（含模型）菜单建立 finally 恢复的同步作用域，保留当前宿主并复用原生 clamp／resize／保存；重复选择已保存倍率也能按实际窗口应用。普通设置 Apply 和其他编辑器保留原行为，不新增每窗口独立倍率。四个操作位点经当前依赖字节码确认，各匹配一次。
- 修改的 26 份 Java：模型数据与渲染为 `PhotonModelAsset`、`PhotonModelEmitterConfig`、`PhotonModelEmitter`、`PhotonModelEmitterRuntime`、`PhotonModelPreviewMaterialUtil`、`PhotonModelSamplerUtil`、`PhotonModelIOUtil`；模型 UI／工程为 `PhotonModelHierarchyView`、`PhotonModelEditor`、`PhotonModelUVCanvas`、`PhotonModelTimelineView`、`PhotonModelProject`、`PhotonModelProjectEditor`、`PhotonModelTextUtil`、`PhotonModelExportUtil`；原生扩展为 `PhotonEditorSettings`、`PhotonEditorShortcutSettings`、`LcPhotonEditorActions`、`PhotonEditorShortcutUtil`、`PhotonEditorTools`、`PhotonEditorTextUtil`；Mixin／窗口适配为 `FXEditorMixin`、`FXProjectMixin`、`ViewMenuMixin`、`AppearanceSettingsMixin`、`PhotonEditorViewScaleUtil`。另在 `META-INF/lobecorp.mixins.json` 登记两个 client Mixin，更新模型特效、Photon 编辑器、修复对照、系统索引和本记录五份文档。
- 全部项目编辑通过 IDEA 完成；26 份 Java 已按修改范围格式化，关闭导入优化与成员重排，资源／生成输出未自动格式化。最终 26 Java、Mixin 配置与四份功能文档的 index 分批诊断均 analyzed、未截断，0 错误／0 警告；本记录另行检查。已核对原生双击、命令作用域、旧调用移除、导出及贴图扫描引用。IDEA 项目编译两次均 isSuccess=true、problems=[]；第二次覆盖订阅清理的最后调整。
- IDEA Data 配置退出码 0，六个 provider 完成；HashCache total／old／new 均 19，written=2、removed=0。生成目录 25 份文件前后无新增／删除，仅 en_us、zh_cn 及两份缓存变化；中英文各由 1107 键变为 1113 键，各新增 6 键、修改 13 键、无删除。生成输出只读核对，未手工改写。Data 保留环境终端、Sodium／Iris、可选类与 refmap 警告，不宣称日志无警告。
- 两份文档写入曾因 Windows 用户映射区域报错而截断末尾，已通过 IDEA 按修改前读取的原文恢复；落盘尾部、全文差异与诊断复核完成，无原内容丢失。IDEA 保存期间曾产生 `docs/systems/photon-editor.md~`，最终已无该临时文件，未手动删除或移动；未另建人工文件备份。范围 git diff --check 退出码 0，保留既有 LF／CRLF 策略提示。
- 保留用户既有暂存／未暂存修改，未提交、推送或手动暂存。5 个新 Java 由 IDEA 自动加入索引；尝试仅撤回新 ExportUtil 的工具自动暂存被自动审批拒绝，理由为缺少这一具体索引变更的可信用户授权并需保护现有暂存，随后停止，文件和索引保留。未运行游戏、热替换、测试或额外资源重载；独立窗口真实缩放、Keymap 焦点组合、检视／右键、定义重开和 FXPack 贴图发布仍待客户端验收。

## 2026-10-10：简单附件与复杂状态分域同步

- 用户确认后实施：单值或几个固定基础值沿用 Neo 附件同步，复杂附件使用所属功能域网络包。`ATTACK_COMBO` 与 `ENTITY_GRAB` 的同步投影保持 Neo；`REASSEMBLY_PROGRESS` 无客户端消费，继续服务端使用。五个复杂附件取消 `.sync(...)`，注册 ID、持久化类型及存档 Codec 保持。
- 新增 `EntitySkillDataSyncPayload` 和 `EntitySkillDataSyncUtil`：资格、技能组及冷却首次完整替换，后续从现有修改入口发送变化键与删除键。资格显式 `false` 与删除覆盖分别编码；冷却沿用 Long 到期时间与既有速度计时，不同步 Clock 或技能运行态，不增加脏容器或细分附件。
- 新增 `ConductorEntityDataSyncPayload`：单位字段变化、单位移除及能力资格变化由 `ConductorAttachmentUtil` 发送，`ConductorData.commit` 对比原有目录摘要并保留修订号。目录 `ConductorSnapshotPayload`、技能生命周期 `EntitySkillSyncPayload`、动画和命中框协议保留。`LcPayloads` 协议版本由 11 升至 12，新增两个 S2C 登记。
- 新增 `EntityDataSyncEvents` 的首次追踪、本人复活与换维度完整快照入口；登录由既有 `ConductorGameplayEvents` 唯一监听先处理目录，再分发本人附件。当前 NeoForge 的 StartTracking 位于生成 bundle 发送之后；Clone 不用于发包，初始快照只读取已有状态或空默认。
- 已通过 IDEA 修改 10 份 Java 文件与 7 份系统文档，并按代码修改行格式化，关闭导入优化及成员重排。10 份 Java 的最终 index 诊断均 analyzed、未截断，0 错误；16 条警告分别为 `EntitySkillUtil` 原有 13 条与 `ConductorData` 原有 3 条，新包、同步工具及事件无新增警告。IDEA 语义引用检查覆盖复杂附件写入口及 `syncUnit` 调用，未残留五个复杂附件的直接 Neo `syncData`；事件保留唯一订阅。
- IDEA 项目编译 `isSuccess=true`；IDEA Data 配置退出码 0。生成目录 25 份文件（含缓存）前后 SHA-256 全部一致，无新增／删除，未手工修改生成输出。Data 预览有原有终端／Sodium 提示，全量临时日志不在 IDEA 可读根内，未据截断预览宣称日志无警告。范围内 `git diff --check` 退出码 0，保留既有 LF／CRLF 策略提示。
- 未执行游戏、测试、运行中 Client 的热替换或多人同步验收；首次追踪／复活／换维度时序依据当前依赖源码核对，尚无联机实测。保留用户既有修改；4 个新文件由 IDEA 自动加入索引，其中 3 个后续修改仍未暂存，未手动暂存、提交、推送或重置，未新建文件备份。

## 2026-10-10：清道夫低弧飞扑与三阶段动作

- 用户连续选择 A 后明确要求开始：采用原版长跳式临时忽略阻力、弧顶高于较高起终点 1～2.5 格、起跳锁定目标当时位置、不在空中追踪；保留 5 tick 前摇。原先移除固定仰角限制的修改继续保留，12 格范围、水平／垂直初速上限 1.8／1.4、冷却、飞行接触伤害、抓取和落地伤害未调整。
- LeapSkill 改为按目标脚底 XZ 搜索附近支撑面；新增 EntityLeapUtil 只负责无阻力离散数学求解，按原版先位移、后扣实际重力的顺序枚举整数 tick，选择最接近期望弧高的有效解。超速或无解时拒绝，不再裁剪速度后仍当作可达；施放门槛、瞄准、前摇和实际起跳时检查落点及整箱路径。
- 落点向支撑面内探入 0.01 格并要求末 tick 仅发生向下碰撞，避免精确贴地未触发 onGround 而多前进一 tick。起跳只写一次初速度；Sweeper.travel 仅在飞扑 ACTIVE 阶段传入零移动输入，保留原版位移、重力及碰撞。原有 40 tick 强制下降兜底保留。
- 本次 runtime 保存原 shouldDiscardFriction，结束、取消及后摇结束幂等恢复；卸载沿原 EntityLeaveLevelEvent 清理链进入取消，无新增事件或网络。恒重力空气模型排除缓降、漂浮、无重力、流体、攀爬、非单位方块速度倍率，以及路径中已知的蛛网／浆果丛／细雪。第三方方块运行副作用、原版小于 0.003 的速度分量归零及环境在起跳后变化仍属于精度和适用边界。
- SweeperAnim、Sweeper 及运行动画 JSON 拆分 0.25 秒蓄力、LEAP_FLIGHT 起身后保持和 LEAP2 落地；三个动作采用 OVERLAP 对齐阶段边沿。SweeperRenderer 通过既有 action 控制器选择飞行根俯仰，使用插值角度与 0.1 秒过渡，扣除头骨重复俯仰。旧 resources/entity/sweeper 源素材和 bbmodel 未改，重导出必须保留新的飞扑分段。
- 新增 EntityLeapUtilTest 的 6 项 JUnit 数学用例，覆盖解析解、独立逐 tick 积分的上下坡落点、等距优先短时、初速上限、下降着地及非法参数；已随 compileTestJava 编译，未执行测试。通过 IDEA 格式化修改范围，关闭导入优化和成员重排；七份源码／资源诊断 complete=true、0 错误，31 条均为既有警告，数学工具与测试无诊断。移除公式的旧符号检索无残留，范围内 git diff --check 退出码 0。
- IDEA 项目编译 success=true，实际执行 compileJava／compileTestJava；编译器仍提示既有过时 API 和未经检查操作。IDEA Data 配置退出码 0；25 份生成文件（含缓存）前后 SHA-256 全部一致，无新增／删除，未手工修改生成输出。Data 全量临时日志不在 IDEA 可读根内，未据截断预览宣称全量日志无警告。
- 同步实体／动画文档、系统索引与本记录；修改前文件备份在 backups/sweeper-leap-parabola-2026-10-10。未执行 JUnit、热替换、重启 Client 或游戏内飞扑验收。IDEA 自动暂存了本次新建的 EntityLeapUtil 与测试文件，限定撤销自动暂存的两次尝试曾被自动审批拒绝（理由为缺少取消暂存的用户授权）；用户随后明确选择 A，已通过 IDEA 仅撤销这两个文件的暂存，代码内容不变，两个文件保留为未跟踪。其余暂存条目通过 cached --raw --no-renames 前后对比完全一致；未提交、推送或重置其他内容。

## 2026-10-10：模型旋转、缩放与轴心的 Client MCP＋Debug 验收

- 按用户要求通过 IDEA 原有 Client 配置启动 Debug，进程 PID 为 42176；Minecraft MCP 的只读玩家查询及截图成功，并与本次 Debug 使用同一游戏进程。已查看 1600×1000 的初始截图，确认检视面板显示旋转、缩放与轴心字段。
- 使用原生 TransformGizmo 的受控射线进行 Y 轴约 −30° 旋转和 X 轴约 1.5 倍缩放；两项操作的轴心世界位置误差均为 0，原始顶点不变。各自调用模型会话 undo／redo 后，完整资产 JSON 分别与操作前／后快照相等。
- 轴心移动通过原生 Screen 的 MouseButtonEvent 点击 AXIS_X，受控设置 renderer.lastHit 后调用 gizmo.updateFrame，再由 Screen 分发释放事件。世界 X 轴移动 0.3 后，pivot 约为 `(0.423205, -0.15, -0.05)`，基础 translation 补偿约为 `(0.126795, 0, 0.15)`；8 个顶点经基础部件矩阵变换的位置最大误差为 0，轴心 undo／redo 的完整 JSON 也与相应快照相等。
- 原生 saveAsProject 弹窗通过 Screen 鼠标事件确认后，文件存在、currentProjectFile 正确、dirty=false；再由 PhotonModelProject.TYPE 读盘，完整 JSON 相等。调用 editor.exit(null) 后，工程清空、场景控制器清空及标签移除均为 true；从原标签的 fileMenu.openProject(file) 重开后，完整 JSON 相等、dirty=false、场景控制器存在、标题正确，旋转、缩放与轴心值均保留。
- 保存重开后再次拖动轴心，通过原生 Screen 右键取消，完整 JSON 精确恢复保存快照，undoStack 数量不变，drag=false、liveBefore=null、cameraMoving=false。轴心编辑中切换 ROTATE 并执行 Preview.updateFrame 后，轴心模式退出，Gizmo 目标恢复为 PhotonModelPartTransform，dirty=false。
- 临时工程保留在 `run/client/ldlib2/assets/pivot-acceptance-20261010-a064452e-580e-4f57-a5c4-c14969ed3055.lcmodelproj.json`；已查看的初始截图位于 Client 游戏目录的 `screenshots/pivot-acceptance-initial-20261010.png`。本轮 IDEA Client 启动日志全量检索 ERROR／FATAL／Unreported exception／EXCEPTION_ACCESS 结果为空。
- 保存弹窗、重开与[最终截图](../../run/client/screenshots/pivot-acceptance-final-20261010.png)均已写盘并实际查看，尺寸均为 1600×1000；最终画面清晰显示 Y=−30°、X 缩放约 1.5、轴心约 `(0.4232, -0.15, -0.05)`，旋转环位于偏离模型中心的新轴心。
- 40 个临时调试对象引用已清理，只移除本轮两个代理断点，原两个禁用的用户异常断点保留。Debug 恢复 running，Minecraft MCP connected=true、port=9876、processAlive=true，玩家只读查询再次成功，日志全量错误过滤再次为空；重开验收标签与唯一临时工程保留供用户查看。
- 上述输入通过调试求值、受控射线与原生 Screen 事件链执行，没有使用桌面控制或物理鼠标，不能视为全部真实输入验收；参数输入字段只确认了外观，未验收字段输入修改。此次只更新两份系统文档，无生产代码修复、运行配置或 JDK 修改，未重新编译、运行 Data 或执行 JUnit；上一条记录的静态检查与当时未运行 Client 的事实继续保留。

## 2026-10-10：模型部件旋转、缩放与可视轴心

- 用户确认 A：移动轴心时模型保持原位。PhotonModelEditor 的普通部件检视增加 XYZ 度旋转、缩放及原生轴心编辑 Toggle；数值轴心与拖动共用 PhotonModelEditUtil.movePartPivot，以 `(R·S−I)d` 补偿基础平移，保持部件及子部件基础世界矩阵和原始顶点不变。已有动画键不重写，后续使用新轴心。
- PhotonModelPreview 接入 PhotonModelPartTransform，实现锁定 LDLib2 26.1.2.42 的原生 ITransform。部件 Gizmo 和变换弹窗写基础 TRS，旋转／缩放绕轴心；点／边／面保留选区中心和几何候选。父位置走完整矩阵，父旋转走四元数链，不分解含剪切矩阵。新增青色轴心标记，原生旋转／缩放工具、非部件模式及动画模式退出轴心编辑。
- 连续编辑保留拖前副本与一条释放历史；原生右键取消后通过 PhotonModelEditSession.cancelLiveEdit 精确恢复快照，不留下撤销记录或坐标往返误差，避免同时触发相机移动和右键菜单。父级奇异矩阵禁用部件手柄，自身零缩放仍可通过部件缩放恢复；轴心及几何编辑继续要求完整矩阵可逆。
- PhotonModelTextUtil 增加四个中英键；版本 1 JSON、原 OBJ 路径、来源发射器和既有动画轨道保持原接口。PhotonModelRegressionTest 新增三项回归，覆盖轴心重定位、父子矩阵／负缩放／顶点保持、动画轨道不重写，以及原生适配器在复杂父变换中的采样和 JSON 往返。
- IDEA 修改范围格式化完成，关闭导入优化和成员重排；七份修改 Java 文件诊断 complete=true、0 错误、3 条原有未使用方法提示。编译 success=true，实际执行 compileJava／compileTestJava；新增回归已编译，未执行。语义引用确认数值与拖动轴心共用工具、部件适配器及连续取消的调用，范围内 git diff --check 退出码 0。
- IDEA Data 配置退出码 0，完整输出 BUILD SUCCESSFUL；19 份资源写入数为 2。25 份生成文件（含缓存）只有 en_us／zh_cn 与两份缓存变化，无新增／删除；两种语言均包含四个新增键，未手工修改生成输出。日志保留第三方可选类缺失提示，本轮未扩大范围处理；模型文档的原有表格弱格式提示保留，未格式化原表格。
- 同步模型系统文档与公开入口说明。保留既有修改和索引状态，新适配器由 IDEA 自动加入索引，未手动暂存、提交、重置或推送，未新建备份。本轮未启动、重启或热替换 Client，轴心外观、真实鼠标操作、取消、撤销及保存重开尚未实际界面验收。

## 2026-10-10：Photon／LDLib2 发布版本升级与兼容适配

- 在 Minecraft 26.1.2／NeoForge 26.1.2.100／Java 25 基线上，将 Photon 26.1.2.2 升为 26.1.2.3、LDLib2 26.1.2.39 升为 26.1.2.42；按 Photon 实际发布 JAR 的最低依赖将 KilaGraph 26.1.0.14 升为 26.1.0.15。三个发布均标为 Beta，未升级 Minecraft、NeoForge 或其他无关模组。官方更新日志、逐领域内容和发布证据汇总在[依赖升级](photon-dependency-upgrade.md)。
- mods 元数据迁入 gradle.properties；build.gradle.kts 的既有 processResources 从属性和版本目录展开 neoforge.mods.toml，修正 issueTrackerURL 的顶层位置及模板表名。保留五项既有 required／BOTH，补充 Photon、LDLib2、KilaGraph 三项 required／CLIENT；展开结果为八项 required，版本范围正确且无残留占位符。源码审计未将全部开发依赖机械声明为强制依赖。
- 为三个 LowDrag Maven 组和新增的 dev.vfyjxf:taffy 精确指定 Firstdark／Central。前两次 IDEA 编译被远程仓库连接超时阻断；修正路由后 IDEA 同步成功，新版主 JAR／源码及 Taffy 进入类路径。
- PhotonModelEmitter、PhotonModelPreviewMaterialUtil 改用 geometry／attributes／indices、quadPaired 与新偏移；保留面拓扑、变换、色调、透明度、法线、UV、光照及阴影路径。PhotonModelRegressionTest 的既有有限性断言同步为分别检查两类数组，未新增测试。
- TextFieldMixin／TextAreaMixin 的粘贴入口迁入 onExecuteCommand；ModularUIWidgetMixin 的 SAVE 路由迁入 dispatchCommand。PhotonAnimationRecordMixin 随原生历史序列化迁移到静态 save，保持基础姿态保护和自定义 snapshotter。实际发布 JAR 的调用描述符、次数及相关局部变量经 MixinMCP 核准。
- PhotonAnimationPlayerAccess／PhotonAnimationPlayerMixin／TimelinePlayerMixin 与 PhotonAnimationRuntime 同步 double 求值和 localTime，移除小数截断。模型原生传输栏的播放状态、步进、末尾跳转及四项默认播放动作接到模型时钟；Geo 独立模拟倍率在新版根 tick 恢复时与 rate 相乘，保留 Timeline 与速度轨道原语义。
- PhotonAnimationTimelineMixin 将新版逐帧和跳末尾接到命名动画独立时钟，保留小数末尾、暂停和排队命令清理，普通 FX／模型路径不变。首次统一编译发现并修正既有测试源码的一处旧网格调用；最终 IDEA 编译 success=true，实际执行 compileJava 与 compileTestJava，未执行测试。
- 修改行通过 IDEA 格式化，关闭导入优化和成员重排。最终全范围诊断覆盖 244 份 Photon 相关主源码／测试源码，complete=true、0 错误、31 条原有代码提示；最初发现的三条历史快照注入错误已修复。最终确认编译 success=true；TOML 模板保留占位符 Logo／模组 ID 的 IDE 解析警告，实际展开及 Data 加载通过。Mixin 审计覆盖 Photon 的 61 个目标 Mixin、48 个目标类、67 个宿主方法与 81 个字符串目标，并核对 LDLib2 文本、命令、窗口、历史、检视、资源、场景与 Gizmo 目标。语义引用与过时调用检查未发现旧网格／长整数求值入口遗留；范围内 git diff --check 退出码 0，仅保留既有 LF／CRLF 策略提示。
- IDEA Data 配置退出码 0，完整输出 BUILD SUCCESSFUL；19 份资源写入数为 0。生成目录 25 份文件（含缓存）前后 SHA-256 完全相同，无新增、删除或内容变化；未手工修改生成输出。日志仍有第三方 JarJar／refmap／可选类及图形驱动提示，本轮未扩大范围处理。
- 同步系统索引、Photon 编辑器、模型特效、命名动画、发射器发射器、修复对照、粒子渲染和客户端 UI 的当前版本／适配说明。保留原有工作区和暂存内容；新文档由 IDEA 自动加入索引，未手动暂存、提交、推送或重置，未新建备份。本轮未运行测试，未启动、重启或热替换 Client，未进行界面、渲染和专用服务器验收。

## 2026-10-10：移除清道夫飞扑角度限制，轨迹优化仅研究

- 仅修改 LeapSkill 的固定角度约束：删除最低 10°／最高 70° 常量及弹道裁剪，初始垂直速度保留现有 ±MAX_VERTICAL_SPEED 上限；朝向直接取当前速度的俯仰，沿用 EntityFacingUtil 的头身转向限速与旧值插值。原目标资格、12 格范围、时间选择、阻力、重力、技能时序、伤害和抓取均保持本轮修改前实现。
- IDEA 修改前核对了已有 Git 差异和两处常量的语义引用；修改后四处预期源码差异与修改前快照精确对应，未改动其余源码。固定角度常量及其临时计算值在手写 Java 中的检索结果为 0。
- IDEA 只格式化弹道裁剪和朝向计算所在行，关闭导入优化与成员重排。LeapSkill 无错误，保留修改前已有的 5 条 Level 资源使用提示，无新增警告。
- IDEA 编译 success=true，实际完成 compileJava；编译输出仍包含既有 Sweeper 过时 API 及未检查操作提示。Data 配置退出码 0，生成目录 25 份文件（含缓存）前后 SHA-256 相同，无新增、删除或内容变化；未手工修改生成资源。
- 抛物线表现当时只完成只读研究。已核对原版 travelInAir、setDiscardFriction 与 LongJumpMidJump；当轮的运动模型、弧高、时长、可达性处理及动画对齐仍待用户讨论，未实施。后续已确认并实现的方案见 [实体与 AI](entities-ai.md#清道夫飞扑低弧轨迹)。
- 同步 entities-ai.md 与 entity-grab.md 的当前角度规则。原 LeapSkill 备份位于 `backups/sweeper-leap-angle-2026-10-10`；保留已有工作区与索引状态，未暂存、提交、重置或推送。未执行测试或游戏验收。

## 2026-10-10：独立回归 main 迁移为 JUnit 测试

- 用户选择 JUnit 方案；版本目录加入 JUnit Jupiter 6.1.3 BOM、Jupiter 与 Platform Launcher，依赖限定为 test。Gradle `test` 使用 JUnit Platform，通过 ModDevGradle 的 `addModdingDependenciesTo(sourceSets.test.get())` 提供测试编译所需的 Minecraft 依赖。
- 移除 PhotonAnimationRegression、PhotonModelRegression、PhotonModelMeshRegressionCheck、PhotonModelTopologyRegressionCheck 的四个 main 入口；在 `src/test/java` 提供四个 Test 类、共 26 个 `@Test` 方法。网格与拓扑类通过 IDEA 语义移动／重命名，独立模型检查迁入测试源集，断言改用 JUnit。
- PhotonAnimationRegression 的游戏内检查接口及调用保持原状；PhotonModelRegression.runLoaded 保留已加载模组环境中的大模型 NBT／旧快照检查，不放入普通 JVM 测试。历史 main 执行记录不作为本轮测试通过证据。
- 最初两次 IDEA 编译受测试依赖解析超时影响，第二次明确记录 maven.neoforged.net 连接超时；Maven Central 的 JUnit BOM 请求正常。仅将 JUnit 及其测试依赖组通过 exclusiveContent 限定到 Maven Central，随后 IDEA 编译 success=true，完成 compileJava、compileTestJava 和 testClasses。未断言超时的底层网络原因。
- IDEA 完成涉及 Java 的格式化，关闭导入优化与成员重排；六份 Java 的完整诊断为 0 错误、0 警告。build.gradle.kts 保留原有 internalTestBundle 的 Zip 完整限定名警告，不在本轮处理。
- IDEA Data 配置退出码 0；生成目录 25 份文件（含缓存）前后 SHA-256 相同，无新增、删除或内容变化。未手工修改生成输出。
- JUnit 注解已在 Lobecorp.test 模块正确解析，但 IDEA/MCP 的测试发现与文件运行入口仍返回空列表，原因尚未确认；不将源码的 26 个测试方法描述为 IDE 已发现。未执行 JUnit 测试、已加载环境检查或游戏运行。
- 同步系统索引、动画、模型及编辑器文档。原四份源码备份位于 `backups/junit-migration-2026-10-10`；保留已有工作区修改，未手动暂存、提交、重置或推送。IDEA 自动登记的新文件以 Git 状态为准。

## 2026-10-10：骨骼添加入口与 UV 模式单选

- 仅修改 PhotonModelEditor 及模型系统文档；不改模型 JSON、会话数据结构、骨骼父级规则或原有 UV 选择逻辑。
- 层级与中央视图共用的右键“添加”菜单增加“添加骨骼”，原动画检视按钮也复用同一动作。通过现有 session.addBone
  创建、记录历史并选中骨骼后，切换绑定姿态、清理临时姿态并打开已有骨骼检视；零动画片段不阻断绑定属性构造。
- UV“顶点／面”由两个操作按钮改为原生 Toggle + ToggleGroup 横向互斥单选，默认顶点、禁止空选；仅新选中项更新画布，旧项取消回调不回写模式。沿用控件高度、操作最小宽度、翻译键与模式切换清空
  UV 角点选区的行为。
- IDEA 修改范围格式化完成，关闭导入优化与成员重排；Editor 无错误或警告。语义引用确认共享添加菜单覆盖层级与中央视图两处调用，新增创建动作覆盖菜单与原检视按钮两处调用，UV
  模式写入统一为一处回调；只读复核了零片段检视与原生组初始化顺序。
- IDEA 编译 success=true，实际执行 compileJava；Data 配置退出码 0，完整输出 BUILD SUCCESSFUL，19 个资源写入数为 0。生成目录
  25 份文件（含缓存）前后 SHA-256 相同，无新增、删除或内容变化；未新增翻译键。
- 本轮未启动、重启或热替换 Client，未进行实际菜单点击、撤销／重做和单选外观验收；不将源码与编译检查当作游戏内通过。保留用户已有修改，未创建备份、手动暂存、提交或重置。

## 2026-10-10：模型视图骨骼辅助显示

- 范围为 PhotonModelPreview、PhotonModelSamplerUtil 及模型系统文档；不改版本 1 JSON、骨骼数据结构、绑定或动画编辑流程。
- 源码确认模型场景辅助绘制没有骨骼入口；新增关节点及父子连线，未选为半透明青色、选中为橙色加粗，关闭深度测试与写入以显示模型内部骨骼。重合父子跳过零长度线但保留点，选中关节点最后单独提交。
- SampledModel 增加实际骨骼层级姿态映射，保留五参与六参兼容构造入口；蒙皮矩阵复制姿态后计算，避免逆绑定污染骨骼位置。预览复用既有缓存，几何模式读取绑定姿态，动画模式共用网格的时间、片段、循环及临时姿态，不重复模型校验或采样。
- IDEA 修改范围格式化完成，关闭导入优化与成员重排。Sampler 无诊断，Preview 仅保留既有 getTransformGizmo
  未使用警告；模型文档保留既有表格弱警告。语义引用确认 SampledModel 15 处引用、采样方法及骨骼姿态访问器各一处调用，无需迁移其他构造调用。
- IDEA 编译 success=true，实际执行 compileJava；Data 配置退出码 0，完整输出 BUILD SUCCESSFUL，19 个资源写入数为 0。生成目录
  25 份文件（含缓存）运行前后 SHA-256 相同，无新增、删除或内容变化。
- 范围内 git diff --check 退出码 0，仅既有 LF／CRLF 策略提示。保留用户既有修改与暂存状态，工作区仍为 dev、489 条、183 暂存／468
  未暂存／2 未跟踪、0 冲突；未创建备份、手动暂存、提交或重置。
- 本轮未启动、重启或热替换 Client，未做游戏内骨骼外观、动画跟随或性能实测；此项只补显示，不增加视窗骨骼拾取、拖动、头尾或长度定义。

## 2026-10-09：UV 翻转填充、绘制缓存与环切提示

- 范围仅为 PhotonModelUVCanvas、PhotonModelEditor、PhotonModelPreview 及模型系统文档；未改模型协议、资产数据或共享材质。
- 确认原生 GUI_TRIANGLE 默认开启剔除，原填充原样提交三角绕序；另按 UV 边界三角化会拒绝跨边折叠或自交 UV。现复用模型几何面的三角拓扑映射面角 UV，逐三角统一 GUI 正面绕序并兼容反射 pose，退化三角单独跳过；简单且未折叠的重合面按边界去重，保留一次 25% 填充；折叠面按实际 UV 三角覆盖去重，避免相同边界不同几何对角线借用错误填充。镜像负零仅在缓存副本中规范化。
- UV 几何去重与三角拓扑按 revision／部件／纹理映射缓存；选择或视图变化只重建投影，静止时复用顶点列表，相同纹理映射不因选择刷新而失效。填充和网格／黑白边／角点最多两个原生 state 提交，保留原屏幕线宽、透明度、点大小、裁剪与元素色调，避免逐边逐点的重复分配和升层。
- 环切增加原生工具行按钮与弹窗帮助。模型预览挂接原生 screenTips，上层绘制且不命中，环切活动时持续显示帮助或无效边提示；Esc 取消环切且保留模型选择，关闭工作区移除控件。操作为：选部件 → 环切 → 在视窗中预览 → 鼠标靠近边 → 黄色切线 → 左键确认。
- IDEA 修改范围格式化完成，关闭导入优化及成员重排；UVCanvas／Editor 无诊断，Preview 仅保留既有 getTransformGizmo 未使用警告。语义引用确认 refreshTextures 的两处工作区调用与 isLoopCutActive 的两处调用，范围内 git diff --check 退出码 0，仅既有 LF／CRLF 策略提示。
- IDEA 编译 success=true，实际执行 compileJava；原有 Data 配置退出码 0，完整输出 BUILD SUCCESSFUL，19 个资源写入数为 0。生成目录 25 份文件（含缓存）运行前后 SHA-256 相同，无新增、删除或内容变化。
- 当前 IDEA 无活动 Client Debug；本轮未启动、重启或热替换游戏，也未做镜像／折叠外观、环切物理交互及 UV 帧率实测，不把源码优化视为已测得性能提升。
- 保留用户既有修改与暂存状态，工作区仍为 dev、489 条、183 暂存／468 未暂存／2 未跟踪、0 冲突；未创建备份、手动暂存、提交或重置。

## 2026-10-09：模型编辑器 MCP 与现有 Client Debug 联合验收

- 用户手动进入世界后接管既有 Client Debug；本轮未启动或重启游戏，未修改运行配置、JVM、JDK 或产品源码。通过原生 photon_editor 命令回调打开编辑器，并在独立临时模型工程验收。
- MCP 可连接游戏、读取玩家并截图；当前适配的 execute_command 返回 no command method found，open_chat 返回 ChatScreen instantiation failed，点击返回成功但未触发所测按钮。窗口扩大到 2560×1494 后 MCP 截图仍为 854×480 的局部画面，因此完整截图改用 Minecraft Screenshot.grab，经 Debug 在渲染线程调用。未使用桌面鼠标／键盘控制。
- 实际画面确认默认白模、左右面板、底部原生时间轴与模型信息浮窗出现。挤出弹窗外框及内容宽度均为 280 GUI 像素；变换弹窗为 280×107，局部拉伸为 280×103，输入框正常铺开。通过原生 Screen.mouseClicked／mouseReleased 验证变换取消按钮关闭弹窗。
- UV 视区实测边长 243.26251 GUI 像素；滚轮处理器将缩放变为 0.8333333 后，框选处理器选中立方体 24 个面角 UV、6 个面，选择未修改资产。添加球体后仅显示该部件 128 个面的 UV，切回 main 只显示 6 个面；添加、撤销、重做和再次撤销恢复 1 部件／8 顶点／6 面。
- 七种曲面均通过原生弹窗确认按钮生成并逐一撤销。包含原立方体的结果分别为：柱体 152 顶点／136 面，锥体 137／135，圆环 136／134，球体 122／134，弧带 42／22，柱面片及球面片各 161／134；每项均为 2 个部件。球体分段输入外观为 16／8，本轮未逐一模拟修改各分段文本。
- 环切通过弹窗进入预览，使用真实场景矩阵投影边的中点并调用鼠标移动处理器，获得 4 段计划；原生场景鼠标点击事件确认后为 12 顶点／10 面，预览清除，撤销恢复 8／6。菜单回调三角化得到 12 个面并可撤销；中心／首点／末点合并各得到 7 个顶点并可撤销。
- 使用原生 ProjectType 保存唯一临时文件 run/client/model-acceptance-1791556637203.lcmodelproj.json，再读取并 loadProject；序列化数据相等、workspace 非空、1 部件。该检查覆盖保存读取和工作区重建，不等于文件选择器、关闭标签及重启布局全流程验收。保留临时文件供复核，未覆盖用户模型。
- 截图保存在 run/client/screenshots/model-acceptance-transform-native.png、model-acceptance-stretch.png、model-acceptance-sphere-dialog.png；另保留一张 MCP 局部截图 model-acceptance-transform.png。恢复后的模型标签保留，Client 运行中；本轮临时断点已自动移除，仅原有两个禁用的用户异常断点，MCP control_mode=false。
- 限制：物理鼠标拖动及 Shift／Ctrl 组合键、均分数量及百分比输入、距离合并／消边／塌缩／造面完整菜单交互、非正方形贴图与滚动条、浮动停靠和关闭重开尚未逐项完成运行验收。既有 5006／115 项回归和编译／Data 结果见前轮，不以本轮事件调用替代真实输入验证。
- 本轮仅补充本记录与模型系统文档，未重复编译、Data 或回归。文档修改前 IDEA 检查：本记录无问题，模型文档有既有表格弱警告；保留用户已有暂存、未暂存及未跟踪内容，无新建备份、手动暂存、提交或重置。

## 2026-10-09：模型网格编辑器 Client 验收启动受阻

- 按用户“启动并验收”授权，通过 IDEA 原有 Client 配置启动 Debug 两次；未修改运行配置、JDK、JVM 参数或产品源码，未控制鼠标键盘。
- 首次在标题画面暂停，通过原生 WorldOpenFlows 打开最近修改的测试存档“新的世界 (3)”；恢复后 Minecraft.runTick 抛出 Sodium 注入方法 handler$zkm000$sodium$postRender 的 NoSuchMethodError，未进入模型工作区。
- 第二次改用项目 ClientRuntimeEvents.onClientTick 断点，原生世界加载已开始，随后 JBR 25.0.4.1+1-610.67 在 Render thread 发生 EXCEPTION_ACCESS_VIOLATION；错误帧 DirectMethodHandle$Holder.invokeStatic，证据 run/client/hs_err_pid27800.log。尚不能由此确认模型代码、断点或调试求值中的哪一项是根因，不修改 Sodium／JVM 绕过。
- 两次会话均已退出。移除本轮 agent 断点，保留原先两个禁用的用户异常断点。没有新建或修改模型工程，未做弹窗、框选、环切、保存重开或撤销的界面验收；第二次世界加载执行了原版存档保存，不声称运行目录零变化。
- 本轮仅更新模型系统文档和本记录；不重复 Data／回归，不把上一轮静态结果当成本轮界面通过。没有创建备份、手动暂存、提交或重置。下一步待确认是否由用户手动进入世界后接管，或先扩大范围排查调试环境。


## 2026-10-09：模型拓扑工具、曲面基本体与 UV 框选

- 修正挤出、变换、局部拉伸弹窗的原生外框与内容宽度不一致；UV 强制按当前部件和纹理过滤，增加空白拖动／B 框选、Shift 追加及 Esc 取消。
- 视窗右键增加合并顶点四模式、消边／塌缩、按顶点生成面、三角化；环切支持悬停预览点击确认、输入百分比和按数量均分。层级与视窗共用基本体菜单，增加球／弧带／柱面片／球面片，所有曲面含可编辑分段数。
- 新增 PhotonModelPrimitiveUtil、PhotonModelTopologyUtil、PhotonModelLoopCutUtil；几何、面角 UV、材质与骨骼权重经候选校验进入现有历史。保持版本 1、形状键拓扑锁与共享资源独立。修改 ModelEditor／Preview／UVCanvas／HierarchyView、模型翻译源及系统文档索引。
- IDEA 修改范围格式化、语义引用和诊断完成，10 个 Java 无新增错误或警告；保留既有 getTransformGizmo 未使用警告及两个回归输出日志点建议。IDEA 编译 isSuccess=true；Data exitCode=0，25 份生成文件仅中英文语言及两份缓存变化，全部新增翻译键齐全。
- 新增两个独立回归入口：PhotonModelMeshRegressionCheck 退出码 0、5006 项断言通过；PhotonModelTopologyRegressionCheck 退出码 0、115 项断言通过，覆盖高分段／闭合／绕序／UV、合并／权重／消边／造面／三角化，以及环切边界和过期拒绝。
- 交互路径复核补齐 Esc 取消 UV 框选时恢复原角点选区，以及按端点 clip.w 将环切屏幕比例转换为模型边比例；最终 IDEA 编译再次成功，三个工作区 Java 复查无新增诊断。范围内 git diff --check 退出码 0，仅既有换行策略提示；IDEA 自动登记五个新文件，未手动暂存，工作区保持 dev、489 条、183 暂存／468 未暂存／2 未跟踪、0 冲突。
- 本轮未启动、重启或热替换 Client；IDEA 当前无活动调试会话，弹窗外观、实际框选／环切预览、撤销及保存重开仍未界面验收。保留用户已有修改，未新建备份、手动暂存、提交或重置。

## 2026-10-09：正方形 UV 视区、原生滚动条与选择填充

- 按用户确认的 A 方案，仅 UV 视区固定为正方形，纹理按原始宽高比适配、居中；缩放下限改为 0.8 倍，上限取 max(1, 纹理长边像素数 / 2)。PhotonModelUVCanvas 的绘制、裁剪、选点、拖动和网格共用该坐标换算。
- 增加原生 Scroller.Horizontal／Vertical，滚动范围按纹理每个轴的实际缩放跨度计算，滑块表示可见比例；缩放、布局和纹理尺寸变化同步滚动条。原生零值范围会产生 NaN，故保留规范化 0～1 范围，无溢出时禁用滚动条及其交互子控件，内容居中。
- UV 顶点改为黑框白芯方形，点尺寸保持原值；网格设为 50% 透明度。UV 顶点关联的面与模型视图选中的面在 UV 画布增加约 25% 不透明度的填充，完全重合面去重，复用现有多边形三角化与 LDLib2 GUI 三角管线。折叠或自交 UV 无法三角化时保留边点编辑，不向渲染线程抛出异常。
- 本轮仅修改 PhotonModelUVCanvas 及相关文档；IDEA 格式化修改范围，Java 诊断为 0 错误、0 警告，语义引用确认缩放、选面和拖动使用统一换算，滚动条刷新覆盖初始化、布局、纹理与缩放变化。
- 未编译、运行 Data、热替换或重启 Client；滚动条实际操作、非正方形贴图外观和填充效果未进行游戏界面验收。保留已有修改，未创建备份、暂存或提交。

## 2026-10-09：UV 视图缩放与固定屏幕线宽

- 按用户确认的 A 方案，PhotonModelUVCanvas 接入 LDLib2 滚轮事件，以鼠标位置为中心缩放到 0.25～16 倍；纹理、网格、绘制与拾取共用正反坐标换算，保留 UV 像素吸附、范围约束和原撤销事务。拖动 UV 时屏蔽滚轮，视图缩放不写入模型资产。
- 核对锁定 LDLib2 源码确认 DrawerHelperClient.drawLines 参数实际为半线宽，不能把 GUI 单位直接当作完整屏幕线宽。现按 GUI 倍率换算为总宽 3 个屏幕像素，白色内线 1 像素，黑边两侧各 1 像素，UV 缩放不改变粗细；已选两层各约 50% 不透明度，未选各 40%。完全重合边合并绘制，保留已选优先；点标记尺寸不变。
- 通过 IDEA 格式化修改范围，PhotonModelUVCanvas 诊断为 0 错误、0 警告；语义引用确认滚轮处理器和坐标换算用于绘制、选点、选面及拖动。同步模型系统文档。
- 本轮未编译、运行 Data、热替换或重启 Client，实际滚轮交互和不同 GUI 倍率下的线宽尚未界面验收；未创建备份、暂存或提交，保留已有修改。

## 2026-10-09：UV 黑白边线

- PhotonModelUVCanvas 原有白色 UV 线在白模贴图上对比不足，改用 2 GUI 像素黑色外描边加 1 GUI 像素白色内线；未选边保留降低透明度的区分，像素网格、角点尺寸与编辑逻辑保持不变。
- 通过 IDEA 格式化修改行，Java 文件诊断为 0 错误、0 警告；同步模型系统文档。
- 本轮未编译、运行 Data、热替换或重启 Client，黑白描边的实际界面效果未验收；保留已有修改，未创建备份、暂存或提交。

## 2026-10-09：边模式选择反馈

- 范围：PhotonModelPreview 的边模式辅助线从 1.5 加粗到 2.5 屏幕像素，已选边独立使用 4 屏幕像素及不透明橙色，增强白模上的颜色与粗细反馈；其他选择模式、点大小和拾取逻辑保持原值。
- 同步模型系统文档。本轮通过 IDEA 格式化修改行并读取 Java 诊断，无新增错误或警告；保留既有 getTransformGizmo 未使用警告。
- 本轮未编译、运行 Data、热替换或重启 Client，新的视觉效果尚未在运行界面验收；保留原有修改，未创建备份、暂存或提交。

## 2026-10-09：模型工作区 Debug 事件链验证

- 经用户明确授权，通过原有 Client 配置运行 Debug，并在 LDLib2 渲染线程断点调用现有 open 接口创建未保存的临时模型标签；窗口仍有两个标签，原特效标签保留。未改运行配置、JVM 或 Java 源码；本轮仅更新本记录与模型系统文档。
- 运行实例确认 PhotonModelTimelineView 的父类为 FXTimelineView，使用原生 ruler、headers、lanes 和 hScroll；四个模式控件各为 38×18 GUI 像素、横向间隔 2，当前 466.707 像素视窗内互不重叠。模型信息容器有 7 行，默认模型为 1 部件、8 顶点、12 边、6 面；局部拉伸原生 Dialog 的 3 项内容正常创建并关闭。
- Debug 通过 UIEventDispatcher 分发点、边、部件和面选择／拖动／释放事件，分别选中 1、2、8、4 个顶点，坐标变化且撤销恢复。点边坐标由已捕获的真实场景矩阵投影；部件与面测试临时设置原生 renderer.lastHit，结束恢复，因此不等于真实鼠标射线或遮挡命中已经验收。首个合成松开事件缺少 dragHandler 而抛出 NPE，补齐原生字段后正常；这是调试输入缺项，未据此修改产品代码。
- 原生时间框输入 0.0625 秒得到 1.25 tick，记录关键帧时间为 1.25；调用原生 togglePlay 后恢复运行，时间推进到 545.25，暂停生效，原生 stop 归零且小数残量为 0。Esc 清空和 B 框选的事件分发通过。UIEvent.isCtrlDown 读取真实键盘状态，仅设置事件 modifiers 无效，Ctrl+A／Ctrl+D 与修饰键吸附仍未完成实际验收。
- 已撤销临时几何和动画数据，恢复 8 顶点／6 面／0 片段；停止 DragHandler 并移除本轮断点，只保留两个禁用的用户异常断点。Client 恢复运行，临时模型标签保留且未保存。没有操作鼠标、键盘或桌面窗口，没有写入用户工程或生成验收文件。
- 本轮不重复编译或 Data，先前静态结果见下节。未进行截图外观、边线可见度、窄宽换行、物理鼠标命中、Ctrl 组合键、保存重开及材质全流程复核；不将 Debug 事件链结果表述为完整界面验收。未新建备份、暂存、提交或重置。
- 文档修改前后 IDEA 诊断一致：本记录无问题，模型系统文档保留既有表格格式弱警告，没有新增诊断。

## 2026-10-09：模型原生时间轴、视窗拾取与模型信息面板

- 按用户确认的 A 方案，将右下角原生 S 浮窗改为模型信息，显示部件、顶点、边、面、选中数量、模型状态和 FPS；移除粒子种子、生命周期与重复播放操作。
- PhotonModelTimelineView 直接继承 FXTimelineView，删除自绘工具条、分栏、标尺、滚动与缩放，模型关键帧接入原生轨道容器。通过现有 FXTimelineViewAccessor／Mixin 适配原生播放、停止、范围与添加菜单，保留模型版本 1 数据和小数 tick；时间框遵循原生秒单位。隐藏版本 1 无法保存的特效标记与粒子结束模式，隔离特效轨道剪贴板。
- PhotonModelEditor 修正顶部选择控件宽度与收缩，保留可换行操作行；移除全选、清空、框选和复制按钮，接入 Ctrl+A、Esc、B、Ctrl+D 与 Delete。局部拉伸设置迁入视窗按钮打开的原生对话框，检视器移除重复入口。
- PhotonModelPreview 从真实绘制阶段捕获相机矩阵，以当前场景矩形投影拾取，避免离屏坐标误按整个游戏窗口换算及事件阶段读取 GUI 矩阵；增加点／边／面／部件直接拖动，共用 Gizmo 几何校验、吸附与历史事务。辅助线关闭深度写入与遮挡测试，边模式线宽为 1.5 像素，点大小不变。
- 修改范围：8 个 Java（ModelEditor、TimelineView、Preview、SceneController、FXTimelineViewAccessor／Mixin、ModelWorldSceneRendererMixin、PhotonModelTextUtil）及本记录、模型系统文档。IDEA 完成修改类格式化（关闭导入优化和重排），8 个 Java 最终诊断为空；末轮 build_project isSuccess=true、problems=[]。时间轴类型、投影捕获入口和拾取返回值完成 IDEA 语义引用检查。Data exitCode=0，25 份生成文件仅中英文语言与两份缓存变化，其余 21 份相同，无新增／删除；范围内 git diff --check 退出码为 0，仅既有换行策略提示。实际界面结果单独保持待验收。
- 本轮没有 Client 启动、重启、JVM／运行配置修改或实际 Debug 验收；IDEA 当前没有活动调试会话，需用户按此前约定手动打开 Debug Client 后继续物理交互验证。未提交、重置、暂存或新建备份，保留原有工作。

## 2026-10-09：模型视图工具、像素 UV 与连续拖动校验

- 模型平移吸附按已确认单位处理：Shift 0.1、Ctrl 0.05、同时按下 0.01；UV 为 1／0.5／0.1 像素，修饰键拖动按整个选区限制纹理边界。新增 PhotonModelSnapUtil；TransformGizmoMixin 仅在模型平移时跳过 LDLib2 原生 Ctrl 0.25 吸附，目标字节码调用通过 IDEA 核对。
- UV 读取当前纹理的 GPU 尺寸，提供像素网格按钮、白色边线与低透明度未选线，数值位移改为像素；部件／面拾取自动切换 UV 并跟随选面纹理。新建工程和基本体采用每部件单白色纹理槽，原草方块及用户资产不迁移。
- 部件／点／边／面、框选、挤出和变换放入原生场景工具行，视图右键沿原生菜单添加／删除，检视面板移除重复操作；OBJ 导入／导出和模型特效导出移至文件菜单。标题统一项目括号结构，未保存特效显示“未命名特效”，路径设置保持。
- PhotonSceneAxisView 为两类视图显示相机朝向 XYZ；模型预览方向阴影使用 CardinalLighting.DEFAULT，开关只作用于预览实例，不保存到资产、不修改来源发射器，不含 AO 或投影阴影。
- 用户提供的 16:21 日志确认 polygon.planarity 从 sampled／partVertex／drawSelection 逃出渲染。PhotonModelPreview 的每帧 Gizmo 更新改为完整基准快照生成候选、三角化受影响面、校验后一起发布顶点和面；失败保留有效资产，绘制增加几何异常提示边界。未复现用户最初的操作，不能将该日志归因为某一个已证实的鼠标动作。
- 修改范围：14 个 Java（模型工作区／UV／Preview／Project／ProjectEditor／Emitter、默认模型与吸附工具、标题与翻译源、FXEditorMixin、新增坐标提示和 Gizmo Mixin），Mixin 登记及系统文档／索引。沿用版本 1 与原 OBJ 支持，无新增依赖。
- 已完成 IDEA 修改范围格式化、14 个 Java 与 Mixin 诊断及语义引用检查，最终 Java／Mixin 无错误或警告；3 份文档保留原有表格弱警告。IDEA build_project 返回 isSuccess=true、problems=[]。IDEA Data exitCode=0；25 个生成文件仅中英文语言及其两份缓存变化，其他 21 份一致，无新增／删除。IDEA PhotonModelRegression exitCode=0，195 项检查通过；未为本轮新增镜像实现的测试。
- 实际界面验收受阻：两次 IDEA Client 调试启动后，在恢复执行阶段均发生 JBR 25.0.4.1 的 EXCEPTION_ACCESS_VIOLATION，错误帧为 DirectMethodHandle$Holder.invokeStatic；报告位于 run/client/hs_err_pid57868.log 和 hs_err_pid24888.log。第二次曾在标题画面调用编辑器入口，但因尚未进入世界缺少 dimension_type 注册表，未成功创建模型工作区；不计为新建、吸附、点边拖动、UV 边界、阴影、布局或保存重开已通过。未修改 JVM、IDE 配置或无关代码来绕过故障。
- 临时客户端断点已移除，保留两个禁用的用户断点；19 份修改文件的 IDEA 文本与磁盘规范化长度及双校验指纹一致，范围内 git diff --check 退出码为 0，仅有既有换行策略提示。Git 收尾为 dev、483 条、178 暂存／463 未暂存／2 未跟踪、0 冲突；未手动暂存、重置、提交或推送，未新建备份。保留此前已有工作与本轮生成输出。

## 2026-10-09：必要注释与强制大括号（已完成）

- 用户要求常规注册及代码本身已经清楚表达的内容不加重复注释，变量名清楚本身不构成删注释理由；另要求控制语句不得省略大括号。根 AGENTS.md、Java 25 专项规范与 .editorconfig 的 Java if／for／while／do-while 强制大括号规则已同步。
- 通过 IDEA 读取 585 个手写 Java 文件，修改 143 个 Java：删除 264 行重复的注册、翻译登记、编解码器和日志类型说明，补齐 866 处条件或循环语句体的大括号；保留单位、业务规则、特殊生命周期和具体兼容约束。未改资源 ID、常量值、调用、修饰符、导入、接口或协议。
- 与本轮修改前逐文件比较，除注释、空白及新增大括号外，代码记号一致，未新增、删除或迁移调用。IDEA 完成范围格式化，关闭导入优化和成员重排；恢复格式化及补丁引入的无关空白差异。最终 143 文件的磁盘 SHA256 与预期文本全部一致（比较时统一换行符），无待保存差异。
- 上一轮 13 文件曾出现 IDEA 文档已改而磁盘未保存，部分 apply_patch 报 Cannot save / Unable to open the file for writing；只读属性为 false，根因未确认。本轮继续通过 IDEA 重新读取并应用差异，已全部保存。维护时不能只凭编辑接口返回成功判断落盘，须核对磁盘内容。历史差异备份保留于 backups/comment-braces-2026-10-09/pending-idea-save.patch，现已应用，不应重复应用；未改用命令行或通用补丁编辑项目文件。
- IDEA 项目诊断此前完整覆盖 143 文件，0 ERROR、324 WARNING；本轮最终 13 文件复查完整，0 ERROR、17 WARNING。保留范围外警告，前后诊断接口覆盖口径不同，不声称新增警告为零。最终 IDEA 编译 success=true、aborted=false，输出确认 compileJava／classes 完成；存在过时 API 与未检查操作提示，结构化 errors／warnings 为 null，不按零计数。
- 最终通过 IDEA 既有 Data 配置执行，exitCode=0。25 份生成文件（19 资源及 6 缓存）前后 SHA256 全部一致，无新增或删除；未手写生成输出，未执行单元测试或启动新的游戏客户端。
- 本轮未手动暂存、取消暂存、重置、提交或推送。保留用户已有工作及期间出现的其他工作区变化。历史差异备份位于已有忽略的 backups 目录。
## 2026-10-09：模型视窗修复、层级操作与独立动画时间轴

- 修正四边形默认 UV、侧面 V 朝向与选择线误用三角管线，预览基础材质写深度，辅助点面不写深度；降低未选辅助标记透明度、细化线宽并保留点大小。几何视图保留基础部件变换，显示、拾取和 Gizmo 使用同一部件采样坐标，矩阵逆映射处理非均匀缩放；不可逆时拒绝视窗变换并提示。
- 模型 GUI 复用原生 Configurator、Label 和可换行按钮行；UV 提供纹理筛选，原生 Selector 的空候选安全处理。移除模型主视图的整体动画面板和 A 开关，特效项目保留。
- 模型层级使用原生右键菜单，支持修改、重命名、添加、连同子级删除及删除并提升子级，统一模型历史。提升只接受能无损保留的基础 TRS，相关部件有动画或矩阵含剪切／退化时保留资产并提示；旧 UUID 不改存储，只调整显示。
- 新增 PhotonModelAnimationWorkspace／PhotonModelTimelineView，左侧动画列表、底部时间轴，复用特效项目结构和控件风格，模型数据及操作独立。沿用 JSON1，支持片段管理、部件／骨骼／形状／透明度轨道、小数 tick、关键帧拖动与编辑，循环暂停保持当前相位；旧按钮动画面板已替换。
- 主要文件：PhotonModelEditor、PhotonModelProjectEditor、PhotonModelUVCanvas、PhotonModelPreview、PhotonModelSceneController、PhotonModelHierarchyView／Util、PhotonModelEditUtil、PhotonModelSamplerUtil、PhotonModelPreviewMaterialUtil、PhotonModelResourcesView、新增两类动画视图及 ModelParticleSceneEditorMixin、翻译源与 Mixin 登记。系统说明及索引同步。
- 当前检查：IDEA 修改范围格式化完成（关闭导入优化和重排），已检查文件诊断为空，语义引用确认新工作区及矩阵变换重载接线。IDEA compileJava 成功；既有 Sweeper 过时 API 与未检查操作提示保留，结构化警告计数为 null，不记为零。IDEA Data 成功、exitCode=0、timedOut=false，这是数据生成而非单元测试；25 份生成文件仅中英文语言 JSON 与两份对应缓存发生变化。
- 首轮 Client 通过 IDEA 调试器触发原生回调并截图：新建完整草方块（8 顶点／10 面）、无初始化中断，UV 四角／四纹理候选、检视标签无旧重叠、模型主视图 A 与整体动画移除；左侧动画列表及底部时间轴实际显示。两种删除、重命名同步面和部件轨道、撤销重做、关键帧 0→2.75→撤销 0→重做 2.75、循环 42.75 暂停为 2.75、Gizmo 回调位移 1 后撤销恢复、工程保存关闭重开保留模型／片段／关键帧已检查。临时验收文件为 run/client/ldlib2/assets/codex-model-round2.lcmodelproj.json；不是用户工程备份。
- 首轮也定位轮廓被盖：WorldSceneRenderer 普通辅助回调早于 PhotonParticleManager.afterRender 的模型提交。新增 ModelWorldSceneRendererMixin 仅把模型项目原生辅助回调延后一次并手动 endBatch，避免先画标记后被表面覆盖；最终 IDEA 编译成功并重新启动 Client，截图确认草方块表面完整、细描边、黄色选中顶点及低透明度未选顶点、选面标记与原生 Gizmo 正常显示，原生层级菜单显示两种删除入口。验收不是物理鼠标操作，完整浮动／最大化／材质全流程未在本轮重复；边模式独立近景截图未完成。Java 范围诊断无新增问题，文档保留既有表格提示，范围内 Git diff --check 通过。
- 未创建备份、未重置或手动暂存／提交；IDEA 自动纳入的新文件保留现状，用户原修改保留。

## 2026-10-09：模型工作区接入原生特效视窗与独立模型资源

- 按已确认的方案修正模型工作区：PhotonModelProjectEditor 作为受限 FXEditor 子类直接复用父类 SceneView、原生相机／工具栏／背景／着色与线框／渲染开关／S、A 浮动面板。左侧为模型层级、UV、模型资源，右侧保留原检视与历史，底部恢复原资源目录并保留模型动画；独立模型工程身份、标题与路径提示继续保留。
- 初始化失败根因包括 UV 轴的 Character 翻译参数和将普通场景对象挂入仅接受 IFXObject 的运行时。轴名先转字符串，异常文本参数非空；工作区完整构造后安装监听和视图，失败清理已安装状态、显示错误且不保存空布局。模型选择与 Gizmo 使用独立场景适配器，局部几何编辑保持基础姿态。
- PhotonModelProject 深复制来源发射器，再建立专用 FXRuntime 和真实 Photon 模型表面渲染；预览不写回来源，只有“应用到发射器”提交并进入来源历史。应用门禁核对原工程、窗口标签和运行时对象身份，合法嵌套允许，来源自身或父节点被删除后拒绝。
- 零刻暂停只见选择线的根因已通过 Client 确认：原生 emit 先入待添加队列，暂停不迁移；独立引擎尚未创建时 getParticleAmount 又把 pendingAdds 计为可见数量。初始化先绑定 DummyWorld 客户端 wrapper 建立独立引擎，再发射并仅迁移零刻待添加粒子；simulateTo 的模型钩子放在 TAIL，reset 只清时间残量。新建和重开均在 time=0.0、age=0、粒子数 2 时已有贴图表面，停止／原生跳回零刻亦保持表面。
- 原生粒子管理器是唯一播放时钟，模型小数 tick 使用整数模拟加小数残量；原生整数定位、重启清除残量。模型时间输入随实际播放更新，编辑和数值拖动期间不覆盖用户输入；A 面板仍测试整体运动与朝向。实际界面另暴露时间输入内容宽度为 0 的缺口，现只为共用时间容器设 96 GUI 像素最小宽度、字段宽度 100%；最终重新启动 Client 后字段宽 96、内容宽 92，控件输入 2.75 后原生模型时钟为 2.75，停止归零后表面仍显示。
- 新增 PhotonModelResourcesView／PhotonModelMaterialUtil：当前资产材质槽和贴图缩略图、引用数量及未使用标记，支持底部普通纹理材质选入、贴图替换、色调／覆盖、选中面分配、重命名和删除未使用槽；重命名同步面引用，在用槽禁止删除，所有资产操作复用模型历史。PNG 沿 LDLib2 原生流程导入共享目录，工程只保存资源 ID，撤销引用不会删除共享文件或修改共享材质对象。
- 关键范围为 15 个 Java（新增原生加载桥接、模型场景控制器、独立模型资源视图、场景 Mixin 和材质工具 5 类），以及 Mixin 注册 JSON。模型工作区／预览／发射器、项目路由、命名动画类型门禁、共享保存和翻译入口同步调整；沿用版本 1 JSON 和原 OBJ 支持，无新依赖或网络协议。缺少 editor.scene 的旧失效布局仅跳过一次恢复，含原生场景的有效自定义布局保留。
- 静态检查：通过 IDEA 完成修改范围格式化，关闭导入优化及成员重排；15 份 Java 最终有效诊断均为 0 错误／0 警告。IDEA 编译 success=true、aborted=false，输出确认 compileJava／classes 完成；errors／warnings 为 null，不按零计数。IDEA 语义检查模型项目加载、场景注入、应用门禁与资源操作调用，未发现过时调用；Mixin JSON 无诊断。两份系统文档及索引各保留一处既有表格弱警告。
- Data：通过 IDEA 既有 Data 配置执行，success=true、exitCode=0、timedOut=false、noTestsFound=true；这是数据生成，不是单元测试。中英文各 1014→1042 键，新增模型资源 28 键，无删除，仅既有 editor.help 按本轮功能更新。25 个生成文件首次仅两份语言和对应两份缓存变化，其余 21 份一致，无新增／删除；再次 Data 后 25 个 SHA256 全部与首次结果一致。生成输出未手写或格式化。
- Client 实际检查：通过 IDEA Client 调试会话驱动原生 UIEvent、文件菜单／弹窗和视图 API，并读取真实 GPU 截图。新建确认后弹窗数为 0，模型 8 个视图安装；默认草方块、原生着色和线框已实际出图。另存、关闭再经原生文件菜单打开后标题／路径与材质内容保留、dirty=false；UV 浮动／回停靠、场景最大化／恢复、标签切换及有效布局恢复已检查。零刻截图为 run/client/screenshots/model-workspace-ready.png，线框为 model-workspace-wireframe.png；最终重新启动 Client 检查时间框修复后的完整截图为 model-workspace-complete.png。
- 材质实际操作覆盖重命名及面引用、在用删除拦截、未使用槽删除、贴图替换、选中面分配、色调／覆盖、普通共享材质选入、PNG 原生导入及撤销／重做；撤销 PNG 引用后共享文件仍存在。Gizmo 回调令选中顶点沿 X 移动 0.25 后撤销还原。模型 2.75 tick 定位、1.375 tick 中间姿态采样、原生跳到 4 tick 清残量、连续播放、A 整体运动及来源隔离／显式应用／来源历史撤销均已检查。
- 验收边界：上述为真实 Client 中的调试器驱动控件回调、状态和 GPU 检查，没有物理鼠标逐项拖拽，也没有长时间性能、复杂透明排序、联机或 ShaderGraph 材质验收。首次发现的 IFXObject 初始化异常已修复并重启 Client；最终新建／重开期间日志未出现模型初始化异常，已有原生着色器采样器等 WARN 未扩大处理。外部大写 .PNG 导入的原生目标路径需改为小写 .png。
- 同步 photon-model-effects.md、photon-editor.md、系统索引与本记录；原较早条目的“未启动游戏”保留为历史结果，不代表本轮未执行。Git 基线 dev 456 条（167 暂存、437 未暂存），收尾 461 条（172 暂存、442 未暂存，0 未跟踪／冲突）；新增 5 类由 IDEA 自动加入索引，原有条目索引状态全部保持，未手动暂存、取消暂存、重置、提交、推送或另建备份。原生保存验收工程及导入贴图留在 run/client/ldlib2/assets，不删除撤销后仍共享的文件。本次临时断点已移除，两个用户断点保留；IDEA 确认 Client 继续运行，保留零刻暂停且无弹窗的模型工作区。

## 2026-10-09：模型项目原生面板与默认草方块修正

- 按用户要求补齐模型项目的原生编辑器外壳与操作：模型编辑器文件菜单可新建特效／模型项目，EditorMixin 双向路由到对应编辑器；保留来源标签和原生未保存确认。增加视图面板入口、文档和设置，复用原窗口尺寸、停靠、最大化、浮动与布局保存，中央 3D 预览保持主窗口。
- 左侧原生视图顺序为模型层级、UV、模型资源，右侧为原检视面板、历史，底部动画。新增 PhotonModelHierarchyView 按实际部件父子关系构建 TreeList，选择写入同一编辑会话；检视、UV、动画使用现有编辑操作和原生历史。Ctrl+Z／Shift+Ctrl+Z／Ctrl+Y 覆盖各面板，文本框保留自身撤销，关闭工作区解绑本实例监听。
- 从既有客户端日志确认创建弹窗残留的根因：Selector 初始候选值为 null，模型 enumSelector 的 Enum.name() 抛出 NPE，创建回调中断后原生弹窗尚未关闭。候选提供器对初始化 null 返回空文本，保留正常翻译；没有修改通用 Dialog 生命周期。
- 新增 PhotonModelDefaultUtil.grassBlock：居中 1×1×1、8 个共享顶点、6 个基础面与 4 个侧面草层，原版顶部／侧面／底部／覆盖纹理及默认草色，仍为可编辑几何与面角 UV。PhotonModelAsset 增加可选 materials 纹理槽，记录纹理、色调和覆盖标记；版本保持 1，旧字段与 OBJ 交换行为保留，缺少映射的旧资产按空槽处理。深复制、验证和原 Gson 保存携带新槽。
- Sampler 在需要时按部件／材质分组；预览显式读取会话资产，运行时复制纹理及原管线设置，不修改共享发射器配置。基础面先绘制，覆盖层后绘制且不写深度；锁定 Photon 源码的深度比较为 LESS_THAN_OR_EQUAL。没有槽及非普通纹理材质保留原路径；未扩展 ShaderGraph 或通用 MTL 加载。
- 共享保存、通知、悬浮样式、资源剪贴板／重命名、文本粘贴扩展到 FX 和模型编辑器，保留 currentEditor 原 FX 返回类型及旧公开重载；浮动面板按所属 EditorHost 解析。未把 FX 专用动画轨道和工具安装到模型项目。
- 本轮 22 个 Java（新增层级视图与默认模型工厂 2 个，既有 20 个）全部通过 IDEA 编辑、限定范围格式化、最终读回；导入优化与成员重排关闭。各负责组与根集成的有效诊断均为 0 错误／0 警告；IDEA 编译 success=true、aborted=false，rawOutput 确认 compileJava／classes 完成，errors／warnings 为 null，不按零计数。
- 原有 Data 配置 success=true、exitCode=0、timedOut=false、noTestsFound=true；这是数据生成，未运行单元测试。中英文各 1010→1014 键，仅新增 editor.help／hierarchy／panels／resources 四键，原文本和键均保留。25 份生成文件 SHA256 比较仅两份语言与对应两份缓存变化，其余 21 份一致，无新增／删除；未手写或格式化生成输出。
- 同步模型系统文档、Photon 编辑器文档、系统索引与本记录。Git 基线 dev 452 条（165 暂存、434 未暂存），收尾 456 条（167 暂存、437 未暂存）；原 452 条索引状态完整保留。新增两类由 IDEA 自动加入索引，另外两项为本次既有 TextField／TextArea 文件修改；未手动暂存、取消暂存、重置、提交、推送或另建备份。
- 文档最终读回与预期内容一致；IDEA 报告模型文档、编辑器文档和系统索引各一处表格格式弱警告，保留原表格排版，不扩大为无关文档格式化。
- 未启动游戏，实际创建弹窗、窗口拖动／浮动、模型选择、UV 交互、覆盖草层与 GPU 效果仍未运行验收；编译和 Data 不替代这些检查。

## 2026-10-09：全项目 Java 25 整理与独立模型项目

- 按用户确认的 A 范围整理 575 个存量手写 Java，独立模型项目新增 2 类后共 577 个。分工为客户端／配置／粒子 146、世界／动画／指挥 156、核心 162、Mixin 113；全部通过 IDEA 编辑、格式化并逐文件读回。存量包名、资源标识、公开契约、注册生命周期和网络字段保持；没有新增依赖、预览开关或机械转换 record／sealed。
- 将传统文档注释转换为中文 `///` Markdown；最终 IDEA 全源码检索 `/**` 为 0 项。补充业务常量含义与单位，使用安全等价的模式 switch、record 模式、未命名变量／模式、局部 `var`、方法引用和 `Math.clamp`，按职责分隔逻辑块。真实包私有声明按公开调用需要补齐可见性，接口隐式 public 和框架签名不机械改写。
- 长方法拆分涉及 ConductorController.tickControlled、ConductorCommandPayload.apply／issueFormation、ConductorScreen.init、ConductorHud.activate／update／renderTooltipAtPointer／activateButton、ConductorControls.onMouse、PhotonEditorTextUtil.register 和 PhotonSubClipboardUtil.paste；新增私有职责方法，保留副作用及执行顺序。HitboxSnapshot.encodeSize 使用 record 模式，保持各形状 double 字段顺序；共享编辑功能 ID 集中于 PhotonEditorTextUtil，既有字符串值不变。各组最终方法、构造器和复杂 lambda 均不超过 100 行。
- 按用户最新要求，将“编辑模型”改为独立工程标签；新增 PhotonModelProject／PhotonModelProjectEditor，复用 LDLib2 的 IProject、ProjectType 和 Editor。FXEditorMixin 注册“文件 → 新建 → 模型项目”与原生打开；EditorMixin 仅将 FX 编辑器中的模型项目载入路由到独立编辑器，保留来源特效标签。模型、UV、骨骼／部件关键帧和形状键编辑继续复用既有工作区，工程保存、另存和撤销／重做使用原生生命周期及 NBT 字节数组历史快照。
- 模型标签为“模型项目-模型名称”，未保存时从来源发射器名或“未命名模型”取名；保存后去掉完整 `.lcmodelproj.json` 后缀。EditorWindowMixin 与 PhotonEditorTitleUtil 复用特效项目的悬浮绝对路径、同名完整路径和刷新设置，LcPhotonEditorSettings 保留 FX 重载并提供通用 Editor 注册入口。最终审阅修复关闭精简标题时模型原标题只有名称的缺口：此时显示完整路径；开启精简标题时按同名冲突开关处理，FX 行为保持。
- 工程保存与 Apply 到来源发射器分别处理；来源特效项目关闭或切换时检查工程身份和窗口成员关系，拒绝写回，独立工程仍可保存和导出。关闭成功回调和真实 onRemoved 幂等释放预览；未保存确认取消、切换标签和最小化保留工程。先调用父类保存布局，再移除工作区和虚拟世界。原 OBJ 支持、`.lcmodelproj.json`／`.lcmodel.json` JSON 版本与资产字段不变；维护依据与公开入口见 [Photon 模型特效](photon-model-effects.md)。
- 实际诊断完整覆盖 577 文件，最终 0 ERROR；客户端 96、世界 324、核心 150、Mixin 28 项警告／提示，合计 598。世界与核心比完整基线分别减少 35／23，Mixin 保留原 28 项；客户端初始 92 文件超时，不能据不完整基线声称全项目新增警告为零。新增模型工程、编辑器、工作区、预览及最终标题修正无错误／警告。Mixin 运行目标类型检查的三项静态误报以中文说明和对应局部 SuppressWarnings 处理，未抑制其他诊断。
- 中央 IDEA 编译 success=true、aborted=false，rawOutput 确认 compileJava／classes 完成；最终标题修正后再次增量编译成功。errors／warnings 为 null，不按零计数；保留 Sweeper 的过时 API 与 unchecked notes。现有 Data 配置 success=true、exitCode=0、timedOut=false、noTestsFound=true；这属于数据生成，未新增或执行单元测试，也未重跑上一阶段模型回归。
- Data 后中英文各 1004→1010 键，仅增加 model.project 命名空间的 editor_required、missing_asset、source_unavailable、title、type、unnamed 六键，无删除、无原文本变化，两语键集合一致。25 个生成文件（19 资源与 6 缓存）SHA256 比较，仅两份语言及对应两份缓存变化，其余 21 份一致，无新增／删除；生成输出没有手写或格式化。IDEA 语义引用核对项目注册／路由、模型编辑入口、标题设置和共享注册调用，编译未发现过时调用错误。
- 同步 java-architecture.md、系统索引、photon-editor.md、photon-model-effects.md、conductor.md、hitbox.md 与本记录。暂存基线 dev 为 287 条（163 暂存、239 未暂存、0 未跟踪／冲突），收尾只读核对原有 287 条的 indexStatus 保持；两份新增工程类由 IDEA 自动加入索引，未执行手动暂存／取消暂存、重置、提交、推送或另建备份。
- 未执行游戏内界面交互、视口命中、透明排序或 GPU／性能验收；静态检查、编译及 Data 不代表这些运行效果已验证。

## 2026-10-09：Java 25 语法与注释专项规范

- 根据用户新项目规则，新增 docs/java-25-language-and-comments.md，并在根 AGENTS.md 增加必须读取的入口与执行规则，在系统索引加入链接。最初写入 .codex/standards，IDEA Git 检查确认 /.codex/ 被忽略后将本次新文档移到 docs，使规则可随仓库保存；没有改动 .gitignore。此规范编写阶段不批量转换 Java 源码，不修改 Gradle、IDEA、Mixin 或 EditorConfig 配置。
- 通过 IDEA 核对 toolchain／语言级别／字节码目标 25、JAVA_25、当前未启用预览，以及既有中文注释和方法引用规则；EditorConfig 已偏好 documentation line comment。查询 Oracle Java 25 官方语言指南、JLS、Javadoc 和 API 清单，区分语言事实与项目选择。
- 规定新增／本次修改 Javadoc 使用 /// Markdown，普通实现说明仍使用 //；记录连续空行、声明位置、参数／返回／异常标签、程序元素链接与 @deprecated 注解配合。现代语法按适用且语义等价条件使用，保留框架签名、初始化顺序与资源／线程所有权；不机械替换为模块导入、紧凑源文件、record 或 sealed。
- 版本边界明确：Markdown Javadoc 自 JDK 23 正式提供，Java 25 可用；灵活构造器、模块导入、紧凑源文件／实例 main 在 25 正式，基本类型模式增强仍预览。默认不新增预览开关／孵化依赖；不使用已撤回的 String Templates。
- 实际检查：四份规范／入口／记录文档通过 IDEA 写入并读回，专项规范的新表格仅按行范围格式化，最终专项规范、AGENTS 和本记录诊断无问题；系统索引原有表格弱提示保留。入口链接已核对，AGENTS 和索引去除本次新增段后与修改前内容一致。IDEA 编译 success=true、aborted=false，compileJava／classes 为 UP-TO-DATE；IDEA 正常 Data 返回 success=true、exitCode=0、timedOut=false、noTestsFound=true，此为数据生成而非测试。25 个生成文件 SHA256 前后完全一致；未执行代码示例编译或 Javadoc 生成。
- Git 基线为 dev 283 条（160 暂存、238 未暂存、0 未跟踪／冲突），规范新增后为 284 条，新增文档未跟踪，原有条目索引状态保持。未执行暂存／取消暂存、提交、推送、重置或另建备份。用户随后要求整理整个项目：IDEA 只读盘点为 575 个手写 Java，按 client/config/particle 144、world/animation/conductor 156、其余核心 275 分组；此时仅盘点，尚未把代码整理记为已实施。

## 2026-10-09：Photon 独立模型特效发射器与轻量编辑器

- 按用户确认新增单模型特效对象 `lobecorp_model_emitter`，不修改原 Photon OBJ／glTF／粒子发射器支持。内嵌、资源和本地文件来源导入为独立版本化模型；外部模型快照进入原 definition，使已导入模型可以跟随普通 .fx／FXPack 保存。每个实例独立运行槽、动画时钟和几何，复用原生命周期、停止协议、材质批次、Timeline 和命名动画 Mixer。
- 新增模型、UV、动画三个编辑工作区：部件／顶点／边／面选择、框选／全选、TRS／Gizmo、基本体、部件／面复制、删除和逐面挤出；面角 UV 保留接缝、普通纹理底图、角点／整面选择、拖动、数值变换及 XYZ 平面投影；片段、关键帧定位、播放、部件与骨骼 TRS、数值权重绑定、形状键 delta／权重关键帧。工程和运行资产使用 .lcmodelproj.json／.lcmodel.json，普通 OBJ 导出烘焙默认姿态。副本编辑、撤销／重做及 Apply 接入宿主历史；关闭未应用编辑不修改发射器定义。
- 运行尺寸采用模型坐标单位，默认保留参考尺寸；局部中段伸长与 START／END 端帽使用同一前向轴，全局 END 补偿长度位移。采样顺序为 morph → stretch/width → skin → part，骨骼采用逆绑定，旋转使用四元数最短弧。缩短时中段最多折叠为零，不翻转或写入 NaN。多个区间各使用同一长度差，第一版不自动分配串联链长；宽度同比影响所有部件，锚点约束长度变化而不是世界空间锁点。
- 收尾修正新功能的实际缺口：内嵌 reload 不再清空模型；循环软停止在当前周期末排空；失败载入保留有效快照并显示错误状态；同类型不同资产的缓存属性绑定使用目标实例中性缺失槽，真实定义变化移除过期槽，不序列化临时值。新增 PhotonModelRecordingMixin 只补新对象在 pollRecording／referenceRecord 两处实例动态属性枚举，其它对象保持原调用。骨骼临时动画姿态与 bind 分离，允许动画零缩放；暂停定位到循环片段末端显示末帧。部分顶点变换前按原面三角化受影响 n-gon，保留 UV 与顶点索引，避免普通四边面局部编辑被平面校验拒绝。
- 关键范围：18 个新增 Java（资产／校验／交换／采样、发射器配置／实例／注册、五个编辑器类、两项编辑辅助、翻译入口、模型回归、录制 Mixin）；既有 TranslationKeys 仅新增模型翻译注册，Mixin JSON 仅新增录制类条目。新增 photon-model-effects.md，更新系统索引、编辑器与粒子说明及本记录；没有新增依赖、世界实体或网络协议。
- 实际检查：通过 IDEA 编辑并格式化新 Java 与既有修改段，关闭导入优化／成员重排；最后 18 个新 Java 无 ERROR／WARNING，模型回归 main 的 println 保留一条 DEBUGGER_SUGGESTION。批量诊断中四个文件首次报告 timedOut，随后逐项补查成功，未把超时空列表当成功。既有 TranslationKeys 的 12 项 unused WARNING 和系统索引既有表格弱提示保留，没有扩大处理。IDEA 语义引用确认编辑入口、运行／预览／OBJ 导出采样、共享 TRS／权重求值与翻译初始化引用；新 Mixin 和注册 JSON 诊断均无问题。
- IDEA 项目编译返回 success=true、aborted=false，rawOutput 确认 compileJava／classes 完成；编译计数 errors/warnings 为 null，不将其描述为零，输出保留未经检查操作提示。随后 IDEA 由 main 位置执行 PhotonModelRegression，compileJava 完成、184 项断言通过、exitCode=0、BUILD SUCCESSFUL。覆盖 OBJ 负索引／接缝／默认姿态烘焙、JSON 严格版本／整数索引／深复制、非扇形凹面与非零面积自交拒绝、局部伸长／独立端帽／塌缩有限法线、共享顶点部件与采样结果隔离、绑定层级、morph 蒙皮顺序和旋转最短弧。
- IDEA 通过现有 Data 配置运行，执行接口返回 success=true、exitCode=0、timedOut=false、noTestsFound=true；此为数据生成，不是测试通过。中英文各 872→1004 键，只新增模型命名空间 131 键及新对象名称 1 键，无删除、无原文本变化，两语键集合一致。25 个生成文件 SHA256 前后仅两份语言及对应两份缓存变化，其余文件一致，无新增／删除；生成输出未手写或格式化。
- 最后修正快照保存：IDEA 依赖源码确认 NBT StringTag 使用 writeUTF，较大的简单模型 JSON 也会超过其长度上限。配置内存仍使用 assetJson 字符串，原同名持久化键改用 UTF-8 ByteArrayTag，兼容旧 StringTag；外部 JSON 版本与 API 不变。独立 main 初次执行保存检查因 ModList 未加载而失败，因此将保存检查分离到 runLoaded()，在真实 Data 注册环境执行，不模拟生产注册状态。
- 最终 IDEA Data 调试会话中，main 的 195 项几何／编辑断言及 runLoaded() 的 6 项保存断言均完成，共 201 次断言，包含逐个网格浮点的有限值检查，不代表 201 个独立场景；后者包括大于 65535 字节模型、字节数组载荷、二进制 NBT 往返、字段／几何恢复及旧字符串兼容。临时 ModGenerator 第 19 行断点已移除，原有两个断点保留；继续后调试会话显示 stopped，未将该状态单独视为退出码证明。随后正常 Data 配置再次返回 success=true、exitCode=0、timedOut=false、noTestsFound=true；最终 IDEA 编译 success=true、aborted=false，compileJava／classes 为 UP-TO-DATE。最后 25 个生成文件 SHA256 与首次成功 Data 后完全一致。
- Git 基线 dev 为 264 条（141 暂存、224 未暂存、0 未跟踪／冲突），Data 后为 283 条（155 暂存、238 未暂存、5 未跟踪、0 冲突）。原有条目索引状态保持；19 个新增文件中 14 个已由 IDEA 加入索引且有后续工作区编辑，5 个仍未跟踪，未执行暂存／取消暂存、重置、提交、推送或创建额外备份。
- 已知限制与未验证：未启动游戏或执行真实 UI／GPU 验收，视口命中、窗口交互、透明排序和性能仍待游戏内检查；模型、UV 和骨骼的自动化几何检查不等于这些交互已验证。逐面挤出、不做自动蒙皮／IK／复杂拓扑；所有部件共用 RendererSetting 材质层，OBJ usemtl 仅交换信息；复杂 ShaderGraph 使用原 Photon 场景查看。资产片段 opacityKeys 协议支持采样，第一版整体透明度编辑沿用原 Photon 属性轨道。维护细节见 [Photon 模型特效](photon-model-effects.md)。


## 2026-10-08：Photon 光束预览空相机修复与依赖补丁对照

- 用户日志指向 Photon 26.1.2.2 的 BeamParticle.getRealEnd 第 170 行；IDEA 依赖源码与字节码确认方块和实体两个分支共两处 camera.entity().level()。LDLib2 26.1.2.39 的 SceneCamera 不关联实体，原生预览链直接传入该相机；原 Lobecorp 长度覆盖只修改 getEnd 向量，不构成本次空相机根因。
- 仅扩展现有 BeamParticleMixin：普通相机保留 original.call(entity)，空相机实体改用公开 getEmitter().getLevel()；两处 Entity.level 使用 require=2。首次 getRaycast 前通过 @Local(name="end") 取得原生已计算终点，相机实体与发射器世界同时为空时返回该 end，不重复坐标计算、不伪造实体、不借用 Minecraft.level。原光束长度覆盖、方块／流体设置、实体检测算法与执行顺序保持；未新增运行类、依赖或 Mixin 配置。
- 中文注释关联 PH-01；新增 photon-ldlib2-fixes.md 汇总 LDLib2 剪贴板、保存焦点、悬浮样式和通知适配，Photon 光束／噪声／曲线相关处理，以及自有扩展的状态、历史、迁移、激活和渲染修复。明确区分上游缺陷、兼容／性能／编辑增强与扩展自身问题，列现象、当前版本、原生入口、补丁类、保留语义、历史证据和升级复核点；更新索引与 particle-rendering.md，不将新增能力或尚未修复的限制记作上游 Bug。
- 实际检查：通过 IDEA 修改、读回并格式化本次 Java 类及修改段，关闭导入优化和成员重排；最终相关 Java 诊断 0 错误、0 警告，独立只读复核确认注入位置、end 局部范围与两处分支。IDEA build_project 返回 isSuccess=true、problems=[]；没有运行测试或游戏，编译和 Data 不证明实际光束预览、碰撞或 GPU 验收通过。
- 第一次正常 IDEA Data 输出被截断且未返回退出码，IDEA 读取其日志确认六个 provider 完成；不把缺失退出码当作已确认成功。第二次通过可跟踪 IDEA 运行接口执行同一 Data 配置，返回 success=true、exitCode=0、timedOut=false、noTestsFound=true；这是 Data 配置，不是测试通过记录。第二次日志确认六个 provider 完成，HashCache total／old／new 均为 19、written=0、removed stale=0。25 个生成文件（19 资源、6 缓存）SHA256 前后完全一致，无新增／删除；没有手写或格式化生成输出。Data 保留其他未安装模组兼容类的 WARN，未扩大处理。
- Git 基线 dev 分支 262 条（140 暂存、223 未暂存、0 未跟踪、0 冲突），最终 264 条（141 暂存、224 未暂存、0 未跟踪、0 冲突）；所有原有条目索引状态码保持，仅增加 BeamParticleMixin 工作区修改与新文档。新文档起初为未跟踪，收尾时已进入索引；未执行暂存／取消暂存命令，不把这一变化描述为整个索引完全未变。未重置、创建备份、提交或推送。
- 待游戏验收：有实体的普通相机、无实体但有预览世界、相机实体和所属世界同时为空；关闭／方块／实体／两者四种射线模式，动态长度、旋转缩放、流体设置、切换场景与命名动画。未关闭扩展做纯净环境复现，不把源码归因误记为该项已执行。

## 2026-10-08：Photon 单边激活命令、循环停止与提示补全

- 按用户确认移除独立单帧选项：仅开始在 clip.start 执行一次开始行为，仅结束在 clip.end 执行一次结束行为；同开沿原区间，皆关不接管。旧 singleFrame=true 转为仅开始，新的序列化只写五个原激活设置，默认仍同开。只结束＋STOP_EMISSION 可以停止循环发射，同时保留当前粒子更新和可见；后续有效开始或显式重启才恢复。
- 已确认源码根因有原生 evaluate 先把单边轨道当区间，以及扩展持续 OR 抵消停止命令。原生区间只接收双端 Activator，普通多区间 OR 保持；单边命令按最新时间、同刻后叶顺序覆盖，后来的真实区间入口恢复。命名 TimelineState 按各播放器制作时间认领，未到点不写所有权；plain Timeline 只捕获已接管点，倒退、静音或删除时恢复活动／显示／发射，原生 Control／双端当前值优先。发射器的循环只改周期进度，不通过 reset 解除停止。
- 时间轴沿原 SignalTrackEditor 的竖线和方块，单边只显示有效点，双边显示区间和两端，两端关闭淡化。初次布局与 registerClipView 回调同步有效点几何，框选、滚动缩放、组移动与引导线一致；只把单点 dragMode 改为 MOVE，保留原选择、历史和冲突约束，不允许边缘拉伸。clip.duration 保持，普通时长检视仍可调整仅结束点；设置及撤销只刷新布局，不重建展开和选择。
- 配置帮助统一放到原生放大镜，移除整组／字段重复悬浮；设置、预设、工具、动画页和列表操作全部接中央 Tooltip 包装。按照控件自己的 WrappedTooltip 监听器身份去重，已有其他监听器不再导致漏包装，内容变化才更新缓存，按原生 170 像素分行；保留原校验提示。帮助正文精简为操作与实际限制，不放修复经过、实现或验收描述。
- 关键文件：PhotonActivatorOptions、PhotonActivatorTimelineUtil、PhotonTimelineTraversalUtil、TimelinePlayerMixin、PhotonAnimationTimelineState／Runtime；ClipTrackEditorMixin、新增 PhotonActivatorClipUtil／ActivatorClipLayoutMixin 及 Mixin JSON；PhotonEditorTooltipUtil、PhotonAnimationUiUtil、Forms、Settings、Tools 等 13 个提示相关类。同步 photon-editor.md、photon-named-animations.md 与本记录，保留现有清道夫、暂存和其他轮次内容。
- 2026-10-08 单边激活与提示补全：24 个修改或新增 Java 完成 IDEA 修改行／涉及类格式化（关闭额外导入优化及成员重排），最终 errorsOnly=false 诊断均无错误／警告，Mixin 配置诊断无问题。IDEA build_project 返回 isSuccess=true、problems=[]；正常 Data 配置 waitForExit=true 返回 exitCode=0。生成目录 25 文件 SHA256 前后无新增／删除，只变更两份语言和对应两份缓存；IDEA 读取解析中英文各由 874 变为 872 键，删除旧 control.single_frame 标签及提示两键，各更新 17 项预期说明，无新增键，其他键值保持。生成输出未手写或格式化。
- 新增断言覆盖旧单帧迁移、仅开始／仅结束及零长度点、点前外部停止、拖回／静音恢复、与原生 Control／双端区间共存、同刻叶顺序与后来入口恢复／清理；零时刻粒子检查增加真实局部引擎的循环 Burst／持续速率停止、已有粒子年龄推进及 CONTINUE 恢复。未运行这些加载后检查或游戏，时间点命中／组拖动、悬浮屏幕边界、循环生成与命名并行仍待游戏内验收。已有不相关修改继续保留，未人工操作暂存、提交、推送或创建备份；新增两个 Java 由 IDEA 自动加入索引。文档既有设置分类表保留一条弱格式提示，不扩大为排版整理。
- Git 实际最终快照：dev，262 条工作（140 暂存、223 未暂存，0 未跟踪／冲突）。实现中途保存的 262 条 porcelain 快照与收尾对比，既有索引状态未变化；这只是该快照之后的检查，不据此推断本轮开始前所有并行工作完全不变。只执行只读 Git 检查，未运行 add、reset、restore、stash、commit 或 push。IDEA 语义引用确认新版 begin 的两处 Runtime 调用与 ownership 的内外调用已同步，源码无 singleFrame() 或 control.single_frame UI 残留；旧协议读取保留。

## 2026-10-08：清道夫清扫与重组接近及失败重定位

- 用户确认自动工作时双方水平碰撞框边缘间距不超过 0.5 格；AI 和指挥家持续工作共用 SweeperWorkUtil，保持原技能最大施放范围、垂直规则、前后摇、动画与移动锁。距离使用两轴分离距离平方和，站位预留 0.25 格余量，不修改实体碰撞框。
- 旧 AI 在进入原技能范围后停止导航，方块终点不能保证实际工作距离，失败后没有重新定位；旧 ConductorWork 将非冷却施放失败直接加入永久跳过集合。新增接近状态在各自 AI／工作实例持有，复用站位解析、碰撞与支撑检查、安全末段移动；位置失败或停滞换站位，导航拒绝立即尝试下一候选，重算用完后重新选目标。显式 64 格路径上限覆盖原 32 格工作搜索，不改 FOLLOW_RANGE。
- AI 在 Brain 后推进工作移动，避免 MoveToTargetSink 或随机闲逛覆盖精确位置；有效清理目标期间不启动闲逛，使用前复核清道夫尸体生物质。其他指挥命令及待瞄准施放优先，自主移动和精确候选仍遵守 allowNavigation。冷却、技能占用、事件拒绝不作为位置失败，保持原技能结束／取消时序；指挥家施放返回 false 不再直接永久跳过目标。
- 关键文件：SweeperAi、ConductorWork、新增 util/entity/ai/SweeperWorkUtil，以及 entities-ai.md、conductor.md、README.md、changes.md。项目文件均通过 IDEA 修改，Java 只格式化涉及范围／新增类，关闭导入优化和成员重排；语义引用核对公共距离入口和更新后的目标资格签名，旧 walkToCleanupTarget 无残留。
- 实际检查：相关 IDEA 源码诊断无新增错误或警告（SweeperAi 原有警告从 22 条减至 19 条，ConductorWork 原有 2 条保持，新工具无问题）；文档未改动的表格区域保留弱提示。IDEA build_project 返回 isSuccess=true、problems=[]，Data 配置退出码 0。生成目录 25 个文件的 SHA256 前后全部一致，无新增、删除或内容变化；未手写或格式化生成资源。
- Git 核对：dev 分支由 256 条工作（137 暂存、216 未暂存）变为 259 条（138 暂存、218 未暂存、0 未跟踪、0 冲突）。新 Java 文件由 IDEA 创建时自动加入索引，未执行暂存／取消暂存命令；原有工作继续保留，未提交、推送或创建额外备份。未运行测试或游戏，台阶、障碍物、移动目标、不可达目标重选和指挥切换的实际表现仍待验收。

## 2026-10-08：Photon 高速经过事件、单帧激活、零时刻预览与项目动画调用

- 高速时间步长现在按前后制作时间收集离散边界，顺序补调用原生 evaluate 后再求值当前时刻；覆盖激活入口／区间结束及原控制、音频和调用片段边界，信号保留原区间派发。精确 localTime 在补求值前保存、正常求值前和 finally 中恢复，begin 清空经过时间；tick 不调用 frame，避免渲染或后处理重复提交。根因是当前时刻 clipAt 可能跨过完整短片段，不能只增加最终时刻查询或另建播放器。
- 激活轨道增加 singleFrame 布尔选项，默认 false，旧调用与旧数据缺失字段保持区间行为。开启后片段起点执行原开始重启／继续及清理规则并锁存活动状态，零长度片段可触发；片段结束不自动执行结束选项，后续由控制轨道、手动控制或显式清理停止。同一对象多个激活轨道保持原 OR 关系，另一激活轨道结束不能解除仍有效的锁存。读写、复制、历史及检视修改必须保留新字段，不把“一次开始后持续运行”解释为只模拟一刻。
- 空选项目测试 TL 的原生新增轨道菜单加入“动画调用”，目标为本特效、默认独立触发，可选择受控播放；复用 ChildTrack、ChildWindow、实参表单、当前测试定义形参、原生 Codec／复制和现有播放器。注册全局用于类型恢复，菜单仅空选上下文显示；运行时要求当前编辑预览 timelinePreview 且来源 UUID 不在动画库，防止可导出动画或普通世界的本特效递归调用。测试内容仍只保存 .fxproj 的 data/lobecorp_animation_preview，.fx、FXPack 及子模板不包含该字段。
- 本特效调用在原生求值期间可能再次 play／stop，引入 apply 的 pending 防重入：外层 pass 结束后重新采样新贡献，受控 complete 排入 afterApply，在新贡献采样后处理；previewOnly 批量 dispose 不在中途反复合成。具体播放句柄和 token 继续校验所有权，遍历采用稳定快照；维护时同时检查同名重启、零长度窗口、保持／释放、关闭和模式切换，不能只修复 Map 遍历而忽略混合器作用域。
- 编辑器场景将 Burst.time=0 在 0 帧初始化，并接入待加入渲染队列；不 advance／tick，不推进粒子年龄、特效时间或管理器计数，不提前发射 rateOverTime。复用原 burstTriggers 的概率／数量／周期，保存零时刻随机数量并在首个正常窗口排除已消费的零触发，clear 清理记录。原预热结果保持，正延迟、非活动、停止发射及已经推进年龄的对象跳过；原非循环寿命不超过一刻不会预热，仍可初始化。本批仅编辑器 DummyWorld／PhotonParticleManager 场景主动调用，普通世界不自动改动。提前抽样的相对随机调用顺序可能变化，不承诺种子图像完全一致。
- Lobecorp 设置新增两个原生开关及悬浮说明：粒子与渲染的 timeline_event_traversal、预览的 zero_time_emission，默认及旧配置缺省 true；分别控制离散经过事件补求值、编辑器 prime 与待加入队列接入。通过所属 DummyWorld 的 PhotonParticleManager.sceneView 查询对应编辑器，子实例沿用其场景设置，未绑定编辑器的实例默认开启；不能调用不存在的静态 PhotonEditorSettings.enabled，也不能借用其他打开项目的设置。沿用现有 features 持久化与分类，未增加 UI 风格。
- 关键文件：PhotonActivatorOptions、PhotonActivatorTimelineUtil、PhotonTimelineTraversalUtil、TimelinePlayerMixin、PhotonEmissionPrimeUtil、ParticleEmitterPrimeMixin、EmissionSettingRuntimePrimeMixin 及待加入粒子桥接；PhotonAnimationCallTrack／Editor、PhotonChildAnimationTrackEditor／Player、PhotonAnimationRuntime、LcPhotonAnimationTypes、PhotonAnimationTimelineMixin、PhotonEditorSettings 及两处翻译集中入口。系统说明同步 photon-editor.md、photon-named-animations.md，既有清道夫及其他轮次记录保持。
- 实际检查：34 个本轮 Java 文件完成 IDEA 范围／新增类格式化，关闭导入优化和成员重排；最终相关诊断无错误或警告，Mixin JSON 诊断无问题。IDEA build_project 返回 isSuccess=true、problems=[]；正常 Data 配置退出码 0，BUILD SUCCESSFUL in 23s，六个 provider 完成，HashCache total／old／new 均为 19、written=2、removed stale=0。生成目录 25 文件 SHA256 前后无新增／删除，仅两份语言文件及对应两份缓存变化；中英文均从 858 增至 874 键，各新增 16 个预期键，原键和值无修改／删除。无过时静态设置调用。文档原表格保留两条弱提示，未扩大为排版整理。
- Git 核对：dev 分支由 241 条工作（124 暂存、200 未暂存）变为 256 条（137 暂存、216 未暂存、0 未跟踪、0 冲突）；原有条目均保留，原 indexStatus 全部一致。13 个新 Java 文件由 IDEA 创建时自动加入索引，未执行暂存／取消暂存命令。文档仅通过 IDEA 窄补丁更新，生成输出未手写或格式化；未提交、推送或创建备份。加载后独立回归和游戏未运行，新增／调整断言不作为执行通过证据。
- 游戏待核实：高速跨过整个短片段和共享边界的事件顺序；单帧持续及后续停止；0 帧爆发、概率、预热、延迟和首个正常窗口；空选测试调用的并行／受控／同名重启、形参跟随、复制与撤销、项目保存往返及导出排除。已有跨周期 MARKER 顺序等独立遗留问题不扩展处理，历史检查不能替代本轮验收。

## 2026-10-08：清道夫飞扑接触范围外扩

- 用户确认伤害和抓取同步扩大：LeapSkill 的独立飞行接触框各方向外扩 0.3 格，BoxSize 使用完整边长，因此宽、高、深各增加 0.6 格。创建与逐 tick 更新均使用 FLIGHT_HITBOX_PADDING，实体自身受击框、碰撞框及原落地范围保持。
- 顺序保持接触判定后尝试伤害、再独立抓取；抓取不以 hurtServer 成功为前提，伤害无敌帧不会跳过抓取。伤害仍走现有服务端管线，单次施放每生物一次、体型上限 2.5 格和脱离规则保持。
- 关键文件：LeapSkill、entity-grab.md、changes.md。通过 IDEA 编辑并仅格式化修改行，关闭导入优化和成员重排；源码读回与基线比较仅包含本次范围、相关注释变化，实体类与抓取系统未改。创建／更新的语义调用分别仍为 onActivate／onTick，两个尺寸写入均使用外扩常量。
- IDEA 诊断无新增错误或警告，原有 5 条 Level 资源警告保持；两个文档批量诊断均 analyzed、无诊断项（不包含全部编辑器弱提示）。IDEA 编译 isSuccess=true、problems=[]；正常 Data 退出码 0、BUILD SUCCESSFUL in 28s，HashCache total／old／new 均为 19、写入 2、删除 0。
- 生成目录的 25 个文件前后 SHA256 检查无新增或删除；Data 实际更新两份语言资源及对应两份缓存，本次没有修改翻译源。刷新后中英文 JSON 均可解析、各 858 键，生成目录 Git 无条目；未保留生成前语言内容快照，不声称已逐键确认其语义差异。
- Git 前后均为 dev、241 条工作（124 暂存、200 未暂存、0 未跟踪、0 冲突），所有已有条目状态码保持。未手动暂存、提交、推送、重置、删除或创建备份；未运行游戏或测试，实际飞行接触范围未游戏内验收。

## 2026-10-08：导出成功提示与最多三条竖向通知

- `.fx` 导出在原 writeCompressed 与 clearCache 均成功后显示 2 秒原生定时成功提示；FXPack 原成功通知 ordinal=1 改为同类，保留 ID、文件和警告详情，错误 ordinal=0 与其他确认弹窗保留。仅 Photon 的 showNotification(String,float) 按所属当前 FXEditor 加入竖向容器，已有保存成功提示自动沿同一入口。
- 同一编辑器最多同时 3 条，超出时原生关闭最早通知；顶部保留原生 10 像素、间隔 2 像素，已在队列的重复 show 不重复加入。容器不参与鼠标命中，close／TICK 清除空容器，没有静态窗口引用；FXProject 用 WeakReference 在 onLoad／onClosed 绑定和清理导出所属编辑器。
- 按所有新增功能进入 Lobecorp 设置的持续规则，资源与剪切板新增“保存与导出通知”（notifications），默认及旧配置缺省开启，使用原 BooleanConfigurator 与换行说明，继续保存到 features 映射。关闭时 `.fx` 恢复原无成功提示、FXPack 恢复原成功确认弹窗、保存恢复原独立定时通知，错误及其他确认弹窗保持。
- 源码确认 `.fx` 原来没有成功提示、FXPack 使用确认型成功通知，LDLib2 .39 定时通知固定 top，连续通知可能重叠。维护时核对实际写入成功顺序、成功／错误调用 ordinal、项目与窗口归属、超限关闭和空容器清理，不扩大为所有弹窗或其他编辑器的全局堆叠。
- 新增 PhotonEditorNotificationUtil、DialogMixin、FXProjectMixin，修改 PhotonEditorTextUtil 与 META-INF/lobecorp.mixins.json；通知初阶段 5 文件 IDEA 诊断无错误／警告，Java 格式化关闭导入优化与成员重排，Mixin JSON 没有自动格式化且复查 errors=[]。该阶段 12 个 Java 编译成功；设置补全增加 PhotonEditorSettings，最终 13 个 Java 经 IDEA 编译成功，isSuccess=true、problems=[]，通知设置涉及 4 个 Java 的最终 errorsOnly=false 诊断均 errors=[]。
- 通知初阶段正常 IDEA Data 为 BUILD SUCCESSFUL in 32s，6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=2；当时仅新增 export_success／export_success_detail 两键，该结果保留为阶段证据，不作为后补设置的 Data 证明。
- 设置补全后最终正常 IDEA Data exitCode=0、BUILD SUCCESSFUL in 21s，6 个 provider 全部完成，HashCache total／old／new 均为 19、removed stale=0、written=0。相对通知起始的 25 个生成文件（19 资源及 6 缓存）无新增或删除，仅 en_us、zh_cn 及对应两个语言缓存变化；两语言各共新增 export_success／export_success_detail 与 feature.notifications／feature.notifications.tooltip 四键，既有键与文本保持，没有手写或格式化生成输出。
- 最终 Git 为 dev、241 条变更（124 暂存、200 未暂存、0 未跟踪、0 冲突），原有各条目索引状态码保持；IDEA 新建上述 3 个 Java 时自动 Add to VCS，暂存从 121 增至 124，没有人为暂存或取消暂存，不把该自动索引变化描述为“索引完全未变”。未新建或运行测试，未创建备份、提交或推送；实际导出、通知堆叠、鼠标命中和窗口生命周期未游戏内验收。布局源码复核 overlay 以 relative 撑高、show 不重置布局、容器只禁自身命中，没有进一步修改布局。
- 空选测试 TL 添加动画调用的新需求仅收到并调查，现有 ChildTrack 只支持发射器发射器子动画，尚未实现该新入口。高速播放／单帧／0 时生成也未实施；单帧调用需先确认“一次开始后持续”或“仅一个模拟 tick”的语义，不能把现有激活／控制／音频点采样或信号区间去重描述为新需求已修复。

## 2026-10-08：空选测试时间轴切换前提交

- 用户反馈空选测试时间轴切换后消失；源码确认旧 suspend 只保存视图，selectAnimation 改变制作选择前没有统一提交当前 TL 投影。普通已完成原生编辑仍沿历史提交，未发现其必然漏路由；本轮增加切换边界防护，不把静态缺口认定为用户现象的唯一游戏内根因。
- PhotonAnimationTimelineContext.prepareSwitch 先结束录制、取消片段拖动，再调用工作区提交并保存游标与视图；suspend 在该流程后才设置 active=false。PhotonAnimationWorkspace.selectAnimation 在改变 selectedId 前执行 prepareSwitch，新建／复制／删除也经 editSelection 在改变选择前准备；commitTimelineChanges 将投影存回当前所属定义，以 refresh=false 提交历史，不重建当前视图。
- 通用 loadSelected 保持只从定义载入，避免撤销或重做后被旧投影覆盖；push 同时比较命名库及工程测试定义，仅实际变化才新增历史。维护时覆盖测试／命名 TL 互切、场景往返、关闭重开、录制／片段拖动，以及无变更切换与撤销重做，不在所有刷新入口机械回写。
- 两类通过 IDEA 修改行范围格式化；通知初阶段 12 个 Java 编译和 Data 32s、written=2 保留为历史证据。通知设置补全后最终整合 13 个 Java 经 IDEA 编译成功，isSuccess=true、problems=[]；最终正常 Data exitCode=0、BUILD SUCCESSFUL in 21s，6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=0，相对通知起始仅两份语言资源及对应两个缓存变化。文档通过 IDEA 行补丁更新，仅修改相关系统说明；未新增或运行测试，真实用户交互与丢失现象的修复效果尚未验收，不将前轮提示／按钮的编译、Data 或历史回归结果作为本轮成功证明。

## 2026-10-08：Photon 控件合并、提示换行与复制名称

- 按用户截图与补充要求，运行动画测试组的四个独立按钮合并为“继续／暂停”切换、重启、停止，去掉重复“动画”后缀。切换同时检查当前目标 PLAYING 与宿主推进状态，只有动作变化时更新原生文字及提示；共享 TL 和既有场景重复控制仍沿已有入口，不添加第二套播放控制。
- 空选测试时间轴正文使用父宽度内的原生 WRAP 与自适应高度。PhotonEditorTooltipUtil 为本项目动画 UI 及已登记扩展配置行提供局部提示换行，复用原版 Tooltip 的 170 像素分行和语言身份缓存，以原生 HoverTooltips 绘制；动态消息变化才更换缓存，不修改全局钩子、提示皮肤或配置行提示图标，不在翻译中手工按字符断行。
- 动画列表复制弹窗改为从原名称预填可用名称，重名自动追加序号；保留确认前编辑、唯一性检查、原定义深复制及 UUID 重映射，不改资源结构。
- 关键文件：PhotonAnimationTestControls、PhotonAnimationForms、PhotonAnimationWorkspace、PhotonAnimationUiUtil、PhotonAnimationTextUtil 和 PhotonEditorTooltipUtil；同步两份 Photon 系统说明，只修改本轮相关段落。
- 本轮 7 个 Java 的 IDEA errorsOnly=false 诊断全部 errors=[] 且未超时；修改段范围格式化关闭导入优化与成员重排，其中 6 类共 12 个修改段由主任务处理，TooltipUtil 由并行任务处理。首次编译等待超时，第二次调用被自动审批误归为小改动拒绝；重新通过 IDEA 核对 AGENTS.md 大型修改规则与 7 文件范围后，限定本轮文件的 build_project 返回 isSuccess=true、problems=[]，不将此前超时或拒绝记为成功。
- 正常 IDEA Data 配置 exitCode=0，BUILD SUCCESSFUL in 26s，6 个 provider 全部完成；HashCache total／old／new 均为 19、removed stale=0、written=2。25 个生成文件包含 6 个缓存，前后仅 en_us、zh_cn 及对应两个缓存哈希变化，其余 17 个资源及 4 个缓存完全一致，无删除。两语各 853→854 键，仅新增 photon.lobecorp.animation.copy_name，各更新 continue／pause／play／stop 短名称及 copy.tooltip，其余文本完全保留；未手写或格式化生成输出。
- 预分行后提交 FormattedCharSequence 可能跳过第三方 GatherComponents 对原 Component 文本的处理，仅本项目包装提示走该路径，其他提示入口保留；第三方兼容、实际游戏布局、按钮切换、鼠标悬浮、截图遮挡和复制交互尚未运行验收。本轮未新增或运行测试，不能引用前轮 5745 项模型回归作为本轮 UI 运行证明。
- 三份文档经 IDEA 行补丁更新并读回，不全篇格式化；命名动画说明与修改记录无错误／警告，编辑器说明仅保留修改前已有的设置表格 WEAK WARNING。提示／按钮阶段 Git 为 dev、238 条变更（121 暂存、197 未暂存、0 未跟踪、0 冲突），较开轮 196 未暂存增加的 1 条为原暂存新增 TestControls 的本轮工作区修改；后续通知阶段新增 3 个 Java 被 IDEA 自动加入索引，暂存增至 124，原有 121 项保留，没有手动暂存或取消暂存、创建备份、提交或推送。

## 2026-10-08：Photon 组合测试重模拟与时钟隔离

- 继续补齐独立组合测试。旧 afterSimulation 的单个 Runnable 会覆盖重模拟期间较早的播放请求；改为按点击顺序执行的 FIFO，命令再次引起重模拟时保留剩余队列。普通单目标暂停／停止保留其他目标的宿主继续状态，显式重置、结束、退出模式和关闭时清理。
- 原生 simulateTo 的 reset／emit 和 prepareTimelineSimulation 的 previewOnly 会释放手动组合。工作区重建前冻结手动目标，运行层 PreviewPlayback 深复制类型实参并保存时钟、循环次数、暂停／HOLD、播放时长、完成状态和启动顺序；重模拟只推进 TL，结束后按新库稳定 UUID 恢复普通命名播放，不保留旧运行实例引用，不补发过去 START／MARKER／COMPLETE 或原生信号。当前制作 UUID 由 TL 控制，受控子片段由原生重模拟重建；删除／不兼容目标单独跳过。
- TL 原生 EndMode／Wrap 仅主动 TL 播放时执行，避免右侧手动组合或有限寿命对象触发自动循环重置。播放切换与 seek 区分宿主运行和 TL 播放，非 TL 回拖从 0 原生重模拟，避免宿主时间与独立制作时间错位。显式 reset／finish／close 及退出命名动画模式清理手动恢复状态；退出模式恢复普通场景，不跨退出恢复过期子实例状态。
- 手动播放快照仅保存在工作区内存，不随项目或导出保存；项目专用测试 TL 的制作内容仍只进入 `.fxproj`。原生重置会重建粒子及子 FX，不保留旧粒子年龄或子播放句柄，实际客户端行为单列验收，不以父时钟恢复代替。
- 关键文件：PhotonAnimationWorkspace、PhotonAnimationTimelineContext、PhotonAnimationPreview、PhotonAnimationRuntime／Playback／Clock、PhotonAnimationTimelineMixin、PhotonAnimationSceneSimulationMixin 及 PhotonAnimationPreviewRegression。根因与维护断言同步命名动画系统文档，继续复用原时间轴、播放器与混合器。
- 修正两次全量回归的夹具问题：setPreviewDispatch(true) 重新开启根音频，Data 没有 Minecraft 客户端，夹具每次开启事件后再关闭音频；Controller.library 保存 NBT，Runtime 重读独立库，原夹具错误比较外部定义对象引用并修改外部库，现断言稳定 UUID、在运行态实际定义上模拟签名变更／删除，不添加生产 getter。前两次 Data 任务自身成功但全量回归未通过，不记作通过。
- 本轮相关 Java 已通过 IDEA 修改行范围格式化，关闭导入优化／成员重排，诊断未新增问题；最终 IDEA build_project 返回 isSuccess=true、problems=[]。修正夹具后，第三次新 Data 已加载环境实际执行 PhotonAnimationChecks.runAll()=5745，全部通过。最终 Data 为 BUILD SUCCESSFUL in 1m 27s，全部 6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=0；19 份生成资源 SHA-256 前后完全一致，没有手工修改生成输出。前轮 5679 项、Data 1m 54s 和 6 个翻译键继续保留为下节历史事实，不作为本次新增修复的检查结果。
- 当前 Minecraft 客户端未运行、桥接 connected=false，真实布局／交互、声音、GPU 与子实例验收未执行；PhotonAnimationPreview 既有未使用 inspectTimeline 重载警告仅记录，不扩大处理。仅移除代理创建的调试断点，保留用户原有两个禁用异常断点。
- 两份文档通过 IDEA 行补丁更新并读回，保留既有内容与排版，不全篇格式化。遵循用户“不取消暂存直接修改”，最终 Git 为 dev、238 条变更（121 暂存、196 未暂存、0 未跟踪、0 冲突），与本轮开始索引无变化；未暂存／取消暂存、提交或推送，未写原始二进制资源或创建磁盘备份。

## 2026-10-08：Photon 空选测试时间轴与组合预览

- 命名动画列表允许点击空白取消制作选择；共享原 TL 使用工程专有独立测试定义，轨道、片段、曲线、标记、录制与播放仍沿原上下文。原场景 Timeline 和已命名动画各自保存内容，没有新增时间轴 UI 或第二套播放器。
- 右侧运行动画测试的目标及临时类型实参独立于左侧制作选择，未选中制作动画也能依次启动多个动画组合预览；暂停、继续和停止按目标处理，临时实参在重启／继续时传入，不改默认形参或资源定义。结束特效与停止目标仍分开。
- FXProject 经 PhotonAnimationProjectAccess 懒创建并持有测试定义，默认独立 UUID、autoDuration=true、autoEnableModules=false；只在 serializeProject／deserializeProject 处理 `data/lobecorp_animation_preview`，采用现有 Definition 格式并记录版本 6。工作区关闭不清除工程内容；FX、FXPack 及子模板不经过项目专有字段，不导出测试定义。
- 修复空选路径：Context 旧 null 模型清空且不存储、Workspace 撤销恢复自动补选默认／首动画、无选择播放／检视分支提前退出。统一 `editingDefinition()` 为实际制作目标，以独立 UUID 保存状态；快照新增测试 NBT 与引用身份，测试单独变更也提交历史，主动空选保持。
- 修复库外预览句柄不可操作：旧 playback／pause／resume／stop 按 library.find 查找，project-only 定义未在库中；具体句柄 stop 又绕回名称路径。改为 UUID 实例查询和归属校验的句柄操作，不把测试定义加入可导出库，保留其他运行贡献。
- 补齐 LDLib2 .39 ScrollerView 的空白 flex 包装层命中，点击短列表下方也转发原生 setSelected(null)，行、按钮、滚动条和拖动继续排除；实际鼠标点击另行验收。编辑预览保留对象的判断覆盖库外测试定义，不改变普通运行实例的寿命规则。
- 关键文件：PhotonAnimationWorkspace、PhotonAnimationTimelineContext、PhotonAnimationEditSnapshot、PhotonAnimationRuntime、PhotonAnimationOutline／Forms／TestControls、PhotonAnimationProjectMixin／ProjectAccess／ProjectPreviewUtil；新增 PhotonAnimationPreviewRegression，经 GuiRegression 接入现有工具检查入口。快照夹具仅分配必要字段，不构建 FXEditor 或运行渲染。
- 本轮实际检查：所有本轮相关 Java 已通过 IDEA 范围格式化及诊断，编译成功、problems=[]；Data 已加载环境实际执行 PhotonAnimationChecks.runAll()=5679，包含项目 ValueIO 往返、导出排除、测试模型隔离、快照撤销重做与身份恢复、并行合成及句柄归属检查。Data 最终 BUILD SUCCESSFUL in 1m 54s、全部 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=2；中英文生成 JSON 各由 847 增至 853 键，6 个新增键及中文文本已核对，生成输出只由 Data 写入。游戏内取消选择、组合预览、输入焦点／折叠状态、录制和窗口关闭恢复仍待验收。
- 系统文档与索引通过 IDEA 行补丁更新，没有全篇格式化文档；保留已有工作，未写原始二进制文件、创建备份、提交或推送。IDEA 自动暂存了本轮 4 个新 Java 文件（ProjectAccess、TestControls、PreviewRegression、ProjectPreviewUtil）；仅撤销这 4 个自动暂存的操作被自动审批拒绝，理由为缺少改变 Git 索引的明确授权，目前保持暂存，未改动既有暂存条目。

## 2026-10-08：Photon 不支持旧调用直接弃用

- 按用户最新“不支持的直接抛弃即可”，v1～5 加载保留可解析动画、单个 SELF PLAY 与固定输入；空 START、不支持旧动作／子实例调用／多动作链／动态或非法调用输入整条丢弃，未被支持内容引用的共享参数一并丢弃。生成 migrationWarnings 非阻塞报告，不再因这些弃用项禁用编辑、播放或正常 v6 保存／导出。
- 未知版本、损坏动画／参数结构、未知轨道及无效默认动画引用继续原件保护与保存阻断；没有将弃用旧调用扩大为忽略损坏定义。不支持规则不会猜测为 PLAY 或自动补造子片段。
- 最新指令取代此前三份资源手工写回及 first-effect 的 0～20 tick 补造轨道方案：取消二进制兜底脚本与备份准备，不再询问二进制授权，不手工改写原始资源；通过加载逻辑迁移、正常保存／导出写 v6。下面旧窗口、读取证据和未执行方案仅保留历史事实。
- 已确认失败规则的部分形参污染根因：localParameter 会逐项追加目标形参，后续非法输入使整条调用失败时，前项可能残留。MigrationUtil 按各动画原形参数量回退该次新增项，引用集合只在整条成功后提交，保留原绑定和此前成功规则的参数身份；需覆盖“前项有效、后项非法”、既有参数及未引用参数弃用，不能仅检查 rules 未增加。
- 关键文件为 PhotonAnimationLibrary、PhotonAnimationMigrationUtil 及原生报告 GUI；核心／GUI IDEA 诊断无问题。IDEA build_project 成功、problems=[]；Data 已加载注册表的暂停环境实际执行 PhotonAnimationMigrationRegression.runLoaded()=378，完整 PhotonAnimationChecks.runAll()=5611，全部通过，以实际返回值计数。此前 253／5391 项仅保留历史，不作为本轮重跑结果。
- 本轮只读 scanProjects 重新扫描 11 文件／9 控制器，全部 blocked=false；first-effect.fxproj 丢弃 1 条规则，magic_circle.fxproj／magic_circle.fx 各丢弃 2 条，共 5 条非阻塞警告，其余 6 个控制器无警告。正常保存／导出、原生报告和游戏窗口交互仍未验收，不将模型回归代替客户端表现。
- IDEA Data 最终日志为 BUILD SUCCESSFUL in 2m 8s，所有 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=2；sessions=[]。中英文生成 JSON 各由 845 增至 847 键，只新增 migration_discarded 与 .tooltip 两键；IDEA 索引确认中文文本正确，终端显示乱码不是文件错误。生成输出仅由 Data 更新，未手写或自动格式化。
- 收尾三份原二进制 SHA-256 与此前历史基线完全一致：first-effect 为 2dd4937e4efb52ba4126142d1931eb0cfb3a98a3b26d02abd13b98b6e3726f47，magic_circle.fxproj 为 554ea078662f7c55350ddef1337154cdbe86ab8131af6968e103d53fd29bbfcf，magic_circle.fx 为 12b953e7dac9df5e1442e9f82810f53feaaa63dc00c04c336e64f8447baefad9。未执行二进制写入、未新增备份、未操作 Git 索引、未提交或推送；实际粒子、音频和 GPU 未验收。
- 文档分工通过 IDEA 范围更新命名动画当前指南、历史方案标识及系统索引，三份均已完整读回；命名动画与修改记录诊断无错误／警告，README 仅保留修改前已有的表格 WEAK WARNING。不全篇格式化，不编辑二进制或生成输出，不操作 Git 索引。

## 2026-10-08：Photon 旧资源迁移窗口确认

本节为已被上方最新弃用指令取代的历史方案；二进制写回及补造轨道均未执行，不再等待兜底授权。

- 用户确认选项 1：first-effect.fxproj 的 circle_expand 使用 `[0,20)` 独立触发子动画片段，对窗口内可见的已有及后续实例按句柄去重播放；20 tick 后停止接入，已启动的子动画继续。保留两份 magic_circle 由外部 finish(true) 清理的已确认选择。
- 通过 IDEA 只读复核当前子播放器和轨道末端求值，无需修改播放器；新轨道末端为 20，不延长父制作范围。记录末端时序边界：最后一次窗口内求值后才生成、到 20 tick 才可见的实例不会接入，实际客户端顺序仍待验收。
- 在命名动画文档列出三份文件的具体字段、规则删除范围、v6 读取迁移、未知标签保留及备份路径。仅通过 IDEA 更新这两份文档，三份二进制原件尚未写回、备份尚未创建；非 IDEA 二进制写入兜底仍等待单独授权，不重复询问已确认的窗口与清理语义。
- 修改前后 IDEA 诊断均无问题；仅格式化新建表格 358～362 行，关闭导入优化和成员重排，修正该表格的格式警告。重新核对三份原件 SHA-256 与此前完全一致，Git 状态仍为 117 暂存／192 未暂存／0 未跟踪／0 冲突，未操作索引；本轮未重新运行编译、Data 或回归。

## 2026-10-08：Photon 旧资源迁移前核对

本节保留最新弃用指令之前的读取与检查证据，逐项决定及二进制兜底流程已经取消。

- 用户已确认两份 magic_circle 资源移除空 START 及 fade 完成 FINISH(force=1)，由外部组合控制显式 finish(true) 清理；first-effect 的旧子调用采用独立触发片段。此处记录结果选择，不代表已经写回资源。
- IDEA 调试只读核对实际 NBT：first-effect.fxproj 为 v5，circle_expand 的 MARKER 规则条件名称为 circle_expand_signal，但 markers 仅为 `{time:0,name:"",data:{}}`，没有匹配标记。更正先前两处将规则名称称为实际标记的文字；父片段开始时刻／窗口仍待用户确定，不能猜测迁移到 0 tick。两份 magic_circle 分别为未压缩工程 NBT 和 GZIP 导出 NBT，均为 v3，expand／fade 引用一致。
- 三份二进制原件保持不变；本轮未保存、导出或创建备份，主代理未执行任何暂存或撤销索引操作。只读 SHA-256：first-effect.fxproj 为 2dd4937e4efb52ba4126142d1931eb0cfb3a98a3b26d02abd13b98b6e3726f47；magic_circle.fxproj 为 554ea078662f7c55350ddef1337154cdbe86ab8131af6968e103d53fd29bbfcf；magic_circle.fx 为 12b953e7dac9df5e1442e9f82810f53feaaa63dc00c04c336e64f8447baefad9。完整路径见命名动画保存段。
- 当前 IDEA/MCP 没有二进制写入／复制接口，原生整模型保存不能保证未知标签保留；按 AGENTS.md 的工具边界，若以最小 NBT 脚本兜底需单独授权。当前仅通过 IDEA 修正系统文档与记录，没有修改 Java、运行配置或手工修改生成资源，不将前轮编译／Data／回归结果列为本轮资源迁移成功。
- 本轮实际启动 IDEA Data 调试并只读获取三份原始 NBT 的规则／标记／哈希；完整启动输出尾部为 BUILD SUCCESSFUL in 9m44s。完整内存迁移预演未完成，发起预演时会话已经结束；sessions=[]，代理断点无残留，仅保留用户原有两个禁用异常断点。该结果不证明资源已迁移或客户端验收，不将此前 253／5391 项回归记为本轮重跑。
- 收尾经 IDEA 终端逐文件核对三份完整 SHA-256，与本轮原始 NBT 读取时完全一致；IDEA git_status 的 src/generated/ 条目为空，生成输出无 Git 差异。观察到开轮的 8 个未跟踪新 Java（??）当前均为已暂存新增（A），状态为 117 暂存／192 未暂存／0 未跟踪；主代理没有执行暂存或撤销操作，原因尚未确认，未改动索引，不把观察到的状态变化归因于未经核实的操作。

## 2026-10-08：Photon 子动画继续审查

- 继续审查运行、复制、存储及 GUI，未发现已确认方案中的其他必需遗漏；修正 PhotonChildAnimationTrackEditor 的无效控件：仅 CONTROLLED 显示 child_completion，INDEPENDENT 保留自己的播放时长及子动画完成行为。仅修改 130～133 行，通过 IDEA 格式化 129～135 行，关闭导入优化／成员重排；前后诊断均无问题，全文只有该处变化，新文件未跟踪状态保持。
- 命名动画文档当前用法澄清完整重模拟与被动采样：内部 START／MARKER／SIGNAL／COMPLETE 及子片段继续运行，外部音频、executor 和全局信号回调静默，主制作实例 authoring 身份与后续子实例分开。旧修复和检查证据保持历史，不改写为本轮运行成功。
- 通过 IDEA 的独立 main 调试只读确认三份受阻资源：first-effect.fxproj 的 circle_expand 存在 MARKER 条件下调用 CHILD_INSTANCES 的 expand 规则；本轮续查确认该条件名称不对应现存标记，详见上方迁移前核对。magic_circle.fxproj／magic_circle.fx 均有空 START 动作列表和 fade 完成 FINISH(force=1)。具体 UUID 及路径见命名动画保存段；当轮没有修改二进制资源，也未确定等价迁移方式。
- Minecraft MCP get_status 返回 connected=false、port=null、processAlive=false，ping 为 Mod not connected。仅在项目 lib 和 run/client/mods 范围未检索到桥接，不能据此保证所有配置均缺失，也不能将客户端连接检查当作游戏交互、音频或 GPU 验收。
- 本轮最终 IDEA build_project 返回 isSuccess=true、problems=[]；正常 Data 配置 waitForExit=true、exitCode=0。IDEA 直接读取解析生成 zh_cn／en_us 各 845 键，与前轮最终生成逐键差异均为空，src/generated 目录 Git diff name-only 为空；生成输出没有手工修改或格式化。
- 两文档当前用法与记录经 IDEA 行补丁、读回和诊断无错误／警告，未自动格式化文档。唯一修改的 Java 最终诊断 errors=[]；没有重跑前轮 253／5391 项回归，只读 NBT 调试也不作为本轮新回归证明。实际模式切换和游戏交互仍待验收。Debugger sessions=[]，代理临时断点已移除，用户原有两个禁用异常断点保持；旧资源未保存，未提交或推送。
- 最终 IDEA Git 状态为 234 条（109 暂存、192 未暂存、8 未跟踪、无冲突），本轮 8 个新文件均为未跟踪。cached --raw --no-abbrev --no-renames 与用户批准限定撤回 8 项后的快照字节一致；继续收尾未额外修改索引，没有暂存、提交、推送或磁盘备份操作。

## 2026-10-08：Photon 子动画片段与独立形参

- 按确认方案复用原 TL 添加发射器发射器的子动画轨道／片段。受控模式以父制作窗口驱动完整子动画，独立模式按子时钟运行；支持固定／父形参输入、默认快照和可选实时跟随，不增加独立制作窗口或旧规则播放器。
- 配置升为 v6：Definition 持有独立 parameters／durationParameter，Parameter 使用 UUID；绑定新增 scaleParameter／offsetParameter。Rule 仅保留来源条件及 target／duration／inputs，旧库共享参数及 Action 运行层移除；公开形参操作指定播放句柄，停止／重启不误操作新的 owner。
- 旧版严格迁移：单本实例 PLAY、固定输入与可归属共享参数自动转换；未知枚举／轨道类型、畸形轨道数据、动态共享来源、未归属参数、子作用域及多操作链报告受阻。保留 originalDefinition 副本及只读迁移报告，不写为新格式；Editor 保存／另存、FXData 持久化与 FX／FXPack 导出先检查完整数据再提交，避免部分迁移或保存成功提示失真。
- 原生参数／触发／输入数组及选择保留，动画形参候选按所属签名定位；删除／改型不以同名误处理其他动画或子资源。场景基础 Timeline 没有命名形参来源，子片段只传固定值；不暴露无效速度控件。
- 调整既有状态、运行、GUI 和共享时间轴回归以新契约执行，保留时钟、八类原生轨道、多态采样、合成及历史检查。旧 Action 仅作为迁移 NBT 输入夹具；新增 [迁移检查](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationMigrationRegression.java)及[子片段检查](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationChildRegression.java)，分别纳入 Timeline／Runtime 菜单目录。
- 新增 scanProjects(Path) 只读递归扫描真实 .fxproj／.fx：通过锁定版 IDEA 源核实工程为未压缩 NBT、导出 .fx 为 GZIP NBT；在 type=lobecorp_animation_controller 下读取 definition，逐文件报告控制器数、版本、原始规则／共享参数／形参数量、迁移后的规则／形参数量及受阻原因。扫描不创建项目或预览、不写资源、不 emit 粒子；注册表未完成时返回解析失败，不能报告迁移通过。
- 修正边界并加入断言：零长度窗口进度定义为 1，不做 0/0；父停止／回退清理其已保持末帧的受控贡献，旧 token 无法停止被新 owner 接管的播放。实时跟随在来源结束后冻结最后值，COMPLETE 在 RELEASE 前冻结输入，不从已经移除的播放表反查形参。静默回放仍处理内部规则，原生信号的 executor 与全局监听两路外部回调分别静默；新增真实宿主信号回归。
- 文档当前用法已改为 v6；历史版本的实际检查保留并明确标记。并行文件归属独立，项目文件均通过 IDEA 修改，保存完整文本基线；没有创建磁盘备份，未提交／推送或手写生成输出。IDEA 创建 8 个新文件时自动暂存，安全删除 4 个原暂存新增类时也自动移除其索引条目；首次仅撤回这 8 项自动暂存的操作被审批拒绝，未执行成功，未绕过审批。相对原索引 110 项，当时为 114 项；其他索引内容保持原件。
- 6 个修改／新增验证 Java 已执行 91 次 IDEA 修改行／新类范围格式化，关闭导入优化与成员重排；每文件完整读回确认格式化仅改变空白。验证文件与本次 Java 最终诊断另按整合结果核对，仅 Regression.main 保留既有日志点建议；4 个系统文档仅索引保留原有表格排版弱提示。新迁移结果表仅按新增行范围对齐，没有格式化 JSON、二进制资源或生成输出。
- 修复父暂停只静默父播放器、受控子音频继续的边界：Playback 记录 token 归属的 controlAudioPaused，ChildAnimationPlayer.pause 仅传播给受控句柄，NativePlayer.silence 递归静默且 updateAudio 检查暂停标记；独立播放保持原行为。旋转倍率不再读取 Quaternion 转换后的折回 Euler：先保留原生 apply，再对 angular 曲线取 property.sample 的原角度，之后累计循环与缩放；其他类型保持原生采集。根因和维护断言同步命名动画专页。
- 前轮收尾源码经 IDEA build_project 编译成功，problems=[]；独立 PhotonAnimationRegression 配置 exitCode=0、253 项通过。Data 已加载调试帧中执行完整 PhotonAnimationChecks.runAll，返回 5391 且无异常。旧断言／夹具问题已核对新契约后修正并完整重跑，不以之前的部分分类通过替代整套结果；检查不覆盖实际客户端粒子引擎、窗口、声卡或 GPU。
- 真实旧工程只读扫描两次结果一致：11 文件（6 个 .fxproj、5 个 .fx）、9 控制器、6 可转换、3 受阻，共享参数均为 0。first-effect.fxproj 的 v5 单规则、magic_circle.fxproj 和 magic_circle.fx 的 v3 各两规则不能等价为单个本特效 PLAY；具体路径和报告见命名动画保存段。没有保存迁移后的旧文件，也没有丢弃原规则；需要逐项决定后才能覆盖或导出。
- 最终正常 IDEA Data 配置 waitForExit=true 返回 exitCode=0。IDEA 直接读取并解析生成 zh_cn／en_us，各 845 键，与最终正常运行前逐键比较差异均为空；原生子轨道键 photon.timeline_track.lobecorp_child_animation 分别为“子动画”／“Child animation”。生成目录 Git diff name-only 为空；JSON lint 只返回空 items，不据此声称逐文件诊断完成。生成输出未手工修改或格式化。
- 前轮收尾 Git 234 条（114 暂存、200 未暂存、3 未跟踪、无冲突），与该轮上一核对范围一致；自动暂存与安全删除造成的索引变化按上文记录，未绕过审批、提交或推送。调试会话已结束，代理临时断点移除，用户原有两个禁用异常断点保留。Client／游戏交互尚未验收；布局焦点、实际音频、保存导出、粒子身份及帧率不能以编译或隔离回归结果代替。
- 用户了解效果后明确选择 1，授权仅撤回本轮 IDEA 自动暂存的 8 个新文件。通过 IDEA 执行的限定操作退出码 0；PhotonAnimationInputEditor、PhotonChildAnimationTrackEditor、PhotonChildAnimationClip、PhotonChildAnimationTrack、PhotonAnimationProjectMixin、PhotonAnimationTargetPickerMixin、PhotonAnimationMigrationGuardUtil 和 PhotonAnimationMigrationUtil 均保留为未跟踪，八文件内容哈希不变。其余暂存条目以 cached --raw --no-renames 逐条核对完全一致；没有撤销代码、删除文件、提交或推送，首次审批拒绝仍作为历史事实保留。

## 2026-10-07：Photon 参数入口、标记管理与 12 个接口命名

- 按已确认方案隐藏发射器发射器的光柱长度、宽度及两个覆盖开关；四字段改为仅持久化，字段名、默认值及既有覆盖行为保留。作者运行绑定由 17 项改为 15 项，两条光柱路径移入隐藏兼容列表，旧轨道仍通过原生 resolveRuntimeBinding 解析。
- PhotonAnimationMixer 的无缓存属性查询增加运行绑定回退，不重新开放新增属性入口；原生 Beam 属性、PhotonSpawnOverrides 及光柱代码 API 保留，旧标签翻译和资源格式不变。
- 命名标记改用与触发规则一致的原生 ArrayConfiguratorGroup。不可变 Marker 修改时转移表单身份，由数组统一提交历史；按实际行的对象身份区分相同值条目，不按 equals 或索引替换。保留时间上限、name/data、存储格式及触发机制。
- 通过 IDEA 逐项重新预览并语义改名，仅覆盖下表 12 个顶层常规接口，预览均无冲突。同步实现、泛型、类字面量及有效文档链接；方法、包、网络协议 11、资源 ID 和生命周期未调整，不提供旧名代理接口。

| 旧名称                     | 当前接口                                                                                                         |
|----------------------------|------------------------------------------------------------------------------------------------------------------|
| `EntityStateHolder`        | [IEntityStateHolder](../../src/main/java/org/unitego/lobecorp/world/entity/state/IEntityStateHolder.java)        |
| `HitboxSize`               | [IHitboxSize](../../src/main/java/org/unitego/lobecorp/world/hitbox/geometry/IHitboxSize.java)                   |
| `ToPayload`                | [IToPayload](../../src/main/java/org/unitego/lobecorp/network/IToPayload.java)                                   |
| `ToServerPayload`          | [IToServerPayload](../../src/main/java/org/unitego/lobecorp/network/ts/IToServerPayload.java)                    |
| `ToClientPayload`          | [IToClientPayload](../../src/main/java/org/unitego/lobecorp/network/tc/IToClientPayload.java)                    |
| `ToServerAndClientPayload` | [IToServerAndClientPayload](../../src/main/java/org/unitego/lobecorp/network/tsc/IToServerAndClientPayload.java) |
| `LcCustomAnimatable`       | [ILcCustomAnimatable](../../src/main/java/org/unitego/lobecorp/animation/ILcCustomAnimatable.java)               |
| `ConductorAbility`         | [IConductorAbility](../../src/main/java/org/unitego/lobecorp/conductor/ability/IConductorAbility.java)           |
| `ConductorTargeting`       | [IConductorTargeting](../../src/main/java/org/unitego/lobecorp/conductor/ability/IConductorTargeting.java)       |
| `EntitySkillAccess`        | [IEntitySkillAccess](../../src/main/java/org/unitego/lobecorp/world/entity/skill/IEntitySkillAccess.java)        |
| `ConductorUnitControl`     | [IConductorUnitControl](../../src/main/java/org/unitego/lobecorp/conductor/control/IConductorUnitControl.java)   |
| `SetBuilder`               | [ISetBuilder](../../src/main/java/org/unitego/lobecorp/serialization/ISetBuilder.java)                           |

- 桥接、Mixin Accessor、内部回调及纯注册／工具／常量容器保留名称。AGENTS 明确本次范围及 ITo...Payload 约定；2026-10-05 已批准的包整理计划保留历史名称，当前使用本文对应表。只读 .codex Skill 中旧名称是历史参考，未绕过文件写入边界改写。
- 参数兼容、标记 GUI 与只读审查并行且文件归属不重叠；接口语义改名及文档由主任务串行整合。初始 75 个引用文件保存完整文本快照，61 个 Java 修改前 IDEA 完整诊断为 0 错误、200 条既有警告。
- 维护要点：隐藏编辑控件仍须保留持久化，旧轨道解析不能只查作者属性列表；不可变标记要转移表单身份并只提交一次历史。语义重构后仍须比较完整源码，恢复其附带的无关导入/代码排版变化，不能只看重命名成功状态。
- IDEA 已格式化四个 Photon 修改文件的涉及行，以及 61 个接口/引用文件的 196 个范围；关闭导入优化和成员重排。最终 61 文件的 Java 词法代码及注释与修改前快照仅做 12 个名称映射后完全一致；未自动格式化 JSON、第三方或生成输出。文档仅调整本次新增表及改名受影响的行。
- 最终 IDEA 完整诊断覆盖 65 / 65 个 Java，0 错误、200 条原有警告；按名称映射对照修改前问题，新增及消失均为 0，没有遗漏、超时或截断。13 份有效文档的 22 个接口链接覆盖全部 12 项且路径正确；旧类型在主/测试源码和资源中检索为 0。
- IDEA 项目编译成功，实际执行 compileJava；工具未提供错误/警告计数，null 不当作零。编译保留 Sweeper 过时 API 及未检查操作说明。本轮 Data 退出码 0，HashCache 输出 19 个文件、写入 0、删除 0；逐份生成 JSON 前后内容及目录树均一致，中英文各 820 键，未修改生成源翻译或手写输出。
- Photon 专页与修改记录最终诊断无错误/警告；索引和旧参考表保留表格排版弱提示，不扩大为文档整理。AGENTS 完整读回已核对，文档诊断被 agnix-lsp 二进制下载失败阻断，不能报告检查成功。Data 保留 Sodium 和可选兼容目标缺类等环境警告，不扩展修复。
- 标记身份链补充核实：replaceFormIdentity 不删除旧映射，edit 的 before 使用 savedSnapshot，新捕获才清理旧映射；撤销按保存的原对象引用及 UUID 列表恢复身份，表单状态以 GroupIdentity(UUID, role) 匹配。这是源码链路核对，仍不替代游戏视觉验收。
- 独立回归入口与 Client 本轮未运行。旧项目/.fx/命名轨道保存往返和无缓存查询、标记新增/修改/删除/排序/相同值/撤销重做/折叠选择保持仍待运行验收。既有标记时间上限、跨周期事件顺序及命名动画文档已列遗留不在本轮修复。
- 起始 Git 有 159 条已有变更；最终 226 条（110 暂存、191 未暂存、0 冲突），IDEA 自动登记 12 个接口重命名，保留原暂存/未暂存工作。未手动暂存、提交、推送、重置、删除或创建磁盘备份。文本快照保存在本次任务内存，不能当作磁盘备份。

## 2026-10-07：技能属性自动补齐改用 NeoForge 事件

- 按用户要求，将冷却速度和原版攻击速度的自动补齐迁至既有 `EntityAttributeCreationEvent`。`EntityAttributeCreation.register` 接收实体类型、实际实现类和属性 Builder，仅对实现 `IEntitySkillHolder` 的类补齐缺失属性；手动值与非技能持有者保持。
- 已确认 NeoForge 事件无法从任意 EntityType 取得实际实现类，`getBaseClass()` 固定返回 Entity.class；因此项目实体在属性注册入口提供类元数据，不在事件中构造实体或反射工厂。新增实体应沿用该辅助方法，其他实体仍可显式注册属性。
- 关键文件：EntityAttributeCreation、LivingEntityMixin，以及技能、注册和事件文档。移除构造属性钩子及专用导入，眩晕刺击守卫、死亡清理和移动钩子保持；复用 LobecorpEvents 的唯一入口。
- IDEA 修改与相关行格式化完成，关闭导入优化和成员重排；两个 Java 文件诊断均无错误或警告。语义调用树确认属性登记仍由 LobecorpEvents 唯一入口调用，源码中旧构造补齐方法已无引用。IDEA 编译 isSuccess=true、problems=[]；Data 退出码 0、BUILD SUCCESSFUL。
- Data 日志显示 19 个生成文件、写入 0、删除 0；VFS 刷新后中英文各 820 键，键和值均无变化，生成目录 Git 无条目。日志保留依赖可选 Mixin 的缺失类警告，未扩大处理；未运行游戏或测试。
- Git 基线为 dev、158 条工作（98 暂存、134 未暂存），收尾为 159 条（98 暂存、135 未暂存、无冲突）；仅 EntityAttributeCreation 新增未暂存状态，其余状态与已有工作保留。未手动暂存、提交、推送、重置、删除或创建备份。
- 四个系统文档通过 IDEA 读回；首次并行诊断超时后，重新检查两个 Java 文件及四个文档成功，代码诊断无错误或警告，文档批量分析均为 analyzed、无诊断项。文档批量模式不包含全部编辑器弱提示。

## 2026-10-07：眩晕冷却速度、动作限制与清道夫飞扑抓取

- 已确认：眩晕 I / II / III+ 的技能冷却速度与攻击速度分别减少 25% / 50% / 100%；III+ 立即取消已有技能、停止物品使用并拒绝攻击/交互/使用/挖掘，玩家额外拦截攻击/使用键。冷却速度用于实现 IEntitySkillHolder 的实体及显式注册该属性的实体，不全局加入所有生物。
- 新增 ENTITY_SKILL_COOLDOWN_SPEED 与持久、不额外同步的 EntitySkillCooldownClock；保留原冷却 Long 附件、Codec、倍率与同步，保存小数余量并在服务端 tick/查询/设置推进，支持暂停、覆盖、卸载和旧存档基准。技能默认成功开始时的冷却规则保持。
- 清道夫飞扑朝向与起跳弹道最低仰角 10°；独立飞行命中框按当前攻击属性 15% 结算，每生物同次仅一次。高宽均 <=2.5 格的有效敌人可同时抓取，默认位于来源脚部 (0,0,0)，与旧锚点分离 >=3 格立即脱离且不再抓回；落地、取消、结束、双方死亡或卸载均清理。原落地攻击保留。
- 新增 EntityGrabUtil / EntityGrabState 与非持久 ENTITY_GRAB 附件，只同步当前关系，复用既有 move、实体 tick 与离开入口，不关闭被抓者 AI/攻击/使用/转向，也不拦截直接传送。现有命中框按 tick 当前位置检测，未新增连续扫掠。
- 文件归属按冷却/注册、动作守卫、飞扑/抓取隔离并行；关键文件为 LcAttributes、LcMobEffects、LcAttachmentTypes、EntitySkillCooldownClock、EntitySkillUtil、StunMobEffect、MobEffectEvents、ClientRuntimeEvents、LivingEntityMixin、新 MobMixin、LeapSkill、EntityGrabUtil、EntityGrabState、EntityMixin、LivingEntityEvents 及 Mixin 配置。IDEA 编辑及修改行/新类格式化，关闭导入优化和成员重排，资源保持原排版。
- 已完成技能施放/取消/覆盖/冷却设置及动作守卫、抓取/释放入口的 IDEA 语义引用复查，最终相关 Java 与 Mixin 配置没有新增错误或警告；EntitySkillUtil 原有 14 条、LcAttributes 原有 4 条、LcMobEffects 原有 4 条、LeapSkill 原有 5 条、LivingEntityEvents 与 EntityMixin 原有各 3 条警告保留。IDEA 编译 isSuccess=true、problems=[]；随后正常 Data 再执行 compileJava，退出码 0、BUILD SUCCESSFUL。日志中的清道夫过时 API、未检查操作和依赖警告未扩展处理。
- Data 日志确认 19 个输出、写入 2、删除 0；VFS 刷新后中英文各由 819 增至 820 键，各仅新增 attribute.name.lobecorp.entity_skill_cooldown_speed，原键和值零变化，生成目录 Git 无条目。生成输出只由 Data 更新，没有手写或格式化。未执行游戏或测试，抓取的玩家连接、多人插值和瞬移互动未运行验收。
- 发现并记录既有 NoAI 持久化恢复问题：卸载只清内存记录，重载后可能无法恢复眩晕关闭的 AI；本轮未扩大修改。维护时不能把该风险当作本轮新增冷却或抓取逻辑已解决。
- 系统文档已通过 IDEA 读回及诊断；索引与事件文档保留表格排版弱提示，不扩大整理既有文档。
- 启动基线 128 条 Git 工作（92 暂存、103 未暂存），收尾检查 158 条（98 暂存、133 未暂存、无冲突），包含范围外并行工作并保留；新 MobMixin、EntityGrabUtil、EntityGrabState、EntitySkillCooldownClock 与抓取文档由 IDEA 自动登记暂存，格式化后的追加变化以 Git 为准。未手动暂存、提交、推送、重置或删除，未创建备份。

## 2026-10-07：中文注释规则与存量说明翻译

- AGENTS 增加注释说明统一中文的规则，覆盖行注释、块注释、Javadoc 和常量说明；保留必要类型/API 标识、标签、链接、代码示例和 IDE 指令。第三方 SetCodec 的版权及 MIT 授权原始声明保持原文。系统索引同步修改流程。
- 并行按遮挡绘制、遮挡管线、遗漏审计分配互不重叠文件。最终复查手写源码、测试、构建配置、工作流及资源注释，核对 190 个文件、847 个含字母的注释上下文；未发现剩余外语说明句，技术片段和注释掉的代码不当作外语说明翻译，生成输出、第三方、备份和嵌套运行仓库不纳入改写。
- 实际翻译 31 处：8 个 Java 中的 28 处、build.gradle.kts 的 1 处和 .github/workflows/build.yml 的 2 处。Java 涉及 PhotonOcclusionDrawUtil/PipelineUtil/Util、PhotonWorldRenderStateMixin、EntitySkillDebugUtil、AttackEntity、PhotonAnimationMixer 和 ConductorRenderStateRegistration；保留所有业务代码、字符串、注入目标和原排版。
- 所有编辑通过 IDEA Document API 完成，并仅格式化对应注释行，关闭导入优化和成员重排。完整内容与逐条翻译预期对照，Java 去注释词法对照一致，原生读取与 Document 读回一致；没有使用命令行编辑或格式化资源、生成文件。
- 维护要点：部分操作返回成功后读回仍有英文残留，具体环境根因未确认；本轮通过最小替换、逐次读回和再次范围格式化修正。后续不能仅以工具 success 判定已落地，必须核对完整最终内容及非注释内容。
- 最终 IDEA 诊断没有新增错误或警告；AttackEntity 原有 9 条未使用警告、build.gradle.kts 原有 1 条冗余限定符警告及索引既有表格弱提示保留。AGENTS 专项诊断因 agnix-lsp 二进制下载失败受阻，正文已通过 IDEA 读回确认，不记为诊断通过，也不扩展为环境修复。
- IDEA 编译 isSuccess=true、problems=[]；正常 Data 退出码 0，日志确认 19 个输出、写入 0、删除 0。刷新 VFS 后中英文各 819 键且完整内容与运行前相同，生成目录 Git 无差异。未执行测试或 Client，不把编译/Data 视为游戏验收。
- 同轮新增接口命名规则：常规接口以 I 开头，纯注册、工具和常量用途按实际职责作为例外。只读调查已区分业务合同、实例桥接、Mixin Accessor 和嵌套回调；现有接口尚未重命名，专用合同是否纳入仍待用户确认。
- 启动 Git 基线 128 条（92 暂存、103 未暂存），检查时为 145 条（96 暂存、119 未暂存、无未跟踪或冲突），期间存在本轮范围外的并行工作并原样保留。本轮未手动暂存、提交、推送、重置或删除，未新建或移动备份。

## 2026-10-07：可选的不透明像素遮挡

- 用户选择方案 1：至少保证不透明部分的几何遮挡，半透明继续 Photon 原流程。新增渲染器原生 ToggleGroup“不透明像素遮挡”，默认关闭；Alpha 阈值默认 0.99、有限范围 0～1，旧文件缺数据保持关闭。最终覆盖同时读取纹理、顶点/粒子颜色、ColorModulator Alpha，并遵守原 DiscardThreshold；Alpha=0 不写遮挡，淡出后不保留整片挡板。
- 根因由锁定 Photon 26.1.2.2 源码确认：同 stage 按 Order/发射器距离排序不能表达相交面的逐像素关系，默认材质不写深度；直接深度写入又会把透明边缘当挡板。新处理在原上传及 scene capture 后、所有主颜色 draw 前预写全部支持作业的裁剪深度，再沿原颜色 Shader、Blend/混合方程、HDR、Bloom、Mask/后处理绘制；不更改旧默认路径或原阶段/目标。
- CPU 作业独立包装 RenderType，实例化作业独立 Programs；effectiveEquals/hash 同步隔离开启态/阈值，双方关闭保留原返回。原 material UBO 通过实际 lambda$drawRun$0 的查询回到源 RenderType，几何/layout/位置偏移/MaskWrite 及原资源释放链保留；帧末清标记，资源重载清本扩展包装/管线缓存，Photon 共享 UBO 仍归 Photon。
- 审查修正共享 sequential 索引扩容关闭旧 buffer、SHORT/INT 切换导致预写引用失效的风险：按 AutoStorageIndexBuffer 身份先聚合最大数量预留，再准备每项 buffer/type。所有可能上传的查找先于 RenderPass；借用 buffer 不关闭，实例化 begin/finally/end 配对。Mixin 字节码确认 drain 的 NEW ArrayList ordinal=1 和 cache/colorTexture/depthTexture LocalVariableTable；原 drawRun 的查询在 synthetic lambda 内，不能仅根据源码排版确定注入方法。
- 仅支持原 core/particle 顶点搭配 hdr_particle/pixel_hdr_particle/sprite_hdr_particle（普通及原7种实例化变体）；原 PixelArt/Sprite UV 和 Alpha 复现。自定义 Shader、ShaderGraph、线框保持原作业/管线。不同 stage/INLINE/LATE/DEFERRED/attachment 之前混入的颜色不能回撤；共面精度和半透明 OIT 不承诺处理，本次未改变 Iris 路由。
- 并行按配置/原控件/翻译、纯管线/Shader、独立回归菜单分工；root 负责作业标记、深度绘制、共享 Mixin/JSON 与文档，后续交叉只读审查。17 个 Java 经 IDEA 新类/修改行格式化，导入优化和成员重排关闭；JSON、Shader 原资源和生成输出未自动格式化。最终 Java/Mixin 静态检查无新增错误/警告，仅 EmitterMixin 原有8条局部名称建议、TranslationKeys 原有12条未使用字段警告保留。
- 关键文件：PhotonOcclusionAccess/Config/Util/DrawUtil/PipelineUtil/Regression；RendererSettingMixin/RuntimeMixin、EmitterMixin、PhotonWorldRenderStateMixin、新 PhotonRenderTypesMixin 和 META-INF/lobecorp.mixins.json；PhotonEditorSettings/Tools/TestMenu、TextUtil/TooltipUtil/TranslationKeys 与独立 photon_occlusion_depth.fsh。设置 opaque_occlusion 默认显示控件，只管理 UI；工具→测试新增“不透明遮挡与原生管线”，纳入全部测试。
- IDEA 编译 isSuccess=true、problems=[]；正常 Data 退出码0。项目内 Data 最新日志确认19输出、写入2、无删除；VFS刷新后中英文各819键，相比原811各新增8个预期遮挡/阈值/设置/测试及提示键，原文本零改变。生成输出仅由 Data 更新，当前生成目录 Git 无条目。
- 隔离回归覆盖默认/旧存储/非法阈值、config/runtime合批等价、CPU/全部实例化声明、三种原材质保留与未知/线框回退、空作业边界；本轮未执行这些断言、Client、GPU Shader 编译或游戏视觉/性能测试。真实交叉法阵、多Order、透明孔/边缘、淡出、实例化、多视图、Bloom、世界和Iris组合需单独验收。编译/Data 不等同于 GPU/Mixin 完整运行。
- 同步粒子渲染、编辑器和系统索引，记录根因、防回归与限制。保留启动时116条 Git 工作（83暂存/98未暂存/1未跟踪），收尾当前128条（85暂存/103未暂存/7未跟踪、无冲突）；未手动暂存、提交、推送、重置或删除，未新增/移动备份。IDEA格式化一度自动审查超时，重试修改行成功；没有转用命令行编辑。

## 2026-10-07：发射形状预览与原生参数、规则编辑

- 用户确认每个参数的一组 min/max 共同约束所有分量；整数继续使用 float[]，精确整数范围为 ±16,777,216，旧超范围整数数据读取时也限值。新增浮点 vec2/vec4 与整数 vec2/vec3/vec4，原 NUMBER/VECTOR/INTEGER/BOOLEAN/COLOR 保存标识保留；definition version=5，继续读取 1～4，第三版时长/模块缺省规则不变。
- 发射器发射器在原选中对象的 drawEditorAfterWorld 中复用 Box/Sphere/Circle 导线及原形状显示开关，完整父级变换只应用一次；BOX 半尺寸转换为原生整尺寸，Circle 位于 XZ 平面，POINT 不画范围，零长度/非有限导线跳过。没有改动形状随机采样、生成位置、子实例继承或存储。
- “动画监视”中文名称改为“动画检视”。参数定义、触发规则、动作和输入使用原 ArrayConfiguratorGroup/ConfiguratorGroup 的边框、拖动与加减入口；保留参数重命名小窗及引用维护。布尔复用开关，颜色选择器与 RGBA 数字字段并存，数值控件遵守类型及有效范围；颜色仅读取不回写 8 位预览值。
- 修正整数先限值后取整造成的越界：先计算有效整数区间，空区间拒绝。改类型/范围先校验默认值和 SELF 固定动作/输入再统一提交，不把父参数约束写到子资源同名目标。原生数组选择按模型 UUID/职责恢复，删除项不套用同位置条目；拖动和颜色弹窗焦点期间延迟表单重建。
- 并行文件归属分为发射形状两文件、三份原生列表编辑器、参数模型/配置与 GUI 回归；root 整合 UI 工具、翻译、版本、工作区/状态、原生一致性回归及文档，无同文件并行写入。14 个 Java 完成 IDEA 类/修改行格式化，导入优化与成员重排关闭；最终诊断无错误/警告，仅 Regression.main 保留原日志点建议。
- IDEA 编译 isSuccess=true、problems=[]；正常 Data 退出码 0。刷新 VFS 后中英文各 811 键，相对原 804 各新增 7 个预期分量/类型键，无删除；英语更新 5 项、中文更新 6 项，均为原数值类型名称、参数范围/值提示及中文面板名称。生成文件没有手写或自动格式化，生成目录 Git 差异为空。
- 扩展配置/GUI 回归，覆盖类型、旧数据、整数区间、布尔/颜色、SELF/CHILD 迁移、原生控件及数组选择；原生一致性补第四版迁移断言。本轮未执行这些测试或 Client，不能把编译/Data 当作回归或游戏验收。实际各方向形状显示、父级缩放、颜色弹窗、列表拖动/选择、折叠和撤销仍待验收。
- IDEA 补丁写入一度遇到 Windows 映射区域占用，失败后实际文件已有部分修改；按修改前上下文通过 IDEA Document API 恢复原尾部，再检查完整内容、诊断和编译。补丁与打开的 Document 也可能不同步，最终相关文件两种读取已核对一致；没有使用命令行编辑绕过。
- 同步系统索引、命名动画、发射器与编辑器文档，并记录根因及防回归要点。保留原 115 条 Git 工作，当前 116 条（83 条暂存、98 条未暂存、1 条未跟踪、无冲突）；未手动暂存、提交、推送、重置或删除，未新增或移动备份。

## 2026-10-07：组合动画调用子动画与编辑状态保留

- 用户选择触发后子动画独立运行，并要求参数输入、调整与连续传递。复用原 SIGNAL/MARKER/TRIGGER 队列、子实例句柄和 PhotonAnimationRuntime 公开接口；新增动作作用域 SELF/CHILD_INSTANCES、ALL/LATEST、SET_DURATION/TRIGGER 和输入映射，不新增轨道、播放器、依赖或同步协议。
- PLAY/CONTINUE 将输入写入该次播放的局部覆盖，TRIGGER 验证并提交实例共享参数后入队；时长和 SET_PARAMETER 的值支持父参数来源，优先读取触发动画局部覆盖。输入按值复制，完成事件保留来源播放引用；参数校验失败不释放旧动画或提交半套共享值。子动作只处理当前存活实例，无自动发射/未来排队，父停止或暂停不自动停止子动画。
- definition version=4，兼容 1～3，第三版自动时长/模块缺省规则单独保留。复制只映射父 spawner，不改写子动画/对象 UUID；父参数改名同步来源，SELF 目标迁移保留，CHILD 目标不按父库删除或改型。删除父参数清理 SELF setter/input 目标，来源回到已保存固定值，子资源同名目标保留。项目模板只过滤父 spawner 超出复制范围的子动作。
- 移除魔法阵示例按钮及表单入口，保留示例构造代码和独立测试入口。修复普通编辑后的原生折叠、滚动和有效 TL 选择恢复；界面状态按临时模型身份保存，撤销恢复令牌，不写 NBT。切换场景/命名模式只切上下文，不主动抢选外侧标签。
- 并行按运行派发/运行回归、工作区状态、动作 GUI/翻译分工，root 负责模型、引用、参数编辑、配置回归和文档，无同文件并行写入。系统索引、命名动画与发射器文档同步接口、制作步骤、根因、防回归与限制。
- 关键文件：Action/Input/Library/Definition、DataUtil/EmitterAnimationUtil；Runtime/Playback/ActionDispatcher；Workspace/FormState/Panels/Outline/Forms/Context/Model/EditSnapshot；RuleEditor/ActionEditor/ParameterEditor/BindingEditor/TextUtil；RuntimeRegression/GuiRegression/TimelineRegression。新增 4 类沿既有配置、运行和编辑职责包放置，无包迁移、共享 Mixin 或 JSON 改动。25 个 Java 已 IDEA 修改范围/新类格式化，关闭导入优化与成员重排；最终分组诊断均无错误/警告，文档仅索引保留既有表格排版弱提示。
- IDEA 编译 isSuccess=true、problems=[]；正常 Data 退出码 0，19 个输出写入 2 个语言文件、无删除。刷新 VFS 后中英文各 804 键，运行前 749：各新增 55 个预期组合控件/提示和枚举键，更新 action.tooltip/rules.tooltip，无删除及其他值变化。生成输出未手写/自动格式化，生成目录 Git 差异为空。
- 新增运行回归 48 条断言（预期由 56 增至 104），配置回归覆盖 NBT、跨资源映射、父参数删除/改型、分组身份/职责；原生一致性更新当前版本并补第三版缺省兼容。新增及既有测试本轮均未执行；未启动 Client。实际生成时序、ALL/LATEST、跨层局部输入、音频/信号和界面布局交互单独待验收。
- 标记编辑仍按固定 duration 限制、直接 Runtime.play 已有实例的非法时长输入差异已记录为独立遗留；临时参数刷新、模板基础变换复制和多循环 MARKER 顺序保留原未修状态，没有扩展修改。
- Git 保留原 111 条工作及暂存/未暂存内容，现 115 条（83 条暂存、97 条未暂存、1 条未跟踪），无冲突；3 个新文件被 IDEA 自动登记，ActionEditor 保持未跟踪，旧 Model 新增未暂存修改。未手动暂存、提交、推送、重置或删除，未新建/移动备份。

## 2026-10-06：减少跨按钮悬浮的整树样式更新

- 用户确认现象为进入/跨按钮移动时短暂掉帧，并选择实施最小方案 1。源码确认旧/新悬浮链共有的祖先仍被移除再添加 __hovered__，触发 StyleEngine 递归重匹配整棵编辑器；修复该冗余不等同于已证明 FPS 主因或测得性能提升。
- 新增客户端 ModularUIMixin，只包装 refreshHoveredElement(FF)V 的唯一 UIElement.removeClass(String)。当前 Photon 项目、设置开启且新缓存路径仍包含正在移除的元素时保留原类；其余走原调用。原 addClass 对已有类自动跳过，无需第二包装、全方法替换、二次 hitTest 或 UI 引用缓存。鼠标事件与路径更新保留，差异分支仍递归刷新；作用域复用 PhotonEditorSaveUtil，涵盖主项目和浮动 EditorHost，其他 UI/无项目保留原逻辑。
- PhotonEditorSettings 的预览分类新增 hover_style_delta，沿现有 FEATURES 派生目录和 features Codec 默认开启；PhotonEditorTextUtil/PhotonEditorTooltipUtil 集中提供标识、名称及原生提示。Mixin JSON 只添加一项客户端注册，未格式化资源。此轮不改粒子运行、动画、保存格式或 Tooltip 渲染，原空目标 hover 类可能残留的问题单独记录、尚未修复。
- 并行按文本/提示两文件与 root 的 Mixin/设置/资源/文档分工，无同一物理文件并行修改。4 个 Java 仅 IDEA 新类或修改行格式化，导入优化与成员重排关闭。IDEA 初检建议匹配目标参数 clazz 并按 newHoveredElement 名称捕获局部；范围内调整后最终 Java/Mixin 配置诊断无错误/警告。当前 .39 方法/调用描述符与局部身份经过源码、字节码和 IDEA 检查，更新依赖须重新核对。
- IDEA 编译 isSuccess=true、problems=[]；正常 Data 退出码 0，19 个输出中写入 2 个语言文件，无删除。刷新 VFS 后中英文各 749 键，仅新增 feature.hover_style_delta 及其 .tooltip 两个预期键，无旧值变更/删除；生成资源未手写或自动格式化，生成目录 Git 差异为空。系统索引与编辑器文档同步行为、源码根因、开关及防回归要点。
- 未执行 Client、性能采样、开关对照或独立测试；按钮/菜单/拖动/滚动/浮动交互与实际帧率改善仍待游戏内验收。保留既有 110 条 Git 工作；新增 Mixin 经 IDEA 自动加入索引，范围内修正后显示 AM，共 111 条（80 条暂存、94 条未暂存），无未跟踪/冲突。未手动暂存、提交、推送、重置或删除旧文件，未新增/移动备份。

## 2026-10-06：项目全局保存与按钮悬浮性能调查

- 按本轮需求补齐 Ctrl+S：新 PhotonEditorSaveUtil 按主 EditorWindow.currentEditor 或浮动 EditorHost 解析当前 Photon 项目；没有祖先时仅回退唯一可见且活动的主窗口，不缓存跨项目引用。ModularUIWidgetMixin 只包装 keyPressed 的原 SAVE 验证/执行分发，并登记 META-INF/lobecorp.mixins.json；未命名项目复用原另存为，验证阶段不保存，其他命令和非 Photon 项目不变。
- 源码及字节码已确认根因：LDLib2 .39 的两个 command event 创建器关闭捕获/冒泡，有焦点时 SAVE 只到焦点元素，无法到 Editor 保存监听；无焦点的浮动根也没有该验证器。现定向原 Editor 分发，不新增原始 Ctrl+S 监听、不全局改命令阶段，避免双重保存。修正此前关于 Dialog Ctrl+Z 冒泡到历史的错误源码判断，原失效目标/索引防御校验保留。
- 两个新增 Java 通过 IDEA 类范围格式化，关闭导入优化与成员重排。IDEA Java/Mixin 配置静态诊断无错误/警告；语义引用确认保存解析仅两个适配调用。IDEA 编译 isSuccess=true、problems=[]，正常 Data 退出码 0，19 个生成输出无写入/删除；刷新 VFS 后中英文各 747 键，与本轮生成前逐项一致。未手工编辑或格式化生成资源。
- 悬浮调查并行只读核对依赖渲染管线和项目扩展：原 refreshHoveredElement 对共同祖先重复切换 __hovered__，可导致整棵编辑器样式重新匹配；稳定提示每帧收集、转换、测宽及触发 NeoForge 提示事件。未发现扩展普通按钮悬浮时重建动画面板/扫描整树/重复注册提示、SDF 默认提示字体或默认模糊。上述是已确认代码开销，FPS 主因仍需运行采样；本轮未修改 hover/tooltip/render 代码、执行 Client、性能对照或独立测试。
- 系统索引与 photon-editor.md 同步保存接口、命令阶段陷阱、悬浮调查证据和后续核实范围；文档仅保留原索引和功能表的两条排版弱提示，不扩大无关格式调整。真实 Ctrl+S 焦点、浮动、通知、另存为与按钮静止/移动性能仍待游戏验收；编译和 Data 不作为这些交互或帧率的成功证明。Git 保留 dev、原 108 条状态和既有暂存/未暂存工作，仅新增两个 Java 被 IDEA 自动加入索引后共 110 条（79 条暂存、93 条未暂存）、无未跟踪/冲突。未手动暂存、提交、推送、重置或删除既有工作，未新增或移动备份。

## 2026-10-06：三轴缩放曲线、缓动选择与窗口标题设置

- 已确认选择 2：对象缩放的 XYZ 按相同时间/数值增量联动，保留各轴差异；同向控制手柄也联动，各轴仍保留自身对齐约束。PhotonScaleCurveUtil 按时间而非索引匹配已有点、从原 dragSnapshot 恢复后共同限幅；拖动不补齐缺失点，新增则同步三轴且遇同时间冲突整次拒绝。AnimationTrackEditorMixin 保留原单选手柄、框选、Shift 锁轴、Alt 复制、Esc 和一次原生撤销。
- PhotonCurveEasingUtil 通过原“+ 添加属性”选择数值曲线的贝塞尔缓动，后续新增点时应用到相邻有效段；颜色/离散及表达式/函数片段覆盖仍走原规则。PhotonEditorTools 增加原生框选后的批量缓动窗口；PhotonCurveEditUtil 与 PhotonCurveViewAccess 同步原 noteRecordingEdit，避免工具修改误录为基础参数变化。实际形状沿原 Codec 保存，当前曲线的临时新增点偏好随编辑身份释放，不新增资源格式。
- 当时仅根据分发器误判 Dialog 中原 Ctrl+Z 仍能到达历史；同日全局保存调查已通过命令创建器纠正：键盘命令关闭捕获/冒泡。原创建确认的 targetId、目标身份及可动画属性校验，批量确认的轨道/属性挂接、锁定和点/手柄快照校验仍保留，防止其他入口导致状态失效；使用原 invalid 提示拒绝，避免改错点或对象。
- PhotonEditorTitleUtil 和 EditorWindowMixin 仅装饰 Photon 项目窗口标签：类型 + 文件名与完整后缀；同窗口内同类型/文件名重复时显示完整路径，悬浮显示 literal 绝对路径。包装原创建及 TICK lambda 的 getTitle 调用，解决追加 TICK 监听先执行而被原回调覆盖的问题；Editor#getTitle、保存确认、未命名项目和其他编辑器标题保持原语义。META-INF/lobecorp.mixins.json 只登记新增混入，未自动格式化资源。
- PhotonEditorSettings 新增六个默认开启的曲线/标题开关以及曲线默认缓动；旧配置缺省线性，原 features/keys/presets 保留。PhotonEditorTextUtil 与 PhotonEditorTooltipUtil 集中注册名称、菜单及悬浮提示；系统索引与 photon-editor.md 同步操作方式、源码根因、防回归和已知边界。
- 并行按曲线联动、缓动、标题分配互不重叠文件，root 统一设置、资源和文档；审查期间发现的手柄逐轴对齐、弹窗失效及新代码未使用警告均在本轮范围内修正。11 个 Java 仅 IDEA 修改段或新类格式化，关闭导入优化和成员重排。最终 IDEA Java/Mixin 配置诊断无错误/警告，编译 isSuccess=true、problems=[]；两份既有 Markdown 表格排版弱提示保留，不做无关排版清理。
- IDEA 正常 Data 退出码 0，生成日志 19 个文件、写入 2 个、无删除。刷新 VFS 后读回中英文各 747 键（原 729 + 18），各仅新增本轮预期名称/提示，无旧值变化或删除；未手写或自动格式化生成输出，生成目录 Git 差异为空。未运行 Client、独立测试或游戏交互；三轴拖动/录制/撤销、弹窗、标题重名和悬浮仍待游戏内验收。
- Git 保留 dev、原 103 条状态及既有暂存/未暂存内容；新增 4 个 Java 被 IDEA 自动加入索引，原已跟踪接口新增未暂存改动状态，共 108 条（77 条暂存、93 条未暂存）、无冲突。未手动暂存、提交、推送、重置、删除、迁移包，未手动创建、移动或删除备份。

## 2026-10-06：时间轴曲线初始化时自动适配上下范围

- 按本轮需求，场景与命名动画共享时间轴的数值曲线首次显示时适配已有数据；无有限数据的原生角度属性使用 -360～360、其他数值使用 -1～1。原速度/布尔固定下限、颜色渐变界面及参数合法值规则保留。
- 源码根因：原 AnimationTrackEditor.buildExpandedRight 不主动适配，fitRangeToKeys 仅在录制时扩张范围；初次自动选择只设置属性，不调整上下界。修改现有 AnimationTrackEditorMixin，在右侧曲线构建 HEAD 按属性弱引用标记执行一次，复用 fit_view 开关；普通重建、折叠及共享上下文恢复不重复适配，保留手动视图。
- PhotonCurveEditUtil.initializeRange 复用键/切线范围计算及上下界写入，覆盖全部分量；表达式复用原 evalExpr，在有效片段内有限采样，近似范围的高频/不连续限制已记录。常量使用默认半幅并考虑 float ULP，跨度以 double 计算，范围保持有限且可表示；不会修改关键帧、基础参数、时间轴时间或运行时控制。
- 通过 IDEA 语义引用核对 initializeRange 仅由原曲线编辑器初始化调用，原手动 fit 入口仍唯一且保留选中关键帧语义；并行只读审查原 UI 构建顺序、Mixin 签名及属性引用恢复。所有修改仅 root 编辑，无同一物理文件并行写入。
- 两个 Java 仅执行 IDEA 行范围格式化，关闭导入优化和成员重排；最终 IDEA 静态诊断无错误或警告，编译 isSuccess=true、problems=[]。正常 Data 退出码 0，IDEA 刷新并读取生成资源：中英文各 729 键，文本与本轮生成前完全一致，生成目录无 Git 差异；无新翻译、手工输出修改或资源格式化。
- 系统文档补充初始化行为、确认根因、浮点边界及防回归维护要点；本轮未运行 Client 或独立回归测试，未实测范围标签、滚轮/中键、实际模式切换和表达式极值显示，不能用编译或 Data 代替游戏验收。
- Git 保持 dev、73 条暂存及无冲突；保留原 102 条既有修改，仅新增原已跟踪 AnimationTrackEditorMixin 的未暂存改动状态（总计 103 条、88 条未暂存）。未提交、推送、重置、删除、移动或新建备份。

## 2026-10-06：场景基础时间轴与命名动画的单向关系

- 按本轮需求，场景时间轴作为固定基础动画，仍按原 TL 的时间规则运行；场景窗口只运行基础动画，命名模式在基础动画当前值上合成独立触发的命名动画。停止命名层保留其他贡献和原 TL，不新增播放器或改写资源格式、动画时长和优先级规则。
- 源码根因：返回场景的 Preview.restoreScene 直接 reloadAnimations，SceneView.play/simulateTo 的 emit 再次创建默认可自动启动的运行态；下一 tick 的默认动画/START 规则和命名 keepAlive 因此仍影响场景。仅 previewOnly 不能完全隔离规则及保活，改为场景模式不挂接控制器。
- 修改 PhotonAnimationRuntimeAccess 新增 setScenePreview，FXRuntimeMixin 按主实例持久标记立即关闭旧运行态并阻止重建；PhotonAnimationSceneSimulationMixin 在 play/反向重放的首次 emit 前设置模式，保留原信号执行，命名作用域沿原 PreviewScope。PhotonAnimationPreview 在借用时解除标记、退出时关闭，恢复原 tick/frame、属性和保活；原 TL 循环直接 emit 继承标记，prepareTimelineSimulation 仍只用于 simulateTo，不在 play 额外重置动画。
- 主场景标记只从 SceneView/Preview 设置，不按 DummyWorld、执行器或宿主全局禁用；其他世界/子 FX 保留原默认动画和 START 能力。修改前后经 IDEA 核对唯一接口实现与全部语义调用点，独立只读审查首次加载、基础重载、模式切换、关闭及直接循环路径。
- PhotonAnimationTimelineRegression 新增 scenePreviewGate、sceneTimelineBase，共 52 项加载断言。覆盖反复重建门禁、原回调和保活身份、显式命名预览、另一实例、原 TL 动态基准 → ADD → MULTIPLY、停止剩余合成、整数/小数时间和重复帧不累计。夹具调用真实 reload/begin 链，未向客户端引擎 emit 粒子，不能替代真实窗口操作。
- 实际 IDEA 编译成功；Data 已加载调试执行完整 runLoaded=4812（本轮前 4760+52），正常 Data 退出码 0。刷新生成目录后由 IDEA 读回，中英文生成文件各 729 键，文本与本轮生成前一致；未新增翻译键或手写生成输出。
- 五个修改 Java 均仅 IDEA 修改行格式化，关闭导入优化和成员重排。最终 IDEA 代码诊断无错误和新增警告，保留修改前已有的 Preview.inspectTimeline 三参数重载未使用提示；最新编译错误/警告为 0。系统文档同步关系、根因和维护要点，未执行 Client、真实标签切换、GPU 或子发射器实际运行验收。
- Git 仍为 dev，102 条已有修改，无冲突，前后新增/移除/状态变化均为空；保留已有 staged/unstaged 工作，未提交、推送、重置、删除、移动或新增备份。临时 Data 断点自动移除，原两个禁用的用户断点保持，调试会话已结束。

## 2026-10-06：修复去重后再次出现的动画状态翻译崩溃

- 用户 19:42 客户端日志确认 IllegalArgumentException：status_format 收到 []，鼠标点击示例后 Forms.liveStatus 构建以及后续 Ticking screen 均由 Workspace.animationStatus 触发。根因是上一轮提取状态显示时遗漏已修复的 List<String> → String 转换；本次不是启动依赖或 SceneInfoMixin 生命周期错误。
- Workspace 改为调用现有 PhotonAnimationUiUtil.animationStatus，统一状态显示边界，问题列表转换为 String；保留状态翻译 Component、时间/进度/循环数字、原翻译键及占位数量，不捕获异常掩盖问题、不改动画运行与资源。
- PhotonAnimationGuiRegression.runLoaded 新增四种 State × 空/非空问题列表的实际生产显示入口检查，共 120 项断言，逐个确认五个翻译参数合法类型、状态翻译及数值/问题内容；未加载检查不包含该项，避免跳过 NeoForge 开发校验造成假通过。
- 实际 IDEA 编译成功；Data 已加载阶段经调试表达式执行完整 runLoaded=4760（原 4640+120），正常 Data 退出码 0。中英文生成文件各 729 键，IDEA 读取文本与本次生成前完全一致，无资源变更、手写生成输出或新增翻译键。
- 修改 Java 仅 IDEA 行范围格式化，关闭导入优化及成员重排；四个相关 Java 与两份文档最终 IDEA 分析全部完成，无错误/警告及超时，最近构建诊断错误/警告均为 0。已有 staged/unstaged 工作保留，未提交/推送/重置；无文件删除或移动，无新增备份。临时断点命中后自动移除，原两个关闭的用户断点未变，Data 调试已结束。
- 未运行 Client 或点击真实编辑器，游戏内复核仍待执行。修复记录明确保留重现原因：文档中的既有修复必须在提取/合并显示时保留，回归直接调用真实生产格式入口，不能只构造一份相似 Component。

## 2026-10-06：Photon 播放入口、采样、工具与测试目录去重

- 按用户选择 1 实施已汇报范围，并行分工 GUI、运行层和工具；共享 Mixin、翻译、文档和最终检查由根任务整合。保留用户原暂存/未暂存修改，未手动暂存、撤销暂存、提交、推送或重置。
- 中央命名动画重复播放栏/状态栏移除；底部实际 owner.timelineView 保留制作预览，右侧 Forms 的既有播放状态组集中所选动画重启、暂停、继续、停止、临时时长和结束 FX。继续对已有实例调用 resume，缺失实例启动运行播放，保留预览模式、游标和 epoch。动态状态及临时时长被动刷新且保护输入焦点，动画时间只读；一般临时参数页刷新仍是独立遗留。
- 新 PhotonAnimationSceneInfoMixin 已登记 Mixin JSON，命名模式隐藏 SceneInfo 直接重启/播放行及时间输入，保留种子、性能、对象信息，回场景恢复原显示状态。已通过 IDEA 核对 Photon 26.1.2.2 initBasicInfo 的实际子节点顺序和一次构造生命周期；真实首帧布局仍待客户端验收。
- 删除无调用 PhotonAnimationRecording、PhotonAnimationTransform、PhotonAnimationTimelineView，Context 不再实现旧视图转发接口并移除无调用代理/commitRecording；保留 stageRecording、原 TL 录制链、InspectorSession、有效模型/历史/状态。TimelineMixin 唯一 pushEdit 调用同步两参数，原生回退保留 undoFn。
- Runtime 常规 tick 只做一轮实际求值；RELEASE 移除或事件/规则可能改变状态时再合成，close/FINISH 后不补求值。Clock 统一部分刻进度与循环次数，Playback 保留独立制作预览协议；统一优先级/启动顺序排序，Mixer 回退使用 SamplingScope + 原 AnimationTrack.sampleInto，移除无调用 previewPose/type/时长 setter。信号前分层 commit 与最终 commit 职责不同，继续保留。
- 精确编辑/批量移动统一菜单，旧 precise/batch_move 配置 ID 任一开启即可使用，G 键与菜单共用判断；Settings.FEATURES 从 Category 与 shortcuts 派生，显示顺序/旧配置不变。祖先查找共用 PhotonAnimationEditingUtil.editor。菜单与 runLoaded 共用新增 PhotonAnimationChecks 六项目录，菜单逐项报告失败、调试入口 fail-fast，已有断言保留。
- 新增 40 条真实回归检查采样次数、SIGNAL 同 tick 参数更新及前缀顺序、COMPLETE/静默 RELEASE、START/TRIGGER FINISH、部分刻循环/暂停/重定时和预览 N−1。第一次加载回归失败 expected=2/actual=1，经源码确认是夹具直接构造 Runtime 没有挂入 FXRuntime 持有的控制器，原生信号没有入队；仅改夹具通过 reloadAnimations/animations 走真实宿主，保留强断言和生产门控，重新验证完整 4640 项通过。根因及防回归同步命名动画文档。
- 实际检查：IDEA build_project 成功；独立 IDEA PhotonAnimationRegression main 退出码 0、74 项通过；Data 已加载调试表达式 runLoaded=4640，其中共享原生一致性 452（原 412+40）。正常 IDEA Data 退出码 0。临时断点在命中后自动移除，原有两个关闭的用户异常断点未改，调试会话已结束。
- 19 个相关 Java 最终 IDEA 分析全部完成且无超时，无新增错误/警告，仅保留 Regression.main 原 System.out 日志点建议；最新构建诊断错误/警告 0。Mixin JSON 解析/诊断正常，相关系统文档及备份索引诊断正常。所有修改 Java 仅类/行范围 IDEA 格式化，关闭导入优化及成员重排；资源、生成 JSON 和备份均未自动格式化。
- 正常 Data 生成中英文各 729 键、动画键各 182；相对本轮开始各新增 8 个运行控件/状态及提示键，更新 play/continue/pause/reset 共 8 个文本/提示，无删除，其余语言条目一致。生成输出未手动编辑。六个无源码调用的候选翻译键因二进制 UI NBT/动态引用尚不能排除而保留，未将不可读取误报为无引用。
- 三个删除代理已逐份通过 IDEA 备份到 backups/2026-10-06-photon-dedup/<原项目相对路径>，删除前文本严格相等；PhotonTimelineStateUtil.java~ 已由 IDEA 移入同目录原路径，前后文本一致。索引和清单已补充；IDEA 自动处理新增/删除条目的 Git 状态，未手动改索引。源码对旧三类及 commitRecording 检索为 0，其他原有状态条目无变化。
- 尚未执行 Client、实际菜单点击、GUI 布局/焦点/滚动/音频/GPU 验收。临时参数界面刷新、模板基础变换复制与跨周期 MARKER 顺序仍列为独立遗留，本轮未扩大处理。

## 2026-10-06：共享原生时间轴、完整检视与独立原生求值

- 保留中央“场景／命名动画”标签，底部始终是同一个 owner.timelineView。PhotonAnimationTimelineContext/TimelineModel 切换完整模型、时钟、标记及历史；原 29 处模型读取、播放头、录制、工具、吸附、剪贴板与原生编辑器使用活动上下文。旧 TimelineView 仅保留兼容类名，不再实例化第二个 View，也不以旧子类判断模式。
- 原时间轴和各动画分别保留播放头、缩放、滚动、行高、选择、展开、标记、EndMode/Wrap 及预览范围；切换前结束录制与拖拽，按模型身份清理失效引用。动画列表沿用原 ArrayConfiguratorGroup，名称用铺满的只读 Label 让出选中皮肤，UUID 选择同步模型及监视，重复点击不重置。实际高亮和点击分开验收。
- FXObject 直接走场景完整 Inspector，只按真实 FXObject.name 字段锁定名称；轨道及片段继续通过原检视提交命名定义。保留原回调、基础值修改和对象历史；原录制同时修改基础值和当前命名关键帧。构建后显式选择原 Inspector 标签，不以代理替代真实对象。
- 基础编辑/历史恢复沿原场景重模拟，保留定义及编辑状态。录制冻结现有姿态而不释放成基础值，手动改值按所有权保留；自动 refresh 不重建冻结运行态，poll 返回仅发布脏模型/NBT，结束录制再重放。新增 PhotonAnimationHistoryUtil/RecordMixin 覆盖真实 SerializableRecordAction 构造及 updateSnapshot 的序列化出口，防止对象/Transform 快照保存合成姿态；不替换原动作或关闭回调。返回场景从起点模拟恢复原时间和播放状态，避免双重 emit。
- 每次播放持有完整原生 Timeline/TimelinePlayer，普通 AnimatedProperty.apply 多态采样实际值/函数，再交现有参数与混合器。evaluateAt(double)/frameAt(double) 仅在当前播放器作用域提供精确制作时间，保留原生字段类型、激活/控制/动画/速度/信号/音频执行阶段；tick 不补 frame，后处理仅由实际渲染帧提交。无控制器实例保留原执行链。
- 定义版本升为 3，完整制作范围取原轨道内容及命名事件末端，自动播放时长跟随内容、固定时长重定时全套；保留 50% 进度及循环次数、并行、混合、参数和旋转累计。新动画默认不自动开启模块，旧 version=1、2 使用固定旧 duration 且保留自动启用；原轨道 Codec、UUID、绑定和事件不更换。原示例显式启用模块以保留示例行为。
- 修复日志所示 syncPreviewDispatch → Preview.runtime 异常：被动同步查询可空运行态，身份变化时重新接管；结束后被动 tick 不自动复活，用户主动播放/重置才重新开始，不捕获任意异常掩盖生命周期问题。基础结构保护保留，动画列表重命名仍可用。
- “工具 → 测试”增加共享时间轴与原生一致性独立入口并加入全部检查，保留已有连续合成/停止恢复/多实例断言。新类最终检查 412 项，其中录制冻结 37 项、原对象/Transform SerializableRecordAction 构造/更新/执行/撤销 92 项；Data 已加载阶段经 IDEA 调试表达式执行完整 PhotonAnimationRegression.runLoaded，最终 4600 项全部通过。本轮未再次执行独立 main，未运行 Client、点击实际菜单或验证客户端布局、焦点、音频和 GPU。
- 收尾先后出现四类夹具失败，均保留原精度与有效断言后修正：属性查询必须用 transform.position 完整 path；Data 未 emit 的宿主 isValid=false，历史夹具用独立 HistoryRuntime 隔离有效宿主（不覆盖真实发射/心跳/销毁，也不修改生产 guard）；q 与 −q 相同旋转，改比较三个旋转基向量；经过原 Codec 的顺序夹具使用已注册 PositionPropertyType，专用 reset 哨兵保持执行顺序检测，不向注册表加入测试类型。这些根因与维护要求同步系统文档。
- 首次 4531 项通过后补查旋转停止恢复，新增断言实际失败：1.25 tick 清空贡献应恢复固定基准 X=1，实际 0.99323838949。真实原因是 Property 把写入前 Euler 与四元数回读主值比较，350° 与约 −10° 或浮点往返差异误判所有权丢失；release 不恢复，连续 ADD 可把旧姿态变为基础值。仅 Property 改为写后 captureLive 指纹，applied 仍用于查询；release 和 captureNative 的旋转恢复使用真实 Quaternionf 快照，函数槽身份分支不变、不放宽相等容差。追加 27 项固定基准停止恢复及 42 项多轴/350° ADD/重复合成/基础与合成查询后，最终完整 4600 项通过。该生产失败与修复不归为四元数符号夹具误报。
- 并行按 GUI、运行适配、验证及只读复核分工，共享 Mixin/交叉引用/文档由主负责人整合，未并行编辑同一物理文件。所有项目修改通过 IDEA；涉及 Java 类或行已范围格式化，关闭导入优化及成员重排，JSON、生成输出和第三方未格式化。最终检查 48 个相关 Java 无错误或警告；独立 main 保留既有 System.out 日志点调试建议，Mixin 配置与相关文档/规范诊断无问题。IDEA 编译成功并实际执行 compileJava；编译工具错误/警告计数为 null，不作零报告。
- 最终正常 IDEA Data 运行退出码 0。中英文生成语言各 721 个键、动画键各 174 个；较本轮开始各新增 8 项（自动时长/制作范围/自动启用模块及独立测试的标签与提示）、更新 4 项（录制/目标文本及提示），无删除，其余语言条目不变。生成输出由 Data 写入并经 IDEA 读取核对，保持只读；现有 .gitignore 忽略 src/generated，未改变忽略规则或手动添加输出。临时断点移除、调试会话结束，原有两个用户断点保留。
- 同步命名动画、编辑器专页、系统索引、问题修复记录及 AGENTS“计划模式允许使用选项 API 提问”规则。临时参数字段刷新、项目模板复制基础变换、跨周期 MARKER 发生顺序三项独立遗留仍未处理；实际模式状态切换、录制双写/保存/撤销、只读名称、浮动布局、全部轨道与资源导出等列入客户端验收，隔离检查不代替游戏结果。
- 关键文件：PhotonAnimationWorkspace/TimelineContext/TimelineModel/Preview/Outline/Forms；PhotonTimelineViewState/StateUtil/FXTimelineViewMixin 与 PhotonAnimationTimelineMixin；PhotonAnimationInspectorMixin/NameMixin/SceneEditorMixin/SceneSimulationMixin/RecordMixin、NameUtil/HistoryUtil；PhotonAnimationNativePlayer/PlayerAccess/SamplingScope/Property/Mixer/Runtime/Playback 与 PlayerMixin/TrackSamplingMixin；PhotonAnimationDefinition/Library/DataUtil、Example、TimelineRegression、Regression/TestMenu、翻译入口与 Mixin 配置。
- Git 保留本轮开始 93 项既有修改及原 64 项暂存/81 项未暂存状态，最终 103 项、74 项暂存、90 项未暂存、无冲突；未手动暂存/取消暂存、提交、推送、重置、删除或创建备份。10 个新增 Java 由 IDEA 创建时自动加入索引，既有 .gitignore 及其他任务修改保持原样。

## 2026-10-06：重复合成基础值恢复与回归

- 用户确认继续修复上一轮发现的生产问题。根因是一个 tick 中先后两次 Mixer.apply，第二次 Property.apply 保存第一次的函数贡献作为基础值；多实例回归停止覆盖后预期 21、实际 54。PhotonAnimationMixer.apply 入口统一释放本合成器仍持有的旧贡献，再采样合成，空贡献也恢复运行槽和自动模块启用。保留 Runtime.tick/frame 在原生求值前的释放与 Property 的外部覆盖保护，未改回调链或公共接口。
- 并行分工为混合器、回归和只读源码复核，不并行编辑同一文件。PhotonAnimationRuntimeRegression 新增 27 项断言，覆盖连续 ADD/MULTIPLY/混合、移除部分贡献、空贡献恢复、基础/合成值查询、外部函数身份与覆盖标记、模块启用恢复及原配置不变；原 CurveClip 检查去除两处手动 release，直接验证重复 apply 和停止恢复。
- 所有权判断保持原协议：函数槽不同对象的新覆盖保留；直接变换的值相等和缓存装箱槽的对象身份无法精确辨别部分同值的新写入。这是既有边界，本轮仅记录，不扩展为所有权协议重构。临时参数字段刷新、项目模板复制基础变换、跨周期 MARKER 派发顺序三项仍未修复。
- 所有项目修改经 IDEA，两个 Java 仅格式化本次涉及行，关闭导入优化与成员重排；生成资源、JSON、第三方文件未格式化。混合器、运行槽适配、运行态及回归诊断无错误或警告；IDEA 语义引用核对 apply/release，调用方保留原公共方法。
- IDEA 编译成功且实际执行 compileJava，工具错误/警告计数为 null，不当作零。Data 已加载阶段使用隔离夹具执行 PhotonAnimationRegression.runLoaded，4188 项断言通过（时钟/参数 38、运行槽 4011、多实例 22、动画运行 56、编辑器配置 61）；此前 expected=21、actual=54 的失败已恢复。本次未再次运行独立 main，未将旧 main 74 项结果作为本轮执行结果。
- 最终 IDEA Data 正常运行退出码 0；中英文生成资源与执行前文本完全一致，各 713 个键、其中动画键各 168 个，无新增、删除或值变化。临时断点已自动移除，调试会话已结束，原有两个用户断点保留；未运行 Client、点击实际测试菜单或进行游戏视觉验收。
- 同步命名动画、编辑器专页及本记录，保留上一轮失败作为历史证据，补充修复和防回归要求。Git 基线 93 项、64 项暂存、81 项未暂存，保持全部基线状态；未手动暂存/取消暂存、提交、推送、重置、删除或创建备份。

## 2026-10-06：六项动画修复、独立测试菜单与遗留核实

- 并行处理互不重叠的文件，所有项目修改通过 IDEA 执行。无效默认动画 ID 先查找再启动，缺失时记录问题并保留 START、原引用与资源；CurveClip 逐粒子函数在原生命周期/随机采样后增加逐轴周期角度，再应用绑定缩放，非片段轴不重复累计。
- 参数类型变化保留共同有限分量，新增分量采用新类型默认值，并同步校验和迁移同参数的 SET_PARAMETER 动作；失败不提交部分迁移，动作数组与默认值分离。预览借用前保存粒子管理器播放状态，退出恢复场景/控制器后恢复原 play/pause；规则动作通过原生 addConfigurator 放入所属规则子组，折叠保留各动作状态与顺序。绑定标题采用翻译 Component，分量使用 XYZ/RGBA/值；非法旧分量可显示并选择修复，不自动改写定义。
- 新增 PhotonEditorTestMenu，将独立检查统一放入“工具 → 测试”：全部、时钟与参数、原运行槽检查、多实例与触发、动画运行回归、编辑器配置。原表单中的运行槽按钮迁出，魔法阵示例保留；Lobecorp 预览分类 tests 默认开启。使用原 TreeBuilder.Menu、通知及控件样式，各检查自己的模型、FX 和控件，失败显示原因并写堆栈，不修改当前编辑实例。
- 测试收尾修正三处夹具边界：普通 main 不能初始化 Photon NumberFunction（依赖 ModList），函数检查移入加载后入口；未挂 ModularUI 的 isDisplayed 读取布局缓存，折叠断言改用原生即时样式；Data 没有 Minecraft 音频管理器，隔离 FX 只关闭自己的音频派发并在 finally 销毁。既有相等断言补充 expected/actual，未删失败断言或降低预期。
- IDEA 编译成功，曾实际执行 compileJava，最终增量检查为 UP-TO-DATE；构建工具错误/警告计数为 null，不当作零。17 个涉及 Java 最终诊断无错误/警告，保留 main 既有 System.out 的日志点调试建议。已按类或修改行范围通过 IDEA 格式化，关闭导入优化和成员重排；JSON、生成输出及第三方文件未格式化。
- IDEA main 配置最终退出码 0，74 项断言通过（基础状态/参数 38、参数迁移 36）。Data 调试进程中运行槽 4011、动画运行 29、编辑器配置 61 项通过；完整加载回归与多实例检查未通过，第 6 项 expected=21、actual=54。已核对基础值 7 正确，生产 tick 两次 apply 之间不释放，旧合成值 18 被再次乘 3；该新增生产问题未扩大修复，最小建议为 Mixer.apply 统一释放旧贡献后再合成。
- 三项原待核实问题均已源码确认：临时参数字段不会被规则/API 刷新；项目模板 copy(true) 绕过基础变换保护；跨多个周期的 MARKER 按标记而非发生时刻排队。本轮只记录根因、影响和建议，仍未修复；新增重复合成问题一并记录。实际场景切换、视觉交互、预览退出恢复及这些遗留仍需客户端验收，未运行 Client 或以编译/Data 代替游戏验收。
- 最终 IDEA Data 退出码 0；中英文生成资源各新增 30 个预期键、更新 type.tooltip 一项、无删除，动画键各为 168；其他条目不变。生成输出由 Data 写入，未手动编辑。同步命名动画、编辑器专页及本记录，记录根因和防回归边界。
- Git 开始时 87 项、61 项暂存、75 项未暂存；最终 93 项、64 项暂存、81 项未暂存、无冲突，全部基线项与原索引状态保留。3 个新 Java 由 IDEA 自动加入索引；另观测到 .gitignore 在期间新增未暂存修改，本任务未编辑，保持原样。未手动暂存/取消暂存、提交、推送、重置、删除或创建备份；调试检查仅新建临时断点，结束后已移除，保留原有断点。

## 2026-10-06：命名动画复用完整 TL、状态保留与原生播放桥接

- 按用户要求直接复用原 FXTimelineView、八种原生轨道菜单/编辑器及 TimelinePlayer。定义从仅 AnimationTrack 扩为原 Track，支持激活、控制、动画、信号、分组、速度、音频和后处理；底部结束模式、循环模式、片段、曲线、标记、滚动、缩放、吸附、剪贴板和快捷键沿用原 TL。独立模型、播放时钟和历史转发不写入项目原时间轴；顶部命名动画的动态时长、并行、优先级及 HOLD/RELEASE 协议保留。
- 修复每次修改重置曲线状态/线不显示：原历史提交 execute=true 会立即反序列化已应用编辑，替换轨道与属性对象，原 UIState 的选择和折叠引用失效。普通提交改为保存已应用定义且不重复执行；新增 PhotonAnimationEditSnapshot 在历史恢复时按动画 UUID 与快照中实际轨道/属性引用恢复，不以目标 UUID 猜测。普通编辑保留曲线视图、选中属性和关键帧；历史恢复清理已失效的片段、标记与关键帧引用。标记按定义记录身份保留，不按索引复用，避免撤销删除后误选其他标记。
- 原生 Inspector 可编辑当前动画的轨道、片段与属性；层级对象使用安全代理，只有对应轨道录制开启时写动画关键帧，原对象基础参数不变。Configurator 会话保留文本焦点、关闭回调和折叠状态；直接配置修改同步定义/历史并重载运行控制器，不重建正在输入的配置器。原生工具转发到当前动画时间轴；Fit 属性范围进入保存和撤销，跨动画缓存中的隐藏关键帧不参与当前操作。
- 预览拖动复用 SceneView.simulateTo，保持原 TL 的首 tick 求值 0、模拟 N 次后停留于 N-1 的语义；播放中拖动结束恢复播放。重新定义或更换动画时强制从起点重模拟，保留场景渲染器；恢复播放复用已有播放器，避免重新播放 Control 或清理刚模拟的粒子。静默重放关闭原生信号/音频及命名 MARKER/COMPLETE/SIGNAL，保留手动 TRIGGER。顶部播放/停止/暂停/恢复/结束命令等到本轮模拟完成后执行，末命令优先；修复暂停时红线前进、换动画沿用旧时钟和恢复时粒子管理器仍暂停。
- 原生运行桥通过 PhotonAnimationNativePlayer、PhotonAnimationPlayerAccess、PhotonAnimationTimelineState 和 PhotonAnimationPlayerMixin 按实例协调激活、控制和速度的归属/优先级，保留原回调链；普通没有控制器的实例沿用原处理。修复颜色通道数为零被误作单值、RGBA/HDR 原生函数采样和对应属性范围；保留粒子自身生命周期与随机数采样。
- 配置版本提升至 2，原生 Track 的 type/data 序列化复用 Timeline.writeTrack/readTrack，仍读取旧 version=1 动画定义。系统剪贴板与递归对象引用映射支持原生轨道/片段；项目发射模板递归筛选复制范围内的目标，保留无绑定信号/音频/后处理轨道，子实例不会指向父对象。新增类按运行适配与编辑职责进入已有功能包，未进行包迁移。
- 关键文件：PhotonAnimationWorkspace、PhotonAnimationTimelineView、PhotonAnimationEditSnapshot、PhotonAnimationPreview、PhotonAnimationRecording、PhotonAnimationInspectorSession；PhotonAnimationDefinition/Library/DataUtil；PhotonAnimationRuntime/Playback/Mixer/Property/NumberFunction/Contribution；PhotonAnimationNativePlayer/PlayerAccess/TimelineState/PreviewScope；PhotonAnimationTimelineMixin、PhotonAnimationSceneSimulationMixin、PhotonAnimationPlayerMixin、PhotonAnimationInspectorMixin、FXRuntimeMixin、TimelinePlayerMixin、FXTimelineViewMixin、AnimationTrackUIStateAccessor、PhotonTimelineEditAccess；PhotonEditorTools、PhotonCurveEditUtil、PhotonTimelineClipboardUtil、PhotonEmitterAnimationUtil、PhotonActivatorTimelineUtil 与 Mixin 注册配置。
- 并行任务按不重叠文件分工，经 IDEA 修改；修改前后核对源码/类型/引用/诊断。全部涉及 Java 类或修改范围已通过 IDEA 格式化，关闭导入优化和成员重排；JSON、生成输出和依赖未自动格式化。本轮 35 个 Java 与 Mixin 配置最终诊断无错误/警告；IDEA 语义引用核对通用 tracks、Inspector 包装接口及原 TL 模型调用点，未使用旧动画轨道专用入口替代原菜单。
- IDEA 最终编译成功且实际执行 compileJava；编译工具错误/警告计数为 null，不视为零。Data 运行配置退出码 0；生成中英文命名动画键仍各 149 个，与运行前内容完全一致，Git 无新增生成资源变更。未运行游戏、独立回归入口或额外测试；全轨道视觉效果、录制、交互、保存重开、并行优先级和普通特效回归仍待客户端验收。
- 本轮源码审查遗留集中记录在命名动画专页：无效默认 UUID 可中断首次 tick、CurveClip 循环旋转累计丢失、参数类型变化不迁移动作值维数，以及表单刷新、规则折叠、标签显示等问题；仅报告，未扩大修复。后续维护必须同时核对对象引用、UIState 生命周期、序列化版本、播放队列与原生求值顺序，不能以编译/Data 成功代替交互验收。
- 同步 photon-named-animations.md、photon-editor.md、photon-emitter-spawner.md、系统索引与本记录的实际接口、保存格式及维护要点；系统专页与记录诊断无问题。README 仍有一项整张表的排版弱警告，未扩大为无关全表格式化。
- Git 保留开始时 75 项既有修改，最终 87 项、61 项暂存、75 项未暂存，无冲突且未丢失原状态项；其中 8 个新 Java 文件由 IDEA 创建时自动加入索引，另 4 个既有文件本轮开始修改。未手动暂存/取消暂存、提交、推送、重置、删除或创建备份。

## 2026-10-06：动画播放状态翻译参数崩溃修复

- 用户运行日志和 crash-2026-10-06_01.53.09-client.txt 确认 Ticking screen 异常：PhotonAnimationWorkspace.update 将 runtime.problems() 的 List<String> 直接传给 status_format，空列表 [] 也不符合 Minecraft 26.1.2 的翻译参数校验。
- IDEA/MixinMCP 核对 TranslatableContents 构造及 problems 返回类型，仅将状态栏的列表参数改为 problems().toString()。保留 Component 状态翻译、数值参数、公开接口与动画运行行为，未新增翻译键或更改资源格式。
- 并行只读核对命名动画 editor 与 util 包的 7 处 Component.translatable 调用，其余参数为无参、Component 或数值，未发现同类不合法参数。系统专页记录现象、确认根因、修复与维护要点；未并行编辑同一文件。
- IDEA 仅格式化 Workspace 的 468–470 行，关闭导入优化与成员重排，最终 Java 诊断无错误/警告。IDEA 编译成功并实际执行 compileJava；编译工具计数为 null，不当作零。Data 配置退出码 0；生成中英文动画键仍各 149 个，前后内容一致，生成输出未手工编辑或格式化。
- 未重新运行客户端或额外测试，动态状态栏与实际播放仍待游戏内验收；编译和 Data 不代表此崩溃的运行回归已通过。
- Git 保留既有 75 项修改、53 项暂存和 63 项未暂存状态，无冲突；未手动暂存、取消暂存、提交、推送、重置、删除或创建备份。

## 2026-10-06：动画列表皮肤、时间轴翻译与监视顶部页签修正

- 根据本轮运行截图，对照锁定 Photon 的 EmissionSetting.buildBurstConfigurator、LDLib2 UI 编辑器内置样式列表、mc.lss 和 ore.lss，通过两项并行只读调查核对原生控件和主题；所有写入由同一负责人经 IDEA 执行，未并行编辑同一文件。
- PhotonAnimationOutline 移除 Item.lineContainer 的 GDP Sprites.BORDER_RT1 内联覆盖，交回 Minecraft 内置皮肤；名称改为无标题 Configurator 中的原生 TextField，显式宽度。名称不能获得焦点，在字段父行捕获阶段仅阻断左键默认文字拖动，保留外层 Item 选中、拖动条排序及右键重命名。原排序历史与引用保持不变。
- PhotonAnimationTimelineView.createTab 改用 setText(key, true)，修复已有翻译键被当作普通字符串显示；语言资源本来已包含“动画时间轴”，未增加重复键。
- PhotonAnimationWorkspace.inspectorTabs 恢复 COLUMN_REVERSE：原 TabView 的孩子为内容区、标签栏，上轮 COLUMN 使标签位于底部。透明化嵌套 tabContentContainer 和标签滚动视口，清除后者 padding，保留配置滚动容器的一层原生边框；未增加业务页或修改全局主题。
- 修正命名动画专页中上轮关于标签方向的错误说明，并记录紫色皮肤、名称宽度风险、翻译重载、子节点顺序、内建样式降级、重复边框和捕获事件的维护要点。此前记录中的顶部页签为上轮实现意图，运行截图证实不符，本轮已修正；视觉交互仍未重新验收。
- IDEA 只格式化三个修改 Java 的涉及行，关闭导入优化和成员重排；资源、生成输出和第三方文件未格式化。三个 Java 最终诊断无错误/警告，语义引用核对名称控件构建入口；公开接口和模型格式未改变。
- IDEA 编译成功；最后的父行捕获调整由最终 Data 配置再次执行 compileJava，Data 退出码 0。中英文动画键仍各 149 个，与执行前逐项一致，时间轴标题和提示存在；生成输出未手工编辑。编译工具错误/警告计数为 null，未当作零。
- 未运行游戏或额外测试；原生皮肤、名字显示、顶部页签、边框数量及选中/拖动/右键交互仍待游戏内验收。
- Git 保留开始时已有 75 项修改，最终仍 75 项、无冲突；未手动暂存、取消暂存、提交、推送、重置、删除或创建备份。

## 2026-10-06：动画列表左侧标签、原生监视页签与折叠修复

- 动画列表从右侧动画监视迁到左侧“特效层级”旁的新标签，目标仍使用原层级；复用原 View 铺满布局和面板恢复逻辑。中央继续共用原场景，未调整动画运行态或资源格式。
- PhotonAnimationOutline 改用原“发射 → 爆发”使用的 ArrayConfiguratorGroup：边框行、左侧拖动条和底部原生加减按钮。新建继续弹窗输入名称，复制和重命名保留在行右键菜单中，删除沿用内部引用清理；排序保持 UUID，拖动结束提交一次原操作历史。
- 选中动画只更新曲线和监视内容，不立即重建列表，避免原生拖动操作被打断。按 LDLib2 源码确认 DRAG_END 仅捕获不冒泡，排序在捕获阶段提交。
- 右侧四个配置入口改用 TabView/Tab，页签顶部水平排列且可滚动，选中页内容填充并滚动；共享表单只在父节点变化时迁移，避免重复添加子节点。移除表单公共分组 setCanCollapse(false)，恢复原生折叠交互。
- 关键文件：PhotonAnimationWorkspace、PhotonAnimationPanels、PhotonAnimationOutline、PhotonAnimationForms、PhotonAnimationTextUtil；更新命名动画专页、编辑器说明及本记录。AGENTS.md 新增问题现象、根因、修复与防踩坑记录规则，并在命名动画专页记录本轮布局、折叠、标签及拖动的维护要点。
- IDEA 已格式化重写的列表、涉及的面板类及其他 Java 修改行，关闭导入优化和成员重排；未格式化资源、生成输出或第三方文件。5 个修改 Java 最终诊断无错误/警告；语义引用核对列表和面板构造调用均已更新。
- IDEA 编译成功，实际执行 compileJava；构建工具错误/警告计数为 null，不视为零。Data 退出码 0；生成中英文动画键仍各 149 个，仅各更新 outline_panel 的标题和提示两项，没有新增或删除键，未手工编辑生成输出。
- 本轮未运行游戏或额外测试；实际铺满、滚动、折叠、拖动排序、右键菜单、撤销重做及停靠恢复仍待游戏内验收。编译和 Data 不代替视觉交互验收。
- Git 保留开始时已有 74 项修改，最终 75 项，无冲突；唯一新增文件状态为已有 AGENTS.md 的修改，未手动暂存、取消暂存、提交、推送、重置、删除或创建备份。

## 2026-10-06：共用场景、原 TL 动画时间轴与显式录制

- 按用户选择 2，录制默认关闭；左侧目标直接使用原特效层级，动画列表放到右侧动画监视。中央借用原 SceneView、相机、渲染器和粒子管理器，不创建第二个预览实例。移动时关闭场景自动释放，退出时恢复原位置、面板与状态。
- 新增 PhotonAnimationTimelineView 继承原 FXTimelineView，复用原 TL 布局、播放头、标记、滚动、曲线与关键帧交互；模型、时钟、历史和录制重定向到所选命名动画。循环按钮同步动画定义，原整体特效结束判断隐藏，保留动画完成行为和显式结束特效。旧简化 TimelineContext 已无语义引用，保留文件未删除。
- 新增 PhotonAnimationRecording、PhotonAnimationTransform：目标轨道开启录制后，场景变换及右侧数值通道只写动画关键帧；新属性保留起点基础值。结束会话提交一次历史并刷新动画控制器，保留原有粒子。颜色等零数值通道仍通过原 TL 渐变/片段编辑。
- 动画模式保护原层级的对象增删、重排、重命名、可见性、对象剪贴板和原始参数监视；原 TL 暂停录制和自动重播整个特效。动画剪贴板与轨道重命名使用独立动画模型，仅接受动画轨道；原生属性移除不向对象回写基础值。历史只允许本工作区操作及进入时边界，文本按键保留。
- 定义变更只重建动画控制器，不重发已有粒子；游标采样不从原特效起点模拟。显式结束后的再次播放重新发射。FXData 序列化在动画预览中临时释放本扩展贡献，保存项目及导出 FX/FXPack 后恢复姿态，避免把预览变换写成基础参数；普通实例保留原序列化。
- 关键文件为 PhotonAnimationPreview、PhotonAnimationWorkspace、PhotonAnimationPanels、PhotonAnimationTimelineView、PhotonAnimationRecording、PhotonAnimationTransform、PhotonAnimationRuntime、PhotonAnimationTimelineMixin、PhotonAnimationDataMixin、PhotonAnimationHierarchyMixin、PhotonAnimationTrackEditorMixin、PhotonAnimationEditingUtil、PhotonAnimationTimelineUtil；同步剪贴板、重命名、原 TL 适配、工具保护和 Mixin 配置。共涉及 26 个 Java 类、1 份 Mixin 配置及 4 份系统文档。
- IDEA 已格式化本次新增/重写类及既有类修改范围，关闭导入优化和成员重排；仅补齐本次索引表格修改行的对齐，JSON、第三方及生成输出未自动格式化。修改的 Java、Mixin 配置及系统文档最终诊断无错误/警告。
- IDEA 编译成功，实际执行 compileJava；构建工具错误/警告计数为 null，不将其写成零。最终 Data 退出码 0；生成中英文动画键各 149 个，相对执行前各新增 4 个录制标题/提示键，更新 reset/target/trigger 三个提示，未删除键；再次生成内容一致，输出未手工编辑。
- IDEA 语义引用核对新 TL、旧简化 Context、剪贴板适配和序列化入口，并按锁定依赖源码/字节码核对 Mixin 目标。本轮未运行游戏、回归入口或额外测试；实际显示、原 TL 交互、录制拖动、保存重开、停靠关闭、并行动画与原时间轴共存仍待游戏内验收。
- Git 保留开始时已有 59 项工作；最终 74 项、无冲突。本次 12 个新 Java 文件由 IDEA 自动加入索引，未手动暂存/取消暂存、提交、推送或重置；无新增备份、删除或包迁移。

## 2026-10-05：动画只读场景同步、层级与子实例动画

- 动画预览复制场景对象、根变换、种子和视图设置；挂接时重建被原生移除流程释放的渲染器，禁用对象变换及配置编辑。动画定义与曲线继续可编辑。
- 场景配置、对象编辑、撤销重做及重新预览通知在绘制前同步；变化后重建独立实例，临时播放状态重置，不每帧序列化整个项目。新增 PhotonAnimationHistoryMixin 并登记客户端配置，保留原历史流程。
- PhotonAnimationOutline 使用原生 TreeList，分别滚动动画与只读目标层级；滚动内容和固定行不再压缩重叠。动画时间轴增加原生水平滚动条和 Shift/横向滚轮平移。
- 项目模板带入过滤后的独立动画控制器；完整 FX 保留已有控制器。子动画自动运行默认动画和 START 规则，实例参数与触发独立，动画时钟随实例倍率及暂停推进；新增按实例 UUID 获取动画运行态的接口。
- 关键文件为 PhotonAnimationPreview、PhotonAnimationWorkspace、PhotonAnimationOutline、PhotonAnimationTimelineContext、PhotonAnimationHistoryMixin、PhotonEmitterAnimationUtil、PhotonSpawnedEffect、PhotonAnimationRuntime；更新对应系统文档及索引。
- IDEA 已格式化新增 Java 类、涉及的预览类和既有 Java 修改行；只对两处本次修改的 Markdown 表格做对齐，Mixin JSON 和生成输出未自动格式化。16 个 Java、Mixin 配置及 5 份文档最终检查无错误/警告。
- IDEA 编译成功，实际执行 compileJava；编译工具未提供错误/警告计数，不将 null 当作零。Data 退出码 0；生成中英文各核对 145 个动画键，相对执行前各新增两个水平滚动键、修改一个只读目标提示，未删除键或手工编辑生成输出。
- IDEA 语义引用核对模板动画复制、子实例倍率、曲线视口与场景修改通知入口。Git 保留原有 49 项修改，增加本任务的 10 项文件状态，无冲突；新增文件由 IDEA 自动加入索引，未手动暂存。
- 实际显示、只读交互、即时同步、水平滚动、被发射实例动画和暂停倍率仍待游戏内验收，本轮未运行客户端或额外测试。
- 保留已有暂存及未暂存内容，未手动暂存、提交、推送、重置或新增备份；生成输出不手工修改。

## 2026-10-05：命名动画外侧面板布局

- 按截图箭头将动画对象列表放到左侧层级区、动画监视设置放到右侧监视区、动画曲线放到下方资源/时间轴区；中央只保留隔离预览、播放控制与状态。
- 新增 PhotonAnimationPanels 管理三个原生 View；复用原停靠/拖动/浮动及布局 API，侧栏页按钮分两行，不改控件画风。新增三个中英文面板标题及悬浮说明，由集中翻译登记。
- PhotonAnimationTab 通过自身原生选中/取消选中回调联动面板，未覆盖容器回调；离开动画恢复此前选中的原面板。关闭或项目切换移除辅助 View、布局回退与预览引用。
- PhotonAnimationWorkspace 将既有内容放入对应 View，给外侧面板接入原焦点命令处理，曲线的复制/粘贴、删除和历史继续共用独立 TimelineContext。未修改特效资源格式、播放或混合逻辑。
- IDEA 已格式化新增 Java 类及三个既有 Java 的修改范围；4 个 Java 与 3 份文档检查无错误/警告。IDEA 编译成功，Data 退出码 0；生成中英文各核对 143 个动画键，其中新增 6 个面板标题/提示键，生成输出未手工编辑。
- 核对原生 View/Tab/布局 API 与新命令绑定的语义引用；实际标签联动、面板焦点快捷键、拖动/浮动、关闭重开和项目切换仍待客户端验收，本轮未运行客户端或额外测试。
- 保留此前暂存和未暂存内容，未手动暂存、提交、推送、重置或新增备份。

## 2026-10-05：场景旁的命名动画标签

- 新增 PhotonAnimationTab，特效项目加载后在原“场景”标签右侧插入“命名动画”，保持场景选中；复用原 View/Tab 的样式、拖动、停靠、浮动及关闭菜单，补充现有标题悬浮提示。
- 首次选择才创建动画工作区、控制器和隔离预览；仅加载普通项目不添加控制器。复用原 `named_animations` 开关及翻译，不新增参数或资源格式。
- PhotonAnimationWorkspace 保留原打开接口，由工具菜单选择同一标签；关闭后重开，手动浮动后由菜单停靠回来。PhotonEditorTools 接入项目加载与清理，显式关闭子工作区的预览。
- IDEA 已格式化新增 Java 类和两个既有 Java 的修改范围；3 个 Java 与 3 份文档检查均无错误/警告。IDEA 编译成功，Data 退出码 0；生成中英文各核对 137 个动画键，以及复用的标题、悬浮提示和设置键，未手工修改生成资源。
- IDEA 语义引用检查确认原打开接口仍由工具菜单调用；标签创建工作区改走延迟创建入口。场景/动画切换、拖动、关闭重开和项目切换尚未做游戏内验收，未运行客户端或额外测试。
- 保留此前暂存与未暂存内容，未手动暂存、提交、推送、重置或创建备份。

## 2026-10-05：Photon 命名动画与参数控制

- 新增 35 个 Java 文件，按客户端动画/适配/编辑/检查、配置、注册和 util 分类；修改 6 个既有 Java 入口及 Mixin 配置。
- 注册不生成粒子的动画控制器，配置复用原对象 NBT/Codec；命名动画、目标 UUID、参数、绑定和触发规则随对象保存。
- 每实例独立参数和时钟，默认动画自动启动；支持并行、优先级和启动顺序合成、覆盖/相加/相乘、暂停/继续/重启、保持末帧或完成释放。
- 原生 RuntimeBinding/NumberFunction 适配保留粒子自身年龄和随机采样；运行模块启用和参数覆盖不改写共享资源。
- 根 tick/frame 复用并保留原回调；信号按原运行实例排队，并调用原 executor 一次；停止生成、停止动画和结束特效分别控制。
- 新增独立浮动工作区、隔离预览与 TimelineContext；复用原曲线、滚动容器和操作历史，临时参数与播放状态不保存。
- “工具 → 命名动画”和 Lobecorp 粒子与渲染开关默认开启，新增控件使用原生样式和集中翻译提示。
- 提供同一目标的 expand/spin/breathe/fade 示例按钮；无限寿命/单次生成由用户在原发射器中配置，示例不改写这些原配置。
- 控制器删除、重新发射、destroy、世界/管理器清理、FX 缓存重载和关闭预览清理覆盖与引用；子树复制同步映射内部目标。
- 时间标记检查发现逐 tick 累加浮点进度可晚一帧触发，改为从时长变更点累计 tick 后计算归一化位置；保持改时长时的进度和循环数。
- IDEA 已格式化全部新增 Java 类和既有 Java 的修改范围；仅整理新增文档表格行，未格式化 JSON 或生成资源。
- IDEA 编译成功；Data 最终退出码 0。中英文生成文件各核对 137 个动画键，另有控制器类型及功能开关标签/提示，未手工修改生成 JSON。
- 独立 IDEA 检查入口退出码 0，38 项断言通过；在 Data 已加载 NeoForge 的调试帧中执行 runLoaded，最终 4071 项断言通过。
- 运行检查覆盖真实原生旋转函数槽、1000 次覆盖/恢复无漂移、基础/合成查询、外部覆盖保留、默认启动、三种合成、停止后剩余贡献、参数隔离、模块启用/恢复、标记、完成事件一次和回调恢复。
- 上述实例检查手动推进根回调，不是实际游戏粒子引擎或渲染验收；布局交互、粒子身份、保存/.fx/FXPack、剪贴板、撤销、世界退出和资源重载仍需客户端验收。
- 新增 Java 与相关既有修改最终 IDEA 检查无新增错误/警告；检查入口保留 println 的调试建议，TranslationKeys 保留原有未使用字段提示。
- 补充命名动画使用/API/触发专页，维护系统索引、编辑器页和渲染页。跨不同单对象复制事务不合并兄弟引用映射，见专页边界。
- 起始 Git 工作区干净；IDEA 自动把新 Java 加入索引。未手动暂存、提交、推送或重置，未创建或清理备份；检查入口产生的 logs/latest.log 保留为未跟踪运行日志。

## 2026-10-05：Photon 配置提示、模板移除与一次性发射

- 用户确认每次启动仅自动触发一次，循环不重复、重启可再次触发，保留原“每周期一次”模式。
- 四个 Lobecorp 功能分类直接显示开关；发射器发射器移除外层同名折叠，内部形状与实例参数子组保留。
- 扩展配置行复用原生提示图标，集中登记中英文悬浮说明；临时预览、批量属性和预设选择也补充说明。
- 项目模板增加原生“移除”按钮，通过原控件值更新与变更通知清除引用，不删除模板对象或已有实例。
- ONE_SHOT 使用仅由重启重置的运行标记；概率、上限或缺失来源不会自动重试，手动发射仍可使用。
- IDEA 已对本轮 Java 修改行及新工具类执行格式化，未格式化 JSON 资源；文档仅修正本轮新增行涉及的表格排版。
- IDEA 编译 isSuccess=true、problems=[]；Data 配置退出码 0。中英文生成文件各核对 137 条扩展悬浮说明、移除标签和 ONE_SHOT 显示，旧 ONCE 显示保持“每周期一次”。
- IDEA 语义引用确认模式只由现有配置与调度使用；本轮 Java 诊断未新增错误或警告。未执行游戏交互或测试，模板撤销及提示图标的游戏内显示尚待验收。
- 更新编辑器和发射器发射器系统文档；保留已有工作区修改，未手动暂存、提交、推送或重置，未创建或清理备份。

## 2026-10-05：历史备份归并与空目录复核

- 通过 IDEA 逐文件将 .backups/TheQueenOfHatred-before-reset-20260923/ 中 60 份历史备份移入根 backups/ 同名目录，保留原层级和文件名。
- 使用只读字节大小和 SHA-256 核对全部文件；IDEA 移动自动删去两份备份的 import，已通过 IDEA 行级补丁恢复，最终 60 份原文件哈希全部一致。
- 原 BACKUP-MANIFEST.txt 保持原文，更新本地备份索引与清单，新增归并校验记录。
- IDEA 源码目录树仍有 431 份 Java；复核原 52 个空源码目录清单仍成立，.backups 下另有 51 个空目录且无文件。
- 用户选择回退方案 1，授权 PowerShell 删除清单中的空目录；逐个检查项目路径、目录链接及实际内容，按子目录到父目录执行非递归删除。
- 103 个目录全部删除：52 个旧源码空目录及 .backups 下 51 个空目录。首轮 5 个空目录访问被拒绝，4 个父目录因子目录保留而跳过；在原清单范围内重试成功，未修改目录权限。
- 清理后 IDEA 目录树确认 431 份 Java、旧源码空目录为 0；60 份原历史备份重新核对大小和 SHA-256 全部一致，保留原路径清单和校验记录。
- 未修改正式 Java、JSON 或生成资源，未运行格式化、编译、Data、测试或游戏；不手动暂存、提交、推送或重置。

## 2026-10-05：Photon 发射器发射器

- 用户选择新增发射器类型，并同时支持项目发射器模板与已保存的完整 FX 资源。
- 新增 15 个 Java 文件，按注册、配置、客户端发射器/运行态、工具分类；原生类型 ID 为 `lobecorp_emitter_spawner`。
- 自身不创建 TileParticle 或提交几何；复用 Photon 深拷贝、FXRuntime、原时间轴与粒子管理器。
- 增加延迟、周期、每周期一次/间隔/速率、数量/概率、实例与每 tick 上限、形状、跟随及旋转/缩放/颜色/速度继承。
- 支持实例寿命、大小、颜色、速度、光柱长度/宽度及播放倍率；缓存运行槽覆盖，目标子对象重启后仍保留参数。
- 接入激活轨道停止/清理与明确的清理重启选择，提供客户端手动发射、停止/继续、暂停、重启、参数和 UUID 实例控制 API。
- 来源链拦截自引用和异步循环，非法输入/缺失来源拒绝本次生成；小数时间步及循环边界使用半开调度窗口。
- Lobecorp 粒子与渲染设置新增 `emitter_spawner` 控件开关，默认开启；UI 沿用 Photon/LDLib2 原控件样式。
- 修改既有 PhotonEditorSettings、PhotonEditorTools、TranslationKeys、PhotonActivatorTimelineUtil，仅增加本功能所需入口。
- 新增本功能专页，维护系统索引、编辑器页和渲染页；IDEA 编译 `isSuccess=true`、`problems=[]`。
- IDEA 已完成新增 Java 类格式化，既有 Java/文档仅格式化本次修改范围；未格式化 JSON 或生成资源。
- 19 个涉及 Java 文件与 5 份系统文档完成 IDEA 诊断：无新增错误/警告，保留 TranslationKeys 既有 12 条未使用字段警告。
- 最终 IDEA Data 配置包含成功的 compileJava，退出码 0；中文“每周期一次”和英文“Once per cycle”生成标签已读取核对。
- IDEA Data 配置退出码 0，中文/英文生成资源均包含 69 个相关翻译键。`src/generated/` 原已被 Git 忽略；读取实际生成内容核对，未手工改写或格式化生成
  JSON。
- IDEA 自动将 15 个新 Java 文件加入索引；保留已有 452 条 Git 工作区记录，不手动暂存、提交、推送或重置。
- 游戏菜单、交互、保存重开、剪贴板、坐标继承、不同倍率和压力场景尚未运行验收；静态/编译结果不作为运行验收证明。
- 本轮没有移动/删除旧备份，也未处理前一项待授权的空目录与历史备份归并。

## 2026-10-05：统一备份路径与空目录清理（部分完成）

- 用户要求清理空目录并统一备份路径，目标采用已有根 backups/。
- 通过 IDEA 将源码旁的 11 份 .java~ 备份移动到 backups/package-migration-2026-10-05/，保留来源相对路径；逐份读取与移动前文本一致。
- 源码目录仍有 416 个 Java 文件，未修改 Java、JSON 或生成资源，未运行编译、Data、测试或游戏。
- 原有 Git 索引保持不变；11 个备份的工作区原路径显示删除，目标按已有 .gitignore 保存在本地。
- IDEA 列出 52 个不含文件的旧源码目录，尚未删除；.backups 中 60 个历史备份文件尚未归并。
- IDEA/MCP 的安全删除和文件移动接口无法解析目录目标；等待用户授权目录操作的回退方式。
- 增加本地备份索引和精确清单，更新工作流程的统一路径约定。

## 2026-10-05：系统文档补充与批准的包迁移（已完成）

- 用户批准第二版包整理方案 1，并要求并行执行。原目标为 269 类迁移；按其保留关联小组的边界，实际 266 类迁移、150 类保留，共 416
  个手写 Java 文件。
- 新增系统索引与各模块说明；规范增加“修改前查文档并核对源码、修改后维护文档及记录”。
- 工具统一根 util，实体与世界内机制归 world，统一入口以下按作用再按类型分类。保留网络 tc/ts/tsc、注册及客户端/事件边界。
- 文档按互不重叠文件并行编写；Java 语义移动和共享引用统一由主任务负责。
- 核对了 LcPayloads 协议 11、LDLib2 26.1.2.39、附件 entity_skill_patch 与 conductor_ability_patch，修正文档旧值。
- IDEA 已预览 269 类迁移；同包强关联类作为连续组移动，不为迁移扩大可见性。并发预览的临时 PSI 失效已串行重试。
- 实际迁移通过 IDEA 完成，项目内 Java 引用和 79 项 Mixin 配置同步更新；未执行 JSON 或生成资源自动格式化。
- ConductorHud、ConductorControls、ConductorLdlibScreen 保留原 client.conductor 包，保持 hoveredLivingEntity 与 drawTooltip
  的 protected 访问，不扩大成员可见性。
- 修正 IDEA 移动遗漏的动画、重组进度与指挥家导入；首轮编译暴露一条静态诊断未报出的导入残留，修正后 IDEA 编译
  isSuccess=true、problems=[]。
- IDEA Data 配置正常退出，退出码 0；随后 Git 检查 src/generated/resources 无变更。未手工修改生成输出。
- IDEA 完成手写 Java 格式化、416 文件分组诊断及相关类复查，迁移错误已消除；保留原有警告，不扩大为警告清理任务。
- 14 份系统文档包括索引、12 个系统说明和修改记录；源码链接按最终路径复查，规范及旧参考文档同步相关事实。
- 14 份系统文档最终 IDEA 诊断均为 0 错误、0 警告；旧架构/API/参考索引保留表格排版提示，不扩大为无关文档格式清理。
- 源码内容对照：排除包声明、导入、迁移类型全名和空白后，416 文件业务代码一致。项目 Java/JSON 的已撤销旧包引用检索为
  0；备份文件不计入手写源码集合。
- 工作区出现的 11 份 .java~ 备份保留，未手工删除或移动；IDEA 自动登记的暂存/重命名以 Git 状态为准，本轮未手动暂存、重置、提交或推送。

### 已知限制与未验证项

- 尚未做游戏交互、帧率、联机、透明排序和剪贴板运行验收。
- 前轮已完成 UI 树扫描优化，但噪声预览逐帧 NBT 比较仍未优化。
- 前轮审计的五处超过约 100 行的方法尚未拆分，见 [格式化审计](../java-formatting-audit.md)；本轮不将包迁移当作复杂方法重构。
- BasicLangGenerator 手动翻译合并基于 user.dir 的父目录；根 lang
  文件是否进入该合并需核对实际运行目录，见 [资源说明](resources-datagen.md)。
- 工作区已有大量暂存与未暂存修改，保留其内容；本轮不重置、提交或推送。IDEA 可能自动将新文件加入索引，应以 Git 状态为准。
