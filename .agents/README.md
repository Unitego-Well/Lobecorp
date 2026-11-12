# 项目技能导航

维护：2026-11-10。保留五个技能的调用名、发现目录和脚本位置，按任务选择一个入口；规范从[项目规范](../docs/standards/README.md)进入。

| 分类 | 入口 |
|---|---|
| 开发规则定位 | [lobecorp-development-rules](skills/lobecorp-development-rules/SKILL.md) |
| 架构建图 | [lobecorp-normify](skills/lobecorp-normify/SKILL.md) |
| 游戏后台控制排查 | [lobecorp-mcp-background-control](skills/lobecorp-mcp-background-control/SKILL.md) |
| Java 版冒烟测试 | [lobecorp-java-smoke-test](skills/lobecorp-java-smoke-test/SKILL.md) |
| 工作区同步 | [lobecorp-workspace-sync](skills/lobecorp-workspace-sync/SKILL.md) |

## 自然语言定位

用户不必记技能名。在当前项目改动的运行验证语境中，出现“跑一下看看”“进游戏检查一下”“测一下能不能正常玩”“验证这次改动有没有问题”“做个冒烟测试”等表达时，优先读取上表的 Java 版冒烟测试技能，再按具体目标选择检查项。这些说法用于定位流程，不替代项目授权要求；“跑一下”若明确指编译、脚本或单测，按其原意处理。

“编译一下”“看看代码有没有问题”“研究怎么测试”不自动触发游戏运行；完整玩法验收、视觉验收和性能测试仍按各自范围处理，不能用冒烟通过代替。
