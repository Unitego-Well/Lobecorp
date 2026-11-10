package org.unitego.lobecorp.registry.translation.photon.editor;

import org.unitego.lobecorp.registry.translation.TranslationRegistry;
import org.unitego.lobecorp.registry.translation.PhotonTranslationKeys;

import java.util.Set;

/// 编辑器扩展选项的悬浮说明及对应标签索引，不持有客户端控件。
public class PhotonEditorTooltipTranslations {
	public static final String TOOLTIP_SUFFIX = ".tooltip";
	private static final String CYCLE_ENABLED_ENGLISH = "Sample the module by particle age with an independent cycle; does not extend particle lifetime.";
	private static final String CYCLE_ENABLED_CHINESE = "按粒子已存活时间独立循环采样，不延长粒子寿命。";
	private static final String CYCLE_TICKS_ENGLISH = "Ticks for one complete curve cycle, at least one; independent of particle lifetime.";
	private static final String CYCLE_TICKS_CHINESE = "完整采样一次曲线的 tick 数，至少 1，与粒子寿命独立。";
	private static final String CYCLE_OFFSET_ENGLISH = "Sampling time offset in ticks, including negatives. Positive advances the phase; values are not shifted.";
	private static final String CYCLE_OFFSET_CHINESE = "采样时间偏移，单位 tick，可为负；正数提前相位，不是属性值增量。";
	private static final String SHORTCUT_TIP_ENGLISH = "Use CTRL/SHIFT/ALT + key; empty disables this action. Conflicting and reserved bindings are rejected.";
	private static final String SHORTCUT_TIP_CHINESE = "使用 CTRL/SHIFT/ALT + 按键；留空禁用此动作。冲突及保留绑定会被拒绝。";

