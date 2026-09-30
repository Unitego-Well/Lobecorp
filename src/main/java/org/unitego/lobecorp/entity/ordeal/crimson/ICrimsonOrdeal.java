package org.unitego.lobecorp.entity.ordeal.crimson;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;

/// 深红系异常实体的类型契约。
public interface ICrimsonOrdeal extends IOrdeal {
	@Override
	default boolean isCamp(Entity entity) {
		return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_CRIMSON) || entity instanceof ICrimsonOrdeal;
	}
}
