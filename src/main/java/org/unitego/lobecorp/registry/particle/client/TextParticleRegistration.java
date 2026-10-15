package org.unitego.lobecorp.registry.particle.client;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.particle.text.TextParticleClient;
import org.unitego.lobecorp.client.particle.text.TextParticleGroup;

public class TextParticleRegistration {

	public static final ParticleRenderType TYPE = new ParticleRenderType(Lobecorp.id("text").toString());
	private static final Identifier RELOAD_ID = Lobecorp.id("text_particle_layouts");

	public static void register(RegisterParticleGroupsEvent event) {
		event.register(TYPE, TextParticleGroup::new);
	}

	public static void registerReload(AddClientReloadListenersEvent event) {
		event.addListener(RELOAD_ID, (ResourceManagerReloadListener) _ -> TextParticleClient.invalidateLayouts());
		event.addDependency(VanillaClientListeners.FONTS, RELOAD_ID);
		event.addDependency(VanillaClientListeners.PARTICLE_RESOURCES, RELOAD_ID);
	}
}
