# Photon／LDLib2 修复与兼容对照

更新日期：2026-10-11。

本文用于升级依赖前定位本项目补丁，区分上游实现的缺陷、依赖行为的兼容适配、性能优化和本项目扩展自身的问题。它不是全部功能清单，也不代替源码与游戏验收。系统入口见[索引](README.md)，功能说明见[Photon 编辑器](photon-editor.md)、[命名动画](photon-named-animations.md)、[发射器发射器](photon-emitter-spawner.md)和[粒子渲染](particle-rendering.md)；各轮实际操作见[修改记录](changes.md)。

## 当前基线与证据口径

| 项目                | 锁定版本           | 核对来源                                    |
| ------------------- | ------------------ | ------------------------------------------- |
| Minecraft／NeoForge | 26.1.2／26.1.2.100 | [版本目录](../../gradle/libs.versions.toml) |
| Photon              | 26.1.2.3           | 同上；IDEA 解析到当前依赖源码               |
| LDLib2              | 26.1.2.42          | 同上；IDEA 解析到当前依赖源码               |

- 当前命名动画配置为第六版；本文涉及的旧格式处理以当前迁移实现为准，不沿用已被取代的第三版、第五版行为。
- “源码确认”表示通过 IDEA 核对当前入口、调用或补丁；不表示实际窗口、粒子引擎、音频或 GPU 已验收。
- “历史检查”只引用对应系统文档已记录的诊断、编译、Data 或断言结果，不把它们计作本轮重新执行。新增断言存在不等于已运行。
- 本轮文档工作只做源码对照和文档检查；PH-01 的代码修改与实际检查由本轮修复负责人执行，结果单独记录。未运行测试或游戏。
- 补丁位于 Lobecorp 的 Mixin、适配类与工具类，没有直接改写第三方 JAR。LDLib2 补丁默认限于当前 Photon 编辑器及相应设置；升级时重新确认限定条件。

## LDLib2：兼容适配与性能优化

### LD-01 系统文本剪贴板入口

- **分类与现象：** 编辑器兼容适配。外部复制文本后，Photon 内文本框粘贴可能仍读取 LDLib2 内部剪贴板内容。对象／轨道／资源的跨窗口复制协议是另外新增的能力，不将它当成 LDLib2 原有承诺。
- **原生入口与根因证据：** .39 的 `TextField.TextFieldClientSupport.getClipboardText` 与 `TextArea.TextAreaClientSupport.getClipboardText` 调用 `ClipboardManager.INSTANCE.paste()`，只接受返回的 String，没有在这两处读取当前系统文本。
- **项目补丁：** [TextFieldMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/TextFieldMixin.java)、[TextAreaMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/TextAreaMixin.java)在 .42 包装原 `onExecuteCommand(UIEvent)` 的取文本调用；当前 Photon 编辑器启用 `system_clipboard` 时改读 `Minecraft.keyboardHandler.getClipboard()`，其他情况调用原操作。上游 .42 已修复系统文本粘贴；关闭本项目选项时遵循新版原生行为，不恢复 .39 内部缓存行为。对象／轨道／资源的类型化协议仍独立保留。
- **保留语义：** 仍由原文本组件执行选择、输入过滤和撤销；不全局替换 ClipboardManager，也不把图片、文件列表、路径或任意 JSON 自动转换成 Photon 对象。对象协议仍先检查版本、种类、数量、引用与目标兼容性。
- **验证与升级复核：** .42 实际源码、字节码及迁移后 IDEA 诊断已核准，本轮统一检查见[依赖升级](photon-dependency-upgrade.md)。跨程序、跨窗口粘贴与输入焦点组合仍未游戏验收。后续升级继续检查两个内部支持类、取文本描述符、编辑器归属及设置关闭后的原路径。

### LD-02 Ctrl+S 在子控件有焦点时的保存路由

