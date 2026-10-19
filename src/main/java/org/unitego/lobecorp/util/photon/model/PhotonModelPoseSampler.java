package org.unitego.lobecorp.util.photon.model;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.ValueKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.ToDoubleFunction;

/// 单资产的纯CPU姿态求值器；准备结果可共享，每次求值只使用调用局部可变数据。
public class PhotonModelPoseSampler {
	private final PhotonModelTopologyUtil.Prepared prepared;
	private final int[] parentOrder;
	private final int[] parents;
	private final float minForward;
	private final float maxForward;
	private final boolean localControl;
	private final float minimumDelta;

	/// 时间沿用文件tick单位；片段为空表示基础姿态，Morph覆盖只作用于本次采样。
	public record Input(float time, String clip, boolean loop, float length, float width,
	                    Anchor anchor, float opacity, Map<String, Float> morphWeights) {
		public Input {
			Objects.requireNonNull(clip);
			Objects.requireNonNull(anchor);
			morphWeights = Map.copyOf(morphWeights);

			if (!Float.isFinite(time) || !Float.isFinite(length) || length < 0
					|| !Float.isFinite(width) || width < 0 || !Float.isFinite(opacity) || opacity < 0 || opacity > 1
					|| morphWeights.values().stream().anyMatch(value -> !Float.isFinite(value))) {
				throw new IllegalArgumentException("模型采样参数包含无效数值。");
			}
		}
	}

	public record Vertex(Vector3 position, Vector3 normal, Vector2 uv) {
	}

	public record Group(List<Vertex> vertices, float opacity) {
		public Group {
			vertices = List.copyOf(vertices);
		}
	}

	/// 仅CPU求值耗时，不包含读取、拓扑准备、排队、上传、GPU或帧总耗时。
	public record Timings(long curvesNanos, long deformationNanos, long normalsNanos) {
	}

	public record Frame(PhotonModelTopologyUtil.Prepared prepared, List<Group> groups, float opacity,
	                    Vector3 min, Vector3 max, @Nullable Timings timings) {
		public Frame {
			groups = List.copyOf(groups);
		}
	}

	public PhotonModelPoseSampler(PhotonModelTopologyUtil.Prepared prepared) throws IOException {
		this.prepared = prepared;
		var definition = prepared.definition();

		if (!definition.bones().isEmpty() || !definition.weights().isEmpty()) {
			throw new IOException("当前CPU模型路径尚未支持骨骼；保留定义并阻止错误播放。");
		}

		parents = new int[definition.parts().size()];
		var partIds = new HashMap<String, Integer>();

		for (int index = 0; index < parents.length; index++) {
			partIds.put(definition.parts().get(index).id(), index);
		}

		var ordered = new ArrayList<Integer>();
		var included = new boolean[parents.length];

		for (int index = 0; index < parents.length; index++) {
			parents[index] = partIds.getOrDefault(definition.parts().get(index).parentId(), -1);
		}

		for (int index = 0; index < parents.length; index++) {
			order(index, ordered, included);
		}

		parentOrder = ordered.stream().mapToInt(Integer::intValue).toArray();
		minForward = (float) definition.vertices().stream().mapToDouble(vertex -> component(vertex, definition.forwardAxis())).min().orElse(0);
		maxForward = (float) definition.vertices().stream().mapToDouble(vertex -> component(vertex, definition.forwardAxis())).max().orElse(0);
		localControl = definition.parts().stream().anyMatch(part -> part.stretch().enabled() || part.anchor() != Anchor.NONE);
		minimumDelta = (float) definition.parts().stream().filter(part -> part.stretch().enabled() && part.anchor() == Anchor.NONE)
				.mapToDouble(part -> part.stretch().start() - part.stretch().end()).max().orElse(-Double.MAX_VALUE);
	}

