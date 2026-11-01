package org.unitego.lobecorp.client.hud;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Post;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.conductor.render.ConductorRendering;
import org.unitego.lobecorp.client.horde.HordeDebugState;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

/// 最后提取的运行覆盖层，保持指令光标与统计文字的原有顺序。
public class RuntimeOverlayHud implements ModularHudLayer {
	public static final RuntimeOverlayHud INSTANCE = new RuntimeOverlayHud();
	private @Nullable ModularUI modularUI;
	private @Nullable ClientLevel level;
	private @Nullable Post frame;

	public void render(Post event) {
		frame = event;
		try {
			ModularHudLayer.super.render(event.getGuiGraphics(), event.getPartialTick());
		} finally {
			frame = null;
		}
	}

	@Override
	public @Nullable ModularUI getModularUI() {
		var current = Minecraft.getInstance().level;
		if (level != current) {
			clear();
			level = current;
		}
		if (level == null) {
			return null;
		}
		if (modularUI == null) {
			var canvas = new OverlayCanvas();
			canvas.layout(layout -> layout.widthPercent(100).heightPercent(100));
			modularUI = new ModularUI(UI.of(canvas, size -> size));
		}
		return modularUI;
	}

	public void clear() {
		if (modularUI != null) {
			modularUI.onRemoved();
			modularUI = null;
		}
		level = null;
	}

	private class OverlayCanvas extends UIElement {
		@Override
		protected void drawBackgroundAdditional(IGUIContext context) {
			if (!(context instanceof GUIContext gui) || frame == null) {
				return;
			}
			ConductorRendering.onGui(frame);
			var minecraft = Minecraft.getInstance();
			if (HordeVisualConfig.STATISTICS.get() && minecraft.screen == null) {
				var lines = HordeDebugState.lines();
				for (int index = 0; index < lines.size(); index++) {
					gui.graphics.text(minecraft.font, lines.get(index), 8, 8 + index * 11, -1);
				}
			}
		}
	}
}
