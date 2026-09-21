package org.unitego.lobecorp.registry.entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class EntityAttributeCreation {
	@SubscribeEvent
	public static void registry(EntityAttributeCreationEvent event) {
		event.put(LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpse.createAttributes().build());
		event.put(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatred.createAttributes().build());
		event.put(OrdealEntityTypes.SWEEPER.get(), Sweeper.createAttributes().build());
	}

	@SubscribeEvent
	public static void addCommonAttributes(EntityAttributeModificationEvent event) {
		event.getTypes().forEach(type -> event.add(type, LcAttributes.DAMAGE_TAKEN_MULTIPLIER));
	}
}
