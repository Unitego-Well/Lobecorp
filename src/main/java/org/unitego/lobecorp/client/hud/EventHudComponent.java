package org.unitego.lobecorp.client.hud;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/// 每个活动事件独占一个组件；容器负责定位和垂直堆叠。
public abstract class EventHudComponent extends UIElement {
    private final EventHudDirection direction;

    protected EventHudComponent(EventHudDirection direction) {
        this.direction = direction;
        layout(layout -> layout.flexShrink(0));
    }

    public final EventHudDirection direction() {
        return direction;
    }
}
