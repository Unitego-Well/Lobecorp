# 粒子效果与渲染

核对日期：2026-10-05。版本依据[版本目录](../../gradle/libs.versions.toml)：Minecraft 26.1.2、NeoForge 26.1.2.100、Photon
26.1.2.2、LDLib2 26.1.2.39、ParticleStorm 1.4.4、GeckoLib 5.5.2。依赖 API
的详细研究入口为[粒子接入研究](../project-reference/09-particle-effects-research.md)。

包迁移方案 1 已执行，Java 链接按当前源码核对。本文只记录源码确认的机制，不把静态检查等同于游戏视觉验收。

## 三套粒子能力与职责

| 体系          | 当前职责和入口                                                                                      | 生命周期边界                                                                        |
|---------------|-----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| 原版粒子      | Lobecorp 注册 ParticleType、客户端 Provider，使用 SingleQuadParticle/QuadParticleRenderState 等渲染 | 粒子引擎推进 tick，粒子自身寿命或绑定条件结束；不承担伤害                           |
| Photon        | 编辑 .fx 效果、对象层级、发射器、材质、时间轴；FXRuntime 为独立播放实例                             | emit 后由 Photon 引擎驱动；通过实例参数、停止/清理控制                              |
| ParticleStorm | 资源驱动的粒子和当前 GeckoLib 粒子关键帧处理                                                        | 本项目复用其关键帧入口、locator 及重载流程                                          |
| Lobecorp 桥接 | PhotonGeoEffects 将带 photon: 前缀的关键帧交给 Photon，并跟随 GeckoLib locator                      | 按 manager/controller/effect/locator 管理独立绑定；非 Photon 标记回原 ParticleStorm |

服务端技能、实体状态和命中框决定伤害、施放及结束。客户端粒子只表现同步后的结果，不再遍历目标结算第二份伤害。编辑器扩展见[Photon 编辑器](photon-editor.md)
，不把编辑器临时预览状态当作多人游戏协议。

## 原版粒子注册、资源和同步

[注册类型](../../src/main/java/org/unitego/lobecorp/registry/particle/LcParticleTypes.java)统一位于
registry；[客户端 Provider 注册](../../src/main/java/org/unitego/lobecorp/registry/particle/client/RegisterParticleProviders.java)
复用 RegisterParticleProvidersEvent，由项目客户端注册入口调用，不另建重复监听。

简单粒子使用 SimpleParticleType；需要实体/技能实例、时间、半径等参数的粒子使用对应 ParticleOptions，提供 Codec 和
StreamCodec。原版 sendParticles 已发送粒子数据，不为同一效果再增加平行网络包。

资源分工：

- [assets/lobecorp/particles](../../src/main/resources/assets/lobecorp/particles)是原版粒子纹理列表，纹理来自
  assets/lobecorp/textures。
- Photon 的 FXHelper.getFX (id) 查找 assets/<namespace>/fx/<path>.fx，调用 ID 不含 fx/ 和 .fx。未在当前手写 assets
  根目录发现已交付的 fx 目录，不能推断女皇现有粒子已全部改成 Photon。
- ParticleStorm 的 particle_definitions 与原版 particles 不互换；当前目录存在并不证明每个效果已经有 ParticleStorm 定义。
- GeckoLib 动画/模型资源位于 assets/lobecorp/geckolib，locator 必须在模型中真实定义。
- src/generated/resources 是 Data 输出，不能直接写生成 JSON 或语言文件。

有持续绑定需求时，[QueenChannelLaserParticleOptions](../../src/main/java/org/unitego/lobecorp/particle/QueenChannelLaserParticleOptions.java)
包含
entityId、runtimeId、startGameTime、durationTicks；[QueenConvergentParticleOptions](../../src/main/java/org/unitego/lobecorp/particle/QueenConvergentParticleOptions.java)
还包含 pulseTicks、star。接收端以 startGameTime 补偿已过去的游戏 tick，再检查当前运行实例，避免把旧消息绑定到新一次技能。

## 女皇粒子系列

这些是当前客户端 Java 粒子实现，复用原版 Provider 和图集；服务端技能效果与客户端画面保持职责分离。

