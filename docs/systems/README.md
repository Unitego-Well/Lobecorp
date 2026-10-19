# 系统文档索引

核对日期：2026-10-14。本文帮助修改前快速定位已有实现；文档可能不完整或过时，源码、IDEA
索引与锁定依赖优先。包迁移已完成，源码链接已按实际路径核对；检查结果见修改记录。

## 功能与入口

| 系统          | 文档                                             | 覆盖范围                                                 |
|---------------|--------------------------------------------------|----------------------------------------------------------|
| 注册与数据    | [注册、附件与数据契约](registration-data.md)     | DeferredRegister、自定义注册表、Capability、附件和持久化 |
| 实体与 AI     | [实体与 AI](entities-ai.md)                      | 实体类别、Brain、移动、状态与生命周期                    |
| 战斗关系 | [战斗关系](combat.md) | 阵营、群体、受击仇恨、共享目标与伤害资格 |
| 临时抓取      | [实体抓取](entity-grab.md)                       | 相对锚点、移动锁、瞬移脱离与双方生命周期清理             |
| 实体技能      | [技能系统](entity-skills.md)                     | 请求、运行态、连段、冷却、轻量效果                       |
| 命中框        | [命中框](hitbox.md)                              | 模板、形状、生命周期、命中与客户端快照                   |
| 动画          | [动画](animation.md)                             | GeckoLib、动画层、状态边沿与同步                         |
| 服务端动画与附着 | [服务端动画、根运动与空间附着](animation-runtime.md) | 完整 data 模型／动画、代码包装、显式实际位移、同生命周期绑定与客户端视觉覆盖 |
| 通用空间附着 | [通用空间附着](attachments.md) | 世界／实体／动画／最终渲染锚点、服务端与客户端独立绑定、物品整体动画与单一同步 |
| 玩家动画      | [玩家动画](player-animation.md)                  | GeckoLib 原生求值、原版动作来源、覆盖／混合、淡入淡出、第三人称皮肤与盔甲、显式开启的第一人称双臂与原版回退、开发日志 |
| 指挥家        | [指挥家](conductor.md)                           | 单位、控制、会话、目录、区块与命令                       |
| 动态视觉精细度 | [实体动画与模型 LOD](visual-quality.md) | 距离采样、模型细节、缓存与豁免 |
| 客户端 UI     | [客户端 UI](client-ui.md)                        | HUD、输入、镜头、头像与 LDLib2 资源                      |
| Photon 编辑器 | [Photon 编辑器](photon-editor.md)                | 设置、快捷键/项目保存、剪贴板、三轴曲线/缓动、标题、悬浮样式优化、模型原生场景与资源职责 |
| 粒子与渲染    | [粒子与渲染](particle-rendering.md)              | 循环、激活、运行参数、不透明像素遮挡/深度/混合和 Geo 桥接               |
| 世界文本粒子  | [文本粒子](text-particles.md)                    | Component 存储、逐字／中心显露、单字关键帧、同步、批量正文／描边和自动范围 |
| 屏幕伤害粒子  | [屏幕伤害粒子](screen-damage-particles.md)        | 结算伤害广播、近战首次居中后固定世界锚点、空间合并、生命比例动画、内容包装与对象池 |
| Photon／LDLib2 修复 | [修复对照](photon-ldlib2-fixes.md)              | 上游缺陷、兼容补丁、扩展自身修复、版本证据与升级复核               |
| Photon 依赖升级 | [发布与适配](photon-dependency-upgrade.md) | Photon／LDLib2／KilaGraph 更新内容、required 依赖、元数据来源与实际检查 |
| 发射器发射器  | [Photon 发射器发射器](photon-emitter-spawner.md) | 项目模板/完整 FX、调度、形状预览、子动画片段、实例参数与生命周期     |
| 命名粒子动画  | [Photon 命名动画](photon-named-animations.md)    | 共享 TL、项目专用组合测试、独立形参、子动画片段与迁移保护    |
| 模型特效      | [Photon 模型特效](photon-model-effects.md)      | `.mfxproj` 工程／`.mfx` 定义／`.mfxpack` 包、原生保存与导出管理、模型默认渲染／实例显式覆盖、部件阴影／双面／负尺寸、原生 Keymap、常规／动画场景与各片段时间轴状态、场景／窗口、默认白模、网格合并／环切／曲面基本体／UV 框选、层级右键／独立动画列表与时间轴、单模型发射器、采样工作区池／有界定义与依赖缓存／单面提前剔除、几何／UV／动画、私有材质槽／原生着色器预览与参数快照／依赖打包与应用、FX 时间轴／命名动画控制模型片段、粒子事件独立实例／默认动画播放 |
| 事件          | [事件入口](events.md)                            | 唯一订阅、分发顺序、发行侧与清理                         |
| 网络          | [网络协议](network.md)                           | ITo…Payload、方向、版本、权威状态与缓存                   |
| 资源与生成    | [资源与数据生成](resources-datagen.md)           | 手写资源、翻译、Provider、生成输出边界                   |
| Minecraft MCP | [Minecraft MCP](minecraft-mcp.md)                | 源码子项目、IDEA Debug、握手、截图、独占控制、端口协议与已知定位限制 |
| 特效 MCP | [Photon 特效 MCP](photon-effect-mcp.md) | 专用工具、工程打开、原生资源读写／纹理导入、透视与区域截图、模型状态去重、参数范围、版本与历史、原生保存／导出、教程冲击波与四变量 |
| AI 特效制作 | [制作技能](../../.codex/skills/lobecorp-effect-authoring/SKILL.md) | 参考视频分解、原生模型／ShaderGraph／粒子制作、固定操作批处理、变量和阶段对照、工程交付 |
| 修改记录      | [修改记录](changes.md)                           | 日期、范围、实际检查、缺口与未验证项                     |

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
