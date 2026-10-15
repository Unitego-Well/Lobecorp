package org.unitego.lobecorp.client.animation;

import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;

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

		apply(pose, snapshots);
		return true;
	}

	public static void apply(LcSkeletalPose pose, BoneSnapshots snapshots) {
		pose.bones().forEach((name, transform) -> snapshots.get(name).ifPresent(snapshot -> {
			var position = transform.position();
			var angles = pose.rotationAngles(name);
			var scale = transform.scale();
			snapshot.setTranslation((float) (position.x * MODEL_UNITS_PER_BLOCK),
					(float) (position.y * MODEL_UNITS_PER_BLOCK), (float) (-position.z * MODEL_UNITS_PER_BLOCK));
			snapshot.setRotation((float) -angles.x, (float) angles.y, (float) -angles.z);
			snapshot.setScale((float) scale.x, (float) scale.y, (float) scale.z);
		}));
	}
}
