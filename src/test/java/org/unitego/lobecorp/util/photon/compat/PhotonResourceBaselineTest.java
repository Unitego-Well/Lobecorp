package org.unitego.lobecorp.util.photon.compat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonResourceBaselineTest {
	@Test
	public void inventoriesCurrentSourceContainersWithoutRewriting() throws Exception {
		var root = projectRoot();
		var folders = List.of("src/main/resources/assets/lobecorp/fx",
				"src/main/resources/assets/lobecorp/models/photon", "src/main/resources/fxpacks");
		var sources = new ArrayList<Source>();
		int packageEntries = 0;

		for (var folder : folders) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					var original = Files.readAllBytes(path);
					var document = PhotonResourceReader.read(path);
					var report = PhotonResourceInspection.inspect(document);
					assertArrayEquals(original, document.bytes(), path.toString());
					assertArrayEquals(original, Files.readAllBytes(path), path.toString());
					assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)), report.sha256());
					assertFalse(report.runtimeReady());
					packageEntries += report.entries().size();
					sources.add(new Source(root.relativize(path).toString().replace('\\', '/'), report.kind().name(),
							report.sha256(), report.sourceBytes(), report.entries().size()));
				}
			}
		}

		assertTrue(sources.stream().anyMatch(source -> source.path().endsWith("counterattack.mfxproj")));
		assertTrue(sources.stream().anyMatch(source -> source.path().endsWith("laser_spell.fxproj")));
		assertTrue(packageEntries > 0);
		System.out.println("R0_SOURCE_BASELINE=" + new Gson().toJson(sources));
	}

	@Test
	public void keepsDifferentCounterattackAssetsAndExistingMorphsDistinct() throws Exception {
		var root = projectRoot().resolve("src/main/resources/assets/lobecorp");
		var model = PhotonResourceInspection.inspect(PhotonResourceReader.read(
				root.resolve("models/photon/the_queen_of_hatred/counterattack.lcmodel.json"))).models().getFirst();
		var project = PhotonResourceInspection.inspect(PhotonResourceReader.read(
				root.resolve("models/photon/the_queen_of_hatred/counterattack.mfxproj"))).models().getFirst();
		assertEquals(242, model.vertices());
		assertEquals(128, model.faces());
		assertEquals(2, model.parts());
		assertEquals(3, model.materials());
		assertEquals(121, project.vertices());
		assertEquals(224, project.faces());
		assertEquals(1, project.parts());

		for (var name : List.of("light_ball", "queen_star_beam")) {
			var report = PhotonResourceInspection.inspect(PhotonResourceReader.read(
					root.resolve("fx/the_queen_of_hatred/" + name + ".fxproj")));
			assertTrue(report.models().stream().anyMatch(value -> value.morphs() > 0), name);
			assertTrue(report.types().keySet().stream().anyMatch(PhotonResourceInspection::isLegacyType));
			assertFalse(report.legacyFields().isEmpty());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"source_assets", "source_packs", "development_assets", "development_packs", "pack_cache", "editor_state"})
	public void recordsCompleteSourceAndPackageEntries(String source) throws Exception {
		var path = switch (source) {
			case "source_assets" -> "src/main/resources/assets";
			case "source_packs" -> "src/main/resources/fxpacks";
			case "development_assets" -> "run/client/ldlib2/assets";
			case "development_packs" -> "run/client/photon/fxpacks";
			case "pack_cache" -> "run/client/photon/fxpack_cache";
			case "editor_state" -> "run/client/ldlib2/editor_layouts";
			default -> throw new IllegalArgumentException(source);
		};
		var manifest = PhotonResourceInventory.scan(List.of(new PhotonResourceInventory.Root(source,
				projectRoot().resolve(path))), PhotonResourceLimits.DEFAULT);
		assertTrue(manifest.failures().isEmpty(), manifest.failures().toString());
		var entries = new ArrayList<Entry>();

		for (var item : manifest.sources()) {
			flatten(item.path(), item.report().origin(), item.report(), entries);
		}

		System.out.println("R0_ROOT_BASELINE=" + new Gson().toJson(new RootManifest(source, path, entries)));
	}

	private static void flatten(String source, String root, PhotonResourceInspection.Report report, List<Entry> entries) {
		entries.add(new Entry(source, report.origin().equals(root) ? "" : report.origin().substring(root.length() + 2),
				report.kind().name(), report.sha256(), report.sourceBytes()));

		for (var child : report.entries()) {
			flatten(source, root, child, entries);
		}
	}

	private static Path projectRoot() {
		var current = Path.of("").toAbsolutePath();

		while (current != null && !Files.isDirectory(current.resolve("src/main/resources/fxpacks"))) {
			current = current.getParent();
		}

		assertNotNull(current, "测试必须由当前项目的IDEA运行。");
		return current;
	}

	public record Source(String path, String kind, String sha256, long bytes, int entries) {
	}

	public record Entry(String source, String entry, String kind, String sha256, long bytes) {
	}

	public record RootManifest(String root, String directory, List<Entry> entries) {
	}
}
