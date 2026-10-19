# NeoForge 事件监听规范

## 适用范围

适用于 Lobecorp 的 NeoForge 模组事件订阅与事件处理。当前代码使用 events（复数）包。

## 订阅包与唯一性

- 所有自动订阅类放在 org.unitego.lobecorp.events 或其子包中；客户端订阅类放在 org.unitego.lobecorp.events.client，并限制在 Dist.CLIENT。
- 按功能域拆分订阅类，避免把通用侧或客户端的所有事件集中在一个类中。
- events 包之外的业务类不得添加 @EventBusSubscriber 或 @SubscribeEvent；业务类可保留由订阅入口调用的普通处理方法。
- 同一事件总线、发行侧和具体事件类型只注册一个订阅方法。不得同时用注解、.register 或 .addListener 重复订阅同一处理。
- 多个模块需要响应同一事件时，由该事件唯一的订阅方法按明确顺序调用各功能处理方法，不得为同一事件增加第二个订阅入口。
- 新事件加入对应功能域的订阅类前，检查整个源码中是否已有相同总线、发行侧和具体事件类型的入口。

## 分发与行为

- 业务处理保留在所属功能类中，订阅类只负责事件入口和必要的有序分发。
- 保持事件的总线、客户端/服务端隔离、取消状态、优先级及模块内调用顺序。
- 同一事件需要调用多个处理方法时，在唯一订阅方法中明确调用顺序。当前 LevelTickEvent.Post 使用 HIGH 优先级，先推进技能效果，再更新命中框；LivingIncomingDamageEvent 先应用实体受伤倍率，再处理指挥家阵营伤害过滤；客户端 RegisterRenderStateModifiersEvent 先注册实体渲染修改器，再注册指挥家修改器。
- 战斗关系通过现有唯一事件入口分发：目标变化先处理 CombatEvents 关系资格，再处理指挥家控制资格；LivingIncomingDamageEvent 先应用承伤倍率，再处理 CombatEvents 阵营伤害过滤。伤害 Post 先记录仇恨，再执行原伤害粒子逻辑。
- 若不同优先级监听器不能在单一入口中保留原有的全部相对时序，检查内部行为依赖，选择能保留关键依赖的唯一优先级，并记录其对其他监听器相对时序的影响；若没有明确的依赖依据，先确认再改。

## 检查

- 用 IDEA 搜索 @EventBusSubscriber 和 @SubscribeEvent，确认订阅类只出现在 events 包及其子包，并按功能域拆分。
- 检查每个（事件总线、发行侧、具体事件类型）只有一个 @SubscribeEvent 入口，并确认分发目标没有被其他方式再次注册。
- 用 IDEA 检查新入口的导入、类型、调用和诊断。除非用户明确要求，不构建、测试、同步、重载或启动游戏。