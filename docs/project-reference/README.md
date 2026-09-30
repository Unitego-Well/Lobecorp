# Lobecorp 项目资料总索引

盘点时间：2026-09-30

本文档集整理当前仓库、仓库内已有的本地资料以及官方/一手外部参考。它是开发者、IDE 工具和自动化代理的导航入口；实际代码、当前 IDEA 索引和版本配置优先于本文档中的概括。

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01-project-baseline.md](01-project-baseline.md) | 项目身份、版本、目录、工作区和文件边界 |
| [02-rules-and-workflow.md](02-rules-and-workflow.md) | 任务边界、工具选择、修改、验证和专项规则 |
| [03-architecture-and-data.md](03-architecture-and-data.md) | Java 包职责、注册入口、实体技能、指挥家、事件和同步架构 |
| [04-api-registries-resources.md](04-api-registries-resources.md) | 项目内部 API、注册表、Payload、资源和依赖 |
| [05-ide-mcp-skills.md](05-ide-mcp-skills.md) | IDEA、Index MCP、调试器、项目 Skill 和本地系统配置 |
| [06-local-mcp-reference.md](06-local-mcp-reference.md) | 仓库内嵌 `minecraft-mod-mcp` 快照及其本地文档 |
| [07-evidence-and-gaps.md](07-evidence-and-gaps.md) | 已检查证据、验证结果、未验证项目和维护清单 |
| [08-external-development-reference.md](08-external-development-reference.md) | 官方 Java、NeoForge、Gradle、IDEA、MCP 和 API 参考 |

## 资料优先级

1. 当前任务提供的项目规则和用户明确要求。
2. 当前检出内容、当前版本配置、当前 IDEA 索引和编辑器诊断。
3. `.codex/standards/` 与 `.codex/skills/` 中的 Lobecorp 专项规则。
4. `.claude/skills/` 中的通用技能及其 references。
5. [08-external-development-reference.md](08-external-development-reference.md) 中列出的官方/一手参考，仅用于版本和概念核对。
6. 其他 `docs/`、`memory/` 和仓库内嵌工具的说明文档。

文档中出现“已确认”时，指本次读取到文件或 IDE 结果；出现“未验证”时，不得将其理解为构建、运行或游戏验收结论。

## 本次范围

- 包含：Lobecorp 主模组源代码、资源、Gradle 配置、元数据、事件/网络/注册入口、项目规则、Skill、IDE/MCP 配置、仓库内嵌 MCP 资料和官方外部参考。
- 不包含：社区文章、外部仓库的最新状态、构建目录的反编译结果、`node_modules` 依赖说明、运行存档内容、日志中的凭据和任何 `.env` 内容；外部资料只核对官方/一手页面。
- 本轮已通过 IDEA 修改当前任务涉及的源码包路径、网络接口、注册入口、可见性和本参考文档，并通过官方网页核对外部参考；未手工修改构建产物、`runData` 输出目录或运行数据，也未改动 `.codex`/`.claude` 规则。

## 维护约定

- Minecraft、NeoForge、Java、Gradle 或主要依赖版本变化时，先更新 [01-project-baseline.md](01-project-baseline.md) 和 [04-api-registries-resources.md](04-api-registries-resources.md)。
- 包职责、事件入口、数据附件或 Payload 变化时，更新 [03-architecture-and-data.md](03-architecture-and-data.md)。
- IDE、MCP 或 Skill 目录变化时，更新 [05-ide-mcp-skills.md](05-ide-mcp-skills.md) 和 [06-local-mcp-reference.md](06-local-mcp-reference.md)。
- 每次更新都在 [07-evidence-and-gaps.md](07-evidence-and-gaps.md) 记录实际检查方式；未执行的构建、测试、运行和游戏操作不能写成成功。
