package org.unitego.lobecorp.conductor.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// 指挥家可施放能力的统一描述和施放契约。
public interface ConductorAbility extends ConductorTargeting {
	Identifier id();

	boolean supports(Mob mob);

	int cooldownTicks(Mob mob);

	default int totalCooldownTicks(Mob mob) {
		return 0;
	}

	default Display display(Mob mob) {
		return new Display(DamageKind.DYNAMIC, 0.0D, AbilityKind.ATTACK);
	}

	default double displayRange(Mob mob) {
		return Math.max(maximumRange(mob), previewRadius(mob));
	}

	boolean cast(Mob mob, @Nullable Entity target, @Nullable Vec3 position);

	enum DamageKind {FIXED, DYNAMIC, NONE}

	enum AbilityKind {BASIC, ATTACK, SUPPORT}

	record Display(DamageKind damageKind, double damage, AbilityKind abilityKind) {
	}
}
