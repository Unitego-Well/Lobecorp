package org.unitego.lobecorp.client.horde;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 只检查实际生产窗口边界与迁移偏移，不创建 GPU 设备或启动游戏。
public class HordeAvoidanceStateTest {
	@Test
	public void activeWindowMatchesBatchHalfOpenRouteEnd() {
		assertEquals(1, HordeAvoidanceState.firstRow(500, 1, 500, 1));
		assertEquals(500, HordeAvoidanceState.lastRow(500, 1, 1000, 1));
		assertEquals(0, HordeAvoidanceState.firstRow(499, 1, 500, 1));
		assertTrue(HordeAvoidanceState.lastRow(-1, 1, 1000, 1) <
				HordeAvoidanceState.firstRow(-1, 1, 500, 1));
		assertTrue(HordeAvoidanceState.lastRow(2001, 1, 1000, 1) <
				HordeAvoidanceState.firstRow(2001, 1, 500, 1));
	}

	@Test
	public void stoppedWaveRetainsOnlyItsRemainingRouteRows() {
		for (int tick = 0; tick <= 2000; tick += 17) {
			long first = HordeAvoidanceState.firstRow(tick, 0.75, 500, 1.8);
			long last = HordeAvoidanceState.lastRow(tick, 0.75, 1000, 1.8);
			for (long row = first; row <= last; row++) {
				double station = tick * 0.75 - row * 1.8;
				assertTrue(station >= -1e-9 && station < 500 + 1e-9);
				assertTrue(row * 1.8 <= Math.min(1000, tick) * 0.75 + 1e-9);
			}
			assertTrue(last - first + 1 <= Math.ceil(500 / 1.8) + 1);
		}
	}

	@Test
	public void overlapMappingSurvivesLargeRowTagsAndCapacityChanges() {
		for (long base : new long[]{0, (1L << 24) - 4, (1L << 32) - 4}) {
			for (int shift : new int[]{-3, 0, 1, 17, 500}) {
				long previous = base + 10;
				long next = previous + shift;
				int delta = HordeAvoidanceState.windowShift(next, previous);
				assertEquals(shift, delta);
				for (int row = 0; row < 278; row++) {
					long identity = next + row;
					int oldRow = row + delta;
					if (oldRow >= 0 && oldRow < 278) {
						assertEquals(identity, previous + oldRow);
					}
				}
			}
		}
	}
}
