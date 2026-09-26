package org.unitego.lobecorp.animation;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.AnimationTimeline;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.animation.object.LoopType;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BoneAnimation;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.Lobecorp;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LcAnimationControllerIntegration {
	private static final String DEBUG_ROOT_BONE = "root";
	private static final String DEBUG_BONE = "up_body";
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
		Class<?> animatableClass = renderPassInfo.renderState().getGeckolibData(DataTickets.ANIMATABLE_CLASS);
		boolean hasDebugRootBone = renderPassInfo.model().getBone(DEBUG_ROOT_BONE).isPresent();
		boolean hasDebugBone = renderPassInfo.model().getBone(DEBUG_BONE).isPresent();
		if (runtimeData.hasDebugRootBone == null || runtimeData.hasDebugRootBone != hasDebugRootBone
				|| runtimeData.hasDebugBone == null || runtimeData.hasDebugBone != hasDebugBone) {
			Lobecorp.LOGGER.info("[AnimationDebug] animatable={} model={} rootBonePresent={} upperBodyBonePresent={} controllers={}",
					animatableClass, renderPassInfo.model().getClass().getName(), hasDebugRootBone, hasDebugBone,
					manager.getAnimationControllers().keySet());
			runtimeData.hasDebugRootBone = hasDebugRootBone;
			runtimeData.hasDebugBone = hasDebugBone;
		}
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
				Pose targetPose = createPose(renderPassInfo, state, controller, transitions.lc$getBlendType());
				Animation animation = state.animationPoint().animation();
				RawAnimation rawAnimation = controller.getCurrentRawAnimation();
				int animationTriggerRevision = transitions.lc$getAnimationTriggerRevision();
				boolean triggerChanged = controllerRuntime.animationTriggerRevision != animationTriggerRevision;
				boolean animationChanged = controllerRuntime.animation != animation || controllerRuntime.rawAnimation != rawAnimation
						|| triggerChanged;
				boolean controllerWeightTransitioning = controllerRuntime.weightTransition;

				if (animationChanged && controllerRuntime.weightTransition && controllerRuntime.weightTarget != 1) {
					controllerRuntime.controllerExiting = false;
					controllerRuntime.beginWeightTransition(1, renderAge, transitions.lc$getFadeInTicks(), transitions);
				}
				if (!controllerRuntime.initialized) {
					controllerRuntime.initialized = true;
					controllerRuntime.weight = 0;
					controllerRuntime.controllerExiting = false;
					controllerRuntime.beginWeightTransition(1, renderAge, transitions.lc$getFadeInTicks(), transitions);
					controllerRuntime.animation = animation;
					controllerRuntime.rawAnimation = rawAnimation;
					controllerRuntime.animationTriggerRevision = animationTriggerRevision;
					controllerRuntime.pose = targetPose;
					transitions.lc$setAnimationTransitionPaused(false);
				} else if (controllerRuntime.animationWeightFadingOut) {
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
					controllerRuntime.animationTransitionTicks = (int)Math.ceil(
							controllerRuntime.weightTransitionTicks * (1 - weightProgress));
					controllerRuntime.animationTransition = controllerRuntime.animationTransitionTicks > 0;
					controllerRuntime.animationTransitionStart = renderAge;
					controllerRuntime.pose = targetPose;
					transitions.lc$setAnimationTransitionPaused(controllerRuntime.animationTransition
							&& transitions.lc$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
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
					transitions.lc$setAnimationTransitionPaused(controllerRuntime.animationTransition
							&& transitions.lc$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
				} else {
					controllerRuntime.pose = targetPose;
				}
				if (!controllerRuntime.controllerExiting && transitions.lc$getFadeOutTransitionMode() == LcTransitionMode.OVERLAP
						&& shouldOverlapControllerExit(controller, transitions.lc$getFadeOutTicks())) {
					controllerRuntime.controllerExiting = true;
					controllerRuntime.beginWeightTransition(0, renderAge, transitions.lc$getFadeOutTicks(), transitions);
				}

				if (!controllerRuntime.weightTransition && controllerRuntime.weight < 1 && !controllerRuntime.controllerExiting) {
					controllerRuntime.beginWeightTransition(1, renderAge, transitions.lc$getFadeInTicks(), transitions);
				}
				boolean animationWeightTransitionWasActive = controllerRuntime.animationWeightTransition;
				updateAnimationWeightTransition(controllerRuntime, renderAge, transitions);
				boolean transitionWasActive = controllerRuntime.weightTransition || controllerRuntime.animationTransition
						|| animationWeightTransitionWasActive || controllerRuntime.animationWeightTransition;
				controllerRuntime.updateWeight(renderAge, transitions);
				double animationProgress = transitionProgress(renderAge, controllerRuntime.animationTransitionStart,
						controllerRuntime.animationTransitionTicks);
				Pose pose = controllerRuntime.animationTransition
						? blendPoses(controllerRuntime.animationSource, controllerRuntime.pose, animationProgress,
								transitions.lc$getRotationTransitionMode())
						: controllerRuntime.pose;
				if (controllerRuntime.animationTransition && animationProgress >= 1) {
					controllerRuntime.animationTransition = false;
					controllerRuntime.animationSource = null;
					transitions.lc$setAnimationTransitionPaused(false);
				}
				controllerRuntime.lastPose = pose;
				controllerRuntime.additive = transitions.lc$getBlendType() == LcControllerBlendType.ADDITIVE;
				LcRotationTransitionMode weightRotationTransitionMode = controllerRuntime.weightTransition
						? controllerRuntime.weightTarget == 1
								? transitions.lc$getFadeInRotationTransitionMode()
								: transitions.lc$getFadeOutRotationTransitionMode()
						: controllerRuntime.animationWeightTransition
							? controllerRuntime.animationWeightTarget == 1
									? transitions.lc$getFadeInRotationTransitionMode()
									: transitions.lc$getFadeOutRotationTransitionMode()
						: transitions.lc$getRotationTransitionMode();
				String debugBonesBefore = describeDebugBones(snapshots);
				applyPose(pose, controllerRuntime.weight * controllerRuntime.animationWeight,
						controllerRuntime.additive, weightRotationTransitionMode, controller, snapshots);
				if (((hasDebugRootBone || hasDebugBone) && (animationChanged || transitionWasActive || controllerRuntime.weightTransition
						|| controllerRuntime.animationTransition)) || hasInvalidScale(snapshots)) {
					logControllerDebug(animatableClass, renderAge, controller, controllerRuntime, transitions, true,
							animationChanged, controllerRuntime.animationSource, targetPose, pose, debugBonesBefore,
							describeDebugBones(snapshots));
				}
			} else if (controllerRuntime.initialized && controllerRuntime.weight > 0) {
				if (!controllerRuntime.weightTransition || controllerRuntime.weightTarget != 0) {
					controllerRuntime.controllerExiting = true;
					controllerRuntime.beginWeightTransition(0, renderAge, transitions.lc$getFadeOutTicks(), transitions);
				}
				boolean transitionWasActive = controllerRuntime.weightTransition || controllerRuntime.animationTransition;
				controllerRuntime.updateWeight(renderAge, transitions);
				if (controllerRuntime.lastPose != null) {
					String debugBonesBefore = describeDebugBones(snapshots);
				applyPose(controllerRuntime.lastPose, controllerRuntime.weight * controllerRuntime.animationWeight,
						controllerRuntime.additive,
						transitions.lc$getFadeOutRotationTransitionMode(), controller, snapshots);
					if (((hasDebugRootBone || hasDebugBone) && (transitionWasActive || controllerRuntime.weightTransition))
							|| hasInvalidScale(snapshots)) {
						logControllerDebug(animatableClass, renderAge, controller, controllerRuntime, transitions, false,
								false, controllerRuntime.animationSource, controllerRuntime.pose, controllerRuntime.lastPose,
								debugBonesBefore, describeDebugBones(snapshots));
					}
				}
			} else if (controllerRuntime.initialized && controllerRuntime.weight == 0) {
				controllerRuntime.reset(transitions);
			}
		}
	}

	private static void beginAnimationTransition(ControllerRuntime runtime, Animation animation, RawAnimation rawAnimation,
			int animationTriggerRevision, Pose targetPose, double renderAge,
			LcAnimationControllerTransitions<?> transitions) {
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
		runtime.beginAnimationWeightTransition(0, renderAge, transitions.lc$getFadeOutTicks());
		transitions.lc$setAnimationTransitionPaused(
				transitions.lc$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
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
			runtime.beginAnimationWeightTransition(1, renderAge, transitions.lc$getFadeInTicks());
			transitions.lc$setAnimationTransitionPaused(
					transitions.lc$getAnimationTransitionMode() == LcTransitionMode.SEQUENTIAL);
		}
		if (!runtime.animationWeightTransition && runtime.pendingAnimationPose != null) {
			runtime.pendingAnimation = null;
			runtime.pendingRawAnimation = null;
			runtime.pendingAnimationPose = null;
			transitions.lc$setAnimationTransitionPaused(false);
		}
	}

	private static void logControllerDebug(Class<?> animatableClass, double renderAge, AnimationController<?> controller,
			ControllerRuntime runtime, LcAnimationControllerTransitions<?> transitions, boolean active,
			boolean animationChanged, Pose sourcePose, Pose targetPose, Pose blendedPose,
			String upBodyBefore, String upBodyAfter) {
		double weightProgress = transitionProgress(renderAge, runtime.weightTransitionStart, runtime.weightTransitionTicks);
		double animationProgress = transitionProgress(renderAge, runtime.animationTransitionStart, runtime.animationTransitionTicks);
		Lobecorp.LOGGER.info("[AnimationDebug] animatable={} age={} controller={} active={} changed={} exiting={} blend={} additive={} "
				+ "weight={}/{} weightFadeActive={} from={} ticks={} progress={} mode={} "
				+ "animationFadeActive={} start={} ticks={} progress={} mode={} "
				+ "rotationFadeIn={} rotationFadeOut={} animationWeight={} animationWeightTarget={} animationWeightFadeActive={} "
				+ "animationWeightFadingOut={} animationWeightFrom={} animationWeightTicks={} animationWeightProgress={} "
				+ "source={} target={} blended={} bonesBefore={} bonesAfter={}",
				animatableClass, renderAge, controller.getName(), active, animationChanged, runtime.controllerExiting,
				transitions.lc$getBlendType(), runtime.additive, runtime.weight, runtime.weightTarget,
				runtime.weightTransition, runtime.weightFrom, runtime.weightTransitionTicks, weightProgress,
				runtime.weightTarget == 1 ? transitions.lc$getFadeInTransitionMode() : transitions.lc$getFadeOutTransitionMode(),
				runtime.animationTransition, runtime.animationTransitionStart, runtime.animationTransitionTicks,
				animationProgress, transitions.lc$getAnimationTransitionMode(), transitions.lc$getFadeInRotationTransitionMode(),
				transitions.lc$getFadeOutRotationTransitionMode(), runtime.animationWeight, runtime.animationWeightTarget,
				runtime.animationWeightTransition, runtime.animationWeightFadingOut, runtime.animationWeightFrom,
				runtime.animationWeightTransitionTicks,
				transitionProgress(renderAge, runtime.animationWeightTransitionStart, runtime.animationWeightTransitionTicks),
				describePoseBones(sourcePose), describePoseBones(targetPose),
				describePoseBones(blendedPose), upBodyBefore, upBodyAfter);
	}

	private static String describePoseBones(Pose pose) {
		return pose == null ? "none" : "root=" + describeSnapshot(pose.bones.get(DEBUG_ROOT_BONE))
				+ ",up_body=" + describeSnapshot(pose.bones.get(DEBUG_BONE));
	}

	private static String describeDebugBones(BoneSnapshots snapshots) {
		return "root=" + describeSnapshot(snapshots.get(DEBUG_ROOT_BONE).orElse(null))
				+ ",up_body=" + describeSnapshot(snapshots.get(DEBUG_BONE).orElse(null));
	}

	private static String describeSnapshot(BoneSnapshot snapshot) {
		if (snapshot == null) {
			return "missing";
		}
		return String.format(Locale.ROOT, "scale=(%.5f,%.5f,%.5f),rotation=(%.5f,%.5f,%.5f),translation=(%.5f,%.5f,%.5f)",
				snapshot.getScaleX(), snapshot.getScaleY(), snapshot.getScaleZ(),
				snapshot.getRotX(), snapshot.getRotY(), snapshot.getRotZ(),
				snapshot.getTranslateX(), snapshot.getTranslateY(), snapshot.getTranslateZ());
	}

	private static boolean hasInvalidScale(BoneSnapshots snapshots) {
		return hasInvalidScale(snapshots.get(DEBUG_ROOT_BONE).orElse(null))
				|| hasInvalidScale(snapshots.get(DEBUG_BONE).orElse(null));
	}

	private static boolean hasInvalidScale(BoneSnapshot snapshot) {
		return snapshot != null && (!Float.isFinite(snapshot.getScaleX()) || !Float.isFinite(snapshot.getScaleY())
				|| !Float.isFinite(snapshot.getScaleZ()) || snapshot.getScaleX() <= 0 || snapshot.getScaleY() <= 0
				|| snapshot.getScaleZ() <= 0);
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

	private static Pose createPose(RenderPassInfo<?> renderPassInfo, ControllerState sourceState, AnimationController<?> controller,
			LcControllerBlendType blendType) {
		AnimationPoint animationPoint = sourceState.animationPoint();
		ControllerState sampleState = new ControllerState(animationPoint, null, -1, 0, false,
				sourceState.easingOverride(), sourceState.renderState(), sourceState.queryValues());
		Map<String, BoneSnapshot> bones = new HashMap<>();
		Set<Channel> channels = new LinkedHashSet<>();
		BoneAnimation[] boneAnimations = animationPoint.animation().boneAnimations();

		for (BoneAnimation boneAnimation : boneAnimations) {
			String boneName = boneAnimation.boneName();
			if (!LcAnimationControllerMask.of(controller).lc$isBoneInfluenced(boneName)) {
				continue;
			}
		boolean hasScale = hasKeyframes(boneAnimation.scaleKeyFrames());
			boolean hasRotation = hasKeyframes(boneAnimation.rotationKeyFrames());
			boolean hasTranslation = hasKeyframes(boneAnimation.positionKeyFrames());
			if (!hasScale && !hasRotation && !hasTranslation) {
				continue;
			}
			GeoBone bone = renderPassInfo.model().getBone(boneName).orElse(null);
			if (bone == null) {
				continue;
			}
			bones.put(boneName, BoneSnapshot.create(bone));
			if (hasScale) {
				channels.add(new Channel(boneName, Transform.SCALE));
			}
			if (hasRotation) {
				channels.add(new Channel(boneName, Transform.ROTATION));
			}
			if (hasTranslation) {
				channels.add(new Channel(boneName, Transform.TRANSLATION));
			}
		}

		BoneSnapshots isolatedSnapshots = boneName -> Optional.ofNullable(bones.get(boneName));
		AnimationProcessor.createBoneSnapshots(sampleState, isolatedSnapshots);
		if (blendType == LcControllerBlendType.OVERRIDE) {
			for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
				addOverrideChannels(bone, bones, channels);
			}
		}
		return new Pose(bones, channels);
	}

	private static void addOverrideChannels(GeoBone bone, Map<String, BoneSnapshot> bones, Set<Channel> channels) {
		bones.computeIfAbsent(bone.name(), ignored -> BoneSnapshot.create(bone));
		channels.add(new Channel(bone.name(), Transform.SCALE));
		channels.add(new Channel(bone.name(), Transform.ROTATION));
		channels.add(new Channel(bone.name(), Transform.TRANSLATION));
		for (GeoBone child : bone.children()) {
			addOverrideChannels(child, bones, channels);
		}
	}

	private static boolean hasKeyframes(com.geckolib.cache.animation.KeyframeStack keyframes) {
		return keyframes.xKeyframes().length > 0 || keyframes.yKeyframes().length > 0 || keyframes.zKeyframes().length > 0;
	}

	private static Pose blendPoses(Pose from, Pose to, double weight, LcRotationTransitionMode rotationTransitionMode) {
		if (from == null) {
			return to;
		}
		Set<Channel> channels = new LinkedHashSet<>(from.channels);
		channels.addAll(to.channels);
		Map<String, BoneSnapshot> bones = new HashMap<>();
		for (Channel channel : channels) {
			BoneSnapshot fromBone = from.bones.get(channel.boneName());
			BoneSnapshot toBone = to.bones.get(channel.boneName());
			GeoBone bone = toBone != null ? toBone.getBone() : fromBone.getBone();
			BoneSnapshot result = bones.computeIfAbsent(channel.boneName(), ignored -> BoneSnapshot.create(bone));
			blendTransform(result, fromBone, from.channels.contains(channel), toBone, to.channels.contains(channel),
					channel.transform(), weight, rotationTransitionMode);
		}
		return new Pose(bones, channels);
	}

	private static void blendTransform(BoneSnapshot result, BoneSnapshot from, boolean fromHasChannel,
			BoneSnapshot to, boolean toHasChannel, Transform transform, double weight,
			LcRotationTransitionMode rotationTransitionMode) {
		float fromX = fromHasChannel ? getValue(from, transform, 0) : getDefault(transform);
		float fromY = fromHasChannel ? getValue(from, transform, 1) : getDefault(transform);
		float fromZ = fromHasChannel ? getValue(from, transform, 2) : getDefault(transform);
		float toX = toHasChannel ? getValue(to, transform, 0) : getDefault(transform);
		float toY = toHasChannel ? getValue(to, transform, 1) : getDefault(transform);
		float toZ = toHasChannel ? getValue(to, transform, 2) : getDefault(transform);
		if (transform == Transform.ROTATION && rotationTransitionMode == LcRotationTransitionMode.SHORTEST_PATH) {
			Quaternionf rotation = rotationQuaternion(fromX, fromY, fromZ)
					.slerp(rotationQuaternion(toX, toY, toZ), (float)weight);
			setRotation(result, rotation);
			return;
		}
		setValue(result, transform, lerp(fromX, toX, weight), lerp(fromY, toY, weight), lerp(fromZ, toZ, weight));
	}

	private static void applyPose(Pose pose, double weight, boolean additive, LcRotationTransitionMode rotationTransitionMode,
			AnimationController<?> controller, BoneSnapshots snapshots) {
		if (pose == null || weight <= 0) {
			return;
		}
		for (Channel channel : pose.channels) {
			if (!LcAnimationControllerMask.of(controller).lc$isBoneInfluenced(channel.boneName())) {
				continue;
			}
			BoneSnapshot layer = pose.bones.get(channel.boneName());
			snapshots.get(channel.boneName()).ifPresent(target ->
				mixTransform(target, layer, channel.transform(), weight, additive, rotationTransitionMode)
			);
		}
	}

	private static void mixTransform(BoneSnapshot target, BoneSnapshot layer, Transform transform, double weight, boolean additive,
			LcRotationTransitionMode rotationTransitionMode) {
		float targetX = getValue(target, transform, 0);
		float targetY = getValue(target, transform, 1);
		float targetZ = getValue(target, transform, 2);
		float layerX = getValue(layer, transform, 0);
		float layerY = getValue(layer, transform, 1);
		float layerZ = getValue(layer, transform, 2);

		if (additive) {
			float defaultValue = getDefault(transform);
			if (transform == Transform.ROTATION && rotationTransitionMode == LcRotationTransitionMode.SHORTEST_PATH) {
				Quaternionf targetRotation = rotationQuaternion(targetX, targetY, targetZ);
				Quaternionf additiveRotation = rotationQuaternion(layerX - defaultValue, layerY - defaultValue, layerZ - defaultValue);
				Quaternionf weightedRotation = new Quaternionf().slerp(additiveRotation, (float)weight);
				targetRotation.mul(weightedRotation);
				setRotation(target, targetRotation);
				return;
			}
			layerX = defaultValue + (layerX - defaultValue) * (float)weight;
			layerY = defaultValue + (layerY - defaultValue) * (float)weight;
			layerZ = defaultValue + (layerZ - defaultValue) * (float)weight;
			if (transform == Transform.SCALE) {
				setValue(target, transform, targetX * layerX, targetY * layerY, targetZ * layerZ);
			} else {
				setValue(target, transform, targetX + layerX - defaultValue, targetY + layerY - defaultValue, targetZ + layerZ - defaultValue);
			}
		} else {
			if (transform == Transform.ROTATION && rotationTransitionMode == LcRotationTransitionMode.SHORTEST_PATH) {
				Quaternionf rotation = rotationQuaternion(targetX, targetY, targetZ)
						.slerp(rotationQuaternion(layerX, layerY, layerZ), (float)weight);
				setRotation(target, rotation);
				return;
			}
			setValue(target, transform, lerp(targetX, layerX, weight), lerp(targetY, layerY, weight), lerp(targetZ, layerZ, weight));
		}
	}

	private static Quaternionf rotationQuaternion(float x, float y, float z) {
		return new Quaternionf().rotationZYX(z, y, x);
	}

	private static void setRotation(BoneSnapshot snapshot, Quaternionf rotation) {
		Vector3f eulerAngles = rotation.getEulerAnglesZYX(new Vector3f());
		snapshot.setRotation(eulerAngles.x, eulerAngles.y, eulerAngles.z);
	}

	private static float getValue(BoneSnapshot snapshot, Transform transform, int axis) {
		return switch (transform) {
			case SCALE -> switch (axis) {
				case 0 -> snapshot.getScaleX();
				case 1 -> snapshot.getScaleY();
				default -> snapshot.getScaleZ();
			};
			case ROTATION -> switch (axis) {
				case 0 -> snapshot.getRotX();
				case 1 -> snapshot.getRotY();
				default -> snapshot.getRotZ();
			};
			case TRANSLATION -> switch (axis) {
				case 0 -> snapshot.getTranslateX();
				case 1 -> snapshot.getTranslateY();
				default -> snapshot.getTranslateZ();
			};
		};
	}

	private static void setValue(BoneSnapshot snapshot, Transform transform, float x, float y, float z) {
		switch (transform) {
			case SCALE -> snapshot.setScale(x, y, z);
			case ROTATION -> snapshot.setRotation(x, y, z);
			case TRANSLATION -> snapshot.setTranslation(x, y, z);
		}
	}

	private static float getDefault(Transform transform) {
		return transform == Transform.SCALE ? 1 : 0;
	}

	private static float lerp(float from, float to, double weight) {
		return (float)(from + (to - from) * weight);
	}

	private static double transitionProgress(double currentAge, double startAge, int durationTicks) {
		if (durationTicks <= 0) {
			return 1;
		}
		return Math.clamp((currentAge - startAge) / durationTicks, 0, 1);
	}

	private enum Transform {
		SCALE,
		ROTATION,
		TRANSLATION
	}

	private record Channel(String boneName, Transform transform) {}

	private record Pose(Map<String, BoneSnapshot> bones, Set<Channel> channels) {}

	private static final class RuntimeData {
		private final Map<AnimationController<?>, ControllerRuntime> controllers = new IdentityHashMap<>();
		private Boolean hasDebugRootBone;
		private Boolean hasDebugBone;
	}

	private static final class ControllerRuntime {
		private boolean initialized;
		private boolean weightTransition;
		private double weight;
		private double weightFrom;
		private double weightTarget;
		private double weightTransitionStart;
		private int weightTransitionTicks;
		private Animation animation;
		private RawAnimation rawAnimation;
		private Pose pose;
		private Pose lastPose;
		private Pose animationSource;
		private boolean animationTransition;
		private double animationTransitionStart;
		private int animationTransitionTicks;
		private boolean additive;
		private boolean controllerExiting;
		private int animationTriggerRevision;
		private double animationWeight = 1;
		private double animationWeightFrom = 1;
		private double animationWeightTarget = 1;
		private double animationWeightTransitionStart;
		private int animationWeightTransitionTicks;
		private boolean animationWeightTransition;
		private boolean animationWeightFadingOut;
		private Animation pendingAnimation;
		private RawAnimation pendingRawAnimation;
		private Pose pendingAnimationPose;

		private void beginAnimationWeightTransition(double target, double startAge, int durationTicks) {
			this.animationWeightFrom = this.animationWeight;
			this.animationWeightTarget = target;
			this.animationWeightTransitionStart = startAge;
			this.animationWeightTransitionTicks = durationTicks;
			this.animationWeightTransition = durationTicks > 0 && this.animationWeightFrom != target;
			if (!this.animationWeightTransition) {
				this.animationWeight = target;
			}
		}

		private void beginWeightTransition(double target, double startAge, int durationTicks, LcAnimationControllerTransitions<?> transitions) {
			this.weightFrom = this.weight;
			this.weightTarget = target;
			this.weightTransitionStart = startAge;
			this.weightTransitionTicks = durationTicks;
			this.weightTransition = durationTicks > 0 && this.weightFrom != target;
			this.animationTransition = false;
			this.animationSource = null;
			transitions.lc$setAnimationTransitionPaused(false);
			transitions.lc$setControllerTransitionPaused(target == 1 && this.weightTransition
					&& transitions.lc$getFadeInTransitionMode() == LcTransitionMode.SEQUENTIAL);
			if (!this.weightTransition) {
				this.weight = target;
			}
		}

		private void updateWeight(double renderAge, LcAnimationControllerTransitions<?> transitions) {
			if (!this.weightTransition) {
				return;
			}
			double progress = transitionProgress(renderAge, this.weightTransitionStart, this.weightTransitionTicks);
			this.weight = this.weightFrom + (this.weightTarget - this.weightFrom) * progress;
			if (progress >= 1) {
				this.weightTransition = false;
				if (this.weightTarget == 1) {
					transitions.lc$setControllerTransitionPaused(false);
				}
			}
		}

		private void reset(LcAnimationControllerTransitions<?> transitions) {
			this.initialized = false;
			this.weightTransition = false;
			this.weight = 0;
			this.animation = null;
			this.rawAnimation = null;
			this.pose = null;
			this.lastPose = null;
			this.animationSource = null;
			this.animationTransition = false;
			this.animationTriggerRevision = 0;
			this.animationWeight = 1;
			this.animationWeightFrom = 1;
			this.animationWeightTarget = 1;
			this.animationWeightTransition = false;
			this.animationWeightFadingOut = false;
			this.pendingAnimation = null;
			this.pendingRawAnimation = null;
			this.pendingAnimationPose = null;
			this.controllerExiting = false;
			transitions.lc$setAnimationTransitionPaused(false);
			transitions.lc$setControllerTransitionPaused(
					transitions.lc$getFadeInTransitionMode() == LcTransitionMode.SEQUENTIAL);
		}
	}
}
