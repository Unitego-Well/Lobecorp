package org.unitego.lobecorp.client.photon.animation.validation;

import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonChildAnimationPlayer;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonAnimationCallTrack;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 用独立宿主和假实例目录执行真实子片段桥；不发射粒子，不借用编辑器，不播放音频。
public class PhotonAnimationChildRegression {
	private int assertions;

	public static int runLoaded() {
		var checks = new PhotonAnimationChildRegression();
		checks.controlledWindow();
		checks.controlledOwnership();
		checks.independentWindow();
		checks.zeroWindow();
		checks.crossedWindow(PhotonChildAnimationClip.Mode.CONTROLLED);
		checks.crossedWindow(PhotonChildAnimationClip.Mode.INDEPENDENT);
		checks.localCalls();
		return checks.assertions;
	}

	private void controlledWindow() {
		var source = animation("parent", 100);
		var target = animation("child", 20);
		var spawner = new RoutingSpawner();
		var clip = new PhotonChildAnimationClip(2, 10, 1);
		clip.animation = target.id.toString();
		clip.completion = PhotonChildAnimationClip.Completion.HOLD;
		var input = new PhotonAnimationInput();
		input.target = Objects.requireNonNull(target.parameter("gain")).id.toString();
		input.source = Objects.requireNonNull(source.parameter("gain")).id.toString();
		input.follow = true;
		clip.inputs.add(input);
		var timeline = timeline(spawner, clip);

		try (var first = new Fixture(target);
		     var late = new Fixture(target);
		     var parent = new Fixture(source, spawner)) {
			spawner.add(first.runtime);
			var caller = parent.runtime.play(source.name, Map.of("gain", new float[]{7}));
			PhotonChildAnimationPlayer.register(parent.fx.timelinePlayer, caller);
			evaluate(parent, timeline, 1);
			check(first.runtime.playback(target.name) == null, "window does not fire before start");
			evaluate(parent, timeline, 2);
			var playback = Objects.requireNonNull(first.runtime.playback(target.name));
			equal(0, playback.clock.progress(), "window starts at zero");
			equal(7, playback.parameter("gain")[0], "child receives caller formal");
			evaluate(parent, timeline, 7);
			equal(0.5, playback.clock.progress(), "controlled child follows parent authored time");
			first.tick();
			first.tick();
			equal(0.5, playback.clock.progress(), "child tick does not advance controlled clock");
			PhotonChildAnimationPlayer.frame(parent.fx.timelinePlayer, parent.fx, timeline, 7.25);
			equal(0.525, playback.progress(0.25), "controlled frame uses parent fractional time");
			PhotonChildAnimationPlayer.pause(parent.fx.timelinePlayer, false);
			check(!playback.controlAudioPaused(), "controlled audio resumes with its parent window");
			PhotonChildAnimationPlayer.pause(parent.fx.timelinePlayer, true);
			check(playback.controlAudioPaused(), "controlled audio pauses with its parent window");
			first.tick();
			equal(0.5, playback.clock.progress(), "paused controlled child remains on the parent clock");
			caller.setParameter("gain", 9);
			equal(9, playback.parameter("gain")[0], "child input follows caller formal");
			spawner.add(late.runtime);
			evaluate(parent, timeline, 8);
			var latePlayback = Objects.requireNonNull(late.runtime.playback(target.name));
			equal(0.6, latePlayback.clock.progress(), "late child catches up inside current window");
			equal(9, latePlayback.parameter("gain")[0], "late child receives current source value");
			check(latePlayback.controlAudioPaused(), "late controlled child inherits paused parent audio");
			PhotonChildAnimationPlayer.pause(parent.fx.timelinePlayer, false);
			check(!playback.controlAudioPaused() && !latePlayback.controlAudioPaused(),
					"parent resume clears audio pause for existing and late controlled children");
			evaluate(parent, timeline, 12);
			check(playback.clock.state() == PhotonAnimationClock.State.HOLDING, "window ends with explicit HOLD");
			equal(1, playback.clock.progress(), "window retains its last frame");
			PhotonChildAnimationPlayer.forget(parent.fx.timelinePlayer);
			check(first.runtime.playback(target.name) == null, "parent cleanup releases its completed HOLD");
			check(late.runtime.playback(target.name) == null, "parent cleanup also releases its late controlled child");
		}
	}

