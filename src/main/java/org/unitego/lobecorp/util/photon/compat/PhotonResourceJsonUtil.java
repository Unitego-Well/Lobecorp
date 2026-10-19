package org.unitego.lobecorp.util.photon.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/// 在Gson构建树之前检查重复字段、深度与节点预算，原字符串另由源文档保留。
public class PhotonResourceJsonUtil {
	public static JsonElement read(byte[] bytes, PhotonResourceLimits limits) throws IOException {
		var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		return read(text, limits);
	}

	public static JsonElement read(String text, PhotonResourceLimits limits) throws IOException {
		try (var reader = new JsonReader(new StringReader(text))) {
			reader.setStrictness(Strictness.STRICT);
			var count = new int[1];
			validate(reader, limits, count, 0);

			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new IOException("JSON根节点后存在额外内容。");
			}
		}

		try {
			return JsonParser.parseString(text);
		} catch (RuntimeException failure) {
			throw new IOException("JSON数据无法解码。", failure);
		}
	}

	private static void validate(JsonReader reader, PhotonResourceLimits limits, int[] count, int depth) throws IOException {
		if (depth > limits.depth() || ++count[0] > limits.jsonNodes()) {
			throw new IOException("JSON数据超出复杂度预算。");
		}

		switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				var keys = new HashSet<String>();

				while (reader.hasNext()) {
					if (!keys.add(reader.nextName())) {
						throw new IOException("JSON对象包含重复字段。");
					}

					validate(reader, limits, count, depth + 1);
				}

				reader.endObject();
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();

				while (reader.hasNext()) {
					validate(reader, limits, count, depth + 1);
				}

				reader.endArray();
			}
			case STRING, NUMBER -> reader.nextString();
			case BOOLEAN -> reader.nextBoolean();
			case NULL -> reader.nextNull();
			default -> throw new IOException("JSON节点类型无效。");
		}
	}
}
