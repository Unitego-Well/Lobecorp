# Minecraft MCP 独立仓库与鼠标修复

记录日期：2026-10-22。

## 源码与消费关系

维护仓库：E:/xm/mc/mod/Unitego-Well/minecraft-mcp，分支 codex/standalone-minecraft-mcp，固定提交 aa8289a485cfaf2b90eca073f2ce1eb719328b6c。独立 Gradle 根工程、Java 25、NeoForge 26.1.2.100、ModDev 2.0.142 与 Gradle 9.6.1 已写入源码；来源与许可证见独立仓库 SOURCE_PROVENANCE.md。未发布 GitHub。

原始项目通过 [锁定文件](../../modules/minecraft-mcp.lock.json) 消费 [源码快照](../../modules/minecraft-mcp/README.md)，保留 :minecraft-mcp 与 bridge 的原路径。快照是消费副本，通用修复以独立仓库为维护入口。此阶段未删除原始副本、未替换成共享目录或符号链接，因此旧运行配置不需要迁移。

更新时比较锁定提交与独立仓库新提交，用 IDEA 合并变更及工作区本地修改；冲突停止，记录新的提交。不能直接覆盖整个目录或以 ours／theirs 丢弃一方。每个工作区使用自己的源码副本、build、node_modules、dist 和 run；19878／19877／19876 与 server 端口保持各自配置。不自动安装依赖、构建、重连或重启。

## 问题与源码修复

已确认源码存在两条会干扰鼠标的路径：开发客户端进入控制模式后，每 tick 强制取消 mouseGrabbed 并设置普通光标；旧模拟移动直接调用 GLFW 或 AWT Robot 移动系统指针。这些路径可解释用户描述，但没有本次运行复现证据，不能认定所有鼠标问题或崩溃均由此造成。

- 保留此前明确要求的开发客户端默认激活，仅授予 MCP 操作权限，不改变鼠标捕获。
- enter／exit 只改变 MCP 授权状态，不强写系统鼠标所有权；release_mouse 为明确的一次性释放请求。
- 模拟鼠标移动、点击、滚轮和键盘只走游戏内部回调，不回退为系统设备输入。当前键盘 API 为 keyPress(long, int, KeyEvent)。
- 内部客户端请求在 runSyntheticInput 的嵌套作用域执行，finally 恢复；仅此作用域内拦截原版 grabMouse，防止模拟点击／关界面隐式居中。真人点击仍使用原版抓取路径。
- 四类 GUI／玩家／实体／方块 direct 与 simulate 接口保留；可选 Photon 桥接扩展保留，业务实现没有拆出。

## 实际结果与边界

独立仓库完成修改行 IDEA 格式处理，相关 Java／Gradle 文件的错误诊断为空。Git 差异检查无格式错误。wrapper JAR 从原项目复制，SHA256 相同。没有执行本次编译、Data、bridge 构建、游戏操作、MCP 调用或运行验收。尚不能宣称正在运行的客户端已加载修复。

后续运行验收应逐实例核对路径与端口，检查真人点击重新抓取、MCP GUI 点击不居中、退出控制模式、跨窗口切换及异常回调后的恢复。只能在相应运行授权下执行，不控制其他工作区的客户端。

最近清道夫 crash-2026-10-21_23.49.14-client.txt 已转交清道夫聊天；崩溃排查由其负责，本任务没有把崩溃归因给 MCP。只使用未归档聊天。7903 已明确暂停模块写入；90be 仍在处理自己的崩溃任务，本轮不直接写其业务文件。

原始 HEAD 保存在 refs/backups/before-minecraft-mcp-extraction-20261022；拆分来源保留在提取分支与独立仓库历史。此前同步脚本、修改记录与未跟踪文件保留原状态；本轮提交仅收录本次变更。
