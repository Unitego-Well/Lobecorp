# Horde 当前文档入口

核对日期：2026-10-23。本工作区只负责 Horde；其他系统保留为[共同基线](README.md)。本文依据工作区当前源码与已有报告整理，包含未提交实现；源码存在、历史运行通过与本轮验收分别判断。

## 当前能力与源码

| 范围 | 当前职责 | 入口 |
| --- | --- | --- |
| 事件与真实参战 | 服务端推进事件、配额、出生和参战生命周期；虚拟单位不创建 Entity | [事件说明](horde-events.md)、[HordeRuntime](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) |
| 固定线路 | 平滑中心线、共用地面采样准备、粗高度脏更新、固定XZ包围组最近点查询；不重新寻路 | [HordeRoute](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java)、[HordeSamplingUtil](../../src/main/java/org/unitego/lobecorp/util/horde/HordeSamplingUtil.java) |
| 同步与客户端 | 完整线路就绪后显示；客户端 tick 检测目标区域，每帧提取绘制块 | [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java)、[HordeClient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) |
| 模型缓存 | 20 FPS 烘焙循环，每顶点每帧存位置与法线，共 6 个 float；按资源代际共享 | [HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) |
| GPU 与让行 | motion pass 求位置、让行与朝向，主体读取顶点帧；批次容量不等于总数量上限 | [渲染实现](horde-rendering-implementation.md)、[HordeGpuRenderer](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)、[motion shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh)、[顶点 shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh) |
| 光照 | CPU 准备已加载区段，5 tick 比较亮度；GPU 在让行后位置采样原版光照 | [HordeLightField](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java) |
| 基准 | 普通清道夫 AI、本地实体渲染、10000 实例隔离 GPU 快照场景 | [BenchMod](benchmod.md)、[HordeRenderScenario](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java) |
| 配置 | 服务端事件规则与客户端数量、范围独立；当前默认值以源码为准 | [HordeEventConfig](../../src/main/java/org/unitego/lobecorp/config/server/HordeEventConfig.java)、[HordeVisualConfig](../../src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java) |

当前是单一三维表示与顶点帧动画。旧图片图集、LOD 渐变与主体射线方案的过程页已合并移除；长期契约见玩法和渲染说明，历史采样与证据归档统一见[BenchMod](benchmod.md)。

## 问题与验证入口

本工作区的缺陷、性能候选、待验收项和历史遗留仅在[Horde 问题汇总](horde-issues.md)维护。本页不复制问题状态，也不使用原始 dev 或 Photon 的结果代表本工作验收。

## 恢复入口

先核对 `codex/horde` 与当前 Git 暂存、未暂存状态，再读对应系统文档和源码；现有 benchmark、路由、渲染、着色器及测试改动不能被覆盖或随文档一并提交。历史快照和备份仅作证据，不自动恢复旧实现。

MCP 消费本工作区 `lib/minecraft-mcp-0.3.0.jar` 与桥接，Minecraft 端口为 19876；Normify 复用原始仓库 CLI，workspace-root 与 cwd 指向本工作区。配置修正不代表已加载连接重载；文档任务不操作游戏或进程。详细路径和历史构建边界见[工作区目录](workspace-layout.md)与[制品规范](minecraft-mcp-artifacts.md)。
