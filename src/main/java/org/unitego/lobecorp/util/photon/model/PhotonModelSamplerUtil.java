package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.ValueKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 编辑预览和运行时共用的形状键、骨骼、部件与局部伸长采样。
public class PhotonModelSamplerUtil {
	/// 退化动画三角形使用有限的默认法线，避免向渲染器写入 NaN。
	private static final Vector3f FALLBACK_NORMAL = new Vector3f(0f, 1f, 0f);

	/// 每个采样线程最多保留两个临时工作区；输出网格不借用池内数据。
	private static final int MAX_POOLED_SCRATCHES = 2;

	private static class ScratchPool {
		private static final ThreadLocal<ArrayDeque<GeometryScratch>> LOCAL = ThreadLocal.withInitial(ArrayDeque::new);
	}

	private static class GeometryScratch {
		private final Vector3f normal = new Vector3f();
		private final Vector3f edge = new Vector3f();
		private final Vector3f cornerNormal = new Vector3f();
		private final float[][] corners = {new float[8], new float[8], new float[8]};
	}

	public static class SampleParameters {
		/// NaN 表示使用资产参考尺寸，其余值为正的模型单位。
		public float length = Float.NaN;
		public float width = Float.NaN;
		public float opacity = 1f;
		public boolean loop = true;
		public Anchor anchor = Anchor.NONE;
		/// 覆盖变换允许单个通道为 null，表示保留关键帧或默认值。
		public Map<String, Transform> partOverrides = new LinkedHashMap<>();
		public Map<String, Transform> boneOverrides = new LinkedHashMap<>();
		public Map<String, Float> morphOverrides = new LinkedHashMap<>();
	}

	/// 部件顶点保留资产的全局顶点下标，便于变形后的编辑器拾取。
	/// 骨骼世界矩阵为模型空间中的实际姿态，不是蒙皮矩阵，编辑器可直接复用本次采样。
	public record SampledModel(PhotonMesh mesh, Map<String, PhotonMesh> partMeshes,
	                           Map<String, Float> partOpacity, float opacity,
	                           Map<String, List<Vector3f>> partVertices,
	                           Map<MaterialGroup, PhotonMesh> materialMeshes,
	                           Map<String, Matrix4f> boneWorldMatrices) {
		public SampledModel withOpacity(float value) {
			return value == opacity ? this : new SampledModel(mesh, partMeshes, partOpacity, value,
					partVertices, materialMeshes, boneWorldMatrices);
		}

		/// 保留原采样结果构造入口；旧调用不提供逐面材质分组。
		@SuppressWarnings("unused")
		public SampledModel(PhotonMesh mesh, Map<String, PhotonMesh> partMeshes,
		                    Map<String, Float> partOpacity, float opacity,
		                    Map<String, List<Vector3f>> partVertices) {
			this(mesh, partMeshes, partOpacity, opacity, partVertices, Map.of());
		}

		/// 保留已有逐面材质采样的构造入口，外部旧调用不提供骨骼姿态。
		@SuppressWarnings("unused")
		public SampledModel(PhotonMesh mesh, Map<String, PhotonMesh> partMeshes,
		                    Map<String, Float> partOpacity, float opacity,
		                    Map<String, List<Vector3f>> partVertices,
		                    Map<MaterialGroup, PhotonMesh> materialMeshes) {
			this(mesh, partMeshes, partOpacity, opacity, partVertices, materialMeshes, Map.of());
		}
	}

	/// 未定义纹理槽的面合并为原部件材质组，避免改变普通 OBJ 的渲染行为。
	public record MaterialGroup(String partId, String materialId) {
	}

	/// 独占不可变定义副本与已校验拓扑；资产编辑后必须重新准备，不缓存动画姿态。
	public static class PreparedModel {
		private final PhotonModelAsset asset;
		private final int[][] triangles;

		private PreparedModel(PhotonModelAsset source) {
			asset = source.copy();
			asset.validate();
			triangles = new int[asset.faces.size()][];

			for (int index = 0; index < triangles.length; index++) {
				triangles[index] = PhotonModelPolygonUtil.triangulate(asset.vertices, asset.faces.get(index).vertices);
			}
		}
	}

