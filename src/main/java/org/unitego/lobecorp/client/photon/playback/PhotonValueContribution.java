package org.unitego.lobecorp.client.photon.playback;

import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Blend;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/// 单帧贡献值不可变；函数只读借用本播放方案的已准备表达式，没有可变出口。
public class PhotonValueContribution {
	private final Blend blend;
	private final int component;
	private final List<Float> values;
	private final List<Float> scales;
	private final List<Float> offsets;
	private final @Nullable Function function;
	public PhotonValueContribution(Blend blend, int component, List<Float> values,
	                               List<Float> scales, List<Float> offsets, @Nullable Function function) {
		if (component < -1 || component > 3 || values.isEmpty() || scales.isEmpty() || offsets.isEmpty()) {
			throw new IllegalArgumentException("动画贡献通道无效。");
		}

		this.blend = blend;
		this.component = component;
		this.values = List.copyOf(values);
		this.scales = List.copyOf(scales);
		this.offsets = List.copyOf(offsets);
		this.function = function;
	}

	private static float channel(List<Float> values, int axis) {
		if (values.size() != 1 && axis >= values.size()) {
			throw new IllegalArgumentException("动画贡献系数通道不匹配。");
		}

		return values.get(values.size() == 1 ? 0 : axis);
	}

	public float apply(float base, int axis, float particleTime, Supplier<Float> random) {
		if (component >= 0 && component != axis || values.size() != 1 && axis >= values.size()) {
			return base;
		}

		float sampled = function == null ? channel(values, axis) : function.sample(axis, particleTime, random);
		float value = sampled * channel(scales, axis) + channel(offsets, axis);

		if (!Float.isFinite(value)) {
			throw new IllegalArgumentException("动画贡献求值不是有限值。");
		}

		float result = switch (blend) {
			case OVERRIDE -> value;
			case ADD -> base + value;
			case MULTIPLY -> base * value;
		};

		if (!Float.isFinite(result)) {
			throw new IllegalArgumentException("动画混合结果不是有限值。");
		}

		return result;
	}

	@FunctionalInterface
	public interface Function {
		float sample(int axis, float particleTime, Supplier<Float> random);
	}
}
