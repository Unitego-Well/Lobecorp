package org.unitego.lobecorp.client.photon.animation.validation;

import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.CurveClip;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.RotationPropertyType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationContribution;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationMixer;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// 模组加载后使用独立数据和运行槽检查启动容错、循环函数与重复合成，不访问编辑器或正在运行的特效。
/// 夹具关闭自己的音频派发，不覆盖真实音频播放，也不依赖客户端声音管理器。
public class PhotonAnimationRuntimeRegression {
	private int assertions;

	/// 需要 NeoForge 完成模组加载；函数贡献及运行槽使用各自新建的检查数据。
	public static int runLoaded() {
		var checks = new PhotonAnimationRuntimeRegression();
		checks.functionContributions();
		checks.defaultAnimation();
		checks.curveClipRotation();
		checks.scaledRotation();
		checks.repeatedComposition();
		checks.compositionTriggers();
		checks.childParameters();
		return checks.assertions + PhotonAnimationChildRegression.runLoaded();
	}

	private void functionContributions() {
		var first = new SampleFunction(10);
		var third = new SampleFunction(20);
		var functions = new NumberFunction[]{first, NumberFunction.constant(7), third};
		var offsets = new float[]{20, 0, -10};
		var contribution = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.OVERRIDE, -1,
				new float[]{0, 0, 0}, functions, false, 2, 3, offsets);
		functions[0] = NumberFunction.constant(99);
		offsets[0] = 99;
		Supplier<Float> random = () -> 0.5f;
		equal(49, contribution.apply(100, 0, 0.25f, random), "function offset before scale");
		equal(0.25, first.lastTime, "particle lifetime time preserved");
		check(first.lastRandom == random, "particle random supplier preserved");
		equal(17, contribution.apply(100, 1, 0.25f, random), "unshifted channel");
		equal(-6, contribution.apply(100, 2, 0.25f, random), "negative independent channel offset");
		Supplier<Float> laterRandom = () -> 0.25f;
		equal(58.5, contribution.apply(100, 0, 0.75f, laterRandom), "later particle lifetime and random");
		equal(0.75, first.lastTime, "absolute lifetime time is not animation time");
		check(first.lastRandom == laterRandom, "later random supplier preserved");

