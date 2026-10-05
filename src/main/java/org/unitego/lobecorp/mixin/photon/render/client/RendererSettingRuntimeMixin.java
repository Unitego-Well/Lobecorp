package org.unitego.lobecorp.mixin.photon.render.client;

import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;

@Mixin(RendererSetting.Runtime.class)
public abstract class RendererSettingRuntimeMixin implements PhotonViewDepthSortAccess {
	@Shadow
	@Final
	protected RendererSetting config;

	@Override
	public boolean lobecorp$isViewDepthSortEnabled() {
		return ((PhotonViewDepthSortAccess) this.config).lobecorp$isViewDepthSortEnabled();
	}
}

