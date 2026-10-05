package org.unitego.lobecorp.network.ts;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.ability.ConductorTargeting;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.control.ConductorMovement;
import org.unitego.lobecorp.conductor.control.ConductorPointAttack;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.conductor.world.ConductorChunkLoading;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;

import java.util.*;

/// 客户端到服务端的指挥家命令载荷及服务器校验入口。
public record ConductorCommandPayload(Action action, String team, String otherTeam,
                                      List<UUID> units, UUID target, double x, double y, double z,
                                      ConductorData.BehaviorState mode, boolean flag, String skill, int color,
                                      TargetSelection targetSelection, ConductorData.FormationMode formationMode,
                                      ConductorData.CombatBehavior combatBehavior) implements ToServerPayload {
	public static final Type<ConductorCommandPayload> TYPE = Lobecorp.type("conductor_command");
	public static final StreamCodec<RegistryFriendlyByteBuf, ConductorCommandPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull ConductorCommandPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			Action action = Action.values()[buffer.readVarInt()];
			String team = buffer.readUtf();
			String otherTeam = buffer.readUtf();
			int count = buffer.readVarInt();
			if (count < 0 || count > MAX_UNITS) {
				throw new IllegalArgumentException("Invalid conductor unit count: " + count);
			}
			List<UUID> units = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				units.add(buffer.readUUID());
			}
			UUID target = buffer.readUUID();
			double x = buffer.readDouble();
			double y = buffer.readDouble();
			double z = buffer.readDouble();
			ConductorData.BehaviorState mode = ConductorData.BehaviorState.values()[buffer.readVarInt()];
			boolean flag = buffer.readBoolean();
			String skill = buffer.readUtf();
			int color = buffer.readInt();
			TargetSelection targetSelection = TargetSelection.values()[buffer.readVarInt()];
			return new ConductorCommandPayload(action, team, otherTeam, units, target, x, y, z,
					mode, flag, skill, color, targetSelection, buffer.readEnum(ConductorData.FormationMode.class),
					buffer.readEnum(ConductorData.CombatBehavior.class));
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull ConductorCommandPayload payload) {
			buffer.writeVarInt(payload.action.ordinal());
			buffer.writeUtf(payload.team);
			buffer.writeUtf(payload.otherTeam);
			buffer.writeVarInt(payload.units.size());
			for (UUID uuid : payload.units) {
				buffer.writeUUID(uuid);
			}
			buffer.writeUUID(payload.target);
			buffer.writeDouble(payload.x);
			buffer.writeDouble(payload.y);
			buffer.writeDouble(payload.z);
			buffer.writeVarInt(payload.mode.ordinal());
			buffer.writeBoolean(payload.flag);
			buffer.writeUtf(payload.skill);
			buffer.writeInt(payload.color);
			buffer.writeVarInt(payload.targetSelection.ordinal());
			buffer.writeEnum(payload.formationMode);
			buffer.writeEnum(payload.combatBehavior);
		}
	};
	private static final int MAX_UNITS = 1024;

	public static ConductorCommandPayload of(Action action, String team, String otherTeam,
	                                         List<UUID> units, UUID target, double x, double y, double z,
	                                         ConductorData.BehaviorState mode, boolean flag, String skill) {
		return new ConductorCommandPayload(action, team, otherTeam, List.copyOf(units), target,
				x, y, z, mode, flag, skill, 0, TargetSelection.NONE, ConductorData.FormationMode.FORMATION, ConductorData.CombatBehavior.PASSIVE);
	}

	public static ConductorCommandPayload of(Action action, String team, String otherTeam,
	                                         List<UUID> units, UUID target, double x, double y, double z,
	                                         ConductorData.ControlMode mode, boolean flag, String skill) {
		return of(action, team, otherTeam, units, target, x, y, z, mode.behaviorState(), flag, skill);
	}

	public static ConductorCommandPayload of(Action action, String team, String otherTeam,
	                                         List<UUID> units, UUID target, double x, double y, double z,
	                                         ConductorData.BehaviorState mode, boolean flag, String skill,
	                                         TargetSelection targetSelection) {
		return new ConductorCommandPayload(action, team, otherTeam, List.copyOf(units), target,
				x, y, z, mode, flag, skill, 0, targetSelection, ConductorData.FormationMode.FORMATION, ConductorData.CombatBehavior.PASSIVE);
	}

	public static ConductorCommandPayload of(Action action, String team, String otherTeam,
	                                         List<UUID> units, UUID target, double x, double y, double z,
	                                         ConductorData.ControlMode mode, boolean flag, String skill,
	                                         TargetSelection targetSelection) {
		return of(action, team, otherTeam, units, target, x, y, z, mode.behaviorState(), flag, skill, targetSelection);
	}

	public static ConductorCommandPayload color(String team, int color) {
		return new ConductorCommandPayload(Action.SET_COLOR, team, "", List.of(), new UUID(0L, 0L),
				0.0D, 0.0D, 0.0D, ConductorData.BehaviorState.IDLE, false, "", color, TargetSelection.NONE, ConductorData.FormationMode.FORMATION, ConductorData.CombatBehavior.PASSIVE);
	}

	@Override
	public void work(IPayloadContext context, ServerPlayer player) {
		apply(player);
	}

	private static void prepareSkill(Mob mob, ConductorData data) {
		ConductorController.prepareForSkillCast(mob);
		data.update(mob.getUUID(), state -> state.command(ConductorData.OrderType.NONE, null, mob.position()));
	}

	private static ConductorAbility conductorAbility(Mob mob, String id) {
		try {
			return ConductorUtil.ability(mob, Identifier.parse(id));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	public ConductorCommandPayload withFormationMode(ConductorData.FormationMode nextMode) {
		return new ConductorCommandPayload(action, team, otherTeam, units, target, x, y, z,
				mode, flag, skill, color, targetSelection, nextMode, combatBehavior);
	}

	public ConductorCommandPayload withCombatBehavior(ConductorData.CombatBehavior nextBehavior) {
		return new ConductorCommandPayload(action, team, otherTeam, units, target, x, y, z,
				mode, flag, skill, color, targetSelection, formationMode, nextBehavior);
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void apply(ServerPlayer player) {
		ConductorData data = ConductorData.get(player.level().getServer());
		if (action == Action.SNAPSHOT) {
			ConductorSnapshotPayload.send(player, data, units);
			return;
		}
		if (team.isBlank()) {
			return;
		}
		switch (action) {
			case CREATE_TEAM -> data.putTeam(team);
			case ASSIGN -> {
				if (data.team(team) == null) {
					return;
				}
				for (UUID uuid : units) {
					Entity entity = player.level().getEntity(uuid);
					if (entity instanceof Mob mob) {
						data.join(mob, team);
					}
				}
			}
			case RELEASE -> {
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit != null) {
						ServerLevel level = player.level().getServer().getLevel(
								ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
						if (level != null && level.getEntity(uuid) instanceof Mob mob) {
							ConductorController.stop(mob);
						}
					}
					data.remove(uuid);
				}
			}
			case SET_COMBAT_BEHAVIOR -> {
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit != null) {
						data.update(uuid, state -> state.behavior = combatBehavior);
						ServerLevel level = player.level().getServer().getLevel(
								ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
						if (level != null && level.getEntity(uuid) instanceof Mob mob)
							ConductorController.resetSharedTarget(mob);
					}
				}
			}
			case SET_MODE -> {
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit != null) {
						ServerLevel level = player.level().getServer().getLevel(
								ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
						Entity entity = level == null ? null : level.getEntity(uuid);
						ConductorData.Position origin = entity instanceof Mob mob
								? ConductorData.Position.of(mob.position()) : unit.origin();
						data.update(uuid, state -> {
							state.activity = mode == ConductorData.BehaviorState.IDLE ? ConductorData.BehaviorState.GUARD : mode;
							state.origin = origin == null ? null : origin.vector();
						});
					}
				}
			}
			case SET_ATTACK_MODE -> {
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit != null) {
						data.update(uuid, state -> state.attack = flag
								? ConductorData.AttackState.MANUAL : ConductorData.AttackState.AUTO);
					}
				}
			}
			case MOVE -> issueFormation(player, data);
			case ATTACK, ATTACK_POINT, STOP -> {
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit == null) {
						continue;
					}
					if (action == Action.ATTACK) {
						ServerLevel level = player.level().getServer().getLevel(
								ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
						Entity selectedTarget = level == null || target == null ? null : level.getEntity(target);
						if (selectedTarget instanceof LivingEntity living && living.isAlive()
								&& !uuid.equals(living.getUUID()) && !data.allied(uuid, living.getUUID())) {
							if (level.getEntity(uuid) instanceof Mob mob)
								ConductorController.stop(mob);
							data.update(uuid, state -> state.command(ConductorData.OrderType.ATTACK, living.getUUID(), new Vec3(x, y, z)));
						}
					} else if (action == Action.ATTACK_POINT) {
						ServerLevel level = player.level().getServer().getLevel(
								ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
						if (level != null && level.getEntity(uuid) instanceof Mob mob
								&& ConductorPointAttack.supports(mob)) {
							ConductorController.stop(mob);
							data.update(uuid, state -> state.command(ConductorData.OrderType.ATTACK_POINT, null, new Vec3(x, y, z)));
						}
					} else {
						data.update(uuid, state -> {
							state.command(ConductorData.OrderType.NONE, null, state.destination);
							state.moveResult = unit.order() == ConductorData.OrderType.MOVE || unit.order() == ConductorData.OrderType.RETURN
									? ConductorData.MoveResult.CANCELED : ConductorData.MoveResult.NONE;
						});
						ServerLevel level = player.level().getServer().getLevel(
								ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
						if (level != null && level.getEntity(uuid) instanceof Mob mob) {
							ConductorController.stop(mob);
						}
					}
				}
			}
			case HOSTILITY -> {
				ConductorData.Team first = data.team(team);
				ConductorData.Team second = data.team(otherTeam);
				if (first == null || second == null || team.equals(otherTeam)) {
					return;
				}
				Set<String> firstEnemies = new HashSet<>(first.enemies());
				Set<String> secondEnemies = new HashSet<>(second.enemies());
				if (flag) {
					firstEnemies.add(otherTeam);
					secondEnemies.add(team);
				} else {
					firstEnemies.remove(otherTeam);
					secondEnemies.remove(team);
				}
				data.putTeam(new ConductorData.Team(team, Set.copyOf(firstEnemies), first.forceLoad(), first.color()));
				data.putTeam(new ConductorData.Team(otherTeam, Set.copyOf(secondEnemies), second.forceLoad(), second.color()));
			}
			case FORCE_LOAD -> {
				ConductorData.Team current = data.team(team);
				if (current != null) {
					data.putTeam(new ConductorData.Team(team, current.enemies(), flag, current.color()));
					ConductorChunkLoading.update(player.level().getServer(), data);
				}
			}
			case CAST -> {
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit == null) {
						continue;
					}
					ServerLevel level = player.level().getServer().getLevel(
							ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
					if (level == null || !(level.getEntity(uuid) instanceof Mob mob) || !mob.isAlive()) {
						ConductorData.MemberInfo info = data.memberInfo(uuid);
						if ((level == null || level.getEntity(uuid) == null) && targetSelection == TargetSelection.NONE
								&& info != null && info.type().equals(OrdealEntityTypes.SWEEPER.getId().toString())) {
							ConductorData.OrderType work = skill.equals(SweeperSkills.SWEEP.getId().toString())
									? ConductorData.OrderType.CLEANUP : skill.equals(SweeperSkills.REASSEMBLE.getId().toString())
									? ConductorData.OrderType.REASSEMBLE : null;
							if (work != null)
								data.update(uuid, state -> state.command(work, null, info.position().vector()));
						}
						continue;
					}
					Entity selectedTarget = target == null ? null : level.getEntity(target);
					if (ConductorUtil.get(mob) == null)
						continue;
					ConductorAbility ability = conductorAbility(mob, skill);
					if (ability != null) {
						if (!ability.isAvailable(mob)
								|| ability.cooldownTicks(mob) > 0
								|| ability instanceof EntitySkillConductorAbility entitySkill && !entitySkill.canBeginCast(mob)) {
							continue;
						}
						if ((ability.targetKind() == ConductorTargeting.TargetKind.ENTITY
								|| ability.targetKind() == ConductorTargeting.TargetKind.EITHER)
								&& targetSelection == TargetSelection.ENTITY) {
							if (!(selectedTarget instanceof LivingEntity living) || !living.isAlive()
									|| uuid.equals(living.getUUID()) || data.allied(uuid, living.getUUID())) {
								continue;
							}
							Vec3 position = ability.targetPosition(mob, living, new Vec3(x, y, z));
							if (!ability.canTarget(mob, living) || !ability.isWithinRange(mob, position)) {
								if (ability.targetKind() != ConductorTargeting.TargetKind.EITHER)
									continue;
								Vec3 corrected = ability.correctPosition(mob, position);
								if (ability.canTargetPosition(mob, corrected)) {
									prepareSkill(mob, data);
									ConductorUtil.cast(mob, ability.id(), null, corrected);
								}
							} else {
								prepareSkill(mob, data);
								ConductorUtil.cast(mob, ability.id(), living, position);
							}
						} else if ((ability.targetKind() == ConductorTargeting.TargetKind.POSITION
								|| ability.targetKind() == ConductorTargeting.TargetKind.EITHER)
								&& targetSelection == TargetSelection.POSITION) {
							Vec3 requested = flag && ability.directional() ? ability.facingPosition(mob) : new Vec3(x, y, z);
							Vec3 position = ability.correctPosition(mob, requested);
							if (ability.canTargetPosition(mob, position)) {
								prepareSkill(mob, data);
								ConductorUtil.cast(mob, ability.id(), null, position);
							}
						} else if (ability.targetKind() == ConductorTargeting.TargetKind.SELF
								&& targetSelection == TargetSelection.NONE) {
							prepareSkill(mob, data);
							ConductorUtil.cast(mob, ability.id(), null, null);
						}
						continue;
					}
				}
			}
			case AIM, END_MANUAL_CONTROL -> {
				if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
					return;
				for (UUID uuid : units) {
					ConductorData.Unit unit = data.unit(uuid);
					if (unit == null || !team.equals(unit.team()))
						continue;
					ServerLevel level = player.level().getServer().getLevel(
							ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
					if (level == null || !(level.getEntity(uuid) instanceof Mob mob) || !mob.isAlive()
							|| !(conductorAbility(mob, skill) instanceof EntitySkillConductorAbility.Laser laser))
						continue;
					if (action == Action.END_MANUAL_CONTROL) {
						laser.releaseControl(mob);
						continue;
					}
					Entity selectedTarget = targetSelection == TargetSelection.ENTITY ? level.getEntity(target) : null;
					LivingEntity living = selectedTarget instanceof LivingEntity candidate && candidate.isAlive()
							&& !data.allied(uuid, candidate.getUUID()) && laser.canTarget(mob, candidate)
							&& laser.isWithinRange(mob, candidate.position()) ? candidate : null;
					laser.updateAim(mob, living, new Vec3(x, y, z), flag);
				}
				return;
			}
			case SET_COLOR -> {
				ConductorData.Team current = data.team(team);
				if (current != null) {
					data.putTeam(current.withColor(color));
				}
			}
		}
		ConductorSnapshotPayload.sendAll(player.level().getServer(), data);
	}

	private void issueFormation(ServerPlayer player, ConductorData data) {
		Map<String, List<FormationUnit>> byDimension = new LinkedHashMap<>();
		for (UUID uuid : units) {
			ConductorData.Unit unit = data.unit(uuid);
			if (unit == null) {
				continue;
			}
			ServerLevel level = player.level().getServer().getLevel(
					ResourceKey.create(Registries.DIMENSION, Identifier.parse(unit.dimension())));
			Entity entity = level == null ? null : level.getEntity(uuid);
			Mob mob = entity instanceof Mob value ? value : null;
			if (mob != null && !mob.isAlive())
				continue;
			byDimension.computeIfAbsent(unit.dimension(), ignored -> new ArrayList<>())
					.add(new FormationUnit(uuid, unit, mob));
		}
		Vec3 destination = new Vec3(x, y, z);
		for (List<FormationUnit> group : byDimension.values()) {
			if (group.isEmpty()) {
				continue;
			}
			double startX = group.stream().filter(entry -> entry.mob() != null)
					.mapToDouble(entry -> entry.mob().getX()).average().orElse(x);
			double startZ = group.stream().filter(entry -> entry.mob() != null)
					.mapToDouble(entry -> entry.mob().getZ()).average().orElse(z);
			Vec3 forward = new Vec3(x - startX, 0.0D, z - startZ);
			if (forward.lengthSqr() < ConductorRules.FORMATION_DIRECTION_EPSILON) {
				forward = new Vec3(0.0D, 0.0D, 1.0D);
			} else {
				forward = forward.normalize();
			}
			Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
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
				if (nearest.mob() != null)
					occupied.add(nearest.mob().getBoundingBox()
							.move(resolved.subtract(nearest.mob().position())).inflate(ConductorRules.ENTITY_AVOIDANCE_MARGIN));
				Vec3 slot = resolved;
				data.update(nearest.uuid(), state -> {
					state.command(ConductorData.OrderType.MOVE, null, slot);
					state.movementSpeed = movementSpeed;
				});
			}
		}
	}

	public enum TargetSelection {
		NONE,
		ENTITY,
		POSITION
	}

	public enum Action {
		SNAPSHOT,
		CREATE_TEAM,
		ASSIGN,
		RELEASE,
		SET_MODE,
		MOVE,
		ATTACK,
		HOSTILITY,
		FORCE_LOAD,
		CAST,
		SET_COLOR,
		SET_ATTACK_MODE,
		STOP,
		ATTACK_POINT,
		SET_COMBAT_BEHAVIOR,
		/// 更新当前手动持续技能的瞄准，不重新发起施放。
		AIM,
		END_MANUAL_CONTROL
	}

	private record FormationUnit(UUID uuid, ConductorData.Unit unit, Mob mob) {
		private Vec3 position() {
			return mob == null ? new Vec3(unit.x(), unit.y(), unit.z()) : mob.position();
		}
	}
}
