package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// 在安全 parked 阶段清理原生 reset 未覆盖的光照缓存及并行查询状态。
@Mixin(value = Emitter.class, remap = false)
public interface EmitterLeaseAccessor {
	@Accessor("lightCache")
	ConcurrentHashMap<BlockPos, Integer> lobecorp$getLeaseLightCache();

	@Accessor("parallelLightPhase")
	void lobecorp$setLeaseParallelLightPhase(boolean value);

	@Accessor("lightQueryQueue")
	Set<BlockPos> lobecorp$getLeaseLightQueryQueue();
}
