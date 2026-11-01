# Lobecorp 项目开发规范

公共规范维护来源为原始项目的已跟踪规范；本文件是分支内完整的版本化快照，按提交同步，不依赖相邻工作树或未提交资料。分支专属补充保留在本分支；同步出现冲突时必须保留，不将分支状态复制到原始项目。

> 除用户当前明确指令外，本文件是 Lobecorp 项目的最高优先级开发规范。
> 嵌套仓库（例如 `run/client/*`）内部的 `AGENTS.md` 只对其自身仓库生效。

本入口与所链接的项目正文供读取它们的客户端共享。GPT/Codex 的模型委派按其全局规则执行，不作为 DS/DeepSeek Harness 的模型要求；DS/DeepSeek-Harness 必须同时执行[DS 执行规则](docs/standards/ds-execution-rules.md)；工具能力以各客户端实际提供的接口为准。

## 开始任务前

先读取相关 Skill、[系统索引](docs/systems/README.md)中匹配当前任务的入口及下表适用规范，再制定简要计划。澄清、命名与兼容规则唯一正文见[开始任务前](docs/standards/workflow.md#开始任务前)。

## DS 执行规则

DS 会话除本文件外必须执行[DS 执行规则](docs/standards/ds-execution-rules.md)：开工门禁、提问与异议、中文与输出格式、每步记录、每约 5 分钟可见进度、IDEA/MCP 优先与收尾验证。GPT/Codex 不套用其中 DS 专属的节奏与输出格式条款。

## 文档管理（DS 与 GPT 共用）

报告、会话交接、计划与记忆放在 `.agent/`，不提交 Git；结构与命名见[统一文档管理规则](docs/standards/agent-docs.md)。Lobecorp 各工作区通用报告、记忆与经验统一归集原始仓库根 `.agent/`；各工作专属进度、问题与验收证据保留所属工作区，原入口链接共享正文。上级工作区根 `.agent/` 仍放跨项目内容；新增、迁移、合并或归档时同步索引，备份旧正文并修复引用。

## 系统文档与修改记录

文档、修改记录、所属工作总览与唯一问题清单的维护规则见[系统文档与修改记录](docs/standards/workflow.md#系统文档与修改记录)。原始项目仅链接各工作入口，不复制其他工作的进度与问题。

## 工具、编辑与并行

子代理只能使用所选模型支持的最低推理档位；创建、接续或重新委派时显式指定，不因任务复杂而自行提高。该要求仅适用于子代理；用户对当前任务的明确指令优先。具体执行见[子代理最低推理档位](docs/standards/workflow.md#子代理最低推理档位)。

简单核对、查找、批量比对等重复性任务交给 DS；负责人负责判断、审查、冲突处理和集成。其他简单、边界明确的任务也可委派给 DS。没有可用 DS 接口时如实说明，在可用能力内推进，不把其他模型称为 DS。DS 出现异常问题或达到多轮上下文门槛时，必须交接到新对话，确认接续后归档旧对话，不在旧上下文反复催继续。具体委派、回报与巡检边界见[DS 简单任务委派与停止检查](docs/standards/workflow.md#ds-简单任务委派与停止检查)。

IDEA 专属编辑、后台 Git、工作树隔离、外部 Minecraft MCP 制品、基准测试与已有修改保护统一见[工具、编辑与并行](docs/standards/workflow.md#工具编辑与并行)。验证与每任务中文提交规则见[修改规模与收尾](docs/standards/workflow.md#修改规模与收尾)及[版本与验证边界](docs/standards/workflow.md#版本与验证边界)。

## 按任务读取

下列规范是项目规则的组成部分，满足条件时必须读取适用章节；不需要同时加载所有文件。

| 当前任务 | 必须读取的入口 |
|---|---|
| 修改文件、格式化、协作或 Git 保护 | [工作流程：工具、编辑与并行](docs/standards/workflow.md#工具编辑与并行) |
| 涉及多个系统／文件、重构、包迁移、数据结构或协议变化；新增翻译键 | [修改规模与收尾](docs/standards/workflow.md#修改规模与收尾) |
| 新增或复用接口、系统、依赖、注册项 | [复用已有能力](docs/standards/workflow.md#复用已有能力) |
| 兼容、迁移、旧格式读取 | [开始任务前](docs/standards/workflow.md#开始任务前)：先确认是否兼容；模型文件仅用 .mfxproj／.mfx／.mfxpack，不兼容此前格式 |
| Java、注释、常量、可见性、接口、系统边界或包整理 | [Java 与包规范](docs/standards/java-and-packages.md)；Java 任务还须读 [Java 25 专项](docs/java-25-language-and-comments.md) |
| 实体、AI、技能、动画、同步或星束命中 | [实体契约](docs/standards/entity-contracts.md)适用章节，再读对应项目 Skill |
| 进入游戏、截图、控制、连接或 Debug | Minecraft MCP Skill（路径 `.codex/skills/lobecorp-minecraft-mcp/SKILL.md` 当前缺失，须先核对入口并读取，缺失状态见[问题汇总](docs/systems/horde-issues.md)）：按[后台输入与测试边界](docs/standards/workflow.md#后台输入与测试边界)核对窗口状态，编辑器嵌入游戏，快速测试世界流程合批 |
| 制作、修改或对照验收特效 | 特效制作 Skill（路径 `.codex/skills/lobecorp-effect-authoring/SKILL.md` 当前缺失，须先核对入口并读取，缺失状态见[问题汇总](docs/systems/horde-issues.md)）：按问题读取制作参考，使用透视 |

## Horde 专属补充

- 开发与验收工具链固定为 **spark、BenchMod、mc mcp、idea mcp、debug**，各环节用途与入口见[项目工具链](docs/systems/README.md#项目工具链)；不得用其他方式替代对应环节。
