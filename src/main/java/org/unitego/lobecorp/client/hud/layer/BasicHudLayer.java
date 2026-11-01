package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class BasicHudLayer extends HudLayer {
	@Override
	protected void renderContents(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (minecraft.options.hideGui || minecraft.player == null) {
			return;
		}

		renderLayer(graphics, deltaTracker);
	}

	protected abstract void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
}
