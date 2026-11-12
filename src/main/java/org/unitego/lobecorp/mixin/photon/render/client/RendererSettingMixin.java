package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.render.PhotonFixedRenderAccess;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;
import org.unitego.lobecorp.config.photon.PhotonFixedRenderSettings;
import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;
import org.unitego.lobecorp.registry.translation.PhotonTranslationKeys;

import java.util.Objects;

@Mixin(RendererSetting.class)
@SuppressWarnings("unused")
public abstract class RendererSettingMixin implements PhotonViewDepthSortAccess, PhotonOcclusionAccess, PhotonFixedRenderAccess {
	@Unique
	@Persisted(subPersisted = true)
	private final PhotonOcclusionConfig lobecorp$occlusion = new PhotonOcclusionConfig();
	@Unique
	@Configurable(name = PhotonTranslationKeys.PHOTON_FIXED_RENDER_KEY, subConfigurable = true, persisted = false)
	private final PhotonFixedRenderSettings lobecorp$fixedRenderSettings = new PhotonFixedRenderSettings((RendererSetting) (Object) this);
	@Unique
	@Persisted
	private boolean lobecorp$viewDepthSort;
	@Unique
	@Persisted
	private boolean lobecorp$fixedRendering;

	@Override
	public void lobecorp$useFixedRendering() {
		lobecorp$fixedRendering = true;
		lobecorp$viewDepthSort = true;
		lobecorp$occlusion.setEnable(true);
	}

	@Override
	public void lobecorp$copyFixedRendering(RendererSetting.Runtime source) {
		lobecorp$fixedRendering = ((PhotonFixedRenderAccess) source).lobecorp$isFixedRendering();
		lobecorp$viewDepthSort = ((PhotonViewDepthSortAccess) source).lobecorp$isViewDepthSortEnabled();
		var occlusion = (PhotonOcclusionAccess) source;
		lobecorp$occlusion.setEnable(occlusion.lobecorp$isOcclusionEnabled());
		lobecorp$occlusion.setAlphaCutoff(occlusion.lobecorp$getOcclusionAlphaCutoff());
	}

	@Override
	public boolean lobecorp$isFixedRendering() {
		return lobecorp$fixedRendering;
	}

	@Override
	public boolean lobecorp$isViewDepthSortEnabled() {
		return lobecorp$fixedRendering || this.lobecorp$viewDepthSort;
	}

	@Override
	public boolean lobecorp$isOcclusionEnabled() {
		return lobecorp$fixedRendering || lobecorp$occlusion.isEnable();
	}

	@Override
	public float lobecorp$getOcclusionAlphaCutoff() {
		return lobecorp$occlusion.getAlphaCutoff();
	}

	@Override
	public PhotonOcclusionConfig lobecorp$getOcclusionConfig() {
		return lobecorp$occlusion;
	}

	@ModifyReturnValue(method = "equals", at = @At("RETURN"))
	private boolean lobecorp$includeOcclusionEquality(boolean original, Object o) {
		return original && o instanceof PhotonOcclusionAccess access
				&& lobecorp$isOcclusionEnabled() == access.lobecorp$isOcclusionEnabled()
				&& (!lobecorp$isOcclusionEnabled() || Float.compare(lobecorp$getOcclusionAlphaCutoff(), access.lobecorp$getOcclusionAlphaCutoff()) == 0)
				&& o instanceof PhotonViewDepthSortAccess depth && lobecorp$isViewDepthSortEnabled() == depth.lobecorp$isViewDepthSortEnabled();
	}

	@ModifyReturnValue(method = "hashCode", at = @At("RETURN"))
	private int lobecorp$includeOcclusionHash(int original) {
		if (!lobecorp$isOcclusionEnabled() && !lobecorp$isViewDepthSortEnabled()) {
			return original;
		}

		return Objects.hash(original, lobecorp$isViewDepthSortEnabled(), lobecorp$isOcclusionEnabled(),
				lobecorp$isOcclusionEnabled() ? lobecorp$getOcclusionAlphaCutoff() : 0f);
	}
}
