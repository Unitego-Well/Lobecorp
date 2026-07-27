package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class SweeperRenderer<T extends Entity & GeoAnimatable, R extends EntityRenderState> extends GeoEntityRenderer<T, R> {
    public SweeperRenderer(EntityRendererProvider.Context context, EntityType<T> entityType) {
        super(context, entityType);
    }
}
