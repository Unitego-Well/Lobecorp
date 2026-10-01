package org.unitego.lobecorp.client.conductor;

import net.minecraft.world.level.chunk.LevelChunk;

/// 原版缓存替换区块时保留仍受观察的区块光照和实体状态。
public interface ConductorChunkCacheAccess {
	boolean lobecorp$retainConductorChunk(LevelChunk chunk);
}
