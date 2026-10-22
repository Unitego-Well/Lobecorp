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

import net.minecraft.nbt.DoubleTag;

/// 事件启动时一次准备的固定线路；运行期间不修改节点或地面高度。
public class HordeRoute {
	private static final int HEIGHT_COLUMNS = 5;
	private static final int MAX_NODES = 8192;
	private final List<Node> nodes;
	private final double length;

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
		double best = Double.MAX_VALUE;
		double station = Math.clamp(from, 0, length);
		int begin = segment(from);
		int end = segment(to);
		for (int i = begin; i <= end; i++) {
			Node first = nodes.get(i);
			Node second = nodes.get(i + 1);
			Vec3 a = first.at();
			Vec3 b = second.at();
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
		double sampleWidth = rules.width() + rules.regionMargin() * 2;
		for (int index = 0; index < nodes.size(); index++) {
			Node node = nodes.get(index);
			Vec3 ground = ground(level, node.position);
			if (ground == null) {
				return false;
			}

			Vec3 forward = nodeDirection(index);
			Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize();
			var heights = new double[HEIGHT_COLUMNS];
			for (int column = 0; column < HEIGHT_COLUMNS; column++) {
				double lateral = (column / (double) (HEIGHT_COLUMNS - 1) - 0.5) * sampleWidth;
				Vec3 sampled = ground(level, ground.add(side.scale(lateral)));
				if (sampled == null) {
					return false;
				}
				heights[column] = sampled.y;
			}

			node.position = ground;
			node.width = sampleWidth;
			node.heights = heights;
		}
		return true;
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
		private final double distance;
		private double width = 64;
		private double[] heights;

		public Node(Vec3 position, double distance) {
			this.position = position;
			this.distance = distance;
			this.heights = new double[]{position.y, position.y, position.y, position.y, position.y};
		}

		public double distance() {
			return distance;
		}

		public Vec3 at() {
			return position;
		}

		public double height(double lateral) {
			double column = Math.clamp(lateral / width + 0.5, 0, 1) * (HEIGHT_COLUMNS - 1);
			int low = Math.min(HEIGHT_COLUMNS - 2, (int) column);
			double f = column - low;
			return heights[low] * (1 - f) + heights[low + 1] * f;
		}

		public CompoundTag save() {
			var tag = new CompoundTag();
			tag.store("position", Vec3.CODEC, position);
			tag.putDouble("distance", distance);
			tag.putDouble("width", width);
			var samples = new ListTag();
			for (int i = 0; i < HEIGHT_COLUMNS; i++) {
				samples.add(DoubleTag.valueOf(heights[i]));
			}
			tag.put("heights", samples);
			return tag;
		}

		public static Node load(CompoundTag tag) {
			Vec3 p = tag.read("position", Vec3.CODEC).orElse(Vec3.ZERO);
			var node = new Node(p, tag.getDoubleOr("distance", 0));
			node.width = tag.getDoubleOr("width", 64);
			var heights = tag.getListOrEmpty("heights");
			for (int i = 0; i < Math.min(HEIGHT_COLUMNS, heights.size()); i++) {
				node.heights[i] = heights.get(i).asDouble().orElse(p.y);
			}
			return node;
		}
	}
}
