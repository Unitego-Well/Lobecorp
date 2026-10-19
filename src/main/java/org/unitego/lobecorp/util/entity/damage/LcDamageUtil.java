package org.unitego.lobecorp.util.entity.damage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.ILivingEntityExpand;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.event.damage.LivingResourceChangeEvent;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.world.entity.damage.LcDamageContext;
import org.unitego.lobecorp.world.entity.damage.LcResourceState;

public class LcDamageUtil {
	public static LcResourceState fullResources(IAttachmentHolder holder) {
		if (holder instanceof LivingEntity entity) {
			float rationality = getMaxRationality(entity);
			float soul = getMaxTheSoul(entity);
			return new LcResourceState(rationality, soul, rationality, soul);
		}

		return new LcResourceState(0.0F, 0.0F, 0.0F, 0.0F);
	}

	public static float getMaxRationality(LivingEntity entity) {
		return (float) entity.getAttributeValue(LcAttributes.MAX_RATIONALITY);
	}

	public static float getMaxTheSoul(LivingEntity entity) {
		return (float) entity.getAttributeValue(LcAttributes.MAX_THE_SOUL);
	}

	public static float getRationality(LivingEntity entity) {
		refreshResources(entity);
		return entity.getData(LcAttachmentTypes.LC_RESOURCES).rationality();
	}

	public static float getTheSoul(LivingEntity entity) {
		refreshResources(entity);
		return entity.getData(LcAttachmentTypes.LC_RESOURCES).theSoul();
	}

	public static void setRationality(LivingEntity entity, float value) {
		setResource(entity, LcDamageType.SPIRIT, value, null);
	}

	public static void setTheSoul(LivingEntity entity, float value) {
		setResource(entity, LcDamageType.THE_SOUL, value, null);
	}

	public static void applyResourceDamage(LivingEntity entity, LcDamageType type, float damage, DamageSource source) {
		float current = type == LcDamageType.SPIRIT ? getRationality(entity) : getTheSoul(entity);
		setResource(entity, type, current - damage, source);
	}

	public static void refreshResources(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return;
		}

		if (!entity.hasData(LcAttachmentTypes.LC_RESOURCES)) {
			entity.setData(LcAttachmentTypes.LC_RESOURCES, fullResources(entity));
			return;
		}

		LcResourceState old = entity.getData(LcAttachmentTypes.LC_RESOURCES);
		float rationalityMax = getMaxRationality(entity);
		float soulMax = getMaxTheSoul(entity);
		if (old.maxRationality() == rationalityMax && old.maxTheSoul() == soulMax) {
			return;
		}

		var updated = new LcResourceState(
				rescale(old.rationality(), old.maxRationality(), rationalityMax),
				rescale(old.theSoul(), old.maxTheSoul(), soulMax), rationalityMax, soulMax);
		entity.setData(LcAttachmentTypes.LC_RESOURCES, updated);
		notifyChange(entity, LcDamageType.SPIRIT, old.rationality(), updated.rationality(), null);
		notifyChange(entity, LcDamageType.THE_SOUL, old.theSoul(), updated.theSoul(), null);
	}

	/// 旧上限为零时没有可保留的比例，重新获得上限后仍为空；资源始终限制在零与上限之间。
	private static float rescale(float current, float previousMax, float nextMax) {
		return previousMax > 0.0F ? Math.clamp(current / previousMax, 0.0F, 1.0F) * nextMax : 0.0F;
	}

	private static void setResource(LivingEntity entity, LcDamageType type, float value, @Nullable DamageSource source) {
		if (entity.level().isClientSide() || !Float.isFinite(value)) {
			return;
		}

		refreshResources(entity);
		LcResourceState old = entity.getData(LcAttachmentTypes.LC_RESOURCES);
		boolean spirit = type == LcDamageType.SPIRIT;
		float maximum = spirit ? old.maxRationality() : old.maxTheSoul();
		float updatedValue = Math.clamp(value, 0.0F, maximum);
		float oldValue = spirit ? old.rationality() : old.theSoul();
		if (updatedValue == oldValue) {
			return;
		}

		var updated = new LcResourceState(spirit ? updatedValue : old.rationality(),
				spirit ? old.theSoul() : updatedValue, old.maxRationality(), old.maxTheSoul());
		entity.setData(LcAttachmentTypes.LC_RESOURCES, updated);
		notifyChange(entity, type, oldValue, updatedValue, source);
	}

	private static void notifyChange(LivingEntity entity, LcDamageType type, float oldValue, float newValue,
	                                 @Nullable DamageSource source) {
		if (oldValue != newValue) {
			NeoForge.EVENT_BUS.post(new LivingResourceChangeEvent(entity, type, oldValue, newValue, source));
		}
	}

	/// 沿用原版受击流程，每次接受的命中只进入一次生命伤害管线。
	public static void actuallyHurt(LivingEntity entity, ServerLevel level, DamageSource source, float amount,
	                                Operation<Void> original) {
		ILivingEntityExpand access = entity;
		LcDamageContext context = access.lobecorp$getDamageContext();
		DamageContainer container = access.lobecorp$getDamageContainer();
		if (context == null || container == null) {
			original.call(level, source, amount);
			return;
		}

		float healthDamage = context.prepare(entity, amount);
		container.setNewDamage(healthDamage);
		original.call(level, source, healthDamage);
		context.finish(entity, source);
	}

	public static float afterDamagePre(LivingEntity entity, DamageContainer container, float damage) {
		LcDamageContext context = entity.lobecorp$getDamageContext();
		if (context == null || !context.isPrepared()) {
			return damage;
		}

		float remaining = context.combineHealth(damage);
		container.setNewDamage(remaining);
		return remaining;
	}
}
