package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import net.minecraft.nbt.FloatTag;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;

import java.io.IOException;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonNamedAuthoringTest {
	@Test
	public void createsCopiesMovesAndDeletesWithoutMutatingSource() throws IOException {
		var empty = new Document(new CompoundTag(), null);
		var added = PhotonNamedAuthoringUtil.add(empty, "first");
		var id = PhotonNamedCodecUtil.read(added.library()).animations().getFirst().id();
		var copied = PhotonNamedAuthoringUtil.duplicate(added, id, "second");
		var definitions = PhotonNamedCodecUtil.read(copied.library()).animations();
		assertNotEquals(id, definitions.getLast().id());
		assertEquals(0, PhotonNamedCodecUtil.read(empty.library()).animations().size());
		assertEquals(1, PhotonNamedCodecUtil.read(added.library()).animations().size());
		var moved = PhotonNamedAuthoringUtil.move(copied, id, 1);
		assertEquals(id, PhotonNamedCodecUtil.read(moved.library()).animations().getLast().id());
		assertEquals(1, PhotonNamedCodecUtil.read(PhotonNamedAuthoringUtil.remove(moved, id).library()).animations().size());
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedAuthoringUtil.add(added, "first"));
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedAuthoringUtil.add(added, "Invalid-1"));
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedAuthoringUtil.move(added, UUID.randomUUID(), 0));
	}

	@Test
	public void renamesOnlyLocalReferencesAndKeepsAllUnknownFields() throws IOException {
		var document = fixture();
		var id = PhotonNamedCodecUtil.read(document.library()).animations().getFirst().id();
		assertEquals(4, PhotonNamedAuthoringUtil.references(document, id).size());
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedAuthoringUtil.remove(document, id));
		var renamed = PhotonNamedAuthoringUtil.rename(document, id, "renamed");
		var library = renamed.library();
		assertEquals("renamed", library.getStringOr("defaultAnimation", ""));
		assertEquals(id, PhotonNamedCodecUtil.read(library).animations().getFirst().id());
		assertEquals("keep", library.getStringOr("future", ""));
		var rule = (CompoundTag) library.getListOrEmpty("rules").getFirst();
		assertEquals("renamed", rule.getStringOr("target", ""));
		assertEquals("renamed", rule.getStringOr("animation", ""));
		assertEquals("first", rule.getStringOr("name", ""));
		assertEquals("keep", rule.getStringOr("future", ""));
		var preview = renamed.preview();
		assertNotNull(preview);
		var group = (CompoundTag) preview.getListOrEmpty("tracks").getFirst();
		var children = group.getCompoundOrEmpty("data").getListOrEmpty("children");
		assertEquals("renamed", clip((CompoundTag) children.get(0)).getStringOr("animation", ""));
		assertEquals("first", clip((CompoundTag) children.get(1)).getStringOr("animation", ""));
		assertEquals("keep", clip((CompoundTag) children.get(0)).getStringOr("future", ""));
		assertEquals("first", document.library().getStringOr("defaultAnimation", ""));
		assertEquals(4, PhotonNamedAuthoringUtil.references(renamed, id).size());
	}

	@Test
	public void preservesIndependentSnapshotsAndRejectsIdentityReplacement() throws IOException {
		var source = fixture();
		var definition = PhotonNamedCodecUtil.read(source.library()).animations().getFirst();
		var replacement = definition.source();
		replacement.putBoolean("loop", true);
		replacement.putString("futureDefinition", "keep");
		var edited = PhotonNamedAuthoringUtil.replaceDefinition(source, definition.id(), replacement);
		replacement.putString("futureDefinition", "changed");
		assertEquals("keep", PhotonNamedCodecUtil.read(edited.library()).animations().getFirst().source().getStringOr("futureDefinition", ""));
		assertFalse(PhotonNamedCodecUtil.read(source.library()).animations().getFirst().loop());
		var copy = edited.library();
		copy.putString("future", "changed");
		assertEquals("keep", edited.library().getStringOr("future", ""));
		replacement.putString("id", UUID.randomUUID().toString());
		assertThrows(IllegalArgumentException.class, () -> PhotonNamedAuthoringUtil.replaceDefinition(source, definition.id(), replacement));
	}

	private static Document fixture() {
		var added = PhotonNamedAuthoringUtil.add(new Document(new CompoundTag(), null), "first");
		var library = added.library();
		library.putString("defaultAnimation", "first");
		library.putString("future", "keep");
		var rules = new ListTag();
		var rule = new CompoundTag();
		rule.putString("animation", "first");
		rule.putString("target", "first");
		rule.putString("name", "first");
		rule.putString("future", "keep");
		rules.add(rule);
		library.put("rules", rules);
		var children = new ListTag();
		children.add(track("lobecorp_animation_call"));
		children.add(track("lobecorp_child_animation"));
		var data = new CompoundTag();
		data.put("children", children);
		var group = new CompoundTag();
		group.putString("type", "group");
		group.put("data", data);
		var tracks = new ListTag();
		tracks.add(group);
		var preview = PhotonProjectPreviewUtil.create();
		preview.put("tracks", tracks);
		return new Document(library, preview);
	}

	@Test
	public void permitsOnlyCurrentControllerWithSupportedNestedData() {
		var source = PhotonNamedAuthoringUtil.add(new Document(new CompoundTag(), null), "first");
		var data = new CompoundTag();
		data.putInt("version", 0);
		data.put("definition", source.library());
		data.putString("futureObject", "keep");
		var wrapper = new CompoundTag();
		wrapper.putString("type", "lobecorp_animation_controller");
		wrapper.put("data", data);
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper.putString("futureWrapper", "unsupported");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper.remove("futureWrapper");
		data.put("version", FloatTag.valueOf(0));
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		data.putInt("version", 1);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		data.putInt("version", 0);
		data.getCompoundOrEmpty("definition").putInt("version", 7);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		data.put("definition", source.library());
		var definition = (CompoundTag) data.getCompoundOrEmpty("definition").getListOrEmpty("animations").getFirst();
		var tracks = new ListTag();
		tracks.add(track("lobecorp_model_animation"));
		definition.put("tracks", tracks);
        assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		tracks.clear();
		tracks.add(track("audio"));
        assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
        tracks.clear();
        tracks.add(track("unsupported_future_track"));
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		definition.remove("tracks");
		data.putString("lobecorp$future", "unsupported");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		data.remove("lobecorp$future");
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		data.remove("definition");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
	}

	@Test
	public void editsExistingLibrariesWithoutChangingTracksOrOriginalFiles() throws Exception {
		var root = Path.of("").toAbsolutePath();
		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		int count = 0;
		for (var folder : new String[]{"src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks"}) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var file : paths.filter(Files::isRegularFile).sorted().toList()) {
					var bytes = Files.readAllBytes(file);
					count += inspect(PhotonResourceReader.read(file));
					assertArrayEquals(bytes, Files.readAllBytes(file));
				}
			}
		}

		assertTrue(count > 0);
		System.out.println("G1_NAMED_AUTHORING_LIBRARIES=" + count);
	}

	private static int inspect(PhotonResourceDocument document) throws IOException {
		int count = document.nbt() == null ? 0 : inspect(document.nbt());
		for (var child : document.entries()) {
			count += inspect(child);
		}

		return count;
	}

	private static int inspect(Tag tag) throws IOException {
		int count = 0;
		if (tag instanceof CompoundTag compound) {
			if (compound.getStringOr("type", "").equals("lobecorp_animation_controller")) {
				var raw = compound.getCompoundOrEmpty("data").getCompoundOrEmpty("definition");
				var snapshot = raw.copy();
				var source = new Document(raw, null);
				var names = PhotonNamedCodecUtil.read(raw).animations().stream().map(Definition::name).toList();
				for (var definition : PhotonNamedCodecUtil.read(raw).animations()) {
					String name = "authoring_probe";
					while (names.contains(name)) {
						name += "_";
					}

					var edited = PhotonNamedAuthoringUtil.rename(source, definition.id(), name);
					for (var current : PhotonNamedCodecUtil.read(edited.library()).animations()) {
						var original = PhotonNamedCodecUtil.read(raw).animations().stream().filter(value -> value.id().equals(current.id())).findFirst().orElseThrow().source();
						if (current.id().equals(definition.id())) {
							original.putString("name", name);
						}

						assertEquals(original, current.source());
					}
				}

				assertEquals(snapshot, raw);
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

	private static CompoundTag track(String type) {
		var clip = new CompoundTag();
		clip.putString("animation", "first");
		clip.putString("future", "keep");
		var clips = new ListTag();
		clips.add(clip);
		var data = new CompoundTag();
		data.put("clips", clips);
		var result = new CompoundTag();
		result.putString("type", type);
		result.put("data", data);
		return result;
	}

	private static CompoundTag clip(CompoundTag track) {
		return (CompoundTag) track.getCompoundOrEmpty("data").getListOrEmpty("clips").getFirst();
	}
}
