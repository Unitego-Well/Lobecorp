# 指挥家系统

> 源码核对日期：2026-10-05。核对范围包括目录、附件、控制能力、命令与快照载荷、区块管理、事件、HUD 和输入入口；通过 IDEA
> 读取当前实现，未进行游戏、多人服务器、重启恢复或性能实测。
> 包整理方案 1 已执行，源码链接按实际路径核对；保留三个指挥家客户端关联类的原包，详情见修改记录。

## 作用与边界

2026-10-18 起，阵营敌对关系、群体归属及共享仇恨由独立的 [战斗关系系统](combat.md) 管理。指挥家只持有控制与显示设置，目标／伤害资格统一访问 CombatUtil；三阶段女皇不再接受控制，但队伍归属保留。

当前控制目录为 `lobecorp:conductor_control`，单位附件为 `lobecorp:conductor_control_unit`；均采用新键，不读取或迁移旧目录／单位数据，旧队伍需要重建。能力资格附件保留，目录快照结构继续兼容本版本客户端；网络协议为 20。下文历史核对范围与运行限制保留，新增接口与存储职责以战斗关系页和当前源码为准。

指挥家管理队伍、单位的持续控制意图、远程目录和客户端操作。服务器保存权威状态；HUD、相机及选择集合只保存客户端会话。指挥能力负责接收操作，实体技能系统负责技能生命周期、冷却、动画和效果，不创建第二套技能状态机。

| 层次       | 权威来源与职责                                                                                                                                                                                             | 生命周期                               |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| 世界目录   | [ConductorData](../../src/main/java/org/unitego/lobecorp/conductor/data/ConductorData.java)：队伍、成员摘要、最后已知信息、待交付修改、释放记录和修订号                                                    | Overworld 的 SavedData；跨维度查找单位 |
| 单位状态   | [ConductorUnitData](../../src/main/java/org/unitego/lobecorp/conductor/data/ConductorUnitData.java)：队伍、行为、活动模式、命令、目标、目的地、活动原点、移动结果与修订号                                  | 实体持久化并同步的附件                 |
| 单位运行态 | [ConductorUnitRuntime](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorUnitRuntime.java)：寻路进度、工作状态、待施放、声波和临时目标                                                   | 非持久化附件；加载后重建               |
| 世界运行态 | [ConductorWorldRuntime](../../src/main/java/org/unitego/lobecorp/conductor/world/ConductorWorldRuntime.java)：票据、观察会话、各玩家快照基线及技能订阅                                                     | 不写入目录 Codec                       |
| 客户端目录 | [ConductorDirectory](../../src/main/java/org/unitego/lobecorp/conductor/data/ConductorDirectory.java) 与 [ConductorClient](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorClient.java) | 收包更新；断线或换世界重置             |

目录摘要不代替已加载实体的附件。查询能力或资格不会自动编队；只有明确的加入操作调用 `ConductorData.join`。修改已加载单位时优先使用
`update`，直接修改附件字段后必须 `commit(mob)`，以更新摘要、发送单位变化字段并标记 SavedData dirty。

## 关键源码与复用入口

| 需求                                   | 入口                                                                                                                                                                                                                                                                                                                 | 使用约束                                     |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| 查询能力、接收命令、移动回调、技能施放 | [ConductorUtil](../../src/main/java/org/unitego/lobecorp/util/conductor/ConductorUtil.java)、[IConductorUnitControl](../../src/main/java/org/unitego/lobecorp/conductor/control/IConductorUnitControl.java)                                                                                                          | 每次查询能力，不长期缓存 Provider 返回对象   |
| 读写与清理指挥附件                     | [ConductorAttachmentUtil](../../src/main/java/org/unitego/lobecorp/util/conductor/ConductorAttachmentUtil.java)                                                                                                                                                                                                      | 业务调用方复用统一入口，避免重复直接附件操作 |
| 控制实现与特殊实体适配                 | [ConductorCapabilities](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorCapabilities.java)、[ConductorMobAdapters](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorMobAdapters.java)                                                                                         | 普通 Mob 兜底，特殊移动或工作通过适配器实现  |
| 指令推进与瞄准后施放                   | [ConductorController](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorController.java)                                                                                                                                                                                                           | 保持命令顺序、AI 资格及技能转向规则          |
| 移动、方向攻击、清理和重组             | [ConductorMovement](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorMovement.java)、[ConductorPointAttack](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorPointAttack.java)、[ConductorWork](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorWork.java) | 复用实体导航；清道夫工作仍由技能驱动         |
| 技能展示、目标规则与预览               | [EntitySkillConductorAbility](../../src/main/java/org/unitego/lobecorp/conductor/ability/EntitySkillConductorAbility.java)、[ConductorTargetingResolver](../../src/main/java/org/unitego/lobecorp/conductor/ability/ConductorTargetingResolver.java)                                                                 | 包装现有实体技能定义，使用既有冷却来源       |
| 能力注册与资格定义                     | [ConductorCapabilityRegistration](../../src/main/java/org/unitego/lobecorp/registry/entity/ConductorCapabilityRegistration.java)、[RegisterConductorAbilities](../../src/main/java/org/unitego/lobecorp/registry/entity/RegisterConductorAbilities.java)                                                             | 由 events 唯一入口分发，不重复注册监听       |

