package org.unitego.lobecorp.animation;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

public final class LcAnimationRuntime {
	private LcAnimationRuntime() {
	}

	public static <R extends GeoRenderState> void apply(RenderPassInfo<R> renderPassInfo,
			BoneSnapshots snapshots) {
		LcAnimationFrame frame = renderPassInfo.getGeckolibData(LcAnimationDataTickets.ANIMATION_FRAME);
		if (frame == null) {
			return;
		}

		for (LcAnimationFrame.Layer layer : frame.layers()) {
			Map<String, LcChannelWeights> mask = layer.definition().mask().resolve(renderPassInfo.model());
			if (mask.isEmpty()) {
				continue;
			}
			Map<String, LcAnimationFrame.BonePose> pose = layer.poseState().resolve(
					layer.controllerState(), renderPassInfo.model(), layer.transitionProgress());
			for (Map.Entry<String, LcAnimationFrame.BonePose> poseEntry : pose.entrySet()) {
				BoneSnapshot destination = snapshots.get(poseEntry.getKey()).orElse(null);
				if (destination == null) {
					continue;
				}
				LcAnimationFrame.BonePose bonePose = poseEntry.getValue();
				LcChannelWeights channelWeights = mask.get(poseEntry.getKey());
				if (channelWeights == null) {
					continue;
				}
				LcChannelWeights weights = channelWeights.multiply(layer.weight() * bonePose.weight());
				blend(destination, bonePose.snapshot(), weights, layer.definition().blendMode());
			}
		}
	}

	private static void blend(BoneSnapshot destination, BoneSnapshot layer, LcChannelWeights weights,
			LcBlendMode blendMode) {
		if (blendMode == LcBlendMode.ADDITIVE) {
			destination.setTranslation(
					destination.getTranslateX() + layer.getTranslateX() * weights.translation(),
					destination.getTranslateY() + layer.getTranslateY() * weights.translation(),
					destination.getTranslateZ() + layer.getTranslateZ() * weights.translation());
			destination.setScale(
					destination.getScaleX() * lerp(1.0F, layer.getScaleX(), weights.scale()),
					destination.getScaleY() * lerp(1.0F, layer.getScaleY(), weights.scale()),
					destination.getScaleZ() * lerp(1.0F, layer.getScaleZ(), weights.scale()));
			blendRotation(destination, layer, weights.rotation(), true);
			return;
		}

		destination.setTranslation(
				lerp(destination.getTranslateX(), layer.getTranslateX(), weights.translation()),
				lerp(destination.getTranslateY(), layer.getTranslateY(), weights.translation()),
				lerp(destination.getTranslateZ(), layer.getTranslateZ(), weights.translation()));
		destination.setScale(
				lerp(destination.getScaleX(), layer.getScaleX(), weights.scale()),
				lerp(destination.getScaleY(), layer.getScaleY(), weights.scale()),
				lerp(destination.getScaleZ(), layer.getScaleZ(), weights.scale()));
		blendRotation(destination, layer, weights.rotation(), false);
	}

	private static void blendRotation(BoneSnapshot destination, BoneSnapshot layer, float weight,
			boolean additive) {
		if (weight <= 0.0F) {
			return;
		}
		Quaternionf base = quaternion(destination);
		Quaternionf target = quaternion(layer);
		if (additive) {
			target = new Quaternionf().slerp(target, weight);
			base.mul(target);
		} else {
			base.slerp(target, weight);
		}
		Vector3f rotation = base.getEulerAnglesZYX(new Vector3f());
		destination.setRotation(rotation.x, rotation.y, rotation.z);
	}

	private static Quaternionf quaternion(BoneSnapshot snapshot) {
		return new Quaternionf().rotationZYX(snapshot.getRotZ(), snapshot.getRotY(), snapshot.getRotX());
	}

	private static float lerp(float start, float end, float weight) {
		return start + (end - start) * weight;
	}
}
