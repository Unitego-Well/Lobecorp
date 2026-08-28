package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.registry.client.LcDataTickets;

public class SweeperRenderer extends GeoEntityRenderer<Sweeper, LivingEntityRenderState> {

    public SweeperRenderer(EntityRendererProvider.Context context) {
        super(context, new SweeperModel());
    }

    @Override
    public int getPackedOverlay(Sweeper animatable, @Nullable Void relatedObject, float u, float partialTick) {
        return super.getPackedOverlay(animatable, relatedObject, u, partialTick);
    }

    @Override
    public void addRenderData(Sweeper animatable, @Nullable Void relatedObject, LivingEntityRenderState renderState, float partialTick) {
        renderState.addGeckolibData(LcDataTickets.SWEEPER_VARIANT, animatable.getVariant());
        renderState.addGeckolibData(LcDataTickets.HEALTHY, animatable.getHealth());
    }
}
