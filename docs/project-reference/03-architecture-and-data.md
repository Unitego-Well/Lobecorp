# 源码架构与数据流

## 总体入口

`org.unitego.lobecorp.Lobecorp` 是 `@Mod("lobecorp")` 主入口。构造函数按注册域初始化附件、标签、Brain 类型、效果、粒子、属性、实体数据序列化器、实体、技能、方块、物品和创造模式标签。

公共辅助方法集中在主类：

- `id(path)`：创建 `lobecorp:path` 标识符。
- `name(path)`：创建完整资源名称字符串。
- `register(registry)`：创建绑定 `lobecorp` 命名空间的 `DeferredRegister`。
- `type(identifier)`：创建自定义 Payload 类型。

事件入口位于 `events`：注册自定义注册表、区块票据、Payload、实体属性和 Datagen；实体 Tick、伤害、离开世界和客户端生命周期由对应功能域订阅类分发。

## 包职责和边界

| 域 | 主要入口 | 数据边界 |
| --- | --- | --- |
| `registry` | `LcRegistrys`、`LcAttachmentTypes`、`LcCapabilities`、各类 `Lc*` 注册器 | 只定义注册对象和注册生命周期 |
| `entity` | 女皇、清道夫、其他 Ordeal、尸体、AI、导航 | 实体自身状态、行为和实体专属表现 |
| `entity_skill` | `IEntitySkill`、`EntitySkillAccess`、`EntitySkillRuntime` | 技能定义、资格、请求、运行实例和效果 |
| `conductor` | `ConductorData`、`ConductorUnitControl`、`ConductorUtil` | 指挥家目录、单位能力、持续意图和世界运行态 |
| `hitbox` | `HitboxManager`、`HitboxInstance`、`HitboxSnapshot` | 几何、命中策略、效果应用和调试快照 |
| `events` | `LobecorpEvents`、`LivingEntityEvents`、`WorldTickEvents` | 唯一自动订阅入口和有序分发 |
| `network` | `ConductorCommandPayload`、`ConductorSnapshotPayload`、技能/命中框 Payload | 编解码、协议分发和客户端镜像 |
| `client` | `ConductorClient`、HUD、Camera、Rendering、Debug | 客户端会话、显示副本、输入和缓存 |
| `animation` | `LcAnimationController*`、`LcAnimationPose` | 动画层、状态表现和过渡，不执行 AI/伤害 |
| `mixin` | 原版实体、Brain、导航、输入和渲染注入 | 最小桥接，不持有长期业务状态 |
| `generator` | `ModGenerator`、语言/标签/模型/粒子 Provider | 生成资源，不作为运行时业务入口 |

## 指挥家数据模型

指挥家设计将持久化数据、运行数据、能力入口和客户端快照分开：

| 层 | 当前实现/标识 | 内容 | 是否持久化 |
| --- | --- | --- | --- |
| 世界目录 | `ConductorData`，SavedData ID `lobecorp:conductor_directory` | 队伍、成员摘要、最后位置、待交付记录、修订号和释放记录 | 是 |
| 单位附件 | `LcAttachmentTypes.CONDUCTOR_UNIT` / `ConductorUnitData` | 模式、目标 UUID、目的地/活动原点、技能 ID 和速度等直接字段 | 是，并同步 |
| 单位运行附件 | `CONDUCTOR_RUNTIME` / `ConductorUnitRuntime` | 路径、工作、声波、方向攻击、仇恨和技能适配运行态 | 否 |
| 能力 | `LcCapabilities.CONDUCTOR_CONTROL` / `ConductorUnitControl` | `supports`、`accept`、`tick`、`stop`、移动和技能控制 | 不保存状态副本 |
| 世界运行态 | `ConductorWorldRuntime` | 票据、玩家目录同步基线和临时运行信息 | 否 |
| 客户端会话 | `ConductorClient.Session` | 目录显示副本、修订号、选择、镜头和技能缓存 | 否；偏好单独保留 |
| 网络 | `ConductorCommandPayload`、`ConductorSnapshotPayload` | 服务端命令、全量/增量目录和版本恢复 | 传输态 |

