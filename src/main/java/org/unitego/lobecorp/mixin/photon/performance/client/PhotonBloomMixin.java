package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.render.PhotonBloom;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformanceGpu;
import org.unitego.lobecorp.client.photon.render.PhotonIrisPipelineAccess;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

@Mixin(PhotonBloom.class)
public class PhotonBloomMixin {
	@WrapMethod(method = "fullscreenPass")
	private static void lobecorp$captureBloomPass(RenderPipeline pipeline, GpuTextureView target,
	                                              GpuTextureView input, GpuBufferSlice params,
	                                              Operation<Void> original) {
		Runnable draw = () -> {
			if (!PhotonPerformanceTraceUtil.enabled(Stage.DRAW)) {
				original.call(pipeline, target, input, params);
				return;
			}
			String pass = "bloom:" + pipeline + "@" + target.getWidth(0) + "x" + target.getHeight(0);
			int node = PhotonPerformanceTraceUtil.namedNode(PhotonPerformanceTraceUtil.currentNode(), "shared_bloom_pass",
					pass, pass, pipeline.toString());
			long scope = PhotonPerformanceTraceUtil.begin(Stage.DRAW, node);
			long gpu = PhotonPerformanceGpu.begin(pass, node, true);
			boolean completed = false;
			try {
				original.call(pipeline, target, input, params);
				completed = true;
			} finally {
				PhotonPerformanceGpu.end(gpu, completed);
				PhotonPerformanceTraceUtil.end(scope, completed);
			}
		};
		if (PhotonIrisPipelineAccess.isPhotonPipeline(pipeline)) {
			PhotonIrisPipelineAccess.drawFullscreen(draw);
		} else {
			draw.run();
		}
	}

	@WrapMethod(method = "run")
	private void lobecorp$captureBloom(GpuTextureView target, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.BLOOM)) {
			original.call(target);
			return;
		}
		String pass = "bloom@" + target.getWidth(0) + "x" + target.getHeight(0);
		int node = PhotonPerformanceTraceUtil.namedNode(0, "shared_bloom", pass, pass, "");
		long scope = PhotonPerformanceTraceUtil.begin(Stage.BLOOM, node);
		long gpu = PhotonPerformanceGpu.begin(pass, node, true);
		boolean completed = false;
		try {
			original.call(target);
			completed = true;
		} finally {
			PhotonPerformanceGpu.end(gpu, completed);
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
