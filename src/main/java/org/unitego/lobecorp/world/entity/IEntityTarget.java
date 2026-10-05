package org.unitego.lobecorp.world.entity;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public interface IEntityTarget {
	@Nullable
	Entity getEntityTarget();

	void setEntityTarget(@Nullable Entity entity);
}
