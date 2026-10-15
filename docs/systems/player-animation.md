# 玩家动画

核对日期：2026-10-12。本文说明玩家姿态接入；实际检查与运行证据见[修改记录](changes.md)。

## 范围与入口

第三人称接入玩家、皮肤外层与原版盔甲，保留原版 AvatarRenderer、皮肤、持物及 layer 提交流程。第一人称仅在当前动画策略显式声明时，从原版相机绘制真实玩家模型的双臂与袖子，物品跟随最终手姿，不绘制身体、头部、腿部、盔甲及其他身体 layer。其他模组只提供显式接入通道，没有自动适配或兼容性保证。

- [LcAnimationDefinition](../../src/main/java/org/unitego/lobecorp/animation/definition/LcAnimationDefinition.java) 的 `playerAnimation(policy)` 显式启用本次播放的玩家姿态。
- [LcPlayerAnimationPolicy](../../src/main/java/org/unitego/lobecorp/animation/player/LcPlayerAnimationPolicy.java) 声明来源权重、骨骼遮罩、TRS 通道遮罩、动画权重、优先级、过渡设置及第一人称手臂接管开关。
- [LcAnimationInstance](../../src/main/java/org/unitego/lobecorp/animation/runtime/LcAnimationInstance.java) 的 `setPlayerPolicy` 修改本次策略，可由原有 `at/onTick` 驱动。
- [LcPlayerAnimationRegistration](../../src/main/java/org/unitego/lobecorp/registry/animation/client/LcPlayerAnimationRegistration.java) 复用唯一客户端 render-state 注册事件；[LcPlayerAnimationRuntime](../../src/main/java/org/unitego/lobecorp/client/animation/player/LcPlayerAnimationRuntime.java) 在状态提取阶段冻结本帧输入。

未声明玩家接入的资源播放不会接管玩家姿态。第一人称默认执行完整原版手臂与持物流程；只有本帧存在 `firstPersonArms=true` 且策略 `weight>0` 的动画层时才启用独立双臂路径。无动画、未声明或权重为 0 时均沿用原版布局。已启用玩家动画时，默认完全覆盖且不混入原版或外部姿态。只有明确声明的来源参与混合。

## 代码策略与原版来源

`fullOverride()` 默认权重为 1，空 `sources` 表示不混合，空 `bones/channels` 表示允许全部骨骼／TRS 通道，`firstPersonArms` 默认关闭。通过 `withSources/withBones/withChannels/withWeight/withPriority/withTransitions/withFirstPersonArms` 派生不可变策略，所有派生方法保留未修改的字段，权重必须在 0–1 内。需要本次动画显示第一人称双臂时显式调用 `withFirstPersonArms(true)`；来源混合设置不会隐式开启它。

[LcPlayerAnimationSources](../../src/main/java/org/unitego/lobecorp/animation/player/LcPlayerAnimationSources.java) 集中提供以下来源 ID：

| 来源             | 对应动作                        |
|------------------|---------------------------------|
| VANILLA_IDLE     | 原版手臂待机 bob                |
| VANILLA_LOOK     | 头部视线                        |
| VANILLA_MOVEMENT | 步行与游泳                      |
| VANILLA_POSTURE  | 飞行／游泳头部调整、骑乘与蹲伏  |
| VANILLA_ATTACK   | 原版挥击                        |
| VANILLA_ITEM     | 原版 ArmPose 持物／使用物品姿态 |
| NEO_ARM_POSE     | NeoForge 扩展 ArmPose           |

原版 `setupAnim` 调用链只执行一次，在明确语义边界记录本次调用的有序 TRS 增量。各 GeckoLib 动画层先独立按来源、部位和通道混入这些增量，再按优先级、策略权重和各自的淡化进度合成。不会从最终模型姿态反推来源，也不会重跑 `setupAnim` 或扩展 ArmPose 回调；禁用其姿态贡献不等于取消回调副作用。

这些增量来自本次实际原版调用上下文。待机来源使用锁定版本 26.1.2 的原生 bob 数学增量，独立于望远镜等持物条件，不额外调用可能被扩展的方法；不自动归类其他模组对 bob 的修改。对依赖其他阶段输入的动作或第三方回调，不保证等价于在任意自定义姿态上重新执行回调。需要独立语义的兼容来源通过公开来源接口提供。

仅混入待机的声明示例：

```java
var policy = LcPlayerAnimationPolicy.fullOverride()
        .withFirstPersonArms(true)
        .withSources(Map.of(LcPlayerAnimationSources.VANILLA_IDLE, 1.0))
        .withTransitions(transitions);
```

