package org.unitego.lobecorp.client.hud.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DamageFilterStateTest {
	private static final float EPSILON = 0.0001F;

	@Test
	public void firstHitOrderFollowsBitIndexAndKeepsDuplicateChannelInPlace() {
		var state = new DamageFilterState();
		state.hit(0b1011);
		assertEquals(List.of(0, 1, 3), channels(state));

		state.hit(0b0010);
		assertEquals(List.of(0, 1, 3), channels(state));
		assertEquals(1, state.entries().get(1).alpha(), EPSILON);

		state.hit(0b1100);
		assertEquals(List.of(0, 1, 3, 2), channels(state));
	}

	@Test
	public void duplicateHitResetsCurrentAndPreviousAndAlphaForThatChannelOnly() {
		var state = new DamageFilterState();
		state.hit(1);
		state.advance(0.5F);
		state.advance(0.5F);
		assertTrue(state.entries().getFirst().alpha() < 1);

		state.hit(1);
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);
		state.advance(0);
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);
	}

	@Test
	public void advanceSamplesThenDecaysSoNextFrameSeesReduction() {
		var state = new DamageFilterState();
		state.hit(1);
		state.advance(0.5F);
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);

		state.advance(0.5F);
		assertEquals(0.9375F, state.entries().getFirst().alpha(), EPSILON);
	}

	@Test
	public void nonEqualDeltaAndPauseArePreserved() {
		var state = new DamageFilterState();
		state.hit(1);
		state.advance(0);
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);

		state.advance(0.2F);
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);
		state.advance(0.2F);
		assertEquals(0.99F, state.entries().getFirst().alpha(), EPSILON);
		state.advance(0);
		assertEquals(0.99F, state.entries().getFirst().alpha(), EPSILON);
	}

	@Test
	public void fadeSpeedClampsLargeDeltaToFullStep() {
		var state = new DamageFilterState();
		state.hit(1);
		state.advance(10);
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);
		state.advance(10);
		assertEquals(0.75F, state.entries().getFirst().alpha(), EPSILON);
	}

	@Test
	public void entryRemovedOnlyWhenAlphaAndCurrentReachZero() {
		var state = new DamageFilterState();
		state.hit(1);
		for (int index = 0; index < 300; index++) {
			state.advance(1);
		}

		assertTrue(state.entries().isEmpty());

		state.hit(1);
		assertEquals(List.of(0), channels(state));
	}

	@Test
	public void clearDropsAllChannelsAndAllowsFreshHitOrder() {
		var state = new DamageFilterState();
		state.hit(0b1111);
		state.clear();
		assertTrue(state.entries().isEmpty());

		state.hit(0b1010);
		assertEquals(List.of(1, 3), channels(state));
		assertEquals(1, state.entries().getFirst().alpha(), EPSILON);
	}

	private static List<Integer> channels(DamageFilterState state) {
		return state.entries().stream().map(DamageFilterState.Layer::channel).toList();
	}
}
