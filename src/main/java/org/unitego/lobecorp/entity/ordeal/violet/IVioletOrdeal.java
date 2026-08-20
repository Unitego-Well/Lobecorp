package org.unitego.lobecorp.entity.ordeal.violet;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;

public interface IVioletOrdeal extends IOrdeal {
    @Override
    default boolean isCamp(Entity entity) {
        return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_VIOLET) || entity instanceof IVioletOrdeal;
    }
}
