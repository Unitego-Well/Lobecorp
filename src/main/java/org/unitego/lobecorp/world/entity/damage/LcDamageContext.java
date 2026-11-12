package org.unitego.lobecorp.world.entity.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.network.tc.damage.DamageParticlePayload;
import org.unitego.lobecorp.network.tc.damage.PlayerDamageFilterPayload;
import org.unitego.lobecorp.particle.damage.DamageParticles;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;
import org.unitego.lobecorp.util.entity.damage.LcShieldUtil;

import java.util.EnumSet;
import java.util.Set;

/// 单次命中的运行态；不写入伤害来源、不持久化，并在嵌套受击返回后恢复外层上下文。
public class LcDamageContext {
	private final Set<LcDamageType> types;
	private float shieldReduction;
	private float physicsDamage;
	private float erosionDamage;
	private float spiritDamage;
	private float soulDamage;
	private double healthRecovery;
	private float pendingHealing;
	private boolean prepared;
	private boolean accepted;
	private int filterChannels;
	private boolean shieldAbsorbed;

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

	private static float finiteDamage(double amount) {
		return (float) Math.min(amount, Float.MAX_VALUE);
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

	public void setPhysicsAfterArmor(float remaining) {
		physicsDamage = remaining;
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

	/// 在原版护甲、保护、抗性及 Pre 阶段完成后合并回血，不向 DamageContainer 写入负伤害。
	public float combineHealth(LivingEntity entity, float damage) {
		double remaining = (double) damage - healthRecovery;
		pendingHealing = finiteDamage(Math.max(0.0, -remaining));
		accepted = true;
		float positive = finiteDamage(Math.max(0.0, remaining));
		double total = (double) physicsDamage + erosionDamage;
		if (positive <= 0 || total <= 0) {
			return positive;
		}

		// 共同减伤与 Pre 处理后按护甲后的通道权重分摊，回血先抵消，不消耗护盾。
		float physics = finiteDamage(positive * (physicsDamage / total));
		float erosion = positive - physics;
		physics = LcShieldUtil.absorb(entity, LcDamageType.PHYSICS, physics);
		erosion = LcShieldUtil.absorb(entity, LcDamageType.EROSION, erosion);
		shieldAbsorbed |= physics + erosion < positive;
		if (physics > 0) {
			filterChannels |= 1 << LcDamageType.PHYSICS.ordinal();
		}

		if (erosion > 0) {
			filterChannels |= 1 << LcDamageType.EROSION.ordinal();
		}

		return finiteDamage((double) physics + erosion);
	}

	public void finish(LivingEntity entity, DamageSource source) {
		prepared = false;
		if (!accepted) {
			return;
		}

		if (pendingHealing > 0.0F) {
			float before = entity.getHealth();
			entity.heal(pendingHealing);
			DamageParticles.send(entity, source, entity.getHealth() - before, DamageParticlePayload.RECOVERY);
		}

		if (spiritDamage != 0.0F) {
			float beforeShield = spiritDamage;
			spiritDamage = LcShieldUtil.absorb(entity, LcDamageType.SPIRIT, spiritDamage);
			shieldAbsorbed |= beforeShield > 0 && spiritDamage < beforeShield;
			LcDamageUtil.applyResourceDamage(entity, LcDamageType.SPIRIT, spiritDamage, source);
			if (spiritDamage > 0) {
				filterChannels |= 1 << LcDamageType.SPIRIT.ordinal();
			}
		}

		if (soulDamage != 0.0F) {
			float beforeShield = soulDamage;
			soulDamage = LcShieldUtil.absorb(entity, LcDamageType.THE_SOUL, soulDamage);
			shieldAbsorbed |= beforeShield > 0 && soulDamage < beforeShield;
			LcDamageUtil.applyResourceDamage(entity, LcDamageType.THE_SOUL, soulDamage, source);
			if (soulDamage > 0) {
				filterChannels |= 1 << LcDamageType.THE_SOUL.ordinal();
			}
		}

		entity.lobecorp$setShieldBlocked(shieldAbsorbed && filterChannels == 0);
		PlayerDamageFilterPayload.send(entity, filterChannels);
	}
}
