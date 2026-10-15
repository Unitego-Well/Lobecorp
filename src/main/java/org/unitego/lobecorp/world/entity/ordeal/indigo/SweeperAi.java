package org.unitego.lobecorp.world.entity.ordeal.indigo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.control.ConductorMovement;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.conductor.ConductorAttachmentUtil;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.util.entity.ai.BrainUtil;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.AttackSkill;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.ReassembleSkill;
import org.unitego.lobecorp.util.entity.ai.SweeperWorkUtil;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.registry.entity.ai.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.entity.ai.LcSensorTypes;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;
import org.unitego.lobecorp.world.entity.skill.EntitySkillCastResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫的 Brain 配置、活动切换、恢复清理与地面群体转向。
public final class SweeperAi {
	// Brain 活动与行为优先级

	/// 核心活动的优先级。
	private static final int CORE_ACTIVITY_PRIORITY = 0;
	/// 闲置活动的优先级。
	private static final int IDLE_ACTIVITY_PRIORITY = 5;
	/// 战斗中常规行为的优先级。
	private static final int FIGHT_BEHAVIOR_PRIORITY = 5;
	/// 飞扑行为的优先级。
	private static final int LEAP_BEHAVIOR_PRIORITY = 3;

	// 注视角度参数

	/// LookTarget 每 tick 允许的最小转向角。
	private static final int MINIMUM_LOOK_ANGLE = 45;
	/// LookTarget 每 tick 允许的最大转向角。
	private static final int MAXIMUM_LOOK_ANGLE = 90;

	// 闲置行为参数

	/// 闲置游走使用的速度倍率。
	private static final float IDLE_STROLL_SPEED = 1.0F;
	/// 闲置游走行为的随机权重。
	private static final int IDLE_STROLL_WEIGHT = 14;
	/// 原地等待行为的随机权重。
	private static final int IDLE_WAIT_WEIGHT = 7;
	/// 随机观察附近生物行为的随机权重。
	private static final int IDLE_LOOK_WEIGHT = 9;
	/// 随机观察附近生物的最大距离。
	private static final double IDLE_LOOK_RANGE = 5.0;
	/// 随机观察附近生物的最大距离平方。
	private static final double IDLE_LOOK_RANGE_SQUARED = IDLE_LOOK_RANGE * IDLE_LOOK_RANGE;
	/// 闲置等待或观察的最短持续时间，单位为游戏刻。
	private static final int MINIMUM_IDLE_WAIT_TICKS = 20;
	/// 闲置等待或观察的最长持续时间，单位为游戏刻。
	private static final int MAXIMUM_IDLE_WAIT_TICKS = 40;

	// 目标移动参数

	/// 追踪攻击或清理目标时的行走速度。
	private static final float TARGET_WALK_SPEED = 3.0F;
	/// 非攻击移动在距目的地此范围内使用常速，单位为方块。
	private static final double RUN_TARGET_DISTANCE = 2.0D;
	/// 连续接近实体目标时从常速升至追踪速度的时间，单位为游戏刻（1 秒）。
	private static final int TARGET_ACCELERATION_TICKS = TICKS_PER_SECOND;
	/// 战斗追踪必须抵达目标所在方块，避免停在普攻范围外。
	private static final int TARGET_CLOSE_ENOUGH_DISTANCE = 0;

	// 地面鸟群参数

