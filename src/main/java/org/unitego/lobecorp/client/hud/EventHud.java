package org.unitego.lobecorp.client.hud;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/// 注册定义与活动实例分离；三个区域由 LDLib2 分别垂直排列。
public class EventHud implements ModularHudLayer {
	public static final EventHud INSTANCE = new EventHud();
	private final Map<Identifier, Definition> definitions = new LinkedHashMap<>();
	private final Map<UUID, ActiveEntry> active = new LinkedHashMap<>();
	private final Map<EventHudDirection, UIElement> stacks = new EnumMap<>(EventHudDirection.class);
	private @Nullable ModularUI modularUI;
	private @Nullable ClientLevel level;

	public void register(Identifier id, String englishName, String chineseName,
	                     Supplier<? extends EventHudComponent> factory) {
		var definition = new Definition(englishName, chineseName, factory);
		if (definitions.putIfAbsent(id, definition) != null) {
			throw new IllegalArgumentException("事件 HUD 已注册：" + id);
		}
	}

	public EventHudComponent show(Identifier type, UUID instanceId) {
		updateSession();
		var existing = active.get(instanceId);
		if (existing != null) {
			if (!existing.type().equals(type)) {
				throw new IllegalArgumentException("事件 HUD 实例类型不一致：" + instanceId);
			}
			return existing.component();
		}
		var definition = definitions.get(type);
		if (definition == null) {
			throw new IllegalArgumentException("事件 HUD 未注册：" + type);
		}
		ensureUI();
		var component = definition.factory().get();
		stacks.get(component.direction()).addChild(component);
		active.put(instanceId, new ActiveEntry(type, component));
		return component;
	}

	public void hide(UUID instanceId) {
		var entry = active.remove(instanceId);
		if (entry != null) {
			stacks.get(entry.component().direction()).removeChild(entry.component());
		}
	}

	public void clear() {
		if (modularUI != null) {
			modularUI.onRemoved();
			modularUI = null;
		}
		active.clear();
		stacks.clear();
	}

	@Override
	public @Nullable ModularUI getModularUI() {
		updateSession();
		return active.isEmpty() ? null : modularUI;
	}

	private void updateSession() {
		var current = Minecraft.getInstance().level;
		if (level != current) {
			clear();
			level = current;
		}
	}

	private void ensureUI() {
		if (modularUI != null) {
			return;
		}
		var root = new UIElement();
		root.layout(layout -> layout.widthPercent(100).heightPercent(100));
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
			root.addChild(stack);
		}
		modularUI = new ModularUI(UI.of(root, size -> size));
	}

	public record Definition(String englishName, String chineseName,
	                         Supplier<? extends EventHudComponent> factory) {
	}

	private record ActiveEntry(Identifier type, EventHudComponent component) {
	}
}
