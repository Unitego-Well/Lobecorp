package org.unitego.lobecorp.util.photon.model;

import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.ValueKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 在提交编辑结果或加载文件前验证引用、拓扑和有限数值。
public class PhotonModelValidationUtil {
	/// 骨骼绑定缩放不允许退化，以便可靠求逆；动画姿态允许零缩放，缩放值为无量纲比例。
	public static final float MIN_SCALE = 1.0e-6f;

	public static void validate(PhotonModelAsset asset) {
		require(asset != null && asset.version == PhotonModelAsset.FORMAT_VERSION, "version");
		finite(asset.referenceLength, "referenceLength");
		finite(asset.referenceWidth, "referenceWidth");
		require(asset.referenceLength > 0f && asset.referenceWidth > 0f, "referenceSize");
		require(asset.forwardAxis >= 0 && asset.forwardAxis < 3, "forwardAxis");
		require(asset.vertices != null && asset.faces != null && asset.parts != null && asset.bones != null
				&& asset.weights != null && asset.morphs != null && asset.clips != null
				&& asset.materials != null, "collections");

		asset.materials.forEach((id, material) -> require(id != null && !id.isBlank() && material != null
				&& material.texture != null && Identifier.tryParse(material.texture) != null
				&& material.tint != null && material.materialData != null, "materials"));
		asset.materials.values().forEach(material -> {
			if (!material.materialData.isEmpty()) {
				PhotonModelMaterialDataUtil.parse(material.materialData);
			}
		});

		asset.vertices.forEach(vertex -> finite(vertex, "vertices"));
		var partParents = new HashMap<String, String>();
		asset.parts.forEach(part -> {
			require(part != null, "parts");
			register(partParents, part.id, part.parentId, "parts");
			finite(part.pivot, "pivot");
			transform(part.transform);
			require(part.anchor != null && part.stretch != null, "partControl");
			finite(part.opacity, "opacity");
			require(part.opacity >= 0f && part.opacity <= 1f, "opacity");
			require(part.stretch.axis >= 0 && part.stretch.axis < 3, "stretch.axis");
			require(!part.stretch.enabled || part.stretch.axis == asset.forwardAxis, "stretch.forwardAxis");
			finite(part.stretch.start, "stretch.start");
			finite(part.stretch.end, "stretch.end");
			require(!part.stretch.enabled || part.stretch.end > part.stretch.start
					&& Float.isFinite(part.stretch.end - part.stretch.start), "stretch.interval");
		});
		hierarchy(partParents, "parts.parentId");
		var boneParents = new HashMap<String, String>();
		asset.bones.forEach(bone -> {
			require(bone != null, "bones");
			register(boneParents, bone.id, bone.parentId, "bones");
			bindTransform(bone.bind);
		});
		hierarchy(boneParents, "bones.parentId");
		validateFaces(asset, partParents.keySet());
		validateWeights(asset, boneParents.keySet());
		var morphIds = validateMorphs(asset);
		validateClips(asset, partParents.keySet(), boneParents.keySet(), morphIds);
	}

