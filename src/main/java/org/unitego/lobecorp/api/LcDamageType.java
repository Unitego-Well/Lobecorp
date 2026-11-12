package org.unitego.lobecorp.api;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.unitego.lobecorp.registry.world.entity.LcAttributes;
import org.unitego.lobecorp.registry.world.tag.LcDamageTypeTags;

/// 四色伤害通道；每个通道绑定标签及防御、承伤属性，枚举顺序用于位掩码。
public enum LcDamageType {
	PHYSICS(LcDamageTypeTags.PHYSICS, LcAttributes.PHYSICS_DEFENSE, LcAttributes.PHYSICS_VULNERABLE),
	SPIRIT(LcDamageTypeTags.SPIRIT, LcAttributes.SPIRIT_DEFENSE, LcAttributes.SPIRIT_VULNERABLE),
	EROSION(LcDamageTypeTags.EROSION, LcAttributes.EROSION_DEFENSE, LcAttributes.EROSION_VULNERABLE),
	THE_SOUL(LcDamageTypeTags.THE_SOUL, LcAttributes.THE_SOUL_DEFENSE, LcAttributes.THE_SOUL_VULNERABLE);

	private final TagKey<DamageType> tag;
	private final Holder<Attribute> defense;
	private final Holder<Attribute> vulnerable;

	LcDamageType(TagKey<DamageType> tag, Holder<Attribute> defense, Holder<Attribute> vulnerable) {
		this.tag = tag;
		this.defense = defense;
		this.vulnerable = vulnerable;
	}

	public TagKey<DamageType> getTag() {
		return tag;
	}

	public Holder<Attribute> getDefense() {
		return defense;
	}

	public Holder<Attribute> getVulnerable() {
		return vulnerable;
	}
}
