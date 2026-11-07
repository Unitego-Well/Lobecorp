# 测试、Bench与验证入口

维护：2026-11-07；当前dev，Java25、Minecraft26.1.2、NeoForge26.1.2.100。维护职责与执行边界；具体矩阵及历史结果仍在所属系统正文，不复制为本轮通过成绩。

| 类型 | 位置 | 本轮盘点与职责 |
|---|---|---|
| 普通JVM测试源码 | src/test/java | 111份Java文件，JUnit平台由build.gradle.kts配置；文件数不是方法数或通过数 |
| 游戏基准夹具 | src/bench/java | 70份Java文件，独立source set与显式运行任务，不自动进入生产main |
| 功能内诊断入口 | src/main/java下debug、photon/performance、photon/acceptance等 | 按命令/开关与功能契约保留；包名不意味着可随意迁移删除或启动即执行 |
| 工具回归与分析 | tools | 分类见[工具索引](../../tools/README.md)，test_*.py等为真实源码 |
| 结果与过程 | build/modBench、.agent、.local | 来源/日期/未验证分别记录；历史报告不是当前验收，不批改生成输出 |

机器扫描覆盖全部跟踪Java；人工深读聚焦窗口、日志门禁、命令与构建，不声称逐测试人工审查或全项目语义验收。没有可无损移除重复测试的证据，保留111份测试与70份Bench源码及名称，不为整齐扩大迁移。

## 编译与运行分开

```powershell
.\gradlew.bat --console=plain compileJava compileBenchJava compileTestJava jar -x test
```

compileTestJava只编译，不代表JUnit执行；compileBenchJava不启动游戏。不要用build/check/verifyBench*替代上述范围，它们可能包含测试或真实游戏。单元/集成测试、游戏、视觉与性能须有对应明确授权。生产包按本次产物检查，不引用旧包判断当前隔离。

## 窗口与日志

普通Client默认不铺满、不限制最小化。专项策略仅由-Dlobecorp.developmentWindowTestPolicy=true启用；Gradle bench运行显式设置，手写IDE/profile需明确传入。不抢焦点、不控制实体鼠标，不改maxFps/VSync/Options；窗口和后台帧率须独立验收。

默认关闭的类别、Photon诊断与保留失败日志见[调试入口](../systems/debugging.md)。功能预热与诊断是不同开关。构建/静态检查不证明游戏画面、帧率、鼠标和窗口行为。
