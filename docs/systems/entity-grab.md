# 实体抓取

> 实现日期：2026-10-07。源码与 IDEA 诊断已核对；编译、Data 和游戏表现以 [修改记录](changes.md) 的实际结果为准。

## 职责与公开入口

[EntityGrabUtil](../../src/main/java/org/unitego/lobecorp/util/entity/EntityGrabUtil.java) 提供独立的临时抓取能力，不依赖指挥家、具体技能或动画。
[EntityGrabState](../../src/main/java/org/unitego/lobecorp/world/entity/control/EntityGrabState.java) 保存实体运行态，由
[LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java) 的 `ENTITY_GRAB` 附件承载。

| 接口                                           | 含义                               |
|------------------------------------------------|------------------------------------|
| `grab(source, target, escapeDistance)`         | 使用默认零偏移抓取目标             |
| `grab(source, target, offset, escapeDistance)` | 使用指定世界轴相对位移抓取目标     |
| `release(source, target)`                      | 只释放属于该抓取者的指定目标       |
| `releaseAll(source)`                           | 释放该抓取者当前持有的全部目标     |
| `clear(entity)`                                | 清理该实体作为抓取者和被抓者的关系 |
| `tick(entity)`                                 | 检查实际位置、来源资格，并更新锚点 |
| `restrictMovement(entity, movement)`           | 在普通运动入口应用移动锁           |

抓取只限制移动，不禁止攻击、使用物品或转向，也不关闭 AI。一个目标同时只有一个抓取者；一个抓取者可以持有多个目标。
工具拒绝自身抓取、跨维度抓取、无效实体、非法参数与循环抓取关系。体型和敌对资格由调用方定义。

## 坐标、脱离与生命周期

目标锚点是 `source.position() + offset`，基准为抓取者脚部；非零偏移按世界 X/Y/Z 轴解释，不随身体或头部旋转。
默认偏移为 `Vec3.ZERO`。不能用法杖的 `EntityFacingUtil.localPosition` 替代：该方法绕眼部旋转，俯仰不为零时会改变零偏移的脚部基准。

更新顺序必须先比较目标实际坐标与**上一次已经固定的锚点**，再写入新锚点。当距离达到调用方的脱离阈值时释放目标，保留其当前位置。
使用旧锚点避免抓取者正常飞行被错误解释成目标逃脱；在钉回之前检查避免把瞬移目标拉回。

普通移动经 [EntityMixin](../../src/main/java/org/unitego/lobecorp/mixin/entity/EntityMixin.java) 的既有 `Entity.move` 拦截入口限制。
直接传送不被该入口禁止；实际位置变化才决定能否脱离。不能只监听 `EntityTeleportEvent`：该事件在传送前发布，部分直接传送及末影珍珠非玩家分支也没有对应事件。
玩家锚点更新使用原版 `ServerPlayer.teleportTo` 的连接同步，普通生物使用当前位置更新；固定时清零速度与跌落累计。

[LivingEntityEvents](../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java) 在技能推进后的实体 Post tick 更新抓取关系，同时更新该实体持有的目标，避免实体推进顺序让锚点落后一 tick。
来源或目标死亡、移除及离开世界会释放关系。抓取开始、结束同步附件事实；历史锚点、脱离阈值和持有目标集合仅保存在服务端。
附件不配置存档序列化或死亡复制，不把抓取写入世界存档。客户端依此附件限制普通移动，伤害和关系变更仍由服务端决定。

## 清道夫飞扑接入

[LeapSkill](../../src/main/java/org/unitego/lobecorp/world/entity/ordeal/indigo/skill/LeapSkill.java) 使用现有命中框系统处理飞行接触，保持原落地范围伤害与眩晕规则。

- 朝向与起跳弹道不设固定仰角限制；朝向跟随实际速度，复用受限头身转向，保留旧角度插值。
- 飞行伤害与抓取共用独立判断框，在清道夫当前宽、高、深基础上每侧外扩 0.3 格，即各完整边长增加 0.6 格；不修改实体本身的受击框或碰撞框。接触有效敌人时先尝试当前攻击属性 15% 的基础伤害，再按体型资格抓取；抓取不依赖伤害成功，伤害无敌帧不会阻止抓取。
- 本次施放单独保存接触 UUID，即使伤害被拒绝或目标摆脱抓取，也不重复处理该生物。
- 实际宽度和高度均不大于 2.5 格的目标可以抓取；没有总目标数量限制。
- 使用默认零偏移；被抓者偏离上次锚点达到 3 格即可摆脱，同一次飞扑不再抓回。
- 落地进入后摇时立即释放；取消、后摇结束及实体卸载重复清理均安全。
- 飞行命中框与落地命中框独立，飞行框绑定本次运行实例，结束时移除。

飞行伤害直接使用攻击属性乘比例进入服务端伤害管线。不能把 `0.15` 直接传给清道夫旧的攻击修饰量接口：该接口使用
`ADD_MULTIPLIED_TOTAL`，参数 `0.15` 表示增加 15%，会变成 115% 而非 15%。此次未改变旧的落地攻击修饰量。

## 检查与已知限制

此实现没有新增独立 Payload、动画状态或命中框类型；复用附件、技能生命周期和既有运动入口。
尚未进行游戏验收，不能声称多人移动插值、玩家连接确认、碰撞交叠或瞬移交互已通过运行检查。
命中框按现有管理器逐 tick 的当前位置检测，不新增连续扫掠判定。技能时序、冷却和原落地伤害配置保持原有定义。