- **分类与现象：** 命令分发兼容适配。焦点位于文本框或其他子控件时，原 Editor 的保存监听不能覆盖任意焦点。
- **原生入口与根因证据：** .39 的 `ModularUIWidget.createExecuteCommandEvent/createValidCommandEvent` 明确关闭捕获和冒泡；`keyPressed` 有焦点时把 EXECUTE_COMMAND 发给焦点元素，无焦点才遍历子树执行命令验证。
- **项目补丁：** [ModularUIWidgetMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/ModularUIWidgetMixin.java)在 .42 的 `dispatchCommand(String,int,int,int)` 包装 SAVE 分发；[PhotonEditorSaveUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorSaveUtil.java)识别原宿主与浮动窗口归属。实际 .42 字节码保留两次 dispatchEvent 与一次 dispatchAllChildren 调用，新版原生命令事件允许冒泡。
- **保留语义：** VALIDATE_COMMAND 只验证，不执行保存；EXECUTE_COMMAND 仍分发一次，已处理的原事件阻止回退重复保存。其他命令和其他编辑器保持新版原生分发，不改全局事件阶段。
- **验证与升级复核：** .42 实际源码、字节码及迁移后 IDEA 诊断已核准，本轮统一检查见[依赖升级](photon-dependency-upgrade.md)。真实焦点、浮动面板、保存及另存为尚待游戏验收。后续升级需重新检查事件阶段标志、焦点分支和原 Editor 的 SAVE 处理。

### LD-03 共同悬浮祖先导致重复样式失效

- **分类与现象：** 性能优化。大型 Photon 面板跨按钮移动时，共同祖先的样式可能被重复标记重算；不能据此宣称所有卡顿或持续帧率下降均已定位。
- **原生入口与根因证据：** .39 的 `ModularUI.refreshHoveredElement` 先对旧路径移除 `__hovered__`，再对新路径添加。共同祖先也经历类变化；`UIElement.onClassIdChanged → StyleEngine.scheduleReloadElementStyles` 递归标记后代样式。
- **项目补丁：** [ModularUIMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/ModularUIMixin.java)在当前 Photon 项目启用 `hover_style_delta` 时，仅跳过仍处于新悬浮路径中的旧祖先移除操作；原 addClass 对已存在的类保持幂等。
- **保留语义：** 原 hitTest、鼠标坐标、路径更新、最后命中目标和 MOUSE_MOVE／LEAVE／ENTER 顺序保留；变化分支正常更新。没有第二次 hitTest、全方法替换或长期控件缓存。
- **验证与升级复核：** 历史 IDEA 格式化、诊断、编译和 Data 通过；未测量实际 FPS 或分配。升级检查唯一 removeClass 位点、新目标局部名和路径缓存。原“新命中为 null 时旧 hover 类未清除”是另一个源码确认问题，本补丁保留原空目标处理，尚未修复。

### LD-04 连续定时通知重叠

- **分类与现象：** 通知布局兼容适配。原 `Dialog.showNotification(String,float)` 均使用同一固定 top，连续通知可能重叠；新增导出成功提示属于项目增强。
- **原生入口与根因证据：** .39 的定时通知各自创建顶层 Dialog，默认位置相同；需要确认的标题／详情通知是不同重载，不能一起替换。
- **项目补丁：** [DialogMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/DialogMixin.java)只标记与路由定时重载；[PhotonEditorNotificationUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorNotificationUtil.java)按当前编辑器竖排、限制最多三条并清理关闭项。[FXProjectMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/FXProjectMixin.java)仅接入导出成功路径。
- **保留语义：** 错误与确认弹窗保留；重复 show 不重复入队，关闭设置恢复原行为。`.fx` 提示在写入与清缓存成功之后；FXPack 只替换成功通知，保留失败通知。
- **验证与升级复核：** 最终通知设置补全的 IDEA 编译／Data 成功是历史记录，本轮源码确认。真实堆叠、超限关闭、窗口销毁及成功／失败导出仍待游戏验收。升级重新核对成功 ordinal=1、错误 ordinal=0、写入顺序、Dialog 重载与归属查找。

### LD-05 独立窗口修改视图大小退回游戏窗口

