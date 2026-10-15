package org.unitego.lobecorp.util.animation;

import org.jspecify.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/// 在一次混合内延续相对旋转分支；调用方分别管理骨骼、阶段和播放修订的状态。
public class LcRotationTransitionUtil {
	/// 单位四元数虚部的退化误差，仅用于浮点精度下的整圈轴判断。
	private static final double AXIS_EPSILON_SQUARED = 1.0E-12;
	/// 相同旋转的 rotation vector 分支相差一个完整圆周，单位为弧度。
	private static final double FULL_TURN = Math.PI * 2;

	/// 纯计算，不修改输入四元数或上一份状态；权重端点直接返回对应输入方向。
	public static Sample interpolate(Quaternionf source, Quaternionf target, double weight, @Nullable State previous) {
		double progress = Math.clamp(weight, 0, 1);
		var start = new Quaternionf(source).normalize();
		var end = new Quaternionf(target).normalize();
		var relative = new Quaternionf(start).conjugate().mul(end).normalize();
		var lift = previous == null ? principal(relative) : continuous(relative, previous.lift);
		var correction = previous == null ? new Quaternionf() : new Quaternionf(previous.correction);
		double correctionWeight = previous == null ? progress : previous.correctionWeight;

		// 多轴路径在整圈处可能不存在连续对数；改用局部短增量续接当前输出，不累积历史曲线。
		if (previous != null && lift.distanceSquared(previous.lift) > Math.PI * Math.PI) {
			var increment = new Quaternionf(previous.relative).conjugate().mul(relative).normalize();
			var advanced = new Quaternionf(previous.weighted).mul(exponential(principal(increment), progress)).normalize();
			lift = principal(relative);
			correction = advanced.mul(exponential(lift, progress).conjugate()).normalize();
			correctionWeight = progress;
		}

		var weighted = exponential(principal(correction), correctionFactor(progress, correctionWeight))
				.mul(exponential(lift, progress)).normalize();
		if (progress <= 0) {
			weighted.identity();
		} else if (progress >= 1) {
			weighted.set(relative);
		}

		var state = new State(relative, lift, weighted, correction, correctionWeight);
		var rotation = progress <= 0 ? start : progress >= 1 ? end : start.mul(weighted).normalize();
		return new Sample(rotation, state);
	}

	/// 只延续四元数输出的等价 ZYX 分支，不用于改写资源或代码声明的原始 Euler 通道。
	public static Vector3f nearbyEuler(Quaternionf rotation, @Nullable Vector3f previous) {
		// 接近俯仰奇点时先用双精度归一化与分解，避免 float 误差被反三角函数放大。
		var angles = new Quaterniond(rotation).normalize().getEulerAnglesZYX(new Vector3d());
		if (previous == null) {
			return new Vector3f(angles);
		}

		var direct = nearby(angles.x, angles.y, angles.z, previous);
		var alternate = nearby(angles.x + Math.PI, Math.PI - angles.y, angles.z + Math.PI, previous);
		return direct.distanceSquared(previous) <= alternate.distanceSquared(previous) ? direct : alternate;
	}

	private static Vector3f nearby(double x, double y, double z, Vector3f previous) {
		return new Vector3f(nearest(x, previous.x), nearest(y, previous.y), nearest(z, previous.z));
	}

	private static float nearest(double angle, double previous) {
		return (float) (angle + Math.rint((previous - angle) / FULL_TURN) * FULL_TURN);
	}

	private static Vector3d principal(Quaternionf rotation) {
		double lengthSquared = imaginaryLengthSquared(rotation);
		if (lengthSquared <= AXIS_EPSILON_SQUARED) {
			return new Vector3d();
		}

		double length = Math.sqrt(lengthSquared);
		double angle = 2 * Math.atan2(length, rotation.w);
		if (angle > Math.PI) {
			angle -= FULL_TURN;
		}

		return new Vector3d(rotation.x, rotation.y, rotation.z).mul(angle / length);
	}

	private static Vector3d continuous(Quaternionf rotation, Vector3d previous) {
		double lengthSquared = imaginaryLengthSquared(rotation);
		if (lengthSquared <= AXIS_EPSILON_SQUARED) {
			double previousLength = previous.length();
			return previousLength == 0 ? new Vector3d()
					: new Vector3d(previous).mul(Math.rint(previousLength / FULL_TURN) * FULL_TURN / previousLength);
		}

		double length = Math.sqrt(lengthSquared);
		var axis = new Vector3d(rotation.x, rotation.y, rotation.z).div(length);
		double angle = 2 * Math.atan2(length, rotation.w);
		double turns = Math.rint((previous.dot(axis) - angle) / FULL_TURN);
		return axis.mul(angle + turns * FULL_TURN);
	}

	private static double imaginaryLengthSquared(Quaternionf rotation) {
		return (double) rotation.x * rotation.x + (double) rotation.y * rotation.y + (double) rotation.z * rotation.z;
	}

	private static Quaternionf exponential(Vector3d vector, double weight) {
		double length = vector.length();
		if (length == 0) {
			return new Quaternionf();
		}

		double halfAngle = length * weight / 2;
		double factor = Math.sin(halfAngle) / length;
		return new Quaternionf((float) (vector.x * factor), (float) (vector.y * factor),
				(float) (vector.z * factor), (float) Math.cos(halfAngle));
	}

	private static double correctionFactor(double weight, double originWeight) {
		if (weight <= 0 || weight >= 1) {
			return 0;
		}

		return weight >= originWeight ? (1 - weight) / (1 - originWeight) : weight / originWeight;
	}

	/// 固定数量的值快照，不持有上一份状态，也不向调用方暴露可变数学对象。
	public static class State {
		private final Quaternionf relative;
		private final Vector3d lift;
		private final Quaternionf weighted;
		private final Quaternionf correction;
		private final double correctionWeight;

		private State(Quaternionf relative, Vector3d lift, Quaternionf weighted, Quaternionf correction, double correctionWeight) {
			this.relative = new Quaternionf(relative);
			this.lift = new Vector3d(lift);
			this.weighted = new Quaternionf(weighted);
			this.correction = new Quaternionf(correction);
			this.correctionWeight = correctionWeight;
		}
	}

	public record Sample(Quaternionf rotation, State state) {
		public Sample {
			rotation = new Quaternionf(rotation);
		}

		@Override
		public Quaternionf rotation() {
			return new Quaternionf(rotation);
		}
	}
}
