package org.unitego.lobecorp.client.conductor.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.conductor.ability.WardenSonicBoomAbility;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;
import org.unitego.lobecorp.util.TranslationKeys;

/// 指挥家界面的集中翻译键及技能名称解析。
public interface ConductorTexts {
	/// 清理命令的翻译键。
	String ORDER_CLEANUP = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_CLEANUP;
	/// 重组命令的翻译键。
	String ORDER_REASSEMBLE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_REASSEMBLE;
	/// 移动到达提示的翻译键。
	String MOVE_ARRIVED = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE_ARRIVED;
	/// 移动不可达提示的翻译键。
	String MOVE_UNREACHABLE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE_UNREACHABLE;
	/// 移动取消提示的翻译键。
	String MOVE_CANCELED = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE_CANCELED;
	/// 清理技能说明的翻译键。
	String CLEANUP_DESCRIPTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_CLEANUP_DESCRIPTION;
	/// 重组技能说明的翻译键。
	String REASSEMBLE_DESCRIPTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REASSEMBLE_DESCRIPTION;
	/// 音爆能力说明的翻译键。
	String SONIC_DESCRIPTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SONIC_DESCRIPTION;
	/// 指挥家按键分类的翻译键。
	String CATEGORY = TranslationKeys.KEY_CATEGORY_LOBECORP_CONDUCTOR;
	/// 切换指挥视角按键的翻译键。
	String TOGGLE = TranslationKeys.KEY_LOBECORP_CONDUCTOR_TOGGLE;
	/// 打开管理面板按键的翻译键。
	String MENU = TranslationKeys.KEY_LOBECORP_CONDUCTOR_MENU;
	/// 跟随成员按键的翻译键。
	String FOLLOW = TranslationKeys.KEY_LOBECORP_CONDUCTOR_FOLLOW;
	/// 向左旋转相机按键的翻译键。
	String ROTATE_LEFT = TranslationKeys.KEY_LOBECORP_CONDUCTOR_ROTATE_LEFT;
	/// 向右旋转相机按键的翻译键。
	String ROTATE_RIGHT = TranslationKeys.KEY_LOBECORP_CONDUCTOR_ROTATE_RIGHT;
	/// 指挥家面板标题的翻译键。
	String TITLE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TITLE;
	/// 设置按钮的翻译键。
	String SETTINGS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SETTINGS;
	/// 成员搜索的翻译键。
	String SEARCH = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SEARCH;
	/// 清除搜索的翻译键。
	String CLEAR_SEARCH = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_CLEAR_SEARCH;
	/// 技能搜索的翻译键。
	String SEARCH_SKILLS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SEARCH_SKILLS;
	/// 队伍成员数量的翻译键。
	String ROSTER_COUNT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ROSTER_COUNT;
	/// 无匹配成员提示的翻译键。
	String NO_MATCHING_MEMBERS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_MATCHING_MEMBERS;
	/// 无匹配技能提示的翻译键。
	String NO_MATCHING_SKILLS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_MATCHING_SKILLS;
	/// 未选择成员提示的翻译键。
	String NO_SELECTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_SELECTION;
	/// 无可用技能提示的翻译键。
	String NO_SKILLS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_SKILLS;
	/// 未知生命值的翻译键。
	String UNKNOWN_HEALTH = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_HEALTH;
	/// 未知属性的翻译键。
	String UNKNOWN_ATTRIBUTES = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_ATTRIBUTES;
	/// 属性标题的翻译键。
	String ATTRIBUTES = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTRIBUTES;
	/// 距离标题的翻译键。
	String DISTANCE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DISTANCE;
	/// 维度标题的翻译键。
	String DIMENSION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DIMENSION;
	/// 已知生命值的翻译键。
	String KNOWN_HEALTH = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_KNOWN_HEALTH;
	/// 平均生命值的翻译键。
	String AVERAGE_HEALTH = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_AVERAGE_HEALTH;
	/// 远程关注成员提示的翻译键。
	String REMOTE_FOCUS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_FOCUS;
	/// 能力就绪状态的翻译键。
	String READY = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_READY;
	/// 减弱动态效果开启状态的翻译键。
	String REDUCE_MOTION_ON = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REDUCE_MOTION_ON;
	/// 减弱动态效果关闭状态的翻译键。
	String REDUCE_MOTION_OFF = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REDUCE_MOTION_OFF;
	/// 状态效果标题的翻译键。
	String EFFECT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_EFFECT;
	/// 空队伍提示的翻译键。
	String EMPTY_TEAM = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_EMPTY_TEAM;
	/// 远程技能标题的翻译键。
	String REMOTE_SKILLS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_SKILLS;
	/// 生命值百分比的翻译键。
	String HEALTH_PERCENT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_HEALTH_PERCENT;
	/// 离线成员状态的翻译键。
	String OFFLINE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_OFFLINE;
	/// 伤害标题的翻译键。
	String DAMAGE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DAMAGE;
	/// 动态伤害说明的翻译键。
	String DAMAGE_DYNAMIC = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DAMAGE_DYNAMIC;
	/// 无伤害说明的翻译键。
	String DAMAGE_NONE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DAMAGE_NONE;
	/// 技能类型标题的翻译键。
	String TYPE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE;
	/// 普通攻击类型的翻译键。
	String TYPE_BASIC = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE_BASIC;
	/// 攻击技能类型的翻译键。
	String TYPE_ATTACK = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE_ATTACK;
	/// 辅助技能类型的翻译键。
	String TYPE_SUPPORT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE_SUPPORT;
	/// 目标类型标题的翻译键。
	String TARGET = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET;
	/// 自身目标类型的翻译键。
	String TARGET_SELF = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_SELF;
	/// 实体目标类型的翻译键。
	String TARGET_ENTITY = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_ENTITY;
	/// 位置目标类型的翻译键。
	String TARGET_POSITION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_POSITION;
	/// 实体或位置目标类型的翻译键。
	String TARGET_EITHER = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_EITHER;
	/// 范围标题的翻译键。
	String RANGE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RANGE;
	/// 未限定范围的翻译键。
	String RANGE_UNSPECIFIED = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RANGE_UNSPECIFIED;
	/// 自身范围的翻译键。
	String RANGE_SELF = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RANGE_SELF;
	/// 总冷却时间的翻译键。
	String COOLDOWN_TOTAL = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_TOTAL;
	/// 剩余冷却时间的翻译键。
	String COOLDOWN_REMAINING = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_REMAINING;
	/// 队伍标题的翻译键。
	String TEAM = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TEAM;
	/// 队伍管理标题的翻译键。
	String TEAM_MANAGEMENT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TEAM_MANAGEMENT;
	/// 操作控制标题的翻译键。
	String CONTROLS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_CONTROLS;
	/// 远程命令标题的翻译键。
	String REMOTE_ORDERS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_ORDERS;
	/// 创建队伍按钮的翻译键。
	String CREATE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_CREATE;
	/// 分配成员按钮的翻译键。
	String ASSIGN = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ASSIGN;
	/// 释放成员按钮的翻译键。
	String RELEASE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RELEASE;
	/// 分散编队模式的翻译键。
	String FORMATION_SCATTERED = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FORMATION_SCATTERED;
	/// 常规编队模式的翻译键。
	String FORMATION_REGULAR = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FORMATION_REGULAR;
	/// 均匀编队模式的翻译键。
	String FORMATION_UNIFORM = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FORMATION_UNIFORM;
	/// 分散编队说明的翻译键。
	String FORMATION_SCATTERED_DESCRIPTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FORMATION_SCATTERED_DESCRIPTION;
	/// 常规编队说明的翻译键。
	String FORMATION_REGULAR_DESCRIPTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FORMATION_REGULAR_DESCRIPTION;
	/// 均匀编队说明的翻译键。
	String FORMATION_UNIFORM_DESCRIPTION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FORMATION_UNIFORM_DESCRIPTION;
	/// 移动命令的翻译键。
	String MOVE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE;
	/// 攻击命令的翻译键。
	String ATTACK = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTACK;
	/// 停止命令的翻译键。
	String STOP = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_STOP;
	/// 自动攻击模式的翻译键。
	String ATTACK_AI = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTACK_AI;
	/// 普通攻击模式的翻译键。
	String ATTACK_BASIC = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTACK_BASIC;
	/// 攻击模式标题的翻译键。
	String ATTACK_MODE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTACK_MODE;
	/// 战斗行为模式标题的翻译键。
	String BEHAVIOR_MODE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_MODE;
	/// 战斗行为当前值的翻译键。
	String BEHAVIOR_VALUE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_VALUE;
	/// 主动战斗行为的翻译键。
	String BEHAVIOR_ACTIVE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_ACTIVE;
	/// 被动战斗行为的翻译键。
	String BEHAVIOR_PASSIVE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_PASSIVE;
	/// 中立战斗行为的翻译键。
	String BEHAVIOR_NEUTRAL = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_NEUTRAL;
	/// 活动模式标题的翻译键。
	String ACTIVITY_MODE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_MODE;
	/// 活动模式当前值的翻译键。
	String ACTIVITY_VALUE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_VALUE;
	/// 巡逻活动模式的翻译键。
	String BEHAVIOR_PATROL = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_PATROL;
	/// 闲置活动模式的翻译键。
	String BEHAVIOR_IDLE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_IDLE;
	/// 守卫活动模式的翻译键。
	String BEHAVIOR_GUARD = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_GUARD;
	/// 待命活动模式的翻译键。
	String BEHAVIOR_STANDBY = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_STANDBY;
	/// 技能冷却不可用提示的翻译键。
	String SKILL_UNAVAILABLE_COOLDOWN = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_COOLDOWN;
	/// 技能施放中不可用提示的翻译键。
	String SKILL_UNAVAILABLE_CASTING = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CASTING;
	/// 技能条件不满足提示的翻译键。
	String SKILL_UNAVAILABLE_CONDITION = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CONDITION;
	/// 技能目标无效提示的翻译键。
	String SKILL_UNAVAILABLE_TARGET = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_TARGET;
	/// 技能目标超出范围提示的翻译键。
	String SKILL_OUT_OF_RANGE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_OUT_OF_RANGE;
	/// 技能冷却显示的翻译键。
	String SKILL_COOLDOWN = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_COOLDOWN;
	/// 远程移动命令的翻译键。
	String REMOTE_MOVE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_MOVE;
	/// 远程攻击命令的翻译键。
	String REMOTE_ATTACK = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_ATTACK;
	/// 远程施放命令的翻译键。
	String REMOTE_CAST = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_CAST;
	/// 目标 UUID 输入的翻译键。
	String TARGET_UUID = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_UUID;
	/// 技能标识输入的翻译键。
	String SKILL_ID = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_ID;
	/// 宣战操作的翻译键。
	String WAR = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_WAR;
	/// 和平操作的翻译键。
	String PEACE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_PEACE;
	/// 强制加载开启状态的翻译键。
	String LOAD_ON = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_LOAD_ON;
	/// 强制加载关闭状态的翻译键。
	String LOAD_OFF = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_LOAD_OFF;
	/// 完整控制模式的翻译键。
	String FULL = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_FULL;
	/// 命令控制模式的翻译键。
	String COMMAND = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_COMMAND;
	/// 柔性控制模式的翻译键。
	String SOFT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SOFT;
	/// 已选择成员标题的翻译键。
	String SELECTED = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SELECTED;
	/// 技能标题的翻译键。
	String SKILLS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILLS;
	/// 关闭按钮的翻译键。
	String CLOSE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_CLOSE;
	/// 下一页按钮的翻译键。
	String NEXT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NEXT;
	/// 上一页按钮的翻译键。
	String PREVIOUS = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_PREVIOUS;
	/// 敌对队伍标题的翻译键。
	String ENEMY_TEAM = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ENEMY_TEAM;
	/// X 坐标输入的翻译键。
	String X = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_X;
	/// Y 坐标输入的翻译键。
	String Y = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_Y;
	/// Z 坐标输入的翻译键。
	String Z = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_Z;
	/// 队伍颜色输入的翻译键。
	String COLOR = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_COLOR;
	/// 应用队伍颜色按钮的翻译键。
	String APPLY_COLOR = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_APPLY_COLOR;
	/// 生命值标题的翻译键。
	String HEALTH = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_HEALTH;
	/// 无命令状态的翻译键。
	String ORDER_NONE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_NONE;
	/// 移动命令状态的翻译键。
	String ORDER_MOVE = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_MOVE;
	/// 实体攻击命令状态的翻译键。
	String ORDER_ATTACK = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_ATTACK;
	/// 位置攻击命令状态的翻译键。
	String ORDER_ATTACK_POINT = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_ATTACK_POINT;
	/// 返回命令状态的翻译键。
	String ORDER_RETURN = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_RETURN;
	/// 停止命令状态的翻译键。
	String ORDER_STOP = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ORDER_STOP;
	/// 未分配队伍提示的翻译键。
	String NO_TEAM = TranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_TEAM;
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
			return Component.translatable(CLEANUP_DESCRIPTION);
		}

		if (ability.equals(SweeperSkills.REASSEMBLE.get().id())) {
			return Component.translatable(REASSEMBLE_DESCRIPTION);
		}

		if (ability.equals(WardenSonicBoomAbility.ID)) {
			return Component.translatable(SONIC_DESCRIPTION);
		}

		return Component.empty();
	}
}
