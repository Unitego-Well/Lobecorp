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
import java.util.UUID;

/// 服务端开幕状态，仅拥有本次临时属性，不改变基础速度或已有修饰符。
public class MidnightCleanupOpening {
	private static final Identifier SPEED_ID = Lobecorp.id("midnight_cleanup/opening_speed");
	private final Map<ServerPlayer, Lock> locks = new HashMap<>();

	public void start(MinecraftServer server, HordeEvent event) {
		if (!MidnightCleanup.ID.equals(event.profile())) {
			return;
		}

		for (var player : server.getPlayerList().getPlayers()) {
			if (!inside(player, event) || player.isSpectator()) {
				continue;
			}

			var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(SPEED_ID);
				speed.addTransientModifier(new AttributeModifier(SPEED_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			locks.put(player, new Lock(event.id(), server.getTickCount() + MidnightCleanup.OPENING_TICKS));
			var toward = event.route().direction(0).scale(-1);
			player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(toward.scale(16)));
			send(player, event.id(), true);
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
	}

	public void finish(MinecraftServer server, HordeEvent event) {
		if (!MidnightCleanup.ID.equals(event.profile())) {
			return;
		}

		locks.entrySet().removeIf(entry -> {
			if (entry.getValue().event().equals(event.id())) {
				release(entry.getKey());
				return true;
			}

			return false;
		});
		for (var player : server.getPlayerList().getPlayers()) {
			if (inside(player, event) && !player.isSpectator()) {
				send(player, event.id(), false);
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

	private static void send(ServerPlayer player, UUID event, boolean opening) {
		var tag = new CompoundTag();
		tag.putString("id", event.toString());
		tag.putString("kind", "presentation");
		var body = new CompoundTag();
		body.putBoolean("opening", opening);
		tag.put("body", body);
		PacketDistributor.sendToPlayer(player, new HordeStatePayload(tag));
	}

	public void clear() {
		locks.keySet().forEach(MidnightCleanupOpening::release);
		locks.clear();
	}

	private record Lock(UUID event, int until) {
	}
}
