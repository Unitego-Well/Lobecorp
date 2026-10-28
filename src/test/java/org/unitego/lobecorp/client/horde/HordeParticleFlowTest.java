package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeParticleFlowTest {
	private static final Vec3 ORIGIN = new Vec3(0, 64, 0);
	private static final Vec3 CAMERA = new Vec3(64, 64, 0);

	@Test
	public void candidateSelectionIsBoundedUniqueAndKeepsBothEnds() {
		assertTrue(HordeParticleFlow.CANDIDATE_LIMIT <= 256);
		assertEquals(128, HordeParticleFlow.SAMPLE_LIMIT);
		for (int phase = 0; phase < 8; phase++) {
			var selected = new HashSet<Long>();
			for (int index = 0; index < 16; index++) {
				long value = HordeParticleFlow.select(1_000_000_000_000L, 1_000_000_100_000L, index, 16, phase);
				assertTrue(value >= 1_000_000_000_000L && value <= 1_000_000_100_000L);
				assertTrue(selected.add(value));
			}

			assertTrue(selected.contains(1_000_000_000_000L));
			assertTrue(selected.contains(1_000_000_100_000L));
		}

		for (int index = 0; index < 16; index++) {
			assertEquals(index, HordeParticleFlow.select(0, 15, index, 16, 7));
		}
	}

	@Test
	public void longRouteProjectsCameraOntoSegmentRatherThanNearestEndpoint() {
		var tide = tide();
		assertEquals(64, tide.particleStation(CAMERA), 0.000001);
		assertEquals(new Vec3(1, 0, 0), tide.forward(64));
		assertEquals(new Vec3(64, 64, 5), tide.world(64, 5));
	}

	@Test
	public void sparseWindowDoesNotCreateIdentityEdges() {
		var field = field(200, 512, List.of());
		var samples = warm(new HordeParticleFlow(), field, CAMERA);
		assertFalse(samples.isEmpty());
		assertTrue(samples.size() <= HordeParticleFlow.SAMPLE_LIMIT);
		var inner = samples.stream().filter(sample -> sample.station() > 2 && sample.station() < 98).toList();
		assertFalse(inner.isEmpty());
		assertTrue(inner.stream().allMatch(HordeParticleSamples.Sample::interior));
		for (var sample : samples) {
			assertTrue(sample.ground().distanceToSqr(CAMERA) <= HordeParticleFlow.RANGE * HordeParticleFlow.RANGE);
			assertTrue(sample.inWave(0, 100));
		}
	}

	@Test
	public void finalCircleUnionExcludesHolesButAllowsBoxCornersAndOtherFloors() {
		var circles = List.of(new HordeSplitState.Circle(64, 0, 4, 0, 3),
				new HordeSplitState.Circle(68, 0, 4, 0, 3));
		assertFalse(HordeParticleFlow.clear(circles, ORIGIN, new Vec3(66, 64, 0), 2));
		assertTrue(HordeParticleFlow.clear(circles, ORIGIN, new Vec3(60.1, 65, 3.9), 0));
		assertTrue(HordeParticleFlow.clear(circles, ORIGIN, new Vec3(64, 70, 0), 2));
		assertFalse(HordeParticleFlow.clear(circles, ORIGIN, new Vec3(64, 63, 0), 2));
		assertTrue(HordeParticleFlow.clear(circles, ORIGIN, new Vec3(64, 60, 0), 2));
		assertTrue(HordeParticleFlow.clear(circles, ORIGIN, new Vec3(64, 62, 0), 2));

		var samples = warm(new HordeParticleFlow(), field(200, 64, circles), CAMERA);
		assertFalse(samples.isEmpty());
		for (var sample : samples) {
			assertTrue(HordeParticleFlow.clear(circles, ORIGIN, sample.ground(), 2));
		}
	}

	@Test
	public void totalLateralMotionIncludingSwayRespectsAvoidanceSpeedBudget() {
		var circles = List.of(new HordeSplitState.Circle(70, 0, 4, 0, 3));
		var flow = new HordeParticleFlow();
		var previous = new HashMap<Long, Vec3>();
		int comparisons = 0;
		boolean naturalExceedsBudget = false;
		boolean moved = false;
		for (int frame = 0; frame < 80; frame++) {
			double time = 6400 + frame * 2;
			var field = new HordeParticleFlow.Field(tide(), ORIGIN, circles, time, 0.01, 20_000, 1, 1, 1, 2);
			var samples = flow.sample(field, CAMERA, 0);
			if (frame >= 2) {
				assertFalse(samples.isEmpty());
			}

			var current = new HashMap<Long, Vec3>();
			for (var sample : samples) {
				if (sample.station() < 60) {
					continue;
				}

				var point = sample.ground();
				current.put(sample.row(), point);
				var before = previous.get(sample.row());
				if (before == null) {
					continue;
				}

				double budget = 0.7 * field.speed() * 2;
				double movement = Math.abs(point.z - before.z);
				assertTrue(movement <= budget + 0.000000001, "总横移包含 sway，不能超出侧速预算");
				moved |= movement > 0.000000001;
				double seed = HordeTide.seed(sample.row(), sample.column());
				naturalExceedsBudget |= Math.abs(HordeTide.sway(time, seed) - HordeTide.sway(time - 2, seed)) > budget;
				comparisons++;
			}
			previous = current;
		}

		assertTrue(comparisons >= 40);
		assertTrue(naturalExceedsBudget);
		assertTrue(moved);
	}

	@Test
	public void unopposedNaturalSwayRemainsUnrestricted() {
		var flow = new HordeParticleFlow();
		boolean exceedsAvoidanceBudget = false;
		for (int frame = 0; frame < 32; frame++) {
			double time = 6400 + frame * 2;
			var field = new HordeParticleFlow.Field(tide(), ORIGIN, List.of(), time, 0.01, 20_000, 1, 1, 1, 2);
			var samples = flow.sample(field, CAMERA, 0);
			if (frame >= 2) {
				assertFalse(samples.isEmpty());
			}

			for (var sample : samples) {
				double seed = HordeTide.seed(sample.row(), sample.column());
				double natural = HordeTide.sway(time, seed);
				assertEquals(natural, sample.ground().z, 0.000000001);
				exceedsAvoidanceBudget |= Math.abs(natural - HordeTide.sway(time - 2, seed)) > 0.7 * field.speed() * 2;
			}
		}

		assertTrue(exceedsAvoidanceBudget);
	}

	@Test
	public void incrementingPhaseKeepsProducingCandidatesAcrossWarmupAndTurnaround() {
		for (var circles : List.of(List.<HordeSplitState.Circle>of(),
				List.of(new HordeSplitState.Circle(64, 0, 8, 0, 3)))) {
			var flow = new HordeParticleFlow();
			var columns = new HashSet<Integer>();
			long phase = 0;
			for (int frame = 0; frame < 80; frame++) {
				var samples = flow.sample(field(200 + frame * 2, 512, circles), CAMERA, ++phase);
				assertTrue(samples.size() <= HordeParticleFlow.SAMPLE_LIMIT);
				if (frame < 2) {
					assertTrue(samples.isEmpty());
				} else {
					assertFalse(samples.isEmpty(), "递增 phase 不应使所有候选反复进入冷启动");
				}
				for (var sample : samples) {
					columns.add(sample.column());
				}
			}

			assertTrue(columns.size() > 16);
		}
	}

	@Test
	public void clearRewindAndLongGapDiscardRepresentativeHistory() {
		var circles = List.of(new HordeSplitState.Circle(110, 0, 4, 0, 3));
		var flow = new HordeParticleFlow();
		assertFalse(warm(flow, field(200, 1, circles), CAMERA).isEmpty());
		assertEquals(new HordeParticleFlow().sample(field(190, 1, circles), CAMERA, 0),
				flow.sample(field(190, 1, circles), CAMERA, 0));
		assertEquals(new HordeParticleFlow().sample(field(210, 1, circles), CAMERA, 0),
				flow.sample(field(210, 1, circles), CAMERA, 0));

		flow.clear();
		assertEquals(new HordeParticleFlow().sample(field(210, 1, circles), CAMERA, 0),
				flow.sample(field(210, 1, circles), CAMERA, 0));
	}

	@Test
	public void expiredOrFutureSnapshotsAreRejectedAcrossTheTenHertzCadence() {
		var snapshot = new HordeParticleSamples.Snapshot(UUID.randomUUID(), 200, 1, 1, 1_000_000_000L, List.of());
		assertTrue(snapshot.fresh(202, 1_100_000_000L));
		assertFalse(snapshot.fresh(204, 1_100_000_000L));
		assertFalse(snapshot.fresh(202, 1_150_000_001L));
		assertFalse(snapshot.fresh(199, 1_000_000_001L));
		assertFalse(snapshot.fresh(201, 999_999_999L));
	}

	@Test
	public void emptyWaveProducesNoFabricatedOccupancy() {
		assertTrue(new HordeParticleFlow().sample(field(-10, 64, List.of()), CAMERA, 0).isEmpty());
		assertTrue(new HordeParticleFlow().sample(field(200, 0, List.of()), CAMERA, 0).isEmpty());
	}

	@Test
	public void stopFreezesBirthRowsAndRouteEndIsHalfOpen() {
		var field = new HordeParticleFlow.Field(tide(), ORIGIN, List.of(), 260, 0.5, 20, 16, 1, 1, 2);
		var samples = warm(new HordeParticleFlow(), field, new Vec3(124, 64, 0));
		assertFalse(samples.isEmpty());
		for (var sample : samples) {
			assertTrue(sample.row() >= 3 && sample.row() <= 10);
		}

		var ended = new HordeParticleFlow.Field(tide(), ORIGIN, List.of(), 276, 0.5, 20, 16, 1, 1, 2);
		assertTrue(warm(new HordeParticleFlow(), ended, new Vec3(124, 64, 0)).isEmpty());
	}

	@Test
	public void finalBirthOffsetsMustRemainInTheDeformedEnvelope() {
		var envelope = new HordeParticleSamples.Envelope(new Vec3(50, 64, 0), new Vec3(1, 0, 0),
				50, -2, 2, 0, 100);
		var sample = new HordeParticleSamples.Sample(8, 8, new Vec3(50, 64, 1.9), 255, 50, envelope);
		assertTrue(sample.contains(sample.ground().add(0, 1.7, 0)));
		assertFalse(sample.contains(sample.ground().add(0, 0, 0.6)));
		assertFalse(sample.contains(new Vec3(101, 64, 0)));
		var circles = List.of(new HordeSplitState.Circle(50, 1.5, 1, 1, 3));
		assertFalse(HordeParticleFlow.clear(circles, ORIGIN, sample.ground().add(0, 1.7, 0), 0));
	}

	private static List<HordeParticleSamples.Sample> warm(HordeParticleFlow flow, HordeParticleFlow.Field field,
	                                                      Vec3 camera) {
		for (int tick = 4; tick >= 2; tick -= 2) {
			flow.sample(new HordeParticleFlow.Field(field.tide(), field.origin(), field.circles(), field.time() - tick,
					field.speed(), field.stop(), field.columns(), field.rowSpacing(), field.columnSpacing(),
					field.entityHeight()), camera, 0);
		}

		return flow.sample(field, camera, 0);
	}

	private static HordeParticleFlow.Field field(double time, int columns, List<HordeSplitState.Circle> circles) {
		return new HordeParticleFlow.Field(tide(), ORIGIN, circles, time, 0.5, 400, columns, 1, 1, 2);
	}

	private static HordeTide tide() {
		int length = 128;
		var route = new float[32 + length + 1];
		route[4] = 1;
		route[16] = length;
		route[19] = length;
		route[20] = 1;
		return new HordeTide(route, new float[4], 2, 2, length, 512, ORIGIN);
	}
}
