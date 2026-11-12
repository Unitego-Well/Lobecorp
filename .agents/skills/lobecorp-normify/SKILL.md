---
name: lobecorp-normify
description: 使用独立标准 MCP 为 Lobecorp 或明确指定的单个仓库生成、查询和维护可下钻的架构模块树、依赖与 API 契约，导出交互式 HTML。适用于架构建图和代码变更后的结构更新，不执行 Minecraft 操控或 Java 构建。
---

# Lobecorp Normify

使用 tools/lobecorp-normify 的标准 stdio MCP。首次连接前阅读包内 README.md 的路径配置与隔离边界；服务必须明确传入当前授权仓库的 --workspace-root。没有对应工具时，使用该包 CLI 或说明需要注册连接，不伪称工具已加载，不修改全局配置。

## 分析与建图

- 先确认用户授权的仓库和建图范围。只读源码，结构数据写入当前工作区 normify-<slug>，不查找归档 AI 或其他工作区。
- 用 normify_help 的 topic=tools 查看实际工具；具体参数通过 topic=tool:<工具名> 按需获取，不猜 schema。
- normify_project_init 建数据项目；按实际职责从根逐层展开，模块记录源码位置、revision 和 normify_fingerprint 得到的指纹。
- 每个模块只存 parent 和出向 deps；API 仅在叶子声明，跨模块箭头尽量使用 from_api/to_api 锚定。默认保留 zh/en 描述。
- 用 module_batch 的 dry_run 审查批量计划再写入。不要直接手改编译产物。
- validate 检查结构与规则，修正本次数据错误后 build、render，交付 HTML、tree.json 和回执路径。此处 build 仅指架构数据编译，不代表授权构建游戏。

## 增量维护

用户要求伴随开发时，可使用 change_open → brief → check → 实施 → module_refresh → change_close。架构计划和工具诊断不能扩大业务代码修改授权。sync 是只读漂移报告；只更新实际受影响模块，禁止用新工作区源码刷新另一工作区的指纹。

模块移动保留 uid，使用 module_move 的 dry_run 预览；删除前保留可恢复版本。记录实际执行过的校验和渲染，不把仅注册服务、静态诊断或历史结果当作架构数据验收。

## 输入、输出与边界

输入：已授权的单仓库和建图范围。输出：架构数据、HTML 与实际校验回执；普通文档分类不调用建图。包说明见[Normify 手册](../../../tools/lobecorp-normify/README.md)。
