package org.unitego.lobecorp.util.photon.model.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;

/// 高级网格工具的独占候选数据；只读写当前模型原树，不提供旧格式入口。
public class PhotonModelEditMesh {
	public static final String DEFAULT_PART_ID = PhotonModelDefinition.DEFAULT_PART;
	public List<Vector3f> vertices = new ArrayList<>();
	public List<Face> faces = new ArrayList<>();
	public List<Part> parts = new ArrayList<>();
	public Map<Integer, List<Weight>> weights = new LinkedHashMap<>();
	public List<Morph> morphs = new ArrayList<>();
	private JsonObject source = new JsonObject();
	private final Map<Vector3f, JsonElement> vertexSources = new IdentityHashMap<>();

	public PhotonModelEditMesh() {
		source.addProperty("version", PhotonModelDefinition.VERSION);
		var part = new Part();
		part.id = DEFAULT_PART_ID;
		parts.add(part);
	}

	public static PhotonModelEditMesh read(JsonObject source) {
		try {
			var definition = PhotonModelCodec.read(source);
			var mesh = new PhotonModelEditMesh();
			mesh.source = source.deepCopy();
			mesh.parts.clear();
			for (int index = 0; index < definition.vertices().size(); index++) {
				var vertex = definition.vertices().get(index);
				var position = new Vector3f(vertex.x(), vertex.y(), vertex.z());
				mesh.vertices.add(position);
				mesh.vertexSources.put(position, source.getAsJsonArray("vertices").get(index).deepCopy());
			}
			for (int index = 0; index < definition.faces().size(); index++) {
				var value = definition.faces().get(index);
				var face = new Face();
				face.source = source.getAsJsonArray("faces").get(index).getAsJsonObject().deepCopy();
				face.vertices = value.vertices().stream().mapToInt(Integer::intValue).toArray();
				face.uv = value.uv().stream().map(v -> new Vector2f(v.x(), v.y())).toArray(Vector2f[]::new);
				face.normals = value.normals().stream().map(v -> new Vector3f(v.x(), v.y(), v.z())).toArray(Vector3f[]::new);
				face.partId = value.partId();
				face.material = value.material();
				mesh.faces.add(face);
			}
			for (int index = 0; index < definition.parts().size(); index++) {
				var value = definition.parts().get(index);
				var part = new Part();
				part.source = source.has("parts") ? source.getAsJsonArray("parts").get(index).getAsJsonObject().deepCopy() : new JsonObject();
				part.id = value.id();
				part.parentId = value.parentId();
				part.pivot.set(value.pivot().x(), value.pivot().y(), value.pivot().z());
				var transform = value.transform();
				part.transform.translation.set(transform.translation().x(), transform.translation().y(), transform.translation().z());
				part.transform.rotation.set(transform.rotation().x(), transform.rotation().y(), transform.rotation().z(), transform.rotation().w());
				part.transform.scale.set(transform.scale().x(), transform.scale().y(), transform.scale().z());
				mesh.parts.add(part);
			}
			definition.weights().forEach((vertex, values) -> {
				var list = new ArrayList<Weight>();
				for (int index = 0; index < values.size(); index++) {
					var weight = new Weight();
					weight.boneId = values.get(index).boneId();
					weight.value = values.get(index).value();
					weight.source = source.getAsJsonObject("weights").getAsJsonArray(Integer.toString(vertex)).get(index).getAsJsonObject().deepCopy();
					list.add(weight);
				}
				mesh.weights.put(vertex, list);
			});
			for (int index = 0; index < definition.morphs().size(); index++) {
				var value = definition.morphs().get(index);
				var morph = new Morph();
				morph.source = source.getAsJsonArray("morphs").get(index).getAsJsonObject().deepCopy();
				value.deltas().forEach((vertex, delta) -> {
					var position = new Vector3f(delta.x(), delta.y(), delta.z());
					morph.deltas.put(vertex, position);
					morph.deltaSources.put(position, morph.source.getAsJsonObject("deltas").get(Integer.toString(vertex)).deepCopy());
				});
				mesh.morphs.add(morph);
			}
			return mesh;
		} catch (IOException invalid) {
			throw new IllegalArgumentException("模型网格无法读取。", invalid);
		}
	}

	public PhotonModelEditMesh copy() {
		return read(document());
	}

