package org.unitego.lobecorp.config.server;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.generator.lang.HordeLang;

/// 事件规则在启动时取快照；工作预算对当前维度的全部事件共同生效。
public class HordeEventConfig {
	public static final ModConfigSpec.IntValue DURATION, INITIAL_QUOTA, MAXIMUM_MULTIPLIER, REINFORCE,
			UNREACHABLE, RETRY, OUTSIDE, FADE, SPLIT, SOFT_BUDGET, CREATES_PER_TICK,
			WORK_PER_TICK, SEND_BYTES, MAX_SEGMENTS_PER_PACKET;
    public static final ModConfigSpec.DoubleValue WIDTH, MARGIN, REGION_MARGIN, SPAWN_DISTANCE,
			SUBSCRIBE_DISTANCE, SUBSCRIBE_HYSTERESIS;
	public static final ModConfigSpec.BooleanValue STATISTICS;

	static {
		var b = ServerConfig.section("events", "Events", "事件");
		b.translation(ServerConfig.translation("events.midnight_cleanup", "Midnight cleanup", "深夜清扫")).push("midnight_cleanup");
		b.translation(ServerConfig.translation("events.midnight_cleanup.wave", "Wave", "浪潮")).push("wave");
		DURATION = b.translation(HordeLang.config("server.duration", "Generation ticks", "生成阶段刻数")).comment("生成阶段的逻辑刻数；收尾按线路长度与事件奔跑速度计算。").defineInRange("durationTicks", 6000, 20, 1728000);
		WIDTH = b.translation(HordeLang.config("server.width", "Width", "浪潮宽度")).defineInRange("width", 128d, 1d, 512d);
		MARGIN = b.translation(HordeLang.config("server.margin", "Corridor margin", "线路走廊余量")).defineInRange("corridorMargin", 8d, 0d, 128d);
		b.pop().translation(HordeLang.config("server.combat", "Combat", "战斗")).push("combat");
		INITIAL_QUOTA = b.translation(HordeLang.config("server.initial_quota", "Initial quota", "初始参战配额")).defineInRange("initialQuota", 1, 1, 10);
		MAXIMUM_MULTIPLIER = b.translation(HordeLang.config("server.maximum_multiplier", "Maximum multiplier", "最大配额倍率")).defineInRange("maximumMultiplier", 10, 1, 10);
		REINFORCE = b.translation(HordeLang.config("server.reinforce", "Reinforcement ticks", "增援间隔刻数")).defineInRange("reinforceTicks", 100, 1, 72000);
		UNREACHABLE = b.translation(HordeLang.config("server.unreachable", "Unreachable timeout", "无法接敌超时")).defineInRange("unreachableTicks", 100, 1, 72000);
		RETRY = b.translation(HordeLang.config("server.retry", "Retry cooldown", "重试冷却刻数")).defineInRange("retryTicks", 100, 1, 72000);
		OUTSIDE = b.translation(HordeLang.config("server.outside", "Outside grace", "连续出界宽限")).defineInRange("outsideGraceTicks", 40, 1, 72000);
		FADE = b.translation(HordeLang.config("server.fade", "Fade ticks", "淡入淡出刻数")).defineInRange("fadeTicks", 5, 1, 200);
		REGION_MARGIN = b.translation(HordeLang.config("server.region_margin", "Region margin", "战斗区域余量")).defineInRange("regionMargin", 8d, 0d, 128d);
		SPLIT = b.translation(HordeLang.config("server.split", "Region split ticks", "区域拆分迟滞")).defineInRange("regionSplitTicks", 40, 1, 1200);
        SPAWN_DISTANCE = b.translation(HordeLang.config("server.spawn_distance", "Spawn distance", "接敌出生距离")).comment("候选优先16格，失败时按12/8/4格回退；配置值为下限，额外计入实体宽度。").defineInRange("spawnDistance", 4d, 1d, 64d);
		b.pop(2).translation(ServerConfig.translation("events.budget", "Runtime budget", "运行预算")).push("budget");
		SOFT_BUDGET = b.translation(HordeLang.config("server.soft_budget", "Soft entity budget", "正常实体软预算")).defineInRange("softEntityBudget", 100, 1, 10000);
		CREATES_PER_TICK = b.translation(HordeLang.config("server.creates_per_tick", "Creates per tick", "每刻实体创建上限")).defineInRange("createsPerTick", 2, 1, 64);
		WORK_PER_TICK = b.translation(HordeLang.config("server.work_per_tick", "Spawn work per tick", "每刻出生检测工作额度")).comment("每维度每刻的出生位置与粗略可达检查工作额度。").defineInRange("spawnWorkPerTick", 16, 1, 1024);
        b.pop().translation(HordeLang.config("server.network", "Network", "网络")).push("network");
		SUBSCRIBE_DISTANCE = b.translation(HordeLang.config("server.subscribe_distance", "Subscription distance", "事件订阅距离")).defineInRange("subscribeDistance", 288d, 32d, 1024d);
		SUBSCRIBE_HYSTERESIS = b.translation(HordeLang.config("server.subscribe_hysteresis", "Subscription hysteresis", "订阅距离迟滞")).defineInRange("subscribeHysteresis", 32d, 0d, 256d);
		SEND_BYTES = b.translation(HordeLang.config("server.send_bytes", "Bytes per player tick", "每玩家每刻发送字节预算")).defineInRange("bytesPerPlayerTick", 16384, 4096, 1048576);
		MAX_SEGMENTS_PER_PACKET = b.translation(HordeLang.config("server.max_segments_per_packet", "Nodes per packet", "每包路线节点上限")).defineInRange("segmentsPerPacket", 8, 1, 32);
		b.pop(2);
		b = ServerConfig.section("debug", "Debug", "调试");
		STATISTICS = b.translation(HordeLang.config("server.statistics", "Performance statistics", "服务端性能统计")).define("statistics", false);
		b.pop();
	}

	public static void init() {
	}
}
