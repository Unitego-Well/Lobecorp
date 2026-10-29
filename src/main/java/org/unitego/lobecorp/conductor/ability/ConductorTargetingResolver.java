package org.unitego.lobecorp.conductor.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.conductor.ConductorUtil;

/// 根据技能 ID 解析实体可用的指挥家目标能力。
public final class ConductorTargetingResolver {
	private ConductorTargetingResolver() {
	}

	public static @Nullable IConductorTargeting find(Mob mob, String id) {
		Identifier identifier = ConductorUtil.parseIdentifier(id);
		return identifier == null ? null : ConductorUtil.ability(mob, identifier);
	}
}
