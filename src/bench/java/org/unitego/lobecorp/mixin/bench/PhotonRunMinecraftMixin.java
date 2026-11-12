package org.unitego.lobecorp.mixin.bench;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonRunMatrixCapture;

@Mixin(value = Minecraft.class)
public class PhotonRunMinecraftMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void lobecorp$runBeforeFrame(boolean advanceGameTime, CallbackInfo callback) {
		var minecraft = (Minecraft) (Object) this;
		if (minecraft.isGameLoadFinished()) {
			PhotonRunMatrixCapture.beforeFrame(minecraft);
		}
	}
}
