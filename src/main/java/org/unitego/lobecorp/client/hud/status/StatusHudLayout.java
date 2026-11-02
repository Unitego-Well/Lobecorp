package org.unitego.lobecorp.client.hud.status;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/// 玩家状态条的纯布局计算：从底部锚点向上堆叠，层间固定 2 像素。
@NullMarked
public record StatusHudLayout(List<Slot> slots, int width, int height) {
	public static final int LAYER_GAP = 2;

	/// 状态条的整数屏幕中心锚点；保留旧组合层的整除中心。
	public static float left(int screenWidth, int leftOffset) {
		return screenWidth / 2 - leftOffset;
	}

	public static StatusHudLayout stack(float left, float bottomY, List<Bar> bars) {
		var slots = new ArrayList<Slot>(bars.size());
		float top = bottomY;
		int width = 0;
		int height = 0;
		int visibleCount = 0;
		for (var bar : bars) {
			if (bar.visible()) {
				top -= bar.height();
				width = Math.max(width, bar.width());
				height += bar.height();
				visibleCount++;
			}

			slots.add(new Slot(left, top, bar.width(), bar.height(), bar.visible()));
			if (bar.visible()) {
				top -= LAYER_GAP;
			}
		}

		return new StatusHudLayout(List.copyOf(slots), width,
				height + Math.max(0, visibleCount - 1) * LAYER_GAP);
	}

	public record Bar(int width, int height, boolean visible) {
	}

	public record Slot(float left, float top, int width, int height, boolean visible) {
	}
}
