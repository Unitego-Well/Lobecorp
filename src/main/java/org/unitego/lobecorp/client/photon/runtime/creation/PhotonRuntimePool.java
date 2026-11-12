package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// 仅接管具有明确租约的 runtime，配置与 TRS 的完整恢复由调用方提供。
public class PhotonRuntimePool {
	private final Predicate<IFXObject> resetSupported;
	private final Consumer<FXRuntime> idleCleanup;
	private final Consumer<FXRuntime> disposedCleanup;
	// 每个单槽池沿用原排空/fence 状态机；同键容量仍由组内真实活跃峰值决定。
	private final Map<Object, Group> groups = new HashMap<>();
	private final Set<FXRuntime> owned = Collections.newSetFromMap(new IdentityHashMap<>());
	private final PhotonLeasePool.Lifecycle<FXRuntime> lifecycle = new PhotonLeasePool.Lifecycle<>() {
		@Override
		public void stop(FXRuntime value, boolean force) {
			value.destroy(force);
			if (force) {
				for (var object : value.objects.values()) {
					if (object instanceof ParticleEmitter emitter) {
						emitter.waitToAdded.clear();
					}
				}
			}
		}

		@Override
		public boolean drained(FXRuntime value) {
			if (!value.isFinished()) {
				return false;
			}

			for (var object : value.objects.values()) {
				if (object == null || object.isAlive()
						|| object instanceof IParticleEmitter emitter && emitter.getParticleAmount() != 0) {
					return false;
				}
			}

			return true;
		}

		@Override
		public boolean reusable(FXRuntime value) {
			return value.objects.values().stream().allMatch(object -> object != null && resetSupported.test(object));
		}

		@Override
		public void parked(FXRuntime value) {
			idleCleanup.accept(value);
		}

		@Override
		public void dispose(FXRuntime value) {
			owned.remove(value);
			try {
				value.destroy(true);
			} finally {
				disposedCleanup.accept(value);
			}
		}
	};
	private PhotonLeasePool.Statistics history = new PhotonLeasePool.Statistics(0, 0, 0, 0, 0, 0, 0, 0);
	private long nextId;
	private long tick;
	private long frame;
	private boolean clearing;

	public PhotonRuntimePool() {
		this(object -> object.getClass() == EmptyFXObject.class || object.getClass() == ParticleEmitter.class);
	}

	/// 主线仅为已具备完整 blueprint reset 的精确类型返回 true，包含原生 root。
	public PhotonRuntimePool(Predicate<IFXObject> resetSupported) {
		this(resetSupported, _ -> {
		});
	}

	public PhotonRuntimePool(Consumer<FXRuntime> idleCleanup) {
		this(object -> object.getClass() == EmptyFXObject.class || object.getClass() == ParticleEmitter.class,
				idleCleanup);
	}

	public PhotonRuntimePool(Predicate<IFXObject> resetSupported, Consumer<FXRuntime> idleCleanup) {
		this(resetSupported, idleCleanup, _ -> {
		});
	}

	public PhotonRuntimePool(Predicate<IFXObject> resetSupported, Consumer<FXRuntime> idleCleanup,
	                         Consumer<FXRuntime> disposedCleanup) {
		this.resetSupported = Objects.requireNonNull(resetSupported);
		this.idleCleanup = Objects.requireNonNull(idleCleanup);
		this.disposedCleanup = Objects.requireNonNull(disposedCleanup);
	}

	private static PhotonLeasePool.Statistics sum(PhotonLeasePool.Statistics left, PhotonLeasePool.Statistics right) {
		return new PhotonLeasePool.Statistics(left.hit() + right.hit(), left.miss() + right.miss(),
				left.reset() + right.reset(), left.created() + right.created(), left.idle() + right.idle(),
				left.retired() + right.retired(), left.active() + right.active(), left.quarantine() + right.quarantine());
	}

