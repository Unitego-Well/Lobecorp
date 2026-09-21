package org.unitego.lobecorp.hitbox;

/// 判断框成功命中的次数和间隔策略。
///
/// @param mode 重复命中方式
/// @param intervalTicks 固定间隔模式的成功命中间隔
/// @param perTargetMaximum 每个目标的成功命中上限，-1 表示无限
/// @param totalMaximum 全实例成功命中上限，-1 表示无限
public record HitboxHitPolicy(HitboxHitMode mode, int intervalTicks, int perTargetMaximum, int totalMaximum) {
	/// 默认策略：每个目标只成功命中一次，总次数不限。
	public static final HitboxHitPolicy ONCE_PER_TARGET = new HitboxHitPolicy(HitboxHitMode.ONCE, 0, 1, -1);

	public HitboxHitPolicy {
		if (intervalTicks < 0 || perTargetMaximum < -1 || totalMaximum < -1) {
			throw new IllegalArgumentException("hit policy values are outside the supported range");
		}
		if (mode == HitboxHitMode.INTERVAL && intervalTicks == 0) {
			throw new IllegalArgumentException("interval mode requires a positive interval");
		}
	}
}
