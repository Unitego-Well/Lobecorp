package org.unitego.lobecorp.world.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HordeRulesTest {
	private static HordeRules rules() {
		return new HordeRules(6000, 64, 1, 1, 8, 1, 3, 100, 100, 100,
				40, 20, 5, 20, 8, 40, 4);
	}

	@Test
	public void deathRaisesQuotaAndResetsReinforcementClock() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, rules());
		target.fightTicks = 99;
		target.death();
		assertEquals(2, target.quota);
		assertEquals(0, target.fightTicks);
		target.death();
		target.death();
		assertEquals(3, target.quota);
	}

	@Test
	public void transferredExcessDoesNotRequestMoreOrRemoveMembers() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, rules());
		target.quota = 3;
		for (int i = 0; i < 5; i++) {
			target.members.add(UUID.randomUUID());
		}
		assertEquals(0, target.missing());
		assertEquals(5, target.members.size());
		target.death();
		assertEquals(3, target.quota);
		assertEquals(5, target.members.size());
	}

	@Test
	public void reservationOccupiesTheLastAvailableSlot() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, rules());
		target.reservations = 1;
		assertEquals(0, target.missing());
	}

	@Test
	public void stateRoundTripPreservesQuotaAndMembersButNotTransientReservations() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), new Vec3(1, 2, 3), rules());
		target.members.add(UUID.randomUUID());
		target.quota = 3;
		target.engaged = true;
		target.fightTicks = 42;
		target.retryUntil = 900;
		target.reservations = 1;
		var restored = HordeTargetAssignment.load(target.save());
		assertEquals(target.members, restored.members);
		assertEquals(target.quota, restored.quota);
		assertEquals(42, restored.fightTicks);
		assertEquals(900, restored.retryUntil);
		assertEquals(0, restored.reservations);
	}

	@Test
	public void spacingSurvivesRuleRoundTrip() {
		var original = rules();
		var restored = HordeRules.load(original.save());
		assertEquals(original, restored);
		assertEquals(1, restored.rowSpacing());
		assertEquals(1, restored.columnSpacing());
	}

	@Test
	public void automaticLoopClosesAndHasNoZeroLengthSegments() {
		var loop = HordeSamplingUtil.loop(List.of(Vec3.ZERO, new Vec3(300, 0, 0)), 128);
		assertEquals(loop.getFirst(), loop.getLast());
		double length = 0;
		for (int index = 1; index < loop.size(); index++) {
			double distance = loop.get(index - 1).distanceTo(loop.get(index));
			assertTrue(distance > 0);
			length += distance;
		}
		assertTrue(length > 1000);
	}

	@Test
	public void explicitLoopDoesNotRepeatTheClosingAnchor() {
		var loop = HordeSamplingUtil.loop(List.of(Vec3.ZERO, new Vec3(300, 0, 0), new Vec3(300, 0, 300), Vec3.ZERO), 128);
		assertEquals(49, loop.size());
		assertEquals(loop.getFirst(), loop.getLast());
	}

	@Test
	public void invalidSpacingIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new HordeRules(6000, 64, 0, 1, 8, 1, 3,
				100, 100, 100, 40, 20, 5, 20, 8, 40, 4));
	}

	@Test
	public void fadingIsPresentationOnlyAndHasFiniteDuration() {
		var state = new HordeParticipant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0, 10, 5);
		assertEquals(1, state.alpha(10));
		assertEquals(0.5f, state.alpha(12.5));
		assertEquals(0, state.alpha(15));
		assertEquals(state, HordeParticipant.load(state.save()));
	}
}
