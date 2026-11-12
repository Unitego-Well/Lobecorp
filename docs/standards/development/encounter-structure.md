# Encounter 结构规范

范围：Lobecorp 的具体玩法、通用 Horde API、玩法登记、客户端展示和翻译。维护归属：项目结构规范；来源：用户本次明确确认的分类方案。更新：2026-11-10。

状态：本文件规定目标结构，当前已进入迁移实施，实际结构见[Encounter 契约](../../systems/encounters/encounters.md)；未记录的验证不得视为通过。女皇子分支当前动画、激光及 Photon 实现排除在本次业务整理范围外。

通用授权与执行使用[共享全局工作协议](C:/Users/wang_/.agents/AGENTS.md)；Java、接口及各职责入口使用[Java 与包规范](java-and-packages.md)。

## 玩法领域与依赖方向

- `Encounter` 表达具体玩法领域。玩法的定义、状态、阶段、进度及其展示使用 Encounter 命名；例如 `EventHud` 的目标名称为 `EncounterHud`。Java/NeoForge 的事件类型仍归 `event`，监听入口仍归 `events`，不把领域更名套用于框架事件。
- `Horde` 只保留可复用的通用群体 API。具体玩法通过公开 API 使用 Horde；Horde 不反向引用具体 Encounter，不承载深夜清扫的专属规则、常量、翻译、HUD 或阶段流程。
- `Encounters`与`Hordes`公共操作入口分别位于`api.encounter`和`api.horde`；定义、运行状态和持久化仍位于对应`world`领域，登记仍归`registry`。
- 依赖方向为具体 Encounter 使用通用 Horde API。运行时、网络、渲染、配置与登记各守对应职责入口，不把具体玩法塞回通用层，也不机械把含 Horde 的全部类型都改名为 Encounter。

## 登记与常量归属

- Encounter 登记、注册表及登记声明归 `registry` 对应功能域。客户端登记统一为 `registry.client.<功能>.<职责>`；例如 Encounter HUD 的登记可归 `registry.client.encounter.hud`，HUD 实现仍归 `client` 对应功能包。
- `MidnightCleanupEncounter` 由命名注册常量 `MIDNIGHT_CLEANUP` 引入，集中承载该玩法的非注册专属常量，不另建独立 `Constants` 类。Definition 由独立管理入口负责登记与查询，具体注册项仿照其他注册类以 `MIDNIGHT_CLEANUP = register(...)` 这样的命名常量声明，不通过 `EncounterRegistration.init()` 的逐项创建流程实现；初始化入口只绑定既有声明，不重放业务声明。
- 配置、实体属性、已有注册结构或公共 API 已有权威值时直接引用，不在 `MidnightCleanupEncounter` 再复制；常量集中不意味着把客户端类型或资源对象放入公共服务端加载路径。
- 服务端可加载的玩法定义、登记和常量不泄漏 `client` 类型；客户端展示、资源及渲染登记在客户端职责入口接入。
- `EncounterDefinition` 使用普通可继承的类，不使用 `record` 或额外添加 `final` 限制扩展。Encounter 的定义类型与具体实现统一放入 `world.encounter` 及子包，仿照 `world.item` 的领域分类；`registry.world.encounter` 只负责注册声明与注册表接入。Definition 使用原版／NeoForge 注册表体系，禁止以自建 Map 和绑定流程替代标准注册生命周期。
- `MidnightCleanupEncounter` 的CRT定义使用Photon原生 `.fullscreen_graph.nbt` 资源，表达式、输入变量与节点接线保存在图资源中，支持资源重载；Java只引用资源和驱动事件生命周期，不动态构造该图或内嵌表达式正文。表达式算法和数值保持不变。

## 翻译登记

- 翻译键、文案及翻译工具的归属、`<类型>TranslationKeys` 直接归包、常量登记和重复转接类清理，统一遵守[翻译归属](java-and-packages.md#翻译归属)。不由单一 `HordeLang` 承载多个玩法的文案。
- 具体 Encounter 的命名注册声明同时提供名称键、中英文和实现工厂，由注册辅助方法登记名称翻译；名称键保持稳定，不再在TranslationKeys类重复登记。开幕标题、台词及命令提示等其他业务文案仍由对应TranslationKeys类登记，不通过独立 `init` 或另一份文案清单补登。

## 迁移影响与源码状态

业务实施时分别核对 Java 类型与引用、登记生命周期、Mixin 配置、网络与数据标识、翻译来源及资源引用；登记移动不能引入客户端到服务端的加载泄漏。目标包、类名和源码当前路径分开记录，已实现、待迁移和未验证明确区分。

领域命名和常量归属不改变已确认玩法规则或数值。既有系统契约仍从[系统索引](../../systems/README.md)定位；源码现状以实际文件为准。
