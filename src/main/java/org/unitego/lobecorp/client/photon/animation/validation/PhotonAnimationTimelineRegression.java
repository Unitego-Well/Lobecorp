package org.unitego.lobecorp.client.photon.animation.validation;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.configurator.SerializableRecordAction;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.CurveClip;
import com.lowdragmc.photon.client.fx.timeline.ExprClip;
import com.lowdragmc.photon.client.fx.timeline.GradientClip;
import com.lowdragmc.photon.client.fx.timeline.PostProcessClip;
import com.lowdragmc.photon.client.fx.timeline.Signal;
import com.lowdragmc.photon.client.fx.timeline.PhotonSignals;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.fx.timeline.property.PositionPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.RotationAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.RotationPropertyType;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationMixer;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPlayerAccess;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTimelineModel;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationNameUtil;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorOptions;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorTrackAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonActivatorTimelineUtil;
import org.unitego.lobecorp.util.photon.runtime.PhotonTimelineTraversalUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// 原生 TL 与命名动画的隔离对照夹具；不借用编辑工作区，不读取世界，不播放音频或执行 GPU 后处理。
public class PhotonAnimationTimelineRegression {
	private int assertions;

	/// 需完成 NeoForge 加载；每次调用创建独立模型、特效、控制器和后处理队列。
	public static int runLoaded() {
		var checks = new PhotonAnimationTimelineRegression();
		checks.modelIsolation();
		checks.nameScope();
		checks.trackRoundTrip();
		checks.childTrackRoundTrip();
		checks.activationOptions();
		checks.activationEntries();
		checks.versionMigration();
		checks.durationPolicy();
		checks.recordingFreeze(false);
		checks.recordingFreeze(true);
		checks.historySnapshots(false, false);
		checks.historySnapshots(true, false);
		checks.historySnapshots(false, true);
		checks.historySnapshots(true, true);
		checks.nativePlaybackChecks();
		checks.scenePreviewGate();
		checks.sceneTimelineBase();
		return checks.assertions + PhotonAnimationMigrationRegression.runLoaded();
	}

	private void nativePlaybackChecks() {
		propertyParity();
		rotationOwnership();
		controlAndSignalParity();
		pointActivation();
		endPointActivation(PhotonActivatorOptions.EndBehavior.ORIGINAL);
		endPointActivation(PhotonActivatorOptions.EndBehavior.STOP_EMISSION);
		endPointActivation(PhotonActivatorOptions.EndBehavior.CLEAR);
		pointReleaseNativePriority(false);
		pointReleaseNativePriority(true);
		pointCommands();
		disabledActivation();
		crossedControlAndSignals();
		postProcessFrames();
		tickEvaluation();
		silentRelease();
		quietRelease();
		quietSignalDispatch();
		finishEvaluation(false);
		finishEvaluation(true);
		fractionalPlayback();
	}

	private void modelIsolation() {
		var original = new FXData();
		var originalTrack = track("signal");
		original.timeline().tracks().add(originalTrack);
		var definition = new PhotonAnimationDefinition();
		definition.name = "first";
		var animation = (AnimationTrack) track("animation");
		var target = new EmptyFXObject();
		var property = rollType().create(target);
		animation.properties().add(property);
		definition.tracks.add(animation);
		var payload = new CompoundTag();
		payload.putInt("payload", 7);
		var markerData = new PhotonAnimationDefinition.Marker(2, "marker", payload);
		definition.markers.add(markerData);
		var first = new PhotonAnimationTimelineModel();
		var second = new PhotonAnimationTimelineModel();
		first.load(definition);
		second.load(definition);
		var marker = first.timeline().markers().getFirst();
		check(first.timeline() != original.timeline() && first.timeline() != second.timeline(), "distinct timeline models");
		check(first.timeline().tracks().getFirst() == animation, "track identity preserved");
		check(((AnimationTrack) first.timeline().tracks().getFirst()).properties().getFirst() == property, "property identity preserved");
		check(marker != second.timeline().markers().getFirst(), "marker editor identities isolated");
		check(original.timeline().tracks().equals(List.of(originalTrack)), "native FX timeline untouched");

		var id = definition.id;
		definition.name = "renamed";
		first.load(definition);
		check(first.timeline().markers().getFirst() == marker && definition.id.equals(id), "rename preserves model selection identities");
		marker.tick(3).name("edited");
		first.store(definition);
		check(definition.tracks.getFirst() == animation, "store retains track reference");
		check(definition.markers.getFirst().data().getIntOr("payload", 0) == 7, "store retains marker payload");
		equal(3, definition.markers.getFirst().time(), "marker time stored");
		check(definition.markers.getFirst().name().equals("edited"), "marker name stored");
		first.load(definition);
		check(first.timeline().markers().getFirst() == marker, "store and load retain marker identity");

		var other = new PhotonAnimationDefinition();
		other.markers.add(definition.markers.getFirst());
		first.load(other);
		check(first.timeline().markers().getFirst() != marker, "switching animation replaces marker identity");
		first.load(null);
		check(first.timeline().tracks().isEmpty() && first.timeline().markers().isEmpty(), "null definition clears named model");
		check(second.timeline().tracks().getFirst() == animation && second.timeline().markers().size() == 1, "other context survives clear");
		check(original.timeline().tracks().equals(List.of(originalTrack)), "native timeline survives switch and clear");
	}

	private void nameScope() {
		try {
			var target = new EmptyFXObject();
			var name = FXObject.class.getDeclaredField("name");
			var roll = FXObject.class.getDeclaredField("roll");
			var animationName = PhotonAnimationDefinition.class.getDeclaredField("name");
			check(!PhotonAnimationNameUtil.locksName(name, target), "scene name remains editable outside animation inspection");
			var result = PhotonAnimationNameUtil.inspect(() -> {
				check(PhotonAnimationNameUtil.locksName(name, target), "animation inspection locks original object name");
				check(!PhotonAnimationNameUtil.locksName(roll, target), "animation name lock does not lock other fields");
				check(!PhotonAnimationNameUtil.locksName(animationName, target), "name lock requires original field identity");
				check(!PhotonAnimationNameUtil.locksName(name, new Object()), "name lock requires FX object owner");
				PhotonAnimationNameUtil.inspect(() -> {
					check(PhotonAnimationNameUtil.locksName(name, target), "nested inspection retains name lock");
					return target;
				});
				check(PhotonAnimationNameUtil.locksName(name, target), "nested inspection restores outer lock");
				return target;
			});
			check(result == target, "name scope returns original builder value");
			check(!PhotonAnimationNameUtil.locksName(name, target), "completed inspection releases name lock");
			var failed = false;

			try {
				PhotonAnimationNameUtil.inspect(() -> {
					check(PhotonAnimationNameUtil.locksName(name, target), "exceptional inspection begins locked");
					throw new IllegalArgumentException("fixture");
				});
			} catch (IllegalArgumentException expected) {
				failed = true;
			}

			check(failed, "name scope propagates builder failure");
			check(!PhotonAnimationNameUtil.locksName(name, target), "failed inspection releases name lock");
		} catch (NoSuchFieldException failure) {
			throw new AssertionError(failure);
		}
	}

	private void trackRoundTrip() {
		var definition = allTracks();
		var group = (TrackGroup) definition.tracks.getFirst();
		var provider = provider();
		var loaded = PhotonAnimationDefinition.read(provider, definition.write(provider));
		var copied = (TrackGroup) loaded.tracks.getFirst();
		check(copied != group && copied.children().size() == 7, "eight native track types round trip");

		for (var index = 0; index < group.children().size(); index++) {
			var before = group.children().get(index);
			var after = copied.children().get(index);
			check(after != before && after.type() == before.type(), "native track type " + index);
			check(after.displayName().equals(before.displayName()), "native track label " + index);
		}

		check(loaded.timeline() != definition.timeline(), "serialized timeline independent");
		check(loaded.leafTracks(false).size() == 7, "unmuted group traversed");
		copied.mute(true);
		check(loaded.leafTracks(false).isEmpty(), "muted group excludes children");
		check(loaded.leafTracks(true).size() == 7, "include muted retains children");
		check(!group.mute(), "loaded mute does not mutate source");
		copied.mute(false);
		copied.children().getFirst().mute(true);
		check(loaded.leafTracks(false).getFirst().mute(), "native leaf traversal preserves leaf mute for player");
		var copy = loaded.timeline().copy();
		check(copy.tracks().getFirst() != copied, "native timeline copy independent");
		check(((TrackGroup) copy.tracks().getFirst()).children().getFirst().mute(), "native copy preserves leaf mute");
	}

	private void childTrackRoundTrip() {
		var track = new PhotonChildAnimationTrack();
		track.setType(LcPhotonAnimationTypes.CHILD_ANIMATION);
		var spawner = UUID.randomUUID();
		track.targetId(spawner);
		var clip = new PhotonChildAnimationClip(2.5, 10, 1);
		clip.animation = UUID.randomUUID().toString();
		clip.mode = PhotonChildAnimationClip.Mode.INDEPENDENT;
		clip.completion = PhotonChildAnimationClip.Completion.RELEASE;
		clip.playbackDuration = 40;
		var input = new PhotonAnimationInput();
		input.target = UUID.randomUUID().toString();
		input.source = UUID.randomUUID().toString();
		input.follow = true;
		input.value = new float[]{2, 3};
		clip.inputs.add(input);
		track.clips().add(clip);
		var encoded = Timeline.writeTrack(track, provider());
		var loaded = (PhotonChildAnimationTrack) Objects.requireNonNull(Timeline.readTrack(provider(), encoded));
		var loadedClip = (PhotonChildAnimationClip) loaded.clips().getFirst();
		check(Objects.equals(loaded.targetId(), spawner), "child track preserves spawner target");
		check(loadedClip.id.equals(clip.id), "child clip retains stable stored identity");
		check(loadedClip.animation.equals(clip.animation), "child animation reference retained");
		check(loadedClip.mode == clip.mode && loadedClip.completion == clip.completion, "child call mode and completion retained");
		equal(2.5, loadedClip.start(), "child fractional start retained");
		equal(40, loadedClip.playbackDuration, "independent child duration retained");
		check(loadedClip.inputs.getFirst().write().equals(input.write()), "child input UUID and follow round trip");
		loadedClip.inputs.getFirst().value[0] = 99;
		equal(2, input.value[0], "child input round trip owns its arrays");
		var copy = clip.copy();
		check(!copy.id.equals(clip.id), "duplicated clip receives independent ownership identity");
		check(copy.animation.equals(clip.animation), "duplicated clip retains child animation reference");
		copy.inputs.getFirst().value[0] = 77;
		equal(2, input.value[0], "duplicated child inputs remain isolated");
	}

