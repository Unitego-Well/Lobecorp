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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/// 虚拟浪潮的客户端镜像；只为预算内显示对象取得池槽位。
public class HordeClient {
	private static final Map<UUID, ClientEvent> EVENTS = new HashMap<>();
	private static final Map<Key, Display> ACTIVE = new HashMap<>();
	private static final ArrayDeque<Display> POOL = new ArrayDeque<>();
	private static final Selection SELECTION = new Selection();
	private static final int INITIAL_CANDIDATES = 256;
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
			HordeImpostorAtlas.clear();
		}
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		HordeVisibility.begin(camera, minecraft.level.getGameTime());
		int configured = HordeVisualConfig.MAX_VISIBLE.get();
		SELECTION.begin(configured == -1 ? Integer.MAX_VALUE : configured);
		candidates = 0;
		for (var event : EVENTS.values()) {
			event.select(camera, partialTick, SELECTION, frustum);
		}
		HordeVisibility.end();
		var instances = new ArrayList<Instance>(SELECTION.size);
		for (var candidate : SELECTION.ordered()) {
			Key key = new Key(candidate.event.id, candidate.number);
			Vec3 point = new Vec3(candidate.x, candidate.y, candidate.z);
			var display = ACTIVE.get(key);
			if (display == null) {
				display = POOL.pollFirst();
				if (display == null) {
					display = new Display();
				}
				display.joinedAt = minecraft.level.getGameTime();
				display.lightAt = Long.MIN_VALUE / 2;
				ACTIVE.put(key, display);
			}
			display.seen = frame;
			display.position = point;
			double detailScale = Math.max(0.25, quality);
			int band = candidate.distance < HordeVisualConfig.NEAR.get() * detailScale ? 0
					: candidate.distance < HordeVisualConfig.FAR.get() * detailScale ? 1 : 2;
			float alpha = (float) HordeSamplingUtil.smooth((minecraft.level.getGameTime() + partialTick - display.joinedAt)
					/ candidate.event.rules.fadeTicks()) * candidate.alpha;
			int x = (int) Math.floor(point.x), y = (int) Math.floor(point.y + 1), z = (int) Math.floor(point.z);
			if (display.lightPosition.getX() != x || display.lightPosition.getY() != y || display.lightPosition.getZ() != z
					|| minecraft.level.getGameTime() - display.lightAt >= 5) {
				display.lightPosition.set(x, y, z);
				display.light = LevelRenderer.getLightCoords(minecraft.level, display.lightPosition);
				display.lightAt = minecraft.level.getGameTime();
			}
			instances.add(new Instance(key, candidate.event.visual, point, new Vec3(candidate.dx, 0, candidate.dz),
					candidate.time, band, candidate.phase, alpha, display.light, candidate.distance));
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
		SELECTION.heap = new Candidate[INITIAL_CANDIDATES];
		SELECTION.size = 0;
		HordeImpostorAtlas.clear();
		lastInstances = List.of();
		quality = 1;
		lastFrameNanos = 0;
		HordeMeshCache.clear();
		HordeAmbient.clear();
		HordeRendering.clear();
		HordeVisibility.clear();
	}

	public record Key(UUID eventId, long number) {
		@Override
		public int hashCode() {
			long mixed = HordeSamplingUtil.mix(number ^ eventId.getMostSignificantBits() ^ eventId.getLeastSignificantBits());
			return (int) (mixed ^ mixed >>> 32);
		}
	}

	public record Instance(Key key, HordeVisualDefinition visual, Vec3 position, Vec3 direction,
	                       double time, int band, int phase, float alpha, int light, double distance) {
	}

	private static class Candidate {
		private ClientEvent event;
		private long number;
		private double x, y, z, dx, dz, time, distance, score;
		private int phase;
		private float alpha;

		private void copy(Candidate value) {
			event = value.event;
			number = value.number;
			x = value.x; y = value.y; z = value.z;
			dx = value.dx; dz = value.dz;
			time = value.time;
			distance = value.distance;
			score = value.score;
			phase = value.phase;
			alpha = value.alpha;
		}
	}

	/// 有界最大堆复用候选槽位；无限模式只取消配额，不另设数量硬上限。
	private static class Selection {
		private Candidate[] heap = new Candidate[INITIAL_CANDIDATES];
		private int size, limit;

		private void begin(int maximum) {
			size = 0;
			limit = maximum;
		}

		private boolean accepts(double score) {
			return limit > 0 && (size < limit || heap[0].score > score);
		}

		private void offer(Candidate value) {
			if (!accepts(value.score)) {
				return;
			}
			if (size < limit) {
				if (size == heap.length) {
					heap = Arrays.copyOf(heap, Math.multiplyExact(heap.length, 2));
				}
				Candidate slot = heap[size];
				if (slot == null) {
					slot = new Candidate();
				}
				slot.copy(value);
				int index = size++;
				while (index > 0 && heap[(index - 1) / 2].score < slot.score) {
					heap[index] = heap[(index - 1) / 2];
					index = (index - 1) / 2;
				}
				heap[index] = slot;
				return;
			}
			Candidate slot = heap[0];
			slot.copy(value);
			int index = 0;
			while (index * 2 + 1 < size) {
				int child = index * 2 + 1;
				if (child + 1 < size && heap[child + 1].score > heap[child].score) {
					child++;
				}
				if (heap[child].score <= slot.score) {
					break;
				}
				heap[index] = heap[child];
				index = child;
			}
			heap[index] = slot;
		}

		private List<Candidate> ordered() {
			var result = Arrays.asList(heap).subList(0, size);
			result.sort((a, b) -> Double.compare(a.distance, b.distance));
			return result;
		}
	}

	private static class Display {
		private Vec3 position = Vec3.ZERO;
		private long seen;
		private long joinedAt;
		private final BlockPos.MutableBlockPos lightPosition = new BlockPos.MutableBlockPos();
		private int light;
		private long lightAt;
	}

	private static class ClientEvent {
		private final Candidate scratch = new Candidate();
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

		private void select(Vec3 camera, float partial, Selection selected, Frustum frustum) {
			if (rules == null || visual == null || length <= 0 || speed <= 0) {
				return;
			}
			var level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			double now = targetTick + Math.clamp(level.getGameTime() - receivedAt + partial, 0, 40);
			double clock = serverClock + Math.clamp(level.getGameTime() - clockReceivedAt + partial, 0, 20);
			double spacing = 1 / Math.sqrt(HordeSamplingUtil.density(rules.width(), rules.density()));
			var sampling = new SamplingFrame(camera, now, clock, spacing,
					Math.max(1, (int) Math.ceil(rules.width() / spacing)),
					(long) Math.floor(Math.min(stop, now) * speed / spacing),
					HordeVisualConfig.rangeBlocks(), HordeVisualConfig.POSE_GROUPS.get(), selected, frustum);
			var entries = new ArrayList<>(nodes.entrySet());
			for (int index = 0; index < entries.size() - 1; index++) {
				var entry = entries.get(index);
				var following = entries.get(index + 1);
				if (following.getKey() != entry.getKey() + 1) {
					continue;
				}
				var a = entry.getValue();
				var b = following.getValue();
				Vec3 start = a.at(now, rules.transitionTicks()), end = b.at(now, rules.transitionTicks());
				double range = sampling.distance + rules.width();
				double padding = rules.width() / 2 + 3;
				if (HordeSamplingUtil.segmentDistanceSqr(camera, start, end) > range * range
						|| !frustum.isVisible(new AABB(Math.min(start.x, end.x) - padding,
						Math.min(start.y, end.y) - padding, Math.min(start.z, end.z) - padding,
						Math.max(start.x, end.x) + padding, Math.max(start.y, end.y) + padding, Math.max(start.z, end.z) + padding))) {
					continue;
				}
				Vec3 direction = end.subtract(start).multiply(1, 0, 1).normalize();
				Vec3 incoming = index > 0 && entries.get(index - 1).getKey() == entry.getKey() - 1
						? end.subtract(entries.get(index - 1).getValue().at(now, rules.transitionTicks())).multiply(1, 0, 1).normalize() : direction;
				Vec3 outgoing = index + 2 < entries.size() && entries.get(index + 2).getKey() == following.getKey() + 1
						? entries.get(index + 2).getValue().at(now, rules.transitionTicks()).subtract(start).multiply(1, 0, 1).normalize() : direction;
				var segment = new SamplingSegment(start, end, incoming, outgoing, direction, a.distance(), b.distance(),
						heightBand(a, now), heightBand(b, now));
				long first = Math.max(0, (long) Math.ceil((now * speed - b.distance()) / spacing - 0.35));
				long last = Math.min(sampling.emitted, (long) Math.floor((now * speed - a.distance()) / spacing + 0.35));
				for (long row = first; row <= last; row++) {
					for (int column = 0; column < sampling.columns; column++) {
						sampleCandidate(sampling, segment, row, column);
					}
				}
			}
		}

		private HeightBand heightBand(HordeRoute.Node node, double now) {
			var values = new double[5];
			for (int i = 0; i < values.length; i++) {
				values[i] = node.height((i / 4d - 0.5) * rules.width(), now, rules.transitionTicks());
			}
			return new HeightBand(values, rules.width());
		}

		private void sampleCandidate(SamplingFrame frame, SamplingSegment segment, long row, int column) {
			long number = row * 65536 + column;
			double station = frame.now * speed - row * frame.spacing
					+ (HordeSamplingUtil.unit(seed, number, 61) - 0.5) * frame.spacing * 0.7;
			if (station < segment.first || station >= segment.last) {
				return;
			}
			double weight = Math.clamp((station - segment.first) / Math.max(0.001, segment.last - segment.first), 0, 1);
			double hx = segment.incoming.x + (segment.outgoing.x - segment.incoming.x) * weight;
			double hz = segment.incoming.z + (segment.outgoing.z - segment.incoming.z) * weight;
			double magnitude = Math.sqrt(hx * hx + hz * hz);
			if (magnitude < 0.001) {
				hx = segment.direction.x;
				hz = segment.direction.z;
			} else {
				hx /= magnitude;
				hz /= magnitude;
			}
			double baseLateral = ((column + 0.5) / frame.columns - 0.5) * rules.width();
			double lateral = baseLateral + HordeSamplingUtil.noise(seed, number, frame.now / 40) * frame.spacing * 0.35;
			double ox = segment.start.x + (segment.end.x - segment.start.x) * weight;
			double oz = segment.start.z + (segment.end.z - segment.start.z) * weight;
			double x = ox - hz * lateral, z = oz + hx * lateral;
			double y = segment.a.height(lateral) * (1 - weight) + segment.b.height(lateral) * weight;
			if (!regions.isEmpty()) {
				var point = new Vec3(x, y, z);
				var side = new Vec3(-hz, 0, hx);
				for (var region : regions.values()) {
					point = region.deflect(point, side, frame.clock, rules.transitionTicks(), baseLateral >= 0 ? 1 : -1);
				}
				x = point.x;
				z = point.z;
				double adjusted = (x - ox) * -hz + (z - oz) * hx;
				y = segment.a.height(adjusted) * (1 - weight) + segment.b.height(adjusted) * weight;
			}
			double cx = x - frame.camera.x, cy = y - frame.camera.y, cz = z - frame.camera.z;
			double distanceSquared = cx * cx + cy * cy + cz * cz;
			if (distanceSquared > frame.distance * frame.distance) {
				return;
			}
			double distance = Math.sqrt(distanceSquared);
			double score = distance + HordeSamplingUtil.unit(seed, number, 31) * 8;
			candidates++;
			if (!frame.selected.accepts(score) || !HordeVisibility.visible(x, y, z, frame.frustum)) {
				return;
			}
			scratch.event = this;
			scratch.number = number;
			scratch.x = x; scratch.y = y; scratch.z = z;
			scratch.dx = hx; scratch.dz = hz;
			scratch.time = frame.now;
			scratch.distance = distance;
			scratch.score = score;
			scratch.phase = (int) (HordeSamplingUtil.unit(seed, number, 47) * frame.phaseGroups);
			scratch.alpha = HordeSamplingUtil.endpointAlpha(station, length, speed, rules.fadeTicks());
			frame.selected.offer(scratch);
		}
	}

	private record SamplingFrame(Vec3 camera, double now, double clock, double spacing, int columns, long emitted,
	                             double distance, int phaseGroups, Selection selected, Frustum frustum) {}

	private record SamplingSegment(Vec3 start, Vec3 end, Vec3 incoming, Vec3 outgoing, Vec3 direction,
	                               double first, double last, HeightBand a, HeightBand b) {}

	private record HeightBand(double[] values, double width) {
		private double height(double lateral) {
			double position = Math.clamp(lateral / width + 0.5, 0, 1) * 4;
			int low = Math.min(3, (int) position);
			return values[low] + (values[low + 1] - values[low]) * (position - low);
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
