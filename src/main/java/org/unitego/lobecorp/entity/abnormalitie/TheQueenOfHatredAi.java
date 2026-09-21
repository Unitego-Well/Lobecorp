package org.unitego.lobecorp.entity.abnormalitie;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import org.unitego.lobecorp.entity.ai.movement.TheQueenOfHatredMovementMode;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.brain.LcSensorTypes;

import java.util.Comparator;
import java.util.List;

/// 憎恶皇后的 Brain 配置、活动切换与目标刷新工具。
public final class TheQueenOfHatredAi {
	// 目标感知

	/// Brain 搜索目标的最大距离。
	public static final double TARGET_SEARCH_RANGE = 32.0;
	/// 目标搜索距离的平方缓存。
	private static final double TARGET_SEARCH_RANGE_SQUARED = TARGET_SEARCH_RANGE * TARGET_SEARCH_RANGE;

	// 活动与行为优先级

	/// 核心活动的优先级。
	private static final int CORE_ACTIVITY_PRIORITY = 0;
	/// 闲置活动的优先级。
	private static final int IDLE_ACTIVITY_PRIORITY = 5;
	/// 战斗控制行为的优先级。
	private static final int COMBAT_BEHAVIOR_PRIORITY = 0;
	/// 无效目标清理行为的优先级。
	private static final int STOP_INVALID_TARGET_PRIORITY = 1;
	/// 战斗移动意图提交行为的优先级。
	private static final int COMBAT_MOVEMENT_PRIORITY = 2;

	// 核心与闲置行为参数

	/// LookTarget 每 tick 允许的最小转向角。
	private static final int MINIMUM_LOOK_ANGLE = 45;
	/// LookTarget 每 tick 允许的最大转向角。
	private static final int MAXIMUM_LOOK_ANGLE = 90;
	/// 闲置游走使用的速度倍率。
	private static final float IDLE_STROLL_SPEED = 1.0F;
	/// 闲置游走行为的随机权重。
	private static final int IDLE_STROLL_WEIGHT = 2;
	/// 原地等待行为的随机权重。
	private static final int IDLE_WAIT_WEIGHT = 1;
	/// 原地等待的最短 tick。
	private static final int MINIMUM_IDLE_WAIT_TICKS = 20;
	/// 原地等待的最长 tick。
	private static final int MAXIMUM_IDLE_WAIT_TICKS = 40;

	/// 憎恶皇后的 Brain 记忆与 Sensor 配置。
	private static final Brain.Provider<TheQueenOfHatred> BRAIN_PROVIDER =
			BrainUtil.provider(TheQueenOfHatredAi::getActivities)
					.addSensorTypes(SensorType.NEAREST_LIVING_ENTITIES,
							LcSensorTypes.THE_QUEEN_OF_HATRED_ATTACKABLES.get(), SensorType.HURT_BY)
					.build();

	private TheQueenOfHatredAi() {
	}

	/// 创建并初始化憎恶皇后的 Brain。
	public static Brain<TheQueenOfHatred> makeBrain(TheQueenOfHatred queen, Brain.Packed packedBrain) {
		return BRAIN_PROVIDER.makeBrain(queen, packedBrain);
	}

	/// 在 Brain tick 前刷新动态攻击目标。
	public static void updateDynamicAttackTarget(TheQueenOfHatred queen) {
		LivingEntity currentTarget = queen.getAttackTarget();
		if (currentTarget != null && queen.isValidTarget(currentTarget)
				&& !queen.shouldRefreshCombatTarget()) {
			return;
		}
		queen.retaliationTargets().removeIf(target -> !queen.isValidTarget(target) || target.isRemoved()
				|| queen.distanceToSqr(target) > TARGET_SEARCH_RANGE_SQUARED);
		LivingEntity target = queen.retaliationTargets().stream()
				.min(Comparator.comparingDouble(queen::distanceToSqr))
				.orElseGet(() -> queen.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE)
						.filter(queen::isValidTarget)
						.orElse(null));
		if (target == null) {
			queen.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		} else {
			queen.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		}
	}

	/// 根据当前记忆切换战斗或闲置活动。
	public static void updateActivity(TheQueenOfHatred queen) {
		queen.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
	}

	private static List<ActivityData<TheQueenOfHatred>> getActivities(TheQueenOfHatred owner) {
		return List.of(
				ActivityData.create(Activity.CORE, CORE_ACTIVITY_PRIORITY, ImmutableList.of(
						new LookAtTargetSink(MINIMUM_LOOK_ANGLE, MAXIMUM_LOOK_ANGLE))),
				ActivityData.create(Activity.IDLE, IDLE_ACTIVITY_PRIORITY, ImmutableList.of(
						submitIdleMovement(),
						StartAttacking.create((level, queen) -> queen.getBrain()
								.getMemory(MemoryModuleType.NEAREST_ATTACKABLE)
								.filter(queen::isValidTarget)),
						new RunOne<>(ImmutableList.of(
								Pair.of(RandomStroll.stroll(IDLE_STROLL_SPEED), IDLE_STROLL_WEIGHT),
								Pair.of(new DoNothing(MINIMUM_IDLE_WAIT_TICKS, MAXIMUM_IDLE_WAIT_TICKS),
										IDLE_WAIT_WEIGHT))))),
				ActivityData.create(Activity.FIGHT, ImmutableList.of(
								Pair.of(COMBAT_BEHAVIOR_PRIORITY, performPlannedAttack()),
								Pair.of(STOP_INVALID_TARGET_PRIORITY, StopAttackingIfTargetInvalid.create()),
								Pair.of(COMBAT_MOVEMENT_PRIORITY, submitCombatMovement())),
						Sets.newHashSet(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
						Sets.newHashSet(MemoryModuleType.ATTACK_TARGET))
		);
	}

	private static OneShot<TheQueenOfHatred> performPlannedAttack() {
		return BehaviorBuilder.create(instance -> instance.point((level, queen, time) ->
				queen.tickCombatController()));
	}

	private static OneShot<TheQueenOfHatred> submitCombatMovement() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, target -> (level, queen, time) -> {
			LivingEntity attackTarget = instance.get(target);
			if (!queen.isValidTarget(attackTarget)) {
				return false;
			}
			if (queen.isDispelLanding() || EntitySkillManager.isMovementLocked(queen)) {
				return true;
			}
			queen.requestCombatMovement(attackTarget);
			return true;
		}));
	}

	private static OneShot<TheQueenOfHatred> submitIdleMovement() {
		return BehaviorBuilder.create(instance -> instance.point((level, queen, time) -> {
			if (queen.isDispelLanding()) {
				return true;
			}
			if (EntitySkillManager.isMovementLocked(queen)) {
				return true;
			}
			if (queen.movementMode() != TheQueenOfHatredMovementMode.GROUND) {
				queen.requestIdleLanding();
				return true;
			}
			queen.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(walkTarget -> {
				queen.requestIdleMovement(walkTarget.getTarget().currentPosition());
				queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			});
			return true;
		}));
	}
}
