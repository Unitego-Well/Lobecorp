# Photon 命名动画与参数控制

## 独立回归测试入口

2026-10-10 起，不启动游戏的时钟／参数及 GUI 数据检查由 `src/test/java` 中的 `PhotonAnimationRegressionTest` 两个 JUnit Jupiter 测试调用，通过 IDEA 测试入口或 Gradle `test` 执行。原 `PhotonAnimationRegression.main` 已移除；菜单共用的 `runIndependent`、`runPropertySlots`、`runInstances` 及加载检查目录保留。测试框架仅进入测试类路径，不进入模组运行依赖或发布包。

普通 JUnit 测试不初始化 NeoForge、Minecraft 客户端或 Photon 注册表；加载环境下的属性槽、实例、运行态和真实界面检查继续使用既有菜单／调试入口。下文历史记录中的 main 执行结果不代表迁移后的测试已运行，实际检查见 [修改记录](changes.md)。

核对日期：2026-10-10。实现针对 Photon 26.1.2.3 / LDLib2 26.1.2.42。
源码与 IDEA 解析结果优先；本文区分已实现能力和待游戏内验收内容。

本轮原生 TimelinePlayer 的 evaluate 与 localTime 已改用 double，项目 Invoker、Accessor、注入回调及原生恢复求值同步更新，不再截断小数 tick。采样作用域、事件跨越与子动画语义保持；发布证据与检查见[依赖升级](photon-dependency-upgrade.md)。

新版传输栏的逐帧与跳末尾转交当前命名动画的独立 playhead／previewTime／seek，保留末尾小数、暂停并清除排队播放命令，不使用 FX 主时钟推进所选动画；普通 FX 和模型工程继续各自路径。

## 第六版实现范围与迁移边界

当前定义版本为 6。每个命名动画声明独立形参，形参使用稳定 UUID；名称仅用于显示与代码查找。不再提供整库共享参数或复杂动作链运行层。原生 TL 继续是唯一制作视图，组合调用改为绑定发射器发射器的原生子动画片段。

按用户最新“不支持的直接抛弃即可”指令，旧 version=1～5 加载时保留可解析的动画和单个本实例 SELF PLAY 规则；固定时长、固定输入及有明确绑定或目标输入归属的共享参数转换到独立形参，生成稳定引用。不支持的旧动作、空 START、CHILD_INSTANCES、多个动作、动态时长／共享来源或非法调用输入整条丢弃，不猜测转换成 PLAY 或子片段。未被保留内容引用的共享参数也丢弃。

已丢弃项进入只读 migrationWarnings，编辑器使用原生非阻塞报告；仅有警告时仍可编辑、播放及正常保存／导出为 v6。加载中的逻辑迁移不手工改写磁盘原件，不再准备三份资源的二进制兜底脚本或自动补造 0～20 tick 子轨道。

未知版本、损坏动画或参数结构、未知轨道类型和无效默认动画引用等仍沿现有保护：保留完整原始 NBT 与阻断报告，禁用该实例扩展编辑／播放，阻止写为 v6。保存、另存、.fx／FXPack 导出继续全量检查，区别非阻塞弃用警告与真正的数据损坏，不能用“丢弃旧动作”跳过动画结构保护。没有控制器的对象继续沿用原生持久化。

下文描述当前第六版用法；“实际检查”中的 2026-10-05～07 结果是历史证据，不代表第六版回归已执行。

## 单属性多绑定（2026-10-14）

同一目标与属性的所有匹配绑定按定义列表顺序合成，不再只取第一项；原生属性采样一次，再分别生成贡献。component>=0 在当前贡献实现中限定目标分量，XYZ 可以分别从不同形参取值；同分量交叠按列表顺序处理，离散属性仍只接受 OVERRIDE。没有绑定时保留默认曲线贡献，单项绑定和函数生命周期／随机数语义不变，定义版本与公开接口不变。

运行配置覆盖位于 RuntimeBinding 槽中，直接读取 config 字段仅反映制作基础值。续作工程已在加载客户端核对三轴范围 [0.5,0.5,6]、中心 [0,0,3]、模型／粒子关闭和同实例重触发；具体结果见[修改记录](changes.md)。

## 模型动画片段

2026-10-13 增加“模型动画”轨道，可在普通时间轴或任一命名动画内绑定模型发射器，并选择其资产中的动画片段。它复用同一个原生 TimelinePlayer 的时间、倍率、暂停、定位及重复触发流程；默认片段时间由轨道覆盖，不改写模型资产或发射器配置。结束后可恢复默认动画或保持采样，停止命名播放会释放该播放拥有的模型覆盖。多个命名播放按既有优先级与启动顺序选择一个完整模型片段，不做模型片段间的骨骼混合。

