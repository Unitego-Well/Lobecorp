package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.SingletonGeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import org.jspecify.annotations.Nullable;

public interface LcAnimatable extends GeoAnimatable {
	void registerLcAnimationLayers(LcAnimationLayerRegistrar registrar);

	static void registerSyncedAnimatable(LcAnimatable animatable) {
		if (animatable instanceof SingletonGeoAnimatable singleton) {
			SingletonGeoAnimatable.registerSyncedAnimatable(singleton);
		}
	}

	default void playAnimation(long instanceId, String layerName, RawAnimation animation) {
		playAnimation(instanceId, layerName, animation, LcAnimationController.DEFAULT_SPEED,
				false, -1, null);
	}

	default void playAnimation(long instanceId, String layerName, RawAnimation animation,
			double speed, boolean reversed) {
		playAnimation(instanceId, layerName, animation, speed, reversed, -1, null);
	}

	default void playAnimation(long instanceId, String layerName, RawAnimation animation,
			double speed, boolean reversed, int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		AnimatableManager<?> manager = getAnimatableInstanceCache().getManagerForId(instanceId);
		AnimationController<?> controller = manager.getAnimationControllers().get(LcAnimationController.CONTROLLER_NAME);
		if (controller instanceof LcAnimationController<?> layeredController) {
			if (transitionTicks < 0) {
				layeredController.play(layerName, animation, speed, reversed, (Integer)null, transitionMode);
			} else {
				layeredController.play(layerName, animation, speed, reversed, transitionTicks, transitionMode);
			}
		}
	}

	default void stopAnimation(long instanceId, String layerName, int transitionTicks) {
		AnimatableManager<?> manager = getAnimatableInstanceCache().getManagerForId(instanceId);
		AnimationController<?> controller = manager.getAnimationControllers().get(LcAnimationController.CONTROLLER_NAME);
		if (controller instanceof LcAnimationController<?> layeredController) {
			if (transitionTicks < 0) {
				layeredController.end(layerName);
			} else {
				layeredController.end(layerName, transitionTicks);
			}
		}
	}

	default void stopAnimation(long instanceId, String layerName) {
		stopAnimation(instanceId, layerName, -1);
	}

	default void triggerAnimation(long instanceId, String controllerName, String animationName) {
		getAnimatableInstanceCache().getManagerForId(instanceId)
				.tryTriggerAnimation(controllerName, animationName);
	}
}
