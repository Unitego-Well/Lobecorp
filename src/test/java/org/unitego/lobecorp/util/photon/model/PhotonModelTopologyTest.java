package org.unitego.lobecorp.util.photon.model;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelTopologyTest {
	@Test
	public void triangulatesConcaveFaceWithoutLosingCornerUvs() throws Exception {
		var model = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[2,0,0],[2,2,0],[1,1,0],[0,2,0]],
				"faces":[{"vertices":[0,1,2,3,4],"uv":[[0,0],[1,0],[1,1],[0.5,0.5],[0,1]]}]}
				""");
		var prepared = PhotonModelTopologyUtil.prepare(model);
		assertEquals(1, prepared.groups().size());
		var group = prepared.groups().getFirst();
		assertEquals(5, group.corners().size());
		assertEquals(9, group.indices().size());
		assertEquals(0.5f, model.faces().get(group.corners().get(3).face()).uv().get(group.corners().get(3).corner()).x());
		assertThrows(UnsupportedOperationException.class, () -> group.indices().clear());
	}

	@Test
	public void rejectsSelfIntersectionAndDegenerateFace() throws Exception {
		var crossing = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,1,0],[0,1,0],[1,0,0]],
				"faces":[{"vertices":[0,1,2,3],"uv":[[0,0],[1,1],[0,1],[1,0]]}]}
				""");
		assertThrows(IOException.class, () -> PhotonModelTopologyUtil.prepare(crossing));
		var line = PhotonModelCodec.read("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[2,0,0]],
				"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[2,0]]}]}
				""");
		assertThrows(IOException.class, () -> PhotonModelTopologyUtil.prepare(line));
	}

	@Test
	public void preparesExistingModelOccurrencesWithoutRewritingFiles() throws Exception {
		var root = Path.of("").toAbsolutePath();

		while (root != null && !Files.isDirectory(root.resolve("src/main/resources/fxpacks"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		int models = 0;

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx", "src/main/resources/assets/lobecorp/models/photon",
				"src/main/resources/fxpacks")) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					var original = Files.readAllBytes(path);

					for (var binding : PhotonModelCodec.readEmbedded(PhotonResourceReader.read(path))) {
						var prepared = PhotonModelTopologyUtil.prepare(binding.definition());
						assertFalse(prepared.groups().isEmpty(), binding.path());
						assertTrue(prepared.groups().stream().allMatch(group -> group.indices().size() % 3 == 0), binding.path());
						models++;
					}

					assertArrayEquals(original, Files.readAllBytes(path));
				}
			}
		}

		assertTrue(models > 0);
		System.out.println("R1_PREPARED_MODEL_OCCURRENCES=" + models);
	}
}
