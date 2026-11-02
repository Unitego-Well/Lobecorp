package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.hud.status.bar.StatusGauge.TextureLayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatusGaugeAnimationTest {
	private static final TextureLayer LAYER = new TextureLayer(
			Identifier.fromNamespaceAndPath("lobecorp", "test"), 0, 0, 78, 7);

	@Test
	public void advanceOwnsFlashAndTrailTime() {
		var gauge = new StatusGauge(93, 11, LAYER, LAYER, LAYER);
		gauge.advance(10, 10, 0.5F, true);
		assertEquals(2, gauge.getLightTicks(), 0.001F);

		gauge.advance(10, 10, 0.5F, true);
		assertEquals(1.5F, gauge.getLightTicks(), 0.001F);
		assertEquals(9.375F, gauge.getRenderedValue(), 0.001F);
	}

	@Test
	public void hiddenFramesDoNotConsumeFlashOrTrailUntilNextVisibleFrame() {
		var gauge = new StatusGauge(93, 11, LAYER, LAYER, LAYER);
		gauge.advance(10, 10, 0.5F, true);
		gauge.advance(10, 10, 0.5F, false);
		assertEquals(1.5F, gauge.getLightTicks(), 0.001F);

		gauge.advance(10, 10, 0.5F, false);
		assertEquals(1.5F, gauge.getLightTicks(), 0.001F);

		gauge.advance(10, 10, 0.5F, true);
		assertEquals(1.5F, gauge.getLightTicks(), 0.001F);
	}

	@Test
	public void valueLerpUsesAdvanceDeltaAndLossCreatesTrail() {
		var gauge = new StatusGauge(93, 11, LAYER, LAYER, LAYER);
		gauge.advance(10, 10, 0, true);
		gauge.advance(5, 10, 0.5F, true);
		assertEquals(3.75F, gauge.getRenderedValue(), 0.001F);
		assertEquals(2, gauge.getLightTicks(), 0.001F);
		assertEquals(39F, gauge.getLightWidth(), 0.001F);

		gauge.advance(5, 10, 0.25F, true);
		assertEquals(4.21875F, gauge.getRenderedValue(), 0.001F);
		assertEquals(1.5F, gauge.getLightTicks(), 0.001F);
		assertEquals(35.1F, gauge.getLightWidth(), 0.001F);
	}

	@Test
	public void firstVisibleHitDrawsFullFlashBeforeAnyDecay() {
		var gauge = new StatusGauge(93, 11, LAYER, LAYER, LAYER);
		gauge.advance(0, 10, 0, true);
		gauge.advance(5, 10, 0.5F, true);
		assertEquals(2, gauge.getLightTicks(), 0.001F);
		assertEquals(0, gauge.getLightWidth(), 0.001F);
	}

	@Test
	public void pausedDeltaKeepsAnimationState() {
		var gauge = new StatusGauge(93, 11, LAYER, LAYER, LAYER);
		gauge.advance(10, 10, 0, true);
		gauge.advance(10, 10, 0, true);
		assertEquals(2, gauge.getLightTicks(), 0.001F);
		assertEquals(0, gauge.getRenderedValue(), 0.001F);
	}

	@Test
	public void clearResetsAnimationStateForFreshPrepare() {
		var gauge = new StatusGauge(93, 11, LAYER, LAYER, LAYER);
		gauge.advance(10, 10, 0.5F, true);
		gauge.clear();
		assertEquals(0, gauge.getRenderedValue(), 0.001F);
		assertEquals(0, gauge.getLightTicks(), 0.001F);
		assertEquals(0, gauge.getLightWidth(), 0.001F);
	}
}