关键原则：已加载单位以附件为准，目录摘要不反向覆盖附件；查看、资格查询和能力查询不能隐式创建单位；死亡和永久移除清理成员，卸载保留成员。

## 实体技能模型

`LcEntitySkills` 是唯一实体技能注册入口，清道夫和憎恶女皇通过注册器声明技能，不建立第二套技能注册表。

| 类型 | 代表对象 | 内容 |
| --- | --- | --- |
| 技能定义 | `IEntitySkill`、`EntitySkill.Properties` | ID、资格、冷却、分组和可调属性 |
| 持久化附件 | `ENTITY_SKILLS`、`ENTITY_SKILL_GROUPS`、`ENTITY_SKILL_COOLDOWNS` | 技能拥有、分组上限和冷却 |
| 运行附件 | `ACTIVE_ENTITY_SKILLS` / `EntitySkillRuntimeData` | 当前运行实例和实例 ID，不序列化 |
| 操作能力 | `EntitySkillAccess` | 查询、校验、施放、Tick、取消和清理 |
| 结构化 API | `EntitySkillCastRequest` / `EntitySkillCastResult` | 唯一施放输入和结果分类 |
| 同步 | `EntitySkillSyncPayload` | 运行实例 ID、创建/更新/删除客户端临时镜像 |
| 轻量效果 | `EntitySkillEffect` / `EntitySkillEffectManager` | Level 内服务端效果，不注册为 Minecraft Entity，不写世界存档 |

技能终止路径要同时清理运行实例、轻量效果和关联 Hitbox；客户端不启动冷却，也不反向修改服务端附件。

## 事件数据流

`WorldTickEvents.onLevelTick` 以高优先级先推进 `EntitySkillEffectEvents`，再推进 `HitboxEvents`。`LivingEntityEvents.onEntityTick` 在服务端为 LivingEntity 查询 `EntitySkillAccess` 并 Tick；没有能力时才回退到 `EntitySkillUtil`。

伤害链路由 `LivingEntityEvents` 统一入口处理：先处理伤害倍率/清道夫吸收，再调用指挥家伤害过滤；实体离开世界时清理技能运行态，并按死亡或永久移除条件处理指挥家成员和尸体创建。

## 视觉同步数据流

1. 服务端技能或实体状态发生生命周期边沿。
2. 持续动作写入 `EntityStateHolder` 或复用已有技能同步。
3. 客户端读取状态边沿，切换动画层，不在每 tick 重播。
4. 服务端确认的粒子通过 `ServerLevel.sendParticles` 发送。
5. 需要调试时，客户端使用现有 Hitbox/技能快照；不新增重复同步通道。

当前基础设施包括 `EntityState`、`EntityStateHolder`、`LcEntityDataSerializers.ENTITY_STATES`、`EntitySkillSyncPayload`、`LcAnimationController` 和 `ServerLevel.sendParticles`。

## Mixin 边界

Mixin 配置覆盖原版伤害、实体/Brain/导航/目标、客户端 Camera/Input/Debug/LevelRenderer、GeckoLib 和 SpawnEgg。业务状态应经能力、事件入口或 `*Util` 访问；Mixin 不应成为第二个注册器、事件总线或状态机。

## 资源和生成流

- 源素材位于 `resources/entity`，包括女皇、清道夫的模型、动画、纹理和 `.bbmodel`。
- 运行时 GeckoLib 资产位于 `src/main/resources/assets/lobecorp/geckolib`。
- 语言源位于根目录 `lang`，生成语言位于 `src/generated/resources/assets/lobecorp/lang`。
- 数据标签、伤害类型和技能标签位于 `src/main/resources/data`；Datagen 结果位于 `src/generated/resources/data`。
- `ModGenerator` 由 `GatherDataEvent.Client` 进入，输出路径由 `build.gradle.kts` 的 `runData` 配置决定。
