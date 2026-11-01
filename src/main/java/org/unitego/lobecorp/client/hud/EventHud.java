package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// 事件 HUD 的独立显示层，绘制顺序位于伤害滤镜上方。
public class EventHud implements GuiLayer {
    public static final EventHud INSTANCE = new EventHud();

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }
}
