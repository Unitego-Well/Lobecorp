# Minecraft MCP 外部制品消费

更新：2026-10-23。

源码只在独立 Git 仓库 `E:/xm/mc/mod/Unitego-Well/minecraft-mcp` 维护。原始 dev 和任务工作区不再保留 `modules/minecraft-mcp`、Gradle 子项目或源码锁文件。

通过外部仓库的 IDEA 配置 `Minecraft MCP Distribution` 构建 JAR 与桥接制品。原始仓库跟踪 `lib/minecraft-mcp-0.3.0.jar`、`lib/minecraft-mcp/bridge` 与 [artifacts.json](../../lib/minecraft-mcp/artifacts.json)。清单固定源码提交、Minecraft/NeoForge/Java 版本及每份制品 SHA256；制品升级后重新生成并审查清单，再提交到 dev，通过 Git 分发。不要在消费者安装 npm、编译 MCP 或添加外部源码子项目。

游戏 JAR 通过 compileOnly 提供扩展 API，仅普通 client 的 mcpClient source set 运行类路径加载（26.1.2 已禁用旧 additionalRuntimeClasspathConfiguration 方式）；发布 JAR、Data、Server、BenchMod 与内部测试分发不包含 MCP。JAR 使用 NeoForge Jar-in-Jar 携带固定 Java-WebSocket 1.5.4。桥接为 Node20+ 自包含构建输出，保留动态 chunk 和许可证；不需要 node_modules。

`.codex/config.toml` 指向各自 `lib/minecraft-mcp/bridge/dist/cli.js` 和 cwd。原始、特效、清道夫分别保留 MC_MCP_PORT 19878、19877、19876；游戏配置保留各自 mcp.port、run、build 和存档。不要将一个工作区的配置直接覆盖给另一个。普通源码 Git 同步不代表已加载的旧 MCP 进程已换版本，新聊天按新配置连接。

消费者 `Minecraft MCP Bridge` 只检查已发布 CLI 和 stdio 工具发现；Effect Runtime/Authoring/Reference 使用随制品发布的脚本，显式操作游戏仍需相应授权。图标导入和桥接构建只属于外部仓库。

2026-10-23：外部 IDEA 构建成功；消费方导入、编译及 Data 结果在本次修改记录另行记录，不能用历史结果替代。尚未启动游戏或验证四类操控和鼠标运行表现。

备份：原始 Git common-dir 下 `mcp-externalization/20261023` 保留删除前源码与生成缓存；工作区迁移记录在 `workspace-relocations`。用户已有改动须先保存并比较，差异不能因删除源码而丢弃。冲突按内容合并，禁止直接选 ours/theirs。

## 历史接入记录

以下完整保留 2026-10-22 原 `minecraft-mcp-workspace.md` 接入记录；其中工作区源码快照、源码锁和源码升级方式已于 2026-10-23 被本页外部制品方案取代，不能作为当前操作入口。提交、备份与当时未验收边界仍为历史证据。

2026-10-22：90be 通过版本锁（历史引用，当前工作区缺失：`../../modules/minecraft-mcp.lock.json`）消费独立仓库 E:/xm/mc/mod/Unitego-Well/minecraft-mcp 的 aa8289a485cfaf2b90eca073f2ce1eb719328b6c。modules/minecraft-mcp 保持工作区独立源码快照，不共享 build、node_modules、dist 或 run。

接入通用鼠标修复、独立构建入口及已提交的特效桥接扩展。保留开发默认授权，取消每 tick 强制释放，模拟输入仅调用内部回调；同步模拟作用域阻止 grabMouse 居中，真人输入不全局禁用。默认端口19876、运行配置、存档、既有 MCP 配置修改及 consts.ts 文案不改变。consts.ts 原有未提交状态保留。

前轮批量补丁遇到 CRLF 上下文失败后暂停，现已核对原文，通过 IDEA 文档 API 逐块完成，未使用终端改写源码。wrapper JAR 直接复制并核对 SHA256 一致，不重建。提交前备份为 refs/backups/before-mcp-integration-90be-20261022/{head,wip,index}；原有业务暂存内容通过独立 Git 索引保护，仅提交本次 MCP 和文档。

只做 IDEA 静态诊断与源码/配置差异核对，不执行构建、测试、Data、安装、重连或游戏操作。源码接入不代表运行中的旧客户端和 bridge 已加载修复。后续升级通过 IDEA 合并源提交差异，保留本地修改，冲突停止。
