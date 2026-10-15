package org.unitego.lobecorp.client.animation;

import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.network.tc.animation.LcAnimationContentPayload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/// 服务器基础内容仅存在当前连接内，不写资源包或本地磁盘。
public class LcClientAnimationContents {
	private static final Map<String, LcAnimationContent> CONTENT = new HashMap<>();
	private static final Map<String, byte[][]> PENDING = new HashMap<>();

	public static @Nullable LcAnimationContent get(String version) {
		return CONTENT.get(version);
	}

	public static void receive(LcAnimationContentPayload payload) {
		if (CONTENT.containsKey(payload.version())) {
			return;
		}

		if (payload.count() <= 0 || payload.index() < 0 || payload.index() >= payload.count()) {
			throw new IllegalArgumentException(payload.version());
		}

		var chunks = PENDING.computeIfAbsent(payload.version(), _ -> new byte[payload.count()][]);
		if (chunks.length != payload.count()) {
			throw new IllegalArgumentException(payload.version());
		}

		chunks[payload.index()] = payload.bytes();
		var bytes = new ByteArrayOutputStream();
		for (var chunk : chunks) {
			if (chunk == null) {
				return;
			}

			bytes.writeBytes(chunk);
		}

		var json = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
		var content = LcAnimationContent.of(payload.modelId(), payload.animationId(),
				json.get("model").getAsString(), json.get("animation").getAsString());
		if (!content.version().equals(payload.version())) {
			throw new IllegalArgumentException(payload.version());
		}

		CONTENT.put(content.version(), content);
		PENDING.remove(content.version());
		LcClientAnimations.contentReady(content.version());
	}

	public static void clear() {
		CONTENT.clear();
		PENDING.clear();
	}
}
