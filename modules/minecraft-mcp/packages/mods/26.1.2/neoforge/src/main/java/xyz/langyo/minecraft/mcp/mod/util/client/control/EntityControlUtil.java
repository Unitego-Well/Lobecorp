package xyz.langyo.minecraft.mcp.mod.util.client.control;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Set;
import java.util.UUID;

import static xyz.langyo.minecraft.mcp.mod.util.client.control.ControlRequestUtil.*;

public class EntityControlUtil {
	public static JsonObject handle(JsonObject request) {
		String action = text(request, "action", "list");
		if (action.equals("list") || action.equals("get")) {
			return client(false, () -> query(request, action));
		}
		String mode = text(request, "mode", "simulate");
		if (mode.equals("simulate")) {
			return client(true, () -> {
				var minecraft = Minecraft.getInstance();
				require(minecraft.level != null && minecraft.player != null, "NO_WORLD", "Enter a world first.");
				var entity = minecraft.level.entitiesForRendering().iterator();
				UUID uuid = UUID.fromString(text(request, "uuid"));
				while (entity.hasNext()) {
					var candidate = entity.next();
					if (candidate.getUUID().equals(uuid)) {
						return simulate(minecraft, candidate, action, request);
					}
				}
				throw new IllegalArgumentException("Entity is not loaded in this client.");
			});
		}
		require(mode.equals("direct"), "INVALID_REQUEST", "Unknown entity mode.");
		return server(true, player -> direct(player, request, action));
	}

