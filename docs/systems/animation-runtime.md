# 服务端动画、根运动与空间附着

> 核对日期：2026-10-12。本文记录已落盘的基础层与公开接入接口；编译、Data 和游戏运行的实际结果以[修改记录](changes.md)
> 为准。本文不表示专用服务器或客户端运行验收已经完成。

## 职责与接入范围

资源动画保存动作内容，代码定义包装资源，并显式声明内容修改、参数查询、时间线回调、根运动及空间附着。未声明根运动或附着时，骨骼动画不会自动移动实际对象，也不会自动建立碰撞、命中框或行为检查。代码定义与实例修改都会影响服务端采样结果。

本层复用既有实体、轻量技能效果、命中框和 Photon 运行态。对象仍由原系统创建、结算和移除；本层持有播放与空间绑定，不新建伤害系统或通用场景树。本轮提供基础系统、API
与接收适配，并提供独立命令接入示例；既有实体技能仍未迁移。

| 职责                 | 公开入口                                                                                                                                                                                                                                                                                                                                    |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 代码定义与延迟登记   | [LcAnimationDefinition](../../src/main/java/org/unitego/lobecorp/animation/definition/LcAnimationDefinition.java)、[LcAnimationDefinitions](../../src/main/java/org/unitego/lobecorp/registry/animation/LcAnimationDefinitions.java)                                                                                                        |
| 服务端完整内容       | [LcAnimationResources](../../src/main/java/org/unitego/lobecorp/animation/resource/LcAnimationResources.java)、[LcAnimationContent](../../src/main/java/org/unitego/lobecorp/animation/resource/LcAnimationContent.java)                                                                                                                    |
| 世界播放与单次运行态 | [LcServerAnimations](../../src/main/java/org/unitego/lobecorp/animation/runtime/LcServerAnimations.java)、[LcAnimationInstance](../../src/main/java/org/unitego/lobecorp/animation/runtime/LcAnimationInstance.java)、[LcAnimationContext](../../src/main/java/org/unitego/lobecorp/animation/runtime/LcAnimationContext.java)              |
| 姿态与坐标           | [LcTransform](../../src/main/java/org/unitego/lobecorp/attachment/LcTransform.java)、[LcSkeleton](../../src/main/java/org/unitego/lobecorp/animation/pose/LcSkeleton.java)、[LcAnimationClip](../../src/main/java/org/unitego/lobecorp/animation/pose/LcAnimationClip.java)                                                             |
| 显式实际位移         | [LcRootMotion](../../src/main/java/org/unitego/lobecorp/animation/behavior/LcRootMotion.java)                                                                                                                                                                                                                                               |
| 锚点与绑定           | [LcAnimationAnchor](../../src/main/java/org/unitego/lobecorp/animation/attachment/LcAnimationAnchor.java)、[LcAttachmentBinding](../../src/main/java/org/unitego/lobecorp/attachment/LcAttachmentBinding.java)、[ILcTransformTarget](../../src/main/java/org/unitego/lobecorp/attachment/ILcTransformTarget.java)                         |
| 内容与状态同步       | [LcAnimationSync](../../src/main/java/org/unitego/lobecorp/network/tc/animation/LcAnimationSync.java)、[LcAnimationSnapshot](../../src/main/java/org/unitego/lobecorp/animation/runtime/LcAnimationSnapshot.java)                                                                                                                           |
| 客户端视觉与独立显示 | [LcClientAnimations](../../src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimations.java)、[LcClientAnimationResources](../../src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimationResources.java)、[LcAnimationDisplays](../../src/main/java/org/unitego/lobecorp/client/animation/LcAnimationDisplays.java) |

## 完整资源与代码包装

服务端按正常数据包优先级读取完整 JSON 对象，资源位置为：

- 模型：`data/<namespace>/animation/models/<path>.geo.json`。
- 动画：`data/<namespace>/animation/animations/<path>.animation.json`。

对应资源 ID 为 `<namespace>:<path>`，不带上述目录前缀与文件后缀。模组内置默认内容放在相同的 `data` 目录，外部数据包可覆盖同
ID 内容；读取入口是 `AnimationResourceEvents` 登记的保留型服务端重载监听器。资源是完整 GeoJSON
与动画文件，不是仅供服务端使用的位移摘要。纹理不在此内容仓库或同步范围内。

