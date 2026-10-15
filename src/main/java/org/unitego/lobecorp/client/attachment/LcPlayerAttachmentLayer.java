package org.unitego.lobecorp.client.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.client.animation.LcAnimationDisplays;

import java.util.Set;

/// LivingEntityRenderer 已在提交渲染层前完成 setupAnim，因此不会消费上一帧姿态。
@NullMarked
public class LcPlayerAttachmentLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public LcPlayerAttachmentLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		var source = state.getRenderData(LcPlayerRenderAnchors.SOURCE);
		if (source == null || state.isSpectator) {
			return;
		}

		var camera = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
		var worldFromRender = new Matrix4f().translation((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
		var context = LcPlayerRenderAnchors.context(getParentModel(), state, source, poses, worldFromRender,
				LcRenderAnchorContext.WORLD_VIEW,
				Set.of(LcPlayerRenderAnchors.BACK, LcPlayerRenderAnchors.LEFT_HAND, LcPlayerRenderAnchors.RIGHT_HAND));
		LcAnimationDisplays.submitRenderAnchors(context, collector, camera, light);
	}
}