	private void controlledOwnership() {
		var target = animation("child", 20);

		try (var child = new Fixture(target)) {
			var firstOwner = new Object();
			var nextOwner = new Object();
			var first = child.runtime.playControlled(target.name, 10, Map.of(), firstOwner);
			child.runtime.driveControlled(first, firstOwner, 0.5);
			var next = child.runtime.playControlled(target.name, 5, Map.of("gain", new float[]{3}), nextOwner);
			check(next != first && !child.runtime.isCurrent(first), "explicit controlled restart replaces active handle");
			equal(0, next.clock.progress(), "controlled restart begins with fresh progress");
			var rejected = false;

			try {
				child.runtime.driveControlled(first, firstOwner, 1);
			} catch (IllegalStateException expected) {
				rejected = true;
			}

			check(rejected, "stale controlled handle is explicitly rejected");
			equal(0, next.clock.progress(), "stale drive leaves new playback progress unchanged");
			child.runtime.completeControlled(first, firstOwner, PhotonAnimationDefinition.Completion.RELEASE);
			child.runtime.stop(first);
			check(child.runtime.playback(target.name) == next, "stale owner cannot drive stop or complete new playback");
			equal(3, next.parameter("gain")[0], "new owner keeps its own inputs");
			child.runtime.completeControlled(next, nextOwner, PhotonAnimationDefinition.Completion.HOLD);
			var held = child.runtime.playControlled(target.name, 5, Map.of(), firstOwner);
			check(held != next && !child.runtime.isCurrent(next), "explicit restart also replaces completed HOLD");
			var ordinary = child.runtime.play(target.name, 8, true, Map.of("gain", new float[]{4}));
			check(!ordinary.controlled() && !child.runtime.isCurrent(held), "ordinary restart replaces active controlled handle");
			child.runtime.stop(held);
			check(child.runtime.playback(target.name) == ordinary, "old controlled handle cannot stop ordinary restart");
			child.runtime.stop(ordinary);
			check(child.runtime.playback(target.name) == null, "current owner handle can stop its playback");
		}
	}

	private void independentWindow() {
		var source = animation("parent", 100);
		var target = animation("child", 20);
		var spawner = new RoutingSpawner();
		var clip = new PhotonChildAnimationClip(0, 2, 1);
		clip.animation = target.id.toString();
		clip.mode = PhotonChildAnimationClip.Mode.INDEPENDENT;
		clip.completion = PhotonChildAnimationClip.Completion.RELEASE;
		clip.playbackDuration = 40;
		var input = new PhotonAnimationInput();
		input.target = Objects.requireNonNull(target.parameter("gain")).id.toString();
		input.value = new float[]{7};
		clip.inputs.add(input);
		var timeline = timeline(spawner, clip);

		try (var child = new Fixture(target);
		     var parent = new Fixture(source, spawner)) {
			spawner.add(child.runtime);
			evaluate(parent, timeline, 0);
			var playback = Objects.requireNonNull(child.runtime.playback(target.name));
			check(!playback.controlled(), "independent window uses child clock");
			check(playback.completion() == target.completion,
					"independent playback keeps child completion despite controlled-only override");
			PhotonChildAnimationPlayer.pause(parent.fx.timelinePlayer, true);
			check(!playback.controlAudioPaused(), "independent playback is not audio-paused by parent window");
			equal(40, playback.clock.duration(), "independent playback duration override");
			equal(7, playback.parameter("gain")[0], "independent fixed input");
			evaluate(parent, timeline, 1);
			check(child.runtime.playback(target.name) == playback, "same window fires each instance once");
			equal(0, playback.clock.progress(), "parent clock does not drive independent progress");
			child.tick();
			child.tick();
			var position = playback.clock.position();
			evaluate(parent, timeline, 2);
			PhotonChildAnimationPlayer.forget(parent.fx.timelinePlayer);
			check(child.runtime.playback(target.name) == playback, "independent playback survives window and parent cleanup");
			child.tick();
			check(playback.clock.position() > position, "independent child continues on its own clock");
			evaluate(parent, timeline, 0);
			var restarted = Objects.requireNonNull(child.runtime.playback(target.name));
			check(restarted != playback, "new parent pass restarts target using a new window");
			PhotonChildAnimationPlayer.forget(parent.fx.timelinePlayer);
		}
	}

	private void zeroWindow() {
		var source = animation("parent", 100);
		var target = animation("child", 20);
		var spawner = new RoutingSpawner();
		var clip = new PhotonChildAnimationClip(2, 0, 1);
		clip.animation = target.id.toString();
		clip.completion = PhotonChildAnimationClip.Completion.HOLD;
		var timeline = timeline(spawner, clip);

		try (var child = new Fixture(target);
		     var parent = new Fixture(source, spawner)) {
			spawner.add(child.runtime);
			evaluate(parent, timeline, 2);
			var playback = Objects.requireNonNull(child.runtime.playback(target.name));
			equal(1, playback.clock.progress(), "zero-length window completes with finite progress");
			evaluate(parent, timeline, 2);
			check(child.runtime.playback(target.name) == playback, "zero-length window fires only once");
			PhotonChildAnimationPlayer.forget(parent.fx.timelinePlayer);
		}
	}

