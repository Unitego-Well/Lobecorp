package org.unitego.lobecorp.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.conductor.ConductorChunkCacheAccess;
import org.unitego.lobecorp.client.conductor.ConductorClientChunks;

import java.util.Map;
import java.util.function.Consumer;

/// 将额外观察区块接入原版客户端区块查询和更新，保留玩家本体缓存。
@Mixin(ClientChunkCache.class)
public abstract class ConductorClientChunkCacheMixin implements ConductorChunkCacheAccess {
	@Unique private ConductorClientChunks lobecorp$observedChunks;

	@Shadow public abstract LongOpenHashSet getLoadedEmptySections();

	@Inject(method = "<init>", at = @At("TAIL"))
	private void lobecorp$createObservationCache(ClientLevel level, int serverChunkRadius, CallbackInfo ci) {
		lobecorp$observedChunks = new ConductorClientChunks(level, serverChunkRadius, this::getLoadedEmptySections);
	}

	@Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$getObservedChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<LevelChunk> cir) {
		LevelChunk chunk = lobecorp$observedChunks.get(x, z);
		if (chunk != null) cir.setReturnValue(chunk);
	}

	@WrapMethod(method = "replaceWithPacketData")
	private LevelChunk lobecorp$receiveObservedChunk(int chunkX, int chunkZ, FriendlyByteBuf readBuffer, Map<Heightmap.Types, long[]> heightmaps,
	                                               Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
	                                               Operation<LevelChunk> original) {
		return lobecorp$observedChunks.handles(chunkX, chunkZ) ? lobecorp$observedChunks.replace(chunkX, chunkZ, readBuffer, heightmaps, blockEntities)
				: original.call(chunkX, chunkZ, readBuffer, heightmaps, blockEntities);
	}

	@WrapMethod(method = "replaceBiomes")
	private void lobecorp$receiveObservedBiomes(int chunkX, int chunkZ, FriendlyByteBuf readBuffer, Operation<Void> original) {
		LevelChunk chunk = lobecorp$observedChunks.get(chunkX, chunkZ);
		if (chunk != null) chunk.replaceBiomes(readBuffer);
		else original.call(chunkX, chunkZ, readBuffer);
	}

	@Inject(method = "drop", at = @At("HEAD"))
	private void lobecorp$dropObservedChunk(ChunkPos pos, CallbackInfo ci) {
		lobecorp$observedChunks.drop(pos);
	}

	@Inject(method = "updateViewCenter", at = @At("TAIL"))
	private void lobecorp$setBodyCenter(int x, int z, CallbackInfo ci) {
		lobecorp$observedChunks.setCenter(x, z);
	}

	@Inject(method = "updateViewRadius", at = @At("TAIL"))
	private void lobecorp$setBodyRadius(int viewRange, CallbackInfo ci) {
		lobecorp$observedChunks.setRadius(viewRange);
	}

	@Inject(method = "onSectionEmptinessChanged", at = @At("TAIL"))
	private void lobecorp$updateObservedSection(int sectionX, int sectionY, int sectionZ, boolean empty, CallbackInfo ci) {
		lobecorp$observedChunks.onSectionEmptinessChanged(sectionX, sectionY, sectionZ, empty);
	}

	@Override
	public boolean lobecorp$retainConductorChunk(LevelChunk chunk) {
		return lobecorp$observedChunks.retain(chunk);
	}
}