	private void order(int index, List<Integer> ordered, boolean[] included) {
		var path = new ArrayList<Integer>();

		for (int current = index; current >= 0 && !included[current]; current = parents[current]) {
			checkCancelled();
			path.add(current);
		}

		for (int current = path.size() - 1; current >= 0; current--) {
			int part = path.get(current);
			ordered.add(part);
			included[part] = true;
		}
	}

	public Frame sample(Input input, boolean measure) {
		checkCancelled();
		var definition = prepared.definition();

		for (var id : input.morphWeights().keySet()) {
			if (definition.morphs().stream().noneMatch(morph -> morph.id().equals(id))) {
				throw new IllegalArgumentException("模型形状键不存在：" + id);
			}
		}

		long begin = measure ? System.nanoTime() : 0;
		var clip = definition.clips().stream().filter(value -> value.id().equals(input.clip())).findFirst().orElse(null);

		if (!input.clip().isEmpty() && clip == null) {
			throw new IllegalArgumentException("模型片段不存在：" + input.clip());
		}

		float time = clipTime(input, clip);
		var matrices = partMatrices(definition, clip, time);
		var weights = morphWeights(definition, input, clip, time);
		float opacity = input.opacity() * (clip == null ? 1 : sampleValue(clip.opacityKeys(), time, 1));
		long curvesEnd = measure ? System.nanoTime() : 0;
		var morphed = morph(definition, weights);
		var positions = positions(definition, input, matrices, morphed);
		long deformationEnd = measure ? System.nanoTime() : 0;
		var groups = new ArrayList<Group>();
		var min = new Vector3f(Float.POSITIVE_INFINITY);
		var max = new Vector3f(Float.NEGATIVE_INFINITY);

		for (var group : prepared.groups()) {
			checkCancelled();
			var part = definition.parts().get(group.part());
			var points = positions.get(group.part());
			var vertices = new ArrayList<Vertex>();
			var normalMatrix = normalMatrix(matrices[group.part()]);
			boolean morphedGeometry = !definition.morphs().isEmpty();
			int lastFace = -1;
			var faceNormal = new Vector3f();

			for (var corner : group.corners()) {
				var face = definition.faces().get(corner.face());
				var position = points.get(corner.vertex());
				if (lastFace != corner.face()) {
					faceNormal = faceNormal(points, face.vertices());
					lastFace = corner.face();
				}

				var normal = new Vector3f(faceNormal);

				if (!morphedGeometry && !face.normals().isEmpty() && normalMatrix != null) {
					var authored = vector(face.normals().get(corner.corner()));
					var scale = dimensionScale(definition, part, input, morphed.get(corner.vertex()));

					if (scale != null && scale.x != 0 && scale.y != 0 && scale.z != 0) {
						authored.div(scale);
						normalMatrix.transform(authored);
						authored.mul(Math.signum(matrices[group.part()].determinant3x3()));
						if (authored.isFinite() && authored.lengthSquared() > 0) {
							normal = normalized(authored);
						}
					}
				}

				if (part.insideOut()) {
					normal.negate();
				}

				vertices.add(new Vertex(position, immutable(normal), face.uv().get(corner.corner())));
				min.min(vector(position));
				max.max(vector(position));
			}

			groups.add(new Group(vertices, part.opacity()));
		}

		if (prepared.groups().isEmpty()) {
			min.zero();
			max.zero();
		}

		var timings = measure ? new Timings(curvesEnd - begin, deformationEnd - curvesEnd, System.nanoTime() - deformationEnd) : null;
		return new Frame(prepared, groups, opacity, immutable(min), immutable(max), timings);
	}

	private Matrix4f[] partMatrices(PhotonModelDefinition definition, @Nullable Clip clip, float time) {
		var result = new Matrix4f[parents.length];

		for (int index : parentOrder) {
			checkCancelled();
			var part = definition.parts().get(index);
			var pivot = vector(part.pivot());
			var matrix = sampleTransform(clip == null ? List.of() : clip.partTracks().getOrDefault(part.id(), List.of()), time, part.transform());
			result[index] = new Matrix4f().translation(pivot).mul(matrix).translate(pivot.negate());

			if (parents[index] >= 0) {
				result[index] = new Matrix4f(result[parents[index]]).mul(result[index]);
			}
		}

		return result;
	}

