package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(NbtIo.class)
public abstract class PhotonDefinitionWriteGuardMixin {
	@Inject(method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V", at = @At("HEAD"))
	private static void lobecorp$checkDefinition(CompoundTag tag, Path file, CallbackInfo ci) throws IOException {
		if (!file.getFileName().toString().endsWith(FX.SUFFIX)) {
			return;
		}

		PhotonEditorProjectGuardUtil.requireSupported(tag);
		if (Files.exists(file)) {
			PhotonEditorProjectGuardUtil.requireSupported(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
		}
	}
}
