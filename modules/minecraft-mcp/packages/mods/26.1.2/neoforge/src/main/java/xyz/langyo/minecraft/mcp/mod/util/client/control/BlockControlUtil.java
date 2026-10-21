package xyz.langyo.minecraft.mcp.mod.util.client.control;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static xyz.langyo.minecraft.mcp.mod.util.client.control.ControlRequestUtil.*;

public class BlockControlUtil {
	public static JsonObject handle(JsonObject request) {
		String action = text(request, "action", "get");
		if (action.equals("get")) {
			return client(false, () -> {
				var minecraft = Minecraft.getInstance();
				require(minecraft.level != null, "NO_WORLD", "Enter a world first.");
				var position = blockPosition(request, "position");
				require(minecraft.level.isInWorldBounds(position) && loaded(minecraft.level, position),
						"UNLOADED_POSITION", "Block is outside a loaded world chunk.");
				return describe(minecraft.level, position);
			});
		}
		String mode = text(request, "mode", "simulate");
		if (mode.equals("simulate")) {
			return client(true, () -> simulate(request, action));
		}
		require(mode.equals("direct"), "INVALID_REQUEST", "Unknown block mode.");
		return server(true, player -> direct(player, request, action));
	}

	private static JsonObject direct(ServerPlayer player, JsonObject request, String action) {
		var level = player.level();
		var from = blockPosition(request, "position");
		var to = action.equals("fill") ? blockPosition(request, "to") : from;
		require(action.equals("set") || action.equals("fill") || action.equals("break"),
				"INVALID_REQUEST", "Unsupported direct block action.");
		long width = Math.abs((long) from.getX() - to.getX()) + 1;
		long height = Math.abs((long) from.getY() - to.getY()) + 1;
		long depth = Math.abs((long) from.getZ() - to.getZ()) + 1;
		require(width <= MAX_FILL_BLOCKS && height <= MAX_FILL_BLOCKS && depth <= MAX_FILL_BLOCKS,
				"REGION_TOO_LARGE", "Fill exceeds " + MAX_FILL_BLOCKS + " blocks.");
		long volume = width * height * depth;
		require(volume <= MAX_FILL_BLOCKS, "REGION_TOO_LARGE", "Fill exceeds " + MAX_FILL_BLOCKS + " blocks.");
		for (var position : BlockPos.betweenClosed(from, to)) {
			require(level.isInWorldBounds(position) && loaded(level, position),
					"UNLOADED_POSITION", "Every block must be inside loaded world bounds.");
		}
		boolean drops = request.has("drops") && bool(request, "drops");
		var target = action.equals("break") ? null : state(request);
		int changed = 0;
		for (var position : BlockPos.betweenClosed(from, to)) {
			boolean applied = action.equals("break") ? level.destroyBlock(position, drops, player)
					: level.setBlock(position, target, Block.UPDATE_ALL);
			if (applied) {
				changed++;
			}
		}
		var data = new JsonObject();
		data.addProperty("changed", changed);
		data.addProperty("requested", volume);
		return data;
	}

	private static BlockState state(JsonObject request) {
		var block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(text(request, "block")))
				.orElseThrow(() -> new IllegalArgumentException("Unknown block."));
		var state = block.defaultBlockState();
		if (request.has("properties")) {
			for (var entry : object(request, "properties").entrySet()) {
				var property = block.getStateDefinition().getProperty(entry.getKey());
				require(property != null && entry.getValue().isJsonPrimitive()
						&& entry.getValue().getAsJsonPrimitive().isString(), "INVALID_REQUEST", "Invalid block property.");
				state = property(state, property, entry.getValue().getAsString());
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState property(BlockState state, Property<T> property, String value) {
		var parsed = property.getValue(value).orElseThrow(() -> new IllegalArgumentException("Invalid property value: " + value));
		return state.setValue(property, parsed);
	}

	private static JsonObject describe(BlockGetter level, BlockPos position) {
		var state = level.getBlockState(position);
		var data = new JsonObject();
		data.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		data.add("position", coordinates(Vec3.atLowerCornerOf(position)));
		var properties = new JsonObject();
		state.getValues().forEach(value -> properties.addProperty(value.property().getName(), value.valueName()));
		data.add("properties", properties);
		data.addProperty("hasBlockEntity", level.getBlockEntity(position) != null);
		data.addProperty("source", "client_loaded_blocks");
		return data;
	}

	private static JsonObject simulate(JsonObject request, String action) {
		var minecraft = Minecraft.getInstance();
		require(minecraft.player != null && minecraft.gameMode != null && minecraft.screen == null,
				"PLAYER_NOT_CONTROLLABLE", "Close the GUI and enter a world first.");
		if (action.equals("stop_break")) {
			minecraft.gameMode.stopDestroyBlock();
			return accepted(true);
		}
		var position = blockPosition(request, "position");
		require(minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(position),
				"NOT_AIMED_AT_TARGET", "Aim at this block before simulating interaction.");
		require(minecraft.player.isWithinBlockInteractionRange(position, 0), "OUT_OF_REACH", "Block is out of reach.");
		var hit = (BlockHitResult) minecraft.hitResult;
		return switch (action) {
			case "start_break" -> accepted(minecraft.gameMode.startDestroyBlock(position, hit.getDirection()));
			case "continue_break" -> accepted(minecraft.gameMode.continueDestroyBlock(position, hit.getDirection()));
			case "use", "place" -> accepted(minecraft.gameMode.useItemOn(minecraft.player,
					PlayerControlUtil.hand(request), hit).consumesAction());
			default -> throw new IllegalArgumentException("Unsupported simulated block action.");
		};
	}
}
