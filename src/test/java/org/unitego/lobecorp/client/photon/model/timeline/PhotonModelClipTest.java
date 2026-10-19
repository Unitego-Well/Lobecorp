package org.unitego.lobecorp.client.photon.model.timeline;

import com.lowdragmc.photon.gui.editor.view.timeline.TrackType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelClipTest {
	private static final HolderLookup.Provider PROVIDER = HolderLookup.Provider.create(Stream.empty());

	@Test
	public void restoresAfterLaterClipAndKeepsLatestStartPrecedence() {
		var track = new PhotonModelClipTrack();
		var held = new PhotonModelClip(0, 20, 1);
		held.animation = "first";
		held.completion = PhotonModelClip.Completion.HOLD;
		var restored = new PhotonModelClip(5, 1, 1);
		restored.animation = "second";
		track.clips().addAll(List.of(held, restored));
		assertSame(held, track.selected(4));
		assertSame(restored, track.selected(5.5));
		assertNull(track.selected(7));
		var latest = new PhotonModelClip(5, 10, 1);
		track.clips().add(latest);
		assertSame(latest, track.selected(7));
	}

	@Test
	public void holdsLastLoopPoseWithoutWrappingBackToStart() {
		var clip = new PhotonModelClip(2, 10, 2);
		clip.offset = 1;
		assertEquals(1, clip.sampleTime(0));
		assertEquals(21, clip.sampleTime(100));
		var sample = new PhotonModelClip.Sample("cycle", 20, true, true, 0, 1);
		assertEquals(10, sample.assetTime(10, true));
		assertFalse(sample.effectiveLoop());
		var active = new PhotonModelClip.Sample("cycle", 20, true, false, 0, 1);
		assertEquals(20, active.assetTime(10, true));
		assertTrue(active.effectiveLoop());
	}

	@Test
	public void keepsUnknownFieldsUuidAndClipMetadataAcrossCopies() {
		var raw = data();
		var target = UUID.randomUUID();
		raw.store("target", UUIDUtil.CODEC, target);
		raw.putString("future_track", "keep");
		var clip = raw.getListOrEmpty("clips").getCompound(0).orElseThrow();
		clip.store("clipTarget", UUIDUtil.CODEC, UUID.randomUUID());
		clip.putString("future_clip", "keep_clip");
		var track = new PhotonModelClipTrack();
		track.setType(new TrackType(PhotonModelClipTrack.class, PhotonModelClipTrack::new, null));
		track.readData(PROVIDER, raw);
		var copied = (PhotonModelClipTrack) track.copy();
		((PhotonModelClip) copied.clips().getFirst()).offset = 3;
		var encoded = copied.writeData(PROVIDER);
		assertEquals(target, encoded.read("target", UUIDUtil.CODEC).orElseThrow());
		assertEquals("keep", encoded.getStringOr("future_track", ""));
		assertEquals("keep_clip", encoded.getListOrEmpty("clips").getCompound(0).orElseThrow().getStringOr("future_clip", ""));
		assertEquals(3, encoded.getListOrEmpty("clips").getCompound(0).orElseThrow().getDoubleOr("offset", 0));
		assertEquals(0, ((PhotonModelClip) track.clips().getFirst()).offset);
		assertEquals("keep_clip", clip.getStringOr("future_clip", ""));
		copied.targetId(null);
		copied.clips().getFirst().targetId(null);
		var unbound = copied.writeData(PROVIDER);
		assertFalse(unbound.contains("target"));
		assertFalse(unbound.getListOrEmpty("clips").getCompound(0).orElseThrow().contains("clipTarget"));
		assertTrue(raw.contains("target"));
	}

	@Test
	public void rejectsMalformedTimeUuidAndBooleanBeforeDecoding() {
		var invalidTime = data();
		invalidTime.getListOrEmpty("clips").getCompound(0).orElseThrow().putString("duration", "broken");
		assertThrows(IllegalArgumentException.class, () -> new PhotonModelClipTrack().readData(PROVIDER, invalidTime));
		var invalidTarget = data();
		invalidTarget.putString("target", "broken");
		assertThrows(IllegalArgumentException.class, () -> new PhotonModelClipTrack().readData(PROVIDER, invalidTarget));
		var invalidLoop = data();
		invalidLoop.getListOrEmpty("clips").getCompound(0).orElseThrow().putByte("loop", (byte) 2);
		assertThrows(IllegalArgumentException.class, () -> new PhotonModelClipTrack().readData(PROVIDER, invalidLoop));
	}

	@Test
	public void roundTripsEverySavedModelAnimationTrackWithoutChangingOriginals() throws Exception {
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
		System.out.println("G1_MODEL_TRACK_ROUNDTRIPS=" + count);
	}

	private static int inspect(PhotonResourceDocument document) {
		int count = document.nbt() == null ? 0 : inspect(document.nbt());

		for (var child : document.entries()) {
			count += inspect(child);
		}

		return count;
	}

	private static int inspect(Tag value) {
		int count = 0;

		if (value instanceof CompoundTag tag) {
			if (tag.getStringOr("type", "").equals("lobecorp_model_animation")) {
				var raw = tag.getCompoundOrEmpty("data");
				var track = new PhotonModelClipTrack();
				track.readData(PROVIDER, raw);
				var encoded = track.writeData(PROVIDER);

				for (var entry : raw.entrySet()) {
					assertEquals(entry.getValue(), encoded.get(entry.getKey()), entry.getKey());
				}

				count++;
			}

			for (var entry : tag.entrySet()) {
				count += inspect(entry.getValue());
			}
		} else if (value instanceof ListTag list) {
			for (var child : list) {
				count += inspect(child);
			}
		}

		return count;
	}

	private static CompoundTag data() {
		var root = new CompoundTag();
		var clip = new CompoundTag();
		clip.putDouble("start", 0);
		clip.putDouble("duration", 10);
		clip.putFloat("speed", 1);
		clip.putString("animation", "clip");
		clip.putDouble("offset", 0);
		clip.putBoolean("loop", false);
		clip.putString("completion", "RESTORE");
		var clips = new ListTag();
		clips.add(clip);
		root.put("clips", clips);
		return root;
	}
}
