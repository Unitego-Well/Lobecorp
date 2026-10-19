package org.unitego.lobecorp.util.entity.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.world.entity.combat.CombatData;
import org.unitego.lobecorp.world.entity.combat.CombatPolicy;
import org.unitego.lobecorp.world.entity.combat.ICombatant;

import java.util.UUID;
import java.util.function.Predicate;

// 世界由实体持有，查询入口不负责关闭它。
@SuppressWarnings("resource")
public class CombatUtil {
	/// 延续指挥家原有的群体仇恨共享距离，单位为方块。
	public static final double AGGRO_SHARE_RANGE = 16.0D;

	public static boolean isIndiscriminate(Entity entity) {
		return entity instanceof ICombatant combatant && combatant.getCombatPolicy() == CombatPolicy.INDISCRIMINATE;
	}

	public static boolean acceptsControl(Entity entity) {
		return !(entity instanceof ICombatant combatant) || combatant.acceptsConductorControl();
	}

	public static boolean participates(Entity entity) {
		if (entity instanceof ICombatant) {
			return true;
		}

		CombatData data = data(entity);
		return data != null && data.affiliation(entity.getUUID()) != null;
	}

	private static @Nullable CombatData data(Entity entity) {
		return entity.level() instanceof ServerLevel level ? CombatData.get(level.getServer()) : null;
	}

	private static Entity rootOwner(Entity entity) {
		if (entity instanceof OwnableEntity ownable && ownable.getRootOwner() instanceof LivingEntity owner) {
			return owner;
		}

		return entity;
	}

	/// 查询归属事实，不因狂暴或控制资格改变；不调用原版 isAlliedTo，避免桥接递归。
	public static boolean isAllied(Entity first, Entity second) {
		if (first == second) {
			return true;
		}

		CombatData data = data(first);
		if (data != null && data.allied(first.getUUID(), second.getUUID())) {
			return true;
		}

		if (first instanceof ICombatant left && left.isNaturalCombatAlly(second)
				|| second instanceof ICombatant right && right.isNaturalCombatAlly(first)) {
			return true;
		}

		Entity left = rootOwner(first);
		Entity right = rootOwner(second);
		return left == right || left.getTeam() != null && left.getTeam().isAlliedTo(right.getTeam());
	}

	public static boolean canTarget(Entity attacker, Entity target) {
		return canTarget(attacker, target, isAllied(attacker, target));
	}

	/// 客户端可传入目录中的阵营快照，服务端仍独立查询权威归属。
	public static boolean canTarget(Entity attacker, Entity target, boolean allied) {
		if (target == attacker || target.level() != attacker.level() || !target.isAlive()
				|| !target.isAttackable() || target.isSpectator()
				|| target instanceof LivingEntity living && !living.attackable()
				|| target instanceof Player player && player.isCreative()) {
			return false;
		}

		return isIndiscriminate(attacker) || isIndiscriminate(target)
				|| !allied && !attacker.isAlliedTo(target);
	}

	public static boolean wantsTarget(Mob attacker, Entity target) {
		if (!canTarget(attacker, target)) {
			return false;
		}
		if (isIndiscriminate(attacker)) {
			return true;
		}
		if (attacker instanceof ICombatant combatant) {
			return switch (combatant.getCombatPolicy()) {
				case HOSTILES_AND_ATTACKERS -> target instanceof Enemy || wasAttackedBy(attacker, target);
				case ALL_NON_ALLIES, INDISCRIMINATE -> true;
			};
		}

		return target == attacker.getLastHurtByMob();
	}

	public static boolean canHarm(LivingEntity attacker, LivingEntity target) {
		CombatData data = data(attacker);
		if (attacker == target) {
			return data == null || data.affiliation(attacker.getUUID()) == null;
		}
		if (isIndiscriminate(attacker) || isIndiscriminate(target)) {
			return true;
		}
		if (!participates(attacker) && !participates(target)) {
			return true;
		}
		if (data != null && data.allied(attacker.getUUID(), target.getUUID())
				|| attacker instanceof ICombatant left && left.isNaturalCombatAlly(target)
				|| target instanceof ICombatant right && right.isNaturalCombatAlly(attacker)) {
			return false;
		}

		return !attacker.isAlliedTo(target) || attacker.getTeam() != null && attacker.getTeam().isAllowFriendlyFire();
	}

	public static boolean isEnemy(Entity first, Entity second) {
		CombatData data = data(first);
		return canTarget(first, second) && (isIndiscriminate(first)
				|| data != null && data.enemies(first.getUUID(), second.getUUID()));
	}

	public static void recordAttack(LivingEntity victim, LivingEntity attacker) {
		if (!victim.level().isClientSide() && victim != attacker) {
			victim.getData(LcAttachmentTypes.COMBAT_MEMORY).recordAttacker(attacker.getUUID());
		}
	}

	private static boolean wasAttackedBy(LivingEntity entity, Entity attacker) {
		return entity.hasData(LcAttachmentTypes.COMBAT_MEMORY)
				&& entity.getData(LcAttachmentTypes.COMBAT_MEMORY).wasAttackedBy(attacker.getUUID());
	}

	public static void rememberSharedTarget(Mob mob, @Nullable LivingEntity target) {
		if (target == null && !mob.hasData(LcAttachmentTypes.COMBAT_MEMORY)) {
			return;
		}
		mob.getData(LcAttachmentTypes.COMBAT_MEMORY).setSharedTarget(target == null ? null : target.getUUID());
	}

	public static boolean isSharedTarget(Mob mob, LivingEntity target) {
		if (!mob.hasData(LcAttachmentTypes.COMBAT_MEMORY)) {
			return false;
		}

		UUID sharedTarget = mob.getData(LcAttachmentTypes.COMBAT_MEMORY).sharedTarget();
		return target.getUUID().equals(sharedTarget);
	}

	public static @Nullable LivingEntity findSharedTarget(Mob mob, Predicate<LivingEntity> acceptsTarget) {
		CombatData data = data(mob);
		if (data == null) {
			return null;
		}

		LivingEntity nearest = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (Mob ally : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(AGGRO_SHARE_RANGE),
				candidate -> candidate != mob && candidate.isAlive()
						&& mob.distanceToSqr(candidate) <= AGGRO_SHARE_RANGE * AGGRO_SHARE_RANGE
						&& data.sameGroup(mob.getUUID(), candidate.getUUID()))) {
			LivingEntity target = ally.getTarget();
			if ((target == null || !canTarget(mob, target) || !acceptsTarget.test(target))
					&& ally.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				target = ally.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			}
			if (target == null || !canTarget(mob, target) || !acceptsTarget.test(target)) {
				continue;
			}

			double distance = mob.distanceToSqr(target);
			if (distance < nearestDistance) {
				nearest = target;
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	public static void clearRuntime(LivingEntity entity) {
		entity.removeData(LcAttachmentTypes.COMBAT_MEMORY);
	}
}
