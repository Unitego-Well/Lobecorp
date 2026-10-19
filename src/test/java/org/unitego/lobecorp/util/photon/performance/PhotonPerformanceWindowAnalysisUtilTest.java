package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Capture;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.View;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPerformanceWindowAnalysisUtilTest {
	@Test
	public void rapidRequestsKeepOnePumpAndOnlyLatestTarget() throws Exception {
		var ready = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var done = new CountDownLatch(1);
		try (var executor = Executors.newSingleThreadExecutor()) {
			var analysis = new PhotonPerformanceWindowAnalysisUtil(task -> executor.execute(() -> {
				ready.countDown();
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
					task.run();
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(failure);
				} finally {
					done.countDown();
				}
			}));
			analysis.request(capture(), frames(), List.of(), 1, 1);
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			for (int index = 0; index < 100; index++) {
				analysis.request(capture(), frames(), List.of(), 2, 2);
			}
			release.countDown();
			assertTrue(done.await(5, TimeUnit.SECONDS));
			assertFalse(analysis.state().working());
			assertNull(analysis.state().error());
			var result = analysis.state().result();
			assertNotNull(result);
			assertEquals(2, result.bounds().firstFrame());
			analysis.close();
		} finally {
			release.countDown();
		}
	}

	@Test
	public void closingBeforeExecutionRevokesPublicationAndNewRequests() {
		var tasks = new ArrayList<Runnable>();
		var analysis = new PhotonPerformanceWindowAnalysisUtil(tasks::add);
		analysis.request(capture(), frames(), List.of(), 1, 1);
		analysis.request(capture(), frames(), List.of(), 2, 2);
		assertEquals(1, tasks.size());
		analysis.close();
		tasks.getFirst().run();
		assertNull(analysis.state().result());
		assertFalse(analysis.state().working());
		analysis.request(capture(), frames(), List.of(), 1, 1);
		assertEquals(1, tasks.size());
	}

	@Test
	public void boundedQueueRejectionIsReportedWithoutRemainingBusy() {
		var analysis = new PhotonPerformanceWindowAnalysisUtil(_ -> {
			throw new RejectedExecutionException();
		});
		analysis.request(capture(), frames(), List.of(), 1, 1);
		assertFalse(analysis.state().working());
		assertNull(analysis.state().result());
		assertEquals("analysis_queue_full", analysis.state().error());
		analysis.close();
	}

	private static Capture capture() {
		return new Capture(new View(1, "test", Mode.DETAIL, 0, Instant.EPOCH, 0, 0,
				List.of(), List.of(), List.of(), List.of(), 0, 0, 0, null,
				new PhotonPerformanceTraceUtil.Quality(0, 0, 0, 0, 0, 0, 0, null), List.of()), "complete", 1000, List.of());
	}

	private static Frames frames() {
		return new Frames(new JsonObject(), 3, 0, new long[]{0, 10, 20}, new long[]{-1, 10, 10},
				new long[3], new long[3], new long[3], new long[3], new int[3], new int[3], new int[3],
				new int[3], new int[3], new int[3], new String[3]);
	}
}
