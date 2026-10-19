package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Bone;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Morph;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Rotation;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Stretch;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Tint;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.ValueKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Weight;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// 当前模型JSON到不可变定义的适配；保留原树，不重新命名部件/片段或丢掉未知字段。
public class PhotonModelCodec {
	public record Binding(String path, PhotonModelDefinition definition) {
	}

	public static List<Binding> readEmbedded(PhotonResourceDocument document) throws IOException {
		var result = new ArrayList<Binding>();
		var tree = document.json();

		if (tree != null && tree.isJsonObject() && tree.getAsJsonObject().has("vertices")) {
			result.add(new Binding(document.origin(), read(tree.getAsJsonObject())));
		}

		var tag = document.nbt();

		if (tag != null) {
			embedded(tag, document.origin(), result, 0);
		}

		for (var child : document.entries()) {
			result.addAll(readEmbedded(child));
		}

		return List.copyOf(result);
	}

	private static void embedded(Tag tag, String path, List<Binding> result, int depth) throws IOException {
		if (depth > PhotonResourceLimits.DEFAULT.depth()) {
			throw new IOException("模型快照嵌套过深：" + path);
		}

		if (tag instanceof CompoundTag compound) {
			for (var entry : compound.entrySet()) {
				var child = path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1");

				if (entry.getKey().equals("asset") || entry.getKey().equals("assetJson")) {
					var data = switch (entry.getValue()) {
						case ByteArrayTag bytes ->
								PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT);
						case StringTag(var text) -> PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT);
						default -> throw new IOException("模型快照不是当前协议的字节或文本数据：" + child);
					};
					result.add(new Binding(child, read(data.getAsJsonObject())));
				} else {
					embedded(entry.getValue(), child, result, depth + 1);
				}
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				embedded(list.get(index), path + "/" + index, result, depth + 1);
			}
		}
	}

	public static PhotonModelDefinition read(String source) throws IOException {
		var tree = PhotonResourceJsonUtil.read(source, PhotonResourceLimits.DEFAULT);

		if (!tree.isJsonObject()) {
			throw new IOException("模型根数据必须是JSON对象。");
		}

		return read(tree.getAsJsonObject());
	}

	public static PhotonModelDefinition read(JsonObject source) throws IOException {
		try {
			if (integer(source, "version", -1) != PhotonModelDefinition.VERSION) {
				throw new IllegalArgumentException("模型版本不支持。");
			}

			var vertices = list(array(source, "vertices"), PhotonModelCodec::vector3);
			var faces = list(array(source, "faces"), item -> face(item.getAsJsonObject()));
			var parts = list(array(source, "parts"), item -> part(item.getAsJsonObject()));

			if (!source.has("parts")) {
				parts = List.of(part(new JsonObject()));
			}

			var materials = new LinkedHashMap<String, Material>();

			for (var entry : object(source, "materials").entrySet()) {
				var item = entry.getValue().getAsJsonObject();
				materials.put(entry.getKey(), new Material(string(item, "texture", ""), string(item, "materialData", ""),
						Tint.valueOf(string(item, "tint", "NONE")), bool(item, "overlay", false)));
			}

			var bones = list(array(source, "bones"), item -> {
				var value = item.getAsJsonObject();
				return new Bone(string(value, "id", ""), string(value, "parentId", ""), transform(object(value, "bind")));
			});
			var weights = new LinkedHashMap<Integer, List<Weight>>();

			for (var entry : object(source, "weights").entrySet()) {
				weights.put(new BigDecimal(entry.getKey()).intValueExact(), list(entry.getValue().getAsJsonArray(), item -> {
					var value = item.getAsJsonObject();
					return new Weight(string(value, "boneId", ""), number(value, "value", 0));
				}));
			}

			var morphs = list(array(source, "morphs"), item -> morph(item.getAsJsonObject()));
			var clips = list(array(source, "clips"), item -> clip(item.getAsJsonObject()));
			var definition = new PhotonModelDefinition(source, number(source, "referenceLength", 1),
					number(source, "referenceWidth", 1), integer(source, "forwardAxis", 2),
					vertices, faces, parts, materials, bones, weights, morphs, clips);
			validate(definition);
			return definition;
		} catch (RuntimeException failure) {
			throw new IOException("模型结构无法按当前协议适配。", failure);
		}
	}

	private static Face face(JsonObject value) {
		return new Face(list(array(value, "vertices"), PhotonModelCodec::integer),
				list(array(value, "uv"), PhotonModelCodec::vector2), list(array(value, "normals"), PhotonModelCodec::vector3),
				string(value, "partId", PhotonModelDefinition.DEFAULT_PART), string(value, "material", ""));
	}

	private static Part part(JsonObject value) {
		var stretch = object(value, "stretch");
		return new Part(string(value, "id", PhotonModelDefinition.DEFAULT_PART), string(value, "parentId", ""),
				value.has("pivot") ? vector3(value.get("pivot")) : Vector3.ZERO, transform(object(value, "transform")),
				new Stretch(bool(stretch, "enabled", false), integer(stretch, "axis", 2),
						number(stretch, "start", 0), number(stretch, "end", 1)),
				Anchor.valueOf(string(value, "anchor", "NONE")), number(value, "opacity", 1),
				bool(value, "shading", false), bool(value, "doubleSided", true), bool(value, "insideOut", false));
	}

	private static Morph morph(JsonObject value) {
		var deltas = new LinkedHashMap<Integer, Vector3>();

		for (var entry : object(value, "deltas").entrySet()) {
			deltas.put(new BigDecimal(entry.getKey()).intValueExact(), vector3(entry.getValue()));
		}

		return new Morph(string(value, "id", ""), deltas, number(value, "weight", 0));
	}

	private static Clip clip(JsonObject value) {
		return new Clip(string(value, "id", ""), number(value, "duration", PhotonModelDefinition.DEFAULT_CLIP_TICKS),
				bool(value, "loop", true), tracks(object(value, "partTracks"), PhotonModelCodec::transformKey),
				tracks(object(value, "boneTracks"), PhotonModelCodec::transformKey),
				tracks(object(value, "morphTracks"), PhotonModelCodec::valueKey),
				list(array(value, "opacityKeys"), PhotonModelCodec::valueKey));
	}

	private static TransformKey transformKey(JsonElement element) {
		var value = element.getAsJsonObject();
		return new TransformKey(number(value, "time", 0), transform(object(value, "transform")));
	}

	private static ValueKey valueKey(JsonElement element) {
		var value = element.getAsJsonObject();
		return new ValueKey(number(value, "time", 0), number(value, "value", 0));
	}

	private static Transform transform(JsonObject value) {
		if (value.isEmpty()) {
			return Transform.IDENTITY;
		}

		return new Transform(value.has("translation") ? vector3(value.get("translation")) : Vector3.ZERO,
				value.has("rotation") ? rotation(value.get("rotation")) : Rotation.IDENTITY,
				value.has("scale") ? vector3(value.get("scale")) : Vector3.ONE);
	}

	private static Rotation rotation(JsonElement value) {
		var array = value.getAsJsonArray();
		require(array.size() == 4, "四元数分量数量无效。");
		return new Rotation(number(array.get(0)), number(array.get(1)), number(array.get(2)), number(array.get(3)));
	}

	private static Vector2 vector2(JsonElement value) {
		var array = value.getAsJsonArray();
		require(array.size() == 2, "UV分量数量无效。");
		return new Vector2(number(array.get(0)), number(array.get(1)));
	}

	private static Vector3 vector3(JsonElement value) {
		var array = value.getAsJsonArray();
		require(array.size() == 3, "向量分量数量无效。");
		return new Vector3(number(array.get(0)), number(array.get(1)), number(array.get(2)));
	}

	private static void validate(PhotonModelDefinition definition) {
		require(definition.referenceLength() > 0 && definition.referenceWidth() > 0, "参考尺寸必须为正。");
		require(definition.forwardAxis() >= 0 && definition.forwardAxis() <= 2, "前向轴无效。");
		var parts = new LinkedHashMap<String, String>();

		for (var part : definition.parts()) {
			require(!part.id().isEmpty() && parts.putIfAbsent(part.id(), part.parentId()) == null, "部件标识重复或为空。");
			require(part.opacity() >= 0 && part.opacity() <= 1, "部件透明度无效。");
			require(part.stretch().axis() >= 0 && part.stretch().axis() <= 2, "伸长轴无效。");

			if (part.stretch().enabled()) {
				require(part.stretch().axis() == definition.forwardAxis() && part.stretch().end() > part.stretch().start(), "伸长区间或轴无效。");
			}
		}

		hierarchy(parts);

		for (var face : definition.faces()) {
			require(face.vertices().size() >= 3 && face.uv().size() == face.vertices().size(), "面的索引或UV数量无效。");
			require(face.normals().isEmpty() || face.normals().size() == face.vertices().size(), "面的法线数量无效。");
			require(parts.containsKey(face.partId()), "面引用了缺失部件。");
			require(new HashSet<>(face.vertices()).size() == face.vertices().size(), "面包含重复顶点索引。");

			for (int index : face.vertices()) {
				require(index >= 0 && index < definition.vertices().size(), "面的顶点引用越界。");
			}
		}

		validateAnimation(definition, parts.keySet());
	}

	private static void validateAnimation(PhotonModelDefinition definition, Set<String> parts) {
		var bones = new LinkedHashMap<String, String>();

		for (var bone : definition.bones()) {
			require(!bone.id().isEmpty() && bones.putIfAbsent(bone.id(), bone.parentId()) == null, "骨骼标识重复或为空。");
			require(bone.bind().scale().x() != 0 && bone.bind().scale().y() != 0 && bone.bind().scale().z() != 0, "骨骼绑定缩放不可为零。");
		}

		hierarchy(bones);
		var morphs = new HashSet<String>();

		for (var morph : definition.morphs()) {
			require(!morph.id().isEmpty() && morphs.add(morph.id()), "形状键标识重复或为空。");

			for (int index : morph.deltas().keySet()) {
				require(index >= 0 && index < definition.vertices().size(), "形状键顶点引用越界。");
			}
		}

		for (var entry : definition.weights().entrySet()) {
			require(entry.getKey() >= 0 && entry.getKey() < definition.vertices().size(), "权重顶点引用越界。");

			for (var weight : entry.getValue()) {
				require(bones.containsKey(weight.boneId()) && weight.value() >= 0, "骨骼权重引用或取值无效。");
			}
		}

		var clips = new HashSet<String>();

		for (var clip : definition.clips()) {
			require(!clip.id().isEmpty() && clips.add(clip.id()) && clip.duration() > 0, "片段标识或时长无效。");
			require(parts.containsAll(clip.partTracks().keySet()) && bones.keySet().containsAll(clip.boneTracks().keySet())
					&& morphs.containsAll(clip.morphTracks().keySet()), "片段轨道目标不存在。");
			clip.partTracks().values().forEach(keys -> transformTimes(keys, clip.duration()));
			clip.boneTracks().values().forEach(keys -> transformTimes(keys, clip.duration()));
			clip.morphTracks().values().forEach(keys -> valueTimes(keys, clip.duration(), false));
			valueTimes(clip.opacityKeys(), clip.duration(), true);
		}
	}

	private static void hierarchy(Map<String, String> parents) {
		for (var id : parents.keySet()) {
			var visited = new HashSet<String>();

			for (var current = id; !current.isEmpty(); current = parents.get(current)) {
				require(parents.containsKey(current) && visited.add(current), "层级存在循环或父项缺失。");
			}
		}
	}

	private static void transformTimes(List<TransformKey> keys, float duration) {
		float previous = -1;

		for (var key : keys) {
			require(key.time() >= 0 && key.time() <= duration && key.time() > previous, "变换关键帧时间无效或未排序。");
			previous = key.time();
		}
	}

	private static void valueTimes(List<ValueKey> keys, float duration, boolean opacity) {
		float previous = -1;

		for (var key : keys) {
			require(key.time() >= 0 && key.time() <= duration && key.time() > previous, "数值关键帧时间无效或未排序。");
			require(!opacity || key.value() >= 0 && key.value() <= 1, "透明度关键帧取值无效。");
			previous = key.time();
		}
	}

	private static <T> List<T> list(JsonArray values, Function<JsonElement, T> reader) {
		var result = new ArrayList<T>();

		for (var value : values) {
			result.add(reader.apply(value));
		}

		return List.copyOf(result);
	}

	private static <T> Map<String, List<T>> tracks(JsonObject values, Function<JsonElement, T> reader) {
		var result = new LinkedHashMap<String, List<T>>();

		for (var entry : values.entrySet()) {
			result.put(entry.getKey(), list(entry.getValue().getAsJsonArray(), reader));
		}

		return Map.copyOf(result);
	}

	private static JsonArray array(JsonObject value, String key) {
		return value.has(key) ? value.getAsJsonArray(key) : new JsonArray();
	}

	private static JsonObject object(JsonObject value, String key) {
		return value.has(key) ? value.getAsJsonObject(key) : new JsonObject();
	}

	private static String string(JsonObject value, String key, String fallback) {
		if (!value.has(key)) {
			return fallback;
		}

		var field = value.getAsJsonPrimitive(key);
		require(field.isString(), "标识或文本类型无效。");
		return field.getAsString();
	}

	private static boolean bool(JsonObject value, String key, boolean fallback) {
		if (!value.has(key)) {
			return fallback;
		}

		var field = value.getAsJsonPrimitive(key);
		require(field.isBoolean(), "布尔字段类型无效。");
		return field.getAsBoolean();
	}

	private static int integer(JsonObject value, String key, int fallback) {
		return value.has(key) ? integer(value.get(key)) : fallback;
	}

	private static int integer(JsonElement value) {
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "整数类型无效。");
		return new BigDecimal(value.getAsString()).intValueExact();
	}

	private static float number(JsonObject value, String key, float fallback) {
		return value.has(key) ? number(value.get(key)) : fallback;
	}

	private static float number(JsonElement value) {
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "数值类型无效。");
		float result = value.getAsFloat();
		require(Float.isFinite(result), "数值不是有限数。");
		return result;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}
}
