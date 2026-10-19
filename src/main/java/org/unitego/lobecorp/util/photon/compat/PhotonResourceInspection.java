package org.unitego.lobecorp.util.photon.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// 生成可追踪的原始数据索引，不将未知注册类型交给会跳过内容的原生解码器。
public class PhotonResourceInspection {
	public static final List<String> LEGACY_TYPES = List.of("lobecorp_animation_controller", "lobecorp_emitter_spawner",
			"lobecorp_model_emitter", "lobecorp_child_animation", "lobecorp_animation_call", "lobecorp_model_animation");
	private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

	public record Value(String path, String value) {
	}

	public record Model(String path, int version, int vertices, int faces, int parts, int materials,
	                    int bones, int morphs, int clips) {
	}

	public record Report(String origin, PhotonResourceDocument.Kind kind, String sha256, long sourceBytes,
	                     Map<String, Integer> types, List<Value> legacyFields, List<Value> identities,
	                     List<Value> references, List<Value> versions, List<Model> models,
	                     List<Report> entries) {
		public Report {
			types = Map.copyOf(types);
			legacyFields = List.copyOf(legacyFields);
			identities = List.copyOf(identities);
			references = List.copyOf(references);
			versions = List.copyOf(versions);
			models = List.copyOf(models);
			entries = List.copyOf(entries);
		}

		/// 数据被保留与被完整适配是两件事，R0不开放运行门禁。
		public boolean runtimeReady() {
			return false;
		}
	}

	public static boolean isLegacyType(String value) {
		int separator = value.indexOf(':');
		return LEGACY_TYPES.contains(separator < 0 ? value : value.substring(separator + 1));
	}

	public static boolean isLegacyField(String key) {
		return key.startsWith("lobecorp$") || key.equals("lobecorpActivation") || key.equals("lobecorp_animation_preview");
	}

	public static Report inspect(PhotonResourceDocument document) throws IOException {
		return inspect(document, PhotonResourceLimits.DEFAULT);
	}

	public static Report inspect(PhotonResourceDocument document, PhotonResourceLimits limits) throws IOException {
		var collector = new Collector(limits);
		var nbt = document.nbt();
		var json = document.json();

		if (nbt != null) {
			collector.nbt(nbt, "", 0);
		}

		if (json != null) {
			collector.json(json, "", 0);
		}

		var children = new ArrayList<Report>();

		for (var entry : document.entries()) {
			children.add(inspect(entry, limits));
		}

		return new Report(document.origin(), document.kind(), document.sha256(), document.sourceBytes(),
				collector.types, collector.legacyFields, collector.identities, collector.references,
				collector.versions, collector.models, children);
	}

	private static class Collector {
		private final PhotonResourceLimits limits;
		private final Map<String, Integer> types = new LinkedHashMap<>();
		private final List<Value> legacyFields = new ArrayList<>();
		private final List<Value> identities = new ArrayList<>();
		private final List<Value> references = new ArrayList<>();
		private final List<Value> versions = new ArrayList<>();
		private final List<Model> models = new ArrayList<>();
		private int nodes;

		private Collector(PhotonResourceLimits limits) {
			this.limits = limits;
		}

		private void visit(int depth) throws IOException {
			if (depth > limits.depth() || ++nodes > limits.jsonNodes()) {
				throw new IOException("资源索引超出复杂度预算。");
			}
		}

		private void nbt(Tag tag, String path, int depth) throws IOException {
			visit(depth);

			if (tag instanceof CompoundTag compound) {
				for (var entry : compound.entrySet()) {
					var child = pointer(path, entry.getKey());
					var value = entry.getValue();

					if (isLegacyField(entry.getKey())) {
						legacyFields.add(new Value(child, value instanceof CompoundTag ? "compound" : value.toString()));
					}

					if (entry.getKey().equals("version")) {
						versions.add(new Value(child, value.toString()));
					}

					if (entry.getKey().equals("type") && value instanceof StringTag(var typeName)) {
						types.merge(typeName, 1, Integer::sum);
					}

					if ((entry.getKey().equals("asset") || entry.getKey().equals("assetJson"))
							&& (value instanceof ByteArrayTag || value instanceof StringTag)) {
						var asset = switch (value) {
							case ByteArrayTag bytes -> PhotonResourceJsonUtil.read(bytes.getAsByteArray(), limits);
							case StringTag(var text) -> PhotonResourceJsonUtil.read(text, limits);
							default -> throw new IOException("模型快照的数据类型无效。");
						};
						json(asset, child + "/$json", depth + 1);
					} else {
						nbt(value, child, depth + 1);
					}
				}
			} else if (tag instanceof ListTag list) {
				for (int index = 0; index < list.size(); index++) {
					nbt(list.get(index), pointer(path, Integer.toString(index)), depth + 1);
				}
			} else if (tag instanceof StringTag(var value)) {
				string(value, path);
			}
		}

		private void json(JsonElement element, String path, int depth) throws IOException {
			visit(depth);

			if (element.isJsonObject()) {
				var object = element.getAsJsonObject();

				if (object.has("vertices") && object.has("faces")) {
					models.add(model(object, path));
				}

				for (var entry : object.entrySet()) {
					var child = pointer(path, entry.getKey());
					var value = entry.getValue();

					if (entry.getKey().equals("version")) {
						versions.add(new Value(child, value.toString()));
					}

					if (entry.getKey().equals("type") && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
						types.merge(value.getAsString(), 1, Integer::sum);
					}

					json(value, child, depth + 1);
				}
			} else if (element.isJsonArray()) {
				var array = element.getAsJsonArray();

				for (int index = 0; index < array.size(); index++) {
					json(array.get(index), pointer(path, Integer.toString(index)), depth + 1);
				}
			} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				string(element.getAsString(), path);
			}
		}

		private void string(String value, String path) {
			if (UUID.matcher(value).matches()) {
				identities.add(new Value(path, value));
			} else if (RESOURCE_ID.matcher(value).matches() || value.startsWith("file(") || value.startsWith("built-in(")) {
				references.add(new Value(path, value));
			}
		}

		private static Model model(JsonObject object, String path) throws IOException {
			try {
				return new Model(path, object.has("version") ? object.get("version").getAsInt() : -1,
						size(object, "vertices"), size(object, "faces"), size(object, "parts"), size(object, "materials"),
						size(object, "bones"), size(object, "morphs"), size(object, "clips"));
			} catch (RuntimeException failure) {
				throw new IOException("内嵌模型结构无法索引：" + path, failure);
			}
		}

		private static int size(JsonObject object, String key) {
			var value = object.get(key);

			if (value == null || value.isJsonNull()) {
				return 0;
			}

			return value.isJsonObject() ? value.getAsJsonObject().size() : value.getAsJsonArray().size();
		}

		private static String pointer(String parent, String key) {
			return parent + "/" + key.replace("~", "~0").replace("/", "~1");
		}
	}
}
