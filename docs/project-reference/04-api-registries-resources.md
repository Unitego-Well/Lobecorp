# 内部 API、注册表、资源与依赖

## 内部公共 API

| API | 文件 | 用途 |
| --- | --- | --- |
| `Lobecorp.id` | `Lobecorp.java` | 创建 `lobecorp` 命名空间 ID |
| `Lobecorp.name` | `Lobecorp.java` | 创建完整资源名称字符串 |
| `Lobecorp.register` | `Lobecorp.java` | 创建模组命名空间的 `DeferredRegister` |
| `Lobecorp.type` | `Lobecorp.java` | 创建 Custom Payload 类型 |
| `ConductorUtil` | `util/ConductorUtil.java` | 指挥家能力、指令、Tick、移动和技能入口 |
| `ConductorAttachmentUtil` | `util/ConductorAttachmentUtil.java` | 指挥家附件读取、同步和清理 |
| `EntitySkillAccess` | `entity_skill/EntitySkillAccess.java` | 实体技能查询、施放、Tick、取消和清理 |
| `EntitySkillUtil` | `util/EntitySkillUtil.java` | 技能状态机内部辅助和兼容入口 |
| `HitboxManager` | `hitbox/HitboxManager.java` | Level 级命中框生命周期和快照 |
| `EntityStateHolder` | `entity_state/EntityStateHolder.java` | 实体持续状态的读写和查询 |
| `ToPayload` | `network/ToPayload.java` | 统一 Payload 工作调度 |
| `ToServerPayload` | `network/ts/ToServerPayload.java` | 服务端玩家校验后的 Payload 入口 |
| `ToClientPayload` | `network/tc/ToClientPayload.java` | 客户端玩家上下文的 Payload 入口 |

## 自定义注册表

`LcRegistrys` 创建并在 `NewRegistryEvent` 注册两个同步注册表：

- `lobecorp:entity_skill`：`IEntitySkill<?>`。
- `lobecorp:entity_skill_group`：`EntitySkillGroup`。

技能注册使用 `LcEntitySkills.REGISTER`，统一生成 ID 和中英文翻译键；技能定义本身仍位于 `entity_skill` 域。

## Attachment 和 Capability

`LcAttachmentTypes` 当前集中定义：

- `reassembly_progress`：尸体重组进度。
- `hitbox_level_data`：Level 级服务端命中框和客户端调试镜像。
- `entity_skill_effect_level_data`：Level 级不持久化技能效果。
- `entity_skills`、`entity_skill_groups`、`entity_skill_cooldowns`：技能资格、分组和冷却，按声明持久化/同步。
- `active_entity_skills`：当前技能运行实例，不序列化。
- `conductor_abilities`：旧指挥家能力适配状态。
- `attack_combo`：攻击段数，运行时字段但同步。
- `conductor_unit`：单位持久化字段和同步编码。
- `conductor_runtime`：单位运行态，不持久化。

`LcCapabilities` 声明：

- `lobecorp:unit_control` → `ConductorUnitControl`。
- `lobecorp:entity_skill` → `EntitySkillAccess`。

能力是操作入口，不是第二套持久化数据源；调用方每次查询，不长期缓存能力对象。

## Payload 协议

`LcPayloads` 当前登记协议版本 `6`：

| 方向 | Payload | 作用 |
| --- | --- | --- |
| 服务端 | `ConductorCommandPayload` | 接收指挥家命令 |
| 客户端 | `ConductorSnapshotPayload` | 目录全量/增量快照 |
| 客户端 | `EntitySkillSyncPayload` | 技能运行实例同步 |
| 客户端 | `LcCustomAnimationSettingsSyncPayload` | 自定义动画设置同步 |
| 客户端 | `HitboxCreatePayload` | 创建客户端命中框镜像 |
| 客户端 | `HitboxUpdatePayload` | 更新客户端命中框镜像 |
| 客户端 | `HitboxRemovePayload` | 删除客户端命中框镜像 |

