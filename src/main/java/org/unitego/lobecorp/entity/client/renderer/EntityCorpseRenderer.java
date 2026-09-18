package org.unitego.lobecorp.entity.client.renderer;

import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.registry.client.LcDataTickets;

public class EntityCorpseRenderer<T extends EntityCorpse<?>> extends EntityRenderer<T, EntityCorpseRenderer.RenderState> {
	public static final ContextKey<Boolean> IS_REVERSE = new ContextKey<>(Lobecorp.id("is_reverse"));

	public EntityCorpseRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void extractRenderState(T entity, RenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Entity ownerEntity = entity.getOwnerEntity();
		if (ownerEntity == null) {
			return;
		}
		EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer =
				state.ownerEntityRenderer = (EntityRenderer<Entity, EntityRenderState>) entityRenderDispatcher.getRenderer(ownerEntity);
		EntityRenderState ownerState = state.ownerEntityRenderState = ownerEntityRenderer.createRenderState(ownerEntity, partialTicks);

		ownerState.addGeckolibData(LcDataTickets.IS_CORPSE, true);

		// 清除死亡/受伤导致的红色闪白叠加层和死亡翻转，尸体不应显示伤害效果
		if (ownerState instanceof LivingEntityRenderState livingState) {
			livingState.hasRedOverlay = false;
			livingState.deathTime = 0;
		}
		if (ownerEntity instanceof LivingEntity livingEntity) {
			livingEntity.hurtTime = 0;
			livingEntity.deathTime = 0;
		}
	}

	@Override
	public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		super.submit(state, poseStack, submitNodeCollector, camera);
		if (state.ownerEntityRenderer == null) {
			return;
		}
		EntityRenderState ownerState = state.ownerEntityRenderState;
		if (ownerState == null) {
			return;
		}
		poseStack.pushPose();
		if (state.getRenderData(IS_REVERSE) == null || Boolean.TRUE.equals(state.getRenderData(IS_REVERSE))) {
			if (ownerState instanceof LivingEntityRenderState livingEntityRenderState) {
				poseStack.mulPose(Axis.YP.rotationDegrees(livingEntityRenderState.bodyRot));
				poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
				poseStack.translate(0, -ownerState.boundingBoxHeight / 2, 0);
				livingEntityRenderState.bodyRot = 0;
			} else {
				poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
				poseStack.translate(0, -ownerState.boundingBoxHeight / 2, 0);
			}
		}

		state.ownerEntityRenderer.submit(ownerState, poseStack, submitNodeCollector, camera);
		poseStack.popPose();
	}

	public static class RenderState extends EntityRenderState {
		@Nullable
		public EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer;
		@Nullable
		public EntityRenderState ownerEntityRenderState;
	}
}
