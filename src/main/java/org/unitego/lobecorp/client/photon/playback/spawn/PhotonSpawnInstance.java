package org.unitego.lobecorp.client.photon.playback.spawn;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.playback.PhotonSpawnTemplateUtil;

import javax.annotation.Nullable;
import java.util.UUID;

import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

/// 独占子FX及随机数；模拟和渲染仍由原生粒子宿主驱动，没有第二次手动tick。
public class PhotonSpawnInstance implements IEffectExecutor {
	private final UUID id = UUID.randomUUID();
	private final PhotonChildSpawner owner;
	private final FXRuntime runtime;
	private final @Nullable PhotonRuntimeFactory.Lease lease;
	private boolean disposed;
	private final IEffectExecutor delegate;
	private RandomSource random;
	private final PhotonSpawnOverrides overrides;
	private final Vector3f offset;
	private final Vector3f origin;
	private final Quaternionf rotation;
	private final Vector3f scale;
	private final Vector3f velocity;
	private final Vector3f travel = new Vector3f();
	private final Vector3f previousTravel = new Vector3f();
	private final boolean follow;
	private final boolean inheritRotation;
	private final boolean inheritScale;
	private final float speed;
	private final int lifetime;
	private double age;
	private boolean started;
	private long generation;

	public PhotonSpawnInstance(PhotonChildSpawner owner, PhotonSpawnTemplateUtil.Template template) {
		this.owner = owner;
		runtime = template.runtime();
		lease = template.lease();
		RandomSource createdRandom;
		try {
			delegate = owner.getEffectExecutor();

			if (delegate == null) {
				throw new IllegalStateException("子发射缺少执行宿主。");
			}

			overrides = new PhotonSpawnOverrides(owner);
			offset = sampleShape();
			origin = owner.transform().localToWorldMatrix().transformPosition(new Vector3f(offset));
			inheritRotation = owner.config.inheritRotation;
			inheritScale = owner.config.inheritScale;
			rotation = inheritRotation ? new Quaternionf(owner.transform().rotation()) : new Quaternionf();
			scale = inheritScale ? new Vector3f(owner.transform().scale()) : new Vector3f(1);
			follow = owner.config.follow;
			speed = Math.clamp(((Number) owner.slot("parameters.playbackSpeed").get()).floatValue(), 0, PhotonSpawnConfig.MAX_PLAYBACK_SPEED);
			lifetime = Math.max(0, owner.config.instanceLifetime);
			velocity = new Vector3f(offset).sub(owner.config.shape.offset);

			if (velocity.lengthSquared() == 0) {
				velocity.set(0, 0, 1);
			}

			velocity.normalize();

			if (inheritRotation) {
				owner.transform().rotation().transform(velocity);
			}

			float launch = owner.function("launchSpeed").get(owner.getRandomSource(), owner.getT()).floatValue();

			if (!Float.isFinite(launch) || !Float.isFinite(speed)) {
				throw new IllegalArgumentException("子实例播放或发射速度无效。");
			}

			velocity.mul(launch);

			if (owner.config.inheritVelocity) {
				velocity.add(owner.getVelocity());
			}

			createdRandom = RandomSource.create(owner.getRandomSource().nextLong());
		} catch (RuntimeException invalid) {
			template.close(true);
			throw invalid;
		}
		random = createdRandom;
	}

	private Vector3f sampleShape() {
		var shape = owner.config.shape;
		var source = owner.getRandomSource();

		if (!shape.size.isFinite() || !shape.offset.isFinite() || shape.size.x < 0 || shape.size.y < 0 || shape.size.z < 0) {
			throw new IllegalArgumentException("子发射形状无效。");
		}

		var value = new Vector3f();

		switch (shape.type) {
			case POINT -> {
			}
			case BOX ->
					value.set(source.nextFloat() - 0.5f, source.nextFloat() - 0.5f, source.nextFloat() - 0.5f).mul(shape.size);
			case SPHERE -> {
				double theta = source.nextDouble() * Math.TAU;
				double z = source.nextDouble() * 2 - 1;
				double radius = Math.cbrt(source.nextDouble());
				double xy = Math.sqrt(1 - z * z);
				value.set((float) (Math.cos(theta) * xy * radius), (float) (z * radius),
						(float) (Math.sin(theta) * xy * radius)).mul(shape.size);
			}
			case CIRCLE -> {
				double theta = source.nextDouble() * Math.TAU;
				double radius = Math.sqrt(source.nextDouble());
				value.set((float) (Math.cos(theta) * radius), 0, (float) (Math.sin(theta) * radius)).mul(shape.size);
			}
		}

		return value.add(shape.offset);
	}

