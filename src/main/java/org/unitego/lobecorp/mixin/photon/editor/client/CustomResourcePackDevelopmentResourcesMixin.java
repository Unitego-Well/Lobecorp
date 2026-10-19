package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.utils.CustomResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.io.File;

@Mixin(CustomResourcePack.class)
public abstract class CustomResourcePackDevelopmentResourcesMixin {
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, name = "location")
	private static File lobecorp$sourceResourcePack(File location) {
		return DevelopmentResourceUtil.packRoot(location);
	}
}
