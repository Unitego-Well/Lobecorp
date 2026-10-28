# Horde 当前文档入口

2026-10-28 路径实测修正（当前）：原16项测试3失败已保留；弱压力渐入端把完整左右需求缩成≤.25格的“平手”，加上两侧前导范围不同，导致远侧误锁，原核心外单位也被推入核心。现改左右共用farDelta提前量、按未乘pressure的几何并集需求选侧；已锁侧保持。运动目标改用连续需求并集U，较小需求不会平均削弱外侧需求；个体有效半径.8–1.35、跨列补偿.45保留分散外缘。Python -B最终17项全部通过（55.862秒，退出0）：单圆speed.25/.75中心占据比均0、全核心.004989/0；三圆中心0、全核心.015272，中位横移6.644/6.664/8.591格，外缘跨度2.822/3.040/3.499格；原门槛不放宽。新增弱渐入选侧回归。仅本轮GLSL/路径测试与文档修改，Java不变；数值通过不等于普通/Iris实机验收。报告见.agent/reports/20261028-horde-soft-pressure.md，累计源码补丁同目录20261028-horde-core-distribution.patch。

以下为前轮历史记录，旧版“当前/未运行”的说法由上文覆盖。

2026-10-28 中心分流修正（覆盖此前软压力版）：07被用户判为视觉失败；HUD圆数/字数为0已定位为提取时清零、GUI先于世界绘制读取的统计时序，不能据此判漏上传。主路线origin=center+(250,0,0)，三牛局部(-250,0/±3.473)、minimumY0与夹具一致，NoAI/NoGravity不设置noPhysics；不改筛选。当前中心改用独立个体有效半径0.9–1.25、强度1.2–1.45及0.97–1缓慢呼吸，重叠混合D/(1+M)，跨列补偿0.65保留散度，纵横压力独立渐变。提前量按增强需求、0.7横速及1.8倍渐入时间计算。仅HordeDebugState/HordeGpuRenderer修正HUD统计；GPU四字状态、0.7积分、3倍sway、CPU区域职责与服务端不变。新增实际积分路径密度/核心占据/位移分位数检查，未执行。详见 .agent/reports/20261028-horde-soft-pressure.md 的当前交付节；补丁同目录20261028-horde-core-distribution.patch。

以下条目按时间保留；旧版“当前”结论以本条覆盖。

2026-10-28 软度调整：用户允许短暂穿入核心和越出路线边缘；当前以渐变压力、稳定个体强度/相位及带自由先验的饱和混合替代圈界投影/hard capacity/hard max。保留0.7积分、锁侧、3倍sway和四字状态，测试改为柔和密度相关不变量。本轮仅静态检查、未运行测试/构建/游戏；普通05/Iris06采集PASSED不代表软度通过。详见[渲染实现](horde-rendering-implementation.md)及 .agent/reports/20261028-horde-soft-pressure.md。

2026-10-28 当前改为每EventData四字双RGBA8避让状态，替代下面静态草稿。整route活动行窗按半开边界迁移，拆组前每事件更新一次；锁侧不随移动圆穿中线翻转，活动/退场的sway+avoidance总横向状态限速0.7。Motion8保留，CPU不做逐单位更新，3倍sway保留。禁止测试前6项标量数值回归已通过；Java窗口测试、GPU/游戏/性能均待父代理。三份新文件有IDEA自动暂存，未提交。详见[渲染实现](horde-rendering-implementation.md)及 .agent/reports/20261028-horde-gpu-state.md。

2026-10-28 路径反例修正（覆盖下文旧幅度说明）：band 仅用于前后引导，不再计入横向安全半径；移除外侧 activation。圆只生成远离自身中心的候选，单遍扫描收集左右包络/导数及朝向圆的近侧容量，优先随机侧、不可容纳完整幅度时尝试另一侧；双侧均不可用时不新增分割横移。原 radius=3.636、band=9.2、across=14 反例偏移为 0。needsPoint 将坐标计算限制到最多 5 个分量，heading 不再额外采样地面。10 项无游戏数值测试退出 0，含 231120 个普通单圆样本；静态无新增问题。详见 .agent/reports/20261028-horde-path-correction.md；最新补丁/清单在 build/agent-backups/20261028-horde-path-correction/。此结果不是游戏或硬件 GLSL 验收。

