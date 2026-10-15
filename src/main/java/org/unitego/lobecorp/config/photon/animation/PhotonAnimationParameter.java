package org.unitego.lobecorp.config.photon.animation;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.UUID;

public class PhotonAnimationParameter {
	public enum Type {
		NUMBER(1), INTEGER(1), BOOLEAN(1), VECTOR(3), COLOR(4),
		FLOAT_VEC2(2), FLOAT_VEC4(4), INTEGER_VEC2(2), INTEGER_VEC3(3), INTEGER_VEC4(4);
		private final int channels;

		@SuppressWarnings("UnnecessaryModifier") // 项目规范要求明确最小可见性。
		private Type(int channels) {
			this.channels = channels;
		}

		public int channels() {
			return channels;
		}

		public boolean isInteger() {
			return this == INTEGER || this == INTEGER_VEC2 || this == INTEGER_VEC3 || this == INTEGER_VEC4;
		}

		public boolean isNumeric() {
			return this != BOOLEAN && this != COLOR;
		}
	}

	/// float[] 能连续精确保存的整数绝对上限；超出的旧整数配置和输入均限值到此边界。
	public static final float MAX_EXACT_INTEGER = 16_777_216;

	private record Bounds(float minimum, float maximum) {
	}

	/// 形参稳定身份；改名不改变动画内绑定或父子传递引用。
	public UUID id = UUID.randomUUID();
	public String name = "";
	public Type type = Type.NUMBER;
	public float[] defaults = {1};
	public float minimum = -Float.MAX_VALUE;
	public float maximum = Float.MAX_VALUE;

	public float effectiveMinimum() {
		return effectiveBounds().minimum();
	}

	public float effectiveMaximum() {
		return effectiveBounds().maximum();
	}

	private Bounds effectiveBounds() {
		if (type == Type.BOOLEAN) {
			return new Bounds(0, 1);
		}

		if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum > maximum) {
			throw new IllegalArgumentException();
		}

		var lower = minimum;
		var upper = maximum;

		if (type.isInteger()) {
			lower = (float) Math.ceil(Math.max(lower, -MAX_EXACT_INTEGER));
			upper = (float) Math.floor(Math.min(upper, MAX_EXACT_INTEGER));
		} else if (type == Type.COLOR) {
			lower = Math.max(lower, 0);
			upper = Math.min(upper, 1);
		}

		if (lower > upper) {
			throw new IllegalArgumentException();
		}

		return new Bounds(lower, upper);
	}

	public float[] validate(float[] input) {
		if (input.length != type.channels()) {
			throw new IllegalArgumentException();
		}

		var bounds = effectiveBounds();
		var result = input.clone();

		for (int i = 0; i < result.length; i++) {
			if (!Float.isFinite(result[i])) {
				throw new IllegalArgumentException();
			}

			if (type == Type.BOOLEAN) {
				result[i] = result[i] >= 0.5f ? 1 : 0;
				continue;
			}

			var value = Math.clamp(result[i], bounds.minimum(), bounds.maximum());
			result[i] = type.isInteger() ? Math.clamp(Math.round(value), bounds.minimum(), bounds.maximum()) : value;
		}

		return result;
	}

	public CompoundTag write() {
		var tag = new CompoundTag();
		tag.putString("id", id.toString());
		tag.putString("name", name);
		tag.putString("type", type.name());
		tag.put("defaults", PhotonAnimationDataUtil.floats(defaults));
		tag.putFloat("minimum", minimum);
		tag.putFloat("maximum", maximum);
		return tag;
	}

	public static PhotonAnimationParameter read(CompoundTag tag) {
		var value = new PhotonAnimationParameter();
		value.id = PhotonAnimationDataUtil.uuid(tag, "id");
		value.name = tag.getStringOr("name", "");
		value.type = PhotonAnimationDataUtil.strictEnumeration(tag, "type", Type.NUMBER);
		value.defaults = PhotonAnimationDataUtil.floats(tag, "defaults", new float[value.type.channels()]);
		value.minimum = tag.getFloatOr("minimum", -Float.MAX_VALUE);
		value.maximum = tag.getFloatOr("maximum", Float.MAX_VALUE);

		if (value.type.isInteger()) {
			if (!Float.isFinite(value.minimum) || !Float.isFinite(value.maximum) || value.minimum > value.maximum) {
				throw new IllegalArgumentException();
			}

			value.minimum = Math.clamp(value.minimum, -MAX_EXACT_INTEGER, MAX_EXACT_INTEGER);
			value.maximum = Math.clamp(value.maximum, -MAX_EXACT_INTEGER, MAX_EXACT_INTEGER);
		} else if (value.type == Type.BOOLEAN) {
			value.minimum = 0;
			value.maximum = 1;
		}

		value.defaults = value.validate(value.defaults);
		return value;
	}
}
