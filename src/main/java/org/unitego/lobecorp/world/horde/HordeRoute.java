package org.unitego.lobecorp.world.horde;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;

import java.util.*;
import java.util.function.UnaryOperator;

/// 逻辑站程及XZ中心线固定；未加载样本暂存输入高度，区块就绪或方块更新后按预算校正。
public class HordeRoute {
	private static final int HEIGHT_GROUPS_PER_TICK = 2;
	private static final double HEIGHT_SAMPLE_SPACING = 1;
	private static final double SAMPLE_SPACING = 1;
	private static final int MAX_NODES = 8192;
	// 固定XZ线路的查询包围组；高度更新不影响二维距离下界。
	private static final int SEARCH_NODE_STRIDE = 32;
	private List<SearchBounds> searchBounds;
	private long geometryRevision;
	private final List<Node> nodes;
	private final double length;
	private final Long2ObjectOpenHashMap<IntArrayList> samplesByGroup = new Long2ObjectOpenHashMap<>();
	private final ArrayDeque<Long> dirtyGroups = new ArrayDeque<>();
	private final Set<Long> queuedGroups = new HashSet<>();
	private int heightColumns;

	private HordeRoute(List<Node> nodes) {
		this.nodes = new ArrayList<>(nodes);
		this.length = nodes.getLast().distance;
		this.searchBounds = prepareSearchBounds();
	}

	public static HordeRoute create(List<Vec3> anchors) {
		var nodes = new ArrayList<Node>();
		double distance = 0;
		nodes.add(new Node(anchors.getFirst(), 0));
		double grid = SAMPLE_SPACING;
		for (int i = 1; i < anchors.size(); i++) {
			Vec3 start = anchors.get(i - 1);
			Vec3 end = anchors.get(i);
			double span = start.distanceTo(end);
			int count = Math.max(1, (int) Math.ceil(span / grid));
			if (nodes.size() + count > MAX_NODES) {
				throw new IllegalArgumentException("路线过长，超过采样节点预算");
			}
			for (int j = 1; j <= count; j++) {
				nodes.add(new Node(start.lerp(end, j / (double) count), distance + span * j / count));
			}
			distance += span;
		}
		if (distance < 1) {
			throw new IllegalArgumentException("起点与终点之间需要有效线路");
		}
		return new HordeRoute(nodes);
	}

	private static long heightGroup(double x, double z) {
		return BlockPos.asLong((int) Math.floor(x) >> 4, 0, (int) Math.floor(z) >> 4);
	}

	public static Vec3 ground(ServerLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return new Vec3(point.x, y, point.z);
	}

	public static HordeRoute load(CompoundTag tag) {
		var nodes = new ArrayList<Node>();
		for (var element : tag.getListOrEmpty("nodes")) {
			if (element instanceof CompoundTag node && nodes.size() < MAX_NODES) {
				nodes.add(Node.load(node));
			}
		}
		if (nodes.size() < 2) {
			throw new IllegalArgumentException("路线缺少节点");
		}
		var route = new HordeRoute(nodes);
		// 旧存档的稀疏横向样本先展开，再由已加载区块逐组校正为逐格高度。
		int columns = (int) Math.ceil(nodes.getFirst().width / HEIGHT_SAMPLE_SPACING) + 1;
		for (var node : nodes) {
			if (node.heights.length == columns) {
				continue;
			}
			var heights = new double[columns];
			for (int column = 0; column < columns; column++) {
				heights[column] = node.height((column / (double) (columns - 1) - 0.5) * node.width);
			}
			node.heights = heights;
			node.revision++;
		}
		route.heightColumns = nodes.getFirst().heightCount();
		for (int index = 0; index < nodes.size(); index++) {
			var node = nodes.get(index);
			if (node.heightCount() != route.heightColumns) {
				throw new IllegalArgumentException("线路高度列数不一致");
			}
			var forward = route.nodeDirection(index);
			for (int column = 0; column < node.heights.length; column++) {
				double lateral = (column / (double) (node.heights.length - 1) - 0.5) * node.width;
				long key = heightGroup(node.position.x - forward.z * lateral, node.position.z + forward.x * lateral);
				route.samplesByGroup.computeIfAbsent(key, _ -> new IntArrayList()).add(index * route.heightColumns + column);
			}
		}
		// 存档中的高度可能来自尚未加载区块的临时坐标，加载后重新核对。
		route.queuedGroups.addAll(route.samplesByGroup.keySet());
		route.dirtyGroups.addAll(route.queuedGroups);
		return route;
	}

