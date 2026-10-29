package org.unitego.lobecorp.client.photon.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhotonModelFailureStateTest {
	@Test
	public void recordsContextAndOnlyNotifiesOnChangedState() {
		var state = new PhotonModelFailureState();

		assertTrue(state.record("重置", new IllegalArgumentException("资产无效")));
		assertFalse(state.record("重置", new IllegalArgumentException("资产无效")));
		assertTrue(state.record("采样", new IllegalArgumentException("资产无效")));
		assertEquals("采样：资产无效", state.current() == null ? null : state.current().detail());
		state.clear();
		assertNull(state.current());
		assertNull(state.message());
	}

	@Test
	public void clearsOnlyMatchingOperation() {
		var state = new PhotonModelFailureState();

		state.record("重置", new IllegalArgumentException("资产无效"));
		state.clear("采样");
		assertNotNull(state.current());
		state.clear("重置");
		assertNull(state.current());
	}

	@Test
	public void fallsBackToExceptionTypeWhenMessageIsBlank() {
		var state = new PhotonModelFailureState();

		state.record("配置变更", new IllegalStateException());

		assertEquals("IllegalStateException", state.message());
	}
}
