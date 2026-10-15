package org.unitego.lobecorp.util.photon.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Face;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampleParameters;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.PreparedModel;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/// 独立的版本化资产协议与标准 OBJ 交换；动画时间使用游戏 tick。
public class PhotonModelIOUtil {
	/// 模型 JSON 的严格编解码入口，集中配置有限数值与向量适配器。
	private static final Gson GSON = createGson();
	/// 只缓存纯资产数据；限制来源字符总量和条目数，避免长期保留已关闭工程的大模型。
	private static final int MAX_CACHED_SOURCE_CHARACTERS = 4 * 1024 * 1024;
	private static final int MAX_CACHED_ASSETS = 32;

	/// 独立延迟初始化，结构热替换不依赖既有工具类重新执行静态初始化器。
	private static class AssetCache {
		private static final LinkedHashMap<String, CachedAsset> ASSETS = new LinkedHashMap<>(16, 0.75f, true);
		private static int sourceCharacters;
	}

	/// 派生数据与来源共用同一 LRU 预算；仅资产读取入口向外返回可编辑副本。
	private static class CachedAsset {
		private final PhotonModelAsset asset;
		private PreparedModel prepared;
		private List<String> textures;
		private CompoundTag materials;

		private CachedAsset(PhotonModelAsset asset) {
			this.asset = asset.copy();
		}
	}

	public static PreparedModel prepareJson(String text) {
		var cached = cachedAsset(text);

		synchronized (cached) {
			if (cached.prepared == null) {
				cached.prepared = PhotonModelSamplerUtil.prepare(cached.asset);
			}

			return cached.prepared;
		}
	}

	public static List<String> textureDependencies(String text) {
		var cached = cachedAsset(text);

		synchronized (cached) {
			if (cached.textures == null) {
				cached.textures = cached.asset.materials.values().stream().map(material -> material.texture).distinct().toList();
			}

			return cached.textures;
		}
	}

	/// 原生打包器会重写依赖路径，只能拿到结构化快照的副本。
	public static CompoundTag materialDependencies(String text) {
		var cached = cachedAsset(text);

		synchronized (cached) {
			if (cached.materials == null) {
				cached.materials = PhotonModelMaterialDataUtil.dependencies(cached.asset);
			}

			return cached.materials.copy();
		}
	}

	private static CachedAsset cachedAsset(String text) {
		var assets = AssetCache.ASSETS;

		synchronized (assets) {
			var cached = assets.get(text);

			if (cached != null) {
				return cached;
			}
		}

		var asset = readJson(text);

		synchronized (assets) {
			var cached = assets.get(text);
			return cached == null ? new CachedAsset(asset) : cached;
		}
	}

	private static Gson createGson() {
		var integerAdapter = new TypeAdapter<Integer>() {
			@Override
			public void write(JsonWriter out, Integer value) throws IOException {
				out.value(value);
			}

			@Override
			public Integer read(JsonReader in) throws IOException {
				requireToken(in, JsonToken.NUMBER);

				try {
					return new BigDecimal(in.nextString()).intValueExact();
				} catch (ArithmeticException exception) {
					throw new JsonSyntaxException("integer", exception);
				}
			}
		};
		var floatAdapter = new TypeAdapter<Float>() {
			@Override
			public void write(JsonWriter out, Float value) throws IOException {
				out.value(value);
			}

			@Override
			public Float read(JsonReader in) throws IOException {
				requireToken(in, JsonToken.NUMBER);
				float value = Float.parseFloat(in.nextString());
				PhotonModelValidationUtil.require(Float.isFinite(value), "number");
				return value;
			}
		};
		var indexAdapter = new TypeAdapter<Integer>() {
			@Override
			public void write(JsonWriter out, Integer value) throws IOException {
				out.value(value);
			}

			@Override
			public Integer read(JsonReader in) throws IOException {
				if (in.peek() == JsonToken.STRING) {
					return new BigDecimal(in.nextString()).intValueExact();
				}

				return integerAdapter.read(in);
			}
		};
		var booleanAdapter = new TypeAdapter<Boolean>() {
			@Override
			public void write(JsonWriter out, Boolean value) throws IOException {
				out.value(value);
			}

			@Override
			public Boolean read(JsonReader in) throws IOException {
				requireToken(in, JsonToken.BOOLEAN);
				return in.nextBoolean();
			}
		};
		return new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().disableJdkUnsafe()
				.registerTypeAdapter(Vector2f.class, new Vector2Adapter())
				.registerTypeAdapter(Vector3f.class, new Vector3Adapter())
				.registerTypeAdapter(Quaternionf.class, new QuaternionAdapter())
				.registerTypeAdapter(int.class, integerAdapter)
				.registerTypeAdapter(Integer.class, indexAdapter)
				.registerTypeAdapter(float.class, floatAdapter)
				.registerTypeAdapter(Float.class, floatAdapter)
				.registerTypeAdapter(boolean.class, booleanAdapter)
				.registerTypeAdapter(Boolean.class, booleanAdapter)
				.registerTypeAdapter(String.class, new StringAdapter())
				.create();
	}

