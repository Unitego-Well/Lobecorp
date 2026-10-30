package org.unitego.lobecorp.util.photon.material;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/// 只负责一次性发布；生成失败时保留空目标，允许下次访问重试，不改变调用线程。
public class LazyBuiltinContents<K, V> {
	private final Map<K, V> contents;
	private final Supplier<Map<K, V>> initializer;
	private volatile boolean initialized;
	private boolean initializing;

	public LazyBuiltinContents(Map<K, V> contents, Supplier<Map<K, V>> initializer) {
		this.contents = contents;
		this.initializer = initializer;
	}

	public boolean initialized() {
		return initialized;
	}

	public synchronized boolean ensureInitialized() {
		if (initialized) {
			return false;
		}

		if (initializing) {
			throw new IllegalStateException("recursive_builtin_initialization");
		}

		initializing = true;
		try {
			var prepared = Objects.requireNonNull(initializer.get());
			contents.putAll(prepared);
			initialized = true;
			return true;
		} finally {
			initializing = false;
		}
	}
}
