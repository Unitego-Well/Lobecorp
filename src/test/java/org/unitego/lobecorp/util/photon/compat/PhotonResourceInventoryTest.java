package org.unitego.lobecorp.util.photon.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonResourceInventoryTest {
	@TempDir
	public Path directory;

	@Test
	public void keepsSourcesWithSameNameDistinctAndRecordsFailures() throws Exception {
		var first = Files.createDirectory(directory.resolve("source"));
		var second = Files.createDirectory(directory.resolve("development"));
		Files.writeString(first.resolve("same.json"), "{\"source\":1}");
		Files.writeString(second.resolve("same.json"), "{\"source\":2}");
		Files.writeString(second.resolve("invalid.json"), "{\"same\":1,\"same\":2}");
		var manifest = PhotonResourceInventory.scan(List.of(new PhotonResourceInventory.Root("source", first),
				new PhotonResourceInventory.Root("development", second)), PhotonResourceLimits.DEFAULT);
		assertEquals(2, manifest.sources().size());
		assertEquals(1, manifest.failures().size());
		assertNotEquals(manifest.sources().get(0).root(), manifest.sources().get(1).root());
		assertNotEquals(manifest.sources().get(0).report().sha256(), manifest.sources().get(1).report().sha256());
		assertEquals("invalid.json", manifest.failures().getFirst().path());
		assertEquals("{\"source\":1}", Files.readString(first.resolve("same.json")));
	}

	@Test
	public void capsTheWholeInventoryAndReportsMissingRoot() throws Exception {
		Files.writeString(directory.resolve("first.json"), "{}");
		Files.writeString(directory.resolve("second.json"), "{}");
		var roots = List.of(new PhotonResourceInventory.Root("source", directory));
		assertThrows(IOException.class, () -> PhotonResourceInventory.scan(roots,
				new PhotonResourceLimits(4096, 4096, 4096, 10, 1, 100)));
		var missing = PhotonResourceInventory.scan(List.of(new PhotonResourceInventory.Root("missing",
				directory.resolve("not_found"))), PhotonResourceLimits.DEFAULT);
		assertTrue(missing.sources().isEmpty());
		assertEquals(1, missing.failures().size());
	}
}
