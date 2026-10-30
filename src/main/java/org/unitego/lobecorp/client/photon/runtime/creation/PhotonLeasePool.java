package org.unitego.lobecorp.client.photon.runtime.creation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// 单线程租约核心；回调允许重入，时钟由宿主在真实 tick 和已完成渲染帧后推进。
public class PhotonLeasePool<T> {
	private final Lifecycle<T> lifecycle;
	private final Map<T, Slot<T>> owned = new IdentityHashMap<>();
	private final Map<Object, ArrayDeque<Slot<T>>> idle = new HashMap<>();
	private final Set<Slot<T>> pending = new LinkedHashSet<>();
	private final Map<Object, Integer> active = new HashMap<>();
	private final Map<Object, Integer> peaks = new HashMap<>();
	private long epoch;
	private long nextId;
	private long tick;
	private long frame;
	private long hits;
	private long misses;
	private long resets;
	private long created;
	private long retired;
	private boolean clearing;

	public PhotonLeasePool(Lifecycle<T> lifecycle) {
		this.lifecycle = Objects.requireNonNull(lifecycle);
	}

	public Lease<T> acquire(Object key, Supplier<T> create, Consumer<T> reset) {
		Objects.requireNonNull(create);
		Objects.requireNonNull(reset);
		if (clearing) {
			throw new IllegalStateException("pool clear in progress");
		}

		long generation = epoch;
		var available = idle.get(key);
		Slot<T> candidate = available == null ? null : available.pollFirst();

		if (candidate != null) {
			candidate.state = State.RESETTING;
			try {
				resets++;
				reset.accept(candidate.value);
			} catch (RuntimeException | Error failure) {
				discard(candidate, failure);
				candidate = null;
			}

			if (generation != epoch) {
				throw new IllegalStateException("pool cleared during reset");
			}
		}

		if (candidate == null) {
			misses++;
			T value = Objects.requireNonNull(create.get(), "runtime supplier returned null");
			if (owned.containsKey(value)) {
				throw new IllegalArgumentException("runtime already owned by pool");
			}

			created++;
			candidate = new Slot<>(key, value);
			if (generation != epoch) {
				retired++;
				lifecycle.dispose(value);
				throw new IllegalStateException("pool cleared during creation");
			}

			owned.put(value, candidate);
		} else {
			hits++;
		}

		candidate.state = State.ACTIVE;
		candidate.lease = new Lease<>(this, candidate, ++nextId, epoch);
		int count = active.merge(key, 1, Integer::sum);
		peaks.merge(key, count, Math::max);
		return candidate.lease;
	}

	public void release(Lease<T> lease, boolean force) {
		if (lease == null || lease.owner != this || !lease.current()) {
			return;
		}

		var slot = lease.slot;
		slot.state = State.STOPPING;
		active.computeIfPresent(slot.key, (_, count) -> count > 1 ? count - 1 : null);
		boolean reusable;
		try {
			lifecycle.stop(slot.value, force);
			reusable = lifecycle.reusable(slot.value);
		} catch (RuntimeException | Error failure) {
			discard(slot, failure);
			throw failure;
		}

		if (owned.get(slot.value) == slot && slot.state == State.STOPPING) {
			if (!reusable) {
				// 未知类型已按 force 参数停止；交宿主自然排空，不额外强制清除可见残留。
				owned.remove(slot.value);
				slot.state = State.RETIRED;
				retired++;
				return;
			}

			slot.state = State.DRAINING;
			pending.add(slot);
		}
	}

	/// frame 必须是已完成的渲染帧标号；禁止用开始渲染的帧号代替完成屏障。
	public void pulse(long hostTick, long completedFrame) {
		if (hostTick < tick || completedFrame < frame) {
			throw new IllegalArgumentException("pool clock moved backwards");
		}

		tick = hostTick;
		frame = completedFrame;
		for (var slot : new ArrayList<>(pending)) {
			if (slot.state == State.DRAINING) {
				slot.state = State.CHECKING;
				boolean drained;
				try {
					drained = lifecycle.drained(slot.value);
				} catch (RuntimeException | Error failure) {
					discard(slot, failure);
					throw failure;
				}

				if (owned.get(slot.value) != slot || slot.state != State.CHECKING) {
					continue;
				}

				slot.state = drained ? State.QUARANTINE : State.DRAINING;
				if (drained) {
					slot.tick = tick;
					slot.frame = frame;
				}
			} else if (slot.state == State.QUARANTINE && tick > slot.tick && frame > slot.frame) {
				park(slot);
			}
		}
	}

