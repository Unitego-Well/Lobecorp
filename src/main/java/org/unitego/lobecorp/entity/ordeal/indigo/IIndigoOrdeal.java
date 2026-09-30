package org.unitego.lobecorp.entity.ordeal.indigo;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;

/// 靛蓝系异常实体的类型契约。
public interface IIndigoOrdeal extends IOrdeal {
	@Override
	default boolean isCamp(Entity entity) {
		return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_INDIGO) || entity instanceof IIndigoOrdeal;
	}
}
