package org.unitego.lobecorp.client.horde;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.horde.HordeParticipant;
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

/// 接收事件快照；每tick提交玩家模拟距离内的整数区域，GPU负责分割运动。
public class HordeClient {
	private static final Map<UUID, ClientEvent> EVENTS = new TreeMap<>();
	private static final Map<UUID, Long> SNAPSHOT_REQUESTS = new TreeMap<>();
	private static final double BLOCK_WIDTH = 16;
	private static final int BLOCK_SEGMENTS = 16;
	private static final double MODEL_PADDING = 6;
	// 水平分割宽深按实际体型三倍，Y仍采用真实高度，避免高处误分割。
	private static final double VISUAL_GAP_SCALE = 3;
	// 从目标包围盒向外至少三格；GPU另加清道夫模型半径。
	private static final double MIN_VISUAL_CLEARANCE = 3;
	private static List<Batch> lastBatches = List.of();
	private static Object generation;
	private static int meshSettings = Integer.MIN_VALUE;
	private static Frustum ambientFrustum;

	public static void apply(CompoundTag packet) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		UUID id;
		try {
			id = UUID.fromString(packet.getStringOr("id", ""));
		} catch (IllegalArgumentException exception) {
			Lobecorp.LOGGER.warn("忽略无效群体事件标识", exception);
			return;
		}
		try {
			String kind = packet.getStringOr("kind", "");
			if (kind.equals("remove")) {
				EVENTS.remove(id);
				SNAPSHOT_REQUESTS.remove(id);
				HordeGpuRenderer.remove(id);
				return;
			}
			long sequence = packet.getLongOr("sequence", 0);
			CompoundTag body = packet.getCompoundOrEmpty("body");
			ClientEvent event = EVENTS.get(id);
			boolean reset = kind.equals("header") && body.getBooleanOr("reset", false);
			if (reset) {
				var replacement = new ClientEvent(id);
				replacement.header(body);
				replacement.sequence = sequence;
				EVENTS.put(id, replacement);
				SNAPSHOT_REQUESTS.remove(id);
				HordeGpuRenderer.remove(id);
				return;
			}
			if (event == null || sequence != event.sequence + 1) {
				requestSnapshot(id);
				return;
			}
			switch (kind) {
				case "header" -> event.header(body);
				case "route" -> event.route(body);
				default -> throw new IllegalArgumentException("未知群体事件同步页面");
			}
			event.sequence = sequence;
		} catch (IllegalArgumentException exception) {
			requestSnapshot(id);
			Lobecorp.LOGGER.warn("忽略无效群体事件快照 {}", id, exception);
		}
	}

	private static void requestSnapshot(UUID id) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		if (now - SNAPSHOT_REQUESTS.getOrDefault(id, Long.MIN_VALUE / 2) >= 40) {
			SNAPSHOT_REQUESTS.put(id, now);
			ClientPacketDistributor.sendToServer(new HordeSnapshotRequestPayload(id));
		}
	}

	public static void tick() {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || EVENTS.isEmpty() || !HordeVisualConfig.ENABLED.get()) {
			return;
		}
		var player = minecraft.player;
		var area = simulationBounds();
		var nearby = minecraft.level.getEntitiesOfClass(Mob.class, area, entity -> entity.isAlive() && !entity.isSpectator());
		var targets = nearby.stream().filter(ClientEvent::isGapTarget).toList();
		var members = nearby.stream().filter(HordeParticipant::isEvent).toList();
		for (var event : EVENTS.values()) {
			if (event.ready && !event.failed) {
				event.updateGaps(targets, members, player);
			}
		}
	}


	private static AABB simulationBounds() {
		var minecraft = Minecraft.getInstance();
		int chunks = minecraft.level.getServerSimulationDistance();
		var chunk = minecraft.player.chunkPosition();
		return new AABB((chunk.x() - chunks) * 16d, minecraft.level.getMinY(), (chunk.z() - chunks) * 16d,
				(chunk.x() + chunks + 1) * 16d, minecraft.level.getMaxY(), (chunk.z() + chunks + 1) * 16d);
	}

	public static List<DebugRange> debugRanges() {
		var minecraft = Minecraft.getInstance();
		var result = new ArrayList<DebugRange>();
		if (minecraft.level == null || minecraft.player == null) {
			return result;
		}
		double range = HordeVisualConfig.rangeBlocks();
		var player = minecraft.player.position();
		result.add(new DebugRange(simulationBounds(), 0xFF00FF00));
		result.add(new DebugRange(new AABB(player.x - range, player.y - 8, player.z - range,
				player.x + range, player.y + 8, player.z + range), 0xFFFFFF00));
		for (var event : EVENTS.values()) {
			if (event.ready && !event.failed && event.routeBounds != null) {
				result.add(new DebugRange(event.routeBounds, 0xFF00FFFF));
				for (var box : event.regions.values()) {
					result.add(new DebugRange(new AABB(box.x(), box.y(), box.z(),
							box.x() + box.sizeX(), box.y() + box.sizeY(), box.z() + box.sizeZ()), 0xFFFF8000));
				}
			}
		}
		return result;
	}

	public static List<Batch> extract(float partialTick, Frustum frustum) {
		ambientFrustum = frustum;
		long extractionStarted = HordeRenderMetrics.extractStarted();
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !HordeVisualConfig.ENABLED.get()) {
			lastBatches = List.of();
			return lastBatches;
		}
		if (generation != GeckoLibResources.getBakedModels() || meshSettings != HordeMeshCache.settingsKey()) {
			generation = GeckoLibResources.getBakedModels();
			meshSettings = HordeMeshCache.settingsKey();
			HordeGpuRenderer.clearModels();
			HordeMeshCache.clear();
			EVENTS.values().forEach(event -> event.failed = false);
		}

		for (var event : EVENTS.values()) {
			if (!event.failed && event.visual != null) {
				HordeGpuRenderer.requestResources(event.visual);
			}
		}
		if (!EVENTS.isEmpty()) {
			HordeGpuRenderer.prepareResources();
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
				if (block.bounds().distanceToSqr(camera) > range * range || !frustum.isVisible(block.bounds())) {
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
		HordeRenderMetrics.extracted(extractionStarted, lastBatches.size());
		return lastBatches;
	}


	public static boolean ambientVisible(Vec3 point) {
		return ambientFrustum != null && HordeVisualConfig.ENABLED.get()
				&& ambientFrustum.isVisible(new AABB(point, point).inflate(0.5));
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
		SNAPSHOT_REQUESTS.clear();
		lastBatches = List.of();
		generation = null;
		meshSettings = Integer.MIN_VALUE;
		ambientFrustum = null;
		HordeRendering.clear();
		HordeMeshCache.clear();
		HordeAmbient.clear();
	}

	public record Block(int index, double first, double last, int firstColumn, int columns, AABB bounds, Vec3 center) {
	}

	public record DebugRange(AABB bounds, int color) {
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
		public float[] heightData;
		public int heightColumns;
		public double groundWidth;
		public final java.util.BitSet changedHeights = new java.util.BitSet();
		public List<Block> blocks = List.of();
		public final Map<String, RegionBox> regions = new TreeMap<>();
		public final Map<String, RegionBox> previousRegions = new TreeMap<>();
		public double regionsChanged;
		public float entityHeight;
		public double length;
		public double speed;
		public float lightProbeHeight;
		public long stop;
		public int columns;
		public boolean ready;
		public boolean failed;
		public long regionVersion;
		private AABB routeBounds;
		private final Map<Integer, HordeRoute.Node> received = new TreeMap<>();
		private long sequence;
		private final HordeRenderClock clock = new HordeRenderClock();
		private int nodeCount;

		private ClientEvent(UUID id) {
			this.id = id;
		}

		public double time(float partial) {
			var minecraft = Minecraft.getInstance();
			var level = minecraft.level;
			return clock.time(level == null ? 0 : level.getGameTime() + partial);
		}

		private void header(CompoundTag tag) {
			var level = Minecraft.getInstance().level;
			if (level == null || tag.getIntOr("format", 0) != 1) {
				throw new IllegalArgumentException("不支持的群体事件同步版本");
			}
			if (!tag.getStringOr("dimension", "").equals(level.dimension().identifier().toString())) {
				throw new IllegalArgumentException("群体事件维度不匹配");
			}
			var nextRules = HordeRules.load(tag.getCompoundOrEmpty("rules"));
			double nextLength = tag.getDoubleOr("length", 0);
			double nextSpeed = tag.getDoubleOr("speed", 0);
			int nextNodeCount = tag.getIntOr("node_count", 0);
			if (!Double.isFinite(nextLength) || nextLength <= 0 || !Double.isFinite(nextSpeed)
					|| nextSpeed <= 0 || nextNodeCount < 2 || nextNodeCount > 8192) {
				throw new IllegalArgumentException("群体事件线路或速度无效");
			}
			var nextVisual = new HordeVisualDefinition(Identifier.parse(tag.getStringOr("model", "")),
					Identifier.parse(tag.getStringOr("animation", "")), tag.getStringOr("run", "run"),
					Identifier.parse(tag.getStringOr("texture", "")));
			if (ready && (nextNodeCount != nodeCount || nextLength != length || !nextRules.equals(rules)
					|| !nextVisual.equals(visual))) {
				throw new IllegalArgumentException("固定线路或资源变更必须提供完整快照");
			}
			rules = nextRules;
			clock.synchronize(tag.getLongOr("tick", 0), level.getGameTime());
			stop = tag.getLongOr("stop", nextRules.duration());
			length = nextLength;
			speed = nextSpeed;
			lightProbeHeight = (float) tag.getDoubleOr("light_probe_height", 1);
			entityHeight = (float) tag.getDoubleOr("entity_height", lightProbeHeight);
			if (!Float.isFinite(entityHeight) || entityHeight <= 0) {
				throw new IllegalArgumentException("群体实体高度无效");
			}
			nodeCount = nextNodeCount;
			visual = nextVisual;
			HordeGpuRenderer.requestResources(nextVisual);
			columns = Math.max(1, (int) Math.floor(rules.width() / rules.columnSpacing()));
		}

		private void route(CompoundTag page) {
			int index = page.getIntOr("first", 0);
			for (var element : page.getListOrEmpty("nodes")) {
				if (element instanceof CompoundTag node && index >= 0 && index < nodeCount) {
					var replacement = HordeRoute.Node.load(node);
					if (ready) {
						if (replacement.revision() > nodes[index].revision()) {
							var previous = nodes[index];
							if (replacement.heightCount() != heightColumns || replacement.sampleWidth() != groundWidth
									|| replacement.distance() != previous.distance()
									|| replacement.at().x != previous.at().x || replacement.at().z != previous.at().z) {
								throw new IllegalArgumentException("线路结构变化需要完整快照");
							}
							nodes[index] = replacement;
							updateHeights(index);
						}
					} else {
						received.put(index, replacement);
					}
					index++;
				}
			}
			if (!ready && received.size() == nodeCount) {
				prepareRoute();
				received.clear();
				ready = true;
			}
		}

		private void prepareRoute() {
			nodes = received.values().toArray(HordeRoute.Node[]::new);
			origin = nodes[0].at();
			heightColumns = nodes[0].heightCount();
			groundWidth = nodes[0].sampleWidth();
			heightData = new float[nodes.length * heightColumns];
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
				double width = groundWidth;
				for (int h = 0; h < heightColumns; h++) {
					heightData[i * heightColumns + h] = (float) (nodes[i].height((h / (double) (heightColumns - 1) - 0.5) * width) - origin.y);
				}
				if (i + 1 < nodes.length && i % BLOCK_SEGMENTS == 0) {
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
			double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
			for (var node : nodes) {
				var at = node.at();
				minX = Math.min(minX, at.x);
				maxX = Math.max(maxX, at.x);
				minY = Math.min(minY, at.y);
				maxY = Math.max(maxY, at.y);
				minZ = Math.min(minZ, at.z);
				maxZ = Math.max(maxZ, at.z);
			}
			double padding = rules.width() / 2 + rules.regionMargin() + HordeSamplingUtil.MAX_VISUAL_OFFSET + MODEL_PADDING;
			routeBounds = new AABB(minX - padding, minY - 24, minZ - padding,
					maxX + padding, maxY + 24, maxZ + padding);
		}

		private void updateHeights(int index) {
			double width = groundWidth;
			for (int h = 0; h < heightColumns; h++) {
				heightData[index * heightColumns + h] = (float) (nodes[index].height((h / (double) (heightColumns - 1) - 0.5) * width) - origin.y);
			}
			changedHeights.set(index);
			var updated = new ArrayList<>(blocks);
			for (int group = Math.max(0, index - 1) / BLOCK_SEGMENTS; group <= Math.min(nodes.length - 2, index) / BLOCK_SEGMENTS; group++) {
				var replacements = new ArrayList<Block>();
				prepareBlocks(group * BLOCK_SEGMENTS, replacements);
				for (var block : replacements) {
					updated.set(block.index(), block);
				}
			}
			blocks = List.copyOf(updated);
		}

		private void prepareBlocks(int segment, List<Block> prepared) {
			int end = Math.min(nodes.length - 1, segment + BLOCK_SEGMENTS);
			var a = nodes[segment].at();
			var b = nodes[end].at();
			double padding = rules.regionMargin() + HordeSamplingUtil.MAX_VISUAL_OFFSET + MODEL_PADDING;
			int blockColumns = Math.max(1, (int) Math.floor(BLOCK_WIDTH / rules.columnSpacing()));
			for (int first = 0; first < columns; first += blockColumns) {
				int count = Math.min(blockColumns, columns - first);
				double low = (first - (columns - 1) / 2d) * rules.columnSpacing() - padding;
				double high = (first + count - 1 - (columns - 1) / 2d) * rules.columnSpacing() + padding;
				AABB bounds = blockBounds(segment, low, high);
				for (int i = segment + 1; i < end; i++) {
					bounds = bounds.minmax(blockBounds(i, low, high));
				}
				double lateral = (first + (count - 1) / 2d - (columns - 1) / 2d) * rules.columnSpacing();
				var forward = b.subtract(a).multiply(1, 0, 1).normalize();
				double height = (nodes[segment].height(lateral) + nodes[end].height(lateral)) / 2;
				var center = new Vec3((a.x + b.x) / 2 - forward.z * lateral, height, (a.z + b.z) / 2 + forward.x * lateral);
				prepared.add(new Block(segment / BLOCK_SEGMENTS * Math.ceilDiv(columns, blockColumns) + first / blockColumns, nodes[segment].distance(), nodes[end].distance(), first, count, bounds, center));
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
			double width = groundWidth;
			for (int i = segment; i <= segment + 1; i++) {
				for (int h = 0; h < heightColumns; h++) {
					double y = nodes[i].height((h / (double) (heightColumns - 1) - 0.5) * width);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
			return new AABB(Math.min(a.x, b.x) + minX - MODEL_PADDING, minY - MODEL_PADDING,
					Math.min(a.z, b.z) + minZ - MODEL_PADDING, Math.max(a.x, b.x) + maxX + MODEL_PADDING,
					maxY + MODEL_PADDING, Math.max(a.z, b.z) + maxZ + MODEL_PADDING);
		}

		private void updateGaps(List<Mob> targets, List<Mob> members, Player player) {
			var updated = new TreeMap<String, RegionBox>();
			updated.put("local", RegionBox.forEntity(player.getBoundingBox()));
			for (var entity : targets) {
				updated.put("target:" + entity.getUUID(),
						RegionBox.forEntity(entity.getBoundingBox()));
			}

			// 成员不单独创建缺口，只扩展同层、正在参战目标的已有战斗包围。
			var seeds = members.isEmpty() ? Map.<String, RegionBox>of() : Map.copyOf(updated);
			for (var member : members) {
				var source = HordeParticipant.get(member);
				var key = "target:" + source.targetId();
				var target = seeds.get(key);
				if (target == null || source.fading() || !source.eventId().equals(id.toString())) {
					continue;
				}
				updated.put(key, RegionBox.includeCombat(target, updated.get(key), member.getBoundingBox(), rules.regionMargin()));
			}

			if (!regions.equals(updated) || !previousRegions.equals(regions)) {
				previousRegions.clear();
				previousRegions.putAll(regions);
				regions.clear();
				regions.putAll(updated);
				regionsChanged = time(0);
				regionVersion++;
			}
		}


		private static boolean isGapTarget(LivingEntity entity) {
            return entity.isAlive() && !entity.isSpectator() && !HordeParticipant.isEvent(entity);
        }
    }

    /// 向GPU提交的整数世界坐标和尺寸，向外取整，避免截断负坐标及实体边界。
    public record RegionBox(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
	    public static RegionBox forEntity(AABB bounds) {
		    double extra = (VISUAL_GAP_SCALE - 1) * 0.5;
		    return of(bounds.inflate(Math.max(MIN_VISUAL_CLEARANCE, bounds.getXsize() * extra), 0,
				    Math.max(MIN_VISUAL_CLEARANCE, bounds.getZsize() * extra)), 1);
	    }


	    public static RegionBox includeCombat(RegionBox target, RegionBox current, AABB member, double margin) {
		    var original = new AABB(target.x(), target.y(), target.z(), target.x() + target.sizeX(),
				    target.y() + target.sizeY(), target.z() + target.sizeZ());
		    if (member.minY >= original.maxY + 0.5 || member.maxY <= original.minY - 0.5
				    || original.distanceToSqr(member.getCenter()) > margin * margin) {
			    return current;
		    }
		    var combined = new AABB(current.x(), current.y(), current.z(), current.x() + current.sizeX(),
				    current.y() + current.sizeY(), current.z() + current.sizeZ());
		    return of(combined.minmax(member.inflate(MIN_VISUAL_CLEARANCE, 0, MIN_VISUAL_CLEARANCE)), 1);
	    }

	    public static RegionBox of(AABB bounds, int grid) {
            int x = Math.floorDiv((int) Math.floor(bounds.minX), grid) * grid;
            int y = Math.floorDiv((int) Math.floor(bounds.minY), grid) * grid;
            int z = Math.floorDiv((int) Math.floor(bounds.minZ), grid) * grid;
            return new RegionBox(x, y, z, Math.ceilDiv((int) Math.ceil(bounds.maxX) - x, grid) * grid,
                    Math.ceilDiv((int) Math.ceil(bounds.maxY) - y, grid) * grid,
                    Math.ceilDiv((int) Math.ceil(bounds.maxZ) - z, grid) * grid);
		}
	}

	public static class Region {
		public Vec3 from = Vec3.ZERO;
		public Vec3 to = Vec3.ZERO;
		public double fromX, fromZ, toX, toZ, changed;
		private boolean initialized;

		public boolean update(Vec3 center, double rx, double rz, double now, int transition) {
			return set(center, Math.max(0, rx), Math.max(0, rz), now, transition);
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
