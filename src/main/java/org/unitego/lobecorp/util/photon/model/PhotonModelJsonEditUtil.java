package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

/// 单字段修改不要求客户端重传完整网格列表；原树的其他字段和数组条目保持。
public class PhotonModelJsonEditUtil {
	private static final Set<String> ROOTS = Set.of("vertices", "faces", "parts", "materials", "bones", "weights", "morphs", "clips", "referenceLength", "referenceWidth", "forwardAxis");

	public static JsonObject set(JsonObject source, String pointer, JsonElement value) {
		if (value == null || value.isJsonNull() || !pointer.startsWith("/") || pointer.length() > 2048) {
			throw new IllegalArgumentException("模型字段路径或值无效。");
		}
		var path = List.of(pointer.substring(1).split("/", -1));
		if (path.size() > 64 || !ROOTS.contains(decode(path.getFirst()))) {
			throw new IllegalArgumentException("模型字段路径不在可编辑范围内。");
		}
		var result = source.deepCopy();
		JsonElement parent = result;
		for (int index = 0; index < path.size() - 1; index++) {
			parent = child(parent, decode(path.get(index)));
		}
		String key = decode(path.getLast());
		if (key.equals("id")) {
			throw new IllegalArgumentException("模型身份字段必须通过包含引用更新的完整资产事务修改。");
		}
		if (parent.isJsonObject()) {
			if (!parent.getAsJsonObject().has(key)) {
				throw new IllegalArgumentException("模型字段不存在，请显式提交结构补丁。");
			}
			parent.getAsJsonObject().add(key, value.deepCopy());
		} else if (parent.isJsonArray()) {
			parent.getAsJsonArray().set(index(key, parent.getAsJsonArray().size()), value.deepCopy());
		} else {
			throw new IllegalArgumentException("模型字段父容器无效。");
		}
		return result;
	}

	private static JsonElement child(JsonElement parent, String key) {
		if (parent.isJsonObject() && parent.getAsJsonObject().has(key)) {
			return parent.getAsJsonObject().get(key);
		}
		if (parent.isJsonArray()) {
			return parent.getAsJsonArray().get(index(key, parent.getAsJsonArray().size()));
		}
		throw new IllegalArgumentException("模型路径不存在：" + key);
	}

	private static int index(String key, int size) {
		if (!key.matches("0|[1-9][0-9]*")) {
			throw new IllegalArgumentException("模型数组下标无效。");
		}
		int index = Integer.parseInt(key);
		if (index >= size) {
			throw new IllegalArgumentException("模型数组下标超出范围。");
		}
		return index;
	}

	private static String decode(String token) {
		if (token.matches(".*~(?![01]).*")) {
			throw new IllegalArgumentException("JSON Pointer转义无效。");
		}
		return token.replace("~1", "/").replace("~0", "~");
	}
}
