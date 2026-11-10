package org.unitego.lobecorp.world.encounter;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.unitego.lobecorp.registry.encounter.EncounterDefinitions;
import org.unitego.lobecorp.world.horde.HordeInstance;
import org.unitego.lobecorp.world.horde.HordeSavedData;
import org.unitego.lobecorp.world.horde.HordeStartRequest;
import org.unitego.lobecorp.world.horde.HordeStartResult;
import org.unitego.lobecorp.world.horde.HordeStopMode;
import org.unitego.lobecorp.world.horde.HordeStopResult;
import org.unitego.lobecorp.world.horde.Hordes;

import java.util.List;
import java.util.UUID;

/// 遭遇负责玩法编排，路线、成员和同步继续由Horde持有。
@SuppressWarnings("resource")
public class Encounters {
	public static HordeStartResult start(ServerLevel level, Identifier definition, HordeStartRequest request) {
		var type = EncounterDefinitions.get(definition);
		if (type == null) {
			return new HordeStartResult(null, "未知遭遇定义");
		}
		var result = Hordes.start(level, type.hordeProfile().id(), request);
		if (result.started()) {
			var instance = HordeSavedData.get(level.getServer()).events().get(result.eventId());
			instance.presentationEnabled(true);
			type.start(level.getServer(), instance);
			HordeSavedData.get(level.getServer()).setDirty();
		}
		return result;
	}

	public static List<HordeInstance> active(MinecraftServer server) {
		return HordeSavedData.get(server).events().values().stream().filter(HordeInstance::presentationEnabled).toList();
	}

	public static void tick(MinecraftServer server) {
		var data = HordeSavedData.get(server);
		EncounterDefinitions.values().forEach(definition -> definition.tick(server, data));
	}

	public static void finish(MinecraftServer server, HordeInstance instance) {
		var definition = EncounterDefinitions.get(instance.profile());
		if (instance.presentationEnabled() && definition != null) {
			definition.finish(server, instance);
		}
	}

	public static HordeStopResult stop(MinecraftServer server, UUID id, HordeStopMode mode) {
		if (active(server).stream().noneMatch(instance -> instance.id().equals(id))) {
			return new HordeStopResult(false, "遭遇不存在");
		}
		return Hordes.stop(server, id, mode);
	}

	public static void skip(ServerPlayer player, UUID id) {
		var instance = HordeSavedData.get(player.level().getServer()).events().get(id);
		var definition = instance == null ? null : EncounterDefinitions.get(instance.profile());
		if (instance != null && instance.presentationEnabled() && definition != null) {
			definition.skip(player, id);
		}
	}

	public static void clear(MinecraftServer server) {
		EncounterDefinitions.values().forEach(definition -> definition.clear(server));
	}
}
