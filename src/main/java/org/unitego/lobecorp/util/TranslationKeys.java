package org.unitego.lobecorp.util;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;

public interface TranslationKeys {
	String PHOTON_EDITOR_TEXT = PhotonEditorTextUtil.register();
	/**
	 * 发射器发射器的原生编辑器控件和数据生成翻译入口。
	 */
	@SuppressWarnings("unused")
	String PHOTON_EMITTER_SPAWNER_TEXT = PhotonEmitterSpawnerTextUtil.register();
	/**
	 * 循环采样的时间偏移；单位 tick。
	 */
	String PHOTON_CYCLE_TIME_OFFSET_AXES_KEY = "photon.lobecorp.cycle_time_offset.axes";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_TIME_OFFSET_AXES = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_TIME_OFFSET_AXES_KEY,
			"Time offset (ticks)", "时间偏移（tick）");
	/**
	 * 循环采样的时间偏移；单位 tick。
	 */
	String PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY = "photon.lobecorp.cycle_time_offset.rotation";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_TIME_OFFSET_ROTATION = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY,
			"Rotation time offset (ticks)", "旋转时间偏移（tick）");
	/**
	 * 循环采样的时间偏移；单位 tick。
	 */
	String PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY = "photon.lobecorp.cycle_time_offset.linear";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_TIME_OFFSET_LINEAR = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY,
			"Linear time offset (ticks)", "线性时间偏移（tick）");
	/**
	 * 循环采样的时间偏移；单位 tick。
	 */
	String PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY = "photon.lobecorp.cycle_time_offset.orbital";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_TIME_OFFSET_ORBITAL = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY,
			"Orbital time offset (ticks)", "轨道时间偏移（tick）");
	/**
	 * 循环采样的时间偏移；单位 tick。
	 */
	String PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY = "photon.lobecorp.cycle_time_offset.center";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_TIME_OFFSET_CENTER = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY,
			"Orbital center time offset (ticks)", "轨道中心时间偏移（tick）");
	/**
	 * 循环采样的时间偏移；单位 tick。
	 */
	String PHOTON_CYCLE_TIME_OFFSET_UV_KEY = "photon.lobecorp.cycle_time_offset.uv";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_TIME_OFFSET_UV = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_TIME_OFFSET_UV_KEY,
			"UV time offset (ticks)", "UV 时间偏移（tick）");
	/**
	 * 循环采样的 X 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_X_KEY = "photon.lobecorp.cycle_offset.x";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_X = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_X_KEY,
			"X time offset (ticks)", "X 时间偏移（tick）");
	/**
	 * 循环采样的 Y 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_Y_KEY = "photon.lobecorp.cycle_offset.y";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_Y = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_Y_KEY,
			"Y time offset (ticks)", "Y 时间偏移（tick）");
	/**
	 * 循环采样的 Z 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_Z_KEY = "photon.lobecorp.cycle_offset.z";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_Z = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_Z_KEY,
			"Z time offset (ticks)", "Z 时间偏移（tick）");
	/**
	 * 循环采样的 翻滚时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_ROLL_KEY = "photon.lobecorp.cycle_offset.roll";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_ROLL = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_ROLL_KEY,
			"Roll time offset (ticks)", "翻滚时间偏移（tick）");
	/**
	 * 循环采样的 俯仰时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_PITCH_KEY = "photon.lobecorp.cycle_offset.pitch";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_PITCH = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_PITCH_KEY,
			"Pitch time offset (ticks)", "俯仰时间偏移（tick）");
	/**
	 * 循环采样的 偏航时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_YAW_KEY = "photon.lobecorp.cycle_offset.yaw";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_YAW = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_YAW_KEY,
			"Yaw time offset (ticks)", "偏航时间偏移（tick）");
	/**
	 * 循环采样的 轨道 X 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_ORBITAL_X_KEY = "photon.lobecorp.cycle_offset.orbital_x";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_ORBITAL_X = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_ORBITAL_X_KEY,
			"Orbital X time offset (ticks)", "轨道 X 时间偏移（tick）");
	/**
	 * 循环采样的 轨道 Y 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_ORBITAL_Y_KEY = "photon.lobecorp.cycle_offset.orbital_y";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_ORBITAL_Y = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_ORBITAL_Y_KEY,
			"Orbital Y time offset (ticks)", "轨道 Y 时间偏移（tick）");
	/**
	 * 循环采样的 轨道 Z 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_ORBITAL_Z_KEY = "photon.lobecorp.cycle_offset.orbital_z";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_ORBITAL_Z = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_ORBITAL_Z_KEY,
			"Orbital Z time offset (ticks)", "轨道 Z 时间偏移（tick）");
	/**
	 * 循环采样的 轨道中心 X 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_CENTER_X_KEY = "photon.lobecorp.cycle_offset.center_x";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_CENTER_X = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_CENTER_X_KEY,
			"Orbital center X time offset (ticks)", "轨道中心 X 时间偏移（tick）");
	/**
	 * 循环采样的 轨道中心 Y 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_CENTER_Y_KEY = "photon.lobecorp.cycle_offset.center_y";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_CENTER_Y = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_CENTER_Y_KEY,
			"Orbital center Y time offset (ticks)", "轨道中心 Y 时间偏移（tick）");
	/**
	 * 循环采样的 轨道中心 Z 时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_CENTER_Z_KEY = "photon.lobecorp.cycle_offset.center_z";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_CENTER_Z = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_CENTER_Z_KEY,
			"Orbital center Z time offset (ticks)", "轨道中心 Z 时间偏移（tick）");
	/**
	 * 循环采样的 径向时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_RADIAL_KEY = "photon.lobecorp.cycle_offset.radial";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_RADIAL = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_RADIAL_KEY,
			"Radial time offset (ticks)", "径向时间偏移（tick）");
	/**
	 * 循环采样的 速度倍率时间偏移 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY = "photon.lobecorp.cycle_offset.multiplier";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_MULTIPLIER = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY,
			"Speed multiplier time offset (ticks)", "速度倍率时间偏移（tick）");
	/**
	 * 循环采样的 红色时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_RED_KEY = "photon.lobecorp.cycle_offset.red";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_RED = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_RED_KEY,
			"Red time offset (ticks)", "红色时间偏移（tick）");
	/**
	 * 循环采样的 绿色时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_GREEN_KEY = "photon.lobecorp.cycle_offset.green";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_GREEN = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_GREEN_KEY,
			"Green time offset (ticks)", "绿色时间偏移（tick）");
	/**
	 * 循环采样的 蓝色时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_BLUE_KEY = "photon.lobecorp.cycle_offset.blue";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_BLUE = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_BLUE_KEY,
			"Blue time offset (ticks)", "蓝色时间偏移（tick）");
	/**
	 * 循环采样的 透明度时间偏移（tick） 的配置标签。
	 */
	String PHOTON_CYCLE_OFFSET_ALPHA_KEY = "photon.lobecorp.cycle_offset.alpha";
	@SuppressWarnings("unused")
	String PHOTON_CYCLE_OFFSET_ALPHA = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_CYCLE_OFFSET_ALPHA_KEY,
			"Alpha time offset (ticks)", "透明度时间偏移（tick）");
	/**
	 * 发射器之间按视线深度重排的原生编辑器开关。
	 */
	String PHOTON_VIEW_DEPTH_SORT_KEY = "photon.lobecorp.renderer.view_depth_sort";
	/**
	 * 说明新排序模式的启用范围与限制。
	 */
	String PHOTON_VIEW_DEPTH_SORT_TIPS_KEY = "photon.lobecorp.renderer.view_depth_sort.tips";
	@SuppressWarnings("unused")
	String PHOTON_VIEW_DEPTH_SORT = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_VIEW_DEPTH_SORT_KEY,
			"View depth sorting", "视线深度排序");
	@SuppressWarnings("unused")
	String PHOTON_VIEW_DEPTH_SORT_TIPS = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_VIEW_DEPTH_SORT_TIPS_KEY,
			"Sort enabled emitters with the same Order by camera view depth. Enable on all overlapping emitters. Does not resolve intersecting geometry.",
			"相同 Order 中开启此选项的发射器按视线深度排序。请在需要互相叠加的发射器上同时开启，无法解决面片相交。");
	/**
	 * 颜色、大小、速度和受力模块的循环开关，与粒子寿命独立。
	 */
	String PHOTON_LIFETIME_CYCLE_ENABLED_KEY = "photon.lobecorp.lifetime_cycle.enabled";
	/**
	 * 对应模块的曲线完整循环一次的 tick 数。
	 */
	String PHOTON_LIFETIME_CYCLE_TICKS_KEY = "photon.lobecorp.lifetime_cycle.ticks";
	@SuppressWarnings("unused")
	String PHOTON_LIFETIME_CYCLE_ENABLED = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_LIFETIME_CYCLE_ENABLED_KEY,
			"Loop curves", "循环曲线");
	@SuppressWarnings("unused")
	String PHOTON_LIFETIME_CYCLE_TICKS = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_LIFETIME_CYCLE_TICKS_KEY,
			"Curve cycle (ticks)", "曲线周期（tick）");
	/**
	 * 循环采样旋转曲线的编辑器开关，与粒子寿命独立。
	 */
	String PHOTON_ROTATION_CYCLE_ENABLED_KEY = "photon.lobecorp.rotation_cycle.enabled";
	/**
	 * 旋转曲线完整循环一次的 tick 数。
	 */
	String PHOTON_ROTATION_CYCLE_TICKS_KEY = "photon.lobecorp.rotation_cycle.ticks";
	String PHOTON_ROTATION_CYCLE_ENABLED = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_ROTATION_CYCLE_ENABLED_KEY,
			"Loop rotation", "循环旋转");
	String PHOTON_ROTATION_CYCLE_TICKS = LangHandler.creates(Lobecorp.NAMESPACE, PHOTON_ROTATION_CYCLE_TICKS_KEY,
			"Rotation cycle (ticks)", "旋转周期（tick）");


	String CONDUCTOR_ABILITY_LOBECORP_WARDEN_SONIC_BOOM = LangHandler.creates(Lobecorp.NAMESPACE, "conductor_ability.lobecorp.warden_sonic_boom",
			"Sonic Boom", "音波冲击");

	String DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_HITBOX = LangHandler.creates(Lobecorp.NAMESPACE, "debug.lobecorp.skill_effect.lobecorp.hitbox",
			"Hitbox: {0}", "命中框：{0}");
	String DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_LIFETIME = LangHandler.creates(Lobecorp.NAMESPACE, "debug.lobecorp.skill_effect.lobecorp.lifetime",
			"Lifetime: {0}", "生命周期：{0}");
	String DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_NAME = LangHandler.creates(Lobecorp.NAMESPACE, "debug.lobecorp.skill_effect.lobecorp.name",
			"Name: {0}", "名称：{0}");
	String DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_OWNER = LangHandler.creates(Lobecorp.NAMESPACE, "debug.lobecorp.skill_effect.lobecorp.owner",
			"Owner: {0}", "所属者：{0}");
	String DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_POSITION = LangHandler.creates(Lobecorp.NAMESPACE, "debug.lobecorp.skill_effect.lobecorp.position",
			"Position: {0}", "位置：{0}");
	String DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_SKILL = LangHandler.creates(Lobecorp.NAMESPACE, "debug.lobecorp.skill_effect.lobecorp.skill",
			"Skill: {0}", "所属技能：{0}");

	String DEBUG_ENTRY_LOBECORP_SKILL_EFFECT_ENTITIES = LangHandler.creates(Lobecorp.NAMESPACE, "debug_entry.lobecorp.skill_effect_entities",
			"Skill Entities", "技能实体");

	String ENTITY_LOBECORP_ENTITY_CORPSE_DISPLAY_NAME = LangHandler.creates(Lobecorp.NAMESPACE, "entity.lobecorp.entity_corpse.display_name",
			"%s Corpse", "%s尸体");

	String KEY_CATEGORY_LOBECORP_CONDUCTOR = LangHandler.creates(Lobecorp.NAMESPACE, "key.category.lobecorp.conductor",
			"Conductor", "指挥家");
	String KEY_LOBECORP_CONDUCTOR_FOLLOW = LangHandler.creates(Lobecorp.NAMESPACE, "key.lobecorp.conductor.follow",
			"Follow selected unit", "跟随选中单位");
	String KEY_LOBECORP_CONDUCTOR_MENU = LangHandler.creates(Lobecorp.NAMESPACE, "key.lobecorp.conductor.menu",
			"Conductor panel", "指挥家界面");
	String KEY_LOBECORP_CONDUCTOR_ROTATE_LEFT = LangHandler.creates(Lobecorp.NAMESPACE, "key.lobecorp.conductor.rotate_left",
			"Rotate camera left", "镜头左转");
	String KEY_LOBECORP_CONDUCTOR_ROTATE_RIGHT = LangHandler.creates(Lobecorp.NAMESPACE, "key.lobecorp.conductor.rotate_right",
			"Rotate camera right", "镜头右转");
	String KEY_LOBECORP_CONDUCTOR_TOGGLE = LangHandler.creates(Lobecorp.NAMESPACE, "key.lobecorp.conductor.toggle",
			"Toggle conductor mode", "切换指挥家模式");

	String PACK_LOBECORP_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "pack.lobecorp.description",
			"Lobotomy Corporation", "脑叶公司");

	String SCREEN_LOBECORP_CONDUCTOR_APPLY_COLOR = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.apply_color",
			"Apply color", "应用颜色");
	String SCREEN_LOBECORP_CONDUCTOR_ASSIGN = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.assign",
			"Assign selected", "选中单位入队");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.attack",
			"Attack", "攻击");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK_AI = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.attack_ai",
			"Attack: AI", "攻击：完全 AI");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK_BASIC = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.attack_basic",
			"Attack: basic only", "攻击：仅普攻");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK_MODE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.attack_mode",
			"Attack mode", "攻击模式");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_GUARD = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_guard",
			"Guard", "驻守");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_STANDBY = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_standby",
			"Standby", "待命");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_COOLDOWN = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_unavailable_cooldown",
			"Unavailable: skill is cooling down", "不可用：技能冷却中");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CASTING = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_unavailable_casting",
			"Unavailable: skill is being cast", "不可用：技能正在施放");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CONDITION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_unavailable_condition",
			"Unavailable: requirements not met", "不可用：当前条件不满足");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_TARGET = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_unavailable_target",
			"Unavailable: invalid target or cast position", "不可用：目标或施放位置无效");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_OUT_OF_RANGE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_out_of_range",
			"Out of casting range", "超出技能施放范围");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_IDLE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_idle",
			"Idle", "待机");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_MODE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_mode",
			"Behavior state", "行为状态");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_VALUE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_value",
			"Behavior: %s", "行为：%s");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_ACTIVE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_active",
			"Aggressive", "主动");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_PASSIVE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_passive",
			"Passive", "被动");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_NEUTRAL = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_neutral",
			"Neutral", "中立");
	String SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_MODE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.activity_mode",
			"Activity mode", "活动模式");
	String SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_VALUE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.activity_value",
			"Activity: %s", "活动：%s");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_PATROL = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.behavior_patrol",
			"Patrol", "巡逻");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_SCATTERED = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.formation_scattered",
			"Scattered", "零散");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_REGULAR = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.formation_regular",
			"Formation", "队形");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_UNIFORM = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.formation_uniform",
			"Uniform-speed formation", "匀速队形");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_SCATTERED_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.formation_scattered_description",
			"Move freely toward the destination without formation slots. Applies to the next move order.", "不分配队形槽位，自由前往指定位置。对下一次移动指令生效。");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_REGULAR_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.formation_regular_description",
			"Units farther forward take the front slots. Each unit moves at its own speed and tries to avoid blocking entities.", "沿移动方向靠前的单位进入前排，各自按原速度移动，尝试绕开挡路实体。");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_UNIFORM_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.formation_uniform_description",
			"Use formation destinations and the slowest selected unit's speed. Units may separate while moving around obstacles.", "按队形分配终点，以本次指令中最慢单位的速度行进；绕行途中不强制保持队形。");
	String SCREEN_LOBECORP_CONDUCTOR_CLOSE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.close",
			"Close", "关闭");
	String SCREEN_LOBECORP_CONDUCTOR_COLOR = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.color",
			"RGB color", "RGB 颜色");
	String SCREEN_LOBECORP_CONDUCTOR_COMMAND = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.command",
			"Patrol", "巡逻");
	String SCREEN_LOBECORP_CONDUCTOR_CONTROLS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.controls",
			"Unit control", "单位控制");
	String SCREEN_LOBECORP_CONDUCTOR_CREATE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.create",
			"Create", "创建");
	String SCREEN_LOBECORP_CONDUCTOR_ENEMY_TEAM = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.enemy_team",
			"Other team", "对方队伍");
	String SCREEN_LOBECORP_CONDUCTOR_FULL = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.full",
			"Idle", "待机");
	String SCREEN_LOBECORP_CONDUCTOR_HEALTH = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.health",
			"Health: %s / %s", "生命：%s / %s");
	String SCREEN_LOBECORP_CONDUCTOR_LOAD_OFF = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.load_off",
			"Chunk loading: off", "区块加载：关闭");
	String SCREEN_LOBECORP_CONDUCTOR_LOAD_ON = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.load_on",
			"Chunk loading: on", "区块加载：开启");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.move",
			"Move", "移动");
	String SCREEN_LOBECORP_CONDUCTOR_NEXT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.next",
			"Next", "下一页");
	String SCREEN_LOBECORP_CONDUCTOR_NO_TEAM = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.no_team",
			"No team", "未入队");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_ATTACK = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_attack",
			"Attacking target", "攻击目标");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_ATTACK_POINT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_attack_point",
			"Attacking point", "攻击地点");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_MOVE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_move",
			"Moving", "移动中");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_NONE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_none",
			"Idle", "待命");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_RETURN = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_return",
			"Returning to origin", "返回原点");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_STOP = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_stop",
			"Stopped", "已停止");
	String SCREEN_LOBECORP_CONDUCTOR_PEACE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.peace",
			"Make peace", "停战");
	String SCREEN_LOBECORP_CONDUCTOR_PREVIOUS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.previous",
			"Previous", "上一页");
	String SCREEN_LOBECORP_CONDUCTOR_RELEASE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.release",
			"Release selected", "释放选中单位");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_ATTACK = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.remote_attack",
			"Attack target UUID", "攻击目标 UUID");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_CAST = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.remote_cast",
			"Cast skill by ID", "按 ID 施放技能");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_MOVE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.remote_move",
			"Move to coordinates", "移动到坐标");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_ORDERS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.remote_orders",
			"Remote orders", "远程命令");
	String SCREEN_LOBECORP_CONDUCTOR_SELECTED = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.selected",
			"Selected: %s", "已选：%s");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_COOLDOWN = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_cooldown",
			"%s · %ss cooldown", "%s · 冷却 %s 秒");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_ID = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skill_id",
			"Skill ID", "技能 ID");
	String SCREEN_LOBECORP_CONDUCTOR_SKILLS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.skills",
			"Skills", "技能");
	String SCREEN_LOBECORP_CONDUCTOR_SOFT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.soft",
			"Guard", "驻守");
	String SCREEN_LOBECORP_CONDUCTOR_STOP = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.stop",
			"Stop", "停止");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_UUID = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.target_uuid",
			"Target UUID", "目标 UUID");
	String SCREEN_LOBECORP_CONDUCTOR_TEAM = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.team",
			"Team name", "队伍名称");
	String SCREEN_LOBECORP_CONDUCTOR_TEAM_MANAGEMENT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.team_management",
			"Team management", "团队管理");
	String SCREEN_LOBECORP_CONDUCTOR_SETTINGS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.settings",
			"Settings", "设置");
	String SCREEN_LOBECORP_CONDUCTOR_SEARCH = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.search",
			"Search members", "搜索队员");
	String SCREEN_LOBECORP_CONDUCTOR_CLEAR_SEARCH = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.clear_search",
			"Clear search", "清除搜索");
	String SCREEN_LOBECORP_CONDUCTOR_SEARCH_SKILLS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.search_skills",
			"Search skills...", "搜索技能...");
	String SCREEN_LOBECORP_CONDUCTOR_ROSTER_COUNT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.roster_count",
			"Selected %s / %s", "已选 %s / %s");
	String SCREEN_LOBECORP_CONDUCTOR_NO_MATCHING_MEMBERS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.no_matching_members",
			"No matching units", "无匹配生物");
	String SCREEN_LOBECORP_CONDUCTOR_NO_MATCHING_SKILLS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.no_matching_skills",
			"No matching skills", "无匹配技能");
	String SCREEN_LOBECORP_CONDUCTOR_NO_SELECTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.no_selection",
			"No unit selected", "未选中生物");
	String SCREEN_LOBECORP_CONDUCTOR_NO_SKILLS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.no_skills",
			"This unit has no skills", "该生物没有技能");
	String SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_HEALTH = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.unknown_health",
			"Health: unknown", "生命值：未知");
	String SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_ATTRIBUTES = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.unknown_attributes",
			"Attributes unavailable", "属性未知");
	String SCREEN_LOBECORP_CONDUCTOR_ATTRIBUTES = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.attributes",
			"ATK %s  ARM %s", "攻 %s  防 %s");
	String SCREEN_LOBECORP_CONDUCTOR_DISTANCE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.distance",
			"Distance: %s", "距离：%s");
	String SCREEN_LOBECORP_CONDUCTOR_DIMENSION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.dimension",
			"Dimension: %s", "维度：%s");
	String SCREEN_LOBECORP_CONDUCTOR_KNOWN_HEALTH = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.known_health",
			"Known %s/%s", "已知 %s/%s");
	String SCREEN_LOBECORP_CONDUCTOR_AVERAGE_HEALTH = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.average_health",
			"Average HP %s%% (%s/%s known)", "平均血量 %s%%（已知 %s/%s）");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_FOCUS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.remote_focus",
			"Unit is not loaded; camera focus unavailable", "单位未加载，无法聚焦视角");
	String SCREEN_LOBECORP_CONDUCTOR_READY = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.ready",
			"Ready", "就绪");
	String SCREEN_LOBECORP_CONDUCTOR_REDUCE_MOTION_ON = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.reduce_motion_on",
			"Reduced motion: On", "减弱动效：开");
	String SCREEN_LOBECORP_CONDUCTOR_REDUCE_MOTION_OFF = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.reduce_motion_off",
			"Reduced motion: Off", "减弱动效：关");
	String SCREEN_LOBECORP_CONDUCTOR_EFFECT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.effect",
			"%s %s (%ss)", "%s %s（%s秒）");
	String SCREEN_LOBECORP_CONDUCTOR_EMPTY_TEAM = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.empty_team",
			"No team members", "队伍暂无成员");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_SKILLS = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.remote_skills",
			"Unit not loaded; use remote orders", "单位未加载，请使用远程指令");
	String SCREEN_LOBECORP_CONDUCTOR_HEALTH_PERCENT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.health_percent",
			"HP %s%%", "生命 %s%%");
	String SCREEN_LOBECORP_CONDUCTOR_OFFLINE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.offline",
			"Offline", "未加载");
	String SCREEN_LOBECORP_CONDUCTOR_DAMAGE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.damage",
			"Damage: %s", "伤害：%s");
	String SCREEN_LOBECORP_CONDUCTOR_DAMAGE_DYNAMIC = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.damage_dynamic",
			"Dynamic", "动态");
	String SCREEN_LOBECORP_CONDUCTOR_DAMAGE_NONE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.damage_none",
			"No direct damage", "无直接伤害");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.type",
			"Type: %s", "类型：%s");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE_BASIC = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.type_basic",
			"Basic attack", "普攻");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE_ATTACK = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.type_attack",
			"Attack skill", "攻击技能");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE_SUPPORT = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.type_support",
			"Support skill", "辅助技能");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.target",
			"Target: %s", "目标：%s");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_SELF = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.target_self",
			"Self", "自身");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_ENTITY = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.target_entity",
			"Entity", "实体");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_POSITION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.target_position",
			"Position", "坐标");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_EITHER = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.target_either",
			"Entity or position", "实体或坐标");
	String SCREEN_LOBECORP_CONDUCTOR_RANGE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.range",
			"Range: %s", "范围：%s");
	String SCREEN_LOBECORP_CONDUCTOR_RANGE_UNSPECIFIED = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.range_unspecified",
			"Dynamic", "动态");
	String SCREEN_LOBECORP_CONDUCTOR_RANGE_SELF = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.range_self",
			"Self", "自身");
	String SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_TOTAL = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.cooldown_total",
			"Cooldown: %ss", "冷却：%s 秒");
	String SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_REMAINING = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.cooldown_remaining",
			"Remaining: %ss", "剩余：%s 秒");
	String SCREEN_LOBECORP_CONDUCTOR_TITLE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.title",
			"Conductor", "指挥家");
	String SCREEN_LOBECORP_CONDUCTOR_WAR = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.war",
			"Declare war", "宣战");
	String SCREEN_LOBECORP_CONDUCTOR_X = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.x",
			"X", "X");
	String SCREEN_LOBECORP_CONDUCTOR_Y = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.y",
			"Y", "Y");
	String SCREEN_LOBECORP_CONDUCTOR_Z = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.z",
			"Z", "Z");

	String SCREEN_LOBECORP_CONDUCTOR_ORDER_CLEANUP = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_cleanup",
			"Cleaning", "持续清理");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_REASSEMBLE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.order_reassemble",
			"Reassembling", "持续重组");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE_ARRIVED = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.move_arrived",
			"Formation reached", "已到达队形槽位");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE_UNREACHABLE = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.move_unreachable",
			"Destination unreachable", "无法到达目标位置");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE_CANCELED = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.move_canceled",
			"Movement canceled", "移动已取消");
	String SCREEN_LOBECORP_CONDUCTOR_CLEANUP_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.cleanup_description",
			"Continuously finds and cleans nearby non-Sweeper corpses and dropped items, restoring health. Finds the next target automatically; stops when no reachable targets remain.", "持续寻找并清理附近的非清道夫尸体和掉落物，恢复生命。完成后自动寻找下一目标；无目标或目标均不可达时结束。");
	String SCREEN_LOBECORP_CONDUCTOR_REASSEMBLE_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.reassemble_description",
			"Continuously finds nearby Sweeper corpses and reassembles them. Consumes biomass equal to 10% of the target's maximum health and revives it with that health. Finds the next target automatically; stops when biomass is insufficient or no reachable targets remain.", "持续寻找并重组附近符合条件的清道夫尸体，消耗目标最大生命值10%的生物质，并以该生命值复活。完成后自动寻找下一目标；资源不足、无目标或目标均不可达时结束。");
	String SCREEN_LOBECORP_CONDUCTOR_SONIC_DESCRIPTION = LangHandler.creates(Lobecorp.NAMESPACE, "screen.lobecorp.conductor.sonic_description",
			"Fires in the chosen direction after charging, hitting only the first valid enemy through walls. Can fire without a target and still enters cooldown.", "前摇后沿指定方向释放穿墙声波，仅命中路径上的首个有效敌人。无需锁定目标，空放也进入冷却。");

	static void init() {
	}
}
