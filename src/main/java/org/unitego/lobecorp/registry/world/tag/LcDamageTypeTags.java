package org.unitego.lobecorp.registry.world.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import org.unitego.lobecorp.Lobecorp;

public interface LcDamageTypeTags {
	TagKey<DamageType> PHYSICS = create("physics", "Physical Damage", "物理伤害");
	TagKey<DamageType> SPIRIT = create("spirit", "Spirit Damage", "精神伤害");
	TagKey<DamageType> EROSION = create("erosion", "Erosion Damage", "侵蚀伤害");
	TagKey<DamageType> THE_SOUL = create("the_soul", "Soul Damage", "灵魂伤害");
	TagKey<DamageType> CAN_HURT_ALLIES = TagKey.create(Registries.DAMAGE_TYPE, Lobecorp.id("can_hurt_allies"));

	static void init() {
	}

	private static TagKey<DamageType> create(String name, String enUs, String zhCn) {
		return LcTags.create(Registries.DAMAGE_TYPE, name, enUs, zhCn);
	}
}
