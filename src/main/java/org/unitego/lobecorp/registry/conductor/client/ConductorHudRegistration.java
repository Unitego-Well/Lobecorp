package org.unitego.lobecorp.registry.conductor.client;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.unitego.lobecorp.client.conductor.ConductorHud;

/// 指挥家 HUD 图层注册入口。
public class ConductorHudRegistration {
	public static void register(RegisterGuiLayersEvent event) {
		ConductorHud.INSTANCE.register();
		event.registerAboveAll(ConductorHud.ID, ConductorHud.INSTANCE);
	}
}
