# Photon、GeckoLib 与 ParticleStorm 粒子接入研究

核对日期：2026-10-03。依据 IDEA/MixinMCP 读取的锁定依赖源码，不能直接套用其他 Minecraft 版本文档。

## 当前版本与研究依据

| 组件                 | 项目锁定版本        | 核对入口                                                                                                                  |
|----------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------|
| Minecraft / NeoForge | 26.1.2 / 26.1.2.100 | gradle/libs.versions.toml                                                                                                 |
| GeckoLib             | 5.5.2               | GeoLocator、RenderPassInfo、RenderUtil、AnimationController、GeoRendererInternals                                         |
| Photon               | 26.1.2.2            | FXHelper、FXRuntime、IEffectExecutor、FXObject、IFXObject、TimelinePlayer                                                 |
| ParticleStorm        | 1.4.4               | GeckoLibHelper、WithCurrentEntity、AnimationTimelineMixin、AnimationControllerMixin、GeoLocatorMixin、RenderPassInfoMixin |

官方概念资料：[Photon Java API](https://low-drag-mc.github.io/LowDragMC-Doc/en/photon2/java-api/)、[Photon 运行时参数](https://low-drag-mc.github.io/LowDragMC-Doc/en/photon2/java-api/runtime-data-injection.html)、[ParticleStorm](https://www.curseforge.com/minecraft/mc-mods/particle-storm)
。这些页面可能描述其他版本；具体签名以当前 IDEA 解析结果为准。

## Photon 播放与参数边界

- FXHelper.getFX (id) 加载 assets/<namespace>/fx/<path>.fx，ID 不包含 fx/ 或 .fx。
- FX#createRuntime () 创建独立实例，FXRuntime#emit (executor) 将对象交给 Photon 粒子引擎。桥接不手动重复 tick 或每帧重新播放。
- IEffectExecutor#getLevel () 提供客户端世界；root 的 updatePos/updateRotation/updateScale 驱动整个效果的锚点。
- FXRuntime#findObject/findObjects 与发射器 runtime () 提供实例级参数覆盖；修改共享 FX 定义会影响其他播放，不应用于单次施放。
- IFXObject#setSelfTimeScale () 为分层模拟倍率，子对象继承父对象倍率。FXObject#emit/reset 会重置该倍率，因此桥接在 emit
  后重新应用用户设置。
- FXObject#tick () 先运行根对象的 Timeline 回调，再按 timeScale 模拟。TimelinePlayer#tick () 的主时钟每游戏 tick
  前进一次。因此模拟变速、模拟冻结与整条 Timeline 的变速/暂停不同。
- 当前 Photon 的每 tick 子步数量上限为 16；极高模拟倍率会被其内部上限限制。桥接不改写该上限。
- FXRuntime#destroy (false) 停止新发射和 Timeline，已有粒子/拖尾自然消散；destroy (true) 立即移除。
- isValid () 在 destroy 后直接为 false，不能用它判断柔和结束的余粒子是否已经消散；桥接结合 isFinished () 和粒子引擎
  generation 清理失效句柄。

## GeckoLib locator 与实例边界

- 粒子关键帧 ParticleKeyframeData 提供 effect 与 locator 字符串；KeyFrameEvent 提供 animatable、renderState 和 controller。
- GeoRendererInternals#captureDefaultRenderState 保存 ANIMATABLE_MANAGER 与 ANIMATABLE_INSTANCE_ID。绑定归实际
  AnimatableManager，不只使用可能冲突的实体编号。
- GeoEntityRenderer#getInstanceId 默认使用 entity.getId ()；代码入口提供相同默认值，其他 renderer 需显式传入自己的
  instanceId。
- locator 只有渲染姿态才有精确位置。RenderPassInfo#renderPosed 先采样并应用动画和 renderer 骨骼调整，再设置 locator
  listener。
- GeoLocator#updatePositionListeners 会应用父骨骼、locator 偏移及 Z/Y/X 旋转；此后捕获矩阵可取得完整变换。
- RenderUtil#extractPoseFromRoot 剥离 render pass 的根矩阵，DataTickets.POSITION 提供世界基准。直接把 PoseStack
  平移加相机坐标不能通用于不同渲染上下文。
- 模型坐标转方块坐标的 16 是 GeckoLib 的格式比例，由库负责。桥接不另写一套 locator 变换。
- 当前桥接针对 locator。骨骼名不能作为 locator 名自动回退；需要在模型资源中定义相应 locator。

## ParticleStorm 的兼容方式

当前版本已经通过 Mixin 接入 GeckoLib：

1. AnimationTimelineMixin 在关键帧时间区间中分发粒子标记，并调用 GeckoLibHelper.processParticleEffect。
2. GeckoLibHelper 按 AnimatableManager 和 locator/粒子 ID 管理绑定；非 locator 效果直接使用渲染基准位置。
3. RenderPassInfoMixin 在渲染阶段建立监听与当前 pass 上下文。
4. GeoLocatorMixin 在定位变换完成后捕获局部空间，listener 负责世界位置。
5. 动画切换、资源重载以及效果自身结束清理绑定；WithCurrentEntity 提供替换实体 renderer 的当前实体。

Lobecorp 已将 ParticleStorm 声明为 required。本桥接复用它的公开关键帧处理入口，不再添加第二套 AnimationTimeline 区间遍历。升级或删除
ParticleStorm 时必须重新核对这条链路。

## 项目桥接结构

| 文件                                     | 职责                                                                      |
|------------------------------------------|---------------------------------------------------------------------------|
| PhotonGeoEffects                         | 两种播放入口、按 manager 保存绑定、关键帧分流、locator 捕获与生命周期清理 |
| PhotonGeoEffect                          | 独立 FXRuntime、模拟倍率、外部时长限制、柔和/强制结束及来源有效性         |
| PhotonAnchorTransform                    | locator 的位置、旋转和缩放快照                                            |
| particlestorm/client/GeckoLibHelperMixin | 消费 Photon 标记，其余标记原样交给 ParticleStorm；重载后在客户端线程清理  |
| geckolib/client/RenderPassInfoMixin      | 动画采样后、安装 listener 前绑定本帧新产生的 locator 效果                 |
| geckolib/client/GeoLocatorMixin          | 在 GeckoLib 已应用完整 locator 变换后捕获锚点                             |
| geckolib/client/AnimationControllerMixin | 控制器切换或重启动画时结束它自己的关键帧效果                              |
| ClientRuntimeEvents                      | 复用已有 tick、退出和世界卸载入口，不建立重复事件订阅                     |

所有注入均登记在 Mixin 配置的 client 部分。公共调用应放在客户端表现代码中；服务端不得加载这些 Photon 类，不新增视觉网络包。

## 动画关键帧使用方式

Photon 使用明确前缀 photon:，其后是完整效果 ID。例如效果资源 assets/lobecorp/fx/laser_charge.fx：

```json
{
  "particle_effects": {
    "0.0": {
      "effect": "photon:lobecorp:laser_charge",
      "locator": "laser_origin"
    }
  }
}
```

该前缀是 GeckoLib 标记的分流语法，不是最终资源 ID；实际加载 lobecorp:laser_charge。未带此前缀的标记继续按 ParticleStorm
处理。同一 locator 上允许两种库分别播放效果。

关键帧路径无需给每个控制器新增 handler，也无需给每个 renderer 新增 layer。自定义关键帧 handler 仍按 GeckoLib/ParticleStorm
原链路运行，不能再手动重复播放 Photon 标记。

同一控制器、效果 ID 和 locator 的仍在播放实例会复用，避免循环动画堆叠无限发射器；实例已结束或自然消散后再次跨过标记可以重新创建。不同控制器互不清理。

关键帧效果绑定动画控制器：动画完成、停止、切换或重启时柔和结束。空 locator 从来源位置立即发射；指定 locator
等待首次有效渲染姿态再发射，避免先从实体脚下喷出粒子。

## 代码主动播放与实例控制

以下是客户端调用示意，效果 ID、locator 名、倍率和时长应由技能定义、资源或配置提供：

```text
PhotonGeoEffects.play(entity, effectId, locatorName).ifPresent(effect -> {
    effect.setSimulationSpeed(simulationSpeed);
    effect.setDurationTicks(durationTicks);
});
```

play 返回 Optional<PhotonGeoEffect>；资源不存在时返回空并记录警告。需要后续控制时保存返回的句柄，结束时调用：

```text
effect.stop(false); // 停止新发射，保留余粒子
effect.stop(true);  // 立即清除，也可升级已开始的柔和结束
```

代码创建的效果不绑定动画控制器，不因动画切换自动结束。技能结束或取消应由已有客户端技能生命周期回调调用 stop；不要另加重复同步。

setDurationTicks 设置从实际发射开始计算的最大持续 tick，修改后不重置已用时间；0 表示不发射，UNLIMITED_DURATION
表示不增加外部截止时间。它不能延长已自然完成的非循环资源。需要任意持续时间的激光，应将持续段做成循环资源，再由代码结束。

setSimulationSpeed 改变粒子模拟倍率，不改变游戏 tick 截止时间，也不改变 Timeline 主时钟。0 冻结模拟，但不承诺暂停
Timeline。资源中的 Speed Track 可能再次覆盖目标槽位，应避免 Timeline 与代码共同控制同一个 root 参数。

effect.runtime () 可以查找发射器并覆写其 runtime () 参数，实现单次播放的发射率、寿命、颜色、宽度等控制；具体字段按发射器类型查询，不做未经确认的统一参数抽象。

其他 GeckoLib 对象可使用显式 play (animatable, instanceId, level, position, effectId, locator)。instanceId 必须匹配
renderer；实体和方块实体可以检测移除。无世界坐标的 GUI/物品预览不具备世界 locator 锚点，当前接口不将其自动映射到玩家位置。

## 生命周期与尚需游戏检查的内容

- 来源实体死亡、移除、换世界或方块实体移除时强制清除。
- 退出、客户端世界卸载和 ParticleStorm 资源重载时强制清除全部绑定；重载清理由 Minecraft.execute 调度到客户端线程。
- 精确旋转和缩放依赖模型渲染；未渲染时沿用最后姿态。已发射的实体效果仍按 tick 跟随实体位置增量；从未渲染过的 locator
  效果保持等待。
- locator 不存在时记录警告并结束，资源缺失不进行每帧加载重试。
- 循环资源必须由代码、动画生命周期或来源清理终止。isStopped () 表示停止发射，isAlive () 在柔和结束后仍可能为 true，直到余粒子消散。
- 项目实现不包含激光资源制作，不改变伤害、技能时序或用户正在修改的指挥家控制。
- 游戏检查仍需覆盖 locator 偏移/旋转/缩放、模型不可见、动画循环/切换/取消、两库标记共存、多人观察和资源重载。编译、Data 与
  IDEA 诊断不能代替这些视觉验收。
