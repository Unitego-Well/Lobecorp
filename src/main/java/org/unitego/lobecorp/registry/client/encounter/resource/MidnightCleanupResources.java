package org.unitego.lobecorp.registry.client.encounter.resource;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupAtmosphere;

public class MidnightCleanupResources {
	private static final Identifier RELOAD_ID = Lobecorp.id("midnight_cleanup_atmosphere");

	public static void registerReload(AddClientReloadListenersEvent event) {
		event.addListener(RELOAD_ID, MidnightCleanupAtmosphere.resourceReloadListener());
		event.addDependency(Identifier.fromNamespaceAndPath("photon", "shader_reload"), RELOAD_ID);
	}
}
