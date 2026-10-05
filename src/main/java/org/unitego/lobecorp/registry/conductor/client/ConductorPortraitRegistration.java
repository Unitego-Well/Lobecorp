package org.unitego.lobecorp.registry.conductor.client;

import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.unitego.lobecorp.client.conductor.render.ConductorPortraitCache;

/// 指挥家肖像画中画渲染器注册入口。
public class ConductorPortraitRegistration {
	public static void register(RegisterPictureInPictureRenderersEvent event) {
		event.register(ConductorPortraitCache.State.class, ConductorPortraitCache.Renderer::new);
	}
}
