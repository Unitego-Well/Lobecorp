package org.unitego.lobecorp.client.photon.render;

import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;

public interface PhotonFixedRenderAccess {
	void lobecorp$useFixedRendering();

	boolean lobecorp$isFixedRendering();

	void lobecorp$copyFixedRendering(RendererSetting.Runtime source);
}
