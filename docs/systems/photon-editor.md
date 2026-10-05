# Photon 粒子编辑器

核对日期：2026-10-05。本文按当前源码和 IDEA 解析的锁定依赖说明编辑器能力；Minecraft 26.1.2、NeoForge 26.1.2.100、Photon
26.1.2.2、LDLib2 26.1.2.39、kilagraph 26.1.0.14。版本来源：[版本目录](../../gradle/libs.versions.toml)。

包迁移方案 1 已执行，Java 链接按当前源码核对。实际迁移与验证结果见[修改记录](changes.md)。

## 范围和职责

Photon 原有编辑器负责项目、对象层级、发射器、材质、资源、时间轴、动画属性和操作历史。Lobecorp 使用现有 LDLib2
Configurator、Dialog、菜单、事件及 Photon 编辑器接口扩展交互，不另建 UI 风格或第二套粒子引擎。

- [PhotonEditorTools](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonEditorTools.java)
  组织工具、预览覆盖、快捷键和控件开关。
- [FXEditorMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/FXEditorMixin.java)
  安装工具，并在关闭、切换项目时清理预览状态。
- [LcPhotonEditorSettings](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonEditorSettings.java)
  注册设置；配置、客户端实现、Mixin 和工具分别放在对应职责域。
- 游戏中的效果播放、模型 locator 跟随和渲染边界见[粒子与渲染](particle-rendering.md)。编辑器预览不承担服务端伤害结算。

## 菜单、设置和开关

原有“文件”“视图”后追加“文档”“设置”“工具”。“设置”直接打开原设置面板，文件菜单中的原设置入口保留。“文档”使用可换行文本和滚动容器，并显示当前快捷键绑定；“工具”只显示已开启的工具入口。

[PhotonEditorSettings](../../src/main/java/org/unitego/lobecorp/config/photon/PhotonEditorSettings.java)集中维护
features、keys、presets 三个映射。设置路径为 Lobecorp，未保存的功能项按开启处理。分类页与快捷键页共享同一配置，不各自保存重复状态。

四个功能分类直接列出配置行，不再套“功能开关”折叠组；左侧设置导航树和内部业务子组保持原结构。
扩展配置行的悬浮说明由 [PhotonEditorTooltipUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorTooltipUtil.java)
集中管理，沿用 LDLib2 原生提示图标、翻译和悬浮样式。现有 Configurator 构造注入只为登记的扩展键安装 ADDED 监听，
在控件加入时设置提示，避免原配置构建器之后清空提示；不额外遍历控件树。原生标签的临时预览/批量字段单独通过 setTips 补充说明。

| 设置分类 | 功能标识                                                                                    | 用途                                               |
|----------|---------------------------------------------------------------------------------------------|----------------------------------------------------|
| 曲线     | precise、time_scale、batch_move、fit、tangents                                              | 精确编辑、时间缩放、批量平移、适配视图、切线预设   |
| 曲线     | curve_pan、axis_lock                                                                        | 中键移动视图、Shift 约束曲线拖动                   |
| 预览     | range_preview、parameters、solo、seed、step、diagnostics                                    | 区间预览、临时参数、独显、种子、逐刻步进、渲染诊断 |
| 粒子     | batch_properties                                                                            | 批量修改已勾选的属性                               |
| 粒子     | rotation_cycle、color_cycle、size_cycle、velocity_cycle、force_cycle、uv_cycle、time_offset | 循环采样及时间偏移                                 |
| 粒子     | depth_sort、control                                                                         | 视深排序控件、激活轨道扩展                         |
| 资源     | presets、system_clipboard、rename_dialog                                                    | 模块预设、系统剪贴板、重命名弹窗                   |
| 快捷键   | shortcuts                                                                                   | 扩展快捷键总开关和逐项绑定                         |

“功能默认开启”指允许使用扩展，不表示所有发射器模块或循环自动开启。各生命周期模块的循环复选框默认关闭，周期默认 20
tick；仍需开启原模块和该模块的循环。关闭编辑器功能开关不能当作批量清除已保存特效参数或游戏运行时配置。

## 发射器发射器

