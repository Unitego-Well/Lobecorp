package org.unitego.lobecorp.conductor.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.registry.ConductorTicketControllers;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/// 指挥家镜头的临时服务端观察位置和区块票据，不改变玩家本体位置。
public class ConductorView {
	public static void update(ServerPlayer player, boolean active, Vec3 position) {
		ConductorData data = ConductorData.get(player.level().getServer());
		State previous = data.runtime.views.get(player.getUUID());
		if (!active) {
			data.runtime.views.remove(player.getUUID());
			if (previous != null) release(player.getUUID(), previous);
			player.level().getChunkSource().chunkMap.move(player);
			return;
		}
		if (!player.isAlive() || !Double.isFinite(position.x) || !Double.isFinite(position.y)
				|| !Double.isFinite(position.z) || !player.level().getWorldBorder().isWithinBounds(BlockPos.containing(position))) {
			return;
		}
		if (previous == null || previous.level != player.level()) {
			if (previous != null) release(player.getUUID(), previous);
			previous = new State(player.level());
			data.runtime.views.put(player.getUUID(), previous);
		}
		previous.position = position;
		previous.updatedAt = previous.level.getGameTime();
	}

	public static @Nullable Vec3 position(ServerPlayer player) {
		State state = ConductorData.get(player.level().getServer()).runtime.views.get(player.getUUID());
		return state != null && valid(player, state) ? state.position : null;
	}

	public static void tick(MinecraftServer server, ConductorData data) {
		Iterator<Map.Entry<UUID, State>> iterator = data.runtime.views.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, State> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			State state = entry.getValue();
			if (player == null || !valid(player, state)) {
				iterator.remove();
				release(entry.getKey(), state);
				if (player != null) player.level().getChunkSource().chunkMap.move(player);
				continue;
			}
			int radius = Mth.clamp(player.requestedViewDistance(), 2, server.getPlayerList().getViewDistance());
			ChunkTrackingView next = ChunkTrackingView.of(ChunkPos.containing(BlockPos.containing(state.position)), radius);
			if (!next.equals(state.tickets)) {
				state.tickets.forEach(pos -> {
					if (!next.contains(pos)) ConductorTicketControllers.forceViewChunk(state.level, entry.getKey(), pos.x(), pos.z(), false);
				});
				next.forEach(pos -> {
					if (!state.tickets.contains(pos)) ConductorTicketControllers.forceViewChunk(state.level, entry.getKey(), pos.x(), pos.z(), true);
				});
				state.tickets = next;
			}
			state.level.getChunkSource().chunkMap.move(player);
		}
	}

	private static boolean valid(ServerPlayer player, State state) {
		return player.isAlive() && player.level() == state.level
				&& state.level.getGameTime() - state.updatedAt <= ConductorRules.VIEW_TIMEOUT_TICKS;
	}

	private static void release(UUID player, State state) {
		state.tickets.forEach(pos -> ConductorTicketControllers.forceViewChunk(state.level, player, pos.x(), pos.z(), false));
	}

	protected static class State {
		private final ServerLevel level;
		private Vec3 position = Vec3.ZERO;
		private long updatedAt;
		private ChunkTrackingView tickets = ChunkTrackingView.EMPTY;

		private State(ServerLevel level) {
			this.level = level;
		}
	}
}
