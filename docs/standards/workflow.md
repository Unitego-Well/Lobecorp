# Lobecorp 工作流程与收尾规则

本文件为分支工作区导航与专属补充；公共规则唯一正文见[原始项目规范](../../../../AGENTS.md)、[工作流程](../../../../docs/standards/workflow.md)和[Java 与包规范](../../../../docs/standards/java-and-packages.md)。分支目标、进度、问题与证据仍归所属工作区；本次归并不改变既有授权或业务规则。

## 开始任务前

见[原始正文：开始任务前](../../../../docs/standards/workflow.md#开始任务前)。

## 系统文档与修改记录

见[原始正文：系统文档与修改记录](../../../../docs/standards/workflow.md#系统文档与修改记录)。

## 工具、编辑与并行

见[原始正文：工具、编辑与并行](../../../../docs/standards/workflow.md#工具编辑与并行)。

- 出现新的测试需求或可能需要新场景时，新增独立场景实现、ID 和配置，保留既有场景；只有用户明确要求修改指定既有场景时，才修改其实现或语义，必要的共享注册仅作追加。

## DS 辅助模型与路由

- DS 辅助使用软件现有模型目录中的 `deepseek-v4.1-flash（DeepSeek 中转站）`，选择模型支持的最低推理档；2026-10-31 核对的实际 ID 为 `ccs-deepseek-2/deepseek-v4.1-flash`，最低档为 `low`。每次分派前核对显示名称与实际 ID，不手填固定路由、不创建自定义模型；目录变化时仍须符合用户指定的中转站与模型。
- 不使用旧的 `deepseek-flash`、标注为“DeepSeek”的其他提供方条目、Pro 或其他模型替代指定的 `deepseek-v4.1-flash（DeepSeek 中转站）`；不可用时如实报告，不自动回退。
- 子代理列表看不到该 DS 时，使用用户授权的独立 DS 辅助对话；不冒称其他模型为 DS，不自行切换主对话模型或修改全局模型、代理配置。
- 故障与余额判断必须对应实际提供方和路由，不将另一条路由的报错推断为“DeepSeek 中转站”不可用。

## 修改规模与收尾

见[原始正文：修改规模与收尾](../../../../docs/standards/workflow.md#修改规模与收尾)。

## 复用已有能力

见[原始正文：复用已有能力](../../../../docs/standards/workflow.md#复用已有能力)。

## 版本与验证边界

见[原始正文：版本与验证边界](../../../../docs/standards/workflow.md#版本与验证边界)。

## 文档管理（DS 与 GPT 共用）

见[原始统一文档管理规则](../../../../docs/standards/agent-docs.md)。

## DS 执行规则

见[原始 DS 执行规则](../../../../docs/standards/ds-execution-rules.md)。
