package org.unitego.lobecorp.util.photon.material;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LazyBuiltinContentsTest {
	@Test
	public void coldConstructionAndWarmReadsPreserveIdentityOrderAndMutableMap() {
		var calls = new AtomicInteger();
		var target = new LinkedHashMap<String, Object>();
		var value = new Object();
		var lazy = new LazyBuiltinContents<>(target, () -> {
			calls.incrementAndGet();
			var staged = new LinkedHashMap<String, Object>();
			staged.put("first", value);
			staged.put("second", new Object());
			return staged;
		});
		assertFalse(lazy.initialized());
		assertEquals(0, calls.get());
		assertTrue(lazy.ensureInitialized());
		assertFalse(lazy.ensureInitialized());
		assertSame(value, target.get("first"));
		assertEquals(List.of("first", "second"), List.copyOf(target.keySet()));
		target.remove("first");
		lazy.ensureInitialized();
		assertFalse(target.containsKey("first"));
		assertEquals(1, calls.get());
	}

	@Test
	public void failedPartialBuildPublishesNothingAndRetries() {
		var calls = new AtomicInteger();
		var target = new LinkedHashMap<String, String>();
		target.put("existing", "untouched");
		var lazy = new LazyBuiltinContents<>(target, () -> {
			var staged = new LinkedHashMap<String, String>();
			staged.put("partial", "never published on failure");
			if (calls.incrementAndGet() == 1) {
				throw new IllegalArgumentException("failed");
			}

			staged.put("complete", "ready");
			return staged;
		});
		assertThrows(IllegalArgumentException.class, lazy::ensureInitialized);
		assertFalse(lazy.initialized());
		assertEquals(Map.of("existing", "untouched"), target);
		assertTrue(lazy.ensureInitialized());
		assertEquals(List.of("existing", "partial", "complete"), List.copyOf(target.keySet()));
		assertEquals(2, calls.get());
	}

	@Test
	public void recursiveReadFailsWithoutPublishingAndCanRetry() {
		var holder = new AtomicReference<LazyBuiltinContents<String, String>>();
		var recursive = new AtomicInteger();
		var target = new LinkedHashMap<String, String>();
		var lazy = new LazyBuiltinContents<>(target, () -> {
			if (recursive.getAndIncrement() == 0) {
				holder.get().ensureInitialized();
			}

			return Map.of("default", "ready");
		});
		holder.set(lazy);
		assertThrows(IllegalStateException.class, lazy::ensureInitialized);
		assertTrue(target.isEmpty());
		assertFalse(lazy.initialized());
		assertTrue(lazy.ensureInitialized());
	}

	@Test
	public void replacementContainerHasIndependentGenerationAndNoGlobalDefault() {
		var value1 = new Object();
		var value2 = new Object();
		var oldMap = new LinkedHashMap<String, Object>();
		var replacementMap = new LinkedHashMap<String, Object>();
		var old = new LazyBuiltinContents<>(oldMap, () -> Map.of("default", value1));
		var replacement = new LazyBuiltinContents<>(replacementMap, () -> Map.of("default", value2));
		old.ensureInitialized();
		assertFalse(replacement.initialized());
		replacement.ensureInitialized();
		assertNotSame(oldMap.get("default"), replacementMap.get("default"));
		assertSame(value1, oldMap.get("default"));
		assertSame(value2, replacementMap.get("default"));
	}
}
