package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.server.HordeEventConfig;

/// 事件启动时固定的服务端规则；计时均为服务器逻辑刻。
public record HordeRules(int duration, double width, double density, double margin, int initialQuota,
                         int maximumMultiplier, int reinforceTicks, int unreachableTicks, int retryTicks,
                         int outsideTicks, int scanTicks, int fadeTicks, int transitionTicks,
                         double regionMargin, int splitTicks, double spawnDistance) {
	public int maximumQuota() {
		return initialQuota * maximumMultiplier;
	}

	public static HordeRules configured() {
		return new HordeRules(HordeEventConfig.DURATION.get(), HordeEventConfig.WIDTH.get(),
				HordeEventConfig.DENSITY.get(), HordeEventConfig.MARGIN.get(),
				HordeEventConfig.INITIAL_QUOTA.get(), HordeEventConfig.MAXIMUM_MULTIPLIER.get(),
				HordeEventConfig.REINFORCE.get(), HordeEventConfig.UNREACHABLE.get(), HordeEventConfig.RETRY.get(),
				HordeEventConfig.OUTSIDE.get(), HordeEventConfig.SCAN.get(), HordeEventConfig.FADE.get(),
				HordeEventConfig.TRANSITION.get(), HordeEventConfig.REGION_MARGIN.get(),
				HordeEventConfig.SPLIT.get(), HordeEventConfig.SPAWN_DISTANCE.get());
	}

	public HordeRules withOverrides(int ticks, double blocks) {
		return new HordeRules(ticks, blocks, density, margin, initialQuota, maximumMultiplier, reinforceTicks,
				unreachableTicks, retryTicks, outsideTicks, scanTicks, fadeTicks, transitionTicks,
				regionMargin, splitTicks, spawnDistance);
	}

	public CompoundTag save() {
		var tag = new CompoundTag();
		tag.putInt("duration", duration);
		tag.putDouble("width", width);
		tag.putDouble("density", density);
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
				Math.clamp(tag.getDoubleOr("density", 1), 0.001, 4),
				Math.max(0, tag.getDoubleOr("margin", 8)), Math.max(1, tag.getIntOr("initial", 1)),
				Math.max(1, tag.getIntOr("multiplier", 3)), Math.max(1, tag.getIntOr("reinforce", 100)),
				Math.max(1, tag.getIntOr("unreachable", 100)), Math.max(1, tag.getIntOr("retry", 100)),
				Math.max(1, tag.getIntOr("outside", 40)), Math.max(1, tag.getIntOr("scan", 20)),
				Math.max(1, tag.getIntOr("fade", 5)), Math.max(1, tag.getIntOr("transition", 20)),
				Math.max(0, tag.getDoubleOr("region_margin", 8)), Math.max(1, tag.getIntOr("split", 40)),
				Math.max(1, tag.getDoubleOr("spawn_distance", 4)));
	}
}
