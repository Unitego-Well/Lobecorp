package org.unitego.lobecorp.conductor.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.ability.ConductorTargeting;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.WardenSonicBoomAbility;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.data.ConductorUnitData;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.world.entity.skill.EntitySkillAccess;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillCastResult;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.UUID;

/// 指挥单位控制器，协调持续指令、技能施放和原版 AI 状态。
public class ConductorController {
	private static final ThreadLocal<Deque<Mob>> BRAIN_OWNERS = ThreadLocal.withInitial(ArrayDeque::new);
	private static final ThreadLocal<Deque<ExplicitSkillCast>> EXPLICIT_SKILL_CASTS = ThreadLocal.withInitial(ArrayDeque::new);

	private ConductorController() {
	}

	public static void tick(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level))
			return;
		ConductorData data = ConductorData.get(level.getServer());
		if (data.unit(mob.getUUID()) == null)
			return;
		if (!mob.isAlive()) {
			stop(mob);
			return;
		}
		ConductorUtil.tick(mob);
	}

	public static void tickControlled(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(mob.getUUID());
		if (unit == null) {
			return;
		}
		if (unit.order() != ConductorData.OrderType.ATTACK_POINT) {
			ConductorPointAttack.stop(mob, unit.order() == ConductorData.OrderType.ATTACK);
		}
		mob.setPersistenceRequired();
		if (!mob.isAlive()) {
			stop(mob);
			return;
		}
		PendingSkillCast pending = ConductorUnitRuntime.get(mob).pendingSkill;
		if (pending != null) {
			clearWalkAndLookTargets(mob);
			stopMovement(mob);
			if (pending.target() != null && (!pending.target().isAlive() || pending.target().isRemoved())) {
				ConductorUnitRuntime.get(mob).pendingSkill = null;
			} else {
				cast(mob, pending.skill(), pending.target(), pending.position());
			}
			return;
		}
		if (mob instanceof Warden warden) {
			for (String member : data.units().keySet()) {
				UUID memberId = UUID.fromString(member);
				if (data.allied(mob.getUUID(), memberId) && level.getEntity(memberId) instanceof LivingEntity ally) {
					warden.clearAnger(ally);
				}
			}
		}
		if (unit.origin() == null) {
			data.update(mob.getUUID(), state -> state.origin = mob.position());
			unit = data.unit(mob.getUUID());
		}

		if (unit.order() == ConductorData.OrderType.MOVE || unit.order() == ConductorData.OrderType.RETURN) {
			executeMoveOrder(mob, data, unit);
			ConductorData.Unit current = data.unit(mob.getUUID());
			if (current != null && (current.order() == ConductorData.OrderType.MOVE
					|| current.order() == ConductorData.OrderType.RETURN)) {
				avoidCollidableEntities(level, mob, null);
			}
			return;
		}
		if (unit.order() == ConductorData.OrderType.ATTACK_POINT) {
			clearTarget(mob);
			clearWalkAndLookTargets(mob);
			stopMovement(mob);
			mob.getLookControl().setLookAt(unit.x(), unit.y(), unit.z());
			ConductorPointAttack.tick(level, mob, new Vec3(unit.x(), unit.y(), unit.z()));
			return;
		}
		if (unit.order() == ConductorData.OrderType.ATTACK) {
			LivingEntity target = findCommandTarget(level, mob, unit);
			if (target == null) {
				clearTarget(mob);
				clearWalkAndLookTargets(mob);
				stopMovement(mob);
				finishAttackOrder(mob, data, unit);
				return;
			}
			if (mob instanceof Warden warden
					&& unit.commandedSkill().equals(WardenSonicBoomAbility.ID.toString())) {
				warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
			} else {
				setTarget(mob, target);
			}
			if (!unit.commandedSkill().isEmpty()) {
				ConductorAbility ability;
				try {
					ability = ConductorUtil.ability(mob, Identifier.parse(unit.commandedSkill()));
				} catch (IllegalArgumentException exception) {
					ability = null;
				}
				if (ability != null) {
					if ((ability.targetKind() == ConductorTargeting.TargetKind.ENTITY
							|| ability.targetKind() == ConductorTargeting.TargetKind.EITHER)
							&& ability.isAvailable(mob) && ability.canTarget(mob, target)
							&& ability.isWithinRange(mob, ability.targetPosition(mob, target, target.position()))
							&& ability.cooldownTicks(mob) == 0) {
						if (ConductorUtil.cast(mob, ability.id(), target, null)
								&& ability instanceof EntitySkillConductorAbility) {
							clearTarget(mob);
							clearWalkAndLookTargets(mob);
							finishAttackOrder(mob, data, unit);
							stopMovement(mob);
							return;
						}
					}
				}
			}
			avoidCollidableEntities(level, mob, target);
			return;
		}

		if (unit.combatBehavior() == ConductorData.CombatBehavior.PASSIVE) {
			if (mob instanceof Warden warden && (hasManualSonicBoomRequest(warden)
					|| isSonicBoomRunning(warden))) {
				return;
			}
			clearTarget(mob);
			clearWalkAndLookTargets(mob);
			stopMovement(mob);
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null && mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		}
		if (target != null && !isAllowedTarget(mob, target, data, unit)) {
			clearTarget(mob);
			target = null;
		}
		if (target == null) {
			LivingEntity attacker = mob.getLastHurtByMob();
			if (attacker != null && attacker.level() == level && isAllowedTarget(mob, attacker, data, unit))
				target = attacker;
		}
		if (target == null) {
			target = findTeamTarget(level, mob, data, unit);
			if (target != null)
				ConductorUnitRuntime.get(mob).sharedTarget = target.getUUID();
		}
		if (target == null) {
			target = findTarget(level, mob, data, unit);
		}
		if (target != null) {
			setTarget(mob, target);
		}
		if (isOutsideBehaviorRadius(mob, unit)) {
			data.update(mob.getUUID(), ConductorController::returnToOrigin);
			ConductorData.Unit returning = data.unit(mob.getUUID());
			clearTarget(mob);
			executeMoveOrder(mob, data, returning);
			return;
		}
		if ((unit.behaviorState() == ConductorData.BehaviorState.GUARD
				|| unit.behaviorState() == ConductorData.BehaviorState.STANDBY) && target == null) {
			clearWalkAndLookTargets(mob);
			stopMovement(mob);
			return;
		}
		avoidCollidableEntities(level, mob, target);
	}

	public static void resetSharedTarget(Mob mob) {
		ConductorUnitRuntime.get(mob).sharedTarget = null;
	}

	public static void stop(Mob mob) {
		ConductorUtil.stop(mob);
	}

	public static void stopControlled(Mob mob) {
		stopControlled(mob, true);
	}

	/// 手动施放前仅清理移动与指令；技能接招由状态机在新施放成功后处理。
	public static void prepareForSkillCast(Mob mob) {
		stopControlled(mob, false);
	}

	private static void stopControlled(Mob mob, boolean cancelSkills) {
		ConductorUnitRuntime.get(mob).pendingSkill = null;
		resetSharedTarget(mob);
		ConductorMovement.clear(mob);
		ConductorWork.cancel(mob);
		ConductorSonicBoom.cancel(mob);
		ConductorPointAttack.stop(mob);
		ConductorUnitRuntime.get(mob).manualSonic = null;
		if (mob instanceof Warden warden) {
			LivingEntity target = warden.getTarget();
			if (target != null)
				warden.clearAnger(target);
			if (warden.level() instanceof ServerLevel level)
				warden.getBrain().stopAll(level, warden);
		}
		clearTarget(mob);
		clearWalkAndLookTargets(mob);
		stopMovement(mob);
		if (cancelSkills)
			EntitySkillUtil.forceCancelSkill(mob);
	}

	private static void stopMovement(Mob mob) {
		mob.getNavigation().stop();
		mob.getMoveControl().setWait();
		ConductorUtil.movementStopped(mob);
	}

	private static void clearWalkAndLookTargets(Mob mob) {
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
	}

	private static void clearTarget(Mob mob) {
		resetSharedTarget(mob);
		mob.setTarget(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		if (mob instanceof Warden) {
			mob.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
		}
	}

	private static void setTarget(Mob mob, LivingEntity target) {
		if (mob instanceof Warden warden) {
			if (warden.getTarget() != target) {
				warden.setAttackTarget(target);
			}
			int anger = warden.getAngerManagement().getActiveAnger(target);
			if (anger < AngerLevel.ANGRY.getMinimumAnger()) {
				warden.increaseAngerAt(target, AngerLevel.ANGRY.getMinimumAnger() - anger, false);
			}
			return;
		}
		mob.setTarget(target);
		mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
	}

	private static LivingEntity findTeamTarget(ServerLevel level, Mob mob, ConductorData data, ConductorData.Unit unit) {
		if (unit.order() != ConductorData.OrderType.NONE || unit.combatBehavior() == ConductorData.CombatBehavior.PASSIVE) {
			return null;
		}
		double range = ConductorRules.TEAM_AGGRO_SHARE_RANGE;
		LivingEntity nearest = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (Mob ally : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(range),
				candidate -> candidate != mob && candidate.isAlive() && mob.distanceToSqr(candidate) <= range * range
						&& data.allied(mob.getUUID(), candidate.getUUID()))) {
			LivingEntity target = ally.getTarget();
			if ((target == null || target.level() != level || !isWithinBehaviorRange(mob, target, data, unit))
					&& ally.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				target = ally.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			}
			if (target == null || target.level() != level || !isWithinBehaviorRange(mob, target, data, unit))
				continue;
			double distance = mob.distanceToSqr(target);
			if (distance < nearestDistance) {
				nearest = target;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static LivingEntity findTarget(ServerLevel level, Mob mob, ConductorData data, ConductorData.Unit unit) {
		if (unit.combatBehavior() != ConductorData.CombatBehavior.ACTIVE
				|| unit.order() != ConductorData.OrderType.NONE) {
			return null;
		}
		double followRange = followRange(mob);
		return level.getEntitiesOfClass(LivingEntity.class,
						mob.getBoundingBox().inflate(followRange),
						target -> isAllowedTarget(mob, target, data, unit)
								&& data.enemies(mob.getUUID(), target.getUUID())
								&& mob.distanceToSqr(target) <= followRange * followRange)
				.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
	}

	private static LivingEntity findCommandTarget(ServerLevel level, Mob mob, ConductorData.Unit unit) {
		if (unit.target().isEmpty()) {
			return null;
		}
		try {
			Entity entity = level.getEntity(UUID.fromString(unit.target()));
			return entity instanceof LivingEntity target && target.isAlive() && target != mob
					&& !ConductorData.get(level.getServer()).allied(mob.getUUID(), target.getUUID()) ? target : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static boolean isAllowedTarget(Mob mob, LivingEntity target, ConductorData data, ConductorData.Unit unit) {
		if (target == mob || !target.isAlive() || data.allied(mob.getUUID(), target.getUUID())) {
			return false;
		}
		if (unit.order() == ConductorData.OrderType.ATTACK
				&& unit.target().equals(target.getUUID().toString())) {
			return true;
		}
		return isWithinBehaviorRange(mob, target, data, unit)
				&& (unit.combatBehavior() == ConductorData.CombatBehavior.ACTIVE
				|| target == mob.getLastHurtByMob() || target.getUUID().equals(ConductorUnitRuntime.get(mob).sharedTarget));
	}

	private static boolean isWithinBehaviorRange(Mob mob, LivingEntity target, ConductorData data, ConductorData.Unit unit) {
		if (target == mob || !target.isAlive() || target.level() != mob.level() || data.allied(mob.getUUID(), target.getUUID())
				|| unit.combatBehavior() == ConductorData.CombatBehavior.PASSIVE
				|| unit.order() != ConductorData.OrderType.NONE
				|| mob.distanceToSqr(target) > followRange(mob) * followRange(mob)) {
			return false;
		}
		if (unit.behaviorState() == ConductorData.BehaviorState.STANDBY)
			return true;
		ConductorData.Position origin = unit.origin();
		return origin != null && target.position().distanceToSqr(origin.vector()) <= behaviorRadius(unit) * behaviorRadius(unit);
	}

	public static boolean allowsTargetChange(Mob mob, LivingEntity target) {
		if (ConductorSonicBoom.active(mob))
			return false;
		if (!(mob.level() instanceof ServerLevel level)) {
			return true;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(mob.getUUID());
		return unit == null || isAllowedTarget(mob, target, data, unit);
	}

	public static boolean isControlled(Mob mob) {
		return mob.level() instanceof ServerLevel level
				&& ConductorData.get(level.getServer()).unit(mob.getUUID()) != null;
	}

	public static boolean isCommandedTarget(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(mob.getUUID());
		return unit != null && unit.order() == ConductorData.OrderType.ATTACK
				&& unit.target().equals(target.getUUID().toString())
				&& isAllowedTarget(mob, target, data, unit);
	}

	private static double followRange(Mob mob) {
		return mob.getAttribute(Attributes.FOLLOW_RANGE) == null
				? ConductorRules.TARGET_SEARCH_RANGE
				: mob.getAttributeValue(Attributes.FOLLOW_RANGE);
	}

	private static double behaviorRadius(ConductorData.Unit unit) {
		return switch (unit.behaviorState()) {
			case PATROL -> ConductorRules.PATROL_RADIUS;
			case GUARD -> ConductorRules.GUARD_RADIUS;
			case IDLE, STANDBY -> 0.0D;
		};
	}

	private static boolean isOutsideBehaviorRadius(Mob mob, ConductorData.Unit unit) {
		if (unit.combatBehavior() == ConductorData.CombatBehavior.PASSIVE || unit.origin() == null
				|| unit.behaviorState() == ConductorData.BehaviorState.STANDBY) {
			return false;
		}
		return mob.position().distanceToSqr(unit.origin().vector())
				> behaviorRadius(unit) * behaviorRadius(unit);
	}

	private static void returnToOrigin(ConductorUnitData state) {
		state.command(state.origin == null ? ConductorData.OrderType.NONE : ConductorData.OrderType.RETURN,
				null, state.origin == null ? state.destination : state.origin);
	}

	private static void finishAttackOrder(Mob mob, ConductorData data, ConductorData.Unit unit) {
		data.update(mob.getUUID(), state -> {
			if (isOutsideBehaviorRadius(mob, unit))
				returnToOrigin(state);
			else
				state.command(ConductorData.OrderType.NONE, null, mob.position());
		});
	}

	private static void executeMoveOrder(Mob mob, ConductorData data, ConductorData.Unit unit) {
		clearTarget(mob);
		clearWalkAndLookTargets(mob);
		Vec3 destination = new Vec3(unit.x(), unit.y(), unit.z());
		if (ConductorUtil.get(mob) == null)
			return;
		ConductorUtil.prepareMovement(mob);
		if (ConductorMovement.arrived(mob, destination)) {
			ConductorMovement.clear(mob);
			stopMovement(mob);
			ConductorUtil.movementArrived(mob);
			data.update(mob.getUUID(), state -> {
				state.command(ConductorData.OrderType.NONE, null, destination);
				state.moveResult = ConductorData.MoveResult.ARRIVED;
				if (unit.order() == ConductorData.OrderType.MOVE)
					state.origin = destination;
			});
			return;
		}
		ConductorUtil.advanceMovement(mob, data, unit);
	}

	public static boolean allowNavigation(Mob mob, Vec3 destination) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return true;
		}
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(mob.getUUID());
		if (unit == null) {
			return true;
		}
		return switch (unit.order()) {
			case MOVE, RETURN -> destination.distanceToSqr(new Vec3(unit.x(), unit.y(), unit.z()))
					<= ConductorRules.FINAL_APPROACH_DISTANCE * ConductorRules.FINAL_APPROACH_DISTANCE;
			case CLEANUP, REASSEMBLE -> true;
			case ATTACK -> {
				LivingEntity target = findCommandTarget(level, mob, unit);
				yield target != null;
			}
			case ATTACK_POINT -> false;
			case NONE -> {
				if (unit.combatBehavior() == ConductorData.CombatBehavior.PASSIVE || unit.origin() == null) {
					yield false;
				}
				if (unit.behaviorState() == ConductorData.BehaviorState.GUARD
						&& (mob.getTarget() == null
						|| !isAllowedTarget(mob, mob.getTarget(), ConductorData.get(level.getServer()), unit))) {
					yield false;
				}
				if (unit.behaviorState() == ConductorData.BehaviorState.STANDBY) {
					LivingEntity target = mob.getTarget();
					if (target == null)
						target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
					yield target != null && isAllowedTarget(mob, target, ConductorData.get(level.getServer()), unit);
				}
				double radius = behaviorRadius(unit);
				yield destination.distanceToSqr(unit.origin().vector()) <= radius * radius;
			}
		};
	}

	public static @Nullable Vec3 commandedDragonDestination(EnderDragon dragon) {
		if (!(dragon.level() instanceof ServerLevel level)) {
			return null;
		}
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(dragon.getUUID());
		return unit != null && (unit.order() == ConductorData.OrderType.MOVE
				|| unit.order() == ConductorData.OrderType.RETURN)
				? ConductorMovement.navigationDestination(dragon, new Vec3(unit.x(), unit.y(), unit.z())) : null;
	}

	private static void avoidCollidableEntities(ServerLevel level, Mob mob, LivingEntity target) {
		if (mob.getNavigation().isDone() && !mob.getMoveControl().hasWanted())
			return;
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(mob.getUUID());
		double attenuation = 1.0;
		if (unit != null && (unit.order() == ConductorData.OrderType.MOVE || unit.order() == ConductorData.OrderType.RETURN)) {
			double distance = mob.position().distanceTo(new Vec3(unit.x(), unit.y(), unit.z()));
			attenuation = Math.min(1.0, distance / ConductorRules.FINAL_APPROACH_DISTANCE);
		}
		Vec3 avoidance = Vec3.ZERO;
		AABB bounds = mob.getBoundingBox().inflate(ConductorRules.ENTITY_AVOIDANCE_MARGIN);
		for (Entity entity : level.getEntities(mob, bounds, Entity::isPushable)) {
			if (entity == target || mob.getUUID().compareTo(entity.getUUID()) < 0)
				continue;
			Vec3 separation = mob.position().subtract(entity.position()).multiply(1.0, 0.0, 1.0);
			if (separation.lengthSqr() == 0.0)
				separation = new Vec3(1.0, 0.0, 0.0);
			avoidance = avoidance.add(separation.normalize());
		}
		if (avoidance.lengthSqr() == 0.0)
			return;
		Vec3 adjustment = avoidance.normalize().scale(ConductorRules.ENTITY_AVOIDANCE_STRENGTH * attenuation);
		if (!ConductorMovement.safeApproach(mob, mob.position().add(adjustment)))
			return;
		Vec3 velocity = mob.getDeltaMovement();
		double speed = velocity.horizontalDistance();
		Vec3 horizontal = velocity.multiply(1.0, 0.0, 1.0).add(adjustment);
		if (horizontal.lengthSqr() > 0.0)
			horizontal = horizontal.normalize().scale(speed);
		mob.setDeltaMovement(horizontal.x, velocity.y, horizontal.z);
	}

	public static void enterBrainTick(LivingEntity entity) {
		if (entity instanceof Mob mob) {
			BRAIN_OWNERS.get().push(mob);
		}
	}

	public static void exitBrainTick(LivingEntity entity) {
		if (entity instanceof Mob) {
			Deque<Mob> owners = BRAIN_OWNERS.get();
			if (!owners.isEmpty()) {
				owners.pop();
			}
			if (owners.isEmpty()) {
				BRAIN_OWNERS.remove();
			}
		}
	}

	public static boolean shouldBlockBrainTarget(LivingEntity target) {
		Deque<Mob> owners = BRAIN_OWNERS.get();
		if (owners.isEmpty()) {
			BRAIN_OWNERS.remove();
			return false;
		}
		Mob owner = owners.peek();
		if (ConductorSonicBoom.active(owner))
			return true;
		if (owner instanceof Warden warden && hasManualSonicBoomRequest(warden)) {
			ManualSonicBoomRequest request = ConductorUnitRuntime.get(warden).manualSonic;
			return request == null || !request.target().equals(target.getUUID());
		}
		if (!(owner.level() instanceof ServerLevel level)) {
			return false;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(owner.getUUID());
		return unit != null && !isAllowedTarget(owner, target, data, unit);
	}

	public static boolean allowWardenSonicBoom(Warden warden, LivingEntity target) {
		if (hasManualSonicBoomRequest(warden)) {
			return true;
		}
		if (!(warden.level() instanceof ServerLevel level)) {
			return true;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(warden.getUUID());
		return unit == null || (unit.attackState() == ConductorData.AttackState.AUTO
				&& isAllowedTarget(warden, target, data, unit));
	}

	public static void consumeManualWardenSonicBoomRequest(Warden warden) {
		ConductorUnitRuntime.get(warden).manualSonic = null;
	}

	public static void completeManualWardenSonicBoom(Warden warden) {
		if (!(warden.level() instanceof ServerLevel level)) {
			return;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(warden.getUUID());
		if (unit == null || !unit.commandedSkill().equals(WardenSonicBoomAbility.ID.toString())) {
			return;
		}
		clearTarget(warden);
		clearWalkAndLookTargets(warden);
		finishAttackOrder(warden, data, unit);
		stopMovement(warden);
	}

	public static boolean manualWardenSonicBoom(Warden warden, LivingEntity target) {
		if (!(warden.level() instanceof ServerLevel level)) {
			return false;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(warden.getUUID());
		if (unit == null || !isValidManualTarget(warden, target, data)) {
			return false;
		}
		if (warden.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_COOLDOWN)
				|| warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) {
			return false;
		}
		ConductorUnitRuntime.get(warden).manualSonic = new ManualSonicBoomRequest(target.getUUID(), level.getGameTime() + ConductorRules.MANUAL_ABILITY_REQUEST_TICKS);
		warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		return true;
	}

	private static boolean isValidManualTarget(Mob mob, LivingEntity target, ConductorData data) {
		return target != mob && target.isAlive() && !data.allied(mob.getUUID(), target.getUUID())
				&& (!(mob instanceof Warden warden) || warden.canTargetEntity(target));
	}

	private static boolean hasManualSonicBoomRequest(Warden warden) {
		ManualSonicBoomRequest request = ConductorUnitRuntime.get(warden).manualSonic;
		if (request == null) {
			return false;
		}
		if (!(warden.level() instanceof ServerLevel level) || level.getGameTime() > request.expiresAt()) {
			ConductorUnitRuntime.get(warden).manualSonic = null;
			return false;
		}
		LivingEntity target = warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		return target != null && request.target().equals(target.getUUID());
	}

	private static boolean isSonicBoomRunning(Warden warden) {
		return warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN);
	}

	public static boolean cast(Mob mob, IEntitySkill<?> skill) {
		return cast(mob, skill, null, null);
	}

	public static boolean cast(Mob mob, IEntitySkill<?> skill, Entity target, Vec3 targetPosition) {
		EntitySkillAccess access = EntitySkillAccess.get(mob);
		if (access == null || !access.supports(skill)) {
			return false;
		}
		if (mob instanceof TheQueenOfHatred queen)
			queen.cancelConductorSitting();
		Deque<ExplicitSkillCast> casts = EXPLICIT_SKILL_CASTS.get();
		casts.push(new ExplicitSkillCast(mob, skill));
		try {
			EntitySkillCastResult<?> result = access.cast(skill, target, targetPosition);
			if (result.started() && result.runtime() != null && skill instanceof LaserSkill) {
				LaserSkill.enableManualControl(result.runtime());
			}
			boolean aiming = result.status() == EntitySkillCastResult.Status.AIMING;
			ConductorUnitRuntime.get(mob).pendingSkill = aiming ? new PendingSkillCast(skill, target, targetPosition) : null;
			return result.started() || aiming;
		} finally {
			casts.pop();
			if (casts.isEmpty()) {
				EXPLICIT_SKILL_CASTS.remove();
			}
		}
	}

	/// 手动持续激光的鼠标更新同时覆盖尚在瞄准的请求和已经开始的运行实例。
	public static void updateLaserAim(Mob mob, IEntitySkill<?> skill, LivingEntity target, Vec3 position, boolean following) {
		PendingSkillCast pending = ConductorUnitRuntime.get(mob).pendingSkill;
		if (pending != null && pending.skill() == skill && skill instanceof LaserSkill) {
			ConductorUnitRuntime.get(mob).pendingSkill = following ? new PendingSkillCast(skill, target, position)
					: new PendingSkillCast(skill, null, mob.position().add(mob.getLookAngle().scale(LaserSkill.RANGE)));
		}
		LaserSkill.updateManualAim(mob, target, position, following);
	}

	/// 退出手动后清除鼠标落点；尚未开始的施放也恢复生物当前目标。
	public static void releaseLaserControl(Mob mob, IEntitySkill<?> skill) {
		PendingSkillCast pending = ConductorUnitRuntime.get(mob).pendingSkill;
		if (pending != null && pending.skill() == skill && skill instanceof LaserSkill) {
			LivingEntity target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			ConductorUnitRuntime.get(mob).pendingSkill = new PendingSkillCast(skill, target,
					target != null ? target.getBoundingBox().getCenter() : mob.position().add(mob.getLookAngle().scale(LaserSkill.RANGE)));
		}
		LaserSkill.releaseManualControl(mob);
	}

	public static boolean isExplicitSkillCast(Mob mob, IEntitySkill<?> skill) {
		Deque<ExplicitSkillCast> casts = EXPLICIT_SKILL_CASTS.get();
		if (casts.isEmpty()) {
			EXPLICIT_SKILL_CASTS.remove();
			return false;
		}
		ExplicitSkillCast cast = casts.peek();
		return cast.mob() == mob && cast.skill() == skill;
	}

	/// 手动技能尚在瞄准时，阻止自动技能抢占其施放位置。
	public static boolean hasPendingSkillCast(Mob mob) {
		return ConductorUnitRuntime.get(mob).pendingSkill != null;
	}

	protected record ManualSonicBoomRequest(UUID target, long expiresAt) {
	}

	private record ExplicitSkillCast(Mob mob, IEntitySkill<?> skill) {
	}

	protected record PendingSkillCast(IEntitySkill<?> skill, @Nullable Entity target, @Nullable Vec3 position) {
	}

}
