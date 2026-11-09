package org.unitego.lobecorp.world.horde;

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
import org.unitego.lobecorp.util.horde.MidnightCleanupViewUtil;

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
	private final Map<UUID, Map<UUID, Observation>> observations = new HashMap<>();
	private static final double SUBTITLE_WIDTH_FACTOR = 1.5;

	public void start(MinecraftServer server, HordeEvent event) {
		if (!MidnightCleanup.ID.equals(event.profile())) {
			return;
		}

		for (var player : server.getPlayerList().getPlayers()) {
			if (!inside(player, event, SUBTITLE_WIDTH_FACTOR) || player.isSpectator() || !player.isAlive()) {
				var previous = observations.get(event.id());
				if (previous != null) {
					previous.remove(player.getUUID());
				}
				continue;
			}

			double station = event.route().nearest(player.position(), 0, event.route().length());
			double arrival = station / event.movementSpeed() - event.tick();
			boolean originalCorridor = inside(player, event);
			var previous = observations.computeIfAbsent(event.id(), _ -> new HashMap<>())
					.put(player.getUUID(), new Observation(event.tick(), arrival, originalCorridor));
			if (event.finished() || arrival > MidnightCleanup.OPENING_WARNING_TICKS) {
				continue;
			}

			var seen = played.computeIfAbsent(event.id(), _ -> new HashSet<>());
			if (!seen.add(player.getUUID())) {
				continue;
			}

			boolean crossedWindow = previous != null && previous.tick() == event.tick() - 1
					&& previous.inside() && previous.arrival() > MidnightCleanup.OPENING_WARNING_TICKS;
			boolean initialWindow = previous == null && event.tick() <= 1
					&& arrival >= MidnightCleanup.OPENING_WARNING_TICKS - 1;
			if (arrival <= 0 || !originalCorridor || !crossedWindow && !initialWindow || locks.containsKey(player)) {
				send(player, event.id(), true, true);
				continue;
			}

			var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(SPEED_ID);
				speed.addTransientModifier(new AttributeModifier(SPEED_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			var toward = event.route().direction(station).scale(-1);
			float targetYaw = (float) Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90;
			float targetPitch = (float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance()));
			locks.put(player, new Lock(event.id(), server.getTickCount() + MidnightCleanupViewUtil.CENTER_END,
					player.getYRot(), player.getXRot(), targetYaw, targetPitch));
			send(player, event.id(), true, false, targetYaw, targetPitch);
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
				if (server.getTickCount() >= lock.until() && player.isAlive() && !player.isSpectator()) {
					player.setYRot(lock.startYaw());
					player.setXRot(lock.startPitch());
					player.setYHeadRot(lock.startYaw());
				}
				release(player);
				return true;
			}

			float age = MidnightCleanupViewUtil.CENTER_END - (lock.until() - server.getTickCount());
			var angles = MidnightCleanupViewUtil.sample(lock.startYaw(), lock.startPitch(), lock.yaw(), lock.pitch(), age);
			player.setYRot(angles.yaw());
			player.setXRot(angles.pitch());
			player.setYHeadRot(angles.yaw());
			return false;
		});
		played.keySet().removeIf(id -> !data.events().containsKey(id));
		observations.keySet().removeIf(id -> !data.events().containsKey(id));
		for (var event : data.events().values()) {
			start(server, event);
		}
	}

	public void finish(MinecraftServer server, HordeEvent event) {
		if (!MidnightCleanup.ID.equals(event.profile())) {
			return;
		}

		played.remove(event.id());
		observations.remove(event.id());
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
		return inside(player, event, 1);
	}

	private static boolean inside(ServerPlayer player, HordeEvent event, double widthFactor) {
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
		double halfWidth = event.rules().width() * widthFactor / 2;
		return dx * dx + dz * dz <= halfWidth * halfWidth;
	}

	public static Identifier speedModifierId() {
		return SPEED_ID;
	}

	public void skip(ServerPlayer player, UUID eventId) {
		var lock = locks.get(player);
		if (lock == null || !lock.event().equals(eventId)) {
			return;
		}
		locks.remove(player);
		player.setYRot(lock.startYaw());
		player.setXRot(lock.startPitch());
		player.setYHeadRot(lock.startYaw());
		release(player);
	}

	private static void release(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
		}
	}

	private static void send(ServerPlayer player, UUID event, boolean opening, boolean bannerOnly) {
		send(player, event, opening, bannerOnly, player.getYRot(), player.getXRot());
	}

	private static void send(ServerPlayer player, UUID event, boolean opening, boolean bannerOnly, float yaw, float pitch) {
		var tag = new CompoundTag();
		tag.putString("id", event.toString());
		tag.putString("kind", "presentation");
		var body = new CompoundTag();
		body.putBoolean("opening", opening);
		body.putBoolean("banner_only", bannerOnly);
		body.putFloat("target_yaw", yaw);
		body.putFloat("target_pitch", pitch);
		tag.put("body", body);
		PacketDistributor.sendToPlayer(player, new HordeStatePayload(tag));
	}

	public void clear() {
		locks.keySet().forEach(MidnightCleanupOpening::release);
		locks.clear();
		played.clear();
		observations.clear();
	}

	private record Observation(long tick, double arrival, boolean inside) {
	}

	private record Lock(UUID event, int until, float startYaw, float startPitch, float yaw, float pitch) {
	}
}