这会排除持物手臂姿态，物品仍使用真实 render state 正常绘制，并跟随最终手臂。物品层自身的刺击／使用物品 PoseStack 变换与骨骼来源不同，本接口不把它们自动拆成骨骼层。

## 原生求值与模型映射

[LcPlayerAnimationSampler](../../src/main/java/org/unitego/lobecorp/client/animation/player/LcPlayerAnimationSampler.java) 以同步快照为唯一播放事实，使用 GeckoLib 5.5.2 原生 loader、AnimationTimeline 和 AnimationProcessor 求值。暂停、变速与 seek 由权威快照决定；晚追踪直接定位当前时间，不回放历史关键帧事件。没有为同一播放再调用原生触发同步。

实例变量通过现有 LcMolangParser 隔离，标准查询使用快照值或真实 Player 上下文；自定义查询必须有明确提供器。未知查询报错，不静默替换为零。资源 JSON 修改随完整内容同步，本地同 ID GeckoLib assets 可覆盖视觉模型／动画，不反写服务端位置或行为。

[LcPlayerPoseBridge](../../src/main/java/org/unitego/lobecorp/client/animation/player/LcPlayerPoseBridge.java) 根据动画骨架相对绑定姿态的变形映射原版模型，处理父子层级与坐标轴。默认部位名称为 `root/head/body/right_arm/left_arm/right_leg/left_leg`。模型局部姿态保存完整平移、旋转和缩放；宽／细手臂以及各实际盔甲模型使用自身初始姿态。皮肤外层继续继承父部位。

同一 render state 多次调用模型设置时复用冻结输入，不推进动画时钟、重复执行逻辑时间线或累计偏移。资源代际变化释放旧模型历史；世界卸载、连接退出与实体失效清理相应缓存。

NORMAL 在标准零绑定旋转骨架保留原始 Euler 圈数；复杂层级通过连续采样维持等价角度分支，首次观察时不能恢复未观察的历史圈数。负缩放保留矩阵分解的镜像补偿，不用原始 Euler 覆盖该补偿；TRS 的 shear 限制沿用通用骨架能力。

## 第一人称双臂与持物

[LcPlayerFirstPersonRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/player/renderer/LcPlayerFirstPersonRenderer.java) 使用独立 AvatarRenderer，宽／细手臂各持有自己的 PlayerModel，通过现有 AddLayers 注册入口初始化。`open` 提取真实玩家状态后读取冻结的 `LcPlayerPoseFrame.layers`，只有存在显式开启第一人称手臂且策略权重大于 0 的当前层时，才调用 `setupAnim` 并压入渲染作用域；否则返回 null。启用时继续复用同一同步快照、GeckoLib 求值、来源混合、骨骼／通道遮罩与淡入淡出，不新增第一人称动画时钟或独立触发通道。

模型按真实身体相对相机的位置和身体朝向提交，保留原版相机，不增加相机跟骨骼或身体的偏移。第一人称入口的原版屏幕持物延迟旋转不再叠加。只提交左右手臂子树与原版皮肤袖子；隐藏其余模型部件不影响动画中的父级变形对手臂的映射。独立模型姿态保留到原版延迟提交刷新完成，不在作用域退出时恢复模型，避免提交时引用已经重置的姿态。

[ItemInHandRendererMixin](../../src/main/java/org/unitego/lobecorp/mixin/minecraft/client/ItemInHandRendererMixin.java) 保留原版 `renderHandsWithItems` 外层流程和逐手 `RenderHandEvent`。仅作用域活动时允许双手进入提交、跳过屏幕持物延迟旋转并替换逐手提交；没有作用域时保留原版手部选择、旋转与手臂持物渲染。具体手臂仍经过 `RenderArmEvent`。单手替换点位于 `isScoping` 调用前，保留方法 HEAD 上 Iris 等现有渲染通道过滤，并沿用原版提交刷新。启用路径中望远镜瞄准时隐藏手臂与持物；睡眠、旁观及 F1 等条件仍由原版外层决定是否进入手部渲染。

普通物品调用原版 `ItemInHandLayer`，采用第三人称物品显示变换和第三人称使用效果，握持位置跟随最终手臂。吃喝等原版第一人称屏幕动作不会自动进入本路径。地图通过 [ItemInHandRendererAccessor](../../src/main/java/org/unitego/lobecorp/mixin/minecraft/client/ItemInHandRendererAccessor.java) 调用原版 `renderMap` 提交真实地图内容，再放到手掌锚点；不替换为普通物品贴图或原版居中的双手屏幕布局。

