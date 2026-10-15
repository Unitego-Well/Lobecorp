package org.unitego.lobecorp.config.photon.model;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.util.photon.model.PhotonModelValidationUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 保存可编辑拓扑、面角 UV、局部控制、骨骼和动画的模型资产。
public class PhotonModelAsset {
	/// 当前编辑工程与运行资产的协议版本。
	public static final int FORMAT_VERSION = 1;
	/// 标准 OBJ 没有分组时使用的默认部件标识。
	public static final String DEFAULT_PART_ID = "main";
	/// 新建资产的参考长度和宽度，单位为模型坐标。
	public static final float DEFAULT_REFERENCE_SIZE = 1f;
	/// 新建模型默认沿 Z 轴延伸。
	public static final int DEFAULT_FORWARD_AXIS = 2;
	/// 默认动画长度为一秒，即 20 个游戏 tick。
	public static final float DEFAULT_CLIP_DURATION = 20f;

	public int version = FORMAT_VERSION;
	public float referenceLength = DEFAULT_REFERENCE_SIZE;
	public float referenceWidth = DEFAULT_REFERENCE_SIZE;
	public int forwardAxis = DEFAULT_FORWARD_AXIS;
	public List<Vector3f> vertices = new ArrayList<>();
	public List<Face> faces = new ArrayList<>();
	/// 可选的逐面纹理槽；旧模型及普通 OBJ 没有定义槽时继续使用发射器材质。
	public Map<String, Material> materials = new LinkedHashMap<>();
	public List<Part> parts = new ArrayList<>();
	public List<Bone> bones = new ArrayList<>();
	public Map<Integer, List<Weight>> weights = new LinkedHashMap<>();
	public List<Morph> morphs = new ArrayList<>();
	public List<Clip> clips = new ArrayList<>();

	public PhotonModelAsset() {
		var part = new Part();
		part.id = DEFAULT_PART_ID;
		parts.add(part);
	}

	public PhotonModelAsset copy() {
		var result = new PhotonModelAsset();
		result.version = version;
		result.referenceLength = referenceLength;
		result.referenceWidth = referenceWidth;
		result.forwardAxis = forwardAxis;
		result.parts.clear();
		vertices.forEach(vertex -> result.vertices.add(new Vector3f(vertex)));
		faces.forEach(face -> result.faces.add(face.copy()));
		materials.forEach((id, material) -> result.materials.put(id, material.copy()));
		parts.forEach(part -> result.parts.add(part.copy()));
		bones.forEach(bone -> result.bones.add(bone.copy()));
		weights.forEach((vertex, influences) -> {
			var copies = new ArrayList<Weight>();
			influences.forEach(weight -> copies.add(weight.copy()));
			result.weights.put(vertex, copies);
		});
		morphs.forEach(morph -> result.morphs.add(morph.copy()));
		clips.forEach(clip -> result.clips.add(clip.copy()));
		return result;
	}

	public void validate() {
		PhotonModelValidationUtil.validate(this);
	}

	public enum Anchor {
		NONE, START, END
	}

	/// 纹理颜色来源；草色在绘制时读取原版已加载的颜色图。
	public enum Tint {
		NONE, GRASS
	}

	/// 保存稳定的纹理资源标识和颜色来源，不保存会随重载变化的图集坐标。
	public static class Material {
		public String texture = "";
		/// 原生材质的完整 NBT 快照；空值沿用纹理槽，着色器资源由原生资源包管理。
		public String materialData = "";
		public Tint tint = Tint.NONE;
		/// 覆盖面在基础表面之后绘制，且不写入深度。
		public boolean overlay;

		public Material copy() {
			var result = new Material();
			result.texture = texture;
			result.materialData = materialData;
			result.tint = tint;
			result.overlay = overlay;
			return result;
		}
	}

	public static class Transform {
		public Vector3f translation = new Vector3f();
		public Quaternionf rotation = new Quaternionf();
		public Vector3f scale = new Vector3f(1f);

		public Transform copy() {
			var result = new Transform();
			result.translation.set(translation);
			result.rotation.set(rotation);
			result.scale.set(scale);
			return result;
		}
	}

	public static class Face {
		public int[] vertices = new int[0];
		public Vector2f[] uv = new Vector2f[0];
		public Vector3f[] normals = new Vector3f[0];
		public String partId = DEFAULT_PART_ID;
		public String material = "";

