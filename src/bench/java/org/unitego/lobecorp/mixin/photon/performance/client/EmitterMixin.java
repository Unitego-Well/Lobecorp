package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.AdditionalGPUDataSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.PhotonPipelines;
import com.lowdragmc.photon.client.render.PhotonStage;
import com.lowdragmc.photon.client.render.PhotonViewSettings;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.CpuRecords;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.MaskWrite;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(Emitter.class)
public class EmitterMixin {
    @WrapMethod(method = "bakeJob")
    private void lobecorp$observeCpuSubmission(List<DrawJob> out, RenderType renderType, VertexFormat.Mode mode,
                                               Camera camera, float partialTicks, PhotonStage stage, int orderInLayer,
                                               @Nullable Vector3fc sortOrigin, @Coerce Object baker,
                                               @Nullable MaskWrite mask, boolean backFacesFirst,
                                               @Nullable CpuRecords records, Operation<Void> original) {
        var submission = PhotonSubmissionDiagnostics.beginSubmission(false);

        if (submission == null) {
            original.call(out, renderType, mode, camera, partialTicks, stage, orderInLayer, sortOrigin, baker, mask, backFacesFirst, records);
            return;
        }

        int start = out.size();
        boolean completed = false;

        try {
            original.call(out, renderType, mode, camera, partialTicks, stage, orderInLayer, sortOrigin, baker, mask, backFacesFirst, records);
            completed = true;
        } finally {
            PhotonSubmissionDiagnostics.endSubmission(submission, out, start, completed);
        }
    }

    @WrapMethod(method = "bakeInstancedGroup")
    private boolean lobecorp$observeInstancedSubmission(PhotonViewSettings settings, List<DrawJob> out, Camera camera,
                                                        RendererSetting.Runtime renderer, AdditionalGPUDataSetting setting,
                                                        PhotonPipelines.InstancedVariant variant, @Coerce Object mesh,
                                                        int instanceFloatCapacity, int pointFloatCapacity,
                                                        Vector3f positionOffset, @Coerce Object filler,
                                                        Operation<Boolean> original) {
        if (!PhotonSubmissionDiagnostics.enabled()) {
            return original.call(settings, out, camera, renderer, setting, variant, mesh,
                    instanceFloatCapacity, pointFloatCapacity, positionOffset, filler);
        }

        int start = out.size();

        try {
            return original.call(settings, out, camera, renderer, setting, variant, mesh,
                    instanceFloatCapacity, pointFloatCapacity, positionOffset, filler);
        } finally {
            PhotonSubmissionDiagnostics.observeInstancedJobs(out, start, false);
        }
    }
}
