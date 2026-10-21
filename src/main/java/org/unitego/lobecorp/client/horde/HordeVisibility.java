package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/// 按包含整个模型的空间格缓存遮挡；未知格显示，过期格轮转复查，不因预算耗尽突然显现。
public class HordeVisibility {
	private static final int CELL_SIZE = 4, CHECKS_PER_FRAME = 32, CACHE_TICKS = 5;
	private static final Map<Long, Cell> CACHE = new HashMap<>();
	private static final ArrayDeque<Cell> PENDING = new ArrayDeque<>();
	private static Vec3 camera = Vec3.ZERO;
	private static Vec3 anchor = Vec3.ZERO;
	private static long tick;
	private static long cleanedAt;
	private static int rejected;

	public static void begin(Vec3 position, long gameTime) {
		if (anchor.distanceToSqr(position) > 4) {
			clear();
			anchor = position;
		}
		camera = position;
		tick = gameTime;
		rejected = 0;
	}

	public static boolean visible(Vec3 point, Frustum frustum) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.getCameraEntity() == null) {
			return false;
		}
		int x = (int) Math.floor(point.x), y = (int) Math.floor(point.y), z = (int) Math.floor(point.z);
		if (minecraft.level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null
				|| !frustum.isVisible(new AABB(point.x - 1, point.y - 1, point.z - 1, point.x + 1, point.y + 4, point.z + 1))) {
			return false;
		}
		x = Math.floorDiv(x, CELL_SIZE) * CELL_SIZE;
		z = Math.floorDiv(z, CELL_SIZE) * CELL_SIZE;
		long key = BlockPos.asLong(x, y, z);
		Cell cell = CACHE.get(key);
		if (cell == null) {
			cell = new Cell(x, y, z);
			CACHE.put(key, cell);
		}
		cell.seen = tick;
		if (!cell.queued && tick - cell.checkedAt >= CACHE_TICKS) {
			cell.queued = true;
			PENDING.addLast(cell);
		}
		if (!cell.visible) {
			rejected++;
		}
		return cell.visible;
	}

	public static void end() {
		int remaining = CHECKS_PER_FRAME;
		while (remaining > 0 && !PENDING.isEmpty()) {
			Cell cell = PENDING.removeFirst();
			cell.queued = false;
			if (tick - cell.seen > CACHE_TICKS) {
				continue;
			}
			remaining--;
			cell.visible = check(cell);
			cell.checkedAt = tick;
		}
		if (tick - cleanedAt >= 20) {
			CACHE.values().removeIf(cell -> !cell.queued && tick - cell.seen > 40);
			cleanedAt = tick;
		}
	}

	private static boolean check(Cell cell) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.getCameraEntity() == null
				|| new AABB(cell.x - 1, cell.y - 1, cell.z - 1, cell.x + 5, cell.y + 4, cell.z + 5).contains(camera)) {
			return true;
		}
		for (int sample = 0; sample < 9; sample++) {
			Vec3 target = sample == 8 ? new Vec3(cell.x + 2, cell.y + 2, cell.z + 2)
					: new Vec3(cell.x + ((sample & 1) == 0 ? -1 : 5),
					cell.y + ((sample & 4) == 0 ? -1 : 4), cell.z + ((sample & 2) == 0 ? -1 : 5));
			var context = new ClipContext(camera, target, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, minecraft.getCameraEntity()) {
				@Override
				public @NonNull VoxelShape getBlockShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos) {
					return state.isSolidRender() ? super.getBlockShape(state, level, pos) : Shapes.empty();
				}
			};
			if (minecraft.level.clip(context).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	public static String statistics() {
		return "occluded=" + rejected + ", cells=" + CACHE.size() + ", pending=" + PENDING.size();
	}

	public static void clear() {
		CACHE.clear();
		PENDING.clear();
		rejected = 0;
	}

	private static class Cell {
		private final int x, y, z;
		private long checkedAt = Long.MIN_VALUE / 2;
		private long seen;
		private boolean visible = true;
		private boolean queued;

		private Cell(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
