package org.unitego.lobecorp.registry.entity.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.event.client.EntityCorpseReverseEvent;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class EntityCorpseReverse {
	@SubscribeEvent
	public static void onRegister(EntityCorpseReverseEvent event) {
		event.register(OrdealEntityTypes.SWEEPER.get());
	}
}
