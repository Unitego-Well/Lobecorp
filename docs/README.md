# Lobecorp 文档总览

范围：本检出的项目文档、规范与技能。维护：2026-11-10。当前实现以源码及锁定依赖为准；目录整理不代表构建、游戏或性能验收。

| 分类 | 入口 |
|---|---|
| 规则 | [项目规范](standards/README.md) |
| 结构 | [项目架构](architecture/README.md) |
| 功能 | [系统契约](systems/README.md) |
| 操作 | [工具与工作区](tooling/README.md) |
| 任务流程 | [项目技能](../.agents/README.md) |
| 目标与方案 | [设计](design/README.md) |
| 资料与经验 | [参考](reference/README.md) |
| 修改与证据 | [记录](records/README.md) |
| 开放项 | [项目问题汇总](issues.md) |

## 阅读与维护方式

规范写必须遵守什么，系统写如何工作，工具写如何操作；技能组合已有入口形成流程，不复制规范正文。设计不是已实现契约，历史记录不是当前进度。每个主题一份正文，每层 README 只维护本层路由或直接所属文档。

新材料先按职责选择目录，再更新直接所属索引；不在总览或系统根目录追加全库清单。布局与生命周期见[文档归属](standards/documentation/agent-docs.md)。

## 工作入口与保留边界

分支专属状态由所属工作维护，本页不复制；活动工作区与历史退役边界见[工作区手册](tooling/workspaces/README.md)。本地 .agent、备份、存档和运行输出不作为随仓库发布的正文。

工具和制品 README 留在 tools、lib 实际目录；测试源码固定读取的 systems/photon-resource-baseline-20261019.json 保留原路径。迁移前总览的独特历史与备份定位见[历史来源](records/navigation/README.md)，不作为当前执行授权。
