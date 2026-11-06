package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.render.PhotonFullscreenPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.render.PhotonIrisPipelineAccess;

/// 新版全屏合成继续使用 Photon 自有管线，原版与光影包管线仍走原路。
@Mixin(PhotonFullscreenPass.class)
public class PhotonFullscreenPassMixin {
	@WrapOperation(method = "draw(Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/buffers/GpuBuffer;Ljava/util/function/Consumer;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private static void lobecorp$bindNativePipeline(RenderPass pass, RenderPipeline pipeline, Operation<Void> original) {
		if (IrisCompat.isUsingShaderPack() && PhotonIrisPipelineAccess.isPhotonPipeline(pipeline)) {
			PhotonIrisPipelineAccess.useNativePipeline(() -> original.call(pass, pipeline));
		} else {
			original.call(pass, pipeline);
		}
	}
}