	public UUID id() {
		return id;
	}

	public void start() {
		if (disposed || lease != null && !lease.current()) {
			throw new IllegalStateException("子实例租约已失效。");
		}
		var root = (FXObject) runtime.root;
		transform(0);
		runtime.emit(this);
		started = true;
		generation = root.getTickHost() == null ? 0 : root.getTickHost().generation();
		var tick = root.getOnUpdateTick();
		var frame = root.getOnUpdateFrame();
		root.setOnUpdateTick(() -> {
			sync();

			if (tick != null) {
				tick.run();
			}

			if (!runtime.isDestroyed()) {
				previousTravel.set(travel);
				float rate = runtime.getRate();
				travel.fma(rate, velocity);
				age += rate;
				transform(1);

				if (lifetime > 0 && age >= lifetime) {
					stop(false);
				}
			}
		});
		root.setOnUpdateFrame(partial -> {
			sync();
			transform(owner.paused() ? 1 : partial);

			if (frame != null) {
				frame.accept(partial);
			}
		});
		runtime.objects.values().forEach(overrides::apply);

		if (owner.config.inheritColor) {
			for (var object : runtime.objects.values()) {
				if (object instanceof IParticleEmitter emitter) {
					emitter.setRGBAColor(owner.getRGBAColor());
				}
			}
		}

		sync();
	}

	public void sync() {
		if (disposed || runtime.isDestroyed()) {
			return;
		}

		runtime.root.setSelfActive(owner.isActive());
		runtime.root.setSelfVisible(owner.isVisible());
		float rate = owner.paused() || !owner.isActive() ? 0 : speed * owner.timeScale();

		if (!Float.isFinite(rate)) {
			throw new IllegalArgumentException("子实例继承倍率无效。");
		}

		runtime.setRate(Math.clamp(rate, 0, PhotonSpawnConfig.MAX_PLAYBACK_SPEED));
	}

	private void transform(float partial) {
		var position = follow ? owner.transform().localToWorldMatrix().transformPosition(new Vector3f(offset)) : new Vector3f(origin);
		position.add(new Vector3f(previousTravel).lerp(travel, Math.clamp(partial, 0, 1)));
		runtime.root.updatePos(position);
		runtime.root.updateRotation(follow && inheritRotation ? owner.transform().rotation() : rotation);
		runtime.root.updateScale(follow && inheritScale ? owner.transform().scale() : scale);
	}

	public @Nullable PhotonNamedRuntime animations() {
		return disposed || runtime.isDestroyed() ? null : ((PhotonNamedAccess) runtime).lobecorp$namedRuntime();
	}

	public void stop(boolean force) {
		if (!disposed && (lease == null || lease.current())) {
			runtime.destroy(force);
			if (force) {
				release(true);
			}
		}
	}

	public boolean finished() {
		if (disposed) {
			return true;
		}
		if (!started) {
			return false;
		}

		var root = (FXObject) runtime.root;

		if (root.getTickHost() != null && root.getTickHost().generation() != generation) {
			stop(true);
			release(true);
			return true;
		}

		boolean complete = runtime.isFinished() || !runtime.isDestroyed() && !runtime.isValid();
		if (complete) {
			release(false);
		}
		return complete;
	}

	private void release(boolean force) {
		disposed = true;
		if (lease != null) {
			lease.close(force);
		}
	}

	@Override
	public Level getLevel() {
		return delegate.getLevel();
	}

	@Override
	public RandomSource getRandomSource() {
		return random;
	}

	@Override
	public void updateFXObjectTick(IFXObject object) {
		if (object.getEffectExecutor() == this) {
			delegate.updateFXObjectTick(object);
			overrides.apply(object);
		}
	}

	@Override
	public void updateFXObjectFrame(IFXObject object, float partialTicks) {
		if (object.getEffectExecutor() == this) {
			delegate.updateFXObjectFrame(object, partialTicks);
			overrides.apply(object);
		}
	}

	@Override
	public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
		delegate.onTimelineSignal(channel, name, data, time);
	}

	@Override
	public PostEffectStack postEffectSink() {
		return delegate.postEffectSink();
	}
}