`LcAnimationDefinition.wrap(modelId, animationId, animationName)` 创建包装 Builder；
`LcAnimationDefinitions.register(id, Supplier<LcAnimationDefinition>)` 保存延迟定义入口，重复 ID 拒绝登记。一次 `play`
固定本次解析出的定义与内容，登记表不保存世界、播放进度或目标对象。

| Builder 方法                  | 本层职责                                                       |
|-------------------------------|----------------------------------------------------------------|
| `editModel` / `editAnimation` | 每次播放从资源 JSON 副本派生内容，支持新增、删除或修改资源字段 |
| `modifyPose`                  | 在资源采样之后修改本次姿态；根运动与附着读取修改后的结果       |
| `query`                       | 显式提供服务端表达式查询；查询名按上下文规范使用，例如 `q.*`   |
| `at(seconds, action)`         | 显式代码时间点回调，时间单位为秒；循环按跨越的时间点触发       |
| `onTick` / `onStop`           | 单次播放的逻辑 tick 与停止回调                                 |
| `rootMotion`                  | 显式声明根骨骼平移轴如何写入实际目标                           |

播放入口为 `LcServerAnimations.play(Entity, definitionId)` 或 `play(ServerLevel, ILcTransformTarget, definitionId)`
。实体入口默认只允许写入位置；通用目标入口的 `sourceEntity()` 为 `null`，不隐式推断来源实体。服务端表达式通过本次
`LcAnimationContext` 读取受支持的实体事实、实例变量和已声明查询，不构造客户端渲染控制器，也不写 GeckoLib
全局变量表。未知服务端查询会报错，应显式提供查询实现。

## 实例参数、内容版本与时间

`LcAnimationInstance` 的参数、时间、暂停状态、内容副本和附着列表彼此隔离。公开操作包括 `setParameter`、`parameters`、
`setSpeed`、`setPaused`、`seek`、`editContent`、`context`、`boneTransform`、`locatorTransform`、`attach` 与 `stop`。

`setSpeed` 接受有限且非负的倍率；逻辑时间按每秒 20 tick 推进，资源与回调时间使用秒。暂停使动画时间增量为零，`onTick` 仍会执行。
`seek` 重采样指定时刻，不回放历史时间点回调，也不把跳转的时间差追补为实际位移。

`setParameter` 写入实例变量，不允许覆盖只读 `q.*` 查询；省略变量前缀时使用 `v.*`。参数变化先重采样当前时间的根运动基线，不把轨迹幅度变化追补成瞬移。

`LcAnimationContent.version` 是模型 ID、动画 ID 和两份完整 JSON 的 SHA-256。数据包重载更新内容仓库，已经开始的实例保持原版本。需要在播放中修改内容时，显式调用
`editContent(modelEdit, animationEdit)`；它从当前实例副本生成新版本、重建骨架与动画，并在当前时间重采样，不修改数据包底稿或其他实例。

`modifyPose` 必须保持纯计算：根运动跨循环边界时还会采样片段起点与终点，不能假定修饰器每 tick
仅调用一次。显式 `query` provider 同样应保持纯计算，状态快照会采集标准查询与已登记
provider，供客户端视觉采样使用。需要产生游戏行为时使用显式时间线或生命周期回调。

## 坐标与显式根运动

公共 `LcTransform` 由位置、四元数旋转和缩放组成；位置单位为格，模型本地前方为 `+Z`。源模型与动画的像素位移映射为
`(x, y, -z) / 16`；源 Euler 角度转换到公共空间后按 ZYX 合成。实体根朝向使用 `rotationYXZ(-bodyYaw, +xRot, 0)`，角度先转换为弧度；非
LivingEntity 使用自身 `yRot`。

声明方式为 `LcRootMotion.direct(bone, axes, localSpace)`、`colliding(bone, axes, localSpace)`，或显式构造并传入自定义
`MotionExecutor`。`Axis` 只有 `X`、`Y`、`Z`。声明的骨骼必须存在且是无父骨骼的顶层根；本轮只映射平移通道，不把根骨骼旋转或缩放变成实体转向或尺寸。

每次推进从修改后的根骨骼姿态求平移差分，跨循环时补入片段首尾的位移差；首帧作为差分基线，不把初始关键帧的绝对偏移转换成瞬移。仅选择声明的轴。
`localSpace=true` 时，差分按 owner 当前缩放与旋转转换到世界空间；否则直接作为世界空间差分。

