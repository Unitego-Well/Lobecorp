# Horde 当前文档入口

核对日期：2026-10-24。本工作区只负责 Horde；其他系统保留为[共同基线](README.md)。本文依据工作区当前源码与已有报告整理，包含未提交实现；源码存在、历史运行通过与本轮验收分别判断。

## 本轮任务进度

2026-10-24尸体与粒子追加任务进行中：水平分割包围按体型3倍、保留真实高度；修复普通尸体漏清理；事件成员及其击杀产生的尸体保留200tick，战斗保护优先，过期离开保护后清理；玩家附近可见范围内按间隔发射篝火烟雾、方块碎屑及原有清道夫刀光。本轮仍不启动游戏，取舍和原版粒子CPU生命周期边界记入问题表。

2026-10-24追加需求已写入：间距1.8格，基础速度不变、GPU随机加减速，左右最大1.5格且个体频率/幅度不同；小整数包围与GPU连续过渡、实际高度区间判断；单目标有效成员与预约上限10；事件成员不参与分割。BenchMod Compile、规则18项和区域3项通过，Data退出0且29份资源SHA256不变。详细优化/验收清单继续维护在horde-issues.md。本次按用户要求未启动、重启或接管游戏，前轮1.2格性能数据不作为本轮验收。

最近更新：2026-10-24。全部剩余问题只维护在[唯一问题汇总](horde-issues.md)，已完成证据记入changes.md；按用户要求不再主动询问，取舍和人工边界记录后继续。

- 已实现：开放多段平滑线路、默认纵横1.8格、随机加减速/横摆与GPU势流转向；每tick按玩家模拟距离发现Mob并保留profile生成资格；服务端仅清除战斗保护外的掉落物及清道夫尸体；未加载事件临时高度与延后采样；六整数小包围与GPU双快照过渡、原版Gizmo、统一客户端配置及翻译、真实和虚拟发光调整。
- 前轮已验证：IDEA编译、规则17项及区域3项回归、三轮Data和BenchMod Compile通过。普通和隔离客户端配置迁移/备份/旧值比对通过；远处未加载事件创建成功；一tick清除与战斗保护、保留普通牛已实际验证。上述证据不等同于完整联机验收。
- Iris实机：定位36→56字节扩展布局错位，GPU补实体标识、中心UV和切线后，100及10000虚拟清道夫实际可见，夜间遮罩发光可见；100个截图约115FPS。10000个30秒采样442帧、帧间隔均值67.98ms/P95 85.74ms、GPU均值67.89ms，约14.71FPS，每帧GPU转接292192256字节，仍是严重性能瓶颈，未宣称解决。环境为RTX3060 Laptop、2560×1494、Iris1.11.4/ComplementaryReimagined_r5.9.3、Vsync开启/120上限、前台最大化且限帧原因NONE。
- 实际复测：当前代码与1.2配置的BenchMod Client两轮de012627/8b660c93均退出0、两场景PASSED；64实体间隔均值4.84/4.31ms，10000实例2.41/2.30ms，GPU均值2.08/1.93ms。此前64实体明显回退未复现，未证明单一原因或实战FPS。独立BenchMod Server启动退出1，客户端第三方依赖缺类，未取得server数据。详见benchmod.md，剩余事项已统一按优先级整理。
- 运行收尾：第三轮Debug游戏59584（启动20:05:52.191Z）正常退出，临时代理67916/启动器59228/桥接69192正常关闭，均已核对不存在；退出接管为false、诊断显示上限恢复-1，Iris设置仅恢复自有时间戳，options.txt哈希不变。临时测试事件保留在隔离世界中，其他用户世界未修改。
- 备份与证据：C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d，含源文件、原配置、两轮完整基准、iris-layout56-10000.json及实际截图。源码混合既有WIP，提交隔离异议继续记录，不覆盖原有5项暂存。

## 当前能力与源码

| 范围 | 当前职责 | 入口 |
| --- | --- | --- |
| 事件与真实参战 | 服务端推进事件、配额、出生和参战生命周期；虚拟单位不创建 Entity | [事件说明](horde-events.md)、[HordeRuntime](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) |
| 开放多段线路 | 平滑中心线、共用地面采样准备、粗高度脏更新、固定XZ包围组最近点查询；不重新寻路 | [HordeRoute](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java)、[HordeSamplingUtil](../../src/main/java/org/unitego/lobecorp/util/horde/HordeSamplingUtil.java) |
| 同步与客户端 | 完整线路就绪后显示；客户端 tick 检测目标区域，每帧提取绘制块 | [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java)、[HordeClient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) |
| 模型缓存 | 20 FPS 烘焙循环，每顶点每帧存位置与法线，共 6 个 float；按资源代际共享 | [HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) |
| GPU 与让行 | motion pass 求位置、让行与朝向，主体读取顶点帧；批次容量不等于总数量上限 | [渲染实现](horde-rendering-implementation.md)、[HordeGpuRenderer](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)、[motion shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh)、[顶点 shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh) |
| 光照 | CPU 准备已加载区段，5 tick 比较亮度；GPU 在让行后位置采样原版光照 | [HordeLightField](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java) |
| 基准 | 普通清道夫 AI、本地实体渲染、10000 实例隔离 GPU 快照场景 | [BenchMod](benchmod.md)、[HordeRenderScenario](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java) |
| 配置 | 服务端事件规则与客户端数量、范围独立；当前默认值以源码为准 | [HordeEventConfig](../../src/main/java/org/unitego/lobecorp/config/server/HordeEventConfig.java)、[HordeVisualConfig](../../src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java) |

当前是单一三维表示与顶点帧动画。旧图片图集、LOD 渐变与主体射线方案的过程页已合并移除；长期契约见玩法和渲染说明，历史采样与证据归档统一见[BenchMod](benchmod.md)。

## 问题与验证入口

本工作区的缺陷、性能候选、待验收项和历史遗留仅在[Horde 问题汇总](horde-issues.md)维护。本页不复制问题状态，也不使用原始 dev 或 Photon 的结果代表本工作验收。

本轮原因分析与实施顺序见[测试前优化建议与问题原因](horde-pretest-optimization.md)；2026-10-24 的 BenchMod Client/Server 实测核对与修正建议见[测试后优化建议与问题原因](horde-posttest-optimization.md)。两页均为分析记录，不改变问题清单的权威性。

## 恢复入口

先核对 `codex/horde` 与当前 Git 暂存、未暂存状态，再读对应系统文档和源码；现有 benchmark、路由、渲染、着色器及测试改动不能被覆盖或随文档一并提交。历史快照和备份仅作证据，不自动恢复旧实现。

MCP 消费本工作区 `lib/minecraft-mcp-0.3.0.jar` 与桥接，Minecraft 端口为 19876；Normify 复用原始仓库 CLI，workspace-root 与 cwd 指向本工作区。配置修正不代表已加载连接重载；文档任务不操作游戏或进程。详细路径和历史构建边界见[工作区目录](workspace-layout.md)与[制品规范](minecraft-mcp-artifacts.md)。
