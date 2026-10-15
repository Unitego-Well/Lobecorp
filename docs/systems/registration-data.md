# 注册、附件与数据契约

核对日期：2026-10-13。以当前注册源码为准；包迁移已完成，链接按当前源码核对。本文是导航与边界说明，不代表游戏运行验收。

## 入口与职责

[Lobecorp](../../src/main/java/org/unitego/lobecorp/Lobecorp.java) 统一挂接
DeferredRegister；[LcRegistrys](../../src/main/java/org/unitego/lobecorp/registry/LcRegistrys.java) 创建同步的
entity_skill 和 entity_skill_group 自定义注册表。实体、AI、技能、状态、物品、方块、标签、粒子、附件和网络登记各有对应 registry
入口，客户端注册也保留在 registry 下。

[LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java)
决定附件持久化及简单值同步；复杂状态通过功能域网络包同步。[LcCapabilities](../../src/main/java/org/unitego/lobecorp/registry/LcCapabilities.java)
暴露实体技能与指挥家控制能力。调用方使用能力公开接口，不能把附件内部状态当作平行状态机。

## 已有附件

| 注册 ID / 字段                                | 数据归属与生命周期                         |
|-----------------------------------------------|--------------------------------------------|
| entity_skill_patch / ENTITY_SKILLS            | 技能资格状态，Codec 持久化，技能数据包同步                   |
| entity_skill_groups                           | 技能分组，持久化，技能数据包同步                           |
| entity_skill_cooldowns                        | 技能冷却，持久化，技能数据包同步                           |
| entity_skill_cooldown_clock                   | 冷却速度计时基准与各技能小数余量，仅持久化 |
| entity_grab / ENTITY_GRAB                     | 当前抓取关系同步，不持久化、不随死亡复制   |
| active_entity_skills                          | 当前运行实例，不序列化，由技能生命周期清理 |
| conductor_ability_patch / CONDUCTOR_ABILITIES | 指挥家能力适配状态，持久化，指挥家数据包同步                     |
| CONDUCTOR_UNIT                                | 单位控制数据，序列化，指挥家数据包同步                        |
| CONDUCTOR_RUNTIME                             | 运行期控制上下文，不序列化                 |
| ATTACK_COMBO                                  | 连段状态，Neo 附件同步但不持久化                         |
| HITBOX_LEVEL_DATA                             | Level 持有的运行期命中框集合，不持久化     |
| ENTITY_SKILL_EFFECT_LEVEL_DATA                | Level 持有的轻量技能效果集合，不持久化     |
| text_particle_level_data / TEXT_PARTICLE_LEVEL_DATA | Level 持有的可修改文本展示实例，不持久化，文本数据包同步 |
| REASSEMBLY_PROGRESS                           | 重组进度运行态，不持久化                   |

上表字段名与注册 ID 不可互换；未列出 ID 的条目应直接查看注册定义，不猜测存档键。

同步规则：单值或几个固定基础值直接使用 Neo 附件同步；Map、集合和复杂控制状态由所属功能域网络包同步。五个复杂附件保留原注册 ID、数据类型及存档 Codec，取消 `.sync(...)`；首次追踪和玩家本人生命周期发送完整快照，后续修改入口直接发送变化条目／字段及删除，不拆分附件，不新增脏状态容器。详见[网络](network.md)。`REASSEMBLY_PROGRESS` 无客户端消费，继续仅服务端使用。

2026-10-07 新增冷却计时附件，原技能到 Long 到期时间的冷却 Codec 保持；旧存档缺少计时附件时从当前游戏时间建立基准。抓取的同步 Codec 只发送抓取者 UUID 与世界轴偏移，脱离阈值、旧锚点和持有目标集合仅在服务端，使用现有附件同步，不新增 Payload。生命周期与维护规则见[技能](entity-skills.md)和[实体抓取](entity-grab.md)。

`LcAttributes.ENTITY_SKILL_COOLDOWN_SPEED` 默认 1、最小 0、最大 1024、可同步。`EntityAttributeCreation` 在既有 NeoForge `EntityAttributeCreationEvent` 中，按登记的实体实现类识别 `IEntitySkillHolder`，补齐该属性和原版 `ATTACK_SPEED`；先检查 Builder 中是否已有属性，不覆盖手动注册值。女皇、清道夫与尸体统一通过带实现类的属性注册辅助方法登记，尸体不实现接口，因此不补齐。其他实体由属性创建入口显式添加。中英文属性名称通过 `LcAttributes.init` 的 `LangHandler` 数据生成登记。

`TEXT_PARTICLE_LEVEL_DATA` 通过 `AttachmentType.builder(TextParticleLevelData::new)` 登记，不配置序列化或 Neo 附件自动同步。服务端保存公共文本数据和可修改实例，不依赖客户端 `Particle` 类；创建、修改、移除及每 20 tick 的完整补发由 `TextParticles` 通过专用 S2C 载荷完成。客户端粒子组和资源重载监听由 `registry.particle.client.TextParticleRegistration` 经已有客户端事件入口登记。接口与生命周期见[文本展示粒子](text-particles.md)。

## 复用与修改前检查

先确认 LcCapabilities 是否已经提供对应入口，再确认 LcAttachmentTypes 的
Codec、同步策略与默认构造器。注册对象在构造、字段初始化和静态初始化阶段优先延迟引用；DeferredHolder 尚未绑定时不得 get ()
。运行数据、持久状态与客户端缓存分别由其所属系统管理。

改变附件字段或 Codec 前检查存档兼容性、Payload 和实体离开/卸载清理链；登记新类型前确认当前 DeferredRegister
和注册表是否已覆盖需求。网络见 [网络](network.md)，技能见 [实体技能](entity-skills.md)
，生成资源见 [资源与数据生成](resources-datagen.md)。

## 维护记录

2026-10-09：常规延迟注册入口、登记项及类型已直接表达的编解码器说明不机械添加注释；变量名清楚本身不作为删除依据。保留附件持久化／同步差异、注册时机、数值单位和具体兼容规则。现有重复模板注释仅复述注册结构或登记文本，维护时按根规范的必要性判断处理；本轮不变更注册逻辑。本轮清理已通过 IDEA 完成并核对磁盘保存，检查结果和历史差异备份见 [修改记录](changes.md)。

修改注册位置、ID、Codec、默认值或能力接口后同步本页与 [修改记录](changes.md)。本次只记录已读取的声明；未执行存档兼容性或联机验证。
