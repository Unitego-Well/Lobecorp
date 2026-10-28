package org.unitego.lobecorp.client.photon.render;

import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.lowdragmc.photon.client.render.PhotonPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PhotonIrisPipelineAccessTest {
	@Test
	public void restoresPreviousBypassAfterSuccessAndFailure() {
		var value = new AtomicBoolean(false);
		var bridge = new PhotonIrisPipelineAccess.Bypass() {
			@Override
			public boolean bypass() {
				return value.get();
			}

			@Override
			public void bypass(boolean enabled) {
				value.set(enabled);
			}
		};
		PhotonIrisPipelineAccess.withBypass(bridge, () -> assertEquals(true, value.get()));
		assertEquals(false, value.get());

		var enabled = new AtomicBoolean(true);
		var enabledBridge = new PhotonIrisPipelineAccess.Bypass() {
			@Override
			public boolean bypass() {
				return enabled.get();
			}

			@Override
			public void bypass(boolean value) {
				enabled.set(value);
			}
		};
		assertThrows(IllegalStateException.class,
				() -> PhotonIrisPipelineAccess.withBypass(enabledBridge, () -> {
					throw new IllegalStateException("draw failed");
				}));
		assertEquals(true, enabled.get());
	}

	@Test
	public void onlyPhotonOwnedPipelinesBypassIrisOverride() {
		assertEquals(true, PhotonIrisPipelineAccess.isPhotonPipeline(PhotonPipelines.mask(
				PhotonPipelines.InstancedVariant.TILE, VertexFormat.Mode.QUADS)));
		assertEquals(false, PhotonIrisPipelineAccess.isPhotonPipeline(RenderPipelines.SOLID_BLOCK));
	}
}
