# 世界领域登记包迁移（2026-11-12）

世界领域登记统一归入`org.unitego.lobecorp.registry.world`，按item、block、entity、effect、damage、encounter、horde、tag继续细分；数据组件归world.item，跨世界领域的附件、能力归world；公共注册表入口LcRegistries及LcRegistrieKeys归上层registry。客户端渲染、通用动画、声音、翻译和网络登记仍按其所属职责组织。

LcLevels.register(path,enUs,zhCn)统一调用Lobecorp.createKey创建ResourceKey<Level>，并以dimension加维度标识符的语言键登记中英文名称。LcWorldClocks独立声明WorldClock资源键。CityDimensions移入world.level，只保留维度运行规则。

本次使用IDEA语义移动更新Java引用和可识别资源引用。迁移不改变注册ID、维度ID、翻译键、协议和资源位置；未向世界运行逻辑引入客户端依赖。

| 来源类 | 目标类 | 原因 |
|---|---|---|
| `registry.block.LcBlocks` | `registry.world.block.LcBlocks` | 世界领域登记集中归属 |
| `registry.item.LcCreativeModeTabs` | `registry.world.item.LcCreativeModeTabs` | 世界领域登记集中归属 |
| `registry.item.LcItems` | `registry.world.item.LcItems` | 世界领域登记集中归属 |
| `registry.item.SpawnEggItems` | `registry.world.item.SpawnEggItems` | 世界领域登记集中归属 |
| `registry.item.WarpAnchorPresets` | `registry.world.item.WarpAnchorPresets` | 世界领域登记集中归属 |
| `registry.entity.AbnormalitieEntityTypes` | `registry.world.entity.AbnormalitieEntityTypes` | 世界领域登记集中归属 |
| `registry.entity.ai.LcMemoryModuleTypes` | `registry.world.entity.ai.LcMemoryModuleTypes` | 世界领域登记集中归属 |
| `registry.entity.ai.LcSensorTypes` | `registry.world.entity.ai.LcSensorTypes` | 世界领域登记集中归属 |
| `registry.entity.ConductorCapabilityRegistration` | `registry.world.entity.ConductorCapabilityRegistration` | 世界领域登记集中归属 |
| `registry.entity.EntityAttributeCreation` | `registry.world.entity.EntityAttributeCreation` | 世界领域登记集中归属 |
| `registry.entity.LcAttributes` | `registry.world.entity.LcAttributes` | 世界领域登记集中归属 |
| `registry.entity.LcEntityDataSerializers` | `registry.world.entity.LcEntityDataSerializers` | 世界领域登记集中归属 |
| `registry.entity.LcEntityTypes` | `registry.world.entity.LcEntityTypes` | 世界领域登记集中归属 |
| `registry.entity.OrdealEntityTypes` | `registry.world.entity.OrdealEntityTypes` | 世界领域登记集中归属 |
| `registry.entity.skill.EntitySkillCapabilities` | `registry.world.entity.skill.EntitySkillCapabilities` | 世界领域登记集中归属 |
| `registry.entity.skill.EntitySkillDefaults` | `registry.world.entity.skill.EntitySkillDefaults` | 世界领域登记集中归属 |
| `registry.entity.skill.LcEntitySkillGroups` | `registry.world.entity.skill.LcEntitySkillGroups` | 世界领域登记集中归属 |
| `registry.entity.skill.LcEntitySkills` | `registry.world.entity.skill.LcEntitySkills` | 世界领域登记集中归属 |
| `registry.entity.skill.SweeperSkills` | `registry.world.entity.skill.SweeperSkills` | 世界领域登记集中归属 |
| `registry.entity.skill.TheQueenOfHatredSkills` | `registry.world.entity.skill.TheQueenOfHatredSkills` | 世界领域登记集中归属 |
| `registry.entity.state.SweeperStates` | `registry.world.entity.state.SweeperStates` | 世界领域登记集中归属 |
| `registry.entity.state.TheQueenOfHatredStates` | `registry.world.entity.state.TheQueenOfHatredStates` | 世界领域登记集中归属 |
| `registry.effect.LcMobEffects` | `registry.world.effect.LcMobEffects` | 世界领域登记集中归属 |
| `registry.encounter.LcEncounters` | `registry.world.encounter.LcEncounters` | 世界领域登记集中归属 |
| `registry.horde.HordeProfiles` | `registry.world.horde.HordeProfiles` | 世界领域登记集中归属 |
| `registry.damage.LcDamageTypes` | `registry.world.damage.LcDamageTypes` | 世界领域登记集中归属 |
| `registry.LcLevels` | `registry.world.LcLevels` | 世界领域登记集中归属 |
| `registry.LcAttachmentTypes` | `registry.world.LcAttachmentTypes` | 世界领域登记集中归属 |
| `registry.LcCapabilities` | `registry.world.LcCapabilities` | 世界领域登记集中归属 |
| `registry.LcRegistries` | `registry.LcRegistries` | 世界领域登记集中归属 |
| `registry.LcRegistrieKeys` | `registry.LcRegistrieKeys` | 世界领域登记集中归属 |
| `registry.LcDataComponents` | `registry.world.item.LcDataComponents` | 世界领域登记集中归属 |
| `registry.dimension.CityDimensions` | `world.level.CityDimensions` | 维度运行逻辑脱离登记包 |
| `registry.tag.LcDamageTypeTags` | `registry.world.tag.LcDamageTypeTags` | 世界领域登记集中归属 |
| `registry.tag.LcEntitySkillTags` | `registry.world.tag.LcEntitySkillTags` | 世界领域登记集中归属 |
| `registry.tag.LcEntityTypeTags` | `registry.world.tag.LcEntityTypeTags` | 世界领域登记集中归属 |
| `registry.tag.LcTags` | `registry.world.tag.LcTags` | 世界领域登记集中归属 |

新增`registry.world.LcWorldClocks`接收原CityDimensions.MIDNIGHT_CLEANUP_CLOCK；原独立维度名称声明由LcLevels.register统一承接。

## 通用登记封装与接口（2026-11-12）

无实例状态的登记类型优先使用interface。LcLevels、LcWorldClocks、LcDataComponents、WarpAnchorPresets、LcEncounters、ConductorCapabilityRegistration、EntityAttributeCreation、EntitySkillCapabilities已接口化。HordeProfiles与EntitySkillDefaults拥有私有可变登记表，保持class以封装状态。

LcWorldClocks.register支持模组路径和完整Identifier；LcDataComponents.register统一持久化Codec及网络StreamCodec配置；WarpAnchorPresets.register接收名称与完整不可变WarpAnchor预设数据，每次创建独立堆栈。LcEncounters开放通用register入口。注册ID、翻译键、数据格式与行为不变。

2026-11-12补充：WarpAnchorPresets已移除，后巷深宵版直接在LcItems中以DeferredItem<WarpAnchorItem>登记；registerWarpAnchor封装共同默认组件、描述和最大堆叠数。
