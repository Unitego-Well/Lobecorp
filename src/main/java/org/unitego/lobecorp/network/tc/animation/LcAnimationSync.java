package org.unitego.lobecorp.network.tc.animation;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.attachment.LcAttachmentSnapshot;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.network.tc.attachment.LcAttachmentStatePayload;
import org.unitego.lobecorp.world.item.animation.LcAttachedItemSnapshot;
import org.unitego.lobecorp.world.item.animation.LcAttachedItems;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/// 追踪关系只决定收件人；同步不会回放技能或动画时间线事件。
@SuppressWarnings("resource")
public class LcAnimationSync {
	private static final Map<Entity, Set<UUID>> TRACKERS = new WeakHashMap<>();
	// 死亡重生会替换 ServerPlayer，同一连接仍须保留已发送实例以删除旧帧。
	private static final Map<ServerGamePacketListenerImpl, PlayerState> PLAYERS = new WeakHashMap<>();

	public static void track(ServerPlayer player, Entity entity) {
		TRACKERS.computeIfAbsent(entity, _ -> new HashSet<>()).add(player.getUUID());
		for (var snapshot : LcServerAnimations.snapshots(entity)) {
			send(player, snapshot);
		}

		syncAttachments(player);
	}

	public static void untrack(ServerPlayer player, Entity entity) {
		var trackers = TRACKERS.get(entity);
		if (trackers != null) {
			trackers.remove(player.getUUID());
		}

		var state = state(player);
		for (var snapshot : List.copyOf(state.snapshots.values())) {
			if (snapshot.ownerEntityId() == entity.getId()) {
				state.snapshots.remove(snapshot.instanceId());
				PacketDistributor.sendToPlayer(player, new LcAnimationRemovePayload(snapshot.instanceId(),
						LcAnimationTermination.UNTRACKED));
			}
		}

		syncAttachments(player);
	}

	public static void sync(ServerLevel level) {
		var snapshots = LcServerAnimations.snapshots(level);
		var attachments = LcAttachments.snapshots(level);
		var items = LcAttachedItems.snapshots(level);
		for (var player : level.players()) {
			var state = state(player);
			Map<UUID, LcAnimationSnapshot> visible = new LinkedHashMap<>();
			for (var snapshot : snapshots) {
				if (!visibleTo(level, player, snapshot)) {
					continue;
				}

				visible.put(snapshot.instanceId(), snapshot);
				if (!snapshot.equals(state.snapshots.get(snapshot.instanceId()))) {
					send(player, snapshot);
				}
			}

			for (var id : List.copyOf(state.snapshots.keySet())) {
				if (!visible.containsKey(id)) {
					var previous = state.snapshots.get(id);
					PacketDistributor.sendToPlayer(player, new LcAnimationRemovePayload(id,
							previous.stopped() ? previous.termination() : LcAnimationTermination.UNAVAILABLE));
				}
			}

			state.snapshots.clear();
			state.snapshots.putAll(visible);
			syncAttachments(level, player, state, snapshots, attachments, items);
		}
	}

	public static void sync(Entity entity) {
		if (entity.level() instanceof ServerLevel level) {
			sync(level);
		}
	}