2026-10-28 路径实现：sway 频率 3 倍、幅度不变；GPU 同侧最大包络提前绕圆，静态局部直线分割速度基准/上界为常规速度×0.7，保留 16 格上限。多圆交接仅保证 C0，动态圆、复杂通道、fade 与超范围净空不作绝对保证；I49 保留。新增 tools/horde_path_contract_test.py，待父代理统一运行和验收；本次仅静态检查。详见[渲染实现](horde-rendering-implementation.md)。

2026-10-28 实机：普通 `client-1793183928425` 与 Iris `client-1793184984851` 的七段低频采集均 PASSED，各23图、GPU错误0。普通测量均值/P99为6.660/20.877ms、>100ms为0；Iris为16.924/41.900ms、>100ms为11。实际碰撞资格分支已检查，连续视觉、六方向/64格边界及真实member仍未完整验收；本轮具体边界见[问题汇总](horde-issues.md)。

2026-10-28 集成检查：IDEA 静态诊断无新增错误，`BenchMod Compile` 与 `Data` 均退出 0；生成的中英文资源均含 `visual.single_card_distance`。`HordeSplitTransitionTest`、`HordeCardProjectionTest`、`HordeOptimizationTest`、`HordeRenderFrameTest` 均退出 0。旧 `HordeMeshReductionTest` 两项失败：仍访问已在本轮前移除的简化模型槽 `models[2]`，本次未改其生产代码或测试。未启动游戏，硬件 GLSL、Iris 画面、连续过渡与性能均未验收。

核对日期：2026-10-27。本工作区只负责 Horde；其他系统保留为[共同基线](README.md)。本文依据工作区当前源码与已有报告整理，包含未提交实现；源码存在、历史运行通过与本轮验收分别判断。

## 本轮任务进度

