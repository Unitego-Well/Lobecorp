package org.unitego.lobecorp.registry.client;

import org.unitego.lobecorp.client.hud.EventHud;
import org.unitego.lobecorp.client.hud.MidnightCleanupHud;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

public class EventHudRegistration {
	public static void register() {
		EventHud.INSTANCE.register(MidnightCleanup.ID, "Midnight Cleanup", "深夜清扫", MidnightCleanupHud::new);
	}
}
