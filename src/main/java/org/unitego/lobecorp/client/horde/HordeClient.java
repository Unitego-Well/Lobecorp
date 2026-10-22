package org.unitego.lobecorp.client.horde;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.network.ts.horde.HordeSnapshotRequestPayload;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeRoute;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/// 接收事件快照并按空间块提交编号区间；CPU 不计算逐单位位置。
public class HordeClient {
	private static final Map<UUID, ClientEvent> EVENTS = new TreeMap<>();
	private static final double BLOCK_WIDTH = 16;
	private static final double MODEL_PADDING = 6;
	private static List<Batch> lastBatches = List.of();
	private static Object generation;

	public static void apply(CompoundTag packet) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		try {
			UUID id = UUID.fromString(packet.getStringOr("id", ""));
			String kind = packet.getStringOr("kind", "");
			if (kind.equals("remove")) {
				EVENTS.remove(id);
				HordeGpuRenderer.remove(id);
				return;
			}
			long sequence = packet.getLongOr("sequence", 0);
			CompoundTag body = packet.getCompoundOrEmpty("body");
			ClientEvent event = EVENTS.get(id);
			boolean reset = kind.equals("header") && body.getBooleanOr("reset", false);
			if (reset) {
				event = new ClientEvent(id);
				EVENTS.put(id, event);
				HordeGpuRenderer.remove(id);
			} else if (event == null || sequence != event.sequence + 1) {
				if (event == null || minecraft.level.getGameTime() - event.lastRequest >= 40) {
					if (event != null) {
						event.lastRequest = minecraft.level.getGameTime();
					}
					ClientPacketDistributor.sendToServer(new HordeSnapshotRequestPayload(id));
				}
				return;
			}
			event.sequence = sequence;
			switch (kind) {
				case "header" -> event.header(body);
				case "route" -> event.route(body);
				case "regions" -> event.regions(body);
				default -> {
				}
			}
		} catch (IllegalArgumentException exception) {
			Lobecorp.LOGGER.warn("忽略无效群体事件快照", exception);
		}
	}

	public static List<Batch> extract(float partialTick, Frustum frustum) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !HordeVisualConfig.ENABLED.get()) {
			lastBatches = List.of();
			return lastBatches;
		}
		if (generation != GeckoLibResources.getBakedModels()) {
			generation = GeckoLibResources.getBakedModels();
			HordeRendering.clear();
			HordeMeshCache.clear();
			EVENTS.values().forEach(event -> event.failed = false);
		}

		var camera = minecraft.gameRenderer.getMainCamera().position();
		var batches = new ArrayList<Batch>();
		double range = HordeVisualConfig.rangeBlocks();
		for (var event : EVENTS.values()) {
			if (!event.ready || event.failed) {
				continue;
			}
			double now = event.time(partialTick);
			for (var block : event.blocks) {
				if (block.bounds().distanceToSqr(camera) > range * range || !frustum.isVisible(block.bounds())
						|| !loaded(block.bounds())) {
					continue;
				}
				long first = Math.max(0, (long) Math.floor((now * event.speed - block.last()) / event.rules.rowSpacing()) + 1);
				long last = Math.min((long) Math.floor(Math.min(event.stop, now) * event.speed / event.rules.rowSpacing()),
						(long) Math.floor((now * event.speed - block.first()) / event.rules.rowSpacing()));
				if (last < first) {
					continue;
				}
				int columns = block.columns();
				long count = Math.multiplyExact(last - first + 1, columns);
				for (long offset = 0; offset < count; ) {
					int size = (int) Math.min(Integer.MAX_VALUE, count - offset);
					batches.add(new Batch(event, block, first, offset, size, now));
					offset += size;
				}
			}
		}

		if (HordeVisualConfig.MAX_VISIBLE.get() >= 0) {
			batches.sort(Comparator.comparingDouble((Batch batch) -> batch.block().bounds().distanceToSqr(camera))
					.thenComparing(batch -> batch.event().id).thenComparingInt(batch -> batch.block().index()));
			int remaining = HordeVisualConfig.MAX_VISIBLE.get();
			var limited = new ArrayList<Batch>();
			for (var batch : batches) {
				if (remaining == 0) {
					break;
				}
				int count = Math.min(remaining, batch.count());
				limited.add(new Batch(batch.event(), batch.block(), batch.firstRow(), batch.offset(), count, batch.time()));
				remaining -= count;
			}
			batches = limited;
		}
		lastBatches = List.copyOf(batches);
		return lastBatches;
	}

	private static boolean loaded(AABB bounds) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(bounds.maxX) >> 4; x++) {
			for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(bounds.maxZ) >> 4; z++) {
				if (level.getChunkSource().getChunkNow(x, z) != null) {
					return true;
				}
			}
		}
		return false;
	}

	public static List<Batch> batches() {
		return lastBatches;
	}

	public static String statistics() {
		return "Horde events=" + EVENTS.size() + ", blocks=" + lastBatches.size()
				+ ", submitted=" + lastBatches.stream().mapToLong(Batch::count).sum();
	}

	public static void clear() {
		EVENTS.clear();
		lastBatches = List.of();
		generation = null;
		HordeRendering.clear();
		HordeMeshCache.clear();
		HordeAmbient.clear();
	}

	public record Block(int index, double first, double last, int firstColumn, int columns, AABB bounds, Vec3 center) {
	}

	public record Batch(ClientEvent event, Block block, long firstRow, long offset, int count, double time) {
	}

	public static class ClientEvent {
		public final UUID id;
		public HordeRules rules;
		public HordeVisualDefinition visual;
		public HordeRoute.Node[] nodes;
		public Vec3 origin;
		public float[] routeData;
		public List<Block> blocks = List.of();
		public final Map<String, Region> regions = new TreeMap<>();
		public double length;
		public double speed;
		public float lightProbeHeight;
		public long stop;
		public int columns;
		public boolean ready;
		public boolean failed;
		public long regionVersion;
		private final Map<Integer, HordeRoute.Node> received = new TreeMap<>();
		private final Map<String, CompoundTag> pendingRegions = new TreeMap<>();
		private long regionBatch = -1;
		private long sequence;
		private long lastRequest = Long.MIN_VALUE / 2;
		private long targetTick;
		private long receivedAt;
		private int nodeCount;

		private ClientEvent(UUID id) {
			this.id = id;
		}

		public double time(float partial) {
			var minecraft = Minecraft.getInstance();
			var level = minecraft.level;
			return level == null ? targetTick : targetTick + Math.clamp(level.getGameTime() - receivedAt + partial, 0, 40);
		}

		private void header(CompoundTag tag) {
			var level = Minecraft.getInstance().level;
			if (level == null || tag.getIntOr("format", 0) != 1) {
				throw new IllegalArgumentException("不支持的群体事件同步版本");
			}
			if (!tag.getStringOr("dimension", "").equals(level.dimension().identifier().toString())) {
				throw new IllegalArgumentException("群体事件维度不匹配");
			}
			rules = HordeRules.load(tag.getCompoundOrEmpty("rules"));
			targetTick = tag.getLongOr("tick", 0);
			receivedAt = level.getGameTime();
			stop = tag.getLongOr("stop", rules.duration());
			length = tag.getDoubleOr("length", 0);
			speed = tag.getDoubleOr("speed", 0);
			lightProbeHeight = (float) tag.getDoubleOr("light_probe_height", 1);
			nodeCount = tag.getIntOr("node_count", 0);
			if (!Double.isFinite(length) || length <= 0 || !Double.isFinite(speed) || speed <= 0 || nodeCount < 2 || nodeCount > 8192) {
				throw new IllegalArgumentException("群体事件线路或速度无效");
			}
			visual = new HordeVisualDefinition(Identifier.parse(tag.getStringOr("model", "")),
					Identifier.parse(tag.getStringOr("animation", "")), tag.getStringOr("run", "run"),
					Identifier.parse(tag.getStringOr("texture", "")));
			columns = Math.max(1, (int) Math.floor(rules.width() / rules.columnSpacing()));
		}

		private void route(CompoundTag page) {
			if (ready) {
				return;
			}
			int index = page.getIntOr("first", 0);
			for (var element : page.getListOrEmpty("nodes")) {
				if (element instanceof CompoundTag node && index >= 0 && index < nodeCount) {
					received.put(index++, HordeRoute.Node.load(node));
				}
			}
			if (received.size() == nodeCount) {
				prepareRoute();
				received.clear();
				ready = true;
			}
		}

		private void prepareRoute() {
			nodes = received.values().toArray(HordeRoute.Node[]::new);
			origin = nodes[0].at();
			int lookupCount = (int) Math.ceil(length) + 1;
			routeData = new float[Math.addExact(Math.multiplyExact(nodes.length, 16), lookupCount)];
			var prepared = new ArrayList<Block>();
			for (int i = 0; i < nodes.length; i++) {
				var point = nodes[i].at().subtract(origin);
				var before = nodes[Math.max(0, i - 1)].at();
				var after = nodes[Math.min(nodes.length - 1, i + 1)].at();
				var direction = after.subtract(before).multiply(1, 0, 1).normalize();
				int at = i * 16;
				routeData[at] = (float) point.x;
				routeData[at + 1] = (float) point.y;
				routeData[at + 2] = (float) point.z;
				routeData[at + 3] = (float) nodes[i].distance();
				routeData[at + 4] = (float) direction.x;
				routeData[at + 5] = (float) direction.z;
				double width = rules.width() + rules.regionMargin() * 2;
				for (int h = 0; h < 5; h++) {
					routeData[at + 8 + h] = (float) (nodes[i].height((h / 4d - 0.5) * width) - origin.y);
				}
				if (i + 1 < nodes.length) {
					prepareBlocks(i, prepared);
				}
			}
			int segment = 0;
			for (int i = 0; i < lookupCount; i++) {
				while (segment + 2 < nodes.length && nodes[segment + 1].distance() <= i) {
					segment++;
				}
				routeData[nodes.length * 16 + i] = segment;
			}
			blocks = List.copyOf(prepared);
		}

		private void prepareBlocks(int segment, List<Block> prepared) {
			var a = nodes[segment].at();
			var b = nodes[segment + 1].at();
			double padding = rules.regionMargin() + MODEL_PADDING;
			int blockColumns = Math.max(1, (int) Math.floor(BLOCK_WIDTH / rules.columnSpacing()));
			for (int first = 0; first < columns; first += blockColumns) {
				int count = Math.min(blockColumns, columns - first);
				double low = (first - (columns - 1) / 2d) * rules.columnSpacing() - padding;
				double high = (first + count - 1 - (columns - 1) / 2d) * rules.columnSpacing() + padding;
				AABB bounds = blockBounds(segment, low, high);
				double lateral = (first + (count - 1) / 2d - (columns - 1) / 2d) * rules.columnSpacing();
				var forward = b.subtract(a).multiply(1, 0, 1).normalize();
				double height = (nodes[segment].height(lateral) + nodes[segment + 1].height(lateral)) / 2;
				var center = new Vec3((a.x + b.x) / 2 - forward.z * lateral, height, (a.z + b.z) / 2 + forward.x * lateral);
				prepared.add(new Block(prepared.size(), nodes[segment].distance(), nodes[segment + 1].distance(), first, count, bounds, center));
			}
		}

		private AABB blockBounds(int segment, double low, double high) {
			var a = nodes[segment].at();
			var b = nodes[segment + 1].at();
			var incoming = nodes[segment + 1].at().subtract(nodes[Math.max(0, segment - 1)].at());
			var outgoing = nodes[Math.min(nodes.length - 1, segment + 2)].at().subtract(nodes[segment].at());
			double start = Math.atan2(incoming.x, -incoming.z);
			double end = Math.atan2(outgoing.x, -outgoing.z);
			double turn = Math.atan2(Math.sin(end - start), Math.cos(end - start));
			double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
			for (double angle : new double[]{start, end, 0, Math.PI / 2, Math.PI, -Math.PI / 2}) {
				double relative = Math.atan2(Math.sin(angle - start), Math.cos(angle - start));
				if (angle != start && angle != end && (turn >= 0 ? relative < 0 || relative > turn : relative > 0 || relative < turn)) {
					continue;
				}
				for (double lateral : new double[]{low, high}) {
					double x = Math.cos(angle) * lateral, z = Math.sin(angle) * lateral;
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
			double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
			double width = rules.width() + rules.regionMargin() * 2;
			for (int i = segment; i <= segment + 1; i++) {
				for (int h = 0; h < 5; h++) {
					double y = nodes[i].height((h / 4d - 0.5) * width);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
			return new AABB(Math.min(a.x, b.x) + minX - MODEL_PADDING, minY - MODEL_PADDING,
					Math.min(a.z, b.z) + minZ - MODEL_PADDING, Math.max(a.x, b.x) + maxX + MODEL_PADDING,
					maxY + MODEL_PADDING, Math.max(a.z, b.z) + maxZ + MODEL_PADDING);
		}

		private void regions(CompoundTag page) {
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
			if (!page.getBooleanOr("last", false)) {
				return;
			}
			double now = time(0);
			boolean changed = false;
			for (var entry : pendingRegions.entrySet()) {
				changed |= regions.computeIfAbsent(entry.getKey(), _ -> new Region())
						.update(entry.getValue(), now, rules.transitionTicks());
			}
			for (var entry : regions.entrySet()) {
				if (!pendingRegions.containsKey(entry.getKey())) {
					changed |= entry.getValue().remove(now, rules.transitionTicks());
				}
			}
			changed |= regions.entrySet().removeIf(entry -> entry.getValue().expired(now, rules.transitionTicks()));
			if (changed) {
				regionVersion++;
			}
			pendingRegions.clear();
			regionBatch = -1;
		}
	}

	public static class Region {
		public Vec3 from = Vec3.ZERO;
		public Vec3 to = Vec3.ZERO;
		public double fromX, fromZ, toX, toZ, changed;
		private boolean initialized;

		public boolean update(CompoundTag tag, double now, int transition) {
			var center = new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0), tag.getDoubleOr("z", 0));
			boolean active = tag.getBooleanOr("active", true);
			double rx = active ? Math.max(0, tag.getDoubleOr("rx", 0)) : 0;
			double rz = active ? Math.max(0, tag.getDoubleOr("rz", 0)) : 0;
			return set(center, rx, rz, now, transition);
		}

		private boolean set(Vec3 center, double rx, double rz, double now, int transition) {
			if (initialized && to.equals(center) && toX == rx && toZ == rz) {
				return false;
			}
			double weight = HordeSamplingUtil.smooth((now - changed) / transition);
			from = initialized ? from.lerp(to, weight) : center;
			fromX = initialized ? fromX + (toX - fromX) * weight : 0;
			fromZ = initialized ? fromZ + (toZ - fromZ) * weight : 0;
			to = center;
			toX = rx;
			toZ = rz;
			changed = now;
			initialized = true;
			return true;
		}

		public boolean remove(double now, int transition) {
			return set(to, 0, 0, now, transition);
		}

		public boolean expired(double now, int transition) {
			return toX == 0 && toZ == 0 && now - changed >= transition;
		}
	}
}
