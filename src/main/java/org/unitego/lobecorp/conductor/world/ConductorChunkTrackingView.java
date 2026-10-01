package org.unitego.lobecorp.conductor.world;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/// 同时保留玩家本体和指挥家镜头的区块观察范围。
public record ConductorChunkTrackingView(Positioned body, Positioned camera) implements ChunkTrackingView {
	@Override
	public boolean contains(int chunkX, int chunkZ, boolean includeNeighbors) {
		return body.contains(chunkX, chunkZ, includeNeighbors) || camera.contains(chunkX, chunkZ, includeNeighbors);
	}

	@Override
	public void forEach(@NonNull Consumer<ChunkPos> consumer) {
		body.forEach(consumer);
		camera.forEach(pos -> {
			if (!body.contains(pos)) consumer.accept(pos);
		});
	}
}
