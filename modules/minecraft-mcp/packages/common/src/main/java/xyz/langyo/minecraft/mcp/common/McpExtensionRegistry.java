package xyz.langyo.minecraft.mcp.common;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/// 承载其他模组提供的结构化命令；处理器自行维护游戏线程与对象生命周期。
public class McpExtensionRegistry {

	private static final Set<String> NATIVE_COMMANDS = Set.of(
			"ping", "screenshot", "screenshot_to_file", "overlay_click", "enter_control_mode",
			"exit_control_mode", "click", "press_key", "type_text", "scroll",
			"scroll_at", "direct_scroll", "select_list_item", "mouse_drag", "drag",
			"hotkey", "execute_command", "get_player_info", "get_world_info", "debug_fields",
			"get_screen_buttons", "click_button_id", "click_button_index", "enumerate_widgets", "call_screen_method",
			"paste_text", "set_view_angle", "look_delta", "right_click", "use_item",
			"place_block", "pause_game", "open_chat", "close_screen", "release_mouse",
			"set_gamemode", "switch_tab");
	private static final Map<String, Registration> COMMANDS = new ConcurrentHashMap<>();

	/// 注册仅属于当前进程的命令，禁止覆盖原生命令或已有处理器。
	///
	/// 处理器返回 `{ok,data/error}`；需要写权限的命令沿用 MCP 接管状态。
	public static void register(String command, boolean requiresControl, Function<JsonObject, JsonObject> handler) {
		if (command == null || command.isBlank() || !command.equals(command.strip())) {
			throw new IllegalArgumentException(Failure.INVALID_COMMAND.message);
		}

		Objects.requireNonNull(handler, Failure.INVALID_HANDLER.message);
		if (NATIVE_COMMANDS.contains(command)) {
			throw new IllegalArgumentException(Failure.RESERVED_COMMAND.message + command);
		}

		var registration = new Registration(requiresControl, handler);
		if (COMMANDS.putIfAbsent(command, registration) != null) {
			throw new IllegalArgumentException(Failure.DUPLICATE_COMMAND.message + command);
		}
	}

	public static boolean contains(String command) {
		return command != null && COMMANDS.containsKey(command);
	}

	/// 校验接管权限后分发原始 JSON；HTTP 层与直接调用共用同一权限边界。
	public static JsonObject dispatch(String command, JsonObject payload) {
		var registration = command == null ? null : COMMANDS.get(command);
		if (registration == null) {
			return error(Failure.UNKNOWN_COMMAND, null);
		}

		if (registration.requiresControl() && !ControlModeHelper.isMcpControlMode()) {
			return error(Failure.CONTROL_REQUIRED, null);
		}

		try {
			var result = registration.handler().apply(payload == null ? new JsonObject() : payload.deepCopy());
			if (result == null || !result.has("ok") || !result.get("ok").isJsonPrimitive()
					|| !result.getAsJsonPrimitive("ok").isBoolean()) {
				return error(Failure.INVALID_RESPONSE, null);
			}

			return result;
		} catch (Exception exception) {
			return error(Failure.HANDLER_FAILED, exception.getMessage());
		}
	}

	private static JsonObject error(Failure failure, String detail) {
		var error = new JsonObject();
		error.addProperty("code", failure.name());
		error.addProperty("message", detail == null || detail.isBlank() ? failure.message : detail);
		var result = new JsonObject();
		result.addProperty("ok", false);
		result.add("error", error);
		return result;
	}

	private record Registration(boolean requiresControl, Function<JsonObject, JsonObject> handler) {
	}

	private enum Failure {
		INVALID_COMMAND("Command must be nonblank without surrounding whitespace."),
		INVALID_HANDLER("Command handler must not be null."),
		RESERVED_COMMAND("Native command cannot be replaced: "),
		DUPLICATE_COMMAND("Command is already registered: "),
		UNKNOWN_COMMAND("Command is not registered."),
		CONTROL_REQUIRED("Enter MCP control mode before calling this command."),
		INVALID_RESPONSE("Command handler must return an object with a boolean ok field."),
		HANDLER_FAILED("Command handler failed.");

		private final String message;

		@SuppressWarnings("UnnecessaryModifier") // 项目规范要求枚举构造器保留显式可见性。
		private Failure(String message) {
			this.message = message;
		}
	}
}