- **分类与现象：** LDLib2 .42 宿主与设置应用兼容缺陷。独立编辑器窗口内使用“视图大小”时，当前标签的窗口偏好可能使共享窗口重新停靠；偏好已为独立窗口时，原生提前返回又会忽略缩放。
- **原生入口与根因证据：** ViewMenu 的大小菜单先修改 AppearanceSettings.screenScale，再调用 EditorSettings.applyCurrentSettings。AppearanceSettings.onApply 先执行 applyPreferredHost，再对 isInOsWindow 返回；EditorSettings 按编辑器标签持有，EditorWindow 则共享，弹出窗口只记录当前标签的 preferOsWindow，新建或切换标签后的偏好可不一致。边框调整大小链未发现该宿主切换，不据此宣称边框拖动存在同一根因。
- **项目补丁：** [ViewMenuMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/ViewMenuMixin.java)包围菜单的设置应用；[PhotonEditorViewScaleUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEditorViewScaleUtil.java)仅为 FXEditor（含模型外壳）建立同步线程作用域并在 finally 恢复；菜单对重复选择也执行设置应用，原生逐项相等跳过 onApply 时按实际倍率差补调原 AppearanceSettings.onApply；[AppearanceSettingsMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/AppearanceSettingsMixin.java)仅在该作用域跳过宿主偏好重施，并允许独立窗口继续进入原生 GUI scale 限定、设置与 resizeGui 流程。
- **保留语义：** 普通设置 Apply 仍执行显式宿主偏好；不修改 preferOsWindow，不复制设置快照或另建缩放系统。其他编辑器保持原行为。视图大小沿用原生共享 GUI 倍率，不承诺每个 OS 窗口独立倍率。
- **验证与升级复核：** 当前依赖源码及 MixinMCP 方法字节码已核对，精确菜单目标为 lambda$createDefaultMenu$2；新增补丁的 IDEA 诊断和统一编译／Data 结果见[修改记录](changes.md)。后续升级须重新核对 synthetic 方法、applyPreferredHost 和 isInOsWindow 两个位点；独立窗口、切换标签、缩放及普通设置应用仍待实际客户端验收。

## Photon：缺陷修复与原生优化

### PH-01 BeamParticle 场景预览相机空实体

- **分类与现象：** 上游实现与预览相机的兼容缺陷。光束启用方块或实体射线检测时，场景预览相机没有实体可导致空指针；正常世界相机不据此判定为有同样问题。
- **原生入口与根因证据：** Photon 26.1.2.2 的 `BeamParticle.getRealEnd(Camera,Vector3f)` 在 BLOCKS／BLOCKS_AND_ENTITIES 和 ENTITIES／BLOCKS_AND_ENTITIES 两条分支均直接调用 `camera.entity().level()`，共两处；前面已经算出经发射器变换的终点。
- **项目补丁：** [BeamParticleMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/BeamParticleMixin.java)扩展现有混入：非空相机实体调用原 Entity.level；空实体时改用 `getEmitter().getLevel()`，`require=2` 校验两处覆盖。相机实体及发射器世界都不存在时，在首个 BeamConfig.getRaycast 前返回原局部 `end`，通过 `@Local(name="end")` 明确定位。
- **保留语义：** 不改变正常相机的世界来源、射线模式、终点变换、方块／流体检测或实体碰撞；已有光束长度覆盖继续生效。仅无世界时跳过不可执行的射线检测，未新增运行类、Mixin 配置或修改 LDLib2。
- **本轮实际检查：** 修复负责人确认 BeamParticleMixin 完成 IDEA 范围／类格式化（关闭导入优化和成员重排），最终诊断无错误／警告；IDEA build_project 返回 isSuccess=true、problems=[]。首次 Data 的原工具输出截断且未给退出码，IDEA 读取日志确认六个 provider 完成、HashCache total／old／new 均为 19、written=0、removed=0；首次生成目录 25 文件 SHA-256 前后完全一致。随后通过可跟踪的 IDEA 运行 API 执行同一 Data 配置，明确返回 success=true、exitCode=0、timedOut=false；第二次日志同样确认六个 provider 完成及相同 HashCache 结果，25 个生成文件再次前后完全一致，无新增、变更或删除。生成输出只读核对，未手写或格式化；未运行测试或游戏，以上不代替实际光束预览与 GPU 验收。
- **升级复核：** 核对两处 Entity.level 的调用次数与描述符、`end` 局部变量、首个 getRaycast 位点及发射器世界接口。游戏需验收正常相机、无实体但有世界、两者均空，以及四种射线模式与动态光束长度。

