package org.unitego.lobecorp.registry.entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class EntityAttributeCreation {
    @SubscribeEvent
    public static void registry(EntityAttributeCreationEvent event) {
        event.put(LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpse.createAttributes().build());
        event.put(OrdealEntityTypes.SWEEPER.get(), Sweeper.createAttributes().build());
    }
}
