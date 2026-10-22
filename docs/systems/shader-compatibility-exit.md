# EXIT 方案与 Lobecorp 光影兼容评估

核对日期：2026-10-18。状态：研究完成，未接入。授权范围为版本、依赖与渲染源码核对及文档整理；没有修改 Java、Shader、资源、Gradle 或运行配置，没有编译、Data、测试、同步、重载或运行游戏。

## 结论与后续选择

EXIT 的独立通道、深度对齐和帧末合成思路可用于 Lobecorp，但当前公开实现不能直接作为 26.1.2 依赖接入。Photon 已提供自己的 Iris 兼容层和 AFTER_PACK 路径，应先复用和诊断已有能力，不能再叠一套通用出口就认为兼容完成。

| 目标                                             | 当前判断                             | 后续行动，均未实施                                         |
|--------------------------------------------------|--------------------------------------|------------------------------------------------------------|
| 修复 Photon 特效在光影下消失、颜色异常或目标错误 | 先检查 Photon 原生目标解析和合成模式 | 收集实际帧内诊断，区分目标、深度、缩放、混合及后处理       |
| 指挥家线框、世界文字等保持自定义颜色             | EXIT 的定向通道有适用价值            | 先明确哪些绘制需要绕过光影，再评估 26.1.2 移植             |
| 降分辨率光影导致遮挡坐标错位                     | EXIT 的缩放探针值得借鉴              | 先复现并确认实际坐标变换；当前未确认存在该故障             |
| 所有模型、粒子统一绕过光影                       | 不建议作为默认方案                   | 会改变世界融合、阴影及后处理语义，不能作为兼容修复附带实施 |
| 自动修复相交法阵、任意半透明或 ShaderGraph       | EXIT 不提供此保证                    | 继续区分对象排序、Alpha 深度预写和多层透明问题             |

研究结论不构成新增依赖、移植、改变材质/合成策略或批量路由的授权。

## 当前环境与证据

