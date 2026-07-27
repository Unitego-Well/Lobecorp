package org.unitego.lobecorp.entity;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public interface IEntityTarget {
    @Nullable
    Entity getEntityTarget();

    void setEntityTarget(@Nullable Entity entity);
}
