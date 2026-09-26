package org.unitego.lobecorp.entity.abnormalitie;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后的 Brain 活动与闲置行为。
public final class TheQueenOfHatredAi {
	/// Brain 活动优先级。
	/// Brain 核心活动的调度优先级。
	private static final int CORE_ACTIVITY_PRIORITY = 0;
	/// Brain 闲置活动的调度优先级。
	private static final int IDLE_ACTIVITY_PRIORITY = 5;
	/// 战斗活动中目标检查行为的优先级。
	private static final int FIGHT_TARGET_CHECK_PRIORITY = 0;
	/// 战斗活动中技能施放行为的优先级。
	private static final int FIGHT_SKILL_PRIORITY = 1;

	/// 闲置随机游走参数。
	/// 随机游走的速度倍率。
	private static final float IDLE_STROLL_SPEED = 1.0F;
	/// 闲置随机游走的最短触发间隔，单位为游戏刻。
	private static final int IDLE_STROLL_INTERVAL_TICKS = 10 * TICKS_PER_SECOND;
	/// 女皇随机动作选择的最短间隔，单位为游戏刻。
	private static final int MINIMUM_IDLE_ACTION_INTERVAL_TICKS = 20 * TICKS_PER_SECOND;
	/// 女皇随机动作选择的最长间隔，单位为游戏刻。
	private static final int MAXIMUM_IDLE_ACTION_INTERVAL_TICKS = 40 * TICKS_PER_SECOND;
	/// 坐下动作的最短持续时间，单位为游戏刻。
	private static final int MINIMUM_SITTING_DURATION_TICKS = 60 * TICKS_PER_SECOND;
	/// 坐下动作的最长持续时间，单位为游戏刻。
	private static final int MAXIMUM_SITTING_DURATION_TICKS = 120 * TICKS_PER_SECOND;
	/// 坐下动作结束后的淡出时间，单位为游戏刻。
	static final int SITTING_FADE_OUT_TICKS = 23;
	/// 随机游走目的地的最小水平距离，单位为格。
	private static final int MINIMUM_IDLE_STROLL_DISTANCE = 2;
	/// 随机游走目的地的最大水平距离，单位为格。
	private static final int MAXIMUM_IDLE_STROLL_DISTANCE = 6;
	/// 随机游走目的地搜索允许的垂直距离，单位为格。
	private static final int IDLE_STROLL_VERTICAL_DISTANCE = 1;
	/// 单次随机游走最多尝试生成目的地的次数。
	private static final int IDLE_STROLL_DESTINATION_ATTEMPTS = 8;
	/// 随机游走目的地的最小水平距离平方。
	private static final double MINIMUM_IDLE_STROLL_DISTANCE_SQUARED =
			MINIMUM_IDLE_STROLL_DISTANCE * MINIMUM_IDLE_STROLL_DISTANCE;
	/// 随机游走目的地的最大水平距离平方。
	private static final double MAXIMUM_IDLE_STROLL_DISTANCE_SQUARED =
			MAXIMUM_IDLE_STROLL_DISTANCE * MAXIMUM_IDLE_STROLL_DISTANCE;

	/// 闲置注视参数。
	/// 注视目标允许的最小转向角度。
	private static final int MINIMUM_LOOK_ANGLE = 45;
	/// 注视目标允许的最大转向角度。
	private static final int MAXIMUM_LOOK_ANGLE = 90;
	/// 闲置注视行为的最短持续时间，单位为游戏刻。
	private static final int MINIMUM_IDLE_LOOK_TICKS = TICKS_PER_SECOND;
	/// 闲置注视行为的最长持续时间，单位为游戏刻。
	private static final int MAXIMUM_IDLE_LOOK_TICKS = 2 * TICKS_PER_SECOND;
	/// 随机转向时允许的最大水平偏转角度。
	private static final float RANDOM_LOOK_MAXIMUM_YAW = 180.0F;
	/// 随机转向时允许的最小俯仰角度。
	private static final float RANDOM_LOOK_MINIMUM_PITCH = -30.0F;
	/// 随机转向时允许的最大俯仰角度。
	private static final float RANDOM_LOOK_MAXIMUM_PITCH = 30.0F;

	/// 闲置停止参数。
	/// 闲置停止行为的最短持续时间，单位为游戏刻。
	private static final int MINIMUM_IDLE_WAIT_TICKS = TICKS_PER_SECOND;
	/// 闲置停止行为的最长持续时间，单位为游戏刻。
	private static final int MAXIMUM_IDLE_WAIT_TICKS = 2 * TICKS_PER_SECOND;

