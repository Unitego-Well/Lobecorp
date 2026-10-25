# Photon 当前能力与恢复入口

核对日期：2026-10-24。适用真实根为 `E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`，分支为 `codex/photon`。本页基于当前工作文件、IDEA 文件读取及现有检查记录；已确认的网格／编辑器／光束续做源码与文档已纳入本地提交 330d297a，尚未推送。配置及两份资源验收测试的既有修改继续保留，提交不代表构建、游戏或完整能力验收通过。

## 当前任务进度

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

2026-10-24 11:25 激光复测完成：bd29ff6 上的真实女皇激光采集 90 秒／52033 帧，三次施放均 STARTED，客户端阶段 0→1→2→移除齐全；第四次输出截图可见女皇、法阵、光束和目标。帧间隔均值／P99／最长为 1.730／8.529／608.366 ms，全帧聚焦最大化、无节流；首发仍有四个 400～608 ms 长间隔，热施放仍有 81／89 ms 尖峰，P06 未解决。动画逐帧字段仍为空，严格同视角 A/B 和稳定缓存收益仍未验收；P11／P14 保留。结果与限制已写入 [测试后报告](photon-optimization-posttest-20261024.md)，备份于 photon-normal-cache-laser-20261024。游戏经 MCP 正常保存退出，桥接均退出 0；未修改生产代码、动画、LOD 或资源。下一步细分首发准备／复制／GPU 首用成本及热施放尖峰；本轮未实施这些优化。

2026-10-24 法线缓存任务：已完成 DS 报告修订、基线归档、实例缓存实现及前后采样记录。MCP／Debug 仅用于测量之外的实例和失效路径确认。报告修正、实例法线／行列式缓存及三项新增数值回归已完成；IDEA 三份代码 0 错误、9 项既有提示，测试 5／5 通过，Debug／MCP 核对实际命中与失效键后已正常退出。有效原实现基线和最终候选两轮均完整保存；复核出现统一约 6.15 ms 帧节奏，前后截图视角也存在差异，稳定性能收益和严格同视角视觉 A/B 尚未充分验收。下一步应先核对实际渲染相机和帧节奏，再受控配对；P11 不关闭，未改动画、LOD、材质、排序或资源内容，详情见 [测试后报告](photon-optimization-posttest-20261024.md)。备份根为既有可视化目录下 photon-normal-cache-20261024。

最近更新：2026-10-25 13:25（北京时间，极端场景优化计划）。计划入口为 photon-extreme-optimization-plan-20261025.md；当前代码仍只含稳定帧快照，后续按 G0～G7 逐步实施。

- 此前详细列表阶段：[待优化详细列表](../issues.md#待优化详细列表)集中列出 P11／P12／P06 的确认热点、P13 的待归因异常和 S01–S23 后续处理；包含代码入口、执行顺序、实现约束与关闭条件。该阶段仅整理文档；后续获授权的法线缓存实现与采样已记录于上方，其他优化建议仍未实施。
- 已完成真实激光复测（2026-10-24 11:32）：Sodium＋BenchMod＋Minecraft MCP＋Debug，90 秒／51432 帧，首发 4～7 秒长帧合计约 2.118 秒、后续施放最长 80／68 ms；路径、数字与优化方案见 [真实激光测试与优化方案](photon-laser-optimization-20261024.md)。生产代码未改。
- 已完成光影激光测试（2026-10-24 12:34）：ComplementaryReimagined r5.9.3 下 7672 帧、空场景 9.320 ms、施放窗口 20～25 ms、首发最长 717 ms；JFR 显示 Iris 36.8%（阴影 31.5%）而 Photon `write` 仅 3.0%，微优化无法覆盖；架构级方案见 [架构报告](photon-laser-architecture-20261024.md)。测试后已恢复 Iris 配置，包文件保留。
- 已完成同代码无光影配对轮（2026-10-24 12:55）：42028 帧、空场景 1.843 ms、施放窗口 6.275／3.944／3.953 ms、首发最长 981.357 ms、GPU 均值 0.642 ms；与光影轮同基线 `6ae3093c`，数据已补入架构报告与 P15。
- 已完成渲染架构与 Mixin 方案报告（2026-10-24）：核对 Photon 原生实例化 `MODEL/MODEL_VAT`、`BeamEmitter` GPU 光束路径与 `IrisCompat.isShadowPass`，给出阴影／Iris 路由、首帧预热等 Mixin 目标；另含参数传输、命名动画与骨骼 GPU 化方案（NamedRuntime/Mixer 约 13%）。只写报告未改代码，见 [架构与 Mixin 报告](photon-render-architecture-mixin-20261024.md)。
- 此前 DS 诊断：BenchMod Photon 两次运行（一次窗口失焦 INVALID、一次 PASSED），JFR 归因、Debug 三处调用路径核对与 Minecraft MCP ping／截图；根因与修正建议见 [测试前文档](photon-optimization-pretest-20261024.md)、[测试后文档](photon-optimization-posttest-20261024.md)。未修改业务代码，无优化后 A/B。
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
| 旧核心移除 | [计划](photon-removal-plan.md)、[结果与备份](photon-removal-result.md) | 2026-10-19 历史，不代表当前恢复代码不存在 |
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
