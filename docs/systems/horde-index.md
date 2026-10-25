# Horde 当前文档入口

核对日期：2026-10-25。本工作区只负责 Horde；其他系统保留为[共同基线](README.md)。本文依据工作区当前源码与已有报告整理，包含未提交实现；源码存在、历史运行通过与本轮验收分别判断。

## 本轮任务进度

2026-10-25 游戏测试重新获用户授权：核对HEAD 11f7721及保留的构建号WIP，使用Sodium+BenchMod隔离普通/光影组，另用IDEA Debug与Minecraft MCP检查当前实例。当前无已有Horde游戏/Debug会话；原Bench配置已经IDEA备份到build/agent-backups/20261025-runtime-compare/。本轮先采事实和失败，不以旧夹具数据代替新直线512夹具验收；后续实测、配置恢复、临时进程清理和对照结果持续更新。测试已结束，普通client-1792939439969与光影client-1792939667496最终summary均3场景PASSED，但浪潮截图近乎天空，计数不等于可见性通过；光影均值15.945ms/P99 31.673ms、GPU 15.578ms，不能标60FPS达标。Debug/MCP新建独立世界的真实事件可见，4帧及520/304/176三档已核对；事件清除、保存退出、control_mode=false，Debug客户端PID28480已正常关闭，临时断点已清理，既有断点保留。离线分析与完整测试/历史对照报告已写入benchmod.md，优先问题归入horde-issues.md的H27/H28，不再运行游戏。本轮未修改生产代码；5份文档错误诊断0错误，提交范围仅限本次报告与进度，保留其他WIP；光影10份带注册SHA的制品已全部核对匹配，补齐尸体环境制品后不再有该项缺失。普通首轮仅summary和独立GPU捕获保全，JFR/完整JSONL归档不完整；光影17份非日志产物已通过IDEA逐文件归档到iris-1，详细限制和对照将写入BenchMod及唯一问题清单。

2026-10-25 本批代码已写入并完成无游戏检查：EX-01三组非嵌套GPU计时/共同帧标识、EX-02隐藏面剔除、EX-03/04/06/07 Iris 4/8/16 MiWords双缓冲与窗口材质合批、EX-05辅助缓存、EX-08显式三档模型、EX-09缩档/原生回退/代际恢复、EX-10 long光照缓存、EX-11增量预热、EX-12运动缓存读取、EX-13稳定编号GPU位移/方向历史及标量归约、EX-14默认四帧等距采样、EX-15静态尸体状态与清道夫冻结网格批绘、EX-16尸体基准、EX-17直线512夹具均已接入。37份Java/GLSL错误诊断全部analyzed，修复两项本轮错误后为0错误；Java警告已审阅，包含借用/持久缓存拥有者提示、内部API、既有空值/注解及未使用项，不宣称零警告。IDEA BenchMod Compile退出0（主代码/基准编译及生产包隔离检查），Data退出0；29份生成文件只变更两份语言和两份缓存，新增9组中英配置翻译，无删除。未运行JUnit、游戏、BenchMod Client/Server或Debug，GPU硬件编译、光影、画面、60FPS及全部A/B门禁保持NOT_RUN，不标DONE。逐项实施、回滚和限制已同步，93份授权文件提交c21899d，原5项暂存按用户A随提交消耗，未推送。工作区只保留通用构建号闭包差异和历史日志；H20关闭。下一步仅在另行获准后进行实机A/B与GPU/画面验收，当前停止于代码交付。备份：build/agent-backups/20261025-extreme-confirmed/，含原字节、SHA256与原5项暂存补丁。

2026-10-25 计时口径确认：用户选择A，新增独立Horde draw整体GPU计时，与阶段求和按同负载分组对照；15%为需要调查的覆盖审计阈值，超出不直接判统计错误。世界GPU与原性能目标保留，不能逐帧精确对账，预热不计入draw总量。已同步执行文档与唯一问题清单，代码尚未实施；本轮只有文档编辑和回读，没有编译、测试或启动游戏/BenchMod/Debug。下一步确认H20提交归属。片段备份：build/agent-backups/20261025-measurement-choice-a/before.json。

2026-10-25 尸体动画确认：用户选择A，本轮不新增尸体重组动画及对应客户端同步，继续静态尸体优化，保留清道夫施术动画、服务端重组进度和复活流程。已通过IDEA同步执行文档的实现/验收范围与唯一问题清单；本轮没有源码修改、编译或运行测试，游戏/BenchMod/Debug仍禁止。下一步确认EX-01计时比较口径。片段备份：build/agent-backups/20261025-corpse-animation-choice-a/before.json。

