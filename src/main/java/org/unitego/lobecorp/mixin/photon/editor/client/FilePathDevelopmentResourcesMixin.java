package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

@Mixin(FilePath.class)
public abstract class FilePathDevelopmentResourcesMixin {
	@ModifyReturnValue(method = "toGameRelative", at = @At("RETURN"))
	private static String lobecorp$portableSourcePath(String original, String rawPath) {
		return DevelopmentResourceUtil.gameRelativeSourcePath(original, rawPath);
	}
}
