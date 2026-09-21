package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.ai.control.TheQueenOfHatredCombatIntent;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredAttackMode;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredBlinkSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredTeleportSkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

import java.util.ArrayList;
import java.util.List;

/// 根据目标距离、敌群密度和生存状态，为憎恶皇后维持短期技能计划。
public final class TheQueenOfHatredAttackPlanner {
	/// 一个技能计划允许保留的最大 tick 数。
	private static final int PLAN_TIMEOUT_TICKS = 5;
	/// 紧急重新定位失败后的重试间隔，单位为 tick。
	private static final int EMERGENCY_REPOSITION_RETRY_TICKS = 20;
	/// 攻击规划收集敌人的范围，单位为格。
	private static final double ENEMY_ANALYSIS_RANGE = 32.0;
	/// 判断近身威胁时使用的敌人半径，单位为格。
	private static final double RETREAT_ENEMY_RADIUS = 6.0;
	/// 半径内达到该数量的敌人时视为需要防御。
	private static final int RETREAT_ENEMY_COUNT = 2;
	/// 低于该生命比例时允许规划防御撤退。
	private static final float RETREAT_HEALTH_RATIO = 0.5F;
	/// 密集目标权重统计半径，单位为格。
	private static final double DENSE_TARGET_RADIUS = 8.0;
	/// 技能目标已在有效距离内时的权重倍率。
	private static final double IN_RANGE_WEIGHT_MULTIPLIER = 4.0;
	/// 密集目标附近每个额外敌人增加的权重倍率。
	private static final double DENSE_TARGET_WEIGHT_PER_ENEMY = 0.75;
	/// 处于危险状态时防御技能的权重倍率。
	private static final double DEFENSIVE_WEIGHT_MULTIPLIER = 6.0;
	/// 触发位移技能所需的最短路径距离，单位为格。
	private static final double MINIMUM_REPOSITION_DISTANCE = 6.0;
	/// 瞬步能够覆盖的最大路径距离，单位为格。
	private static final double MAXIMUM_BLINK_PATH_DISTANCE = TheQueenOfHatredBlinkSkill.MAXIMUM_DISTANCE;
	/// 拉远时向敌群反方向请求的路径目标距离，单位为格。
	private static final double RETREAT_PATH_TARGET_DISTANCE = 32.0;
	/// 选择长距离传送撤退所需的最小水平距离，单位为格。
	private static final double MINIMUM_RETREAT_TELEPORT_DISTANCE = 16.0;
	/// 重新定位路径允许搜索的最大距离，单位为格。
	private static final double MAXIMUM_REPOSITION_PATH_DISTANCE = TheQueenOfHatredTeleportSkill.MAXIMUM_DISTANCE;
	/// 攻击规划敌人扫描的性能分析区段。

	private final TheQueenOfHatred queen;
	@Nullable
	private TheQueenOfHatredSkill plannedSkill;
	@Nullable
	private LivingEntity plannedTarget;
	private int remainingPlanTicks;
	private int emergencyRepositionRetryTicks;
	private long enemyAnalysisGameTime = Long.MIN_VALUE;
	private List<LivingEntity> cachedEnemies = List.of();

	public TheQueenOfHatredAttackPlanner(TheQueenOfHatred queen) {
		this.queen = queen;
	}

	@Nullable
	public TheQueenOfHatredCombatIntent plan() {
		return plan(true, false);
	}

	@Nullable
	public TheQueenOfHatredCombatIntent planAttackAfterDodge() {
		if (plannedSkill != null && (plannedSkill.attackMode() == TheQueenOfHatredAttackMode.DEFENSIVE_RETREAT
				|| plannedSkill.attackMode() == TheQueenOfHatredAttackMode.REPOSITION)) {
			clear();
		}
		return plan(false, true);
	}

