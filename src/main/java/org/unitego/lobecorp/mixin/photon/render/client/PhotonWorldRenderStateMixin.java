package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonBufferCache;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionDrawUtil;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionUtil;
import org.unitego.lobecorp.util.photon.render.PhotonViewDepthSortUtil;

import java.util.Comparator;
import java.util.List;

@Mixin(PhotonWorldRenderState.class)
public abstract class PhotonWorldRenderStateMixin {
	/// 当前原生六参数drain在HDR目标就绪后建立第二个列表，遮挡预写沿用同一目标与原生上传缓存。
	@Unique
	private static final String LOBECORP_DRAIN = "drain(Ljava/util/List;ZLcom/lowdragmc/photon/client/postfx/runtime/PostEffectStack;ZZZ)V";

	@Inject(method = LOBECORP_DRAIN, at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 1))
	private static void lobecorp$writeOcclusion(List<DrawJob> jobs, boolean bloom, PostEffectStack stack,
	                                            boolean last, boolean layer, boolean opaqueDepth, CallbackInfo ci,
	                                            @Local(name = "cache") PhotonBufferCache cache,
	                                            @Local(name = "colorTexture") GpuTextureView color,
	                                            @Local(name = "depthTexture") GpuTextureView depth) {
		PhotonOcclusionDrawUtil.draw(jobs, cache, color, depth);
	}

	@WrapOperation(method = "lambda$drawRun$0", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/render/PhotonMaterialUniforms;sliceFor(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private static GpuBufferSlice lobecorp$sourceMaterial(RenderType type, Operation<GpuBufferSlice> original) {
		return original.call(PhotonOcclusionUtil.source(type));
	}

	@WrapOperation(method = LOBECORP_DRAIN, at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
	private static void lobecorp$sortByViewDepth(List<DrawJob> jobs, Comparator<DrawJob> comparator, Operation<Void> original) {
		original.call(jobs, comparator);
		PhotonViewDepthSortUtil.reorder(jobs);
	}

	@Inject(method = "endFrame", at = @At("TAIL"))
	private static void lobecorp$clearFrame(CallbackInfo ci) {
		PhotonOcclusionUtil.clearFrame();
		PhotonViewDepthSortUtil.clearFrame();
	}
}
