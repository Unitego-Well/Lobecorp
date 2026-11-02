package org.unitego.lobecorp.client.hud;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Post;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.render.ConductorRendering;
import org.unitego.lobecorp.client.horde.HordeDebugState;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.host.HudHost;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

/// 最后提取的运行覆盖层，保持指令光标与统计文字的原有顺序。
public class RuntimeOverlayHud {
	public static final Identifier ID = Lobecorp.id("runtime_overlay_hud");
	public static final RuntimeOverlayHud INSTANCE = new RuntimeOverlayHud();

	private RuntimeOverlayHud() {
	}

	/// 向统一管理器注册运行覆盖层宿主；实际创建与清理由管理器负责。
	public void register() {
		HudLayerManager.INSTANCE.register(ID, this::visible, () -> {
			var content = new UIElement();
			content.layout(layout -> layout.widthPercent(100).heightPercent(100));
			return new HudHost(content, this::draw, () -> {
			});
		});
	}

	/// 保持原事件入口；宿主登记由启动期串行集成完成，此处只提交当帧提取。
	public void render(Post event) {
		HudLayerManager.INSTANCE.render(ID, event.getGuiGraphics(), event.getPartialTick());
	}

	public void clear() {
		HudLayerManager.INSTANCE.clear(ID);
	}

	private boolean visible() {
		return Minecraft.getInstance().level != null;
	}

	private void draw(HudFrame frame) {
		ConductorRendering.onGui(frame.graphics());
		var minecraft = Minecraft.getInstance();
		if (HordeVisualConfig.STATISTICS.get() && minecraft.screen == null) {
			var lines = HordeDebugState.lines();
			for (int index = 0; index < lines.size(); index++) {
				frame.graphics().text(minecraft.font, lines.get(index), 8, 8 + index * 11, -1);
			}
		}
	}
}
