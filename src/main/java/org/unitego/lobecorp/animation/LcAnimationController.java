package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.EasingType;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTimeline;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BoneAnimation;
import com.geckolib.cache.animation.Keyframe;
import com.geckolib.cache.animation.KeyframeStack;
import com.geckolib.cache.animation.keyframeevent.CustomInstructionKeyframeData;
import com.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import com.geckolib.cache.animation.keyframeevent.SoundKeyframeData;
import com.geckolib.loading.math.MolangQueries;
import com.geckolib.loading.math.MathValue;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.util.ClientUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LcAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
	public static final String CONTROLLER_NAME = "lobecorp_layered_animation";
	public static final double DEFAULT_SPEED = 1.0;
	private static long resourceReloadRevision;

	private final Map<String, LayerRuntime<T>> layers;

	public LcAnimationController(Consumer<LcAnimationLayerRegistrar> layerRegistration) {
		super(CONTROLLER_NAME, ignored -> PlayState.STOP);
		LayerCollector collector = new LayerCollector();
		layerRegistration.accept(collector);
		this.layers = collector.build();
	}

	public static void onAnimationResourcesReloaded() {
		resourceReloadRevision++;
	}

	public void play(String layerName, RawAnimation animation) {
		play(layerName, animation, DEFAULT_SPEED, false, null, null);
	}

	public void play(String layerName, RawAnimation animation, double speed, boolean reversed) {
		play(layerName, animation, speed, reversed, null, null);
	}

	public void play(String layerName, RawAnimation animation, int transitionTicks) {
		play(layerName, animation, DEFAULT_SPEED, false, transitionTicks, null);
	}

	public void play(String layerName, RawAnimation animation, int transitionTicks,
			LcTransitionMode transitionMode) {
		play(layerName, animation, DEFAULT_SPEED, false, transitionTicks,
				Objects.requireNonNull(transitionMode));
	}

	public void play(String layerName, RawAnimation animation, double speed, boolean reversed,
			@Nullable Integer transitionTicks) {
		play(layerName, animation, speed, reversed, transitionTicks, null);
	}

	public void play(String layerName, RawAnimation animation, double speed, boolean reversed,
			@Nullable Integer transitionTicks, @Nullable LcTransitionMode transitionMode) {
		if (speed < 0.0) {
			throw new IllegalArgumentException("Animation speed cannot be negative");
		}
		if (transitionTicks != null && transitionTicks < 0) {
			throw new IllegalArgumentException("Transition ticks cannot be negative");
		}
		LayerRuntime<T> layer = layer(layerName);
		layer.play(new Playback(Objects.requireNonNull(animation), speed, reversed,
				transitionTicks == null ? layer.definition.transitionTicks() : transitionTicks,
				transitionMode == null ? layer.definition.transitionMode() : transitionMode));
	}

	public void stop(String layerName) {
		layer(layerName).stop();
	}

	public void resume(String layerName) {
		layer(layerName).resume();
	}

	public void end(String layerName) {
		layer(layerName).end(null);
	}

	public void end(String layerName, int transitionTicks) {
		if (transitionTicks < 0) {
			throw new IllegalArgumentException("Transition ticks cannot be negative");
		}
		layer(layerName).end(transitionTicks);
	}

	public void setWeight(String layerName, float weight) {
		layer(layerName).setWeight(weight);
	}

	public void setSoundKeyframeHandler(String layerName,
			AnimationController.KeyframeEventHandler<T, SoundKeyframeData> handler) {
		layer(layerName).controller.setSoundKeyframeHandler(Objects.requireNonNull(handler));
	}

	public void setParticleKeyframeHandler(String layerName,
			AnimationController.KeyframeEventHandler<T, ParticleKeyframeData> handler) {
		layer(layerName).controller.setParticleKeyframeHandler(Objects.requireNonNull(handler));
	}

	public void setCustomInstructionKeyframeHandler(String layerName,
			AnimationController.KeyframeEventHandler<T, CustomInstructionKeyframeData> handler) {
		layer(layerName).controller.setCustomInstructionKeyframeHandler(Objects.requireNonNull(handler));
	}

	@Override
	public @Nullable ControllerState extractControllerState(T animatable, GeoRenderState renderState,
			AnimatableManager<T> manager, MolangQueries.Actor<T> ignoredActor, GeoModel<T> geoModel) {
		List<LcAnimationFrame.Layer> frameLayers = new ArrayList<>(layers.size());
		double renderTime = manager.getFirstRenderTick() - renderState.getAnimatableAge();
		for (LayerRuntime<T> layer : layers.values()) {
			ControllerState state = layer.extract(animatable, renderState, manager, geoModel, renderTime);
			if (layer.hasPose() && layer.displayWeight > 0.0F) {
				frameLayers.add(new LcAnimationFrame.Layer(layer.definition, state, layer.poseState,
						layer.poseTransitionProgress(), layer.displayWeight));
			}
		}
		renderState.addGeckolibData(LcAnimationDataTickets.ANIMATION_FRAME, new LcAnimationFrame(frameLayers));
		return null;
	}

	private LayerRuntime<T> layer(String layerName) {
		LayerRuntime<T> layer = layers.get(layerName);
		if (layer == null) {
			throw new IllegalArgumentException("Unknown animation layer: " + layerName);
		}
		return layer;
	}

	private static final class LayerCollector implements LcAnimationLayerRegistrar {
		private final Map<String, LcLayerDefinition> definitions = new LinkedHashMap<>();

		@Override
		public void add(LcLayerDefinition definition) {
			Objects.requireNonNull(definition);
			if (definitions.putIfAbsent(definition.name(), definition) != null) {
				throw new IllegalArgumentException("Duplicate animation layer: " + definition.name());
			}
		}

		private <A extends GeoAnimatable> Map<String, LayerRuntime<A>> build() {
			Map<String, LayerRuntime<A>> result = new LinkedHashMap<>(definitions.size());
			for (LcLayerDefinition definition : definitions.values()) {
				result.put(definition.name(), new LayerRuntime<>(definition));
			}
			return result;
		}
	}

	private static final class LayerRuntime<A extends GeoAnimatable> {
		private final LcLayerDefinition definition;
		private final LayerController<A> controller;
		private final LcAnimationFrame.PoseState poseState = new LcAnimationFrame.PoseState();
		private @Nullable Playback playback;
		private float requestedWeight = 1.0F;
		private float displayWeight;
		private float transitionFromWeight;
		private float transitionTargetWeight;
		private int weightTransitionTicks;
		private double weightTransitionElapsed;
		private double poseTransitionElapsed;
		private double lastAnimationAge = Double.NaN;
		private boolean paused;
		private boolean stopping;
		private long observedResourceReloadRevision = resourceReloadRevision;

		private LayerRuntime(LcLayerDefinition definition) {
			this.definition = definition;
			this.controller = new LayerController<>(CONTROLLER_NAME + "/" + definition.name(),
					() -> playback);
		}

		private void play(Playback playback) {
			boolean wasEmpty = this.playback == null;
			boolean wasStopping = stopping;
			if (!wasEmpty) {
				poseState.beginTransition();
			}
			this.playback = playback;
			this.paused = false;
			this.stopping = false;
			this.poseTransitionElapsed = wasEmpty ? playback.transitionTicks() : 0.0;
			controller.setTransitionTicks(0);
			controller.prepareRestart();
			if (wasEmpty || wasStopping) {
				beginWeightTransition(requestedWeight, playback.transitionTicks());
			}
		}

		private void stop() {
			if (playback != null && !stopping) {
				paused = true;
			}
		}

		private void resume() {
			if (playback != null && !stopping) {
				paused = false;
			}
		}

		private void end(@Nullable Integer transitionTicks) {
			if (playback == null) {
				return;
			}
			paused = false;
			stopping = true;
			beginWeightTransition(0.0F,
					transitionTicks == null ? playback.transitionTicks() : transitionTicks);
		}

		private void setWeight(float weight) {
			requestedWeight = Mth.clamp(weight, 0.0F, 1.0F);
			if (!stopping) {
				beginWeightTransition(requestedWeight, transitionTicks());
			}
		}

		private @Nullable ControllerState extract(A animatable, GeoRenderState renderState,
				AnimatableManager<A> manager, GeoModel<A> geoModel, double renderTime) {
			refreshAnimationResources();
			advanceTransitions(renderState.getAnimatableAge());
			if (playback == null) {
				return null;
			}
			if (stopping && displayWeight <= 0.0F) {
				playback = null;
				paused = false;
				stopping = false;
				controller.reset();
				poseState.clear();
				return null;
			}
			boolean sequentialTransition = playback.transitionMode() == LcTransitionMode.SEQUENTIAL
					&& (poseTransitionProgress() < 1.0F || displayWeight != transitionTargetWeight);
			controller.setAnimationSpeed(paused || sequentialTransition ? 0.0 : playback.speed());
			MolangQueries.Actor<A> actor = new MolangQueries.Actor<>(animatable, renderState, controller,
					renderTime, renderState.getPartialTick(), Objects.requireNonNull(ClientUtil.getLevel()),
					Objects.requireNonNull(ClientUtil.getClientPlayer()), ClientUtil.getCameraPos());
			ControllerState state = controller.extractControllerState(animatable, renderState, manager, actor, geoModel);
			if (!paused && !stopping && playback.autoEnds() && controller.hasAnimationFinished()) {
				end(null);
			}
			return state;
		}

		private boolean hasPose() {
			return playback != null;
		}

		private void refreshAnimationResources() {
			if (observedResourceReloadRevision == resourceReloadRevision) {
				return;
			}
			observedResourceReloadRevision = resourceReloadRevision;
			if (playback != null) {
				controller.reset();
			}
		}

		private void beginWeightTransition(float targetWeight, int transitionTicks) {
			transitionFromWeight = displayWeight;
			transitionTargetWeight = targetWeight;
			weightTransitionTicks = transitionTicks;
			weightTransitionElapsed = 0.0;
			if (transitionTicks == 0) {
				displayWeight = targetWeight;
			}
		}

		private void advanceTransitions(double animationAge) {
			double ageDelta = Double.isNaN(lastAnimationAge) ? 0.0 : Math.max(0.0, animationAge - lastAnimationAge);
			lastAnimationAge = animationAge;
			if (paused && !stopping) {
				return;
			}
			boolean poseTransitioning = !stopping && poseTransitionProgress() < 1.0F;
			advanceWeightTransition(ageDelta);
			if (poseTransitioning) {
				poseTransitionElapsed += ageDelta;
			}
		}

		private void advanceWeightTransition(double ageDelta) {
			if (displayWeight == transitionTargetWeight) {
				return;
			}
			int transitionTicks = weightTransitionTicks;
			if (transitionTicks == 0) {
				displayWeight = transitionTargetWeight;
				return;
			}
			weightTransitionElapsed += ageDelta;
			float progress = (float)Mth.clamp(weightTransitionElapsed / transitionTicks, 0.0, 1.0);
			displayWeight = Mth.lerp(progress, transitionFromWeight, transitionTargetWeight);
		}

		private float poseTransitionProgress() {
			if (playback == null || playback.transitionTicks() == 0) {
				return 1.0F;
			}
			return (float)Mth.clamp(poseTransitionElapsed / playback.transitionTicks(), 0.0, 1.0);
		}

		private int transitionTicks() {
			return playback == null ? definition.transitionTicks() : playback.transitionTicks();
		}
	}

	private static final class LayerController<A extends GeoAnimatable> extends AnimationController<A> {
		private final Supplier<@Nullable Playback> playback;

		private LayerController(String name, Supplier<@Nullable Playback> playback) {
			super(name, 0, test -> {
				Playback current = playback.get();
				return current == null ? PlayState.STOP : test.setAndContinue(current.animation());
			});
			this.playback = playback;
		}

		private void prepareRestart() {
			this.currentRawAnimation = null;
		}

		@Override
		protected void initializeNewAnimation(A animatable, GeoRenderState renderState, GeoModel<A> geoModel,
				double previousAnimationSpeed, int previousTransitionTicks) {
			super.initializeNewAnimation(animatable, renderState, geoModel, previousAnimationSpeed,
					previousTransitionTicks);
			Playback current = playback.get();
			if (current != null && current.reversed() && currentRawAnimation != null) {
				this.timeline = createReversedTimeline(currentRawAnimation, animatable, geoModel,
						triggeredAnimTime > 0 ? previousTransitionTicks : transitionTicks);
				this.animationPoint = timeline == null ? null
						: timeline.createAnimationPoint(timelineTime, null, easingOverride);
			}
		}
	}

	private static <A extends GeoAnimatable> @Nullable AnimationTimeline createReversedTimeline(
			RawAnimation rawAnimation, A animatable, GeoModel<A> geoModel, int transitionTicks) {
		List<RawAnimation.Stage> rawStages = rawAnimation.getAnimationStages();
		List<AnimationTimeline.Stage> stages = new ArrayList<>(rawStages.size());
		double transitionTime = transitionTicks / 20.0;
		double currentTime = 0.0;
		for (int i = rawStages.size() - 1; i >= 0; i--) {
			RawAnimation.Stage rawStage = rawStages.get(i);
			Animation animation = AnimationProcessor.getOrCreateAnimation(rawStage, animatable, geoModel);
			if (animation == null) {
				continue;
			}
			Animation reversedAnimation = reverse(animation);
			if (transitionTime > 0.0) {
				stages.add(new AnimationTimeline.Stage(currentTime, currentTime + transitionTime, true,
						reversedAnimation, null));
				currentTime += transitionTime;
			}
			stages.add(new AnimationTimeline.Stage(currentTime, currentTime + reversedAnimation.length(), false,
					reversedAnimation, rawStage.loopType()));
			currentTime += reversedAnimation.length();
		}
		if (stages.isEmpty()) {
			return null;
		}
		if (transitionTime > 0.0) {
			Animation lastAnimation = Objects.requireNonNull(stages.getLast().animation());
			stages.add(new AnimationTimeline.Stage(currentTime, currentTime + transitionTime, true,
					lastAnimation, null));
		}
		return new AnimationTimeline(stages.toArray(new AnimationTimeline.Stage[0]));
	}

	private static Animation reverse(Animation animation) {
		BoneAnimation[] sourceBones = animation.boneAnimations();
		BoneAnimation[] reversedBones = new BoneAnimation[sourceBones.length];
		for (int i = 0; i < sourceBones.length; i++) {
			BoneAnimation bone = sourceBones[i];
			reversedBones[i] = new BoneAnimation(bone.boneName(),
					reverse(bone.rotationKeyFrames(), animation.length()),
					reverse(bone.positionKeyFrames(), animation.length()),
					reverse(bone.scaleKeyFrames(), animation.length()));
		}
		Animation.KeyframeMarkers markers = animation.keyframeMarkers();
		return Animation.create(animation.name(), animation.length(), animation.loopType(), reversedBones,
				new Animation.KeyframeMarkers(reverse(markers.sounds(), animation.length()),
						reverse(markers.particles(), animation.length()),
						reverse(markers.customInstructions(), animation.length())));
	}

	private static KeyframeStack reverse(KeyframeStack stack, double animationLength) {
		return new KeyframeStack(reverse(stack.xKeyframes(), animationLength),
				reverse(stack.yKeyframes(), animationLength), reverse(stack.zKeyframes(), animationLength));
	}

	private static Keyframe[] reverse(Keyframe[] keyframes, double animationLength) {
		Keyframe[] reversed = new Keyframe[keyframes.length];
		for (int i = 0; i < keyframes.length; i++) {
			Keyframe source = keyframes[keyframes.length - 1 - i];
			reversed[i] = new Keyframe(animationLength - source.startTime() - source.length(), source.length(),
					source.endValue(), source.startValue(), reverse(source.easingType()),
					reverseEasingArguments(source));
		}
		return reversed;
	}

	private static EasingType reverse(EasingType easingType) {
		if (easingType == EasingType.CATMULLROM) {
			return easingType;
		}
		return easingArgument -> {
			var transformer = easingType.buildTransformer(easingArgument);
			return value -> 1.0 - transformer.apply(1.0 - value);
		};
	}

	private static MathValue[] reverseEasingArguments(Keyframe keyframe) {
		MathValue[] arguments = keyframe.easingArgs().clone();
		if (keyframe.easingType() == EasingType.CATMULLROM && arguments.length >= 2) {
			MathValue first = arguments[0];
			arguments[0] = arguments[1];
			arguments[1] = first;
		}
		return arguments;
	}

	private static SoundKeyframeData[] reverse(SoundKeyframeData[] markers, double animationLength) {
		SoundKeyframeData[] reversed = new SoundKeyframeData[markers.length];
		for (int i = 0; i < markers.length; i++) {
			SoundKeyframeData marker = markers[markers.length - 1 - i];
			reversed[i] = new SoundKeyframeData(animationLength - marker.getTime(), marker.getSound(),
					marker.getLocatorName());
		}
		return reversed;
	}

	private static ParticleKeyframeData[] reverse(ParticleKeyframeData[] markers, double animationLength) {
		ParticleKeyframeData[] reversed = new ParticleKeyframeData[markers.length];
		for (int i = 0; i < markers.length; i++) {
			ParticleKeyframeData marker = markers[markers.length - 1 - i];
			reversed[i] = new ParticleKeyframeData(animationLength - marker.getTime(), marker.getEffect(),
					marker.getLocatorName());
		}
		return reversed;
	}

	private static CustomInstructionKeyframeData[] reverse(CustomInstructionKeyframeData[] markers,
			double animationLength) {
		CustomInstructionKeyframeData[] reversed = new CustomInstructionKeyframeData[markers.length];
		for (int i = 0; i < markers.length; i++) {
			CustomInstructionKeyframeData marker = markers[markers.length - 1 - i];
			reversed[i] = new CustomInstructionKeyframeData(animationLength - marker.getTime(),
					marker.getInstructions());
		}
		return reversed;
	}

	private record Playback(RawAnimation animation, double speed, boolean reversed, int transitionTicks,
			LcTransitionMode transitionMode) {
		private boolean autoEnds() {
			List<RawAnimation.Stage> stages = animation.getAnimationStages();
			return !stages.isEmpty() && stages.getLast().loopType() == LoopType.PLAY_ONCE;
		}
	}
}
