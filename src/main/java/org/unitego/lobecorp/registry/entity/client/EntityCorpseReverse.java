package org.unitego.lobecorp.registry.entity.client;

import org.unitego.lobecorp.event.EntityCorpseReverseEvent;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

public class EntityCorpseReverse {
	public static void onRegister(EntityCorpseReverseEvent event) {
		event.register(OrdealEntityTypes.SWEEPER.get());
	}
}
