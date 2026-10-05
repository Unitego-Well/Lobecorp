package org.unitego.lobecorp.client.conductor.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.conductor.ConductorControls;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// 缓存原版玩家区块缓存范围之外的观察区块，沿用原版区块包、光照和生命周期。
public class ConductorClientChunks {
	/// 原版客户端区块缓存额外保留的邻接区块数量。
	private static final int CACHE_BORDER_CHUNKS = 3;
	private final ClientLevel level;
	private final Supplier<LongOpenHashSet> emptySections;
	private final Map<Long, LevelChunk> chunks = new HashMap<>();
	private int centerX;
	private int centerZ;
	private int radius;
	private boolean observed;

	public ConductorClientChunks(ClientLevel level, int viewRadius, Supplier<LongOpenHashSet> emptySections) {
		this.level = level;
		this.emptySections = emptySections;
		setRadius(viewRadius);
	}

	public void setCenter(int x, int z) {
		centerX = x;
		centerZ = z;
	}

	public void setRadius(int viewRadius) {
		radius = Math.max(2, viewRadius) + CACHE_BORDER_CHUNKS;
		chunks.values().forEach(this::refreshEmptySections);
	}

	public boolean handles(int x, int z) {
		observed |= ConductorControls.active();
		return chunks.containsKey(ChunkPos.pack(x, z)) || observed
				&& (Math.abs(x - centerX) > radius || Math.abs(z - centerZ) > radius);
	}

	public @Nullable LevelChunk get(int x, int z) {
		return chunks.get(ChunkPos.pack(x, z));
	}

	public LevelChunk replace(int x, int z, FriendlyByteBuf buffer, Map<Heightmap.Types, long[]> heightmaps,
	                          Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities) {
		ChunkPos pos = new ChunkPos(x, z);
		LevelChunk chunk = chunks.computeIfAbsent(pos.pack(), ignored -> new LevelChunk(level, pos));
		chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
		refreshEmptySections(chunk);
		level.onChunkLoaded(pos);
		NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
		return chunk;
	}

	public void drop(ChunkPos pos) {
		LevelChunk chunk = chunks.remove(pos.pack());
		if (chunk == null)
			return;
		for (int index = 0; index < chunk.getSections().length; index++)
			emptySections.get().remove(SectionPos.asLong(pos.x(), chunk.getSectionYFromSectionIndex(index), pos.z()));
		NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
		level.unload(chunk);
	}

	public boolean retain(LevelChunk unloading) {
		LevelChunk retained = chunks.get(unloading.getPos().pack());
		if (retained == null)
			return false;
		if (retained != unloading)
			unloading.clearAllBlockEntities();
		refreshEmptySections(retained);
		return true;
	}

	public void onSectionEmptinessChanged(int x, int y, int z, boolean empty) {
		if (!chunks.containsKey(ChunkPos.pack(x, z)))
			return;
		long section = SectionPos.asLong(x, y, z);
		if (empty)
			emptySections.get().add(section);
		else if (emptySections.get().remove(section))
			level.onSectionBecomingNonEmpty(section);
	}

	private void refreshEmptySections(LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		LevelChunkSection[] sections = chunk.getSections();
		for (int index = 0; index < sections.length; index++)
			onSectionEmptinessChanged(pos.x(), chunk.getSectionYFromSectionIndex(index), pos.z(), sections[index].hasOnlyAir());
	}
}
