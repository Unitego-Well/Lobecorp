# Photon 粒子编辑器

核对日期：2026-10-11。本文按当前源码和 IDEA 解析的锁定依赖说明编辑器能力；Minecraft 26.1.2、NeoForge 26.1.2.100、Photon
26.1.2.3、LDLib2 26.1.2.42、KilaGraph 26.1.0.15。版本来源：[版本目录](../../gradle/libs.versions.toml)，升级变化与检查见[依赖升级](photon-dependency-upgrade.md)。

包迁移方案 1 已执行，Java 链接按当前源码核对。实际迁移与验证结果见[修改记录](changes.md)。

新版原生时间轴传输栏增加步进和跳转末尾；模型时间轴直接沿用这些组件，将播放状态、步进与末尾跳转接到模型片段时钟，保留小数 tick。模型项目沿用原生动作标识、默认键位和用户重绑定，将四个播放动作路由到实际模型时间轴，不使用已移除的父类 FX 时间轴。几何与 UV 框选、取消等动作在原生 Keymap 注册相应作用域，优先于通用场景动作，未匹配按键仍走原生命令。

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
- 独立“模型特效项目”从文件菜单新建／打开，提供轻量模型、UV 与动画编辑，默认 1×1×1 单纹理白模；发射器“编辑模型”同样打开独立标签。左侧为模型层级／UV／模型资源／动画列表，右侧保留原生检视／历史，中央固定原生 `SceneView`，底部保留原资源目录与模型时间轴；复用原生窗口、面板拖动停靠、最大化、浮动与布局保存。左侧只管理当前模型的基础材质槽与贴图引用，底部仍为共享材质／网格库。加载项目同时识别模型工程、模型定义和 OBJ；文件导出沿用原生定义／FXPack／管理 FXPack 菜单。入口与资产约定见 [Photon 模型特效](photon-model-effects.md)，保持原 OBJ 粒子支持。

## MCP 自动化入口

[Photon 特效 MCP](photon-effect-mcp.md)提供能力与工程状态读取、新建、批量编辑、预览、保存、导出及撤销／重做。入口复用原生工程、UUID、时间轴、模型会话、历史和文件格式，实际操作在客户端线程执行；粒子 FX 编辑前先暂停并重新读取 revision。MCP common 只提供通用命令注册，特效业务由 Lobecorp 客户端后端持有，普通游戏表现仍由原运行时管理。

## 菜单、设置和开关

