package org.unitego.lobecorp.util.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.control.ConductorMovement;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.SweepSkill;

import java.util.List;
import java.util.UUID;

/// 清道夫自动清扫和重组共用的工作站位与接近逻辑，不修改技能自身的施放范围。
public class SweeperWorkUtil {
	/// 自动开始工作时双方水平碰撞框边缘允许的最大间距，单位为格。
	private static final double WORK_DISTANCE = 0.5D;
	/// 站位预留半个工作间距，避免方块路径终点误差使实体停在范围外。
	private static final double POSITION_MARGIN = WORK_DISTANCE / 2.0D;
	/// 每次定位依次尝试的目标周围方向数量。
	private static final int POSITION_SIDES = 8;

	public static boolean isReady(Sweeper sweeper, Entity target) {
		return isNearTarget(sweeper.getBoundingBox(), target)
				&& SweepSkill.isWithinRange(sweeper, target);
	}

	private static boolean isNearTarget(AABB bounds, Entity target) {
		AABB targetBounds = target.getBoundingBox();
		double dx = Math.max(0.0D, Math.max(targetBounds.minX - bounds.maxX, bounds.minX - targetBounds.maxX));
		double dz = Math.max(0.0D, Math.max(targetBounds.minZ - bounds.maxZ, bounds.minZ - targetBounds.maxZ));
		return dx * dx + dz * dz <= WORK_DISTANCE * WORK_DISTANCE;
	}

	private static Vec3 requestedPosition(Sweeper sweeper, Entity target, int side) {
		AABB bounds = target.getBoundingBox();
		Vec3 center = bounds.getCenter();
		double angle = Math.atan2(sweeper.getZ() - center.z, sweeper.getX() - center.x)
				+ side * Math.PI * 2.0D / POSITION_SIDES;
		double x = Math.cos(angle);
		double z = Math.sin(angle);
		double halfWidth = sweeper.getBbWidth() / 2.0D + POSITION_MARGIN;
		double radius = Math.min((bounds.getXsize() / 2.0D + halfWidth) / Math.abs(x),
				(bounds.getZsize() / 2.0D + halfWidth) / Math.abs(z));
		return new Vec3(center.x + x * radius, target.getY(), center.z + z * radius);
	}

	private static void stopMovement(Sweeper sweeper) {
		sweeper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		sweeper.getBrain().eraseMemory(MemoryModuleType.PATH);
		sweeper.getNavigation().stop();
		sweeper.getMoveControl().setWait();
	}

	/// 每个自动工作控制方各自保存的临时接近状态，不持久化或同步。
	public static class Approach {
		private @Nullable UUID targetId;
		private @Nullable Vec3 targetPosition;
		private @Nullable Vec3 destination;
		private @Nullable Vec3 progress;
		private long progressAt;
		private long retryAt;
		private int nextSide;
		private int replans;

		public void reset() {
			targetId = null;
			targetPosition = null;
			destination = null;
			progress = null;
			retryAt = 0L;
			nextSide = 0;
			replans = 0;
		}

		public void clear(Sweeper sweeper) {
			if (targetId != null) {
				stopMovement(sweeper);
			}

			reset();
		}

		/// 技能占用或冷却等待不计入移动停滞时间。
		public void pause(Sweeper sweeper, long now) {
			progress = sweeper.position();
			progressAt = now;
		}

