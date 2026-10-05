# 实体动画与视觉同步

> 核对日期：2026-10-05。本文按 IDEA 读取的迁移前源码说明实际实现，不代表游戏运行验收。
> 包迁移按[已批准方案](../java-package-organization-plan.md)完成，链接已按实际源码路径核对；结果见[修改记录](changes.md)。

## 真实接口与职责边界

当前系统在 GeckoLib 原生 `AnimationController` 上增加过渡、遮罩和姿态混合能力。没有要求使用名为 `LcAnimationController`
的替代控制器；新增代码应以当前接口和 IDEA 解析为准。

动画表现不决定服务端技能时序或伤害。持续实体事实来自同步数据，技能动作来自技能生命周期回调，设置载荷只传递动画过渡配置。相关服务端机制见[实体与 AI](entities-ai.md)、[实体技能](entity-skills.md)。

| 职责                 | 核对入口                                                                                                                                                                                                                                             |
|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 播放、停止和设置     | [LcCustomAnimatable](../../src/main/java/org/unitego/lobecorp/animation/LcCustomAnimatable.java)                                                                                                                                                     |
| 原生控制器构造       | [LcAnimationControllerBuilder](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerBuilder.java)                                                                                                                                 |
| 遮罩能力             | [LcAnimationControllerMask](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerMask.java)                                                                                                                                       |
| 过渡能力与参数       | [LcAnimationControllerTransitions](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerTransitions.java)、[LcAnimationTransitionSettings](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationTransitionSettings.java) |
| 运行时权重及姿态缓存 | [LcAnimationControllerRuntime](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerRuntime.java)、[LcAnimationPose](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationPose.java)                                     |
| 渲染集成             | [LcAnimationControllerIntegration](../../src/main/java/org/unitego/lobecorp/animation/LcAnimationControllerIntegration.java)                                                                                                                         |
| Mixin 接入           | [AnimationControllerMaskMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/AnimationControllerMaskMixin.java)、[RenderPassInfoMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/client/RenderPassInfoMixin.java)            |
| 过渡设置同步         | [LcCustomAnimationSettingsSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/LcCustomAnimationSettingsSyncPayload.java)                                                                                                                |

`LcCustomAnimatable` 继承 GeoEntity，播放和停止复用 `triggerAnim`、`stopTriggeredAnim`
。包含设置的版本先应用或同步设置，再调用原生触发接口。客户端通过实体动画管理器查找控制器并应用设置；服务端只向跟踪玩家发送设置载荷。

## 动画层、遮罩与过渡

控制器按注册顺序参与姿态处理。混合类型为 `ADDITIVE`、`OVERRIDE` 和 `MASK`，它们分别用于叠加、覆盖和限定通道的混合；实际骨骼与通道范围由姿态提取及遮罩共同决定。

遮罩接口可以设置启用骨骼、锁定骨骼及删除这些限制。控制器由 Mixin 实现扩展接口，使用 `of` 访问；不要把此能力强制转给没有相应
Mixin 的其他依赖版本控制器。

过渡分为两层：

- 控制器进入和退出时的权重，决定该层对整体姿态的影响。
- 当前动画与新动画之间的姿态权重，决定同一层内部如何切换。

`OVERLAP` 支持交叠混合，`SEQUENTIAL` 按退出再进入处理。旋转模式包括普通插值与 `SHORTEST_PATH`
；后者通过四元数路径处理旋转，不应简单线性相加角度。设置记录集中描述进入/退出时间、过渡模式、旋转模式与混合类型。

姿态提取处理位置、旋转和缩放通道，结合骨骼遮罩；覆盖模式还会处理受影响的默认通道。更改模式前必须核对骨骼默认姿态、父子关系和前层作用范围，不能仅根据动画名称推断效果。

## 渲染执行链与缓存清理

`RenderPassInfoMixin` 只在动画对象实现 `LcCustomAnimatable` 时接入自定义集成，其余对象保留原路径。

1. 从渲染状态取得动画管理器，使用管理器 DataTicket 保存本对象的运行容器。
2. 依据渲染年龄和控制器状态更新进入、退出与动画切换权重。
3. 提取、缓存并混合控制器姿态，再将结果应用到骨骼。
4. 控制器停止但权重未归零时继续完成退出；归零后重置运行数据。
5. 重置清除上一姿态、待切换姿态、权重和触发修订状态，并恢复相关暂停状态。

触发修订用于识别同一动画再次触发。缓存属于具体动画管理器和控制器，不是实体服务端技能数据；替换控制器、移除实体或切换资源时，需核对缓存所属对象及依赖本身的销毁行为。

## 女皇与清道夫实例

| 实例   | 动画层与表现入口                                                                                                                                                                                                                                                                                                                    |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 女皇   | [TheQueenOfHatred](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatred.java)：移动层 ADDITIVE，眼部 MASK，动作层 OVERRIDE；[TheQueenOfHatredAnim](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatredAnim.java)管理动作名称 |
| 清道夫 | [Sweeper](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/Sweeper.java)：移动层 ADDITIVE，动作层 OVERRIDE；[SweeperAnim](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/SweeperAnim.java)管理动作名称                                                                                      |

移动层由原生状态处理器根据移动和实体状态选择待机、行走、跑动或坐姿。动作层默认停止，使用注册的可触发动作；具体动作的开始、结束和取消由技能回调配合处理。不能用每
tick 重播来维持当前动作。

清道夫的 `playActionAnimation` 和 `stopActionAnimation` 有客户端守卫，技能同步后的客户端生命周期回调调用表现。女皇同名入口没有该守卫，直接复用
GeckoLib 触发接口；运行回调会在服务端及同步客户端执行。两者不能概括为同一种触发路径。女皇与 GeckoLib
触发同步的交互和去重尚需核对，不应再叠加独立生命周期广播。清道夫清理动作与其他动作还选择不同过渡模式，修改前保留该差异。

当前移动层源码直接读取 `walkAnimation.speed()` 参与倍率计算。项目规范要求动态倍率使用渲染部分刻插值并保留速度判定与迟滞；本文不据此宣称现有路径已完成该优化，相关插值效果尚未验收。

## 状态、设置与粒子的同步分工

- 同步实体状态列表、女皇阶段和清道夫变体用于持续姿态及模型选择。
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

本轮只核对资源入口及源码引用，没有格式化或编辑 JSON，也没有逐条核对资源内动画长度、触发名称和定位器。生成输出保持只读，资源生成问题应修改生成源。

## 修改前核对与尚未验收

修改前读取[动画规范](../../.codex/skills/lobecorp-entity-animation/SKILL.md)
和[视觉同步规范](../../.codex/skills/lobecorp-entity-visual-sync/SKILL.md)，再用 IDEA 确认当前 GeckoLib 类型、Mixin
目标、控制器引用和调用侧。历史类名与文档不能替代当前解析结果。

核对动画层顺序、遮罩骨骼、默认姿态、暂停标记、重触发、移动倍率、服务端权威、晚加入同步，以及完成、取消、卸载后的姿态与粒子清理。不要改变技能前摇、后摇或碰撞时间来补偿纯视觉过渡。

本轮核对了表列接口、姿态集成、Mixin、实体层配置、模型及同步入口。未做多人时序、资源重载、实体移除后的依赖缓存、全部资源名称匹配或游戏视觉验收；没有执行编译、Data
或运行游戏。维护记录见[系统维护记录](changes.md)。
