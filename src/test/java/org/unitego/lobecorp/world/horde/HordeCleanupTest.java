package org.unitego.lobecorp.world.horde;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.horde.HordeCleanupUtil;

import static org.junit.jupiter.api.Assertions.*;

public class HordeCleanupTest {
    @Test
    public void eventCorpseLivesUntilTheTwoHundredthTick() {
        long created = 1000;
        long deadline = created + HordeCleanupUtil.CORPSE_RETENTION_TICKS;
        assertEquals(1200, deadline);
        assertFalse(HordeCleanupUtil.mayClearCorpse(deadline - 1, deadline, false));
        assertTrue(HordeCleanupUtil.mayClearCorpse(deadline, deadline, false));
    }

    @Test
    public void combatProtectionWinsAndExpiredCorpsesClearAfterLeaving() {
        long deadline = 1200;
        assertFalse(HordeCleanupUtil.mayClearCorpse(deadline, deadline, true));
        assertFalse(HordeCleanupUtil.mayClearCorpse(deadline + 10000, deadline, true));
        assertTrue(HordeCleanupUtil.mayClearCorpse(deadline + 10000, deadline, false));
    }

    @Test
    public void untimedOrdinaryCorpsesNeverGainAutomaticCleanupEligibility() {
        assertFalse(HordeCleanupUtil.mayClearCorpse(0, -1, false));
        assertFalse(HordeCleanupUtil.mayClearCorpse(Long.MAX_VALUE, -1, false));
    }
}
