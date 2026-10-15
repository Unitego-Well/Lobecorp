package org.unitego.lobecorp.util.photon.animation;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter.Type;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.List;

public class PhotonAnimationTextUtil {
	/// 编辑器入口与辅助功能的统一开关。
	public static final String FEATURE = "named_animations";
	/// 本系统标签和悬浮说明的翻译前缀。
	public static final String PREFIX = "photon.lobecorp.animation.";
	/// 原生参数列表新增行的名称前缀；重复时添加序号。
	public static final String PARAMETER_NAME = "parameter";
	/// 向量参数使用 XYZW；原生三分量绑定沿用 XYZ。
	private static final List<String> VECTOR_COMPONENTS = List.of("component.x", "component.y", "component.z", "component.w");
	/// 颜色分量包含透明度 Alpha，供参数与绑定选择器共用。
	private static final List<String> COLOR_COMPONENTS = List.of("component.r", "component.g", "component.b", "component.a");

	public static String key(String suffix) {
		return PREFIX + suffix;
	}

	public static String componentSuffix(int channels, int axis) {
		if (axis < 0) {
			return "component.all";
		}

		if (channels == 3 && axis < channels) {
			return VECTOR_COMPONENTS.get(axis);
		}

		if (channels == 4 && axis < COLOR_COMPONENTS.size()) {
			return COLOR_COMPONENTS.get(axis);
		}

		return "value";
	}

	public static String componentSuffix(Type type, int axis) {
		if (axis < 0) {
			return "component.all";
		}

		if (type.channels() == 1 || axis >= type.channels()) {
			return "value";
		}

		return type == Type.COLOR ? COLOR_COMPONENTS.get(axis) : VECTOR_COMPONENTS.get(axis);
	}

	private static void text(String suffix, String english, String chinese, String englishTip, String chineseTip) {
		LangHandler.creates(Lobecorp.NAMESPACE, key(suffix), english, chinese);
		LangHandler.creates(Lobecorp.NAMESPACE, key(suffix + ".tooltip"), englishTip, chineseTip);
	}

