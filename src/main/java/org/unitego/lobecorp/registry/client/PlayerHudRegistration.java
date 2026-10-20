package org.unitego.lobecorp.registry.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.PlayerScreenFilters;
import org.unitego.lobecorp.client.hud.PlayerStatusHud;
import org.unitego.lobecorp.config.client.PlayerHudConfig;

public class PlayerHudRegistration {
	public static void register(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, Lobecorp.id("player_status_hud"), PlayerStatusHud.INSTANCE);
		event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, original -> (graphics, delta) -> {
			var player = Minecraft.getInstance().player;
			if (!PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get() || player == null || player.isSpectator() || player.isCreative()) {
				original.render(graphics, delta);
			}
		});
		event.wrapLayer(VanillaGuiLayers.ARMOR_LEVEL, original -> (graphics, delta) -> {
			var pose = graphics.pose();
			pose.pushMatrix();
			try {
				pose.translate(0, -PlayerStatusHud.INSTANCE.armorOffset());
				original.render(graphics, delta);
			} finally {
				pose.popMatrix();
			}
		});
		event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Lobecorp.id("damage_screen_filter"), PlayerScreenFilters.INSTANCE::extractDamage);
		event.registerAboveAll(Lobecorp.id("rationality_screen_filter"), PlayerScreenFilters.INSTANCE::extractRationality);
	}
}
