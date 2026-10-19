package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.HDRColorFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import org.joml.Vector4f;

import java.util.List;
import java.util.function.Supplier;

/// 只进入运行槽；基础出生/存活期曲线继续按粒子自身时间求值，不固化成特效帧的常数。
public class PhotonMixedFunction implements NumberFunction {
	private final NumberFunction base;
	private final List<PhotonValueContribution> values;
	private final int axis;
	private final boolean color;
	private final AnimatedPropertyType type;

	public PhotonMixedFunction(NumberFunction base, List<PhotonValueContribution> values, int axis,
	                           boolean color, AnimatedPropertyType type) {
		this.base = base;
		this.values = List.copyOf(values);
		this.axis = axis;
		this.color = color;
		this.type = type;
	}

	public static NumberFunction create(NumberFunction base, List<PhotonValueContribution> values, int axis,
	                                    boolean color, boolean hdr, AnimatedPropertyType type) {
		return hdr ? new Hdr(base, values, type) : new PhotonMixedFunction(base, values, axis, color, type);
	}

	public static Vector4f color(NumberFunction source, float time, Supplier<Float> random) {
		var result = new Vector4f();

		if (source instanceof HDRColorFunction hdr) {
			hdr.sampleHDR(time, random, result);
			return result;
		}

		return ColorUtils.toVector4f(source.get(time, random).intValue());
	}

	@Override
	public Number get(float time, Supplier<Float> random) {
		if (color) {
			var result = rgba(time, random);

			for (int index = 0; index < 4; index++) {
				result.setComponent(index, Math.clamp(result.get(index), 0, 1));
			}

			return ColorUtils.fromVector4f(result);
		}

		float result = base.get(time, random).floatValue();

		for (var value : values) {
			result = value.apply(result, axis, time, random);
		}

		return type.clampValue(result);
	}

	protected Vector4f rgba(float time, Supplier<Float> random) {
		var result = color(base, time, random);

		for (var value : values) {
			for (int index = 0; index < 4; index++) {
				result.setComponent(index, value.apply(result.get(index), index, time, random));
			}
		}

		result.w = Math.clamp(result.w, 0, 1);
		return result;
	}

	@Override
	public void loadConfig(NumberFunctionConfig config) {
		base.loadConfig(config);
	}

	@Override
	public NumberFunction copy() {
		return create(base.copy(), values, axis, color, this instanceof HDRColorFunction, type);
	}

	@Override
	public void createConfigurator(NumberFunctionConfigurator configurator) {
		base.createConfigurator(configurator);
	}

	private static class Hdr extends PhotonMixedFunction implements HDRColorFunction {
		private Hdr(NumberFunction base, List<PhotonValueContribution> values, AnimatedPropertyType type) {
			super(base, values, 0, true, type);
		}

		@Override
		public void sampleHDR(float time, Supplier<Float> random, Vector4f out) {
			out.set(rgba(time, random));
		}
	}
}
