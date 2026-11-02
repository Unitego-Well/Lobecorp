package org.unitego.lobecorp.client.hud.host;

import net.minecraft.client.DeltaTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class HudFrameScopeTest {
	@Test
	public void scopeRestoresPreviousFrameAndRepeatedCloseIsSafe() {
		var first = frame();
		var second = frame();

		try (var outer = HudFrameScope.open(first)) {
			assertSame(first, HudFrameScope.current());

			try (var inner = HudFrameScope.open(second)) {
				assertSame(second, HudFrameScope.current());
			}

			assertSame(first, HudFrameScope.current());
		}

		assertNull(HudFrameScope.current());
	}

	@Test
	public void closingOldInnerScopeDoesNotLeakAcrossFrames() {
		var first = frame();
		var second = frame();
		var stale = HudFrameScope.open(first);

		try (var current = HudFrameScope.open(second)) {
			assertSame(second, HudFrameScope.current());
		}

		stale.close();
		stale.close();
		assertNull(HudFrameScope.current());
	}

	private static HudFrame frame() {
		return new HudFrame(null, DeltaTracker.ZERO, null, null, 320, 240);
	}
}
