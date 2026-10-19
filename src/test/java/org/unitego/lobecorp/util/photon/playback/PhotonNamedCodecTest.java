package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Parameter;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ParameterType;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonNamedCodecTest {
	@Test
	public void retainsUnknownFieldsAndIndependentSourceSnapshots() throws Exception {
		var source = library();
		source.putString("future", "keep");
		var original = source.copy();
		var parsed = PhotonNamedCodecUtil.read(source);
		parsed.source().putString("future", "changed");
		parsed.animations().getFirst().source().putString("name", "changed");
		assertEquals(original, parsed.source());
		assertEquals("pulse", parsed.animations().getFirst().name());
		assertThrows(UnsupportedOperationException.class, () -> parsed.animations().clear());
		assertThrows(UnsupportedOperationException.class, () -> parsed.animations().getFirst().parameters().clear());
	}

	@Test
	public void keepsParameterChannelQuantizationAndBounds() {
		var integer = new Parameter(UUID.randomUUID(), "i", ParameterType.INTEGER, List.of(3.8f), -10, 10);
		assertEquals(List.of(4f), integer.defaults());
		assertEquals(List.of(10f), integer.coerce(List.of(100f)));
		var extreme = new Parameter(UUID.randomUUID(), "large", ParameterType.INTEGER,
				List.of(Float.MAX_VALUE), Float.MAX_VALUE, Float.MAX_VALUE);
		assertEquals(List.of(PhotonNamedLibrary.MAX_EXACT_INTEGER), extreme.defaults());
		var color = new Parameter(UUID.randomUUID(), "c", ParameterType.COLOR, List.of(-1f, 0.2f, 1.5f, 1f), -10, 10);
		assertEquals(List.of(0f, 0.2f, 1f, 1f), color.defaults());
		assertThrows(IllegalArgumentException.class, () -> color.coerce(List.of(1f)));
	}

	@Test
	public void rejectsUnknownVersionDuplicateIdentityAndNonFiniteValues() throws Exception {
		var unsupported = library();
		unsupported.putInt("version", 5);
		assertThrows(IOException.class, () -> PhotonNamedCodecUtil.read(unsupported));
		var duplicate = library();
		duplicate.getListOrEmpty("animations").add(duplicate.getListOrEmpty("animations").getFirst().copy());
		assertThrows(IOException.class, () -> PhotonNamedCodecUtil.read(duplicate));
		var invalid = library();
		invalid.getListOrEmpty("animations").getCompound(0).orElseThrow().putDouble("duration", Double.NaN);
		assertThrows(IOException.class, () -> PhotonNamedCodecUtil.read(invalid));
	}

	@Test
	public void adaptsAllExistingControllerLibrariesWithoutChangingFiles() throws Exception {
		var root = Path.of("").toAbsolutePath();

		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		var totals = new LinkedHashMap<String, Integer>();

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks")) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					var original = Files.readAllBytes(path);
					inspect(PhotonResourceReader.read(path), totals);
					assertArrayEquals(original, Files.readAllBytes(path));
				}
			}
		}

		assertTrue(totals.getOrDefault("libraries", 0) > 0);
		System.out.println("G1_NAMED_WIRE_BASELINE=" + totals);
	}

	private static void inspect(PhotonResourceDocument document, Map<String, Integer> totals) throws IOException {
		if (document.nbt() != null) {
			inspect(document.nbt(), document.origin(), totals);
		}

		for (var child : document.entries()) {
			inspect(child, totals);
		}
	}

	private static void inspect(Tag value, String path, Map<String, Integer> totals) throws IOException {
		if (value instanceof CompoundTag tag) {
			if (tag.getStringOr("type", "").equals("lobecorp_emitter_spawner")) {
				var config = tag.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
				totals.merge("spawner", 1, Integer::sum);

				for (var key : new String[]{"source", "mode", "endBehavior", "follow", "includeChildren", "inheritRotation", "inheritScale", "inheritColor", "inheritVelocity", "clearOnRestart", "instanceLifetime"}) {
					totals.merge("spawner." + key + "=" + config.get(key), 1, Integer::sum);
				}

				var shape = config.getCompoundOrEmpty("shape");
				totals.merge("spawner.shape=" + shape, 1, Integer::sum);
				var parameters = config.getCompoundOrEmpty("parameters");

				for (var key : new String[]{"overrideLifetime", "overrideSize", "overrideColor", "overrideSpeed", "overrideBeamLength", "overrideBeamWidth", "playbackSpeed", "targetName"}) {
					totals.merge("spawner.parameters." + key + "=" + parameters.get(key), 1, Integer::sum);
				}
			}

			if (tag.getStringOr("type", "").equals("lobecorp_animation_controller")) {
				var raw = tag.getCompoundOrEmpty("data").getCompoundOrEmpty("definition");
				PhotonNamedLibrary library;

				try {
					library = PhotonNamedCodecUtil.read(raw);
				} catch (IOException failure) {
					throw new IOException(path, failure);
				}

				assertEquals(raw, library.source());
				totals.merge("libraries", 1, Integer::sum);
				totals.merge("animations", library.animations().size(), Integer::sum);
				totals.merge("rules", library.rules().size(), Integer::sum);
				library.rules().forEach(rule -> totals.merge("event." + rule.event(), 1, Integer::sum));

				for (var animation : library.animations()) {
					assertEquals(animation.source().getStringOr("id", ""), animation.id().toString());
					totals.merge("parameters", animation.parameters().size(), Integer::sum);
					totals.merge("bindings", animation.bindings().size(), Integer::sum);
					totals.merge("markers", animation.markers().size(), Integer::sum);
					totals.merge("autoDuration." + animation.autoDuration(), 1, Integer::sum);
					totals.merge("autoEnable." + animation.autoEnableModules(), 1, Integer::sum);
					totals.merge("completion." + animation.completion(), 1, Integer::sum);

					for (var track : animation.tracks()) {
						trackTypes(track, totals);
					}

					for (var binding : animation.bindings()) {
						totals.merge("source." + binding.source(), 1, Integer::sum);
					}

					for (var parameter : animation.parameters()) {
						totals.merge("parameter." + parameter.type(), 1, Integer::sum);
					}
				}
			}

			for (var entry : tag.entrySet()) {
				inspect(entry.getValue(), path + "/" + entry.getKey(), totals);
			}
		} else if (value instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				inspect(list.get(index), path + "/" + index, totals);
			}
		}
	}

	private static void trackTypes(Tag value, Map<String, Integer> totals) {
		if (value instanceof CompoundTag tag) {
			if (tag.get("type") != null) {
				totals.merge("type." + tag.getStringOr("type", "unknown"), 1, Integer::sum);
			}

			tag.entrySet().forEach(entry -> trackTypes(entry.getValue(), totals));
		} else if (value instanceof ListTag list) {
			list.forEach(child -> trackTypes(child, totals));
		}
	}

	private static CompoundTag library() {
		var animation = new CompoundTag();
		animation.putString("id", UUID.randomUUID().toString());
		animation.putString("name", "pulse");
		animation.putDouble("duration", 20);
		var parameter = new CompoundTag();
		parameter.putString("id", UUID.randomUUID().toString());
		parameter.putString("name", "amplitude");
		parameter.putString("type", "NUMBER");
		var defaults = new ListTag();
		defaults.add(FloatTag.valueOf(1));
		parameter.put("defaults", defaults);
		var parameters = new ListTag();
		parameters.add(parameter);
		animation.put("parameters", parameters);
		var animations = new ListTag();
		animations.add(animation);
		var source = new CompoundTag();
		source.putInt("version", 6);
		source.put("animations", animations);
		return source;
	}
}
