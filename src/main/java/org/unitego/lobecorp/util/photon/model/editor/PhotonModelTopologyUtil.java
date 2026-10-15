package org.unitego.lobecorp.util.photon.model.editor;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Weight;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 在编辑会话的候选资产上修改拓扑；提交和撤销由会话负责。
public class PhotonModelTopologyUtil {
	/// UV 接缝、法线与同一平面纹理映射的浮点比较容差。
	private static final float ATTRIBUTE_EPSILON = 1.0e-5f;
	private static final String INVALID = PhotonModelTextUtil.key("topology.invalid");

	public enum MergeMode {
		CENTER, FIRST, LAST, DISTANCE
	}

	/// 首末按选中集合的迭代顺序确定；按距离合并使用距离邻接的连通分组。
	public static void merge(PhotonModelAsset asset, Set<Integer> vertices, MergeMode mode, float distance) {
		require(vertices.size() >= 2 && mode != null);
		require(asset.morphs.isEmpty());
		requireSinglePart(asset, vertices);
		var groups = new ArrayList<List<Integer>>();

		if (mode == MergeMode.DISTANCE) {
			require(Float.isFinite(distance) && distance >= 0f);
			var pending = new LinkedHashSet<>(vertices);

			while (!pending.isEmpty()) {
				var group = new ArrayList<Integer>();
				int first = pending.getFirst();
				pending.remove(first);
				group.add(first);

				for (int i = 0; i < group.size(); i++) {
					var origin = asset.vertices.get(group.get(i));
					var iterator = pending.iterator();

					while (iterator.hasNext()) {
						int candidate = iterator.next();

						if (origin.distance(asset.vertices.get(candidate)) <= distance) {
							group.add(candidate);
							iterator.remove();
						}
					}
				}

				groups.add(group);
			}
		} else {
			groups.add(new ArrayList<>(vertices));
		}

		applyGroups(asset, groups, mode);
	}

	public static void collapse(PhotonModelAsset asset, Set<Edge> edges) {
		require(!edges.isEmpty() && asset.morphs.isEmpty());
		var adjacency = new LinkedHashMap<Integer, Set<Integer>>();

		for (var edge : edges) {
			var incident = incidentFaces(asset.faces, edge);
			require(!incident.isEmpty() && incident.size() <= 2);
			adjacency.computeIfAbsent(edge.first(), _ -> new LinkedHashSet<>()).add(edge.second());
			adjacency.computeIfAbsent(edge.second(), _ -> new LinkedHashSet<>()).add(edge.first());
		}

		requireSinglePart(asset, adjacency.keySet());
		var pending = new LinkedHashSet<>(adjacency.keySet());
		var groups = new ArrayList<List<Integer>>();

		while (!pending.isEmpty()) {
			var group = new ArrayList<Integer>();
			int first = pending.getFirst();
			pending.remove(first);
			group.add(first);

			for (int i = 0; i < group.size(); i++) {
				for (int vertex : adjacency.get(group.get(i))) {
					if (pending.remove(vertex)) {
						group.add(vertex);
					}
				}
			}

			groups.add(group);
		}

		applyGroups(asset, groups, MergeMode.CENTER);
	}

