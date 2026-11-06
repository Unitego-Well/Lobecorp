package org.unitego.lobecorp.registry.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.unitego.lobecorp.client.hud.event.EventHud;
import org.unitego.lobecorp.client.hud.TopLeftHud;
import org.unitego.lobecorp.client.hud.filter.DamageFilter;
import org.unitego.lobecorp.client.hud.filter.RationalityFilter;
import org.unitego.lobecorp.client.hud.PlayerStatusHud;
import org.unitego.lobecorp.config.client.HealthBarStyle;
import org.unitego.lobecorp.config.client.PlayerHudConfig;

public class PlayerHudRegistration {
	public static void register(RegisterGuiLayersEvent event) {
		EventHudRegistration.register();
		event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, PlayerStatusHud.ID, PlayerStatusHud.INSTANCE);
		event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, original -> (graphics, delta) -> {
			var player = Minecraft.getInstance().player;
			if (PlayerHudConfig.HEALTH_STYLE.get() == HealthBarStyle.VANILLA
					|| player == null || player.isSpectator() || player.isCreative()) {
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
		event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, DamageFilter.ID,
				DamageFilter.INSTANCE);
		event.registerAbove(DamageFilter.ID, EventHud.ID, TopLeftHud.INSTANCE);
		event.registerAboveAll(RationalityFilter.ID, RationalityFilter.INSTANCE);
	}
}
