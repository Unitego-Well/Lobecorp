package org.unitego.lobecorp.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Post;
import org.unitego.lobecorp.client.horde.HordeDebugState;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

/// 在 Post 阶段绘制浪潮统计文字。
public class RuntimeOverlayHud extends ContainerHudRenderer {
	public static final RuntimeOverlayHud INSTANCE = new RuntimeOverlayHud();

	private RuntimeOverlayHud() {
		setPosition(8, 8);
		setLayout(Layout.VERTICAL);
		setGap(2);
		setAutoSize(true);
	}

	/// 保持原事件入口；直接绘制统计文字。
	public void render(Post event) {
		if (!visible()) {
			return;
		}

		GuiGraphicsExtractor graphics = event.getGuiGraphics();
		var lines = HordeDebugState.lines();
		while (getChildren().size() > lines.size()) {
			remove(getChildren().getLast());
		}
		while (getChildren().size() < lines.size()) {
			add(new TextHudRenderer());
		}
		for (int index = 0; index < lines.size(); index++) {
			((TextHudRenderer) getChildren().get(index)).setText(Component.literal(lines.get(index)));
		}
		super.render(graphics);
	}

	private boolean visible() {
		var minecraft = Minecraft.getInstance();
		return minecraft.level != null && HordeVisualConfig.STATISTICS.get() && minecraft.screen == null;
	}
}
