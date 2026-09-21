package org.unitego.lobecorp.hitbox;

/// 服务端判断框命中效果。
@FunctionalInterface
public interface IHitboxEffect {
	/// 尝试对候选目标执行效果。
	/// 只有返回 {@code true} 时才记录成功次数并开始命中间隔。
	///
	/// @param context 本次命中的服务端上下文
	/// @return 效果是否成功生效
	boolean apply(HitboxEffectContext context);
}
