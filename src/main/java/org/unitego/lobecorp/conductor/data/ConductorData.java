package org.unitego.lobecorp.conductor.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.util.conductor.ConductorAttachmentUtil;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.conductor.world.ConductorWorldRuntime;

import java.util.*;
import java.util.function.Consumer;

/// 指挥家世界目录，持久化队伍、成员摘要、待交付指令和修订号。
public class ConductorData extends SavedData {
	public static final Codec<ConductorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Team.CODEC).fieldOf("teams").forGetter(data -> data.teams),
			Codec.unboundedMap(Codec.STRING, Unit.CODEC).fieldOf("members").forGetter(data -> data.units),
			Codec.unboundedMap(Codec.STRING, MemberInfo.CODEC).optionalFieldOf("member_info", Map.of()).forGetter(data -> data.memberInfo),
			Codec.unboundedMap(Codec.STRING, Pending.CODEC).optionalFieldOf("pending", Map.of()).forGetter(data -> data.pending),
			Codec.LONG.optionalFieldOf("revision", 0L).forGetter(data -> data.revision)
	).apply(instance, ConductorData::new));
	private static final SavedDataType<ConductorData> TYPE =
			new SavedDataType<>(Lobecorp.id("conductor_directory"), ConductorData::new, CODEC);
	public final ConductorWorldRuntime runtime = new ConductorWorldRuntime();
	private final Map<String, Team> teams;
	private final Map<String, Unit> units;
	private final Map<String, MemberInfo> memberInfo;
	private final Map<String, Pending> pending;
	private final Map<UUID, Mob> loaded = new HashMap<>();
	private MinecraftServer server;
	private long revision;

	public ConductorData() {
		this(Map.of(), Map.of(), Map.of(), Map.of(), 0L);
	}

	private ConductorData(Map<String, Team> teams, Map<String, Unit> units, Map<String, MemberInfo> memberInfo,
	                      Map<String, Pending> pending, long revision) {
		this.teams = new LinkedHashMap<>(teams);
		this.units = new LinkedHashMap<>(units);
		this.memberInfo = new LinkedHashMap<>(memberInfo);
		this.pending = new LinkedHashMap<>(pending);
		this.revision = revision;
	}

	public static ConductorData get(MinecraftServer server) {
		ConductorData data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
		data.server = server;
		return data;
	}

	public static int defaultColor(String name) {
		float hue = (float) Math.floorMod(name.hashCode(), ConductorRules.COLOR_HUE_STEPS)
				/ ConductorRules.COLOR_HUE_STEPS;
		return Mth.hsvToRgb(hue, ConductorRules.COLOR_SATURATION, ConductorRules.COLOR_BRIGHTNESS)
				& 0xFFFFFF;
	}

	public Map<String, Team> teams() {
		return Map.copyOf(teams);
	}

	public Map<String, Unit> units() {
		return Map.copyOf(units);
	}

	public Team team(String name) {
		return teams.get(name);
	}

	public Unit unit(UUID uuid) {
		Mob mob = loaded.get(uuid);
		if (mob != null && !mob.isRemoved() && ConductorAttachmentUtil.hasUnit(mob)) {
			return ConductorAttachmentUtil.unit(mob).snapshot(mob);
		}
		return units.get(uuid.toString());
	}

	public MemberInfo memberInfo(UUID uuid) {
		return memberInfo.get(uuid.toString());
	}

	public Map<String, MemberInfo> memberInfo() {
		return Map.copyOf(memberInfo);
	}

	public void refreshLocations() {
		for (Mob mob : List.copyOf(loaded.values())) {
			if (!mob.isRemoved() && ConductorAttachmentUtil.hasUnit(mob))
				remember(mob);
		}
	}

	public void remember(Mob mob) {
		MemberInfo next = new MemberInfo(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(),
				mob.getDisplayName().getString(), Position.of(mob.position()));
		if (!next.equals(memberInfo.put(mob.getUUID().toString(), next))) {
			revision++;
			setDirty();
		}
	}

	public void putTeam(String name) {
		teams.putIfAbsent(name, new Team(name, Set.of(), false, defaultColor(name)));
		revision++;
		setDirty();
	}

	public void putTeam(Team team) {
		teams.put(team.name(), team);
		revision++;
		setDirty();
	}

	public boolean join(Mob mob, String team) {
		if (!teams.containsKey(team) || ConductorUtil.get(mob) == null)
			return false;
		ConductorUnitData state = new ConductorUnitData();
		state.team = team;
		state.origin = mob.position();
		state.destination = mob.position();
		assign(mob.getUUID(), state.snapshot(mob));
		if (!ConductorAttachmentUtil.hasUnit(mob))
			return false;
		mob.setPersistenceRequired();
		remember(mob);
		return true;
	}

	public void update(UUID uuid, Consumer<ConductorUnitData> mutation) {
		Unit before = unit(uuid);
		if (before == null)
			return;
		Mob mob = find(uuid);
		ConductorUnitData state;
		if (mob != null) {
			if (!ConductorAttachmentUtil.hasUnit(mob))
				return;
			state = ConductorAttachmentUtil.unit(mob);
		} else {
			state = new ConductorUnitData();
			state.read(before);
		}
		mutation.accept(state);
		if (mob != null) {
			if (!ConductorUtil.accept(mob, state.snapshot(mob))) {
				state.read(before);
				return;
			}
			commit(mob);
		} else
			assign(uuid, state.snapshot(before.dimension(), before.chunkX(), before.chunkZ()));
	}

	private void assign(UUID uuid, Unit unit) {
		Mob mob = find(uuid);
		long next = ++revision;
		if (mob != null) {
			if (!ConductorUtil.accept(mob, unit))
				return;
			ConductorUnitData state = ConductorAttachmentUtil.unit(mob);
			state.revision = next;
			loaded.put(uuid, mob);
			ConductorAttachmentUtil.syncUnit(mob);
			units.put(uuid.toString(), state.snapshot(mob));
			pending.remove(uuid.toString());
		} else {
			units.put(uuid.toString(), unit);
			pending.put(uuid.toString(), new Pending(unit, next, false));
		}
		setDirty();
	}

	public void commit(Mob mob) {
		if (!ConductorAttachmentUtil.hasUnit(mob))
			return;
		ConductorUnitData state = ConductorAttachmentUtil.unit(mob);
		state.revision = ++revision;
		units.put(mob.getUUID().toString(), state.snapshot(mob));
		ConductorAttachmentUtil.syncUnit(mob);
		remember(mob);
		setDirty();
	}

	private Mob find(UUID uuid) {
		Mob cached = loaded.get(uuid);
		if (cached != null && !cached.isRemoved())
			return cached;
		if (server != null)
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(uuid) instanceof Mob mob && !mob.isRemoved())
					return mob;
			}
		return null;
	}

	public void attach(Mob mob) {
		String key = mob.getUUID().toString();
		Pending delivery = pending.get(key);
		if (delivery != null && delivery.released()) {
			ConductorAttachmentUtil.removeUnitAndRuntime(mob);
			pending.remove(key);
			setDirty();
			return;
		}
		if (!ConductorAttachmentUtil.hasUnit(mob))
			return;
		ConductorUnitData state = ConductorAttachmentUtil.unit(mob);
		if (delivery != null) {
			if (delivery.revision() > state.revision) {
				state.read(delivery.value());
				state.revision = delivery.revision();
			}
			pending.remove(key);
		}
		if (!teams.containsKey(state.team)) {
			ConductorAttachmentUtil.removeUnit(mob);
			remove(mob.getUUID());
			return;
		}
		if (state.order == OrderType.ATTACK_POINT || state.commandedSkill != null) {
			state.order = OrderType.NONE;
			state.target = null;
			state.commandedSkill = null;
		}
		if (ConductorUtil.get(mob) == null) {
			ConductorAttachmentUtil.removeUnit(mob);
			remove(mob.getUUID());
			return;
		}
		ConductorAttachmentUtil.removeRuntime(mob);
		if (!ConductorUtil.accept(mob, state.snapshot(mob))) {
			state.command(OrderType.NONE, null, mob.position());
			state.moveResult = MoveResult.CANCELED;
			ConductorUtil.stop(mob);
		}
		loaded.put(mob.getUUID(), mob);
		commit(mob);
	}

	public void detach(Mob mob, boolean permanent) {
		if (ConductorAttachmentUtil.hasUnit(mob))
			commit(mob);
		loaded.remove(mob.getUUID());
		if (permanent) {
			String key = mob.getUUID().toString();
			units.remove(key);
			memberInfo.remove(key);
			pending.remove(key);
			ConductorAttachmentUtil.removeUnit(mob);
			revision++;
			setDirty();
		}
		ConductorAttachmentUtil.removeRuntime(mob);
	}

	public long revision() {
		return revision;
	}

	public void remove(UUID uuid) {
		Unit previous = units.get(uuid.toString());
		Mob mob = find(uuid);
		if (mob != null) {
			ConductorAttachmentUtil.removeUnitAndRuntime(mob);
			pending.remove(uuid.toString());
		} else if (previous != null) {
			pending.put(uuid.toString(), new Pending(previous, ++revision, true));
		}
		loaded.remove(uuid);
		boolean removedUnit = units.remove(uuid.toString()) != null;
		boolean removedInfo = memberInfo.remove(uuid.toString()) != null;
		if (removedUnit || removedInfo) {
			revision++;
			setDirty();
		}
	}

	public boolean allied(UUID first, UUID second) {
		Unit left = unit(first);
		Unit right = unit(second);
		return left != null && right != null && left.team().equals(right.team());
	}

	public boolean enemies(UUID first, UUID second) {
		Unit left = unit(first);
		Unit right = unit(second);
		Team team = left == null ? null : team(left.team());
		return team != null && right != null && team.enemies().contains(right.team());
	}

	public enum BehaviorState {
		PATROL,
		IDLE,
		GUARD,
		/// 空闲时停在当前位置，追击后不返回原驻守点。
		STANDBY;

		private static BehaviorState fromSerialized(String value) {
			return switch (value) {
				case "FULL" -> IDLE;
				case "COMMAND", "SOFT" -> PATROL;
				default -> valueOf(value);
			};
		}
	}

	public enum CombatBehavior {
		ACTIVE, PASSIVE, NEUTRAL
	}

	public enum ControlMode {
		FULL(BehaviorState.IDLE),
		COMMAND(BehaviorState.PATROL),
		SOFT(BehaviorState.GUARD),
		STANDBY(BehaviorState.STANDBY);

		private final BehaviorState behaviorState;

		private ControlMode(BehaviorState behaviorState) {
			this.behaviorState = behaviorState;
		}

		public BehaviorState behaviorState() {
			return behaviorState;
		}
	}

	public enum OrderType {
		NONE,
		MOVE,
		ATTACK,
		ATTACK_POINT,
		RETURN,
		CLEANUP,
		REASSEMBLE;

		private static OrderType fromSerialized(String value) {
			return value.equals("STOP") ? NONE : valueOf(value);
		}
	}

	public enum FormationMode {
		SCATTERED, FORMATION, UNIFORM
	}

	public enum MoveResult {
		NONE, ARRIVED, UNREACHABLE, CANCELED
	}

	public enum AttackState {
		AUTO,
		MANUAL;

		private static AttackState fromSerialized(String value) {
			return switch (value) {
				case "AI" -> AUTO;
				case "BASIC" -> MANUAL;
				default -> valueOf(value);
			};
		}
	}

	public record Team(String name, Set<String> enemies, boolean forceLoad, int color) {
		public static final Codec<Team> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Team::name),
				Codec.STRING.listOf().xmap(Set::copyOf, java.util.List::copyOf).fieldOf("enemies").forGetter(Team::enemies),
				Codec.BOOL.fieldOf("force_load").forGetter(Team::forceLoad),
				Codec.INT.optionalFieldOf("color", -1).forGetter(Team::color)
		).apply(instance, Team::new));

		public Team {
			color = color < 0 ? defaultColor(name) : color & 0xFFFFFF;
		}

		public Team withColor(int nextColor) {
			return new Team(name, enemies, forceLoad, nextColor);
		}
	}

	public record Position(double x, double y, double z) {
		public static final Codec<Position> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("x").forGetter(Position::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Position::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Position::z)
		).apply(instance, Position::new));

		public static Position of(Vec3 position) {
			return new Position(position.x, position.y, position.z);
		}

		public Vec3 vector() {
			return new Vec3(x, y, z);
		}
	}

	public record MemberInfo(String type, String name, Position position) {
		public static final Codec<MemberInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(MemberInfo::type),
				Codec.STRING.fieldOf("name").forGetter(MemberInfo::name),
				Position.CODEC.fieldOf("position").forGetter(MemberInfo::position)
		).apply(instance, MemberInfo::new));
	}

	public record Unit(String dimension, String team, BehaviorState behaviorState, OrderType order,
	                   String target, double x, double y, double z, int chunkX, int chunkZ, AttackState attackState,
	                   String commandedSkill, Position origin, MoveResult moveResult, double movementSpeed,
	                   CombatBehavior combatBehavior) {
		public static final Codec<Unit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("dimension").forGetter(Unit::dimension),
				Codec.STRING.fieldOf("team").forGetter(Unit::team),
				Codec.STRING.xmap(BehaviorState::fromSerialized, BehaviorState::name).fieldOf("mode").forGetter(Unit::behaviorState),
				Codec.STRING.xmap(OrderType::fromSerialized, OrderType::name).fieldOf("order").forGetter(Unit::order),
				Codec.STRING.fieldOf("target").forGetter(Unit::target),
				Codec.DOUBLE.fieldOf("x").forGetter(Unit::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Unit::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Unit::z),
				Codec.INT.fieldOf("chunk_x").forGetter(Unit::chunkX),
				Codec.INT.fieldOf("chunk_z").forGetter(Unit::chunkZ),
				Codec.STRING.xmap(AttackState::fromSerialized, AttackState::name).optionalFieldOf("attack_mode", AttackState.AUTO).forGetter(Unit::attackState),
				Codec.STRING.optionalFieldOf("commanded_skill", "").forGetter(Unit::commandedSkill),
				Position.CODEC.optionalFieldOf("origin").forGetter(unit -> Optional.ofNullable(unit.origin())),
				Codec.STRING.xmap(MoveResult::valueOf, MoveResult::name).optionalFieldOf("move_result", MoveResult.NONE).forGetter(Unit::moveResult),
				Codec.DOUBLE.optionalFieldOf("movement_speed", ConductorRules.NATURAL_MOVEMENT_SPEED).forGetter(Unit::movementSpeed),
				Codec.STRING.xmap(CombatBehavior::valueOf, CombatBehavior::name).optionalFieldOf("combat_behavior")
						.forGetter(unit -> Optional.of(unit.combatBehavior()))
		).apply(instance, Unit::new));

		private Unit(String dimension, String team, BehaviorState behaviorState, OrderType order, String target,
		             double x, double y, double z, int chunkX, int chunkZ, AttackState attackState,
		             String commandedSkill, Optional<Position> origin, MoveResult moveResult, double movementSpeed, Optional<CombatBehavior> combatBehavior) {
			this(dimension, team, behaviorState, order, target, x, y, z, chunkX, chunkZ,
					attackState, commandedSkill, origin.orElse(null), moveResult, movementSpeed,
					combatBehavior.orElse(CombatBehavior.NEUTRAL));
		}

	}

	public record Pending(Unit value, long revision, boolean released) {
		public static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Unit.CODEC.fieldOf("value").forGetter(Pending::value),
				Codec.LONG.fieldOf("revision").forGetter(Pending::revision),
				Codec.BOOL.fieldOf("released").forGetter(Pending::released)
		).apply(instance, Pending::new));
	}
}
