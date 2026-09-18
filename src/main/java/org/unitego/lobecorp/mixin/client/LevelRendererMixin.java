package org.unitego.lobecorp.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LevelRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@ModifyExpressionValue(
			method = {"extractBlockOutline", "renderHitOutline"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_SHAPES:Z", opcode = Opcodes.GETSTATIC)
	)
	private boolean lobecorp$showDebugShapes(boolean configured) {
		return configured && LcDebugEntries.isDebugRenderingEnabled(LcDebugEntries.DEBUG_SHAPES);
	}
}