此路径只复用已知原版和 NeoForge 入口。其他模组自定义玩家模型、独立手部管线或特殊物品效果需通过公开通道接入，不保证自动兼容；保留事件入口也不等于已完成所有监听器的运行验证。第一人称盔甲和身体 layer 不在当前范围。

当前模型提取持物使用当前库存，原版逐手事件及 Iris 单手过滤可能仍使用原版换装缓存；切换过渡帧存在两者取物不同的静态风险。该风险尚未运行复现，相关光影表现也未验收，不能将保留 HEAD 过滤等同于换装过程中状态始终一致。

### 无动画时双臂仍显示的问题

用户报告没有当前动画时仍出现身体空间的双臂，期望恢复原版第一人称布局。源码确认原入口只检查渲染器、相机实体和视角，随后无条件建立独立双臂作用域，未检查动画是否声明显示。修复把接管条件放在 `setupAnim` 和作用域创建之前，并由当前冻结动画层的策略决定；渲染器已注册、持有物品或存在附着登记都不能作为启用条件。

维护时保留无作用域下 Mixin 的完整原版回退。软停止期间当前声明层仍在冻结视觉尾部中，允许完成淡出；尾部到期移除后恢复原版路径。不能仅以服务端逻辑实例已停止为由提前切断淡出，也不能用历史动画或长期缓存维持双臂接管。代码修复与运行验收分别以修改记录的实际结果为准。

## 代码姿态修饰所有权

服务端 `modifyPose` 回调使用 ServerLevel 上下文，不在客户端执行 Java 回调：

- `modifyPose(ownedBones, modifier)` 显式声明拥有的骨骼，客户端应用这些骨骼的绝对局部 TRS／Euler 结果。
- 输出缺少已声明骨骼时按 identity 处理，可以明确归零或删除增量。
- 无范围的 `modifyPose(modifier)` 把返回姿态包含的全部骨骼作为绝对结果。
- 不通过数值差异推断增量语义；显式写入与资源相同的值仍能覆盖客户端视觉资源。

若只希望修饰少数骨骼，使用带所有权集合的重载，避免无范围输出覆盖其他客户端动画通道。

## 过渡、同步与生命周期

淡入淡出复用 LcAnimationTransitionSettings，时长单位为 tick。默认 fullOverride 不指定时长，调用方通过代码声明；示例使用 8 tick。稳定播放不混合原版与退出过渡返回原版是不同阶段。

原版→自定义、自定义替换／策略变更及自定义→原版均保留各层当前已显示的贡献作为过渡起点。返回原版时目标持续随原版状态变化。多个实例分别处理淡入淡出后按显式优先级合成，骨骼和 TRS 遮罩控制作用范围，不用某个实例的进度替其他实例淡化。

过渡中再次修订或停止时，把本层当前输入／输出及基底保留比例压为有界快照；保持当下显示值和后续低层基底响应，不累积递归历史或回放旧 SLERP 曲线。首次观察已软停止的播放时，以同步的冻结终止姿态和真实剩余进度构造淡出。

`SHORTEST_PATH` 在同一阶段内通过 [LcRotationTransitionUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcRotationTransitionUtil.java) 延续相对旋转分支，避免运动目标跨 180° 时逐帧重新选短弧而突然反向。Direct 的策略权重与外层淡入／替换／淡出分别保存每骨骼的不可变采样结果；SEQUENTIAL 两半段切换时重置外层状态，播放修订或停止阶段变化时也不沿用旧外层分支。当前原版基底继续参与计算，不能用固定旋转偏移替代动态混合。四元数输出延续邻近的等价 ZYX 分支，供后续层及冻结贡献读取；`NORMAL` 仍按原始 Euler 值插值并保留圈数。

`LcPlayerPoseContribution.evaluate` 返回姿态和下一份贡献，不修改输入历史；`LcPlayerPoseBridge` 只在新的冻结帧提交一次。冻结旧输出与重复模型渲染不会额外推进连续状态。状态随实例层、实际模型历史和资源代一同释放，不建立跨播放的旋转缓存；相邻采样已经丢失的圈数及资源／代码主动跳变不由此推断或修正。本轮执行结果见[修改记录](changes.md)。

