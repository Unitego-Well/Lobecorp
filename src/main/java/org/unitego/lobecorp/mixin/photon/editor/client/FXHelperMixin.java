package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(FXHelper.class)
public abstract class FXHelperMixin {

	@WrapOperation(method = "listAllFX", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;"))
	private static Map<Identifier, Resource> lobecorp$listModelEffects(ResourceManager resources, String directory,
	                                                                   Predicate<Identifier> filter, Operation<Map<Identifier, Resource>> original) {
		return original.call(resources, directory,
				filter.or(location -> location.getPath().endsWith(PhotonModelFileUtil.DEFINITION_SUFFIX)));
	}

	@WrapOperation(method = "lambda$listAllFX$1", at = @At(value = "INVOKE",
			target = "Ljava/lang/String;substring(II)Ljava/lang/String;"))
	private static String lobecorp$modelEffectId(String path, int beginIndex, int endIndex, Operation<String> original) {
		var finalEnd = path.endsWith(PhotonModelFileUtil.DEFINITION_SUFFIX)
				? path.length() - PhotonModelFileUtil.DEFINITION_SUFFIX.length() : endIndex;
		return original.call(path, beginIndex, finalEnd);
	}

	@WrapOperation(method = "listAllFX", at = @At(value = "INVOKE",
			target = "Ljava/util/Collections;unmodifiableList(Ljava/util/List;)Ljava/util/List;"))
	private static List<Identifier> lobecorp$deduplicateEffects(List<Identifier> list, Operation<List<Identifier>> original) {
		return original.call(list.stream().distinct().toList());
	}

	@WrapOperation(method = "loadFX", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resources/Identifier;fromNamespaceAndPath(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"))
	private static Identifier lobecorp$findModelEffect(String namespace, String path, Operation<Identifier> original) {
		var fx = original.call(namespace, path);
		var resources = Minecraft.getInstance().getResourceManager();

		if (resources.getResource(fx).isPresent()) {
			return fx;
		}

		var model = Identifier.fromNamespaceAndPath(namespace,
				path.substring(0, path.length() - PhotonModelFileUtil.FX_SUFFIX.length()) + PhotonModelFileUtil.DEFINITION_SUFFIX);
		return resources.getResource(model).isPresent() ? model : fx;
	}
}
