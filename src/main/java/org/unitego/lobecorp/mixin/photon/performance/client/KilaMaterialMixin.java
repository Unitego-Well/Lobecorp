package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

import java.util.Map;

@Mixin(targets = "com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial", remap = false)
public class KilaMaterialMixin {
	@WrapOperation(method = "renderType", at = @At(value = "INVOKE",
			target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object lobecorp$renderType(Map<Object, Object> map, Object key, Operation<Object> original) {
		return PhotonMaterialDiagnosticsUtil.lookup(Stage.GRAPH_MATERIAL_CACHE, map, key, original);
	}
}
