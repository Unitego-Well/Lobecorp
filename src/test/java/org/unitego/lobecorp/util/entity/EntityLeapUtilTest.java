package org.unitego.lobecorp.util.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityLeapUtilTest {
	/// 独立逐 tick 积分与期望落点比较的误差容限，单位为格。
	private static final double POSITION_EPSILON = 1.0E-9;

	@Test
	public void sameHeightHasKnownTenTickSolution() {
		var trajectory = EntityLeapUtil.solve(new Vec3(6.0, 0.0, 0.0),
				1.0, 1.0, 1.0, 0.08, 1.8, 1.4, 40);
		assertNotNull(trajectory);
		assertEquals(10, trajectory.flightTicks());
		assertEquals(0.6, trajectory.movement().x, POSITION_EPSILON);
		assertEquals(0.36, trajectory.movement().y, POSITION_EPSILON);
	}

	@Test
	public void unevenTerrainReachesLockedEndpoint() {
		for (var displacement : new Vec3[]{new Vec3(8.0, 2.0, 3.0), new Vec3(-8.0, -3.0, 3.0)}) {
			var trajectory = EntityLeapUtil.solve(displacement, 1.0, 2.5, 1.6, 0.08, 1.8, 1.4, 40);
			assertNotNull(trajectory);
			Vec3 position = Vec3.ZERO;
			Vec3 movement = trajectory.movement();
			double highestY = position.y;
			for (int tick = 0; tick < trajectory.flightTicks(); tick++) {
				position = position.add(movement);
				highestY = Math.max(highestY, position.y);
				if (tick == trajectory.flightTicks() - 1) {
					assertTrue(movement.y < 0.0);
				}

				movement = movement.add(0.0, -0.08, 0.0);
			}

			assertEquals(displacement.x, position.x, POSITION_EPSILON);
			assertEquals(displacement.y, position.y, POSITION_EPSILON);
			assertEquals(displacement.z, position.z, POSITION_EPSILON);
			double arcHeight = highestY - Math.max(0.0, displacement.y);
			assertTrue(arcHeight >= 1.0 && arcHeight <= 2.5);
			assertTrue(trajectory.movement().y > 0.0 && trajectory.movement().y <= 1.4);
			assertTrue(trajectory.movement().horizontalDistance() <= 1.8);
		}
	}

	@Test
	public void equalArcErrorsPreferShorterFlight() {
		var trajectory = EntityLeapUtil.solve(new Vec3(6.0, 0.0, 0.0),
				1.0, 1.2, 1.1, 0.08, 1.8, 1.4, 40);
		assertNotNull(trajectory);
		assertEquals(10, trajectory.flightTicks());
	}

	@Test
	public void cannotReachWhenSpeedCapsPreventSolution() {
		assertNull(EntityLeapUtil.solve(new Vec3(100.0, 0.0, 0.0),
				1.0, 2.5, 1.5, 0.08, 1.8, 1.4, 40));
		assertNull(EntityLeapUtil.solve(new Vec3(6.0, 0.0, 0.0),
				1.0, 2.5, 1.5, 0.08, 1.8, 0.1, 40));
	}

	@Test
	public void upwardEndpointIsNotAcceptedAsLanding() {
		assertNull(EntityLeapUtil.solve(new Vec3(1.0, 5.0, 0.0),
				0.0, 2.5, 1.0, 0.08, 10.0, 10.0, 2));
	}

	@Test
	public void invalidParametersAreRejected() {
		assertNull(EntityLeapUtil.solve(new Vec3(Double.NaN, 0.0, 0.0),
				1.0, 2.5, 1.5, 0.08, 1.8, 1.4, 40));
		assertNull(EntityLeapUtil.solve(Vec3.ZERO, 1.0, 2.5, 1.5, 0.0, 1.8, 1.4, 40));
		assertNull(EntityLeapUtil.solve(Vec3.ZERO, 2.5, 1.0, 1.5, 0.08, 1.8, 1.4, 40));
		assertNull(EntityLeapUtil.solve(Vec3.ZERO, 1.0, 2.5, Double.POSITIVE_INFINITY, 0.08, 1.8, 1.4, 40));
		assertNull(EntityLeapUtil.solve(Vec3.ZERO, 1.0, 2.5, 1.5, 0.08, 1.8, 1.4, 1));
	}
}