	public static String register() {
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_animation_controller", "Animation controller", "动画控制器");
		LangHandler.creates(Lobecorp.NAMESPACE, PhotonEditorTextUtil.key("feature." + FEATURE), "Named animations workspace", "命名动画工作区");
		LangHandler.creates(Lobecorp.NAMESPACE, PhotonEditorTextUtil.key("feature." + FEATURE + ".tooltip"), "Enable the named animation workspace.", "开启命名动画入口与编辑辅助功能。");
		registerControls();
		registerChoreography();
		registerActions();
		registerComponents();
		text("validation", "Run loaded checks", "运行槽检查", "Runs property ownership checks in the loaded client.", "在已加载客户端检查属性覆盖、恢复及基础值漂移。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("validation_result"), "Passed: %s", "通过：%s");
		LangHandler.creates(Lobecorp.NAMESPACE, key("status_format"), "%s | t=%s | progress=%s | cycles=%s | %s", "%s | 时间=%s | 进度=%s | 循环=%s | %s");
		registerEnums();
		return PREFIX;
	}

	private static void registerControls() {
		text("title", "Named animations", "命名动画", "Define animations and parameters in this FX.", "在同一个特效中定义动画、参数和触发规则。");
		text("outline_panel", "Animation list", "动画列表", "Select an animation or click empty space to use the project-only test timeline. Drag the handle to reorder; right-click to duplicate, rename or delete.", "选择动画；点击空白取消选择并使用仅存于项目的测试时间轴；拖动左侧拖动条排序，右键复制、重命名或删除。");
		text("inspector_panel", "Animation inspector", "动画检视", "Configure animations, bindings, parameters and trigger rules.", "配置动画、混合绑定、参数与触发规则。");
		text("timeline_panel", "Animation timeline", "动画时间轴", "Edit the selected named animation's object tracks and curves.", "编辑所选命名动画的对象轨道与曲线。");
		text("horizontal_scroll", "Horizontal scroll", "水平滚动", "Drag to pan time; Shift + wheel pans horizontally without editing keys.", "拖动滚动条移动时间视图；Shift 加滚轮水平滚动，不修改关键帧。");
		text("new", "New animation", "新建动画", "Add an independently timed animation.", "新增一段独立计时的动画。");
		text("copy", "Duplicate", "复制", "Duplicate the selected animation and its name; append a number when the name already exists.", "复制所选动画及名称；重名时自动加序号。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("copy_name"), "%s_%s", "%s_%s");
		text("rename", "Rename", "重命名", "Rename without changing references.", "修改显示名称，引用保持不变。");
		text("delete", "Delete", "删除", "Remove this definition and its internal references.", "删除该定义及其内部引用。");
		text("default", "Default animation", "默认动画", "Played when the runtime starts; empty means manual.", "实例启动时播放；空值表示手动启动。");
		text("duration", "Duration (ticks)", "播放时长（tick）", "Runtime changes preserve progress and cycle count.", "运行时修改时长保留当前进度和循环次数。");
		text("auto_duration", "Follow content duration", "时长跟随内容", "Use the complete authored range as the default playback duration. Disable to retime all content to a fixed duration.", "默认播放时长使用完整制作范围；关闭后将全部内容重定时到固定播放时长。");
		text("authored_duration", "Authored range (ticks)", "制作范围（tick）", "Includes native track content and named event markers; empty content uses the default duration.", "包含原生轨道内容及命名事件标记；空内容沿用默认时长。");
		text("auto_enable_modules", "Automatically enable modules", "自动启用模块", "Temporarily enable lifetime modules used by the animation on this instance. Otherwise follow native module switches.", "在当前实例中临时开启动画使用的生命周期模块；关闭时遵循原生模块开关。");
		text("loop", "Loop", "循环", "Repeat without resetting cumulative angular motion.", "循环播放，旋转保留跨周期累积。");
		text("priority", "Priority", "优先级", "Higher values are composed later; ties use start order.", "较高值后合成；同级按启动顺序合成。");
		text("completion", "On completion", "完成行为", "Hold retains the final contribution; release removes it.", "保持保留末帧贡献；释放移除该动画贡献。");
		text("play", "Restart", "重启", "Restart the test target with its inputs and playback duration without replacing particles or other animations.", "使用测试实参与播放时长重启测试目标，保留粒子和其他动画。");
		text("continue", "Continue", "继续", "Apply test inputs and resume the target without changing its progress; start it if absent.", "应用测试实参并恢复目标当前进度；尚未运行时启动。");
		text("stop", "Stop", "停止", "Remove only the test target's contribution.", "仅移除测试目标动画的贡献。");
		text("pause", "Pause", "暂停", "Pause only the test target while holding its pose; the scene and other animations can keep running.", "仅暂停测试目标并保持姿态，场景和其他动画可继续运行。");
		text("resume", "Resume", "恢复", "Resume a paused animation.", "恢复已暂停的动画。");
		text("reset", "Resimulate preview", "重新模拟预览", "Reset the shared preview and replay to the current authoring cursor, then pause.", "重置共用预览，重新模拟到当前制作游标后暂停。");
		text("finish", "Finish FX", "结束特效", "Explicitly clear the preview effect and its particles.", "显式清理预览特效和粒子。");
		text("target", "Target object", "目标对象", "Select the target in the original FX hierarchy; recording edits base values and records keys.", "直接在原特效层级选择目标；录制同时修改基础值并记录关键帧。");
		text("record_off", "Edit base settings; enable track recording to also record keys.", "编辑基础参数；开启轨道录制时同时记录关键帧。", "Edit parameters in the inspector or use transform tools. Object names and hierarchy structure cannot be changed.", "可在检视中修改参数，或使用变换工具；对象名称和层级结构不可修改。");
		text("record_values", "Recording values", "录制参数", "Change base settings and record keys at the animation playhead.", "在动画播放头处修改基础值并记录关键帧。");
		text("property", "Native property", "原生属性", "Start properties affect births; over-lifetime properties affect survivors.", "初始参数影响新粒子；生命周期模块参数影响已存活粒子。");
		text("add_track", "Add object track", "添加对象轨道", "Create a curve track for the selected object.", "为目标对象创建曲线轨道。");
		text("blend", "Blend", "混合", "Override, add or multiply; discrete properties only override.", "覆盖、相加或相乘；离散属性仅支持覆盖。");
		text("source", "Value source", "数值来源", "Choose curves, parameters or runtime variables.", "选择曲线、参数或运行变量。");
		text("component", "Component (-1: all)", "分量（-1：全部）", "Restrict the contribution to one target component.", "限定只影响目标的一个分量。");
		text("scale", "Scale", "缩放", "Applied to the sampled contribution before blending.", "采样值合成前乘以此值。");
		text("offset", "Offset", "偏移", "Added to the sampled contribution before blending.", "采样值合成前加上此值。");
		text("parameter", "Parameter", "形参", "Read a formal parameter owned by this animation playback.", "读取当前动画播放实例独立持有的形参。");
		text("parameters", "Parameters", "形参定义", "Each animation declares its own typed defaults and ranges; runtime inputs belong to its playback handle.", "每个动画独立定义类型、默认值和范围；运行输入由具体播放句柄持有。");
		text("new_parameter", "New parameter", "新建形参", "Add a uniquely named formal parameter to this animation.", "为当前动画新增名称唯一的形参。");
		text("type", "Type", "类型", "Preserve matching components and fill new ones from type defaults; calls must match the declared type.", "保留共同分量，新增分量取类型默认值；调用实参须符合声明类型。");
		text("minimum", "Minimum", "最小值", "All components share this range. Integers are limited to +/-16,777,216; colors to 0..1.", "全部分量共用此范围；整数限制为 ±16,777,216，颜色限制为 0～1。");
		text("maximum", "Maximum", "最大值", "All components share this range. Integers are limited to +/-16,777,216; colors to 0..1.", "全部分量共用此范围；整数限制为 ±16,777,216，颜色限制为 0～1。");
		text("value", "Value / default", "数值／默认值", "Edit values within the declared type and range. Colors support a picker and RGBA components.", "按类型及取值范围编辑；颜色支持拾色器和 RGBA 分量。");
		text("preview_parameters", "Preview parameters", "预览参数", "Temporary overrides; never saved into the FX.", "仅作用于预览，不写入资源。");
		text("bindings", "Bindings", "混合与绑定", "Select a target property, then its value source.", "选择目标原生属性，然后设置数值来源。");
		text("rules", "Trigger rules", "触发规则", "Each matching event plays one animation in this FX with validated inputs, in rule order.", "匹配事件后按规则顺序播放本特效动画，并传入校验后的实参。");
		text("new_rule", "New rule", "新建规则", "Choose an event, a local animation and its inputs.", "选择事件、本特效动画及其实参。");
		text("event", "Event", "触发来源", "Start, completion, marker, native signal or code trigger.", "实例启动、动画完成、时间标记、原生信号或代码指令。");
		text("channel", "Channel", "通道", "Empty matches every native signal channel.", "留空匹配所有原生信号通道。");
		text("name", "Name", "名称", "Empty rule filters match all; definitions require unique names.", "规则过滤条件留空表示全部；定义名称必须唯一。");
		text("new_action", "Add action", "添加动作", "Actions execute from top to bottom.", "动作从上到下依次执行。");
		text("action", "Action", "动作", "Control animations, duration, parameters or named triggers on this instance or existing spawned instances.", "控制所属实例或已发射子实例的动画、时长、参数与命名触发；不创建新实例。");
		text("force", "Clear particles", "立即清理粒子", "Otherwise stop generation and allow remnants to drain.", "关闭时仅停止生成，保留残留粒子自然结束。");
		text("trigger", "Manual trigger", "手动触发", "Queue a named code trigger on the preview instance.", "向当前预览实例发送指定名称的触发事件。");
		text("markers", "Time markers", "时间标记", "Fire when live playback crosses the authored tick.", "实时播放经过标记时间时触发。");
		text("new_marker", "Add marker", "添加标记", "Scrubbing does not dispatch events.", "拖动编辑器时间不会触发事件。");
		text("time", "Animation time (ticks)", "动画时间（tick）", "Authoring coordinates stay unchanged when runtime duration changes.", "运行时调整时长不改变曲线的编辑时间坐标。");
		text("example", "Magic circle example", "魔法阵动画示例", "Add expand, spin, breathe and fade to the selected particle emitter.", "为所选粒子发射器添加展开、旋转、呼吸和淡出。");
		text("invalid", "Invalid definition or selection", "定义或选择无效", "Check unique names, references, parameter types and duration.", "检查名称唯一性、引用、参数类型及播放时长。");
		text("stop_emission", "Stop emission", "停止生成", "Keep existing particles alive.", "保留已生成粒子。");
		text("status", "Playback status", "播放状态", "Shows state, progress, cycles and runtime binding problems.", "显示状态、进度、循环次数及运行时绑定问题。");
		text("runtime_controls", "Animation runtime test", "运行动画测试", "Test targets independently of the animation being edited and combine several running animations. The timeline below controls authoring preview.", "独立选择测试目标并组合运行多个动画，不切换正在编辑的动画；底部时间轴控制制作预览。");
		text("test_target", "Test animation", "测试动画", "Select a named animation to play without changing the authoring selection or stopping other animations.", "选择要播放的命名动画，不改变制作选择，也不停止其他动画。");
		text("test_inputs", "Test inputs", "测试实参", "Enter inputs for this test; restart or continue applies them without changing saved defaults.", "输入本次测试使用的实参；重启或继续时应用，不修改保存的默认值。");
		text("test_timeline", "Project-only test timeline", "当前使用项目专用测试时间轴", "This authoring timeline is saved with the project and omitted from FX and FXPack exports.", "此制作时间轴随项目保存，导出 FX 和 FXPack 时不包含它。");
		text("scene_state", "Scene state", "场景状态", "Shows whether the shared particle simulation is running, paused or stopped.", "显示共用粒子场景正在运行、暂停或停止。");
		text("animation_state", "Test animation state", "测试动画状态", "Shows the test target independently of scene playback, including progress, cycles and binding problems.", "独立显示测试目标实例的状态、进度、循环次数及绑定问题。");
		text("runtime_duration", "Temporary duration (ticks)", "临时播放时长（tick）", "Change only the running instance while preserving progress and cycles; this override is not saved.", "仅修改运行实例，保留进度及循环次数；此覆盖不保存到资源。");
	}

	private static void registerChoreography() {
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_child_animation", "Child animation", "子动画");
		LangHandler.creates(Lobecorp.NAMESPACE, "photon.timeline_track.lobecorp_child_animation", "Child animation", "子动画");
		text("input_override", "Override default", "覆盖默认值", "Use the target animation default when disabled; overrides are saved only after editing.", "关闭时使用目标动画默认值；编辑后才保存实参覆盖。");
		text("input_follow", "Follow source", "持续跟随", "Continuously read the parent animation parameter; otherwise capture it at invocation.", "开启后持续读取父动画形参；关闭时在调用时取快照。");
		text("scale_parameter", "Scale parameter", "倍率形参", "Use a scalar formal parameter as the curve multiplier; empty uses the fixed multiplier.", "选择标量形参作为曲线倍率；空值使用固定倍率。");
		text("offset_parameter", "Offset parameter", "偏移形参", "Use a scalar formal parameter as the curve offset; empty uses the fixed offset.", "选择标量形参作为曲线偏移；空值使用固定偏移。");
		text("duration_parameter", "Duration parameter", "时长形参", "Use a scalar formal parameter as independent playback duration; controlled clips own their clock.", "选择标量形参控制独立播放时长；受控片段使用片段长度决定时钟。");
		text("rule_target", "Animation to play", "播放动画", "Play a named animation in this FX; child animations are called by timeline clips.", "此规则仅播放本特效的目标命名动画；子动画通过时间轴片段调用。");
		text("child_animation", "Child animation", "子动画", "Select an animation from the bound emitter spawner's child FX.", "选择绑定发射器发射器的子特效动画。");
		text("child_mode", "Clip mode", "片段模式", "Controlled playback follows clip progress; independent playback triggers each child once during the window.", "受控播放跟随片段进度；独立播放在窗口内对每个子实例触发一次。");
		text("child_completion", "Clip completion", "片段结束行为", "Controlled playback inherits, holds or releases its contribution; this never clears the whole child FX.", "受控动画在窗口结束时继承、保持或释放自身贡献；不清理整个子特效。");
		text("migration_blocked", "Animation migration blocked", "动画迁移受阻", "The original definition is preserved. Resolve the reported incompatibilities before editing, saving or exporting.", "原配置已保留；处理报告中的不兼容项后再编辑、保存或导出。");
		text("migration_discarded", "Content discarded during migration", "迁移时丢弃的内容", "Unsupported legacy entries were skipped. The remaining content can be edited, saved and exported unless a blocking problem is also reported.", "不支持的旧条目已跳过；没有阻断问题时，其余内容可继续编辑、保存和导出。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.CONTROLLED"), "Controlled by clip", "受控播放");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.INDEPENDENT"), "Independent on entry", "独立触发");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.INHERIT"), "Use animation completion", "沿用动画定义");
	}

	private static void registerActions() {
		LangHandler.creates(Lobecorp.NAMESPACE, LcPhotonAnimationTypes.CALL_TRACK_NAME, "Animation call", "动画调用");
		LangHandler.creates(Lobecorp.NAMESPACE, LcPhotonAnimationTypes.CALL_TRACK_TRANSLATION_KEY, "Animation call", "动画调用");
		text("call_target", "This FX", "本特效", "Call named animations in this FX from the project-only test timeline. Test tracks are saved with the project and excluded from FX exports.", "在项目专用测试时间轴中调用本特效的命名动画；测试轨道随项目保存，不导出到特效资源。");
		text("call_animation", "Animation to call", "调用动画", "Select a named animation from this FX. Calls store its stable ID; renaming the animation keeps the reference.", "选择本特效的命名动画；调用保存稳定 ID，动画重命名后引用保持不变。");
		text("call_mode", "Playback mode", "播放模式", "Independent calls start the target once on entry. Controlled calls map its complete content to the clip and release only their own contribution when stopped.", "独立触发在进入片段时启动目标一次；受控播放将目标完整内容映射到片段，停止时仅释放该片段持有的贡献。");
		text("action_scope", "Action scope", "动作范围", "Choose this instance or child instances already emitted by a spawner. The rule never emits new instances.", "选择所属实例或发射器已生成的子实例；规则动作不会生成新实例。");
		text("action_spawner", "Emitter spawner", "发射器发射器", "Select a spawner in this FX by stable object ID; its name is only the display label.", "按稳定对象 ID 选择当前特效中的发射器发射器；名称仅用于显示。");
		text("action_instances", "Child instances", "子实例选择", "All targets every surviving child; latest targets only the most recently emitted surviving child.", "全部作用于当前存活子实例；最新仅作用于最近发射且仍存活的子实例。");
		text("child_library_missing", "Child library unavailable", "子动画库未解析", "Select a spawner whose saved FX contains exactly one animation controller. Missing resources or references are preserved and can be entered manually.", "选择来源特效包含唯一动画控制器的发射器；缺失资源及旧引用保持不变，可手动输入引用。");
		text("action_animation", "Target animation", "目标动画", "Select from the target instance's animation library. Selection stores its stable ID.", "从目标实例的动画库选择；选择后保存动画稳定 ID。");
		text("action_animation_reference", "Animation ID / name", "动画 ID／名称", "Enter a stable ID or exact animation name. Unresolved saved references are retained.", "输入稳定 ID 或完整动画名称；无法解析的已保存引用仍然保留。");
		text("duration_source", "Duration source", "时长来源", "Read a parent number or integer parameter once at dispatch. Its value must be finite and nonnegative; empty uses the fixed duration.", "派发时读取一次父实例的数值或整数参数；必须有限且非负；留空使用固定时长。");
		text("action_duration", "Fixed duration (ticks)", "固定时长（tick）", "Use -1 for the target animation duration. Independent calls may override it; controlled calls map all content to the parent clip length.", "-1 使用目标动画定义时长；独立调用可以覆盖，受控调用始终将完整内容映射到父片段长度。");
		text("action_parameter", "Target parameter", "目标参数", "Set a shared parameter on the selected instance. The target definition controls type, dimensions and range.", "设置所选实例的共享参数；类型、分量和范围由目标定义约束。");
		text("action_parameter_reference", "Parameter name", "参数名称", "Enter the target instance's parameter name. Missing resource definitions do not clear the saved reference.", "输入目标实例的参数名称；资源定义缺失时不会清空已保存引用。");
		text("parameter_source", "Parameter value source", "参数数值来源", "Read a matching-size parent parameter once, preferring the triggering play's local override over its shared value. Empty uses fixed components.", "读取分量数匹配的父参数快照，优先触发播放的局部覆盖，再取共享值；留空使用固定分量。");
		text("fixed_value", "Fixed value", "固定数值", "Edit value components within the target parameter's declared range.", "编辑数值分量，取值遵循目标形参的范围。");
		text("action_trigger", "Trigger name", "触发器名称", "Queue a named trigger on the selected instance after writing inputs to its shared parameters; forward the originating event data.", "先将输入写入所选实例的共享参数，再派发命名触发；转发来源事件数据。");
		text("action_inputs", "Parameter inputs", "动画实参", "Inputs override the target animation's defaults on one playback handle; source values are captured unless follow is enabled.", "实参只覆盖目标动画本次播放的默认形参；来源默认取快照，可单独开启持续跟随。");
		text("action_input", "Input mapping", "实参映射", "Map a fixed value or compatible parent formal parameter to the target animation's formal parameter.", "将固定值或兼容的父动画形参传入目标动画的形参。");
		text("new_input", "Add input", "添加输入", "Add a target parameter mapping; choose an unused known target when available.", "新增目标参数映射；存在候选时优先选择未使用的目标。");
		text("delete_input", "Remove input", "移除输入", "Remove only this mapping from the action.", "仅移除此动作中的当前映射。");
		text("input_target", "Input target", "输入目标", "Choose a parameter in the target instance. Explicit changes preserve common fixed components and fill new ones from target defaults.", "选择目标实例参数；明确切换时保留共有固定分量，新增分量使用目标默认值。");
		text("input_target_reference", "Target parameter name", "目标参数名称", "Enter an exact parameter name when the target library is unavailable; unresolved names remain saved.", "目标库不可用时可输入完整参数名称；未解析名称仍保存。");
		text("input_source", "Parent value source", "父形参来源", "Choose a compatible formal parameter from the parent animation; empty uses fixed values.", "选择父动画类型兼容的形参；留空使用固定数值。");
		text("action_emission_target", "Emission target", "停止生成目标", "An empty target stops every emitter in the selected instance while preserving existing particles.", "空目标停止所选实例中全部发射器生成，保留已生成粒子。");
		text("child_emission_target", "Child object ID", "子对象 ID", "Choose or enter an object UUID from the child's source. Empty stops all child emitters; parent object references are not substituted.", "选择或输入子实例来源中的对象 UUID；留空停止全部子发射器；不会替换为父对象引用。");
		text("no_reference", "None", "未选择", "Choose or enter a target reference.", "选择或输入目标引用。");
		text("fixed_source", "Fixed value", "固定数值", "Use the authored fixed value instead of a parent parameter.", "使用编辑时设置的固定数值，代替父参数。");
		text("all_objects", "All emitters", "全部发射器", "Stop generation throughout the selected instance.", "停止所选实例中的全部生成。");
	}

	private static void registerComponents() {
		LangHandler.creates(Lobecorp.NAMESPACE, key("binding_title"), "%s / %s", "%s / %s");
		text("component.all", "All components", "全部分量", "Apply this binding to every component.", "将该绑定应用到全部分量。");
		text("component.invalid", "Invalid component (%s)", "无效分量（%s）", "Select a valid component to repair this saved binding.", "选择有效分量以修正此已保存的绑定。");

		for (var axis : List.of("x", "y", "z", "w")) {
			text("component." + axis, axis.toUpperCase(), axis.toUpperCase(), "Vector component.", "向量分量。");
		}

		text("component.r", "R", "R", "Red component.", "红色分量。");
		text("component.g", "G", "G", "Green component.", "绿色分量。");
		text("component.b", "B", "B", "Blue component.", "蓝色分量。");
		text("component.a", "A", "A", "Alpha component; zero is transparent and one is opaque.", "透明度分量；0 为透明，1 为不透明。");
	}

	private static void registerEnums() {
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.HOLD"), "Hold final frame", "保持最后一帧");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.RELEASE"), "Release contribution", "完成后释放");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.OVERRIDE"), "Override", "覆盖");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.ADD"), "Add", "相加");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.MULTIPLY"), "Multiply", "相乘");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.CURVE"), "Curve", "曲线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.PARAMETER"), "Parameter", "参数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.EFFECT_TIME"), "Effect time", "特效时间");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.ANIMATION_TIME"), "Animation time", "动画时间");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.PROGRESS"), "Progress", "进度");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.LOOP_COUNT"), "Loop count", "循环次数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.NUMBER"), "Float", "浮点数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.INTEGER"), "Integer", "整数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.BOOLEAN"), "Boolean", "布尔");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.VECTOR"), "Float vec3", "浮点数 vec3");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.FLOAT_VEC2"), "Float vec2", "浮点数 vec2");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.FLOAT_VEC4"), "Float vec4", "浮点数 vec4");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.INTEGER_VEC2"), "Integer vec2", "整数 vec2");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.INTEGER_VEC3"), "Integer vec3", "整数 vec3");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.INTEGER_VEC4"), "Integer vec4", "整数 vec4");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.COLOR"), "Color", "颜色");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.START"), "Instance start", "实例启动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.COMPLETE"), "Animation completion", "动画完成");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.MARKER"), "Time marker", "时间标记");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.SIGNAL"), "Native signal", "原生信号");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.TRIGGER"), "Code trigger", "代码指令");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.PLAY"), "Play / restart", "播放／重启");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.CONTINUE"), "Continue", "继续");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.STOP"), "Stop animation", "停止动画");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.PAUSE"), "Pause", "暂停");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.RESUME"), "Resume", "恢复");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.SET_PARAMETER"), "Set parameter", "设置参数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.SET_DURATION"), "Set duration", "设置时长");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.SELF"), "This instance", "所属实例");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.CHILD_INSTANCES"), "Spawned child instances", "已发射子实例");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.ALL"), "All surviving instances", "全部存活实例");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.LATEST"), "Latest surviving instance", "最新存活实例");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.STOP_EMISSION"), "Stop emission", "停止生成");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.FINISH"), "Finish FX", "结束特效");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.PLAYING"), "Playing", "播放中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.PAUSED"), "Paused", "已暂停");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.HOLDING"), "Holding", "保持末帧");
		LangHandler.creates(Lobecorp.NAMESPACE, key("enum.STOPPED"), "Stopped", "已停止");
	}
}
