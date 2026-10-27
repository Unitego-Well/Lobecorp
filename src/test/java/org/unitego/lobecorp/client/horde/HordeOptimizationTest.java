package org.unitego.lobecorp.client.horde;

import com.google.gson.JsonParser;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HordeOptimizationTest {
	@Test
	public void failureSequenceSurvivesFrameResetForStrictBenchmarkValidation() {
		long before = HordeRenderMetrics.failureSequence();
		HordeRenderMetrics.beginFrame(false);
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.FAILURES, 1);
		HordeRenderMetrics.endFrame();
		HordeRenderMetrics.beginFrame(false);
		assertEquals(0, HordeRenderMetrics.value(HordeRenderMetrics.Counter.FAILURES));
		assertEquals(before + 1, HordeRenderMetrics.failureSequence());
		HordeRenderMetrics.endFrame();
	}

	@TempDir
	public Path temporary;

	@Test
	public void coverageReusesGeometryButHonorsLightHeightChanges() {
		var cache = new HordeLightCoverage();
		var bounds = new AABB(-17, -32, -1, 16, 48, 1);
		long[] first = cache.candidates(bounds, -1, 2, 0);
		assertSame(first, cache.candidates(bounds, -1, 2, 1));
		assertEquals(1, cache.builds());
		for (long key : first) {
			assertTrue(SectionPos.y(key) >= -1 && SectionPos.y(key) < 2);
		}
		assertNotSame(first, cache.candidates(bounds, 0, 1, 2));
		assertEquals(2, cache.builds());
	}

	@Test
	public void loadedColumnIsSharedAcrossHeightsAndRecheckedEveryTick() {
		var cache = new HordeLightCoverage();
		var calls = new AtomicInteger();
		long low = SectionPos.asLong(-1875000, -4, 1874999);
		long high = SectionPos.asLong(-1875000, 19, 1874999);
		assertTrue(cache.loaded(low, 0, column -> {
			calls.incrementAndGet();
			assertEquals(-1875000, SectionPos.x(column));
			assertEquals(1874999, SectionPos.z(column));
			return true;
		}));
		assertTrue(cache.loaded(high, 0, _ -> fail("同列同刻重复查询")));
		assertFalse(cache.loaded(high, 1, _ -> {
			calls.incrementAndGet();
			return false;
		}));
		assertFalse(cache.loaded(low, 1, _ -> fail("未加载结果也应缓存")));
		assertEquals(2, calls.get());
		cache.clear();
		assertEquals(0, cache.queries());
	}

	@Test
	public void farWorldCoverageKeepsVanillaSectionCoordinateBits() {
		var cache = new HordeLightCoverage();
		long[] values = cache.candidates(new AABB(29999984, 0, -30000000, 29999999, 15, -29999985), -4, 20, 0);
		assertEquals(1, values.length);
		assertEquals(1874999, SectionPos.x(values[0]));
		assertEquals(-1875000, SectionPos.z(values[0]));
	}

	@Test
	public void packedChunksPreserveAllQuadsAndResetOnlyAtCapacity() {
		var layout = new HordeVertexLayout(new int[]{8, 4, 8, 4}, 14, 14 * 12);
		assertArrayEquals(new int[]{0, 8, 0, 8}, layout.offsets());
		assertArrayEquals(new int[]{2, 4}, layout.ends());
		assertEquals(12, layout.maximumVertices());
		var exact = new HordeVertexLayout(new int[]{12}, 14, 14 * 12);
		assertArrayEquals(new int[]{1}, exact.ends());
	}

	@Test
	public void oversizedOrPartialQuadsFailInsteadOfDroppingInstances() {
		assertThrows(IllegalArgumentException.class, () -> new HordeVertexLayout(new int[]{16}, 14, 14 * 12));
		assertThrows(IllegalArgumentException.class, () -> new HordeVertexLayout(new int[]{5}, 14, 1000));
		assertArrayEquals(new int[0], new HordeVertexLayout(new int[0], 14, 1000).ends());
	}

	@Test
	public void cpuCaptureSavesEarlyFailurePhaseAndMissingGpuAsNull() throws Exception {
		Path path = temporary.resolve("stages.jsonl");
		HordeRenderMetrics.start(path, false);
		try {
			HordeRenderMetrics.phase(HordeRenderMetrics.Phase.STABILIZE);
			HordeRenderMetrics.beginFrame(true);
			long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.PREPARE);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.FAILURES, 1);
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.PREPARE, started);
			HordeRenderMetrics.endFrame();
		} finally {
			HordeRenderMetrics.finish();
		}
		var lines = Files.readAllLines(path);
		var header = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
		assertEquals(1, header.get("frames").getAsInt());
		assertFalse(header.get("gpu_enabled").getAsBoolean());
		var row = JsonParser.parseString(lines.get(1)).getAsJsonObject();
		assertEquals("STABILIZE", row.get("phase").getAsString());
		assertEquals(1, row.getAsJsonObject("counts").get("failures").getAsInt());
		var prepare = row.getAsJsonObject("stages").getAsJsonObject("prepare");
		assertEquals(1, prepare.get("calls").getAsInt());
		assertTrue(prepare.get("gpu_sample_ns").isJsonNull());
	}

	@Test
	public void captureOwnershipIsNotOverwrittenAndZeroFramesStillSave() throws Exception {
		Path path = temporary.resolve("empty.jsonl");
		HordeRenderMetrics.start(path, false);
		try {
			assertThrows(IllegalStateException.class, () -> HordeRenderMetrics.start(temporary.resolve("other.jsonl"), false));
		} finally {
			HordeRenderMetrics.finish();
		}
		assertEquals(1, Files.readAllLines(path).size());
		assertFalse(Files.exists(temporary.resolve("other.jsonl")));
	}
}
