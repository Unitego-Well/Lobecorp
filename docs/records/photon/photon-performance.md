# Photon 性能优化与采样

核对日期：2026-10-18。当前目标是在本机、固定分辨率与视角下，以单个完整 laser_spell 的 100 FPS／10 ms 帧时间为验收指标；编辑器和世界分别记录，首次加载与预热后分别记录。平均帧率、P95、P99、CPU、GPU、分配量和系统占用率不能相互替代。

## 本轮等价优化

- 模型运行采样预建不变的角点映射、UV、索引和材质分组，每次姿态独占位置／法线流。按部件引用的顶点计算，适用时共享部件法线矩阵；编辑与导出的完整采样路径保留。
- Photon 自有 CPU 顶点缓冲按 MeshData 所有权回收，空闲池最多 128 项／16 MiB，单项超过 1 MiB 直接释放。异常烘焙立即释放；原生全帧清理正常结束后才处理孤立借出，不能关闭其他 collector 仍持有的缓冲。资源重载与退出清空空闲池。
- 模型材质缓存命中直接比较当前字段，失效时才创建比较快照。没有跨实例共享可变材质、UBO 或参数。
- 重复模型快照安装复用规范 JSON；派生文本纳入已有 32 项／4 MiB 文本 LRU，非规范输入仍按旧规则规范化。编辑提交和强制外部重载保持原写入路径；不缓存可变资产的序列化结果。
- Photon.drawRun 内将同时变化的 read／draw FBO 绑定折为一次 GL_FRAMEBUFFER 调用，继续更新原有缓存并保留 RenderPass 创建、关闭、退出绑定 0 和绘制顺序。统计入口为 PhotonFramebufferBindUtil.stats()，作用域以 finally 恢复；[OpenGL 语义](https://wikis.khronos.org/opengl/GLAPI/glBindFramebuffer)。2026-10-18 新客户端已确认生效，作用域深度与原生缓存状态检查见下文。
- 命名动画每次所有权扫描复用当次有序播放列表，直接遍历实时轨道树；回调后的播放顺序重新读取，没有跨帧缓存优先级、轨道或参数，也不跳过 HOLD 的原生执行。
- Photon 主编辑器的固定图元列表记录实际修改，先用整表标量包围盒排除不交叠，再按 16 项块查询。固定规范边界的小列表或索引预算不足时直接按底层 ArrayList 下标短路扫描，避免 AbstractList 迭代器；未知／动态／异常边界及帧代次失效仍回退。追加有效边界可直接扩大整表包围盒，其余修改按版本失效；小列表不申请 32 项索引数组，不改变层级选择。
- 模型与动画补充语义回归。几何共享流、独占姿态、UV 接缝、材质回退、镜像、伸长边界、形状键／蒙皮、实时 mute／children、同 tick 替换和 HOLD 继续推进分别检查。

源码入口：模型采样（历史引用，当前工作区缺失：`../../src/main/java/org/unitego/lobecorp/util/photon/model/PhotonModelSamplerUtil.java`）、缓冲池（历史引用，当前工作区缺失：`../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonVertexBufferUtil.java`）、动画调度（历史引用，当前工作区缺失：`../../src/main/java/org/unitego/lobecorp/client/photon/animation/runtime/PhotonAnimationRuntime.java`）。运行、编译、测试、Data 和性能结果在实际完成后更新，不能把这里的实现状态当成验收通过。

## 优化前前台基线

原始记录位于 run/client/performance/before-1792150533249.json；这是原版本客户端在源码补丁编译、热替换之前的运行输出。2560×1494、游戏主窗最大化、透视、Bloom 开启，40 秒／1747 帧，记录的全部帧均获得前台焦点；采样期间通过原生 onInputReceived 排除 AFK 限帧，没有修改配置。初始阶段没有完全清除蓄力来源，不用它作为空场结果；结束后的 phase=6 才是零来源阶段。

| 区间                 | 样本 | 帧间隔 P50 | 帧间隔 P99 |    CPU P50 |     GPU P50 |
|----------------------|-----:|-----------:|-----------:|-----------:|------------:|
| 持续输出第 15～34 秒 |  751 | 21.3205 ms | 47.4174 ms | 17.6466 ms | 8.909824 ms |
| 结束后的零来源阶段   |  274 | 13.7752 ms | 33.3128 ms | 12.3943 ms |  3.61472 ms |

持续输出区间渲染线程 Java 堆分配约 164.25 MiB/s；这不含其他线程、原生 malloc 或 GPU 内存。CPU 记录是前一完整 Minecraft renderFrame 在 swap／limiter 之前的时间。GPU 查询异步回收、不阻塞，范围为 RenderFrameEvent.Pre 到 Post，排除 extract／present，不能称为整个 GPU 帧。首次激光阶段有加载尖峰，只有 3 个区间样本，不能作为可靠的分位统计。完整原始记录保留该尖峰。

基线采样时 IDEA 同时进行源码编辑／检查，背景负载没有完全隔离；它可用于发现问题，严格前后比较仍需空闲编辑器条件下复采。尚未完成世界场景或 100 FPS 验收。

## 第一轮优化后的实测

编辑器 after_editor_stage_one-20261016-143316-386-50633535596800.json 与基线使用相同相机、界面区域、透视、Bloom 和 2560×1494 分辨率；前台、最大化、NONE／260、VSync 关闭。持续区间第 15～34 秒 1107 帧，平均 58.2 FPS，帧间隔 P50／P99 为 15.7788／34.0372 ms，前一 renderFrame CPU P50 为 13.2558 ms。持续时实际仅 laser 来源为 1，结束六个来源归零，problems=[]；没有达到 100 FPS。源码编辑背景负载未完全隔离，不把它解释成严格因果 A/B。

对照优化前相同区间平均约 39.5 FPS、帧间隔 P50／P99 21.3205／47.4174 ms。Java 堆分配平均每帧约 4.16→3.05 MiB；不限制 FPS 时每秒为 164.25→177.69 MiB/s，更多帧会增加每秒分配，不能据此说分配优化失效。GPU 查询范围不同，仍不计算前后 GPU 改善比例。

世界 after_world_laser_spell-20261016-135031-779-48068933559300.json 第 15～34 秒 2090 帧，平均约 110.0 FPS、P50／P99 7.3238／27.2719 ms，全程前台最大化／NONE／260。该场景玩家位于 (-0.5,56,-2.5)，视角 0／0，特效根位置 (2.5,57.62,7.5)，朝向 (-0.7071,0,0.7071)。后续截图确认世界激光可见；世界与编辑器测量不能互换，稳定 100 FPS 仍未达标。

854×480 的窗口准备试跑不计入成绩；首次编辑器采样因过期预览引用在阶段回调失败，没有导出完整记录，已改为阶段内读取当前预览实例。阶段起始与输入计时均移至首个 render 入口，避免 Debug 暂停消耗采样及 AFK 预算。

## Spark 的用途与限制

本地 profile-2026-10-16_20.09.23.sparkprofile 为 JAVA／EXECUTION、4 ms 间隔、35.528 秒；文件是裸 protobuf，保存到 run/client/config/spark，没有上传。Render thread 权重 34996 ms，其中限帧器 inclusive 82.638%、Unsafe.park self 81.872%，因此不能将总权重当作活跃 CPU 时间，也不能把该样本当作同一前台条件的帧率基线。

第一个实质 self 热点是 GuiRenderState.hasIntersection，1904 ms／5.441%。调用来自 LDLib2 GUIContext.addGuiElement、SpriteTexture 和时间轴刻度。方法 inclusive 统计需要去掉同方法递归重复；父子包含时间不能相加。Spark 用来找调用与分配热点，GPU 仍用独立查询。

## 第二轮检查与采样（2026-10-18）

工作区迁至 E:/xm/mc/mod/Unitego-Well/Lobecorp 后，IDEA 编译 isSuccess=true、problems=[]；模型 JUnit 24／24、GUI JUnit 7／7 通过。Data 实际日志 BUILD SUCCESSFUL in 27s，19 文件写入 0／删除 0，生成目录 Git 差异为空。相关代码无 IDEA 错误，数组注解、preparedAsset 可空性、@Local 参数命名及采样回调可空性建议分别保留。更晚的代码修改需重新记录实际检查。

after_world_second_20261018 的第 15～34 秒 2905 帧，平均 152.8 FPS，帧间隔 P50／P99 为 5.571／23.3833 ms，渲染 CPU／GPU P50 为 2.9824／1.544192 ms。全程前台最大化、2560×1494、VSync 关闭；持续期 NONE／260，首帧仍携带输入恢复前的 30 帧上限。来源实际为 laser=1、其他 5 个为 0，problems=[]，不能与来源数量不同的历史样本作严格因果比较。世界截图已查看。

世界第 38 秒 FBO 累计 scopes=86492、foldedBinds=172984、depth=0、peakDepth=1；原缓存 read／write 与真实 GL 绑定均为 0／0。缓冲池结束借出归零，空闲约 3.25 MiB。这确认了调用折叠和归还，不把省去调用次数直接换算为 GPU 收益。

after_editor_second_20261018 的第 15～34 秒全部失去前台焦点，0 个有效前台区间；同份空场数据也不作为对照。重新采集的 editor_profile_20261018 在相同透视相机、Bloom、嵌入区域下，前台第 15～34 秒 1302 帧平均 68.51 FPS、P50／P99 13.117／28.0625 ms，含 Spark 采样开销。两场景均未达到 P99 不超过 10 ms 的稳定目标；首次加载／阶段切换尖峰保留在原始数据，不当作预热后稳态。

本地 profile-2026-10-18_01.53.15.sparkprofile 验证 35 个线程／13975 节点，Render thread 权重 35000 ms，节点一致性检查通过。整个 profile 的原生 hasIntersection self 为 16.83%，AbstractList.Itr.next 为 14.61%；GameRenderer.extract inclusive 为 45.21%，布局／Yoga 仅 12 ms。实际时间窗对应配套 capture 第 5.060249～40.672249 秒，2138 帧均为输出阶段、前台最大化／NONE／260。只有 2 个聚合桶，不能把这些调用栈比例称为精确 19 秒切片；原生 GL 权重可能含驱动阻塞。下一步只针对已确认固定边界的小列表扫描与保守包围盒，保持动态回退和层级规则。

上述 JSON 位于 run/client/performance；文件名时间戳使用 UTC，本文日期和本地 sparkprofile 使用 Asia/Shanghai。着色器、模型几何、粒子数量与本轮资源内容均未改动；未另建字节备份、手动暂存或提交。

### GUI 回退定位与后续状态

整表包围盒／直扫补丁实际完成 IDEA 编译、GUI JUnit 13／13、Data BUILD SUCCESSFUL in 24s；19 文件写入 0／删除 0，生成目录无差异。随后新客户端 editor_final_gui_20261018-20261017-182345-424-94362717058100.json 的第 15～34 秒 759 帧全部前台最大化／NONE／260，平均 39.89 FPS，P50／P95／P99 为 22.5843／36.7650／43.8573 ms；结束空场第 38.2～39.9 秒约 57.86 FPS。相机、嵌入区域、Bloom 与资源 revision 保持，结果未达到目标，也不能直接归因于新增包围盒。

用户要求测试关闭非焦点限帧后，调用方将 inactivityFpsLimit 从 AFK 临时设为 MINIMIZED，pauseOnLostFocus 原本为 false 并保持；未保存 options 文件，窗口保持最大化。editor_warm_profile_20261018-20261017-182910-269-94687565814100.json 的持续区间虽未获得焦点，实际全部 NONE／260，严格完整区间约 45.73 FPS、P99 51.1408 ms。对应本地 profile-2026-10-18_02.29.19.sparkprofile 的 37 线程／10709 节点守恒检查通过；原生 hasIntersection inclusive 45.57%，其中旧迭代器仍占主要成本，新增 query inclusive 1.20%。样本混合蓄力、输出、结束、空场且后台，不作前台因果 A/B。

运行探针发现 26 个节点／1026 个图元，其中 16 个列表含不可索引图元；有 60 个 LDGlyphRenderState、6 个 FloatLineStripRenderState，以及零宽时间线矩形。依赖源码确认前两种 record 的 bounds 固定；零宽高对象不能直接当空图元丢弃，原生 intersects 在对象位于查询内部时仍可能命中。已补充固定类型及零尺寸缓存边界，查询仍保留正尺寸规则；负尺寸、溢出、空 position 与未知动态类型仍回退。新增测试构造器参数错误已修复，工具类无错误／4 条既有 final 建议，测试无错误／3 条冗余抑制提示；此最新补丁尚未编译、运行测试、Data 或客户端复测，不能套用此前 13／13 的结果。

用户随后确认 `/lobecorp photon_editor` 独立扩展入口，原生 `/photon_editor` 拦截扩展内容。源码入口隔离已实现，GUI 索引按扩展编辑器所属关系启用；上述待检查状态属于历史步骤，最新编译和客户端实测见下节，不能将此前帧率当成本次入口迁移后的性能结果。

### 独立扩展入口加载后的实测

新客户端加载了固定文字／折线及零尺寸边界补丁与入口隔离：IDEA 编译 isSuccess=true、problems=[]，GUI JUnit 17／17、exitCode=0，正常 Data exitCode=0、BUILD SUCCESSFUL in 19s，19 文件写入 0／删除 0、生成目录无 Git 差异。原生和扩展入口、dirty 工程保护、最小化恢复、子发射器真实提交与嵌入式模型标签分别完成运行检查，见[修改记录](../changes.md#2026-10-18photon-独立扩展编辑器与原生入口隔离)。

editor_split_final_20261018-20261017-195246-519-99703877720100.json 为 40.0079 秒／4117 帧。持续第 15～34 秒严格完整区间 1865 个样本，平均 98.28 FPS，帧间隔 P50／P95／P99 为 8.4089／19.6409／26.7062 ms；前一 Minecraft CPU P50／P99 为 7.3948／14.5603 ms，GameRenderer.render CPU 为 5.1996／10.4361 ms，GPU 为 4.885504／9.885573 ms。渲染线程堆分配平均 2.1104 MiB／帧，结束后零来源第 38.2～39.9 秒 156 个样本平均 92.39 FPS、P99 13.5484 ms。尚未达到稳定 100 FPS。

上述持续区间均前台最大化、2560×1494、NONE／260、VSync 关闭、透视与 Bloom 开启。临时 MINIMIZED／pauseOnLostFocus=false 在采样结束恢复为原 AFK／false，并释放鼠标；只关闭本轮创建且无 dirty 的临时标签，保留原工程 revision=9c0ea99ede2a7ecfc0a1af420a6854f8174fb2d39cc8c44586a523d81778346c。当前嵌入场景高度 213.4523 GUI 像素，与旧 221.3333 不同，且另有 Confluence 客户端进程，不能解释成严格单客户端 A/B。持续来源 laser=1、其余为 0，结束全部归零、problems=[]，截图已查看。结束缓冲池 borrowed=0、cached=26／3.25 MiB，FBO depth=0、peakDepth=1。

## 扩展编辑器遮挡主世界

新入口内嵌、最大化且窗口以不透明纯色完整覆盖游戏 GUI 时，`PhotonEditorWorldRenderMixin` 只改 `GameRenderer.extract/render` 首个 `Minecraft.level` 读取所决定的 shouldRenderLevel；不写真实 level、advanceGameTime 或 pause，不取消完整 render。GUI／PIP、主相机更新、世界 tick、预览粒子时钟及 RenderFrame.Post 清理保留。同帧复用 extract 开始的条件，异常及 render finally 清理缓存。原生编辑器、OS 宿主、非满屏、透明、变换、遮罩、布局／样式待更新或 UI 动画运行时均回退主世界绘制，粒子／模型动画不属于这个 UI 动画队列。维护入口为 PhotonEditorWorldRenderUtil（历史引用，当前工作区缺失：`../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonEditorWorldRenderUtil.java`），stats 记录条件命中与提取／绘制跳过次数。

该优化只支持正常提取不透明编辑器 GUI 的事件链。第三方取消／替换 ScreenEvent.Render.Pre 的 GUI 提取发生在主世界提取之后，当前无法安全补回本帧世界；不声称这种替换可安全回退。升级须重新核对两个 FIELD ordinal、GUI／PIP 与帧末清理路径，不能只凭 Mixin 能启动认定兼容。

加入门禁后再次 IDEA 编译 isSuccess=true、problems=[]，正常 Data exitCode=0、BUILD SUCCESSFUL in 23s，19 文件写入 0／删除 0；GUI 索引未再改动，17／17 的结果沿用该相同代码。新客户端已加载门禁，标准链跳过计数实际增长，最终提取／绘制均为 27792，透明探针返回 false。场景区域与上一份入口隔离采样一致，截图已查看，工程 dirty=false；AFK／pause=false 已还原，鼠标已释放，本轮断点移除并保留三处用户断点。

首次 editor_world_gate_20261018-20261017-201853-779-101271156154100.json 为 40.0016 秒／4552 帧，第 15～34 秒 1794 个完整区间平均 94.51 FPS、P50／P99 8.6353／28.9707 ms，空场第 38.2～39.9 秒约 243.74 FPS、P99 7.6818 ms。预热后 editor_world_gate_warm_20261018-20261017-202324-061-101541441702900.json 为 40.0018 秒／6169 帧，持续 2561 个样本平均 134.89 FPS，P50／P95／P99 6.4616／13.4915／19.6093 ms；前一 Minecraft CPU P50／P99 5.6913／11.2540 ms，render CPU 3.8913／7.9963 ms、GPU 4.031488／8.016282 ms，堆分配平均 2.2954 MiB／帧。空场 425 个样本约 250.51 FPS、P99 6.4126 ms。

持续和空场均全前台最大化／NONE／260、2560×1494、VSync 关闭、透视及 Bloom 开启；预热与首次记录分别保留，不能挑平均数当作稳定 100 FPS。背景仍有另一 Confluence 客户端，未停止或改动；不作严格因果 A/B。阶段均无 problems，结束来源归零；预热池 borrowed=0、cached=31／3.875 MiB，FBO depth=0。另用本地 35 秒 Spark 定位剩余长帧，仅 --save-to-file，不上传，文件为 profile-2026-10-18_04.25.38.sparkprofile，配套 editor_world_gate_spark_20261018-20261017-202532-159-101669541031100.json；不能以含采样开销结果替代无 Spark 预热记录。

此 Spark 的 36 线程与渲染线程 13980 节点一致性检查通过，渲染权重 35000 ms；实际对应 capture 第 3.039～38.559 秒，混合蓄力／输出／结束且仅有两个粗桶，不能精确裁成 19 秒持续切片。完整区间 15～34 秒 1947 样本约 102.53 FPS、P50／P99 8.0588／27.0587 ms。聚合 GUI 原扫描 inclusive 296 ms／0.846%（其内迭代器 108 ms），新 query 1428 ms／4.08%（其内 scan 956 ms）；父子时间不能相加。GUI sort 2992 ms／8.549%、FBO bind 2304 ms／6.583% 是后续候选，原生 GL 样本可能包含驱动阻塞，不能直接当纯 Java CPU 成本。KG prepareUpload 60 ms、材质 UBO 4 ms、全部 writeToBuffer 108 ms，占比没有支持优先重写材质上传算法；本轮不追加这种改动。

## 世界运行时复制与分配优化（2026-10-18）

最新完整前后表、分阶段数据、1%／0.1% low、最低一秒、最长帧、CPU／GPU、GC与限制见[世界特效性能对比](photon-world-performance-comparison.md)。有效对照只用固定相机的before V1与修正树归属后的after V2；第一版after因复制控制器错误连接源父节点而作废，不能沿用其成绩。

本轮模型运行复制在独立线程作用域复用不可变JSON文本，正常保存／导出保持原字节NBT和完整依赖；精确控制器直接建立新对象，保留局部变换与内部ID，由新运行树建立父子关系。动画混合复用调用独占工作区并保留安装后的独立快照；空闲顶点池扩至128项／16MiB，查找较大容量时保留可供其他请求使用的小项。语义及内存代价详见对比页。

预热稳态单实例最低一秒86→108FPS、1% low39.43→52.32FPS；四实例最低一秒31→54FPS、1% low16.31→27.77FPS、P99 56.950→32.824ms。四实例每帧渲染线程分配7.751→2.996MiB，原生缓冲丢弃42624→0。单实例最长帧42.47→51.08ms、GC总暂停未下降，仍未达到稳定100FPS。单实例前失焦、后前台，双方持续期均NONE／260；四实例双方前台，不能忽略环境与周期波动。

## 短调用采样

在渲染线程调用 PhotonFrameCapture.start("benchmark", 40.0)，用 phase("stage_a", 10.0, action) 登记一次阶段动作，snapshot() 读取状态，stop() 提前保存，cancel() 取消且不保存。计时从恢复执行后的首个 render 入口开始，避免 Debug 暂停占用采样与阶段时长。时长限制为 10～120 秒，最多 131072 帧／64 个阶段；结束自动写入 run/client/performance，标签仅使用小写字母、数字与下划线。入口见[采样器](../../../src/main/java/org/unitego/lobecorp/client/photon/performance/PhotonFrameCapture.java)。

整帧间隔取相邻 GameRenderer.render 入口，包含中间 tick、extract、swap 和 limiter；previous_minecraft_ns 记录前一 Minecraft.renderFrame 的 update／extract／render／present，不含外层客户端 tick、swap 和 limiter。GPU 只测 GameRenderer.render，异步读取、最多一个 pending，不抢占原版全帧查询。此 GPU 范围与优化前 Pre→Post 范围不同，不能直接用两者计算 GPU 改善比例。原版底层 endProfile 持续抛错时没有公开 abort 接口，设备异常清理只能重试并报告不可用。

阶段回调发生在 extraction 之后，新特效几何通常下一帧才进入；阶段切换首帧属于过渡帧。记录每帧阶段、窗口／前台／限帧、硬件、线程分配和缓冲池统计。start 只调用一次 onInputReceived，不改窗口焦点、配置或相机，超过 60 秒的采样仍须检查 AFK 状态。着色器／Bloom 的画质或层数尚未调整。

## 动态视觉精细度与统一渲染

2026-10-18 新增距离/可见负载出生密度、参考姿态自动网格烘焙、远处可选平面化、显式接入的实体动画降帧及实体小细节 LOD；总开关、阈值与逐对象/实例豁免见[动态视觉精细度](../../systems/client/visual-quality.md)。同轮现有工程统一开启视深排序和不透明遮挡，当前画面配置已经不同于上一轮性能表；本轮尚无严格性能 A/B，不将旧成绩作为此配置下的稳定 100 FPS 证明。

## 检查与维护

实际检查和备份情况见[修改记录](../changes.md)。缓存只保存明确归属的数据；可变姿态、完整 FXRuntime、外部句柄及引擎仍持有的网格不能直接入池。结果必须报告采样范围、分辨率、视角、前后台、限帧、种子、阶段、首次／预热状态以及未完成项。

2026-10-16 的动画时间轴加载回归完成 1031 条断言；RUNTIME 总入口在既有 ChildRegression 的库外预览字符串查询夹具处失败，与本轮优化边界无关，未扩大修改。2026-10-18 的实际编译、JUnit、Data 和运行状态见上文，不能以旧一轮结果代替新代码验收。

## 代码动画接入与释放前准备（2026-10-18）

本轮先完成源码，不启动游戏；以下是源码路径对照，没有新的 FPS、P99 或 GPU 结论。

| 路径                     | 修改前                         | 修改后                                                           |
|--------------------------|--------------------------------|------------------------------------------------------------------|
| 子 FX 动画库             | 发射校验、运行态构造各解码一次 | 校验后的独占库直接传入；预备库按原 NBT 快照核对后消费            |
| 女皇光束子运行态         | 输出启动时同步复制/创建        | 蓄力期间预备一个；释放时只消费，不重建该份运行态                 |
| 光束模型 JSON 与不变拓扑 | 模型 reset/首次采样才完成准备  | 未发射实例显式 prepareRuntimeAsset，保留可变姿态在实际采样时计算 |
| 持续期长度截断           | 锚点和参数分别查询             | 共用当前 tick 的起点/终点                                        |
| 动画持有与失效           | 原技能事件拥有特效             | 动画 UUID 拥有特效；清除/加载失败不逐 tick 重建                  |

预备仅给女皇当前光束源显式调用；每个发射器最多持有一份未发射模板，不共享活动 FXRuntime 或可变动画库，不预先消耗发射随机数，也不触发粒子/音频/绘制。重启保留待消费模板，真正停止/清空释放；消费时核对 FX 资源身份、ID、来源路径和深度，失效则销毁并走正常创建。模型准备只涉及 CPU 资产和拓扑，不等同于 GPU 预编译或上传。

17 个 Java 的修改范围格式化、IDEA 诊断、最终编译和 Data(exitCode=0)已完成；源代码优化和系统文档已备份。尚无本轮前后帧时间数据，不能宣称已消除释放卡顿或达到稳定 100 FPS。后续按相同分辨率/视角、前台且无失焦限帧，分别测首次和预热释放前后窗口；关注最长帧、P99、1%/0.1% low、最低一秒、CPU/GPU 与分配量，再决定 GPU 材质/上传等剩余优化。角色俯仰和阶段绑定的接口与边界见[女皇技能特效](../../systems/effects/queen-beam-skill-effects.md)。


## 真实女皇技能释放与代码动画逐帧显示（2026-10-19）

后续已在重启后的 Debug 客户端使用真实 LaserSkill 连续施放，并用本地 Spark 与逐帧采样定位首次和预热长帧。当前帧容量为 131072，summary.started_ns 用于阶段精确对齐；额外统计从蓄力入口到释放后两秒的完整窗口，避免把工作前移当作消除卡顿。

最新前后表、完整原始记录索引、被撤回的试验、加载缓存/Bloom 的内存代价、渲染范围和稳定目标的实际状态见[释放瞬间实测](laser-release-profile.md)。可复用开发流程由 QueenBeamPerformanceTrial 承载，不执行 Debuggee 异步表达式或在采样期间暂停；默认未配置环境变量时不启动测试。可选 JFR 分配记录只用于定位分配栈，带采样开销的记录与无采样器成绩分列。
