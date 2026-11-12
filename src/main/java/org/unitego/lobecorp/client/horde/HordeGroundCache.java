package org.unitego.lobecorp.client.horde;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

/// 只读已加载区块的地表列；方块更新、区块替换与切世界使样本失效。
public class HordeGroundCache {
	private static final Long2ObjectOpenHashMap<Entry> columns = new Long2ObjectOpenHashMap<>();
	private static ClientLevel world;
	private static long cleaned = Long.MIN_VALUE;

	public static Sample sample(double x, double z) {
		var level = Minecraft.getInstance().level;
		if (world != level) {
			clear();
			world = level;
		}
		if (level == null) {
			return null;
		}

		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		long key = BlockPos.asLong(bx, 0, bz);
		var chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
		if (chunk == null) {
			columns.remove(key);
			return null;
		}

		var cached = columns.get(key);
		if (cached != null && cached.chunk() == chunk) {
			return cached.sample();
		}

		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		var position = new BlockPos(bx, y - 1, bz);
		var state = level.getBlockState(position);
		var shape = state.getCollisionShape(level, position);
		double height = shape.isEmpty() ? y : y - 1 + shape.max(Direction.Axis.Y);
		boolean passable = !shape.isEmpty() && state.getFluidState().isEmpty()
				&& level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
				&& level.getBlockState(position.above(2)).getCollisionShape(level, position.above(2)).isEmpty();
		var result = new Sample(height, state, passable);
		columns.put(key, new Entry(chunk, result));
		return result;
	}

	public static void tick() {
		var level = Minecraft.getInstance().level;
		if (world != level) {
			clear();
			world = level;
		}
		if (level == null || level.getGameTime() == cleaned) {
			return;
		}
		cleaned = level.getGameTime();
		if (cleaned % 20 != 0) {
			return;
		}

		var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		double range = Math.max(96, HordeVisualConfig.PARTICLE_RANGE.get() + 16);
		columns.keySet().removeIf(key -> Math.abs(BlockPos.getX(key) - camera.x) > range
				|| Math.abs(BlockPos.getZ(key) - camera.z) > range);
	}

	public static void invalidate(BlockPos position) {
		// 邻柱碰撞形状可依赖本柱方块，连同一格邻域失效。
		for (int z = -1; z <= 1; z++) {
			for (int x = -1; x <= 1; x++) {
				columns.remove(BlockPos.asLong(position.getX() + x, 0, position.getZ() + z));
				HordeNearTerrain.invalidate(position.getX() + x, position.getZ() + z);
			}
		}
	}

	public static void invalidateChunk(int x, int z) {
		for (int dz = -1; dz <= 16; dz++) {
			for (int dx = -1; dx <= 16; dx++) {
				columns.remove(BlockPos.asLong(x * 16 + dx, 0, z * 16 + dz));
				HordeNearTerrain.invalidate(x * 16 + dx, z * 16 + dz);
			}
		}
	}

	public static void clear() {
		columns.clear();
		world = null;
		cleaned = Long.MIN_VALUE;
	}

	public record Sample(double height, BlockState state, boolean passable) {
	}

	private record Entry(LevelChunk chunk, Sample sample) {
	}
}
