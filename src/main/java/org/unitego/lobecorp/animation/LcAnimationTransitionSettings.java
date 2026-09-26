package org.unitego.lobecorp.animation;

import com.geckolib.animation.AnimationController;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.unitego.lobecorp.util.EnumStreamCodecUtil;

public record LcAnimationTransitionSettings(
		int fadeInTicks,
		int fadeOutTicks,
		LcTransitionMode fadeInTransitionMode,
		LcTransitionMode fadeOutTransitionMode,
		LcRotationTransitionMode fadeInRotationTransitionMode,
		LcRotationTransitionMode fadeOutRotationTransitionMode,
		LcTransitionMode animationTransitionMode,
		LcRotationTransitionMode rotationTransitionMode,
		LcControllerBlendType blendType
) {
	private static final StreamCodec<ByteBuf, LcTransitionMode> TRANSITION_MODE_CODEC =
			EnumStreamCodecUtil.create(LcTransitionMode.class);
	private static final StreamCodec<ByteBuf, LcRotationTransitionMode> ROTATION_TRANSITION_MODE_CODEC =
			EnumStreamCodecUtil.create(LcRotationTransitionMode.class);
	private static final StreamCodec<ByteBuf, LcControllerBlendType> BLEND_TYPE_CODEC =
			EnumStreamCodecUtil.create(LcControllerBlendType.class);
	public static final StreamCodec<ByteBuf, LcAnimationTransitionSettings> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LcAnimationTransitionSettings::fadeInTicks,
			ByteBufCodecs.VAR_INT, LcAnimationTransitionSettings::fadeOutTicks,
			TRANSITION_MODE_CODEC, LcAnimationTransitionSettings::fadeInTransitionMode,
			TRANSITION_MODE_CODEC, LcAnimationTransitionSettings::fadeOutTransitionMode,
			ROTATION_TRANSITION_MODE_CODEC, LcAnimationTransitionSettings::fadeInRotationTransitionMode,
			ROTATION_TRANSITION_MODE_CODEC, LcAnimationTransitionSettings::fadeOutRotationTransitionMode,
			TRANSITION_MODE_CODEC, LcAnimationTransitionSettings::animationTransitionMode,
			ROTATION_TRANSITION_MODE_CODEC, LcAnimationTransitionSettings::rotationTransitionMode,
			BLEND_TYPE_CODEC, LcAnimationTransitionSettings::blendType,
			LcAnimationTransitionSettings::new
	);

	public void applyTo(AnimationController<?> controller) {
		LcAnimationControllerTransitions<?> transitions = LcAnimationControllerTransitions.of(controller);
		transitions.lc$setFadeInTicks(fadeInTicks);
		transitions.lc$setFadeOutTicks(fadeOutTicks);
		transitions.lc$setFadeInTransitionMode(fadeInTransitionMode);
		transitions.lc$setFadeOutTransitionMode(fadeOutTransitionMode);
		transitions.lc$setFadeInRotationTransitionMode(fadeInRotationTransitionMode);
		transitions.lc$setFadeOutRotationTransitionMode(fadeOutRotationTransitionMode);
		transitions.lc$setAnimationTransitionMode(animationTransitionMode);
		transitions.lc$setRotationTransitionMode(rotationTransitionMode);
		transitions.lc$setBlendType(blendType);
	}
}