| 项目                        | 当前值                           | 证据                                                                                           |
|-----------------------------|----------------------------------|------------------------------------------------------------------------------------------------|
| Minecraft                   | 26.1.2                           | [版本目录](../../gradle/libs.versions.toml)；IDEA 解析 minecraft-patched-26.1.2.100-merged.jar |
| NeoForge                    | 26.1.2.100                       | 版本目录、build.gradle.kts、IDEA 依赖                                                          |
| Java                        | 25                               | [构建配置](../../build.gradle.kts) 的 toolchain                                                |
| Photon / LDLib2 / KilaGraph | 26.1.2.3 / 26.1.2.42 / 26.1.0.15 | 版本目录及 IDEA 实际类路径                                                                     |
| GeckoLib / ParticleStorm    | 5.5.2 / 1.4.4                    | 版本目录及 IDEA 实际类路径                                                                     |
| Iris / Sodium               | 1.11.4+mc26.1.2 / 0.9.2+mc26.1.2 | IDEA 实际依赖名称；未写在版本目录中                                                            |
| 本地 JAR 来源               | lib/*.jar 加入 implementation    | build.gradle.kts；没有本次新增依赖                                                             |

读取时 run/client/config/iris.properties 为 enableShaders=false，选定文件为 ComplementaryReimagined_r5.9.3.zip。它仅是磁盘配置快照，不证明当前游戏进程的启用状态，也不是 BSL/Kappa 运行验证。

本地 photon-client.toml 为 iris_composite_mode=AUTO、fx_composite_mode=LATE、iris_use_translucent_particle_program=false，Bloom 和光影下自定义后处理开关启用。本轮只读，没有改变任何设置。

### EXIT 上游版本

浏览器核对 [全部分支](https://github.com/hotpad100c/emergency-exit/branches/all) 时仅有 master 与 port/1.21.11，没有查到 26.1.2 分支。master 页面显示提交 4709b76b998ec1bf79b11be5d7b95d7a3d0b80a8，README 声明 Minecraft 26.3、Fabric/NeoForge 入口、版本 0.1.0+26.3；仓库页面没有已发布 Release。分支、发布与兼容声明需要在实际接入前重新查询。

26.3 实现使用 com.mojang.renderpearl.api，当前 Photon/项目使用 com.mojang.blaze3d 管线与纹理 API。上游 portal 对 26.3 已移除的 output override 另做 Mixin；当前 PhotonRenderOutput 仍读取 RenderSystem.outputColorTextureOverride/outputDepthTextureOverride。不能只改依赖版本号或复制调用示例。

IDEA 已确认当前 Iris 具有 ImmediateState.bypass、线程局部 skipExtension、isRenderingLevel，以及 IrisPipelines.copyPipeline。说明部分底层机制存在移植基础，不代表完整 EXIT、Mixin 位点、深度复制或 GPU Shader 已兼容。

## Lobecorp 已有渲染实现

### Photon 原生路径

以下实现来自 IDEA 实际解析的 photon-neoforge-26.1-26.1.2.3-sources.jar，包名前缀为 com.lowdragmc.photon，不能按更早版本记忆替代：

1. client.render.PhotonWorldRenderState 在世界绘制阶段 drain 作业，保留普通合批及 InstancedJob。颜色先进入自己的 RGBA16F HDR 目标或透明 FX 层，再做 Bloom/后处理及合成；透明层并非未经处理的原始几何。
2. client.compat.iris.internal.IrisTargetResolver 查询光影粒子程序实际 FBO、draw buffer、colortex、格式、深度及尺寸；不会固定认定 attachment 0 就是最终颜色。
3. 可合成的颜色目标使用 PREMULTIPLIED_ACCUM；判断为编码 GBuffer 等不适合写颜色的目标时，选择 AFTER_PACK。SCENE_REPLACE 和 DISABLED 也有明确分支，AUTO 是选择入口。
4. PhotonWorldRenderState.finishLayer 将可写目标立即合成；需要延后的层交给 PhotonDeferredLayer.park，由 PhotonPostFX.onLevelRenderComplete 调用 compositePending。存在帧末旁路能力，但不等同于 EXIT 的通道深度体系。
5. AFTER_PACK 的特效不再经过光影的 Bloom/DOF/雾；Photon 自己的 HDR/Bloom 仍有单独流程，不能把它与光影后处理混为一谈。
6. IrisTargetResolver.reportDegradations 在目标与缓冲尺寸不同时记录 SIZE，在深度附件不同时记录 DEPTH。已核对的路径没有体现 EXIT 那种通过已知四边形反求缩放和平移的机制；这只是潜在研究缺口，不能直接认定为当前 Bug。

关键源码窗口：PhotonWorldRenderState.drain 328–605、finishLayer 622–642；IrisTargetResolver.resolveMode 313–339、reportDegradations 437–451；IrisCompositeMode；PhotonDeferredLayer；PhotonRenderOutput；PhotonPostFX.onLevelRenderComplete。

### Lobecorp 对 Photon 的扩展

[PhotonWorldRenderStateMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/render/client/PhotonWorldRenderStateMixin.java) 在原作业上传和 scene capture 后、颜色绘制前加入 Alpha 裁剪深度预写，同时接入视深排序、帧末标记清理及缓冲复用。它没有在本轮新增或替换 Iris 路由。

[PhotonOcclusionPipelineUtil](../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonOcclusionPipelineUtil.java) 只支持已识别的原生 particle 顶点和 HDR/Pixel/Sprite 片元组合；自定义材质 Uniform、线框和未知着色器不走该深度变体。颜色仍保留 Photon 原管线，深度预写不等于 EXIT 的玻璃/手掩码。

[PhotonFixedRenderUtil](../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonFixedRenderUtil.java) 为 Emitter 和 PhotonModelEmitter 接入既有修复渲染状态。这不是“固定最终颜色”或“已绕过光影”的开关。完整排序、遮挡及已知限制见[粒子与渲染](particle-rendering.md#photon-不透明像素遮挡扩展)。

Mixin 中仍有 Photon 26.1.2.2 的位点说明，当前依赖已经是 .3。当前可解析到相应 drain 签名不等于运行注入成功；本轮没有改注释、调整注入或重新运行客户端。

### 其他绘制路径

| 路径                     | 当前源码入口                                                                                                                                                                                                                                                                                      | 与 EXIT 的关系                                         |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| 指挥家环、路径、瞄准预览 | [ClientRenderingEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRenderingEvents.java) 分发 SubmitCustomGeometryEvent；[ConductorRendering.onCustomGeometry](../../src/main/java/org/unitego/lobecorp/client/conductor/render/ConductorRendering.java) 提交 RenderTypes.lines | 是定向 batch/draw 候选；需要明确保留遮挡还是透视       |
| 世界文字粒子             | [TextParticleGroup.State.submit](../../src/main/java/org/unitego/lobecorp/client/particle/text/TextParticleGroup.java) 按字体 RenderType 合批提交                                                                                                                                                 | 有独立提交边界；不能因接入 Photon 就自动覆盖此路径     |
| 旧女皇星束粒子           | [QueenLaserParticle](../../src/main/java/org/unitego/lobecorp/client/particle/QueenLaserParticle.java) 使用 SingleQuadParticle、TRANSLUCENT、FULL_BRIGHT                                                                                                                                          | 属于原版粒子组；FULL_BRIGHT 不保证绕过光影后处理       |
| 女皇实体主体             | [TheQueenOfHatredRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/abnormalitie/the_queen_of_hatred/renderer/TheQueenOfHatredRenderer.java) 继承 GeoEntityRenderer                                                                                                                 | 普通实体渲染不是可直接全局替换的 EXIT 通道             |
| Photon 编辑器预览        | [PhotonEditorWorldRenderMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/render/client/PhotonEditorWorldRenderMixin.java) 及 LDLib2 自有场景                                                                                                                                          | 目标、相机与生命周期独立，世界场景接入不证明编辑器兼容 |

本表确认的是源码路径存在，不保证这些旧粒子仍被所有当前技能调用；本轮没有更改技能调用链或 ParticleStorm 关键帧分发。

## EXIT 能补充什么，以及会改变什么

### 独立深度与坐标对齐

EXIT 在手绘制前、半透明绘制前分别保存深度，帧末再参考最终深度。通道可按世界遮挡，也可透视；手像素通过两份深度差异识别，玻璃后内容默认变暗至 50%。这是透明表面的近似处理，不还原完整折射、着色与多层透射。

BSL 路径直接复制深度；Kappa 示例将场景缩到缓冲角落，EXIT 用已知四边形探针求每轴 a、b，再按 floor(a*p+b) 读取深度；SR 路径读取其渲染尺寸，让通道与场景同尺寸，最后拉伸合成。探针结果下一帧生效，只随管线/尺寸变化重新测量；SR 与 TAAU 叠加、任意顶点扭曲仍有边界。

这与“目标纹理宽高相同”是两个问题：同尺寸缓冲也可能只有一角存在有效场景。Photon 的尺寸提示不证明已经处理了这种顶点缩放；同样，EXIT 示例成功也不证明所有光影都可使用相同探针。

### 不能直接包裹 Photon 完成兼容

- EXIT 通道声明为 RGBA8+D32，Photon 原特效目标为 RGBA16F。将原 HDR 特效直接改画到 RGBA8 可能提前截断高亮颜色并影响 Photon Bloom；这是基于格式与流程的风险判断，未做运行对照。
- Photon 已输出到自有颜色/深度纹理，portal 自动重定向主目标不意味着会接管这些自有目标、scene capture、mask 和后处理。需要有明确的输出边界与合成顺序。
- EXIT 只能为其管理的提交自动补画半透明深度；任意自开 pass 中已混合的透明像素不因此得到准确几何深度。不能默认 Photon 原始作业或 ShaderGraph 被 portal 看见后就能准确跨通道排序。
- EXIT 的多通道合成每像素只保存一个深度，三个及以上重叠通道有近似误差，不是通用 OIT。现有 Alpha 阈值预写和视深排序仍各有职责。
- 选择绕过光影意味着退出光影的着色、雾、TAA 等流程，且不向光影阴影 pass 提交。若用户希望特效自然融入世界，应优先保留 Photon 可用的光影内合成路径。

## 后续调用与核对顺序

后续请求“按 EXIT 研究处理光影兼容”时，先读本页和[粒子与渲染](particle-rendering.md)，然后通过 IDEA 重新核对以下内容；本节仅为后续方案，没有执行或授权其中的运行操作。

1. 确认 Minecraft、NeoForge、Photon、Iris 实际版本，以及 EXIT 是否新增对应分支/Release。重新读取源码，不复用 26.3 的 RenderPass、纹理或 Mixin 签名。
2. 一次确认一个关键目标：保留光影处理、保持自定义最终颜色，或显示透视辅助内容。先选择一个具体效果/绘制入口，避免全局包裹。
3. 检查当前 IrisConfig、PhotonConfig、IrisCompat、IrisDiagnostics 及实际帧内 IrisFrameTarget/lastFailure/degradations。区分没有绘制作业、DISABLED、编码目标、SIZE、DEPTH 与混合错误。
4. Photon 路径先评估原生 AUTO/AFTER_PACK 能否满足目标；若只有坐标对齐缺口，再评估在既有兼容层内借鉴探针，不默认新建第二套帧生命周期。
5. 独立线框/文本路径再评估 26.1.2 的受控通道接入。定清 HDR/Alpha、主目标与自有目标、普通/实例化几何、半透明深度、手与玻璃、首次出现和退出清理的所有权。
6. 明确授权实现后，另行确定版本移植、依赖、客户端可选加载与事件入口。新库、资源与协议变化不能由本研究自动推出。
7. 需要运行验收时另行授权：无光影、BSL、当前 Complementary、Kappa 降分辨率、窗口变化、第一/第三人称、手遮挡、玻璃/水、淡出、交叉透明、HDR/Bloom、CPU/实例化、编辑器与世界卸载分别验证。SR 与 TAAU 组合列为独立未支持/待研究项。

可直接使用的后续任务描述：“先按 shader-compatibility-exit.md 刷新当前版本，定位指定效果的实际绘制路径与 Iris 合成目标，确认是复用 Photon、借鉴深度对齐还是移植独立通道；确认实现范围后再修改。”该描述不预先授权修改或主动验证。

## 来源、检查与限制

- [EXIT 流程页面](https://hotpad100c.github.io/emergency-exit/render-scale/#bsl)：BSL/Kappa/SR 对照、手与玻璃、缩放探针及作者声明的验证边界。
- [固定提交 README](https://github.com/hotpad100c/emergency-exit/blob/4709b76b998ec1bf79b11be5d7b95d7a3d0b80a8/README.md)：26.3 API、通道格式、透明补深度、portal 与合成顺序。
- [固定提交 SceneScale](https://github.com/hotpad100c/emergency-exit/blob/4709b76b998ec1bf79b11be5d7b95d7a3d0b80a8/common/src/main/java/ml/mypals/exit/impl/SceneScale.java)、[SceneDepth](https://github.com/hotpad100c/emergency-exit/blob/4709b76b998ec1bf79b11be5d7b95d7a3d0b80a8/common/src/main/java/ml/mypals/exit/impl/SceneDepth.java)：前轮浏览器源码研究，本轮重新核对主分支标识与版本。
- 当前 Photon/Iris 依赖源码、项目 Java、配置、类型和调用通过 IDEA/MCP 读取。IDEA 检索有未覆盖/截断边界时，已对关键类直接解析；没有将无搜索命中当作能力不存在。
- 上游页面只声明 NeoForge 编译检查；Fabric 的演示帧与本地静态解析均不能替代 Lobecorp 的光影视觉验收。
- 本轮读取部分大型文件诊断返回 timedOut=true、errors=[]，结果不完整；不能记为全量无错误。文档最终诊断与实际修改清单见[本轮记录](changes.md#2026-10-18exit-光影兼容静态评估)。
- 文件只通过 IDEA 新增/行级补丁修改，保留已有文档及暂存/未暂存工作。没有删除或覆盖代码、没有手动暂存/提交；未另建备份。