	public double length() {
		return length;
	}

	public int size() {
		return nodes.size();
	}

	public Vec3 point(double distance, double lateral) {
		int index = segment(distance);
		Node first = nodes.get(index);
		Node second = nodes.get(index + 1);
		double ratio = Math.clamp((distance - first.distance) / Math.max(0.001, second.distance - first.distance), 0, 1);
		Vec3 origin = first.at().lerp(second.at(), ratio);
		Vec3 forward = nodeDirection(index).lerp(nodeDirection(index + 1), ratio).normalize();
		double horizontal = Math.max(0.001, Math.hypot(forward.x, forward.z));
		Vec3 offset = new Vec3(-forward.z / horizontal * lateral, 0, forward.x / horizontal * lateral);
		double y = first.height(lateral) * (1 - ratio) + second.height(lateral) * ratio;
		return new Vec3(origin.x + offset.x, y, origin.z + offset.z);
	}

	public Vec3 direction(double distance) {
		int index = segment(distance);
		double ratio = Math.clamp((distance - nodes.get(index).distance)
				/ Math.max(0.001, nodes.get(index + 1).distance - nodes.get(index).distance), 0, 1);
		return nodeDirection(index).lerp(nodeDirection(index + 1), ratio).normalize();
	}

	private Vec3 nodeDirection(int index) {
		Vec3 delta = nodes.get(Math.min(nodes.size() - 1, index + 1)).position
				.subtract(nodes.get(Math.max(0, index - 1)).position);
		return new Vec3(delta.x, 0, delta.z).normalize();
	}

	public double nearest(Vec3 position, double from, double to) {
		from = Math.clamp(from, 0, length);
		to = Math.clamp(to, from, length);
		double best = Double.MAX_VALUE;
		double station = Math.clamp(from, 0, length);
		int begin = segment(from);
		int end = segment(to);
		SearchBounds preferred = null;
		double preferredDistance = Double.MAX_VALUE;
		for (var bounds : searchBounds) {
			if (bounds.last() <= begin || bounds.first() > end) {
				continue;
			}
			double distance = bounds.distanceSqr(position);
			if (distance < preferredDistance) {
				preferred = bounds;
				preferredDistance = distance;
			}
		}
		for (int pass = -1; pass < searchBounds.size(); pass++) {
			var bounds = pass < 0 ? preferred : searchBounds.get(pass);
			if (bounds == null || pass >= 0 && bounds == preferred || bounds.distanceSqr(position) > best) {
				continue;
			}
			for (int i = Math.max(begin, bounds.first()); i <= Math.min(end, bounds.last() - 1); i++) {
				Node first = nodes.get(i);
				Node second = nodes.get(i + 1);
				Vec3 a = first.at();
				Vec3 b = second.at();
				Vec3 delta = b.subtract(a);
				double t = delta.lengthSqr() == 0 ? 0 : Math.clamp(position.subtract(a).dot(delta) / delta.lengthSqr(), 0, 1);
				double s = first.distance + (second.distance - first.distance) * t;
				s = Math.clamp(s, Math.max(first.distance, from), Math.min(second.distance, to));
				t = (s - first.distance) / Math.max(0.001, second.distance - first.distance);
				double distance = position.distanceToSqr(a.lerp(b, t));
				if (distance < best) {
					best = distance;
					station = s;
				}
			}
		}
		return station;
	}

	private List<SearchBounds> prepareSearchBounds() {
		var result = new ArrayList<SearchBounds>();
		for (int first = 0; first + 1 < nodes.size(); first += SEARCH_NODE_STRIDE) {
			int last = Math.min(nodes.size() - 1, first + SEARCH_NODE_STRIDE);
			double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
			for (int index = first; index <= last; index++) {
				var point = nodes.get(index).position;
				minX = Math.min(minX, point.x);
				maxX = Math.max(maxX, point.x);
				minZ = Math.min(minZ, point.z);
				maxZ = Math.max(maxZ, point.z);
			}
			result.add(new SearchBounds(first, last, minX, maxX, minZ, maxZ));
		}
		return List.copyOf(result);
	}

	public int segment(double station) {
		int low = 0;
		int high = nodes.size() - 2;
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (nodes.get(mid).distance <= station) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		return low;
	}

	public boolean prepare(ServerLevel level, HordeRules rules) {
		return prepareDeferred(rules, point -> ground(level, point));
	}

