package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.photon.client.shadergraph.runtime.ShaderGraphRuntime;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

@Mixin(ShaderGraphRuntime.class)
public class ShaderGraphRuntimeMixin {
	@WrapMethod(method = "get")
	private static ShaderGraphRuntime.Entry lobecorp$lookup(IResourcePath path, Operation<ShaderGraphRuntime.Entry> original) {
		return PhotonMaterialDiagnosticsUtil.graphLookup(path, original);
	}

	@WrapMethod(method = "compile")
	private static ShaderGraphRuntime.Entry lobecorp$build(CompoundTag tag, Operation<ShaderGraphRuntime.Entry> original) {
		return PhotonMaterialDiagnosticsUtil.call(Stage.GRAPH_RESOURCE_BUILD, original, tag);
	}
}
