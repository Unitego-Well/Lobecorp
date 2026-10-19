# 实体与 AI

> 初次核对：2026-10-05。基线按 IDEA 读取的迁移前源码说明；2026-10-10 飞扑更新见对应段落，均不代表游戏运行验收。
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
| 实体状态与属性      | [IEntityStateHolder](../../src/main/java/org/unitego/lobecorp/world/entity/state/IEntityStateHolder.java)、[EntityState](../../src/main/java/org/unitego/lobecorp/world/entity/state/EntityState.java)、[LcAttributes](../../src/main/java/org/unitego/lobecorp/registry/entity/LcAttributes.java) |
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

### 清道夫移动速度与目标加速

2026-10-11 调整：[SweeperAi.updateMovementSpeed](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/SweeperAi.java) 在原版 MoveControl 推进前统一选择清道夫本 tick 的速度倍率。无有效攻击／清理实体目标且无指挥家指令时，随机闲逛固定使用既有常速 1，不因 WALK_TARGET 或导航回退目的地较远而切换至 3。已确认的源码原因是原距离判断同时覆盖这些闲逛位置，远目标会产生短时 3 倍速度；不能再把任意位置目的地视为追踪实体目标。

攻击、清扫和重组等实体目标使用 20 tick（1 秒）的线性加速，从倍率 1 升至既有追踪倍率 3。起点绑定稳定实体对象，同一实体移动、坐标变化或路径节点更新不会重启；目标变化、实际停止、技能占用、无目标闲逛、位置指令接管或进入常规近距减速区时重置。AI tick 间断超过 1 刻也重启，避免 NoAI 暂停后直接续用满速。计时与目标只归该 AI 实例所有，不持久化或同步。

指挥家 MOVE／RETURN 优先使用命令目的地，按原位置移动方式选速，不纳入实体目标的加速计时；MOVE 的自定义速度上限仍在最后应用。非攻击移动在距目的地不超过 2 格时使用常速，距离采用既有脚部三维距离，适用于清理接近和位置移动。攻击追击保留原范围与到位规则，不因 2 格减速阈值改变技能选择、攻击范围或停止容差。此阈值仅修改清道夫的 `RUN_TARGET_DISTANCE`，不改指挥家的精确接近／避让参数或清理 0.5 格工作间距。

原版 `MoveControl.hasWanted` 只识别 MOVE_TO，JUMPING 仍在移动但返回 false；速度更新以 `hasWanted` 或活跃导航作为移动资格，避免正常跳跃／台阶不断清空加速。`setWantedPosition` 在 JUMPING 时保留该操作状态，速度与原路径仍可更新；技能守卫继续优先。编辑器、编译与 Data 的实际结果见[修改记录](changes.md)，闲逛、目标切换、跳跃连续加速和 2 格减速手感仍待游戏验收。

### 清道夫地面鸟群移动

2026-10-11 按用户确认的 A+B 范围实现：自主闲逛和战斗追击使用 Boids 的分离、对齐与聚合。[SweeperAi](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/SweeperAi.java) 复用原 `NEAREST_LIVING_ENTITIES` 和可见生物记忆，在水平 6 格、脚部高差不超过 1 格内最多取 12 个有效同类，每 5 tick 重算群体影响，每 tick 以 0.25 比例平滑。分离距离取双方半宽之和再加 0.6 格；对齐读取实际水平速度；聚合趋向同类中心。对齐和聚合仅参考具有相同攻击目标的同类，闲逛时双方都无攻击目标也可参与；不同目标的同类只参与近距分离。

局部转向在 `MobConductorMixin` 调用的既有 `updateMovementSpeed` 末尾、原版 `MoveControl.tick` 之前执行。保留原 `WALK_TARGET`、路径、速度倍率和路点 Y，只调整最多前方 0.75 格的水平目标；寻路前进分量至少为 1，偏转上限为 45 度。距下一节点 1.5 格内逐渐减弱影响，0.35 格内完全交回导航。追击修正必须缩短与攻击目标的距离，进入共享普攻范围后退出，避免重新形成范围外停滞。新增方向和计时归每个 AI 实例所有，不持久化或同步，不新增传感器、管理器、移动控制器或网络协议。

指挥家控制的单位、清扫／重组接近、技能运行、施放前 `AIMING` 所在 tick、水／熔岩／攀爬、乘坐、离地和禁用 AI 时不参与群体转向。路点高差超过 0.25 格时交回原寻路处理跳跃或台阶。候选先通过 `ConductorMovement.safeApproach` 检查整箱碰撞与支撑，再以 0.25 格间距检查脚底覆盖区域的流体与当前实体寻路 malus；失败直接使用原路径目标，不另算路径或停止导航。群体转向不承诺绕开所有第三方动态地形，障碍和高差仍由原导航处理。