	@Nullable
	private TheQueenOfHatredCombatIntent plan(boolean allowRetreat, boolean offensiveOnly) {
		if (emergencyRepositionRetryTicks > 0) {
			emergencyRepositionRetryTicks--;
		}
		if (EntitySkillManager.hasActiveSkills(queen)) {
			return null;
		}
		List<LivingEntity> enemies = nearbyEnemies();
		if (enemies.isEmpty()) {
			clear();
			return null;
		}
		boolean inDanger = isInDanger(enemies);
		if (allowRetreat && inDanger && emergencyRepositionRetryTicks == 0) {
			clear();
			emergencyRepositionRetryTicks = EMERGENCY_REPOSITION_RETRY_TICKS;
			TheQueenOfHatredCombatIntent reposition = planEmergencyReposition(enemies);
			if (reposition != null) {
				return reposition;
			}
		}
		LivingEntity currentTarget = queen.getAttackTarget();
		TheQueenOfHatredCombatIntent approach = currentTarget != null
				? planApproachReposition(currentTarget) : null;
		if (approach != null) {
			clear();
			return approach;
		}
		if (!isPlanValid()) {
			choosePlan(enemies, allowRetreat && inDanger, offensiveOnly);
		}
		if (plannedSkill == null || plannedTarget == null) {
			return null;
		}
		remainingPlanTicks--;
		double distance = plannedSkill.planningDistance(queen, plannedTarget);
		if (distance < plannedSkill.minimumPlanningRange()
				|| distance > plannedSkill.maximumPlanningRange()) {
			return null;
		}
		// 缓存计划可能在五 tick 的规划窗口内因状态、目标或资源变化而失效，提交前必须再次走完整施法校验。
		if (!EntitySkillManager.canCast(queen, plannedSkill)) {
			clear();
			return null;
		}
		TheQueenOfHatredCombatIntent intent = TheQueenOfHatredCombatIntent.cast(plannedSkill, plannedTarget);
		clear();
		return intent;
	}

	public TheQueenOfHatredAttackMode mode() {
		return plannedSkill == null ? TheQueenOfHatredAttackMode.MID_RANGE_STRAFE : plannedSkill.attackMode();
	}

	public double minimumRange() {
		return plannedSkill == null ? TheQueenOfHatred.HOSTILE_SAFE_DISTANCE
				: plannedSkill.minimumPlanningRange();
	}

	public double maximumRange() {
		return plannedSkill == null ? TheQueenOfHatred.DEFAULT_ORBIT_MAXIMUM_RANGE
				: plannedSkill.maximumPlanningRange();
	}

	@Nullable
	public TheQueenOfHatredCombatIntent planMeleeCounterattack(LivingEntity target) {
		List<WeightedPlan> candidates = new ArrayList<>();
		int nearbyEnemyCount = nearbyEnemies().size();
		for (TheQueenOfHatredSkill skill : skills()) {
			if (!skill.is(LcEntitySkillTags.MELEE)
					|| !EntitySkillManager.canCast(queen, skill)) {
				continue;
			}
			double distance = skill.planningDistance(queen, target);
			if (distance >= skill.minimumPlanningRange() && distance <= skill.maximumPlanningRange()) {
				candidates.add(new WeightedPlan(skill, target,
						skill.planningWeight(queen, target, nearbyEnemyCount)));
			}
		}
		WeightedPlan selected = weightedRandom(candidates);
		return selected == null ? null : TheQueenOfHatredCombatIntent.cast(selected.skill(), selected.target());
	}

	@Nullable
	public TheQueenOfHatredCombatIntent planThreatReposition(@Nullable LivingEntity threat,
			@Nullable Vec3 projectileVelocity, boolean teleport) {
		List<LivingEntity> enemies = new ArrayList<>(nearbyEnemies());
		if (threat != null && queen.isValidTarget(threat) && !enemies.contains(threat)) {
			enemies.add(threat);
		}
		Vec3 destination = enemies.isEmpty()
				? findProjectileDodgeDestination(projectileVelocity, teleport)
				: findRetreatDestination(enemies, teleport);
		if (destination == null) {
			return null;
		}
		LivingEntity nearestThreat = enemies.stream()
				.min((left, right) -> Double.compare(queen.distanceToSqr(left), queen.distanceToSqr(right)))
				.orElse(threat);
		if (nearestThreat == null) {
			nearestThreat = queen.getAttackTarget();
		}
		return TheQueenOfHatredCombatIntent.reposition(
				teleport ? TheQueenOfHatredSkills.TELEPORT.get() : TheQueenOfHatredSkills.BLINK.get(),
				nearestThreat, destination, true);
	}

