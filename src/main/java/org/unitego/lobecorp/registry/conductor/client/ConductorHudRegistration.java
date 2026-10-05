package org.unitego.lobecorp.registry.conductor.client;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.ConductorHud;

/// 指挥家 HUD 图层注册入口。
public class ConductorHudRegistration {
	/// 指挥家 HUD 图层的资源 ID。
	private static final Identifier HUD_LAYER_ID = Lobecorp.id("conductor_hud");

	public static void register(RegisterGuiLayersEvent event) {
		event.registerAboveAll(HUD_LAYER_ID, ConductorHud.INSTANCE);
	}
}