静态审查修正了两处实现陷阱：原版 MoveControl 的 `hasWanted` 会在本实体 tick 内恢复 WAIT，不能用它代表邻居持续移动，否则对齐通常为零；仅限制前进且保持速度会抹掉同线邻居的纵向分离，因此近距前方且横向退化时转成确定的侧向绕行。先统一世界侧向，再按实体 ID 分侧，避免迎面双方选择相同世界方向。参数集中在 `SweeperAi` 的 `FLOCK_*` 常量，调整时须同时保留节点推进、技能瞄准和精确清理优先级。编辑器及运行配置的实际结果见[修改记录](changes.md)；群体幅度、窄路、台阶及战斗到位仍需游戏验收。

### 清道夫攻击距离与追击停止

2026-10-11 修正：AI 的普攻／飞扑选择与 `LeapSkill.canUse` 共用 [AttackSkill.isWithinAttackRange](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/skill/AttackSkill.java)，按双方脚部三维距离不超过既有 2 格范围判断。该方法同时供普攻开始与激活检查使用；攻击半径、伤害、连段和冷却规则未调整。

已确认的源码原因：原版 `isWithinMeleeAttackRange` 以外扩后的实体碰撞框相交判断近战，和普攻技能的距离球不同；宽体、对角或有高度差的目标可能被 AI 选为普攻，但被技能拒绝，飞扑也被原版近战条件排除。战斗追击原先的 `closeEnoughDist=1` 又按脚部方块坐标的曼哈顿距离停止，实际脚部距离可能超过 2 格。现将仅用于战斗追击的停止容差设为 0，使飞扑冷却或弹道不可达时仍继续接近普攻范围；清理工作接近逻辑保持原值。

维护时不得再使用原版近战碰撞范围作为这两个自定义技能的分界，也不能把方块导航容差等同于技能真实距离。`canBeginCast` 只检查开始／覆盖资格，不能据此认定具体技能范围、冷却与瞄准均已通过；施放未开始时不进入冷却仍为原有规则。本轮编辑器、编译与 Data 的实际结果见[修改记录](changes.md)，尚未进行宽体目标、移动目标或障碍地形中的游戏验收。

### 清道夫清扫与重组的接近位置

2026-10-08 调整：AI 和指挥家持续工作共用 [SweeperWorkUtil](../../src/main/java/org/unitego/lobecorp/util/entity/ai/SweeperWorkUtil.java)。自动开始前要求双方水平碰撞框边缘间距不超过 0.5 格，同时满足原 `SweepSkill.isWithinRange`；未修改技能自身的最大施放范围、垂直判定、前后摇、动画或移动锁。水平距离按两轴分离距离的平方和计算，不能用外扩矩形替代圆形距离而放大对角间距。

已确认的源码原因：旧 AI 一进入原技能范围就清除导航，方块行走目标不能保证精确工作距离；技能未开始时没有重新定位分支。旧指挥家工作在施放返回 false 时直接永久跳过当前目标，也缺少最后一段精确接近。新的 `Approach` 分别由 AI 实例和指挥家工作实例持有，不持久化或同步。AI 在 Brain 推进后处理接近，清理残留 `WALK_TARGET` / `PATH`，避免核心寻路和随机闲逛覆盖精确移动；有清理目标时不启动闲逛。

站位先在目标周围选择，再复用 `ConductorMovement.resolve` 检查碰撞、支撑和路径；末段仅在 `safeApproach` 允许时直接推进 MoveControl。原技能距离判定失败、路径失败或停滞时换站位重算，导航拒绝某个候选时立即尝试其他候选。复用 10 tick 重试、40 tick 停滞和最多 3 次重算；定位仍失败时 AI 清除本次目标等待传感器重选，指挥家跳过该工作目标继续搜索。32 格工作搜索使用显式 64 格路径上限，避免清道夫默认 16 格 FOLLOW_RANGE 提前拒绝较远目标。

技能占用和冷却等待不计入移动停滞；事件拒绝等非位置原因不会驱动重定位。清道夫尸体在自动使用前再次检查生物质资格。其他指挥命令或待施放技能生效时 AI 只重置自己的接近状态，不停止指挥导航；自主接近与精确候选均遵守 `allowNavigation`，不绕过原活动范围或被动限制。技能运行中丢失位置仍先完成原后摇，解除移动锁后再接近。

实际检查：IDEA 相关源码无新增错误或警告，编译成功，Data 配置退出码 0，生成目录 25 个文件哈希不变；尚未进行游戏中的台阶、障碍物、移动目标或指挥切换验收。

## 实体状态、属性与状态效果

