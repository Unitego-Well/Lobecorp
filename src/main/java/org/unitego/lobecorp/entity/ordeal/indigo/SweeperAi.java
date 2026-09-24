package org.unitego.lobecorp.entity.ordeal.indigo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperSweepSkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.brain.LcSensorTypes;
import org.unitego.lobecorp.registry.entity_skill.SweeperSkills;

import java.util.List;
import java.util.Optional;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫的 Brain 配置、活动切换与恢复清理工具。
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
	/// 寻路目标允许停止移动的距离。
	private static final int TARGET_CLOSE_ENOUGH_DISTANCE = 1;

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
	private boolean recoveryCleanup;
	private boolean recoveryCleanupDecisionMade;
	private long recoveryCleanupRetryGameTime;

	private SweeperAi(Sweeper sweeper) {
		this.sweeper = sweeper;
	}

	static SweeperAi create(Sweeper sweeper) {
		return new SweeperAi(sweeper);
	}

	/// 创建并初始化清道夫的 Brain。
	public static Brain<Sweeper> makeBrain(Sweeper sweeper, Brain.Packed packedBrain) {
		return BRAIN_PROVIDER.makeBrain(sweeper, packedBrain);
	}

	/// 根据当前生命、目标和技能状态更新恢复清理与活动。
	void updateActivity() {
		updateRecoveryCleanup();
		if (sweeper.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()
				&& (EntitySkillManager.isCasting(sweeper, SweeperSkills.SWEEP.get())
				|| EntitySkillManager.isCasting(sweeper, SweeperSkills.REASSEMBLE.get()))) {
			EntitySkillManager.cancelSkill(sweeper);
		}
		sweeper.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
	}

	/// 恢复清理状态下受击时按概率恢复战斗。
	boolean onHurt() {
		if (!recoveryCleanup) {
			return true;
		}
		if (sweeper.getRandom().nextFloat() >= RECOVERY_CLEANUP_INTERRUPTION_CHANCE) {
			return false;
		}
		recoveryCleanup = false;
		recoveryCleanupDecisionMade = true;
		recoveryCleanupRetryGameTime = sweeper.level().getGameTime() + RECOVERY_CLEANUP_RETRY_TICKS;
		EntitySkillManager.cancelSkill(sweeper);
		sweeper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		return true;
	}

	private static List<ActivityData<Sweeper>> getActivities(Sweeper owner) {
		return List.of(
				ActivityData.create(Activity.CORE, CORE_ACTIVITY_PRIORITY, ImmutableList.of(
						new LookAtTargetSink(MINIMUM_LOOK_ANGLE, MAXIMUM_LOOK_ANGLE), new MoveToTargetSink())
				), ActivityData.create(Activity.IDLE, IDLE_ACTIVITY_PRIORITY, ImmutableList.of(
						StartAttacking.create((level, sweeper) -> sweeper.ai().isRecoveryCleanup()
								&& sweeper.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
										.filter(SweeperAi::isValidCleanupTarget).isPresent()
								? Optional.empty()
								: sweeper.getBrain()
										.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
										.orElse(NearestVisibleLivingEntities.empty())
										.findClosest(sweeper::isValidTarget)),
						walkToCleanupTarget(),
						disposeCleanupTarget(),
						new RunOne<>(ImmutableList.of(
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
		).apply(instance, (lookTarget, nearestEntities) -> (level, sweeper, time) -> {
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
		).apply(instance, target -> (level, sweeper, time) -> {
			if (EntitySkillManager.hasActiveSkills(sweeper)) {
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
		).apply(instance, target -> (level, sweeper, time) -> {
			if (EntitySkillManager.hasActiveSkills(sweeper)) {
				return false;
			}

			LivingEntity attackTarget = instance.get(target);
			if (!sweeper.isWithinMeleeAttackRange(attackTarget)) {
				return false;
			}

			if (!EntitySkillManager.cast(sweeper, SweeperSkills.ATTACK.get())) {
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
		).apply(instance, target -> (level, sweeper, time) -> {
			if (EntitySkillManager.hasActiveSkills(sweeper)) {
				return false;
			}

			LivingEntity attackTarget = instance.get(target);
			if (sweeper.isWithinMeleeAttackRange(attackTarget)) {
				return false;
			}

			if (!EntitySkillManager.cast(sweeper, SweeperSkills.LEAP.get())) {
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

	/// 没有战斗目标和技能占用时，走向最近的清理目标。
	private static OneShot<Sweeper> walkToCleanupTarget() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()),
				instance.absent(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, (nearestCleanupTarget, attackTarget) -> (level, sweeper, time) -> {
			if (EntitySkillManager.hasActiveSkills(sweeper)) {
				return false;
			}

			Entity target = instance.get(nearestCleanupTarget);
			if (!isValidCleanupTarget(target) || isWithinCleanupRange(sweeper, target)) {
				sweeper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				sweeper.getNavigation().stop();
				return false;
			}

			BehaviorUtils.setWalkAndLookTargetMemories(sweeper, target,
					TARGET_WALK_SPEED, TARGET_CLOSE_ENOUGH_DISTANCE);
			return true;
		}));
	}

	/// 靠近后优先重组尸体，否则清扫尸体或物品。
	private static OneShot<Sweeper> disposeCleanupTarget() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()),
				instance.absent(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, (nearestCleanupTarget, attackTarget) -> (level, sweeper, time) -> {
			if (EntitySkillManager.hasActiveSkills(sweeper)) {
				return false;
			}

			Entity target = instance.get(nearestCleanupTarget);
			if (!isValidCleanupTarget(target) || !isWithinCleanupRange(sweeper, target)) {
				return false;
			}

			if (!(target instanceof EntityCorpse<?> corpse)) {
				return EntitySkillManager.cast(sweeper, SweeperSkills.SWEEP.get());
			}

			if (EntitySkillManager.cast(sweeper, SweeperSkills.REASSEMBLE.get())) {
				return true;
			}

			if (corpse.getOwnerEntity() instanceof Sweeper) {
				return false;
			}

			return EntitySkillManager.cast(sweeper, SweeperSkills.SWEEP.get());
		}));
	}

	private static boolean isValidCleanupTarget(Entity target) {
		return target.isAlive() && (target instanceof EntityCorpse<?>
				|| target instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty());
	}

	private static boolean isWithinCleanupRange(Sweeper sweeper, Entity target) {
		return SweeperSweepSkill.isWithinRange(sweeper, target);
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
				.filter(SweeperAi::isValidCleanupTarget)
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

		if (!EntitySkillManager.isCasting(sweeper, SweeperSkills.SWEEP.get())
				&& !EntitySkillManager.isCasting(sweeper, SweeperSkills.REASSEMBLE.get())) {
			EntitySkillManager.cancelSkill(sweeper);
		}

		sweeper.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
	}

	private boolean isRecoveryCleanup() {
		return recoveryCleanup;
	}
}
