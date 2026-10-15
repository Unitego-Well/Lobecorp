package org.unitego.lobecorp.registry.particle.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.particle.screen.ScreenParticles;
import org.unitego.lobecorp.client.particle.screen.ScreenTextContent;

public class ScreenParticleRegistration {

	private static final Identifier LAYER_ID = Lobecorp.id("screen_particles");
	private static final Identifier RELOAD_ID = Lobecorp.id("screen_particle_contents");

	public static void register(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.AFTER_CAMERA_DECORATIONS, LAYER_ID, ScreenParticles::extract);
	}

	public static void registerReload(AddClientReloadListenersEvent event) {
		event.addListener(RELOAD_ID, (ResourceManagerReloadListener) _ -> ScreenTextContent.invalidateLayouts());
		event.addDependency(VanillaClientListeners.FONTS, RELOAD_ID);
	}
}
