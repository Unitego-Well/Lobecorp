package org.unitego.lobecorp.animation;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTimeline;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.animation.Animation;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import org.unitego.lobecorp.animation.LcAnimationControllerRuntime.ControllerRuntime;
import org.unitego.lobecorp.animation.LcAnimationControllerRuntime.RuntimeData;
import org.unitego.lobecorp.animation.LcAnimationPose.*;

import static org.unitego.lobecorp.animation.LcAnimationControllerRuntime.transitionProgress;

public class LcAnimationControllerIntegration {
	private static final String RUNTIME_DATA_ID = "lobecorp_animation_controller_runtime";
	private static final DataTicket<RuntimeData> RUNTIME_DATA = DataTicket.create(RUNTIME_DATA_ID, RuntimeData.class);

	public static <R extends GeoRenderState> void apply(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
		AnimatableManager<?> manager = renderPassInfo.renderState().getGeckolibData(DataTickets.ANIMATABLE_MANAGER);
		if (manager == null) {
			return;
		}

		RuntimeData runtimeData = manager.getAnimatableData(RUNTIME_DATA);
		if (runtimeData == null) {
			runtimeData = new RuntimeData();
			manager.setAnimatableData(RUNTIME_DATA, runtimeData);
		}

		ControllerState[] controllerStates = renderPassInfo.renderState()
				.getOrDefaultGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES, new ControllerState[0]);
		double renderAge = renderPassInfo.renderState().getAnimatableAge();
		int stateIndex = 0;
		for (AnimationController<?> controller : manager.getAnimationControllers().values()) {
			ControllerRuntime controllerRuntime = runtimeData.controllers.computeIfAbsent(controller, ignored -> new ControllerRuntime());
			LcAnimationControllerTransitions<?> transitions = LcAnimationControllerTransitions.of(controller);
			ControllerState state = controller.isAnimatingBones() && stateIndex < controllerStates.length
					? controllerStates[stateIndex++] : null;
			boolean stoppedAtAnimationEnd = state != null && controller.getPlayState() == PlayState.STOP
					&& state.animationPoint().hasFinished();
			boolean active = controller.isAnimatingBones() && state != null && !stoppedAtAnimationEnd;
			if (active) {
				applyActiveController(renderPassInfo, snapshots, controller, state, transitions, controllerRuntime, renderAge);
			} else if (controllerRuntime.initialized && controllerRuntime.weight > 0) {
				applyControllerExit(snapshots, controller, controllerRuntime, renderAge, transitions);
			} else if (controllerRuntime.initialized && controllerRuntime.weight == 0) {
				controllerRuntime.reset(transitions);
			}
		}
	}

	private static void applyControllerExit(BoneSnapshots snapshots, AnimationController<?> controller, ControllerRuntime controllerRuntime, double renderAge, LcAnimationControllerTransitions<?> transitions) {
		if (!controllerRuntime.weightTransition || controllerRuntime.weightTarget != 0) {
			controllerRuntime.controllerExiting = true;
			controllerRuntime.beginWeightTransition(0, renderAge, transitions.lobecorp$getFadeOutTicks(), transitions);
		}
		controllerRuntime.updateWeight(renderAge, transitions);
		if (controllerRuntime.lastPose != null) {
			LcAnimationPose.applyPose(controllerRuntime.lastPose, controllerRuntime.weight * controllerRuntime.animationWeight,
					controllerRuntime.additive,
					transitions.lobecorp$getFadeOutRotationTransitionMode(), controller, snapshots);
		}
	}

	private static <R extends GeoRenderState> void applyActiveController(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots, AnimationController<?> controller, ControllerState state, LcAnimationControllerTransitions<?> transitions, ControllerRuntime controllerRuntime, double renderAge) {
		updateAnimationTransition(renderPassInfo, controller, state, transitions, controllerRuntime, renderAge);
		applyActivePose(snapshots, controller, transitions, controllerRuntime, renderAge);
	}

	private static <R extends GeoRenderState> void updateAnimationTransition(RenderPassInfo<R> renderPassInfo, AnimationController<?> controller, ControllerState state, LcAnimationControllerTransitions<?> transitions, ControllerRuntime controllerRuntime, double renderAge) {
		Pose targetPose = LcAnimationPose.createPose(renderPassInfo, state, controller, transitions.lobecorp$getBlendType());
		Animation animation = state.animationPoint().animation();
		RawAnimation rawAnimation = controller.getCurrentRawAnimation();
		int animationTriggerRevision = transitions.lobecorp$getAnimationTriggerRevision();
		boolean triggerChanged = controllerRuntime.animationTriggerRevision != animationTriggerRevision;
		boolean animationChanged = controllerRuntime.animation != animation || controllerRuntime.rawAnimation != rawAnimation
				|| triggerChanged;
		boolean controllerWeightTransitioning = controllerRuntime.weightTransition;

		if (animationChanged && controllerRuntime.weightTransition && controllerRuntime.weightTarget != 1) {
			controllerRuntime.controllerExiting = false;
			controllerRuntime.beginWeightTransition(1, renderAge, transitions.lobecorp$getFadeInTicks(), transitions);
		}
		if (!controllerRuntime.initialized) {
			controllerRuntime.initialized = true;
			controllerRuntime.weight = 0;
			controllerRuntime.controllerExiting = false;
			controllerRuntime.beginWeightTransition(1, renderAge, transitions.lobecorp$getFadeInTicks(), transitions);
			controllerRuntime.animation = animation;
			controllerRuntime.rawAnimation = rawAnimation;
			controllerRuntime.animationTriggerRevision = animationTriggerRevision;
			controllerRuntime.pose = targetPose;
			transitions.lobecorp$setAnimationTransitionPaused(false);
		} else if (controllerRuntime.animationWeightFadingOut
				&& transitions.lobecorp$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL) {
			controllerRuntime.animation = animation;
			controllerRuntime.rawAnimation = rawAnimation;
			controllerRuntime.animationTriggerRevision = animationTriggerRevision;
			controllerRuntime.pendingAnimation = animation;
			controllerRuntime.pendingRawAnimation = rawAnimation;
			controllerRuntime.pendingAnimationPose = targetPose;
		} else if (triggerChanged && !controllerWeightTransitioning) {
			beginAnimationTransition(controllerRuntime, animation, rawAnimation, animationTriggerRevision,
					targetPose, renderAge, transitions);
		} else if (controllerRuntime.animationWeightTransition && animationChanged
				&& !controllerWeightTransitioning) {
			beginAnimationTransition(controllerRuntime, animation, rawAnimation, animationTriggerRevision,
					targetPose, renderAge, transitions);
		} else if (animationChanged && controllerRuntime.weightTransition) {
			controllerRuntime.animation = animation;
			controllerRuntime.rawAnimation = rawAnimation;
			controllerRuntime.animationTriggerRevision = animationTriggerRevision;
			controllerRuntime.animationSource = controllerRuntime.lastPose != null
					? controllerRuntime.lastPose
					: controllerRuntime.pose;
			double weightProgress = transitionProgress(renderAge, controllerRuntime.weightTransitionStart,
					controllerRuntime.weightTransitionTicks);
			controllerRuntime.animationTransitionTicks = (int) Math.ceil(
					controllerRuntime.weightTransitionTicks * (1 - weightProgress));
			controllerRuntime.animationTransition = controllerRuntime.animationTransitionTicks > 0;
			controllerRuntime.animationTransitionStart = renderAge;
			controllerRuntime.pose = targetPose;
			transitions.lobecorp$setAnimationTransitionPaused(controllerRuntime.animationTransition
					&& transitions.lobecorp$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
		} else if (animationChanged) {
			controllerRuntime.animation = animation;
			controllerRuntime.rawAnimation = rawAnimation;
			controllerRuntime.animationTriggerRevision = animationTriggerRevision;
			controllerRuntime.animationSource = controllerRuntime.lastPose != null
					? controllerRuntime.lastPose
					: controllerRuntime.pose;
			controllerRuntime.animationTransition = controller.getTransitionTicks() > 0;
			controllerRuntime.animationTransitionStart = renderAge;
			controllerRuntime.animationTransitionTicks = controller.getTransitionTicks();
			controllerRuntime.pose = targetPose;
			transitions.lobecorp$setAnimationTransitionPaused(controllerRuntime.animationTransition
					&& transitions.lobecorp$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
		} else {
			controllerRuntime.pose = targetPose;
		}
	}

	private static void applyActivePose(BoneSnapshots snapshots, AnimationController<?> controller, LcAnimationControllerTransitions<?> transitions, ControllerRuntime controllerRuntime, double renderAge) {
		if (!controllerRuntime.controllerExiting && transitions.lobecorp$getFadeOutTransitionMode() == LcTransitionMode.OVERLAP
				&& shouldOverlapControllerExit(controller, transitions.lobecorp$getFadeOutTicks())) {
			controllerRuntime.controllerExiting = true;
			controllerRuntime.beginWeightTransition(0, renderAge, transitions.lobecorp$getFadeOutTicks(), transitions);
		}

		if (!controllerRuntime.weightTransition && controllerRuntime.weight < 1 && !controllerRuntime.controllerExiting) {
			controllerRuntime.beginWeightTransition(1, renderAge, transitions.lobecorp$getFadeInTicks(), transitions);
		}
		updateAnimationWeightTransition(controllerRuntime, renderAge, transitions);
		controllerRuntime.updateWeight(renderAge, transitions);
		double animationProgress = transitionProgress(renderAge, controllerRuntime.animationTransitionStart,
				controllerRuntime.animationTransitionTicks);
		Pose pose = controllerRuntime.animationTransition
				? LcAnimationPose.blendPoses(controllerRuntime.animationSource, controllerRuntime.pose, animationProgress,
				transitions.lobecorp$getRotationTransitionMode())
				: controllerRuntime.pose;
		if (controllerRuntime.animationTransition && animationProgress >= 1) {
			controllerRuntime.animationTransition = false;
			controllerRuntime.animationSource = null;
			transitions.lobecorp$setAnimationTransitionPaused(false);
		}
		controllerRuntime.lastPose = pose;
		controllerRuntime.additive = transitions.lobecorp$getBlendType() == LcControllerBlendType.ADDITIVE;
		LcRotationTransitionMode weightRotationTransitionMode = controllerRuntime.weightTransition
				? controllerRuntime.weightTarget == 1
				? transitions.lobecorp$getFadeInRotationTransitionMode()
				: transitions.lobecorp$getFadeOutRotationTransitionMode()
				: controllerRuntime.animationWeightTransition
				? controllerRuntime.animationWeightTarget == 1
				? transitions.lobecorp$getFadeInRotationTransitionMode()
				: transitions.lobecorp$getFadeOutRotationTransitionMode()
				: transitions.lobecorp$getRotationTransitionMode();
		LcAnimationPose.applyPose(pose, controllerRuntime.weight * controllerRuntime.animationWeight,
				controllerRuntime.additive, weightRotationTransitionMode, controller, snapshots);
	}

	private static void beginAnimationTransition(ControllerRuntime runtime, Animation animation, RawAnimation rawAnimation,
	                                             int animationTriggerRevision, Pose targetPose, double renderAge,
	                                             LcAnimationControllerTransitions<?> transitions) {
		if (transitions.lobecorp$getAnimationTransitionMode() == LcTransitionMode.OVERLAP) {
			runtime.animationSource = runtime.lastPose != null ? runtime.lastPose : runtime.pose;
			runtime.animation = animation;
			runtime.rawAnimation = rawAnimation;
			runtime.animationTriggerRevision = animationTriggerRevision;
			runtime.pose = targetPose;
			runtime.animationTransitionStart = renderAge;
			runtime.animationTransitionTicks = transitions.lobecorp$getFadeInTicks();
			runtime.animationTransition = runtime.animationTransitionTicks > 0;
			runtime.animationWeightFadingOut = false;
			runtime.animationWeightTransition = false;
			runtime.animationWeight = 1;
			runtime.pendingAnimation = null;
			runtime.pendingRawAnimation = null;
			runtime.pendingAnimationPose = null;
			transitions.lobecorp$setAnimationTransitionPaused(false);
			return;
		}
		runtime.pose = runtime.lastPose != null ? runtime.lastPose : runtime.pose;
		runtime.animationTransition = false;
		runtime.animationSource = null;
		runtime.animation = animation;
		runtime.rawAnimation = rawAnimation;
		runtime.animationTriggerRevision = animationTriggerRevision;
		runtime.pendingAnimation = animation;
		runtime.pendingRawAnimation = rawAnimation;
		runtime.pendingAnimationPose = targetPose;
		runtime.animationWeightFadingOut = true;
		runtime.beginAnimationWeightTransition(0, renderAge, transitions.lobecorp$getFadeOutTicks());
		transitions.lobecorp$setAnimationTransitionPaused(
				transitions.lobecorp$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
	}

	private static void updateAnimationWeightTransition(ControllerRuntime runtime, double renderAge,
	                                                    LcAnimationControllerTransitions<?> transitions) {
		if (runtime.animationWeightTransition) {
			double progress = transitionProgress(renderAge, runtime.animationWeightTransitionStart,
					runtime.animationWeightTransitionTicks);
			runtime.animationWeight = runtime.animationWeightFrom
					+ (runtime.animationWeightTarget - runtime.animationWeightFrom) * progress;
			if (progress >= 1) {
				runtime.animationWeightTransition = false;
			}
		}
		if (!runtime.animationWeightTransition && runtime.animationWeightFadingOut) {
			runtime.animationWeightFadingOut = false;
			runtime.animation = runtime.pendingAnimation;
			runtime.rawAnimation = runtime.pendingRawAnimation;
			runtime.pose = runtime.pendingAnimationPose;
			runtime.animationWeight = 0;
			runtime.beginAnimationWeightTransition(1, renderAge, transitions.lobecorp$getFadeInTicks());
			transitions.lobecorp$setAnimationTransitionPaused(
					transitions.lobecorp$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
		}
		if (!runtime.animationWeightTransition && runtime.pendingAnimationPose != null) {
			runtime.pendingAnimation = null;
			runtime.pendingRawAnimation = null;
			runtime.pendingAnimationPose = null;
			transitions.lobecorp$setAnimationTransitionPaused(false);
		}
	}

	private static boolean shouldOverlapControllerExit(AnimationController<?> controller, int fadeOutTicks) {
		if (fadeOutTicks <= 0 || controller.getTimeline() == null) {
			return false;
		}
		AnimationTimeline.Stage[] stages = controller.getTimeline().stages();
		if (stages.length == 0) {
			return false;
		}
		AnimationTimeline.Stage lastStage = stages[stages.length - 1];
		LoopType loopType = lastStage.loopType();
		if (loopType == LoopType.DEFAULT && lastStage.animation() != null) {
			loopType = lastStage.animation().loopType();
		}
		if (loopType != LoopType.PLAY_ONCE) {
			return false;
		}
		double remainingTime = controller.getTimeline().lastAnimationEndTime() - controller.getCurrentTimelineTime();
		return remainingTime >= 0 && remainingTime <= fadeOutTicks / 20d;
	}

}
