package org.unitego.lobecorp.client.conductor.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.TOOLTIP_DELAY_MS;

public class ConductorHudTooltipStateTest {
	@Test
	public void sameTargetKeepsDelayStart() {
		var state = new ConductorHudTooltipState();
		state.prepareTarget("member", 1_000L);
		assertFalse(state.visible(1_000L + TOOLTIP_DELAY_MS - 1));
		assertTrue(state.visible(1_000L + TOOLTIP_DELAY_MS));

		state.prepareTarget("member", 2_000L);
		assertTrue(state.visible(2_000L));
		assertEquals("member", state.targetId());
	}

	@Test
	public void targetChangeResetsDelay() {
		var state = new ConductorHudTooltipState();
		state.prepareTarget("member", 1_000L);
		assertTrue(state.visible(1_250L));

		state.prepareTarget("other", 1_300L);
		assertFalse(state.visible(1_300L + TOOLTIP_DELAY_MS - 1));
		assertTrue(state.visible(1_300L + TOOLTIP_DELAY_MS));
	}


	@Test
	public void repeatedVisibilityReadsDoNotMoveDelayStart() {
		var state = new ConductorHudTooltipState();
		state.prepareTarget("member", 1_000L);
		assertFalse(state.visible(1_000L));
		assertFalse(state.visible(1_100L));
		state.prepareTarget("member", 1_100L);
		assertFalse(state.visible(1_000L + TOOLTIP_DELAY_MS - 1));
		assertTrue(state.visible(1_000L + TOOLTIP_DELAY_MS));
	}

	@Test
	public void clearDropsTargetAndDelay() {
		var state = new ConductorHudTooltipState();
		state.prepareTarget("member", 1_000L);
		state.clear();

		assertEquals("", state.targetId());
		assertFalse(state.visible(10_000L));
	}
}
