package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonMaterialDiagnosticsUtil.Stage;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlProgram")
public class GlProgramMixin {
	@WrapOperation(method = "link", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glLinkProgram(I)V"))
	private static void lobecorp$link(int program, Operation<Void> original) {
		PhotonMaterialDiagnosticsUtil.call(Stage.GL_LINK_PROGRAM_CALL, original, program);
	}
}
