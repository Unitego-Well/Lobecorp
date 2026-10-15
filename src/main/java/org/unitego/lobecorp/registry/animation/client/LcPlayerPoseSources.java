package org.unitego.lobecorp.registry.animation.client;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.animation.player.ILcPlayerPoseSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// 兼容来源显式登记后才参与客户端姿态采样，不探测或适配其他模组。
public class LcPlayerPoseSources {
	private static final String DUPLICATE_SOURCE = "玩家姿态来源已登记：";
	private static final Map<Identifier, ILcPlayerPoseSource> SOURCES = new LinkedHashMap<>();

	public static void register(Identifier id, ILcPlayerPoseSource source) {
		Objects.requireNonNull(id);
		Objects.requireNonNull(source);
		if (SOURCES.putIfAbsent(id, source) != null) {
			throw new IllegalArgumentException(DUPLICATE_SOURCE + id);
		}
	}

	public static Map<Identifier, ILcPlayerPoseSource> sources() {
		return Map.copyOf(SOURCES);
	}
}
