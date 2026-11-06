package org.unitego.lobecorp.util.photon.playback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 只验证生产弱表核心的绑定语义，不构造 runtime、编辑器或游戏对象。
public class PhotonRuntimePreviewUtilTest {
	@Test
	public void bindAndGet() {
		var bindings = new PhotonRuntimePreviewUtil.Bindings<Object, Object>();
		var key = new Object();
		var value = new Object();

		bindings.put(key, value);

		assertSame(value, bindings.get(key));
	}

	@Test
	public void overwriteKeepsLatest() {
		var bindings = new PhotonRuntimePreviewUtil.Bindings<Object, Object>();
		var key = new Object();
		var first = new Object();
		var second = new Object();

		bindings.put(key, first);
		bindings.put(key, second);

		assertSame(second, bindings.get(key));
	}

	@Test
	public void exactUnbindDoesNotRemoveDifferentOwner() {
		var bindings = new PhotonRuntimePreviewUtil.Bindings<Object, Object>();
		var key = new Object();
		var owner = new Object();
		var other = new Object();

		bindings.put(key, owner);

		assertFalse(bindings.remove(key, other));
		assertSame(owner, bindings.get(key));
		assertTrue(bindings.remove(key, owner));
		assertNull(bindings.get(key));
	}

	@Test
	public void weakValueClearedIsAbsent() {
		var bindings = new PhotonRuntimePreviewUtil.Bindings<Object, Object>();
		var key = new Object();
		var first = new Object();

		bindings.put(key, first);
		var reference = bindings.reference(key);
		reference.clear();

		assertNull(bindings.get(key));

		var second = new Object();
		bindings.put(key, second);
		assertSame(second, bindings.get(key));
	}

	@Test
	public void bindingsArePerKey() {
		var bindings = new PhotonRuntimePreviewUtil.Bindings<Object, Object>();
		var firstKey = new Object();
		var secondKey = new Object();
		var first = new Object();
		var second = new Object();

		bindings.put(firstKey, first);
		bindings.put(secondKey, second);

		assertSame(first, bindings.get(firstKey));
		assertSame(second, bindings.get(secondKey));
		assertFalse(bindings.remove(firstKey, second));
	}
}
