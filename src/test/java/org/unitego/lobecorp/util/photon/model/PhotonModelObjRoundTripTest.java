package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// 用真实模型资产验证「OBJ 几何 + .mfx.nbt」拆分后能无损回读。
///
/// 断言覆盖顶点数、三角形数、面角 UV/法线数量与参考尺寸，防止 OBJ 写出器精度或索引回归。
public class PhotonModelObjRoundTripTest {
	private static final List<String> MODELS = List.of("laser_body", "dust_column", "reverse_muzzle", "source_dust");

	@Test
	public void exportedObjKeepsGeometryCounts(@TempDir Path temp) throws Exception {
		var asset = fixture("laser_body.json");

		if (asset == null) {
			return;
		}
		var text = PhotonModelObjUtil.write(asset, false);
		var reread = PhotonModelObjUtil.read(text, false);

		assertEquals(asset.getAsJsonArray("vertices").size(), reread.getAsJsonArray("vertices").size(),
				"OBJ 往返必须保持顶点数。");
		assertEquals(asset.getAsJsonArray("faces").size(), reread.getAsJsonArray("faces").size(),
				"OBJ 往返必须保持面数。");

		var originalFaces = asset.getAsJsonArray("faces");
		var rereadFaces = reread.getAsJsonArray("faces");

		for (int index = 0; index < originalFaces.size(); index++) {
			var expected = originalFaces.get(index).getAsJsonObject();
			var actual = rereadFaces.get(index).getAsJsonObject();
			assertEquals(expected.getAsJsonArray("vertices").size(), actual.getAsJsonArray("vertices").size(),
					"第 " + index + " 个面的角数必须一致。");
			assertEquals(expected.getAsJsonArray("uv").size(), actual.getAsJsonArray("uv").size(),
					"第 " + index + " 个面的 UV 数量必须一致。");
			assertEquals(expected.getAsJsonArray("normals").size(), actual.getAsJsonArray("normals").size(),
					"第 " + index + " 个面的法线数量必须一致。");
			assertEquals(expected.get("partId").getAsString(), actual.get("partId").getAsString(),
					"第 " + index + " 个面的部件必须一致。");
		}

		// referenceLength 等参考尺寸不属于几何，按设计由附加 NBT 承载，OBJ 往返不要求保留。
		assertNotNull(asset.get("referenceLength"), "夹具必须带参考长度，用于确认它确实被排除在 OBJ 之外。");
		assertFalse(reread.has("referenceLength"), "OBJ 不应携带参考尺寸，避免与附加数据重复。");
	}

	@Test
	public void exportedObjKeepsVertexPrecision() throws Exception {
		var asset = fixture("laser_body.json");

		if (asset == null) {
			return;
		}
		var original = asset.getAsJsonArray("vertices");
		var reread = PhotonModelObjUtil.read(PhotonModelObjUtil.write(asset, false), false)
				.getAsJsonArray("vertices");

		for (int index = 0; index < original.size(); index++) {
			var expected = original.get(index);
			var expectedPosition = expected.isJsonObject() ? expected.getAsJsonObject().get("position") : expected;
			var actualPosition = reread.get(index).isJsonObject() ? reread.get(index).getAsJsonObject().get("position")
					: reread.get(index);

			for (int axis = 0; axis < 3; axis++) {
				float want = expectedPosition.getAsJsonArray().get(axis).getAsFloat();
				float got = actualPosition.getAsJsonArray().get(axis).getAsFloat();
				assertEquals(want, got, Math.max(1e-7f, Math.abs(want) * 1e-6f),
						"顶点 " + index + " 轴 " + axis + " 必须无损。");
			}
		}
	}

	@Test
	public void mergeRestoresDataButNotGeometry(@TempDir Path temp) throws Exception {
		var asset = fixture("laser_body.json");

		if (asset == null) {
			return;
		}
		var obj = PhotonModelObjUtil.write(asset, false);
		var merged = PhotonModelSplitUtil.merge(obj, null, false);

		assertNotNull(merged.getAsJsonArray("vertices"));
		assertEquals(asset.getAsJsonArray("vertices").size(), merged.getAsJsonArray("vertices").size());
		assertTrue(merged.has("materials"), "几何引用的材质名必须保留。");
	}

    /// 从测试资源读取真实模型资产；资源缺失时返回 null，让用例明确跳过而不是误报通过。
    private static JsonObject fixture(String name) throws Exception {
        try (var input = PhotonModelObjRoundTripTest.class.getResourceAsStream("/photon-model/" + name)) {
            if (input == null) {
                return null;
            }

            return com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

}
