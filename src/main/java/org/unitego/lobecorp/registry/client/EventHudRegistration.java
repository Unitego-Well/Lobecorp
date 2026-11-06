package org.unitego.lobecorp.registry.client;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.event.EventHud;
import org.unitego.lobecorp.client.hud.event.MidnightCleanupHud;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

public class EventHudRegistration {
	private static final Identifier EVENT_STACK_WRAPPER = Lobecorp.id("event_hud/top_left/events");

	public static void register() {
		EventHud.INSTANCE.registerWrapper(EVENT_STACK_WRAPPER);
		EventHud.INSTANCE.register(MidnightCleanup.ID, "Midnight Cleanup", "深夜清扫", EVENT_STACK_WRAPPER, MidnightCleanupHud::new);
	}
}
