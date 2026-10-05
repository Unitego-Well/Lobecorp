package org.unitego.lobecorp.mixin.photon.render.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;
import org.unitego.lobecorp.util.TranslationKeys;

@Mixin(RendererSetting.class)
@SuppressWarnings("unused")
public abstract class RendererSettingMixin implements PhotonViewDepthSortAccess {
	@Unique
	@Configurable(name = TranslationKeys.PHOTON_VIEW_DEPTH_SORT_KEY, tips = TranslationKeys.PHOTON_VIEW_DEPTH_SORT_TIPS_KEY)
	private boolean lobecorp$viewDepthSort;

	@Override
	public boolean lobecorp$isViewDepthSortEnabled() {
		return this.lobecorp$viewDepthSort;
	}
}