	private static JsonObject query(JsonObject request, String action) {
		var minecraft = Minecraft.getInstance();
		require(minecraft.level != null && minecraft.player != null, "NO_WORLD", "Enter a world first.");
		UUID uuid = action.equals("get") ? UUID.fromString(text(request, "uuid")) : null;
		var center = request.has("center") ? position(request, "center") : minecraft.player.position();
		double radius = request.has("radius") ? number(request, "radius", 0, 128) : 32;
		int limit = request.has("limit") ? integer(request, "limit", 1, MAX_QUERY_RESULTS) : 64;
		String type = text(request, "type", "");
		var matches = new JsonArray();
		boolean truncated = false;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (uuid != null && !entity.getUUID().equals(uuid)) {
				continue;
			}
			if (uuid == null && (entity.position().distanceToSqr(center) > radius * radius
					|| !type.isEmpty() && !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(type))) {
				continue;
			}
			if (action.equals("get")) {
				return entityState(entity);
			}
			if (matches.size() == limit) {
				truncated = true;
				break;
			}
			matches.add(entityState(entity));
		}
		require(uuid == null, "ENTITY_NOT_FOUND", "Entity is not loaded in this client.");
		var data = new JsonObject();
		data.add("entities", matches);
		data.addProperty("truncated", truncated);
		data.addProperty("source", "client_loaded_entities");
		return data;
	}

	private static JsonObject direct(ServerPlayer player, JsonObject request, String action) {
		var level = player.level();
		if (action.equals("spawn")) {
			var position = position(request, "position");
			var blockPos = BlockPos.containing(position);
			require(level.isInWorldBounds(blockPos) && loaded(level, blockPos),
					"UNLOADED_POSITION", "Spawn position must be inside a loaded world chunk.");
			var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(text(request, "type")))
					.orElseThrow(() -> new IllegalArgumentException("Unknown entity type."));
			var entity = type.create(level, null, blockPos, EntitySpawnReason.COMMAND, false, false);
			require(entity != null && !(entity instanceof Player), "SPAWN_REJECTED", "Entity cannot be spawned.");
			entity.snapTo(position.x, position.y, position.z, entity.getYRot(), entity.getXRot());
			require(level.addFreshEntity(entity), "SPAWN_REJECTED", "World rejected the entity.");
			return entityState(entity);
		}
		var entity = level.getEntity(UUID.fromString(text(request, "uuid")));
		require(entity != null && !entity.isRemoved(), "ENTITY_NOT_FOUND", "Entity is not loaded in this dimension.");
		require(!(entity instanceof Player), "PLAYER_TARGET", "Use player_control for the local player.");
		switch (action) {
			case "teleport" -> {
				var position = position(request, "position");
				var blockPos = BlockPos.containing(position);
				require(level.isInWorldBounds(blockPos) && loaded(level, blockPos),
						"UNLOADED_POSITION", "Destination must be inside a loaded world chunk.");
				entity.teleportTo(position.x, position.y, position.z);
			}
			case "set_state" -> setState(entity, object(request, "state"));
			case "navigate" -> {
				require(entity instanceof Mob, "NOT_A_MOB", "Navigation requires a Mob.");
				var position = position(request, "position");
				require(level.isInWorldBounds(BlockPos.containing(position))
								&& loaded(level, BlockPos.containing(position)),
						"UNLOADED_POSITION", "Navigation destination must be in a loaded world chunk.");
				double speed = number(request, "speed", 0.01, 4);
				return accepted(((Mob) entity).getNavigation().moveTo(position.x, position.y, position.z, speed));
			}
			case "stop" -> {
				require(entity instanceof Mob, "NOT_A_MOB", "Stopping navigation requires a Mob.");
				((Mob) entity).getNavigation().stop();
			}
			case "remove" -> entity.discard();
			default -> throw new IllegalArgumentException("Unsupported direct entity action.");
		}
		return entityState(entity);
	}

	private static void setState(Entity entity, JsonObject state) {
		require(!state.entrySet().isEmpty() && Set.of("name", "health", "noAi", "invulnerable", "glowing").containsAll(state.keySet()),
				"INVALID_REQUEST", "Unsupported or empty entity state.");
		String name = state.has("name") ? text(state, "name") : null;
		if (name != null) {
			require(name.length() <= 256, "INVALID_REQUEST", "Name is too long.");
		}
		if (state.has("health")) {
			require(entity instanceof LivingEntity, "NOT_LIVING", "Health requires a living entity.");
			number(state, "health", 0, ((LivingEntity) entity).getMaxHealth());
		}
		if (state.has("noAi")) {
			require(entity instanceof Mob, "NOT_A_MOB", "AI state requires a Mob.");
		}
		for (String key : new String[]{"noAi", "invulnerable", "glowing"}) {
			if (state.has(key)) {
				bool(state, key);
			}
		}
		if (name != null) {
			entity.setCustomName(name.isEmpty() ? null : Component.literal(name));
		}
		if (state.has("health")) {
			((LivingEntity) entity).setHealth(state.get("health").getAsFloat());
		}
		if (state.has("noAi")) {
			((Mob) entity).setNoAi(bool(state, "noAi"));
		}
		if (state.has("invulnerable")) {
			entity.setInvulnerable(bool(state, "invulnerable"));
		}
		if (state.has("glowing")) {
			entity.setGlowingTag(bool(state, "glowing"));
		}
	}

	public static JsonObject simulate(Minecraft minecraft, Entity entity, String action, JsonObject request) {
		require(minecraft.player != null && minecraft.gameMode != null && minecraft.screen == null,
				"PLAYER_NOT_CONTROLLABLE", "Close the GUI and enter a world first.");
		require(!entity.isRemoved() && entity != minecraft.player, "INVALID_TARGET", "Target is unavailable.");
		boolean attack = action.equals("attack");
		require(attack || action.equals("interact"), "INVALID_REQUEST", "Simulated entity action must be attack or interact.");
		var hand = PlayerControlUtil.hand(request);
		require(!attack || hand == InteractionHand.MAIN_HAND, "INVALID_REQUEST", "Attacks use the main hand.");
		boolean reachable = attack
				? minecraft.player.isWithinAttackRange(minecraft.player.getMainHandItem(), entity.getBoundingBox(), 0)
				: minecraft.player.isWithinEntityInteractionRange(entity, 0);
		require(reachable && minecraft.player.hasLineOfSight(entity), "OUT_OF_REACH", "Target is obstructed or out of reach.");
		require(minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity,
				"NOT_AIMED_AT_TARGET", "Aim at the target before simulating interaction.");
		if (attack) {
			minecraft.gameMode.attack(minecraft.player, entity);
			minecraft.player.swing(InteractionHand.MAIN_HAND);
			return accepted(true);
		}
		var result = minecraft.gameMode.interact(minecraft.player, entity, (EntityHitResult) minecraft.hitResult, hand);
		return accepted(result.consumesAction());
	}
}
