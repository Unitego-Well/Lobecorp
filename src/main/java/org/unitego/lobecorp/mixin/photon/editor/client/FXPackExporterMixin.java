package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;

import java.nio.file.FileSystem;
import java.nio.file.Path;

@Mixin(FXPackExporter.class)
public abstract class FXPackExporterMixin {
	@WrapOperation(method = "exportInto", at = @At(value = "INVOKE",
			target = "Ljava/nio/file/FileSystem;getPath(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", ordinal = 1))
	private static Path lobecorp$modelDefinitionEntry(FileSystem zip, String first, String[] other, Operation<Path> original) {
		var name = other[other.length - 1];
		var definition = PhotonModelFileUtil.definitionPath(zip, name);

		if (name.equals(definition)) {
			return original.call(zip, first, other);
		}

		var paths = other.clone();
		paths[paths.length - 1] = definition;
		return original.call(zip, first, paths);
	}
}
