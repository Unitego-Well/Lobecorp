package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;

@Mixin(ByteBufferBuilder.class)
public class ByteBufferBuilderMixin {
    @Shadow
    private long capacity;
    @Shadow
    private long writeOffset;

    @Inject(method = "<init>(IJ)V", at = @At("RETURN"))
    private void lobecorp$observeCreation(int initialCapacity, long maxCapacity, CallbackInfo ci) {
        var submission = PhotonSubmissionDiagnostics.currentSubmission();

        if (submission != null) {
            PhotonSubmissionDiagnostics.bufferCreated(submission, this, capacity);
        }
    }

    @WrapMethod(method = "reserve")
    private long lobecorp$observeReserve(int size, Operation<Long> original) {
        var submission = PhotonSubmissionDiagnostics.currentSubmission();

        if (submission == null) {
            return original.call(size);
        }

        PhotonSubmissionDiagnostics.reserve(submission, this, size, capacity);
        boolean completed = false;

        try {
            long pointer = original.call(size);
            completed = true;
            return pointer;
        } finally {
            PhotonSubmissionDiagnostics.reserved(submission, capacity, writeOffset, completed);
        }
    }

    @WrapMethod(method = "resize")
    private void lobecorp$observeResize(long newCapacity, Operation<Void> original) {
        var submission = PhotonSubmissionDiagnostics.currentSubmission();

        if (submission == null) {
            original.call(newCapacity);
            return;
        }

        long oldCapacity = capacity;
        boolean completed = false;

        try {
            original.call(newCapacity);
            completed = true;
        } finally {
            PhotonSubmissionDiagnostics.resized(submission, this, oldCapacity, capacity, completed);
        }
    }
}