	private void park(Slot<T> slot) {
		long generation = epoch;
		slot.state = State.PARKING;
		try {
			lifecycle.parked(slot.value);
		} catch (RuntimeException | Error failure) {
			discard(slot, failure);
			throw failure;
		}

		if (generation != epoch || owned.get(slot.value) != slot || slot.state != State.PARKING) {
			return;
		}

		var available = idle.computeIfAbsent(slot.key, _ -> new ArrayDeque<>());
		if (available.size() >= peaks.getOrDefault(slot.key, 0)) {
			discard(slot, null);
			return;
		}

		slot.state = State.IDLE;
		slot.lease = null;
		pending.remove(slot);
		available.addLast(slot);
	}

	public void clear() {
		if (clearing) {
			return;
		}

		clearing = true;
		epoch++;
		var previous = new ArrayList<>(owned.values());
		owned.clear();
		idle.clear();
		pending.clear();
		active.clear();
		peaks.clear();
		for (var slot : previous) {
			slot.state = State.RETIRED;
		}

		Throwable failure = null;
		try {
			for (var slot : previous) {
				retired++;
				try {
					lifecycle.dispose(slot.value);
				} catch (RuntimeException | Error problem) {
					if (failure == null) {
						failure = problem;
					} else if (failure != problem) {
						failure.addSuppressed(problem);
					}
				}
			}
		} finally {
			clearing = false;
		}

		if (failure instanceof RuntimeException exception) {
			throw exception;
		}
		if (failure instanceof Error error) {
			throw error;
		}
	}

	public Statistics statistics() {
		int idle = 0;
		int leased = 0;
		int quarantine = 0;
		for (var slot : owned.values()) {
			if (slot.state == State.IDLE) {
				idle++;
			} else if (slot.state == State.ACTIVE) {
				leased++;
			} else {
				quarantine++;
			}
		}

		return new Statistics(hits, misses, resets, created, idle, retired, leased, quarantine);
	}

	private void discard(Slot<T> slot, Throwable original) {
		if (owned.get(slot.value) != slot) {
			return;
		}

		owned.remove(slot.value);
		pending.remove(slot);
		var available = idle.get(slot.key);
		if (available != null) {
			available.remove(slot);
		}

		slot.state = State.RETIRED;
		retired++;
		try {
			lifecycle.dispose(slot.value);
		} catch (RuntimeException | Error failure) {
			if (original == null) {
				throw failure;
			}
			if (original != failure) {
				original.addSuppressed(failure);
			}
		}
	}

	public interface Lifecycle<T> {
		void stop(T value, boolean force);

		boolean drained(T value);

		default boolean reusable(T value) {
			return true;
		}

		/// 仅在排空且跨过真实 tick 与已完成帧屏障后调用，此时尚不可领取。
		default void parked(T value) {
		}

		void dispose(T value);
	}

	public record Statistics(long hit, long miss, long reset, long created, int idle, long retired,
	                         int active, int quarantine) {
	}

	public static class Lease<T> {
		private final PhotonLeasePool<T> owner;
		private final Slot<T> slot;
		private final long id;
		private final long epoch;

		private Lease(PhotonLeasePool<T> owner, Slot<T> slot, long id, long epoch) {
			this.owner = owner;
			this.slot = slot;
			this.id = id;
			this.epoch = epoch;
		}

		public long id() {
			return id;
		}

		public boolean current() {
			return epoch == owner.epoch && slot.state == State.ACTIVE && slot.lease == this
					&& owner.owned.get(slot.value) == slot;
		}

		/// 返回值只在当前租约有效期间借用，调用方不得跨 release 保存后继续操作。
		public T runtime() {
			if (!current()) {
				throw new IllegalStateException("stale runtime lease");
			}

			return slot.value;
		}
	}

	private enum State {
		RESETTING, ACTIVE, STOPPING, CHECKING, DRAINING, QUARANTINE, PARKING, IDLE, RETIRED
	}

	private static class Slot<T> {
		private final Object key;
		private final T value;
		private State state;
		private Lease<T> lease;
		private long tick;
		private long frame;

		private Slot(Object key, T value) {
			this.key = key;
			this.value = value;
		}
	}
}
