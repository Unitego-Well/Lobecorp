package org.unitego.lobecorp.world.entity.attribute;

import net.minecraft.world.entity.ai.attributes.Attribute;

/// 仅限制最大值的实体属性。
public class MaxAttribute extends Attribute {
	/// 属性默认值高于上限时的错误说明。
	private static final String INVALID_DEFAULT_MESSAGE = "Default value must be less than or equal to the maximum value";

	private final double maxValue;

	public MaxAttribute(String descriptionId, double defaultValue, double maxValue) {
		super(descriptionId, defaultValue);
		if (defaultValue > maxValue) {
			throw new IllegalArgumentException(INVALID_DEFAULT_MESSAGE);
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