	@Nullable
	private Vec3 findProjectileDodgeDestination(@Nullable Vec3 projectileVelocity, boolean teleport) {
		if (projectileVelocity == null || projectileVelocity.lengthSqr() <= Mth.square(Mth.EPSILON)) {
			return null;
		}
		Vec3 horizontalVelocity = projectileVelocity.multiply(1.0, 0.0, 1.0);
		Vec3 firstDirection;
		if (horizontalVelocity.lengthSqr() <= Mth.square(Mth.EPSILON)) {
			float yaw = queen.getYRot() * Mth.DEG_TO_RAD;
			firstDirection = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		} else {
			Vec3 normalized = horizontalVelocity.normalize();
			firstDirection = new Vec3(-normalized.z, 0.0, normalized.x);
		}
		Vec3 first = queen.findRepositionDestination(
				queen.position().add(firstDirection.scale(RETREAT_PATH_TARGET_DISTANCE)),
				MAXIMUM_REPOSITION_PATH_DISTANCE, !teleport);
		Vec3 second = queen.findRepositionDestination(
				queen.position().subtract(firstDirection.scale(RETREAT_PATH_TARGET_DISTANCE)),
				MAXIMUM_REPOSITION_PATH_DISTANCE, !teleport);
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		return queen.distanceToSqr(first) >= queen.distanceToSqr(second) ? first : second;
	}

	@Nullable
	private TheQueenOfHatredCombatIntent planApproachReposition(LivingEntity target) {
		Vec3 targetDirection = target.position().subtract(queen.position());
		Vec3 approachTarget = targetDirection.lengthSqr() <= Mth.square(Mth.EPSILON)
				? target.position()
				: target.position().subtract(targetDirection.normalize().scale(TheQueenOfHatred.HOSTILE_SAFE_DISTANCE));
		Vec3 destination = queen.findRepositionDestination(approachTarget,
				MAXIMUM_REPOSITION_PATH_DISTANCE, true);
		if (destination == null) {
			return null;
		}
		double distance = queen.position().distanceTo(destination);
		if (distance <= MINIMUM_REPOSITION_DISTANCE) {
			return null;
		}
		if (distance <= MAXIMUM_BLINK_PATH_DISTANCE) {
			return EntitySkillManager.isOnCooldown(queen, TheQueenOfHatredSkills.BLINK.get()) ? null
					: TheQueenOfHatredCombatIntent.reposition(TheQueenOfHatredSkills.BLINK.get(), target,
					destination, false);
		}
		destination = queen.findRepositionDestination(approachTarget, MAXIMUM_REPOSITION_PATH_DISTANCE, false);
		return destination == null ? null : TheQueenOfHatredCombatIntent.reposition(
				TheQueenOfHatredSkills.TELEPORT.get(), target, destination, false);
	}

	@Nullable
	private TheQueenOfHatredCombatIntent planEmergencyReposition(List<LivingEntity> enemies) {
		boolean blinkAvailable = !EntitySkillManager.isOnCooldown(queen, TheQueenOfHatredSkills.BLINK.get());
		Vec3 destination = findRetreatDestination(enemies, !blinkAvailable);
		if (destination == null && blinkAvailable) {
			blinkAvailable = false;
			destination = findRetreatDestination(enemies, true);
		}
		if (destination == null) {
			return null;
		}
		LivingEntity nearestThreat = enemies.stream()
				.min((left, right) -> Double.compare(queen.distanceToSqr(left), queen.distanceToSqr(right)))
				.orElse(enemies.getFirst());
		if (blinkAvailable) {
			return TheQueenOfHatredCombatIntent.reposition(TheQueenOfHatredSkills.BLINK.get(), nearestThreat,
					destination, true);
		}
		return TheQueenOfHatredCombatIntent.reposition(TheQueenOfHatredSkills.TELEPORT.get(), nearestThreat,
				destination, true);
	}

