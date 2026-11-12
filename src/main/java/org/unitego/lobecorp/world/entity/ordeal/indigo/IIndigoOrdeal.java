package org.unitego.lobecorp.world.entity.ordeal.indigo;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.registry.world.tag.LcEntityTypeTags;
import org.unitego.lobecorp.world.entity.ordeal.IOrdeal;

/// 靛蓝系异常实体的类型契约。
public interface IIndigoOrdeal extends IOrdeal {
	@Override
	default boolean isCamp(Entity entity) {
		return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_INDIGO) || entity instanceof IIndigoOrdeal;
	}
}
