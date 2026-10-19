package org.unitego.lobecorp.config.photon.playback;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 命名播放的不可变制作定义；原始NBT另存，运行句柄和可变原生轨道不写入此对象。
public record PhotonNamedLibrary(CompoundTag source, String defaultAnimation,
                                 List<Definition> animations, List<Rule> rules) {
	public static final int VERSION = 6;
	public static final double DEFAULT_DURATION = 20;
	/// float通道能够连续精确表示的整数边界，与现有形参协议一致。
	public static final float MAX_EXACT_INTEGER = 16_777_216;

	public PhotonNamedLibrary {
		source = source.copy();
		animations = List.copyOf(animations);
		rules = List.copyOf(rules);
	}

	@Override
	public CompoundTag source() {
		return source.copy();
	}

	public enum Completion {
		HOLD, RELEASE
	}

	public enum Blend {
		OVERRIDE, ADD, MULTIPLY
	}

	public enum ValueSource {
		CURVE, PARAMETER, EFFECT_TIME, ANIMATION_TIME, PROGRESS, LOOP_COUNT
	}

	public enum Event {
		START, COMPLETE, MARKER, SIGNAL, TRIGGER
	}

	public enum ParameterType {
		NUMBER(1), INTEGER(1), BOOLEAN(1), VECTOR(3), COLOR(4),
		FLOAT_VEC2(2), FLOAT_VEC4(4), INTEGER_VEC2(2), INTEGER_VEC3(3), INTEGER_VEC4(4);

		private final int channels;

		private ParameterType(int channels) {
			this.channels = channels;
		}

		public int channels() {
			return channels;
		}

		public boolean integer() {
			return this == INTEGER || this == INTEGER_VEC2 || this == INTEGER_VEC3 || this == INTEGER_VEC4;
		}
	}

	public record Parameter(UUID id, String name, ParameterType type, List<Float> defaults, float minimum,
	                        float maximum) {
		public Parameter {
			defaults = coerce(type, defaults, minimum, maximum);
		}

		public List<Float> coerce(List<Float> input) {
			return coerce(type, input, minimum, maximum);
		}

		private static List<Float> coerce(ParameterType type, List<Float> input, float minimum, float maximum) {
			if (input.size() != type.channels() || !Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum > maximum) {
				throw new IllegalArgumentException("命名动画形参通道或范围无效。");
			}

			float lower = minimum;
			float upper = maximum;

			if (type.integer()) {
				lower = (float) Math.ceil(Math.clamp(lower, -MAX_EXACT_INTEGER, MAX_EXACT_INTEGER));
				upper = (float) Math.floor(Math.clamp(upper, -MAX_EXACT_INTEGER, MAX_EXACT_INTEGER));
			} else if (type == ParameterType.COLOR || type == ParameterType.BOOLEAN) {
				lower = type == ParameterType.BOOLEAN ? 0 : Math.max(lower, 0);
				upper = type == ParameterType.BOOLEAN ? 1 : Math.min(upper, 1);
			}

			if (lower > upper) {
				throw new IllegalArgumentException("命名动画形参有效范围为空。");
			}

			var result = new ArrayList<Float>();

			for (float value : input) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("命名动画形参包含非有限值。");
				}

				float clamped = Math.clamp(value, lower, upper);
				result.add(type == ParameterType.BOOLEAN ? value >= 0.5f ? 1f : 0f
						: type.integer() ? (float) Math.round(clamped) : clamped);
			}

			return List.copyOf(result);
		}
	}

	public record Binding(UUID target, String property, Blend blend, ValueSource source, String parameter,
	                      int component, float scale, float offset, String scaleParameter, String offsetParameter) {
	}

	public record Input(String target, String source, List<Float> value, boolean follow) {
		public Input {
			value = List.copyOf(value);
		}
	}

	public record Marker(double time, String name, CompoundTag data) {
		public Marker {
			data = data.copy();
		}

		@Override
		public CompoundTag data() {
			return data.copy();
		}
	}

	public record Rule(Event event, String animation, String channel, String name, String target,
	                   double duration, List<Input> inputs) {
		public Rule {
			inputs = List.copyOf(inputs);
		}
	}

	public record Definition(CompoundTag source, UUID id, String name, double duration, boolean autoDuration,
	                         boolean autoEnableModules, boolean loop, int priority, Completion completion,
	                         String durationParameter, List<Parameter> parameters, List<CompoundTag> tracks,
	                         List<Binding> bindings, List<Marker> markers) {
		public Definition {
			source = source.copy();
			parameters = List.copyOf(parameters);
			tracks = tracks.stream().map(CompoundTag::copy).toList();
			bindings = List.copyOf(bindings);
			markers = List.copyOf(markers);
		}

		@Override
		public CompoundTag source() {
			return source.copy();
		}

		@Override
		public List<CompoundTag> tracks() {
			return tracks.stream().map(CompoundTag::copy).toList();
		}
	}
}
