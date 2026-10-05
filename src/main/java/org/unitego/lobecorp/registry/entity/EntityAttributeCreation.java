package org.unitego.lobecorp.registry.entity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;

public class EntityAttributeCreation {
	public static void registry(EntityAttributeCreationEvent event) {
		event.put(LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpse.createAttributes().build());
		event.put(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatred.createAttributes().build());
		event.put(OrdealEntityTypes.SWEEPER.get(), Sweeper.createAttributes().build());
	}

	public static void addCommonAttributes(EntityAttributeModificationEvent event) {
		event.getTypes().forEach(type -> event.add(type, LcAttributes.DAMAGE_TAKEN_MULTIPLIER));
	}
}
