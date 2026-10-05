package org.unitego.lobecorp.util.photon.editor;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

/**
 * 发射器发射器的配置标签、枚举显示和数据生成翻译。
 */
public class PhotonEmitterSpawnerTextUtil {
	/**
	 * 本扩展编辑器控件与翻译键的统一前缀。
	 */
	public static final String PREFIX = "photon.lobecorp.emitter_spawner.";
	/**
	 * 最近一次发射的稳定状态标识，对应翻译键后缀。
	 */
	public static final String STATUS_OK = "ok";
	/**
	 * 未找到有效的项目发射器模板。
	 */
	public static final String STATUS_MISSING_TEMPLATE = "missing_template";
	/**
	 * 已保存 FX 资源不可用。
	 */
	public static final String STATUS_MISSING_FX = "missing_fx";
	/**
	 * 来源循环或嵌套层数超过配置。
	 */
	public static final String STATUS_RECURSIVE = "recursive";
	/**
	 * 数值或模板数据无效。
	 */
	public static final String STATUS_INVALID = "invalid";
	/**
	 * 发射实例数量已达到配置上限。
	 */
	public static final String STATUS_CAPACITY = "capacity";
	/**
	 * Lobecorp 粒子分类中的控件开关。
	 */
	public static final String FEATURE = "emitter_spawner";
	/**
	 * 发射器配置组标题。
	 */
	public static final String CONFIG = PREFIX + "config";
	/**
	 * 启用。
	 */
	public static final String ENABLED = PREFIX + "enabled";
	/**
	 * 来源。
	 */
	public static final String SOURCE = PREFIX + "source";
	/**
	 * 发射器模板。
	 */
	public static final String TEMPLATE = PREFIX + "template";
	/**
	 * 清除项目模板引用的原生按钮标签，不删除模板对象。
	 */
	public static final String CLEAR_TEMPLATE = PREFIX + "clear_template";
	/**
	 * FX 资源。
	 */
	public static final String FX = PREFIX + "fx";
	/**
	 * 包含模板子对象。
	 */
	public static final String INCLUDE_CHILDREN = PREFIX + "include_children";
	/**
	 * 发射持续时间（tick）。
	 */
	public static final String DURATION = PREFIX + "duration";
	/**
	 * 循环发射。
	 */
	public static final String LOOPING = PREFIX + "looping";
	/**
	 * 开始延迟（tick）。
	 */
	public static final String START_DELAY = PREFIX + "start_delay";
	/**
	 * 发射模式。
	 */
	public static final String MODE = PREFIX + "mode";
	/**
	 * 发射间隔（tick）。
	 */
	public static final String INTERVAL = PREFIX + "interval";
	/**
	 * 每次发射实例数。
	 */
	public static final String COUNT = PREFIX + "count";
	/**
	 * 每 tick 发射实例数。
	 */
	public static final String RATE = PREFIX + "rate";
	/**
	 * 发射概率。
	 */
	public static final String PROBABILITY = PREFIX + "probability";
	/**
	 * 最大存活实例数。
	 */
	public static final String MAX_INSTANCES = PREFIX + "max_instances";
	/**
	 * 总发射数量（0：不限）。
	 */
	public static final String MAX_TOTAL = PREFIX + "max_total";
	/**
	 * 每 tick 最大发射数量。
	 */
	public static final String MAX_PER_TICK = PREFIX + "max_per_tick";
	/**
	 * 最大嵌套深度。
	 */
	public static final String MAX_DEPTH = PREFIX + "max_depth";
	/**
	 * 实例存活时间（tick，0：沿用原效果）。
	 */
	public static final String INSTANCE_LIFETIME = PREFIX + "instance_lifetime";
	/**
	 * 结束行为。
	 */
	public static final String END_BEHAVIOR = PREFIX + "end_behavior";
	/**
	 * 重启时清理实例。
	 */
	public static final String CLEAR_ON_RESTART = PREFIX + "clear_on_restart";
	/**
	 * 跟随发射器变换。
	 */
	public static final String FOLLOW = PREFIX + "follow";
	/**
	 * 继承旋转。
	 */
	public static final String INHERIT_ROTATION = PREFIX + "inherit_rotation";
	/**
	 * 继承缩放。
	 */
	public static final String INHERIT_SCALE = PREFIX + "inherit_scale";
	/**
	 * 继承颜色。
	 */
	public static final String INHERIT_COLOR = PREFIX + "inherit_color";
	/**
	 * 继承速度。
	 */
	public static final String INHERIT_VELOCITY = PREFIX + "inherit_velocity";
	/**
	 * 实例移动速度（格/tick）。
	 */
	public static final String LAUNCH_SPEED = PREFIX + "launch_speed";
	/**
	 * 发射形状。
	 */
	public static final String SHAPE = PREFIX + "shape";
	/**
	 * 形状。
	 */
	public static final String SHAPE_TYPE = PREFIX + "shape_type";
	/**
	 * 形状范围／半径。
	 */
	public static final String SHAPE_SIZE = PREFIX + "shape_size";
	/**
	 * 本地位置偏移。
	 */
	public static final String SHAPE_OFFSET = PREFIX + "shape_offset";
	/**
	 * 实例参数。
	 */
	public static final String PARAMETERS = PREFIX + "parameters";
	/**
	 * 参数目标（空：所有兼容发射器）。
	 */
	public static final String TARGET = PREFIX + "target";
	/**
	 * 覆盖粒子寿命。
	 */
	public static final String OVERRIDE_LIFETIME = PREFIX + "override_lifetime";
	/**
	 * 粒子寿命（tick）。
	 */
	public static final String LIFETIME = PREFIX + "lifetime";
	/**
	 * 覆盖粒子初始大小。
	 */
	public static final String OVERRIDE_SIZE = PREFIX + "override_size";
	/**
	 * 粒子初始大小。
	 */
	public static final String SIZE = PREFIX + "size";
	/**
	 * 覆盖粒子初始颜色。
	 */
	public static final String OVERRIDE_COLOR = PREFIX + "override_color";
	/**
	 * 粒子初始颜色。
	 */
	public static final String COLOR = PREFIX + "color";
	/**
	 * 覆盖粒子初始速度。
	 */
	public static final String OVERRIDE_SPEED = PREFIX + "override_speed";
	/**
	 * 粒子初始速度。
	 */
	public static final String SPEED = PREFIX + "speed";
	/**
	 * 覆盖光柱长度。
	 */
	public static final String OVERRIDE_BEAM_LENGTH = PREFIX + "override_beam_length";
	/**
	 * 光柱长度（格）。
	 */
	public static final String BEAM_LENGTH = PREFIX + "beam_length";
	/**
	 * 覆盖光柱宽度。
	 */
	public static final String OVERRIDE_BEAM_WIDTH = PREFIX + "override_beam_width";
	/**
	 * 光柱宽度（格）。
	 */
	public static final String BEAM_WIDTH = PREFIX + "beam_width";
	/**
	 * 实例播放倍率。
	 */
	public static final String PLAYBACK_SPEED = PREFIX + "playback_speed";
	/**
	 * 存活实例。
	 */
	public static final String INSTANCES = PREFIX + "instances";
	/**
	 * 已生成实例数。
	 */
	public static final String GENERATED = PREFIX + "generated";
	/**
	 * 最近发射状态。
	 */
	public static final String FAILURE = PREFIX + "failure";