	private static void validateFaces(PhotonModelAsset asset, Set<String> partIds) {
		for (var face : asset.faces) {
			require(face != null && face.vertices != null && face.vertices.length >= 3, "face.vertices");
			require(face.uv != null && face.uv.length == face.vertices.length, "face.uv");
			require(face.normals != null && (face.normals.length == 0
					|| face.normals.length == face.vertices.length), "face.normals");
			require(partIds.contains(face.partId) && face.material != null, "face.partId");
			var indices = new HashSet<Integer>();

			for (int i = 0; i < face.vertices.length; i++) {
				int index = face.vertices[i];
				require(index >= 0 && index < asset.vertices.size() && indices.add(index), "face.index");
				finite(face.uv[i]);

				if (face.normals.length != 0) {
					finite(face.normals[i], "face.normals");
				}
			}

			PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);
		}
	}

	private static void validateWeights(PhotonModelAsset asset, Set<String> boneIds) {
		asset.weights.forEach((index, influences) -> {
			require(index != null && index >= 0 && index < asset.vertices.size() && influences != null,
					"weights.vertex");
			var ids = new HashSet<String>();
			float sum = 0f;

			for (var influence : influences) {
				require(influence != null && boneIds.contains(influence.boneId)
						&& ids.add(influence.boneId), "weights.boneId");
				finite(influence.value, "weights.value");
				require(influence.value >= 0f, "weights.value");
				sum += influence.value;
			}

			require(influences.isEmpty() || Float.isFinite(sum) && sum > 0f, "weights.sum");
		});
	}

	private static Set<String> validateMorphs(PhotonModelAsset asset) {
		var ids = new HashSet<String>();

		for (var morph : asset.morphs) {
			require(morph != null && morph.id != null && !morph.id.isBlank()
					&& ids.add(morph.id) && morph.deltas != null, "morph.id");
			finite(morph.weight, "morph.weight");
			morph.deltas.forEach((index, delta) -> {
				require(index != null && index >= 0 && index < asset.vertices.size(), "morph.topology");
				finite(delta, "morph.delta");
			});
		}

		return ids;
	}

	private static void validateClips(PhotonModelAsset asset, Set<String> parts, Set<String> bones,
	                                  Set<String> morphs) {
		var ids = new HashSet<String>();

		for (var clip : asset.clips) {
			require(clip != null && clip.id != null && !clip.id.isBlank() && ids.add(clip.id), "clip.id");
			finite(clip.duration, "clip.duration");
			require(clip.duration > 0f && clip.partTracks != null && clip.boneTracks != null
					&& clip.morphTracks != null && clip.opacityKeys != null, "clip");
			transformTracks(clip.partTracks, parts, clip.duration);
			transformTracks(clip.boneTracks, bones, clip.duration);
			clip.morphTracks.forEach((id, keys) -> {
				require(morphs.contains(id), "clip.morph");
				valueKeys(keys, clip.duration, false);
			});
			valueKeys(clip.opacityKeys, clip.duration, true);
		}
	}

	private static void transformTracks(Map<String, List<TransformKey>> tracks,
	                                    Set<String> targets, float duration) {
		tracks.forEach((id, keys) -> {
			require(targets.contains(id) && keys != null, "clip.target");
			float previous = -1f;

			for (var key : keys) {
				require(key != null, "clip.key");
				checkTime(key.time, previous, duration);
				transform(key.transform);
				previous = key.time;
			}
		});
	}

	private static void valueKeys(List<ValueKey> keys, float duration, boolean opacity) {
		require(keys != null, "clip.keys");
		float previous = -1f;

		for (var key : keys) {
			require(key != null, "clip.key");
			checkTime(key.time, previous, duration);
			finite(key.value, "clip.value");
			require(!opacity || key.value >= 0f && key.value <= 1f, "clip.opacity");
			previous = key.time;
		}
	}

	private static void checkTime(float time, float previous, float duration) {
		finite(time, "clip.time");
		require(time >= 0f && time <= duration && time > previous, "clip.timeOrder");
	}

	private static void register(Map<String, String> parents, String id, String parent, String field) {
		require(id != null && !id.isBlank() && parent != null && !parents.containsKey(id), field + ".id");
		parents.put(id, parent);
	}

	private static void hierarchy(Map<String, String> parents, String field) {
		for (String id : parents.keySet()) {
			var visited = new HashSet<String>();
			String current = id;

			while (!current.isEmpty()) {
				require(parents.containsKey(current) && visited.add(current), field);
				current = parents.get(current);
			}
		}
	}

	public static void transform(Transform transform) {
		require(transform != null, "transform");
		finite(transform.translation, "transform.translation");
		finite(transform.scale, "transform.scale");
		Quaternionf rotation = transform.rotation;
		require(rotation != null && Float.isFinite(rotation.x) && Float.isFinite(rotation.y)
						&& Float.isFinite(rotation.z) && Float.isFinite(rotation.w)
						&& Float.isFinite(rotation.lengthSquared()) && rotation.lengthSquared() > 0f,
				"transform.rotation");
	}

	private static void bindTransform(Transform transform) {
		transform(transform);
		require(Math.abs(transform.scale.x) > MIN_SCALE && Math.abs(transform.scale.y) > MIN_SCALE
				&& Math.abs(transform.scale.z) > MIN_SCALE, "bone.bind.scale");
	}

	private static void finite(Vector3f vector, String field) {
		require(vector != null && Float.isFinite(vector.x) && Float.isFinite(vector.y)
				&& Float.isFinite(vector.z), field);
	}

	private static void finite(Vector2f vector) {
		require(vector != null && Float.isFinite(vector.x) && Float.isFinite(vector.y), "face.uv");
	}

	private static void finite(float value, String field) {
		require(Float.isFinite(value), field);
	}

	public static void require(boolean condition, String field) {
		if (!condition) {
			throw new IllegalArgumentException(field);
		}
	}
}
