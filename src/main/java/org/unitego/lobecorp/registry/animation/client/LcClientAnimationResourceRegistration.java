package org.unitego.lobecorp.registry.animation.client;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.animation.LcClientAnimationResourceReloadListener;

public class LcClientAnimationResourceRegistration {
	private static final Identifier RELOAD_ID = Lobecorp.id("client_animation_resources");

	public static void registerReload(AddClientReloadListenersEvent event) {
		event.addListener(RELOAD_ID, new LcClientAnimationResourceReloadListener());
		event.addDependency(GeckoLibResources.RELOAD_LISTENER_ID, RELOAD_ID);
	}
}
