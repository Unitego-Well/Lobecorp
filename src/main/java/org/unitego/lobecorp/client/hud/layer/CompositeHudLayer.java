package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/// 按加入顺序从锚点向上堆叠；不可见子层仍刷新数值，避免再次显示时使用旧状态。
@NullMarked
public class CompositeHudLayer extends BasicHudLayer {
	private static final int LAYER_GAP = 2;
	private final List<Entry> layers = new ArrayList<>();

	public void addLayer(HudLayer layer, BooleanSupplier visible) {
		layers.add(new Entry(layer, visible));
	}

	public void addLayer(HudLayer layer) {
		addLayer(layer, () -> true);
	}

	public List<HudLayer> getLayers() {
		return layers.stream().map(Entry::layer).toList();
	}

	@Override
	public void init(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		super.init(graphics, deltaTracker);
		updatePosition();
		for (var entry : layers) {
			entry.layer().setPosition(leftPos, topPos);
			entry.layer().init(graphics, deltaTracker);
		}
	}

	protected void updatePosition() {
	}

	@Override
	protected void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			for (var entry : layers) {
				if (!entry.visible().getAsBoolean()) {
					continue;
				}

				pose.translate(0, -entry.layer().getHeight());
				entry.layer().render(graphics, deltaTracker);
				pose.translate(0, -LAYER_GAP);
			}
		} finally {
			pose.popMatrix();
		}
	}

	@Override
	public int getWidth() {
		return layers.stream().filter(entry -> entry.visible().getAsBoolean())
				.mapToInt(entry -> entry.layer().getWidth()).max().orElse(0);
	}

	@Override
	public int getHeight() {
		int height = 0;
		int count = 0;
		for (var entry : layers) {
			if (entry.visible().getAsBoolean()) {
				height += entry.layer().getHeight();
				count++;
			}
		}

		return height + Math.max(0, count - 1) * LAYER_GAP;
	}

	@Override
	public void clear() {
		super.clear();
		for (var entry : layers) {
			entry.layer().clear();
		}
	}

	private record Entry(HudLayer layer, BooleanSupplier visible) {
	}
}
