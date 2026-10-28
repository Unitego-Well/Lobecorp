package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.LinkedHashSet;

/// OBJ交换只表示几何、面角UV/法线和分组材质标签，不冒充完整模型工程备份。
public class PhotonModelObjUtil {
	public static JsonObject read(String text, boolean flipV) throws IOException {
		var vertices = new JsonArray();
		var uv = new JsonArray();
		var normals = new JsonArray();
		var faces = new JsonArray();
		var directives = new JsonArray();
		var parts = new LinkedHashSet<String>();
		var materials = new JsonObject();
		String part = "main";
		String material = "";
		int lineNumber = 0;
		try {
			for (var source : text.split("\\R")) {
				lineNumber++;
				String line = source.split("#", 2)[0].trim();
				if (line.isEmpty()) {
					continue;
				}
				var words = line.split("\\s+");
				switch (words[0]) {
					case "v" -> vertices.add(vector(words, 3));
					case "vt" -> {
						var point = vector(words, 2);
						if (flipV) {
							point.set(1, new JsonPrimitive(1 - point.get(1).getAsDouble()));
						}
						uv.add(point);
					}
					case "vn" -> normals.add(vector(words, 3));
					case "o", "g" -> {
						part = words.length == 1 ? "main" : line.substring(words[0].length()).trim();
						directives.add(source);
					}
					case "usemtl" -> {
						material = words.length == 1 ? "" : line.substring(words[0].length()).trim();
						if (!material.isEmpty()) {
							materials.add(material, new JsonObject());
						}
					}
					case "f" -> {
						if (words.length < 4) {
							throw new IllegalArgumentException("面至少需要三个顶点。");
						}
						var face = new JsonObject();
						var indices = new JsonArray();
						var faceUV = new JsonArray();
						var faceNormals = new JsonArray();
						for (int index = 1; index < words.length; index++) {
							var corner = words[index].split("/", -1);
							if (corner.length > 3) {
								throw new IllegalArgumentException("面角格式无效。");
							}
							indices.add(index(corner[0], vertices.size()));
							if (corner.length > 1 && !corner[1].isEmpty()) {
								faceUV.add(uv.get(index(corner[1], uv.size())).deepCopy());
							}
							if (corner.length > 2 && !corner[2].isEmpty()) {
								faceNormals.add(normals.get(index(corner[2], normals.size())).deepCopy());
							}
						}
						if (!faceUV.isEmpty() && faceUV.size() != indices.size() || !faceNormals.isEmpty() && faceNormals.size() != indices.size()) {
							throw new IllegalArgumentException("同一面混用了缺失的UV或法线。");
						}

						if (faceUV.isEmpty()) {
							for (int index = 0; index < indices.size(); index++) {
								var point = new JsonArray();
								point.add(0);
								point.add(flipV ? 1 : 0);
								faceUV.add(point);
							}
						}

						face.add("vertices", indices);
						face.add("uv", faceUV);
						face.add("normals", faceNormals);
						face.addProperty("partId", part);
						face.addProperty("material", material);
						faces.add(face);
						parts.add(part);
					}
					case "s", "mtllib" -> directives.add(source);
					default -> throw new IllegalArgumentException("不支持的OBJ指令：" + words[0]);
				}
			}
		} catch (RuntimeException invalid) {
			throw new IOException("OBJ第" + lineNumber + "行无法导入。", invalid);
		}
		var result = new JsonObject();
		result.addProperty("version", 1);
		result.add("vertices", vertices);
		result.add("faces", faces);
		result.add("materials", materials);
		var partList = new JsonArray();
		for (var id : parts) {
			var entry = new JsonObject();
			entry.addProperty("id", id);
			partList.add(entry);
		}
		if (!parts.isEmpty()) {
			result.add("parts", partList);
		}
		result.add("objDirectives", directives);
		PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(result));
		return result;
	}

	public static String write(JsonObject asset, boolean flipV) throws IOException {
		var model = PhotonModelCodec.read(asset);
		var text = new StringBuilder();
		for (var vertex : model.vertices()) {
			text.append("v ").append(vertex.x()).append(' ').append(vertex.y()).append(' ').append(vertex.z()).append('\n');
		}
		int uvOffset = 0;
		int normalOffset = 0;
		String part = null;
		String material = null;
		for (var face : model.faces()) {
			if (!face.partId().equals(part)) {
				part = face.partId();
				text.append("g ").append(part).append('\n');
			}
			if (!face.material().equals(material)) {
				material = face.material();
				text.append("usemtl ").append(material).append('\n');
			}
			for (var uv : face.uv()) {
				text.append("vt ").append(uv.x()).append(' ').append(flipV ? 1 - uv.y() : uv.y()).append('\n');
			}
			for (var normal : face.normals()) {
				text.append("vn ").append(normal.x()).append(' ').append(normal.y()).append(' ').append(normal.z()).append('\n');
			}
			text.append('f');
			for (int index = 0; index < face.vertices().size(); index++) {
				text.append(' ').append(face.vertices().get(index) + 1);
				if (!face.uv().isEmpty() || !face.normals().isEmpty()) {
					text.append('/');
					if (!face.uv().isEmpty()) {
						text.append(uvOffset + index + 1);
					}
					if (!face.normals().isEmpty()) {
						text.append('/').append(normalOffset + index + 1);
					}
				}
			}
			text.append('\n');
			uvOffset += face.uv().size();
			normalOffset += face.normals().size();
		}
		return text.toString();
	}

	private static JsonArray vector(String[] words, int size) {
		if (words.length < size + 1 || words.length > size + 2) {
			throw new IllegalArgumentException("OBJ向量分量数量无效。");
		}
		if (words[0].equals("v") && words.length == 5 && Double.parseDouble(words[4]) != 1) {
			throw new IllegalArgumentException("不支持非单位齐次坐标。");
		}
		var result = new JsonArray();
		for (int index = 1; index <= size; index++) {
			double value = Double.parseDouble(words[index]);
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException("OBJ坐标必须有限。");
			}
			result.add(value);
		}
		return result;
	}

	private static int index(String text, int size) {
		int parsed = Integer.parseInt(text);
		int index = parsed > 0 ? parsed - 1 : size + parsed;
		if (parsed == 0 || index < 0 || index >= size) {
			throw new IllegalArgumentException("OBJ索引超出范围。");
		}
		return index;
	}
}
