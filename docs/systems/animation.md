# 实体动画与视觉同步

> 初次核对：2026-10-05。基线按 IDEA 读取的迁移前源码说明；2026-10-10 飞扑更新见对应段落，均不代表游戏运行验收。
> 包迁移按[已批准方案](../java-package-organization-plan.md)完成，链接已按实际源码路径核对；结果见[修改记录](changes.md)。

## 真实接口与职责边界

玩家第一／第三人称使用同一服务端播放快照与 GeckoLib 原生求值，并接入原版动作来源。第三人称保留皮肤外层和原版盔甲，第一人称只提交独立玩家模型的双臂／袖层及随手物品。覆盖／混合、代码策略、淡入淡出及第一人称边界见[玩家动画](player-animation.md)。

既有视觉控制器系统在 GeckoLib 原生 `AnimationController` 上增加过渡、遮罩和姿态混合能力。没有要求使用名为 `LcAnimationController`
的替代控制器；新增代码应以当前接口和 IDEA 解析为准。

既有视觉控制器的动画表现不决定服务端技能时序或伤害。持续实体事实来自同步数据，技能动作来自技能生命周期回调，设置载荷只传递动画过渡配置。相关服务端机制见[实体与 AI](entities-ai.md)、[实体技能](entity-skills.md)。

新的显式服务端运行态从完整模型／动画内容及代码包装采样，只有声明根运动、空间附着或回调时才产生对应实际行为；客户端资源覆盖仅改变视觉。它提供基础 API 与适配，未迁移既有技能，资源路径、内容版本和生命周期见[服务端动画、根运动与空间附着](animation-runtime.md)。

| 职责                 | 核对入口                                                                                                                                                                                                                                             |
|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 播放、停止和设置     | [ILcCustomAnimatable](../../src/main/java/org/unitego/lobecorp/animation/ILcCustomAnimatable.java)                                                                                                                                                   |
| 原生控制器构造       | [LcAnimationControllerBuilder](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerBuilder.java)                                                                                                                                 |
| 遮罩能力             | [LcAnimationControllerMask](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerMask.java)                                                                                                                                       |
| 过渡能力与参数       | [LcAnimationControllerTransitions](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerTransitions.java)、[LcAnimationTransitionSettings](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationTransitionSettings.java) |
| 运行时权重及姿态缓存 | [LcAnimationControllerRuntime](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerRuntime.java)、[LcAnimationPose](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationPose.java)                                     |
| 渲染集成             | [LcAnimationControllerIntegration](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerIntegration.java)                                                                                                                         |
| Mixin 接入           | [AnimationControllerMaskMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/AnimationControllerMaskMixin.java)、[RenderPassInfoMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/client/RenderPassInfoMixin.java)            |
| 过渡设置同步         | [LcCustomAnimationSettingsSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/LcCustomAnimationSettingsSyncPayload.java)                                                                                                                |

`ILcCustomAnimatable` 继承 GeoEntity，播放和停止复用 `triggerAnim`、`stopTriggeredAnim`
。包含设置的版本先应用或同步设置，再调用原生触发接口。客户端通过实体动画管理器查找控制器并应用设置；服务端只向跟踪玩家发送设置载荷。

## 动画层、遮罩与过渡

控制器按注册顺序参与姿态处理。混合类型为 `ADDITIVE`、`OVERRIDE` 和 `MASK`，它们分别用于叠加、覆盖和限定通道的混合；实际骨骼与通道范围由姿态提取及遮罩共同决定。

遮罩接口可以设置启用骨骼、锁定骨骼及删除这些限制。控制器由 Mixin 实现扩展接口，使用 `of` 访问；不要把此能力强制转给没有相应
Mixin 的其他依赖版本控制器。

过渡分为两层：

- 控制器进入和退出时的权重，决定该层对整体姿态的影响。
- 当前动画与新动画之间的姿态权重，决定同一层内部如何切换。

`OVERLAP` 支持交叠混合，`SEQUENTIAL` 按退出再进入处理。`NORMAL` 保留资源 Euler 数值路径及完整圈数；`SHORTEST_PATH` 首次选择最短四元数路径，后续采样保持与上一旋转向量临近的等价分支，避免移动目标跨 180° 时重新选路。设置记录集中描述进入/退出时间、过渡模式、旋转模式与混合类型。

