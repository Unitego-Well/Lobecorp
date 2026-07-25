package org.unitego.lobecorp.entity.ordeal.white;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.entity.ordeal.violet.IVioletOrdeal;
import org.unitego.lobecorp.init.tag.LcEntityTypeTags;

public interface IWhiteOrdeal extends IOrdeal {
    @Override
    default boolean isCamp(Entity entity) {
        return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_WHITE) || entity instanceof IWhiteOrdeal;
    }
}
