package org.unitego.lobecorp.util.photon.editor;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

import java.util.List;

public class PhotonEditorTextUtil {
	/// Photon 编辑器扩展的翻译键命名空间。
	public static final String PREFIX = "photon.lobecorp.editor.";
	/// 独立测试菜单与设置项共用的功能标识。
	public static final String TEST_FEATURE = "tests";
	/// 保留悬浮路径共同祖先状态、减少重复样式更新的设置标识。
	public static final String HOVER_STYLE_DELTA_FEATURE = "hover_style_delta";
	/// 高速时间轴经过短片段时补处理开始、结束事件的设置标识。
	public static final String TIMELINE_EVENT_TRAVERSAL_FEATURE = "timeline_event_traversal";
	/// 编辑预览在时间轴零时刻初始化首次发射的设置标识。
	public static final String ZERO_TIME_EMISSION_FEATURE = "zero_time_emission";
	/// 不透明遮挡配置的编辑器可见性标识；不管理游戏中保存的开关。
	public static final String OCCLUSION_FEATURE = "opaque_occlusion";
	/// 缩放曲线按同时间关键帧联动 XYZ 点和手柄的设置标识。
	public static final String SCALE_LINK_FEATURE = "scale_curve_link";
	/// 创建曲线时显示缓动选择入口的设置标识。
	public static final String CURVE_EASING_FEATURE = "curve_easing";
	/// 对选中关键帧批量应用缓动的设置标识。
	public static final String BATCH_EASING_FEATURE = "batch_curve_easing";
	/// 创建与批量编辑使用的原生贝塞尔缓动预设。
	public static final List<String> CURVE_EASING_PRESETS = List.of("linear", "ease_in", "ease_out", "ease_both", "flat");
	/// 旧设置缺省时沿用线性曲线。
	public static final String DEFAULT_CURVE_EASING_PRESET = "linear";
	/// Photon 窗口标签使用文件名与后缀的设置标识。
	public static final String COMPACT_TITLE_FEATURE = "compact_window_titles";
	/// 同类型同名文件使用完整路径区分的设置标识。
	public static final String TITLE_CONFLICT_FEATURE = "title_path_conflicts";
	/// Photon 窗口标签悬浮显示完整路径的设置标识。
	public static final String TITLE_PATH_TOOLTIP_FEATURE = "title_path_tooltip";
	/// 导出成功提示与保存通知纵向堆叠的设置标识。
	public static final String NOTIFICATIONS_FEATURE = "notifications";
	/// 测试失败日志格式；第二个参数保留异常堆栈。
	public static final String TEST_FAILURE_LOG = "Photon editor check failed: {}";

	/// 适配曲线视图的扩展开关标识，保持既有设置键。
	public static final String FIT_VIEW_FEATURE = "fit_view";
	/// 曲线中键平移的扩展开关标识，保持既有设置键。
	public static final String CURVE_PAN_FEATURE = "curve_pan";
	/// Shift 拖动锁轴的扩展开关标识，保持既有设置键。
	public static final String AXIS_LOCK_FEATURE = "axis_lock";
	/// 精确关键帧编辑的扩展开关标识，保持既有设置键。
	public static final String PRECISE_FEATURE = "precise";

	public static String key(String name) {
		return PREFIX + name;
	}

