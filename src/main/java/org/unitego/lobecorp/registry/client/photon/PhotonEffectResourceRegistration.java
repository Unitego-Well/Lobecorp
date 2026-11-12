package org.unitego.lobecorp.registry.client.photon;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamResourcePreparation;

public class PhotonEffectResourceRegistration {

	public static void registerReload(AddClientReloadListenersEvent event) {
		PhotonOwnedLaser.register();
		QueenBeamResourcePreparation.register();
		var owned = Lobecorp.id("photon_owned_effect_resources");
		event.addListener(owned, new PhotonOwnedEffectResources());
		event.addDependency(Identifier.fromNamespaceAndPath("photon", "fx_cache"), owned);
		event.addDependency(Identifier.fromNamespaceAndPath("photon", "shader_reload"), owned);
		event.addDependency(VanillaClientListeners.TEXTURES, owned);
		event.addDependency(VanillaClientListeners.ATLASES, owned);
		event.addDependency(VanillaClientListeners.SHADERS, owned);
	}
}
