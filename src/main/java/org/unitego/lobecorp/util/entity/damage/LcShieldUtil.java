package org.unitego.lobecorp.util.entity.damage;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.particle.damage.DamageParticles;
import org.unitego.lobecorp.network.tc.damage.DamageParticlePayload;
import org.unitego.lobecorp.config.server.ShieldConfig;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.world.entity.damage.LcShieldState;

/// 服务端拥有护盾结算；效果负责期限，附件负责剩余量及授予时容量。
public class LcShieldUtil {
	public static Holder<MobEffect> effect(LcDamageType type) {
		return switch (type) {
			case PHYSICS -> LcMobEffects.PHYSICS_ABSORPTION_SHIELD;
			case SPIRIT -> LcMobEffects.SPIRIT_ABSORPTION_SHIELD;
			case EROSION -> LcMobEffects.EROSION_ABSORPTION_SHIELD;
			case THE_SOUL -> LcMobEffects.SOUL_ABSORPTION_SHIELD;
		};
	}

	public static void added(MobEffectEvent.Added event) {
		var entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}

		var incoming = event.getEffectInstance();
		var old = event.getOldEffectInstance();
		for (var type : LcDamageType.values()) {
			if (!incoming.getEffect().equals(effect(type))) {
				continue;
			}

			if (old != null && old.getAmplifier() > incoming.getAmplifier()) {
				return;
			}

			if (!ShieldConfig.ENABLE_MULTI_SHIELD.get()) {
				for (var other : LcDamageType.values()) {
					if (other != type) {
						entity.removeEffect(effect(other));
					}
				}
			}

			float capacity = (float) ((incoming.getAmplifier() + 1.0) * ShieldConfig.SHIELD_ADDITIONAL_VALUE_PER_LEVEL.get());
			entity.setData(LcAttachmentTypes.LC_SHIELDS, entity.getData(LcAttachmentTypes.LC_SHIELDS)
					.with(type, new LcShieldState.Pool(capacity, capacity)));
			return;
		}
	}

	public static void removed(LivingEntity entity, Holder<MobEffect> effect) {
		if (entity.level().isClientSide() || !entity.hasData(LcAttachmentTypes.LC_SHIELDS)) {
			return;
		}

		for (var type : LcDamageType.values()) {
			if (effect.equals(effect(type))) {
				entity.setData(LcAttachmentTypes.LC_SHIELDS, entity.getData(LcAttachmentTypes.LC_SHIELDS)
						.with(type, LcShieldState.Pool.EMPTY));
				return;
			}
		}
	}

	/// 补偿死亡直接清空效果等不经过移除事件的路径，不从效果等级恢复已经耗尽的护盾。
	public static void tick(LivingEntity entity) {
		if (!entity.hasData(LcAttachmentTypes.LC_SHIELDS)) {
			return;
		}

		var old = entity.getData(LcAttachmentTypes.LC_SHIELDS);
		var next = old;
		for (var type : LcDamageType.values()) {
			if (!entity.hasEffect(effect(type)) && !old.get(type).equals(LcShieldState.Pool.EMPTY)) {
				next = next.with(type, LcShieldState.Pool.EMPTY);
			}
		}

		if (!next.equals(old)) {
			entity.setData(LcAttachmentTypes.LC_SHIELDS, next);
		}
	}

	public static float absorb(LivingEntity entity, LcDamageType type, float damage) {
		if (entity.level().isClientSide() || damage <= 0 || !entity.hasEffect(effect(type))) {
			return damage;
		}

		var state = entity.getData(LcAttachmentTypes.LC_SHIELDS);
		var pool = state.get(type);
		if (pool.amount() <= 0) {
			return damage;
		}

		float absorbed = Math.min(damage, pool.amount());
		var container = entity.lobecorp$getDamageContainer();
		float left = pool.amount() - absorbed;
		entity.setData(LcAttachmentTypes.LC_SHIELDS, state.with(type, new LcShieldState.Pool(left, pool.maximum())));
		if (container != null) {
			DamageParticles.send(entity, container.getSource(), absorbed, DamageParticlePayload.SHIELD);
		}
		if (left <= 0) {
			entity.removeEffect(effect(type));
			if (ShieldConfig.ENABLE_SHIELD_DAMAGE_IMMUNITY.get()) {
				return 0;
			}
		}

		return damage - absorbed;
	}

	/// 沿用旧项目：没有四色标记的普通伤害依次经过当前持有的护盾。
	public static float absorbUntyped(LivingEntity entity, float damage) {
		float incoming = damage;
		for (var type : LcDamageType.values()) {
			damage = absorb(entity, type, damage);
		}

		entity.lobecorp$setShieldBlocked(incoming > 0 && damage <= 0);
		return damage;
	}
}
