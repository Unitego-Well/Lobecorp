# 玩家 HUD、屏幕滤镜与四色护盾

## 当前：LDLib2 事件 HUD

使用LDLib2 26.1.2.42的 `ModularHudLayer` 和 `ModularUI`，统一组件 `EventHudComponent` 继承 `UIElement`，构造时指定 `TOP/LEFT/RIGHT`。顶部居中位于屏幕上方，左右从屏幕高度三分之一处开始，各自由column容器按加入顺序向下堆叠、间隔4 GUI像素；组件高度由内容决定，禁止收缩，移除自动补位。

注册签名为 `register(id, englishName, chineseName, componentFactory)`；名称保存在类型定义中，组件工厂每个活动UUID调用一次。同UUID同类型重复show返回已有实例，类型冲突拒绝。`show(type, uuid)` 显示、`hide(uuid)`移除、`clear()`清理活动UI；换世界清理实例并保留定义。深夜清扫注册 `MidnightCleanupHud::new`，固定LEFT，目前仅空容器，未接入自动事件同步、纹理或计时内容。HUD仍注册在伤害滤镜之上。

本轮IDEA定向编译与Data成功，生成目录无新增Git差异；未进游戏，布局、动态堆叠及缩放尚无实机视觉证据。下方早期GuiLayer注册方式为历史，已由此实现替代。

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
| [GuiUtil](../../src/main/java/org/unitego/lobecorp/util/client/gui/GuiUtil.java)                                                                                                                                                                                                                            | 浮点精灵、UV 裁切、平铺、九宫格、原生纹理、纯色及垂直渐变 |
| [HudLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/HudLayer.java)、[BasicHudLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/BasicHudLayer.java)                                                                                                                          | 玩家会话、字体、屏幕尺寸、位置与绘制入口                  |
| [CompositeHudLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/CompositeHudLayer.java)                                                                                                                                                                                                       | 有序子层、独立显示条件、统一初始化、上堆叠与尺寸计算      |
| [StatusBarLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/StatusBarLayer.java)、[HorizontalStatusBar](../../src/main/java/org/unitego/lobecorp/client/hud/widget/HorizontalStatusBar.java)                                                                                                 | 数据／文本与背景、浮点填充、损失拖尾、两段闪光分开维护    |
| [HealthBarLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/HealthBarLayer.java)、[RationalityBarLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/RationalityBarLayer.java)、[ShieldBarLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/ShieldBarLayer.java) | 独立生命、理智与按伤害类型复用的护盾状态条                |
| [ScreenFilter](../../src/main/java/org/unitego/lobecorp/client/hud/widget/ScreenFilter.java)、[ScreenFilterLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/ScreenFilterLayer.java)                                                                                                         | 可组合的全屏纹理和透明度，缺图跳过                        |
| [DamageScreenFilterLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/DamageScreenFilterLayer.java)、[RationalityScreenFilterLayer](../../src/main/java/org/unitego/lobecorp/client/hud/layer/RationalityScreenFilterLayer.java)                                                              | 首次命中顺序叠加／复位／逐帧淡出，以及三层理智交叉淡变    |

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
| textures/gui/sprites/hud_bar/health      |       11 |          0 | 已迁移 |
| textures/gui/sprites/hud_bar/rationality |       10 |          2 | 已迁移 |
| textures/gui/sprites/hud_bar/shield      |       12 |          0 | 已迁移 |
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
