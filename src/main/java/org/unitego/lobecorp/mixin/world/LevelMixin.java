package org.unitego.lobecorp.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.world.horde.HordeRuntime;

@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	@SuppressWarnings("ConstantValue")
	private void lobecorp$invalidateHordeGround(BlockPos pos, BlockState blockState, int updateFlags, int updateLimit,
	                                            CallbackInfoReturnable<Boolean> result) {
		if (result.getReturnValueZ() && (Object) this instanceof ServerLevel level) {
			HordeRuntime.blockUpdated(level, pos);
		}
	}
}