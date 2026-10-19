package org.unitego.lobecorp.client.photon.model;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.model.PhotonModelClock.Settings;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelClockTest {
	@Test
	public void handlesDelayPauseAndSpeedWithoutSharingTime() {
		var first = new PhotonModelClock();
		var second = new PhotonModelClock();
		var settings = new Settings(true, false, true, 10, 2, 2);
		first.reset(settings, false);
		second.reset(settings, false);
		assertFalse(first.visible(settings));
		first.advance(1, settings);
		assertEquals(0, first.animationTime(0, 1, settings));
		first.advance(2, settings);
		assertTrue(first.visible(settings));
		assertEquals(2, first.animationTime(0, 1, settings));
		assertEquals(3, first.animationTime(0.5f, 1, settings));
		assertEquals(0, second.age());
		var paused = new Settings(true, true, true, 10, 2, 2);
		first.advance(100, paused);
		assertEquals(3, first.age());
		assertEquals(2, first.animationTime(1, 4, paused));
	}

	@Test
	public void stopsAtCycleBoundaryAndForceClearsImmediately() {
		var clock = new PhotonModelClock();
		var settings = new Settings(true, false, true, 10, 0, 1);
		clock.reset(settings, false);
		clock.advance(3, settings);
		clock.stop(settings, false);
		clock.advance(6, settings);
		assertTrue(clock.visible(settings));
		clock.advance(1, settings);
		assertTrue(clock.finished());
		assertFalse(clock.visible(settings));
		clock.reset(settings, false);
		clock.stop(settings, true);
		assertTrue(clock.finished());
	}

	@Test
	public void supportsPreserveResetAndNonLoopingLifetime() {
		var clock = new PhotonModelClock();
		var settings = new Settings(true, false, false, 10, 2, 1);
		clock.reset(settings, false);
		clock.advance(7, settings);
		clock.reset(settings, true);
		assertEquals(5, clock.animationTime(0, 1, settings));
		clock.advance(12, settings);
		assertTrue(clock.finished());
		clock.reset(settings, false);
		assertEquals(0, clock.animationTime(0, 1, settings));
		assertThrows(IllegalArgumentException.class, () -> new Settings(true, false, true, 0, 0, 1));
	}
}
