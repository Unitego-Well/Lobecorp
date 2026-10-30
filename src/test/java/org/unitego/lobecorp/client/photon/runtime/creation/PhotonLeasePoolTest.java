package org.unitego.lobecorp.client.photon.runtime.creation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonLeasePoolTest {
	@Test
	public void quarantineRequiresDrainedThenBothRealClocksAndInvalidatesOldLease() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var first = pool.acquire("fx", Fake::new, Fake::reset);
		var value = first.runtime();
		pool.release(first, false);
		assertFalse(first.current());
		assertThrows(IllegalStateException.class, first::runtime);
		assertFalse(value.force);
		pool.pulse(1, 1);
		assertEquals(0, pool.statistics().idle());
		value.alive = false;
		pool.pulse(2, 2);
		pool.pulse(3, 2);
		assertEquals(0, pool.statistics().idle());
		pool.pulse(3, 3);
		assertEquals(1, pool.statistics().idle());
		var second = pool.acquire("fx", Fake::new, Fake::reset);
		assertSame(value, second.runtime());
		assertNotEquals(first.id(), second.id());
		pool.release(first, true);
		assertTrue(second.current());
		assertEquals(1, value.stops);
		assertEquals(1, value.resets);
		assertEquals(1, pool.statistics().hit());
	}

	@Test
	public void sameFrameMissesAreImmediateAndNeverDropEffects() {
		var pool = new PhotonLeasePool<>(new FakeLifecycle());
		var leases = new ArrayList<PhotonLeasePool.Lease<Fake>>();
		for (int i = 0; i < 32; i++) {
			leases.add(pool.acquire("fx", Fake::new, Fake::reset));
		}
		leases.forEach(lease -> pool.release(lease, true));
		pool.pulse(1, 1);
		for (int i = 0; i < 32; i++) {
			assertTrue(pool.acquire("fx", Fake::new, Fake::reset).current());
		}
		assertEquals(64, pool.statistics().created());
		assertEquals(64, pool.statistics().miss());
		assertEquals(0, pool.statistics().hit());
	}

	@Test
	public void clearInvalidatesActiveAndDrainingAndLateReleaseCannotReturnThem() {
		var pool = new PhotonLeasePool<>(new FakeLifecycle());
		var active = pool.acquire("fx", Fake::new, Fake::reset);
		var draining = pool.acquire("fx", Fake::new, Fake::reset);
		var first = active.runtime();
		var second = draining.runtime();
		pool.release(draining, false);
		pool.clear();
		pool.release(active, true);
		pool.release(draining, true);
		pool.pulse(10, 10);
		assertFalse(active.current());
		assertFalse(draining.current());
		assertEquals(1, first.disposals);
		assertEquals(1, second.disposals);
		assertEquals(0, pool.statistics().idle());
		assertEquals(2, pool.statistics().retired());
	}

	@Test
	public void resetFailureDisposesCandidateAndCreatesReplacementImmediately() {
		var pool = new PhotonLeasePool<>(new FakeLifecycle());
		var first = pool.acquire("fx", Fake::new, Fake::reset);
		var broken = first.runtime();
		pool.release(first, true);
		pool.pulse(1, 1);
		pool.pulse(2, 2);
		var replacement = pool.acquire("fx", Fake::new, _ -> {
			throw new IllegalStateException("reset failed");
		});
		assertNotSame(broken, replacement.runtime());
		assertEquals(1, broken.disposals);
		assertEquals(2, pool.statistics().created());
		assertEquals(1, pool.statistics().reset());
		assertEquals(1, pool.statistics().retired());
	}

	@Test
	public void resetCallbackMayAcquireWithoutReusingReservedCandidate() {
		var pool = new PhotonLeasePool<>(new FakeLifecycle());
		var first = pool.acquire("fx", Fake::new, Fake::reset);
		var value = first.runtime();
		pool.release(first, true);
		pool.pulse(1, 1);
		pool.pulse(2, 2);
		var nested = new ArrayList<PhotonLeasePool.Lease<Fake>>();
		var next = pool.acquire("fx", Fake::new, runtime -> {
			runtime.reset();
			nested.add(pool.acquire("fx", Fake::new, Fake::reset));
		});
		assertSame(value, next.runtime());
		assertNotSame(value, nested.getFirst().runtime());
		assertTrue(next.current());
		assertTrue(nested.getFirst().current());
	}

	@Test
	public void clearInsideResetOrCreateCannotPublishOldGeneration() {
		var pool = new PhotonLeasePool<>(new FakeLifecycle());
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		var value = lease.runtime();
		pool.release(lease, true);
		pool.pulse(1, 1);
		pool.pulse(2, 2);
		assertThrows(IllegalStateException.class,
				() -> pool.acquire("fx", Fake::new, _ -> pool.clear()));
		assertEquals(1, value.disposals);
		var made = new Fake();
		assertThrows(IllegalStateException.class, () -> pool.acquire("fx", () -> {
			pool.clear();
			return made;
		}, Fake::reset));
		assertEquals(1, made.disposals);
		assertEquals(0, pool.statistics().active());
	}

	@Test
	public void stopAndDrainCallbacksCanClearWithoutReenqueuing() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		lifecycle.onStop = pool::clear;
		pool.release(lease, false);
		assertEquals(0, pool.statistics().quarantine());
		lifecycle.onStop = () -> {
		};
		var another = pool.acquire("fx", Fake::new, Fake::reset);
		pool.release(another, true);
		lifecycle.onDrain = pool::clear;
		pool.pulse(1, 1);
		assertEquals(0, pool.statistics().quarantine());
	}

	@Test
	public void failureCleanupContinuesAndCreateFailuresPropagate() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var disposed = new AtomicInteger();
		lifecycle.onDispose = () -> {
			disposed.incrementAndGet();
			throw new IllegalStateException("dispose");
		};
		pool.acquire("one", Fake::new, Fake::reset);
		pool.acquire("two", Fake::new, Fake::reset);
		assertThrows(IllegalStateException.class, pool::clear);
		assertEquals(2, disposed.get());
		assertEquals(0, pool.statistics().active());
		assertThrows(IllegalArgumentException.class, () -> pool.acquire("fx", () -> {
			throw new IllegalArgumentException("create");
		}, Fake::reset));
		assertThrows(IllegalArgumentException.class, () -> pool.pulse(-1, 0));
	}

	@Test
	public void keysAndForeignLeasesRemainIsolated() {
		var lifecycle = new FakeLifecycle();
		var first = new PhotonLeasePool<>(lifecycle);
		var second = new PhotonLeasePool<>(lifecycle);
		var lease = first.acquire(null, Fake::new, Fake::reset);
		second.release(lease, true);
		assertTrue(lease.current());
		assertThrows(IllegalArgumentException.class,
				() -> first.acquire("other", lease::runtime, Fake::reset));
		first.release(lease, true);
		first.pulse(1, 1);
		first.pulse(2, 2);
		assertEquals(1, first.statistics().idle());
		assertEquals(0, first.statistics().hit());
	}

	@Test
	public void unknownTypesRetireWithoutForceDroppingNaturalRemnants() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var lease = pool.acquire("unknown", Fake::new, Fake::reset);
		var value = lease.runtime();
		value.reusable = false;
		value.particles = 5;
		pool.release(lease, false);
		pool.pulse(1, 1);
		pool.pulse(2, 2);
		assertEquals(0, pool.statistics().idle());
		assertEquals(1, pool.statistics().retired());
		assertFalse(value.force);
		assertEquals(0, value.disposals);
		assertEquals(5, value.particles);
		assertNotSame(value, pool.acquire("unknown", Fake::new, Fake::reset).runtime());
	}

	@Test
	public void removedEmitterWithResidualParticlesCannotEnterQuarantine() {
		var pool = new PhotonLeasePool<>(new FakeLifecycle());
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		var value = lease.runtime();
		value.particles = 4;
		pool.release(lease, false);
		value.alive = false;
		pool.pulse(1, 1);
		pool.pulse(2, 2);
		assertEquals(0, pool.statistics().idle());
		value.particles = 0;
		pool.pulse(3, 3);
		pool.pulse(4, 3);
		assertEquals(0, pool.statistics().idle());
		pool.pulse(4, 4);
		assertEquals(1, pool.statistics().idle());
	}

	@Test
	public void parkedRunsOnlyAfterDrainAndBothBarriersBeforeIdlePublication() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		var value = lease.runtime();
		var calls = new AtomicInteger();
		lifecycle.onParked = () -> {
			assertEquals(0, pool.statistics().idle());
			assertFalse(lease.current());
			calls.incrementAndGet();
		};
		pool.release(lease, false);
		pool.pulse(1, 1);
		assertEquals(0, calls.get());
		value.alive = false;
		pool.pulse(2, 2);
		pool.pulse(3, 2);
		assertEquals(0, calls.get());
		pool.pulse(3, 3);
		assertEquals(1, calls.get());
		assertEquals(1, pool.statistics().idle());
		pool.pulse(4, 4);
		assertEquals(1, calls.get());
	}

	@Test
	public void parkedFailureDestroysCandidateAndCannotPublishIdle() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		var value = lease.runtime();
		pool.release(lease, true);
		pool.pulse(1, 1);
		lifecycle.onParked = () -> {
			throw new IllegalStateException("parked failed");
		};
		assertThrows(IllegalStateException.class, () -> pool.pulse(2, 2));
		assertEquals(1, value.disposals);
		assertEquals(1, pool.statistics().retired());
		assertEquals(0, pool.statistics().idle());
		assertNotSame(value, pool.acquire("fx", Fake::new, Fake::reset).runtime());
	}

	@Test
	public void parkedClearReentryCannotPublishPreviousEpochOrDisposeTwice() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		var value = lease.runtime();
		pool.release(lease, true);
		pool.pulse(1, 1);
		lifecycle.onParked = pool::clear;
		pool.pulse(2, 2);
		assertEquals(1, value.disposals);
		assertEquals(0, pool.statistics().idle());
		assertEquals(0, pool.statistics().quarantine());
		pool.release(lease, true);
		assertEquals(1, value.disposals);
	}

	@Test
	public void parkedAcquireAndPulseReentryCannotRentOrParkReservedValueAgain() {
		var lifecycle = new FakeLifecycle();
		var pool = new PhotonLeasePool<>(lifecycle);
		var lease = pool.acquire("fx", Fake::new, Fake::reset);
		var value = lease.runtime();
		pool.release(lease, true);
		pool.pulse(1, 1);
		var calls = new AtomicInteger();
		lifecycle.onParked = () -> {
			calls.incrementAndGet();
			assertNotSame(value, pool.acquire("fx", Fake::new, Fake::reset).runtime());
			pool.pulse(3, 3);
		};
		pool.pulse(2, 2);
		assertEquals(1, calls.get());
		assertEquals(1, pool.statistics().idle());
		assertSame(value, pool.acquire("fx", Fake::new, Fake::reset).runtime());
	}

	private static class Fake {
		private boolean alive = true;
		private boolean reusable = true;
		private int particles;
		private boolean force;
		private int resets;
		private int stops;
		private int disposals;

		private void reset() {
			resets++;
			alive = true;
		}
	}

	private static class FakeLifecycle implements PhotonLeasePool.Lifecycle<Fake> {
		private Runnable onStop = () -> {
		};
		private Runnable onDrain = () -> {
		};
		private Runnable onParked = () -> {
		};
		private Runnable onDispose = () -> {
		};

		@Override
		public void stop(Fake value, boolean force) {
			value.stops++;
			value.force = force;
			if (force) {
				value.alive = false;
			}
			onStop.run();
		}

		@Override
		public boolean drained(Fake value) {
			onDrain.run();
			return !value.alive && value.particles == 0;
		}

		@Override
		public boolean reusable(Fake value) {
			return value.reusable;
		}

		@Override
		public void parked(Fake value) {
			onParked.run();
		}

		@Override
		public void dispose(Fake value) {
			value.disposals++;
			value.alive = false;
			onDispose.run();
		}
	}
}
