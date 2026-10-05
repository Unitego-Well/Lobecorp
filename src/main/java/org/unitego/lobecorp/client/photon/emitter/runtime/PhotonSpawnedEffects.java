package org.unitego.lobecorp.client.photon.emitter.runtime;

import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import org.unitego.lobecorp.util.photon.emitter.PhotonEmitterSpawnParameterUtil;
import org.unitego.lobecorp.util.photon.emitter.PhotonEmitterSpawnShapeUtil;
import org.unitego.lobecorp.util.photon.emitter.PhotonEmitterTemplateUtil;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个发射器独占的生成实例列表；统一负责停止、清理和完成回收。
 */
public class PhotonSpawnedEffects {
	private final PhotonEmitterSpawner owner;
	private final List<PhotonSpawnedEffect> effects = new ArrayList<>();

	public PhotonSpawnedEffects(PhotonEmitterSpawner owner) {
		this.owner = owner;
	}

	public int size() {
		return effects.size();
	}

	public List<UUID> handles() {
		return effects.stream().map(PhotonSpawnedEffect::handle).toList();
	}

	public void prune() {
		effects.removeIf(PhotonSpawnedEffect::finished);
	}

	public boolean spawn(PhotonSpawnOverrides override) {
		PhotonEmitterTemplateUtil.SpawnTemplate template = null;
		try {
			template = PhotonEmitterTemplateUtil.create(owner);
			if (template == null) {
				return false;
			}

			var values = override == null ? PhotonEmitterSpawnParameterUtil.sample(owner) : override;
			var offset = PhotonEmitterSpawnShapeUtil.sample(owner.config.shape, owner.getRandomSource());
			var velocity = velocity(offset);
			var effect = new PhotonSpawnedEffect(owner, template, values, offset, velocity);
			effect.start();
			effects.add(effect);
			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_OK);
			return true;
		} catch (RuntimeException exception) {
			if (template != null) {
				template.runtime().destroy(true);
			}

			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_INVALID);
			return false;
		}
	}

	private Vector3f velocity(Vector3f offset) {
		var direction = new Vector3f(offset).sub(owner.config.shape.offset);
		if (direction.lengthSquared() == 0) {
			direction.set(0, 0, 1);
		}

		direction.normalize();
		if (owner.config.inheritRotation) {
			owner.transform().rotation().transform(direction);
		}

		var speed = owner.runtime().launchSpeed.get().get(owner.getRandomSource(), owner.getT()).floatValue();
		if (!Float.isFinite(speed)) {
			throw new IllegalArgumentException();
		}

		direction.mul(speed);
		if (owner.config.inheritVelocity) {
			direction.add(owner.getVelocity());
		}
		return direction;
	}

	public void stop(boolean force) {
		effects.forEach(effect -> effect.stop(force));
		if (force) {
			effects.clear();
		}
	}

	public boolean stop(UUID handle, boolean force) {
		for (var iterator = effects.iterator(); iterator.hasNext(); ) {
			var effect = iterator.next();
			if (!effect.handle().equals(handle)) {
				continue;
			}

			effect.stop(force);
			if (force) {
				iterator.remove();
			}
			return true;
		}
		return false;
	}

	public void syncState() {
		effects.forEach(PhotonSpawnedEffect::syncState);
	}
}
