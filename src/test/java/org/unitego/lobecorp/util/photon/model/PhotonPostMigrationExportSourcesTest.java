package org.unitego.lobecorp.util.photon.model;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.registry.photon.client.LcPhotonSpawnTypes;
import org.unitego.lobecorp.util.photon.PhotonFXResourceUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPostMigrationExportSourcesTest {
	@TempDir
	public Path directory;

	@Test
	public void fileExportReadsCurrentGeometryInsteadOfMatchingOldSnapshot() throws Exception {
		var path = directory.resolve("current.obj");
		Files.writeString(path, "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
		var config = new PhotonModelOutputConfig(false);
		config.source = PhotonModelOutputConfig.Source.FILE;
		config.file = path.toString();
		var original = PhotonModelSourceUtil.load(config);
		config.setAssetSnapshot(original.snapshot());
		config.snapshotSource = "FILE|null|" + config.file + "|false";
		Files.writeString(path, "v 0 0 0\nv 4 0 0\nv 0 1 0\nf 1 2 3\n");
		assertEquals(1, PhotonModelSourceUtil.load(config).definition().vertices().get(1).x());

		PhotonModelPackExportUtil.prepare(config);

		assertEquals(PhotonModelOutputConfig.Source.EMBEDDED, config.source);
		assertEquals("", config.file);
		assertNull(config.resource);
		assertEquals(4, PhotonModelSourceUtil.load(config).definition().vertices().get(1).x());
		Files.delete(path);
		assertEquals(4, PhotonModelSourceUtil.load(config).definition().vertices().get(1).x());
	}

	@Test
	public void embeddedExportDoesNotRequireFormerAuthoringFile() throws Exception {
		var config = new PhotonModelOutputConfig(false);
		config.setAssetSnapshot(new ByteArrayTag("""
				{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}]}
				""".getBytes(StandardCharsets.UTF_8)));
		config.file = directory.resolve("missing.obj").toString();
		PhotonModelPackExportUtil.prepare(config);
		assertEquals(PhotonModelOutputConfig.Source.EMBEDDED, config.source);
		assertEquals(3, PhotonModelSourceUtil.load(config).definition().vertices().size());
	}

	@Test
	public void nativeAndModelDefinitionReadersChooseTheSameResource() {
		var nativePath = Identifier.parse("test:fx/child.fx");
		var modelPath = Identifier.parse("test:fx/child.mfx");
		assertEquals(nativePath, PhotonFXResourceUtil.resolve(nativePath, Set.of(nativePath, modelPath)::contains));
		assertEquals(modelPath, PhotonFXResourceUtil.resolve(nativePath, Set.of(modelPath)::contains));
		assertEquals(modelPath, PhotonFXResourceUtil.resolve(nativePath, _ -> false));
		assertEquals(modelPath, PhotonFXResourceUtil.resolve(modelPath, _ -> false));
	}

	@Test
	public void nativeSourceTraversalIncludesCustomChildrenAndPreservesInput() throws Exception {
		var nativeId = Identifier.parse("test:native");
		var childId = Identifier.parse("test:child");
		var data = new CompoundTag();
		var objects = new ListTag();
		var custom = new CompoundTag();
		custom.putString("type", LcPhotonSpawnTypes.TYPE_NAME);
		var childData = new CompoundTag();
		var childConfig = new CompoundTag();
		childConfig.putString("source", "FX");
		childConfig.putString("fxLocation", childId.toString());
		childData.put("config", childConfig);
		custom.put("data", childData);
		custom.putString("future", "preserve");
		objects.add(custom);
		var project = new CompoundTag();
		project.putString("type", LcPhotonSpawnTypes.TYPE_NAME);
		var projectData = new CompoundTag();
		var projectConfig = new CompoundTag();
		projectConfig.putString("source", "PROJECT");
		projectConfig.putString("fxLocation", "test:unused");
		projectData.put("config", projectConfig);
		project.put("data", projectData);
		objects.add(project);
		var unknown = new CompoundTag();
		unknown.putString("source", "FX");
		unknown.putString("fxLocation", "unknown author text, not an identifier");
		objects.add(unknown);
		data.put("fxObjects", objects);
		var before = data.copy();
		var nativeSources = Set.of(nativeId);
		assertEquals(Set.of(nativeId, childId), PhotonModelPackExportUtil.instanceSources(data, nativeSources));
		assertEquals(before, data);
		assertEquals(Set.of(nativeId), nativeSources);
		childConfig.putString("fxLocation", "invalid id");
		assertThrows(IOException.class, () -> PhotonModelPackExportUtil.instanceSources(data, nativeSources));
	}
}
