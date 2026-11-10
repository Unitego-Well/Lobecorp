package org.unitego.lobecorp.world.horde.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.config.server.HordeConfig;
import org.unitego.lobecorp.network.tc.horde.HordeStatePayload;
import org.unitego.lobecorp.world.horde.Hordes;
import org.unitego.lobecorp.world.horde.HordeInstance;
import org.unitego.lobecorp.world.horde.HordeRuntime;
import org.unitego.lobecorp.world.horde.HordeSavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Comparator;

/// 订阅独立排序；未订阅路段的修订不被误判成网络缺包。
public class HordeSync {
	private static final Map<ServerPlayer, Map<UUID, Long>> REQUESTS = new WeakHashMap<>();
	private final Map<UUID, Map<UUID, Subscription>> players = new HashMap<>();
	private static final int ENVELOPE_BUDGET = 512;

	public static void request(ServerPlayer player, UUID eventId) {
		var data = HordeSavedData.get(player.level().getServer());
		var event = data.events().get(eventId);
		if (event == null || !interested(player, event, true, null)) {
			return;
		}
		var events = REQUESTS.computeIfAbsent(player, _ -> new HashMap<>());
		events.keySet().removeIf(id -> !data.events().containsKey(id));
		long now = player.level().getGameTime();
		if (now - events.getOrDefault(eventId, Long.MIN_VALUE / 2) >= 40) {
			events.put(eventId, now);
		}
	}

	public void tick(MinecraftServer server, HordeSavedData data, HordeRuntime runtime) {
		var connected = server.getPlayerList().getPlayers();
		players.keySet().removeIf(id -> connected.stream().noneMatch(player -> player.getUUID().equals(id)));
		for (var player : connected) {
			var projections = new HashMap<UUID, Projection>();
			var subscriptions = players.computeIfAbsent(player.getUUID(), _ -> new HashMap<>());
			int remaining = HordeConfig.SEND_BYTES.get();
			for (UUID id : List.copyOf(subscriptions.keySet())) {
				var event = data.events().get(id);
				if (event == null || !interested(player, event, true, projections)) {
					send(player, id, subscriptions.get(id), "remove", new CompoundTag(), data.clock());
					subscriptions.remove(id);
				}
			}
			var events = data.events().values().stream()
					.filter(event -> interested(player, event, subscriptions.containsKey(event.id()), projections))
					.sorted(Comparator.comparingDouble(event -> projection(player, event, projections).distance())).toList();
			for (var event : events) {
				if (remaining <= 0) {
					break;
				}
				var subscription = subscriptions.computeIfAbsent(event.id(), _ -> new Subscription());
				if (subscription.reset || data.clock() - subscription.lastHeader >= 20) {
					subscription.first = 0;
					subscription.last = event.route().size() - 1;
				}
				int first = subscription.first;
				int last = subscription.last;
				var requested = REQUESTS.get(player);
				if (requested != null && requested.getOrDefault(event.id(), Long.MIN_VALUE) > subscription.requestSeen) {
					subscription.requestSeen = requested.get(event.id());
					subscription.reset = true;
					subscription.sent.clear();
					subscription.scanRevision = Long.MIN_VALUE;
				}
				if (subscription.reset || data.clock() - subscription.lastHeader >= 20 || subscription.wasRunning != event.running()) {
					var header = event.header();
					header.putInt("format", 2);
					if (subscription.reset) {
						header.put("overview", event.route().overview(8));
					}
					header.putBoolean("reset", subscription.reset);
					header.putInt("scope_first", first);
					header.putInt("scope_last", last);
					var profile = Hordes.profile(event.profile());
					if (profile != null) {
						header.putString("model", profile.visuals().model().toString());
						header.putString("animation", profile.visuals().animation().toString());
						header.putString("run", profile.visuals().runAnimation());
						header.putString("texture", profile.visuals().texture().toString());
                        header.putDouble("light_probe_height", profile.dimensions().eyeHeight());
                        header.putDouble("entity_height", profile.dimensions().height());
					}
					// 低预算先减少粗略锚点，范围和端点仍在首包中。
					if (subscription.reset) {
						int intervals = 8;
						while (header.sizeInBytes() + ENVELOPE_BUDGET > remaining && intervals > 1) {
							intervals /= 2;
							header.put("overview", event.route().overview(intervals));
						}
					}
					if (header.sizeInBytes() + ENVELOPE_BUDGET > remaining) {
						continue;
					}
					remaining -= header.sizeInBytes() + ENVELOPE_BUDGET;
					send(player, event.id(), subscription, "header", header, data.clock());
					subscription.lastHeader = data.clock();
					subscription.reset = false;
					subscription.wasRunning = event.running();
				}
			}
			// 所有范围首包优先于细节，避免近处路线耗尽预算后远处事件仍不可见。
			for (var event : events) {
				if (remaining <= 0) {
					break;
				}

				var subscription = subscriptions.get(event.id());
				if (subscription == null || subscription.reset) {
					continue;
				}

				int first = subscription.first, last = subscription.last;
				int center = event.route().segment(projection(player, event, projections).station());
				int previousMaximum = 2 * Math.max(subscription.center - first, last - subscription.center);
				if (subscription.scanRevision == Long.MIN_VALUE
						|| Math.abs(center - subscription.center) >= HordeConfig.MAX_SEGMENTS_PER_PACKET.get()
						|| subscription.scanOffset > previousMaximum && subscription.scanRevision != event.revision()) {
					subscription.scanRevision = event.revision();
					subscription.scanOffset = 0;
					subscription.center = center;
				}
				int maximumOffset = 2 * Math.max(subscription.center - first, last - subscription.center);
				while (subscription.scanOffset <= maximumOffset && remaining > 0) {
					int offset = subscription.scanOffset;
					int index = subscription.center + (offset % 2 == 0 ? offset / 2 : -(offset + 1) / 2);
					if (index < first || index > last) {
						subscription.scanOffset++;
						continue;
					}
					long nodeRevision = event.route().node(index).revision();
					if (subscription.sent.getOrDefault(index, Long.MIN_VALUE) == nodeRevision) {
						subscription.scanOffset++;
						continue;
					}
					int count = Math.min(HordeConfig.MAX_SEGMENTS_PER_PACKET.get(), last - index + 1);
					var slice = event.route().slice(index, count);
					while (count > 1 && slice.sizeInBytes() + ENVELOPE_BUDGET > remaining) {
						slice = event.route().slice(index, --count);
					}
					if (slice.sizeInBytes() + ENVELOPE_BUDGET > remaining) {
						break;
					}
					send(player, event.id(), subscription, "route", slice, data.clock());
					remaining -= slice.sizeInBytes() + ENVELOPE_BUDGET;
					for (int i = index; i < index + count; i++) {
						subscription.sent.put(i, event.route().node(i).revision());
					}
					subscription.scanOffset++;
				}
			}
		}
	}

