package org.unitego.lobecorp.client.photon.animation;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;

/// 原生可保存的无粒子对象；运行状态不进入持久化数据。
public class PhotonAnimationController extends EmptyFXObject {
	@Persisted
	public CompoundTag definition = new CompoundTag();
	private @Nullable PhotonAnimationRuntime playback;

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonAnimationTypes.CONTROLLER;
	}

	public PhotonAnimationLibrary library() {
		return PhotonAnimationLibrary.read(Platform.getFrozenRegistry(), definition);
	}

	public void library(PhotonAnimationLibrary library) {
		definition = library.write(Platform.getFrozenRegistry());
	}

	public void attach(@Nullable PhotonAnimationRuntime runtime) {
		playback = runtime;
	}

	@Override
	public boolean isAlive() {
		return !removed && playback != null && !playback.closed() || super.isAlive();
	}

	@Override
	public boolean isPlaying() {
		return !removed && playback != null && !playback.closed() || super.isPlaying();
	}

	@Override
	public PhotonAnimationController shallowCopy() {
		var copy = new PhotonAnimationController();
		copy.definition = definition.copy();
		return copy;
	}

	@Override
	public void remove(boolean force) {
		if (playback != null) {
			playback.close();
		}

		super.remove(force);
	}
}
