package org.unitego.lobecorp.animation;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.animation.BoneAnimation;
import com.geckolib.cache.animation.KeyframeStack;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class LcAnimationPose {
	protected static Pose createPose(RenderPassInfo<?> renderPassInfo, ControllerState sourceState, AnimationController<?> controller,
	                       LcControllerBlendType blendType) {
		AnimationPoint animationPoint = sourceState.animationPoint();
		ControllerState sampleState = new ControllerState(animationPoint, null, -1, 0, false,
				sourceState.easingOverride(), sourceState.renderState(), sourceState.queryValues());
		Map<String, BoneSnapshot> bones = new HashMap<>();
		Set<Channel> channels = new LinkedHashSet<>();
		BoneAnimation[] boneAnimations = animationPoint.animation().boneAnimations();

		for (BoneAnimation boneAnimation : boneAnimations) {
			String boneName = boneAnimation.boneName();
			if (!LcAnimationControllerMask.of(controller).lobecorp$isBoneInfluenced(boneName)) {
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

	private static boolean hasKeyframes(KeyframeStack keyframes) {
		return keyframes.xKeyframes().length > 0 || keyframes.yKeyframes().length > 0 || keyframes.zKeyframes().length > 0;
	}

	protected static Pose blendPoses(Pose from, Pose to, double weight, LcRotationTransitionMode rotationTransitionMode) {
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
					.slerp(rotationQuaternion(toX, toY, toZ), (float) weight);
			setRotation(result, rotation);
			return;
		}
		setValue(result, transform, lerp(fromX, toX, weight), lerp(fromY, toY, weight), lerp(fromZ, toZ, weight));
	}

	protected static void applyPose(Pose pose, double weight, boolean additive, LcRotationTransitionMode rotationTransitionMode,
	                      AnimationController<?> controller, BoneSnapshots snapshots) {
		if (pose == null || weight <= 0) {
			return;
		}
		for (Channel channel : pose.channels) {
			if (!LcAnimationControllerMask.of(controller).lobecorp$isBoneInfluenced(channel.boneName())) {
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
				Quaternionf weightedRotation = new Quaternionf().slerp(additiveRotation, (float) weight);
				targetRotation.mul(weightedRotation);
				setRotation(target, targetRotation);
				return;
			}
			layerX = defaultValue + (layerX - defaultValue) * (float) weight;
			layerY = defaultValue + (layerY - defaultValue) * (float) weight;
			layerZ = defaultValue + (layerZ - defaultValue) * (float) weight;
			if (transform == Transform.SCALE) {
				setValue(target, transform, targetX * layerX, targetY * layerY, targetZ * layerZ);
			} else {
				setValue(target, transform, targetX + layerX - defaultValue, targetY + layerY - defaultValue, targetZ + layerZ - defaultValue);
			}
		} else {
			if (transform == Transform.ROTATION && rotationTransitionMode == LcRotationTransitionMode.SHORTEST_PATH) {
				Quaternionf rotation = rotationQuaternion(targetX, targetY, targetZ)
						.slerp(rotationQuaternion(layerX, layerY, layerZ), (float) weight);
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
		return (float) (from + (to - from) * weight);
	}

	private enum Transform {
		SCALE,
		ROTATION,
		TRANSLATION
	}

	private record Channel(String boneName, Transform transform) {
	}

	protected record Pose(Map<String, BoneSnapshot> bones, Set<Channel> channels) {
	}
}
