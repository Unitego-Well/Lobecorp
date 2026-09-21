package org.unitego.lobecorp.entity.attribute;

import net.minecraft.world.entity.ai.attributes.Attribute;

public class MinAttribute extends Attribute {
	private final double minValue;

	public MinAttribute(String descriptionId, double defaultValue, double minValue) {
		super(descriptionId, defaultValue);
		if (defaultValue < minValue) {
			throw new IllegalArgumentException("Default value must be greater than or equal to the minimum value");
		}
		this.minValue = minValue;
	}

	public double getMinValue() {
		return minValue;
	}

	@Override
	public double sanitizeValue(double value) {
		return Math.max(minValue, value);
	}
}
