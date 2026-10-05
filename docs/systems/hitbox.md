# 命中框

> 核对日期：2026-10-05。本文按 IDEA 读取的迁移前源码说明实际实现，不代表游戏运行验收。
> 包迁移按[已批准方案](../java-package-organization-plan.md)完成，链接已按实际源码路径核对；结果见[修改记录](changes.md)。

## 职责与公开入口

命中框在服务端负责空间筛选和效果结算，客户端快照用于观察及调试。几何、模板、每次实例、管理与成功策略彼此分工；粒子和模型形状不自动决定实际命中范围。技能生命周期见[实体技能](entity-skills.md)。

| 职责             | 核对入口                                                                                                                                                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 几何及尺寸类型   | [HitboxGeometry](../../src/main/java/org/unitego/lobecorp/world/hitbox/geometry/HitboxGeometry.java)、[HitboxSize](../../src/main/java/org/unitego/lobecorp/world/hitbox/geometry/HitboxSize.java)、[HitboxShapeType](../../src/main/java/org/unitego/lobecorp/world/hitbox/geometry/HitboxShapeType.java) |
| 共享定义         | [HitboxTemplate](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxTemplate.java)                                                                                                                                                                                                                |
| 每次运行数据     | [HitboxInstance](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxInstance.java)、[HitboxLevelData](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxLevelData.java)                                                                                                                 |
| 创建、结算和移除 | [HitboxManager](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxManager.java)                                                                                                                                                                                                                  |
| 成功与视线规则   | [HitboxHitPolicy](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxHitPolicy.java)、[HitboxHitMode](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxHitMode.java)、[HitboxLineOfSightMode](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxLineOfSightMode.java)        |
| 效果回调         | [IHitboxEffect](../../src/main/java/org/unitego/lobecorp/world/hitbox/IHitboxEffect.java)、[HitboxEffectContext](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxEffectContext.java)                                                                                                           |
| 客户端镜像       | [HitboxSnapshot](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxSnapshot.java)、[HitboxDebugRenderer](../../src/main/java/org/unitego/lobecorp/client/hitbox/HitboxDebugRenderer.java)                                                                                                        |
| 事件调度         | [WorldTickEvents](../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java)、[HitboxEvents](../../src/main/java/org/unitego/lobecorp/events/HitboxEvents.java)                                                                                                                                 |

几何能力包括箱体、球体、椭球、圆柱、圆锥及环形、扇形和箱环柱体。尺寸类型及转换以具体 `HitboxSize` 实现为准；`HitboxGeometry`
同时提供粗筛包围盒和精确相交判断，不应通过包围盒命中直接替代精确判断。

`HitboxTemplate` 固定初始尺寸、目标过滤、效果回调和用途。实例设置尺寸时不能更换模板的形状类型。模板没有单独的注册或网络协议；实例保存位置、旋转、局部偏移、旋转中心、来源、跟随、成功记录和生命周期。

## 创建、推进与清理

`HitboxManager.create` 加入维度管理器并立即发送一个尚未激活的快照。需要结算时显式激活；寿命包括预览时间，必须为正数。命中框没有沿用粒子的“寿命
0 表示无限”规则。

每个服务端 LevelTick.Post 的处理顺序：

1. 技能效果先更新，然后命中框入口开始推进。
2. 从当前实例列表的副本遍历，移除寿命到期或来源无效的实例。
3. 更新跟随位置和朝向。
4. 对已激活且未耗尽的实例进行目标筛选及精确结算。
5. 对仍存在的实例发送更新快照，再推进寿命。

寿命为 1 的实例可在一个管理器 tick 中结算，下一次到期检查移除。实例关联的来源、跟随与技能运行绑定分别设置；即使没有启用跟随，存在无效来源也会触发来源清理。来源死亡、移除或离开维度都需要核对。

跟随支持局部偏移和来源旋转，以及可选头部旋转。修改坐标系前同时检查局部偏移、旋转中心、几何长轴与客户端快照，不应仅旋转可视模型。

技能结束或取消时通过 `removeForSkillRuntime` 移除绑定到该次运行的实例。手动 `remove`
从维度容器删除、标记移除并发送移除载荷。无来源实例达到总成功上限时立即移除；有来源实例会标记耗尽，保留到正常寿命结束以支持剩余表现。

