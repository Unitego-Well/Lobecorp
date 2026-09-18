package org.unitego.lobecorp.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
	@ModifyExpressionValue(
			method = "refreshRendererList",
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_PATHFINDING:Z", opcode = Opcodes.GETSTATIC)
	)
	private boolean lobecorp$showPathfinding(boolean configured) {
		return configured && LcDebugEntries.isDebugRenderingEnabled(LcDebugEntries.DEBUG_PATHFINDING);
	}

	@ModifyExpressionValue(
			method = "refreshRendererList",
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_GOAL_SELECTOR:Z", opcode = Opcodes.GETSTATIC)
	)
	private boolean lobecorp$showGoalSelector(boolean configured) {
		return configured && LcDebugEntries.isDebugRenderingEnabled(LcDebugEntries.DEBUG_GOAL_SELECTOR);
	}
}
