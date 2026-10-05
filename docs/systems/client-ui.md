# 客户端表现与 UI

> 源码核对日期：2026-10-05。通过 IDEA 核对客户端注册与事件、实体模型/Renderer、通用 GeckoLib
> 图层、调试显示、指挥家界面和头像缓存；未进行游戏内渲染、资源重载、窗口缩放、远程服务器或性能实测。
> 包整理方案 1 已执行，Java 链接按实际路径核对；保留关联小组及检查结果见修改记录。

## 作用与边界

客户端负责消费同步状态、提取渲染数据、提交绘制、组织输入和显示界面。服务器的技能状态、命中判断和控制资格不能由 Renderer 或 UI
反向决定。客户端实现放在 `client`；注册仍归 `registry`；自动事件入口归 `events.client`；Mixin 只负责版本相关桥接。

本页描述客户端共享基础。指挥家 HUD、镜头和输入的完整执行链见 [指挥家系统](conductor.md)，Photon
编辑器扩展见 [Photon 编辑器](photon-editor.md)，普通粒子、材质和混合见 [粒子与渲染](particle-rendering.md)
。实体状态与动画的同步边界见 [实体与技能](entity-skills.md)。

## 注册与事件链

| 入口                                                                                                                                                                                                                                                                 | 职责                                                                                                                       |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [LobecorpClientEvents](../../src/main/java/org/unitego/lobecorp/events/client/LobecorpClientEvents.java)                                                                                                                                                             | 限制 `Dist.CLIENT` 的注册事件分发：实体 Renderer、尸体翻转、render-state modifier、调试项、粒子 Provider、键位、头像和 HUD |
| [ClientRuntimeEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)                                                                                                                                                               | 客户端 tick、移动/鼠标/滚轮、登录与登出/世界卸载；向指挥家输入与 Photon 表现分发                                           |
| [ClientRenderingEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRenderingEvents.java)                                                                                                                                                           | 名称、世界 render-state 提取、自定义几何、手部及 GUI 渲染分发                                                              |
| [EntityRenderers](../../src/main/java/org/unitego/lobecorp/registry/entity/client/EntityRenderers.java)                                                                                                                                                              | 注册女皇、清道夫、尸体及三类魔法星 Renderer                                                                                |
| [RegisterRenderStateModifiers](../../src/main/java/org/unitego/lobecorp/registry/entity/client/RegisterRenderStateModifiers.java)                                                                                                                                    | 给尸体 Renderer 写入翻转控制数据                                                                                           |
| [ConductorRenderStateRegistration](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorRenderStateRegistration.java)                                                                                                                         | 给实体渲染状态写入指挥选择显示数据                                                                                         |
| [ConductorPortraitRegistration](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorPortraitRegistration.java)、[ConductorHudRegistration](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorHudRegistration.java) | 注册头像画中画 Renderer 和 `lobecorp:conductor_hud` 图层                                                                   |

扩展客户端功能时先检查现有注册或分发入口。不要在表现类新增一套监听来重复驱动同一状态；注册期获取 DeferredHolder
仅发生在正确的注册回调中，其他初始化继续使用延迟引用。

## 实体模型、资源与 Renderer

