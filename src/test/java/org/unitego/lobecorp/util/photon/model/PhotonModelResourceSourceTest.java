package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonParser;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 通过生产加载入口验证真实拆分资源；资源缺失直接失败，不跳过。
public class PhotonModelResourceSourceTest {
	private static final PackResources PACK = (PackResources) Proxy.newProxyInstance(
			PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class}, (_, method, _) -> {
				if (method.getName().equals("packId")) {
					return "test";
				}

				throw new UnsupportedOperationException(method.getName());
			});
	private static final String MODEL_PATH = "models/photon/the_queen_of_hatred/beam/";
	private static final List<String> MODELS = List.of("laser_body", "dust_column", "reverse_muzzle", "source_dust");

	@Test
	public void resourceLoadingRestoresAllFourModelsAndSnapshots() throws Exception {
		for (var name : MODELS) {
			var requests = new ArrayList<Identifier>();
			ResourceProvider resources = id -> {
				requests.add(id);
				return resource(id);
			};
			var loaded = PhotonModelSourceUtil.load(config(name), resources);
			var expected = fixture(name);
			assertDefinition(expected, loaded.definition());
			assertInstanceOf(ByteArrayTag.class, loaded.snapshot());
			assertDefinition(expected, PhotonModelAssetUtil.read(loaded.snapshot(), new CompoundTag()));
			assertEquals(List.of(id(name + ".obj"), id(name + ".mfx.nbt")), requests);
			var snapshot = JsonParser.parseString(new String(((ByteArrayTag) loaded.snapshot()).getAsByteArray(),
					StandardCharsets.UTF_8)).getAsJsonObject();

			for (var field : List.of("parts", "bones", "weights", "morphs", "clips", "materials")) {
				assertEquals(expected.source().get(field), snapshot.get(field), name + ":" + field);
			}
		}
	}

	@Test
	public void absentCompanionKeepsPlainObjBehavior() throws Exception {
		var config = config("laser_body");
		var loaded = PhotonModelSourceUtil.load(config, id -> id.equals(id("laser_body.obj")) ? resource(id) : Optional.empty());
		assertFalse(loaded.definition().faces().isEmpty());
		assertTrue(loaded.definition().bones().isEmpty());
		assertTrue(loaded.definition().clips().isEmpty());
	}

	@Test
	public void damagedCompanionFailsInsteadOfReturningGeometry() {
		var config = config("laser_body");
		var failure = assertThrows(IOException.class, () -> PhotonModelSourceUtil.load(config,
				id -> id.equals(id("laser_body.obj")) ? resource(id) : Optional.of(bytes(new byte[]{10, 0}))));
		assertTrue(failure.getMessage().contains("laser_body.mfx.nbt"));
	}

	@Test
	public void invalidCompanionFieldsFailInsteadOfDroppingAnimation() throws Exception {
		for (var raw : List.of("", "true", "{")) {
			var data = new CompoundTag();
			data.putString("raw_clips", raw);
			var encoded = nbt(data);
			var config = config("laser_body");
			assertThrows(IOException.class, () -> PhotonModelSourceUtil.load(config,
					id -> id.equals(id("laser_body.obj")) ? resource(id) : Optional.of(bytes(encoded))));
		}
	}

	@Test
	public void invalidModelWrapperFailsInsteadOfDroppingData() throws Exception {
		var data = new CompoundTag();
		data.putString("model", "invalid");
		var encoded = nbt(data);
		var config = config("laser_body");
		assertThrows(IOException.class, () -> PhotonModelSourceUtil.load(config,
				id -> id.equals(id("laser_body.obj")) ? resource(id) : Optional.of(bytes(encoded))));
	}

	@Test
	public void fileSourceDoesNotReadResourceCompanion(@TempDir Path directory) throws Exception {
		var file = directory.resolve("laser_body.obj");

		try (var input = PhotonModelResourceSourceTest.class.getResourceAsStream("/assets/lobecorp/" + MODEL_PATH + file.getFileName())) {
			assertNotNull(input);
			Files.copy(input, file);
		}

		var config = config("laser_body");
		config.source = PhotonModelOutputConfig.Source.FILE;
		config.file = file.toString();
		var loaded = PhotonModelSourceUtil.load(config, _ -> {
			throw new AssertionError("文件来源不应读取资源提供者。");
		});
		assertFalse(loaded.definition().faces().isEmpty());
		assertTrue(loaded.definition().bones().isEmpty());
	}

	private static void assertDefinition(PhotonModelDefinition expected, PhotonModelDefinition actual) {
		assertEquals(expected.referenceLength(), actual.referenceLength());
		assertEquals(expected.referenceWidth(), actual.referenceWidth());
		assertEquals(expected.forwardAxis(), actual.forwardAxis());
		assertEquals(expected.vertices().size(), actual.vertices().size());
		assertEquals(expected.faces().size(), actual.faces().size());
		assertEquals(expected.parts(), actual.parts());
		assertEquals(expected.bones(), actual.bones());
		assertEquals(expected.weights(), actual.weights());
		assertEquals(expected.morphs(), actual.morphs());
		assertEquals(expected.clips(), actual.clips());
		assertEquals(expected.materials(), actual.materials());
	}

	private static PhotonModelOutputConfig config(String name) {
		// 只验证资源来源，不初始化渲染材质；材质注册需要游戏环境。
		var config = new PhotonModelOutputConfig(false);
		config.source = PhotonModelOutputConfig.Source.RESOURCE;
		config.resource = id(name + ".obj");
		return config;
	}

	private static Identifier id(String file) {
		return Identifier.fromNamespaceAndPath("lobecorp", MODEL_PATH + file);
	}

	private static Optional<Resource> resource(Identifier id) {
		var path = "/assets/" + id.getNamespace() + "/" + id.getPath();
		var url = PhotonModelResourceSourceTest.class.getResource(path);

		if (url == null) {
			return Optional.empty();
		}

		return Optional.of(new Resource(PACK, url::openStream));
	}

	private static Resource bytes(byte[] data) {
		return new Resource(PACK, () -> new ByteArrayInputStream(data));
	}

	private static byte[] nbt(CompoundTag data) throws IOException {
		var output = new ByteArrayOutputStream();

		try (var stream = new DataOutputStream(output)) {
			NbtIo.write(data, stream);
		}

		return output.toByteArray();
	}

	private static PhotonModelDefinition fixture(String name) throws Exception {
		try (var input = PhotonModelResourceSourceTest.class.getResourceAsStream("/photon-model/" + name + ".json")) {
			assertNotNull(input, name);
			return PhotonModelCodec.read(JsonParser.parseString(new String(input.readAllBytes(),
					StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}
}