	public boolean prepare(HordeRules rules, UnaryOperator<Vec3> sampler) {
		return prepare(rules, sampler, false);
	}

	public boolean prepareDeferred(HordeRules rules, UnaryOperator<Vec3> sampler) {
		return prepare(rules, sampler, true);
	}

	private boolean prepare(HordeRules rules, UnaryOperator<Vec3> sampler, boolean allowUnloaded) {
		samplesByGroup.clear();
		dirtyGroups.clear();
		queuedGroups.clear();
		double sampleWidth = Math.ceil(HordeSamplingUtil.groundSampleWidth(rules.width(), rules.regionMargin()));
		heightColumns = (int) Math.ceil(sampleWidth / HEIGHT_SAMPLE_SPACING) + 1;
		for (var node : nodes) {
			node.width = sampleWidth;
			node.heights = new double[heightColumns];
		}
		for (int index = 0; index < nodes.size(); index++) {
			Node node = nodes.get(index);
			Vec3 ground = sampler.apply(node.position);
			if (ground == null) {
				if (!allowUnloaded) {
					return false;
				}

				ground = node.position;
			}

			Vec3 forward = nodeDirection(index);
			Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize();
			var heights = new double[heightColumns];
			for (int column = 0; column < heightColumns; column++) {
				double lateral = (column / (double) (heightColumns - 1) - 0.5) * sampleWidth;
				Vec3 position = ground.add(side.scale(lateral));
				Vec3 sampled = sampler.apply(position);
				if (sampled == null && !allowUnloaded) {
					return false;
				}

				heights[column] = sampled == null ? position.y : sampled.y;
				long key = heightGroup(position.x, position.z);
				samplesByGroup.computeIfAbsent(key, _ -> new IntArrayList()).add(index * heightColumns + column);
				if (sampled == null && queuedGroups.add(key)) {
					dirtyGroups.addLast(key);
				}
			}

			node.position = ground;
			node.width = sampleWidth;
			node.heights = heights;
		}
		return true;
	}

	public void invalidate(BlockPos position) {
		long group = heightGroup(position.getX(), position.getZ());
		if (samplesByGroup.containsKey(group) && queuedGroups.add(group)) {
			dirtyGroups.addLast(group);
		}
	}

	public boolean refresh(ServerLevel level) {
		var changedNodes = new BitSet();
		int groups = Math.min(HEIGHT_GROUPS_PER_TICK, dirtyGroups.size());
		for (int work = 0; work < groups; work++) {
			long group = dirtyGroups.removeFirst();
			queuedGroups.remove(group);
			boolean pending = false;
			for (int sample : samplesByGroup.get(group)) {
				int index = sample / heightColumns;
				int column = sample % heightColumns;
				var node = nodes.get(index);
				var forward = nodeDirection(index);
				double lateral = (column / (double) (heightColumns - 1) - 0.5) * node.width;
				var floor = ground(level, node.position.add(-forward.z * lateral, 0, forward.x * lateral));
				if (floor == null) {
					pending = true;
					continue;
				}
				if (node.heights[column] != floor.y) {
					node.heights[column] = floor.y;
					changedNodes.set(index);
				}
			}
			if (pending && queuedGroups.add(group)) {
				dirtyGroups.addLast(group);
			}
		}
		for (int index = changedNodes.nextSetBit(0); index >= 0; index = changedNodes.nextSetBit(index + 1)) {
			var node = nodes.get(index);
			node.position = new Vec3(node.position.x, node.height(0), node.position.z);
			node.revision++;
		}
		return !changedNodes.isEmpty();
	}

	/// 诊断供主线命令/报告读取；失败不发布部分路线。
	public String detourDiagnostic() {
		return "fixed_a";
	}

	public long geometryRevision() {
		return geometryRevision;
	}

	public Node node(int index) {
		return nodes.get(index);
	}

	public CompoundTag save() {
		var tag = new CompoundTag();
		var list = new ListTag();
		for (Node node : nodes) {
			list.add(node.save());
		}
		tag.put("nodes", list);
		return tag;
	}