	private void crossedWindow(PhotonChildAnimationClip.Mode mode) {
		var source = animation("parent", 100);
		var target = animation("child", 20);
		var spawner = new RoutingSpawner();
		var clip = new PhotonChildAnimationClip(2, 0.25, 1);
		clip.animation = target.id.toString();
		clip.mode = mode;
		clip.completion = PhotonChildAnimationClip.Completion.HOLD;
		clip.playbackDuration = 40;
		var timeline = timeline(spawner, clip);

		try (var first = new Fixture(target);
		     var late = new Fixture(target);
		     var parent = new Fixture(source, spawner)) {
			spawner.add(first.runtime);
			evaluate(parent, timeline, 1);
			check(first.runtime.playback(target.name) == null, "crossed window remains idle before start");
			spawner.add(late.runtime);
			evaluate(parent, timeline, 4);
			var playback = Objects.requireNonNull(first.runtime.playback(target.name));
			var latePlayback = Objects.requireNonNull(late.runtime.playback(target.name));
			var controlled = mode == PhotonChildAnimationClip.Mode.CONTROLLED;
			check(playback.controlled() == controlled && latePlayback.controlled() == controlled,
					"crossed window keeps its configured playback mode");
			equal(controlled ? 1 : 0, playback.clock.progress(), "crossed window preserves mode-specific progress");
			equal(controlled ? 1 : 0, latePlayback.clock.progress(), "crossed window also discovers late instances");
			equal(controlled ? clip.duration() : clip.playbackDuration, playback.clock.duration(),
					"crossed window keeps controlled or independent duration");
			evaluate(parent, timeline, 4);
			check(first.runtime.playback(target.name) == playback && late.runtime.playback(target.name) == latePlayback,
					"same-time sample does not restart a completed crossed window");
			PhotonChildAnimationPlayer.forget(parent.fx.timelinePlayer);

			if (controlled) {
				check(first.runtime.playback(target.name) == null && late.runtime.playback(target.name) == null,
						"parent cleanup releases both crossed controlled contributions");
				return;
			}

			check(first.runtime.playback(target.name) == playback && late.runtime.playback(target.name) == latePlayback,
					"parent cleanup leaves independent crossed playbacks running");
			first.tick();
			first.tick();
			check(playback.clock.position() > 0, "independent crossed playback advances on its own clock");
		}
	}

	private void localCalls() {
		var controlled = animation("controlled", 20);
		var independent = animation("independent", 20);
		var source = animation("test", 100);
		var call = new PhotonAnimationCallTrack();
		call.setType(LcPhotonAnimationTypes.ANIMATION_CALL);
		var controlledClip = new PhotonChildAnimationClip(0, 4, 1);
		controlledClip.animation = controlled.id.toString();
		controlledClip.completion = PhotonChildAnimationClip.Completion.HOLD;
		var independentClip = new PhotonChildAnimationClip(1, 0.25, 1);
		independentClip.animation = independent.id.toString();
		independentClip.mode = PhotonChildAnimationClip.Mode.INDEPENDENT;
		independentClip.playbackDuration = 40;
		var input = new PhotonAnimationInput();
		input.target = Objects.requireNonNull(independent.parameter("gain")).id.toString();
		input.source = Objects.requireNonNull(source.parameter("gain")).id.toString();
		input.follow = true;
		independentClip.inputs.add(input);
		call.clips().addAll(List.of(controlledClip, independentClip));
		source.tracks.add(call);

		try (var fixture = new LocalFixture(controlled, independent)) {
			fixture.runtime.playPreview(source, 0);
			var caller = Objects.requireNonNull(fixture.runtime.playback(source.id.toString()));
			var first = Objects.requireNonNull(fixture.runtime.playback(controlled.name));
			check(fixture.runtime.isTestTimeline(caller), "project-only preview is a valid local call source");
			check(first.controlled(), "local controlled call uses the same runtime and owned playback");
			equal(0, first.clock.progress(), "zero-time local call starts during preview installation");
			fixture.tick();
			var second = Objects.requireNonNull(fixture.runtime.playback(independent.name));
			check(fixture.runtime.playback(controlled.name) == first && !second.controlled(),
					"local independent call runs alongside the existing controlled animation");
			equal(0.25, first.clock.progress(), "local controlled progress follows the test timeline");
			equal(40, second.clock.duration(), "local independent call retains its configured duration");
			caller.setParameter("gain", 9);
			equal(9, second.parameter("gain")[0], "local call follows the test timeline formal");
			fixture.tick();
			check(fixture.runtime.playback(controlled.name) == first && fixture.runtime.playback(independent.name) == second,
					"local calls do not restart when the test timeline advances beyond a short clip");
			fixture.runtime.stop(caller);
			check(fixture.runtime.playback(controlled.name) == null, "stopping the test timeline releases its controlled local contribution");
			check(fixture.runtime.playback(independent.name) == second, "stopping the test timeline keeps independent local animation");
			equal(9, second.parameter("gain")[0], "stopped local source freezes followed formals");
			var position = second.clock.position();
			fixture.tick();
			check(second.clock.position() > position, "independent local animation continues after its source stops");
			check(fixture.runtime.problems().isEmpty(), "local calls install and release without runtime problems");
		}
	}