| 粒子                                                                                                                 | 画面行为                                                                 | 结束条件                                            |
|----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------|
| [QueenLaserParticle](../../src/main/java/org/unitego/lobecorp/client/particle/QueenLaserParticle.java)               | 一次消息显示整条短暂星束，沿向量拼接朝相机纹理片；宽度来自 StarBeamSkill | 2 tick 闪光寿命，不追踪目标                         |
| [QueenChannelLaserParticle](../../src/main/java/org/unitego/lobecorp/client/particle/QueenChannelLaserParticle.java) | 持续激光，读当前女皇起点、瞄准方向和方块截断终点；宽度来自 LaserSkill    | 时限、实体不存在/死亡，或对应 runtimeId 不再 ACTIVE |
| [QueenRepelWaveParticle](../../src/main/java/org/unitego/lobecorp/client/particle/QueenRepelWaveParticle.java)       | 水平双面退散波，按技能半径扩张，达到最大半径后淡出，播放纵向纹理帧       | RepelWaveEffect 的持续时间                          |
| [QueenMagicCircleParticle](../../src/main/java/org/unitego/lobecorp/client/particle/QueenMagicCircleParticle.java)   | 固定落点双面法阵；pillar 模式沿竖直方向拼接相机朝向纹理片；寿命内淡出    | Options 的持续时间                                  |
| [QueenConvergentParticle](../../src/main/java/org/unitego/lobecorp/client/particle/QueenConvergentParticle.java)     | 按女皇插值位置显示成型/下降星星，或向内收拢的水平双面环                  | 持续/脉冲时限、所有者失效或技能实例进入恢复/结束    |

上述效果使用 TRANSLUCENT 层和 FULL_BRIGHT 光照值。FULL_BRIGHT 使纹理不受环境暗光影响，不等于向世界投射光源或自动产生
Bloom。普通纹理片的拼接量随长度和片距增加，不能仅按“一个粒子对象”估算渲染成本。

[StarBeamSkill](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/skill/StarBeamSkill.java)
服务端按方块截断后的向量创建瞬时命中框并发送 QUEEN_LASER。星束规则为射程 15 格、完整直径 0.5 格、穿透多个有效敌人、每目标一次；粒子本身不修改这些规则。

[LaserSkill](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/skill/LaserSkill.java)
发送持续激光 Options，客户端跟随技能实例；伤害仍由服务端 HitboxManager 结算。修改光束表现的宽度/长度时应明确是否只改画面，不能让客户端预览覆盖反向改变命中框权威值。

## Photon 播放与实例参数

[PhotonGeoEffects](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffects.java)负责查找资源、创建绑定和
locator 更新；[PhotonGeoEffect](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffect.java)持有独立
FXRuntime、来源实体、时限和停止状态。

1. FXHelper.getFX 加载效果定义；FX.createRuntime 创建每次独立实例。
2. runtime.emit (executor) 把对象交给 Photon 引擎。executor 提供客户端世界，root 的位置、旋转和缩放负责锚点。
3. runtime.findObject/findObjects 和 emitter.runtime () 可定位实例 RuntimeValue；修改单次播放参数时覆盖实例，不写共享 FX
   配置。
4. 模拟倍率在 emit 后重新应用，因为 FXObject.emit/reset 会重置它。时间轴主时钟与模拟子步是两个机制。
5. destroy (false) 停止发射和时间轴，已有粒子/拖尾按原寿命自然结束；destroy (true) 立即清理。无限寿命残留不能假定一定自然结束。
6. 句柄是否结束结合 isFinished 和引擎 generation；isValid 在 destroy 后立即为 false，不能作为余粒子已消散的唯一证明。

setDurationTicks 使用从实际 emit 开始计的游戏 tick：0 不发射，-1
不设置外部截止，其他非负值到时柔和停止。外部时限不会延长一个已经自然结束的非循环资源；需要持续激光时应让资源本身具备循环发射能力。

setSimulationSpeed 调整对象模拟，0 冻结模拟但不自动暂停主时间轴；Photon 原每 tick 子步上限为
16，桥接不修改上限。对象层级倍率继承和时间轴播放变速不能混为一项。游戏代码入口返回 Optional<PhotonGeoEffect>
，调用方要处理资源缺失，而不是每帧重新尝试加载。

## 发射已有发射器与完整 FX

新增 Photon 原生对象类型 `lobecorp_emitter_spawner`：自身不发射 TileParticle，也不提交绘制作业；按周期/速率生成独立模板子树或完整
FX。
实例参数覆盖与源资源分离，已生成实例仍由 Photon 引擎更新/渲染。停止新增、柔和结束与强制清理分别处理。
形状、继承、播放倍率、数量/递归限制、原时间轴接入和客户端 API 见[发射器发射器](photon-emitter-spawner.md)。

## GeckoLib locator 桥接

