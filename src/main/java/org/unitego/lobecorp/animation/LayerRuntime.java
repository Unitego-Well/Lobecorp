package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import com.geckolib.loading.math.MolangQueries;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.util.ClientUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class LayerRuntime<A extends GeoAnimatable> {
	public final LcLayerDefinition definition;
	public final LayerController<A> controller;
	public final LcAnimationFrame.PoseState poseState = new LcAnimationFrame.PoseState();
	private @Nullable Playback playback;
	private @Nullable A animatable;
	private AnimationController.@Nullable KeyframeEventHandler<A, ParticleKeyframeData> particleKeyframeHandler;
	private float requestedWeight = 1.0F;
	public float displayWeight;
	private float transitionFromWeight;
	private float transitionTargetWeight;
	private int weightTransitionTicks;
	private double weightTransitionElapsed;
	private double poseTransitionElapsed;
	private double lastAnimationAge = Double.NaN;
	private boolean paused;
	private boolean stopping;
	private long observedResourceReloadRevision = LcAnimationController.resourceReloadRevision;

	LayerRuntime(LcLayerDefinition definition) {
		this.definition = definition;
		this.controller = new LayerController<>(LcAnimationController.CONTROLLER_NAME + "/" + definition.name(),
				() -> playback);
		this.controller.setParticleKeyframeHandler(event -> {
			Playback current = playback;
			if (current != null) {
				LcAnimationEffectHooks.particle(definition.name(), event, current.speed());
			}
			if (particleKeyframeHandler != null) {
				particleKeyframeHandler.handle(event);
			}
		});
	}

	public void play(Playback playback) {
		if (animatable != null) {
			LcAnimationEffectHooks.play(animatable, definition.name());
		}
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

	public void stop() {
		if (playback == null || stopping) {
			return;
		}
		paused = true;
		if (animatable != null) {
			LcAnimationEffectHooks.pause(animatable, definition.name());
		}
	}

	public void resume() {
		if (playback == null || stopping) {
			return;
		}
		paused = false;
		if (animatable != null) {
			LcAnimationEffectHooks.resume(animatable, definition.name(), playback.speed());
		}
	}

	public void end(@Nullable Integer transitionTicks) {
		if (playback == null) {
			return;
		}
		paused = false;
		stopping = true;
		if (animatable != null) {
			LcAnimationEffectHooks.end(animatable, definition.name());
		}
		beginWeightTransition(0.0F,
				transitionTicks == null ? playback.transitionTicks() : transitionTicks);
	}

	public void setWeight(float weight) {
		requestedWeight = Mth.clamp(weight, 0.0F, 1.0F);
		if (!stopping) {
			beginWeightTransition(requestedWeight, transitionTicks());
		}
	}

	public void setSpeed(double speed) {
		if (playback == null || stopping) {
			return;
		}
		playback = new Playback(playback.animation(), speed, playback.reversed(),
				playback.transitionTicks(), playback.transitionMode());
		if (animatable != null) {
			LcAnimationEffectHooks.speed(animatable, definition.name(), speed);
		}
	}

	public void setParticleKeyframeHandler(AnimationController.KeyframeEventHandler<A, ParticleKeyframeData> handler) {
		particleKeyframeHandler = handler;
	}

	public void setAnimationStateHandler(AnimationController.AnimationStateHandler<A> handler) {
		controller.setAnimationStateHandler(handler);
	}

	public void clearAnimationStateHandler() {
		controller.clearAnimationStateHandler();
	}

	public @Nullable ControllerState extract(A animatable, GeoRenderState renderState,
	                                          AnimatableManager<A> manager, GeoModel<A> geoModel, double renderTime) {
		this.animatable = animatable;
		refreshAnimationResources();
		advanceTransitions(renderState.getAnimatableAge());
		boolean customHandler = controller.hasCustomAnimationStateHandler();
		if (playback == null && !customHandler) {
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
		boolean sequentialTransition = playback != null
				&& playback.transitionMode() == LcTransitionMode.SEQUENTIAL
				&& (poseTransitionProgress() < 1.0F || displayWeight != transitionTargetWeight);
		controller.setAnimationSpeed(paused || sequentialTransition ? 0.0
				: playback == null ? LcAnimationController.DEFAULT_SPEED : playback.speed());
		MolangQueries.Actor<A> actor = new MolangQueries.Actor<>(animatable, renderState, controller,
				renderTime, renderState.getPartialTick(), Objects.requireNonNull(ClientUtil.getLevel()),
				Objects.requireNonNull(ClientUtil.getClientPlayer()), ClientUtil.getCameraPos());
		ControllerState state = controller.extractControllerState(animatable, renderState, manager, actor, geoModel);
		if (customHandler) {
			syncCustomAnimationState();
		}
		if (!customHandler && !stopping && !controller.hasResolvedAnimation()) {
			end(null);
		}
		if (!customHandler && playback != null && !paused && !stopping && playback.autoEnds()
				&& controller.hasAnimationFinished()) {
			end(null);
		}
		return state;
	}

	private void syncCustomAnimationState() {
		RawAnimation animation = controller.currentAnimation();
		if (controller.getPlayState() == PlayState.STOP || animation == null) {
			if (playback != null) {
				end(null);
			}
			return;
		}
		if (playback != null && playback.animation() == animation && !stopping) {
			return;
		}
		boolean wasEmpty = playback == null;
		boolean wasStopping = stopping;
		if (!wasEmpty) {
			poseState.beginTransition();
		}
		playback = new Playback(animation, LcAnimationController.DEFAULT_SPEED, false,
				definition.transitionTicks(), definition.transitionMode());
		paused = false;
		stopping = false;
		poseTransitionElapsed = wasEmpty ? definition.transitionTicks() : 0.0;
		if (wasEmpty || wasStopping) {
			beginWeightTransition(requestedWeight, definition.transitionTicks());
		}
	}

	public boolean hasPose() {
		return playback != null;
	}

	private void refreshAnimationResources() {
		if (observedResourceReloadRevision == LcAnimationController.resourceReloadRevision) {
			return;
		}
		observedResourceReloadRevision = LcAnimationController.resourceReloadRevision;
		if (playback == null) {
			return;
		}
		if (animatable != null) {
			LcAnimationEffectHooks.end(animatable, definition.name());
		}
		controller.reset();
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

	public void advanceWeightTransition(double ageDelta) {
		if (displayWeight == transitionTargetWeight) {
			return;
		}
		int transitionTicks = weightTransitionTicks;
		if (transitionTicks == 0) {
			displayWeight = transitionTargetWeight;
			return;
		}
		weightTransitionElapsed += ageDelta;
		float progress = (float) Mth.clamp(weightTransitionElapsed / transitionTicks, 0.0, 1.0);
		displayWeight = Mth.lerp(progress, transitionFromWeight, transitionTargetWeight);
	}

	public float poseTransitionProgress() {
		if (playback == null || playback.transitionTicks() == 0) {
			return 1.0F;
		}
		return (float) Mth.clamp(poseTransitionElapsed / playback.transitionTicks(), 0.0, 1.0);
	}

	private int transitionTicks() {
		return playback == null ? definition.transitionTicks() : playback.transitionTicks();
	}
}
