package org.unitego.lobecorp.client.animation;

import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
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

		float partialTick = renderPassInfo.getOrDefaultGeckolibData(DataTickets.PARTIAL_TICK, 1F);
		var pose = LcClientAnimations.visualPoseForEntity(id.intValue(), partialTick);
		if (pose == null) {
			return false;
		}

		var beam = LcClientAnimations.snapshotForEntity(id.intValue(), value -> TheQueenOfHatredAnimationDefinitions.isBeam(value.definitionId()));
		if (beam != null) {
			float weight = beamWeight(beam, partialTick);
			apply(pose, snapshots, weight);
		} else {
			apply(pose, snapshots);
		}
		return true;
	}

	private static float beamWeight(LcAnimationSnapshot beam, float partialTick) {
		return (float) LcClientAnimations.beamPoseWeight(beam.ownerEntityId(), partialTick);
	}

	private static void apply(LcSkeletalPose pose, BoneSnapshots snapshots, float weight) {
		pose.bones().forEach((name, transform) -> snapshots.get(name).ifPresent(snapshot -> {
			var position = transform.position();
			var angles = pose.rotationAngles(name);
			var scale = transform.scale();
			var rotation = new Quaternionf().rotationZYX(snapshot.getRotZ(), snapshot.getRotY(), snapshot.getRotX())
					.slerp(new Quaternionf().rotationZYX((float) -angles.z, (float) angles.y, (float) -angles.x), weight)
					.getEulerAnglesZYX(new Vector3f());
			snapshot.setTranslation(Mth.lerp(weight, snapshot.getTranslateX(), (float) (position.x * MODEL_UNITS_PER_BLOCK)),
					Mth.lerp(weight, snapshot.getTranslateY(), (float) (position.y * MODEL_UNITS_PER_BLOCK)),
					Mth.lerp(weight, snapshot.getTranslateZ(), (float) (-position.z * MODEL_UNITS_PER_BLOCK)));
			snapshot.setRotation(rotation.x, rotation.y, rotation.z);
			snapshot.setScale(Mth.lerp(weight, snapshot.getScaleX(), (float) scale.x),
					Mth.lerp(weight, snapshot.getScaleY(), (float) scale.y), Mth.lerp(weight, snapshot.getScaleZ(), (float) scale.z));
		}));
	}

	public static void apply(LcSkeletalPose pose, BoneSnapshots snapshots) {
		apply(pose, snapshots, 1);
	}

}
