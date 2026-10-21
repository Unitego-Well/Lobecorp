package org.unitego.lobecorp.client.horde;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.unitego.lobecorp.Lobecorp;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.network.ts.horde.HordeSnapshotRequestPayload;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeRoute;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.UUID;

/// 虚拟浪潮的客户端镜像；只为预算内显示对象取得池槽位。
public class HordeClient {
	private static final Map<UUID, ClientEvent> EVENTS = new HashMap<>();
	private static final Map<Key, Display> ACTIVE = new HashMap<>();
	private static final ArrayDeque<Display> POOL = new ArrayDeque<>();
	private static long frame;
	private static long lastFrameNanos;
	private static double quality = 1;
	private static double averageFrame = 1d / 60;
	private static long recoveryAt;
	private static Object generation;
	private static int candidates;
	private static List<Instance> lastInstances = List.of();

	public static void apply(CompoundTag packet) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		try {
			UUID id = UUID.fromString(packet.getStringOr("id", ""));
			String kind = packet.getStringOr("kind", "");
			long sequence = packet.getLongOr("sequence", 0);
			CompoundTag body = packet.getCompoundOrEmpty("body");
			ClientEvent event = EVENTS.get(id);
			boolean reset = kind.equals("header") && body.getBooleanOr("reset", false);
			if (kind.equals("remove")) {
				EVENTS.remove(id);
				return;
			}
			if (event == null && !reset) {
				ClientPacketDistributor.sendToServer(new HordeSnapshotRequestPayload(id));
				return;
			}
			if (reset) {
				event = new ClientEvent(id);
				EVENTS.put(id, event);
			} else if (sequence != event.sequence + 1) {
				if (minecraft.level.getGameTime() - event.lastRequest >= 40) {
					event.lastRequest = minecraft.level.getGameTime();
					ClientPacketDistributor.sendToServer(new HordeSnapshotRequestPayload(id));
				}
				return;
			}
			event.sequence = sequence;
			event.serverClock = packet.getLongOr("clock", 0);
			event.clockReceivedAt = minecraft.level.getGameTime();
			switch (kind) {
				case "header" -> {
					event.receivedAt = minecraft.level.getGameTime();
					if (!body.getStringOr("dimension", "").equals(minecraft.level.dimension().identifier().toString())) {
						EVENTS.remove(id);
						return;
					}
					event.header(body);
				}
				case "regions" -> event.installRegions(body);
				case "route" -> {
					int index = body.getIntOr("first", 0);
					for (var element : body.getListOrEmpty("nodes")) {
						if (element instanceof CompoundTag node) {
							event.nodes.put(index++, HordeRoute.Node.load(node));
						}
					}
				}
				default -> {
				}
			}
		} catch (IllegalArgumentException exception) {
			Lobecorp.LOGGER.warn("忽略无效群体事件快照", exception);
		}
	}

	public static List<Instance> extract(float partialTick, Frustum frustum) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !HordeVisualConfig.ENABLED.get()) {
			lastInstances = List.of();
			return lastInstances;
		}
		HordeVisualConfig.refreshPreset();
		updateQuality();
		frame++;
		if (generation != GeckoLibResources.getBakedModels()) {
			generation = GeckoLibResources.getBakedModels();
			HordeMeshCache.clear();
			HordeGpuRenderer.clear();
		}
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		HordeVisibility.begin(camera, minecraft.level.getGameTime());
		int configured = HordeVisualConfig.MAX_VISIBLE.get();
		int limit = configured == -1 ? Integer.MAX_VALUE : configured;
		var selected = new PriorityQueue<>(Comparator.comparingDouble(Candidate::score).reversed());
		candidates = 0;
		for (var event : EVENTS.values()) {
			event.select(camera, partialTick, limit, selected, frustum);
		}
		HordeVisibility.end();
		var ordered = new ArrayList<>(selected);
		ordered.sort(Comparator.comparingDouble(Candidate::distance));
		var instances = new ArrayList<Instance>();
		for (var candidate : ordered) {
			var display = ACTIVE.get(candidate.key());
			if (display == null) {
				display = POOL.pollFirst();
				if (display == null) {
					display = new Display();
				}
				display.position = candidate.position();
				display.joinedAt = minecraft.level.getGameTime();
				ACTIVE.put(candidate.key(), display);
			}
			display.seen = frame;
			display.position = candidate.position();
			double detailScale = Math.max(0.25, quality);
			int band = candidate.distance() < HordeVisualConfig.NEAR.get() * detailScale ? 0
					: candidate.distance() < HordeVisualConfig.FAR.get() * detailScale ? 1 : 2;
			float alpha = (float) HordeSamplingUtil.smooth((minecraft.level.getGameTime() + partialTick - display.joinedAt)
					/ candidate.event().rules.fadeTicks()) * candidate.alpha();
			int light = LevelRenderer.getLightCoords(minecraft.level,
					BlockPos.containing(display.position.add(0, 1, 0)));
			instances.add(new Instance(candidate.key(), candidate.event().visual, display.position,
					candidate.direction(), candidate.time(), band, candidate.phase(), alpha, light, candidate.distance()));
		}
		var iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (entry.getValue().seen != frame) {
				POOL.addLast(entry.getValue());
				iterator.remove();
			}
		}
		int release = 32;
		while (POOL.size() > HordeVisualConfig.POOL_IDLE.get() && release-- > 0) {
			POOL.removeLast();
		}
		HordeMeshCache.beginFrame();
		lastInstances = List.copyOf(instances);
		return lastInstances;
	}

	public static List<Instance> instances() {
		return lastInstances;
	}

	public static double quality() {
		return quality;
	}

	private static void updateQuality() {
		long now = System.nanoTime();
		if (lastFrameNanos != 0) {
			double seconds = Math.min(0.25, (now - lastFrameNanos) / 1_000_000_000d);
			averageFrame += (seconds - averageFrame) * 0.1;
			double target = 1 / HordeVisualConfig.TARGET_FPS.get();
			if (HordeVisualConfig.ADAPTIVE.get() && averageFrame > target * 1.15) {
				quality = Math.max(HordeVisualConfig.MIN_QUALITY.get(), quality - seconds * 0.8);
				recoveryAt = now + 2_000_000_000L;
			} else if (!HordeVisualConfig.ADAPTIVE.get() || now > recoveryAt) {
				quality = Math.min(1, quality + seconds * 0.1);
			}
		}
		lastFrameNanos = now;
	}

	public static String statistics() {
		return "Horde: " + lastInstances.size() + " visible, " + candidates + " candidates, pool " + POOL.size()
				+ ", quality " + Math.round(quality * 100) + "%, meshes " + HordeMeshCache.statistics();
	}

	public static void clear() {
		EVENTS.clear();
		ACTIVE.clear();
		POOL.clear();
		lastInstances = List.of();
		quality = 1;
		lastFrameNanos = 0;
		HordeMeshCache.clear();
		HordeAmbient.clear();
		HordeRendering.clear();
		HordeVisibility.clear();
	}

	public record Key(UUID eventId, long number) {
	}

	public record Instance(Key key, HordeVisualDefinition visual, Vec3 position, Vec3 direction,
	                       double time, int band, int phase, float alpha, int light, double distance) {
	}

	private record Candidate(Key key, ClientEvent event, Vec3 position, Vec3 direction, double time,
	                         int phase, double distance, double score, float alpha) {
	}

	private static class Display {
		private Vec3 position = Vec3.ZERO;
		private long seen;
		private long joinedAt;
	}

	private static class ClientEvent {
		private final UUID id;
		private final TreeMap<Integer, HordeRoute.Node> nodes = new TreeMap<>();
		private final Map<String, Region> regions = new HashMap<>();
		private final Map<String, CompoundTag> pendingRegions = new HashMap<>();
		private long regionBatch = -1;
		private HordeRules rules;
		private HordeVisualDefinition visual;
		private long seed;
		private long sequence;
		private long serverClock;
		private long receivedAt;
		private long clockReceivedAt;
		private long lastRequest = Long.MIN_VALUE / 2;
		private long targetTick;
		private long stop;
		private double length;
		private double speed;

		private ClientEvent(UUID id) {
			this.id = id;
		}

		private void header(CompoundTag tag) {
			rules = HordeRules.load(tag.getCompoundOrEmpty("rules"));
			seed = tag.getLongOr("seed", 0);
			targetTick = tag.getLongOr("tick", 0);
			stop = tag.getLongOr("stop", rules.duration());
			length = tag.getDoubleOr("length", 0);
			speed = tag.getDoubleOr("speed", 0);
			int first = tag.getIntOr("scope_first", 0);
			int last = tag.getIntOr("scope_last", tag.getIntOr("node_count", 0) - 1);
			nodes.entrySet().removeIf(entry -> entry.getKey() < first || entry.getKey() > last);
			visual = new HordeVisualDefinition(Identifier.parse(tag.getStringOr("model", "")),
					Identifier.parse(tag.getStringOr("animation", "")), tag.getStringOr("run", "run"),
					Identifier.parse(tag.getStringOr("texture", "")));
		}

		private void installRegions(CompoundTag page) {
			long batch = page.getLongOr("batch", -1);
			if (page.getBooleanOr("first", false)) {
				pendingRegions.clear();
				regionBatch = batch;
			}
			if (batch != regionBatch) {
				return;
			}
			for (var element : page.getListOrEmpty("regions")) {
				if (element instanceof CompoundTag zone) {
					pendingRegions.put(zone.getStringOr("id", ""), zone.copy());
				}
			}
			if (page.getBooleanOr("last", false)) {
				pendingRegions.forEach((key, zone) -> regions.computeIfAbsent(key, _ -> new Region())
						.update(zone, serverClock, rules.transitionTicks()));
				regions.keySet().removeIf(key -> !pendingRegions.containsKey(key));
				pendingRegions.clear();
				regionBatch = -1;
			}
		}

		private void select(Vec3 camera, float partial, int limit, PriorityQueue<Candidate> selected, Frustum frustum) {
			if (rules == null || visual == null || length <= 0 || speed <= 0) {
				return;
			}
			var level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			double now = targetTick + Math.clamp(level.getGameTime() - receivedAt + partial, 0, 40);
			double clock = serverClock + Math.clamp(level.getGameTime() - clockReceivedAt + partial, 0, 20);
			double head = now * speed;
			double spacing = 1 / Math.sqrt(HordeSamplingUtil.density(rules.width(), rules.density()));
			int columns = Math.max(1, (int) Math.ceil(rules.width() / spacing));
			long emitted = (long) Math.floor(Math.min(stop, now) * speed / spacing);
			double maximumDistance = HordeVisualConfig.rangeBlocks();
			var entries = new ArrayList<>(nodes.entrySet());
			for (int index = 0; index < entries.size() - 1; index++) {
				var entry = entries.get(index);
				var following = entries.get(index + 1);
				if (following.getKey() != entry.getKey() + 1) {
					continue;
				}
				var a = entry.getValue();
				var b = following.getValue();
				Vec3 start = a.at(now, rules.transitionTicks());
				Vec3 end = b.at(now, rules.transitionTicks());
				double range = maximumDistance + rules.width();
				double distanceToSegment = HordeSamplingUtil.segmentDistanceSqr(camera, start, end);
				double padding = rules.width() / 2 + 3;
				if (distanceToSegment > range * range || !frustum.isVisible(new AABB(
						Math.min(start.x, end.x) - padding, Math.min(start.y, end.y) - padding, Math.min(start.z, end.z) - padding,
						Math.max(start.x, end.x) + padding, Math.max(start.y, end.y) + padding, Math.max(start.z, end.z) + padding))) {
					continue;
				}
				long firstRow = Math.max(0, (long) Math.ceil((head - b.distance()) / spacing - 0.35));
				long last = Math.min(emitted, (long) Math.floor((head - a.distance()) / spacing + 0.35));
				Vec3 incoming = index > 0 && entries.get(index - 1).getKey() == entry.getKey() - 1
						? end.subtract(entries.get(index - 1).getValue().at(now, rules.transitionTicks())) : end.subtract(start);
				Vec3 outgoing = index + 2 < entries.size() && entries.get(index + 2).getKey() == following.getKey() + 1
						? entries.get(index + 2).getValue().at(now, rules.transitionTicks()).subtract(start) : end.subtract(start);
				incoming = incoming.multiply(1, 0, 1).normalize();
				outgoing = outgoing.multiply(1, 0, 1).normalize();
				for (long row = firstRow; row <= last; row++) {
					Vec3 direction = new Vec3(end.x - start.x, 0, end.z - start.z).normalize();
					for (int column = 0; column < columns; column++) {
						long number = row * 65536 + column;
						double station = head - row * spacing
								+ (HordeSamplingUtil.unit(seed, number, 61) - 0.5) * spacing * 0.7;
						if (station < a.distance() || station >= b.distance()) {
							continue;
						}
						double weight = Math.clamp((station - a.distance()) / Math.max(0.001, b.distance() - a.distance()), 0, 1);
						Vec3 heading = incoming.lerp(outgoing, weight).normalize();
						if (heading.lengthSqr() < 0.001) {
							heading = direction;
						}
						Vec3 side = new Vec3(-heading.z, 0, heading.x);
						double baseLateral = ((column + 0.5) / columns - 0.5) * rules.width();
						double lateral = baseLateral
								+ HordeSamplingUtil.noise(seed, number, now / 40) * spacing * 0.35;
						Vec3 origin = start.lerp(end, weight);
						double y = a.height(lateral, now, rules.transitionTicks()) * (1 - weight)
								+ b.height(lateral, now, rules.transitionTicks()) * weight;
						Vec3 point = new Vec3(origin.x, y, origin.z).add(side.scale(lateral));
						for (var region : regions.values()) {
							point = region.deflect(point, side, clock, rules.transitionTicks(), baseLateral >= 0 ? 1 : -1);
						}
						double adjustedLateral = point.subtract(origin).dot(side);
						double adjustedY = a.height(adjustedLateral, now, rules.transitionTicks()) * (1 - weight)
								+ b.height(adjustedLateral, now, rules.transitionTicks()) * weight;
						point = new Vec3(point.x, adjustedY, point.z);
						double distance = point.distanceTo(camera);
						if (distance > maximumDistance || !HordeVisibility.visible(point, frustum)) {
							continue;
						}
						double score = distance + HordeSamplingUtil.unit(seed, number, 31) * 8;
						candidates++;
						Candidate worst = selected.peek();
						if (selected.size() < limit || worst != null && worst.score() > score) {
							var candidate = new Candidate(new Key(id, number), this, point, heading, now,
									(int) (HordeSamplingUtil.unit(seed, number, 47) * HordeVisualConfig.POSE_GROUPS.get()),
									distance, score, HordeSamplingUtil.endpointAlpha(station, length, speed, rules.fadeTicks()));
							if (selected.size() >= limit) {
								selected.poll();
							}
							selected.add(candidate);
						}
					}
				}
			}
		}
	}

	private static class Region {
		private Vec3 previous = Vec3.ZERO;
		private Vec3 center = Vec3.ZERO;
		private double rx;
		private double rz;
		private double previousRx;
		private double previousRz;
		private long changed;
		private boolean active;
		private double previousStrength;

		private void update(CompoundTag tag, long clock, int transition) {
			Vec3 next = new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0), tag.getDoubleOr("z", 0));
			boolean nextActive = tag.getBooleanOr("active", true);
			if (next.equals(center) && nextActive == active
					&& rx == tag.getDoubleOr("rx", 0) && rz == tag.getDoubleOr("rz", 0)) {
				return;
			}
			previous = center.equals(Vec3.ZERO) ? next : previous.lerp(center, HordeSamplingUtil.smooth((clock - changed) / (double) transition));
			previousRx = rx;
			previousRz = rz;
			previousStrength = strength(clock, transition);
			center = next;
			rx = Math.max(1, tag.getDoubleOr("rx", 1));
			rz = Math.max(1, tag.getDoubleOr("rz", 1));
			active = nextActive;
			changed = clock;
		}

		private double strength(double clock, int transition) {
			double progress = HordeSamplingUtil.smooth((clock - changed) / transition);
			return previousStrength + ((active ? 1 : 0) - previousStrength) * progress;
		}

		private Vec3 deflect(Vec3 point, Vec3 side, double clock, int transition, double sign) {
			double f = HordeSamplingUtil.smooth((clock - changed) / transition);
			Vec3 c = previous.lerp(center, f);
			double xRadius = previousRx + (rx - previousRx) * f;
			double zRadius = previousRz + (rz - previousRz) * f;
			double x = point.x - c.x;
			double z = point.z - c.z;
			double normalized = x * x / Math.max(1, xRadius * xRadius) + z * z / Math.max(1, zRadius * zRadius);
			if (normalized >= 4) {
				return point;
			}
			double amount = HordeSamplingUtil.smooth(1 - normalized / 4) * Math.max(xRadius, zRadius) * strength(clock, transition);
			return point.add(side.scale(sign * amount));
		}
	}
}
