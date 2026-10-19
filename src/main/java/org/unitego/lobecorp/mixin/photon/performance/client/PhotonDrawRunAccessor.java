package org.unitego.lobecorp.mixin.photon.performance.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.lowdragmc.photon.client.render.PhotonWorldRenderState$Run")
public interface PhotonDrawRunAccessor {
	@Accessor("renderType")
	RenderType lobecorp$renderType();
}
