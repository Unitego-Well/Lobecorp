package org.unitego.lobecorp.entity.ai.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

/// 只负责憎恶女皇的贴地悬浮、重力抵消和落地缓冲。
public final class TheQueenOfHatredHoverController {
	/// 常态悬浮时女皇脚底与地面的目标距离，单位为格。
	private static final double NORMAL_GROUND_CLEARANCE = 0.5;
	/// 悬浮时允许女皇脚底离开局部地面的最大距离，单位为格。
	private static final double MAXIMUM_GROUND_CLEARANCE = 2.5;
	/// 局部地面探测额外向上容差，单位为格。
	private static final double GROUND_SCAN_UPWARD_MARGIN = 1.0;
	/// 局部地面探测向下检查的最大距离，单位为格。
	private static final int GROUND_SCAN_DEPTH = 5;
	/// 垂直误差转换为速度修正的比例。
	private static final double VERTICAL_CORRECTION_FACTOR = 0.08;
	/// 单 tick 最大垂直修正速度，单位为格每 tick。
	private static final double MAXIMUM_VERTICAL_CORRECTION = 0.25;
	/// 局部探测不到地面时使用的保守下降速度，单位为格每 tick。
	private static final double GROUND_RECOVERY_DESCENT_SPEED = -0.2;
	/// 抵消实体常规重力的基础向上速度。
	private static final double GRAVITY_COMPENSATION = 0.08;
	/// 下落时提前开始限制落点速度的 tick 数。
	private static final int LANDING_PREDICTION_TICKS = 3;
	/// 判定实际位移进展的最小距离平方。
	private static final double MOVEMENT_PROGRESS_EPSILON = 0.0025;
	/// 地面高度比较使用的浮点容差。
	private static final double GROUND_EPSILON = 1.0E-4;
	/// 悬浮控制器局部地面扫描的性能分析区段。
	private static final String PROFILER_HOVER_GROUND_SCAN = "queenHoverGroundScan";

	private final TheQueenOfHatred queen;
	private Vec3 lastPosition;
	private int noProgressTicks;

	public TheQueenOfHatredHoverController(TheQueenOfHatred queen) {
		this.queen = queen;
	}

	public int noProgressTicks() {
		return noProgressTicks;
	}

	public void tick(double navigationTargetY, boolean moving) {
		if (!queen.isHovering()) {
			stop();
			return;
		}
		queen.resetFallDistance();
		updateProgress(moving);
		double groundY = findLocalGroundY();
		if (!Double.isFinite(groundY)) {
			// 只扫描女皇附近；高空或悬崖边找不到地面时主动下降，避免把“未知地面”误当成可长期悬停。
			Vec3 movement = queen.getDeltaMovement();
			queen.setDeltaMovement(movement.x, Math.min(movement.y, GROUND_RECOVERY_DESCENT_SPEED), movement.z);
			return;
		}
		double minimumY = groundY + NORMAL_GROUND_CLEARANCE;
		double maximumY = groundY + MAXIMUM_GROUND_CLEARANCE;
		double desiredY = moving && Double.isFinite(navigationTargetY)
				? Mth.clamp(navigationTargetY, minimumY, maximumY)
				: minimumY;
		Vec3 movement = queen.getDeltaMovement();
		double correction = Mth.clamp((desiredY - queen.getY()) * VERTICAL_CORRECTION_FACTOR,
				-MAXIMUM_VERTICAL_CORRECTION, MAXIMUM_VERTICAL_CORRECTION);
		double verticalMovement = correction < 0.0 ? correction : GRAVITY_COMPENSATION + correction;
		double predictedY = queen.getY() + verticalMovement * LANDING_PREDICTION_TICKS;
		if (verticalMovement < 0.0 && predictedY < minimumY) {
			verticalMovement = Math.max(verticalMovement,
					(minimumY - queen.getY()) / LANDING_PREDICTION_TICKS);
		}
		queen.setDeltaMovement(movement.x, verticalMovement, movement.z);
	}

	public void stop() {
		lastPosition = null;
		noProgressTicks = 0;
	}

	private void updateProgress(boolean moving) {
		Vec3 position = queen.position();
		if (!moving || lastPosition == null || position.distanceToSqr(lastPosition) > MOVEMENT_PROGRESS_EPSILON) {
			noProgressTicks = 0;
		} else {
			noProgressTicks++;
		}
		lastPosition = position;
	}

	private double findLocalGroundY() {
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_HOVER_GROUND_SCAN);
		try {
			BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
			int startY = Mth.floor(queen.getY() + GROUND_SCAN_UPWARD_MARGIN);
			int minimumY = Mth.floor(queen.getY() - GROUND_SCAN_DEPTH);
			for (int y = startY; y >= minimumY; y--) {
				position.set(queen.getBlockX(), y, queen.getBlockZ());
				VoxelShape shape =
						queen.level().getBlockState(position).getCollisionShape(queen.level(), position);
				if (!shape.isEmpty()) {
					double surfaceY = y + shape.max(Direction.Axis.Y);
					if (surfaceY <= queen.getY() + GROUND_SCAN_UPWARD_MARGIN + GROUND_EPSILON) {
						return surfaceY;
					}
				}
			}
			return Double.NaN;
		} finally {
			profiler.pop();
		}
	}
}
