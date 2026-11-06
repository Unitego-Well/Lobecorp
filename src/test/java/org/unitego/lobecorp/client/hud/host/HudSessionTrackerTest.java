package org.unitego.lobecorp.client.hud.host;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HudSessionTrackerTest {
	@Test
	public void sameObjectIdentityDoesNotChangeSession() {
		var level = new Object();
		var player = new Object();
		var tracker = new HudSessionTracker();

		assertTrue(tracker.update(level, player));
		assertFalse(tracker.update(level, player));
		assertFalse(tracker.unavailable());
	}

	@Test
	public void newPlayerObjectChangesSessionEvenWhenLevelIdentityIsSame() {
		var level = new Object();
		var firstPlayer = new Object();
		var secondPlayer = new Object();
		var tracker = new HudSessionTracker();

		tracker.update(level, firstPlayer);
		assertTrue(tracker.update(level, secondPlayer));
		assertFalse(tracker.update(level, secondPlayer));
	}

	@Test
	public void clearMakesNextUpdateAChangeAndReportsUnavailable() {
		var tracker = new HudSessionTracker();
		tracker.update(new Object(), new Object());

		tracker.clear();
		assertTrue(tracker.unavailable());
		assertTrue(tracker.update(new Object(), new Object()));
	}
}