2026-10-25 尸体距离确认：用户选择B，保留5区块（80格）基础跟踪；180格仅为已存在客户端尸体的渲染上限，复杂/动画尸体仍为32格，不扩大服务器下发范围。已同步问题清单和执行文档；本轮仅文档记录与回读，没有源码修改、编译或测试，游戏/BenchMod/Debug仍禁止。继续确认尸体重组动画范围。片段备份：build/agent-backups/20261025-corpse-tracking-choice-b/before.json。

2026-10-25 执行顺序确认：用户选择A，后续已确认代码按实现依赖继续，不因前置实机门禁未执行而停下；未确认的关键取舍继续逐项询问。运行门禁仍全部未执行，不降低目标或标为DONE；游戏、BenchMod和Debug须另行明确授权。本轮仅通过IDEA同步四份文档并回读，没有修改源码或启动任何测试；修改片段备份：build/agent-backups/20261025-execution-choice-a/before.json。

2026-10-25 异议确认：用户选择EX-13方案B，平滑优先、每tick位移≤0.75格；突发包围允许多tick退出，期间不隐藏。已同步唯一问题清单与执行文档的合同、步骤和验收口径，代码未实施。本轮仅记录决定，未启动游戏、BenchMod或Debug，未执行编译、Data或测试；继续逐项确认剩余问题。

2026-10-25 极端优化执行中：依据执行文档 v2，先实施无依赖的 EX-01 测量固化、EX-10 光照整数区段缓存及 EX-11 增量模型预热。用户本轮明确禁止游戏测试，覆盖执行文档的强制 BenchMod/Debug 条款；本轮不启动游戏、BenchMod Client/Server 或 Debug，全部实机门禁标为未执行，不提前进入依赖已通过门禁的任务。代码、计划、总览和唯一问题清单的修改前原字节通过 IDEA 保存为 Base64 备份及 SHA256 清单：build/agent-backups/20261025-extreme-code/。已记录 EX-13 瞬移深度与每 tick 位移约束冲突、GPU 阶段和世界计时窗口差异；继续独立授权项，不宣称 60 FPS 或完整计划完成。本批代码已接入：EX-01 完整阶段异步求和/独立世界查询/环境制品，EX-10 原生 long 缓存，EX-11 header 排队、增量烘焙、预算内管线预热和完整资源上传。统一 IDEA 静态检查完成：12 份 Java 全部 analyzed、0 错误；修正3项本轮提示后两文件复查0警告，剩余11项为GeckoLib内部API、既有可空引用/注解、借用世界资源提示及冗余抑制，未扩大清理。未执行编译、Data、单元测试、游戏、BenchMod 或 Debug，所有运行门禁未执行。 性能、GPU硬件编译和画面仍未验收；EX-01未通过实机门禁，后续依赖项不提前标完成。可独立提交范围仅含本轮新建 HordeBenchmarkEnvironment、开始时干净的 PhotonFrameCapture 和 changes.md；其余 Horde 源码/总览/问题页与既有 WIP 或未跟踪文件重叠，不能整份夹带提交，按 H20 保留工作区差异及原5项暂存。独立3文件已提交 7dcfe5e，未推送；其余代码和文档继续留在工作区，计划尚未完成。

2026-10-25 Sodium只读诊断已完成：普通、光影、干净光影复测三轮summary均两场景PASSED。10000实例帧间隔均值/P95分别3.290/4.215ms、34.967/36.100ms、56.359/74.134ms；对应GameRenderer GPU均值2.875/34.438/47.593ms（不同20/30秒捕获窗口，不混算）。六份JFR已按MEASURE窗口解析，GPU阶段、分配、日志、截图与代码对照归档；已确认事实、证据支持推断、待验证假设及优先建议见horde-issues.md的2026-10-25诊断节。主要判断是光影GPU执行/呈现路径压力，不能单独归因复制；两次光影明显波动，区域/区段不等同，未采GPU频率功耗，保留不确定性。六条Iris Missing program和退出/保存异常未关闭。864份源码/着色器指纹不变，没有代码修改；Iris、Sodium选项与options.txt哈希恢复/保持，Shader ZIP不变。客户端均已自动结束，仅保留原始记录及临时只读分析工具。备份：C:/Users/wang_/AppData/Local/Temp/horde-sodium-diagnosis-20261025-6d507349。下一步只记录建议，不实施优化；真实粒子、无限/多事件与联机场景未覆盖。

