package org.unitego.lobecorp.mixin.world.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.horde.HordeGroundCache;
import org.unitego.lobecorp.registry.client.entity.LcDebugEntries;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "blockChanged", at = @At("HEAD"))
	private void lobecorp$invalidateGround(BlockGetter level, BlockPos pos, BlockState old,
	                                      BlockState current, int updateFlags, CallbackInfo callback) {
		if (level == Minecraft.getInstance().level) {
			HordeGroundCache.invalidate(pos);
		}
	}

	@ModifyExpressionValue(
			method = {"extractBlockOutline", "renderHitOutline"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_SHAPES:Z", opcode = Opcodes.GETSTATIC)
	)
	private boolean lobecorp$showDebugShapes(boolean configured) {
		return configured && LcDebugEntries.isDebugRenderingEnabled(LcDebugEntries.DEBUG_SHAPES);
	}
}
