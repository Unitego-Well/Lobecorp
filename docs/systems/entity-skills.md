# 实体技能

> 核对日期：2026-10-05。本文按 IDEA 读取的迁移前源码说明实际实现，不代表游戏运行验收。
> 包迁移按[已批准方案](../java-package-organization-plan.md)完成，链接已按实际源码路径核对；结果见[修改记录](changes.md)。

## 公共边界与注册

技能的公共操作通过 [EntitySkillAccess](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillAccess.java)
提供，[EntitySkillAttachmentAccess](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillAttachmentAccess.java)
接入附件并委托同一 [EntitySkillUtil](../../src/main/java/org/unitego/lobecorp/util/entity/skill/EntitySkillUtil.java)
状态机。新增使用方复用这个入口，不复制生命周期推进逻辑。

| 职责                 | 核对入口                                                                                                                                                                                                                                          |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 技能定义与一次性配置 | [IEntitySkill](../../src/main/java/org/unitego/lobecorp/world/entity/skill/IEntitySkill.java)、[EntitySkill](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkill.java)                                                        |
| 每次施放数据         | [EntitySkillRuntime](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillRuntime.java)、[EntitySkillRuntimeData](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillRuntimeData.java)                      |
| 请求与结果           | [EntitySkillCastRequest](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillCastRequest.java)、[EntitySkillCastResult](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillCastResult.java)                |
| 注册生命周期参数     | [TheQueenOfHatredSkills](../../src/main/java/org/unitego/lobecorp/registry/entity/skill/TheQueenOfHatredSkills.java)、[SweeperSkills](../../src/main/java/org/unitego/lobecorp/registry/entity/skill/SweeperSkills.java)                          |
| 多段技能             | [MultiStageSkill](../../src/main/java/org/unitego/lobecorp/world/entity/skill/MultiStageSkill.java)、[MultiStageBasicSkill](../../src/main/java/org/unitego/lobecorp/world/entity/skill/MultiStageBasicSkill.java)                                |
| 手动方向控制         | [EntitySkillManualControl](../../src/main/java/org/unitego/lobecorp/world/entity/skill/EntitySkillManualControl.java)、[LaserSkill](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/skill/LaserSkill.java) |
| 生命周期同步         | [EntitySkillSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/EntitySkillSyncPayload.java)                                                                                                                                         |
| 服务端推进与卸载清理 | [LivingEntityEvents](../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java)                                                                                                                                                     |

`Properties` 集中配置 ID、前摇、持续、后摇、冷却、技能组、互斥、替换和移动限制。注册配置只保存一次，不在实现类重复保存生命周期数值。注册阶段引用使用延迟获取，避免绑定完成前解引用注册项。

技能定义是共享的配置与行为；`EntitySkillRuntime` 是一次施放的数据，保存实体、技能、运行
ID、阶段计时、目标、成功标记、连段序号和类型化附加数据。活动实例及续段状态不序列化，不作为跨世界迁移数据。

## 开始施放与时间推进

当前阶段枚举只有 `WINDUP → ACTIVE → RECOVERY`。冷却是独立的到期时间，不是第四个活动阶段；历史注释中的 COOLDOWN 不能作为当前状态机依据。

开始施放的顺序：

1. 初始化及检查续段状态、技能归属、冷却、互斥和技能组容量。
2. 创建本次运行数据，设置目标并检查具体技能资格及替换资格。
3. 先瞄准；未到位时返回瞄准结果，不开始前摇和默认冷却。
4. 通过可取消的服务端施放事件，重新检查必要条件并处理被允许的旧技能替换。
5. 加入活动实例，启动默认冷却，应用移动约束，调用前摇开始并发送开始事件。
6. 推进为零的阶段，允许同一次调用立即进入下一阶段。

服务端 `EntityTick.Post` 通过能力或既有工具入口推进活动技能。每次推进执行移动限制、计时及当前阶段回调；回调后再次确认运行实例是否仍然存在，避免已经取消的实例继续执行。

| 配置      | 当前含义                                   |
|-----------|--------------------------------------------|
| 前摇为 0  | 立即激活                                   |
| 持续为 0  | 激活后立即结束持续                         |
| 持续为 -1 | 保持 ACTIVE，等待明确结束或取消            |
| 后摇为 0  | 持续结束后立即完成                         |
| 导航锁定  | 停止导航；不等同于所有实体运动钩子         |
| 移动锁定  | 工具入口限制运动，实体自身移动钩子仍需配合 |

默认冷却在成功开始施放时，以游戏时间和冷却倍率计算到期时间，和三个阶段同时推进。失败、瞄准中或事件拒绝不启动默认冷却；完成和取消不重启它。显式的本次施放冷却覆盖在终止路径单独应用，不能把覆盖和默认冷却混为一谈。

