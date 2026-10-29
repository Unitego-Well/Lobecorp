package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

@Mixin(Timeline.class)
public abstract class PhotonTimelineDataGuardMixin {
    @Inject(method = "readTrack", at = @At("HEAD"))
    private static void lobecorp$checkCopiedTrack(HolderLookup.Provider provider, CompoundTag entry, CallbackInfoReturnable<Track> cir) {
        if (!PhotonCastProfiler.enabled()) {
            PhotonEditorProjectGuardUtil.requireSupported(entry);
            return;
        }

        var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.GUARD_TRACK);
        try {
            PhotonEditorProjectGuardUtil.requireSupported(entry);
            scope.succeeded();
        } finally {
            PhotonFXLoadDiagnosticsUtil.finish(scope);
        }
    }

    @Inject(method = "deserializeNBT", at = @At("HEAD"))
    private void lobecorp$checkBeforeClearing(HolderLookup.Provider provider, CompoundTag tag, CallbackInfo ci) {
        if (!PhotonCastProfiler.enabled()) {
            PhotonEditorProjectGuardUtil.requireSupported(tag);
            return;
        }

        var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.GUARD_TIMELINE);
        try {
            PhotonEditorProjectGuardUtil.requireSupported(tag);
            scope.succeeded();
        } finally {
            PhotonFXLoadDiagnosticsUtil.finish(scope);
        }
    }
}
