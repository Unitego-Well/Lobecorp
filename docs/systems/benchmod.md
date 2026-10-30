# BenchMod 基准工具链

## 2026-10-31 实例创建架构三方案对照

根/FX/PROJECT统一创建边界已实现，A编译fresh、B受管完整复位、C索引布局均可显式启动；C保留原生kernel，不是完整ECS/GPU。38纯测试、编译/生产包隔离与Data通过（当前23份生成JSON重复SHA一致）。代码完成后12独立JVM/世界、36次STARTED，每局主40秒及归档后74契约通过；首个13fail样本另存并排除，修复后N01r1独立重测。

| 策略 | P95中位ms | P99中位ms | 最坏max ms | >50ms/三局 | >100ms/三局 | >250ms/三局 | 平均分配GiB/40s |
|---|---:|---:|---:|---:|---:|---:|---:|
| NATIVE | 11.884 | 31.622 | 249.921 | 93 | 24 | 0 | 5.760 |
| COMPILED | 11.603 | 26.373 | 495.826 | 60 | 19 | 4 | 4.722 |
| POOLED | 12.155 | 26.560 | 463.592 | 77 | 21 | 3 | 4.311 |
| INDEXED | 12.594 | 25.871 | 401.620 | 50 | 15 | 3 | 4.217 |

P95/P99为三局中位，max为最坏值，阈值数为三局合计。分配含采集开销，不是净保留。C减少>100ms频次37.5%及平均分配约26.8%，但最坏402ms高于原生250ms；没有全指标赢家，默认NATIVE，A/B/C保留开关。P22/P23开放，无本批光影/完整视觉或长期生命周期验收。

[接口与所有权](photon-runtime-creation.md)；本地完整报告 `.agent/reports/20261031-runtime-redesign-final.md`，证据 `build/agent-backups/20261031-runtime-redesign/`。四套freeze-v1各694原字节文件、freeze-v2各8覆盖文件及独立manifest已保存。六配置原SHA和五项原WIP/暂存SHA保持；自有游戏/Debug/observer/日志点/子代理已回收，未推送。


## 2026-10-31 核心长帧归因与次要克隆优化

本批离线重读最新A/B/B/A四局JFR，main/prewarm分别用owned完整CSV对齐并核对pid。74个>50ms间隔合计7.179s，GameRenderer段墙钟2.150s、直接GC重叠160.459ms；185个慢帧Render Java样本中76条栈截断。重叠分类为子派生46、持久化52、NBT copy20、几何21、诊断46，不是独占CPU比例。±2ms是假设敏感性检查，非校准误差上限，raw trace仍有界。

源码确认nextTemplate消费prepared后置空，后续无候选即同步create；FX来源创建完整runtime，PROJECT来源逐对象copy并建runtime/timeline。首批慢帧另有类加载/调用点/几何/绘制；不能把所有停顿归为反射、材质或GC，也没有重开Debug影响隔离。DS累计表8份objects SHA通过，resource列全空，不能伪造资源归因；shared/inclusive/嵌套分配不相加。原DS回报中的两处趋势/对象口径已在主报告纠正。

次要优化仅省canonical typed恢复的一份adapt整树克隆：共享校验+borrowCanonicalConfig，公开adapt仍独占，renderer小子树/asset/依赖/source继续独占，旧适配与错误顺序不变，公共类型Codec未改。

全部代码完成后编译/包隔离、7定向JUnit、Data通过（0写删、29生成SHA不变）。一个全新普通FULL三触发、44原生用例通过；15模型每方案240copy相对原生分配少20.093%，只是单JVM局部对照，不与旧10.8%相加或称FPS收益。主窗最长405.416ms、>50ms19帧，P22/P23保持开放，无光影/截图。