- `direct` 将差分加到 `ownerTarget` 当前实际位置，保持其旋转与缩放。
- `colliding` 要求存在 `sourceEntity`，否则抛出参数契约异常。它以该实体当前包围盒调用原版 `Entity.collideBoundingBox`，包含
  `Level.getEntityCollisions` 返回的碰撞形状，再把允许差分交给 owner。
- 默认受阻策略是丢弃本 tick 被裁剪的差分，动画时钟继续推进；不积累以后补偿的位移。自定义 `MotionExecutor` 可显式实现其他策略。

默认实现不调用 `Entity.move`，不接管重力、导航、速度、碰撞标志、步高处理或指挥家／抓取移动规则。需要这些规则协作时由调用方显式决定；本层不会自动停止
AI 或获得对所有原版移动的独占权。

根运动执行后，已消费的平移轴从输出姿态清零。`boneTransform`、`locatorTransform` 与附着使用 owner
实际位置和消费后的骨架，所以碰撞受阻时不会再从视觉根偏移追加一次位移。客户端收到相同根骨骼与轴声明，也会从本地视觉覆盖的姿态中移除这些轴。

## 同一目标的空间附着

附着已独立为通用系统，入口、目标适配、客户端本地使用、最终渲染矩阵与清理规则见[通用空间附着](attachments.md)。

`LcAttachments.bind(target, anchor)` 不要求动画存在；`instance.attach(target, anchor)` 只负责把创建的绑定显式归属本次播放。
动画来源使用 `LcAnimationAnchor.bone(instance, bone, offset)`／`locator(instance, bone, locator, offset)`；
世界和实体来源使用 `LcAttachmentAnchors.world(level, transform)`／`entity(entity, offset)`。
目标必须与来源处于同一 Level，已停止动画拒绝新增归属绑定。

`setTrajectory(Supplier<LcTransform>)` 显式叠加调用方的采样结果，不再把通用绑定强制绑定到动画 context。
`switchAnchor(next, true)` 保持当前世界变换；解除绑定不删除目标或停止其自身动画。
根运动和附着共用按底层对象身份登记的写入权，没有显式声明时不自动建立空间驱动。

既有实体、技能效果、命中框与客户端 Photon 适配器继续接收同一目标对象的变换，各自保留伤害、碰撞、寿命及恢复跟随规则。

## 玩家姿态接入

代码包装可通过 `playerAnimation(policy)` 显式驱动玩家姿态，实例通过 `setPlayerPolicy` 动态管理原版来源、遮罩、权重和过渡。玩家视觉由 GeckoLib 原生时间线求值，服务端根运动与附着仍保持原有逻辑权威。第一人称默认执行完整原版手臂持物流程；当前冻结动画层显式声明 `withFirstPersonArms(true)` 且策略权重大于 0 时，才提交独立玩家模型的双臂／袖层和随手物品。软停止保留该层视觉淡出，到期后回到原版路径。接口、所有权与生命周期详见[玩家动画](player-animation.md)。

`modifyPose(ownedBones, modifier)` 显式声明要同步的骨骼绝对结果；无范围重载以返回姿态全部骨骼为结果。不从数值差异反推增量，不在客户端执行服务端回调。

## 内容同步与客户端视觉覆盖

同步入口沿用 `network.tc` 和 `IToClientPayload`：`LcAnimationContentPayload` 发送完整模型与动画 JSON，按 24 KiB 分块；
`LcAnimationStatePayload` 发送播放 UUID、内容版本、时间、姿态、参数、owner 世界变换与根通道声明；
`LcAnimationRemovePayload` 移除客户端播放。协议 15 的通用绑定与物品描述改由独立 `LcAttachmentStatePayload` 同步，支持无动画绑定。当前协议 16 在原玩家策略中增加第一人称手臂声明，不新增触发通道。回调、过滤器和真实目标对象引用不会序列化到客户端。

每个连接按内容版本缓存已发送内容，先发送内容，再发送引用该版本的状态。客户端先收到状态但缺少内容时暂存状态，完整重组且校验版本后安装。实体播放发送给跟踪该实体的玩家及其自身；无
sourceEntity 的通用 owner 播放发送给该 Level 的玩家。新加入追踪时发送当前快照，不回放历史时间点行为；失去可见性或播放移除时发送移除消息。缓存只在当前连接内存在，不写磁盘资源包。

