package org.unitego.lobecorp.client.photon.playback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPlaybackClockTest {
	@Test
	public void preservesNormalizedPositionWhenDurationChangesAndAvoidsCycleDrift() {
		var clock = new PhotonPlaybackClock(100, true);

		for (int tick = 0; tick < 100; tick++) {
			clock.advance(1);
		}

		assertEquals(1, clock.loops(0));
		assertEquals(0, clock.progress(0));
		clock.advance(25);
		clock.duration(200);
		assertEquals(0.25, clock.progress(0));
		clock.advance(50);
		assertEquals(0.5, clock.progress(0));
	}

	@Test
	public void pausesWithoutDebtAndDeliversCompletionExactlyOnce() {
		var clock = new PhotonPlaybackClock(10, false);
		clock.advance(5);
		clock.pause();
		clock.advance(100);
		assertEquals(0.5, clock.progress(1));
		clock.resume();
		clock.advance(5);
		assertTrue(clock.takeCompletion());
		assertFalse(clock.takeCompletion());
		clock.pause();
		clock.resume();
		assertEquals(PhotonPlaybackClock.State.HOLDING, clock.state());
		assertEquals(1, clock.progress(1));
	}

	@Test
	public void restoresCompleteSnapshotsAndSeparatesTwoClocks() {
		var first = new PhotonPlaybackClock(10, true);
		var second = new PhotonPlaybackClock(10, true);
		first.advance(7);
		var snapshot = first.snapshot();
		first.advance(8);
		first.restore(snapshot);
		assertEquals(0.7, first.progress(0));
		assertEquals(0, second.progress(0));
		first.seek(0.4);
		assertEquals(0.4, first.progress(0));
		assertEquals(0, second.progress(0));
	}

	@Test
	public void rejectsInvalidTimesAndKeepsZeroDurationNonLoopCompletion() {
		assertThrows(IllegalArgumentException.class, () -> new PhotonPlaybackClock(0, true));
		var clock = new PhotonPlaybackClock(0, false);
		clock.advance(0);
		assertTrue(clock.takeCompletion());
		assertEquals(1, clock.progress(0));
		assertThrows(IllegalArgumentException.class, () -> clock.duration(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> clock.seek(-1));
	}
}
