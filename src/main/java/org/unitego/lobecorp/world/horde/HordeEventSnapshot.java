package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record HordeEventSnapshot(UUID eventId, CompoundTag data) {
	public HordeEventSnapshot {
		data = data.copy();
	}

	@Override
	public CompoundTag data() {
		return data.copy();
	}
}
