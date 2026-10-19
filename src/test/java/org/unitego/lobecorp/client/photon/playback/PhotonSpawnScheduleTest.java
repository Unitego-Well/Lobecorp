package org.unitego.lobecorp.client.photon.playback;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonSpawnSchedule;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig.Mode;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonSpawnScheduleTest {
	@Test
	public void usesHalfOpenIntervalAndPreservesFractionalSteps() {
		var schedule = new PhotonSpawnSchedule();
		var bursts = new AtomicInteger();

		for (int step = 0; step < 20; step++) {
			schedule.advance(step * 0.25, 0.25, 4, 1, true, Mode.INTERVAL, 2, 0,
					bursts::incrementAndGet, _ -> fail("不应触发速率发射"));
		}

		assertEquals(2, bursts.get());
		schedule.advance(5, 0.25, 4, 1, true, Mode.INTERVAL, 2, 0, bursts::incrementAndGet, _ -> {
		});
		assertEquals(3, bursts.get());
	}

	@Test
	public void onceRepeatsPerCycleButOneShotAttemptsOnlyOnceUntilReset() {
		var schedule = new PhotonSpawnSchedule();
		var bursts = new AtomicInteger();
		schedule.advance(0, 5, 2, 0, true, Mode.ONCE, 1, 0, bursts::incrementAndGet, _ -> {
		});
		assertEquals(3, bursts.get());
		schedule.reset();
		bursts.set(0);
		schedule.advance(0, 5, 2, 0, true, Mode.ONE_SHOT, 1, 0, bursts::incrementAndGet, _ -> {
		});
		schedule.advance(5, 5, 2, 0, true, Mode.ONE_SHOT, 1, 0, bursts::incrementAndGet, _ -> {
		});
		assertEquals(1, bursts.get());
		schedule.reset();
		schedule.advance(0, 1, 2, 0, false, Mode.ONE_SHOT, 1, 0, bursts::incrementAndGet, _ -> {
		});
		assertEquals(2, bursts.get());
	}

	@Test
	public void accumulatesRateWithoutInventingDebtAfterWindowEnd() {
		var schedule = new PhotonSpawnSchedule();
		var total = new AtomicInteger();

		for (int step = 0; step < 10; step++) {
			schedule.advance(step, 1, 8, 0, false, Mode.RATE, 1, 0.5,
					() -> fail("不应触发间隔发射"), total::addAndGet);
		}

		assertEquals(4, total.get());
		assertThrows(IllegalArgumentException.class, () -> schedule.advance(0, Double.NaN, 10, 0,
				false, Mode.RATE, 1, 1, () -> {
				}, _ -> {
				}));
	}
}
