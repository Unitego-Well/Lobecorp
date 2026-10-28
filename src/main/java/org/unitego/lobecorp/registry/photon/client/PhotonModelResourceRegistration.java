package org.unitego.lobecorp.registry.photon.client;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;

public class PhotonModelResourceRegistration {
    private static final Identifier RELOAD_ID = Lobecorp.id("photon_model_resources");

    public static void registerReload(AddClientReloadListenersEvent event) {
        event.addListener(RELOAD_ID, PhotonModelSourceUtil.resourceReloadListener());
    }
}
