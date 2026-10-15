package org.unitego.lobecorp.util.photon.model.editor;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Weight;
import org.unitego.lobecorp.util.photon.model.PhotonModelPolygonUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 在单个部件的四边面带中预览和提交环切，预览不修改资产。
public class PhotonModelLoopCutUtil {
	/// 限制一次环切产生的细分量，避免交互输入造成过量网格分配。
	public static final int MAX_CUTS = 256;
	/// 比例传播的浮点误差容限，不用于合并几何顶点。
	private static final float RATIO_EPSILON = 1.0e-6f;

	public record Segment(Vector3f first, Vector3f second) {
		public Segment {
			first = new Vector3f(first);
			second = new Vector3f(second);
		}

		@Override
		public Vector3f first() {
			return new Vector3f(first);
		}

		@Override
		public Vector3f second() {
			return new Vector3f(second);
		}
	}

	/// 只暴露不可变预览数据；提交时核对相关拓扑，拒绝过期计划。
	public static class Plan {
		private final String partId;
		private final int vertexCount;
		private final int faceCount;
		private final Map<Edge, List<Float>> edgeCuts;
		private final List<FaceCut> faces;
		private final Map<Integer, Vector3f> positions;
		private final List<Segment> segments;

		private Plan(PhotonModelAsset asset, String partId, Map<Edge, List<Float>> edgeCuts,
		             List<FaceCut> faces, List<Segment> segments) {
			this.partId = partId;
			vertexCount = asset.vertices.size();
			faceCount = asset.faces.size();
			this.edgeCuts = Collections.unmodifiableMap(new LinkedHashMap<>(edgeCuts));
			this.faces = List.copyOf(faces);
			this.segments = List.copyOf(segments);
			positions = new LinkedHashMap<>();

			for (var edge : edgeCuts.keySet()) {
				positions.put(edge.first(), new Vector3f(asset.vertices.get(edge.first())));
				positions.put(edge.second(), new Vector3f(asset.vertices.get(edge.second())));
			}
		}

		public String partId() {
			return partId;
		}

		public List<Segment> segments() {
			return segments;
		}
	}

	private record FaceCut(int index, int corner, Face source, List<Float> fractions) {
	}

	/// ratio 为负数时按 count 个切口均分；否则沿起始边 first 到 second 的方向使用单个比例。
	public static Plan plan(PhotonModelAsset asset, String partId, Edge seed, float ratio, int count) {
		require(asset != null && partId != null && seed != null && Float.isFinite(ratio));
		require(asset.morphs.isEmpty());
		var fractions = fractions(ratio, count);
		var adjacency = adjacency(asset);
		seed = Edge.of(seed.first(), seed.second());
		require(adjacency.containsKey(seed));
		var edgeCuts = new LinkedHashMap<Edge, List<Float>>();
		var faces = new LinkedHashMap<Integer, FaceCut>();
		var pending = new ArrayDeque<Edge>();
		edgeCuts.put(seed, fractions);
		pending.add(seed);

		while (!pending.isEmpty()) {
			var edge = pending.removeFirst();
			var neighbors = adjacency.get(edge);
			require(neighbors != null && !neighbors.isEmpty() && neighbors.size() <= 2);

			for (int index : neighbors) {
				var face = asset.faces.get(index);
				require(Objects.equals(face.partId, partId) && face.vertices.length == 4);
				int corner = edgeCorner(face, edge);
				var existing = faces.get(index);

				if (existing != null) {
					require(corner == existing.corner || corner == (existing.corner + 2) % 4);
					continue;
				}

				PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices);
				var directed = orient(edgeCuts.get(edge), face.vertices[corner] != edge.first());
				faces.put(index, new FaceCut(index, corner, face.copy(), directed));
				var opposite = edgeAt(face, corner + 2);
				var propagated = orient(directed, face.vertices[(corner + 3) % 4] != opposite.first());
				var previous = edgeCuts.putIfAbsent(opposite, propagated);

				if (previous == null) {
					pending.add(opposite);
				} else {
					require(sameFractions(previous, propagated));
				}
			}
		}

		require(!faces.isEmpty());

		for (var entry : edgeCuts.entrySet()) {
			interpolateWeights(asset, entry.getKey(), entry.getValue().getFirst());
		}