协议 14 扩展现有状态／移除载荷，携带策略、修订号、阶段开始时刻、停止时刻、终止原因和代码修饰结果。当前协议 16 在同一策略编解码内增加 `firstPersonArms` 布尔值，仍复用完整内容同步、自身玩家与新追踪快照，不新增第一人称触发载荷。

软停止立即结束服务端逻辑、释放附着并执行一次停止回调，只保留冻结终止快照供视觉淡出。停止追踪、来源失效、世界卸载等硬清理立即释放视觉状态。客户端尾部不运行逻辑回调或历史动作。

## 兼容接入

通过 [LcPlayerPoseSources](../../src/main/java/org/unitego/lobecorp/registry/animation/client/LcPlayerPoseSources.java) 登记 `Identifier → ILcPlayerPoseSource`，再由策略显式请求该 ID。来源只在状态提取阶段采样，返回按原版部位命名的局部增量；位移单位为格，旋转使用原版模型轴。

未请求的来源不采样。第三方自定义模型可通过公开姿态桥接接口接入；未知模型或其他模组最终写入的姿态不会自动归类为已知原版来源。本轮不引入新的动画库依赖。

## 开发日志开关

[LcAnimationDebugUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcAnimationDebugUtil.java) 的 `public static volatile boolean ENABLED` 默认关闭，仅在非生产环境生效。用户确认采用开发内存开关，通过 IDEA Debug Evaluate 设置 `LcAnimationDebugUtil.ENABLED = true` 开启，设为 `false` 关闭；表达式需导入该类，或在该类的调试帧中直接使用 `ENABLED`。重启恢复关闭，不保存配置，不改变动画启用状态或同步协议；独立客户端与服务器需分别设置各自 JVM 的开关。

日志前缀为 `[LC Animation Debug]`。服务端记录启动回调完成、策略实际变化和首次停止；客户端记录首次安装、修订／停止变化、实际移除、视觉尾部到期及非空世界缓存清理。玩家模型只在首次接入、修订或停止边沿记录当前层结果、模型实例标识、淡化进度及右臂／头部姿态；该结果仍可被后续更高优先级的层改变。原生采样失败记录资源、时间、修订和原异常后继续原样抛出。

稳定 tick、重复 render state 不输出日志；复用现有快照与模型历史判定边沿，没有额外去重缓存。中途开启不会补发已发生的边沿，检查完整链路时先开启再播放。服务端日志直接读取已存字段，不调用 `snapshotParameters` 或额外执行查询／时间线回调。

## 示例与验证边界

```text
/lobecorp animation player play @s override
/lobecorp animation player play @s idle
/lobecorp animation player play @s dynamic 0.5 12 12 sequential shortest_path
/lobecorp animation player stop @s
```

