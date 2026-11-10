package org.unitego.lobecorp.config.server;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.registry.translation.HordeConfigTranslationKeys;

import static org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys.EVENTS;
import static org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys.EVENTS_MIDNIGHT_CLEANUP;
import static org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys.EVENTS_MIDNIGHT_CLEANUP_WAVE;
import static org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys.EVENTS_BUDGET;
import static org.unitego.lobecorp.registry.translation.ServerConfigTranslationKeys.DEBUG;

/// 事件规则在启动时取快照；工作预算对当前维度的全部事件共同生效。
public class HordeConfig {
	public static final ModConfigSpec.IntValue DURATION, INITIAL_QUOTA, MAXIMUM_MULTIPLIER, REINFORCE,
			UNREACHABLE, RETRY, OUTSIDE, FADE, SPLIT, SOFT_BUDGET, CREATES_PER_TICK,
			WORK_PER_TICK, SEND_BYTES, MAX_SEGMENTS_PER_PACKET;
    public static final ModConfigSpec.DoubleValue WIDTH, MARGIN, REGION_MARGIN, SPAWN_DISTANCE,
			SUBSCRIBE_DISTANCE, SUBSCRIBE_HYSTERESIS;
	public static final ModConfigSpec.BooleanValue STATISTICS;

	static {
		var b = ServerConfig.section("events", EVENTS);
		b.translation(EVENTS_MIDNIGHT_CLEANUP).push("midnight_cleanup");
		b.translation(EVENTS_MIDNIGHT_CLEANUP_WAVE).push("wave");
		DURATION = b.translation(HordeConfigTranslationKeys.SERVER_DURATION).comment("生成阶段的逻辑刻数；收尾按线路长度与事件奔跑速度计算。").defineInRange("durationTicks", 6000, 20, 1728000);
		WIDTH = b.translation(HordeConfigTranslationKeys.SERVER_WIDTH).defineInRange("width", 128d, 1d, 512d);
		MARGIN = b.translation(HordeConfigTranslationKeys.SERVER_MARGIN).defineInRange("corridorMargin", 8d, 0d, 128d);
		b.pop().translation(HordeConfigTranslationKeys.SERVER_COMBAT).push("combat");
		INITIAL_QUOTA = b.translation(HordeConfigTranslationKeys.SERVER_INITIAL_QUOTA).defineInRange("initialQuota", 1, 1, 10);
		MAXIMUM_MULTIPLIER = b.translation(HordeConfigTranslationKeys.SERVER_MAXIMUM_MULTIPLIER).defineInRange("maximumMultiplier", 10, 1, 10);
		REINFORCE = b.translation(HordeConfigTranslationKeys.SERVER_REINFORCE).defineInRange("reinforceTicks", 100, 1, 72000);
		UNREACHABLE = b.translation(HordeConfigTranslationKeys.SERVER_UNREACHABLE).defineInRange("unreachableTicks", 100, 1, 72000);
		RETRY = b.translation(HordeConfigTranslationKeys.SERVER_RETRY).defineInRange("retryTicks", 100, 1, 72000);
		OUTSIDE = b.translation(HordeConfigTranslationKeys.SERVER_OUTSIDE).defineInRange("outsideGraceTicks", 40, 1, 72000);
		FADE = b.translation(HordeConfigTranslationKeys.SERVER_FADE).defineInRange("fadeTicks", 5, 1, 200);
		REGION_MARGIN = b.translation(HordeConfigTranslationKeys.SERVER_REGION_MARGIN).defineInRange("regionMargin", 8d, 0d, 128d);
		SPLIT = b.translation(HordeConfigTranslationKeys.SERVER_SPLIT).defineInRange("regionSplitTicks", 40, 1, 1200);
        SPAWN_DISTANCE = b.translation(HordeConfigTranslationKeys.SERVER_SPAWN_DISTANCE).comment("候选优先16格，失败时按12/8/4格回退；配置值为下限，额外计入实体宽度。").defineInRange("spawnDistance", 4d, 1d, 64d);
		b.pop(2).translation(EVENTS_BUDGET).push("budget");
		SOFT_BUDGET = b.translation(HordeConfigTranslationKeys.SERVER_SOFT_BUDGET).defineInRange("softEntityBudget", 100, 1, 10000);
		CREATES_PER_TICK = b.translation(HordeConfigTranslationKeys.SERVER_CREATES_PER_TICK).defineInRange("createsPerTick", 2, 1, 64);
		WORK_PER_TICK = b.translation(HordeConfigTranslationKeys.SERVER_WORK_PER_TICK).comment("每维度每刻的出生位置与粗略可达检查工作额度。").defineInRange("spawnWorkPerTick", 16, 1, 1024);
        b.pop().translation(HordeConfigTranslationKeys.SERVER_NETWORK).push("network");
		SUBSCRIBE_DISTANCE = b.translation(HordeConfigTranslationKeys.SERVER_SUBSCRIBE_DISTANCE).defineInRange("subscribeDistance", 288d, 32d, 1024d);
		SUBSCRIBE_HYSTERESIS = b.translation(HordeConfigTranslationKeys.SERVER_SUBSCRIBE_HYSTERESIS).defineInRange("subscribeHysteresis", 32d, 0d, 256d);
		SEND_BYTES = b.translation(HordeConfigTranslationKeys.SERVER_SEND_BYTES).defineInRange("bytesPerPlayerTick", 16384, 4096, 1048576);
		MAX_SEGMENTS_PER_PACKET = b.translation(HordeConfigTranslationKeys.SERVER_MAX_SEGMENTS_PER_PACKET).defineInRange("segmentsPerPacket", 8, 1, 32);
		b.pop(2);
		b = ServerConfig.section("debug", DEBUG);
		STATISTICS = b.translation(HordeConfigTranslationKeys.SERVER_STATISTICS).define("statistics", false);
		b.pop();
	}

	public static void init() {
	}
}
