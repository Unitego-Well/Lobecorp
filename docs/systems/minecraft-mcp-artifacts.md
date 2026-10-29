# Minecraft MCP 外部制品消费

更新：2026-10-25（合并迁移资料；未升级制品）。

源码只在独立 Git 仓库 `E:/xm/mc/mod/Unitego-Well/minecraft-mcp` 维护。原始 dev 和任务工作区不再保留 `modules/minecraft-mcp`、Gradle 子项目或源码锁文件。

通过外部仓库的 IDEA 配置 `Minecraft MCP Distribution` 构建 JAR 与桥接制品。原始仓库跟踪 `lib/minecraft-mcp-0.3.0.jar`、`lib/minecraft-mcp/bridge` 与 [artifacts.json](../../lib/minecraft-mcp/artifacts.json)。清单固定源码提交、Minecraft/NeoForge/Java 版本及每份制品 SHA256；制品升级后重新生成并审查清单，再提交到 dev，通过 Git 分发。不要在消费者安装 npm、编译 MCP 或添加外部源码子项目。

游戏 JAR 通过 compileOnly 提供扩展 API，仅普通 client 的 mcpClient source set 运行类路径加载（26.1.2 已禁用旧 additionalRuntimeClasspathConfiguration 方式）；发布 JAR、Data、Server、BenchMod 与内部测试分发不包含 MCP。JAR 使用 NeoForge Jar-in-Jar 携带固定 Java-WebSocket 1.5.4。桥接为 Node20+ 自包含构建输出，保留动态 chunk 和许可证；不需要 node_modules。

`.codex/config.toml` 指向各自 `lib/minecraft-mcp/bridge/dist/cli.js` 和 cwd。原始、特效、清道夫分别保留 MC_MCP_PORT 19878、19877、19876；游戏配置保留各自 mcp.port、run、build 和存档。不要将一个工作区的配置直接覆盖给另一个。普通源码 Git 同步不代表已加载的旧 MCP 进程已换版本，新聊天按新配置连接。

消费者 `Minecraft MCP Bridge` 只检查已发布 CLI 和 stdio 工具发现；Effect Runtime/Authoring/Reference 使用随制品发布的脚本，显式操作游戏仍需相应授权。图标导入和桥接构建只属于外部仓库。

2026-10-23：外部 IDEA 构建成功；消费方导入、编译及 Data 结果在本次修改记录另行记录，不能用历史结果替代。尚未启动游戏或验证四类操控和鼠标运行表现。

## 独立仓库迁移记录（历史）

2026-10-22 拆分阶段固定独立仓库提交 `aa8289a485cfaf2b90eca073f2ce1eb719328b6c`，当时分支为 `codex/standalone-minecraft-mcp`；完整来源与许可证以独立仓库 `SOURCE_PROVENANCE.md` 为准。旧方案通过各工作区源码快照消费，并保留 `modules/minecraft-mcp` 和 Gradle 子项目；该方式已被2026-10-23的制品模式替代，不能按历史说明恢复模块或共享 build、dist、node_modules 与 run。

旧工作区接入提交 `2403dea`、`2ad1e1e` 及112个文件一致性、10／11个文件静态检查等只作为当时证据，详细阶段见[修改记录](changes.md)，不代表各工作区当前业务或运行验收。原始拆分前引用为 `refs/backups/before-minecraft-mcp-extraction-20261022`；旧7903的快照与接入前备份分别为 `refs/backups/before-mcp-snapshot-7903-20261022`、`refs/backups/before-mcp-integration-7903-20261022`，旧90be为 `refs/backups/before-mcp-integration-90be-20261022`，均仅作追溯。

拆分阶段的鼠标修复及真人／模拟输入隔离统一见[后台控制与鼠标契约](minecraft-mcp-background-control.md#当前鼠标契约2026-10-22)，四类 direct／simulate 接口见[结构化操控](minecraft-mcp-controls.md)。源码一致、静态检查或制品提交都不能证明旧客户端进程已加载修复；运行验收仍须逐实例核对并具有对应授权。旧独立仓库过程页原文已备份至 `.git/docs-arrange-20261025-3c524a79/`。

备份：原始 Git common-dir 下 `mcp-externalization/20261023` 保留删除前源码与生成缓存；工作区迁移记录在 `workspace-relocations`。用户已有改动须先保存并比较，差异不能因删除源码而丢弃。冲突按内容合并，禁止直接选 ours/theirs。

Horde 旧90be接入时默认端口19876及既有运行配置、存档、MCP配置和 consts.ts 修改保持；wrapper JAR 直接复制并核对SHA256一致。接入前备份为 `refs/backups/before-mcp-integration-90be-20261022/{head,wip,index}`，通过独立Git索引保留业务暂存；当时仅做IDEA静态诊断与源码／配置差异核对，未构建、运行、安装或重连，不能证明旧客户端与bridge已加载修复。
