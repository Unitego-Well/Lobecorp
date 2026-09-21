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
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredPillarOfLight;

/// 使用原版信标纹理渲染竖直粉色光柱。
public class TheQueenOfHatredPillarOfLightRenderer extends EntityRenderer<TheQueenOfHatredPillarOfLight,
		TheQueenOfHatredPillarOfLightRenderer.RenderState> {
	/// 原版信标光柱纹理。
	private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/beacon_beam.png");
	/// 光柱主题色。
	private static final int COLOR = 0xFFFF69B4;
	/// 信标光柱核心半径。
	private static final float CORE_RADIUS = 0.2F;
	/// 信标光柱外层光晕半径。
	private static final float GLOW_RADIUS = 0.25F;
	/// 外层光晕透明度。
	private static final int GLOW_ALPHA_MASK = 0x66FFFFFF;
	/// 光柱纹理每 tick 的滚动速度。
	private static final float TEXTURE_SCROLL_PER_TICK = 0.02F;

	/// @param context 实体渲染器上下文
	public TheQueenOfHatredPillarOfLightRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public boolean shouldRender(TheQueenOfHatredPillarOfLight entity, Frustum culler,
			double camX, double camY, double camZ) {
		double maximumY = entity.level().getMaxY();
		AABB beamBounds = new AABB(entity.getX() - GLOW_RADIUS, entity.getY(), entity.getZ() - GLOW_RADIUS,
				entity.getX() + GLOW_RADIUS, maximumY, entity.getZ() + GLOW_RADIUS);
		return culler.isVisible(beamBounds);
	}

	@Override
	public void extractRenderState(TheQueenOfHatredPillarOfLight entity, RenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.height = (float) Math.max(entity.level().getMaxY() - entity.getY(), 0.0);
		state.age = entity.tickCount + partialTicks;
	}

	@Override
	public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		RenderType renderType = RenderTypes.beaconBeam(BEAM_TEXTURE, true);
		collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
			poseStack.pushPose();
			poseStack.last().set(pose);
			renderBeam(poseStack, consumer, state.height, CORE_RADIUS, COLOR, state.age);
			renderBeam(poseStack, consumer, state.height, GLOW_RADIUS, COLOR & GLOW_ALPHA_MASK, state.age);
			poseStack.popPose();
		});
	}

	private static void renderBeam(PoseStack poseStack, VertexConsumer consumer, float height, float radius,
			int color, float age) {
		float minimumV = -age * TEXTURE_SCROLL_PER_TICK;
		float maximumV = minimumV + height;
		Matrix4f pose = poseStack.last().pose();
		quad(consumer, pose, -radius, radius, radius, radius, height, color, minimumV, maximumV);
		quad(consumer, pose, radius, radius, radius, -radius, height, color, minimumV, maximumV);
		quad(consumer, pose, radius, -radius, -radius, -radius, height, color, minimumV, maximumV);
		quad(consumer, pose, -radius, -radius, -radius, radius, height, color, minimumV, maximumV);
	}

	private static void quad(VertexConsumer consumer, Matrix4f pose, float firstX, float firstZ,
			float secondX, float secondZ, float height, int color, float minimumV, float maximumV) {
		vertex(consumer, pose, firstX, 0.0F, firstZ, color, 0.0F, minimumV);
		vertex(consumer, pose, firstX, height, firstZ, color, 0.0F, maximumV);
		vertex(consumer, pose, secondX, height, secondZ, color, 1.0F, maximumV);
		vertex(consumer, pose, secondX, 0.0F, secondZ, color, 1.0F, minimumV);
	}

	private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
			int color, float u, float v) {
		consumer.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(0)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(0.0F, 1.0F, 0.0F);
	}

	/// 光柱实体的逐帧渲染数据。
	public static class RenderState extends EntityRenderState {
		public float height;
		public float age;
	}
}
