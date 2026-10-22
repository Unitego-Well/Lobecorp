package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/// 编辑器几何操作只修改显式选中的原树数据，交由现有模型事务验证和提交。
public class PhotonModelWorkspaceEditUtil {
	public static void renamePart(JsonObject asset, String before, String after) {
		if (!after.matches("[a-z_]+")) {
			throw new IllegalArgumentException("部件名称只能包含小写字母和下划线。");
		}
		var parts = asset.getAsJsonArray("parts");
		for (var value : parts) {
			var part = value.getAsJsonObject();
			if (after.equals(text(part, "id")) && !before.equals(after)) {
				throw new IllegalArgumentException("部件名称已存在。");
			}
		}
		for (var value : parts) {
			var part = value.getAsJsonObject();
			if (before.equals(text(part, "id"))) {
				part.addProperty("id", after);
			}
			if (before.equals(text(part, "parentId"))) {
				part.addProperty("parentId", after);
			}
		}
		for (var value : asset.getAsJsonArray("faces")) {
			var face = value.getAsJsonObject();
			if (before.equals(facePart(face))) {
				face.addProperty("partId", after);
			}
		}
		var clips = asset.getAsJsonArray("clips");
		if (clips != null) {
			for (var value : clips) {
				var clip = value.getAsJsonObject();
				var tracks = clip.getAsJsonObject("partTracks");
				if (tracks != null && tracks.has(before)) {
					tracks.add(after, tracks.remove(before));
				}
			}
		}
	}

	public static void duplicatePart(JsonObject asset, String id) {
		var parts = asset.getAsJsonArray("parts");
		var source = parts.asList().stream().map(JsonElement::getAsJsonObject)
				.filter(part -> id.equals(text(part, "id"))).findFirst().orElseThrow();
		var name = available(parts, "part");
		var copy = source.deepCopy();
		copy.addProperty("id", name);
		parts.add(copy);
		var vertices = asset.getAsJsonArray("vertices");
		var faces = asset.getAsJsonArray("faces");
		Map<Integer, Integer> indices = new LinkedHashMap<>();
		for (var value : faces.deepCopy()) {
			var face = value.getAsJsonObject();
			if (!id.equals(facePart(face))) {
				continue;
			}
			var next = face.deepCopy();
			next.addProperty("partId", name);
			var corners = new JsonArray();
			for (var old : face.getAsJsonArray("vertices")) {
				int index = old.getAsInt();
				int target = indices.computeIfAbsent(index, key -> {
					int added = vertices.size();
					vertices.add(vertices.get(key).deepCopy());
					copyVertexMetadata(asset, key, added);
					return added;
				});
				corners.add(target);
			}
			next.add("vertices", corners);
			faces.add(next);
		}
	}

	private static void copyVertexMetadata(JsonObject asset, int source, int target) {
		var weights = asset.getAsJsonObject("weights");
		if (weights != null && weights.has(Integer.toString(source))) {
			weights.add(Integer.toString(target), weights.get(Integer.toString(source)).deepCopy());
		}
		var morphs = asset.getAsJsonArray("morphs");
		if (morphs != null) {
			for (var value : morphs) {
				var deltas = value.getAsJsonObject().getAsJsonObject("deltas");
				if (deltas != null && deltas.has(Integer.toString(source))) {
					deltas.add(Integer.toString(target), deltas.get(Integer.toString(source)).deepCopy());
				}
			}
		}
	}

	public static void removePart(JsonObject asset, String id) {
		var parts = asset.getAsJsonArray("parts");
		if (parts.asList().stream().anyMatch(value -> id.equals(text(value.getAsJsonObject(), "parentId")))) {
			throw new IllegalArgumentException("请先删除或重新挂接子部件。");
		}
		parts.asList().removeIf(value -> id.equals(text(value.getAsJsonObject(), "id")));
		asset.getAsJsonArray("faces").asList().removeIf(value -> id.equals(facePart(value.getAsJsonObject())));
		var clips = asset.getAsJsonArray("clips");
		if (clips != null) {
			for (var value : clips) {
				var tracks = value.getAsJsonObject().getAsJsonObject("partTracks");
				if (tracks != null) {
					tracks.remove(id);
				}
			}
		}
	}

	private static String facePart(JsonObject face) {
		return face.has("partId") ? face.get("partId").getAsString() : PhotonModelDefinition.DEFAULT_PART;
	}

	public static void primitive(JsonObject asset, boolean cube, String parent) {
		var parts = asset.has("parts") ? asset.getAsJsonArray("parts") : new JsonArray();
		if (!asset.has("parts") && !asset.getAsJsonArray("faces").isEmpty()) {
			var implicit = new JsonObject();
			implicit.addProperty("id", PhotonModelDefinition.DEFAULT_PART);
			parts.add(implicit);
		}
		var name = available(parts, cube ? "cube" : "plane");
		var part = new JsonObject();
		part.addProperty("id", name);
		part.addProperty("parentId", parent);
		part.add("pivot", JsonParser.parseString("[0,0,0]"));
		part.add("transform", JsonParser.parseString("{\"translation\":[0,0,0],\"rotation\":[0,0,0,1],\"scale\":[1,1,1]}"));
		part.addProperty("opacity", 1);
		part.addProperty("shading", false);
		part.addProperty("doubleSided", true);
		part.addProperty("insideOut", false);
		parts.add(part);
		asset.add("parts", parts);
		var vertices = asset.getAsJsonArray("vertices");
		int offset = vertices.size();
		var points = JsonParser.parseString(cube
				? "[[-0.5,-0.5,-0.5],[0.5,-0.5,-0.5],[0.5,0.5,-0.5],[-0.5,0.5,-0.5],[-0.5,-0.5,0.5],[0.5,-0.5,0.5],[0.5,0.5,0.5],[-0.5,0.5,0.5]]"
				: "[[-0.5,-0.5,0],[0.5,-0.5,0],[0.5,0.5,0],[-0.5,0.5,0]]").getAsJsonArray();
		points.forEach(value -> vertices.add(value.deepCopy()));
		var quads = JsonParser.parseString(cube ? "[[3,2,1,0],[4,5,6,7],[0,1,5,4],[2,3,7,6],[1,2,6,5],[4,7,3,0]]" : "[[0,1,2,3]]").getAsJsonArray();
		for (var value : quads) {
			var face = new JsonObject();
			var corners = new JsonArray();
			value.getAsJsonArray().forEach(index -> corners.add(offset + index.getAsInt()));
			face.add("vertices", corners);
			face.add("uv", JsonParser.parseString("[[0,0],[1,0],[1,1],[0,1]]"));
			face.addProperty("partId", name);
			face.addProperty("material", "");
			asset.getAsJsonArray("faces").add(face);
		}
	}

	private static String available(JsonArray parts, String prefix) {
		var name = new StringBuilder(prefix);
		while (true) {
			String candidate = name.toString();
			if (parts.asList().stream().noneMatch(value -> candidate.equals(text(value.getAsJsonObject(), "id")))) {
				return candidate;
			}
			name.append("_copy");
		}
	}

	private static String text(JsonObject value, String key) {
		return value.has(key) ? value.get(key).getAsString() : "";
	}
}