| 作用域 | 关键实现                                                                                                                                                                                                                                                                                                      | 当前资源和表现来源                                                                                     |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| 女皇   | [TheQueenOfHatredModel](../../src/main/java/org/unitego/lobecorp/client/entity/abnormalitie/the_queen_of_hatred/model/TheQueenOfHatredModel.java)、[TheQueenOfHatredRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/abnormalitie/the_queen_of_hatred/renderer/TheQueenOfHatredRenderer.java) | 模型/动画 ID `lobecorp:entity/the_queen_of_hatred`；根据 QUEEN_PHASE_TWO ticket 切换普通与歇斯底里纹理 |
| 清道夫 | [SweeperModel](../../src/main/java/org/unitego/lobecorp/client/entity/ordeal/indigo/model/SweeperModel.java)、[SweeperRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/ordeal/indigo/renderer/SweeperRenderer.java)                                                                           | 变种模型/动画/纹理映射，当前 B/C/D 显式回退 A 资源；尸体使用单独模型/纹理，生物量与发光读渲染数据      |
| 尸体   | [EntityCorpseRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/renderer/EntityCorpseRenderer.java)、[EntityCorpseReverse](../../src/main/java/org/unitego/lobecorp/registry/entity/client/EntityCorpseReverse.java)                                                                            | 复用原实体表现，翻转例外通过 EntityCorpseReverseEvent 与 modifier 提供                                 |
| 魔法星 | [MagicStarRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/projectile/renderer/MagicStarRenderer.java)、[MagicStarRenderState](../../src/main/java/org/unitego/lobecorp/client/entity/projectile/renderer/MagicStarRenderState.java)                                                          | 专用投射物 render state；具体资源和尺寸跟随原实体定义                                                  |

模型类选择资源，Renderer
从客户端实体状态提取当帧数据并提交绘制。[LcDataTickets](../../src/main/java/org/unitego/lobecorp/registry/animation/client/LcDataTickets.java)
集中声明 GeckoLib DataTicket；清道夫 Renderer 提取变种、生物量比值等数据，模型及图层消费该数据。新视觉参数应明确权威来源、同步途径、默认值与失效处理，不用渲染缓存代替实体状态。

手写资源位于 [assets/lobecorp](../../src/main/resources/assets/lobecorp) 的
geckolib/models、geckolib/animations、textures 等目录。Java 包移动不改变资源 ID、骨骼名或纹理命名。资源作者提供的 JSON、图像和
NBT 不自动格式化，也不因为整理 Java 改写它们。

## 可复用 GeckoLib 图层

| 能力                | 入口                                                                                                                                                                                                                                       | 约束与清理                                                                                                                      |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| 发光遮罩            | [AutoGlowingRenderLayer](../../src/main/java/org/unitego/lobecorp/client/renderer/AutoGlowingRenderLayer.java)                                                                                                                             | 从基础纹理派生 `_glowmask.png`，缺少资源则跳过；强度来自参数或 DataTicket，限制到 0～1；临时改变 render color 后在 finally 恢复 |
| 骨骼可见性          | [BoneVisibilityGeoLayer](../../src/main/java/org/unitego/lobecorp/client/renderer/BoneVisibilityGeoLayer.java)                                                                                                                             | 用 Predicate 或 `fromDataTickets` 计算当前帧可见性；未提供 ticket 时默认可见，只跳过目标骨骼自身几何，不连带隐藏子骨骼          |
| 定向缩放立方体与 UV | [DynamicCubeGeoLayer](../../src/main/java/org/unitego/lobecorp/client/renderer/DynamicCubeGeoLayer.java)                                                                                                                                   | 指定骨骼及各 Direction 的缩放 ticket，跳过原骨骼几何并提交替代几何；可组合额外 RenderPassProvider，共享基础纹理和遮罩           |
| 指挥选择轮廓        | [ConductorGeoOutlineLayer](../../src/main/java/org/unitego/lobecorp/client/conductor/render/ConductorGeoOutlineLayer.java)、[ConductorRendering](../../src/main/java/org/unitego/lobecorp/client/conductor/render/ConductorRendering.java) | 根据指挥家选择状态显示，不保存服务端队伍权威状态                                                                                |

这些图层面对 GeoAnimatable/GeoRenderer 共享契约，保留在 `client.renderer`。新增实体优先组合已有图层；修改几何或发光时检查
packed light、overlay、颜色、深度、骨骼 pivot、UV 和额外 render pass 一致性。

## 调试显示

