package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.photon.PhotonFXResourceUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 模型定义参与同一资源管理器和缓存；同ID的原生.fx优先，.mfx不读取开发目录作为兜底。
@Mixin(FXHelper.class)
public abstract class FXHelperMixin {
	@Unique
	private static volatile List<Identifier> lobecorp$modelIds;

	@ModifyExpressionValue(method = "readTag", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXHelper;fileLocation(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/resources/Identifier;"))
	private static Identifier lobecorp$modelDefinition(Identifier original) {
		var manager = Minecraft.getInstance().getResourceManager();
		return PhotonFXResourceUtil.resolve(original, id -> manager.getResource(id).isPresent());
	}

	@ModifyReturnValue(method = "listAllFX", at = @At("RETURN"))
	private static List<Identifier> lobecorp$includeModelDefinitions(List<Identifier> original) {
		var models = lobecorp$modelIds;
		if (models == null) {
			models = new ArrayList<>();
			for (var location : Minecraft.getInstance().getResourceManager().listResources("fx", id -> id.getPath().endsWith(".mfx")).keySet()) {
				var path = location.getPath();
				models.add(Identifier.fromNamespaceAndPath(location.getNamespace(), path.substring(FXHelper.FX_PATH.length(), path.length() - 4)));
			}
			lobecorp$modelIds = List.copyOf(models);
		}
		var result = new ArrayList<>(original);
		for (var id : models) {
			if (!result.contains(id)) {
				result.add(id);
			}
		}
		result.sort(Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath));
		return List.copyOf(result);
	}

	@Inject(method = "clearCache", at = @At("HEAD"))
	private static void lobecorp$clearModelIds(CallbackInfoReturnable<Integer> ci) {
		lobecorp$modelIds = null;
	}
}
