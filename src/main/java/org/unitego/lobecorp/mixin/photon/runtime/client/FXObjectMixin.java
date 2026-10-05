package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.gameobject.FXObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivationOverrideAccess;

@Mixin(FXObject.class)
public abstract class FXObjectMixin implements PhotonActivationOverrideAccess {
	@Unique
	private boolean lobecorp$ownActivation;

	@Override
	public boolean lobecorp$hasOwnActivation() {
		return lobecorp$ownActivation;
	}

	@Override
	public void lobecorp$setOwnActivation(boolean ownActivation) {
		lobecorp$ownActivation = ownActivation;
	}
}
