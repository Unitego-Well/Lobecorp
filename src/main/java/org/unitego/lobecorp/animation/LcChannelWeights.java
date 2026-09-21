package org.unitego.lobecorp.animation;

import net.minecraft.util.Mth;

public record LcChannelWeights(float translation, float rotation, float scale) {
	public static final LcChannelWeights FULL = new LcChannelWeights(1.0F, 1.0F, 1.0F);
	public static final LcChannelWeights NONE = new LcChannelWeights(0.0F, 0.0F, 0.0F);

	public LcChannelWeights {
		translation = Mth.clamp(translation, 0.0F, 1.0F);
		rotation = Mth.clamp(rotation, 0.0F, 1.0F);
		scale = Mth.clamp(scale, 0.0F, 1.0F);
	}

	public LcChannelWeights multiply(float multiplier) {
		return new LcChannelWeights(translation * multiplier, rotation * multiplier, scale * multiplier);
	}
}
