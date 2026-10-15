package org.unitego.lobecorp.registry.photon.client;

import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectAutomation;
import xyz.langyo.minecraft.mcp.common.McpExtensionRegistry;

import java.util.List;

public class LcPhotonMcpCommands {
	public static final String MOD_ID = "mcpmod";
	private static boolean registered;
	private static final List<String> READ_COMMANDS = List.of("effect_capabilities", "effect_state",
			"effect_resources", "effect_resource_read", "effect_view_state");
	private static final List<String> WRITE_COMMANDS = List.of("effect_create", "effect_edit_batch", "effect_preview",
			"effect_save", "effect_export", "effect_history", "effect_open", "effect_resource_write",
			"effect_texture_import", "effect_view");

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

		registered = true;
	}
}
