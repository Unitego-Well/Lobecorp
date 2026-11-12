---
name: lobecorp-mcp-background-control
description: 诊断和处理 Lobecorp Minecraft MCP 接管退出后鼠标被抓取、后台或 AFK 限帧及优化 Mod 节流，核对同一游戏实例的接管和恢复状态。适用于后台控制排查，不自动改用户 Options 或其他工作区。
---

# MCP 后台控制

先阅读 [后台控制方案](../../../docs/tooling/minecraft-mcp/minecraft-mcp-background-control.md)，按当前问题选择鼠标释放、原版限帧、优化
Mod 或验收章节。Minecraft 版本、Mod 版本及端口以当次实例核对为准。

- 端口从当前工作区配置和实际游戏实例核对，不按历史 19878／19876／19877 推断归属；不得顺带操控其他工作区，不检索、恢复或唤醒归档 AI。
- 区分 stdio bridge 连接与游戏端 control_mode。断开 stdio 不等于退出游戏接管；结束先显式 exit_control_mode 并读回 false。
- 退出不主动 grab／lock 系统鼠标，由用户主动点击恢复原版输入。失败后清理本次模拟的持续按键与鼠标按钮。
- 接管开发客户端时只取消原版额外不活动节流，保留 configured maxFps、VSync 和用户设置；不得自动改写或保存 Options。
- 对优化 Mod 先核对实际安装、配置及触发分支，再局部处理。不把低 FPS 一律认定为限帧，也不把未发现某 Mod 当作完整排除。
- 经 IDEA 读源码、诊断和已有配置；构建、启动、等待 AFK 及游戏操作按当次授权执行。静态、编译、加载和运行验收分别报告，未满足时长或条件的步骤标为未执行。

## 输入、输出与边界

输入：已核对的游戏实例、接管状态与故障现象。输出：诊断、恢复状态及未验证边界；普通代码或文档任务不触发游戏操控。
