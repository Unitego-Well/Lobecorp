package org.unitego.lobecorp.animation;

import java.util.Objects;

public record LcLayerDefinition(String name, LcBlendMode blendMode, LcBoneMask mask, int transitionTicks,
		LcTransitionMode transitionMode) {
	public LcLayerDefinition(String name, LcBlendMode blendMode, LcBoneMask mask, int transitionTicks) {
		this(name, blendMode, mask, transitionTicks, LcTransitionMode.OVERLAP);
	}

	public LcLayerDefinition {
		if (name.isBlank()) {
			throw new IllegalArgumentException("Layer name cannot be blank");
		}
		if (transitionTicks < 0) {
			throw new IllegalArgumentException("Transition ticks cannot be negative");
		}
		Objects.requireNonNull(blendMode);
		Objects.requireNonNull(mask);
		Objects.requireNonNull(transitionMode);
	}
}
