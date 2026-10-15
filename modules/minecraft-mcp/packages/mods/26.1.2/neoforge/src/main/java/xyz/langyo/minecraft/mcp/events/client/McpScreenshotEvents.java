package xyz.langyo.minecraft.mcp.events.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import xyz.langyo.minecraft.mcp.common.ScreenshotHelper;

@EventBusSubscriber(modid = "mcpmod", value = Dist.CLIENT)
public class McpScreenshotEvents {

	@SubscribeEvent
	public static void onRenderFrame(RenderFrameEvent.Post ignored) {
		ScreenshotHelper.onRenderFrame(Minecraft.getInstance());
	}
}
