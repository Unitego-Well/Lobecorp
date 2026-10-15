package org.unitego.lobecorp.animation.resource;

import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/// 一次播放锁定的完整模型与动画内容，版本同时包含资源标识和原始内容。
public record LcAnimationContent(Identifier modelId, Identifier animationId, String modelJson,
                                 String animationJson, String version) {

	public LcAnimationContent {
		Objects.requireNonNull(modelId);
		Objects.requireNonNull(animationId);
		Objects.requireNonNull(modelJson);
		Objects.requireNonNull(animationJson);
		Objects.requireNonNull(version);
	}

	public static LcAnimationContent of(Identifier modelId, Identifier animationId, String modelJson,
	                                    String animationJson) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			for (var value : new String[]{modelId.toString(), animationId.toString(), modelJson, animationJson}) {
				var bytes = value.getBytes(StandardCharsets.UTF_8);
				digest.update(new byte[]{(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
						(byte) (bytes.length >>> 8), (byte) bytes.length});
				digest.update(bytes);
			}

			return new LcAnimationContent(modelId, animationId, modelJson, animationJson,
					HexFormat.of().formatHex(digest.digest()));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
