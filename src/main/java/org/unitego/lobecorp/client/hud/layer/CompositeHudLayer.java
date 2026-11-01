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
		addChild(layer);
		return this;
	}

	public CompositeHudLayer addLayer(HudLayer layer) {
		return addLayer(layer, () -> true);
	}

	public boolean removeLayer(HudLayer layer) {
		boolean removed = layers.removeIf(entry -> entry.layer().equals(layer));
		if (removed) {
			removeChild(layer);
		}

		return removed;
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
		int top = topPos;
		for (var entry : layers) {
			boolean visible = entry.visible().getAsBoolean();
			entry.layer().setVisible(visible);
			if (visible) {
				top -= entry.layer().getHeight();
			}

			entry.layer().setPosition(leftPos, top);
			entry.layer().init(graphics, deltaTracker);
			if (visible) {
				top -= LAYER_GAP;
			}
		}
	}

	protected void updatePosition() {
	}

	@Override
	protected void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// 子节点由 LDLib2 提取，不再独立调用子层 render。
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
