package org.unitego.lobecorp.entity.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.entity.EntityCorpse;

public class EntityCorpseRenderer extends EntityRenderer<EntityCorpse, EntityCorpseRenderer.EntityCorpseRenderState> {
    public EntityCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityCorpseRenderState createRenderState() {
        return new EntityCorpseRenderState();
    }

    @Override
    public void extractRenderState(EntityCorpse entity, EntityCorpseRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Entity ownerEntity = entity.getOwnerEntity();
        if (ownerEntity != null) {
            EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer = state.ownerEntityRenderer = (EntityRenderer<Entity, EntityRenderState>) entityRenderDispatcher.getRenderer(ownerEntity);
            state.ownerEntityRenderState = ownerEntityRenderer.createRenderState(ownerEntity, partialTicks);
        }
    }

    @Override
    public void submit(EntityCorpseRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        if (state.ownerEntityRenderer == null || state.ownerEntityRenderState == null) {
            return;
        }
        state.ownerEntityRenderer.submit(state.ownerEntityRenderState, poseStack, submitNodeCollector, camera);
    }

    public static class EntityCorpseRenderState extends EntityRenderState {
        @Nullable
        public EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer;
        @Nullable
        public EntityRenderState ownerEntityRenderState;
    }
}
