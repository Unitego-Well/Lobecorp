package org.unitego.lobecorp.mixin.photon.material.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.Resource;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.material.LazyBuiltinResourceProvider;
import org.unitego.lobecorp.util.photon.material.PhotonMaterialPreparationUtil;

@Mixin(value = Resource.class)
public abstract class ResourceMixin<T> {
	@WrapOperation(
			method = "buildBuiltin(Lcom/lowdragmc/lowdraglib2/editor/resource/ResourceInstance;)V",
			at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/lowdragmc/lowdraglib2/editor/resource/ResourceInstance;)Lcom/lowdragmc/lowdraglib2/editor/resource/BuiltinResourceProvider;"),
			require = 1
	)
	private BuiltinResourceProvider<T> lobecorp$lazyShaderBuiltin(String name, ResourceInstance<T> resourceInstance,
	                                                              Operation<BuiltinResourceProvider<T>> original) {
		if (PhotonMaterialPreparationUtil.supports(resourceInstance)) {
			return PhotonMaterialPreparationUtil.create(name, resourceInstance);
		}

		return original.call(name, resourceInstance);
	}

	@WrapOperation(
			method = "buildBuiltin(Lcom/lowdragmc/lowdraglib2/editor/resource/ResourceInstance;)V",
			at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/editor/resource/Resource;buildBuiltin(Lcom/lowdragmc/lowdraglib2/editor/resource/BuiltinResourceProvider;)V"),
			require = 1
	)
	private void lobecorp$deferShaderBuiltin(Resource<T> resource, BuiltinResourceProvider<T> provider,
	                                         Operation<Void> original) {
		if (provider instanceof LazyBuiltinResourceProvider<?> && resource.getClass() == ShaderGraphResource.class) {
			return;
		}

		original.call(resource, provider);
	}
}
