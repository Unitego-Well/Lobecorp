package org.unitego.lobecorp.world.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/// 只在已加载区域做有界的粗网格可达预检，实际路径仍由正常实体导航负责。
public class HordeSpawnPlanner {
	private static final int REACH_NODES = 32;
	private static final double REACH_GRID = 2;
	private static final int POSITION_ATTEMPTS = 16;

	public static @Nullable Vec3 candidate(ServerLevel level, HordeEvent event, LivingEntity target, int attempt, EntityDimensions dimensions) {
		attempt = Math.floorMod(attempt, POSITION_ATTEMPTS);
		double station = event.station(target.position());
		Vec3 direction = event.route().direction(station);
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		double distance = event.rules().spawnDistance() + target.getBbWidth() / 2 + dimensions.width() / 2;
		int lateralStep = (attempt + 1) / 2;
		double lateral = lateralStep * (attempt % 2 == 0 ? 1 : -1) * REACH_GRID;
		Vec3 point = target.position().subtract(direction.scale(distance)).add(side.scale(attempt == 0 ? 0 : lateral));
		Vec3 floor = HordeRoute.ground(level, point);
		if (floor == null || !safe(level, floor, dimensions) || !reachable(level, floor, target.position(), dimensions)) {
			return null;
		}
		return floor;
	}

	public static boolean hidden(ServerLevel level, Vec3 point, EntityDimensions dimensions) {
		for (var player : level.players()) {
			if (player.distanceToSqr(point) < 4096 && level.clip(new ClipContext(player.getEyePosition(),
							point.add(0, dimensions.height() / 2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
					.getType() == HitResult.Type.MISS) {
				return false;
			}
		}
		return true;
	}

	private static boolean safe(ServerLevel level, Vec3 point, EntityDimensions dimensions) {
		BlockPos block = BlockPos.containing(point);
		return level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) != null
				&& level.getFluidState(block.below()).isEmpty()
				&& level.noCollision(dimensions.makeBoundingBox(point));
	}

	private static boolean reachable(ServerLevel level, Vec3 start, Vec3 goal, EntityDimensions dimensions) {
		var queue = new ArrayDeque<Vec3>();
		Set<Long> visited = new HashSet<>();
		queue.add(start);
		for (int expanded = 0; expanded < REACH_NODES && !queue.isEmpty(); expanded++) {
			Vec3 current = queue.removeFirst();
			if (current.distanceToSqr(goal) <= REACH_GRID * REACH_GRID * 2) {
				return true;
			}
			int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] offset : offsets) {
				Vec3 next = HordeRoute.ground(level, current.add(offset[0] * REACH_GRID, 0, offset[1] * REACH_GRID));
				if (next != null && Math.abs(next.y - current.y) <= 1.5
						&& next.distanceToSqr(start) < 1024 && visited.add(BlockPos.containing(next).asLong()) && safe(level, next, dimensions)) {
					queue.addLast(next);
				}
			}
		}
		return false;
	}
}
