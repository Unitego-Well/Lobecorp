package xyz.langyo.minecraft.mcp.mod.client;

import xyz.langyo.minecraft.mcp.common.McpExtensionRegistry;
import xyz.langyo.minecraft.mcp.mod.events.client.McpInputEvents;
import xyz.langyo.minecraft.mcp.mod.util.client.control.BlockControlUtil;
import xyz.langyo.minecraft.mcp.mod.util.client.control.EntityControlUtil;
import xyz.langyo.minecraft.mcp.mod.util.client.control.GuiControlUtil;
import xyz.langyo.minecraft.mcp.mod.util.client.control.PlayerControlUtil;

public class ClientControlCommands {
	public static void register() {
		McpExtensionRegistry.register("gui_control", false, GuiControlUtil::handle);
		McpExtensionRegistry.register("player_control", false, PlayerControlUtil::handle);
		McpExtensionRegistry.register("entity_control", false, EntityControlUtil::handle);
		McpExtensionRegistry.register("block_control", false, BlockControlUtil::handle);
		McpInputEvents.register();
	}
}
