package org.unitego.lobecorp.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.resource.LcAnimationResources;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class AnimationResourceEvents {
	@SubscribeEvent
	public static void onServerReloadListeners(AddServerReloadListenersEvent event) {
		event.addRetainedListener(LcAnimationResources.KEY, new LcAnimationResources());
	}
}