`IConductorUnitControl` 提供 `supports`、`accept`、`tick`、`stop`、`prepareMovement`、`advanceMovement`、`movementStopped`、
`movementArrived`、`abilities` 和 `cast`。通用控制、清道夫、监守者及其他适配器按现有接口组合，不在调用方重新实现控制状态。

能力注册通过 [ConductorCapabilityEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorCapabilityEvents.java)
的 `LOWEST` 入口执行。实体技能的 EntityCapability、指挥单位控制的 EntityCapability 与 `IConductorAbility`
技能包装是三个不同边界；新增能力先检查现有 Provider 和定义，不能把包装资格数据与实体技能所有权混用。

## 执行链与状态清理

### 加入、修改与恢复

1. 客户端选择单位并提交命令；服务器从目录、实体及能力读取状态，按当前命令分支检查单位是否存在、实体支持情况、目标或施放资格。
2. 明确编队调用 `join`；已加载单位通过 `accept` 写入附件，`commit` 更新目录与同步。客户端不能把显示摘要直接写回服务器附件。
3. 未加载单位的 `update` 基于目录摘要生成带修订号的 `Pending`；后续修改覆盖最新意图，不追加动作队列。未加载单位释放时留下
   tombstone，待实体加载后清理附件。
4. [ConductorGameplayEvents](../../src/main/java/org/unitego/lobecorp/events/ConductorGameplayEvents.java) 的实体加入事件调用
   `attach`：先处理释放或较新的待交付状态，再核对队伍和能力，清理运行附件并恢复持续控制。
5. `attach` 清除方向攻击或带 `commandedSkill` 的一次性意图，避免加载后重播技能；移动、返回、追敌与工作按现有导航及搜索逻辑重建。

单位默认状态为中立战斗行为、驻守活动模式、自动攻击及无命令。`CombatBehavior`、`BehaviorState`、`AttackState` 与 `OrderType`
分别描述行为、活动范围、攻击接管及执行意图；它们不是一套可以互换的枚举。

### 移动、攻击与手动技能

控制器处理待施放后，再推进移动/返回、方向攻击、实体攻击、被动行为和自主目标选择。特殊实体通过能力回调选择移动实现；移动结果区分到达、不可达与取消。需要调整寻路时同时核对原版导航桥接和
`ConductorMovement`，避免只改 HUD 预览。

实体技能包装最终复用 [EntitySkillConductorAbility](../../src/main/java/org/unitego/lobecorp/conductor/ability/EntitySkillConductorAbility.java)
与 [IEntitySkillAccess](../../src/main/java/org/unitego/lobecorp/world/entity/skill/IEntitySkillAccess.java)
。指挥施放先瞄准到位再进入技能前摇与冷却；连续技能的运行阶段、命中框和同步交由 [实体与技能系统](entity-skills.md) 说明。

[ConductorManualSkillControl](../../src/main/java/org/unitego/lobecorp/client/conductor/input/ConductorManualSkillControl.java)
以单位 UUID 与技能 ID
记录手动会话。只有选中且仍在施放的单位能够接管；按住左键更新有效实体或位置目标，松开左键暂停跟随瞄准，取消选中发送结束控制以恢复生物目标。界面占用、失去窗口焦点、单位失效或开始同步超时都会走对应停止/移除路径。
`AIM` 与 `END_MANUAL_CONTROL` 的服务端处理仍需检查当前激光能力和目标选择，不把客户端鼠标状态当成权威。

### 清道夫持续清理与重组

[ConductorWork](../../src/main/java/org/unitego/lobecorp/conductor/control/ConductorWork.java) 在指挥工作生效时独立推进，清道夫 Brain 不运行。2026-10-08 起与自主 AI 共用 [SweeperWorkUtil](../../src/main/java/org/unitego/lobecorp/util/entity/ai/SweeperWorkUtil.java) 的站位和接近逻辑：自动开始需水平碰撞框边缘间距不超过 0.5 格并满足原技能范围，路径末段使用安全的精确 MoveControl 移动。

