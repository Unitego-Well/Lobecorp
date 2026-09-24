package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animation.RawAnimation;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.LcAnimationSyncPayload;

public interface LcGeoEntity extends LcAnimatable, GeoEntity {
	default void playAnimation(String layerName, RawAnimation animation) {
		playAnimation(layerName, animation, LcAnimationController.DEFAULT_SPEED, false, -1, null);
	}

	default void playAnimation(String layerName, RawAnimation animation, double speed, boolean reversed) {
		playAnimation(layerName, animation, speed, reversed, -1, null);
	}

	default void playAnimation(String layerName, RawAnimation animation, double speed,
			boolean reversed, int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		Entity entity = (Entity)this;
		if (entity.level().isClientSide()) {
			LcAnimatable.super.playAnimation(entity.getId(), layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		} else {
			LcAnimationSyncPayload.sendEntity(entity, layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		}
	}

	default void stopAnimation(String layerName, int transitionTicks) {
		Entity entity = (Entity)this;
		if (entity.level().isClientSide()) {
			LcAnimatable.super.stopAnimation(entity.getId(), layerName, transitionTicks);
		} else {
			LcAnimationSyncPayload.stopEntity(entity, layerName, transitionTicks);
		}
	}

	default void stopAnimation(String layerName) {
		stopAnimation(layerName, -1);
	}

	default void triggerAnimation(String controllerName, String animationName) {
		GeoEntity.super.triggerAnim(controllerName, animationName);
	}

	default void stopTriggeredAnimation(String controllerName, @Nullable String animationName) {
		GeoEntity.super.stopTriggeredAnim(controllerName, animationName);
	}
}
