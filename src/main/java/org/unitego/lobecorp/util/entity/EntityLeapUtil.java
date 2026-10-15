package org.unitego.lobecorp.util.entity;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// 求解先位移、后扣重力且不受阻力影响的离散飞跃；不读取或修改实体与世界。
public class EntityLeapUtil {
	/// 弧高边界及候选高度差比较的浮点误差容限，单位为格。
	private static final double HEIGHT_EPSILON = 1.0E-6;

	/// 弧高以起终点中较高的一端为基准；速度单位为格/tick，重力单位为格/tick²。
	/// 求解不裁剪初速度；无法在限定时间、弧高及速度内向上起跳并下降到终点时返回 null。
	public static @Nullable Trajectory solve(Vec3 displacement, double minimumArcHeight, double maximumArcHeight,
	                                         double desiredArcHeight, double gravity, double maxHorizontalSpeed,
	                                         double maxVerticalSpeed, int maxFlightTicks) {
		if (!Double.isFinite(displacement.x) || !Double.isFinite(displacement.y) || !Double.isFinite(displacement.z)
				|| !Double.isFinite(minimumArcHeight) || !Double.isFinite(maximumArcHeight)
				|| !Double.isFinite(desiredArcHeight) || !Double.isFinite(gravity)
				|| !Double.isFinite(maxHorizontalSpeed) || !Double.isFinite(maxVerticalSpeed)
				|| minimumArcHeight < 0.0 || maximumArcHeight < minimumArcHeight
				|| desiredArcHeight < minimumArcHeight || desiredArcHeight > maximumArcHeight
				|| gravity <= 0.0 || maxHorizontalSpeed <= 0.0 || maxVerticalSpeed <= 0.0 || maxFlightTicks < 2) {
			return null;
		}

		double horizontalDistance = Math.hypot(displacement.x, displacement.z);
		if (!Double.isFinite(horizontalDistance)) {
			return null;
		}

		Trajectory best = null;
		double bestError = Double.POSITIVE_INFINITY;
		for (long ticks = 2; ticks <= maxFlightTicks; ticks++) {
			double verticalSpeed = displacement.y / ticks + gravity * (ticks - 1) / 2.0;
			double horizontalSpeed = horizontalDistance / ticks;
			if (!Double.isFinite(verticalSpeed) || verticalSpeed <= 0.0 || verticalSpeed > maxVerticalSpeed
					|| horizontalSpeed > maxHorizontalSpeed || verticalSpeed - gravity * (ticks - 1) >= 0.0) {
				continue;
			}

			int peakTick = (int) Math.min(ticks, Math.ceil(verticalSpeed / gravity));
			double peakHeight = peakTick * verticalSpeed - gravity * peakTick * (peakTick - 1) / 2.0;
			double arcHeight = peakHeight - Math.max(0.0, displacement.y);
			if (!Double.isFinite(arcHeight) || arcHeight + HEIGHT_EPSILON < minimumArcHeight
					|| arcHeight - HEIGHT_EPSILON > maximumArcHeight) {
				continue;
			}

			double error = Math.abs(arcHeight - desiredArcHeight);
			if (error < bestError - HEIGHT_EPSILON) {
				best = new Trajectory(new Vec3(displacement.x / ticks, verticalSpeed, displacement.z / ticks), (int) ticks);
				bestError = error;
			}
		}

		return best;
	}

	public record Trajectory(Vec3 movement, int flightTicks) {
	}
}
