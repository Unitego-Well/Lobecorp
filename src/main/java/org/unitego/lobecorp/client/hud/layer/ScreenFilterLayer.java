package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.unitego.lobecorp.client.hud.widget.ScreenFilter;

import java.util.List;

/// 顺序绘制一组全屏滤镜，并以屏幕尺寸作为该层的占位大小。
@NullMarked
public class ScreenFilterLayer extends BasicHudLayer {
	protected final List<ScreenFilter> filters;

	public ScreenFilterLayer(ScreenFilter... filters) {
		this.filters = List.of(filters);
	}

	@Override
	protected void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		for (var filter : filters) {
			filter.render(graphics);
		}
	}

	@Override
	public int getWidth() {
		return screenWidth;
	}

	@Override
	public int getHeight() {
		return screenHeight;
	}

	@Override
	public void clear() {
		super.clear();
		for (var filter : filters) {
			filter.setAlpha(0);
		}
	}
}
