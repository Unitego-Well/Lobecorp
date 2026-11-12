# 事件入口与分发

## 2026-10-28 追踪与事件载荷整理

EntityDataSyncEvents.onStartTracking 将重复 ServerPlayer 判断合并，仍先发送动画追踪，再为 LivingEntity 发送初始状态。LivingResourceChangeEvent 仍通过构造器赋值并保留公开 getter；2026-10-29 复核恢复了原字段声明，不为消除风格警告额外加入 final。

LivingEntityEvents.onLivingDeath 虽名为死亡处理，实际参数仍是 EntityLeaveLevelEvent；本批保留公开方法名，仅注释说明离开世界清理不限于死亡，不以无内部引用改变公开入口。总线、优先级、取消语义和模块清理顺序未改。

核对日期：2026-10-13。以当前订阅代码为准；不代表事件运行时次数验证。

## 订阅规则

本节合并原事件监听规范，保留既有订阅与修改约束；其中带日期的检查结果仅代表原阶段。

### 适用范围

适用于 Lobecorp 的 NeoForge 模组事件订阅与事件处理。当前代码使用 events（复数）包。

### 订阅包与唯一性

- 所有自动订阅类放在 org.unitego.lobecorp.events 或其子包中；客户端订阅类放在 org.unitego.lobecorp.events.client，并限制在 Dist.CLIENT。
- 按功能域拆分订阅类，避免把通用侧或客户端的所有事件集中在一个类中。
- events 包之外的业务类不得添加 @EventBusSubscriber 或 @SubscribeEvent；业务类可保留由订阅入口调用的普通处理方法。
- 同一事件总线、发行侧和具体事件类型只注册一个订阅方法。不得同时用注解、.register 或 .addListener 重复订阅同一处理。
- 多个模块需要响应同一事件时，由该事件唯一的订阅方法按明确顺序调用各功能处理方法，不得为同一事件增加第二个订阅入口。
- 新事件加入对应功能域的订阅类前，检查整个源码中是否已有相同总线、发行侧和具体事件类型的入口。

### 分发与行为

- 业务处理保留在所属功能类中，订阅类只负责事件入口和必要的有序分发。
- 保持事件的总线、客户端/服务端隔离、取消状态、优先级及模块内调用顺序。
- 同一事件需要调用多个处理方法时，在唯一订阅方法中明确调用顺序。当前 LevelTickEvent.Post 使用 HIGH 优先级，先推进技能效果，再更新命中框；LivingIncomingDamageEvent 先应用实体受伤倍率，再处理指挥家阵营伤害过滤；客户端 RegisterRenderStateModifiersEvent 先注册实体渲染修改器，再注册指挥家修改器。
- 战斗关系通过现有唯一事件入口分发：目标变化先处理 CombatEvents 关系资格，再处理指挥家控制资格；LivingIncomingDamageEvent 先应用承伤倍率，再处理 CombatEvents 阵营伤害过滤。伤害 Post 先记录仇恨，再执行原伤害粒子逻辑。
- 若不同优先级监听器不能在单一入口中保留原有的全部相对时序，检查内部行为依赖，选择能保留关键依赖的唯一优先级，并记录其对其他监听器相对时序的影响；若没有明确的依赖依据，先确认再改。

### 技能客户端生命周期

女皇 Photon 技能表现从现有 LcClientAnimations 权威快照消费阶段与实例身份，不新增技能监听或同步通道。ClientRuntimeEvents 的既有 tick、登录、退出和世界卸载入口统一推进和清理 QueenBeamEffects，资源重载同步清理宿主；实体动画与实体模型LOD保持。2026-10-21已完成静态检查和编译，真实技能链仍受游戏HUD异常和MCP控制激活阻断，未宣称运行通过。历史 EntitySkillClientEvents 的五个阶段监听已移除，不恢复重复入口；NeoForge抽象 EntitySkillEvent 仍不得直接注册监听。

### 检查

- 用 IDEA 搜索 @EventBusSubscriber 和 @SubscribeEvent，确认订阅类只出现在 events 包及其子包，并按功能域拆分。
- 检查每个（事件总线、发行侧、具体事件类型）只有一个 @SubscribeEvent 入口，并确认分发目标没有被其他方式再次注册。
- 用 IDEA 检查新入口的导入、类型、调用和诊断。除非用户明确要求，不构建、测试、同步、重载或启动游戏。

## 唯一入口

2026-10-18 战斗关系接入：目标变化仍由 ConductorGameplayEvents 唯一订阅，依次分发 CombatEvents 的关系资格和 ConductorEvents 的控制资格；承伤倍率之后由 CombatEvents 统一过滤阵营伤害，伤害 Post 先记录仇恨再生成原粒子。没有增加订阅入口，详见[战斗关系](../entities/combat.md)。

