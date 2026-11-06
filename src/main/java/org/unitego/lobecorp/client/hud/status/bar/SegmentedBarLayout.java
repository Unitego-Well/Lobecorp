package org.unitego.lobecorp.client.hud.status.bar;

/// 按值、吸收、护盾的优先级分配空间；超出最小宽度预算的尾段隐藏。
public class SegmentedBarLayout {
	private static final float VALUE_MINIMUM_RATIO = 0.2F;
	private static final float EXTRA_MINIMUM_WIDTH = 2;

	public static float[] widths(float width, float maximum, float[] values) {
		float[] result = new float[values.length];
		if (width <= 0 || maximum <= 0 || values.length == 0) {
			return result;
		}
		float sum = 0, requested = 0;
		for (int index = 0; index < values.length; index++) {
			float value = Math.max(0, values[index]);
			sum += value;
			requested += index == 0 ? width * value / maximum
					: value > 0 ? Math.max(EXTRA_MINIMUM_WIDTH, width * value / maximum) : 0;
		}
		if (requested <= width) {
			for (int index = 0; index < values.length; index++) {
				result[index] = index == 0 ? width * values[index] / maximum
						: values[index] > 0 ? Math.max(EXTRA_MINIMUM_WIDTH, width * values[index] / maximum) : 0;
			}
			return result;
		}
		int extraCount = 0;
		for (int index = 1; index < values.length; index++) {
			if (values[index] > 0) {
				extraCount++;
			}
		}
		float baseMinimum = values[0] > 0 ? width * VALUE_MINIMUM_RATIO : 0;
		int visibleExtras = Math.min(extraCount, (int) ((width - baseMinimum) / EXTRA_MINIMUM_WIDTH));
		float baseMaximum = width - visibleExtras * EXTRA_MINIMUM_WIDTH;
		result[0] = values[0] > 0 ? Math.min(baseMaximum, Math.max(baseMinimum, width * values[0] / sum)) : 0;
		float budget = width - result[0], extraSum = 0;
		int count = 0;
		for (int index = 1; index < values.length; index++) {
			if (values[index] <= 0) {
				continue;
			}
			if ((count + 1) * EXTRA_MINIMUM_WIDTH > budget) {
				break;
			}
			result[index] = EXTRA_MINIMUM_WIDTH;
			extraSum += values[index];
			count++;
		}
		float remainder = budget - count * EXTRA_MINIMUM_WIDTH;
		for (int index = 1; index < values.length; index++) {
			if (result[index] > 0 && extraSum > 0) {
				result[index] += remainder * values[index] / extraSum;
			}
		}
		return result;
	}
}
