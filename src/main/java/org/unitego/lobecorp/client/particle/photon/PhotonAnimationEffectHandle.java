package org.unitego.lobecorp.client.particle.photon;

import com.lowdragmc.photon.client.fx.FXRuntime;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PhotonAnimationEffectHandle {
	private final PhotonGeoAnchorExecutor executor;
	private final Vector3f offset = new Vector3f();
	private final Quaternionf rotation = new Quaternionf();
	private final Vector3f scale = new Vector3f(1.0F);
	private float speed;
	private boolean paused;
	private boolean ended;

	PhotonAnimationEffectHandle(PhotonGeoAnchorExecutor executor, double speed) {
		this.executor = executor;
		this.speed = (float)speed;
		applySpeed();
	}

	public void pause() {
		if (!ended) {
			paused = true;
			applySpeed();
		}
	}

	public void resume() {
		if (!ended) {
			paused = false;
			applySpeed();
		}
	}

	public void stop() {
		stop(false);
	}

	public void stop(boolean force) {
		if (!ended) {
			ended = true;
			executor.destroy(force);
		}
	}

	public void setSpeed(double speed) {
		if (speed < 0.0) {
			throw new IllegalArgumentException("Effect speed cannot be negative");
		}
		this.speed = (float)speed;
		applySpeed();
	}

	public void setOffset(float x, float y, float z) {
		offset.set(x, y, z);
	}

	public void setRotation(Quaternionf rotation) {
		this.rotation.set(rotation);
	}

	public void setScale(float x, float y, float z) {
		scale.set(x, y, z);
	}

	public boolean isAlive() {
		FXRuntime runtime = executor.getRuntime();
		return !ended && (runtime == null || runtime.isValid());
	}

	Vector3f offset() {
		return offset;
	}

	Quaternionf rotation() {
		return rotation;
	}

	Vector3f scale() {
		return scale;
	}

	void markEnded() {
		ended = true;
	}

	void applySpeed() {
		FXRuntime runtime = executor.getRuntime();
		if (runtime != null) {
			runtime.getRoot().setSelfTimeScale(paused ? 0.0F : speed);
		}
	}
}
