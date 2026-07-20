package org.unitego.lobecorp.entity.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.entity.EntityCorpse;

public class EntityCorpseRenderer<T extends EntityCorpse<?>> extends EntityRenderer<T, EntityCorpseRenderer.EntityCorpseRenderState> {
    public EntityCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityCorpseRenderState createRenderState() {
        return new EntityCorpseRenderState();
    }

    @Override
    public void extractRenderState(T entity, EntityCorpseRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Entity ownerEntity = entity.getOwnerEntity();
        if (ownerEntity != null) {
            EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer =
                    state.ownerEntityRenderer = (EntityRenderer<Entity, EntityRenderState>) entityRenderDispatcher.getRenderer(ownerEntity);
            EntityRenderState ownerState = state.ownerEntityRenderState = ownerEntityRenderer.createRenderState(ownerEntity, partialTicks);
            if (ownerState instanceof LivingEntityRenderState livingState) {
                // 清除死亡/受伤导致的红色闪白叠加层和死亡翻转，尸体不应显示伤害效果
                livingState.hasRedOverlay = false;
                livingState.deathTime = 0;
            }
        }
    }

    @Override
    public void submit(EntityCorpseRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);

        if (state.ownerEntityRenderer == null) {
            return;
        }
        EntityRenderState ownerState = state.ownerEntityRenderState;
        if (ownerState == null) {
            return;
        }
        poseStack.pushPose();
        if (ownerState instanceof LivingEntityRenderState livingEntityRenderState) {
            poseStack.mulPose(Axis.YP.rotationDegrees(livingEntityRenderState.bodyRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.translate(0, -ownerState.boundingBoxHeight / 2, 0);
            livingEntityRenderState.bodyRot = 0;
        }

        state.ownerEntityRenderer.submit(ownerState, poseStack, submitNodeCollector, camera);
        poseStack.popPose();
    }

    public static class EntityCorpseRenderState extends EntityRenderState {
        @Nullable
        public EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer;
        @Nullable
        public EntityRenderState ownerEntityRenderState;
    }
}
