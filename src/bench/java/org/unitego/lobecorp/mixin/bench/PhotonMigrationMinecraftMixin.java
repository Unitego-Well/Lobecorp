package org.unitego.lobecorp.mixin.bench;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonMigrationMatrixCapture;

/// 仅新进入场景装载；采集入口早于Bench在世界可用后的setup。
@Mixin(value = Minecraft.class, remap = false)
public class PhotonMigrationMinecraftMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void lobecorp$hybridLoading(boolean advance, CallbackInfo callback) {
		var minecraft = (Minecraft) (Object) this;
		PhotonMigrationMatrixCapture.loadingFrame(minecraft);
		if (advance && minecraft.isGameLoadFinished()) {
			PhotonMigrationMatrixCapture.beforeRender(minecraft);
		}
	}

	@Inject(method = "renderFrame", at = @At("RETURN"))
	private void lobecorp$hybridAfterFrame(boolean advance, CallbackInfo callback) {
		PhotonMigrationMatrixCapture.afterFrame((Minecraft) (Object) this);
	}
}
