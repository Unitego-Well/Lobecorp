package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

@Mixin(targets = "com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting", remap = false)
public class MaterialSettingMixin {
	@WrapMethod(method = "getRenderType")
	private RenderType lobecorp$material(VertexFormat.Mode mode, Operation<RenderType> original) {
		return PhotonMaterialDiagnosticsUtil.call(Stage.MATERIAL_LOOKUP, original, mode);
	}
}
