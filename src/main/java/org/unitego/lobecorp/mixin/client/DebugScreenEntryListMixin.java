package org.unitego.lobecorp.mixin.client;

import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.debug.LcDebugRuntimeOptions;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;

@Mixin(DebugScreenEntryList.class)
public class DebugScreenEntryListMixin {
	@Inject(method = "rebuildCurrentList", at = @At("TAIL"))
	private void lobecorp$updateLocalServerDebugOptions(CallbackInfo callbackInfo) {
		DebugScreenEntryList entries = (DebugScreenEntryList) (Object) this;
		LcDebugRuntimeOptions.setMonitorTickTimesFromClient(
				entries.isCurrentlyEnabled(LcDebugEntries.DEBUG_ENABLED)
						|| entries.isCurrentlyEnabled(LcDebugEntries.DEBUG_MONITOR_TICK_TIMES)
		);
	}
}