	/// 闲置行为选择权重。
	/// 随机游走行为在 RunOne 中的选择权重。
	private static final int IDLE_STROLL_WEIGHT = 4;
	/// 注视生物行为在 RunOne 中的选择权重。
	private static final int IDLE_LOOK_ENTITY_WEIGHT = 1;
	/// 随机看向方向行为在 RunOne 中的选择权重。
	private static final int IDLE_LOOK_DIRECTION_WEIGHT = 1;
	/// 停止行为在 RunOne 中的选择权重。
	private static final int IDLE_STOP_WEIGHT = 1;
	private static final Brain.Provider<TheQueenOfHatred> BRAIN_PROVIDER =
			BrainUtil.provider(TheQueenOfHatredAi::getActivities)
					.addMemoryTypes(
							MemoryModuleType.LOOK_TARGET,
							MemoryModuleType.WALK_TARGET,
							MemoryModuleType.ATTACK_TARGET,
							MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
							MemoryModuleType.HURT_BY
					)
					.addSensorTypes(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY)
					.build();

	private TheQueenOfHatredAi() {
	}

	/// 创建并初始化女皇的 Brain。
	public static Brain<TheQueenOfHatred> makeBrain(TheQueenOfHatred queen, Brain.Packed packedBrain) {
		return BRAIN_PROVIDER.makeBrain(queen, packedBrain);
	}

	static void updateActivity(TheQueenOfHatred queen) {
		queen.getBrain().setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
		if (queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			queen.cancelSitting();
			queen.cancelSittingEdgeApproach();
		}
		if (queen.isSittingOrFadingOut()) {
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
		}
	}