[LcDebugEntries](../../src/main/java/org/unitego/lobecorp/registry/entity/client/LcDebugEntries.java)
集中注册状态、技能、技能效果、命中框及原版调试开关，并按启用状态注册 Renderer。调试显示属于诊断消费端，不承担技能推进或命中结算。

| 调试内容       | 实现                                                                                                                                            | 数据来源                                                    |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| 实体状态       | [EntityStateDebugRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/state/debug/EntityStateDebugRenderer.java)                    | EntityStateHolder 与客户端实体状态，按距离显示文本          |
| 技能运行       | [EntitySkillDebugRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/skill/debug/EntitySkillDebugRenderer.java)                    | 技能能力/当前同步运行态，用于观察技能阶段                   |
| 技能效果       | [EntitySkillEffectDebugRenderer](../../src/main/java/org/unitego/lobecorp/client/entity/skill/effect/debug/EntitySkillEffectDebugRenderer.java) | Level 的客户端命中框快照中携带的 EntitySkillEffectDebugInfo |
| 命中框几何     | [HitboxDebugRenderer](../../src/main/java/org/unitego/lobecorp/client/hitbox/HitboxDebugRenderer.java)                                          | HITBOX_LEVEL_DATA 的客户端快照；不是客户端伤害检测          |
| 本地 tick 监控 | [LcDebugRuntimeOptions](../../src/main/java/org/unitego/lobecorp/client/debug/LcDebugRuntimeOptions.java)                                       | 客户端向本地集成服务器传递的运行选项，保持配置默认值回退    |

新增调试项先复用 LcDebugEntries 和现有网络快照；只显示实际可取得的同步信息。不要把本地集成服务器专用选项当成远程服务器通信，也不要为普通同步字段重复增加载荷。

## 界面与 LDLib2 约定

当前版本目录锁定 Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25、GeckoLib 5.5.2 与 LDLib2
26.1.2.39，见 [依赖版本](../../gradle/libs.versions.toml)。复用依赖 API 时以 IDEA 当前解析为准，旧文档或其他模组示例不能证明本版本可用。

### 现有运行入口

[ConductorHud](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorHud.java) 实现 `ModularHudLayer`
，持有代码构建的 UI 与
ModularUI，按成员/选择/技能变化挂载卡片；[ConductorLdlibScreen](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorLdlibScreen.java)
实现 `ModularUIScreen`，`create`/`create(Tab)`
代码创建团队、单位和远程页。[ConductorControls](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorControls.java)
的 `openPanel` 选择该页面并请求最新目录。

当前另有 [ConductorScreen](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorScreen.java) 原生
Screen
实现。[resources/ldlib2](../../resources/ldlib2) 中 XML 和 UI NBT 是项目现有资产；所核对的 HUD/LDLib 页面 `createLayout`
使用 Java，不应假设编辑资产即可改变这两个运行界面。

当前界面使用本地输入与缓存，权威命令通过已有 Payload 提交。新增界面先判断是否只操作客户端本地状态、已有命令是否足够，或需要真正
Menu-backed 交互；不要因为使用 LDLib2 就绕过服务器校验。

### 组件与交互修改检查

- 沿用现有 UI 风格、组件、主题和对话框；Photon
  内扩展完整使用原生编辑器样式，具体开关与交互见 [Photon 编辑器](photon-editor.md)。
- 文本、元素
  ID、配置及资源路径使用现有集中定义；指挥家主题由 [ConductorHudTheme](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorHudTheme.java)
  、业务文本由 [ConductorTexts](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorTexts.java) 管理。
- 动态挂载/重建只维护当前内容，避免重复监听和重复追加；有文本焦点时区分文本编辑与全局快捷键，窗口失焦时释放控制输入。
- 滚动需要受限视口与真实内容高度；检查 ScrollerView、父容器尺寸和 adaptive height，不以“显示滚动条”作为滚动可用的证明。
- 如果新页面实际使用 UITemplateElement，核对 provider 注册、资源路径、元素 ID、具体类型、local stylesheet、类名及 selector
  祖先关系；本次未为现有代码页面改成模板驱动。
