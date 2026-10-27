package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeParticleSamplesTest {
	@Test
	public void onlySamplesWithinTheFreshnessWindowAreAccepted() {
		var value = new HordeParticleSamples.Snapshot(UUID.randomUUID(), 40.5, 7, 3,
				System.nanoTime() - 10_000_000L, new ArrayList<>());
		assertTrue(value.fresh(40.8, System.nanoTime()));
		assertFalse(value.fresh(39.9, System.nanoTime()));
		assertFalse(value.fresh(50.5, System.nanoTime()));
	}

	@Test
	public void occupancyMaskSeparatesInteriorFromBoundary() {
		var ground = new Vec3(1, 64, 1);
		assertTrue(new HordeParticleSamples.Sample(8, 4, ground, 255, 12).interior());
		for (int bit = 0; bit < 8; bit++) {
			var candidate = new HordeParticleSamples.Sample(8, 4, ground, 255 ^ 1 << bit, 12);
			assertTrue(candidate.boundary());
		}
		assertFalse(new HordeParticleSamples.Sample(8, 4, ground, 0, 12).interior());
	}

	@Test
	public void waveWindowIsInclusiveAndRejectsInvertedOrNonFiniteBounds() {
		var samples = new ArrayList<HordeParticleSamples.Sample>();
		samples.add(new HordeParticleSamples.Sample(Long.MAX_VALUE, 3, Vec3.ZERO, 255, 4096.125));
		var snapshot = new HordeParticleSamples.Snapshot(UUID.randomUUID(), 1, 2, 3, 4, samples);
		assertEquals(1, snapshot.samples().size());
		assertTrue(snapshot.samples().getFirst().inWave(0, 8192));
		assertTrue(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, 0).inWave(0, 8192));
		assertTrue(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, 8192).inWave(0, 8192));
		assertFalse(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, 10).inWave(11, 9));
		assertFalse(new HordeParticleSamples.Sample(0, 0, Vec3.ZERO, 255, Double.NaN).inWave(0, 8192));
	}
}
