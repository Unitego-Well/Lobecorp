package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// 仅检查快照契约，不初始化外层GPU管线或启动游戏。
public class HordeParticleSamplesTest {
	@Test
	public void recentFrameDoesNotBecomeFutureOrStaleEvidence() {
		var value = new HordeParticleSamples.Snapshot(UUID.randomUUID(), 40.5, 7, 3,
				1_000_000_000L, List.of());
		assertTrue(value.fresh(40.5, 1_000_000_000L));
		assertTrue(value.fresh(41.5, 1_100_000_000L));
		assertFalse(value.fresh(40.49, 1_010_000_000L));
		assertFalse(value.fresh(41.51, 1_010_000_000L));
		assertFalse(value.fresh(41, 1_100_000_001L));
		assertFalse(value.fresh(41, 999_999_999L));
		assertFalse(value.fresh(Double.NaN, 1_010_000_000L));
		assertFalse(value.fresh(Double.POSITIVE_INFINITY, 1_010_000_000L));
	}

	@Test
	public void onlyCompleteActualNeighborhoodIsInterior() {
		var ground = new Vec3(1, 2, 3);
		assertTrue(new HordeParticleSamples.Sample(8, 4, ground, 255, 12).interior());
		for (int bit = 0; bit < 8; bit++) {
			var candidate = new HordeParticleSamples.Sample(8, 4, ground, 255 ^ 1 << bit, 12);
			assertFalse(candidate.interior());
			assertTrue(candidate.boundary());
		}
		assertFalse(new HordeParticleSamples.Sample(8, 4, ground, 0, 12).interior());
	}

	@Test
	public void snapshotOwnsImmutableSampleList() {
		var samples = new ArrayList<HordeParticleSamples.Sample>();
		samples.add(new HordeParticleSamples.Sample(Long.MAX_VALUE, 3, Vec3.ZERO, 255, 4096.125));
		var snapshot = new HordeParticleSamples.Snapshot(UUID.randomUUID(), 1, 2, 3, 4, samples);
		samples.clear();
		assertEquals(1, snapshot.samples().size());
		assertEquals(Long.MAX_VALUE, snapshot.samples().getFirst().row());
		assertEquals(4096.125, snapshot.samples().getFirst().station());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.samples().clear());
	}

	@Test
	public void nineWordReadbackKeepsIdentityMaskAndStationOffsets() {
		assertEquals(9216, HordeParticleSamples.READBACK_BYTES);
		assertEquals(0, HordeParticleSamples.READBACK_BYTES % 12);
		var data = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN);
		data.position(36);
		data.putFloat(1).putFloat(2).putFloat(3).putInt(0xfffffff0).putInt(42)
				.putInt(7).putInt(255).putInt(1).putFloat(4096.125f);
		var sample = HordeParticleSamples.Sample.read(data, 36, new Vec3(10, 20, 30));
		assertEquals(42L << 32 | 0xfffffff0L, sample.row());
		assertEquals(7, sample.column());
		assertEquals(new Vec3(11, 22, 33), sample.ground());
		assertEquals(255, sample.occupancyMask());
		assertEquals(4096.125, sample.station());
	}

	@Test
	public void stationRejectsOutsideWaveAndNonFiniteSamples() {
		assertTrue(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, 0).inWave(0, 8192));
		assertTrue(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, 8192).inWave(0, 8192));
		for (double station : new double[]{-0.01, 8192.01, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertFalse(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, station).inWave(0, 8192));
		}
		assertFalse(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, 10).inWave(11, 9));
	}

	@Test
	public void localIntegerRowDifferenceKeeps8192BlockStationWithinOneHundredth() {
		// 对照GLSL的局部整数行差再转float；大于float精确整数范围的全局行号不得先转float。
		long referenceRow = (42L << 32) | 0xfffffff0L;
		double referenceStation = 8191.987;
		double spacing = 1.8;
		for (int offset = -4500; offset <= 4500; offset++) {
			long actualRow = referenceRow + offset;
			int difference = (int) actualRow - (int) referenceRow;
			assertEquals(offset, difference);
			double expected = referenceStation - difference * spacing;
			if (expected < 0 || expected > 8192) {
				continue;
			}
			float station = (float) referenceStation - difference * (float) spacing;
			assertEquals(expected, station, 0.01);
		}
	}
}