event 放 Mod 自定义事件，events
放订阅与分发。唯一性按总线、发行侧和事件类型判断，保留优先级、取消语义及执行顺序。专项规则见 [事件监听规范](#订阅规则)
和 事件架构 Skill（历史路径：`../../.codex/skills/lobecorp-event-architecture/SKILL.md`；当前 dev 未保留）。

| 入口                                                                                                                          | 实际职责                                                                      |
|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [LobecorpEvents](../../../src/main/java/org/unitego/lobecorp/events/LobecorpEvents.java)                                         | 自定义注册表、Ticket 控制器、Payload、实体属性与数据生成登记                  |
| [WorldTickEvents](../../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java)                                       | LevelTick.Post HIGH：技能效果 → 动画／附着 → 命中框 → 动画同步 → 文本状态同步；ServerTick.Post 推进区块加载并清理卸载世界，ServerStopped 清理动画、文本与同步缓存 |
| [AnimationResourceEvents](../../../src/main/java/org/unitego/lobecorp/events/AnimationResourceEvents.java)                     | AddServerReloadListenersEvent 的动画内容唯一登记入口，保留型监听器读取完整 data 模型／动画 |
| [CommandEvents](../../../src/main/java/org/unitego/lobecorp/events/CommandEvents.java)                                         | RegisterCommandsEvent 的命令唯一登记入口，分发独立动画接入示例命令 |
| [LivingEntityEvents](../../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java)                                 | 服务端实体技能 tick、伤害链、实体离开清理与尸体生成                           |
| [ConductorCapabilityEvents](../../../src/main/java/org/unitego/lobecorp/events/ConductorCapabilityEvents.java)                   | 指挥家能力绑定                                                                |
| [ConductorAbilityRegistrationEvents](../../../src/main/java/org/unitego/lobecorp/events/ConductorAbilityRegistrationEvents.java) | 指挥家能力注册分发                                                            |
| [ConductorGameplayEvents](../../../src/main/java/org/unitego/lobecorp/events/ConductorGameplayEvents.java)                       | 玩家生命周期与指挥家会话处理                                                  |
| [EntityDataSyncEvents](../../../src/main/java/org/unitego/lobecorp/events/EntityDataSyncEvents.java)                             | 首次追踪、本人复活及换维度时补发复杂附件快照                                                        |
| [MobEffectEvents](../../../src/main/java/org/unitego/lobecorp/events/MobEffectEvents.java)                                       | 眩晕恢复、攻击/交互/使用/挖掘的动作守卫                                      |
| [ClientRuntimeEvents](../../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)                        | 客户端 tick、输入、登录、注销及世界卸载                                             |
| [ClientRenderingEvents](../../../src/main/java/org/unitego/lobecorp/events/client/ClientRenderingEvents.java)                    | 客户端渲染回调                                                                |
| [LobecorpClientEvents](../../../src/main/java/org/unitego/lobecorp/events/client/LobecorpClientEvents.java)                      | 客户端专属登记入口，包含文本 ParticleGroup 与资源重载监听                                                            |

入口名称不等同于总线声明，新增监听前必须打开注解与方法签名核对。

## 2026-11-02 客户端 HUD 生命周期契约

玩家状态、事件与屏幕滤镜共用 `HudLayerManager.INSTANCE`；客户端登录、注销和世界卸载仍由 `ClientRuntimeEvents` 唯一入口调用 `clear()`。管理器保持单一会话跟踪器，`synchronizeSession()` 在状态写入前执行，`clearAction` 在显式清理和真实会话切换时执行且不依赖宿主是否已创建。事件 HUD 的状态保存包装父子关系、注册顺序与 `show` 顺序；包装支持可空父包装，父级必须先行注册，旧无参重载仍为根包装。视图按注册顺序建立包装树，每个容器为“工厂与根布局固定子节点、受管子包装、活动 HUD”顺序；`prepare` 只同步本视图实际挂载的活动 HUD，不再清空整棵容器，`hide` 自动补位，`clear` 保留包装树与固定节点，`clearActive` 保留定义；不新增 HUD 专用事件订阅。

## 生命周期与处理顺序

LivingEntityEvents 仅在服务端推进技能：实体具有 IEntitySkillAccess 时走公开接口，否则走兼容工具。随后推进该实体的服务端动画，再维护抓取锚点；非 LivingEntity 同样推进实体动画。伤害回调保留既有倍率、清道夫吸收/战斗目标与指挥家筛选处理顺序。

`LivingDamageEvent.Post` 由同一类唯一监听并委托 `DamageParticles.onDamage`，只消费已结算伤害，不改变伤害规则。屏幕粒子的客户端 tick、登录／注销和世界卸载复用 `ClientRuntimeEvents`；GUI 图层与字体重载监听经 `LobecorpClientEvents` 委托 `ScreenParticleRegistration`，不新增平行事件订阅。内容、合并、池和位置规则见[屏幕伤害粒子](../client/screen-damage-particles.md)。

LevelTick.Post 的既有唯一入口依次推进技能效果、未处理的动画、物品显示描述和通用附着，再结算命中框并同步最终快照；动画实例按世界游戏 tick 防止重复推进。`LcAnimationSync.sync` 之后调用 `TextParticles.tick`，只调度每 20 tick 的文本完整补发，不推进逐字或单字动画。`AnimationResourceEvents` 只在服务端重载监听登记事件添加一个保留型内容仓库。ServerTick.Post 清理卸载世界，ServerStoppedEvent 释放动画、附着、物品描述、文本实例及同步缓存，接入与生命周期见[动画运行时](../animation/animation-runtime.md)、[通用空间附着](../animation/attachments.md)和[文本展示粒子](../client/text-particles.md)。

`LobecorpEvents.registerEntityAttributes` 是 `EntityAttributeCreationEvent` 的唯一入口，委托 `EntityAttributeCreation` 根据登记的实体实现类为技能持有者补齐缺失属性，保留手动值；不在实体构造 Mixin 中补齐，也不增加重复订阅。原 `EntityAttributeModificationEvent` 的通用受伤倍率登记保持。

实体离开时清理技能运行状态，并由指挥家停止/脱离；KILLED 与 DISCARDED 的永久离开语义区别于临时卸载。尸体只从符合条件的被击杀
Mob 创建，不能因迁移入口而重复生成。

复杂附件首次同步使用 `PlayerEvent.StartTracking`，当前 NeoForge 在实体生成 bundle 发送之后触发该事件。登录仍只有 `ConductorGameplayEvents.onPlayerLogin` 一个订阅入口，先处理既有目录／会话，再分发本人附件完整快照；`EntityDataSyncEvents` 处理追踪、复活与换维度，不订阅较早的 Clone。两类功能域载荷只读取现有持久化状态，不创建技能运行态或自动加入队伍。

ClientRuntimeEvents 在 tick 先推进当前客户端世界的本地附着，再推进玩家动画运行态、指挥家客户端、输入控制和 Photon Geo 效果；登录、注销和世界卸载清理本地附着、物品描述及渲染缓存，继续沿用原控制、特效和动画内容清理。客户端订阅限制
Dist.CLIENT，不能加载到专用服务器。文本实例缓存同样在登录、注销和世界卸载时清理；逐字与单字动画由客户端粒子 tick 推进。文本自定义 `ParticleGroup` 和资源重载监听统一经 `LobecorpClientEvents` 委托 `TextParticleRegistration` 登记，不新增平行订阅入口。

`CommandEvents.onRegisterCommands` 委托 `LcAnimationCommand` 注册 `/lobecorp animation player|attachment|item`，沿用管理员权限，不增加第二个命令事件订阅。同一入口追加 `LcDebugCommand.registerServer`，注册 `/lobecorp debug`；客户端命令由 `LobecorpClientEvents.onRegisterClientCommands` 同一入口追加 `LcDebugCommand.registerClient`，不新增订阅。旧命令及 DemoClient 已移除；真实玩家附着与独立物品显示由通用绑定、物品描述及现有提取／提交事件维护，命令参数和结束时序见[动画调试命令](../animation/animation-runtime.md#动画调试命令)，统一调试入口见[非 Photon 调试开关](../../tooling/debugging.md)。

## 修改前检查与复用

通过 IDEA 查找目标事件全部订阅点与调用链，优先在已有唯一入口按顺序分发功能处理。不要因添加新系统重复声明
SubscribeEvent，也不要把服务端状态结算复制到客户端入口。

2026-10-07 在既有 MobEffectEvents 接入攻击、物品 Start/Tick/Stop、方块与实体交互、左键挖掘及最终 BreakBlockEvent 的三级眩晕守卫。使用 Tick 先 `stopUsingItem` 再取消，避免取消 tick 意外进入原版完成流程；左键 ABORT 放行以清理挖掘状态。ClientRuntimeEvents 的唯一 `InteractionKeyMappingTriggered` 入口拒绝攻击/使用键、禁止挥手并停止持续挖掘；服务端仍独立检查。原版 Mob 攻击与 LivingEntity 刺击由对应 Mixin 使用同一判定。

LivingEntityEvents 的服务端实体 tick 按技能 → 动画 → 临时抓取推进，实体离开时先停止该来源的动画播放，再执行技能、抓取双方关系、指挥家与尸体处理。清理细节见 [指挥家](../encounters/conductor.md)、[技能](../entities/entity-skills.md)、[实体抓取](../entities/entity-grab.md)、[粒子渲染](../client/particle-rendering.md)
。变动后更新 [修改记录](../../records/changes.md)。