	public static PreparedModel prepare(PhotonModelAsset asset) {
		return new PreparedModel(asset);
	}

	/// 运行实例复用拓扑；时间、尺寸、形状键、骨骼和属性覆盖仍逐次求值。
	public static SampledModel sample(PreparedModel prepared, String clipId, float time, SampleParameters parameters) {
		return sample(prepared.asset, clipId, time, parameters, prepared.triangles, false);
	}

	/// 运行绘制只构建实际使用的部件或材质网格，不生成拾取与骨骼编辑数据。
	public static SampledModel sampleForRender(PreparedModel prepared, String clipId, float time, SampleParameters parameters) {
		return sample(prepared.asset, clipId, time, parameters, prepared.triangles, true);
	}

	/// 无几何轨道的片段可跨时间复用姿态，透明度仍按原始时间单独求值。
	public static float geometryTime(PreparedModel prepared, String clipId, float time) {
		var clip = findClip(prepared.asset, clipId);
		return clip == null || clip.partTracks.isEmpty() && clip.boneTracks.isEmpty() && clip.morphTracks.isEmpty()
				? 0f : time;
	}

	/// 不生成网格即可判断整体透明度，保持片段循环和末帧钳制语义。
	public static float opacity(PreparedModel prepared, String clipId, float time, boolean loop) {
		PhotonModelValidationUtil.require(Float.isFinite(time), "sample");
		var clip = findClip(prepared.asset, clipId);
		return clip == null ? 1f : sampleValue(clip.opacityKeys, clipTime(clip, time, loop), 1f);
	}

	/// 时间单位为游戏 tick；先在绑定坐标拉伸，再蒙皮和应用部件变换。
	public static SampledModel sample(PhotonModelAsset asset, String clipId, float time,
	                                  SampleParameters parameters) {
		asset.validate();
		return sample(asset, clipId, time, parameters, null, false);
	}

	private static SampledModel sample(PhotonModelAsset asset, String clipId, float time,
	                                   SampleParameters parameters, @Nullable int[][] topology, boolean renderOnly) {
		PhotonModelValidationUtil.require(parameters != null && Float.isFinite(time), "sample");
		float length = dimension(parameters.length, asset.referenceLength);
		float width = dimension(parameters.width, asset.referenceWidth);
		validateParameters(asset, parameters);
		Clip clip = findClip(asset, clipId);
		float sampleTime = clipTime(clip, time, parameters.loop);
		var morphed = morphVertices(asset, clip, sampleTime, parameters);
		var boneWorld = boneWorldMatrices(asset, clip, sampleTime, parameters);
		var skinMatrices = skinMatrices(asset, boneWorld);
		var partMatrices = partMatrices(asset, clip, sampleTime, parameters);
		var positions = partVertices(asset, parameters, length, width, morphed, skinMatrices, partMatrices);
		var normals = normalMatrices(asset, length, width, morphed, skinMatrices, partMatrices);
		var fullBuilder = renderOnly ? null : new PhotonMesh.Builder();
		var builders = new LinkedHashMap<String, PhotonMesh.Builder>();
		var materialBuilders = new LinkedHashMap<MaterialGroup, PhotonMesh.Builder>();
		var partOpacity = new LinkedHashMap<String, Float>();
		var partInsideOut = new LinkedHashMap<String, Boolean>();

		for (var part : asset.parts) {
			if (!renderOnly || asset.materials.isEmpty()) {
				builders.put(part.id, new PhotonMesh.Builder());
			}
			partOpacity.put(part.id, part.opacity);
			partInsideOut.put(part.id, part.insideOut);
		}

		var pool = ScratchPool.LOCAL.get();
		var scratch = pool.pollFirst();

		if (scratch == null) {
			scratch = new GeometryScratch();
		}

		try {
			appendFaces(asset, topology, positions, normals, fullBuilder, builders, materialBuilders, partInsideOut, scratch);
		} finally {
			if (pool.size() < MAX_POOLED_SCRATCHES) {
				pool.addFirst(scratch);
			}
		}

		var meshes = new LinkedHashMap<String, PhotonMesh>();
		builders.forEach((id, builder) -> meshes.put(id, builder.build()));
		var materialMeshes = new LinkedHashMap<MaterialGroup, PhotonMesh>();
		materialBuilders.forEach((group, builder) -> materialMeshes.put(group, builder.build()));
		float opacity = parameters.opacity * (clip == null ? 1f : sampleValue(clip.opacityKeys, sampleTime, 1f));
		return new SampledModel(fullBuilder == null ? PhotonMesh.EMPTY : fullBuilder.build(), meshes, partOpacity, opacity,
				renderOnly ? Map.of() : positions, materialMeshes, renderOnly ? Map.of() : boneWorld);
	}