ParticleStorm 1.4.4 已接入 GeckoLib
粒子关键帧。项目的[GeckoLibHelperMixin](../../src/main/java/org/unitego/lobecorp/mixin/particlestorm/client/GeckoLibHelperMixin.java)
包装 processParticleEffect，仅消费 photon: 前缀，其他标记调用原入口。例：关键帧 effect 为 photon:lobecorp:laser_charge 时，实际
Photon ID 为 lobecorp:laser_charge。没有第二套动画区间遍历器。

绑定使用实际 AnimatableManager 和 renderer instanceId；默认实体入口与 GeoEntityRenderer 一样使用 entity.getId，特殊
renderer 需显式传入一致的 instanceId。相同 controller、效果 ID、locator 的仍活动绑定复用；不同 controller
分开，循环动画不会无限累积同一活动效果。

[RenderPassInfoMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/client/RenderPassInfoMixin.java)在
renderPosed
已应用动画和骨骼调整后建立监听；[GeoLocatorMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/client/GeoLocatorMixin.java)
在 locator 位移和旋转已经应用后捕获变换。RenderUtil.extractPoseFromRoot 剥离渲染根矩阵，DataTickets.POSITION 提供世界基准，再构造
PhotonAnchorTransform。

模型到方块坐标的比例和骨骼/locator Z/Y/X 变换由 GeckoLib 处理，桥接不另写除以 16 或“PoseStack 平移直接加相机”的通用算法。骨骼名不会自动回退成
locator。

- locator 为空时可按已有世界基准立即播放；有 locator 时等待首次有效渲染姿态。
- 暂时未渲染时使用最后锚点和来源实体位移跟随，不能称为离屏仍精确采样骨骼。
- 第一次姿态尚未到来时等待；模型实际没有所需 locator 则记录警告并结束。
- 缺少 FX 资源返回空结果并记录警告，避免不断重试加载。

## 运行、同步与清理链

客户端关键帧桥接复用已经同步的实体动画，没有新增同一效果的独立每 tick 网络驱动。代码主动播放的实例不自动归某个动画控制器；调用方须保存句柄并明确技能/行为结束后的
stop 策略。

[AnimationControllerMixin](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/client/AnimationControllerMixin.java)
在动画切换或重启时柔和停止该控制器旧的关键帧效果。PhotonGeoEffects.tick 也检查控制器 STOP、完成或原动画更换；来源死亡、移除或世界变化强制结束。

[ClientRuntimeEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)在客户端 Post tick
更新桥接，在退出和客户端世界卸载时 clear。ParticleStorm afterReload 经客户端线程执行 clear，所有绑定强制清理；新状态不得继承旧世界、旧模型或旧
generation 的句柄。

当前 [PhotonParticleRuntimeTrial](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonParticleRuntimeTrial.java)
由客户端 LoggingIn 入口调用，程序构建一个非循环球形发射器并在玩家方块位置启动。它是现有登录演示入口，不应写成技能系统或“已关闭”的试验；是否保留/增加开关需单独确认。

## 排序、深度与混合

Order/orderInLayer 是绘制顺序配置，不是世界空间距离。先后绘制和深度测试共同决定可见性；把 Order 调到最前不能保证一个半透明面与所有其他透明面正确交叉。

| 参数                  | 作用和取舍                                                 |
|-----------------------|------------------------------------------------------------|
| 深度测试              | 与深度缓冲比较，通常让前面的实体/方块遮挡粒子              |
| 深度写入 / depth mask | 把当前像素深度写入缓冲；透明纹理写入可能使后面的粒子被挡住 |
| 顶点/对象排序         | 半透明通常需要从远到近混合；按整体排序对相交面仍有局限     |
| Alpha                 | 覆盖/混合权重，与 RGB 和光照分开                           |
| Blend                 | RGB 与 Alpha 分别使用源/目标因子；两组因子作用不同         |

本项目的[PhotonViewDepthSortUtil](../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonViewDepthSortUtil.java)
登记开启视深排序的发射器绘制作业，普通和实例化提交均接入。在同一 orderInLayer 的连续组内，标记槽位按视深从远到近重排；未标记槽位和不同
Order 分组保留。
发射器提交时计算深度，弱键映射避免跨帧强持有绘制作业及 GPU 资源；当前工具没有显式每帧清空方法，也没有按 TRANSLUCENT 层筛选登记。
[RendererSettingMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/render/client/RendererSettingMixin.java)
提供手动开关。

该处理是对象/绘制作业排序，不是逐像素无序透明（OIT），不保证 magic_circle
一类相交法阵在所有角度完全正确。深度写入、Order、透明排序和材质需要一起检查；纹理尺寸变更不能修复排序算法的固有限制。