	private void activationOptions() {
		var defaults = PhotonActivatorOptions.read(new CompoundTag());
		check(defaults.equals(PhotonActivatorOptions.DEFAULT) && defaults.intervalControl(), "missing activation data preserves interval defaults");
		var original = new PhotonActivatorOptions(true, PhotonActivatorOptions.StartBehavior.RESTART, false,
				PhotonActivatorOptions.EndBehavior.CLEAR, false);
		check(PhotonActivatorOptions.read(original.write()).equals(original), "start-only activation options round trip");
		check(!original.write().contains("singleFrame"), "new activation data omits independent single-frame flag");
		var legacy = PhotonActivatorOptions.DEFAULT.write();
		legacy.putBoolean("singleFrame", true);
		var migrated = PhotonActivatorOptions.read(legacy);
		check(migrated.startControl() && !migrated.endControl() && migrated.pointControl(), "legacy single-frame data migrates to start-only control");
		check(!migrated.write().contains("singleFrame"), "migrated activation data writes only endpoint controls");
		legacy.putBoolean("singleFrame", false);
		check(PhotonActivatorOptions.read(legacy).equals(PhotonActivatorOptions.DEFAULT), "legacy false flag preserves both endpoint controls");
		check(new PhotonActivatorOptions(true, PhotonActivatorOptions.StartBehavior.RESTART, true,
				PhotonActivatorOptions.EndBehavior.CLEAR, false, true).equals(original), "legacy constructor converts enabled single-frame flag");
		var activator = track("activator");
		((PhotonActivatorTrackAccess) activator).lobecorp$setActivationOptions(original);
		check(PhotonActivatorTimelineUtil.options(activator.copy()).equals(original), "track copy preserves endpoint activation controls");
		var saved = Timeline.writeTrack(activator, provider());
		var loaded = Objects.requireNonNull(Timeline.readTrack(provider(), saved));
		check(PhotonActivatorTimelineUtil.options(loaded).equals(original), "native track codec preserves activation options");
	}

