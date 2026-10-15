package org.unitego.lobecorp.world.entity.attribute;

import net.minecraft.world.entity.ai.attributes.Attribute;

/// 仅限制最小值的实体属性。
public class MinAttribute extends Attribute {
	/// 属性默认值低于下限时的错误说明。
	private static final String INVALID_DEFAULT_MESSAGE = "Default value must be greater than or equal to the minimum value";

	private final double minValue;

	public MinAttribute(String descriptionId, double defaultValue, double minValue) {
		super(descriptionId, defaultValue);
		if (defaultValue < minValue) {
			throw new IllegalArgumentException(INVALID_DEFAULT_MESSAGE);
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
