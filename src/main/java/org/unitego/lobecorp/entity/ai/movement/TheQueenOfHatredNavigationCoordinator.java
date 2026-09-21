package org.unitego.lobecorp.entity.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

import java.util.Objects;

/// 统一维护憎恶女皇的移动意图、模式切换与后续导航执行入口。
public final class TheQueenOfHatredNavigationCoordinator {
	/// 导航执行使用的速度倍率。
	private static final double NAVIGATION_SPEED_MODIFIER = 1.0;
	/// 连续战斗移动目标之间的刷新间隔，单位为 tick。
	private static final int COMBAT_DESTINATION_REFRESH_TICKS = 10;
	/// 战斗移动预测目标位置的提前量，单位为 tick。
	private static final int COMBAT_TARGET_PREDICTION_TICKS = 5;
	/// 每次生成战斗位置时绕目标推进的水平角度。
	private static final double COMBAT_ORBIT_ANGLE = Math.PI / 4.0;
	/// 常态悬浮路径目标高出局部地面的距离，单位为格。
	private static final double NORMAL_GROUND_CLEARANCE = 0.5;
	/// 悬浮路径目标允许高出局部地面的最大距离，单位为格。
	private static final double MAXIMUM_GROUND_CLEARANCE = 2.5;
	/// 目标位置局部地面探测向上的容差，单位为格。
	private static final double GROUND_SCAN_UPWARD_MARGIN = 1.0;
	/// 目标位置局部地面探测向下检查的最大距离，单位为格。
	private static final int GROUND_SCAN_DEPTH = 5;
	/// 地面路径节点到达判定半径，单位为格。
	private static final int PATH_REACH_RANGE = 1;
	/// 闲置目标完成判定使用的距离平方，单位为平方格。
	private static final double IDLE_DESTINATION_REACHED_DISTANCE_SQUARED = 1.0;
	/// 水平向量退化时使用的最小长度平方。
	private static final double MINIMUM_DIRECTION_LENGTH_SQUARED = 1.0E-6;
	/// 战斗目的地计算的性能分析区段。
	private static final String PROFILER_COMBAT_DESTINATION = "queenCombatDestination";
	/// 战斗直线移动目标更新的性能分析区段。
	private static final String PROFILER_COMBAT_MOVE_UPDATE = "queenCombatMoveUpdate";
	/// 导航目的地局部地面扫描的性能分析区段。
	private static final String PROFILER_NAVIGATION_GROUND_SCAN = "queenNavigationGroundScan";

	private final TheQueenOfHatred queen;
	@Nullable
	private TheQueenOfHatredMovementIntent currentIntent;
	private long submittedGameTime;
	@Nullable
	private FailureReason lastFailureReason;
	private long lastFailureGameTime = Long.MIN_VALUE;
	private int destinationRefreshTicks;
	private int orbitDirection = 1;

	public TheQueenOfHatredNavigationCoordinator(TheQueenOfHatred queen) {
		this.queen = queen;
	}

	public boolean submit(TheQueenOfHatredMovementIntent intent) {
		if (queen.isUncontrolledFalling()) {
			return false;
		}
		if (currentIntent != null && sameIntent(currentIntent, intent)) {
			return true;
		}
		if (currentIntent != null && !isExpired()
				&& !intent.priority().canReplace(currentIntent.priority())) {
			return false;
		}
		currentIntent = intent;
		submittedGameTime = queen.level().getGameTime();
		destinationRefreshTicks = 0;
		updateMovementMode(intent);
		return true;
	}

	public void tick() {
		if (queen.isUncontrolledFalling()) {
			queen.stopNavigationMovement();
			clearIntent();
			return;
		}
		if (isExpired()) {
			queen.stopNavigationMovement();
			clearIntent();
		}
		if (currentIntent == null) {
			return;
		}
		switch (currentIntent.type()) {
			case IDLE_STROLL -> tickIdleStroll(currentIntent);
			case COMBAT_POSITION, THREE_DIMENSIONAL_PURSUIT -> tickCombatMovement(currentIntent);
			case LANDING -> tickLanding(currentIntent);
			default -> {
			}
		}
		if (queen.movementMode() == TheQueenOfHatredMovementMode.LANDING && queen.onGround()) {
			queen.setMovementMode(TheQueenOfHatredMovementMode.GROUND);
			queen.activateNavigation(TheQueenOfHatredMovementMode.GROUND);
			if (currentIntent != null && currentIntent.type() == TheQueenOfHatredMovementIntent.Type.LANDING
					&& !(queen.isDispelLanding()
					&& currentIntent.priority() == TheQueenOfHatredMovementIntent.Priority.FORCED_LANDING)) {
				clearIntent();
			}
		}
	}

