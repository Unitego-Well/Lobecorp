package org.unitego.lobecorp.config.server;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.generator.lang.HordeLang;

/// 事件规则在启动时取快照；工作预算对当前维度的全部事件共同生效。
public class HordeEventConfig {
	public static final String FILE_NAME = "lobecorp-horde-events.toml";
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.IntValue DURATION, INITIAL_QUOTA, MAXIMUM_MULTIPLIER, REINFORCE,
			UNREACHABLE, RETRY, OUTSIDE, SCAN, FADE, TRANSITION, SPLIT, SOFT_BUDGET, CREATES_PER_TICK,
			WORK_PER_TICK, SCAN_WORK, ROUTE_WORK, ROUTE_RECHECK, SEND_BYTES, MAX_SEGMENTS_PER_PACKET;
	public static final ModConfigSpec.DoubleValue WIDTH, DENSITY, MARGIN, REGION_MARGIN, SPAWN_DISTANCE,
			GRID_SIZE, SUBSCRIBE_DISTANCE, SUBSCRIBE_HYSTERESIS;
	public static final ModConfigSpec.BooleanValue STATISTICS;

	static {
		var b = new ModConfigSpec.Builder();
		b.push("event");
		DURATION = b.translation(HordeLang.config("server.duration", "Generation ticks", "生成阶段刻数")).comment("生成阶段的逻辑刻数；收尾预计使用相同时长。").defineInRange("durationTicks", 6000, 20, 1728000);
		WIDTH = b.translation(HordeLang.config("server.width", "Width", "浪潮宽度")).defineInRange("width", 128d, 1d, 512d);
		DENSITY = b.translation(HordeLang.config("server.density", "Base density", "基础视觉密度")).comment("128格宽时默认每平方格1个影像，以几乎贴合的最高密度为基准；其他宽度按平方根增长换算总量。").defineInRange("density", 1d, 0.001d, 4d);
		MARGIN = b.translation(HordeLang.config("server.margin", "Corridor margin", "线路走廊余量")).defineInRange("corridorMargin", 8d, 0d, 128d);
		b.pop().push("combat");
		INITIAL_QUOTA = b.translation(HordeLang.config("server.initial_quota", "Initial quota", "初始参战配额")).defineInRange("initialQuota", 1, 1, 16);
		MAXIMUM_MULTIPLIER = b.translation(HordeLang.config("server.maximum_multiplier", "Maximum multiplier", "最大配额倍率")).defineInRange("maximumMultiplier", 3, 1, 16);
		REINFORCE = b.translation(HordeLang.config("server.reinforce", "Reinforcement ticks", "增援间隔刻数")).defineInRange("reinforceTicks", 100, 1, 72000);
		UNREACHABLE = b.translation(HordeLang.config("server.unreachable", "Unreachable timeout", "无法接敌超时")).defineInRange("unreachableTicks", 100, 1, 72000);
		RETRY = b.translation(HordeLang.config("server.retry", "Retry cooldown", "重试冷却刻数")).defineInRange("retryTicks", 100, 1, 72000);
		OUTSIDE = b.translation(HordeLang.config("server.outside", "Outside grace", "连续出界宽限")).defineInRange("outsideGraceTicks", 40, 1, 72000);
		SCAN = b.translation(HordeLang.config("server.scan", "Scan ticks", "扫描间隔刻数")).defineInRange("scanTicks", 20, 1, 1200);
		FADE = b.translation(HordeLang.config("server.fade", "Fade ticks", "淡入淡出刻数")).defineInRange("fadeTicks", 5, 1, 200);
		REGION_MARGIN = b.translation(HordeLang.config("server.region_margin", "Region margin", "战斗区域余量")).defineInRange("regionMargin", 8d, 0d, 128d);
		SPLIT = b.translation(HordeLang.config("server.split", "Region split ticks", "区域拆分迟滞")).defineInRange("regionSplitTicks", 40, 1, 1200);
		SPAWN_DISTANCE = b.translation(HordeLang.config("server.spawn_distance", "Spawn distance", "接敌出生距离")).defineInRange("spawnDistance", 4d, 1d, 64d);
		SOFT_BUDGET = b.translation(HordeLang.config("server.soft_budget", "Soft entity budget", "正常实体软预算")).defineInRange("softEntityBudget", 100, 1, 10000);
		CREATES_PER_TICK = b.translation(HordeLang.config("server.creates_per_tick", "Creates per tick", "每刻实体创建上限")).defineInRange("createsPerTick", 2, 1, 64);
		WORK_PER_TICK = b.translation(HordeLang.config("server.work_per_tick", "Spawn work per tick", "每刻出生检测工作额度")).comment("每维度每刻的出生位置与粗略可达检查工作额度。").defineInRange("spawnWorkPerTick", 16, 1, 1024);
		SCAN_WORK = b.translation(HordeLang.config("server.scan_work", "Scan nodes per tick", "每刻共享目标扫描节点预算")).defineInRange("scanNodesPerTick", 8, 1, 128);
		b.pop().push("route");
		GRID_SIZE = b.translation(HordeLang.config("server.grid_size", "Coarse grid size", "路线粗网格尺寸")).defineInRange("gridSize", 8d, 2d, 32d);
		TRANSITION = b.translation(HordeLang.config("server.transition", "Transition ticks", "路线与让行过渡刻数")).defineInRange("transitionTicks", 20, 1, 200);
		ROUTE_WORK = b.translation(HordeLang.config("server.route_work", "Route nodes per tick", "每刻路线节点预算")).defineInRange("nodesPerTick", 2, 1, 128);
		ROUTE_RECHECK = b.translation(HordeLang.config("server.route_recheck", "Route recheck ticks", "地形复查间隔")).defineInRange("recheckTicks", 100, 20, 72000);
		b.pop().push("network");
		SUBSCRIBE_DISTANCE = b.translation(HordeLang.config("server.subscribe_distance", "Subscription distance", "事件订阅距离")).defineInRange("subscribeDistance", 288d, 32d, 1024d);
		SUBSCRIBE_HYSTERESIS = b.translation(HordeLang.config("server.subscribe_hysteresis", "Subscription hysteresis", "订阅距离迟滞")).defineInRange("subscribeHysteresis", 32d, 0d, 256d);
		SEND_BYTES = b.translation(HordeLang.config("server.send_bytes", "Bytes per player tick", "每玩家每刻发送字节预算")).defineInRange("bytesPerPlayerTick", 16384, 4096, 1048576);
		MAX_SEGMENTS_PER_PACKET = b.translation(HordeLang.config("server.max_segments_per_packet", "Nodes per packet", "每包路线节点上限")).defineInRange("segmentsPerPacket", 8, 1, 32);
		b.pop();
		STATISTICS = b.translation(HordeLang.config("server.statistics", "Performance statistics", "服务端性能统计")).define("statistics", false);
		SPEC = b.build();
	}
}
