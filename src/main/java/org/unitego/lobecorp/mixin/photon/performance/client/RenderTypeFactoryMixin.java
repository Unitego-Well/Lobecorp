package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

import java.util.Map;
import java.util.function.Function;

@Mixin(targets = "com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory")
public class RenderTypeFactoryMixin {
	@WrapOperation(method = {"getOrBuildPipeline"},
			at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private static Object lobecorp$cache(Map<Object, Object> map, Object key,
	                                     Function<Object, Object> mapping, Operation<Object> original) {
		return PhotonMaterialDiagnosticsUtil.cache(Stage.KG_PIPELINE_CACHE, Stage.KG_PIPELINE_BUILD, map, key, mapping, original);
	}

	@WrapMethod(method = "createMaterial(Lcom/lowdragmc/kilagraph/rendertype/compiler/CompiledShaderGraph;)Lcom/lowdragmc/kilagraph/rendertype/runtime/RenderTypeGraphMaterial;")
	private static RenderTypeGraphMaterial lobecorp$material(CompiledShaderGraph compiled, Operation<RenderTypeGraphMaterial> original) {
		return PhotonMaterialDiagnosticsUtil.call(Stage.KG_MATERIAL_CREATE, original, compiled);
	}
}