	@Nullable
	private Vec3 findRetreatDestination(List<LivingEntity> enemies, boolean teleport) {
		Vec3 enemyCenter = enemies.stream().map(LivingEntity::position).reduce(Vec3.ZERO, Vec3::add)
				.scale(1.0 / enemies.size());
		Vec3 away = queen.position().subtract(enemyCenter).multiply(1.0, 0.0, 1.0);
		if (away.horizontalDistanceSqr() <= 1.0E-4) {
			away = new Vec3(queen.getRandom().nextDouble() - 0.5, 0.0,
					queen.getRandom().nextDouble() - 0.5);
		}
		away = away.normalize();
		Vec3 destination = queen.findRepositionDestination(
				queen.position().add(away.scale(RETREAT_PATH_TARGET_DISTANCE)),
				MAXIMUM_REPOSITION_PATH_DISTANCE, !teleport);
		if (destination == null && !teleport) {
			return null;
		}
		if (destination == null) {
			destination = queen.findRepositionDestination(
					queen.position().add(away.scale(RETREAT_PATH_TARGET_DISTANCE)),
					MAXIMUM_REPOSITION_PATH_DISTANCE, false);
		}
		return destination != null
				&& destination.subtract(queen.position()).horizontalDistance() >= MINIMUM_RETREAT_TELEPORT_DISTANCE
				? destination : null;
	}

	private boolean isPlanValid() {
		return plannedSkill != null && plannedTarget != null && remainingPlanTicks > 0
				&& queen.isValidTarget(plannedTarget)
				&& EntitySkillManager.canCast(queen, plannedSkill);
	}

	private void choosePlan(List<LivingEntity> enemies, boolean defensiveOnly, boolean offensiveOnly) {
		List<WeightedPlan> candidates = new ArrayList<>();
		boolean damageReductionAvailable = defensiveOnly
				&& EntitySkillManager.canCast(queen, TheQueenOfHatredSkills.DAMAGE_REDUCTION.get());
		for (TheQueenOfHatredSkill skill : skills()) {
			if (!EntitySkillManager.canCast(queen, skill)
					|| skill.attackMode() == TheQueenOfHatredAttackMode.REPOSITION
					|| offensiveOnly && skill.attackMode() == TheQueenOfHatredAttackMode.DEFENSIVE_RETREAT
					|| queen.getRandom().nextFloat() >= skill.planningChance()) {
				continue;
			}
			if (defensiveOnly && skill.attackMode() != TheQueenOfHatredAttackMode.DEFENSIVE_RETREAT) {
				continue;
			}
			if (damageReductionAvailable && skill != TheQueenOfHatredSkills.DAMAGE_REDUCTION.get()) {
				continue;
			}
			LivingEntity target = selectTarget(skill, enemies);
			double distance = skill.planningDistance(queen, target);
			boolean inRange = distance >= skill.minimumPlanningRange()
					&& distance <= skill.maximumPlanningRange();
			double weight = skill.planningWeight(queen, target, enemies.size());
			if (inRange) {
				weight *= IN_RANGE_WEIGHT_MULTIPLIER;
			}
			if (skill.prefersDenseTarget()) {
				weight *= 1.0 + nearbyEnemyCount(target, enemies) * DENSE_TARGET_WEIGHT_PER_ENEMY;
			}
			if (skill.attackMode() == TheQueenOfHatredAttackMode.DEFENSIVE_RETREAT) {
				weight *= DEFENSIVE_WEIGHT_MULTIPLIER;
			}
			if (weight > 0.0) {
				candidates.add(new WeightedPlan(skill, target, weight));
			}
		}
		WeightedPlan selected = weightedRandom(candidates);
		if (selected == null) {
			clear();
			return;
		}
		plannedSkill = selected.skill();
		plannedTarget = selected.target();
		remainingPlanTicks = PLAN_TIMEOUT_TICKS;
	}