`EntityState` 由 ID 和可空的互斥组组成。同一非空组的状态互斥；添加状态会移除重复 ID
及冲突状态，并通过持有者更新不可变状态列表。女皇状态修改涉及坐姿尺寸时刷新碰撞尺寸，客户端同步更新也有对应刷新入口。

状态定义由 [TheQueenOfHatredStates](../../src/main/java/org/unitego/lobecorp/registry/entity/state/TheQueenOfHatredStates.java)
与 [SweeperStates](../../src/main/java/org/unitego/lobecorp/registry/entity/state/SweeperStates.java)
集中注册。状态用于表达实体事实，不能替代技能阶段、冷却或药水效果的各自权威数据。

`ENTITY_SKILL_COOLDOWN_MULTIPLIER` 和 `DAMAGE_TAKEN_MULTIPLIER` 是注册的可同步属性。伤害入口应用承伤倍率，技能入口应用冷却倍率；改变基础属性与添加修饰符是不同操作。

眩晕 I / II / III+ 分别通过属性修饰符减少 25% / 50% / 100% 的技能冷却速度和原版 `ATTACK_SPEED`。冷却速度的属性登记范围及小数计时见[技能系统](entity-skills.md)。原有移动速度修饰保留；固定技能前后摇和动画不按攻击速度重新缩放。

III+ 立即强制取消正在施放的技能并停止使用物品，拒绝新技能、攻击、物品使用、交互与挖掘；玩家攻击/使用键同时在客户端拦截，服务端入口再次拒绝。达到 III+ 且 Mob 原先启用了 AI 时才关闭 AI，并记录自己关闭过的对象。移除、到期或降为低等级时恢复这些对象的 AI；原版隐藏效果降级的恢复最多延后一 tick。不能把原本已经关闭 AI 的对象无条件重新启用。

### 女皇三个阶段

2026-10-18 已实现阶段数据与属性部分：`TheQueenOfHatred.getPhase()` 返回 1／2／3，实体同步字段、存档 `Phase` 和渲染票据 `QUEEN_PHASE` 均使用整数。不读取旧 `PhaseTwo`，不增加旧阶段迁移分支。

- 一阶段剩余生命不超过当前上限的 35% 时进入二阶段，保留原二阶段 25% 基础攻击提升。
- 受伤结算后仍存活、剩余生命不超过进入三阶段前上限的 10% 时直接进入三阶段；不播放转阶段动画，不拦截致死伤害。一次非致死伤害跨过两个阈值也可直接到三阶段。
- 三阶段使用稳定 ID `queen_phase_three_max_health` 的永久 `ADD_MULTIPLIED_TOTAL` 修饰符，将最终生命上限翻倍，仅在首次进入时回满。原版先加载属性再读取生命值，避免三阶段存档的血量被旧上限截断；加载已处于三阶段的存档不会再次回满或叠加修饰符。
- `isPhaseTwo()` 表示二阶段起的能力资格，包含三阶段；现有技能强化、动画与癫狂纹理继续沿此资格使用。本轮已确认三阶段暂沿用二阶段表现，未创建新资产。
- 玩家伤害免疫仅保留在一阶段。二阶段起的无差别目标与伤害资格由统一战斗关系系统提供；三阶段脱离指挥控制但保留队伍归属，原版生物仍按自身 AI 攻击／反击。

静态诊断、编译、Data 与运行验证的实际结果见[修改记录](changes.md)；代码落盘不代表游戏行为已验收。

### 仇恨、群体与阵营

女皇、清道夫与指挥家共用独立的 [战斗关系系统](combat.md)。阵营／群体归属、受击仇恨、共享目标和攻击／伤害资格经统一 API 访问；原版 Team、驯服归属、Sensor／Goal／Brain 与守望者愤怒保留适配，清道夫 Boids 不变。

女皇二阶段继续接受指挥但允许误伤友军；三阶段撤销控制意图并恢复自主 AI，保留当前队伍归属。规则、公开入口、非持久化仇恨及新存档格式见系统文档；旧指挥家目录／单位附件不迁移。

### 清道夫飞扑低弧轨迹

2026-10-10 按已确认方案实现：原版长跳式运动、低弧猛扑、起跳锁定目标当前位置、保留 5 tick（0.25 秒）前摇。飞扑不设固定最小／最大仰角；头部与身体继续按实际速度方向限速转向。12 格最大水平范围、1.8 格/tick 水平初速度上限、1.4 格/tick 垂直初速度上限，以及原冷却、伤害与抓取规则保留。

[EntityLeapUtil](../../src/main/java/org/unitego/lobecorp/util/entity/EntityLeapUtil.java) 只求解先位移、后扣重力的无阻力离散轨迹。弧顶高于起点与真实落点中较高的一端 1～2.5 格，期望弧高随水平距离增长；枚举允许的整数飞行 tick，选择弧高最接近期望值且速度与下降落地条件均满足的解。无解时拒绝施放，不通过裁剪初速度破坏已求轨迹。