	public Lease acquire(Object key, Supplier<FXRuntime> create, Consumer<FXRuntime> reset) {
		Objects.requireNonNull(create);
		Objects.requireNonNull(reset);
		if (clearing) {
			throw new IllegalStateException("特效池正在清理。");
		}

		var group = groups.computeIfAbsent(key, _ -> new Group());
		if (group.invalid) {
			throw new IllegalStateException("特效池定义已失效。");
		}

		var lane = group.lanes.stream().filter(value -> value.statistics().idle() != 0).findFirst()
				.orElseGet(() -> {
					var value = new PhotonLeasePool<>(lifecycle);
					group.lanes.add(value);
					return value;
				});
		var delegate = lane.acquire(key, () -> {
			var value = Objects.requireNonNull(create.get(), "runtime supplier returned null");
			if (!owned.add(value)) {
				throw new IllegalArgumentException("runtime already owned by pool");
			}

			return value;
		}, reset);
		group.peak = Math.max(group.peak, group.lanes.stream().mapToInt(value -> value.statistics().active()).sum());
		return new Lease(this, lane, delegate, ++nextId);
	}

	public void release(Lease lease, boolean force) {
		if (lease != null && lease.owner == this) {
			lease.pool.release(lease.delegate, force);
		}
	}

	/// 只立即销毁已经跨过屏障的闲置实例；活跃及排空中的实例继续原生命周期。
	public void invalidate(Predicate<Object> keys) {
		for (var entry : new ArrayList<>(groups.entrySet())) {
			if (keys.test(entry.getKey())) {
				entry.getValue().invalid = true;
				trim(entry.getKey(), entry.getValue());
			}
		}
	}

	public void pulse(long hostTick, long completedFrame) {
		if (hostTick < tick || completedFrame < frame) {
			throw new IllegalArgumentException("pool clock moved backwards");
		}

		tick = hostTick;
		frame = completedFrame;
		for (var entry : new ArrayList<>(groups.entrySet())) {
			var group = entry.getValue();
			for (var lane : new ArrayList<>(group.lanes)) {
				lane.pulse(hostTick, completedFrame);
			}

			trim(entry.getKey(), group);
		}
	}

	private void trim(Object key, Group group) {
		int kept = 0;
		for (var lane : new ArrayList<>(group.lanes)) {
			var stats = lane.statistics();
			if (stats.active() != 0 || stats.quarantine() != 0) {
				continue;
			}

			if (stats.idle() != 0 && !group.invalid && kept++ < group.peak) {
				continue;
			}

			if (group.lanes.remove(lane)) {
				try {
					lane.clear();
				} finally {
					history = sum(history, lane.statistics());
				}
			}
		}

		if (group.lanes.isEmpty()) {
			groups.remove(key, group);
		}
	}

	public void clear() {
		if (clearing) {
			return;
		}

		clearing = true;
		var previous = new ArrayList<>(groups.values());
		groups.clear();
		Throwable failure = null;
		try {
			for (var group : previous) {
				group.invalid = true;
				var lanes = new ArrayList<>(group.lanes);
				group.lanes.clear();
				for (var lane : lanes) {
					try {
						lane.clear();
					} catch (RuntimeException | Error problem) {
						if (failure == null) {
							failure = problem;
						} else if (failure != problem) {
							failure.addSuppressed(problem);
						}
					} finally {
						history = sum(history, lane.statistics());
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

	public PhotonLeasePool.Statistics statistics() {
		var result = history;
		for (var group : groups.values()) {
			for (var lane : group.lanes) {
				result = sum(result, lane.statistics());
			}
		}
		return result;
	}

	private static class Group {
		private final List<PhotonLeasePool<FXRuntime>> lanes = new ArrayList<>();
		private int peak;
		private boolean invalid;
	}

	public static class Lease {
		private final PhotonRuntimePool owner;
		private final PhotonLeasePool<FXRuntime> pool;
		private final PhotonLeasePool.Lease<FXRuntime> delegate;
		private final long id;

		private Lease(PhotonRuntimePool owner, PhotonLeasePool<FXRuntime> pool,
		              PhotonLeasePool.Lease<FXRuntime> delegate, long id) {
			this.owner = owner;
			this.pool = pool;
			this.delegate = delegate;
			this.id = id;
		}

		public FXRuntime runtime() {
			return delegate.runtime();
		}

		public long id() {
			return id;
		}

		public boolean current() {
			return delegate.current();
		}
	}
}