		var unshifted = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.OVERRIDE, -1,
				new float[]{0}, new NumberFunction[]{first}, false, 2, 3);
		equal(9, unshifted.apply(100, 0, 0.25f, random), "existing constructor keeps zero cycle offset");
		var selected = new PhotonAnimationContribution(PhotonAnimationBinding.Blend.ADD, 2,
				new float[]{0, 0, 0}, new NumberFunction[]{first, first, third}, false, 2, 3,
				new float[]{20, 0, -10});
		equal(100, selected.apply(100, 0, 0.25f, random), "component selection remains isolated");
		equal(94, selected.apply(100, 2, 0.25f, random), "component selection keeps additive blend");
	}

	private void defaultAnimation() {
		var target = animation("startup", 20);
		var library = parameters(target);
		var start = new PhotonAnimationRule();
		start.event = PhotonAnimationRule.Event.START;
		start.target = target.id.toString();
		start.inputs.add(input(Objects.requireNonNull(target.parameter("gain")), null, 7));
		library.rules.add(start);
		library.defaultAnimation = UUID.randomUUID().toString();
		var controller = new PhotonAnimationController();
		controller.library(library);
		var stored = controller.definition.copy();
		var fx = new FXRuntime(new FXData(List.of(controller)));

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			var originalTick = root.getOnUpdateTick();

			try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
				Objects.requireNonNull(root.getOnUpdateTick()).run();
				var playback = Objects.requireNonNull(runtime.playback(target.name));
				equal(7, playback.parameter("gain")[0], "invalid default still executes START");
				check(runtime.problems().contains(library.defaultAnimation), "invalid default reference reported");
				check(controller.definition.equals(stored), "invalid default preserves saved definition");
				runtime.setParameter(playback, "gain", 3);
				root.getOnUpdateTick().run();
				check(runtime.playback(target.name) == playback, "START does not restart after invalid default");
				equal(3, playback.parameter("gain")[0], "START is not repeated after invalid default");
			}

			check(root.getOnUpdateTick() == originalTick, "startup fixture restores original callback");
		} finally {
			fx.destroy(true);
		}
	}

	private void curveClipRotation() {
		var emitter = new ParticleEmitter();
		var fx = new FXRuntime(new FXData(List.of(emitter)));

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var slot = emitter.runtime().startRotation;
			var original = slot.get();
			var definition = new PhotonAnimationDefinition();
			definition.loop = true;
			definition.duration = 20;
			var first = new SampleFunction(10);
			var third = new SampleFunction(20);
			var property = rotationProperty(emitter, first, third);
			var track = new AnimationTrack();
			track.targetId(emitter.id());
			track.properties().add(property);
			definition.tracks.add(track);
			var binding = definition.binding(emitter.id(), property.type().path());
			binding.scale = 2;
			binding.offset = 3;
			var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
			var mixer = new PhotonAnimationMixer(fx, problem -> {
				throw new AssertionError(problem);
			});
			Supplier<Float> random = () -> 0.5f;

			try {
				mixer.apply(List.of(playback), 0, 0);
				var initial = slot.get().get(0.25f, random);
				equal(9, initial.x, "CurveClip zero cycle x");
				equal(7, initial.y, "plain channel zero cycle y");
				equal(14, initial.z, "CurveClip zero cycle z");
				playback.clock.advance(40);
				mixer.apply(List.of(playback), 40, 0);
				var later = slot.get().get(0.25f, random);
				equal(129, later.x, "CurveClip two cycles positive x");
				equal(23, later.y, "plain channel two cycles is shifted once");
				equal(-26, later.z, "CurveClip two cycles negative z");
				equal(0.25, first.lastTime, "mixer preserves particle lifetime x");
				equal(0.25, third.lastTime, "mixer preserves particle lifetime z");
				check(first.lastRandom == random && third.lastRandom == random, "mixer preserves particle random suppliers");
				check(property.curveClips(0).getFirst().curve() == first, "authored x function is unchanged");
				check(property.curveClips(2).getFirst().curve() == third, "authored z function is unchanged");
				mixer.apply(List.of(), 40, 0);
				check(!slot.isOverridden() && slot.get() == original, "stopping contributions restores original slot");
				check(emitter.config.getStartRotation() == original, "authored emitter config is unchanged");
			} finally {
				mixer.clear();
			}
		} finally {
			fx.destroy(true);
		}
	}

	private void scaledRotation() {
		var emitter = new ParticleEmitter();
		var fx = new FXRuntime(new FXData(List.of(emitter)));
		var mixer = new PhotonAnimationMixer(fx, problem -> {
			throw new AssertionError(problem);
		});

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var definition = new PhotonAnimationDefinition();
			definition.loop = true;
			definition.autoDuration = false;
			definition.duration = 20;
			var parameter = new PhotonAnimationParameter();
			parameter.name = "angle_scale";
			parameter.defaults = new float[]{0.5f};
			definition.parameters.add(parameter);
			var property = RotationPropertyType.INSTANCE.create(emitter);
			property.putKey(2, 0, 0);
			property.putKey(2, 20, 360);
			var track = new AnimationTrack();
			track.targetId(emitter.id());
			track.properties().add(property);
			definition.tracks.add(track);
			var binding = definition.binding(emitter.id(), property.type().path());
			binding.component = 2;
			binding.scaleParameter = parameter.id.toString();
			var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
			playback.advance(15);
			mixer.apply(List.of(playback), 15, 0);
			equal(135, mixer.query(emitter.id(), property.type().path(), true)[2],
					"angular scale uses the authored 270 degrees before quaternion wrap");
			playback.advance(20);
			mixer.apply(List.of(playback), 35, 0);
			equal(315, mixer.query(emitter.id(), property.type().path(), true)[2],
					"angular scale retains the accumulated cycle before scaling");
			mixer.apply(List.of(playback), 35, 0);
			equal(315, mixer.query(emitter.id(), property.type().path(), true)[2],
					"repeating scaled angular sample does not accumulate");
		} finally {
			mixer.clear();
			fx.destroy(true);
		}
	}

	private void repeatedComposition() {
		var emitter = new ParticleEmitter();
		emitter.config.rotationOverLifetime.setRoll(NumberFunction.constant(7));
		emitter.config.rotationOverLifetime.setEnable(false);
		var fx = new FXRuntime(new FXData(List.of(emitter)));

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var module = emitter.runtime().rotationOverLifetime;
			var slot = module.roll;
			var original = slot.get();
			var additive = scalarPlayback(emitter, PhotonAnimationBinding.Blend.ADD, 2, 0);
			var multiplier = scalarPlayback(emitter, PhotonAnimationBinding.Blend.MULTIPLY, 3, 1);
			var combined = List.of(additive, multiplier);
			var mixer = new PhotonAnimationMixer(fx, problem -> {
				throw new AssertionError(problem);
			});

			try {
				mixer.apply(List.of(additive), 0, 0);
				equal(9, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "first additive contribution");
				check(module.isEnable() && module.enable.isOverridden(), "contribution enables runtime module");
				mixer.apply(List.of(additive), 0, 0);
				equal(9, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "repeated additive does not accumulate");
				equal(7, mixer.query(emitter.id(), "config.rotationOverLifetime.roll", false)[0], "repeated apply keeps base value");
				equal(9, mixer.query(emitter.id(), "config.rotationOverLifetime.roll", true)[0], "repeated apply keeps composed value");
				mixer.apply(List.of(), 0, 0);
				check(!slot.isOverridden() && slot.get() == original, "empty apply immediately restores original function");
				check(!module.isEnable() && !module.enable.isOverridden(), "empty apply restores disabled module");

				mixer.apply(List.of(multiplier), 0, 0);
				equal(21, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "first multiplier contribution");
				mixer.apply(List.of(multiplier), 0, 0);
				equal(21, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "repeated multiplier does not accumulate");
				mixer.apply(combined, 0, 0);
				equal(27, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "mixed contributions use original base");
				mixer.apply(combined, 0, 0);
				equal(27, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "repeated mixed contributions do not accumulate");
				mixer.apply(List.of(multiplier), 0, 0);
				equal(21, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "removing additive retains multiplier contribution");
				mixer.apply(List.of(), 0, 0);
				check(!slot.isOverridden() && slot.get() == original, "removing all mixed contributions restores original function");
				check(!module.isEnable() && !module.enable.isOverridden(), "removing all contributions restores module enable");

				externalContributions(emitter, mixer, combined);
				check(emitter.config.rotationOverLifetime.getRoll() == original && !emitter.config.rotationOverLifetime.isEnable(),
						"repeated composition leaves authored config unchanged");
			} finally {
				mixer.clear();
			}
		} finally {
			fx.destroy(true);
		}
	}

	private void externalContributions(ParticleEmitter emitter, PhotonAnimationMixer mixer, List<PhotonAnimationPlayback> combined) {
		var module = emitter.runtime().rotationOverLifetime;
		var slot = module.roll;
		var external = NumberFunction.constant(11);
		slot.set(external);
		mixer.apply(combined, 0, 0);
		equal(39, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "composition uses external base");
		mixer.apply(combined, 0, 0);
		equal(39, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "repeated composition uses external base once");

		var later = NumberFunction.constant(13);
		slot.set(later);
		module.enable.set(false);
		mixer.apply(combined, 0, 0);
		equal(45, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "next apply uses later external override");
		equal(13, mixer.query(emitter.id(), "config.rotationOverLifetime.roll", false)[0], "query sees later external base");
		check(module.isEnable(), "active contribution temporarily enables externally disabled module");
		mixer.release();
		check(slot.get() == later && slot.isOverridden(), "release restores external function identity");
		check(!module.isEnable() && module.enable.isOverridden(), "release preserves external disabled override");

		mixer.apply(combined, 0, 0);
		equal(45, slot.get().get(0.5f, () -> 0.5f).doubleValue(), "apply after release keeps external base");
		var latest = NumberFunction.constant(17);
		slot.set(latest);
		module.enable.set(false);
		mixer.release();
		check(slot.get() == latest && slot.isOverridden(), "release leaves later external function untouched");
		check(!module.isEnable() && module.enable.isOverridden(), "release leaves later external module override untouched");
		mixer.apply(List.of(), 0, 0);
		check(slot.get() == latest && slot.isOverridden(), "empty apply leaves external function untouched");
		check(!module.isEnable() && module.enable.isOverridden(), "empty apply leaves external module override untouched");
	}


	private void compositionTriggers() {
		var source = animation("source", 2);
		source.completion = PhotonAnimationDefinition.Completion.RELEASE;
		var target = animation("target", 20);
		var library = parameters(source, target);
		target.durationParameter = Objects.requireNonNull(target.parameter("span")).id.toString();
		callRule(library, source, target, PhotonAnimationRule.Event.SIGNAL, "launch");
		callRule(library, source, target, PhotonAnimationRule.Event.COMPLETE, "");

		try (var fixture = new RuntimeFixture(library)) {
			var runtime = fixture.runtime;
			var local = new float[]{7};
			var sourcePlayback = runtime.play(source.name, -1, true, Map.of("gain", local, "span", new float[]{40}));
			local[0] = 99;
			runtime.signal(source.id.toString(), "", "launch", new CompoundTag());
			fixture.tick();
			var first = Objects.requireNonNull(runtime.playback(target.name));
			equal(40, first.clock.duration(), "trigger reads source duration formal");
			equal(7, first.parameter("gain")[0], "trigger copies source local formal");
			equal(7, sourcePlayback.parameter("gain")[0], "call inputs are isolated from caller arrays");
			runtime.setParameter(sourcePlayback, "gain", 3);
			equal(7, first.parameter("gain")[0], "snapshot input does not follow later source writes");
			fixture.tick();
			fixture.tick();
			check(runtime.playback(source.name) == null, "COMPLETE fixture releases its source");
			var current = Objects.requireNonNull(runtime.playback(target.name));
			check(current != first, "COMPLETE restarts only its target");
			equal(40, current.clock.duration(), "released source retains duration snapshot");
			equal(3, current.parameter("gain")[0], "COMPLETE reads released source final formal");
			check(!runtime.isCurrent(first), "replacement invalidates old target handle");
			current.clock.advance(20);
			current.clock.pause();
			var values = new float[]{9};
			var continued = runtime.play(target.name, 80, false, Map.of("gain", values));
			values[0] = 99;
			check(continued == current, "continue retains playback instance");
			equal(0.5, current.clock.position(), "continue retiming preserves half progress");
			equal(80, current.clock.duration(), "continue updates duration");
			equal(9, current.parameter("gain")[0], "continue copies updated input");
			check(current.clock.state() == PhotonAnimationClock.State.PLAYING, "continue resumes after validation");
			atomicParameters(runtime, current);
		}
	}

	private void atomicParameters(PhotonAnimationRuntime runtime, PhotonAnimationPlayback playback) {
		boolean rejected = false;

		try {
			runtime.play(playback.definition.name, 60, false, Map.of("gain", new float[]{1, 2}));
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}

		check(rejected, "continue rejects invalid local input");
		equal(80, playback.clock.duration(), "rejected continue preserves duration");
		equal(0.5, playback.clock.position(), "rejected continue preserves progress");
		equal(9, playback.parameter("gain")[0], "rejected continue preserves formal");
		var values = new LinkedHashMap<String, float[]>();
		values.put("result", new float[]{5});
		values.put("gain", new float[]{1, 2});
		rejected = false;

		try {
			runtime.parameters(playback, values);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}

		check(rejected, "batch formals reject invalid input");
		equal(2, playback.parameter("result")[0], "rejected batch leaves earlier valid formal untouched");
		equal(9, playback.parameter("gain")[0], "rejected batch leaves other formal untouched");
	}

	private void childParameters() {
		var source = animation("composition", 100);
		var target = animation("child", 20);
		var parentLibrary = parameters(source);
		var childLibrary = parameters(target);
		target.durationParameter = Objects.requireNonNull(target.parameter("span")).id.toString();

		try (var first = new RuntimeFixture(childLibrary);
		     var second = new RuntimeFixture(childLibrary);
		     var parent = new RuntimeFixture(parentLibrary)) {
			var caller = parent.runtime.play(source.name, Map.of("gain", new float[]{7}, "span", new float[]{40}));
			var snapshot = first.runtime.play(target.name, caller.parameters().entrySet().stream()
					.collect(Collectors.toMap(entry -> Objects.requireNonNull(source.parameter(entry.getKey())).name, Map.Entry::getValue)));
			var following = second.runtime.play(target.name);
			following.follow("gain", caller, "gain");
			following.follow("span", caller, "span");
			equal(40, snapshot.clock.duration(), "snapshot duration is copied");
			equal(40, following.clock.duration(), "following duration is initialized");
			caller.setParameters(Map.of("gain", new float[]{9}, "span", new float[]{80}));
			equal(7, snapshot.parameter("gain")[0], "snapshot does not follow caller");
			equal(9, following.parameter("gain")[0], "following sees caller current formal");
			equal(80, following.clock.duration(), "following duration is retimed");
			equal(0, snapshot.clock.position(), "independent child clocks do not advance with parent writes");
			var copied = following.parameters();
			copied.get(Objects.requireNonNull(target.parameter("gain")).id.toString())[0] = 99;
			equal(9, following.parameter("gain")[0], "formal query returns deep copies");
			parent.runtime.stop(source.name);
			equal(9, following.parameter("gain")[0], "stopped source freezes last followed value");
			check(first.runtime.playback(target.name) == snapshot && second.runtime.playback(target.name) == following,
					"stopping parent preserves independent child playbacks");
			parent.runtime.play(source.name, Map.of("gain", new float[]{3}));
			equal(9, following.parameter("gain")[0], "new caller does not inherit old follow ownership");
			following.setParameter("gain", 5);
			equal(5, following.parameter("gain")[0], "explicit write releases a follow binding");
		}
	}

	private PhotonAnimationDefinition animation(String name, double duration) {
		var definition = new PhotonAnimationDefinition();
		definition.name = name;
		definition.autoDuration = false;
		definition.duration = duration;
		return definition;
	}

	private PhotonAnimationLibrary parameters(PhotonAnimationDefinition... definitions) {
		var library = new PhotonAnimationLibrary();
		library.animations.addAll(List.of(definitions));

		for (var definition : definitions) {
			for (var name : List.of("span", "gain", "result")) {
				var parameter = new PhotonAnimationParameter();
				parameter.name = name;
				parameter.defaults = new float[]{name.equals("span") ? 20 : 2};
				definition.parameters.add(parameter);
			}
		}

		return library;
	}

	private PhotonAnimationInput input(PhotonAnimationParameter target, @Nullable PhotonAnimationParameter source, float... value) {
		var input = new PhotonAnimationInput();
		input.target = target.id.toString();
		input.source = source == null ? "" : source.id.toString();
		input.value = value.clone();
		return input;
	}

	private void callRule(PhotonAnimationLibrary library, PhotonAnimationDefinition source,
	                      PhotonAnimationDefinition target, PhotonAnimationRule.Event event, String name) {
		var rule = new PhotonAnimationRule();
		rule.animation = source.id.toString();
		rule.event = event;
		rule.name = name;
		rule.target = target.id.toString();
		rule.inputs.add(input(Objects.requireNonNull(target.parameter("gain")), Objects.requireNonNull(source.parameter("gain")), 0));
		rule.inputs.add(input(Objects.requireNonNull(target.parameter("span")), Objects.requireNonNull(source.parameter("span")), 20));
		library.rules.add(rule);
	}

	private PhotonAnimationPlayback scalarPlayback(ParticleEmitter emitter, PhotonAnimationBinding.Blend blend, float value, long order) {
		var definition = new PhotonAnimationDefinition();
		definition.autoDuration = false;
		definition.autoEnableModules = true;
		var type = emitter.getFXObjectType().animatableProperties(emitter).stream()
				.filter(candidate -> candidate.path().equals("config.rotationOverLifetime.roll")).findFirst().orElseThrow();
		var property = type.create(emitter);
		property.putKey(0, 0, value);
		var track = new AnimationTrack();
		track.targetId(emitter.id());
		track.properties().add(property);
		definition.tracks.add(track);
		definition.binding(emitter.id(), type.path()).blend = blend;
		return new PhotonAnimationPlayback(definition, -1, order, Map.of());
	}

	private ConfigAnimatedProperty rotationProperty(ParticleEmitter emitter, SampleFunction first, SampleFunction third) {
		var type = emitter.getFXObjectType().animatableProperties(emitter).stream()
				.filter(value -> value.path().equals("config.startRotation")).findFirst().orElseThrow();
		var property = (ConfigAnimatedProperty) type.create(emitter);
		var start = new float[]{10, 2, 5};
		var end = new float[]{40, 6, -5};

		for (var axis = 0; axis < start.length; axis++) {
			property.putKey(axis, 0, start[axis]);
			property.putKey(axis, 20, end[axis]);
		}

		property.curveClips(0).add(new CurveClip(0, 20, first));
		property.curveClips(2).add(new CurveClip(0, 20, third));
		return property;
	}

	private void equal(double expected, double actual, String name) {
		check(Math.abs(expected - actual) < 0.00001, name + ": expected=" + expected + ", actual=" + actual);
	}

	private void check(boolean condition, String name) {
		assertions++;

		if (!condition) {
			throw new AssertionError(name);
		}
	}


	/// 独立运行夹具只模拟对象回调，不 emit 粒子、不访问音频或当前工作区。
	private static class RuntimeFixture implements AutoCloseable {
		private final FXRuntime fx;
		private final PhotonAnimationRuntime runtime;

		private RuntimeFixture(PhotonAnimationLibrary library, IFXObject... objects) {
			var controller = new PhotonAnimationController();
			controller.library(library);
			var values = new ArrayList<>(List.of(objects));
			values.add(controller);
			fx = new FXRuntime(new FXData(values));
			fx.timelinePlayer.setAudioDispatch(false);

			try {
				runtime = new PhotonAnimationRuntime(fx, controller);
			} catch (RuntimeException failure) {
				fx.destroy(true);
				throw failure;
			}
		}

		private void tick() {
			Objects.requireNonNull(((FXObject) fx.getRoot()).getOnUpdateTick()).run();
		}

		@Override
		public void close() {
			try {
				runtime.close();
			} finally {
				fx.destroy(true);
			}
		}
	}

	private static class SampleFunction implements NumberFunction {
		private final float multiplier;
		private float lastTime;
		private Supplier<Float> lastRandom;

		private SampleFunction(float multiplier) {
			this.multiplier = multiplier;
		}

		@Override
		public Number get(float time, Supplier<Float> random) {
			lastTime = time;
			lastRandom = random;
			return time * multiplier + random.get();
		}

		@Override
		public void loadConfig(NumberFunctionConfig config) {
		}

		@Override
		public NumberFunction copy() {
			return new SampleFunction(multiplier);
		}

		@Override
		public void createConfigurator(NumberFunctionConfigurator configurator) {
		}
	}
}
