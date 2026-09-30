package org.unitego.lobecorp.client.conductor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.data.ConductorDirectory;
import org.unitego.lobecorp.util.ConductorAttachmentUtil;
import org.unitego.lobecorp.util.ConductorUtil;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConductorClient {
	private static final UUID NO_TARGET = new UUID(0L, 0L);
	private static final int ABILITY_SNAPSHOT_RETRY_TICKS = 100;
	private static Session state = new Session();

	public static void receive(ConductorSnapshotPayload payload) {
		if (!payload.full() && (state.awaitingSnapshot || payload.baseRevision() != state.directoryRevision)) {
			if (!state.awaitingSnapshot) {
				state.awaitingSnapshot = true;
				requestSnapshot();
			}
			return;
		}
		ConductorDirectory next = payload.full() ? payload.data()
				: state.snapshot.merge(payload.data(), payload.removedTeams(), payload.removedUnits());
		boolean rosterChanged = !state.snapshot.equals(next);
		state.snapshot = next;
		state.directoryRevision = payload.revision();
		state.awaitingSnapshot = false;
		Map<AbilityCooldownKey, Integer> nextCooldowns = new HashMap<>();
		Map<AbilityCooldownKey, Integer> nextTotals = new HashMap<>();
		for (ConductorSnapshotPayload.AbilityCooldown cooldown : payload.abilityCooldowns()) {
			nextCooldowns.put(new AbilityCooldownKey(cooldown.unit(), cooldown.ability()), cooldown.ticks());
			nextTotals.put(new AbilityCooldownKey(cooldown.unit(), cooldown.ability()), cooldown.totalTicks());
		}
		state.abilityCooldowns = Map.copyOf(nextCooldowns);
		state.abilityCooldownTotals = Map.copyOf(nextTotals);
		Map<UUID, List<Identifier>> nextAbilities = new HashMap<>();
		for (ConductorSnapshotPayload.UnitAbilities abilities : payload.unitAbilities()) {
			nextAbilities.put(abilities.unit(), List.copyOf(abilities.abilities()));
		}
		boolean abilitiesChanged = !state.unitAbilities.equals(nextAbilities);
		state.unitAbilities = Map.copyOf(nextAbilities);
		for (UUID unit : nextAbilities.keySet()) state.abilitySnapshotRequests.remove(unit);
		state.cooldownSnapshotTick = Minecraft.getInstance().player == null
				? 0 : Minecraft.getInstance().player.tickCount;
		if (rosterChanged || abilitiesChanged) state.revision++;
	}

	public static int abilityCooldownTicks(UUID unit, Identifier ability) {
		var level = Minecraft.getInstance().level;
		if (level != null && level.getEntity(unit) instanceof Mob mob
				&& ConductorUtil.ability(mob, ability) instanceof EntitySkillConductorAbility local) {
			return local.cooldownTicks(mob);
		}
		int initial = state.abilityCooldowns.getOrDefault(new AbilityCooldownKey(unit, ability), 0);
		if (Minecraft.getInstance().player == null) {
			return initial;
		}
		return Math.max(0, initial - (Minecraft.getInstance().player.tickCount - state.cooldownSnapshotTick));
	}

	public static int abilityCooldownTotalTicks(UUID unit, Identifier ability) {
		var level = Minecraft.getInstance().level;
		if (level != null && level.getEntity(unit) instanceof Mob mob
				&& ConductorUtil.ability(mob, ability) instanceof EntitySkillConductorAbility local) {
			return local.totalCooldownTicks(mob);
		}
		return state.abilityCooldownTotals.getOrDefault(new AbilityCooldownKey(unit, ability), 0);
	}

	public static boolean hasAbility(UUID unit, Identifier ability) {
		var level = Minecraft.getInstance().level;
		if (level != null && level.getEntity(unit) instanceof Mob mob) {
			return ConductorUtil.ability(mob, ability) != null;
		}
		return state.unitAbilities.getOrDefault(unit, List.of()).contains(ability);
	}

	public static void requestAbilitiesIfMissing(UUID unit) {
		if (state.unitAbilities.containsKey(unit) || Minecraft.getInstance().player == null) return;
		int tick = Minecraft.getInstance().player.tickCount;
		Integer previous = state.abilitySnapshotRequests.get(unit);
		if (previous != null && tick >= previous && tick - previous < ABILITY_SNAPSHOT_RETRY_TICKS) return;
		state.abilitySnapshotRequests.put(unit, tick);
		send(ConductorCommandPayload.Action.SNAPSHOT, "", "", List.of(unit), null,
				0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, "");
	}

	public static ConductorData.Unit unit(UUID uuid) {
		ConductorData.Unit member = state.snapshot.unit(uuid);
		if (member == null) return null;
		var level = Minecraft.getInstance().level;
		if (level != null && level.getEntity(uuid) instanceof Mob mob && ConductorAttachmentUtil.hasUnit(mob)) {
			var attachment = ConductorAttachmentUtil.unit(mob);
			if (attachment.revision >= state.directoryRevision) return attachment.snapshot(mob);
		}
		return member;
	}

	public static ConductorDirectory snapshot() {
		return state.snapshot;
	}

	public static int revision() {
		return state.revision;
	}

	public static void send(ConductorCommandPayload.Action action, String team, String otherTeam,
	                        List<UUID> units, UUID target, double x, double y, double z,
	                        ConductorData.ControlMode mode, boolean flag, String skill) {
		ConductorCommandPayload.TargetSelection targetSelection = action == ConductorCommandPayload.Action.CAST
				&& target != null ? ConductorCommandPayload.TargetSelection.ENTITY
				: ConductorCommandPayload.TargetSelection.NONE;
		sendTarget(action, team, otherTeam, units, target, x, y, z, mode, flag, skill, targetSelection);
	}

	public static void sendTarget(ConductorCommandPayload.Action action, String team, String otherTeam,
	                              List<UUID> units, UUID target, double x, double y, double z,
	                              ConductorData.ControlMode mode, boolean flag, String skill,
	                              ConductorCommandPayload.TargetSelection targetSelection) {
		ClientPacketDistributor.sendToServer(ConductorCommandPayload.of(action, team, otherTeam,
						units, target == null ? NO_TARGET : target, x, y, z, mode, flag, skill, targetSelection)
				.withFormationMode(ConductorHud.INSTANCE.formationMode()));
	}

	public static void setCombatBehavior(String team, List<UUID> units, ConductorData.CombatBehavior behavior) {
		ClientPacketDistributor.sendToServer(ConductorCommandPayload.of(ConductorCommandPayload.Action.SET_COMBAT_BEHAVIOR,
						team, "", units, NO_TARGET, 0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, "")
				.withCombatBehavior(behavior));
	}

	public static void reset() {
		state = new Session();
	}

	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && minecraft.player != null && state.level != minecraft.level) {
			state.level = minecraft.level;
			requestSnapshot();
		}
	}

	public static void requestSnapshot() {
		send(ConductorCommandPayload.Action.SNAPSHOT, "", "", List.of(), null,
				0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, "");
	}

	public static void setColor(String team, int color) {
		ClientPacketDistributor.sendToServer(ConductorCommandPayload.color(team, color));
	}

	private record AbilityCooldownKey(UUID unit, Identifier ability) {
	}

	private static class Session {
		private final Map<UUID, Integer> abilitySnapshotRequests = new HashMap<>();
		private ConductorDirectory snapshot = ConductorDirectory.empty();
		private Map<AbilityCooldownKey, Integer> abilityCooldowns = Map.of();
		private Map<AbilityCooldownKey, Integer> abilityCooldownTotals = Map.of();
		private Map<UUID, List<Identifier>> unitAbilities = Map.of();
		private int cooldownSnapshotTick;
		private int revision;
		private long directoryRevision;
		private boolean awaitingSnapshot;

		private ClientLevel level;
	}
}