现有 START／SIGNAL／TRIGGER 仍只触发命名 FX 动画。粒子出生／死亡／碰撞按原生子发射器新建独立模型特效并播放默认片段，具体设置、保存与导出约定见[模型特效](photon-model-effects.md#由粒子特效触发模型动画2026-10-13)。

## 入口与所有权

打开特效项目后，“场景”右侧提供原生“命名动画”标签，点击切换到独立动画工作区。
“工具 → 命名动画”选择同一个标签，关闭后可从该入口重新打开；手动浮动的标签从该入口停靠回来。
设置位于“Lobecorp → 粒子与渲染 → 命名动画”，默认开启。
标签首次打开时借用原场景；没有控制器时，通过原操作历史添加一个 `lobecorp_animation_controller` 对象。
只打开项目、不选择动画标签，不会添加控制器或初始化额外粒子预览。
每个特效只使用一个控制器；已有多个控制器时拒绝打开，不自动删除对象。

控制器不产生或渲染粒子。配置作为对象的 definition NBT 保存，复用 Photon 对象 Codec、项目、.fx、FXPack 和复制接口。
动画和目标对象采用 UUID 引用，名称用于显示和代码查找；动画重命名不改变 UUID。
控制器保存命名动画和简化触发入口；每个动画分别保存完整原生 Timeline、形参、绑定和标记。播放状态及形参值仅保存在各自 FXRuntime 的播放句柄中。当前 definition version=6，旧版按上节边界迁移。

职责入口：

- [配置模型](../../src/main/java/org/unitego/lobecorp/config/photon/animation)：NBT 格式、动画、参数、绑定及规则。
- [控制器](../../src/main/java/org/unitego/lobecorp/client/photon/animation/PhotonAnimationController.java)：Photon 扩展对象与运行态挂接。
- [注册](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonAnimationTypes.java)：原生 FXObjectType 注册。
- [播放运行态](../../src/main/java/org/unitego/lobecorp/client/photon/animation/runtime/PhotonAnimationRuntime.java)：实例时钟、触发队列、参数和公开操作。
- [子片段桥](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonChildAnimationPlayer.java)：原生播放器驱动已有子实例的受控／独立播放及片段归属清理。
- [子动画轨道](../../src/main/java/org/unitego/lobecorp/client/photon/animation/timeline)：原生 Track／Clip 的配置、复制和 NBT。
- [旧定义迁移](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationMigrationUtil.java)：保留支持内容、丢弃不支持旧调用并报告；版本或动画结构损坏时保留原件并阻止覆盖。
- [适配层](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter)：原生 RuntimeBinding、NumberFunction、属性贡献合成。
- [原生轨道播放](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationNativePlayer.java)：复用 TimelinePlayer 执行激活、控制、速度、信号、音频和后处理；普通动画属性继续进入贡献混合。
- [编辑工作区](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationWorkspace.java)：共用场景、动画录制、TL 交互与历史。
- [监视会话](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationInspectorSession.java)：轨道、片段等配置器变更保存到动画定义与历史；对象检视直接使用原 Inspector 和基础参数历史。
- [编辑快照](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationEditSnapshot.java)：保存定义及工作区模型引用，恢复编辑状态时保留有效轨道和属性身份。
- [项目测试存储](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationProjectAccess.java)：FXProject 独立持有测试定义，关闭工作区不删除；[存储工具](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationProjectPreviewUtil.java)复用原定义格式，仅写工程字段。
- [组合测试控件](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationTestControls.java)：右侧独立选择测试动画与临时实参，不改变制作选择或动画默认值。
- [动画标签](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationTab.java)：场景旁的原生标签、首次打开初始化与关闭清理；拖动、停靠和浮动沿用原 View。
- [外侧面板](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationPanels.java)：放置左侧动画列表、原层级、动画检视和共享时间轴，模式切换保留用户当前标签。
- [表单状态](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationFormState.java)：临时折叠与滚动快照，沿用原生控件，不写入资源。
- [工具](../../src/main/java/org/unitego/lobecorp/util/photon/animation)：翻译、UI、复制映射、生命周期和实例查询。

## 制作与预览

场景时间轴是始终存在的基础动画，仍按原生时间轴规则推进，单独保存于 FXData.timeline。
场景模式只运行它，不挂接命名动画运行态；默认命名动画、START/SIGNAL/TRIGGER 规则与命名动画的保活不会改变主场景预览。
命名模式在同一场景中先求值基础时间轴，再按优先级和启动顺序合成命名动画；各命名动画仍可独立触发、暂停、改变时长或停止。
停止命名动画只移除它的贡献，剩余动画继续叠加在场景时间轴的当前值上；切回场景恢复进入前的场景时间和播放状态。
这是播放贡献的单向关系；原检视和录制对项目基础参数的真实编辑仍按既有共享检视规则保存。

中央“场景／命名动画”继续使用同一个 SceneView、相机、渲染器和粒子管理器；底部始终是原来的 `owner.timelineView`。
不创建第二个 TL 页面或 UI 实例。原时间轴与各命名动画由独立的
[TimelineContext](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationTimelineContext.java)
和 [TimelineModel](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationTimelineModel.java) 切换数据与历史。
动画、激活、控制、信号、分组、速度、音频、后处理八类轨道，片段、曲线、标记、录制、复制/粘贴、EndMode/Wrap 和工具均沿用原 TL。

左侧“动画列表”铺满面板，使用“发射 → 爆发”的原生数组行、拖动条、边框和加减按钮。
动画名称用铺满的只读 Label 显示，避免文本框背景遮住原生选中皮肤；实际选中 UUID 和视觉高亮分别核实。
“+”新建、“-”删除选中项，行右键复制、重命名和删除。拖动结束提交一次排序历史，保留 UUID 和引用。
复制弹窗以原动画名称生成预填值，重名时追加可用序号；仍可在确认前编辑名称，沿用原名称唯一性检查和复制历史。
左键同步当前制作动画、时间轴和动画检视，重复选择当前动画不会重置状态；点击列表空白可取消选择，进入项目专用测试时间轴。
目标仍直接使用原“特效层级”，不建立另一个目标树。
切换中央模式仅改变编辑上下文，不自动选择左侧列表、右侧动画检视或底部 TL 标签；用户主动选择对象/轨道时仍沿用原检视行为。
表单值编辑、新建/复制/重命名等重建会恢复有效选择、折叠和滚动；已删除的项不恢复为其他同位置项。

右侧原“监视面板”直接显示完整场景检视，包含原模块、纹理、颜色与配置器。
只按字段身份锁定 FXObject.name，名称不能输入、粘贴或通过该字段修改；动画列表重命名仍可用。
选择对象、轨道或片段后显示原检视标签，避免配置器已构建却藏在未选中的面板。
命名动画、混合与绑定、参数定义和触发规则仍位于单独的“动画检视”四个原生标签页，页签在顶部且分组可折叠。

1. 在场景中配置纹理、粒子寿命、发射方式和基础参数，打开命名动画并新建或选中动画。
2. 在原层级选目标，用共享 TL 原菜单添加轨道、属性、关键帧、片段或分组。
3. 未录制时，原检视和变换工具修改基础参数，沿用场景原历史。
4. 开启目标轨道的原生录制后，原检视与变换工具同时修改基础值并记录当前命名动画关键帧，与普通 TL 一致。
   进入录制冻结当前显示姿态；未作编辑不能因恢复基础值而误写关键帧。结束录制恢复正常采样与所有权保护。
5. 命名动画时长选择“自动”时跟随完整制作内容，包括原轨道末端和命名事件标记；空内容使用默认时长。
   关闭自动后使用固定播放时长，整套内容按比例重定时。制作范围是只读信息，播放时长不是内容裁切点。
6. 新动画默认遵循原模块 enable；需要扩展代为开启时勾选“自动启用模块”。旧定义读取时继续开启该选项。

原时间轴、每个命名动画及项目测试时间轴分别保存播放头、缩放、滚动、行高、曲线展开项、选择、标记显示、EndMode/Wrap 和预览范围。
切换前结束录制与拖拽，恢复后只清理失效引用；普通值编辑保留仍有效的轨道、属性和关键帧身份。
基础参数编辑和撤销重做沿原场景重模拟，保留命名定义、编辑状态与游标。
恢复原场景时从起点重放到进入动画前的时间，再恢复播放或暂停；避免先 reloadEffect 发射又 simulateTo 再次发射。
活动上下文只由实际 owner.timelineView 与工作区映射确定；已移除无调用的 PhotonAnimationTimelineView、Recording 和 Transform 代理，原生录制及 InspectorSession 保留。

拖动播放头仍调用原场景 requestSimulateTo / simulateTo，重放发射、激活、控制和粒子演化；回拖或基础值变更可重新生成预览粒子。
完整重放期间静默外部音频、executor 回调和全局信号监听，内部 START／MARKER／COMPLETE／SIGNAL 及子片段仍按经过区间执行。主制作实例按 authoring 身份隔离默认动画与启动事件，后续子实例保留正常启动编排；命令等待重模拟结束执行。
被动 UI 同步使用可空运行态查询；结束或重建期间跳过，运行态身份改变时显式重新绑定。
“结束特效”后保持结束，屏幕 tick 不自动重建；只有用户主动播放、重新开始或重置预览才恢复。
底部共享 TL 负责制作时间、播放、停止、EndMode 和 Wrap。中央重复播放栏与状态栏已移除。
右侧“动画检视 → 命名动画 → 运行动画测试”通过“测试动画”独立选择运行目标，提供“继续／暂停”切换、重启、停止三个动画按钮，以及临时时长和结束整个特效；该目标不与左侧制作选择绑定，未选择制作动画时也可操作。
当前测试目标处于 PLAYING 且宿主场景运行时显示“暂停”；目标未运行、已暂停或宿主停止推进时显示“继续”。被动刷新只在动作变化时更新原生按钮文字与对应提示，不重建测试实参或新增监听。
已有测试动画的“继续”应用临时实参并恢复当前实例进度；没有该实例时按配置时长启动。依次选择并播放其他测试目标可组合运行，暂停、继续或停止仅处理当前测试目标，不移除其他动画的贡献。
动画游标只读；临时时长与播放状态被动刷新，输入焦点期间不覆盖文字。临时时长不写入定义，制作游标与独立运行时长不合并为同一字段。
命名模式隐藏场景信息中重复的重新开始、播放和时间输入，保留种子、性能与对象信息；返回场景恢复原显示状态。
底部 EndMode/Wrap 只控制编辑预览，不改写命名动画的循环与 HOLD/RELEASE 配置。

临时播放、测试实参和视图状态不写入资源；命名动画定义修改仍保存到项目、.fx、FXPack 和原操作历史。项目专用测试时间轴的内容单独保存于 .fxproj，不导出到 .fx 或 FXPack。
FXData 序列化时临时释放预览贡献并在完成后恢复，基础变换不会保存成合成姿态。
动画模式继续保护对象新增、删除、重排、重命名与可见性；原检视范围内的合法配置复制/粘贴及基础编辑可用。
对象编辑历史登记原来源与回调，禁止在动画模式撤销进入前的对象结构操作。
关闭或切换项目恢复共用场景并清理辅助面板、上下文及监听，不销毁原粒子管理器。
这些实际窗口、录制和历史交互仍需客户端验收，隔离模型检查不能替代。

### 项目专用组合测试

取消左侧动画选择后，共享 TL 显示独立测试定义。仍可使用原生轨道、片段、曲线、标记和录制，原场景时间轴及命名动画各自的内容不被替换；测试定义拥有独立 UUID，默认自动时长且不自动启用原生模块。重新选择命名动画后恢复其制作上下文，测试内容继续保留。

测试定义属于 FXProject，保存位置为 `.fxproj` 的 `data/lobecorp_animation_preview`，与 `data/fx` 同级，采用 `PhotonAnimationDefinition.write/read` 并记录当前版本 6。旧工程没有该字段时首次使用创建；关闭动画工作区后由工程继续持有，重新打开工作区或保存加载可恢复内容。编辑历史同时比较和保存命名库及测试定义，因此只修改测试 TL 也会提交历史；撤销重做恢复有效轨道、属性和标记引用，空选不会自动跳回默认或第一动画。
离开当前制作上下文前，prepareSwitch 先结束录制与片段拖动，再将当前 TL 投影提交到所属定义和历史，最后保存游标与视图。动画选择在改变 selectedId 前执行，退出命名模式由 suspend 执行，新建、复制及删除经 editSelection 进入同一切换边界；内容没有变化时不添加历史，也不重建正在离开的视图。普通载入与撤销恢复不回存旧投影，避免覆盖已恢复的定义。

该定义没有加入控制器 definition 或 `library.animations`，外部不能按动画名调用它。`.fx`、FXPack 和子实例模板只序列化／复制 FXData，因此不包含项目测试字段；无需导出时临时删除内容。普通场景 Timeline、命名动画默认入口和运行配置继续按原格式保存。

组合测试步骤：点击动画列表空白；在右侧“命名动画 → 运行动画测试”选择第一个“测试动画”，按其类型和范围输入临时“测试实参”后重启；切换测试目标并启动第二个动画。测试目标切换不会改左侧制作选择或停止已运行动画，可分别暂停、继续或停止目标查看叠加结果。测试实参只属于工作区，在重启或继续时传入，不修改参数默认值或保存资源；结束整个特效与停止单个动画仍分开。

组合测试的手动播放与底部 TL 时钟分别控制。TL 回拖、普通编辑及上下文切换需要重模拟时，先冻结手动目标的时间、循环次数、暂停／保持末帧状态、启动顺序、播放时长和类型实参；只推进制作 TL，再按稳定 UUID 恢复手动组合，不补发过去的启动、标记、完成或原生信号。重模拟期间的操作按点击顺序排队；暂停或停止一个目标不会取消其他目标待恢复的宿主运行。与当前制作动画 UUID 相同的目标交由 TL 控制，不恢复为第二份普通播放。

右侧组合运行时，底部 TL 的播放按钮启动 TL；只有 TL 主动播放时才执行其 EndMode／Wrap 结束处理。单纯右侧测试不会因有限寿命对象结束而被 TL 自动循环重启。非 TL 播放状态下回拖从 0 原生重模拟，避免粒子管理器时间与独立制作时间混用。显式重置预览、结束特效、退出命名动画模式或关闭工作区会清除手动播放恢复状态；退出模式后恢复普通场景，不跨退出恢复过期子实例状态。

这些播放快照仅在工作区内存中存在，不进入项目保存、`.fx` 或 FXPack；项目专用测试 TL 的制作内容仍按上述字段保存。原生场景重置会重建粒子与子 FX 实例，恢复的是手动动画贡献、实参与播放状态，不承诺保留旧粒子年龄或子播放句柄；真实子实例与 GPU 行为仍需游戏验收。
空选测试 TL 现在可在原生“添加轨道”菜单中新建“动画调用”轨道，添加片段后选择本特效的命名动画；片段复用子动画的实参表单，父形参来源为当前测试定义。轨道头显示“本特效”，不选择发射器发射器目标，默认使用独立触发，也可选择受控播放。独立触发启动的动画继续使用自己的时长，受控播放将完整子内容映射到片段长度；暂停、完成及停止沿用子动画的具体句柄／所有权规则。

该入口只显示在命名动画模式的空选测试上下文，轨道采用 PhotonAnimationCallTrack，复用 ChildTrack 的 Codec、原生复制和片段编辑，不新增第二套时间轴或播放器。原生类型仍全局注册以保证保存后恢复具体类型，但运行时再次校验来源必须为当前编辑预览的 timelinePreview，且来源 UUID 不在动画库中；复制或异常数据不能借此在可导出的命名动画或正常世界中建立本特效自调用。测试 TL 的调用片段随工程字段保存，不加入 .fx、FXPack 或子模板。

高速经过事件和编辑预览零时刻爆发已接入实现；具体范围、默认值及防重复处理见 [时间轴经过事件与零时刻预览](photon-editor.md#高速播放经过事件与零时刻预览)。激活轨道不再提供独立单帧选项，仅开始或仅结束就是对应边界的一次命令；仅结束可停止循环生成并保留已有粒子。编译、Data 与游戏验收分别按下文当轮记录，不混用历史结果。

前轮实现检查（历史证据）：相关 Java 已通过 IDEA 范围格式化及诊断，编译成功且 problems=[]；在 Data 已加载环境实际执行 PhotonAnimationChecks.runAll()=5679，包含新增项目 ValueIO 保存往返、导出排除、测试 TL 模型隔离、快照撤销重做与有效引用恢复、并行合成和句柄归属检查。Data 最终 BUILD SUCCESSFUL in 1m 54s，19 个生成文件数量不变、written=2；中英文 JSON 各由 847 增至 853 键，仅新增 6 个测试入口与提示键。该轮结果不作为后续重模拟修复的检查证明；游戏内空白取消选择、组合预览、滚动／焦点、录制及窗口关闭恢复仍需另行验收。

## 已修复问题与维护要点

2026-10-08 激活单边命令与提示：

- **单边点被原生区间提前接管**：原 evaluate 的 Activator 统一按区间写 active／visible，命名状态协调也未看点是否到达。原生区间阶段现在仅含双端 Activator，TimelineState 按各播放器制作时间调用 controlsAt；单边点前不抢所有权，拖回点前或关闭轨道时恢复基础状态。命名和场景都沿原播放器执行，不新增播放系统。
- **停止循环被持续区间抵消**：扩展原来对多条激活做持续 OR，停止命令与仍活跃的轨道并存时可能被重新开启。普通双端轨道仍保留 OR，单边最新命令按时间／叶顺序覆盖，后续真实区间入口或开始点恢复。STOP_EMISSION 保留原粒子、更新和可见，CLEAR 不能被已经失去优先权的旧开始点阻止。
- **标记画出后命中位置仍错**：只改纹理或初始布局会被原 registerClipView 的重新定位覆盖。视图登记回调、组拖动、引导线与初始布局统一按有效边界设置几何；只用 MOVE 复用原生组选择、历史和拖动，保留存储 duration 与原碰撞约束。原检视布局不新增样式，独立单帧字段移除。
- **说明重复且部分不换行**：旧配置组和字段可同时挂提示，漏包装的设置、工具及列表仍走未限制宽度的文本。配置说明统一放到原帮助放大镜，普通按钮采用缓存的原生 Tooltip 分行；按 WrappedTooltip 自身监听器识别避免重复，不以已有其他监听器作为跳过条件。提示正文仅保留用户操作和有效限制，输入校验仍沿原入口。

2026-10-08 高速事件与项目动画调用：

- **时间步长跨过整段**：原生当前时刻采样可能跳过短片段。补处理只收集离散起止边界，复用原 evaluate；精确 localTime 在补采样前后保存恢复，tick 不调用 frame。begin 清空经过时间，内部重入不再次遍历；不能借此复制原控制、音频或后处理实现，也不能把原信号区间派发当作所有轨道都已支持跨越。
- **单边与区间语义必须分开**：startControl／endControl 同开时为原区间，异或为对应起点／终点命令，皆关不接管。旧 singleFrame=true 转为仅开始，新写入不保留该独立字段；复制、历史、读写和检视保持两端开关。前一轮单帧默认 false 的描述为历史行为，以此处为准。
- **项目测试调用不得泄漏到导出运行**：菜单只向空选测试 TL 提供动画调用，原生注册只承担 Codec／复制恢复。运行时以当前编辑预览播放句柄和不属于动画库的测试 UUID 再校验；片段仍使用 ChildWindow、形参和所有权，不以名称误停后来重启的播放。测试定义只写入 .fxproj 的既有专用字段。
- **同一特效求值中播放或停止会重入**：调用本特效动画会在原生求值期间安装或释放播放贡献。apply 重入仅标记 pending，外层完成当前 pass 后再采样新贡献；受控完成排入 afterApply，在新贡献采样完成后处理。批量替换预览的 dispose 不在中间反复 apply；遍历使用稳定快照及具体句柄／token 校验。维护时覆盖零长度窗口、同名重启、受控保持／释放和预览关闭，不仅以“Map 已取快照”判定混合器作用域安全。
- **零时刻显示不等于提前模拟**：Burst 0 的编辑器初始化与待加入队列接入不推进时钟、年龄或持续速率；首次正常窗口跳过已消费的零触发，预热与正延迟仍遵循原规则。该入口限定编辑器场景，不自动改变普通世界发射。提前抽样可能改变随机调用顺序，种子图像一致性与实际渲染需游戏内核实。

本轮源码、菜单和设置说明已同步；两个新设置 timeline_event_traversal、zero_time_emission 分别位于 Lobecorp 的粒子与渲染、预览分类，默认及缺省开启。Java 诊断、编译、Data 与加载后检查按下文实际记录收尾，不能将后续步骤视为已通过。

2026-10-08 空选测试时间轴切换前提交：

- **用户反馈空选测试时间轴切换后消失**：源码确认旧 suspend 只保存视图，selectAnimation 改变制作选择前也没有统一提交当前 TL 投影；普通已完成的原生编辑仍沿历史入口提交，未发现其必然漏路由，不能把上述切换缺口认定为截图现象的唯一游戏内根因。新增离开上下文前的显式提交防护，实际复现与修复效果仍待游戏内验收。
- **提交必须保持旧定义所有权**：Context.prepareSwitch 在当前 active 上下文结束录制、取消片段拖动，Workspace.commitTimelineChanges 先 storeModel 再以 refresh=false 提交历史，之后保存游标与视图；选择切换必须在 selectedId 改变前完成，暂停上下文必须在 active=false 前完成，新建／复制／删除统一通过 editSelection 先准备再编辑。维护时同时覆盖空选测试 TL 与命名 TL、切到场景再返回、关闭重开，以及未结束录制／拖动；不能先换选择后把旧投影存入新定义。
- **普通载入和撤销不能回写旧模型**：loadSelected 继续只从当前定义载入；提交限定在显式离开上下文的 prepareSwitch，不放到所有 loadSelected 或刷新入口。push 同时比较命名库与工程测试定义，只在实际内容变化时入历史，refresh=false 避免切换前重建视图；维护时检查撤销、重做及无变更切换，不能为保留内容反过来覆盖刚恢复的数据。

上述两类已通过 IDEA 修改行范围格式化；通知初阶段 12 个 Java 编译及 Data 32s、written=2 保留为阶段证据。通知设置补全后最终整合 13 个 Java 经 IDEA 编译成功，isSuccess=true、problems=[]；最终正常 Data exitCode=0、BUILD SUCCESSFUL in 21s，6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=0。相对通知起始仅语言资源及两个语言缓存变化，两语言各共新增四项通知相关键，其他生成资源保持。未新增或运行测试，实际切换、录制／拖动、历史和关闭重开交互仍未验收，不把编译或 Data 作为用户丢失现象已消除的游戏内证明。

2026-10-08 控件合并、长提示换行与复制名称：

- **继续与暂停分别占用按钮，重复“动画”后缀挤占空间**：测试组原来同时构建重启动画、暂停动画、继续动画、停止动画四个按钮。现按实际目标播放状态和宿主推进状态切换“继续／暂停”，后面保留“重启”“停止”；底部共享 TL 继续使用原生播放切换，不增加另一组播放控件。维护时必须同时检查目标和宿主，不能仅按目标时钟 PLAYING 显示暂停，也不能每 tick 重设文字、提示或重建配置组。
- **空选说明与长悬浮提示被遮挡**：空选“测试时间轴”使用未显式换行的 TextElement，扩展提示也仅把整段 Component 交给原样式。说明正文改为父宽度内原生 WRAP 和自适应高度；动画按钮、配置器及已登记扩展配置行的提示由 PhotonEditorTooltipUtil 在本地 HOVER_TOOLTIPS 提交原生 HoverTooltips，复用原版 Tooltip 的 170 像素自动分行和语言缓存。维护时区分正文布局与悬浮文本，不在翻译内容中按字符数插入换行，不改全局提示钩子、原提示图标或皮肤；动态动作提示读取当前消息，只在消息变化时更换缓存。
- **复制动画弹窗丢失名称**：旧 duplicate 使用空字符串作为输入初值，复制后必须重新输入原名。现从选中动画名称生成唯一预填名称，冲突时追加序号，仍由原弹窗确认、名称检查与定义复制路径完成；不改原动画名称、UUID 重映射或资源格式。

本轮 7 个 Java 经 IDEA 完成修改段范围格式化，关闭导入优化与成员重排，errorsOnly=false 诊断均 errors=[] 且未超时。首次编译等待超时，第二次调用被自动审批误按小改动规则拒绝；重新核对项目大型修改规则及 7 文件范围后，IDEA build_project 限定本轮文件编译成功，isSuccess=true、problems=[]。正常 IDEA Data 退出码 0、BUILD SUCCESSFUL in 26s，6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=2。
中英文生成资源各由 853 增至 854 键，仅新增 photon.lobecorp.animation.copy_name；各更新继续、暂停、重启、停止四个短名称及 copy.tooltip，其余键与文本完全保留。25 个生成文件包含 6 个缓存，前后仅两份语言文件及其两个缓存哈希变化，其余 17 个资源与 4 个缓存一致，无删除；生成输出未手写或格式化。本轮未新增或运行测试，不把前轮 5745 项回归记为本轮重跑。
预分行后提交 FormattedCharSequence 可能跳过第三方 GatherComponents 对原 Component 文本的处理，仅本项目包装提示使用该路径；其他提示仍沿原入口，兼容性未运行验收。实际按钮切换、长提示边界、窄面板正文、截图遮挡和复制弹窗仍需游戏内验收。提示／按钮阶段的 IDEA Git 为 dev、238 条变更（121 暂存、197 未暂存、0 未跟踪、0 冲突）；后续通知新增 3 个 Java 被 IDEA 自动加入索引，暂存增至 124，原有 121 项保留，没有手动暂存或取消暂存、创建备份、提交或推送。

2026-10-08 组合测试重模拟与时钟隔离：

- **重模拟期间连续播放只剩最后一次请求**：旧 afterSimulation 只保存一个 Runnable，后点击目标覆盖先点击目标。现使用 FIFO 队列，重模拟完成后依次执行；命令再次启动重模拟时保留尚未执行的尾部，显式重置／结束／退出模式／关闭时清理。维护时须覆盖 A＋B 连续请求、命令引起第二次模拟、取消后的队列和单目标暂停／停止，不能只检查一个延迟播放。
- **回拖或编辑清掉手动组合**：原生 SceneView.simulateTo 的 reset／emit 会重建运行态，prepareTimelineSimulation 的 previewOnly 又释放全部贡献，单纯恢复 TL 游标无法保存右侧组合。现重建前按手动目标集合采集 PreviewPlayback，冻结普通命名播放；重模拟后通过新库 UUID 恢复时钟、启动顺序和独立实参，不引用旧 Runtime 或旧句柄。受控子片段和制作 TL 由原生重模拟重建，不进入手动快照；同制作 UUID 由 TL 控制，删除或签名不兼容的目标单独丢弃，不破坏其他组合。
- **恢复时重复派发过去事件**：从 0 重新 play 会重复启动及已经过的标记／完成，原生播放器首次同步也可能补发信号。恢复使用原播放器和混合器的瞬态恢复路径，保持完成事件状态并对首次原生采样采用静默同步；后续正常经过区间仍可触发新事件。快照只保存播放数据，不保存或恢复事件队列，也不能为恢复姿态重播过去的外部声音／回调。
- **右侧测试被 TL 自动结束或误当作 TL 播放**：原生 updatePlaybackEnd 以宿主粒子管理器 isPlaying 判定，右侧手动播放也会满足该条件；seek／播放切换又将全局运行状态当作 TL 状态。现 EndMode／Wrap 仅在主动 TL 播放时生效，恢复宿主运行与恢复 TL 播放分开；非 TL 回拖从 0 原生重模拟，不能把手动组合推进后的宿主时间当作制作游标。
- **显式清理后旧组合复活**：重模拟恢复状态只属于一次工作区恢复，重置、结束、退出模式和关闭必须同时清理目标集合、快照及恢复标志；不得写入资源或在被动屏幕 tick 中自动重启。原生 reset 重建粒子及子 FX 的限制须继续单列游戏验收，不能把父动画状态恢复称为旧粒子实例保留。
- **Data 回归夹具误用客户端音频**：setPreviewDispatch(true) 会重新开启根播放器音频，而 Data 环境没有 Minecraft 客户端。事件检查夹具每次启用事件派发后再关闭音频，保留生产播放行为；维护时不能假定前一次关闭音频会覆盖后续派发开关，也不能为无客户端夹具增加生产音频禁用逻辑。
- **回归夹具比较或修改错误库实例**：Controller.library 保存的是 NBT，Runtime 构造时重读为独立库；夹具原先比较外部定义对象身份，并修改外部库来模拟签名变更／删除，实际运行库没有变化。现断言稳定 UUID，并操作运行态实际定义；不为测试添加生产 getter。维护时先核对序列化及运行库所有权，不能把外部模型副本当作运行实例。

本次相关 Java 已完成 IDEA 修改行范围格式化，关闭导入优化与成员重排，诊断未新增问题；最终重新编译返回 isSuccess=true、problems=[]。前两次回归因上述夹具问题未通过，虽然各次 Data 任务自身成功，也不作为全量回归通过。修正夹具后，第三次 Data 已加载环境实际执行 PhotonAnimationChecks.runAll()=5745，全部通过；最终 Data 为 BUILD SUCCESSFUL in 1m 27s，6 个 provider 完成，HashCache total／old／new 均为 19、removed stale=0、written=0，19 份生成资源 SHA-256 前后完全一致。当前 Minecraft 客户端未运行，桥接 connected=false；实际窗口、声音、GPU 及子实例交互未验收。PhotonAnimationPreview 中既有未使用 inspectTimeline 重载警告只记录，本次不扩大删除范围。

2026-10-08 独立测试时间轴：

- **空选后时间轴清空或恢复时自动补选**：旧 Context 把 null 传入 TimelineModel.load 并跳过存储，Workspace.restore 又将 null 补成默认／首动画；无选择时多处播放、检视和完成判断也提前退出。现 `editingDefinition()` 在空选时返回项目独立测试定义，以其 UUID 切换模型、视图和时钟；撤销恢复只清理无效的非空选择，不把主动空选改成其他动画。维护时要区分“没有命名动画制作选择”与“没有可编辑时间轴”，不能重新用 hasSelection 阻断通用 TL 编辑。
- **测试定义有播放实例但不能暂停／停止**：旧运行方法先通过 library.find(name) 再查播放表，项目测试定义不在库中，具体句柄 stop 也回调名称查找而失效。现增加 UUID 播放查询及具体播放句柄暂停、继续和停止，操作前校验当前归属；不把测试定义塞进真实动画库。维护时必须覆盖库外定义、同名重启后的旧句柄及停止后剩余贡献，不能仅测普通按名称播放。
- **列表下方空白未取消选择**：LDLib2 .39 ScrollerView 在视口与内容间还有 flex 包装层，命中检查遗漏该层会使短列表下方空白点击失效。现白名单包含包装层，继续排除行内控件、按钮、滚动条和拖动；真实点击仍待游戏验收，维护时应查当前依赖的 hitTest 与容器结构，不假定空白只返回 viewport。
- **只改测试内容不进入撤销历史或混入导出**：旧快照／变更比较仅看命名库，项目测试更改会被判为未变化；放入 FXData 或控制器则会随 .fx 与子模板输出。现快照包含测试定义及引用身份，保存限定在 FXProject 的独立 ValueIO 字段，原导出门禁保留。维护时分别核对项目保存与 FX/FXPack 序列化边界，不用导出时临时删对象掩盖所有权错误。

2026-10-08 子动画与独立形参：

- **父暂停后受控子音频继续播放**：原 Runtime.pause 只静默父 NativePlayer，受控子时钟仍由父进度驱动且保留 PLAYING 状态，音频判断没有父暂停门控。现由 ChildAnimationPlayer.pause 将暂停状态传到仍属于当前 token 的受控句柄，Playback 保存 controlAudioPaused，NativePlayer.silence 递归静默子播放器，updateAudio 同时检查该标记。独立触发不参与父暂停传播；恢复时按原父派发策略重新启用音频。防回归须分别检查受控、独立、同名重启后的旧 token 和嵌套子片段，不能只断言父时钟暂停。
- **旋转倍率使用折回角度**：原生属性先写入 Quaternion，再从 Transform 采集 Euler 会折回主值；0→360° 到 75% 的 270° 会变成 -90°，乘 0.5 得到 -45°。现仍执行原 AnimatedProperty.apply，仅 CURVE 来源且 angular 属性的贡献值重新使用 property.sample(time) 保留原曲线角度，再计算循环偏移和倍率；其他属性继续使用原生采集值。防回归检查 0→360°、非单位倍率、跨周期、负向旋转和停止恢复，不能先把旋转折回再缩放。
- **静默重模拟漏掉内部编排或后续子实例**：静默作用域覆盖完整 simulateTo，内部 START／MARKER／COMPLETE／SIGNAL 和子片段照常运行，外部 executor、全局信号监听及音频分别关闭。主制作实例的 authoring 状态按 FXRuntime 身份隔离，后续子实例不继承主实例 previewOnly。维护时不能用一个总开关同时关闭内部规则与外部表现，也不能只静默首次 emit 而漏掉延迟发射。实际游戏音频和拖动交互仍待验收。
- **独立子片段显示无效结束行为**：运行层只允许受控模式覆盖子动画完成行为，原检视却在独立模式也显示该字段。现仅 CONTROLLED 显示 child_completion，INDEPENDENT 保留独立播放时长，沿用子动画自己的完成行为；不能把未被运行层使用的选项展示为有效配置。IDEA 修改行格式化及诊断已完成，真实窗口切换仍待游戏验收。

2026-10-07 原生参数控件与数组列表：

- **整数取整越界**：旧逻辑先按浮点范围限值再 round，例如 [0.2,0.3] 会得到范围外的 0。现先计算可表示的有效整数区间，空区间拒绝；输入和旧文件统一遵守 float[] 的精确整数硬边界，不能承诺完整 int32 精度。
- **类型输入不匹配**：旧参数页全部使用普通数字字段，布尔和颜色也按分量输入。现复用原生开关、颜色选择器和带类型/范围的数字字段；RGBA 精确输入仍保留，仅读取颜色时不能回写 8 位预览结果。
- **原生数组重建误选或打断操作**：ArrayConfiguratorGroup 的 forceUpdate 按序号替换，不能用于恢复模型选择。现保持显式事务刷新，FormState 按条目内层分组的 UUID/role 恢复选择；删掉的项不跳到同位置其他项。颜色选择器挂在 UI 根，焦点保护必须沿祖先检查 ColorSelector；原生拖动未结束也延迟表单重建。
- **IDE 文件保存受占用影响**：本轮 DataUtil 的补丁写入遇到 Windows 映射区域占用，工具报失败但文件已部分写入。按修改前内容核对并通过 IDEA Document API 恢复原文件尾，再检查诊断。工具写入报错不能假定文件完全未变，应立即读回并核对完整内容，禁止用命令行绕过。

以上为源码与编辑器确认；实际游戏内颜色弹窗、数组拖动及布局仍待验收。

2026-10-07 组合动作与编辑状态：

- **按钮后表单/列表状态丢失**：showPage 原先直接清空重建配置器，Outline 重建新的 ScrollerView，TimelineContext 恢复时清空所有选择。现分别保存原生折叠/滚动值，并仅剔除已失效的 TL 选择。规则、动作、输入、参数和标记按临时模型身份识别，撤销快照保存身份令牌；删除前项或重命名不能靠行号/显示标题套用旧状态。界面身份不进入 NBT，关闭工作区清理缓存。
- **模式切换抢选标签**：Panels.setActive 曾显式选择动画列表、动画检视与 TL，并在返回时恢复其他标签。现只保证辅助 View 安装，保留用户当前页签；显式选择对象后的原检视跳转仍保留，不能一并移除。
- **跨资源引用被当作父引用**：组合动作的 spawner 属于父对象，target 动画/对象和输入 target 属于子资源。复制只映射父 spawner；父动画删除、参数改名/改类型只处理 SELF 的目标，参数来源名仍随父参数更新。删除父参数会移除 SELF 目标映射/设置动作，清空来源回到已保存的固定值，不删除子资源同名目标。项目模板过滤只剔除指向复制范围外 spawner 的子动作，不用父对象表过滤子 target。
- **局部参数在完成后丢失**：RELEASE 会先移除播放实例，再处理 COMPLETE。事件现在保留其来源播放实例，完成动作仍可读取该次播放的局部参数。后续规则按触发时读取并复制输入；不要只在派发时从当前 playing 表反查来源。
- **无效输入破坏已有动画**：PLAY 在释放旧实例前验证新输入并构造新播放；CONTINUE 验证后更新局部参数并保持进度。TRIGGER 的多项参数先全部验证再提交，避免半套更新。子实例只隔离参数/状态类异常，不吞掉任意 RuntimeException。
- **示例入口混入制作表单**：已移除“魔法阵动画示例”按钮及其表单动作入口；示例构造代码保留，未删除独立测试入口。

以上是源码确认的根因和实现，实际游戏内点击、折叠/滚动恢复和子实例播放仍待验收。

2026-10-06 场景与命名动画隔离：源码确认 SceneView.play/simulateTo 的 emit 会重新创建可自动启动的命名控制器，返回场景时 Preview.restoreScene 也直接重建；下一次 tick 因默认动画或 START 规则改变场景，单独 previewOnly 还会保留命名保活与规则入口。
现在通过 RuntimeAccess.setScenePreview 给 SceneView 的主 FXRuntime 设置持久预览标记，场景模式立即关闭旧控制器并禁止 reload/emit 重建；命名模式只解除标记，再由已有预览路径显式创建控制器。
模式必须在首次 emit 的原生 t=0 信号之前设置，后续原 TL 循环的直接 emit 沿用实例标记；play 不额外重置所选动画，prepareTimelineSimulation 仍只用于制作重放。
不要按 DummyWorld、粒子管理器或执行器类型全局禁用：发射器发出的子 FX 与游戏中的实例各自保留正常默认动画及触发能力。标记不进入项目或 .fx 序列化，也不新增基础动画播放器。
TimelineRegression 增加场景门禁、原回调/保活身份恢复、未标记实例、显式命名预览、原 TL 动态基准与 ADD/MULTIPLY 停止恢复检查；Data 夹具调用真实 reload/begin 链，不发射客户端粒子，实际 SceneView 切换和渲染仍须游戏内验收。
本轮实际 IDEA 编译与 Data 成功，加载完整 runLoaded=4812（新增 52 项），生成中英文各 729 键且文本未变；代码诊断无新增问题，保留修改前已有的 Preview.inspectTimeline 未使用重载提示。未运行 Client 或实际子发射器，游戏内待验收范围不变。

2026-10-06 去重整理：

- **多个播放入口绕开上下文**：中央命名运行、TL 制作预览和 SceneInfo 直接重模拟的作用域不同，重复按钮和可编辑时间造成操作歧义。制作预览保留原 TL，独立动画运行集中到既有监视组，命名模式隐藏 SceneInfo 的两个操作节点。节点顺序已核对当前 Photon initBasicInfo；升级依赖时重新确认，而非按截图猜索引。
- **继续与恢复不等价**：play(false) 会切回运行计时，直接用于已有制作预览实例会丢失游标/epoch 语义。统一按钮先查当前实例，已有时 resume，缺失时 play(false)；触发规则的 CONTINUE 与 RESUME 协议仍独立保留。
- **播放字段与状态停止刷新**：普通 NumberConfigurator 的 forceUpdate=false，旧状态栏也依赖场景正在播放。运行字段改为带焦点保护的被动刷新，动画时间只读；状态区每个 UI tick 从可空运行态读取，结束后显示结束而不自动重建。此修复不包含参数定义页的临时参数字段，后者仍列为独立遗留问题。
- **常规 tick 重复求值**：首轮 apply 后只在 RELEASE 移除贡献或事件/规则可能改变运行态时再合成，关闭后不继续 apply；启动 FINISH 同样提前退出。原生信号前的分层 commit 与最终 commit 承担不同职责，继续保留，不把必要顺序当成重复删除。
- **回退采样另写属性遍历**：没有完整原生播放器时，AnimationTrack.sampleInto 置于原采样作用域，复用原属性 apply 多态链；时钟进度/循环计算和优先级排序也由单一入口负责。原制作预览时钟与运行完成协议仍区分。
- **测试目录重复**：菜单和 runLoaded 共用 PhotonAnimationChecks；菜单逐项报告失败，调试入口 fail-fast，既有断言保持。旧无调用代理和源码 .java~ 已移至根 backups 的原路径，删除前经 IDEA 读回文本核对，不格式化备份。
- **信号回归夹具未接宿主**：新增检查首次失败为 expected=2、actual=1。夹具直接 new Runtime，原生信号却经 FXRuntime RuntimeAccess 持有的动画控制器转发，因而规则没有入队。改用 reloadAnimations/animations 建立真实宿主归属，保留采样次数、信号前贡献及同 tick 参数结果的强断言；生产门控未因此修改。只验证本地 trigger 的夹具不能证明原生 SIGNAL 转发，需覆盖实际宿主挂接。
- **待核实翻译**：validation_result、timeline_panel、horizontal_scroll、property、add_track 与 editor.features 暂保留。源码检索无调用不足以排除二进制 UI NBT 或动态引用，普通 IDEA 文本接口无法读取这些 NBT，不能据此宣称可安全删除。

本次共享视图整合修复：

2026-10-06 19:42 客户端报告再次确认状态翻译参数崩溃：去重提取 Workspace.animationStatus 时遗漏此前问题列表的字符串转换，点击示例后重建表单以及后续屏幕 tick 都把 List<String> 直接传入 status_format，空列表同样非法。
本次统一经 PhotonAnimationUiUtil.animationStatus 构建五个参数，在显示边界把 problems 转为 String；状态继续使用翻译 Component，时间、进度和循环数仍为数字。不吞异常、不修改动画运行或翻译资源，也不改 SceneInfo 生命周期。
GuiRegression.runLoaded 直接调用该生产显示入口，覆盖四种 State 的空/非空问题列表，新增 120 项断言检查原生允许的参数类型及显示数据。独立未加载环境会跳过 NeoForge 的开发校验，不能用编译或未加载组件构建代替；提取 UI 显示时必须保留类型转换并测试实际生产入口。
IDEA 编译成功，加载阶段完整 runLoaded=4760（原 4640+120）；正常 Data 退出码 0，中英文生成文本各 729 键且与本次运行前完全一致。三个修改 Java 与 Forms 调用检查及文档按实际诊断记录收尾，代码仅格式化修改行，未手改生成资源。本次未重启 Client 或复现游戏内点击，完整界面仍待客户端复核。

- **被动同步访问已结束的运行态**：严格 runtime() getter 在结束／重建期间抛出异常。同步改用 runtimeOrNull，身份变化时重新绑定；结束后不在 tick 中自动复活，不捕获任意异常掩盖问题。
- **第二 TL 实例造成上下文与线不一致**：所有原模型读取、播放头、工具、历史及录制路由到同一个 owner.timelineView；通过上下文切换，不再用旧视图子类辨识命名模式。
- **原检视与录制语义被代理截断**：对象直接走原完整 Inspector，仅锁 FXObject.name；原基础修改、回调与历史保留，原录制双写复用。构建后显式选择原检视标签。
- **制作时间取整或被播放时长裁切**：每个播放器持有完整 Timeline，以隔离的 double evaluateAt/frameAt 进入原执行阶段，普通属性通过真正的 AnimatedProperty.apply 多态采样。自动／固定播放时长映射整套制作范围。
- **后处理重复提交**：tick 不调用 frame，只有实际渲染帧提交各播放器的后处理；录制冻结当前姿态，不因释放贡献误记基础值。
- **对象撤销快照固化预览姿态**：原 SerializableRecordAction 直接序列化对象／Transform，绕过 FXData 出口。构造基线和 updateSnapshot 的实际 serialize 调用均以当前运行态 serializeBase 保护；手动改值按所有权保留，原历史及恢复回调不替换。
- **录制未结束时保存遗漏关键帧**：原 pollRecording 修改命名模型但控制器 NBT 未发布。录制自动刷新标记脏模型，poll 返回时仅 storeModel/stageRecording；不结束录制、不推额外历史、不改已保存快照边界。
- **录制自动重放覆盖手动姿态**：同游标 refreshPreview 重建运行态会丢失冻结所有权；录制自动刷新延后重模拟，只发布定义。主动挪播放头仍走原 seek，结束录制后恢复正常场景重放。
- **动画行高亮被覆盖**：名称改只读 Label，选择行为与视觉皮肤分别验收；不能只从遮挡推断点击失效。
- **旋转一致性检查误报反向**：SHORTEST 的原生四元数经主值 Euler 捕获再重建后，可从 q 变为整体反号的 −q，物理旋转相同。Data 已加载调试确认 0.25 tick 的 Z/W 同时反号且 abs(dot)=0.99999994。回归改为比较旋转后的三个基向量，保持精度并能识别真正反向；不因四元数逐分量不同改写生产旋转逻辑。
- **停止旋转未恢复基准**：新增停止恢复断言在 1.25 tick 失败，基准 X 应为 1，实际为 0.99323838949。Property 原比较写入前 Euler 与四元数回读主值；350° 回读约 −10°，或往返产生浮点差异时会误判失去所有权。修复记录写后实际 captureLive 指纹，查询仍保留合成值；旋转基准与临时原生采样恢复用真实四元数快照，避免 Euler 往返改动基准。不得以放宽相等容差代替所有权修复，函数槽仍按原对象身份判断。

以下是此前修复与根因记录，涉及独立视图／录制代理的旧描述以本次“制作与预览”为准：

- **播放动画时状态栏导致客户端崩溃**：2026-10-06 的 Ticking screen 报告确认 status_format 将 runtime.problems() 的 List<String> 直接作为翻译参数，空列表也触发 IllegalArgumentException。锁定 Minecraft 26.1.2 的 TranslatableContents 在开发环境仅接受 Component、Number、Boolean 和 String；现将该列表转为字符串后传入，保留状态栏显示和运行问题查询接口。后续集合、枚举、null 和复杂对象必须先转换为合法显示参数，不能因编译或 Data 成功就认为动态 UI 调用有效；游戏内播放尚未重新验收。
- **动画列表只剩细条**：源码分析发现旧布局给外层 ScrollerView 留出固定高度，却让内部 TreeList 同时使用 flex 填充和禁止收缩；滚动内容没有明确高度，存在无法推出内容高度的布局风险，尚未通过运行时测量确认唯一根因。现改用原生列表，外侧 View 和列表根节点设置宽高铺满，滚动视口 flex 填充并设置 minHeight(0)，列表内容按行自然高度且禁止收缩，底部按钮不参与压缩。游戏内实际布局仍待验收。
- **监视分组显示箭头却不能折叠**：表单公共 group 方法显式调用 setCanCollapse(false)。移除该禁用设置，保留原 ConfiguratorGroup 的点击和显示状态逻辑；后续无明确需求不要禁用分组折叠。
- **时间轴标题显示翻译键**：PhotonAnimationTimelineView.createTab 调用 Tab.setText(String)，该重载默认 translate=false；语言资源已存在，增加翻译键不能解决调用问题。改为 setText(key, true)，其他页签保持 Component.translatable。
- **监视页签在底部**：TabView 的孩子顺序为内容区、标签栏，原 COLUMN_REVERSE 用于让第二个孩子位于顶部；上轮强制 COLUMN 导致位置倒置。恢复 COLUMN_REVERSE，后续必须同时核对方向与真实子节点顺序，不能只按方向名称判断。
- **多余面板框**：嵌套 TabView 的 tabContentContainer 与 tabScroller.viewPort 都可通过默认皮肤或 ore.lss 再画边框，而配置页 ScrollerView 本身已有边框。将前两者的背景在构建完成后设置为 IGuiTexture.EMPTY，并清零标签视口的 padding；仅保留配置滚动容器边框，不新增业务页或修改全局主题。不要随后调用 moveInlineAsDefault，否则空背景会降为默认值并被 LSS 覆盖。
- **动画行紫色皮肤与名称压缩**：Item.lineContainer 的 Sprites.BORDER_RT1 内联覆盖指定了 GDP 皮肤，使 Minecraft LSS 无法接管。原爆发与 UI 编辑器内置样式列表不手写此覆盖，现移除。名称改为无标题 Configurator 中的原生 TextField，行与字段明确宽度；此前内层 label 根节点未指定宽度是压缩风险，实际尺寸仍待运行验收。字段不能获得焦点，在其父行的捕获阶段仅阻断左键后续处理，保留更外层 Item 捕获选择、拖动条及右键菜单，避免名称框启动文字选区拖动。当前 UIEventDispatcher 在目标捕获结束后仍可执行第一个目标冒泡监听，因此拦截必须放在祖先行，不能只放在 TextField 自身。
- **共享内容迁移**：UIElement.addChildAt 不允许向同一父节点重复添加，共享表单仅在父节点变化时迁移。
- **拖动排序与历史**：ArrayConfiguratorGroup 在拖动中重排 ItemConfigurator，不能在选中或每次移动时重建列表。选中只刷新曲线与监视内容；DRAG_END 在当前 LDLib2 版本仅有捕获阶段，列表在捕获阶段提交最终顺序，再由现有编辑历史更新资源。不要改成仅冒泡监听或逐移动提交。
- 维护时以 Photon 26.1.2.3 / LDLib2 26.1.2.42 的 IDEA 源码为准；原生组件参考 [TabView](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/ui/components/tab-view.html) 和 [ScrollerView](https://low-drag-mc.github.io/LowDragMC-Doc/zh/ldlib2/ui/components/scroller-view.html)。静态、编译和 Data 检查不代表折叠、拖动与视口显示已经通过游戏验收。

- **每次修改后时间轴状态重置**：定义恢复曾通过 NBT 重建轨道和属性，原 TL 用对象身份保存的曲线状态、展开项与选择因此失效。现用编辑快照保存工作区轨道/属性引用，未改变的模型不重新读取；常规修改提交定义而不执行一次完整历史恢复。历史恢复只清理无效选择，保留仍属于当前模型的引用。维护时不要为普通值变更重新创建整套 TL 模型或清空编辑会话。
- **文本输入与原生配置修改丢失状态**：表单每次值变更后重建会移除正在输入的字段；原生片段/信号配置器直接修改模型也会绕过工作区 pushEdit。现保留当前配置器和文本焦点，通过监视会话的 CHANGE_EVENT 提交 TL 模型、定义和快照历史，并更新运行态；需要刷新表单时等待文本编辑结束。历史合并使用工作区与字段来源边界，不能把同名的其他操作合并进当前输入。
- **层级选中后原监视面板无法编辑动画**：此前阻断原监视路径或反复清理配置组，使原 TL 的选择与配置器不能复用。现 FXObject 选择进入安全录制/只读代理，其他原生 IConfigurable 进入监视会话；保留原配置组、关闭回调和通知监听，避免 InspectorView 与 Workspace 相互递归。代理不向原对象参数写值，非录制目标也不开放粘贴或拖入写值。
- **动画时间轴能力受限**：旧模型只接受 AnimationTrack，额外隐藏 EndMode 并将底部循环同步到命名动画，无法完整复用原 TL。现保存通用 Track 与嵌套分组，沿用八种原生轨道编辑器和 TimelinePlayer；底部结束/循环是独立编辑预览设置，顶部命名动画时钟与完成协议保持原定义。普通属性混合与出生/持续绑定不转交给原生播放器重复写入。
- **重模拟重复触发或播放实例被清空**：只采样属性无法还原发射与控制状态；重新 emit 时可能先执行时间零事件，随后同步又将已准备的新运行态当成未知实例清理。现 seek 沿 Scene simulateTo 重放，emit 使用预览静默作用域，事件队列在静默时拒绝 MARKER/COMPLETE/SIGNAL；adoptRuntime 在准备后接管运行态。静默只用于命名编辑预览，保留主动 TRIGGER 和内部模拟，恢复原场景不使用全局静默。
- **片段、标记及其他动画的关键帧选择引用过期**：载入定义会更换标记与片段引用，原 TL 吸附、删除或监视可能仍访问旧选择。当前动画重复载入按 Definition.Marker 身份复用标记，并按当前模型修剪选择及曲线缓存中的选中关键帧；撤销、切换动画时清除失效的片段、标记与监视会话。不能按列表下标匹配已删除或重排的标记，也不能把另一动画的选择带到当前模型。
- **预览范围与命令时机**：Fit 范围存入当前工作区并进入历史；正在重模拟时，顶部播放、继续、暂停等命令延迟到模拟结束再执行，以最后一条命令为准，恢复命令同时恢复粒子管理器。维护时不能让正在推进的模拟覆盖用户最后的播放意图。

## 形参与绑定

在动画列表选择一个动画，再到“动画检视 → 形参”编辑其签名。每个动画独立声明参数，即使名称相同也不共享值。每项具有稳定 UUID、名称、类型、默认值及一组所有分量共用的最小／最大值；改名保留 UUID，删除或改型只清理实际所属动画的引用。子片段目标还需确认发射器项目模板确实来自当前主库；外部资源即使 UUID 相同也保留其目标输入。无法确定来源的代码重载采用保守处理，父形参 source 引用仍按所属动画清理。

类型包括浮点数及浮点 vec2／vec3／vec4、整数及整数 vec2／vec3／vec4、布尔和 RGBA 颜色。NUMBER／INTEGER／VECTOR 的保存标识分别对应浮点数／整数／浮点 vec3。运行写入校验分量数、有限值和有效范围；整数限于 ±16,777,216，空整数区间拒绝。布尔规范为 0/1，颜色范围为用户范围与 0..1 的交集。返回的数组与 Map 为副本。

形参和输入沿用 LDLib2 原生 ArrayConfiguratorGroup，使用“发射 → 爆发”的数组行、拖动条、选择及 +/-。布尔使用原生开关，颜色同时保留原生颜色选择器和精确 RGBA 输入。颜色选择器只有明确编辑时才进行 8 位 ARGB 转换，表单打开／刷新不量化浮点值。

一次 play 为该播放创建独立参数值。调用返回的 PhotonAnimationPlayback 可读写该次播放的形参，Runtime 包装接口同样需要指定句柄。同名动画重启后旧句柄失效；旧句柄不能修改、停止或完成新的播放。continue 使用 restart=false，保留句柄、进度和未提供的输入。

绑定页为原生属性选择曲线、形参、特效时间、动画时间、进度或循环次数；绑定来源使用当前动画的形参 UUID。目标分量、覆盖／相加／相乘保持原功能，scaleParameter／offsetParameter 可分别选择该动画的标量形参；固定缩放和偏移仍可使用。动画时长可选择一个非负标量形参，也可由 play 的明确 duration 覆盖。

固定输入在调用时复制；实时跟随只针对明确指定的输入项。来源句柄停止、替换或释放后保留最后有效值并解除跟随；新播放不会自动接管旧来源。显式修改一个目标形参会解除该项跟随，其他项继续跟随。形参时长变化保持当前进度与循环次数。

出生属性（config.startSize／startColor／startLifetime）影响之后生成的粒子；已有粒子的大小、颜色和自旋继续使用 config.sizeOverLifetime.size、config.colorOverLifetime.color 和 config.rotationOverLifetime.roll/pitch/yaw。NumberFunction 保留各粒子的年龄和随机数，不以单一动画采样值替代。

模块 enable 只在勾选“自动启用模块”时临时开启，显式 enable 动画优先；新定义默认关闭，旧 version=1、2 保持历史开启语义。停止只移除本扩展持有的覆盖，不改共享配置，动画不会自动延长粒子寿命。

## 时钟与合成

启动后自动播放 defaultAnimation，START 规则可播放其他动画。
各动画独立计时，按优先级从低到高、同级按启动顺序合成。
每帧先释放本扩展上一轮贡献，读取原生时间轴/外部操作后的基础值，再统一合成写入。
基础值不是上一帧的合成结果；外部后来写入的覆盖不会被本扩展清除。

| 混合 | 运算             | 适用                                     |
|------|------------------|------------------------------------------|
| 覆盖 | 结果 = 当前贡献  | 全部属性，布尔及其他离散属性仅允许此模式 |
| 相加 | 结果 += 当前贡献 | 数值、整数、向量、颜色                   |
| 相乘 | 结果 *= 当前贡献 | 数值、整数、向量、颜色                   |

最终结果按属性规则限制范围。停止一个动画后，剩余动画继续从未合成基础值计算。
完成默认 HOLD 保持末帧；RELEASE 完成后释放本动画贡献。停止动画不会清理粒子，结束特效另行控制。

运行时修改时长保持归一化位置。例如播放到 50% 后由 20 tick 改成 100 tick，仍处于 50%。
循环动画保存完整周期数加周期内进度；改时长不重置周期数。
普通关键帧及有效 CurveClip 函数贡献都累计完整周期角度差，函数仍按粒子原生命周期和随机数求值，再应用绑定缩放与偏移。
非循环 0 时长在推进时完成；循环必须使用正时长。
每次播放持有完整原生 Timeline 与独立播放器状态。普通属性通过 AnimatedProperty.apply 多态采集实际值或函数，保留原曲线、表达式、颜色、整数及短角旋转语义，再交现有绑定与混合器合成。
evaluateAt(double) 与 frameAt(double) 的精确制作时间限定在当前播放器作用域；激活、控制、动画、速度、信号和音频顺序复用原执行阶段。
tick 不补做 frame；后处理每个播放器只在实际渲染帧提交一次。原生字段类型和无控制器特效的执行链保持不变。
底部 TL 预览使用独立时钟：所选动画进入 HOLD 后，原生预览结束/循环行为仍按各自设置处理，不改变命名运行的完成协议。

## 命名动画标记管理

动画检视的标记使用与触发规则相同的原生 `ArrayConfiguratorGroup`，提供边框、折叠、行选择、拖动排序和加减按钮。每行编辑时间及名称，已有事件 data 保留，存储格式和播放触发机制不变。
标记是不可变记录；修改时先把表单身份转移到新记录，再由数组的更新回调统一提交操作历史。值相同的不同标记仍按对象身份区分，不按 `equals` 或行号定位，避免错替换以及选中、折叠状态丢失。
身份转移保留旧映射直至新快照捕获；操作历史的前态使用已保存快照，撤销时按原 Marker 引用及 UUID 恢复。表单用 `GroupIdentity(UUID, role)` 保存选中与折叠，不能把不可变记录替换误当成删除后新增。

本轮仅改变管理界面，不改变时间上限、事件派发顺序或编辑器静默机制。当前时间输入仍以 `animation.duration` 为上限，自动时长下与完整制作范围不一致的既有问题另列；跨周期 MARKER 顺序也继续保留为遗留。
2026-10-07 标记界面经 IDEA 修改行格式化和诊断，整合编译成功、正常 Data 退出码 0，生成资源不变。实际新增、删除、排序、相同值条目、撤销重做及状态恢复仍待游戏内验收，编辑本身不应触发运行事件；本次未运行独立回归入口。

## 简化触发入口

事件来源仍为 START、COMPLETE、MARKER、SIGNAL、TRIGGER。来源动画 UUID、channel、name 为空时表示不限制相应字段。每条入口只有目标本实例动画、播放时长和形参输入；目标是单个动画 UUID，不再提供动作链、子作用域、SET_PARAMETER 或 FINISH 作者选项。

START 是特效实例启动一次；默认动画与 START 入口分别执行。COMPLETE 只在自然完成时产生一次，保持末帧不重复产生；stop 和编辑器拖动不伪造完成事件。COMPLETE 在来源 RELEASE 前冻结形参输入，目标仍能读取其最后快照，不能继续跟随已结束的来源。

MARKER 按经过区间触发，时长调整自身不重复触发；原生 SIGNAL 转发到所属 FXRuntime。事件排队处理，每 tick 预算 256，剩余递归事件丢弃并记录问题。普通特效的原生回调顺序及所属实例隔离保留。手动 trigger 可携带目标形参固定输入，命中规则后再按目标签名校验。

被动 UI 同步、编辑和时长重定时本身不额外生成 START／COMPLETE 或标记；完整重模拟仍按经过区间执行内部编排，与被动采样区分。外部回调及音频在重模拟期间静默，不以关闭内部事件代替。结束整个特效仍由外部 finish API 或宿主生命周期控制。

## 组合动画调用子动画

1. 将法阵分别导出为含控制器的 .fx，每个子资源定义 start／hold／end 等命名动画和需要接收的独立形参。
2. 在组合特效中创建“发射器发射器”，选择完整 FX 来源，配置一次性或原有调度方式。子动画片段只调用已有实例，不隐式发射。
3. 在组合命名动画的共享 TL 中添加“子动画”轨道，轨道目标选择父层级中的发射器发射器，片段选择子动画 UUID。参数候选来自该子资源的签名。
4. 片段选择“受控”或“独立”。受控由父制作时间把完整子动画映射到片段窗口，支持整数／小数时间及末帧保持；独立在窗口中对各已存活实例调用一次，之后按子时钟推进。
5. 为每个目标形参填固定值，或选择当前组合动画的来源形参 UUID；默认快照，按需开启实时跟随。场景基础时间轴没有命名形参来源，只能填写固定输入。
6. 外部调用组合动画并传入形参；组合的时长形参可以整体拉长／缩短父轨道内容，片段中的输入再传给子动画。子动画的时长形参可控制独立播放，受控模式由父窗口长度优先控制。

窗口覆盖当前存活实例；窗口内稍后生成的实例也接入当前窗口，受控模式定位到当前进度。窗口结束后新实例不补发历史调用。相同窗口不会每 tick 重启同一实例；父时间回退／进入新循环会建立新窗口。

同名动画的显式重启可替换正在运行的受控播放，旧句柄和旧 token 随即失效；旧片段后续完成、停止或回退不能触达新的播放。父停止、关闭或回退只清理自己仍拥有的受控播放，包括已保持末帧的贡献；独立播放继续运行，仍服从发射器活动状态、实例时限及父 FX 销毁。

片段的原生 speed 字段不参与子播放：受控完整一次由父窗口控制，独立由子定义、playbackDuration 或形参时长控制；锁定版本的原生片段检视只有开始与窗口时长，扩展不添加无效速度控件。零长度受控窗口即时采样末帧并完成一次，独立零窗口仅触发一次调用。
结束行为覆盖只在受控模式显示，可沿用子动画、保持末帧或释放；独立模式沿用子动画自身的完成行为，不提供无效覆盖项。

调用不存在的动画、缺少控制器、维度／范围不合法或缺少来源形参时记录问题，不新建实例、不恢复旧规则运行层。完整源及运行边界见 [发射器发射器](photon-emitter-spawner.md)。

## 客户端代码使用

在已有 FXRuntime emit 之后，通过 PhotonAnimationUtil.of 获取控制器；无控制器实例返回 null。操作在客户端线程执行，服务端行为沿用现有视觉同步入口，不为本功能新增网络协议。

```java
import com.lowdragmc.photon.client.fx.FXRuntime;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUtil;

import java.util.Map;

public class AnimationUsage {
    @SuppressWarnings("ConstantValue") // IDEA 数据流不解析运行时 Mixin 提供的接口。
    public static void apply(FXRuntime fxRuntime) {
        var animations = PhotonAnimationUtil.of(fxRuntime);
        if (animations == null) {
            return;
        }

        // expand 声明 radius 与 introTicks，并选择 introTicks 作为时长形参。
        var expand = animations.play("expand", Map.of(
                "radius", new float[]{3},
                "introTicks", new float[]{40}));
        animations.setParameter(expand, "radius", 2);
        var radius = animations.parameter(expand, "radius");

        // phase1 的子动画片段把本次形参转发给各法阵。
        animations.play("phase1", Map.of("radius", radius));
        animations.play("spin");
        animations.setDuration("spin", 100);
        animations.pause("spin");
        animations.resume("spin");
        animations.stop("spin");
    }
}
```

play(name, duration, restart, inputs) 中 duration=-1 使用定义或形参时长；明确非负 duration 覆盖形参时长。restart=true 默认重启；false 继续已有进度，输入完整校验后一起更新。局部形参 Map 支持名称或 UUID，但同一参数不能同时以两种键重复传入。

Playback.parameter(name)、parameters() 查询副本，setParameter／setParameters 修改当前句柄，follow(target, source, sourceParam) 建立逐项跟随。Runtime.isCurrent(handle) 查询句柄身份，Runtime.parameter／parameters／setParameter 包装方法检查当前归属。不存在或已替换的句柄不能作为全局参数表使用。

playback(name) 返回可空播放状态，clock.state()/progress()/loops()/duration() 查询运行时钟。effectTime() 为特效 tick，Playback.time(partialTicks) 为制作采样时间。property(targetUUID, path, false/true) 区分基础／合成值；NumberFunction 查询是原生参考采样，不代表所有粒子的年龄／随机结果。

trigger(name, data, inputs) 排队简化入口，宿主不推进时等待实际 tick。finish(force) 结束整个特效，close() 只清理扩展状态和恢复本扩展覆盖／回调；停止动画不会替代结束特效。

## 同一个魔法阵制作参考

1. 在主编辑器层级中选中一个使用法阵纹理的 ParticleEmitter；配置粒子寿命为 0、仅启动时生成一颗、
   持续发射率为 0，保留对象和原生时间轴直到显式结束。
2. 手动创建 expand、spin、breathe、fade，并为同一个发射器的持续模块添加曲线。
3. 配置 expand（20 tick，大小 0→1，默认启动并保持末帧）、spin（80 tick，旋转 0→360，循环）、
   breathe（60 tick，大小倍率 1→1.1→1，循环相乘）、fade（20 tick，Alpha 1→0，相乘）。原模块需开启，或明确选择“自动启用模块”。
4. 分别添加 START 入口播放 spin 与 breathe；退场 TRIGGER 入口播放 fade，也可用原生信号或代码触发。
   若退场前需要停止 breathe，或 fade 完成后需要结束特效，由外部 API 显式处理；第六版入口只播放一个动画，不提供操作链或 FINISH 动作。
5. 这些动画只作用于同一个目标发射器的持续模块，无需替换法阵粒子。

## 保存、复制与清理

原时间轴与每个命名动画分别存储，轨道继续使用原生 Timeline.writeTrack/readTrack。第六版为各动画保存 parameters／durationParameter，绑定保存 parameter／scaleParameter／offsetParameter，输入保存 target／source UUID、固定 value 与 follow。控制器库不再保存共享参数，简化规则只保存来源条件、target、duration 与 inputs。

发射器项目模板复制单个控制器及复制范围内的原生轨道、绑定与子片段父目标；完整 FX 使用源资源控制器。动画和形参 UUID 属于定义，子动画 UUID 和输入目标属于子资源，不当作父场景对象 ID 重映射。子片段复制创建独立片段身份；保存加载保留原片段身份。复制、系统剪贴板及 FXPack 复用原生序列化。

旧 version=1、2 的固定 duration、模块自动启用及无 type 的动画轨道读取规则保留；version=3～5 保留当时自动时长缺省。旧动作／调用枚举或输入不能转换时，按已确认范围丢弃整条规则并报告警告；不会保留半条调用、把未知动作回退为 PLAY，或将旧子调用推测为新的子轨道。未来版本及损坏动画结构继续受阻并保留原件。

原生 Timeline.readTrack 遇到未知 type 会返回 null，组轨道也会跳过未知子项；仅在读完后检查会丢失原始数据。迁移必须在读取 Definition 前递归检查 tracks／children 的列表、条目、已注册 type 和 data 结构；未知或畸形项阻止迁移并保留完整原件，不把静默跳过当作兼容成功。

[保存保护](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationMigrationGuardUtil.java)先审查同一 FXData 的全部控制器，只有没有阻断问题才提交；弃用警告不阻止保存支持内容。Editor 保存／另存、FX 导出和 FXPack 导出共用保护，真正受阻时显示原因，不误报保存成功。只读工程扫描器按 .fxproj 的未压缩 NBT 和 .fx 的 GZIP NBT 读取，不加载工作区、不写资源。
“资源与剪切板 → 保存与导出通知”（notifications）默认开启。开启时 `.fx` 导出在实际写入及清缓存成功后显示 2 秒原生定时提示，FXPack 成功也使用定时提示并保留详情；当前 Photon 编辑器的定时通知最多同时三条、竖向排列，保存成功也沿同一入口。关闭后恢复原 `.fx` 无成功提示、FXPack 成功确认弹窗与保存独立定时通知，错误及其他确认弹窗保持原行为。归属、关闭清理与未验收边界见[导出成功与定时通知](photon-editor.md#导出成功与定时通知)，不以提示出现替代资源内容验收。
本轮通知设置补全后最终 13 个 Java 的 IDEA 编译成功，Data exitCode=0、BUILD SUCCESSFUL in 21s、written=0；先前 12 个 Java 与 Data 32s、written=2 仅作初阶段证据保留。相对通知起始 25 个生成文件中仅两份语言资源及对应两个缓存变化，两语言各共新增 export_success／export_success_detail 与 feature.notifications／feature.notifications.tooltip，原有文本保持。通知初阶段 Git 为 dev、241 条变更（124 暂存、200 未暂存、0 未跟踪、0 冲突）；原有条目索引状态码保持，3 个新 Java 由 IDEA 自动加入索引，没有人为暂存或取消暂存。真实通知设置、布局与导出交互仍待验收。

2026-10-08 本轮在 Data 已加载注册表的 IDEA 调试帧中重新只读 scanProjects：11 个文件、9 个控制器全部 blocked=false。first-effect.fxproj 丢弃 1 条旧规则，magic_circle.fxproj 与 magic_circle.fx 各丢弃 2 条，共 5 条非阻塞警告；其余 6 个控制器无警告。该结果确认最新策略的加载审查不再受旧动作阻断，没有修改任何二进制原件，也不等同于实际编辑器保存／导出验收。

### 已被最新弃用指令取代的手工迁移方案

2026-10-08 最新弃用指令之前，在已加载注册表的 IDEA 调试帧中只读扫描 run/client/ldlib2 的 11 个文件（6 个 .fxproj、5 个 .fx），发现 9 个控制器：当时 6 个可转换、3 个受阻，原共享参数数量均为 0。“可转换”仅表示当时内存迁移审查通过，未保存或覆盖任何旧资源。下表是历史阻断结果，不能作为最新弃用策略下的重新扫描结论，路径均相对 run/client/ldlib2。

| 文件                                                    | 原版本 | 原规则数 | 待处理内容                          |
|---------------------------------------------------------|--------|----------|-------------------------------------|
| assets/lobecorp/the_queen_of_hatred/first-effect.fxproj | 5      | 1        | 第 1 条规则不符合单个本特效 PLAY    |
| assets/lobecorp/the_queen_of_hatred/magic_circle.fxproj | 3      | 2        | 第 1、2 条规则不符合单个本特效 PLAY |
| assets/photon/fx/the_queen_of_hatred/magic_circle.fx    | 3      | 2        | 第 1、2 条规则不符合单个本特效 PLAY |

当时报告为“触发规则无法等价迁移：N／只支持单个本特效 PLAY；子调用和操作链需要逐项处理”。原先等待逐项决定的流程已被用户最新弃用指令取代：这些不支持旧规则在加载迁移时丢弃并报告，支持动画保留，正常保存／导出写为 v6；不再按此旧报告阻断。

此前继续通过 IDEA 调试只读解析原始 NBT，确认 first-effect.fxproj 的 circle_expand 有一条 MARKER 规则，其条件名称为 circle_expand_signal，动作调用 CHILD_INSTANCES 下的 expand 子动画，发射器 UUID 为 07a7c183-7e59-4599-a6a8-128cc793fa5f，目标子动画 UUID 为 a03d5b34-04f6-4fed-8304-10e7a778c19b。实际 markers 仅有 `{time:0,name:"",data:{}}`，不存在匹配标记，先前误称实际标记的记录已更正。用户曾选择子片段覆盖父动画 0～20 tick，但该补造方案已经被最新弃用指令取代；目前仅丢弃旧 CHILD_INSTANCES 规则，不推断标记时间或补造轨道。

magic_circle.fxproj 与 magic_circle.fx 均有一条空 START 动作列表，以及 fade 完成后 FINISH(force=1)；两份资源的 expand／fade UUID 一致。最新加载迁移会丢弃这两条不支持规则；淡出后的清理仍由外部组合控制显式调用 finish(true)，不恢复旧 FINISH 动作链。新的子动画编排应在 TL 中主动配置，不能因弃用迁移自动添加先前方案的 `[0,20)` 窗口。

下表保留此前批准但未执行的手工写回方案，现已取代，不再执行二进制写回或新增子轨道：

| 项目相对路径                                                              | 批准的变更                                                                                                                                                                                                        |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| run/client/ldlib2/assets/lobecorp/the_queen_of_hatred/first-effect.fxproj | circle_expand 追加 lobecorp_child_animation 轨道；保持上述目标 UUID，片段 start=0、duration=20、mode=INDEPENDENT、completion=INHERIT、playbackDuration=-1、inputs=[]；仅移除旧 CHILD_INSTANCES 规则，保留原标记。 |
| run/client/ldlib2/assets/lobecorp/the_queen_of_hatred/magic_circle.fxproj | 仅移除空 START 和 fade 完成 FINISH(force=1) 两条旧规则，保留动画定义与引用。                                                                                                                                      |
| run/client/ldlib2/assets/photon/fx/the_queen_of_hatred/magic_circle.fx    | 与工程文件执行相同规则迁移，保留 GZIP 格式。                                                                                                                                                                      |

旧方案要求按原版本读取再生成 v6，仅替换原始完整 NBT 副本中的 definition，保留其余标签；曾拟在 backups/2026-10-08/photon-animation-v6 按项目相对路径备份原始字节。该方案和备份均未执行，最新指令已取消手工迁移脚本；当前通过加载中的逻辑迁移和原生保存／导出写 v6，不仅修改 version 字段。

旧方案的只读复核确认 `[0,20)` 内按实例句柄去重接入，独立子动画从自身进度 0 开始；20 tick 后停止接入，已启动播放继续运行，父暂停或停止不会结束它们。轨道末端 20 不延长父制作范围；最后一次窗口内求值后才生成、到 20 tick 才可见的实例不会接入。这是原子片段边界说明，不表示最新迁移会创建该片段；客户端生成与求值顺序仍需游戏内验收。

当时未改写或保存三份 NBT，也未创建磁盘备份；IDEA/MCP 缺少二进制编辑能力曾使兜底方案等待授权。该兜底流程已经取消，不再询问或准备二进制写回。维护时仍应区分规则条件与实际标记、加载迁移与磁盘保存，不能将弃用报告当作已覆盖原件的证明。

前轮 IDEA Data 调试已读取三份原始 NBT、规则、实际标记及哈希，输出为 BUILD SUCCESSFUL in 9m44s；完整内存手工迁移预演未完成，发起时会话已结束，sessions=[]，代理断点无残留，用户原有两个禁用异常断点保留。该轮收尾三份 SHA-256 完全一致，生成目录无 Git 差异。以上均为最新弃用实现之前的证据，不证明本轮迁移、回归或客户端验收通过。

### 当前运行态清理

destroy、重新 emit、控制器删除、粒子管理器清理、世界退出和资源重载关闭运行态。子桥停止／遗忘释放属于该播放器 token 的受控句柄，独立播放仍由自身 FXRuntime 管理。形参跟随用弱引用，来源失效冻结最后值；清理不能停止被新 owner 接管的播放。

场景模式关闭命名扩展并恢复原回调、基础覆盖与 retention。动画预览关闭恢复原场景，不直接释放共用粒子管理器；普通无控制器特效不增加动画 tick。

## 历史修复与审查遗留

以下问题记录来自先前版本；旧共享参数／动作链的描述保留根因证据，第六版的当前入口以前文为准。

2026-10-08 本轮弃用旧规则时确认的维护问题：convertRule 处理固定输入会通过 localParameter 向目标动画追加形参；若之后某项输入无效而整条规则被丢弃，先前追加的形参可能残留，并错误占用共享参数归属。现每条规则转换前记录各动画形参数量和已有引用，仅在规则整体成功后合并引用；失败时回退该条规则新增的形参，保留绑定及先前成功调用的形参身份。防回归同时检查“先有效后非法”的输入、原有绑定、先前成功规则和未引用参数弃用，不能只验证失败规则未进入 rules 列表。见 [迁移实现](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationMigrationUtil.java)。

2026-10-06 本轮六项修复已落入源码，客户端实际表现仍待验收：

- **无效默认动画 UUID**：根因是首次 tick 直接 play 缺失动画，异常不在构造保护范围内。
  现先查找默认动画；引用不存在时记录问题并安全跳过，START 规则仍执行，不删除或改写原 defaultAnimation 和已保存数据。
  防回归时同时检查首次启动、START 只执行一次和损坏引用原样保留，不能在运行时静默修补资源。
  见 [运行态](../../src/main/java/org/unitego/lobecorp/client/photon/animation/runtime/PhotonAnimationRuntime.java)。
- **CurveClip 跨周期旋转累计**：普通采样已有角度周期偏移，CurveClip 函数贡献却漏掉了偏移。
  现保留粒子生命周期时间和随机 supplier，在函数按生命周期采样后添加跨周期角度偏移，再应用绑定的 scale 和 offset。
  防回归检查普通关键帧与 CurveClip、多个循环、各分量正负偏移及停止后的运行槽恢复；不要把函数采样时间替换成命名动画进度。
  见 [混合器](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationMixer.java)
  与 [函数贡献](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationContribution.java)。
- **参数类型迁移**：此前只重建 defaults，已有 SET_PARAMETER 动作仍保存旧维度。
  现保留共享分量，为新增分量使用新类型默认值；对默认值和同参数动作值分别执行新类型、范围及有限值校验，
  并同步迁移全部对应 SET_PARAMETER 动作。动作新增分量继承迁移后的参数默认值，数组按值隔离；
  校验失败时不提交半套迁移，同类型修改保持原值。不要仅更新参数定义而遗漏规则动作，或让动作与 defaults 共用数组。
  见 [参数编辑](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationParameterEditor.java)。
- **退出预览恢复播放状态**：此前只保存面板与资源状态，未保存进入前粒子管理器是否播放。
  现进入时保存该状态，恢复场景和常规控制器后按原状态 play 或 pause；不销毁共用粒子管理器。
  防回归分别检查原场景播放与暂停时进入、切回标签和关闭工作区；不要拿动画预览退出时的状态替代进入前状态。
  见 [预览](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationPreview.java)。
- **规则动作折叠归属**：动作曾加入页面 container，成为规则分组的兄弟节点。
  现通过原生 addConfigurator 加入对应规则的子组，规则折叠同时隐藏动作；各动作保留自己的折叠状态与顺序。
  防回归核对真实父节点及 configurators 列表，不只隐藏页面上的某段控件。
  见 [规则编辑](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationRuleEditor.java)。
- **绑定标题及分量标签**：此前拼接 displayNameKey 字符串可能显示翻译键，各轴标签也无法区分。
  现标题使用翻译 Component，向量显示 XYZ、颜色显示 RGBA、标量显示值；颜色候选分量包含 Alpha。
  非法旧分量保留为带提示的候选项，构建界面不自动改写数据，用户可选择有效分量修复。
  防回归核对标题翻译参数类型、颜色真实值分量数及旧非法值可显示/可修复，不能用属性内部通道数遗漏 Alpha。
  见 [绑定编辑](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationBindingEditor.java)
  与 [参数编辑](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationParameterEditor.java)。

2026-10-06 经用户确认继续修复重复合成问题：

- **重复合成把旧贡献当作基础值**：此前已加载多实例回归在第 6 项失败，停止覆盖动画后剩余乘法应得到 21，实际得到 54。
  夹具基础值 7 正确；tickTimelines 在一次 tick 中先后两次 apply，中间未释放第一次安装的函数，Property.apply 再把旧合成值 18 保存为基础值。
  现于 Mixer.apply 入口统一 release 仍属于自己的旧贡献，再采样和构造新贡献；空列表同时释放已移除属性和临时启用的模块。
  仅在 Property.apply 释放不足以覆盖第二轮已移除动画的属性。保留原生执行前的 tick/frame 释放及外部覆盖身份检查。
  防回归检查连续 ADD、MULTIPLY、混合、停止部分/全部贡献、基础/合成值查询，以及外部函数覆盖、启用覆盖和原配置不变。
  CurveClip 检查也不再手动释放后才断言恢复，确保实际测试连续 apply。多实例检查和完整加载回归现已通过。
  见 [运行态](../../src/main/java/org/unitego/lobecorp/client/photon/animation/runtime/PhotonAnimationRuntime.java)、
  [混合器](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationMixer.java)
  与 [属性贡献](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationProperty.java)。
  所有权保护沿用原判断：函数槽的新对象覆盖可保留；直接变换使用值相等判断，缓存装箱槽使用对象身份，无法精确辨别部分同值的新写入。
  本次未扩展所有权协议，不能将回归结论推广为任意同值外部覆盖均可辨别。

以下三项已由当前源码确认，仍未修复；源码结论不等于客户端表现已实测：

- **P2，临时参数界面不跟随规则或外部更新**：临时字段使用 forceUpdate=false 的 NumberConfigurator，
  当前 LDLib2 .39 只在 forceUpdate 开启时每个 screenTick 重新读取 supplier。规则/API 修改运行时参数不会重建页面，
  因此效果参数已变，字段仍显示旧值，直到页面重建。需要仅对临时字段恢复被动刷新，并核对输入焦点期间行为。
  见 [参数编辑](../../src/main/java/org/unitego/lobecorp/client/photon/animation/editor/PhotonAnimationParameterEditor.java)
  与 [UI 工具](../../src/main/java/org/unitego/lobecorp/util/photon/animation/PhotonAnimationUiUtil.java)。
- **P2，项目模板复制固化动画变换**：copySubtree 直接调用对象 copy(true)，经对象 Codec 复制当前 Transform，
  绕过 FXData.serializeNBT 的基础值保护。根位置归零后，根旋转/缩放及子级变换仍可成为复制实例的 base；
  例如基础缩放 1、ADD 动画贡献 1 时复制得到基础缩放 2，子实例重复贡献后可能为 3。
  这是复制实例基础值的差异，不表示源资源已永久改写；config.* 运行槽覆盖不会一概复制进配置。
  现有 serializeBase 只保护编辑器预览，直接套用它不足以覆盖普通运行实例。
  见 [模板复制](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterTemplateUtil.java)、
  [序列化保护](../../src/main/java/org/unitego/lobecorp/mixin/photon/animation/client/PhotonAnimationDataMixin.java)
  与 [属性贡献](../../src/main/java/org/unitego/lobecorp/client/photon/animation/adapter/PhotonAnimationProperty.java)。
- **P2，多个循环的 MARKER 未按发生时间派发**：markers 以标记为外循环、周期为内循环，事件队列按 FIFO 执行。
  时长 1、A 在 0.25、B 在 0.75，单次推进 2.5 时应为 A0、B0、A1、B1、A2，当前为 A0、A1、A2、B0、B1；
  A/B 分别设置参数 1/2 时，最终值会从应有的 1 变成 2。未排序标记列表还可使同一周期内逆序。
  需要按本次跨越的发生时刻稳定排序入队，同时保留同刻顺序及事件预算。
  见 [运行态](../../src/main/java/org/unitego/lobecorp/client/photon/animation/runtime/PhotonAnimationRuntime.java)。

2026-10-07 另发现既有标记编辑限制：RuleEditor.markers 仍把输入时间限制在 animation.duration，自动时长模式下该字段不等于完整制作范围。较晚的合法标记可能被拉回固定字段上限；本轮只记录，未扩展为编辑范围修复，客户端表现待核实。
历史问题：直接 Runtime.play(existing, duration, false, inputs) 曾在 NaN／负无穷时继续修改已有播放输入。2026-10-08 改为 Playback.setParameters(inputs, duration) 完整校验后一次提交，非有限时长、循环零时长或受控播放的显式改时长均在改参前拒绝；失败不提交部分输入。该行为已核对当前源码，执行结果见本轮实际检查。

## 实际检查与游戏验收

2026-10-08 单边激活与提示补全：24 个修改或新增 Java 完成 IDEA 修改行／涉及类格式化（关闭额外导入优化及成员重排），最终 errorsOnly=false 诊断均无错误／警告，Mixin 配置诊断无问题。IDEA build_project 返回 isSuccess=true、problems=[]；正常 Data 配置 waitForExit=true 返回 exitCode=0。生成目录 25 文件 SHA256 前后无新增／删除，只变更两份语言和对应两份缓存；IDEA 读取解析中英文各由 874 变为 872 键，删除旧 control.single_frame 标签及提示两键，各更新 17 项预期说明，无新增键，其他键值保持。生成输出未手写或格式化。

新增断言覆盖旧单帧迁移、仅开始／仅结束及零长度点、点前外部停止、拖回／静音恢复、与原生 Control／双端区间共存、同刻叶顺序与后来入口恢复／清理；零时刻粒子检查增加真实局部引擎的循环 Burst／持续速率停止、已有粒子年龄推进及 CONTINUE 恢复。未运行这些加载后检查或游戏，时间点命中／组拖动、悬浮屏幕边界、循环生成与命名并行仍待游戏内验收。已有不相关修改继续保留，未人工操作暂存、提交、推送或创建备份；新增两个 Java 由 IDEA 自动加入索引。文档既有设置分类表保留一条弱格式提示，不扩大为排版整理。

下面的四项功能检查及更早轮次保留为历史证据；独立 singleFrame 字段已由上面的单边控制取代。

2026-10-08 本轮新增四项功能：34 个相关 Java 经 IDEA 范围／新增类格式化与最终诊断，无错误／警告；IDEA build_project 成功、problems=[]。正常 Data 退出码 0、BUILD SUCCESSFUL in 23s，六个 provider 完成；生成目录 25 文件无新增／删除，仅两份语言文件和对应缓存变化，中英文各从 858 增至 874 键，原键值不变，16 个新增键均为本轮内容。原有 Git 条目及暂存状态保留，未手动操作索引、提交／推送或创建备份。未运行加载后独立回归及游戏交互；新增回归覆盖单帧缺省与序列化、经过边界、项目调用具体类型、同宿主受控／独立及运行来源保护、实际局部粒子引擎初始化等，断言存在不等于已执行。下面的旧轮次数字保留为历史证据。

本轮另需游戏验收：大步长经过完整短片段时的激活／控制／音频和信号顺序；单帧开始后持续、重启／继续及后续停止；0 帧爆发显示、预热、正延迟、概率及首个正常窗口不重复；空选测试 TL 本特效并行调用、受控完成／释放、同名重启句柄隔离、复制保存往返和导出排除。实际场景与正常世界的边界不能用隔离夹具或编译结果替代。

2026-10-08 本轮旧规则弃用：IDEA build_project 成功、problems=[]，核心与 GUI 诊断无错误／警告。在 Data 已加载注册表的暂停环境实际执行 PhotonAnimationMigrationRegression.runLoaded()，返回 378；完整 PhotonAnimationChecks.runAll() 返回 5611，全部通过。真实旧文件重新扫描结果见保存段。正常 Data 日志为 BUILD SUCCESSFUL in 2m 8s，所有 provider 完成，HashCache 的 total／old／new 均为 19、removed stale=0、written=2；调试会话 sessions=[]。生成中英文各从 845 增至 847 键，仅新增 migration_discarded 及其 .tooltip，IDEA 索引确认中文正确；终端展示乱码不能据此判为文件编码错误。三份原二进制 SHA-256 与历史基线完全一致，未写回二进制、未新增备份，未操作索引或提交／推送。以上模型／加载检查不代替游戏窗口、保存导出、实际粒子、音频与 GPU 验收。

2026-10-08 前轮第六版收尾源码：IDEA build_project 编译成功，problems=[]；独立 PhotonAnimationRegression 配置 exitCode=0，253 项通过。Data 已加载调试帧中执行完整 PhotonAnimationChecks.runAll，返回 5391 且无异常；覆盖独立形参、句柄隔离、原生槽／轨道、受控与独立子片段、跟随冻结、静默内部编排、迁移保护及 GUI 模型。该轮真实旧文件审查结果见保存段。最终正常 IDEA Data 配置明确 exitCode=0；生成中英文各 845 键，与该次运行前逐键一致，子轨道标题译为“子动画”／“Child animation”，生成目录 Git 差异为空。输出没有手工修改或格式化；JSON lint 的空 items 不作为逐文件诊断证据，实际内容已由 IDEA 读取解析。上述检查没有运行实际游戏粒子引擎、音频输出、窗口交互或 GPU。

2026-10-08 此前继续审查：修正独立子片段的无效结束行为控件，IDEA 修改行格式化后诊断无问题；当轮 build_project 返回 isSuccess=true、problems=[]，正常 Data 配置 waitForExit=true、exitCode=0。IDEA 直接读取解析生成中英文各 845 键，与前轮最终生成逐键差异为空，生成目录 Git diff name-only 为空，未手写或格式化输出。只读 NBT 调试用于确认旧规则，未重跑上述 253／5391 项回归，也不作为新增回归证明。Minecraft MCP 未连接，实际两模式窗口交互、音频和 GPU 尚未验收；当时三个旧文件仍受阻，后来由最新弃用指令取代待确认流程。

2026-10-07 本轮发射形状与参数、规则编辑：14 个相关 Java 完成 IDEA 类/修改行格式化，关闭导入优化和成员重排；最终诊断无错误/警告，Regression.main 保留原日志点建议。
IDEA 编译 isSuccess=true、problems=[]，正常 Data 退出码 0；刷新 VFS 后中英文各 811 键，相对原 804 各新增 7 个预期分量/类型键，无删除，英语更新 5 项、中文更新 6 项，均为本轮预期类型名称、范围/值提示及中文面板名称。生成文件未手写或格式化，生成目录 Git 差异为空。
配置和 GUI 回归补充类型/范围/原生控件/选择身份检查，原生一致性补第四版迁移检查；本轮没有执行这些测试或 Client。颜色弹窗、原生列表拖动/选择、折叠及撤销属于实际游戏验收，静态控件构造断言也不能代替。完整文件与检查记录见 [修改记录](changes.md)。

2026-10-07 本轮组合控制及状态修复：25 个相关 Java 均通过 IDEA 修改范围/新类格式化，关闭导入优化和成员重排；最终分组诊断无错误/警告。4 份系统文档已更新，无新增诊断问题，索引保留既有表格排版弱提示。
IDEA 编译 isSuccess=true、problems=[]，正常 Data 退出码 0；19 个输出写入 2 个语言文件、无删除。VFS 刷新后中英文各 804 键，相对运行前各新增 55 个组合控件/提示和枚举键，更新 action.tooltip/rules.tooltip 两个预期文本，无旧键删除或其他值变更；生成输出未手工编辑或格式化，生成目录 Git 差异为空。
运行回归新增 48 条断言（预期该目录由 56 增至 104），GUI 回归增加保存、映射、父参数删除/改型、模型身份及分组职责状态检查；原生一致性更新当前版断言并增加 version=3 缺省兼容检查。本轮未执行这些测试、Client 或游戏交互，不能把此前 runLoaded 数量当作本轮通过结果。
实际延迟发射时机、ALL/LATEST 的存活句柄、局部参数连续传递、并行信号/音频、面板折叠/滚动及切换焦点仍需客户端验收。现有“工具 → 测试”可执行各自隔离的检查，不修改当前项目。

2026-10-06 去重整理的实际结果：IDEA 编译成功，独立 PhotonAnimationRegression main 74 项通过；Data 已加载阶段通过 IDEA 调试表达式执行完整 runLoaded，最终 4640 项通过（共享原生一致性由 412 增至 452，其他目录不变）。新增 40 项检查常规单次求值、SIGNAL 参数再次合成/前缀顺序、自然及静默 RELEASE、START/TRIGGER FINISH、部分刻重定时和制作预览 N−1 协议。
正常 Data 退出码 0；中英文各 729 个键、动画键各 182 个，相对本轮开始各新增 8 个状态/控件及提示键，更新 8 个播放按钮文本/提示，无删除，其余条目一致。生成文件只由 Data 写入，未手改或格式化。
19 个相关 Java 最终 IDEA 分析全部完成，无新增错误/警告；仅保留 Regression.main 原有 System.out 日志点建议。最近构建诊断错误/警告均为 0，Mixin JSON 诊断无问题；修改 Java 通过 IDEA 类/行格式化，关闭导入优化和成员重排。三个旧代理和 commitRecording 的源码引用检索为 0，实际录制及 InspectorSession 保留。
三个代理删除前已备份并逐份 IDEA 文本严格核对，.java~ 移动前后文本一致；见 [备份清单](../../backups/2026-10-06-photon-dedup/cleanup-manifest.md)。未运行 Client 或点击游戏内菜单，场景控件首次切换、折叠布局、输入焦点及真实音频/GPU 仍需验收。六个可能被二进制 UI NBT/动态引用的翻译键暂保留；三项独立遗留问题未扩展处理。

独立检查统一移入“工具 → 测试”，不再放在命名动画表单中；原“运行槽检查”和“魔法阵动画示例”按钮均已移除，示例构造代码与测试入口保留。
设置路径为“Lobecorp → 预览 → 独立测试入口”（tests），默认开启，关闭后隐藏测试菜单。
菜单包含全部测试、时钟与参数、运行槽检查（原入口）、多实例与触发、动画运行回归、共享时间轴与原生一致性以及编辑器配置：

- [原有回归入口](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationRegression.java)
  分别提供时钟/参数、实际运行槽和多实例/触发检查。
- [动画运行回归](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationRuntimeRegression.java)
  检查无效默认动画启动容错、函数贡献、CurveClip 跨周期偏移，以及重复合成、停止恢复和外部运行槽覆盖。
- [编辑器配置检查](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationGuiRegression.java)
  检查形参改型／删除归属、输入数组折叠、原生选择身份、翻译和分量候选；控件不加入当前工作区。
- [共享时间轴与原生一致性](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationTimelineRegression.java)
  使用隔离模型和播放器检查原生八类轨道、分组静音、函数片段、整数／小数制作时间、原生属性多态、控制／信号顺序、后处理次数、时长及旧版本迁移；另检查录制冻结与真实 SerializableRecordAction 基础快照。真实窗口状态与音频另行验收。
- [测试菜单](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonEditorTestMenu.java)
  使用各检查自己的模型、FX 和控件，不修改当前特效、播放或游标；失败详情与堆栈写入日志。
  原生对象和控件检查需模组加载完成，独立 IDEA main 只运行不依赖已加载环境的时钟、参数和 GUI 模型部分。
- [第六版迁移检查](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationMigrationRegression.java)纳入共享时间轴检查：形参 UUID、单 SELF PLAY 转换、不支持旧调用与未引用共享参数的弃用警告、失败调用形参回退，以及未知版本／损坏轨道阻断与原件副本；scanProjects(Path) 只读列出真实工程版本与迁移报告，执行结果按本轮记录确认。
- [子动画片段检查](../../src/main/java/org/unitego/lobecorp/client/photon/animation/validation/PhotonAnimationChildRegression.java)纳入动画运行回归：受控／独立时钟、延迟实例、跟随、父清理、owner 替换及零长度窗口。
  Photon NumberFunction 的静态注册需要 ModList，函数贡献检查也属于加载后入口；“独立”指夹具与当前特效隔离，不等于可脱离模组启动。
  未挂接 ModularUI 的配置器或同帧样式修改后，isDisplayed 读取的是计算缓存；隔离折叠断言使用原生 getValueImmediate(DISPLAY)，不改生产折叠处理。
  已有多实例及新增 FX 夹具只关闭各自 timelinePlayer 的音频派发，并在 finally 销毁；Data 没有 Minecraft 客户端音频管理器，不覆盖真实音频播放。
  真实 SerializableRecordAction 夹具使用独立 HistoryRuntime 提供有效宿主：Data 没有客户端 particleEngine，未 emit 的普通 FXRuntime.isValid=false 会被原查询正确关闭。
  该夹具保留原动作构造、快照、执行及撤销路径，仅隔离宿主有效性；不覆盖实际 emit、心跳和销毁判定，也不修改生产 isValid 保护。
  属性查询使用原类型的完整 path（如 transform.position），不能以显示名称或缺少前缀的 position 代替。
  经过控制器 NBT 保存的夹具必须使用 Photon 已注册的原生属性类型；匿名类型没有 Codec 注册键，会在 AnimatedPropertyType.serialize 写入 null 时失败，不应为此修改生产序列化或临时污染全局注册表。

已执行结果记在 [修改记录](changes.md)。历史验证：独立入口 38 项断言通过；
Data 调试进程中的已加载检查 4071 项断言通过，包含实例合成、参数隔离和触发。这些是此前结果，不能作为本轮新增检查通过的证据。
六项修复收尾时 main 74 项通过，完整加载回归曾因多实例第 6 项失败（expected=21、actual=54）中断；该历史失败保留在修改记录。
此前重复合成修复后，经 IDEA 执行 compileJava，编译成功；Data 正常运行退出码 0，中英文生成资源与执行前完全一致。
该轮在 Data 已加载阶段通过 IDEA 调试表达式执行完整 runLoaded，共 4188 项断言通过：时钟/参数 38、运行槽 4011、多实例 22、动画运行 56、编辑器配置 61。
该轮动画运行新增 27 项断言，已有 CurveClip 恢复检查取消手动释放；未再次运行独立 main。

此前共享时间轴与原检视整合：IDEA 编译成功并实际执行 compileJava；正常 Data 运行退出码 0。
Data 已加载阶段通过 IDEA 调试表达式执行完整 runLoaded，最终 4600 项断言通过，包含原 4188 项及新增共享时间轴与原生一致性 412 项。
新增检查包括模型隔离、名称字段身份、八类轨道 Codec、版本迁移、完整内容重定时、函数/整数/颜色/短角旋转原生对照、控制/信号顺序及后处理提交；录制冻结 37 项和真实原生历史 92 项包含在其中。
先前 4531 项通过后补查发现旋转停止恢复仍失败，追加 27 项固定基准恢复及 42 项多轴/350° ADD/重复合成/查询检查。写后实际值指纹与真实四元数恢复修复后，完整回归全部通过；不能用原通过数量代替追加检查结果。
检查中发现并修正查询 path、未 emit 宿主、四元数整体符号和匿名未注册类型四类夹具边界；保留历史断言和生产有效性保护，不用弱化断言或修改注册表绕过失败。
48 个相关 Java 最终 IDEA 诊断无错误或警告，保留独立 main 原 System.out 的日志点调试建议；Mixin 配置及相关规范/系统文档诊断无问题。仅格式化本次 Java 修改类或行，关闭导入优化和成员重排。
中英文生成资源各 721 个键，动画键各 174 个；相较本轮开始各新增 8 个预期键、更新 4 个文本/提示，无删除，其余语言条目不变。生成输出由 Data 写入，未手动编辑或格式化。
本轮未运行独立 main、Client 或点击游戏内测试菜单；模型、历史和后处理队列断言不覆盖真实窗口、音频与 GPU。
退出预览恢复原场景播放状态仍需客户端验收，隔离配置器断言不等于完整场景切换验证。
编译、Data、静态诊断和这些断言不代表视觉交互验收。

以下尚需客户端验收：

- 同一法阵并行展开/自旋/呼吸/淡出，停止指定动画、保持末帧和强制清理，全程同一粒子实例。
- 调整 50% 动画时长、循环周期与标记区间；多实例隔离、出生/持续参数、原生信号、延迟发射和旧时间轴共存。
- 八种原生轨道、嵌套分组、片段/多选编辑与录制；完整原检视名称锁定、未录制基础值修改、录制双写、基础撤销和重做。
- 前后拖动与定义刷新重模拟，首次 emit、延迟子实例及跨周期重放仍执行内部 START／MARKER／COMPLETE／SIGNAL 编排；外部音频、信号监听和回调静默。暂停时手动触发与受控子音频恢复另需验收。
- 底部 EndMode/Wrap 与右侧命名动画运行时长、HOLD/RELEASE 独立；正在播放时编辑和回拖后的继续行为。
- 反复修改后保持播放头、曲线视图、展开项、文本焦点和有效选择；撤销重做及动画切换不留下失效片段/标记引用。
- 浮动窗口布局、滚动、悬浮提示、文本输入、活动时间轴工具/快捷键、组合预览、窗口关闭及世界退出清理。
- version=1～5 弃用警告与损坏数据阻断报告、仅有弃用警告时的编辑／保存／导出，以及真正受阻时的原件保护；version=6 项目／.fx／FXPack 保存加载、子轨道复制／剪贴板引用映射及资源重载。
