package org.unitego.lobecorp.client.animation;

import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.animation.LcAnimationControllerIntegration;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;

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

		float partialTick = renderPassInfo.getOrDefaultGeckolibData(DataTickets.PARTIAL_TICK, 1F);
		var pose = LcClientAnimations.visualPoseForEntity(id.intValue(), partialTick);
		if (pose == null) {
			return false;
		}

		var beam = LcClientAnimations.snapshotForEntity(id.intValue(), value -> TheQueenOfHatredAnimationDefinitions.isBeam(value.definitionId()));
		if (beam != null) {
			LcAnimationControllerIntegration.apply(renderPassInfo, snapshots);
			float weight = beamWeight(beam, partialTick);
			var level = Minecraft.getInstance().level;
			var owner = level == null ? null : level.getEntity(id.intValue());
			if (owner != null && LcClientAnimationResources.visualClip(beam.content(), beam.animationName()) != null) {
				pose = TheQueenOfHatredAnimationDefinitions.aimPose(pose, owner.getViewXRot(partialTick));
			}
			apply(pose, snapshots, weight);
		} else {
			apply(pose, snapshots);
		}
		return true;
	}

	private static float beamWeight(LcAnimationSnapshot beam, float partialTick) {
		var parameters = beam.parameters();
		if (!parameters.containsKey(TheQueenOfHatredBeamAnimation.PHASE_STARTED_TICK)) {
			return 0;
		}
		double phase = parameters.getOrDefault(TheQueenOfHatredBeamAnimation.PHASE, 0.0);
        double now = beam.paused() ? beam.serverTick() : LcClientAnimations.renderServerTick(beam.instanceId(), partialTick);
        double elapsed = Math.max(0, now - parameters.get(TheQueenOfHatredBeamAnimation.PHASE_STARTED_TICK));
		double duration = parameters.getOrDefault(phase == TheQueenOfHatredBeamAnimation.Phase.RECOVERY.ordinal()
				? TheQueenOfHatredBeamAnimation.RECOVERY_TICKS : TheQueenOfHatredBeamAnimation.WINDUP_TICKS, 0.0);
		float progress = duration <= 0 ? 1 : (float) Math.clamp(elapsed / duration, 0, 1);
		float weight = phase == TheQueenOfHatredBeamAnimation.Phase.OUTPUT.ordinal() ? 1
				: phase == TheQueenOfHatredBeamAnimation.Phase.RECOVERY.ordinal() ? 1 - progress : progress;
		return weight * weight * (3 - 2 * weight);
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
