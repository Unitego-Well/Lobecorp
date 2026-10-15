package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.render.PhotonBufferCache;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionDrawUtil;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionUtil;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.render.PhotonViewDepthSortUtil;

import java.util.Comparator;
import java.util.List;

@Mixin(PhotonWorldRenderState.class)
public abstract class PhotonWorldRenderStateMixin {
	/// drain 重载与预写阶段的分配序号固定对应 Photon 26.1.2.2。
	@Unique
	private static final String LOBECORP_DRAIN = "drain(Ljava/util/List;ZLcom/lowdragmc/photon/client/postfx/runtime/PostEffectStack;ZZZ)V";

	@Inject(method = LOBECORP_DRAIN, at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 1))
	private static void lobecorp$opaqueDepth(List<DrawJob> jobs, boolean bloomEnabled, PostEffectStack stack,
	                                         boolean lastStage, boolean layerMode, boolean opaqueDepth, CallbackInfo ci,
	                                         @Local(name = "cache") PhotonBufferCache cache,
	                                         @Local(name = "colorTexture") GpuTextureView colorTexture,
	                                         @Local(name = "depthTexture") GpuTextureView depthTexture) {
		PhotonOcclusionDrawUtil.draw(jobs, cache, colorTexture, depthTexture);
	}

	@WrapOperation(method = "lambda$drawRun$0", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/render/PhotonMaterialUniforms;sliceFor(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private static GpuBufferSlice lobecorp$sourceMaterial(RenderType renderType, Operation<GpuBufferSlice> original) {
		return original.call(PhotonOcclusionUtil.source(renderType));
	}

	@Inject(method = "endFrame", at = @At("TAIL"))
	private static void lobecorp$clearOcclusionFrame(CallbackInfo ci) {
		PhotonOcclusionUtil.clearFrame();
	}

	@WrapOperation(method = LOBECORP_DRAIN,
			at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
	private static void lobecorp$sortViewDepth(List<DrawJob> jobs, Comparator<DrawJob> c, Operation<Void> original) {
		original.call(jobs, c);
		PhotonViewDepthSortUtil.reorder(jobs);
	}
}
