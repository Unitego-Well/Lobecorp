package org.unitego.lobecorp.animation;

import com.geckolib.animatable.SingletonGeoAnimatable;
import com.geckolib.animation.RawAnimation;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.LcAnimationSyncPayload;

public interface LcSingletonGeoAnimatable extends LcAnimatable, SingletonGeoAnimatable {
	default void playAnimation(Entity relatedEntity, long instanceId, String layerName, RawAnimation animation) {
		playAnimation(relatedEntity, instanceId, layerName, animation,
				LcAnimationController.DEFAULT_SPEED, false, -1, null);
	}

	default void playAnimation(Entity relatedEntity, long instanceId, String layerName,
			RawAnimation animation, double speed, boolean reversed) {
		playAnimation(relatedEntity, instanceId, layerName, animation, speed, reversed, -1, null);
	}

	default void playAnimation(Entity relatedEntity, long instanceId, String layerName,
			RawAnimation animation, double speed, boolean reversed, int transitionTicks,
			@Nullable LcTransitionMode transitionMode) {
		if (relatedEntity.level().isClientSide()) {
			LcAnimatable.super.playAnimation(instanceId, layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		} else {
			LcAnimationSyncPayload.sendSingleton(this, relatedEntity, instanceId, layerName, animation,
					speed, reversed, transitionTicks, transitionMode);
		}
	}

	default void stopAnimation(Entity relatedEntity, long instanceId, String layerName, int transitionTicks) {
		if (relatedEntity.level().isClientSide()) {
			LcAnimatable.super.stopAnimation(instanceId, layerName, transitionTicks);
		} else {
			LcAnimationSyncPayload.stopSingleton(this, relatedEntity, instanceId, layerName, transitionTicks);
		}
	}

	default void stopAnimation(Entity relatedEntity, long instanceId, String layerName) {
		stopAnimation(relatedEntity, instanceId, layerName, -1);
	}

	default void triggerAnimation(Entity relatedEntity, long instanceId,
			String controllerName, String animationName) {
		SingletonGeoAnimatable.super.triggerAnim(relatedEntity, instanceId, controllerName, animationName);
	}
}
