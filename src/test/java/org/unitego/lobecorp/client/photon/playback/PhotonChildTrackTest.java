package org.unitego.lobecorp.client.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildClip;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonChildTrackTest {
	@Test
	public void preservesClipIdentityUnknownFieldsAndIndependentCopies() {
		var raw = fixture();
		var original = raw.copy();
		var track = new PhotonChildTrack();
		track.readData(null, raw);
		assertEquals(original, track.writeData(null));
		var clip = (PhotonChildClip) track.clips().getFirst();
		var copied = clip.copy();
		assertEquals(clip.id, copied.id);
		assertEquals(0.5, clip.windowProgress(5));
		copied.animation = "changed";
		copied.source().putString("future", "changed");
		assertEquals("pulse", clip.animation);
		assertEquals("keep", clip.source().getStringOr("future", ""));
		clip.targetId(null);
		assertFalse(track.writeData(null).getListOrEmpty("clips").getCompoundOrEmpty(0).contains("clipTarget"));
		assertEquals(original, raw);
	}

	@Test
	public void rejectsBadIdentityTypesAndNonFiniteWindow() {
		var raw = fixture();
		raw.getListOrEmpty("clips").getCompoundOrEmpty(0).putString("id", "bad");
		assertThrows(IllegalArgumentException.class, () -> new PhotonChildTrack().readData(null, raw));
		var badNumber = fixture();
		badNumber.getListOrEmpty("clips").getCompoundOrEmpty(0).putDouble("duration", Double.NaN);
		assertThrows(IllegalArgumentException.class, () -> new PhotonChildTrack().readData(null, badNumber));
		var badType = fixture();
		badType.putString("mute", "false");
		assertThrows(IllegalArgumentException.class, () -> new PhotonChildTrack().readData(null, badType));
	}

	@Test
	public void roundTripsExistingChildTracksWithoutWritingAssets() throws Exception {
		var root = Path.of("").toAbsolutePath();

		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		int count = 0;

		for (var folder : new String[]{"src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks"}) {
			try (var files = Files.walk(root.resolve(folder))) {
				for (var path : files.filter(Files::isRegularFile).sorted().toList()) {
					var bytes = Files.readAllBytes(path);
					count += inspect(PhotonResourceReader.read(path));
					assertArrayEquals(bytes, Files.readAllBytes(path));
				}
			}
		}

		assertTrue(count > 0);
		System.out.println("G1_CHILD_TRACK_ROUNDTRIPS=" + count);
	}

	private static int inspect(PhotonResourceDocument document) throws IOException {
		int count = document.nbt() == null ? 0 : inspect(document.nbt());

		for (var child : document.entries()) {
			count += inspect(child);
		}

		return count;
	}

	private static int inspect(Tag value) {
		int count = 0;

		if (value instanceof CompoundTag tag) {
			String type = tag.getStringOr("type", "");

			if (type.equals("lobecorp_child_animation") || type.equals("lobecorp_animation_call")) {
				var raw = tag.getCompoundOrEmpty("data");
				var original = raw.copy();
				var track = new PhotonChildTrack();
				track.readData(null, raw);
				var roundTrip = track.writeData(null);
				assertSubset(original, roundTrip);
				assertEquals(original, raw);
				count++;
			}

			for (var entry : tag.entrySet()) {
				count += inspect(entry.getValue());
			}
		} else if (value instanceof ListTag list) {
			for (var entry : list) {
				count += inspect(entry);
			}
		}

		return count;
	}

	private static void assertSubset(Tag expected, Tag actual) {
		if (expected instanceof CompoundTag source && actual instanceof CompoundTag destination) {
			for (var entry : source.entrySet()) {
				assertSubset(entry.getValue(), destination.get(entry.getKey()));
			}
		} else if (expected instanceof ListTag source && actual instanceof ListTag destination) {
			assertEquals(source.size(), destination.size());

			for (int index = 0; index < source.size(); index++) {
				assertSubset(source.get(index), destination.get(index));
			}
		} else {
			assertEquals(expected, actual);
		}
	}

	private static CompoundTag fixture() {
		var clip = new CompoundTag();
		clip.putString("id", UUID.randomUUID().toString());
		clip.putDouble("start", 0);
		clip.putDouble("duration", 10);
		clip.putFloat("speed", 1);
		clip.putString("animation", "pulse");
		clip.putString("mode", "CONTROLLED");
		clip.putString("completion", "HOLD");
		clip.putDouble("playbackDuration", -1);
		clip.putBoolean("randomSeed", false);
		clip.putLong("seed", 0);
		clip.putString("future", "keep");
		var input = new CompoundTag();
		input.putString("target", "amplitude");
		input.putString("source", "");
		input.putBoolean("follow", false);
		input.putString("future", "input_keep");
		var values = new ListTag();
		values.add(FloatTag.valueOf(2));
		input.put("value", values);
		var inputs = new ListTag();
		inputs.add(input);
		clip.put("inputs", inputs);
		var clips = new ListTag();
		clips.add(clip);
		var track = new CompoundTag();
		track.putString("name", "");
		track.putBoolean("mute", false);
		track.putBoolean("lock", false);
		track.put("clips", clips);
		return track;
	}
}
