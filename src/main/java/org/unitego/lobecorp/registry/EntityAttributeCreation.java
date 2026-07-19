package org.unitego.lobecorp.registry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.unitego.lobecorp.init.OrdealEntityTypes;
import org.unitego.lobecorp.ordeal.indigo.Sweeper;

@EventBusSubscriber
public class EntityAttributeCreation {
    @SubscribeEvent
    public static void registry(EntityAttributeCreationEvent event) {
        event.put(OrdealEntityTypes.SWEEPER.get(), Sweeper.createAttributes().build());
    }
}
