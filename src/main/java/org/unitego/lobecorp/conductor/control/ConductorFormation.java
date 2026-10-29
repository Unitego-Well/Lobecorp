package org.unitego.lobecorp.conductor.control;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 指挥队形移动的领域实现，负责按维度分组、排序和分配可达槽位。
public class ConductorFormation {
	/// 为所选单位分配队形槽位并写入移动命令，保持输入遍历顺序与维度分组。
	public static void issue(ServerPlayer player, ConductorData data, List<UUID> units,
	                         double x, double y, double z, ConductorData.FormationMode formationMode) {
		var byDimension = groupUnits(player, data, units);
		var destination = new Vec3(x, y, z);

		for (List<FormationUnit> group : byDimension.values()) {
			if (group.isEmpty()) {
				continue;
			}

			double startX = group.stream().filter(entry -> entry.mob() != null)
					.mapToDouble(entry -> entry.mob().getX()).average().orElse(x);
			double startZ = group.stream().filter(entry -> entry.mob() != null)
					.mapToDouble(entry -> entry.mob().getZ()).average().orElse(z);
			var forward = new Vec3(x - startX, 0.0D, z - startZ);

			if (forward.lengthSqr() < ConductorRules.FORMATION_DIRECTION_EPSILON) {
				forward = new Vec3(0.0D, 0.0D, 1.0D);
			} else {
				forward = forward.normalize();
			}

			var right = new Vec3(forward.z, 0.0D, -forward.x);
			Vec3 direction = forward;
			group.sort(Comparator.comparingDouble((FormationUnit entry) -> entry.position().dot(direction)).reversed()
					.thenComparing(FormationUnit::uuid));
			double movementSpeed = formationMode == ConductorData.FormationMode.UNIFORM
					? group.stream().filter(entry -> entry.mob() != null).mapToDouble(entry -> ConductorMovement.baseSpeed(entry.mob()))
					.min().orElse(ConductorRules.NATURAL_MOVEMENT_SPEED) : ConductorRules.NATURAL_MOVEMENT_SPEED;
			int columns = (int) Math.ceil(Math.sqrt(group.size()));
			int rows = (int) Math.ceil(group.size() / (double) columns);
			double maxWidth = group.stream().filter(entry -> entry.mob() != null)
					.mapToDouble(entry -> entry.mob().getBbWidth()).max().orElse(ConductorRules.FORMATION_DEFAULT_WIDTH);
			double spacing = maxWidth + ConductorRules.FORMATION_GAP;

			for (int rowStart = 0; rowStart < group.size(); rowStart += columns) {
				group.subList(rowStart, Math.min(group.size(), rowStart + columns)).sort(
						Comparator.comparingDouble((FormationUnit entry) -> entry.position().dot(right)).thenComparing(FormationUnit::uuid));
			}

			List<Vec3> slots = new ArrayList<>();

			for (int index = 0; index < group.size(); index++) {
				int row = index / columns;
				int column = index % columns;
				int rowSize = Math.min(columns, group.size() - row * columns);
				double sideOffset = (column - (rowSize - 1) / 2.0D) * spacing;
				double forwardOffset = (row - (rows - 1) / 2.0D) * spacing;
				Vec3 slot = destination.add(right.scale(sideOffset)).subtract(forward.scale(forwardOffset));
				slots.add(formationMode == ConductorData.FormationMode.SCATTERED ? destination : slot);
			}

			List<AABB> occupied = new ArrayList<>();

			for (int index = 0; index < group.size(); index++) {
				FormationUnit nearest = group.get(index);
				Vec3 nearestSlot = Objects.requireNonNull(slots.get(index));
				Vec3 resolved = nearestSlot;

				if (nearest.mob() != null) {
					ConductorController.stop(nearest.mob());
					resolved = ConductorMovement.resolve(nearest.mob(), nearestSlot,
							formationMode == ConductorData.FormationMode.SCATTERED ? List.of() : occupied);
				}

				if (resolved == null) {
					data.update(nearest.uuid(), state -> {
						state.command(ConductorData.OrderType.NONE, null, nearestSlot);
						state.moveResult = ConductorData.MoveResult.UNREACHABLE;
					});
					continue;
				}

				if (nearest.mob() != null) {
					occupied.add(nearest.mob().getBoundingBox()
							.move(resolved.subtract(nearest.mob().position())).inflate(ConductorRules.ENTITY_AVOIDANCE_MARGIN));
				}
				Vec3 slot = resolved;
				data.update(nearest.uuid(), state -> {
					state.command(ConductorData.OrderType.MOVE, null, slot);
					state.movementSpeed = movementSpeed;
				});
			}
		}
	}

	/// 保留单位遍历顺序，按维度收集可参加队形移动的单位。
	private static Map<String, List<FormationUnit>> groupUnits(ServerPlayer player, ConductorData data, List<UUID> units) {
		Map<String, List<FormationUnit>> byDimension = new LinkedHashMap<>();

		for (UUID uuid : units) {
			if (!data.acceptsControl(uuid)) {
				continue;
			}
			ConductorData.Unit unit = data.unit(uuid);

			if (unit == null) {
				continue;
			}

			ServerLevel level = player.level().getServer().getLevel(
					ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
			Entity entity = level == null ? null : level.getEntity(uuid);
			Mob mob = entity instanceof Mob value ? value : null;

			if (mob != null && !mob.isAlive()) {
				continue;
			}
			byDimension.computeIfAbsent(unit.dimension(), _ -> new ArrayList<>())
					.add(new FormationUnit(uuid, unit, mob));
		}

		return byDimension;
	}

	private record FormationUnit(UUID uuid, ConductorData.Unit unit, Mob mob) {
		private Vec3 position() {
			return mob == null ? new Vec3(unit.x(), unit.y(), unit.z()) : mob.position();
		}
	}
}
