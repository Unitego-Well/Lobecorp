package org.unitego.lobecorp.conductor.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// 指挥单位路径、队形槽位和到达判定的运行控制。
public final class ConductorMovement {
	private ConductorMovement() {
	}

	public static boolean flying(Mob mob) {
		return mob instanceof Ghast || mob instanceof Phantom || mob instanceof EnderDragon || mob.isNoGravity()
				|| mob.getNavigation() instanceof FlyingPathNavigation;
	}

	public static double baseSpeed(Mob mob) {
		return flying(mob) && !mob.onGround() && mob.getAttribute(Attributes.FLYING_SPEED) != null
				? mob.getAttributeValue(Attributes.FLYING_SPEED) : mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	public static Vec3 uniformMovement(Entity entity, MoverType moverType, Vec3 movement) {
		if (moverType != MoverType.SELF || !(entity instanceof Mob mob)
				|| !(mob.level() instanceof ServerLevel level))
			return movement;
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(mob.getUUID());
		if (unit == null || unit.order() != ConductorData.OrderType.MOVE || unit.movementSpeed() < 0.0D)
			return movement;
		double speed = flying(mob) ? movement.length() : movement.horizontalDistance();
		if (speed <= unit.movementSpeed())
			return movement;
		double ratio = unit.movementSpeed() / speed;
		return flying(mob) ? movement.scale(ratio) : movement.multiply(ratio, 1.0D, ratio);
	}

	public static Vec3 navigationDestination(Mob mob, Vec3 destination) {
		Progress state = ConductorUnitRuntime.get(mob).movement;
		return state == null || state.detour == null ? destination : state.detour;
	}

	private static double speedModifier(Mob mob, ConductorData.Unit unit) {
		double base = baseSpeed(mob);
		return unit.movementSpeed() < 0.0D ? ConductorRules.MOVEMENT_SPEED
				: base <= 0.0D ? 0.0D : Math.min(ConductorRules.MOVEMENT_SPEED, unit.movementSpeed() / base);
	}

	public static boolean arrived(Mob mob, Vec3 destination) {
		Vec3 delta = destination.subtract(mob.position());
		double tolerance = mob instanceof EnderDragon ? ConductorRules.DRAGON_ARRIVAL_DISTANCE : ConductorRules.ARRIVAL_DISTANCE;
		return flying(mob) ? delta.lengthSqr() <= tolerance * tolerance
				: delta.horizontalDistanceSqr() <= tolerance * tolerance && Math.abs(delta.y) <= ConductorRules.ARRIVAL_HEIGHT;
	}

	public static void clear(Mob mob) {
		ConductorUnitRuntime.get(mob).movement = null;
		mob.getNavigation().stop();
		mob.getMoveControl().setWait();
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		Vec3 velocity = mob.getDeltaMovement();
		mob.setDeltaMovement(flying(mob) ? Vec3.ZERO : new Vec3(0.0, velocity.y, 0.0));
	}

	public static boolean safeApproach(Mob mob, Vec3 destination) {
		return safeApproach(mob, mob.position(), destination);
	}

	private static boolean safeApproach(Mob mob, Vec3 origin, Vec3 destination) {
		Vec3 delta = destination.subtract(origin);
		int samples = Math.max(1, (int) Math.ceil(delta.length() / ConductorRules.PATH_SAMPLE_DISTANCE));
		for (int index = 1; index <= samples; index++) {
			Vec3 point = origin.add(delta.scale(index / (double) samples));
			AABB bounds = mob.getBoundingBox().move(point.subtract(mob.position()));
			if (!mob.level().noCollision(mob, bounds))
				return false;
			if (!flying(mob) && !supported(mob, bounds))
				return false;
		}
		return true;
	}

	private static @Nullable Entity blockingEntity(Mob mob, Vec3 origin, Vec3 destination) {
		double margin = mob.getBbWidth() / 2.0D + ConductorRules.ENTITY_AVOIDANCE_MARGIN;
		AABB corridor = mob.getBoundingBox().move(origin.subtract(mob.position()))
				.expandTowards(destination.subtract(origin)).inflate(ConductorRules.ENTITY_AVOIDANCE_MARGIN);
		Entity nearest = null;
		double distance = Double.POSITIVE_INFINITY;
		for (Entity entity : mob.level().getEntities(mob, corridor, entity -> entity.isAlive() && entity.isPushable())) {
			if (entity.getRootVehicle() == mob.getRootVehicle())
				continue;
			AABB bounds = entity.getBoundingBox().inflate(margin, 0.0D, margin).expandTowards(0.0D, -mob.getBbHeight(), 0.0D);
			if (bounds.contains(origin) && !bounds.contains(destination))
				continue;
			Optional<Vec3> hit = bounds.clip(origin, destination);
			if (!bounds.contains(destination) && hit.isEmpty())
				continue;
			double candidate = hit.orElse(destination).distanceToSqr(origin);
			if (candidate < distance) {
				distance = candidate;
				nearest = entity;
			}
		}
		return nearest;
	}

	private static @Nullable Entity pathBlocker(Mob mob, PathNavigation navigation, Vec3 destination) {
		Path path = navigation.getPath();
		Vec3 origin = mob.position();
		double remaining = ConductorRules.ENTITY_AVOIDANCE_LOOKAHEAD;
		int index = path == null ? 0 : path.getNextNodeIndex();
		if (path == null || navigation.isDone()) {
			Vec3 delta = destination.subtract(origin);
			Vec3 end = delta.length() > remaining ? origin.add(delta.normalize().scale(remaining)) : destination;
			if (!safeApproach(mob, end))
				return null;
		}
		do {
			Vec3 next = path != null && index < path.getNodeCount() ? path.getEntityPosAtNode(mob, index++) : destination;
			Vec3 delta = next.subtract(origin);
			if (delta.length() > remaining)
				next = origin.add(delta.normalize().scale(remaining));
			Entity blocker = blockingEntity(mob, origin, next);
			if (blocker != null)
				return blocker;
			remaining -= origin.distanceTo(next);
			origin = next;
		} while (remaining > ConductorRules.ARRIVAL_DISTANCE && path != null && index < path.getNodeCount());
		return null;
	}

	private static boolean detour(Mob mob, Entity blocker, Progress state) {
		Vec3 direction = blocker.position().subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
		if (direction.lengthSqr() < ConductorRules.FORMATION_DIRECTION_EPSILON) {
			direction = state.destination.subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
		}
		if (direction.lengthSqr() < ConductorRules.FORMATION_DIRECTION_EPSILON)
			return false;
		direction = direction.normalize();
		Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
		double clearance = (blocker.getBbWidth() + mob.getBbWidth()) / 2.0D
				+ ConductorRules.ENTITY_AVOIDANCE_MARGIN + ConductorRules.ARRIVAL_DISTANCE;
		for (int side = 0; side < ConductorRules.ENTITY_AVOIDANCE_SIDES; side++) {
			Vec3 offset = right.scale(side == 0 ? clearance : -clearance);
			Vec3 entry = project(mob, mob.position().add(offset));
			Vec3 exit = project(mob, new Vec3(blocker.getX(), mob.getY(), blocker.getZ()).add(offset).add(direction.scale(clearance)));
			if (entry == null || exit == null || exit.subtract(state.destination).dot(direction) > 0.0D
					|| !safeApproach(mob, mob.position(), entry)
					|| !safeApproach(mob, entry, exit) || blockingEntity(mob, mob.position(), entry) != null
					|| blockingEntity(mob, entry, exit) != null)
				continue;
			state.detour = entry;
			state.detourExit = exit;
			state.position = mob.position();
			return true;
		}
		return false;
	}

	private static boolean avoidPathEntities(Mob mob, PathNavigation navigation, Progress state, double speed) {
		if (mob.distanceToSqr(state.destination) <= ConductorRules.FINAL_APPROACH_DISTANCE * ConductorRules.FINAL_APPROACH_DISTANCE) {
			state.detour = null;
			state.detourExit = null;
			return false;
		}
		if (state.detour != null && arrived(mob, state.detour)) {
			state.detour = state.detourExit;
			state.detourExit = null;
			state.position = mob.position();
			state.progressAt = mob.tickCount;
			state.avoidanceAt = mob.tickCount;
		}
		if (state.detour != null && (!safeApproach(mob, state.detour)
				|| blockingEntity(mob, mob.position(), state.detour) != null)) {
			state.detour = null;
			state.detourExit = null;
			state.avoidanceAt = mob.tickCount;
		}
		if (state.detour == null && mob.tickCount >= state.avoidanceAt) {
			Entity blocker = pathBlocker(mob, navigation, state.destination);
			if (blocker != null)
				detour(mob, blocker, state);
			state.avoidanceAt = mob.tickCount + ConductorRules.MOVEMENT_RETRY_TICKS;
		}
		if (state.detour == null)
			return false;
		navigation.stop();
		mob.getMoveControl().setWantedPosition(state.detour.x, state.detour.y, state.detour.z, speed);
		return true;
	}

	private static boolean supported(Mob mob, AABB bounds) {
		AABB probe = new AABB(bounds.minX, bounds.minY - ConductorRules.SUPPORT_PROBE_DEPTH, bounds.minZ,
				bounds.maxX, bounds.minY, bounds.maxZ);
		return mob.level().getBlockCollisions(mob, probe).iterator().hasNext();
	}

	private static @Nullable Vec3 project(Mob mob, Vec3 requested) {
		if (flying(mob))
			return mob.level().noCollision(mob,
					mob.getBoundingBox().move(requested.subtract(mob.position()))) ? requested : null;
		AABB column = new AABB(requested.x - mob.getBbWidth() / 2.0, requested.y - ConductorRules.SLOT_SEARCH_RADIUS,
				requested.z - mob.getBbWidth() / 2.0, requested.x + mob.getBbWidth() / 2.0,
				requested.y + ConductorRules.SLOT_SEARCH_RADIUS, requested.z + mob.getBbWidth() / 2.0);
		List<Double> heights = new ArrayList<>();
		for (VoxelShape shape : mob.level().getBlockCollisions(mob, column)) {
			for (AABB bounds : shape.toAabbs())
				heights.add(bounds.maxY);
		}
		heights.sort(Comparator.comparingDouble(height -> Math.abs(height - requested.y)));
		for (double height : heights) {
			Vec3 candidate = new Vec3(requested.x, height, requested.z);
			AABB bounds = mob.getBoundingBox().move(candidate.subtract(mob.position()));
			if (mob.level().noCollision(mob, bounds) && supported(mob, bounds))
				return candidate;
		}
		return null;
	}

	public static @Nullable Vec3 resolve(Mob mob, Vec3 requested, List<AABB> occupied) {
		for (int radius = 0; radius <= ConductorRules.SLOT_SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++)
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius
							|| dx * dx + dz * dz > ConductorRules.SLOT_SEARCH_RADIUS * ConductorRules.SLOT_SEARCH_RADIUS)
						continue;
					Vec3 candidate = project(mob, requested.add(dx, 0.0, dz));
					if (candidate == null || candidate.distanceToSqr(requested)
							> ConductorRules.SLOT_SEARCH_RADIUS * ConductorRules.SLOT_SEARCH_RADIUS)
						continue;
					AABB bounds = mob.getBoundingBox().move(candidate.subtract(mob.position())).inflate(ConductorRules.ENTITY_AVOIDANCE_MARGIN);
					if (occupied.stream().anyMatch(bounds::intersects))
						continue;
					if (mob instanceof EnderDragon || arrived(mob, candidate)
							|| candidate.distanceToSqr(mob.position()) > ConductorRules.PATH_SEGMENT_RANGE * ConductorRules.PATH_SEGMENT_RANGE
							|| candidate.distanceToSqr(mob.position()) <= ConductorRules.FINAL_APPROACH_DISTANCE * ConductorRules.FINAL_APPROACH_DISTANCE
							&& safeApproach(mob, candidate))
						return candidate;
					PathNavigation navigation = mob instanceof Ghast ? new FlyingPathNavigation(mob, mob.level()) : mob.getNavigation();
					Path path = navigation.createPath(BlockPos.containing(candidate), 0, ConductorRules.PATH_SEGMENT_RANGE);
					if (usable(mob, path))
						return candidate;
				}
		}
		return null;
	}

	private static boolean usable(Mob mob, @Nullable Path path) {
		if (path == null || path.getNodeCount() == 0)
			return false;
		Vec3 endpoint = path.getEntityPosAtNode(mob, path.getNodeCount() - 1);
		return path.canReach() || endpoint.distanceToSqr(mob.position())
				>= ConductorRules.PATH_MIN_PROGRESS * ConductorRules.PATH_MIN_PROGRESS;
	}

	public static boolean advance(Mob mob, PathNavigation navigation, ConductorData data, ConductorData.Unit unit) {
		Vec3 destination = new Vec3(unit.x(), unit.y(), unit.z());
		Progress state = ConductorUnitRuntime.get(mob).movement;
		if (state == null) {
			Vec3 resolved = resolve(mob, destination, List.of());
			if (resolved == null) {
				clear(mob);
				data.update(mob.getUUID(), value -> {
					value.command(ConductorData.OrderType.NONE, null, value.destination);
					value.moveResult = ConductorData.MoveResult.UNREACHABLE;
				});
				return false;
			}
			state = new Progress(mob, resolved);
			ConductorUnitRuntime.get(mob).movement = state;
			if (!resolved.equals(destination)) {
				data.update(mob.getUUID(), value -> value.destination = resolved);
				return false;
			}
		}
		if (!destination.equals(state.destination)) {
			state = new Progress(mob, destination);
			ConductorUnitRuntime.get(mob).movement = state;
		}
		double progress = ConductorRules.MOVEMENT_PROGRESS_DISTANCE;
		Path currentPath = navigation.getPath();
		boolean pathProgress = currentPath != null && currentPath == state.path
				&& currentPath.getNextNodeIndex() > state.pathIndex;
		Vec3 progressTarget = state.detour == null ? destination : state.detour;
		if (state.position.distanceTo(progressTarget) - mob.position().distanceTo(progressTarget) >= progress || pathProgress) {
			state.position = mob.position();
			state.progressAt = mob.tickCount;
			state.replans = 0;
		}
		state.path = currentPath;
		state.pathIndex = currentPath == null ? 0 : currentPath.getNextNodeIndex();
		if (mob.tickCount - state.progressAt >= ConductorRules.MOVEMENT_STALL_TICKS) {
			state.progressAt = mob.tickCount;
			navigation.stop();
			mob.getMoveControl().setWait();
			state.retryAt = mob.tickCount;
			state.detour = null;
			state.detourExit = null;
			state.avoidanceAt = mob.tickCount;
			if (++state.replans > ConductorRules.MOVEMENT_MAX_REPLANS) {
				Vec3 replacement = state.alternative ? null : resolve(mob, destination,
						List.of(mob.getBoundingBox().move(destination.subtract(mob.position()))));
				if (replacement == null) {
					clear(mob);
					data.update(mob.getUUID(), value -> {
						value.command(ConductorData.OrderType.NONE, null, value.destination);
						value.moveResult = ConductorData.MoveResult.UNREACHABLE;
					});
					return false;
				}
				state.alternative = true;
				state.replans = 0;
				state.destination = replacement;
				data.update(mob.getUUID(), value -> value.destination = replacement);
				return false;
			}
		}
		double speed = speedModifier(mob, unit);
		navigation.setSpeedModifier(speed);
		if (avoidPathEntities(mob, navigation, state, speed))
			return true;
		if (mob instanceof EnderDragon)
			return true;
		if (destination.distanceToSqr(mob.position())
				<= ConductorRules.FINAL_APPROACH_DISTANCE * ConductorRules.FINAL_APPROACH_DISTANCE
				&& safeApproach(mob, destination)) {
			navigation.stop();
			mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, speed);
		} else if ((navigation.isDone() || !BlockPos.containing(destination).equals(navigation.getTargetPos()))
				&& mob.tickCount >= state.retryAt) {
			Path path = navigation.createPath(BlockPos.containing(destination), 0, ConductorRules.PATH_SEGMENT_RANGE);
			if (usable(mob, path))
				navigation.moveTo(path, speed);
			state.retryAt = mob.tickCount + ConductorRules.MOVEMENT_RETRY_TICKS;
		}
		return true;
	}

	protected static final class Progress {
		private Vec3 destination;
		private Vec3 position;
		private int progressAt;
		private int retryAt;
		private int replans;
		private Path path;
		private int pathIndex;
		private boolean alternative;
		private Vec3 detour;
		private Vec3 detourExit;
		private int avoidanceAt;

		private Progress(Mob mob, Vec3 destination) {
			this.destination = destination;
			position = mob.position();
			progressAt = mob.tickCount;
		}
	}
}
