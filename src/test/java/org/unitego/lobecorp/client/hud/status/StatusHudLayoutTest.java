package org.unitego.lobecorp.client.hud.status;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatusHudLayoutTest {
	@Test
	public void stacksVisibleBarsUpwardWithTwoPixelGap() {
		var layout = StatusHudLayout.stack(100, 200, List.of(
				new StatusHudLayout.Bar(90, 9, true),
				new StatusHudLayout.Bar(93, 11, true),
				new StatusHudLayout.Bar(93, 11, false)));

		assertEquals(191, layout.slots().getFirst().top(), 0.001);
		assertEquals(178, layout.slots().get(1).top(), 0.001);
		assertEquals(176, layout.slots().get(2).top(), 0.001);
		assertEquals(22, layout.height());
		assertEquals(93, layout.width());
		assertEquals(100, layout.slots().getFirst().left(), 0.001);
	}

	@Test
	public void hiddenBarsDoNotContributeWidthHeightOrGap() {
		var layout = StatusHudLayout.stack(10, 50, List.of(
				new StatusHudLayout.Bar(90, 9, false),
				new StatusHudLayout.Bar(93, 11, true)));

		assertEquals(11, layout.height());
		assertEquals(93, layout.width());
		assertEquals(39, layout.slots().get(1).top(), 0.001);
	}

	@Test
	public void zeroVisibleBarsProduceZeroHeight() {
		var layout = StatusHudLayout.stack(0, 30, List.of(new StatusHudLayout.Bar(90, 9, false)));
		assertEquals(0, layout.height());
		assertEquals(0, layout.width());
	}

	@Test
	public void oddWidthUsesIntegerCenterAnchor() {
		assertEquals(95, StatusHudLayout.left(401, 105), 0.001F);
	}
}
