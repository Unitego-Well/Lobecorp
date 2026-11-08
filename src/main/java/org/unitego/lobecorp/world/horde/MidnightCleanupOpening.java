package org.unitego.lobecorp.world.horde;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.network.tc.horde.HordeStatePayload;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/// 服务端开幕状态，仅拥有本次临时属性，不改变基础速度或已有修饰符。
public class MidnightCleanupOpening {
	private static final Identifier SPEED_ID = Lobecorp.id("midnight_cleanup/opening_speed");
	private final Map<ServerPlayer, Lock> locks = new HashMap<>();
	/// 每位玩家在同一事件中只触发一次，离开范围或重连不重置。
	private final Map<UUID, Set<UUID>> played = new HashMap<>();
	private static final int ARRIVAL_WARNING_TICKS = 20 * 20;

	public void start(MinecraftServer server, HordeEvent event) {
		if (!MidnightCleanup.ID.equals(event.profile())) {
			return;
		}

		for (var player : server.getPlayerList().getPlayers()) {
			if (!inside(player, event) || player.isSpectator() || !player.isAlive()) {
				continue;
			}

			double station = event.route().nearest(player.position(), 0, event.route().length());
			double arrival = station / event.movementSpeed() - event.tick();
			if (event.finished() || arrival > ARRIVAL_WARNING_TICKS) {
				continue;
			}

			var seen = played.computeIfAbsent(event.id(), _ -> new HashSet<>());
			if (!seen.add(player.getUUID())) {
				continue;
			}

			if (arrival <= 0) {
				send(player, event.id(), true, true);
				continue;
			}

			var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(SPEED_ID);
				speed.addTransientModifier(new AttributeModifier(SPEED_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			locks.put(player, new Lock(event.id(), server.getTickCount() + MidnightCleanup.OPENING_TICKS));
			var toward = event.route().direction(station).scale(-1);
			player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(toward.scale(16)));
			send(player, event.id(), true, false);
		}
	}

	public void tick(MinecraftServer server, HordeSavedData data) {
		locks.entrySet().removeIf(entry -> {
			var player = entry.getKey();
			var lock = entry.getValue();
			var event = data.events().get(lock.event());
			if (server.getTickCount() >= lock.until() || !player.isAlive() || player.isSpectator()
					|| server.getPlayerList().getPlayer(player.getUUID()) != player || event == null
					|| !player.level().dimension().equals(event.dimension())) {
				release(player);
				return true;
			}

			return false;
		});
		played.keySet().removeIf(id -> !data.events().containsKey(id));
		for (var event : data.events().values()) {
			start(server, event);
		}
	}

	public void finish(MinecraftServer server, HordeEvent event) {
		if (!MidnightCleanup.ID.equals(event.profile())) {
			return;
		}

		played.remove(event.id());
		locks.entrySet().removeIf(entry -> {
			if (entry.getValue().event().equals(event.id())) {
				release(entry.getKey());
				return true;
			}

			return false;
		});
		for (var player : server.getPlayerList().getPlayers()) {
			if (inside(player, event) && !player.isSpectator()) {
				send(player, event.id(), false, false);
			}
		}
	}

	private static boolean inside(ServerPlayer player, HordeEvent event) {
		if (!player.level().dimension().equals(event.dimension())) {
			return false;
		}

		var point = player.position();
		double station = event.route().nearest(point, 0, event.route().length());
		var center = event.route().point(station, 0);
		var direction = event.route().point(Math.min(event.route().length(), station + 1), 0)
				.subtract(event.route().point(Math.max(0, station - 1), 0)).normalize();
		double along = (point.x - center.x) * direction.x + (point.z - center.z) * direction.z;
		if (station < 0.001 && along < 0 || station > event.route().length() - 0.001 && along > 0) {
			return false;
		}

		double dx = point.x - center.x, dz = point.z - center.z;
		return dx * dx + dz * dz <= event.rules().width() * event.rules().width() / 4;
	}

	private static void release(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
		}
	}

	private static void send(ServerPlayer player, UUID event, boolean opening, boolean bannerOnly) {
		var tag = new CompoundTag();
		tag.putString("id", event.toString());
		tag.putString("kind", "presentation");
		var body = new CompoundTag();
		body.putBoolean("opening", opening);
		body.putBoolean("banner_only", bannerOnly);
		tag.put("body", body);
		PacketDistributor.sendToPlayer(player, new HordeStatePayload(tag));
	}

	public void clear() {
		locks.keySet().forEach(MidnightCleanupOpening::release);
		locks.clear();
		played.clear();
	}

	private record Lock(UUID event, int until) {
	}
}