	public static String enumKey(Enum<?> value) {
		return PREFIX + "enum." + value.name();
	}

	public static String register() {
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_emitter_spawner", "Emitter spawner", "发射器发射器");
		LangHandler.creates(Lobecorp.NAMESPACE, CONFIG, "Emitter spawner", "发射器发射器");
		LangHandler.creates(Lobecorp.NAMESPACE, PhotonEditorTextUtil.key("feature." + FEATURE), "Emitter spawner controls", "发射器发射器控件");
		registerFields();
		registerEnums();
		registerStatus();
		return PREFIX;
	}

	private static void registerFields() {
		LangHandler.creates(Lobecorp.NAMESPACE, ENABLED, "Enabled", "启用");
		LangHandler.creates(Lobecorp.NAMESPACE, SOURCE, "Source", "来源");
		LangHandler.creates(Lobecorp.NAMESPACE, TEMPLATE, "Emitter template", "发射器模板");
		LangHandler.creates(Lobecorp.NAMESPACE, CLEAR_TEMPLATE, "Remove", "移除");
		LangHandler.creates(Lobecorp.NAMESPACE, FX, "FX resource", "FX 资源");
		LangHandler.creates(Lobecorp.NAMESPACE, INCLUDE_CHILDREN, "Include template children", "包含模板子对象");
		LangHandler.creates(Lobecorp.NAMESPACE, DURATION, "Duration (ticks)", "发射持续时间（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, LOOPING, "Loop emission", "循环发射");
		LangHandler.creates(Lobecorp.NAMESPACE, START_DELAY, "Start delay (ticks)", "开始延迟（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, MODE, "Emission mode", "发射模式");
		LangHandler.creates(Lobecorp.NAMESPACE, INTERVAL, "Interval (ticks)", "发射间隔（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, COUNT, "Instances per trigger", "每次发射实例数");
		LangHandler.creates(Lobecorp.NAMESPACE, RATE, "Instances per tick", "每 tick 发射实例数");
		LangHandler.creates(Lobecorp.NAMESPACE, PROBABILITY, "Spawn probability", "发射概率");
		LangHandler.creates(Lobecorp.NAMESPACE, MAX_INSTANCES, "Maximum live instances", "最大存活实例数");
		LangHandler.creates(Lobecorp.NAMESPACE, MAX_TOTAL, "Total instance limit (0: unlimited)", "总发射数量（0：不限）");
		LangHandler.creates(Lobecorp.NAMESPACE, MAX_PER_TICK, "Maximum spawns per tick", "每 tick 最大发射数量");
		LangHandler.creates(Lobecorp.NAMESPACE, MAX_DEPTH, "Maximum nesting depth", "最大嵌套深度");
		LangHandler.creates(Lobecorp.NAMESPACE, INSTANCE_LIFETIME, "Instance lifetime (ticks, 0: original)", "实例存活时间（tick，0：沿用原效果）");
		LangHandler.creates(Lobecorp.NAMESPACE, END_BEHAVIOR, "End behavior", "结束行为");
		LangHandler.creates(Lobecorp.NAMESPACE, CLEAR_ON_RESTART, "Clear instances on restart", "重启时清理实例");
		LangHandler.creates(Lobecorp.NAMESPACE, FOLLOW, "Follow spawner transform", "跟随发射器变换");
		LangHandler.creates(Lobecorp.NAMESPACE, INHERIT_ROTATION, "Inherit rotation", "继承旋转");
		LangHandler.creates(Lobecorp.NAMESPACE, INHERIT_SCALE, "Inherit scale", "继承缩放");
		LangHandler.creates(Lobecorp.NAMESPACE, INHERIT_COLOR, "Inherit color", "继承颜色");
		LangHandler.creates(Lobecorp.NAMESPACE, INHERIT_VELOCITY, "Inherit velocity", "继承速度");
		LangHandler.creates(Lobecorp.NAMESPACE, LAUNCH_SPEED, "Instance movement speed (blocks/tick)", "实例移动速度（格/tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, SHAPE, "Spawn shape", "发射形状");
		LangHandler.creates(Lobecorp.NAMESPACE, SHAPE_TYPE, "Shape", "形状");
		LangHandler.creates(Lobecorp.NAMESPACE, SHAPE_SIZE, "Shape extent / radius", "形状范围／半径");
		LangHandler.creates(Lobecorp.NAMESPACE, SHAPE_OFFSET, "Local position offset", "本地位置偏移");
		LangHandler.creates(Lobecorp.NAMESPACE, PARAMETERS, "Instance parameters", "实例参数");
		LangHandler.creates(Lobecorp.NAMESPACE, TARGET, "Parameter target (empty: all compatible emitters)", "参数目标（空：所有兼容发射器）");
		LangHandler.creates(Lobecorp.NAMESPACE, OVERRIDE_LIFETIME, "Override particle lifetime", "覆盖粒子寿命");
		LangHandler.creates(Lobecorp.NAMESPACE, LIFETIME, "Particle lifetime (ticks)", "粒子寿命（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, OVERRIDE_SIZE, "Override particle start size", "覆盖粒子初始大小");
		LangHandler.creates(Lobecorp.NAMESPACE, SIZE, "Particle start size", "粒子初始大小");
		LangHandler.creates(Lobecorp.NAMESPACE, OVERRIDE_COLOR, "Override particle start color", "覆盖粒子初始颜色");
		LangHandler.creates(Lobecorp.NAMESPACE, COLOR, "Particle start color", "粒子初始颜色");
		LangHandler.creates(Lobecorp.NAMESPACE, OVERRIDE_SPEED, "Override particle start speed", "覆盖粒子初始速度");
		LangHandler.creates(Lobecorp.NAMESPACE, SPEED, "Particle start speed", "粒子初始速度");
		LangHandler.creates(Lobecorp.NAMESPACE, OVERRIDE_BEAM_LENGTH, "Override beam length", "覆盖光柱长度");
		LangHandler.creates(Lobecorp.NAMESPACE, BEAM_LENGTH, "Beam length (blocks)", "光柱长度（格）");
		LangHandler.creates(Lobecorp.NAMESPACE, OVERRIDE_BEAM_WIDTH, "Override beam width", "覆盖光柱宽度");
		LangHandler.creates(Lobecorp.NAMESPACE, BEAM_WIDTH, "Beam width (blocks)", "光柱宽度（格）");
		LangHandler.creates(Lobecorp.NAMESPACE, PLAYBACK_SPEED, "Instance playback speed", "实例播放倍率");
		LangHandler.creates(Lobecorp.NAMESPACE, INSTANCES, "Live instances", "存活实例");
		LangHandler.creates(Lobecorp.NAMESPACE, GENERATED, "Generated instances", "已生成实例数");
		LangHandler.creates(Lobecorp.NAMESPACE, FAILURE, "Last spawn status", "最近发射状态");
	}

	private static void registerEnums() {
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.PROJECT", "Project emitter", "项目发射器");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.FX", "Saved FX resource", "已保存 FX 资源");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.ONCE", "Once per cycle", "每周期一次");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.INTERVAL", "Interval", "按间隔");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.RATE", "Rate", "按速率");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.ONE_SHOT", "One shot per start", "一次性发射");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.WAIT", "Keep existing instances", "保留已有实例");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.STOP_EMISSION", "Stop instance emission; let remnants drain", "停止实例发射并等待残留结束");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.CLEAR", "Clear all instances", "清理所有实例");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.POINT", "Point", "点");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.BOX", "Box", "盒");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.SPHERE", "Sphere", "球");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "enum.CIRCLE", "Circle", "圆");
	}

	private static void registerStatus() {
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "status.missing_template", "Template missing or is not an emitter.", "模板不存在或不是发射器。");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "status.missing_fx", "FX resource is unavailable.", "FX 资源不可用。");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "status.recursive", "Recursive source or nesting limit reached.", "检测到循环来源或达到嵌套深度上限。");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "status.invalid", "Invalid configuration or template data.", "配置或模板数据无效。");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "status.capacity", "Instance or per-tick limit reached.", "达到实例数量或每 tick 发射上限。");
		LangHandler.creates(Lobecorp.NAMESPACE, PREFIX + "status.ok", "Ready", "正常");
	}
}