六配置字节恢复、5代码验证后未变、原WIP/暂存保留；Sol关闭、DS对照归档，客户端/Debug/observer结束、日志点移除。报告.agent/reports/20261031-core-problem-and-secondary.md，DS原始回报另存并注明勘误；证据build/agent-backups/20261031-core-secondary/，业务提交仅本轮文件，未推送。

## 2026-10-31 模型复制与默认材质准备优化

PhotonModelOutput接入有界typed标量恢复，保留完整公共Codec编码、guard、adaptObject和source，复杂renderer继续原生parser，未消除大NBT拷贝。两个photon.material.client Mixin正常client注册，仅延迟确切ShaderGraphResource未用default_particle的原生生成，保留provider/global/meta/LoadBuiltin/真实Map/缓存，不改GL线程或负载。

全部代码和控制配置完成后，编译/生产包隔离、4项机制JUnit、Data通过（0写删，29生成SHA不变）。同源A/B/B/A四新JVM/世界各40秒，共12触发/172原生用例通过；A为nativeModelCopy=true和eagerShaderBuiltin=true，B均false，默认false。归档前材质计数与归档后checker分账；pack钩子用例不是完整Minecraft重载。

| 局次 | 主间隔均值ms | P99 ms | 最长ms | >50ms |
|---|---|---|---|---|
| A1 | 8.333 | 34.468 | 351.570 | 19 |
| B1 | 8.082 | 32.096 | 241.920 | 17 |
| B2 | 7.998 | 31.103 | 298.247 | 20 |
| A2 | 8.068 | 33.821 | 287.429 | 18 |

两组>50ms各37帧，不宣布FPS/长帧修复。每JVM11资源/80对象、15模型每方案240copy微对照，配对分配少10.793%～10.884%，有一轮耗时反向22.35%。B文件请求39080/40464期间1个lazy provider/0默认图生成；计数含热命中，非磁盘I/O次数，A原生provider不计新计数器。

四局各50材质任务全在main前，最慢A280.537/250.142ms、B228.795/194.555ms，预热最长仍0.87～1.59秒。JFR/Spark、19877桥接及完整CSV SHA有效，raw trace有界覆盖；非旧12局矩阵重跑，不与旧探针批次作FPS因果比较。无光影/截图，PNG0，P22/P23开放。

六配置SHA恢复，验证后源码哈希不变，WIP/原暂存保留；四游戏/Debug/observer结束，自有日志点移除。接续Sol关闭，deepseek-v4.1-flash中转站low对照完成并归档。完整报告.agent/reports/20261031-copy-material-optimization-final.md，证据build/agent-backups/20261031-copy-material-optimization/。本地控制配置已消费world/result，重跑须新建标识。

## 2026-10-31 四线并行批次收尾

10-30启动的复制/材质/长帧/释放四线已统一集成。新PhotonRuntimeCopyLedger以lobecorp.photon.copyLedger.enabled显式开启（8次/块、60秒软预算），八个材质Mixin仅lobecorp.photon.diag.material=true时应用，真实mapping执行计miss、实际GL调用单独计数；默认均关闭，插件不提前加载客户端诊断类。PhotonRuntimeLifetimeNativeCheck只在lobecorp.photon.bench.retentionProbe=true且main/复制账本归档后请求GC、检查缓存/弱引用/整堆差，不污染40秒窗口。

生产修复：ConductorControls.clearSession分离纯清理，不在卸载/注销grabMouse或向关闭连接syncView，修正既有断连GUI异常并让后续Photon清理继续；prepare已有的EMBEDDED死键扫描提取pruneCleared并接到生命周期clear，只删已回收求值器的键，不淘汰live或RESOURCE准备。

6 Java+7 Python测试通过，回收修正后受影响3 Java重跑通过；编译/生产包隔离/Data通过（0写删、29生成SHA不变）。v1两新FULL+v2最终激光复验共3局9触发、82原生检查通过，不是12局矩阵。三局各11资源/80对象账本完整；各8独占runtime/120引用回收，最终3死键630868B→0、RESOURCE4不变；整堆差不等于模型独占净存活内存。三次退出未见旧GUI/RejectedExecution异常，Native v1 IDE stdout仍有Spark停止采样CancellationException，文件保存和场景通过。

