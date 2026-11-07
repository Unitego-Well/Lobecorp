package org.unitego.lobecorp.api;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;
import org.unitego.lobecorp.world.entity.damage.LcDamageContext;

/// 通过 Interface Injection 暴露生物资源和当前伤害上下文；运行态由 LivingEntityMixin 实现。
public interface ILivingEntityExpand {
	default boolean lobecorp$isShieldBlocked() {
		throw new NoMixinException();
	}

	default void lobecorp$setShieldBlocked(boolean blocked) {
		throw new NoMixinException();
	}

	default boolean lobecorp$isCapturingShieldBlock() {
		throw new NoMixinException();
	}

	default void lobecorp$setCapturingShieldBlock(boolean capture) {
		throw new NoMixinException();
	}
	default float lobecorp$getRationality() {
		return LcDamageUtil.getRationality((LivingEntity) this);
	}

	default float lobecorp$getTheSoul() {
		return LcDamageUtil.getTheSoul((LivingEntity) this);
	}

	default float lobecorp$getMaxRationality() {
		return LcDamageUtil.getMaxRationality((LivingEntity) this);
	}

	default float lobecorp$getMaxTheSoul() {
		return LcDamageUtil.getMaxTheSoul((LivingEntity) this);
	}

	default void lobecorp$setRationality(float value) {
		LcDamageUtil.setRationality((LivingEntity) this, value);
	}

	default void lobecorp$setTheSoul(float value) {
		LcDamageUtil.setTheSoul((LivingEntity) this, value);
	}

	default void lobecorp$healRationality(float amount) {
		lobecorp$setRationality(lobecorp$getRationality() + Math.max(0.0F, amount));
	}

	default void lobecorp$healTheSoul(float amount) {
		lobecorp$setTheSoul(lobecorp$getTheSoul() + Math.max(0.0F, amount));
	}

	@Nullable
	default LcDamageContext lobecorp$getDamageContext() {
		throw new NoMixinException();
	}

	default void lobecorp$setDamageContext(@Nullable LcDamageContext context) {
		throw new NoMixinException();
	}

	@Nullable
	default DamageContainer lobecorp$getDamageContainer() {
		throw new NoMixinException();
	}
}