	private static float[] morphWeights(PhotonModelDefinition definition, Input input, @Nullable Clip clip, float time) {
		var weights = new float[definition.morphs().size()];

		for (int index = 0; index < weights.length; index++) {
			var morph = definition.morphs().get(index);
			weights[index] = input.morphWeights().getOrDefault(morph.id(),
					clip == null ? morph.weight() : sampleValue(clip.morphTracks().getOrDefault(morph.id(), List.of()), time, morph.weight()));
		}

		return weights;
	}

	private static List<Vector3> morph(PhotonModelDefinition definition, float[] weights) {
		var points = definition.vertices().stream().map(PhotonModelPoseSampler::vector).toList();

		for (int index = 0; index < weights.length; index++) {
			checkCancelled();
			if (weights[index] == 0) {
				continue;
			}

			var morph = definition.morphs().get(index);

			for (var entry : morph.deltas().entrySet()) {
				points.get(entry.getKey()).fma(weights[index], vector(entry.getValue()));
			}
		}

		return points.stream().map(PhotonModelPoseSampler::immutable).toList();
	}

	private List<Map<Integer, Vector3>> positions(PhotonModelDefinition definition, Input input, Matrix4f[] matrices, List<Vector3> source) {
		var result = new ArrayList<Map<Integer, Vector3>>();

		for (int index = 0; index < parents.length; index++) {
			result.add(new HashMap<>());
		}

		for (var group : prepared.groups()) {
			checkCancelled();
			var part = definition.parts().get(group.part());
			var points = result.get(group.part());

			for (var corner : group.corners()) {
				points.computeIfAbsent(corner.vertex(), vertex -> {
					var position = dimensions(definition, part, input, source.get(vertex));
					matrices[group.part()].transformPosition(position);
					return immutable(position);
				});
			}
		}

		return result;
	}

	private Vector3f dimensions(PhotonModelDefinition definition, Part part, Input input, Vector3 source) {
		var value = vector(source);
		int axis = definition.forwardAxis();
		float delta = Math.max(input.length() - definition.referenceLength(), minimumDelta);
		float forward = value.get(axis);

		if (!localControl) {
			float anchor = switch (input.anchor()) {
				case NONE -> 0;
				case START -> minForward;
				case END -> maxForward;
			};
			forward = anchor + (forward - anchor) * input.length() / definition.referenceLength();
		} else {
			if (part.anchor() == Anchor.END) {
				forward += delta;
			} else if (part.anchor() != Anchor.START && part.stretch().enabled()) {
				var stretch = part.stretch();
				float amount = Math.clamp((forward - stretch.start()) / (stretch.end() - stretch.start()), 0, 1);
				forward += amount * delta;
			}

			if (input.anchor() == Anchor.END) {
				forward -= delta;
			}
		}

		value.mul(input.width() / definition.referenceWidth());
		value.setComponent(axis, forward);
		return value;
	}

	@Nullable
	private Vector3f dimensionScale(PhotonModelDefinition definition, Part part, Input input, Vector3 source) {
		var scale = new Vector3f(input.width() / definition.referenceWidth());
		float forwardScale = 1;

		if (!localControl) {
			forwardScale = input.length() / definition.referenceLength();
		} else if (part.anchor() == Anchor.NONE && part.stretch().enabled()) {
			float forward = component(source, definition.forwardAxis());
			float delta = Math.max(input.length() - definition.referenceLength(), minimumDelta);

			if (delta != 0 && (forward == part.stretch().start() || forward == part.stretch().end())) {
				return null;
			}

			if (forward > part.stretch().start() && forward < part.stretch().end()) {
				forwardScale += delta / (part.stretch().end() - part.stretch().start());
			}
		}

		return scale.setComponent(definition.forwardAxis(), forwardScale);
	}

