package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animation.RawAnimation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.LcAnimationSyncPayload;

public interface LcGeoItem extends LcAnimatable, GeoItem,LcSingletonGeoAnimatable {
	default void playAnimation(Entity relatedEntity, ItemStack stack, String layerName, RawAnimation animation) {
		playAnimation(relatedEntity, stack, layerName, animation, LcAnimationController.DEFAULT_SPEED,
				false, -1, null);
	}

	default void playAnimation(Entity relatedEntity, ItemStack stack, String layerName,
			RawAnimation animation, double speed, boolean reversed) {
		playAnimation(relatedEntity, stack, layerName, animation, speed, reversed, -1, null);
	}

	default void playAnimation(Entity relatedEntity, ItemStack stack, String layerName,
			RawAnimation animation, double speed, boolean reversed, int transitionTicks,
			@Nullable LcTransitionMode transitionMode) {
		long instanceId = GeoItem.getId(stack);
		if (relatedEntity.level().isClientSide()) {
			LcSingletonGeoAnimatable.super.playAnimation(relatedEntity, instanceId, layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		} else if (relatedEntity.level() instanceof ServerLevel level) {
			instanceId = GeoItem.getOrAssignId(stack, level);
			LcAnimationSyncPayload.sendItem(this, relatedEntity, instanceId, layerName, animation,
					speed, reversed, transitionTicks, transitionMode);
		}
	}

	default void stopAnimation(Entity relatedEntity, ItemStack stack, String layerName, int transitionTicks) {
		long instanceId = GeoItem.getId(stack);
		if (relatedEntity.level().isClientSide()) {
			LcSingletonGeoAnimatable.super.stopAnimation(relatedEntity, instanceId, layerName, transitionTicks);
		} else if (relatedEntity.level() instanceof ServerLevel level) {
			instanceId = GeoItem.getOrAssignId(stack, level);
			LcAnimationSyncPayload.stopItem(this, relatedEntity, instanceId, layerName, transitionTicks);
		}
	}

	default void stopAnimation(Entity relatedEntity, ItemStack stack, String layerName) {
		stopAnimation(relatedEntity, stack, layerName, -1);
	}

	default void triggerAnimation(Entity relatedEntity, ItemStack stack,
			String controllerName, String animationName) {
		if (relatedEntity.level().isClientSide()) {
			LcSingletonGeoAnimatable.super.triggerAnimation(
					relatedEntity, GeoItem.getId(stack), controllerName, animationName);
		} else if (relatedEntity.level() instanceof ServerLevel level) {
			LcSingletonGeoAnimatable.super.triggerAnimation(
					relatedEntity, GeoItem.getOrAssignId(stack, level), controllerName, animationName);
		}
	}
}
