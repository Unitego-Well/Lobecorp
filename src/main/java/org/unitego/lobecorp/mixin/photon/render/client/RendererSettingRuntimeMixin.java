package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.render.PhotonFixedRenderAccess;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;
import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;

import java.util.Objects;

@Mixin(RendererSetting.Runtime.class)
public abstract class RendererSettingRuntimeMixin implements PhotonViewDepthSortAccess, PhotonOcclusionAccess, PhotonFixedRenderAccess {
	@Shadow
	@Final
	protected RendererSetting config;

	@Override
	public void lobecorp$useFixedRendering() {
		((PhotonFixedRenderAccess) config).lobecorp$useFixedRendering();
	}

	@Override
	public void lobecorp$copyFixedRendering(RendererSetting.Runtime source) {
		((PhotonFixedRenderAccess) config).lobecorp$copyFixedRendering(source);
	}

	@Override
	public boolean lobecorp$isFixedRendering() {
		return ((PhotonFixedRenderAccess) config).lobecorp$isFixedRendering();
	}

	@Override
	public boolean lobecorp$isViewDepthSortEnabled() {
		return ((PhotonViewDepthSortAccess) this.config).lobecorp$isViewDepthSortEnabled();
	}

	@Override
	public boolean lobecorp$isOcclusionEnabled() {
		return ((PhotonOcclusionAccess) config).lobecorp$isOcclusionEnabled();
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
				&& lobecorp$isOcclusionEnabled() == access.lobecorp$isOcclusionEnabled()
				&& (!lobecorp$isOcclusionEnabled() || Float.compare(lobecorp$getOcclusionAlphaCutoff(), access.lobecorp$getOcclusionAlphaCutoff()) == 0)
				&& o instanceof PhotonViewDepthSortAccess depth && lobecorp$isViewDepthSortEnabled() == depth.lobecorp$isViewDepthSortEnabled();
	}

	@ModifyReturnValue(method = "effectiveHashCode", at = @At("RETURN"))
	private int lobecorp$includeOcclusionHash(int original) {
		if (!lobecorp$isOcclusionEnabled() && !lobecorp$isViewDepthSortEnabled()) {
			return original;
		}

		return Objects.hash(original, lobecorp$isViewDepthSortEnabled(), lobecorp$isOcclusionEnabled(),
				lobecorp$isOcclusionEnabled() ? lobecorp$getOcclusionAlphaCutoff() : 0f);
	}
}
