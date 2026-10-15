# 事件入口与分发

核对日期：2026-10-13。以当前订阅代码为准；不代表事件运行时次数验证。

## 唯一入口

event 放 Mod 自定义事件，events
放订阅与分发。唯一性按总线、发行侧和事件类型判断，保留优先级、取消语义及执行顺序。专项规则见 [事件监听规范](../event-registration.md)
和 [事件架构 Skill](../../.codex/skills/lobecorp-event-architecture/SKILL.md)。

| 入口                                                                                                                          | 实际职责                                                                      |
|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [LobecorpEvents](../../src/main/java/org/unitego/lobecorp/events/LobecorpEvents.java)                                         | 自定义注册表、Ticket 控制器、Payload、实体属性与数据生成登记                  |
| [WorldTickEvents](../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java)                                       | LevelTick.Post HIGH：技能效果 → 动画／附着 → 命中框 → 动画同步 → 文本状态同步；ServerTick.Post 推进区块加载并清理卸载世界，ServerStopped 清理动画、文本与同步缓存 |
| [AnimationResourceEvents](../../src/main/java/org/unitego/lobecorp/events/AnimationResourceEvents.java)                     | AddServerReloadListenersEvent 的动画内容唯一登记入口，保留型监听器读取完整 data 模型／动画 |
| [CommandEvents](../../src/main/java/org/unitego/lobecorp/events/CommandEvents.java)                                         | RegisterCommandsEvent 的命令唯一登记入口，分发独立动画接入示例命令 |
| [LivingEntityEvents](../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java)                                 | 服务端实体技能 tick、伤害链、实体离开清理与尸体生成                           |
| [ConductorCapabilityEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorCapabilityEvents.java)                   | 指挥家能力绑定                                                                |
| [ConductorAbilityRegistrationEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorAbilityRegistrationEvents.java) | 指挥家能力注册分发                                                            |
| [ConductorGameplayEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorGameplayEvents.java)                       | 玩家生命周期与指挥家会话处理                                                  |
| [EntityDataSyncEvents](../../src/main/java/org/unitego/lobecorp/events/EntityDataSyncEvents.java)                             | 首次追踪、本人复活及换维度时补发复杂附件快照                                                        |
| [MobEffectEvents](../../src/main/java/org/unitego/lobecorp/events/MobEffectEvents.java)                                       | 眩晕恢复、攻击/交互/使用/挖掘的动作守卫                                      |
| [ClientRuntimeEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)                        | 客户端 tick、输入、登录、注销及世界卸载                                             |
| [ClientRenderingEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRenderingEvents.java)                    | 客户端渲染回调                                                                |
| [LobecorpClientEvents](../../src/main/java/org/unitego/lobecorp/events/client/LobecorpClientEvents.java)                      | 客户端专属登记入口，包含文本 ParticleGroup 与资源重载监听                                                            |

入口名称不等同于总线声明，新增监听前必须打开注解与方法签名核对。

## 生命周期与处理顺序

LivingEntityEvents 仅在服务端推进技能：实体具有 IEntitySkillAccess 时走公开接口，否则走兼容工具。随后推进该实体的服务端动画，再维护抓取锚点；非 LivingEntity 同样推进实体动画。伤害回调保留既有倍率、清道夫吸收/战斗目标与指挥家筛选处理顺序。

`LivingDamageEvent.Post` 由同一类唯一监听并委托 `DamageParticles.onDamage`，只消费已结算伤害，不改变伤害规则。屏幕粒子的客户端 tick、登录／注销和世界卸载复用 `ClientRuntimeEvents`；GUI 图层与字体重载监听经 `LobecorpClientEvents` 委托 `ScreenParticleRegistration`，不新增平行事件订阅。内容、合并、池和位置规则见[屏幕伤害粒子](screen-damage-particles.md)。

