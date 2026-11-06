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
	private final Map<String, ClipData> clipData;
	private final Object ownerToken = new Object();
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
		private final Matrix3f combinedNormalTransform = new Matrix3f();
		private final Vector3f pivot = new Vector3f();
		private final Matrix4f local = new Matrix4f();
		private final Matrix4f pivoted = new Matrix4f();
		private Matrix4f[] parts = new Matrix4f[0];
		private Matrix4f[] boneWorld = new Matrix4f[0];
		private Matrix4f[] boneSkin = new Matrix4f[0];
		private float[] morphWeights = new float[0];
		private Vector3f[] positionValues = new Vector3f[0];
		private int[] positionEpochs = new int[0];
		private Vector3f[] morphedPositions = new Vector3f[0];
		private final Vector3f min = new Vector3f();
		private final Vector3f max = new Vector3f();
		private final Vector3f morphDelta = new Vector3f();
		private final Vector3f dimensionValue = new Vector3f();
		private final Vector3f dimensionScale = new Vector3f();
		private boolean morphed;
		private int positionStride;
		private int positionEpoch;

		private void clear() {
			faceNormal.zero();
			faceEdgeA.zero();
			faceEdgeB.zero();
			cornerNormal.zero();
			skin.clear();
			skinPool.clear();
			parts = new Matrix4f[0];
			boneWorld = new Matrix4f[0];
			boneSkin = new Matrix4f[0];
			morphWeights = new float[0];
			positionValues = new Vector3f[0];
			positionEpochs = new int[0];
			morphedPositions = new Vector3f[0];
			morphed = false;
			positionStride = 0;
			positionEpoch = 0;
		}
	}

	private record ClipData(List<List<TransformKey>> partTracks, List<List<TransformKey>> boneTracks,
	                        List<List<ValueKey>> morphTracks, List<ValueKey> opacityKeys) {
	}

	/// 调用线程独占的求值工作区；不持有sampler或ThreadLocal键，避免默认值形成强引用环。
	public static final class Workspace implements AutoCloseable {
		private final Object ownerToken;
		private final Thread ownerThread;
		private final SamplingScratch scratch;
		private boolean busy;
		private boolean closed;

		private Workspace(Object ownerToken, Thread ownerThread) {
			this.ownerToken = ownerToken;
			this.ownerThread = ownerThread;
			scratch = new SamplingScratch();
		}

		@Override
		public void close() {
			if (ownerThread != Thread.currentThread()) {
				throw new IllegalStateException("模型求值工作区只能在创建线程关闭。");
			}

			if (closed) {
				return;
			}

			if (busy) {
				throw new IllegalStateException("模型求值工作区正在使用。");
			}

			closed = true;
			scratch.clear();
		}
	}

	public interface IPrimitivePoseSink {
		void beginFrame(PhotonModelTopologyUtil.Prepared prepared, float opacity, int groupCount);

		void beginGroup(int groupIndex, int vertexCount, float opacity);

		void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v);

		void endGroup();

		void endFrame(Vector3 min, Vector3 max, @Nullable Timings timings);
	}

	private static final class FrameSink implements IPrimitivePoseSink {
		private final PhotonModelTopologyUtil.Prepared prepared;
		private final List<Group> groups = new ArrayList<>();
		private List<Vertex> vertices;
		private float opacity;
		private float groupOpacity;
		private Vector3 min;
		private Vector3 max;
		private @Nullable Timings timings;

		private FrameSink(PhotonModelTopologyUtil.Prepared prepared) {
			this.prepared = prepared;
		}

		@Override
		public void beginFrame(PhotonModelTopologyUtil.Prepared prepared, float opacity, int groupCount) {
			groups.clear();
			this.opacity = opacity;
		}

		@Override
		public void beginGroup(int groupIndex, int vertexCount, float opacity) {
			vertices = new ArrayList<>(vertexCount);
			groupOpacity = opacity;
		}

		@Override
		public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
			vertices.add(new Vertex(new Vector3(x, y, z), new Vector3(nx, ny, nz), new Vector2(u, v)));
		}

		@Override
		public void endGroup() {
			groups.add(new Group(vertices, groupOpacity));
			vertices = null;
		}

		@Override
		public void endFrame(Vector3 min, Vector3 max, @Nullable Timings timings) {
			this.min = min;
			this.max = max;
			this.timings = timings;
		}

		private Frame frame() {
			return new Frame(prepared, groups, opacity, min, max, timings);
		}
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
		var indexedParts = new HashMap<String, Integer>();

		for (int index = 0; index < parents.length; index++) {
			indexedParts.put(definition.parts().get(index).id(), index);
		}

		var partIndices = Map.copyOf(indexedParts);
		partIds = Set.copyOf(partIndices.keySet());
		var indexedMorphs = new LinkedHashMap<String, Integer>();

		for (int index = 0; index < definition.morphs().size(); index++) {
			indexedMorphs.put(definition.morphs().get(index).id(), index);
		}

		morphIds = Set.copyOf(indexedMorphs.keySet());
		var compiledClips = new LinkedHashMap<String, ClipData>();

		for (var clip : clips.values()) {
			var partTracks = new ArrayList<List<TransformKey>>(definition.parts().size());
			var boneTracks = new ArrayList<List<TransformKey>>(definition.bones().size());
			var morphTracks = new ArrayList<List<ValueKey>>(definition.morphs().size());

			for (var part : definition.parts()) {
				partTracks.add(clip.partTracks().getOrDefault(part.id(), List.of()));
			}

			for (var bone : definition.bones()) {
				boneTracks.add(clip.boneTracks().getOrDefault(bone.id(), List.of()));
			}

			for (var morph : definition.morphs()) {
				morphTracks.add(clip.morphTracks().getOrDefault(morph.id(), List.of()));
			}

			compiledClips.put(clip.id(), new ClipData(List.copyOf(partTracks), List.copyOf(boneTracks),
					List.copyOf(morphTracks), clip.opacityKeys()));
		}

		clipData = Map.copyOf(compiledClips);

		var ordered = new ArrayList<Integer>();
		var included = new boolean[parents.length];

		for (int index = 0; index < parents.length; index++) {
			parents[index] = indexedParts.getOrDefault(definition.parts().get(index).parentId(), -1);
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
		var sink = new FrameSink(prepared);
		sampleInto(input, measure, scratch.get(), sink);
		return sink.frame();
	}

	public void sampleInto(Input input, boolean measure, Workspace workspace, IPrimitivePoseSink sink) {
		Objects.requireNonNull(input);
		Objects.requireNonNull(workspace);
		Objects.requireNonNull(sink);

		if (workspace.ownerToken != ownerToken) {
			throw new IllegalArgumentException("模型求值工作区不属于当前采样器。");
		}

		if (workspace.ownerThread != Thread.currentThread()) {
			throw new IllegalStateException("模型求值工作区只能在创建线程使用。");
		}

		if (workspace.closed) {
			throw new IllegalStateException("模型求值工作区已关闭。");
		}

		if (workspace.busy) {
			throw new IllegalStateException("模型求值工作区不可重入。");
		}

		workspace.busy = true;

		try {
			sampleInto(input, measure, workspace.scratch, sink);
		} finally {
			workspace.busy = false;
		}
	}

	public Workspace newWorkspace() {
		return new Workspace(ownerToken, Thread.currentThread());
	}

	public @Nullable Clip clip(String id) {
		return clipIndex.get(id);
	}

	private void sampleInto(Input input, boolean measure, SamplingScratch scratch, IPrimitivePoseSink sink) {
		checkCancelled();
		var definition = prepared.definition();

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

		var compiledClip = clip == null ? null : clipData.get(clip.id());
		float time = clipTime(input, clip);
		var matrices = partMatrices(definition, input.partOverrides(), compiledClip, time, scratch);
		var skin = skinMatrices(definition, input.boneOverrides(), compiledClip, time, scratch);
		var weights = morphWeights(definition, input, compiledClip, time, scratch);
		float opacity = input.opacity() * (compiledClip == null ? 1 : sampleValue(compiledClip.opacityKeys(), time, 1));
		long curvesEnd = measure ? System.nanoTime() : 0;
		morph(definition, weights, scratch);
		computePositions(definition, input, matrices, scratch);
		long deformationEnd = measure ? System.nanoTime() : 0;
		sink.beginFrame(prepared, opacity, prepared.groups().size());
		var min = scratch.min.set(Float.POSITIVE_INFINITY);
		var max = scratch.max.set(Float.NEGATIVE_INFINITY);
		boolean morphedGeometry = !definition.morphs().isEmpty();

		for (int groupIndex = 0; groupIndex < prepared.groups().size(); groupIndex++) {
			checkCancelled();
			var group = prepared.groups().get(groupIndex);
			int partIndex = group.part();
			var part = definition.parts().get(partIndex);
			var normalMatrix = normalMatrix(matrices[partIndex], scratch);
			int lastFace = -1;
			sink.beginGroup(groupIndex, group.corners().size(), part.opacity());

			for (var corner : group.corners()) {
				var face = definition.faces().get(corner.face());
				var position = positionValue(partIndex, corner.vertex(), scratch);

				if (lastFace != corner.face()) {
					faceNormal(partIndex, face.vertices(), scratch);
					lastFace = corner.face();
				}

				var normal = scratch.cornerNormal.set(scratch.faceNormal);

				if (!morphedGeometry && !face.normals().isEmpty() && normalMatrix != null) {
					var authoredValue = face.normals().get(corner.corner());
					var authored = scratch.authored.set(authoredValue.x(), authoredValue.y(), authoredValue.z());
					var scale = dimensionScale(definition, part, input, morphedPosition(corner.vertex(), scratch), scratch);


					if (scale != null && scale.x != 0 && scale.y != 0 && scale.z != 0) {
						var deformation = skin.get(corner.vertex());
						if (deformation == null) {
							authored.div(scale);
							normalMatrix.transform(authored);
							authored.mul(Math.signum(matrices[partIndex].determinant3x3()));
						} else {
							var combined = scratch.combined.set(matrices[partIndex]).mul(deformation).scale(scale);
							var normalTransform = normalMatrixInto(combined, scratch);
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

				float px = position.x();
				float py = position.y();
				float pz = position.z();
				var uv = face.uv().get(corner.corner());
				sink.vertex(px, py, pz, normal.x, normal.y, normal.z, uv.x(), uv.y());

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

			sink.endGroup();
		}

		if (prepared.groups().isEmpty()) {
			min.zero();
			max.zero();
		}

		var timings = measure ? new Timings(curvesEnd - begin, deformationEnd - curvesEnd, System.nanoTime() - deformationEnd) : null;
		sink.endFrame(immutable(min), immutable(max), timings);
	}

	public PhotonModelTopologyUtil.Prepared prepared() {
		return prepared;
	}

	private Matrix4f[] partMatrices(PhotonModelDefinition definition, Map<String, PartOverride> overrides,
	                                @Nullable ClipData clip, float time, SamplingScratch scratch) {
		if (scratch.parts.length != parents.length) {
			scratch.parts = matrices(parents.length);
		}

		var result = scratch.parts;

		for (int index : parentOrder) {
			checkCancelled();
			var part = definition.parts().get(index);
			var pivot = scratch.pivot.set(part.pivot().x(), part.pivot().y(), part.pivot().z());
			var sampled = sampleTransform(clip == null ? List.of() : clip.partTracks().get(index), time, part.transform());
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

	private float[] morphWeights(PhotonModelDefinition definition, Input input, @Nullable ClipData clip, float time,
	                             SamplingScratch scratch) {
		if (scratch.morphWeights.length != definition.morphs().size()) {
			scratch.morphWeights = new float[definition.morphs().size()];
		}

		var weights = scratch.morphWeights;

		for (int index = 0; index < weights.length; index++) {
			var morph = definition.morphs().get(index);
			weights[index] = input.morphWeights().getOrDefault(morph.id(),
					clip == null ? morph.weight() : sampleValue(clip.morphTracks().get(index), time, morph.weight()));
		}

		return weights;
	}

	private void morph(PhotonModelDefinition definition, float[] weights, SamplingScratch scratch) {
		boolean changed = false;

		for (float weight : weights) {
			if (weight != 0) {
				changed = true;
				break;
			}
		}

		if (!changed) {
			scratch.morphed = false;
			return;
		}

		if (scratch.morphedPositions.length != definition.vertices().size()) {
			scratch.morphedPositions = new Vector3f[definition.vertices().size()];

			for (int index = 0; index < scratch.morphedPositions.length; index++) {
				scratch.morphedPositions[index] = new Vector3f();
			}
		}

		for (int index = 0; index < scratch.morphedPositions.length; index++) {
			var source = definition.vertices().get(index);
			scratch.morphedPositions[index].set(source.x(), source.y(), source.z());
		}

		for (int index = 0; index < weights.length; index++) {
			checkCancelled();

			if (weights[index] == 0) {
				continue;
			}

			var morph = definition.morphs().get(index);

			for (var entry : morph.deltas().entrySet()) {
				var delta = entry.getValue();
				scratch.morphedPositions[entry.getKey()].fma(weights[index], scratch.morphDelta.set(delta.x(), delta.y(), delta.z()));
			}
		}

		scratch.morphed = true;
	}

	/// 结果写入线程私有槽位；代数与旧实现相同，不向公开 Frame 暴露可变引用。
	private void computePositions(PhotonModelDefinition definition, Input input, Matrix4f[] matrices, SamplingScratch scratch) {
		int vertexCount = definition.vertices().size();
		int required = definition.parts().size() * vertexCount;

		if (scratch.positionValues.length != required) {
			scratch.positionValues = new Vector3f[required];
			scratch.positionEpochs = new int[required];

			for (int index = 0; index < scratch.positionValues.length; index++) {
				scratch.positionValues[index] = new Vector3f();
			}

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

				var position = dimensions(definition, part, input, morphedPosition(vertex, scratch), scratch);
				var deformation = scratch.skin.get(vertex);

				if (deformation != null) {
					deformation.transformPosition(position);
				}

				matrices[partIndex].transformPosition(position);
				scratch.positionValues[cell].set(position);
				scratch.positionEpochs[cell] = epoch;
			}
		}
	}

	private Vector3f positionValue(int partIndex, int vertex, SamplingScratch scratch) {
		return scratch.positionValues[partIndex * scratch.positionStride + vertex];
	}

	private Vector3f morphedPosition(int vertex, SamplingScratch scratch) {
		if (scratch.morphed) {
			return scratch.morphedPositions[vertex];
		}

		var source = prepared.definition().vertices().get(vertex);
		return scratch.morphDelta.set(source.x(), source.y(), source.z());
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

	private Map<Integer, Matrix4f> skinMatrices(PhotonModelDefinition definition, Map<String, PartOverride> overrides,
	                                            @Nullable ClipData clip, float time, SamplingScratch scratch) {
		if (definition.weights().isEmpty()) {
			return Map.of();
		}

		if (scratch.boneWorld.length != skeleton.parents().length) {
			scratch.boneWorld = matrices(skeleton.parents().length);
			scratch.boneSkin = matrices(skeleton.parents().length);
		}

		var world = scratch.boneWorld;
		var skin = scratch.boneSkin;
		for (int index : skeleton.order()) {
			checkCancelled();
			var bone = definition.bones().get(index);
			var sampled = sampleTransform(clip == null ? List.of() : clip.boneTracks().get(index), time, bone.bind());
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

	private Vector3f dimensions(PhotonModelDefinition definition, Part part, Input input, Vector3f source,
	                            SamplingScratch scratch) {
		var value = scratch.dimensionValue.set(source);
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
	private Vector3f dimensionScale(PhotonModelDefinition definition, Part part, Input input, Vector3f source,
	                                SamplingScratch scratch) {
		var scale = scratch.dimensionScale.set(input.width() / definition.referenceWidth());
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
	private static Matrix3f normalMatrix(Matrix4f matrix, SamplingScratch scratch) {
		return matrix.determinant3x3() == 0 ? null : matrix.normal(scratch.normalTransform);
	}

	@Nullable
	private static Matrix3f normalMatrixInto(Matrix4f matrix, SamplingScratch scratch) {
		return matrix.determinant3x3() == 0 ? null : matrix.normal(scratch.combinedNormalTransform);
	}

	private void faceNormal(int partIndex, List<Integer> face, SamplingScratch scratch) {
		var origin = positionValue(partIndex, face.getFirst(), scratch);
		float ox = origin.x();
		float oy = origin.y();
		float oz = origin.z();
		scratch.faceNormal.zero();

		for (int index = 1; index + 1 < face.size(); index++) {
			var first = positionValue(partIndex, face.get(index), scratch);
			var second = positionValue(partIndex, face.get(index + 1), scratch);
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
		var first = a.transform();
		var second = b.transform();
		var rotated = rotation(first).slerp(rotation(second), amount);
		var translation = new Vector3f(first.translation().x(), first.translation().y(), first.translation().z())
				.lerp(vector(second.translation()), amount);
		var scale = new Vector3f(first.scale().x(), first.scale().y(), first.scale().z())
				.lerp(vector(second.scale()), amount);
		return new Transform(immutable(translation), new Rotation(rotated.x, rotated.y, rotated.z, rotated.w),
				immutable(scale));
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
		var rotation = rotation(value);
		return target.translationRotateScale(value.translation().x(), value.translation().y(), value.translation().z(),
				rotation.x, rotation.y, rotation.z, rotation.w, value.scale().x(), value.scale().y(), value.scale().z());
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

	private static float component(Vector3f value, int axis) {
		return switch (axis) {
			case 0 -> value.x;
			case 1 -> value.y;
			default -> value.z;
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
