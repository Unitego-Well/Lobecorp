package org.unitego.lobecorp.registry;

import com.geckolib.renderer.GeoEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.unitego.lobecorp.init.LcEntityTypes;

@EventBusSubscriber(Dist.CLIENT)
public class EntityRenderers {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                LcEntityTypes.SWEEPER.get(),
                context -> new GeoEntityRenderer<>(context, LcEntityTypes.SWEEPER.get())
        );
    }
}