	private static void appendFaces(PhotonModelAsset asset, @Nullable int[][] topology,
	                                Map<String, List<Vector3f>> positions, Map<String, Map<Integer, Matrix3f>> normals,
	                                @Nullable PhotonMesh.Builder full, Map<String, PhotonMesh.Builder> parts,
	                                Map<MaterialGroup, PhotonMesh.Builder> materials, Map<String, Boolean> insideOut,
	                                GeometryScratch scratch) {
		for (int faceIndex = 0; faceIndex < asset.faces.size(); faceIndex++) {
			var face = asset.faces.get(faceIndex);
			var materialId = asset.materials.containsKey(face.material) ? face.material : "";
			var material = asset.materials.isEmpty() ? null
					: materials.computeIfAbsent(new MaterialGroup(face.partId, materialId), _ -> new PhotonMesh.Builder());
			int[] triangles = topology == null ? PhotonModelPolygonUtil.triangulate(asset.vertices, face.vertices) : topology[faceIndex];

			for (int index = 0; index < triangles.length; index += 3) {
				appendTriangle(full, parts.get(face.partId), material, face, triangles, index, positions.get(face.partId),
						normals.getOrDefault(face.partId, Map.of()), insideOut.get(face.partId), scratch);
			}
		}
	}

	private static float dimension(float requested, float reference) {
		float value = Float.isNaN(requested) ? reference : requested;
		PhotonModelValidationUtil.require(Float.isFinite(value) && value >= 0f, "sample.size");
		return value;
	}

	private static void validateParameters(PhotonModelAsset asset, SampleParameters parameters) {
		PhotonModelValidationUtil.require(parameters.anchor != null && parameters.partOverrides != null
				&& parameters.boneOverrides != null && parameters.morphOverrides != null
				&& Float.isFinite(parameters.opacity) && parameters.opacity >= 0f
				&& parameters.opacity <= 1f, "sample.parameters");
		if (parameters.partOverrides.isEmpty() && parameters.boneOverrides.isEmpty() && parameters.morphOverrides.isEmpty()) {
			return;
		}

		var partIds = asset.parts.stream().map(part -> part.id).toList();
		var boneIds = asset.bones.stream().map(bone -> bone.id).toList();
		var morphIds = asset.morphs.stream().map(morph -> morph.id).toList();
		parameters.partOverrides.forEach((id, transform) ->
				PhotonModelValidationUtil.require(partIds.contains(id) && transform != null, "sample.part"));
		parameters.boneOverrides.forEach((id, transform) ->
				PhotonModelValidationUtil.require(boneIds.contains(id) && transform != null, "sample.bone"));
		parameters.morphOverrides.forEach((id, weight) ->
				PhotonModelValidationUtil.require(morphIds.contains(id) && weight != null
						&& Float.isFinite(weight), "sample.morph"));
	}

	private static Clip findClip(PhotonModelAsset asset, String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}

