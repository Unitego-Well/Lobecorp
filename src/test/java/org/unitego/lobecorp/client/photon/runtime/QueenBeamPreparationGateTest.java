package org.unitego.lobecorp.client.photon.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QueenBeamPreparationGateTest {
	@Test
	public void retriesFailedAttemptAfterDelayWithoutChangingGeneration() {
		var gate = new QueenBeamResourcePreparation.PreparationGate();
		assertTrue(gate.begin("native:1", 0));
		assertFalse(gate.ready("native:1"));
		assertFalse(gate.begin("native:1", 999_999_999));
		assertTrue(gate.begin("native:1", 1_000_000_000));
		gate.complete("native:1");
		assertTrue(gate.ready("native:1"));
		assertFalse(gate.begin("native:1", 5_000_000_000L));
	}

	@Test
	public void changedDeviceModeOrResourcesBypassFailedAttemptDelay() {
		var gate = new QueenBeamResourcePreparation.PreparationGate();
		assertTrue(gate.begin("native:1", 0));
		gate.complete("native:1");
		assertTrue(gate.begin("hybrid:1", 1));
		assertFalse(gate.ready("native:1"));
		assertFalse(gate.ready("hybrid:1"));
		assertTrue(gate.begin("hybrid:2", 2));
		gate.complete("hybrid:2");
		assertTrue(gate.ready("hybrid:2"));
	}
}