	public static String register() {
		registerCategoriesAndClipboard();
		registerActivationAndBatch();
		registerFeatureSwitches();
		registerActionsAndForms();
		PhotonEditorTooltipUtil.register();
		registerTests();
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + OCCLUSION_FEATURE), "Opaque occlusion controls", "不透明遮挡控件");
		registerCurveAndTitleOptions();
		LangHandler.creates(Lobecorp.NAMESPACE, key("unnamed_effect"), "Unnamed effect", "未命名特效");
		return PREFIX;
	}

	/// 登记设置分类、通知和系统剪切板说明。
	private static void registerCategoriesAndClipboard() {
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_curves", "Curve editing", "曲线编辑");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_preview", "Preview", "预览");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_particles", "Particles and rendering", "粒子与渲染");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_resources", "Resources and clipboard", "资源与剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("settings_tip"), "Select a category to configure extensions. Configure keyboard bindings in Keymap. Feature switches default to enabled.", "选择左侧分类设置扩展功能；快捷键绑定在 Keymap 中设置，功能开关默认开启。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.system_clipboard"), "System clipboard", "使用系统剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.rename_dialog"), "Rename dialogs", "重命名弹窗");
		LangHandler.creates(Lobecorp.NAMESPACE, key("clipboard"), "Clipboard", "剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("export_success"), "Export successful", "导出成功");
		LangHandler.creates(Lobecorp.NAMESPACE, key("export_success_detail"), "Export successful\n%s", "导出成功\n%s");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + NOTIFICATIONS_FEATURE), "Save and export notifications", "保存与导出通知");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + NOTIFICATIONS_FEATURE + ".tooltip"), "Show timed export success notifications and stack them with save notifications, up to three at once. New notifications close the oldest when full. Disable to restore native Photon notifications.", "显示导出成功定时通知，与保存通知垂直排列，最多同时显示 3 条；超过时关闭最早一条。关闭恢复 Photon 原通知方式。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + TIMELINE_EVENT_TRAVERSAL_FEATURE), "Timeline interval events", "时间轴经过事件补处理");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + TIMELINE_EVENT_TRAVERSAL_FEATURE + ".tooltip"), "Execute clip starts and ends crossed by fast playback in time order.", "按时间顺序执行高速播放经过的片段开始和结束。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + ZERO_TIME_EMISSION_FEATURE), "Initialize emission at time zero", "零时刻发射初始化");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + ZERO_TIME_EMISSION_FEATURE + ".tooltip"), "Show initial bursts at timeline time zero in the editor preview. Continuous emission is not emitted early; start delays still apply.", "在编辑预览的时间轴 0 显示初始爆发；不提前生成持续发射粒子，开始延迟仍按设置等待。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("copy_system"), "Copy to system clipboard", "复制到系统剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("paste_system"), "Paste from system clipboard", "从系统剪切板粘贴");
		LangHandler.creates(Lobecorp.NAMESPACE, key("clipboard_invalid"), "Paste rejected: invalid data, incompatible type, missing or ambiguous target, locked destination, or overlapping content. No content was pasted.", "无法粘贴：数据无效、类型不符、目标缺失或不唯一、目标锁定，或与现有内容冲突。未写入粘贴内容。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("clipboard_help"), "Ctrl+C/V uses the system clipboard for selected keys, gradient/curve/expression clips and color stops, timeline clips/tracks, hierarchy objects and resource cells. Context menus also offer Copy/Paste from system clipboard; native duplication and copy-to actions remain available. Module Copy/Paste uses the same clipboard. Cross-project tracks require uniquely matching object names/types; a single clip group can paste onto a selected compatible track. Pasting is one undoable operation. Text fields keep their own copy/paste behavior.", "Ctrl+C/V 对选中关键帧、渐变/曲线/表达式子片段及颜色点、时间轴片段/轨道、层级粒子和资源格使用系统剪切板。右键也可复制到或从系统剪切板粘贴，原有直接复制和复制到功能继续可用。模块复制/粘贴共用同一剪切板。跨项目轨道要求对象名称与类型唯一匹配；单组片段可粘贴到选中的兼容轨道。每次粘贴可一次撤销，输入框仍使用自身的文本复制粘贴。");
	}

	/// 登记激活轨道生命周期与批量编辑说明。
	private static void registerActivationAndBatch() {
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.control"), "Activator track lifecycle options", "激活轨道开始与结束选项");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control"), "Activation behavior", "激活行为");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.start"), "Control start", "开始控制");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.end"), "Control end", "结束控制");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.start_behavior"), "Start behavior", "开始行为");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.end_behavior"), "End behavior", "结束行为");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.restart"), "Restart (original)", "重新启动（原版）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.clear_on_restart"), "Clear existing particles on restart", "重新启动时清理已有粒子");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.continue"), "Continue current state", "继续当前运行状态");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.original"), "Stop updates and hide (original)", "停止更新并隐藏（原版）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.stop_emission"), "Stop particle emission only", "仅停止生成粒子");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.clear"), "Clear particles and hide", "清除现有粒子并隐藏");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.tip"), "Only start enabled: run the start behavior once at the clip start. Only end enabled: run the end behavior once at the clip end. Both enabled: control the clip interval. Clips on this track share these settings.", "仅开启开始：在片段起点执行一次开始行为。仅开启结束：在片段终点执行一次结束行为。两者开启：按片段区间控制。同一轨道的片段共用这些设置。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_materials"), "Copy source materials", "复制来源材质列表");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_cycles"), "Copy source cycle settings", "复制来源循环设置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_source"), "Source emitter", "来源发射器");
		LangHandler.creates(Lobecorp.NAMESPACE, key("details"), "Select keys or tracks before editing. Track selection edits all properties on those tracks. Scaling keeps the earliest selected time fixed. Step presets add native expression clips; remove these clips to return to the original curve. Aligned tangents stay linked while dragging; Break disables the link. Batch editing changes only checked fields; materials and cycles are copied from the displayed source. Module presets are stored in editor settings. Parameters are temporary and restored on close. Isolation keeps the selected branches and their parents. Resource buttons locate the emitter inspector. The cancel action cancels keyframe and tangent drags.", "编辑前选择关键帧或轨道；选择轨道会编辑轨道内全部属性。时间缩放保持最早选中时间不变。阶梯预设添加原生表达式片段；删除片段即可恢复原曲线。对齐切线后拖动保持联动，打断解除联动。批量编辑只修改勾选字段，材质和循环设置从显示的来源发射器复制。模块预设保存在编辑器设置中。参数仅临时预览，关闭恢复。独显保留选中分支和父级。资源按钮定位发射器检查器。取消拖动动作可取消关键帧和切线拖动。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("step"), "Step (expression clips)", "阶梯（表达式片段）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("depth_test"), "Depth test", "深度测试");
		LangHandler.creates(Lobecorp.NAMESPACE, key("depth_write"), "Depth write", "深度写入");
	}

	/// 登记扩展功能开关和设置入口。
	private static void registerFeatureSwitches() {
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_shortcuts", "Shortcut settings", "快捷键设置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + PRECISE_FEATURE), "Precise keyframe editing", "精确编辑关键帧");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.time_scale"), "Scale selected time", "选中内容时间缩放");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.batch_move"), "Move across tracks", "跨轨道批量移动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.fit"), "Fit curve view", "适配曲线视图");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.tangents"), "Curve presets and tangents", "曲线预设与切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.range_preview"), "Loop preview range", "局部区间循环预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.parameters"), "Temporary preview parameters", "临时预览参数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.batch_properties"), "Batch edit emitter properties", "批量修改发射器属性");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.presets"), "Module presets and selective copy", "模块预设与选择性复制");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.solo"), "Isolate preview", "独显预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.seed"), "Fixed random seed entry", "固定随机种子入口");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.step"), "Step playback", "逐 tick 播放");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.diagnostics"), "Render and resource diagnostics", "渲染与资源诊断");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + HOVER_STYLE_DELTA_FEATURE), "Shared hover style updates", "悬浮样式更新优化");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.shortcuts"), "Editor shortcuts", "编辑器快捷键");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + CURVE_PAN_FEATURE), "Middle-button curve pan", "曲线中键平移");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + AXIS_LOCK_FEATURE), "Shift drag axis lock", "Shift 拖动锁轴");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.rotation_cycle"), "Rotation cycle controls", "旋转循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.color_cycle"), "Color cycle controls", "颜色循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.size_cycle"), "Size cycle controls", "大小循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.velocity_cycle"), "Velocity cycle controls", "速度循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.force_cycle"), "Force cycle controls", "受力循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.uv_cycle"), "UV cycle controls", "UV 循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.time_offset"), "Cycle time offset controls", "循环时间偏移控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.depth_sort"), "View-depth sorting control", "视图深度排序控件");
	}

	/// 登记快捷操作、表单字段和编辑帮助。
	private static void registerActionsAndForms() {
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.fit_selected"), "fit selected", "适配选中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.fit_all"), "fit all", "显示全部");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_back"), "step back", "后退一 tick");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_forward"), "step forward", "前进一 tick");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_back_large"), "step back large", "大步后退");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_forward_large"), "step forward large", "大步前进");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.duplicate"), "duplicate", "复制选中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.move"), "move", "移动关键帧");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.scale"), "scale", "缩放时间");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.range_in"), "range in", "设置预览起点");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.range_out"), "range out", "设置预览终点");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.range_loop"), "range loop", "循环预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.solo"), "solo", "独显选中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.cancel"), "cancel", "取消拖动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("documents"), "Documentation", "文档");
		LangHandler.creates(Lobecorp.NAMESPACE, key("settings"), "Settings", "设置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("features"), "Features", "功能开关");
		LangHandler.creates(Lobecorp.NAMESPACE, key("shortcuts"), "Shortcuts", "快捷键");
		LangHandler.creates(Lobecorp.NAMESPACE, key("tools"), "Tools", "工具");
		LangHandler.creates(Lobecorp.NAMESPACE, key("close"), "Close", "关闭");
		LangHandler.creates(Lobecorp.NAMESPACE, key("apply"), "Apply", "应用");
		LangHandler.creates(Lobecorp.NAMESPACE, key("reset"), "Reset", "重置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("time"), "Time (ticks)", "时间（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("value"), "Value", "数值");
		LangHandler.creates(Lobecorp.NAMESPACE, key("time_delta"), "Time offset (ticks)", "时间增量（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("value_delta"), "Value offset", "数值增量");
		LangHandler.creates(Lobecorp.NAMESPACE, key("factor"), "Time scale factor", "时间缩放倍率");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preview_start"), "Preview start (ticks)", "预览起点（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preview_end"), "Preview end (ticks)", "预览终点（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preview_loop"), "Loop preview", "循环预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("linear"), "Linear", "线性");
		LangHandler.creates(Lobecorp.NAMESPACE, key("ease_in"), "Ease in", "缓入");
		LangHandler.creates(Lobecorp.NAMESPACE, key("ease_out"), "Ease out", "缓出");
		LangHandler.creates(Lobecorp.NAMESPACE, key("ease_both"), "Ease in/out", "缓入缓出");
		LangHandler.creates(Lobecorp.NAMESPACE, key("flat"), "Flat tangents", "水平切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("align"), "Align tangents", "对齐切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("break"), "Break tangents", "打断切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preset_name"), "Preset name", "预设名称");
		LangHandler.creates(Lobecorp.NAMESPACE, key("save_preset"), "Save module preset", "保存模块预设");
		LangHandler.creates(Lobecorp.NAMESPACE, key("load_preset"), "Apply module preset", "应用模块预设");
		LangHandler.creates(Lobecorp.NAMESPACE, key("module"), "Module", "模块");
		LangHandler.creates(Lobecorp.NAMESPACE, key("copy_module"), "Copy module", "复制模块");
		LangHandler.creates(Lobecorp.NAMESPACE, key("paste_module"), "Paste module", "粘贴模块");
		LangHandler.creates(Lobecorp.NAMESPACE, key("no_selection"), "Select editable unlocked keyframes or emitters first.", "请先选择可编辑且未锁定的关键帧或发射器。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("invalid"), "Invalid input, overlapping keys, or unavailable resource.", "输入无效、关键帧重叠或资源不可用。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("parameter_tip"), "Preview only. Reset or close the panel to resume timeline control. Start parameters affect newly spawned particles.", "仅影响预览。重置或关闭面板恢复时间轴控制。起始参数只影响新生成的粒子。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("shortcut_tip"), "Set primary and secondary bindings in Keymap; clear a binding to disable it. Keymap displays conflicts and restores defaults.", "在 Keymap 中设置主、副快捷键，清空绑定可禁用；Keymap 提示冲突并支持恢复默认。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("help"), "Extensions are controlled by Settings > Lobecorp. Configure all keyboard bindings in Settings > Keymap; the list below shows the current bindings. Switches affect editor controls only; saved effects keep playing. Moving keys and scaling time operate on the selection; scaling keeps the earliest time fixed. Fit actions adjust the curve view, and range actions control preview playback. Alt-drag copies keys. Text fields retain their original keyboard behavior.", "扩展功能由“设置 > Lobecorp”管理，全部快捷键在“设置 > Keymap”设置；下方列出当前实际绑定。开关只控制编辑器，已保存特效照常播放。移动与时间缩放作用于选中关键帧，缩放保持最早时间不变；适配动作调整曲线视图，区间动作控制预览播放。Alt 拖动复制关键帧，输入框保留原键盘操作。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_tip"), "Only checked properties are applied to selected particle emitters; all other settings are preserved.", "只将勾选的属性应用到选中的粒子发射器，保留其余设置。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("diagnostic_tip"), "Read-only authored render state and material resources. Intersecting translucent geometry may still require a different rendering technique.", "只读显示配置的渲染状态和材质资源。相交半透明几何仍可能需要其他渲染技术。");
	}

	private static void registerCurveAndTitleOptions() {
		LangHandler.creates(Lobecorp.NAMESPACE, key("curve_easing_default"), "Default curve easing", "默认曲线缓动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("curve_easing"), "Curve easing", "曲线缓动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("add_curve_easing"), "Add curve with easing", "添加曲线并选择缓动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_curve_easing"), "Choose easing for selected keys", "为选中关键帧选择缓动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("curve_easing_tip"), "Choose a Bezier easing preset. It shapes segments as new keys are added; the initial single key has no segment. Existing expression clips retain their native behavior.", "选择贝塞尔缓动预设，之后新增关键帧时设置相邻曲线段；初始单个关键帧尚无曲线段。已有表达式片段保留原行为。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_curve_easing_tip"), "Marquee-select keys, then choose an easing preset. One undo restores all edited handles; native expression clips may override the curve.", "框选关键帧后选择缓动预设，可一次撤销全部手柄变化；原生表达式片段可能覆盖曲线求值。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + SCALE_LINK_FEATURE), "Link scale XYZ curves and handles", "缩放 XYZ 曲线与手柄联动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + CURVE_EASING_FEATURE), "Choose easing when adding curves", "添加曲线时选择缓动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + BATCH_EASING_FEATURE), "Batch edit selected easing", "批量修改选中曲线缓动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + COMPACT_TITLE_FEATURE), "Compact window titles", "窗口标题仅显示文件名");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + TITLE_CONFLICT_FEATURE), "Show full paths for duplicate titles", "同名窗口显示完整路径");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + TITLE_PATH_TOOLTIP_FEATURE), "Show full path on title hover", "窗口标题悬浮显示完整路径");
	}

	private static void registerTests() {
		LangHandler.creates(Lobecorp.NAMESPACE, key("tests"), "Tests", "测试");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature." + TEST_FEATURE), "Independent tests", "独立测试入口");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.all"), "Run all checks", "全部测试");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.clock_parameters"), "Clock and parameters", "时钟与参数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.instances"), "Instances and triggers", "多实例与触发");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.runtime"), "Runtime regression", "动画运行回归");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.gui"), "Editor configuration", "编辑器配置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.timeline"), "Shared timeline and native compatibility", "共享时间轴与原生兼容");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.timeline.tooltip"), "Check isolated timeline models, native sampling and format compatibility without modifying the open effect.", "检查独立时间轴模型、原生采样和版本兼容，不修改当前特效。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.initial_emission"), "Initial particle emission", "零时刻粒子发射");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.initial_emission.tooltip"), "Check initial bursts, particle age, pending queues and first-step deduplication in an isolated preview world. Requires a loaded client world.", "在隔离预览世界检查初始爆发、粒子年龄、待加入队列及首步去重；需要已进入客户端世界。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.occlusion"), "Opaque occlusion and native pipelines", "不透明遮挡与原生管线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.occlusion.tooltip"), "Check opaque occlusion settings and render pipelines without modifying the open effect. Does not test the rendered image.", "检查不透明遮挡配置和渲染管线，不修改当前特效；不包含实际画面测试。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.passed"), "%s: passed %s assertions", "%s：通过 %s 项断言");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.failed"), "%s: failed (%s)", "%s：失败（%s）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("test.isolation"), "Checks use isolated objects and do not modify the open effect. Failures are logged with their stack traces.", "测试使用隔离对象，不修改当前特效；失败详情与堆栈写入日志。");
	}
}
