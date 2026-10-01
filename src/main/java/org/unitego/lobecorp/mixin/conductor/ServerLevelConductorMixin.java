package org.unitego.lobecorp.mixin.conductor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.world.ConductorChunkTrackingView;
import org.unitego.lobecorp.conductor.world.ConductorView;

import java.util.List;

/// 镜头范围内的粒子沿用原版载荷，绕过以玩家本体为中心的粒子距离限制。
@Mixin(ServerLevel.class)
public abstract class ServerLevelConductorMixin {
	@Shadow @Final private List<ServerPlayer> players;

	@Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$sendObservedParticles(ServerPlayer player, boolean overrideLimiter, double x, double y, double z,
	                                          Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
		if (!players.contains(player) || ConductorView.position(player) == null
				|| !(player.getChunkTrackingView() instanceof ConductorChunkTrackingView view)
				|| !view.camera().contains(ChunkPos.containing(BlockPos.containing(x, y, z)))
				|| !(packet instanceof ClientboundLevelParticlesPacket particles)) return;
		player.connection.send(new ClientboundLevelParticlesPacket(particles.getParticle(), true, particles.alwaysShow(),
				particles.getX(), particles.getY(), particles.getZ(), particles.getXDist(), particles.getYDist(),
				particles.getZDist(), particles.getMaxSpeed(), particles.getCount()));
		cir.setReturnValue(true);
	}
}
