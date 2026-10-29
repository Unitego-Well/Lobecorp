package org.unitego.lobecorp.client.animation;

import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;

/// 公共姿态使用格和向前 +Z；这里只转换 GeckoLib 5.5.2 的动画增量字段。
public class LcAnimationBoneAdapter {
	private static final double MODEL_UNITS_PER_BLOCK = 16;

	public static boolean hasEntityAnimation(RenderPassInfo<?> renderPassInfo) {
		if (!(renderPassInfo.renderer() instanceof GeoEntityRenderer<?, ?>)) {
			return false;
		}

		var id = renderPassInfo.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		return id != null && LcClientAnimations.hasEntityAnimation(id.intValue());
	}

	public static boolean blendsEntityAnimation(RenderPassInfo<?> renderPassInfo) {
		var id = renderPassInfo.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		return id != null && LcClientAnimations.hasEntityAnimation(id.intValue(),
				snapshot -> TheQueenOfHatredAnimationDefinitions.isBeam(snapshot.definitionId()));
	}

	public static boolean applyEntity(RenderPassInfo<?> renderPassInfo, BoneSnapshots snapshots) {
		if (!(renderPassInfo.renderer() instanceof GeoEntityRenderer<?, ?>)) {
			return false;
		}

		var id = renderPassInfo.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		if (id == null) {
			return false;
		}

		var partialTick = renderPassInfo.getOrDefaultGeckolibData(DataTickets.PARTIAL_TICK, 1F);
		var pose = LcClientAnimations.visualPoseForEntity(id.intValue(), partialTick);
		if (pose == null) {
			return false;
		}

		apply(pose, snapshots, LcClientAnimations.beamPoseWeight(id.intValue(), partialTick));
		return true;
	}

	public static void apply(LcSkeletalPose pose, BoneSnapshots snapshots) {
		apply(pose, snapshots, 1);
	}

	private static void apply(LcSkeletalPose pose, BoneSnapshots snapshots, double weight) {
		pose.bones().forEach((name, transform) -> snapshots.get(name).ifPresent(snapshot -> {
			var position = transform.position();
			var angles = pose.rotationAngles(name);
			var scale = transform.scale();
			snapshot.setTranslation(lerp(snapshot.getTranslateX(), position.x * MODEL_UNITS_PER_BLOCK, weight),
					lerp(snapshot.getTranslateY(), position.y * MODEL_UNITS_PER_BLOCK, weight),
					lerp(snapshot.getTranslateZ(), -position.z * MODEL_UNITS_PER_BLOCK, weight));
			snapshot.setRotation(lerp(snapshot.getRotX(), -angles.x, weight),
					lerp(snapshot.getRotY(), angles.y, weight), lerp(snapshot.getRotZ(), -angles.z, weight));
			snapshot.setScale(lerp(snapshot.getScaleX(), scale.x, weight),
					lerp(snapshot.getScaleY(), scale.y, weight), lerp(snapshot.getScaleZ(), scale.z, weight));
		}));
	}

	private static float lerp(float from, double to, double weight) {
		return (float) (from + (to - from) * weight);
	}
}
