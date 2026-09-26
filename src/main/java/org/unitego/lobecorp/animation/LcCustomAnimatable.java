package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animation.AnimationController;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.LcCustomAnimationSettingsSyncPayload;

public interface LcCustomAnimatable extends GeoEntity {
	default void playCustomAnimation(String controllerName, String animationName) {
		triggerAnim(controllerName, animationName);
	}

	default void playCustomAnimation(String controllerName, String animationName,
			LcAnimationTransitionSettings settings) {
		applyOrSyncAnimationSettings(controllerName, settings);
		triggerAnim(controllerName, animationName);
	}

	default void stopCustomAnimation(String controllerName, @Nullable String animationName) {
		stopTriggeredAnim(controllerName, animationName);
	}

	default void stopCustomAnimation(String controllerName, @Nullable String animationName,
			LcAnimationTransitionSettings settings) {
		applyOrSyncAnimationSettings(controllerName, settings);
		stopTriggeredAnim(controllerName, animationName);
	}

	default void applyCustomAnimationSettings(String controllerName, LcAnimationTransitionSettings settings) {
		Entity entity = (Entity)this;
		if (!entity.level().isClientSide()) {
			return;
		}
		AnimationController<?> controller = getAnimatableInstanceCache()
				.getManagerForId(entity.getId())
				.getAnimationControllers()
				.get(controllerName);
		if (controller != null) {
			settings.applyTo(controller);
		}
	}

	private void applyOrSyncAnimationSettings(String controllerName, LcAnimationTransitionSettings settings) {
		Entity entity = (Entity)this;
		if (entity.level().isClientSide()) {
			applyCustomAnimationSettings(controllerName, settings);
		} else {
			LcCustomAnimationSettingsSyncPayload.send(entity, controllerName, settings);
		}
	}
}