[LeapSkill](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/skill/LeapSkill.java) 以目标当前脚底 XZ 为落点水平坐标，在脚底上下各 1 格内寻找可站立支撑面；预检整条轨迹的实体碰撞框和落地支撑。求解终点向支撑面内探入 0.01 格，使最后一个 tick 触发原版碰撞落地，避免理论终点恰好贴地却继续滑行。这个探入量不计入弧高基准。

`canUse` 与瞄准准备都要求有可达解，前摇期间和起跳时重新检查目标、落点及轨迹。起跳这一刻锁定落点并只赋予一次初速度，飞行中不跟踪或预判目标；目标可通过移动躲避。客户端动作仍复用技能同步，三阶段时序见[动画](animation.md#清道夫飞扑三阶段)。

飞行期间保存 runtime 中原有 `discardFriction` 标记并临时设为 true，保留 `getGravity()` 与原版碰撞；`Sweeper.travel` 仅在飞扑 ACTIVE 阶段向父类传 `Vec3.ZERO`，抑制移动输入加速度而不强写位置。结束、取消和后摇结束均幂等恢复原阻力值；实体卸载沿现有运行态取消链恢复，不无条件写 false。现有超时下降保护保留，正常轨迹由起跳初速与重力推进。

模型以恒重力空气运动为前提：施放与预检排除流体、攀爬、漂浮、缓降；无重力使 `getGravity()` 为零，由求解器拒绝。途中碰撞框内的流体、攀爬方块、蛛网、甜浆果丛、细雪，以及经 `MOVEMENT_EFFICIENCY` 修正后实际方块速度因子不为 1 的路径均拒绝。预检不能保证第三方运行时副作用或施放后环境变化；原版小于 0.003 的速度分量归零也属于精度边界，不承诺实际落点与数学结果完全精确。

已确认的源码原因：旧模型水平速度每 tick 保留 0.91，约 10 tick 后只剩 39%，6～10 tick 的求解时间让同高轨迹弧顶较低；先求解再裁剪初速也可能失去可达性。新实现联合选择弧高、整数时间和受限初速，维护时必须同时保留物理前提、路径复查与终止清理，不能只调整公式或重新加入固定仰角限制。

飞行接触仍通过独立命中框结算当前攻击属性的 15%，同次飞扑每个生物仅一次。高宽均不超过 2.5 格的有效敌人可被同时抓取；落地、结束或取消解除，原落地范围伤害保留。工具接口、3 格瞬移脱离及非持久化边界见[实体抓取](entity-grab.md)。

数学回归已写为 [EntityLeapUtilTest](../../src/test/java/org/unitego/lobecorp/util/entity/EntityLeapUtilTest.java) 的 JUnit 测试，本次未执行测试；编辑器、编译及 Data 的实际结果统一见[修改记录](changes.md)。未进行游戏中的飞行手感、台阶／障碍、移动目标或多人时序验收。

已确认的既有维护问题：眩晕关闭 AI 后 `NoAI=true` 会进入存档，卸载回调只移除内存记录；重载的新对象不在记录集合中，效果结束后可能无法恢复 AI。本轮只记录，未扩大修改此持久化行为；不能将本轮动作守卫或编辑器检查当作该问题已修复。

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
- 2026-10-05 初次核对没有修改配置、资源、存档格式或生成输出；新增翻译键仍必须通过 IDEA Data 生成。

## 修改前核对点

1. 核对实体、AI、技能、碰撞和动画的所有共享调用点，确认状态属于 AI 私有决策还是共享事实。
2. 核对目标记忆的写入、失效、死亡、卸载与指挥家接管路径。
3. 转向复用 [EntityFacingUtil](../../src/main/java/org/unitego/lobecorp/util/entity/EntityFacingUtil.java)
   ，头和身体同时转向，保留限速，不写引擎插值旧值。
4. 核对状态互斥、尺寸刷新及客户端同步；临时修饰符与永久修饰符分别处理。
5. 保留唯一事件入口、总线、优先级及取消语义，遵循[事件规范](../event-registration.md)。

## 核对范围与尚未验收

本页核对了表列源码及行为、传感器、状态注册和附件入口；没有逐一核对所有颜色考验的完整行为、投射物实现、原版寻路内部、存档往返和多人断线重连。AI
时序、边缘寻路、尸体复原和眩晕恢复均尚未做游戏运行验收。

维护时先用本文定位入口，再通过 IDEA
核对当前实现；文档缺项不代表功能不存在。包迁移完成后更新目标链接与核对状态。修改记录统一写入[系统维护记录](changes.md)。