最终预热50材料：GRAPH_LOOKUP35次/18hit17miss/1278.754ms；实际glCompileShader34次/3.119ms、glLinkProgram34次/19.382ms（尝试，不是成功program数，inclusive不相加）。最慢材质436.985ms，记录GL调用段不足以单独解释。旧PID8284主窗JFR慢帧Java330样本中子派生119、ParticleEmitter.update12；不是wall独占。未新增生产复制快路径或声称材质提速。

main native P99/max21.654/240.403ms，laser v1=187.727/680.316ms，v2=180.618/731.667ms；新探针组合不能与旧组直接做FPS收益对比。六配置恢复，WIP/原暂存保持，三代理/游戏/Debug/observer结束。完整报告.agent/reports/20261031-photon-parallel-final.md，证据build/agent-backups/20261030-parallel-optimization/。一份分析器.pyc清理被自动审批blocked by policy拒绝，IDEA只读预检也未定位，保留且不提交；中转DS Flash实际404无产出，失败对话已归档。

## 2026-10-30 类型复制和材质粒度预热

PhotonChildSpawner/PhotonNamedController接入有界typed copy：每次保留完整wrapper编码、guard、unknown/source与当前定义，只直接恢复已核对的独占字段。Curve.copy会丢lockControlPoint，曲线/扩展类型/实际规范化差异继续原生parse；不共享config/transform/槽/随机/播放状态。PhotonRenderPrewarm按准备、单材料、单变体任务排队，保留材料优先、渲染线程和既有重载清理；WorkQueue的代次防止clear后的旧continuation回填。builtIncludingHits包含命中，10ms仅限制开始任务。

所有代码完成后，编译/包隔离、7项fake-clock队列测试和Data通过（0写删、29生成SHA不变）。4项复制JUnit因缺FML未执行断言，已备份并迁入原生入口，不计纯测通过。两个新普通FULL进程各3触发/40秒完成、总体及03场景PASSED，20项元数据（含encode-first真实缓存命中）+24项复制原生检查通过；不替换旧12局矩阵，光影/截图未执行。用户配置六份SHA恢复，WIP/原暂存保持，游戏/Debug/observer和两Sol代理均结束；中转DS Flash实际404无产出，失败对话归档。

真实激光根FXData为13对象（1spawner/1controller），两JVM ABBA分配观测约降1.22%～1.25%，墙钟未改善，未触及新类型的control也有JIT/次序差，不宣称FPS收益。40秒native均值7.071ms/P99 20.101ms/最长214.376ms，laser均值13.693ms/P99 151.029ms/最长775.821ms；laser>50ms117帧、>250ms11帧，P22/P23开放。

激光预热32次drain、50个MATERIAL任务、15个CHILD、built50/failed0；SHELL/MATERIAL/CHILD单项最大1143.772/587.177/822.792ms，模型准备也可超预算。VARIANT本次0，仅表明未触达，不是变体全覆盖。退出仍有既有GUI断连异常，native另有RejectedExecutionException，不据PASSED关闭P18。完整报告.agent/reports/20261030-runtime-copy-prewarm-final.md，证据build/agent-backups/20261030-remaining-optimization/及两份独立raw-results。新的本地Remaining运行配置保留已使用world/result，重跑须改成新路径。

## 2026-10-30 编码端元数据缓存与离线往返对照

生产 MetadataMixin 现在同时包装 PersistedParser.serializeInternal / deserializeInternal 各两个声明数组调用，复用同一 ClassValue helper，order900 和正常 client 安装不变。默认 deepCopy 先编码再解码，上轮仅解码缓存未覆盖编码端；不共享 ManagedKey/IRef、实例、provider 或可变 FX/NBT，不改原生算法和负载。

