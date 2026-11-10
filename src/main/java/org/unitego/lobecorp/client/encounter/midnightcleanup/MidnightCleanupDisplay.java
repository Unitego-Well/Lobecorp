package org.unitego.lobecorp.client.encounter.midnightcleanup;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.encounter.IEncounterDisplay;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.hud.encounter.EncounterHud;
import org.unitego.lobecorp.util.horde.HordeTimingUtil;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;

public class MidnightCleanupDisplay implements IEncounterDisplay {

	private final Set<UUID> started = new HashSet<>();
	private final Set<UUID> closed = new HashSet<>();
	private final Set<UUID> visible = new HashSet<>();
	public static List<HordeClient.ClientHorde> instances() {
		return HordeClient.events().stream().filter(instance -> instance.presentationEnabled
				&& MidnightCleanupEncounter.ID.equals(instance.profile)).toList();
	}

	@Override
	public void receive(UUID instance, CompoundTag body) {
		if (!body.getBooleanOr("opening", true)) {
			closed.add(instance);
			EncounterHud.INSTANCE.hide(instance);
		}
		MidnightCleanupCinematic.receive(instance, body);
	}

	@Override
	public void tick() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			clear();
			return;
		}
		var active = new HashSet<UUID>();
		for (var event : HordeClient.events()) {
			if (!event.ready || event.failed || !event.presentationEnabled || event.tide == null
					|| !MidnightCleanupEncounter.ID.equals(event.profile) || event.speed <= 0) {
				continue;
			}
			active.add(event.id);
			double time = event.time(0);
			double station = event.tide.projectedStation(player.position());
			double remaining = HordeTimingUtil.closingRemaining(event.stop, station, event.speed, time, event.spawnCutoffTicks);
			if (closed.contains(event.id) || remaining < 0) {
				EncounterHud.INSTANCE.hide(event.id);
				continue;
			}
			if (!started.contains(event.id) && station / event.speed - time > MidnightCleanupEncounter.ARRIVAL_WARNING_TICKS) {
				continue;
			}
			started.add(event.id);
			if (EncounterHud.INSTANCE.show(MidnightCleanupEncounter.ID, event.id) instanceof MidnightCleanupHud hud) {
				hud.update(remaining, time);
			}
		}
		for (var id : visible) {
			if (!active.contains(id)) {
				EncounterHud.INSTANCE.hide(id);
				MidnightCleanupAtmosphere.remove(id);
			}
		}
		visible.clear();
		visible.addAll(active);
	}

	@Override
	public void clear() {
		visible.forEach(EncounterHud.INSTANCE::hide);
		visible.clear();
		started.clear();
		closed.clear();
		MidnightCleanupAmbient.clear();
		MidnightCleanupSky.clear();
		MidnightCleanupCinematic.clear();
	}
}
