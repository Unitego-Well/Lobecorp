package org.unitego.lobecorp.client.hud.event;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.client.hud.EventHudComponent;
import org.unitego.lobecorp.client.hud.EventHudDirection;
import org.unitego.lobecorp.client.hud.host.HudFrame;

import java.util.EnumMap;
import java.util.Map;

/// 事件 HUD 的普通 UI 视图：持有三个固定区域，并按纯状态恢复子节点顺序。
///
/// 子节点只在活动集合与当前顺序不同时重建，不参与会话清理或绘制循环。
@NullMarked
public final class EventHudView extends UIElement {
	private final EventHudState<EventHudComponent> state;
	private final Map<EventHudDirection, UIElement> stacks = new EnumMap<>(EventHudDirection.class);

	public EventHudView(EventHudState<EventHudComponent> state) {
		this.state = state;
		layout(layout -> layout.widthPercent(100).heightPercent(100));
		for (var direction : EventHudDirection.values()) {
			var stack = new UIElement();
			stack.lss("position", "absolute");
			stack.lss("flex-direction", "column");
			stack.lss("gap", 4);
			stack.lss("width", "33.333%");
			stack.lss("top", direction == EventHudDirection.TOP ? "12px" : "33.333%");
			stack.lss("left", switch (direction) {
				case LEFT -> "0%";
				case TOP -> "33.333%";
				case RIGHT -> "66.666%";
			});
			stack.lss("align-items", switch (direction) {
				case LEFT -> "flex-start";
				case TOP -> "center";
				case RIGHT -> "flex-end";
			});
			stacks.put(direction, stack);
			addChild(stack);
		}
	}

	/// 在宿主布局前同步活动组件；仅顺序变化时重建对应区域的子节点。
	public void prepare(HudFrame ignored) {
		for (var direction : EventHudDirection.values()) {
			var desired = state.activeInOrder(direction);
			var stack = stacks.get(direction);
			if (stack.getChildren().equals(desired)) {
				continue;
			}

			stack.clearAllChildren();
			for (var component : desired) {
				stack.addChild(component);
			}
		}
	}

	/// 释放子节点引用，保留区域容器以便同一宿主重新显示。
	public void clear() {
		for (var stack : stacks.values()) {
			stack.clearAllChildren();
		}
	}
}
