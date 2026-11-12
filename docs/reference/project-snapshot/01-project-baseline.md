# 项目基线与系统边界

盘点时间：2026-09-30

## 项目身份

| 项目项 | 当前值 | 证据 |
| --- | --- | --- |
| 模组 ID / 命名空间 | `lobecorp` | `gradle.properties`、`Lobecorp.java` |
| 展示名称 | `Lobotomy Corporation` | `src/main/resources/META-INF/neoforge.mods.toml` |
| 模组版本 | `0.0.3` | `gradle.properties` |
| Minecraft | `26.1.2` | `gradle/libs.versions.toml` |
| NeoForge | `26.1.2.100` | `gradle/libs.versions.toml`、IDEA 依赖 |
| Java | `25` | `build.gradle.kts`、Mixin `JAVA_25` |
| Gradle 脚本 | Kotlin DSL | `build.gradle.kts`、`settings.gradle.kts` |
| 主模块 | `Lobecorp.main` | IDEA 模块列表 |
| Java 根包 | `org.unitego.lobecorp` | `src/main/java` |
| 主类 | `org.unitego.lobecorp.Lobecorp` | `@Mod(Lobecorp.NAMESPACE)` |
| 当前 Git 分支 | `dev` | IDEA Git 状态 |

## 顶层目录

| 目录 | 作用 | 处理边界 |
| --- | --- | --- |
| `src/main/java` | Java 主源码 | 业务、注册、事件、网络、客户端和 Mixin 的实际实现 |
| `src/main/resources` | 模组运行资源 | `META-INF`、资产、数据、标签和伤害类型 |
| `src/generated/resources` | Datagen 输出 | 视为生成结果，不手工顺便整理 |
| `resources` | 建模、动画和源素材 | 包含 GeckoLib/Blockbench 相关素材 |
| `lang` | 语言源文件 | `en_us.json`、`zh_cn.json`，生成结果位于资源树中 |
| `docs` | 项目说明 | 已有 `java-architecture.md`，本次新增本资料集 |
| `.codex` | Codex 配置、规范和 Lobecorp Skill | 属于本仓库的自动化协作规则 |
| `.claude` | Claude/通用 Skill 和参考资料 | 只读取规则和文档，不读取敏感脚本配置 |
| `memory` | 项目事实和历史记录 | 仅作上下文参考，当前代码优先 |
| `run` | 游戏运行目录及嵌入工具快照 | 不当作主模组源代码，不在其中写整理文档 |
| `build`、`.gradle` | 构建/缓存产物 | 本次不清理、不重生成、不依据缓存推断源码状态 |
| `lib` | 本地 JAR | 依赖证据；不使用文本搜索替代 IDE/JAR 专用查询 |

## 源码域概览

当前 Java 结构由 IDEA 目录树和已有 Java 架构记录确认。已有架构文档记录主 Java 源集约 284 个文件；由于当前工作区存在并行移动和修改，具体文件以实时目录和 IDEA 索引为准。

| 顶层包 | 主要职责 |
| --- | --- |
| `api` | 跨域接口、危险等级和 Mixin 扩展契约 |
| `attribute` | 属性实现及属性边界辅助类 |
| `animation` | 动画控制器、层、姿态、过渡和自定义动画接口 |
| `client` | 指挥家 HUD、镜头、输入、渲染、粒子和调试显示 |
| `conductor` | 指挥家能力、目录、单位控制、生命周期、世界运行态 |
| `debug` | 调试运行选项 |
| `effect` | Mob Effect 实现 |
| `entity` | 实体、AI、传感器、导航、目标和实体专属表现 |
| `entity_skill` | 技能定义、请求/结果、运行实例、技能效果和适配 |
| `entity_state` | 可同步实体状态模型 |
| `event` | 事件类型和事件数据定义 |
| `events` | NeoForge 自动订阅入口和有序分发 |
| `generator` | 语言、标签、模型、粒子和数据生成 |
| `hitbox` | 命中框几何、生命周期、效果和调试快照 |
| `item` | 模组物品实现 |
| `mixin` | 原版/第三方桥接和最小注入 |
| `network` | CustomPacketPayload、编解码和协议分发 |
| `particle` | 粒子类型和选项 |
| `registry` | NeoForge 注册对象和注册生命周期 |
| `serialization` | Codec 和集合序列化辅助 |
| `util` | 无状态工具、翻译键、技能/指挥家入口 |

## 元数据和加载边界

`neoforge.mods.toml` 声明模组 ID、版本、展示名称、作者、图标、Mixin 配置、Access Transformer 和必需依赖。当前声明的必需依赖包括 Minecraft、NeoForge、GeckoLib、Curios 和 ParticleStorm。

`lobecorp.mixins.json` 使用 `JAVA_25`，公共 Mixin 位于 `org.unitego.lobecorp.mixin`，客户端 Mixin 单独列在 `client` 节点。`interfaces.json` 将 `DamageSource` 扩展到 `IDamageSourceExpand`。

## 工作区状态

本次检查时 IDEA Git 状态显示：

- 当前分支：`dev`。
- 状态条目：313；本次完整状态查询返回条目可能因查询上限截断展示，但计数由 IDEA Git 状态提供。
- 暂存条目：226；未暂存条目：245；两者可能对同一文件同时成立。
- 冲突：0；未跟踪文件：9（按本次 IDEA 查询结果）。
- 现有修改涉及 Gradle、文档、语言、动画资源、Java 包迁移、客户端指挥家、技能、实体、Mixin、注册和网络等多个域。

这些变更属于用户、并行工作流和本次规范整理。本资料集不重排、不格式化、不恢复、不删除现有变更；新增的整理文档位于 `docs/project-reference/`。

## 内嵌独立仓库

`run/client/.mcp-repair-build` 和 `run/client/devbridge-backup-20260924/mcp-secure-build` 都包含独立的 `.git` 和 `AGENTS.md`，它们是 `minecraft-mod-mcp` 的工作副本/备份，不是 Lobecorp 的主源码模块。它们的分支、PR、提交和验证规则只适用于各自嵌套仓库；除非用户明确要求，本项目文档不对其进行修改。
