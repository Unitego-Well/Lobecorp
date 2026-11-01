# 女皇激光与瞬时星束的技能特效

> 2026-10-23 最新 WIP：帧回调锚点、单份输出准备、参数暂存和阶段开始 tick／姿态混合已写入当前源码，见 [未完成记录](photon-workspace.md) 与 [QueenBeamEffects](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java)。当前星束保持既有 0 tick 契约；首次开销、连续跟随和结束衔接尚无本轮游戏／视觉／P99 验收。下表四份可编辑工程目标在本工作区缺失，运行 ID 属历史调用说明，不能据此认定手写资产已存在。

> 2026-10-21 独立工作区当前实现：已按最小依赖接回四个原有定义、阶段生命周期和原样服务端资源；星束仍为当前注册的0 tick，不采用下文历史2 tick。Photon消费端为已见蓄力的瞬时实例补一次输出，严格适配已实现的固定渲染/遮挡及粒子旋转缩放周期字段。真实调用、问题台账和复测边界见[集中验证](photon-static-fixes-20261021.md#最小依赖协调与新验证轮次)。原assets、伤害/时长、实体动画和模型LOD逻辑不修改。以下2026-10-19及之前内容均为历史，不能替代本轮验收。

> 2026-10-19 当前状态：女皇自建 Photon 技能表现与预热桥接已移除；服务端技能业务、实体动画及资源保留，本次没有添加视觉占位。 实际检查与备份见[移除实施记录](photon-removal-result.md)。下文涉及已移除实现的说明仅保留为历史参考。

核对日期：2026-10-19。激光技能使用 laser_spell，星束技能使用从 laser_spell 复制的 star_beam_spell。服务端技能决定阶段和持续时间，技能通过代码动画实例同步阶段，客户端从动画快照呈现动作和特效；没有新增网络包，也不让特效结算伤害。

## 资产

| 效果 | 运行 ID | 可编辑工程 | 对象数 |
|---|---|---|---|
| 激光法术 | lobecorp:the_queen_of_hatred/laser_spell | laser_spell.fxproj（历史路径 `../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser_spell.fxproj`，本工作区不存在） | 9 |
| 瞬时星束法术 | lobecorp:the_queen_of_hatred/star_beam_spell | star_beam_spell.fxproj（历史路径 `../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell.fxproj`，本工作区不存在） | 5 |
| 星束光球 | lobecorp:the_queen_of_hatred/star_beam_spell_ball | star_beam_spell_ball.fxproj（历史路径 `../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell_ball.fxproj`，本工作区不存在） | 7 |
| 星束光束 | lobecorp:the_queen_of_hatred/star_beam_spell_beam | star_beam_spell_beam.fxproj（历史路径 `../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell_beam.fxproj`，本工作区不存在） | 6 |

三个新工程分别有同目录 .fx 与 src/main/resources/fxpacks 下的同名 .fxpack。组合包含 46 个文件、4 份定义；两个子包分别 7/9 个文件，均不包含 fxproj。原 laser_spell、laser、light_ball 的工程、定义和包逐份与本轮备份字节一致，没有修改原模型、ShaderGraph 或纹理。

星束组合层保留一个法阵、光球源、光束源、共用锚点与控制器；删除副本中的另外两个法阵和独立汇聚粒子。顶层位置与缩放为原值的 60%。光球只保留光核、内外辉光、十字星与少量首爆火花；光束只保留内外两柱、少量白线和末端粒子。花瓣模型/生成器、源头尘雾、三色持续星粒、发射尖刺和密集带状层不进入星束副本。

## 阶段由技能控制

[注册时序](../../src/main/java/org/unitego/lobecorp/registry/entity/skill/TheQueenOfHatredSkills.java)是权威来源：

| 阶段 | 激光 | 星束 | 客户端行为 |
|---|---|---|---|
| WINDUP | 20t | 20t | charge_start，展开法阵与光球 |
| ACTIVE | 200t | 2t | laser_start，光球爆发并发射光束；停止独立蓄力粒子 |
| RECOVERY | 10t | 10t | laser_end 与 charge_end 同时收尾 |
| 完成/取消 | 代码动画停止 | 代码动画停止 | 动画快照移除后释放宿主及子实例 |

星束的 2t 只为闪光显示保留持续阶段；伤害仍只在 onActivate 的当前刻命中框中结算一次，没有增加持续伤害。射程 15、完整直径 0.5、方块截断和穿透有效敌人的规则保持。激光射程 20、完整直径 1.5、累计接触、转向和冷却规则保持。

[QueenBeamEffects](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java)为每个代码动画播放 UUID 独占一个 PhotonGeoEffect；启动前只修改此实例控制器，将子调用切为 CONTROLLED，并取消该实例的默认自动入口。资源里的制作曲线仍是原时长，入场和结束通过技能时长重定时；完成入场后保持，收到技能阶段消息才结束，不使用资源内固定 200t 全段自动串联。

控制输入采用现有 PhotonAnimationRuntime 公共接口。光束 length/width 按实际锚点缩放换算，避免星束缩小后射程或命中宽度也被错误缩小。输出阶段持续更新根朝向及方块截断长度；父阶段 HOLD 后，直接更新已存在光束子播放的输入，避免子调用只保留完成时的旧参数快照。没有逐 tick 重播动画。

## 起点与方向

[LaserSkill.origin](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/skill/LaserSkill.java)继续作为服务端命中框、截断射线和指挥家预览的身体中心原点。客户端激光以该中心沿朝向向前 1 格的位置作为首法阵起点；星束首法阵使用现有 StarBeamSkill.origin/QueenSkillUtil.staffOrigin 的法杖局部基准，普通与二阶段各自取原来的估计位置。没有改动伤害起点、射程或技能时长，也没有新增客户端骨骼定位器。

此前错误地减去光球/光束父锚点，会把法阵移到后方。现在读取当前运行树的 circle 相对根位置，用首法阵对齐指定起点，并保留工程内部法阵、光球和光束的相对排布；读取子光束 anchor 与发射器变换计算实际光束起点，剩余可见长度按技能终点沿发射轴投影，近处墙体截断到源后方时长度归零，不反向发射。局部 +Z 旋转到技能朝向；星束激活时冻结基准起点/终点，持续激光在 ACTIVE 跟随瞄准，后摇冻结最后方向和位置。

## 同步与清理

[代码动画定义](../../src/main/java/org/unitego/lobecorp/registry/animation/TheQueenOfHatredAnimationDefinitions.java)包装 `aim`/`aim2`，通过 `effect(...)` 关联 laser_spell 或 star_beam_spell。普通与癫狂阶段共四个定义；[技能动画运行入口](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatredBeamAnimation.java)在前摇创建实例，激活/结束效果时更新 `v.beam_phase`，完成/取消时停止。时长读取技能注册，不复制一套固定计时器。代码动画关闭自动片段结束，动作到末帧后继续保持到技能显式停止。

原 EntitySkillClientEvents 的五个特效监听已删除，两个技能不再触发原生 AIM action。其他技能的 action 控制器保留。现有 LcAnimationStatePayload 同步动画 UUID、定义 ID 与参数；客户端只读取这些快照，不执行服务端 Java 回调。首次安装或晚追踪会按收到的阶段启动；晚追踪的入场/首爆以及多人跨阶段表现尚未验收。

上半身 `up_body` 在代码姿态修饰器中按 `q.head_x_rotation` 俯仰，双臂和法杖随父骨骼一起转动。先绕实体本地 X 轴俯仰，再叠加资源侧身姿态。头部是否追加原生俯仰依据已安装代码动画判断，不依赖特效是否创建成功；普通头部左右转向保留。客户端资源覆盖继续合并代码明确拥有的骨骼绝对结果。女皇专属的纯 aimPose 由两端共享：客户端近距每个渲染帧先采样本地动作曲线，再叠加插值的瞄准俯仰，避免整个 up_body 被服务端每 tick 的姿态覆盖。本地显示时钟不随每份网络快照重新起算；播放版本、暂停或速度变化时校正。其他服务端行为回调仍不在客户端执行。

服务端完整资源副本位于 `data/lobecorp/animation/models/entity/the_queen_of_hatred.geo.json` 和 `data/lobecorp/animation/animations/entity/the_queen_of_hatred.animation.json`；本轮正文与 assets 原文件一致。修改骨架或 aim/aim2 时须同步维护 data 副本，原客户端资产和 Photon 工程未改写。

[客户端 tick 入口](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)更新播放实例，登录、退出、客户端世界卸载清理集合。动画消失、来源实体失效或宿主 generation 变化时立即清理。已经清除或加载失败的动画 UUID 不逐 tick 重建特效；该动画移除后释放此记录，新施放使用新 UUID。两个技能仍不发送旧 QUEEN_LASER/QUEEN_CHANNEL_LASER 粒子。

## 释放瞬间的源码优化

- 蓄力期间为光束源预备一个未发射 FXRuntime，预解析其动画库及 CPU 模型/固定拓扑；释放时消费此实例，再绑定随机值、参数、空间变换和宿主。预备不启动粒子、时间线、音频或绘制。
- 每个源最多一个预备实例；重启可保留待消费实例，真正停止/清空时释放；使用前检查 FX 定义身份、来源 ID、递归路径和深度，资源变化则销毁并正常重建。
- FX 发射入口将已经校验的动画库交给运行态，避免每次发射解码两次。库归单一运行实例，编辑器可变库和正在播放的运行态不进入共享池。
- 持续阶段的锚点与长度共用一次射线结果，减少重复方块截断查询。

这些是源码确认的工作转移和重复计算消除，不是帧时间成绩。GPU 首次编译/上传、首爆与后期的成本仍须在游戏中采样，详见性能说明（历史路径 `photon-performance.md#代码动画接入与释放前准备2026-10-18`，本工作区不存在）。

## 逐帧显示、结束衔接与本地性能采样（2026-10-19）

女皇代码动画接管期间，基础 GeckoLib 控制器继续推进；蓄力按现有 windup 时长以平滑权重混入，RECOVERY 按现有后摇时长混回基础姿态，头部俯仰也按相同权重交接。没有延长技能或伤害阶段；来源失效和取消仍立即清理。远处 Native 控制器降频缓存保持原策略，女皇代码动作也沿用 30/12 FPS 与 action/实体豁免；默认距离起点为 50 格。

加载准备包括本地视觉曲线、模型 CPU 资产、图管线和纹理，以及限量未发射实例。实例只独占移交一次，已播放对象不回收；资源重载、退出和世界卸载清理。控制模式切换直接移交独占动画库，不再为此重新编码完整 NBT。世界当前尺寸的 Bloom 链保持到世界退出或配置/尺寸变化，避免原生 120 帧闲置驱逐导致重复分配。

真实技能的 Debug/Spark、首次/预热最长帧、P99、最低一秒、逐帧样本及未解决问题见释放瞬间实测（历史路径 `laser-release-profile.md`，本工作区不存在）。项目原 FX、模型、纹理、ShaderGraph 未改动；不能用源码改动或平均帧率代替稳定验收。

## 代码检查（2026-10-18，历史）

17 个 Java 文件已通过 IDEA 诊断与修改范围格式化，保留既有未使用 API、借用 Level 和原渲染器注解等警告；新增类及本次主要特效/模型预备路径无错误或警告。最终 IDEA 编译成功，Data exitCode=0。data/资产 JSON 正文一致且可解析；旧技能特效事件与这两个技能的原 AIM 调用已检查。没有启动游戏、重载资源、修改特效工程或执行性能/视觉验收；停止到 locomotion 的衔接、首帧、俯仰方向和 P99/最低帧率均待实机确认。

本轮需重启开发客户端加载新定义和类变化；没有自动重启用户游戏。源码与文档备份（本地未发布定位：`../../backups/queen-code-animation-20261018/README.md`，不作为公共证据入口）。

## 上一轮技能事件绑定检查（历史）

- 七个 Java 经 IDEA 分批检查，无代码错误；两个新类无错误/警告，既有借用 Level 等提示保留。
- IDEA 编译成功；最终 Data exitCode=0，19 个生成文件写入 0/删除 0，生成目录无手工修改。
- 原生工程校验、保存回读及压缩定义往返通过；包内定义与 fxproj 排除已核对，原模板字节一致。
- 本轮未启动战斗或执行技能实机验收，按用户要求由用户验证。

上一轮新增事件订阅要求重启客户端；该事件入口现已被本轮代码动画绑定替换。重启后可通过既有技能/指挥家入口分别施放激光与星束，验证起点、朝向、时长、墙体截断和取消；本轮没有自动重启或关闭用户游戏。

修改前资产与源码/文档快照见本轮备份（本地未发布定位：`../../backups/queen-beam-skill-binding-20261018`，不作为公共证据入口）。
