package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.util.TranslationKeys;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扩展选项的悬浮说明，复用 LDLib2 原生配置行提示图标。
 */
public class PhotonEditorTooltipUtil {
	/**
	 * 配置标签对应的悬浮说明翻译后缀。
	 */
	public static final String TOOLTIP_SUFFIX = ".tooltip";
	/**
	 * 仅记录本项目扩展控件；不改变原生配置行的说明。
	 */
	private static final Map<String, Tip> TIPS = createTips();

	public static boolean hasTooltip(String name) {
		return name != null && TIPS.containsKey(name);
	}

	public static void apply(Configurator configurator, String name) {
		if (!hasTooltip(name)) {
			return;
		}

		configurator.tip.getStyle().tooltips(name + TOOLTIP_SUFFIX);
		configurator.tip.setDisplay(true);
	}

	public static void register() {
		TIPS.forEach((name, tip) -> LangHandler.creates(Lobecorp.NAMESPACE, name + TOOLTIP_SUFFIX,
				tip.english(), tip.chinese()));
	}

	private static Map<String, Tip> createTips() {
		var tips = new LinkedHashMap<String, Tip>();
		addFeatureTips(tips);
		addSpawnerTips(tips);
		addParameterTips(tips);
		addEditorTips(tips);
		addCycleTips(tips);
		addShortcutTips(tips);
		return Map.copyOf(tips);
	}

