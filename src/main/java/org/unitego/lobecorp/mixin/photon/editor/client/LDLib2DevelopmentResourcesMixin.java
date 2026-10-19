package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.lowdraglib2.LDLib2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.io.File;

@Mixin(LDLib2.class)
public abstract class LDLib2DevelopmentResourcesMixin {
	@ModifyReturnValue(method = "getAssetsDir", at = @At("RETURN"))
	private static File lobecorp$sourceAssets(File original) {
		return DevelopmentResourceUtil.assetsDirectory(original);
	}
}
