package org.unitego.lobecorp.util.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.*;

/// 事件共享的已加载地形四邻域 A*；允许背离目标，禁止跨越高差及强加载。
public class HordeRouteDetourUtil {
	private static final int MAX_EXPANSIONS = 16384;
	private static final int SEARCH_MARGIN = 96;


	public record Result(List<Vec3> points, String diagnostic) {}
	private record Cell(int x, int z) {}
	private record Entry(Cell cell, double cost, double score) {}

	public static Result find(ServerLevel level, Vec3 start, Vec3 end) {
		Cell source = new Cell((int) Math.floor(start.x), (int) Math.floor(start.z));
		Cell target = new Cell((int) Math.floor(end.x), (int) Math.floor(end.z));
		var heights = new HashMap<Cell, Integer>();
		var costs = new HashMap<Cell, Double>();
		var parents = new HashMap<Cell, Cell>();
		var open = new PriorityQueue<Entry>(Comparator.comparingDouble(Entry::score));
		if (height(level, source, heights) == Integer.MIN_VALUE || height(level, target, heights) == Integer.MIN_VALUE) {
			return new Result(List.of(), "endpoint_unloaded_or_blocked");
		}
		costs.put(source, 0.0);
		open.add(new Entry(source, 0, heuristic(source, target)));
		int expanded = 0;
		while (!open.isEmpty() && expanded++ < MAX_EXPANSIONS) {
			var entry = open.remove();
			var current = entry.cell;
			if (entry.cost != costs.getOrDefault(current, Double.POSITIVE_INFINITY)) {
				continue;
			}
			if (current.equals(target)) {
				var points = new ArrayList<Vec3>();
				for (Cell cell = target; cell != null; cell = parents.get(cell)) {
					points.add(new Vec3(cell.x + 0.5, heights.get(cell), cell.z + 0.5));
				}
				Collections.reverse(points);
				var first = new Vec3(start.x, heights.get(source), start.z);
				var last = new Vec3(end.x, heights.get(target), end.z);
				// 保留锚点XZ；接入块中心的短段也必须满足碰撞约束。
				if (!clearSegment(level, first, points.getFirst()) || !clearSegment(level, points.getLast(), last)) {
					return new Result(List.of(), "endpoint_connection_blocked");
				}
				points.addFirst(first);
				points.addLast(last);
				return new Result(List.copyOf(points), "complete");
			}
			int currentY = heights.get(current);
			for (int direction = 0; direction < 4; direction++) {
				var next = new Cell(current.x + (direction == 0 ? 1 : direction == 1 ? -1 : 0),
						current.z + (direction == 2 ? 1 : direction == 3 ? -1 : 0));
				if (next.x < Math.min(source.x, target.x) - SEARCH_MARGIN || next.x > Math.max(source.x, target.x) + SEARCH_MARGIN
						|| next.z < Math.min(source.z, target.z) - SEARCH_MARGIN || next.z > Math.max(source.z, target.z) + SEARCH_MARGIN) {
					continue;
				}
				int y = height(level, next, heights);
				if (y == Integer.MIN_VALUE || Math.abs(y - currentY) > 1) {
					continue;
				}
				double cost = entry.cost + 1 + Math.abs(y - currentY) * 0.25;
				if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
					continue;
				}
				costs.put(next, cost);
				parents.put(next, current);
				open.add(new Entry(next, cost, cost + heuristic(next, target)));
			}
		}
		return new Result(List.of(), open.isEmpty() ? "no_complete_loaded_path" : "expansion_budget_exhausted");
	}

	public static boolean affectsSearch(BlockPos pos, Vec3 start, Vec3 end) {
		return pos.getX() >= Math.floor(Math.min(start.x, end.x)) - SEARCH_MARGIN
				&& pos.getX() <= Math.floor(Math.max(start.x, end.x)) + SEARCH_MARGIN
				&& pos.getZ() >= Math.floor(Math.min(start.z, end.z)) - SEARCH_MARGIN
				&& pos.getZ() <= Math.floor(Math.max(start.z, end.z)) + SEARCH_MARGIN;
	}

	/// 连续碰撞形状扫掠，中心线代理宽0.6、高1.8；不跨未加载区块。
	public static boolean clearSegment(ServerLevel level, Vec3 from, Vec3 to) {
		var min = BlockPos.containing(Math.min(from.x, to.x) - 0.3, Math.min(from.y, to.y), Math.min(from.z, to.z) - 0.3);
		var max = BlockPos.containing(Math.max(from.x, to.x) + 0.3, Math.max(from.y, to.y) + 1.8, Math.max(from.z, to.z) + 0.3);
		for (var pos : BlockPos.betweenClosed(min, max)) {
			if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
				return false;
			}
			for (var box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
				var world = box.move(pos.getX(), pos.getY(), pos.getZ());
				var expanded = new AABB(world.minX - 0.3, world.minY - 1.8 + 0.001, world.minZ - 0.3,
						world.maxX + 0.3, world.maxY - 0.001, world.maxZ + 0.3);
				if (expanded.contains(from) || expanded.contains(to) || expanded.clip(from, to).isPresent()) {
					return false;
				}
			}
		}
		return true;
	}

	private static double heuristic(Cell from, Cell to) {
		return Math.abs(from.x - to.x) + Math.abs(from.z - to.z);
	}

	private static int height(ServerLevel level, Cell cell, Map<Cell, Integer> cache) {
		return cache.computeIfAbsent(cell, key -> {
			if (level.getChunkSource().getChunkNow(key.x >> 4, key.z >> 4) == null) {
				return Integer.MIN_VALUE;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, key.x, key.z);
			var floor = new BlockPos(key.x, y - 1, key.z);
			if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
					|| !level.getFluidState(floor).isEmpty()
					|| !level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()
					|| !level.getBlockState(floor.above(2)).getCollisionShape(level, floor.above(2)).isEmpty()) {
				return Integer.MIN_VALUE;
			}
			return y;
		});
	}
}