	private static void addFeatureTips(Map<String, Tip> tips) {
		tips.put(PhotonEditorTextUtil.key("feature.precise"), new Tip("Edit selected keyframe time and value precisely; locked or overlapping keys are rejected.", "精确修改选中关键帧的时间和值；锁定或时间冲突的关键帧不会修改。"));
		tips.put(PhotonEditorTextUtil.key("feature.time_scale"), new Tip("Scale selected keyframe times around the earliest selected time.", "以最早选中时间为基准缩放选中关键帧的时间。"));
		tips.put(PhotonEditorTextUtil.key("feature.batch_move"), new Tip("Move selected keys across tracks with time and value offsets.", "跨轨道移动选中关键帧，可指定时间增量和数值增量。"));
		tips.put(PhotonEditorTextUtil.key("feature.fit"), new Tip("Fit selected or all curves in the timeline view.", "按选中内容或全部曲线适配时间轴视图。"));
		tips.put(PhotonEditorTextUtil.key("feature.tangents"), new Tip("Enable curve presets and tangent editing.", "显示曲线预设、切线对齐、打断和重置入口。"));
		tips.put(PhotonEditorTextUtil.key("feature.range_preview"), new Tip("Preview a local timeline range, optionally in a loop.", "按指定起止时间预览，可在该区间内循环。"));
		tips.put(PhotonEditorTextUtil.key("feature.parameters"), new Tip("Temporarily override preview size, speed, color and beam dimensions; closing restores timeline control.", "临时调整预览大小、速度、颜色和光柱尺寸；关闭面板恢复时间轴控制。"));
		tips.put(PhotonEditorTextUtil.key("feature.batch_properties"), new Tip("Apply only checked properties to selected emitters.", "批量修改选中发射器，只应用已勾选的字段。"));
		tips.put(PhotonEditorTextUtil.key("feature.presets"), new Tip("Copy modules or save and apply module presets in editor settings.", "复制模块，或在编辑器设置中保存、应用模块预设。"));
		tips.put(PhotonEditorTextUtil.key("feature.solo"), new Tip("Preview selected branches and their parents in isolation.", "独显选中分支及其父级；再次操作恢复预览。"));
		tips.put(PhotonEditorTextUtil.key("feature.seed"), new Tip("Expose the fixed random seed editor for reproducible local previews.", "显示固定随机种子入口，便于重复比较本地预览。"));
		tips.put(PhotonEditorTextUtil.key("feature.step"), new Tip("Step the preview forward or backward by ticks.", "按 tick 前进或后退预览。"));
		tips.put(PhotonEditorTextUtil.key("feature.diagnostics"), new Tip("Show read-only render state and resource information.", "只读显示渲染状态和材质资源信息。"));
		tips.put(PhotonEditorTextUtil.key("feature.shortcuts"), new Tip("Enable custom editor shortcuts; text fields retain their own keyboard input.", "启用扩展快捷键；文本输入框保留原键盘输入。"));
		tips.put(PhotonEditorTextUtil.key("feature.curve_pan"), new Tip("Pan the curve view with the middle mouse button without changing keyframes.", "中键拖动平移曲线视图，不改变关键帧数据。"));
		tips.put(PhotonEditorTextUtil.key("feature.axis_lock"), new Tip("Hold Shift while dragging curve data to lock the dominant movement axis.", "Shift 拖动曲线数据时按主要方向锁定水平或垂直移动。"));
		tips.put(PhotonEditorTextUtil.key("feature.rotation_cycle"), new Tip("Show rotation cycle controls; saved runtime settings remain active when hidden.", "显示旋转循环控件；关闭仅隐藏控件，已保存的循环继续生效。"));
		tips.put(PhotonEditorTextUtil.key("feature.color_cycle"), new Tip("Show color cycle controls; saved runtime settings remain active when hidden.", "显示颜色循环控件；关闭仅隐藏控件，已保存的循环继续生效。"));
		tips.put(PhotonEditorTextUtil.key("feature.size_cycle"), new Tip("Show size cycle controls; saved runtime settings remain active when hidden.", "显示大小循环控件；关闭仅隐藏控件，已保存的循环继续生效。"));
		tips.put(PhotonEditorTextUtil.key("feature.velocity_cycle"), new Tip("Show velocity cycle controls; saved runtime settings remain active when hidden.", "显示速度循环控件；关闭仅隐藏控件，已保存的循环继续生效。"));
		tips.put(PhotonEditorTextUtil.key("feature.force_cycle"), new Tip("Show force cycle controls; saved runtime settings remain active when hidden.", "显示受力循环控件；关闭仅隐藏控件，已保存的循环继续生效。"));
		tips.put(PhotonEditorTextUtil.key("feature.uv_cycle"), new Tip("Show UV cycle controls; saved runtime settings remain active when hidden.", "显示 UV 循环控件；关闭仅隐藏控件，已保存的循环继续生效。"));
		tips.put(PhotonEditorTextUtil.key("feature.time_offset"), new Tip("Show cycle time offset controls; offsets change sampling time rather than values.", "显示循环时间偏移控件；偏移改变采样时间，不给属性值增加数值。"));
		tips.put(PhotonEditorTextUtil.key("feature.depth_sort"), new Tip("Show view-depth sorting controls; hiding does not change saved sorting.", "显示视线深度排序控件；隐藏不改变已保存的排序设置。"));
		tips.put(PhotonEditorTextUtil.key("feature.control"), new Tip("Show start and end lifecycle options on activator tracks; control tracks retain native behavior.", "显示激活轨道开始和结束行为；控制轨道保留原逻辑。"));
		tips.put(PhotonEditorTextUtil.key("feature.system_clipboard"), new Tip("Use the system clipboard for supported editor data and text; arbitrary JSON is not converted to effects.", "对支持的编辑器数据和文本使用系统剪切板；普通 JSON 不自动转成特效。"));
		tips.put(PhotonEditorTextUtil.key("feature.rename_dialog"), new Tip("Use native rename dialogs while retaining uniqueness, validation and history.", "使用原生重命名小窗口，保留名称校验、唯一性和操作历史。"));
		tips.put(PhotonEditorTextUtil.key("feature." + PhotonEmitterSpawnerTextUtil.FEATURE), new Tip("Show emitter spawner controls; hiding them does not stop saved effects.", "显示发射器发射器控件；隐藏不会停止已保存的特效。"));
	}