全部代码完成后，IDEA 定向检查无错误；8 项真实 parser 回归通过，另含生产注解选择器/描述符/order 静态 gate（不等于实际游戏注入）。三个独立 JVM 以 A=上轮仅解码缓存、B=两端缓存对照，每 JVM 每方案 1200 fixture 往返；暖测 A 编码 methods/fields 各4800次、B各0，解码均0。分配约12521～12789→9944～10250 bytes/往返，观测下降19.85%～20.60%。包含dispatch、构造、Tag copy/hash和语义校验；不是实际FX/FPS/净存活内存收益，冷固定A后B也不作首发性能证明。

编译、生产包隔离与无窗口Data通过，Data0写删、29生成文件SHA不变；未启动游戏、Debug、截图或新12局矩阵，P22/P23保持开放。中转站Flash low辅助实际404（组内无账号支持该模型），无DS结论，未回退路由；失败对话归档，两个Sol实现代理关闭，三基准JVM退出，用户WIP/暂存保护。证据build/agent-backups/20261030-encode-metadata/，完整报告.agent/reports/20261030-encode-metadata-final.md。基准入口PhotonPersistedParserBenchmark，使用独立新输出目录。

## 2026-10-30 原生持久化元数据首批优化

按新目标先做v6四组12局36触发，再依据新报告实现，代码及必要配置全部完成后统一验证。生产PhotonPersistedMetadataUtil用两个ClassValue惰性cell分别缓存当前声明类Method[]/Field[]；PhotonPersistedMetadataMixin仅包装PersistedParser.deserializeInternal的两个调用，正常client默认安装，miss保留原Operation链，诊断order1000在缓存order900外层观察访问。缓存不持有Operation/Supplier/实例/IRef/ManagedKey，不移动父类、root或setter顺序，不改FX/资源/技能/生命周期。

7缓存单测、3真实原生parser双路径回归、9诊断账本共19项通过；三独立JVM元数据访问微基准数组内容/顺序等价，暖访问原反射38400→0、1475→0 bytes/访问、约287–295→84–88ns/访问，仅局部访问范围，不是全parser零分配或FPS收益。test/bench复用现有MixinExtras普通JVM类路径，检查专用补充不进FULL游戏类路径，不加生产依赖。

优化后v8四组12局36触发完成，六FULL总体/场景PASSED；每局测量及诊断归档后执行9项原生Codec/回调/继承/嵌套flatten/实例隔离/Tag不变与8缓存槽检查，54/54通过，不混进40秒主窗口。v7集成pilot因生成VM丢失mcp.port导致监测不足，排除性能对照；受控FULL参数现显式19877，防止IDEA同步覆盖生成值。

实际FULL复制账本按copy归一化，分配中位数约849335→786475 bytes/copy，观测下降7.401%，含探针和类型组成差异。整局时间没有改善：Run激光合并P99 v6=11.659ms、v8=28.375ms；普通Run P50约1.048→6.198ms。两个当前环境未安装缓存的独立控制仍为约6.1–6.3ms底部节拍，仅支持节拍变化不能单凭缓存存在归因；不证明每个慢帧无关，也不宣称FPS收益。P22/P23继续开放。

六份独立启动JFR把进入世界列为次要：world_open→run_start代理8.124–10.300秒，非精确冻结时间，加载GUI及登录后JEI2.711–3.434秒为后续候选，未改JEI/原生GUI。DS flash低档两份复核完成，第三轮上游402余额不足中断，未假称通过或切Pro。所有游戏/Debug/辅助会话结束，配置原字节恢复，原WIP和原暂存参数保护。证据build/agent-backups/20261030-goal-retest-baseline/及20261030-goal-optimized/；完整本地报告.agent/reports/20261030-metadata-optimization-final.md。编译/生产包隔离、Data0写删通过，原始控制与失败证据保留。


