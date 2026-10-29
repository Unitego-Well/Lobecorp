package org.unitego.lobecorp.world.horde;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record HordeStartResult(@Nullable UUID eventId, String message) {
	public boolean started() {
		return eventId != null;
	}
}