	/// 鸟群邻域的水平半径，单位为方块。
	private static final double FLOCK_NEIGHBOR_RADIUS = 6.0D;
	/// 仅聚合近似同层的同类，允许的脚部高差，单位为方块。
	private static final double FLOCK_NEIGHBOR_HEIGHT = 1.0D;
	/// 单次最多处理的附近同类数量，防止密集群体增加无界计算量。
	private static final int FLOCK_MAX_NEIGHBORS = 12;
	/// 群体方向的重算间隔，单位为游戏刻；邻居列表仍由既有 Sensor 维护。
	private static final int FLOCK_UPDATE_TICKS = 5;
	/// 每 tick 向最新群体方向插值的比例，避免邻居变化导致转向突跳。
	private static final double FLOCK_SMOOTHING = 0.25D;
	/// 双方碰撞框之外保留的分离间隙，单位为方块。
	private static final double FLOCK_SEPARATION_MARGIN = 0.6D;
	/// 分离、对齐和聚合的相对权重；寻路前进分量始终至少为 1。
	private static final double FLOCK_SEPARATION_WEIGHT = 1.2D;
	/// 同目标同类的移动方向对本次转向的影响权重。
	private static final double FLOCK_ALIGNMENT_WEIGHT = 0.35D;
	/// 同目标同类的中心位置对本次转向的影响权重。
	private static final double FLOCK_COHESION_WEIGHT = 0.25D;
	/// 横向分量与前进分量的最大比值；1 对应最多偏转 45 度。
	private static final double FLOCK_MAX_LATERAL_RATIO = 1.0D;
	/// 群体转向只检查前方短段，单位为方块；后续路线仍由导航决定。
	private static final double FLOCK_LOOK_AHEAD = 0.75D;
	/// 在下一路径节点的此距离内逐渐减弱群体转向，单位为方块。
	private static final double FLOCK_NODE_FADE_DISTANCE = 1.5D;
	/// 贴近路径节点时完全交回导航，避免节点到达判定被分离方向拖延。
	private static final double FLOCK_NODE_ARRIVAL_DISTANCE = 0.35D;
	/// 路点有明显高差时交回原版跳跃／台阶处理，单位为方块。
	private static final double FLOCK_MAX_WANTED_HEIGHT = 0.25D;
	/// 候选短段的危险地面采样间距，单位为方块。
	private static final double FLOCK_SAMPLE_DISTANCE = 0.25D;
	/// 水平向量的平方长度退化阈值，避免零距离归一化。
	private static final double FLOCK_VECTOR_EPSILON = 1.0E-6D;

	// 恢复清理参数

	/// 尝试进入恢复清理状态的生命比例阈值。
	private static final float RECOVERY_CLEANUP_HEALTH_THRESHOLD = 0.2F;
	/// 低生命时进入恢复清理状态的概率。
	private static final float RECOVERY_CLEANUP_START_CHANCE = 0.6F;
	/// 退出恢复清理状态的生命比例阈值。
	private static final float RECOVERY_CLEANUP_END_HEALTH_THRESHOLD = 0.7F;
	/// 恢复清理期间受击后转入战斗的概率。
	private static final float RECOVERY_CLEANUP_INTERRUPTION_CHANCE = 0.3F;
	/// 低生命恢复清理判定失败后的重试间隔。
	private static final int RECOVERY_CLEANUP_RETRY_TICKS = 5 * TICKS_PER_SECOND;

	/// 清道夫使用的 Brain 记忆与 Sensor 配置。
	private static final Brain.Provider<Sweeper> BRAIN_PROVIDER = BrainUtil.provider(SweeperAi::getActivities)
			.addSensorTypes(
					SensorType.NEAREST_LIVING_ENTITIES,
					LcSensorTypes.ORDEAL_ATTACKABLES.get(),
					LcSensorTypes.NEAREST_CLEANUP_TARGET.get(),
					SensorType.HURT_BY
			).build();
	private final Sweeper sweeper;
	private final SweeperWorkUtil.Approach cleanupApproach = new SweeperWorkUtil.Approach();
	private boolean recoveryCleanup;
	private boolean recoveryCleanupDecisionMade;
	private long recoveryCleanupRetryGameTime;
	private Vec3 flockInfluence = Vec3.ZERO;
	private Vec3 flockSteering = Vec3.ZERO;
	private int nextFlockUpdateTick;
	private int lastSkillAimTick = -1;
	private @Nullable Entity accelerationTarget;
	private int accelerationStartedAt;
	private int lastAccelerationTick;

	private SweeperAi(Sweeper sweeper) {
		this.sweeper = sweeper;
	}

	/// 为指定清道夫创建独立的 AI 运行状态。
	public static SweeperAi create(Sweeper sweeper) {
		return new SweeperAi(sweeper);
	}

	/// 创建并初始化清道夫的 Brain。
	public static Brain<Sweeper> makeBrain(Sweeper sweeper, Brain.Packed packedBrain) {
		return BRAIN_PROVIDER.makeBrain(sweeper, packedBrain);
	}

