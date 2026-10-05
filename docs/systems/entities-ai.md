# 实体与 AI

> 核对日期：2026-10-05。本文按 IDEA 读取的迁移前源码说明实际实现，不代表游戏运行验收。
> 包迁移按[已批准方案](../java-package-organization-plan.md)完成，链接已按实际源码路径核对；结果见[修改记录](changes.md)。

## 职责与入口

世界实体按异常、考验及投射物分类。实体保留引擎回调、同步字段、共享移动和碰撞机制；AI 类负责决策及每个实体自己的 AI
计时。技能状态机、命中结算与客户端姿态分别见[技能](entity-skills.md)、[命中框](hitbox.md)、[动画](animation.md)。

| 职责                | 核对入口                                                                                                                                                                                                                                                                                         |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 女皇实体与每实例 AI | [TheQueenOfHatred](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatred.java)、[TheQueenOfHatredAi](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatredAi.java)                           |
| 清道夫实体与 AI     | [Sweeper](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/Sweeper.java)、[SweeperAi](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/SweeperAi.java)                                                                                                     |
| Brain 构造          | [BrainUtil](../../src/main/java/org/unitego/lobecorp/util/entity/ai/BrainUtil.java)、[ProviderBuilder](../../src/main/java/org/unitego/lobecorp/world/entity/ai/memory/ProviderBuilder.java)                                                                                                     |
| 记忆与传感器注册    | [LcMemoryModuleTypes](../../src/main/java/org/unitego/lobecorp/registry/entity/ai/LcMemoryModuleTypes.java)、[LcSensorTypes](../../src/main/java/org/unitego/lobecorp/registry/entity/ai/LcSensorTypes.java)                                                                                     |
| 实体事件与离开世界  | [LivingEntityEvents](../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java)                                                                                                                                                                                                    |
| 尸体与净化          | [EntityCorpse](../../src/main/java/org/unitego/lobecorp/world/entity/EntityCorpse.java)、[EntityPurificationUtil](../../src/main/java/org/unitego/lobecorp/util/entity/EntityPurificationUtil.java)                                                                                              |
| 实体状态与属性      | [EntityStateHolder](../../src/main/java/org/unitego/lobecorp/world/entity/state/EntityStateHolder.java)、[EntityState](../../src/main/java/org/unitego/lobecorp/world/entity/state/EntityState.java)、[LcAttributes](../../src/main/java/org/unitego/lobecorp/registry/entity/LcAttributes.java) |
| 状态效果清理        | [StunMobEffect](../../src/main/java/org/unitego/lobecorp/world/effect/StunMobEffect.java)、[MobEffectEvents](../../src/main/java/org/unitego/lobecorp/events/MobEffectEvents.java)                                                                                                               |

## Brain、目标和移动的执行链

女皇的服务端 `customServerAiStep` 先更新阶段，随后推进 Brain、活动选择及 AI 自身逻辑；执行父类回调后，再恢复技能锁定朝向和坐姿边缘朝向。Brain
使用 CORE、IDLE、FIGHT 活动，包含观察、行走、攻击目标、寻路失败时间及受伤记忆。IDLE 搜索可见的有效敌人或闲逛；FIGHT 检查目标并选择技能。

闲置计时、坐姿资格、坐姿阶段、边缘接近和技能选择属于女皇 AI。同步实体状态、尺寸变化、技能转向限制和 `travel`
钩子属于实体。坐姿、淡出或移动锁定时的运动限制不能只通过停掉 Brain 实现。

清道夫先处理生物质恢复；指挥家工作生效时跳过 Brain 及活动选择。技能推进由实体事件入口独立处理，因此修改 AI
接管时必须同时核对技能与导航，不应把停止 AI 等同于取消当前技能。

复用行为与传感器：

- [WalkToEntity](../../src/main/java/org/unitego/lobecorp/world/entity/ai/behavior/WalkToEntity.java)
  通过原版观察、行走目标记忆驱动移动；其生物目标版本清除已经死亡的目标。
- [AttackEntity](../../src/main/java/org/unitego/lobecorp/world/entity/ai/behavior/AttackEntity.java)
  检查攻击目标、距离、可见性和攻击冷却。结果回调收到是否命中的布尔值；不能假定该回调只在命中时运行。
- [NearestEntitySensor](../../src/main/java/org/unitego/lobecorp/world/entity/ai/sensing/NearestEntitySensor.java)
  按区域、过滤器、距离及视线选择单个目标。
- [NearbyEntitiesSensor](../../src/main/java/org/unitego/lobecorp/world/entity/ai/sensing/NearbyEntitiesSensor.java)
  生成距离排序的附近目标及可见目标包装。
- [NearestVisibleEntities](../../src/main/java/org/unitego/lobecorp/world/entity/ai/memory/NearestVisibleEntities.java)
  缓存本次包装内的视线查询；不要跨生命周期复用其缓存。