		/// 返回 false 表示尝试其他站位后仍无法接近，调用方可以重新选择工作目标。
		public boolean advance(Sweeper sweeper, Entity target, double speed, long now) {
			if (!target.getUUID().equals(targetId)) {
				stopMovement(sweeper);
				reset();
				targetId = target.getUUID();
				targetPosition = target.position();
				pause(sweeper, now);
			}

			sweeper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			sweeper.getBrain().eraseMemory(MemoryModuleType.PATH);
			sweeper.getLookControl().setLookAt(target);

			if (isReady(sweeper, target)) {
				stopMovement(sweeper);
				pause(sweeper, now);
				replans = 0;
				return true;
			}

			if (progress == null || sweeper.position().distanceToSqr(progress)
					>= ConductorRules.MOVEMENT_PROGRESS_DISTANCE * ConductorRules.MOVEMENT_PROGRESS_DISTANCE) {
				pause(sweeper, now);
			}

			if (targetPosition == null || target.position().distanceToSqr(targetPosition)
					>= ConductorRules.MOVEMENT_PROGRESS_DISTANCE * ConductorRules.MOVEMENT_PROGRESS_DISTANCE) {
				targetPosition = target.position();
				destination = null;
				retryAt = now;
				stopMovement(sweeper);
			}

			if (now - progressAt >= ConductorRules.MOVEMENT_STALL_TICKS) {
				destination = null;
				retryAt = now;
				stopMovement(sweeper);
				pause(sweeper, now);

				if (++replans > ConductorRules.MOVEMENT_MAX_REPLANS) {
					return false;
				}
			}

			if (destination != null && !ConductorController.allowNavigation(sweeper, destination)) {
				destination = null;
				retryAt = now;
				stopMovement(sweeper);
			}

			if (destination != null && sweeper.distanceToSqr(destination)
					<= ConductorRules.MOVEMENT_PROGRESS_DISTANCE * ConductorRules.MOVEMENT_PROGRESS_DISTANCE) {
				destination = null;
				retryAt = now;
				stopMovement(sweeper);
				pause(sweeper, now);

				if (++replans > ConductorRules.MOVEMENT_MAX_REPLANS) {
					return false;
				}
			}

			if (destination != null && sweeper.distanceToSqr(destination)
					<= ConductorRules.FINAL_APPROACH_DISTANCE * ConductorRules.FINAL_APPROACH_DISTANCE
					&& ConductorMovement.safeApproach(sweeper, destination)) {
				sweeper.getNavigation().stop();
				sweeper.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, speed);
				return true;
			}

			if (now < retryAt || destination != null && !sweeper.getNavigation().isDone()) {
				return true;
			}

			retryAt = now + ConductorRules.MOVEMENT_RETRY_TICKS;
			stopMovement(sweeper);

			if (locate(sweeper, target, speed)) {
				return true;
			}

			return ++replans <= ConductorRules.MOVEMENT_MAX_REPLANS;
		}

		private boolean locate(Sweeper sweeper, Entity target, double speed) {
			destination = null;

			for (int attempt = 0; attempt < POSITION_SIDES; attempt++) {
				int side = (nextSide + attempt) % POSITION_SIDES;
				Vec3 candidate = ConductorMovement.resolve(sweeper, requestedPosition(sweeper, target, side), List.of());

				if (candidate == null || !ConductorController.allowNavigation(sweeper, candidate)
						|| !isNearTarget(sweeper.getBoundingBox().move(candidate.subtract(sweeper.position())), target)) {
					continue;
				}

				boolean direct = sweeper.distanceToSqr(candidate)
						<= ConductorRules.FINAL_APPROACH_DISTANCE * ConductorRules.FINAL_APPROACH_DISTANCE
						&& ConductorMovement.safeApproach(sweeper, candidate);
				Path path = direct ? null : sweeper.getNavigation().createPath(BlockPos.containing(candidate), 0,
						ConductorRules.PATH_SEGMENT_RANGE);

				if (!direct && (path == null || !path.canReach())) {
					continue;
				}

				if (direct) {
					sweeper.getMoveControl().setWantedPosition(candidate.x, candidate.y, candidate.z, speed);
				} else if (!sweeper.getNavigation().moveTo(path, speed)) {
					sweeper.getNavigation().stop();
					continue;
				}

				destination = candidate;
				nextSide = (side + 1) % POSITION_SIDES;
				return true;
			}

			nextSide = (nextSide + 1) % POSITION_SIDES;
			return false;
		}
	}
}
