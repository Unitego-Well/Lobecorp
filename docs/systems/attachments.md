# 通用空间附着

核对日期：2026-10-12。附着是世界、实体、动画与渲染共用的空间系统；玩家和物品是接入方。

## 核心边界

入口为 [LcAttachments](../../src/main/java/org/unitego/lobecorp/attachment/LcAttachments.java)，目标契约为
[ILcTransformTarget](../../src/main/java/org/unitego/lobecorp/attachment/ILcTransformTarget.java)，来源契约为
[ILcAttachmentAnchor](../../src/main/java/org/unitego/lobecorp/attachment/ILcAttachmentAnchor.java)。
通用核心不持有动画实例，不创建对象、伤害、碰撞或物品库存。没有显式绑定就没有附着行为，也不扫描对象来猜测绑定。

绑定只负责写入目标变换。目标身份与寿命由原系统管理；解除绑定不销毁目标、不停止目标自身动画、不自动恢复旧跟随。
同一目标的根运动与附着共用写入权。创建、更新与切换限定同一 Level；来源或目标失效时释放绑定，不自动跨维度迁移。

共享 TRS 值 [LcTransform](../../src/main/java/org/unitego/lobecorp/attachment/LcTransform.java) 的位置单位是格。
实体、轻量技能效果、命中框和客户端 Photon 适配器继续复用该目标接口，不建立平行目标管理器。

## 来源与生命周期

- `LcAttachmentAnchors.world(level, transform)`：绝对世界锚点，不要求实体或动画。
- `LcAttachmentAnchors.entity(entity, offset)`：实体局部锚点，数字实体 ID 配合 UUID 校验。
- `LcAttachmentAnchors.custom(level, sampler, available)`：创建侧自定义来源；代码不发送到远端，远端收到最终世界结果。
- `LcAnimationAnchor.bone(instance, bone, offset)`／`locator(...)`：读取原动画实例当前姿态；不增加时钟。
- `LcRenderAnchorReference`：显式视觉来源，由客户端提供器消费，不反向决定服务端世界位置。

`LcAttachments.bind(target, anchor)` 创建独立绑定。可通过 `setTrajectory(Supplier<LcTransform>)` 组合调用方自己的局部动画，
通过 `switchAnchor(next, preserveWorldTransform)` 切换来源。保持世界变换时，在当前采样时刻计算校正；不可逆来源拒绝切换，
保留原绑定。切换保持同一绑定 UUID，不重建对象或重启它自己的动画。

`LcAnimationInstance.attach(target, anchor)` 只是显式绑定动画生命周期的入口：实例停止释放经该入口创建的绑定。
直接调用通用入口创建的绑定不归动画所有。动画骨骼来源在原播放停止后失效；独立世界或实体来源不会因无关动画停止而结束。

## 客户端直接使用

同一个通用入口接受客户端 Level，无需服务器播放、消息或占位动画。例如在客户端调用：

```text
var binding = LcAttachments.bind(target, LcAttachmentAnchors.entity(entity, offset));
binding.setTrajectory(() -> localTransform);
LcAttachedItems.register(binding, stack, ItemDisplayContext.FIXED, null, null);
```

`target` 与 `entity` 必须属于当前客户端世界。这里的修改只作用于客户端；调用方不应将本地位置当作服务端玩法结果。
已有自定义显示也可通过 `LcAnimationDisplays.register(binding.id(), display)` 接入。

本地绑定与服务端同步镜像分别保存。服务端全量帧、停止追踪和移除只清理服务端分区，不释放客户端本地绑定。
登录、退出及世界卸载清理本地绑定、显示描述与渲染缓存；尚未渲染过的本地登记也会清理。

## 最终渲染姿态

[ILcRenderAnchorProvider](../../src/main/java/org/unitego/lobecorp/client/attachment/ILcRenderAnchorProvider.java) 与
[LcRenderAnchorContext](../../src/main/java/org/unitego/lobecorp/client/attachment/LcRenderAnchorContext.java)
是提供给其他渲染器或模组的显式接入通道。提供器在实际模型完成姿态处理后提交当前帧节点矩阵。
矩阵带有宿主 UUID、数字 ID、视图和空间转换信息；保留完整矩阵，不以 TRS 分解替代最终渲染矩阵。

通过 `LcRenderAnchorProviders.register(id, provider)` 登记提供器，在渲染器完成本帧姿态后调用
`LcAnimationDisplays.submitRenderAnchors(context, collector, camera, packedLight)`。
自定义显示实现 `ILcAnimationDisplay.extract(level, partialTick)`，返回只保存本帧渲染数据的 `Frame`，由其 `submit` 提交。
提供器应同时提供当帧可解析的节点矩阵与实际可见节点集合，不能把矩阵存在等同于允许绘制。

玩家适配提供 `back`、`left_hand`、`right_hand`。第三人称在原版模型完成 setupAnim 与动画混合后使用层提交；
第三人称没有自定义玩家动画时仍可使用原版最终姿态附着。第一人称只有当前玩家动画策略显式声明 `firstPersonArms=true` 且权重大于 0 时，
才在独立双臂模型完成身体变换后提交当前可见手节点，背部不绘制；软停止的视觉尾部保留到淡出结束。
无动画或未声明时执行原版第一人称手臂持物流程，当前适配器不发布这一视图的最终手节点。客户端仍可直接登记绑定与手节点引用，
但登记不会隐式开启双臂渲染，也不代表对应节点在当前视图可用；该视图无当前提供器时不使用旧帧矩阵冒充。

