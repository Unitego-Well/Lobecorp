# 事件入口与分发

核对日期：2026-10-05。以当前订阅代码为准；不代表事件运行时次数验证。

## 唯一入口

event 放 Mod 自定义事件，events
放订阅与分发。唯一性按总线、发行侧和事件类型判断，保留优先级、取消语义及执行顺序。专项规则见 [事件监听规范](../../.codex/standards/event-registration.md)
和 [事件架构 Skill](../../.codex/skills/lobecorp-event-architecture/SKILL.md)。

| 入口                                                                                                                          | 实际职责                                                                      |
|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [LobecorpEvents](../../src/main/java/org/unitego/lobecorp/events/LobecorpEvents.java)                                         | 自定义注册表、Ticket 控制器、Payload、实体属性与数据生成登记                  |
| [WorldTickEvents](../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java)                                       | LevelTick.Post HIGH：先技能效果，后命中框；ServerTick.Post 推进指挥家区块加载 |
| [LivingEntityEvents](../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java)                                 | 服务端实体技能 tick、伤害链、实体离开清理与尸体生成                           |
| [ConductorCapabilityEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorCapabilityEvents.java)                   | 指挥家能力绑定                                                                |
| [ConductorAbilityRegistrationEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorAbilityRegistrationEvents.java) | 指挥家能力注册分发                                                            |
| [ConductorGameplayEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorGameplayEvents.java)                       | 玩家生命周期与指挥家会话处理                                                  |
| [MobEffectEvents](../../src/main/java/org/unitego/lobecorp/events/MobEffectEvents.java)                                       | 状态效果影响的移动处理                                                        |
| [ClientRuntimeEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)                        | 客户端 tick、输入、注销及世界卸载                                             |
| [ClientRenderingEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRenderingEvents.java)                    | 客户端渲染回调                                                                |
| [LobecorpClientEvents](../../src/main/java/org/unitego/lobecorp/events/client/LobecorpClientEvents.java)                      | 客户端专属登记入口                                                            |

入口名称不等同于总线声明，新增监听前必须打开注解与方法签名核对。

## 生命周期与处理顺序

LivingEntityEvents 仅在服务端推进技能：实体具有 EntitySkillAccess 时走公开接口，否则走兼容工具。伤害回调保留既有倍率、清道夫吸收/战斗目标与指挥家筛选处理顺序。

实体离开时清理技能运行状态，并由指挥家停止/脱离；KILLED 与 DISCARDED 的永久离开语义区别于临时卸载。尸体只从符合条件的被击杀
Mob 创建，不能因迁移入口而重复生成。

ClientRuntimeEvents 在 tick 推进指挥家客户端、输入控制和 Photon Geo 效果；注销和世界卸载清理控制与特效缓存。客户端订阅限制
Dist.CLIENT，不能加载到专用服务器。

## 修改前检查与复用

通过 IDEA 查找目标事件全部订阅点与调用链，优先在已有唯一入口按顺序分发功能处理。不要因添加新系统重复声明
SubscribeEvent，也不要把服务端状态结算复制到客户端入口。

本次未新增订阅或改变处理顺序。清理细节见 [指挥家](conductor.md)、[技能](entity-skills.md)、[粒子渲染](particle-rendering.md)
。变动后更新 [修改记录](changes.md)。