- 弹窗修改使用工作副本，确认后提交；取消不能留下业务修改。需要撤销的编辑器操作接入既有历史事务，不建立平行撤销栈。

## 执行链与会话/缓存清理

1. `LobecorpClientEvents` 注册客户端资源、Renderer、调试项、HUD 和输入键位。
2. `ClientRuntimeEvents` 消费客户端 tick/输入及会话事件；`ClientRenderingEvents` 消费当前渲染阶段并分发到指挥显示。
3. 实体 Renderer 在当帧 render state 中提取所需事实；模型、图层和提交器读取该状态，不长期缓存实体能力或服务器对象。
4. 指挥 HUD
   头像通过 [ConductorPortraitCache](../../src/main/java/org/unitego/lobecorp/client/conductor/render/ConductorPortraitCache.java)
   收集目标、提交画中画状态，再由注册的头像 Renderer 绘制；`beginFrame` 管理帧缓存、`clear` 管理失效。
5. 登出及客户端世界卸载经 `ConductorControls.clearSession` 退出输入控制并重置目录，同时清理
   PhotonGeoEffects。相机、选择、待命令和手动控制的完整退出链见 [指挥家](conductor.md)。

新增跨帧资源或会话状态时必须提供清理入口，并核对断线、世界卸载、资源重载、实体失效及 UI 重建。偏好与设置有自己的持久化生命周期，不因为清理会话删除用户配置。

## 配置、资源与网络关系

| 分类           | 管理位置                                                                                                                                                                     |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 客户端偏好     | [ConductorHudPreferences](../../src/main/java/org/unitego/lobecorp/client/conductor/hud/ConductorHudPreferences.java) 使用 config 下的 Properties；Photon 设置见独立系统文档 |
| 游戏键位       | [ConductorKeyMappings](../../src/main/java/org/unitego/lobecorp/registry/conductor/client/ConductorKeyMappings.java)；键位注册与运行时输入处理分开                           |
| 模型/动画/纹理 | [手写客户端资源](../../src/main/resources/assets/lobecorp)；骨骼名及资源 ID 由对应模型/图层集中管理                                                                          |
| UI/编辑器资产  | [resources/ldlib2](../../resources/ldlib2)；是否为运行时权威取决于具体加载代码                                                                                               |
| 同步           | [LcPayloads](../../src/main/java/org/unitego/lobecorp/registry/LcPayloads.java)、实体同步数据/附件和既有 tc/ts 载荷；渲染只消费同步结果                                      |

## 修改前检查点

先确认发行侧和当前 render-state API，再沿注册 → 事件分发 → 运行类 → 资源路径检查 IDEA 引用。新增表现先查是否已有
DataTicket、图层、头像提交或调试快照能力；不能以新增 RenderLayer 代替需要服务端同步的事实。

修改 UI 时同时读运行 wrapper
与资产来源；修改资源时保留作者排版，生成输出只通过生成源更新。涉及动画和粒子状态时遵循 [实体动画规范](../../.codex/skills/lobecorp-entity-animation/SKILL.md)
和 [实体视觉同步规范](../../.codex/skills/lobecorp-entity-visual-sync/SKILL.md)。

## 待查项与文档维护

- 本次未做分辨率/GUI 缩放、动态卡片滚动、键盘导航、跨窗口焦点、透明遮挡或多人同步验收。
- 发光遮罩存在性缓存、头像画中画缓存在资源重载和不同模型切换下的失效行为需结合运行核对；这里未将未验证路径描述为已修复。
- UI XML/NBT 资产的全部独立用途尚未逐文件追踪；添加模板页面前继续核对实际资源 provider 和加载链。
- 按 [系统变更记录](changes.md) 记录新增/修改的入口、UI 操作、配置、资源和同步关系及实际诊断；包迁移完成后确认目标链接与类声明一致。