	/// 首包发送至多九个稀疏锚点与完整范围，细节仍走原有分页。
	public CompoundTag overview(int intervals) {
		var tag = new CompoundTag();
		var anchors = new ListTag();
		int stride = Math.max(1, Math.ceilDiv(nodes.size() - 1, intervals));
		for (int index = 0; ; index = Math.min(nodes.size() - 1, index + stride)) {
			var node = nodes.get(index);
			var coarse = Node.blend(node, node, 1, 3, node.distance()).save();
			coarse.putInt("index", index);
			anchors.add(coarse);
			if (index == nodes.size() - 1) {
				break;
			}
		}

		tag.put("anchors", anchors);
		tag.putInt("height_columns", heightColumns);
		double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
		double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
		for (var node : nodes) {
			minX = Math.min(minX, node.position.x);
			minY = Math.min(minY, node.position.y);
			minZ = Math.min(minZ, node.position.z);
			maxX = Math.max(maxX, node.position.x);
			maxY = Math.max(maxY, node.position.y);
			maxZ = Math.max(maxZ, node.position.z);
		}
		tag.store("min", Vec3.CODEC, new Vec3(minX, minY, minZ));
		tag.store("max", Vec3.CODEC, new Vec3(maxX, maxY, maxZ));
		return tag;
	}

	public CompoundTag slice(int first, int count) {
		var tag = new CompoundTag();
		tag.putLong("geometry_revision", geometryRevision);
		tag.putInt("first", first);
		tag.putInt("total", nodes.size());
		tag.putDouble("length", length);
		var list = new ListTag();
		for (int i = first; i < Math.min(nodes.size(), first + count); i++) {
			list.add(nodes.get(i).save());
		}
		tag.put("nodes", list);
		return tag;
	}

	private record SearchBounds(int first, int last, double minX, double maxX, double minZ, double maxZ) {
		private double distanceSqr(Vec3 position) {
			double dx = Math.max(0, Math.max(minX - position.x, position.x - maxX));
			double dz = Math.max(0, Math.max(minZ - position.z, position.z - maxZ));
			return dx * dx + dz * dz;
		}
	}

	public static class Node {
		private final double distance;
		private Vec3 position;
		private double width = 64;
		private double[] heights;
		private long revision;

		public Node(Vec3 position, double distance) {
			this.position = position;
			this.distance = distance;
			this.heights = new double[]{position.y, position.y, position.y, position.y, position.y};
		}

		/// 客户端粗略高度及五刻细化共用插值；距离单独传入以保持查找表单调。
		public static Node blend(Node from, Node to, double ratio, int columns, double distance) {
			var node = new Node(from.position.lerp(to.position, ratio), distance);
			node.width = to.width;
			node.revision = to.revision;
			node.heights = new double[columns];
			for (int column = 0; column < columns; column++) {
				double lateral = (column / (double) (columns - 1) - 0.5) * node.width;
				node.heights[column] = from.height(lateral) * (1 - ratio) + to.height(lateral) * ratio;
			}
			return node;
		}

		public static Node load(CompoundTag tag) {
			Vec3 p = tag.read("position", Vec3.CODEC).orElse(Vec3.ZERO);
			var node = new Node(p, tag.getDoubleOr("distance", 0));
			node.width = tag.getDoubleOr("width", 64);
			if (!Double.isFinite(node.width) || node.width <= 0) {
				throw new IllegalArgumentException("线路高度采样宽度无效");
			}
			var heights = tag.getListOrEmpty("heights");
			if (heights.size() < 2 || heights.size() > 1025) {
				throw new IllegalArgumentException("线路高度样本数量无效");
			}
			node.heights = new double[heights.size()];
			node.revision = tag.getLongOr("revision", 0);
			for (int i = 0; i < heights.size(); i++) {
				node.heights[i] = heights.get(i).asDouble().orElse(p.y);
				if (!Double.isFinite(node.heights[i])) {
					throw new IllegalArgumentException("线路高度样本必须为有限数");
				}
			}
			return node;
		}

		public double distance() {
			return distance;
		}

		public long revision() {
			return revision;
		}

		public double sampleWidth() {
			return width;
		}

		public int heightCount() {
			return heights.length;
		}

		public Vec3 at() {
			return position;
		}

		public double height(double lateral) {
			double column = Math.clamp(lateral / width + 0.5, 0, 1) * (heights.length - 1);
			int low = Math.min(heights.length - 2, (int) column);
			double f = column - low;
			return heights[low] * (1 - f) + heights[low + 1] * f;
		}

		public CompoundTag save() {
			var tag = new CompoundTag();
			tag.store("position", Vec3.CODEC, position);
			tag.putDouble("distance", distance);
			tag.putDouble("width", width);
			tag.putLong("revision", revision);
			var samples = new ListTag();
			for (int i = 0; i < heights.length; i++) {
				samples.add(DoubleTag.valueOf(heights[i]));
			}
			tag.put("heights", samples);
			return tag;
		}
	}
}
