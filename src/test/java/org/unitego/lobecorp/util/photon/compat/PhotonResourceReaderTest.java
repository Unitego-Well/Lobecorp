package org.unitego.lobecorp.util.photon.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonResourceReaderTest {
	@TempDir
	public Path directory;

	@Test
	public void preservesUnknownNbtAndDefendsMutableAccessors() throws Exception {
		var tag = new CompoundTag();
		tag.putString("type", "future_model_type");
		tag.putString("future", "preserve");
		var data = nbt(tag, false);
		var path = Files.write(directory.resolve("unknown.fxproj"), data);
		var document = PhotonResourceReader.read(path);
		assertArrayEquals(data, document.bytes());
		assertEquals("preserve", document.nbt().getStringOr("future", ""));
		var decoded = document.nbt();
		decoded.remove("future");
		document.bytes()[0] = 0;
		assertEquals("preserve", document.nbt().getStringOr("future", ""));
		assertArrayEquals(data, document.bytes());
		assertFalse(PhotonResourceInspection.inspect(document).runtimeReady());
	}

	@Test
	public void acceptsCompressedRuntimeAndRejectsWrongContainer() throws Exception {
		var tag = new CompoundTag();
		tag.putString("type", "lobecorp_model_emitter");
		var path = Files.write(directory.resolve("model.mfx"), nbt(tag, true));
		var document = PhotonResourceReader.read(path);
		assertEquals(PhotonResourceDocument.Kind.MODEL_FX, document.kind());
		assertTrue(PhotonResourceInspection.isLegacyType(document.nbt().getStringOr("type", "")));
		Files.write(path, nbt(tag, false));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
	}

	@Test
	public void rejectsTrailingNbtInsteadOfDroppingIt() throws Exception {
		var data = nbt(new CompoundTag(), false);
		var extra = Arrays.copyOf(data, data.length + 1);
		Files.write(directory.resolve("extra.fxproj"), extra);
		assertThrows(IOException.class, () -> PhotonResourceReader.read(directory.resolve("extra.fxproj")));
	}

	@Test
	public void enforcesSourceAndExpandedBudgets() throws Exception {
		var tag = new CompoundTag();
		tag.putString("large", "a".repeat(8192));
		var path = Files.write(directory.resolve("large.fx"), nbt(tag, true));
		var limits = new PhotonResourceLimits(512, 512, 1024, 8, 4, 100);
		assertTrue(Files.size(path) < limits.sourceBytes());
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path, limits));
		Files.write(directory.resolve("raw.bin"), new byte[513]);
		assertThrows(IOException.class, () -> PhotonResourceReader.read(directory.resolve("raw.bin"), limits));
	}

	@Test
	public void preservesArchiveUnknownEntriesAndModelDefinition() throws Exception {
		var unknown = new byte[]{0, 2, -1, 4};
		var tag = new CompoundTag();
		tag.putString("type", "lobecorp_model_emitter");
		var path = directory.resolve("models.mfxpack");
		archive(path, Map.of("assets/test/fx/probe.mfx", nbt(tag, true), "extra/future.bin", unknown));
		var original = Files.readAllBytes(path);
		var document = PhotonResourceReader.read(path);
		assertArrayEquals(original, document.bytes());
		assertEquals(2, document.entries().size());
		var binary = document.entries().stream().filter(entry -> entry.kind() == PhotonResourceDocument.Kind.BINARY).findFirst().orElseThrow();
		assertArrayEquals(unknown, binary.bytes());
		assertTrue(document.entries().stream().anyMatch(entry -> entry.kind() == PhotonResourceDocument.Kind.MODEL_FX));
	}

	@Test
	public void rejectsTraversalAndTruncatedArchive() throws Exception {
		var path = directory.resolve("unsafe.fxpack");
		archive(path, Map.of("../outside.bin", new byte[]{1}));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
		archive(path, Map.of("safe.bin", new byte[]{1}));
		var bytes = Files.readAllBytes(path);
		Files.write(path, Arrays.copyOf(bytes, bytes.length - 22));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
	}

	@Test
	public void archiveBudgetsApplyAcrossEntries() throws Exception {
		var path = directory.resolve("many.fxpack");
		var contents = new LinkedHashMap<String, byte[]>();
		contents.put("first.bin", new byte[400]);
		contents.put("second.bin", new byte[400]);
		archive(path, contents);
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path, new PhotonResourceLimits(4096, 700, 4096, 8, 5, 100)));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path, new PhotonResourceLimits(4096, 4096, 4096, 8, 1, 100)));
	}

	@Test
	public void rejectsDuplicateJsonAndExcessiveComplexity() throws Exception {
		var limits = PhotonResourceLimits.DEFAULT;
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("{\"value\":1,\"value\":2}", limits));
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("{\"array\":[[[[1]]]]}", new PhotonResourceLimits(4096, 4096, 4096, 2, 10, 100)));
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("[1,2,3]", new PhotonResourceLimits(4096, 4096, 4096, 10, 10, 2)));
		assertThrows(IOException.class, () -> PhotonResourceJsonUtil.read("{\"value\":1} trailing", limits));
	}

	@Test
	public void rejectsInvalidUtf8InEmbeddedModel() throws Exception {
		var tag = new CompoundTag();
		tag.putByteArray("assetJson", new byte[]{'{', '"', -1, '"', ':', '1', '}'});
		var path = Files.write(directory.resolve("invalid.fxproj"), nbt(tag, false));
		assertThrows(IOException.class, () -> PhotonResourceInspection.inspect(PhotonResourceReader.read(path)));
	}

	@Test
	public void indexesEmbeddedModelAndKeepsReadinessSeparate() throws Exception {
		var asset = "{\"version\":1,\"vertices\":[],\"faces\":[],\"parts\":[{}],\"morphs\":[{}],\"future\":true}";
		var tag = new CompoundTag();
		tag.putByteArray("assetJson", asset.getBytes(StandardCharsets.UTF_8));
		tag.putBoolean("lobecorp$fixedRendering", false);
		tag.putString("type", "lobecorp_model_emitter");
		tag.putString("target", "943473cc-da2d-4ed4-9c35-2bac5e3dea6b");
		tag.putString("material", "file(assets/test/material.nbt)");
		var path = Files.write(directory.resolve("model.fxproj"), nbt(tag, false));
		var report = PhotonResourceInspection.inspect(PhotonResourceReader.read(path));
		assertEquals(1, report.models().getFirst().morphs());
		assertEquals(1, report.models().getFirst().parts());
		assertEquals(1, report.identities().size());
		assertEquals(1, report.references().size());
		assertEquals(1, report.legacyFields().size());
		assertEquals(1, report.types().get("lobecorp_model_emitter"));
		assertFalse(report.runtimeReady());
		assertEquals(asset, new String(PhotonResourceReader.read(path).nbt().getByteArray("assetJson").orElseThrow(), StandardCharsets.UTF_8));
	}

	@Test
	public void directoryPayloadCannotBypassArchiveBudget() throws Exception {
		var path = directory.resolve("directory.fxpack");
		archive(path, Map.of("hidden/", new byte[1024]));
		assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
	}

	@Test
	public void fileGuardStillBlocksLegacyAndAllowsPlainNativeData() throws Exception {
		var tag = new CompoundTag();
		tag.putString("type", "particle_emitter");
		var path = Files.write(directory.resolve("native.fxproj"), nbt(tag, false));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.putBoolean("lobecorp$fixedRendering", false);
		Files.write(path, nbt(tag, false));
		assertFalse(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.putInt("lobecorp$fixedRendering", 0);
		Files.write(path, nbt(tag, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.putBoolean("lobecorp$fixedRendering", false);
		tag.putBoolean("lobecorp$unrecognized", false);
		Files.write(path, nbt(tag, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		tag.remove("lobecorp$unrecognized");
		tag.putBoolean("lobecorp$fixedRendering", true);
		Files.write(path, nbt(tag, false));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(path.toFile()));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(directory.resolve("model.mfxproj").toFile()));
		archive(directory.resolve("legacy.fxpack"), Map.of("assets/test/fx/effect.fx", nbt(tag, true)));
		assertTrue(PhotonEditorProjectGuardUtil.containsFileExtensions(directory.resolve("legacy.fxpack").toFile()));
	}

	@Test
	public void interruptedReadDoesNotClearCancellation() throws Exception {
		var path = Files.write(directory.resolve("cancel.bin"), new byte[64]);
		Thread.currentThread().interrupt();

		try {
			assertThrows(IOException.class, () -> PhotonResourceReader.read(path));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	private static byte[] nbt(CompoundTag tag, boolean compressed) throws IOException {
		var output = new ByteArrayOutputStream();

		if (compressed) {
			NbtIo.writeCompressed(tag, output);
		} else {
			try (var data = new DataOutputStream(output)) {
				NbtIo.write(tag, data);
			}
		}

		return output.toByteArray();
	}

	private static void archive(Path path, Map<String, byte[]> contents) throws IOException {
		try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
			for (var entry : contents.entrySet()) {
				output.putNextEntry(new ZipEntry(entry.getKey()));
				output.write(entry.getValue());
				output.closeEntry();
			}
		}
	}
}
