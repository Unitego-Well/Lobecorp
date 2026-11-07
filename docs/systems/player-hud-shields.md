# 玩家 HUD、屏幕滤镜与四色护盾

## 2026-11-07 吸收伤害与滤镜

额外生命承受的伤害也通过最终NeoForge结算触发原伤害滤镜；四色护盾仅给出消耗数字，不产生四色滤镜，完全挡住时抑制本次原版受击镜头和击退。部分穿透保留余伤反馈。负系数实际恢复显示绿色+数值，不触发受伤滤镜。不通过改成攻击失败或提前扣盾实现，现有成功语义与吸收顺序保留。实现范围与未实机验收边界见[四色伤害](four-color-damage.md#2026-11-07-伤害反馈边界)及[屏幕数字](screen-damage-particles.md)。


## 2026-11-07：头像居中与状态数值文字修正

头像组件修正脸部/外层的内部偏移，面板仅创建时按(头像框尺寸-头像实际尺寸)/2定位；保留用户头像大小，不逐帧重排。数值开启阴影，格式为当前值+附加值 / 最大值（例如12+111 / 20），深灰加号且两侧无空格；理智高蓝低红仅作用当前值，最大值和附加值白色。既有Shift及2秒保持/0.25秒淡出不变；未进行游戏视觉验收。

## 2026-11-07 NEW面板数值显示

数值按“当前值 / 最大值”显示，吸收与护盾加号灰色。理智文字复用既有高低阈值：比例≥0.7蓝色、<1/3红色，中间白色；其他附加数值白色。初次加载只建立基线，默认隐藏。当前值、最大值或任一段数值变化后重置提示：保持40游戏tick（2秒），随后5tick（0.25秒）仅淡出，无淡入；暂停沿现有HUD时间暂停。按住Shift直接全显，不设置持续或淡出；松开时只有未结束的变化提示继续，否则立即隐藏。每条独立计时，切换玩家/清理会话时重置，不改条动画或用户头像改动。

本轮仅构建/静态检查，不代表已完成游戏内颜色、Shift或淡出视觉验收。


## 2026-11-06 NEW 独立纹理与 nine_slice

生命组合图按原像素拆为 full、poisioned、withered、frozen、absorbing、physic_shield_bar、erosion_shield_bar、null（各38×7）；理智拆为 rationality_tall、rationality_low、rationality、spirit_shield_bar（各20×7）。null只保留占位，不接入灵魂护盾。12张输出已逐像素核对；源图原字节备份位于 build/agent-backups/20261106-newhud-texture-split，确认无代码引用后删除两张旧value.png。

12张值纹理各配置png.mcmeta的nine_slice，左右边框1、上下0；生命/理智box左右20/15、上下0，头像框和icon未改。PanelStatusBar改为选择独立精灵，移除横向UV偏移和纵向切行，使用完整精灵的原版nine_slice规则。框按动画合计值/最大值伸长，比例限制1～3：生命66～198px，理智48～144px；内部值区增加与框相同的宽度，右侧数字跟随框尾。保持此前挤压、减少变白和pose抖动规则。

IDEA检查无错误；源码及测试源码编译、生产包隔离通过（39秒）。12张裁切像素一致，14份元数据合法且全部入包，生产包旧组合图条目0。未运行游戏或测试，视觉尚未验收；未提交。二进制清理的CLI例外授权已按用户要求通知协调。

## 2026-11-06 NEW 值条、护盾与减少反馈

生命 NEW 与默认/原版互斥，理智 NEW 与默认条互斥。新增 shieldStyle=DEFAULT/NEW（默认DEFAULT）；NEW 护盾仅随对应 NEW 值条生效，否则保留默认独立条。物理/侵蚀映射生命，精神映射理智；灵魂护盾保留默认条，不使用生命纹理第8个占位行。

PanelStatusBar 复用 StatusBarComponent/StatusGauge；生命值纹理38×63、理智20×31，行内容7px、行距1px，以v=row×8选择。生命基础值按原有心形类型选择默认/中毒/凋零/冰冻行，吸收为第5行，物理/侵蚀为第6/7行；理智复用原阈值，高/低/中/精神护盾依次选行。新旧条共享状态判断，不重复触发隐藏旧生命条的心形事件。

SegmentedBarLayout 按值→吸收→护盾分配宽度；挤压时基础值至少保留20%，非零附加段优先保留2px，不够容纳时隐藏尾部。各段横向UV按其相对整条填充区域的起点向下取整，纵向UV仍选择状态行，不从额外段自身重启纹理。条形使用动画值，数字使用真实值，右侧显示最大 / 当前 + 吸收 + 护盾，零附加项不显示，隐藏段仍计入数字。

对应段减少后白色闪光2tick并保留损失白条。StatusBarShake 按各段减少量/该段容量计算百分比，每减少1%对应0.1px，抖动向量幅度上限10px，不设1px下限；在完整圆周随机选择方向，8tick内偏移、反向回弹、复位。整框、图标、数值段和右侧数字共用一次pose变换，不修改布局坐标。初始采样、切样式、会话切换不触发虚假损失，暂停不推进动画。

源码及测试源码编译、生产包隔离、Data已通过；Data23输出写入2份语言文件。最终IDEA检查无错误；收尾构建16秒成功，新增护盾配置中英文翻译已进入构建资源。仅编译测试源码，未执行测试或游戏，视觉效果未验收；未提交。

## 2026-11-06 左上区域统一堆叠

TopLeftHud 将 status 包中的 PlayerStatusPanelHud 与 EventHud 作为独立子元素，以通用纵向容器排列；边距4、间距4统一归组合层。EventHud 不再引用状态面板、面板显隐或高度，其布局从局部原点0开始。保留子包装优先的事件顺序，面板隐藏由容器补位；会话同步保留先到事件，退出显式清空两部分。注册层序及ID保持。代码与测试源码引用已更新；IDEA检查无错误，compileJava、compileTestJava及生产包隔离检查通过（18秒）。仅编译测试源码，未执行测试或游戏，未提交。

## 历史：2026-11-04 LDLib2 组件迁移

四类玩家/事件/滤镜图层统一从 `HudLayerManager.layer(id)` 接入，ID 由所属入口集中提供，保留原版层包装与层序。事件包装通过 `IHudUpdatable` 在布局前更新；注册侧 `TopLeftHud` 持有固定玩家面板，事件视图不再识别具体玩家组件类型。`PlayerAvatar` 组合框背景、皮肤基础脸与外层，保持 50/40/44 尺寸；面板保持 119×50、右侧间距 3、健康框 66×15 与理智框 47×15。统计文字使用 Label；Post 阶段指挥家指令光标由已有事件入口先行分发，特殊绘制与统计分开。

本次验证：IDEA 检查、BenchMod Compile（含生产包隔离）、7 类 52 项状态测试与 Data 通过，Data 23 输出写入 0。游戏只检查本工作区新建测试世界的头像/理智条、护盾 10→6 的部分填充/闪光以及创造/生存/旁观显隐；不将这些截图视作全部样式、低理智滤镜交叉淡变、特殊投影和缩放矩阵的完整视觉验收。新 HUD 数值填充和计时器业务仍未新增。

## 当前架构契约（2026-11-05）

普通战斗 HUD 使用原版 `GuiLayer/GuiGraphicsExtractor`。调用链为注册入口 → 具体 HUD → 绘制组件；玩家状态、事件和滤镜不再经过 `HudLayerManager/HudHost/HudFrame`。管理器与 LDLib2 宿主只保留给指挥家实际需要的交互、布局与布局后绘制。屏幕粒子直接调用原有 `ScreenParticles.extract`，投影、合并和寿命规则仍由粒子系统持有；统计文字在原 Post 入口直接绘制。

### 模块化渲染基础

非指挥家 HUD 使用 BasicHudRenderer 保存浮点 x/y/width/height、visible 和独立的位置/尺寸像素对齐策略（INHERIT/NONE/ROUND/FLOOR/CEIL）。容器累加原始父坐标，最终绘制才对齐，不回写布局或动画。

ContainerHudRenderer 管理单一父级、子元素、自由/纵向/横向布局、间距、自动尺寸、起点/居中/末端锚点和可选剪裁；隐藏子元素不占排列空间。TextureHudRenderer 的 sprite(id) 使用图集相对 ID，texture(id) 使用完整纹理路径；纹理共用颜色、透明度、尺寸与 UV，精灵保持原版缩放元数据。RectangleHudRenderer 处理纯色/渐变，TextHudRenderer 处理文字测量、阴影和对齐。

AbstractHud 统一世界/玩家身份切换、暂停帧增量和渲染入口；玩家、事件和滤镜分别保留自身显隐及清理规则。根默认随屏幕尺寸，setFitToScreen(false) 可使用自定义尺寸。

StatusBarComponent、StatusGaugeView、PlayerStatusHudView、PlayerStatusPanelHud 和事件组件已接入容器；StatusGauge 继续只维护动画数字。StatusGaugeView 使用纹理及矩形元素组合，面板以头像、生命和理智子元素组成，统计文字使用纵向容器。TextureLayer 保留为可变样式描述，不再承担绘制。

基本使用：容器 setPosition/setSize，add 子元素；通过 setLayout、setGap、setAutoSize、setClip、setAnchor 配置布局，通过 setPositionAlignment/setSizeAlignment 或 setPixelAlignment 配置对齐。组件先更新状态，再 render，动画推进不放在通用绘制基类。

### 浮点绘制扩展

`IGuiGraphicsExtractorExpand` 通过 `META-INF/interfaces.json` 注入 GuiGraphicsExtractor；客户端 GuiGraphicsExtractorMixin 实现接口并提供原实例的 Minecraft 与 GUI 图集。`lobecorp$` 浮点重载按当前原版 fill/blit/blitSprite、平铺和九宫格实现；像素 UV 偏移仍为整数。底层分别提交 FloatColoredRectangleRenderState、FloatBlitRenderState 和 FloatTiledBlitRenderState，保留浮点顶点，包围盒向外取整并应用原版剪裁。原 GuiUtil 已删除，调用方全部迁移。

### 状态条与纹理

`PlayerStatusHud` 直接注册原版层，取玩家与帧增量后调用 `PlayerStatusHudView.prepare/render`；视图保存本帧纯布局供渲染复用。生命、理智与四色护盾按既有底部锚点和 2 像素间距堆叠；隐藏条不占高度。护甲偏移仍使用相同布局指标，生命样式 VANILLA 决定原版心形层是否显示。

`StatusBarComponent` 负责采样、格式化与绘制顺序；`StatusGauge` 仅保留数字动画状态。`StatusGaugeView` 直接持有底图、填充和闪光纹理，外观切换直接更新组件，不再经 TextureLayer/Appearance 快照转交。填充使用 IGuiGraphicsExtractorExpand 的浮点图集区域裁切，保持原纹理比例与 mcmeta 动画。生命图形偏移 +3，文字保持组件基准 x，吸收图偏移 +13；零护盾保留文字，零理智使用动态图。文字显式关闭阴影，与本次迁移前的 Label 一致。

原始 PNG 与皮肤使用 `GuiGraphicsExtractor.lobecorp$blit`，GUI 图集使用 `lobecorp$blitSprite`，两者不能混用。`PlayerAvatar` ~~保持框先绘、~~脸后绘、外层最后绘制；框50、脸40、外层44。玩家面板119×50，右间距3，健康框66×15、理智框47×15。

### 滤镜组件

`DamageFilter` 与 `RationalityFilter` 直接实现原版图层入口，分别持有自己的配置、状态、纹理与绘制顺序。共用 `AbstractScreenFilter` 复用 HudSessionTracker、会话切换清理、显隐和暂停增量；删除 PlayerScreenFilters 门面及包装层。伤害通道用内部 Channel 集中保存 current/previous 与 ScreenFilter，删除 DamageScreenFilter 子类和平行状态数组；理智强度直接写入三层 ScreenFilter，透明度只存一份。两类滤镜共用 ScreenFilter 的纹理路径、透明度限制与提取器扩展的全屏绘制。网络通过 DamageFilter.receiveHit 先同步会话再命中，生命周期分别 tick/resetSession；原有测试观察入口保留。

### 动效与会话

状态条与滤镜沿用暂停归零的实时帧增量；插值、拖尾、两段闪光、重复命中复位和隐藏行为保持。render 不推进动画。伤害滤镜状态按首次命中登记，绘制使用相反顺序以保持迁移前实际覆盖关系：LDLib2 45 先按同 z 的子索引降序排序，再反向绘制，净效果为加入顺序；旧视图逆序加入，原版循环因此同样逆序。理智三层保持原顺序。缺失滤镜资源跳过，alpha 只应用一次。

玩家状态、事件与滤镜通过 AbstractHud 统一使用 HudSessionTracker 检查 level/player 身份。滤镜 hit 与事件 show 在写状态前同步会话，首次渲染不会误清同会话的新事件。ClientRuntimeEvents 的登录、退出和世界卸载统一显式清理普通 HUD 与保留的指挥家管理器；暂隐不等于销毁。

### 事件布局与注册

事件组件继承 ContainerHudRenderer，具有浮点位置、尺寸、显隐和子元素绘制能力。EventHud 先画可见玩家面板，然后用生产布局函数递归排列：子包装先于所属容器的活动事件，根事件同样参与，活动实例仍按 show 顺序。面板隐藏时不占高度，无会话直接返回。首次绘制锁定包装注册；show/hide 的 UUID 幂等、类型冲突检查与清理定义保留。

注册仅保留包装 ID/父子关系与事件工厂；旧 UIElement 工厂、任意 LSS 根布局入口、EventHudView 与 IHudUpdatable 已移除。当前事件业务仍为左上纵向堆叠；基础容器另提供自由、纵向、横向布局及锚点。MidnightCleanupHud 仍为空业务组件，未新增计时器或 NEW 数值绘制。

PlayerHudRegistration 直接注册玩家状态、事件与两个滤镜，保留原版生命层包装和护甲位移。层序仍为玩家状态高于 PLAYER_HEALTH；伤害滤镜高于 CAMERA_OVERLAYS；事件高于伤害滤镜；理智滤镜高于所有已注册层。

本轮实现与验证结果以最新修改记录为准。下方按日期保留历史；历史 LDLib2 迁移测试不能作为当前原版实现的验收证据。

## 2026-11-01 修复迁移后玩家 HUD 初始化被跳过

HudLayer曾创建宿主ModularUI，但getModularUI只返回UIElement的框架归属；该归属在ModularUI.init时才赋值。ModularHudLayer.render先读getter，读到null就返回，导致根UI永远无法进入init。现根层优先返回已创建宿主，子节点未拥有宿主时仍返回框架归属。此次为可直接确认的绘制跳过路径修复，IDEA定向编译通过；未做游戏视觉复测，不宣称所有迁移HUD均已实机恢复。

## 2026-11-01 HUD 迁移补全：运行覆盖层

已盘点全部RegisterGuiLayersEvent入口与RenderGuiEvent.Post分发：生命/理智/护盾、伤害/低理智滤镜、屏幕粒子、事件HUD、指挥家HUD均经LDLib2驱动。另将Post阶段的指挥家指令光标/框选及Horde统计文字接入RuntimeOverlayHud的LDLib2画布，保持原Post阶段和先指令后统计的绘制顺序；沿既有登录/登出/卸载入口释放UI，不新增订阅。特定投影、精灵裁切与画线仍由组件回调复用原绘制算法，未重写世界渲染或原版HUD。

主线最后IDEA项目编译成功；前一批Bench生产包隔离与Data通过，最后补全仅代码、无资源变化。未做实机视觉验收，不能将编译成功当作位置、动画、交互及滤镜覆盖正确。原业务WIP与AM测试保留。

## 2026-11-01 全部模组 HUD 迁入 LDLib2

玩家生命、理智、四色护盾与两个全屏滤镜统一由 `HudLayer` 的LDLib2 ModularUI驱动；各子层为真实UIElement，CompositeHudLayer通过子节点树提交，不再逐个调用子层独立render。既有状态取值、精灵、动画、配置、存活与创造模式可见条件、护甲偏移与滤镜注册层级保留；精灵裁切等特定绘制在LDLib2节点回调中执行。

HudLayer的getModularUI返回框架维护的UI归属，只有HUD根render入口创建宿主，不在子节点getter里懒建UI；clear释放宿主后保留节点结构，支持会话重建。ScreenParticleHud接管屏幕伤害粒子的GuiLayer注册与UI生命周期，在LDLib2画布回调内继续使用原粒子投影、合并、内容和动画；登录、登出、卸载沿既有事件入口释放画布。事件HUD与指挥家HUD原已使用LDLib2，本轮保留；原版HUD与世界渲染不在迁移范围。

验证：主线审查源差异并修正根/子UI归属，IDEA项目编译与最后生命周期改动定向编译成功；BenchMod Compile与生产包隔离成功；Data退出0，生成目录无Git差异。尚未进行实机视觉验证，状态条位置/动画、滤镜覆盖顺序和屏幕粒子画面不可据编译判定验收通过。

## 历史：LDLib2 事件 HUD

事件 HUD 已改用「根布局配置 + 第一层包装」：`EventHudView` 根为满屏 `UIElement`，`EventHud.registerRootLayout` 在宿主首次创建前生效，调用方可用原生 LDLib2 layout/LSS 设置 flex、grid、padding 或绝对定位；默认不预置三分区。包装只能直属根，`registerWrapper` 创建第一层容器；事件定义可在注册时指定包装或直接挂根，不再通过组件类型或方向枚举决定位置。

`EventHudComponent` 只继承 `UIElement`，默认 `flexShrink(0)`；`MidnightCleanupHud` 也仅保留空组件。包装工厂延迟到宿主创建、不能跨宿主共享节点；重复包装 ID、null 参数、引用未登记包装及宿主创建后追加包装/根布局均明确拒绝。视图保持包装节点直属根，根顺序为包装注册顺序在前、直接 HUD 的显示顺序在后，包装内按显示顺序；仅在子项顺序变化时同步，不每帧重建 UI 树。

注册签名为 `register(id, englishName, chineseName, factory)` 与带 `wrapperId` 的重载；名称保存在类型定义中，组件工厂每个活动 UUID 调用一次。同 UUID 同类型重复 show 返回已有实例，类型冲突拒绝。`show(type, uuid)` 显示、`hide(uuid)` 移除、`clear()` 清理活动 UI；换世界清理实例并保留定义。深夜清扫当前为空容器，未接入自动事件同步、纹理或计时内容。HUD 仍注册在伤害滤镜之上。

本轮 IDEA 定向编译与迁移后的 `EventHudStateTest` 通过；未进游戏，根布局、包装定位、动态堆叠及缩放尚无实机视觉证据。下方早期 GuiLayer 与方向堆叠描述为历史，已由此实现替代。

## 2026-11-01 事件 HUD 注册

`EventHud.register(Identifier, GuiLayer)` 按事件ID注册绘制器，重复ID拒绝注册，按注册顺序调用。`EventHudRegistration.register()` 由既有 `PlayerHudRegistration.register` 初始化，使用 `MidnightCleanup.ID` 注册 `MidnightCleanupHud.INSTANCE`；深夜清扫目前为空绘制入口，尚无布局、计时或激活条件。四份Java文件经IDEA定向编译通过，未做游戏验证。

## 2026-11-01 事件 HUD 显示层

`PlayerHudRegistration.register` 在既有 HUD 注册入口中注册 `lobecorp:event_hud`，使用 `registerAbove` 放在 `lobecorp:damage_screen_filter` 上方，由 `EventHud.INSTANCE` 承担绘制入口。当前仅建立空显示层，未添加计时器、文本或事件状态逻辑。两份改动 Java 文件通过 IDEA 定向编译；未执行游戏视觉验收。

## 来源与范围

2026-10-21，用户确认旧项目为 ImaginaryCraft，当前本地目录为
`E:/xm/mc/mod/ArchitectureDepartment/ImaginaryCraft`。只读参考其 GoldenBoughsLib 模块，
不修改旧项目。本次选择迁移生命条、理智条、四色护盾条及对应配置，后追加受伤和低理智屏幕滤镜；
不迁移枪械蓄力、斩味或自然恢复理智玩法。护盾世界特效渲染留空。

目标锁定 Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25。旧 Kotlin HUD 的即时 GuiGraphics 绘制
改为当前 GuiGraphicsExtractor 提取 API；不照搬旧 RenderSystem 深度、混合开关。

## 当前实施状态

代码及旧项目现存资源已完成迁移，未执行运行验收；提交结果见修改记录。

- `client.hud.PlayerStatusHud`：生命／吸收生命、理智、按物理→精神→侵蚀→灵魂排序的护盾条；
  左侧数值、变化插值、受损拖尾、闪光和低理智动态纹理。护甲层随可见条数上移。
- 生命条按旧配置默认关闭，关闭时保留原版生命显示；理智条仍显示。
  创造模式仅显示理智条；旁观和隐藏 GUI 时不显示。
- `client.hud.PlayerScreenFilters`：三层低理智滤镜及四色受伤通知消费；
  缺少滤镜资源时跳过该层，不绘制缺失纹理。登录、登出、世界卸载及玩家实例更换时清理缓存。
- `registry.client.PlayerHudRegistration` 经既有客户端注册入口挂接，不重复订阅事件。
- 新增两个动画元数据文件，通过 IDEA 保留旧文件内容；38 张 PNG 已按用户授权使用 PowerShell 复制，
  每份 SHA-256 与旧项目原文件相同，新增 38 份、覆盖 0 份。

旧来源：`client/gui/hudlayers/LeftBarLayer.kt`、`NewHealthBarLayer.kt`、`StatusBarLayer.kt`、
`module/rationality/hud/*`、`module/shield/hud/*`、
`client/gui/hudlayers/screenfilter/LcDamageScreenFilterLayer.kt`、
`config/LibClientConfig.kt`、`LibServerConfig.kt`、
`init/LibAbsorptionShieldsRegistry.kt` 和旧生物／伤害事件实现。

## GUI 工具与通用 HUD 层（2026-10-21）

本轮以 ImaginaryCraft 的 Minecraft 1.21.1 源码为设计依据，目标仍为当前锁定的
Minecraft 26.1.2／NeoForge 26.1.2.100／Java 25。迁移的是浮点绘制能力和分层设计，
不保留已经失效的 GuiGraphics、RenderType、三维 GUI z 偏移或即时 BufferUploader 调用。

### 复用入口

| 入口                                                                                                                                                                                                                                                                                                        | 职责                                                      |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| [IGuiGraphicsExtractorExpand](../../src/main/java/org/unitego/lobecorp/api/client/gui/IGuiGraphicsExtractorExpand.java)                                                                                                                                                                                                                            | 浮点精灵、UV 裁切、平铺、九宫格、原生纹理、纯色及垂直渐变 |
| [HudLayerManager](../../src/main/java/org/unitego/lobecorp/client/hud/HudLayerManager.java)                                                                                                                                                                                                                 | 统一注册、会话跟踪、宿主创建、显隐与清理                    |
| [HudHost](../../src/main/java/org/unitego/lobecorp/client/hud/host/HudHost.java)                                                                                                                                                                                                                            | 组合持有 ModularUI，提供布局前准备与提取后绘制缝            |
| [PlayerStatusHudView](../../src/main/java/org/unitego/lobecorp/client/hud/status/PlayerStatusHudView.java)、[StatusBarComponent](../../src/main/java/org/unitego/lobecorp/client/hud/status/StatusBarComponent.java)                                                                                         | 生命、理智与四色护盾的数值刷新、槽位与普通 UIElement 绘制  |
| [StatusGauge](../../src/main/java/org/unitego/lobecorp/client/hud/status/bar/StatusGauge.java)                                                                                                                                                                                                              | 背景、浮点填充、损失拖尾与两段闪光                          |
| [ScreenFilter](../../src/main/java/org/unitego/lobecorp/client/hud/widget/ScreenFilter.java)                                                                                                                                                                                                                | 新滤镜共用的全屏纹理与透明度，缺图跳过                      |
| [EventHudState](../../src/main/java/org/unitego/lobecorp/client/hud/event/EventHudState.java)、[EventHud](../../src/main/java/org/unitego/lobecorp/client/hud/event/EventHud.java)                                                                                                                     | 包装归属与 `show` 顺序、容器布局及补位               |

GuiUtil 使用当前 AtlasIds.GUI、SpriteId、GuiMetadataSection 与 RenderPipeline。
九宫格识别当前版本的 stretchInner；GuiSpriteScaling 非封闭，默认分支按普通拉伸处理。
浮点坐标保留到顶点提取，矩阵、UV、颜色和裁剪冻结为 GuiElementRenderState；
延迟绘制不读取可变 HUD 状态，不操作全局着色器、混合或深度开关。自定义填充管线需采用 GUI 的无纹理位置／颜色顶点布局。
零上限和非正尺寸跳过填充，边界使用保守取整后再应用当前矩阵与裁剪。
旧接口中的 z 不映射成被忽略的参数；前后关系由 GuiLayer 注册顺序及当前 GUI 分层负责。

### 单实例与旧 HUD 设计

PlayerStatusHud、PlayerScreenFilters 均只有公开 INSTANCE 和私有构造器。
生命、理智、四种护盾及两个滤镜层的可变状态由各自实例持有，不再使用全局静态动画状态。
注册、护甲位移、受击通知、tick 和会话清理的既有调用点全部访问同一实例，没有增加事件监听或网络字段。

玩家 HUD 使用组合层按生命→理智→物理→精神→侵蚀→灵魂顺序向上堆叠，间距 2 GUI 像素。
锚点仍为屏幕中线左移 105、底部上移 30；创造模式下移 5，关闭新生命条时上移 10。
组合高度按实际间距计算，护甲层上移组合高度加 1。子层在不可见时仍刷新当前数值；
玩家实例、字体或屏幕尺寸变化后同步到子层，登录／退出／卸载继续使用已有清理链。

生命条保留 90×9、填充偏移 11／1 和 78×7；生命条图像整体右移 3，文本仍以组合锚点右对齐。
吸收生命显示金色附加数值，变化后先显示 2 tick 的独立闪光纹理；中毒、凋零、冰冻外观沿用当前 NeoForge
PlayerHeartTypeEvent 钩子，未调用当前版本私有的 HeartType.forPlayer。
理智与护盾条保留 93×11、填充偏移 14／2 和 78×7；低理智背景为 −1／−2、94×14。
零理智的 98×14 动态纹理替换普通条，不再叠画；当前 0～上限的理智语义及此前三分之一区间适配保持。

受伤滤镜恢复旧设计的 LinkedHashSet 叠加顺序、同类型再次命中复位和实时帧增量的平滑淡出，
不再用固定的客户端 tick 直接线性扣透明度；tick 只检查会话和配置失效。
暂停时不推进动画，隐藏 GUI 时不提取滤镜。两张缺失源滤镜继续跳过，未生成替代素材。
本轮不改变护盾结算、六项配置默认值、资源 ID、语言键、协议版本或护盾世界特效留空的约定。

### 本轮检查与备份

全部代码修改和 IDEA 格式处理完成后统一读取静态诊断；本轮未运行编译、构建、Data、测试、同步、重载或游戏。
首次诊断发现的非封闭类型 switch 及嵌套静态类型可空注解问题已在本轮范围修正；
补丁未匹配时先读回实际状态再按上下文处理，没有整文件覆盖。
五个既有 Java 文件修改前的文本快照位于 backups/hud-design-20261021/*.java.bak，未删除源码。
本轮统一读取 18 个 Java 与 3 个文档的 IDEA 诊断，0 错误；
保留三个公开复用入口未使用提示及载荷原有两个借用世界提示，修改记录仍有三处历史失效链接警告。
INSTANCE 的语义引用已核对，状态 HUD 为 5 处、滤镜入口为 7 处。
独立 Git 索引只收录本次 21 个文件，ClientRuntimeEvents 中并行的五行女皇特效代码保留在工作区，不纳入本次提交。
提交前 HEAD 备份为 `refs/backups/before-hud-design-20261021-89fv5zhg`，原索引与暂存差异另存于
`C:/Users/wang_/AppData/Local/Temp/lobecorp-hud-design-20261021-89fv5zhg`。独立提交记录见[修改记录](changes.md)。

## GUI 与 HUD 二次对照补齐（2026-10-21）

本次只读对照 GoldenBoughsLib 中的 GuiUtil、HUD 基类／组合层／状态条／滤镜、四个护盾包装类、
相关数字格式方法、GUI 注册事件及两个旧 GUI Mixin；核对范围共 21 份旧源码的适用部分。
当前仍锁定 Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25，全部写入通过 IDEA 完成。

| 对照项                              | 发现与处理                                                                                                                                                           |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| GuiUtil 精灵绘制                    | 补齐 TextureAtlasSprite 的直接浮点裁切入口（含颜色重载），以及九宫格／平铺的默认白色入口；已有完整绘制、UV、颜色与矩阵快照继续复用                                   |
| 非 Stretch 精灵裁切回退             | 旧实现对该重载直接拉伸精灵，原迁移再次分派元数据可能变为重复平铺／九宫格；现恢复直接精灵回退                                                                         |
| GuiUtil 管线矩形及渐变              | 补齐 fillRenderType 的 RenderPipeline／TextureSetup 提取式入口；反向 Y 坐标保持颜色与原端点对应，不颠倒渐变                                                          |
| HudLayer／CompositeHudLayer         | 补齐位置访问、requirePlayer 和尺寸变化钩子；恢复链式 addLayer、同层更新条件而不重复插入、显式移除及玩家变更立即传播                                                  |
| StatusBarLayer／HorizontalStatusBar | 补齐完整文字、最大值文字及绘字扩展点，手动闪光／拖尾控制、独立纹理层访问与替换；TextureLayer 保持不可变，使用 withPosition／withSize／withTexture 后显式设置回状态条 |
| 数值与低值拖尾                      | 按旧 HUD 恢复整数不带小数、非整数固定两位；吸收生命固定两位且带千位分隔，采用 HALF_UP 舍入与固定 Locale；插值数值 0～1 时清除拖尾                                    |
| ShieldBarLayer                      | 零护盾只保留文字，不绘条图；补充伤害类型读取，四个旧包装类继续等价映射为 ShieldBarLayer(type)                                                                        |
| 理智滤镜结束                        | 修正插值前丢失符号导致内部 Alpha 长期非零的差异：健康区间保留负偏移，阈值尾数按浮点精度结束，上限为零立即清空；可见效果未运行验证                                    |

### 等价实现与版本边界

- 旧 IHudLayer 对应 HudLayer；BasicHudLayer、CompositeHudLayer、StatusBarLayer 与 HorizontalStatusBar
  均已有当前实现，不重复创建同义类。旧公开可变集合改为只读视图加 addLayer／removeLayer，
  同一子层重复加入保留顺序并更新显示条件，避免额外绘制和重复推进动画。
- 旧 LeftBarLayer 对应 PlayerStatusHud.INSTANCE；生命、理智及四种护盾的顺序、尺寸、偏移、可见性、
  独立闪光与布局已核对。PlayerScreenFilters.INSTANCE 持有独立受伤／理智滤镜层，颜色、透明度、首次命中顺序、
  再次命中复位、帧增量淡出及会话清理链已有对应实现。
- 旧 z、RenderType 和调用方直接写 VertexConsumer 的重载不做旧版兼容壳；
  当前使用 GuiLayer 顺序、RenderPipeline、TextureSetup 和冻结的 GuiElementRenderState。
  fillRenderType 接收位置或位置／颜色布局的管线，不补充 UV、法线或其他该布局未提供的顶点属性；
  当前 BufferBuilder 对不存在的 COLOR 元素会跳过写入，已通过 IDEA 阅读该实现。
- 九宫格尺寸相等／单轴相等的旧专用分支由现有区域算法等价覆盖，继续遵守当前 stretchInner；
  不恢复旧右侧宽度混用或 x／y 对调等异常。位置访问使用 left／top 的明确语义。
- 组合总高继续按实际 2 像素间距计算，不恢复旧总高只累计 1 像素、绘制却移动 2 像素的不一致。
  旧 AbstractWidget 的装饰绘制由状态条／滤镜组件和层显示条件覆盖，不新增旧空旁白或无实际用途的交互逻辑。
- 旧 SAVING_INDICATOR 图层名不直接复制到 26.1.2，理智滤镜沿用当前可用的上层注册；
  新生命条开关、护甲位移和两个滤镜开关已有当前注册／分发实现，没有重复事件入口。
- 两个旧 GUI Mixin 分别服务物品独立 GUI 模型与枪械准星；枪械蓄力、斩味及物品模型系统继续不在本次范围。
  两张源项目缺失的精神／灵魂滤镜不冒充遗漏代码或重新制图，保持记录和缺图跳过。
- 不改变理智 0～上限、资源同步、护盾伤害结算、配置默认值、协议版本、资源 ID 或世界特效留空规则。

### 本次实际检查

补齐 8 个既有 Java 文件，新增复用 API 尚无当前调用时保留 IDE 未使用提示。
完成全部代码及修改行格式后，统一读取 18 个 Java 的 IDEA 诊断：0 错误，22 个公开复用入口提示及 2 个原有借用世界提示。
未运行编译、构建、Data、测试、同步、资源重载或游戏；以上为源码和静态诊断证据，不作为视觉或运行验收。
8 份修改前文本快照保留在 backups/hud-audit-20261021/*.java.bak，未删除文件。
本次通过独立 Git 索引仅提交补齐代码与三份同步文档，保留用户其他修改和原暂存；备份信息见下文。

提交前 HEAD 已备份为 `refs/backups/before-hud-audit-20261021-k26u66fp`，原 Git 索引与暂存差异快照位于
`C:/Users/wang_/AppData/Local/Temp/lobecorp-hud-audit-20261021-k26u66fp`。本轮未删除文件，旧项目保持只读。

## 配置迁移

迁移的是定义、键、默认值与对应行为，未读取、覆盖或迁移用户旧运行目录中的个人 TOML。

| 文件                        | 键                                        | 默认值 | 范围／用途                 |
|-----------------------------|-------------------------------------------|--------|----------------------------|
| lobecorp-hud-client.toml    | enable_new_health_bar                     | false  | 替换原版生命／吸收生命显示 |
| lobecorp-hud-client.toml    | enable_low_rationality_filter             | true   | 三层低理智滤镜             |
| lobecorp-hud-client.toml    | enable_lobotomy_corporation_damage_filter | true   | 受击滤镜                   |
| lobecorp-shield-server.toml | enable_multi_shield                       | false  | 四色护盾共存               |
| lobecorp-shield-server.toml | enable_shield_damage_immunity             | false  | 破盾时免除对应通道本次溢出 |
| lobecorp-shield-server.toml | shield_additional_value_per_level         | 10.0   | 0～1000，每级授予量        |

客户端配置不影响服务端结算。护盾 HUD 读取同步附件中的授予时容量，不读取客户端的 SERVER 配置。
本次不增加或变更理智自动恢复规则。

## 理智适配

当前理智保持 0～上限，未恢复旧项目的负理智玩法。高于等于 70% 使用旧高理智外观；
低于三分之一使用低理智外观，零值显示旧动态纹理。
最低三分之一区间从弱到强映射旧三层滤镜的完整交叉淡变；上限为零时停用比例滤镜，避免除零。
该映射是适配当前资源范围的实现选择，尚未做视觉验收。

## 护盾重写与伤害接入

`LcShieldState` 用四个固定 Pool 保存当前量和授予时容量（共八个浮点数）。
`lc_shields / LC_SHIELDS` 使用 Codec 持久化及 Neo 附件自动同步，死亡重生不复制，不新增护盾状态包。

`LcShieldUtil` 统一效果映射、授予、清理与扣除。四个效果注册为
`physics_absorption_shield`、`spirit_absorption_shield`、
`erosion_absorption_shield`、`soul_absorption_shield`。
首次施加和相同／更高等级重施按当前配置填满；低等级重施不重置高等级剩余量。
禁用共存时新效果移除其余三种护盾。移除／到期沿用唯一 MobEffectEvents 入口及取消判断；
服务端 tick 清理已经没有效果的残余附件，不根据等级重新生成耗尽量。

- 四色伤害先走既有防御／承伤系数；负系数恢复不消耗护盾。
- 物理走护甲与韧性，侵蚀保持跳过；共同保护、抗性及原有 Pre 处理后，
  先抵消生命恢复，再按护甲后的物理／侵蚀权重分配剩余正伤害，各自消耗对应护盾。
- 精神／灵魂在已接受的受击结束时消耗各自护盾，再提交对应资源变化。
- 无四色标记的普通伤害保留原结算，按旧项目规则依次经过当前持有的护盾；
  没有把普通伤害标记为物理。
- 破盾免伤只免除本次该通道的溢出，其他通道继续结算。移除效果、死亡、过期不额外播放世界特效。
- 未注册护盾球体、网格、粒子或自定义渲染器；世界特效实现留空。

生命通道共用减伤后的比例分摊以及无标记伤害被多个护盾依次吸收，均未做战斗或联机验收。

## 受伤滤镜同步

协议版本更新为 21，新增 `network.tc.damage.PlayerDamageFilterPayload`，包含维度、玩家 UUID、
四色通道位掩码；只发送给受击玩家本人。四个位按 LcDamageType 枚举顺序解释。
该独立瞬时事件可表达纯精神／灵魂命中，现有仅生命伤害的数字通知无法覆盖；
理智与护盾值仍使用原有附件同步，滤镜包不重复保存状态。
客户端校验玩家与维度，过滤绘制只消费服务端事件，不决定伤害。

## 资源清单与已知缺失

旧资产根：`module/GoldenBoughsLib/src/main/resources/assets/goldenboughs_lib`。
目标根：`src/main/resources/assets/lobecorp`，各相对路径保持不变。

| 相对目录                                 | PNG 数量 | 文本元数据 | 状态   |
|------------------------------------------|---------:|-----------:|--------|
| textures/gui/sprites/hud/bar/health      |       11 |          0 | 已迁移 |
| textures/gui/sprites/hud/bar/rationality |       10 |          2 | 已迁移 |
| textures/gui/sprites/hud/bar/shield      |       12 |          0 | 已迁移 |
| textures/gui/filter                      |        5 |          0 | 已迁移 |

共 38 张 PNG、2 份动画元数据。现存滤镜为 physics、erosion、rationality、rationality1、rationality2。
旧源码还引用 `textures/gui/filter/spirit.png` 与 `the_soul.png`，但这两张滤镜图在旧仓库中不存在；
同名粒子／属性图片不是屏幕滤镜，未冒充原图迁移。对应通道通知已接入，缺图层跳过。

IDEA MCP 的文本接口不能复制 PNG；用户于 2026-10-21 明确授权仅对上述 38 张 PNG 使用 PowerShell。
复制前确认数量、目标路径和目标是否存在，复制后逐份比较 SHA-256；旧项目未写入，未覆盖目标文件。

## 实际检查与恢复入口

- 已读取本次既有文件的 IDEA 错误／警告、相关类型、源码与 Git 差异；保留工作区原有修改。
- 最新已检查 19 个 Java 与 9 个文档／规则文件，未发现语法或类型错误；文档保留历史失效链接和表格样式提示。原文件的未检查转换、未使用成员、可选
  final 和借用世界提示保留；
  新增服务端工具／载荷也有借用世界提示，未关闭游戏拥有的世界。
- 自动审批最初拒绝八个新增文件的格式化，未执行该次操作。
  用户随后要求将格式纳入编辑规则，已同步 AGENTS.md 和 workflow.md；之后 IDEA 格式化成功。
  新文件按项目格式处理，既有代码只处理本次修改范围，关闭额外导入优化和成员重排。
- 新建本页的整页 Markdown 格式化最初被自动审批拒绝，该次未执行；
  用户随后明确允许 Markdown 格式化，本页按 IDEA 项目格式处理，其他既有文档仅处理本次修改范围。
- 未执行编译、构建、Data、测试、资源重载、同步或游戏运行。新增四个效果的中英文名称只写入 LangHandler 生成源，
  尚未运行 Data，未手写生成输出，不能报告运行资源或语言显示已验收。
- 未删除文件，旧项目保持只读；提交前 HEAD 已保留在 Git 引用 `refs/backups/before-player-hud-20261021-nm_x6kcu`。
  原 Git 索引、暂存差异及两个共用文件的原工作区差异快照保存在
  `C:/Users/wang_/AppData/Local/Temp/lobecorp-hud-migration-20261021-nm_x6kcu`。未覆盖用户源码或原暂存内容。
- 精神／灵魂受伤滤镜原图仍缺失，资源层跳过；未绘制替代图，也未恢复负理智或自动恢复玩法。
  后续运行、视觉、存档与联机检查仍未执行；本次使用独立 Git 索引提交，
  Lobecorp.java、LcPayloads.java 只提交 HUD 所需配置与协议接入，原有女皇注册和载荷入口重构保留在工作区。
