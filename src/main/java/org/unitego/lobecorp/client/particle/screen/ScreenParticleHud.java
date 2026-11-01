package org.unitego.lobecorp.client.particle.screen;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/// LDLib2 管理屏幕粒子的绘制生命周期；世界投影与内容批次仍归粒子系统。
public class ScreenParticleHud implements ModularHudLayer {
    public static final ScreenParticleHud INSTANCE = new ScreenParticleHud();
    private @Nullable ModularUI modularUI;
    private @Nullable ClientLevel level;
    private @Nullable DeltaTracker deltaTracker;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        this.deltaTracker = deltaTracker;
        try {
            ModularHudLayer.super.render(graphics, deltaTracker);
        } finally {
            this.deltaTracker = null;
        }
    }

    @Override
    public @Nullable ModularUI getModularUI() {
        var current = Minecraft.getInstance().level;
        if (level != current) {
            clear();
            level = current;
        }
        if (level == null || Minecraft.getInstance().options.hideGui) {
            return null;
        }
        if (modularUI == null) {
            var canvas = new ParticleCanvas();
            canvas.layout(layout -> layout.widthPercent(100).heightPercent(100));
            modularUI = new ModularUI(UI.of(canvas, size -> size));
        }
        return modularUI;
    }

    public void clear() {
        if (modularUI != null) {
            modularUI.onRemoved();
            modularUI = null;
        }
        level = null;
    }

    private class ParticleCanvas extends UIElement {
        @Override
        protected void drawBackgroundAdditional(IGUIContext context) {
            if (context instanceof GUIContext clientContext && deltaTracker != null) {
                ScreenParticles.extract(clientContext.graphics, deltaTracker);
            }
        }
    }
}
