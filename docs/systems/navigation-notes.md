# 系统导航补充与历史证据

从系统索引移出的详细入口说明，原核对日期为 2026-10-14；本次重组不构成源码或运行重新验收。默认只在查询对应功能、独立回归入口或历史证据边界时读取相关段落。当前入口见[系统索引](README.md)，项目规则见[根规范](../../AGENTS.md)。

附件同步遵循简单值使用 Neo、复杂状态使用功能域网络包的规则，首次快照与增量边界见[网络协议](network.md#简单附件与复杂状态)。

统一命令 `/lobecorp animation player|attachment|item` 提供指定玩家、播放速度、淡入淡出和过渡类型；附着示例使用真实玩家的背部、手部与世界锚点，物品整体动画独立播放。入口见[动画调试命令](animation-runtime.md#动画调试命令)；既有实体技能仍未迁移。

长淡化的连续旋转由 [LcRotationTransitionUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcRotationTransitionUtil.java) 提供，Gecko、玩家与物品分别拥有过渡状态，NORMAL 保留原始 Euler 通道。独立检查入口为[旋转数学回归](../../src/test/java/org/unitego/lobecorp/util/animation/LcRotationTransitionRegression.java)与[玩家贡献回归](../../src/test/java/org/unitego/lobecorp/client/animation/player/LcPlayerRotationRegression.java)；执行结果及调试链路限制见[修改记录](changes.md)。

主源码分类覆盖
root/api、world、client、conductor、animation、attachment、registry、events/event、network、mixin、config、particle、generator、serialization、util。工具与
Codec 辅助按其使用系统导航；本索引不声称逐个公共方法或未实现占位实体都有完整说明。

清道夫清扫／重组的自动接近、0.5 格工作间距和失败重定位入口见[实体与 AI](entities-ai.md#清道夫清扫与重组的接近位置)；指挥家持续工作通过相同工具推进，说明见[指挥家](conductor.md#清道夫持续清理与重组)。

清道夫自主闲逛与战斗追击的分离、对齐和聚合见[地面鸟群移动](entities-ai.md#清道夫地面鸟群移动)；群体转向复用原寻路，技能瞄准、清理精确接近和指挥家控制优先。

清道夫闲逛常速、实体目标 1 秒线性加速和常规移动 2 格减速边界见[移动速度](entities-ai.md#清道夫移动速度与目标加速)；位置指令优先级、MOVE 速度上限和精确清理间距保留。

清道夫飞扑的低弧求解、落点预检和物理状态恢复见[飞扑轨迹](entities-ai.md#清道夫飞扑低弧轨迹)；三阶段动作见[动画](animation.md)，飞扑不再附加特殊 root 俯仰。数学入口为 [EntityLeapUtil](../../src/main/java/org/unitego/lobecorp/util/entity/EntityLeapUtil.java)，独立 JUnit 入口为 [EntityLeapUtilTest](../../src/test/java/org/unitego/lobecorp/util/entity/EntityLeapUtilTest.java)。

本次接口命名整理只覆盖 12 个已确认的顶层常规接口，旧名与新名对应及实际验证见 [修改记录](changes.md)。桥接、Mixin Accessor、内部回调和纯注册／工具／常量容器保留名称。

## 修改流程

项目专项 Skill 与全局 MCP 的整理入口见 [Minecraft MCP 配置维护](minecraft-mcp.md#skill-与全局配置维护2026-10-14)，范围、备份和实际检查见 [修改记录](changes.md)。项目 6 个专项入口保留，当前 API 与包路径以系统文档和 IDEA 解析为准；历史迁移参考不作为新的实施计划。

Photon 独立回归的 JUnit 测试入口位于 `src/test/java`；时钟／参数与 GUI 数据检查见 [命名动画](photon-named-animations.md#独立回归测试入口)，模型交换、网格与拓扑检查见 [模型特效](photon-model-effects.md#独立回归测试入口)。原 main 入口已移除，加载环境检查仍使用原菜单／调试接口。

1. 查看本索引和目标系统文档，先确定已有能力、职责和约束。
2. 通过 IDEA 查看当前源码、类型、引用和锁定依赖。文档没有记录时继续查源码，不能据此新建重复系统。
3. 按已确认范围修改；跨系统依赖、网络方向和资源输出遵守项目规范。注释说明统一使用中文，必要技术片段及第三方版权/许可证原文保持。
4. 修改后同步相关系统文档、入口与链接，在 changes.md 写实际结果。计划、已实现、历史证据和未验证内容分别说明。

## 架构与版本参考

- [项目资料总索引](../project-reference/README.md)
- [开发规则](../../AGENTS.md) 与 [工作流程](../project-reference/02-rules-and-workflow.md)
- [Java 25 语法与注释规范](../java-25-language-and-comments.md)：正式语法、Markdown Javadoc、框架兼容与预览边界。
- [Java 架构](../java-architecture.md) 与 [批准的包迁移清单](../java-package-organization-plan.md)
- [锁定版本](../../gradle/libs.versions.toml)：Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25、Photon 26.1.2.3、LDLib2
  26.1.2.42、KilaGraph 26.1.0.15、GeckoLib 5.5.2、ParticleStorm 1.4.4。

## 证据边界

本轮按系统分工核对仓库源码并补充文档。编辑器诊断、编译/Data 的实际完成状态统一记录在
changes.md；静态文档和编译成功不能证明游戏性能、透明排序、快捷键或剪贴板交互已经通过运行验收。外部库的完整 API 以当前 IDEA
依赖解析为准，不将其他版本示例当作当前能力。

玩家第一人称双臂／袖子、当前持物、自定义姿态与策略切换、完成淡出、地图背景及第三人称完整模型的既有运行检查属于历史证据。当前改为动画策略显式开启双臂；无动画、未声明或权重为 0 时执行原版第一人称手臂持物流程。显式门控与原版回退的实际检查以本轮修改记录为准，不能沿用旧版无动画双臂截图宣称通过。地图数据内容、其他物品及第三方兼容场景仍未验收，具体边界见[玩家动画](player-animation.md#示例与验证边界)。
