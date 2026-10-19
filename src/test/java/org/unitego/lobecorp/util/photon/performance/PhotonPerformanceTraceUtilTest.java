package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Capture;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Event;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPerformanceTraceUtilTest {
	@AfterEach
	public void clear() {
		PhotonPerformanceTraceUtil.finish("test_cleanup");
	}

	@Test
	public void disabledCaptureDoesNotCreateScopes() {
		assertFalse(PhotonPerformanceTraceUtil.active());
		assertEquals(0, PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0));
		assertNull(PhotonPerformanceTraceUtil.view());
	}

	@Test
	public void nestedScopesHaveExclusiveTimeAndStableParents() {
		PhotonPerformanceTraceUtil.start("nested", Mode.DETAIL);
		int parent = PhotonPerformanceTraceUtil.node(new Object());
		int child = PhotonPerformanceTraceUtil.node(new Object());
		long outer = PhotonPerformanceTraceUtil.begin(Stage.SEEK, parent);
		long inner = PhotonPerformanceTraceUtil.begin(Stage.REPLAY, child);
		PhotonPerformanceTraceUtil.end(inner, true);
		PhotonPerformanceTraceUtil.end(outer, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		var events = events(capture);
		assertEquals(2, events.size());
		var outerEvent = events.getFirst();
		var innerEvent = events.getLast();
		assertEquals(outerEvent.id(), innerEvent.parent());
		assertEquals(outerEvent.endNs() - outerEvent.startNs() - (innerEvent.endNs() - innerEvent.startNs()),
				outerEvent.selfNs());
		assertTrue(outerEvent.completed());
		assertFalse(outerEvent.failed());
		assertEquals(0, capture.view().incomplete());
	}

	@Test
	public void equalObjectsRemainDifferentRuntimeIdentities() {
		PhotonPerformanceTraceUtil.start("identities", Mode.DETAIL);
		var first = new String("same");
		var second = new String("same");
		int firstId = PhotonPerformanceTraceUtil.node(first);
		int secondId = PhotonPerformanceTraceUtil.node(second);
		assertNotEquals(firstId, secondId);
		assertEquals(firstId, PhotonPerformanceTraceUtil.node(first));
		PhotonPerformanceTraceUtil.frame(1, System.nanoTime());
		assertEquals(firstId, PhotonPerformanceTraceUtil.node(first));
		assertEquals(secondId, PhotonPerformanceTraceUtil.node(second));
	}

	@Test
	public void threadEventsHaveUniqueIdsAndIndependentNesting() throws Exception {
		PhotonPerformanceTraceUtil.start("threads", Mode.DETAIL);
		var ready = new CountDownLatch(2);
		var proceed = new CountDownLatch(1);
		try (var workers = Executors.newFixedThreadPool(2)) {
			var first = workers.submit(() -> recordWorker(ready, proceed));
			var second = workers.submit(() -> recordWorker(ready, proceed));
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			proceed.countDown();
			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);
		}
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		var events = events(capture);
		assertEquals(2, events.size());
		assertNotEquals(events.getFirst().id(), events.getLast().id());
		assertTrue(events.stream().allMatch(event -> event.parent() == 0 && event.completed()));
	}

	@Test
	public void failedAndOpenScopesAreNotReportedAsSuccessful() {
		PhotonPerformanceTraceUtil.start("failures", Mode.DETAIL);
		long failed = PhotonPerformanceTraceUtil.begin(Stage.FX_CREATE, 0);
		PhotonPerformanceTraceUtil.end(failed, false);
		PhotonPerformanceTraceUtil.begin(Stage.SEEK, 0);
		var capture = PhotonPerformanceTraceUtil.finish("interrupted");
		assertNotNull(capture);
		var events = events(capture);
		assertTrue(events.getFirst().failed());
		assertFalse(events.getLast().completed());
		assertEquals(1, capture.view().incomplete());
	}

	@Test
	public void previousSessionHandleCannotCloseNewScope() {
		PhotonPerformanceTraceUtil.start("old", Mode.DETAIL);
		long old = PhotonPerformanceTraceUtil.begin(Stage.SEEK, 0);
		PhotonPerformanceTraceUtil.finish("replaced");
		PhotonPerformanceTraceUtil.start("new", Mode.DETAIL);
		long current = PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0);
		PhotonPerformanceTraceUtil.end(old, true);
		PhotonPerformanceTraceUtil.end(current, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertEquals(0, capture.view().incomplete());
		assertTrue(events(capture).getFirst().completed());
	}

	@Test
	public void overviewAggregatesDoNotPretendToContainDetailedEvents() {
		PhotonPerformanceTraceUtil.start("overview", Mode.OVERVIEW);
		assertEquals(0, PhotonPerformanceTraceUtil.begin(Stage.MODEL_PART, 0));
		long token = PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0);
		PhotonPerformanceTraceUtil.end(token, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertTrue(events(capture).isEmpty());
		assertEquals(1, capture.view().aggregates().getFirst().calls());
		assertEquals(0, capture.view().dropped());
	}

	@Test
	public void depthAndPerFrameBudgetsKeepOriginalStackBalanced() {
		PhotonPerformanceTraceUtil.start("capacity", Mode.DETAIL);
		long[] handles = new long[PhotonPerformanceTraceUtil.MAX_DEPTH + 1];
		for (int index = 0; index < handles.length; index++) {
			handles[index] = PhotonPerformanceTraceUtil.begin(Stage.SEEK, 0);
		}
		assertEquals(0, handles[handles.length - 1]);
		for (int index = handles.length - 1; index >= 0; index--) {
			PhotonPerformanceTraceUtil.end(handles[index], true);
		}
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_SPANS_PER_FRAME + 10; index++) {
			long token = PhotonPerformanceTraceUtil.begin(Stage.OBJECT_TICK, 0);
			PhotonPerformanceTraceUtil.end(token, true);
		}
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertEquals(0, capture.view().incomplete());
		assertTrue(capture.view().dropped() > 0);
		assertTrue(events(capture).size() <= PhotonPerformanceTraceUtil.MAX_SPANS_PER_FRAME);
	}


	@Test
	public void observedGenerationAndLocalTimeFollowParentOwnership() {
		PhotonPerformanceTraceUtil.start("generation", Mode.DETAIL);
		int root = PhotonPerformanceTraceUtil.node(new Object());
		int child = PhotonPerformanceTraceUtil.namedNode(root, "part", "part_a", "part_a", "");
		PhotonPerformanceTraceUtil.generation(root, 7);
		long token = PhotonPerformanceTraceUtil.begin(Stage.MODEL_PART, child, 4.5);
		PhotonPerformanceTraceUtil.end(token, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertEquals(7, events(capture).getFirst().generation());
		assertEquals(4.5, events(capture).getFirst().localTime());
	}

	@Test
	public void importantMilestonesSurviveRollingHighFrequencyMarks() {
		PhotonPerformanceTraceUtil.start("milestones", Mode.DETAIL);
		PhotonPerformanceTraceUtil.important(Stage.CLIENT_PHASE, 0, "first_charge", "", 0, 0);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_MARKS + 10; index++) {
			PhotonPerformanceTraceUtil.mark(Stage.COMMAND_SEND, 0, "aim", "", index, 0);
		}
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertTrue(capture.view().marks().stream().anyMatch(mark -> mark.code().equals("first_charge")));
		assertTrue(capture.view().marks().stream().anyMatch(mark -> mark.value() == PhotonPerformanceTraceUtil.MAX_MARKS + 9));
		assertTrue(capture.view().overwritten() > 0);
	}

	@Test
	public void namedNodesRemainResolvableAtDictionaryCapacity() {
		PhotonPerformanceTraceUtil.start("dictionary", Mode.DETAIL);
		int original = PhotonPerformanceTraceUtil.namedNode(0, "part", "existing", "existing", "");
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_NODES; index++) {
			PhotonPerformanceTraceUtil.namedNode(0, "part", Integer.toString(index), Integer.toString(index), "");
		}
		assertEquals(original, PhotonPerformanceTraceUtil.namedNode(0, "part", "existing", "existing", ""));
		assertEquals(0, PhotonPerformanceTraceUtil.namedNode(0, "part", "overflow", "overflow", ""));
	}

	@Test
	public void peakCallEvidenceSurvivesRawRingOverwrite() {
		PhotonPerformanceTraceUtil.start("outlier", Mode.DETAIL);
		long first = PhotonPerformanceTraceUtil.begin(Stage.SEEK, 0);
		PhotonPerformanceTraceUtil.end(first, true);
		for (int index = 0; index < PhotonPerformanceTraceUtil.EVENTS_PER_THREAD + 10; index++) {
			PhotonPerformanceTraceUtil.frame(index / 1000, 0);
			long token = PhotonPerformanceTraceUtil.begin(Stage.OBJECT_TICK, 0);
			PhotonPerformanceTraceUtil.end(token, true);
		}
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertTrue(events(capture).stream().noneMatch(event -> event.id() == first));
		var seek = capture.view().aggregates().stream().filter(value -> value.stage() == Stage.SEEK).findFirst().orElseThrow();
		assertNotNull(seek.maximumEvent());
		assertEquals(first, seek.maximumEvent().id());
		assertEquals(seek.maximumNs(), seek.maximumEvent().endNs() - seek.maximumEvent().startNs());
	}

	@Test
	public void nodeCapacityStopsNewSpansButKeepsAnOpenParent() {
		PhotonPerformanceTraceUtil.start("node_limit", Mode.DETAIL);
		long parent = PhotonPerformanceTraceUtil.begin(Stage.SEEK, 0);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_NODES - 1; index++) {
			PhotonPerformanceTraceUtil.namedNode(0, "part", Integer.toString(index), "part", "");
		}
		assertEquals(0, PhotonPerformanceTraceUtil.namedNode(0, "part", "overflow", "overflow", ""));
		var limit = PhotonPerformanceTraceUtil.limit();
		assertNotNull(limit);
		assertEquals(PhotonPerformanceTraceUtil.LimitReason.NODES, limit.reason());
		assertFalse(PhotonPerformanceTraceUtil.active());
		assertEquals(0, PhotonPerformanceTraceUtil.begin(Stage.REPLAY, 0));
		assertEquals(1, PhotonPerformanceTraceUtil.namedNode(0, "part", "0", "part", ""));
		PhotonPerformanceTraceUtil.end(parent, true);
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		assertEquals(1, capture.view().quality().deniedNodeRequests());
		assertEquals(0, capture.view().incomplete());
		assertEquals(1, capture.view().aggregates().getFirst().calls());
		assertEquals(limit, capture.view().quality().limit());
		assertTrue(events(capture).getFirst().completed());
	}

	@Test
	public void aggregateAdmissionReservesParentBeforeChildrenFillTheTable() {
		PhotonPerformanceTraceUtil.start("aggregate_limit", Mode.DETAIL);
		long parent = PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_AGGREGATES_PER_THREAD - 1; index++) {
			int node = PhotonPerformanceTraceUtil.namedNode(0, "part", Integer.toString(index), "part", "");
			long child = PhotonPerformanceTraceUtil.begin(Stage.OBJECT_TICK, node);
			PhotonPerformanceTraceUtil.end(child, true);
		}
		assertEquals(0, PhotonPerformanceTraceUtil.begin(Stage.MODEL_SAMPLE, 1));
		PhotonPerformanceTraceUtil.end(parent, true);
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		assertNotNull(capture.view().quality().limit());
		assertEquals(PhotonPerformanceTraceUtil.LimitReason.AGGREGATES, capture.view().quality().limit().reason());
		assertEquals(1, capture.view().quality().deniedAggregateRequests());
		assertEquals(0, capture.view().quality().deniedNodeRequests());
		assertEquals(PhotonPerformanceTraceUtil.MAX_AGGREGATES_PER_THREAD, capture.view().aggregates().size());
		var render = capture.view().aggregates().stream().filter(value -> value.stage() == Stage.RENDER).findFirst().orElseThrow();
		assertEquals(1, render.calls());
		assertNotNull(render.maximumEvent());
		assertTrue(render.maximumEvent().completed());
		assertEquals(0, capture.view().incomplete());
	}

	@Test
	public void rollingRawBudgetKeepsWholeAggregatesWithoutStoppingTheCapture() {
		PhotonPerformanceTraceUtil.start("raw_budget", Mode.DETAIL);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_SPANS_PER_FRAME + 3; index++) {
			long token = PhotonPerformanceTraceUtil.begin(Stage.OBJECT_TICK, 0);
			PhotonPerformanceTraceUtil.end(token, true);
		}
		assertNull(PhotonPerformanceTraceUtil.limit());
		assertTrue(PhotonPerformanceTraceUtil.active());
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertEquals(3, capture.view().quality().droppedRawSpans());
		assertEquals(0, capture.view().quality().deniedAggregateRequests());
		assertEquals(PhotonPerformanceTraceUtil.MAX_SPANS_PER_FRAME, events(capture).size());
		assertEquals(PhotonPerformanceTraceUtil.MAX_SPANS_PER_FRAME + 3, capture.view().aggregates().getFirst().calls());
	}

	@Test
	public void counterCapacityHasItsOwnReasonAndDoesNotMasqueradeAsRawLoss() {
		PhotonPerformanceTraceUtil.start("counter_limit", Mode.DETAIL);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_AGGREGATES_PER_THREAD / 2; index++) {
			int node = PhotonPerformanceTraceUtil.namedNode(0, "part", Integer.toString(index), "part", "");
			PhotonPerformanceTraceUtil.counter(node, PhotonPerformanceTraceUtil.Metric.CACHE_HIT, 1);
			PhotonPerformanceTraceUtil.counter(node, PhotonPerformanceTraceUtil.Metric.CACHE_MISS, 1);
		}
		PhotonPerformanceTraceUtil.counter(1, PhotonPerformanceTraceUtil.Metric.REQUESTS, 1);
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		assertNotNull(capture.view().quality().limit());
		assertEquals(PhotonPerformanceTraceUtil.LimitReason.COUNTERS, capture.view().quality().limit().reason());
		assertEquals(1, capture.view().quality().deniedCounterRequests());
		assertEquals(0, capture.view().quality().droppedRawSpans());
		assertEquals(PhotonPerformanceTraceUtil.MAX_AGGREGATES_PER_THREAD, capture.view().counters().size());
	}

	@Test
	public void deniedNinthThreadStillAllowsTheFirstEightToClose() throws Exception {
		PhotonPerformanceTraceUtil.start("thread_limit", Mode.DETAIL);
		var ready = new CountDownLatch(PhotonPerformanceTraceUtil.MAX_THREADS);
		var proceed = new CountDownLatch(1);
		try (var workers = Executors.newFixedThreadPool(PhotonPerformanceTraceUtil.MAX_THREADS)) {
			var futures = new ArrayList<Future<?>>();
			for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_THREADS; index++) {
				futures.add(workers.submit(() -> recordWorker(ready, proceed)));
			}
			try {
				assertTrue(ready.await(5, TimeUnit.SECONDS));
				assertEquals(0, PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0));
			} finally {
				proceed.countDown();
			}
			for (var future : futures) {
				future.get(5, TimeUnit.SECONDS);
			}
		}
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		assertNotNull(capture.view().quality().limit());
		assertEquals(PhotonPerformanceTraceUtil.LimitReason.THREADS, capture.view().quality().limit().reason());
		assertEquals(1, capture.view().quality().deniedThreadRequests());
		assertEquals(PhotonPerformanceTraceUtil.MAX_THREADS, events(capture).size());
		assertEquals(0, capture.view().incomplete());
	}

	@Test
	public void importantMarkCapacityKeepsTheFirstCause() {
		PhotonPerformanceTraceUtil.start("important_limit", Mode.DETAIL);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_IMPORTANT_MARKS + 1; index++) {
			PhotonPerformanceTraceUtil.important(Stage.CLIENT_PHASE, 0, "phase", "", index, 0);
		}
		var limit = PhotonPerformanceTraceUtil.limit();
		assertNotNull(limit);
		assertEquals(PhotonPerformanceTraceUtil.LimitReason.IMPORTANT_MARKS, limit.reason());
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_NODES + 1; index++) {
			PhotonPerformanceTraceUtil.namedNode(0, "later", Integer.toString(index), "", "");
		}
		assertSame(limit, PhotonPerformanceTraceUtil.limit());
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		assertEquals(PhotonPerformanceTraceUtil.MAX_IMPORTANT_MARKS, capture.view().marks().size());
		assertEquals(1, capture.view().quality().droppedImportantMarks());
	}

	@Test
	public void scopedDefinitionsKeepTargetAndExecutionOwnershipSeparate() {
		PhotonPerformanceTraceUtil.start("contexts", Mode.DETAIL);
		var definition = new Object();
		int target = PhotonPerformanceTraceUtil.namedNode(0, "target", "a", "target_a", "");
		int otherTarget = PhotonPerformanceTraceUtil.namedNode(0, "target", "b", "target_b", "");
		int firstPlayer = PhotonPerformanceTraceUtil.namedNode(0, "timeline_player", "first", "first", "");
		int secondPlayer = PhotonPerformanceTraceUtil.namedNode(0, "timeline_player", "second", "second", "");
		var description = new PhotonPerformanceTraceUtil.Node(0, target, "track", "track", "", "");
		int first = PhotonPerformanceTraceUtil.node(definition, description, firstPlayer);
		int second = PhotonPerformanceTraceUtil.node(definition, description, secondPlayer);
		int third = PhotonPerformanceTraceUtil.node(definition,
				new PhotonPerformanceTraceUtil.Node(0, otherTarget, "track", "track", "", ""), firstPlayer);
		assertNotEquals(first, second);
		assertNotEquals(first, third);
		assertEquals(first, PhotonPerformanceTraceUtil.knownNode(definition, target, firstPlayer));
		assertEquals(second, PhotonPerformanceTraceUtil.knownNode(definition, target, secondPlayer));
		assertEquals(first, PhotonPerformanceTraceUtil.node(definition, description, firstPlayer));
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertEquals(target, capture.view().nodes().get(first).parent());
		assertEquals(3, capture.view().contexts().size());
		assertTrue(capture.view().contexts().contains(new PhotonPerformanceTraceUtil.Context(first, firstPlayer)));
		assertTrue(capture.view().contexts().contains(new PhotonPerformanceTraceUtil.Context(second, secondPlayer)));
	}

	@Test
	public void equalScopedDefinitionsStillUseObjectIdentity() {
		PhotonPerformanceTraceUtil.start("scoped_identity", Mode.DETAIL);
		var first = new String("equal");
		var second = new String("equal");
		var description = new PhotonPerformanceTraceUtil.Node(0, 0, "track", "track", "", "");
		int firstId = PhotonPerformanceTraceUtil.node(first, description, 0);
		int secondId = PhotonPerformanceTraceUtil.node(second, description, 0);
		assertNotEquals(firstId, secondId);
		assertEquals(firstId, PhotonPerformanceTraceUtil.knownNode(first, 0, 0));
		assertEquals(secondId, PhotonPerformanceTraceUtil.knownNode(second, 0, 0));
	}

	@Test
	public void animationContextFindsTheNearestTickOrFrameAcrossChildScopes() {
		PhotonPerformanceTraceUtil.start("animation_stack", Mode.DETAIL);
		int frameOwner = PhotonPerformanceTraceUtil.node(new Object());
		int tickOwner = PhotonPerformanceTraceUtil.node(new Object());
		long frame = PhotonPerformanceTraceUtil.begin(Stage.ANIMATION_FRAME, frameOwner, 2.5);
		long model = PhotonPerformanceTraceUtil.begin(Stage.MODEL_SAMPLE, 0);
		assertEquals(frameOwner, PhotonPerformanceTraceUtil.currentNode(Stage.ANIMATION, Stage.ANIMATION_FRAME));
		long tick = PhotonPerformanceTraceUtil.begin(Stage.ANIMATION, tickOwner, 3);
		assertEquals(tickOwner, PhotonPerformanceTraceUtil.currentNode(Stage.ANIMATION, Stage.ANIMATION_FRAME));
		PhotonPerformanceTraceUtil.end(tick, true);
		assertEquals(frameOwner, PhotonPerformanceTraceUtil.currentNode(Stage.ANIMATION, Stage.ANIMATION_FRAME));
		PhotonPerformanceTraceUtil.end(model, true);
		PhotonPerformanceTraceUtil.end(frame, true);
		assertEquals(0, PhotonPerformanceTraceUtil.currentNode(Stage.ANIMATION, Stage.ANIMATION_FRAME));
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		assertEquals(2.5, events(capture).getFirst().localTime());
		assertEquals(0, capture.view().incomplete());
	}

	@Test
	public void scopedLookupSurvivesTheSharedDictionaryBudget() {
		PhotonPerformanceTraceUtil.start("scoped_budget", Mode.DETAIL);
		var owner = new Object();
		int target = PhotonPerformanceTraceUtil.namedNode(0, "target", "target", "target", "");
		int player = PhotonPerformanceTraceUtil.namedNode(0, "timeline_player", "player", "player", "");
		var description = new PhotonPerformanceTraceUtil.Node(0, target, "track", "track", "", "");
		int original = PhotonPerformanceTraceUtil.node(owner, description, player);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_NODES - 4; index++) {
			PhotonPerformanceTraceUtil.namedNode(0, "fill", Integer.toString(index), "fill", "");
		}
		assertEquals(0, PhotonPerformanceTraceUtil.node(owner, description, 0));
		assertEquals(original, PhotonPerformanceTraceUtil.knownNode(owner, target, player));
		assertEquals(original, PhotonPerformanceTraceUtil.node(owner, description, player));
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		assertEquals(PhotonPerformanceTraceUtil.MAX_NODES, capture.view().nodes().size());
		assertEquals(1, capture.view().contexts().size());
		assertEquals(1, capture.view().quality().deniedNodeRequests());
	}

	@Test
	public void newSessionsDoNotReuseScopedMappingsFromAnEarlierCapture() {
		var definition = new Object();
		var description = new PhotonPerformanceTraceUtil.Node(0, 0, "track", "track", "", "");
		PhotonPerformanceTraceUtil.start("old_scoped", Mode.DETAIL);
		PhotonPerformanceTraceUtil.node(definition, description, 0);
		var first = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(first);
		PhotonPerformanceTraceUtil.start("new_scoped", Mode.DETAIL);
		assertEquals(0, PhotonPerformanceTraceUtil.knownNode(definition, 0, 0));
		PhotonPerformanceTraceUtil.node(definition, description, 0);
		var second = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(second);
		assertNotEquals(first.view().sessionId(), second.view().sessionId());
		assertEquals(2, second.view().nodes().size());
	}

	private static void recordWorker(CountDownLatch ready, CountDownLatch proceed) {
		long token = PhotonPerformanceTraceUtil.begin(Stage.COMMAND_HANDLE, 0);
		ready.countDown();
		try {
			if (!proceed.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("worker_timeout");
			}
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(failure);
		} finally {
			PhotonPerformanceTraceUtil.end(token, true);
		}
	}

	private static List<Event> events(Capture capture) {
		var events = new ArrayList<Event>();
		capture.threads().forEach(thread -> thread.forEachEvent(events::add));
		return events;
	}
}
