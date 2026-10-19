# 仇恨、群体与阵营

2026-10-18 实现；当前源码与 IDEA 为权威。编译、Data 和运行验收分别见[修改记录](changes.md)。

## 系统边界

[CombatData](../../src/main/java/org/unitego/lobecorp/world/entity/combat/CombatData.java) 独立持有阵营敌对关系与成员的阵营／群体归属；[CombatMemory](../../src/main/java/org/unitego/lobecorp/world/entity/combat/CombatMemory.java) 保存受击仇恨和共享目标。模块不依赖女皇、清道夫或指挥家内部实现。实体通过 [ICombatant](../../src/main/java/org/unitego/lobecorp/world/entity/combat/ICombatant.java) 声明自然关系、目标策略和控制资格。

指挥家继续负责命令、行为范围、导航、技能提交及目录展示；清道夫 Boids 和各实体技能时序不迁入战斗关系系统。频繁使用的规则统一通过 [CombatUtil](../../src/main/java/org/unitego/lobecorp/util/entity/combat/CombatUtil.java) 访问，调用方不直接读写附件。

## 公开入口

| API | 契约 |
|---|---|
| `isAllied` | 查询实际阵营、自然友军、原版 Team 和驯服根拥有者关系；不因狂暴而删除归属。 |
| `canTarget` | 排除自身、跨世界、死亡、不可攻击对象及创造／旁观玩家，再应用友军与无差别攻击规则。客户端重载接收目录中的阵营快照，服务端重新查询权威数据。 |
| `wantsTarget` | 组合攻击资格与实体策略：女皇一阶段寻找 Enemy 或已记录攻击者，二阶段起无差别；清道夫寻找非友军。 |
| `canHarm` | 统一参与者的友军伤害资格；纯原版实体间不增加伤害拦截，原版伤害类型、免疫、无敌时间和实际减伤仍由引擎处理。 |
| `recordAttack` | 记录服务端受击仇恨，不引入分值、衰减或新的切换门槛。 |
| `findSharedTarget`／`rememberSharedTarget`／`isSharedTarget` | 16 格内在同阵营、同群体成员之间延续已有共享目标及距离选择；消费者传入自身行为范围条件。 |
| `acceptsControl`／`participates` | 查询控制资格和系统参与范围，不修改队伍或状态。 |
| `clearRuntime` | 清理不持久化的仇恨与共享目标。 |

阵营和群体是不同字段；当前指挥家把队伍映射到同名阵营与群体，使用 `conductor/` 前缀隔离名称。清道夫自然友军继续使用既有靛蓝系类型／标签规则。原版友军伤害开关保留；模组明确的阵营保护和女皇无差别策略由统一入口处理。

## 女皇与控制

- 二阶段阈值为 35%，三阶段阈值为进入前生命上限的 10%；受伤后仍存活才可直接进入三阶段，致死伤害不拦截。三阶段永久生命修饰符将最终上限翻倍，仅进入时回满，加载不重复治疗。
- 二阶段起可选择并伤害任意有效生物，包括友军；其他生物也可按原版攻击／反击规则攻击她。没有给动物、村民等添加主动战斗 AI，也不绕过创造／旁观模式等引擎资格。
- 二阶段继续遵守指挥家的命令、主动／中立／被动行为及范围约束，技能范围效果允许误伤友军。
- 三阶段 `acceptsControl=false`。进入边沿调用 `ConductorController.detachControl`，撤销指令、移动及等待中的手动施放，释放激光手动接管，保留已开始的普通技能。
- 队伍、阵营与群体归属保留；目录仍能展示她。命令、移动约束、Brain 目标守卫及技能守卫均按控制资格放行自主 AI／拒绝外部控制。未加载单位的远程意图在加载后重新检查，不能重新控制三阶段女皇。
- 三阶段暂沿用二阶段外观、动画与技能；没有新增转阶段动画或资源。

## 原版与事件适配

`EntityMixin` 仅对 ICombatant 或已编入战斗阵营的实体，在原版 `isAlliedTo(Entity)` 返回处合并关系；纯原版实体对保留原结果，涉及女皇二阶段起时允许突破友军身份的攻击限制。原版宠物拥有者关系只作为参与者关系的输入，不给纯原版实体新增伤害保护。关系查询不反调该桥接方法，避免递归。Sensor、Goal、Brain 和守望者现有桥接继续调用目标资格，守望者保留原版愤怒和战斗行为。

事件仍沿用唯一订阅：`ConductorGameplayEvents.onTargetChange` 先调用 `CombatEvents` 的关系守卫，再调用指挥家控制守卫；`LivingEntityEvents.onLivingIncomingDamage` 先应用承伤倍率，再处理关系伤害过滤；伤害 Post 记录受击仇恨并继续原伤害粒子。清道夫完全由生物质吸收的伤害也经原回调记录仇恨。没有重复订阅或新增战斗 Payload。

## 存储与生命周期

| 数据 | 位置与生命周期 |
|---|---|
| 阵营／群体 | Overworld SavedData `lobecorp:combat_relations`；保存敌对关系及 UUID 归属，卸载保留。 |
| 控制目录 | 新 SavedData `lobecorp:conductor_control`；只保存队伍的颜色／强加载设置、单位控制摘要、远程交付与修订号。Team 是合并战斗关系后的同步 DTO。 |
| 单位控制 | 新实体附件 `lobecorp:conductor_control_unit`；持续意图持久化，加载复核资格。 |
| 仇恨／共享目标 | `lobecorp:combat_memory` 非持久化附件；离开世界清理，不保存实体强引用，不发送同步。 |

按用户确认不读取、不迁移旧 `conductor_directory`／`conductor_unit`，不删除旧文件；旧队伍需要重新建立。已有指挥家能力资格附件保持。网络协议升至 20，继续使用既有目录／附件快照。

## 检查边界

已通过 IDEA 检查新模块类型、导入、调用和原入口；格式化仅覆盖修改行。没有执行游戏、联机、存档往返或动画视觉验收。大型修改编译／Data 的最终结果以修改记录为准，不用前轮阶段修改的成功结果替代。
