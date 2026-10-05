# 系统文档索引

核对日期：2026-10-05。本文帮助修改前快速定位已有实现；文档可能不完整或过时，源码、IDEA
索引与锁定依赖优先。包迁移已完成，源码链接已按实际路径核对；检查结果见修改记录。

## 功能与入口

| 系统          | 文档                                             | 覆盖范围                                                 |
|---------------|--------------------------------------------------|----------------------------------------------------------|
| 注册与数据    | [注册、附件与数据契约](registration-data.md)     | DeferredRegister、自定义注册表、Capability、附件和持久化 |
| 实体与 AI     | [实体与 AI](entities-ai.md)                      | 实体类别、Brain、移动、状态与生命周期                    |
| 实体技能      | [技能系统](entity-skills.md)                     | 请求、运行态、连段、冷却、轻量效果                       |
| 命中框        | [命中框](hitbox.md)                              | 模板、形状、生命周期、命中与客户端快照                   |
| 动画          | [动画](animation.md)                             | GeckoLib、动画层、状态边沿与同步                         |
| 指挥家        | [指挥家](conductor.md)                           | 单位、控制、会话、目录、区块与命令                       |
| 客户端 UI     | [客户端 UI](client-ui.md)                        | HUD、输入、镜头、头像与 LDLib2 资源                      |
| Photon 编辑器 | [Photon 编辑器](photon-editor.md)                | 设置、快捷键、剪贴板、曲线、重命名与文档                 |
| 粒子与渲染    | [粒子与渲染](particle-rendering.md)              | 循环、激活、运行参数、深度/混合和 Geo 桥接               |
| 发射器发射器  | [Photon 发射器发射器](photon-emitter-spawner.md) | 项目模板/完整 FX、调度、实例参数和生命周期               |
| 事件          | [事件入口](events.md)                            | 唯一订阅、分发顺序、发行侧与清理                         |
| 网络          | [网络协议](network.md)                           | To…Payload、方向、版本、权威状态与缓存                   |
| 资源与生成    | [资源与数据生成](resources-datagen.md)           | 手写资源、翻译、Provider、生成输出边界                   |
| 修改记录      | [修改记录](changes.md)                           | 日期、范围、实际检查、缺口与未验证项                     |

主源码分类覆盖
root/api、world、client、conductor、animation、registry、events/event、network、mixin、config、particle、generator、serialization、util。工具与
Codec 辅助按其使用系统导航；本索引不声称逐个公共方法或未实现占位实体都有完整说明。

## 修改流程

1. 查看本索引和目标系统文档，先确定已有能力、职责和约束。
2. 通过 IDEA 查看当前源码、类型、引用和锁定依赖。文档没有记录时继续查源码，不能据此新建重复系统。
3. 按已确认范围修改；跨系统依赖、网络方向和资源输出遵守项目规范。
4. 修改后同步相关系统文档、入口与链接，在 changes.md 写实际结果。计划、已实现、历史证据和未验证内容分别说明。

## 架构与版本参考

- [项目资料总索引](../project-reference/README.md)
- [开发规则](../../AGENTS.md) 与 [工作流程](../project-reference/02-rules-and-workflow.md)
- [Java 架构](../java-architecture.md) 与 [批准的包迁移清单](../java-package-organization-plan.md)
- [锁定版本](../../gradle/libs.versions.toml)：Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25、Photon 26.1.2.2、LDLib2
  26.1.2.39、GeckoLib 5.5.2、ParticleStorm 1.4.4。

## 证据边界

本轮按系统分工核对仓库源码并补充文档。编辑器诊断、编译/Data 的实际完成状态统一记录在
changes.md；静态文档和编译成功不能证明游戏性能、透明排序、快捷键或剪贴板交互已经通过运行验收。外部库的完整 API 以当前 IDEA
依赖解析为准，不将其他版本示例当作当前能力。
