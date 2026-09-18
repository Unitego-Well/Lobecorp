package org.unitego.lobecorp.entity.ordeal.indigo;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;

public interface IIndigoOrdeal extends IOrdeal {
	@Override
	default boolean isCamp(Entity entity) {
		return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_INDIGO) || entity instanceof IIndigoOrdeal;
	}
}