混合计算可理解为 Cout = Cs × Fs + Cd × Fd；Alpha 使用独立的源/目标 Alpha 因子。同一枚举作为 RGB 因子或 Alpha 因子时必须按对应通道理解。

| 目标                    | 常见数学组合及限制                                                                               |
|-------------------------|--------------------------------------------------------------------------------------------------|
| 普通直通 Alpha 透明纹理 | RGB：SRC_ALPHA、ONE_MINUS_SRC_ALPHA；Alpha：ONE、ONE_MINUS_SRC_ALPHA                             |
| 已预乘 Alpha 的颜色     | RGB：ONE、ONE_MINUS_SRC_ALPHA；需确认纹理/着色器已经预乘，不能直接互换                           |
| 加法发光叠加            | RGB 使用加法权重使重叠处更亮；背景参与结果，不能保证半透明仍等于原颜色                           |
| 保留颜色/减少叠色       | 需要明确覆盖、裁剪阈值或透明策略；不存在既任意半透明透出背景又完全不受背景颜色影响的普通混合因子 |

Alpha 目标因子不会解决 RGB 的叠色或深度遮挡。关闭深度测试会使粒子穿过前方物体；开启透明深度写入会产生另一类遮挡。诊断 UI
能展示当前状态，但没有自动选择“一套适合全部特效”的参数。发光亮度、世界光源、Bloom 与混合是独立概念。

## 噪声预览与当前性能工作

[NoisePreviewMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/render/client/NoisePreviewMixin.java)替换
NoiseSetting.NoisePreview.drawInternal，缓存噪声采样后的颜色数组。缓存条件包含预览宽/高、频率、quality 和 remap 内容；seed
属于预览实例。Noise1D 只采样一行并拉伸，2D/3D 按预览网格采样，沿用原重映射和灰度逻辑。

[PhotonNoisePreviewState](../../src/main/java/org/unitego/lobecorp/client/photon/render/PhotonNoisePreviewState.java)
一次提交 GUI 状态，保留 pose、颜色 tint 和 scissor；在 buildVertices 中为每个网格单元生成顶点，避免逐像素单独提交 GUI
元素。编辑器控件登记优化见[编辑器页](photon-editor.md)。

仍存在的开销：

- 每次预览绘制仍通过 PersistedParser.serializeNBT (remap) 构造用于变更比较的 NBT，未改成无分配缓存键。
- 缓存命中仍生成网格顶点；高分辨率、多个同时显示的噪声预览会增加顶点工作。
- 修改尺寸/频率/quality/remap 仍按原分辨率重采样；未通过降频刷新或降采样改变画面。
- 游戏运行中的 NoiseSetting 粒子更新与编辑器 NoisePreview 是不同入口，本次优化不能称为“所有噪声运行时已经优化”。

未进行帧率、CPU 分配或 GPU 计时实测，也没有引入 OIT、额外缓存框架或新的性能依赖。

## 修改前核对和维护

1. 区分原版 Options/网络粒子、Photon 资源、ParticleStorm 定义及 Gecko 关键帧；先复用各自入口，不增加第二套 tick 或网络播放机制。
2. 客户端粒子与服务端伤害、命中框、技能实例状态保持分离；检查时限、起始游戏时间、实体失效和旧 runtimeId。
3. 新 Photon 资源核对 namespace、fx 路径、locator 是否真实存在、renderer instanceId 和独立运行时覆盖。
4. 生命周期变化同时核对动画切换/重启、技能取消、离屏、实体死亡、世界卸载、退出、资源重载和柔和结束残留。
5. 排序变更核对 Order 分组、普通/实例化绘制入口、深度测试/写入、材质 Alpha 和相交透明面；不能只测试正面角度。
6. 性能变更列出缓存失效条件和对象拥有者，不降低交互刷新、不遗漏可变配置；保留待实测项。
7. 修改 Mixin 时核对锁定依赖方法和[Mixin 配置](../../src/main/resources/META-INF/lobecorp.mixins.json)，只通过 IDEA
   更新资源排版。
8. 行为、资源、同步、参数、清理或路径改变后同步本文，在[变更记录](changes.md)写实际验证；静态/编译结果与视觉、多客户端、资源重载测试分别记录。

待运行核对：locator 的旋转/缩放与不同 renderer
上下文、遮挡/离屏再次可见、关键帧循环与动画切换、多人客户端状态、资源重载、半透明法阵不同观察角度、系统剪贴板交互以及噪声性能。本文没有将这些项目描述为已通过。
