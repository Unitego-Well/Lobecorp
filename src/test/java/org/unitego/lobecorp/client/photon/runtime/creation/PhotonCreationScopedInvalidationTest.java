package org.unitego.lobecorp.client.photon.runtime.creation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 纯租约核心的独立域清理契约，不依赖游戏，不推进 Factory 的生产时钟。
public class PhotonCreationScopedInvalidationTest {
	@Test
	public void clearingProbeDomainCannotInvalidateOtherDomainOrReviveAnyReleasedBackend() {
		var disposed = new ArrayList<Node>();
		var probe = new PhotonLeasePool<>(lifecycle(disposed));
		var foreignDisposed = new ArrayList<Node>();
		var foreign = new PhotonLeasePool<>(lifecycle(foreignDisposed));
		var foreignLease = foreign.acquire("world", Node::new, _ -> {
		});
		var active = probe.acquire("world", Node::new, _ -> {
		});
		var idle = probe.acquire("world", Node::new, _ -> {
		});
		var draining = probe.acquire("world", Node::new, _ -> {
		});
		var activeNode = active.runtime();
		var idleNode = idle.runtime();
		var drainingNode = draining.runtime();

		probe.release(idle, true);
		probe.pulse(1, 1);
		probe.pulse(2, 2);
		probe.release(draining, true);
		assertEquals(1, probe.statistics().active());
		assertEquals(1, probe.statistics().idle());
		assertEquals(1, probe.statistics().quarantine());

		probe.clear();
		for (var old : List.of(active, idle, draining)) {
			assertFalse(old.current());
			assertThrows(IllegalStateException.class, old::runtime);
			probe.release(old, true);
		}
		probe.pulse(3, 3);
		probe.pulse(4, 4);
		assertEquals(3, disposed.size());
		assertEquals(3, disposed.stream().distinct().count());
		assertEquals(0, probe.statistics().active());
		assertEquals(0, probe.statistics().idle());
		assertEquals(0, probe.statistics().quarantine());
		assertTrue(foreignLease.current());
		assertTrue(foreignDisposed.isEmpty());

		var fresh = probe.acquire("world", Node::new, _ -> {
		});
		assertNotSame(activeNode, fresh.runtime());
		assertNotSame(idleNode, fresh.runtime());
		assertNotSame(drainingNode, fresh.runtime());
		probe.release(active, true);
		assertTrue(fresh.current());
		probe.clear();
		foreign.clear();
		assertEquals(4, disposed.size());
		assertEquals(1, foreignDisposed.size());
	}

	private static PhotonLeasePool.Lifecycle<Node> lifecycle(List<Node> disposed) {
		return new PhotonLeasePool.Lifecycle<>() {
			@Override
			public void stop(Node value, boolean force) {
				value.drained = force;
			}

			@Override
			public boolean drained(Node value) {
				return value.drained;
			}

			@Override
			public void dispose(Node value) {
				disposed.add(value);
			}
		};
	}

	private static class Node {
		private boolean drained;
	}
}