	private static void addSpawnerTips(Map<String, Tip> tips) {
		tips.put(PhotonEmitterSpawnerTextUtil.ENABLED, new Tip("Stop new instances when disabled; existing instances continue updating.", "关闭后停止新增实例，已有实例继续更新。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SOURCE, new Tip("Use a project emitter subtree or a saved FX with its complete timeline.", "选择项目发射器子树，或带完整时间轴的已保存 FX。"));
		tips.put(PhotonEmitterSpawnerTextUtil.TEMPLATE, new Tip("Drag an emitter from the hierarchy here. The template itself keeps its original playback.", "把层级中的发射器拖到此处；模板自身仍按原逻辑播放。"));
		tips.put(PhotonEmitterSpawnerTextUtil.CLEAR_TEMPLATE, new Tip("Clear this reference without deleting the template object or existing spawned instances.", "清除此引用，不删除模板对象或已生成的实例。"));
		tips.put(PhotonEmitterSpawnerTextUtil.FX, new Tip("Choose an available saved FX resource ID. Missing resources reject the current spawn.", "选择可用的已保存 FX 资源 ID；资源缺失时拒绝本次生成。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INCLUDE_CHILDREN, new Tip("Copy the project template children along with the selected emitter.", "复制项目模板时同时包含所选发射器的子对象。"));
		tips.put(PhotonEmitterSpawnerTextUtil.DURATION, new Tip("Length of one emission cycle in ticks; at least one tick. Does not set child particle lifetime.", "一个发射周期的长度，单位 tick，至少 1；不是子粒子的寿命。"));
		tips.put(PhotonEmitterSpawnerTextUtil.LOOPING, new Tip("Repeat emission cycles. One-shot mode still triggers only once per start.", "重复发射周期；一次性发射模式仍然每次启动只触发一次。"));
		tips.put(PhotonEmitterSpawnerTextUtil.START_DELAY, new Tip("Delay the first eligible emission in ticks; not repeated at each cycle.", "首次发射前等待的 tick 数，不在每周期重复等待。"));
		tips.put(PhotonEmitterSpawnerTextUtil.MODE, new Tip("Once per cycle repeats at each cycle; one shot triggers once per start. Interval uses count; rate accumulates instances per tick.", "每周期一次随周期重复；一次性发射每次启动只触发一次。按间隔使用每次实例数；按速率累计每 tick 数量。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INTERVAL, new Tip("Ticks between triggers in interval mode; at least one tick.", "按间隔模式两次触发之间的 tick 数，至少 1。"));
		tips.put(PhotonEmitterSpawnerTextUtil.COUNT, new Tip("Requested instances per trigger in once-per-cycle, one-shot and interval modes; limits and probability still apply.", "每周期一次、一次性和按间隔模式每次尝试生成的实例数；仍受概率和数量上限限制。"));
		tips.put(PhotonEmitterSpawnerTextUtil.RATE, new Tip("Instances per tick in rate mode; fractional amounts accumulate. One instance per tick is twenty per second.", "按速率模式每 tick 的实例数，小数会累计；每 tick 1 个相当于每秒 20 个。"));
		tips.put(PhotonEmitterSpawnerTextUtil.PROBABILITY, new Tip("Independent probability for each attempt, from zero to one. Failed one-shot attempts are not retried.", "每个尝试独立判断概率，范围 0 到 1；一次性发射失败不会自动重试。"));
		tips.put(PhotonEmitterSpawnerTextUtil.MAX_INSTANCES, new Tip("Maximum simultaneous live instances. Zero prevents spawning; excess requests are discarded.", "同时存活的实例上限，0 表示不生成；超出的请求丢弃。"));
		tips.put(PhotonEmitterSpawnerTextUtil.MAX_TOTAL, new Tip("Successful instance limit since restart; zero is unlimited. Restart resets the counter.", "自重启以来成功生成实例的上限，0 表示不限；重启重置计数。"));
		tips.put(PhotonEmitterSpawnerTextUtil.MAX_PER_TICK, new Tip("Maximum spawn attempts per game tick, shared by simulation substeps and manual calls; excess is not queued.", "每游戏 tick 的尝试次数上限，模拟子步和手动调用共用；超出部分不积压补发。"));
		tips.put(PhotonEmitterSpawnerTextUtil.MAX_DEPTH, new Tip("Maximum nested source depth; recursive source chains are rejected.", "允许嵌套发射的最大来源深度；循环来源会被拒绝。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INSTANCE_LIFETIME, new Tip("Child instance playback ticks before soft stop; zero retains the source lifetime. Infinite particles still require cleanup.", "生成实例播放多少 tick 后柔和停止；0 沿用来源寿命。无限寿命粒子仍需清理。"));
		tips.put(PhotonEmitterSpawnerTextUtil.END_BEHAVIOR, new Tip("At the end of non-looping emission, keep instances, stop their emission, or clear them immediately.", "非循环发射结束时，可保留已有实例、停止其发射或立即清理。"));
		tips.put(PhotonEmitterSpawnerTextUtil.CLEAR_ON_RESTART, new Tip("Clear existing instances on restart; explicit API or activator-track choices take precedence.", "重启时清理已有实例；API 或激活轨道明确指定的清理选择优先。"));
		tips.put(PhotonEmitterSpawnerTextUtil.FOLLOW, new Tip("Keep spawned anchors following the spawner transform; independent motion is added on top.", "生成实例的锚点继续跟随发射器变换，独立运动位移叠加其上。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INHERIT_ROTATION, new Tip("Apply spawner rotation to spawned anchors and launch direction.", "将发射器旋转应用到生成实例的锚点和移动方向。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INHERIT_SCALE, new Tip("Apply spawner scale to spawned instance anchors.", "将发射器缩放应用到生成实例锚点。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INHERIT_COLOR, new Tip("Multiply spawned effects by the spawner RGBA color, including alpha.", "使用发射器 RGBA 颜色乘色生成效果，包含透明度。"));
		tips.put(PhotonEmitterSpawnerTextUtil.INHERIT_VELOCITY, new Tip("Add spawner world movement velocity to independent instance motion.", "在实例独立运动中加入发射器的世界位移速度。"));
		tips.put(PhotonEmitterSpawnerTextUtil.LAUNCH_SPEED, new Tip("Independent instance speed in blocks per tick, directed away from shape center; center uses local positive Z.", "实例独立移动速度，单位格/tick；沿形状中心向外，中心点使用本地 +Z。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SHAPE, new Tip("Configure spawn position distribution and local offset.", "配置发射位置分布和本地偏移。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SHAPE_TYPE, new Tip("Point, box volume, sphere volume, or circle area in the local XZ plane.", "点、盒体积分布、球体积分布或本地 XZ 平面的圆面积分布。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SHAPE_SIZE, new Tip("Box half-extents or sphere/circle radii in blocks; components permit ellipsoids and ellipses.", "盒的半边长或球/圆半径，单位格；各分量可形成椭球或椭圆。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SHAPE_OFFSET, new Tip("Offset the spawn shape center in spawner local space, in blocks.", "在发射器本地坐标中偏移发射形状中心，单位格。"));
	}

	private static void addParameterTips(Map<String, Tip> tips) {
		tips.put(PhotonEmitterSpawnerTextUtil.PARAMETERS, new Tip("Override parameters in newly spawned copies; source templates and existing copies are preserved.", "覆盖新生成副本的参数，保留来源模板和已有副本。"));
		tips.put(PhotonEmitterSpawnerTextUtil.TARGET, new Tip("Exact emitter name to receive overrides; empty applies to all compatible emitters in the copy.", "按发射器名称精确匹配参数目标；留空应用到副本内所有兼容发射器。"));
		tips.put(PhotonEmitterSpawnerTextUtil.OVERRIDE_LIFETIME, new Tip("Replace start lifetime for new particles in spawned copies.", "替换生成副本中新粒子的起始寿命。"));
		tips.put(PhotonEmitterSpawnerTextUtil.LIFETIME, new Tip("New particle start lifetime in ticks; zero retains the native particle-type meaning.", "新粒子的起始寿命，单位 tick；0 的意义沿用原粒子类型。"));
		tips.put(PhotonEmitterSpawnerTextUtil.OVERRIDE_SIZE, new Tip("Replace new particle start size in spawned copies.", "替换生成副本中新粒子的初始大小。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SIZE, new Tip("New particle initial XYZ size; effective only when its override is enabled.", "新粒子的初始 XYZ 大小，仅开启对应覆盖时生效。"));
		tips.put(PhotonEmitterSpawnerTextUtil.OVERRIDE_COLOR, new Tip("Replace particle start color or beam color in spawned copies.", "替换生成副本中粒子初始颜色或光束颜色。"));
		tips.put(PhotonEmitterSpawnerTextUtil.COLOR, new Tip("Initial RGBA color, including alpha; effective only when its override is enabled.", "初始 RGBA 颜色，包含透明度，仅开启对应覆盖时生效。"));
		tips.put(PhotonEmitterSpawnerTextUtil.OVERRIDE_SPEED, new Tip("Replace new particle start speed in spawned copies.", "替换生成副本中新粒子的初始速度。"));
		tips.put(PhotonEmitterSpawnerTextUtil.SPEED, new Tip("New particle initial speed; effective only when its override is enabled.", "新粒子的初始速度，仅开启对应覆盖时生效。"));
		tips.put(PhotonEmitterSpawnerTextUtil.OVERRIDE_BEAM_LENGTH, new Tip("Override beam length in spawned copies.", "覆盖生成副本中的光柱长度。"));
		tips.put(PhotonEmitterSpawnerTextUtil.BEAM_LENGTH, new Tip("Beam length in blocks; applies only to compatible beams with the override enabled.", "光柱长度，单位格；仅对开启覆盖的兼容光束生效。"));
		tips.put(PhotonEmitterSpawnerTextUtil.OVERRIDE_BEAM_WIDTH, new Tip("Override beam width in spawned copies.", "覆盖生成副本中的光柱宽度。"));
		tips.put(PhotonEmitterSpawnerTextUtil.BEAM_WIDTH, new Tip("Beam width in blocks; applies only to compatible beams with the override enabled.", "光柱宽度，单位格；仅对开启覆盖的兼容光束生效。"));
		tips.put(PhotonEmitterSpawnerTextUtil.PLAYBACK_SPEED, new Tip("Multiply child timeline and simulation speed; zero freezes, one is normal, maximum sixteen.", "同时调整实例时间轴和模拟速度；0 冻结，1 正常，最大 16。"));
	}

	private static void addEditorTips(Map<String, Tip> tips) {
		tips.put(PhotonEditorTextUtil.key("time"), new Tip("Selected keyframe time in ticks; invalid or overlapping times are rejected.", "选中关键帧的时间，单位 tick；无效或冲突时间会被拒绝。"));
		tips.put(PhotonEditorTextUtil.key("value"), new Tip("Selected keyframe value; changing it does not shift sampling time.", "选中关键帧的属性值，修改不会偏移采样时间。"));
		tips.put(PhotonEditorTextUtil.key("time_delta"), new Tip("Add this many ticks to selected keyframe times.", "给选中关键帧的时间增加该 tick 数。"));
		tips.put(PhotonEditorTextUtil.key("value_delta"), new Tip("Add this amount to selected keyframe values.", "给选中关键帧的属性值增加该数值。"));
		tips.put(PhotonEditorTextUtil.key("factor"), new Tip("Scale time around the earliest selected keyframe; tangents follow the time change.", "以最早选中关键帧为基准缩放时间，切线随时间变更。"));
		tips.put(PhotonEditorTextUtil.key("preview_start"), new Tip("Start of the local preview range in ticks.", "局部预览区间的起点，单位 tick。"));
		tips.put(PhotonEditorTextUtil.key("preview_end"), new Tip("End of the local preview range in ticks; must be after its start.", "局部预览区间的终点，单位 tick，必须晚于起点。"));
		tips.put(PhotonEditorTextUtil.key("preview_loop"), new Tip("Repeat only the specified preview range.", "仅在指定预览区间内反复播放。"));
		tips.put(PhotonEditorTextUtil.key("control.start"), new Tip("Apply the start behavior when an activator clip is entered; disable to leave entry unchanged.", "进入激活片段时执行开始行为；关闭后不处理开始边界。"));
		tips.put(PhotonEditorTextUtil.key("control.end"), new Tip("Apply the end behavior when an activator clip is exited; disable to leave exit unchanged.", "离开激活片段时执行结束行为；关闭后不处理结束边界。"));
		tips.put(PhotonEditorTextUtil.key("control.start_behavior"), new Tip("Restart resets the subtree; continue preserves its current running state.", "重启重置子树运行状态；继续保留当前运行状态。"));
		tips.put(PhotonEditorTextUtil.key("control.end_behavior"), new Tip("Original hides and stops updates; stop emission keeps particles updating; clear removes existing particles.", "原行为隐藏并停止更新；仅停止生成保留已有粒子更新；清理移除已有粒子。"));
		tips.put(PhotonEditorTextUtil.key("control.clear_on_restart"), new Tip("Clear existing particles when restarting; disabled keeps them while resetting emission time.", "重新启动时清理已有粒子；关闭后保留已有粒子，但仍重置发射计时。"));
		tips.put(PhotonEditorTextUtil.key("batch_materials"), new Tip("Copy the displayed source material list to selected emitters when checked.", "勾选后将显示的来源材质列表复制到选中发射器。"));
		tips.put(PhotonEditorTextUtil.key("batch_cycles"), new Tip("Copy displayed source cycle settings to selected emitters when checked.", "勾选后将显示的来源循环设置复制到选中发射器。"));
		tips.put(PhotonEditorTextUtil.key("batch_source"), new Tip("Source emitter for material and cycle copies.", "复制材质和循环设置时使用的来源发射器。"));
		tips.put(PhotonEditorTextUtil.key("preset_name"), new Tip("Name for the saved module preset in editor settings.", "保存在编辑器设置中的模块预设名称。"));
		tips.put(PhotonEditorTextUtil.key("module"), new Tip("Choose the emitter module to copy, save or apply.", "选择要复制、保存或应用的发射器模块。"));
		tips.put(PhotonEditorTextUtil.key("depth_test"), new Tip("Test against scene depth so geometry behind nearer surfaces can be hidden.", "与场景深度比较，使被较近表面遮挡的几何不显示。"));
		tips.put(PhotonEditorTextUtil.key("depth_write"), new Tip("Write depth; transparent surfaces can then hide particles behind them.", "写入深度；透明表面也可能因此遮住其后的粒子。"));
	}

	private static void addCycleTips(Map<String, Tip> tips) {
		var enabled = new Tip("Sample the module by particle age with an independent cycle; does not extend particle lifetime.",
				"按粒子已存活时间独立循环采样，不延长粒子寿命。");
		tips.put(TranslationKeys.PHOTON_LIFETIME_CYCLE_ENABLED_KEY, enabled);
		tips.put(TranslationKeys.PHOTON_ROTATION_CYCLE_ENABLED_KEY, new Tip(
				"Loop rotation by particle age and accumulate full-cycle angle changes to keep spinning.",
				"按粒子已存活时间循环旋转，累积整周期角度差以保持连续自旋。"));
		var ticks = new Tip("Ticks for one complete curve cycle, at least one; independent of particle lifetime.",
				"完整采样一次曲线的 tick 数，至少 1，与粒子寿命独立。");
		tips.put(TranslationKeys.PHOTON_LIFETIME_CYCLE_TICKS_KEY, ticks);
		tips.put(TranslationKeys.PHOTON_ROTATION_CYCLE_TICKS_KEY, ticks);
		var offset = new Tip("Sampling time offset in ticks, including negatives. Positive advances the phase; values are not shifted.",
				"采样时间偏移，单位 tick，可为负；正数提前相位，不是属性值增量。");
		for (var key : new String[]{
				TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_AXES_KEY, TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY,
				TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY, TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY,
				TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY, TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_UV_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_X_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_Y_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_Z_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_ROLL_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_PITCH_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_YAW_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_ORBITAL_X_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_ORBITAL_Y_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_ORBITAL_Z_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_CENTER_X_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_CENTER_Y_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_CENTER_Z_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_RADIAL_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_RED_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_GREEN_KEY,
				TranslationKeys.PHOTON_CYCLE_OFFSET_BLUE_KEY, TranslationKeys.PHOTON_CYCLE_OFFSET_ALPHA_KEY}) {
			tips.put(key, offset);
		}
		tips.put(TranslationKeys.PHOTON_VIEW_DEPTH_SORT_KEY, new Tip(
				"Sort enabled emitters with the same Order by view depth. Enable on all overlapping emitters; intersecting geometry remains unresolved.",
				"相同 Order 下按视线深度排序；需在互相叠加的发射器上同时开启，无法解决面片相交。"));
	}

	private static void addShortcutTips(Map<String, Tip> tips) {
		var tip = new Tip("Use CTRL/SHIFT/ALT + key; empty disables this action. Conflicting and reserved bindings are rejected.",
				"使用 CTRL/SHIFT/ALT + 按键；留空禁用此动作。冲突及保留绑定会被拒绝。");
		for (var action : new String[]{"fit_selected", "fit_all", "step_back", "step_forward",
				"step_back_large", "step_forward_large", "duplicate", "move", "scale", "range_in",
				"range_out", "range_loop", "solo", "cancel"}) {
			tips.put(PhotonEditorTextUtil.key("action." + action), tip);
		}
	}

	private record Tip(String english, String chinese) {
	}
}
