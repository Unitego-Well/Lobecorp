package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PhotonPerformanceTraceConfigUtilTest {
	@Test
	public void absentOrInvalidNodePropertyUsesDefault() {
		assertEquals("lobecorp.photon.trace.maxNodes", PhotonPerformanceTraceConfigUtil.MAX_NODES_PROPERTY);
		assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxNodes(null));

		for (var value : new String[]{"", "invalid", "16384.0", " 16384", "16384 ", "2147483648", "-2147483649"}) {
			assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxNodes(value), value);
		}
	}

	@Test
	public void nodeCapacityClampsAtActualBoundaries() {
		assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxNodes("-2147483648"));
		assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxNodes("0"));
		assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxNodes("4095"));
		assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxNodes("4096"));
		assertEquals(4097, PhotonPerformanceTraceConfigUtil.maxNodes("4097"));
		assertEquals(16383, PhotonPerformanceTraceConfigUtil.maxNodes("16383"));
		assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxNodes("16384"));
		assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxNodes("16385"));
		assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxNodes("65536"));
		assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxNodes("2147483647"));
	}

    @Test
    public void absentOrInvalidCounterPropertyUsesDefault() {
        assertEquals("lobecorp.photon.trace.maxCountersPerThread", PhotonPerformanceTraceConfigUtil.MAX_COUNTERS_PROPERTY);
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxCountersPerThread(null));

        for (var value : new String[]{"", "invalid", "16384.0", " 16384", "16384 ", "2147483648", "-2147483649"}) {
            assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxCountersPerThread(value), value);
        }
    }

    @Test
    public void counterCapacityClampsAtActualBoundaries() {
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("-2147483648"));
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("0"));
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("4095"));
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("4096"));
        assertEquals(4097, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("4097"));
        assertEquals(16383, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("16383"));
        assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("16384"));
        assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("16385"));
        assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("65536"));
        assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxCountersPerThread("2147483647"));
    }

    @Test
    public void absentOrInvalidPropertyUsesDefault() {
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread(null));

        for (var value : new String[]{"", "invalid", "16384.0", "2147483648", "-2147483649"}) {
            assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread(value), value);
        }
    }

    @Test
    public void diagnosticCapacityIsBounded() {
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("-2147483648"));
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("4095"));
        assertEquals(4096, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("4096"));
        assertEquals(16384, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("16384"));
        assertEquals(65536, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("65536"));
        assertEquals(65536, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("65537"));
        assertEquals(65536, PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread("2147483647"));
    }
}
