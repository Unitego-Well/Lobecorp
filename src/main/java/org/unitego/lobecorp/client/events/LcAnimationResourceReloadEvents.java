package org.unitego.lobecorp.client.events;

import com.geckolib.cache.GeckoLibResources;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimationController;

import static org.unitego.lobecorp.Lobecorp.id;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public final class LcAnimationResourceReloadEvents {
	private static final String RELOAD_LISTENER_PATH = "animation_state";

	private LcAnimationResourceReloadEvents() {
	}

	@SubscribeEvent
	public static void addReloadListener(AddClientReloadListenersEvent event) {
		var listenerId = id(RELOAD_LISTENER_PATH);
		event.addListener(listenerId,
				(ResourceManagerReloadListener)resourceManager -> LcAnimationController.onAnimationResourcesReloaded());
		event.addDependency(GeckoLibResources.RELOAD_LISTENER_ID, listenerId);
	}
}
