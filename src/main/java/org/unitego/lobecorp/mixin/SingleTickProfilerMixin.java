package org.unitego.lobecorp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.util.profiling.SingleTickProfiler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.debug.LcDebugRuntimeOptions;

@Mixin(SingleTickProfiler.class)
public class SingleTickProfilerMixin {
	@ModifyExpressionValue(
			method = "createTickProfiler",
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_MONITOR_TICK_TIMES:Z", opcode = Opcodes.GETSTATIC)
	)
	private static boolean lobecorp$monitorTickTimes(boolean configured) {
		return LcDebugRuntimeOptions.monitorTickTimes(configured);
	}
}