## 结束、取消与清理

`end` 结束 ACTIVE、执行持续结束回调并进入 RECOVERY；后摇为正时仍然存在活动实例。`finish`
执行后摇结束、处理续段状态、移除绑定效果及命中框、应用显式冷却覆盖、移除活动实例并发送完成事件。

普通取消遵守技能的可取消条件；强制取消可以绕过该条件。取消回调之后，同样移除绑定的轻量效果与命中框，再移除运行实例并处理显式冷却覆盖和取消事件。

`clearRuntimeState` 强制结束活动运行、清除续段并重置相关临时记忆，保留技能归属和现有冷却。`clearTemporaryState`
还会清除冷却，尸体及复原路径使用这种临时状态重置。改变清理入口前应核对死亡、卸载、世界切换和指挥家解除关系。

具体技能持有的状态、属性修饰符、动画及粒子仍由对应终止回调清理；框架绑定清理不能自动替代未绑定资源的清理。具体实例见[命中框](hitbox.md)
和[动画](animation.md)。

## 连段与手动控制

`MultiStageSkill` 为每个实体、每个技能保存独立序列。当前段号在开始前写入运行实例；上一段完成后进入续段窗口，等待期间不占用活动技能组或移动锁。完成最后一段或窗口到期时重置段号，并开始整套冷却；取消不会自动推进段号。

`MultiStageBasicSkill` 的默认续段窗口为 10 tick，允许空挥续段，并支持在上一段后摇中接下一段。不能用其他场景的
`ATTACK_COMBO` 替代自身序列状态。AI 与指挥家施放复用同一入口，不能各自保存一套段数。

`EntitySkillManualControl` 是单次运行实例上的控制数据，包含准备、手动接管和恢复自动目标的模式。接管可更新目标、位置或冻结方向；释放后读取
Mob 当前有效攻击目标，没有目标时保持当前朝向。它不重启阶段、不重置冷却，也不建立独立同步协议。

手动能力需要具体技能接入；当前核对了激光技能的准备、更新与释放入口，不能推断全部技能默认支持手动控制。请求权限与指挥家选择资格在相应服务端控制入口检查。

## 独立轻量技能效果

[EntitySkillEffect](../../src/main/java/org/unitego/lobecorp/world/entity/skill/effect/EntitySkillEffect.java)是由
ServerLevel 管理的效果实例，拥有自己的位置、寿命和可选来源。它不是 Minecraft 实体，不注册 EntityType，不写入世界存档，也不是命中框。

[EntitySkillEffectManager](../../src/main/java/org/unitego/lobecorp/world/entity/skill/effect/EntitySkillEffectManager.java)
统一添加、推进与移除。寿命可为正数或无限标记；来源不可用时的移除绑定，与技能运行结束时的移除绑定分别控制。移除时调用效果自身清理并解除运行绑定。

[WorldTickEvents](../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java)在 LevelTick.Post
先推进技能效果，再推进命中框，使效果能够在本刻结算前更新或创建命中框。新增效果应组合既有命中框及快照能力，不另建平行伤害遍历。

[QueenStarfallEffect](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/skill/effect/QueenStarfallEffect.java)
在固定区域独立生成星星；其构造绑定来源不可用清理，不绑定施放运行结束。因此技能结束不意味着区域效果立即结束。生成的星星是另外的投射物实体，具有自己的飞行参数和生命周期。

## 权威同步与持久化边界

- 服务端回调发送运行 ID、技能 ID、阶段、计时、连段与目标信息；客户端按 ID 查找或创建运行镜像，再执行对应表现回调。
- 客户端在后摇结束或取消回调后移除镜像；服务端是施放资格、计时和伤害的权威。
- 技能归属、技能组和冷却由 [LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java)
  中的相应附件持久化及同步；活动实例和续段不持久化。
- 同步回调与实体持续同步状态各有职责，不再添加重复 Payload 驱动同一动画或粒子。
- 参数修改需同时核对注册 Properties、具体技能规则、附件 Codec、同步载荷及调用方。动画和粒子资源关联见表现文档，生成资源保持只读。

## 修改前核对与尚未验收

修改前检查施放结果、事件拒绝、瞄准门槛、替换规则、移动锁、零阶段推进、无限持续、默认与覆盖冷却、续段失败与过期、所有终止回调。新增公共操作优先复用能力接口及类型化数据，保留唯一状态机。

本轮已核对表列源码、事件入口、附件以及效果管理；未逐个核对所有具体技能、指挥家完整请求校验、跨维度行为、晚加入客户端的全量恢复和多人网络时序。没有执行游戏、编译或
Data。维护记录见[系统维护记录](changes.md)。
