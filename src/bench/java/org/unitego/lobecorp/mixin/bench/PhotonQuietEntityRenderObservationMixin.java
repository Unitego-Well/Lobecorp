package org.unitego.lobecorp.mixin.bench;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonQuietMatrixCapture;
import org.unitego.lobecorp.client.bench.PhotonRunMatrixCapture;

/// 仅 Quiet 或 Run 场景装载；记录目标实体实际提交次数，不改变提交内容。
@Mixin(value = EntityRenderDispatcher.class, remap = false)
public class PhotonQuietEntityRenderObservationMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void lobecorp$quietEntitySubmit(EntityRenderState renderState, CameraRenderState camera,
                                            double x, double y, double z, PoseStack poseStack,
                                            SubmitNodeCollector submitNodeCollector, CallbackInfo callback) {
        if (PhotonRunMatrixCapture.Variant.selected() != null) {
            PhotonRunMatrixCapture.observeEntitySubmit(renderState);
        } else {
            PhotonQuietMatrixCapture.observeEntitySubmit(renderState);
        }
    }
}
