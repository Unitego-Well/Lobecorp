package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.EasingType;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class LcAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
	public static final String CONTROLLER_NAME = "lobecorp_layered_animation";
	public static final double DEFAULT_SPEED = 1.0;
	public static long resourceReloadRevision;

	private final Map<String, LayerRuntime<T>> layers;
	private final Map<String, TriggerableAnimation> triggerableAnimations = new LinkedHashMap<>();
	private final Map<String, List<String>> outputSuppressors = new LinkedHashMap<>();
	@Nullable
	private String activeTriggerLayer;

	public LcAnimationController(Consumer<LcAnimationLayerRegistrar> layerRegistration) {
		super(CONTROLLER_NAME, ignored -> PlayState.STOP);
		LayerCollector collector = new LayerCollector();
		layerRegistration.accept(collector);
		this.layers = collector.build();
	}

	public static void onAnimationResourcesReloaded() {
		resourceReloadRevision++;
	}

	public LcAnimationController<T> triggerableAnim(String layerName, String triggerName,
			RawAnimation animation) {
		layer(layerName);
		super.triggerableAnim(triggerName, animation);
		triggerableAnimations.put(triggerName, new TriggerableAnimation(layerName, animation));
		return this;
	}

	public LcAnimationController<T> suppressLayerOutputWhilePlaying(String layerName,
			String suppressingLayerName) {
		layer(layerName);
		layer(suppressingLayerName);
		List<String> suppressors = outputSuppressors.computeIfAbsent(layerName, ignored -> new ArrayList<>());
		if (!suppressors.contains(suppressingLayerName)) {
			suppressors.add(suppressingLayerName);
		}
		return this;
	}

	@Override
	public boolean triggerAnimation(String triggerName) {
		TriggerableAnimation trigger = triggerableAnimations.get(triggerName);
		if (trigger == null || !super.triggerAnimation(triggerName)) {
			return false;
		}
		LayerRuntime<T> layer = layer(trigger.layerName());
		layer.play(new Playback(trigger.animation(), DEFAULT_SPEED, false,
				layer.definition.transitionTicks(), layer.definition.transitionMode()));
		activeTriggerLayer = trigger.layerName();
		return true;
	}

	@Override
	public boolean stopTriggeredAnimation() {
		if (!super.stopTriggeredAnimation()) {
			return false;
		}
		if (activeTriggerLayer != null) {
			layer(activeTriggerLayer).end(null);
			activeTriggerLayer = null;
		}
		return true;
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

	public void setSpeed(String layerName, double speed) {
		if (speed < 0.0) {
			throw new IllegalArgumentException("Animation speed cannot be negative");
		}
		layer(layerName).setSpeed(speed);
	}

	public void setSoundKeyframeHandler(String layerName,
			AnimationController.KeyframeEventHandler<T, SoundKeyframeData> handler) {
		layer(layerName).controller.setSoundKeyframeHandler(Objects.requireNonNull(handler));
	}

	public void setParticleKeyframeHandler(String layerName,
			AnimationController.KeyframeEventHandler<T, ParticleKeyframeData> handler) {
		layer(layerName).setParticleKeyframeHandler(Objects.requireNonNull(handler));
	}

	public void setCustomInstructionKeyframeHandler(String layerName,
			AnimationController.KeyframeEventHandler<T, CustomInstructionKeyframeData> handler) {
		layer(layerName).controller.setCustomInstructionKeyframeHandler(Objects.requireNonNull(handler));
	}

	public void setAnimationStateHandler(String layerName, AnimationController.AnimationStateHandler<T> handler) {
		layer(layerName).setAnimationStateHandler(Objects.requireNonNull(handler));
	}

	public @Nullable RawAnimation currentAnimation(String layerName) {
		return layer(layerName).controller.currentAnimation();
	}

	public void clearAnimationStateHandler(String layerName) {
		layer(layerName).clearAnimationStateHandler();
	}

	@Override
	public @Nullable ControllerState extractControllerState(T animatable, GeoRenderState renderState,
			AnimatableManager<T> manager, MolangQueries.Actor<T> ignoredActor, GeoModel<T> geoModel) {
		Map<String, @Nullable ControllerState> controllerStates = new LinkedHashMap<>();
		for (LayerRuntime<T> layer : layers.values()) {
			controllerStates.put(layer.definition.name(),
					layer.extract(animatable, renderState, manager, geoModel,
							manager.getFirstRenderTick() - renderState.getAnimatableAge()));
		}
		List<LcAnimationFrame.Layer> frameLayers = new ArrayList<>(layers.size());
		for (LayerRuntime<T> layer : layers.values()) {
			float outputWeight = layer.displayWeight;
			for (String suppressor : outputSuppressors.getOrDefault(layer.definition.name(), List.of())) {
				outputWeight *= 1.0F - layer(suppressor).displayWeight;
			}
			if (layer.hasPose() && outputWeight > 0.0F) {
				frameLayers.add(new LcAnimationFrame.Layer(layer.definition,
						controllerStates.get(layer.definition.name()), layer.poseState,
						layer.poseTransitionProgress(), outputWeight));
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

	public static <A extends GeoAnimatable> @Nullable AnimationTimeline createReversedTimeline(
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

}
