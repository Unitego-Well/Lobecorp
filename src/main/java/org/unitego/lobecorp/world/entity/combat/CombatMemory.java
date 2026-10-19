package org.unitego.lobecorp.world.entity.combat;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// 受击仇恨与群体共享目标仅存于当前实体运行态，不持久化、不同步。
public class CombatMemory {
	private final Set<UUID> attackers = new HashSet<>();
	private @Nullable UUID sharedTarget;

	public void recordAttacker(UUID attacker) {
		attackers.add(attacker);
	}

	public boolean wasAttackedBy(UUID attacker) {
		return attackers.contains(attacker);
	}

	public @Nullable UUID sharedTarget() {
		return sharedTarget;
	}

	public void setSharedTarget(@Nullable UUID target) {
		sharedTarget = target;
	}
}
