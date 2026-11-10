package org.unitego.lobecorp.registry.translation;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.conductor.ability.WardenSonicBoomAbility;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface ConductorTranslationKeys {
	String CONDUCTOR_ABILITY_LOBECORP_WARDEN_SONIC_BOOM = register("conductor_ability.lobecorp.warden_sonic_boom", "Sonic Boom", "音波冲击");
	String KEY_CATEGORY_LOBECORP_CONDUCTOR = register("key.category.lobecorp.conductor", "Conductor", "指挥家");
	String KEY_LOBECORP_CONDUCTOR_FOLLOW = register("key.lobecorp.conductor.follow", "Follow selected unit", "跟随选中单位");
	String KEY_LOBECORP_CONDUCTOR_MENU = register("key.lobecorp.conductor.menu", "Conductor panel", "指挥家界面");
	String KEY_LOBECORP_CONDUCTOR_ROTATE_LEFT = register("key.lobecorp.conductor.rotate_left", "Rotate camera left", "镜头左转");
	String KEY_LOBECORP_CONDUCTOR_ROTATE_RIGHT = register("key.lobecorp.conductor.rotate_right", "Rotate camera right", "镜头右转");
	String KEY_LOBECORP_CONDUCTOR_TOGGLE = register("key.lobecorp.conductor.toggle", "Toggle conductor mode", "切换指挥家模式");
	String SCREEN_LOBECORP_CONDUCTOR_APPLY_COLOR = register("screen.lobecorp.conductor.apply_color", "Apply color", "应用颜色");
	String SCREEN_LOBECORP_CONDUCTOR_ASSIGN = register("screen.lobecorp.conductor.assign", "Assign selected", "选中单位入队");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK = register("screen.lobecorp.conductor.attack", "Attack", "攻击");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK_AI = register("screen.lobecorp.conductor.attack_ai", "Attack: AI", "攻击：完全 AI");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK_BASIC = register("screen.lobecorp.conductor.attack_basic", "Attack: basic only", "攻击：仅普攻");
	String SCREEN_LOBECORP_CONDUCTOR_ATTACK_MODE = register("screen.lobecorp.conductor.attack_mode", "Attack mode", "攻击模式");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_GUARD = register("screen.lobecorp.conductor.behavior_guard", "Guard", "驻守");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_STANDBY = register("screen.lobecorp.conductor.behavior_standby", "Standby", "待命");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_COOLDOWN = register("screen.lobecorp.conductor.skill_unavailable_cooldown", "Unavailable: skill is cooling down", "不可用：技能冷却中");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CASTING = register("screen.lobecorp.conductor.skill_unavailable_casting", "Unavailable: skill is being cast", "不可用：技能正在施放");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CONDITION = register("screen.lobecorp.conductor.skill_unavailable_condition", "Unavailable: requirements not met", "不可用：当前条件不满足");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_TARGET = register("screen.lobecorp.conductor.skill_unavailable_target", "Unavailable: invalid target or cast position", "不可用：目标或施放位置无效");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_OUT_OF_RANGE = register("screen.lobecorp.conductor.skill_out_of_range", "Out of casting range", "超出技能施放范围");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_IDLE = register("screen.lobecorp.conductor.behavior_idle", "Idle", "待机");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_MODE = register("screen.lobecorp.conductor.behavior_mode", "Behavior state", "行为状态");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_VALUE = register("screen.lobecorp.conductor.behavior_value", "Behavior: %s", "行为：%s");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_ACTIVE = register("screen.lobecorp.conductor.behavior_active", "Aggressive", "主动");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_PASSIVE = register("screen.lobecorp.conductor.behavior_passive", "Passive", "被动");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_NEUTRAL = register("screen.lobecorp.conductor.behavior_neutral", "Neutral", "中立");
	String SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_MODE = register("screen.lobecorp.conductor.activity_mode", "Activity mode", "活动模式");
	String SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_VALUE = register("screen.lobecorp.conductor.activity_value", "Activity: %s", "活动：%s");
	String SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_PATROL = register("screen.lobecorp.conductor.behavior_patrol", "Patrol", "巡逻");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_SCATTERED = register("screen.lobecorp.conductor.formation_scattered", "Scattered", "零散");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_REGULAR = register("screen.lobecorp.conductor.formation_regular", "Formation", "队形");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_UNIFORM = register("screen.lobecorp.conductor.formation_uniform", "Uniform-speed formation", "匀速队形");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_SCATTERED_DESCRIPTION = register("screen.lobecorp.conductor.formation_scattered_description", "Move freely toward the destination without formation slots. Applies to the next move order.", "不分配队形槽位，自由前往指定位置。对下一次移动指令生效。");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_REGULAR_DESCRIPTION = register("screen.lobecorp.conductor.formation_regular_description", "Units farther forward take the front slots. Each unit moves at its own speed and tries to avoid blocking entities.", "沿移动方向靠前的单位进入前排，各自按原速度移动，尝试绕开挡路实体。");
	String SCREEN_LOBECORP_CONDUCTOR_FORMATION_UNIFORM_DESCRIPTION = register("screen.lobecorp.conductor.formation_uniform_description", "Use formation destinations and the slowest selected unit's speed. Units may separate while moving around obstacles.", "按队形分配终点，以本次指令中最慢单位的速度行进；绕行途中不强制保持队形。");
	String SCREEN_LOBECORP_CONDUCTOR_CLOSE = register("screen.lobecorp.conductor.close", "Close", "关闭");
	String SCREEN_LOBECORP_CONDUCTOR_COLOR = register("screen.lobecorp.conductor.color", "RGB color", "RGB 颜色");
	String SCREEN_LOBECORP_CONDUCTOR_COMMAND = register("screen.lobecorp.conductor.command", "Patrol", "巡逻");
	String SCREEN_LOBECORP_CONDUCTOR_CONTROLS = register("screen.lobecorp.conductor.controls", "Unit control", "单位控制");
	String SCREEN_LOBECORP_CONDUCTOR_CREATE = register("screen.lobecorp.conductor.create", "Create", "创建");
	String SCREEN_LOBECORP_CONDUCTOR_ENEMY_TEAM = register("screen.lobecorp.conductor.enemy_team", "Other team", "对方队伍");
	String SCREEN_LOBECORP_CONDUCTOR_FULL = register("screen.lobecorp.conductor.full", "Idle", "待机");
	String SCREEN_LOBECORP_CONDUCTOR_HEALTH = register("screen.lobecorp.conductor.health", "Health: %s / %s", "生命：%s / %s");
	String SCREEN_LOBECORP_CONDUCTOR_LOAD_OFF = register("screen.lobecorp.conductor.load_off", "Chunk loading: off", "区块加载：关闭");
	String SCREEN_LOBECORP_CONDUCTOR_LOAD_ON = register("screen.lobecorp.conductor.load_on", "Chunk loading: on", "区块加载：开启");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE = register("screen.lobecorp.conductor.move", "Move", "移动");
	String SCREEN_LOBECORP_CONDUCTOR_NEXT = register("screen.lobecorp.conductor.next", "Next", "下一页");
	String SCREEN_LOBECORP_CONDUCTOR_NO_TEAM = register("screen.lobecorp.conductor.no_team", "No team", "未入队");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_ATTACK = register("screen.lobecorp.conductor.order_attack", "Attacking target", "攻击目标");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_ATTACK_POINT = register("screen.lobecorp.conductor.order_attack_point", "Attacking point", "攻击地点");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_MOVE = register("screen.lobecorp.conductor.order_move", "Moving", "移动中");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_NONE = register("screen.lobecorp.conductor.order_none", "Idle", "待命");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_RETURN = register("screen.lobecorp.conductor.order_return", "Returning to origin", "返回原点");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_STOP = register("screen.lobecorp.conductor.order_stop", "Stopped", "已停止");
	String SCREEN_LOBECORP_CONDUCTOR_PEACE = register("screen.lobecorp.conductor.peace", "Make peace", "停战");
	String SCREEN_LOBECORP_CONDUCTOR_PREVIOUS = register("screen.lobecorp.conductor.previous", "Previous", "上一页");
	String SCREEN_LOBECORP_CONDUCTOR_RELEASE = register("screen.lobecorp.conductor.release", "Release selected", "释放选中单位");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_ATTACK = register("screen.lobecorp.conductor.remote_attack", "Attack target UUID", "攻击目标 UUID");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_CAST = register("screen.lobecorp.conductor.remote_cast", "Cast skill by ID", "按 ID 施放技能");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_MOVE = register("screen.lobecorp.conductor.remote_move", "Move to coordinates", "移动到坐标");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_ORDERS = register("screen.lobecorp.conductor.remote_orders", "Remote orders", "远程命令");
	String SCREEN_LOBECORP_CONDUCTOR_SELECTED = register("screen.lobecorp.conductor.selected", "Selected: %s", "已选：%s");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_COOLDOWN = register("screen.lobecorp.conductor.skill_cooldown", "%s · %ss cooldown", "%s · 冷却 %s 秒");
	String SCREEN_LOBECORP_CONDUCTOR_SKILL_ID = register("screen.lobecorp.conductor.skill_id", "Skill ID", "技能 ID");
	String SCREEN_LOBECORP_CONDUCTOR_SKILLS = register("screen.lobecorp.conductor.skills", "Skills", "技能");
	String SCREEN_LOBECORP_CONDUCTOR_SOFT = register("screen.lobecorp.conductor.soft", "Guard", "驻守");
	String SCREEN_LOBECORP_CONDUCTOR_STOP = register("screen.lobecorp.conductor.stop", "Stop", "停止");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_UUID = register("screen.lobecorp.conductor.target_uuid", "Target UUID", "目标 UUID");
	String SCREEN_LOBECORP_CONDUCTOR_TEAM = register("screen.lobecorp.conductor.team", "Team name", "队伍名称");
	String SCREEN_LOBECORP_CONDUCTOR_TEAM_MANAGEMENT = register("screen.lobecorp.conductor.team_management", "Team management", "团队管理");
	String SCREEN_LOBECORP_CONDUCTOR_SETTINGS = register("screen.lobecorp.conductor.settings", "Settings", "设置");
	String SCREEN_LOBECORP_CONDUCTOR_SEARCH = register("screen.lobecorp.conductor.search", "Search members", "搜索队员");
	String SCREEN_LOBECORP_CONDUCTOR_CLEAR_SEARCH = register("screen.lobecorp.conductor.clear_search", "Clear search", "清除搜索");
	String SCREEN_LOBECORP_CONDUCTOR_SEARCH_SKILLS = register("screen.lobecorp.conductor.search_skills", "Search skills...", "搜索技能...");
	String SCREEN_LOBECORP_CONDUCTOR_ROSTER_COUNT = register("screen.lobecorp.conductor.roster_count", "Selected %s / %s", "已选 %s / %s");
	String SCREEN_LOBECORP_CONDUCTOR_NO_MATCHING_MEMBERS = register("screen.lobecorp.conductor.no_matching_members", "No matching units", "无匹配生物");
	String SCREEN_LOBECORP_CONDUCTOR_NO_MATCHING_SKILLS = register("screen.lobecorp.conductor.no_matching_skills", "No matching skills", "无匹配技能");
	String SCREEN_LOBECORP_CONDUCTOR_NO_SELECTION = register("screen.lobecorp.conductor.no_selection", "No unit selected", "未选中生物");
	String SCREEN_LOBECORP_CONDUCTOR_NO_SKILLS = register("screen.lobecorp.conductor.no_skills", "This unit has no skills", "该生物没有技能");
	String SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_HEALTH = register("screen.lobecorp.conductor.unknown_health", "Health: unknown", "生命值：未知");
	String SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_ATTRIBUTES = register("screen.lobecorp.conductor.unknown_attributes", "Attributes unavailable", "属性未知");
	String SCREEN_LOBECORP_CONDUCTOR_ATTRIBUTES = register("screen.lobecorp.conductor.attributes", "ATK %s  ARM %s", "攻 %s  防 %s");
	String SCREEN_LOBECORP_CONDUCTOR_DISTANCE = register("screen.lobecorp.conductor.distance", "Distance: %s", "距离：%s");
	String SCREEN_LOBECORP_CONDUCTOR_DIMENSION = register("screen.lobecorp.conductor.dimension", "Dimension: %s", "维度：%s");
	String SCREEN_LOBECORP_CONDUCTOR_KNOWN_HEALTH = register("screen.lobecorp.conductor.known_health", "Known %s/%s", "已知 %s/%s");
	String SCREEN_LOBECORP_CONDUCTOR_AVERAGE_HEALTH = register("screen.lobecorp.conductor.average_health", "Average HP %s%% (%s/%s known)", "平均血量 %s%%（已知 %s/%s）");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_FOCUS = register("screen.lobecorp.conductor.remote_focus", "Unit is not loaded; camera focus unavailable", "单位未加载，无法聚焦视角");
	String SCREEN_LOBECORP_CONDUCTOR_READY = register("screen.lobecorp.conductor.ready", "Ready", "就绪");
	String SCREEN_LOBECORP_CONDUCTOR_REDUCE_MOTION_ON = register("screen.lobecorp.conductor.reduce_motion_on", "Reduced motion: On", "减弱动效：开");
	String SCREEN_LOBECORP_CONDUCTOR_REDUCE_MOTION_OFF = register("screen.lobecorp.conductor.reduce_motion_off", "Reduced motion: Off", "减弱动效：关");
	String SCREEN_LOBECORP_CONDUCTOR_EFFECT = register("screen.lobecorp.conductor.effect", "%s %s (%ss)", "%s %s（%s秒）");
	String SCREEN_LOBECORP_CONDUCTOR_EMPTY_TEAM = register("screen.lobecorp.conductor.empty_team", "No team members", "队伍暂无成员");
	String SCREEN_LOBECORP_CONDUCTOR_REMOTE_SKILLS = register("screen.lobecorp.conductor.remote_skills", "Unit not loaded; use remote orders", "单位未加载，请使用远程指令");
	String SCREEN_LOBECORP_CONDUCTOR_HEALTH_PERCENT = register("screen.lobecorp.conductor.health_percent", "HP %s%%", "生命 %s%%");
	String SCREEN_LOBECORP_CONDUCTOR_OFFLINE = register("screen.lobecorp.conductor.offline", "Offline", "未加载");
	String SCREEN_LOBECORP_CONDUCTOR_DAMAGE = register("screen.lobecorp.conductor.damage", "Damage: %s", "伤害：%s");
	String SCREEN_LOBECORP_CONDUCTOR_DAMAGE_DYNAMIC = register("screen.lobecorp.conductor.damage_dynamic", "Dynamic", "动态");
	String SCREEN_LOBECORP_CONDUCTOR_DAMAGE_NONE = register("screen.lobecorp.conductor.damage_none", "No direct damage", "无直接伤害");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE = register("screen.lobecorp.conductor.type", "Type: %s", "类型：%s");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE_BASIC = register("screen.lobecorp.conductor.type_basic", "Basic attack", "普攻");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE_ATTACK = register("screen.lobecorp.conductor.type_attack", "Attack skill", "攻击技能");
	String SCREEN_LOBECORP_CONDUCTOR_TYPE_SUPPORT = register("screen.lobecorp.conductor.type_support", "Support skill", "辅助技能");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET = register("screen.lobecorp.conductor.target", "Target: %s", "目标：%s");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_SELF = register("screen.lobecorp.conductor.target_self", "Self", "自身");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_ENTITY = register("screen.lobecorp.conductor.target_entity", "Entity", "实体");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_POSITION = register("screen.lobecorp.conductor.target_position", "Position", "坐标");
	String SCREEN_LOBECORP_CONDUCTOR_TARGET_EITHER = register("screen.lobecorp.conductor.target_either", "Entity or position", "实体或坐标");
	String SCREEN_LOBECORP_CONDUCTOR_RANGE = register("screen.lobecorp.conductor.range", "Range: %s", "范围：%s");
	String SCREEN_LOBECORP_CONDUCTOR_RANGE_UNSPECIFIED = register("screen.lobecorp.conductor.range_unspecified", "Dynamic", "动态");
	String SCREEN_LOBECORP_CONDUCTOR_RANGE_SELF = register("screen.lobecorp.conductor.range_self", "Self", "自身");
	String SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_TOTAL = register("screen.lobecorp.conductor.cooldown_total", "Cooldown: %ss", "冷却：%s 秒");
	String SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_REMAINING = register("screen.lobecorp.conductor.cooldown_remaining", "Remaining: %ss", "剩余：%s 秒");
	String SCREEN_LOBECORP_CONDUCTOR_TITLE = register("screen.lobecorp.conductor.title", "Conductor", "指挥家");
	String SCREEN_LOBECORP_CONDUCTOR_WAR = register("screen.lobecorp.conductor.war", "Declare war", "宣战");
	String SCREEN_LOBECORP_CONDUCTOR_X = register("screen.lobecorp.conductor.x", "X", "X");
	String SCREEN_LOBECORP_CONDUCTOR_Y = register("screen.lobecorp.conductor.y", "Y", "Y");
	String SCREEN_LOBECORP_CONDUCTOR_Z = register("screen.lobecorp.conductor.z", "Z", "Z");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_CLEANUP = register("screen.lobecorp.conductor.order_cleanup", "Cleaning", "持续清理");
	String SCREEN_LOBECORP_CONDUCTOR_ORDER_REASSEMBLE = register("screen.lobecorp.conductor.order_reassemble", "Reassembling", "持续重组");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE_ARRIVED = register("screen.lobecorp.conductor.move_arrived", "Formation reached", "已到达队形槽位");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE_UNREACHABLE = register("screen.lobecorp.conductor.move_unreachable", "Destination unreachable", "无法到达目标位置");
	String SCREEN_LOBECORP_CONDUCTOR_MOVE_CANCELED = register("screen.lobecorp.conductor.move_canceled", "Movement canceled", "移动已取消");
	String SCREEN_LOBECORP_CONDUCTOR_CLEANUP_DESCRIPTION = register("screen.lobecorp.conductor.cleanup_description", "Continuously finds and cleans nearby non-Sweeper corpses and dropped items, restoring health. Finds the next target automatically; stops when no reachable targets remain.", "持续寻找并清理附近的非清道夫尸体和掉落物，恢复生命。完成后自动寻找下一目标；无目标或目标均不可达时结束。");
	String SCREEN_LOBECORP_CONDUCTOR_REASSEMBLE_DESCRIPTION = register("screen.lobecorp.conductor.reassemble_description", "Continuously finds nearby Sweeper corpses and reassembles them. Consumes biomass equal to 10% of the target's maximum health and revives it with that health. Finds the next target automatically; stops when biomass is insufficient or no reachable targets remain.", "持续寻找并重组附近符合条件的清道夫尸体，消耗目标最大生命值10%的生物质，并以该生命值复活。完成后自动寻找下一目标；资源不足、无目标或目标均不可达时结束。");
	String SCREEN_LOBECORP_CONDUCTOR_SONIC_DESCRIPTION = register("screen.lobecorp.conductor.sonic_description", "Fires in the chosen direction after charging, hitting only the first valid enemy through walls. Can fire without a target and still enters cooldown.", "前摇后沿指定方向释放穿墙声波，仅命中路径上的首个有效敌人。无需锁定目标，空放也进入冷却。");
	/// 实体技能名称翻译键的前缀。
	String SKILL_TRANSLATION_PREFIX = "entity_skill.";
	/// 指挥家能力名称翻译键的前缀。
	String ABILITY_TRANSLATION_PREFIX = "conductor_ability.";

	static String skillName(Identifier id) {
		return SKILL_TRANSLATION_PREFIX + id.getNamespace() + "." + id.getPath();
	}

	static String abilityName(Identifier id) {
		return ABILITY_TRANSLATION_PREFIX + id.getNamespace() + "." + id.getPath();
	}

	static Component abilityDescription(Identifier ability) {
		if (ability.equals(SweeperSkills.SWEEP.get().id())) {
			return Component.translatable(SCREEN_LOBECORP_CONDUCTOR_CLEANUP_DESCRIPTION);
		}

		if (ability.equals(SweeperSkills.REASSEMBLE.get().id())) {
			return Component.translatable(SCREEN_LOBECORP_CONDUCTOR_REASSEMBLE_DESCRIPTION);
		}

		if (ability.equals(WardenSonicBoomAbility.ID)) {
			return Component.translatable(SCREEN_LOBECORP_CONDUCTOR_SONIC_DESCRIPTION);
		}

		return Component.empty();
	}
}
