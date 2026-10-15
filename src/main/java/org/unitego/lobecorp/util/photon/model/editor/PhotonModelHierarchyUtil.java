package org.unitego.lobecorp.util.photon.model.editor;

import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 删除模型部件并维护面、顶点、形状键和动画引用；由编辑会话统一记录历史。
public class PhotonModelHierarchyUtil {
	/// TRS 重建矩阵允许的相对浮点误差，用于拒绝无法无损保存的剪切变换。
	private static final float TRS_EPSILON = 0.00001f;

	public static void deletePart(PhotonModelAsset asset, String partId, boolean includeChildren) {
		asset.validate();
		var removed = asset.parts.stream().filter(part -> part.id.equals(partId)).findFirst()
				.orElseThrow(() -> failure("editor.hierarchy.missing_part"));
		var deletedIds = deletedIds(asset, partId, includeChildren);
		var promoted = includeChildren ? Map.<String, Transform>of() : promotedTransforms(asset, removed);
		var candidates = new LinkedHashSet<Integer>();

		for (var face : asset.faces) {
			if (deletedIds.contains(face.partId)) {
				for (int vertex : face.vertices) {
					candidates.add(vertex);
				}
			}
		}

		for (var part : asset.parts) {
			var transform = promoted.get(part.id);

			if (transform != null) {
				part.parentId = removed.parentId;
				part.transform = transform;
			}
		}

		asset.faces.removeIf(face -> deletedIds.contains(face.partId));

		for (var face : asset.faces) {
			for (int vertex : face.vertices) {
				candidates.remove(vertex);
			}
		}

		PhotonModelEditUtil.deleteVertices(asset, candidates);
		asset.parts.removeIf(part -> deletedIds.contains(part.id));
		asset.clips.forEach(clip -> clip.partTracks.keySet().removeAll(deletedIds));
	}

	private static Set<String> deletedIds(PhotonModelAsset asset, String partId, boolean includeChildren) {
		var result = new LinkedHashSet<String>();
		result.add(partId);

		if (!includeChildren) {
			return result;
		}

		boolean expanded;

		do {
			expanded = false;

			for (var part : asset.parts) {
				if (result.contains(part.parentId)) {
					expanded |= result.add(part.id);
				}
			}
		} while (expanded);

		return result;
	}

	private static Map<String, Transform> promotedTransforms(PhotonModelAsset asset, Part removed) {
		var children = asset.parts.stream().filter(part -> part.parentId.equals(removed.id)).toList();
		var result = new LinkedHashMap<String, Transform>();

		if (children.isEmpty()) {
			return result;
		}

		for (var clip : asset.clips) {
			if (!clip.partTracks.getOrDefault(removed.id, List.of()).isEmpty()
					|| children.stream().anyMatch(child -> !clip.partTracks.getOrDefault(child.id, List.of()).isEmpty())) {
				throw failure("editor.hierarchy.animated_parent");
			}
		}

		var parentLocal = localMatrix(removed);

		for (var child : children) {
			var pivot = child.pivot;
			var local = new Matrix4f().translation(-pivot.x, -pivot.y, -pivot.z)
					.mul(parentLocal).mul(localMatrix(child)).translate(pivot);
			result.put(child.id, decompose(local));
		}

		return result;
	}

	private static Matrix4f localMatrix(Part part) {
		var transform = part.transform;
		return new Matrix4f().translation(part.pivot)
				.mul(new Matrix4f().translationRotateScale(transform.translation,
						new Quaternionf(transform.rotation).normalize(), transform.scale))
				.translate(-part.pivot.x, -part.pivot.y, -part.pivot.z);
	}

	private static Transform decompose(Matrix4f matrix) {
		var result = new Transform();
		matrix.getTranslation(result.translation);
		matrix.getScale(result.scale);

		if (!matrix.isFinite() || result.scale.x == 0 || result.scale.y == 0 || result.scale.z == 0) {
			throw failure("editor.hierarchy.non_trs");
		}

		if (matrix.determinant() < 0) {
			result.scale.x = -result.scale.x;
		}

		new Matrix4f(matrix).scale(1 / result.scale.x, 1 / result.scale.y, 1 / result.scale.z)
				.getNormalizedRotation(result.rotation).normalize();
		var rebuilt = new Matrix4f().translationRotateScale(result.translation, result.rotation, result.scale);

		if (!rebuilt.isFinite()) {
			throw failure("editor.hierarchy.non_trs");
		}

		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				float source = matrix.get(column, row);
				float target = rebuilt.get(column, row);
				float tolerance = TRS_EPSILON * Math.max(1f, Math.max(Math.abs(source), Math.abs(target)));

				if (Math.abs(source - target) > tolerance) {
					throw failure("editor.hierarchy.non_trs");
				}
			}
		}

		return result;
	}

	private static IllegalArgumentException failure(String key) {
		return new IllegalArgumentException(Component.translatable(PhotonModelTextUtil.key(key)).getString());
	}
}