	public void retainVertexSources(PhotonModelEditMesh mesh) {
		vertexSources.putAll(mesh.vertexSources);
		var retained = new IdentityHashMap<Vector3f, Boolean>();
		vertices.forEach(vertex -> retained.put(vertex, true));
		vertexSources.keySet().removeIf(vertex -> !retained.containsKey(vertex));
	}

	public void validate() {
		try {
			PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(document()));
		} catch (IOException invalid) {
			throw new IllegalArgumentException("网格操作产生无效模型。", invalid);
		}
	}

	public void removePartTracks(Set<String> ids) {
		var clips = source.getAsJsonArray("clips");
		if (clips != null) {
			for (var value : clips) {
				var tracks = value.getAsJsonObject().getAsJsonObject("partTracks");
				if (tracks != null) {
					ids.forEach(tracks::remove);
				}
			}
		}
	}

	public boolean hasPartTracks(String id) {
		var clips = source.getAsJsonArray("clips");
		return clips != null && clips.asList().stream().map(JsonElement::getAsJsonObject)
				.anyMatch(clip -> clip.has("partTracks") && clip.getAsJsonObject("partTracks").has(id)
						&& !clip.getAsJsonObject("partTracks").getAsJsonArray(id).isEmpty());
	}

	public JsonObject document() {
		var result = source.deepCopy();
		var points = new JsonArray();
		vertices.forEach(vertex -> points.add(preserveNumbers(vertexSources.get(vertex), vector(vertex))));
		result.add("vertices", points);
		var polygons = new JsonArray();
		for (var face : faces) {
			var value = face.source.deepCopy();
			var indices = new JsonArray();
			for (int vertex : face.vertices) {
				indices.add(vertex);
			}
			writeKnown(value, "vertices", indices);
			var uv = new JsonArray();
			for (var point : face.uv) {
				var pair = new JsonArray();
				pair.add(point.x);
				pair.add(point.y);
				uv.add(pair);
			}
			writeKnown(value, "uv", uv);
			var normals = new JsonArray();
			for (var normal : face.normals) {
				normals.add(vector(normal));
			}
			if (!normals.isEmpty() || value.has("normals")) {
				writeKnown(value, "normals", normals);
			}
			if (!face.partId.equals(DEFAULT_PART_ID) || value.has("partId")) {
				value.addProperty("partId", face.partId);
			}
			if (!face.material.isEmpty() || value.has("material")) {
				value.addProperty("material", face.material);
			}
			polygons.add(value);
		}
		result.add("faces", polygons);
		var groups = new JsonArray();
		for (var part : parts) {
			var value = part.source.deepCopy();
			value.addProperty("id", part.id);
			if (!part.parentId.isEmpty() || value.has("parentId")) {
				value.addProperty("parentId", part.parentId);
			}
			if (part.pivot.lengthSquared() != 0 || value.has("pivot")) {
				writeKnown(value, "pivot", vector(part.pivot));
			}
			var transform = value.has("transform") ? value.getAsJsonObject("transform") : new JsonObject();
			if (part.transform.translation.lengthSquared() != 0 || transform.has("translation")) {
				writeKnown(transform, "translation", vector(part.transform.translation));
			}
			if (!part.transform.rotation.equals(new Quaternionf()) || transform.has("rotation")) {
				var rotation = vector(new Vector3f(part.transform.rotation.x, part.transform.rotation.y, part.transform.rotation.z));
				rotation.add(part.transform.rotation.w);
				writeKnown(transform, "rotation", rotation);
			}
			if (!part.transform.scale.equals(new Vector3f(1)) || transform.has("scale")) {
				writeKnown(transform, "scale", vector(part.transform.scale));
			}
			if (!transform.isEmpty()) {
				value.add("transform", transform);
			}
			groups.add(value);
		}
        if (source.has("parts") || groups.size() != 1 || !parts.getFirst().id.equals(DEFAULT_PART_ID)
                || groups.get(0).getAsJsonObject().size() > 1) {
			result.add("parts", groups);
		}
		var influences = new JsonObject();
		weights.forEach((vertex, entries) -> {
			var list = new JsonArray();
			for (var weight : entries) {
				var value = weight.source.deepCopy();
				value.addProperty("boneId", weight.boneId);
				writeKnown(value, "value", new JsonPrimitive(weight.value));
				list.add(value);
			}
			influences.add(Integer.toString(vertex), list);
		});
		if (!influences.isEmpty() || source.has("weights")) {
			result.add("weights", influences);
		}
		if (!morphs.isEmpty() || source.has("morphs")) {
			var shapes = new JsonArray();
			for (var morph : morphs) {
				var value = morph.source.deepCopy();
				var deltas = new JsonObject();
				morph.deltas.forEach((vertex, delta) -> deltas.add(Integer.toString(vertex), preserveNumbers(morph.deltaSources.get(delta), vector(delta))));
				value.add("deltas", deltas);
				shapes.add(value);
			}
			result.add("morphs", shapes);
		}
		return result;
	}

	private static JsonArray vector(Vector3f value) {
		var result = new JsonArray();
		result.add(value.x);
		result.add(value.y);
		result.add(value.z);
		return result;
	}

	private static void writeKnown(JsonObject value, String key, JsonElement next) {
		value.add(key, preserveNumbers(value.get(key), next));
	}

	private static JsonElement preserveNumbers(JsonElement before, JsonElement next) {
		if (before != null && before.isJsonPrimitive() && next.isJsonPrimitive()
				&& before.getAsJsonPrimitive().isNumber() && next.getAsJsonPrimitive().isNumber()
				&& Float.compare(before.getAsFloat(), next.getAsFloat()) == 0) {
			return before.deepCopy();
		}
		if (before != null && before.isJsonArray() && next.isJsonArray() && before.getAsJsonArray().size() == next.getAsJsonArray().size()) {
			var result = new JsonArray();
			for (int index = 0; index < next.getAsJsonArray().size(); index++) {
				result.add(preserveNumbers(before.getAsJsonArray().get(index), next.getAsJsonArray().get(index)));
			}
			return result;
		}
		return next;
	}

	public static Weight blendedWeight(List<Weight> entries, String bone, float value) {
		JsonObject metadata = null;
		Weight result = null;
		for (var entry : entries) {
			if (!bone.equals(entry.boneId)) {
				continue;
			}
			var extra = entry.source.deepCopy();
			extra.remove("boneId");
			extra.remove("value");
			if (metadata != null && !metadata.equals(extra)) {
				throw new IllegalArgumentException("不能合并含不一致扩展字段的顶点权重。");
			}
			metadata = extra;
			result = entry.copy();
		}
		if (result == null) {
			result = new Weight();
		}
		result.boneId = bone;
		result.value = value;
		return result;
	}

	public static boolean sameFaceMetadata(Face first, Face second) {
		var a = first.source.deepCopy();
		var b = second.source.deepCopy();
		for (var key : List.of("vertices", "uv", "normals", "partId", "material")) {
			a.remove(key);
			b.remove(key);
		}
		return a.equals(b);
	}

	public static class Transform {
		public Vector3f translation = new Vector3f();
		public Quaternionf rotation = new Quaternionf();
		public Vector3f scale = new Vector3f(1);
	}

	public static class Part {
		public String id = "";
		public String parentId = "";
		public Vector3f pivot = new Vector3f();
		public Transform transform = new Transform();
		private JsonObject source = new JsonObject();
	}

	public static class Face {
		public int[] vertices = new int[0];
		public Vector2f[] uv = new Vector2f[0];
		public Vector3f[] normals = new Vector3f[0];
		public String partId = DEFAULT_PART_ID;
		public String material = "";
		public JsonObject source = new JsonObject();

		public Face copy() {
			var result = new Face();
			result.source = source.deepCopy();
			result.vertices = vertices.clone();
			result.uv = Arrays.stream(uv).map(Vector2f::new).toArray(Vector2f[]::new);
			result.normals = Arrays.stream(normals).map(Vector3f::new).toArray(Vector3f[]::new);
			result.partId = partId;
			result.material = material;
			return result;
		}
	}

	public static class Weight {
		public String boneId = "";
		public float value;
		private JsonObject source = new JsonObject();

		public Weight copy() {
			var result = new Weight();
			result.boneId = boneId;
			result.value = value;
			result.source = source.deepCopy();
			return result;
		}
	}

	public static class Morph {
		public Map<Integer, Vector3f> deltas = new LinkedHashMap<>();
		private JsonObject source = new JsonObject();
		private final Map<Vector3f, JsonElement> deltaSources = new IdentityHashMap<>();
	}
}
