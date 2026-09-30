package org.unitego.lobecorp.conductor.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.control.ConductorSonicBoom;

import java.util.List;

/// 监守者声波的指挥家能力适配。
public final class WardenSonicBoomAbility implements ConductorAbility {
	public static final Identifier ID = Lobecorp.id("warden_sonic_boom");

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public boolean supports(Mob mob) {
		return mob instanceof Warden;
	}

	@Override
	public Vec3 facingPosition(Mob mob) {
		return ConductorSonicBoom.origin(mob).add(mob.getLookAngle());
	}

	@Override
	public boolean directional() {
		return true;
	}

	@Override
	public ConductorTargeting.TargetKind targetKind() {
		return ConductorTargeting.TargetKind.POSITION;
	}

	@Override
	public ConductorTargeting.RangeMetric rangeMetric() {
		return ConductorTargeting.RangeMetric.HORIZONTAL;
	}

	@Override
	public double maximumRange(Mob mob) {
		return ConductorRules.WARDEN_SONIC_BOOM_HORIZONTAL_RANGE;
	}

	@Override
	public boolean canTarget(Mob mob, LivingEntity target) {
		return mob instanceof Warden warden && warden.canTargetEntity(target);
	}

	@Override
	public boolean isWithinRange(Mob mob, Vec3 position) {
		Vec3 offset = position.subtract(mob.position());
		return offset.horizontalDistanceSqr()
				<= ConductorRules.WARDEN_SONIC_BOOM_HORIZONTAL_RANGE
				* ConductorRules.WARDEN_SONIC_BOOM_HORIZONTAL_RANGE
				&& Math.abs(offset.y) <= ConductorRules.WARDEN_SONIC_BOOM_VERTICAL_RANGE;
	}

	@Override
	public Vec3 targetPosition(Mob mob, LivingEntity target, Vec3 position) {
		return target.position();
	}

	@Override
	public ConductorTargeting.PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
		return new ConductorTargeting.PreviewGeometry(List.of(), List.of(new ConductorTargeting.PreviewBeam(
				ConductorSonicBoom.origin(mob), ConductorSonicBoom.endpoint(mob, requestedPosition),
				ConductorRules.WARDEN_SONIC_BOOM_PREVIEW_HALF_WIDTH)));
	}

	@Override
	public Vec3 correctPosition(Mob mob, Vec3 position) {
		return ConductorSonicBoom.endpoint(mob, position);
	}

	@Override
	public int cooldownTicks(Mob mob) {
		if (!(mob instanceof Warden warden)) {
			return 0;
		}
		long cooldown = warden.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_COOLDOWN)
				? warden.getBrain().getTimeUntilExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN) : 0L;
		long active = warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
				? warden.getBrain().getTimeUntilExpiry(MemoryModuleType.ATTACK_COOLING_DOWN) : 0L;
		return (int) Math.min(Integer.MAX_VALUE, Math.max(cooldown, active));
	}

	@Override
	public int totalCooldownTicks(Mob mob) {
		return SonicBoom.COOLDOWN;
	}

	@Override
	public Display display(Mob mob) {
		return new Display(DamageKind.FIXED, ConductorRules.WARDEN_SONIC_BOOM_DAMAGE, AbilityKind.ATTACK);
	}

	@Override
	public boolean cast(Mob mob, @Nullable Entity target, @Nullable Vec3 position) {
		return mob instanceof Warden warden && position != null && ConductorSonicBoom.start(warden, position);
	}
}
