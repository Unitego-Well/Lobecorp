package org.unitego.lobecorp.util.photon.model;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelCodecTest {
	@Test
	public void preservesUnknownFieldsAndKeepsDefinitionImmutable() throws Exception {
		var text = "{\"version\":1,\"vertices\":[],\"faces\":[],\"future\":{\"keep\":true}}";
		var model = PhotonModelCodec.read(text);
		var tree = model.source();
		tree.remove("future");
		assertTrue(model.source().has("future"));
		assertThrows(UnsupportedOperationException.class, () -> model.vertices().clear());
		assertEquals("main", model.parts().getFirst().id());
	}

	@Test
	public void rejectsBrokenVersionsTopologyAndReferences() {
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":2}"));
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":1.5}"));
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":1,\"referenceLength\":0}"));
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":1,\"vertices\":[[1e1000,0,0]]}"));
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":1,\"parts\":[{\"id\":\"a\",\"parentId\":\"a\"}]}"));
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":1,\"vertices\":[[0,0,0]],\"faces\":[{\"vertices\":[0,0,0],\"uv\":[[0,0],[0,0],[0,0]]}]}"));
		assertThrows(IOException.class, () -> PhotonModelCodec.read("{\"version\":1,\"morphs\":[{\"id\":\"expand\",\"deltas\":{\"99\":[1,0,0]}}]}"));
	}

	@Test
	public void adaptsAllExistingEmbeddedModelsWithoutChangingSource() throws Exception {
		var root = root();
		int models = 0;
		boolean morph = false;

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx",
				"src/main/resources/assets/lobecorp/models/photon", "src/main/resources/fxpacks")) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					var before = Files.readAllBytes(path);
					var bindings = PhotonModelCodec.readEmbedded(PhotonResourceReader.read(path));
					models += bindings.size();
					morph |= bindings.stream().anyMatch(binding -> !binding.definition().morphs().isEmpty());
					assertArrayEquals(before, Files.readAllBytes(path), path.toString());
				}
			}
		}

		assertTrue(models > 0);
		assertTrue(morph);
		System.out.println("R0_MODEL_BINDINGS=" + models);
	}

	private static Path root() {
		var path = Path.of("").toAbsolutePath();

		while (path != null && !Files.isDirectory(path.resolve("src/main/resources/fxpacks"))) {
			path = path.getParent();
		}

		assertNotNull(path);
		return path;
	}
}
