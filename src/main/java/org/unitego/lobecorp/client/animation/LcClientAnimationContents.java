package org.unitego.lobecorp.client.animation;

import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.network.tc.animation.LcAnimationContentPayload;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/// 服务器基础内容仅存在当前连接内，不写资源包或本地磁盘。
public class LcClientAnimationContents {
	private static final Map<String, LcAnimationContent> CONTENT = new HashMap<>();
	private static final Reassembly PENDING = new Reassembly();

	public static @Nullable LcAnimationContent get(String version) {
		return CONTENT.get(version);
	}

	public static void receive(LcAnimationContentPayload payload) {
		if (CONTENT.containsKey(payload.version())) {
			return;
		}

		var bytes = PENDING.receive(payload.version(), payload.index(), payload.count(), payload.bytes());
		if (bytes == null) {
			return;
		}

		var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
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

	/// 只接管调用方独占的分块；完整之前不分配拼接缓冲区。
	protected static class Reassembly {
		private final Map<String, PendingAssembly> pending = new HashMap<>();

		protected byte @Nullable [] receive(String version, int index, int count, byte[] bytes) {
			if (count <= 0 || index < 0 || index >= count) {
				throw new IllegalArgumentException(version);
			}

			var assembly = pending.computeIfAbsent(version, _ -> new PendingAssembly(count));
			if (assembly.chunks.length != count) {
				throw new IllegalArgumentException(version);
			}

			return assembly.receive(index, bytes);
		}

		protected void remove(String version) {
			pending.remove(version);
		}

		protected void clear() {
			pending.clear();
		}
	}

	private static class PendingAssembly {
		private final byte[] @Nullable [] chunks;
		private int received;
		private long totalBytes;

		private PendingAssembly(int count) {
			chunks = new byte[count][];
		}

		private byte @Nullable [] receive(int index, byte[] bytes) {
			var previous = chunks[index];
			if (previous == null) {
				received++;
			} else {
				totalBytes -= previous.length;
			}

			chunks[index] = bytes;
			totalBytes += bytes.length;
			if (received != chunks.length) {
				return null;
			}

			var result = new byte[Math.toIntExact(totalBytes)];
			int offset = 0;
			for (var slot : chunks) {
				var chunk = Objects.requireNonNull(slot);
				System.arraycopy(chunk, 0, result, offset, chunk.length);
				offset += chunk.length;
			}

			return result;
		}
	}
}
