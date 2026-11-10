# Encounter 与 Horde API

当前源码结构（2026-11-10）：本文说明本次迁移后的职责和行为；构建与 Data 证据另记任务报告，不代表游戏验收。

## 定义与注册

`world.encounter` 放置 `IEncounterDefinition`、普通可继承的 `EncounterDefinition<T>` 及玩法实现。`registry.LcRegistries.ENCOUNTER` 使用 NeoForge `RegistryBuilder` 建立注册表，经既有 `NewRegistryEvent` 入口登记；`registry.encounter.LcEncounters` 通过 `DeferredRegister` 和 `MIDNIGHT_CLEANUP` 常量注册定义，构造函数中只挂接事件总线。实现与对应 Horde profile 在注册事件阶段创建、接入，不在声明阶段读取延迟实体 holder。

`registry.encounter.EncounterDefinitions` 通过真实注册表查询，不持有第二份 Map。`world.encounter.Encounters` 编排具体玩法，`world.horde.Hordes` 提供通用浪潮 API；Horde 不反向依赖深夜清扫。

## 深夜清扫倒计时与收尾

计数器沿用原先提前到达十秒的显示时机，显示到当地停止生成边界的剩余时间。玩家位置投影至路线，使用 `stop + station / speed - time - spawnCutoffTicks` 计算剩余刻数；深夜清扫的截止提前量为100刻，即五秒，与禁止新增参战者的判断共用。

剩余值小于零时，对同一玩家、同一遭遇只发送一次收尾展示，隐藏倒计时并显示结束字幕。该时点不停止浪潮，不清空实体、路线或持续环境效果；浪潮实际结束时不会重复播放字幕。若仍在开幕受控阶段，先解除控制再进入字幕。

完整开幕、Esc跳过、字幕溶解、天空覆盖和原有末尾恢复继续使用已有演出公共控制。客户端由 `EncounterClient` 路由到具体 `IEncounterDisplay`，HUD通过 `EncounterHudRegistry` 登记；Horde资源准备和回收经 `HordeClientHooks` 接入。

## 命令与网络

`encounter template midnight_cleanup`、`encounter start`、`stop`、`clear`、`list`、`stats` 处理玩法。原 `horde` 命令保留通用路线和浪潮能力；直接启动原始 Horde 不自动启用遭遇演出。

协议版本33。遭遇展示和跳过使用 `EncounterStatePayload`、`EncounterSkipPayload`；同步与存档保留已有资源标识，增加展示所有权字段，服务器与客户端须同步更新。

## 翻译与资源

所有类型键类直接归入 `registry.translation`，命名为 `<类型>TranslationKeys`。键与中英文在命名常量声明处登记；需要注解的键保留编译期常量。Data加载声明来源后导出，避免另一份文案清单。

深夜清扫专属非注册规则位于 `MidnightCleanupEncounter`；大气表达式正文从客户端资源读取。注册声明、客户端资源与服务端可加载的定义保持各自职责。
