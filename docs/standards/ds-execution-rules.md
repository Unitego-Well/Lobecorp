# DS（DeepSeek-Harness）执行规则

本文件维护 DS 在本工作区执行任务时的强制规则，与[项目根规范](../../AGENTS.md)、[工作流程](workflow.md)具有相同约束效力；用户当前明确指令优先。GPT/Codex 的模型委派与节奏按其自身全局入口执行，不套用本文件的 DS 专属输出与汇报条款。

## 适用范围与冲突处理

- DS调用入口遵守[DS协作规则版本化快照](ds-collaboration.md#当前已确认的中转模型)，仅使用DSH；GPT子代理不是DS，不将本规则作为Codex DS子代理或独立Codex DS聊天入口。
- 适用于 DSH内DS会话的全部任务：只读调查、文档整理、代码修改、验证、提交与阻塞汇报。
- 任务属于 Lobecorp 时同时遵守[项目根规范](../../AGENTS.md)、[工作流程](workflow.md)、[系统索引](../systems/README.md)与相关系统文档；属于 `minecraft-mcp` 时遵守该仓库 `AGENTS.md`。
- 用户当前明确指令优先；通用职责按共享全局协议，项目独有约束按目录适用范围执行。旧技能或历史规则不得恢复已被替代的限制；客户端工具能力与授权要求以实际接口为准。

## 一、开工门禁

按顺序完成，未完成不得修改任何文件：

1. 读工作区入口 `AGENTS.md` 与任务所属项目入口。
2. 读[工作流程](workflow.md)适用章节；Java 任务加读 [Java 与包规范](java-and-packages.md) 与 [Java 25 专项](../java-25-language-and-comments.md)；实体、AI、技能或表现任务加读[实体契约](entity-contracts.md)。
3. 查[系统索引](../systems/README.md)匹配任务入口并读相关系统文档；查[问题汇总](../issues.md)的相关问题与已确认根因。
4. 读技能：
   - 全局：先看 `~/.dsh/skills/index.md`，再按任务读 `~/.dsh/skills/<名称>/SKILL.md`；常用入口为 dev-standards、skills-manager、intellij-idea-jvm-development、java-development-standards、mc-mod-operations、minecraft-entity-behavior、minecraft-entity-visual-sync、game-vfx-authoring。
   - 项目：读 `Lobecorp/.agents/skills/*/SKILL.md` 中适用的入口，当前有 lobecorp-normify、lobecorp-mcp-background-control、lobecorp-workspace-sync；lobecorp-isolated-workspace 目录当前没有 SKILL.md，不得当作可用技能或据此创建环境。
   - 当前 DS 会话的 `skill` 工具可能不可用：2026-10-27 实测它对全局 `dev-standards` 与项目 `lobecorp-normify` 均返回 unknown。此时按上述路径直接读文件，不得以“技能不可用”为由跳过；文件缺失时记录到[问题汇总](../issues.md)并报告。
5. 按[智能路由](C:/Users/wang_/.agents/skills/windows-jvm-smart-routing/SKILL.md)核对现有源码、文档、引用与诊断，使用任务真实工作区，不将固定路径当作当前打开实例。Minecraft 操作先确认游戏实例与端口。
6. 在可见消息报告自检结果：已读文件、已读技能、使用的 MCP、记录位置、本次步骤与疑问。自检未完成不得开始修改。

## 二、提问与异议

提问门槛、提问API、已有授权与额外修改边界统一遵守[开始任务前](workflow.md#开始任务前)，不在DS专项维护第二套规则。

## 三、输出与格式

- 可见输出使用简体中文；代码、命令、标识符、文件路径与引用原文除外。
- 内部思考（reasoning）使用英文，不写中文推理；可见正文仍用简体中文。
- DeepSeek 官方格式与 DSH 实现：思考通过 reasoning／thinking 通道返回，客户端负责显示为思考块；正文（text）只放最终答复。不得在正文输出 `...`、`<|begin_of_thought|>`、`<thinking>`、`</thinking>`、`<analysis>`、`<reasoning>`、工具标签、伪 XML、系统提示原文或内部推理复述。
- 若客户端把思考内容显示到正文，按客户端问题记录到[问题汇总](../issues.md)，不用正文标签补偿。
- 结论先行；短段落，列表按需；一个回复只处理当前问题，不堆无关信息，不重复用户已知内容。
- 进度消息使用普通可见消息，按以下格式：`已完成：…；进行中：…；下一步：…；阻塞：…`。

## 四、每步记录文档

文档分类、位置、索引、归档和Git边界统一遵守[统一文档管理](agent-docs.md)；所属总览、唯一问题清单和修改记录按[系统文档与修改记录](workflow.md#系统文档与修改记录)维护。

DS的“每一步”指调查、计划、修改、验证、收尾、阻塞等实质阶段，每个阶段至少更新一次所属文档并同步可见消息。不得写逐项工具调用流水账。

## 五、工具与编辑

编辑器、后台Git、WIP保护、分支隔离、停止交接与临时对象管理统一遵守[工具、编辑与并行](workflow.md#工具编辑与并行)及共享全局协议。工具缺失时如实报告影响，按实际授权选择降级方式，不将未执行报告为成功。

Minecraft操作先读取对应专项入口并核对所属实例，测试输入边界见[后台输入与测试边界](workflow.md#后台输入与测试边界)。本专项不额外放行游戏操作。

## 六、验证与收尾

验证范围、证据分级、纯文档检查、提交和结束条件统一遵守[版本与验证边界](workflow.md#版本与验证边界)与[修改规模与收尾](workflow.md#修改规模与收尾)。仅报告实际完成的检查，不复制独立的默认禁测或自动放行条款。

## 七、任务自检清单

- [ ] 已读工作区入口、项目根规范与适用规范
- [ ] 已读全局与项目技能的实际文件（或记录缺失）
- [ ] 已确认 IDEA 项目路径、MCP 与记录位置
- [ ] 疑问已提出并等待回答
- [ ] 每个实质阶段已更新文档
- [ ] 可见输出中文、无内部标记
- [ ] 最近 5 分钟内有可见进度消息
- [ ] 验证与提交按实际结果报告

## 八、维护

- 本规则由用户确认后修改；变更时同步[项目根规范](../../AGENTS.md)、[总索引](../README.md)、[修改记录](../systems/changes.md)与[问题汇总](../issues.md)。
- 规则细化不扩大任务范围；通用职责引用共享全局协议，项目约束按目录范围执行，不另行维护相反的优先级规则。