	private void activationEntries() {
		var timeline = new Timeline();
		var activator = track("activator");
		timeline.tracks().add(activator);
		activator.clips().addAll(List.of(new Clip(2, 1, 1), new Clip(4, 1, 1)));
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 6).equals(List.of(2.0, 4.0)), "separate activation windows retain both crossed entries");
		check(PhotonTimelineTraversalUtil.boundaries(timeline, 1, 6).equals(List.of(2.0, 3.0, 4.0, 5.0)), "interval traversal includes entry and exit boundaries");
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 2, 4).equals(List.of(4.0)), "crossed entry interval excludes old and includes new endpoint");
		activator.clips().clear();
		activator.clips().addAll(List.of(new Clip(2, 3, 1), new Clip(3, 3, 1)));
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 7).equals(List.of(2.0)), "overlapping windows share one activation entry");
		check(PhotonTimelineTraversalUtil.boundaries(timeline, 1, 7).equals(List.of(2.0, 6.0)), "overlapping interval traversal omits interior endpoints");
		activator.clips().clear();
		activator.clips().addAll(List.of(new Clip(2, 1, 1), new Clip(3, 1, 1)));
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 5).equals(List.of(2.0)), "adjacent activation windows do not create a restart gap");
		check(PhotonTimelineTraversalUtil.boundaries(timeline, 1, 5).equals(List.of(2.0, 4.0)), "adjacent interval traversal retains only union boundaries");
		activator.clips().clear();
		activator.clips().add(new Clip(2, 0, 1));
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 3).isEmpty(), "zero-length interval activation remains inert");
		((PhotonActivatorTrackAccess) activator).lobecorp$setActivationOptions(new PhotonActivatorOptions(true,
				PhotonActivatorOptions.StartBehavior.RESTART, false, PhotonActivatorOptions.EndBehavior.ORIGINAL, true));
		activator.clips().addAll(List.of(new Clip(1.5, 0, 1), new Clip(2, 0, 1)));
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 3).equals(List.of(1.5, 2.0)), "start-only points include zero-length clips and deduplicate equal times");
		check(PhotonTimelineTraversalUtil.boundaries(timeline, 1, 3).equals(List.of(1.5, 2.0)), "start-only traversal uses start points only");
		check(!PhotonActivatorTimelineUtil.controlsAt(activator, 1) && PhotonActivatorTimelineUtil.controlsAt(activator, 1.5), "start-only ownership begins exactly at its first point");
		((PhotonActivatorTrackAccess) activator).lobecorp$setActivationOptions(new PhotonActivatorOptions(false,
				PhotonActivatorOptions.StartBehavior.RESTART, true, PhotonActivatorOptions.EndBehavior.STOP_EMISSION, true));
		activator.clips().clear();
		activator.clips().addAll(List.of(new Clip(1, 1, 1), new Clip(1.5, 1, 1), new Clip(3, 0, 1)));
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 3).equals(List.of(2.0, 2.5, 3.0)), "end-only points use every valid clip end without union suppression");
		check(PhotonTimelineTraversalUtil.boundaries(timeline, 1, 4).equals(List.of(2.0, 2.5, 3.0)), "end-only traversal uses every point end without clip starts");
		check(!PhotonActivatorTimelineUtil.controlsAt(activator, 1.5) && PhotonActivatorTimelineUtil.controlsAt(activator, 2), "end-only ownership begins at the end rather than clip start");
		check(!PhotonActivatorTimelineUtil.nativeInterval(activator), "point activator is excluded from native interval evaluation");
		activator.mute(true);
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 3).isEmpty(), "muted activation leaf has no entries");
		activator.mute(false);
		var group = (TrackGroup) track("group");
		group.children().add(activator);
		timeline.tracks().clear();
		timeline.tracks().add(group);
		group.mute(true);
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 1, 3).isEmpty(), "muted parent excludes activation subtree");
		group.mute(false);
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 3, 1).isEmpty(), "reverse seek does not replay activation entries");
		check(PhotonActivatorTimelineUtil.crossedEntries(timeline, 2, 2).isEmpty(), "same-time sample does not replay activation entries");
	}

	private void pointActivation() {
		var target = new CountingTarget();
		var activator = track("activator");
		activator.targetId(target.id());
		activator.clips().add(new Clip(2, 0, 1));
		((PhotonActivatorTrackAccess) activator).lobecorp$setActivationOptions(new PhotonActivatorOptions(true,
				PhotonActivatorOptions.StartBehavior.RESTART, false, PhotonActivatorOptions.EndBehavior.CLEAR, true));
		var timeline = new Timeline();
		timeline.tracks().add(activator);
		var fx = new FXRuntime(new FXData(List.of(target), timeline));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.begin(effect);
			var baseline = target.resets;
			var access = (PhotonAnimationPlayerAccess) fx.timelinePlayer;
			check(target.isSelfActive() && !PhotonActivatorTimelineUtil.controlsAt(activator, 0), "pending start-only point leaves normal runtime state unchanged");
			access.lobecorp$evaluateAt(2);
			equal(1, target.resets - baseline, "start-only point executes restart exactly once");
			check(target.isSelfActive() && target.isSelfTimelineVisible(), "start-only point keeps the target active and visible");
			access.lobecorp$evaluateAt(2);
			access.lobecorp$evaluateAt(10);
			equal(1, target.resets - baseline, "same-time and later samples do not repeat start-only restart");
			check(target.isSelfActive() && target.isSelfTimelineVisible(), "start-only point ignores its unused end behavior");
		} finally {
			fx.timelinePlayer.stop();
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void endPointActivation(PhotonActivatorOptions.EndBehavior behavior) {
		var target = new EmissionTarget();
		CountingTarget counter = target;
		var activator = track("activator");
		activator.targetId(target.id());
		activator.clips().add(new Clip(2, 1, 1));
		((PhotonActivatorTrackAccess) activator).lobecorp$setActivationOptions(new PhotonActivatorOptions(false,
				PhotonActivatorOptions.StartBehavior.RESTART, true, behavior, true));
		var timeline = new Timeline();
		timeline.tracks().add(activator);
		var fx = new FXRuntime(new FXData(List.of(target), timeline));
		var effect = new ProbeEffect(counter);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.begin(effect);
			var baseline = counter.resets;
			var access = (PhotonAnimationPlayerAccess) fx.timelinePlayer;
			access.lobecorp$evaluateAt(2.5);
			check(target.isSelfActive() && target.isSelfTimelineVisible() && !target.lobecorp$isEmissionStopped(),
					"pending end-only point leaves normal activity and emission unchanged");
			target.lobecorp$setEmissionStopped(true);
			access.lobecorp$evaluateAt(2.5);
			check(target.lobecorp$isEmissionStopped(), "pending end-only point preserves an outside manual emission stop");
			target.lobecorp$setEmissionStopped(false);
			access.lobecorp$evaluateAt(3);
			boolean draining = behavior == PhotonActivatorOptions.EndBehavior.STOP_EMISSION;
			check(target.isSelfActive() == draining && target.isSelfTimelineVisible() == draining,
					"end-only behavior keeps only draining particles active and visible");
			check(target.lobecorp$isEmissionStopped() == draining, "end-only stop blocks new emission without stopping updates");
			equal(behavior == PhotonActivatorOptions.EndBehavior.CLEAR ? 1 : 0, target.clears,
					"end-only clear removes remnants only when explicitly configured");
			access.lobecorp$evaluateAt(3);
			access.lobecorp$evaluateAt(6);
			equal(0, counter.resets - baseline, "end-only points never execute the unused restart behavior");
			equal(behavior == PhotonActivatorOptions.EndBehavior.CLEAR ? 1 : 0, target.clears,
					"repeated samples do not repeat end-only clear");
			access.lobecorp$evaluateAt(2.5);
			check(target.isSelfActive() && target.isSelfTimelineVisible() && !target.lobecorp$isEmissionStopped(),
					"plain timeline backward seek restores pre-point activity visibility and emission");
			access.lobecorp$evaluateAt(3);
			activator.mute(true);
			access.lobecorp$evaluateAt(3);
			check(target.isSelfActive() && target.isSelfTimelineVisible() && !target.lobecorp$isEmissionStopped(),
					"muting the end-only owner restores the original state");
		} finally {
			fx.timelinePlayer.stop();
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void pointReleaseNativePriority(boolean controlled) {
		var target = new EmissionTarget();
		var point = track("activator");
		point.targetId(target.id());
		point.clips().add(new Clip(1, 1, 1));
		((PhotonActivatorTrackAccess) point).lobecorp$setActivationOptions(new PhotonActivatorOptions(false,
				PhotonActivatorOptions.StartBehavior.CONTINUE, true, PhotonActivatorOptions.EndBehavior.ORIGINAL, true));
		var owner = track(controlled ? "control" : "activator");

		if (!controlled) {
			owner.targetId(target.id());
			((PhotonActivatorTrackAccess) owner).lobecorp$setActivationOptions(new PhotonActivatorOptions(true,
					PhotonActivatorOptions.StartBehavior.CONTINUE, true, PhotonActivatorOptions.EndBehavior.ORIGINAL, true));
		}

		owner.clips().add(new Clip(0, 10, 1).targetId(target.id()).randomSeed(false));
		var timeline = new Timeline();
		timeline.tracks().addAll(List.of(owner, point));
		var fx = new FXRuntime(new FXData(List.of(target), timeline));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.begin(effect);
			var access = (PhotonAnimationPlayerAccess) fx.timelinePlayer;
			access.lobecorp$evaluateAt(2);
			check(!target.isSelfTimelineVisible(), "end-only point overrides prior native visibility");
			point.mute(true);
			owner.clips().clear();
			owner.clips().add(new Clip(0, 1, 1).targetId(target.id()).randomSeed(false));
			access.lobecorp$evaluateAt(3);
			check(!target.isSelfActive() && !target.isSelfTimelineVisible(),
					"releasing point ownership preserves inactive native " + (controlled ? "control" : "interval") + " state");
			timeline.tracks().remove(owner);
			access.lobecorp$evaluateAt(3);
			check(target.isSelfActive() && target.isSelfTimelineVisible(), "releasing native ownership restores original native defaults");
		} finally {
			fx.timelinePlayer.stop();
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void pointCommands() {
		var target = new EmissionTarget();
		CountingTarget counter = target;
		var interval = track("activator");
		interval.targetId(target.id());
		interval.clips().addAll(List.of(new Clip(0, 5, 1), new Clip(8, 2, 1)));
		((PhotonActivatorTrackAccess) interval).lobecorp$setActivationOptions(new PhotonActivatorOptions(true,
				PhotonActivatorOptions.StartBehavior.RESTART, true, PhotonActivatorOptions.EndBehavior.CLEAR, true));
		var stop = track("activator");
		stop.targetId(target.id());
		stop.clips().addAll(List.of(new Clip(2, 1, 1), new Clip(6, 1, 1), new Clip(11, 1, 1)));
		((PhotonActivatorTrackAccess) stop).lobecorp$setActivationOptions(new PhotonActivatorOptions(false,
				PhotonActivatorOptions.StartBehavior.RESTART, true, PhotonActivatorOptions.EndBehavior.STOP_EMISSION, true));
		var start = track("activator");
		start.targetId(target.id());
		start.clips().addAll(List.of(new Clip(4, 0, 1), new Clip(12, 0, 1)));
		((PhotonActivatorTrackAccess) start).lobecorp$setActivationOptions(new PhotonActivatorOptions(true,
				PhotonActivatorOptions.StartBehavior.CONTINUE, false, PhotonActivatorOptions.EndBehavior.ORIGINAL, true));
		var timeline = new Timeline();
		timeline.tracks().addAll(List.of(interval, stop, start));
		var fx = new FXRuntime(new FXData(List.of(target), timeline));
		var effect = new ProbeEffect(counter);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.begin(effect);
			var baseline = counter.resets;
			var access = (PhotonAnimationPlayerAccess) fx.timelinePlayer;
			access.lobecorp$evaluateAt(2);
			check(!target.lobecorp$isEmissionStopped(), "pending stop and start points do not claim ongoing interval emission");
			access.lobecorp$evaluateAt(3);
			check(target.isSelfActive() && target.lobecorp$isEmissionStopped(), "latest end-only stop overrides an older continuously active interval");
			access.lobecorp$evaluateAt(4);
			check(target.isSelfActive() && !target.lobecorp$isEmissionStopped(), "later start-only continue restores emission");
			equal(0, counter.resets - baseline, "start-only continue preserves existing particles and age");
			access.lobecorp$evaluateAt(7);
			check(target.isSelfActive() && target.lobecorp$isEmissionStopped(), "next end-only point latches another emission stop");
			access.lobecorp$evaluateAt(8);
			check(target.isSelfActive() && !target.lobecorp$isEmissionStopped(), "later real interval entry recovers emission from a point stop");
			equal(1, counter.resets - baseline, "later interval entry retains its original restart behavior");
			access.lobecorp$evaluateAt(10);
			check(!target.isSelfActive(), "restored interval retains its configured end behavior");
			equal(1, target.clears, "overridden old start-only command does not suppress later interval clear");
			access.lobecorp$evaluateAt(12);
			check(!target.lobecorp$isEmissionStopped(), "later leaf wins when start and stop points share one time");
			timeline.tracks().clear();
			timeline.tracks().addAll(List.of(interval, start, stop));
			access.lobecorp$evaluateAt(12);
			check(target.lobecorp$isEmissionStopped(), "same-time point precedence follows current native leaf order");
			start.mute(true);
			stop.mute(true);
			var otherInterval = track("activator");
			otherInterval.targetId(target.id());
			otherInterval.clips().add(new Clip(0, 20, 1));
			timeline.tracks().add(otherInterval);
			access.lobecorp$evaluateAt(12);
			check(target.isSelfActive() && !target.lobecorp$isEmissionStopped(), "ordinary overlapping interval tracks retain native OR semantics");
		} finally {
			fx.timelinePlayer.stop();
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void disabledActivation() {
		var target = new EmissionTarget();
		CountingTarget counter = target;
		var activator = track("activator");
		activator.targetId(target.id());
		activator.clips().add(new Clip(2, 2, 1));
		((PhotonActivatorTrackAccess) activator).lobecorp$setActivationOptions(new PhotonActivatorOptions(false,
				PhotonActivatorOptions.StartBehavior.RESTART, false, PhotonActivatorOptions.EndBehavior.CLEAR, true));
		var timeline = new Timeline();
		timeline.tracks().add(activator);
		var fx = new FXRuntime(new FXData(List.of(target), timeline));
		var effect = new ProbeEffect(counter);

		try {
			target.setSelfActive(false);
			target.setSelfTimelineVisible(false);
			target.lobecorp$setEmissionStopped(true);
			var baseline = counter.resets;
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.begin(effect);
			((PhotonAnimationPlayerAccess) fx.timelinePlayer).lobecorp$evaluateAt(3);
			check(!target.isSelfActive() && !target.isSelfTimelineVisible() && target.lobecorp$isEmissionStopped(),
					"both disabled endpoints preserve outside activity visibility and manual emission stop");
			check(!PhotonActivatorTimelineUtil.controlsAt(activator, 3)
					&& PhotonActivatorTimelineUtil.crossedEntries(timeline, 0, 5).isEmpty(), "disabled endpoints own no target or event boundary");
			check(PhotonTimelineTraversalUtil.boundaries(timeline, 0, 5).isEmpty(), "disabled endpoints add no traversal samples");
			equal(0, counter.resets - baseline, "disabled endpoints do not restart the target");
			equal(0, target.clears, "disabled endpoints do not clear existing particles");
		} finally {
			fx.timelinePlayer.stop();
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void crossedControlAndSignals() {
		CountingTarget controlled = new CountingPositionTarget();
		var activated = new CountingTarget();
		var control = (ControlTrack) track("control");
		control.clips().add(new Clip(2, 0.25, 1).targetId(controlled.id()).randomSeed(false));
		var activator = track("activator");
		activator.targetId(activated.id());
		activator.clips().add(new Clip(2.5, 0.25, 1));
		var animation = (AnimationTrack) track("animation");
		animation.targetId(controlled.id());
		var position = PositionPropertyType.INSTANCE.create(controlled);
		position.addExprClip(0, new ExprClip(0, 5, "t * 10 + 7"));
		animation.properties().add(position);
		var speed = (SpeedTrack) track("speed");
		speed.targetId(controlled.id());
		speed.speedProperty().addExprClip(0, new ExprClip(0, 5, "t + 1"));
		var later = (SignalTrack) track("signal");
		later.displayName("later");
		later.signals().add(new Signal(2.625, "second"));
		var earlier = (SignalTrack) track("signal");
		earlier.displayName("earlier");
		earlier.signals().add(new Signal(2.125, "first"));
		var timeline = new Timeline();
		timeline.tracks().addAll(List.of(control, activator, animation, speed, later, earlier));
		var fx = new FXRuntime(new FXData(List.of(controlled, activated), timeline));
		var effect = new ProbeEffect(controlled);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.begin(effect);
			var access = (PhotonAnimationPlayerAccess) fx.timelinePlayer;
			access.lobecorp$evaluateAt(1);
			var controlResets = controlled.resets;
			var activationResets = activated.resets;
			access.lobecorp$evaluateAt(4);
			equal(1, controlled.resets - controlResets, "crossed fractional control clip still restarts once");
			equal(1, activated.resets - activationResets, "crossed fractional activation clip still restarts once");
			check(!controlled.isSelfActive() && !activated.isSelfActive(), "crossed windows retain their original final inactive state");
			check(effect.signals.equals(List.of("earlier:first:2.125:28.25:true:3.125", "later:second:2.625:33.25:false:3.625")),
					"crossed signals follow event time and observe native pose activity and speed");
			access.lobecorp$evaluateAt(4);
			equal(1, controlled.resets - controlResets, "same-time sample does not restart crossed control");
			equal(1, activated.resets - activationResets, "same-time sample does not restart crossed activation");
			check(effect.signals.size() == 2, "same-time sample does not dispatch crossed signals twice");
		} finally {
			fx.timelinePlayer.stop();
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void versionMigration() {
		var provider = provider();
		var current = allTracks();
		current.name = "current";
		var currentLibrary = library(current);
		var encoded = currentLibrary.write(provider);
		check(encoded.getIntOr("version", 0) == PhotonAnimationDataUtil.VERSION, "library writes current version");
		var reloaded = PhotonAnimationLibrary.read(provider, encoded).animations.getFirst();
		check(reloaded.autoDuration && !reloaded.autoEnableModules, "current version native defaults");
		check(reloaded.id.equals(current.id), "current version stable identity");

		var second = current.write(provider);
		second.remove("autoDuration");
		second.remove("autoEnableModules");
		var v3 = PhotonAnimationLibrary.read(provider, versioned(second, PhotonAnimationDataUtil.AUTO_DURATION_VERSION)).animations.getFirst();
		check(v3.autoDuration && !v3.autoEnableModules, "version three missing flags retain native defaults");
		check(v3.id.equals(current.id), "version three stable identity");
		check(v3.leafTracks(true).size() == 7, "version three typed native tracks preserved");
		var v4 = PhotonAnimationLibrary.read(provider, versioned(second, 4)).animations.getFirst();
		check(v4.autoDuration && !v4.autoEnableModules, "version four missing flags retain native defaults");
		check(v4.id.equals(current.id), "version four stable identity");
		check(v4.leafTracks(true).size() == 7, "version four typed native tracks preserved");

		var v2 = PhotonAnimationLibrary.read(provider, versioned(second, 2)).animations.getFirst();
		check(!v2.autoDuration && v2.autoEnableModules, "version two compatibility flags");
		check(v2.leafTracks(true).size() == 7, "version two typed native tracks preserved");

		var flat = new PhotonAnimationDefinition();
		flat.name = "legacy";
		flat.duration = 12;
		var animation = (AnimationTrack) track("animation");
		animation.targetId(UUID.randomUUID());
		flat.tracks.add(animation);
		var legacy = flat.write(provider);
		legacy.remove("autoDuration");
		legacy.remove("autoEnableModules");
		var flatTracks = new ListTag();
		flatTracks.add(animation.writeData(provider));
		legacy.put("tracks", flatTracks);
		var v1 = PhotonAnimationLibrary.read(provider, versioned(legacy, 1)).animations.getFirst();
		check(!v1.autoDuration && v1.autoEnableModules, "version one compatibility flags");
		check(v1.tracks.getFirst() instanceof AnimationTrack, "version one flat animation track migrated");
		check(Objects.equals(v1.tracks.getFirst().targetId(), animation.targetId()), "version one target identity preserved");
		equal(12, v1.playbackDuration(), "version one fixed duration");
		var unspecified = PhotonAnimationDefinition.read(provider, legacy);
		check(!unspecified.autoDuration && unspecified.autoEnableModules, "legacy direct read compatibility");
		check(!legacy.contains("autoDuration"), "migration leaves source tag intact");

		var futureTag = versioned(second, PhotonAnimationDataUtil.VERSION + 1);
		var future = PhotonAnimationLibrary.read(provider, futureTag);
		check(future.migrationBlocked(), "future library version is blocked");
		check(Objects.equals(future.originalDefinition(), futureTag), "future library retains complete source");
	}

	private void durationPolicy() {
		var definition = new PhotonAnimationDefinition();
		equal(20, definition.authoredDuration(), "empty authored duration fallback");
		var control = track("control");
		control.clips().add(new Clip(3, 25, 1));
		definition.tracks.add(control);
		equal(28, definition.authoredDuration(), "native clip span determines duration");
		definition.markers.add(new PhotonAnimationDefinition.Marker(40, "tail", new CompoundTag()));
		equal(40, definition.playbackDuration(), "named marker extends automatic duration");
		definition.duration = 100;
		equal(40, definition.playbackDuration(), "automatic duration ignores override");
		definition.autoDuration = false;
		equal(100, definition.playbackDuration(), "explicit duration retimes authored content");
		definition.loop = true;
		var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
		playback.advance(250);
		equal(2, playback.loops(0), "loop count before retime");
		equal(0.5, playback.progress(0), "half progress before retime");
		equal(20, playback.time(0), "half authored time before retime");
		playback.setDuration(200);
		equal(2, playback.loops(0), "retime retains loop count");
		equal(0.5, playback.progress(0), "retime retains half progress");
		equal(20, playback.time(0), "retime retains authored sample");
	}

	private void recordingFreeze(boolean edit) {
		var target = new CountingTarget();
		var controller = new PhotonAnimationController();
		var fx = new FXRuntime(new FXData(List.of(target, controller)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			PositionPropertyType.INSTANCE.apply(target, new float[]{7, 1, 2});
			var definition = new PhotonAnimationDefinition();
			definition.name = "freeze";
			var track = (AnimationTrack) track("animation");
			track.targetId(target.id());
			var property = PositionPropertyType.INSTANCE.create(target);
			property.putKey(0, 0, 2);
			property.putKey(0, 20, 12);
			track.properties().add(property);
			definition.tracks.add(track);
			var binding = definition.binding(target.id(), property.type().path());
			binding.blend = PhotonAnimationBinding.Blend.ADD;
			binding.component = 0;
			controller.library(library(definition));
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);

			try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
				runtime.previewOnly();
				runtime.playPreview(definition, 10);
				var pose = target.transform().localPosition().x;
				check(pose > 7, "recording fixture has a composed additive pose");
				equal(7, runtime.property(target.id(), property.type().path(), false)[0], "recording fixture retains original base");
				var tick = Objects.requireNonNull(root.getOnUpdateTick());
				var frame = Objects.requireNonNull(root.getOnUpdateFrame());
				runtime.setPreviewRecording(true);
				equal(pose, target.transform().localPosition().x, "entering recording retains current pose");
				tick.run();
				equal(pose, target.transform().localPosition().x, "recording tick retains pose");
				tick.run();
				equal(pose, target.transform().localPosition().x, "recording second tick does not recompute pose");
				frame.accept(0.75f);
				equal(pose, target.transform().localPosition().x, "recording frame does not recompute pose");
				equal(7, runtime.serializeBase(() -> target.transform().localPosition().x), "recording serialization reads authored base");
				equal(pose, target.transform().localPosition().x, "recording serialization restores frozen pose");
				frame.accept(0.25f);
				equal(pose, target.transform().localPosition().x, "serialization preserves subsequent recording freeze");

				if (edit) {
					editedRecording(runtime, target, tick, frame);
				}

				runtime.setPreviewRecording(false);
				equal(edit ? 19 : pose, target.transform().localPosition().x, "leaving recording does not immediately overwrite live value");
				runtime.stop(definition.name);
				position(edit, target, "stopping recording animation");
			}

			position(edit, target, "closing recording controller");
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void editedRecording(PhotonAnimationRuntime runtime, CountingTarget target, Runnable tick,
	                             Consumer<Float> frame) {
		PositionPropertyType.INSTANCE.apply(target, new float[]{19, 3, 4});
		tick.run();
		equal(19, target.transform().localPosition().x, "recording tick preserves user position edit");
		frame.accept(0.5f);
		equal(3, target.transform().localPosition().y, "recording frame preserves user Y edit");
		equal(4, target.transform().localPosition().z, "recording frame preserves user Z edit");
		equal(19, runtime.serializeBase(() -> target.transform().localPosition().x), "serialization retains user base edit");
		equal(19, target.transform().localPosition().x, "serialization restoration does not overwrite user base edit");
	}

	private void position(boolean edit, CountingTarget target, String name) {
		var value = target.transform().localPosition();
		equal(edit ? 19 : 7, value.x, name + " X ownership");
		equal(edit ? 3 : 1, value.y, name + " Y ownership");
		equal(edit ? 4 : 2, value.z, name + " Z ownership");
	}

	private void historySnapshots(boolean transform, boolean edit) {
		var target = new CountingTarget();
		var controller = new PhotonAnimationController();
		var fx = new HistoryRuntime(new FXData(List.of(target, controller)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			PositionPropertyType.INSTANCE.apply(target, new float[]{0, 0, 0});
			var definition = new PhotonAnimationDefinition();
			definition.name = "history";
			var track = (AnimationTrack) track("animation");
			track.targetId(target.id());
			var property = PositionPropertyType.INSTANCE.create(target);
			property.putKey(0, 0, 10);
			property.putKey(0, 20, 10);
			track.properties().add(property);
			definition.tracks.add(track);
			controller.library(library(definition));
			fx.getRoot().setEffect(effect);
			@SuppressWarnings("DataFlowIssue") // FXRuntime 访问接口由已加载的 Mixin 注入。
			var access = (PhotonAnimationRuntimeAccess) fx;
			access.lobecorp$reloadAnimations();

			try (var runtime = Objects.requireNonNull(access.lobecorp$animations())) {
				runtime.previewOnly();
				runtime.playPreview(definition, 10);
				equal(10, target.transform().localPosition().x, "history fixture shows animated pose");
				ValueIOSerializable serializable = transform ? target.transform() : target;
				var constructed = SerializableRecordAction.of(serializable);
				equal(10, target.transform().localPosition().x, "native history constructor restores preview pose");
				var updated = SerializableRecordAction.of(serializable);
				equal(10, target.transform().localPosition().x, "second native history constructor restores preview pose");

				if (edit) {
					runtime.setPreviewRecording(true);
					equal(10, target.transform().localPosition().x, "history recording begins from composed pose");
					PositionPropertyType.INSTANCE.apply(target, new float[]{19, 3, 4});
					equal(19, target.transform().localPosition().x, "history fixture accepts manual recording edit");
				}

				updated.updateSnapshot();
				equal(edit ? 19 : 10, target.transform().localPosition().x, "native snapshot update preserves live value");
				runtime.setPreviewRecording(false);
				runtime.stop(definition.name);
				historyPosition(edit, target, "native history stop restores owned base");
				PositionPropertyType.INSTANCE.apply(target, new float[]{99, 8, 9});
				constructed.undo();
				historyPosition(false, target, "native constructor snapshot undo contains authored base");
				PositionPropertyType.INSTANCE.apply(target, new float[]{99, 8, 9});
				constructed.execute();
				historyPosition(false, target, "native constructor snapshot execute contains authored base");
				PositionPropertyType.INSTANCE.apply(target, new float[]{99, 8, 9});
				updated.undo();
				historyPosition(edit, target, "native updated snapshot undo contains authored value");
				PositionPropertyType.INSTANCE.apply(target, new float[]{99, 8, 9});
				updated.execute();
				historyPosition(edit, target, "native updated snapshot execute contains authored value");
			}

			historyPosition(edit, target, "native history close preserves restored value");
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void historyPosition(boolean edited, CountingTarget target, String name) {
		var position = target.transform().localPosition();
		equal(edited ? 19 : 0, position.x, name + " X");
		equal(edited ? 3 : 0, position.y, name + " Y");
		equal(edited ? 4 : 0, position.z, name + " Z");
	}

	private void propertyParity() {
		var emitter = new ParticleEmitter();
		var fx = new FXRuntime(new FXData(List.of(emitter)));
		var mixer = new PhotonAnimationMixer(fx, problem -> {
			throw new AssertionError(problem);
		});

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var scalar = (ConfigAnimatedProperty) property(emitter, "config.rotationOverLifetime.roll");
			scalar.curveClips(0).add(new CurveClip(0, 4, new ParticleFunction(5)));
			var vector = (ConfigAnimatedProperty) property(emitter, "config.startRotation");
			vector.curveClips(0).add(new CurveClip(0, 4, new ParticleFunction(7)));
			vector.curveClips(2).add(new CurveClip(0, 4, new ParticleFunction(-3)));
			var color = (ColorAnimatedProperty) property(emitter, "config.startColor");
			color.gradientClips().add(new GradientClip(0, 4, new GradientColor(0x40ff0000, 0xc00000ff)));
			compareProperty(emitter, scalar, fx, mixer);
			compareProperty(emitter, vector, fx, mixer);
			compareProperty(emitter, color, fx, mixer);
			var integerType = emitter.getFXObjectType().animatableProperties(emitter).stream()
					.filter(type -> type instanceof ConfigPropertyType config && config.valueType() == ConfigValueType.INT)
					.findFirst().orElseThrow();
			var integer = integerType.create(emitter);
			integer.addExprClip(0, new ExprClip(0, 4, "t * 2 + 1.2"));
			compareProperty(emitter, integer, fx, mixer);
			check(!emitter.runtime().rotationOverLifetime.isEnable(), "new native parity preserves disabled module");
			check(!emitter.runtime().rotationOverLifetime.roll.isOverridden(), "parity releases runtime slot");
			rotationParity(emitter, fx, mixer);
		} finally {
			mixer.clear();
			fx.destroy(true);
		}
	}

	private void compareProperty(ParticleEmitter emitter, AnimatedProperty property, FXRuntime fx, PhotonAnimationMixer mixer) {
		var definition = new PhotonAnimationDefinition();
		var track = (AnimationTrack) track("animation");
		track.targetId(emitter.id());
		track.properties().add(property);
		definition.tracks.add(track);
		var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
		var binding = emitter.getFXObjectType().resolveRuntimeBinding(emitter, property.type().path().substring("config.".length()));
		check(binding != null, "native runtime binding resolved " + property.type().path());
		var previousTime = 0.0;

		for (var time : List.of(0.0, 0.25, 0.75, 1.0, 2.5)) {
			property.apply(emitter, time);
			var nativeValue = Objects.requireNonNull(binding).slot(emitter).get();
			property.type().restore(emitter, property);
			playback.advance(time - previousTime);
			mixer.apply(List.of(playback), time, 0);
			var namedValue = binding.slot(emitter).get();

			for (var particleTime : List.of(0.25f, 0.75f)) {
				var random = (Supplier<Float>) () -> 0.3f;

				if (nativeValue instanceof NumberFunction3 nativeVector && namedValue instanceof NumberFunction3 namedVector) {
					var expected = nativeVector.get(particleTime, random);
					var actual = namedVector.get(particleTime, random);
					equal(expected.x, actual.x, "native vector X parity at " + time);
					equal(expected.y, actual.y, "native vector Y parity at " + time);
					equal(expected.z, actual.z, "native vector Z parity at " + time);
				} else if (nativeValue instanceof NumberFunction nativeFunction && namedValue instanceof NumberFunction namedFunction) {
					equal(nativeFunction.get(particleTime, random).doubleValue(), namedFunction.get(particleTime, random).doubleValue(),
							"native function/color parity at " + time);
				} else {
					check(nativeValue.equals(namedValue), "native boxed integer parity at " + time);
				}
			}

			mixer.apply(List.of(), time, 0);
			check(!binding.slot(emitter).isOverridden(), "parity clears owned slot at " + time);
			previousTime = time;
		}

		check(fx.getSceneObject(emitter.id()) == emitter, "property parity keeps original object");
	}

	private void rotationParity(ParticleEmitter emitter, FXRuntime fx, PhotonAnimationMixer mixer) {
		var property = (RotationAnimatedProperty) RotationPropertyType.INSTANCE.create(emitter);
		property.interpMode(RotationAnimatedProperty.InterpMode.SHORTEST);
		property.putKey(2, 0, 350);
		property.putKey(2, 1, 5);
		property.putKey(2, 4, 25);
		var definition = new PhotonAnimationDefinition();
		var track = (AnimationTrack) track("animation");
		track.targetId(emitter.id());
		track.properties().add(property);
		definition.tracks.add(track);
		var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
		var previousTime = 0.0;
		var base = new Quaternionf(emitter.transform().localRotation());

		for (var time : List.of(0.25, 0.75, 1.25)) {
			property.apply(emitter, time);
			var expected = new Quaternionf(emitter.transform().localRotation());
			RotationPropertyType.INSTANCE.restore(emitter, property);
			playback.advance(time - previousTime);
			mixer.apply(List.of(playback), time, 0);
			var actual = emitter.transform().localRotation();
			compareRotation(expected, actual, time, "native shortest rotation");

			mixer.apply(List.of(), time, 0);
			compareRotation(base, emitter.transform().localRotation(), time, "empty contribution restores base rotation");
			previousTime = time;
		}

		check(fx.getSceneObject(emitter.id()) == emitter, "rotation parity keeps original object");
	}

	private void compareRotation(Quaternionf expected, Quaternionf actual, double time, String name) {
		for (var axis : List.of(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1))) {
			var expectedDirection = expected.transform(axis, new Vector3f());
			var actualDirection = actual.transform(axis, new Vector3f());
			equal(expectedDirection.x, actualDirection.x, name + " basis " + axis + " X parity at " + time);
			equal(expectedDirection.y, actualDirection.y, name + " basis " + axis + " Y parity at " + time);
			equal(expectedDirection.z, actualDirection.z, name + " basis " + axis + " Z parity at " + time);
		}
	}

	private void rotationOwnership() {
		var emitter = new ParticleEmitter();
		var fx = new FXRuntime(new FXData(List.of(emitter)));
		var mixer = new PhotonAnimationMixer(fx, problem -> {
			throw new AssertionError(problem);
		});

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var type = RotationPropertyType.INSTANCE;
			type.apply(emitter, new float[]{17, 29, -43});
			var base = new Quaternionf(emitter.transform().localRotation());
			var baseValues = type.capture(emitter);
			var property = type.create(emitter);
			property.putKey(2, 0, 0);

			var definition = new PhotonAnimationDefinition();
			var track = (AnimationTrack) track("animation");
			track.targetId(emitter.id());
			track.properties().add(property);
			definition.tracks.add(track);

			var binding = new PhotonAnimationBinding();
			binding.target = emitter.id();
			binding.property = type.path();
			binding.component = 2;
			binding.offset = 350;
			binding.blend = PhotonAnimationBinding.Blend.ADD;
			definition.bindings.add(binding);
			var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
			playback.advance(0.25);

			var expectedValues = baseValues.clone();
			expectedValues[2] += binding.offset;
			type.apply(emitter, expectedValues);
			var expected = new Quaternionf(emitter.transform().localRotation());
			emitter.transform().localRotation(new Quaternionf(base));

			mixer.apply(List.of(playback), 0.25, 0);
			compareRotation(expected, emitter.transform().localRotation(), 0.25, "rotation ADD uses original base");
			compareRotationQuery(baseValues, mixer.query(emitter.id(), type.path(), false), "first ADD base query");
			compareRotationQuery(expectedValues, mixer.query(emitter.id(), type.path(), true), "first ADD composed query");

			var first = new Quaternionf(emitter.transform().localRotation());
			mixer.apply(List.of(playback), 0.25, 0);
			compareRotation(first, emitter.transform().localRotation(), 0.25, "repeated rotation ADD does not accumulate");
			compareRotationQuery(baseValues, mixer.query(emitter.id(), type.path(), false), "repeated ADD base query");
			compareRotationQuery(expectedValues, mixer.query(emitter.id(), type.path(), true), "repeated ADD composed query");

			mixer.apply(List.of(), 0.25, 0);
			compareRotation(base, emitter.transform().localRotation(), 0.25, "rotation ADD release restores multi-axis base");
			compareRotationQuery(baseValues, mixer.query(emitter.id(), type.path(), false), "released ADD base query");
		} finally {
			mixer.clear();
			fx.destroy(true);
		}
	}

	private void compareRotationQuery(float[] expected, float[] actual, String name) {
		for (var axis = 0; axis < expected.length; axis++) {
			equal(expected[axis], actual[axis], name + " axis " + axis);
		}
	}

	private void controlAndSignalParity() {
		CountingTarget nativeTarget = new CountingPositionTarget();
		CountingTarget namedTarget = new CountingPositionTarget();
		var nativeDefinition = controlledDefinition(nativeTarget);
		var namedDefinition = controlledDefinition(namedTarget);
		var nativeFX = new FXRuntime(new FXData(List.of(nativeTarget), nativeDefinition.timeline()));
		var controller = new PhotonAnimationController();
		controller.library(library(namedDefinition));
		var namedFX = new FXRuntime(new FXData(List.of(namedTarget, controller)));
		var nativeEffect = new ProbeEffect(nativeTarget);
		var namedEffect = new ProbeEffect(namedTarget);

		try {
			nativeFX.timelinePlayer.setAudioDispatch(false);
			namedFX.timelinePlayer.setAudioDispatch(false);
			namedFX.getRoot().setEffect(namedEffect);
			nativeFX.timelinePlayer.begin(nativeEffect);
			var access = (PhotonAnimationPlayerAccess) nativeFX.timelinePlayer;

			try (var runtime = new PhotonAnimationRuntime(namedFX, controller)) {
				runtime.setTimeScale(() -> 0.25);
				var root = (FXObject) namedFX.getRoot();
				var tick = Objects.requireNonNull(root.getOnUpdateTick());
				tick.run();
				var nativeInitialResets = nativeTarget.resets;
				var namedInitialResets = namedTarget.resets;

				for (var step = 1; step <= 4; step++) {
					var time = step * 0.25;
					access.lobecorp$evaluateAt(time);
					tick.run();
					equal(nativeTarget.transform().localPosition().x, namedTarget.transform().localPosition().x,
							"control/property fraction parity " + time);
					equal(nativeTarget.getSelfTimeScale(), namedTarget.getSelfTimeScale(), "speed fraction parity " + time);
					check(nativeTarget.isSelfActive() == namedTarget.isSelfActive(), "activator/control activity parity " + time);
				}

				equal(nativeTarget.resets - nativeInitialResets, namedTarget.resets - namedInitialResets, "control restart count parity");
				check(nativeEffect.signals.equals(namedEffect.signals), "signals see native control/property/speed order");
				check(nativeEffect.signals.size() == 2, "fractional signals fire exactly once");
				access.lobecorp$evaluateAt(1);
				runtime.setTimeScale(() -> 0);
				tick.run();
				check(nativeEffect.signals.size() == 2 && namedEffect.signals.size() == 2, "same-time evaluation does not repeat signals");
			}
		} finally {
			nativeFX.timelinePlayer.stop();
			nativeFX.destroy(true);
			namedFX.destroy(true);
			nativeEffect.stack.onFrameEnd();
			namedEffect.stack.onFrameEnd();
		}
	}

	private PhotonAnimationDefinition controlledDefinition(CountingTarget target) {
		var definition = new PhotonAnimationDefinition();
		definition.name = "ordered";
		var control = (ControlTrack) track("control");
		control.clips().add(new Clip(0.25, 2, 1).targetId(target.id()).randomSeed(false).seed(3));
		var activator = track("activator");
		activator.targetId(target.id());
		activator.clips().add(new Clip(0.25, 2, 1));
		var animation = (AnimationTrack) track("animation");
		animation.targetId(target.id());
		var property = PositionPropertyType.INSTANCE.create(target);
		property.addExprClip(0, new ExprClip(0, 4, "t * 10 + 7"));
		animation.properties().add(property);
		var speed = (SpeedTrack) track("speed");
		speed.targetId(target.id());
		speed.speedProperty().addExprClip(0, new ExprClip(0, 4, "t + 1"));
		var signal = (SignalTrack) track("signal");
		signal.displayName("ordered");
		signal.signals().add(new Signal(0.25, "first"));
		signal.signals().add(new Signal(0.75, "second"));
		var muted = (TrackGroup) track("group");
		muted.mute(true);
		var mutedSignal = (SignalTrack) track("signal");
		mutedSignal.signals().add(new Signal(0.5, "muted"));
		muted.children().add(mutedSignal);
		definition.tracks.addAll(List.of(control, activator, animation, speed, signal, muted));
		return definition;
	}

	private void postProcessFrames() {
		var definition = new PhotonAnimationDefinition();
		definition.name = "post";
		var track = track("post_process");
		var clip = new PostProcessClip(0, 10, 1).effect("pack(lobecorp:regression/unused_post_effect)");
		check(clip.effectPath() != null, "post-effect resource path parses without loading resource");
		track.clips().add(clip);
		definition.tracks.add(track);
		var controller = new PhotonAnimationController();
		controller.library(library(definition));
		var fx = new FXRuntime(new FXData(List.of(controller)));
		var effect = new ProbeEffect(new CountingTarget());

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);

			try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
				var tick = Objects.requireNonNull(root.getOnUpdateTick());
				var frame = Objects.requireNonNull(root.getOnUpdateFrame());
				tick.run();
				equal(0, effect.frames, "start tick does not submit post-processing");
				tick.run();
				equal(0, effect.frames, "advance tick does not submit post-processing");
				runtime.pause(definition.name);
				runtime.resume(definition.name);
				equal(0, effect.frames, "playback commands do not submit post-processing");
				frame.accept(0.25f);
				equal(1, effect.frames, "one post-effect submission per frame");
				frame.accept(0.75f);
				equal(2, effect.frames, "second frame produces one more submission");
				runtime.stop(definition.name);
				frame.accept(0.5f);
				equal(2, effect.frames, "stopped animation releases post-effect producer");
			}
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void tickEvaluation() {
		var target = new CountingTarget();
		var definition = evaluationDefinition(target);
		var controller = new PhotonAnimationController();
		controller.library(evaluationLibrary(definition));
		var fx = new FXRuntime(new FXData(List.of(target, controller)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);
			@SuppressWarnings("DataFlowIssue") // 使用宿主拥有的控制器，让真实原生信号走 Mixin 转发。
			var access = (PhotonAnimationRuntimeAccess) fx;
			access.lobecorp$reloadAnimations();

			try (var runtime = Objects.requireNonNull(access.lobecorp$animations())) {
				var playback = runtime.play(definition.name);
				var animation = (AnimationTrack) playback.definition.tracks.getFirst();
				var samples = new int[]{0};
				animation.properties().set(0, new CountingAnimatedProperty(animation.properties().getFirst(), samples));
				var tick = Objects.requireNonNull(root.getOnUpdateTick());
				tick.run();
				equal(3, target.transform().localPosition().x, "initial native sample applies parameter");

				samples[0] = 0;
				tick.run();
				equal(1, samples[0], "ordinary tick samples native property once");
				check(effect.signals.isEmpty(), "ordinary tick has no signal to reconcile");

				samples[0] = 0;
				tick.run();
				equal(2, samples[0], "signal tick preserves rule reconciliation sample");
				equal(3, target.transform().localPosition().x, "trigger input does not rewrite source formal");
				equal(9, Objects.requireNonNull(runtime.playback("evaluation_SIGNAL")).parameter("gain")[0],
						"signal starts its target with fixed input in the same tick");
				check(effect.signals.size() == 1 && effect.signals.getFirst().contains(":2.0:3.0:"),
						"native signal sees prefix composition before its rule changes parameter");

				samples[0] = 0;
				tick.run();
				equal(1, samples[0], "tick after signal returns to one native sample");

				samples[0] = 0;
				tick.run();
				equal(1, samples[0], "RELEASE reconciles remaining state without sampling disposed animation");
				check(runtime.playback(definition.name) == null, "natural RELEASE removes playback");
				equal(0, target.transform().localPosition().x, "natural RELEASE restores base in same tick");
				equal(13, Objects.requireNonNull(runtime.playback("evaluation_COMPLETE")).parameter("gain")[0],
						"completion trigger executes after source RELEASE");
				equal(1, effect.signals.size(), "reconciliation does not repeat native signal");
			}
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void silentRelease() {
		var target = new CountingTarget();
		var definition = evaluationDefinition(target);
		definition.duration = 1;
		var controller = new PhotonAnimationController();
		controller.library(evaluationLibrary(definition));
		var fx = new FXRuntime(new FXData(List.of(target, controller)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);

			try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
				runtime.previewOnly();
				runtime.play(definition.name);
				Objects.requireNonNull(root.getOnUpdateTick()).run();
				check(runtime.playback(definition.name) == null, "silent RELEASE removes playback without events");
				equal(0, target.transform().localPosition().x, "silent RELEASE still restores base");
				check(runtime.playback("evaluation_COMPLETE") == null, "silent RELEASE does not dispatch COMPLETE trigger");
				check(effect.signals.isEmpty(), "silent RELEASE does not dispatch native signal");
			}
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	/// 普通实例的静默重放继续执行内部编排；制作预览的暂停事件策略由独立夹具检查。
	private void quietRelease() {
		var target = new CountingTarget();
		var definition = evaluationDefinition(target);
		definition.markers.add(new PhotonAnimationDefinition.Marker(2, "mid", new CompoundTag()));
		var library = evaluationLibrary(definition);
		var startup = parameterRule(library, definition, PhotonAnimationRule.Event.START, 7);
		startup.animation = "";
		library.rules.add(startup);
		library.rules.add(parameterRule(library, definition, PhotonAnimationRule.Event.MARKER, 11));
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(target, controller)));
		var effect = new ProbeEffect(target);
		var previousScope = PhotonAnimationPreviewScope.active();

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);
			@SuppressWarnings("DataFlowIssue") // 挂接宿主，命名动画信号仍经过实际 Mixin 转发。
			var access = (PhotonAnimationRuntimeAccess) fx;
			PhotonAnimationPreviewScope.enter();

			try {
				access.lobecorp$reloadAnimations();
				fx.timelinePlayer.begin(effect);
				var runtime = Objects.requireNonNull(access.lobecorp$animations());
				var tick = Objects.requireNonNull(root.getOnUpdateTick());
				tick.run();
				equal(7, Objects.requireNonNull(runtime.playback("evaluation_START")).parameter("gain")[0],
						"quiet replay dispatches its internal START rule");
				check(runtime.playback(definition.name) != null, "quiet replay starts the configured default animation");
				tick.run();
				tick.run();
				equal(11, Objects.requireNonNull(runtime.playback("evaluation_MARKER")).parameter("gain")[0],
						"quiet replay dispatches its internal MARKER rule");
				equal(9, Objects.requireNonNull(runtime.playback("evaluation_SIGNAL")).parameter("gain")[0],
						"quiet named player dispatches its internal SIGNAL rule");
				tick.run();
				tick.run();
				check(runtime.playback(definition.name) == null, "quiet RELEASE removes its completed source");
				equal(0, target.transform().localPosition().x, "quiet RELEASE restores the native base");
				var completed = Objects.requireNonNull(runtime.playback("evaluation_COMPLETE"));
				equal(13, completed.parameter("gain")[0], "quiet replay dispatches its internal COMPLETE rule");
				check(effect.signals.isEmpty(), "quiet named player suppresses external signal callbacks");
				tick.run();
				check(runtime.playback("evaluation_COMPLETE") == completed, "quiet completion is dispatched once");
			} finally {
				PhotonAnimationPreviewScope.leave();
			}

			check(PhotonAnimationPreviewScope.active() == previousScope, "quiet completion scope is restored");
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	// 宿主负责运行态，监听器和静默作用域在 finally 逐项释放。
	private void quietSignalDispatch() {
		var target = new CountingTarget();
		var definition = new PhotonAnimationDefinition();
		definition.name = "quiet_target";
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		definition.parameters.add(parameter);
		var library = library(definition);
		var rule = new PhotonAnimationRule();
		rule.event = PhotonAnimationRule.Event.SIGNAL;
		rule.channel = "quiet";
		rule.name = "change";
		rule.target = definition.id.toString();
		var input = new PhotonAnimationInput();
		input.target = parameter.id.toString();
		input.value = new float[]{9};
		rule.inputs.add(input);
		library.rules.add(rule);
		var signal = (SignalTrack) track("signal");
		signal.displayName(rule.channel);
		signal.signals().add(new Signal(1, rule.name));
		var timeline = new Timeline();
		timeline.tracks().add(signal);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(target, controller), timeline));
		var effect = new ProbeEffect(target);
		var callbacks = new int[]{0};
		PhotonSignals.Listener listener = (source, _, _, _, _) -> {
			if (source == effect) {
				callbacks[0]++;
			}
		};
		PhotonSignals.register(listener);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.timelinePlayer.setSignalDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);
			@SuppressWarnings("DataFlowIssue") // 真正挂接宿主，主时间轴信号必须经实际 Mixin 路由到本实例。
			var access = (PhotonAnimationRuntimeAccess) fx;
			var previousScope = PhotonAnimationPreviewScope.active();
			PhotonAnimationPreviewScope.enter();

			try {
				access.lobecorp$reloadAnimations();
				fx.timelinePlayer.begin(effect);
				var runtime = Objects.requireNonNull(access.lobecorp$animations());
				var tick = Objects.requireNonNull(root.getOnUpdateTick());
				tick.run();
				tick.run();
				var playback = Objects.requireNonNull(runtime.playback(definition.name));
				equal(9, playback.parameter("gain")[0], "quiet native signal still invokes its internal rule");
				check(effect.signals.isEmpty(), "quiet native signal suppresses executor callback");
				equal(0, callbacks[0], "quiet native signal suppresses global listener");
				tick.run();
				check(runtime.playback(definition.name) == playback, "quiet native signal does not repeat after its interval");
			} finally {
				PhotonAnimationPreviewScope.leave();
			}

			check(PhotonAnimationPreviewScope.active() == previousScope, "quiet signal scope is restored");
		} finally {
			PhotonSignals.unregister(listener);
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private PhotonAnimationDefinition evaluationDefinition(CountingTarget target) {
		var definition = new PhotonAnimationDefinition();
		definition.name = "evaluation";
		definition.autoDuration = false;
		definition.duration = 4;
		definition.completion = PhotonAnimationDefinition.Completion.RELEASE;
		var animation = (AnimationTrack) track("animation");
		animation.targetId(target.id());
		var property = PositionPropertyType.INSTANCE.create(target);
		property.putKey(0, 0, 2);
		property.putKey(0, 4, 2);
		animation.properties().add(property);
		var signals = (SignalTrack) track("signal");
		signals.displayName("evaluation");
		signals.signals().add(new Signal(2, "change"));
		definition.tracks.addAll(List.of(animation, signals));
		var binding = new PhotonAnimationBinding();
		binding.target = target.id();
		binding.property = PositionPropertyType.INSTANCE.path();
		binding.source = PhotonAnimationBinding.Source.PARAMETER;
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		parameter.defaults = new float[]{3};
		definition.parameters.add(parameter);
		binding.parameter = parameter.id.toString();
		binding.component = 0;
		definition.bindings.add(binding);
		return definition;
	}

	private PhotonAnimationLibrary evaluationLibrary(PhotonAnimationDefinition definition) {
		var library = library(definition);
		library.rules.add(parameterRule(library, definition, PhotonAnimationRule.Event.SIGNAL, 9));
		library.rules.add(parameterRule(library, definition, PhotonAnimationRule.Event.COMPLETE, 13));
		return library;
	}

	private PhotonAnimationRule parameterRule(PhotonAnimationLibrary library, PhotonAnimationDefinition definition,
	                                          PhotonAnimationRule.Event event, float value) {
		var target = new PhotonAnimationDefinition();
		target.name = definition.name + "_" + event.name();
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		target.parameters.add(parameter);
		library.animations.add(target);
		var rule = new PhotonAnimationRule();
		rule.event = event;
		rule.animation = definition.id.toString();
		rule.target = target.id.toString();

		if (event == PhotonAnimationRule.Event.SIGNAL) {
			rule.channel = "evaluation";
			rule.name = "change";
		}

		var input = new PhotonAnimationInput();
		input.target = parameter.id.toString();
		input.value = new float[]{value};
		rule.inputs.add(input);
		return rule;
	}

	private void finishEvaluation(boolean startup) {
		var target = new CountingTarget();
		var definition = new PhotonAnimationDefinition();
		definition.name = "finish";
		var library = library(definition);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var samples = new int[]{0};
		var nativeTrack = (AnimationTrack) track("animation");
		nativeTrack.targetId(target.id());
		nativeTrack.properties().add(new CountingAnimatedProperty(PositionPropertyType.INSTANCE.create(target), samples));
		var timeline = new Timeline();
		timeline.tracks().add(nativeTrack);
		var fx = new FXRuntime(new FXData(List.of(target, controller), timeline));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);
			fx.timelinePlayer.begin(effect);

			try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
				var tick = Objects.requireNonNull(root.getOnUpdateTick());

				if (!startup) {
					tick.run();
				}

				samples[0] = 0;
				runtime.finish(true);
				equal(0, samples[0], "explicit finish does not evaluate native timeline, startup=" + startup);
				check(runtime.closed(), "FINISH closes animation runtime, startup=" + startup);
				check(fx.timelinePlayer.isFinished(), "FINISH stops native player, startup=" + startup);
			}
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void fractionalPlayback() {
		var definition = new PhotonAnimationDefinition();
		definition.loop = true;
		definition.autoDuration = false;
		definition.duration = 4;
		var animation = (AnimationTrack) track("animation");
		var property = PositionPropertyType.INSTANCE.create(new CountingTarget());
		property.putKey(0, 2, 0);
		animation.properties().add(property);
		definition.tracks.add(animation);
		var playback = new PhotonAnimationPlayback(definition, -1, 0, Map.of());
		playback.advance(3.5);
		equal(0.875, playback.progress(0), "ordinary progress uses full clock position");
		equal(1.75, playback.time(0), "ordinary position maps complete authored range");
		equal(1, playback.loops(1), "fractional sampling crosses loop boundary");
		equal(0.125, playback.progress(1), "fractional progress wraps at boundary");
		equal(0.25, playback.time(1), "fractional authored time wraps at boundary");
		playback.clock.pause();
		equal(0.875, playback.progress(1), "paused fractional progress stays frozen");
		equal(0, playback.loops(1), "paused fractional sampling does not add cycle");
		playback.clock.resume();
		playback.setDuration(8);
		equal(0.875, playback.clock.progress(), "retiming preserves normalized position");
		equal(0, playback.progress(1), "retimed fractional clock reaches boundary");
		equal(1, playback.clock.loops(1), "retimed fractional cycle count is preserved");
		playback.advance(1);
		equal(0.0625, playback.clock.progress(0.5), "fractional progress uses new duration");
		equal(0.125, playback.time(0.5), "retimed authored sample uses centralized progress");
		var preview = new PhotonAnimationPlayback(definition, 2, 0, Map.of(), false);
		preview.timelinePreview = true;
		preview.previewAt(0);
		preview.advance(1);
		equal(0, preview.time(0), "native preview first tick samples zero");
		equal(0.125, preview.progress(0.25), "native preview fraction uses authored sample");
		preview.advance(1);
		equal(1.25, preview.time(0.25), "native preview continues its own sample after clock HOLDING");
		equal(0.625, preview.progress(0.25), "native preview does not substitute clamped clock progress");
		preview.clock.pause();
		equal(1, preview.time(0.25), "paused native preview excludes render partial tick");
		equal(0.5, preview.progress(0.25), "paused native preview preserves N-1 protocol");
	}

	/// Data 夹具复用 emit 的 reload/begin 链；不向客户端引擎发射粒子。
	@SuppressWarnings("resource") // 运行态由 FXRuntime 拥有，finally 统一销毁。
	private void scenePreviewGate() {
		var target = new CountingTarget();
		var library = sceneRelationLibrary(target, true);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(target, controller), sceneRelationTimeline(target)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);
			var previousTick = Objects.requireNonNull(root.getOnUpdateTick());
			var previousFrame = Objects.requireNonNull(root.getOnUpdateFrame());
			var previousRetention = target.getKeepAlive();
			@SuppressWarnings("DataFlowIssue") // 已加载环境通过 Mixin 提供运行态接口。
			var access = (PhotonAnimationRuntimeAccess) fx;
			fx.timelinePlayer.begin(effect);
			access.lobecorp$reloadAnimations();
			var running = Objects.requireNonNull(access.lobecorp$animations());
			check(root.getOnUpdateTick() != previousTick, "named runtime wraps native tick callback");
			check(root.getOnUpdateFrame() != previousFrame, "named runtime wraps native frame callback");
			check(target.getKeepAlive() != previousRetention, "named runtime owns target retention");
			Objects.requireNonNull(root.getOnUpdateTick()).run();
			equal(6, target.transform().localPosition().x, "default named animation contributes before scene isolation");
			equal(9, Objects.requireNonNull(running.playback("scene_events")).parameter("gain")[0], "normal runtime still dispatches START trigger");

			access.lobecorp$setScenePreview(true);
			check(running.closed(), "scene mode immediately closes existing named runtime");
			sceneCallbacks(fx, target, previousTick, previousFrame, previousRetention, "enter scene");
			equal(4, target.transform().localPosition().x, "scene mode releases named contribution");

			for (var restart = 0; restart < 3; restart++) {
				PhotonAnimationPreviewScope.enter();

				try {
					access.lobecorp$reloadAnimations();
					fx.timelinePlayer.begin(effect);
				} finally {
					PhotonAnimationPreviewScope.leave();
				}

				running.trigger("change", new CompoundTag());
				Objects.requireNonNull(root.getOnUpdateTick()).run();
				Objects.requireNonNull(root.getOnUpdateFrame()).accept(0.25f);
				sceneCallbacks(fx, target, previousTick, previousFrame, previousRetention, "scene restart " + restart);
				equal(4.25, target.transform().localPosition().x, "scene restarts keep native fractional animation " + restart);
			}

			scenePreviewInstanceIsolation(fx);

			access.lobecorp$setScenePreview(false);
			check(access.lobecorp$animations() == null, "entering named mode does not resurrect runtime automatically");
			var previousSignals = effect.signals.size();
			PhotonAnimationPreviewScope.enterAuthoring(fx);

			try {
				access.lobecorp$reloadAnimations();
				var preview = Objects.requireNonNull(access.lobecorp$animations());
				preview.previewOnly();
				check(preview.playback("scene_events") == null, "authoring preview clears pending START rules");
				fx.timelinePlayer.setSignalDispatch(false);
				fx.timelinePlayer.begin(effect);
				check(preview.playback("add") == null, "named replay suppresses automatic default animation");
				preview.playPreview(library.animations.getFirst(), 0);
				equal(6, target.transform().localPosition().x, "explicit named preview uses native scene base");
				Objects.requireNonNull(root.getOnUpdateTick()).run();
				equal(1, Objects.requireNonNull(preview.playback("add")).parameter("gain")[0], "silent named replay keeps its own formal defaults");
				equal(9, Objects.requireNonNull(preview.playback("scene_events")).parameter("gain")[0],
						"silent authoring replay preserves the scene timeline internal SIGNAL rule");
				equal(previousSignals, effect.signals.size(), "silent authoring replay suppresses external signal callbacks");
				check(preview.playback("add") != null, "explicit named preview remains active");
			} finally {
				PhotonAnimationPreviewScope.leaveAuthoring();
			}

			access.lobecorp$setScenePreview(true);
			sceneCallbacks(fx, target, previousTick, previousFrame, previousRetention, "return to scene");
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	@SuppressWarnings("resource") // 只查询宿主状态，不拥有返回的运行态。
	private void sceneCallbacks(FXRuntime fx, CountingTarget target, Runnable tick, Consumer<Float> frame,
	                            BooleanSupplier retention, String name) {
		var root = (FXObject) fx.getRoot();
		check(((PhotonAnimationRuntimeAccess) fx).lobecorp$animations() == null, name + " has no default or rule runtime");
		check(root.getOnUpdateTick() == tick, name + " restores native tick identity");
		check(root.getOnUpdateFrame() == frame, name + " restores native frame identity");
		check(target.getKeepAlive() == retention, name + " restores target retention identity");
	}

	@SuppressWarnings("resource") // 模拟另一个世界或子 FX 宿主，不复用编辑器运行态。
	private void scenePreviewInstanceIsolation(FXRuntime scene) {
		var target = new CountingTarget();
		var controller = new PhotonAnimationController();
		controller.library(sceneRelationLibrary(target, true));
		var fx = new FXRuntime(new FXData(List.of(target, controller), sceneRelationTimeline(target)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			fx.getRoot().setEffect(effect);
			fx.timelinePlayer.begin(effect);
			@SuppressWarnings("DataFlowIssue") // 已加载环境通过 Mixin 提供运行态接口。
			var access = (PhotonAnimationRuntimeAccess) fx;
			PhotonAnimationPreviewScope.enter();

			try {
				access.lobecorp$reloadAnimations();
			} finally {
				PhotonAnimationPreviewScope.leave();
			}

			var runtime = Objects.requireNonNull(access.lobecorp$animations());
			Objects.requireNonNull(((FXObject) fx.getRoot()).getOnUpdateTick()).run();
			check(runtime.playback("add") != null, "other FX runtime remains able to start its default animation");
			equal(9, Objects.requireNonNull(runtime.playback("scene_events")).parameter("gain")[0], "other FX runtime preserves normal START dispatch");
			equal(6, target.transform().localPosition().x, "other FX runtime retains its own native and named layers");
			check(((PhotonAnimationRuntimeAccess) scene).lobecorp$animations() == null, "other FX does not reopen scene runtime");
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private void sceneTimelineBase() {
		var target = new CountingTarget();
		var library = sceneRelationLibrary(target, false);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(target, controller), sceneRelationTimeline(target)));
		var effect = new ProbeEffect(target);

		try {
			fx.timelinePlayer.setAudioDispatch(false);
			var root = (FXObject) fx.getRoot();
			root.setEffect(effect);
			fx.timelinePlayer.begin(effect);
			@SuppressWarnings("DataFlowIssue") // 已加载环境通过 Mixin 提供运行态接口。
			var access = (PhotonAnimationRuntimeAccess) fx;
			access.lobecorp$reloadAnimations();
			var runtime = Objects.requireNonNull(access.lobecorp$animations());
			runtime.previewOnly();
			runtime.play("add");
			runtime.play("multiply");
			var tick = Objects.requireNonNull(root.getOnUpdateTick());
			var frame = Objects.requireNonNull(root.getOnUpdateFrame());
			tick.run();
			equal(18, target.transform().localPosition().x, "native scene base then ADD then MULTIPLY");
			equal(4, runtime.property(target.id(), PositionPropertyType.INSTANCE.path(), false)[0], "base query excludes named contributions");
			tick.run();
			equal(21, target.transform().localPosition().x, "advancing scene updates base before named composition");
			runtime.stop("multiply");
			equal(7, target.transform().localPosition().x, "stopping multiply retains add on current scene base");
			runtime.stop("add");
			equal(5, target.transform().localPosition().x, "stopping all named animations restores current scene animation");
			runtime.play("add");
			runtime.play("multiply");
			tick.run();
			equal(24, target.transform().localPosition().x, "replaying named layers still follows scene clock");
			frame.accept(0.5f);
			equal(25.5, target.transform().localPosition().x, "fractional scene sample precedes named frame composition");
			frame.accept(0.5f);
			equal(25.5, target.transform().localPosition().x, "repeated render frame does not accumulate named layers");
			equal(6.5, runtime.property(target.id(), PositionPropertyType.INSTANCE.path(), false)[0], "fractional base query remains native");
			equal(25.5, runtime.property(target.id(), PositionPropertyType.INSTANCE.path(), true)[0], "composed query includes both named layers");
			runtime.stop("multiply");
			equal(8, target.transform().localPosition().x, "stop resamples remaining add at current native tick");
			runtime.stop("add");
			equal(6, target.transform().localPosition().x, "stop leaves native timeline active at its current tick");
			check(fx.getSceneObject(target.id()) == target, "scene and named animation keep the same target object");
		} finally {
			fx.destroy(true);
			effect.stack.onFrameEnd();
		}
	}

	private PhotonAnimationLibrary sceneRelationLibrary(CountingTarget target, boolean defaults) {
		var add = sceneRelationDefinition(target, "add", 2, PhotonAnimationBinding.Blend.ADD, 0);
		var multiply = sceneRelationDefinition(target, "multiply", 3, PhotonAnimationBinding.Blend.MULTIPLY, 1);
		var library = new PhotonAnimationLibrary();
		var observer = new PhotonAnimationDefinition();
		observer.name = "scene_events";
		library.animations.addAll(List.of(add, multiply, observer));
		library.defaultAnimation = defaults ? add.id.toString() : "";
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		parameter.defaults = new float[]{1};
		add.parameters.add(parameter);
		var observerParameter = new PhotonAnimationParameter();
		observerParameter.name = "gain";
		observer.parameters.add(observerParameter);

		for (var event : List.of(PhotonAnimationRule.Event.START, PhotonAnimationRule.Event.SIGNAL, PhotonAnimationRule.Event.TRIGGER)) {
			var rule = new PhotonAnimationRule();
			rule.event = event;
			rule.target = observer.id.toString();
			var input = new PhotonAnimationInput();
			input.target = observerParameter.id.toString();
			input.value = new float[]{9};
			rule.inputs.add(input);

			if (event != PhotonAnimationRule.Event.START) {
				rule.channel = event == PhotonAnimationRule.Event.SIGNAL ? "scene" : "";
				rule.name = "change";
			}

			library.rules.add(rule);
		}

		return library;
	}

	private PhotonAnimationDefinition sceneRelationDefinition(CountingTarget target, String name, float value,
	                                                          PhotonAnimationBinding.Blend blend, int priority) {
		var definition = new PhotonAnimationDefinition();
		definition.name = name;
		definition.priority = priority;
		var animation = (AnimationTrack) track("animation");
		animation.targetId(target.id());
		var property = PositionPropertyType.INSTANCE.create(target);
		property.putKey(0, 0, value);
		property.putKey(0, 8, value);
		animation.properties().add(property);
		definition.tracks.add(animation);
		definition.binding(target.id(), property.type().path()).blend = blend;
		return definition;
	}

	private Timeline sceneRelationTimeline(CountingTarget target) {
		var timeline = new Timeline();
		var animation = (AnimationTrack) track("animation");
		animation.targetId(target.id());
		var property = PositionPropertyType.INSTANCE.create(target);
		property.addExprClip(0, new ExprClip(0, 8, "t + 4"));
		animation.properties().add(property);
		var signals = (SignalTrack) track("signal");
		signals.displayName("scene");
		signals.signals().add(new Signal(0, "change"));
		timeline.tracks().addAll(List.of(animation, signals));
		return timeline;
	}

	private static PhotonAnimationDefinition allTracks() {
		var definition = new PhotonAnimationDefinition();
		var group = (TrackGroup) track("group");

		for (var key : List.of("activator", "control", "animation", "signal", "speed", "audio", "post_process")) {
			var child = track(key);
			child.displayName(key);
			group.children().add(child);
		}

		definition.tracks.add(group);
		return definition;
	}

	private static Track track(String key) {
		return Objects.requireNonNull(PhotonRegistries.TIMELINE_TRACKS.get(key)).create();
	}

	private static AnimatedProperty property(ParticleEmitter emitter, String path) {
		return emitter.getFXObjectType().animatableProperties(emitter).stream()
				.filter(type -> type.path().equals(path)).findFirst().orElseThrow().create(emitter);
	}

	private static PhotonAnimationLibrary library(PhotonAnimationDefinition definition) {
		var library = new PhotonAnimationLibrary();
		library.animations.add(definition);
		library.defaultAnimation = definition.id.toString();
		return library;
	}

	private static CompoundTag versioned(CompoundTag definition, int version) {
		var animations = new ListTag();
		animations.add(definition.copy());
		var tag = new CompoundTag();
		tag.putInt("version", version);
		tag.put("animations", animations);
		return tag;
	}

	private static HolderLookup.Provider provider() {
		return HolderLookup.Provider.create(Stream.empty());
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
			public float[] capture(FXObject target) {
				return new float[]{target.roll};
			}

			@Override
			public void apply(FXObject target, float[] values) {
				target.roll = values[0];
			}
		};
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

	/// 历史序列化夹具假定宿主有效；不覆盖真实 emit、引擎心跳或清空生命周期。
	private static class HistoryRuntime extends FXRuntime {
		private HistoryRuntime(FXData data) {
			super(data);
		}

		@Override
		public boolean isValid() {
			return true;
		}
	}

	/// 只计数真实原生属性 apply；克隆共享计数器，不替代原生采样或合成算法。
	private static class CountingAnimatedProperty extends AnimatedProperty {
		private final int[] samples;

		private CountingAnimatedProperty(AnimatedProperty source, int[] samples) {
			super(source.type(), source.base().clone(), source.snapshotChannels(), source.rangeMin(), source.rangeMax());
			this.samples = samples;
			restoreExprClipsFrom(source);
		}

		@Override
		public void apply(FXObject target, double time) {
			samples[0]++;
			super.apply(target, time);
		}

		@Override
		public AnimatedProperty copy() {
			return new CountingAnimatedProperty(this, samples);
		}
	}

	private static class CountingTarget extends EmptyFXObject {
		private int resets;

		@Override
		public void reset() {
			super.reset();
			resets++;
			roll = -100;
		}
	}

	private static class EmissionTarget extends CountingTarget implements PhotonEmissionControlAccess {
		private boolean stopped;
		private int clears;

		@Override
		public void reset() {
			super.reset();
			stopped = false;
		}

		@Override
		public boolean lobecorp$isEmissionStopped() {
			return stopped;
		}

		@Override
		public void lobecorp$setEmissionStopped(boolean value) {
			stopped = value;
		}

		@Override
		public void lobecorp$clearParticles() {
			clears++;
		}

		@Override
		public void lobecorp$setPreserveOnReset(boolean preserve) {
		}
	}

	private static class CountingPositionTarget extends CountingTarget {
		@Override
		public void reset() {
			super.reset();
			PositionPropertyType.INSTANCE.apply(this, new float[]{-100, 0, 0});
		}
	}

	private static class ProbeEffect implements IEffectExecutor {
		private final CountingTarget target;
		private final RandomSource random = RandomSource.create(1);
		private final PostEffectStack stack = new PostEffectStack();
		private final List<String> signals = new ArrayList<>();
		private int frames;

		private ProbeEffect(CountingTarget target) {
			this.target = target;
		}

		@Override
		public Level getLevel() {
			return null;
		}

		@Override
		public RandomSource getRandomSource() {
			return random;
		}

		@Override
		public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
			signals.add(channel + ":" + name + ":" + time + ":" + target.transform().localPosition().x + ":" + target.isSelfActive()
					+ ":" + target.getSelfTimeScale());
		}

		@Override
		public PostEffectStack postEffectSink() {
			frames++;
			return stack;
		}
	}

	private record ParticleFunction(float multiplier) implements NumberFunction {
		@Override
		public Number get(float time, Supplier<Float> random) {
			return time * multiplier + random.get();
		}

		@Override
		public void loadConfig(NumberFunctionConfig config) {
		}

		@Override
		public NumberFunction copy() {
			return new ParticleFunction(multiplier);
		}

		@Override
		public void createConfigurator(NumberFunctionConfigurator configurator) {
		}
	}
}
