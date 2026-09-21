package org.unitego.lobecorp.entity.attribute;

import net.minecraft.world.entity.ai.attributes.Attribute;

public class MaxAttribute extends Attribute {
	private final double maxValue;

	public MaxAttribute(String descriptionId, double defaultValue, double maxValue) {
		super(descriptionId, defaultValue);
		if (defaultValue > maxValue) {
			throw new IllegalArgumentException("Default value must be less than or equal to the maximum value");
		}
		this.maxValue = maxValue;
	}

	public double getMaxValue() {
		return maxValue;
	}

	@Override
	public double sanitizeValue(double value) {
		return Math.min(value, maxValue);
	}
}