	public void finishForcedLanding() {
		if (currentIntent != null
				&& currentIntent.priority() == TheQueenOfHatredMovementIntent.Priority.FORCED_LANDING) {
			clearIntent();
		}
	}

	public void beginIdleLanding() {
		if (currentIntent != null
				&& currentIntent.priority() == TheQueenOfHatredMovementIntent.Priority.FORCED_LANDING) {
			return;
		}
		clearIntent();
		submit(TheQueenOfHatredMovementIntent.landing(false));
	}

	public void submitIdle(TheQueenOfHatredMovementIntent intent) {
		if (currentIntent != null
				&& currentIntent.priority() == TheQueenOfHatredMovementIntent.Priority.FORCED_LANDING) {
			return;
		}
		clearIntent();
		submit(intent);
	}

	public void clearIntent() {
		currentIntent = null;
		destinationRefreshTicks = 0;
	}

	public void reportFailure(FailureReason reason) {
		lastFailureReason = reason;
		lastFailureGameTime = queen.level().getGameTime();
	}

	@Nullable
	public TheQueenOfHatredMovementIntent currentIntent() {
		return currentIntent;
	}

	@Nullable
	public FailureReason lastFailureReason() {
		return lastFailureReason;
	}

	public long lastFailureGameTime() {
		return lastFailureGameTime;
	}

	private boolean isExpired() {
		return currentIntent != null && currentIntent.hasTimeout()
				&& queen.level().getGameTime() - submittedGameTime >= currentIntent.timeoutTicks();
	}

	private boolean sameIntent(TheQueenOfHatredMovementIntent current,
			TheQueenOfHatredMovementIntent submitted) {
		return current.type() == submitted.type()
				&& current.priority() == submitted.priority()
				&& current.target() == submitted.target()
				&& Objects.equals(current.destination(), submitted.destination())
				&& current.minimumDistance() == submitted.minimumDistance()
				&& current.maximumDistance() == submitted.maximumDistance()
				&& current.timeoutTicks() == submitted.timeoutTicks();
	}

	private void updateMovementMode(TheQueenOfHatredMovementIntent intent) {
		TheQueenOfHatredMovementMode movementMode = switch (intent.type()) {
			case IDLE_STROLL -> TheQueenOfHatredMovementMode.GROUND;
			case THREE_DIMENSIONAL_PURSUIT -> TheQueenOfHatredMovementMode.THREE_DIMENSIONAL_PURSUIT;
			case LANDING -> TheQueenOfHatredMovementMode.LANDING;
			default -> TheQueenOfHatredMovementMode.HOVER;
		};
		queen.setMovementMode(movementMode);
		queen.activateNavigation(movementMode);
	}

	private void tickIdleStroll(TheQueenOfHatredMovementIntent intent) {
		Vec3 destination = intent.destination();
		if (destination == null) {
			clearIntent();
			return;
		}
		if (queen.distanceToSqr(destination) <= IDLE_DESTINATION_REACHED_DISTANCE_SQUARED) {
			clearIntent();
			return;
		}
		if (queen.getNavigation().isDone()) {
			if (!queen.getNavigation().moveTo(destination.x, destination.y, destination.z,
					PATH_REACH_RANGE, NAVIGATION_SPEED_MODIFIER)) {
				reportFailure(FailureReason.PATH_NOT_FOUND);
			}
		}
	}

