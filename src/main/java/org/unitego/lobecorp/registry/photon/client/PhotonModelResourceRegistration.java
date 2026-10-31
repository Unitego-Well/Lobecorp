package org.unitego.lobecorp.registry.photon.client;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamResourcePreparation;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;

public class PhotonModelResourceRegistration {
	private static final Identifier RELOAD_ID = Lobecorp.id("photon_model_resources");

	public static void registerReload(AddClientReloadListenersEvent event) {
		PhotonOwnedLaser.register();
		QueenBeamResourcePreparation.register();
		event.addListener(RELOAD_ID, PhotonModelSourceUtil.resourceReloadListener());
		var owned = Lobecorp.id("photon_owned_effect_resources");
		event.addListener(owned, new PhotonOwnedEffectResources());
		event.addDependency(RELOAD_ID, owned);
		event.addDependency(Identifier.fromNamespaceAndPath("photon", "fx_cache"), owned);
		event.addDependency(Identifier.fromNamespaceAndPath("photon", "shader_reload"), owned);
		event.addDependency(VanillaClientListeners.TEXTURES, owned);
		event.addDependency(VanillaClientListeners.ATLASES, owned);
		event.addDependency(VanillaClientListeners.SHADERS, owned);
	}
}