位置判定失败、寻路失败或停滞时保留当前目标并尝试其他站位；用完重算次数才加入本次工作的跳过集合。冷却、技能占用或事件拒绝不再因施放返回 false 而永久跳过有效目标，施放请求按 10 tick 间隔重试；技能占用和到位等待不计入移动停滞。运行状态仍由原非持久化工作附件持有，持续指令、技能冷却、存档及网络协议不变。根因、距离计算及实际检查见[实体与 AI](entities-ai.md#清道夫清扫与重组的接近位置)，实际游戏效果尚未验收。

### 卸载、永久移除与客户端退出

[LivingEntityEvents](../../src/main/java/org/unitego/lobecorp/events/LivingEntityEvents.java) 的离开世界入口先清理实体技能运行态，再停止指挥控制并
`detach`。卸载保留目录成员；`KILLED` 或 `DISCARDED` 视为永久移除，清理单位、成员信息及待交付记录。修改该链路时保留先停止控制、再移除附件的顺序。

[ClientRuntimeEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java) 在登出和客户端世界卸载时调用
`ConductorControls.clearSession`。该入口退出控制模式、清理选择/跟随/待命令/手动技能会话、关闭相机并重置客户端目录；HUD
偏好文件不随会话删除。头像缓存的 `clear` 与 `beginFrame` 按客户端渲染实现维护，新增缓存应接入明确的失效路径。

## 远程区块与目录同步

[ConductorChunkLoading](../../src/main/java/org/unitego/lobecorp/conductor/world/ConductorChunkLoading.java)
由 [WorldTickEvents](../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java) 的服务端 tick 入口调用：维护队伍
`forceLoad`
单位票据，刷新位置并周期发送目录与订阅的技能资料。[ConductorView](../../src/main/java/org/unitego/lobecorp/conductor/world/ConductorView.java)
保存临时观察位置，检查玩家存活、维度、有限坐标、世界边界和超时，维护观察区块票据并更新区块跟踪。

观察位置不改变玩家本体坐标。客户端远程区块缓存见 [ConductorClientChunks](../../src/main/java/org/unitego/lobecorp/client/conductor/world/ConductorClientChunks.java)、[ConductorChunkCacheAccess](../../src/main/java/org/unitego/lobecorp/client/conductor/world/ConductorChunkCacheAccess.java)
；服务端及客户端 Mixin 桥接必须与观察生命周期一起检查。

[ConductorTicketControllers](../../src/main/java/org/unitego/lobecorp/registry/conductor/ConductorTicketControllers.java)
注册 `lobecorp:conductor_directory_units` 和 `lobecorp:conductor_views`
。启动时移除这两个控制器遗留票据；单位保持加载按目录重建，观察票据只属于当前会话。退出观察、玩家失效、换维度或超过观察更新等待时间时释放对应票据。

| 协议                                                                                                          | 方向            | 职责                                                   |
|---------------------------------------------------------------------------------------------------------------|-----------------|--------------------------------------------------------|
| [ConductorCommandPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ConductorCommandPayload.java)   | 客户端 → 服务端 | 编队、释放、模式、移动、攻击、技能、手动瞄准及快照请求 |
| [ConductorViewPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ConductorViewPayload.java)         | 客户端 → 服务端 | 观察激活状态与位置                                     |
| [ConductorSnapshotPayload](../../src/main/java/org/unitego/lobecorp/network/tc/ConductorSnapshotPayload.java) | 服务端 → 客户端 | 全量/增量目录、成员信息与技能显示资料                  |
| [单位数据包](../../src/main/java/org/unitego/lobecorp/network/tc/ConductorEntityDataSyncPayload.java)              | 服务端 → 客户端       | 已加载实体的单位状态及能力资格，首次完整、后续增量                              |

首次或恢复请求发送全量目录；常规更新按各玩家基线发送增删改。客户端发现 `baseRevision`
不一致时请求全量，不用缺失基线拼接增量。已加载单位显示可读取较新的同步附件，远程单位使用目录和按需技能资料。

单位与能力附件保持原类型、注册 ID 和存档结构，取消 Neo 自动同步。`commit` 对比现有目录摘要与当前单位字段并携带新修订号；能力修改发送变化 ID 与删除覆盖。`ConductorAttachmentUtil.sendInitial` 向新追踪者发送完整快照，玩家本人登录／复活／换维度也补发；单位移除单独清除单位附件，不误清除能力资格。目标、原点及一次性技能意图的空值必须显式发送，防止客户端保留旧值。远程目录和按需技能资料继续沿用原协议。

当前 [LcPayloads](../../src/main/java/org/unitego/lobecorp/registry/LcPayloads.java) 协议版本为 `12`
；这是一份源码快照，不应把旧专项记录中的版本 `6` 当成当前值。改变载荷字段或编码时同时更新注册版本和客户端接收逻辑。

## HUD、输入、配置与资源

[ConductorClient](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorClient.java) 提供 `snapshot`、
`unit`、`receive`、`send`、`sendTarget`、`requestSnapshot`
及技能资料查询。[ConductorControls](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorControls.java)
负责控制模式、选择、命令目标、面板和相机输入，业务界面复用 `openPanel` 入口。

[ConductorHud](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorHud.java) 作为 LDLib2
`ModularHudLayer` 注册在其他 HUD 图层上方，提供成员卡、技能卡、搜索、选中单位及操作按钮；技能展示按 BASIC → ATTACK → SUPPORT
稳定排序。[ConductorLdlibScreen](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorLdlibScreen.java)
使用代码创建团队、单位和远程操作页；[ConductorScreen](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorScreen.java)
是另一个原生 Screen 实现，当前 `openPanel` 调用选择 LDLib2 页面。

| 管理项                                  | 来源                                                                                                                                                                                                                                                                                                                                                                                                               |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 目录/观察同步间隔、超时、移动与战斗规则 | [ConductorRules](../../src/main/java/org/unitego/lobecorp/conductor/config/ConductorRules.java)；修改单位/时间含义前检查调用点                                                                                                                                                                                                                                                                                     |
| 默认键位                                | [ConductorKeyMappings](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorKeyMappings.java)：控制模式 K，镜头左/右旋转 Q/E，可由游戏键位设置管理                                                                                                                                                                                                                                          |
| HUD、头像与渲染状态注册                 | [ConductorHudRegistration](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorHudRegistration.java)、[ConductorPortraitRegistration](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorPortraitRegistration.java)、[ConductorRenderStateRegistration](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorRenderStateRegistration.java) |
| 本地偏好                                | [ConductorHudPreferences](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorHudPreferences.java)：config/lobecorp-conductor-hud.properties，保存折叠、减少动态与队形                                                                                                                                                                                                                          |
| 业务文本与视觉规则                      | [ConductorTexts](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorTexts.java)、[ConductorHudTheme](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorHudTheme.java)                                                                                                                                                                                                     |
| 资产参考                                | [resources/ldlib2](../../resources/ldlib2) 下 conductor XML/UI NBT；当前 HUD 与面板 `createLayout` 使用 Java 构建，不能只改参考资产推断运行界面变化                                                                                                                                                                                                                                                                |

持久化使用 `lobecorp:conductor_directory`，不是旧 `lobecorp:conductor` SavedData；当前代码不执行旧目录导入或删除。指挥单位附件为
`conductor_unit`，运行附件为 `conductor_runtime`，资格增删修正为 `conductor_ability_patch`
，见 [LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java)。附件跟踪同步与远程目录
Payload 具有不同覆盖范围，不能用重复网络载荷代替既有同步。

## 修改前检查点

- 先读取 [项目规则](../../AGENTS.md) 和 [指挥家 Skill](../../.codex/skills/lobecorp-conductor/SKILL.md)，再通过 IDEA
  核对最新路径、类型和引用；专项参考保留业务边界与迁移历史，当前实现以本页、相关系统文档和源码为准。
- 确认改动属于目录、持久化附件、非持久化运行态、能力操作或客户端显示，避免复制权威来源。
- 核对 `join/update/commit/attach/detach/remove`、待交付修订号、释放记录及同步基线；读取查询不能隐式创建成员。
- 施放检查同时覆盖目标、射程、所有权、冷却、互斥、连段和取消，复用既有能力、技能与命中框。
- 区块改动检查票据申请/释放、观察超时、跨维度、客户端缓存与相关 Mixin；保留现有网络分层和唯一事件入口。
- HUD 改动检查文本输入与快捷键抢占、选择取消、焦点失效、会话清理和偏好持久化；沿用 [客户端与 UI 约定](client-ui.md)。

## 待查项与文档维护

- 未实测多玩家共享目录的命令权限、断线清理、跨维度观察、远程未加载单位指令交付和服务器重启恢复；本页不提供运行验收结论。
- 世界目录、工作能力和客户端缓存的全量边界组合尚未形成完整游戏场景表；资源更新后的头像/纹理缓存失效需结合实际运行核对。
- 指挥家 Skill 曾把旧迁移快照中的类名、协议和依赖版本当作当前要求。现已修正入口与引用，当前实现统一导航到系统文档；迁移计划明确保留为历史，后续维护先核对 IDEA 源码，避免复制旧快照。
- 按 [系统变更记录](changes.md) 记录接口、配置、资源、网络、生命周期及验证事实；后续包调整时再次核对所有源码链接。

## Java 25 维护

2026-10-09 的规范整理将控制 tick 中的友方 Warden 怒气清理、攻击指令处理和空闲战斗选择拆成私有方法，主入口保留控制资格、指令优先级、挂起施放和重组顺序。服务端命令分发按具体命令拆分，编队单位收集独立为私有步骤；HUD 的按钮动作、鼠标分支、成员提示和卡片回收分别处理。公开入口、命令枚举、编解码字段与顺序、冷却及网络协议保持原行为，实际诊断与编译结果见 [修改记录](changes.md)。