发送缓存按连接身份保存，玩家死亡重生替换 ServerPlayer 时仍保留已发送实例历史，以便移除旧帧。停止追踪只清理客户端播放帧，独立显示登记保留，重新追踪可用相同
UUID 恢复显示。

客户端本地资源包可在标准 GeckoLib 路径覆盖视觉：

- 模型：`assets/<namespace>/geckolib/models/<path>.geo.json`。
- 动画：`assets/<namespace>/geckolib/animations/<path>.animation.json`。

模型与动画分别检查本地覆盖，不存在时使用服务器完整内容。无本地动画覆盖时以权威姿态插值为主，并对同一播放修订内的循环边界旋转单独采样；有覆盖时按权威时间与参数采样本地片段。骨骼／locator
附着会用客户端视觉骨架、消费后的视觉姿态和插值后的 owner 世界变换重新计算；世界锚点附着插值服务端世界结果。这些结果只供显示，不反向改变实际位置、碰撞或命中。

`LcAnimationBoneAdapter` 把公共姿态转换为 GeckoLib 的骨骼增量字段。现有实体姿态桥接不会自动把每个实体的模型替换为服务器模型；需要读取服务器完整模型的渲染器显式使用
`LcServerGeoModel`。客户端覆盖应保留所使用骨骼与 locator 的名称契约；本层不提供自动重定向。

### 循环边界的旋转跳变

默认物品整体动画的 2 秒循环已在 IDEA Debug 中复现跳变：相邻快照时间为 1.950000000000001 与 2.000000000000001 秒，Y 旋转从约 -351° 回到 0°，旧半刻 Euler 线性插值得到约 -175.5°，与连续转动方向相差约 180°。当时 `visualClip=null`，未使用本地 assets 动画覆盖；已确认根因是同步快照把循环回零当作普通 Euler 数值差进行插值。该证据定位了默认循环中的一次跳变，不能据此解释全部旋转问题。

`LcClientAnimations.visualPose` 先比较 `playbackRevision`。修订不同直接使用当前姿态；本地覆盖采样也直接使用当前时间，`worldTransform` 同样不混入旧修订结果。相同修订、无本地动画覆盖时，`interpolateLoopPose` 先保留原快照插值；只有循环片段跨越周期边界且时长大于 0 时，才按插值后的时间取模采样当前派生内容的片段，替换资源骨骼的旋转与 Euler 结果。位移、缩放及未跨循环的姿态仍沿用原快照插值。

前后任一快照的 `poseOverrides` 拥有的骨骼不参加这次资源旋转替换，继续保留代码定义的同步绝对结果；资源采样没有对应骨骼时也保留原结果。采样只读取当前同步参数，不执行服务端 Java 回调。`LcClientAnimationResources.contentClip(content, name)` 按内容版本与片段名缓存派生内容片段，`clear()` 清理该缓存，不把客户端本地覆盖与服务端派生内容混为同一缓存键。

维护时不要把循环首尾的 Euler 圈数回零直接作线性插值，也不要把所有通道统一改为最短路径而丢失资源定义的转圈轨迹。资源自身首尾不连续、代码明确写入的绝对跳变仍保留。长淡入期间移动旋转目标跨越 180° 的独立换向问题由下述连续过渡处理；根因复现不能作为修复已通过运行验收的证据。

### 循环边界修复后验证（2026-10-12）

新代码的单人 Client Debug 中，物品实例 `25dd4fa5-5e1e-4289-81dd-8ee637a13091` 实际跨圈快照时间为 3.999999999999994 与 4.049999999999994 秒，前后 Euler 为 -360.000010° 与 -9.00000025°。旧半刻插值为 -184.500005°；修复后为 -4.500000125°，与资源在该插值时间的采样期望一致，四元数方向误差 `errorDegrees=0`。本次 `sameRevision=true`、`poseOverrides=0`，位移与缩放保持原快照插值；没有以本例覆盖代码拥有骨骼的跳过分支或其他资源的全部循环轨迹。

