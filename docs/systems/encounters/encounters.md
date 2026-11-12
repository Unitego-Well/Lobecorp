# Encounter 与 Horde API

当前源码结构（2026-11-10）：本文说明本次迁移后的职责和行为；构建与 Data 证据另记任务报告，不代表游戏验收。

## 定义与注册

`world.encounter` 放置 `IEncounterDefinition`、普通可继承的 `EncounterDefinition<T>` 及玩法实现。`registry.LcRegistries.ENCOUNTER` 使用 NeoForge `RegistryBuilder` 建立注册表，经既有 `NewRegistryEvent` 入口登记；`registry.world.encounter.LcEncounters` 通过 `DeferredRegister` 和 `MIDNIGHT_CLEANUP` 常量注册定义，构造函数中只挂接事件总线。实现与对应 Horde profile 在注册事件阶段创建、接入，不在声明阶段读取延迟实体 holder。

`api.encounter.Encounters`通过真实注册表查询定义并编排具体玩法，不持有第二份定义Map；`api.horde.Hordes`提供通用浪潮API。领域实现、运行状态和持久化保留在`world`；Horde不反向依赖深夜清扫。

## 深夜清扫倒计时与收尾

计数器沿用原先提前到达十秒的显示时机，显示到当地停止生成边界的剩余时间。玩家位置投影至路线，使用 `stop + station / speed - time - spawnCutoffTicks` 计算剩余刻数；深夜清扫的截止提前量为100刻，即五秒，与禁止新增参战者的判断共用。

剩余值小于零时，对同一玩家、同一遭遇只发送一次收尾展示，隐藏倒计时并显示结束字幕。该时点不停止浪潮，不清空实体、路线或持续环境效果；浪潮实际结束时不会重复播放字幕。若仍在开幕受控阶段，先解除控制再进入字幕。

完整开幕、Esc跳过、字幕溶解、天空覆盖和原有末尾恢复继续使用已有演出公共控制。客户端由 `EncounterClient` 路由到具体 `IEncounterDisplay`，HUD通过 `EncounterHudRegistry` 登记；Horde资源准备和回收经 `HordeClientHooks` 接入。

## 命令与网络

`encounter template midnight_cleanup`、`encounter start`、`stop`、`clear`、`list`、`stats` 处理玩法。原 `horde` 命令保留通用路线和浪潮能力；直接启动原始 Horde 不自动启用遭遇演出。

协议版本33。遭遇展示和跳过使用 `EncounterStatePayload`、`EncounterSkipPayload`；同步与存档保留已有资源标识，增加展示所有权字段，服务器与客户端须同步更新。

## 翻译与资源

注册项名称与中英文随所属注册声明登记；其余业务键类归入`registry.translation`，命名为`<类型>TranslationKeys`，在命名常量声明处登记；需要注解的键保留编译期常量。Data直接消费注册与翻译常量已登记的文案，避免另一份名称清单。

深夜清扫专属非注册规则位于 `MidnightCleanupEncounter`；大气表达式正文从客户端资源读取。注册声明、客户端资源与服务端可加载的定义保持各自职责。

## 2026-11-10 注册翻译、公共入口与标题

`LcSoundEvents.register(path, enUs, zhCn, range)`在登记延迟holder时同时登记声音文案，七项清道夫声音使用既有`sound.lobecorp.*`语言键规则；不提前读取holder，不新增`sounds.json`字幕行为。`LcEncounters`的注册声明同时提供名称键、中英文和实现工厂，保留`horde.lobecorp.midnight_cleanup`；删除TranslationKeys中的重复名称登记，其余台词键保持独立。

开场标题两行为“都市的清道夫／深夜清扫”，完成时为“都市的清道夫／深夜清扫完成”。英文分别为“The City’s Sweepers／Midnight Cleanup”和“The City’s Sweepers／Midnight Cleanup Complete”，属于本次确认的游戏语境译法，不宣称完整标题已有官方原文。原入场和结束叙述字幕、动画、颜色与时序不变，完整开幕和简版字幕共用完成态名称选择。

| 类型 | 原位置 | 当前位置 | 影响 |
|---|---|---|---|
| Encounters | world.encounter | api.encounter | IDEA语义移动，更新命令、服务端跳过包和世界tick引用 |
| Hordes | world.horde | api.horde | IDEA语义移动，更新运行时、同步、命令、遭遇入口和Bench引用 |

资源ID、注册生命周期、存档与网络协议不变；Hitbox、技能特效、物品附着等其他系统的入口拆分仍需独立盘点。验证结果见[修改记录](../../records/changes.md)，不以源码迁移替代运行验收。

本轮验证：受影响Java的IDEA错误检查、API方法体与叙述字幕保留检查通过。主/Bench构建在主编译阶段被四份本轮未修改文件中五处缺失技能注册表键的错误阻断；Bench编译和DataGen未完成，生成语言文件未手工修改。未进行游戏或视觉验收。

## 深夜清扫三段背景音乐（2026-11-10）

音乐由客户端 `MidnightCleanupMusic` 管理，与清道夫实体和动画声音独立。开场消息开始播放 `start.ogg`，自然播完接入 `loop.ogg` 的零延迟流式循环；现有完成字幕消息到达时立即播放一次 `end.ogg`，当前段在20游戏刻（约1秒）内线性淡出，同时结束段从静音线性淡入；淡出结束释放旧实例，结束段继续播完，不等待浪潮实体全部移除。没有收到开场消息时，到达既有 HUD 提醒范围可开始音乐。重复消息不重播开头；多个重叠遭遇共用一条音乐，最后一个完成时播放结束段。遭遇被移除且没有其它播放所有者时直接停止；已开始的尾奏允许播完。退出世界或切换会话沿既有 Encounter clear 链清理。

`MidnightCleanupSoundEvents` 声明 `encounter.midnight_cleanup.start/loop/end` 三项声音及中英文名称；`SoundGenerator` 生成 `stream: true` 描述。资源位于 `assets/lobecorp/sounds/encounter/midnight_cleanup/`，用户提供的 `intro.ogg`、`drop(loop).ogg`、`outro.ogg` 在资源内分别命名为 `start.ogg`、`loop.ogg`、`end.ogg`，三份文件字节不变。名称统一为“遭遇／深夜清扫／开始、持续、结束”，英文同步登记。交叉淡化只改变两条声音实例各自的音量，暂停时冻结，期间继续避让原版音乐；不会修改全局音乐音量。播放使用 MUSIC 分类、相对位置及无距离衰减，遵循玩家音乐音量。音乐活动期间，既有客户端事件入口通过 `SelectMusicEvent` 暂停原版音乐选择，结束后自动解除，不修改用户选项。

循环段由原版音频引擎续接；开始段到循环段的切换在客户端 tick 检测自然结束后发生，不承诺采样级无缝。IDEA错误检查、复用现存Minecraft制品的 compileJava/runData、processResources及资源哈希检查通过；常规依赖重建被正在运行的游戏文件锁阻断，未关闭游戏。未进行游戏内试听，实际衔接与音量仍待听觉验收。
