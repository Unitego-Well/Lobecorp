package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;

import java.util.Objects;
import java.util.function.Predicate;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// 仅接管具有明确租约的 runtime，配置与 TRS 的完整恢复由调用方提供。
public class PhotonRuntimePool {
	private final Predicate<IFXObject> resetSupported;
	private final Consumer<FXRuntime> idleCleanup;
	private final PhotonLeasePool<FXRuntime> pool = new PhotonLeasePool<>(new PhotonLeasePool.Lifecycle<>() {
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
			value.destroy(true);
		}
	});

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
		this.resetSupported = Objects.requireNonNull(resetSupported);
		this.idleCleanup = Objects.requireNonNull(idleCleanup);
	}

	public Lease acquire(Object key, Supplier<FXRuntime> create, Consumer<FXRuntime> reset) {
		return new Lease(pool.acquire(key, create, reset));
	}

	public void release(Lease lease, boolean force) {
		if (lease != null) {
			pool.release(lease.delegate, force);
		}
	}

	public void pulse(long hostTick, long frame) {
		pool.pulse(hostTick, frame);
	}

	public void clear() {
		pool.clear();
	}

	public PhotonLeasePool.Statistics statistics() {
		return pool.statistics();
	}

	public static class Lease {
		private final PhotonLeasePool.Lease<FXRuntime> delegate;

		private Lease(PhotonLeasePool.Lease<FXRuntime> delegate) {
			this.delegate = delegate;
		}

		public FXRuntime runtime() {
			return delegate.runtime();
		}

		public long id() {
			return delegate.id();
		}

		public boolean current() {
			return delegate.current();
		}
	}
}
