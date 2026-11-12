package org.unitego.lobecorp.api.encounter;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.horde.Hordes;
import org.unitego.lobecorp.registry.LcRegistries;
import org.unitego.lobecorp.world.encounter.IEncounterDefinition;
import org.unitego.lobecorp.world.horde.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/// 遭遇负责玩法编排，路线、成员和同步继续由Horde持有。
@SuppressWarnings("resource")
public class Encounters {
	public static HordeStartResult start(ServerLevel level, Identifier definition, HordeStartRequest request) {
		var type = get(definition);
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
		values().forEach(definition -> definition.tick(server, data));
	}

	public static void finish(MinecraftServer server, HordeInstance instance) {
		var definition = get(instance.profile());
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
		var definition = instance == null ? null : get(instance.profile());
		if (instance != null && instance.presentationEnabled() && definition != null) {
			definition.skip(player, id);
		}
	}

	public static void clear(MinecraftServer server) {
		values().forEach(definition -> definition.clear(server));
	}

	public static @Nullable IEncounterDefinition get(Identifier id) {
		var definition = LcRegistries.ENCOUNTER.getValue(id);
		return definition == null ? null : definition.get();
	}

	public static Collection<IEncounterDefinition> values() {
		return LcRegistries.ENCOUNTER.stream().map(definition -> (IEncounterDefinition) definition.get()).toList();
	}
}
