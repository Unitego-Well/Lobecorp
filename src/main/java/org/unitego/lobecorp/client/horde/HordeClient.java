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
	// 圆核心按水平体型1.5倍，Y仍采用真实高度，避免高处误分割。
	private static final double VISUAL_GAP_SCALE = 1.5;
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
				closeEvent(EVENTS.remove(id));
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
				closeEvent(EVENTS.put(id, replacement));
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

	/// 事件被替换或移除时释放它的分割圆表纹理。
	private static void closeEvent(ClientEvent event) {
		if (event != null) {
			event.split.close();
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
		var nearby = minecraft.level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && !entity.isSpectator());
		var targets = nearby.stream().filter(entity -> entity != player && ClientEvent.isGapTarget(entity)).toList();
		var members = nearby.stream().filter(Mob.class::isInstance).map(Mob.class::cast).filter(HordeParticipant::isEvent).toList();
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
					if (box == null) {
						continue;
					}
					result.add(new DebugRange(new AABB(box.x(), box.y(), box.z(),
							box.x() + box.sizeX(), box.y() + box.sizeY(), box.z() + box.sizeZ()), 0xFFFF8000));
				}
			}
		}
		return result;
	}

	public static List<Batch> extract(float partialTick, Frustum frustum) {
		ambientFrustum = frustum;
		HordeDebugState.beginFrame();
		HordeDebugState.setEvents(EVENTS.size());
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
		long submitted = 0;
		for (var batch : lastBatches) {
			submitted += batch.count();
		}
		HordeDebugState.setBatches(lastBatches.size(), submitted);
		for (var event : EVENTS.values()) {
			if (!event.ready || event.failed) {
				continue;
			}
			double now = event.time(partialTick);
			double head = HordeTide.head(now, event.speed);
			HordeDebugState.setTide(head, Math.max(0, head - event.length), 0,
					(long) Math.floor(Math.max(0, Math.min(head, event.length)) / event.rules.rowSpacing()));
			break;
		}
		HordeRenderMetrics.extracted(extractionStarted, lastBatches.size());
		return lastBatches;
	}


	public static boolean ambientVisible(Vec3 point) {
		return ambientFrustum != null && HordeVisualConfig.ENABLED.get()
				&& ambientFrustum.isVisible(new AABB(point, point).inflate(0.5));
	}

	/// 含玩家自身核心的目标圆查询；仅供粒子候选筛选，不代表GPU平滑状态或实际单位占据。
	public static boolean ambientGapClear(Vec3 point) {
		for (var event : EVENTS.values()) {
			if (!event.ready || event.failed) {
				continue;
			}
			for (var box : event.regions.values()) {
				if (box.contains(point)) {
					return false;
				}
			}
		}
		return true;
	}

	/// 含玩家自身核心的胶囊引导轮廓有符号距离；仅用于CPU候选，实际单位占据须查询GPU。
	public static double ambientGapBoundaryDistance(ClientEvent event, Vec3 point, Vec3 forward) {
		double distance = Double.POSITIVE_INFINITY;
		for (var box : event.regions.values()) {
			distance = Math.min(distance, box.contourDistance(point, forward, event.speed));
		}
		return distance;
	}

	public static List<Batch> batches() {
		return lastBatches;
	}

	/// 只读事件集合，供调试叠层与 Gizmo 读取当帧状态。
	public static List<ClientEvent> events() {
		return List.copyOf(EVENTS.values());
	}

	public static String statistics() {
		long submitted = lastBatches.stream().mapToLong(Batch::count).sum();
		double head = 0;
		double rows = 0;
		int splits = 0;
		for (var event : EVENTS.values()) {
			if (!event.ready || event.failed) {
				continue;
			}
			double now = event.time(0);
			head = Math.max(head, HordeTide.head(now, event.speed));
			rows += Math.max(0, HordeTide.head(now, event.speed) / event.rules.rowSpacing());
			splits += event.split.count();
		}
		return "blocks=" + lastBatches.size() + ", submitted=" + submitted + ", tideHead="
				+ String.format("%.1f", head) + ", rows=" + Math.round(rows) + ", splits=" + splits;
	}

	public static void clear() {
		EVENTS.values().forEach(HordeClient::closeEvent);
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
		private final Map<String, RegionBox> lingeringTargets = new TreeMap<>();
		private final Map<String, Double> lingeringTargetUntil = new TreeMap<>();
		private static final double REGION_LINGER_TICKS = 30;
		public double regionsChanged;
		public float entityHeight;
		public double length;
		public double speed;
		public float lightProbeHeight;
		/// 按进度求位置的纯函数；路线就绪后创建，客户端不再保存逐单位状态。
		public HordeTide tide;
		/// 分割割圆表：CPU 平滑后上传，GPU 只做连续让步。
		public final HordeSplitState split = new HordeSplitState();
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
			tide = new HordeTide(routeData, heightData, nodes.length, heightColumns, length, groundWidth, origin);
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

		private void updateGaps(List<LivingEntity> targets, List<Mob> members, Player player) {
			double now = time(0);
			var updated = new TreeMap<String, RegionBox>();
			updated.put("local", RegionBox.forEntity(player.getBoundingBox()));
			for (var entity : targets) {
				// 只对浪潮可视范围内的目标开缺口：远处的生物（含其他无关事件/动物）不应在空地上制造分割。
				if (!isRelevantTarget(entity, player)) {
					continue;
				}
				var key = "target:" + entity.getUUID();
				var box = RegionBox.forEntity(entity.getBoundingBox());
				updated.put(key, box);
				lingeringTargets.put(key, box);
				lingeringTargetUntil.put(key, now + REGION_LINGER_TICKS);
			}
			// target:与member:共享滞留表；生物消失或成员淡出后都保留30tick，再交GPU收缩退场。
			var iterator = lingeringTargetUntil.entrySet().iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				var lingering = lingeringTargets.get(entry.getKey());
				if (lingering == null || now >= entry.getValue()) {
					iterator.remove();
					lingeringTargets.remove(entry.getKey());
					continue;
				}
				updated.putIfAbsent(entry.getKey(), lingering);
			}

			// 每个参战生物保留自己的圆核心，不把多个核心合并成矩形禁区。
			var seeds = members.isEmpty() ? Map.<String, RegionBox>of() : Map.copyOf(updated);
			for (var member : members) {
				var source = HordeParticipant.get(member);
				var key = "target:" + source.targetId();
				var target = source.targetId().equals(player.getUUID().toString()) ? seeds.get("local") : seeds.get(key);
				if (target == null || source.fading() || !source.eventId().equals(id.toString())) {
					continue;
				}
				var bounds = member.getBoundingBox();
				if (bounds.minY >= target.y() + target.sizeY() || bounds.maxY <= target.y()) {
					continue;
				}
				// 成员核心按生物自身尺寸取圆，不加三格外扩：真实成员只是浪潮里的实体，不是需要绕行的目标。
				var memberKey = "member:" + member.getUUID();
				var box = RegionBox.forMember(bounds);
				updated.put(memberKey, box);
				lingeringTargets.put(memberKey, box);
				lingeringTargetUntil.put(memberKey, now + REGION_LINGER_TICKS);
			}

			if (!regions.equals(updated) || !previousRegions.equals(regions)) {
				HordeDebugState.gapChanged();
				previousRegions.clear();
				previousRegions.putAll(regions);
				regions.clear();
				regions.putAll(updated);
				regionsChanged = now;
				regionVersion++;
			}
		}

		private boolean isRelevantTarget(LivingEntity entity, Player player) {
			double range = HordeVisualConfig.rangeBlocks();
			if (player.distanceToSqr(entity) > range * range) {
				return false;
			}
			if (routeBounds != null && !routeBounds.inflate(48).contains(entity.position())) {
				return false;
			}
			return entity.isAlive() && !entity.isSpectator() && !HordeParticipant.isEvent(entity);
		}


		private static boolean isGapTarget(LivingEntity entity) {
            return entity.isAlive() && !entity.isSpectator() && !HordeParticipant.isEvent(entity);
        }
    }

	/// 圆核心的正方形外包围与真实高度；GPU仅将外包围用于候选索引，窄相位使用圆。
	public record RegionBox(double x, double y, double z, double sizeX, double sizeY, double sizeZ) {
		public static RegionBox forEntity(AABB bounds) {
			double bodyRadius = Math.hypot(bounds.getXsize(), bounds.getZsize()) * 0.5;
			double radius = Math.max(bodyRadius * VISUAL_GAP_SCALE, bodyRadius + MIN_VISUAL_CLEARANCE);
			var center = bounds.getCenter();
			return new RegionBox(center.x - radius, bounds.minY, center.z - radius,
					radius * 2, bounds.getYsize(), radius * 2);
		}

		/// 参战成员只按自身水平外接圆取核心，不加三格净空：成员是浪潮里的实体，不该撑出硬禁区。
		public static RegionBox forMember(AABB bounds) {
			double radius = Math.max(0.5, Math.hypot(bounds.getXsize(), bounds.getZsize()) * 0.5);
			var center = bounds.getCenter();
			return new RegionBox(center.x - radius, bounds.minY, center.z - radius,
					radius * 2, bounds.getYsize(), radius * 2);
		}


		public double radius() {
			return Math.max(sizeX, sizeZ) * 0.5;
		}

		public boolean contains(Vec3 point) {
			double dx = point.x - x - sizeX * 0.5;
			double dz = point.z - z - sizeZ * 0.5;
			return point.y >= y && point.y < y + sizeY && dx * dx + dz * dz < radius() * radius();
		}

		/// 引导外轮廓沿潮方向延长，但不扩大圆核心禁区。
		public double contourDistance(Vec3 point, Vec3 forward, double speed) {
			if (point.y < y || point.y >= y + sizeY) {
				return Double.POSITIVE_INFINITY;
			}
			var direction = forward.multiply(1, 0, 1).normalize();
			double dx = point.x - x - sizeX * 0.5;
			double dz = point.z - z - sizeZ * 0.5;
			double along = dx * direction.x + dz * direction.z;
			double across = -dx * direction.z + dz * direction.x;
			double radius = radius() + 0.45;
			double band = 3.2 + speed * 8;
			double nearest = Math.clamp(along, -band - radius * 0.6, band * 0.6 + radius * 0.5);
			return Math.hypot(along - nearest, across) - radius;
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
