package xyz.langyo.minecraft.mcp.mod.events.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;
import net.neoforged.neoforge.common.NeoForge;
import xyz.langyo.minecraft.mcp.mod.util.client.control.PlayerControlUtil;

public class McpInputEvents {
	public static void register() {
		NeoForge.EVENT_BUS.addListener(McpInputEvents::beforeClientTick);
		NeoForge.EVENT_BUS.addListener(McpInputEvents::onClientTick);
	}

	private static void beforeClientTick(ClientTickEvent.Pre event) {
		ControlModeHelper.tickMcpControlMode(Minecraft.getInstance());
		PlayerControlUtil.checkContext();
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		PlayerControlUtil.tick();
	}
}
