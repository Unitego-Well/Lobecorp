package org.unitego.lobecorp.client.photon.model.validation;

import org.joml.Vector2f;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Primitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;

import java.util.List;

/// 模组加载后的 Photon 模型快照检查，独立算法回归位于测试源集。
@SuppressWarnings("unused") // 供 IDEA 已加载环境的调试表达式调用。
public class PhotonModelRegression {
	private static int assertions;


	/// 模组扫描完成后检查 Photon 配置的大模型 NBT 保存，供 IDEA Data 调试表达式调用。
	public static int runLoaded() throws IOException {
		assertions = 0;
		largeSnapshotPayload();
		return assertions;
	}


	private static void largeSnapshotPayload() throws IOException {
		var asset = new PhotonModelAsset();
		PhotonModelEditUtil.addPrimitive(asset, Primitive.RING);
		String json = PhotonModelIOUtil.writeJson(asset);
		check(json.getBytes(StandardCharsets.UTF_8).length > 65535);
		var provider = HolderLookup.Provider.create(Stream.empty());
		var config = new PhotonModelEmitterConfig();
		config.renderer.getMaterials().clear();
		config.assetJson = json;
		config.duration = 137;
		var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
		config.serialize(output);
		var tag = output.buildResult();
		check(tag.get("assetJson") instanceof ByteArrayTag);
		var bytes = new ByteArrayOutputStream();
		NbtIo.write(tag, new DataOutputStream(bytes));
		var persisted = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		var restored = new PhotonModelEmitterConfig();
		restored.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, provider, persisted));
		check(restored.assetJson.equals(json));
		check(restored.duration == 137);
		check(PhotonModelIOUtil.readJson(restored.assetJson).faces.size() == asset.faces.size());
		var legacy = new CompoundTag();
		String previous = PhotonModelIOUtil.writeJson(triangle());
		legacy.putString("assetJson", previous);
		restored.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, provider, legacy));
		check(restored.assetJson.equals(previous));
	}


	private static PhotonModelAsset triangle() {
		var asset = new PhotonModelAsset();
		asset.vertices.addAll(List.of(new Vector3f(), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f)));
		var face = new Face();
		face.vertices = new int[]{0, 1, 2};
		face.uv = new Vector2f[]{new Vector2f(), new Vector2f(), new Vector2f()};
		asset.faces.add(face);
		return asset;
	}

	private static void check(boolean condition) {
		assertions++;

		if (!condition) {
			throw new AssertionError("Model assertion " + assertions);
		}
	}
}
