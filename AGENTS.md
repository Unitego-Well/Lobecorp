# Lobecorp 项目规则入口

通用沟通、授权、执行、验证、协作与清理以[共享全局工作协议](C:/Users/wang_/.agents/AGENTS.md)为唯一正文；文档分层遵循[通用文档管理](C:/Users/wang_/.agents/standards/document-management.md)。项目只维护自身技术约束；历史快照中的通用条款与共享协议不一致时，以共享协议为准。

本入口适用于当前仓库；嵌套仓库（例如 `run/client/*`）内部的 AGENTS.md 仅对其自身仓库生效。

按当前请求选择读取范围：问候、闲聊、简单确认及与项目无关的问题直接回复，不调用工具或加载本入口链接。工作目录位于本仓库不代表已经开始开发任务。已注入或已读且未变化的规则不重复读取。

涉及项目时，先定位当前问题，只读取下表与当前动作直接相关的正文或章节；表格是可选导航，不是开工清单。仅在实施开发且需要对应约束时，读取实际可用的 dev-standards 和[项目薄技能](.agents/skills/lobecorp-development-rules/SKILL.md)中的相关内容。文档编辑不自动加载 Java、游戏、特效或 DS 流程；简单操作不额外生成计划、报告或交接。

| 当前任务 | 适用正文 |
|---|---|
| 功能定位、当前契约与项目工具链 | [系统索引](docs/systems/README.md)、[工具手册](docs/tooling/README.md)及其中匹配入口；[智能路由](C:/Users/wang_/.agents/skills/windows-jvm-smart-routing/SKILL.md) |
| Java、接口、常量、包分类、注册、翻译、Bench/Mixin 边界 | [Java 与包规范](docs/standards/development/java-and-packages.md)；Java 语法和注释另读 [Java 25 专项](docs/standards/development/java-25-language-and-comments.md) |
| Encounter、Horde、具体玩法归属、玩法常量与翻译登记 | [Encounter 结构规范](docs/standards/development/encounter-structure.md) |
| 工作树、外部制品、测试场景、模型格式、资源命名、接口复用、项目收尾 | [工作流程](docs/standards/workflow/README.md)中的对应专项章节 |
| 实体、AI、技能、动画、同步或星束命中 | [实体契约](docs/standards/development/entity-contracts.md)对应章节及实际可用专项 Skill |
| 进入游戏、截图、输入、连接或 Debug | [后台输入与测试边界](docs/standards/workflow/game-testing.md#后台输入与测试边界)；Minecraft MCP Skill 的旧路径缺失状态见[问题汇总](docs/issues.md)，先核对实际入口；编辑器嵌入游戏，快速测试世界流程合批 |
| 特效制作、修改、对照验收 | [系统索引](docs/systems/README.md)中的特效入口及实际可用专项 Skill；旧特效 Skill 路径缺失状态见[问题汇总](docs/issues.md)，制作与验收使用透视 |
| 项目报告、计划、记忆、分支进度与交接 | [代理文档归属](docs/standards/documentation/agent-docs.md)；[系统文档与修改记录](docs/standards/documentation/agent-docs.md#系统文档与修改记录) |
| DS 委派或接续 | [共享 DS 协作](C:/Users/wang_/.agents/standards/ds-collaboration.md)；项目版本化[协作入口](docs/standards/collaboration/ds-collaboration.md)及 [DS 执行规则](docs/standards/collaboration/ds-execution-rules.md)，仅适用 DS 专属部分 |
| 子代理配置与项目分工 | [子代理模型、渠道与最低推理档位](docs/standards/collaboration/subagents.md)及[工作区入口](docs/tooling/workspaces/workspace-layout.md) |

项目共用报告、经验及分支专属材料按代理文档正文归属；原始项目只链接各工作入口，不复制其他工作的进度与问题。公共规范随 Git 提交同步，保留分支专属补充与冲突；共享协议及专项工具能力以当前实际入口为准。

本次规范重整状态（2026-11-10）：结构与迁移后的源码契约见[Encounter 与 Horde API](docs/systems/encounters/encounters.md)，验证结果以实际任务报告为准；女皇子分支当前动画、激光及 Photon 实现不在本次业务整理范围。

已知目标路径时直接读取所需正文；仅在无法定位时使用[文档总览](docs/README.md)或[技能导航](.agents/README.md)查找匹配入口，找到即停止展开。不要递归读取链接、默认加载全部技能，或为“准备可能用到”而预加载历史、记忆和参考。