### PH-02 噪声预览重复采样与逐像素 GUI 提交

- **分类与现象：** 原生性能优化。噪声模块面板显示预览时工作量大；运行中粒子的 NoiseSetting 更新是另一个入口。
- **原生入口与根因证据：** `NoiseSetting.NoisePreview.drawInternal` 每次绘制遍历预览网格采样、重映射并分别提交 GUI fill；参数未变化时仍重复采样。
- **项目补丁：** [NoisePreviewMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/render/client/NoisePreviewMixin.java)按宽高、频率、quality 和 remap 缓存采样颜色；[PhotonNoisePreviewState](../../src/main/java/org/unitego/lobecorp/client/photon/render/PhotonNoisePreviewState.java)一次提交 GUI 状态，在原网格分辨率构建顶点。
- **保留语义：** seed 归预览实例，Noise1D 一行拉伸，其余按原网格、灰度和 remap 计算；保留 pose、tint、scissor。没有降频或降采样。每帧 remap NBT 比较、缓存命中时顶点生成仍有开销。
- **验证与升级复核：** 本轮依赖和补丁源码确认；未进行 FPS、CPU 分配或 GPU 计时，不把其他轮次检查或“有缓存”当作性能实测。升级核对绘制入口、GUI 状态提取生命周期、颜色顺序和 remap 持久化。

### PH-03 曲线首次展开的可见范围

- **分类与现象：** 编辑辅助增强。角度、负值或大值曲线首次展开时，原默认范围可能不便观察，不等于关键帧求值错误。
- **原生入口与根因证据：** `AnimationTrackEditor.buildExpandedRight` 创建曲线视图；原 `fitRangeToKeys` 是显式操作且只扩大范围，未覆盖本项目要求的首次自适应。
- **项目补丁：** [AnimationTrackEditorMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/editor/client/AnimationTrackEditorMixin.java)配合[PhotonCurveEditUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonCurveEditUtil.java)按属性身份只初始化一次，处理角度、有限值、关键帧和函数范围；颜色曲线保留原行为。
- **保留语义：** 不改关键帧数据或原采样，不在每帧、刷新和普通编辑时重新 fit，不覆盖用户后续平移／缩放；double 范围与有限值边界用于防溢出。
- **验证与升级复核：** 历史两类 IDEA 格式化、诊断、编译和 Data 通过，真实拖动及范围视觉未验收。本轮源码确认；升级检查视图建造入口、属性身份与保存恢复范围，不能改回每次重建都初始化。

## 本项目扩展自身的修复

以下根因属于 Lobecorp 新增动画、时间轴或渲染集成，不标作 Photon／LDLib2 原生缺陷。

### EX-01 动画状态翻译传入集合

- **现象与证据：** 自有状态格式把问题 List 直接作为 Component.translatable 参数，历史客户端出现非法格式参数；[PhotonAnimationUiUtil.animationStatus](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationUiUtil.java)现传 `problems.toString()`，状态名称仍用原翻译 Component。
- **保留与复核：** 不把整条状态改成硬编码文本；新增格式参数须核对原 TranslatableContents 允许的类型，空／非空集合均检查。历史加载回归包含此项，本轮未重跑；实际状态栏与两种语言仍需客户端确认。

### EX-02 切换或修改后时间轴状态及内容提交

