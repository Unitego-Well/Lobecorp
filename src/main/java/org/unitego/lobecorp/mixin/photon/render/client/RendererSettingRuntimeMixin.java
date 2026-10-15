package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;

import java.util.Objects;

@Mixin(RendererSetting.Runtime.class)
public abstract class RendererSettingRuntimeMixin implements PhotonViewDepthSortAccess, PhotonOcclusionAccess {
	@Shadow
	@Final
	protected RendererSetting config;

	@Override
	public boolean lobecorp$isViewDepthSortEnabled() {
		return ((PhotonViewDepthSortAccess) this.config).lobecorp$isViewDepthSortEnabled();
	}

	@Override
	public boolean lobecorp$isOcclusionEnabled() {
		return lobecorp$getOcclusionConfig().isEnable();
	}

	@Override
	public float lobecorp$getOcclusionAlphaCutoff() {
		return lobecorp$getOcclusionConfig().getAlphaCutoff();
	}

	@Override
	public PhotonOcclusionConfig lobecorp$getOcclusionConfig() {
		return ((PhotonOcclusionAccess) config).lobecorp$getOcclusionConfig();
	}

	@ModifyReturnValue(method = "effectiveEquals", at = @At("RETURN"))
	private boolean lobecorp$includeOcclusionEquality(boolean original, RendererSetting.Runtime o) {
		return original && o instanceof PhotonOcclusionAccess access
				&& lobecorp$getOcclusionConfig().equals(access.lobecorp$getOcclusionConfig());
	}

	@ModifyReturnValue(method = "effectiveHashCode", at = @At("RETURN"))
	private int lobecorp$includeOcclusionHash(int original) {
		if (!lobecorp$isOcclusionEnabled()) {
			return original;
		}

		return Objects.hash(original, true, lobecorp$getOcclusionAlphaCutoff());
	}
}