该轮 7 个相关 Java 文件 IDEA 复查无新增诊断，项目编译成功，Data 退出码为 0，25 个生成文件的集合及逐文件文本保持不变。玩家显式第一人称开关、原版画面与停止到期回退的运行证据见[玩家动画](player-animation.md#显式第一人称门控修复验收2026-10-12)；该轮未取得淡出中间权重。一次 Client Debug 在 18:11:44 出现 PID 36000 的 `DirectMethodHandle` Render thread native 崩溃，之后原配置重启完成上述旋转及停止回退检查。这些历史结果不包含随后新增的连续过渡修复。

### 移动目标的连续旋转过渡（2026-10-12）

已确认的数学反例是源旋转 0°、权重 0.5、目标由 179.9° 移到 180.1°：逐帧独立最短路径会从约 89.95° 翻到约 -89.95°。共享 [LcRotationTransitionUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcRotationTransitionUtil.java) 首次取相对旋转的最短向量，后续选择最接近上一向量的等价分支，使该例连续经过约 90.05°。当前源姿态仍参与计算，因此源为 60°、目标为 90°、权重 0.5 时仍得到 75°，不冻结玩家原版基底。

当多轴整圈奇点使相邻等价旋转向量的差超过 π 时，工具按相邻相对四元数的局部最短增量推进上一加权结果，再以当前主分支重建修正；修正随权重向 0／1 连续衰减。状态只含固定数量的四元数、向量与权重，不累积历史轨迹；API 不修改输入或上一状态，返回旋转使用副本。`SHORTEST_PATH` 输出另保留临近的等价 Euler 分支，避免后续 `NORMAL` 层或冻结结果读取规范 Euler 时重新引入跳变。

Gecko 控制器的动画切换和层权重分别持有骨骼历史；玩家混合与独立物品显示沿用各自运行态的所有权和清理入口，详见[动画控制器](animation.md#渲染执行链与缓存清理)、[玩家动画](player-animation.md)与[通用空间附着](attachments.md)。上一帧历史按采样／渲染时刻冻结，同一时刻重复计算不推进状态；新播放、修订、过渡阶段或模式变化及结束清理时，不把旧分支带到新生命周期。资源循环边界重采样仍跳过前后 `poseOverrides` 拥有的骨骼，不在客户端重跑服务端修饰回调。

`NORMAL` 保留既有 Euler 数值路径，资源完整圈数和代码明确给出的绝对结果不统一归一化。连续四元数分支要求相邻采样足够接近；低采样下相对转动超过半圈，或调用方主动 seek／修改绝对结果时，不能从两个四元数推断中间应有的圈数。

首轮数学与玩家回归在接近 90° 的 Euler 分解处分别出现约 `1.18224E-4` 与 `1.00553E-4` 弧度误差，超过 `1E-4` 断言。`nearbyEuler` 改为先以 `Quaterniond` 归一化并分解为双精度角度，选定等价分支后再输出 `Vector3f`；玩家输出复用同一路径，断言阈值没有放宽。最终 IDEA 编译通过，两个回归 main 退出码均为 0；两者也通过新 Debug 会话中预置的无条件断点检查并正常停止，没有 `breakpointErrors`，玩家 `movingVanillaBase` 的 `right_arm.yRot=1.308997`，约为 75°。Data 退出码为 0，IDEA VFS 刷新后 25 个生成文件的集合和逐文件文本保持不变。

随后用原 Client 配置新建 Debug 会话并通过 Minecraft MCP 进入已有单人世界，服务端 Brigadier 实际登记 `diamond_sword` 的 80 tick 淡入／淡出、`OVERLAP`／`SHORTEST_PATH` 播放；客户端显示及独立旋转历史均为 1。软停止冻结姿态且不增加播放修订号，淡出到期后服务端播放会话、客户端显示和物品旋转历史均为 0。无动画时的实际截图仍为原版第一人称手臂；本轮没有取得长过渡逐帧画面，因此跨 180° 的连续性以数值回归为证，不把登记与到期清理当作完整视觉验收。全部代理断点已移除，日志开关保持 false，退出 MCP 控制模式后 Client Debug 保持 running；完整实际结果见[修改记录](changes.md)。

## 显式登记独立模型与物品显示

客户端通过 `LcAnimationDisplays.register(targetId, ILcAnimationDisplay)` 登记自定义显示，或者使用
`registerModel(targetId, animationId, texture)`、`registerItem(targetId, stack, displayContext)`，物品也可显式指定独立动画 UUID 与局部骨骼。`targetId` 是播放实例
UUID 或附着绑定 UUID；模型参数 `animationId` 是该模型自身播放的 UUID，不是资源 ID。物品显示保存传入 ItemStack
的副本，复用原版物品渲染状态。

登记只建立客户端显示，不创建 Minecraft 实体或服务端生命周期。独立服务端对象可实现 `ILcTransformTarget`，由其原管理者保留位置和寿命，再用通用
`play`／`attach` 入口驱动。未登记显示的播放仍可在服务端采样与移动，但不会自动生成客户端模型或物品。

`ClientRenderingEvents` 在 `ExtractLevelRenderStateEvent` 提取本帧变换和渲染数据，在 `SubmitCustomGeometryEvent`
提交。播放结束或收到移除消息只清理播放帧，不注销显示；接入方需要明确调用 `LcAnimationDisplays.remove(targetId)`
，客户端世界卸载统一清空显示登记。纹理由客户端资源和显式 `texture` ID
提供，本轮不传输纹理。

## 动画调试命令

唯一注册入口 [CommandEvents](../../src/main/java/org/unitego/lobecorp/events/CommandEvents.java) 委托
[LcAnimationCommand](../../src/main/java/org/unitego/lobecorp/registry/command/LcAnimationCommand.java)，要求管理员权限。
玩家参数采用原版玩家选择器；支持名字、`@s`、`@a` 等，命令方不必是玩家。

```text
/lobecorp animation player play <players> [override|idle|dynamic] [speed] [fade_in] [fade_out] [transition] [rotation]
/lobecorp animation attachment play <players> <item> [speed] [item_speed] [fade_in] [fade_out] [transition] [rotation]
/lobecorp animation attachment anchor <players> <BACK|HAND|WORLD>
/lobecorp animation item play <players> <item> [speed] [fade_in] [fade_out] [transition] [rotation]
/lobecorp animation <player|attachment|item> <pause|resume|stop> <players>
/lobecorp animation <player|attachment|item> speed <players> <speed>
/lobecorp animation attachment item <pause|resume|stop> <players>
/lobecorp animation attachment item speed <players> <speed>
```

可选参数按上面顺序提供；默认速度 1、淡入淡出各 8 tick、`OVERLAP` 与 `SHORTEST_PATH`。
速度须有限且非负，淡入淡出须非负整数。枚举参数区分大小写：`transition` 使用 `OVERLAP`／`SEQUENTIAL`，旋转使用 `NORMAL`／`SHORTEST_PATH`。
物品参数使用原版物品参数解析，可携带组件；显示保存副本，不发放物品。

例如：

```text
/lobecorp animation player play @s dynamic 0.5 12 12 SEQUENTIAL SHORTEST_PATH
/lobecorp animation attachment play @s minecraft:diamond_sword 1 0.5 8 8 OVERLAP SHORTEST_PATH
/lobecorp animation attachment pause @s
/lobecorp animation attachment item speed @s 2
/lobecorp animation attachment anchor @s WORLD
/lobecorp animation attachment stop @s
/lobecorp animation item play @s minecraft:diamond_axe
/lobecorp animation item stop @s
```

附着示例使用真实玩家，不生成白模或移动玩家。0～1.5 秒从背部沿资源轨迹移到手边，1.5 秒切手部来源，3 秒切世界来源并叠加下落轨迹；
6 秒片段结束后请求物品整体动画淡出。手动切锚保留对象、绑定 UUID 和物品自身播放时间。
暂停附着时间线不暂停物品自身动画，使用 `attachment item` 子命令独立控制后者。
停止物品自身示例对象会释放它的绑定；通用系统的单独 `binding.release()` 仍不停止目标动画。

物品世界示例在目标玩家水平前方 2 格、抬高 1 格建立独立显示；采用
`example/item_pose` 的 2 秒循环局部 TRS，不启用根运动。物品内部骨骼继续交由 GeckoLib。
资源动画仍由代码定义包装，三个玩家模式均显式开启第一人称双臂；动态玩家模式只更改当前策略的来源，保留命令指定的过渡设置和第一人称声明。

旧 `animation_example`、`player_animation_example` 已移除，不提供兼容别名。
既有实体技能未自动迁移。当前检查结果见[修改记录](changes.md)。

## 推进顺序与清理

`LivingEntityEvents` 的服务端 `EntityTickEvent.Post` 顺序为技能运行态推进 → `LcServerAnimations.tick(entity)` → 抓取锚点更新；非
LivingEntity 同样进入实体动画推进。每个动画实例按 Level 游戏 tick 防止重复 advance。

服务端 [WorldTickEvents](../../src/main/java/org/unitego/lobecorp/events/WorldTickEvents.java) 的 `LevelTickEvent.Post`
HIGH 顺序为技能效果更新 → `LcServerAnimations.finishTick(level)` → 物品描述维护 → `LcAttachments.tick(level)` → 命中框结算 → 动画与绑定同步。`finishTick`
只推进尚未处理的动画实例并移除停止实例；通用附着单独更新一次，命中框读取本 tick 的空间结果，同步读取结算后的最终快照。

单个 `advance` 的顺序为可用性与结束检查 → `onTick` → 时间推进与姿态修饰 → 根运动执行 → 消费根平移 →
跨过的时间点回调。非循环片段到达结尾后保留本次最终姿态，下次推进停止。`stop` 幂等，先释放绑定，再调用 `onStop`；不会重复触发停止回调。

时间点动作按时间稳定排序，同一时刻保留登记顺序。

维护回调时保留激活失败回滚与清理期间禁止新建播放的边界，避免 `onStop` 重入留下世界引用；`seek`／`editContent`
采样失败回滚原状态。回调中的 `seek`、`editContent` 或 `stop` 会取消旧时间线后续分发，不应继续消费已经失效的采样区间。

实体离开 Level 时停止该来源实体的播放；ServerTick.Post 清理已经卸载的 Level，服务器停止清理世界运行态与同步缓存。目标适配器在更新时检查原
Level 和对象可用性，不迁移绑定到另一个世界。客户端世界卸载清理播放、待安装状态和显示；登录／退出连接还会清理内容与资源缓存。

## 开发诊断

独立播放与玩家姿态共用 [LcAnimationDebugUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcAnimationDebugUtil.java) 的默认关闭开发内存开关，只有非生产环境输出。通过 Debug 动态设置 `ENABLED`，生命周期日志直接读取当前实例字段，不为诊断再次调用动画查询提供器；客户端安装、停止尾部与模型层日志的开关用法及边界见[玩家动画开发日志](player-animation.md#开发日志开关)。

2026-10-12 对 6 次 native 崩溃的日志审计均出现 `redefine → singleStep` 关联，其中 4 次早于本轮玩家修复；问题帧均在 JBR 25.0.4.1 的 `DirectMethodHandle$Holder.invokeStatic`。目前只是高度相关的候选路径，尚未确认 JVM 根因。调试时先完成编译再启动新会话，不在运行中 HotSwap，避开 `ClientRuntimeEvents` 的条件日志断点与复杂求值，优先使用开发日志与预置无条件暂停位置。本轮两个独立回归 Debug 正常停止，游戏 Debug 暂停／恢复未复现原生崩溃；此措施用于规避干扰，不表示 JVM 崩溃已经修复，没有修改 JDK、IDE 设置、VM 参数或第三方模组。

## 已知限制与检查边界

- 已提供上述独立命令接入示例；现有实体技能仍未迁移，也未为既有技能自动生成定义、附着声明或显示登记。
- 模型与动画内容完整传输不等于实现所有 Bedrock 运行行为；实际位移、检查、附着和代码回调仍需显式定义。本层不自动接管资源中的粒子、声音或伤害逻辑。
- 根运动只支持顶层根平移，默认碰撞只是包围盒差分裁剪；没有旋转根运动、尺寸驱动、受阻时间回滚、步高或导航接管。
- `LcTransform` 采用 TRS。非均匀缩放与旋转组合可能产生 shear，矩阵分解无法精确保留该部分；保持世界变换的切锚与层级组合也受此限制，不能据此承诺任意仿射矩阵完全无损。
- 客户端资源覆盖仅影响视觉，不会同步回服务端；视觉骨骼与真实碰撞可能因覆盖而不同。纹理、原系统的命中规则和目标创建／移除协议不在新增内容同步内。
- 循环边界重采样和连续过渡分别处理同步回零与移动目标换向；资源自身不连续、代码绝对跳变以及低采样无法推断完整圈数仍有各自边界，不代表所有旋转跳变均已解决。
- 本轮 IDEA 编译、Data、单人世界 MCP／Debug 的实际检查见 changes.md。专用服务器、多人晚加入／停止追踪、全部玩家姿态及第三方着色器组合未验收；不将单人检查推广到这些场景。
