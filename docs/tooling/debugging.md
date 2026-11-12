# 非 Photon 游戏调试开关

更新：2026-11-07。适用当前原始 dev；非 Photon 类别日志与 Photon 专项诊断分别维护，不借用历史分支成绩。

## 统一类别开关

`org.unitego.lobecorp.util.debug.LcDebugUtil` 是技能、动画、附着三类开发日志的唯一开关。默认全部关闭，生产环境始终关闭，不依赖客户端类、不写配置文件、不跨 JVM 同步。

| 类别 | 日志 | 属性 |
|---|---|---|
| 技能 | `EntitySkillDebugUtil` | `lobecorp.debug.skill` |
| 动画 | `LcAnimationDebugUtil` | `lobecorp.debug.animation` |
| 附着 | `LcAttachmentDebugUtil` | `lobecorp.debug.attachment` |

启动属性：

- `lobecorp.debug=true`：三类默认全部开启，例如 `-Dlobecorp.debug=true`。
- 单类别属性：只覆盖对应类别的默认值，例如 `-Dlobecorp.debug.skill=true`。
- 单类别显式 `false` 覆盖全局 `true`，例如 `-Dlobecorp.debug=true -Dlobecorp.debug.animation=false` 只关闭动画。
- 属性解析使用标准 `Boolean.parseBoolean`：缺失时全局为 false、分项继承全局；存在空串或非 `true` 文本为 false。

日志级别沿用现有实现：技能、动画、附着正常边沿为 INFO；动画服务初始化耗时也纳入 ANIMATION 类别，默认不打印。动画采样失败保留 ERROR。动画状态日志仍只在 revision 或 stopped 变化时输出，不逐帧刷屏。

## 命令

仅开发环境注册，生产环境不注册。

```text
/lobecorp debug
/lobecorp debug status
/lobecorp debug skill on|off
/lobecorp debug animation on|off
/lobecorp debug attachment on|off
/lobecorp debug all on|off
```

- 服务端命令要求 `GAMEMASTERS`，影响当前服务端 JVM。
- 客户端命令无管理员门槛，只影响当前客户端 JVM。
- 集成服务器客户端与服务端在同一 JVM，两侧共用同一开关；远程客户端不能通过该命令改变远端服务端日志，远端管理员可在服务端控制台执行。
- 命令只改运行期内存值，重启后重新按启动属性初始化，不持久化、不新增网络包。
- `/lobecorp` 根下追加 `debug`，保留既有 `animation`、`horde` 和 Photon 客户端命令；客户端本地优先，未知命令仍回退服务端。

## Photon 诊断与常规启动

- `lobecorp.photon.diag.cast=true` 开启 CASTPROF/FXLOAD，以及光束预热、队列drain和完成摘要；默认关闭。保留诊断字段，失败warn/error不受该开关抑制。
- `lobecorp.photon.diag.material=true` 独立开启CASTMATERIAL账本；它不是功能预热开关。`prewarmBeam`和`prewarmRender`原默认行为保持，不因关闭日志而取消预热。
- 性能命令、采集面板及工具分析保留显式调用边界，不在启动时自动运行测试。普通开发入口已有MC_DEBUG/F3启动属性未在本轮擅自重置，见构建runs配置。
- 常规Client不会自动安装Win32铺满/防最小化策略；`lobecorp.developmentWindowTestPolicy`默认false，仅明确测试入口设true。Gradle bench运行配置显式设定，手写IDE/脚本profile须自行明确，不依赖启动环境猜测。
- 不改变鼠标接管、后台节流、maxFps、VSync或Options。本轮仅源码/构建与静态核对，窗口状态及实际帧率未实机验收。
- 测试/Bench/工具测试的职责、编译及未执行边界见[测试与验证入口](testing.md)；通用脚本见[工具使用手册](dev-scripts.md)。

## 与原版 F3 的关系

F3 调试条目、11 个 `LcDebugEntries` 条目、四个 `configured &&` mixin 与 `HordeDebugRenderer` 本轮保持不变。

原版 `DEBUG_PATHFINDING`、`DEBUG_GOAL_SELECTOR`、`DEBUG_SHAPES`、`DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES` 仍要求 JVM 属性 `MC_DEBUG_ENABLED` 才能让 `configured` 为 true；仅勾选 F3 条目默认不生效。`DEBUG_MONITOR_TICK_TIMES` 由 `LcDebugRuntimeOptions` 从客户端单向覆盖，仍只服务 tick profiler，不属于本页三类日志开关。

## 清理边界

- 删除启动横幅 `Unitego.`。
- `HordeDebugState` 删除无调用的 `notice/lastNotice`。
- `HordeDebugState.reset()` 只在 `HordeClient.clear()` 调用，重置全部会话观测值；不改变 `beginFrame()`/`beginDraw()` 的当帧时序，不挂 GPU 资源重载，不重置 `HordeRenderMetrics`。
- 保留真实失败 warn/error、命令回复、两个不同域的 `STATISTICS`、`HordeRenderMetrics`、`lobecorp.horde.splitDeduplication`、`lobecorp.developmentResources` 和 Photon 专属内容。
