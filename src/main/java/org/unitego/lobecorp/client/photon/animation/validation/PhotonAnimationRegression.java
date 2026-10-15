package org.unitego.lobecorp.client.photon.animation.validation;

import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationContribution;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationProperty;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.PhotonRegistries;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;

import java.util.List;

import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;

import java.util.Map;
import java.util.Objects;

/// 游戏内菜单与 JUnit 测试共用的状态、参数和属性合成检查。
public class PhotonAnimationRegression {
	private static int assertions;


	public static int runIndependent() {
		assertions = 0;
		finiteDuration();
		loopingDuration();
		holdPause();
		mixing();
		parameters();
		legacyParameterNames();
		parameterVectors();
		parameterBounds();
		discreteParameterBounds();
		localOverrides();
		return assertions;
	}

	/// 仅在 NeoForge 已完成模组加载后调用，包含真实 Photon 属性槽检查。
	@SuppressWarnings("unused") // 保留 IDEA 调试表达式使用的完整加载检查入口。
	public static int runLoaded() {
		return PhotonAnimationChecks.runAll();
	}

	public static int runPropertySlots() {
		assertions = 0;
		ownedProperties();
		runtimeSlots();
		return assertions;
	}

	public static int runInstances() {
		assertions = 0;
		instancePlayback();
		return assertions;
	}

	private static void finiteDuration() {
		var clock = new PhotonAnimationClock(20, false);
		clock.advance(10);
		equal(0.5, clock.progress());
		clock.setDuration(100);
		equal(0.5, clock.progress());
		clock.advance(49);
		equal(0.99, clock.progress());
		check(!clock.takeCompletion());
		clock.advance(1);
		equal(1, clock.progress());
		check(clock.takeCompletion());
		check(!clock.takeCompletion());
		clock.setDuration(200);
		clock.advance(100);
		equal(1, clock.progress());
		check(!clock.takeCompletion());
	}

	private static void loopingDuration() {
		var clock = new PhotonAnimationClock(20, true);
		clock.advance(50);
		equal(2, clock.loops());
		equal(0.5, clock.progress());
		clock.setDuration(100);
		equal(2, clock.loops());
		equal(0.5, clock.progress());
		clock.advance(50);
		equal(3, clock.loops());
		equal(0, clock.progress());
		check(!clock.takeCompletion());

		try {
			clock.setDuration(0);
			throw new AssertionError();
		} catch (IllegalArgumentException expected) {
			equal(100, clock.duration());
		}
	}

	private static void holdPause() {
		var clock = new PhotonAnimationClock(10, false);
		clock.advance(5);
		clock.pause();
		clock.advance(100);
		equal(0.5, clock.progress());
		equal(0.5, clock.samplePosition(0.5));
		clock.resume();
		clock.advance(5);
		check(clock.state() == PhotonAnimationClock.State.HOLDING);
		clock.pause();
		clock.resume();
		check(clock.state() == PhotonAnimationClock.State.HOLDING);
		clock.stop();
		check(clock.state() == PhotonAnimationClock.State.STOPPED);
		var instant = new PhotonAnimationClock(0, false);
		instant.advance(1);
		check(instant.takeCompletion());
		check(!instant.takeCompletion());
	}