## 目标筛选与成功策略

结算链为粗筛包围盒 → 模板及实例过滤 → 按目标中心距离排序 → 目标成功次数/间隔资格 → 精确相交 → 视线 → 效果回调 → 成功记账。

效果回调返回 `true` 才记录成功、消耗次数并影响下一次允许时间。被拒绝的伤害或失败效果不会自动算命中，因此伤害类回调应返回实际结算结果。

| 模式       | 重复处理含义                           |
|------------|----------------------------------------|
| ONCE       | 每个目标仅成功一次                     |
| EVERY_TICK | 每 tick 可以再次成功，仍受次数上限约束 |
| INTERVAL   | 按正数 tick 间隔再次成功               |

每目标上限与总成功上限独立。默认 `ONCE_PER_TARGET` 每个目标成功一次、总次数不限制。修改策略时同时核对失败回调、不同目标排序、间隔及耗尽路径，不能只检查枚举名称。

视线可选择不限制、从命中框位置检测或实体到实体检测。一般检测对目标包围盒取样，实体到实体模式也对来源包围盒取样；任意采样连线不被碰撞方块阻挡即满足资格，流体不作为该检测的遮挡。它不是纹理透明度或渲染深度判断。

## 快照、网络与调试

[HitboxSnapshot](../../src/main/java/org/unitego/lobecorp/world/hitbox/HitboxSnapshot.java)
传递几何、变换、用途、阶段标记及调试关联信息；不会向客户端发送服务端过滤器或效果回调。

- [HitboxCreatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxCreatePayload.java)建立客户端镜像。
- [HitboxUpdatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxUpdatePayload.java)更新快照和最后更新时间。
- [HitboxRemovePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxRemovePayload.java)按编号删除镜像。

实例编号以 Level 容器为范围。服务端实例和客户端快照容器分开；未及时更新的客户端快照还有过期清理。存在来源时发送给跟踪来源实体的玩家，无来源时发送给固定范围内玩家。该同步是服务端结果的镜像，客户端不能据此结算伤害。

Level 数据由 [LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java)
注册，不作为世界存档中的持续命中框数据。改变快照字段需同时修改编解码、三个载荷、客户端存储和调试渲染；不要另建平行 Payload
分类。

## 女皇星束的已确认约束

[StarBeamSkill](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/skill/StarBeamSkill.java)
复用现有管理器结算，规则为：

- 射程 15 格，完整直径 0.5 格；圆柱半径由直径的一半确定。
- 先通过方块碰撞截断终点，再以实际长度构造单 tick 命中框。
- 穿透多个有效敌人，每个目标最多成功一次，不用第一个实体截断光束。
- 使用技能自身敌对资格及实际伤害结果，成功后记录运行实例成功。
- 已进行终点方块截断，因此该实例使用不额外限制视线的模式。

发射位置、终点计算和旋转来自技能及 [QueenSkillUtil](../../src/main/java/org/unitego/lobecorp/util/entity/skill/QueenSkillUtil.java)
；粒子广播显示光束，服务端圆柱负责命中。修改射程、直径、方块截断、穿透、瞬时结算及重复规则前必须得到明确授权。

## 复用与修改前核对

新增空间效果优先组合模板、实例、成功策略与技能绑定。轻量技能效果可以持有或更新命中框，但两者不是同一对象；详情见[实体技能](entity-skills.md)
。持续场需要选择明确的正数寿命与刷新策略，不能无依据把无限技能的 -1 直接传入命中框。

修改前核对尺寸类型、坐标单位、旋转方向、中心偏移、激活时刻、预览计时、跟随、来源失效、技能结束、所有成功上限和视线。几何或快照变化还需核对客户端显示和实际服务端判断的一致性。

本轮没有修改命中框配置、网络协议、资源或生成输出。资源特效参数与伤害范围没有自动绑定关系；若需要绑定，应先检查已有公开能力和项目锁定版本。

## 核对范围与尚未验收

已通过 IDEA
核对核心模板、实例、管理器、几何入口、策略、快照、载荷、调度与星束实现。未逐种形状核验数值精度、旋转边界、服务器性能、多玩家观察切换或网络丢包；也没有游戏运行验收。迁移后链接仍需统一检查。维护记录见[系统维护记录](changes.md)。
