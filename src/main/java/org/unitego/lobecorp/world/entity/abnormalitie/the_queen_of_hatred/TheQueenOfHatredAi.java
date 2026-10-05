package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.entity.ai.BrainUtil;
import org.unitego.lobecorp.world.entity.skill.EntitySkillCastRequest;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.SweepSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarBeamSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.TeleportSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.RefractionSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.ConvergentSkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.registry.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.entity.state.TheQueenOfHatredStates;
import org.unitego.lobecorp.registry.effect.LcMobEffects;

import java.util.HashMap;
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
	/// AI 选择横扫的最大距离，单位为格。
	private static final double SWEEP_SELECTION_RANGE = SweepSkill.MELEE_RANGE;
	/// 近身围攻时优先回旋所需的有效敌人数。
	private static final int SPIN_DEFENSE_TARGET_COUNT = 2;
	/// 吸引范围内至少三个有效敌人时优先聚爆。
	private static final int CONVERGENT_TARGET_COUNT = 3;
	/// AI 传送落点距目标的水平距离，单位为格，落点位于横扫范围内。
	private static final double TELEPORT_TARGET_OFFSET = 3.0;
	/// 围绕目标搜索安全传送落点的方向数。
	private static final int TELEPORT_DESTINATION_DIRECTIONS = 8;
	/// 传送落点允许与目标脚下相差的高度，单位为格。
	private static final int TELEPORT_VERTICAL_SEARCH_DISTANCE = 2;
	/// 闲置随机游走参数。
	/// 随机游走的速度倍率。
	private static final float IDLE_STROLL_SPEED = 1.0F;
	/// 闲置随机游走的最短触发间隔，单位为游戏刻。
	private static final int IDLE_STROLL_INTERVAL_TICKS = 10 * TICKS_PER_SECOND;
	/// 女皇随机动作选择的最短间隔，单位为游戏刻。
	private static final int MINIMUM_IDLE_ACTION_INTERVAL_TICKS = 20 * TICKS_PER_SECOND;
	/// 女皇随机动作选择的最长间隔，单位为游戏刻。
	private static final int MAXIMUM_IDLE_ACTION_INTERVAL_TICKS = 40 * TICKS_PER_SECOND;
	/// rest 一次性动画的时长，资源中为 2.75 秒，单位为游戏刻。
	private static final int REST_DURATION_TICKS = 55;
	/// toss 一次性动画的时长，资源中为 2 秒，单位为游戏刻。
	private static final int TOSS_DURATION_TICKS = 2 * TICKS_PER_SECOND;
	/// 坐下动作的最短持续时间，单位为游戏刻。
	private static final int MINIMUM_SITTING_DURATION_TICKS = 60 * TICKS_PER_SECOND;
	/// 坐下动作的最长持续时间，单位为游戏刻。
	private static final int MAXIMUM_SITTING_DURATION_TICKS = 120 * TICKS_PER_SECOND;
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
	/// 判断平整地面时使用的高度误差。
	private static final double GROUND_CHECK_EPSILON = 1.0E-5D;
	/// 坐到边缘时允许身体超出支撑面的距离。
	private static final double SITTING_EDGE_OVERHANG = 0.1D;
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
	private static final int IDLE_STROLL_WEIGHT = 40;
	/// 注视生物行为在 RunOne 中的选择权重。
	private static final int IDLE_LOOK_ENTITY_WEIGHT = 15;
	/// 随机看向方向行为在 RunOne 中的选择权重。
	private static final int IDLE_LOOK_DIRECTION_WEIGHT = 3;
	/// 停止行为在 RunOne 中的选择权重。
	private static final int IDLE_STOP_WEIGHT = 10;
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
	private long nextIdleStrollGameTime;
	private long nextIdleActionGameTime;
	private long idleAnimationEndGameTime;
	private long sittingEndGameTime;
	private long sittingFadeOutEndGameTime;
	private BlockPos sittingEdgePathTarget;
	private Vec3 sittingEdgeApproachTarget;
	private float sittingEdgeApproachSpeedModifier;
	private float sittingEdgeFacingYaw;
	private boolean hasSittingEdgeFacingYaw;
	private boolean sittingEdgeFacingLocked;
	private boolean sittingEdgeApproachingDirectly;

	protected TheQueenOfHatredAi() {
	}

	/// 创建并初始化女皇的 Brain。
	public static Brain<TheQueenOfHatred> makeBrain(TheQueenOfHatred queen, Brain.Packed packedBrain) {
		return BRAIN_PROVIDER.makeBrain(queen, packedBrain);
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
								Pair.of(FIGHT_SKILL_PRIORITY, performCombatSkill())
						), Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
						Set.of(MemoryModuleType.ATTACK_TARGET))
		);
	}

	private static OneShot<TheQueenOfHatred> performCombatSkill() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.present(MemoryModuleType.ATTACK_TARGET)
		).apply(instance, target -> (level, queen, time) -> {
			queen.cancelConductorSitting();
			if (EntitySkillUtil.require(queen).activeSkills().stream().anyMatch(runtime -> !runtime.isInterruptibleBySkill())) {
				return false;
			}
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
			LivingEntity attackTarget = instance.get(target);
			if (EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.PURIFICATION.get())) {
				return EntitySkillUtil.cast(queen, TheQueenOfHatredSkills.PURIFICATION.get()).started();
			}
			if (!queen.hasEffect(LcMobEffects.QUEEN_DAMAGE_REDUCTION)
					&& EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.DAMAGE_REDUCTION.get())) {
				return EntitySkillUtil.cast(queen, TheQueenOfHatredSkills.DAMAGE_REDUCTION.get()).started();
			}
			if (EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.CONVERGENT.get())
					&& level.getEntitiesOfClass(LivingEntity.class, queen.getBoundingBox().inflate(ConvergentSkill.PULL_RADIUS),
							candidate -> candidate.isAlive() && queen.isHatedTarget(candidate) && queen.hasLineOfSight(candidate)
									&& queen.distanceToSqr(candidate) <= ConvergentSkill.PULL_RADIUS * ConvergentSkill.PULL_RADIUS)
					.size() >= CONVERGENT_TARGET_COUNT) {
				return EntitySkillUtil.cast(queen, TheQueenOfHatredSkills.CONVERGENT.get()).started();
			}
			if (queen.distanceTo(attackTarget) <= SWEEP_SELECTION_RANGE) {
				boolean spinAvailable = !EntitySkillUtil.isOnCooldown(queen, TheQueenOfHatredSkills.SPIN.get());
				if (spinAvailable && level.getEntitiesOfClass(LivingEntity.class,
								queen.getBoundingBox().inflate(SWEEP_SELECTION_RANGE), candidate -> candidate.isAlive()
										&& queen.isHatedTarget(candidate) && queen.hasLineOfSight(candidate)
										&& queen.distanceToSqr(candidate) <= SWEEP_SELECTION_RANGE * SWEEP_SELECTION_RANGE)
						.size() >= SPIN_DEFENSE_TARGET_COUNT) {
					return EntitySkillUtil.cast(queen, TheQueenOfHatredSkills.SPIN.get()).started();
				}
				if (!EntitySkillUtil.isOnCooldown(queen, TheQueenOfHatredSkills.SWEEP.get())) {
					return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
							TheQueenOfHatredSkills.SWEEP.get(), attackTarget, null)).started();
				}
				if (spinAvailable) {
					return EntitySkillUtil.cast(queen, TheQueenOfHatredSkills.SPIN.get()).started();
				}
				return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
						TheQueenOfHatredSkills.ATTACK.get(), attackTarget, null)).started();
			}
			for (var skill : List.of(TheQueenOfHatredSkills.MARK.get(), TheQueenOfHatredSkills.SLOWNESS.get(),
					TheQueenOfHatredSkills.PILLAR_OF_LIGHT.get(), TheQueenOfHatredSkills.STARFALL.get(), TheQueenOfHatredSkills.LASER.get())) {
				if (EntitySkillUtil.canCast(queen, skill)) {
					return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(skill, attackTarget, null)).started();
				}
			}
			if (queen.distanceToSqr(attackTarget) <= StarBeamSkill.RANGE * StarBeamSkill.RANGE
					&& EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.STAR_BEAM.get())) {
				return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
						TheQueenOfHatredSkills.STAR_BEAM.get(), attackTarget, null)).started();
			}
			if (queen.distanceToSqr(attackTarget) <= RefractionSkill.range() * RefractionSkill.range()
					&& EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.REFRACTION.get())) {
				return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
						TheQueenOfHatredSkills.REFRACTION.get(), attackTarget, null)).started();
			}
			if (EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.DASH.get())) {
				return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
						TheQueenOfHatredSkills.DASH.get(), attackTarget, null)).started();
			}
			if (EntitySkillUtil.canCast(queen, TheQueenOfHatredSkills.BLINK.get())) {
				return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
						TheQueenOfHatredSkills.BLINK.get(), attackTarget, null)).started();
			}
			if (!EntitySkillUtil.isOnCooldown(queen, TheQueenOfHatredSkills.TELEPORT.get())) {
				Vec3 destination = teleportDestination(queen, attackTarget);
				if (destination != null)
					return EntitySkillUtil.cast(queen, new EntitySkillCastRequest<>(
							TheQueenOfHatredSkills.TELEPORT.get(), null, destination)).started();
			}
			return EntitySkillUtil.cast(queen, TheQueenOfHatredSkills.REPEL.get()).started();
		}));
	}

	@Nullable
	private static Vec3 teleportDestination(TheQueenOfHatred queen, LivingEntity target) {
		Vec3 offset = queen.position().subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize();
		if (offset.lengthSqr() == 0.0)
			offset = Vec3.directionFromRotation(0.0F, queen.getYRot());
		for (int direction = 0; direction < TELEPORT_DESTINATION_DIRECTIONS; direction++) {
			Vec3 horizontal = offset.yRot((float) (direction * Math.TAU / TELEPORT_DESTINATION_DIRECTIONS))
					.scale(TELEPORT_TARGET_OFFSET);
			for (int height = -TELEPORT_VERTICAL_SEARCH_DISTANCE; height <= TELEPORT_VERTICAL_SEARCH_DISTANCE; height++) {
				Vec3 destination = target.position().add(horizontal).add(0.0, height, 0.0);
				if (TeleportSkill.isSafeDestination(queen, destination))
					return destination;
			}
		}
		return null;
	}

	private static OneShot<TheQueenOfHatred> walkToRandomDestination() {
		return BehaviorBuilder.create(instance -> instance.group(
				instance.absent(MemoryModuleType.WALK_TARGET)
		).apply(instance, walkTarget -> (level, queen, time) -> {
			if (queen.isSittingOrFadingOut() || queen.ai().isApproachingSittingEdge()
					|| queen.ai().idleAnimationEndGameTime != 0 || !queen.ai().canStartIdleStroll(queen)) {
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
			queen.ai().delayNextIdleStroll(queen);
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

	protected void onHurtBy(TheQueenOfHatred queen, LivingEntity attacker) {
		queen.cancelConductorSitting();
		queen.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
		queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	private boolean canStartIdleStroll(TheQueenOfHatred queen) {
		return queen.level().getGameTime() >= nextIdleStrollGameTime;
	}

	private void delayNextIdleStroll(TheQueenOfHatred queen) {
		nextIdleStrollGameTime = queen.level().getGameTime() + IDLE_STROLL_INTERVAL_TICKS;
	}

	private long nextIdleActionGameTime() {
		return nextIdleActionGameTime;
	}

	private void scheduleNextIdleAction(TheQueenOfHatred queen, int delayTicks) {
		nextIdleActionGameTime = queen.level().getGameTime() + delayTicks;
	}

	private SittingGround getSittingGround(TheQueenOfHatred queen) {
		if (!queen.onGround() || queen.isOnFire()) {
			return SittingGround.UNSAFE;
		}
		return getSittingGround(queen, queen.getBoundingBox());
	}

	private SittingGround getSittingGround(TheQueenOfHatred queen, AABB bounds) {
		int minX = Mth.floor(bounds.minX + GROUND_CHECK_EPSILON);
		int maxX = Mth.floor(bounds.maxX - GROUND_CHECK_EPSILON);
		int minZ = Mth.floor(bounds.minZ + GROUND_CHECK_EPSILON);
		int maxZ = Mth.floor(bounds.maxZ - GROUND_CHECK_EPSILON);
		int supportY = Mth.floor(bounds.minY - GROUND_CHECK_EPSILON);
		double supportTop = Double.NaN;
		int supportedBlocks = 0;
		int unsupportedBlocks = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos supportPos = new BlockPos(x, supportY, z);
				BlockState supportState = queen.level().getBlockState(supportPos);
				if (!supportState.getFluidState().isEmpty()
						|| supportState.is(BlockTags.FIRE)
						|| supportState.is(Blocks.LAVA)) {
					return SittingGround.UNSAFE;
				}
				VoxelShape shape = supportState.getCollisionShape(queen.level(), supportPos);
				if (!supportState.isFaceSturdy(queen.level(), supportPos, Direction.UP)) {
					if (!shape.isEmpty()) {
						return SittingGround.UNSAFE;
					}
					unsupportedBlocks++;
					continue;
				}
				double top = supportPos.getY() + shape.max(Direction.Axis.Y);
				if (Double.isNaN(supportTop)) {
					supportTop = top;
				} else if (Math.abs(supportTop - top) > GROUND_CHECK_EPSILON) {
					return SittingGround.UNSAFE;
				}
				supportedBlocks++;
			}
		}
		if (supportedBlocks == 0 || Math.abs(supportTop - bounds.minY) > GROUND_CHECK_EPSILON) {
			return SittingGround.UNSAFE;
		}
		for (BlockPos blockPos : BlockPos.betweenClosed(
				Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
				Mth.floor(bounds.maxX - GROUND_CHECK_EPSILON),
				Mth.floor(bounds.maxY - GROUND_CHECK_EPSILON),
				Mth.floor(bounds.maxZ - GROUND_CHECK_EPSILON))) {
			BlockState state = queen.level().getBlockState(blockPos);
			if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)) {
				return SittingGround.UNSAFE;
			}
		}
		return unsupportedBlocks == 0 ? SittingGround.FLAT : SittingGround.EDGE;
	}

	private float getSittingEdgeFacingYaw(TheQueenOfHatred queen) {
		AABB bounds = queen.getBoundingBox();
		int minX = Mth.floor(bounds.minX + GROUND_CHECK_EPSILON);
		int maxX = Mth.floor(bounds.maxX - GROUND_CHECK_EPSILON);
		int minZ = Mth.floor(bounds.minZ + GROUND_CHECK_EPSILON);
		int maxZ = Mth.floor(bounds.maxZ - GROUND_CHECK_EPSILON);
		int supportY = Mth.floor(bounds.minY - GROUND_CHECK_EPSILON);
		double edgeDirectionX = 0.0D;
		double edgeDirectionZ = 0.0D;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos supportPos = new BlockPos(x, supportY, z);
				BlockState supportState = queen.level().getBlockState(supportPos);
				if (supportState.getCollisionShape(queen.level(), supportPos).isEmpty()) {
					edgeDirectionX += x + 0.5D - queen.getX();
					edgeDirectionZ += z + 0.5D - queen.getZ();
				}
			}
		}
		return edgeDirectionX == 0.0D && edgeDirectionZ == 0.0D
				? queen.getYRot()
				: Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-edgeDirectionX, edgeDirectionZ)));
	}

	protected void applySittingEdgeFacing(TheQueenOfHatred queen) {
		queen.applySittingEdgeFacing(sittingEdgeFacingYaw);
	}

	protected float sittingEdgeFacingYaw() {
		return sittingEdgeFacingYaw;
	}

	protected boolean sittingEdgeFacingLocked() {
		return sittingEdgeFacingLocked;
	}

	private boolean isApproachingSittingEdge() {
		return sittingEdgeApproachTarget != null;
	}

	private boolean tryApproachNearbySittingEdge(TheQueenOfHatred queen) {
		AABB bounds = queen.getBoundingBox();
		int supportY = Mth.floor(bounds.minY - GROUND_CHECK_EPSILON);
		double supportTop = bounds.minY;
		Map<BlockPos, SittingEdgeApproachTarget> edgeApproachTargets = new HashMap<>();
		for (int x = queen.blockPosition().getX() - MAXIMUM_IDLE_STROLL_DISTANCE;
		     x <= queen.blockPosition().getX() + MAXIMUM_IDLE_STROLL_DISTANCE; x++) {
			for (int z = queen.blockPosition().getZ() - MAXIMUM_IDLE_STROLL_DISTANCE;
			     z <= queen.blockPosition().getZ() + MAXIMUM_IDLE_STROLL_DISTANCE; z++) {
				double deltaX = queen.getX() - (x + 0.5D);
				double deltaZ = queen.getZ() - (z + 0.5D);
				if (deltaX * deltaX + deltaZ * deltaZ
						> MAXIMUM_IDLE_STROLL_DISTANCE_SQUARED) {
					continue;
				}
				BlockPos supportPos = new BlockPos(x, supportY, z);
				BlockState supportState = queen.level().getBlockState(supportPos);
				if (!supportState.isFaceSturdy(queen.level(), supportPos, Direction.UP)
						|| !supportState.getFluidState().isEmpty()
						|| supportState.is(BlockTags.FIRE)
						|| supportState.is(Blocks.LAVA)
						|| Math.abs(supportPos.getY() + supportState.getCollisionShape(queen.level(), supportPos)
						.max(Direction.Axis.Y) - supportTop) > GROUND_CHECK_EPSILON) {
					continue;
				}
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					BlockPos neighborPos = supportPos.relative(direction);
					BlockState neighborState = queen.level().getBlockState(neighborPos);
					if (!neighborState.getCollisionShape(queen.level(), neighborPos).isEmpty()
							|| !neighborState.getFluidState().isEmpty()
							|| neighborState.is(BlockTags.FIRE)
							|| neighborState.is(Blocks.LAVA)) {
						continue;
					}
					double edgeX = x + 0.5D + direction.getStepX()
							* (0.5D - queen.getBbWidth() * 0.5D + SITTING_EDGE_OVERHANG);
					double edgeZ = z + 0.5D + direction.getStepZ()
							* (0.5D - queen.getBbWidth() * 0.5D + SITTING_EDGE_OVERHANG);
					AABB edgeBounds = bounds.move(edgeX - queen.getX(), 0.0D, edgeZ - queen.getZ());
					if (getSittingGround(queen, edgeBounds) != SittingGround.EDGE
							|| !queen.level().noCollision(queen, edgeBounds)) {
						continue;
					}
					BlockPos pathTarget = supportPos.above(Mth.floor(supportTop) - supportY);
					float facingYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(
							-direction.getStepX(), direction.getStepZ())));
					edgeApproachTargets.put(pathTarget,
							new SittingEdgeApproachTarget(new Vec3(edgeX, queen.getY(), edgeZ), facingYaw));
				}
			}
		}
		if (edgeApproachTargets.isEmpty()) {
			return false;
		}
		Path path = queen.getNavigation().createPath(edgeApproachTargets.keySet(), 0);
		if (path == null || !path.canReach()) {
			return false;
		}
		sittingEdgePathTarget = path.getTarget();
		SittingEdgeApproachTarget approachTarget = edgeApproachTargets.get(sittingEdgePathTarget);
		if (approachTarget == null) {
			sittingEdgePathTarget = null;
			return false;
		}
		sittingEdgeApproachTarget = approachTarget.position();
		sittingEdgeFacingYaw = approachTarget.facingYaw();
		hasSittingEdgeFacingYaw = true;
		sittingEdgeApproachSpeedModifier = IDLE_STROLL_SPEED;
		queen.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
				new WalkTarget(sittingEdgePathTarget, IDLE_STROLL_SPEED, 0));
		return true;
	}

	private boolean tickSittingEdgeApproach(TheQueenOfHatred queen) {
		if (sittingEdgeApproachTarget == null) {
			return false;
		}
		if (!sittingEdgeApproachingDirectly) {
			if (!queen.getNavigation().isDone()) {
				return true;
			}
			Vec3 pathTargetPosition = Vec3.atBottomCenterOf(sittingEdgePathTarget);
			if (queen.position().distanceToSqr(pathTargetPosition) > 0.25D) {
				if (queen.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
					return true;
				}
				cancelSittingEdgeApproach(queen);
				return false;
			}
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
			sittingEdgeApproachingDirectly = true;
		}
		if (!queen.onGround()) {
			cancelSittingEdgeApproach(queen);
			return false;
		}
		queen.getMoveControl().setWantedPosition(sittingEdgeApproachTarget.x, queen.getY(),
				sittingEdgeApproachTarget.z, sittingEdgeApproachSpeedModifier);
		return true;
	}

	private void cancelSittingEdgeApproach(TheQueenOfHatred queen) {
		if (!isApproachingSittingEdge()) {
			return;
		}
		sittingEdgePathTarget = null;
		sittingEdgeApproachTarget = null;
		sittingEdgeApproachSpeedModifier = 0.0F;
		sittingEdgeApproachingDirectly = false;
		if (!sittingEdgeFacingLocked) {
			hasSittingEdgeFacingYaw = false;
		}
		queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		queen.getNavigation().stop();
		queen.getMoveControl().setWait();
	}

	private void startSitting(TheQueenOfHatred queen, int durationTicks, boolean onEdge) {
		boolean hasApproachFacingYaw = hasSittingEdgeFacingYaw;
		float approachFacingYaw = sittingEdgeFacingYaw;
		cancelSittingEdgeApproach(queen);
		queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		queen.getNavigation().stop();
		queen.setDeltaMovement(Vec3.ZERO);
		sittingEndGameTime = queen.level().getGameTime() + durationTicks;
		if (onEdge) {
			sittingEdgeFacingYaw = hasApproachFacingYaw ? approachFacingYaw : getSittingEdgeFacingYaw(queen);
			hasSittingEdgeFacingYaw = true;
			sittingEdgeFacingLocked = true;
			queen.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
			applySittingEdgeFacing(queen);
			queen.addEntityState(TheQueenOfHatredStates.SITTING_EDGE);
		} else {
			sittingEdgeFacingLocked = false;
			hasSittingEdgeFacingYaw = false;
			queen.addEntityState(TheQueenOfHatredStates.SITTING);
		}
	}

	private boolean tickSitting(TheQueenOfHatred queen, long gameTime) {
		if (queen.isSitting() && gameTime >= sittingEndGameTime) {
			queen.addEntityState(queen.isSittingEdge()
					? TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT
					: TheQueenOfHatredStates.SITTING_FADE_OUT);
			sittingFadeOutEndGameTime = gameTime + TheQueenOfHatred.SITTING_FADE_OUT_TICKS;
			return false;
		}
		if (queen.isSittingFadeOut() && gameTime >= sittingFadeOutEndGameTime) {
			queen.removeEntityState(TheQueenOfHatredStates.SITTING_FADE_OUT);
			queen.removeEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT);
			sittingEndGameTime = 0;
			sittingFadeOutEndGameTime = 0;
			sittingEdgeFacingLocked = false;
			hasSittingEdgeFacingYaw = false;
			return true;
		}
		return false;
	}

	protected void cancelSitting(TheQueenOfHatred queen) {
		scheduleNextIdleAction(queen, Mth.nextInt(queen.getRandom(),
				MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
		if (idleAnimationEndGameTime != 0) {
			idleAnimationEndGameTime = 0;
			queen.stopActionAnimation();
		}
		cancelSittingEdgeApproach(queen);
		if (!queen.isSittingOrFadingOut()) {
			return;
		}
		queen.removeEntityState(TheQueenOfHatredStates.SITTING);
		queen.removeEntityState(TheQueenOfHatredStates.SITTING_EDGE);
		queen.removeEntityState(TheQueenOfHatredStates.SITTING_FADE_OUT);
		queen.removeEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT);
		sittingEndGameTime = 0;
		sittingFadeOutEndGameTime = 0;
		sittingEdgeFacingLocked = false;
		hasSittingEdgeFacingYaw = false;
	}

	protected void updateActivity(TheQueenOfHatred queen) {
		queen.getBrain().setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
		if (queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			cancelSitting(queen);
			cancelSittingEdgeApproach(queen);
		}
		if (queen.isSittingOrFadingOut() && !queen.getNavigation().isDone()) {
			cancelSitting(queen);
		}
		if (queen.isSittingOrFadingOut()) {
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
		}
	}

	protected void tick(TheQueenOfHatred queen) {
		long gameTime = queen.level().getGameTime();
		if (EntitySkillUtil.hasActiveSkills(queen)) {
			cancelSitting(queen);
			return;
		}
		if (idleAnimationEndGameTime != 0) {
			if (gameTime >= idleAnimationEndGameTime) {
				idleAnimationEndGameTime = 0;
				queen.stopActionAnimation();
				scheduleNextIdleAction(queen, Mth.nextInt(queen.getRandom(),
						MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			}
			return;
		}
		if (tickSitting(queen, gameTime)) {
			scheduleNextIdleAction(queen, Mth.nextInt(queen.getRandom(),
					MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			return;
		}
		if (queen.isSittingOrFadingOut()
				|| queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			return;
		}
		if (isApproachingSittingEdge()) {
			if (getSittingGround(queen) == SittingGround.EDGE) {
				startSitting(queen, Mth.nextInt(queen.getRandom(),
						MINIMUM_SITTING_DURATION_TICKS, MAXIMUM_SITTING_DURATION_TICKS), true);
			} else if (!tickSittingEdgeApproach(queen)) {
				scheduleNextIdleAction(queen, Mth.nextInt(queen.getRandom(),
						MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			}
			return;
		}
		if (nextIdleActionGameTime() == 0) {
			scheduleNextIdleAction(queen, Mth.nextInt(queen.getRandom(),
					MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			return;
		}
		if (gameTime < nextIdleActionGameTime()) {
			return;
		}
		SittingGround sittingGround = getSittingGround(queen);
		if (sittingGround == SittingGround.UNSAFE) {
			scheduleNextIdleAction(queen, Mth.nextInt(queen.getRandom(),
					MINIMUM_IDLE_ACTION_INTERVAL_TICKS, MAXIMUM_IDLE_ACTION_INTERVAL_TICKS));
			return;
		}
		IdleAction[] actions = IdleAction.values();
		IdleAction action = actions[queen.getRandom().nextInt(actions.length)];
		if (action != IdleAction.SIT) {
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
			queen.setDeltaMovement(Vec3.ZERO);
			queen.playActionAnimation(action == IdleAction.REST ? TheQueenOfHatredAnim.REST : TheQueenOfHatredAnim.TOSS);
			idleAnimationEndGameTime = gameTime + (action == IdleAction.REST ? REST_DURATION_TICKS : TOSS_DURATION_TICKS);
			return;
		}
		if (sittingGround == SittingGround.EDGE) {
			startSitting(queen, Mth.nextInt(queen.getRandom(),
					MINIMUM_SITTING_DURATION_TICKS, MAXIMUM_SITTING_DURATION_TICKS), true);
			return;
		}
		if (tryApproachNearbySittingEdge(queen)) {
			return;
		}
		startSitting(queen, Mth.nextInt(queen.getRandom(),
						MINIMUM_SITTING_DURATION_TICKS, MAXIMUM_SITTING_DURATION_TICKS),
				false);
	}

	private enum IdleAction {
		SIT,
		REST,
		TOSS
	}

	private enum SittingGround {
		UNSAFE,
		FLAT,
		EDGE
	}

	private record SittingEdgeApproachTarget(Vec3 position, float facingYaw) {
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
