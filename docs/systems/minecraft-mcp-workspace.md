# Minecraft MCP 工作区接入

2026-10-22：90be 通过[版本锁](../../modules/minecraft-mcp.lock.json)消费独立仓库 E:/xm/mc/mod/Unitego-Well/minecraft-mcp 的 aa8289a485cfaf2b90eca073f2ce1eb719328b6c。modules/minecraft-mcp 保持工作区独立源码快照，不共享 build、node_modules、dist 或 run。

接入通用鼠标修复、独立构建入口及已提交的特效桥接扩展。保留开发默认授权，取消每 tick 强制释放，模拟输入仅调用内部回调；同步模拟作用域阻止 grabMouse 居中，真人输入不全局禁用。默认端口19876、运行配置、存档、既有 MCP 配置修改及 consts.ts 文案不改变。consts.ts 原有未提交状态保留。

前轮批量补丁遇到 CRLF 上下文失败后暂停，现已核对原文，通过 IDEA 文档 API 逐块完成，未使用终端改写源码。wrapper JAR 直接复制并核对 SHA256 一致，不重建。提交前备份为 refs/backups/before-mcp-integration-90be-20261022/{head,wip,index}；原有业务暂存内容通过独立 Git 索引保护，仅提交本次 MCP 和文档。

只做 IDEA 静态诊断与源码/配置差异核对，不执行构建、测试、Data、安装、重连或游戏操作。源码接入不代表运行中的旧客户端和 bridge 已加载修复。后续升级通过 IDEA 合并源提交差异，保留本地修改，冲突停止。