2026-10-28 客户端分割已实现并完成静态诊断：local/target/member 统一排除旁观、`noPhysics` 与无有效碰撞体积，滞留复核资格；新圆从零、退场精确至零、复入沿用当前状态，GPU 引导幅度与 CPU 粒子轮廓同步消退。新增 4 项无游戏回归待父代理运行；本次不构建、不运行、不提交，原空纹理 3 行 WIP 保留。详见[渲染实现](horde-rendering-implementation.md#2026-10-28-客户端碰撞资格与连续分割)与[修改记录](changes.md#2026-10-28-客户端碰撞资格与连续分割)。

2026-10-28 方向片固定提交 12 顶点 / 18 索引，原版/Iris 共用 GPU 选面；新增独立 `singleCardDistance=64`（`-1` 关闭），保留 `cardDistance=32`。六方向源帧（24 顶点）和图集在资源阶段预烘焙上传并缓存；CPU 沿用区域批量提交和现有批次参数，不新增逐实例工作或 CPU 距离/方向选择。GPU 复用 `OriginTime.xyz=event.origin-camera`，按每实例最终位置、heading 和相机高度选面；严格超过阈值只光栅化一片，其余两片退化为零面积，仍处理 12 个输出槽。原有完整模型/片批次 LOD 与数量范围不变。五份源码静态诊断 0 错误，11 项既有警告；未构建、Data、测试或运行，由父代理统一验证。详见[片实现与限制](horde-rendering-implementation.md#2026-10-28-方向片剔除与超远单片)。

2026-10-27 当前阶段：用户确认后已完成一次低频截图视觉复测（`client-1793109801528`），七段各3张，加warmup共23张，间隔至少100 tick，运行中不读图/比图。控制与完整性PASSED、环境有效、失焦0、GPU绘制失败0；测量P99 17.800ms、最长92.287ms、>100ms为0帧，较旧重型取证的周期尖峰明显减少，但启动/暖机仍有尖峰，全部CPU/GPU问题未据此宣告解决。Spark已保存，自有游戏/Debug/GPU及探针已释放。结束后仅目视已保存代表图；稀疏截图不能证明逐帧连续性或I49精确位置。片比例修复和原WIP保留；不重跑、不重启I41优化。

当前有效 Horde 对话：`01a17d1c-da93-7422-bb8a-1916d4a764d1`。旧对话 `01a1669e-f661-71f0-a9ab-eec2d7301cd2` 保留，但从本工作区后续检索、跟踪和投递中排除，不读取或唤醒。早期游戏时段已结束；当前仅恢复测试代码重写，新的游戏运行需用户确认；不自动同步原始分支。

2026-10-27 重写验收动态阶段（历史记录，后续状态见当前阶段）：实机确认无割圆首次绘制在 motion pass 内延迟分配纹理失败，已在 `HordeSplitState.publish()` 提前建立零纹理；仅 3 行生产代码修复。修复版 BenchMod Compile 成功、15 项既有回归通过（粒子 3、圆区 4、优化 8）；修复版诊断轮浪潮可见且该异常未复现，但窗口最大化失败，不能计正式有效样本。两次诊断 Spark 已上传、对应 PID62036/53904 与 Debug 均已退出。七段严格门禁视觉夹具与沿用既有负载的性能包装入口正在准备，六轮性能与 I49 实际偏差仍未完成；不把准备报告作为本段放行后的释放结论。

2026-10-27 重写验收准备（历史静态阶段，当时实机未放行）：复用 `codex/horde` 与 `a1de8439`，通过 IDEA 核对编辑器及现有 WIP，13 份原字节备份位于 `build/agent-backups/20261027-horde-acceptance-preflight/`，未保存差异均为 false。I49 现已确认还存在分割缺失、低速漂移系数及地形阻尼/数值精度差异，尚无实机超过 1 格的证据。当前夹具配置为 500×500、速度 0.75、10 监守者、玩家站程 400、测量 2400 tick；世界/seed、制品、窗口与光影须在开测时实取冻结。七类矩阵、粒子量化及普通/光影各三轮仍未执行，等待本对话明确放行；未启动游戏、Debug 或构建，未申请 GPU 时段。新报告与矩阵准备放本工作区忽略的 `.agent/`。

2026-10-27 按进度的潮与确定性分割（已重写，单场景实机 PASSED）：用户要求先备份、整体重写，顺序为「先按进度的潮、再分割」，并补大量调试信息。备份为 Git 快照 `refs/heads/backup-20261027-pre-rewrite`（`0c4ffb08`）与 `build/agent-backups/20261027-pre-rewrite/`。已删除 `HordeUnitState`、`HordeUnitStats`、`HordeRegionState`、`HordeSpatialIndex` 及其着色器；新增 `HordeTide`、`HordeSplitState`、`HordeDebugState` 与 `horde_split.glsl`，重写 `horde_motion.glsl`/`horde_motion.fsh`，粒子采样改 CPU 纯函数。位置只由波头站程、行列与事件时钟决定，分割只做连续让步，光照缺失回退露天亮度不再隐藏单位，因此「历史失效回落锚点」与「`-1` 隐藏」两条机制不存在。检查：IDEA 编译 0 错误、`BenchMod Compile` 退出 0、17 项无游戏测试通过、BenchMod `lobecorp.horde-render` 实机 PASSED（`valid=true`、焦点 losses=0、25,643 实例、分割圆 64、粒子 5.45/刻、`visible_pixels` 547,611、draw GPU 5.252 ms、帧 15.318/P99 33.154 ms、failures=0、spark `https://spark.lucko.me/8ZU75qbZIC`）。未完成：多目标/夹缝/高低楼层/多人/跨维度矩阵与着色器行为定量。契约见[视觉过渡与战斗区域](horde-visual-transition.md)，实现见[渲染实现](horde-rendering-implementation.md#2026-10-27-按进度的潮与确定性分割当前实现)，问题见 I48–I50。

2026-10-26 视觉过渡与战斗区域（已实现，待实机验收）：用户先要求“先计划”，随后授权“开始执行”，明确本轮只做视觉效果与连续过渡，不含 I41 性能优化，且不得以减少数量、模型面数或显示范围换取。契约见[视觉过渡与战斗区域](horde-visual-transition.md)，实现见[渲染实现](horde-rendering-implementation.md#2026-10-26-圆形核心事件局部编码与真实脚点粒子)，修改记录见[changes.md](changes.md#2026-10-26-第七轮圆形核心真实脚点粒子与独立战斗方区)。要点：每个生物一个圆核心（玩家/成员/目标，Y 真实，30 tick 滞留 + GPU 40 tick 退场）；GPU 区域状态改事件局部 floatBits 并统一切圆判定，胶囊只作引导轮廓；单位改持久速度 + 限加速度积分，取消位置投影与隐藏；新增 `HordeParticleSamples` 从 GPU 取相机 32 格内 256 个真实脚点（9 字/点、9216B 异步回读、8 邻居掩码与站程、1 tick/100 ms 新鲜度），粒子改为边界碎屑+灰尘同点、刀光独立批次（1–4 tick、4–10 位置、每处 1–3 粒、+1.7 格、0.7–1.2 倍）；服务端新增运行期方形战斗区与 32 格/10 tick 分层快照。检查：`BenchMod Compile` 退出 0，8 套无游戏测试 61 项通过，`Data` 退出 0，Java/GLSL 静态诊断 0 错误。未执行硬件 GLSL、游戏内外观与轨迹、粒子画面、多人/跨维度调度及性能对比。保留原有构建号、I41、DOC-HORDE-01 与提问规则等他人进度，不夹带提交。

2026-10-26 20:33 I41 光影性能优化（已完成本轮 A/B，负结果）：用户选择继续优化并授权同夹具游戏 A/B。只读核对确认：虚拟浪潮跳过阴影阶段；`CommandEncoder` 无 compute、`TextureFormat` 仅 RGBA8/RED8/RED8I，byte-packed「1 word = 1 像素」使 14 字/顶点与单位 7 次重算无法用 MRT 合并。阶段口径已纠正：须看 `gpu_frame_sum_ns`，`gpu_sample_ns` 只是轮换单采样；第 09 轮完整均值为 expand 4.262、units 2.743、base 2.336、spatial 1.701 ms。本轮只改 `horde_iris_vertices.fsh` 局部冗余（片 UV 按 word 拆支、法线归一化移入 word8），不碰共享 `HordeIrisAnimation` 帧缓存（尸体路径共享且无零法线回退）。同会话两轮实测：expand 4.442 vs 4.437 ms、世界 GPU 19.159 vs 19.201 ms，**无可测收益**，已还原为原字节，生产代码无残留改动。同时发现夹具跨轮不稳定（regions 2 vs 归档轮 90、base 6.09 vs 2.34 ms），当前夹具实际瓶颈为 base 6.09 + expand 4.44 ms；达标需结构性减顶点/填充（远端片降面或降实例），已待确认杠杆。基线轮归档 `build/agent-backups/20261026-i41-expand-opt/baseline-reverted/`；夹具配置已恢复。详见 [BenchMod](benchmod.md) 第六轮节与 [horde-issues](horde-issues.md) I41。保留原有构建号、实体契约、DOC-HORDE-01 与提问规则进度等他人改动，不夹带提交。

2026-10-26 提问规则更新：已将根规范与工作流程统一为优先使用当前软件可用的提问 API，没有时直接在对话中提问；保留一次一个问题、等待回答与未回答不视为确认。仅修改规则与本工作记录，保留既有工作；检查范围为 IDEA 回读与文档诊断，不执行构建、Data、测试、同步、重载或游戏操作。原字节备份位于 `build/agent-backups/question-api-20261026-2010/`。

2026-10-26 HORDE-RETEST-05（追加于进度开头，避免覆盖并行文档整理）：按用户要求整体重写 GPU 避让（单位 GPU 速度状态、玩家竖直胶囊、格点邻居分离、分割区域代价梯度；提前分流、加大侧向速度、绕行后横向阻尼会合；生物刷新/尖峰不再整批重播种，改为每帧限幅平滑推出），不再在 `location()`/`advanceUnit` 上打补丁。修复片颜色红蓝互换根因（NativeImage ARGB vs ABGR），片新增 glowmask 发光图集、cutout+背面剔除（按用户要求“透明渲染、非半透明”），浪潮夹具速度改 0.75 格/刻。多缺口分割修复：目标按视距+路线过滤、缺口滞留 30 tick、走廊取最强单区域、硬推力取最深、null 防御；生物分割范围 `VISUAL_GAP_SCALE` 3→1.5；`HordeEvent.covers` 改为严格波带 `[tail,head]`，消除“潮未到就提前生成清道夫/粒子”。监守者轮改为真实 `HordeEvent`（500 宽/6000 tick，事件自身生成真实清道夫、与全 AI 监守者真实战斗，玩家旁观者+服务端锁位；监守者 Pose.STANDING/清 IS_EMERGING/长 DIG_COOLDOWN），09/10 仍为合成夹具。新增/改造基准：清道夫30 有AI/无AI、清道夫尸体30、随机生物尸体100、500×500 浪潮、同上+10 监守者；12 轮（6 场景×光影/非光影，每轮重启客户端）全部 PASSED、焦点 losses=0、Spark 链接齐全，数据表与证据见 benchmod.md 第五轮与 build/agent-backups/20261026-matrix12/。最终数据：09/10 合成 19.196/22.989/25.037、6.534/9.609/12.381 ms（世界GPU 19.082/7.486）；11/12 真实事件 20.814/26.035/30.115、9.336/12.512/15.478 ms（世界GPU 20.652/4.143）。未达标：horde 光影轮（09/11）帧均 19.2/20.8ms、世界GPU 19.1/20.7ms（目标 16.7/20/15ms），非光影（10/12）达标。无效尝试（焦点被外部抢占、Spark 上传失败、warden 生成、NPE、并发编译、covers 前轮次）均保留证据未计入。交接文档见 docs/systems/horde-handoff.md。本次代码与文档提交（未推送）。

2026-10-27 DOC-HORDE-01文档整理已完成静态收尾：当前99份自有Markdown合并为97份，完整分类索引逐项覆盖且均从根README可达；事件监听规范全量迁入events.md，MCP旧接入过程迁入制品说明历史节，原两页备份后删除。现存Skill、FAQ、记忆、技术/规则/独特历史证据、DS新增交接与视觉文档及第三方来源/许可证保留；不恢复缺失源码、技能或截图，处置边界仅维护于horde-issues。统计1445处本地内联链接/183处锚点引用，缺失文件/锚点/孤立/旧页入链均0；IDEA七页诊断0错误，保留17项既有表弱警告和1项既有R2引用警告，不能将静态导航通过等同于运行验收。原生补丁误改的entity-contracts CRLF24及末尾空行已用新IDEA哈希保护接口恢复；Sol实施、Astra独立复核，现行MCP入口已纠正。备份：build/agent-backups/20261027-doc-horde-resume/、20261027-doc-horde-root-resume/，旧45份Base64/SHA证据保留。提交仅本次文档增量，horde-index只提交本段；DS代码/配置/资源/生成输出、15项原暂存与其他DS进度/WIP保持，不推送、同步、构建、Data、业务测试或操作游戏。此前本聊天测试报告与DS提示词已交付；性能/视觉未解决事项继续按当前horde-handoff、horde-visual-transition与唯一问题清单恢复，不自行增加业务目标。旧对话仅完成已有收尾，不创建、归档或轮换聊天/工作树。

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

2026-10-26 第二轮（HORDE-RETEST-02，追加于进度末尾）：按“先测试、再修改、再测试”完成。基线：光影 client-1792975839616 帧均/P99 13.889/17.863ms、世界GPU 13.305ms、draw 7.239ms、展开 658,284；普通 client-1792976051992 帧 3.402/5.690ms、世界GPU 2.026ms、draw 1.507ms。修改：①新增 BenchWindow 每tick还原/重聚焦并计数，三个场景的 setup 与 STABILIZE/WARMUP/MEASURE tick 调用；Horde 在 MEASURE 重置计数，verify 增加 focused/iconified/focus_losses==0 硬门禁，环境 JSON 记录焦点字段；②中景 4盒112→5盒136、远景 1盒24→2盒48，近景520与4帧动画不变，新增按档实例计数。复测：光影三轮 client-1792976399030/1792976864064/1792977119469 帧均 14.365/15.059/15.026ms、P99 18.064/18.624/17.971ms、世界GPU 13.834/14.398/14.534ms、draw 7.818/8.457/8.193ms、展开 898k、复制 50.3MB，全部 PASSED、10000 实例、焦点 0/0，目标帧均≤16.7/P99≤20/世界GPU≤15 仍满足；普通有效两轮 client-1792977359699/1792978532864 帧 6.168/6.184ms（165Hz 帧间隔地板）、世界GPU 2.026/1.897ms、draw 1.359/1.225ms，另有 client-1792977705014 与 1792978004840 因焦点被抢分别被本夹具门禁/BenchMod 环境拒绝，保留证据不计数据。截图：基准 artifacts/screenshots 与游戏内铺满取景 build/agent-backups/20261026-appearance-final/in-game-fill/fill-D.png、fill-F.png、fill-night.png。规则“测试保持焦点、失焦自动恢复并记录、每轮必须截屏交付”已写入原始项目 AGENTS.md/workflow.md 并同步工作区。未完成：E4、区域净空定量、普通组第三轮稳定样本、LOD 切换录像；详细数据/限制见 benchmod.md 2026-10-26 第二轮节。

2026-10-26 第三轮（HORDE-RETEST-03，追加于进度末尾）：按“先测试、再修改、再测试”复核罐子/红色元素并补回。核对 sweeper_a.geo.json：`can`（6 立方体灰罐体，y8–34、z2.5–14.1）与子骨骼 `biomass`（9×19×9，UV[0,29]，扩散与 glowmask 均为红，平均 RGB≈145,69,74）是清道夫元素，双臂薄刀面 UV[0,65] 也为红；旧代理把罐子并入躯干、远档合并红刀面，红色丢失。修改 HordeMeshReduction：新增 PART_CAN（can/jar/pot/biomass），中景 5 盒改为腿脚/躯干/双臂(合并)/头/罐子（136 顶点不变），远景仍 2 盒 48；bestFace 在法线匹配面中按 UV 中心红色优势选面，修复 NativeImage 提前关闭导致的模型准备失败。运行：client-1792980328267 horde PASSED（帧 15.403/17.653/18.837ms、世界GPU 14.620ms、draw 8.855、展开 897,237、中景4741/远景5258）但整轮因外部抢焦点 INCONCLUSIVE；client-1792980557725 被本夹具焦点门禁拒绝(losses=1)；client-1792980845712/1792981136050 PASSED 但 GPU 被并行负载拖慢（世界GPU 33.229/25.472ms），不计达标。同顶点档位 520/136/48 的干净三轮仍为上一轮 14.365/15.059/15.026ms、世界GPU 13.834/14.398/14.534ms；新构建尚未取得干净三轮。视觉证据 build/agent-backups/20261026-appearance-can/can-A3.png、can-B3.png（整体红旧版）；修正后灰身体+灰罐体+局部红见 build/agent-backups/20261026-appearance-spark/artifacts/screenshots/horde-render.png（client-1792983163376 PASSED、Spark 链接 https://spark.lucko.me/1kvmGBN9y2、世界GPU 23.013ms 受并行负载影响不计达标）。Spark 在浪潮测试开始时开启、覆盖整个场景（timeout 600 而非 30 秒），测量后停止并在尸体阶段等待上传链接、requireSaved 后才允许关闭客户端；Sodium 保持加载。规则已记入 benchmod.md 第三轮节。

2026-10-26 第四轮（HORDE-RETEST-04，进行中）：按用户要求实现远档片/20帧/距离降帧机制并验证。已完成：`animationFrames` 默认20（近/中/远/片共用，四档均 frames=20）；新增第4档片模型（1盒6方向面片=24顶点，四档 520/136/48/24）；新增配置 `cardDistance`（-1不使用）与 `frameDistance`（-1不降低、0不动）；`horde_motion.fsh` 按到相机绝对距离在GPU降帧（<d全帧、d..2d半、2d..3d四分之一、>3d静止），`HordeParameters` 增第18个vec4 FrameLod（288B），新增 lod_card 计数。验证 client-1792986006389 PASSED：片档72块/4673实例，展开785,071（低于2盒远档898k），帧13.079/14.291/14.915ms、世界GPU12.702ms、焦点0；截图/配置在 build/agent-backups/20261026-card-lod/。未完成：真正的6方向离屏视图烘焙（当前为6方向面片盒）、10项新测试矩阵（清道夫30 AI/无AI、尸体30、随机尸体100、500×500无上限、+10监守者，各光影/非光影且每项重启）；详见 benchmod.md 第四轮节与 horde-issues I39。

2026-10-26 交接：完整计划、已实现/未完成、运行命令、测试矩阵与下一步顺序已写入 [horde-handoff.md](horde-handoff.md)。新对话从该文档继续；当前避让“绕过了但未完全绕过”，10 项测试矩阵尚未实现。

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
