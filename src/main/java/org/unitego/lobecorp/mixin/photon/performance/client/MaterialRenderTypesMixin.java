package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

import java.util.Map;
import java.util.function.Function;

@Mixin(targets = "com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialRenderTypes")
public class MaterialRenderTypesMixin {
	@WrapOperation(method = {"hdrParticle(Lnet/minecraft/resources/Identifier;Lnet/minecraft/resources/Identifier;Lcom/lowdragmc/photon/client/render/PhotonPipelines$ParticlePipelineKey;Lcom/lowdragmc/photon/client/render/PhotonMaterialUniforms$Values;)Lnet/minecraft/client/renderer/rendertype/RenderType;"},
			at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private static Object lobecorp$cache(Map<Object, Object> map, Object key,
	                                     Function<Object, Object> mapping, Operation<Object> original) {
		return PhotonMaterialDiagnosticsUtil.cache(Stage.HDR_MATERIAL_CACHE, Stage.HDR_MATERIAL_BUILD, map, key, mapping, original);
	}
}
