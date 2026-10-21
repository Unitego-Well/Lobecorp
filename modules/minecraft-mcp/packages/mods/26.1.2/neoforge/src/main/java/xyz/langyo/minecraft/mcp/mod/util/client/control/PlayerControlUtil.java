package xyz.langyo.minecraft.mcp.mod.util.client.control;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static xyz.langyo.minecraft.mcp.mod.util.client.control.ControlRequestUtil.*;

public class PlayerControlUtil {
	private static final Map<KeyMapping, Integer> HELD_KEYS = new HashMap<>();
	private static LocalPlayer inputOwner;
	private static ClientLevel inputLevel;

	public static JsonObject handle(JsonObject request) {
		String action = text(request, "action", "get");
		if (action.equals("get") || action.equals("inventory")) {
			return client(false, () -> {
				var player = Minecraft.getInstance().player;
				require(player != null, "NO_PLAYER", "Enter a world first.");
				var data = entityState(player);
				data.addProperty("food", player.getFoodData().getFoodLevel());
				data.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
				if (action.equals("inventory")) {
					var items = new JsonArray();
					for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
						var item = player.getInventory().getItem(slot);
						var entry = new JsonObject();
						entry.addProperty("slot", slot);
						entry.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
						entry.addProperty("count", item.getCount());
						items.add(entry);
					}
					data.add("items", items);
				}
				return data;
			});
		}
		if (text(request, "mode", "simulate").equals("simulate")) {
			return client(!action.equals("release"), () -> simulate(request, action));
		}
		require(text(request, "mode").equals("direct"), "INVALID_REQUEST", "Unknown player mode.");
		return server(true, player -> direct(player, request, action));
	}

	private static JsonObject direct(ServerPlayer player, JsonObject request, String action) {
		var level = player.level();
		switch (action) {
			case "teleport" -> {
				var position = position(request, "position");
				require(level.isInWorldBounds(BlockPos.containing(position)),
						"OUT_OF_WORLD", "Destination is outside world bounds.");
				player.teleportTo(position.x, position.y, position.z);
			}
			case "set_state" -> {
				var state = object(request, "state");
				require(!state.entrySet().isEmpty() && Set.of("health", "food", "saturation").containsAll(state.keySet()),
						"INVALID_REQUEST", "State supports health, food and saturation.");
				float health = state.has("health") ? (float) number(state, "health", 0, player.getMaxHealth()) : player.getHealth();
				int food = state.has("food") ? integer(state, "food", 0, 20) : player.getFoodData().getFoodLevel();
				float saturation = state.has("saturation") ? (float) number(state, "saturation", 0, food)
						: Math.min(player.getFoodData().getSaturationLevel(), food);
				player.setHealth(health);
				player.getFoodData().setFoodLevel(food);
				player.getFoodData().setSaturation(saturation);
			}
			case "gamemode" -> {
				String mode = text(request, "gamemode");
				require(Set.of("survival", "creative", "adventure", "spectator").contains(mode),
						"INVALID_REQUEST", "Unknown game mode.");
				var gameType = GameType.byName(mode);
				require(player.gameMode.getGameModeForPlayer() == gameType || player.setGameMode(gameType),
						"ACTION_REJECTED", "Game mode change was rejected.");
			}
			case "set_slot" -> {
				int slot = integer(request, "slot", 0, player.getInventory().getContainerSize() - 1);
				var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(text(request, "item")))
						.orElseThrow(() -> new IllegalArgumentException("Unknown item."));
				int count = integer(request, "count", 0, item.getDefaultMaxStackSize());
				player.getInventory().setItem(slot, count == 0 ? ItemStack.EMPTY : new ItemStack(item, count));
				player.getInventory().setChanged();
				player.connection.send(player.getInventory().createInventoryUpdatePacket(slot));
			}
			case "select_slot" -> {
				int slot = integer(request, "slot", 0, 8);
				player.getInventory().setSelectedSlot(slot);
				player.connection.send(new ClientboundSetHeldSlotPacket(slot));
			}
			default -> throw new IllegalArgumentException("Unsupported direct player action.");
		}
		return entityState(player);
	}

	private static JsonObject simulate(JsonObject request, String action) {
		var minecraft = Minecraft.getInstance();
		if (action.equals("release")) {
			release();
			return accepted(true);
		}
		require(minecraft.player != null && minecraft.gameMode != null && minecraft.screen == null,
				"PLAYER_NOT_CONTROLLABLE", "Close the GUI and enter a world first.");
		var player = minecraft.player;
		switch (action) {
			case "input" -> {
				var keys = object(request, "keys");
				var mappings = mappings(minecraft);
				require(!keys.entrySet().isEmpty() && mappings.keySet().containsAll(keys.keySet()),
						"INVALID_REQUEST", "Unknown or empty input set.");
				int ticks = integer(request, "ticks", 1, MAX_INPUT_TICKS);
				for (var entry : keys.entrySet()) {
					bool(keys, entry.getKey());
				}
				if (inputOwner != player) {
					release();
				}
				inputOwner = player;
				inputLevel = minecraft.level;
				for (var entry : keys.entrySet()) {
					var mapping = mappings.get(entry.getKey());
					boolean down = bool(keys, entry.getKey());
					boolean wasDown = mapping.isDown();
					mapping.setDown(down);
					if (down && !wasDown && (entry.getKey().equals("attack") || entry.getKey().equals("use"))) {
						KeyMapping.click(mapping.getKey());
					}
					if (down) {
						HELD_KEYS.put(mapping, ticks);
					} else {
						clearKey(mapping);
						HELD_KEYS.remove(mapping);
					}
				}
			}
			case "look" -> {
				player.setYRot((float) number(request, "yaw", -180, 180));
				player.setXRot((float) number(request, "pitch", -90, 90));
			}
			case "select_slot" -> player.getInventory().setSelectedSlot(integer(request, "slot", 0, 8));
			case "attack" -> {
				var target = minecraft.hitResult;
				require(target instanceof EntityHitResult, "NO_ENTITY_TARGET", "Aim at an entity.");
				return EntityControlUtil.simulate(minecraft, ((EntityHitResult) target).getEntity(), "attack", request);
			}
			case "use" -> {
				var result = minecraft.gameMode.useItem(player, hand(request));
				return accepted(result.consumesAction());
			}
			default -> throw new IllegalArgumentException("Unsupported simulated player action.");
		}
		return accepted(true);
	}

	public static InteractionHand hand(JsonObject request) {
		String hand = text(request, "hand", "main");
		require(hand.equals("main") || hand.equals("off"), "INVALID_REQUEST", "Unknown hand.");
		return hand.equals("main") ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	public static void checkContext() {
		var minecraft = Minecraft.getInstance();
		if (!HELD_KEYS.isEmpty() && (!ControlModeHelper.isMcpControlMode()
				|| minecraft.player != inputOwner || minecraft.level != inputLevel || minecraft.screen != null)) {
			release();
		}
	}

	public static void tick() {
		checkContext();
		if (HELD_KEYS.isEmpty()) {
			inputOwner = null;
			inputLevel = null;
			return;
		}
		var iterator = HELD_KEYS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int remaining = entry.getValue() - 1;
			if (remaining <= 0) {
				clearKey(entry.getKey());
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
	}

	private static void release() {
		for (var key : HELD_KEYS.keySet()) {
			clearKey(key);
		}
		HELD_KEYS.clear();
		inputOwner = null;
		inputLevel = null;
	}

	@SuppressWarnings("StatementWithEmptyBody")
	private static void clearKey(KeyMapping key) {
		key.setDown(false);
		while (key.consumeClick()) {
			// 清除本工具尚未被游戏消费的按下事件，避免交回控制后补发攻击或使用。
		}
	}

	private static Map<String, KeyMapping> mappings(Minecraft minecraft) {
		var options = minecraft.options;
		return Map.of("forward", options.keyUp, "backward", options.keyDown, "left", options.keyLeft,
				"right", options.keyRight, "jump", options.keyJump, "sneak", options.keyShift,
				"sprint", options.keySprint, "attack", options.keyAttack, "use", options.keyUse);
	}
}
