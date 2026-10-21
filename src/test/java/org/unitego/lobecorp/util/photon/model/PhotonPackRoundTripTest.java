package org.unitego.lobecorp.util.photon.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPackRoundTripTest {
	private static final String DEFINITION = "assets/test/fx/probe.fx";
	private static final String RESOURCE = "assets/test/payload.bin";
	private static final String METADATA = "{\"pack\":{\"description\":\"probe\",\"pack_format\":34}}";
	@TempDir
	public Path directory;

	@BeforeAll
	public static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void acceptsFullDefinitionAndResourceReadback() throws Exception {
		var archive = archive(DEFINITION);
		var path = directory.resolve("complete.fxpack");
		archive.write(path);
		archive.verify(PhotonResourceReader.read(path), null);
	}

	@Test
	public void rejectsMissingAndChangedDefinitionOrResource() throws Exception {
		var archive = archive(DEFINITION);
		for (var entry : new String[]{DEFINITION, RESOURCE}) {
			var path = directory.resolve(entry.equals(DEFINITION) ? "definition.fxpack" : "resource.fxpack");
			archive.write(path);
			try (var zip = FileSystems.newFileSystem(path)) {
				Files.delete(zip.getPath(entry));
			}

			assertThrows(IOException.class, () -> archive.verify(PhotonResourceReader.read(path), null));
			archive.write(path);
			try (var zip = FileSystems.newFileSystem(path)) {
				if (entry.equals(DEFINITION)) {
					var changed = archive.definitions().get(DEFINITION).copy();
					changed.putString("future", "changed");
					NbtIo.writeCompressed(changed, zip.getPath(entry));
				} else {
					Files.write(zip.getPath(entry), new byte[]{9});
				}
			}

			assertThrows(IOException.class, () -> archive.verify(PhotonResourceReader.read(path), null));
		}
	}

	@Test
	public void acceptsEquivalentCompressedNbtBytes() throws Exception {
		var archive = archive(DEFINITION);
		var path = directory.resolve("encoding.fxpack");
		archive.write(path);
		try (var zip = FileSystems.newFileSystem(path)) {
			var entry = zip.getPath(DEFINITION);
			var compressed = Files.readAllBytes(entry);
			compressed[4] = 1;
			Files.write(entry, compressed);
		}

		archive.verify(PhotonResourceReader.read(path), null);
	}

	@Test
	public void preservesUnknownOldEntriesAndMetadataDuringIncrementalExport() throws Exception {
		var archive = archive(DEFINITION);
		var path = directory.resolve("incremental.fxpack");
		archive.write(path);
		var metadata = "{\"pack\":{\"description\":\"original\",\"pack_format\":34},\"future\":true}";
		try (var zip = FileSystems.newFileSystem(path)) {
			Files.writeString(zip.getPath("pack.mcmeta"), metadata);
			Files.write(zip.getPath("unknown.bin"), new byte[]{0, -1, 4});
		}

		var original = PhotonResourceReader.read(path);
		archive.write(path);
		archive.verify(PhotonResourceReader.read(path), original);
		try (var zip = FileSystems.newFileSystem(path)) {
			assertEquals(metadata, Files.readString(zip.getPath("pack.mcmeta")));
			Files.write(zip.getPath("unknown.bin"), new byte[]{1});
		}

		assertThrows(IOException.class, () -> archive.verify(PhotonResourceReader.read(path), original));
		try (var zip = FileSystems.newFileSystem(path)) {
			Files.delete(zip.getPath("unknown.bin"));
		}

		assertThrows(IOException.class, () -> archive.verify(PhotonResourceReader.read(path), original));
	}

	@Test
	public void onlyAllowsReplacedModelDefinitionToRemoveItsNativeShadow() throws Exception {
		var path = directory.resolve("model.mfxpack");
		var nativeArchive = archive(DEFINITION);
		nativeArchive.write(path);
		var original = PhotonResourceReader.read(path);
		var model = archive("assets/test/fx/probe.mfx");
		model.write(path);
		model.verify(PhotonResourceReader.read(path), original);
		try (var zip = FileSystems.newFileSystem(path)) {
			assertFalse(Files.exists(zip.getPath(DEFINITION)));
			Files.write(zip.getPath("unexpected.bin"), new byte[]{1});
		}

		assertThrows(IOException.class, () -> model.verify(PhotonResourceReader.read(path), original));
	}

	@Test
	public void rejectsMissingInvalidOrChangedOldMetadata() throws Exception {
		var archive = archive(DEFINITION);
		var path = directory.resolve("metadata.fxpack");
		archive.write(path);
		var original = PhotonResourceReader.read(path);
		for (var metadata : new String[]{"{}", "{\"pack\":{\"description\":\"invalid\",\"pack_format\":-1}}",
				"{\"pack\":{\"description\":\"changed\",\"pack_format\":34}}"}) {
			try (var zip = FileSystems.newFileSystem(path)) {
				Files.writeString(zip.getPath("pack.mcmeta"), metadata);
			}

			assertThrows(IOException.class, () -> archive.verify(PhotonResourceReader.read(path), original));
		}

		try (var zip = FileSystems.newFileSystem(path)) {
			Files.delete(zip.getPath("pack.mcmeta"));
		}

		assertThrows(IOException.class, () -> archive.verify(PhotonResourceReader.read(path), original));
	}

	private static PhotonModelPackExportUtil.Archive archive(String name) {
		var definition = new CompoundTag();
		definition.putInt("version", 5);
		definition.putString("future", "preserve");
		return new PhotonModelPackExportUtil.Archive(Identifier.fromNamespaceAndPath("test", "probe"),
				Map.of(name, definition), Map.of(RESOURCE, "resource".getBytes(StandardCharsets.UTF_8)), METADATA);
	}
}
