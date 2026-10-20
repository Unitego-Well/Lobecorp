package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class BasicHudLayer extends HudLayer {
	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (minecraft.options.hideGui || minecraft.player == null) {
			return;
		}

		init(graphics, deltaTracker);
		renderLayer(graphics, deltaTracker);
	}

	protected abstract void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
}
