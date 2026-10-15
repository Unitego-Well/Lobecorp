package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.HDRColorFunction;
import org.joml.Vector4f;

import java.util.List;
import java.util.function.Supplier;

/// 只安装在运行槽中的适配函数，基础随机数和生命周期曲线仍由原函数求值。
public class PhotonAnimationNumberFunction implements NumberFunction {
	private final NumberFunction base;
	private final List<PhotonAnimationContribution> contributions;
	private final int axis;
	private final boolean color;
	private final AnimatedPropertyType type;

	public PhotonAnimationNumberFunction(NumberFunction base, List<PhotonAnimationContribution> contributions,
	                                     int axis, boolean color, AnimatedPropertyType type) {
		this.base = base;
		this.contributions = List.copyOf(contributions);
		this.axis = axis;
		this.color = color;
		this.type = type;
	}

	public static PhotonAnimationNumberFunction create(NumberFunction base, List<PhotonAnimationContribution> contributions,
	                                                   int axis, boolean color, boolean hdr, AnimatedPropertyType type) {
		return hdr ? new HDR(base, contributions, type) : new PhotonAnimationNumberFunction(base, contributions, axis, color, type);
	}

	public static Vector4f sampleColor(NumberFunction function, float time, Supplier<Float> random) {
		if (function instanceof HDRColorFunction hdr) {
			var rgba = new Vector4f();
			hdr.sampleHDR(time, random, rgba);
			return rgba;
		}

		return ColorUtils.toVector4f(function.get(time, random).intValue());
	}

	@Override
	public Number get(float time, Supplier<Float> random) {
		if (color) {
			var rgba = sampleRGBA(time, random);

			for (var channel = 0; channel < 4; channel++) {
				rgba.setComponent(channel, Math.clamp(rgba.get(channel), 0, 1));
			}

			return ColorUtils.fromVector4f(rgba);
		}

		var result = base.get(time, random).floatValue();

		for (var contribution : contributions) {
			result = contribution.apply(result, axis, time, random);
		}

		return type.clampValue(result);
	}

	protected Vector4f sampleRGBA(float time, Supplier<Float> random) {
		var rgba = sampleColor(base, time, random);

		for (var contribution : contributions) {
			for (var channel = 0; channel < 4; channel++) {
				rgba.setComponent(channel, contribution.apply(rgba.get(channel), channel, time, random));
			}
		}

		rgba.w = Math.clamp(rgba.w, 0, 1);
		return rgba;
	}

	@Override
	public void loadConfig(NumberFunctionConfig config) {
		base.loadConfig(config);
	}

	@Override
	public NumberFunction copy() {
		return create(base.copy(), contributions, axis, color, this instanceof HDRColorFunction, type);
	}

	@Override
	public void createConfigurator(NumberFunctionConfigurator configurator) {
		base.createConfigurator(configurator);
	}

	private static class HDR extends PhotonAnimationNumberFunction implements HDRColorFunction {
		private HDR(NumberFunction base, List<PhotonAnimationContribution> contributions, AnimatedPropertyType type) {
			super(base, contributions, 0, true, type);
		}

		@Override
		public void sampleHDR(float time, Supplier<Float> random, Vector4f out) {
			out.set(sampleRGBA(time, random));
		}
	}
}