LevelTick.Post 的既有唯一入口依次推进技能效果、未处理的动画、物品显示描述和通用附着，再结算命中框并同步最终快照；动画实例按世界游戏 tick 防止重复推进。`LcAnimationSync.sync` 之后调用 `TextParticles.tick`，只调度每 20 tick 的文本完整补发，不推进逐字或单字动画。`AnimationResourceEvents` 只在服务端重载监听登记事件添加一个保留型内容仓库。ServerTick.Post 清理卸载世界，ServerStoppedEvent 释放动画、附着、物品描述、文本实例及同步缓存，接入与生命周期见[动画运行时](animation-runtime.md)、[通用空间附着](attachments.md)和[文本展示粒子](text-particles.md)。

`LobecorpEvents.registerEntityAttributes` 是 `EntityAttributeCreationEvent` 的唯一入口，委托 `EntityAttributeCreation` 根据登记的实体实现类为技能持有者补齐缺失属性，保留手动值；不在实体构造 Mixin 中补齐，也不增加重复订阅。原 `EntityAttributeModificationEvent` 的通用受伤倍率登记保持。

实体离开时清理技能运行状态，并由指挥家停止/脱离；KILLED 与 DISCARDED 的永久离开语义区别于临时卸载。尸体只从符合条件的被击杀
Mob 创建，不能因迁移入口而重复生成。

复杂附件首次同步使用 `PlayerEvent.StartTracking`，当前 NeoForge 在实体生成 bundle 发送之后触发该事件。登录仍只有 `ConductorGameplayEvents.onPlayerLogin` 一个订阅入口，先处理既有目录／会话，再分发本人附件完整快照；`EntityDataSyncEvents` 处理追踪、复活与换维度，不订阅较早的 Clone。两类功能域载荷只读取现有持久化状态，不创建技能运行态或自动加入队伍。

ClientRuntimeEvents 在 tick 先推进当前客户端世界的本地附着，再推进玩家动画运行态、指挥家客户端、输入控制和 Photon Geo 效果；登录、注销和世界卸载清理本地附着、物品描述及渲染缓存，继续沿用原控制、特效和动画内容清理。客户端订阅限制
Dist.CLIENT，不能加载到专用服务器。文本实例缓存同样在登录、注销和世界卸载时清理；逐字与单字动画由客户端粒子 tick 推进。文本自定义 `ParticleGroup` 和资源重载监听统一经 `LobecorpClientEvents` 委托 `TextParticleRegistration` 登记，不新增平行订阅入口。

`CommandEvents.onRegisterCommands` 委托 `LcAnimationCommand` 注册 `/lobecorp animation player|attachment|item`，沿用管理员权限，不增加第二个命令事件订阅。旧命令及 DemoClient 已移除；真实玩家附着与独立物品显示由通用绑定、物品描述及现有提取／提交事件维护，命令参数和结束时序见[动画调试命令](animation-runtime.md#动画调试命令)。

## 修改前检查与复用

通过 IDEA 查找目标事件全部订阅点与调用链，优先在已有唯一入口按顺序分发功能处理。不要因添加新系统重复声明
SubscribeEvent，也不要把服务端状态结算复制到客户端入口。

2026-10-07 在既有 MobEffectEvents 接入攻击、物品 Start/Tick/Stop、方块与实体交互、左键挖掘及最终 BreakBlockEvent 的三级眩晕守卫。使用 Tick 先 `stopUsingItem` 再取消，避免取消 tick 意外进入原版完成流程；左键 ABORT 放行以清理挖掘状态。ClientRuntimeEvents 的唯一 `InteractionKeyMappingTriggered` 入口拒绝攻击/使用键、禁止挥手并停止持续挖掘；服务端仍独立检查。原版 Mob 攻击与 LivingEntity 刺击由对应 Mixin 使用同一判定。

LivingEntityEvents 的服务端实体 tick 按技能 → 动画 → 临时抓取推进，实体离开时先停止该来源的动画播放，再执行技能、抓取双方关系、指挥家与尸体处理。清理细节见 [指挥家](conductor.md)、[技能](entity-skills.md)、[实体抓取](entity-grab.md)、[粒子渲染](particle-rendering.md)
。变动后更新 [修改记录](changes.md)。
