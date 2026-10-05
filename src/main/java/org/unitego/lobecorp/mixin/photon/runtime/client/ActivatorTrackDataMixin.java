package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorTrackAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorOptions;

@Mixin(Track.class)
public abstract class ActivatorTrackDataMixin {
	@Inject(method = "writeExtra", at = @At("RETURN"))
	private void lobecorp$writeActivation(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
		if ((Object) this instanceof ActivatorTrack) {
			var options = ((PhotonActivatorTrackAccess) this).lobecorp$getActivationOptions();
			if (!options.equals(PhotonActivatorOptions.DEFAULT))
				tag.put(PhotonActivatorOptions.TAG, options.write());
		}
	}

	@Inject(method = "readExtra", at = @At("RETURN"))
	private void lobecorp$readActivation(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
		if ((Object) this instanceof ActivatorTrack) {
			((PhotonActivatorTrackAccess) this).lobecorp$setActivationOptions(
					PhotonActivatorOptions.read(tag.getCompoundOrEmpty(PhotonActivatorOptions.TAG)));
		}
	}
}
