package org.unitego.lobecorp.mixin.photon.render.client;

import com.lowdragmc.photon.client.render.PhotonRenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionUtil;
import org.unitego.lobecorp.util.photon.render.PhotonViewDepthSortUtil;

@Mixin(PhotonRenderTypes.class)
public abstract class PhotonRenderTypesMixin {
	@Inject(method = "onResourceReload", at = @At("HEAD"))
	private static void lobecorp$clearCompatibilityPipelines(CallbackInfo ci) {
		PhotonOcclusionUtil.clear();
		PhotonViewDepthSortUtil.clearFrame();
		QueenBeamEffects.clear();
	}
}