- [OrdealAttackablesSensor](../../src/main/java/org/unitego/lobecorp/world/entity/ai/sensing/OrdealAttackablesSensor.java)
  组合考验目标资格和原版攻击资格。

清道夫清理目标传感器可以识别尸体和非空物品实体；清道夫尸体还要满足复原资格。扫描周期、范围和注册入口以 `LcSensorTypes`
为准。新增记忆必须先检查是否已有等价记忆，再检查 Codec、传感器声明及活动条件，不能另建重复 Brain。

## 实体状态、属性与状态效果

`EntityState` 由 ID 和可空的互斥组组成。同一非空组的状态互斥；添加状态会移除重复 ID
及冲突状态，并通过持有者更新不可变状态列表。女皇状态修改涉及坐姿尺寸时刷新碰撞尺寸，客户端同步更新也有对应刷新入口。

状态定义由 [TheQueenOfHatredStates](../../src/main/java/org/unitego/lobecorp/registry/entity/state/TheQueenOfHatredStates.java)
与 [SweeperStates](../../src/main/java/org/unitego/lobecorp/registry/entity/state/SweeperStates.java)
集中注册。状态用于表达实体事实，不能替代技能阶段、冷却或药水效果的各自权威数据。

`ENTITY_SKILL_COOLDOWN_MULTIPLIER` 和 `DAMAGE_TAKEN_MULTIPLIER` 是注册的可同步属性。伤害入口应用承伤倍率，技能入口应用冷却倍率；改变基础属性与添加修饰符是不同操作。

眩晕效果仅在达到实现中的强度条件、且 Mob 原先启用了 AI 时关闭 AI，并记录自己关闭过的对象。移除或到期事件恢复这些对象的
AI；实体离开世界会清理记录。不能把原本已经关闭 AI 的对象无条件重新启用。

## 尸体、复原与净化

`LivingEntityEvents` 在服务端实体离开世界时先清除技能运行态并处理指挥家关系。仅 `KILLED` 原因的
Mob、且不是尸体实体时创建尸体；不能据此推导玩家或所有 LivingEntity 都会产生尸体。

尸体创建链：

1. 清除原实体临时状态，然后保存拥有者 NBT。
2. 重建用于尸体显示的拥有者实体，复制位置和朝向。
3. 尸体持有拥有者数据与缓存尺寸，通过实体数据序列化器同步显示所需信息。
4. 复原入口重建拥有者、恢复数据并再次清除临时状态；调用方负责实际加入世界。

临时清理包含运动、跌落、火焰、空气、冻结、状态效果、非永久属性修饰符、实体状态、攻击与行走等记忆、技能临时状态、目标和导航。拥有者数据变化会重新创建显示缓存并刷新尺寸。尸体腐败及无效拥有者清理也由实体生命周期处理。

净化是另一种操作：只移除有害状态效果和按属性样式判定为负面的修饰符，包含符合条件的永久修饰符；不重置基础属性，也不等同于尸体的全量临时清理。资格检测应复用
`needsPurification`，执行复用 `purify`。

## 同步、配置与资源关系

- 实体持续状态通过 `SynchedEntityData`
  同步；序列化器见 [LcEntityDataSerializers](../../src/main/java/org/unitego/lobecorp/registry/entity/LcEntityDataSerializers.java)。
- 技能归属、技能组及冷却由 [LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java)
  注册的附件承载；活动技能是非持久化运行态。
- AI 行为参数在对应 AI、传感器及注册源维护；状态 ID 与属性 ID 必须与持久化、网络 Codec 和使用方保持一致。
- 实体模型、动画、粒子资源只决定表现；服务端目标资格、阶段和命中结算不从纹理或客户端姿态推导。
- 本轮没有修改配置、资源、存档格式或生成输出；新增翻译键仍必须通过 IDEA Data 生成。

## 修改前核对点

1. 核对实体、AI、技能、碰撞和动画的所有共享调用点，确认状态属于 AI 私有决策还是共享事实。
2. 核对目标记忆的写入、失效、死亡、卸载与指挥家接管路径。
3. 转向复用 [EntityFacingUtil](../../src/main/java/org/unitego/lobecorp/util/entity/EntityFacingUtil.java)
   ，头和身体同时转向，保留限速，不写引擎插值旧值。
4. 核对状态互斥、尺寸刷新及客户端同步；临时修饰符与永久修饰符分别处理。
5. 保留唯一事件入口、总线、优先级及取消语义，遵循[事件规范](../../.codex/standards/event-registration.md)。

## 核对范围与尚未验收

本页核对了表列源码及行为、传感器、状态注册和附件入口；没有逐一核对所有颜色考验的完整行为、投射物实现、原版寻路内部、存档往返和多人断线重连。AI
时序、边缘寻路、尸体复原和眩晕恢复均尚未做游戏运行验收。

维护时先用本文定位入口，再通过 IDEA
核对当前实现；文档缺项不代表功能不存在。包迁移完成后更新目标链接与核对状态。修改记录统一写入[系统维护记录](changes.md)。