## 2026-10-30 三连发 FULL 与无测试参数 Run 矩阵

四组各三局、每局三次触发，12局/36次已完成；每局独立JVM与新世界，主窗口40秒自然DURATION。Run使用真实VM无测试参数入口；FULL使用Sodium、BenchMod、MCP、IDEA Debug、JFR、Spark、CAST、提交及逐对象GPU诊断。六局FULL总体/场景均PASSED，测试截图0。不能将运行通过解释为长帧修复或视觉验收。

共用核心 PhotonRepeatedEffectTrial 由唯一客户端tick驱动，2/7/12秒调度独占实例、保持五秒开始间隔，记录真实派发/服务端开始/首次活跃、重叠、内存、窗口、资产与加载类哈希，异步导出及自有对象清理。MCP扩展 photon_repeat_status/start/cancel/close/prepare；BASE前端拒绝真实测试VM参数，FULL通过03场景启动。prepare只最大化自有游戏，不调用focus。原生资源为原字节first_effect2，保留禁用扩展字段，不改变生产技能冷却、数量或生命周期。

先后观察到AGGREGATES、NODES、COUNTERS容量停止，失败原件保留且不计有效局。三个独立诊断属性的默认预算仍4096；maxNodes和maxCountersPerThread钳制4096..16384，maxAggregatesPerThread钳制4096..65536。保留所有失败与质量guard，raw环不扩大。有效FULL为v4普通三局+激光01，v5激光02/03；v5额外扩counter预算。有效采集中所有容量拒绝为0，aggregate完整，raw仍是有限尾环，不能声称全程原始事件完整。

主窗口间隔P99/最长(ms)：Run激光17.327–18.868 / 104.136–129.843；FULL激光70.562–160.500 / 477.709–771.786；Run普通8.643–9.868 / 29.980–67.201；FULL普通10.974–11.168 / 133.564–171.806。native三次之间无重叠，laser触发前活跃数0/1/2。不同负载、探针组合、容量和跨时段不是单一变量A/B，不据差额宣称优化收益。P22/P23保持开放。

可复用入口在tools/photon/performance/及IDEA Photon Run Control No Test Parameters / Photon Repeat Full。build_repeat_manifest支持显式v4+v5重试选择并保留来源；FULL CSV规范化为带原路径/哈希的帧JSON。审计按重复-D最后值、DURATION实测窗口和世界初始快照分类；静态FX/材质/纹理仍严格校验。分析区分矩阵执行完整性与配置可比性，保留straddle、前一render归属、连续慢帧和unknown。原始机器数据不因审计修正而改变。

Java目标回归29项、Python合成74项通过，IDEA main/bench编译及生产包隔离通过；Data written=0/removed=0。六份用户options/Iris/Spark恢复原字节SHA，原四WIP与既有暂存run-control参数保护；游戏/Debug与任务子代理收尾，未推送。证据build/agent-backups/20261030-repeat-matrix-final/；矩阵已完成但进程环境快照等缺失仍unknown，后续研究优先子实例Codec复制和原生持久化元数据，光影未测试。


## 2026-10-29 第一批采样分配优化与研究复核

PhotonModelPoseSampler在原线程私有SamplingScratch内复用part/bone矩阵、pivot和morph数组，并按组/角点数预分配输出列表；对外帧/顶点仍独立不可变。PhotonNamedRuntime稳定成员集复用排序快照，所有成员put/remove与close失效，不改优先级、事件与释放顺序。没有新资源池/容量限制/用户开关，不改NormalCache或技能负载。

离线bench入口PhotonSamplingAllocationBenchmark用旧JAR与新class的独立child-first加载器，在同JVM/Prepared/输入比较完整raw float属性、帧不变性、线程分配及新增直接工作区。三次新JVM四fixture472组输入全等价，分配降幅中位数dust_column34.71%、laser_body36.07%、reverse_muzzle12.72%、source_dust14.27%；增加368–616 bytes/采样器/线程直接工作区（非总堆/资源/GPU）。旧JAR指纹与输入/CSV/等价hash均保留在本批build证据目录，未用重写的旧算法。