	@Nullable
	private static Matrix3f normalMatrix(Matrix4f matrix) {
		return matrix.determinant3x3() == 0 ? null : matrix.normal(new Matrix3f());
	}

	private static Vector3f faceNormal(Map<Integer, Vector3> points, List<Integer> face) {
		var result = new Vector3f();
		var origin = vector(points.get(face.getFirst()));

		for (int index = 1; index + 1 < face.size(); index++) {
			var a = vector(points.get(face.get(index))).sub(origin);
			var b = vector(points.get(face.get(index + 1))).sub(origin);
			result.add(a.cross(b));
		}

		return normalized(result);
	}

	private static Vector3f normalized(Vector3f value) {
		double length = Math.hypot(value.x, Math.hypot(value.y, value.z));
		return length > 0 && Double.isFinite(length)
				? value.set((float) (value.x / length), (float) (value.y / length), (float) (value.z / length))
				: value.zero();
	}

	private static float clipTime(Input input, @Nullable Clip clip) {
		if (clip == null) {
			return input.time();
		}

		if (!input.loop() || !clip.loop()) {
			return Math.clamp(input.time(), 0, clip.duration());
		}

		float time = input.time() % clip.duration();
		return time < 0 ? time + clip.duration() : time;
	}

	private static void checkCancelled() {
		if (Thread.currentThread().isInterrupted()) {
			throw new CancellationException("模型姿态求值已取消。");
		}
	}

	private static float sampleValue(List<ValueKey> keys, float time, float fallback) {
		if (keys.isEmpty()) {
			return fallback;
		}

		int next = upperBound(keys, time, ValueKey::time);

		if (next == 0) {
			return keys.getFirst().value();
		}

		if (next == keys.size()) {
			return keys.getLast().value();
		}

		var a = keys.get(next - 1);
		var b = keys.get(next);
		return Math.fma((time - a.time()) / (b.time() - a.time()), b.value() - a.value(), a.value());
	}

	private static Matrix4f sampleTransform(List<TransformKey> keys, float time, Transform fallback) {
		if (keys.isEmpty()) {
			return matrix(fallback);
		}

		int next = upperBound(keys, time, TransformKey::time);

		if (next == 0) {
			return matrix(keys.getFirst().transform());
		}

		if (next == keys.size()) {
			return matrix(keys.getLast().transform());
		}

		var a = keys.get(next - 1);
		var b = keys.get(next);
		float amount = (time - a.time()) / (b.time() - a.time());
		return new Matrix4f().translationRotateScale(
				vector(a.transform().translation()).lerp(vector(b.transform().translation()), amount),
				rotation(a.transform()).slerp(rotation(b.transform()), amount),
				vector(a.transform().scale()).lerp(vector(b.transform().scale()), amount));
	}

	private static <T> int upperBound(List<T> keys, float time, ToDoubleFunction<T> keyTime) {
		int low = 0;
		int high = keys.size();

		while (low < high) {
			int middle = (low + high) >>> 1;

			if (keyTime.applyAsDouble(keys.get(middle)) <= time) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}

		return low;
	}

	private static Matrix4f matrix(Transform value) {
		return new Matrix4f().translationRotateScale(vector(value.translation()), rotation(value), vector(value.scale()));
	}

	private static Quaternionf rotation(Transform value) {
		var rotation = value.rotation();
		var result = new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w());
		return result.lengthSquared() > 0 ? result.normalize() : result.identity();
	}

	private static float component(Vector3 value, int axis) {
		return switch (axis) {
			case 0 -> value.x();
			case 1 -> value.y();
			default -> value.z();
		};
	}

	private static Vector3f vector(Vector3 value) {
		return new Vector3f(value.x(), value.y(), value.z());
	}

	private static Vector3 immutable(Vector3f value) {
		if (!value.isFinite()) {
			throw new IllegalArgumentException("模型姿态计算超出有限坐标范围。");
		}

		return new Vector3(value.x, value.y, value.z);
	}
}