	private static Projection projection(ServerPlayer player, HordeInstance event, @Nullable Map<UUID, Projection> cache) {
		if (cache != null) {
			return cache.computeIfAbsent(event.id(), _ -> projection(player, event, null));
		}

		double station = event.route().nearest(player.position(), 0, event.route().length());
		return new Projection(station, event.route().point(station, 0).distanceTo(player.position()));
	}

	private static double subscriptionDistance(ServerPlayer player) {
		return Math.max(HordeConfig.SUBSCRIBE_DISTANCE.get(), player.requestedViewDistance() * 16d);
	}

	private static boolean interested(ServerPlayer player, HordeInstance event, boolean already,
	                                  @Nullable Map<UUID, Projection> cache) {
		return player.level().dimension().equals(event.dimension()) && projection(player, event, cache).distance()
				<= subscriptionDistance(player) + event.rules().width() / 2
				+ (already ? HordeConfig.SUBSCRIBE_HYSTERESIS.get() : 0);
	}

	private record Projection(double station, double distance) {
	}

	private static void send(ServerPlayer player, UUID id, Subscription subscription, String kind, CompoundTag body, long clock) {
		var tag = new CompoundTag();
		tag.putString("id", id.toString());
		tag.putString("kind", kind);
		tag.putLong("sequence", ++subscription.sequence);
		tag.putLong("clock", clock);
		tag.put("body", body);
		PacketDistributor.sendToPlayer(player, new HordeStatePayload(tag));
	}

	private static class Subscription {
		private long sequence;
		private long scanRevision = Long.MIN_VALUE;
		private int scanOffset;
		private int center;
		private long requestSeen = Long.MIN_VALUE;
		private boolean wasRunning;
		private long lastHeader;
		private boolean reset = true;
		private final Map<Integer, Long> sent = new HashMap<>();
		private int first;
		private int last;
	}
}