命令要求管理员权限，显式玩家选择器支持单人或多人，命令来源不必是玩家。override、idle 和 dynamic 示例均显式开启第一人称双臂。默认运行 override；idle 仅混原版待机；dynamic 在 1.5 秒仅切换来源为 idle，保留当前速度、过渡设置与第一人称声明；重复执行可观察打断，stop 退出。片段长 3 秒，只修改视觉姿态，不启用根运动、不发放物品。完整参数及 pause/resume/speed 见[动画调试命令](animation-runtime.md#动画调试命令)。

玩家最终模型的背部／双手可作为[通用空间附着](attachments.md)的客户端视觉提供器，第三人称未播放自定义玩家动画时也能使用原版最终姿态。第一人称最终手节点仅在上述显式双臂路径启用时发布，附着登记本身不会启用该路径；客户端独立绑定仍可登记及推进。第一人称只绘制可见手节点附着，第三人称使用原版层入口。

资源位于 `data/lobecorp/animation/models/example/player_pose.geo.json` 与 `animation/animations/example/player_pose.animation.json`，使用原版皮肤绘制，不提供独立方块白模或替换玩家纹理。

IDEA 诊断、编译、Data 与用户要求的 MCP + Debug 验证结果须以本轮修改记录为准。尚未完成的画面、多人、第一人称或第三方兼容场景不得据静态检查宣称通过。

### 显式第一人称门控修复验收（2026-10-12）

本轮单人 Client Debug 实测无动画时 `frames=0`、`open=null`，并实际查看[原版第一人称回退](../../run/client/screenshots/player-first-person-gated-vanilla-20261012.png)。同一暂停玩家实例 `cf73d0d1-2ed2-491f-abb9-62f7b2e77918` 在 `firstPersonArms=false` 时 `open=null`，改为 true 后取得 scope，作用域内 `active=true`、关闭后为 false；已实际查看[显式动画双臂](../../run/client/screenshots/player-first-person-gated-active-20261012.png)。

随后使用新代码的 Client Debug 会话，从服务端直接停止玩家实例 `519f03d8-dd39-4afe-8ea4-51d121fa36b9` 与物品实例，等待淡出到期后客户端实测 `frames=0`、`pending=0`、`openNull=true`、`active=false`。该检查确认结束后恢复原版路径；本轮未取得淡出中间权重，不把这次到期检查记作完整淡出过程证据。物品循环旋转的修复后数值见[动画运行时](animation-runtime.md#循环边界修复后验证2026-10-12)。

最终 7 个相关 Java 文件 IDEA 复查无新增诊断，项目编译成功，Data 退出码为 0，25 个生成文件的集合及逐文件文本均保持不变。本轮一次 Client Debug 在 18:11:44 出现 PID 36000 的 native `hs_err`，位置涉及 Render thread 的 `DirectMethodHandle`，根因尚未查明；随后使用原配置重启完成旋转与停止回退检查，不代表该 native 崩溃已解决。

### 门控前历史证据

以下各轮是第一人称显式门控修复之前的历史证据。无自定义策略时仍显示双臂的旧截图记录了旧行为，不能作为当前原版回退的验收结果；本轮门控、未声明策略及淡出结束回退的检查见上方独立验收记录。

2026-10-12 前轮在单人 Client Debug 中确认覆盖姿态、同实例切换 vanilla_idle、淡入／软停止淡出及尾部清理；当时仅有原生采样、同步策略和最终模型数值证据。前轮截图调用问题与 Sodium 注入方法的 NoSuchMethodError 见[系统修改记录](changes.md)，未据此修改第三方模组。

同日补充开发日志后，新的 Client Debug 会话确认默认关闭及再次关闭均静默，开启后记录播放、1.5 秒策略修订、完成／主动停止与尾部清理。MCP 成功保存并实际查看[玩家覆盖姿态](../../run/client/screenshots/player-animation-override-20261012.png)与[胸甲同步姿态](../../run/client/screenshots/player-animation-chest-20261012.png)；冻结 1 秒、淡入完成时，主模型与独立胸甲模型右臂均为 X=1.7453293、Z=-0.2617994 弧度（100°／-15°）。胸甲临时替换此前为空的胸部槽，结束后已确认恢复为空；停止后客户端帧及模型历史均为 0，日志开关关闭。该会话未复现前轮崩溃，仍不能推定其根因已解决。

截至开发日志轮，仅补验默认皮肤、当前持物和原版胸甲；当时头盔／护腿／靴子各槽、多人与晚加入、第三方模型、组合压力场景及第一人称未验收。暂停 Debug 产生了服务端追帧与 spark 统计超时警告，不将其作为正常帧率或性能证据。

第一人称接入轮的 5 个 Java 文件 IDEA 诊断为 0，项目编译成功，Data 退出码为 0；25 个生成文件逐文件内容保持不变。新的 Client Debug 与 MCP 实际查看[无自定义策略的双臂、袖子与当前刷怪蛋](../../run/client/screenshots/player-first-person-idle-20261012.png)，画面没有身体或盔甲。

冻结 override 在 1 秒且淡入完成时，第一人称模型右臂 X=1.7453293、Z=-0.2617994 弧度，左臂 Z=0.17453292 弧度。原示例右臂正向旋转 100° 后位于相机后方，平视时离开视野；[俯视覆盖姿态](../../run/client/screenshots/player-first-person-override-down-20261012.png)只见左臂，不能作为该动作右臂在第一人称可见的证据。

正常 dynamic 播放期间未中途暂停，日志确认 1.5 秒切换 idle 策略、3 秒 FINISHED 及随后客户端帧／模型历史均清零；模型首次进入、策略修订和完成时分别观察到 fadeIn=0.8125、fadeIn=0.125、fadeOut=0.15625。另一次暂停后的 stop 因包到达时已超过 8 tick，只确认恢复原版姿态，不作为淡出过程证据。

临时副手地图没有 map_id；[第一人称地图](../../run/client/screenshots/player-first-person-map-20261012.png)只确认原版背景与边框可绘制，未验证已有地图数据、装饰或标记内容。当前会话日志未出现 ERROR／FATAL；暂停仍产生服务端追帧与 spark 统计超时警告，不能作为性能证据。本轮使用 SLIM 皮肤；第一人称望远镜、睡眠／旁观／F1、其他使用物品、WIDE 皮肤、多人与第三方接入等场景尚未完成运行验收。

[第三人称正面回归](../../run/client/screenshots/player-first-person-third-person-20261012.png)确认完整头部、身体、双腿与当前持物仍可绘制。结束后实测副手恢复为 0 个 minecraft:air，主手仍为 1 个 lobecorp:sweeper_spawn_egg；日志开关关闭，模型历史为 0，第一人称 scope 非活动。相机恢复 FIRST_PERSON、yaw=-71.60007、pitch=-33.6，MCP 确认位置与旋转。临时断点全部移除，仅保留原有两条禁用的用户异常断点，Client Debug 保持运行。完整检查见本轮[修改记录](changes.md)。

### 综合复验（同日追加）

本轮未修改代码。IDEA 再次编译成功，Data 退出码为 0，25 个生成文件的集合及逐文件内容保持不变；14 个相关 Java 文件复查为 0 个错误、6 条既有未使用警告。

新 Client Debug 会话中，dynamic 实例 6e154f55… 全程未中途暂停；日志确认 1.5 秒来源切为 vanilla_idle、3 秒 FINISHED，模型完成淡出先后观察到 fadeOut=0.125 与 0.96875，随后实际检查客户端帧与模型历史均为 0。首次模型日志的 fadeIn=1，本轮该次播放不能作为淡入中间帧证据。

[综合复验基线](../../run/client/screenshots/player-comprehensive-baseline-20261012.png)实际显示 SLIM 双臂、袖层、主手屏障和副手砂岩。当前新基线为创造模式、主手 1 个 barrier、副手 1 个 sandstone、全部盔甲槽为空，位置为 (62.20273619533319, 56, 182.02224929062757)。副手临时换为 glass 后，实测稳定时绘制左手与原版副手缓存均为 glass；换装不一致条件断点未命中，没有运行复现上述静态风险，也不能据此排除切换瞬间的问题。副手随后已实测恢复为 1 个 sandstone。

本轮起初移除暂停断点，改用 `suspendPolicy=NONE` 的日志点；实际命中的 FP_VERIFY 样本为 mainArm=RIGHT、actualOff=1 sandstone、eventOff=1 sandstone、camera=FIRST_PERSON、invisible=false、scopesBefore=0，无 breakpointErrors，该采样阶段 Debug 始终保持 RUNNING。本次 temporary 日志点未自动移除，已显式清理；该阶段结束时仅保留原有两条禁用的用户异常断点。

随后用户确认已手动设置 IDEA 不抢占窗口焦点，并授权继续使用暂停断点，才恢复暂停调试。MCP 未修改对应全局设置。override 实例 5663f715-dd33-4742-9918-52206bd07aad 在服务端 seek 至 1 秒并暂停播放；第一人称入口实际检查为模型历史 1、客户端帧 1，右臂 X=1.7453293、Z=-0.2617994 弧度，左臂 Z=0.17453292 弧度，并实际查看[综合复验覆盖姿态](../../run/client/screenshots/player-comprehensive-override-20261012.png)。本阶段没有修改 Java 代码。

接着设置 hideGui=true、发送 stop，移除临时断点并恢复运行；[F1 画面](../../run/client/screenshots/player-comprehensive-f1-20261012.png)确认 UI、手臂与持物均不显示。运行时实际原始字段为 hideGui=true、HISTORIES=1、FRAMES=0、PENDING=0，说明该时刻 F1 停止后的有界模型历史仍留存 1 份，不能记作模型历史已清零。

随后恢复 hideGui=false，并切换至 THIRD_PERSON_FRONT、pitch=0；[综合复验第三人称](../../run/client/screenshots/player-comprehensive-third-person-20261012.png)实际显示完整头部、身体、双腿及双手持物。恢复渲染后实际检查 HISTORIES=0、FRAMES=0、PENDING=0、scopes=0，确认 F1 期间留存的模型历史已释放；主手仍为 1 个 barrier、副手为 1 个 sandstone，四个盔甲槽均为 0 个 air。

结束前实际恢复 FIRST_PERSON、yaw=-24.650059、pitch=3.150028，并关闭日志开关，返回值确认 ENABLED=false。移除全部代理临时断点后继续运行；MCP 确认位置约为 (62.2, 56, 182)、旋转约为 (-24.7, 3.2)、创造模式、生命值 20、饥饿值 20。IDEA 状态为 running，断点列表仅保留两条原有且禁用的用户异常断点。WIDE 皮肤、其他物品使用动作、多人及着色器等场景仍未完成运行验收。
