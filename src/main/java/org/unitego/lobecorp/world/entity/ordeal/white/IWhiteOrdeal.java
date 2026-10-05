package org.unitego.lobecorp.world.entity.ordeal.white;

import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.world.entity.ordeal.IOrdeal;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;

/// 白色系异常实体的类型契约。
public interface IWhiteOrdeal extends IOrdeal {
	@Override
	default boolean isCamp(Entity entity) {
		return IOrdeal.super.isCamp(entity) || entity.is(LcEntityTypeTags.ORDEAL_WHITE) || entity instanceof IWhiteOrdeal;
	}
}