原对象添加菜单增加“发射器发射器”，支持项目发射器模板与已保存的完整 FX 资源，自身不创建粒子。
监视面板沿用原 Configurator、搜索/拖放、枚举和向量控件；设置的粒子与渲染分类中新增默认开启的
`emitter_spawner` 控件开关。保存、操作历史和剪贴板复用原对象类型序列化。
外层同名折叠组移除，配置行直接加入监视面板；形状、实例参数等内部子组保留。
项目模板行提供原生“移除”按钮，只清除引用，不删除模板对象。新增“一次性发射”模式每次启动只自动触发一次，
循环不重复、重启可再次触发；原“每周期一次”模式和保存标识保持原语义。
调度、实例参数、暂停/重启/结束行为及客户端 API 见[专页](photon-emitter-spawner.md)。

## 曲线视图与编辑

这里的曲线视图是时间轴中动画轨道展开后的曲线区域。颜色属性不走数值曲线中键平移分支。主要实现为[AnimationTrackEditorMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/AnimationTrackEditorMixin.java)
和[PhotonCurveEditUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonCurveEditUtil.java)。

- 中键拖动移动视图：横向改变时间轴滚动，纵向改变当前属性的显示范围偏移。它不修改关键帧值；适配视图会重置相关显示偏移。
- Shift + 左键拖关键帧或曲线选择，超过拖动阈值后按主要方向锁定水平或垂直；修改的是曲线数据。松开 Shift 恢复普通拖动。单纯点击仍保留选择语义。
- Alt + 左键拖动可在结束时复制所拖关键帧；不叠加 Ctrl/Shift，并检查轨道锁定和目标时间冲突。
- 精确编辑可修改所选关键帧的时间和值；批量移动加时间/值增量；时间缩放围绕选中关键帧的最早时间缩放，并同步切线。
- 切线菜单包含线性、缓入、缓出、两端缓动、阶梯、平坦、对齐、断开和重置入口。实际操作受属性类型和表达式片段限制。
- 修改检查有限值、非负时间、相邻时间间隔和锁定状态；失败恢复快照，通过现有操作历史提交可撤销变更。

[FXTimelineViewMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/FXTimelineViewMixin.java)
只提供时间适配、平移和复制粘贴连接，不替换原时间轴全部行为。

## 快捷键

默认绑定如下；所有扩展快捷键在文本输入框、TextArea 和 Dialog 中让出输入。大多数动作要求处于时间轴范围，独显和取消按各自作用域处理。

| 按键                  | 动作                          |
|-----------------------|-------------------------------|
| F / Home              | 适配选中 / 全部曲线和时间范围 |
| 左 / 右方向键         | 后退 / 前进 1 tick            |
| Shift + 左 / 右方向键 | 后退 / 前进 10 tick           |
| Ctrl + D              | 复制选中内容                  |
| G / S                 | 平移编辑 / 时间缩放编辑       |
| I / O                 | 设置预览区间起点 / 终点       |
| L                     | 切换预览区间循环              |
| Shift + H             | 切换选中对象独显              |
| Escape                | 取消当前扩展拖动              |

[PhotonEditorShortcutUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorShortcutUtil.java)
校验组合键。空绑定可禁用单项；拒绝重复修饰键、冲突绑定和保留的原生保存、撤销/重做、复制/粘贴/剪切、播放、删除等组合。快捷键设置不取代文本编辑控件的原按键逻辑。

## 生命周期模块的循环

[PhotonLifetimeCycleUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonLifetimeCycleUtil.java)
按粒子已经存活的时间采样循环；实际寿命与循环周期分开。有限寿命和无限寿命均可使用，循环不会自行延长寿命或改变发射次数。

相位为 fraction ((age + timeOffset) / cycleTicks)，age 和 timeOffset 均以 tick 计，周期至少 1 tick。负时间偏移也按数学取模回到周期内。例如周期
20、偏移 -1 表示起始采样上一周期末段，偏移 3 表示提前 3 tick；不是给坐标、角度、颜色或大小增加数值。

| 模块           | 循环采样和偏移                                                               |
|----------------|------------------------------------------------------------------------------|
| 生命周期内旋转 | 翻滚/俯仰/偏航沿用原曲线采样；跨周期累积整周期角度差，避免自旋每次回到起始角 |
| 生命周期内颜色 | 按循环相位采样原颜色功能                                                     |
| 生命周期内大小 | 按各分量循环相位采样大小                                                     |
| 生命周期内速度 | 按原速度模块的坐标与运算流程替换时间采样                                     |
| 生命周期内受力 | 按原受力流程替换时间采样                                                     |
| UV 动画        | 对支持的粒子/光束/拖尾来源采样循环 UV 时间                                   |

