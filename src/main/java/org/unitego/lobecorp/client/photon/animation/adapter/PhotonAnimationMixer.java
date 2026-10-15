package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PhotonAnimationMixer {
	public record Key(UUID target, String property) {
	}

	private final FXRuntime runtime;
	private final Map<Key, PhotonAnimationProperty> properties = new LinkedHashMap<>();
	private final Consumer<String> report;
	private final Map<PhotonAnimationPlayback, Map<Key, List<PhotonAnimationContribution>>> layers = new LinkedHashMap<>();
	private double effectTime;
	private double partialTicks;
	private boolean recording;

	public PhotonAnimationMixer(FXRuntime runtime, Consumer<String> report) {
		this.runtime = runtime;
		this.report = report;
	}

	public void release() {
		properties.values().forEach(PhotonAnimationProperty::release);
	}

	public void clear() {
		release();
		properties.clear();
		layers.clear();
	}

	public void apply(List<PhotonAnimationPlayback> animations, double effectTime, double partialTicks) {
		begin(effectTime, partialTicks, false);

		for (var playback : animations) {
			samplePose(playback);
		}

		commit();
	}

	public void begin(double effectTime, double partialTicks, boolean recording) {
		this.recording = recording;

		if (recording) {
			return;
		}

		// 每次合成都释放自己的旧贡献，避免包装函数或偏移重复安装。
		release();
		layers.clear();
		this.effectTime = effectTime;
		this.partialTicks = partialTicks;
	}

	public PhotonAnimationSamplingScope scope(PhotonAnimationPlayback playback) {
		return new PhotonAnimationSamplingScope(new Layer(playback));
	}

	/// 录制只冻结原生属性，控制、速度、信号和渲染阶段仍执行。
	public PhotonAnimationSamplingScope recordingScope() {
		return new PhotonAnimationSamplingScope(new PhotonAnimationSamplingScope.Handler() {
			@Override
			public void beginPass() {
			}

			@Override
			public void sample(AnimatedProperty property, FXObject target, double time, Runnable original) {
			}

			@Override
			public void commit() {
			}
		});
	}

	/// 暂时解除自己仍拥有的覆盖以保存基础值，随后恢复冻结姿态。
	public <T> T serializeFrozenBase(Supplier<T> serializer) {
		var owned = properties.entrySet().stream().filter(entry -> entry.getValue().ownsValue()).map(Map.Entry::getKey).toList();
		var values = contributions();
		release();

		try {
			return serializer.get();
		} finally {
			for (var key : owned) {
				var contribution = values.get(key);

				if (contribution != null) {
					properties.get(key).apply(contribution);
				}
			}
		}
	}

	/// 无运行执行器的姿态查询也调用真实的原生属性 apply。
	@SuppressWarnings("unused") // 采样作用域只负责收集原生属性并恢复外层调用。
	public void samplePose(PhotonAnimationPlayback playback) {
		try (var sampling = scope(playback)) {
			PhotonAnimationSamplingScope.beginPass();
			var time = playback.time(partialTicks);

			for (var leaf : playback.definition.leafTracks(false)) {
				if (!(leaf instanceof AnimationTrack track) || track instanceof SpeedTrack || track.mute()
						|| !(runtime.getSceneObject(track.targetId()) instanceof FXObject target)) {
					continue;
				}

				track.sampleInto(target, time);
			}

			PhotonAnimationSamplingScope.commit();
		}
	}

	public void commit() {
		if (recording) {
			return;
		}

		release();
		contributions().forEach((key, values) -> {
			try {
				properties.get(key).apply(values);
			} catch (IllegalArgumentException failure) {
				report.accept(key.property());
			}
		});
	}

	private Map<Key, List<PhotonAnimationContribution>> contributions() {
		var contributions = new LinkedHashMap<Key, List<PhotonAnimationContribution>>();

		for (var layer : layers.values()) {
			layer.forEach((key, values) -> contributions.computeIfAbsent(key, _ -> new ArrayList<>()).addAll(values));
		}

		return contributions;
	}

	private class Layer implements PhotonAnimationSamplingScope.Handler {
		private final PhotonAnimationPlayback playback;
		private final Map<Key, List<PhotonAnimationContribution>> contributions = new LinkedHashMap<>();

		private Layer(PhotonAnimationPlayback playback) {
			this.playback = playback;
			layers.put(playback, contributions);
		}

		@Override
		public void beginPass() {
			contributions.clear();
		}

		@Override
		public void sample(AnimatedProperty property, FXObject target, double time, Runnable original) {
			// 原生录制读取检视面板的编辑基础值，命名动画不能重新安装运行覆盖。
			if (recording) {
				return;
			}

			var key = new Key(target.id(), property.type().path());
			var bindings = playback.definition.bindings.stream()
					.filter(value -> value.matches(key.target(), key.property())).toList();

			if (bindings.isEmpty()) {
				bindings = List.of(new PhotonAnimationBinding());
			}

			var discrete = property.type().stepped() && (!(property.type() instanceof ConfigPropertyType config)
					|| config.valueType() != ConfigValueType.INT);
			bindings = bindings.stream()
					.filter(binding -> !discrete || binding.blend == PhotonAnimationBinding.Blend.OVERRIDE).toList();

			if (bindings.isEmpty()) {
				return;
			}

			try {
				var slot = properties.computeIfAbsent(key, _ -> new PhotonAnimationProperty(target, property.type()));
				var nativeSample = slot.captureNative(original);

				for (var binding : bindings) {
					var value = sampleValue(playback, property, binding, nativeSample.values(), time);
					var sampled = contribution(playback, property, binding, nativeSample, value);
					contributions.computeIfAbsent(key, _ -> new ArrayList<>()).add(sampled);
				}
			} catch (IllegalArgumentException failure) {
				report.accept(key.property());
			}
		}

		@Override
		public void commit() {
			if (playback.definition.autoEnableModules && !recording) {
				enableModules(contributions);
			}

			PhotonAnimationMixer.this.commit();
		}
	}

	private float[] sampleValue(PhotonAnimationPlayback playback, AnimatedProperty property,
	                            PhotonAnimationBinding binding, float[] nativeValue, double time) {
		var values = switch (binding.source) {
			// 原生旋转已执行；保留未折回的角度，再计算倍率和跨周期贡献。
			case CURVE -> property.type().angular() ? property.sample(time) : nativeValue.clone();
			case PARAMETER -> playback.parameter(binding.parameter);
			case EFFECT_TIME -> new float[]{(float) (effectTime + partialTicks)};
			case ANIMATION_TIME -> new float[]{(float) (playback.progress(partialTicks) * playback.clock.duration())};
			case PROGRESS -> new float[]{(float) playback.progress(partialTicks)};
			case LOOP_COUNT -> new float[]{playback.loops(partialTicks)};
		};

		if (binding.source == PhotonAnimationBinding.Source.CURVE) {
			var offsets = cycleOffsets(playback, property, values.length);

			for (var axis = 0; axis < values.length; axis++) {
				values[axis] += offsets[axis];
			}
		}

		return values;
	}

	private float[] cycleOffsets(PhotonAnimationPlayback playback, AnimatedProperty property, int count) {
		var offsets = new float[count];

		if (!(property.type().angular() || property.type().path().contains("rotationOverLifetime.")
				|| property.type().path().equals("config.startRotation"))) {
			return offsets;
		}

		var start = property.sample(0);
		var end = property.sample(playback.authoredDuration());

		for (var axis = 0; axis < count; axis++) {
			offsets[axis] = playback.loops(partialTicks) * (end[axis] - start[axis]);
		}

		return offsets;
	}

	private PhotonAnimationContribution contribution(PhotonAnimationPlayback playback, AnimatedProperty property,
	                                                 PhotonAnimationBinding binding, PhotonAnimationProperty.NativeSample sample,
	                                                 float[] values) {
		var scales = coefficients(playback, binding.scaleParameter, binding.scale, values.length);
		var offsets = coefficients(playback, binding.offsetParameter, binding.offset, values.length);
		var transformed = values.clone();

		for (var axis = 0; axis < transformed.length; axis++) {
			transformed[axis] = transformed[axis] * channel(scales, axis) + channel(offsets, axis);

			if (!Float.isFinite(transformed[axis])) {
				throw new IllegalArgumentException(binding.property);
			}
		}

		if (binding.source != PhotonAnimationBinding.Source.CURVE) {
			return new PhotonAnimationContribution(binding.blend, binding.component, transformed);
		}

		var functions = switch (sample.function()) {
			case NumberFunction function -> new NumberFunction[]{function};
			case NumberFunction3 vector -> new NumberFunction[]{vector.x, vector.y, vector.z};
			case null, default -> null;
		};

		if (functions == null) {
			return new PhotonAnimationContribution(binding.blend, binding.component, transformed);
		}

		return new PhotonAnimationContribution(binding.blend, binding.component, transformed, functions,
				property.type() instanceof ColorPropertyType, binding.scale, binding.offset,
				cycleOffsets(playback, property, values.length), scales, offsets);
	}

	private float[] coefficients(PhotonAnimationPlayback playback, String parameter, float fallback, int count) {
		var values = parameter.isEmpty() ? new float[]{fallback} : playback.parameter(parameter);

		if (values.length != 1 && values.length != count) {
			throw new IllegalArgumentException(parameter);
		}

		for (var value : values) {
			if (!Float.isFinite(value)) {
				throw new IllegalArgumentException(parameter);
			}
		}

		return values;
	}

	private float channel(float[] values, int axis) {
		return values.length == 1 ? values[0] : values[axis];
	}

	private void enableModules(Map<Key, List<PhotonAnimationContribution>> contributions) {
		for (var key : List.copyOf(contributions.keySet())) {
			if (!key.property().startsWith(PhotonAnimationProperty.CONFIG_PREFIX) || key.property().endsWith(".enable")) {
				continue;
			}

			var path = key.property().substring(PhotonAnimationProperty.CONFIG_PREFIX.length());
			var dot = path.indexOf('.');

			if (dot < 0) {
				continue;
			}

			var enable = PhotonAnimationProperty.CONFIG_PREFIX + path.substring(0, dot) + ".enable";
			var enabledKey = new Key(key.target(), enable);
			var target = properties.get(key).target();
			var type = target.getFXObjectType().animatableProperties(target).stream()
					.filter(value -> value.path().equals(enable)).findFirst().orElse(null);

			if (type == null || contributions.containsKey(enabledKey)) {
				continue;
			}

			properties.computeIfAbsent(enabledKey, _ -> new PhotonAnimationProperty(target, type));
			contributions.put(enabledKey, List.of(new PhotonAnimationContribution(
					PhotonAnimationBinding.Blend.OVERRIDE, -1, new float[]{1})));
		}
	}

	/// 旧配置属性仍可通过运行绑定查询，无需重新开放编辑器的新增属性入口。
	private AnimatedPropertyType resolveProperty(FXObject object, String property) {
		var type = object.getFXObjectType().animatableProperties(object).stream()
				.filter(candidate -> candidate.path().equals(property)).findFirst().orElse(null);

		if (type != null) {
			return type;
		}

		if (!property.startsWith(PhotonAnimationProperty.CONFIG_PREFIX)) {
			throw new IllegalArgumentException(property);
		}

		var path = property.substring(PhotonAnimationProperty.CONFIG_PREFIX.length());
		var binding = object.getFXObjectType().resolveRuntimeBinding(object, path);

		if (binding == null) {
			throw new IllegalArgumentException(property);
		}

		return binding.type == ConfigValueType.COLOR
				? ColorPropertyType.fromBinding(binding) : ConfigPropertyType.fromBinding(binding);
	}

	public float[] query(UUID target, String property, boolean composed) {
		var value = properties.get(new Key(target, property));

		if (value == null) {
			if (!(runtime.getSceneObject(target) instanceof FXObject object)) {
				throw new IllegalArgumentException(property);
			}

			var type = resolveProperty(object, property);
			value = new PhotonAnimationProperty(object, type);
			properties.put(new Key(target, property), value);
		}

		return value.query(composed);
	}
}
