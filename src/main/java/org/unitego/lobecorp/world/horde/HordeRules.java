package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.server.HordeConfig;

/// 事件启动时固定的服务端规则；计时均为服务器逻辑刻。
/// scanTicks与transitionTicks保留快照结构；发现与空间更新由运行期调度，GPU不再使用旧区域过渡计时。
public record HordeRules(int duration, double width, double margin,
                         int initialQuota,
                         int maximumMultiplier, int reinforceTicks, int unreachableTicks, int retryTicks,
                         int outsideTicks, int scanTicks, int fadeTicks, int transitionTicks,
                         double regionMargin, int splitTicks, double spawnDistance) {
	/// 每四十点最大生命值提供一个参战名额，包含待生成预约。
	private static final double HEALTH_PER_TARGET_QUOTA = 40;

	public HordeRules {
		if (!Double.isFinite(width) || width <= 0) {
			throw new IllegalArgumentException("群体线路宽度与间距必须为有限正数");
		}
		if (!Double.isFinite(margin) || margin < 0 || !Double.isFinite(regionMargin) || regionMargin < 0
				|| !Double.isFinite(spawnDistance) || spawnDistance <= 0) {
			throw new IllegalArgumentException("群体区域余量与出生距离必须为有效有限数");
		}
	}

	public static int maximumQuota(float maxHealth) {
		return Math.clamp(Math.round(maxHealth / HEALTH_PER_TARGET_QUOTA), 2, Integer.MAX_VALUE);
	}

	public static HordeRules configured() {
		return new HordeRules(HordeConfig.DURATION.get(), HordeConfig.WIDTH.get(),
				HordeConfig.MARGIN.get(),
				HordeConfig.INITIAL_QUOTA.get(), HordeConfig.MAXIMUM_MULTIPLIER.get(),
				HordeConfig.REINFORCE.get(), HordeConfig.UNREACHABLE.get(), HordeConfig.RETRY.get(),
				HordeConfig.OUTSIDE.get(), 1, HordeConfig.FADE.get(),
				20, HordeConfig.REGION_MARGIN.get(),
				HordeConfig.SPLIT.get(), HordeConfig.SPAWN_DISTANCE.get());
	}

	public HordeRules withOverrides(int ticks, double blocks) {
		return new HordeRules(ticks, blocks, margin, initialQuota, maximumMultiplier, reinforceTicks,
				unreachableTicks, retryTicks, outsideTicks, scanTicks, fadeTicks, transitionTicks,
				regionMargin, splitTicks, spawnDistance);
	}

	public CompoundTag save() {
		var tag = new CompoundTag();
		tag.putInt("duration", duration);
		tag.putDouble("width", width);
		tag.putDouble("margin", margin);
		tag.putInt("initial", initialQuota);
		tag.putInt("multiplier", maximumMultiplier);
		tag.putInt("reinforce", reinforceTicks);
		tag.putInt("unreachable", unreachableTicks);
		tag.putInt("retry", retryTicks);
		tag.putInt("outside", outsideTicks);
		tag.putInt("scan", scanTicks);
		tag.putInt("fade", fadeTicks);
		tag.putInt("transition", transitionTicks);
		tag.putDouble("region_margin", regionMargin);
		tag.putInt("split", splitTicks);
		tag.putDouble("spawn_distance", spawnDistance);
		return tag;
	}

	public static HordeRules load(CompoundTag tag) {
		return new HordeRules(Math.max(20, tag.getIntOr("duration", 6000)),
				Math.clamp(tag.getDoubleOr("width", 128), 1, 512),
				Math.max(0, tag.getDoubleOr("margin", 8)), Math.max(1, tag.getIntOr("initial", 1)),
				Math.max(1, tag.getIntOr("multiplier", 3)), Math.max(1, tag.getIntOr("reinforce", 100)),
				Math.max(1, tag.getIntOr("unreachable", 100)), Math.max(1, tag.getIntOr("retry", 100)),
				Math.max(1, tag.getIntOr("outside", 40)), Math.max(1, tag.getIntOr("scan", 20)),
				Math.max(1, tag.getIntOr("fade", 5)), Math.max(1, tag.getIntOr("transition", 20)),
				Math.max(0, tag.getDoubleOr("region_margin", 8)), Math.max(1, tag.getIntOr("split", 40)),
				Math.max(1, tag.getDoubleOr("spawn_distance", 4)));
	}
}
