package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;

/// 固定基准负载的预算、布局和合成资产，独立于 FML 与游戏运行态。
public class PhotonBenchWorkloadUtil {
	private static final int MAX_MODELS = 64;
	private static final int MAX_GRID = 64;
	private static final float SPACING = 2.5f;

	public record Layout(int staticModels, int animatedModels, int grid, int beams) {
		public Layout {
			validateGrid(grid);
			if (staticModels < 0 || animatedModels < 0 || staticModels > MAX_MODELS
					|| animatedModels > MAX_MODELS || staticModels + animatedModels > MAX_MODELS
					|| beams != 0 && beams != 1 && beams != 4) {
				throw new IllegalArgumentException("模型基准负载超出预算。");
			}
		}

		public int models() {
			return staticModels + animatedModels;
		}

		public Vector3 offset(int index) {
			if (index < 0 || index >= models()) {
				throw new IndexOutOfBoundsException(index);
			}
			if (models() == 1) {
				return Vector3.ZERO;
			}

			int columns = models() > 16 ? 8 : 4;
			int rows = (models() + columns - 1) / columns;
			int row = index / columns;
			return new Vector3((index % columns - (columns - 1) / 2f) * SPACING, 0,
					(row - (rows - 1) / 2f) * SPACING);
		}
	}

	private static void validateGrid(int grid) {
		if (grid < 1 || grid > MAX_GRID) {
			throw new IllegalArgumentException("基准网格密度超出预算。");
		}
	}

	public static JsonObject asset(int meshGrid) {
		validateGrid(meshGrid);
		var asset = new JsonObject();
		asset.addProperty("version", 1);
		var vertices = new JsonArray();
		var faces = new JsonArray();
		var weights = new JsonObject();
		for (int y = 0; y <= meshGrid; y++) {
			for (int x = 0; x <= meshGrid; x++) {
				var vertex = new JsonArray();
				vertex.add((float) x / meshGrid - 0.5f);
				vertex.add((float) y / meshGrid - 0.5f);
				vertex.add(0);
				weights.add(Integer.toString(vertices.size()), JsonParser.parseString("[{\"boneId\":\"root\",\"value\":1}]"));
				vertices.add(vertex);
			}
		}
		for (int y = 0; y < meshGrid; y++) {
			for (int x = 0; x < meshGrid; x++) {
				int first = y * (meshGrid + 1) + x;
				var face = new JsonObject();
				var indices = new JsonArray();
				for (int index : new int[]{first, first + 1, first + meshGrid + 2, first + meshGrid + 1}) {
					indices.add(index);
				}
				face.add("vertices", indices);
				face.add("uv", JsonParser.parseString("[[0,0],[1,0],[1,1],[0,1]]"));
				faces.add(face);
			}
		}
		asset.add("vertices", vertices);
		asset.add("faces", faces);
		asset.add("weights", weights);
		asset.add("bones", JsonParser.parseString("[{\"id\":\"root\"}]"));
		asset.add("clips", JsonParser.parseString("""
				[{"id":"turn","duration":40,"loop":true,"boneTracks":{"root":[
				{"time":0,"transform":{}},{"time":20,"transform":{"rotation":[0,0.38268343,0,0.9238795]}},
				{"time":40,"transform":{}}]}}]
				"""));
		return asset;
	}
}