	static void tick(TheQueenOfHatred queen) {
		long gameTime = queen.level().getGameTime();
		if (queen.tickSitting(gameTime, SITTING_FADE_OUT_TICKS)) {
			queen.scheduleNextIdleAction(Mth.nextInt(queen.getRandom(),
					MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			return;
		}
		if (queen.isSittingOrFadingOut()
				|| queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			return;
		}
		if (queen.isApproachingSittingEdge()) {
			if (queen.getSittingGround() == TheQueenOfHatred.SittingGround.EDGE) {
				queen.startSitting(Mth.nextInt(queen.getRandom(),
						MINIMUM_SITTING_DURATION_TICKS, MAXIMUM_SITTING_DURATION_TICKS), true);
			} else if (!queen.tickSittingEdgeApproach()) {
				queen.scheduleNextIdleAction(Mth.nextInt(queen.getRandom(),
						MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			}
			return;
		}
		if (queen.nextIdleActionGameTime() == 0) {
			queen.scheduleNextIdleAction(Mth.nextInt(queen.getRandom(),
					MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			return;
		}
		if (gameTime < queen.nextIdleActionGameTime()) {
			return;
		}
		TheQueenOfHatred.SittingGround sittingGround = queen.getSittingGround();
		if (sittingGround == TheQueenOfHatred.SittingGround.UNSAFE) {
			queen.scheduleNextIdleAction(Mth.nextInt(queen.getRandom(),
					MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			return;
		}
		if (sittingGround == TheQueenOfHatred.SittingGround.EDGE) {
			queen.startSitting(Mth.nextInt(queen.getRandom(),
					MINIMUM_SITTING_DURATION_TICKS, MAXIMUM_SITTING_DURATION_TICKS), true);
			return;
		}
		if (queen.tryApproachNearbySittingEdge(MAXIMUM_IDLE_STROLL_DISTANCE, IDLE_STROLL_SPEED)) {
			return;
		}
		queen.startSitting(Mth.nextInt(queen.getRandom(),
				MINIMUM_SITTING_DURATION_TICKS, MAXIMUM_SITTING_DURATION_TICKS),
				false);
	}

	private static List<ActivityData<TheQueenOfHatred>> getActivities(TheQueenOfHatred owner) {
		return List.of(
				ActivityData.create(Activity.CORE, CORE_ACTIVITY_PRIORITY, ImmutableList.of(
						new LookAtTargetSink(MINIMUM_LOOK_ANGLE, MAXIMUM_LOOK_ANGLE),
						new MoveToTargetSink()
				)), 
				ActivityData.create(Activity.IDLE, IDLE_ACTIVITY_PRIORITY, ImmutableList.of(
						StartAttacking.create((level, queen) -> queen.getBrain()
								.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
								.orElse(NearestVisibleLivingEntities.empty())
								.findClosest(queen::isHatedTarget)),
						new RunOne<>(ImmutableList.of(
								Pair.of(walkToRandomDestination(), IDLE_STROLL_WEIGHT),
								Pair.of(lookAtRandomLivingEntity(), IDLE_LOOK_ENTITY_WEIGHT),
								Pair.of(lookInRandomDirection(), IDLE_LOOK_DIRECTION_WEIGHT),
								Pair.of(new RandomStopAndWaitBehavior(), IDLE_STOP_WEIGHT)
						))
				)),
				ActivityData.create(Activity.FIGHT, ImmutableList.of(
						Pair.of(FIGHT_TARGET_CHECK_PRIORITY, StopAttackingIfTargetInvalid.create()),
						Pair.of(FIGHT_SKILL_PRIORITY, performRepel())
					), Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
						Set.of(MemoryModuleType.ATTACK_TARGET))
		);
	}

	private static OneShot<TheQueenOfHatred> performRepel() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, target -> (level, queen, time) -> {
			if (EntitySkillManager.hasActiveSkills(queen) || !queen.canCastSkillNow()) {
				return false;
			}
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
			return EntitySkillManager.cast(queen, TheQueenOfHatredSkills.REPEL.get());
		}));
	}

	private static OneShot<TheQueenOfHatred> walkToRandomDestination() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.absent(MemoryModuleType.WALK_TARGET)
		).apply(instance, walkTarget -> (level, queen, time) -> {
			if (queen.isSittingOrFadingOut() || queen.isApproachingSittingEdge()
					|| !queen.canStartIdleStroll()) {
				return false;
			}
			Vec3 destination = null;
			for (int attempt = 0; attempt < IDLE_STROLL_DESTINATION_ATTEMPTS; attempt++) {
				Vec3 candidate = LandRandomPos.getPos(
						queen, MAXIMUM_IDLE_STROLL_DISTANCE, IDLE_STROLL_VERTICAL_DISTANCE);
				if (candidate == null) {
					continue;
				}
				double distanceSquared = queen.position().subtract(candidate).horizontalDistanceSqr();
				if (distanceSquared >= MINIMUM_IDLE_STROLL_DISTANCE_SQUARED
						&& distanceSquared <= MAXIMUM_IDLE_STROLL_DISTANCE_SQUARED) {
					destination = candidate;
					break;
				}
			}
			if (destination == null) {
				return false;
			}
			walkTarget.set(new WalkTarget(destination, IDLE_STROLL_SPEED, 0));
			queen.delayNextIdleStroll(IDLE_STROLL_INTERVAL_TICKS);
			return true;
		}));
	}

	private static OneShot<TheQueenOfHatred> lookAtRandomLivingEntity() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.absent(MemoryModuleType.LOOK_TARGET),
				instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
		).apply(instance, (lookTarget, nearestEntities) -> (level, queen, time) -> {
			List<LivingEntity> candidates = instance.get(nearestEntities)
					.find(candidate -> candidate != queen && candidate.isAlive())
					.toList();
			if (candidates.isEmpty()) {
				return false;
			}
			LivingEntity target = candidates.get(queen.getRandom().nextInt(candidates.size()));
			int duration = Mth.nextInt(queen.getRandom(), MINIMUM_IDLE_LOOK_TICKS, MAXIMUM_IDLE_LOOK_TICKS);
			lookTarget.setWithExpiry(new EntityTracker(target, true), duration);
			return true;
		}));
	}

	private static OneShot<TheQueenOfHatred> lookInRandomDirection() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.absent(MemoryModuleType.LOOK_TARGET)
		).apply(instance, lookTarget -> (level, queen, time) -> {
			float pitch = Mth.nextFloat(queen.getRandom(),
					RANDOM_LOOK_MINIMUM_PITCH, RANDOM_LOOK_MAXIMUM_PITCH);
			float yaw = Mth.wrapDegrees(queen.getYRot()
					+ Mth.nextFloat(queen.getRandom(), -RANDOM_LOOK_MAXIMUM_YAW, RANDOM_LOOK_MAXIMUM_YAW));
			Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
			int duration = Mth.nextInt(queen.getRandom(), MINIMUM_IDLE_LOOK_TICKS, MAXIMUM_IDLE_LOOK_TICKS);
			lookTarget.setWithExpiry(new BlockPosTracker(queen.getEyePosition().add(direction)), duration);
			return true;
		}));
	}

	private static final class RandomStopAndWaitBehavior extends Behavior<TheQueenOfHatred> {
		private long waitEndTimestamp;

		private RandomStopAndWaitBehavior() {
			super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), MAXIMUM_IDLE_WAIT_TICKS);
		}

		@Override
		protected void start(ServerLevel level, TheQueenOfHatred queen, long timestamp) {
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
			int duration = Mth.nextInt(queen.getRandom(), MINIMUM_IDLE_WAIT_TICKS, MAXIMUM_IDLE_WAIT_TICKS);
			waitEndTimestamp = timestamp + duration;
		}

		@Override
		protected boolean canStillUse(ServerLevel level, TheQueenOfHatred queen, long timestamp) {
			return timestamp < waitEndTimestamp;
		}
	}
}
