package org.unitego.lobecorp.config.photon.model;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/// 当前版本模型的不可变定义；原JSON独立保留未知字段，播放姿态不写回定义。
public record PhotonModelDefinition(JsonObject source, float referenceLength, float referenceWidth, int forwardAxis,
                                    List<Vector3> vertices, List<Face> faces, List<Part> parts,
                                    Map<String, Material> materials, List<Bone> bones,
                                    Map<Integer, List<Weight>> weights, List<Morph> morphs, List<Clip> clips) {
	public static final int VERSION = 1;
	public static final String DEFAULT_PART = "main";
	/// 模型片段使用原文件的游戏tick单位。
	public static final float DEFAULT_CLIP_TICKS = 20;

	public PhotonModelDefinition {
		source = source.deepCopy();
		vertices = List.copyOf(vertices);
		faces = List.copyOf(faces);
		parts = List.copyOf(parts);
		materials = Map.copyOf(materials);
		bones = List.copyOf(bones);
		weights = immutableLists(weights);
		morphs = List.copyOf(morphs);
		clips = List.copyOf(clips);
	}

	@Override
	public JsonObject source() {
		return source.deepCopy();
	}

	private static <K, V> Map<K, List<V>> immutableLists(Map<K, List<V>> values) {
		var result = new LinkedHashMap<K, List<V>>();
		values.forEach((key, value) -> result.put(key, List.copyOf(value)));
		return Map.copyOf(result);
	}

	public record Vector2(float x, float y) {
	}

	public record Vector3(float x, float y, float z) {
		public static final Vector3 ZERO = new Vector3(0, 0, 0);
		public static final Vector3 ONE = new Vector3(1, 1, 1);
	}

	public record Rotation(float x, float y, float z, float w) {
		public static final Rotation IDENTITY = new Rotation(0, 0, 0, 1);
	}

	public record Transform(Vector3 translation, Rotation rotation, Vector3 scale) {
		public static final Transform IDENTITY = new Transform(Vector3.ZERO, Rotation.IDENTITY, Vector3.ONE);
	}

	public record Stretch(boolean enabled, int axis, float start, float end) {
	}

	public enum Anchor {
		NONE, START, END
	}

	public enum Tint {
		NONE, GRASS
	}

	public record Face(List<Integer> vertices, List<Vector2> uv, List<Vector3> normals, String partId,
	                   String material) {
		public Face {
			vertices = List.copyOf(vertices);
			uv = List.copyOf(uv);
			normals = List.copyOf(normals);
		}
	}

	public record Part(String id, String parentId, Vector3 pivot, Transform transform, Stretch stretch,
	                   Anchor anchor, float opacity, boolean shading, boolean doubleSided, boolean insideOut) {
	}

	public record Material(String texture, String materialData, Tint tint, boolean overlay) {
	}

	public record Bone(String id, String parentId, Transform bind) {
	}

	public record Weight(String boneId, float value) {
	}

	public record Morph(String id, Map<Integer, Vector3> deltas, float weight) {
		public Morph {
			deltas = Map.copyOf(deltas);
		}
	}

	public record TransformKey(float time, Transform transform) {
	}

	public record ValueKey(float time, float value) {
	}

	public record Clip(String id, float duration, boolean loop, Map<String, List<TransformKey>> partTracks,
	                   Map<String, List<TransformKey>> boneTracks, Map<String, List<ValueKey>> morphTracks,
	                   List<ValueKey> opacityKeys) {
		public Clip {
			partTracks = immutableLists(partTracks);
			boneTracks = immutableLists(boneTracks);
			morphTracks = immutableLists(morphTracks);
			opacityKeys = List.copyOf(opacityKeys);
		}
	}
}
