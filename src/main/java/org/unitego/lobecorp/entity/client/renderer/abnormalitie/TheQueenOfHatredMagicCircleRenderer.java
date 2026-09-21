package org.unitego.lobecorp.entity.client.renderer.abnormalitie;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredMagicCircle;

/// 在水平面使用原版未知纹理渲染法阵占位图。
public class TheQueenOfHatredMagicCircleRenderer extends EntityRenderer<TheQueenOfHatredMagicCircle,
		TheQueenOfHatredMagicCircleRenderer.RenderState> {
	/// 避免法阵与脚下方块表面重叠闪烁的高度。
	private static final float SURFACE_OFFSET = 0.01F;
	/// 法阵占位图使用的完整不透明白色。
	private static final int COLOR = 0xFFFFFFFF;

	/// @param context 实体渲染器上下文
	public TheQueenOfHatredMagicCircleRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public boolean shouldRender(TheQueenOfHatredMagicCircle entity, Frustum culler,
			double camX, double camY, double camZ) {
		return culler.isVisible(new AABB(entity.position(), entity.position())
				.inflate(TheQueenOfHatredMagicCircle.RADIUS, SURFACE_OFFSET, TheQueenOfHatredMagicCircle.RADIUS));
	}

	@Override
	public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		RenderType renderType = RenderTypes.entityTranslucentEmissive(MissingTextureAtlasSprite.getLocation());
		collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
			poseStack.pushPose();
			poseStack.last().set(pose);
			renderCircle(poseStack, consumer);
			poseStack.popPose();
		});
	}

	private static void renderCircle(PoseStack poseStack, VertexConsumer consumer) {
		float radius = TheQueenOfHatredMagicCircle.RADIUS;
		Matrix4f pose = poseStack.last().pose();
		vertex(consumer, pose, -radius, -radius, 0.0F, 0.0F);
		vertex(consumer, pose, -radius, radius, 0.0F, 1.0F);
		vertex(consumer, pose, radius, radius, 1.0F, 1.0F);
		vertex(consumer, pose, radius, -radius, 1.0F, 0.0F);
	}

	private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float z, float u, float v) {
		consumer.addVertex(pose, x, SURFACE_OFFSET, z)
				.setColor(COLOR)
				.setUv(u, v)
				.setOverlay(0)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(0.0F, 1.0F, 0.0F);
	}

	/// 法阵没有额外的逐帧同步渲染数据。
	public static class RenderState extends EntityRenderState {
	}
}
