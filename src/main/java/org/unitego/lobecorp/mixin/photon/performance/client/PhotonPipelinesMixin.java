package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

import java.util.Map;
import java.util.function.Function;

@Mixin(targets = "com.lowdragmc.photon.client.render.PhotonPipelines")
public class PhotonPipelinesMixin {
	@WrapOperation(method = {"hdrParticle(Lnet/minecraft/resources/Identifier;Lcom/lowdragmc/photon/client/render/PhotonPipelines$ParticlePipelineKey;)Lcom/mojang/blaze3d/pipeline/RenderPipeline;", "instancedHdrParticle(Lcom/lowdragmc/photon/client/render/PhotonPipelines$InstancedVariant;Lnet/minecraft/resources/Identifier;Lcom/lowdragmc/photon/client/render/PhotonPipelines$ParticlePipelineKey;)Lcom/mojang/blaze3d/pipeline/RenderPipeline;", "customShader", "instancedCustomShader", "graphShader"},
			at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private static Object lobecorp$cache(Map<Object, Object> map, Object key,
	                                     Function<Object, Object> mapping, Operation<Object> original) {
		return PhotonMaterialDiagnosticsUtil.cache(Stage.PHOTON_PIPELINE_CACHE, Stage.PHOTON_PIPELINE_BUILD, map, key, mapping, original);
	}
}
