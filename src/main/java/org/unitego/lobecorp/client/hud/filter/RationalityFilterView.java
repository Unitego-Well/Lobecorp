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
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

/// 低理智滤镜视图：`prepare` 采样理智并推进平滑状态，绘制只读取三层透明度。
@NullMarked
public final class RationalityFilterView extends UIElement {
	private final RationalityFilterState state;
	private final ScreenFilter[] filters = {
			new ScreenFilter(texture("rationality")),
			new ScreenFilter(texture("rationality1")),
			new ScreenFilter(texture("rationality2"))
	};

	public RationalityFilterView(RationalityFilterState state) {
		this.state = state;
		layout(layout -> layout.widthPercent(100).heightPercent(100));
	}

	public void prepare(HudFrame frame) {
		var player = frame.player();
		if (player == null) {
			state.clear();
			return;
		}

		state.advance(LcDamageUtil.getRationality(player), LcDamageUtil.getMaxRationality(player), deltaTicks(frame));
	}

	@Override
	protected void drawBackgroundAdditional(IGUIContext context) {
		if (!(context instanceof GUIContext gui)) {
			return;
		}

		GuiGraphicsExtractor graphics = gui.graphics;
		for (int index = 0; index < filters.length; index++) {
			var filter = filters[index];
			filter.setAlpha(state.alpha(index));
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