	public static PhotonModelAsset readJson(String text) {
		var assets = AssetCache.ASSETS;

		synchronized (assets) {
			var cached = assets.get(text);

			if (cached != null) {
				return cached.asset.copy();
			}
		}

		JsonObject root = jsonObject(text);
		PhotonModelValidationUtil.require(root != null && root.has("version")
				&& root.get("version").isJsonPrimitive()
				&& root.getAsJsonPrimitive("version").isNumber(), "version");
		int version = root.getAsJsonPrimitive("version").getAsBigDecimal().intValueExact();
		PhotonModelValidationUtil.require(version == PhotonModelAsset.FORMAT_VERSION, "version");
		var asset = GSON.fromJson(root, PhotonModelAsset.class);
		asset.validate();
		rememberAsset(text, asset);
		return asset;
	}

	/// 缓存副本不向调用方暴露；内容变化使用不同键，损坏输入不进入缓存。
	private static void rememberAsset(String text, PhotonModelAsset asset) {
		if (text.length() > MAX_CACHED_SOURCE_CHARACTERS) {
			return;
		}

		var assets = AssetCache.ASSETS;

		synchronized (assets) {
			if (assets.get(text) != null) {
				return;
			}

			assets.put(text, new CachedAsset(asset));
			AssetCache.sourceCharacters += text.length();

			while (assets.size() > MAX_CACHED_ASSETS || AssetCache.sourceCharacters > MAX_CACHED_SOURCE_CHARACTERS) {
				AssetCache.sourceCharacters -= assets.pollFirstEntry().getKey().length();
			}
		}
	}

	private static JsonObject jsonObject(String text) {
		try (var reader = GSON.newJsonReader(new StringReader(text))) {
			JsonElement root = jsonElement(reader);
			requireToken(reader, JsonToken.END_DOCUMENT);
			PhotonModelValidationUtil.require(root.isJsonObject(), "root");
			return root.getAsJsonObject();
		} catch (IOException exception) {
			throw new JsonSyntaxException(exception);
		}
	}

	private static JsonElement jsonElement(JsonReader reader) throws IOException {
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				var object = new JsonObject();

				while (reader.hasNext()) {
					String name = reader.nextName();
					PhotonModelValidationUtil.require(!object.has(name), "json.duplicate");
					object.add(name, jsonElement(reader));
				}

				reader.endObject();
				yield object;
			}

