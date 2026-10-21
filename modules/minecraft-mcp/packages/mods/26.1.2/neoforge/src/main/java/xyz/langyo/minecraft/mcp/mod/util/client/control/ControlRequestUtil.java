package xyz.langyo.minecraft.mcp.mod.util.client.control;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public class ControlRequestUtil {
	private static final int REQUEST_TIMEOUT_SECONDS = 5;
	public static final int MAX_QUERY_RESULTS = 256;
	public static final int MAX_FILL_BLOCKS = 4096;
	public static final int MAX_INPUT_TICKS = 200;

	public static JsonObject client(boolean mutation, Supplier<JsonObject> operation) {
		var minecraft = Minecraft.getInstance();
		var result = new CompletableFuture<JsonObject>();
		if (minecraft.isSameThread()) {
			complete(result, mutation, operation);
		} else {
			minecraft.execute(() -> complete(result, mutation, operation));
		}
		return await(result);
	}

	public static JsonObject server(boolean mutation, Function<ServerPlayer, JsonObject> operation) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.isSameThread()) {
			return failure("WRONG_THREAD", "Server requests must originate outside the client thread.");
		}
		var result = new CompletableFuture<JsonObject>();
		minecraft.execute(() -> {
			if (result.isDone()) {
				return;
			}
			try {
				checkControl(mutation);
				var server = minecraft.getSingleplayerServer();
				require(server != null && minecraft.player != null, "INTEGRATED_SERVER_REQUIRED",
						"Direct world mutation requires this client's integrated server.");
				var owner = minecraft.player.getUUID();
				var clientLevel = minecraft.player.level();
				var dimension = clientLevel.dimension();
				server.execute(() -> complete(result, mutation, () -> {
					var player = server.getPlayerList().getPlayer(owner);
					var playerLevel = player == null ? null : player.level();
					require(playerLevel != null && playerLevel.dimension().equals(dimension),
							"CONTEXT_CHANGED", "Player or dimension changed before execution.");
					return operation.apply(player);
				}));
			} catch (RuntimeException exception) {
				result.complete(error(exception));
			}
		});
		return await(result);
	}

	private static void complete(CompletableFuture<JsonObject> result, boolean mutation, Supplier<JsonObject> operation) {
		if (result.isDone()) {
			return;
		}
		try {
			checkControl(mutation);
			var response = new JsonObject();
			response.addProperty("ok", true);
			response.add("data", operation.get());
			result.complete(response);
		} catch (RuntimeException exception) {
			result.complete(error(exception));
		}
	}

	private static JsonObject await(CompletableFuture<JsonObject> result) {
		try {
			return result.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			result.cancel(false);
			return failure("INTERRUPTED", "Completion is unknown; do not automatically retry mutations.");
		} catch (TimeoutException exception) {
			result.cancel(false);
			return failure("TIMEOUT", "Completion is unknown; do not automatically retry mutations.");
		} catch (ExecutionException exception) {
			return failure("EXECUTION_FAILED", String.valueOf(exception.getCause()));
		}
	}

	private static void checkControl(boolean mutation) {
		require(!mutation || ControlModeHelper.isMcpControlMode(), "CONTROL_REQUIRED", "Enter MCP control mode first.");
	}

	private static JsonObject error(RuntimeException exception) {
		return failure(exception instanceof RequestFailure request ? request.code : "INVALID_REQUEST",
				String.valueOf(exception.getMessage()));
	}

	public static JsonObject failure(String code, String message) {
		var error = new JsonObject();
		error.addProperty("code", code);
		error.addProperty("message", message);
		var response = new JsonObject();
		response.addProperty("ok", false);
		response.add("error", error);
		return response;
	}

	public static void require(boolean condition, String code, String message) {
		if (!condition) {
			throw new RequestFailure(code, message);
		}
	}

	public static String text(JsonObject request, String key) {
		require(request.has(key) && request.get(key).isJsonPrimitive()
				&& request.getAsJsonPrimitive(key).isString(), "INVALID_REQUEST", "Expected text: " + key);
		return request.get(key).getAsString();
	}

	public static String text(JsonObject request, String key, String fallback) {
		return request.has(key) ? text(request, key) : fallback;
	}

	public static double number(JsonObject request, String key, double minimum, double maximum) {
		require(request.has(key) && request.get(key).isJsonPrimitive()
				&& request.getAsJsonPrimitive(key).isNumber(), "INVALID_REQUEST", "Expected number: " + key);
		double value = request.get(key).getAsDouble();
		require(Double.isFinite(value) && value >= minimum && value <= maximum,
				"INVALID_REQUEST", "Number outside permitted range: " + key);
		return value;
	}

	public static int integer(JsonObject request, String key, int minimum, int maximum) {
		double value = number(request, key, minimum, maximum);
		require(value == Math.rint(value), "INVALID_REQUEST", "Expected integer: " + key);
		return (int) value;
	}

	public static boolean bool(JsonObject request, String key) {
		require(request.has(key) && request.get(key).isJsonPrimitive()
				&& request.getAsJsonPrimitive(key).isBoolean(), "INVALID_REQUEST", "Expected boolean: " + key);
		return request.get(key).getAsBoolean();
	}

	public static JsonObject object(JsonObject request, String key) {
		require(request.has(key) && request.get(key).isJsonObject(), "INVALID_REQUEST", "Expected object: " + key);
		return request.getAsJsonObject(key);
	}

	public static Vec3 position(JsonObject request, String key) {
		var value = object(request, key);
		return new Vec3(number(value, "x", -30000000, 30000000),
				number(value, "y", -30000000, 30000000), number(value, "z", -30000000, 30000000));
	}

	public static BlockPos blockPosition(JsonObject request, String key) {
		var value = object(request, key);
		return new BlockPos(integer(value, "x", -30000000, 30000000),
				integer(value, "y", -30000000, 30000000), integer(value, "z", -30000000, 30000000));
	}

	public static boolean loaded(Level level, BlockPos position) {
		return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(position.getX()),
				SectionPos.blockToSectionCoord(position.getZ()));
	}

	public static JsonObject coordinates(Vec3 position) {
		var data = new JsonObject();
		data.addProperty("x", position.x);
		data.addProperty("y", position.y);
		data.addProperty("z", position.z);
		return data;
	}

	public static JsonObject entityState(Entity entity) {
		var data = new JsonObject();
		data.addProperty("uuid", entity.getUUID().toString());
		data.addProperty("entityId", entity.getId());
		data.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		data.addProperty("name", entity.getName().getString());
		data.add("position", coordinates(entity.position()));
		data.addProperty("yaw", entity.getYRot());
		data.addProperty("pitch", entity.getXRot());
		data.addProperty("alive", entity.isAlive());
		data.addProperty("invulnerable", entity.isInvulnerable());
		data.addProperty("glowing", entity.isCurrentlyGlowing());
		if (entity instanceof LivingEntity living) {
			data.addProperty("health", living.getHealth());
			data.addProperty("maxHealth", living.getMaxHealth());
		}
		return data;
	}

	public static JsonObject accepted(boolean value) {
		var data = new JsonObject();
		data.addProperty("accepted", value);
		return data;
	}

	private static class RequestFailure extends IllegalArgumentException {
		private final String code;

		private RequestFailure(String code, String message) {
			super(message);
			this.code = code;
		}
	}
}
