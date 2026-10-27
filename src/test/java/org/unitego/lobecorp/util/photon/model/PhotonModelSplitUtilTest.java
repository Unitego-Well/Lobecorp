package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 验证「OBJ 几何 + 附加 NBT」拆分后再合并，与原始模型资产语义一致。
///
/// 这些用例直接构造最小资产，不依赖外部 .fx，保证回归可在无游戏环境下运行。
public class PhotonModelSplitUtilTest {
	private static JsonObject minimalAsset() {
		var asset = new JsonObject();
		asset.addProperty("version", 1);
		asset.addProperty("referenceLength", 12.0f);
		asset.addProperty("referenceWidth", 0.96f);
		asset.addProperty("forwardAxis", 2);

		var vertices = new JsonArray();
		vertices.add(vector(0, 0, 0));
		vertices.add(vector(1, 0, 0));
		vertices.add(vector(1, 1, 0));
		vertices.add(vector(0, 1, 0));
		asset.add("vertices", vertices);

		var faces = new JsonArray();
		var face = new JsonObject();
		face.add("vertices", integers(0, 1, 2, 3));
		var uv = new JsonArray();
		uv.add(vector2(0, 0));
		uv.add(vector2(1, 0));
		uv.add(vector2(1, 1));
		uv.add(vector2(0, 1));
		face.add("uv", uv);
		face.addProperty("partId", "body");
		face.addProperty("material", "glow");
		faces.add(face);
		asset.add("faces", faces);

		var materials = new JsonObject();
		var glow = new JsonObject();
		glow.addProperty("texture", "photon:textures/particle/white.png");
		glow.addProperty("overlay", true);
		materials.add("glow", glow);
		asset.add("materials", materials);

		var parts = new JsonArray();
		var part = new JsonObject();
		part.addProperty("id", "body");
		parts.add(part);
		asset.add("parts", parts);

		var bones = new JsonArray();
		var bone = new JsonObject();
		bone.addProperty("id", "root");
		bones.add(bone);
		asset.add("bones", bones);

		var clips = new JsonArray();
		var clip = new JsonObject();
		clip.addProperty("id", "beam");
		clip.addProperty("duration", 200);
		clips.add(clip);
		asset.add("clips", clips);

		return asset;
	}

	@Test
	public void geometryFromObjSurvivesMergeWithData() throws Exception {
		var asset = minimalAsset();
		var obj = PhotonModelObjUtil.write(asset, false);
		var data = dataOf(asset);
		var merged = PhotonModelSplitUtil.merge(obj, data, false);

		assertEquals(1, merged.get("version").getAsInt());
		assertEquals(4, merged.getAsJsonArray("vertices").size(), "OBJ 必须完整带回四个顶点。");
		assertEquals(1, merged.getAsJsonArray("faces").size(), "OBJ 必须完整带回一个面。");

		var face = merged.getAsJsonArray("faces").get(0).getAsJsonObject();
		assertEquals("body", face.get("partId").getAsString());
		assertEquals("glow", face.get("material").getAsString());
		assertEquals(4, face.getAsJsonArray("uv").size(), "面角 UV 必须保留。");
	}

	@Test
	public void dataRestoresNonGeometryFields() throws Exception {
		var asset = minimalAsset();
		var obj = PhotonModelObjUtil.write(asset, false);
		var merged = PhotonModelSplitUtil.merge(obj, dataOf(asset), false);

		assertEquals(12.0f, merged.get("referenceLength").getAsFloat());
		assertEquals(0.96f, merged.get("referenceWidth").getAsFloat());
		assertEquals(2, merged.get("forwardAxis").getAsInt());
		assertTrue(merged.has("bones"), "骨骼必须由附加数据恢复。");
		assertTrue(merged.has("clips"), "动画片段必须由附加数据恢复。");
		assertEquals("beam", merged.getAsJsonArray("clips").get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test
	public void materialTextureComesFromData() throws Exception {
		var asset = minimalAsset();
		var obj = PhotonModelObjUtil.write(asset, false);
		var merged = PhotonModelSplitUtil.merge(obj, dataOf(asset), false);
		var materials = merged.getAsJsonObject("materials");

		assertTrue(materials.has("glow"), "几何引用的材质必须存在。");
		assertEquals("photon:textures/particle/white.png",
				materials.getAsJsonObject("glow").get("texture").getAsString(),
				"材质纹理来自附加数据。");
		assertTrue(materials.getAsJsonObject("glow").get("overlay").getAsBoolean());
	}

	@Test
	public void mergedAssetParsesIntoDefinition() throws Exception {
		var asset = minimalAsset();
		var obj = PhotonModelObjUtil.write(asset, false);
		var merged = PhotonModelSplitUtil.merge(obj, dataOf(asset), false);
		var definition = PhotonModelCodec.read(merged);

		assertNotNull(definition);
		assertEquals(4, definition.vertices().size());
		assertEquals(1, definition.faces().size());
		assertEquals(12.0f, definition.referenceLength());
		assertFalse(definition.clips().isEmpty(), "合并后必须仍能读到动画片段。");
	}

	@Test
	public void dataPathFollowsSiblingConvention() {
		assertEquals("assets/x/body.mfx.nbt", PhotonModelSplitUtil.dataPath("assets/x/body.obj"));
		assertEquals("body.mfx.nbt", PhotonModelSplitUtil.dataPath("body"));
	}

	@Test
	public void mergeWithoutDataKeepsGeometryOnly() throws Exception {
		var obj = PhotonModelObjUtil.write(minimalAsset(), false);
		var merged = PhotonModelSplitUtil.merge(obj, null, false);

		assertEquals(4, merged.getAsJsonArray("vertices").size());
		assertFalse(merged.has("bones"), "没有附加数据时不应凭空产生骨骼。");
	}

	/// 复刻拆分工具写出的 NBT 结构，避免测试依赖 Python 产出的二进制文件。
	private static net.minecraft.nbt.CompoundTag dataOf(JsonObject asset) {
		var root = new net.minecraft.nbt.CompoundTag();
		root.putInt("version", asset.get("version").getAsInt());
		root.putFloat("referenceLength", asset.get("referenceLength").getAsFloat());
		root.putFloat("referenceWidth", asset.get("referenceWidth").getAsFloat());
		root.putInt("forwardAxis", asset.get("forwardAxis").getAsInt());

		var materials = new net.minecraft.nbt.CompoundTag();
		var glow = new net.minecraft.nbt.CompoundTag();
		glow.putString("texture", "photon:textures/particle/white.png");
		glow.putBoolean("overlay", true);
		materials.put("glow", glow);
		root.put("materials", materials);

		for (String key : List.of("parts", "bones", "clips")) {
			root.putString("raw_" + key, asset.get(key).toString());
		}

		var wrapper = new net.minecraft.nbt.CompoundTag();
		wrapper.put("model", root);
		return wrapper;
	}

	private static JsonArray vector(float x, float y, float z) {
		var result = new JsonArray();
		result.add(x);
		result.add(y);
		result.add(z);
		return result;
	}

	private static JsonArray vector2(float x, float y) {
		var result = new JsonArray();
		result.add(x);
		result.add(y);
		return result;
	}

	private static JsonArray integers(int... values) {
		var result = new JsonArray();
		for (int value : values) {
			result.add(value);
		}
		return result;
	}
}
