package org.unitego.lobecorp.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.event.client.EntityCorpseReverseEvent;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class Events {

    @SubscribeEvent
    public static void onClientSetup(EntityRenderersEvent.AddLayers event) {
        NeoForge.EVENT_BUS.post(new EntityCorpseReverseEvent());
    }
}
