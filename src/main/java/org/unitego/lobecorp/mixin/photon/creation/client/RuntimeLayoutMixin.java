package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.ParticleTickHost;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.creation.IPhotonRuntimeCreationAccess;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Mixin(value = FXRuntime.class)
public abstract class RuntimeLayoutMixin implements IPhotonRuntimeCreationAccess {
	@Shadow
	@Final
	@Mutable
	public FXData fxData;
	@Shadow
	@Final
	public IFXObject root;
	@Shadow
	@Final
	@Mutable
	public Map<UUID, IFXObject> objects;
	@Shadow
	@Final
	@Mutable
	public TimelinePlayer timelinePlayer;
	@Shadow
	private boolean emitted;
	@Shadow
	private boolean destroyed;
	@Shadow
	private ParticleTickHost host;
	@Shadow
	private int generationAtEmit;
	@Shadow
	@Final
	private List<FXInstanceObject> instances;
	@Shadow
	private boolean beginningInstances;
	@Unique
	private Runnable lobecorp$initialTick;
	@Unique
	private Consumer<Float> lobecorp$initialFrame;
	@Unique
	private BooleanSupplier lobecorp$initialKeepAlive;

	@Inject(method = "initRuntime", at = @At("HEAD"))
	private void lobecorp$usePreparedIndex(CallbackInfo result) {
		var indexed = PhotonRuntimeFactory.indexedMap(fxData, objects);
		if (indexed != null) {
			objects = indexed;
		}
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$captureConstructorCallbacks(FXData data, CallbackInfo result) {
		if (root instanceof FXObject object) {
			lobecorp$initialTick = object.getOnUpdateTick();
			lobecorp$initialFrame = object.getOnUpdateFrame();
			lobecorp$initialKeepAlive = object.getKeepAlive();
		}
	}

	@Inject(method = "destroy", at = @At("TAIL"))
	private void lobecorp$retireForcedScope(boolean force, CallbackInfo result) {
		if (force) {
			PhotonRuntimeFactory.onRuntimeForceDestroyed((FXRuntime) (Object) this);
		}
	}

	@Override
	public void lobecorp$resetCreationState(FXData data) {
		timelinePlayer.stop();
		instances.forEach(FXInstanceObject::stopAudio);
		instances.clear();
		beginningInstances = false;
		fxData = data;
		timelinePlayer = new TimelinePlayer((FXRuntime) (Object) this, data.timeline());
		emitted = false;
		destroyed = false;
		host = null;
		generationAtEmit = 0;
	}

	@Override
	public void lobecorp$restoreCreationCallbacks() {
		if (root instanceof FXObject object) {
			object.setOnUpdateTick(lobecorp$initialTick);
			object.setOnUpdateFrame(lobecorp$initialFrame);
			object.setKeepAlive(lobecorp$initialKeepAlive);
		}
	}
}