普通模式单次激光三轮总体/场景PASSED，正式均值7.305/8.213/7.044ms，最长308.581/283.902/262.119ms；no_prepared197/196/196，长帧未解决。观察到原行断点外部移除，保留最新两异常断点并解除mute；不能把调试条件变化、跨时段游戏数字当完整优化A/B。P22/P23不关闭，光影未跑。新scratch7项+Geometry5项通过；IDEA编译/生产包隔离及Data通过，29生成文件与三配置/四WIP原SHA核对不变。

DS的S/H/P三个独立研究包已交付停止；原生持久化尚未改，不能直接以IManaged字段容器代替Configurable解析，也不缓存实例IRef或急切构造全部key。用户新的“三次触发/间隔五秒/全面与无测试参数Run对照”矩阵另起批次，本段三轮每局只有一次不能冒充新矩阵。完整本地报告.agent/reports/20261029-first-performance-optimization.md；证据build/agent-backups/20261029-first-optimization/。


## 2026-10-29 对象内部诊断与原生数据对照

只补信息，不优化。四个新增诊断Mixin在显式cast诊断时安装：IFXObject对象分派、FXObjectType基类fixData、PersistedParser标准工厂、反射与持久化。PhotonFXLoadDiagnosticsUtil提供startDetailedCapture(label)/finishDetailedCapture，导出对象身份/资源/父边界/返回或异常，以及按对象、阶段、声明类、字段、配置路径、root与结果类型分组的count/inclusive/exclusive/max/failed。对象summary写FXLOAD，字段不逐项写日志；结束清空，不保留世界/NBT/业务对象。coverage/missing与attributionComplete=false明确未计时工作，自定义codec绕过处不作零成本结论。

PhotonBenchDiagnostics只增强本场景唯一RUNNING的BenchMod录制，ClassLoad/ClassDefine阈值0、FileRead/FileWrite/JavaMonitorEnter阈值1ms，均开栈并回读实际settings；记录game_pid。两组都在世界稳定十秒后开始，正常/异常/teardown归档一次详细JSON。

沿用精确场景lobecorp.photon-02-first-cast-performance，缺省仍为真实激光；临时-Dlobecorp.photon.bench.controlFx=photon:first_effect2选择PhotonNativeControlScenario。原生对照为已有编辑器包FX，单particle_emitter、无生效扩展、20tick非循环。保留12个禁用扩展字段，所以no_active_extensions=true但strict_zero_extension_keys=false；Lobecorp guard/Mixin仍安装，不能称为纯净Photon。材质和纹理按原SHA临时布置在隔离ldlib2/assets，先真实FXHelper.getFX再哈希审计，防止预读；原件不改，结束清理实例三份副本。原生必须有活跃粒子/发射器、other jobs、Photon drain draw调用与材质解析，不能用model jobs为0误判空负载。

六次新进程按laser01/native01/laser02/native02/laser03/native03串行，PID41540/48080/65512/75432/72908/20876，等待10.009–10.044秒，全为总体/场景PASSED、完整未截断、PNG0。激光每轮10FX/79源对象，30次查询中20次无本地load；普通每轮1FX/1原生对象，峰值400粒子、1发射器、40次活跃tick。

| 组别 | 首次最慢FX查询ms（3轮） | 预热最长ms | 正式均值ms | 正式最长ms |
|---|---|---|---|---|
| 激光 | 4401.500 / 2018.250 / 1925.351 | 5978.090 / 3748.744 / 2788.455 | 8.445 / 8.868 / 7.387 | 346.897 / 399.444 / 648.169 |
| 原生数据 | 450.411 / 433.713 / 423.831 | 570.639 / 553.490 / 555.110 | 3.355 / 3.296 / 4.121 | 148.150 / 130.250 / 180.581 |

