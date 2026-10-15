package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.RotationPropertyType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// 仅恢复自己仍持有的覆盖值，不覆盖其他时间轴后来写入的内容。
public class PhotonAnimationProperty {
	public record NativeSample(float[] values, @Nullable Object function) {
	}

	/// Photon 原生配置属性的路径前缀。
	public static final String CONFIG_PREFIX = "config.";
	private final FXObject target;
	private final AnimatedPropertyType type;
	private final @Nullable RuntimeBinding binding;
	private @Nullable Object previous;
	private @Nullable Object installed;
	private boolean overridden;
	private float[] base;
	private float @Nullable [] applied;
	private float @Nullable [] appliedLive;
	private @Nullable Quaternionf baseRotation;

	public static int valueChannelCount(AnimatedPropertyType type) {
		return type instanceof ColorPropertyType ? 4 : type.channelCount();
	}

	public PhotonAnimationProperty(FXObject target, AnimatedPropertyType type) {
		this.target = target;
		this.type = type;
		binding = type.path().startsWith(CONFIG_PREFIX)
				? target.getFXObjectType().resolveRuntimeBinding(target, type.path().substring(CONFIG_PREFIX.length())) : null;
		base = capture();
	}

	public void release() {
		if (binding != null) {
			var slot = binding.slot(target);

			if (installed != null && slot.get() == installed) {
				restore(slot, overridden, previous);
			}
		} else if (ownsValue()) {
			restoreDirect(base, baseRotation);
		}

		installed = null;
		applied = null;
		appliedLive = null;
		baseRotation = null;
	}

	public boolean ownsValue() {
		if (binding != null) {
			return installed != null && binding.slot(target).get() == installed;
		}

		return appliedLive != null && Arrays.equals(type.captureLive(target), appliedLive);
	}

	private void restore(RuntimeValue<?> slot, boolean wasOverridden, @Nullable Object value) {
		if (wasOverridden) {
			slot.setRaw(value);
			return;
		}

		slot.clear();
	}

	public void apply(List<PhotonAnimationContribution> values) {
		base = capture();

		if (binding == null) {
			baseRotation = captureRotation();
			applied = compose(base, values);
			type.apply(target, applied);
			// 所有权比较写后实际值，避免旋转主值和类型转换与输入值不一致。
			appliedLive = type.captureLive(target).clone();
			return;
		}

		var slot = binding.slot(target);
		previous = slot.get();
		overridden = slot.isOverridden();
		applied = compose(base, values);
		installed = effective(previous, values);
		slot.setRaw(installed);
	}

	/// 临时执行原生属性，读取其实际运行值及函数后恢复调用前状态。
	public NativeSample captureNative(Runnable sampler) {
		var before = capture();
		var beforeRotation = captureRotation();
		var slot = binding == null ? null : binding.slot(target);
		var wasOverridden = slot != null && slot.isOverridden();
		var previousValue = slot == null ? null : slot.get();

		try {
			sampler.run();
			return new NativeSample(capture(), slot == null ? null : slot.get());
		} finally {
			if (slot != null) {
				restore(slot, wasOverridden, previousValue);
			} else {
				restoreDirect(before, beforeRotation);
			}
		}
	}

	private @Nullable Quaternionf captureRotation() {
		return type instanceof RotationPropertyType ? new Quaternionf(target.transform().localRotation()) : null;
	}

	private void restoreDirect(float[] values, @Nullable Quaternionf rotation) {
		if (rotation != null) {
			// 恢复原始四元数，避免临时采样和反复释放经过 Euler 转换后漂移。
			target.transform().localRotation(new Quaternionf(rotation));
			return;
		}

		type.apply(target, values);
	}

	private Object effective(Object value, List<PhotonAnimationContribution> values) {
		if (value instanceof NumberFunction function) {
			var color = type instanceof ColorPropertyType;
			var hdr = type instanceof ColorPropertyType colorType && colorType.isHDR(target);
			return PhotonAnimationNumberFunction.create(function, values, 0, color, hdr, type);
		}

		if (value instanceof NumberFunction3 vector) {
			return new NumberFunction3(wrap(vector.x, values, 0), wrap(vector.y, values, 1), wrap(vector.z, values, 2));
		}

		type.apply(target, compose(base, values));
		return Objects.requireNonNull(binding).slot(target).get();
	}

	private NumberFunction wrap(NumberFunction value, List<PhotonAnimationContribution> values, int axis) {
		return new PhotonAnimationNumberFunction(value, values, axis, false, type);
	}

	private float[] compose(float[] initial, List<PhotonAnimationContribution> values) {
		var result = initial.clone();

		for (var contribution : values) {
			for (var axis = 0; axis < result.length; axis++) {
				result[axis] = contribution.apply(result[axis], axis);
			}
		}

		for (var axis = 0; axis < result.length; axis++) {
			result[axis] = type instanceof ColorPropertyType colorType
					? colorType.isHDR(target) && axis < 3 ? result[axis] : Math.clamp(result[axis], 0, 1)
					: type.clampValue(result[axis]);
		}

		return result;
	}

	private float[] capture() {
		if (type instanceof ColorPropertyType colorType) {
			var rgba = colorType.captureHDRColor(target).toVector4f();
			return new float[]{rgba.x, rgba.y, rgba.z, rgba.w};
		}

		return type.capture(target);
	}

	public float[] query(boolean composed) {
		if (applied == null && installed == null) {
			base = capture();
		}

		return (composed && applied != null ? applied : base).clone();
	}

	public FXObject target() {
		return target;
	}

}
