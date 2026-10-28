package org.unitego.lobecorp.client.photon.render;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PhotonIrisPipelineAccessTest {
	@Test
	public void restoresPreviousBypassAfterSuccessAndFailure() {
		var value = new AtomicBoolean(false);
		PhotonIrisPipelineAccess.withBypass(value::get, value::set, () -> assertEquals(true, value.get()));
		assertEquals(false, value.get());

		var enabled = new AtomicBoolean(true);
		assertThrows(IllegalStateException.class,
				() -> PhotonIrisPipelineAccess.withBypass(enabled::get, enabled::set, () -> {
					throw new IllegalStateException("draw failed");
				}));
		assertEquals(true, enabled.get());
	}
}