激光主要冷阻塞进一步定位：star_beam_spell_beam中的laser_body，PhotonModelOutput主工厂3915.945/1533.647/1497.198ms，其构造调用点内NormalCache的JFR ClassLoad为3879.273/1509.355/1464.751ms。该类加载包含区间覆盖工厂绝大部分，不能称为秒级法线算法、纯类转换CPU或精确排除Debug/JVMTI等待；原用户条件断点在该嵌套类内，采集mute无暂停，但没有无Debug配对。普通ParticleEmitter主工厂128.545–163.880ms；对象内部141次父类/子配置CLASS、205次KEY/REF/WRITE，字段引用57–83ms，也有真实首次反射/加载开销。

两组对象数、持续时长与材质不同，普通活跃仅约1.8秒、20秒含长空尾；总均值差不能当扩展功能净成本。正式最长帧另外有模拟/绘制/Codec/NBT复制/类加载及诊断聚合样本，不能都归到NormalCache。激光详细JSON约66MB/轮、13万聚合行，探针与输出成本明确存在，不能作无探针性能A/B。P22/P23继续开放，光影暂缓。

IDEA编译/benchClasses/生产包隔离通过，诊断单测9/9；Data成功written=0/removed=0、含缓存29文件哈希不变。三份隔离配置原字节恢复、三断点保持并解除mute、游戏/helper/桥接/JFR读取均结束。四份用户WIP哈希不变，不改写历史DS误纳暂存测试。方法与逐阶段全文见本地.agent/reports/20261029-deserialize-detail-comparison.md，完整证据及脚本在build/agent-backups/20261029-deserialize-detail-comparison/，原始报告在build/modBench/raw-results/photon-deser-compare-20261029-{laser,native}-0{1,2,3}/client。普通artifact前缀photon-02-native-data-control，激光photon-02-first-cast-performance，分析按workload推导前缀。


## 2026-10-29 FX 冷加载与校验分段诊断

现有 `lobecorp.photon.diag.cast=true` 同时开启新增 `PhotonFXLoadDiagnosticsMixin`；插件只在该属性为true时装载此Mixin。关闭时新增代码不生成计时对象、读时钟或写FXLOAD日志，原校验保持。没有新增缓存、跳过校验、重排预热或改变负载。

`FXLOAD` 汇总边界分query／load／template_prepare／template_next，带id、parentId、parentBoundaryId、parentStage、rootId、FX及打开资源、线程与单调起止ns。阶段包含OPEN、READ_COMPRESSED、VERSION_FIXES、DESERIALIZE、GUARD_FX、GUARD_OBJECT、GUARD_TIMELINE、GUARD_TRACK及模板创建。每阶段记录count／inclusive／exclusive／max／failedCount；exclusive只扣已计时子调用，仍含等待、GC、对象内部检查及诊断开销。子边界明细不重复汇入父账本，需按父边界关联，不相加包含时间。

query的 `reuse_possible_wait` 仅表示没有在本查询线程进入loadFX，不保证无等待；通过实际loadFX进入标记分类，不额外探测CACHE。`failed` 表示异常逃逸该计时scope，原加载器捕获异常并返回null时仍须结合returned=null及子阶段失败数判断。useCache仅在query行有查询含义，load行的false不是更改原缓存策略。

prepareNext增加直接definitionNs／definitionStartNs／definitionEndNs、namedNs，并以diagnosticId关联内部query。三轮普通实测均通过真实负载与计时关联检查，十秒门禁保留、测试PNG为零、光影未跑；每轮30次query、10个不同FX各冷加载一次、20次无本地加载复用。首个慢资源均为star_beam_spell_beam.fx，直接查询4520.764／1795.049／1520.958ms，其中DESERIALIZE为4507.145／1789.790／1515.126ms，打开和压缩NBT读取合计仅3.700–8.735ms。已单独计时guard为310.437／157.746／127.073ms，剩余反序列化区间仍未分解为内部校验／反射／类加载等，不宣称优化或独占CPU归因。

