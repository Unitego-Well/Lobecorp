package org.unitego.lobecorp.conductor.control;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.hitbox.*;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.Comparator;

/// 指挥家声波的服务端前摇、命中框和生命周期控制。
public final class ConductorSonicBoom {
	private static final TypedDataKey<Vec3> DIRECTION = TypedDataKey.create();
	private static final HitboxTemplate HITBOX = new HitboxTemplate(new BoxSize(0.0, 0.0, 0.0),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof Warden warden) || !(context.target() instanceof LivingEntity target))
					return false;
				if (!target.hurtServer(context.level(), warden.damageSources().sonicBoom(warden),
						(float) ConductorRules.WARDEN_SONIC_BOOM_DAMAGE)) return false;
				Vec3 direction = context.instance().getData(DIRECTION);
				if (direction == null) return true;
				double resistance = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				target.push(direction.x * ConductorRules.WARDEN_SONIC_KNOCKBACK_HORIZONTAL * resistance,
						direction.y * ConductorRules.WARDEN_SONIC_KNOCKBACK_VERTICAL * resistance,
						direction.z * ConductorRules.WARDEN_SONIC_KNOCKBACK_HORIZONTAL * resistance);
				return true;
			});

	private ConductorSonicBoom() {
	}

	public static Vec3 origin(Mob mob) {
		return mob.position().add(mob.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, mob.getYRot()));
	}

	public static Vec3 endpoint(Mob mob, Vec3 requested) {
		Vec3 start = origin(mob);
		Vec3 direction = requested.subtract(start);
		if (requested.distanceToSqr(mob.position()) <= ConductorRules.FORMATION_DIRECTION_EPSILON
				|| direction.lengthSqr() <= ConductorRules.FORMATION_DIRECTION_EPSILON) direction = mob.getLookAngle();
		direction = direction.normalize();
		double horizontal = direction.horizontalDistance();
		double length = horizontal > ConductorRules.FORMATION_DIRECTION_EPSILON
				? ConductorRules.WARDEN_SONIC_BOOM_HORIZONTAL_RANGE / horizontal : Double.POSITIVE_INFINITY;
		if (Math.abs(direction.y) > ConductorRules.FORMATION_DIRECTION_EPSILON)
			length = Math.min(length, ConductorRules.WARDEN_SONIC_BOOM_VERTICAL_RANGE / Math.abs(direction.y));
		return start.add(direction.scale(length));
	}

	public static boolean active(Mob mob) {
		return mob instanceof Warden warden && ConductorUnitRuntime.get(warden).sonic != null;
	}

	public static boolean start(Warden mob, Vec3 position) {
		if (!(mob.level() instanceof ServerLevel level) || active(mob)) return false;
		Vec3 offset = endpoint(mob, position).subtract(origin(mob));
		mob.getBrain().stopAll(level, mob);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		ConductorUnitRuntime.get(mob).sonic = new Cast(offset.normalize(), offset.length(), level.getGameTime());
		mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, ConductorRules.WARDEN_SONIC_DURATION_TICKS);
		mob.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_DELAY, Unit.INSTANCE, ConductorRules.WARDEN_SONIC_WINDUP_TICKS);
		SonicBoom.setCooldown(mob, ConductorRules.WARDEN_SONIC_DURATION_TICKS + SonicBoom.COOLDOWN);
		level.broadcastEntityEvent(mob, ConductorRules.WARDEN_SONIC_EVENT);
		mob.playSound(SoundEvents.WARDEN_SONIC_CHARGE, ConductorRules.WARDEN_SONIC_VOLUME, ConductorRules.WARDEN_SONIC_PITCH);
		return true;
	}

	public static void cancel(Mob mob) {
		if (!(mob instanceof Warden warden) || ConductorUnitRuntime.get(warden).sonic == null) return;
		ConductorUnitRuntime.get(warden).sonic = null;
		warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
		warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN);
		SonicBoom.setCooldown(warden, SonicBoom.COOLDOWN);
	}

	public static void tick(Warden mob, ServerLevel level) {
		Cast cast = ConductorUnitRuntime.get(mob).sonic;
		if (cast == null) return;
		mob.getNavigation().stop();
		mob.getMoveControl().setWait();
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		Vec3 direction = cast.direction();
		float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		long elapsed = level.getGameTime() - cast.started();
		if (elapsed == ConductorRules.WARDEN_SONIC_WINDUP_TICKS) fire(mob, level, cast);
		if (elapsed >= ConductorRules.WARDEN_SONIC_DURATION_TICKS) cancel(mob);
	}

	private static void fire(Warden mob, ServerLevel level, Cast cast) {
		Vec3 start = origin(mob);
		Vec3 end = start.add(cast.direction().scale(cast.length()));
		double halfWidth = ConductorRules.WARDEN_SONIC_BOOM_PREVIEW_HALF_WIDTH;
		ConductorData data = ConductorData.get(level.getServer());

		HitboxInstance hitbox = HitboxManager.create(HITBOX, level, start.add(end).scale(0.5),
				HitboxManager.CURRENT_TICK_DURATION);
		hitbox.setSource(mob);
		hitbox.setSize(new BoxSize(halfWidth * 2.0, halfWidth * 2.0, cast.length()));
		hitbox.setRotation(new Vec3(-Math.asin(cast.direction().y) * Mth.RAD_TO_DEG,
				-Math.atan2(-cast.direction().x, cast.direction().z) * Mth.RAD_TO_DEG, 0.0));
		LivingEntity first = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(halfWidth),
						target -> target != mob && target.isAlive() && HitboxGeometry.intersects(hitbox, target.getBoundingBox()) && mob.canTargetEntity(target)
								&& !mob.isAlliedTo(target) && !data.allied(mob.getUUID(), target.getUUID())
								&& (target.getBoundingBox().inflate(halfWidth).contains(start)
								|| target.getBoundingBox().inflate(halfWidth).clip(start, end).isPresent()))
				.stream().min(Comparator.comparingDouble(target -> target.getBoundingBox().inflate(halfWidth)
						.contains(start) ? 0.0 : target.getBoundingBox().inflate(halfWidth).clip(start, end)
						.orElse(end).distanceToSqr(start))).orElse(null);
		hitbox.appendTargetFilter(target -> target == first);
		hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
		hitbox.setData(DIRECTION, cast.direction());
		hitbox.activate();
		int steps = Mth.floor(cast.length()) + ConductorRules.WARDEN_SONIC_PARTICLE_EXTENSION;
		for (int step = 1; step < steps; step++) {
			Vec3 point = start.add(cast.direction().scale(step));
			level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		mob.playSound(SoundEvents.WARDEN_SONIC_BOOM, ConductorRules.WARDEN_SONIC_VOLUME, ConductorRules.WARDEN_SONIC_PITCH);
	}

	protected record Cast(Vec3 direction, double length, long started) {
	}
}
