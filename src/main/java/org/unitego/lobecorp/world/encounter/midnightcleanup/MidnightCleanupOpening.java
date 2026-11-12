package org.unitego.lobecorp.world.encounter.midnightcleanup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;
import org.unitego.lobecorp.network.tc.encounter.EncounterStatePayload;
import org.unitego.lobecorp.util.encounter.MidnightCleanupViewUtil;
import org.unitego.lobecorp.world.horde.HordeInstance;
import org.unitego.lobecorp.world.horde.HordeSavedData;
import org.unitego.lobecorp.util.horde.HordePresenceUtil;

import java.util.*;

import static org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter.SPEED_ID;
import static org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter.SUBTITLE_WIDTH_FACTOR;

/// 服务端开幕状态，仅拥有本次临时属性，不改变基础速度或已有修饰符。
public class MidnightCleanupOpening {
	private final Map<ServerPlayer, Lock> locks = new HashMap<>();
	/// 每位玩家在同一事件中只触发一次，离开范围或重连不重置。
	private final Map<UUID, Set<UUID>> played = new HashMap<>();
	private final Map<UUID, Map<UUID, Observation>> observations = new HashMap<>();
	private final Map<UUID, Set<UUID>> closed = new HashMap<>();

	private static boolean inside(ServerPlayer player, HordeInstance event) {
		return inside(player, event, 1);
	}

	private static boolean inside(ServerPlayer player, HordeInstance event, double widthFactor) {
		if (!player.level().dimension().equals(event.dimension())) {
			return false;
		}

		var point = player.position();
		double station = event.route().nearest(point, 0, event.route().length());
		var center = event.route().point(station, 0);
		var direction = event.route().point(Math.min(event.route().length(), station + 1), 0)
				.subtract(event.route().point(Math.max(0, station - 1), 0)).normalize();
		return HordePresenceUtil.contains(point, station, center, direction, 0, event.route().length(),
				event.rules().width() * widthFactor / 2);
	}

	public static Identifier speedModifierId() {
		return SPEED_ID;
	}

	private static void release(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
		}
	}

	private static void sendBanner(ServerPlayer player, UUID event, boolean opening) {
		send(player, event, opening, true, player.getYRot(), player.getXRot());
	}

	private static void send(ServerPlayer player, UUID event, boolean opening, boolean bannerOnly, float yaw, float pitch) {
		var tag = new CompoundTag();
		tag.putString("id", event.toString());
		tag.putString("kind", "presentation");
		tag.putString("definition", MidnightCleanupEncounter.ID.toString());
		var body = new CompoundTag();
		body.putBoolean("opening", opening);
		body.putBoolean("banner_only", bannerOnly);
		body.putFloat("target_yaw", yaw);
		body.putFloat("target_pitch", pitch);
		tag.put("body", body);
		PacketDistributor.sendToPlayer(player, new EncounterStatePayload(tag));
	}

	public void start(MinecraftServer server, HordeInstance event) {
		if (!event.presentationEnabled() || !MidnightCleanupEncounter.ID.equals(event.profile())) {
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
			double arrival = HordePresenceUtil.arrival(station, event.tick(), event.movementSpeed());
			boolean originalCorridor = inside(player, event);
			var previous = observations.computeIfAbsent(event.id(), _ -> new HashMap<>())
					.put(player.getUUID(), new Observation(event.tick(), arrival, originalCorridor));
			if (event.finished() || arrival > MidnightCleanupEncounter.OPENING_WARNING_TICKS) {
				continue;
			}

			var seen = played.computeIfAbsent(event.id(), _ -> new HashSet<>());
			if (!seen.add(player.getUUID())) {
				continue;
			}

			boolean crossedWindow = previous != null && previous.tick() == event.tick() - 1
					&& previous.inside() && previous.arrival() > MidnightCleanupEncounter.OPENING_WARNING_TICKS;
			boolean initialWindow = previous == null && event.tick() <= 1
					&& arrival >= MidnightCleanupEncounter.OPENING_WARNING_TICKS - 1;
			if (arrival <= 0 || !originalCorridor || !crossedWindow && !initialWindow || locks.containsKey(player)) {
				sendBanner(player, event.id(), true);
				continue;
			}

			var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(SPEED_ID);
				speed.addTransientModifier(new AttributeModifier(SPEED_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			var center = event.route().point(HordePresenceUtil.center(event.head(), event.tail()), 0);
			var toward = center.subtract(player.getEyePosition());
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
				if (server.getTickCount() >= lock.until() && player.isAlive() && !player.isSpectator()
						&& event != null && player.level().dimension().equals(event.dimension())) {
					var toward = event.route().point(HordePresenceUtil.center(event.head(), event.tail()), 0).subtract(player.getEyePosition());
					float yaw = (float) Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90;
					float pitch = (float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance()));
					player.setYRot(yaw);
					player.setXRot(pitch);
					player.setYHeadRot(yaw);
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
		closed.keySet().removeIf(id -> !data.events().containsKey(id));
		for (var event : data.events().values()) {
			start(server, event);
			closing(server, event, false);
		}
	}

	public void finish(MinecraftServer server, HordeInstance event) {
		if (!event.presentationEnabled() || !MidnightCleanupEncounter.ID.equals(event.profile())) {
			return;
		}
		closing(server, event, true);

		played.remove(event.id());
		observations.remove(event.id());
		locks.entrySet().removeIf(entry -> {
			if (entry.getValue().event().equals(event.id())) {
				release(entry.getKey());
				return true;
			}

			return false;
		});
	}

	private void closing(MinecraftServer server, HordeInstance event, boolean forced) {
		if (!event.presentationEnabled() || !MidnightCleanupEncounter.ID.equals(event.profile())) {
			return;
		}
		for (var player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !inside(player, event, SUBTITLE_WIDTH_FACTOR)
					|| !forced && event.closingRemaining(player.position()) >= 0) {
				continue;
			}
			if (closed.computeIfAbsent(event.id(), _ -> new HashSet<>()).add(player.getUUID())) {
				skip(player, event.id());
				sendBanner(player, event.id(), false);
			}
		}
	}

	public void skip(ServerPlayer player, UUID eventId) {
		var lock = locks.get(player);
		if (lock == null || !lock.event().equals(eventId)) {
			return;
		}
		locks.remove(player);
		release(player);
	}

	public void clear() {
		locks.keySet().forEach(MidnightCleanupOpening::release);
		locks.clear();
		played.clear();
		observations.clear();
		closed.clear();
	}

	private record Observation(long tick, double arrival, boolean inside) {
	}

	private record Lock(UUID event, int until, float startYaw, float startPitch, float yaw, float pitch) {
	}
}