	private LivingEntity selectTarget(TheQueenOfHatredSkill skill, List<LivingEntity> enemies) {
		if (!skill.prefersDenseTarget()) {
			return skill.selectPlanningTarget(queen, enemies);
		}
		return enemies.stream()
				.max((left, right) -> Integer.compare(
						nearbyEnemyCount(left, enemies), nearbyEnemyCount(right, enemies)))
				.orElse(enemies.getFirst());
	}

	private int nearbyEnemyCount(LivingEntity center, List<LivingEntity> enemies) {
		double radiusSquared = DENSE_TARGET_RADIUS * DENSE_TARGET_RADIUS;
		return (int) enemies.stream().filter(enemy -> enemy.distanceToSqr(center) <= radiusSquared).count();
	}

	private WeightedPlan weightedRandom(List<WeightedPlan> candidates) {
		double totalWeight = candidates.stream().mapToDouble(WeightedPlan::weight).sum();
		if (totalWeight <= 0.0) {
			return null;
		}
		double selection = queen.getRandom().nextDouble() * totalWeight;
		for (WeightedPlan candidate : candidates) {
			selection -= candidate.weight();
			if (selection <= 0.0) {
				return candidate;
			}
		}
		return candidates.getLast();
	}

	private List<LivingEntity> nearbyEnemies() {
		long gameTime = queen.level().getGameTime();
		if (enemyAnalysisGameTime != Long.MIN_VALUE
				&& gameTime - enemyAnalysisGameTime < queen.combatEnemyAnalysisIntervalTicks()) {
			return cachedEnemies;
		}
		AABB bounds = queen.getBoundingBox().inflate(ENEMY_ANALYSIS_RANGE);
		cachedEnemies = List.copyOf(
				queen.level().getEntitiesOfClass(LivingEntity.class, bounds, queen::isValidTarget));
		enemyAnalysisGameTime = gameTime;
		return cachedEnemies;
	}

	private boolean isInDanger(List<LivingEntity> enemies) {
		if (queen.getHealth() < queen.getMaxHealth() * RETREAT_HEALTH_RATIO) {
			return true;
		}
		double radiusSquared = RETREAT_ENEMY_RADIUS * RETREAT_ENEMY_RADIUS;
		return enemies.stream().filter(enemy -> queen.distanceToSqr(enemy) <= radiusSquared)
				.limit(RETREAT_ENEMY_COUNT).count() >= RETREAT_ENEMY_COUNT;
	}

	private List<TheQueenOfHatredSkill> skills() {
		return List.of(
				TheQueenOfHatredSkills.SWEEP.get(),
				TheQueenOfHatredSkills.DISPEL.get(),
				TheQueenOfHatredSkills.SPIN.get(),
				TheQueenOfHatredSkills.LASER.get(),
				TheQueenOfHatredSkills.HEAL.get(),
				TheQueenOfHatredSkills.DAMAGE_REDUCTION.get(),
				TheQueenOfHatredSkills.PURIFICATION.get(),
				TheQueenOfHatredSkills.SLOWNESS.get(),
				TheQueenOfHatredSkills.MARK.get(),
				TheQueenOfHatredSkills.STARFALL.get(),
				TheQueenOfHatredSkills.PILLAR_OF_LIGHT.get());
	}

	private void clear() {
		plannedSkill = null;
		plannedTarget = null;
		remainingPlanTicks = 0;
	}

	private record WeightedPlan(TheQueenOfHatredSkill skill, LivingEntity target, double weight) {
	}
}
