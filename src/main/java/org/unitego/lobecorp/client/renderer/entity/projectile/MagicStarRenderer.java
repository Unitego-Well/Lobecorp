package org.unitego.lobecorp.client.renderer.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.unitego.lobecorp.entity.projectile.MagicStarProjectile;

public class MagicStarRenderer extends EntityRenderer<MagicStarProjectile, MagicStarRenderState> {
	private final ItemModelResolver itemModelResolver;

	public MagicStarRenderer(EntityRendererProvider.Context context) {
		super(context);
		itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public MagicStarRenderState createRenderState() {
		return new MagicStarRenderState();
	}

	@Override
	public void extractRenderState(MagicStarProjectile entity, MagicStarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.scale = entity.starSize().width();
		itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.GROUND, entity);
	}

	@Override
	public void submit(MagicStarRenderState state, PoseStack poseStack,
	                   SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.scale(state.scale, state.scale, state.scale);
		poseStack.mulPose(camera.orientation);
		state.item.submit(poseStack, submitNodeCollector, state.lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
