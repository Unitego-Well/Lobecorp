# 玩家 HUD、屏幕滤镜与四色护盾

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
