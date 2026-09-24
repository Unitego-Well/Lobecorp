package org.unitego.lobecorp.client.particle.photon;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.BiConsumer;

public final class PhotonGeoAnchorLayer<T extends GeoAnimatable, R extends GeoRenderState>
		extends GeoRenderLayer<T, Void, R> {
	public PhotonGeoAnchorLayer(GeoRenderer<T, Void, R> renderer) {
		super(renderer);
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<R> renderPassInfo,
			@NonNull BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
		long instanceId = renderPassInfo.getOrDefaultGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID, 0L);
		List<PhotonAnimationEffects.ActiveEffect> effects =
				PhotonAnimationEffects.INSTANCE.getActiveEffects(instanceId);
		for (PhotonAnimationEffects.ActiveEffect effect : effects) {
			renderPassInfo.model().getLocator(effect.anchorName())
					.ifPresentOrElse(locator -> consumer.accept(locator.parent(),
									(info, bone, renderTasks) -> captureLocator(info, locator, effect)),
							() -> renderPassInfo.model().getBone(effect.anchorName())
									.ifPresent(bone -> consumer.accept(bone,
											(info, ignored, renderTasks) -> capture(info, effect))));
		}
	}

	private static <R extends GeoRenderState> void captureLocator(RenderPassInfo<R> renderPassInfo,
			GeoLocator locator, PhotonAnimationEffects.ActiveEffect effect) {
		PoseStack poseStack = renderPassInfo.poseStack();
		poseStack.pushPose();
		locator.parent().translateAwayFromPivotPoint(poseStack);
		poseStack.translate(locator.offsetX() / 16.0F, locator.offsetY() / 16.0F, locator.offsetZ() / 16.0F);
		if (locator.rotZ() != 0.0F) {
			poseStack.mulPose(Axis.ZP.rotation(locator.rotZ()));
		}
		if (locator.rotY() != 0.0F) {
			poseStack.mulPose(Axis.YP.rotation(locator.rotY()));
		}
		if (locator.rotX() != 0.0F) {
			poseStack.mulPose(Axis.XP.rotation(locator.rotX()));
		}
		capture(renderPassInfo, effect);
		poseStack.popPose();
	}

	private static <R extends GeoRenderState> void capture(RenderPassInfo<R> renderPassInfo,
			PhotonAnimationEffects.ActiveEffect effect) {
		Matrix4f matrix = renderPassInfo.poseStack().last().pose();
		Vector3f position = matrix.getTranslation(new Vector3f());
		position.add((float)renderPassInfo.cameraState().pos.x,
				(float)renderPassInfo.cameraState().pos.y,
				(float)renderPassInfo.cameraState().pos.z);
		Quaternionf rotation = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
		Vector3f scale = matrix.getScale(new Vector3f());
		effect.executor().updateAnchor(new PhotonAnchorTransform(position, rotation, scale));
	}
}