- **现象与证据：** 早期第二时间轴视图、模型替换及刷新导致编辑状态丢失；后续空选测试 TL 离开时未先提交当前投影，可能再次载入旧内容。当前[PhotonAnimationTimelineContext](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationTimelineContext.java)的 prepareSwitch 结束录制与拖拽、提交当前 TL 并保存状态；[PhotonAnimationWorkspace](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationWorkspace.java)共用原 owner.timelineView。
- **保留与复核：** 原场景与各动画分别保存游标、范围、展开及选择；重复选择不重置。只在离开／切换边界回存，不能让普通载入或撤销恢复覆盖恢复结果。测试 TL／预览状态保存在工程预览数据，排除运行资源导出。
- **实际边界：** 历史共享一致性回归与后续编译／Data 分别有记录，不互相替代；实际切换、排序、录制、撤销及窗口布局未游戏验收。

### EX-03 被动 UI 同步访问已结束的运行态

- **现象与证据：** `syncPreviewDispatch → Preview.runtime` 在结束或重建过程中曾访问不存在的运行态。当前[PhotonAnimationPreview](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationPreview.java)提供 runtimeOrNull，过滤不存在／关闭的实例；Workspace 的被动同步通过可空查询安全跳过，实际身份变化重新绑定。
- **保留与复核：** 结束特效后等待主动重启，不因 UI tick 自动 emit；不捕获任意异常掩盖问题。检查运行态销毁、工程重载、切换模式和首次重建的边界。
- **实际边界：** 当前源码确认，历史编译／Data 与模型检查见命名动画页；真实结束后停留、重新播放及窗口关闭仍待游戏验收。

### EX-04 重复合成、停止恢复与录制基础快照

- **现象与证据：** 自有混合器曾把上次合成值再次当基础值，ADD／MULTIPLY 逐帧漂移；旋转经 Euler／Quaternion 往返后，写前值不等于真实写后值，所有权判断和停止恢复可能失效。当前[PhotonAnimationMixer](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationMixer.java)每次采集前释放自己仍持有的贡献，[PhotonAnimationProperty](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationProperty.java)保存写后实际指纹、函数身份和原四元数。
- **关联修复：** [PhotonAnimationRecordMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/animation/client/PhotonAnimationRecordMixin.java)与[PhotonAnimationHistoryUtil](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationHistoryUtil.java)包装原 SerializableRecordAction 序列化，捕获基础值而非正在合成的变换，避免撤销固化贡献。
- **保留与复核：** 停止只撤回仍属自己的写入，保留其他动画及外部覆盖；保持真实四元数，不依赖重算 Euler 判断等价。直接值与外部恰好写入相同值仍无法仅凭数值区分所有权，不承诺检测所有同值写入。
- **实际边界：** 历史完整加载回归确实覆盖重复 ADD、停止恢复、多轴旋转和原生历史；本轮未重跑。升级核对原属性多态、函数槽、四元数写入和记录构造／更新快照入口。

### EX-05 单边激活与停止生成状态被原区间重新打开

- **现象与证据：** 新增“仅开始／仅结束”属于时间点命令；如果仍进入原区间 OR 或每次持续压制状态，点后的外部停止／恢复会被覆盖。[TimelinePlayerMixin](../../src/main/java/org/unitego/lobecorp/mixin/photon/runtime/client/TimelinePlayerMixin.java)通过[PhotonActivatorTimelineUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonActivatorTimelineUtil.java)只让原双端区间进入原生区间链，新增单边点按经过边界执行；[PhotonTimelineTraversalUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonTimelineTraversalUtil.java)处理经过的边界。
- **保留与复核：** 原 Control／双端区间继续原逻辑；单边不是独立 singleFrame 字段的新副本。拖回、静音、零长度、大步长及同刻叶顺序需同时检查；每个播放器的精确时间与状态独立。
- **实际边界：** 该轮最终 IDEA 诊断、编译／Data 有实际记录，新增加载断言尚未运行；时间点命中、组拖动、循环及命名并行仍待游戏验收。

### EX-06 第六版迁移失败残留局部形参

