package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;

import javax.annotation.Nullable;

/// 此入口在 setup 成功后逐项发出一个 OpenGL draw，避免把跳过的 RenderPass 请求计为绘制。
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {
    @WrapMethod(method = "drawFromBuffers")
    private void lobecorp$observeDrawDispatch(@Coerce Object renderPass, int baseVertex, int firstIndex,
                                              int drawCount, @Nullable VertexFormat.IndexType indexType,
                                              @Coerce Object pipeline, int instanceCount, Operation<Void> original) {
        original.call(renderPass, baseVertex, firstIndex, drawCount, indexType, pipeline, instanceCount);
        PhotonSubmissionDiagnostics.drawDispatched(indexType != null, drawCount, instanceCount);
    }
}
