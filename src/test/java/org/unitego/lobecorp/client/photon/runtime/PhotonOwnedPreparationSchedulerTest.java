package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKey;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources.SourceKind;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler.Kind;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedPreparationScheduler.Task;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonOwnedPreparationSchedulerTest {
	@BeforeEach
	public void resetBeforeTest() {
		PhotonOwnedPreparationScheduler.reset();
	}

	@AfterEach
	public void resetAfterTest() {
		PhotonOwnedPreparationScheduler.reset();
	}

	@Test
	public void earlierSuccessPreservesLaterDeadline() {
		assertRemainingDeadline(true);
	}

	@Test
	public void laterSuccessPreservesEarlierDeadline() {
		assertRemainingDeadline(false);
	}

	private void assertRemainingDeadline(boolean completeEarlier) {
		var early = failedDevice(source("laser", SourceKind.AUTHORING, 1), 100);
		var late = failedDevice(source("laser", SourceKind.PUBLISHED_PREVIEW, 1), 200);
		assertFalse(PhotonOwnedPreparationScheduler.hasWork(99));
		assertTrue(PhotonOwnedPreparationScheduler.hasWork(100));
		assertEquals(List.of(early), PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, 100));

		var completed = completeEarlier ? early : late;
		var remaining = completeEarlier ? late : early;
		PhotonOwnedPreparationScheduler.commit(PhotonOwnedPreparationScheduler.ready(
				PhotonOwnedPreparationScheduler.begin(completed)));

		assertFalse(PhotonOwnedPreparationScheduler.current(completed, completed.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(remaining, remaining.revision()));
		assertFalse(PhotonOwnedPreparationScheduler.hasWork(remaining.dueAt() - 1));
		assertTrue(PhotonOwnedPreparationScheduler.hasWork(remaining.dueAt()));
		assertTrue(PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, remaining.dueAt() - 1).isEmpty());
		assertEquals(List.of(remaining), PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, remaining.dueAt()));
	}

	@Test
	public void cancelSnapshotPreservesDeviceRetryForBothPreviewKinds() {
		for (var sourceKind : List.of(SourceKind.AUTHORING, SourceKind.PUBLISHED_PREVIEW)) {
			PhotonOwnedPreparationScheduler.reset();
			var published = source("laser", sourceKind, 1);
			var device = failedDevice(published, 100);
			var changed = source("laser", sourceKind, 2);
			var kind = sourceKind == SourceKind.AUTHORING ? Kind.AUTHORING : Kind.PUBLISHED_PREVIEW;
			var snapshot = PhotonOwnedPreparationScheduler.enqueue(kind, changed, changed.revision());

			PhotonOwnedPreparationScheduler.cancelSnapshot(published.effectId(), sourceKind);

			assertFalse(PhotonOwnedPreparationScheduler.current(snapshot, changed.revision()));
			assertTrue(PhotonOwnedPreparationScheduler.current(device, published.revision()));
			assertTrue(PhotonOwnedPreparationScheduler.ready(kind, 100).isEmpty());
			assertFalse(PhotonOwnedPreparationScheduler.hasWork(99));
			assertTrue(PhotonOwnedPreparationScheduler.hasWork(100));
			assertEquals(List.of(device), PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, 100));
		}
	}

	@Test
	public void cancelSourceRemovesAllMatchingTasksAndPreservesOtherSources() {
		var key = source("laser", SourceKind.AUTHORING, 1);
		var device = failedDevice(key, 100);
		var snapshot = PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING,
				source("laser", SourceKind.AUTHORING, 2), 2);
		var otherKind = PhotonOwnedPreparationScheduler.enqueue(Kind.PUBLISHED_PREVIEW,
				source("laser", SourceKind.PUBLISHED_PREVIEW, 1), 1);
		var otherEffect = PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING,
				source("other", SourceKind.AUTHORING, 1), 1);

		PhotonOwnedPreparationScheduler.cancelSource(key.effectId(), key.kind());

		assertFalse(PhotonOwnedPreparationScheduler.current(device, device.revision()));
		assertFalse(PhotonOwnedPreparationScheduler.current(snapshot, snapshot.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(otherKind, otherKind.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(otherEffect, otherEffect.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, 100).isEmpty());
		assertEquals(List.of(otherKind), PhotonOwnedPreparationScheduler.ready(Kind.PUBLISHED_PREVIEW, 100));
		assertEquals(List.of(otherEffect), PhotonOwnedPreparationScheduler.ready(Kind.AUTHORING, 100));
	}

	@Test
	public void resetInvalidatesOldTaskEvenIfSameRevisionIsReused() {
		var key = source("laser", SourceKind.AUTHORING, 1);
		var old = PhotonOwnedPreparationScheduler.begin(
				PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING, key, key.revision()));

		PhotonOwnedPreparationScheduler.reset();

		assertFalse(PhotonOwnedPreparationScheduler.closed());
		assertFalse(PhotonOwnedPreparationScheduler.current(old, key.revision()));
		assertFalse(PhotonOwnedPreparationScheduler.hasWork(0));
		var next = PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING, key, key.revision());
		assertNotEquals(old.generation(), next.generation());
		assertFalse(PhotonOwnedPreparationScheduler.current(old, key.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(next, key.revision()));
	}

	@Test
	public void closeInvalidatesTaskAndRejectsEnqueueUntilReset() {
		var key = source("laser", SourceKind.AUTHORING, 1);
		var old = failedDevice(key, 100);

		PhotonOwnedPreparationScheduler.close();

		assertTrue(PhotonOwnedPreparationScheduler.closed());
		assertFalse(PhotonOwnedPreparationScheduler.current(old, key.revision()));
		assertFalse(PhotonOwnedPreparationScheduler.hasWork(100));
		assertTrue(PhotonOwnedPreparationScheduler.ready(Kind.DEVICE, 100).isEmpty());
		assertThrows(IllegalStateException.class,
				() -> PhotonOwnedPreparationScheduler.enqueue(Kind.DEVICE, key, key.revision()));

		PhotonOwnedPreparationScheduler.reset();
		var next = PhotonOwnedPreparationScheduler.enqueue(Kind.DEVICE, key, key.revision());
		assertFalse(PhotonOwnedPreparationScheduler.current(old, key.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(next, key.revision()));
	}

	@Test
	public void newerRevisionReplacesInFlightSnapshotWithoutReplacingOtherSource() {
		var first = source("laser", SourceKind.AUTHORING, 1);
		var old = PhotonOwnedPreparationScheduler.begin(
				PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING, first, first.revision()));
		var other = PhotonOwnedPreparationScheduler.enqueue(Kind.PUBLISHED_PREVIEW,
				source("laser", SourceKind.PUBLISHED_PREVIEW, 1), 1);
		var second = source("laser", SourceKind.AUTHORING, 2);
		var latest = PhotonOwnedPreparationScheduler.enqueue(Kind.AUTHORING, second, second.revision());

		assertFalse(PhotonOwnedPreparationScheduler.current(old, first.revision()));
		assertFalse(PhotonOwnedPreparationScheduler.current(latest, first.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(latest, second.revision()));
		assertTrue(PhotonOwnedPreparationScheduler.current(other, other.revision()));
		assertEquals(List.of(latest), PhotonOwnedPreparationScheduler.ready(Kind.AUTHORING, 0));
		assertEquals(List.of(other), PhotonOwnedPreparationScheduler.ready(Kind.PUBLISHED_PREVIEW, 0));
	}

	private Task failedDevice(SourceKey source, long deadline) {
		var task = PhotonOwnedPreparationScheduler.enqueue(Kind.DEVICE, source, source.revision());
		return PhotonOwnedPreparationScheduler.fail(PhotonOwnedPreparationScheduler.begin(task), deadline, "device");
	}

	private SourceKey source(String path, SourceKind kind, long revision) {
		return new SourceKey(Identifier.fromNamespaceAndPath("scheduler_test", path), kind, revision);
	}
}