			case BEGIN_ARRAY -> {
				reader.beginArray();
				var array = new JsonArray();

				while (reader.hasNext()) {
					array.add(jsonElement(reader));
				}

				reader.endArray();
				yield array;
			}

			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}

			default -> throw new JsonSyntaxException(reader.getPath());
		};
	}

	public static String writeJson(PhotonModelAsset asset) {
		asset.validate();
		var text = GSON.toJson(asset);
		rememberAsset(text, asset);
		return text;
	}

	public static PhotonModelAsset readObj(String text, boolean flipV) {
		var asset = new PhotonModelAsset();
		var parts = new LinkedHashMap<String, Part>();
		parts.put(PhotonModelAsset.DEFAULT_PART_ID, asset.parts.getFirst());
		var uv = new ArrayList<Vector2f>();
		var normals = new ArrayList<Vector3f>();
		String partId = PhotonModelAsset.DEFAULT_PART_ID;
		String material = "";
		var continued = new StringBuilder();

		for (String raw : text.split("\\R")) {
			String line = raw;
			int comment = line.indexOf('#');

			if (comment >= 0) {
				line = line.substring(0, comment);
			}

			line = line.trim();

			if (line.endsWith("\\")) {
				continued.append(line, 0, line.length() - 1).append(' ');
				continue;
			}

			line = continued + line;
			continued.setLength(0);

			if (line.isBlank()) {
				continue;
			}

			String[] tokens = line.split("\\s+");

			switch (tokens[0]) {
				case "v" -> asset.vertices.add(position(tokens));
				case "vt" -> {
					PhotonModelValidationUtil.require(tokens.length >= 2, "obj.vt");
					float v = tokens.length > 2 ? number(tokens[2]) : 0f;
					uv.add(new Vector2f(number(tokens[1]), flipV ? 1f - v : v));
				}

				case "vn" -> normals.add(position(tokens));
				case "o", "g" -> {
					partId = tokens.length > 1 ? line.substring(tokens[0].length()).trim()
							: PhotonModelAsset.DEFAULT_PART_ID;

					if (!parts.containsKey(partId)) {
						var part = new Part();
						part.id = partId;
						asset.parts.add(part);
						parts.put(partId, part);
					}
				}

				case "usemtl" -> material = tokens.length > 1 ? line.substring(tokens[0].length()).trim() : "";
				case "f" -> asset.faces.add(face(tokens, asset.vertices.size(), uv, normals, partId, material));
				default -> {
					// OBJ 的材质库和光滑组保留为外部交换信息，不参与可编辑几何引用。
				}
			}
		}

		PhotonModelValidationUtil.require(continued.isEmpty(), "obj.continuation");
		updateReferenceSize(asset);
		asset.validate();
		return asset;
	}

	private static Vector3f position(String[] tokens) {
		PhotonModelValidationUtil.require(tokens.length >= 4, "obj.position");
		var result = new Vector3f(number(tokens[1]), number(tokens[2]), number(tokens[3]));

		if (tokens[0].equals("v") && tokens.length == 5) {
			float homogeneous = number(tokens[4]);
			PhotonModelValidationUtil.require(homogeneous != 0f, "obj.w");
			result.div(homogeneous);
		}

		return result;
	}

	private static Face face(String[] tokens, int positionCount, List<Vector2f> uv,
	                         List<Vector3f> normals, String partId, String material) {
		PhotonModelValidationUtil.require(tokens.length >= 4, "obj.face");
		var face = new Face();
		int count = tokens.length - 1;
		face.vertices = new int[count];
		face.uv = new Vector2f[count];
		face.normals = new Vector3f[count];
		face.partId = partId;
		face.material = material;
		boolean allNormals = true;

		for (int i = 0; i < count; i++) {
			String[] references = tokens[i + 1].split("/", -1);
			PhotonModelValidationUtil.require(references.length <= 3, "obj.reference");
			face.vertices[i] = index(references[0], positionCount);
			int uvIndex = references.length > 1 && !references[1].isEmpty()
					? index(references[1], uv.size()) : -1;
			face.uv[i] = uvIndex < 0 ? new Vector2f() : new Vector2f(uv.get(uvIndex));
			int normalIndex = references.length > 2 && !references[2].isEmpty()
					? index(references[2], normals.size()) : -1;
			face.normals[i] = normalIndex < 0 ? new Vector3f() : new Vector3f(normals.get(normalIndex));
			allNormals &= normalIndex >= 0;
		}

		if (!allNormals) {
			face.normals = new Vector3f[0];
		}

		return face;
	}

	private static int index(String text, int count) {
		int raw = Integer.parseInt(text);
		int index = raw < 0 ? count + raw : raw - 1;
		PhotonModelValidationUtil.require(raw != 0 && index >= 0 && index < count, "obj.index");
		return index;
	}

	private static float number(String text) {
		float value = Float.parseFloat(text);
		PhotonModelValidationUtil.require(Float.isFinite(value), "obj.number");
		return value;
	}

	private static void updateReferenceSize(PhotonModelAsset asset) {
		if (asset.vertices.isEmpty()) {
			return;
		}

		var min = new Vector3f(Float.POSITIVE_INFINITY);
		var max = new Vector3f(Float.NEGATIVE_INFINITY);

		for (var position : asset.vertices) {
			min.min(position);
			max.max(position);
		}

		var extent = new Vector3f(max).sub(min);
		float length = extent.get(asset.forwardAxis);

		if (length > 0f && Float.isFinite(length)) {
			asset.referenceLength = length;
		}

		float width = Math.max(extent.get((asset.forwardAxis + 1) % 3),
				extent.get((asset.forwardAxis + 2) % 3));

		if (width > 0f && Float.isFinite(width)) {
			asset.referenceWidth = width;
		}
	}

	/// 导出默认姿态的三角网格，烘焙部件变换和默认形状键；完整控制仍保存在 JSON 中。
	public static String writeObj(PhotonModelAsset asset, boolean flipV) {
		asset = bakeObjAsset(asset);
		var text = new StringBuilder();

		for (var position : asset.vertices) {
			vector(text, "v", position.x, position.y, position.z);
		}

		for (var face : asset.faces) {
			for (var uv : face.uv) {
				text.append("vt ").append(uv.x).append(' ').append(flipV ? 1f - uv.y : uv.y).append('\n');
			}
		}

		for (var face : asset.faces) {
			var normal = PhotonModelPolygonUtil.normal(asset.vertices, face.vertices);

			for (int i = 0; i < face.vertices.length; i++) {
				var source = face.normals.length == 0 ? normal : face.normals[i];
				vector(text, "vn", source.x, source.y, source.z);
			}
		}

		int cornerIndex = 1;
		String currentPart = null;
		String currentMaterial = null;

		for (var face : asset.faces) {
			if (!face.partId.equals(currentPart)) {
				text.append("o ").append(identifier(face.partId)).append('\n');
				currentPart = face.partId;
			}

			if (!face.material.equals(currentMaterial)) {
				text.append("usemtl ").append(identifier(face.material)).append('\n');
				currentMaterial = face.material;
			}

			text.append('f');

			for (int vertex : face.vertices) {
				text.append(' ').append(vertex + 1).append('/').append(cornerIndex).append('/').append(cornerIndex);
				cornerIndex++;
			}

			text.append('\n');
		}

		return text.toString();
	}

	private static PhotonModelAsset bakeObjAsset(PhotonModelAsset source) {
		source.validate();
		var sampled = PhotonModelSamplerUtil.sample(source, "", 0f,
				new SampleParameters());
		var result = new PhotonModelAsset();
		result.parts.clear();
		var partInsideOut = new LinkedHashMap<String, Boolean>();

		for (var part : source.parts) {
			var bakedPart = new Part();
			bakedPart.id = part.id;
			result.parts.add(bakedPart);
			partInsideOut.put(part.id, part.insideOut);
		}

		var indices = new LinkedHashMap<VertexKey, Integer>();

		for (var face : source.faces) {
			int[] triangles = PhotonModelPolygonUtil.triangulate(source.vertices, face.vertices);
			boolean insideOut = partInsideOut.get(face.partId);

			for (int i = 0; i < triangles.length; i += 3) {
				var baked = new Face();
				baked.vertices = new int[3];
				baked.uv = new Vector2f[3];
				baked.partId = face.partId;
				baked.material = face.material;

				for (int corner = 0; corner < 3; corner++) {
					int triangleCorner = insideOut && corner > 0 ? 3 - corner : corner;
					int sourceCorner = triangles[i + triangleCorner];
					int sourceIndex = face.vertices[sourceCorner];
					var key = new VertexKey(face.partId, sourceIndex);
					Integer index = indices.get(key);

					if (index == null) {
						index = result.vertices.size();
						indices.put(key, index);
						result.vertices.add(new Vector3f(sampled.partVertices().get(face.partId).get(sourceIndex)));
					}

					baked.vertices[corner] = index;
					baked.uv[corner] = new Vector2f(face.uv[sourceCorner]);
				}

				result.faces.add(baked);
			}
		}

		result.validate();
		return result;
	}

	private record VertexKey(String partId, int index) {
	}

	private static void vector(StringBuilder text, String directive, float x, float y, float z) {
		text.append(directive).append(' ').append(x).append(' ').append(y).append(' ').append(z).append('\n');
	}

	private static String identifier(String value) {
		PhotonModelValidationUtil.require(value.indexOf('\n') < 0 && value.indexOf('\r') < 0
				&& value.indexOf('#') < 0 && value.indexOf('\\') < 0, "obj.identifier");
		return value;
	}

	private static float[] readVector(JsonReader reader, int size) throws IOException {
		requireToken(reader, JsonToken.BEGIN_ARRAY);
		reader.beginArray();
		float[] values = new float[size];

		for (int i = 0; i < size; i++) {
			requireToken(reader, JsonToken.NUMBER);
			values[i] = Float.parseFloat(reader.nextString());
			PhotonModelValidationUtil.require(Float.isFinite(values[i]), "vector");
		}

		PhotonModelValidationUtil.require(!reader.hasNext(), "vector.size");
		reader.endArray();
		return values;
	}

	private static void requireToken(JsonReader reader, JsonToken token) throws IOException {
		if (reader.peek() != token) {
			throw new JsonSyntaxException(reader.getPath());
		}
	}

	private static class StringAdapter extends TypeAdapter<String> {
		@Override
		public void write(JsonWriter out, String value) throws IOException {
			out.value(value);
		}

		@Override
		public String read(JsonReader in) throws IOException {
			requireToken(in, JsonToken.STRING);
			return in.nextString();
		}
	}

	private static class Vector2Adapter extends TypeAdapter<Vector2f> {
		@Override
		public void write(JsonWriter out, Vector2f value) throws IOException {
			out.beginArray().value(value.x).value(value.y).endArray();
		}

		@Override
		public Vector2f read(JsonReader in) throws IOException {
			float[] values = readVector(in, 2);
			return new Vector2f(values[0], values[1]);
		}
	}

	private static class Vector3Adapter extends TypeAdapter<Vector3f> {
		@Override
		public void write(JsonWriter out, Vector3f value) throws IOException {
			out.beginArray().value(value.x).value(value.y).value(value.z).endArray();
		}

		@Override
		public Vector3f read(JsonReader in) throws IOException {
			float[] values = readVector(in, 3);
			return new Vector3f(values[0], values[1], values[2]);
		}
	}

	private static class QuaternionAdapter extends TypeAdapter<Quaternionf> {
		@Override
		public void write(JsonWriter out, Quaternionf value) throws IOException {
			out.beginArray().value(value.x).value(value.y).value(value.z).value(value.w).endArray();
		}

		@Override
		public Quaternionf read(JsonReader in) throws IOException {
			float[] values = readVector(in, 4);
			return new Quaternionf(values[0], values[1], values[2], values[3]);
		}
	}
}
