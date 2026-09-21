package org.unitego.lobecorp.animation;

import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record LcAnimationFrame(List<Layer> layers) {
	public LcAnimationFrame {
		layers = List.copyOf(layers);
	}

	public record Layer(LcLayerDefinition definition, @Nullable ControllerState controllerState,
			PoseState poseState, float transitionProgress, float weight) {
	}

	public record BonePose(BoneSnapshot snapshot, float weight) {
	}

	public static final class PoseState {
		private Map<String, BonePose> previousPose = Map.of();
		private Map<String, BonePose> displayedPose = Map.of();

		public void beginTransition() {
			previousPose = displayedPose;
		}

		public Map<String, BonePose> resolve(@Nullable ControllerState controllerState,
				BakedGeoModel model, float transitionProgress) {
			if (controllerState == null) {
				return displayedPose;
			}
			Map<String, BonePose> currentPose = createPose(controllerState, model);
			if (previousPose.isEmpty() || transitionProgress >= 1.0F) {
				displayedPose = currentPose;
				previousPose = Map.of();
				return displayedPose;
			}
			displayedPose = blend(previousPose, currentPose, transitionProgress, model);
			return displayedPose;
		}

		public void clear() {
			previousPose = Map.of();
			displayedPose = Map.of();
		}

		private static Map<String, BonePose> createPose(ControllerState controllerState, BakedGeoModel model) {
			Map<String, BonePose> pose = new LinkedHashMap<>();
			AnimationProcessor.createBoneSnapshots(controllerState, boneName -> model.getBone(boneName)
					.map(bone -> BoneSnapshot.create(bone))
					.map(snapshot -> {
						pose.put(boneName, new BonePose(snapshot, 1.0F));
						return snapshot;
					}));
			return Map.copyOf(pose);
		}

		private static Map<String, BonePose> blend(Map<String, BonePose> previous,
				Map<String, BonePose> current, float progress, BakedGeoModel model) {
			Map<String, BonePose> result = new LinkedHashMap<>();
			for (String boneName : new LinkedHashSet<>(previous.keySet())) {
				blendBone(result, boneName, previous.get(boneName), current.get(boneName), progress, model);
			}
			for (String boneName : current.keySet()) {
				if (!previous.containsKey(boneName)) {
					blendBone(result, boneName, null, current.get(boneName), progress, model);
				}
			}
			return Map.copyOf(result);
		}

		private static void blendBone(Map<String, BonePose> result, String boneName,
				@Nullable BonePose previous, @Nullable BonePose current, float progress, BakedGeoModel model) {
			if (previous == null) {
				BonePose currentPose = java.util.Objects.requireNonNull(current);
				result.put(boneName, new BonePose(currentPose.snapshot(), currentPose.weight() * progress));
				return;
			}
			if (current == null) {
				result.put(boneName, new BonePose(previous.snapshot(), previous.weight() * (1.0F - progress)));
				return;
			}
			GeoBone bone = model.getBone(boneName).orElse(null);
			if (bone == null) {
				return;
			}
			BoneSnapshot snapshot = BoneSnapshot.create(bone);
			interpolate(snapshot, previous.snapshot(), current.snapshot(), progress);
			result.put(boneName, new BonePose(snapshot,
					Mth.lerp(progress, previous.weight(), current.weight())));
		}

		private static void interpolate(BoneSnapshot result, BoneSnapshot previous,
				BoneSnapshot current, float progress) {
			result.setTranslation(
					Mth.lerp(progress, previous.getTranslateX(), current.getTranslateX()),
					Mth.lerp(progress, previous.getTranslateY(), current.getTranslateY()),
					Mth.lerp(progress, previous.getTranslateZ(), current.getTranslateZ()));
			result.setScale(
					Mth.lerp(progress, previous.getScaleX(), current.getScaleX()),
					Mth.lerp(progress, previous.getScaleY(), current.getScaleY()),
					Mth.lerp(progress, previous.getScaleZ(), current.getScaleZ()));
			Quaternionf rotation = quaternion(previous).slerp(quaternion(current), progress);
			Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
			result.setRotation(angles.x, angles.y, angles.z);
		}

		private static Quaternionf quaternion(BoneSnapshot snapshot) {
			return new Quaternionf().rotationZYX(snapshot.getRotZ(), snapshot.getRotY(), snapshot.getRotX());
		}
	}
}
