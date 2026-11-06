package org.unitego.lobecorp.client.conductor.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConductorHudViewSlotTest {
	@Test
	public void staleHostReleaseDoesNotClearReplacementView() {
		var slot = new ConductorHudViewSlot<Object>();
		var first = new Object();
		var second = new Object();
		slot.set(first);
		slot.set(second);

		assertFalse(slot.clearIfSame(first));
		assertSame(second, slot.get());
	}

	@Test
	public void releasingCurrentHostClearsOnlyThatReference() {
		var slot = new ConductorHudViewSlot<Object>();
		var view = new Object();
		slot.set(view);

		assertTrue(slot.clearIfSame(view));
		assertNull(slot.get());
	}
}