	private static PhotonAnimationDefinition animation(String name, double duration) {
		var definition = new PhotonAnimationDefinition();
		definition.name = name;
		definition.autoDuration = false;
		definition.duration = duration;
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		parameter.defaults = new float[]{2};
		definition.parameters.add(parameter);
		return definition;
	}

	private static Timeline timeline(RoutingSpawner spawner, PhotonChildAnimationClip clip) {
		var timeline = new Timeline();
		var track = new PhotonChildAnimationTrack();
		track.targetId(spawner.id());
		track.clips().add(clip);
		timeline.tracks().add(track);
		return timeline;
	}

	private static void evaluate(Fixture parent, Timeline timeline, double time) {
		PhotonChildAnimationPlayer.evaluate(parent.fx.timelinePlayer, parent.fx, timeline, time);
	}

	private void equal(double expected, double actual, String name) {
		check(Double.isFinite(actual) && Math.abs(expected - actual) < 0.00001, name);
	}

	private void check(boolean value, String name) {
		assertions++;

		if (!value) {
			throw new AssertionError(name);
		}
	}

	private static class Fixture implements AutoCloseable {
		private final FXRuntime fx;
		private final PhotonAnimationRuntime runtime;

		private Fixture(PhotonAnimationDefinition definition, IFXObject... objects) {
			var library = new PhotonAnimationLibrary();
			library.animations.add(definition);
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
				PhotonChildAnimationPlayer.forget(fx.timelinePlayer);
				runtime.close();
			} finally {
				fx.destroy(true);
			}
		}
	}

	/// 本特效调用使用宿主真正持有的运行态，专门覆盖预览采样期间的重入安装与释放。
	private static class LocalFixture implements AutoCloseable {
		private final FXRuntime fx;
		private final @NotNull PhotonAnimationRuntime runtime;
		private final LocalEffect effect = new LocalEffect();

		private LocalFixture(PhotonAnimationDefinition... definitions) {
			var library = new PhotonAnimationLibrary();
			library.animations.addAll(List.of(definitions));
			var controller = new PhotonAnimationController();
			controller.library(library);
			fx = new FXRuntime(new FXData(List.of(controller)));
			fx.timelinePlayer.setAudioDispatch(false);
			fx.getRoot().setEffect(effect);
			@SuppressWarnings("DataFlowIssue") // 通过已加载的 Mixin 建立宿主字段，不并存第二运行态。
			var access = (PhotonAnimationRuntimeAccess) fx;
			access.lobecorp$reloadAnimations();
			runtime = Objects.requireNonNull(access.lobecorp$animations());
			runtime.previewOnly();
		}

		private void tick() {
			Objects.requireNonNull(((FXObject) fx.getRoot()).getOnUpdateTick()).run();
		}

		@Override
		public void close() {
			try {
				fx.destroy(true);
			} finally {
				effect.stack.onFrameEnd();
			}
		}
	}

	private static class LocalEffect implements IEffectExecutor {
		private final RandomSource random = RandomSource.create(1);
		private final PostEffectStack stack = new PostEffectStack();

		@Override
		public Level getLevel() {
			return null;
		}

		@Override
		public RandomSource getRandomSource() {
			return random;
		}

		@Override
		public PostEffectStack postEffectSink() {
			return stack;
		}
	}

	/// 仅隔离发射器的已存活实例目录，所有调用仍经过实际片段桥和子运行态。
	private static class RoutingSpawner extends PhotonEmitterSpawner {
		private final Map<UUID, PhotonAnimationRuntime> children = new LinkedHashMap<>();

		private void add(PhotonAnimationRuntime runtime) {
			children.put(UUID.randomUUID(), runtime);
		}

		@Override
		public List<UUID> instanceHandles() {
			return List.copyOf(children.keySet());
		}

		@Override
		public @Nullable PhotonAnimationRuntime instanceAnimations(@NotNull UUID handle) {
			return children.get(handle);
		}

		@Override
		public int emitNow(int count) {
			throw new AssertionError("Child animation calls must not emit new instances");
		}
	}
}