“视图大小”在独立窗口中沿原生 GUI 倍率流程调整，不再重新应用当前标签的窗口宿主偏好；再次选择已保存的倍率也会按当前实际窗口应用。普通设置面板 Apply 仍保留显式宿主选择。标签偏好与共享窗口不一致的根因、补丁范围及升级复核见[LD-05](photon-ldlib2-fixes.md#ld-05-独立窗口修改视图大小退回游戏窗口)，实际交互尚未客户端验收。

特效编辑器在原有“文件”“视图”后追加“文档”“设置”“工具”。“设置”直接打开原设置面板，文件菜单中的原设置入口保留。“文档”使用可换行文本和滚动容器，并显示当前快捷键绑定；“工具”只显示已开启的工具入口。独立模型编辑器同样提供原生文件／视图及文档／设置入口：文件中新建特效或模型特效项目，视图中的“面板”重新显示各模型工作区、场景与原资源目录。模型文档说明层级、UV、动画、模型资源、面板和导出操作；特效专用工具仍由 FX 编辑器安装。

模型外壳 `PhotonModelProjectEditor` 受限复用 `FXEditor`，通过 `IPhotonModelEditorBridge` 调用 `Editor` 基类加载模型项目；跨类型项目由 `EditorMixin` 路由到对应标签，`FXEditorMixin` 对特效工具及仅适用于 `FXProject` 的预览／动画绑定设置门禁。中央场景以真实 `FXRuntime` 渲染复制的模型发射器，选择覆盖与手柄留在 `SceneEditor` 的 Preview 中，跟随发射器世界变换并以本地 Gizmo 坐标编辑，禁止挂入只接收 `IFXObject` 的 `FXRuntime`；原生 `particleManager` 是唯一预览时钟，小数定位保存残量，原生 S 定位与 reset 清零残量。编辑与保存不改变来源发射器，只有“应用到发射器”写回并进入来源历史。模型材质槽进入同一会话历史，PNG 只保存共享资源包引用，不打包文件，撤销或删槽不删除共享资源；生命周期、初始化失败清理及翻译参数限制见 [Photon 模型特效](photon-model-effects.md#维护与实际检查)。

旧模型布局缺少 `editor.scene` 槽时仅本次跳过布局恢复，随后还原原设置；正常自定义布局照常恢复。时间框按 `session.time` 刷新，但焦点输入与该字段的数值拖动期间不覆盖。其父容器以 `TIME_INPUT_MIN_WIDTH=96` GUI 像素保留最小宽度，`TextField` 宽度为 100%；底部与右侧检视共用该入口，不在通用容器加 `flex(1)`，避免纵向检视占满剩余高度。输入区收缩为 4×14、内容宽度为零的实际根因见模型文档的维护说明，修后外观不以静态检查替代客户端复核。应用门禁检查来源运行时按 UUID 登记的对象身份，兼容仍登记的嵌套发射器，并拒绝删除发射器／父节点后或被同 UUID 其他实例替换的来源。普通创建失败先清理并通知，再返回确认回调完成关闭；不能把局部修复或静态检查当作全部 UI 已验收，实际检查仍见 [修改记录](changes.md)。

零刻暂停预览另需在原生 emit 后迁移待加入的粒子：首次初始化在 `play` 后处理，Stop 归零在 `simulateTo` TAIL 处理，仅在已初始化、零刻、正式粒子数为零且运行时已 emit 时执行一次迁移并还原时间零；reset 钩子不执行迁移。`particlesToAdd` 未进入渲染组导致表面缺失的实际根因与维护边界见 [Photon 模型特效](photon-model-effects.md#维护与实际检查)，客户端验收结果仍由修改记录区分。初始化须在 `play` 前通过 `DummyWorld.ClientSupport.asClientWorld` 为尚未绑定 level 的专用 `ParticleManager` 设置客户端世界 wrapper，提前建立独立引擎；否则 `getParticleAmount()` 返回的 `pendingAdds` 计数会掩盖实例尚未进入渲染队列的问题。此顺序与已有零刻迁移／TAIL／reset 规则同时保留。

[PhotonEditorSettings](../../src/main/java/org/unitego/lobecorp/config/photon/PhotonEditorSettings.java)集中维护
features、旧 keys、presets 映射和 curveEasingPreset。功能设置路径为 Lobecorp，未保存的功能项按开启处理；旧设置缺少缓动预设时采用线性。逐项快捷键统一进入原生“设置 → Keymap”；旧 keys 仅用于一次性迁移，通过 bindIfUnset 保留已有原生自定义绑定，成功迁移后移除对应旧项，避免重置键位后再次恢复旧绑定。

四个功能分类直接列出配置行，不再套“功能开关”折叠组；左侧设置导航树和内部业务子组保持原结构。
扩展配置行的悬浮说明由 [PhotonEditorTooltipUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorTooltipUtil.java)
集中管理，沿用 LDLib2 原生提示图标、翻译和悬浮样式。现有 Configurator 构造注入只为登记的扩展键安装 ADDED 监听，
在控件加入时设置提示，避免原配置构建器之后清空提示；不额外遍历控件树。临时预览、批量字段和快捷键也通过同一 tips 入口补充说明，配置项只在原生放大镜上显示帮助，保留输入校验提示，不给行或整组重复挂说明。

长提示在已登记的扩展配置行、本项目动画 UI、设置、预设、列表操作和参数工具内，复用原版 Tooltip 按 170 像素自动分行；通过控件本地 HOVER_TOOLTIPS 交给原生 HoverTooltips 绘制，保留提示图标、翻译和皮肤。动态动画动作提示在悬浮时读取当前消息，消息改变才更换 Tooltip，语言身份变化由原版缓存重建分行。是否已经包装按控件自己的 WrappedTooltip 监听器身份判断，重复注册只替换消息来源；不能因存在其他 HOVER_TOOLTIPS 监听器就跳过换行，也不保存全局控件引用。该入口不修改全局 Tooltip 或未登记的原生配置行；新增提示须沿同一入口，不能每次 ADDED 重复安装监听或把字符数当成像素宽度。帮助正文说明操作和限制，不混入修复经过或实现验收描述。
预分行后提交 FormattedCharSequence 的路径可能跳过第三方 GatherComponents 对原 Component 文本的处理；仅本项目包装提示使用该路径，其他提示仍沿原入口。第三方兼容、屏幕边界和实际悬浮布局尚未游戏内验收，不能把静态检查或 Data 成功当作这类兼容性证明。

| 设置分类 | 功能标识                                                                                    | 用途                                               |
|----------|---------------------------------------------------------------------------------------------|----------------------------------------------------|
| 曲线     | precise、time_scale、batch_move、fit、tangents                                              | 精确编辑、时间缩放、批量平移、适配视图、切线预设   |
| 曲线     | curve_pan、axis_lock                                                                        | 中键移动视图、Shift 约束曲线拖动                   |
| 曲线     | scale_curve_link、curve_easing、batch_curve_easing                                           | 缩放三轴点与手柄联动、新增及批量缓动选择           |
| 预览     | range_preview、parameters、solo、seed、step、diagnostics                                    | 区间预览、临时参数、独显、种子、逐刻步进、渲染诊断 |
| 预览     | hover_style_delta                                                                          | 悬浮时保留共同祖先状态，仅刷新变化分支             |
| 预览     | zero_time_emission                                                                         | 编辑器场景零时刻爆发初始化，不推进粒子年龄         |
| 粒子     | batch_properties                                                                            | 批量修改已勾选的属性                               |
| 粒子     | rotation_cycle、color_cycle、size_cycle、velocity_cycle、force_cycle、uv_cycle、time_offset | 循环采样及时间偏移                                 |
| 粒子     | depth_sort、control                                                                         | 视深排序控件、激活轨道扩展                         |
| 粒子     | timeline_event_traversal                                                                   | 高速播放经过的离散时间轴事件补处理                 |
| 粒子     | opaque_occlusion                                                                           | 不透明遮挡控件，仅管理 UI 可见性                   |
| 资源     | presets、system_clipboard、rename_dialog                                                    | 模块预设、系统剪贴板、重命名弹窗                   |
| 资源     | compact_window_titles、title_path_conflicts、title_path_tooltip                              | 文件名标题、同名路径区分、完整路径悬浮             |
| 资源     | notifications                                                                               | 保存与导出通知，最多三条竖向排列                   |
| 快捷键   | shortcuts                                                                                   | Keymap 页的扩展快捷键总开关；逐项绑定使用原生动作表                         |

“功能默认开启”指允许使用扩展，不表示所有发射器模块或循环自动开启。各生命周期模块的循环复选框默认关闭，周期默认 20
tick；仍需开启原模块和该模块的循环。关闭编辑器功能开关不能当作批量清除已保存特效参数或游戏运行时配置。

不透明遮挡控件复用原渲染器 ToggleGroup 和阈值数字字段；发射器开关默认关闭、阈值默认 0.99，均有原生悬浮提示。设置中的 opaque_occlusion 只隐藏控件，已保存 FX 的运行行为仍由发射器配置决定；支持材质、使用步骤和渲染边界见[不透明像素遮挡](particle-rendering.md#photon-不透明像素遮挡扩展)。

两类原生场景左下角共用相机朝向 XYZ 提示；模型工作区新增平移吸附、像素 UV 网格和预览方向阴影，几何操作移入视图，导入导出归入文件菜单。模型几何／历史和特效轨道仍独立处理，具体规则见 [模型视图与编辑维护](photon-model-effects.md#模型视图与编辑维护)。

## 窗口标题

Photon 的特效项目窗口标签默认保留原项目类型，只显示文件名与完整后缀；独立模型项目显示“[模型特效项目] - 模型名称”，从工程文件名移除完整 `.mfxproj` 后缀。相同编辑器窗口内，原项目类型与文件名相同的 Photon 标签改为完整路径；Windows 文件名比较忽略大小写。关闭同名标签或另存为后，随原生每 20 tick 的标题更新重新判定，最多约一秒。
悬浮标签显示当前文件的绝对路径，路径使用 literal Component，不作为翻译键。未保存的特效项目补充“未命名特效”，模型项目使用“未命名模型”；空编辑器保留原标题；仅装饰 EditorWindow 中的 Photon 特效和模型标签，不改变特效 Editor#getTitle、保存确认弹窗或文件引用。模型编辑器自身标题使用工程名称，完整路径提示和重名扫描复用同一工具，不另建标题系统。
三个开关位于“设置 → Lobecorp → 资源与剪切板”，默认开启，可以分别恢复原完整路径显示、关闭同名检测或隐藏路径提示。

## 命名动画

打开特效项目后，在“场景”右侧增加原生“命名动画”标签；首次选择才初始化工作区及控制器。
“工具 → 命名动画”定位同一标签，关闭后可重新打开，手动浮动时停靠回来；默认保留场景选中状态。
设置开关为粒子与渲染分类中的 `named_animations`，默认开启；首次加载关闭该开关的项目不添加标签。
左侧目标直接使用原特效层级，旁边的动画列表使用原数组行、拖动条、只读 Label 与加减按钮；选中同步 UUID，重复点击保留状态。
右侧命名配置继续使用四个原生标签；对象与轨道选择显示原完整检视，仅锁定 FXObject.name。
未录制时修改基础参数；开启原 TL 录制后同时修改基础值与当前命名动画关键帧，沿用原场景历史。
中央共用场景、渲染器、相机和粒子管理器；底部只保留实际 owner.timelineView，在原时间轴与独立命名模型间切换上下文。
八种轨道、片段、曲线、标记、录制、播放设置和工具直接复用原 TL；各上下文保存游标、缩放、滚动、展开项、选择及预览范围。
切换结束录制与拖拽，普通编辑保留有效引用，撤销仅清理失效引用；恢复原场景时间和播放状态时避免重复发射。
命名播放持有完整原生 Timeline，通过精确 double 时间作用域和 AnimatedProperty.apply 多态采样进入现有混合器；
保留独立播放、并行、优先级、覆盖/相加/相乘、绑定、跨周期旋转及 HOLD/RELEASE。tick 不补做 frame，后处理只在实际渲染帧提交。
制作范围取完整内容和事件标记；自动时长跟随范围，固定时长重定时整套内容。运行中改时长保持进度和循环次数。
新动画遵循原模块状态，可选择自动启用；示例显式启用以保留表现。定义 version=5，兼容 1～4；旧 version=1、2 保留固定时长及自动模块启用，第三版及以后沿用原生缺省规则。
原时间轴与命名定义分别保存，原轨道 Codec、复制引用映射、项目/.fx/FXPack 携带一致数据，剪贴板协议不变。
被动 UI 使用可空运行态查询并在身份改变时重新绑定；结束后不自动复活，主动播放或重置才重启。
底部共享 TL 控制制作预览；右侧运行动画测试组按目标及宿主状态显示“继续／暂停”，其后为“重启”“停止”，并保留临时时长，中央重复播放栏已移除。
取消制作选择后的测试时间轴说明按面板宽度原生换行并自适应高度；动画列表复制弹窗从原名预填唯一名称，重名时追加序号，仍可在确认前编辑。
切换动画或离开命名模式前，先结束录制／片段拖动并提交当前测试或命名 TL 投影，再保存游标与视图；选择改变前完成旧定义提交，新建、复制和删除也经 editSelection 进入同一 prepareSwitch 边界，内容未变化不新增历史。普通载入与撤销恢复不回存旧投影，避免恢复结果被覆盖；空选测试时间轴切换丢失的用户反馈与实际验收边界见[命名动画维护记录](photon-named-animations.md#已修复问题与维护要点)。
命名模式隐藏场景信息中的播放行与时间输入，保留种子/性能；运行状态被动刷新，游标只读，已有实例继续时保留原预览模式。
录制期间冻结当前姿态，保留所有权；序列化临时还原基础值后恢复自身贡献，不把冻结或用户编辑误记为动画变更。
关闭或切换项目恢复共用场景、清理上下文与监听，保护动画模式中的对象结构；合法原检视基础编辑与配置复制粘贴可用。
临时参数字段刷新、项目模板复制基础变换和跨周期 MARKER 顺序已源码确认有问题，尚未修复；根因、防回归、同值覆盖判断限制及待验收项见[命名动画专页](photon-named-animations.md)。

## 独立测试菜单

“工具 → 测试”提供全部测试、时钟与参数、运行槽检查（原入口）、多实例与触发、动画运行回归、共享时间轴与原生一致性、编辑器配置以及不透明遮挡与原生管线。
[Lobecorp 设置](../../src/main/java/org/unitego/lobecorp/config/photon/PhotonEditorSettings.java)的预览分类新增 tests（独立测试入口），默认开启；关闭后隐藏该菜单。
原命名动画表单中的运行槽检查和魔法阵示例按钮均已移除；测试入口不要求创建动画工作区或修改当前特效。
[PhotonEditorTestMenu](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonEditorTestMenu.java)
调用各检查自己的模型、FX 和控件，不借用当前编辑实例，不改变当前播放或游标；结果通过原生通知显示，失败堆栈写入日志。
菜单与完整加载回归统一使用 [PhotonAnimationChecks](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationChecks.java) 目录；菜单逐项报告，调试入口保持失败即中断。
部分检查需要模组已加载；独立的 PhotonAnimationRegressionTest 位于 src/test/java，通过 JUnit 执行不依赖该环境的时钟、参数和迁移检查，原 main 已移除。
Photon 函数贡献需要 ModList 初始化，放在加载后的动画运行回归中；独立夹具不等于可脱离模组启动。配置器折叠测试读取原生即时样式，避免未挂 UI 时误读计算缓存。
此前重复合成修复后，加载后的完整回归 4188 项通过，包含运行槽 4011、多实例 22、动画运行 56、编辑器配置 61 和时钟/参数 38；该轮编译/Data 与旧 main 74 项属于历史结果。
此前共享视图整合经 IDEA 编译，正常 Data 退出码 0；加载后的完整 runLoaded 最终为 4600 项通过，新增共享时间轴与原生一致性 412 项。包括录制冻结、真实 SerializableRecordAction 基础快照，以及追加查出的旋转停止恢复和重复 ADD 回归；实际窗口、音频和 GPU 另行验收。
中英文各新增 8 个预期翻译键、更新 4 个文本/提示，无删除；生成输出未手工编辑。本轮未再次运行独立 main。
实际结果见[修改记录](changes.md)；本次未运行 Client 或点击实际菜单，隔离断言不代表客户端视觉交互验收。
2026-10-06 去重收尾：IDEA 编译和正常 Data 成功（退出码 0），独立 main 74 项、完整加载回归 4640 项通过，新增门控/结束/重定时 40 项。中英文各 729 键，本轮各新增 8 键、更新 8 项文本/提示、无删除；源码、资源及备份检查见命名动画专页和变更记录。

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

- 场景与命名动画共用原时间轴：每个数值属性首次显示曲线时自动适配全部分量的关键帧和切线，并预留 10% 上下余量。恒定曲线使用默认半幅，避免零跨度或上下标签显示成相同数值；角度半幅为 360 度，其他数值为 1。
- 没有有限曲线数据时，原生角度属性默认 -360～360，其他数值属性默认 -1～1；速度、布尔等类型声明的固定下限继续生效。颜色仍使用原渐变编辑器。数值范围仅影响显示，不修改关键帧、目标基础参数、合法值限制或运行时采样。
- 初始化复用“适配曲线视图”（fit_view）开关，默认开启；按属性身份使用弱引用标记，普通编辑、布局刷新、折叠和场景/命名上下文切换不再次覆盖手动缩放和平移。重新加载项目或新增属性的新身份会重新初始化，手动适配入口仍可随时使用。
- 表达式片段通过原 evalExpr 在片段内按 64 个间隔一次性采样，考虑半开结束边界并忽略非有限结果；其范围是近似值，不保证捕捉任意高频或不连续表达式的全部极值。适配不按片段 tick 长度逐刻遍历，也不在每 tick 重新计算。

- 中键拖动移动视图：横向改变时间轴滚动，纵向改变当前属性的显示范围偏移。它不修改关键帧值；适配视图会重置相关显示偏移。
- Shift + 左键拖关键帧或曲线选择，超过拖动阈值后按主要方向锁定水平或垂直；修改的是曲线数据。松开 Shift 恢复普通拖动。单纯点击仍保留选择语义。
- Alt + 左键拖动可在结束时复制所拖关键帧；不叠加 Ctrl/Shift，并检查轨道锁定和目标时间冲突。
- 精确编辑与批量移动统一为同一入口，可修改所选关键帧的时间和值；时间缩放围绕选中关键帧的最早时间缩放，并同步切线。原 precise 与 batch_move 两个设置 ID 保留，任意一个开启即可使用，G 键与菜单共用判断。
- 切线菜单包含线性、缓入、缓出、两端缓动、阶梯、平坦、对齐、断开和重置入口。实际操作受属性类型和表达式片段限制。
- 原生对象“缩放”属性支持 XYZ 同时间关键帧和同向控制手柄按相同时间/数值增量联动，保留各轴差值；沿用原单选、框选、Shift 锁轴、Alt 复制和一次拖动撤销。拖动时缺少同时间点或对应手柄的轴不自动补点，对齐切线逐轴保留自身约束。
- 缩放新增关键帧同步三轴：各轴取该时间的原曲线采样值加主轴增量；任一轴已存在同时间点时整次拒绝，避免覆盖已有曲线。精确编辑、时间缩放与切线批量入口也扩展为同时间三轴；其他属性和生命周期内大小的分轴函数仍使用原编辑逻辑。
- “+ 添加属性”在数值、非离散属性创建前显示原生缓动选择窗口，提供线性、缓入、缓出、两端缓动和平坦；取消不创建属性。原新曲线只有一个关键帧，后续加点形成曲线段时应用预设并进入原新增点历史；颜色/离散属性沿用原创建流程，表达式或函数片段覆盖的区间不自动改底层切线。
- 原生框选后使用“工具 → 为选中关键帧选择缓动”批量调整关键帧手柄，不移动未选中的关键帧。设置中的默认曲线缓动决定弹窗初始值；弹窗选择只用于当前曲线，不修改全局设置。编辑曲线重载后新增点回到设置默认，已经形成的贝塞尔形状仍由原 Codec 保存。
- 三轴联动、新增曲线缓动选择、框选批量缓动位于“设置 → Lobecorp → 曲线编辑”，默认开启；分别关闭后恢复分轴操作、原属性创建及隐藏批量入口。精确编辑与切线的应用、撤销、重做同步原录制参考，避免额外录制基础参数。
- 修改检查有限值、非负时间、相邻时间间隔和锁定状态；失败恢复快照，通过现有操作历史提交可撤销变更。

[FXTimelineViewMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/FXTimelineViewMixin.java)
只提供时间适配、平移和复制粘贴连接，不替换原时间轴全部行为。

## 快捷键

扩展和模型动作统一注册原生 EditorAction，由“设置 → Keymap”提供搜索、主／备用键与逐项恢复默认。默认绑定如下；输入框、TextArea 和 Dialog 让出输入，作用域与执行资格仍由各动作检查。大多数扩展动作要求处于时间轴范围，独显和取消按各自作用域处理。

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
与 [LcPhotonEditorActions](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonEditorActions.java)集中管理动作 ID、KeyChord 默认绑定及旧配置迁移，不再使用扩展字符串绑定表或独立按键分发器。模型复用原生撤销／重做、全选与复制动作 ID，模型框选、取消与删除注册各自作用域；几何／UV 动作优先于通用场景键，防止默认 B 同时切换 Bloom。空绑定、冲突显示和恢复默认沿用原生 Keymap，文本组件保留原输入逻辑。

### 项目保存

当前 Photon 特效或模型项目打开后，Ctrl+S 可在时间轴、层级、检视、资源、命名动画和项目外壳中保存，不要求场景页取得焦点。主窗口始终取 EditorWindow 的当前编辑器；浮动面板通过原 EditorHost 保存所属项目，不跟随主窗口切换到另一个项目。无焦点时同样支持保存。未命名项目沿用原另存为窗口。
[PhotonEditorSaveUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorSaveUtil.java)只解析所属当前项目，不缓存编辑器引用；[ModularUIWidgetMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/ModularUIWidgetMixin.java)仅适配原 SAVE 的验证和执行分发。其他快捷键、非 Photon 编辑器以及保存格式沿用原逻辑。根级通知等没有编辑器祖先时，仅在当前 UI 存在唯一可见且活动的 EditorWindow 时回退，避免保存错误项目。

已确认根因：LDLib2 .39 的 createExecuteCommandEvent/createValidCommandEvent 关闭捕获和冒泡。有焦点时 EXECUTE_COMMAND 只发送给焦点元素；只有无焦点才遍历子树验证，原 Editor 保存监听因此无法覆盖任意控件焦点。不能靠给 EditorWindow 增加捕获监听修复，也不能全局开启所有命令冒泡；现将 SAVE 定向原 Editor，验证阶段不保存，执行仍只分发一次。IDEA 编译、正常 Data 和静态诊断通过，实际焦点、浮动面板、通知与另存为仍需客户端验收。

### 导出成功与定时通知

2026-10-14 层级与焦点修复：通知栈重新明确使用原生对话框层级 zIndex=1，避免默认 zIndex=0 与普通面板混排。定时通知本身和已有后代均关闭命中检测；仅对进入本项目通知栈的定时提示跳过 Dialog.show 中的 focus 调用，避免保存时抢走输入框、时间轴或普通确认框的焦点。设置关闭、非 Photon 和需要确认的普通 Dialog 保留原生焦点行为；堆叠数量、计时及关闭机制不变。新增 isNotificationStack 仅供该 Mixin 判断归属。IDEA 编译与静态诊断通过；重启后的原生提示实际 focusPreserved=true、可命中后代数=0，普通确认 Dialog 仍取得焦点，关闭后本轮恢复原焦点。已查看 build/mcp/notification-layer-fixed.png 与 notification-and-cone-verified.png，提示显示在顶部面板之上；完整浮动窗口、设置关闭与长期交互矩阵仍未验收。

设置位于“设置 → Lobecorp → 资源与剪切板 → 保存与导出通知”，标识 notifications，默认及旧配置缺省开启，继续保存在原 features 映射。原生 BooleanConfigurator 的说明复用本项目提示换行入口；关闭时 `.fx` 恢复原无成功提示、FXPack 恢复原成功确认弹窗、保存恢复原独立定时通知，错误与其他确认弹窗保持。

导出 `.fx` 在原生 writeCompressed 与 clearCache 均成功后显示 2 秒原生定时成功提示；FXPack 原成功通知改为同样的定时提示，保留导出 ID、文件及警告详情。失败、锁定错误与需要确认的普通弹窗保留原行为，不能在写入前或失败分支显示成功。

同一 Photon FXEditor 的定时通知由 [PhotonEditorNotificationUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorNotificationUtil.java) 放入原生竖向容器，顶部保留 10 像素、通知间隔 2 像素，最多同时 3 条；新通知超出上限时原生关闭最早一条。已有保存成功提示也沿同一入口，其他编辑器与非定时弹窗不进入该容器。容器不参与鼠标命中，通知关闭或 TICK 时移除空容器；项目导出编辑器引用使用 WeakReference，在 onLoad／onClosed 绑定和清理，没有静态窗口持有。

已确认源码差异：Photon `.fx` 导出原来没有成功提示，FXPack 成功使用需要确认的通知；LDLib2 .39 定时通知使用相同固定 top，连续出现时可能叠在同一位置。现在 [FXProjectMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/FXProjectMixin.java) 仅补成功路径，[DialogMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/DialogMixin.java) 仅标记和路由 showNotification(String,float)，已在队列的重复 show 不重复加入。维护时重新核对 `.fx` 写入／清缓存顺序、FXPack 成功通知 ordinal=1 与错误 ordinal=0，以及 Editor／浮动窗口归属；真实堆叠、超限关闭、导出与窗口生命周期仍需游戏内验收。

通知初阶段 5 个相关文件的 IDEA 诊断无错误／警告，Java 修改范围已格式化，Mixin JSON 保持原排版并复查 errors=[]；该阶段 12 个 Java 编译成功，Data 为 BUILD SUCCESSFUL in 32s、written=2，仅新增两项导出成功翻译，该结果不作为后补设置的 Data 证明。
设置补全后最终 13 个 Java 经 IDEA 编译成功，isSuccess=true、problems=[]；最终正常 Data exitCode=0、BUILD SUCCESSFUL in 21s，6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=0。相对通知修改开始时的 25 个生成文件，没有新增或删除，仅两份语言资源及对应两个缓存变化；两语言各共新增 export_success／export_success_detail 与 feature.notifications／feature.notifications.tooltip 四键，既有键及文本保持，没有手写或格式化生成输出。真实开关、导出、堆叠与窗口交互仍未游戏内验收。

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
默认：控制开始、开始时重启、控制结束、原结束行为、重启时清理；单帧触发默认关闭。开始控制和结束控制可独立关闭。

| 选项             | 结果                                                         |
|------------------|--------------------------------------------------------------|
| 开始：重启       | 重置运行/发射计时；“清理”默认开启，可关闭以保留已有粒子      |
| 开始：继续       | 继续当前运行状态，不每次从头播放                             |
| 结束：原行为     | 按 Photon 原激活轨道逻辑处理                                 |
| 结束：仅停止生成 | 保留对象活动和已有粒子，只停止新发射，让有限寿命粒子自然结束 |
| 结束：清理       | 执行清理结束                                                 |
| 仅开始控制       | 在片段起点执行一次开始行为并保持结果                         |
| 仅结束控制       | 在片段终点执行一次结束行为，之前不接管对象                   |
| 开始和结束均开启 | 按原片段区间控制开始与结束                                   |
| 开始和结束均关闭 | 不接管激活与发射                                             |

仅停止生成不会给无限寿命粒子自动补上淡出或消失时间；需要之后显式清理或另行安排结束表现。循环模块也不自带独立淡出包络。

[PhotonActivatorTimelineUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonActivatorTimelineUtil.java)
合并同对象的多条未静音激活轨道，按边界执行行为。具有自身激活轨道的子对象可覆盖父对象的激活和继承停止发射状态。父级手动隐藏仍遮住子对象；显式重启/清理子树也不能被理解为“子对象对父级一切操作免疫”。ControlTrack
若存在，仍参与最终 ticking 判定。

轨道保存字段位于 lobecorpActivation，含
startControl、startBehavior、endControl、endBehavior、clearOnRestart；缺失或非法枚举回退默认值。旧数据的 singleFrame=true 迁移为仅开始控制，新保存不再写 singleFrame。缺少该旧字段时尊重原两端开关；默认仍同时控制两端。复制轨道、序列化和恢复需要一起保留这些参数。

单边控制就是时间点命令，不再提供独立单帧开关；零长度片段也可作点。停止循环发射时关闭开始控制、开启结束控制并选择“仅停止生成粒子”，在片段终点生效，已有粒子继续更新。相同对象的普通双端区间保留 OR；最新单边命令覆盖仍持续的区间，后来的单边开始或真实区间入口才能恢复生成。同刻按叶轨道顺序处理，后者优先。发射器自身循环不解除停止状态；显式重启仍按开始行为恢复发射。

时间轴复用原 SignalTrackEditor 的竖线和方块：仅开始显示起点、仅结束显示终点、双端显示区间及两端点、两端关闭时淡化。新增 [PhotonActivatorClipUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonActivatorClipUtil.java) 与 [ActivatorClipLayoutMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/ActivatorClipLayoutMixin.java) 同步初次布局、原生回调、滚动缩放和拖动引导线，命中与框选使用点的实际几何。单点只允许原生移动和组移动，禁用边缘拉伸；保留原 clip.duration 与冲突约束，移动平移整个片段，普通片段时长检视仍能调整仅结束点。选项变更及撤销只 refreshLaneLayout，不重建选择和展开状态。

2026-10-08 根因与维护：原生 evaluate 把所有 Activator 当区间并提前写活动／显示；扩展多轨道 OR 又可能把仅结束的停止生成抵消。现在原生区间阶段只接收双端 Activator，单边由原边界扩展求值；命名 TimelineState 按每个播放器的制作时间认领已生效的点，未到点不写继承激活和发射。无命名控制器时只保存已接管点的活动／显示／发射基础值，拖回点前、静音或移除点时恢复，仍存在的原生 Control／双端轨道值优先。维护时覆盖点前外部停止、停止与持续区间并存、后来区间恢复及清理，不能用每帧活跃 OR 重新开启已停止的循环。

### 高速播放经过事件与零时刻预览

原生播放器按当前制作时间求值，较大的时间步长可能直接跨过短激活、控制或音频片段。新增 [PhotonTimelineTraversalUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonTimelineTraversalUtil.java) 收集前后制作时间之间的离散边界，按顺序补调用原生 evaluate，再执行当前时间的原求值；动画曲线仍由原生阶段采样，不另建播放器。信号继续使用原区间派发和去重；单边只补对应起点或终点，双端重叠片段按合并区间的真实入口和退出处理，不在区间重叠内部重复触发。

补求值需要暂时设置精确 localTime，并在正常求值前及 finally 中恢复原值；begin 重置经过时间，内部边界求值由重入标识保护。tick 补处理不调用 frame，避免音频、后处理或渲染贡献重复提交。维护时同时覆盖整段跨越、共享起止点、分组静音、回拖／重启与信号顺序，不能只增加最终时刻的 clipAt 查询。设置“Lobecorp → 粒子与渲染 → 时间轴经过事件补处理”（timeline_event_traversal）默认及旧配置缺省开启；关闭后恢复仅当前时间的原生采样入口。

原编辑预览在首次模拟更新后才把爆发粒子接入渲染，时间轴 0 时看不到初始粒子。本轮 [PhotonEmissionPrimeUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonEmissionPrimeUtil.java) 只在使用 DummyWorld 和独立 PhotonParticleManager 的编辑器场景中初始化 Burst.time=0，并将待加入粒子接入已有渲染队列；不调用 advance／tick，不推进粒子年龄、特效时钟或管理器计数，也不提前发射 rateOverTime 粒子。正常世界的 FX 不自动调用此初始化。

初始化复用原 burstTriggers 的数量、概率和周期判断；保存已经抽样的零时刻数量，首次正常发射窗口复用并排除已消费的零触发，clear 时清理记录。已有预热结果沿原规则保留，正开始延迟、已推进年龄、停止生成或非活动对象跳过初始化；非循环且寿命不超过一刻时，原预热不会执行，仍允许零时刻初始化。待加入队列只接入一次，首次正常更新仍处理出生事件和寿命。

维护时不能为显示 0 帧提前模拟一刻，也不能预发持续速率或重复抽样已消费的爆发。提前发生的零时刻抽样可能改变与后续随机操作的相对顺序，即使概率和原 Burst 计算相同，也不承诺相同种子下图像逐像素不变。设置“Lobecorp → 预览 → 零时刻发射初始化”（zero_time_emission）默认及旧配置缺省开启；关闭后不执行 prime 或待加入队列的额外接入。上述实际首帧、预热、概率与延迟效果仍待游戏内验收。

两个设置按运行实例所属管理器的 sceneView 查询编辑器，子实例沿用所属场景，普通世界或未绑定编辑器时默认开启经过事件处理；零时刻初始化仍只处理局部场景。不要使用其他窗口的当前项目替代实例所属编辑器，也不要将 PhotonEditorSettings 的实例方法当作静态 API。

2026-10-08 本批实际检查：34 个相关 Java 的 IDEA 最终诊断无错误／警告，修改范围格式化关闭额外导入优化和成员重排；IDEA 编译成功、Data 退出码 0。生成目录 25 文件无新增／删除，只有两份语言及对应缓存变化，中英文各新增 16 键，原有 858 键和值不变。新增“工具 → 测试 → 零时刻粒子发射”检查入口及相应加载后回归，但本轮未执行该入口、其他加载后回归或游戏验收；详见 [修改记录](changes.md)。

2026-10-08 单边激活与提示补全：24 个修改或新增 Java 完成 IDEA 修改行／涉及类格式化（关闭额外导入优化及成员重排），最终 errorsOnly=false 诊断均无错误／警告，Mixin 配置诊断无问题。IDEA build_project 返回 isSuccess=true、problems=[]；正常 Data 配置 waitForExit=true 返回 exitCode=0。生成目录 25 文件 SHA256 前后无新增／删除，只变更两份语言和对应两份缓存；IDEA 读取解析中英文各由 874 变为 872 键，删除旧 control.single_frame 标签及提示两键，各更新 17 项预期说明，无新增键，其他键值保持。生成输出未手写或格式化。

新增断言覆盖旧单帧迁移、仅开始／仅结束及零长度点、点前外部停止、拖回／静音恢复、与原生 Control／双端区间共存、同刻叶顺序与后来入口恢复／清理；零时刻粒子检查增加真实局部引擎的循环 Burst／持续速率停止、已有粒子年龄推进及 CONTINUE 恢复。未运行这些加载后检查或游戏，时间点命中／组拖动、悬浮屏幕边界、循环生成与命名并行仍待游戏内验收。已有不相关修改继续保留，未人工操作暂存、提交、推送或创建备份；新增两个 Java 由 IDEA 自动加入索引。文档既有设置分类表保留一条弱格式提示，不扩大为排版整理。

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

### 按钮悬浮时短暂掉帧：样式差量更新

2026-10-06 用户确认现象为“进入按钮或跨按钮移动时短暂下降”，不是停住后的持续低 FPS。按锁定 LDLib2 .39 和当前扩展源码确认下列开销；样式共同祖先的重复更新已优化，尚未运行 Client、性能采样或开关对照，实际 FPS 改善幅度未验证。

- 跨控件时，ModularUI.refreshHoveredElement 685～700 行先移除旧祖先链的 __hovered__，再添加新链；共同祖先也重复变动。UIElement.onClassIdChanged 908～913 行调用 StyleEngine.scheduleReloadElementStyles 189～197 行，递归标记整棵子树；下一帧重新匹配全局与祖先局部样式。大型编辑器会放大开销。目标稳定时不重复，因此主要解释跨按钮移动卡顿；持续下降还需排查命中目标是否反复变化。
- 稳定显示提示期间，ModularUIWidget.extractRenderState 468～490 行每帧清空并收集提示，521～523 行调用 DrawerHelperClient.drawTooltip。其 toClientTooltips 再调用 NeoForge ClientHooks.gatherTooltipComponentsFromElements，转换文本、测宽/换行并触发 GatherComponents；原版 tooltip 渲染还触发 Pre/Texture 事件并再次测宽。短文本的常规开销是否足以造成骤降尚无运行证据。
- 普通提示采用原版字体；原背景只有两次 blitSprite，未发现默认模糊或额外场景渲染。项目普通按钮提示在构建/ADDED 时设置；未发现悬浮触发动画面板重建、整树扫描、重复注册，或自定义 hover 尺寸规则/全局 Tooltip 事件处理器。新增标题路径提示每帧产生 Component 和列表副本，仅影响项目标题，没有文件读取，不能解释所有按钮。

[ModularUIMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/ModularUIMixin.java)只包装原 refreshHoveredElement(FF)V 中唯一 removeClass 调用。正在移除 __hovered__ 的元素仍存在于新目标的缓存路径中时，按当前 Photon 项目及设置保留它的类；其余调用原操作。共同祖先后续原 addClass 因状态已存在而直接跳过，避免共同根再次使整棵样式树失效。差异分支仍递归更新，保留父子悬浮与局部后代样式规则；没有二次命中检测、全方法替换或长期 UI 缓存。

设置位于“Lobecorp → 预览 → 悬浮样式更新优化”，标识 hover_style_delta，默认开启并提供原生提示；关闭后恢复原处理。作用域复用 PhotonEditorSaveUtil.currentProjectEditor 的 FX／模型当前项目与 EditorHost 解析，支持主窗口与浮动面板；非 Photon 或没有项目时沿用原逻辑。开关只影响编辑器，不改项目、特效定义、粒子运行或保存格式。

维护时保留原鼠标坐标、hitTest、悬浮路径 clear/addAll、最后命中目标及 Widget 的 MOUSE_MOVE/LEAVE/ENTER 顺序。当前依赖的 newHoveredElement 局部名称与 removeClass 描述符已通过 IDEA 核对；更新 LDLib2 时重新确认，不能用第二次 hitTest 或简单隐藏提示替代。IDEA 格式化、Java/Mixin 静态诊断、编译和 Data 已完成；中英文各仅新增开关名称及提示，共 749 键，原有 747 项文本保留。实际客户端按钮、菜单、滚动、拖动、浮动及 FPS 对比尚未验收。

后续若仍有短暂卡顿，优先比较该开关开启/关闭的相同面板，并采样 StyleEngine、DrawerHelperClient、Font 及底层渲染。提示缓存仍只是候选，须保留动态提示、语言、屏幕宽度与 NeoForge 事件语义，本轮未改动提示生成/渲染管线。另确认原 refreshHoveredElement 在旧路径非空且新命中为 null 时不会清除旧 hover 类，属于独立既有问题；本次保留原空目标处理，尚未修复。

## 修改前核对和维护

2026-10-06 三轴与缓动维护：缩放通道必须按关键帧时间匹配，不能按索引配对，不扩展 UI 选中集合，以保留原单选控制手柄显示。联动每帧从原 dragSnapshot 恢复后应用共同增量与所有通道的邻居限制，避免累积、穿越关键帧；逐轴同步对侧切线，不能只更新主轴。新增曲线与加点的缓动必须在原快照历史边界内完成，避免撤销只恢复点而遗漏手柄。
工具直接修改曲线绕开原交互入口时，必须调用原 noteRecordingEdit 同步录制参考；否则原录制轮询可能把预览变化误识别为基础对象修改。弱引用仅保存当前编辑身份的缓动偏好，不另建曲线序列化格式；表达式和函数片段仍遵循原生覆盖规则。
此前关于缓动弹窗 Ctrl+Z 会沿原 EXECUTE_COMMAND 冒泡到历史的判断有误：本轮核对命令创建器确认捕获/冒泡均关闭，键盘命令仅到焦点控件；不能只读通用分发器推断命令可达性。创建确认的 targetId、目标身份及可动画属性校验，批量确认的轨道/属性挂接、锁定、通道数及选中点/手柄快照校验继续保留，以拒绝其他入口造成的失效状态；状态失效使用原提示，不把旧索引映射到另一个点。
本轮 11 个 Java 完成 IDEA 修改段/新类格式化，关闭导入优化与成员重排；最终 Java 与 Mixin 配置诊断无错误/警告，IDEA 编译成功。正常 Data 退出码 0：19 个输出中写入 2 个语言文件，无删除；中英文各 747 键，各新增 18 个预期键，原 729 项文本完全保留。未运行 Client、独立测试或真实 UI 验收，拖动/录制/撤销、弹窗和标签悬浮仍需游戏内核实。

2026-10-06 窗口标题维护：原 EditorWindow.createEditorButton 直接写入 Editor#getTitle，并在原 TICK 回调每 20 tick 更新；UIElement 新监听的 addFirst 顺序会让追加的标题 TICK 回调先执行，随后被原回调覆盖。现仅包装创建及对应原生 lambda 中的 getTitle 调用，保留原刷新周期与保存确认标题，重名检测不缓存窗口引用。悬浮路径使用当前文件的 literal Component；新增混入同步登记配置，原生静态 lambda 的包装处理器使用静态方法。实际标签重绘与悬浮交互仍需游戏内验收。

2026-10-06 曲线初始化范围修复：原 buildExpandedRight 不适配范围，fitRangeToKeys 只在录制路径增长已有范围，不会在首次打开时收缩到数据。现于原编辑器构建曲线前调用 initializeRange，复用键/切线范围收集与上下界写入，手动适配保留选中关键帧语义。
维护时不能将一次性适配放到每次重建或 tick，否则会再次覆盖手动视图状态；状态恢复必须保留属性引用。初始化跨度用 double，最小余量考虑 float ULP，写入范围限制为有限 float，并保证可表示的上下差值，避免恒定大值余量被舍入或正负大值相减溢出。
本轮两个 Java 完成 IDEA 修改行格式化、无错误/警告的静态诊断与编译，Data 退出码 0；中英文生成资源各 729 键、文本与生成前一致，生成目录无 Git 差异。未执行 Client、游戏内范围交互或独立回归测试，不能以这些检查代替实际视觉验收。

2026-10-06 去重后，FEATURES 从 Category 的条目与 shortcuts 派生，避免分类和开关目录重复维护；旧配置 ID、默认值及显示顺序保持。编辑器祖先查找统一使用 PhotonAnimationEditingUtil.editor，跨 Photon 工具沿用同一窗口边界。
旧 editor.features 翻译尚不能排除二进制 UI NBT 引用，保持原条目；未经确认不得以文本搜索无结果直接删除资源键。

1. 核对 Photon、LDLib2 锁定版本、Mixin 目标方法及原事件/撤销语义；不要套用其他版本的 UI 示例。
2. 区分功能可见性、模块 enable、循环 enable 与运行时实际行为；新增开关需进入对应分类和默认值策略。
3. 修改剪贴板时同时检查复制入口、VALIDATE_COMMAND/EXECUTE_COMMAND、目标兼容性、文本焦点、反序列化和一次撤销边界。
4. 修改激活或循环时核对父子覆盖、重启清理、原坐标系、有限/无限寿命、存档及复制字段。
5. 新增参数覆盖要核对共享定义与实例、重播和关闭清理；新增 UI 复用原控件/样式。
6. 保持 [Mixin 配置](../../src/main/resources/META-INF/lobecorp.mixins.json)
   和[翻译集中入口](../../src/main/java/org/unitego/lobecorp/util/TranslationKeys.java)一致；新翻译由 Data 生成，不手写生成输出。
7. 系统行为、配置、协议、清理或路径变化后同步本文，在[变更记录](changes.md)
   记录日期、影响和实际验证。依赖桥接细节另见[研究记录](../project-reference/09-particle-effects-research.md)。