逻辑偏移 `offset` 与视觉偏移 `renderOffset` 分开：实体根到手／背的位置只进入逻辑来源，不能在最终手节点上重复叠加。
提供器路线与世界路线每次只选一条，不重复绘制。提供器未出现时不拿上一帧矩阵冒充当前姿态。
保持连续的同提供器切换使用当前帧矩阵；跨路线暂缺当前来源时允许回到权威世界结果，不能无限隐藏。
第一人称与世界使用不同投影，因此世界变换连续不保证屏幕像素连续。

## 独立物品动画

[LcAttachedItems](../../src/main/java/org/unitego/lobecorp/world/item/animation/LcAttachedItems.java) 管理显式 ItemStack 显示描述，
保存副本、显示上下文、可选独立动画 UUID／局部骨骼和过渡时刻。物品整体位置、旋转与缩放动画使用既有动画运行时，
最终显示为“当前附着节点 × 局部校正／轨迹 × 物品自身局部动画”。不再次乘自身动画 owner 的世界变换。

内部物品模型骨骼动画沿用原版物品渲染与 GeckoLib，不新增另一套 GeoItem 骨骼系统。
物品整体动画的淡入淡出在 identity 与局部 TRS 之间进行，不表示透明度淡化。
软停止冻结自身动画，按显示时刻完成淡出后显式结束对应示例对象；不借用玩家姿态策略。
单独进出使用完整配置时长；`SEQUENTIAL` 替换等待旧显示淡出，再同时启动新物品与附着时间线。
尚未开始的显示不提交，也不额外等待一次不可见物品的淡出；`OVERLAP` 允许旧、新对象同时过渡。
空 ItemStack 在命令、显示登记和快照入口拒绝，命令错误使用中英文翻译键。

物品自身局部 TRS 通过 `LcClientAnimations.visualPose` 读取。默认循环已确认存在跨周期 Euler 回零被旧快照插值解释为反向半圈的问题；客户端现在仅在同一播放修订跨循环时，从同步的派生内容片段采样资源旋转，保留原位移、缩放及代码声明拥有骨骼的绝对结果。修订发生时直接使用当前姿态和世界变换，不混入旧修订。根因证据、缓存及维护边界见[循环边界的旋转跳变](animation-runtime.md#循环边界的旋转跳变)；不要通过改切锚校正或重启物品自身动画掩盖这类采样问题。

循环修复不改变服务端实际位置、附着生命周期或物品过渡设置。2026-10-12 后续修复另行处理长淡化中运动旋转目标跨 180° 的换向：`LcAnimationDisplays` 按绑定 UUID 保存物品入场／出场各自的连续相对旋转状态，通过 [LcRotationTransitionUtil](../../src/main/java/org/unitego/lobecorp/util/animation/LcRotationTransitionUtil.java) 延续分支。显示对象随快照重建时保留状态；动画修订、开始时刻、动画／骨骼标识、过渡设置或模型／动画资源代改变时重新建立状态，停止时刻改变只重置出场状态，注销或世界清理时释放。

物品入场的四元数输出延续邻近等价 Euler 分支供 NORMAL 出场读取；NORMAL 仍使用资源原始角度，位置与缩放沿用原插值。该修复不更改普通 `LcTransform.interpolate`、权威位置或附着生命周期。资源自身不连续、代码绝对跳变与相邻采样已丢失的旋转圈数仍有边界；实际运行检查以[修改记录](changes.md)为准，不能宣称所有旋转跳跃均已消除。

## 同步与推进

当前协议 16 沿用协议 15 的 [LcAttachmentStatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/attachment/LcAttachmentStatePayload.java)，
独立发送绑定及物品显示描述；动画状态不再重复携带绑定。绑定可在没有任何动画时同步。
锚点引用原动画实例 UUID，读取已有内容、时间与姿态，不重放历史回调或新增播放事实。

首次发送当前世界全部可见服务端绑定，后续发送当前可见更新与移除差集。物品描述是该次更新绑定的完整描述，
缺席表示注销该绑定的物品显示；全量仅替换服务端分区。实体锚点按追踪关系及玩家本人发送，世界锚点发给同 Level 玩家。

服务端沿原唯一事件入口推进：技能效果 → 动画 → 物品描述维护 → 通用绑定 → 命中结算 → 同步。
客户端沿原 ClientTick 入口更新本地绑定，提取时冻结显示状态，提交时消费当前帧渲染节点。
世界、连接与服务端停止路径分别清理绑定、物品描述和同步缓存。

## 开发日志与检查边界

[LcAttachmentDebugUtil](../../src/main/java/org/unitego/lobecorp/util/attachment/LcAttachmentDebugUtil.java)
的 `ENABLED` 默认关闭，只在非生产环境生效。通过 IDEA Debug 将其设为 true／false，记录创建、切锚、视觉来源变化和释放边沿；
不记录每 tick 的位置，不持久化开关。客户端与独立服务器 JVM 分别设置。

调试命令及当前检查结果见 [动画运行时](animation-runtime.md)、[玩家动画](player-animation.md) 与 [修改记录](changes.md)。
协议与包迁移直接切换新入口，不保留旧 API 或旧命令兼容别名。实际编译、Data 和运行证据以修改记录为准。