	private void tickCombatMovement(TheQueenOfHatredMovementIntent intent) {
		LivingEntity target = intent.target();
		if (target == null || !queen.isValidTarget(target)) {
			queen.stopNavigationMovement();
			clearIntent();
			return;
		}
		if (destinationRefreshTicks > 0) {
			destinationRefreshTicks--;
			return;
		}
		if (queen.navigationNoProgressTicks() >= COMBAT_DESTINATION_REFRESH_TICKS) {
			orbitDirection = -orbitDirection;
			reportFailure(FailureReason.NO_PROGRESS);
		}
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_COMBAT_DESTINATION);
		Vec3 destination = combatDestination(target, intent.minimumDistance(), intent.maximumDistance());
		profiler.pop();
		profiler.push(PROFILER_COMBAT_MOVE_UPDATE);
		Vec3 safeDestination = queen.findRepositionDestination(destination,
				queen.position().distanceTo(destination), true);
		if (safeDestination == null) {
			reportFailure(FailureReason.PATH_NOT_FOUND);
			queen.requestBlockedCombatReposition(target);
			queen.stopNavigationMovement();
		} else {
			queen.getMoveControl().setWantedPosition(safeDestination.x, safeDestination.y,
					safeDestination.z, NAVIGATION_SPEED_MODIFIER);
		}
		profiler.pop();
		destinationRefreshTicks = COMBAT_DESTINATION_REFRESH_TICKS;
	}

	private void tickLanding(TheQueenOfHatredMovementIntent intent) {
		queen.setNoGravity(false);
		if (queen.onGround()) {
			queen.stopNavigationMovement();
			return;
		}
		Vec3 destination = intent.destination();
		if (destination == null) {
			queen.stopNavigationMovement();
			return;
		}
		if (queen.getNavigation().isDone()) {
			if (!queen.getNavigation().moveTo(destination.x, destination.y, destination.z,
					PATH_REACH_RANGE, NAVIGATION_SPEED_MODIFIER)) {
				reportFailure(FailureReason.PATH_NOT_FOUND);
			}
		}
	}

	private Vec3 combatDestination(LivingEntity target, double minimumDistance, double maximumDistance) {
		Vec3 targetPosition = queen.predictTargetPosition(target, COMBAT_TARGET_PREDICTION_TICKS);
		Vec3 away = new Vec3(queen.getX() - targetPosition.x, 0.0, queen.getZ() - targetPosition.z);
		if (away.lengthSqr() < MINIMUM_DIRECTION_LENGTH_SQUARED) {
			float yaw = queen.getYRot() * Mth.DEG_TO_RAD;
			away = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		}
		double currentDistance = away.length();
		double desiredDistance = Mth.clamp(currentDistance, minimumDistance, maximumDistance);
		if (currentDistance >= minimumDistance && currentDistance <= maximumDistance) {
			double angle = COMBAT_ORBIT_ANGLE * orbitDirection;
			double cosine = Math.cos(angle);
			double sine = Math.sin(angle);
			away = new Vec3(away.x * cosine - away.z * sine, 0.0,
					away.x * sine + away.z * cosine);
		}
		Vec3 horizontalDirection = away.normalize();
		double destinationX = targetPosition.x + horizontalDirection.x * desiredDistance;
		double destinationZ = targetPosition.z + horizontalDirection.z * desiredDistance;
		double groundY = findLocalGroundY(destinationX, targetPosition.y, destinationZ);
		double minimumY = groundY + NORMAL_GROUND_CLEARANCE;
		double destinationY = Mth.clamp(targetPosition.y, minimumY,
				groundY + MAXIMUM_GROUND_CLEARANCE);
		return new Vec3(destinationX, destinationY, destinationZ);
	}

	private double findLocalGroundY(double x, double referenceY, double z) {
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_NAVIGATION_GROUND_SCAN);
		try {
			BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
			int startY = Mth.floor(referenceY + GROUND_SCAN_UPWARD_MARGIN);
			int minimumY = Mth.floor(referenceY - GROUND_SCAN_DEPTH);
			for (int y = startY; y >= minimumY; y--) {
				position.set(Mth.floor(x), y, Mth.floor(z));
				VoxelShape shape = queen.level().getBlockState(position).getCollisionShape(queen.level(), position);
				if (!shape.isEmpty()) {
					return y + shape.max(Direction.Axis.Y);
				}
			}
			// 有界扫描失败时分段降低目标高度，交由悬浮控制器继续下降，不能沿用高空目标高度。
			return Math.min(referenceY, queen.getY()) - MAXIMUM_GROUND_CLEARANCE;
		} finally {
			profiler.pop();
		}
	}

	public enum FailureReason {
		PATH_NOT_FOUND,
		NO_PROGRESS
	}
}
