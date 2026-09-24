package org.unitego.lobecorp.client.particle.photon;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PhotonGeoAnchorExecutor extends FXEffectExecutor {
	private PhotonAnimationEffectHandle handle;
	private PhotonAnchorTransform anchor;
	private final Entity entity;

	PhotonGeoAnchorExecutor(FX fx, Level level, Entity entity) {
		super(fx, level);
		this.entity = entity;
	}

	@Override
	public void start() {
		if (anchor == null || handle == null || runtime != null) {
			return;
		}
		resetFinishedNotification();
		runtime = fx.createRuntime();
		applyTransform();
		runtime.emit(this);
		handle.applySpeed();
	}

	@Override
	public void updateFXObjectTick(IFXObject fxObject) {
		if (runtime != null && fxObject == runtime.getRoot() && !entity.isAlive()) {
			destroy(true);
			handle.markEnded();
			notifyFinished();
		} else if (runtime != null && fxObject == runtime.getRoot() && runtimeEnded()) {
			if (handle != null) {
				handle.markEnded();
			}
			notifyFinished();
		}
	}

	@Override
	public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
		if (runtime == null || fxObject != runtime.getRoot() || anchor == null || handle == null) {
			return;
		}
		applyTransform();
	}

	private void applyTransform() {
		Vector3f position = new Vector3f(handle.offset()).mul(anchor.scale());
		anchor.rotation().transform(position);
		position.add(anchor.position());
		Quaternionf rotation = new Quaternionf(anchor.rotation()).mul(handle.rotation());
		Vector3f scale = new Vector3f(anchor.scale()).mul(handle.scale());
		runtime.getRoot().updatePos(position);
		runtime.getRoot().updateRotation(rotation);
		runtime.getRoot().updateScale(scale);
	}

	void setHandle(PhotonAnimationEffectHandle handle) {
		this.handle = handle;
	}

	void updateAnchor(PhotonAnchorTransform anchor) {
		this.anchor = anchor;
		start();
	}

	void destroy(boolean force) {
		if (runtime != null) {
			runtime.destroy(force);
		}
	}
}