	public static void remove(Entity entity, UUID instanceId) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		for (var player : level.players()) {
			var state = state(player);
			if (state.snapshots.remove(instanceId) != null) {
				PacketDistributor.sendToPlayer(player, new LcAnimationRemovePayload(instanceId,
						LcAnimationTermination.UNAVAILABLE));
			}
		}
	}

	public static void clear(Entity entity) {
		for (var snapshot : LcServerAnimations.snapshots(entity)) {
			remove(entity, snapshot.instanceId());
		}

		TRACKERS.remove(entity);
	}

	public static void clear(ServerLevel level) {
		TRACKERS.keySet().removeIf(entity -> entity.level() == level);
		for (var player : level.players()) {
			var state = PLAYERS.remove(player.connection);
			if (state == null) {
				continue;
			}

			for (var id : state.snapshots.keySet()) {
				PacketDistributor.sendToPlayer(player, new LcAnimationRemovePayload(id,
						LcAnimationTermination.WORLD_CLEAR));
			}

			PacketDistributor.sendToPlayer(player, new LcAttachmentStatePayload(true, level.getGameTime(),
					List.of(), Set.of(), List.of()));
		}
	}

	public static void clear(MinecraftServer server) {
		TRACKERS.keySet().removeIf(entity -> entity.level() instanceof ServerLevel level && level.getServer() == server);
		PLAYERS.keySet().removeIf(connection -> connection.player.level().getServer() == server);
	}

	private static boolean visibleTo(ServerLevel level, ServerPlayer player, LcAnimationSnapshot snapshot) {
		if (snapshot.ownerEntityId() < 0 || snapshot.ownerEntityId() == player.getId()) {
			return true;
		}

		var owner = level.getEntity(snapshot.ownerEntityId());
		return owner != null && TRACKERS.getOrDefault(owner, Set.of()).contains(player.getUUID());
	}

	private static void syncAttachments(ServerPlayer player) {
		var level = player.level();
		var state = state(player);
		var animations = LcServerAnimations.snapshots(level);
		for (var snapshot : animations) {
			if (visibleTo(level, player, snapshot) && !snapshot.equals(state.snapshots.get(snapshot.instanceId()))) {
				send(player, snapshot);
			}
		}

		syncAttachments(level, player, state, animations,
				LcAttachments.snapshots(level), LcAttachedItems.snapshots(level));
	}

	private static void syncAttachments(ServerLevel level, ServerPlayer player, PlayerState state,
	                                    List<LcAnimationSnapshot> animations, List<LcAttachmentSnapshot> attachments,
	                                    List<LcAttachedItemSnapshot> items) {
		Map<UUID, LcAttachmentSnapshot> visible = new LinkedHashMap<>();
		for (var snapshot : attachments) {
			if (visibleTo(level, player, snapshot, animations)) {
				visible.put(snapshot.id(), snapshot);
			}
		}

		var removed = new HashSet<>(state.attachments);
		removed.removeAll(visible.keySet());
		if (!state.attachmentsInitialized || !visible.isEmpty() || !removed.isEmpty()) {
			var visibleItems = items.stream().filter(item -> visible.containsKey(item.bindingId())).toList();
			PacketDistributor.sendToPlayer(player, new LcAttachmentStatePayload(!state.attachmentsInitialized,
					level.getGameTime(), List.copyOf(visible.values()), removed, visibleItems));
		}

		state.attachments.clear();
		state.attachments.addAll(visible.keySet());
		state.attachmentsInitialized = true;
	}

	private static boolean visibleTo(ServerLevel level, ServerPlayer player, LcAttachmentSnapshot snapshot,
	                                 List<LcAnimationSnapshot> animations) {
		var anchor = snapshot.anchor();
		var visible = switch (anchor.kind()) {
			case WORLD -> true;
			case ENTITY -> visibleEntity(level, player, anchor.entityId(), anchor.sourceId());
			case ANIMATION -> animations.stream().anyMatch(animation ->
					animation.instanceId().equals(anchor.sourceId()) && visibleTo(level, player, animation));
		};
		if (!visible) {
			return false;
		}

		var render = snapshot.renderAnchor();
		return render == null || render.entityId() == 0
				|| visibleEntity(level, player, render.entityId(), render.sourceId());
	}

	private static boolean visibleEntity(ServerLevel level, ServerPlayer player, int entityId, @Nullable UUID sourceId) {
		var entity = level.getEntity(entityId);
		if (entity == null || !entity.getUUID().equals(sourceId)) {
			return false;
		}

		return entity == player || TRACKERS.getOrDefault(entity, Set.of()).contains(player.getUUID());
	}

	private static PlayerState state(ServerPlayer player) {
		var state = PLAYERS.computeIfAbsent(player.connection, _ -> new PlayerState());
		if (!player.level().dimension().equals(state.dimension)) {
			state.snapshots.clear();
			state.attachments.clear();
			state.attachmentsInitialized = false;
			state.dimension = player.level().dimension();
		}

		return state;
	}

	private static void send(ServerPlayer player, LcAnimationSnapshot snapshot) {
		var state = state(player);
		if (state.content.add(snapshot.content().version())) {
			sendContent(player, snapshot.content());
		}

		PacketDistributor.sendToPlayer(player, LcAnimationStatePayload.of(snapshot));
		state.snapshots.put(snapshot.instanceId(), snapshot);
	}

	private static void sendContent(ServerPlayer player, LcAnimationContent content) {
		var json = new JsonObject();
		json.addProperty("model", content.modelJson());
		json.addProperty("animation", content.animationJson());
		var bytes = json.toString().getBytes(StandardCharsets.UTF_8);
		var count = Math.ceilDiv(bytes.length, LcAnimationContentPayload.CHUNK_BYTES);
		for (int index = 0; index < count; index++) {
			var start = index * LcAnimationContentPayload.CHUNK_BYTES;
			var part = Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + LcAnimationContentPayload.CHUNK_BYTES));
			PacketDistributor.sendToPlayer(player, new LcAnimationContentPayload(content.modelId(), content.animationId(),
					content.version(), index, count, part));
		}
	}

	private static class PlayerState {
		private @Nullable ResourceKey<Level> dimension;
		private final Set<String> content = new HashSet<>();
		private final Map<UUID, LcAnimationSnapshot> snapshots = new HashMap<>();
		private final Set<UUID> attachments = new HashSet<>();
		private boolean attachmentsInitialized;
	}
}
