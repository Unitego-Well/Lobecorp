package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/// 把「OBJ 几何 + .mfx.nbt 附加数据」合并成模型资产 JSON，供 `PhotonModelCodec` 统一解析。
///
/// 拆分目标是让模型本体成为外部可直接编辑的 OBJ，而骨骼、蒙皮、形变、动画与材质参数
/// 保留在与 `.shader_graph.nbt` 同风格的独立 NBT 中；两者以同目录同文件名配对。
/// 合并只补齐 OBJ 无法表达的字段，不覆盖 OBJ 已提供的几何，因此任一来源缺失都会明确报错。
public class PhotonModelSplitUtil {
	/// 附加数据文件名后缀，与 `.shader_graph.nbt` 的组织方式一致。
	public static final String DATA_SUFFIX = ".mfx.nbt";
	/// 附加数据里保存原 JSON 数组的键前缀。
	private static final String RAW_PREFIX = "raw_";

	private PhotonModelSplitUtil() {
	}

	/// 合并 OBJ 文本与附加 NBT；`data` 为 null 时只接受几何完备的资产。
	///
	/// 附加 NBT 兼容两种写法：顶层直接是字段，或包一层 `model` 复合标签；两者都按同一逻辑读取。
	public static JsonObject merge(String objText, CompoundTag data, boolean flipV) throws IOException {
		var geometry = PhotonModelObjUtil.read(objText, flipV);

		if (data == null) {
			return geometry;
		}

		var fields = readData(data);

		if (fields == null) {
			return geometry;
		}

		var result = geometry;

		if (fields.contains("version")) {
			result.addProperty("version", fields.getInt("version").orElse(1));
		}

		if (fields.contains("referenceLength")) {
			result.addProperty("referenceLength", fields.getFloat("referenceLength").orElse(1f));
		}

		if (fields.contains("referenceWidth")) {
			result.addProperty("referenceWidth", fields.getFloat("referenceWidth").orElse(1f));
		}

		if (fields.contains("forwardAxis")) {
			result.addProperty("forwardAxis", fields.getInt("forwardAxis").orElse(2));
		}

		if (fields.get("materials") instanceof CompoundTag materials) {
			result.add("materials", readMaterials(materials, result.get("materials")));
		}

		for (String key : new String[]{"parts", "bones", "weights", "morphs", "clips"}) {
			var raw = fields.get(RAW_PREFIX + key);

			if (raw == null) {
				continue;
			}

			var text = raw.asString().orElse("");
			if (text.isBlank()) {
				throw new IOException("模型附加数据中的 " + key + " 不是有效 JSON。");
			}

			try {
				var parsed = JsonParser.parseString(text);
				if (parsed.isJsonArray()) {
					result.add(key, parsed.getAsJsonArray());
				} else if (parsed.isJsonObject()) {
					result.add(key, parsed.getAsJsonObject());
				} else {
					throw new IOException("模型附加数据中的 " + key + " 必须是 JSON 数组或对象。");
				}
			} catch (RuntimeException invalid) {
				throw new IOException("模型附加数据中的 " + key + " 不是有效 JSON。", invalid);
			}
		}

		return result;
	}

	/// 材质按名字合并：OBJ 只提供名字，附加 NBT 提供纹理与参数；OBJ 未列出的材质丢弃。
	private static JsonObject readMaterials(CompoundTag source, JsonElement geometryMaterials) {
		var result = new JsonObject();
		var names = new java.util.LinkedHashSet<String>();

		if (geometryMaterials != null && geometryMaterials.isJsonObject()) {
			names.addAll(geometryMaterials.getAsJsonObject().keySet());
		}

		for (var name : source.keySet()) {
			if (names.add(name)) {
				// 附加数据里出现但几何没用到的材质不写入，避免模型引用不存在的材质。
			}
		}

		for (var name : names) {
			var entry = new JsonObject();

			if (source.get(name) instanceof CompoundTag tag) {
				tag.getString("texture").filter(value -> !value.isBlank()).ifPresent(value -> entry.addProperty("texture", value));
				tag.getString("materialData").filter(value -> !value.isBlank())
						.ifPresent(value -> entry.addProperty("materialData", value));
				tag.getString("tint").filter(value -> !value.isBlank()).ifPresent(value -> entry.addProperty("tint", value));
				tag.getBoolean("overlay").ifPresent(value -> entry.addProperty("overlay", value));
			}

			result.add(name, entry);
		}

		return result;
	}

	/// 读取附加 NBT 资源；缺失返回 null，由调用方决定是否允许只有几何。
	public static CompoundTag readData(Tag tag) throws IOException {
		if (tag == null) {
			return null;
		}

		if (!(tag instanceof CompoundTag compound)) {
			throw new IOException("模型附加数据必须是 NBT 复合标签。");
		}

		var model = compound.get("model");

		if (model instanceof CompoundTag inner) {
			return inner;
		}

		if (model != null) {
			throw new IOException("模型附加数据的 model 必须是 NBT 复合标签。");
		}

		return compound;
	}

	/// 由模型资源路径推导同目录的附加数据路径；`foo.obj` → `foo.mfx.nbt`。
	public static String dataPath(String modelPath) {
		var dot = modelPath.lastIndexOf('.');

		if (dot < 0) {
			return modelPath + DATA_SUFFIX;
		}

		return modelPath.substring(0, dot) + DATA_SUFFIX;
	}

	/// 读取与模型同目录配对的附加 NBT；仅缺失时返回 null，损坏资源必须明确失败。
	public static CompoundTag companionData(Identifier model) throws IOException {
		return companionData(Minecraft.getInstance().getResourceManager(), model);
	}

	public static CompoundTag companionData(ResourceProvider resources, Identifier model) throws IOException {
		var document = companionDocument(resources, model);
		return document == null ? null : readData(document.nbt());
	}

	/// 保留伴生原字节指纹，供组合资产身份使用；缺失与存在的空 NBT 明确区分。
	public static @Nullable PhotonResourceDocument companionDocument(ResourceProvider resources, Identifier model) throws IOException {
		if (model == null) {
			return null;
		}

		var id = Identifier.fromNamespaceAndPath(model.getNamespace(), dataPath(model.getPath()));
		var resource = resources.getResource(id).orElse(null);

		if (resource == null) {
			return null;
		}

		try (var input = resource.open()) {
			var document = PhotonResourceReader.read(resource.sourcePackId() + ":" + id,
					PhotonResourceDocument.Kind.NBT, input, PhotonResourceLimits.DEFAULT);
			readData(document.nbt());
			return document;
		} catch (IOException | RuntimeException unreadable) {
			throw new IOException("模型附加数据无法读取：" + id, unreadable);
		}
	}


    /// 便于诊断：把合并结果写成文本，不参与运行路径。
	public static String describe(JsonObject merged) {
		return new String(merged.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}
}
