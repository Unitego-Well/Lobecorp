# Horde 视觉过渡交接文档

更新日期：2026-10-27。工作区 `.workspace/lobecorp_horde`，分支 `codex/horde`。本页记录本轮（圆核心、胶囊绕行、分区粒子、服务端方区）的已完成、未解决与下一步，供新会话直接接手。契约见[视觉过渡与战斗区域](horde-visual-transition.md)，实现见[渲染实现](horde-rendering-implementation.md)，问题见[Horde 问题汇总](horde-issues.md)。

## 当前状态一句话

视觉外观用户看过夹具截图并反馈「看起来没有问题」，但只覆盖固定夹具的截图片面，粒子的分区/密度并未逐项确认；**「绕行区域被无过渡地重新填满」和「单位进入后被清除」两个现象仍未解决**，本轮定位已推翻多个猜测，最新方向明确在波带着色器的可见性判定，详见下文「未解决问题」。

## 固定工具链

用户明确指定本项目工具为 **spark + BenchMod + mc mcp + idea mcp + debug**，入口与分工见[项目工具链](README.md#项目工具链)。不得用其他方式替代对应环节。

## 已完成并提交

- `e6484c1` 视觉过渡主实现：客户端每个生物一个圆核心（本地玩家/参战成员/目标，真实 Y，30 tick 滞留 + GPU 40 tick 退场）、GPU 区域状态事件局部 floatBits、胶囊仅作引导轮廓、单位持久速度 + 限加速度积分、`HordeParticleSamples` 真实脚点采样（9 字/点、9216B 异步回读、8 邻居掩码与站程）、边界碎屑与灰尘同点 + 刀光独立批次、服务端运行期方形战斗区与 32 格/10 tick 分层快照。
- `4e1f98f` 烟雾改用清道夫飞扑起跳同款粒子（`short_smoke`、`big_smoke` 纹理、无重力短烟行为，速度沿用飞扑参数，不额外缩放），删除自建的篝火烟雾派生类。
- 验证：`BenchMod Compile` 退出 0；8 套无游戏测试共 61 项通过；`Data` 退出 0；静态诊断 0 错误。BenchMod `lobecorp.horde-render` 场景 PASSED（25,525 实例、regions 101–128、particles 8.48/刻、visible_pixels 45,010），spark 链接见 `changes.md` 第七轮条。

## 未提交的本地改动（约 17 个文件）

本轮排查过程中改写了一部分实现与基准设施，**尚未提交**，接手时先看 `git status` 与 `git diff` 再决定取舍。

- 区域缓存重写：`HordeRegionState` 删除整表 `deadline`/`expired`，改为 `Targets.reap(time)` 按槽位独立回收，并把 `eventVersion`（事件侧）与 `contentVersion`（内容侧，仅槽位集合或坐标/尺寸真正变化时递增）拆开，上传只在内容变化时发生。
- 区域序号稳定：`Targets.words` 按 generation 升序输出活跃序号，避免低编号槽位复用导致已有序号整体移位。
- 成员核心：`HordeClient` 新增 `RegionBox.forMember`，成员按自身水平外接圆取核心，不再加三格净空。
- 单位历史容量：`HordeUnitState` 移除显存预算收缩，`ring`/`perColumn` 只增不减，扩容时用 `copyTextureToTexture` 搬运旧历史并保留前后缓冲位置。
- 片几何：`HordeMeshReduction` 片档改为六个正交正方形面片（±x/±y/±z），按正方边长铺满 UV，避免被压扁；`HordeCardBake` 图集布局不变。
- 诊断计数：`HordeRenderMetrics` 新增 `REGION_SYNCS`、`REGION_UPLOADS`、`REGION_REBUILDS`，并在 `HordeGpuRenderer.statistics()` 输出 `regionSync=`、`regionUpload=`、`regionRebuild=`。正常游玩不可见，只进调试叠层与基准报告。
- 基准设施：按用户要求移除全部场景的焦点门禁（`HordeRenderScenario`、`CorpseRenderScenario`、`RandomCorpseRenderScenario`、`Sweeper30RenderScenario`），`BenchWindow` 不再抢占焦点、改为关闭原版失焦与不活动限流（`InactivityFpsLimit.MINIMIZED` + 每 tick `onInputReceived`），并在 teardown 恢复用户原设置。

## 未解决问题（接手重点）

### 现象

1. 绕行/分割区域会**无过渡地被重新填满**，玩家静止时也会发生，转动视角时更明显。
2. 单位**进入区域后又被清除**。

### 已排除的猜测（都有实测证据，不要再重复）

| 猜测 | 排除依据 |
|---|---|
| 区域数据每帧重建 | 诊断计数 `region_rebuilds` 恒为 **0**；`region_syncs`、`region_uploads` 中位数均为 0，均值约 0.1、0.16 每帧 |
| 区域活跃序号重排 | 已按 generation 排序修正，并有 `HordeRegionTest` 三项回归测试钉住 |
| 单位历史环页容量不足导致别名 | 已改为只增不减且扩容搬运历史，编译与测试通过 |
| 视锥剔除导致消失 | 用户明确否认「并没有被剔除」 |
| `HordeMeshCache.settingsKey()` 抖动触发 `clearModels` | 该 key 只含 `ANIMATION_FRAMES` 与 `REDUCE_HIDDEN_FACES`，与视角无关 |
| GPU 区域状态纹理重建 | 同第一行，`REGION_REBUILDS=0` |

### 最新方向（下一步从这里开始）

`core/horde_motion.fsh` 第 26–31 行决定单位是否可见：

```glsl
} else if (component == 4) {
    vec3 point = unitPosition(row, column, station, lateral, seed);
    vec2 light = lightAt(point);
    bool visible = station >= SegmentRegions.x && station < SegmentRegions.y
    && station >= 0.0 && station < SpacingLength.w;
    value = !visible || light.x < 0.0 ? -1.0 : light.x + light.y * 16.0;
}
```

问题在于：**可见性用的是按行号算出的解析 `station`，而显示位置来自可能已被避让推移的单位历史**。两者不是同一个位置。`horde_unit.fsh` 里单位速度上限是 `baseSpeed * 1.5`，退出方向还带前向分量，因此单位可以被推过波头（`SegmentRegions.y`）或波尾，随即被判 `!visible` → 输出 `-1` → 该单位被清除；下一帧又从历史取回显示，形成「进入之后又被清除」的闪烁。

历史实现有类似行为可对照：`docs/systems/horde-extreme-execution-plan.md` 的 EX-13 记录旧代码 `horde_motion.fsh:30` 曾用 `!regionClear(point) ? -1.0`，即进入区域即隐藏，该逻辑已在 HORDE-RETEST-05 的避让重写中移除。

建议做法：

1. 在 `include/horde_motion.glsl` 增加一个站位反查函数：把单位的实际 XZ 投影回路线，得到真实站程。路线数据布局为每节点 16 个 float，`node*16+3` 是弧长、`+4/+5` 是前向 xy，`LightRoute.y` 是节点数，`SpacingLength.w` 是总长度，`count*16` 处是节点索引查找表。
2. 让 `horde_motion.fsh` 组件 4 用**实际站程**判定波带可见性，而不是解析 `station`。
3. 复核 `advanceUnit` 是否应把位移后的站程钳制在 `[SegmentRegions.x, SegmentRegions.y]` 内，避免把单位推出波带。
4. 若仍有「瞬间拉回」残留，再考虑把 `advanceUnit` 的 `acrossGap * 0.32` 常驻锚点回拉换成持久侧向偏移状态（12 字 → 13 字）。

### 复现与验证方式

- 基准：IDEA 运行配置 `BenchClient`，单场景 `lobecorp.horde-render`。`build/moddev/benchClientRunVmArgs.txt` 的 `-DmodBench.scenarios` 需为单个值；注意该文件是构建产物，可能被 Gradle 重新生成覆盖。
- 报告与产物：`build/modBench/raw-results/default/client/`（`report.md`、`summary.json`、`artifacts/screenshots/`、`artifacts/spark/horde-profile.txt`、`artifacts/jfr/`）。
- 实机：客户端 MCP 端口为 **19876**（本工作区），原始仓库实例是 19878，不要混用；结束务必 `exit_control_mode`。
- 浪潮命令：`/lobecorp horde start lobecorp:midnight_cleanup <起点x y z> <终点x y z> [ticks] [width] [waypoints]`，需 gamemaster 权限；省略参数时默认 6000 tick、宽 128。管理命令 `list`/`stats`/`stop <uuid>`/`clear <uuid>`。

## 已知限制（非缺陷，不要当回归）

- 深包围时「当帧清空」与「连续位移」不可同时保证，接受连续退出但不隐藏。
- GPU 真实脚点采样是有界轮换样本（256 个），不是穷尽覆盖；缺邻只能作边界候选。
- 粒子快照最多 1 tick / 100 ms 旧，点数不足时少发，不伪造占据。
- 服务端方区只存在于运行期，加载后重建，不改存档格式；备用出生点可能落在方区外，因此不承诺全部在方区内出生。
- 粗地形高度是采样近似，薄墙、屋檐、多层楼板可能穿模。
- 本会话模型**不能读取图片**，所有截图只能确认文件生成与像素统计，外观判断需人工。

## 检查边界

已执行：`BenchMod Compile`、8 套无游戏测试、`Data`、IDEA 静态诊断、BenchMod `lobecorp.horde-render` 基准、spark 剖析。

未执行：硬件 GLSL 之外的着色器实机行为定量、多人及跨维度服务端调度、换包/重载/窗口变化、性能对比。上述未执行项不得以编译或静态检查代替。
