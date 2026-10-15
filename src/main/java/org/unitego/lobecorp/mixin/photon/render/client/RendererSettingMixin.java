package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;
import org.unitego.lobecorp.util.TranslationKeys;

import java.util.Objects;

@Mixin(RendererSetting.class)
@SuppressWarnings("unused")
public abstract class RendererSettingMixin implements PhotonViewDepthSortAccess, PhotonOcclusionAccess {
	@Unique
	@Configurable(name = TranslationKeys.PHOTON_VIEW_DEPTH_SORT_KEY, tips = TranslationKeys.PHOTON_VIEW_DEPTH_SORT_TIPS_KEY)
	private boolean lobecorp$viewDepthSort;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_OCCLUSION_KEY, subConfigurable = true,
			tips = TranslationKeys.PHOTON_OCCLUSION_TIPS_KEY)
	private final PhotonOcclusionConfig lobecorp$occlusion = new PhotonOcclusionConfig();

	@Override
	public boolean lobecorp$isViewDepthSortEnabled() {
		return this.lobecorp$viewDepthSort;
	}

	@Override
	public boolean lobecorp$isOcclusionEnabled() {
		return lobecorp$occlusion.isEnable();
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
				&& lobecorp$occlusion.equals(access.lobecorp$getOcclusionConfig());
	}

	@ModifyReturnValue(method = "hashCode", at = @At("RETURN"))
	private int lobecorp$includeOcclusionHash(int original) {
		if (!lobecorp$isOcclusionEnabled()) {
			return original;
		}

		return Objects.hash(original, true, lobecorp$getOcclusionAlphaCutoff());
	}
}

