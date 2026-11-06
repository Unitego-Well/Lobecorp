package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditMesh;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelHierarchyUtil;

/// 编辑器几何操作只修改显式选中的原树数据，交由现有模型事务验证和提交。
public class PhotonModelWorkspaceEditUtil {
	/// 缺省部件只作为编辑视图返回，只有显式部件编辑才写入原树。
	public static JsonArray parts(JsonObject asset) {
		if (asset.has("parts")) {
			return asset.getAsJsonArray("parts");
		}
		var result = new JsonArray();
		var part = new JsonObject();
		part.addProperty("id", PhotonModelDefinition.DEFAULT_PART);
		result.add(part);
		return result;
	}

	public static void renameNode(JsonObject asset, String kind, String before, String after) {
		if (!kind.equals("bones") && !kind.equals("morphs") || !after.matches("[a-z_]+")) {
			throw new IllegalArgumentException("骨骼或形变名称只能包含小写字母和下划线。");
		}
		var values = asset.getAsJsonArray(kind);
		var node = values.asList().stream().map(JsonElement::getAsJsonObject)
				.filter(value -> before.equals(text(value, "id"))).findFirst().orElseThrow();
		if (before.equals(after)) {
			return;
		}
		if (values.asList().stream().map(JsonElement::getAsJsonObject)
				.anyMatch(value -> after.equals(text(value, "id")))) {
			throw new IllegalArgumentException("名称已存在。");
		}
		node.addProperty("id", after);
		if (kind.equals("bones")) {
			for (var value : values) {
				var bone = value.getAsJsonObject();
				if (before.equals(text(bone, "parentId"))) {
					bone.addProperty("parentId", after);
				}
			}
			var weights = asset.getAsJsonObject("weights");
			if (weights != null) {
				for (var entries : weights.entrySet()) {
					for (var value : entries.getValue().getAsJsonArray()) {
						var influence = value.getAsJsonObject();
						if (before.equals(text(influence, "boneId"))) {
							influence.addProperty("boneId", after);
						}
					}
				}
			}
		}
		var clips = asset.getAsJsonArray("clips");
		if (clips != null) {
			for (var value : clips) {
				var tracks = value.getAsJsonObject().getAsJsonObject(kind.equals("bones") ? "boneTracks" : "morphTracks");
				if (tracks != null && tracks.has(before)) {
					tracks.add(after, tracks.remove(before));
				}
			}
		}
	}

	public static void removeNode(JsonObject asset, String kind, int index) {
		var values = asset.getAsJsonArray(kind);
		String id = values.get(index).getAsJsonObject().get("id").getAsString();
		if (kind.equals("bones")) {
			if (values.asList().stream().map(JsonElement::getAsJsonObject).anyMatch(value -> id.equals(text(value, "parentId")))) {
				throw new IllegalArgumentException("请先删除或重新挂接子骨骼。");
			}
			var weights = asset.getAsJsonObject("weights");
			if (weights != null) {
				for (var entries : weights.entrySet()) {
					entries.getValue().getAsJsonArray().asList().removeIf(value -> id.equals(text(value.getAsJsonObject(), "boneId")));
				}
			}
		}
		var clips = asset.getAsJsonArray("clips");
		if (clips != null) {
			for (var clip : clips) {
				var tracks = clip.getAsJsonObject().getAsJsonObject(kind.equals("bones") ? "boneTracks" : "morphTracks");
				if (tracks != null) {
					tracks.remove(id);
				}
			}
		}
		values.remove(index);
	}

	public static void removePart(JsonObject asset, String id, boolean includeChildren) {
		var candidate = PhotonModelEditMesh.read(asset);
		PhotonModelHierarchyUtil.deletePart(candidate, id, includeChildren);
		candidate.validate();
		var result = candidate.document();
		asset.asMap().clear();
		result.entrySet().forEach(entry -> asset.add(entry.getKey(), entry.getValue()));
	}

	public static void renameMaterial(JsonObject asset, String before, String after) {
		if (!after.matches("[a-z_]+") || asset.getAsJsonObject("materials").has(after) && !before.equals(after)) {
			throw new IllegalArgumentException("材质名称无效或已存在。");
		}
		var materials = asset.getAsJsonObject("materials");
		var value = materials.remove(before);
		if (value == null) {
			throw new IllegalArgumentException("材质不存在。");
		}
		materials.add(after, value);
		for (var face : asset.getAsJsonArray("faces")) {
			if (before.equals(text(face.getAsJsonObject(), "material"))) {
				face.getAsJsonObject().addProperty("material", after);
			}
		}
	}

	public static void removeMaterial(JsonObject asset, String name) {
		if (asset.getAsJsonArray("faces").asList().stream().anyMatch(face -> name.equals(text(face.getAsJsonObject(), "material")))) {
			throw new IllegalArgumentException("材质仍被面使用。");
		}
		asset.getAsJsonObject("materials").remove(name);
	}
	public static void renamePart(JsonObject asset, String before, String after) {
		if (!after.matches("[a-z_]+")) {
			throw new IllegalArgumentException("部件名称只能包含小写字母和下划线。");
		}
		var parts = parts(asset);
		asset.add("parts", parts);
		for (var value : parts) {
			var part = value.getAsJsonObject();
			if (after.equals(partId(part)) && !before.equals(after)) {
				throw new IllegalArgumentException("部件名称已存在。");
			}
		}
		for (var value : parts) {
			var part = value.getAsJsonObject();
			if (before.equals(partId(part))) {
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
		var parts = parts(asset);
		asset.add("parts", parts);
		var source = parts.asList().stream().map(JsonElement::getAsJsonObject)
				.filter(part -> id.equals(partId(part))).findFirst().orElseThrow();
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
		var clips = asset.getAsJsonArray("clips");
		if (clips != null) {
			for (var value : clips) {
				var tracks = value.getAsJsonObject().getAsJsonObject("partTracks");
				if (tracks != null && tracks.has(id)) {
					tracks.add(name, tracks.get(id).deepCopy());
				}
			}
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
		var parts = parts(asset);
		asset.add("parts", parts);
		if (parts.asList().stream().anyMatch(value -> id.equals(text(value.getAsJsonObject(), "parentId")))) {
			throw new IllegalArgumentException("请先删除或重新挂接子部件。");
		}
		parts.asList().removeIf(value -> id.equals(partId(value.getAsJsonObject())));
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

	public static String partId(JsonObject part) {
		return part.has("id") ? part.get("id").getAsString() : PhotonModelDefinition.DEFAULT_PART;
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
