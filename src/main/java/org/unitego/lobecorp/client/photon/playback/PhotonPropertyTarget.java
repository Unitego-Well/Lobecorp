package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.RotationPropertyType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.lowdragmc.photon.client.fx.timeline.property.PositionPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ScalePropertyType;
import org.unitego.lobecorp.client.photon.editor.PhotonAuthoringPose;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// 每目标属性唯一写入者；一次采样临时调用原生类型并精确恢复，不把上帧结果再叠加。
public class PhotonPropertyTarget {
	public record Sample(List<Float> values, @Nullable PhotonValueContribution.Function function) {
		public Sample {
			values = List.copyOf(values);
		}
	}

	private final FXObject target;
	private final AnimatedPropertyType type;
	private final @Nullable RuntimeBinding binding;
	private @Nullable Object installed;
	private @Nullable Object previous;
	private boolean wasOverridden;
	private @Nullable float[] before;
	private @Nullable float[] after;
	private @Nullable Quaternionf rotation;

	public PhotonPropertyTarget(FXObject target, AnimatedPropertyType type) {
		this.target = target;
		this.type = type;
		binding = type.path().startsWith("config.")
				? target.getFXObjectType().resolveRuntimeBinding(target, type.path().substring("config.".length())) : null;

		if (type.path().startsWith("config.") && binding == null) {
			throw new IllegalArgumentException("命名动画属性没有可用运行槽：" + type.path());
		}
	}

	public Sample sample(AnimatedProperty property, double time) {
		var slot = slot();
		var direct = capture();
		var quaternion = quaternion();
		boolean overridden = slot != null && slot.isOverridden();
		var raw = slot == null ? null : slot.get();

		try {
			property.apply(target, time);
			return new Sample(list(capture()), function(slot == null ? null : slot.get()));
		} finally {
			if (slot != null) {
				restore(slot, overridden, raw);
			} else {
				restoreDirect(direct, quaternion);
			}
		}
	}

	private @Nullable PhotonValueContribution.Function function(@Nullable Object raw) {
		if (raw instanceof NumberFunction3 vector) {
			return (axis, time, random) -> switch (axis) {
				case 0 -> vector.x.get(time, random).floatValue();
				case 1 -> vector.y.get(time, random).floatValue();
				case 2 -> vector.z.get(time, random).floatValue();
				default -> throw new IllegalArgumentException("三维函数分量越界。");
			};
		}

		if (raw instanceof NumberFunction scalar) {
			return type instanceof ColorPropertyType
					? (axis, time, random) -> PhotonMixedFunction.color(scalar, time, random).get(axis)
					: (_, time, random) -> scalar.get(time, random).floatValue();
		}

		return null;
	}

	public void install(List<PhotonValueContribution> contributions) {
		if (contributions.isEmpty()) {
			return;
		}

		var slot = slot();
		var base = capture();
		before = base;
		rotation = quaternion();

		if (slot == null) {
			type.apply(target, compose(base, contributions));
			after = type.captureLive(target).clone();
			return;
		}

		previous = slot.get();
		wasOverridden = slot.isOverridden();

		if (previous instanceof NumberFunction scalar) {
			boolean color = type instanceof ColorPropertyType;
			boolean hdr = type instanceof ColorPropertyType colorType && colorType.isHDR(target);
			installed = PhotonMixedFunction.create(scalar, contributions, 0, color, hdr, type);
		} else if (previous instanceof NumberFunction3 vector) {
			installed = new NumberFunction3(
					PhotonMixedFunction.create(vector.x, contributions, 0, false, false, type),
					PhotonMixedFunction.create(vector.y, contributions, 1, false, false, type),
					PhotonMixedFunction.create(vector.z, contributions, 2, false, false, type));
		} else {
			type.apply(target, compose(base, contributions));
			installed = slot.get();
		}

		slot.setRaw(installed);
	}

	/// 只恢复本层仍持有的写入；其他调用方已替换的值不回滚。
	public void release() {
		var slot = slot();

		if (slot != null) {
			if (installed != null && slot.get() == installed) {
				restore(slot, wasOverridden, previous);
			}
		} else if (after != null && before != null && Arrays.equals(type.captureLive(target), after)) {
			restoreDirect(before, rotation);
		}

		installed = null;
		previous = null;
		before = null;
		after = null;
		rotation = null;
	}

	private float[] compose(float[] base, List<PhotonValueContribution> values) {
		var result = base.clone();

		for (var value : values) {
			for (int axis = 0; axis < result.length; axis++) {
				result[axis] = value.apply(result[axis], axis, 0, () -> 0.5f);
			}
		}

		for (int axis = 0; axis < result.length; axis++) {
			result[axis] = type instanceof ColorPropertyType color
					? color.isHDR(target) && axis < 3 ? result[axis] : Math.clamp(result[axis], 0, 1)
					: type.clampValue(result[axis]);
		}

		return result;
	}

    public PhotonAuthoringPose authoredPose(FXObject object, PhotonAuthoringPose current) {
        if (object != target || before == null || after == null) {
            return current;
        }
        if (type instanceof PositionPropertyType && current.position().equals(new Vector3f(after[0], after[1], after[2]))) {
            return new PhotonAuthoringPose(new Vector3f(before[0], before[1], before[2]), current.rotation(), current.scale());
        }
        if (type instanceof ScalePropertyType && current.scale().equals(new Vector3f(after[0], after[1], after[2]))) {
            return new PhotonAuthoringPose(current.position(), current.rotation(), new Vector3f(before[0], before[1], before[2]));
        }
        if (type instanceof RotationPropertyType && rotation != null && Arrays.equals(type.captureLive(object), after)) {
            return new PhotonAuthoringPose(current.position(), rotation, current.scale());
        }
        return current;
    }

	private float[] capture() {
		if (type instanceof ColorPropertyType color) {
			var value = color.captureHDRColor(target).toVector4f();
			return new float[]{value.x, value.y, value.z, value.w};
		}

		return type.capture(target).clone();
	}

	private @Nullable Quaternionf quaternion() {
		return type instanceof RotationPropertyType ? new Quaternionf(target.transform().localRotation()) : null;
	}

	private void restoreDirect(float[] values, @Nullable Quaternionf quaternion) {
		if (quaternion != null) {
			target.transform().localRotation(new Quaternionf(quaternion));
		} else {
			type.apply(target, values);
		}
	}

	private @Nullable RuntimeValue<?> slot() {
		return binding == null ? null : binding.slot(target);
	}

	private static void restore(RuntimeValue<?> slot, boolean overridden, @Nullable Object value) {
		if (overridden) {
			slot.setRaw(value);
		} else {
			slot.clear();
		}
	}

	private static List<Float> list(float[] values) {
		var result = new ArrayList<Float>();

		for (float value : values) {
			result.add(value);
		}

		return List.copyOf(result);
	}
}