2026-10-24继续优化代码与无游戏检查完成：GPU 32/16/8三级候选位图、地形/区域同源GPU阻尼与流场、包围盒外至少3格加模型半径、同层战斗包围扩展、公共时钟连续校正、GPU异常保留有效缓存及累计失败校验、Iris GPU一次帧切线缓存、潮内客户端多区域分级粒子已写入。13份Java与9份GLSL错误诊断均0错误，必要引用核对完成；编译通过，新增9项、区域4项及既有缓存/统计10项共23项回归通过，Data退出0、29份含缓存生成文件SHA256全部不变。按用户随后取消测试的指令，没有启动/接管游戏、Debug或光影开关基准；GPU硬件编译、画面和性能数据均未取得，不用历史数据代替。备份及检查产物：C:/Users/wang_/AppData/Local/Temp/horde-spatial-smooth-20261024-124157-e4bdef48。源码仍与既有WIP混合，原5项暂存不动，提交边界见H20。

2026-10-24核实后优化进行中（不启动游戏）：①可落盘CPU阶段/工作量统计与不嵌套GPU轮换采样，失败阶段也保存；②光照覆盖/区段索引缓存；③稳定区域身份与GPU持久状态平滑、连续约束；④有界Iris批量展开/复制与材质合批。不改变数量、LOD、发光或动画采样；异议只记入horde-issues.md。源文件与生成前哈希备份：C:/Users/wang_/AppData/Local/Temp/horde-staged-optimization-20261024-9e6feda1。阶段①～④代码均已写入：阶段JSONL/失败收尾，光照几何/同刻区块状态/最多8份区段索引缓存，稳定区域身份与GPU中心/半尺寸/速度双缓冲，Iris工作集合批和静态中心UV缓存。9份Java与4份GLSL错误诊断均完成且0错误；BenchMod Compile成功，新增9项及区域4项回归通过；Data退出0，29份生成文件（含23份实际资源）SHA256全部不变。本轮未启动游戏、MC MCP、Debug或真实基准；运行性能、GPU驱动编译和画面仍待验收。源码与原有WIP混合，H20提交边界保留。

2026-10-24已复核DS两份诊断/测试报告并将核实与异议集中记入horde-issues.md：当前原始光影轮整体FAILED，10k夹具稳定阶段超时；光照集合重建是优先候选；PBO必经主机往返的归因未证实，260配置值表示不限帧；旧0.5范围/512行种子/缺少收敛分支等结论已对照当前源码校正。原报告与现存产物归档到horde-ds-review-20261024-d2f64f03。本轮仅审阅和同步文档，没有改代码、启动游戏或重跑测试。

2026-10-24尸体与粒子追加代码已写入：水平分割按体型3倍、Y保持真实高度；事件成员及其击杀目标尸体创建起保留200tick，战斗保护优先，到期仍受保护则延后；补齐受害者尸体来源与绝对期限存档，普通无来源尸体不扩大删除。默认24格可见且无遮挡范围内，每tick发射一次篝火烟雾/方块碎屑/原有刀光。编译、尸体3项及区域4项回归通过，Data退出0；23份实际生成资源仅中英文两份语言更新，另有两份生成缓存变化。本轮未启动游戏，原版粒子CPU生命周期和审批拒绝范围记于H26。

2026-10-24追加需求已写入：间距1.8格，基础速度不变、GPU随机加减速，左右最大1.5格且个体频率/幅度不同；小整数包围与GPU连续过渡、实际高度区间判断；单目标有效成员与预约上限10；事件成员不参与分割。BenchMod Compile、规则18项和区域3项通过，Data退出0且29份资源SHA256不变。详细优化/验收清单继续维护在horde-issues.md。本次按用户要求未启动、重启或接管游戏，前轮1.2格性能数据不作为本轮验收。

最近更新：2026-10-24。全部剩余问题只维护在[唯一问题汇总](horde-issues.md)，已完成证据记入changes.md；按用户要求不再主动询问，取舍和人工边界记录后继续。