连续分支计算集中在 [LcRotationTransitionUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcRotationTransitionUtil.java)。源姿态和目标姿态仍取当前帧；多轴整圈奇点无法延续同一旋转向量分支时，以局部最短增量重建有界修正，并随权重向 0／1 衰减，端点仍精确返回当前源／目标。只有 `SHORTEST_PATH` 输出选择临近的等价 Euler 表示，防止后续普通层或冻结姿态再次读到表示分支跳变；不改写 `NORMAL` 输入。

每个骨骼只保存固定数量的旋转状态，不积累轨迹列表。相邻采样相对转动超过半圈时，单凭四元数无法推断中间圈数；资源要求的完整转圈应继续使用其 Euler 路径。数学回归入口为 [LcRotationTransitionRegression](../../src/test/java/org/unitego/lobecorp/util/animation/LcRotationTransitionRegression.java)，本轮编译、Data 与回归执行结果以[修改记录](changes.md)为准，本文不把代码落盘当作运行通过。

姿态提取处理位置、旋转和缩放通道，结合骨骼遮罩；覆盖模式还会处理受影响的默认通道。更改模式前必须核对骨骼默认姿态、父子关系和前层作用范围，不能仅根据动画名称推断效果。

## 渲染执行链与缓存清理

`RenderPassInfoMixin` 在 GeoEntityRenderer 存在显式服务端动画帧时优先接入 `LcAnimationBoneAdapter`，应用权威／本地视觉覆盖后的姿态。没有此运行态时，动画对象实现 `ILcCustomAnimatable` 才接入下述既有视觉控制器集成，其余对象保留原路径；服务器完整模型的渲染器需显式使用 `LcServerGeoModel`，详见[新运行态](animation-runtime.md#内容同步与客户端视觉覆盖)。

1. 从渲染状态取得动画管理器，使用管理器 DataTicket 保存本对象的运行容器。
2. 依据渲染年龄和控制器状态更新进入、退出与动画切换权重。
3. 提取、缓存并混合控制器姿态，再将结果应用到骨骼。
4. 控制器停止但权重未归零时继续完成退出；归零后重置运行数据。
5. 重置清除上一姿态、待切换姿态、权重、触发修订及旋转分支状态，并恢复相关暂停状态。

触发修订用于识别同一动画再次触发。缓存属于具体动画管理器和控制器，不是实体服务端技能数据；替换控制器、移除实体或切换资源时，需核对缓存所属对象及依赖本身的销毁行为。

同一控制器的动画切换与层权重各自保存按骨骼隔离的旋转历史。每个渲染年龄冻结上一帧状态，同一时刻重复渲染从同一份旧状态纯计算，不额外推进分支。新动画或触发修订、新权重阶段、模式／叠加方式变化、时间倒退及重置会清理对应历史；动画内部切换结束清理其独立历史，层权重历史仍用于后续移动目标与静态部分权重。

## 远距离姿态采样

`LcAnimationControllerUtil.withDistanceLod(controller)` 显式接入距离采样，只给女皇与清道夫的 5 个控制器启用。原生时间和关键帧事件继续推进，只缓存本层姿态贡献；首次呈现、切换、重新触发及循环边界重采样。距离、采样 FPS、逐实体和逐控制器豁免配置见[动态视觉精细度](visual-quality.md)。

## 初始默认姿态免淡入

2026-10-18 已通过统一工具 [LcAnimationControllerUtil.withoutInitialFadeIn](../../src/main/java/org/unitego/lobecorp/util/animation/LcAnimationControllerUtil.java) 接入女皇与清道夫的 locomotion 默认层。该工具配置控制器的首次呈现资格；每个控制器运行实例独立保存 `presented`，第一次获得有效姿态时直接使用完整层权重，停止／重新进入及后续切换仍使用原 `fadeInTicks`、淡出与过渡模式。`reset` 只重置过渡，不清除已呈现标记；新控制器或新运行容器重新取得首次资格。action 层与眼睛层保留原设置。

已确认原因：项目锁定 GeckoLib 5.5.2，`AnimationControllerMaskMixin` 对 `ILcCustomAnimatable` 的原生时间线已传入 0 过渡；首次淡入实际来自项目集成默认的 3 tick 层权重，单独设置原生 `transitionTicks(0)` 无法解决。直接把 `fadeInTicks` 永久设为 0 还会改变后续进入／切换。原生参数含义参见[GeckoLib 5 官方控制器说明](https://wiki.geckolib.com/docs/geckolib5/concepts/animation/controller/overview/)。

未修改动画 JSON、播放速度、技能时序或停止清理，不增加同步载荷。IDEA／编译／Data 结果见[修改记录](changes.md)；尚未做游戏首帧、停止重入或晚追踪的视觉验收。

## 女皇与清道夫实例

| 实例   | 动画层与表现入口                                                                                                                                                                                                                                                                                                                    |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 女皇   | [TheQueenOfHatred](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatred.java)：移动层 ADDITIVE，眼部 MASK，动作层 OVERRIDE；[TheQueenOfHatredAnim](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatredAnim.java)管理动作名称 |
| 清道夫 | [Sweeper](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/Sweeper.java)：移动层 ADDITIVE，动作层 OVERRIDE；[SweeperAnim](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/SweeperAnim.java)管理动作名称                                                                                      |

移动层由原生状态处理器根据移动和实体状态选择待机、行走、跑动或坐姿。动作层默认停止，使用注册的可触发动作；具体动作的开始、结束和取消由技能回调配合处理。不能用每
tick 重播来维持当前动作。

清道夫的 `playActionAnimation` 和 `stopActionAnimation` 有客户端守卫，技能同步后的客户端生命周期回调调用表现。女皇同名入口没有该守卫，直接复用
GeckoLib 触发接口；运行回调会在服务端及同步客户端执行。两者不能概括为同一种触发路径。女皇与 GeckoLib
触发同步的交互和去重尚需核对，不应再叠加独立生命周期广播。清道夫清理与飞扑动作采用 OVERLAP，其他攻击动作仍采用 SEQUENTIAL。

当前移动层源码直接读取 `walkAnimation.speed()` 参与倍率计算。项目规范要求动态倍率使用渲染部分刻插值并保留速度判定与迟滞；本文不据此宣称现有路径已完成该优化，相关插值效果尚未验收。

### 清道夫飞扑三阶段

2026-10-10 按已确认的 5 tick 前摇与低弧猛扑方案调整，仅复用既有技能生命周期同步：

- `WINDUP_START` 触发 `LEAP`：原蓄力段压到 0.25 秒，结束时保持下蹲，与 5 tick 前摇对齐。
- `ACTIVATE` 触发 `LEAP_FLIGHT`：0.2 秒内从下蹲过渡到腾空姿态，随后保持；这段资源长度不决定实际飞行时间。
- 原版碰撞确认实际落地后，`END` 触发 `LEAP2` 衔接落地动作。取消仍停止动作动画，不依赖动画回调结算落地伤害。

三个飞扑动作使用 `OVERLAP`，使新时间线在姿态过渡期间继续推进；其他攻击动作的过渡方式保留。
[SweeperRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/ordeal/indigo/renderer/SweeperRenderer.java) 保留普通头部转向；2026-10-11 按用户要求移除飞扑专属的 root 俯仰、动作权重过渡和头骨俯仰补偿，飞扑姿态由上述三阶段动作资源表现。前摇／后摇包含在 `LEAP` 实体状态中，因此不能单凭该状态判断腾空。没有新增表现状态或网络通道。

已确认的源码原因：旧 `leap` 长 1 秒，下蹲／起身关键段在约 0.5／0.75 秒，与 0.25 秒前摇不匹配；资源中 6.1 像素的 root 上移也会叠加真实实体位移。现已拆分动作并去掉腾空保持和落地开头的虚假上移。维护时应分别核对技能边沿、动画时间线和碰撞落地，不能用固定资源时间代替实际落地。

本次仅修改 [运行资产](../../src/main/resources/assets/lobecorp/geckolib/animations/entity/sweeper_a.animation.json) 的飞扑段；`resources/entity/sweeper` 下的源素材及 `.bbmodel` 保留。后续从旧素材重导出时，必须同步保留 `leap`、`leap_flight`、`leap2` 分段与新时序，避免覆盖运行资产。本次编译、Data 和编辑器结果见[修改记录](changes.md)；未进行游戏视觉验收。

## 状态、设置与粒子的同步分工

- 同步实体状态列表、女皇整数阶段（1／2／3，渲染票据 `QUEEN_PHASE`）和清道夫变体用于持续姿态及模型选择；女皇二、三阶段暂沿用现有癫狂纹理与表现。
- [EntitySkillSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/EntitySkillSyncPayload.java)
  传递施放阶段回调，客户端对应运行实例触发动作和短时表现。
- 过渡设置载荷只同步控制器配置，播放与停止仍复用 GeckoLib 原生接口。
- [LcDataTickets](../../src/main/java/org/unitego/lobecorp/registry/animation/client/LcDataTickets.java)提供渲染状态数据；模型读取对应
  ticket，而不是自行复制一套实体业务状态。
- GeckoLib 动画切换与定位器 Mixin 对 Photon
  效果及锚点有集成；关联入口为 [PhotonGeoEffects](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffects.java)
  。切换、停止、取消和实体移除时必须核对效果是否清理，不假定动画结束一定清理所有独立粒子。

持续事实优先使用既有同步状态；瞬时触发复用既有生命周期与事件入口。新增网络同步前先检查已有能力，避免同一表现被状态、实体事件与自定义载荷重复驱动。

## 模型、动画及资源关系

模型入口为 [TheQueenOfHatredModel](../../src/main/java/org/unitego/lobecorp/client/entity/abnormalitie/the_queen_of_hatred/model/TheQueenOfHatredModel.java)
与 [SweeperModel](../../src/main/java/org/unitego/lobecorp/client/entity/ordeal/indigo/model/SweeperModel.java)。女皇模型使用阶段
ticket 选择纹理；清道夫模型保留当前变体映射和尸体处理。

当前运行资源入口：

- [女皇动画](../../src/main/resources/assets/lobecorp/geckolib/animations/entity/the_queen_of_hatred.animation.json)。
- [清道夫 A 动画](../../src/main/resources/assets/lobecorp/geckolib/animations/entity/sweeper_a.animation.json)。
- [女皇模型](../../src/main/resources/assets/lobecorp/geckolib/models/entity/the_queen_of_hatred.geo.json)。
- [清道夫 A 模型](../../src/main/resources/assets/lobecorp/geckolib/models/entity/sweeper_a.geo.json)。

控制器触发名称、动画枚举、资源动画名称、骨骼名、定位器和粒子资源引用需要一起检查。存在 A/B/C/D
等变体不代表每个变体都有独立模型或动画；当前模型映射以源码为准。

2026-10-05 的初次核对只检查资源入口及源码引用，没有格式化或编辑 JSON，也没有逐条核对资源内动画长度、触发名称和定位器。生成输出保持只读，资源生成问题应修改生成源。

## 修改前核对与尚未验收

修改前读取[动画规范](../../.codex/skills/lobecorp-entity-animation/SKILL.md)
和[视觉同步规范](../../.codex/skills/lobecorp-entity-visual-sync/SKILL.md)，再用 IDEA 确认当前 GeckoLib 类型、Mixin
目标、控制器引用和调用侧。历史类名与文档不能替代当前解析结果。

核对动画层顺序、遮罩骨骼、默认姿态、暂停标记、重触发、移动倍率、服务端权威、晚加入同步，以及完成、取消、卸载后的姿态与粒子清理。不要改变技能前摇、后摇或碰撞时间来补偿纯视觉过渡。

2026-10-05 的初次核对检查了表列接口、姿态集成、Mixin、实体层配置、模型及同步入口。未做多人时序、资源重载、实体移除后的依赖缓存、全部资源名称匹配或游戏视觉验收；当时没有执行编译、Data
或运行游戏。维护记录见[系统维护记录](changes.md)。
