package org.unitego.lobecorp.client.hud.filter;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.widget.ScreenFilter;

/// 四色伤害滤镜视图：`prepare` 推进纯状态，绘制只读取当帧透明度。
@NullMarked
public final class DamageFilterView extends UIElement {
	private static final String[] TEXTURES = {"physics", "spirit", "erosion", "the_soul"};
	private final DamageFilterState state;
	private final ScreenFilter[] filters = new ScreenFilter[DamageFilterState.CHANNEL_COUNT];

	public DamageFilterView(DamageFilterState state) {
		this.state = state;
		for (int index = 0; index < filters.length; index++) {
			filters[index] = new ScreenFilter(texture(TEXTURES[index]));
		}

		layout(layout -> layout.widthPercent(100).heightPercent(100));
	}

	public void prepare(HudFrame frame) {
		state.advance(deltaTicks(frame));
	}

	@Override
	protected void drawBackgroundAdditional(IGUIContext context) {
		if (!(context instanceof GUIContext gui)) {
			return;
		}

		GuiGraphicsExtractor graphics = gui.graphics;
		for (var layer : state.entries()) {
			var filter = filters[layer.channel()];
			filter.setAlpha(layer.alpha());
			filter.render(graphics);
		}
	}

	public void clear() {
		for (var filter : filters) {
			filter.setAlpha(0);
		}
	}

	private static float deltaTicks(HudFrame frame) {
		return Minecraft.getInstance().isPaused() ? 0 : frame.deltaTracker().getRealtimeDeltaTicks();
	}

	private static Identifier texture(String name) {
		return Lobecorp.id("textures/gui/filter/" + name + ".png");
	}
}
