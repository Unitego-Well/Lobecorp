package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animation.RawAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.LcAnimationSyncPayload;

public interface LcGeoBlockEntity extends LcAnimatable, GeoBlockEntity {
	default void playAnimation(String layerName, RawAnimation animation) {
		playAnimation(layerName, animation, LcAnimationController.DEFAULT_SPEED, false, -1, null);
	}

	default void playAnimation(String layerName, RawAnimation animation, double speed, boolean reversed) {
		playAnimation(layerName, animation, speed, reversed, -1, null);
	}

	default void playAnimation(String layerName, RawAnimation animation, double speed,
			boolean reversed, int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		BlockEntity blockEntity = (BlockEntity)this;
		Level level = blockEntity.getLevel();
		if (level == null) {
			return;
		}
		if (level.isClientSide()) {
			LcAnimatable.super.playAnimation(0L, layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		} else {
			LcAnimationSyncPayload.sendBlockEntity(blockEntity, layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		}
	}

	default void stopAnimation(String layerName, int transitionTicks) {
		BlockEntity blockEntity = (BlockEntity)this;
		Level level = blockEntity.getLevel();
		if (level == null) {
			return;
		}
		if (level.isClientSide()) {
			LcAnimatable.super.stopAnimation(0L, layerName, transitionTicks);
		} else {
			LcAnimationSyncPayload.stopBlockEntity(blockEntity, layerName, transitionTicks);
		}
	}

	default void stopAnimation(String layerName) {
		stopAnimation(layerName, -1);
	}

	default void triggerAnimation(String controllerName, String animationName) {
		GeoBlockEntity.super.triggerAnim(controllerName, animationName);
	}
}
