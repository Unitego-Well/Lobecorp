package org.unitego.lobecorp.animation.pose;

/// 查询名使用完整的 query.、variable.、temp. 前缀；写入由每个播放实例自行管理。
@FunctionalInterface
public interface ILcAnimationQueries {
	double get(String name);

	default void set(String name, double value) {
		throw new LcAnimationQueryWriteException(name);
	}
}
