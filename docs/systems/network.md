# 网络协议与同步

核对日期：2026-10-13。协议登记由 [LcPayloads](../../src/main/java/org/unitego/lobecorp/registry/LcPayloads.java) 决定，当前
NETWORK_VERSION 为 18；本文不代表联机测试通过。

## 方向与入口

[IToPayload](../../src/main/java/org/unitego/lobecorp/network/IToPayload.java) 使用 context.enqueueWork
把处理转入正确线程。network.ts
的 [IToServerPayload](../../src/main/java/org/unitego/lobecorp/network/ts/IToServerPayload.java) 校验
ServerPlayer；network.tc 的 [IToClientPayload](../../src/main/java/org/unitego/lobecorp/network/tc/IToClientPayload.java)
面向客户端玩家；network.tsc
的 [IToServerAndClientPayload](../../src/main/java/org/unitego/lobecorp/network/tsc/IToServerAndClientPayload.java)
保留双向接口。本次不另建 Payload 分类。

| 方向  | 已登记 Payload                                                                                                                                                                                                                                                                                                | 职责                                           |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| C → S | [ConductorCommandPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ConductorCommandPayload.java)                                                                                                                                                                                                   | 指挥家操作命令，服务端重新检查实体、资格和状态 |
| C → S | [ConductorViewPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ConductorViewPayload.java)                                                                                                                                                                                                         | 观察/控制会话相关请求                          |
| S → C | [ConductorSnapshotPayload](../../src/main/java/org/unitego/lobecorp/network/tc/ConductorSnapshotPayload.java)                                                                                                                                                                                                 | 指挥家客户端快照                               |
| S → C | [EntitySkillSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/EntitySkillSyncPayload.java)                                                                                                                                                                                                     | 技能运行表现同步                               |
| S → C | [EntitySkillDataSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/EntitySkillDataSyncPayload.java)                                                                                                                                                                                             | 技能资格、技能组及冷却的完整／增量同步                            |
| S → C | [ConductorEntityDataSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/ConductorEntityDataSyncPayload.java)                                                                                                                                                                                     | 已加载实体的指挥单位字段及能力资格同步                            |
| S → C | [LcCustomAnimationSettingsSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/LcCustomAnimationSettingsSyncPayload.java)                                                                                                                                                                         | 自定义动画设置同步                             |
| S → C | [HitboxCreatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxCreatePayload.java)、[HitboxUpdatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxUpdatePayload.java)、[HitboxRemovePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxRemovePayload.java) | 命中框客户端快照创建、更新与移除               |
| S → C | [LcAnimationContentPayload](../../src/main/java/org/unitego/lobecorp/network/tc/animation/LcAnimationContentPayload.java) | 按版本分块传输完整模型与动画内容 |
| S → C | [LcAnimationStatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/animation/LcAnimationStatePayload.java) | 服务端动画时间、姿态、参数、世界变换 |
| S → C | [LcAnimationRemovePayload](../../src/main/java/org/unitego/lobecorp/network/tc/animation/LcAnimationRemovePayload.java) | 移除客户端播放帧，保留显式显示登记 |
| S → C | [LcAttachmentStatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/attachment/LcAttachmentStatePayload.java) | 通用绑定全量／更新／移除及物品显示描述 |
| S → C | [TextParticleStatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/text/TextParticleStatePayload.java) | 当前 Level 的文本展示实例全量／更新／移除 |
| S → C | [DamageParticlePayload](../../src/main/java/org/unitego/lobecorp/network/tc/damage/DamageParticlePayload.java) | 一次已结算伤害的维度、来源／目标 UUID、类型、固定位置、数值及近战语义 |

当前登记为 2 项 C2S、14 项 S2C，不能把存在 tsc 接口解释为已登记双向业务包。

## 状态权威与复用

服务端决定控制、技能和伤害；客户端提交请求与表现缓存。协议层负责 Codec 与派发，不能信任客户端参数替代服务器资格验证。

新增同步前先检查附件自动同步、SynchedEntityData、实体事件、已有技能同步和快照是否已经表达该状态。普通注册与世界同步复用原版/NeoForge，不添加重复通道。持续动画通过状态边沿切换，瞬时粒子按已有表现链触发。

## 简单附件与复杂状态

简单值或几个固定基础值沿用 Neo `.sync(codec)`：当前为 `ATTACK_COMBO` 和 `ENTITY_GRAB` 的客户端移动约束投影。复杂附件保留持久化及业务入口，通过所属功能域载荷发送；不拆分为多个附件，也不维护脏容器。

`EntitySkillDataSyncPayload` 首次完整替换技能资格、技能组和冷却，后续只携带变化键及删除键。资格的 `false` 为显式禁用，删除键为恢复实体默认资格，两者不可混用。冷却仍发送原 Long 到期时间，速度和小数计时仅在服务端推进。

`ConductorEntityDataSyncPayload` 首次发送完整单位状态与能力资格；后续发送单位变化字段、单位移除或能力变化键。单位修订号随修改提交，允许清空可空目标、原点和技能意图。能力的显式启用／禁用与删除覆盖保持原语义。远程目录仍由 `ConductorSnapshotPayload` 按各玩家基线同步。

