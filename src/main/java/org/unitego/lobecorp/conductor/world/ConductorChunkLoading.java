package org.unitego.lobecorp.conductor.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;
import org.unitego.lobecorp.registry.ConductorTicketControllers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 指挥家远程单位的区块票据和目录位置刷新。
public class ConductorChunkLoading {
	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		ConductorData data = ConductorData.get(server);
		update(server, data);
		if (server.overworld().getGameTime() % ConductorRules.DIRECTORY_SYNC_INTERVAL_TICKS == 0) {
			data.refreshLocations();
			ConductorSnapshotPayload.sendAll(server, data);
		}
	}

	public static void update(MinecraftServer server, ConductorData data) {
		Map<UUID, Location> active = data.runtime.tickets;
		Map<UUID, Location> wanted = new HashMap<>();
		for (Map.Entry<String, ConductorData.Unit> entry : data.units().entrySet()) {
			UUID uuid = UUID.fromString(entry.getKey());
			ConductorData.Unit unit = entry.getValue();
			ServerLevel level = level(server, unit.dimension());
			if (level == null) {
				continue;
			}
			Entity entity = level.getEntity(uuid);
			if (entity == null) {
				for (ServerLevel candidate : server.getAllLevels()) {
					entity = candidate.getEntity(uuid);
					if (entity != null) {
						break;
					}
				}
			}
			if (entity != null && (!unit.dimension().equals(entity.level().dimension().identifier().toString())
					|| unit.chunkX() != entity.chunkPosition().x() || unit.chunkZ() != entity.chunkPosition().z())) {
				if (entity instanceof Mob mob) data.commit(mob);
				unit = data.unit(uuid);
			}
			ConductorData.Team team = data.team(unit.team());
			if (team != null && team.forceLoad()) {
				wanted.put(uuid, new Location(unit.dimension(), unit.chunkX(), unit.chunkZ()));
			}
		}
		for (Map.Entry<UUID, Location> entry : active.entrySet()) {
			if (!entry.getValue().equals(wanted.get(entry.getKey()))) {
				force(server, entry.getKey(), entry.getValue(), false);
			}
		}
		for (Map.Entry<UUID, Location> entry : wanted.entrySet()) {
			if (!entry.getValue().equals(active.get(entry.getKey()))) {
				force(server, entry.getKey(), entry.getValue(), true);
			}
		}
		active.clear();
		active.putAll(wanted);
	}

	private static ServerLevel level(MinecraftServer server, String dimension) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
	}

	private static void force(MinecraftServer server, UUID uuid, Location location, boolean enable) {
		ServerLevel level = level(server, location.dimension());
		if (level != null) {
			ConductorTicketControllers.forceChunk(level, uuid, location.x(), location.z(), enable);
		}
	}

	protected record Location(String dimension, int x, int z) {
	}
}
