package org.unitego.lobecorp.registry.tag;

import net.minecraft.tags.TagKey;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.registry.LcRegistrys;

/// 实体技能的原版注册表标签。
public interface LcEntitySkillTags {
	/// 会改变施法者位置的技能。
	TagKey<IEntitySkill<?>> MOVEMENT = create("movement", "Movement", "位移");
	/// 包含近身攻击效果的技能。
	TagKey<IEntitySkill<?>> MELEE = create("melee", "Melee", "近战");
	/// 不包含远程攻击效果的近战技能。
	TagKey<IEntitySkill<?>> PURE_MELEE = create("pure_melee", "Pure Melee", "纯近战");
	/// 包含远程攻击效果的技能。
	TagKey<IEntitySkill<?>> RANGED = create("ranged", "Ranged", "远程");
	/// 会生成攻击投射物的技能。
	TagKey<IEntitySkill<?>> PROJECTILE = create("projectile", "Projectile", "投射物");
	/// 使用魔法效果的技能。
	TagKey<IEntitySkill<?>> MAGIC = create("magic", "Magic", "魔法");
	/// 使用憎恶皇后通用施法动作的独立法术技能。
	TagKey<IEntitySkill<?>> SPELL = create("spell", "Spell", "法术");
	/// 能够恢复生命的技能。
	TagKey<IEntitySkill<?>> HEALING = create("healing", "Healing", "治疗");
	/// 会直接造成伤害或生成伤害来源的技能。
	TagKey<IEntitySkill<?>> DAMAGE = create("damage", "Damage", "伤害");
	/// 施放期间需要停止水平移动以保持朝向或近战站位的技能。
	TagKey<IEntitySkill<?>> STATIONARY = create("stationary", "Stationary", "定身施放");

	private static TagKey<IEntitySkill<?>> create(String name, String enUs, String zhCn) {
		return LcTags.create(LcRegistrys.ENTITY_SKILL_KEY, name, enUs, zhCn);
	}

	/// 强制初始化本接口中的标签声明。
	static void init() {
	}
}
