package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonProjectPreviewTest {
	@Test
	public void preservesUnknownFieldsAndStableIdentityWithoutAddingToLibrary() throws IOException {
		var source = fixture();
		source.putString("future", "keep");
		var parsed = PhotonProjectPreviewUtil.read(source);
		assertEquals(UUID.fromString(source.getStringOr("id", "")), parsed.id());
		assertEquals(source, parsed.source());
		parsed.source().putString("future", "changed");
		assertEquals("keep", parsed.source().getStringOr("future", ""));
		var copied = PhotonProjectPreviewUtil.checkedCopy(source);
		copied.putString("name", "changed");
		assertEquals("preview", source.getStringOr("name", ""));
		assertFalse(source.contains("animations"));
	}

	@Test
	public void rejectsUnsupportedVersionAndInvalidIdentityWithoutChangingSource() {
		var unsupported = fixture();
		unsupported.putInt("version", PhotonNamedLibrary.VERSION + 1);
		var original = unsupported.copy();
		assertThrows(IOException.class, () -> PhotonProjectPreviewUtil.read(unsupported));
		assertEquals(original, unsupported);
		var invalid = fixture();
		invalid.putString("id", "broken");
		assertThrows(IllegalArgumentException.class, () -> PhotonProjectPreviewUtil.checkedCopy(invalid));
	}

	@Test
	public void readsExistingProjectPreviewsAndPreservesOriginalFiles() throws Exception {
		var root = Path.of("").toAbsolutePath();

		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		int previews = 0;

		for (var folder : new String[]{"src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks"}) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					var bytes = Files.readAllBytes(path);
					previews += inspect(PhotonResourceReader.read(path));
					assertArrayEquals(bytes, Files.readAllBytes(path));
				}
			}
		}

		assertTrue(previews > 0);
		System.out.println("G1_PROJECT_PREVIEW_DEFINITIONS=" + previews);
	}

	private static int inspect(PhotonResourceDocument document) throws IOException {
		int count = document.nbt() == null ? 0 : inspect(document.nbt());

		for (var child : document.entries()) {
			count += inspect(child);
		}

		return count;
	}

	private static int inspect(Tag value) throws IOException {
		int count = 0;

		if (value instanceof CompoundTag compound) {
			if (compound.get(PhotonProjectPreviewUtil.PROJECT_FIELD) instanceof CompoundTag source) {
				assertEquals(source, PhotonProjectPreviewUtil.read(source).source());
				count++;
			}

			for (var entry : compound.entrySet()) {
				count += inspect(entry.getValue());
			}
		} else if (value instanceof ListTag list) {
			for (var entry : list) {
				count += inspect(entry);
			}
		}

		return count;
	}

	@Test
	public void permitsOnlyValidatedPreviewContainersWithoutUnsupportedContents() {
		var project = new CompoundTag();
		var source = fixture();
		source.putString("future", "keep");
		var data = new CompoundTag();
		data.put(PhotonProjectPreviewUtil.PROJECT_FIELD, source);
		project.put("data", data);
		assertFalse(PhotonEditorProjectGuardUtil.containsProjectTag(project));
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(project));
		assertEquals("keep", source.getStringOr("future", ""));
		source.putString("lobecorp$future", "unsupported");
		assertTrue(PhotonEditorProjectGuardUtil.containsProjectTag(project));
		source.remove("lobecorp$future");
		var track = new CompoundTag();
		track.putString("type", "lobecorp_animation_call");
		var tracks = new ListTag();
		tracks.add(track);
		source.put("tracks", tracks);
		assertTrue(PhotonEditorProjectGuardUtil.containsProjectTag(project));
		source.remove("tracks");
		source.putInt("version", PhotonNamedLibrary.VERSION + 1);
		assertTrue(PhotonEditorProjectGuardUtil.containsProjectTag(project));
		data.putString(PhotonProjectPreviewUtil.PROJECT_FIELD, "broken");
		assertTrue(PhotonEditorProjectGuardUtil.containsProjectTag(project));
	}

	private static CompoundTag fixture() {
		var source = new CompoundTag();
		source.putInt("version", PhotonNamedLibrary.VERSION);
		source.putString("id", UUID.randomUUID().toString());
		source.putString("name", "preview");
		source.putDouble("duration", 20);
		return source;
	}

	@Test
	public void editsMetadataWithoutReplacingIdentityTracksOrUnknownFields() throws IOException {
		var source = fixture();
		source.putString("name", "existing-name-1");
		source.putString("future", "keep");
		var track = new CompoundTag();
		track.putString("futureTrack", "keep");
		var tracks = new ListTag();
		tracks.add(track);
		source.put("tracks", tracks);
		var original = source.copy();
		var edited = PhotonProjectPreviewUtil.edit(source, "existing-name-1", 50, false, true, 2,
				PhotonNamedLibrary.Completion.RELEASE);
		var definition = PhotonProjectPreviewUtil.read(edited);
		assertEquals(original, source);
		assertEquals(source.get("id"), edited.get("id"));
		assertEquals(tracks, edited.get("tracks"));
		assertEquals("keep", edited.getStringOr("future", ""));
		assertTrue(definition.loop());
		assertFalse(definition.autoDuration());
		assertEquals(50, definition.duration());
		assertEquals(PhotonNamedLibrary.Completion.RELEASE, definition.completion());
		assertThrows(IllegalArgumentException.class, () -> PhotonProjectPreviewUtil.edit(source, "new-name", 20, true, false, 0, PhotonNamedLibrary.Completion.HOLD));
		assertThrows(IllegalArgumentException.class, () -> PhotonProjectPreviewUtil.edit(source, "valid_name", Double.NaN, true, false, 0, PhotonNamedLibrary.Completion.HOLD));
		assertNotEquals(PhotonProjectPreviewUtil.read(PhotonProjectPreviewUtil.create()).id(),
				PhotonProjectPreviewUtil.read(PhotonProjectPreviewUtil.create()).id());
	}
}
