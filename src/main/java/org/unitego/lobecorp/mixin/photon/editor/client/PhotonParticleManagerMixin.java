package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.PhotonParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonPreviewTimeAccess;

@Mixin(value = PhotonParticleManager.class, remap = false)
public abstract class PhotonParticleManagerMixin implements PhotonPreviewTimeAccess {
	@Shadow
	private boolean isPlaying;
	@Unique
	private float lobecorp$seekFraction;

	@Override
	public void lobecorp$seekFraction(float fraction) {
		if (!Float.isFinite(fraction) || fraction < 0 || fraction >= 1) {
			throw new IllegalArgumentException("预览帧内时间必须位于[0,1)。");
		}

		lobecorp$seekFraction = fraction;
	}

	@ModifyReturnValue(method = {"getTime(F)F", "getRealTime(F)F"}, at = @At("RETURN"))
	private float lobecorp$pausedTime(float original) {
		return isPlaying ? original : original + lobecorp$seekFraction;
	}

	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/client/scene/ParticleManager;render(Lnet/minecraft/client/renderer/SubmitNodeStorage;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;F)V"), index = 4)
	private float lobecorp$pausedFrame(float original) {
		return isPlaying ? original : lobecorp$seekFraction;
	}

	@Inject(method = {"play", "clear", "setTime"}, at = @At("HEAD"))
	private void lobecorp$clearSeek(CallbackInfo ci) {
		lobecorp$seekFraction = 0;
	}
}
