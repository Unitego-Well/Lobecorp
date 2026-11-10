package org.unitego.lobecorp.world.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/// 只在已加载区域做有界的粗网格可达预检，实际路径仍由正常实体导航负责。
public class HordeSpawnPlanner {
	// 每个候选最多扩展的粗网格节点数，搜索优先朝目标推进。
	private static final int REACH_NODES = 128;
	private static final double REACH_GRID = 2;
	private static final int POSITION_ATTEMPTS = 16;
	// 优先在目标后方较远处生成，失败时逐级缩短，避免 16 格附近不可达时完全不生成。
	private static final double[] SPAWN_DISTANCES = {16, 12, 8, 4};

	public static @Nullable Vec3 candidate(ServerLevel level, HordeInstance event, LivingEntity target, HordeCombatRegion region, int attempt, EntityDimensions dimensions) {
		attempt = Math.floorMod(attempt, POSITION_ATTEMPTS);
		double station = event.station(target.position());
		Vec3 direction = event.route().direction(station);
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		double distance = Math.max(event.rules().spawnDistance(),
				SPAWN_DISTANCES[Math.min(SPAWN_DISTANCES.length - 1, attempt / 4)])
				+ target.getBbWidth() / 2 + dimensions.width() / 2;
		int localAttempt = attempt % 4;
		int lateralStep = (localAttempt + 1) / 2;
		double lateral = lateralStep * (localAttempt % 2 == 0 ? 1 : -1) * REACH_GRID;
		Vec3 point = target.position().subtract(direction.scale(distance)).add(side.scale(localAttempt == 0 ? 0 : lateral));
		// 方区先提供局部候选；狭小方区无法落脚时仍尝试原距离，避免阻断出生。
		Vec3 floor = HordeRoute.ground(level, region.clampHorizontal(point, dimensions.width() / 2));
		if (floor != null && valid(level, floor, target.position(), dimensions)) {
			return floor;
		}
		floor = HordeRoute.ground(level, point);
		return floor != null && valid(level, floor, target.position(), dimensions) ? floor : null;
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

	public static boolean valid(ServerLevel level, Vec3 point, Vec3 goal, EntityDimensions dimensions) {
		return safe(level, point, dimensions) && reachable(level, point, goal, dimensions);
	}

	private static boolean safe(ServerLevel level, Vec3 point, EntityDimensions dimensions) {
		BlockPos block = BlockPos.containing(point);
		return level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) != null
				&& level.getFluidState(block.below()).isEmpty()
				&& level.noCollision(dimensions.makeBoundingBox(point));
	}

	public static boolean reachable(ServerLevel level, Vec3 start, Vec3 goal, EntityDimensions dimensions) {
		return reachable(start, goal, point -> HordeRoute.ground(level, point), point -> safe(level, point, dimensions));
	}

	/// 有界粗网格预检；采样器只提供已加载地面，通行条件由调用方持有。
	public static boolean reachable(Vec3 start, Vec3 goal, UnaryOperator<Vec3> sampler, Predicate<Vec3> passable) {
		var queue = new PriorityQueue<Vec3>(Comparator.comparingDouble(point -> point.distanceToSqr(goal)));
		Set<Long> visited = new HashSet<>();
		visited.add(BlockPos.containing(start).asLong());
		queue.add(start);
		for (int expanded = 0; expanded < REACH_NODES && !queue.isEmpty(); expanded++) {
			Vec3 current = queue.remove();
			if (current.distanceToSqr(goal) <= REACH_GRID * REACH_GRID * 2) {
				return true;
			}
			int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] offset : offsets) {
				Vec3 next = sampler.apply(current.add(offset[0] * REACH_GRID, 0, offset[1] * REACH_GRID));
				if (next != null && Math.abs(next.y - current.y) <= 1.5
						&& next.distanceToSqr(start) <= Math.pow(Math.max(32, start.distanceTo(goal) + REACH_GRID), 2) && visited.add(BlockPos.containing(next).asLong()) && passable.test(next)) {
					queue.add(next);
				}
			}
		}
		return false;
	}
}