		return asset.clips.stream().filter(clip -> clip.id.equals(id)).findFirst().orElse(null);
	}

	private static float clipTime(Clip clip, float time, boolean loop) {
		if (clip == null) {
			return 0f;
		}

		if (loop && clip.loop) {
			return (time % clip.duration + clip.duration) % clip.duration;
		}

		return Math.clamp(time, 0f, clip.duration);
	}

	private static List<Vector3f> morphVertices(PhotonModelAsset asset, Clip clip, float time,
	                                            SampleParameters parameters) {
		if (asset.morphs.isEmpty()) {
			return asset.vertices;
		}

		var positions = new ArrayList<Vector3f>(asset.vertices.size());
		asset.vertices.forEach(vertex -> positions.add(new Vector3f(vertex)));

		for (var morph : asset.morphs) {
			float weight = clip == null ? morph.weight
					: sampleValue(clip.morphTracks.get(morph.id), time, morph.weight);
			weight = parameters.morphOverrides.getOrDefault(morph.id, weight);

			for (var entry : morph.deltas.entrySet()) {
				positions.get(entry.getKey()).fma(weight, entry.getValue());
			}
		}

		return positions;
	}

	private static Map<String, Matrix4f> boneWorldMatrices(PhotonModelAsset asset, Clip clip, float time,
	                                                       SampleParameters parameters) {
		var sampledLocals = new LinkedHashMap<String, Matrix4f>();
		var parents = new LinkedHashMap<String, String>();

		for (var bone : asset.bones) {
			parents.put(bone.id, bone.parentId);
			var sampled = sampleTransform(clip == null ? null : clip.boneTracks.get(bone.id), time, bone.bind);
			sampled = override(sampled, parameters.boneOverrides.get(bone.id));
			sampledLocals.put(bone.id, matrix(sampled));
		}

		return worldMatrices(sampledLocals, parents);
	}

	private static Map<String, Matrix4f> skinMatrices(PhotonModelAsset asset, Map<String, Matrix4f> sampledWorld) {
		var bindLocals = new LinkedHashMap<String, Matrix4f>();
		var parents = new LinkedHashMap<String, String>();

		for (var bone : asset.bones) {
			parents.put(bone.id, bone.parentId);
			bindLocals.put(bone.id, matrix(bone.bind));
		}

		var bindWorld = worldMatrices(bindLocals, parents);
		var skinMatrices = new LinkedHashMap<String, Matrix4f>();
		parents.keySet().forEach(id -> skinMatrices.put(id,
				new Matrix4f(sampledWorld.get(id)).mul(new Matrix4f(bindWorld.get(id)).invert())));
		return skinMatrices;
	}

	private static Vector3f skinVertex(PhotonModelAsset asset, int index, Vector3f position,
	                                   Map<String, Matrix4f> matrices) {
		var influences = asset.weights.get(index);

		if (influences == null || influences.isEmpty()) {
			return position;
		}

		float total = 0f;

		for (var influence : influences) {
			total += influence.value;
		}

		var blended = new Vector3f();

		for (var influence : influences) {
			var transformed = matrices.get(influence.boneId).transformPosition(new Vector3f(position));
			blended.fma(influence.value / total, transformed);
		}

		return blended;
	}

	private static Map<String, Matrix4f> partMatrices(PhotonModelAsset asset, Clip clip, float time,
	                                                  SampleParameters parameters) {
		var locals = new LinkedHashMap<String, Matrix4f>();
		var parents = new LinkedHashMap<String, String>();

		for (var part : asset.parts) {
			var sampled = sampleTransform(clip == null ? null : clip.partTracks.get(part.id), time, part.transform);
			sampled = override(sampled, parameters.partOverrides.get(part.id));
			var local = new Matrix4f().translation(part.pivot).mul(matrix(sampled))
					.translate(-part.pivot.x, -part.pivot.y, -part.pivot.z);
			parents.put(part.id, part.parentId);
			locals.put(part.id, local);
		}

		return worldMatrices(locals, parents);
	}

	/// 返回包含轴心和父级的基础部件矩阵，供几何编辑在显示坐标与资产坐标之间转换。
	public static Map<String, Matrix4f> basePartMatrices(PhotonModelAsset asset) {
		asset.validate();
		return partMatrices(asset, null, 0, new SampleParameters());
	}

	private static Map<String, Matrix4f> worldMatrices(Map<String, Matrix4f> locals,
	                                                   Map<String, String> parents) {
		var result = new LinkedHashMap<String, Matrix4f>();

		for (String id : locals.keySet()) {
			worldMatrix(id, locals, parents, result);
		}

		return result;
	}

	private static Matrix4f worldMatrix(String id, Map<String, Matrix4f> locals,
	                                    Map<String, String> parents, Map<String, Matrix4f> result) {
		Matrix4f cached = result.get(id);

		if (cached != null) {
			return cached;
		}

		String parent = parents.get(id);
		Matrix4f world = parent.isEmpty() ? new Matrix4f(locals.get(id))
				: new Matrix4f(worldMatrix(parent, locals, parents, result)).mul(locals.get(id));
		PhotonModelValidationUtil.require(world.isFinite(), "sample.matrix");
		result.put(id, world);
		return world;
	}

	private static Matrix4f matrix(Transform transform) {
		PhotonModelValidationUtil.transform(transform);
		return new Matrix4f().translationRotateScale(transform.translation,
				new Quaternionf(transform.rotation).normalize(), transform.scale);
	}

	/// 对已验证的轨道采样局部 TRS；时间为 tick，调用方处理 clip 的循环和时长。
	public static Transform sampleTransform(List<TransformKey> keys, float time, Transform fallback) {
		if (keys == null || keys.isEmpty()) {
			return fallback.copy();
		}

		if (time <= keys.getFirst().time) {
			return keys.getFirst().transform.copy();
		}

		for (int i = 1; i < keys.size(); i++) {
			var next = keys.get(i);

			if (time <= next.time) {
				var previous = keys.get(i - 1);
				float alpha = (time - previous.time) / (next.time - previous.time);
				var result = new Transform();
				previous.transform.translation.lerp(next.transform.translation, alpha, result.translation);
				previous.transform.scale.lerp(next.transform.scale, alpha, result.scale);
				result.rotation.set(previous.transform.rotation).normalize()
						.slerp(new Quaternionf(next.transform.rotation).normalize(), alpha).normalize();
				return result;
			}
		}

		return keys.getLast().transform.copy();
	}

	/// 对已验证的数值轨道采样；时间为 tick，调用方处理 clip 的循环和时长。
	public static float sampleValue(List<ValueKey> keys, float time, float fallback) {
		if (keys == null || keys.isEmpty()) {
			return fallback;
		}

		if (time <= keys.getFirst().time) {
			return keys.getFirst().value;
		}

		for (int i = 1; i < keys.size(); i++) {
			var next = keys.get(i);

			if (time <= next.time) {
				var previous = keys.get(i - 1);
				float alpha = (time - previous.time) / (next.time - previous.time);
				return previous.value + (next.value - previous.value) * alpha;
			}
		}

		return keys.getLast().value;
	}

	private static Transform override(Transform sampled, Transform override) {
		if (override == null) {
			return sampled;
		}

		if (override.translation != null) {
			sampled.translation.set(override.translation);
		}

		if (override.rotation != null) {
			sampled.rotation.set(override.rotation);
		}

		if (override.scale != null) {
			sampled.scale.set(override.scale);
		}

		PhotonModelValidationUtil.transform(sampled);
		return sampled;
	}

	private static Map<String, List<Vector3f>> partVertices(PhotonModelAsset asset,
	                                                        SampleParameters parameters,
	                                                        float length, float width,
	                                                        List<Vector3f> morphed,
	                                                        Map<String, Matrix4f> skinMatrices,
	                                                        Map<String, Matrix4f> matrices) {
		var result = new LinkedHashMap<String, List<Vector3f>>();
		float delta = length - asset.referenceLength;
		float widthRatio = width / asset.referenceWidth;
		boolean localControl = asset.parts.stream().anyMatch(part ->
				part.stretch.enabled || part.anchor != Anchor.NONE);

		if (localControl) {
			delta = clampStretchDelta(asset, delta);
		}

		PhotonModelValidationUtil.require(Float.isFinite(widthRatio), "sample.width");
		float fixed = anchorCoordinate(asset, parameters.anchor);

		for (var part : asset.parts) {
			var positions = new ArrayList<Vector3f>(asset.vertices.size());

			for (int index = 0; index < morphed.size(); index++) {
				var position = new Vector3f(morphed.get(index));

				if (localControl) {
					stretch(position, part, delta, asset.forwardAxis);
				} else {
					float scaled = fixed + (position.get(asset.forwardAxis) - fixed)
							* length / asset.referenceLength;
					position.setComponent(asset.forwardAxis, scaled);
				}

				if (localControl && parameters.anchor == Anchor.END) {
					position.setComponent(asset.forwardAxis, position.get(asset.forwardAxis) - delta);
				}

				for (int axis = 0; axis < 3; axis++) {
					if (axis != asset.forwardAxis) {
						position.setComponent(axis, position.get(axis) * widthRatio);
					}
				}

				position = skinVertex(asset, index, position, skinMatrices);
				matrices.get(part.id).transformPosition(position);
				PhotonModelValidationUtil.require(position.isFinite(), "sample.vertex");
				positions.add(position);
			}

			result.put(part.id, positions);
		}

		return result;
	}

	private static float clampStretchDelta(PhotonModelAsset asset, float delta) {
		float result = delta;

		for (var part : asset.parts) {
			if (part.stretch.enabled && part.anchor == Anchor.NONE) {
				result = Math.max(result, part.stretch.start - part.stretch.end);
			}
		}

		return result;
	}

	private static float anchorCoordinate(PhotonModelAsset asset, Anchor anchor) {
		if (anchor == Anchor.NONE || asset.vertices.isEmpty()) {
			return 0f;
		}

		float result = asset.vertices.getFirst().get(asset.forwardAxis);

		for (var vertex : asset.vertices) {
			float coordinate = vertex.get(asset.forwardAxis);
			result = anchor == Anchor.START ? Math.min(result, coordinate) : Math.max(result, coordinate);
		}

		return result;
	}

	private static void stretch(Vector3f position, Part part, float delta, int forwardAxis) {
		if (part.anchor == Anchor.END) {
			position.setComponent(forwardAxis, position.get(forwardAxis) + delta);
			return;
		}

		if (part.anchor == Anchor.START || !part.stretch.enabled) {
			return;
		}

		var stretch = part.stretch;
		float span = stretch.end - stretch.start;
		PhotonModelValidationUtil.require(span + delta >= 0f, "stretch.length");
		float coordinate = position.get(stretch.axis);

		if (coordinate <= stretch.start) {
			return;
		}

		float displaced = coordinate >= stretch.end ? coordinate + delta
				: stretch.start + (coordinate - stretch.start) * (span + delta) / span;
		position.setComponent(stretch.axis, displaced);
	}

	/// 面角法线保留硬边；形状键没有法线增量时沿用变形面法线，避免使用过期方向。
	private static Map<String, Map<Integer, Matrix3f>> normalMatrices(PhotonModelAsset asset, float length, float width,
	                                                                  List<Vector3f> morphed,
	                                                                  Map<String, Matrix4f> skinMatrices,
	                                                                  Map<String, Matrix4f> partMatrices) {
		if (!asset.vertices.equals(morphed)) {
			return Map.of();
		}

		var parts = new LinkedHashMap<String, Part>();
		asset.parts.forEach(part -> parts.put(part.id, part));
		boolean localControl = asset.parts.stream().anyMatch(part ->
				part.stretch.enabled || part.anchor != Anchor.NONE);
		float delta = length - asset.referenceLength;

		if (localControl) {
			delta = clampStretchDelta(asset, delta);
		}

		var result = new LinkedHashMap<String, Map<Integer, Matrix3f>>();

		for (var face : asset.faces) {
			if (face.normals.length == 0) {
				continue;
			}

			var part = parts.get(face.partId);
			var matrices = result.computeIfAbsent(part.id, _ -> new LinkedHashMap<>());

			for (int index : face.vertices) {
				if (matrices.containsKey(index)) {
					continue;
				}

				var position = morphed.get(index);
				var stretch = part.stretch;
				float coordinate = position.get(stretch.axis);

				// 伸长区间边界没有唯一缩放导数，改用该三角形的变形面法线。
				if (delta != 0f && part.anchor == Anchor.NONE && stretch.enabled
						&& (coordinate == stretch.start || coordinate == stretch.end)) {
					matrices.put(index, null);
					continue;
				}

				float axisScale = localControl ? stretchScale(position, part, delta)
						: length / asset.referenceLength;
				var scale = new Vector3f(width / asset.referenceWidth).setComponent(asset.forwardAxis, axisScale);
				var matrix = new Matrix3f(partMatrices.get(part.id)).mul(skinMatrix(asset, index, skinMatrices)).scale(scale);
				float determinant = matrix.determinant();

				if (!Float.isFinite(determinant) || determinant == 0f) {
					matrices.put(index, null);
					continue;
				}

				matrix.normal();

				// 镜像部件的法线跟随实际绕序，翻面操作仍在输出三角形时单独处理。
				if (determinant < 0f) {
					matrix.scale(-1f);
				}

				matrices.put(index, matrix);
			}
		}

		return result;
	}

	private static float stretchScale(Vector3f position, Part part, float delta) {
		if (part.anchor != Anchor.NONE || !part.stretch.enabled) {
			return 1f;
		}

		var stretch = part.stretch;
		float coordinate = position.get(stretch.axis);

		if (coordinate <= stretch.start || coordinate >= stretch.end) {
			return 1f;
		}

		float span = stretch.end - stretch.start;
		return (span + delta) / span;
	}

	private static Matrix3f skinMatrix(PhotonModelAsset asset, int index, Map<String, Matrix4f> matrices) {
		var influences = asset.weights.get(index);

		if (influences == null || influences.isEmpty()) {
			return new Matrix3f();
		}

		float total = 0f;

		for (var influence : influences) {
			total += influence.value;
		}

		var blended = new Matrix3f().zero();

		for (var influence : influences) {
			blended.add(new Matrix3f(matrices.get(influence.boneId)).scale(influence.value / total));
		}

		return blended;
	}

	private static Vector3f sampleNormal(Face face, int corner, Map<Integer, Matrix3f> matrices,
	                                     Vector3f fallback, boolean insideOut, Vector3f target) {
		var matrix = matrices.get(face.vertices[corner]);

		if (face.normals.length == 0 || matrix == null) {
			return fallback;
		}

		var normal = matrix.transform(target.set(face.normals[corner]));

		if (!normal.isFinite() || normal.lengthSquared() == 0f || !Float.isFinite(normal.lengthSquared())) {
			return fallback;
		}

		normal.normalize();

		if (insideOut) {
			normal.negate();
		}

		return normal;
	}

	private static void appendTriangle(@Nullable PhotonMesh.Builder full, @Nullable PhotonMesh.Builder part,
	                                   @Nullable PhotonMesh.Builder material, Face face, int[] triangles, int offset,
	                                   List<Vector3f> positions, Map<Integer, Matrix3f> normals, boolean insideOut,
	                                   GeometryScratch scratch) {
		var a = positions.get(face.vertices[triangles[offset]]);
		var b = positions.get(face.vertices[triangles[offset + 1]]);
		var c = positions.get(face.vertices[triangles[offset + 2]]);
		var normal = scratch.normal.set(b).sub(a).cross(scratch.edge.set(c).sub(a));

		if (!normal.isFinite() || normal.lengthSquared() == 0f) {
			normal.set(FALLBACK_NORMAL);
		} else {
			normal.normalize();
		}

		if (insideOut) {
			normal.negate();
		}

		for (int corner = 0; corner < 3; corner++) {
			var source = triangles[offset + corner];
			var vertex = positions.get(face.vertices[source]);
			writeVertex(scratch.corners[corner], vertex, face.uv[source],
					sampleNormal(face, source, normals, normal, insideOut, scratch.cornerNormal));
		}

		var first = scratch.corners[0];
		var second = scratch.corners[insideOut ? 2 : 1];
		var third = scratch.corners[insideOut ? 1 : 2];

		if (full != null) {
			full.triangle(first, second, third);
		}

		if (part != null) {
			part.triangle(first, second, third);
		}

		if (material != null) {
			material.triangle(first, second, third);
		}
	}

	private static void writeVertex(float[] target, Vector3f position, Vector2f uv, Vector3f normal) {
		target[0] = position.x;
		target[1] = position.y;
		target[2] = position.z;
		target[3] = uv.x;
		target[4] = uv.y;
		target[5] = normal.x;
		target[6] = normal.y;
		target[7] = normal.z;
	}

}
