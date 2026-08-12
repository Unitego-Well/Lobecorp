package org.unitego.lobecorp.entity.client.renderer.ordeal;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperVariant;

public class SweeperRenderer extends GeoEntityRenderer<Sweeper, LivingEntityRenderState> {
    /// 渲染时把实体的变种传给 [SweeperModel]
    public static final DataTicket<SweeperVariant> VARIANT = DataTickets.create("sweeper_variant", SweeperVariant.class);

    public SweeperRenderer(EntityRendererProvider.Context context, EntityType<Sweeper> entityType) {
        super(context, new SweeperModel());
    }

    @Override
    public void captureDefaultRenderState(Sweeper animatable, @Nullable Void relatedObject, LivingEntityRenderState renderState, float partialTick) {
        super.captureDefaultRenderState(animatable, relatedObject, renderState, partialTick);
        if (renderState instanceof GeoRenderState geoRenderState) {
            geoRenderState.addGeckolibData(VARIANT, animatable.getVariant());
        }
    }
}
