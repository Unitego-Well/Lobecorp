package org.unitego.lobecorp.world.encounter;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.unitego.lobecorp.world.horde.HordeInstance;
import org.unitego.lobecorp.world.horde.HordeSavedData;
import org.unitego.lobecorp.world.horde.IHordeProfile;

import java.util.UUID;

@SuppressWarnings("UnnecessaryModifier")
public interface IEncounterDefinition {
	public Identifier id();

	public IHordeProfile hordeProfile();

	public void start(MinecraftServer server, HordeInstance instance);

	public void tick(MinecraftServer server, HordeSavedData data);

	public void finish(MinecraftServer server, HordeInstance instance);

	public void skip(ServerPlayer player, UUID instance);

	public void clear(MinecraftServer server);
}
