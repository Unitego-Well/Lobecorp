package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationSamplingScope;

/// 复用属性自身的多态 apply，不另建曲线、表达式或函数片段采样器。
@Mixin(AnimationTrack.class)
public class PhotonAnimationTrackSamplingMixin {
	@WrapOperation(method = "sampleInto", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/AnimatedProperty;apply(Lcom/lowdragmc/photon/client/gameobject/FXObject;D)V"))
	private void lobecorp$captureProperty(AnimatedProperty property, FXObject target, double time, Operation<Void> original) {
		PhotonAnimationSamplingScope.apply(property, target, time, () -> original.call(property, target, time));
	}
}
