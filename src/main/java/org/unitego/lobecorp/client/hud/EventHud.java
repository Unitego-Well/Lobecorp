package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.LinkedHashMap;
import java.util.Map;

/// 事件 HUD 的独立显示层，绘制顺序位于伤害滤镜上方。
public class EventHud implements GuiLayer {
	public static final EventHud INSTANCE = new EventHud();
	private final Map<Identifier, GuiLayer> entries = new LinkedHashMap<>();

	public void register(Identifier id, GuiLayer renderer) {
		if (entries.putIfAbsent(id, renderer) != null) {
			throw new IllegalArgumentException("事件 HUD 已注册：" + id);
		}
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		for (var renderer : entries.values()) {
			renderer.render(graphics, deltaTracker);
		}
	}
}