协议层只负责编解码和分发；服务端处理器仍需重新校验资格、实体、权限和当前状态，不能把客户端输入当作权威状态。

## 状态、标签和数据资源

| 资源域 | 当前位置 | 说明 |
| --- | --- | --- |
| 实体状态 | `registry/entity_state` | 清道夫动作状态、女皇姿态状态及互斥组 |
| 实体技能标签 | `src/main/resources/data/lobecorp/tags/entity_skill` | magic、movement、damage、healing、melee、ranged 等分类 |
| 伤害类型 | `src/main/resources/data/lobecorp/damage_type` | 例如魔法星伤害 |
| 原版伤害标签 | `src/main/resources/data/minecraft/tags/damage_type` | projectile、bypasses_cooldown 等行为标签 |
| 语言 | 根目录 `lang` | 中英文源文本 |
| GeckoLib 模型/动画 | `src/main/resources/assets/lobecorp/geckolib` | 运行时模型和动画 |
| 粒子定义 | `src/main/resources/assets/lobecorp/particle_definitions` | 粒子 JSON |
| UI | `src/main/resources/assets/lobecorp/ui` | 客户端界面资源 |
| 生成资源 | `src/generated/resources` | Datagen 输出，不手工覆盖 |

## 主要依赖版本

以下表格取自 `gradle/libs.versions.toml` 和 IDEA 项目依赖列表；版本是当前检出配置，不代表已经下载或运行验证。

| 依赖 | 配置版本/当前索引名称 | 用途 |
| --- | --- | --- |
| ModDev Gradle | `2.0.142` | NeoForge Gradle 插件 |
| MixinMCP Decompile | `1.5.0` | Mixin/依赖源码查询支持 |
| MixinSquared | `0.3.7-beta.3` | 跨 Mixin 协作 |
| AnvilLib | `2.0.0+snapshot.485` | AnvilLib 组件族 |
| GeckoLib | `5.5.2` | 实体模型和动画 |
| Curios | `15.0.0-beta.2+26.1.2` | 饰品槽/集成 |
| JEI | `29.34.0.90` | 物品/配方查看集成 |
| Jade | `26.1.8+neoforge` | 信息提示集成 |
| Re-Tro Damage Indicators | `ldSJzzoV` | 伤害/血量显示集成 |
| LDLib2 | `26.1.2.36` | UI 和公共库 |
| KilaGraph | `26.1.0.14` | Photon 依赖 |
| Photon | `26.1.2.2` | 粒子/视觉效果 |
| ParticleStorm | `1.4.4` | 粒子效果 |
| 本地 IMBlocker | `IMBlocker-7.3.2-neoforge-26.1+.jar` | 本地输入法冲突修复 |
| 本地 spark | `spark-1.10.172-neoforge.jar` | 性能分析 |

Gradle 同时把 `lib/*.jar` 加入开发 classpath，并把主要运行依赖放入 `internalTestMods`。本轮已通过 IDEA 完成项目编译，并成功执行 `Data` 数据生成；完整 Gradle 构建、内部测试包和游戏运行仍未验证。

## 构建和运行入口（记录与验证状态）

- `build.gradle.kts`：主构建、Jar 命名、客户端/服务端/Data 运行配置、内部测试包和资源展开。
- `run/client`、`run/server`、`run/data`：对应运行目录。
- `.run/Internal Test Bundle.run.xml`：IDEA Gradle 配置 `internalTestBundle`。
- `.github/workflows/build.yml`：Push/PR 触发，使用 JDK 25 和 `./gradlew build`。
- `src/generated/resources`：DataGen 输出目录。

本轮验证：IDEA 项目构建成功；IDEA `Data` 配置退出码为 0，生成目录已通过 IDEA 文件树检查；完整 `build`、`internalTestBundle`、客户端、服务端和游戏测试未执行。
