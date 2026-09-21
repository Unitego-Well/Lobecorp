package org.unitego.lobecorp.entity.client.renderer.abnormalitie;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredLaser;

/// 使用原版信标纹理渲染横向激光实体。
public class TheQueenOfHatredLaserRenderer extends EntityRenderer<TheQueenOfHatredLaser,
		TheQueenOfHatredLaserRenderer.RenderState> {
	/// 原版信标光柱纹理。
	private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/beacon_beam.png");
	/// 未强化光束颜色。
	private static final int NORMAL_COLOR = 0xFFFF69B4;
	/// 强化光束颜色。
	private static final int ENHANCED_COLOR = 0xFFFF2020;
	/// 外层光晕相对核心的半径倍率。
	private static final float GLOW_RADIUS_MULTIPLIER = 1.25F;
	/// 光柱纹理每 tick 的滚动速度。
	private static final float TEXTURE_SCROLL_PER_TICK = 0.02F;

	/// @param context 实体渲染器上下文
	public TheQueenOfHatredLaserRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public boolean shouldRender(TheQueenOfHatredLaser entity, Frustum culler,
			double camX, double camY, double camZ) {
		Vec3 end = entity.position().add(entity.direction().scale(entity.beamLength()));
		AABB beamBounds = new AABB(entity.position(), end).inflate(entity.beamRadius());
		return culler.isVisible(beamBounds);
	}

	@Override
	public void extractRenderState(TheQueenOfHatredLaser entity, RenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.direction = entity.direction();
		state.length = entity.beamLength();
		state.radius = entity.beamRadius();
		state.enhanced = entity.isEnhanced();
		state.age = entity.tickCount + partialTicks;
	}

	@Override
	public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		int color = state.enhanced ? ENHANCED_COLOR : NORMAL_COLOR;
		RenderType renderType = RenderTypes.beaconBeam(BEAM_TEXTURE, true);
		collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
			poseStack.pushPose();
			poseStack.last().set(pose);
			Vector3f direction = state.direction.toVector3f();
			poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
					direction.x, direction.y, direction.z));
			renderBeam(poseStack, consumer, state.length, state.radius, color, state.age);
			renderBeam(poseStack, consumer, state.length, state.radius * GLOW_RADIUS_MULTIPLIER,
					color & 0x66FFFFFF, state.age);
			poseStack.popPose();
		});
	}

	private static void renderBeam(PoseStack poseStack, VertexConsumer consumer, float length, float radius,
			int color, float age) {
		float minimumV = -age * TEXTURE_SCROLL_PER_TICK;
		float maximumV = minimumV + length;
		Matrix4f pose = poseStack.last().pose();
		quad(consumer, pose, -radius, radius, radius, radius, length, color, minimumV, maximumV);
		quad(consumer, pose, radius, radius, radius, -radius, length, color, minimumV, maximumV);
		quad(consumer, pose, radius, -radius, -radius, -radius, length, color, minimumV, maximumV);
		quad(consumer, pose, -radius, -radius, -radius, radius, length, color, minimumV, maximumV);
	}

	private static void quad(VertexConsumer consumer, Matrix4f pose, float firstX, float firstZ,
			float secondX, float secondZ, float length, int color, float minimumV, float maximumV) {
		vertex(consumer, pose, firstX, 0.0F, firstZ, color, 0.0F, minimumV);
		vertex(consumer, pose, firstX, length, firstZ, color, 0.0F, maximumV);
		vertex(consumer, pose, secondX, length, secondZ, color, 1.0F, maximumV);
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

	/// 激光实体的不可变帧渲染数据。
	public static class RenderState extends EntityRenderState {
		public Vec3 direction = new Vec3(0.0, 0.0, 1.0);
		public float length;
		public float radius;
		public float age;
		public boolean enhanced;
	}
}
