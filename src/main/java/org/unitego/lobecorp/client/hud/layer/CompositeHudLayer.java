package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/// 按加入顺序从锚点向上堆叠；不可见子层仍刷新数值，避免再次显示时使用旧状态。
@NullMarked
public class CompositeHudLayer extends BasicHudLayer {
	private static final int LAYER_GAP = 2;
	private final List<Entry> layers = new ArrayList<>();

	public CompositeHudLayer addLayer(HudLayer layer, BooleanSupplier visible) {
		for (int index = 0; index < layers.size(); index++) {
			if (layers.get(index).layer().equals(layer)) {
				layers.set(index, new Entry(layer, visible));
				return this;
			}
		}

		layers.add(new Entry(layer, visible));
		return this;
	}

	public CompositeHudLayer addLayer(HudLayer layer) {
		return addLayer(layer, () -> true);
	}

	public boolean removeLayer(HudLayer layer) {
		return layers.removeIf(entry -> entry.layer().equals(layer));
	}

	public List<HudLayer> getLayers() {
		return layers.stream().map(Entry::layer).toList();
	}

	@Override
	public void playerChanged(@Nullable LocalPlayer nextPlayer) {
		super.playerChanged(nextPlayer);
		for (var entry : layers) {
			entry.layer().playerChanged(nextPlayer);
		}
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
