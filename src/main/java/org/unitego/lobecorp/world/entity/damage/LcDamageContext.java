package org.unitego.lobecorp.world.entity.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

import java.util.EnumSet;
import java.util.Set;

/// 单次命中的运行态；不写入伤害来源、不持久化，并在嵌套受击返回后恢复外层上下文。
public class LcDamageContext {
	private Set<LcDamageType> types;
	private float shieldReduction;
	private float physicsDamage;
	private float erosionDamage;
	private float spiritDamage;
	private float soulDamage;
	private double healthRecovery;
	private float pendingHealing;
	private boolean prepared;
	private boolean accepted;

	private LcDamageContext(Set<LcDamageType> types) {
		this.types = types;
	}

	@Nullable
	public static LcDamageContext create(DamageSource source) {
		var types = EnumSet.noneOf(LcDamageType.class);
		for (LcDamageType type : LcDamageType.values()) {
			if (source.is(type.getTag())) {
				types.add(type);
			}
		}

		return types.isEmpty() ? null : new LcDamageContext(types);
	}

	public boolean hasIndependentResources() {
		return types.contains(LcDamageType.SPIRIT) || types.contains(LcDamageType.THE_SOUL);
	}

	public boolean hasHealthDamage() {
		return types.contains(LcDamageType.PHYSICS) || types.contains(LcDamageType.EROSION);
	}

	public void setShieldReduction(float reduction) {
		shieldReduction = reduction;
	}

	public boolean isPrepared() {
		return prepared;
	}

	public float getPhysicsDamage() {
		return physicsDamage;
	}

	public float getErosionDamage() {
		return erosionDamage;
	}

	public float prepare(LivingEntity entity, float amount) {
		float healthAmount = Math.max(0.0F, amount - shieldReduction);
		float physics = calculate(entity, LcDamageType.PHYSICS, healthAmount);
		float erosion = calculate(entity, LcDamageType.EROSION, healthAmount);
		physicsDamage = Math.max(0.0F, physics);
		erosionDamage = Math.max(0.0F, erosion);
		healthRecovery = Math.max(0.0, -(double) physics) + Math.max(0.0, -(double) erosion);
		spiritDamage = calculate(entity, LcDamageType.SPIRIT, amount);
		soulDamage = calculate(entity, LcDamageType.THE_SOUL, amount);
		prepared = true;
		return finiteDamage((double) physicsDamage + erosionDamage);
	}

	/// 零系数直接免疫；只有负系数能够吸收，防御造成的负结果被截断为零。
	private float calculate(LivingEntity entity, LcDamageType type, float amount) {
		if (!types.contains(type) || amount <= 0.0F) {
			return 0.0F;
		}

		double coefficient = entity.getAttributeValue(type.getVulnerable());
		if (coefficient == 0.0) {
			return 0.0F;
		}

		double scaled = amount * coefficient;
		if (coefficient < 0.0) {
			return -finiteDamage(-scaled);
		}

		return finiteDamage(Math.max(0.0, scaled - entity.getAttributeValue(type.getDefense())));
	}

	private static float finiteDamage(double amount) {
		return (float) Math.min(amount, Float.MAX_VALUE);
	}

	/// 在原版护甲、保护、抗性及 Pre 阶段完成后合并回血，不向 DamageContainer 写入负伤害。
	public float combineHealth(float damage) {
		double remaining = (double) damage - healthRecovery;
		pendingHealing = finiteDamage(Math.max(0.0, -remaining));
		accepted = true;
		return finiteDamage(Math.max(0.0, remaining));
	}

	public void finish(LivingEntity entity, DamageSource source) {
		prepared = false;
		if (!accepted) {
			return;
		}

		if (pendingHealing > 0.0F) {
			entity.heal(pendingHealing);
		}

		if (spiritDamage != 0.0F) {
			LcDamageUtil.applyResourceDamage(entity, LcDamageType.SPIRIT, spiritDamage, source);
		}

		if (soulDamage != 0.0F) {
			LcDamageUtil.applyResourceDamage(entity, LcDamageType.THE_SOUL, soulDamage, source);
		}
	}
}
