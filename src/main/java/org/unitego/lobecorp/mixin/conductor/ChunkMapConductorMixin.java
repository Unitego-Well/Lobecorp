package org.unitego.lobecorp.mixin.conductor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.conductor.world.ConductorChunkTrackingView;
import org.unitego.lobecorp.conductor.world.ConductorView;

/// 将镜头范围加入原版区块观察，客户端原版缓存中心仍跟随玩家本体。
@Mixin(ChunkMap.class)
public abstract class ChunkMapConductorMixin {
	@Shadow @Final private ServerLevel level;

	@Shadow
	private int getPlayerViewDistance(ServerPlayer player) {
		throw new AssertionError();
	}

	@Shadow
	private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView next) {
		throw new AssertionError();
	}

	@Shadow
	private void markChunkPendingToSend(ServerPlayer player, ChunkPos pos) {
		throw new AssertionError();
	}

	@Shadow
	private static void dropChunk(ServerPlayer player, ChunkPos pos) {
		throw new AssertionError();
	}

	@Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
	private void lobecorp$observeCamera(ServerPlayer player, CallbackInfo ci) {
		Vec3 camera = ConductorView.position(player);
		if (camera == null) return;
		int radius = getPlayerViewDistance(player);
		ConductorChunkTrackingView next = new ConductorChunkTrackingView(
				new ChunkTrackingView.Positioned(player.chunkPosition(), radius),
				new ChunkTrackingView.Positioned(ChunkPos.containing(BlockPos.containing(camera)), radius));
		if (!next.equals(player.getChunkTrackingView())) applyChunkTrackingView(player, next);
		ci.cancel();
	}

	@Inject(method = "applyChunkTrackingView", at = @At("HEAD"), cancellable = true)
	private void lobecorp$updateObservation(ServerPlayer player, ChunkTrackingView next, CallbackInfo ci) {
		ChunkTrackingView previous = player.getChunkTrackingView();
		if (!(next instanceof ConductorChunkTrackingView) && !(previous instanceof ConductorChunkTrackingView)) return;
		if (player.level() == level) {
			ChunkPos nextCenter = next instanceof ConductorChunkTrackingView view ? view.body().center()
					: next instanceof ChunkTrackingView.Positioned view ? view.center() : null;
			ChunkPos previousCenter = previous instanceof ConductorChunkTrackingView view ? view.body().center()
					: previous instanceof ChunkTrackingView.Positioned view ? view.center() : null;
			if (nextCenter != null && !nextCenter.equals(previousCenter))
				player.connection.send(new ClientboundSetChunkCacheCenterPacket(nextCenter.x(), nextCenter.z()));
			previous.forEach(pos -> {
				if (!next.contains(pos)) dropChunk(player, pos);
			});
			next.forEach(pos -> {
				if (!previous.contains(pos)) markChunkPendingToSend(player, pos);
			});
			player.setChunkTrackingView(next);
		}
		ci.cancel();
	}
}
