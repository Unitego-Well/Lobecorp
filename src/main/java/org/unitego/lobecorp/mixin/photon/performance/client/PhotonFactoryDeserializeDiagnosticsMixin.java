package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

import java.util.function.Supplier;

@Mixin(targets = "com.lowdragmc.lowdraglib2.utils.PersistedParser$1", remap = false)
public abstract class PhotonFactoryDeserializeDiagnosticsMixin {
	@WrapOperation(method = "decode", at = @At(value = "INVOKE",
			target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"))
	private Object lobecorp$create(Supplier<?> supplier, Operation<Object> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(supplier);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.FACTORY_CREATE, "", ""), original, supplier);
	}
}
