package org.unitego.lobecorp.client.hud.status.bar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbsorptionFlashTest {
	@Test
	public void firstHitDrawsFullFlashBeforeDecay() {
		var flash = new AbsorptionFlash();
		flash.prepare(4, 0.5F, true);
		assertEquals(2, flash.lightTicks(), 0.001F);
	}

	@Test
	public void hiddenFramesDoNotConsumeFlash() {
		var flash = new AbsorptionFlash();
		flash.prepare(4, 0.5F, true);
		flash.prepare(4, 0.5F, false);
		assertEquals(1.5F, flash.lightTicks(), 0.001F);

		flash.prepare(4, 0.5F, false);
		assertEquals(1.5F, flash.lightTicks(), 0.001F);

		flash.prepare(4, 0.5F, true);
		assertEquals(1.5F, flash.lightTicks(), 0.001F);
	}

	@Test
	public void changedVisibleDeltaSettlesPreviousFrameThenSamplesHit() {
		var flash = new AbsorptionFlash();
		flash.prepare(4, 0.5F, true);
		flash.prepare(4, 0.25F, true);
		assertEquals(1.5F, flash.lightTicks(), 0.001F);
	}

	@Test
	public void pausedVisibleFrameDoesNotDecay() {
		var flash = new AbsorptionFlash();
		flash.prepare(4, 0, true);
		assertEquals(2, flash.lightTicks(), 0.001F);
		flash.prepare(4, 0, true);
		assertEquals(2, flash.lightTicks(), 0.001F);
	}
}
