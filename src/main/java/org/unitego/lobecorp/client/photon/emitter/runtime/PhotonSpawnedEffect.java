package org.unitego.lobecorp.client.photon.emitter.runtime;

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
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;
import org.unitego.lobecorp.util.photon.emitter.PhotonEmitterSpawnParameterUtil;
import org.unitego.lobecorp.util.photon.emitter.PhotonEmitterTemplateUtil;

import java.util.UUID;

/**
 * 一个生成实例的所有权、时钟和参数覆盖；粒子仍由 Photon 的管理器更新。
 */
public class PhotonSpawnedEffect implements IEffectExecutor {
	private final UUID handle = UUID.randomUUID();
	private final PhotonEmitterSpawner owner;
	private final FXRuntime runtime;
	private final IEffectExecutor delegate;
	private final RandomSource random;
	private final PhotonEmitterSpawnParameterUtil.OverrideBinding parameters;
	private final Vector3f localOffset;
	private final Vector3f origin;
	private final Quaternionf rotation;
	private final Vector3f scale;
	private final Vector3f velocity;
	private final Vector3f travel = new Vector3f();
	private final Vector3f previousTravel = new Vector3f();
	private final boolean follow;
	private final boolean inheritRotation;
	private final boolean inheritScale;
	private final float playbackSpeed;
	private final int lifetime;
	private double age;
	private double previousAge;
	private long timelineTicks;
	private long hostGeneration;
	private boolean expired;

	public PhotonSpawnedEffect(PhotonEmitterSpawner owner, PhotonEmitterTemplateUtil.SpawnTemplate template,
	                           PhotonSpawnOverrides values, Vector3f offset, Vector3f velocity) {
		this.owner = owner;
		runtime = template.runtime();
		delegate = owner.getEffectExecutor();
		random = RandomSource.create(owner.getRandomSource().nextLong());
		parameters = PhotonEmitterSpawnParameterUtil.bind(values);
		localOffset = new Vector3f(offset);
		origin = owner.transform().localToWorldMatrix().transformPosition(new Vector3f(offset));
		inheritRotation = owner.config.inheritRotation;
		inheritScale = owner.config.inheritScale;
		rotation = inheritRotation ? new Quaternionf(owner.transform().rotation()) : new Quaternionf();
		scale = inheritScale ? new Vector3f(owner.transform().scale()) : new Vector3f(1);
		this.velocity = new Vector3f(velocity);
		follow = owner.config.follow;
		playbackSpeed = Math.clamp(values.playbackSpeed(), 0, PhotonEmitterSpawnConfig.MAX_PLAYBACK_SPEED);
		lifetime = Math.max(0, owner.config.instanceLifetime);
		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonEmitterSpawner spawner) {
				spawner.bindSourceScope(template.scope(), template.sourcePath());
			}
		}
	}

	public UUID handle() {
		return handle;
	}

	public void start() {
		syncTransform(0);
		if (runtime.root instanceof FXObject root) {
			root.setOnUpdateTick(this::tickRoot);
			root.setOnUpdateFrame(this::frameRoot);
		}

		runtime.emit(this);
		if (runtime.root instanceof FXObject root && root.getTickHost() != null) {
			hostGeneration = root.getTickHost().generation();
		}

		runtime.objects.values().forEach(parameters::apply);
		if (owner.config.inheritColor) {
			for (var object : runtime.objects.values()) {
				if (object instanceof IParticleEmitter emitter) {
					emitter.setRGBAColor(owner.getRGBAColor());
				}
			}
		}
		syncState();
	}

	private void tickRoot() {
		syncState();
		if (!owner.isActive() && owner.getScene() instanceof FXRuntime source && source.isFinished()) {
			stop(true);
			return;
		}

		if (owner.isPaused() || !owner.isActive() || runtime.isDestroyed()) {
			return;
		}

		previousTravel.set(travel);
		var speed = effectiveSpeed();
		travel.fma(speed, velocity);
		previousAge = age;
		age += speed;
		while (timelineTicks < Math.floor(age)) {
			runtime.getTimelinePlayer().tick();
			timelineTicks++;
		}
		syncTransform(1);
		if (!expired && lifetime > 0 && age >= lifetime) {
			expired = true;
			stop(false);
		}
	}

	private void frameRoot(float partialTicks) {
		syncState();
		if (owner.isActive() && !runtime.isDestroyed()) {
			var interpolation = owner.isPaused() ? 0 : Math.clamp(partialTicks, 0, 1);
			var time = previousAge + (age - previousAge) * interpolation;
			var lastEvaluation = Math.max(0, timelineTicks - 1);
			runtime.getTimelinePlayer().frame((float) (time - lastEvaluation));
		}
		syncTransform(owner.isPaused() ? 1 : partialTicks);
	}

	public void syncState() {
		runtime.root.setSelfActive(owner.isActive());
		runtime.root.setSelfVisible(owner.isVisible());
		runtime.root.setSelfTimeScale(effectiveSpeed());
	}

	private float effectiveSpeed() {
		if (owner.isPaused()) {
			return 0;
		}

		var speed = playbackSpeed * owner.timeScale();
		return Float.isFinite(speed) ? Math.clamp(speed, 0, PhotonEmitterSpawnConfig.MAX_PLAYBACK_SPEED) : 0;
	}

	private void syncTransform(float partialTicks) {
		var position = follow
				? owner.transform().localToWorldMatrix().transformPosition(new Vector3f(localOffset))
				: new Vector3f(origin);
		position.add(new Vector3f(previousTravel).lerp(travel, Math.clamp(partialTicks, 0, 1)));
		runtime.root.updatePos(position);
		runtime.root.updateRotation(follow && inheritRotation ? owner.transform().rotation() : rotation);
		runtime.root.updateScale(follow && inheritScale ? owner.transform().scale() : scale);
	}

	public void stop(boolean force) {
		runtime.destroy(force);
	}

	public boolean finished() {
		if (runtime.root instanceof FXObject root && root.getTickHost() != null
				&& root.getTickHost().generation() != hostGeneration) {
			stop(true);
			return true;
		}

		return runtime.isFinished() || !runtime.isDestroyed() && !runtime.isValid();
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
		delegate.updateFXObjectTick(object);
		parameters.apply(object);
	}

	@Override
	public void updateFXObjectFrame(IFXObject object, float partialTicks) {
		delegate.updateFXObjectFrame(object, partialTicks);
		parameters.apply(object);
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
