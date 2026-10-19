package org.unitego.lobecorp.util.photon.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelAssetTest {
	@Test
	public void derivesMissingModelRendererFromSavedCurrentFormatWithoutMutatingSource() throws Exception {
		var source = new CompoundTag();
		source.putInt("version", 0);
		var config = new CompoundTag();
		var renderer = new CompoundTag();
		renderer.putString("saved_probe", "keep");
		config.put("renderer", renderer);
		config.putString("assetJson", "{\"version\":1}");
		source.put("config", config);
		config.put("resource", LDLibExtraCodecs.createStringNull(NbtOps.INSTANCE));
		var original = source.copy();
		var adapted = PhotonModelAssetUtil.adaptObject(source);
		assertEquals(renderer, adapted.getCompoundOrEmpty("config").getCompoundOrEmpty("modelRenderer"));
		assertEquals(original, source);
		assertFalse(config.contains("modelRenderer"));
		adapted.getCompoundOrEmpty("config").getCompoundOrEmpty("modelRenderer").putString("saved_probe", "changed");
		assertEquals("keep", renderer.getStringOr("saved_probe", ""));
		config.putString("resource", "invalid id");
		assertThrows(IOException.class, () -> PhotonModelAssetUtil.adaptObject(source));
		config.put("resource", LDLibExtraCodecs.createStringNull(NbtOps.INSTANCE));
		source.putInt("version", 1);
		assertThrows(IOException.class, () -> PhotonModelAssetUtil.adaptObject(source));
	}

	@Test
	public void appliesPackageMaterialPathsOnlyToEffectiveDefinition() throws Exception {
		var source = StringTag.valueOf("""
				{"version":1,"materials":{"main":{"materialData":"{type:'texture',data:{texture:'source:textures/a.png'}}"}}}
				""");
		var original = source.copy();
		var dependencies = new CompoundTag();
		dependencies.put("main", PhotonModelAssetUtil.material("{type:'texture',data:{texture:'pack:textures/a.png'}}"));
		var definition = PhotonModelAssetUtil.read(source, dependencies);
		assertTrue(definition.materials().get("main").materialData().contains("pack:textures/a.png"));
		assertEquals(original, source);
		assertTrue(source.asString().orElseThrow().contains("source:textures/a.png"));
		assertThrows(IOException.class, () -> PhotonModelAssetUtil.material("{data:{}}"));
	}

	@Test
	public void resolvesEveryExistingEmbeddedMaterialOverrideWithoutRewriting() throws Exception {
		var root = Path.of("").toAbsolutePath();

		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		int count = 0;

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks")) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var file : paths.filter(Files::isRegularFile).sorted().toList()) {
					var original = Files.readAllBytes(file);
					count += inspect(PhotonResourceReader.read(file));
					assertArrayEquals(original, Files.readAllBytes(file));
				}
			}
		}

		assertTrue(count > 0);
		System.out.println("G1_EMBEDDED_MATERIAL_BINDINGS=" + count);
	}

	private static int inspect(PhotonResourceDocument document) throws Exception {
		int count = document.nbt() == null ? 0 : inspect(document.nbt());

		for (var child : document.entries()) {
			count += inspect(child);
		}

		return count;
	}

	private static int inspect(Tag tag) throws Exception {
		int count = 0;

		if (tag instanceof CompoundTag compound) {
			if (compound.getStringOr("type", "").equals("lobecorp_model_emitter")) {
				var original = compound.getCompoundOrEmpty("data").copy();
				CompoundTag data;

				try {
					data = PhotonModelAssetUtil.adaptObject(compound.getCompoundOrEmpty("data"));
				} catch (IOException failure) {
					var resource = original.getCompoundOrEmpty("config").get("resource");
					throw new IOException("模型来源字段：" + original.getStringOr("name", "") + " resource=" + resource, failure);
				}
				var config = data.getCompoundOrEmpty("config");
				var asset = config.get("assetJson");
				assertNotNull(asset);
				var definition = PhotonModelAssetUtil.read(asset, config.getCompoundOrEmpty("assetMaterialDependencies"));
				PhotonModelTopologyUtil.prepare(definition);
				assertEquals(original, compound.getCompoundOrEmpty("data"));
				assertTrue(config.get("modelRenderer") instanceof CompoundTag);
				count++;
			}

			for (var entry : compound.entrySet()) {
				count += inspect(entry.getValue());
			}
		} else if (tag instanceof ListTag list) {
			for (var child : list) {
				count += inspect(child);
			}
		}

		return count;
	}
}
