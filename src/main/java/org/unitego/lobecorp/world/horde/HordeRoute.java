package org.unitego.lobecorp.world.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.config.server.HordeEventConfig;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import net.minecraft.nbt.DoubleTag;

/// 固定弧长坐标的分段线路；地形修订只平滑改变坐标，不重编号虚拟影像。
public class HordeRoute {
	private static final int HEIGHT_COLUMNS = 5;
	private static final int MAX_NODES = 8192;
	private final List<Node> nodes;
	private final double length;
	private int cursor;

	private HordeRoute(List<Node> nodes) {
		this.nodes = new ArrayList<>(nodes);
		this.length = nodes.getLast().distance;
	}

	public static HordeRoute create(List<Vec3> anchors) {
		var nodes = new ArrayList<Node>();
		double distance = 0;
		nodes.add(new Node(anchors.getFirst(), 0));
		double grid = HordeEventConfig.GRID_SIZE.get();
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

	public double length() {
		return length;
	}

	public int size() {
		return nodes.size();
	}

	/// 闭环接缝两侧可能同时靠近玩家，订阅范围必须覆盖两侧的节点编号。
	public int[] nearbyRange(Vec3 position, double radius, long tick, int transition) {
		int first = nodes.size() - 1;
		int last = 0;
		for (int index = 0; index < nodes.size(); index++) {
			if (nodes.get(index).at(tick, transition).distanceToSqr(position) <= radius * radius) {
				first = Math.clamp(index - 1, 0, first);
				last = Math.clamp(index + 1, last, nodes.size() - 1);
			}
		}
		return new int[]{Math.min(first, last), last};
	}

	public Vec3 point(double distance, double lateral, double tick, int transition) {
		int index = segment(distance);
		Node first = nodes.get(index);
		Node second = nodes.get(index + 1);
		double ratio = Math.clamp((distance - first.distance) / Math.max(0.001, second.distance - first.distance), 0, 1);
		Vec3 origin = first.at(tick, transition).lerp(second.at(tick, transition), ratio);
		Vec3 forward = second.at(tick, transition).subtract(first.at(tick, transition));
		double horizontal = Math.max(0.001, Math.hypot(forward.x, forward.z));
		Vec3 offset = new Vec3(-forward.z / horizontal * lateral, 0, forward.x / horizontal * lateral);
		double y = first.height(lateral, tick, transition) * (1 - ratio) + second.height(lateral, tick, transition) * ratio;
		return new Vec3(origin.x + offset.x, y, origin.z + offset.z);
	}

	public Vec3 direction(double distance, double tick, int transition) {
		int index = segment(distance);
		Vec3 delta = nodes.get(index + 1).at(tick, transition).subtract(nodes.get(index).at(tick, transition));
		return new Vec3(delta.x, 0, delta.z).normalize();
	}

	public double nearest(Vec3 position, double from, double to, long tick, int transition) {
		double best = Double.MAX_VALUE;
		double station = Math.clamp(from, 0, length);
		int begin = segment(from);
		int end = segment(to);
		for (int i = begin; i <= end; i++) {
			Node first = nodes.get(i);
			Node second = nodes.get(i + 1);
			Vec3 a = first.at(tick, transition);
			Vec3 b = second.at(tick, transition);
			Vec3 delta = b.subtract(a);
			double t = delta.lengthSqr() == 0 ? 0 : Math.clamp(position.subtract(a).dot(delta) / delta.lengthSqr(), 0, 1);
			double s = first.distance + (second.distance - first.distance) * t;
			if (s < from || s > to) {
				continue;
			}
			double d = HordeSamplingUtil.segmentDistanceSqr(position, a, b);
			if (d < best) {
				best = d;
				station = s;
			}
		}
		return station;
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
		if (ground(level, nodes.getFirst().position) == null) {
			return false;
		}
		refineNode(level, 0, rules, 0);
		return true;
	}

	public boolean refine(ServerLevel level, HordeRules rules, long tick, int work) {
		boolean changed = false;
		for (int n = 0; n < work; n++) {
			int index = Math.floorMod(cursor++, nodes.size());
			Node node = nodes.get(index);
			if (tick - node.checkedAt < HordeEventConfig.ROUTE_RECHECK.get()) {
				continue;
			}
			node.checkedAt = tick;
			changed |= refineNode(level, index, rules, tick);
		}
		return changed;
	}

	private boolean refineNode(ServerLevel level, int index, HordeRules rules, long tick) {
		Node node = nodes.get(index);
		BlockPos position = BlockPos.containing(node.position);
		if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
			return false;
		}
		Vec3 selected = node.position;
		double best = terrainCost(level, node.position, node.position);
		double grid = HordeEventConfig.GRID_SIZE.get();
		if (index > 0 && index < nodes.size() - 1 && best > 0) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					Vec3 candidate = node.position.add(x * grid, 0, z * grid);
					double cost = terrainCost(level, candidate, node.position) + Math.hypot(x, z);
					if (cost < best) {
						best = cost;
						selected = candidate;
					}
				}
			}
		}
		var ground = ground(level, selected);
		if (ground == null) {
			return false;
		}
		Vec3 forward = index + 1 < nodes.size() ? nodes.get(index + 1).position.subtract(selected)
				: selected.subtract(nodes.get(index - 1).position);
		Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize();
		double[] heights = new double[HEIGHT_COLUMNS];
		for (int column = 0; column < HEIGHT_COLUMNS; column++) {
			double lateral = (column / (double) (HEIGHT_COLUMNS - 1) - 0.5) * rules.width();
			Vec3 sampled = ground(level, ground.add(side.scale(lateral)));
			heights[column] = sampled == null ? ground.y : sampled.y;
		}
		if (ground.distanceToSqr(node.position) < 0.001 && Arrays.equals(heights, node.heights)) {
			return false;
		}
		node.previous = node.at(tick, rules.transitionTicks());
		node.previousHeights = node.heights.clone();
		node.position = ground;
		node.heights = heights;
		node.width = rules.width();
		node.changedAt = tick;
		return true;
	}

	private static double terrainCost(ServerLevel level, Vec3 point, Vec3 reference) {
		Vec3 floor = ground(level, point);
		if (floor == null) {
			return 10000;
		}
		BlockPos below = BlockPos.containing(floor).below();
		double score = Math.abs(floor.y - reference.y) * 4;
		if (!level.getFluidState(below).isEmpty()) {
			score += 1000;
		}
		if (Math.abs(floor.y - reference.y) > HordeEventConfig.GRID_SIZE.get()) {
			score += 100;
		}
		return score;
	}

	public static Vec3 ground(ServerLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return new Vec3(point.x, y, point.z);
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

	public CompoundTag slice(int first, int count) {
		var tag = new CompoundTag();
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
		return new HordeRoute(nodes);
	}

	public static class Node {
		private Vec3 position;
		private Vec3 previous;
		private final double distance;
		private double width = 64;
		private double[] heights;
		private double[] previousHeights;
		private long changedAt;
		private long checkedAt = Long.MIN_VALUE / 2;

		public Node(Vec3 position, double distance) {
			this.position = position;
			this.previous = position;
			this.distance = distance;
			this.heights = new double[]{position.y, position.y, position.y, position.y, position.y};
			this.previousHeights = heights.clone();
		}

		public double distance() {
			return distance;
		}

		public Vec3 at(double tick, int transition) {
			return previous.lerp(position, HordeSamplingUtil.smooth((tick - changedAt) / transition));
		}

		public double height(double lateral, double tick, int transition) {
			double column = Math.clamp(lateral / width + 0.5, 0, 1) * (HEIGHT_COLUMNS - 1);
			int low = Math.min(HEIGHT_COLUMNS - 2, (int) column);
			double f = column - low;
			double old = previousHeights[low] * (1 - f) + previousHeights[low + 1] * f;
			double next = heights[low] * (1 - f) + heights[low + 1] * f;
			return old + (next - old) * HordeSamplingUtil.smooth((tick - changedAt) / transition);
		}

		public CompoundTag save() {
			var tag = new CompoundTag();
			tag.store("position", Vec3.CODEC, position);
			tag.store("previous", Vec3.CODEC, previous);
			tag.putDouble("distance", distance);
			tag.putDouble("width", width);
			tag.putLong("changed", changedAt);
			var samples = new ListTag();
			var old = new ListTag();
			for (int i = 0; i < HEIGHT_COLUMNS; i++) {
				samples.add(DoubleTag.valueOf(heights[i]));
				old.add(DoubleTag.valueOf(previousHeights[i]));
			}
			tag.put("heights", samples);
			tag.put("old_heights", old);
			return tag;
		}

		public static Node load(CompoundTag tag) {
			Vec3 p = tag.read("position", Vec3.CODEC).orElse(Vec3.ZERO);
			var node = new Node(p, tag.getDoubleOr("distance", 0));
			node.previous = tag.read("previous", Vec3.CODEC).orElse(p);
			node.changedAt = tag.getLongOr("changed", 0);
			node.width = tag.getDoubleOr("width", 64);
			var heights = tag.getListOrEmpty("heights");
			var old = tag.getListOrEmpty("old_heights");
			for (int i = 0; i < Math.min(HEIGHT_COLUMNS, heights.size()); i++) {
				node.heights[i] = heights.get(i).asDouble().orElse(p.y);
				node.previousHeights[i] = i < old.size() ? old.get(i).asDouble().orElse(p.y) : node.heights[i];
			}
			return node;
		}
	}
}
