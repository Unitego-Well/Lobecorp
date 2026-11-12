package org.unitego.lobecorp.mixin.photon.material.client;

import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.material.PhotonMaterialPreparationUtil;

@Mixin(value = ResourceInstance.class)
public abstract class ResourceInstanceMixin {
	@Inject(method = "getResource", at = @At("RETURN"), require = 1)
	private void lobecorp$countFileWithoutDefault(IResourcePath path, CallbackInfoReturnable<Object> cir) {
		PhotonMaterialPreparationUtil.recordFileRequest((ResourceInstance<?>) (Object) this, path, cir.getReturnValue());
	}
}
