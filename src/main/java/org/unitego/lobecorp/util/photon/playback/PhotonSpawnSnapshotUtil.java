package org.unitego.lobecorp.util.photon.playback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/// 按源实例顺序解析并生成独立快照；缺失值在首个命中项立即失败。
public class PhotonSpawnSnapshotUtil {
	public static <K, V, R> Map<K, R> snapshot(Map<K, V> instances, Function<V, R> resolver,
	                                           String missingMessagePrefix) {
		var result = new HashMap<K, R>();

		for (var entry : instances.entrySet()) {
			var value = resolver.apply(entry.getValue());

			if (value == null) {
				throw new IllegalArgumentException(missingMessagePrefix + entry.getKey());
			}

			result.put(entry.getKey(), value);
		}

		return result;
	}
}
