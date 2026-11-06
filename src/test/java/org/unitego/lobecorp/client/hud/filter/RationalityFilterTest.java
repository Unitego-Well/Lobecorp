package org.unitego.lobecorp.client.hud.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RationalityFilterTest {
	private static final float EPSILON = 0.0001F;

	@Test
	public void ratioAboveOneThirdClearsAllLayers() {
		var state = new RationalityFilter();
		state.advance(1, 3, 1);
		assertEquals(0, state.alpha(0), EPSILON);
		assertEquals(0, state.alpha(1), EPSILON);
		assertEquals(0, state.alpha(2), EPSILON);
	}

	@Test
	public void healthyIntervalLeavesNegativeTargetAndClearsPreviousIntensity() {
		var state = new RationalityFilter();
		state.advance(0, 3, 1);
		state.advance(3, 3, 1);
		assertEquals(0, state.alpha(0), EPSILON);
		assertEquals(0, state.alpha(1), EPSILON);
		assertEquals(0, state.alpha(2), EPSILON);
	}

	@Test
	public void maximumZeroOrInvalidClearsState() {
		var state = new RationalityFilter();
		state.advance(0, 3, 1);
		state.advance(0, 0, 1);
		assertEquals(0, state.alpha(0), EPSILON);

		state.advance(0, 3, 1);
		state.advance(0, Float.NaN, 1);
		assertEquals(0, state.alpha(0), EPSILON);
	}

	@Test
	public void zeroRationalityShowsCrossFadeLayers() {
		var state = new RationalityFilter();
		state.advance(0, 3, 2);
		assertEquals(0, state.alpha(0), EPSILON);
		assertEquals(0.499F, state.alpha(1), 0.002F);
		assertEquals(1, state.alpha(2), EPSILON);
	}

	@Test
	public void pauseDoesNotAdvanceIntensity() {
		var state = new RationalityFilter();
		state.advance(0, 3, 0);
		assertEquals(0, state.alpha(0), EPSILON);

		state.advance(0, 3, 1);
		float first = state.alpha(0);
		state.advance(0, 3, 0);
		assertEquals(first, state.alpha(0), EPSILON);
	}

	@Test
	public void clearResetsIntensityForFreshSession() {
		var state = new RationalityFilter();
		state.advance(0, 3, 1);
		state.clear();
		assertEquals(0, state.alpha(0), EPSILON);
		assertEquals(0, state.alpha(1), EPSILON);
		assertEquals(0, state.alpha(2), EPSILON);
	}
}