	private static void applyGroups(PhotonModelAsset asset, List<List<Integer>> groups, MergeMode mode) {
		int[] representative = new int[asset.vertices.size()];
		var positions = new ArrayList<>(asset.vertices);
		var weights = new LinkedHashMap<>(asset.weights);

		for (int i = 0; i < representative.length; i++) {
			representative[i] = i;
		}

		for (var group : groups) {
			if (group.size() < 2) {
				continue;
			}

			int target = mode == MergeMode.LAST ? group.getLast() : group.getFirst();
			var position = new Vector3f();

			if (mode == MergeMode.FIRST || mode == MergeMode.LAST) {
				position.set(asset.vertices.get(target));
			} else {
				group.forEach(vertex -> position.add(asset.vertices.get(vertex)));
				position.div(group.size());
				weights.put(target, averageWeights(asset, group));
			}

			positions.set(target, position);
			group.forEach(vertex -> representative[vertex] = target);
		}

		var faces = new ArrayList<Face>();

		for (var face : asset.faces) {
			boolean changed = Arrays.stream(face.vertices).anyMatch(vertex -> representative[vertex] != vertex
					|| !positions.get(vertex).equals(asset.vertices.get(vertex)));

			if (!changed) {
				faces.add(face.copy());
				continue;
			}

			var reduced = remapFace(face, representative);

			if (reduced.vertices.length < 3) {
				continue;
			}

			try {
				PhotonModelPolygonUtil.triangulate(positions, reduced.vertices);
				faces.add(reduced);
			} catch (IllegalArgumentException _) {
				// 合并导致原多边形离面时，沿原来的有效三角化保留剩余表面。
				for (var triangle : triangles(asset, face)) {
					var mapped = remapFace(triangle, representative);

					if (mapped.vertices.length >= 3) {
						PhotonModelPolygonUtil.triangulate(positions, mapped.vertices);
						faces.add(mapped);
					}
				}
			}
		}

		var compact = new ArrayList<Vector3f>();
		var compactWeights = new LinkedHashMap<Integer, List<Weight>>();
		int[] remap = new int[representative.length];

		for (int old = 0; old < representative.length; old++) {
			if (representative[old] == old) {
				remap[old] = compact.size();
				compact.add(positions.get(old));

				if (weights.containsKey(old)) {
					compactWeights.put(remap[old], new ArrayList<>(weights.get(old).stream().map(Weight::copy).toList()));
				}
			}
		}

		for (var face : faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				face.vertices[corner] = remap[face.vertices[corner]];
			}
		}

