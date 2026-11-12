package org.unitego.lobecorp.mixin.photon.creation.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

@Mixin(value = FX.class)
public class FXCreationMixin {
	@WrapMethod(method = "createRuntime(Z)Lcom/lowdragmc/photon/client/fx/FXRuntime;")
	private FXRuntime lobecorp$createFromDefinition(boolean deep, Operation<FXRuntime> original) {
		return PhotonRuntimeFactory.create((FX) (Object) this, deep, () -> original.call(deep));
	}
}