	private static void mixing() {
		var override = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.OVERRIDE, -1, new float[]{5});
		var add = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.ADD, -1, new float[]{2});
		var multiply = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.MULTIPLY, -1, new float[]{3});
		equal(21, multiply.apply(add.apply(override.apply(7, 0), 0), 0));
		equal(27, multiply.apply(add.apply(7, 0), 0));
		var axis = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.ADD, 1, new float[]{3});
		equal(7, axis.apply(7, 0));
		equal(10, axis.apply(7, 1));
	}

	private static void ownedProperties() {
		var target = new EmptyFXObject();
		target.roll = 7;
		var property = new PhotonAnimationProperty(target, rollType());
		var values = List.of(new PhotonAnimationContribution(PhotonAnimationBinding.Blend.ADD, -1, new float[]{2}),
				new PhotonAnimationContribution(PhotonAnimationBinding.Blend.MULTIPLY, -1, new float[]{3}));

		for (int tick = 0; tick < 1000; tick++) {
			property.release();
			property.apply(values);
			equal(27, target.roll);
			equal(7, property.query(false)[0]);
		}

		property.release();
		equal(7, target.roll);
		property.apply(List.of(values.getLast()));
		equal(21, target.roll);
		target.roll = 11;
		property.release();
		equal(11, target.roll);
		property.apply(values);
		equal(39, target.roll);
		property.release();
		equal(11, target.roll);
	}

	private static AnimatedPropertyType rollType() {
		return new AnimatedPropertyType() {
			@Override
			public String path() {
				return "roll";
			}

			@Override
			public int channelCount() {
				return 1;
			}

			@Override
			public String channelKey(int channel) {
				return "roll";
			}

			@Override
			public float[] capture(FXObject object) {
				return new float[]{object.roll};
			}

			@Override
			public void apply(FXObject object, float[] value) {
				object.roll = value[0];
			}
		};
	}

	private static void runtimeSlots() {
		var emitter = new ParticleEmitter();
		emitter.config.rotationOverLifetime.setRoll(NumberFunction.constant(7));
		var type = emitter.getFXObjectType().animatableProperties(emitter).stream()
				.filter(value -> value.path().equals("config.rotationOverLifetime.roll")).findFirst().orElseThrow();
		var property = new PhotonAnimationProperty(emitter, type);
		var values = List.of(new PhotonAnimationContribution(PhotonAnimationBinding.Blend.ADD, -1, new float[]{2}),
				new PhotonAnimationContribution(PhotonAnimationBinding.Blend.MULTIPLY, -1, new float[]{3}));
		var slot = emitter.runtime().rotationOverLifetime.roll;

		for (int tick = 0; tick < 1000; tick++) {
			property.release();
			property.apply(values);
			equal(27, slot.get().get(0.5f, () -> 0.5f).doubleValue());
			equal(7, emitter.config.rotationOverLifetime.getRoll().get(0.5f, () -> 0.5f).doubleValue());
		}

		property.release();
		check(!slot.isOverridden());
		var external = NumberFunction.constant(11);
		slot.setRaw(external);
		property.apply(values);
		equal(39, slot.get().get(0, () -> 0.5f).doubleValue());
		equal(11, property.query(false)[0]);
		equal(39, property.query(true)[0]);
		property.release();
		check(slot.get() == external);
		property.apply(values);
		var later = NumberFunction.constant(13);
		slot.setRaw(later);
		property.release();
		check(slot.get() == later);
	}

	@SuppressWarnings("ConstantValue") // FXRuntime 的控制接口由运行时 Mixin 注入。
	private static void instancePlayback() {
		var emitter = new ParticleEmitter();
		emitter.config.rotationOverLifetime.setRoll(NumberFunction.constant(7));
		var controller = new PhotonAnimationController();
		var library = new PhotonAnimationLibrary();
		var parameter = new PhotonAnimationParameter();
		parameter.name = "angle";
		parameter.defaults = new float[]{2};
		var primary = scalarAnimation(library, emitter, "primary", PhotonAnimationBinding.Blend.OVERRIDE, 2);
		primary.parameters.add(parameter);
		var binding = primary.binding(emitter.id(), "config.rotationOverLifetime.roll");
		binding.source = PhotonAnimationBinding.Source.PARAMETER;
		binding.parameter = parameter.id.toString();
		var additive = scalarAnimation(library, emitter, "add", PhotonAnimationBinding.Blend.ADD, 4);
		var multiplier = scalarAnimation(library, emitter, "multiply", PhotonAnimationBinding.Blend.MULTIPLY, 3);
		multiplier.priority = 1;
		timelineRules(library, primary);
		library.defaultAnimation = primary.id.toString();
		var signal = new PhotonAnimationRule();
		signal.event = PhotonAnimationRule.Event.SIGNAL;
		signal.channel = "test";
		signal.name = "done";
		signal.target = primary.id.toString();
		var input = new PhotonAnimationInput();
		input.target = parameter.id.toString();
		input.value = new float[]{8};
		signal.inputs.add(input);
		library.rules.add(signal);
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(emitter, controller)));

		try {
			// 独立数据夹具不访问客户端音频；真实声音播放由客户端验收覆盖。
			fx.timelinePlayer.setAudioDispatch(false);
			check(fx instanceof PhotonAnimationRuntimeAccess);
			var root = (FXObject) fx.getRoot();
			var originalTick = root.getOnUpdateTick();

			try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
				Objects.requireNonNull(root.getOnUpdateTick()).run();
				equal(2, rotation(emitter));
				check(emitter.runtime().rotationOverLifetime.isEnable());
				runtime.play(additive.name);
				runtime.play(multiplier.name);
				root.getOnUpdateTick().run();
				equal(18, rotation(emitter));
				runtime.stop(additive.name);
				equal(6, rotation(emitter));
				runtime.stop(primary.name);
				equal(21, rotation(emitter));
				runtime.stop(multiplier.name);
				equal(7, rotation(emitter));
				check(!emitter.runtime().rotationOverLifetime.isEnable());
				runtime.signal("test", "done", new CompoundTag());
				root.getOnUpdateTick().run();
				equal(8, Objects.requireNonNull(runtime.playback(primary.name)).parameter(parameter.name)[0]);
				isolatedParameter(library, runtime);
				timelineEvents(runtime, root, primary);
				equal(7, emitter.config.rotationOverLifetime.getRoll().get(0, () -> 0.5f).doubleValue());
				check(fx.getSceneObject(emitter.id()) == emitter);
			}

			check(root.getOnUpdateTick() == originalTick);
			check(!emitter.runtime().rotationOverLifetime.roll.isOverridden());
		} finally {
			fx.destroy(true);
		}
	}

	private static void timelineRules(PhotonAnimationLibrary library, PhotonAnimationDefinition primary) {
		primary.markers.add(new PhotonAnimationDefinition.Marker(10, "mid", new CompoundTag()));

		for (var event : List.of(PhotonAnimationRule.Event.MARKER, PhotonAnimationRule.Event.COMPLETE)) {
			var target = new PhotonAnimationDefinition();
			target.name = event.name();
			var parameter = new PhotonAnimationParameter();
			parameter.name = "angle";
			target.parameters.add(parameter);
			library.animations.add(target);
			var rule = new PhotonAnimationRule();
			rule.event = event;
			rule.animation = primary.id.toString();
			rule.target = target.id.toString();
			var input = new PhotonAnimationInput();
			input.target = parameter.id.toString();
			input.value = new float[]{event == PhotonAnimationRule.Event.MARKER ? 5 : 9};
			rule.inputs.add(input);
			library.rules.add(rule);
		}
	}

	private static void timelineEvents(PhotonAnimationRuntime runtime, FXObject root, PhotonAnimationDefinition primary) {
		var playback = runtime.play(primary.name);

		for (int tick = 0; tick < 10; tick++) {
			Objects.requireNonNull(root.getOnUpdateTick()).run();
		}

		equal(0.5, playback.clock.progress());
		equal(5, Objects.requireNonNull(runtime.playback("MARKER")).parameter("angle")[0]);
		runtime.setDuration(primary.name, 100);
		equal(0.5, playback.clock.progress());

		for (int tick = 0; tick < 50; tick++) {
			Objects.requireNonNull(root.getOnUpdateTick()).run();
		}

		equal(9, Objects.requireNonNull(runtime.playback("COMPLETE")).parameter("angle")[0]);
		check(playback.clock.state() == PhotonAnimationClock.State.HOLDING);
		runtime.setDuration(primary.name, 50);
		runtime.setParameter(playback, "angle", 4);
		Objects.requireNonNull(root.getOnUpdateTick()).run();
		equal(4, runtime.parameter(playback, "angle")[0]);
	}

	private static PhotonAnimationDefinition scalarAnimation(PhotonAnimationLibrary library, ParticleEmitter emitter,
	                                                         String name, PhotonAnimationBinding.Blend blend, float value) {
		var animation = new PhotonAnimationDefinition();
		animation.name = name;
		animation.autoDuration = false;
		animation.autoEnableModules = true;
		var track = new AnimationTrack();
		track.setType(PhotonRegistries.TIMELINE_TRACKS.get("animation"));
		track.targetId(emitter.id());
		var type = emitter.getFXObjectType().animatableProperties(emitter).stream()
				.filter(candidate -> candidate.path().equals("config.rotationOverLifetime.roll")).findFirst().orElseThrow();
		var property = type.create(emitter);
		property.putKey(0, 0, value);
		property.putKey(0, 20, value);
		track.properties().add(property);
		animation.tracks.add(track);
		animation.binding(emitter.id(), type.path()).blend = blend;
		library.animations.add(animation);
		return animation;
	}

	private static double rotation(ParticleEmitter emitter) {
		return emitter.runtime().rotationOverLifetime.roll.get().get(0.5f, () -> 0.5f).doubleValue();
	}

	private static void isolatedParameter(PhotonAnimationLibrary library, PhotonAnimationRuntime first) {
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(controller)));

		try {
			// 此参数隔离夹具不覆盖真实音频，不依赖全局客户端声音管理器。
			fx.timelinePlayer.setAudioDispatch(false);

			try (var second = new PhotonAnimationRuntime(fx, controller)) {
				var secondPlayback = second.play("primary");
				equal(2, secondPlayback.parameter("angle")[0]);
				second.setParameter(secondPlayback, "angle", 3);
				equal(8, Objects.requireNonNull(first.playback("primary")).parameter("angle")[0]);
				equal(3, secondPlayback.parameter("angle")[0]);
			}
		} finally {
			fx.destroy(true);
		}
	}

	private static void parameters() {
		var definition = new PhotonAnimationParameter();
		definition.type = PhotonAnimationParameter.Type.VECTOR;
		definition.defaults = new float[]{1, 2, 3};
		definition.minimum = 0;
		definition.maximum = 2;
		var roundTrip = PhotonAnimationParameter.read(definition.write());
		equal(2, roundTrip.defaults[2]);
		var first = roundTrip.validate(new float[]{0, 1, 2});
		var second = roundTrip.validate(new float[]{2, 1, 0});
		first[0] = 10;
		equal(2, second[0]);
		equal(1, roundTrip.defaults[0]);
		definition.type = PhotonAnimationParameter.Type.BOOLEAN;
		equal(1, definition.validate(new float[]{0.7f})[0]);
		equal(0, definition.validate(new float[]{0.2f})[0]);
		definition.type = PhotonAnimationParameter.Type.INTEGER;
		equal(2, definition.validate(new float[]{1.7f})[0]);
	}

	private static void legacyParameterNames() {
		var names = List.of("NUMBER", "INTEGER", "VECTOR", "BOOLEAN", "COLOR");
		var channels = new int[]{1, 1, 3, 1, 4};

		for (var index = 0; index < names.size(); index++) {
			var tag = new CompoundTag();
			tag.putString("type", names.get(index));
			var loaded = PhotonAnimationParameter.read(tag);
			check(loaded.type.name().equals(names.get(index)));
			check(loaded.defaults.length == channels[index]);
			check(loaded.write().getStringOr("type", "").equals(names.get(index)));
		}
	}

	private static void parameterVectors() {
		for (var type : PhotonAnimationParameter.Type.values()) {
			if (!type.isNumeric() || type.channels() == 1) {
				continue;
			}

			var definition = new PhotonAnimationParameter();
			definition.type = type;
			definition.minimum = -2.2f;
			definition.maximum = 4.8f;
			var input = new float[]{-5.4f, 2.6f, 9.6f, Float.MAX_VALUE};
			definition.defaults = new float[type.channels()];
			System.arraycopy(input, 0, definition.defaults, 0, definition.defaults.length);
			var loaded = PhotonAnimationParameter.read(definition.write());
			check(loaded.type == type && loaded.defaults.length == type.channels());
			var expected = type.isInteger() ? new float[]{-2, 3, 4, 4} : new float[]{-2.2f, 2.6f, 4.8f, 4.8f};

			for (var axis = 0; axis < loaded.defaults.length; axis++) {
				equal(expected[axis], loaded.defaults[axis]);
			}

			var query = loaded.validate(loaded.defaults);
			query[0] = 99;
			equal(expected[0], loaded.defaults[0]);
			equal(-5.4f, definition.defaults[0]);
		}
	}

	private static void parameterBounds() {
		var definition = new PhotonAnimationParameter();
		definition.type = PhotonAnimationParameter.Type.INTEGER;
		definition.defaults = new float[]{Float.MAX_VALUE};
		var loaded = PhotonAnimationParameter.read(definition.write());
		check(loaded.type == PhotonAnimationParameter.Type.INTEGER);
		equal(-PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.minimum);
		equal(PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.maximum);
		equal(PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.defaults[0]);
		equal(-PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.validate(new float[]{-Float.MAX_VALUE})[0]);

		definition.minimum = Float.MAX_VALUE;
		definition.maximum = Float.MAX_VALUE;
		loaded = PhotonAnimationParameter.read(definition.write());
		equal(PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.minimum);
		equal(PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.maximum);
		equal(PhotonAnimationParameter.MAX_EXACT_INTEGER, loaded.defaults[0]);
		definition.maximum = PhotonAnimationParameter.MAX_EXACT_INTEGER;
		rejected(() -> PhotonAnimationParameter.read(definition.write()));

		definition.minimum = 0.2f;
		definition.maximum = 3.2f;
		equal(1, definition.effectiveMinimum());
		equal(3, definition.effectiveMaximum());
		equal(1, definition.validate(new float[]{0.2f})[0]);
		equal(3, definition.validate(new float[]{3.8f})[0]);

		definition.minimum = -3.2f;
		definition.maximum = -0.2f;
		equal(-3, definition.effectiveMinimum());
		equal(-1, definition.effectiveMaximum());
		equal(-1, definition.validate(new float[]{-0.5f})[0]);
		equal(-3, definition.validate(new float[]{-3.8f})[0]);

		definition.minimum = 0.2f;
		definition.maximum = 0.3f;
		rejected(() -> definition.validate(new float[]{0.25f}));
		definition.minimum = Float.NaN;
		rejected(definition::effectiveMinimum);

		definition.minimum = 0;
		definition.maximum = 3;
		rejected(() -> definition.validate(new float[]{Float.POSITIVE_INFINITY}));
		rejected(() -> definition.validate(new float[]{Float.NaN}));
		rejected(() -> definition.validate(new float[]{1, 2}));
	}

	private static void discreteParameterBounds() {
		var definition = new PhotonAnimationParameter();
		definition.type = PhotonAnimationParameter.Type.BOOLEAN;
		definition.minimum = 2;
		definition.maximum = 3;
		equal(0, definition.effectiveMinimum());
		equal(1, definition.effectiveMaximum());
		equal(0, definition.validate(new float[]{0.2f})[0]);
		equal(1, definition.validate(new float[]{0.7f})[0]);
		definition.minimum = Float.NaN;
		definition.maximum = Float.NEGATIVE_INFINITY;
		var loaded = PhotonAnimationParameter.read(definition.write());
		check(loaded.type == PhotonAnimationParameter.Type.BOOLEAN);
		equal(0, loaded.minimum);
		equal(1, loaded.maximum);
		rejected(() -> definition.validate(new float[]{Float.NaN}));

		definition.type = PhotonAnimationParameter.Type.COLOR;
		definition.minimum = 0.25f;
		definition.maximum = 0.75f;
		definition.defaults = new float[]{-1, 0.4f, 2, Float.MAX_VALUE};
		loaded = PhotonAnimationParameter.read(definition.write());
		check(loaded.type == PhotonAnimationParameter.Type.COLOR);
		equal(0.25f, loaded.effectiveMinimum());
		equal(0.75f, loaded.effectiveMaximum());
		equal(0.25f, loaded.defaults[0]);
		equal(0.4f, loaded.defaults[1]);
		equal(0.75f, loaded.defaults[2]);
		equal(0.75f, loaded.defaults[3]);
		definition.minimum = 2;
		definition.maximum = 3;
		rejected(() -> definition.validate(new float[]{1, 1, 1, 1}));
	}

	private static void rejected(Runnable operation) {
		var rejected = false;

		try {
			operation.run();
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}

		check(rejected);
	}

	private static void localOverrides() {
		var definition = new PhotonAnimationDefinition();
		var parameter = new PhotonAnimationParameter();
		parameter.name = "size";
		parameter.defaults = new float[]{2};
		definition.parameters.add(parameter);
		var local = new float[]{5};
		var first = new PhotonAnimationPlayback(definition, 20, 0, Map.of("size", local));
		var second = new PhotonAnimationPlayback(definition, 20, 1, Map.of());
		local[0] = 10;
		equal(5, first.parameter("size")[0]);
		equal(2, second.parameter("size")[0]);
		var query = first.parameter("size");
		query[0] = 99;
		equal(5, first.parameter("size")[0]);
		first.clock.advance(10);
		first.setDuration(100);
		equal(10, first.time(0));
	}

	private static void equal(double expected, double actual) {
		assertions++;

		if (!(Math.abs(expected - actual) < 0.00001)) {
			throw new AssertionError("assertion " + assertions + ": expected=" + expected + ", actual=" + actual);
		}
	}

	private static void check(boolean result) {
		assertions++;

		if (!result) {
			throw new AssertionError(assertions);
		}
	}
}
