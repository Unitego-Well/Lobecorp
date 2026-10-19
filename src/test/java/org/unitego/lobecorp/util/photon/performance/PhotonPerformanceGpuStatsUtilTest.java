package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPerformanceGpuStatsUtilTest {
	@Test
	public void preservesFirstAndMaximumOutsideLaterSamples() {
		var stats = new PhotonPerformanceGpuStatsUtil();
		var sample = new JsonObject();
		sample.addProperty("frame_id", 1);
		stats.sample(1, "batch", 7, false, "measured", 9000, sample);
		sample.addProperty("frame_id", 200);
		for (int frame = 2; frame < 10000; frame++) {
			stats.sample(frame, "batch", 7, false, "measured", 100, sample);
		}
		var result = stats.summaries().getFirst();
		assertEquals(9999, result.get("measured").getAsLong());
		assertEquals(9000, result.get("duration_ns_max").getAsLong());
		assertEquals(1, result.getAsJsonObject("first_sample").get("frame_id").getAsInt());
		assertEquals(1, result.getAsJsonObject("maximum_sample").get("frame_id").getAsInt());
		assertEquals(1, result.get("first_frame_id").getAsInt());
		assertEquals(9999, result.get("last_frame_id").getAsInt());
	}

	@Test
	public void separatesSharedScopesAndKeepsUnavailableDistinctFromZero() {
		var stats = new PhotonPerformanceGpuStatsUtil();
		var sample = new JsonObject();
		stats.sample(3, "bloom", 7, true, "query_timeout", -1, sample);
		stats.skipped(2, "bloom", 7, true);
		stats.sample(1, "bloom", 7, false, "measured", 0, sample);
		var shared = stats.summaries().getFirst();
		assertEquals(2, shared.get("requested").getAsLong());
		assertEquals(1, shared.get("unavailable").getAsLong());
		assertEquals(1, shared.get("skipped_budget_or_pending").getAsLong());
		assertTrue(shared.get("duration_ns_sum").isJsonNull());
		assertTrue(shared.get("maximum_sample").isJsonNull());
		assertEquals("shared_or_unattributed", shared.get("ownership").getAsString());
		var owned = stats.summaries().get(1);
		assertEquals(0, owned.get("duration_ns_max").getAsLong());
		assertEquals(1, owned.get("measured").getAsLong());
	}

	@Test
	public void fullDictionaryStillAcceptsExistingGroups() {
		var stats = new PhotonPerformanceGpuStatsUtil();
		for (int index = 0; index < PhotonPerformanceGpuStatsUtil.MAX_GROUPS; index++) {
			stats.skipped(index, "pass_" + index, 0, true);
		}
		stats.skipped(9000, "new_pass", 0, true);
		stats.sample(9001, "pass_0", 0, true, "measured", 6000, new JsonObject());
		assertEquals(1, stats.dropped());
		assertEquals(PhotonPerformanceGpuStatsUtil.MAX_GROUPS, stats.summaries().size());
		var result = stats.summaries().getFirst();
		assertEquals(2, result.get("requested").getAsLong());
		assertEquals(6000, result.get("duration_ns_max").getAsLong());
	}
}
