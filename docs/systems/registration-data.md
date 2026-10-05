# 注册、附件与数据契约

核对日期：2026-10-05。以当前注册源码为准；包迁移已完成，链接按当前源码核对。本文是导航与边界说明，不代表游戏运行验收。

## 入口与职责

[Lobecorp](../../src/main/java/org/unitego/lobecorp/Lobecorp.java) 统一挂接
DeferredRegister；[LcRegistrys](../../src/main/java/org/unitego/lobecorp/registry/LcRegistrys.java) 创建同步的
entity_skill 和 entity_skill_group 自定义注册表。实体、AI、技能、状态、物品、方块、标签、粒子、附件和网络登记各有对应 registry
入口，客户端注册也保留在 registry 下。

[LcAttachmentTypes](../../src/main/java/org/unitego/lobecorp/registry/LcAttachmentTypes.java)
决定数据持久化与同步；[LcCapabilities](../../src/main/java/org/unitego/lobecorp/registry/LcCapabilities.java)
暴露实体技能与指挥家控制能力。调用方使用能力公开接口，不能把附件内部状态当作平行状态机。

## 已有附件

| 注册 ID / 字段                                | 数据归属与生命周期                         |
|-----------------------------------------------|--------------------------------------------|
| entity_skill_patch / ENTITY_SKILLS            | 技能资格状态，Codec 持久化并同步           |
| entity_skill_groups                           | 技能分组，持久化并同步                     |
| entity_skill_cooldowns                        | 技能冷却，持久化并同步                     |
| active_entity_skills                          | 当前运行实例，不序列化，由技能生命周期清理 |
| conductor_ability_patch / CONDUCTOR_ABILITIES | 指挥家能力适配状态，持久化并同步           |
| CONDUCTOR_UNIT                                | 单位控制数据，序列化并同步                 |
| CONDUCTOR_RUNTIME                             | 运行期控制上下文，不序列化                 |
| ATTACK_COMBO                                  | 连段状态，同步但不持久化                   |
| HITBOX_LEVEL_DATA                             | Level 持有的运行期命中框集合，不持久化     |
| ENTITY_SKILL_EFFECT_LEVEL_DATA                | Level 持有的轻量技能效果集合，不持久化     |
| REASSEMBLY_PROGRESS                           | 重组进度运行态，不持久化                   |

上表字段名与注册 ID 不可互换；未列出 ID 的条目应直接查看注册定义，不猜测存档键。

## 复用与修改前检查

先确认 LcCapabilities 是否已经提供对应入口，再确认 LcAttachmentTypes 的
Codec、同步策略与默认构造器。注册对象在构造、字段初始化和静态初始化阶段优先延迟引用；DeferredHolder 尚未绑定时不得 get ()
。运行数据、持久状态与客户端缓存分别由其所属系统管理。

改变附件字段或 Codec 前检查存档兼容性、Payload 和实体离开/卸载清理链；登记新类型前确认当前 DeferredRegister
和注册表是否已覆盖需求。网络见 [网络](network.md)，技能见 [实体技能](entity-skills.md)
，生成资源见 [资源与数据生成](resources-datagen.md)。

## 维护记录

修改注册位置、ID、Codec、默认值或能力接口后同步本页与 [修改记录](changes.md)。本次只记录已读取的声明；未执行存档兼容性或联机验证。
