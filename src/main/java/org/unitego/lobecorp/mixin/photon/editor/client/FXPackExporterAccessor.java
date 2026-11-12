package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/// 资源解析仍在客户端线程完成；后台仅接收原生重写后的标签和资源字节。
@Mixin(FXPackExporter.class)
public interface FXPackExporterAccessor {
	@Invoker("<init>")
	static FXPackExporter lobecorp$create(HolderLookup.Provider provider) {
		throw new UnsupportedOperationException();
	}

	@Invoker("sanitize")
	static String lobecorp$sanitize(String name) {
		throw new UnsupportedOperationException();
	}

	@Accessor("PACK_MCMETA")
	static String lobecorp$metadata() {
		throw new UnsupportedOperationException();
	}

	@Invoker("walk")
	void lobecorp$walk(CompoundTag data);

	@Invoker("packSources")
	Map<Identifier, byte[]> lobecorp$packSources(CompoundTag root, Identifier self) throws IOException;

	@Accessor("files")
	Map<Identifier, byte[]> lobecorp$files();

	@Accessor("warnings")
	List<String> lobecorp$warnings();
}
