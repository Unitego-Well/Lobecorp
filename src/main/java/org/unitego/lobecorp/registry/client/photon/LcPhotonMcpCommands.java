package org.unitego.lobecorp.registry.client.photon;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectAutomation;
import org.unitego.lobecorp.client.photon.performance.PhotonRepeatedEffectTrial;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;
import xyz.langyo.minecraft.mcp.common.McpExtensionRegistry;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LcPhotonMcpCommands {
	public static final String MOD_ID = "mcpmod";
	private static final List<String> READ_COMMANDS = List.of("effect_capabilities", "effect_state",
			"effect_resources", "effect_resource_read", "effect_view_state", "effect_animation_state", "effect_resource_audit");
	private static final List<String> WRITE_COMMANDS = List.of("effect_create", "effect_edit_batch", "effect_preview",
			"effect_save", "effect_export", "effect_history", "effect_open", "effect_close", "effect_resource_write",
			"effect_texture_import", "effect_view", "effect_animation_edit", "effect_animation_control");
	private static boolean registered;

	private static JsonObject repeat(String action, JsonObject request) {
		var minecraft = Minecraft.getInstance();
		FutureTask<JsonObject> task = new FutureTask<>(() -> {
			if (!action.equals("status") && !ControlModeHelper.isMcpControlMode()) {
				throw new IllegalStateException("repeat_control_required");
			}

			var label = request.has("label") ? request.get("label").getAsString() : "";
			switch (action) {
				case "start" -> PhotonRepeatedEffectTrial.start(
						PhotonRepeatedEffectTrial.Kind.valueOf(request.get("kind").getAsString().toUpperCase(Locale.ROOT)),
						PhotonRepeatedEffectTrial.Mode.BASE, label, null);
				case "prepare" -> PhotonRepeatedEffectTrial.prepareWindow();
				case "cancel" -> PhotonRepeatedEffectTrial.cancel(label);
				case "close" -> PhotonRepeatedEffectTrial.close(label);
				default -> {
				}
			}

			var result = new JsonObject();
			result.addProperty("ok", true);
			result.add("status", action.equals("status") || action.equals("prepare") ? PhotonRepeatedEffectTrial.runtimeStatus(label)
					: PhotonRepeatedEffectTrial.status(label));
			return result;
		});
		if (minecraft.isSameThread()) {
			task.run();
		} else {
			minecraft.execute(task);
		}

		try {
			return task.get(10, TimeUnit.SECONDS);
		} catch (Exception failure) {
			task.cancel(false);
			var result = new JsonObject();
			result.addProperty("ok", false);
			result.addProperty("operation_may_be_running", failure instanceof TimeoutException);
			result.addProperty("error", failure.toString());
			return result;
		}
	}

	public static void register() {
		if (registered) {
			return;
		}

		for (var command : READ_COMMANDS) {
			McpExtensionRegistry.register(command, false, request -> PhotonEffectAutomation.execute(command, request, false));
		}

		for (var command : WRITE_COMMANDS) {
			McpExtensionRegistry.register(command, true, request -> PhotonEffectAutomation.execute(command, request, true));
		}

		McpExtensionRegistry.register("photon_repeat_status", false, request -> repeat("status", request));
		for (var action : List.of("start", "close", "cancel", "prepare")) {
			McpExtensionRegistry.register("photon_repeat_" + action, true, request -> repeat(action, request));
		}

		registered = true;
	}
}
