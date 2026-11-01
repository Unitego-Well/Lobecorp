package org.unitego.lobecorp.util.photon.model;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Rotation;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.ValueKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
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
	private final BoneRig skeleton;
	private final Map<String, Clip> clipIndex;
	private final Set<String> partIds;
	private final Set<String> morphIds;
	private final ThreadLocal<SamplingScratch> scratch = ThreadLocal.withInitial(SamplingScratch::new);

	/// 采样期临时对象按线程隔离：同一准备结果可被多个线程并发求值，不能共享可写缓冲。
	private static final class SamplingScratch {
		private final Vector3f faceNormal = new Vector3f();
		private final Vector3f faceEdgeA = new Vector3f();
		private final Vector3f faceEdgeB = new Vector3f();
		private final Vector3f cornerNormal = new Vector3f();
		private final Map<Integer, Matrix4f> skin = new HashMap<>();
		private final List<Matrix4f> skinPool = new ArrayList<>();
		private final float[] skinCombined = new float[16];
		private final float[] skinValues = new float[16];
		private final Vector3f authored = new Vector3f();
		private final Matrix4f combined = new Matrix4f();
		private final Matrix3f normalTransform = new Matrix3f();
		private final Vector3f pivot = new Vector3f();
		private final Matrix4f local = new Matrix4f();
		private final Matrix4f pivoted = new Matrix4f();
		private Matrix4f[] parts = new Matrix4f[0];
		private Matrix4f[] boneWorld = new Matrix4f[0];
		private Matrix4f[] boneSkin = new Matrix4f[0];
		private float[] morphWeights = new float[0];
		private Vector3[] positionValues = new Vector3[0];
		private int[] positionEpochs = new int[0];
		private int positionStride;
		private int positionEpoch;
	}

	private record BoneRig(Map<String, Integer> indices, int[] parents, int[] order, Matrix4f[] inverseBind) {
	}

	public record PartOverride(@Nullable Vector3 translation, @Nullable Rotation rotation, @Nullable Vector3 scale) {
		public PartOverride {
			if (translation != null && !vector(translation).isFinite() || scale != null && !vector(scale).isFinite()
					|| rotation != null && (!Float.isFinite(rotation.x()) || !Float.isFinite(rotation.y())
					|| !Float.isFinite(rotation.z()) || !Float.isFinite(rotation.w()))) {
				throw new IllegalArgumentException("部件覆盖包含无效数值。");
			}
		}
	}

	/// 时间沿用文件tick单位；空片段为基础姿态。未覆盖的TRS分量继续使用该次片段采样值。
	public record Input(float time, String clip, boolean loop, float length, float width,
	                    Anchor anchor, float opacity, Map<String, Float> morphWeights,
	                    Map<String, PartOverride> partOverrides, Map<String, PartOverride> boneOverrides) {
		public Input(float time, String clip, boolean loop, float length, float width,
		             Anchor anchor, float opacity, Map<String, Float> morphWeights, Map<String, PartOverride> partOverrides) {
			this(time, clip, loop, length, width, anchor, opacity, morphWeights, partOverrides, Map.of());
		}

		public Input(float time, String clip, boolean loop, float length, float width,
		             Anchor anchor, float opacity, Map<String, Float> morphWeights) {
			this(time, clip, loop, length, width, anchor, opacity, morphWeights, Map.of());
		}

		public Input {
			Objects.requireNonNull(clip);
			Objects.requireNonNull(anchor);
			morphWeights = Map.copyOf(morphWeights);
			partOverrides = Map.copyOf(partOverrides);
			boneOverrides = Map.copyOf(boneOverrides);

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

		skeleton = prepareBones(definition);
		var clips = new HashMap<String, Clip>();

		for (var clip : definition.clips()) {
			clips.putIfAbsent(clip.id(), clip);
		}

		clipIndex = Map.copyOf(clips);

		parents = new int[definition.parts().size()];
		var partIndices = new HashMap<String, Integer>();

		for (int index = 0; index < parents.length; index++) {
			partIndices.put(definition.parts().get(index).id(), index);
		}

		partIds = Set.copyOf(partIndices.keySet());
		morphIds = definition.morphs().stream().map(PhotonModelDefinition.Morph::id).collect(Collectors.toUnmodifiableSet());

		var ordered = new ArrayList<Integer>();
		var included = new boolean[parents.length];

		for (int index = 0; index < parents.length; index++) {
			parents[index] = partIndices.getOrDefault(definition.parts().get(index).parentId(), -1);
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
		var scratch = this.scratch.get();
		var faceNormalScratch = scratch.faceNormal;
		var cornerNormalScratch = scratch.cornerNormal;
		var authoredScratch = scratch.authored;
		var combinedScratch = scratch.combined;

		for (var id : input.morphWeights().keySet()) {
			if (!morphIds.contains(id)) {
				throw new IllegalArgumentException("模型形状键不存在：" + id);
			}
		}

		for (var id : input.partOverrides().keySet()) {
			if (!partIds.contains(id)) {
				throw new IllegalArgumentException("模型部件不存在：" + id);
			}
		}
		for (var id : input.boneOverrides().keySet()) {
			if (!skeleton.indices().containsKey(id)) {
				throw new IllegalArgumentException("模型骨骼不存在：" + id);
			}
		}

		long begin = measure ? System.nanoTime() : 0;
		var clip = input.clip().isEmpty() ? null : clipIndex.get(input.clip());

		if (!input.clip().isEmpty() && clip == null) {
			throw new IllegalArgumentException("模型片段不存在：" + input.clip());
		}

		float time = clipTime(input, clip);
		var matrices = partMatrices(definition, input.partOverrides(), clip, time);
		var skin = skinMatrices(definition, input.boneOverrides(), clip, time);
		var weights = morphWeights(definition, input, clip, time);
		float opacity = input.opacity() * (clip == null ? 1 : sampleValue(clip.opacityKeys(), time, 1));
		long curvesEnd = measure ? System.nanoTime() : 0;
		var morphed = morph(definition, weights);
		computePositions(definition, input, matrices, morphed, skin);
		long deformationEnd = measure ? System.nanoTime() : 0;
		var groups = new ArrayList<Group>(prepared.groups().size());
		var min = new Vector3f(Float.POSITIVE_INFINITY);
		var max = new Vector3f(Float.NEGATIVE_INFINITY);

		for (var group : prepared.groups()) {
			checkCancelled();
			int partIndex = group.part();
			var part = definition.parts().get(partIndex);
			var vertices = new ArrayList<Vertex>(group.corners().size());
			var normalMatrix = normalMatrix(matrices[partIndex]);
			boolean morphedGeometry = !definition.morphs().isEmpty();
			int lastFace = -1;

			for (var corner : group.corners()) {
				var face = definition.faces().get(corner.face());
				var position = positionValue(partIndex, corner.vertex());

				if (lastFace != corner.face()) {
					faceNormal(partIndex, face.vertices());
					lastFace = corner.face();
				}

				var normal = cornerNormalScratch.set(faceNormalScratch);

				if (!morphedGeometry && !face.normals().isEmpty() && normalMatrix != null) {
					var authoredValue = face.normals().get(corner.corner());
					var authored = authoredScratch.set(authoredValue.x(), authoredValue.y(), authoredValue.z());
					var scale = dimensionScale(definition, part, input, morphed.get(corner.vertex()));

					if (scale != null && scale.x != 0 && scale.y != 0 && scale.z != 0) {
						var deformation = skin.get(corner.vertex());
						if (deformation == null) {
							authored.div(scale);
							normalMatrix.transform(authored);
							authored.mul(Math.signum(matrices[partIndex].determinant3x3()));
						} else {
							var combined = combinedScratch.set(matrices[partIndex]).mul(deformation).scale(scale);
							var normalTransform = normalMatrixInto(combined);
							if (normalTransform != null) {
								normalTransform.transform(authored);
								authored.mul(Math.signum(combined.determinant3x3()));
							} else {
								authored.zero();
							}
						}
						if (authored.isFinite() && authored.lengthSquared() > 0) {
							normal.set(normalized(authored));
						}
					}
				}

				if (part.insideOut()) {
					normal.negate();
				}

				vertices.add(new Vertex(position, immutable(normal), face.uv().get(corner.corner())));
				float px = position.x();
				float py = position.y();
				float pz = position.z();
				if (px < min.x) {
					min.x = px;
				}
				if (py < min.y) {
					min.y = py;
				}
				if (pz < min.z) {
					min.z = pz;
				}
				if (px > max.x) {
					max.x = px;
				}
				if (py > max.y) {
					max.y = py;
				}
				if (pz > max.z) {
					max.z = pz;
				}
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

	public PhotonModelTopologyUtil.Prepared prepared() {
		return prepared;
	}

	private Matrix4f[] partMatrices(PhotonModelDefinition definition, Map<String, PartOverride> overrides, @Nullable Clip clip, float time) {
		var scratch = this.scratch.get();

		if (scratch.parts.length != parents.length) {
			scratch.parts = matrices(parents.length);
		}

		var result = scratch.parts;

		for (int index : parentOrder) {
			checkCancelled();
			var part = definition.parts().get(index);
			var pivot = scratch.pivot.set(part.pivot().x(), part.pivot().y(), part.pivot().z());
			var sampled = sampleTransform(clip == null ? List.of() : clip.partTracks().getOrDefault(part.id(), List.of()), time, part.transform());
			var override = overrides.get(part.id());

			if (override != null) {
				sampled = new Transform(override.translation() == null ? sampled.translation() : override.translation(),
						override.rotation() == null ? sampled.rotation() : override.rotation(),
						override.scale() == null ? sampled.scale() : override.scale());
			}

			var matrix = matrix(sampled, scratch.local);
			var pivoted = scratch.pivoted.translation(pivot).mul(matrix).translate(pivot.negate());

			if (parents[index] >= 0) {
				result[index].set(result[parents[index]]).mul(pivoted);
			} else {
				result[index].set(pivoted);
			}
		}

		return result;
	}

	private float[] morphWeights(PhotonModelDefinition definition, Input input, @Nullable Clip clip, float time) {
		var scratch = this.scratch.get();

		if (scratch.morphWeights.length != definition.morphs().size()) {
			scratch.morphWeights = new float[definition.morphs().size()];
		}

		var weights = scratch.morphWeights;

		for (int index = 0; index < weights.length; index++) {
			var morph = definition.morphs().get(index);
			weights[index] = input.morphWeights().getOrDefault(morph.id(),
					clip == null ? morph.weight() : sampleValue(clip.morphTracks().getOrDefault(morph.id(), List.of()), time, morph.weight()));
		}

		return weights;
	}

	private static List<Vector3> morph(PhotonModelDefinition definition, float[] weights) {
		boolean changed = false;

		for (float weight : weights) {
			if (weight != 0) {
				changed = true;
				break;
			}
		}

		// 无形状键或权重全为 0 时结果与源顶点逐值相同，跳过两次整表复制。
		if (!changed) {
			return definition.vertices();
		}

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

	/// 结果写入线程私有槽位；代数与逐帧新建 Map 的旧实现相同，lambda 与装箱不再产生。
	private void computePositions(PhotonModelDefinition definition, Input input, Matrix4f[] matrices, List<Vector3> source,
	                              Map<Integer, Matrix4f> skin) {
		var scratch = this.scratch.get();
		int vertexCount = definition.vertices().size();
		int required = definition.parts().size() * vertexCount;

		if (scratch.positionValues.length != required) {
			scratch.positionValues = new Vector3[required];
			scratch.positionEpochs = new int[required];
			scratch.positionEpoch = 0;
		}

		scratch.positionStride = vertexCount;
		int epoch = ++scratch.positionEpoch;

		if (epoch == 0) {
			Arrays.fill(scratch.positionEpochs, 0);
			epoch = ++scratch.positionEpoch;
		}

		for (var group : prepared.groups()) {
			checkCancelled();
			int partIndex = group.part();
			var part = definition.parts().get(partIndex);
			int base = partIndex * vertexCount;

			for (var corner : group.corners()) {
				int vertex = corner.vertex();
				int cell = base + vertex;

				if (scratch.positionEpochs[cell] == epoch) {
					continue;
				}

				var position = dimensions(definition, part, input, source.get(vertex));
				var deformation = skin.get(vertex);

				if (deformation != null) {
					deformation.transformPosition(position);
				}

				matrices[partIndex].transformPosition(position);
				scratch.positionValues[cell] = immutable(position);
				scratch.positionEpochs[cell] = epoch;
			}
		}
	}

	private Vector3 positionValue(int partIndex, int vertex) {
		var scratch = this.scratch.get();
		return scratch.positionValues[partIndex * scratch.positionStride + vertex];
	}

	private static BoneRig prepareBones(PhotonModelDefinition definition) throws IOException {
		int count = definition.bones().size();
		var ids = new LinkedHashMap<String, Integer>();
		for (int index = 0; index < count; index++) {
			ids.put(definition.bones().get(index).id(), index);
		}
		int[] parents = new int[count];
		var order = new ArrayList<Integer>();
		var world = new Matrix4f[count];
		for (int index = 0; index < count; index++) {
			parents[index] = ids.getOrDefault(definition.bones().get(index).parentId(), -1);
		}
		while (order.size() < count) {
			boolean progressed = false;
			for (int index = 0; index < count; index++) {
				checkCancelled();
				if (world[index] != null || parents[index] >= 0 && world[parents[index]] == null) {
					continue;
				}
				var local = matrix(definition.bones().get(index).bind());
				world[index] = parents[index] < 0 ? local : new Matrix4f(world[parents[index]]).mul(local);
				order.add(index);
				progressed = true;
			}
			if (!progressed) {
				throw new IOException("模型骨骼父链无法排序。");
			}
		}
		var inverse = new Matrix4f[count];
		for (int index = 0; index < count; index++) {
			inverse[index] = new Matrix4f(world[index]).invert();
			if (!inverse[index].isFinite()) {
				throw new IOException("模型骨骼绑定矩阵不可逆：" + definition.bones().get(index).id());
			}
		}
		return new BoneRig(Map.copyOf(ids), parents, order.stream().mapToInt(Integer::intValue).toArray(), inverse);
	}

	private Map<Integer, Matrix4f> skinMatrices(PhotonModelDefinition definition, Map<String, PartOverride> overrides, @Nullable Clip clip, float time) {
		if (definition.weights().isEmpty()) {
			return Map.of();
		}
		var scratch = this.scratch.get();

		if (scratch.boneWorld.length != skeleton.parents().length) {
			scratch.boneWorld = matrices(skeleton.parents().length);
			scratch.boneSkin = matrices(skeleton.parents().length);
		}

		var world = scratch.boneWorld;
		var skin = scratch.boneSkin;
		for (int index : skeleton.order()) {
			checkCancelled();
			var bone = definition.bones().get(index);
			var sampled = sampleTransform(clip == null ? List.of() : clip.boneTracks().getOrDefault(bone.id(), List.of()), time, bone.bind());
			var override = overrides.get(bone.id());
			if (override != null) {
				sampled = new Transform(override.translation() == null ? sampled.translation() : override.translation(),
						override.rotation() == null ? sampled.rotation() : override.rotation(),
						override.scale() == null ? sampled.scale() : override.scale());
			}
			var local = matrix(sampled, scratch.local);
			int parent = skeleton.parents()[index];

			if (parent < 0) {
				world[index].set(local);
			} else {
				world[index].set(world[parent]).mul(local);
			}

			skin[index].set(world[index]).mul(skeleton.inverseBind()[index]);
		}
		// 结果只在本次采样内被 positions 读取；复用线程私有存储，避免每顶点数组/矩阵/流对象。
		scratch.skin.clear();
		int poolIndex = 0;

		for (var entry : definition.weights().entrySet()) {
			checkCancelled();
			double total = 0;

			for (var influence : entry.getValue()) {
				total += influence.value();
			}

			if (total <= 0) {
				continue;
			}

			Arrays.fill(scratch.skinCombined, 0);

			for (var influence : entry.getValue()) {
				skin[skeleton.indices().get(influence.boneId())].get(scratch.skinValues);
				float weight = (float) (influence.value() / total);

				for (int component = 0; component < scratch.skinValues.length; component++) {
					scratch.skinCombined[component] += scratch.skinValues[component] * weight;
				}
			}

			Matrix4f matrix;

			if (poolIndex < scratch.skinPool.size()) {
				matrix = scratch.skinPool.get(poolIndex);
			} else {
				matrix = new Matrix4f();
				scratch.skinPool.add(matrix);
			}

			poolIndex++;
			matrix.set(scratch.skinCombined);

			if (!matrix.isFinite()) {
				throw new IllegalArgumentException("模型蒙皮结果包含非有限数值。");
			}

			scratch.skin.put(entry.getKey(), matrix);
		}

		return scratch.skin;
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

	@Nullable
	private Matrix3f normalMatrixInto(Matrix4f matrix) {
		return matrix.determinant3x3() == 0 ? null : matrix.normal(scratch.get().normalTransform);
	}

	private void faceNormal(int partIndex, List<Integer> face) {
		var scratch = this.scratch.get();
		var origin = positionValue(partIndex, face.getFirst());
		float ox = origin.x();
		float oy = origin.y();
		float oz = origin.z();
		scratch.faceNormal.zero();

		for (int index = 1; index + 1 < face.size(); index++) {
			var first = positionValue(partIndex, face.get(index));
			var second = positionValue(partIndex, face.get(index + 1));
			scratch.faceEdgeA.set(first.x() - ox, first.y() - oy, first.z() - oz);
			scratch.faceEdgeB.set(second.x() - ox, second.y() - oy, second.z() - oz);
			scratch.faceEdgeA.cross(scratch.faceEdgeB);
			scratch.faceNormal.add(scratch.faceEdgeA);
		}

		normalized(scratch.faceNormal);
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

	private static Transform sampleTransform(List<TransformKey> keys, float time, Transform fallback) {
		if (keys.isEmpty()) {
			return fallback;
		}

		int next = upperBound(keys, time, TransformKey::time);

		if (next == 0) {
			return keys.getFirst().transform();
		}

		if (next == keys.size()) {
			return keys.getLast().transform();
		}

		var a = keys.get(next - 1);
		var b = keys.get(next);
		float amount = (time - a.time()) / (b.time() - a.time());
		var rotated = rotation(a.transform()).slerp(rotation(b.transform()), amount);
		return new Transform(immutable(vector(a.transform().translation()).lerp(vector(b.transform().translation()), amount)),
				new Rotation(rotated.x, rotated.y, rotated.z, rotated.w),
				immutable(vector(a.transform().scale()).lerp(vector(b.transform().scale()), amount)));
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

	private static Matrix4f[] matrices(int count) {
		var result = new Matrix4f[count];

		for (int index = 0; index < count; index++) {
			result[index] = new Matrix4f();
		}

		return result;
	}

	private static Matrix4f matrix(Transform value) {
		return matrix(value, new Matrix4f());
	}

	private static Matrix4f matrix(Transform value, Matrix4f target) {
		return target.translationRotateScale(vector(value.translation()), rotation(value), vector(value.scale()));
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
