package org.unitego.lobecorp.world.entity.combat;

import net.minecraft.world.entity.Entity;

public interface ICombatant {
	CombatPolicy getCombatPolicy();

	default boolean isNaturalCombatAlly(Entity entity) {
		return false;
	}

	default boolean acceptsConductorControl() {
		return true;
	}
}