- **现象与证据：** 转换旧规则时，若先添加形参再失败，可能留下无调用引用的参数或污染“已引用参数”集合。[PhotonAnimationMigrationUtil](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationMigrationUtil.java)现按每条规则保存原参数数量，失败回退新增形参，仅成功后提交引用集合。
- **保留与复核：** 当前策略丢弃不支持的旧规则并报告非阻塞警告，保留支持动画、既有绑定和成功规则；未知版本／损坏轨道仍走迁移保护。加载审查不写回二进制原件，不能把被取代的旧“三个文件受阻”报告当成当前结果。
- **实际边界：** 历史该轮迁移加载检查 378 项、完整检查 5611 项通过，并只读审查真实旧文件；本轮未重跑。升级或更改格式需检查失败回退、UUID、警告归属与保存／导出往返。

### EX-07 自有遮挡预写持有已扩容的索引缓冲

- **现象与证据：** 新增不透明像素深度预写若逐项 prepare 并保存共享 AutoStorageIndexBuffer 引用，后续扩容会关闭旧缓冲且可能由 SHORT 改 INT。当前[PhotonOcclusionDrawUtil](../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonOcclusionDrawUtil.java)先按索引存储身份聚合最大需求并 reserveIndices，再读取全部 buffer／type。
- **保留与复核：** 不关闭借用的 mesh、顶点、实例、索引及原材质 UBO；不改变原主颜色顺序。未知 Shader 沿原管线，半透明排序和不同 stage 已混合颜色不由此修复。
- **实际边界：** 渲染页记录 IDEA 格式化、诊断、编译和正常 Data 通过；实际 Mixin 加载、Shader 编译、CPU／实例化、交叉纹理、Bloom／Iris 和 GPU 尚未游戏验收。升级检查缓冲所有权、索引类型转换与 RenderPass 生命周期。

## 不应记为已修复上游 Bug 的内容

| 内容                                                | 正确定位               | 维护边界                                                                                   |
| --------------------------------------------------- | ---------------------- | ------------------------------------------------------------------------------------------ |
| 不依赖有限寿命的循环大小、颜色、旋转、UV 与时间偏移 | 本项目新增控制能力     | 不把寿命为 0 的原归一化时间语义判作普遍缺陷；有限寿命仍按原规则                            |
| 0 帧 Burst 提前显示                                 | 编辑器首帧增强         | 不提前推进一 tick，不预发持续速率；随机抽样顺序与实际首帧仍需验收                          |
| 命名动画、参数、触发规则、子动画和发射器发射器      | 本项目扩展系统         | 自有计时、迁移、GUI 和生命周期问题归扩展维护，不转嫁为上游 Bug                             |
| 交叉透明纹理的 Order／深度遮挡                      | 透明排序算法的能力边界 | 整作业排序不能表达所有逐像素交叉关系；不透明像素预写不是 OIT，不能声称任意角度半透明都正确 |
| 新增重命名窗口、快捷键、折叠策略、导出提示          | 编辑器工作流增强       | 保留原组件和样式；功能增加本身不是缺陷证据                                                 |

## 升级与验收记录要求

1. 先在 IDEA 核对新版本入口、描述符、重载、调用次数、局部变量和设置限定，再判断补丁保留、修改或删除；仅“可编译”不能证明注入实际命中。
2. 上游已有等效修复时避免双重处理，比较设置关闭、普通世界、无控制器特效及外部编辑器的行为；不要直接删除仍承载扩展语义的混入。
3. 诊断、格式化、编译、Data、生成资源检查、隔离断言及客户端视觉分别记录。性能需相同面板／配置开关的实测，通知、剪贴板和动画需实际交互，渲染需实际 GPU／Shader 验收。
4. 本文不把临时参数刷新、模板基础变换复制、跨周期 MARKER 顺序或 LD-03 的空悬浮目标问题记为已解决；已知根因和当前限制见各系统文档，修改前仍需再核对源码。
5. 每次修复同步对应系统页、本文条目和修改记录；记录问题现象、根因证据、保留语义及实际执行结果，防止下一轮重复引入或把历史检查误报成本轮通过。