多分量时间偏移复用 NumberFunction3 的“全部合一/分离轴”编辑样式。旋转与轴名映射须按 yaw/pitch/roll 字段核对，不能把 UI
分量顺序直接当成世界空间旋转顺序。UV 为单一时间偏移。原模块关闭或循环未开启时走原采样路径。

旋转实现见[RotationOverLifetimeRuntimeMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/RotationOverLifetimeRuntimeMixin.java)
；UV
入口见[UVAnimationSettingMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/UVAnimationSettingMixin.java)
。生命周期为 0 时仍需按 Photon 对该粒子类型的寿命语义处理；本扩展解决循环采样，不把所有类型的 0 自动解释成无限寿命。

## 激活轨道：开始、结束和父子关系

扩展目标是 ActivatorTrack（激活轨道）。选项接入轨道监视面板；既有 ControlTrack 仍保留控制时间与 ticking 的原行为。历史命名
control 是功能开关标识，不代表把这套扩展移到 ControlTrack。

[PhotonActivatorOptions](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonActivatorOptions.java)
默认：控制开始、开始时重启、控制结束、原结束行为、重启时清理。开始控制和结束控制可独立关闭。

| 选项             | 结果                                                         |
|------------------|--------------------------------------------------------------|
| 开始：重启       | 重置运行/发射计时；“清理”默认开启，可关闭以保留已有粒子      |
| 开始：继续       | 继续当前运行状态，不每次从头播放                             |
| 结束：原行为     | 按 Photon 原激活轨道逻辑处理                                 |
| 结束：仅停止生成 | 保留对象活动和已有粒子，只停止新发射，让有限寿命粒子自然结束 |
| 结束：清理       | 执行清理结束                                                 |

仅停止生成不会给无限寿命粒子自动补上淡出或消失时间；需要之后显式清理或另行安排结束表现。循环模块也不自带独立淡出包络。

[PhotonActivatorTimelineUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonActivatorTimelineUtil.java)
合并同对象的多条未静音激活轨道，按边界执行行为。具有自身激活轨道的子对象可覆盖父对象的激活和继承停止发射状态。父级手动隐藏仍遮住子对象；显式重启/清理子树也不能被理解为“子对象对父级一切操作免疫”。ControlTrack
若存在，仍参与最终 ticking 判定。

轨道保存字段位于 lobecorpActivation，含
startControl、startBehavior、endControl、endBehavior、clearOnRestart；缺失或非法枚举回退默认值。复制轨道、序列化和恢复需要一起保留这些参数。

## 系统剪贴板与资源编辑

[PhotonSystemClipboardUtil](../../src/main/java/org/unitego/lobecorp/util/photon/clipboard/PhotonSystemClipboardUtil.java)
将编辑器数据写成文本：
协议前缀为 LOBECORP_PHOTON:1 加换行，后面是包含 kind 和 data 的 SNBT。数据按值复制，不保留源编辑器对象。文本上限 4 × 1024 ×
1024 字符，条目上限 4096；对象/轨道恢复还检查递归深度、类型、有限值、锁定和引用合法性。

| 内容类型            | 处理入口和边界                                                        |
|---------------------|-----------------------------------------------------------------------|
| keys、tracks、clips | 关键帧、轨道、片段工具；检查目标类型/轴、时间冲突、对象引用和撤销快照 |
| objects             | 层级对象子树；生成新对象 ID，映射内部引用，并处理名称冲突             |
| resource            | 资源配置；只允许兼容资源容器接收                                      |
| module              | 发射器模块快照；要求模块类型匹配                                      |
| animation           | 曲线、渐变、表达式片段等子数据，按目标类型恢复                        |
| 普通文本            | TextField/TextArea 原文本编辑流程读取，不解析为轨道或资源             |

启用 system_clipboard
时，[TextFieldMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/TextFieldMixin.java)
与[TextAreaMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/TextAreaMixin.java)仅在 Photon
编辑器读取 Minecraft keyboardHandler 的最新系统剪贴板文本，修正 LDLib2 .39 原输入入口只读内部缓存的问题。复制、文本过滤、选区和撤销沿用原控件；关闭开关或离开
Photon 编辑器则走原入口。

