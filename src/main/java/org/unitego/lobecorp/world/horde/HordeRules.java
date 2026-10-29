package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.server.HordeEventConfig;

/// 事件启动时固定的服务端规则；计时均为服务器逻辑刻。
/// scanTicks与transitionTicks保留快照结构；发现与空间更新由运行期调度，GPU不再使用旧区域过渡计时。
public record HordeRules(int duration, double width, double rowSpacing, double columnSpacing, double margin,
                         int initialQuota,
                         int maximumMultiplier, int reinforceTicks, int unreachableTicks, int retryTicks,
                         int outsideTicks, int scanTicks, int fadeTicks, int transitionTicks,
                         double regionMargin, int splitTicks, double spawnDistance) {
	/// 单个目标的有效参战人数硬上限，包含待生成预约。
	public static final int MAXIMUM_TARGET_QUOTA = 10;

	public HordeRules {
		if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(rowSpacing) || rowSpacing <= 0
				|| !Double.isFinite(columnSpacing) || columnSpacing <= 0) {
			throw new IllegalArgumentException("群体线路宽度与间距必须为有限正数");
		}
		if (!Double.isFinite(margin) || margin < 0 || !Double.isFinite(regionMargin) || regionMargin < 0
				|| !Double.isFinite(spawnDistance) || spawnDistance <= 0) {
			throw new IllegalArgumentException("群体区域余量与出生距离必须为有效有限数");
		}
		long quota = (long) initialQuota * maximumMultiplier;
		if (quota < Integer.MIN_VALUE || quota > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("群体参战配额乘积超出整数范围");
		}
	}

	public int maximumQuota() {
		return Math.min(MAXIMUM_TARGET_QUOTA, initialQuota * maximumMultiplier);
	}

	public static HordeRules configured() {
		return new HordeRules(HordeEventConfig.DURATION.get(), HordeEventConfig.WIDTH.get(),
				HordeEventConfig.ROW_SPACING.get(), HordeEventConfig.COLUMN_SPACING.get(), HordeEventConfig.MARGIN.get(),
				HordeEventConfig.INITIAL_QUOTA.get(), HordeEventConfig.MAXIMUM_MULTIPLIER.get(),
				HordeEventConfig.REINFORCE.get(), HordeEventConfig.UNREACHABLE.get(), HordeEventConfig.RETRY.get(),
				HordeEventConfig.OUTSIDE.get(), 1, HordeEventConfig.FADE.get(),
				20, HordeEventConfig.REGION_MARGIN.get(),
				HordeEventConfig.SPLIT.get(), HordeEventConfig.SPAWN_DISTANCE.get());
	}

	public HordeRules withOverrides(int ticks, double blocks) {
		return new HordeRules(ticks, blocks, rowSpacing, columnSpacing, margin, initialQuota, maximumMultiplier, reinforceTicks,
				unreachableTicks, retryTicks, outsideTicks, scanTicks, fadeTicks, transitionTicks,
				regionMargin, splitTicks, spawnDistance);
	}

	public CompoundTag save() {
		var tag = new CompoundTag();
		tag.putInt("duration", duration);
		tag.putDouble("width", width);
		tag.putDouble("row_spacing", rowSpacing);
		tag.putDouble("column_spacing", columnSpacing);
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
				Math.clamp(tag.getDoubleOr("row_spacing", 1.8), 0.25, 16),
				Math.clamp(tag.getDoubleOr("column_spacing", 1.8), 0.25, 16),
				Math.max(0, tag.getDoubleOr("margin", 8)), Math.max(1, tag.getIntOr("initial", 1)),
				Math.max(1, tag.getIntOr("multiplier", 3)), Math.max(1, tag.getIntOr("reinforce", 100)),
				Math.max(1, tag.getIntOr("unreachable", 100)), Math.max(1, tag.getIntOr("retry", 100)),
				Math.max(1, tag.getIntOr("outside", 40)), Math.max(1, tag.getIntOr("scan", 20)),
				Math.max(1, tag.getIntOr("fade", 5)), Math.max(1, tag.getIntOr("transition", 20)),
				Math.max(0, tag.getDoubleOr("region_margin", 8)), Math.max(1, tag.getIntOr("split", 40)),
				Math.max(1, tag.getDoubleOr("spawn_distance", 4)));
	}
}
