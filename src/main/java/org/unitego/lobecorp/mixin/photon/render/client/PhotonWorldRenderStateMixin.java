package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.render.PhotonViewDepthSortUtil;

import java.util.Comparator;
import java.util.List;

@Mixin(PhotonWorldRenderState.class)
public abstract class PhotonWorldRenderStateMixin {
	@WrapOperation(method = "drain(Ljava/util/List;ZLcom/lowdragmc/photon/client/postfx/runtime/PostEffectStack;ZZZ)V",
			at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
	private static void lobecorp$sortViewDepth(List<DrawJob> jobs, Comparator<DrawJob> c, Operation<Void> original) {
		original.call(jobs, c);
		PhotonViewDepthSortUtil.reorder(jobs);
	}
}
