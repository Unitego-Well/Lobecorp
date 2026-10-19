package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.util.List;

@Mixin(MultiPackResourceManager.class)
public abstract class MultiPackResourceManagerDevelopmentResourcesMixin {
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, name = "packs")
	private static List<PackResources> lobecorp$developmentRoots(List<PackResources> packs, @Local(argsOnly = true, name = "type") PackType type) {
		return DevelopmentResourceUtil.runtimePacks(type, packs);
	}
}