		public Face copy() {
			var result = new Face();
			result.vertices = vertices.clone();
			result.uv = new Vector2f[uv.length];
			result.normals = new Vector3f[normals.length];

			for (int i = 0; i < uv.length; i++) {
				result.uv[i] = new Vector2f(uv[i]);
			}

			for (int i = 0; i < normals.length; i++) {
				result.normals[i] = new Vector3f(normals[i]);
			}

			result.partId = partId;
			result.material = material;
			return result;
		}
	}

	public static class Stretch {
		public boolean enabled;
		/// 首版启用伸长时必须与资产 forwardAxis 一致，端帽沿同一轴移动。
		public int axis = DEFAULT_FORWARD_AXIS;
		public float start;
		public float end = DEFAULT_REFERENCE_SIZE;

		public Stretch copy() {
			var result = new Stretch();
			result.enabled = enabled;
			result.axis = axis;
			result.start = start;
			result.end = end;
			return result;
		}
	}

	public static class Part {
		public String id = "";
		public String parentId = "";
		public Vector3f pivot = new Vector3f();
		public Transform transform = new Transform();
		public Stretch stretch = new Stretch();
		public Anchor anchor = Anchor.NONE;
		public float opacity = 1f;
		/// 按面法线应用原版基础方向明暗，不产生投影阴影。
		public boolean shading;
		public boolean doubleSided = true;
		/// 翻转本部件的表面绕序与法线，保留几何坐标和面角 UV。
		public boolean insideOut;

		public Part copy() {
			var result = new Part();
			result.id = id;
			result.parentId = parentId;
			result.pivot.set(pivot);
			result.transform = transform.copy();
			result.stretch = stretch.copy();
			result.anchor = anchor;
			result.opacity = opacity;
			result.shading = shading;
			result.doubleSided = doubleSided;
			result.insideOut = insideOut;
			return result;
		}
	}

	public static class Bone {
		public String id = "";
		public String parentId = "";
		public Transform bind = new Transform();

		public Bone copy() {
			var result = new Bone();
			result.id = id;
			result.parentId = parentId;
			result.bind = bind.copy();
			return result;
		}
	}

	public static class Weight {
		public String boneId = "";
		public float value;

		public Weight copy() {
			var result = new Weight();
			result.boneId = boneId;
			result.value = value;
			return result;
		}
	}

	public static class Morph {
		public String id = "";
		public Map<Integer, Vector3f> deltas = new LinkedHashMap<>();
		public float weight;

		public Morph copy() {
			var result = new Morph();
			result.id = id;
			result.weight = weight;
			deltas.forEach((vertex, delta) -> result.deltas.put(vertex, new Vector3f(delta)));
			return result;
		}
	}

	public static class TransformKey {
		/// 关键帧时间，单位为游戏 tick，允许部分刻。
		public float time;
		public Transform transform = new Transform();

		public TransformKey copy() {
			var result = new TransformKey();
			result.time = time;
			result.transform = transform.copy();
			return result;
		}
	}

	public static class ValueKey {
		/// 关键帧时间，单位为游戏 tick，允许部分刻。
		public float time;
		public float value;

		public ValueKey copy() {
			var result = new ValueKey();
			result.time = time;
			result.value = value;
			return result;
		}
	}

	public static class Clip {
		public String id = "";
		/// 动画总时长，单位为游戏 tick。
		public float duration = DEFAULT_CLIP_DURATION;
		public boolean loop = true;
		public Map<String, List<TransformKey>> partTracks = new LinkedHashMap<>();
		public Map<String, List<TransformKey>> boneTracks = new LinkedHashMap<>();
		public Map<String, List<ValueKey>> morphTracks = new LinkedHashMap<>();
		public List<ValueKey> opacityKeys = new ArrayList<>();

		public Clip copy() {
			var result = new Clip();
			result.id = id;
			result.duration = duration;
			result.loop = loop;
			copyTransformTracks(partTracks, result.partTracks);
			copyTransformTracks(boneTracks, result.boneTracks);
			morphTracks.forEach((id, keys) -> {
				var copies = new ArrayList<ValueKey>();
				keys.forEach(key -> copies.add(key.copy()));
				result.morphTracks.put(id, copies);
			});
			opacityKeys.forEach(key -> result.opacityKeys.add(key.copy()));
			return result;
		}

		private static void copyTransformTracks(Map<String, List<TransformKey>> source,
		                                        Map<String, List<TransformKey>> target) {
			source.forEach((id, keys) -> {
				var copies = new ArrayList<TransformKey>();
				keys.forEach(key -> copies.add(key.copy()));
				target.put(id, copies);
			});
		}
	}
}
