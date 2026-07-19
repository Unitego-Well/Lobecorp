package org.unitego.lobecorp.registry.client;

import com.geckolib.renderer.GeoEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.unitego.lobecorp.init.OrdealEntityTypes;

@EventBusSubscriber(Dist.CLIENT)
public class EntityRenderers {
    @SubscribeEvent
    public static void registry(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(OrdealEntityTypes.SWEEPER.get(),
                context -> new GeoEntityRenderer<>(context, OrdealEntityTypes.SWEEPER.get()));
    }
}