		asset.vertices = compact;
		asset.faces = faces;
		asset.weights = compactWeights;
	}

	private static List<Weight> averageWeights(PhotonModelAsset asset, List<Integer> group) {
		var values = new LinkedHashMap<String, Float>();
		boolean weighted = false;
		boolean unweighted = false;

		for (int vertex : group) {
			var influences = asset.weights.getOrDefault(vertex, List.of());
			float sum = 0f;

			for (var influence : influences) {
				sum += influence.value;
			}

			if (sum == 0f) {
				unweighted = true;
				continue;
			}

			weighted = true;

			for (var influence : influences) {
				values.merge(influence.boneId, influence.value / sum / group.size(), Float::sum);
			}
		}

		// 当前权重协议不能混合骨骼影响与未绑定的单位变换。
		require(!weighted || !unweighted);
		var result = new ArrayList<Weight>();
		values.forEach((bone, value) -> {
			var weight = new Weight();
			weight.boneId = bone;
			weight.value = value;
			result.add(weight);
		});
		return result;
	}

	private static Face remapFace(Face face, int[] representative) {
		var corners = new ArrayList<Integer>();
		var seen = new HashSet<Integer>();

		for (int corner = 0; corner < face.vertices.length; corner++) {
			int vertex = representative[face.vertices[corner]];

			if (!corners.isEmpty() && representative[face.vertices[corners.getLast()]] == vertex) {
				continue;
			}

			corners.add(corner);
		}

		if (corners.size() > 1 && representative[face.vertices[corners.getFirst()]]
				== representative[face.vertices[corners.getLast()]]) {
			corners.removeLast();
		}

		for (int corner : corners) {
			require(seen.add(representative[face.vertices[corner]]));
		}

		var result = copyCorners(face, corners);
		result.normals = new Vector3f[0];

		for (int i = 0; i < result.vertices.length; i++) {
			result.vertices[i] = representative[result.vertices[i]];
		}

		return result;
	}

	/// 只消除同部件、同材质、同平面且 UV 映射连续的流形内边。
	public static void dissolve(PhotonModelAsset asset, Set<Edge> edges) {
		require(!edges.isEmpty());
		var result = new ArrayList<>(asset.faces);

		for (var edge : edges) {
			var incident = incidentFaces(result, edge);
			require(incident.size() == 2);
			int first = incident.getFirst();
			int second = incident.getLast();
			var a = result.get(first);
			var b = result.get(second);
			require(Objects.equals(a.partId, b.partId) && Objects.equals(a.material, b.material));
			int startA = edgeCorner(a, edge);
			int startB = edgeCorner(b, edge);
			require(a.vertices[startA] == b.vertices[(startB + 1) % b.vertices.length]);
			requireCompatibleCorner(a, startA, b, (startB + 1) % b.vertices.length);
			requireCompatibleCorner(a, (startA + 1) % a.vertices.length, b, startB);
			var merged = joinFaces(a, startA, b, startB);
			PhotonModelPolygonUtil.triangulate(asset.vertices, merged.vertices);
			require(PhotonModelPolygonUtil.normal(asset.vertices, a.vertices)
					.dot(PhotonModelPolygonUtil.normal(asset.vertices, b.vertices)) > 1f - ATTRIBUTE_EPSILON);
			requireAffineUV(asset, a, merged);
			result.set(first, merged);
			result.remove(second);
		}

		asset.faces = result;
	}

	private static Face joinFaces(Face a, int startA, Face b, int startB) {
		var aCorners = new ArrayList<Integer>();

		for (int i = 1; i <= a.vertices.length; i++) {
			aCorners.add((startA + i) % a.vertices.length);
		}

		var result = copyCorners(a, aCorners);
		int count = a.vertices.length + b.vertices.length - 2;
		result.vertices = Arrays.copyOf(result.vertices, count);
		result.uv = Arrays.copyOf(result.uv, count);

		if (result.normals.length != 0) {
			result.normals = Arrays.copyOf(result.normals, count);
		}

		for (int i = 2; i < b.vertices.length; i++) {
			int corner = (startB + i) % b.vertices.length;
			int target = a.vertices.length + i - 2;
			result.vertices[target] = b.vertices[corner];
			result.uv[target] = new Vector2f(b.uv[corner]);

			if (result.normals.length != 0) {
				result.normals[target] = new Vector3f(b.normals[corner]);
			}
		}

		require(Arrays.stream(result.vertices).distinct().count() == count);
		return result;
	}

	private static void requireCompatibleCorner(Face a, int cornerA, Face b, int cornerB) {
		require(a.uv[cornerA].distance(b.uv[cornerB]) <= ATTRIBUTE_EPSILON);
		require((a.normals.length == 0) == (b.normals.length == 0));

		if (a.normals.length != 0) {
			require(a.normals[cornerA].distance(b.normals[cornerB]) <= ATTRIBUTE_EPSILON);
		}
	}

	private static void requireAffineUV(PhotonModelAsset asset, Face source, Face merged) {
		int[] triangle = PhotonModelPolygonUtil.triangulate(asset.vertices, source.vertices);
		int a = triangle[0];
		int b = triangle[1];
		int c = triangle[2];
		var origin = asset.vertices.get(source.vertices[a]);
		var first = new Vector3f(asset.vertices.get(source.vertices[b])).sub(origin);
		var second = new Vector3f(asset.vertices.get(source.vertices[c])).sub(origin);
		double xx = first.dot(first);
		double xy = first.dot(second);
		double yy = second.dot(second);
		double denominator = xx * yy - xy * xy;
		require(denominator > 0d);

		for (int corner = 0; corner < merged.vertices.length; corner++) {
			var relative = new Vector3f(asset.vertices.get(merged.vertices[corner])).sub(origin);
			double px = relative.dot(first);
			double py = relative.dot(second);
			float u = (float) ((px * yy - py * xy) / denominator);
			float v = (float) ((py * xx - px * xy) / denominator);
			var expected = new Vector2f(source.uv[b]).sub(source.uv[a]).mul(u)
					.add(new Vector2f(source.uv[c]).sub(source.uv[a]).mul(v)).add(source.uv[a]);
			require(expected.distance(merged.uv[corner]) <= ATTRIBUTE_EPSILON);
		}
	}

	/// 按选择顺序建立平面简单多边形；新 UV 按主平面归一化。
	public static void createFace(PhotonModelAsset asset, String partId, Set<Integer> orderedVertices) {
		require(orderedVertices.size() >= 3);
		require(asset.parts.stream().anyMatch(part -> Objects.equals(part.id, partId)));
		require(Objects.equals(requireSinglePart(asset, orderedVertices), partId));

		for (var existing : asset.faces) {
			require(existing.vertices.length != orderedVertices.size()
					|| !Arrays.stream(existing.vertices).allMatch(orderedVertices::contains));
		}

		var order = new ArrayList<>(orderedVertices);
		var face = new Face();
		face.partId = partId;
		asset.faces.stream().filter(existing -> Objects.equals(existing.partId, partId)).findFirst()
				.ifPresent(existing -> face.material = existing.material);
		face.vertices = order.stream().mapToInt(Integer::intValue).toArray();
		PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);
		int orientation = 0;

		for (int i = 0; i < face.vertices.length; i++) {
			var edge = Edge.of(face.vertices[i], face.vertices[(i + 1) % face.vertices.length]);
			var incident = incidentFaces(asset.faces, edge);
			require(incident.size() <= 1);

			if (!incident.isEmpty()) {
				var neighbor = asset.faces.get(incident.getFirst());
				int corner = edgeCorner(neighbor, edge);
				int nextOrientation = neighbor.vertices[corner] == face.vertices[i] ? 1 : -1;
				require(orientation == 0 || orientation == nextOrientation);

				if (orientation == 0) {
					face.material = neighbor.material;
				}

				orientation = nextOrientation;
			}
		}

		if (orientation > 0) {
			Collections.reverse(order);
			face.vertices = order.stream().mapToInt(Integer::intValue).toArray();
		}

		face.uv = projectedUV(asset, face.vertices);
		asset.faces.add(face);
	}

	private static Vector2f[] projectedUV(PhotonModelAsset asset, int[] vertices) {
		var normal = PhotonModelPolygonUtil.normal(asset.vertices, vertices).absolute();
		int dropped = normal.x >= normal.y && normal.x >= normal.z ? 0 : normal.y >= normal.z ? 1 : 2;
		int first = (dropped + 1) % 3;
		int second = (dropped + 2) % 3;
		var min = new Vector2f(Float.POSITIVE_INFINITY);
		var max = new Vector2f(Float.NEGATIVE_INFINITY);
		var result = new Vector2f[vertices.length];

		for (int i = 0; i < vertices.length; i++) {
			var vertex = asset.vertices.get(vertices[i]);
			result[i] = new Vector2f(vertex.get(first), vertex.get(second));
			min.min(result[i]);
			max.max(result[i]);
		}

		var span = max.sub(min);
		require(span.x > 0f && span.y > 0f);

		for (var uv : result) {
			uv.sub(min).div(span);
		}

		return result;
	}

	public static void triangulate(PhotonModelAsset asset, List<Integer> faces) {
		var selected = new HashSet<>(faces);
		require(!selected.isEmpty());
		selected.forEach(index -> require(index >= 0 && index < asset.faces.size()));
		var result = new ArrayList<Face>();

		for (int i = 0; i < asset.faces.size(); i++) {
			var face = asset.faces.get(i);

			if (selected.contains(i) && face.vertices.length > 3) {
				result.addAll(triangles(asset, face));
			} else {
				result.add(face);
			}
		}

		asset.faces = result;
	}

	private static List<Face> triangles(PhotonModelAsset asset, Face face) {
		int[] corners = PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);
		var result = new ArrayList<Face>();

		for (int i = 0; i < corners.length; i += 3) {
			result.add(copyCorners(face, List.of(corners[i], corners[i + 1], corners[i + 2])));
		}

		return result;
	}

	private static Face copyCorners(Face source, List<Integer> corners) {
		var face = new Face();
		face.partId = source.partId;
		face.material = source.material;
		face.vertices = new int[corners.size()];
		face.uv = new Vector2f[corners.size()];
		face.normals = new Vector3f[source.normals.length == 0 ? 0 : corners.size()];

		for (int i = 0; i < corners.size(); i++) {
			int corner = corners.get(i);
			face.vertices[i] = source.vertices[corner];
			face.uv[i] = new Vector2f(source.uv[corner]);

			if (face.normals.length != 0) {
				face.normals[i] = new Vector3f(source.normals[corner]);
			}
		}

		return face;
	}

	private static List<Integer> incidentFaces(List<Face> faces, Edge edge) {
		var result = new ArrayList<Integer>();

		for (int i = 0; i < faces.size(); i++) {
			if (edgeCorner(faces.get(i), edge) >= 0) {
				result.add(i);
			}
		}

		return result;
	}

	private static int edgeCorner(Face face, Edge edge) {
		for (int i = 0; i < face.vertices.length; i++) {
			if (Edge.of(face.vertices[i], face.vertices[(i + 1) % face.vertices.length]).equals(edge)) {
				return i;
			}
		}

		return -1;
	}

	private static String requireSinglePart(PhotonModelAsset asset, Set<Integer> vertices) {
		vertices.forEach(vertex -> require(vertex >= 0 && vertex < asset.vertices.size()));
		var owners = new HashSet<String>();

		for (var face : asset.faces) {
			if (Arrays.stream(face.vertices).anyMatch(vertices::contains)) {
				owners.add(face.partId);
			}
		}

		require(owners.size() == 1);
		return owners.iterator().next();
	}

	private static void require(boolean valid) {
		if (!valid) {
			throw new IllegalArgumentException(INVALID);
		}
	}
}
