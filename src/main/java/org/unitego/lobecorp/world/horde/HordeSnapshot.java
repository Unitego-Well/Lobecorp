package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record HordeSnapshot(UUID eventId, CompoundTag data) {
	public HordeSnapshot {
		data = data.copy();
	}

	@Override
	public CompoundTag data() {
		return data.copy();
	}
}
