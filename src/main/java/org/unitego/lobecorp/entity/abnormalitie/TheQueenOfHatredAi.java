package org.unitego.lobecorp.entity.abnormalitie;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.entity_state.TheQueenOfHatredStates;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后的 Brain 活动与闲置行为。
public final class TheQueenOfHatredAi {
	private record SittingEdgeApproachTarget(Vec3 position, float facingYaw) {
	}

	enum SittingGround {
		UNSAFE,
		FLAT,
		EDGE
	}

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
	private long sittingEndGameTime;
	private long sittingFadeOutEndGameTime;
	private BlockPos sittingEdgePathTarget;
	private Vec3 sittingEdgeApproachTarget;
	private float sittingEdgeApproachSpeedModifier;
	private float sittingEdgeFacingYaw;
	private boolean hasSittingEdgeFacingYaw;
	private boolean sittingEdgeFacingLocked;
	private boolean sittingEdgeApproachingDirectly;

	TheQueenOfHatredAi() {
	}

	/// 创建并初始化女皇的 Brain。
	public static Brain<TheQueenOfHatred> makeBrain(TheQueenOfHatred queen, Brain.Packed packedBrain) {
		return BRAIN_PROVIDER.makeBrain(queen, packedBrain);
	}

	void onHurtBy(TheQueenOfHatred queen, LivingEntity attacker) {
		queen.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
		queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	boolean canStartIdleStroll(TheQueenOfHatred queen) {
		return queen.level().getGameTime() >= nextIdleStrollGameTime;
	}

	void delayNextIdleStroll(TheQueenOfHatred queen) {
		nextIdleStrollGameTime = queen.level().getGameTime() + IDLE_STROLL_INTERVAL_TICKS;
	}

	long nextIdleActionGameTime() {
		return nextIdleActionGameTime;
	}

	void scheduleNextIdleAction(TheQueenOfHatred queen, int delayTicks) {
		nextIdleActionGameTime = queen.level().getGameTime() + delayTicks;
	}

	private boolean canCastSkillNow(TheQueenOfHatred queen) {
		return queen.level().getGameTime() >= queen.skillCastAvailableAfterGameTime();
	}

	SittingGround getSittingGround(TheQueenOfHatred queen) {
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

	void applySittingEdgeFacing(TheQueenOfHatred queen) {
		queen.applySittingEdgeFacing(sittingEdgeFacingYaw);
	}

	float sittingEdgeFacingYaw() {
		return sittingEdgeFacingYaw;
	}

	boolean sittingEdgeFacingLocked() {
		return sittingEdgeFacingLocked;
	}

	boolean isApproachingSittingEdge() {
		return sittingEdgeApproachTarget != null;
	}

	boolean tryApproachNearbySittingEdge(TheQueenOfHatred queen) {
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

	boolean tickSittingEdgeApproach(TheQueenOfHatred queen) {
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

	void cancelSittingEdgeApproach(TheQueenOfHatred queen) {
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

	void startSitting(TheQueenOfHatred queen, int durationTicks, boolean onEdge) {
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

	boolean tickSitting(TheQueenOfHatred queen, long gameTime) {
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

	void cancelSitting(TheQueenOfHatred queen) {
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
		nextIdleActionGameTime = 0;
	}

	void updateActivity(TheQueenOfHatred queen) {
		queen.getBrain().setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
		if (queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			cancelSitting(queen);
			cancelSittingEdgeApproach(queen);
		}
		if (queen.isSittingOrFadingOut()) {
			queen.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			queen.getNavigation().stop();
		}
	}

	void tick(TheQueenOfHatred queen) {
		long gameTime = queen.level().getGameTime();
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
			if (EntitySkillManager.hasActiveSkills(queen) || !queen.ai().canCastSkillNow(queen)) {
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
			if (queen.isSittingOrFadingOut() || queen.ai().isApproachingSittingEdge()
				|| !queen.ai().canStartIdleStroll(queen)) {
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
