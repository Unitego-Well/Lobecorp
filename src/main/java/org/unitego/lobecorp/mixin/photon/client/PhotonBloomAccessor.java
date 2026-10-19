package org.unitego.lobecorp.mixin.photon.client;

import com.lowdragmc.photon.client.render.PhotonBloom;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PhotonBloom.class)
public interface PhotonBloomAccessor {
	@Invoker("copyPipeline")
	static RenderPipeline lobecorp$copyPipeline() {
		throw new AssertionError();
	}

	@Invoker("brightPipeline")
	static RenderPipeline lobecorp$brightPipeline(float scale) {
		throw new AssertionError();
	}

	@Invoker("blitPipeline")
	static RenderPipeline lobecorp$blitPipeline(float scale) {
		throw new AssertionError();
	}

	@Invoker("downPipeline")
	static RenderPipeline lobecorp$downPipeline() {
		throw new AssertionError();
	}

	@Invoker("upPipeline")
	static RenderPipeline lobecorp$upPipeline() {
		throw new AssertionError();
	}
}
