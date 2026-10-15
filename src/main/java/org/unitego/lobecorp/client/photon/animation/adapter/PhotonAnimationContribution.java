package org.unitego.lobecorp.client.photon.animation.adapter;

import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;

import java.util.function.Supplier;

/// 函数贡献在逐粒子求值后加入各分量的周期偏移，再应用绑定缩放；静态值已包含该偏移。
public record PhotonAnimationContribution(PhotonAnimationBinding.Blend blend, int component, float[] values,
                                          NumberFunction[] functions, boolean color, float scale, float offset,
                                          float[] functionOffsets, float[] scales, float[] offsets) {
	public PhotonAnimationContribution {
		values = values.clone();
		functions = functions == null ? null : functions.clone();
		functionOffsets = functionOffsets == null ? null : functionOffsets.clone();
		scales = scales == null ? null : scales.clone();
		offsets = offsets == null ? null : offsets.clone();
	}

	public PhotonAnimationContribution(PhotonAnimationBinding.Blend blend, int component, float[] values,
	                                   NumberFunction[] functions, boolean color, float scale, float offset,
	                                   float[] functionOffsets) {
		this(blend, component, values, functions, color, scale, offset, functionOffsets, null, null);
	}

	public PhotonAnimationContribution(PhotonAnimationBinding.Blend blend, int component, float[] values,
	                                   NumberFunction[] functions, boolean color, float scale, float offset) {
		this(blend, component, values, functions, color, scale, offset, null);
	}

	public PhotonAnimationContribution(PhotonAnimationBinding.Blend blend, int component, float[] values) {
		this(blend, component, values, null, false, 1, 0);
	}

	public float apply(float base, int axis, float particleTime, Supplier<Float> random) {
		if (functions == null) {
			return apply(base, axis);
		}

		if (component >= 0 && component != axis) {
			return base;
		}

		var index = functions.length == 1 ? 0 : axis;

		if (index >= functions.length) {
			return base;
		}

		var function = functions[index];
		var value = color ? PhotonAnimationNumberFunction.sampleColor(function, particleTime, random).get(axis)
				: function.get(particleTime, random).floatValue();

		if (functionOffsets != null && axis < functionOffsets.length) {
			value += functionOffsets[axis];
		}

		return blend.combine(base, value * coefficient(scales, axis, scale) + coefficient(offsets, axis, offset));
	}

	private float coefficient(float[] values, int axis, float fallback) {
		return values == null ? fallback : values[values.length == 1 ? 0 : axis];
	}

	public float apply(float base, int axis) {
		if (component >= 0 && component != axis) {
			return base;
		}

		var index = values.length == 1 ? 0 : axis;

		if (index >= values.length) {
			return base;
		}

		return blend.combine(base, values[index]);
	}
}
