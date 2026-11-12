package org.unitego.lobecorp.client.visual.sky;

import net.minecraft.world.phys.Vec3;

/// 世界空间天空覆盖场；不依赖事件、相机朝向或渲染资源。每个调用方独立持有平滑状态。
public class DirectionalSkyCoverage {
	private final Profile profile;
	private double yaw;
	private float coverage;
	public DirectionalSkyCoverage(Profile profile) {
		this.profile = profile;
		reset(new Vec3(0, 0, 1));
	}

	private static Vec3 horizontal(Vec3 direction) {
		double length = Math.hypot(direction.x, direction.z);
		return length > 0.0001 ? new Vec3(direction.x / length, 0, direction.z / length) : new Vec3(0, 0, 1);
	}

	private static double smooth(double value) {
		double t = Math.clamp(value, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/// headCenter 为当前波头，bandCenter 为离观察者最近的已占据波带中心；方向和半宽属于该波带。
	/// 距离包含高度差，横向投影限制在波带半宽内；靠近边缘时全覆盖逐渐减弱。
	public Sample sample(Vec3 observer, Vec3 headCenter, Vec3 bandCenter, Vec3 bandForward, double halfWidth) {
		var forward = horizontal(bandForward);
		var side = new Vec3(-forward.z, 0, forward.x);
		var offset = observer.subtract(bandCenter);
		double width = Math.max(0, halfWidth);
		double lateral = offset.dot(side);
		var nearest = bandCenter.add(side.scale(Math.clamp(lateral, -width, width)));
		double distance = observer.distanceTo(nearest);
		double proximity = 1 - smooth((distance - profile.nearDistance())
				/ (profile.farDistance() - profile.nearDistance()));
		double inset = Math.max(0, width - Math.abs(lateral));
		double edge = 0.55 + 0.45 * smooth(inset / Math.clamp(width, Math.min(1, profile.edgeDistance()), profile.edgeDistance()));
		float target = (float) (profile.minimumCoverage() + (1 - profile.minimumCoverage()) * proximity * edge);

		// 远处看当前波头，进入波带后连续转向最近波带，避免始终指向路线起点。
		double headWeight = smooth(distance / profile.farDistance());
		var source = nearest.add(headCenter.subtract(nearest).scale(headWeight)).subtract(observer);
		var direction = source.horizontalDistanceSqr() > 0.0001 ? horizontal(source) : forward.scale(-1);
		return new Sample(direction, target);
	}

	/// elapsedTicks 由调用方提供，同帧多次读取应传零；覆盖可增可减，方向沿水平最短圆弧平滑。
	public Sample update(Sample target, double elapsedTicks) {
		double weight = -Math.expm1(-Math.max(0, elapsedTicks) / profile.responseTicks());
		var direction = horizontal(target.direction());
		double targetYaw = Math.atan2(direction.z, direction.x);
		double delta = Math.atan2(Math.sin(targetYaw - yaw), Math.cos(targetYaw - yaw));
		yaw += delta * weight;
		coverage += (float) ((Math.clamp(target.coverage(), 0, 1) - coverage) * weight);
		return new Sample(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), coverage);
	}

	/// 激活或换世界时重置；资源重载时调用方可保留此状态。
	public void reset(Vec3 direction) {
		var normalized = horizontal(direction);
		yaw = Math.atan2(normalized.z, normalized.x);
		coverage = profile.minimumCoverage();
	}

	public Sample minimum(Vec3 direction) {
		return new Sample(horizontal(direction), profile.minimumCoverage());
	}

	/// 距离单位为格，响应时间单位为游戏刻；覆盖下限供路线尚未就绪时使用。
	public record Profile(float minimumCoverage, double nearDistance, double farDistance,
	                      double edgeDistance, double responseTicks) {
		public Profile {
			if (!(minimumCoverage >= 0 && minimumCoverage <= 1 && nearDistance >= 0
					&& farDistance > nearDistance && edgeDistance > 0 && responseTicks > 0)) {
				throw new IllegalArgumentException("天空覆盖参数超出有效范围");
			}
		}
	}

	/// 水平单位世界方向和零到一覆盖；Vec3 不可变，不持有调用方可变对象。
	public record Sample(Vec3 direction, float coverage) {
	}
}