	private static List<ActivityData<Sweeper>> getActivities(Sweeper owner) {
		return List.of(
				ActivityData.create(Activity.CORE, CORE_ACTIVITY_PRIORITY, ImmutableList.of(
						new LookAtTargetSink(MINIMUM_LOOK_ANGLE, MAXIMUM_LOOK_ANGLE), new MoveToTargetSink())
				), ActivityData.create(Activity.IDLE, IDLE_ACTIVITY_PRIORITY, ImmutableList.of(
						StartAttacking.create((_, sweeper) -> sweeper.ai().isRecoveryCleanup()
								&& sweeper.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
								.filter(target -> isValidCleanupTarget(sweeper, target)).isPresent()
								? Optional.empty()
								: sweeper.getBrain()
								.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
								.orElse(NearestVisibleLivingEntities.empty())
								.findClosest(sweeper::isValidTarget)),
						disposeCleanupTarget(),
						new RunOne<>(Map.of(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get(), MemoryStatus.VALUE_ABSENT), ImmutableList.of(
								Pair.of(new DoNothing(MINIMUM_IDLE_WAIT_TICKS, MAXIMUM_IDLE_WAIT_TICKS),
										IDLE_WAIT_WEIGHT),
								Pair.of(RandomStroll.stroll(IDLE_STROLL_SPEED), IDLE_STROLL_WEIGHT),
								Pair.of(lookAtRandomNearbyLiving(), IDLE_LOOK_WEIGHT)))
				)), ActivityData.create(Activity.FIGHT,
						ImmutableList.of(
								Pair.of(FIGHT_BEHAVIOR_PRIORITY, walkToAttackTarget()),
								Pair.of(FIGHT_BEHAVIOR_PRIORITY, StopAttackingIfTargetInvalid.create()),
								Pair.of(FIGHT_BEHAVIOR_PRIORITY, performAttack()),
								Pair.of(LEAP_BEHAVIOR_PRIORITY, performLeap())
						),
						Sets.newHashSet(
								Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
						Sets.newHashSet(
								MemoryModuleType.ATTACK_TARGET)
				)
		);
	}

	private static OneShot<Sweeper> lookAtRandomNearbyLiving() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.absent(MemoryModuleType.LOOK_TARGET),
				instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
		).apply(instance, (lookTarget, nearestEntities) -> (_, sweeper, _) -> {
			List<LivingEntity> candidates = instance.get(nearestEntities)
					.find(candidate -> candidate != sweeper && candidate.isAlive()
							&& sweeper.distanceToSqr(candidate) <= IDLE_LOOK_RANGE_SQUARED)
					.toList();

			if (candidates.isEmpty()) {
				return false;
			}

			LivingEntity target = candidates.get(sweeper.getRandom().nextInt(candidates.size()));
			int duration = MINIMUM_IDLE_WAIT_TICKS + sweeper.getRandom().nextInt(
					MAXIMUM_IDLE_WAIT_TICKS - MINIMUM_IDLE_WAIT_TICKS + 1);
			lookTarget.setWithExpiry(new EntityTracker(target, true), duration);
			return true;
		}));
	}

	/// 没有技能占用时走向战斗目标。
	private static OneShot<Sweeper> walkToAttackTarget() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, target -> (_, sweeper, _) -> {
			if (EntitySkillUtil.hasActiveSkills(sweeper)) {
				stopMovementForSkill(sweeper);
				return false;
			}

			BehaviorUtils.setWalkAndLookTargetMemories(sweeper, instance.get(target),
					TARGET_WALK_SPEED, TARGET_CLOSE_ENOUGH_DISTANCE);
			return true;
		}));
	}

	/// 近战攻击：目标在射程内时通过技能系统施放。
	private static OneShot<Sweeper> performAttack() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, target -> (_, sweeper, _) -> {
			if (!EntitySkillUtil.require(sweeper).canBeginCast(SweeperSkills.ATTACK.get())) {
				return false;
			}

			LivingEntity attackTarget = instance.get(target);
			if (!AttackSkill.isWithinAttackRange(sweeper, attackTarget)) {
				return false;
			}

			var result = EntitySkillUtil.cast(sweeper, SweeperSkills.ATTACK.get());
			if (result.status() == EntitySkillCastResult.Status.AIMING) {
				sweeper.ai().lastSkillAimTick = sweeper.tickCount;
			}

			if (!result.started()) {
				return false;
			}

			stopMovementForSkill(sweeper);
			return true;
		}));
	}

	/// 飞扑：目标不在近战范围时冲向目标。
	private static OneShot<Sweeper> performLeap() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, target -> (_, sweeper, _) -> {
			if (!EntitySkillUtil.require(sweeper).canBeginCast(SweeperSkills.LEAP.get())) {
				return false;
			}

			LivingEntity attackTarget = instance.get(target);
			if (AttackSkill.isWithinAttackRange(sweeper, attackTarget)) {
				return false;
			}

			var result = EntitySkillUtil.cast(sweeper, SweeperSkills.LEAP.get());
			if (result.status() == EntitySkillCastResult.Status.AIMING) {
				sweeper.ai().lastSkillAimTick = sweeper.tickCount;
			}

			if (!result.started()) {
				return false;
			}

			stopMovementForSkill(sweeper);
			return true;
		}));
	}

	/// 清除技能开始前残留的寻路目标，避免核心 MoveToTargetSink 在不可打断动作期间继续推动实体。
	private static void stopMovementForSkill(Sweeper sweeper) {
		sweeper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		sweeper.getNavigation().stop();
	}

	/// 靠近后优先重组尸体，否则清扫尸体或物品。
	private static OneShot<Sweeper> disposeCleanupTarget() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()),
				instance.absent(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, (nearestCleanupTarget, _) -> (_, sweeper, _) -> {
			if (EntitySkillUtil.require(sweeper).activeSkills().stream().anyMatch(runtime -> !runtime.isInterruptibleBySkill())) {
				return false;
			}

			Entity target = instance.get(nearestCleanupTarget);
			if (!isValidCleanupTarget(sweeper, target) || !SweeperWorkUtil.isReady(sweeper, target)) {
				return false;
			}

			var skill = target instanceof EntityCorpse<?> corpse && corpse.getOwnerEntity() instanceof Sweeper
					? SweeperSkills.REASSEMBLE.get() : SweeperSkills.SWEEP.get();
			if (EntitySkillUtil.isOnCooldown(sweeper, skill)
					|| !EntitySkillUtil.require(sweeper).canBeginCast(skill)) {
				return false;
			}

			return EntitySkillUtil.cast(sweeper, skill).started();
		}));
	}

	private static boolean isValidCleanupTarget(Sweeper sweeper, Entity target) {
		return target.isAlive() && (target instanceof EntityCorpse<?> corpse
				&& (!(corpse.getOwnerEntity() instanceof Sweeper) || ReassembleSkill.canReassemble(sweeper, corpse))
				|| target instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty());
	}

	/// 在 Brain 行为推进后接近工作站位，避免核心寻路或闲逛覆盖最后一段精确移动。
	private void updateCleanupMovement() {
		if (sweeper.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			cleanupApproach.reset();
			return;
		}

		if (ConductorController.isControlled(sweeper)
				&& (ConductorAttachmentUtil.unit(sweeper).order != ConductorData.OrderType.NONE
				|| ConductorController.hasPendingSkillCast(sweeper))) {
			cleanupApproach.reset();
			return;
		}

		Entity target = sweeper.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()).orElse(null);
		if (target == null || !isValidCleanupTarget(sweeper, target)) {
			cleanupApproach.clear(sweeper);
			sweeper.getBrain().eraseMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get());
			return;
		}

		if (!ConductorController.allowNavigation(sweeper, target.position())) {
			cleanupApproach.reset();
			return;
		}

		long now = sweeper.tickCount;
		if (EntitySkillUtil.hasActiveSkills(sweeper)) {
			cleanupApproach.pause(sweeper, now);
			return;
		}

		if (!cleanupApproach.advance(sweeper, target, TARGET_WALK_SPEED, now)) {
			cleanupApproach.clear(sweeper);
			sweeper.getBrain().eraseMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get());
		}
	}

	/// 根据当前生命、目标和技能状态更新恢复清理与活动。
	protected void updateActivity() {
		updateRecoveryCleanup();
		if (sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()
				&& (EntitySkillUtil.isCasting(sweeper, SweeperSkills.SWEEP.get())
				|| EntitySkillUtil.isCasting(sweeper, SweeperSkills.REASSEMBLE.get()))) {
			EntitySkillUtil.cancelSkill(sweeper);
		}

		sweeper.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
		updateCleanupMovement();
	}

	protected void updateMovementSpeed() {
		// JUMPING 不计入 hasWanted，但活跃导航仍需连续更新加速和速度倍率。
		if (!sweeper.isAlive() || !sweeper.getMoveControl().hasWanted() && sweeper.getNavigation().isDone()
				|| EntitySkillUtil.hasActiveSkills(sweeper) || !(sweeper.level() instanceof ServerLevel level)) {
			resetFlocking();
			resetTargetAcceleration();
			return;
		}
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(sweeper.getUUID());
		Vec3 destination;
		Entity movementTarget = null;
		boolean chasing = false;
		if (unit != null && (unit.order() == ConductorData.OrderType.MOVE || unit.order() == ConductorData.OrderType.RETURN)) {
			destination = new Vec3(unit.x(), unit.y(), unit.z());
		} else {
			LivingEntity attackTarget = sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			Entity cleanupTarget = sweeper.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()).orElse(null);
			if (attackTarget != null && attackTarget.isAlive()) {
				destination = attackTarget.position();
				movementTarget = attackTarget;
				chasing = true;
			} else if (cleanupTarget != null && cleanupTarget.isAlive()) {
				destination = cleanupTarget.position();
				movementTarget = cleanupTarget;
			} else {
				destination = sweeper.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
						.map(target -> target.getTarget().currentPosition()).orElse(null);
			}
		}

		if (destination == null) {
			BlockPos target = sweeper.getNavigation().getTargetPos();
			if (target != null) {
				destination = Vec3.atBottomCenterOf(target);
			}
		}

		if (destination == null) {
			resetFlocking();
			resetTargetAcceleration();
			return;
		}

		boolean running = chasing || (movementTarget != null || unit != null && unit.order() != ConductorData.OrderType.NONE)
				&& sweeper.position().distanceToSqr(destination) > RUN_TARGET_DISTANCE * RUN_TARGET_DISTANCE;
		double speed;
		if (running && movementTarget != null) {
			speed = accelerateToTarget(movementTarget);
		} else {
			resetTargetAcceleration();
			speed = running ? TARGET_WALK_SPEED : IDLE_STROLL_SPEED;
		}

		if (unit != null && unit.order() == ConductorData.OrderType.MOVE && unit.movementSpeed() >= 0.0D) {
			double base = ConductorMovement.baseSpeed(sweeper);
			speed = base <= 0.0D ? 0.0D : Math.min(speed, unit.movementSpeed() / base);
		}

		sweeper.getNavigation().setSpeedModifier(speed);
		Vec3 wanted = new Vec3(sweeper.getMoveControl().getWantedX(),
				sweeper.getMoveControl().getWantedY(), sweeper.getMoveControl().getWantedZ());
		wanted = steerWithFlock(level, wanted, destination, chasing);
		sweeper.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
	}

	private double accelerateToTarget(Entity target) {
		if (accelerationTarget != target || sweeper.tickCount - lastAccelerationTick > 1) {
			accelerationTarget = target;
			accelerationStartedAt = sweeper.tickCount;
		}

		lastAccelerationTick = sweeper.tickCount;
		double progress = Math.min(1.0D, (sweeper.tickCount - accelerationStartedAt) / (double) TARGET_ACCELERATION_TICKS);
		return IDLE_STROLL_SPEED + (TARGET_WALK_SPEED - IDLE_STROLL_SPEED) * progress;
	}

	private void resetTargetAcceleration() {
		accelerationTarget = null;
		accelerationStartedAt = 0;
		lastAccelerationTick = 0;
	}

	private static boolean isFlockingSuppressed(Sweeper member) {
		return !member.isAlive() || member.isNoAi() || !member.onGround() || member.isPassenger()
				|| member.isInWater() || member.isInLava() || member.onClimbable()
				|| ConductorController.isControlled(member) || EntitySkillUtil.hasActiveSkills(member)
				|| member.ai().lastSkillAimTick == member.tickCount
				|| !member.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				&& member.getBrain().hasMemoryValue(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get());
	}

	/// 只调整当前路径的水平局部方向，不替换 WALK_TARGET、路径或运动物理。
	private Vec3 steerWithFlock(ServerLevel level, Vec3 wanted, Vec3 destination, boolean chasing) {
		if (isFlockingSuppressed(sweeper) || sweeper.getNavigation().isDone()
				|| !sweeper.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
				|| Math.abs(wanted.y - sweeper.getY()) > FLOCK_MAX_WANTED_HEIGHT
				|| chasing && sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
				.filter(target -> AttackSkill.isWithinAttackRange(sweeper, target)).isPresent()) {
			resetFlocking();
			return wanted;
		}

		Vec3 route = wanted.subtract(sweeper.position()).multiply(1.0D, 0.0D, 1.0D);
		double distance = route.length();
		if (distance <= FLOCK_NODE_ARRIVAL_DISTANCE) {
			return wanted;
		}

		route = route.scale(1.0D / distance);
		if (sweeper.tickCount >= nextFlockUpdateTick) {
			flockInfluence = calculateFlockInfluence(level, route);
			nextFlockUpdateTick = sweeper.tickCount + FLOCK_UPDATE_TICKS;
		}

		flockSteering = flockSteering.lerp(flockInfluence, FLOCK_SMOOTHING);
		if (flockSteering.lengthSqr() <= FLOCK_VECTOR_EPSILON) {
			return wanted;
		}

		Vec3 desired = route.add(flockSteering.scale(Math.min(1.0D, distance / FLOCK_NODE_FADE_DISTANCE)));
		double forward = Math.max(1.0D, desired.dot(route));
		Vec3 lateral = desired.subtract(route.scale(desired.dot(route)));
		double lateralLimit = forward * FLOCK_MAX_LATERAL_RATIO;
		if (lateral.lengthSqr() > lateralLimit * lateralLimit) {
			lateral = lateral.normalize().scale(lateralLimit);
		}

		Vec3 direction = route.scale(forward).add(lateral).normalize();
		Vec3 candidate = sweeper.position().add(direction.scale(Math.min(distance, FLOCK_LOOK_AHEAD)));
		candidate = new Vec3(candidate.x, wanted.y, candidate.z);
		if (chasing && candidate.distanceToSqr(destination) >= sweeper.position().distanceToSqr(destination)
				|| !safeFlockStep(level, candidate)) {
			return wanted;
		}

		return candidate;
	}

	private Vec3 calculateFlockInfluence(ServerLevel level, Vec3 route) {
		Vec3 separation = Vec3.ZERO;
		Vec3 alignment = Vec3.ZERO;
		Vec3 cohesion = Vec3.ZERO;
		int neighbors = 0;
		int separating = 0;
		int aligned = 0;
		int cohesive = 0;
		LivingEntity target = sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		var visible = sweeper.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
				.orElse(NearestVisibleLivingEntities.empty());
		for (LivingEntity entity : sweeper.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of())) {
			if (!(entity instanceof Sweeper neighbor) || neighbor == sweeper || isFlockingSuppressed(neighbor)
					|| level.getEntity(neighbor.getId()) != neighbor
					|| Math.abs(neighbor.getY() - sweeper.getY()) > FLOCK_NEIGHBOR_HEIGHT) {
				continue;
			}

			Vec3 offset = sweeper.position().subtract(neighbor.position()).multiply(1.0D, 0.0D, 1.0D);
			double distance = offset.length();
			if (distance > FLOCK_NEIGHBOR_RADIUS || !visible.contains(neighbor)) {
				continue;
			}

			if (neighbors++ >= FLOCK_MAX_NEIGHBORS) {
				break;
			}

			double separationDistance = (sweeper.getBbWidth() + neighbor.getBbWidth()) / 2.0D + FLOCK_SEPARATION_MARGIN;
			if (distance < separationDistance) {
				// 完全重叠时以实体 ID 选择相反方向，避免零向量令双方始终无法分离。
				Vec3 away = offset.lengthSqr() <= FLOCK_VECTOR_EPSILON
						? new Vec3(1.0D, 0.0D, 1.0D).normalize().scale(sweeper.getId() < neighbor.getId() ? -1.0D : 1.0D)
						: offset.scale(1.0D / distance);
				Vec3 sideways = away.subtract(route.scale(away.dot(route)));
				if (sideways.lengthSqr() <= FLOCK_VECTOR_EPSILON && offset.dot(route) <= 0.0D) {
					// 同线前方邻居改为侧向绕行；先统一世界侧向，再按 ID 分侧，避免迎面双方选择同一侧。
					Vec3 side = new Vec3(-route.z, 0.0D, route.x);
					if (side.x < 0.0D || side.x == 0.0D && side.z < 0.0D) {
						side = side.scale(-1.0D);
					}

					away = side.scale(sweeper.getId() < neighbor.getId() ? -1.0D : 1.0D);
				}

				separation = separation.add(away.scale(1.0D - distance / separationDistance));
				separating++;
			}

			if (neighbor.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) != target) {
				continue;
			}

			cohesion = cohesion.subtract(offset);
			cohesive++;
			if (!neighbor.getNavigation().isDone()) {
				Vec3 heading = neighbor.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
				if (heading.lengthSqr() > FLOCK_VECTOR_EPSILON) {
					alignment = alignment.add(heading.normalize());
					aligned++;
				}
			}
		}

		Vec3 influence = separating == 0 ? Vec3.ZERO : separation.scale(FLOCK_SEPARATION_WEIGHT / separating);
		if (aligned > 0) {
			influence = influence.add(alignment.scale(1.0D / aligned).subtract(route).scale(FLOCK_ALIGNMENT_WEIGHT));
		}

		if (cohesive > 0) {
			influence = influence.add(cohesion.scale(FLOCK_COHESION_WEIGHT / (cohesive * FLOCK_NEIGHBOR_RADIUS)));
		}

		return influence;
	}

	private boolean safeFlockStep(ServerLevel level, Vec3 destination) {
		if (!ConductorMovement.safeApproach(sweeper, destination)) {
			return false;
		}

		Vec3 delta = destination.subtract(sweeper.position());
		int samples = Math.max(1, (int) Math.ceil(delta.length() / FLOCK_SAMPLE_DISTANCE));
		for (int sample = 1; sample <= samples; sample++) {
			Vec3 point = sweeper.position().add(delta.scale(sample / (double) samples));
			var bounds = sweeper.getBoundingBox().move(point.subtract(sweeper.position()));
			for (var pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, point.y, bounds.minZ),
					BlockPos.containing(bounds.maxX, point.y, bounds.maxZ))) {
				if (!level.getFluidState(pos).isEmpty()
						|| sweeper.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(sweeper, pos)) != 0.0F) {
					return false;
				}
			}
		}

		return true;
	}

	private void resetFlocking() {
		flockInfluence = Vec3.ZERO;
		flockSteering = Vec3.ZERO;
		nextFlockUpdateTick = 0;
	}

	/// 恢复清理状态下受击时按概率恢复战斗。
	protected boolean onHurt() {
		if (!recoveryCleanup) {
			return true;
		}

		if (sweeper.getRandom().nextFloat() >= RECOVERY_CLEANUP_INTERRUPTION_CHANCE) {
			return false;
		}

		recoveryCleanup = false;
		recoveryCleanupDecisionMade = true;
		recoveryCleanupRetryGameTime = sweeper.level().getGameTime() + RECOVERY_CLEANUP_RETRY_TICKS;
		EntitySkillUtil.cancelSkill(sweeper);
		sweeper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		return true;
	}

	private void updateRecoveryCleanup() {
		float healthRatio = sweeper.getHealth() / sweeper.getMaxHealth();
		if (healthRatio >= RECOVERY_CLEANUP_END_HEALTH_THRESHOLD) {
			recoveryCleanup = false;
			recoveryCleanupDecisionMade = false;
			recoveryCleanupRetryGameTime = 0L;
			return;
		}

		if (!recoveryCleanup && healthRatio >= RECOVERY_CLEANUP_HEALTH_THRESHOLD) {
			recoveryCleanupDecisionMade = false;
			recoveryCleanupRetryGameTime = 0L;
			return;
		}

		long gameTime = sweeper.level().getGameTime();
		if (!recoveryCleanup && recoveryCleanupDecisionMade
				&& gameTime >= recoveryCleanupRetryGameTime) {
			recoveryCleanupDecisionMade = false;
		}

		boolean hasCleanupTarget = sweeper.getBrain()
				.getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
				.filter(target -> isValidCleanupTarget(sweeper, target))
				.isPresent();

		if (!recoveryCleanup && !recoveryCleanupDecisionMade && hasCleanupTarget) {
			recoveryCleanupDecisionMade = true;
			boolean startCleanup = sweeper.getRandom().nextFloat() < RECOVERY_CLEANUP_START_CHANCE;
			recoveryCleanup = startCleanup;
			if (!startCleanup) {
				recoveryCleanupRetryGameTime = gameTime + RECOVERY_CLEANUP_RETRY_TICKS;
			}
		}

		if (!recoveryCleanup || !hasCleanupTarget) {
			return;
		}

		if (!EntitySkillUtil.isCasting(sweeper, SweeperSkills.SWEEP.get())
				&& !EntitySkillUtil.isCasting(sweeper, SweeperSkills.REASSEMBLE.get())) {
			EntitySkillUtil.cancelSkill(sweeper);
		}

		sweeper.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
	}

	private boolean isRecoveryCleanup() {
		return recoveryCleanup;
	}
}