	public static final String FEATURE_HOVER_STYLE_DELTA = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.HOVER_STYLE_DELTA_FEATURE) + TOOLTIP_SUFFIX,
			"Reduce style refresh work while hovering over editor controls.", "减少悬浮编辑器控件时的样式刷新开销。");
	public static final String FEATURE_SCALE_CURVE_LINK = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.SCALE_LINK_FEATURE) + TOOLTIP_SUFFIX,
			"Link scale XYZ keys and same-direction handles at matching times using the same delta; preserve axis differences. Missing keys are not added during dragging.", "按同一时间的已有关键帧联动缩放 XYZ 与同向手柄，使用相同增量并保留轴间差异；拖动时不自动补齐缺失关键帧。");
	public static final String FEATURE_CURVE_EASING = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.CURVE_EASING_FEATURE) + TOOLTIP_SUFFIX,
			"Choose a native curve easing preset when adding an animated property; the new curve and preset share one undo step.", "添加动画属性时选择原生曲线缓动预设；新增曲线与缓动共用一次撤销。");
	public static final String FEATURE_BATCH_CURVE_EASING = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.BATCH_EASING_FEATURE) + TOOLTIP_SUFFIX,
			"Apply an easing preset to marquee-selected keyframes using one undo step; unselected keyframe positions remain unchanged.", "对框选关键帧批量应用缓动预设，可一次撤销；未选中关键帧的位置保持不变。");
	public static final String FEATURE_COMPACT_WINDOW_TITLES = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.COMPACT_TITLE_FEATURE) + TOOLTIP_SUFFIX,
			"Photon window tabs show the original project type, file name and extension. Disable to restore native full-path titles.", "Photon 窗口标签保留原项目类型，仅显示文件名和后缀；关闭恢复原完整路径标题。");
	public static final String FEATURE_TITLE_PATH_CONFLICTS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.TITLE_CONFLICT_FEATURE) + TOOLTIP_SUFFIX,
			"Show complete paths when tabs in the same editor have matching project types and file names.", "同一编辑器内，类型和文件名相同的标签显示完整路径。");
	public static final String FEATURE_TITLE_PATH_TOOLTIP = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.TITLE_PATH_TOOLTIP_FEATURE) + TOOLTIP_SUFFIX,
			"Hover over a Photon window tab to see its complete file path, even when the title is compact.", "悬浮 Photon 窗口标签显示完整文件路径，即使标题已缩短也可查看。");
	public static final String FEATURE_TESTS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.TEST_FEATURE) + TOOLTIP_SUFFIX,
			"Show Tools > Tests. Checks use isolated objects and leave the open effect unchanged.", "显示“工具 > 测试”；检查使用隔离对象，不修改当前特效。");
	public static final String FEATURE_PRECISE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.precise") + TOOLTIP_SUFFIX,
			"Edit selected keyframe time and value precisely; locked or overlapping keys are rejected.", "精确修改选中关键帧的时间和值；锁定或时间冲突的关键帧不会修改。");
	public static final String FEATURE_TIME_SCALE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.time_scale") + TOOLTIP_SUFFIX,
			"Scale selected keyframe times around the earliest selected time.", "以最早选中时间为基准缩放选中关键帧的时间。");
	public static final String FEATURE_BATCH_MOVE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.batch_move") + TOOLTIP_SUFFIX,
			"Move selected keys across tracks with time and value offsets.", "跨轨道移动选中关键帧，可指定时间增量和数值增量。");
	public static final String FEATURE_FIT = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.fit") + TOOLTIP_SUFFIX,
			"Fit selected or all curves in the timeline view.", "按选中内容或全部曲线适配时间轴视图。");
	public static final String FEATURE_TANGENTS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.tangents") + TOOLTIP_SUFFIX,
			"Enable curve presets and tangent editing.", "显示曲线预设、切线对齐、打断和重置入口。");
	public static final String FEATURE_RANGE_PREVIEW = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.range_preview") + TOOLTIP_SUFFIX,
			"Preview a local timeline range, optionally in a loop.", "按指定起止时间预览，可在该区间内循环。");
	public static final String FEATURE_PARAMETERS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.parameters") + TOOLTIP_SUFFIX,
			"Temporarily override preview size, speed, color and beam dimensions; closing restores timeline control.", "临时调整预览大小、速度、颜色和光柱尺寸；关闭面板恢复时间轴控制。");
	public static final String FEATURE_BATCH_PROPERTIES = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.batch_properties") + TOOLTIP_SUFFIX,
			"Apply only checked properties to selected emitters.", "批量修改选中发射器，只应用已勾选的字段。");
	public static final String FEATURE_PRESETS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.presets") + TOOLTIP_SUFFIX,
			"Copy modules or save and apply module presets in editor settings.", "复制模块，或在编辑器设置中保存、应用模块预设。");
	public static final String FEATURE_SOLO = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.solo") + TOOLTIP_SUFFIX,
			"Preview selected branches and their parents in isolation.", "独显选中分支及其父级；再次操作恢复预览。");
	public static final String FEATURE_SEED = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.seed") + TOOLTIP_SUFFIX,
			"Expose the fixed random seed editor for reproducible local previews.", "显示固定随机种子入口，便于重复比较本地预览。");
	public static final String FEATURE_STEP = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.step") + TOOLTIP_SUFFIX,
			"Step the preview forward or backward by ticks.", "按 tick 前进或后退预览。");
	public static final String FEATURE_DIAGNOSTICS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.diagnostics") + TOOLTIP_SUFFIX,
			"Show read-only render state and resource information.", "只读显示渲染状态和材质资源信息。");
	public static final String FEATURE_SHORTCUTS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.shortcuts") + TOOLTIP_SUFFIX,
			"Enable custom editor shortcuts; text fields retain their own keyboard input.", "启用扩展快捷键；文本输入框保留原键盘输入。");
	public static final String FEATURE_CURVE_PAN = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.curve_pan") + TOOLTIP_SUFFIX,
			"Pan the curve view with the middle mouse button without changing keyframes.", "中键拖动平移曲线视图，不改变关键帧数据。");
	public static final String FEATURE_AXIS_LOCK = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.axis_lock") + TOOLTIP_SUFFIX,
			"Hold Shift while dragging curve data to lock the dominant movement axis.", "Shift 拖动曲线数据时按主要方向锁定水平或垂直移动。");
	public static final String FEATURE_ROTATION_CYCLE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.rotation_cycle") + TOOLTIP_SUFFIX,
			"Show rotation cycle controls; saved runtime settings remain active when hidden.", "显示旋转循环控件；关闭仅隐藏控件，已保存的循环继续生效。");
	public static final String FEATURE_COLOR_CYCLE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.color_cycle") + TOOLTIP_SUFFIX,
			"Show color cycle controls; saved runtime settings remain active when hidden.", "显示颜色循环控件；关闭仅隐藏控件，已保存的循环继续生效。");
	public static final String FEATURE_SIZE_CYCLE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.size_cycle") + TOOLTIP_SUFFIX,
			"Show size cycle controls; saved runtime settings remain active when hidden.", "显示大小循环控件；关闭仅隐藏控件，已保存的循环继续生效。");
	public static final String FEATURE_VELOCITY_CYCLE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.velocity_cycle") + TOOLTIP_SUFFIX,
			"Show velocity cycle controls; saved runtime settings remain active when hidden.", "显示速度循环控件；关闭仅隐藏控件，已保存的循环继续生效。");
	public static final String FEATURE_FORCE_CYCLE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.force_cycle") + TOOLTIP_SUFFIX,
			"Show force cycle controls; saved runtime settings remain active when hidden.", "显示受力循环控件；关闭仅隐藏控件，已保存的循环继续生效。");
	public static final String FEATURE_UV_CYCLE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.uv_cycle") + TOOLTIP_SUFFIX,
			"Show UV cycle controls; saved runtime settings remain active when hidden.", "显示 UV 循环控件；关闭仅隐藏控件，已保存的循环继续生效。");
	public static final String FEATURE_TIME_OFFSET = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.time_offset") + TOOLTIP_SUFFIX,
			"Show cycle time offset controls; offsets change sampling time rather than values.", "显示循环时间偏移控件；偏移改变采样时间，不给属性值增加数值。");
	public static final String FEATURE_OPAQUE_OCCLUSION = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEditorTranslations.OCCLUSION_FEATURE) + TOOLTIP_SUFFIX,
			"Show optional opaque-pixel occlusion controls. Hiding them preserves saved emitter settings and their runtime behavior.", "显示可选的不透明像素遮挡控件；隐藏不修改发射器已保存的设置和运行行为。");
	public static final String FEATURE_DEPTH_SORT = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.depth_sort") + TOOLTIP_SUFFIX,
			"Show view-depth sorting controls; hiding does not change saved sorting.", "显示视线深度排序控件；隐藏不改变已保存的排序设置。");
	public static final String FEATURE_CONTROL = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.control") + TOOLTIP_SUFFIX,
			"Show activator start and end options. Enabling only one boundary makes it a one-time event at that boundary.", "显示激活轨道开始和结束选项；仅开启一端时，在对应边界执行一次。");
	public static final String FEATURE_SYSTEM_CLIPBOARD = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.system_clipboard") + TOOLTIP_SUFFIX,
			"Use the system clipboard for supported editor data and text; arbitrary JSON is not converted to effects.", "对支持的编辑器数据和文本使用系统剪切板；普通 JSON 不自动转成特效。");
	public static final String FEATURE_RENAME_DIALOG = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature.rename_dialog") + TOOLTIP_SUFFIX,
			"Use native rename dialogs while retaining uniqueness, validation and history.", "使用原生重命名小窗口，保留名称校验、唯一性和操作历史。");
	public static final String FEATURE_EMITTER_SPAWNER = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "feature." + PhotonEmitterSpawnerTranslations.FEATURE) + TOOLTIP_SUFFIX,
			"Show emitter spawner controls; hiding them does not stop saved effects.", "显示发射器发射器控件；隐藏不会停止已保存的特效。");
	public static final String EMITTER_SPAWNER_ENABLED = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.ENABLED + TOOLTIP_SUFFIX,
			"Stop new instances when disabled; existing instances continue updating.", "关闭后停止新增实例，已有实例继续更新。");
	public static final String EMITTER_SPAWNER_SOURCE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SOURCE + TOOLTIP_SUFFIX,
			"Use a project emitter subtree or a saved FX with its complete timeline.", "选择项目发射器子树，或带完整时间轴的已保存 FX。");
	public static final String EMITTER_SPAWNER_TEMPLATE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.TEMPLATE + TOOLTIP_SUFFIX,
			"Drag an emitter from the hierarchy here. The template itself keeps its original playback.", "把层级中的发射器拖到此处；模板自身仍按原逻辑播放。");
	public static final String EMITTER_SPAWNER_CLEAR_TEMPLATE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.CLEAR_TEMPLATE + TOOLTIP_SUFFIX,
			"Clear this reference without deleting the template object or existing spawned instances.", "清除此引用，不删除模板对象或已生成的实例。");
	public static final String EMITTER_SPAWNER_FX = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.FX + TOOLTIP_SUFFIX,
			"Choose an available saved FX resource ID. Missing resources reject the current spawn.", "选择可用的已保存 FX 资源 ID；资源缺失时拒绝本次生成。");
	public static final String EMITTER_SPAWNER_INCLUDE_CHILDREN = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INCLUDE_CHILDREN + TOOLTIP_SUFFIX,
			"Copy the project template children along with the selected emitter.", "复制项目模板时同时包含所选发射器的子对象。");
	public static final String EMITTER_SPAWNER_DURATION = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.DURATION + TOOLTIP_SUFFIX,
			"Length of one emission cycle in ticks; at least one tick. Does not set child particle lifetime.", "一个发射周期的长度，单位 tick，至少 1；不是子粒子的寿命。");
	public static final String EMITTER_SPAWNER_LOOPING = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.LOOPING + TOOLTIP_SUFFIX,
			"Repeat emission cycles. One-shot mode still triggers only once per start.", "重复发射周期；一次性发射模式仍然每次启动只触发一次。");
	public static final String EMITTER_SPAWNER_START_DELAY = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.START_DELAY + TOOLTIP_SUFFIX,
			"Delay the first eligible emission in ticks; not repeated at each cycle.", "首次发射前等待的 tick 数，不在每周期重复等待。");
	public static final String EMITTER_SPAWNER_MODE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.MODE + TOOLTIP_SUFFIX,
			"Once per cycle repeats at each cycle; one shot triggers once per start. Interval uses count; rate accumulates instances per tick.", "每周期一次随周期重复；一次性发射每次启动只触发一次。按间隔使用每次实例数；按速率累计每 tick 数量。");
	public static final String EMITTER_SPAWNER_INTERVAL = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INTERVAL + TOOLTIP_SUFFIX,
			"Ticks between triggers in interval mode; at least one tick.", "按间隔模式两次触发之间的 tick 数，至少 1。");
	public static final String EMITTER_SPAWNER_COUNT = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.COUNT + TOOLTIP_SUFFIX,
			"Requested instances per trigger in once-per-cycle, one-shot and interval modes; limits and probability still apply.", "每周期一次、一次性和按间隔模式每次尝试生成的实例数；仍受概率和数量上限限制。");
	public static final String EMITTER_SPAWNER_RATE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.RATE + TOOLTIP_SUFFIX,
			"Instances per tick in rate mode; fractional amounts accumulate. One instance per tick is twenty per second.", "按速率模式每 tick 的实例数，小数会累计；每 tick 1 个相当于每秒 20 个。");
	public static final String EMITTER_SPAWNER_PROBABILITY = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.PROBABILITY + TOOLTIP_SUFFIX,
			"Independent probability for each attempt, from zero to one. Failed one-shot attempts are not retried.", "每个尝试独立判断概率，范围 0 到 1；一次性发射失败不会自动重试。");
	public static final String EMITTER_SPAWNER_MAX_INSTANCES = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.MAX_INSTANCES + TOOLTIP_SUFFIX,
			"Maximum simultaneous live instances. Zero prevents spawning; excess requests are discarded.", "同时存活的实例上限，0 表示不生成；超出的请求丢弃。");
	public static final String EMITTER_SPAWNER_MAX_TOTAL = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.MAX_TOTAL + TOOLTIP_SUFFIX,
			"Successful instance limit since restart; zero is unlimited. Restart resets the counter.", "自重启以来成功生成实例的上限，0 表示不限；重启重置计数。");
	public static final String EMITTER_SPAWNER_MAX_PER_TICK = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.MAX_PER_TICK + TOOLTIP_SUFFIX,
			"Maximum spawn attempts per game tick, shared by simulation substeps and manual calls; excess is not queued.", "每游戏 tick 的尝试次数上限，模拟子步和手动调用共用；超出部分不积压补发。");
	public static final String EMITTER_SPAWNER_MAX_DEPTH = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.MAX_DEPTH + TOOLTIP_SUFFIX,
			"Maximum nested source depth; recursive source chains are rejected.", "允许嵌套发射的最大来源深度；循环来源会被拒绝。");
	public static final String EMITTER_SPAWNER_INSTANCE_LIFETIME = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INSTANCE_LIFETIME + TOOLTIP_SUFFIX,
			"Child instance playback ticks before soft stop; zero retains the source lifetime. Infinite particles still require cleanup.", "生成实例播放多少 tick 后柔和停止；0 沿用来源寿命。无限寿命粒子仍需清理。");
	public static final String EMITTER_SPAWNER_END_BEHAVIOR = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.END_BEHAVIOR + TOOLTIP_SUFFIX,
			"At the end of non-looping emission, keep instances, stop their emission, or clear them immediately.", "非循环发射结束时，可保留已有实例、停止其发射或立即清理。");
	public static final String EMITTER_SPAWNER_CLEAR_ON_RESTART = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.CLEAR_ON_RESTART + TOOLTIP_SUFFIX,
			"Clear existing instances on restart; explicit API or activator-track choices take precedence.", "重启时清理已有实例；API 或激活轨道明确指定的清理选择优先。");
	public static final String EMITTER_SPAWNER_FOLLOW = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.FOLLOW + TOOLTIP_SUFFIX,
			"Keep spawned anchors following the spawner transform; independent motion is added on top.", "生成实例的锚点继续跟随发射器变换，独立运动位移叠加其上。");
	public static final String EMITTER_SPAWNER_INHERIT_ROTATION = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INHERIT_ROTATION + TOOLTIP_SUFFIX,
			"Apply spawner rotation to spawned anchors and launch direction.", "将发射器旋转应用到生成实例的锚点和移动方向。");
	public static final String EMITTER_SPAWNER_INHERIT_SCALE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INHERIT_SCALE + TOOLTIP_SUFFIX,
			"Apply spawner scale to spawned instance anchors.", "将发射器缩放应用到生成实例锚点。");
	public static final String EMITTER_SPAWNER_INHERIT_COLOR = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INHERIT_COLOR + TOOLTIP_SUFFIX,
			"Multiply spawned effects by the spawner RGBA color, including alpha.", "使用发射器 RGBA 颜色乘色生成效果，包含透明度。");
	public static final String EMITTER_SPAWNER_INHERIT_VELOCITY = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.INHERIT_VELOCITY + TOOLTIP_SUFFIX,
			"Add spawner world movement velocity to independent instance motion.", "在实例独立运动中加入发射器的世界位移速度。");
	public static final String EMITTER_SPAWNER_LAUNCH_SPEED = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.LAUNCH_SPEED + TOOLTIP_SUFFIX,
			"Independent instance speed in blocks per tick, directed away from shape center; center uses local positive Z.", "实例独立移动速度，单位格/tick；沿形状中心向外，中心点使用本地 +Z。");
	public static final String EMITTER_SPAWNER_SHAPE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SHAPE + TOOLTIP_SUFFIX,
			"Configure spawn position distribution and local offset.", "配置发射位置分布和本地偏移。");
	public static final String EMITTER_SPAWNER_SHAPE_TYPE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SHAPE_TYPE + TOOLTIP_SUFFIX,
			"Point, box volume, sphere volume, or circle area in the local XZ plane.", "点、盒体积分布、球体积分布或本地 XZ 平面的圆面积分布。");
	public static final String EMITTER_SPAWNER_SHAPE_SIZE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SHAPE_SIZE + TOOLTIP_SUFFIX,
			"Box half-extents or sphere/circle radii in blocks; components permit ellipsoids and ellipses.", "盒的半边长或球/圆半径，单位格；各分量可形成椭球或椭圆。");
	public static final String EMITTER_SPAWNER_SHAPE_OFFSET = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SHAPE_OFFSET + TOOLTIP_SUFFIX,
			"Offset the spawn shape center in spawner local space, in blocks.", "在发射器本地坐标中偏移发射形状中心，单位格。");
	public static final String EMITTER_SPAWNER_PARAMETERS = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.PARAMETERS + TOOLTIP_SUFFIX,
			"Override parameters in newly spawned copies; source templates and existing copies are preserved.", "覆盖新生成副本的参数，保留来源模板和已有副本。");
	public static final String EMITTER_SPAWNER_TARGET = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.TARGET + TOOLTIP_SUFFIX,
			"Exact emitter name to receive overrides; empty applies to all compatible emitters in the copy.", "按发射器名称精确匹配参数目标；留空应用到副本内所有兼容发射器。");
	public static final String EMITTER_SPAWNER_OVERRIDE_LIFETIME = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.OVERRIDE_LIFETIME + TOOLTIP_SUFFIX,
			"Replace start lifetime for new particles in spawned copies.", "替换生成副本中新粒子的起始寿命。");
	public static final String EMITTER_SPAWNER_LIFETIME = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.LIFETIME + TOOLTIP_SUFFIX,
			"New particle start lifetime in ticks; zero retains the native particle-type meaning.", "新粒子的起始寿命，单位 tick；0 的意义沿用原粒子类型。");
	public static final String EMITTER_SPAWNER_OVERRIDE_SIZE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.OVERRIDE_SIZE + TOOLTIP_SUFFIX,
			"Replace new particle start size in spawned copies.", "替换生成副本中新粒子的初始大小。");
	public static final String EMITTER_SPAWNER_SIZE = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SIZE + TOOLTIP_SUFFIX,
			"New particle initial XYZ size; effective only when its override is enabled.", "新粒子的初始 XYZ 大小，仅开启对应覆盖时生效。");
	public static final String EMITTER_SPAWNER_OVERRIDE_COLOR = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.OVERRIDE_COLOR + TOOLTIP_SUFFIX,
			"Replace particle start color or beam color in spawned copies.", "替换生成副本中粒子初始颜色或光束颜色。");
	public static final String EMITTER_SPAWNER_COLOR = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.COLOR + TOOLTIP_SUFFIX,
			"Initial RGBA color, including alpha; effective only when its override is enabled.", "初始 RGBA 颜色，包含透明度，仅开启对应覆盖时生效。");
	public static final String EMITTER_SPAWNER_OVERRIDE_SPEED = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.OVERRIDE_SPEED + TOOLTIP_SUFFIX,
			"Replace new particle start speed in spawned copies.", "替换生成副本中新粒子的初始速度。");
	public static final String EMITTER_SPAWNER_SPEED = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.SPEED + TOOLTIP_SUFFIX,
			"New particle initial speed; effective only when its override is enabled.", "新粒子的初始速度，仅开启对应覆盖时生效。");
	public static final String EMITTER_SPAWNER_OVERRIDE_BEAM_LENGTH = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.OVERRIDE_BEAM_LENGTH + TOOLTIP_SUFFIX,
			"Override beam length in spawned copies.", "覆盖生成副本中的光柱长度。");
	public static final String EMITTER_SPAWNER_BEAM_LENGTH = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.BEAM_LENGTH + TOOLTIP_SUFFIX,
			"Beam length in blocks; applies only to compatible beams with the override enabled.", "光柱长度，单位格；仅对开启覆盖的兼容光束生效。");
	public static final String EMITTER_SPAWNER_OVERRIDE_BEAM_WIDTH = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.OVERRIDE_BEAM_WIDTH + TOOLTIP_SUFFIX,
			"Override beam width in spawned copies.", "覆盖生成副本中的光柱宽度。");
	public static final String EMITTER_SPAWNER_BEAM_WIDTH = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.BEAM_WIDTH + TOOLTIP_SUFFIX,
			"Beam width in blocks; applies only to compatible beams with the override enabled.", "光柱宽度，单位格；仅对开启覆盖的兼容光束生效。");
	public static final String EMITTER_SPAWNER_PLAYBACK_SPEED = TranslationRegistry.register(PhotonEmitterSpawnerTranslations.PLAYBACK_SPEED + TOOLTIP_SUFFIX,
			"Multiply child timeline and simulation speed; zero freezes, one is normal, maximum sixteen.", "同时调整实例时间轴和模拟速度；0 冻结，1 正常，最大 16。");
	public static final String TIME = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "time") + TOOLTIP_SUFFIX,
			"Selected keyframe time in ticks; invalid or overlapping times are rejected.", "选中关键帧的时间，单位 tick；无效或冲突时间会被拒绝。");
	public static final String VALUE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "value") + TOOLTIP_SUFFIX,
			"Selected keyframe value; changing it does not shift sampling time.", "选中关键帧的属性值，修改不会偏移采样时间。");
	public static final String TIME_DELTA = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "time_delta") + TOOLTIP_SUFFIX,
			"Add this many ticks to selected keyframe times.", "给选中关键帧的时间增加该 tick 数。");
	public static final String VALUE_DELTA = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "value_delta") + TOOLTIP_SUFFIX,
			"Add this amount to selected keyframe values.", "给选中关键帧的属性值增加该数值。");
	public static final String FACTOR = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "factor") + TOOLTIP_SUFFIX,
			"Scale time around the earliest selected keyframe; tangents follow the time change.", "以最早选中关键帧为基准缩放时间，切线随时间变更。");
	public static final String PREVIEW_START = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "preview_start") + TOOLTIP_SUFFIX,
			"Start of the local preview range in ticks.", "局部预览区间的起点，单位 tick。");
	public static final String PREVIEW_END = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "preview_end") + TOOLTIP_SUFFIX,
			"End of the local preview range in ticks; must be after its start.", "局部预览区间的终点，单位 tick，必须晚于起点。");
	public static final String PREVIEW_LOOP = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "preview_loop") + TOOLTIP_SUFFIX,
			"Repeat only the specified preview range.", "仅在指定预览区间内反复播放。");
	public static final String CONTROL_START = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "control.start") + TOOLTIP_SUFFIX,
			"Apply the start behavior at the clip start. With end control disabled, execute it once and keep its result; with both enabled, control the clip interval.", "在片段起点执行开始行为；仅开启开始时执行一次并保持结果，同时开启结束时按片段区间控制。");
	public static final String CONTROL_END = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "control.end") + TOOLTIP_SUFFIX,
			"Apply the end behavior at the clip end. With start control disabled, execute it once; with both enabled, control the clip interval.", "在片段终点执行结束行为；仅开启结束时执行一次，同时开启开始时按片段区间控制。");
	public static final String CONTROL_START_BEHAVIOR = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "control.start_behavior") + TOOLTIP_SUFFIX,
			"Restart resets the subtree; continue preserves its current running state.", "重启重置子树运行状态；继续保留当前运行状态。");
	public static final String CONTROL_END_BEHAVIOR = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "control.end_behavior") + TOOLTIP_SUFFIX,
			"Original hides and stops updates; stop emission keeps particles updating; clear removes existing particles.", "原行为隐藏并停止更新；仅停止生成保留已有粒子更新；清理移除已有粒子。");
	public static final String CONTROL_CLEAR_ON_RESTART = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "control.clear_on_restart") + TOOLTIP_SUFFIX,
			"Clear existing particles when restarting; disabled keeps them while resetting emission time.", "重新启动时清理已有粒子；关闭后保留已有粒子，但仍重置发射计时。");
	public static final String BATCH_MATERIALS = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "batch_materials") + TOOLTIP_SUFFIX,
			"Copy the displayed source material list to selected emitters when checked.", "勾选后将显示的来源材质列表复制到选中发射器。");
	public static final String BATCH_CYCLES = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "batch_cycles") + TOOLTIP_SUFFIX,
			"Copy displayed source cycle settings to selected emitters when checked.", "勾选后将显示的来源循环设置复制到选中发射器。");
	public static final String BATCH_SOURCE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "batch_source") + TOOLTIP_SUFFIX,
			"Source emitter for material and cycle copies.", "复制材质和循环设置时使用的来源发射器。");
	public static final String PRESET_NAME = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "preset_name") + TOOLTIP_SUFFIX,
			"Name for the saved module preset in editor settings.", "保存在编辑器设置中的模块预设名称。");
	public static final String MODULE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "module") + TOOLTIP_SUFFIX,
			"Choose the emitter module to copy, save or apply.", "选择要复制、保存或应用的发射器模块。");
	public static final String DEPTH_TEST = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "depth_test") + TOOLTIP_SUFFIX,
			"Test against scene depth so geometry behind nearer surfaces can be hidden.", "与场景深度比较，使被较近表面遮挡的几何不显示。");
	public static final String DEPTH_WRITE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "depth_write") + TOOLTIP_SUFFIX,
			"Write depth; transparent surfaces can then hide particles behind them.", "写入深度；透明表面也可能因此遮住其后的粒子。");
	public static final String LIFETIME_CYCLE_ENABLED = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_LIFETIME_CYCLE_ENABLED_KEY + TOOLTIP_SUFFIX,
			CYCLE_ENABLED_ENGLISH, CYCLE_ENABLED_CHINESE);
	public static final String ROTATION_CYCLE_ENABLED = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_ROTATION_CYCLE_ENABLED_KEY + TOOLTIP_SUFFIX,
			"Loop rotation by particle age and accumulate full-cycle angle changes to keep spinning.", "按粒子已存活时间循环旋转，累积整周期角度差以保持连续自旋。");
	public static final String LIFETIME_CYCLE_TICKS = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_LIFETIME_CYCLE_TICKS_KEY + TOOLTIP_SUFFIX,
			CYCLE_TICKS_ENGLISH, CYCLE_TICKS_CHINESE);
	public static final String ROTATION_CYCLE_TICKS = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_ROTATION_CYCLE_TICKS_KEY + TOOLTIP_SUFFIX,
			CYCLE_TICKS_ENGLISH, CYCLE_TICKS_CHINESE);
	public static final String CYCLE_TIME_OFFSET_AXES = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_TIME_OFFSET_AXES_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_TIME_OFFSET_ROTATION = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_TIME_OFFSET_LINEAR = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_TIME_OFFSET_ORBITAL = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_TIME_OFFSET_CENTER = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_TIME_OFFSET_UV = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_TIME_OFFSET_UV_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_X = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_X_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_Y = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_Y_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_Z = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_Z_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_ROLL = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_ROLL_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_PITCH = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_PITCH_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_YAW = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_YAW_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_ORBITAL_X = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_ORBITAL_X_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_ORBITAL_Y = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_ORBITAL_Y_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_ORBITAL_Z = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_ORBITAL_Z_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_CENTER_X = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_CENTER_X_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_CENTER_Y = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_CENTER_Y_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_CENTER_Z = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_CENTER_Z_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_RADIAL = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_RADIAL_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_MULTIPLIER = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_RED = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_RED_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_GREEN = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_GREEN_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_BLUE = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_BLUE_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String CYCLE_OFFSET_ALPHA = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_CYCLE_OFFSET_ALPHA_KEY + TOOLTIP_SUFFIX,
			CYCLE_OFFSET_ENGLISH, CYCLE_OFFSET_CHINESE);
	public static final String RENDERER_VIEW_DEPTH_SORT = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_VIEW_DEPTH_SORT_KEY + TOOLTIP_SUFFIX,
			"Sort enabled emitters with the same Order by view depth. Enable on all overlapping emitters; intersecting geometry remains unresolved.", "相同 Order 下按视线深度排序；需在互相叠加的发射器上同时开启，无法解决面片相交。");
	public static final String RENDERER_OPAQUE_OCCLUSION = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_OCCLUSION_KEY + TOOLTIP_SUFFIX,
			"Prewrite occlusion depth for sufficiently opaque pixels of supported native materials, then retain Photon color rendering. Transparent pixels keep native blending. Disabled uses the original render path.", "为支持的原生材质中足够不透明的像素预写遮挡深度，再沿用 Photon 绘制颜色；透明像素保留原混合，关闭后沿原渲染路径处理。");
	public static final String RENDERER_OPAQUE_OCCLUSION_ALPHA_CUTOFF = TranslationRegistry.register(PhotonTranslationKeys.PHOTON_OCCLUSION_ALPHA_CUTOFF_KEY + TOOLTIP_SUFFIX,
			"Only pixels whose effective alpha is at or above this threshold write occlusion depth. Range zero to one; texture and particle fades still contribute to alpha.", "仅有效 Alpha 达到此阈值的像素写入遮挡深度；范围 0 到 1，纹理透明度和粒子淡出仍参与判定。");
	public static final String ACTION_FIT_SELECTED = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.fit_selected") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_FIT_ALL = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.fit_all") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_STEP_BACK = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.step_back") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_STEP_FORWARD = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.step_forward") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_STEP_BACK_LARGE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.step_back_large") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_STEP_FORWARD_LARGE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.step_forward_large") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_DUPLICATE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.duplicate") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_MOVE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.move") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_SCALE = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.scale") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_RANGE_IN = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.range_in") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_RANGE_OUT = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.range_out") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_RANGE_LOOP = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.range_loop") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_SOLO = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.solo") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);
	public static final String ACTION_CANCEL = TranslationRegistry.register((PhotonEditorTranslations.PREFIX + "action.cancel") + TOOLTIP_SUFFIX,
			SHORTCUT_TIP_ENGLISH, SHORTCUT_TIP_CHINESE);

	private static final Set<String> TIPS = Set.of(
			FEATURE_HOVER_STYLE_DELTA,
			FEATURE_SCALE_CURVE_LINK,
			FEATURE_CURVE_EASING,
			FEATURE_BATCH_CURVE_EASING,
			FEATURE_COMPACT_WINDOW_TITLES,
			FEATURE_TITLE_PATH_CONFLICTS,
			FEATURE_TITLE_PATH_TOOLTIP,
			FEATURE_TESTS,
			FEATURE_PRECISE,
			FEATURE_TIME_SCALE,
			FEATURE_BATCH_MOVE,
			FEATURE_FIT,
			FEATURE_TANGENTS,
			FEATURE_RANGE_PREVIEW,
			FEATURE_PARAMETERS,
			FEATURE_BATCH_PROPERTIES,
			FEATURE_PRESETS,
			FEATURE_SOLO,
			FEATURE_SEED,
			FEATURE_STEP,
			FEATURE_DIAGNOSTICS,
			FEATURE_SHORTCUTS,
			FEATURE_CURVE_PAN,
			FEATURE_AXIS_LOCK,
			FEATURE_ROTATION_CYCLE,
			FEATURE_COLOR_CYCLE,
			FEATURE_SIZE_CYCLE,
			FEATURE_VELOCITY_CYCLE,
			FEATURE_FORCE_CYCLE,
			FEATURE_UV_CYCLE,
			FEATURE_TIME_OFFSET,
			FEATURE_OPAQUE_OCCLUSION,
			FEATURE_DEPTH_SORT,
			FEATURE_CONTROL,
			FEATURE_SYSTEM_CLIPBOARD,
			FEATURE_RENAME_DIALOG,
			FEATURE_EMITTER_SPAWNER,
			EMITTER_SPAWNER_ENABLED,
			EMITTER_SPAWNER_SOURCE,
			EMITTER_SPAWNER_TEMPLATE,
			EMITTER_SPAWNER_CLEAR_TEMPLATE,
			EMITTER_SPAWNER_FX,
			EMITTER_SPAWNER_INCLUDE_CHILDREN,
			EMITTER_SPAWNER_DURATION,
			EMITTER_SPAWNER_LOOPING,
			EMITTER_SPAWNER_START_DELAY,
			EMITTER_SPAWNER_MODE,
			EMITTER_SPAWNER_INTERVAL,
			EMITTER_SPAWNER_COUNT,
			EMITTER_SPAWNER_RATE,
			EMITTER_SPAWNER_PROBABILITY,
			EMITTER_SPAWNER_MAX_INSTANCES,
			EMITTER_SPAWNER_MAX_TOTAL,
			EMITTER_SPAWNER_MAX_PER_TICK,
			EMITTER_SPAWNER_MAX_DEPTH,
			EMITTER_SPAWNER_INSTANCE_LIFETIME,
			EMITTER_SPAWNER_END_BEHAVIOR,
			EMITTER_SPAWNER_CLEAR_ON_RESTART,
			EMITTER_SPAWNER_FOLLOW,
			EMITTER_SPAWNER_INHERIT_ROTATION,
			EMITTER_SPAWNER_INHERIT_SCALE,
			EMITTER_SPAWNER_INHERIT_COLOR,
			EMITTER_SPAWNER_INHERIT_VELOCITY,
			EMITTER_SPAWNER_LAUNCH_SPEED,
			EMITTER_SPAWNER_SHAPE,
			EMITTER_SPAWNER_SHAPE_TYPE,
			EMITTER_SPAWNER_SHAPE_SIZE,
			EMITTER_SPAWNER_SHAPE_OFFSET,
			EMITTER_SPAWNER_PARAMETERS,
			EMITTER_SPAWNER_TARGET,
			EMITTER_SPAWNER_OVERRIDE_LIFETIME,
			EMITTER_SPAWNER_LIFETIME,
			EMITTER_SPAWNER_OVERRIDE_SIZE,
			EMITTER_SPAWNER_SIZE,
			EMITTER_SPAWNER_OVERRIDE_COLOR,
			EMITTER_SPAWNER_COLOR,
			EMITTER_SPAWNER_OVERRIDE_SPEED,
			EMITTER_SPAWNER_SPEED,
			EMITTER_SPAWNER_OVERRIDE_BEAM_LENGTH,
			EMITTER_SPAWNER_BEAM_LENGTH,
			EMITTER_SPAWNER_OVERRIDE_BEAM_WIDTH,
			EMITTER_SPAWNER_BEAM_WIDTH,
			EMITTER_SPAWNER_PLAYBACK_SPEED,
			TIME,
			VALUE,
			TIME_DELTA,
			VALUE_DELTA,
			FACTOR,
			PREVIEW_START,
			PREVIEW_END,
			PREVIEW_LOOP,
			CONTROL_START,
			CONTROL_END,
			CONTROL_START_BEHAVIOR,
			CONTROL_END_BEHAVIOR,
			CONTROL_CLEAR_ON_RESTART,
			BATCH_MATERIALS,
			BATCH_CYCLES,
			BATCH_SOURCE,
			PRESET_NAME,
			MODULE,
			DEPTH_TEST,
			DEPTH_WRITE,
			LIFETIME_CYCLE_ENABLED,
			ROTATION_CYCLE_ENABLED,
			LIFETIME_CYCLE_TICKS,
			ROTATION_CYCLE_TICKS,
			CYCLE_TIME_OFFSET_AXES,
			CYCLE_TIME_OFFSET_ROTATION,
			CYCLE_TIME_OFFSET_LINEAR,
			CYCLE_TIME_OFFSET_ORBITAL,
			CYCLE_TIME_OFFSET_CENTER,
			CYCLE_TIME_OFFSET_UV,
			CYCLE_OFFSET_X,
			CYCLE_OFFSET_Y,
			CYCLE_OFFSET_Z,
			CYCLE_OFFSET_ROLL,
			CYCLE_OFFSET_PITCH,
			CYCLE_OFFSET_YAW,
			CYCLE_OFFSET_ORBITAL_X,
			CYCLE_OFFSET_ORBITAL_Y,
			CYCLE_OFFSET_ORBITAL_Z,
			CYCLE_OFFSET_CENTER_X,
			CYCLE_OFFSET_CENTER_Y,
			CYCLE_OFFSET_CENTER_Z,
			CYCLE_OFFSET_RADIAL,
			CYCLE_OFFSET_MULTIPLIER,
			CYCLE_OFFSET_RED,
			CYCLE_OFFSET_GREEN,
			CYCLE_OFFSET_BLUE,
			CYCLE_OFFSET_ALPHA,
			RENDERER_VIEW_DEPTH_SORT,
			RENDERER_OPAQUE_OCCLUSION,
			RENDERER_OPAQUE_OCCLUSION_ALPHA_CUTOFF,
			ACTION_FIT_SELECTED,
			ACTION_FIT_ALL,
			ACTION_STEP_BACK,
			ACTION_STEP_FORWARD,
			ACTION_STEP_BACK_LARGE,
			ACTION_STEP_FORWARD_LARGE,
			ACTION_DUPLICATE,
			ACTION_MOVE,
			ACTION_SCALE,
			ACTION_RANGE_IN,
			ACTION_RANGE_OUT,
			ACTION_RANGE_LOOP,
			ACTION_SOLO,
			ACTION_CANCEL);

	public static boolean hasTooltip(String name) {
		return name != null && TIPS.contains(name + TOOLTIP_SUFFIX);
	}

}
