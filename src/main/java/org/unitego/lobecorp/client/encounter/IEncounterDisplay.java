package org.unitego.lobecorp.client.encounter;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

@SuppressWarnings("UnnecessaryModifier")
public interface IEncounterDisplay {
	public void receive(UUID instance, CompoundTag body);

	public void tick();

	public void clear();
}