- 已实现：开放多段平滑线路、默认纵横1.8格、随机加减速/横摆与GPU势流转向；每tick按玩家模拟距离发现Mob并保留profile生成资格；服务端仅清除战斗保护外的掉落物及到期事件来源尸体；未加载事件临时高度与延后采样；六整数小包围与GPU持久阻尼状态、原版Gizmo、统一客户端配置及翻译、真实和虚拟发光调整。
- 前轮已验证：IDEA编译、规则17项及区域3项回归、三轮Data和BenchMod Compile通过。普通和隔离客户端配置迁移/备份/旧值比对通过；远处未加载事件创建成功；一tick清除与战斗保护、保留普通牛已实际验证。上述证据不等同于完整联机验收。
- Iris实机：定位36→56字节扩展布局错位，GPU补实体标识、中心UV和切线后，100及10000虚拟清道夫实际可见，夜间遮罩发光可见；100个截图约115FPS。10000个30秒采样442帧、帧间隔均值67.98ms/P95 85.74ms、GPU均值67.89ms，约14.71FPS，每帧GPU转接292192256字节，仍是严重性能瓶颈，未宣称解决。环境为RTX3060 Laptop、2560×1494、Iris1.11.4/ComplementaryReimagined_r5.9.3、Vsync开启/120上限、前台最大化且限帧原因NONE。
- 实际复测：当前代码与1.2配置的BenchMod Client两轮de012627/8b660c93均退出0、两场景PASSED；64实体间隔均值4.84/4.31ms，10000实例2.41/2.30ms，GPU均值2.08/1.93ms。此前64实体明显回退未复现，未证明单一原因或实战FPS。独立BenchMod Server启动退出1，客户端第三方依赖缺类，未取得server数据。详见benchmod.md，剩余事项已统一按优先级整理。
- 运行收尾：第三轮Debug游戏59584（启动20:05:52.191Z）正常退出，临时代理67916/启动器59228/桥接69192正常关闭，均已核对不存在；退出接管为false、诊断显示上限恢复-1，Iris设置仅恢复自有时间戳，options.txt哈希不变。临时测试事件保留在隔离世界中，其他用户世界未修改。
- 备份与证据：C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d，含源文件、原配置、两轮完整基准、iris-layout56-10000.json及实际截图。源码混合既有WIP，提交隔离异议继续记录，不覆盖原有5项暂存。

2026-10-26 HORDE-RETEST-01（追加于进度末尾，避免覆盖并行文档整理）：先核实 H27 并修复基准门禁，再在同一 512 宽直线/10000 实例夹具上完成普通与光影复测，随后实施并复测 LOD 与单位历史页优化。已确认事实：H27 的空画面来自“玩家悬空作为事件/相机锚点”，相机被抬到地面之上约192.6格、超过12区块雾端192格，全部雾化；旧 verify 只查计数/文件。已改为地面锚定，并加入同机位浪潮开关 A/B 像素门禁、相机-地面几何断言和 visible_pixels；错误画面不能再通过。修改前光影三轮同夹具帧均23.578/23.702/23.659ms、P99 46.913/46.497/46.710ms、世界GPU 23.212/23.266/23.255ms、Horde draw 16.6ms；修改后最终配置 client-1792950624281/1792950875573/1792951133286 帧均14.444/14.456/14.992ms、P99 16.348/16.273/16.919ms、世界GPU 14.089/14.123/14.648ms、draw 7.35/7.34/7.95ms，帧均/P99/世界GPU三项目标达到；普通同夹具帧均3.365ms/P99 6.777ms/世界GPU 1.663ms。修改为：三档模型520/112/24顶点（近景全精度、按块迟滞）、单位历史按行环页稳定映射（查找纹理不再每帧重建、参数16→17个vec4）；期间修复中景列片数按单块9列计算导致多列历史失效的缺陷（修复后历史复制1.47MB/帧、单位GPU 0.47ms）。64/256尸体分别8.0/8.4ms、fallback 0、draw2。未完成：真实事件Debug/MCP功能（新世界无命令、MCP GUI 加载既有命令世界失败）、E4换包重载、普通256尸体、首帧≤100ms（STABILIZE 216–341ms）、中远LOD美术接受；另有一次并行Photon基准干扰，受影响样本已丢弃。证据 build/agent-backups/20261026-runs/ 与 20261026-horde-opt/；详细对照/限制见 benchmod.md 2026-10-26 节，剩余问题只在 horde-issues.md 维护。代码与报告已提交 54f61e9；horde-index 只提交本条进度，并行文档整理的未暂存条目未被夹带。2026-10-26 补充：Debug+MCP 已在有命令隔离世界启动真实事件（id cad5c81e-4f36-4724-a96f-c848a9c4fedd），overlay 与 Debug 求值确认 events=1、submitted=GPU instances（3180→9484）、lightSections 153→476、animationFrames/LOD=4/true、modelBuilds=3、irisMiWords=16/slots=2；夜间发光、近中远三档、650ms 两帧位姿变化与镜头 x=110→60 移动后持续可见均已截图并归档 build/agent-backups/20261026-runtime-event/；区域净空定量、E4、真实事件 256 尸体仍未完成。契约级回归：HordeRegionTest（3格净空/中断进出连续）、HordeSpatialFieldTest、HordeRulesTest、HordeCleanupTest、HordeOptimizationTest 的 IDEA JUnit 运行均退出 0。普通组 256 尸体已补测：client-1792953137469 帧均 1.986ms/P99 4.382ms、static_drawn 256、fallback 0、draws 1。