修改入口向实体追踪者及玩家本人发送增量。`PlayerEvent.StartTracking` 在生成 bundle 发送之后向新追踪者发送完整快照；本人在登录、复活、换维度后补发完整快照，不在较早的 Clone 事件发送。完整快照读取已有附件或空默认，不触发编队、技能运行态创建或历史技能回放。现有 `EntitySkillSyncPayload` 继续按顺序发送生命周期边沿。

## 服务端动画内容与状态帧

`LcAnimationSync` 使用同一分类下新增的三种 S2C 载荷。完整 GeoJSON 与动画 JSON 按内容版本以 24 KiB 分块发送，状态帧引用版本并携带播放 UUID、时间、姿态、参数查询快照、owner 世界变换与根通道；客户端缺少内容时等待重组及版本校验后安装。回调、伤害过滤器与服务端目标引用不传输，客户端接收和视觉采样不执行服务端时间线行为。

实体播放按追踪关系和玩家本人发送，无来源实体的通用 owner 播放发送给同 Level 玩家；新追踪者读取当前快照，不重放历史事件。发送缓存归当前连接所有，玩家重生替换 ServerPlayer 时保留实例历史，以便清理旧帧。运行中实例固定内容版本，数据包重载不自动替换；显式 `editContent` 只生成该实例的新版本。客户端同 ID 资源可以覆盖视觉，不反向改变服务端位置、碰撞或命中，纹理不在新增内容传输内。

协议 14 在同一 state/remove 通道增加玩家姿态策略、播放修订、服务器／阶段／终止时刻、终止原因和代码修饰骨骼绝对结果。玩家软停止保留终止快照直到淡出结束；停止追踪、来源失效和世界清理立即删除。第一／第三人称共用这些状态，没有增加第二种玩家动画触发同步，详见[玩家动画](player-animation.md)。

remove 载荷不注销独立模型／物品显示；重追踪后可恢复原登记。显示接入方须明确调用 `LcAnimationDisplays.remove(targetId)` 结束登记，否则到 `clearWorld` 统一清理。现有技能生命周期同步继续使用原载荷，未迁移技能。内容路径、显式行为、显示 API 和清理顺序见[服务端动画、根运动与空间附着](animation-runtime.md)。

协议 15 将绑定移到独立附着帧，支持无动画来源及客户端本地绑定。绑定引用原动画 UUID 而非复制播放事实；先发送所需动画内容／状态，再发送绑定。首次全量仅替换服务端镜像分区，后续传当前可见绑定及移除差集；每次更新绑定的物品描述缺席表示注销该显示。服务器移除、全量与停止追踪不清客户端自行创建的绑定。逻辑偏移与最终渲染节点偏移分开，客户端矩阵仅影响视觉。详细契约与清理见[通用空间附着](attachments.md)。

协议 16 在 `LcAnimationStatePayload` 既有玩家策略中编解码 `firstPersonArms` 布尔值，默认关闭；本帧存在显式开启且策略权重大于 0 的层时才接管第一人称双臂。软停止沿用原视觉尾部与时刻，淡出结束后恢复原版手臂持物流程。没有增加载荷类型、另一套播放事实或客户端触发通道，也不提供旧协议读取兼容。

## 文本展示实例

协议 17 新增 `TextParticleStatePayload`，由 `TextParticles` 向同一 Level 的所有玩家发送。创建和修改立即发送指定实例，移除立即发送 ID；每 20 tick 补发当前 Level 的完整集合，供新加入玩家恢复并清除已经不存在的实例。它管理可修改实例，普通一次性粒子仍沿用原版 `sendParticles`。

客户端按当前 Level 和实例 UUID 保存粒子，只接收更高修订号的更新；相同修订号的周期补发不会重建粒子或重置进度。`startedTick` 用于首次安装和显式重启时定位进度，逐字与单字动画在客户端 tick 推进，不逐 tick 回传或同步。`Component` 保留样式与翻译结构，在各客户端本地解析；登录、注销和世界卸载清理实例缓存。接口、渲染缓存与已知限制见[文本展示粒子](text-particles.md)。

## 屏幕伤害通知

协议 18 新增 `DamageParticlePayload`。服务端唯一 `LivingDamageEvent.Post` 入口读取有限且大于零的 `getHealthDamage()`，向发生位置周围 32 格、同维度玩家发送一次通知；数值不含吸收，但包含超过目标剩余生命的伤害，不是前后生命差。来源 UUID 用于严格区分攻击者，不能将所有非玩家来源归成同一合并键。

客户端校验当前维度，并仅在 `melee` 且攻击者 UUID 等于本地玩家 UUID 时使用屏幕中心；其他玩家的近战同样投影服务端给出的世界发生位置。合并与动画在客户端推进，不增加周期包、客户端回传或服务器粒子实例。精确碰撞点、估算点和中心回退的来源及限制见[屏幕伤害粒子](screen-damage-particles.md)。

## 修改前与收尾检查

检查 LcPayloads 的版本和登记方向、具体
TYPE/STREAM_CODEC、双方处理器、发送点、缓存失效和世界卸载。变更结构或协议需按大型修改验证，并同步 [注册与数据](registration-data.md)
、对应业务页及 [修改记录](changes.md)。包迁移只改变 Java 类型位置，协议 ID 和编码保持不变。