原 AssetBrowser 文件/目录操作仍使用原 ClipboardAssets；跨编辑器资源配置协议与操作系统文件列表不是同一种数据。普通
JSON、路径文本或外部图片不会自动转换为编辑器对象。原层级“复制”即时复制行为保留，系统复制入口另行处理。非法协议或不兼容类型在修改前拒绝。

资源重命名使用现有 StringEditorDialog 小窗，按原字符限制和唯一性检查；层级、轨道及 GPU
数据名称的处理保留各自的历史通知和锁定约束。实现见[PhotonEditorRenameUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorRenameUtil.java)
。不直接改写生成资源或二进制纹理。

## 预览参数、批量属性和预设

工具中的参数面板针对选中对象的当前预览实例：普通粒子可改开始大小 XYZ、开始速度、开始颜色；BeamEmitter
可改光束长度和宽度。长度覆盖由光束运行时访问接口应用，修改后从预览起点模拟回当前时间，不修改共享 FX 定义。关闭参数面板、重置或切换项目清除覆盖。

区间预览使用本地起点/终点和循环开关；独显保留选中对象相关的父子链；逐刻步进和种子调整复用场景粒子管理器。固定种子便于对比预览，但不代表随机效果在不同客户端天然一致。

批量属性只写勾选项，包括速度、统一大小、颜色、Order、材质和循环配置；部分项以第一个选中发射器为来源。模块预设支持
rotation、color、size、velocity、force、uv、renderer、emission、shape，可复制模块或保存到设置的
presets。预设/批量操作会改变特效配置，区别于临时运行时预览覆盖。

渲染诊断面板展示层、Order、顶点排序、材质深度测试/写入、混合和纹理是否存在，并可定位对象；它不会自动修复材质配置。

## 性能、清理和已知边界

当前控件开关同步在构造时登记一次，之后由 Configurator 的 ADDED/REMOVED 事件登记/注销，不再每 tick 遍历整棵编辑器控件树。弱键映射不持有控件；每
tick 仍检查已登记控件的设置和父级模块，显示状态有变化才调用 setDisplay。LDLib2 .39
对嵌套子树递归分发事件，移除时父链仍可用，跨编辑器移动可注销旧登记并加入新登记。

噪声预览已经采用采样像素缓存和一个 GUI 渲染状态提交；详情及仍存在的 NBT
分配、顶点生成开销见[粒子与渲染](particle-rendering.md)。这不是降低交互刷新频率或限制粒子数量的方案。

项目关闭/替换会移除运行时预览绑定、参数覆盖、独显和区间循环状态。新增临时功能须接入同一清理链，不能把旧运行时引用保存在设置中。

尚未完成游戏内验收：系统文本/对象/资源跨窗口粘贴，全部快捷键焦点组合，嵌套控件跨编辑器移动，以及噪声场景的帧率/分配对比。IDE
诊断、编译和 Data 结果不证明这些交互已经通过实际运行测试。

## 修改前核对和维护

1. 核对 Photon、LDLib2 锁定版本、Mixin 目标方法及原事件/撤销语义；不要套用其他版本的 UI 示例。
2. 区分功能可见性、模块 enable、循环 enable 与运行时实际行为；新增开关需进入对应分类和默认值策略。
3. 修改剪贴板时同时检查复制入口、VALIDATE_COMMAND/EXECUTE_COMMAND、目标兼容性、文本焦点、反序列化和一次撤销边界。
4. 修改激活或循环时核对父子覆盖、重启清理、原坐标系、有限/无限寿命、存档及复制字段。
5. 新增参数覆盖要核对共享定义与实例、重播和关闭清理；新增 UI 复用原控件/样式。
6. 保持 [Mixin 配置](../../src/main/resources/META-INF/lobecorp.mixins.json)
   和[翻译集中入口](../../src/main/java/org/unitego/lobecorp/util/TranslationKeys.java)一致；新翻译由 Data 生成，不手写生成输出。
7. 系统行为、配置、协议、清理或路径变化后同步本文，在[变更记录](changes.md)
   记录日期、影响和实际验证。依赖桥接细节另见[研究记录](../project-reference/09-particle-effects-research.md)。
