package org.unitego.lobecorp.mixin.conductor.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.conductor.world.ConductorChunkCacheAccess;

/// 玩家缓存轮换时，不卸载仍在指挥家观察缓存内的区块光照和实体。
@Mixin(ClientLevel.class)
public abstract class ConductorClientLevelMixin {
	@Inject(method = "unload", at = @At("HEAD"), cancellable = true)
	private void lobecorp$retainObservedChunk(LevelChunk levelChunk, CallbackInfo ci) {
		ClientLevel level = (ClientLevel) (Object) this;
		if (((ConductorChunkCacheAccess) level.getChunkSource()).lobecorp$retainConductorChunk(levelChunk))
			ci.cancel();
	}
}
