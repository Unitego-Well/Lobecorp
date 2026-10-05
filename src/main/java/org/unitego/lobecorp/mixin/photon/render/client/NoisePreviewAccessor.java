package org.unitego.lobecorp.mixin.photon.render.client;

import com.lowdragmc.photon.client.gameobject.emitter.data.NoiseSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseSetting.NoisePreview.class)
@SuppressWarnings("UnnecessaryModifier")
public interface NoisePreviewAccessor {
	@Accessor("this$0")
	public NoiseSetting lobecorp$config();
}
