package org.unitego.lobecorp.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
	@ModifyExpressionValue(
			method = "emitGizmos",
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES:Z", opcode = Opcodes.GETSTATIC)
	)
	private boolean lobecorp$showLocalServerEntityHitBoxes(boolean configured) {
		return configured && LcDebugEntries.isDebugRenderingEnabled(LcDebugEntries.DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES);
	}
}