		return new Plan(asset, partId, edgeCuts, new ArrayList<>(faces.values()), preview(asset, faces.values()));
	}

	private static List<Float> fractions(float ratio, int count) {
		if (ratio >= 0) {
			require(ratio > RATIO_EPSILON && ratio < 1 - RATIO_EPSILON);
			return List.of(ratio);
		}

		require(count > 0 && count <= MAX_CUTS);
		var result = new ArrayList<Float>(count);

		for (int cut = 1; cut <= count; cut++) {
			result.add((float) cut / (count + 1));
		}

		return List.copyOf(result);
	}

	private static Map<Edge, List<Integer>> adjacency(PhotonModelAsset asset) {
		var result = new LinkedHashMap<Edge, List<Integer>>();

		for (int index = 0; index < asset.faces.size(); index++) {
			var face = asset.faces.get(index);

			for (int corner = 0; corner < face.vertices.length; corner++) {
				result.computeIfAbsent(edgeAt(face, corner), _ -> new ArrayList<>()).add(index);
			}
		}

		return result;
	}

	private static Edge edgeAt(Face face, int corner) {
		return Edge.of(face.vertices[corner % face.vertices.length],
				face.vertices[(corner + 1) % face.vertices.length]);
	}

	private static int edgeCorner(Face face, Edge edge) {
		for (int corner = 0; corner < face.vertices.length; corner++) {
			if (edgeAt(face, corner).equals(edge)) {
				return corner;
			}
		}

		throw invalid();
	}

	private static List<Float> orient(List<Float> values, boolean reversed) {
		if (!reversed) {
			return values;
		}

		var result = new ArrayList<Float>(values.size());

		for (int index = values.size() - 1; index >= 0; index--) {
			result.add(1 - values.get(index));
		}

		return List.copyOf(result);
	}

	private static boolean sameFractions(List<Float> first, List<Float> second) {
		if (first.size() != second.size()) {
			return false;
		}

		for (int index = 0; index < first.size(); index++) {
			if (Math.abs(first.get(index) - second.get(index)) > RATIO_EPSILON) {
				return false;
			}
		}

		return true;
	}

	private static List<Segment> preview(PhotonModelAsset asset, Iterable<FaceCut> faces) {
		var result = new ArrayList<Segment>();

		for (var cut : faces) {
			var vertices = cut.source.vertices;
			int corner = cut.corner;

			for (float fraction : cut.fractions) {
				var first = new Vector3f(asset.vertices.get(vertices[corner]))
						.lerp(asset.vertices.get(vertices[(corner + 1) % 4]), fraction);
				var second = new Vector3f(asset.vertices.get(vertices[(corner + 3) % 4]))
						.lerp(asset.vertices.get(vertices[(corner + 2) % 4]), fraction);
				result.add(new Segment(first, second));
			}
		}

		return result;
	}

	/// 完整候选网格验证成功后才替换几何和权重；调用方负责统一历史事务。
	public static void apply(PhotonModelAsset asset, Plan plan) {
		require(asset != null && plan != null && asset.morphs.isEmpty());
		validateSnapshot(asset, plan);
		var candidate = asset.copy();
		var added = addVertices(candidate, plan.edgeCuts);
		var replacements = new LinkedHashMap<Integer, List<Face>>();

		for (var cut : plan.faces) {
			replacements.put(cut.index, splitFace(cut, plan.edgeCuts, added));
		}

		var faces = new ArrayList<Face>();

		for (int index = 0; index < candidate.faces.size(); index++) {
			var replacement = replacements.get(index);

			if (replacement == null) {
				faces.add(candidate.faces.get(index));
			} else {
				faces.addAll(replacement);
			}
		}

		candidate.faces = faces;
		candidate.validate();
		asset.vertices = candidate.vertices;
		asset.faces = candidate.faces;
		asset.weights = candidate.weights;
	}

	private static void validateSnapshot(PhotonModelAsset asset, Plan plan) {
		require(asset.vertices.size() == plan.vertexCount && asset.faces.size() == plan.faceCount);
		var expected = new HashSet<Integer>();
		plan.faces.forEach(cut -> expected.add(cut.index));
		var neighbors = adjacency(asset);

		for (var edge : plan.edgeCuts.keySet()) {
			var adjacent = neighbors.get(edge);
			require(adjacent != null && !adjacent.isEmpty() && adjacent.size() <= 2
					&& expected.containsAll(adjacent));
		}

		for (var entry : plan.positions.entrySet()) {
			require(entry.getValue().equals(asset.vertices.get(entry.getKey())));
		}

		for (var cut : plan.faces) {
			var current = asset.faces.get(cut.index);
			var source = cut.source;
			require(Objects.equals(current.partId, source.partId) && Objects.equals(current.material, source.material)
					&& Arrays.equals(current.vertices, source.vertices) && Arrays.equals(current.uv, source.uv)
					&& Arrays.equals(current.normals, source.normals));
		}
	}

	private static Map<Edge, List<Integer>> addVertices(PhotonModelAsset asset, Map<Edge, List<Float>> edgeCuts) {
		var result = new LinkedHashMap<Edge, List<Integer>>();

		for (var entry : edgeCuts.entrySet()) {
			var edge = entry.getKey();
			var indices = new ArrayList<Integer>();

			for (float ratio : entry.getValue()) {
				int added = asset.vertices.size();
				asset.vertices.add(new Vector3f(asset.vertices.get(edge.first()))
						.lerp(asset.vertices.get(edge.second()), ratio));
				var weights = interpolateWeights(asset, edge, ratio);

				if (!weights.isEmpty()) {
					asset.weights.put(added, weights);
				}

				indices.add(added);
			}

			result.put(edge, indices);
		}

		return result;
	}

	private static List<Weight> interpolateWeights(PhotonModelAsset asset, Edge edge, float ratio) {
		var values = new LinkedHashMap<String, Float>();
		var first = asset.weights.getOrDefault(edge.first(), List.of());
		var second = asset.weights.getOrDefault(edge.second(), List.of());
		// 空权重表示部件绑定，不能用归一化骨骼权重表达它与蒙皮之间的混合。
		require(first.isEmpty() == second.isEmpty());
		accumulateWeights(values, first, 1 - ratio);
		accumulateWeights(values, second, ratio);
		var result = new ArrayList<Weight>();
		values.forEach((bone, value) -> {
			if (value > 0) {
				var weight = new Weight();
				weight.boneId = bone;
				weight.value = value;
				result.add(weight);
			}
		});
		return result;
	}

	private static void accumulateWeights(Map<String, Float> result, List<Weight> source, float factor) {
		float sum = 0;

		for (var weight : source) {
			sum += weight.value;
		}

		if (source.isEmpty()) {
			return;
		}

		require(Float.isFinite(sum) && sum > 0);

		for (var weight : source) {
			result.merge(weight.boneId, weight.value / sum * factor, Float::sum);
		}
	}

	private static List<Face> splitFace(FaceCut cut, Map<Edge, List<Float>> edgeCuts,
	                                    Map<Edge, List<Integer>> added) {
		var result = new ArrayList<Face>();
		var bounds = new ArrayList<Float>();
		bounds.add(0f);
		bounds.addAll(cut.fractions);
		bounds.add(1f);

		for (int band = 0; band < bounds.size() - 1; band++) {
			float from = bounds.get(band);
			float to = bounds.get(band + 1);
			var face = new Face();
			face.partId = cut.source.partId;
			face.material = cut.source.material;
			int a = cut.corner;
			int b = (a + 1) % 4;
			int c = (a + 2) % 4;
			int d = (a + 3) % 4;
			face.vertices = new int[]{
					vertexAt(cut.source, a, b, from, edgeCuts, added),
					vertexAt(cut.source, a, b, to, edgeCuts, added),
					vertexAt(cut.source, d, c, to, edgeCuts, added),
					vertexAt(cut.source, d, c, from, edgeCuts, added)};
			face.uv = new Vector2f[]{uvAt(cut.source, a, b, from), uvAt(cut.source, a, b, to),
					uvAt(cut.source, d, c, to), uvAt(cut.source, d, c, from)};

			if (cut.source.normals.length != 0) {
				face.normals = new Vector3f[]{normalAt(cut.source, a, b, from), normalAt(cut.source, a, b, to),
						normalAt(cut.source, d, c, to), normalAt(cut.source, d, c, from)};
			}

			result.add(face);
		}

		return result;
	}

	private static int vertexAt(Face face, int first, int second, float ratio,
	                            Map<Edge, List<Float>> edgeCuts, Map<Edge, List<Integer>> added) {
		if (ratio == 0) {
			return face.vertices[first];
		}

		if (ratio == 1) {
			return face.vertices[second];
		}

		var edge = Edge.of(face.vertices[first], face.vertices[second]);
		float canonical = face.vertices[first] == edge.first() ? ratio : 1 - ratio;
		var fractions = edgeCuts.get(edge);

		for (int index = 0; index < fractions.size(); index++) {
			if (Math.abs(fractions.get(index) - canonical) <= RATIO_EPSILON) {
				return added.get(edge).get(index);
			}
		}

		throw invalid();
	}

	private static Vector2f uvAt(Face face, int first, int second, float ratio) {
		return new Vector2f(face.uv[first]).lerp(face.uv[second], ratio);
	}

	private static Vector3f normalAt(Face face, int first, int second, float ratio) {
		var normal = new Vector3f(face.normals[first]).lerp(face.normals[second], ratio);

		if (normal.lengthSquared() > 0) {
			normal.normalize();
		}

		return normal;
	}

	private static void require(boolean valid) {
		if (!valid) {
			throw invalid();
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException(PhotonModelTextUtil.key("loop_cut.invalid"));
	}
}
