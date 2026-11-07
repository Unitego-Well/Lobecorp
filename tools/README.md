# 项目脚本与工具索引

维护：2026-11-07，当前仅原始dev。按用途选择入口，不将历史场景自动改接当前实例，不因日期或测试命名删除源码。普通操作CLI，语义连接IDEA；运行仍按实际授权。

| 分类 | 入口 | 使用边界 |
|---|---|---|
| 高频开发 | [dev手册](../docs/tooling/dev-scripts.md)、[简短入口](dev/README.md) | 读写/补丁、符号/引用/调用与重构；默认预览，有界输出 |
| 架构工具 | [Normify](lobecorp-normify/README.md) | 显式真实根；不自动安装、生成架构或注册服务 |
| 工作区维护 | [迁移](workspaces/Move-Workspace.ps1)、[占用核查](workspaces/Get-WorkspaceDirectoryOwners.ps1) | 明确授权的真实目标；当前无活动子工作区，不自动重建或按进程名清理 |
| 当前特效采集说明 | [Photon性能入口](photon/performance/README.md) | 推荐入口、显式run门禁和证据边界由该正文维护，不复制矩阵状态 |
| 可复用分析 | [世界性能](photon/analyze_world_performance.py)、[帧分析](photon/performance/analyze_repeat_frames.py)、[输入审计](photon/performance/audit_repeat_inputs.py) | 消费明确输入；旧数据分析不是当前游戏验收 |
| 工具回归源码 | photon/performance/test_*.py、*.test.mjs、horde_path_contract_test.py、Normify verify-protocol.mjs | 属于源码，不是垃圾输出；执行需要相应验证授权 |
| 历史采集与环境 | photon/performance/observe_*、旧日期runner及config | 按性能README的当前/历史标记读取，部分依赖退役目录或旧窗口行为；不作为当前推荐运行入口，不机械换路径后运行 |

职责见[测试与验证入口](../docs/tooling/testing.md)。业务资源、生成输出、第三方lib/vendor、缓存与归档不纳入脚本批改。使用正文保持唯一，索引不另建进度台账。