## 当前能力与源码

| 范围 | 当前职责 | 入口 |
| --- | --- | --- |
| 事件与真实参战 | 服务端推进事件、配额、出生和参战生命周期；虚拟单位不创建 Entity | [事件说明](horde-events.md)、[HordeRuntime](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) |
| 开放多段线路 | 平滑中心线、共用地面采样准备、粗高度脏更新、固定XZ包围组最近点查询；不重新寻路 | [HordeRoute](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java)、[HordeSamplingUtil](../../src/main/java/org/unitego/lobecorp/util/horde/HordeSamplingUtil.java) |
| 同步与客户端 | 完整线路就绪后显示；客户端 tick 检测目标区域，每帧提取绘制块 | [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java)、[HordeClient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) |
| 模型缓存 | header 排队，提取阶段按预算增量烘焙并上传完整资源；默认完整周期4个等距帧、显式三档模型和隐藏面剔除；每顶点每帧6个float，运行门禁未执行 | [HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) |
| GPU 与让行 | motion pass 求位置、让行与朝向，主体读取顶点帧；批次容量不等于总数量上限 | [渲染实现](horde-rendering-implementation.md)、[HordeGpuRenderer](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)、[motion shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh)、[顶点 shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh) |
| 光照 | CPU 准备已加载区段，5 tick 比较亮度；GPU 在让行后位置采样原版光照 | [HordeLightField](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java) |
| 基准 | 普通清道夫 AI、本地实体渲染、10000 实例隔离 GPU 快照场景 | [BenchMod](benchmod.md)、[HordeRenderScenario](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java) |
| 配置 | 服务端事件规则与客户端数量、范围独立；当前默认值以源码为准 | [HordeEventConfig](../../src/main/java/org/unitego/lobecorp/config/server/HordeEventConfig.java)、[HordeVisualConfig](../../src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java) |

当前是单一三维表示与顶点帧动画。旧图片图集、LOD 渐变与主体射线方案的过程页已合并移除；长期契约见玩法和渲染说明，历史采样与证据归档统一见[BenchMod](benchmod.md)。

## 问题与验证入口

本工作区的缺陷、性能候选、待验收项和历史遗留仅在[Horde 问题汇总](horde-issues.md)维护。本页不复制问题状态，也不使用原始 dev 或 Photon 的结果代表本工作验收。

本轮原因分析与实施顺序见[测试前优化建议与问题原因](horde-pretest-optimization.md)；2026-10-24 的 BenchMod Client/Server 实测核对与修正建议见[测试后优化建议与问题原因](horde-posttest-optimization.md)。两页均为分析记录，不改变问题清单的权威性。2026-10-25 的极端负载优化顺序、预算与验收矩阵见[极端情况优化方案](horde-extreme-optimization-plan.md)（首批 EX-01/10/11 已写入，未做实机验收）；供执行型 GPT 逐项落地的任务书见[GPT 执行文档](horde-extreme-execution-plan.md)（2026-10-25 决策：极端目标改为 60 FPS；追加 EX-13…EX-17：分割区域不可进入、4 帧等距动画、尸体默认静态/32-180 格/批渲染/同步、512 宽直线性能夹具）。

## 恢复入口

先核对 `codex/horde` 与当前 Git 暂存、未暂存状态，再读对应系统文档和源码；现有benchmark、路由、渲染、着色器和测试改动不能覆盖；用户2026-10-25已确认Horde范围可随本次任务提交，其他系统及日志/备份不夹带。历史快照和备份仅作证据，不自动恢复旧实现。

MCP 消费本工作区 `lib/minecraft-mcp-0.3.0.jar` 与桥接，Minecraft 端口为 19876；Normify 复用原始仓库 CLI，workspace-root 与 cwd 指向本工作区。配置修正不代表已加载连接重载；文档任务不操作游戏或进程。详细路径和历史构建边界见[工作区目录](workspace-layout.md)与[制品规范](minecraft-mcp-artifacts.md)。
