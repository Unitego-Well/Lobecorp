package org.unitego.lobecorp.entity.ordeal.green;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.entity.ordeal.violet.IVioletOrdeal;
import org.unitego.lobecorp.init.tag.LcEntityTypeTags;

public interface IGreenOrdeal extends IOrdeal {
    @Override
    default boolean isCamp(Entity entity) {
        return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_GREEN) || entity instanceof IGreenOrdeal;
    }
}