代码／4项纯账本测试、IDEA编译／生产包隔离、Data及三次普通采集均完成；Data生成29文件哈希不变。测试源码指纹、完整原始日志、分段与JFR分析位于 `build/agent-backups/20261029-fxload-diagnostics/`，原生结果为 `photon-fxload-diag-20261029-plain-0{1,2,3}`。新增观察者开销没有开关对照，帧时间不作优化A/B。

## 2026-10-28 无截图性能采集与进入世界后延迟

显式场景 `lobecorp.photon-02-first-cast-performance` 只运行一次真实光束，不混入合成模型。PERFORMANCE 模式跳过暖场／OUTPUT 截图与图片读取；精确 profile 的 bench-only 桥阻止框架失败截图，不伪造成功图片。验收改为真实模型 CPU jobs、顶点、索引及 drain draw calls 非零，且提交／drain／协议错误为零；采集通过不等于首施放性能达标。

场景在客户端世界与玩家存在、screen 和 overlay 均消失后连续等待至少 10 秒，等待期间再次出现界面则重计。之后才启动本场景 Spark、预热帧采集与服务端夹具；BenchMod 自带 JFR／阶段计时仍可覆盖等待段，分析须按 world_available_ns／world_settled_ns 排除。WARMUP 原有 100 tick 保留。正式主窗口为 20 秒，MEASURE 还含 Spark 停采／导出／保存／实件验证尾部，不混用两者帧统计。

`PHOTONSTAGE` 记录世界可用、等待完成、夹具投递、主采样与停采的单调时钟和 epoch 锚点。显式 `lobecorp.photon.diag.cast=true` 补充命名运行态 prepared／matchesController／重建原因、buildNamed／emit 完整耗时和子模板准备／采用／创建／启动；原预热队列与 CASTPROF 补绝对起止 ns。嵌套耗时不可相加；诊断开销未以开关对照量化。

本轮普通／ComplementaryReimagined_r5.9.3 各三次独立进程、新世界与独立原始报告，全部等待 10.008–10.049 秒、真实负载通过、测试 PNG 为零。普通主窗口最大间隔 328.741／452.012／839.768 ms，光影 433.499／820.846／993.090 ms；预热最大间隔分别 5169.203／4274.698／3657.454 ms 与 2743.218／2916.295／4418.074 ms。前五轮总体 PASSED；光影第三轮场景 PASSED、总体 INCONCLUSIVE，原因是环境审计记录初始窗口 resize。用户明确不为该项补跑，标记原样保留。

六轮均记录外层命名控制器改变后重建一次；子模板各复用 6 次、现场创建 192–199 次。首个 SHELL 任务 2.007–4.515 秒，确认 10 ms 队列预算无法限制单个同步任务。P22／P23 仍开放，不把延迟后结果与旧诊断配置直接解释为优化收益。原始数据位于 `build/modBench/raw-results/photon-delayed-20261028-{plain,shader}-0{1,2,3}/client`，完整控制台、采集摘要及逐帧 JFR 分析位于 `build/agent-backups/20261028-delayed-performance/`。

本轮光影三轮另有28／27／27条 Iris `Missing program ... in override list`，在正式施放阶段输出 Throwable 全栈；当前 Iris `MixinShaderManager_Overrides.redirectIrisProgram` 按 missingShaders 集合首次遇到管线时记录，不能称为每帧无限重复。长帧 JFR 同时采到 Throwable 栈扩展／格式化、控制台与文件写入，证实日志路径参与开销，尚未量化独占时间。六轮最长正式间隔中的 GC 重叠为0–8.489ms，不能解释全部长帧；旧诊断中的 glCompileShader 证据不直接代替本轮逐帧归因。

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
